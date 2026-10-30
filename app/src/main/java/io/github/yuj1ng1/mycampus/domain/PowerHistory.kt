package io.github.yuj1ng1.mycampus.domain

import java.math.BigDecimal
import java.time.LocalDate

data class DailyUsage(val date: LocalDate, val value: BigDecimal?)

object PowerHistory {
    /** Seven completed calendar days; missing is not zero and today may be incomplete. */
    fun lastSevenDays(rows: List<DisplayRow>, today: LocalDate): List<DailyUsage> {
        val byDate = rows.mapNotNull { row ->
            runCatching { LocalDate.parse(row.title) to BigDecimal(row.detail) }.getOrNull()
                ?.takeIf { it.second.signum() >= 0 }
        }.toMap()
        return (7L downTo 1L).map { offset -> today.minusDays(offset).let { DailyUsage(it, byDate[it]) } }
    }
}
