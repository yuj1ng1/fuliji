package io.github.yuj1ng1.mycampus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeekRulesTest {
    @Test fun parityAppliesOnlyToItsOwnSegment() {
        assertEquals(setOf(1, 5, 7, 8, 10, 11, 12, 13, 14, 15),
            WeekRules.parse("1周,5-7周(单),8-10周(双),11-15周"))
    }
    @Test fun inclusiveAndNoncontiguousWeeks() {
        assertEquals(setOf(1, 6, 7, 8), WeekRules.parse("1周,6-8周"))
    }
    @Test fun fullwidthPunctuation() {
        assertEquals(setOf(2, 4, 7), WeekRules.parse("2－4周（双），7周"))
    }
    @Test fun unknownSyntaxIsRejected() {
        listOf("", "0周", "4-2周", "1-99周", "5-7周(未知)", "1周,").forEach {
            assertTrue(it, runCatching { WeekRules.parse(it) }.isFailure)
        }
    }
    @Test fun springMondayIsNotTheTuesdayClassStart() {
        val spring = SchoolCalendar.terms[1]
        assertEquals(1, spring.weekOn(LocalDate.of(2027, 2, 23)))
        assertEquals(1, spring.weekOn(LocalDate.of(2027, 2, 28)))
        assertEquals(2, spring.weekOn(LocalDate.of(2027, 3, 1)))
    }
    @Test fun holidayAndYearBoundary() {
        assertNull(SchoolCalendar.termOn(LocalDate.of(2027, 2, 1)))
        assertEquals(17, SchoolCalendar.terms[0].weekOn(LocalDate.of(2027, 1, 1)))
        assertNull(SchoolCalendar.terms[0].weekOn(LocalDate.of(2027, 1, 18)))
    }
}
