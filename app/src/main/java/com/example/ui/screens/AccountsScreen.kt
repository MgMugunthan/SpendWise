package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountSummary
import com.example.domain.model.AccountType
import com.example.domain.model.BalanceAdjustmentModel
import com.example.domain.model.BankAccountModel
import com.example.domain.model.CurrencySpec
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.TransactionModel
import com.example.ui.SpendWiseUiState
import com.example.ui.components.BankBrandBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TransactionRowItem
import com.example.ui.theme.LocalSpendWiseColors
import com.example.ui.theme.SpendWiseTextStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountsScreen(
    state: SpendWiseUiState,
    onSaveAccount: (BankAccountModel) -> Unit,
    onSetAccountActive: (Long, Boolean) -> Unit,
    onReconcileBalance: (accountId: Long, reportedBalanceMinorUnits: Long, note: String) -> Unit,
    onDeleteAdjustment: (Long) -> Unit,
    onSelectTransaction: (TransactionModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val swColors = LocalSpendWiseColors.current
    val hide = state.settings.hideBalances
    val activeSummaries = remember(state.accountSummaries) { state.accountSummaries.filter { it.account.isActive } }
    val archivedSummaries = remember(state.accountSummaries) { state.accountSummaries.filter { !it.account.isActive } }

    var editingAccount by remember { mutableStateOf<BankAccountModel?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var inspectingAccountSummary by remember { mutableStateOf<AccountSummary?>(null) }
    var reconcilingSummary by remember { mutableStateOf<AccountSummary?>(null) }

    Box(modifier = modifier.fillMaxSize().testTag("accounts_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Bank Accounts",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Track multiple accounts with masked 4-digit suffixes only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Consolidated Multi-Currency Balance Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, swColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Consolidated Estimated Tracked Balance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.consolidatedByCurrency.forEach { consolidated ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = MoneyFormatter.formatMinorUnits(
                                            consolidated.estimatedTrackedBalanceMinorUnits,
                                            consolidated.currencyCode,
                                            isHidden = hide
                                        ),
                                        style = SpendWiseTextStyles.CardAmount,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${consolidated.accountCount} active ${consolidated.currencyCode} account(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = swColors.mutedText
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "In: ${MoneyFormatter.formatMinorUnits(consolidated.totalIncomeMinorUnits, consolidated.currencyCode, isHidden = hide)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = swColors.income
                                    )
                                    Text(
                                        text = "Out: ${MoneyFormatter.formatMinorUnits(consolidated.totalExpenseMinorUnits, consolidated.currencyCode, isHidden = hide)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = swColors.expense
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Estimated tracked balances reflect starting balance + confirmed credits − confirmed debits + reconciliation adjustments. Currencies are kept separate.",
                            style = MaterialTheme.typography.labelSmall,
                            color = swColors.mutedText
                        )
                    }
                }
            }

            if (activeSummaries.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.AccountBalance,
                        title = "Add your first bank account",
                        description = "Enter your bank name, nickname, and last 4 digits so SpendWise can automatically link detected bank notifications.",
                        actionLabel = "Add Bank Account",
                        onAction = { showAddSheet = true }
                    )
                }
            } else {
                item {
                    Text(
                        text = "Active Accounts (${activeSummaries.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(activeSummaries, key = { it.account.accountId }) { summary ->
                    BankAccountCard(
                        summary = summary,
                        hideBalances = hide,
                        onClick = { inspectingAccountSummary = summary }
                    )
                }
            }

            if (archivedSummaries.isNotEmpty()) {
                item {
                    Text(
                        text = "Archived Accounts (${archivedSummaries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(archivedSummaries, key = { it.account.accountId }) { summary ->
                    BankAccountCard(
                        summary = summary,
                        hideBalances = hide,
                        onClick = { inspectingAccountSummary = summary }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingAccount = null
                showAddSheet = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("accounts_add_fab")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add bank account")
        }
    }

    if (showAddSheet) {
        AddEditAccountSheet(
            existingAccount = editingAccount,
            defaultCurrency = state.settings.defaultCurrency,
            onDismiss = {
                showAddSheet = false
                editingAccount = null
            },
            onSave = { account ->
                onSaveAccount(account)
                showAddSheet = false
                editingAccount = null
            }
        )
    }

    inspectingAccountSummary?.let { summary ->
        val freshSummary = state.accountSummaries.firstOrNull { it.account.accountId == summary.account.accountId } ?: summary
        val accountTxs = remember(state.allTransactions, freshSummary.account.accountId) {
            state.allTransactions.filter {
                it.accountId == freshSummary.account.accountId || it.targetAccountId == freshSummary.account.accountId
            }
        }
        val accountAdjustments = remember(state.balanceAdjustments, freshSummary.account.accountId) {
            state.balanceAdjustments.filter { it.accountId == freshSummary.account.accountId }
        }
        AccountDetailSheet(
            summary = freshSummary,
            transactions = accountTxs,
            adjustments = accountAdjustments,
            state = state,
            onDismiss = { inspectingAccountSummary = null },
            onReconcile = {
                reconcilingSummary = freshSummary
            },
            onDeleteAdjustment = onDeleteAdjustment,
            onEdit = {
                editingAccount = freshSummary.account
                inspectingAccountSummary = null
                showAddSheet = true
            },
            onToggleArchive = {
                onSetAccountActive(freshSummary.account.accountId, !freshSummary.account.isActive)
                inspectingAccountSummary = null
            },
            onSelectTransaction = { tx ->
                inspectingAccountSummary = null
                onSelectTransaction(tx)
            }
        )
    }

    reconcilingSummary?.let { targetSummary ->
        val latest = state.accountSummaries.firstOrNull { it.account.accountId == targetSummary.account.accountId }
            ?: targetSummary
        ReconcileBalanceDialog(
            summary = latest,
            onDismiss = { reconcilingSummary = null },
            onConfirm = { reportedMinor, note ->
                onReconcileBalance(latest.account.accountId, reportedMinor, note)
                reconcilingSummary = null
            }
        )
    }
}

@Composable
private fun BankAccountCard(
    summary: AccountSummary,
    hideBalances: Boolean,
    onClick: () -> Unit
) {
    val swColors = LocalSpendWiseColors.current
    val acc = summary.account
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (acc.isActive) 1f else 0.65f)
            .testTag("account_card_${acc.accountId}")
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, swColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankBrandBadge(bankName = acc.bankName, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = acc.accountNickname,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!acc.isActive) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${acc.bankName} · ${acc.accountType.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (acc.accountNumberSuffix.isNotEmpty()) {
                        Text(
                            text = "A/c XX${acc.accountNumberSuffix}",
                            style = SpendWiseTextStyles.MonoBadge,
                            color = swColors.mutedText
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Inflow: +${MoneyFormatter.formatMinorUnits(summary.periodIncomeMinorUnits, acc.currencyCode, isHidden = hideBalances)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = swColors.income
                )
                Text(
                    text = "Outflow: -${MoneyFormatter.formatMinorUnits(summary.periodExpenseMinorUnits, acc.currencyCode, isHidden = hideBalances)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = swColors.expense
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailSheet(
    summary: AccountSummary,
    transactions: List<TransactionModel>,
    adjustments: List<BalanceAdjustmentModel>,
    state: SpendWiseUiState,
    onDismiss: () -> Unit,
    onReconcile: () -> Unit,
    onDeleteAdjustment: (Long) -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onSelectTransaction: (TransactionModel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val swColors = LocalSpendWiseColors.current
    val acc = summary.account
    val hide = state.settings.hideBalances
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.categoryId } }
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.accountId } }
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankBrandBadge(bankName = acc.bankName, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(acc.accountNickname, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${acc.bankName} · ${acc.accountType.displayName} · XX${acc.accountNumberSuffix.ifEmpty { "----" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Estimated Tracked Balance", style = MaterialTheme.typography.labelSmall)
                    Text(
                        MoneyFormatter.formatMinorUnits(summary.estimatedBalanceMinorUnits, acc.currencyCode, isHidden = hide),
                        style = SpendWiseTextStyles.CardAmount,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Starting Balance", style = MaterialTheme.typography.labelSmall)
                    Text(
                        MoneyFormatter.formatMinorUnits(acc.openingBalanceMinorUnits, acc.currencyCode, isHidden = hide),
                        style = SpendWiseTextStyles.RowAmount
                    )
                    if (summary.totalAdjustmentsMinorUnits != 0L) {
                        Text(
                            text = "Adj: ${MoneyFormatter.formatMinorUnits(summary.totalAdjustmentsMinorUnits, acc.currencyCode, showSign = true, isHidden = hide)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Button(
                onClick = onReconcile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("account_reconcile_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Balance, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Update / Reconcile Bank Balance")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                OutlinedButton(onClick = onToggleArchive, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (acc.isActive) Icons.Rounded.Archive else Icons.Rounded.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (acc.isActive) "Archive" else "Restore")
                }
            }

            if (adjustments.isNotEmpty()) {
                Text(
                    text = "Balance Reconciliation Adjustments (${adjustments.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    adjustments.take(3).forEach { adj ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Reconciled to ${MoneyFormatter.formatMinorUnits(adj.reportedBalanceMinorUnits, adj.currencyCode, isHidden = hide)}",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "${dateFmt.format(Date(adj.adjustmentDateMillis))} · ${adj.note}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = swColors.mutedText
                                    )
                                }
                                Text(
                                    text = MoneyFormatter.formatMinorUnits(
                                        adj.deltaMinorUnits,
                                        adj.currencyCode,
                                        showSign = true,
                                        isHidden = hide
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (adj.deltaMinorUnits >= 0L) swColors.income else swColors.expense
                                )
                                IconButton(
                                    onClick = { onDeleteAdjustment(adj.adjustmentId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Remove adjustment",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Account History (${transactions.size})",
                style = MaterialTheme.typography.titleSmall
            )

            if (transactions.isEmpty()) {
                Text(
                    text = "No transactions linked to this account yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions, key = { it.transactionId }) { tx ->
                        TransactionRowItem(
                            transaction = tx,
                            category = categoryMap[tx.categoryId],
                            account = accountMap[tx.accountId],
                            hideBalances = hide,
                            onClick = { onSelectTransaction(tx) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconcileBalanceDialog(
    summary: AccountSummary,
    onDismiss: () -> Unit,
    onConfirm: (reportedBalanceMinorUnits: Long, note: String) -> Unit
) {
    val acc = summary.account
    val swColors = LocalSpendWiseColors.current
    var balanceText by remember {
        mutableStateOf(MoneyFormatter.minorToEditableString(summary.estimatedBalanceMinorUnits, acc.currencyCode))
    }
    var noteText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val parsedMinor = MoneyFormatter.parseMajorToMinor(balanceText, acc.currencyCode)
    val previewDelta = parsedMinor?.let { it - summary.estimatedBalanceMinorUnits }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reconcile ${acc.accountNickname}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current estimated balance: ${MoneyFormatter.formatMinorUnits(summary.estimatedBalanceMinorUnits, acc.currencyCode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            balanceText = input
                            errorText = null
                        }
                    },
                    label = { Text("Current Bank Balance (${CurrencySpec.fromCode(acc.currencyCode).symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("reconcile_balance_input")
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Reason / Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("reconcile_note_input")
                )
                if (previewDelta != null) {
                    Text(
                        text = "Dated adjustment: ${MoneyFormatter.formatMinorUnits(previewDelta, acc.currencyCode, showSign = true)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (previewDelta >= 0L) swColors.income else swColors.expense
                    )
                }
                Text(
                    text = "Saved as a dated balance adjustment. Your starting balance, transaction history, and income/expense totals remain unchanged.",
                    style = MaterialTheme.typography.labelSmall,
                    color = swColors.mutedText
                )
                if (errorText != null) {
                    Text(errorText!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minor = MoneyFormatter.parseMajorToMinor(balanceText, acc.currencyCode)
                    if (minor == null) {
                        errorText = "Enter a valid balance amount."
                        return@Button
                    }
                    onConfirm(minor, noteText.trim())
                },
                modifier = Modifier.testTag("reconcile_confirm_button")
            ) {
                Text("Save Adjustment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditAccountSheet(
    existingAccount: BankAccountModel?,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (BankAccountModel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val popularBanks = listOf(
        "State Bank of India",
        "HDFC Bank",
        "ICICI Bank",
        "Axis Bank",
        "Indian Bank",
        "Canara Bank",
        "Kotak Mahindra Bank",
        "Chase",
        "Revolut"
    )

    var bankName by remember { mutableStateOf(existingAccount?.bankName ?: "State Bank of India") }
    var nickname by remember { mutableStateOf(existingAccount?.accountNickname ?: "") }
    var suffix by remember { mutableStateOf(existingAccount?.accountNumberSuffix ?: "") }
    var accountType by remember { mutableStateOf(existingAccount?.accountType ?: AccountType.SAVINGS) }
    var currencyCode by remember { mutableStateOf(existingAccount?.currencyCode ?: defaultCurrency) }
    var openingBalanceText by remember {
        mutableStateOf(
            existingAccount?.let { MoneyFormatter.minorToEditableString(it.openingBalanceMinorUnits, it.currencyCode) } ?: "0"
        )
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (existingAccount == null) "Add Bank Account" else "Edit Account",
                style = MaterialTheme.typography.headlineSmall
            )

            Text("Popular Banks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                popularBanks.forEach { bank ->
                    FilterChip(
                        selected = bankName.equals(bank, ignoreCase = true),
                        onClick = { bankName = bank },
                        label = { Text(bank) }
                    )
                }
            }

            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it; errorText = null },
                label = { Text("Bank Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("account_bank_input")
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it; errorText = null },
                label = { Text("Account Nickname (e.g., Main Savings)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("account_nickname_input")
            )

            OutlinedTextField(
                value = suffix,
                onValueChange = { input ->
                    suffix = input.filter { it.isDigit() }.take(4)
                },
                label = { Text("Last 4 Digits Only (Optional for Cash)") },
                supportingText = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Never enter full bank account or card numbers.")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("account_suffix_input")
            )

            Text("Account Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AccountType.entries.forEach { type ->
                    FilterChip(
                        selected = accountType == type,
                        onClick = { accountType = type },
                        label = { Text(type.displayName) }
                    )
                }
            }

            Text("Currency", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CurrencySpec.supportedCurrencies.forEach { spec ->
                    FilterChip(
                        selected = currencyCode == spec.code,
                        onClick = { currencyCode = spec.code },
                        label = { Text("${spec.symbol} ${spec.code}") }
                    )
                }
            }

            OutlinedTextField(
                value = openingBalanceText,
                onValueChange = { input ->
                    if (input.all { it.isDigit() || it == '.' }) openingBalanceText = input
                },
                label = { Text("Opening Balance (${CurrencySpec.fromCode(currencyCode).symbol})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("account_opening_balance_input")
            )

            if (errorText != null) {
                Text(errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }

            Button(
                onClick = {
                    if (bankName.isBlank()) {
                        errorText = "Please enter a bank or wallet name."
                        return@Button
                    }
                    val openingMinor = MoneyFormatter.parseMajorToMinor(openingBalanceText.ifBlank { "0" }, currencyCode)
                    if (openingMinor == null) {
                        errorText = "Enter a valid opening balance."
                        return@Button
                    }
                    val now = System.currentTimeMillis()
                    onSave(
                        BankAccountModel(
                            accountId = existingAccount?.accountId ?: 0L,
                            bankName = bankName.trim(),
                            accountNickname = nickname.trim().ifEmpty { "${bankName.trim()} ${accountType.displayName}" },
                            accountNumberSuffix = suffix,
                            accountType = accountType,
                            currencyCode = currencyCode,
                            openingBalanceMinorUnits = openingMinor,
                            isActive = existingAccount?.isActive ?: true,
                            createdAt = existingAccount?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("account_save_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (existingAccount == null) "Save Account" else "Update Account")
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
