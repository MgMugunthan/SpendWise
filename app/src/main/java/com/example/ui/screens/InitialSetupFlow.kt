package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppSettings
import com.example.domain.model.AccountType
import com.example.domain.model.BankAccountModel
import com.example.domain.model.CurrencySpec
import com.example.domain.model.MoneyFormatter
import com.example.intelligence.SpendWiseNotificationListenerService
import com.example.ui.components.BankBrandBadge
import com.example.ui.components.SpendWiseLogoMark
import com.example.ui.components.isBiometricHardwareSupported
import com.example.ui.theme.LocalSpendWiseColors

/**
 * Simple 3-page first-time setup:
 * Page 1: Welcome + Profile (Name, Age, Email, Address) + 4-digit PIN & Biometric setup
 * Page 2: Bank Account Details (Bank, Last 4 Digits, Account Type, Current Balance)
 * Page 3: Notification Access Reason + Android System Warning / Restricted Settings Guide + Turn On
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InitialSetupFlow(
    settings: AppSettings,
    existingAccounts: List<BankAccountModel>,
    notificationAccessGranted: Boolean,
    onSaveProfile: (name: String, age: String, email: String, address: String) -> Unit,
    onConfigurePin: (String) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onSaveAccount: (BankAccountModel) -> Unit,
    onFinishSetup: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val swColors = LocalSpendWiseColors.current
    val bioAvailable = remember { isBiometricHardwareSupported(context) }

    var step by remember { mutableIntStateOf(0) }

    // Page 1 state: Profile & Security
    var userName by remember { mutableStateOf(settings.userName) }
    var userAge by remember { mutableStateOf(settings.userAge) }
    var userEmail by remember { mutableStateOf(settings.userEmail) }
    var userAddress by remember { mutableStateOf(settings.userAddress) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(settings.biometricEnabled) }
    var page1Error by remember { mutableStateOf<String?>(null) }

    // Page 2 state: Bank Account
    val popularBanks = remember {
        listOf(
            "State Bank of India",
            "HDFC Bank",
            "ICICI Bank",
            "Axis Bank",
            "Kotak Mahindra Bank",
            "Canara Bank",
            "Indian Bank",
            "Punjab National Bank"
        )
    }
    var bankName by remember { mutableStateOf("State Bank of India") }
    var nickname by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf(AccountType.SAVINGS) }
    var currencyCode by remember { mutableStateOf(settings.defaultCurrency) }
    var currentBalanceText by remember { mutableStateOf("") }
    var page2Error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = step > 0) {
        step -= 1
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("initial_setup_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar with logo, step count & Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpendWiseLogoMark(size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SpendWise Setup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Step ${step + 1} of 3",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = {
                        if (userName.isNotBlank() || userEmail.isNotBlank()) {
                            onSaveProfile(userName, userAge, userEmail, userAddress)
                        }
                        onFinishSetup()
                    },
                    modifier = Modifier.testTag("setup_skip_button")
                ) {
                    Text("Skip")
                }
            }

            // Clean 3-segment step indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (index <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            when (step) {
                // ============================================================
                // PAGE 1: Welcome + Profile + PIN & Biometric
                // ============================================================
                0 -> {
                    Text(
                        text = "Welcome to SpendWise",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Tell us a little about yourself and set up local app lock. All details stay strictly on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it; page1Error = null },
                        label = { Text("Your Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = userAge,
                            onValueChange = { input -> userAge = input.filter { it.isDigit() }.take(3) },
                            label = { Text("Age") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.35f).testTag("setup_age_input")
                        )
                        OutlinedTextField(
                            value = userEmail,
                            onValueChange = { userEmail = it },
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.weight(0.65f).testTag("setup_email_input")
                        )
                    }

                    OutlinedTextField(
                        value = userAddress,
                        onValueChange = { userAddress = it },
                        label = { Text("Address (Optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("setup_address_input")
                    )

                    // Local Security: PIN & Biometric
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, swColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "App Security (PIN & Biometric)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { input ->
                                        pinInput = input.filter { it.isDigit() }.take(4)
                                        page1Error = null
                                    },
                                    label = { Text("4-Digit PIN") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("setup_pin_input")
                                )
                                OutlinedTextField(
                                    value = confirmPinInput,
                                    onValueChange = { input ->
                                        confirmPinInput = input.filter { it.isDigit() }.take(4)
                                        page1Error = null
                                    },
                                    label = { Text("Confirm PIN") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("setup_confirm_pin_input")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fingerprint,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Biometric Unlock", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = if (bioAvailable) "Use fingerprint with your PIN" else "Biometric hardware not detected on this device",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = enableBiometric && bioAvailable,
                                    onCheckedChange = { enableBiometric = it },
                                    enabled = bioAvailable,
                                    modifier = Modifier.testTag("setup_biometric_switch")
                                )
                            }
                        }
                    }

                    if (page1Error != null) {
                        Text(
                            text = page1Error!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            if (pinInput.isNotEmpty() || confirmPinInput.isNotEmpty()) {
                                if (pinInput.length != 4) {
                                    page1Error = "PIN must be exactly 4 digits (or leave blank to set later)."
                                    return@Button
                                }
                                if (pinInput != confirmPinInput) {
                                    page1Error = "PINs do not match."
                                    return@Button
                                }
                                onConfigurePin(pinInput)
                            }
                            onSaveProfile(userName, userAge, userEmail, userAddress)
                            if (bioAvailable) {
                                onSetBiometricEnabled(enableBiometric)
                            }
                            step = 1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("setup_page1_next_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Next: Bank Account Details")
                    }
                }

                // ============================================================
                // PAGE 2: Bank Account Details
                // ============================================================
                1 -> {
                    Text(
                        text = "Add Your Bank Account",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Enter your bank name, last 4 digits, account type, and current balance. Never enter full account numbers or bank logins.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (existingAccounts.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, swColors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Added Accounts (${existingAccounts.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                existingAccounts.forEach { acc ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BankBrandBadge(bankName = acc.bankName, size = 34.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(acc.accountNickname, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${acc.bankName} · XX${acc.accountNumberSuffix.ifEmpty { "----" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = MoneyFormatter.formatMinorUnits(acc.openingBalanceMinorUnits, acc.currencyCode),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = swColors.income
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        popularBanks.forEach { bank ->
                            FilterChip(
                                selected = bankName.equals(bank, ignoreCase = true),
                                onClick = { bankName = bank; page2Error = null },
                                label = { Text(bank) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it; page2Error = null },
                        label = { Text("Bank Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_bank_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = suffix,
                            onValueChange = { input -> suffix = input.filter { it.isDigit() }.take(4) },
                            label = { Text("Last 4 Digits") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.45f).testTag("setup_suffix_input")
                        )
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("Nickname (Optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(0.55f).testTag("setup_nickname_input")
                        )
                    }

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

                    OutlinedTextField(
                        value = currentBalanceText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' }) {
                                currentBalanceText = input
                                page2Error = null
                            }
                        },
                        label = { Text("Current Balance (${CurrencySpec.fromCode(currencyCode).symbol}) — Optional") },
                        supportingText = { Text("Used as your starting tracked balance. You can reconcile anytime.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_starting_balance_input")
                    )

                    if (page2Error != null) {
                        Text(page2Error!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }

                    fun trySaveCurrentAccount(): Boolean {
                        if (bankName.isBlank()) {
                            page2Error = "Please enter a bank name."
                            return false
                        }
                        val minor = MoneyFormatter.parseMajorToMinor(currentBalanceText.ifBlank { "0" }, currencyCode)
                        if (minor == null) {
                            page2Error = "Enter a valid balance amount."
                            return false
                        }
                        val now = System.currentTimeMillis()
                        onSaveAccount(
                            BankAccountModel(
                                accountId = 0L,
                                bankName = bankName.trim(),
                                accountNickname = nickname.trim().ifEmpty { "${bankName.trim()} ${accountType.displayName}" },
                                accountNumberSuffix = suffix,
                                accountType = accountType,
                                currencyCode = currencyCode,
                                openingBalanceMinorUnits = minor,
                                isActive = true,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        nickname = ""
                        suffix = ""
                        currentBalanceText = ""
                        page2Error = null
                        return true
                    }

                    OutlinedButton(
                        onClick = { trySaveCurrentAccount() },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setup_add_account_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save & Add Another Account")
                    }

                    Button(
                        onClick = {
                            if (suffix.isNotBlank() || currentBalanceText.isNotBlank() || nickname.isNotBlank()) {
                                if (!trySaveCurrentAccount()) return@Button
                            }
                            step = 2
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("setup_next_step_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Next: Notification Setup")
                    }

                    TextButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to Profile & Security")
                    }
                }

                // ============================================================
                // PAGE 3: Notification Access Reason + System Warning Guide
                // ============================================================
                else -> {
                    Text(
                        text = "Turn On Bank Alert Detection",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    // Full Reason Card
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Why SpendWise Needs Notification Access",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "• Automatically records new bank SMS & UPI debit/credit alerts as they arrive — no manual entry needed.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Privacy-first: SpendWise NEVER reads your old SMS inbox, NEVER asks for bank logins, and automatically ignores OTPs, promos, and balance-only messages.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• 100% Offline: SpendWise has zero internet permission, so your notifications can never leave your device.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // How to Handle Android System Warning & Restricted Setting Dialog
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = swColors.attentionContainer,
                        border = BorderStroke(1.dp, swColors.attention.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = swColors.attention,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "How to Handle Android's Warning Sign",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Android shows a standard security warning (or a \"Restricted setting\" block on Android 13+) for every app that uses Notification Access. Follow these steps:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "1. Tap \"Turn On Notification Access\" below and choose SpendWise.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "2. If you see \"Restricted setting — unavailable for your security\": tap OK, tap \"Open App Info\" below → tap the ⋮ (three dots) in the top-right corner → select \"Allow restricted settings\" and verify with your phone lock.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "3. Return to Notification Access, toggle SpendWise ON, and tap \"Allow\" on the system confirmation dialog.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Live Status & Turn On Controls
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (notificationAccessGranted) swColors.income.copy(alpha = 0.5f) else swColors.cardBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (notificationAccessGranted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (notificationAccessGranted) swColors.income else swColors.attention,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (notificationAccessGranted) "Notification Access is ON" else "Notification Access is OFF",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (notificationAccessGranted) "Ready to detect incoming bank alerts." else "Turn on below so SpendWise can track automatically.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            SpendWiseNotificationListenerService.createNotificationAccessSettingsIntent()
                                        )
                                    }.onFailure {
                                        onNotify("Open Android Settings -> Notification Access to enable SpendWise")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("setup_open_notification_access_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (notificationAccessGranted) "Manage Notification Access" else "Turn On Notification Access")
                            }

                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }.onFailure {
                                        onNotify("Open Settings -> Apps -> SpendWise -> ⋮ -> Allow restricted settings")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("setup_open_app_info_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.SettingsApplications, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open App Info (Allow Restricted Settings)")
                            }
                        }
                    }

                    Button(
                        onClick = onFinishSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("setup_finish_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Go to Dashboard")
                    }

                    TextButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to Bank Account Details")
                    }
                }
            }
        }
    }
}
