package io.github.yuj1ng1.mycampus.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yuj1ng1.mycampus.BuildConfig
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.data.SchoolAccount
import io.github.yuj1ng1.mycampus.web.SchoolBrowserLease
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@Composable
fun SchoolConnectionSettings(app: CampusApplication, onDismiss: () -> Unit) {
    val options by app.schoolOptions.options.collectAsStateWithLifecycle()
    val progress by app.schoolSync.progress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(app.schoolSecrets.hasAccount()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try { username = withContext(Dispatchers.IO) { app.schoolSecrets.account()?.username.orEmpty() } }
        catch (_: Exception) { message = "原登录信息暂时无法读取，请重新填写并保存。" }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, title = { Text("学校连接与更新") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("福理集 " + BuildConfig.VERSION_NAME + " · 非学校官方应用", style = MaterialTheme.typography.labelMedium)
                Text("可选择在这台手机加密保存账号密码，用于学校 WebVPN 和统一认证。不会把教务密码用于后勤后台，也不上传账号。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(username, { username = it.take(100) }, label = { Text("学校学号 / 工号") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("school-username"))
                OutlinedTextField(password, { password = it.take(256) }, label = { Text(if (saved) "新密码（留空保留已保存密码）" else "学校密码") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("school-password"))
                Text("修改学号会清理先前的校园缓存并退出旧会话，账本保留。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Button(
                    enabled = !saving && username.isNotBlank() && (password.isNotEmpty() || saved),
                    modifier = Modifier.fillMaxWidth().testTag("save-school-account"),
                    onClick = {
                        saving = true
                        scope.launch {
                            SchoolBrowserLease.enter()
                            try {
                              app.schoolSync.editConnection {
                                val old = withContext(Dispatchers.IO) { runCatching { app.schoolSecrets.account() }.getOrNull() }
                                val actualPassword = password.ifEmpty { if (old?.username == username.trim()) old.password else "" }
                                require(actualPassword.isNotEmpty()) { "请输入该账号的密码。" }
                                val changed = old != null && old.username != username.trim()
                                if (changed) {
                                    suspendCancellableCoroutine<Unit> { next -> CookieManager.getInstance().removeAllCookies { if (next.isActive) next.resume(Unit) } }
                                    WebStorage.getInstance().deleteAllData()
                                    withContext(Dispatchers.IO) { app.repository.clearCampusCache() }
                                }
                                withContext(Dispatchers.IO) {
                                    app.schoolSecrets.saveAccount(SchoolAccount(username.trim(), actualPassword))
                                    app.schoolOptions.update(autoLoginPaused = false)
                                }
                                saved = true; password = ""; message = "已加密保存在这台手机，登录失败或遇到验证码时会停止重试。"
                              }
                            } catch (_: Exception) { message = "未能保存，请检查账号和密码后重试。" }
                            finally { SchoolBrowserLease.leave(); saving = false }
                        }
                    },
                ) { Text(if (saving) "正在保存…" else "加密保存登录信息") }
                if (saved) TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { app.schoolSecrets.removeAccount(); app.schoolOptions.update(autoLoginPaused = true) }
                            saved = false; password = ""; message = "已删除本机保存的账号密码。仍有效的学校会话可继续使用。"
                        } catch (_: Exception) { message = "删除未完成，请重试。" }
                    }
                }) { Text("删除本机保存的账号密码") }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("尽量保持学校登录", style = MaterialTheme.typography.titleSmall)
                        Text("使用学校提供的7天免登录选项，实际有效期由学校决定。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(options.rememberSession, { app.schoolOptions.update(rememberSession = it) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("教务自动更新", style = MaterialTheme.typography.titleSmall)
                        Text("打开应用时检查课表、考试和成绩，后台约每6小时尝试；系统可能延后。用电在宿舍页面单独设置，失败保留旧数据。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(options.autoRefresh, {
                        app.schoolOptions.update(autoRefresh = it)
                        app.schoolSync.configureSchedule()
                        if (it) app.applicationScope.launch { app.schoolSync.refresh(onlyStale = true) }
                    }, modifier = Modifier.testTag("school-auto-refresh"))
                }
                if (options.autoLoginPaused) Text("自动登录已暂缓：请本人在学校页面完成确认，或更新本机保存的登录信息。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { app.applicationScope.launch { app.schoolSync.refresh() } }, enabled = !progress.running && !saving,
                    modifier = Modifier.fillMaxWidth()) { Text(if (progress.running) "正在更新…" else "立即更新已保存的数据") }
                Text(progress.message, style = MaterialTheme.typography.bodySmall)
                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                Text("后勤账号与教务分开，请从“后勤登录与连接”设置。验证码需本人填写。课程/考试提醒、低电量提醒、账本导出和自动入账尚未提供。",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("完成") } },
    )
}
