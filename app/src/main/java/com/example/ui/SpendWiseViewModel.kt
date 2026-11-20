package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppSettings
import com.example.data.repository.SpendWiseRepository
import com.example.domain.engine.FinancialCalculator
import com.example.domain.model.AccountSummary
import com.example.domain.model.AnalyticsTimeRange
import com.example.domain.model.BalanceAdjustmentModel
import com.example.domain.model.BankAccountModel
import com.example.domain.model.BudgetModel
import com.example.domain.model.BudgetProgress
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategorySpendSummary
import com.example.domain.model.CategoryType
import com.example.domain.model.CurrencyConsolidatedSummary
import com.example.domain.model.DailySpendPoint
import com.example.domain.model.NotificationRecordModel
import com.example.domain.model.RecipientSpendSummary
import com.example.domain.model.SpendingInsight
import com.example.domain.model.ThemePreference
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionSortOrder
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.intelligence.ParseOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TransactionFilters(
    val searchQuery: String = "",
    val typeFilter: TransactionType? = null,
    val statusFilter: TransactionStatus? = null,
    val accountIdFilter: Long? = null,
    val categoryIdFilter: Long? = null,
    val sortOrder: TransactionSortOrder = TransactionSortOrder.NEWEST
)

data class SpendWiseUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedMonthLabel: String = "",
    val activeCurrency: String = "INR",
    val accounts: List<BankAccountModel> = emptyList(),
    val accountSummaries: List<AccountSummary> = emptyList(),
    val consolidatedByCurrency: List<CurrencyConsolidatedSummary> = emptyList(),
    val activeConsolidated: CurrencyConsolidatedSummary = CurrencyConsolidatedSummary("INR", 0L, 0L, 0L, 0L, 0),
    val categories: List<CategoryModel> = emptyList(),
    val allTransactions: List<TransactionModel> = emptyList(),
    val currentMonthTransactions: List<TransactionModel> = emptyList(),
    val filteredTransactions: List<TransactionModel> = emptyList(),
    val pendingReviewTransactions: List<TransactionModel> = emptyList(),
    val recentTransactions: List<TransactionModel> = emptyList(),
    val homeCategorySpending: List<CategorySpendSummary> = emptyList(),
    val homeDailyTrend: List<DailySpendPoint> = emptyList(),
    val insights: List<SpendingInsight> = emptyList(),
    val budgets: List<BudgetModel> = emptyList(),
    val budgetProgress: List<BudgetProgress> = emptyList(),
    // Analytics state
    val analyticsRange: AnalyticsTimeRange = AnalyticsTimeRange.THIS_MONTH,
    val analyticsAccountId: Long? = null,
    val analyticsCategorySpending: List<CategorySpendSummary> = emptyList(),
    val analyticsTopRecipients: List<RecipientSpendSummary> = emptyList(),
    val analyticsDailyTrend: List<DailySpendPoint> = emptyList(),
    val analyticsTotalIncome: Long = 0L,
    val analyticsTotalExpense: Long = 0L,
    val analyticsPrevExpense: Long = 0L,
    val analyticsAccountActivity: List<AccountSummary> = emptyList(),
    // Filters & diagnostics
    val filters: TransactionFilters = TransactionFilters(),
    val notificationRecords: List<NotificationRecordModel> = emptyList(),
    val balanceAdjustments: List<BalanceAdjustmentModel> = emptyList()
)

private data class ViewSelectionState(
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val month: Int = Calendar.getInstance().get(Calendar.MONTH),
    val currencyOverride: String? = null,
    val filters: TransactionFilters = TransactionFilters(),
    val analyticsRange: AnalyticsTimeRange = AnalyticsTimeRange.THIS_MONTH,
    val analyticsAccountId: Long? = null
)

class SpendWiseViewModel(
    private val repository: SpendWiseRepository
) : ViewModel() {

    private val selectionState = MutableStateFlow(ViewSelectionState())

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _lastParseOutcome = MutableStateFlow<ParseOutcome?>(null)
    val lastParseOutcome: StateFlow<ParseOutcome?> = _lastParseOutcome.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var hasEvaluatedInitialLock = false

    init {
        viewModelScope.launch {
            repository.initializeWorkspaceIfNeeded()
        }
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                if (!hasEvaluatedInitialLock) {
                    hasEvaluatedInitialLock = true
                    _isLocked.value = settings.appLockEnabled && settings.hasPinConfigured
                } else if (!settings.appLockEnabled) {
                    _isLocked.value = false
                }
            }
        }
    }

    private val coreDataFlow = combine(
        repository.accountsFlow,
        repository.categoriesFlow,
        repository.transactionsFlow,
        repository.budgetsFlow,
        repository.balanceAdjustmentsFlow
    ) { accounts, categories, transactions, budgets, adjustments ->
        CoreDatabaseSnapshot(accounts, categories, transactions, budgets, adjustments)
    }

    private val dataFlow = combine(
        coreDataFlow,
        repository.notificationRecordsFlow
    ) { core, records ->
        DatabaseSnapshot(
            accounts = core.accounts,
            categories = core.categories,
            transactions = core.transactions,
            budgets = core.budgets,
            balanceAdjustments = core.balanceAdjustments,
            records = records
        )
    }

    val uiState: StateFlow<SpendWiseUiState> = combine(
        dataFlow,
        repository.settingsFlow,
        selectionState
    ) { snapshot, settings, sel ->
        buildUiState(snapshot, settings, sel)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SpendWiseUiState(isLoading = true)
    )

    private fun buildUiState(
        snapshot: DatabaseSnapshot,
        settings: AppSettings,
        sel: ViewSelectionState
    ): SpendWiseUiState {
        val (monthStart, monthEnd) = FinancialCalculator.getMonthRange(sel.year, sel.month)
        val prevCal = Calendar.getInstance().apply {
            set(sel.year, sel.month, 1)
            add(Calendar.MONTH, -1)
        }
        val (prevStart, prevEnd) = FinancialCalculator.getMonthRange(
            prevCal.get(Calendar.YEAR),
            prevCal.get(Calendar.MONTH)
        )

        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(monthStart))
        val monthTxs = snapshot.transactions.filter { it.transactionDateMillis in monthStart..monthEnd }
        val prevMonthTxs = snapshot.transactions.filter { it.transactionDateMillis in prevStart..prevEnd }

        val accountSummaries = FinancialCalculator.calculateAccountSummaries(
            accounts = snapshot.accounts,
            allTransactions = snapshot.transactions,
            periodStartMillis = monthStart,
            periodEndMillis = monthEnd,
            balanceAdjustments = snapshot.balanceAdjustments
        )

        val consolidatedList = FinancialCalculator.calculateConsolidatedByCurrency(
            accountSummaries = accountSummaries,
            periodTransactions = monthTxs
        )

        val preferredCurrency = sel.currencyOverride
            ?: consolidatedList.firstOrNull { it.currencyCode == settings.defaultCurrency }?.currencyCode
            ?: consolidatedList.firstOrNull()?.currencyCode
            ?: settings.defaultCurrency

        val activeConsolidated = consolidatedList.firstOrNull { it.currencyCode == preferredCurrency }
            ?: CurrencyConsolidatedSummary(preferredCurrency, 0L, 0L, 0L, 0L, 0)

        val homeCategories = FinancialCalculator.calculateCategorySpending(
            transactions = monthTxs,
            categories = snapshot.categories,
            currencyCode = preferredCurrency
        )
        val homeTrend = FinancialCalculator.calculateDailyTrend(
            transactions = monthTxs,
            currencyCode = preferredCurrency,
            startMillis = monthStart,
            endMillis = monthEnd
        )
        val budgetProgress = FinancialCalculator.calculateBudgetProgress(
            budgets = snapshot.budgets,
            categories = snapshot.categories,
            monthTransactions = monthTxs
        )
        val insights = FinancialCalculator.generateLocalInsights(
            currentMonthTxs = monthTxs,
            previousMonthTxs = prevMonthTxs,
            categorySummaries = homeCategories,
            budgetProgressList = budgetProgress,
            currencyCode = preferredCurrency
        )

        val pendingReview = snapshot.transactions.filter {
            it.transactionStatus == TransactionStatus.PENDING_REVIEW
        }

        val filteredTxs = applyTransactionFilters(
            transactions = snapshot.transactions,
            categories = snapshot.categories,
            accounts = snapshot.accounts,
            filters = sel.filters
        )

        // Analytics computation for selected range & optional account filter
        val (anStart, anEnd, anPrevStart, anPrevEnd) = computeAnalyticsBounds(sel.analyticsRange)
        val anTxsBase = snapshot.transactions.filter { tx ->
            tx.transactionDateMillis in anStart..anEnd &&
                tx.transactionStatus == TransactionStatus.CONFIRMED &&
                (sel.analyticsAccountId == null || tx.accountId == sel.analyticsAccountId || tx.targetAccountId == sel.analyticsAccountId)
        }
        val anPrevTxs = snapshot.transactions.filter { tx ->
            tx.transactionDateMillis in anPrevStart..anPrevEnd &&
                tx.transactionStatus == TransactionStatus.CONFIRMED &&
                tx.transactionType == TransactionType.DEBIT &&
                tx.currencyCode.equals(preferredCurrency, ignoreCase = true) &&
                (sel.analyticsAccountId == null || tx.accountId == sel.analyticsAccountId)
        }

        val anIncome = anTxsBase
            .filter { it.transactionType == TransactionType.CREDIT && it.currencyCode.equals(preferredCurrency, ignoreCase = true) }
            .sumOf { it.amountMinorUnits }
        val anExpense = anTxsBase
            .filter { it.transactionType == TransactionType.DEBIT && it.currencyCode.equals(preferredCurrency, ignoreCase = true) }
            .sumOf { it.amountMinorUnits }
        val anPrevExpense = anPrevTxs.sumOf { it.amountMinorUnits }

        val anCategorySpending = FinancialCalculator.calculateCategorySpending(
            transactions = anTxsBase,
            categories = snapshot.categories,
            currencyCode = preferredCurrency
        )
        val anTopRecipients = FinancialCalculator.calculateTopRecipients(
            transactions = anTxsBase,
            currencyCode = preferredCurrency
        )
        val anDailyTrend = FinancialCalculator.calculateDailyTrend(
            transactions = anTxsBase,
            currencyCode = preferredCurrency,
            startMillis = anStart,
            endMillis = anEnd
        )
        val anAccountActivity = FinancialCalculator.calculateAccountSummaries(
            accounts = snapshot.accounts.filter { it.isActive && it.currencyCode.equals(preferredCurrency, ignoreCase = true) },
            allTransactions = snapshot.transactions,
            periodStartMillis = anStart,
            periodEndMillis = anEnd,
            balanceAdjustments = snapshot.balanceAdjustments
        )

        return SpendWiseUiState(
            isLoading = false,
            settings = settings,
            selectedYear = sel.year,
            selectedMonth = sel.month,
            selectedMonthLabel = monthLabel,
            activeCurrency = preferredCurrency,
            accounts = snapshot.accounts,
            accountSummaries = accountSummaries,
            consolidatedByCurrency = consolidatedList,
            activeConsolidated = activeConsolidated,
            categories = snapshot.categories,
            allTransactions = snapshot.transactions,
            currentMonthTransactions = monthTxs,
            filteredTransactions = filteredTxs,
            pendingReviewTransactions = pendingReview,
            recentTransactions = snapshot.transactions
                .filter { it.transactionStatus == TransactionStatus.CONFIRMED }
                .take(6),
            homeCategorySpending = homeCategories,
            homeDailyTrend = homeTrend,
            insights = insights,
            budgets = snapshot.budgets,
            budgetProgress = budgetProgress,
            analyticsRange = sel.analyticsRange,
            analyticsAccountId = sel.analyticsAccountId,
            analyticsCategorySpending = anCategorySpending,
            analyticsTopRecipients = anTopRecipients,
            analyticsDailyTrend = anDailyTrend,
            analyticsTotalIncome = anIncome,
            analyticsTotalExpense = anExpense,
            analyticsPrevExpense = anPrevExpense,
            analyticsAccountActivity = anAccountActivity,
            filters = sel.filters,
            notificationRecords = snapshot.records,
            balanceAdjustments = snapshot.balanceAdjustments
        )
    }

    private fun applyTransactionFilters(
        transactions: List<TransactionModel>,
        categories: List<CategoryModel>,
        accounts: List<BankAccountModel>,
        filters: TransactionFilters
    ): List<TransactionModel> {
        val catMap = categories.associateBy { it.categoryId }
        val accMap = accounts.associateBy { it.accountId }
        val query = filters.searchQuery.trim().lowercase()

        val filtered = transactions.filter { tx ->
            if (filters.typeFilter != null && tx.transactionType != filters.typeFilter) return@filter false
            if (filters.statusFilter != null && tx.transactionStatus != filters.statusFilter) return@filter false
            if (filters.accountIdFilter != null && tx.accountId != filters.accountIdFilter && tx.targetAccountId != filters.accountIdFilter) {
                return@filter false
            }
            if (filters.categoryIdFilter != null && tx.categoryId != filters.categoryIdFilter) return@filter false
            if (query.isNotEmpty()) {
                val catName = catMap[tx.categoryId]?.categoryName.orEmpty().lowercase()
                val accName = accMap[tx.accountId]?.let { "${it.bankName} ${it.accountNickname} ${it.accountNumberSuffix}" }
                    .orEmpty().lowercase()
                val matches = tx.displayCounterparty.lowercase().contains(query) ||
                    tx.recipient.lowercase().contains(query) ||
                    tx.sender.lowercase().contains(query) ||
                    tx.counterpartyAccountSuffix.orEmpty().contains(query) ||
                    tx.description.lowercase().contains(query) ||
                    tx.referenceNumber.orEmpty().lowercase().contains(query) ||
                    catName.contains(query) ||
                    accName.contains(query)
                if (!matches) return@filter false
            }
            true
        }

        return when (filters.sortOrder) {
            TransactionSortOrder.NEWEST -> filtered.sortedByDescending { it.transactionDateMillis }
            TransactionSortOrder.HIGHEST_AMOUNT -> filtered.sortedByDescending { it.amountMinorUnits }
            TransactionSortOrder.LOWEST_AMOUNT -> filtered.sortedBy { it.amountMinorUnits }
        }
    }

    private data class FourBounds(val start: Long, val end: Long, val prevStart: Long, val prevEnd: Long)

    private fun computeAnalyticsBounds(range: AnalyticsTimeRange): FourBounds {
        val now = Calendar.getInstance()
        val endOfToday = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return when (range) {
            AnalyticsTimeRange.THIS_WEEK -> {
                val start = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_MONTH, -6)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val prevStart = start - 7L * 86_400_000L
                val prevEnd = start - 1L
                FourBounds(start, endOfToday, prevStart, prevEnd)
            }
            AnalyticsTimeRange.THIS_MONTH -> {
                val (s, e) = FinancialCalculator.getMonthRange(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
                val prev = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                val (ps, pe) = FinancialCalculator.getMonthRange(prev.get(Calendar.YEAR), prev.get(Calendar.MONTH))
                FourBounds(s, e, ps, pe)
            }
            AnalyticsTimeRange.LAST_MONTH -> {
                val last = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                val (s, e) = FinancialCalculator.getMonthRange(last.get(Calendar.YEAR), last.get(Calendar.MONTH))
                val twoAgo = (now.clone() as Calendar).apply { add(Calendar.MONTH, -2) }
                val (ps, pe) = FinancialCalculator.getMonthRange(twoAgo.get(Calendar.YEAR), twoAgo.get(Calendar.MONTH))
                FourBounds(s, e, ps, pe)
            }
            AnalyticsTimeRange.LAST_THREE_MONTHS -> {
                val startCal = (now.clone() as Calendar).apply {
                    add(Calendar.MONTH, -2)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = startCal.timeInMillis
                val prevStartCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, -3) }
                FourBounds(start, endOfToday, prevStartCal.timeInMillis, start - 1L)
            }
        }
    }

    // ========================================================================
    // User Actions
    // ========================================================================
    fun shiftSelectedMonth(delta: Int) {
        selectionState.update { current ->
            val cal = Calendar.getInstance().apply {
                set(current.year, current.month, 1)
                add(Calendar.MONTH, delta)
            }
            current.copy(year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH))
        }
    }

    fun selectActiveCurrency(code: String) {
        selectionState.update { it.copy(currencyOverride = code) }
    }

    fun updateFilters(transform: (TransactionFilters) -> TransactionFilters) {
        selectionState.update { it.copy(filters = transform(it.filters)) }
    }

    fun clearFilters() {
        selectionState.update { it.copy(filters = TransactionFilters()) }
    }

    fun setAnalyticsRange(range: AnalyticsTimeRange) {
        selectionState.update { it.copy(analyticsRange = range) }
    }

    fun setAnalyticsAccountFilter(accountId: Long?) {
        selectionState.update { it.copy(analyticsAccountId = accountId) }
    }

    fun toggleHideBalances() {
        viewModelScope.launch {
            val current = uiState.value.settings.hideBalances
            repository.setHideBalances(!current)
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { repository.setThemePreference(theme) }
    }

    fun setDefaultCurrency(code: String) {
        viewModelScope.launch {
            repository.setDefaultCurrency(code)
            selectionState.update { it.copy(currencyOverride = code) }
        }
    }

    fun setNotificationDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationDetectionEnabled(enabled) }
    }

    fun toggleAllowedPackage(pkg: String) {
        viewModelScope.launch { repository.toggleAllowedPackage(pkg) }
    }

    fun saveAccount(account: BankAccountModel) {
        viewModelScope.launch {
            repository.saveAccount(account)
            postMessage(if (account.accountId == 0L) "Account added" else "Account updated")
        }
    }

    fun setAccountActive(accountId: Long, active: Boolean) {
        viewModelScope.launch {
            repository.setAccountActive(accountId, active)
            postMessage(if (active) "Account restored" else "Account archived")
        }
    }

    fun reconcileAccountBalance(
        accountId: Long,
        reportedBalanceMinorUnits: Long,
        note: String = ""
    ) {
        viewModelScope.launch {
            runCatching {
                repository.reconcileAccountBalance(
                    accountId = accountId,
                    reportedBalanceMinorUnits = reportedBalanceMinorUnits,
                    note = note
                )
            }.onSuccess {
                postMessage("Balance reconciled with dated adjustment")
            }.onFailure { err ->
                postMessage("Reconciliation failed: ${err.message ?: "Unknown error"}")
            }
        }
    }

    fun deleteBalanceAdjustment(adjustmentId: Long) {
        viewModelScope.launch {
            repository.deleteBalanceAdjustment(adjustmentId)
            postMessage("Reconciliation adjustment removed")
        }
    }

    fun saveTransaction(tx: TransactionModel, learnMerchantRule: Boolean = true) {
        viewModelScope.launch {
            repository.saveTransaction(tx, learnMerchantCategory = learnMerchantRule)
            postMessage(if (tx.transactionId == 0L) "Transaction recorded" else "Transaction updated")
        }
    }

    fun approvePendingTransaction(tx: TransactionModel) {
        viewModelScope.launch {
            repository.approvePendingTransaction(tx, learnMerchantRule = true)
            postMessage("Transaction confirmed & learned")
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
            postMessage("Transaction deleted")
        }
    }

    fun addCustomCategory(name: String, type: CategoryType, iconKey: String, colorHex: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCustomCategory(name, type, iconKey, colorHex)
            postMessage("Category \"$name\" added")
        }
    }

    fun deleteCustomCategory(categoryId: Long) {
        viewModelScope.launch {
            repository.deleteCustomCategory(categoryId)
            postMessage("Custom category removed")
        }
    }

    fun saveBudget(categoryId: Long, amountMinorUnits: Long, currencyCode: String, threshold: Int = 80) {
        viewModelScope.launch {
            repository.upsertBudget(categoryId, amountMinorUnits, currencyCode, threshold)
            postMessage("Monthly budget saved")
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            repository.deleteBudget(budgetId)
            postMessage("Budget removed")
        }
    }

    fun simulateBankNotification(sourcePackage: String, title: String, body: String) {
        viewModelScope.launch {
            val outcome = repository.processIncomingNotification(
                sourcePackage = sourcePackage,
                title = title,
                body = body,
                timestampMillis = System.currentTimeMillis(),
                enforcePackageAllowList = false
            )
            _lastParseOutcome.value = outcome
            val msg = when (outcome) {
                is ParseOutcome.Parsed -> {
                    val statusLabel = if (outcome.transaction.transactionStatus == TransactionStatus.CONFIRMED) {
                        "Auto-recorded"
                    } else {
                        "Queued for review"
                    }
                    "$statusLabel: ${outcome.transaction.displayCounterparty}"
                }
                is ParseOutcome.Duplicate -> "Duplicate skipped: ${outcome.reason}"
                is ParseOutcome.Ignored -> outcome.reason
            }
            postMessage(msg)
        }
    }

    fun clearLastParseOutcome() {
        _lastParseOutcome.value = null
    }

    // Security & App Lock
    fun configurePin(pin: String) {
        viewModelScope.launch {
            repository.configurePin(pin)
            postMessage("App Lock PIN enabled")
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAppLockEnabled(enabled)
            postMessage(if (enabled) "App Lock enabled" else "App Lock disabled")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricEnabled(enabled) }
    }

    fun saveUserProfile(name: String, age: String, email: String, address: String) {
        viewModelScope.launch {
            repository.saveUserProfile(name, age, email, address)
        }
    }

    fun lockNow() {
        if (uiState.value.settings.appLockEnabled && uiState.value.settings.hasPinConfigured) {
            _isLocked.value = true
        }
    }

    fun unlockWithPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.verifyPin(pin)
            if (ok) _isLocked.value = false
            onResult(ok)
        }
    }

    fun unlockWithBiometricSuccess() {
        _isLocked.value = false
    }

    // Data Export / Backup / Reset
    suspend fun generateCsvContent(): String = repository.generateCsvExport()

    suspend fun generateEncryptedBackupContent(passphrase: String): String =
        repository.createEncryptedBackup(passphrase)

    fun restoreEncryptedBackup(content: String, passphrase: String) {
        viewModelScope.launch {
            val result = repository.restoreEncryptedBackup(content, passphrase)
            result.onSuccess { count ->
                postMessage("Restored encrypted backup ($count transactions)")
            }.onFailure { err ->
                postMessage("Restore failed: ${err.message ?: "Wrong passphrase or invalid file"}")
            }
        }
    }

    fun clearTransactionsOnly() {
        viewModelScope.launch {
            repository.clearTransactionsOnly()
            postMessage("All transactions cleared")
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAllUserData()
            postMessage("All local data wiped")
        }
    }

    fun markOnboardingCompleted() {
        viewModelScope.launch {
            repository.markOnboardingCompleted()
        }
    }

    fun postMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private data class CoreDatabaseSnapshot(
        val accounts: List<BankAccountModel>,
        val categories: List<CategoryModel>,
        val transactions: List<TransactionModel>,
        val budgets: List<BudgetModel>,
        val balanceAdjustments: List<BalanceAdjustmentModel>
    )

    private data class DatabaseSnapshot(
        val accounts: List<BankAccountModel>,
        val categories: List<CategoryModel>,
        val transactions: List<TransactionModel>,
        val budgets: List<BudgetModel>,
        val balanceAdjustments: List<BalanceAdjustmentModel>,
        val records: List<NotificationRecordModel>
    )

    companion object {
        fun factory(repository: SpendWiseRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SpendWiseViewModel(repository) as T
                }
            }
    }
}
