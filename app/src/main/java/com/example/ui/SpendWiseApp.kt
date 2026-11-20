package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.ui.components.AppLockOverlay
import com.example.ui.components.SpendWiseLaunchSplash
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AddEditTransactionSheet
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InitialSetupFlow
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen

enum class SpendWiseTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    TRANSACTIONS("transactions", "Activity", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    ANALYTICS("analytics", "Analytics", Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    ACCOUNTS("accounts", "Accounts", Icons.Rounded.AccountBalance, Icons.Outlined.AccountBalance),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

@Composable
fun SpendWiseApp(
    viewModel: SpendWiseViewModel,
    notificationAccessGranted: Boolean
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val lastParseOutcome by viewModel.lastParseOutcome.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()

    var showLaunchSplash by rememberSaveable { mutableStateOf(true) }
    var manualSetupFlowOpen by remember { mutableStateOf(false) }

    var currentTab by remember { mutableStateOf(SpendWiseTab.HOME) }
    var showTransactionSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionModel?>(null) }
    var initialAddType by remember { mutableStateOf(TransactionType.DEBIT) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // 1. Launch Splash Animation (logo with two arrows gliding to two sides of screen)
    if (showLaunchSplash) {
        SpendWiseLaunchSplash(
            onAnimationFinished = { showLaunchSplash = false }
        )
        return
    }

    // 2. App Lock Gate
    if (isLocked) {
        AppLockOverlay(
            biometricEnabled = state.settings.biometricEnabled,
            onVerifyPin = { pin, cb -> viewModel.unlockWithPin(pin, cb) },
            onBiometricSuccess = { viewModel.unlockWithBiometricSuccess() }
        )
        return
    }

    // 3. First-time initial setup (add accounts + starting balance + enable notification access)
    val shouldShowInitialSetup = !state.isLoading &&
        !state.settings.hasCompletedOnboarding &&
        state.accounts.isEmpty() &&
        state.allTransactions.isEmpty()

    if (shouldShowInitialSetup || manualSetupFlowOpen) {
        InitialSetupFlow(
            settings = state.settings,
            existingAccounts = state.accounts,
            notificationAccessGranted = notificationAccessGranted,
            onSaveProfile = viewModel::saveUserProfile,
            onConfigurePin = viewModel::configurePin,
            onSetBiometricEnabled = viewModel::setBiometricEnabled,
            onSaveAccount = viewModel::saveAccount,
            onFinishSetup = {
                if (shouldShowInitialSetup) {
                    viewModel.markOnboardingCompleted()
                }
                manualSetupFlowOpen = false
                currentTab = SpendWiseTab.HOME
            },
            onNotify = viewModel::postMessage
        )
        return
    }

    // Back button returns cleanly from secondary tabs to Home
    BackHandler(enabled = currentTab != SpendWiseTab.HOME) {
        currentTab = SpendWiseTab.HOME
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        SpendWiseTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        pendingCount = state.pendingReviewTransactions.size
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        SpendWiseTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        pendingCount = state.pendingReviewTransactions.size
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 760.dp)
                    ) {
                        when (currentTab) {
                            SpendWiseTab.HOME -> HomeScreen(
                                state = state,
                                notificationAccessGranted = notificationAccessGranted,
                                onShiftMonth = viewModel::shiftSelectedMonth,
                                onSelectCurrency = viewModel::selectActiveCurrency,
                                onToggleHideBalances = viewModel::toggleHideBalances,
                                onOpenAddTransaction = { type ->
                                    editingTransaction = null
                                    initialAddType = type
                                    showTransactionSheet = true
                                },
                                onOpenTransactionDetail = { tx ->
                                    editingTransaction = tx
                                    showTransactionSheet = true
                                },
                                onNavigateToTransactions = { pendingOnly ->
                                    if (pendingOnly) {
                                        viewModel.updateFilters {
                                            it.copy(statusFilter = TransactionStatus.PENDING_REVIEW, typeFilter = null)
                                        }
                                    } else {
                                        viewModel.clearFilters()
                                    }
                                    currentTab = SpendWiseTab.TRANSACTIONS
                                },
                                onNavigateToAnalytics = { currentTab = SpendWiseTab.ANALYTICS },
                                onNavigateToAccounts = { currentTab = SpendWiseTab.ACCOUNTS },
                                onNavigateToSettings = { currentTab = SpendWiseTab.SETTINGS }
                            )

                            SpendWiseTab.TRANSACTIONS -> TransactionsScreen(
                                state = state,
                                onUpdateFilters = viewModel::updateFilters,
                                onClearFilters = viewModel::clearFilters,
                                onOpenAddTransaction = {
                                    editingTransaction = null
                                    initialAddType = TransactionType.DEBIT
                                    showTransactionSheet = true
                                },
                                onSelectTransaction = { tx ->
                                    editingTransaction = tx
                                    showTransactionSheet = true
                                }
                            )

                            SpendWiseTab.ANALYTICS -> AnalyticsScreen(
                                state = state,
                                onSelectRange = viewModel::setAnalyticsRange,
                                onSelectAccountFilter = viewModel::setAnalyticsAccountFilter,
                                onSaveBudget = { catId, minor, code ->
                                    viewModel.saveBudget(catId, minor, code)
                                },
                                onDeleteBudget = viewModel::deleteBudget
                            )

                            SpendWiseTab.ACCOUNTS -> AccountsScreen(
                                state = state,
                                onSaveAccount = viewModel::saveAccount,
                                onSetAccountActive = viewModel::setAccountActive,
                                onReconcileBalance = viewModel::reconcileAccountBalance,
                                onDeleteAdjustment = viewModel::deleteBalanceAdjustment,
                                onSelectTransaction = { tx ->
                                    editingTransaction = tx
                                    showTransactionSheet = true
                                }
                            )

                            SpendWiseTab.SETTINGS -> SettingsScreen(
                                state = state,
                                notificationAccessGranted = notificationAccessGranted,
                                lastParseOutcome = lastParseOutcome,
                                onSetTheme = viewModel::setThemePreference,
                                onSetDefaultCurrency = viewModel::setDefaultCurrency,
                                onToggleHideBalances = viewModel::toggleHideBalances,
                                onSetDetectionEnabled = viewModel::setNotificationDetectionEnabled,
                                onSimulateNotification = viewModel::simulateBankNotification,
                                onClearLastOutcome = viewModel::clearLastParseOutcome,
                                onConfigurePin = viewModel::configurePin,
                                onSetAppLockEnabled = viewModel::setAppLockEnabled,
                                onSetBiometricEnabled = viewModel::setBiometricEnabled,
                                onLockNow = viewModel::lockNow,
                                onAddCategory = viewModel::addCustomCategory,
                                onDeleteCustomCategory = viewModel::deleteCustomCategory,
                                onGenerateCsv = viewModel::generateCsvContent,
                                onGenerateEncryptedBackup = viewModel::generateEncryptedBackupContent,
                                onRestoreEncryptedBackup = viewModel::restoreEncryptedBackup,
                                onClearTransactions = viewModel::clearTransactionsOnly,
                                onDeleteAllData = viewModel::deleteAllData,
                                onOpenInitialSetup = { manualSetupFlowOpen = true },
                                onNotify = viewModel::postMessage
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTransactionSheet) {
        AddEditTransactionSheet(
            existingTransaction = editingTransaction,
            initialType = initialAddType,
            defaultCurrency = state.activeCurrency,
            accounts = state.accounts,
            categories = state.categories,
            onDismiss = {
                showTransactionSheet = false
                editingTransaction = null
            },
            onSave = { tx, learn ->
                viewModel.saveTransaction(tx, learnMerchantRule = learn)
            },
            onApprovePending = { tx ->
                viewModel.approvePendingTransaction(tx)
            },
            onDelete = { id ->
                viewModel.deleteTransaction(id)
            }
        )
    }
}

@Composable
private fun TabIconWithBadge(
    tab: SpendWiseTab,
    selected: Boolean,
    pendingCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab == SpendWiseTab.TRANSACTIONS && pendingCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(pendingCount.coerceAtMost(99).toString()) }
            }
        ) {
            Icon(icon, contentDescription = tab.label)
        }
    } else {
        Icon(icon, contentDescription = tab.label)
    }
}
