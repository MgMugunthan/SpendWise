package com.example.intelligence

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.SpendWiseApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Local-only Android NotificationListenerService for automatic bank transaction detection
 * from setup onwards.
 *
 * Privacy safeguards:
 * - Processes only user-approved banking, UPI, and messaging packages.
 * - Executes parsing and Room persistence strictly off the main thread on Dispatchers.Default.
 * - Never reads historical SMS inbox and never requests READ_SMS permission.
 * - Never logs raw notification text or OTPs.
 * - Never transmits notification data off the device.
 */
class SpendWiseNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val sourcePackage = sbn.packageName ?: return
        if (sourcePackage == packageName) return

        // Skip Android group summary wrappers ("2 new messages") so we only process individual alerts
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotEmpty() } }
            .orEmpty()

        // Prefer the richest actual message body: EXTRA_BIG_TEXT -> latest InboxStyle line -> EXTRA_TEXT
        val body = when {
            bigText.length >= text.length && bigText.isNotBlank() -> bigText
            textLines.isNotEmpty() -> textLines.last()
            else -> text
        }
        if (title.isBlank() && body.isBlank()) return

        val app = applicationContext as? SpendWiseApplication ?: return
        val timestamp = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        serviceScope.launch {
            app.repository.processIncomingNotification(
                sourcePackage = sourcePackage,
                title = title,
                body = body,
                timestampMillis = timestamp,
                enforcePackageAllowList = true
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        fun isNotificationAccessGranted(context: Context): Boolean {
            return runCatching {
                val enabledListeners = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                val expected = ComponentName(context, SpendWiseNotificationListenerService::class.java).flattenToString()
                enabledListeners.split(":").any {
                    it.equals(expected, ignoreCase = true) ||
                        it.contains(context.packageName, ignoreCase = true)
                }
            }.getOrDefault(false)
        }

        fun createNotificationAccessSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
