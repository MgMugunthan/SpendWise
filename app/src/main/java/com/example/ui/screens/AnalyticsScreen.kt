package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AnalyticsTimeRange
import com.example.domain.model.BudgetProgress
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategoryType
import com.example.domain.model.CurrencySpec
import com.example.domain.model.MoneyFormatter
import com.example.ui.SpendWiseUiState
import com.example.ui.components.BankBrandBadge
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IncomeExpenseComparisonChart
import com.example.ui.components.SpendingTrendLineChart
import com.example.ui.theme.LocalSpendWiseColors
import com.example.ui.theme.SpendWiseTextStyles
import kotlin.math.abs

private enum class AnalyticsTab(val label: String) {
    CATEGORY("Category"),
    TRENDS("Trends"),
    CASH_FLOW("Cash Flow"),
    BUDGETS("Budgets")
}

@Composable
fun AnalyticsScreen(
    state: SpendWiseUiState,
    onSelectRange: (AnalyticsTimeRange) -> Unit,
    onSelectAccountFilter: (Long?) -> Unit,
    onSaveBudget: (categoryId: Long, amountMinorUnits: Long, currencyCode: String) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    val hide = state.settings.hideBalances
    val currency = state.activeCurrency

    var activeTab by remember { mutableStateOf(AnalyticsTab.CATEGORY) }
    var selectedSlice by remember(state.analyticsCategorySpending) { mutableIntStateOf(0) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showAddBudgetDialog by remember { mutableStateOf(false) }

    val selectedAccount = remember(state.accounts, state.analyticsAccountId) {
        state.accounts.firstOrNull { it.accountId == state.analyticsAccountId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Segmented tab selector matching visual blueprint
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AnalyticsTab.entries.forEachIndexed { idx, tab ->
                        SegmentedButton(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            shape = SegmentedButtonDefaults.itemShape(idx, AnalyticsTab.entries.size),
                            modifier = Modifier.testTag("analytics_tab_${tab.name.lowercase()}")
                        ) {
                            Text(tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }

                // Date Range & Account Filter Row
                if (activeTab != AnalyticsTab.BUDGETS) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AnalyticsTimeRange.entries) { range ->
                            FilterChip(
                                selected = state.analyticsRange == range,
                                onClick = { onSelectRange(range) },
                                label = { Text(range.displayName) }
                            )
                        }
                        item {
                            Box {
                                FilterChip(
                                    selected = selectedAccount != null,
                                    onClick = { showAccountMenu = true },
                                    label = { Text(selectedAccount?.accountNickname ?: "All Accounts") }
                                )
                                DropdownMenu(
                                    expanded = showAccountMenu,
                                    onDismissRequest = { showAccountMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Accounts") },
                                        onClick = {
                                            onSelectAccountFilter(null)
                                            showAccountMenu = false
                                        }
                                    )
                                    state.accounts.filter { it.isActive }.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text("${acc.bankName} · ${acc.accountNickname}") },
                                            onClick = {
                                                onSelectAccountFilter(acc.accountId)
                                                showAccountMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        when (activeTab) {
            AnalyticsTab.CATEGORY -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, swColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Total Spending (${state.analyticsRange.displayName})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = MoneyFormatter.formatMinorUnits(
                                    state.analyticsTotalExpense,
                                    currency,
                                    isHidden = hide
                                ),
                                style = SpendWiseTextStyles.HeroAmount,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(18.dp))

                            CategoryDonutChart(
                                items = state.analyticsCategorySpending,
                                currencyCode = currency,
                                hideBalances = hide,
                                chartSize = 224.dp,
                                strokeWidth = 30.dp,
                                selectedIndex = selectedSlice,
                                onSelectSlice = { selectedSlice = it }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap any slice to inspect category details",
                                style = MaterialTheme.typography.labelSmall,
                                color = swColors.mutedText
                            )
                        }
                    }
                }

                if (state.analyticsCategorySpending.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.PieChart,
                            title = "No spending data in this period",
                            description = "Confirmed debit transactions will appear here grouped by category."
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "Category Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    items(state.analyticsCategorySpending, key = { it.category.categoryId }) { item ->
                        val idx = state.analyticsCategorySpending.indexOf(item)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { selectedSlice = idx },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (selectedSlice == idx) 1.5.dp else 1.dp,
                                color = if (selectedSlice == idx) Color(item.category.colorHex) else swColors.cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconBadge(
                                    iconKey = item.category.iconKey,
                                    colorHex = item.category.colorHex,
                                    size = 42.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.category.categoryName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = MoneyFormatter.formatMinorUnits(
                                                item.amountMinorUnits,
                                                currency,
                                                isHidden = hide
                                            ),
                                            style = SpendWiseTextStyles.RowAmount.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((item.percentage / 100f).coerceIn(0.03f, 1f))
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(item.category.colorHex))
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${item.percentage.toInt()}% · ${item.transactionCount} transaction(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = swColors.mutedText
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Merchants & Recipients
                if (state.analyticsTopRecipients.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top Merchants & Recipients",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, swColors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                state.analyticsTopRecipients.forEachIndexed { rank, rec ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#${rank + 1}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rec.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${rec.transactionCount} payment(s) · ${rec.percentage.toInt()}% of spend",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = swColors.mutedText
                                            )
                                        }
                                        Text(
                                            text = MoneyFormatter.formatMinorUnits(rec.amountMinorUnits, currency, isHidden = hide),
                                            style = SpendWiseTextStyles.RowAmount,
                                            color = swColors.expense
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnalyticsTab.TRENDS -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, swColors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Spending Trajectory",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tap points along the curve to inspect daily spending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = swColors.mutedText
                            )
                            Spacer(Modifier.height(14.dp))
                            if (state.analyticsDailyTrend.isEmpty()) {
                                Text(
                                    text = "No daily spending records found in this timeframe.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                SpendingTrendLineChart(
                                    points = state.analyticsDailyTrend,
                                    currencyCode = currency,
                                    hideBalances = hide,
                                    chartHeight = 180.dp
                                )
                            }
                        }
                    }
                }

                // Period-over-Period Comparison Card
                item {
                    val diff = state.analyticsTotalExpense - state.analyticsPrevExpense
                    val pctText = if (state.analyticsPrevExpense > 0L) {
                        val pct = ((abs(diff).toFloat() / state.analyticsPrevExpense.toFloat()) * 100f).toInt()
                        if (diff > 0) "+$pct% vs previous period" else "-$pct% vs previous period"
                    } else {
                        "No prior period comparison available yet"
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, swColors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Period Comparison",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Current Period", style = MaterialTheme.typography.labelSmall, color = swColors.mutedText)
                                    Text(
                                        MoneyFormatter.formatMinorUnits(state.analyticsTotalExpense, currency, isHidden = hide),
                                        style = SpendWiseTextStyles.CardAmount,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Previous Period", style = MaterialTheme.typography.labelSmall, color = swColors.mutedText)
                                    Text(
                                        MoneyFormatter.formatMinorUnits(state.analyticsPrevExpense, currency, isHidden = hide),
                                        style = SpendWiseTextStyles.CardAmount,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = pctText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (diff <= 0L) swColors.income else swColors.expense
                            )
                        }
                    }
                }

                // Account-wise Financial Activity
                if (state.analyticsAccountActivity.isNotEmpty()) {
                    item {
                        Text(
                            text = "Account-Wise Activity",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(state.analyticsAccountActivity, key = { it.account.accountId }) { accSummary ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, swColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BankBrandBadge(bankName = accSummary.account.bankName, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = accSummary.account.accountNickname,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${accSummary.transactionCount} transaction(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = swColors.mutedText
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${MoneyFormatter.formatMinorUnits(accSummary.periodIncomeMinorUnits, currency, isHidden = hide)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = swColors.income
                                    )
                                    Text(
                                        text = "-${MoneyFormatter.formatMinorUnits(accSummary.periodExpenseMinorUnits, currency, isHidden = hide)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = swColors.expense
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnalyticsTab.CASH_FLOW -> {
                item {
                    val net = state.analyticsTotalIncome - state.analyticsTotalExpense
                    val savingsRate = if (state.analyticsTotalIncome > 0L) {
                        ((net.toFloat() / state.analyticsTotalIncome.toFloat()) * 100f).toInt()
                    } else {
                        0
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, swColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Income vs Expense",
                                style = MaterialTheme.typography.titleMedium
                            )
                            IncomeExpenseComparisonChart(
                                incomeMinorUnits = state.analyticsTotalIncome,
                                expenseMinorUnits = state.analyticsTotalExpense,
                                currencyCode = currency,
                                hideBalances = hide
                            )
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (net >= 0L) swColors.incomeContainer else swColors.expenseContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Net Cash Flow",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = MoneyFormatter.formatMinorUnits(net, currency, showSign = true, isHidden = hide),
                                            style = SpendWiseTextStyles.CardAmount,
                                            color = if (net >= 0L) swColors.income else swColors.expense
                                        )
                                    }
                                    if (state.analyticsTotalIncome > 0L) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Savings Rate",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "$savingsRate%",
                                                style = SpendWiseTextStyles.CardAmount,
                                                color = if (savingsRate >= 0) swColors.income else swColors.expense
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "Internal transfers between your own bank accounts are excluded from consolidated income and expenses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = swColors.mutedText
                            )
                        }
                    }
                }
            }

            AnalyticsTab.BUDGETS -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Budgets",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = state.selectedMonthLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = swColors.mutedText
                            )
                        }
                        Button(
                            onClick = { showAddBudgetDialog = true },
                            modifier = Modifier.testTag("add_budget_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Set Limit")
                        }
                    }
                }

                if (state.budgetProgress.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Savings,
                            title = "No monthly budgets set",
                            description = "Set spending limits for Food, Shopping, or Transport to track usage and receive visual warnings.",
                            actionLabel = "Create First Budget",
                            onAction = { showAddBudgetDialog = true }
                        )
                    }
                } else {
                    items(state.budgetProgress, key = { it.budget.budgetId }) { progress ->
                        BudgetProgressCard(
                            progress = progress,
                            hideBalances = hide,
                            onDelete = { onDeleteBudget(progress.budget.budgetId) }
                        )
                    }
                }
            }
        }
    }

    if (showAddBudgetDialog) {
        SetBudgetDialog(
            expenseCategories = state.categories.filter { it.categoryType == CategoryType.EXPENSE },
            currencyCode = currency,
            onDismiss = { showAddBudgetDialog = false },
            onSave = { catId, amountMinor ->
                onSaveBudget(catId, amountMinor, currency)
                showAddBudgetDialog = false
            }
        )
    }
}

@Composable
private fun BudgetProgressCard(
    progress: BudgetProgress,
    hideBalances: Boolean,
    onDelete: () -> Unit
) {
    val swColors = LocalSpendWiseColors.current
    val statusColor = when {
        progress.isOverBudget -> MaterialTheme.colorScheme.error
        progress.isWarning -> swColors.attention
        else -> swColors.income
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, swColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBadge(
                    iconKey = progress.category.iconKey,
                    colorHex = progress.category.colorHex,
                    size = 40.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.category.categoryName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${progress.percentUsed.toInt()}% used · Limit ${MoneyFormatter.formatMinorUnits(progress.budget.amountMinorUnits, progress.budget.currencyCode, isHidden = hideBalances)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete budget",
                        tint = swColors.mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((progress.percentUsed / 100f).coerceIn(0.03f, 1f))
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spent: ${MoneyFormatter.formatMinorUnits(progress.spentMinorUnits, progress.budget.currencyCode, isHidden = hideBalances)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (progress.isOverBudget) {
                        "Over by ${MoneyFormatter.formatMinorUnits(abs(progress.remainingMinorUnits), progress.budget.currencyCode, isHidden = hideBalances)}"
                    } else {
                        "Left: ${MoneyFormatter.formatMinorUnits(progress.remainingMinorUnits, progress.budget.currencyCode, isHidden = hideBalances)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun SetBudgetDialog(
    expenseCategories: List<CategoryModel>,
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit
) {
    var selectedCat by remember { mutableStateOf(expenseCategories.firstOrNull()) }
    var limitInput by remember { mutableStateOf("5000") }
    var showCatMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    Surface(
                        onClick = { showCatMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(selectedCat?.categoryName ?: "Select Category")
                            Text("Change", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(expanded = showCatMenu, onDismissRequest = { showCatMenu = false }) {
                        expenseCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.categoryName) },
                                onClick = {
                                    selectedCat = cat
                                    showCatMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { if (it.all { ch -> ch.isDigit() || ch == '.' }) limitInput = it },
                    label = { Text("Monthly Limit (${CurrencySpec.fromCode(currencyCode).symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cat = selectedCat ?: return@Button
                    val minor = MoneyFormatter.parseMajorToMinor(limitInput, currencyCode) ?: return@Button
                    if (minor > 0L) onSave(cat.categoryId, minor)
                }
            ) {
                Text("Save Budget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
