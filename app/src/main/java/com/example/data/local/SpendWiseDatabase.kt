package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.BalanceAdjustmentDao
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.MerchantRuleDao
import com.example.data.local.dao.NotificationProcessingDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BalanceAdjustmentEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.NotificationProcessingEntity
import com.example.data.local.entity.TransactionEntity
import com.example.domain.model.CategoryType

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        NotificationProcessingEntity::class,
        BudgetEntity::class,
        MerchantRuleEntity::class,
        BalanceAdjustmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SpendWiseDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationProcessingDao(): NotificationProcessingDao
    abstract fun budgetDao(): BudgetDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun balanceAdjustmentDao(): BalanceAdjustmentDao

    companion object {
        @Volatile
        private var INSTANCE: SpendWiseDatabase? = null

        /**
         * Safe, non-destructive migration from v1 to v2:
         * - Preserves all existing accounts, transactions, categories, budgets, rules, and notification records.
         * - Adds optional `availableBalanceMinorUnits` column to `transactions`.
         * - Creates `balance_adjustments` table for dated manual balance reconciliation records.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN availableBalanceMinorUnits INTEGER DEFAULT NULL"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `balance_adjustments` (
                        `adjustmentId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `accountId` INTEGER NOT NULL,
                        `reportedBalanceMinorUnits` INTEGER NOT NULL,
                        `previousEstimatedBalanceMinorUnits` INTEGER NOT NULL,
                        `deltaMinorUnits` INTEGER NOT NULL,
                        `currencyCode` TEXT NOT NULL,
                        `adjustmentDateMillis` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`accountId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_balance_adjustments_accountId` ON `balance_adjustments` (`accountId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_balance_adjustments_adjustmentDateMillis` ON `balance_adjustments` (`adjustmentDateMillis`)"
                )
            }
        }

        /**
         * Safe, non-destructive migration from v2 to v3:
         * - Adds optional `counterpartyAccountSuffix` (last 4 digits of sender/receiver account/card/phone)
         *   to `transactions` without touching any existing rows.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN counterpartyAccountSuffix TEXT DEFAULT NULL"
                )
            }
        }

        fun getInstance(context: Context): SpendWiseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpendWiseDatabase::class.java,
                    "spendwise_finance.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        // Strict 3-colour brand system for categories: Emerald, Blue, and Orange shades only.
        val defaultCategories: List<CategoryEntity> = listOf(
            // Expense Categories (Orange & Blue shades)
            CategoryEntity(categoryName = "Food", categoryType = CategoryType.EXPENSE.name, iconKey = "food", colorHex = 0xFFEA580C),
            CategoryEntity(categoryName = "Restaurants", categoryType = CategoryType.EXPENSE.name, iconKey = "restaurant", colorHex = 0xFFF97316),
            CategoryEntity(categoryName = "Groceries", categoryType = CategoryType.EXPENSE.name, iconKey = "groceries", colorHex = 0xFF059669),
            CategoryEntity(categoryName = "Shopping", categoryType = CategoryType.EXPENSE.name, iconKey = "shopping", colorHex = 0xFF2563EB),
            CategoryEntity(categoryName = "Transport", categoryType = CategoryType.EXPENSE.name, iconKey = "transport", colorHex = 0xFF3B82F6),
            CategoryEntity(categoryName = "Fuel", categoryType = CategoryType.EXPENSE.name, iconKey = "fuel", colorHex = 0xFFC2410C),
            CategoryEntity(categoryName = "Bills", categoryType = CategoryType.EXPENSE.name, iconKey = "bills", colorHex = 0xFF1D4ED8),
            CategoryEntity(categoryName = "Electricity", categoryType = CategoryType.EXPENSE.name, iconKey = "electricity", colorHex = 0xFFEA580C),
            CategoryEntity(categoryName = "Rent", categoryType = CategoryType.EXPENSE.name, iconKey = "rent", colorHex = 0xFF2563EB),
            CategoryEntity(categoryName = "Entertainment", categoryType = CategoryType.EXPENSE.name, iconKey = "entertainment", colorHex = 0xFFF97316),
            CategoryEntity(categoryName = "Healthcare", categoryType = CategoryType.EXPENSE.name, iconKey = "healthcare", colorHex = 0xFF059669),
            CategoryEntity(categoryName = "Education", categoryType = CategoryType.EXPENSE.name, iconKey = "education", colorHex = 0xFF3B82F6),
            CategoryEntity(categoryName = "Travel", categoryType = CategoryType.EXPENSE.name, iconKey = "travel", colorHex = 0xFF1D4ED8),
            CategoryEntity(categoryName = "Subscriptions", categoryType = CategoryType.EXPENSE.name, iconKey = "subscriptions", colorHex = 0xFFEA580C),
            CategoryEntity(categoryName = "Person-to-Person", categoryType = CategoryType.EXPENSE.name, iconKey = "person", colorHex = 0xFF2563EB),
            CategoryEntity(categoryName = "Bank Charges", categoryType = CategoryType.EXPENSE.name, iconKey = "charges", colorHex = 0xFFC2410C),
            CategoryEntity(categoryName = "Transfers", categoryType = CategoryType.TRANSFER.name, iconKey = "transfer", colorHex = 0xFF2563EB),
            CategoryEntity(categoryName = "ATM Withdrawal", categoryType = CategoryType.EXPENSE.name, iconKey = "atm", colorHex = 0xFFC2410C),
            CategoryEntity(categoryName = "Other", categoryType = CategoryType.EXPENSE.name, iconKey = "other", colorHex = 0xFF3B82F6),
            // Income Categories (Emerald shades)
            CategoryEntity(categoryName = "Salary", categoryType = CategoryType.INCOME.name, iconKey = "salary", colorHex = 0xFF059669),
            CategoryEntity(categoryName = "Refund", categoryType = CategoryType.INCOME.name, iconKey = "refund", colorHex = 0xFF10B981),
            CategoryEntity(categoryName = "Money Received", categoryType = CategoryType.INCOME.name, iconKey = "received", colorHex = 0xFF047857),
            CategoryEntity(categoryName = "Interest", categoryType = CategoryType.INCOME.name, iconKey = "interest", colorHex = 0xFF2563EB),
            CategoryEntity(categoryName = "Cash Deposit", categoryType = CategoryType.INCOME.name, iconKey = "cash", colorHex = 0xFF059669),
            CategoryEntity(categoryName = "Other Income", categoryType = CategoryType.INCOME.name, iconKey = "other_income", colorHex = 0xFF10B981)
        )

        /**
         * Idempotently ensures all default categories exist (including newly introduced categories
         * like "Bank Charges" and "Person-to-Person" after a v1 -> v2 upgrade) via IGNORE conflict
         * strategy, without touching existing user accounts, transactions, or custom categories.
         */
        suspend fun ensureDefaultCategories(db: SpendWiseDatabase) {
            db.categoryDao().insertCategories(defaultCategories)
        }
    }
}
