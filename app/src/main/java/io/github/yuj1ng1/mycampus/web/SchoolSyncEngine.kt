package io.github.yuj1ng1.mycampus.web

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

object SchoolBrowserLease {
    private val count = AtomicInteger(0)
    val active: Boolean get() = count.get() > 0
    fun enter() { count.incrementAndGet() }
    fun leave() { count.updateAndGet { (it - 1).coerceAtLeast(0) } }
}
class SchoolBrowserBusy : Exception()

/** Uses the same app cookie jar and the actual page extractor. No credentials in WorkManager input or HTTP reimplementations. */
class SchoolSyncEngine(private val context: Context, private val options: SchoolPreferences, private val secrets: SchoolSecrets) {
    suspend fun fetch(source: CampusSource, requestedScope: String?): Capture = withContext(Dispatchers.Main.immediate) {
        if (SchoolBrowserLease.active) throw SchoolBrowserBusy()
        val view = WebView(context)
        SchoolWebSettings.apply(view)
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1800, android.view.View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 1800)
        var loading = true
        var generation = 0
        var failure: CaptureException? = null
        val auth = SchoolLoginSession(context, options, { secrets.account() }, { secrets.powerAccount() })
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame || WebPolicy.isSchoolUrl(request.url.toString())) return false
                failure = CaptureException(if (source.isAcademic) "学校连接跳转到了尚未支持的地址，请本人打开学校页面确认。" else "后勤需要企业微信授权，请本人完成后勤接入。",
                    if (source.isAcademic) "NEEDS_LOGIN" else "NEEDS_WECOM")
                return true
            }
            override fun onPageStarted(web: WebView, url: String?, favicon: Bitmap?) { loading = true; generation++ }
            override fun onPageFinished(web: WebView, url: String?) { loading = false; CookieManager.getInstance().flush() }
            override fun onReceivedError(web: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) failure = CaptureException("学校连接暂时失败，原有数据已保留。", "NETWORK_ERROR")
            }
            override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) failure = CaptureException("学校暂时无法提供查询，原有数据已保留。", "NETWORK_ERROR")
            }
            override fun onReceivedSslError(web: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); failure = CaptureException("学校证书校验未通过，已停止连接。", "NETWORK_ERROR")
            }
        }
        view.loadUrl(source.entryUrl)
        var querySubmitted = false
        var routedLoginBody = false
        var retriedPowerAuthorization = false
        try {
            withTimeout(75_000) {
                while (true) {
                    if (SchoolBrowserLease.active) throw SchoolBrowserBusy()
                    failure?.let { throw it }
                    if (loading) { delay(500); continue }
                    val progress = auth.advance(view, source)
                    if (progress.requiresUser) throw CaptureException(progress.message, when (progress.state) { "CAPTCHA" -> "NEEDS_CAPTCHA"; "PHONE_REQUIRED" -> "NEEDS_PHONE_VERIFICATION"; else -> "NEEDS_LOGIN" })
                    if (progress.state != "NONE") { delay(700); continue }
                    val url = view.url.orEmpty()
                    val currentGeneration = generation
                    if (source.matchesResultUrl(url)) {
                        val root = SchoolScripts.evaluate(view, SchoolScripts.capture(context, source))
                        if (generation != currentGeneration || view.url != url) { delay(300); continue }
                        val state = root.optString("status")
                        if (state == "ok" || state == "empty") {
                            val capture = CaptureReader.read(source, url, root.toString())
                            val sameScope = requestedScope == null || if (source.isAcademic) SchoolScripts.sameAcademicScope(requestedScope, capture.scope) else requestedScope == capture.scope
                            if (sameScope) return@withTimeout capture
                        }
                        if (state == "login") {
                            if (!source.isAcademic) {
                                val link = secrets.powerLink()
                                if (!retriedPowerAuthorization && link != null) {
                                    retriedPowerAuthorization = true
                                    view.loadUrl(link)
                                    delay(500); continue
                                }
                                throw CaptureException("学校仍要求手机号验证或绑定，请本人打开后勤连接确认，原有数据已保留。", "NEEDS_PHONE_VERIFICATION")
                            }
                            if (routedLoginBody) throw CaptureException("学校登录尚未完成，请本人确认。", "NEEDS_LOGIN")
                            routedLoginBody = true
                            view.loadUrl(WebPolicy.SSO_URL)
                            delay(500); continue
                        }
                        if (state == "partial") CaptureReader.read(source, url, root.toString())
                        if (!querySubmitted && state != "loading") {
                            val action = SchoolScripts.evaluate(view, SchoolScripts.query(context, source, requestedScope)).optString("state")
                            if (action == "query_submitted") querySubmitted = true
                            if (action == "selection_required") throw CaptureException("请本人打开学校页面，选择学期或房间完成一次查询。", "NEEDS_SELECTION")
                        }
                    }
                    delay(650)
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
        } catch (_: TimeoutCancellationException) {
            throw CaptureException(if (source.isAcademic) "本次自动更新超时，原有数据已保留。" else "后勤授权或房间查询尚未完成，请本人检查后勤接入。",
                if (source.isAcademic) "NETWORK_ERROR" else "NEEDS_WECOM")
        } finally { view.stopLoading(); view.destroy() }
    }
}
