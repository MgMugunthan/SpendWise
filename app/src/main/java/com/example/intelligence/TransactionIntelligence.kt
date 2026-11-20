package com.example.intelligence

import com.example.domain.model.BankAccountModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.ConfidenceLevel
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionType
import com.example.security.SpendWiseCryptoManager
import kotlin.math.abs

data class AccountMatchResult(
    val matchedAccount: BankAccountModel?,
    val isAmbiguous: Boolean,
    val reason: String
)

/**
 * 9. Extracts counterparty and separates merchants, bank charges, and unknown person recipients.
 */
object RecipientExtractor {
    private val structuredRailPatterns = listOf(
        // e.g. "Info: UPI/P2P/432198765432/RAHUL SHARMA" or "UPI/432198765432/SWIGGY"
        Regex("""(?i)\b(?:info[:\s]+)?(?:UPI|IMPS|NEFT|RTGS)/(?:(?:P2P|P2M|P2A|CR|DR)/)?(?:[0-9]{6,20}/)?([A-Za-z][A-Za-z0-9 &._'\-]{1,36}?)(?=/|\.|,|;|\bon\b|\bref\b|\bavl\b|\bbal\b|$)"""),
        // e.g. "trf to RAHUL SHARMA Refno 123456789"
        Regex("""(?i)\b(?:trf\s+to|paid\s+to|sent\s+to|transferred\s+to|upi\s+transaction\s+to|upi\s+payment\s+to|payment\s+to|to\s+vpa|to)\s+([A-Za-z0-9][A-Za-z0-9 @&._'\-]{1,42}?)(?=\s*(?:\.|,|;|\bon\b|\bref(?:no)?\b|\butr\b|\brrn\b|\bupi\b|\bvia\b|\bavl\b|\bavail\b|\bbal\b|\bfor\b|\busing\b|\bif\b|\bnot\b|$))"""),
        // e.g. "spent INR 1,899 at AMAZON INDIA on 19-Nov"
        Regex("""(?i)\b(?:spent\s+at|used\s+at|at|towards|merchant)\s+([A-Za-z0-9][A-Za-z0-9 &._'\-]{1,40}?)(?=\s*(?:\.|,|;|\bon\b|\bref(?:no)?\b|\butr\b|\bupi\b|\bvia\b|\bavl\b|\bavail\b|\bbal\b|\bfor\b|\busing\b|$))"""),
        // e.g. "received from PRIYA NAIR" / "credited by ACME CORP"
        Regex("""(?i)\b(?:received\s+from|credited\s+by|credited\s+from|trf\s+from|from)\s+([A-Za-z0-9][A-Za-z0-9 @&._'\-]{1,42}?)(?=\s*(?:\.|,|;|\bon\b|\bref(?:no)?\b|\butr\b|\brrn\b|\bupi\b|\bvia\b|\bavl\b|\bavail\b|\bbal\b|\bfor\b|$))""")
    )

    private val vpaRegex = Regex("""\b([A-Za-z0-9._\-]{2,32})@([A-Za-z]{2,20})\b""")

    private val invalidFragments = setOf(
        "your", "your account", "your a/c", "your ac", "a/c", "ac", "acct", "account",
        "rs", "inr", "upi", "imps", "neft", "rtgs", "bank", "card", "date", "refno",
        "ref", "utr", "sms", "call", "dial", "not you", "if not"
    )

    private val businessTokens = setOf(
        "pvt", "ltd", "limited", "llp", "corp", "corporation", "inc", "store", "stores",
        "mart", "supermarket", "cafe", "coffee", "hotel", "hotels", "resort", "pharmacy",
        "pharma", "hospital", "clinic", "lab", "labs", "diagnostic", "medical", "medicals",
        "enterprises", "enterprise", "services", "service", "technologies", "tech",
        "solutions", "traders", "trading", "retail", "bakery", "restaurant", "restaurants",
        "bistro", "kitchen", "motors", "travels", "travel", "payments", "pay", "bank",
        "school", "college", "university", "academy", "airlines", "airways", "rail",
        "metro", "power", "electricity", "gas", "telecom", "insurance", "finance",
        "finserv", "digital", "media", "works", "infra", "payroll", "india", "online",
        "bazaar", "mall", "cinema", "cinemas", "fuel", "petroleum", "oil", "agency",
        "agencies", "distributors", "electronics", "foods", "jewellers", "textiles"
    )

    fun extract(text: String): String? = extractClassified(text, isBankCharge = false)?.name

    fun extractClassified(
        text: String,
        isBankCharge: Boolean,
        bankName: String? = null
    ): ExtractedCounterparty? {
        val cleanText = BalanceExtractor.stripBalanceClauses(text)

        if (isBankCharge) {
            return ExtractedCounterparty(
                name = buildBankChargeLabel(cleanText, bankName),
                kind = CounterpartyKind.BANK_CHARGE
            )
        }

        val isPosMerchantCue = Regex("""(?i)\b(?:spent\s+at|used\s+at|at\s+[A-Z]|pos\s+txn|upi/p2m/)\b""").containsMatchIn(cleanText)
        val isExplicitP2pCue = Regex("""(?i)\b(?:upi/p2p/|imps/p2a/|trf\s+to|sent\s+to|received\s+from)\b""").containsMatchIn(cleanText)

        for (pattern in structuredRailPatterns) {
            for (match in pattern.findAll(cleanText)) {
                val rawGroup = match.groupValues[1].trim()
                val formatted = cleanCounterpartyCandidate(rawGroup) ?: continue
                val kind = classifyCounterpartyKind(
                    name = formatted,
                    rawToken = rawGroup,
                    isPosMerchantCue = isPosMerchantCue,
                    isExplicitP2pCue = isExplicitP2pCue
                )
                return ExtractedCounterparty(formatted, kind)
            }
        }

        // Fallback: extract name from standalone VPA if present (e.g., rahul.sharma@okaxis)
        val vpaMatch = vpaRegex.find(cleanText)
        if (vpaMatch != null) {
            val handle = vpaMatch.groupValues[1]
            val formatted = formatVpaHandle(handle)
            if (formatted != null) {
                val kind = classifyCounterpartyKind(
                    name = formatted,
                    rawToken = vpaMatch.value,
                    isPosMerchantCue = isPosMerchantCue,
                    isExplicitP2pCue = true
                )
                return ExtractedCounterparty(formatted, kind)
            }
        }

        return null
    }

    private fun cleanCounterpartyCandidate(rawInput: String): String? {
        var candidate = rawInput
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '.', ':', '/', '*')

        // If candidate is a UPI VPA like rahul.sharma@okicici, convert handle cleanly
        if (candidate.contains("@")) {
            val handle = candidate.substringBefore("@")
            candidate = formatVpaHandle(handle) ?: return null
        }

        // Trim trailing account/date/reference noise if regex over-captured
        candidate = candidate
            .replace(Regex("""(?i)\s+(?:a/c|ac|acct|account)\s+[xX*0-9]+$"""), "")
            .replace(Regex("""(?i)\s+ref(?:no)?\s*[A-Za-z0-9]+$"""), "")
            .trim(' ', '-', '.', ':', '/')

        val lower = candidate.lowercase()
        if (candidate.length < 2 || lower in invalidFragments || candidate.all { it.isDigit() }) {
            return null
        }
        // Reject fragments starting with "a/c " or "your "
        if (lower.startsWith("your ") || lower.startsWith("a/c ") || lower.startsWith("ac ")) {
            return null
        }

        return candidate.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isUpperCase() && it.isLetter() } && word.lowercase() !in setOf("sri", "sai", "ram", "raj", "dev", "om")) {
                word
            } else {
                word.lowercase().replaceFirstChar { it.titlecase() }
            }
        }
    }

    private fun formatVpaHandle(handle: String): String? {
        val cleaned = handle
            .replace(Regex("[._\\-]+"), " ")
            .replace(Regex("\\d+$"), "") // drop trailing digits like rahul98 -> rahul
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 2 || cleaned.all { it.isDigit() }) return null
        return cleaned.split(" ").joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    private fun classifyCounterpartyKind(
        name: String,
        rawToken: String,
        isPosMerchantCue: Boolean,
        isExplicitP2pCue: Boolean
    ): CounterpartyKind {
        val lower = name.lowercase()
        val words = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

        // 1. Known merchant catalog match -> MERCHANT
        if (TransactionCategorizer.matchesKnownMerchant(lower)) {
            return CounterpartyKind.MERCHANT
        }
        // 2. Business/entity suffix or merchant keyword -> MERCHANT
        if (words.any { it in businessTokens }) {
            return CounterpartyKind.MERCHANT
        }
        // 3. Explicit merchant VPA / POS card payment -> MERCHANT
        if (rawToken.contains("p2m", ignoreCase = true) || isPosMerchantCue) {
            return CounterpartyKind.MERCHANT
        }
        // 4. Looks like a human person name (1..4 alphabetic/initial words, e.g. "Rahul Sharma", "Priya Nair", "S. Kumar")
        val looksLikePersonName = words.size in 1..4 &&
            words.all { it.all { ch -> ch.isLetter() } } &&
            words.any { it.length >= 3 }

        if (looksLikePersonName || isExplicitP2pCue) {
            return CounterpartyKind.PERSON
        }

        return CounterpartyKind.UNKNOWN
    }

    private fun buildBankChargeLabel(text: String, bankName: String?): String {
        val lower = text.lowercase()
        val specific = when {
            lower.contains("sms") -> "SMS Alert Charges"
            lower.contains("annual maintenance") || Regex("\\bamc\\b").containsMatchIn(lower) -> "Annual Maintenance Charge"
            lower.contains("debit card") || lower.contains("card annual") || lower.contains("card renewal") || lower.contains("card issuance") -> "Debit Card Fee"
            lower.contains("minimum balance") || lower.contains("non-maintenance") || lower.contains("non maintenance") || Regex("\\b(mab|amb)\\b").containsMatchIn(lower) -> "Minimum Balance Penalty"
            lower.contains("atm") -> "ATM Usage Charges"
            lower.contains("cheque") -> "Cheque Return Charges"
            lower.contains("ecs") || lower.contains("nach") -> "Auto-Debit Return Charges"
            lower.contains("penalty") || lower.contains("penal") || lower.contains("late") -> "Bank Penalty / Fee"
            lower.contains("gst") -> "GST & Bank Charges"
            else -> "Bank Service Charges"
        }
        return if (!bankName.isNullOrBlank()) "$bankName · $specific" else specific
    }
}

/**
 * 10. Matches extracted bank + 4-digit suffix against active user accounts.
 *     Never guesses between multiple active accounts when ambiguous.
 */
object AccountMatcher {
    fun match(
        bankName: String?,
        suffix: String?,
        activeAccounts: List<BankAccountModel>,
        currencyCode: String
    ): AccountMatchResult {
        if (activeAccounts.isEmpty()) {
            return AccountMatchResult(null, false, "No active bank accounts configured")
        }

        // 1. Exact bank + suffix match
        if (!bankName.isNullOrBlank() && !suffix.isNullOrBlank()) {
            val exact = activeAccounts.filter {
                it.accountNumberSuffix == suffix &&
                    it.bankName.equals(bankName, ignoreCase = true)
            }
            if (exact.size == 1) return AccountMatchResult(exact.first(), false, "Matched bank & suffix XX$suffix")
            if (exact.size > 1) return AccountMatchResult(null, true, "Multiple accounts match $bankName XX$suffix")
        }

        // 2. Suffix-only match (unique across active accounts)
        if (!suffix.isNullOrBlank()) {
            val bySuffix = activeAccounts.filter { it.accountNumberSuffix == suffix }
            if (bySuffix.size == 1) return AccountMatchResult(bySuffix.first(), false, "Matched unique account suffix XX$suffix")
            if (bySuffix.size > 1) return AccountMatchResult(null, true, "Multiple accounts end with XX$suffix")
        }

        // 3. Bank-only match when user has exactly one active account at that bank in the same currency
        if (!bankName.isNullOrBlank()) {
            val byBank = activeAccounts.filter {
                it.bankName.equals(bankName, ignoreCase = true) &&
                    it.currencyCode.equals(currencyCode, ignoreCase = true)
            }
            if (byBank.size == 1) return AccountMatchResult(byBank.first(), false, "Matched sole $bankName account")
            if (byBank.size > 1) return AccountMatchResult(null, true, "Multiple $bankName accounts exist; suffix missing")
        }

        // 4. Sole active account in matching currency when notification omitted both bank and suffix
        //    (e.g., UPI app payment alert "Paid ₹1 to Rahul" when the user tracks 1 INR bank account)
        if (bankName.isNullOrBlank() && suffix.isNullOrBlank()) {
            val byCurrency = activeAccounts.filter { it.currencyCode.equals(currencyCode, ignoreCase = true) }
            if (byCurrency.size == 1) {
                return AccountMatchResult(byCurrency.first(), false, "Matched sole active $currencyCode account")
            }
        }

        return AccountMatchResult(null, false, "Could not confidently identify account")
    }
}

/**
 * 11. Local rule-based categorizer:
 *     1. Internal Transfer -> Transfers
 *     2. Locally learned user rules (for merchants OR person recipients)
 *     3. Bank Charges (SMS alert fees, AMC, card fees, penalties, ATM fees, GST) -> Bank Charges
 *     4. Income categories (Salary, Refund, Interest, Cash Deposit, Money Received)
 *     5. ATM Withdrawal (cash withdrawal, non-fee)
 *     6. Known merchant categories (Food, Shopping, Bills, etc.)
 *     7. Unknown person recipients -> Person-to-Person (instead of dumping into Other!)
 *     8. Fallback -> Other
 */
object TransactionCategorizer {
    private val expenseKeywordRules: List<Pair<String, List<String>>> = listOf(
        "Food" to listOf("swiggy", "zomato", "ubereats", "doordash", "dominos", "pizza", "mcdonald", "kfc", "starbucks", "cafe", "bakery", "chai", "tea", "burger king", "subway"),
        "Restaurants" to listOf("restaurant", "bistro", "bar & grill", "dining", "diner", "kitchen", "dhaba"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "instamart", "dmart", "supermarket", "grocery", "groceries", "fresh", "mart", "more retail", "reliance fresh", "jiomart", "milk"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "croma", "decathlon", "ikea", "zara", "h&m", "nike", "shoe", "mall", "store", "reliance digital"),
        "Transport" to listOf("uber", "ola", "rapido", "metro", "irctc", "redbus", "auto", "cab", "taxi", "parking", "fastag", "toll", "namma yatri"),
        "Fuel" to listOf("hpcl", "iocl", "bpcl", "indian oil", "bharat petroleum", "shell", "petrol", "diesel", "fuel", "nayara"),
        "Electricity" to listOf("bescom", "tangedco", "mahavitaran", "tata power", "adani electricity", "electricity", "power bill", "cesc", "bses"),
        "Bills" to listOf("airtel", "jio", "vi ", "vodafone", "bsnl", "broadband", "act fibernet", "water bill", "gas bill", "indane", "bharatgas", "recharge", "billdesk", "dth", "tata play"),
        "Rent" to listOf("rent", "landlord", "nobroker", "housing", "properties"),
        "Entertainment" to listOf("bookmyshow", "pvr", "inox", "cinema", "movie", "steam", "playstation", "xbox", "bowling"),
        "Subscriptions" to listOf("netflix", "spotify", "youtube", "hotstar", "disney", "prime video", "apple.com", "google one", "icloud", "chatgpt", "audible"),
        "Healthcare" to listOf("apollo", "pharmeasy", "1mg", "netmeds", "hospital", "clinic", "pharmacy", "medical", "diagnostic", "lab", "doctor", "medplus"),
        "Education" to listOf("udemy", "coursera", "school", "college", "tuition", "university", "books", "byju", "unacademy"),
        "Travel" to listOf("indigo", "air india", "vistara", "makemytrip", "goibibo", "agoda", "airbnb", "hotel", "resort", "flight", "cleartrip", "akasa"),
        "ATM Withdrawal" to listOf("atm", "cash withdrawal")
    )

    private val incomeKeywordRules: List<Pair<String, List<String>>> = listOf(
        "Salary" to listOf("salary", "payroll", "stipend", "wages"),
        "Refund" to listOf("refund", "reversal", "reversed", "cashback"),
        "Interest" to listOf("interest", "int.pd", "dividend"),
        "Cash Deposit" to listOf("cash deposit", "cdm")
    )

    fun matchesKnownMerchant(normalizedLower: String): Boolean {
        return expenseKeywordRules.any { (_, keywords) ->
            keywords.any { normalizedLower.contains(it) }
        } || incomeKeywordRules.any { (_, keywords) ->
            keywords.any { normalizedLower.contains(it) }
        }
    }

    fun categorize(
        merchant: String?,
        recipient: String?,
        sender: String?,
        counterpartyKind: CounterpartyKind,
        rawText: String,
        transactionType: TransactionType,
        paymentMethod: PaymentMethod,
        isBankCharge: Boolean,
        categories: List<CategoryModel>,
        userLearnedRules: Map<String, Long>
    ): Long? {
        if (transactionType == TransactionType.INTERNAL_TRANSFER) {
            return categories.firstOrNull { it.categoryName.equals("Transfers", ignoreCase = true) }?.categoryId
        }

        // 1. User-learned local rule takes top priority across merchant, recipient, and sender
        val candidateKeys = listOfNotNull(merchant, recipient, sender)
            .map { normalizeMerchantKey(it) }
            .filter { it.isNotEmpty() && it != "unknown" }
            .distinct()
        for (key in candidateKeys) {
            val learnedCatId = userLearnedRules[key]
            if (learnedCatId != null && categories.any { it.categoryId == learnedCatId }) {
                return learnedCatId
            }
        }

        val cleanText = BalanceExtractor.stripBalanceClauses(rawText)
        val haystack = "${merchant.orEmpty()} ${recipient.orEmpty()} ${sender.orEmpty()} $cleanText".lowercase()

        // 2. Bank charges (SMS fees, AMC, debit card fees, penalties, ATM fees, GST) -> Bank Charges
        if (transactionType == TransactionType.DEBIT &&
            (isBankCharge || paymentMethod == PaymentMethod.CHARGES || TransactionClassifier.bankChargePattern.containsMatchIn(cleanText))
        ) {
            categories.firstOrNull { it.categoryName.equals("Bank Charges", ignoreCase = true) }
                ?.let { return it.categoryId }
        }

        // 3. Income categorization
        if (transactionType == TransactionType.CREDIT) {
            for ((catName, keywords) in incomeKeywordRules) {
                if (keywords.any { haystack.contains(it) }) {
                    categories.firstOrNull { it.categoryName.equals(catName, ignoreCase = true) }
                        ?.let { return it.categoryId }
                }
            }
            // If no sender or merchant name was identified, place in Other Income / Other
            val hasNamedSender = !sender.isNullOrBlank() && !sender.equals("Unknown", ignoreCase = true)
            val hasNamedMerchant = !merchant.isNullOrBlank() && !merchant.equals("Unknown", ignoreCase = true)
            if (!hasNamedSender && !hasNamedMerchant) {
                return categories.firstOrNull { it.categoryName.equals("Other Income", ignoreCase = true) }?.categoryId
                    ?: categories.firstOrNull { it.categoryName.equals("Other", ignoreCase = true) }?.categoryId
            }
            return categories.firstOrNull { it.categoryName.equals("Money Received", ignoreCase = true) }?.categoryId
                ?: categories.firstOrNull { it.categoryName.equals("Other Income", ignoreCase = true) }?.categoryId
        }

        // 4. ATM Cash Withdrawal
        if (paymentMethod == PaymentMethod.ATM) {
            categories.firstOrNull { it.categoryName.equals("ATM Withdrawal", ignoreCase = true) }
                ?.let { return it.categoryId }
        }

        // 5. Known merchant keyword rules
        for ((catName, keywords) in expenseKeywordRules) {
            if (keywords.any { haystack.contains(it) }) {
                categories.firstOrNull { it.categoryName.equals(catName, ignoreCase = true) }
                    ?.let { return it.categoryId }
            }
        }

        // 6. Person-to-person transfer (unknown person name recognized as recipient -> Person-to-Person)
        //    If there is NO sender/receiver name at all, skip directly to Other.
        val hasNamedRecipient = !recipient.isNullOrBlank() && !recipient.equals("Unknown", ignoreCase = true)
        if (hasNamedRecipient && (counterpartyKind == CounterpartyKind.PERSON || merchant.isNullOrBlank())) {
            categories.firstOrNull { it.categoryName.equals("Person-to-Person", ignoreCase = true) }
                ?.let { return it.categoryId }
        }

        return categories.firstOrNull { it.categoryName.equals("Other", ignoreCase = true) }?.categoryId
    }

    fun normalizeMerchantKey(name: String?): String {
        return name.orEmpty()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/**
 * 12. Multi-layered duplicate detection:
 *     (a) Notification SHA-256 fingerprint
 *     (b) Exact Bank Reference / UTR match
 *     (c) Time-bounded (15 minute) fallback match on Account + Amount + Type + Counterparty
 *     Legitimate identical amounts with distinct reference numbers or outside the window are preserved.
 */
object DuplicateDetector {
    private const val FALLBACK_DUPLICATE_WINDOW_MILLIS = 15L * 60L * 1000L // 15 minutes

    fun computeNotificationFingerprint(
        sourcePackage: String,
        title: String,
        body: String,
        minuteBucketTimestamp: Long
    ): String {
        val bucket = minuteBucketTimestamp / 60_000L
        val canonical = "${sourcePackage.lowercase()}|${title.trim().lowercase()}|${body.trim().lowercase()}|$bucket"
        return SpendWiseCryptoManager.sha256Hex(canonical)
    }

    fun isFallbackDuplicate(
        candidateAmountMinorUnits: Long,
        candidateType: TransactionType,
        candidateAccountId: Long?,
        candidateCounterparty: String?,
        candidateTimestamp: Long,
        candidateReference: String?,
        recentExisting: List<TransactionModel>
    ): Boolean {
        val normalizedCandidate = TransactionCategorizer.normalizeMerchantKey(candidateCounterparty)

        return recentExisting.any { existing ->
            if (existing.amountMinorUnits != candidateAmountMinorUnits) return@any false
            if (existing.transactionType != candidateType) return@any false

            // If both carry distinct bank reference numbers, they are genuinely separate transactions
            if (!candidateReference.isNullOrBlank() && !existing.referenceNumber.isNullOrBlank()) {
                return@any candidateReference.equals(existing.referenceNumber, ignoreCase = true)
            }

            val timeDiff = abs(existing.transactionDateMillis - candidateTimestamp)
            if (timeDiff > FALLBACK_DUPLICATE_WINDOW_MILLIS) return@any false

            val sameAccount = candidateAccountId == null || existing.accountId == null || existing.accountId == candidateAccountId
            val existingParty = TransactionCategorizer.normalizeMerchantKey(existing.displayCounterparty)
            val sameCounterparty = normalizedCandidate.isEmpty() ||
                normalizedCandidate == "unknown" ||
                existingParty.isEmpty() ||
                existingParty == "unknown" ||
                normalizedCandidate == existingParty

            sameAccount && sameCounterparty
        }
    }
}

/**
 * 13. Scores confidence based on signal strength and ensures:
 *     - Valid completed notifications with an unambiguous account match (including ₹1 payments!)
 *       reach HIGH confidence and confirm reliably.
 *     - Pending/processing payments or unmatched/ambiguous accounts are NEVER marked CONFIRMED.
 */
object TransactionValidator {
    fun scoreCandidate(
        baseClassificationScore: Float,
        hasAmount: Boolean,
        hasBank: Boolean,
        hasAccountSuffix: Boolean,
        hasCounterparty: Boolean,
        hasReference: Boolean,
        hasAvailableBalance: Boolean = false,
        accountMatched: Boolean,
        accountAmbiguous: Boolean,
        isPending: Boolean = false
    ): Pair<Float, ConfidenceLevel> {
        if (!hasAmount) return 0.10f to ConfidenceLevel.LOW
        var score = baseClassificationScore + 0.30f // valid debit/credit + positive amount
        if (accountMatched && !accountAmbiguous) score += 0.22f
        if (hasBank) score += 0.06f
        if (hasAccountSuffix) score += 0.06f
        if (hasCounterparty) score += 0.05f
        if (hasReference) score += 0.05f
        if (hasAvailableBalance) score += 0.04f
        if (accountAmbiguous) score -= 0.22f

        val clamped = if (isPending) {
            score.coerceIn(0.50f, 0.68f)
        } else {
            score.coerceIn(0f, 1f)
        }

        val level = when {
            isPending -> ConfidenceLevel.MEDIUM
            clamped >= 0.80f && accountMatched && !accountAmbiguous -> ConfidenceLevel.HIGH
            clamped >= 0.50f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        return clamped to level
    }
}
