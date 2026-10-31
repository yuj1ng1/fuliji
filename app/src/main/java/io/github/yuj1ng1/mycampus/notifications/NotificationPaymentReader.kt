package io.github.yuj1ng1.mycampus.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.github.yuj1ng1.mycampus.domain.*

/** The result contains no notification text, title, key, or merchant. */
data class NotificationPaymentRead(val source: String?, val recognition: PaymentRecognition)

object NotificationPaymentReader {
    fun read(item: StatusBarNotification): NotificationPaymentRead {
        val source = PaymentCandidates.sourceName(item.packageName)
        fun result(reason: PaymentParseReason) = NotificationPaymentRead(source, PaymentRecognition(null, reason))
        if (source == null) return result(PaymentParseReason.UNSUPPORTED_SOURCE)
        val notification = item.notification ?: return result(PaymentParseReason.EMPTY_CONTENT)
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return result(PaymentParseReason.GROUP_SUMMARY)
        val extras = notification.extras ?: return result(PaymentParseReason.EMPTY_CONTENT)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf(String::isNotBlank)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
        if (!PaymentCandidates.acceptsTitle(item.packageName, title)) return result(PaymentParseReason.UNSUPPORTED_TITLE)
        val texts = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n"),
        ).filter(String::isNotBlank).distinct()
        if (texts.isEmpty()) return result(PaymentParseReason.EMPTY_CONTENT)
        // Do not turn an abbreviated success field into a payment if the full field says it failed.
        if (texts.any(PaymentCandidates::isIncomplete)) return result(PaymentParseReason.INCOMPLETE)
        val readings = texts.map { PaymentCandidates.inspect(item.packageName, title, it, item.key, item.postTime, false) }
        val recognized = readings.filter { it.candidate != null }
        if (recognized.isEmpty()) return NotificationPaymentRead(source, readings.first())
        if (recognized.map { it.candidate!!.kind }.distinct().size != 1) return result(PaymentParseReason.CONFLICTING_CONTENT)
        val first = recognized.first()
        if (recognized.mapNotNull { it.candidate!!.amountCents }.distinct().size > 1) {
            return NotificationPaymentRead(source, PaymentRecognition(first.candidate!!.copy(amountCents = null), PaymentParseReason.AMOUNT_REVIEW))
        }
        return NotificationPaymentRead(source, first)
    }
}
