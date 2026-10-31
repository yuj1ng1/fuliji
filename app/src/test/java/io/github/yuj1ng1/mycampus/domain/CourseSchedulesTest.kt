package io.github.yuj1ng1.mycampus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CourseSchedulesTest {
    @Test fun weeklyFilterHonorsSegmentParityAndKeepsUnscheduledRecords() {
        val schedule = CourseSchedules.read(listOf(
            DisplayRow("星期一 · 合成课程甲", "(1-2节)1-4周(单),8-10周\n示例教室\n示例教师"),
            DisplayRow("星期五 · 合成课程乙", "(3-4节)2-8周(双)\n另一教室"),
            DisplayRow("实践与其他课程", "示例实践，没有固定星期"),
            DisplayRow("星期三 · 待核对课程", "无法识别的规则")))
        assertEquals(2, schedule.forWeek(8).size)
        assertEquals(listOf("合成课程甲"), schedule.forWeek(3).map { it.name })
        assertEquals(0, schedule.forWeek(11).size)
        assertEquals(2, schedule.unscheduled.size)
        assertEquals(listOf("示例教室", "示例教师"), schedule.courses.first().details)
    }

    @Test fun overlappingClassesArePlacedSideBySideWithoutMergingDifferentTimes() {
        val schedule = CourseSchedules.read(listOf(
            DisplayRow("星期一 · 合成甲", "(1-2节)1-16周"),
            DisplayRow("星期一 · 合成乙", "(2-3节)1-16周"),
            DisplayRow("星期一 · 合成丙", "(4-5节)1-16周"),
            DisplayRow("星期二 · 合成丁", "(1-2节)1-16周")))
        val placed = CourseSchedules.place(schedule.forWeek(8))
        assertEquals(4, placed.size)
        assertEquals(setOf(0, 1), placed.take(2).map { it.lane }.toSet())
        assertTrue(placed.take(2).all { it.lanes == 2 })
        assertTrue(placed.drop(2).all { it.lanes == 1 })
    }

    @Test fun knownAcademicCalendarMapsWeekEightAndDoesNotInventOtherTerms() {
        val term = CourseSchedules.term("2026-2027学年第1学期")!!
        assertEquals(8, term.weekOn(LocalDate.of(2026, 10, 31)))
        assertEquals(LocalDate.of(2026, 10, 26), CourseSchedules.monday(term, 8))
        assertNull(CourseSchedules.term("2028-2029学年第1学期"))
        assertNull(CourseSchedules.monday(null, 8))
    }
}
