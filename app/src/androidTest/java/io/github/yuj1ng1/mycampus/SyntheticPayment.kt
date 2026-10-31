package io.github.yuj1ng1.mycampus

import android.app.Notification
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry

/** Synthetic payloads enter the same reader as the service. No Alipay app or real transaction is used. */
internal fun syntheticPayment(
    text: String = "你有一笔18.60元的支出，点击领3元合成优惠券。",
    title: String = "交易提醒", id: Int = 7106, time: Long = 1234567,
    packageName: String = "com.eg.android.AlipayGphone", summary: Boolean = false,
    extras: (Notification) -> Unit = {},
): StatusBarNotification {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val notice = Notification.Builder(context, "synthetic-only")
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).build()
    if (summary) notice.flags = notice.flags or Notification.FLAG_GROUP_SUMMARY
    extras(notice)
    return StatusBarNotification(packageName, packageName, id, "synthetic-payment-$id", 10000, 0, 0, notice, UserHandle.getUserHandleForUid(10000), time)
}
