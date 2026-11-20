package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BalanceAdjustmentEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.NotificationProcessingEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isActive DESC, createdAt ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isActive DESC, createdAt ASC")
    suspend fun getAllAccountsOnce(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isActive = 1")
    suspend fun getActiveAccountsOnce(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = :isActive, updatedAt = :updatedAt WHERE accountId = :accountId")
    suspend fun setAccountActive(accountId: Long, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY isDefault DESC, categoryName ASC")
    fun observeActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY categoryId ASC")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE isDefault = 0 AND categoryId = :categoryId")
    suspend fun deleteCustomCategory(categoryId: Long)

    @Query("DELETE FROM categories WHERE isDefault = 0")
    suspend fun deleteAllCustomCategories()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transactionDateMillis DESC, transactionId DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transactionDateMillis DESC, transactionId DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE referenceNumber = :referenceNumber LIMIT 1")
    suspend fun findByReferenceNumber(referenceNumber: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE amountMinorUnits = :amountMinorUnits
          AND transactionType = :transactionType
          AND transactionDateMillis BETWEEN :fromMillis AND :toMillis
        """
    )
    suspend fun findPotentialDuplicates(
        amountMinorUnits: Long,
        transactionType: String,
        fromMillis: Long,
        toMillis: Long
    ): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE transactionId = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface NotificationProcessingDao {
    @Query("SELECT * FROM notification_processing_records ORDER BY notificationTimestamp DESC LIMIT 50")
    fun observeRecentRecords(): Flow<List<NotificationProcessingEntity>>

    @Query("SELECT * FROM notification_processing_records WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): NotificationProcessingEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: NotificationProcessingEntity): Long

    @Query("DELETE FROM notification_processing_records")
    suspend fun deleteAllRecords()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY budgetId ASC")
    fun observeAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY budgetId ASC")
    suspend fun getAllBudgetsOnce(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE budgetId = :budgetId")
    suspend fun deleteBudgetById(budgetId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun getAllRulesOnce(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE normalizedMerchant = :normalized LIMIT 1")
    suspend fun findByMerchant(normalized: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: MerchantRuleEntity): Long

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAllRules()
}

@Dao
interface BalanceAdjustmentDao {
    @Query("SELECT * FROM balance_adjustments ORDER BY adjustmentDateMillis DESC, adjustmentId DESC")
    fun observeAllAdjustments(): Flow<List<BalanceAdjustmentEntity>>

    @Query("SELECT * FROM balance_adjustments ORDER BY adjustmentDateMillis DESC, adjustmentId DESC")
    suspend fun getAllAdjustmentsOnce(): List<BalanceAdjustmentEntity>

    @Query("SELECT * FROM balance_adjustments WHERE accountId = :accountId ORDER BY adjustmentDateMillis DESC, adjustmentId DESC")
    suspend fun getAdjustmentsForAccountOnce(accountId: Long): List<BalanceAdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(adjustment: BalanceAdjustmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustments(adjustments: List<BalanceAdjustmentEntity>): List<Long>

    @Query("DELETE FROM balance_adjustments WHERE adjustmentId = :adjustmentId")
    suspend fun deleteAdjustmentById(adjustmentId: Long)

    @Query("DELETE FROM balance_adjustments")
    suspend fun deleteAllAdjustments()
}
