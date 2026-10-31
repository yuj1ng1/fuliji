package io.github.yuj1ng1.mycampus

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.SchoolAccount
import io.github.yuj1ng1.mycampus.web.SchoolScripts
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LoginScriptTest {
    private val account = SchoolAccount("synthetic-user", "dummy-'\"-\\-中文-__CAMPUS_SOURCE__")
    private val form = """<input id="user_login"><input id="user_password" type="password">
        <button type="submit" onclick="document.body.dataset.clicks='1'">Login</button>"""

    private fun runPage(url: String, html: String, power: Boolean = false): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val script = if (power) SchoolScripts.powerBinding(context, account) else SchoolScripts.login(context, "VPN", account, true)
        val done = CountDownLatch(1)
        val result = AtomicReference<JSONObject>()
        val error = AtomicReference<Throwable>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: WebView
            scenario.onActivity { activity ->
                view = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(web: WebView, ignored: String?) {
                            web.evaluateJavascript(script) { encoded ->
                                try {
                                    val state = JSONObject(JSONTokener(encoded).nextValue() as String).getString("state")
                                    web.evaluateJavascript("""JSON.stringify({user:document.querySelector('#user_login,#studentid').value,
                                        password:document.querySelector('#user_password,#password').value,clicks:document.body.dataset.clicks||'0',
                                        phone:document.querySelector('#mobile')?.value||'',code:document.querySelector('#code')?.value||''});""") { values ->
                                        try { result.set(JSONObject(JSONTokener(values).nextValue() as String).put("state", state)) }
                                        catch (failure: Throwable) { error.set(failure) }
                                        done.countDown()
                                    }
                                } catch (failure: Throwable) { error.set(failure); done.countDown() }
                            }
                        }
                    }
                }
                activity.setContentView(view)
                view.loadDataWithBaseURL(url, "<!doctype html>" + html, "text/html", "UTF-8", null)
            }
            assertTrue(done.await(20, TimeUnit.SECONDS))
            scenario.onActivity { view.destroy() }
            error.get()?.let { throw AssertionError("Synthetic login script test failed", it) }
            return requireNotNull(result.get())
        }
    }

    @Test fun approvedFormUsesOrdinarySubmitAndPreservesQuotedPassword() {
        val result = runPage("https://webvpn.fjut.edu.cn/users/sign_in", form + "<div style='display:none'><input id='_rucaptcha'></div>")
        assertEquals("submitted", result.getString("state"))
        assertEquals(account.username, result.getString("user"))
        assertEquals(account.password, result.getString("password"))
        assertEquals("1", result.getString("clicks"))
    }

    @Test fun visibleCaptchaStopsBeforeFillingOrSubmitting() {
        val result = runPage("https://webvpn.fjut.edu.cn/users/sign_in", form + "<input id='_rucaptcha'>")
        assertEquals("manual", result.getString("state"))
        assertEquals("", result.getString("password"))
        assertEquals("0", result.getString("clicks"))
    }

    @Test fun lookalikeFormOnHqOrOtherHostCannotReceiveAcademicPassword() {
        for (url in listOf("https://hqwx.fjut.edu.cn/agent/app/index.php", "https://webvpn.fjut.edu.cn.evil.example/users/sign_in")) {
            val result = runPage(url, form)
            assertEquals("blocked", result.getString("state"))
            assertEquals("", result.getString("password"))
            assertEquals("0", result.getString("clicks"))
        }
    }

    private val binding = """<p>用户身份认证</p><input id="studentid"><input id="password" type="password">
        <input id="mobile"><input id="code"><button onclick="document.body.dataset.clicks='1'">获取验证码</button>
        <button onclick="document.body.dataset.clicks='2'">立即绑定</button>"""

    @Test fun powerIdentityPrefillNeverRequestsSmsOrConfirmsBinding() {
        val result = runPage("https://hqwx.fjut.edu.cn/agent/app/index.php?i=67&c=entry&do=register&m=maihu_portal", binding, power = true)
        assertEquals("phone_required", result.getString("state"))
        assertEquals(account.username, result.getString("user"))
        assertEquals(account.password, result.getString("password"))
        assertEquals("", result.getString("phone"))
        assertEquals("", result.getString("code"))
        assertEquals("0", result.getString("clicks"))
    }

    @Test fun bindingPrefillCannotFillOtherHostsOrBackendLogin() {
        for (url in listOf("https://example.com/agent/app/index.php?i=67&c=entry", "https://hqwx.fjut.edu.cn/agent/web/index.php?c=user&a=login")) {
            val result = runPage(url, binding, power = true)
            assertEquals("blocked", result.getString("state"))
            assertEquals("", result.getString("password"))
        }
    }
}
