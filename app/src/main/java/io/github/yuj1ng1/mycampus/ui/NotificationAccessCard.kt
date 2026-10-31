package io.github.yuj1ng1.mycampus.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.domain.SchoolCalendar
import io.github.yuj1ng1.mycampus.notifications.*
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun NotificationAccessCard() {
    val context = LocalContext.current
    val app = context.applicationContext as CampusApplication
    fun granted() = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(context, PaymentNotificationListener::class.java))
    var access by remember { mutableStateOf(granted()) }
    val state by app.paymentStatus.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = granted() }
    PaymentNotificationStatusCard(access, state, onReconnect = { PaymentNotificationListener.ensureConnected(context, manual = true) },
        onSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
}

@Composable
fun PaymentNotificationStatusCard(access: Boolean, state: PaymentCaptureSnapshot, onReconnect: () -> Unit, onSettings: () -> Unit) {
    fun time(value: Long) = Instant.ofEpochMilli(value).atZone(SchoolCalendar.zone).format(DateTimeFormatter.ofPattern("M.dd HH:mm"))
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("支付通知", fontWeight = FontWeight.SemiBold)
            Text(when { !access -> "尚未开启通知访问"; state.connected -> "已连接 · 识别结果进入待确认"; state.requestedAt != 0L -> "已申请恢复连接 · 等待系统响应"; else -> "已授权 · 监听尚未连接" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            if (state.lastSource.isNotEmpty()) {
                Text("最近收到：${state.lastSource} · ${time(state.lastReceivedAt)}", style = MaterialTheme.typography.labelSmall)
                Text("最近处理：" + paymentCaptureMessage(state.lastReason), style = MaterialTheme.typography.bodySmall,
                    color = if (state.lastReason in setOf("WRITE_ERROR", "READ_ERROR")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            } else Text(if (state.callbacks > 0) "系统回调已到达，尚未收到支付来源通知。" else "尚未收到支付来源通知。", style = MaterialTheme.typography.bodySmall)
            if (state.lastReason == "READ_ERROR" && state.lastSource.isEmpty()) Text(paymentCaptureMessage(state.lastReason), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (state.lastScanAt > 0) Text("现有通知检查：${time(state.lastScanAt)} · ${state.scannedCount} 条支付来源通知", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text("连接后会检查通知栏中仍保留的支付通知；已清除的提醒无法从这里补回。识别成功仍需本人核对，才计入支出。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            if (access) OutlinedButton(onClick = onReconnect, modifier = Modifier.testTag("notification-reconnect")) { Text("连接并检查现有通知") }
            TextButton(onClick = onSettings, modifier = Modifier.testTag("notification-settings")) { Text(if (access) "查看系统授权" else "由我开启通知访问") }
            if (access && !state.connected) Text("若系统一直未连接，可在系统授权页手动关闭后重新开启通知访问。仅授权不代表监听已接通。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text("只处理微信和支付宝；通知正文不保存，无需专门消费来测试。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
