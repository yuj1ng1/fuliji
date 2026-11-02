package io.github.yuj1ng1.mycampus.domain

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    fun cents(raw: String): Long? = runCatching {
        require(Regex("""^\d+(?:\.\d{1,2})?$""").matches(raw.trim()))
        BigDecimal(raw.trim()).setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2).longValueExact().also { require(it > 0) }
    }.getOrNull()

    fun yuan(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
}
