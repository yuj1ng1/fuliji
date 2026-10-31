package io.github.yuj1ng1.mycampus.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.domain.PaymentCandidates
import java.lang.ref.WeakReference

class PaymentNotificationListener : NotificationListenerService() {
    private val app get() = application as CampusApplication
    override fun onListenerConnected() {
        super.onListenerConnected()
        live = WeakReference(this)
        app.paymentStatus.connection(true)
        scanExisting()
    }
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (live?.get() === this) { live = null; app.paymentStatus.connection(false) }
    }
    override fun onDestroy() {
        if (live?.get() === this) { live = null; app.paymentStatus.connection(false) }
        super.onDestroy()
    }
    override fun onNotificationPosted(item: StatusBarNotification?) {
        if (item != null) app.paymentCapture.receive(item)
    }
    private fun scanExisting() {
        if (live?.get() !== this || !app.paymentStatus.state.value.connected) return
        try {
            val existing = activeNotifications.orEmpty().filter { PaymentCandidates.isSupportedPackage(it.packageName) }
                .sortedWith(compareBy<StatusBarNotification> { if (it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) 0 else 1 }.thenBy { it.postTime })
            app.paymentStatus.scanned(existing.size)
            existing.forEach { app.paymentCapture.receive(it) }
        } catch (_: Exception) { app.paymentStatus.operationalError() }
    }
    companion object {
        private var live: WeakReference<PaymentNotificationListener>? = null
        private var lastRequestElapsed = 0L
        /** Reuses existing user authorization; never changes access settings, filters, or battery policy. */
        fun ensureConnected(context: Context, manual: Boolean = false) {
            val app = context.applicationContext as CampusApplication
            val component = ComponentName(context, PaymentNotificationListener::class.java)
            if (!context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)) return
            val current = live?.get()
            if (current != null && app.paymentStatus.state.value.connected) {
                if (manual) current.scanExisting()
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (lastRequestElapsed != 0L && now - lastRequestElapsed in 0 until (if (manual) 2_000L else 60_000L)) return
            lastRequestElapsed = now
            try {
                app.paymentStatus.requested()
                NotificationListenerService.requestRebind(component)
            } catch (_: Exception) { app.paymentStatus.operationalError() }
        }
    }
}
