package io.github.yuj1ng1.mycampus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun homeAndCampusNavigationWorkWithoutSchoolLogin() {
        ui.onNodeWithTag("home").assertIsDisplayed()
        ui.onNodeWithText("福理集").assertIsDisplayed()
        ui.onNodeWithTag("tab-2").performClick()
        ui.onNodeWithText("考试安排").assertIsDisplayed()
        ui.onNodeWithText("成绩查询").assertIsDisplayed()
        ui.onNodeWithText("学年校历").assertIsDisplayed()
        ui.onNodeWithText("学年校历").performClick()
        ui.onNodeWithContentDescription("福建理工大学2026—2027学年官方校历").assertExists()
        ui.onNodeWithText("返回").performClick()
    }

    @Test fun invalidMoneyCannotBeSavedAndEntryCanBeCancelled() {
        ui.onNodeWithTag("tab-3").performClick()
        ui.onNodeWithText("本月支出").assertIsDisplayed()
        ui.onNodeWithText("近7天消费").assertIsDisplayed()
        ui.onNodeWithTag("add-expense").performClick()
        ui.onNodeWithTag("expense-amount").performTextInput("1.001")
        ui.onNodeWithTag("save-expense").assertIsNotEnabled()
        ui.onNodeWithTag("expense-amount").performTextClearance()
        ui.onNodeWithTag("expense-amount").performTextInput("1.23")
        ui.onNodeWithTag("save-expense").assertIsEnabled()
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithTag("ledger").assertIsDisplayed()
    }
}
