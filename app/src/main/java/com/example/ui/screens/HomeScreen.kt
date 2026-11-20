package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountSummary
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.SpendingInsight
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionType
import com.example.ui.SpendWiseUiState
import com.example.ui.components.BankBrandBadge
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SpendWiseLogoMark
import com.example.ui.components.SpendingTrendLineChart
import com.example.ui.components.TransactionRowItem
import com.example.ui.theme.LocalSpendWiseColors
import com.example.ui.theme.SpendWiseTextStyles
import java.util.Calendar

@Composable
fun HomeScreen(
    state: SpendWiseUiState,
    notificationAccessGranted: Boolean,
    onShiftMonth: (Int) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onToggleHideBalances: () -> Unit,
    onOpenAddTransaction: (TransactionType) -> Unit,
    onOpenTransactionDetail: (TransactionModel) -> Unit,
    onNavigateToTransactions: (pendingOnly: Boolean) -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.categoryId } }
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.accountId } }
    val hide = state.settings.hideBalances
    var selectedDonutSlice by remember(state.homeCategorySpending) { mutableIntStateOf(0) }

    val timeGreeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }
    val enteredName = state.settings.userName.trim()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Header with Brand Logo, System-Time Greeting + User Name, Detection Status & Settings Shortcut
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    SpendWiseLogoMark(size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (enteredName.isNotEmpty()) "$timeGreeting," else timeGreeting,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("home_greeting_label")
                        )
                        Text(
                            text = enteredName.ifEmpty { "SpendWise" },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_user_name_label")
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val detectionActive = notificationAccessGranted && state.settings.notificationDetectionEnabled
                    Surface(
                        modifier = Modifier
                            .testTag("home_detection_badge")
                            .clip(CircleShape)
                            .clickable(onClick = onNavigateToSettings),
                        shape = CircleShape,
                        color = if (detectionActive) swColors.incomeContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (detectionActive) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                contentDescription = "Notification detection status",
                                tint = if (detectionActive) swColors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (detectionActive) "Auto On" else "Auto Off",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (detectionActive) swColors.income else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Open Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Month Switcher & Multi-currency Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, swColors.cardBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onShiftMonth(-1) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous month",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = state.selectedMonthLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { onShiftMonth(1) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("home_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next month",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (state.consolidatedByCurrency.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.consolidatedByCurrency.forEach { summary ->
                            val selected = summary.currencyCode == state.activeCurrency
                            Surface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSelectCurrency(summary.currencyCode) },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = if (selected) null else BorderStroke(1.dp, swColors.cardBorder)
                            ) {
                                Text(
                                    text = summary.currencyCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Financial Overview Hero Card
        item {
            FinancialOverviewHeroCard(
                state = state,
                onToggleHideBalances = onToggleHideBalances
            )
        }

        // 4. Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Rounded.Add,
                    label = "Add Txn",
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick = { onOpenAddTransaction(TransactionType.DEBIT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_transaction")
                )
                QuickActionTile(
                    icon = Icons.AutoMirrored.Rounded.CompareArrows,
                    label = "Transfer",
                    tint = MaterialTheme.colorScheme.secondary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { onOpenAddTransaction(TransactionType.INTERNAL_TRANSFER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_transfer")
                )
                QuickActionTile(
                    icon = if (state.pendingReviewTransactions.isNotEmpty()) Icons.Rounded.RateReview else Icons.Rounded.AccountBalance,
                    label = if (state.pendingReviewTransactions.isNotEmpty()) {
                        "Review (${state.pendingReviewTransactions.size})"
                    } else {
                        "Accounts"
                    },
                    tint = if (state.pendingReviewTransactions.isNotEmpty()) swColors.attention else MaterialTheme.colorScheme.secondary,
                    containerColor = if (state.pendingReviewTransactions.isNotEmpty()) swColors.attentionContainer else MaterialTheme.colorScheme.secondaryContainer,
                    onClick = {
                        if (state.pendingReviewTransactions.isNotEmpty()) {
                            onNavigateToTransactions(true)
                        } else {
                            onNavigateToAccounts()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_review")
                )
                QuickActionTile(
                    icon = Icons.Rounded.BarChart,
                    label = "Analytics",
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onNavigateToAnalytics,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_analytics")
                )
            }
        }

        // 5. Pending Review Banner (when auto-detected transactions need user confirmation)
        if (state.pendingReviewTransactions.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_pending_review_banner")
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onNavigateToTransactions(true) },
                    shape = MaterialTheme.shapes.medium,
                    color = swColors.attentionContainer,
                    border = BorderStroke(1.dp, swColors.attention.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Pending review",
                            tint = swColors.attention,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${state.pendingReviewTransactions.size} detected transaction(s) need review",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Confirm account and category so your balances stay accurate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Review now",
                            tint = swColors.attention
                        )
                    }
                }
            }
        }

        // 6. Local Factual Spending Insights Strip
        if (state.insights.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(title = "Spending Insights")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.insights) { insight ->
                            InsightCard(insight = insight)
                        }
                    }
                }
            }
        }

        // 7. Spending Breakdown Donut Card + Top Categories
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, swColors.cardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SectionHeader(
                        title = "Category Spending",
                        actionLabel = "Analytics",
                        onActionClick = onNavigateToAnalytics
                    )
                    Spacer(Modifier.height(12.dp))

                    if (state.homeCategorySpending.isEmpty()) {
                        Text(
                            text = "No confirmed expenses recorded for ${state.selectedMonthLabel} yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CategoryDonutChart(
                                items = state.homeCategorySpending.take(6),
                                currencyCode = state.activeCurrency,
                                hideBalances = hide,
                                chartSize = 152.dp,
                                strokeWidth = 20.dp,
                                selectedIndex = selectedDonutSlice,
                                onSelectSlice = { selectedDonutSlice = it }
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                state.homeCategorySpending.take(4).forEachIndexed { idx, cat ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedDonutSlice = idx }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(cat.category.colorHex))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = cat.category.categoryName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${cat.percentage.toInt()}%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Monthly Spending Trend Mini Chart
        if (state.homeDailyTrend.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, swColors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        SectionHeader(title = "Monthly Spending Trend")
                        Spacer(Modifier.height(10.dp))
                        SpendingTrendLineChart(
                            points = state.homeDailyTrend,
                            currencyCode = state.activeCurrency,
                            hideBalances = hide,
                            chartHeight = 132.dp
                        )
                    }
                }
            }
        }

        // 9. Tracked Bank Accounts Summary
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "Tracked Accounts",
                    actionLabel = "Manage",
                    onActionClick = onNavigateToAccounts
                )
                val activeAccounts = state.accountSummaries.filter { it.account.isActive }
                if (activeAccounts.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.AccountBalance,
                        title = "No bank accounts yet",
                        description = "Add your bank accounts with their last 4 digits to track balances and match notifications automatically.",
                        actionLabel = "Add Account",
                        onAction = onNavigateToAccounts
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(activeAccounts, key = { it.account.accountId }) { summary ->
                            MiniAccountSummaryCard(
                                summary = summary,
                                hideBalances = hide,
                                onClick = onNavigateToAccounts
                            )
                        }
                    }
                }
            }
        }

        // 10. Recent Transactions
        item {
            SectionHeader(
                title = "Recent Activity",
                actionLabel = "View All",
                onActionClick = { onNavigateToTransactions(false) }
            )
        }

        if (state.recentTransactions.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "Your ledger is empty",
                    description = "Enable live notification detection, add a bank account, or record your first transaction manually. Tracking starts from setup onwards.",
                    actionLabel = "Add Transaction",
                    onAction = { onOpenAddTransaction(TransactionType.DEBIT) }
                )
            }
        } else {
            items(state.recentTransactions, key = { it.transactionId }) { tx ->
                TransactionRowItem(
                    transaction = tx,
                    category = categoryMap[tx.categoryId],
                    account = accountMap[tx.accountId],
                    hideBalances = hide,
                    onClick = { onOpenTransactionDetail(tx) }
                )
            }
        }
    }
}

@Composable
private fun FinancialOverviewHeroCard(
    state: SpendWiseUiState,
    onToggleHideBalances: () -> Unit
) {
    val swColors = LocalSpendWiseColors.current
    val summary = state.activeConsolidated
    val hide = state.settings.hideBalances

    val totalVolume = (summary.totalIncomeMinorUnits + summary.totalExpenseMinorUnits).coerceAtLeast(1L)
    val expenseShare = (summary.totalExpenseMinorUnits.toFloat() / totalVolume.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(swColors.heroStart, swColors.heroMid, swColors.heroEnd)
                )
            )
    ) {
        // Subtle atmospheric geometric circles
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.height * 0.65f,
                center = Offset(size.width * 0.92f, size.height * 0.12f)
            )
            drawCircle(
                color = Color(0xFFF97316).copy(alpha = 0.16f),
                radius = size.height * 0.35f,
                center = Offset(size.width * 0.10f, size.height * 0.95f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Estimated Tracked Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "${summary.accountCount} active ${summary.currencyCode} account(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
                IconButton(
                    onClick = onToggleHideBalances,
                    modifier = Modifier.testTag("toggle_balance_visibility")
                ) {
                    Icon(
                        imageVector = if (hide) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (hide) "Show balances" else "Hide balances",
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = MoneyFormatter.formatMinorUnits(
                    summary.estimatedTrackedBalanceMinorUnits,
                    summary.currencyCode,
                    isHidden = hide
                ),
                style = SpendWiseTextStyles.HeroAmount,
                color = Color.White,
                modifier = Modifier.testTag("hero_balance_amount")
            )

            Spacer(Modifier.height(16.dp))

            // Income vs Expense Summary Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroMetricPill(
                    icon = Icons.Rounded.ArrowDownward,
                    label = "Income",
                    amount = MoneyFormatter.formatMinorUnits(summary.totalIncomeMinorUnits, summary.currencyCode, isHidden = hide),
                    accentColor = Color(0xFF6EE7B7),
                    modifier = Modifier.weight(1f)
                )
                HeroMetricPill(
                    icon = Icons.Rounded.ArrowUpward,
                    label = "Expenses",
                    amount = MoneyFormatter.formatMinorUnits(summary.totalExpenseMinorUnits, summary.currencyCode, isHidden = hide),
                    accentColor = Color(0xFFFDBA74),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Net cash flow + visual ratio bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Net Cash Flow",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Text(
                    text = MoneyFormatter.formatMinorUnits(
                        summary.netCashFlowMinorUnits,
                        summary.currencyCode,
                        showSign = true,
                        isHidden = hide
                    ),
                    style = SpendWiseTextStyles.RowAmount.copy(fontWeight = FontWeight.Bold),
                    color = if (summary.netCashFlowMinorUnits >= 0L) Color(0xFFA7F3D0) else Color(0xFFFED7AA)
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            ) {
                val incomeBarShare = (1f - expenseShare).coerceIn(0.05f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(incomeBarShare)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6EE7B7))
                )
            }
        }
    }
}

@Composable
private fun HeroMetricPill(
    icon: ImageVector,
    label: String,
    amount: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = amount,
                    style = SpendWiseTextStyles.RowAmount.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, swColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InsightCard(insight: SpendingInsight) {
    val swColors = LocalSpendWiseColors.current
    val accent = when {
        insight.isWarning -> swColors.expense
        insight.isPositive -> swColors.income
        else -> MaterialTheme.colorScheme.secondary
    }
    val container = when {
        insight.isWarning -> swColors.expenseContainer
        insight.isPositive -> swColors.incomeContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Surface(
        modifier = Modifier.width(250.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (insight.isWarning) Icons.Rounded.WarningAmber else Icons.Rounded.TipsAndUpdates,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = insight.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun MiniAccountSummaryCard(
    summary: AccountSummary,
    hideBalances: Boolean,
    onClick: () -> Unit
) {
    val swColors = LocalSpendWiseColors.current
    val acc = summary.account
    Surface(
        modifier = Modifier
            .width(196.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, swColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankBrandBadge(bankName = acc.bankName, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = acc.accountNickname,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (acc.accountNumberSuffix.isNotEmpty()) {
                            "XX${acc.accountNumberSuffix} · ${acc.accountType.displayName}"
                        } else {
                            acc.accountType.displayName
                        },
                        style = SpendWiseTextStyles.MonoBadge,
                        color = swColors.mutedText,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Est. Balance",
                style = MaterialTheme.typography.labelSmall,
                color = swColors.mutedText
            )
            Text(
                text = MoneyFormatter.formatMinorUnits(
                    summary.estimatedBalanceMinorUnits,
                    acc.currencyCode,
                    isHidden = hideBalances
                ),
                style = SpendWiseTextStyles.RowAmount.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
