package io.github.yuj1ng1.mycampus.data

import io.github.yuj1ng1.mycampus.domain.SchoolCalendar
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate

data class DailySpending(val date: LocalDate, val cents: BigInteger) {
    val yuan: String get() = BigDecimal(cents, 2).toPlainString()
}

private fun spendingByDate(entries: List<LedgerEntry>): Map<LocalDate, BigInteger> {
    val totals = mutableMapOf<LocalDate, BigInteger>()
    entries.asSequence().filter { it.status == "CONFIRMED" && it.kind == "EXPENSE" && (it.amountCents ?: 0L) > 0L }.forEach { entry ->
        val date = Instant.ofEpochMilli(entry.occurredAt).atZone(SchoolCalendar.zone).toLocalDate()
        totals[date] = (totals[date] ?: BigInteger.ZERO) + BigInteger.valueOf(requireNotNull(entry.amountCents))
    }
    return totals
}

fun expenseTotal(entries: List<LedgerEntry>, date: LocalDate): String = BigDecimal(spendingByDate(entries)[date] ?: BigInteger.ZERO, 2).toPlainString()

fun monthlyExpenseTotal(entries: List<LedgerEntry>, today: LocalDate): String {
    val start = today.withDayOfMonth(1)
    val cents = spendingByDate(entries).asSequence().filter { it.key in start..today }.fold(BigInteger.ZERO) { sum, day -> sum + day.value }
    return BigDecimal(cents, 2).toPlainString()
}

/** Seven local accounting dates including today; zero means no confirmed local spending entries. */
fun lastSevenDaysSpending(entries: List<LedgerEntry>, today: LocalDate): List<DailySpending> {
    val byDate = spendingByDate(entries)
    return (6L downTo 0L).map { offset -> today.minusDays(offset).let { DailySpending(it, byDate[it] ?: BigInteger.ZERO) } }
}
