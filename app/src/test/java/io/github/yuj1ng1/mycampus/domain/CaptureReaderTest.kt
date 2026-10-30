package io.github.yuj1ng1.mycampus.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CaptureReaderTest {
    private fun read(source: CampusSource, value: JSONObject) = CaptureReader.read(source, source.entryUrl, value.toString())
    private fun power(value: String) = JSONObject().put("status", "ok").put("scope", "示例校区 示例楼 示例房间").put("remaining", value)

    @Test fun zeroPowerIsValidAndDecimalPrecisionIsPreserved() {
        assertEquals("0.00 度", read(CampusSource.POWER, power("剩余量：0.00度")).rows.single().detail)
        assertEquals("-1.25 度", read(CampusSource.POWER, power("-1.25度")).rows.single().detail)
    }
    @Test fun missingPowerAndUnknownUnitsAreRejected() {
        listOf("", "NaN度", "20", "20元", "1度或2度").forEach {
            assertTrue(runCatching { read(CampusSource.POWER, power(it)) }.isFailure)
        }
    }
    @Test fun loginAndPartialListsNeverBecomeEmptySuccess() {
        listOf("login", "partial", "loading", "not_queried").forEach {
            assertTrue(runCatching { read(CampusSource.GRADES, JSONObject().put("status", it)) }.isFailure)
        }
    }
    @Test fun emptyRequiresTheCorrectBusinessPageAndScope() {
        val empty = JSONObject().put("status", "empty").put("scope", "2026-2027 第1学期")
        assertTrue(read(CampusSource.EXAMS, empty).empty)
        assertTrue(runCatching { CaptureReader.read(CampusSource.EXAMS, WebPolicy.VPN_URL, empty.toString()) }.isFailure)
        assertTrue(runCatching { read(CampusSource.EXAMS, empty.put("scope", "")) }.isFailure)
    }
    @Test fun multipleCoursesAndUnfixedCoursesArePreserved() {
        val cells = JSONArray().put(JSONObject().put("day", 6).put("text",
            "示例课程A\n(1-2节)1周,5-7周(单)\n示例教室A\n示例教师A\n示例课程B\n(3-4节)8-10周(双)\n示例教室B\n示例教师B"))
        val root = JSONObject().put("status", "ok").put("scope", "2026-2027学年第1学期")
            .put("cells", cells).put("unfixed", JSONArray().put("实践课程：示例实践/2-3周/无"))
        val result = read(CampusSource.COURSES, root)
        assertEquals(3, result.rows.size)
        assertTrue(result.rows[0].title.startsWith("星期六"))
        assertEquals(result.rows, CaptureReader.decode(result.payload))
    }
    @Test fun unknownCourseRuleRejectsWholeReplacement() {
        val root = JSONObject().put("status", "ok").put("scope", "2026-2027学年第1学期")
            .put("cells", JSONArray().put(JSONObject().put("day", 1).put("text",
                "示例课程\n(1-2节)1-3周\n示例教室\n示例教师\n另一示例课程\n(5~6节)4周")))
        assertTrue(runCatching { read(CampusSource.COURSES, root) }.isFailure)
    }
    @Test fun deceptiveHostsAndUnsafeSchemesAreBlocked() {
        assertTrue(WebPolicy.isSchoolUrl(WebPolicy.VPN_URL))
        listOf("http://webvpn.fjut.edu.cn/", "https://webvpn.fjut.edu.cn.evil.example/",
            "https://user@webvpn.fjut.edu.cn/", "javascript:alert(1)", "file:///sdcard/a.html",
            "https://webvpn.fjut.edu.cn:444/").forEach { assertFalse(it, WebPolicy.isSchoolUrl(it)) }
    }
}
