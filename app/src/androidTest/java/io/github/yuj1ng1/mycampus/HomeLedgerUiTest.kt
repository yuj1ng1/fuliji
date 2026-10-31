package io.github.yuj1ng1.mycampus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class HomeLedgerUiTest {
    @get:Rule val ui = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val monday = LocalDate.of(2026, 10, 26)
    private val rows = listOf(
        DisplayRow("星期一 · 示例课程A", "(1-2节)1-16周\n示例教学楼101\n示例教师A"),
        DisplayRow("星期一 · 示例课程B", "(3-4节)1-16周\n示例教学楼202\n示例教师B"),
        DisplayRow("星期一 · 示例实验课", "(5-6节)1-16周\n示例实验楼303\n示例教师C"),
        DisplayRow("星期一 · 示例课程D", "(7-8节)1-16周\n示例教学楼404\n示例教师D"),
    )
    private val snapshot = SavedSnapshot("synthetic-home-agenda", "COURSES", "2026-2027学年第1学期", CaptureReader.encode(rows), rows.size, 1)

    private fun save(node: SemanticsNodeInteraction, name: String) {
        File(context.cacheDir, name).outputStream().use { node.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun preview(mode: HomeCourseMode) {
        val room = "示例宿舍 · 合成数据"
        val captured = monday.atTime(12, 30).atZone(SchoolCalendar.zone).toInstant().toEpochMilli()
        val balance = SavedSnapshot("synthetic-home-balance", "POWER", room, CaptureReader.encode(listOf(DisplayRow(room, "42.80 度"))), 1, captured)
        val usageRows = (1L..7L).map { DisplayRow(monday.minusDays(it).toString(), (3 + it).toString()) }
        val usage = SavedSnapshot("synthetic-home-usage", "POWER_USAGE", room, CaptureReader.encode(usageRows), 7, captured)
        ui.setContent { CampusTheme {
            Scaffold(topBar = { CampusHeader("福理集", "首页对比 · 下列为合成数据", {}) }, bottomBar = { CampusBottomBar(0) {} }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    CampusDashboard(monday, listOf(snapshot, balance, usage), ledger(monday), {}, {}, {}, {}, {}, {}, {},
                        now = LocalTime.of(13, 10), homeCourseMode = mode)
                }
            }
        } }
        ui.onNodeWithText("示例实验课").assertIsDisplayed()
        if (mode == HomeCourseMode.TODAY) {
            ui.onNodeWithText("示例课程A").assertIsDisplayed()
            ui.onNodeWithText("示例课程D").assertIsDisplayed()
        } else {
            ui.onNodeWithText("示例课程A").assertDoesNotExist()
            ui.onNodeWithText("50分钟后开始").assertIsDisplayed()
        }
        val key = if (mode == HomeCourseMode.TODAY) "today" else "next"
        save(ui.onRoot(), "synthetic-home-$key.png")
        save(ui.onNodeWithTag("home-course-card"), "synthetic-home-$key-card.png")
    }

    @Test fun todayHomePreviewShowsAllFourCourses() = preview(HomeCourseMode.TODAY)
    @Test fun nextHomePreviewShowsOnlyNearestUnstartedCourse() = preview(HomeCourseMode.NEXT)

    @Test fun homeChoiceIsInteractiveAndSurvivesPreferencesRecreation() {
        val namespace = "synthetic-home-ui-options"
        val preferences = UiPreferences(context, namespace).apply { chooseHomeCourseMode(HomeCourseMode.TODAY) }
        try {
            ui.setContent { CampusTheme {
                val mode by preferences.homeCourseMode.collectAsState()
                Box(Modifier.padding(20.dp)) { HomeCourseCard(snapshot, monday, LocalTime.of(13, 10), mode, preferences::chooseHomeCourseMode, {}, {}) }
            } }
            ui.onNodeWithTag("home-mode-next").performClick().assertIsSelected()
            ui.onNodeWithText("示例课程A").assertDoesNotExist()
            assertEquals(HomeCourseMode.NEXT, UiPreferences(context, namespace).homeCourseMode.value)
            ui.onNodeWithTag("home-mode-today").performClick().assertIsSelected()
            ui.onNodeWithText("示例课程A").assertExists()
        } finally { context.deleteSharedPreferences(namespace) }
    }

    private fun entry(id: String, date: LocalDate, cents: Long, status: String = "CONFIRMED", kind: String = "EXPENSE") =
        LedgerEntry(id, "合成样例", kind, cents, date.atTime(12, 0).atZone(SchoolCalendar.zone).toInstant().toEpochMilli(), "USER", "合成消费", status)

    private fun ledger(today: LocalDate): List<LedgerEntry> {
        val values = listOf(1250L, 0L, 1800L, 820L, 2250L, 3680L, 1540L)
        return values.mapIndexedNotNull { index, cents -> if (cents == 0L) null else entry("day-$index", today.minusDays(6L - index), cents) } +
            listOf(entry("earlier-month", today.withDayOfMonth(1), 8860), entry("draft", today, 99999, "DRAFT"), entry("refund", today, 1000, kind = "REFUND"))
    }

    @Test fun ledgerPreviewShowsMonthlyTotalAndSelectableSevenDayValues() {
        val today = LocalDate.of(2026, 10, 31)
        ui.setContent { CampusTheme {
            Scaffold(topBar = { CampusHeader("账本", "以下均为合成消费数据", {}) }, bottomBar = { CampusBottomBar(3) {} }) { padding ->
                Box(Modifier.padding(padding).padding(20.dp)) { LedgerSummaryCard(ledger(today), today) }
            }
        } }
        ui.onNodeWithTag("month-spending").assertTextEquals("¥ 202.00")
        ui.onNodeWithContentDescription("2026-10-26：¥0.00").assertExists()
        ui.onNodeWithTag("selected-spending-day").assertTextEquals("10.31 · ¥ 15.40")
        save(ui.onRoot(), "synthetic-ledger-summary.png")
        ui.onNodeWithTag("spend-day-2").performClick()
        ui.onNodeWithTag("selected-spending-day").assertTextEquals("10.27 · ¥ 18.00")
    }

    @Test fun confirmingOrDeletingEntriesChangesSummaryWithoutCountingDrafts() {
        val today = LocalDate.of(2026, 10, 31)
        var entries by mutableStateOf(listOf(entry("confirmed", today, 1000), entry("pending", today, 550, "DRAFT")))
        ui.setContent { CampusTheme { LedgerSummaryCard(entries, today) } }
        ui.onNodeWithTag("month-spending").assertTextEquals("¥ 10.00")
        ui.runOnIdle { entries = entries.map { if (it.id == "pending") it.copy(status = "CONFIRMED") else it } }
        ui.onNodeWithTag("month-spending").assertTextEquals("¥ 15.50")
        ui.runOnIdle { entries = entries.filterNot { it.id == "confirmed" } }
        ui.onNodeWithTag("month-spending").assertTextEquals("¥ 5.50")
    }
}
