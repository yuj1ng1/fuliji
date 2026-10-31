package io.github.yuj1ng1.mycampus.notifications

import android.service.notification.StatusBarNotification
import io.github.yuj1ng1.mycampus.data.CampusRepository
import io.github.yuj1ng1.mycampus.domain.PaymentCandidates
import io.github.yuj1ng1.mycampus.domain.PaymentParseReason
import kotlinx.coroutines.*

class PaymentNotificationCapture(private val repository: CampusRepository, private val status: PaymentCaptureStatus, private val scope: CoroutineScope) {
    fun receive(item: StatusBarNotification): Job? {
        status.callback()
        val reading = try { NotificationPaymentReader.read(item) } catch (_: Exception) {
            PaymentCandidates.sourceName(item.packageName)?.let { status.observed(it, PaymentParseReason.INVALID_EVENT) }
            return null
        }
        val source = reading.source ?: return null
        val token = status.observed(source, reading.recognition.reason)
        val candidate = reading.recognition.candidate ?: return null
        // Only the parsed candidate crosses to the writer; no Bundle, title, or original text is retained.
        return scope.launch {
            try { status.completed(token, repository.saveCandidateIfNew(candidate), candidate.amountCents == null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status.writeFailed(token) }
        }
    }
}
