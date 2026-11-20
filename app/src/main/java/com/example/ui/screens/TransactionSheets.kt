package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.BankAccountModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategoryType
import com.example.domain.model.CurrencySpec
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionModel
import com.example.domain.model.TransactionSource
import com.example.domain.model.TransactionStatus
import com.example.domain.model.TransactionType
import com.example.ui.components.CategoryIcons
import com.example.ui.theme.LocalSpendWiseColors
import com.example.ui.theme.SpendWiseTextStyles

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTransactionSheet(
    existingTransaction: TransactionModel?,
    initialType: TransactionType,
    defaultCurrency: String,
    accounts: List<BankAccountModel>,
    categories: List<CategoryModel>,
    onDismiss: () -> Unit,
    onSave: (TransactionModel, Boolean) -> Unit,
    onApprovePending: (TransactionModel) -> Unit,
    onDelete: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val swColors = LocalSpendWiseColors.current
    val activeAccounts = remember(accounts) { accounts.filter { it.isActive }.ifEmpty { accounts } }

    var txType by remember { mutableStateOf(existingTransaction?.transactionType ?: initialType) }
    var currencyCode by remember {
        mutableStateOf(
            existingTransaction?.currencyCode
                ?: activeAccounts.firstOrNull()?.currencyCode
                ?: defaultCurrency
        )
    }
    var amountText by remember {
        mutableStateOf(
            existingTransaction?.let { MoneyFormatter.minorToEditableString(it.amountMinorUnits, it.currencyCode) } ?: ""
        )
    }
    var counterparty by remember { mutableStateOf(existingTransaction?.displayCounterparty?.takeIf { it != "Unknown" } ?: "") }
    var selectedAccountId by remember {
        mutableStateOf(existingTransaction?.accountId ?: activeAccounts.firstOrNull()?.accountId)
    }
    var targetAccountId by remember {
        mutableStateOf(
            existingTransaction?.targetAccountId
                ?: activeAccounts.firstOrNull { it.accountId != selectedAccountId }?.accountId
        )
    }
    var selectedCategoryId by remember { mutableStateOf(existingTransaction?.categoryId) }
    var paymentMethod by remember { mutableStateOf(existingTransaction?.paymentMethod ?: PaymentMethod.UPI) }
    var counterpartySuffix by remember { mutableStateOf(existingTransaction?.counterpartyAccountSuffix.orEmpty()) }
    var referenceNumber by remember { mutableStateOf(existingTransaction?.referenceNumber.orEmpty()) }
    var description by remember { mutableStateOf(existingTransaction?.description.orEmpty()) }
    var learnMerchantRule by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val matchingCategories = remember(categories, txType) {
        when (txType) {
            TransactionType.CREDIT -> categories.filter { it.categoryType == CategoryType.INCOME }
            TransactionType.DEBIT -> categories.filter { it.categoryType == CategoryType.EXPENSE }
            TransactionType.INTERNAL_TRANSFER -> categories.filter { it.categoryType == CategoryType.TRANSFER }
        }.ifEmpty { categories }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        existingTransaction == null -> "Add Transaction"
                        existingTransaction.transactionStatus == TransactionStatus.PENDING_REVIEW -> "Review Detected Transaction"
                        else -> "Transaction Details"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (existingTransaction != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }

            // Source & Confidence metadata card for auto-detected notifications
            if (existingTransaction?.sourceType == TransactionSource.BANK_NOTIFICATION) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Auto-detected · Confidence ${(existingTransaction.confidenceScore * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Extracted locally on device. Confirm or edit details below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            existingTransaction.availableBalanceMinorUnits?.let { avail ->
                                Text(
                                    text = "Bank-reported available balance: ${MoneyFormatter.formatMinorUnits(avail, existingTransaction.currencyCode)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Transaction Type Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val types = listOf(TransactionType.DEBIT, TransactionType.CREDIT, TransactionType.INTERNAL_TRANSFER)
                types.forEachIndexed { idx, type ->
                    SegmentedButton(
                        selected = txType == type,
                        onClick = {
                            txType = type
                            selectedCategoryId = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(idx, types.size),
                        modifier = Modifier.testTag("tx_type_${type.name.lowercase()}")
                    ) {
                        Text(type.displayName, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Amount + Currency Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            amountText = input
                            errorText = null
                        }
                    },
                    label = { Text("Amount (${CurrencySpec.fromCode(currencyCode).symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = SpendWiseTextStyles.CardAmount,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tx_amount_input")
                )
            }

            // Counterparty / Merchant
            OutlinedTextField(
                value = counterparty,
                onValueChange = { counterparty = it },
                label = {
                    Text(
                        when (txType) {
                            TransactionType.DEBIT -> "Merchant or Recipient"
                            TransactionType.CREDIT -> "Sender or Source"
                            TransactionType.INTERNAL_TRANSFER -> "Transfer Label (e.g., Monthly Savings)"
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_counterparty_input")
            )

            if (txType != TransactionType.INTERNAL_TRANSFER) {
                OutlinedTextField(
                    value = counterpartySuffix,
                    onValueChange = { input -> counterpartySuffix = input.filter { it.isDigit() }.take(4) },
                    label = {
                        Text(
                            if (txType == TransactionType.CREDIT) "Sender Last 4 Digits (Optional)"
                            else "Receiver Last 4 Digits (Optional)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_counterparty_suffix_input")
                )
            }

            // Account Selection
            Text(
                text = if (txType == TransactionType.INTERNAL_TRANSFER) "From Account" else "Bank Account",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (activeAccounts.isEmpty()) {
                Text(
                    text = "No accounts added yet — transaction will be saved as Unassigned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = swColors.mutedText
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeAccounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.accountId,
                            onClick = {
                                selectedAccountId = acc.accountId
                                currencyCode = acc.currencyCode
                            },
                            label = {
                                Text("${acc.accountNickname} (${acc.accountNumberSuffix.ifEmpty { acc.bankName }})")
                            }
                        )
                    }
                }
            }

            if (txType == TransactionType.INTERNAL_TRANSFER && activeAccounts.size >= 2) {
                Text(
                    text = "To Account (excluded from consolidated income/expense)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeAccounts.filter { it.accountId != selectedAccountId }.forEach { acc ->
                        FilterChip(
                            selected = targetAccountId == acc.accountId,
                            onClick = { targetAccountId = acc.accountId },
                            label = { Text("${acc.accountNickname} (XX${acc.accountNumberSuffix})") }
                        )
                    }
                }
            }

            // Category Selection
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                matchingCategories.forEach { cat ->
                    val isSelected = selectedCategoryId == cat.categoryId
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryId = cat.categoryId },
                        leadingIcon = {
                            Icon(
                                imageVector = CategoryIcons.forKey(cat.iconKey),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(cat.categoryName) }
                    )
                }
            }

            // Payment Method Selection
            Text(
                text = "Payment Method",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickMethods = listOf(
                    PaymentMethod.UPI,
                    PaymentMethod.DEBIT_CARD,
                    PaymentMethod.CREDIT_CARD,
                    PaymentMethod.IMPS,
                    PaymentMethod.NEFT,
                    PaymentMethod.ATM,
                    PaymentMethod.CASH,
                    PaymentMethod.BANK_TRANSFER
                )
                quickMethods.forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method.displayName) }
                    )
                }
            }

            // Reference & Notes
            OutlinedTextField(
                value = referenceNumber,
                onValueChange = { referenceNumber = it },
                label = { Text("Reference / UTR (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes (Optional)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Local merchant rule learning checkbox
            if (counterparty.isNotBlank() && txType != TransactionType.INTERNAL_TRANSFER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = learnMerchantRule,
                        onCheckedChange = { learnMerchantRule = it }
                    )
                    Text(
                        text = "Remember category for \"$counterparty\" locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            fun buildModel(status: TransactionStatus): TransactionModel? {
                val minor = MoneyFormatter.parseMajorToMinor(amountText, currencyCode)
                if (minor == null || minor <= 0L) {
                    errorText = "Please enter a valid positive amount."
                    return null
                }
                if (txType == TransactionType.INTERNAL_TRANSFER && selectedAccountId == targetAccountId) {
                    errorText = "Select two different accounts for an internal transfer."
                    return null
                }
                val finalCategoryId = selectedCategoryId ?: matchingCategories.firstOrNull()?.categoryId
                val cleanParty = counterparty.trim().ifEmpty { "Unknown" }
                val now = System.currentTimeMillis()
                val isExistingPersonOnly = existingTransaction != null &&
                    existingTransaction.merchant.isBlank() &&
                    (existingTransaction.recipient.isNotBlank() || existingTransaction.sender.isNotBlank())
                val resolvedMerchant = when {
                    txType == TransactionType.INTERNAL_TRANSFER -> cleanParty
                    isExistingPersonOnly -> ""
                    existingTransaction?.merchant?.isNotBlank() == true -> cleanParty
                    else -> if (txType == TransactionType.DEBIT) cleanParty else ""
                }
                val resolvedRecipient = if (txType != TransactionType.CREDIT) cleanParty else ""
                val resolvedSender = if (txType == TransactionType.CREDIT) cleanParty else ""
                return TransactionModel(
                    transactionId = existingTransaction?.transactionId ?: 0L,
                    accountId = selectedAccountId,
                    targetAccountId = if (txType == TransactionType.INTERNAL_TRANSFER) targetAccountId else null,
                    amountMinorUnits = minor,
                    currencyCode = currencyCode,
                    transactionType = txType,
                    recipient = resolvedRecipient,
                    sender = resolvedSender,
                    merchant = resolvedMerchant,
                    categoryId = finalCategoryId,
                    paymentMethod = paymentMethod,
                    transactionDateMillis = existingTransaction?.transactionDateMillis ?: now,
                    referenceNumber = referenceNumber.trim().takeIf { it.isNotEmpty() },
                    sourceType = existingTransaction?.sourceType ?: TransactionSource.MANUAL,
                    transactionStatus = status,
                    description = description.trim(),
                    confidenceScore = existingTransaction?.confidenceScore ?: 1.0f,
                    availableBalanceMinorUnits = existingTransaction?.availableBalanceMinorUnits,
                    counterpartyAccountSuffix = counterpartySuffix.takeIf { it.length == 4 },
                    createdAt = existingTransaction?.createdAt ?: now,
                    updatedAt = now
                )
            }

            // Action Buttons
            if (existingTransaction?.transactionStatus == TransactionStatus.PENDING_REVIEW) {
                Button(
                    onClick = {
                        buildModel(TransactionStatus.CONFIRMED)?.let {
                            onApprovePending(it)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("tx_confirm_review_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm & Record Transaction")
                }
            } else {
                Button(
                    onClick = {
                        buildModel(TransactionStatus.CONFIRMED)?.let {
                            onSave(it, learnMerchantRule)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("tx_save_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (existingTransaction == null) "Save Transaction" else "Update Transaction")
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Cancel")
            }
        }
    }

    if (showDeleteConfirm && existingTransaction != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This will permanently remove \"${existingTransaction.displayCounterparty}\" from your local Room database.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(existingTransaction.transactionId)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
