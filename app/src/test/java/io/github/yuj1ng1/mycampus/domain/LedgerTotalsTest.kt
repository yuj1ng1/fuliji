package io.github.yuj1ng1.mycampus.domain

import io.github.yuj1ng1.mycampus.data.LedgerEntry
import io.github.yuj1ng1.mycampus.data.expenseTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LedgerTotalsTest {
    private fun entry(id: String, amount: Long?, status: String = "CONFIRMED", kind: String = "EXPENSE",
                      time: Long = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()) =
        LedgerEntry(id, "合成样例", kind, amount, time, "USER", "", status)

    @Test fun draftsAndRefundsAreNotAddedAsSpending() {
        val rows = listOf(entry("a", 123), entry("b", 1000, "DRAFT"), entry("c", 1000, kind = "REFUND"), entry("d", null))
        assertEquals("1.23", expenseTotal(rows, LocalDate.of(2026, 10, 30)))
    }
    @Test fun localDayUsesShanghaiBoundary() {
        val row = entry("a", 100, time = Instant.parse("2026-10-29T16:00:00Z").toEpochMilli())
        assertEquals("1.00", expenseTotal(listOf(row), LocalDate.of(2026, 10, 30)))
        assertEquals("0.00", expenseTotal(listOf(row), LocalDate.of(2026, 10, 29)))
    }
    @Test fun largeTotalsDoNotWrapToNegative() {
        assertEquals("184467440737095516.14", expenseTotal(listOf(entry("a", Long.MAX_VALUE), entry("b", Long.MAX_VALUE)), LocalDate.of(2026, 10, 30)))
    }
}
