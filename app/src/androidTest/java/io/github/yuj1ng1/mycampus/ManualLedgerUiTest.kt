package io.github.yuj1ng1.mycampus

import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.SchoolCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ManualLedgerUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication

    @Test fun manualExpenseIsSavedAndUpdatesTheMonthlySummaryWithoutNotificationAccess(): Unit = runBlocking {
        val note = "合成手动验证-" + UUID.randomUUID().toString().take(8)
        val before = monthlyExpenseTotal(app.repository.ledger.first(), LocalDate.now(SchoolCalendar.zone))
        try {
            ui.onNodeWithTag("tab-3").performClick()
            ui.onNodeWithText("支付通知").assertDoesNotExist()
            ui.onNodeWithTag("notification-settings").assertDoesNotExist()
            ui.onNodeWithTag("manual-add-expense").performScrollTo().performClick()
            ui.onNodeWithTag("expense-amount").performTextInput("6.30")
            ui.onNodeWithTag("expense-note").performTextInput(note)
            ui.onNodeWithTag("save-expense").performClick()
            ui.waitUntil(5_000) { runBlocking { app.repository.ledger.first().any { it.note == note } } }
            val saved = app.repository.ledger.first().single { it.note == note }
            assertEquals(630L, saved.amountCents)
            assertEquals("USER", saved.timeBasis)
            assertEquals("CONFIRMED", saved.status)
            ui.onNodeWithTag("month-spending").performScrollTo().assertTextEquals(
                "¥ " + BigDecimal(before).add(BigDecimal("6.30")).setScale(2).toPlainString(),
            )
            @Suppress("DEPRECATION")
            val services = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SERVICES).services.orEmpty()
            assertFalse(services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" })
        } finally { app.repository.ledger.first().filter { it.note == note }.forEach { app.repository.deleteEntry(it.id) } }
    }

    @Test fun oldDraftIsKeptForManualReviewAndRetainsItsDate(): Unit = runBlocking {
        val id = "synthetic-legacy-ui"
        val time = System.currentTimeMillis()
        app.database.dao().insertEntry(LedgerEntry(id, "旧版合成", "EXPENSE", 540, time, "NOTIFICATION", "", "DRAFT"))
        try {
            ui.onNodeWithTag("tab-3").performClick()
            ui.onNodeWithTag("review-legacy-entry").performScrollTo().performClick()
            ui.onNodeWithTag("expense-amount").performTextClearance()
            ui.onNodeWithTag("expense-amount").performTextInput("6.00")
            ui.onNodeWithTag("save-expense").performClick()
            ui.waitUntil(5_000) { runBlocking { app.repository.ledger.first().single { it.id == id }.status == "CONFIRMED" } }
            val saved = app.repository.ledger.first().single { it.id == id }
            assertEquals(600L, saved.amountCents)
            assertEquals(time, saved.occurredAt)
        } finally { app.repository.deleteEntry(id) }
    }
}
