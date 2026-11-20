package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.spendWiseDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "spendwise_settings"
)

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.LIGHT,
    val hideBalances: Boolean = false,
    val defaultCurrency: String = "INR",
    val notificationDetectionEnabled: Boolean = true,
    val allowedPackages: Set<String> = DEFAULT_SUPPORTED_PACKAGES,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val autoLockTimeoutSeconds: Int = 60,
    val hasCompletedOnboarding: Boolean = false,
    val userName: String = "",
    val userAge: String = "",
    val userEmail: String = "",
    val userAddress: String = ""
) {
    companion object {
        val DEFAULT_SUPPORTED_PACKAGES = setOf(
            // Messaging & SMS apps across stock & OEM Android devices
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.miui.mms",
            "com.oneplus.mms",
            "com.coloros.mms",
            "com.vivo.message",
            "com.truecaller",
            // Supported banking apps
            "com.sbi.lotusintouch",
            "com.sbi.SBIFreedomPlus",
            "com.snapwork.hdfc",
            "com.csam.icici.bank.imobile",
            "com.icicibank.pockets",
            "com.axis.mobile",
            "com.msf.kbank.mobile",
            "com.canarabank.mobility",
            "com.IndianBank.IndOASIS",
            "com.bob.mconnectplus",
            "com.pnb.pnbone",
            "com.fedmobile",
            "com.idfcfirstbank.optimus",
            "com.rblbank.mobank",
            // Popular UPI apps
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.amazon.mShop.android.shopping",
            "com.dreamplug.androidapp"
        )
    }
}

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val HIDE_BALANCES = booleanPreferencesKey("hide_balances")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val DETECTION_ENABLED = booleanPreferencesKey("notification_detection_enabled")
        val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val AUTO_LOCK_SECONDS = intPreferencesKey("auto_lock_seconds")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AGE = stringPreferencesKey("user_age")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_ADDRESS = stringPreferencesKey("user_address")
    }

    val settingsFlow: Flow<AppSettings> = context.spendWiseDataStore.data.map { prefs ->
        AppSettings(
            themePreference = runCatching {
                ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.LIGHT.name)
            }.getOrDefault(ThemePreference.LIGHT),
            hideBalances = prefs[Keys.HIDE_BALANCES] ?: false,
            defaultCurrency = prefs[Keys.DEFAULT_CURRENCY] ?: "INR",
            notificationDetectionEnabled = prefs[Keys.DETECTION_ENABLED] ?: true,
            allowedPackages = prefs[Keys.ALLOWED_PACKAGES] ?: AppSettings.DEFAULT_SUPPORTED_PACKAGES,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            hasPinConfigured = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            autoLockTimeoutSeconds = prefs[Keys.AUTO_LOCK_SECONDS] ?: 60,
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            userName = prefs[Keys.USER_NAME] ?: "",
            userAge = prefs[Keys.USER_AGE] ?: "",
            userEmail = prefs[Keys.USER_EMAIL] ?: "",
            userAddress = prefs[Keys.USER_ADDRESS] ?: ""
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun getPinHashAndSalt(): Pair<String, String>? {
        val prefs = context.spendWiseDataStore.data.first()
        val hash = prefs[Keys.PIN_HASH] ?: return null
        val salt = prefs[Keys.PIN_SALT] ?: return null
        return hash to salt
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.spendWiseDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setHideBalances(hide: Boolean) {
        context.spendWiseDataStore.edit { it[Keys.HIDE_BALANCES] = hide }
    }

    suspend fun setDefaultCurrency(currencyCode: String) {
        context.spendWiseDataStore.edit { it[Keys.DEFAULT_CURRENCY] = currencyCode }
    }

    suspend fun setNotificationDetectionEnabled(enabled: Boolean) {
        context.spendWiseDataStore.edit { it[Keys.DETECTION_ENABLED] = enabled }
    }

    suspend fun toggleAllowedPackage(packageName: String) {
        context.spendWiseDataStore.edit { prefs ->
            val current = prefs[Keys.ALLOWED_PACKAGES] ?: AppSettings.DEFAULT_SUPPORTED_PACKAGES
            prefs[Keys.ALLOWED_PACKAGES] = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        }
    }

    suspend fun setPinCredentials(hash: String, salt: String, enableLock: Boolean = true) {
        context.spendWiseDataStore.edit {
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
            it[Keys.APP_LOCK_ENABLED] = enableLock
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.spendWiseDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.spendWiseDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun saveUserProfile(
        name: String,
        age: String,
        email: String,
        address: String
    ) {
        context.spendWiseDataStore.edit {
            it[Keys.USER_NAME] = name.trim()
            it[Keys.USER_AGE] = age.filter { ch -> ch.isDigit() }.take(3)
            it[Keys.USER_EMAIL] = email.trim()
            it[Keys.USER_ADDRESS] = address.trim()
        }
    }

    suspend fun markOnboardingCompleted() {
        context.spendWiseDataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = true }
    }

    suspend fun resetPrivacyCredentials() {
        context.spendWiseDataStore.edit {
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
            it[Keys.APP_LOCK_ENABLED] = false
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun clearUserProfile() {
        context.spendWiseDataStore.edit {
            it.remove(Keys.USER_NAME)
            it.remove(Keys.USER_AGE)
            it.remove(Keys.USER_EMAIL)
            it.remove(Keys.USER_ADDRESS)
        }
    }
}
