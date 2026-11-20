package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.SpendWiseDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.BalanceAdjustmentEntity
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MerchantRuleEntity
import com.example.data.local.entity.NotificationProcessingEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.toDomain
import com.example.data.local.entity.toEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SettingsDataStore
import com.example.domain.engine.FinancialCalculator
import com.example.domain.model.BalanceAdjustmentModel
import com.example.domain.model.BankAccountModel
import com.example.domain.model.BudgetModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategoryType
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.NotificationRecordModel
import com.example.domain.model.ThemePreference
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionStatus
import com.example.intelligence.DuplicateDetector
import com.example.intelligence.ParseOutcome
import com.example.intelligence.ReferenceExtractor
import com.example.intelligence.TransactionCategorizer
import com.example.intelligence.TransactionDateExtractor
import com.example.intelligence.TransactionParserEngine
import com.example.security.SpendWiseCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class SpendWiseRepository(
    private val database: SpendWiseDatabase,
    private val settingsDataStore: SettingsDataStore,
    private val parserEngine: TransactionParserEngine = TransactionParserEngine()
) {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val transactionDao = database.transactionDao()
    private val notificationDao = database.notificationProcessingDao()
    private val budgetDao = database.budgetDao()
    private val merchantRuleDao = database.merchantRuleDao()
    private val balanceAdjustmentDao = database.balanceAdjustmentDao()

    // Serializes concurrent notification deliveries (e.g., simultaneous SMS + Bank App alerts)
    // so duplicate checks and atomic DB writes cannot race.
    private val notificationProcessingMutex = Mutex()

    val accountsFlow: Flow<List<BankAccountModel>> =
        accountDao.observeAllAccounts().map { list -> list.map { it.toDomain() } }

    val categoriesFlow: Flow<List<CategoryModel>> =
        categoryDao.observeActiveCategories().map { list -> list.map { it.toDomain() } }

    val transactionsFlow: Flow<List<TransactionModel>> =
        transactionDao.observeAllTransactions().map { list -> list.map { it.toDomain() } }

    val budgetsFlow: Flow<List<BudgetModel>> =
        budgetDao.observeAllBudgets().map { list -> list.map { it.toDomain() } }

    val balanceAdjustmentsFlow: Flow<List<BalanceAdjustmentModel>> =
        balanceAdjustmentDao.observeAllAdjustments().map { list -> list.map { it.toDomain() } }

    val notificationRecordsFlow: Flow<List<NotificationRecordModel>> =
        notificationDao.observeRecentRecords().map { list -> list.map { it.toDomain() } }

    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow

    suspend fun initializeWorkspaceIfNeeded() = withContext(Dispatchers.IO) {
        SpendWiseCryptoManager.ensureKeystoreMasterKey()
        SpendWiseDatabase.ensureDefaultCategories(database)
    }

    suspend fun markOnboardingCompleted() {
        settingsDataStore.markOnboardingCompleted()
    }

    // ========================================================================
    // Accounts CRUD & Manual Balance Reconciliation
    // ========================================================================
    suspend fun saveAccount(account: BankAccountModel): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sanitized = account.copy(
            accountNumberSuffix = account.accountNumberSuffix.filter { it.isDigit() }.takeLast(4),
            updatedAt = now,
            createdAt = if (account.accountId == 0L) now else account.createdAt
        )
        if (sanitized.accountId == 0L) {
            accountDao.insertAccount(sanitized.toEntity())
        } else {
            accountDao.updateAccount(sanitized.toEntity())
            sanitized.accountId
        }
    }

    suspend fun setAccountActive(accountId: Long, active: Boolean) = withContext(Dispatchers.IO) {
        accountDao.setAccountActive(accountId, active)
    }

    /**
     * Records a dated balance reconciliation adjustment for an account without overwriting its
     * original starting balance or altering transaction history.
     *
     * Adjustment delta = reportedBalanceMinorUnits - currentEstimatedBalanceMinorUnits.
     */
    suspend fun reconcileAccountBalance(
        accountId: Long,
        reportedBalanceMinorUnits: Long,
        note: String = "",
        adjustmentDateMillis: Long = System.currentTimeMillis()
    ): BalanceAdjustmentModel = withContext(Dispatchers.IO) {
        database.withTransaction {
            val account = accountDao.getAllAccountsOnce()
                .firstOrNull { it.accountId == accountId }
                ?.toDomain()
                ?: throw IllegalArgumentException("Account $accountId not found")

            val allTransactions = transactionDao.getAllTransactionsOnce().map { it.toDomain() }
            val existingAdjustments = balanceAdjustmentDao.getAdjustmentsForAccountOnce(accountId)
                .map { it.toDomain() }

            val previousEstimated = FinancialCalculator.calculateSingleAccountEstimatedBalance(
                account = account,
                allTransactions = allTransactions,
                balanceAdjustments = existingAdjustments
            )
            val delta = reportedBalanceMinorUnits - previousEstimated
            val now = System.currentTimeMillis()
            val cleanNote = note.trim().ifEmpty {
                "Manual balance update to ${MoneyFormatter.formatMinorUnits(reportedBalanceMinorUnits, account.currencyCode)}"
            }

            val entity = BalanceAdjustmentEntity(
                accountId = accountId,
                previousEstimatedBalanceMinorUnits = previousEstimated,
                reportedBalanceMinorUnits = reportedBalanceMinorUnits,
                deltaMinorUnits = delta,
                currencyCode = account.currencyCode,
                note = cleanNote,
                adjustmentDateMillis = adjustmentDateMillis,
                createdAt = now
            )
            val id = balanceAdjustmentDao.insertAdjustment(entity)
            entity.copy(adjustmentId = id).toDomain()
        }
    }

    suspend fun deleteBalanceAdjustment(adjustmentId: Long) = withContext(Dispatchers.IO) {
        balanceAdjustmentDao.deleteAdjustmentById(adjustmentId)
    }

    // ========================================================================
    // Transactions CRUD & Local Counterparty (Merchant & Recipient) Learning
    // ========================================================================
    suspend fun saveTransaction(
        transaction: TransactionModel,
        learnMerchantCategory: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val entity = transaction.copy(
                updatedAt = now,
                createdAt = if (transaction.transactionId == 0L) now else transaction.createdAt
            ).toEntity()

            val id = if (entity.transactionId == 0L) {
                transactionDao.insertTransaction(entity)
            } else {
                transactionDao.updateTransaction(entity)
                entity.transactionId
            }

            if (learnMerchantCategory && transaction.categoryId != null) {
                val learningKey = transaction.counterpartyForLearning
                val normalized = TransactionCategorizer.normalizeMerchantKey(learningKey)
                if (normalized.isNotEmpty() && normalized != "unknown") {
                    merchantRuleDao.upsertRule(
                        MerchantRuleEntity(
                            normalizedMerchant = normalized,
                            categoryId = transaction.categoryId,
                            updatedAt = now
                        )
                    )
                }
            }
            id
        }
    }

    suspend fun deleteTransaction(transactionId: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteTransactionById(transactionId)
    }

    suspend fun approvePendingTransaction(
        transaction: TransactionModel,
        learnMerchantRule: Boolean = true
    ) = saveTransaction(
        transaction = transaction.copy(
            transactionStatus = TransactionStatus.CONFIRMED,
            confidenceScore = 1.0f
        ),
        learnMerchantCategory = learnMerchantRule
    )

    // ========================================================================
    // Categories & Budgets
    // ========================================================================
    suspend fun addCustomCategory(
        name: String,
        type: CategoryType,
        iconKey: String,
        colorHex: Long
    ): Long = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(
            CategoryEntity(
                categoryName = name.trim(),
                categoryType = type.name,
                iconKey = iconKey,
                colorHex = colorHex,
                isDefault = false,
                isActive = true
            )
        )
    }

    suspend fun deleteCustomCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        categoryDao.deleteCustomCategory(categoryId)
    }

    suspend fun upsertBudget(
        categoryId: Long,
        amountMinorUnits: Long,
        currencyCode: String,
        alertThresholdPercent: Int = 80
    ) = withContext(Dispatchers.IO) {
        budgetDao.upsertBudget(
            BudgetEntity(
                categoryId = categoryId,
                amountMinorUnits = amountMinorUnits,
                currencyCode = currencyCode,
                alertThresholdPercent = alertThresholdPercent,
                isActive = true
            )
        )
    }

    suspend fun deleteBudget(budgetId: Long) = withContext(Dispatchers.IO) {
        budgetDao.deleteBudgetById(budgetId)
    }

    // ========================================================================
    // Live Notification Detection Pipeline (Serialized + Atomic DB Transaction)
    // ========================================================================
    suspend fun processIncomingNotification(
        sourcePackage: String,
        title: String,
        body: String,
        timestampMillis: Long = System.currentTimeMillis(),
        enforcePackageAllowList: Boolean = true
    ): ParseOutcome = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getSettingsOnce()
        val fingerprint = DuplicateDetector.computeNotificationFingerprint(
            sourcePackage = sourcePackage,
            title = title,
            body = body,
            minuteBucketTimestamp = timestampMillis
        )

        if (enforcePackageAllowList) {
            if (!settings.notificationDetectionEnabled) {
                return@withContext ParseOutcome.Ignored("Detection paused by user", fingerprint)
            }
            if (sourcePackage !in settings.allowedPackages) {
                return@withContext ParseOutcome.Ignored("Unsupported source app", fingerprint)
            }
        }

        notificationProcessingMutex.withLock {
            database.withTransaction {
                // Ensure default categories exist even if a notification arrives before MainActivity launches
                SpendWiseDatabase.ensureDefaultCategories(database)

                val existingRecord = notificationDao.findByFingerprint(fingerprint)
                val combinedText = "$title $body"
                val extractedRef = ReferenceExtractor.extract(combinedText)
                val existingRefTx = extractedRef?.let { transactionDao.findByReferenceNumber(it) }

                val activeAccounts = accountDao.getActiveAccountsOnce().map { it.toDomain() }
                val categories = categoryDao.getAllCategoriesOnce().map { it.toDomain() }
                val learnedRules = merchantRuleDao.getAllRulesOnce()
                    .associate { it.normalizedMerchant to it.categoryId }

                val extractedDateMillis = TransactionDateExtractor.extractMillis(combinedText, timestampMillis)
                val windowMillis = 20L * 60L * 1000L
                val recentTxs = transactionDao.getAllTransactionsOnce()
                    .filter {
                        abs(it.transactionDateMillis - timestampMillis) <= windowMillis ||
                            abs(it.transactionDateMillis - extractedDateMillis) <= windowMillis
                    }
                    .map { it.toDomain() }

                val outcome = parserEngine.parse(
                    sourcePackage = sourcePackage,
                    title = title,
                    body = body,
                    timestampMillis = timestampMillis,
                    activeAccounts = activeAccounts,
                    categories = categories,
                    userLearnedRules = learnedRules,
                    recentTransactionsForDuplicateCheck = recentTxs,
                    existingReferenceMatch = existingRefTx != null,
                    existingFingerprintMatch = existingRecord != null,
                    defaultCurrency = settings.defaultCurrency
                )

                when (outcome) {
                    is ParseOutcome.Parsed -> {
                        val txId = transactionDao.insertTransaction(outcome.transaction.toEntity())
                        notificationDao.insertRecord(
                            NotificationProcessingEntity(
                                sourcePackage = sourcePackage,
                                notificationTimestamp = timestampMillis,
                                fingerprint = outcome.fingerprint,
                                processingStatus = outcome.transaction.transactionStatus.name,
                                associatedTransactionId = txId,
                                summaryLabel = "${outcome.bankName ?: "Bank"} · ${outcome.transaction.displayCounterparty}"
                            )
                        )
                        outcome.copy(transaction = outcome.transaction.copy(transactionId = txId))
                    }
                    is ParseOutcome.Duplicate -> {
                        notificationDao.insertRecord(
                            NotificationProcessingEntity(
                                sourcePackage = sourcePackage,
                                notificationTimestamp = timestampMillis,
                                fingerprint = "${outcome.fingerprint}_dup_${timestampMillis % 100000}",
                                processingStatus = "DUPLICATE",
                                associatedTransactionId = null,
                                summaryLabel = outcome.reason
                            )
                        )
                        outcome
                    }
                    is ParseOutcome.Ignored -> {
                        // Record diagnostic metadata only; never persist raw OTP or message text
                        notificationDao.insertRecord(
                            NotificationProcessingEntity(
                                sourcePackage = sourcePackage,
                                notificationTimestamp = timestampMillis,
                                fingerprint = outcome.fingerprint,
                                processingStatus = "IGNORED",
                                associatedTransactionId = null,
                                summaryLabel = outcome.reason
                            )
                        )
                        outcome
                    }
                }
            }
        }
    }

    // ========================================================================
    // Settings & Privacy
    // ========================================================================
    suspend fun setThemePreference(theme: ThemePreference) = settingsDataStore.setThemePreference(theme)
    suspend fun setHideBalances(hide: Boolean) = settingsDataStore.setHideBalances(hide)
    suspend fun setDefaultCurrency(code: String) = settingsDataStore.setDefaultCurrency(code)
    suspend fun setNotificationDetectionEnabled(enabled: Boolean) =
        settingsDataStore.setNotificationDetectionEnabled(enabled)
    suspend fun toggleAllowedPackage(pkg: String) = settingsDataStore.toggleAllowedPackage(pkg)
    suspend fun setAppLockEnabled(enabled: Boolean) = settingsDataStore.setAppLockEnabled(enabled)
    suspend fun setBiometricEnabled(enabled: Boolean) = settingsDataStore.setBiometricEnabled(enabled)

    suspend fun saveUserProfile(name: String, age: String, email: String, address: String) =
        settingsDataStore.saveUserProfile(name, age, email, address)

    suspend fun configurePin(pin: String) {
        val (hash, salt) = SpendWiseCryptoManager.hashPin(pin)
        settingsDataStore.setPinCredentials(hash, salt, enableLock = true)
    }

    suspend fun verifyPin(pin: String): Boolean {
        val pair = settingsDataStore.getPinHashAndSalt() ?: return false
        return SpendWiseCryptoManager.verifyPin(pin, pair.first, pair.second)
    }

    suspend fun clearTransactionsOnly() = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.deleteAllTransactions()
            notificationDao.deleteAllRecords()
        }
    }

    suspend fun deleteAllUserData() = withContext(Dispatchers.IO) {
        database.withTransaction {
            transactionDao.deleteAllTransactions()
            balanceAdjustmentDao.deleteAllAdjustments()
            budgetDao.deleteAllBudgets()
            accountDao.deleteAllAccounts()
            notificationDao.deleteAllRecords()
            merchantRuleDao.deleteAllRules()
            categoryDao.deleteAllCustomCategories()
        }
        settingsDataStore.resetPrivacyCredentials()
        settingsDataStore.clearUserProfile()
    }

    // ========================================================================
    // Export & Encrypted Backup / Restore (Schema v2, backwards-compatible v1)
    // ========================================================================
    suspend fun generateCsvExport(): String = withContext(Dispatchers.IO) {
        val txs = transactionDao.getAllTransactionsOnce().map { it.toDomain() }
        val accounts = accountDao.getAllAccountsOnce().map { it.toDomain() }
        val categories = categoryDao.getAllCategoriesOnce().map { it.toDomain() }
        SpendWiseCryptoManager.buildTransactionsCsv(txs, accounts, categories)
    }

    suspend fun createEncryptedBackup(passphrase: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("exportedAt", System.currentTimeMillis())

            val accArray = JSONArray()
            accountDao.getAllAccountsOnce().forEach { a ->
                accArray.put(JSONObject().apply {
                    put("accountId", a.accountId)
                    put("bankName", a.bankName)
                    put("accountNickname", a.accountNickname)
                    put("accountNumberSuffix", a.accountNumberSuffix)
                    put("accountType", a.accountType)
                    put("currencyCode", a.currencyCode)
                    put("openingBalanceMinorUnits", a.openingBalanceMinorUnits)
                    put("isActive", a.isActive)
                    put("createdAt", a.createdAt)
                    put("updatedAt", a.updatedAt)
                })
            }
            put("accounts", accArray)

            val txArray = JSONArray()
            transactionDao.getAllTransactionsOnce().forEach { t ->
                txArray.put(JSONObject().apply {
                    put("accountId", t.accountId ?: JSONObject.NULL)
                    put("targetAccountId", t.targetAccountId ?: JSONObject.NULL)
                    put("amountMinorUnits", t.amountMinorUnits)
                    put("currencyCode", t.currencyCode)
                    put("transactionType", t.transactionType)
                    put("recipient", t.recipient)
                    put("sender", t.sender)
                    put("merchant", t.merchant)
                    put("categoryId", t.categoryId ?: JSONObject.NULL)
                    put("paymentMethod", t.paymentMethod)
                    put("transactionDateMillis", t.transactionDateMillis)
                    put("referenceNumber", t.referenceNumber ?: JSONObject.NULL)
                    put("sourceType", t.sourceType)
                    put("transactionStatus", t.transactionStatus)
                    put("description", t.description)
                    put("confidenceScore", t.confidenceScore.toDouble())
                    put("availableBalanceMinorUnits", t.availableBalanceMinorUnits ?: JSONObject.NULL)
                    put("counterpartyAccountSuffix", t.counterpartyAccountSuffix ?: JSONObject.NULL)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                })
            }
            put("transactions", txArray)

            val adjustmentArray = JSONArray()
            balanceAdjustmentDao.getAllAdjustmentsOnce().forEach { adj ->
                adjustmentArray.put(JSONObject().apply {
                    put("accountId", adj.accountId)
                    put("previousEstimatedBalanceMinorUnits", adj.previousEstimatedBalanceMinorUnits)
                    put("reportedBalanceMinorUnits", adj.reportedBalanceMinorUnits)
                    put("deltaMinorUnits", adj.deltaMinorUnits)
                    put("currencyCode", adj.currencyCode)
                    put("note", adj.note)
                    put("adjustmentDateMillis", adj.adjustmentDateMillis)
                    put("createdAt", adj.createdAt)
                })
            }
            put("balanceAdjustments", adjustmentArray)

            val budgetArray = JSONArray()
            budgetDao.getAllBudgetsOnce().forEach { b ->
                budgetArray.put(JSONObject().apply {
                    put("categoryId", b.categoryId)
                    put("amountMinorUnits", b.amountMinorUnits)
                    put("currencyCode", b.currencyCode)
                    put("alertThresholdPercent", b.alertThresholdPercent)
                    put("isActive", b.isActive)
                })
            }
            put("budgets", budgetArray)
        }
        SpendWiseCryptoManager.encryptBackupPayload(root.toString(), passphrase)
    }

    suspend fun restoreEncryptedBackup(encryptedText: String, passphrase: String): Result<Int> =
        withContext(Dispatchers.IO) {
            SpendWiseCryptoManager.decryptBackupPayload(encryptedText, passphrase).mapCatching { jsonStr ->
                val root = JSONObject(jsonStr)
                database.withTransaction {
                    SpendWiseDatabase.ensureDefaultCategories(database)

                    // Clear current user records atomically before restoring clean snapshot
                    transactionDao.deleteAllTransactions()
                    balanceAdjustmentDao.deleteAllAdjustments()
                    budgetDao.deleteAllBudgets()
                    accountDao.deleteAllAccounts()

                    val oldToNewAccountId = mutableMapOf<Long, Long>()
                    val accArray = root.optJSONArray("accounts") ?: JSONArray()
                    for (i in 0 until accArray.length()) {
                        val obj = accArray.getJSONObject(i)
                        val oldId = obj.getLong("accountId")
                        val newId = accountDao.insertAccount(
                            AccountEntity(
                                bankName = obj.getString("bankName"),
                                accountNickname = obj.getString("accountNickname"),
                                accountNumberSuffix = obj.optString("accountNumberSuffix", ""),
                                accountType = obj.optString("accountType", "SAVINGS"),
                                currencyCode = obj.optString("currencyCode", "INR"),
                                openingBalanceMinorUnits = obj.optLong("openingBalanceMinorUnits", 0L),
                                isActive = obj.optBoolean("isActive", true),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                        oldToNewAccountId[oldId] = newId
                    }

                    val validCategoryIds = categoryDao.getAllCategoriesOnce().map { it.categoryId }.toSet()
                    val txArray = root.optJSONArray("transactions") ?: JSONArray()
                    val restoredTxs = mutableListOf<TransactionEntity>()
                    for (i in 0 until txArray.length()) {
                        val obj = txArray.getJSONObject(i)
                        val oldAccId = if (obj.isNull("accountId")) null else obj.getLong("accountId")
                        val oldTargetId = if (obj.isNull("targetAccountId")) null else obj.getLong("targetAccountId")
                        val catId = if (obj.isNull("categoryId")) null else obj.getLong("categoryId")
                        val availBal = if (!obj.has("availableBalanceMinorUnits") || obj.isNull("availableBalanceMinorUnits")) {
                            null
                        } else {
                            obj.getLong("availableBalanceMinorUnits")
                        }
                        val partySuffix = if (!obj.has("counterpartyAccountSuffix") || obj.isNull("counterpartyAccountSuffix")) {
                            null
                        } else {
                            obj.optString("counterpartyAccountSuffix").takeIf { it.isNotBlank() }
                        }
                        restoredTxs += TransactionEntity(
                            accountId = oldAccId?.let { oldToNewAccountId[it] },
                            targetAccountId = oldTargetId?.let { oldToNewAccountId[it] },
                            amountMinorUnits = obj.getLong("amountMinorUnits"),
                            currencyCode = obj.optString("currencyCode", "INR"),
                            transactionType = obj.optString("transactionType", "DEBIT"),
                            recipient = obj.optString("recipient", ""),
                            sender = obj.optString("sender", ""),
                            merchant = obj.optString("merchant", ""),
                            categoryId = catId?.takeIf { it in validCategoryIds },
                            paymentMethod = obj.optString("paymentMethod", "OTHER"),
                            transactionDateMillis = obj.optLong("transactionDateMillis", System.currentTimeMillis()),
                            referenceNumber = if (obj.isNull("referenceNumber")) null else obj.optString("referenceNumber"),
                            sourceType = obj.optString("sourceType", "IMPORTED"),
                            transactionStatus = obj.optString("transactionStatus", "CONFIRMED"),
                            description = obj.optString("description", ""),
                            confidenceScore = obj.optDouble("confidenceScore", 1.0).toFloat(),
                            availableBalanceMinorUnits = availBal,
                            counterpartyAccountSuffix = partySuffix,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    }
                    transactionDao.insertTransactions(restoredTxs)

                    val adjustmentArray = root.optJSONArray("balanceAdjustments") ?: JSONArray()
                    val restoredAdjustments = mutableListOf<BalanceAdjustmentEntity>()
                    for (i in 0 until adjustmentArray.length()) {
                        val obj = adjustmentArray.getJSONObject(i)
                        val mappedAccountId = oldToNewAccountId[obj.getLong("accountId")] ?: continue
                        val prevBal = if (obj.has("previousEstimatedBalanceMinorUnits")) {
                            obj.optLong("previousEstimatedBalanceMinorUnits", 0L)
                        } else {
                            obj.optLong("previousEstimatedMinorUnits", 0L)
                        }
                        val deltaBal = if (obj.has("deltaMinorUnits")) {
                            obj.getLong("deltaMinorUnits")
                        } else {
                            obj.optLong("adjustmentDeltaMinorUnits", 0L)
                        }
                        restoredAdjustments += BalanceAdjustmentEntity(
                            accountId = mappedAccountId,
                            previousEstimatedBalanceMinorUnits = prevBal,
                            reportedBalanceMinorUnits = obj.getLong("reportedBalanceMinorUnits"),
                            deltaMinorUnits = deltaBal,
                            currencyCode = obj.optString("currencyCode", "INR"),
                            note = obj.optString("note", ""),
                            adjustmentDateMillis = obj.optLong("adjustmentDateMillis", System.currentTimeMillis()),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    }
                    if (restoredAdjustments.isNotEmpty()) {
                        balanceAdjustmentDao.insertAdjustments(restoredAdjustments)
                    }

                    val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
                    for (i in 0 until budgetArray.length()) {
                        val obj = budgetArray.getJSONObject(i)
                        val catId = obj.getLong("categoryId")
                        if (catId in validCategoryIds) {
                            budgetDao.upsertBudget(
                                BudgetEntity(
                                    categoryId = catId,
                                    amountMinorUnits = obj.getLong("amountMinorUnits"),
                                    currencyCode = obj.optString("currencyCode", "INR"),
                                    alertThresholdPercent = obj.optInt("alertThresholdPercent", 80),
                                    isActive = obj.optBoolean("isActive", true)
                                )
                            )
                        }
                    }

                    restoredTxs.size
                }
            }
        }
}
