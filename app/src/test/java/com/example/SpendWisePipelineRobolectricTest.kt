package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SpendWiseDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.SpendWiseRepository
import com.example.domain.engine.FinancialCalculator
import com.example.domain.model.AccountType
import com.example.domain.model.BankAccountModel
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.intelligence.ParseOutcome
import com.example.ui.SpendWiseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpendWisePipelineRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: SpendWiseDatabase
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: SpendWiseRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDataStore = SettingsDataStore(context)
        repository = SpendWiseRepository(database, settingsDataStore)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `end-to-end 1 rupee notification pipeline persists atomically, blocks duplicates, and updates ViewModel balance`() = runBlocking {
        repository.initializeWorkspaceIfNeeded()

        val now = System.currentTimeMillis()
        val sbiId = repository.saveAccount(
            BankAccountModel(
                accountId = 0L,
                bankName = "State Bank of India",
                accountNickname = "SBI Main",
                accountNumberSuffix = "1234",
                accountType = AccountType.SAVINGS,
                currencyCode = "INR",
                openingBalanceMinorUnits = 1_000_000L, // ₹10,000.00
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        )

        // 1. Live ₹1 SBI UPI notification with currency omitted & available balance included
        val outcome1 = repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Dear UPI user A/C X1234 debited by 1.0 on date 20Nov26 trf to RAHUL SHARMA Refno 632412345678. Available balance is Rs.9,999.00.",
            timestampMillis = now,
            enforcePackageAllowList = true
        )

        assertTrue("Expected Parsed for ₹1 UPI notification, got $outcome1", outcome1 is ParseOutcome.Parsed)
        val parsed = outcome1 as ParseOutcome.Parsed
        assertEquals(TransactionStatus.CONFIRMED, parsed.transaction.transactionStatus)
        assertEquals(100L, parsed.transaction.amountMinorUnits)
        assertEquals(999_900L, parsed.transaction.availableBalanceMinorUnits)
        assertEquals(sbiId, parsed.transaction.accountId)
        assertEquals("Rahul Sharma", parsed.transaction.recipient)
        assertEquals("", parsed.transaction.merchant)

        // Verify reactive DB Flow & ViewModel UI state update
        val viewModel = SpendWiseViewModel(repository)
        val state = viewModel.uiState.first { !it.isLoading && it.allTransactions.isNotEmpty() }
        val sbiSummary = state.accountSummaries.first { it.account.accountId == sbiId }
        assertEquals(999_900L, sbiSummary.estimatedBalanceMinorUnits) // ₹10,000 - ₹1 = ₹9,999.00
        assertEquals(1, state.recentTransactions.size)
        assertEquals(100L, state.recentTransactions.first().amountMinorUnits)

        // 2. Duplicate re-delivery (same Refno from a bank app package) MUST be blocked
        val duplicateOutcome = repository.processIncomingNotification(
            sourcePackage = "com.sbi.lotusintouch",
            title = "SBI YONO",
            body = "A/c XX1234 debited Rs.1.00 UPI to RAHUL SHARMA Refno 632412345678.",
            timestampMillis = now + 5_000L,
            enforcePackageAllowList = true
        )
        assertTrue("Duplicate notification with same Refno must be blocked", duplicateOutcome is ParseOutcome.Duplicate)
        assertEquals(1, database.transactionDao().getAllTransactionsOnce().size)

        // 3. Balance-only notification MUST NOT create a transaction
        val balanceOnlyOutcome = repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIBNK",
            body = "Balance enquiry: Available balance in A/c XX1234 is Rs.9,999.00.",
            timestampMillis = now + 10_000L,
            enforcePackageAllowList = true
        )
        assertTrue("Balance-only alert must be ignored", balanceOnlyOutcome is ParseOutcome.Ignored)
        assertEquals(1, database.transactionDao().getAllTransactionsOnce().size)

        // 4. Pending transaction must NOT change confirmed balance
        val pendingOutcome = repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "UPI payment of Rs.50.00 from A/c XX1234 to ZOMATO is pending processing. Ref 999988887777.",
            timestampMillis = now + 15_000L,
            enforcePackageAllowList = true
        )
        assertTrue(pendingOutcome is ParseOutcome.Parsed)
        assertEquals(TransactionStatus.PENDING_REVIEW, (pendingOutcome as ParseOutcome.Parsed).transaction.transactionStatus)

        val updatedBalance = FinancialCalculator.calculateSingleAccountEstimatedBalance(
            account = database.accountDao().getAllAccountsOnce().first().let {
                BankAccountModel(it.accountId, it.bankName, it.accountNickname, it.accountNumberSuffix, AccountType.valueOf(it.accountType), it.currencyCode, it.openingBalanceMinorUnits, it.isActive, it.createdAt, it.updatedAt)
            },
            allTransactions = repository.transactionsFlow.first(),
            balanceAdjustments = repository.balanceAdjustmentsFlow.first()
        )
        assertEquals(999_900L, updatedBalance) // Pending ₹50 did NOT alter confirmed balance!
    }

    @Test
    fun `manual balance reconciliation records dated adjustment without modifying starting balance or transactions`() = runBlocking {
        repository.initializeWorkspaceIfNeeded()
        val now = System.currentTimeMillis()

        val accountId = repository.saveAccount(
            BankAccountModel(
                accountId = 0L,
                bankName = "HDFC Bank",
                accountNickname = "HDFC Salary",
                accountNumberSuffix = "5678",
                accountType = AccountType.SALARY,
                currencyCode = "INR",
                openingBalanceMinorUnits = 500_000L, // ₹5,000.00 starting balance
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        )

        // Record a ₹500.00 debit -> estimated balance becomes ₹4,500.00
        repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-HDFCBK",
            body = "HDFC Bank: A/c XX5678 debited Rs.500.00 via UPI to SWIGGY. Ref 44556677.",
            timestampMillis = now,
            enforcePackageAllowList = true
        )

        // Reconcile balance to ₹4,350.00 (₹150.00 missing offline spend)
        val adjustment = repository.reconcileAccountBalance(
            accountId = accountId,
            reportedBalanceMinorUnits = 435_000L,
            note = "Checked HDFC mobile app balance"
        )

        assertEquals(450_000L, adjustment.previousEstimatedBalanceMinorUnits)
        assertEquals(435_000L, adjustment.reportedBalanceMinorUnits)
        assertEquals(-15_000L, adjustment.deltaMinorUnits)

        // Verify opening balance and transaction count are untouched
        val storedAccount = database.accountDao().getAllAccountsOnce().first { it.accountId == accountId }
        assertEquals(500_000L, storedAccount.openingBalanceMinorUnits)
        assertEquals(1, database.transactionDao().getAllTransactionsOnce().size)

        // Verify recalculated summaries reflect ₹4,350.00 while expense stays ₹500.00 (not inflated by ₹150)
        val accounts = repository.accountsFlow.first()
        val txs = repository.transactionsFlow.first()
        val adjustments = repository.balanceAdjustmentsFlow.first()
        val summaries = FinancialCalculator.calculateAccountSummaries(
            accounts = accounts,
            allTransactions = txs,
            periodStartMillis = now - 60_000L,
            periodEndMillis = now + 60_000L,
            balanceAdjustments = adjustments
        )
        assertEquals(435_000L, summaries.first().estimatedBalanceMinorUnits)
        assertEquals(-15_000L, summaries.first().totalAdjustmentsMinorUnits)

        val consolidated = FinancialCalculator.calculateConsolidatedByCurrency(summaries, txs).first()
        assertEquals(435_000L, consolidated.estimatedTrackedBalanceMinorUnits)
        assertEquals(50_000L, consolidated.totalExpenseMinorUnits)
        assertEquals(0L, consolidated.totalIncomeMinorUnits)
    }

    @Test
    fun `learns user category correction for unknown human recipient locally across subsequent alerts`() = runBlocking {
        repository.initializeWorkspaceIfNeeded()
        val now = System.currentTimeMillis()
        repository.saveAccount(
            BankAccountModel(
                accountId = 0L,
                bankName = "State Bank of India",
                accountNickname = "SBI",
                accountNumberSuffix = "1234",
                accountType = AccountType.SAVINGS,
                currencyCode = "INR",
                openingBalanceMinorUnits = 2_000_000L,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        )

        val categories = repository.categoriesFlow.first()
        val p2pId = categories.first { it.categoryName == "Person-to-Person" }.categoryId
        val rentId = categories.first { it.categoryName == "Rent" }.categoryId

        // First transfer to unknown person "Arjun Mehta" -> auto-assigned Person-to-Person
        val firstOutcome = repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Dear UPI user A/C X1234 debited by 15000.0 on date 01Nov26 trf to ARJUN MEHTA Refno 100000000001.",
            timestampMillis = now,
            enforcePackageAllowList = true
        ) as ParseOutcome.Parsed
        assertEquals(p2pId, firstOutcome.transaction.categoryId)
        assertEquals("Arjun Mehta", firstOutcome.transaction.recipient)

        // User corrects category to Rent with local learning enabled
        repository.saveTransaction(
            transaction = firstOutcome.transaction.copy(categoryId = rentId),
            learnMerchantCategory = true
        )

        // Second transfer to ARJUN MEHTA -> automatically categorized as Rent!
        val secondOutcome = repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Dear UPI user A/C X1234 debited by 15000.0 on date 01Dec26 trf to ARJUN MEHTA Refno 100000000002.",
            timestampMillis = now + 3_600_000L,
            enforcePackageAllowList = true
        ) as ParseOutcome.Parsed
        assertEquals(rentId, secondOutcome.transaction.categoryId)
        assertEquals(TransactionType.DEBIT, secondOutcome.transaction.transactionType)
    }

    @Test
    fun `safe Room migration from v1 to v3 preserves existing accounts and transactions without data loss`() = runBlocking {
        val dbName = "spendwise_migration_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create authentic schema v1 database directly via SQLiteDatabase
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `accounts` (
                `accountId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bankName` TEXT NOT NULL,
                `accountNickname` TEXT NOT NULL,
                `accountNumberSuffix` TEXT NOT NULL,
                `accountType` TEXT NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `openingBalanceMinorUnits` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `categoryId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `categoryType` TEXT NOT NULL,
                `iconKey` TEXT NOT NULL,
                `colorHex` INTEGER NOT NULL,
                `isDefault` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_categoryName_categoryType` ON `categories` (`categoryName`, `categoryType`)")
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transactions` (
                `transactionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `accountId` INTEGER,
                `targetAccountId` INTEGER,
                `amountMinorUnits` INTEGER NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `transactionType` TEXT NOT NULL,
                `recipient` TEXT NOT NULL,
                `sender` TEXT NOT NULL,
                `merchant` TEXT NOT NULL,
                `categoryId` INTEGER,
                `paymentMethod` TEXT NOT NULL,
                `transactionDateMillis` INTEGER NOT NULL,
                `referenceNumber` TEXT,
                `sourceType` TEXT NOT NULL,
                `transactionStatus` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `confidenceScore` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`accountId`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`categoryId`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionDateMillis` ON `transactions` (`transactionDateMillis`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_referenceNumber` ON `transactions` (`referenceNumber`)")
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notification_processing_records` (
                `recordId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourcePackage` TEXT NOT NULL,
                `notificationTimestamp` INTEGER NOT NULL,
                `fingerprint` TEXT NOT NULL,
                `processingStatus` TEXT NOT NULL,
                `associatedTransactionId` INTEGER,
                `summaryLabel` TEXT NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_processing_records_fingerprint` ON `notification_processing_records` (`fingerprint`)")
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
                `budgetId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `amountMinorUnits` INTEGER NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `alertThresholdPercent` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`categoryId`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `merchant_rules` (
                `normalizedMerchant` TEXT NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`normalizedMerchant`)
            )
            """.trimIndent()
        )

        // Seed existing v1 user data
        sqlite.execSQL(
            """
            INSERT INTO accounts (accountId, bankName, accountNickname, accountNumberSuffix, accountType, currencyCode, openingBalanceMinorUnits, isActive, createdAt, updatedAt)
            VALUES (1, 'State Bank of India', 'Legacy SBI', '1234', 'SAVINGS', 'INR', 2500000, 1, 1000, 1000)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            INSERT INTO transactions (transactionId, accountId, targetAccountId, amountMinorUnits, currencyCode, transactionType, recipient, sender, merchant, categoryId, paymentMethod, transactionDateMillis, referenceNumber, sourceType, transactionStatus, description, confidenceScore, createdAt, updatedAt)
            VALUES (1, 1, NULL, 45000, 'INR', 'DEBIT', '', '', 'Swiggy', NULL, 'UPI', 2000, 'REF-V1-001', 'BANK_NOTIFICATION', 'CONFIRMED', 'Legacy order', 0.95, 2000, 2000)
            """.trimIndent()
        )
        sqlite.version = 1
        sqlite.close()

        // Open with Room v3 + MIGRATION_1_2 & MIGRATION_2_3 (no destructive migration!)
        val migratedDb = Room.databaseBuilder(context, SpendWiseDatabase::class.java, dbName)
            .addMigrations(SpendWiseDatabase.MIGRATION_1_2, SpendWiseDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            SpendWiseDatabase.ensureDefaultCategories(migratedDb)
            val accounts = migratedDb.accountDao().getAllAccountsOnce()
            val transactions = migratedDb.transactionDao().getAllTransactionsOnce()
            val categories = migratedDb.categoryDao().getAllCategoriesOnce()

            assertEquals(1, accounts.size)
            assertEquals("Legacy SBI", accounts.first().accountNickname)
            assertEquals(2_500_000L, accounts.first().openingBalanceMinorUnits)

            assertEquals(1, transactions.size)
            assertEquals(45_000L, transactions.first().amountMinorUnits)
            assertNull(transactions.first().availableBalanceMinorUnits)
            assertNull(transactions.first().counterpartyAccountSuffix)

            assertTrue(categories.any { it.categoryName == "Person-to-Person" })
            assertTrue(categories.any { it.categoryName == "Bank Charges" })

            // Verify new balance_adjustments table works cleanly post-migration
            val migratedRepo = SpendWiseRepository(migratedDb, settingsDataStore)
            val adj = migratedRepo.reconcileAccountBalance(1L, 2_450_000L, "Post-migration reconciliation")
            assertNotNull(adj)
            assertEquals(-5_000L, adj.deltaMinorUnits)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun `manifest does not declare READ_SMS or INTERNET permissions`() {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse("READ_SMS must be completely removed", "android.permission.READ_SMS" in requested)
        assertFalse("INTERNET must not be declared for offline-first SpendWise", "android.permission.INTERNET" in requested)
    }

    @Test
    fun `saves user onboarding profile locally and surfaces name in ViewModel state`() = runBlocking {
        repository.initializeWorkspaceIfNeeded()
        repository.saveUserProfile(
            name = "Mugunthan",
            age = "24",
            email = "mugunthan@example.com",
            address = "Chennai, India"
        )

        val storedSettings = settingsDataStore.settingsFlow.first()
        assertEquals("Mugunthan", storedSettings.userName)
        assertEquals("24", storedSettings.userAge)
        assertEquals("mugunthan@example.com", storedSettings.userEmail)
        assertEquals("Chennai, India", storedSettings.userAddress)

        val viewModel = SpendWiseViewModel(repository)
        val uiState = viewModel.uiState.first { !it.isLoading && it.settings.userName == "Mugunthan" }
        assertEquals("Mugunthan", uiState.settings.userName)
        assertEquals("24", uiState.settings.userAge)
    }

    @Test
    fun `persists sender and receiver last 4 digits and routes unnamed alerts to Other categories`() = runBlocking {
        repository.initializeWorkspaceIfNeeded()
        val now = System.currentTimeMillis()
        repository.saveAccount(
            BankAccountModel(
                accountId = 0L,
                bankName = "State Bank of India",
                accountNickname = "SBI Main",
                accountNumberSuffix = "1234",
                accountType = AccountType.SAVINGS,
                currencyCode = "INR",
                openingBalanceMinorUnits = 500_000L,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
        )

        val categories = repository.categoriesFlow.first()
        val otherExpenseId = categories.first { it.categoryName == "Other" }.categoryId
        val otherIncomeId = categories.first { it.categoryName == "Other Income" }.categoryId

        val debitOutcome = repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Your A/c XX1234 debited Rs.150.00 via UPI to A/c XX9876. Ref 77661122.",
            timestampMillis = now,
            enforcePackageAllowList = true
        ) as ParseOutcome.Parsed
        assertEquals("9876", debitOutcome.transaction.counterpartyAccountSuffix)
        assertEquals(otherExpenseId, debitOutcome.transaction.categoryId)

        val creditOutcome = repository.processIncomingNotification(
            sourcePackage = "com.google.android.apps.messaging",
            title = "VM-SBIUPI",
            body = "Your A/c XX1234 credited Rs.400.00 via IMPS from A/c XX4321. Ref 88771122.",
            timestampMillis = now + 5_000L,
            enforcePackageAllowList = true
        ) as ParseOutcome.Parsed
        assertEquals("4321", creditOutcome.transaction.counterpartyAccountSuffix)
        assertEquals(otherIncomeId, creditOutcome.transaction.categoryId)

        val stored = repository.transactionsFlow.first()
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.counterpartyAccountSuffix == "9876" && it.categoryId == otherExpenseId })
        assertTrue(stored.any { it.counterpartyAccountSuffix == "4321" && it.categoryId == otherIncomeId })
    }
}
