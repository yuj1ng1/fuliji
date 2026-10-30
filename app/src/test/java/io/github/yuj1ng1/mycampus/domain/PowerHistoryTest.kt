package io.github.yuj1ng1.mycampus.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PowerHistoryTest {
    private val today = LocalDate.of(2026, 10, 30)
    private fun capture(vararg rows: String) = CaptureReader.read(CampusSource.POWER_USAGE, CampusSource.POWER_USAGE.entryUrl,
        JSONObject().put("status", "ok").put("scope", "示例宿舍").put("usage", JSONArray(rows.toList())).toString())

    @Test fun sourceHistoryPreservesDatesAndExactNumbers() {
        val data = capture("2026-10-29\n2.50", "2026-10-28 0")
        assertEquals(listOf(DisplayRow("2026-10-28", "0"), DisplayRow("2026-10-29", "2.50")), data.rows)
        assertEquals(data.rows, CaptureReader.decode(data.payload))
    }

    @Test fun sevenCompletedDaysKeepMissingSeparateFromZero() {
        val result = PowerHistory.lastSevenDays(capture("2026-10-29 2.50", "2026-10-27 0", "2026-10-30 99", "2026-10-22 88").rows, today)
        assertEquals(7, result.size)
        assertEquals(today.minusDays(7), result.first().date)
        assertEquals(today.minusDays(1), result.last().date)
        assertNull(result[5].value)
        assertEquals(BigDecimal.ZERO, result[4].value)
        assertEquals(BigDecimal("2.50"), result.last().value)
        assertEquals(2, result.count { it.value != null })
    }

    @Test fun invalidOrDuplicateHistoryCannotReplacePreviousData() {
        listOf(
            arrayOf("2026-02-30 2"), arrayOf("2026-10-29 -2"), arrayOf("2026-10-29 NaN"),
            arrayOf("2026-10-29 1", "2026-10-29 2"), arrayOf("2026-10-29 ***"), emptyArray(),
        ).forEach { assertTrue(runCatching { capture(*it) }.isFailure) }
    }

    @Test fun windowWorksAcrossYearBoundary() {
        val result = PowerHistory.lastSevenDays(listOf(DisplayRow("2026-12-31", "1.20")), LocalDate.of(2027, 1, 3))
        assertEquals(LocalDate.of(2026, 12, 27), result.first().date)
        assertEquals(BigDecimal("1.20"), result[4].value)
        assertEquals(LocalDate.of(2027, 1, 2), result.last().date)
    }
}
