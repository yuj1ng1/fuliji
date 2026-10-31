package io.github.yuj1ng1.mycampus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yuj1ng1.mycampus.notifications.PaymentCaptureSnapshot
import io.github.yuj1ng1.mycampus.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationStatusUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun grantedButDisconnectedOffersReconnectWithoutPretendingToBeConnected() {
        var requests = 0
        ui.setContent { CampusTheme { PaymentNotificationStatusCard(true, PaymentCaptureSnapshot(), { requests++ }, {}) } }
        ui.onNodeWithText("已授权 · 监听尚未连接").assertIsDisplayed()
        ui.onNodeWithTag("notification-reconnect").performClick()
        assertEquals(1, requests)
    }

    @Test fun missingAccessOnlyOffersUserControlledSystemSettings() {
        var settings = 0
        ui.setContent { CampusTheme { PaymentNotificationStatusCard(false, PaymentCaptureSnapshot(), {}, { settings++ }) } }
        ui.onNodeWithText("尚未开启通知访问").assertIsDisplayed()
        ui.onNodeWithTag("notification-reconnect").assertDoesNotExist()
        ui.onNodeWithTag("notification-settings").performClick()
        assertEquals(1, settings)
    }

    @Test fun connectedReaderReportsSavedDraftAndActiveScanSeparately() {
        val state = PaymentCaptureSnapshot(connected = true, lastSource = "支付宝", lastReceivedAt = 1,
            lastReason = "SAVED", lastScanAt = 1, scannedCount = 2)
        ui.setContent { CampusTheme { PaymentNotificationStatusCard(true, state, {}, {}) } }
        ui.onNodeWithText("已连接 · 识别结果进入待确认").assertIsDisplayed()
        ui.onNodeWithText("最近处理：已保存为待确认，核对后才计入支出").assertIsDisplayed()
        ui.onNodeWithText("现有通知检查：", substring = true).assertIsDisplayed()
    }
}
