package com.example.domain.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

enum class AccountType(val displayName: String) {
    SAVINGS("Savings"),
    CURRENT("Current"),
    SALARY("Salary"),
    CREDIT_CARD("Credit Card"),
    CASH_WALLET("Cash Wallet"),
    OTHER("Other")
}

enum class TransactionType(val displayName: String) {
    DEBIT("Expense"),
    CREDIT("Income"),
    INTERNAL_TRANSFER("Transfer")
}

enum class CategoryType(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

enum class PaymentMethod(val displayName: String) {
    UPI("UPI"),
    IMPS("IMPS"),
    NEFT("NEFT"),
    RTGS("RTGS"),
    ATM("ATM"),
    DEBIT_CARD("Debit Card"),
    CREDIT_CARD("Credit Card"),
    BANK_TRANSFER("Bank Transfer"),
    CASH("Cash"),
    REFUND("Refund"),
    INTEREST("Interest"),
    SALARY("Salary"),
    CHARGES("Account Charges"),
    INTERNATIONAL("International"),
    OTHER("Other")
}

enum class TransactionSource(val displayName: String) {
    BANK_NOTIFICATION("Auto-Detected"),
    MANUAL("Manual Entry"),
    IMPORTED("Imported")
}

enum class TransactionStatus(val displayName: String) {
    CONFIRMED("Confirmed"),
    PENDING_REVIEW("Needs Review"),
    IGNORED("Ignored")
}

enum class ThemePreference(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

enum class AnalyticsTimeRange(val displayName: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_THREE_MONTHS("3 Months")
}

enum class TransactionSortOrder(val displayName: String) {
    NEWEST("Newest"),
    HIGHEST_AMOUNT("Highest"),
    LOWEST_AMOUNT("Lowest")
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class CurrencySpec(
    val code: String,
    val symbol: String,
    val name: String,
    val decimalPlaces: Int,
    val useIndianGrouping: Boolean = false
) {
    val minorUnitFactor: Long
        get() = 10.0.pow(decimalPlaces).roundToLong()

    companion object {
        val INR = CurrencySpec("INR", "₹", "Indian Rupee", 2, useIndianGrouping = true)
        val USD = CurrencySpec("USD", "$", "US Dollar", 2)
        val EUR = CurrencySpec("EUR", "€", "Euro", 2)
        val GBP = CurrencySpec("GBP", "£", "British Pound", 2)
        val AED = CurrencySpec("AED", "AED ", "UAE Dirham", 2)
        val SGD = CurrencySpec("SGD", "S$", "Singapore Dollar", 2)
        val JPY = CurrencySpec("JPY", "¥", "Japanese Yen", 0)

        val supportedCurrencies = listOf(INR, USD, EUR, GBP, AED, SGD, JPY)

        fun fromCode(code: String): CurrencySpec {
            return supportedCurrencies.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: INR
        }
    }
}

object MoneyFormatter {
    fun formatMinorUnits(
        amountMinorUnits: Long,
        currencyCode: String,
        showSign: Boolean = false,
        isHidden: Boolean = false
    ): String {
        if (isHidden) return "••••••"
        val spec = CurrencySpec.fromCode(currencyCode)
        val isNegative = amountMinorUnits < 0
        val absMinor = abs(amountMinorUnits)
        val factor = spec.minorUnitFactor
        val major = absMinor / factor
        val minor = absMinor % factor

        val majorFormatted = if (spec.useIndianGrouping) {
            formatIndianInteger(major)
        } else {
            formatInternationalInteger(major)
        }

        val numberPart = if (spec.decimalPlaces == 0) {
            majorFormatted
        } else {
            val minorStr = minor.toString().padStart(spec.decimalPlaces, '0')
            "$majorFormatted.$minorStr"
        }

        val prefix = when {
            isNegative -> "-"
            showSign && amountMinorUnits > 0 -> "+"
            else -> ""
        }
        return "$prefix${spec.symbol}$numberPart"
    }

    fun parseMajorToMinor(input: String, currencyCode: String): Long? {
        val cleaned = input.replace(",", "").replace(" ", "").trim()
        if (cleaned.isEmpty()) return null
        val spec = CurrencySpec.fromCode(currencyCode)
        val parts = cleaned.split(".")
        if (parts.size > 2) return null
        val majorPart = parts[0].ifEmpty { "0" }
        val major = majorPart.toLongOrNull() ?: return null
        if (major < 0) return null
        if (spec.decimalPlaces == 0) return major

        val rawFraction = if (parts.size == 2) parts[1] else ""
        if (rawFraction.any { !it.isDigit() }) return null
        val paddedFraction = rawFraction
            .take(spec.decimalPlaces)
            .padEnd(spec.decimalPlaces, '0')
        val minor = paddedFraction.toLongOrNull() ?: return null
        return major * spec.minorUnitFactor + minor
    }

    fun minorToEditableString(amountMinorUnits: Long, currencyCode: String): String {
        val spec = CurrencySpec.fromCode(currencyCode)
        val absMinor = abs(amountMinorUnits)
        if (spec.decimalPlaces == 0) return absMinor.toString()
        val major = absMinor / spec.minorUnitFactor
        val minor = absMinor % spec.minorUnitFactor
        return if (minor == 0L) {
            major.toString()
        } else {
            "$major.${minor.toString().padStart(spec.decimalPlaces, '0')}"
        }
    }

    private fun formatIndianInteger(value: Long): String {
        val str = value.toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val rest = str.dropLast(3)
        val groupedRest = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$groupedRest,$lastThree"
    }

    private fun formatInternationalInteger(value: Long): String {
        val str = value.toString()
        if (str.length <= 3) return str
        return str.reversed().chunked(3).joinToString(",").reversed()
    }
}

data class BankAccountModel(
    val accountId: Long,
    val bankName: String,
    val accountNickname: String,
    val accountNumberSuffix: String,
    val accountType: AccountType,
    val currencyCode: String,
    val openingBalanceMinorUnits: Long,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class CategoryModel(
    val categoryId: Long,
    val categoryName: String,
    val categoryType: CategoryType,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean,
    val isActive: Boolean
)

data class TransactionModel(
    val transactionId: Long,
    val accountId: Long?,
    val targetAccountId: Long? = null,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val transactionType: TransactionType,
    val recipient: String,
    val sender: String,
    val merchant: String,
    val categoryId: Long?,
    val paymentMethod: PaymentMethod,
    val transactionDateMillis: Long,
    val referenceNumber: String?,
    val sourceType: TransactionSource,
    val transactionStatus: TransactionStatus,
    val description: String,
    val confidenceScore: Float,
    val availableBalanceMinorUnits: Long? = null,
    val counterpartyAccountSuffix: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val displayCounterparty: String
        get() = when {
            merchant.isNotBlank() -> merchant
            transactionType == TransactionType.DEBIT && recipient.isNotBlank() -> recipient
            transactionType == TransactionType.CREDIT && sender.isNotBlank() -> sender
            recipient.isNotBlank() -> recipient
            sender.isNotBlank() -> sender
            !counterpartyAccountSuffix.isNullOrBlank() -> when (transactionType) {
                TransactionType.DEBIT -> "To A/c XX$counterpartyAccountSuffix"
                TransactionType.CREDIT -> "From A/c XX$counterpartyAccountSuffix"
                TransactionType.INTERNAL_TRANSFER -> "A/c XX$counterpartyAccountSuffix"
            }
            else -> "Unknown"
        }

    /**
     * Normalized lookup key for locally learned categorization rules (supports both merchants and person recipients).
     */
    val counterpartyForLearning: String
        get() = when {
            merchant.isNotBlank() -> merchant
            transactionType == TransactionType.DEBIT && recipient.isNotBlank() -> recipient
            transactionType == TransactionType.CREDIT && sender.isNotBlank() -> sender
            else -> ""
        }
}

data class BalanceAdjustmentModel(
    val adjustmentId: Long = 0L,
    val accountId: Long,
    val reportedBalanceMinorUnits: Long,
    val previousEstimatedBalanceMinorUnits: Long,
    val deltaMinorUnits: Long,
    val currencyCode: String,
    val adjustmentDateMillis: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class BudgetModel(
    val budgetId: Long,
    val categoryId: Long,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val alertThresholdPercent: Int,
    val isActive: Boolean
)

data class NotificationRecordModel(
    val processingId: Long,
    val sourcePackage: String,
    val notificationTimestamp: Long,
    val fingerprint: String,
    val processingStatus: String,
    val associatedTransactionId: Long?,
    val summaryLabel: String
)

data class AccountSummary(
    val account: BankAccountModel,
    val estimatedBalanceMinorUnits: Long,
    val periodIncomeMinorUnits: Long,
    val periodExpenseMinorUnits: Long,
    val transactionCount: Int,
    val totalAdjustmentsMinorUnits: Long = 0L,
    val lastReconciledAtMillis: Long? = null
)

data class CurrencyConsolidatedSummary(
    val currencyCode: String,
    val estimatedTrackedBalanceMinorUnits: Long,
    val totalIncomeMinorUnits: Long,
    val totalExpenseMinorUnits: Long,
    val netCashFlowMinorUnits: Long,
    val accountCount: Int
)

data class CategorySpendSummary(
    val category: CategoryModel,
    val amountMinorUnits: Long,
    val transactionCount: Int,
    val percentage: Float
)

data class RecipientSpendSummary(
    val name: String,
    val amountMinorUnits: Long,
    val transactionCount: Int,
    val percentage: Float
)

data class DailySpendPoint(
    val label: String,
    val dayKey: Long,
    val expenseMinorUnits: Long,
    val incomeMinorUnits: Long
)

data class BudgetProgress(
    val budget: BudgetModel,
    val category: CategoryModel,
    val spentMinorUnits: Long,
    val remainingMinorUnits: Long,
    val percentUsed: Float,
    val isOverBudget: Boolean,
    val isWarning: Boolean
)

data class SpendingInsight(
    val title: String,
    val subtitle: String,
    val isPositive: Boolean,
    val isWarning: Boolean = false
)
