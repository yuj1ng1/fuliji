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
}
