package io.github.yuj1ng1.mycampus.domain

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

data class DisplayRow(val title: String, val detail: String)
data class Capture(val scope: String, val rows: List<DisplayRow>, val payload: String, val empty: Boolean)

class CaptureException(val userMessage: String, val state: String = "UNRECOGNIZED") : Exception(userMessage)

object CaptureReader {
    fun read(source: CampusSource, actualUrl: String, raw: String): Capture {
        if (!source.matchesResultUrl(actualUrl)) throw CaptureException("请先进入" + source.label + "查询页面，再读取结果。", "NEEDS_LOGIN")
        if (raw.length > 500_000) throw CaptureException("结果过大，请缩小查询范围。")
        val root = runCatching { JSONObject(raw) }.getOrElse { throw CaptureException("暂时无法识别页面结果，请重试。") }
        when (root.optString("status")) {
            "login" -> throw CaptureException("请先在学校页面完成登录。", "NEEDS_LOGIN")
            "loading" -> throw CaptureException("学校页面仍在加载，请查询完成后再读取。", "NOT_READY")
            "not_queried" -> throw CaptureException("请先选择条件并点击学校页面的查询。", "NOT_READY")
            "partial" -> throw CaptureException("当前结果有多页，请在原页面增大每页条数后重新读取。", "PARTIAL")
            "ok", "empty" -> Unit
            else -> throw CaptureException("尚未识别到完整结果；原有数据已保留。")
        }
        val scope = root.optString("scope").trim()
        if (scope.isBlank() || scope.length > 200) throw CaptureException("无法确认本次查询的学期或房间，暂不保存。")
        if (source == CampusSource.POWER) return readPower(root, scope)
        if (source == CampusSource.POWER_USAGE) return readUsage(root, scope)
        if (root.optString("status") == "empty") {
            if (source == CampusSource.COURSES) throw CaptureException("尚不能确认课表为空，请核对原页面。")
            return Capture(scope, emptyList(), "[]", true)
        }
        val rows = if (source == CampusSource.COURSES) readCourses(root) else readGrid(source, root)
        if (rows.isEmpty()) throw CaptureException("页面没有可确认的完整记录，暂不保存。")
        return Capture(scope, rows, encode(rows), false)
    }

    private fun readPower(root: JSONObject, room: String): Capture {
        val rawAmount = root.optString("remaining").trim()
        val match = Regex("""^(?:剩余(?:电)?量\s*[:：]?\s*)?(-?\d+(?:\.\d+)?)\s*(?:度|kWh)$""", RegexOption.IGNORE_CASE)
            .matchEntire(rawAmount) ?: throw CaptureException("没有读取到带单位的有效电量，请先选择房间并查询。", "NOT_READY")
        val amount = runCatching { BigDecimal(match.groupValues[1]) }.getOrNull()
            ?: throw CaptureException("电量格式暂时无法识别。")
        val rows = listOf(DisplayRow(room, amount.toPlainString() + " 度"))
        return Capture(room, rows, encode(rows), false)
    }

    private fun readUsage(root: JSONObject, room: String): Capture {
        val array = root.optJSONArray("usage") ?: throw CaptureException("请在“房间用量”中选择房间，查询后再读取。", "NOT_READY")
        if (array.length() !in 1..2000) throw CaptureException("尚未找到有效的日用量明细，原有数据已保留。")
        val pattern = Regex("""^(\d{4}-\d{2}-\d{2})\s+(\d{1,10}(?:\.\d{1,8})?)$""")
        val dates = mutableSetOf<LocalDate>()
        val rows = (0 until array.length()).map { index ->
            val match = pattern.matchEntire(array.getString(index).trim())
                ?: throw CaptureException("有用量行无法识别，暂不覆盖原记录。")
            val date = runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()
                ?: throw CaptureException("有用量日期无法识别，暂不覆盖原记录。")
            if (!dates.add(date)) throw CaptureException("用量表存在重复日期，暂不覆盖原记录。")
            DisplayRow(date.toString(), BigDecimal(match.groupValues[2]).toPlainString())
        }.sortedBy { it.title }
        return Capture(room, rows, encode(rows), false)
    }

    private fun readCourses(root: JSONObject): List<DisplayRow> {
        root.optJSONArray("courses")?.let { courses ->
            if (courses.length() !in 1..300 || root.optJSONObject("diagnostics")?.optInt("courseBlocks") != courses.length()) {
                throw CaptureException("课程记录不完整，暂不覆盖原课表。")
            }
            val rows = (0 until courses.length()).map { index ->
                val course = courses.getJSONObject(index)
                val day = course.optInt("day")
                val name = cleanCourseLine(course.optString("name"))
                val rawRule = cleanCourseLine(course.optString("rule"))
                if (day !in 1..7 || name.length !in 1..150) throw CaptureException("有课程名称或星期无法确认，暂不覆盖原课表。")
                validateCourseRule(rawRule)
                val details = mutableListOf(rawRule)
                course.optJSONArray("details")?.let { fields ->
                    for (field in 0 until fields.length()) cleanCourseLine(fields.getString(field)).takeIf(String::isNotBlank)?.let(details::add)
                }
                DisplayRow("星期" + "一二三四五六日"[day - 1] + " · " + name, details.joinToString("\n"))
            }.toMutableList()
            appendUnfixed(root, rows)
            return rows
        }
        val cells = root.optJSONArray("cells") ?: throw CaptureException("没有找到课表。")
        if (cells.length() > 200) throw CaptureException("课表结构暂时无法识别。")
        val rows = mutableListOf<DisplayRow>()
        val rule = Regex("""^\((\d{1,2})(?:-(\d{1,2}))?节\)(.+)$""")
        for (i in 0 until cells.length()) {
            val cell = cells.getJSONObject(i)
            val day = cell.optInt("day")
            if (day !in 1..7) throw CaptureException("无法确认课程的星期。")
            val lines = cell.optString("text").replace(Regex("[\\uE000-\\uF8FF]"), "")
                .lines().map(String::trim).filter(String::isNotBlank)
            if (lines.any { "节)" in it.replace("）", ")") && !rule.matches(it.replace("（", "(").replace("）", ")")) }) {
                throw CaptureException("有课程时间规则无法识别，暂不覆盖原课表。")
            }
            var identified = 0
            lines.forEachIndexed { index, line ->
                val match = rule.matchEntire(line.replace("（", "(").replace("）", ")")) ?: return@forEachIndexed
                val name = lines.getOrNull(index - 1)?.takeIf { it.length in 1..150 }
                    ?: throw CaptureException("有课程名称缺失，暂不覆盖原课表。")
                val sections = match.groupValues[1].toInt()..match.groupValues[2].ifBlank { match.groupValues[1] }.toInt()
                if (sections.first !in 1..24 || sections.last !in sections.first..24) throw CaptureException("有课程节次无法识别。")
                runCatching { WeekRules.parse(match.groupValues[3]) }.getOrElse { throw CaptureException("有课程周次无法识别，暂不覆盖原课表。") }
                val following = lines.drop(index + 1).takeWhile { !rule.matches(it) }.take(2)
                rows += DisplayRow("星期" + "一二三四五六日"[day - 1] + " · " + name, (listOf(line) + following).joinToString("\n"))
                identified++
            }
            if (lines.isNotEmpty() && identified == 0) throw CaptureException("有课程单元格无法识别，暂不覆盖原课表。")
        }
        appendUnfixed(root, rows)
        return rows
    }

    private fun cleanCourseLine(raw: String) = raw.replace(Regex("[\\uE000-\\uF8FF]"), "").trim()
        .replace("（", "(").replace("）", ")")

    private fun validateCourseRule(raw: String) {
        val match = Regex("""^\((\d{1,2})(?:-(\d{1,2}))?节\)(.+)$""").matchEntire(raw)
            ?: throw CaptureException("有课程时间规则无法识别，暂不覆盖原课表。")
        val start = match.groupValues[1].toInt()
        val end = match.groupValues[2].ifBlank { match.groupValues[1] }.toInt()
        if (start !in 1..24 || end !in start..24) throw CaptureException("有课程节次无法识别。")
        runCatching { WeekRules.parse(match.groupValues[3]) }.getOrElse { throw CaptureException("有课程周次无法识别，暂不覆盖原课表。") }
    }

    private fun appendUnfixed(root: JSONObject, rows: MutableList<DisplayRow>) {
        root.optJSONArray("unfixed")?.let { array ->
            for (i in 0 until array.length()) {
                val detail = array.getString(i).trim()
                if (detail.isNotBlank()) rows += DisplayRow("实践与其他课程", detail.take(3000))
            }
        }
    }

    private fun readGrid(source: CampusSource, root: JSONObject): List<DisplayRow> {
        val array = root.optJSONArray("rows") ?: throw CaptureException("没有读取到查询列表。")
        if (array.length() > 1500) throw CaptureException("结果过多，请缩小查询范围。")
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val name = item.optString("kcmc").trim()
            if (name.isBlank() || name.length > 200) throw CaptureException("列表包含无法识别的课程，暂不覆盖原数据。")
            val fields = if (source == CampusSource.EXAMS) listOf(
                "kssj" to "时间", "cdmc" to "地点", "cdxqmc" to "校区", "zwh" to "座号", "ksmc" to "考试",
            ) else listOf("cj" to "成绩", "xf" to "学分", "jd" to "来源绩点", "ksxz" to "成绩性质", "cjbz" to "备注")
            val detail = fields.mapNotNull { (key, label) ->
                item.optString(key).trim().takeIf(String::isNotEmpty)?.take(500)?.let { label + "：" + it }
            }.joinToString("\n")
            if (detail.isBlank()) throw CaptureException("列表缺少有效业务字段，暂不覆盖原数据。")
            DisplayRow(name, detail)
        }
    }

    fun encode(rows: List<DisplayRow>): String = JSONArray().apply {
        rows.forEach { put(JSONObject().put("title", it.title).put("detail", it.detail)) }
    }.toString()

    fun decode(raw: String): List<DisplayRow> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { array.getJSONObject(it).let { row -> DisplayRow(row.getString("title"), row.getString("detail")) } }
    }.getOrDefault(emptyList())
}
