package io.github.yuj1ng1.mycampus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.SavedSnapshot
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.ui.*
import org.junit.Rule
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
            Scaffold(topBar = { CampusHeader("课表", "界面演示 · 以下均为合成数据", {}) }, bottomBar = { CampusBottomBar(1) {} }) { padding ->
                CourseWeekView(snapshot, today, Modifier.fillMaxSize().padding(padding))
            }
        } }
        ui.onNodeWithText("第8周").assertIsDisplayed()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "synthetic-week-table.png").outputStream().use { stream ->
            ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
