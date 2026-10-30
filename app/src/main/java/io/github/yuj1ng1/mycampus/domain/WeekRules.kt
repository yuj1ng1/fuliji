package io.github.yuj1ng1.mycampus.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object WeekRules {
    private val segment = Regex("""^(\d{1,2})(?:-(\d{1,2}))?(?:周)?(?:\(([单双])(?:周)?\))?$""")

    fun parse(raw: String): Set<Int> {
        val cleaned = raw.trim().replace("，", ",").replace("、", ",")
            .replace("（", "(").replace("）", ")").replace("—", "-")
            .replace("－", "-").replace(" ", "")
        require(cleaned.isNotBlank()) { "缺少周次" }
        return cleaned.split(",").flatMap { part ->
            val match = segment.matchEntire(part) ?: error("无法识别周次：" + part)
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].ifBlank { match.groupValues[1] }.toInt()
            require(start in 1..60 && end in start..60) { "周次超出可解释范围" }
            val parity = match.groupValues[3]
            (start..end).filter { parity.isBlank() || (it % 2 == 1) == (parity == "单") }
        }.toSortedSet().also { require(it.isNotEmpty()) { "周次规则没有有效结果" } }
    }
}

data class AcademicTerm(val label: String, val monday: LocalDate, val end: LocalDate) {
    fun weekOn(date: LocalDate): Int? = if (date < monday || date > end) null
        else (ChronoUnit.DAYS.between(monday, date) / 7 + 1).toInt()
}

object SchoolCalendar {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    val terms = listOf(
        AcademicTerm("2026—2027 · 第一学期", LocalDate.of(2026, 9, 7), LocalDate.of(2027, 1, 17)),
        AcademicTerm("2026—2027 · 第二学期", LocalDate.of(2027, 2, 22), LocalDate.of(2027, 7, 4)),
    )
    fun termOn(date: LocalDate): AcademicTerm? = terms.firstOrNull { it.weekOn(date) != null }
}
