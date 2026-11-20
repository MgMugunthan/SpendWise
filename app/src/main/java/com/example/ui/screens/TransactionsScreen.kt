package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionSortOrder
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.ui.SpendWiseUiState
import com.example.ui.TransactionFilters
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TransactionRowItem
import com.example.ui.theme.LocalSpendWiseColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    state: SpendWiseUiState,
    onUpdateFilters: ((TransactionFilters) -> TransactionFilters) -> Unit,
    onClearFilters: () -> Unit,
    onOpenAddTransaction: () -> Unit,
    onSelectTransaction: (TransactionModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    val filters = state.filters
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.categoryId } }
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.accountId } }

    var showAccountMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val grouped = remember(state.filteredTransactions) {
        groupTransactionsByDay(state.filteredTransactions)
    }

    val hasActiveFilters = filters.searchQuery.isNotBlank() ||
        filters.typeFilter != null ||
        filters.statusFilter != null ||
        filters.accountIdFilter != null ||
        filters.categoryIdFilter != null

    Box(modifier = modifier.fillMaxSize().testTag("transactions_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${state.filteredTransactions.size} record(s) · Local Room SQLite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasActiveFilters) {
                            TextButton(onClick = onClearFilters) {
                                Text("Reset")
                            }
                        }
                        Box {
                            Surface(
                                onClick = { showSortMenu = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, swColors.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Sort,
                                        contentDescription = "Sort transactions",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = filters.sortOrder.displayName,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                TransactionSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.displayName) },
                                        onClick = {
                                            onUpdateFilters { it.copy(sortOrder = order) }
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = filters.searchQuery,
                    onValueChange = { q -> onUpdateFilters { it.copy(searchQuery = q) } },
                    placeholder = { Text("Search merchant, recipient, ref, category...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (filters.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateFilters { it.copy(searchQuery = "") } }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transactions_search_input")
                )

                // Horizontal Filter Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filters.typeFilter == null && filters.statusFilter == null,
                            onClick = {
                                onUpdateFilters { it.copy(typeFilter = null, statusFilter = null) }
                            },
                            label = { Text("All") },
                            modifier = Modifier.testTag("filter_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filters.typeFilter == TransactionType.DEBIT,
                            onClick = {
                                onUpdateFilters {
                                    it.copy(
                                        typeFilter = if (it.typeFilter == TransactionType.DEBIT) null else TransactionType.DEBIT,
                                        statusFilter = null
                                    )
                                }
                            },
                            label = { Text("Expenses") },
                            modifier = Modifier.testTag("filter_debit")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filters.typeFilter == TransactionType.CREDIT,
                            onClick = {
                                onUpdateFilters {
                                    it.copy(
                                        typeFilter = if (it.typeFilter == TransactionType.CREDIT) null else TransactionType.CREDIT,
                                        statusFilter = null
                                    )
                                }
                            },
                            label = { Text("Income") },
                            modifier = Modifier.testTag("filter_credit")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filters.typeFilter == TransactionType.INTERNAL_TRANSFER,
                            onClick = {
                                onUpdateFilters {
                                    it.copy(
                                        typeFilter = if (it.typeFilter == TransactionType.INTERNAL_TRANSFER) null else TransactionType.INTERNAL_TRANSFER,
                                        statusFilter = null
                                    )
                                }
                            },
                            label = { Text("Transfers") }
                        )
                    }
                    if (state.pendingReviewTransactions.isNotEmpty() || filters.statusFilter == TransactionStatus.PENDING_REVIEW) {
                        item {
                            FilterChip(
                                selected = filters.statusFilter == TransactionStatus.PENDING_REVIEW,
                                onClick = {
                                    onUpdateFilters {
                                        it.copy(
                                            statusFilter = if (it.statusFilter == TransactionStatus.PENDING_REVIEW) null else TransactionStatus.PENDING_REVIEW,
                                            typeFilter = null
                                        )
                                    }
                                },
                                label = { Text("Review (${state.pendingReviewTransactions.size})") },
                                modifier = Modifier.testTag("filter_pending_review")
                            )
                        }
                    }

                    // Account Picker Chip
                    item {
                        Box {
                            val selectedAcc = filters.accountIdFilter?.let { accountMap[it] }
                            FilterChip(
                                selected = selectedAcc != null,
                                onClick = { showAccountMenu = true },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(selectedAcc?.accountNickname ?: "All Accounts") }
                            )
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Accounts") },
                                    onClick = {
                                        onUpdateFilters { it.copy(accountIdFilter = null) }
                                        showAccountMenu = false
                                    }
                                )
                                state.accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.bankName} · ${acc.accountNickname}") },
                                        onClick = {
                                            onUpdateFilters { it.copy(accountIdFilter = acc.accountId) }
                                            showAccountMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Category Picker Chip
                    item {
                        Box {
                            val selectedCat = filters.categoryIdFilter?.let { categoryMap[it] }
                            FilterChip(
                                selected = selectedCat != null,
                                onClick = { showCategoryMenu = true },
                                label = { Text(selectedCat?.categoryName ?: "All Categories") }
                            )
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Categories") },
                                    onClick = {
                                        onUpdateFilters { it.copy(categoryIdFilter = null) }
                                        showCategoryMenu = false
                                    }
                                )
                                state.categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.categoryName) },
                                        onClick = {
                                            onUpdateFilters { it.copy(categoryIdFilter = cat.categoryId) }
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Date-grouped transactions list
            if (grouped.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.ReceiptLong,
                        title = if (hasActiveFilters) "No matching transactions" else "No transactions recorded",
                        description = if (hasActiveFilters) {
                            "Try clearing your active filters or search terms."
                        } else {
                            "Tap the + button to log an expense, income, or account transfer."
                        },
                        actionLabel = if (hasActiveFilters) "Clear Filters" else "Add Transaction",
                        onAction = if (hasActiveFilters) onClearFilters else onOpenAddTransaction
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (dateLabel, txs) ->
                        item(key = "header_$dateLabel") {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(txs, key = { it.transactionId }) { tx ->
                            TransactionRowItem(
                                transaction = tx,
                                category = categoryMap[tx.categoryId],
                                account = accountMap[tx.accountId],
                                hideBalances = state.settings.hideBalances,
                                onClick = { onSelectTransaction(tx) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenAddTransaction,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("transactions_add_fab")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add transaction")
        }
    }
}

private fun groupTransactionsByDay(
    transactions: List<TransactionModel>
): List<Pair<String, List<TransactionModel>>> {
    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
    val headerFmt = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())

    val grouped = linkedMapOf<String, MutableList<TransactionModel>>()
    val txCal = Calendar.getInstance()

    for (tx in transactions) {
        txCal.timeInMillis = tx.transactionDateMillis
        val label = when {
            isSameDay(txCal, todayCal) -> "Today"
            isSameDay(txCal, yesterdayCal) -> "Yesterday"
            else -> headerFmt.format(Date(tx.transactionDateMillis))
        }
        grouped.getOrPut(label) { mutableListOf() }.add(tx)
    }

    return grouped.entries.map { it.key to it.value }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
