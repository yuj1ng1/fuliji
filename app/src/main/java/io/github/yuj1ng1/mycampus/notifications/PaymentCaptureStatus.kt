package io.github.yuj1ng1.mycampus.notifications

import android.content.Context
import io.github.yuj1ng1.mycampus.domain.PaymentParseReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PaymentCaptureSnapshot(
    val connected: Boolean = false, val requestedAt: Long = 0, val callbacks: Long = 0,
    val lastSource: String = "", val lastReceivedAt: Long = 0, val lastReason: String = "NONE",
    val savedCount: Long = 0, val lastScanAt: Long = 0, val scannedCount: Int = 0,
)

/** Redacted operational metadata only. Connection is always live, never restored as true from disk. */
class PaymentCaptureStatus(context: Context, namespace: String = "payment_capture_status") {
    private val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(PaymentCaptureSnapshot(
        lastSource = preferences.getString("source", "").orEmpty().takeIf { it in setOf("微信支付", "支付宝") }.orEmpty(),
        lastReceivedAt = preferences.getLong("received_at", 0), lastReason = preferences.getString("reason", "NONE").orEmpty(),
        savedCount = preferences.getLong("saved_count", 0), lastScanAt = preferences.getLong("scan_at", 0), scannedCount = preferences.getInt("scan_count", 0),
    ))
    val state = mutable.asStateFlow()
    private var sequence = 0L
    @Synchronized private fun update(change: (PaymentCaptureSnapshot) -> PaymentCaptureSnapshot) {
        val next = change(mutable.value)
        preferences.edit().putString("source", next.lastSource).putString("reason", next.lastReason).putLong("received_at", next.lastReceivedAt)
            .putLong("saved_count", next.savedCount).putLong("scan_at", next.lastScanAt).putInt("scan_count", next.scannedCount).apply()
        mutable.value = next
    }
    fun connection(value: Boolean) = update { it.copy(connected = value, requestedAt = if (value) 0 else it.requestedAt) }
    fun requested() = update { it.copy(requestedAt = System.currentTimeMillis()) }
    @Synchronized fun callback() { mutable.value = mutable.value.copy(callbacks = mutable.value.callbacks + 1) }
    fun scanned(count: Int) = update { it.copy(lastScanAt = System.currentTimeMillis(), scannedCount = count) }
    fun operationalError() = update { it.copy(lastReason = "READ_ERROR") }
    @Synchronized fun observed(source: String, reason: PaymentParseReason): Long {
        if (reason == PaymentParseReason.GROUP_SUMMARY && mutable.value.lastReceivedAt != 0L) return -1
        val token = ++sequence
        update { it.copy(lastSource = source, lastReceivedAt = System.currentTimeMillis(), lastReason = reason.name) }
        return token
    }
    @Synchronized fun completed(token: Long, inserted: Boolean, missingAmount: Boolean) = update {
        it.copy(savedCount = it.savedCount + if (inserted) 1 else 0,
            lastReason = if (token != sequence) it.lastReason else if (!inserted) "DUPLICATE" else if (missingAmount) "SAVED_REVIEW" else "SAVED")
    }
    @Synchronized fun writeFailed(token: Long) = update { if (token == sequence) it.copy(lastReason = "WRITE_ERROR") else it }
}

fun paymentCaptureMessage(reason: String): String = when (reason) {
    "SAVED" -> "已保存为待确认，核对后才计入支出"
    "SAVED_REVIEW" -> "已保存为待确认，金额需要本人核对"
    "DUPLICATE" -> "同一通知已存在，未重复写入或覆盖原记录"
    "GROUP_SUMMARY" -> "收到汇总通知，汇总本身不记账；将检查单条通知"
    "UNSUPPORTED_TITLE" -> "收到支付来源通知，但标题暂未匹配支付规则"
    "EMPTY_CONTENT" -> "收到支付来源通知，但没有可读取的正文"
    "NOT_PAYMENT" -> "通知未识别为交易记录，已忽略"
    "INCOMPLETE" -> "交易失败或尚未完成，已忽略"
    "CONFLICTING_CONTENT" -> "正文片段互相矛盾，需要手动核对"
    "INVALID_EVENT" -> "通知格式或时间无法确认，未保存"
    "WRITE_ERROR" -> "候选记录保存失败，请重试"
    "READ_ERROR" -> "现有通知读取失败，请检查监听连接或系统授权"
    "RECOGNIZED", "AMOUNT_REVIEW" -> "已识别，正在保存候选记录"
    else -> "尚未收到支付来源通知"
}
