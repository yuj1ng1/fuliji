package io.github.yuj1ng1.mycampus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.SavedSnapshot
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.ui.*
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CourseWeekUiTest {
    @get:Rule val ui = createComposeRule()
    private val today = LocalDate.of(2026, 10, 31)
    private val rows = listOf(
        DisplayRow("星期一 · 合成课程甲", "(1-2节)1-16周\n示例教学楼A101\n示例教师甲"),
        DisplayRow("星期二 · 合成课程乙", "(3-4节)1-16周\n示例教学楼B202\n示例教师乙"),
        DisplayRow("星期三 · 奇数周课程", "(5-6节)1-15周(单)\n示例实验室"),
        DisplayRow("星期三 · 偶数周课程", "(5-6节)2-16周(双)\n示例实验室"),
        DisplayRow("星期五 · 合成课程丙", "(1-2节)1-16周\n示例教学楼C303"),
        DisplayRow("星期五 · 合成课程丁", "(7-8节)1-16周\n示例教学楼D404"),
        DisplayRow("实践与其他课程", "合成实践说明，未提供固定节次"))
    private val snapshot = SavedSnapshot("synthetic-week-table", "COURSES", "2026-2027学年第1学期", CaptureReader.encode(rows), rows.size, 1)

    @Test fun switchingWeekFiltersParityAndDetailsPreserveTeacherAndRoom() {
        ui.setContent { CampusTheme { CourseWeekView(snapshot, today, Modifier.fillMaxSize()) } }
        ui.onNodeWithText("第8周").assertIsDisplayed()
        ui.onNodeWithText("偶数周课程").assertExists()
        ui.onNodeWithText("奇数周课程").assertDoesNotExist()
        ui.onNodeWithTag("next-week").performClick()
        ui.onNodeWithText("第9周").assertExists()
        ui.onNodeWithText("奇数周课程").assertExists()
        ui.onNodeWithText("偶数周课程").assertDoesNotExist()
        ui.onNodeWithContentDescription("周一，1至2节，合成课程甲").performClick()
        ui.onNodeWithText("示例教师甲").assertIsDisplayed()
        ui.onNodeWithText("关闭").performClick()
        ui.onNodeWithTag("course-list-toggle").performClick()
        ui.onNodeWithText("星期一", substring = true).assertDoesNotExist()
        ui.onNodeWithText("奇数周课程").assertExists()
    }

    @Test fun syntheticWeekGridScreenshot() {
        ui.setContent { CampusTheme {
            Scaffold(topBar = { CampusHeader("课表 · 合成示例", "", {}, compact = true) }, bottomBar = { CampusBottomBar(1) {} }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { CourseScheduleScreen(listOf(fullDaySnapshot), emptyList(), today) }
            }
        } }
        ui.onNodeWithText("第8周").assertIsDisplayed()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "synthetic-week-table.png").outputStream().use { stream ->
            ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private val fullDayRows = (0..4).map { block ->
        DisplayRow("星期一 · 合成日间第${block + 1}段", "(${block * 2 + 1}-${block * 2 + 2}节)1-16周\n示例教室\n示例教师")
    }
    private val fullDaySnapshot = snapshot.copy(id = "synthetic-full-day", payload = CaptureReader.encode(fullDayRows), itemCount = fullDayRows.size)

    private fun verifyDayFits(fontScale: Float) {
        ui.setContent { CampusTheme {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                Box(Modifier.requiredSize(360.dp, 680.dp)) {
                    Scaffold(topBar = { CampusHeader("课表", "", {}, compact = true) }, bottomBar = { CampusBottomBar(1) {} }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) { CourseScheduleScreen(listOf(fullDaySnapshot), emptyList(), today) }
                    }
                }
            }
        } }
        val viewport = ui.onNodeWithTag("course-grid-viewport").getUnclippedBoundsInRoot()
        for (block in 0..3) {
            val node = ui.onNodeWithContentDescription("周一，${block * 2 + 1}至${block * 2 + 2}节，合成日间第${block + 1}段")
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("Daytime block must fit fully above navigation", bounds.top >= viewport.top - 1.dp && bounds.bottom <= viewport.bottom + 1.dp)
        }
        val evening = ui.onNodeWithContentDescription("周一，9至10节，合成日间第5段")
        assertTrue(evening.getUnclippedBoundsInRoot().top >= viewport.bottom - 1.dp)
        ui.onNodeWithTag("course-grid-viewport").performTouchInput { swipeUp() }
        evening.assertIsDisplayed()
        ui.onNodeWithTag("course-more").performClick()
        ui.onNodeWithText("更新此学期").assertIsDisplayed()
    }

    @Test fun firstEightSectionsFitIncludingRealHeaderAndNavigation() = verifyDayFits(1f)
    @Test fun largerSystemTextStillKeepsFourDaytimeBlocksOnFirstScreen() = verifyDayFits(1.25f)
}
