package io.github.yuj1ng1.mycampus.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
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
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.ui.CampusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.net.URI

class CampusWebActivity : ComponentActivity() {
    private lateinit var schoolView: WebView
    private lateinit var source: CampusSource
    private var loading by mutableStateOf(true)
    private var saving by mutableStateOf(false)
    private var message by mutableStateOf("请在学校页面完成登录和查询，然后读取保存。")
    private var host by mutableStateOf("")
    private var resultPage by mutableStateOf(false)
    private var navigationGeneration = 0
    private val app get() = application as CampusApplication

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = runCatching { CampusSource.valueOf(intent.getStringExtra("source").orEmpty()) }.getOrNull()
            ?: run { finish(); return }
        if (source == CampusSource.POWER_USAGE) message = "请进入“房间用量”，选择宿舍完成查询，再读取保存。"
        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(false)
        schoolView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val url = request.url.toString()
                    if (WebPolicy.isSchoolUrl(url)) return false
                    message = "已暂停打开学校连接范围外的页面：" + request.url.host.orEmpty()
                    return true
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    navigationGeneration++
                    resultPage = false
                    loading = true
                    host = runCatching { URI(url.orEmpty()).host.orEmpty() }.getOrDefault("")
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    resultPage = source.matchesResultUrl(url.orEmpty())
                    CookieManager.getInstance().flush()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        loading = false
                        resultPage = false
                        fail("NETWORK_ERROR", "学校页面暂时无法打开，请检查网络后重试。")
                    }
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        loading = false
                        resultPage = false
                        fail("NETWORK_ERROR", "学校页面返回访问错误（" + errorResponse.statusCode + "），原有数据已保留。")
                    }
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    loading = false
                    resultPage = false
                    fail("NETWORK_ERROR", "学校页面的证书验证未通过，已停止连接。")
                }
            }
            webChromeClient = WebChromeClient()
        }
        setContent {
            CampusTheme {
                Scaffold(
                    topBar = {
                        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { finish() }) { Text("返回") }
                                TextButton(onClick = { schoolView.loadUrl(WebPolicy.VPN_URL) }) { Text("学校登录") }
                                TextButton(onClick = { schoolView.loadUrl(source.entryUrl) }) { Text("打开" + source.label) }
                            }
                            Text(source.label + " · " + host, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
                            Text(message, Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { capturePage() }, enabled = !loading && !saving && resultPage,
                                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                            ) { Text(if (saving) "正在保存…" else "读取并保存") }
                            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    },
                ) { padding ->
                    AndroidView(factory = { schoolView }, modifier = Modifier.fillMaxSize().padding(padding))
                }
                BackHandler { if (schoolView.canGoBack()) schoolView.goBack() else finish() }
            }
        }
        schoolView.loadUrl(source.entryUrl)
    }

    private fun capturePage() {
        val url = schoolView.url.orEmpty()
        if (!source.matchesResultUrl(url)) {
            fail("NEEDS_LOGIN", "请先完成学校登录，并打开对应的查询页面。")
            return
        }
        val generation = navigationGeneration
        saving = true
        val script = assets.open("capture.js").bufferedReader().use { it.readText() }
            .replace("__CAMPUS_SOURCE__", source.name)
        schoolView.evaluateJavascript(script) { encoded ->
            if (generation != navigationGeneration || schoolView.url != url || isFinishing) {
                saving = false
                message = "页面已经切换，请在新页面查询后重新读取。"
                return@evaluateJavascript
            }
            val capture = try {
                val raw = JSONTokener(encoded).nextValue() as? String
                    ?: throw CaptureException("当前页面暂时无法读取。")
                CaptureReader.read(source, url, raw)
            } catch (exception: Exception) {
                saving = false
                val problem = exception as? CaptureException
                fail(problem?.state ?: "UNRECOGNIZED", problem?.userMessage ?: "当前页面暂时无法读取，原有数据已保留。")
                return@evaluateJavascript
            }
            app.applicationScope.launch {
                try {
                    app.repository.saveCapture(source, capture)
                    withContext(Dispatchers.Main) { saving = false; finish() }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { saving = false; message = "本地保存失败，请稍后重试。" }
                }
            }
        }
    }

    private fun fail(state: String, displayMessage: String) {
        message = displayMessage
        app.applicationScope.launch {
            try { app.repository.recordFailure(source, state, displayMessage) }
            catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing) message = displayMessage + "\n本地状态暂时无法保存。"
                }
            }
        }
    }

    override fun onResume() { super.onResume(); if (::schoolView.isInitialized) schoolView.onResume() }
    override fun onPause() { if (::schoolView.isInitialized) schoolView.onPause(); super.onPause() }
    override fun onDestroy() {
        if (::schoolView.isInitialized) { schoolView.stopLoading(); schoolView.destroy() }
        super.onDestroy()
    }

    companion object {
        fun open(context: Context, source: CampusSource) {
            context.startActivity(Intent(context, CampusWebActivity::class.java).putExtra("source", source.name))
        }
    }
}
