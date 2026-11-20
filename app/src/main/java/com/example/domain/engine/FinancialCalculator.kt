package com.example.domain.engine

import com.example.domain.model.AccountSummary
import com.example.domain.model.BalanceAdjustmentModel
import com.example.domain.model.BankAccountModel
import com.example.domain.model.BudgetModel
import com.example.domain.model.BudgetProgress
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategorySpendSummary
import com.example.domain.model.CurrencyConsolidatedSummary
import com.example.domain.model.DailySpendPoint
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.RecipientSpendSummary
import com.example.domain.model.SpendingInsight
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Pure, deterministic financial calculation engine operating strictly on integer minor units.
 *
 * Key invariants:
 * - Never uses floating-point arithmetic for monetary balances (uses integer paise for INR).
 * - Never sums unlike currencies into a single balance; produces per-currency summaries.
 * - Only TransactionStatus.CONFIRMED transactions affect balances, income, and expenses.
 *   Pending, ignored, failed, or duplicate records never change balances.
 * - Dated manual balance adjustments (reconciliations) update estimated tracked balances without
 *   overwriting starting balances or inflating income/expense totals.
 * - Internal transfers update individual account balances accurately (out of source, into target)
 *   but are excluded from consolidated income and expense analytics to prevent double-counting.
 */
object FinancialCalculator {

    fun calculateSingleAccountEstimatedBalance(
        account: BankAccountModel,
        allTransactions: List<TransactionModel>,
        balanceAdjustments: List<BalanceAdjustmentModel> = emptyList()
    ): Long {
        var estimatedBalance = account.openingBalanceMinorUnits
        for (tx in allTransactions) {
            if (tx.transactionStatus != TransactionStatus.CONFIRMED) continue
            if (!tx.currencyCode.equals(account.currencyCode, ignoreCase = true)) continue
            val isSource = tx.accountId == account.accountId
            val isTarget = tx.targetAccountId == account.accountId
            if (!isSource && !isTarget) continue

            when (tx.transactionType) {
                TransactionType.CREDIT -> if (isSource) estimatedBalance += tx.amountMinorUnits
                TransactionType.DEBIT -> if (isSource) estimatedBalance -= tx.amountMinorUnits
                TransactionType.INTERNAL_TRANSFER -> {
                    if (isSource) estimatedBalance -= tx.amountMinorUnits
                    if (isTarget) estimatedBalance += tx.amountMinorUnits
                }
            }
        }
        for (adj in balanceAdjustments) {
            if (adj.accountId == account.accountId &&
                adj.currencyCode.equals(account.currencyCode, ignoreCase = true)
            ) {
                estimatedBalance += adj.deltaMinorUnits
            }
        }
        return estimatedBalance
    }

    fun calculateAccountSummaries(
        accounts: List<BankAccountModel>,
        allTransactions: List<TransactionModel>,
        periodStartMillis: Long,
        periodEndMillis: Long,
        balanceAdjustments: List<BalanceAdjustmentModel> = emptyList()
    ): List<AccountSummary> {
        val confirmed = allTransactions.filter { it.transactionStatus == TransactionStatus.CONFIRMED }
        val adjustmentsByAccount = balanceAdjustments.groupBy { it.accountId }

        return accounts.map { account ->
            var estimatedBalance = account.openingBalanceMinorUnits
            var periodIncome = 0L
            var periodExpense = 0L
            var txCount = 0

            for (tx in confirmed) {
                val isSource = tx.accountId == account.accountId
                val isTarget = tx.targetAccountId == account.accountId
                if (!isSource && !isTarget) continue
                if (!tx.currencyCode.equals(account.currencyCode, ignoreCase = true)) continue

                val inPeriod = tx.transactionDateMillis in periodStartMillis..periodEndMillis
                when (tx.transactionType) {
                    TransactionType.CREDIT -> {
                        if (isSource) {
                            estimatedBalance += tx.amountMinorUnits
                            if (inPeriod) {
                                periodIncome += tx.amountMinorUnits
                                txCount++
                            }
                        }
                    }
                    TransactionType.DEBIT -> {
                        if (isSource) {
                            estimatedBalance -= tx.amountMinorUnits
                            if (inPeriod) {
                                periodExpense += tx.amountMinorUnits
                                txCount++
                            }
                        }
                    }
                    TransactionType.INTERNAL_TRANSFER -> {
                        if (isSource) {
                            estimatedBalance -= tx.amountMinorUnits
                            if (inPeriod) txCount++
                        }
                        if (isTarget) {
                            estimatedBalance += tx.amountMinorUnits
                            if (inPeriod) txCount++
                        }
                    }
                }
            }

            val accountAdjustments = adjustmentsByAccount[account.accountId].orEmpty()
                .filter { it.currencyCode.equals(account.currencyCode, ignoreCase = true) }
            val adjustmentsSum = accountAdjustments.sumOf { it.deltaMinorUnits }
            estimatedBalance += adjustmentsSum

            AccountSummary(
                account = account,
                estimatedBalanceMinorUnits = estimatedBalance,
                periodIncomeMinorUnits = periodIncome,
                periodExpenseMinorUnits = periodExpense,
                transactionCount = txCount,
                totalAdjustmentsMinorUnits = adjustmentsSum,
                lastReconciledAtMillis = accountAdjustments.maxOfOrNull { it.adjustmentDateMillis }
            )
        }
    }

    /**
     * Groups consolidated financial overview by currency so unlike currencies are never added together.
     */
    fun calculateConsolidatedByCurrency(
        accountSummaries: List<AccountSummary>,
        periodTransactions: List<TransactionModel>
    ): List<CurrencyConsolidatedSummary> {
        val confirmed = periodTransactions.filter { it.transactionStatus == TransactionStatus.CONFIRMED }
        val activeSummaries = accountSummaries.filter { it.account.isActive }

        val currencies = (activeSummaries.map { it.account.currencyCode } + confirmed.map { it.currencyCode })
            .distinct()
            .ifEmpty { listOf("INR") }

        return currencies.map { code ->
            val accountsInCurrency = activeSummaries.filter { it.account.currencyCode.equals(code, ignoreCase = true) }
            val txsInCurrency = confirmed.filter { it.currencyCode.equals(code, ignoreCase = true) }

            val totalBalance = accountsInCurrency.sumOf { it.estimatedBalanceMinorUnits }
            // Exclude internal transfers from consolidated income & expenses
            val totalIncome = txsInCurrency
                .filter { it.transactionType == TransactionType.CREDIT }
                .sumOf { it.amountMinorUnits }
            val totalExpense = txsInCurrency
                .filter { it.transactionType == TransactionType.DEBIT }
                .sumOf { it.amountMinorUnits }

            CurrencyConsolidatedSummary(
                currencyCode = code,
                estimatedTrackedBalanceMinorUnits = totalBalance,
                totalIncomeMinorUnits = totalIncome,
                totalExpenseMinorUnits = totalExpense,
                netCashFlowMinorUnits = totalIncome - totalExpense,
                accountCount = accountsInCurrency.size
            )
        }
    }

    fun calculateCategorySpending(
        transactions: List<TransactionModel>,
        categories: List<CategoryModel>,
        currencyCode: String
    ): List<CategorySpendSummary> {
        val eligibleDebits = transactions.filter {
            it.transactionStatus == TransactionStatus.CONFIRMED &&
                it.transactionType == TransactionType.DEBIT &&
                it.currencyCode.equals(currencyCode, ignoreCase = true)
        }
        val totalDebit = eligibleDebits.sumOf { it.amountMinorUnits }
        if (totalDebit <= 0L) return emptyList()

        val categoryMap = categories.associateBy { it.categoryId }
        val fallbackCategory = categories.firstOrNull { it.categoryName.equals("Other", ignoreCase = true) }
            ?: CategoryModel(-1L, "Other", com.example.domain.model.CategoryType.EXPENSE, "more", 0xFF64748B, true, true)

        return eligibleDebits
            .groupBy { it.categoryId ?: fallbackCategory.categoryId }
            .map { (catId, txs) ->
                val cat = categoryMap[catId] ?: fallbackCategory
                val sum = txs.sumOf { it.amountMinorUnits }
                val pct = (sum.toFloat() / totalDebit.toFloat()) * 100f
                CategorySpendSummary(
                    category = cat,
                    amountMinorUnits = sum,
                    transactionCount = txs.size,
                    percentage = pct
                )
            }
            .sortedByDescending { it.amountMinorUnits }
    }

    fun calculateTopRecipients(
        transactions: List<TransactionModel>,
        currencyCode: String,
        limit: Int = 8
    ): List<RecipientSpendSummary> {
        val eligibleDebits = transactions.filter {
            it.transactionStatus == TransactionStatus.CONFIRMED &&
                it.transactionType == TransactionType.DEBIT &&
                it.currencyCode.equals(currencyCode, ignoreCase = true)
        }
        val totalDebit = eligibleDebits.sumOf { it.amountMinorUnits }
        if (totalDebit <= 0L) return emptyList()

        return eligibleDebits
            .groupBy { it.displayCounterparty.trim().ifEmpty { "Unknown" } }
            .map { (name, txs) ->
                val sum = txs.sumOf { it.amountMinorUnits }
                RecipientSpendSummary(
                    name = name,
                    amountMinorUnits = sum,
                    transactionCount = txs.size,
                    percentage = (sum.toFloat() / totalDebit.toFloat()) * 100f
                )
            }
            .sortedByDescending { it.amountMinorUnits }
            .take(limit)
    }

    fun calculateDailyTrend(
        transactions: List<TransactionModel>,
        currencyCode: String,
        startMillis: Long,
        endMillis: Long
    ): List<DailySpendPoint> {
        val confirmed = transactions.filter {
            it.transactionStatus == TransactionStatus.CONFIRMED &&
                it.currencyCode.equals(currencyCode, ignoreCase = true) &&
                it.transactionDateMillis in startMillis..endMillis &&
                it.transactionType != TransactionType.INTERNAL_TRANSFER
        }

        val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        val cal = Calendar.getInstance()
        val grouped = mutableMapOf<Long, Pair<Long, Long>>() // dayStart -> (expense, income)

        for (tx in confirmed) {
            cal.timeInMillis = tx.transactionDateMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayKey = cal.timeInMillis
            val current = grouped[dayKey] ?: (0L to 0L)
            grouped[dayKey] = if (tx.transactionType == TransactionType.DEBIT) {
                (current.first + tx.amountMinorUnits) to current.second
            } else {
                current.first to (current.second + tx.amountMinorUnits)
            }
        }

        return grouped.entries
            .sortedBy { it.key }
            .map { (dayKey, totals) ->
                DailySpendPoint(
                    label = dayFormat.format(Date(dayKey)),
                    dayKey = dayKey,
                    expenseMinorUnits = totals.first,
                    incomeMinorUnits = totals.second
                )
            }
    }

    fun calculateBudgetProgress(
        budgets: List<BudgetModel>,
        categories: List<CategoryModel>,
        monthTransactions: List<TransactionModel>
    ): List<BudgetProgress> {
        val catMap = categories.associateBy { it.categoryId }
        val confirmedDebits = monthTransactions.filter {
            it.transactionStatus == TransactionStatus.CONFIRMED &&
                it.transactionType == TransactionType.DEBIT
        }

        return budgets
            .filter { it.isActive && it.amountMinorUnits > 0L }
            .mapNotNull { budget ->
                val category = catMap[budget.categoryId] ?: return@mapNotNull null
                val spent = confirmedDebits
                    .filter {
                        it.categoryId == budget.categoryId &&
                            it.currencyCode.equals(budget.currencyCode, ignoreCase = true)
                    }
                    .sumOf { it.amountMinorUnits }
                val remaining = budget.amountMinorUnits - spent
                val pct = (spent.toFloat() / budget.amountMinorUnits.toFloat()) * 100f
                BudgetProgress(
                    budget = budget,
                    category = category,
                    spentMinorUnits = spent,
                    remainingMinorUnits = remaining,
                    percentUsed = pct,
                    isOverBudget = spent > budget.amountMinorUnits,
                    isWarning = pct >= budget.alertThresholdPercent
                )
            }
            .sortedByDescending { it.percentUsed }
    }

    /**
     * Generates factual, local-only spending insights strictly from recorded database transactions.
     * Never fabricates patterns when insufficient history exists.
     */
    fun generateLocalInsights(
        currentMonthTxs: List<TransactionModel>,
        previousMonthTxs: List<TransactionModel>,
        categorySummaries: List<CategorySpendSummary>,
        budgetProgressList: List<BudgetProgress>,
        currencyCode: String
    ): List<SpendingInsight> {
        val insights = mutableListOf<SpendingInsight>()

        val currentSpend = currentMonthTxs
            .filter {
                it.transactionStatus == TransactionStatus.CONFIRMED &&
                    it.transactionType == TransactionType.DEBIT &&
                    it.currencyCode.equals(currencyCode, ignoreCase = true)
            }
            .sumOf { it.amountMinorUnits }

        val prevSpend = previousMonthTxs
            .filter {
                it.transactionStatus == TransactionStatus.CONFIRMED &&
                    it.transactionType == TransactionType.DEBIT &&
                    it.currencyCode.equals(currencyCode, ignoreCase = true)
            }
            .sumOf { it.amountMinorUnits }

        if (currentSpend > 0L && prevSpend > 0L) {
            val diff = currentSpend - prevSpend
            val pctChange = ((abs(diff).toFloat() / prevSpend.toFloat()) * 100f).toInt()
            val formattedDiff = MoneyFormatter.formatMinorUnits(abs(diff), currencyCode)
            if (diff > 0) {
                insights += SpendingInsight(
                    title = "Spending up $pctChange% vs last month",
                    subtitle = "You spent $formattedDiff more compared to the previous month.",
                    isPositive = false,
                    isWarning = pctChange >= 15
                )
            } else if (diff < 0) {
                insights += SpendingInsight(
                    title = "Spending down $pctChange% vs last month",
                    subtitle = "You saved $formattedDiff compared to the previous month.",
                    isPositive = true
                )
            }
        }

        val topCat = categorySummaries.firstOrNull()
        if (topCat != null) {
            val amountStr = MoneyFormatter.formatMinorUnits(topCat.amountMinorUnits, currencyCode)
            insights += SpendingInsight(
                title = "${topCat.category.categoryName} is your top expense",
                subtitle = "$amountStr (${topCat.percentage.toInt()}% of tracked monthly spending).",
                isPositive = false
            )
        }

        val urgentBudget = budgetProgressList.firstOrNull { it.isOverBudget || it.isWarning }
        if (urgentBudget != null) {
            val pct = urgentBudget.percentUsed.toInt()
            insights += SpendingInsight(
                title = if (urgentBudget.isOverBudget) {
                    "${urgentBudget.category.categoryName} budget exceeded"
                } else {
                    "${urgentBudget.category.categoryName} budget at $pct%"
                },
                subtitle = if (urgentBudget.isOverBudget) {
                    "Exceeded by ${MoneyFormatter.formatMinorUnits(abs(urgentBudget.remainingMinorUnits), urgentBudget.budget.currencyCode)}."
                } else {
                    "${MoneyFormatter.formatMinorUnits(urgentBudget.remainingMinorUnits, urgentBudget.budget.currencyCode)} left for this month."
                },
                isPositive = false,
                isWarning = true
            )
        }

        return insights
    }

    fun getMonthRange(year: Int, monthZeroIndexed: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, monthZeroIndexed, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val end = cal.timeInMillis
        return start to end
    }
}
