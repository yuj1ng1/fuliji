package io.github.yuj1ng1.mycampus.domain

import org.junit.Assert.*
import org.junit.Test

class PaymentsTest {
    private fun candidate(body: String, key: String = "key-a", time: Long = 1234L, title: String = "微信支付", summary: Boolean = false) =
        PaymentCandidates.parse("com.tencent.mm", title, body, key, time, summary)

    @Test fun moneyUsesExactCents() {
        assertEquals(1230L, Money.cents("12.30"))
        assertEquals("12.30", Money.yuan(1230))
    }
    @Test fun invalidAmountsAreNotRoundedOrMadePositive() {
        listOf("0", "-1", "1.001", "NaN", "1e3", "9999999999999999999999").forEach { assertNull(it, Money.cents(it)) }
    }
    @Test fun normalChatAndOtherAppsAreDiscarded() {
        assertNull(candidate("今天支付成功12.30元", title = "示例同学"))
        assertNull(PaymentCandidates.parse("other.app", "微信支付", "付款12.30元", "key-a", 1234, false))
        assertNull(candidate("你的验证码123456"))
    }
    @Test fun groupedNotificationsAreNotTransactions() {
        assertNull(candidate("付款12.30元", summary = true))
    }
    @Test fun failedAndPendingPaymentsAreDiscarded() {
        listOf("付款失败12.30元", "待支付12.30元", "退款失败5.00元").forEach { assertNull(candidate(it)) }
    }
    @Test fun explicitPaymentProducesOnlyACandidate() {
        val draft = requireNotNull(candidate("付款成功12.30元"))
        assertEquals(1230L, draft.amountCents)
        assertEquals("EXPENSE", draft.kind)
        assertEquals("微信支付", draft.sourceName)
    }
    @Test fun ambiguousAmountsRequireUserInput() {
        assertNull(requireNotNull(candidate("付款20.00元，优惠2.00元")).amountCents)
        assertNull(requireNotNull(candidate("付款-5.00元")).amountCents)
        assertNull(requireNotNull(candidate("付款1,000.00元")).amountCents)
    }
    @Test fun sameEventIsStableButTwoSameAmountPaymentsRemainSeparate() {
        val first = requireNotNull(candidate("付款12.30元"))
        assertEquals(first.eventId, requireNotNull(candidate("付款12.30元")).eventId)
        assertNotEquals(first.eventId, requireNotNull(candidate("付款12.30元", key = "key-b")).eventId)
        assertNotEquals(first.eventId, requireNotNull(candidate("付款12.30元", time = 1235L)).eventId)
    }
    @Test fun refundIsNotAnExpense() {
        val refund = requireNotNull(candidate("退款成功5.00元"))
        assertEquals("REFUND", refund.kind)
        assertEquals(500L, refund.amountCents)
    }

    private fun alipay(body: String, title: String = "交易提醒") =
        PaymentCandidates.inspect("com.eg.android.AlipayGphone", title, body, "synthetic-alipay", 1234, false)

    @Test fun alipayExpenseSentenceExcludesTrailingCouponAmount() {
        val reading = alipay("你有一笔18.60元的支出，点击领3元合成优惠券。")
        val draft = requireNotNull(reading.candidate)
        assertEquals(1860L, draft.amountCents)
        assertEquals("EXPENSE", draft.kind)
        assertEquals("支付宝", draft.sourceName)
        assertEquals(PaymentParseReason.RECOGNIZED, reading.reason)
    }

    @Test fun primaryExpenseCanBeSmallerThanPromotionAndIsNotARecharge() {
        val draft = requireNotNull(alipay("您有一笔4.50元的支出，点击领100元充值优惠。 ").candidate)
        assertEquals(450L, draft.amountCents)
        assertEquals("EXPENSE", draft.kind)
    }

    @Test fun tradeReminderTitleIsRestrictedToAlipay() {
        assertNull(candidate("你有一笔18.60元的支出。", title = "交易提醒"))
        assertNull(PaymentCandidates.parse("other.app", "交易提醒", "你有一笔18.60元的支出。", "key", 1, false))
        assertNull(alipay("点击领取3元优惠券。").candidate)
        assertNull(alipay("您收到一条新消息").candidate)
    }

    @Test fun failedOrPendingTradeRemindersStayOutOfLedger() {
        listOf("你有一笔18.60元的支出，交易失败。", "你有一笔18.60元的支出，处理中。", "扣款失败18.60元")
            .forEach { assertEquals(PaymentParseReason.INCOMPLETE, alipay(it).reason) }
    }

    @Test fun multipleExpenseClausesAndUnrecognizedMultiAmountFormatsRequireReview() {
        assertNull(requireNotNull(alipay("你有一笔18.60元的支出，另有一笔5.40元的支出。").candidate).amountCents)
        assertNull(requireNotNull(alipay("付款18.60元，优惠3元。").candidate).amountCents)
        assertNull(requireNotNull(alipay("你有一笔18.60元的支出，优惠3元。", title = "支付宝").candidate).amountCents)
    }

    @Test fun invalidExpenseAmountAndEventMetadataAreNotInvented() {
        assertNull(requireNotNull(alipay("你有一笔0元的支出。").candidate).amountCents)
        assertNull(alipay("").candidate)
        assertEquals(PaymentParseReason.INVALID_EVENT,
            PaymentCandidates.inspect("com.eg.android.AlipayGphone", "交易提醒", "支出1元", "", 0, false).reason)
    }
}
