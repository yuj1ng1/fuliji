package io.github.yuj1ng1.mycampus

import android.app.Notification
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.notifications.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentCaptureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "synthetic-notification-status"
    private lateinit var database: CampusDatabase
    private lateinit var repository: CampusRepository
    private lateinit var status: PaymentCaptureStatus
    private lateinit var scope: CoroutineScope
    private lateinit var capture: PaymentNotificationCapture

    @Before fun setup() {
        context.deleteSharedPreferences(namespace)
        database = Room.inMemoryDatabaseBuilder(context, CampusDatabase::class.java).build()
        repository = CampusRepository(database)
        status = PaymentCaptureStatus(context, namespace)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        capture = PaymentNotificationCapture(repository, status, scope)
    }
    @After fun close() { scope.cancel(); database.close(); context.deleteSharedPreferences(namespace) }

    @Test fun notificationTextAndBigTextAndInboxLinesUseTheProductionReader() {
        val notices = listOf(
            syntheticPayment(),
            syntheticPayment(text = "", extras = { it.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "你有一笔18.60元的支出，合成优惠3元。") }),
            syntheticPayment(text = "", extras = { it.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("你有一笔18.60元的支出，", "合成优惠3元。")) }),
            syntheticPayment(extras = { it.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, " ") }),
        )
        for (notice in notices) {
            val read = NotificationPaymentReader.read(notice)
            assertEquals(1860L, read.recognition.candidate?.amountCents)
            assertEquals("支付宝", read.source)
        }
    }

    @Test fun summaryAndUntrustedAppAndEmptyBodyCannotCreateEntries() = runBlocking {
        val notices = listOf(syntheticPayment(summary = true), syntheticPayment(packageName = "synthetic.other"), syntheticPayment(text = ""))
        for (notice in notices) assertNull(capture.receive(notice))
        assertTrue(repository.ledger.first().isEmpty())
        assertEquals("EMPTY_CONTENT", status.state.value.lastReason)
    }

    @Test fun fullFailureVetoesAbbreviatedSuccessAndConflictingAmountsStayUnknown() {
        val failed = syntheticPayment(extras = { it.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "交易失败18.60元") })
        assertEquals(PaymentParseReason.INCOMPLETE, NotificationPaymentReader.read(failed).recognition.reason)
        val conflict = syntheticPayment(extras = { it.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "你有一笔5.40元的支出。") })
        val result = NotificationPaymentReader.read(conflict).recognition
        assertEquals(PaymentParseReason.AMOUNT_REVIEW, result.reason)
        assertNotNull(result.candidate)
        assertNull(result.candidate!!.amountCents)
        val kindConflict = syntheticPayment(extras = { it.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "退款成功5.40元") })
        assertEquals(PaymentParseReason.CONFLICTING_CONTENT, NotificationPaymentReader.read(kindConflict).recognition.reason)
    }

    @Test fun captureWritesDraftThenReplayPreservesTheUsersConfirmation() = runBlocking {
        val notice = syntheticPayment()
        requireNotNull(capture.receive(notice)).join()
        val draft = repository.ledger.first().single()
        assertEquals(1860L, draft.amountCents)
        assertEquals("DRAFT", draft.status)
        assertEquals("NOTIFICATION", draft.timeBasis)
        assertEquals(notice.postTime, draft.occurredAt)
        assertEquals("", draft.note)
        assertEquals("SAVED", status.state.value.lastReason)
        repository.confirmExpense(draft.id, 1900, "合成本人修正")
        requireNotNull(capture.receive(notice)).join()
        val kept = repository.ledger.first().single()
        assertEquals("CONFIRMED", kept.status)
        assertEquals(1900L, kept.amountCents)
        assertEquals("合成本人修正", kept.note)
        assertEquals(1L, status.state.value.savedCount)
        assertEquals("DUPLICATE", status.state.value.lastReason)
    }

    @Test fun diagnosticPersistenceContainsNoPayloadOrAmountAndNoStaleConnection() = runBlocking {
        val notice = syntheticPayment(text = "你有一笔18.60元的支出，合成商户标记勿存3元。")
        requireNotNull(capture.receive(notice)).join()
        status.connection(true)
        val metadata = context.getSharedPreferences(namespace, Context.MODE_PRIVATE).all
        assertEquals(setOf("source", "reason", "received_at", "saved_count", "scan_at", "scan_count"), metadata.keys)
        for (value in metadata.values.filterIsInstance<String>()) {
            assertFalse(value.contains("18.60"))
            assertFalse(value.contains("交易提醒"))
            assertFalse(value.contains("商户标记"))
            assertFalse(value.contains(notice.key))
        }
        val restored = PaymentCaptureStatus(context, namespace).state.value
        assertFalse(restored.connected)
        assertEquals(0L, restored.requestedAt)
        assertEquals(0L, restored.callbacks)
        assertEquals("SAVED", restored.lastReason)
        assertEquals("支付宝", restored.lastSource)
    }

    @Test fun databaseWriteFailureHasADistinctSafeDiagnostic() = runBlocking {
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER synthetic_reject BEFORE INSERT ON ledger_entries BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END",
            )
        }
        requireNotNull(capture.receive(syntheticPayment())).join()
        assertEquals("WRITE_ERROR", status.state.value.lastReason)
        assertEquals(0L, status.state.value.savedCount)
    }
}
