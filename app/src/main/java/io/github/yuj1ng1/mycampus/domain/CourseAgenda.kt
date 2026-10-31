package io.github.yuj1ng1.mycampus.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class SectionTime(val start: LocalTime, val end: LocalTime)

/** Sections 1–10 are transcribed from the user's timetable screenshot. No extrapolated evening times. */
object CourseTimes {
    private val sections = listOf(
        "08:20" to "09:05", "09:15" to "10:00", "10:15" to "11:00", "11:05" to "11:50",
        "14:00" to "14:45", "14:55" to "15:40", "15:55" to "16:40", "16:50" to "17:35",
        "18:30" to "19:15", "19:25" to "20:10",
    ).map { SectionTime(LocalTime.parse(it.first), LocalTime.parse(it.second)) }
    fun section(number: Int): SectionTime? = sections.getOrNull(number - 1)
    fun range(first: Int, last: Int): SectionTime? = if (first in 1..last && last <= sections.size)
        SectionTime(sections[first - 1].start, sections[last - 1].end) else null
    fun label(time: LocalTime) = time.format(DateTimeFormatter.ofPattern("HH:mm"))
    fun label(first: Int, last: Int): String = range(first, last)?.let { label(it.start) + "–" + label(it.end) } ?: "时间待补充"
}

enum class AgendaState { READY, UNKNOWN_TERM, OUTSIDE_TERM }
data class DayAgenda(val state: AgendaState, val courses: List<ScheduledCourse>, val current: List<ScheduledCourse>,
                     val next: List<ScheduledCourse>, val unknownTimes: Int, val unscheduled: Int)

object CourseAgenda {
    fun forDay(scope: String, rows: List<DisplayRow>, date: LocalDate, now: LocalTime): DayAgenda {
        val schedule = CourseSchedules.read(rows)
        val term = CourseSchedules.term(scope) ?: return DayAgenda(AgendaState.UNKNOWN_TERM, emptyList(), emptyList(), emptyList(), 0, schedule.unscheduled.size)
        val week = term.weekOn(date) ?: return DayAgenda(AgendaState.OUTSIDE_TERM, emptyList(), emptyList(), emptyList(), 0, schedule.unscheduled.size)
        val courses = schedule.forWeek(week).filter { it.day == date.dayOfWeek.value }
            .sortedWith(compareBy({ it.firstSection }, { it.lastSection }, { it.name }))
        val current = courses.filter { course -> CourseTimes.range(course.firstSection, course.lastSection)?.let { now >= it.start && now < it.end } == true }
        val future = courses.filter { course -> CourseTimes.range(course.firstSection, course.lastSection)?.let { now < it.start } == true }
        val nextStart = future.firstOrNull()?.let { CourseTimes.range(it.firstSection, it.lastSection)?.start }
        val next = future.filter { CourseTimes.range(it.firstSection, it.lastSection)?.start == nextStart }
        return DayAgenda(AgendaState.READY, courses, current, next,
            courses.count { CourseTimes.range(it.firstSection, it.lastSection) == null }, schedule.unscheduled.size)
    }
}
