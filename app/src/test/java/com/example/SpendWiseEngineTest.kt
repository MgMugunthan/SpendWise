package com.example

import com.example.domain.engine.FinancialCalculator
import com.example.domain.model.AccountType
import com.example.domain.model.BalanceAdjustmentModel
import com.example.domain.model.BankAccountModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategoryType
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.intelligence.AccountMatcher
import com.example.intelligence.ParseOutcome
import com.example.intelligence.TransactionDateExtractor
import com.example.intelligence.TransactionParserEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SpendWiseEngineTest {

    private val engine = TransactionParserEngine()

    private val accounts = listOf(
        BankAccountModel(1L, "State Bank of India", "Main Savings", "1234", AccountType.SAVINGS, "INR", 1000000L, true, 0L, 0L),
        BankAccountModel(2L, "HDFC Bank", "Salary", "5678", AccountType.SALARY, "INR", 500000L, true, 0L, 0L),
        BankAccountModel(3L, "ICICI Bank", "Coral Card", "9012", AccountType.CREDIT_CARD, "INR", 0L, true, 0L, 0L),
        BankAccountModel(4L, "Chase", "US Checking", "4321", AccountType.CURRENT, "USD", 200000L, true, 0L, 0L)
    )

    private val categories = listOf(
        CategoryModel(1L, "Food", CategoryType.EXPENSE, "food", 0xFFF97316, true, true),
        CategoryModel(2L, "Shopping", CategoryType.EXPENSE, "shopping", 0xFF2563EB, true, true),
        CategoryModel(3L, "ATM Withdrawal", CategoryType.EXPENSE, "atm", 0xFF64748B, true, true),
        CategoryModel(4L, "Other", CategoryType.EXPENSE, "other", 0xFF64748B, true, true),
        CategoryModel(5L, "Refund", CategoryType.INCOME, "refund", 0xFF14B8A6, true, true),
        CategoryModel(6L, "Salary", CategoryType.INCOME, "salary", 0xFF059669, true, true),
        CategoryModel(7L, "Money Received", CategoryType.INCOME, "received", 0xFF22C55E, true, true),
        CategoryModel(8L, "Person-to-Person", CategoryType.EXPENSE, "person", 0xFF2563EB, true, true),
        CategoryModel(9L, "Bank Charges", CategoryType.EXPENSE, "charges", 0xFFC2410C, true, true),
        CategoryModel(10L, "Interest", CategoryType.INCOME, "interest", 0xFF10B981, true, true),
        CategoryModel(11L, "Rent", CategoryType.EXPENSE, "rent", 0xFFEA580C, true, true),
        CategoryModel(12L, "Cash Deposit", CategoryType.INCOME, "cash", 0xFF059669, true, true),
        CategoryModel(13L, "Other Income", CategoryType.INCOME, "other_income", 0xFF059669, true, true)
    )

    @Test
    fun `parses real-world SBI 1 rupee UPI SMS without currency symbol and confirms transaction`() {
        val result = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Dear UPI user A/C X1234 debited by 1.0 on date 20Nov26 trf to RAHUL SHARMA Refno 632412345678. If not u? call 1800111109.",
            timestampMillis = 1_763_600_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        )

        assertTrue("Expected Parsed outcome for ₹1 SBI UPI SMS, got $result", result is ParseOutcome.Parsed)
        val parsed = result as ParseOutcome.Parsed
        assertEquals(TransactionType.DEBIT, parsed.transaction.transactionType)
        assertEquals(100L, parsed.transaction.amountMinorUnits) // ₹1.00 = 100 paise
        assertEquals("INR", parsed.transaction.currencyCode)
        assertEquals(1L, parsed.transaction.accountId)
        assertEquals(PaymentMethod.UPI, parsed.transaction.paymentMethod)
        assertEquals("632412345678", parsed.transaction.referenceNumber)
        assertEquals("Rahul Sharma", parsed.transaction.recipient)
        assertEquals("", parsed.transaction.merchant)
        assertEquals(8L, parsed.transaction.categoryId) // Person-to-Person, NOT Other!
        assertEquals(TransactionStatus.CONFIRMED, parsed.transaction.transactionStatus)
    }

    @Test
    fun `parses 1 rupee debit and credit messages containing available balance independently`() {
        val debitWithAvail = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "AD-SBIBNK",
            body = "Your A/c XX1234 is debited by Rs.1.00 on 19-11-2026 via UPI to SWIGGY. Ref No 789456123. Available balance is Rs.12,499.00.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        )
        assertTrue("Debit with Available balance must parse as transaction, got $debitWithAvail", debitWithAvail is ParseOutcome.Parsed)
        val parsedDebit = debitWithAvail as ParseOutcome.Parsed
        assertEquals(TransactionType.DEBIT, parsedDebit.transaction.transactionType)
        assertEquals(100L, parsedDebit.transaction.amountMinorUnits)
        assertEquals(1249900L, parsedDebit.transaction.availableBalanceMinorUnits)
        assertEquals("Swiggy", parsedDebit.transaction.merchant)
        assertEquals(1L, parsedDebit.transaction.categoryId) // Food
        assertEquals(TransactionStatus.CONFIRMED, parsedDebit.transaction.transactionStatus)

        val creditWithAvail = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-HDFCBK",
            body = "HDFC Bank: A/c XX5678 credited with INR 1 via IMPS from VERIFICATION TEST. Ref UTR11223344. Avl Bal: Rs.5,001.50.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        )
        assertTrue("Credit with Avl Bal must parse as transaction, got $creditWithAvail", creditWithAvail is ParseOutcome.Parsed)
        val parsedCredit = creditWithAvail as ParseOutcome.Parsed
        assertEquals(TransactionType.CREDIT, parsedCredit.transaction.transactionType)
        assertEquals(100L, parsedCredit.transaction.amountMinorUnits)
        assertEquals(500150L, parsedCredit.transaction.availableBalanceMinorUnits)
        assertEquals(2L, parsedCredit.transaction.accountId)
        assertEquals(PaymentMethod.IMPS, parsedCredit.transaction.paymentMethod)
        assertEquals(TransactionStatus.CONFIRMED, parsedCredit.transaction.transactionStatus)
    }

    @Test
    fun `rejects balance-only messages, OTPs, promotional offers, and failed transactions`() {
        val ignoredSamples = listOf(
            "Available balance is Rs.15,240.00 in your A/c XX1234.",
            "Balance enquiry: Available balance in A/c XX1234 is Rs.15,240.00.",
            "Your A/c XX1234 current balance is INR 12,000.00 as of 20-11-2026.",
            "Mini statement for A/c XX1234: Bal Rs.12,000.00.",
            "Your OTP is 482910 for Rs.500 transaction on A/c XX1234. Do not share.",
            "Insufficient balance in A/c XX1234 for transaction of Rs.500.",
            "Transaction of Rs.1.00 on A/c XX1234 failed due to technical error.",
            "Your debit card payment of Rs.450.00 at SWIGGY was declined.",
            "Congratulations! Pre-approved loan offer of Rs.2,00,000. Apply now!"
        )

        ignoredSamples.forEach { sample ->
            val outcome = engine.parse(
                sourcePackage = "com.google.android.apps.messaging",
                title = "Bank Alert",
                body = sample,
                timestampMillis = 1_763_500_000_000L,
                activeAccounts = accounts,
                categories = categories,
                userLearnedRules = emptyMap(),
                recentTransactionsForDuplicateCheck = emptyList(),
                existingReferenceMatch = false,
                existingFingerprintMatch = false
            )
            assertTrue("Expected Ignored for: $sample, got $outcome", outcome is ParseOutcome.Ignored)
        }
    }

    @Test
    fun `parses NEFT, RTGS, IMPS, ATM, card, deposit, refund, and interest formats`() {
        val cases = listOf(
            Triple("HDFC Bank", "HDFC Bank: A/c XX5678 credited Rs.25,000.50 via NEFT from ACME CORP. Ref UTR99887766.", PaymentMethod.NEFT),
            Triple("SBI", "A/c XX1234 credited with Rs.2,50,000.00 via RTGS from BUILDER ESCROW. Ref RTGS2026001.", PaymentMethod.RTGS),
            Triple("SBI ATM", "ATM withdrawal Rs.1000 from A/c XX1234. Ref 77889900.", PaymentMethod.ATM),
            Triple("ICICI Bank", "ICICI Credit Card ending 9012 spent INR 1,899.00 at AMAZON INDIA on 19-Nov. Ref TXN55443322.", PaymentMethod.CREDIT_CARD),
            Triple("SBI", "Cash deposited Rs.5,000.00 in A/c XX1234 at Branch. Ref DEP12345.", PaymentMethod.CASH),
            Triple("SBI", "Refund received Rs.350.00 to A/c XX1234 from Amazon. Ref REF112233.", PaymentMethod.OTHER),
            Triple("SBI", "Your A/c XX1234 is credited with Rs.412.00 towards quarterly savings interest. Avl Bal Rs.15,412.00.", PaymentMethod.BANK_TRANSFER)
        )

        cases.forEach { (title, body, expectedMethod) ->
            val outcome = engine.parse(
                sourcePackage = "com.google.android.apps.messaging",
                title = title,
                body = body,
                timestampMillis = 1_763_500_000_000L,
                activeAccounts = accounts,
                categories = categories,
                userLearnedRules = emptyMap(),
                recentTransactionsForDuplicateCheck = emptyList(),
                existingReferenceMatch = false,
                existingFingerprintMatch = false
            )
            assertTrue("Expected Parsed for: $body, got $outcome", outcome is ParseOutcome.Parsed)
            val parsed = outcome as ParseOutcome.Parsed
            assertEquals("Payment method mismatch for: $body", expectedMethod, parsed.transaction.paymentMethod)
        }

        // Verify Interest category specifically
        val interestOutcome = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBI",
            body = "Your A/c XX1234 is credited with Rs.412.00 towards quarterly savings interest. Avl Bal Rs.15,412.00.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        ) as ParseOutcome.Parsed
        assertEquals(10L, interestOutcome.transaction.categoryId) // Interest
        assertEquals(41200L, interestOutcome.transaction.amountMinorUnits)
        assertEquals(1541200L, interestOutcome.transaction.availableBalanceMinorUnits)
    }

    @Test
    fun `detects bank charges including SMS fees, AMC, card fees, penalties, ATM charges, and GST`() {
        val chargeMessages = listOf(
            "Your A/c XX1234 is debited by Rs.17.70 towards SMS alert charges incl. GST on 19-11-2026. Avl Bal Rs.12,481.30.",
            "Your SBI A/c XX1234 debited Rs.236.00 for Debit Card Annual Maintenance Charge (AMC). Ref CHG9911.",
            "Your HDFC A/c XX5678 is debited by Rs.590.00 towards minimum balance penalty and GST.",
            "A/c XX1234 debited Rs.23.60 for ATM usage charge beyond free limit. Avl Bal Rs.9,976.40.",
            "Your A/c XX5678 debited Rs.295.00 towards cheque bounce charge."
        )

        chargeMessages.forEach { msg ->
            val outcome = engine.parse(
                sourcePackage = "com.google.android.apps.messaging",
                title = "Bank Alert",
                body = msg,
                timestampMillis = 1_763_500_000_000L,
                activeAccounts = accounts,
                categories = categories,
                userLearnedRules = emptyMap(),
                recentTransactionsForDuplicateCheck = emptyList(),
                existingReferenceMatch = false,
                existingFingerprintMatch = false
            )
            assertTrue("Expected Parsed for bank charge: $msg, got $outcome", outcome is ParseOutcome.Parsed)
            val parsed = outcome as ParseOutcome.Parsed
            assertEquals(TransactionType.DEBIT, parsed.transaction.transactionType)
            assertEquals(PaymentMethod.CHARGES, parsed.transaction.paymentMethod)
            assertEquals("Expected Bank Charges category for: $msg", 9L, parsed.transaction.categoryId)
            assertEquals(TransactionStatus.CONFIRMED, parsed.transaction.transactionStatus)
        }
    }

    @Test
    fun `separates unknown person recipients from merchants and respects locally learned user rules`() {
        val p2pOutcome = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBI Alert",
            body = "Your A/c XX1234 debited Rs.4,500.00 via UPI to PRIYA NAIR. Ref 4455667788.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        ) as ParseOutcome.Parsed

        assertEquals("Priya Nair", p2pOutcome.transaction.recipient)
        assertEquals("", p2pOutcome.transaction.merchant)
        assertEquals(8L, p2pOutcome.transaction.categoryId) // Person-to-Person

        // Now user teaches SpendWise locally that "Priya Nair" is their landlord (Rent)
        val learnedOutcome = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBI Alert",
            body = "Your A/c XX1234 debited Rs.4,500.00 via UPI to PRIYA NAIR. Ref 4455667799.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = mapOf("priya nair" to 11L), // Rent
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        ) as ParseOutcome.Parsed

        assertEquals("Priya Nair", learnedOutcome.transaction.recipient)
        assertEquals("", learnedOutcome.transaction.merchant)
        assertEquals(11L, learnedOutcome.transaction.categoryId) // Learned Rent!
    }

    @Test
    fun `extracts sender and receiver last 4 digits and routes unnamed transactions to Other`() {
        // Unnamed debit with receiver account suffix -> Other + counterpartyAccountSuffix = 9876
        val unnamedDebit = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Your A/c XX1234 debited Rs.250.00 via UPI to A/c XX9876. Ref 55667788.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        ) as ParseOutcome.Parsed
        assertEquals("9876", unnamedDebit.transaction.counterpartyAccountSuffix)
        assertEquals("To A/c XX9876", unnamedDebit.transaction.displayCounterparty)
        assertEquals(4L, unnamedDebit.transaction.categoryId) // Other
        assertEquals(TransactionStatus.CONFIRMED, unnamedDebit.transaction.transactionStatus)

        // Unnamed credit with sender account suffix -> Other Income + counterpartyAccountSuffix = 4321
        val unnamedCredit = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Your A/c XX1234 credited Rs.500.00 via IMPS from A/c XX4321. Ref 88776655.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        ) as ParseOutcome.Parsed
        assertEquals("4321", unnamedCredit.transaction.counterpartyAccountSuffix)
        assertEquals("From A/c XX4321", unnamedCredit.transaction.displayCounterparty)
        assertEquals(13L, unnamedCredit.transaction.categoryId) // Other Income

        // UPI phone VPA suffix extraction (9876543210@ybl -> 3210)
        val phoneVpaDebit = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Your A/c XX1234 debited Rs.120.00 via UPI to 9876543210@ybl. Ref 99887711.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        ) as ParseOutcome.Parsed
        assertEquals("3210", phoneVpaDebit.transaction.counterpartyAccountSuffix)

        // Named recipient with parenthesized receiver suffix (A/c XX8899)
        val namedWithSuffix = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Your A/c XX1234 debited Rs.300.00 via UPI to RAHUL SHARMA (A/c XX8899). Ref 33445566.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        ) as ParseOutcome.Parsed
        assertEquals("8899", namedWithSuffix.transaction.counterpartyAccountSuffix)
        assertEquals(1L, namedWithSuffix.transaction.accountId) // User's own XX1234 still matched properly
    }

    @Test
    fun `blocks duplicate notifications while allowing distinct references and flags pending alerts for review`() {
        val dupRef = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBI",
            body = "Your A/c XX1234 debited Rs.500.00 to SWIGGY. Ref No 123456789.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = true,
            existingFingerprintMatch = false
        )
        assertTrue(dupRef is ParseOutcome.Duplicate)

        // Fallback window duplicate without reference number
        val existingTx = TransactionModel(
            transactionId = 10L,
            accountId = 1L,
            targetAccountId = null,
            amountMinorUnits = 100L,
            currencyCode = "INR",
            transactionType = TransactionType.DEBIT,
            recipient = "Rahul Sharma",
            sender = "",
            merchant = "",
            categoryId = 8L,
            paymentMethod = PaymentMethod.UPI,
            transactionDateMillis = 1_763_500_000_000L,
            referenceNumber = null,
            sourceType = TransactionSource.BANK_NOTIFICATION,
            transactionStatus = TransactionStatus.CONFIRMED,
            description = "",
            confidenceScore = 0.9f,
            createdAt = 1_763_500_000_000L,
            updatedAt = 1_763_500_000_000L
        )
        val fallbackDup = engine.parse(
            sourcePackage = "com.sbi.lotusintouch",
            title = "SBI",
            body = "Your A/c XX1234 debited Rs.1.00 via UPI to Rahul Sharma.",
            timestampMillis = 1_763_500_120_000L, // 2 minutes later
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = listOf(existingTx),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        )
        assertTrue("Expected fallback duplicate block within 15 min window", fallbackDup is ParseOutcome.Duplicate)

        // Distinct reference numbers for two back-to-back ₹1 transfers MUST NOT be flagged as duplicate
        val existingWithRef = existingTx.copy(referenceNumber = "111111111111")
        val secondDistinctTransfer = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Dear UPI user A/C X1234 debited by 1.0 trf to RAHUL SHARMA Refno 222222222222.",
            timestampMillis = 1_763_500_120_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = listOf(existingWithRef),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        )
        assertTrue("Distinct reference numbers must not be treated as duplicate", secondDistinctTransfer is ParseOutcome.Parsed)

        // Pending / processing notification must stay PENDING_REVIEW (never CONFIRMED)
        val pendingOutcome = engine.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBI Alert",
            body = "UPI payment of Rs.250.00 from A/c XX1234 to SWIGGY is pending processing. Ref 77665544.",
            timestampMillis = 1_763_500_000_000L,
            activeAccounts = accounts,
            categories = categories,
            userLearnedRules = emptyMap(),
            recentTransactionsForDuplicateCheck = emptyList(),
            existingReferenceMatch = false,
            existingFingerprintMatch = false
        )
        assertTrue(pendingOutcome is ParseOutcome.Parsed)
        assertEquals(TransactionStatus.PENDING_REVIEW, (pendingOutcome as ParseOutcome.Parsed).transaction.transactionStatus)
    }

    @Test
    fun `account matcher handles sole active account safely and flags ambiguous duplicates`() {
        val soleInrAccount = listOf(accounts[0])
        val soleMatch = AccountMatcher.match(null, null, soleInrAccount, "INR")
        assertNotNull(soleMatch.matchedAccount)
        assertEquals(1L, soleMatch.matchedAccount?.accountId)

        // Multiple INR accounts with no bank or suffix -> must NOT guess
        val multiNoHint = AccountMatcher.match(null, null, accounts, "INR")
        assertNull(multiNoHint.matchedAccount)

        // Ambiguous duplicate suffix
        val duplicateSuffixAccounts = listOf(
            accounts[0],
            accounts[0].copy(accountId = 99L, accountNickname = "Second SBI")
        )
        val ambiguousMatch = AccountMatcher.match("State Bank of India", "1234", duplicateSuffixAccounts, "INR")
        assertTrue(ambiguousMatch.isAmbiguous)
        assertNull(ambiguousMatch.matchedAccount)
    }

    @Test
    fun `financial calculator excludes pending transactions, excludes internal transfers from income-expense, applies dated adjustments, and separates currencies`() {
        val now = System.currentTimeMillis()
        val txs = listOf(
            // ₹5,000 confirmed salary credit to HDFC
            TransactionModel(1L, 2L, null, 500000L, "INR", TransactionType.CREDIT, "", "Employer", "Salary", 6L, PaymentMethod.NEFT, now, null, TransactionSource.MANUAL, TransactionStatus.CONFIRMED, "", 1f, null, null, now, now),
            // ₹2,000 confirmed internal transfer from HDFC (2) to SBI (1)
            TransactionModel(2L, 2L, 1L, 200000L, "INR", TransactionType.INTERNAL_TRANSFER, "SBI", "HDFC", "Self Transfer", null, PaymentMethod.IMPS, now, null, TransactionSource.MANUAL, TransactionStatus.CONFIRMED, "", 1f, null, null, now, now),
            // ₹500 confirmed food debit from SBI (1)
            TransactionModel(3L, 1L, null, 50000L, "INR", TransactionType.DEBIT, "", "", "Swiggy", 1L, PaymentMethod.UPI, now, null, TransactionSource.MANUAL, TransactionStatus.CONFIRMED, "", 1f, null, null, now, now),
            // ₹999 PENDING_REVIEW debit from SBI (1) — MUST NOT affect balance or expenses!
            TransactionModel(4L, 1L, null, 99900L, "INR", TransactionType.DEBIT, "Unknown", "", "", 4L, PaymentMethod.UPI, now, null, TransactionSource.BANK_NOTIFICATION, TransactionStatus.PENDING_REVIEW, "", 0.5f, null, null, now, now),
            // ₹777 IGNORED credit to HDFC (2) — MUST NOT affect balance or income!
            TransactionModel(5L, 2L, null, 77700L, "INR", TransactionType.CREDIT, "", "Promo", "", 7L, PaymentMethod.OTHER, now, null, TransactionSource.BANK_NOTIFICATION, TransactionStatus.IGNORED, "", 0.2f, null, null, now, now),
            // $100 confirmed debit from Chase USD account (4)
            TransactionModel(6L, 4L, null, 10000L, "USD", TransactionType.DEBIT, "", "", "Coffee", 1L, PaymentMethod.DEBIT_CARD, now, null, TransactionSource.MANUAL, TransactionStatus.CONFIRMED, "", 1f, null, null, now, now)
        )

        // Dated manual reconciliation adjustment of -₹150.00 on SBI (account 1)
        val adjustments = listOf(
            BalanceAdjustmentModel(
                adjustmentId = 1L,
                accountId = 1L,
                previousEstimatedBalanceMinorUnits = 1150000L,
                reportedBalanceMinorUnits = 1135000L,
                deltaMinorUnits = -15000L,
                currencyCode = "INR",
                note = "Manual bank statement check",
                adjustmentDateMillis = now,
                createdAt = now
            )
        )

        val accountSummaries = FinancialCalculator.calculateAccountSummaries(
            accounts = accounts,
            allTransactions = txs,
            periodStartMillis = now - 10_000L,
            periodEndMillis = now + 10_000L,
            balanceAdjustments = adjustments
        )

        val sbi = accountSummaries.first { it.account.accountId == 1L }
        val hdfc = accountSummaries.first { it.account.accountId == 2L }
        val chase = accountSummaries.first { it.account.accountId == 4L }

        // SBI: 10,000 + 2,000 (transfer in) - 500 (confirmed debit) - 150 (adjustment) = 11,350.00 INR
        // (₹999 pending debit is excluded!)
        assertEquals(1135000L, sbi.estimatedBalanceMinorUnits)
        assertEquals(-15000L, sbi.totalAdjustmentsMinorUnits)
        assertEquals(1000000L, sbi.account.openingBalanceMinorUnits) // Starting balance untouched!

        // HDFC: 5,000 + 5,000 (credit) - 2,000 (transfer out) = 8,000.00 INR (₹777 ignored excluded!)
        assertEquals(800000L, hdfc.estimatedBalanceMinorUnits)

        // Chase: 2,000 - 100 = 1,900.00 USD
        assertEquals(190000L, chase.estimatedBalanceMinorUnits)

        val consolidated = FinancialCalculator.calculateConsolidatedByCurrency(accountSummaries, txs)
        assertEquals(2, consolidated.size)

        val inr = consolidated.first { it.currencyCode == "INR" }
        assertEquals(500000L, inr.totalIncomeMinorUnits) // Internal transfer & ignored excluded!
        assertEquals(50000L, inr.totalExpenseMinorUnits) // Internal transfer, pending, & adjustment excluded!
        assertEquals(1935000L, inr.estimatedTrackedBalanceMinorUnits) // 11,350 + 8,000

        val usd = consolidated.first { it.currencyCode == "USD" }
        assertEquals(10000L, usd.totalExpenseMinorUnits)
        assertEquals(190000L, usd.estimatedTrackedBalanceMinorUnits)
    }

    @Test
    fun `extracts embedded transaction dates accurately across Indian bank formats`() {
        val fallback = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 20, 14, 30, 0) }.timeInMillis

        val parsedDmy = TransactionDateExtractor.extractMillis("debited by Rs.500 on 17-11-2026", fallback)
        val cal1 = Calendar.getInstance().apply { timeInMillis = parsedDmy }
        assertEquals(2026, cal1.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, cal1.get(Calendar.MONTH))
        assertEquals(17, cal1.get(Calendar.DAY_OF_MONTH))

        val parsedAlpha = TransactionDateExtractor.extractMillis("debited by 1.0 on date 05Oct26 trf to RAHUL", fallback)
        val cal2 = Calendar.getInstance().apply { timeInMillis = parsedAlpha }
        assertEquals(2026, cal2.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal2.get(Calendar.MONTH))
        assertEquals(5, cal2.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `money formatter handles Indian grouping, minor units, and zero-decimal currencies`() {
        assertEquals("₹1,24,500.50", MoneyFormatter.formatMinorUnits(12450050L, "INR"))
        assertEquals("$1,250.00", MoneyFormatter.formatMinorUnits(125000L, "USD"))
        assertEquals("¥5,000", MoneyFormatter.formatMinorUnits(5000L, "JPY"))
        assertEquals("••••••", MoneyFormatter.formatMinorUnits(12450050L, "INR", isHidden = true))
    }
}
