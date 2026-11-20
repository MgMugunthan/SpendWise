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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.CategoryModel
import com.example.domain.model.CategoryType
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.NotificationRecordModel
import com.example.intelligence.ParseOutcome
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.CategoryIcons
import com.example.ui.theme.LocalSpendWiseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SyntheticBankSample(
    val label: String,
    val title: String,
    val body: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationTesterSheet(
    records: List<NotificationRecordModel>,
    lastOutcome: ParseOutcome?,
    onSimulate: (pkg: String, title: String, body: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val swColors = LocalSpendWiseColors.current
    val timeFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    val samples = remember {
        listOf(
            SyntheticBankSample(
                "SBI ₹1 UPI + Avl Bal",
                "VM-SBIUPI",
                "Dear UPI user A/C X1234 debited by 1.0 on date 20Nov26 trf to RAHUL SHARMA Refno 632412345678. Available balance is Rs.12,499.00."
            ),
            SyntheticBankSample(
                "SBI UPI Swiggy",
                "SBI Alert",
                "Your A/c XX1234 is debited by Rs.500.00 on 19-11-2026. UPI transaction to SWIGGY. Ref No 789456123."
            ),
            SyntheticBankSample(
                "HDFC Salary Credit",
                "HDFC Bank",
                "HDFC Bank: A/c XX5678 credited with Rs.25,000.00 via NEFT from ACME PAYROLL. Ref UTR202611991. Avl Bal Rs.75,000.00."
            ),
            SyntheticBankSample(
                "SMS Alert Fee (Charges)",
                "AD-SBIBNK",
                "Your A/c XX1234 is debited by Rs.17.70 towards SMS alert charges incl. GST on 19-11-2026. Avl Bal Rs.12,481.30."
            ),
            SyntheticBankSample(
                "ICICI Card Amazon",
                "ICICI Bank",
                "ICICI Credit Card ending 9012 spent INR 1,899.00 at AMAZON INDIA on 19-Nov. Ref TXN55443322."
            ),
            SyntheticBankSample(
                "ATM Withdrawal",
                "SBI ATM",
                "Your SBI A/c XX1234 debited Rs.2,000.00 for ATM cash withdrawal. Ref 99118822."
            ),
            SyntheticBankSample(
                "Balance Only (Ignore)",
                "VM-SBIBNK",
                "Balance enquiry: Available balance in A/c XX1234 is Rs.15,240.00."
            ),
            SyntheticBankSample(
                "OTP (Should Ignore)",
                "HDFC Bank",
                "Your OTP is 482910 for Rs.5,000 transaction on card XX9012. Do not share with anyone."
            ),
            SyntheticBankSample(
                "Unknown Suffix (Review)",
                "Kotak Bank",
                "Your Kotak A/c XX7777 is debited by Rs.840.00 via UPI payment to METRO RAIL."
            )
        )
    }

    var titleInput by remember { mutableStateOf(samples.first().title) }
    var bodyInput by remember { mutableStateOf(samples.first().body) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Notification Detection Diagnostics",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Test SpendWise's offline rule-based parser with synthetic bank messages. Valid transactions write directly to Room; OTPs, promos, and duplicates are rejected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Tap a synthetic bank alert:", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                samples.forEach { sample ->
                    FilterChip(
                        selected = bodyInput == sample.body,
                        onClick = {
                            titleInput = sample.title
                            bodyInput = sample.body
                        },
                        label = { Text(sample.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Notification Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bodyInput,
                onValueChange = { bodyInput = it },
                label = { Text("Notification Body") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("tester_body_input")
            )

            Button(
                onClick = {
                    onSimulate("com.google.android.apps.messaging", titleInput, bodyInput)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_parser_test_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Run Local Parser Pipeline")
            }

            lastOutcome?.let { outcome ->
                val (badgeTitle, badgeDetail, tint, bg) = when (outcome) {
                    is ParseOutcome.Parsed -> {
                        val tx = outcome.transaction
                        val amt = MoneyFormatter.formatMinorUnits(tx.amountMinorUnits, tx.currencyCode)
                        val availLine = tx.availableBalanceMinorUnits?.let {
                            "\nReported Avl Bal: ${MoneyFormatter.formatMinorUnits(it, tx.currencyCode)}"
                        }.orEmpty()
                        Quad(
                            "Parsed (${tx.transactionStatus.displayName}) · ${(tx.confidenceScore * 100).toInt()}%",
                            "${tx.transactionType.displayName} $amt · ${tx.displayCounterparty} · ${tx.paymentMethod.displayName}\nAccount: ${outcome.matchReason}$availLine",
                            swColors.income,
                            swColors.incomeContainer
                        )
                    }
                    is ParseOutcome.Duplicate -> Quad(
                        "Duplicate Blocked",
                        outcome.reason,
                        swColors.attention,
                        swColors.attentionContainer
                    )
                    is ParseOutcome.Ignored -> Quad(
                        "Safely Ignored",
                        outcome.reason,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = bg,
                    border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("parser_result_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(badgeTitle, style = MaterialTheme.typography.titleSmall, color = tint)
                        Text(badgeDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (records.isNotEmpty()) {
                Text("Recent Processing Metadata (No Raw Text Stored)", style = MaterialTheme.typography.labelLarge)
                records.take(6).forEach { rec ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(rec.processingStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(timeFmt.format(Date(rec.notificationTimestamp)), style = MaterialTheme.typography.labelSmall, color = swColors.mutedText)
                            }
                            Text(rec.summaryLabel, style = MaterialTheme.typography.bodySmall)
                            Text("SHA-256: ${rec.fingerprint.take(16)}…", style = MaterialTheme.typography.labelSmall, color = swColors.mutedText)
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManagerSheet(
    categories: List<CategoryModel>,
    onAddCategory: (name: String, type: CategoryType, iconKey: String, colorHex: Long) -> Unit,
    onDeleteCustomCategory: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(CategoryType.EXPENSE) }
    var selectedIcon by remember { mutableStateOf("shopping") }
    val colorChoices = listOf(0xFF059669L, 0xFF2563EBL, 0xFFEA580CL, 0xFF10B981L, 0xFF3B82F6L, 0xFFF97316L)
    var selectedColor by remember { mutableStateOf(colorChoices.first()) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Manage Categories", style = MaterialTheme.typography.headlineSmall)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val types = listOf(CategoryType.EXPENSE, CategoryType.INCOME)
                types.forEachIndexed { idx, t ->
                    SegmentedButton(
                        selected = newType == t,
                        onClick = { newType = t },
                        shape = SegmentedButtonDefaults.itemShape(idx, types.size)
                    ) { Text(t.displayName) }
                }
            }

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New Category Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryIcons.selectableKeys.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedIcon == key,
                        onClick = { selectedIcon = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        selectedColor = colorChoices[newName.length % colorChoices.size]
                        onAddCategory(newName.trim(), newType, selectedIcon, selectedColor)
                        newName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Custom Category")
            }

            Text("Existing Categories (${categories.size})", style = MaterialTheme.typography.titleSmall)
            categories.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryIconBadge(iconKey = cat.iconKey, colorHex = cat.colorHex, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cat.categoryName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${cat.categoryType.displayName} · ${if (cat.isDefault) "Default" else "Custom"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!cat.isDefault) {
                        IconButton(onClick = { onDeleteCustomCategory(cat.categoryId) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete custom category", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyArchitectureDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy & Security Architecture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "• Zero Cloud / Zero Internet: SpendWise does not declare the Android INTERNET permission. Your bank alerts and transactions cannot be transmitted off your phone.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Sensitive Data Minimization: Full account numbers, card numbers, OTPs, and raw SMS texts are never stored. Only last-4 account suffixes and SHA-256 fingerprints are kept.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Cryptography: App Lock PINs use salted PBKDF2-HMAC-SHA256 (100,000 iterations). Android Keystore provisions a hardware-backed AES-256-GCM key where supported, and exported backups use passphrase-derived AES-256-GCM.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Honest Limitation: Local Room SQLite relies on Android's sandboxed file-based encryption (FBE) and disabled cloud backup (allowBackup=false). On rooted devices with superuser access, local unencrypted SQLite files can be inspected; keep App Lock and device encryption enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Understood") }
        }
    )
}

@Composable
fun SetPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set 4-Digit App Lock PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(4); error = null },
                    label = { Text("Enter 4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() }.take(4); error = null },
                    label = { Text("Confirm 4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length != 4 -> error = "PIN must be exactly 4 digits."
                        pin != confirmPin -> error = "PINs do not match."
                        else -> onSavePin(pin)
                    }
                }
            ) { Text("Save PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BackupPassphraseDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text("Backup Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (passphrase.length < 6) {
                        error = "Use at least 6 characters."
                    } else {
                        onConfirm(passphrase)
                    }
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
