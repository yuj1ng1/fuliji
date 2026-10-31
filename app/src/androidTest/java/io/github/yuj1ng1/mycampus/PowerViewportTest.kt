package io.github.yuj1ng1.mycampus

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.web.CampusWebActivity
import io.github.yuj1ng1.mycampus.web.SchoolScripts
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PowerViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bindingUrl = "https://hqwx.fjut.edu.cn/agent/app/index.php?i=67&c=entry&do=register&m=maihu_portal"
    private fun document(height: String) = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html{height:100%}body{position:absolute;margin:0;width:100%;height:$height;overflow:hidden}
        .page-group{position:relative;height:100%}.page{position:relative;height:100%;overflow:hidden}
        .content{position:absolute;top:0;bottom:0;left:0;right:0;overflow:auto}input{display:block;height:40px;margin:12px;width:80%}</style>
        <div class="page-group"><div id="register-page" class="page page-current"><div class="content native-scroll">
        <p>合成绑定表单 · 用户身份认证</p><input id="mobile"><input id="code"><input id="studentid"><input id="password" type="password">
        <button onclick="document.body.dataset.actions='1'">获取验证码</button><button onclick="document.body.dataset.actions='2'">立即绑定</button>
        </div></div></div>"""

    private fun evaluate(scenario: ActivityScenario<CampusWebActivity>, view: WebView, script: String): JSONObject {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        scenario.onActivity { view.evaluateJavascript(script) { result.set(it); done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        val raw = JSONTokener(result.get()).nextValue()
        return if (raw is String) JSONObject(raw) else JSONObject()
    }

    private val geometry = """(function(){var p=document.querySelector('#register-page'),c=p&&p.querySelector('.content'),e=document.querySelector('#mobile');
        var r=e&&e.getBoundingClientRect();return JSON.stringify({hit:!!r&&document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===e,
        body:document.body.getBoundingClientRect().height,page:p?p.getBoundingClientRect().height:0,content:c?c.getBoundingClientRect().height:0,
        adapted:document.documentElement.classList.contains('fuliji-power-viewport'),actions:document.body.dataset.actions||'0',viewport:innerHeight});})();"""

    private fun withPage(height: String, block: (ActivityScenario<CampusWebActivity>, WebView) -> Unit) {
        val intent = Intent(context, CampusWebActivity::class.java).putExtra("source", "POWER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<CampusWebActivity>(intent).use { scenario ->
            lateinit var view: WebView
            scenario.onActivity { activity ->
                view = activity.javaClass.getDeclaredField("schoolView").apply { isAccessible = true }.get(activity) as WebView
                view.stopLoading()
                val original = view.webViewClient
                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(web: WebView, url: String?, icon: Bitmap?) = original.onPageStarted(web, url, icon)
                    override fun onPageFinished(web: WebView, url: String?) = original.onPageFinished(web, url)
                    override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse =
                        WebResourceResponse("text/html", "UTF-8", document(height).byteInputStream())
                }
                view.loadUrl(bindingUrl)
            }
            block(scenario, view)
        }
    }

    @Test fun actualAppWindowUnclipsZeroHeightFormAndPhoneCanReceiveFocus() = withPage("0px") { scenario, view ->
        var visible = false
        for (attempt in 0 until 100) {
            if (evaluate(scenario, view, geometry).optBoolean("hit")) { visible = true; break }
            Thread.sleep(100)
        }
        assertTrue("A created but clipped input is not a displayed form", visible)
        val actual = evaluate(scenario, view, geometry)
        assertTrue(actual.getDouble("page") >= actual.getDouble("viewport") - 1)
        assertTrue(actual.getDouble("content") > 0)
        assertTrue(actual.getBoolean("adapted"))
        assertEquals("0", actual.getString("actions"))
        ui.onNodeWithTag("school-web-view").assertIsDisplayed()
        ui.waitForIdle()
        val point = evaluate(scenario, view, """(function(){var r=document.querySelector('#mobile').getBoundingClientRect();return JSON.stringify({x:(r.x+r.width/2)/innerWidth,y:(r.y+r.height/2)/innerHeight});})();""")
        ui.onNodeWithTag("school-web-view").performTouchInput { click(androidx.compose.ui.geometry.Offset((width * point.getDouble("x")).toFloat(), (height * point.getDouble("y")).toFloat())) }
        var focused = false
        for (attempt in 0 until 25) {
            if (evaluate(scenario, view, """JSON.stringify({focused:document.activeElement.id==='mobile'});""").optBoolean("focused")) { focused = true; break }
            Thread.sleep(100)
        }
        assertTrue("A native touch should focus the visible phone field", focused)
        assertEquals("0", evaluate(scenario, view, geometry).getString("actions"))
    }

    @Test fun healthyPageKeepsItsSizeAndAdapterIsRemovedOutsideSupportedRoute() = withPage("450px") { scenario, view ->
        for (attempt in 0 until 100) {
            if (evaluate(scenario, view, geometry).optBoolean("hit")) break
            Thread.sleep(100)
        }
        val original = evaluate(scenario, view, geometry)
        assertTrue(original.getBoolean("hit"))
        assertFalse(original.getBoolean("adapted"))
        assertEquals(450.0, original.getDouble("body"), 1.0)
        scenario.onActivity { view.evaluateJavascript("document.body.style.height='0px';", null) }
        var adapted = JSONObject()
        for (attempt in 0 until 30) {
            adapted = evaluate(scenario, view, SchoolScripts.powerViewport(context))
            if (adapted.optString("state") == "visible") break
            Thread.sleep(100)
        }
        println("VIEWPORT_FIXTURE " + adapted)
        assertEquals("visible", adapted.optString("state"))
        scenario.onActivity { view.evaluateJavascript("history.replaceState({},'', '?i=67&c=entry&m=other&do=other');", null) }
        assertEquals("outside", evaluate(scenario, view, SchoolScripts.powerViewport(context)).getString("state"))
        assertFalse(evaluate(scenario, view, geometry).getBoolean("adapted"))
    }
}
