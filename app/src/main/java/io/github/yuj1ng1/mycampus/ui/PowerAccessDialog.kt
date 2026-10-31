package io.github.yuj1ng1.mycampus.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.data.SchoolAccount
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.web.SchoolBrowserLease
import kotlinx.coroutines.*
import kotlin.coroutines.resume

fun openEnterpriseWeChat(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.tencent.wework")
    if (intent == null) { Toast.makeText(context, "未找到企业微信，请手动打开企业微信工作台。", Toast.LENGTH_LONG).show(); return }
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "请手动打开企业微信工作台。", Toast.LENGTH_LONG).show() }
}

@Composable
fun PowerAccessDialog(app: CampusApplication, initialText: String = "", onConnected: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialText.ifBlank { CampusSource.POWER.entryUrl }.take(16_384)) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var savedAccount by remember { mutableStateOf(app.schoolSecrets.hasPowerAccount()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val link = SchoolAccessPolicy.extractPowerEntry(input)
    LaunchedEffect(Unit) {
        try { username = withContext(Dispatchers.IO) { app.schoolSecrets.powerAccount()?.username.orEmpty() } }
        catch (_: Exception) { message = "原后勤账号无法读取，请重新填写。" }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, title = { Text("后勤连接") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("首次使用通常需要在学校页面输入手机号、短信验证码并确认绑定。可在这里保存独立后勤账号，用于填入身份认证。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(input, { input = it.take(16_384) }, label = { Text("水电查询链接") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    visualTransformation = if (SchoolAccessPolicy.isSignedPowerLink(link.orEmpty())) PasswordVisualTransformation() else VisualTransformation.None,
                    minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth().testTag("power-entry"))
                Text(if (link != null) "已识别学校水电查询入口，可以连接。" else "请粘贴福建理工大学水电充值页面的完整 HTTPS 链接。",
                    style = MaterialTheme.typography.bodySmall, color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                OutlinedTextField(username, { username = it.take(100) }, label = { Text("后勤学号（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("power-username"))
                OutlinedTextField(password, { password = it.take(256) }, label = { Text(if (savedAccount) "后勤密码（留空保留）" else "后勤独立密码（可选）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("power-password"))
                Text("填写账号后，点击连接会在这台手机加密保存。后勤密码与教务密码分开，不会预置到安装包。更换已保存的学号会清除学校登录会话，原课表和账本保留。", style = MaterialTheme.typography.bodySmall)
                if (savedAccount) TextButton(enabled = !saving, onClick = {
                    scope.launch {
                        try { withContext(Dispatchers.IO) { app.schoolSecrets.removePowerAccount() }; savedAccount = false; username = ""; password = ""; message = "已删除本机后勤账号。有效会话可继续使用。" }
                        catch (_: Exception) { message = "删除未完成，请重试。" }
                    }
                }) { Text("删除本机后勤账号") }
                TextButton(onClick = { openEnterpriseWeChat(context) }, enabled = !saving) { Text("也可打开企业微信 → 智慧后勤") }
                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = link != null && !saving && (password.isEmpty() || username.isNotBlank()), modifier = Modifier.testTag("connect-power-entry"), onClick = {
                saving = true
                scope.launch {
                    SchoolBrowserLease.enter()
                    try {
                        app.schoolSync.editConnection {
                            val old = withContext(Dispatchers.IO) { app.schoolSecrets.powerAccount() }
                            val actualPassword = password.ifEmpty { if (old?.username == username.trim()) old.password else "" }
                            if (username.isNotBlank()) require(actualPassword.isNotEmpty())
                            val changed = old != null && username.isNotBlank() && old.username != username.trim()
                            if (changed) {
                                suspendCancellableCoroutine<Unit> { next -> CookieManager.getInstance().removeAllCookies { if (next.isActive) next.resume(Unit) } }
                                WebStorage.getInstance().deleteAllData()
                                withContext(Dispatchers.IO) { app.repository.clearPowerCache() }
                            }
                            withContext(Dispatchers.IO) {
                                app.schoolSecrets.savePowerLink(if (changed) CampusSource.POWER.entryUrl else requireNotNull(link))
                                if (username.isNotBlank()) app.schoolSecrets.savePowerAccount(SchoolAccount(username.trim(), actualPassword))
                            }
                        }
                        password = ""; onConnected()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "连接信息未保存，请检查链接；填写新学号时也需要填写独立后勤密码。" }
                    finally { SchoolBrowserLease.leave(); saving = false }
                }
            }) { Text(if (saving) "正在保存…" else "保存到手机并连接") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("稍后") } },
    )
}
