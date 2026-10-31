package io.github.yuj1ng1.mycampus.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.yuj1ng1.mycampus.BuildConfig
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.ui.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI

class CampusWebActivity : ComponentActivity() {
    private lateinit var schoolView: WebView
    private lateinit var source: CampusSource
    private lateinit var login: SchoolLoginSession
    private var loading by mutableStateOf(true)
    private var saving by mutableStateOf(false)
    private var message by mutableStateOf("正在打开学校页面…")
    private var host by mutableStateOf("")
    private var resultPage by mutableStateOf(false)
    private var powerAccess by mutableStateOf(false)
    private var showDiagnostics by mutableStateOf(false)
    private var diagnostics by mutableStateOf("{}")
    private var navigationGeneration = 0
    private var pageStartedAt = 0L
    private var fatalPageError = false
    private var checking = false
    private var pendingSave = false
    private var preparedPowerTab = false
    private var redirectedBodyLogin = false
    private var lastFailure = ""
    private var browserLease = false
    private val app get() = application as CampusApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = runCatching { CampusSource.valueOf(intent.getStringExtra("source").orEmpty()) }.getOrNull()
            ?: run { finish(); return }
        SchoolBrowserLease.enter()
        browserLease = true
        enableEdgeToEdge()
        login = SchoolLoginSession(this, app.schoolOptions, { app.schoolSecrets.account() }, { app.schoolSecrets.powerAccount() })
        schoolView = WebView(this)
        SchoolWebSettings.apply(schoolView)
        schoolView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame || WebPolicy.isSchoolUrl(request.url.toString())) return false
                resultPage = false
                if (!source.isAcademic) message = "此后勤入口需要独立授权，请使用“后勤接入”。"
                else message = "学校跳转到当前连接范围外：" + request.url.host.orEmpty()
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                navigationGeneration++
                pageStartedAt = SystemClock.elapsedRealtime()
                resultPage = false
                fatalPageError = false
                loading = true
                preparedPowerTab = false
                host = runCatching { URI(url.orEmpty()).host.orEmpty() }.getOrDefault("")
                message = "正在加载学校页面…"
            }
            override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                CookieManager.getInstance().flush()
                if (!fatalPageError) message = if (source.isAcademic) "请完成学校查询，识别到完整结果后即可保存。" else "请完成后勤独立登录并选择宿舍，识别到结果后即可保存。"
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) pageFailed("NETWORK_ERROR", "学校页面暂时无法打开，请检查网络后重试。")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) pageFailed("NETWORK_ERROR", "学校页面返回访问错误（" + response.statusCode + "），原有数据已保留。")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                pageFailed("NETWORK_ERROR", "学校证书验证未通过，已停止连接。")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                pageFailed("NETWORK_ERROR", "学校网页进程已退出，请返回后重新打开。")
                android.widget.Toast.makeText(this@CampusWebActivity, "学校网页进程已退出，请重新打开。", android.widget.Toast.LENGTH_LONG).show()
                finish()
                return true
            }
        }
        schoolView.webChromeClient = WebChromeClient()
        setContent {
            CampusTheme {
                Scaffold(topBar = {
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { finish() }, enabled = !saving) { Text("返回") }
                            TextButton(onClick = {
                                if (source.isAcademic) {
                                    app.schoolOptions.update(autoLoginPaused = false)
                                    login.reset()
                                    redirectedBodyLogin = false
                                    schoolView.loadUrl(WebPolicy.SSO_URL)
                                } else powerAccess = true
                            }, enabled = !saving) { Text(if (source.isAcademic) "学校登录" else "后勤接入") }
                            TextButton(onClick = { loadEntry() }, enabled = !saving) { Text("重新打开") }
                        }
                        Text(source.label + " · " + host, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
                        Text(message, Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            pendingSave = true
                            lifecycleScope.launch { inspectPage() }
                        }, enabled = !loading && !saving && resultPage,
                            modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp)) { Text(if (saving) "正在保存…" else "读取并保存") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showDiagnostics = true }, contentPadding = PaddingValues(12.dp, 0.dp), modifier = Modifier.height(30.dp)) {
                                Text("连接诊断", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }) { padding ->
                    AndroidView(factory = { schoolView }, modifier = Modifier.fillMaxSize().padding(padding))
                }
                BackHandler { if (schoolView.canGoBack()) schoolView.goBack() else finish() }
                if (powerAccess) PowerAccessDialog(app,
                    onConnected = { powerAccess = false; loadEntry(useSavedAuthorization = true) }, onDismiss = { powerAccess = false })
                if (showDiagnostics) AlertDialog(
                    onDismissRequest = { showDiagnostics = false }, title = { Text("连接诊断") },
                    text = { Text("仅包含页面状态和数量，不包含账号、课程内容、Cookie或授权链接。\n\n" + diagnostics, style = MaterialTheme.typography.bodySmall) },
                    confirmButton = { TextButton(onClick = {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("榕集连接诊断", diagnostics))
                        showDiagnostics = false
                    }) { Text("复制诊断") } },
                    dismissButton = { TextButton(onClick = { showDiagnostics = false }) { Text("返回") } },
                )
            }
        }
        loadEntry(useSavedAuthorization = !source.isAcademic && intent.getBooleanExtra("authorize", false))
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (!loading && !saving && !fatalPageError) inspectPage()
                    delay(900)
                }
            }
        }
    }

    private fun loadEntry(useSavedAuthorization: Boolean = false) {
        pendingSave = false
        redirectedBodyLogin = false
        try { schoolView.loadUrl(if (!source.isAcademic && useSavedAuthorization) app.schoolSecrets.powerLink() ?: source.entryUrl else source.entryUrl) }
        catch (_: Exception) { message = "本机授权暂时无法读取，请重新完成后勤接入。"; powerAccess = !source.isAcademic }
    }

    private suspend fun inspectPage() {
        if (checking || loading || saving || fatalPageError || isFinishing) return
        checking = true
        try {
            val progress = withTimeout(10_000) { login.advance(schoolView, source) }
            if (progress.state != "NONE") {
                resultPage = false
                message = progress.message
                if (progress.state == "PHONE_REQUIRED") fail("NEEDS_PHONE_VERIFICATION", progress.message)
                return
            }
            val url = schoolView.url.orEmpty()
            if (!source.matchesResultUrl(url)) return
            val generation = navigationGeneration
            if (!source.isAcademic && source == CampusSource.POWER_USAGE && !preparedPowerTab) {
                val action = SchoolScripts.evaluate(schoolView, SchoolScripts.query(this, source, null))
                preparedPowerTab = action.optString("state") != "unavailable"
            }
            val root = withTimeout(10_000) { SchoolScripts.evaluate(schoolView, SchoolScripts.capture(this@CampusWebActivity, source)) }
            if (generation != navigationGeneration || schoolView.url != url || isFinishing) return
            val state = root.optString("status")
            val info = JSONObject().put("app", BuildConfig.VERSION_NAME).put("source", source.name)
                .put("host", host).put("webView", WebView.getCurrentWebViewPackage()?.versionName.orEmpty())
                .put("status", state).put("counts", root.optJSONObject("diagnostics") ?: JSONObject())
            diagnostics = info.toString(2)
            if (state == "login") {
                resultPage = false
                pendingSave = false
                if (source.isAcademic && !redirectedBodyLogin) {
                    redirectedBodyLogin = true
                    schoolView.loadUrl(WebPolicy.SSO_URL)
                } else if (!source.isAcademic) {
                    fail("NEEDS_PHONE_VERIFICATION", "后勤需要本人完成手机号验证与身份认证，请在学校页面操作；可从“后勤接入”保存独立后勤账号。")
                } else message = "学校登录尚未完成，请在学校页面确认。"
                return
            }
            if (state == "not_queried" || state == "loading" || state == "unknown") {
                resultPage = false
                if (!source.isAcademic && SystemClock.elapsedRealtime() - pageStartedAt > 20_000 && state == "unknown") {
                    message = "后勤页面尚未完成授权或加载，请点“后勤接入”或重新打开。"
                } else message = if (source.isAcademic) "请先选择学期并完成学校页面的查询，结果加载后即可保存。" else "请先选择宿舍并完成查询；用量请切换到“房间用量”。"
                return
            }
            val capture = CaptureReader.read(source, url, root.toString())
            resultPage = true
            if (!pendingSave) {
                message = if (capture.empty) "本次查询已确认无记录，可以保存。" else "已识别完整结果，可点击“读取并保存”。"
                return
            }
            pendingSave = false
            saving = true
            try {
                withContext(Dispatchers.IO) { app.repository.saveCapture(source, capture) }
                CookieManager.getInstance().flush()
                finish()
            } finally { saving = false }
        } catch (_: TimeoutCancellationException) {
            resultPage = false
            pendingSave = false
            message = "本次读取超时，请重新打开或查看连接诊断；旧数据已保留。"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (problem: Exception) {
            resultPage = false
            pendingSave = false
            val captureProblem = problem as? CaptureException
            fail(captureProblem?.state ?: "UNRECOGNIZED", captureProblem?.userMessage ?: "当前学校页面暂时无法读取，原有数据已保留。")
        } finally { checking = false }
    }

    private fun pageFailed(state: String, text: String) {
        loading = false
        resultPage = false
        fatalPageError = true
        fail(state, text)
    }

    private fun fail(state: String, text: String) {
        message = text
        val key = navigationGeneration.toString() + "|" + state + "|" + text
        if (key == lastFailure) return
        lastFailure = key
        app.applicationScope.launch {
            try { app.repository.recordFailure(source, state, text) }
            catch (_: Exception) { /* The visible error remains; never log school content or URLs. */ }
        }
    }

    override fun onResume() { super.onResume(); if (::schoolView.isInitialized) schoolView.onResume() }
    override fun onPause() { if (::schoolView.isInitialized) schoolView.onPause(); super.onPause() }
    override fun onDestroy() {
        if (::schoolView.isInitialized) { schoolView.stopLoading(); schoolView.destroy() }
        if (browserLease) { SchoolBrowserLease.leave(); browserLease = false }
        super.onDestroy()
    }
    companion object {
        fun open(context: Context, source: CampusSource, authorize: Boolean = false) {
            context.startActivity(Intent(context, CampusWebActivity::class.java).putExtra("source", source.name).putExtra("authorize", authorize))
        }
    }
}
