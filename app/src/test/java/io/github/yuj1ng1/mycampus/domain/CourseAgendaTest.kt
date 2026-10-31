package io.github.yuj1ng1.mycampus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CourseAgendaTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val scope = "2026-2027学年第1学期"
    private fun row(name: String, rule: String, day: String = "一") = DisplayRow("星期$day · $name", "$rule\n示例教室")

    @Test fun tenSectionTimesMatchReferenceAndUnknownTimesAreNeverExtrapolated() {
        assertEquals("08:20", CourseTimes.label(CourseTimes.section(1)!!.start))
        assertEquals("11:50", CourseTimes.label(CourseTimes.section(4)!!.end))
        assertEquals("14:00–15:40", CourseTimes.label(5, 6))
        assertEquals("15:55–17:35", CourseTimes.label(7, 8))
        assertEquals("18:30–20:10", CourseTimes.label(9, 10))
        assertNull(CourseTimes.section(11)); assertNull(CourseTimes.range(9, 12))
        assertNull(CourseTimes.range(0, 2))
    }

    @Test fun todayUsesWeekParityAndWeekdayRatherThanAllSavedRecords() {
        val rows = listOf(row("单周", "(1-2节)1-15周(单)"), row("双周", "(3-4节)2-16周(双)"), row("明天", "(1-2节)1-16周", "二"))
        val agenda = CourseAgenda.forDay(scope, rows, monday, LocalTime.of(8, 0))
        assertEquals(AgendaState.READY, agenda.state)
        assertEquals(listOf("双周"), agenda.courses.map { it.name })
        assertEquals(listOf("双周"), agenda.next.map { it.name })
        assertTrue(CourseAgenda.forDay(scope, rows, monday.plusDays(5), LocalTime.NOON).courses.isEmpty())
    }

    @Test fun currentAndNextRespectExactBoundariesAndKeepSimultaneousClasses() {
        val rows = listOf(row("早课", "(1-2节)1-16周"), row("后课甲", "(3-4节)1-16周"), row("后课乙", "(3-4节)1-16周"))
        val starting = CourseAgenda.forDay(scope, rows, monday, LocalTime.of(8, 20))
        assertEquals(listOf("早课"), starting.current.map { it.name })
        assertEquals(2, starting.next.size)
        val ended = CourseAgenda.forDay(scope, rows, monday, LocalTime.of(10, 0))
        assertTrue(ended.current.isEmpty()); assertEquals(2, ended.next.size)
        val lastStart = CourseAgenda.forDay(scope, rows, monday, LocalTime.of(10, 15))
        assertEquals(2, lastStart.current.size); assertTrue(lastStart.next.isEmpty())
        val after = CourseAgenda.forDay(scope, rows, monday, LocalTime.of(11, 50))
        assertTrue(after.current.isEmpty()); assertTrue(after.next.isEmpty())
    }

    @Test fun unknownOrOutOfTermCalendarIsDistinctFromAFreeDay() {
        val rows = listOf(row("示例", "(1-2节)1-16周"))
        assertEquals(AgendaState.UNKNOWN_TERM, CourseAgenda.forDay("2028-2029学年第1学期", rows, monday, LocalTime.NOON).state)
        assertEquals(AgendaState.OUTSIDE_TERM, CourseAgenda.forDay(scope, rows, LocalDate.of(2027, 7, 5), LocalTime.NOON).state)
    }

    @Test fun unmappedLateSectionsAndPracticeRecordsRemainVisibleAsUncertain() {
        val agenda = CourseAgenda.forDay(scope, listOf(row("晚课待核对", "(11-12节)1-16周"), DisplayRow("实践与其他课程", "合成实践")), monday, LocalTime.of(20, 30))
        assertEquals(1, agenda.courses.size); assertEquals(1, agenda.unknownTimes); assertEquals(1, agenda.unscheduled)
        assertTrue(agenda.next.isEmpty()); assertTrue(agenda.current.isEmpty())
    }
}
