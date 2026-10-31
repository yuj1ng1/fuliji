package io.github.yuj1ng1.mycampus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeCourseCard(snapshot: SavedSnapshot?, today: LocalDate, now: LocalTime, mode: HomeCourseMode,
                   onModeChange: (HomeCourseMode) -> Unit, onCourses: () -> Unit, onConnect: () -> Unit) {
    val agenda = remember(snapshot?.payload, snapshot?.scope, today, now) { snapshot?.let { CourseAgenda.forDay(it.scope, CaptureReader.decode(it.payload), today, now) } }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("home-course-card")) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CampusIcons.Calendar, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(if (mode == HomeCourseMode.TODAY) "今日课程" else "下一节课", Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onCourses, contentPadding = PaddingValues(3.dp, 0.dp), modifier = Modifier.height(30.dp)) {
                    Text("查看课表", style = MaterialTheme.typography.labelMedium)
                    Icon(CampusIcons.Arrow, null, Modifier.size(13.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == HomeCourseMode.TODAY, onClick = { onModeChange(HomeCourseMode.TODAY) }, label = { Text("今日课程") }, modifier = Modifier.testTag("home-mode-today"))
                FilterChip(selected = mode == HomeCourseMode.NEXT, onClick = { onModeChange(HomeCourseMode.NEXT) }, label = { Text("下一节课") }, modifier = Modifier.testTag("home-mode-next"))
            }
            when {
                snapshot == null -> {
                    Text("还没有保存课表", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onConnect, shape = RoundedCornerShape(9.dp)) { Text("连接教务") }
                }
                agenda?.state == AgendaState.UNKNOWN_TERM -> Text("学期日期待配置，暂不能判断今日课程。请查看课表。", style = MaterialTheme.typography.bodySmall)
                agenda?.state == AgendaState.OUTSIDE_TERM -> Text("当前日期不在已保存学期内，请查看或更新课表。", style = MaterialTheme.typography.bodySmall)
                agenda != null -> {
                    if (mode == HomeCourseMode.TODAY) {
                        if (agenda.courses.isEmpty()) Text("今日无固定节次课程", style = MaterialTheme.typography.titleMedium)
                        agenda.courses.forEachIndexed { index, course ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                            val time = CourseTimes.range(course.firstSection, course.lastSection)
                            CourseAgendaRow(course, when { time == null -> "待补时间"; course in agenda.current -> "当前课段"; now >= time.end -> "已结束"; else -> "待上课" })
                        }
                    } else {
                        if (agenda.next.isNotEmpty()) {
                            val start = requireNotNull(CourseTimes.range(agenda.next.first().firstSection, agenda.next.first().lastSection)).start
                            val minutes = (Duration.between(now, start).seconds + 59) / 60
                            Text("${minutes}分钟后开始", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            agenda.next.forEach { CourseAgendaRow(it, "待上课", prominent = true) }
                        } else Text(when { agenda.unknownTimes > 0 -> "部分课程时间待补充"; agenda.current.isNotEmpty() -> "当天没有后续课程"; agenda.courses.isEmpty() -> "今日无固定节次课程"; else -> "今日课程已结束" }, style = MaterialTheme.typography.titleMedium)
                        if (agenda.current.isNotEmpty()) Text("当前课段：" + agenda.current.joinToString("、") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (agenda.unknownTimes > 0) Text("有 ${agenda.unknownTimes} 个课段时间待补充，下一节仅按已知时间计算。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    if (agenda.unscheduled > 0) Text("另有 ${agenda.unscheduled} 条实践或待核对记录，请查看课表。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("基于已保存课表", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun CourseAgendaRow(course: ScheduledCourse, state: String, prominent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(if (prominent) 65.dp else 58.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val time = CourseTimes.range(course.firstSection, course.lastSection)
            Text(time?.let { CourseTimes.label(it.start) } ?: "待补充", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(time?.let { CourseTimes.label(it.end) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(course.name, style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${course.firstSection}–${course.lastSection}节" + course.details.firstOrNull()?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!prominent) Text(state, style = MaterialTheme.typography.labelSmall, color = if (state == "当前课段") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
    }
}
