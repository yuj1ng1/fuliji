package io.github.yuj1ng1.mycampus.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.domain.SchoolAccessPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var input by remember { mutableStateOf(initialText.take(16_384)) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var hasSaved by remember { mutableStateOf(app.schoolSecrets.hasPowerLink()) }
    val link = SchoolAccessPolicy.extractPowerLink(input)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, title = { Text("企业微信后勤接入") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("智慧后勤通过企业微信识别身份，不能假设网页后台使用教务密码。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { openEnterpriseWeChat(context) }, modifier = Modifier.fillMaxWidth()) { Text("打开企业微信") }
                Text("1. 企业微信工作台 → 智慧后勤 → 水电充值。\n2. 等页面加载完成，从右上角复制完整链接。\n3. 仅在下面粘贴，或从系统分享中选择榕集。", style = MaterialTheme.typography.bodySmall)
                Text("授权链接可能包含登录凭据，请勿发到聊天或公开仓库。这里只在此手机加密保存。链接能否跨应用复用、何时过期由学校决定。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(input, { input = it.take(16_384) }, label = { Text("粘贴后勤授权查询链接") },
                    visualTransformation = PasswordVisualTransformation(), minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Text(if (link != null) "已识别到学校后勤授权链接。" else "需要加载完成后的授权链接，普通入口地址不包含登录状态。",
                    style = MaterialTheme.typography.bodySmall, color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                if (hasSaved) OutlinedButton(onClick = onConnected, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("尝试已保存的后勤授权") }
                if (hasSaved) TextButton(onClick = {
                    scope.launch {
                        try { withContext(Dispatchers.IO) { app.schoolSecrets.removePowerLink() }; hasSaved = false; message = "已删除本机保存的授权链接，校园缓存保留。" }
                        catch (_: Exception) { message = "删除未完成，请重试。" }
                    }
                }) { Text("删除本机保存的授权链接") }
                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
                Text("若企业微信未提供可复制的授权链接，或学校不允许跨应用复用，请继续在企业微信查询。榕集不会读取企业微信的私有会话或绕过认证。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = link != null && !saving, onClick = {
                saving = true
                scope.launch {
                    try { withContext(Dispatchers.IO) { app.schoolSecrets.savePowerLink(requireNotNull(link)) }; onConnected() }
                    catch (_: Exception) { message = "授权链接未能保存，请重新复制完整链接。" }
                    finally { saving = false }
                }
            }) { Text(if (saving) "正在保存…" else "仅保存到本机并连接") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("稍后") } },
    )
}
