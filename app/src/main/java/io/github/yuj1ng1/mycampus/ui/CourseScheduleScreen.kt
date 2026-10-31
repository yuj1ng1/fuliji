package io.github.yuj1ng1.mycampus.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.web.CampusWebActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CourseScheduleScreen(snapshots: List<SavedSnapshot>, connections: List<ConnectionState>, today: LocalDate) {
    val app = LocalContext.current.applicationContext as CampusApplication
    val context = LocalContext.current
    val progress by app.schoolSync.progress.collectAsStateWithLifecycle()
    val saved = snapshots.filter { it.source == CampusSource.COURSES.name }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val snapshot = saved.firstOrNull { it.id == selectedId } ?: saved.firstOrNull()
    var more by remember { mutableStateOf(false) }
    val connection = connections.firstOrNull { it.source == CampusSource.COURSES.name }
    Column(Modifier.fillMaxSize()) {
        if (connection != null && connection.state != "SUCCESS") Text(connection.message + if (snapshot != null) "，显示上次保存课表。" else "",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        if (snapshot == null) {
            Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还没有课表", fontWeight = FontWeight.SemiBold)
                    Text("先从学校查询读取并保存，课表会按教学周显示在这里。", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { CampusWebActivity.open(context, CampusSource.COURSES) }, modifier = Modifier.testTag("connect-COURSES")) { Text("学校查询") }
                }
            }
        } else {
            CourseWeekView(snapshot, today, Modifier.weight(1f), headerActions = {
                Box {
                    IconButton(onClick = { more = true }, modifier = Modifier.size(40.dp).testTag("course-more")) { Text("⋮", fontSize = 24.sp) }
                    DropdownMenu(more, { more = false }) {
                        DropdownMenuItem(text = { Text(snapshot.scope) }, enabled = false, onClick = {})
                        DropdownMenuItem(text = { Text("学校查询") }, modifier = Modifier.testTag("connect-COURSES"), onClick = { more = false; CampusWebActivity.open(context, CampusSource.COURSES) })
                        DropdownMenuItem(text = { Text(if (progress.running) "正在更新…" else "更新此学期") }, enabled = !progress.running, onClick = {
                            more = false; app.applicationScope.launch { app.schoolSync.refresh(CampusSource.COURSES, requestedScope = snapshot.scope) }
                        })
                        if (saved.size > 1) saved.forEach { item -> DropdownMenuItem(text = { Text(item.scope) }, onClick = { selectedId = item.id; more = false }) }
                    }
                }
            })
        }
    }
}

private val courseColors = listOf(Color(0xFFEAF1FF), Color(0xFFE7F5F0), Color(0xFFFFF2DE), Color(0xFFF1ECFF), Color(0xFFFFEDEE), Color(0xFFE8F5FC))

@Composable
fun CourseWeekView(snapshot: SavedSnapshot, today: LocalDate, modifier: Modifier = Modifier, headerActions: @Composable () -> Unit = {}) {
    val schedule = remember(snapshot.payload) { CourseSchedules.read(CaptureReader.decode(snapshot.payload)) }
    val term = remember(snapshot.scope) { CourseSchedules.term(snapshot.scope) }
    val weekCount = CourseSchedules.weekCount(term, schedule)
    val currentWeek = term?.weekOn(today)
    var week by rememberSaveable(snapshot.id) { mutableIntStateOf((currentWeek ?: 1).coerceIn(1, weekCount)) }
    var listView by rememberSaveable(snapshot.id) { mutableStateOf(false) }
    var chooseWeek by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ScheduledCourse?>(null) }
    LaunchedEffect(weekCount) { week = week.coerceIn(1, weekCount) }
    val active = schedule.forWeek(week)
    val monday = CourseSchedules.monday(term, week)
    val sectionCount = maxOf(10, active.maxOfOrNull { it.lastSection } ?: 10)
    Column(modifier.fillMaxWidth().testTag("course-week-view")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { week-- }, enabled = week > 1, modifier = Modifier.size(40.dp).testTag("previous-week")) { Text("‹", fontSize = 25.sp) }
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { chooseWeek = true }) {
                    Column {
                        Text("第${week}周", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(monday?.let { it.format(DateTimeFormatter.ofPattern("M.d")) + " — " + it.plusDays(6).format(DateTimeFormatter.ofPattern("M.d")) }
                            ?: "教学周日期待配置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                DropdownMenu(chooseWeek, { chooseWeek = false }) {
                    for (value in 1..weekCount) DropdownMenuItem(text = { Text("第${value}周") }, onClick = { week = value; chooseWeek = false })
                }
            }
            TextButton(onClick = { week = (currentWeek ?: 1).coerceIn(1, weekCount) }, enabled = currentWeek != null, contentPadding = PaddingValues(4.dp), modifier = Modifier.width(44.dp)) { Text("本周") }
            TextButton(onClick = { listView = !listView }, contentPadding = PaddingValues(4.dp), modifier = Modifier.width(44.dp).testTag("course-list-toggle")) { Text(if (listView) "周表" else "列表") }
            IconButton(onClick = { week++ }, enabled = week < weekCount, modifier = Modifier.size(40.dp).testTag("next-week")) { Text("›", fontSize = 25.sp) }
            headerActions()
        }
        if (!listView) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Spacer(Modifier.width(42.dp))
            for (day in 1..7) {
                val date = monday?.plusDays(day - 1L)
                val highlight = date == today
                Column(Modifier.weight(1f).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("一二三四五六日"[day - 1].toString(), fontSize = 12.sp, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    Text(date?.dayOfMonth?.toString() ?: "", modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (highlight) MaterialTheme.colorScheme.primary else Color.Transparent).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = if (highlight) Color.White else MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("course-grid-viewport")) {
            // Measure the actual space below all headers. Eight daytime sections fill one viewport.
            val sectionHeight = maxHeight / 8
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (listView) {
                active.sortedWith(compareBy({ it.day }, { it.firstSection })).forEach { course ->
                    Card(onClick = { detail = course }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(course.name, fontWeight = FontWeight.SemiBold)
                            Text("周" + "一二三四五六日"[course.day - 1] + " · ${course.firstSection}—${course.lastSection}节 · " + CourseTimes.label(course.firstSection, course.lastSection), style = MaterialTheme.typography.bodySmall)
                            Text(course.details.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Column(Modifier.width(42.dp)) {
                        for (section in 1..sectionCount) Column(Modifier.height(sectionHeight).fillMaxWidth().padding(top = 4.dp).testTag("section-time-$section"), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(section.toString(), fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.secondary)
                            CourseTimes.section(section)?.let { time ->
                                Text(CourseTimes.label(time.start), fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                Text(CourseTimes.label(time.end), fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.secondary)
                            } ?: Text("待补充", fontSize = 8.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    val placements = CourseSchedules.place(active)
                    for (day in 1..7) BoxWithConstraints(Modifier.weight(1f).height(sectionHeight * sectionCount).border(0.5.dp, Color(0xFFEDF0F6))) {
                        val width = maxWidth
                        for (section in 1 until sectionCount) HorizontalDivider(Modifier.offset(y = sectionHeight * section), color = Color(0xFFEDF0F6), thickness = 0.5.dp)
                        placements.filter { it.course.day == day }.forEach { placement ->
                            val course = placement.course
                            val color = courseColors[Math.floorMod(course.name.hashCode(), courseColors.size)]
                            Surface(onClick = { detail = course }, shape = RoundedCornerShape(7.dp), color = color,
                                modifier = Modifier.offset(x = width * placement.lane / placement.lanes, y = sectionHeight * (course.firstSection - 1))
                                    .width(width / placement.lanes).height(sectionHeight * (course.lastSection - course.firstSection + 1)).padding(2.dp)
                                    .semantics { contentDescription = "周" + "一二三四五六日"[day - 1] + "，${course.firstSection}至${course.lastSection}节，" + course.name }) {
                                Column(Modifier.padding(horizontal = 4.dp, vertical = 7.dp)) {
                                    Text(course.name, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(6.dp))
                                    if (course.details.isNotEmpty()) Text(course.details.first(), fontSize = 9.sp, lineHeight = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFF59677E))
                                    if (placement.lanes > 1) Text("同节多课", fontSize = 8.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            if (active.isEmpty()) Text("本周没有固定节次的课程", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
            if (schedule.unscheduled.isNotEmpty()) {
                Text("实践与待核对课程", Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp), fontWeight = FontWeight.SemiBold)
                schedule.unscheduled.forEach { row ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(14.dp)) { Text(row.title, fontWeight = FontWeight.SemiBold); Text(row.detail, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Text("保存于 " + java.time.Instant.ofEpochMilli(snapshot.capturedAt).atZone(SchoolCalendar.zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")), Modifier.padding(20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
    detail?.let { course -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(course.name) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("星期" + "一二三四五六日"[course.day - 1] + " · ${course.firstSection}—${course.lastSection}节")
            Text(CourseTimes.label(course.firstSection, course.lastSection))
            Text(course.weekRule)
            course.details.forEach { Text(it) }
        } }, confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } }) }
}
