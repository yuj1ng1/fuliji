package io.github.yuj1ng1.mycampus

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.net.toUri
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.notifications.PaymentNotificationListener
import io.github.yuj1ng1.mycampus.ui.*
import io.github.yuj1ng1.mycampus.web.CampusWebActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as CampusApplication).repository
        setContent { CampusTheme { CampusApp(repository) } }
    }
}

private val tabs = listOf("今日", "课表", "校园", "账本")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusApp(repository: CampusRepository) {
    val context = LocalContext.current
    val app = context.applicationContext as CampusApplication
    val snapshots by repository.snapshots.collectAsStateWithLifecycle(emptyList())
    val connections by repository.connections.collectAsStateWithLifecycle(emptyList())
    val ledger by repository.ledger.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<CampusSource?>(null) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var addExpense by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LedgerEntry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var today by remember { mutableStateOf(LocalDate.now(SchoolCalendar.zone)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { app.applicationScope.launch { app.schoolSync.onForeground() } }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                today = LocalDate.now(SchoolCalendar.zone)
                delay(60_000)
            }
        }
    }
    val power = snapshots.firstOrNull { it.source == CampusSource.POWER.name }
    val drafts = ledger.count { it.status == "DRAFT" }

    Scaffold(
        topBar = {
            CampusHeader(if (tab == 0) "榕集" else tabs[tab], "福建理工大学",
                onSettings = { showSettings = true }, onAdd = if (tab == 3) ({ addExpense = true }) else null)
        },
        bottomBar = {
            CampusBottomBar(tab) { tab = it }
        },
    ) { insets ->
        AnimatedContent(tab, Modifier.fillMaxSize().padding(insets), label = "page") { selected ->
            when (selected) {
                0 -> CampusDashboard(today, snapshots, ledger,
                    onCourses = { tab = 1 }, onConnectCourses = { CampusWebActivity.open(context, CampusSource.COURSES) },
                    onPower = { detail = CampusSource.POWER }, onLedger = { tab = 3 },
                    onExam = { detail = CampusSource.EXAMS }, onGrades = { detail = CampusSource.GRADES }, onCalendar = { showCalendar = true })
                1 -> SnapshotScreen(CampusSource.COURSES, snapshots, connections)
                2 -> LazyColumn(
                    contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { SectionTitle("教务服务") }
                    item { ServiceCard(CampusIcons.Exam, "考试安排", "时间、地点与座号", onClick = { detail = CampusSource.EXAMS }) }
                    item { ServiceCard(CampusIcons.Grades, "成绩查询", "查看并保存学期成绩", onClick = { detail = CampusSource.GRADES }) }
                    item { ServiceCard(CampusIcons.Calendar, "学年校历", "2026—2027 · 官方校历", onClick = { showCalendar = true }) }
                    item { SectionTitle("宿舍生活") }
                    item { ServiceCard(CampusIcons.Bolt, "宿舍电量", power?.scope ?: "选择房间，读取剩余电量", onClick = { detail = CampusSource.POWER }) }
                }
                else -> LazyColumn(
                    modifier = Modifier.testTag("ledger"),
                    contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text("今日已确认支出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text("¥ " + expenseTotal(ledger, today), Modifier.padding(top = 7.dp), style = MaterialTheme.typography.headlineLarge)
                                if (drafts > 0) Text(drafts.toString() + " 笔支付通知待确认，不计入支出", Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    item { NotificationAccessCard() }
                    item {
                        FilledTonalButton(onClick = { addExpense = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("记一笔")
                        }
                    }
                    if (ledger.isEmpty()) item { EmptyCard("还没有消费记录", "可以先手动记一笔。支付通知授权由你自行开启。") }
                    items(ledger, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(entry.note.ifBlank { entry.source }, fontWeight = FontWeight.SemiBold)
                                    Text(entry.amountCents?.let { "¥" + Money.yuan(it) } ?: "金额待核对", fontWeight = FontWeight.Bold)
                                }
                                Text(entry.source + " · " + kindName(entry.kind) + " · " + timestamp(entry.occurredAt),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                if (entry.status == "DRAFT") {
                                    Text("待确认 · 采用通知时间", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    TextButton(onClick = { editing = entry }) { Text("核对后记为支出") }
                                }
                                TextButton(onClick = { deleting = entry }) { Text("删除记录", color = MaterialTheme.colorScheme.secondary) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCalendar) CalendarViewer { showCalendar = false }
    if (showSettings) SchoolConnectionSettings(app) { showSettings = false }
    detail?.let { source ->
        Dialog(onDismissRequest = { detail = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TextButton(onClick = { detail = null }) { Text("返回校园") }
                    Box(Modifier.weight(1f)) {
                        if (source == CampusSource.POWER) PowerScreen(snapshots, connections, today)
                        else SnapshotScreen(source, snapshots, connections)
                    }
                }
            }
        }
    }
    if (addExpense || editing != null) {
        val entry = editing
        ExpenseDialog(
            entry = entry,
            onDismiss = { addExpense = false; editing = null },
            onSave = { amount, note ->
                scope.launch {
                    try {
                        if (entry == null) repository.addExpense(amount, note) else repository.confirmExpense(entry.id, amount, note)
                        addExpense = false; editing = null
                    } catch (_: Exception) { error = "这笔记录暂时无法保存，请重试。" }
                }
            },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("删除这笔记录？") },
            text = { Text("仅删除本机账本中的这条记录，不影响实际支付。") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try { repository.deleteEntry(entry.id); deleting = null }
                    catch (_: Exception) { error = "这笔记录暂时无法删除，请重试。" }
                }
            }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("保留") } },
        )
    }
    error?.let { text ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("暂时无法保存") },
            text = { Text(text) }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } })
    }
}

@Composable
private fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

@Composable
private fun ServiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(CampusIcons.Arrow, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun SnapshotScreen(source: CampusSource, snapshots: List<SavedSnapshot>, connections: List<ConnectionState>) {
    val context = LocalContext.current
    val app = context.applicationContext as CampusApplication
    val sync by app.schoolSync.progress.collectAsStateWithLifecycle()
    val saved = snapshots.filter { it.source == source.name }
    val connection = connections.firstOrNull { it.source == source.name }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("我的" + source.label, style = MaterialTheme.typography.titleMedium) }
        item {
            Text("在学校页面完成登录和查询后，点击顶部“读取并保存”。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        item {
            Button(onClick = { CampusWebActivity.open(context, source) }, modifier = Modifier.fillMaxWidth().testTag("connect-" + source.name)) {
                Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp)); Text("打开学校查询")
            }
        }
        if (source.isAcademic) item {
            OutlinedButton(onClick = { app.applicationScope.launch { app.schoolSync.refresh(source) } }, enabled = !sync.running, modifier = Modifier.fillMaxWidth()) {
                Text(if (sync.running) "正在更新…" else "使用已有登录更新")
            }
        }
        connection?.let { state ->
            item {
                Text(state.message + if (state.state != "SUCCESS" && saved.isNotEmpty()) "\n下面保留上次成功保存的数据。" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.state == "SUCCESS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
        if (saved.isEmpty()) item {
            EmptyCard("还没有保存的记录", "连接学校并完成查询，即可保存到手机，离线也能查看。")
        }
        saved.forEach { snapshot ->
            item {
                Column {
                    Text(snapshot.scope, fontWeight = FontWeight.SemiBold)
                    Text("保存于 " + timestamp(snapshot.capturedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            val rows = CaptureReader.decode(snapshot.payload)
            if (snapshot.itemCount == 0) item { EmptyCard("当前查询暂无记录", "这是已保存的查询结果，不代表以后不会公布新信息。") }
            items(rows) { row ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.title, fontWeight = FontWeight.SemiBold)
                        Text(row.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerScreen(snapshots: List<SavedSnapshot>, connections: List<ConnectionState>, today: LocalDate) {
    val context = LocalContext.current
    val app = context.applicationContext as CampusApplication
    var showPowerAccess by remember { mutableStateOf(false) }
    val saved = snapshots.filter { it.source == CampusSource.POWER.name || it.source == CampusSource.POWER_USAGE.name }
    val latestRoom = saved.firstOrNull()?.scope
    var room by remember(latestRoom) { mutableStateOf(latestRoom) }
    val power = saved.firstOrNull { it.source == CampusSource.POWER.name && it.scope == room }
    val usage = saved.firstOrNull { it.source == CampusSource.POWER_USAGE.name && it.scope == room }
    val series = PowerHistory.lastSevenDays(usage?.let { CaptureReader.decode(it.payload) }.orEmpty(), today)
    if (showPowerAccess) PowerAccessDialog(app, onConnected = {
        showPowerAccess = false
        CampusWebActivity.open(context, CampusSource.POWER, authorize = true)
    }, onDismiss = { showPowerAccess = false })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("宿舍用电", style = MaterialTheme.typography.headlineSmall) }
        if (saved.map { it.scope }.distinct().size > 1) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.map { it.scope }.distinct().forEach { savedRoom ->
                    FilterChip(selected = room == savedRoom, onClick = { room = savedRoom }, label = { Text(savedRoom) })
                }
            }
        }
        item { PowerUsageCard(power, usage, today, room) }
        item {
            OutlinedButton(onClick = { showPowerAccess = true }, modifier = Modifier.fillMaxWidth()) { Text("企业微信后勤接入 / 重新授权") }
        }
        if (saved.isEmpty()) item { Text("后勤使用独立授权。若网页空白或要求未知密码，请从上方进入企业微信接入；不要填写教务密码。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { CampusWebActivity.open(context, CampusSource.POWER) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
                    Text("查询余额")
                }
                OutlinedButton(onClick = { CampusWebActivity.open(context, CampusSource.POWER_USAGE) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
                    Text("查询用量")
                }
            }
        }
        item { Text("余额与用量需要分别查询保存。查看用量时，请在后勤页面选择“房间用量”和宿舍。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        connections.filter { it.source in setOf(CampusSource.POWER.name, CampusSource.POWER_USAGE.name) && it.state != "SUCCESS" }.forEach { state ->
            item { Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        item { SectionTitle("每日明细") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    series.asReversed().forEachIndexed { index, day ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(day.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)), style = MaterialTheme.typography.bodySmall)
                            Text(day.value?.toPlainString() ?: "暂无记录", style = MaterialTheme.typography.bodySmall,
                                color = if (day.value == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        usage?.let { snapshot -> item { Text("用量保存于 " + timestamp(snapshot.capturedAt) + " · 仅展示学校公布的数值，原表未标单位。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) } }
    }
}

@Composable
private fun NotificationAccessCard() {
    val context = LocalContext.current
    var access by remember { mutableStateOf(false) }
    val connected by PaymentNotificationListener.connected.collectAsStateWithLifecycle()
    val lastWriteFailed by PaymentNotificationListener.lastWriteFailed.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, PaymentNotificationListener::class.java))
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("支付通知", fontWeight = FontWeight.SemiBold)
            Text(if (!access) "尚未开启通知访问" else if (connected) "已连接 · 新识别记录将进入待确认" else "已授权 · 等待系统连接",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            if (lastWriteFailed) Text("保存支付记录失败，请稍后重试。", color = MaterialTheme.colorScheme.error)
            Text("仅保存候选金额、来源和通知时间，正文不保存。请核对后计入支出。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text(if (access) "查看系统授权" else "由我开启通知访问") }
        }
    }
}

@Composable
private fun ExpenseDialog(entry: LedgerEntry?, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var amount by remember(entry?.id) { mutableStateOf(entry?.amountCents?.let(Money::yuan).orEmpty()) }
    var note by remember(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    val cents = Money.cents(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "记一笔支出" else "核对支出") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (entry != null) Text(entry.source + " · " + kindName(entry.kind) + "\n采用通知时间，请与原账单核对。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(amount, { amount = it }, label = { Text("金额（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.testTag("expense-amount"),
                    isError = amount.isNotEmpty() && cents == null)
                OutlinedTextField(note, { note = it.take(100) }, label = { Text("备注（选填）") }, modifier = Modifier.testTag("expense-note"), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null, onClick = { onSave(requireNotNull(cents), note) }, modifier = Modifier.testTag("save-expense")) {
                Text("保存为支出")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CalendarViewer(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val image = remember { context.assets.open("calendar-2026-2027.png").use { BitmapFactory.decodeStream(it).asImageBitmap() } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("返回") }
                    Text("学年校历", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, WebPolicy.CALENDAR_URL.toUri())) }) { Text("官方来源") }
                }
                Text("2026—2027 学年 · 双指或双击放大", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.White)
                    .transformable(transform)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 3f; offset = Offset.Zero }) }) {
                    Image(image, "福建理工大学2026—2027学年官方校历",
                        Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                        contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

private fun timestamp(value: Long) = Instant.ofEpochMilli(value).atZone(SchoolCalendar.zone)
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun kindName(kind: String) = when (kind) {
    "EXPENSE" -> "支出"; "REFUND" -> "退款"; "TOPUP" -> "充值"; "TRANSFER" -> "转账"; "INCOME" -> "收款"; else -> "待核对"
}
