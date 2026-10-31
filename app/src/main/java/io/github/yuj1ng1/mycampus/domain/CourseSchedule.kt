package io.github.yuj1ng1.mycampus.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ScheduledCourse(
    val name: String, val day: Int, val firstSection: Int, val lastSection: Int,
    val weeks: Set<Int>, val weekRule: String, val details: List<String>,
)
data class CourseSchedule(val courses: List<ScheduledCourse>, val unscheduled: List<DisplayRow>) {
    fun forWeek(week: Int) = courses.filter { week in it.weeks }
    val lastWeek get() = courses.maxOfOrNull { it.weeks.max() } ?: 1
}
data class CoursePlacement(val course: ScheduledCourse, val lane: Int, val lanes: Int)

object CourseSchedules {
    fun read(rows: List<DisplayRow>): CourseSchedule {
        val courses = mutableListOf<ScheduledCourse>()
        val other = mutableListOf<DisplayRow>()
        val heading = Regex("^星期([一二三四五六日天])\\s*·\\s*(.+)$")
        val rule = Regex("^\\((\\d{1,2})(?:-(\\d{1,2}))?节\\)(.+)$")
        for (row in rows) {
            val parsed = runCatching {
                val title = heading.matchEntire(row.title) ?: error("No fixed weekday")
                val lines = row.detail.lines().map(String::trim).filter(String::isNotBlank)
                val match = rule.matchEntire(lines.first().replace('（', '(').replace('）', ')')) ?: error("No fixed sections")
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].ifEmpty { match.groupValues[1] }.toInt()
                require(start in 1..24 && end in start..24)
                ScheduledCourse(title.groupValues[2], if (title.groupValues[1] == "天") 7 else "一二三四五六日".indexOf(title.groupValues[1]) + 1,
                    start, end, WeekRules.parse(match.groupValues[3]), match.groupValues[3], lines.drop(1))
            }.getOrNull()
            if (parsed == null) other += row else courses += parsed
        }
        return CourseSchedule(courses, other)
    }

    /** Partition overlapping intervals into columns. Inclusive section ranges must never hide each other. */
    fun place(courses: List<ScheduledCourse>): List<CoursePlacement> {
        val result = mutableListOf<CoursePlacement>()
        for (day in 1..7) {
            val ordered = courses.filter { it.day == day }.sortedWith(compareBy({ it.firstSection }, { it.lastSection }, { it.name }))
            var group = mutableListOf<Pair<ScheduledCourse, Int>>()
            val ends = mutableListOf<Int>()
            var groupEnd = 0
            fun flush() {
                group.forEach { (course, lane) -> result += CoursePlacement(course, lane, ends.size) }
                group = mutableListOf(); ends.clear()
            }
            for (course in ordered) {
                if (group.isNotEmpty() && course.firstSection > groupEnd) flush()
                var lane = ends.indexOfFirst { it < course.firstSection }
                if (lane < 0) { lane = ends.size; ends += course.lastSection } else ends[lane] = course.lastSection
                group += course to lane
                groupEnd = if (group.size == 1) course.lastSection else maxOf(groupEnd, course.lastSection)
            }
            if (group.isNotEmpty()) flush()
        }
        return result
    }

    fun term(scope: String): AcademicTerm? {
        val match = Regex("(\\d{4})[-—](\\d{4}).*?第?([12])学期").find(scope) ?: return null
        val year = match.groupValues[1] + "—" + match.groupValues[2]
        val term = if (match.groupValues[3] == "1") "第一学期" else "第二学期"
        return SchoolCalendar.terms.firstOrNull { it.label == "$year · $term" }
    }

    fun weekCount(term: AcademicTerm?, schedule: CourseSchedule) = maxOf(schedule.lastWeek,
        term?.let { (ChronoUnit.DAYS.between(it.monday, it.end) / 7 + 1).toInt() } ?: 1).coerceIn(1, 60)

    fun monday(term: AcademicTerm?, week: Int): LocalDate? = term?.monday?.plusWeeks(week - 1L)
}
