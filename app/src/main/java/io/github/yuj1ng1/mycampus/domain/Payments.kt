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

enum class PaymentParseReason { RECOGNIZED, AMOUNT_REVIEW, UNSUPPORTED_SOURCE, GROUP_SUMMARY, UNSUPPORTED_TITLE, EMPTY_CONTENT, NOT_PAYMENT, INCOMPLETE, INVALID_EVENT, CONFLICTING_CONTENT }
data class PaymentRecognition(val candidate: PaymentCandidate?, val reason: PaymentParseReason)

object PaymentCandidates {
    private val sources = mapOf("com.tencent.mm" to "微信支付", "com.eg.android.AlipayGphone" to "支付宝")
    private val trustedTitles = mapOf(
        "com.tencent.mm" to setOf("微信支付", "微信支付凭证", "支付助手"),
        "com.eg.android.AlipayGphone" to setOf("支付宝", "支付宝支付", "支付助手", "支付宝通知", "交易提醒"),
    )
    private val yuanAmount = Regex("""(?<![\d.,\-])(\d+(?:\.\d{1,2})?)\s*元(?![\d])""")
    private val markedAmount = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)(?![\d.])""")
    private val paymentWords = Regex("支付成功|付款成功|付款|消费|支出|退款|收款|转账|充值")
    private val incompleteStatus = Regex("支付失败|付款失败|退款失败|扣款失败|交易失败|交易关闭|交易取消|已取消|待支付|待付款|未支付|支付中|处理中")
    private val alipayExpense = Regex("""^\s*(?:你|您)有一笔\s*(\d+(?:\.\d{1,2})?)\s*元\s*的?\s*支出(?=\s|[，。,；;！!]|$)""")
    private val expenseClauses = Regex("""\d+(?:\.\d{1,2})?\s*元\s*的?\s*支出""")

    fun sourceName(packageName: String): String? = sources[packageName]
    fun isSupportedPackage(packageName: String) = packageName in sources
    fun acceptsTitle(packageName: String, title: String): Boolean = title.trim() in trustedTitles[packageName].orEmpty()
    fun isIncomplete(body: String) = incompleteStatus.containsMatchIn(body)

    fun parse(packageName: String, title: String, body: String, key: String, postTime: Long, groupSummary: Boolean): PaymentCandidate? =
        inspect(packageName, title, body, key, postTime, groupSummary).candidate

    fun inspect(packageName: String, title: String, body: String, key: String, postTime: Long, groupSummary: Boolean): PaymentRecognition {
        fun skip(reason: PaymentParseReason) = PaymentRecognition(null, reason)
        val sourceName = sources[packageName] ?: return skip(PaymentParseReason.UNSUPPORTED_SOURCE)
        if (groupSummary) return skip(PaymentParseReason.GROUP_SUMMARY)
        if (!acceptsTitle(packageName, title)) return skip(PaymentParseReason.UNSUPPORTED_TITLE)
        if (body.isBlank()) return skip(PaymentParseReason.EMPTY_CONTENT)
        if (body.length > 4000 || postTime <= 0 || key.isBlank()) return skip(PaymentParseReason.INVALID_EVENT)
        if (isIncomplete(body)) return skip(PaymentParseReason.INCOMPLETE)
        if (!paymentWords.containsMatchIn(body)) return skip(PaymentParseReason.NOT_PAYMENT)
        val explicitAlipayExpense = if (packageName == "com.eg.android.AlipayGphone" && title.trim() == "交易提醒") alipayExpense.find(body) else null
        val kind = when {
            explicitAlipayExpense != null -> "EXPENSE"
            "退款" in body -> "REFUND"
            "转账" in body -> "TRANSFER"
            "充值" in body -> "TOPUP"
            "收款" in body -> "INCOME"
            else -> "EXPENSE"
        }
        val amounts = yuanAmount.findAll(body).toList().ifEmpty { markedAmount.findAll(body).toList() }
        // Only the verified Alipay expense sentence disambiguates a trailing promotion amount.
        // Never select the first/largest amount from arbitrary multi-amount notifications.
        val amount = if (explicitAlipayExpense != null) {
            if (expenseClauses.findAll(body).count() == 1) Money.cents(explicitAlipayExpense.groupValues[1]) else null
        } else if (amounts.size == 1) Money.cents(amounts.single().groupValues[1]) else null
        // Recognition is deliberately a draft until actual device samples are validated.
        val identity = listOf(packageName, key, postTime.toString(), title, body).joinToString("\u0000")
        return PaymentRecognition(PaymentCandidate(sha256(identity), sourceName, kind, amount, postTime),
            if (amount == null) PaymentParseReason.AMOUNT_REVIEW else PaymentParseReason.RECOGNIZED)
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
