package io.github.yuj1ng1.mycampus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yuj1ng1.mycampus.data.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun LedgerSummaryCard(entries: List<LedgerEntry>, today: LocalDate) {
    val month = remember(entries, today) { monthlyExpenseTotal(entries, today) }
    val series = remember(entries, today) { lastSevenDaysSpending(entries, today) }
    val drafts = entries.count { it.status == "DRAFT" }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("ledger-summary")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("本月支出", style = MaterialTheme.typography.titleSmall)
                Text("${today.monthValue}月 · 截至今日", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("¥ " + month, Modifier.weight(1f).testTag("month-spending"), style = MaterialTheme.typography.headlineLarge)
                Column(horizontalAlignment = Alignment.End) {
                    Text("今日支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("¥ " + series.last().yuan, style = MaterialTheme.typography.titleSmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("近7天消费", style = MaterialTheme.typography.titleSmall)
                Text("含今天 · 已确认支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            SpendingChart(series)
            if (drafts > 0) Text("${drafts} 笔待确认，尚未计入支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("按本机已确认支出汇总，通知记录采用通知时间。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun SpendingChart(series: List<DailySpending>) {
    var selected by remember(series) { mutableIntStateOf(6) }
    val maxCents = series.maxOf { BigDecimal(it.cents) }.max(BigDecimal(100)).multiply(BigDecimal("1.2"))
    val ratios = series.map { BigDecimal(it.cents).divide(maxCents, MathContext.DECIMAL64).toFloat() }
    val blue = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.secondary
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(series[selected].date.format(DateTimeFormatter.ofPattern("M.dd")) + " · ¥ " + series[selected].yuan,
            Modifier.testTag("selected-spending-day"), style = MaterialTheme.typography.labelMedium, color = blue)
        Row {
            Column(Modifier.width(42.dp).height(104.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maxCents, maxCents / BigDecimal(2), BigDecimal.ZERO).forEach { cents -> Text(axisAmount(cents.movePointLeft(2)), fontSize = 9.sp, color = muted, maxLines = 1) }
            }
            Canvas(Modifier.weight(1f).height(104.dp).testTag("spending-chart")
                .semantics { contentDescription = "近7天已确认支出，" + series.joinToString("，") { it.date.toString() + "：¥" + it.yuan } }
                .pointerInput(series) { detectTapGestures { tap -> selected = (tap.x / (size.width / 7f)).toInt().coerceIn(0, 6) } }) {
                val top = 6.dp.toPx()
                val bottom = size.height - 7.dp.toPx()
                val step = size.width / 7f
                val inset = step / 2f
                for (row in 0..2) {
                    val y = top + (bottom - top) * row / 2f
                    drawLine(line, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                }
                ratios.forEachIndexed { index, ratio ->
                    val point = Offset(inset + index * step, bottom - ratio * (bottom - top))
                    if (index > 0) drawLine(blue, Offset(inset + (index - 1) * step, bottom - ratios[index - 1] * (bottom - top)), point, 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(Color.White, if (selected == index) 4.5.dp.toPx() else 3.5.dp.toPx(), point)
                    drawCircle(blue, if (selected == index) 3.2.dp.toPx() else 2.3.dp.toPx(), point)
                }
            }
        }
        Row {
            Spacer(Modifier.width(42.dp))
            series.forEachIndexed { index, day ->
                Box(Modifier.weight(1f).testTag("spend-day-$index").clickable { selected = index }
                    .semantics { contentDescription = day.date.toString() + "：¥" + day.yuan }, contentAlignment = Alignment.Center) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("M.dd")), Modifier.padding(vertical = 5.dp), fontSize = 9.sp, color = if (index == selected) blue else muted)
                }
            }
        }
        if (series.all { it.cents.signum() == 0 }) Text("近7天暂无已确认支出", style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

private fun axisAmount(yuan: BigDecimal): String {
    val unit = when { yuan >= BigDecimal("1000000000000") -> BigDecimal("1000000000000") to "万亿"; yuan >= BigDecimal("100000000") -> BigDecimal("100000000") to "亿"; yuan >= BigDecimal("10000") -> BigDecimal("10000") to "万"; else -> null }
    return if (unit != null) yuan.divide(unit.first, 1, RoundingMode.HALF_UP).toPlainString() + unit.second else yuan.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
