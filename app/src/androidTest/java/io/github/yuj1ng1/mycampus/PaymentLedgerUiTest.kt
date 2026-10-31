package io.github.yuj1ng1.mycampus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.monthlyExpenseTotal
import io.github.yuj1ng1.mycampus.domain.SchoolCalendar
import io.github.yuj1ng1.mycampus.notifications.NotificationPaymentReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PaymentLedgerUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun syntheticNoticeCanBeReviewedAndConfirmedInTheActualLedger(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication
        val notice = syntheticPayment(time = System.currentTimeMillis(), id = 7160)
        val id = requireNotNull(NotificationPaymentReader.read(notice).recognition.candidate).eventId
        val prior = app.repository.ledger.first()
        val sumBefore = prior.filter { it.status == "CONFIRMED" && it.kind == "EXPENSE" }.sumOf { it.amountCents ?: 0 }
        val monthBefore = monthlyExpenseTotal(prior, LocalDate.now(SchoolCalendar.zone))
        try {
            requireNotNull(app.paymentCapture.receive(notice)).join()
            ui.onNodeWithTag("tab-3").performClick()
            ui.waitUntil(5_000) { ui.onAllNodesWithTag("review-latest-payment").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("month-spending").assertTextEquals("¥ $monthBefore")
            // A recognized notice is a draft; the monthly summary does not count it yet.
            val before = app.repository.ledger.first().single { it.id == id }
            assertEquals("DRAFT", before.status)
            assertEquals(sumBefore, app.repository.ledger.first().filter { it.status == "CONFIRMED" && it.kind == "EXPENSE" }.sumOf { it.amountCents ?: 0 })
            ui.onNodeWithTag("review-latest-payment").performScrollTo().performClick()
            ui.onNodeWithTag("expense-amount").assertTextContains("18.60")
            ui.onNodeWithTag("save-expense").performClick()
            ui.waitUntil(5_000) {
                runBlocking { app.repository.ledger.first().single { it.id == id }.status == "CONFIRMED" }
            }
            val after = app.repository.ledger.first().single { it.id == id }
            assertEquals(1860L, after.amountCents)
            assertEquals(notice.postTime, after.occurredAt)
            assertEquals(sumBefore + 1860, app.repository.ledger.first().filter { it.status == "CONFIRMED" && it.kind == "EXPENSE" }.sumOf { it.amountCents ?: 0 })
            ui.onNodeWithTag("month-spending").performScrollTo()
                .assertTextEquals("¥ " + BigDecimal(monthBefore).add(BigDecimal("18.60")).setScale(2).toPlainString())
        } finally { app.repository.deleteEntry(id) }
    }
}
