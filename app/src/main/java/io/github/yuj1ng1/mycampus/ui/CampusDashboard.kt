package io.github.yuj1ng1.mycampus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Blue = Color(0xFF356AE6)
private val Muted = Color(0xFF8B96A9)
private val Line = Color(0xFFEDF0F6)

@Composable
fun CampusHeader(title: String, subtitle: String, onSettings: () -> Unit, onAdd: (() -> Unit)? = null, compact: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(if (compact) 46.dp else 66.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title == "福理集") {
            Surface(shape = RoundedCornerShape(12.dp), color = Blue) {
                Icon(CampusIcons.School, null, Modifier.padding(9.dp).size(22.dp), tint = Color.White)
            }
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
        if (onAdd != null) IconButton(onClick = onAdd, modifier = Modifier.testTag("add-expense")) {
            Icon(CampusIcons.Add, "新增记账", Modifier.size(21.dp))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
            Icon(CampusIcons.Settings, "设置", Modifier.size(21.dp), tint = Color(0xFF69788E))
        }
    }
}

@Composable
fun CampusBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("今日", "课表", "校园", "账本")
    val icons = listOf(CampusIcons.Home, CampusIcons.Calendar, CampusIcons.School, CampusIcons.Wallet)
    Surface(color = Color.White) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = Line)
            Row(Modifier.fillMaxWidth().height(63.dp), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { index, label ->
                    val active = index == selected
                    Column(
                        Modifier.weight(1f).fillMaxHeight().testTag("tab-" + index)
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) }),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icons[index], label + "页面", Modifier.size(22.dp), tint = if (active) Blue else Muted)
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) Blue else Muted,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun CampusDashboard(
    today: LocalDate,
    snapshots: List<SavedSnapshot>,
    ledger: List<LedgerEntry>,
    onCourses: () -> Unit,
    onConnectCourses: () -> Unit,
    onPower: () -> Unit,
    onLedger: () -> Unit,
    onExam: () -> Unit,
    onGrades: () -> Unit,
    onCalendar: () -> Unit,
) {
    val course = snapshots.firstOrNull { it.source == CampusSource.COURSES.name }
    val powerRoom = snapshots.firstOrNull { it.source in setOf(CampusSource.POWER.name, CampusSource.POWER_USAGE.name) }?.scope
    val power = snapshots.firstOrNull { it.source == CampusSource.POWER.name && it.scope == powerRoom }
    val usage = snapshots.firstOrNull { it.source == CampusSource.POWER_USAGE.name && it.scope == powerRoom }
    val week = SchoolCalendar.termOn(today)?.weekOn(today)
    val drafts = ledger.count { it.status == "DRAFT" }
    LazyColumn(
        Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(today.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)), style = MaterialTheme.typography.headlineSmall)
                Text(today.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)), Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.weight(1f))
                if (week != null) Surface(color = Color(0xFFEBF1FF), shape = RoundedCornerShape(7.dp)) {
                    Text("校历第" + week + "周", Modifier.padding(9.dp, 5.dp), style = MaterialTheme.typography.labelMedium, color = Blue)
                }
            }
        }
        item {
            Card(onClick = onCourses, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    SectionLine("我的课表", CampusIcons.Calendar, if (course == null) "未连接" else "查看课表")
                    if (course == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("还没有保存课表", style = MaterialTheme.typography.titleMedium)
                                Text("连接教务，课表随时可看", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            Button(onClick = onConnectCourses, shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(36.dp)) {
                                Text("连接教务", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Text(course.scope, style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text("已保存 " + course.itemCount + " 条课程记录", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item { PowerUsageCard(power, usage, today, powerRoom, onPower) }
        item {
            Card(onClick = onLedger, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(CampusIcons.Wallet, Color(0xFF6E75CA), Color(0xFFF0F0FC))
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("今日支出", style = MaterialTheme.typography.titleSmall)
                        Text(if (drafts == 0) "已确认的消费记录" else drafts.toString() + " 笔待确认", style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                    Text("¥ " + expenseTotal(ledger, today), style = MaterialTheme.typography.titleLarge)
                    Icon(CampusIcons.Arrow, null, Modifier.padding(start = 8.dp).size(16.dp), tint = Muted)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("校园服务", style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
                        Shortcut("考试安排", CampusIcons.Exam, Blue, Color(0xFFEDF2FF), onExam, Modifier.weight(1f))
                        Shortcut("成绩查询", CampusIcons.Grades, Color(0xFF329E99), Color(0xFFEAF7F5), onGrades, Modifier.weight(1f))
                        Shortcut("学年校历", CampusIcons.Calendar, Color(0xFFC79041), Color(0xFFFCF4E8), onCalendar, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(CampusIcons.Shield, null, Modifier.size(12.dp), tint = Muted)
                Text("数据保存在本机", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
fun PowerUsageCard(power: SavedSnapshot?, usage: SavedSnapshot?, today: LocalDate, room: String?, onClick: (() -> Unit)? = null) {
    val series = PowerHistory.lastSevenDays(usage?.let { CaptureReader.decode(it.payload) }.orEmpty(), today)
    val known = series.count { it.value != null }
    val balance = power?.let { CaptureReader.decode(it.payload).firstOrNull()?.detail?.removeSuffix(" 度") }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CampusIcons.Bolt, null, Modifier.size(18.dp), tint = Blue)
                Text("宿舍用电", Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.titleSmall)
                if (onClick != null) TextButton(onClick = onClick, contentPadding = PaddingValues(5.dp, 0.dp), modifier = Modifier.height(30.dp)) {
                    Text("查看详情", style = MaterialTheme.typography.labelMedium)
                    Icon(CampusIcons.Arrow, null, Modifier.size(13.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("剩余电量", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(balance ?: "— —", style = MaterialTheme.typography.headlineMedium, color = if (balance == null) Muted else MaterialTheme.colorScheme.onSurface)
                        if (balance != null) Text("度", Modifier.padding(start = 5.dp, bottom = 4.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("近7天用量", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text(series.first().date.format(DateTimeFormatter.ofPattern("M.dd")) + " – " + series.last().date.format(DateTimeFormatter.ofPattern("M.dd")),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            UsageChart(series)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(room ?: "未选择宿舍", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (known == 0) "尚无用量记录" else "已记录 " + known + "/7 天", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            if (usage != null) Text("用量采用原站数值（原表未标单位）· 缺失日期不补零", style = MaterialTheme.typography.labelSmall, color = Muted)
            if (power != null) Text("余额保存于 " + java.time.Instant.ofEpochMilli(power.capturedAt).atZone(SchoolCalendar.zone)
                .format(DateTimeFormatter.ofPattern("M月d日 HH:mm")), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun UsageChart(series: List<DailyUsage>) {
    val anyData = series.any { it.value != null }
    val values = series.map { it.value?.toFloat() }
    val max = (values.filterNotNull().maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.2f
    val description = series.joinToString("，") { it.date.toString() + "：" + (it.value?.toPlainString() ?: "无记录") }
    var selected by remember(series) { mutableIntStateOf(series.indexOfLast { it.value != null }.coerceAtLeast(0)) }
    Column {
        if (anyData) Text(series[selected].date.format(DateTimeFormatter.ofPattern("M.dd")) + " · " + (series[selected].value?.toPlainString() ?: "暂无记录"),
            Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelMedium, color = Blue)
        Row {
            if (anyData) Column(Modifier.width(29.dp).height(76.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(max, max / 2, 0f).forEach { tick -> Text(String.format(Locale.ROOT, if (max < 10) "%.1f" else "%.0f", tick), fontSize = 9.sp, color = Muted) }
            }
            Box(Modifier.weight(1f).height(76.dp)) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "近7天学校日用量，" + description }
                .pointerInput(series) { detectTapGestures { tap -> selected = (tap.x / (size.width / 7f)).toInt().coerceIn(0, 6) } }) {
                val top = 5.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                val inset = size.width / 14f
                val step = size.width / 7f
                (0..2).forEach { index ->
                    val y = top + (bottom - top) * index / 2f
                    drawLine(Line, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
                }
                values.forEachIndexed { index, value ->
                    if (value != null) {
                        val point = Offset(inset + index * step, bottom - value / max * (bottom - top))
                        val previous = values.getOrNull(index - 1)
                        if (previous != null) drawLine(Blue, Offset(inset + (index - 1) * step, bottom - previous / max * (bottom - top)),
                            point, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(Color.White, if (selected == index) 4.5.dp.toPx() else 3.5.dp.toPx(), point)
                        drawCircle(Blue, if (selected == index) 3.2.dp.toPx() else 2.3.dp.toPx(), point)
                    }
                }
            }
            if (!anyData) Text("暂无这7天的用量记录", Modifier.align(Alignment.Center).background(Color.White).padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            if (anyData) Spacer(Modifier.width(29.dp))
            series.forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("M.dd")), fontSize = 9.sp, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun SectionLine(title: String, icon: ImageVector, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = Blue)
        Text(title, Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.titleSmall)
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = Muted)
        Icon(CampusIcons.Arrow, null, Modifier.padding(start = 3.dp).size(13.dp), tint = Muted)
    }
}

@Composable
private fun Shortcut(label: String, icon: ImageVector, tint: Color, background: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier, color = Color.White) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTile(icon, tint, background)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color = Blue, background: Color = Color(0xFFEDF2FF)) {
    Surface(shape = RoundedCornerShape(12.dp), color = background) {
        Icon(icon, null, Modifier.padding(10.dp).size(21.dp), tint = tint)
    }
}
