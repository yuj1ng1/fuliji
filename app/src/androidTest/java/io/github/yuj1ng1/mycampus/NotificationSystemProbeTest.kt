package io.github.yuj1ng1.mycampus

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.notifications.PaymentNotificationListener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/** Opt-in on our emulator only. Uses a real shell-source notification, never impersonates a payment app. */
@RunWith(AndroidJUnit4::class)
class NotificationSystemProbeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue(label, condition())
    }
    @Suppress("UNCHECKED_CAST") private fun listener(): PaymentNotificationListener? {
        val field = PaymentNotificationListener::class.java.getDeclaredField("live").apply { isAccessible = true }
        return (field.get(null) as? WeakReference<PaymentNotificationListener>)?.get()
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }

    @Test fun actualSystemBindingRebindAndCallbacksAreDistinctFromSyntheticPaymentParsing() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("notification_system_probe") == "yes")
        val context = instrumentation.targetContext
        val component = ComponentName(context, PaymentNotificationListener::class.java)
        assertTrue("Grant access only on the owned emulator before this opt-in probe",
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component))
        val app = context.applicationContext as CampusApplication
        val tag = "fuliji-synthetic-framework"
        val original = app.repository.ledger.first()
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync { PaymentNotificationListener.ensureConnected(context, manual = true) }
            waitFor("onListenerConnected", { app.paymentStatus.state.value.connected })
            val initialScan = app.paymentStatus.state.value.lastScanAt
            assertTrue(initialScan > 0)
            try {
                val callbacks = app.paymentStatus.state.value.callbacks
                shell("cmd notification post -t FulijiSynthetic $tag synthetic-framework-delivery")
                waitFor("real framework callback", { app.paymentStatus.state.value.callbacks > callbacks })
                var ownKey: String? = null
                instrumentation.runOnMainSync {
                    ownKey = listener()!!.activeNotifications.firstOrNull { n -> n.packageName == "com.android.shell" && n.tag == tag }?.key
                }
                assertNotNull("our actual visible shell notification", ownKey)
                // Unsupported shell source must never enter the payment ledger.
                assertEquals(original, app.repository.ledger.first())
                instrumentation.runOnMainSync { listener()!!.requestUnbind() }
                waitFor("onListenerDisconnected", { !app.paymentStatus.state.value.connected })
                waitFor("explicit foreground/manual rebind", {
                    instrumentation.runOnMainSync { PaymentNotificationListener.ensureConnected(context, manual = true) }
                    app.paymentStatus.state.value.connected
                })
                assertTrue(app.paymentStatus.state.value.lastScanAt >= initialScan)
                val beforeManual = app.paymentStatus.state.value.lastScanAt
                SystemClock.sleep(20)
                instrumentation.runOnMainSync { PaymentNotificationListener.ensureConnected(context, manual = true) }
                assertTrue(app.paymentStatus.state.value.lastScanAt > beforeManual)
            } finally {
                instrumentation.runOnMainSync {
                    listener()?.activeNotifications?.filter { n -> n.packageName == "com.android.shell" && n.tag == tag }
                        ?.forEach { n -> listener()?.cancelNotification(n.key) }
                }
            }
        }
    }
}
