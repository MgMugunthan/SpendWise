package com.example.intelligence

import com.example.domain.model.BankAccountModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.ConfidenceLevel
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType

sealed class ParseOutcome {
    data class Ignored(val reason: String, val fingerprint: String) : ParseOutcome()
    data class Duplicate(val reason: String, val fingerprint: String) : ParseOutcome()
    data class Parsed(
        val transaction: TransactionModel,
        val bankName: String?,
        val accountSuffix: String?,
        val availableBalanceMinorUnits: Long?,
        val matchReason: String,
        val confidenceLevel: ConfidenceLevel,
        val fingerprint: String
    ) : ParseOutcome()
}

/**
 * Orchestrates SpendWise's complete local offline transaction detection pipeline:
 * Classification -> Independent Balance Extraction -> Amount & Currency -> Date ->
 * Account Suffix -> Bank -> Separated Merchant/Recipient -> Payment Method ->
 * Reference -> Account Matching -> Duplicate Check -> Validation & Confidence ->
 * Categorisation (Learned Rules -> Bank Charges -> Known Merchants -> Person-to-Person).
 */
class TransactionParserEngine {

    fun parse(
        sourcePackage: String,
        title: String,
        body: String,
        timestampMillis: Long,
        activeAccounts: List<BankAccountModel>,
        categories: List<CategoryModel>,
        userLearnedRules: Map<String, Long>,
        recentTransactionsForDuplicateCheck: List<TransactionModel>,
        existingReferenceMatch: Boolean,
        existingFingerprintMatch: Boolean,
        defaultCurrency: String = "INR"
    ): ParseOutcome {
        val combinedText = listOf(title.trim(), body.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")

        val fingerprint = DuplicateDetector.computeNotificationFingerprint(
            sourcePackage = sourcePackage,
            title = title,
            body = body,
            minuteBucketTimestamp = timestampMillis
        )

        if (combinedText.isBlank()) {
            return ParseOutcome.Ignored("Empty notification content", fingerprint)
        }

        if (existingFingerprintMatch) {
            return ParseOutcome.Duplicate("Notification already processed (matching fingerprint)", fingerprint)
        }

        // Step 1: Reject OTPs, promos, failed/declined, and balance-only messages; classify Debit/Credit/Pending/Charge
        val classification = TransactionClassifier.classify(combinedText)
        if (classification is ClassificationDecision.Ignored) {
            return ParseOutcome.Ignored(classification.reason, fingerprint)
        }
        val txDecision = classification as ClassificationDecision.Transaction

        // Step 2: Extract available balance & transaction amount independently (never confuse the two)
        val extractedBalance = BalanceExtractor.extract(combinedText, defaultCurrency)
        val extractedAmount = AmountExtractor.extract(combinedText, defaultCurrency)
            ?: return ParseOutcome.Ignored("No valid monetary amount found", fingerprint)

        // Step 3: Extract bank, suffix, sender/receiver last 4 digits, date, payment method, reference, and separated counterparty
        val bankName = BankIdentifier.identify(combinedText, sourcePackage)
        val suffix = AccountExtractor.extractSuffix(combinedText)
        val counterpartySuffix = AccountExtractor.extractCounterpartySuffix(combinedText, primaryAccountSuffix = suffix)
        val txDateMillis = TransactionDateExtractor.extractMillis(combinedText, timestampMillis)
        val paymentMethod = PaymentMethodDetector.detect(combinedText, isBankCharge = txDecision.isBankCharge)
        val referenceNumber = ReferenceExtractor.extract(combinedText)
        val extractedCounterparty = RecipientExtractor.extractClassified(
            text = combinedText,
            isBankCharge = txDecision.isBankCharge,
            bankName = bankName
        )

        // Separate merchant, recipient, and sender cleanly!
        val merchantName: String
        val recipientName: String
        val senderName: String

        when (extractedCounterparty?.kind) {
            CounterpartyKind.MERCHANT, CounterpartyKind.BANK_CHARGE -> {
                merchantName = extractedCounterparty.name
                recipientName = ""
                senderName = if (txDecision.type == TransactionType.CREDIT) extractedCounterparty.name else ""
            }
            CounterpartyKind.PERSON -> {
                merchantName = ""
                recipientName = if (txDecision.type == TransactionType.DEBIT) extractedCounterparty.name else ""
                senderName = if (txDecision.type == TransactionType.CREDIT) extractedCounterparty.name else ""
            }
            CounterpartyKind.UNKNOWN -> {
                merchantName = ""
                recipientName = if (txDecision.type == TransactionType.DEBIT) extractedCounterparty.name else ""
                senderName = if (txDecision.type == TransactionType.CREDIT) extractedCounterparty.name else ""
            }
            null -> {
                merchantName = if (txDecision.isBankCharge) "Bank Charges" else ""
                recipientName = ""
                senderName = ""
            }
        }

        val counterpartyForDup = merchantName.ifBlank { recipientName.ifBlank { senderName } }

        // Step 4: Match bank account
        val matchResult = AccountMatcher.match(
            bankName = bankName,
            suffix = suffix,
            activeAccounts = activeAccounts,
            currencyCode = extractedAmount.currencyCode
        )

        // Step 5: Duplicate check (reference number + fallback heuristic within 15-minute window)
        if (existingReferenceMatch && !referenceNumber.isNullOrBlank()) {
            return ParseOutcome.Duplicate("Duplicate bank reference $referenceNumber", fingerprint)
        }
        if (DuplicateDetector.isFallbackDuplicate(
                candidateAmountMinorUnits = extractedAmount.amountMinorUnits,
                candidateType = txDecision.type,
                candidateAccountId = matchResult.matchedAccount?.accountId,
                candidateCounterparty = counterpartyForDup,
                candidateTimestamp = txDateMillis,
                candidateReference = referenceNumber,
                recentExisting = recentTransactionsForDuplicateCheck
            )
        ) {
            return ParseOutcome.Duplicate("Duplicate transaction detected within 15-minute window", fingerprint)
        }

        // Step 6: Validate and compute confidence score
        val (score, level) = TransactionValidator.scoreCandidate(
            baseClassificationScore = txDecision.scoreBoost,
            hasAmount = true,
            hasBank = bankName != null,
            hasAccountSuffix = suffix != null,
            hasCounterparty = extractedCounterparty != null || counterpartySuffix != null,
            hasReference = referenceNumber != null,
            hasAvailableBalance = extractedBalance != null,
            accountMatched = matchResult.matchedAccount != null,
            accountAmbiguous = matchResult.isAmbiguous,
            isPending = txDecision.isPending
        )

        if (level == ConfidenceLevel.LOW) {
            return ParseOutcome.Ignored("Low confidence (${(score * 100).toInt()}%) — ignored safely", fingerprint)
        }

        // Step 7: Local categorization (Learned Rules -> Bank Charges -> Income -> Known Merchants -> Person-to-Person -> Other)
        val categoryId = TransactionCategorizer.categorize(
            merchant = merchantName.takeIf { it.isNotBlank() },
            recipient = recipientName.takeIf { it.isNotBlank() },
            sender = senderName.takeIf { it.isNotBlank() },
            counterpartyKind = extractedCounterparty?.kind ?: CounterpartyKind.UNKNOWN,
            rawText = combinedText,
            transactionType = txDecision.type,
            paymentMethod = paymentMethod,
            isBankCharge = txDecision.isBankCharge,
            categories = categories,
            userLearnedRules = userLearnedRules
        )

        // Only completed, high-confidence notifications with a verified account match are CONFIRMED.
        // Pending payments or unmatched/ambiguous accounts are queued as PENDING_REVIEW and do NOT change balances.
        val status = if (!txDecision.isPending && level == ConfidenceLevel.HIGH && matchResult.matchedAccount != null) {
            TransactionStatus.CONFIRMED
        } else {
            TransactionStatus.PENDING_REVIEW
        }

        val notes = buildString {
            if (bankName != null) append("$bankName ")
            if (suffix != null) append("XX$suffix · ")
            append(paymentMethod.displayName)
            if (counterpartySuffix != null) {
                val partyLabel = if (txDecision.type == TransactionType.CREDIT) "Sender" else "Receiver"
                append(" · $partyLabel XX$counterpartySuffix")
            }
            if (extractedBalance != null && extractedBalance.currencyCode.equals(extractedAmount.currencyCode, ignoreCase = true)) {
                append(" · Bank Avl Bal: ${MoneyFormatter.formatMinorUnits(extractedBalance.amountMinorUnits, extractedBalance.currencyCode)}")
            }
            if (txDecision.isPending) {
                append(" · Pending payment (not yet in balance)")
            } else if (status == TransactionStatus.PENDING_REVIEW) {
                append(" · Review: ${matchResult.reason}")
            }
        }

        val model = TransactionModel(
            transactionId = 0L,
            accountId = matchResult.matchedAccount?.accountId,
            targetAccountId = null,
            amountMinorUnits = extractedAmount.amountMinorUnits,
            currencyCode = extractedAmount.currencyCode,
            transactionType = txDecision.type,
            recipient = recipientName,
            sender = senderName,
            merchant = merchantName,
            categoryId = categoryId,
            paymentMethod = paymentMethod,
            transactionDateMillis = txDateMillis,
            referenceNumber = referenceNumber,
            sourceType = TransactionSource.BANK_NOTIFICATION,
            transactionStatus = status,
            description = notes,
            confidenceScore = score,
            availableBalanceMinorUnits = extractedBalance
                ?.takeIf { it.currencyCode.equals(extractedAmount.currencyCode, ignoreCase = true) }
                ?.amountMinorUnits,
            counterpartyAccountSuffix = counterpartySuffix,
            createdAt = timestampMillis,
            updatedAt = timestampMillis
        )

        return ParseOutcome.Parsed(
            transaction = model,
            bankName = bankName,
            accountSuffix = suffix,
            availableBalanceMinorUnits = model.availableBalanceMinorUnits,
            matchReason = matchResult.reason,
            confidenceLevel = level,
            fingerprint = fingerprint
        )
    }
}
