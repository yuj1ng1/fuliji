package io.github.yuj1ng1.mycampus.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.domain.PaymentCandidates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { connected.value = true }
    override fun onListenerDisconnected() { connected.value = false }
    override fun onDestroy() { connected.value = false; super.onDestroy() }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        val notification = item.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (item.packageName !in setOf("com.tencent.mm", "com.eg.android.AlipayGphone")) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (!PaymentCandidates.acceptsTitle(title)) return
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val candidate = PaymentCandidates.parse(item.packageName, title, body, item.key, item.postTime, false) ?: return
        val app = application as CampusApplication
        app.applicationScope.launch {
            try {
                app.repository.saveCandidate(candidate)
                lastWriteFailed.value = false
            } catch (_: Exception) {
                lastWriteFailed.value = true
            }
        }
    }

    companion object {
        val connected = MutableStateFlow(false)
        val lastWriteFailed = MutableStateFlow(false)
    }
}
