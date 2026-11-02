package io.github.yuj1ng1.mycampus.domain

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun moneyUsesExactCents() {
        assertEquals(1230L, Money.cents("12.30"))
        assertEquals("12.30", Money.yuan(1230))
    }
    @Test fun invalidAmountsAreNotRoundedOrMadePositive() {
        listOf("0", "-1", "1.001", "NaN", "1e3", "9999999999999999999999").forEach { assertNull(it, Money.cents(it)) }
    }
}
