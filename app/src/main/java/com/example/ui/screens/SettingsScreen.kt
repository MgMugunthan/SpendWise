package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.CategoryType
import com.example.domain.model.CurrencySpec
import com.example.domain.model.ThemePreference
import com.example.intelligence.ParseOutcome
import com.example.intelligence.SpendWiseNotificationListenerService
import com.example.ui.SpendWiseUiState
import com.example.ui.components.SpendWiseLogoMark
import com.example.ui.components.isBiometricHardwareSupported
import com.example.ui.theme.LocalSpendWiseColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: SpendWiseUiState,
    notificationAccessGranted: Boolean,
    lastParseOutcome: ParseOutcome?,
    onSetTheme: (ThemePreference) -> Unit,
    onSetDefaultCurrency: (String) -> Unit,
    onToggleHideBalances: () -> Unit,
    onSetDetectionEnabled: (Boolean) -> Unit,
    onSimulateNotification: (pkg: String, title: String, body: String) -> Unit,
    onClearLastOutcome: () -> Unit,
    onConfigurePin: (String) -> Unit,
    onSetAppLockEnabled: (Boolean) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onAddCategory: (name: String, type: CategoryType, iconKey: String, colorHex: Long) -> Unit,
    onDeleteCustomCategory: (Long) -> Unit,
    onGenerateCsv: suspend () -> String,
    onGenerateEncryptedBackup: suspend (String) -> String,
    onRestoreEncryptedBackup: (String, String) -> Unit,
    onClearTransactions: () -> Unit,
    onDeleteAllData: () -> Unit,
    onOpenInitialSetup: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val swColors = LocalSpendWiseColors.current
    val settings = state.settings
    val bioAvailable = remember { isBiometricHardwareSupported(context) }

    var showNotificationTester by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showClearTxConfirm by remember { mutableStateOf(false) }
    var showWipeAllConfirm by remember { mutableStateOf(false) }

    // Encrypted backup & restore passphrase dialogs
    var pendingBackupPassphraseDialog by remember { mutableStateOf(false) }
    var activeBackupPassphrase by remember { mutableStateOf("") }
    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }

    // Zero-permission Android SAF launchers (Google Play policy compliant)
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val csv = onGenerateCsv()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                    onNotify("CSV exported successfully")
                }.onFailure {
                    onNotify("CSV export failed: ${it.message}")
                }
            }
        }
    }

    val encryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && activeBackupPassphrase.length >= 6) {
            val pass = activeBackupPassphrase
            activeBackupPassphrase = ""
            scope.launch {
                runCatching {
                    val encrypted = onGenerateEncryptedBackup(pass)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(encrypted.toByteArray(Charsets.UTF_8))
                    }
                    onNotify("Encrypted AES-256 backup saved")
                }.onFailure {
                    onNotify("Encrypted backup failed: ${it.message}")
                }
            }
        }
    }

    val encryptedRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    pendingRestoreContent = content
                } else {
                    onNotify("Selected backup file is empty")
                }
            }.onFailure {
                onNotify("Could not read backup file")
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Settings & Privacy",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "100% offline-first · Zero cloud accounts · Zero internet permission",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Transaction Detection Section
        item {
            SettingsGroupCard(title = "Automatic Bank Detection") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (notificationAccessGranted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = if (notificationAccessGranted) swColors.income else swColors.expense,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (notificationAccessGranted) "Notification Access Granted" else "Notification Access Not Enabled",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Tracks new supported bank & UPI notifications locally from setup onwards. Never reads SMS history; ignores OTPs, promos & balance-only alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(SpendWiseNotificationListenerService.createNotificationAccessSettingsIntent())
                            }.onFailure {
                                onNotify("Open Android Settings -> Notification Access to enable SpendWise")
                            }
                        },
                        modifier = Modifier.testTag("open_notification_settings_btn")
                    ) {
                        Text(if (notificationAccessGranted) "Manage" else "Enable")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsToggleRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Pause / Enable Auto-Detection",
                    subtitle = "Automatically process incoming supported bank alerts",
                    checked = settings.notificationDetectionEnabled,
                    onCheckedChange = onSetDetectionEnabled
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsActionRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Detection Diagnostics & Live Parser Tester",
                    subtitle = "${state.notificationRecords.size} recent log(s) · Test SBI, HDFC, UPI & OTP filters",
                    onClick = { showNotificationTester = true },
                    testTag = "open_notification_tester_row"
                )
            }
        }

        // 2. Privacy & Security Section
        item {
            SettingsGroupCard(title = "Privacy & Security") {
                SettingsToggleRow(
                    icon = Icons.Rounded.Lock,
                    title = "App Lock (4-Digit PIN)",
                    subtitle = if (settings.hasPinConfigured) "PIN protected with PBKDF2-HMAC-SHA256" else "Set a 4-digit PIN to lock SpendWise",
                    checked = settings.appLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable && !settings.hasPinConfigured) {
                            showPinDialog = true
                        } else {
                            onSetAppLockEnabled(enable)
                        }
                    },
                    testTag = "toggle_app_lock"
                )

                if (settings.hasPinConfigured) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsActionRow(
                        icon = Icons.Rounded.Key,
                        title = "Change PIN or Lock Now",
                        subtitle = "Update your 4-digit passcode",
                        onClick = { showPinDialog = true },
                        trailing = {
                            TextButton(onClick = onLockNow) { Text("Lock Now") }
                        }
                    )
                }

                if (bioAvailable) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric Unlock",
                        subtitle = "Use fingerprint when App Lock is enabled",
                        checked = settings.biometricEnabled,
                        onCheckedChange = onSetBiometricEnabled
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsToggleRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Hide Balances by Default",
                    subtitle = "Mask financial amounts across screens",
                    checked = settings.hideBalances,
                    onCheckedChange = { onToggleHideBalances() },
                    testTag = "toggle_hide_balances"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsActionRow(
                    icon = Icons.Rounded.Security,
                    title = "Local Security & Encryption Info",
                    subtitle = "Android Keystore AES-256-GCM, offline guarantees & honest limitations",
                    onClick = { showPrivacyDialog = true }
                )
            }
        }

        // 3. Appearance & Currency Section
        item {
            SettingsGroupCard(title = "Appearance & Currency") {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemePreference.entries.forEachIndexed { idx, pref ->
                            SegmentedButton(
                                selected = settings.themePreference == pref,
                                onClick = { onSetTheme(pref) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemePreference.entries.size),
                                modifier = Modifier.testTag("theme_${pref.name.lowercase()}")
                            ) {
                                Text(pref.displayName)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box {
                    SettingsActionRow(
                        icon = Icons.Rounded.EnhancedEncryption,
                        title = "Default Currency",
                        subtitle = CurrencySpec.fromCode(settings.defaultCurrency).let { "${it.name} (${it.symbol} ${it.code})" },
                        onClick = { showCurrencyMenu = true }
                    )
                    DropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { showCurrencyMenu = false }
                    ) {
                        CurrencySpec.supportedCurrencies.forEach { spec ->
                            DropdownMenuItem(
                                text = { Text("${spec.symbol} ${spec.code} — ${spec.name}") },
                                onClick = {
                                    onSetDefaultCurrency(spec.code)
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Data Management & Backups
        item {
            SettingsGroupCard(title = "Data Management & Backups") {
                SettingsActionRow(
                    icon = Icons.Rounded.Download,
                    title = "Export Transactions (CSV)",
                    subtitle = "Save spreadsheet-ready CSV via Android Storage Access Framework",
                    onClick = { csvExportLauncher.launch("spendwise_transactions.csv") },
                    testTag = "export_csv_row"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.EnhancedEncryption,
                    title = "Create Encrypted Backup (.swbak)",
                    subtitle = "AES-256-GCM passphrase-encrypted backup for phone transfer",
                    onClick = { pendingBackupPassphraseDialog = true },
                    testTag = "export_encrypted_backup_row"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.Upload,
                    title = "Restore Encrypted Backup",
                    subtitle = "Decrypt and restore accounts, transactions, and budgets",
                    onClick = { encryptedRestoreLauncher.launch(arrayOf("*/*")) },
                    testTag = "restore_encrypted_backup_row"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Initial Account & Notification Setup",
                    subtitle = "Add bank accounts, set starting balances & configure live notification access",
                    onClick = onOpenInitialSetup,
                    testTag = "settings_initial_setup_row"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Clear Transaction History",
                    subtitle = "Remove all transactions while keeping bank accounts & categories",
                    tint = swColors.expense,
                    onClick = { showClearTxConfirm = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Delete All Application Data",
                    subtitle = "Permanently wipe accounts, transactions, budgets, rules & PIN",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showWipeAllConfirm = true }
                )
            }
        }

        // 5. General Section
        item {
            SettingsGroupCard(title = "General") {
                SettingsActionRow(
                    icon = Icons.Rounded.Category,
                    title = "Category Management",
                    subtitle = "${state.categories.size} active categories · Create custom categories",
                    onClick = { showCategoryManager = true },
                    testTag = "category_manager_row"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow(
                    icon = Icons.Rounded.Info,
                    title = "About SpendWise",
                    subtitle = "Version 1.0.0 · Offline-First Personal Finance",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    // Modals & Dialogs
    if (showNotificationTester) {
        NotificationTesterSheet(
            records = state.notificationRecords,
            lastOutcome = lastParseOutcome,
            onSimulate = onSimulateNotification,
            onDismiss = {
                onClearLastOutcome()
                showNotificationTester = false
            }
        )
    }

    if (showCategoryManager) {
        CategoryManagerSheet(
            categories = state.categories,
            onAddCategory = onAddCategory,
            onDeleteCustomCategory = onDeleteCustomCategory,
            onDismiss = { showCategoryManager = false }
        )
    }

    if (showPrivacyDialog) {
        PrivacyArchitectureDialog(onDismiss = { showPrivacyDialog = false })
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onSavePin = { pin ->
                onConfigurePin(pin)
                showPinDialog = false
            }
        )
    }

    if (pendingBackupPassphraseDialog) {
        BackupPassphraseDialog(
            title = "Encrypt Backup File",
            subtitle = "Choose a strong passphrase (min 6 characters). You will need this exact passphrase to restore on another phone.",
            confirmLabel = "Choose Save Location",
            onDismiss = { pendingBackupPassphraseDialog = false },
            onConfirm = { pass ->
                activeBackupPassphrase = pass
                pendingBackupPassphraseDialog = false
                encryptedBackupLauncher.launch("spendwise_backup.swbak")
            }
        )
    }

    pendingRestoreContent?.let { content ->
        BackupPassphraseDialog(
            title = "Decrypt & Restore Backup",
            subtitle = "Enter the passphrase used when creating this encrypted SpendWise backup.",
            confirmLabel = "Restore Now",
            onDismiss = { pendingRestoreContent = null },
            onConfirm = { pass ->
                onRestoreEncryptedBackup(content, pass)
                pendingRestoreContent = null
            }
        )
    }

    if (showClearTxConfirm) {
        AlertDialog(
            onDismissRequest = { showClearTxConfirm = false },
            title = { Text("Clear All Transactions?") },
            text = { Text("All recorded transactions and notification fingerprints will be deleted from Room. Your bank accounts will remain.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearTransactions()
                        showClearTxConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearTxConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showWipeAllConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeAllConfirm = false },
            title = { Text("Delete All SpendWise Data?") },
            text = { Text("This permanently deletes all accounts, transactions, budgets, custom categories, merchant rules, and PIN settings from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllData()
                        showWipeAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeAllConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpendWiseLogoMark(size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("SpendWise", style = MaterialTheme.typography.titleLarge)
                        Text("Version 1.0.0", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                Text(
                    "SpendWise is an offline-first, privacy-focused personal finance application built with Kotlin, Jetpack Compose, Material 3, and Room SQLite.\n\n" +
                        "All transaction parsing, multi-bank matching, categorization, and analytics happen exclusively on your Android device with zero cloud accounts and zero internet permissions.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    val swColors = LocalSpendWiseColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, swColors.cardBorder)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    testTag: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
