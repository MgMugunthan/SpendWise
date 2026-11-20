package com.example.intelligence

import com.example.domain.model.MoneyFormatter
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionType
import java.util.Calendar

data class ExtractedAmount(
    val amountMinorUnits: Long,
    val currencyCode: String
)

enum class CounterpartyKind {
    MERCHANT,
    PERSON,
    BANK_CHARGE,
    UNKNOWN
}

data class ExtractedCounterparty(
    val name: String,
    val kind: CounterpartyKind
)

sealed class ClassificationDecision {
    data class Transaction(
        val type: TransactionType,
        val scoreBoost: Float,
        val isPending: Boolean = false,
        val isBankCharge: Boolean = false
    ) : ClassificationDecision()

    data class Ignored(val reason: String) : ClassificationDecision()
}

/**
 * 1. Identifies bank name from sender DLT headers, notification title, body, or package metadata.
 */
object BankIdentifier {
    private val bankPatterns: List<Pair<String, Regex>> = listOf(
        "State Bank of India" to Regex("\\b(sbi|state bank|sbiinb|sbipsg|sbiupi|sbicrd|atmsbi|cbssbi|yono)\\b", RegexOption.IGNORE_CASE),
        "HDFC Bank" to Regex("\\b(hdfc|hdfcbk|hdfcbank|hdfccc)\\b", RegexOption.IGNORE_CASE),
        "ICICI Bank" to Regex("\\b(icici|icicib|icicit|imobile)\\b", RegexOption.IGNORE_CASE),
        "Axis Bank" to Regex("\\b(axis|axisbk|axisbank)\\b", RegexOption.IGNORE_CASE),
        "Indian Bank" to Regex("\\b(indian bank|indbnk|indoasis)\\b", RegexOption.IGNORE_CASE),
        "Canara Bank" to Regex("\\b(canara|canbnk)\\b", RegexOption.IGNORE_CASE),
        "Kotak Mahindra Bank" to Regex("\\b(kotak|kotakb)\\b", RegexOption.IGNORE_CASE),
        "Punjab National Bank" to Regex("\\b(pnb|pnbsms|punjab national)\\b", RegexOption.IGNORE_CASE),
        "Bank of Baroda" to Regex("\\b(bob|bobsms|bank of baroda|baroda)\\b", RegexOption.IGNORE_CASE),
        "Federal Bank" to Regex("\\b(federal bank|fedbnk)\\b", RegexOption.IGNORE_CASE),
        "IDFC First Bank" to Regex("\\b(idfc|idfcfb|idfc first)\\b", RegexOption.IGNORE_CASE),
        "Yes Bank" to Regex("\\b(yes bank|yesbnk)\\b", RegexOption.IGNORE_CASE),
        "IndusInd Bank" to Regex("\\b(indusind|indusb)\\b", RegexOption.IGNORE_CASE),
        "Union Bank of India" to Regex("\\b(union bank|unionb)\\b", RegexOption.IGNORE_CASE),
        "Bank of India" to Regex("\\b(bank of india|boiind)\\b", RegexOption.IGNORE_CASE),
        "RBL Bank" to Regex("\\b(rbl bank|rblbnk)\\b", RegexOption.IGNORE_CASE),
        "Chase" to Regex("\\b(chase|jpmorgan)\\b", RegexOption.IGNORE_CASE),
        "Revolut" to Regex("\\b(revolut)\\b", RegexOption.IGNORE_CASE),
        "HSBC" to Regex("\\b(hsbc)\\b", RegexOption.IGNORE_CASE),
        "Citi" to Regex("\\b(citi|citibank)\\b", RegexOption.IGNORE_CASE)
    )

    private val packageBankHints = mapOf(
        "com.sbi.lotusintouch" to "State Bank of India",
        "com.sbi.SBIFreedomPlus" to "State Bank of India",
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.csam.icici.bank.imobile" to "ICICI Bank",
        "com.icicibank.pockets" to "ICICI Bank",
        "com.axis.mobile" to "Axis Bank",
        "com.msf.kbank.mobile" to "Kotak Mahindra Bank",
        "com.canarabank.mobility" to "Canara Bank",
        "com.IndianBank.IndOASIS" to "Indian Bank",
        "com.bob.mconnectplus" to "Bank of Baroda",
        "com.pnb.pnbone" to "Punjab National Bank",
        "com.fedmobile" to "Federal Bank",
        "com.idfcfirstbank.optimus" to "IDFC First Bank",
        "com.rblbank.mobank" to "RBL Bank"
    )

    fun identify(text: String, sourcePackage: String = ""): String? {
        for ((bankName, regex) in bankPatterns) {
            if (regex.containsMatchIn(text)) return bankName
        }
        return packageBankHints[sourcePackage]
    }
}

/**
 * 2. Extracts available / clear / updated balance independently from notifications and strips
 *    balance clauses so transaction amount & recipient extractors never confuse balance with amount.
 */
object BalanceExtractor {
    private val balanceClauseRegex = Regex(
        """(?i)(?:avl\.?\s*bal(?:ance)?|avail(?:able)?\.?\s*bal(?:ance)?|clear\s*bal(?:ance)?|closing\s*bal(?:ance)?|current\s*bal(?:ance)?|updated\s*bal(?:ance)?|a/c\s*bal(?:ance)?|acct\s*bal(?:ance)?|\bbal(?:ance)?)(?:\s+(?:in|of|for)\s+(?:your\s+)?(?:a/c|ac|acct|account)\s+[A-Za-z0-9xX*\-.]+)?(?:\s*(?:is|as\s+on\s+[A-Za-z0-9/\-.]+|:|=|-))*\s*(?:(₹|Rs\.?|INR|\$|USD|€|EUR|£|GBP|AED|SGD|S\$|¥|JPY)\s*)?([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?:/-)?(?:\s*(INR|Rs\.?|USD|EUR|GBP|AED|SGD|JPY)\b)?"""
    )

    private val balanceOnlyIndicatorRegex = Regex(
        """(?i)\b(balance\s+enquiry|available\s+balance|avl\.?\s*bal|avail\.?\s*bal|clear\s+bal|closing\s+balance|current\s+balance|account\s+balance|a/c\s+bal|mini\s+statement|bal\s+is)\b"""
    )

    fun extract(text: String, fallbackCurrency: String = "INR"): ExtractedAmount? {
        val match = balanceClauseRegex.find(text) ?: return null
        val leadingSymbol = match.groupValues[1]
        val numberStr = match.groupValues[2]
        val trailingCode = match.groupValues[3]
        val rawCurrency = leadingSymbol.ifBlank { trailingCode }
        val currencyCode = if (rawCurrency.isNotBlank()) {
            AmountExtractor.mapSymbolToCode(rawCurrency, fallbackCurrency)
        } else {
            fallbackCurrency
        }
        val minor = MoneyFormatter.parseMajorToMinor(numberStr, currencyCode) ?: return null
        return ExtractedAmount(minor, currencyCode)
    }

    fun stripBalanceClauses(text: String): String {
        return text.replace(balanceClauseRegex, " ").replace(Regex("\\s+"), " ").trim()
    }

    fun hasBalanceIndicator(text: String): Boolean = balanceOnlyIndicatorRegex.containsMatchIn(text)
}

/**
 * 3. Rejects OTPs, promos, failed/declined alerts, and balance-only messages,
 *    while preserving real debit/credit notifications that also include an "available balance" clause.
 *    Also flags explicitly pending/processing payments and detects bank charges cleanly.
 */
object TransactionClassifier {
    private val otpRejection = Regex(
        "\\b(otp|one[\\s-]?time[\\s-]?password|verification code|security code|do not share|valid for \\d+ min|mpin|cvv)\\b",
        RegexOption.IGNORE_CASE
    )
    private val promoRejection = Regex(
        "\\b(pre-?approved|loan offer|credit limit increase|apply now|cashback offer|flat \\d+% off|avail now|congratulations|win up to|special offer|emi starting|upgrade your card|exciting offers?)\\b",
        RegexOption.IGNORE_CASE
    )
    private val hardNonTransactionRejection = Regex(
        "\\b(insufficient balance|insufficient funds|declined|failed|unsuccessful|could not be processed|not completed|rejected|exceeds limit|card blocked|mini statement|statement is ready|due date reminder|minimum due|payment reminder|requested money|collect request|has requested|mandate created|autopay setup|bill generated)\\b",
        RegexOption.IGNORE_CASE
    )
    private val pendingIndicator = Regex(
        "\\b(is pending|payment pending|txn pending|transaction pending|status[:\\s]+pending|under process|processing|awaiting confirmation|on hold|authorization hold|transaction initiated|payment initiated)\\b",
        RegexOption.IGNORE_CASE
    )

    val bankChargePattern = Regex(
        "\\b(sms\\s*(?:alert)?\\s*(?:charges?|fees?)|annual\\s*maintenance\\s*(?:charges?|fees?)|\\bamc\\b|debit\\s*card\\s*(?:annual\\s*)?(?:charges?|fees?)|card\\s*(?:annual|issuance|renewal|maintenance)\\s*(?:charges?|fees?)|minimum\\s*balance\\s*(?:charges?|penalty|fee)|non[\\s-]*maintenance\\s*(?:charges?|penalty|fee)|\\b(?:mab|amb)\\s*(?:charges?|penalty|fee)|atm\\s*(?:usage\\s*)?(?:charges?|fees?)|cash\\s*withdrawal\\s*fee|cheque\\s*(?:bounce|return)\\s*(?:charges?|fees?)|(?:ecs|nach)\\s*return\\s*(?:charges?|fees?)|penal\\s*(?:charges?|interest)|penalty|late\\s*(?:payment\\s*)?fee|service\\s*(?:charges?|tax)|bank\\s*charges?|folio\\s*charges?|convenience\\s*fee|processing\\s*fee|gst\\s*(?:charged|debited|deducted|levied|of|rs|inr|₹)|(?:charges?|fees?)\\s*(?:including|with|\\+|and)?\\s*gst)\\b",
        RegexOption.IGNORE_CASE
    )

    private val refundPattern = Regex(
        "\\b(refund|refunded|reversed|reversal|cashback\\s+credited)\\b",
        RegexOption.IGNORE_CASE
    )
    private val interestPattern = Regex(
        "\\b(interest\\s+credited|int\\.?\\s*pd|sb\\s+interest|quarterly\\s+interest|credited\\s+with\\s+interest)\\b",
        RegexOption.IGNORE_CASE
    )
    private val depositPattern = Regex(
        "\\b(deposited|cash\\s+deposit|cdm\\s+deposit|cheque\\s+deposit)\\b",
        RegexOption.IGNORE_CASE
    )
    private val debitPatterns = Regex(
        "\\b(debited|spent|paid|withdrawn|withdrawal|purchase|deducted|sent\\s+to|sent\\s+(?:rs|inr|₹)|payment\\s+of|charged|levied|recovered|dr\\.?|autopay\\s+debited|auto[\\s-]?debit(?:ed)?|used\\s+at|transferred\\s+to|trf\\s+to|ecs\\s+debit|nach\\s+debit|emi\\s+debited|pos\\s+txn)\\b",
        RegexOption.IGNORE_CASE
    )
    private val creditPatterns = Regex(
        "\\b(credited|received|deposited|refund|refunded|cashback\\s+credited|salary\\s+credited|interest\\s+credited|cr\\.?|reversed|money\\s+received|added\\s+to|trf\\s+from)\\b",
        RegexOption.IGNORE_CASE
    )

    fun classify(text: String): ClassificationDecision {
        if (otpRejection.containsMatchIn(text)) {
            return ClassificationDecision.Ignored("Rejected: OTP / security verification message")
        }
        if (promoRejection.containsMatchIn(text)) {
            return ClassificationDecision.Ignored("Rejected: Promotional / marketing alert")
        }
        if (hardNonTransactionRejection.containsMatchIn(text)) {
            return ClassificationDecision.Ignored("Rejected: Failed, declined, or non-completed alert")
        }

        // Strip "available balance" clauses before checking transaction verbs so balance wording
        // never blocks a real debit/credit message and never falsely triggers a transaction.
        val txClauseText = BalanceExtractor.stripBalanceClauses(text)

        val isPending = pendingIndicator.containsMatchIn(txClauseText)
        val isCharge = bankChargePattern.containsMatchIn(txClauseText)
        val hasRefund = refundPattern.containsMatchIn(txClauseText)
        val hasInterest = interestPattern.containsMatchIn(txClauseText)
        val hasDeposit = depositPattern.containsMatchIn(txClauseText)
        val hasDebit = debitPatterns.containsMatchIn(txClauseText) || isCharge
        val hasCredit = creditPatterns.containsMatchIn(txClauseText) || hasRefund || hasInterest || hasDeposit

        if (!hasDebit && !hasCredit) {
            return if (BalanceExtractor.hasBalanceIndicator(text)) {
                ClassificationDecision.Ignored("Ignored: Balance-only message (no transaction)")
            } else {
                ClassificationDecision.Ignored("Ignored: No debit or credit transaction indicators found")
            }
        }

        val resolvedType = when {
            hasRefund -> TransactionType.CREDIT
            hasInterest && !hasDebit -> TransactionType.CREDIT
            isCharge && !hasRefund -> TransactionType.DEBIT
            hasDebit && !hasCredit -> TransactionType.DEBIT
            hasCredit && !hasDebit -> TransactionType.CREDIT
            else -> {
                val debitIndex = debitPatterns.find(txClauseText)?.range?.first ?: Int.MAX_VALUE
                val creditIndex = creditPatterns.find(txClauseText)?.range?.first ?: Int.MAX_VALUE
                if (debitIndex <= creditIndex) TransactionType.DEBIT else TransactionType.CREDIT
            }
        }

        val boost = when {
            isPending -> 0.15f
            hasDebit && hasCredit && !hasRefund && !isCharge -> 0.22f
            else -> 0.30f
        }

        return ClassificationDecision.Transaction(
            type = resolvedType,
            scoreBoost = boost,
            isPending = isPending,
            isBankCharge = isCharge && resolvedType == TransactionType.DEBIT
        )
    }
}

/**
 * 4. Extracts transaction amount and currency into exact integer minor units (e.g. ₹1.00 = 100 paise).
 *    Strips available-balance clauses first and supports real Indian bank formats including
 *    currency-omitted amounts after debit/credit verbs (e.g. SBI "debited by 1.0 on date ...").
 */
object AmountExtractor {
    private val currencyAmountRegex = Regex(
        """(?i)(₹|Rs\.?|INR|\$|USD|€|EUR|£|GBP|AED|SGD|S\$|¥|JPY)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?:/-)?"""
    )
    private val trailingCurrencyRegex = Regex(
        """(?i)\b([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?:/-)?\s*(INR|Rs\.?|USD|EUR|GBP|AED|SGD|JPY)\b"""
    )
    private val verbAmountRegex = Regex(
        """(?i)\b(?:debited|credited|deducted|paid|spent|withdrawn|transferred|received|charged|levied|recovered|deposited|refunded)\s+(?:by|for|with|of)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?:/-)?(?=\s|$|\.|,|;)"""
    )

    fun extract(text: String, fallbackCurrency: String = "INR"): ExtractedAmount? {
        val sanitized = BalanceExtractor.stripBalanceClauses(text)

        val match = currencyAmountRegex.find(sanitized)
        if (match != null) {
            val code = mapSymbolToCode(match.groupValues[1], fallbackCurrency)
            val minor = MoneyFormatter.parseMajorToMinor(match.groupValues[2], code)
            if (minor != null && minor > 0L) {
                return ExtractedAmount(minor, code)
            }
        }

        val trailing = trailingCurrencyRegex.find(sanitized)
        if (trailing != null) {
            val code = mapSymbolToCode(trailing.groupValues[2], fallbackCurrency)
            val minor = MoneyFormatter.parseMajorToMinor(trailing.groupValues[1], code)
            if (minor != null && minor > 0L) {
                return ExtractedAmount(minor, code)
            }
        }

        // Real-world SBI/Indian bank UPI format: "A/C X1234 debited by 1.0 on date 20Nov26 trf to RAHUL"
        val verbMatch = verbAmountRegex.find(sanitized)
        if (verbMatch != null) {
            val minor = MoneyFormatter.parseMajorToMinor(verbMatch.groupValues[1], fallbackCurrency)
            if (minor != null && minor > 0L) {
                return ExtractedAmount(minor, fallbackCurrency)
            }
        }

        return null
    }

    fun mapSymbolToCode(raw: String, fallback: String): String {
        return when (raw.trim().uppercase().removeSuffix(".")) {
            "₹", "RS", "INR" -> "INR"
            "$", "USD" -> "USD"
            "€", "EUR" -> "EUR"
            "£", "GBP" -> "GBP"
            "AED" -> "AED"
            "S$", "SGD" -> "SGD"
            "¥", "JPY" -> "JPY"
            else -> fallback
        }
    }
}

/**
 * 5. Extracts only the last 4 digits of an account/card suffix (never stores full account numbers).
 *    Supports single-X (SBI "A/C X1234"), multi-X ("XX1234"), asterisks ("*1234"), dots ("..1234"),
 *    spaced masks ("XX 1234"), and "ending 1234".
 */
object AccountExtractor {
    private val suffixPatterns = listOf(
        Regex("""(?i)(?:a/c|ac|acct|account|card)\s*(?:no\.?|number|ending(?:\s+with|\s+in)?)?\s*[xX*\.\-\s]{0,8}([0-9]{4})\b"""),
        Regex("""(?i)\b(?:x{1,6}|\*{1,6}|\.{2,4})\s*([0-9]{4})\b"""),
        Regex("""(?i)\bending\s+(?:in|with)?\s*[xX*]*([0-9]{4})\b""")
    )

    private val counterpartyAccountPatterns = listOf(
        // Explicit sender/receiver account or card suffix: "trf to A/c XX9876", "from beneficiary account ending 4321"
        Regex("""(?i)\b(?:trf\s+to|paid\s+to|sent\s+to|transferred\s+to|credited\s+to|to\s+beneficiary|to|received\s+from|credited\s+from|credited\s+by|trf\s+from|sent\s+by|from)\s+(?:beneficiary\s+)?(?:a/c|ac|acct|account|card|upi\s+a/c)\s*(?:no\.?|number|ending(?:\s+with|\s+in)?)?\s*[xX*\.\-\s]{0,8}([0-9]{4})\b"""),
        // Masked suffix right after to/from: "trf to XX9876", "from *4321"
        Regex("""(?i)\b(?:trf\s+to|paid\s+to|sent\s+to|transferred\s+to|to|received\s+from|from)\s+(?:x{1,6}|\*{1,6}|\.{2,4})\s*([0-9]{4})\b"""),
        // Sender/receiver mobile number or phone-based UPI VPA: "to 9876543210@ybl", "from 9876543210", "UPI/9876543210"
        Regex("""(?i)\b(?:trf\s+to|paid\s+to|sent\s+to|transferred\s+to|to|received\s+from|from|upi(?:/p2p)?/)\s*(?:\+?91[\-\s]?)?[6-9][0-9]{5}([0-9]{4})(?:@[A-Za-z0-9]+|\b)"""),
        // Counterparty suffix in parentheses after name: "to RAHUL (A/c XX8899)" or "from PRIYA (XX4321)"
        Regex("""(?i)\((?:a/c|ac|acct|account|card)?\s*[xX*\.\-\s]{1,6}([0-9]{4})\)""")
    )

    private val allMaskedSuffixRegex = Regex("""(?i)\b(?:x{1,6}|\*{1,6}|\.{2,4})\s*([0-9]{4})\b""")

    fun extractSuffix(text: String): String? {
        for (pattern in suffixPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val digits = match.groupValues[1]
                // Avoid matching years like 2025/2026 unless preceded by explicit account/card/mask token
                if (digits.length == 4 && !(digits.startsWith("20") && pattern == suffixPatterns[0] && !text.contains("x$digits", true) && !text.contains("*$digits", true) && !text.contains("ending $digits", true) && !text.contains("ending in $digits", true) && !text.contains("ending with $digits", true) && !text.contains("a/c $digits", true) && !text.contains("card $digits", true))) {
                    return digits
                }
            }
        }
        return null
    }

    /**
     * Extracts the last 4 digits of the sender or receiver account, card, or phone/UPI handle
     * (strictly avoiding the user's own primary account suffix).
     */
    fun extractCounterpartySuffix(text: String, primaryAccountSuffix: String?): String? {
        val cleanText = BalanceExtractor.stripBalanceClauses(text)

        for (pattern in counterpartyAccountPatterns) {
            for (match in pattern.findAll(cleanText)) {
                val digits = match.groupValues[1]
                if (digits.length == 4 && digits != primaryAccountSuffix) {
                    return digits
                }
            }
        }

        // Secondary fallback: if the alert contains a second distinct masked account suffix (e.g. XX1234 ... XX9876)
        if (!primaryAccountSuffix.isNullOrBlank()) {
            for (match in allMaskedSuffixRegex.findAll(cleanText)) {
                val digits = match.groupValues[1]
                if (digits.length == 4 && digits != primaryAccountSuffix) {
                    return digits
                }
            }
        }
        return null
    }
}

/**
 * 6. Extracts transaction date from notification text when present, falling back to notification postTime.
 */
object TransactionDateExtractor {
    private val monthMap = mapOf(
        "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
        "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11
    )

    private val dmyNumeric = Regex("""\b(0?[1-9]|[12][0-9]|3[01])[-/.](0?[1-9]|1[0-2])[-/.](20\d{2}|\d{2})\b""")
    private val isoDate = Regex("""\b(20\d{2})-(0?[1-9]|1[0-2])-(0?[1-9]|[12][0-9]|3[01])\b""")
    private val dMonY = Regex(
        """(?i)\b(0?[1-9]|[12][0-9]|3[01])[\s\-]?(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s\-]?(20\d{2}|\d{2})\b"""
    )
    private val dMonOnly = Regex(
        """(?i)\b(0?[1-9]|[12][0-9]|3[01])[\s\-](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b"""
    )

    fun extractMillis(text: String, fallbackTimestampMillis: Long): Long {
        val fallbackCal = Calendar.getInstance().apply { timeInMillis = fallbackTimestampMillis }

        var year: Int? = null
        var month: Int? = null
        var day: Int? = null

        val mIso = isoDate.find(text)
        val mDMonY = dMonY.find(text)
        val mDmy = dmyNumeric.find(text)
        val mDMonOnly = dMonOnly.find(text)

        when {
            mIso != null -> {
                year = mIso.groupValues[1].toIntOrNull()
                month = mIso.groupValues[2].toIntOrNull()?.minus(1)
                day = mIso.groupValues[3].toIntOrNull()
            }
            mDMonY != null -> {
                day = mDMonY.groupValues[1].toIntOrNull()
                month = monthMap[mDMonY.groupValues[2].lowercase().take(3)]
                val rawY = mDMonY.groupValues[3].toIntOrNull()
                year = rawY?.let { if (it < 100) 2000 + it else it }
            }
            mDmy != null -> {
                day = mDmy.groupValues[1].toIntOrNull()
                month = mDmy.groupValues[2].toIntOrNull()?.minus(1)
                val rawY = mDmy.groupValues[3].toIntOrNull()
                year = rawY?.let { if (it < 100) 2000 + it else it }
            }
            mDMonOnly != null -> {
                day = mDMonOnly.groupValues[1].toIntOrNull()
                month = monthMap[mDMonOnly.groupValues[2].lowercase().take(3)]
                year = fallbackCal.get(Calendar.YEAR)
            }
        }

        if (year == null || month == null || day == null) return fallbackTimestampMillis
        if (year !in 2015..2100 || month !in 0..11 || day !in 1..31) return fallbackTimestampMillis

        // If extracted calendar day matches notification post day, preserve exact notification timestamp
        if (fallbackCal.get(Calendar.YEAR) == year &&
            fallbackCal.get(Calendar.MONTH) == month &&
            fallbackCal.get(Calendar.DAY_OF_MONTH) == day
        ) {
            return fallbackTimestampMillis
        }

        return Calendar.getInstance().apply {
            isLenient = false
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.let { cal ->
            runCatching { cal.timeInMillis }.getOrDefault(fallbackTimestampMillis)
        }
    }
}

/**
 * 7. Extracts bank reference / UTR / RRN / UPI ref number if present.
 */
object ReferenceExtractor {
    private val patterns = listOf(
        Regex("""(?i)\b(?:upi\s*ref(?:\s*no\.?|\s*id)?|ref(?:\s*no\.?|\s*id)?|refno|utr(?:\s*no\.?)?|rrn|txn(?:\s*id|\s*no\.?)?)[:\s#\-]*([A-Za-z0-9]{6,24})\b"""),
        Regex("""(?i)\b(?:UPI|IMPS|NEFT|RTGS)[/:]([0-9]{8,20})\b"""),
        Regex("""(?i)\b(?:UPI|IMPS|NEFT|RTGS)/(?:P2P|P2M|P2A|CR|DR)/([0-9]{8,20})\b""")
    )

    fun extract(text: String): String? {
        for (pattern in patterns) {
            val match = pattern.find(text)?.groupValues?.getOrNull(1)?.uppercase()
            if (!match.isNullOrBlank() && match.any { it.isDigit() }) {
                return match
            }
        }
        return null
    }
}

/**
 * 8. Detects payment method accurately (including Bank Charges before card/ATM when it's a fee).
 */
object PaymentMethodDetector {
    fun detect(text: String, isBankCharge: Boolean = false): PaymentMethod {
        val lower = text.lowercase()
        if (isBankCharge || TransactionClassifier.bankChargePattern.containsMatchIn(text)) {
            return PaymentMethod.CHARGES
        }
        return when {
            lower.contains("atm") || lower.contains("cash withdrawal") -> PaymentMethod.ATM
            lower.contains("upi") || lower.contains("vpa") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") || lower.contains("bhim") -> PaymentMethod.UPI
            lower.contains("imps") -> PaymentMethod.IMPS
            lower.contains("neft") -> PaymentMethod.NEFT
            lower.contains("rtgs") -> PaymentMethod.RTGS
            lower.contains("credit card") || lower.contains("cc ending") || lower.contains("credit a/c") -> PaymentMethod.CREDIT_CARD
            lower.contains("debit card") || lower.contains("pos ") || lower.contains("dc ending") || lower.contains("card ending") -> PaymentMethod.DEBIT_CARD
            lower.contains("refund") || lower.contains("reversed") || lower.contains("reversal") -> PaymentMethod.REFUND
            lower.contains("salary") || lower.contains("payroll") -> PaymentMethod.SALARY
            lower.contains("interest") || lower.contains("int.pd") -> PaymentMethod.INTEREST
            lower.contains("cash deposit") || lower.contains("cdm") -> PaymentMethod.CASH
            lower.contains("swift") || lower.contains("wire") || lower.contains("forex") -> PaymentMethod.INTERNATIONAL
            lower.contains("transfer") || lower.contains("trf ") -> PaymentMethod.BANK_TRANSFER
            else -> PaymentMethod.OTHER
        }
    }
}
