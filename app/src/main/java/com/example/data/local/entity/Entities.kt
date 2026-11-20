package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AccountType
import com.example.domain.model.BalanceAdjustmentModel
import com.example.domain.model.BankAccountModel
import com.example.domain.model.BudgetModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategoryType
import com.example.domain.model.NotificationRecordModel
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["bankName", "accountNumberSuffix"]),
        Index(value = ["isActive"])
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val accountId: Long = 0L,
    val bankName: String,
    val accountNickname: String,
    val accountNumberSuffix: String,
    val accountType: String,
    val currencyCode: String,
    val openingBalanceMinorUnits: Long,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["categoryName", "categoryType"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val categoryId: Long = 0L,
    val categoryName: String,
    val categoryType: String,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean = true,
    val isActive: Boolean = true
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["transactionDateMillis"]),
        Index(value = ["referenceNumber"]),
        Index(value = ["transactionStatus"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val transactionId: Long = 0L,
    val accountId: Long?,
    val targetAccountId: Long? = null,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val transactionType: String,
    val recipient: String,
    val sender: String,
    val merchant: String,
    val categoryId: Long?,
    val paymentMethod: String,
    val transactionDateMillis: Long,
    val referenceNumber: String?,
    val sourceType: String,
    val transactionStatus: String,
    val description: String,
    val confidenceScore: Float,
    val availableBalanceMinorUnits: Long? = null,
    val counterpartyAccountSuffix: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notification_processing_records",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["notificationTimestamp"])
    ]
)
data class NotificationProcessingEntity(
    @PrimaryKey(autoGenerate = true) val processingId: Long = 0L,
    val sourcePackage: String,
    val notificationTimestamp: Long,
    val fingerprint: String,
    val processingStatus: String,
    val associatedTransactionId: Long? = null,
    val summaryLabel: String = ""
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val budgetId: Long = 0L,
    val categoryId: Long,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val startDateMillis: Long = 0L,
    val endDateMillis: Long = 0L,
    val alertThresholdPercent: Int = 80,
    val isActive: Boolean = true
)

@Entity(
    tableName = "merchant_rules",
    indices = [Index(value = ["normalizedMerchant"], unique = true)]
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val ruleId: Long = 0L,
    val normalizedMerchant: String,
    val categoryId: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "balance_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["adjustmentDateMillis"])
    ]
)
data class BalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val adjustmentId: Long = 0L,
    val accountId: Long,
    val reportedBalanceMinorUnits: Long,
    val previousEstimatedBalanceMinorUnits: Long,
    val deltaMinorUnits: Long,
    val currencyCode: String,
    val adjustmentDateMillis: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// Mappers between Room entities and clean domain models
fun AccountEntity.toDomain() = BankAccountModel(
    accountId = accountId,
    bankName = bankName,
    accountNickname = accountNickname,
    accountNumberSuffix = accountNumberSuffix,
    accountType = runCatching { AccountType.valueOf(accountType) }.getOrDefault(AccountType.SAVINGS),
    currencyCode = currencyCode,
    openingBalanceMinorUnits = openingBalanceMinorUnits,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun BankAccountModel.toEntity() = AccountEntity(
    accountId = accountId,
    bankName = bankName,
    accountNickname = accountNickname,
    accountNumberSuffix = accountNumberSuffix.filter { it.isDigit() }.takeLast(4),
    accountType = accountType.name,
    currencyCode = currencyCode,
    openingBalanceMinorUnits = openingBalanceMinorUnits,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CategoryEntity.toDomain() = CategoryModel(
    categoryId = categoryId,
    categoryName = categoryName,
    categoryType = runCatching { CategoryType.valueOf(categoryType) }.getOrDefault(CategoryType.EXPENSE),
    iconKey = iconKey,
    colorHex = colorHex,
    isDefault = isDefault,
    isActive = isActive
)

fun TransactionEntity.toDomain() = TransactionModel(
    transactionId = transactionId,
    accountId = accountId,
    targetAccountId = targetAccountId,
    amountMinorUnits = amountMinorUnits,
    currencyCode = currencyCode,
    transactionType = runCatching { TransactionType.valueOf(transactionType) }.getOrDefault(TransactionType.DEBIT),
    recipient = recipient,
    sender = sender,
    merchant = merchant,
    categoryId = categoryId,
    paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.OTHER),
    transactionDateMillis = transactionDateMillis,
    referenceNumber = referenceNumber,
    sourceType = runCatching { TransactionSource.valueOf(sourceType) }.getOrDefault(TransactionSource.MANUAL),
    transactionStatus = runCatching { TransactionStatus.valueOf(transactionStatus) }.getOrDefault(TransactionStatus.CONFIRMED),
    description = description,
    confidenceScore = confidenceScore,
    availableBalanceMinorUnits = availableBalanceMinorUnits,
    counterpartyAccountSuffix = counterpartyAccountSuffix,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TransactionModel.toEntity() = TransactionEntity(
    transactionId = transactionId,
    accountId = accountId,
    targetAccountId = targetAccountId,
    amountMinorUnits = amountMinorUnits,
    currencyCode = currencyCode,
    transactionType = transactionType.name,
    recipient = recipient,
    sender = sender,
    merchant = merchant,
    categoryId = categoryId,
    paymentMethod = paymentMethod.name,
    transactionDateMillis = transactionDateMillis,
    referenceNumber = referenceNumber?.takeIf { it.isNotBlank() },
    sourceType = sourceType.name,
    transactionStatus = transactionStatus.name,
    description = description,
    confidenceScore = confidenceScore,
    availableBalanceMinorUnits = availableBalanceMinorUnits,
    counterpartyAccountSuffix = counterpartyAccountSuffix?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 },
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun BudgetEntity.toDomain() = BudgetModel(
    budgetId = budgetId,
    categoryId = categoryId,
    amountMinorUnits = amountMinorUnits,
    currencyCode = currencyCode,
    alertThresholdPercent = alertThresholdPercent,
    isActive = isActive
)

fun NotificationProcessingEntity.toDomain() = NotificationRecordModel(
    processingId = processingId,
    sourcePackage = sourcePackage,
    notificationTimestamp = notificationTimestamp,
    fingerprint = fingerprint,
    processingStatus = processingStatus,
    associatedTransactionId = associatedTransactionId,
    summaryLabel = summaryLabel
)

fun BalanceAdjustmentEntity.toDomain() = BalanceAdjustmentModel(
    adjustmentId = adjustmentId,
    accountId = accountId,
    reportedBalanceMinorUnits = reportedBalanceMinorUnits,
    previousEstimatedBalanceMinorUnits = previousEstimatedBalanceMinorUnits,
    deltaMinorUnits = deltaMinorUnits,
    currencyCode = currencyCode,
    adjustmentDateMillis = adjustmentDateMillis,
    note = note,
    createdAt = createdAt
)

fun BalanceAdjustmentModel.toEntity() = BalanceAdjustmentEntity(
    adjustmentId = adjustmentId,
    accountId = accountId,
    reportedBalanceMinorUnits = reportedBalanceMinorUnits,
    previousEstimatedBalanceMinorUnits = previousEstimatedBalanceMinorUnits,
    deltaMinorUnits = deltaMinorUnits,
    currencyCode = currencyCode,
    adjustmentDateMillis = adjustmentDateMillis,
    note = note,
    createdAt = createdAt
)
