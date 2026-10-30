package io.github.yuj1ng1.mycampus.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest

object Money {
    fun cents(raw: String): Long? = runCatching {
        require(Regex("""^\d+(?:\.\d{1,2})?$""").matches(raw.trim()))
        BigDecimal(raw.trim()).setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2).longValueExact().also { require(it > 0) }
    }.getOrNull()

    fun yuan(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
}

data class PaymentCandidate(
    val eventId: String,
    val sourceName: String,
    val kind: String,
    val amountCents: Long?,
    val notificationTime: Long,
)

object PaymentCandidates {
    private val sources = mapOf("com.tencent.mm" to "微信支付", "com.eg.android.AlipayGphone" to "支付宝")
    private val trustedTitles = setOf("微信支付", "微信支付凭证", "支付宝", "支付宝支付", "支付助手", "支付宝通知")
    private val yuanAmount = Regex("""(?<![\d.,\-])(\d+(?:\.\d{1,2})?)\s*元(?![\d])""")
    private val markedAmount = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)(?![\d.])""")
    private val paymentWords = Regex("支付成功|付款成功|付款|消费|退款|收款|转账|充值")
    private val incompleteStatus = Regex("支付失败|付款失败|退款失败|交易关闭|交易取消|已取消|待支付|待付款|未支付|支付中|处理中")

    fun acceptsTitle(title: String): Boolean = title.trim() in trustedTitles

    fun parse(packageName: String, title: String, body: String, key: String, postTime: Long, groupSummary: Boolean): PaymentCandidate? {
        val sourceName = sources[packageName] ?: return null
        if (groupSummary || !acceptsTitle(title) || body.length !in 1..4000 ||
            !paymentWords.containsMatchIn(body) || incompleteStatus.containsMatchIn(body) ||
            postTime <= 0 || key.isBlank()) return null
        val kind = when {
            "退款" in body -> "REFUND"
            "转账" in body -> "TRANSFER"
            "充值" in body -> "TOPUP"
            "收款" in body -> "INCOME"
            else -> "EXPENSE"
        }
        val amounts = yuanAmount.findAll(body).toList().ifEmpty { markedAmount.findAll(body).toList() }
        val amount = if (amounts.size == 1) Money.cents(amounts.single().groupValues[1]) else null
        // Recognition is deliberately a draft until actual device samples are validated.
        val identity = listOf(packageName, key, postTime.toString(), title, body).joinToString("\u0000")
        return PaymentCandidate(sha256(identity), sourceName, kind, amount, postTime)
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
