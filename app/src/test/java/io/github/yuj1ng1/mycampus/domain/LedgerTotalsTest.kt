package io.github.yuj1ng1.mycampus.domain

import io.github.yuj1ng1.mycampus.data.LedgerEntry
import io.github.yuj1ng1.mycampus.data.expenseTotal
import io.github.yuj1ng1.mycampus.data.monthlyExpenseTotal
import io.github.yuj1ng1.mycampus.data.lastSevenDaysSpending
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

    @Test fun monthlyTotalUsesLocalMonthBoundaryAndExcludesFutureAndUnconfirmedEntries() {
        fun at(id: String, amount: Long?, instant: String, kind: String = "EXPENSE", status: String = "CONFIRMED") = entry(id, amount, status, kind, Instant.parse(instant).toEpochMilli())
        val rows = listOf(at("before", 10000, "2026-09-30T15:59:59Z"), at("first", 123, "2026-09-30T16:00:00Z"),
            at("last", 456, "2026-10-31T15:59:59Z"), at("after", 10000, "2026-10-31T16:00:00Z"),
            at("draft", 9999, "2026-10-30T12:00:00Z", status = "DRAFT"), at("refund", 500, "2026-10-30T12:00:00Z", kind = "REFUND"),
            at("invalid", -9999, "2026-10-30T12:00:00Z"))
        assertEquals("5.79", monthlyExpenseTotal(rows, LocalDate.of(2026, 10, 31)))
        assertEquals("1.23", monthlyExpenseTotal(rows, LocalDate.of(2026, 10, 15)))
    }

    @Test fun sevenDaysIncludesTodayAndKeepsUnrecordedLocalDaysAtZero() {
        val today = LocalDate.of(2026, 10, 31)
        fun on(id: String, amount: Long, date: LocalDate) = entry(id, amount, time = date.atStartOfDay(SchoolCalendar.zone).toInstant().toEpochMilli())
        val days = lastSevenDaysSpending(listOf(on("old", 5000, today.minusDays(7)), on("first", 123, today.minusDays(6)), on("today", 1225, today)), today)
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first().date); assertEquals(today, days.last().date)
        assertEquals(listOf("1.23", "0.00", "0.00", "0.00", "0.00", "0.00", "12.25"), days.map { it.yuan })
    }

    @Test fun confirmationAndRemovalRecomputeWithoutMovingNotificationDate() {
        val today = LocalDate.of(2026, 10, 30)
        val draft = entry("draft", 456, "DRAFT")
        assertEquals("0.00", monthlyExpenseTotal(listOf(draft), today))
        assertEquals("4.56", monthlyExpenseTotal(listOf(draft.copy(status = "CONFIRMED")), today))
        assertEquals("0.00", monthlyExpenseTotal(emptyList(), today))
        assertEquals("0.00", monthlyExpenseTotal(listOf(draft.copy(status = "CONFIRMED")), LocalDate.of(2026, 11, 1)))
    }

    @Test fun monthlyAndChartAmountsKeepCentPrecisionBeyondLongTotalRange() {
        val rows = listOf(entry("a", Long.MAX_VALUE), entry("b", Long.MAX_VALUE))
        val today = LocalDate.of(2026, 10, 30)
        assertEquals("184467440737095516.14", monthlyExpenseTotal(rows, today))
        assertEquals("184467440737095516.14", lastSevenDaysSpending(rows, today).last().yuan)
    }
}
