package io.github.yuj1ng1.mycampus.data

import io.github.yuj1ng1.mycampus.domain.SchoolCalendar
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate

fun expenseTotal(entries: List<LedgerEntry>, date: LocalDate): String {
    val total = entries.asSequence().filter {
        it.status == "CONFIRMED" && it.kind == "EXPENSE" && it.amountCents != null &&
            Instant.ofEpochMilli(it.occurredAt).atZone(SchoolCalendar.zone).toLocalDate() == date
    }.fold(BigInteger.ZERO) { sum, item -> sum + BigInteger.valueOf(requireNotNull(item.amountCents)) }
    return BigDecimal(total, 2).toPlainString()
}
