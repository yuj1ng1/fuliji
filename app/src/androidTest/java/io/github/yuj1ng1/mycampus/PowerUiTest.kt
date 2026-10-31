package io.github.yuj1ng1.mycampus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.SavedSnapshot
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.io.File

@RunWith(AndroidJUnit4::class)
class PowerUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun newerUsageForAnotherRoomNeverShowsPreviousRoomsBalance() {
        val balance = SavedSnapshot("power-a", "POWER", "合成示例宿舍A", CaptureReader.encode(listOf(DisplayRow("A", "123.45 度"))), 1, 1)
        val history = SavedSnapshot("usage-b", "POWER_USAGE", "合成示例宿舍B", CaptureReader.encode(listOf(DisplayRow("2026-10-29", "2.50"))), 1, 2)
        ui.setContent { CampusTheme { CampusDashboard(LocalDate.of(2026, 10, 30), listOf(history, balance), emptyList(), {}, {}, {}, {}, {}, {}, {}) } }
        ui.onNodeWithText("合成示例宿舍B").assertExists()
        ui.onNodeWithText("123.45").assertDoesNotExist()
        ui.onNodeWithContentDescription("近7天学校日用量，", substring = true).assertExists()
    }

    @Test fun chartPreservesZeroAndDescribesMissingDays() {
        val history = SavedSnapshot("usage", "POWER_USAGE", "合成示例宿舍", CaptureReader.encode(listOf(
            DisplayRow("2026-10-29", "0"), DisplayRow("2026-10-27", "2.50"))), 2, 2)
        ui.setContent { CampusTheme { PowerUsageCard(null, history, LocalDate.of(2026, 10, 30), history.scope) } }
        ui.onNodeWithContentDescription("2026-10-29：0", substring = true).assertExists()
        ui.onNodeWithContentDescription("2026-10-28：无记录", substring = true).assertExists()
        ui.onNodeWithText("已记录 2/7 天").assertExists()
    }

    @Test fun selectingAChartDayShowsItsExactSourceValue() {
        // Only the test APK creates this fixture; it is never written to the app database.
        val today = LocalDate.of(2026, 10, 30)
        val values = listOf("4.20", "6.10", "3.80", "8.40", "5.60", "7.30", "4.90")
        val history = SavedSnapshot("usage-demo", "POWER_USAGE", "示例宿舍 · 合成数据", CaptureReader.encode(
            values.mapIndexed { index, value -> DisplayRow(today.minusDays(7L - index).toString(), value) }), 7, 2)
        val balance = SavedSnapshot("power-demo", "POWER", history.scope, CaptureReader.encode(listOf(DisplayRow(history.scope, "42.80 度"))), 1, 1)
        ui.setContent {
            CampusTheme {
                Scaffold(topBar = { CampusHeader("福理集", "界面演示 · 下列均为合成数据", {}) }, bottomBar = { CampusBottomBar(0) {} }) { padding ->
                    Box(Modifier.padding(padding)) {
                        CampusDashboard(today, listOf(history, balance), emptyList(), {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        ui.onNodeWithText("10.29 · 4.90").assertExists()
        ui.onNodeWithContentDescription("近7天学校日用量，", substring = true).performTouchInput { click(center) }
        ui.onNodeWithText("10.26 · 8.40").assertExists()
        ui.onNodeWithText("42.80").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "synthetic-dashboard.png").outputStream().use { stream ->
            ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
