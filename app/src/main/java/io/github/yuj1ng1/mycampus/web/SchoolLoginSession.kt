package io.github.yuj1ng1.mycampus.web

import android.content.Context
import android.os.SystemClock
import android.webkit.WebView
import io.github.yuj1ng1.mycampus.data.SchoolAccount
import io.github.yuj1ng1.mycampus.data.SchoolPreferences
import io.github.yuj1ng1.mycampus.domain.*
import java.net.URI

data class AuthProgress(val state: String, val message: String = "") {
    val requiresUser get() = state in setOf("LOGIN_REQUIRED", "CAPTCHA", "REJECTED", "PHONE_REQUIRED")
}

/** One academic submission per origin. HQ only pre-fills identity fields; the user controls SMS and binding. */
class SchoolLoginSession(
    private val context: Context,
    private val options: SchoolPreferences,
    private val accountProvider: () -> SchoolAccount?,
    private val powerAccountProvider: () -> SchoolAccount? = { null },
) {
    private val submitted = mutableMapOf<String, Long>()
    private var routedToSso = false
    private var routedFromPortal = false
    fun reset() { submitted.clear(); routedToSso = false; routedFromPortal = false }

    suspend fun advance(view: WebView, source: CampusSource): AuthProgress {
        val url = view.url.orEmpty()
        if (!source.isAcademic && SchoolAccessPolicy.allowsPowerIdentityFields(url)) {
            val state = SchoolScripts.evaluate(view, SchoolScripts.powerBinding(context, null)).optString("state")
            if (state == "phone_required") {
                val account = powerAccountProvider()
                if (account != null) SchoolScripts.evaluate(view, SchoolScripts.powerBinding(context, account))
                return AuthProgress("PHONE_REQUIRED", if (account != null)
                    "已填入后勤学号和密码。请本人输入手机号、接收短信验证码，核对后在学校页面点击“立即绑定”。"
                    else "请本人完成手机号验证，填写后勤独立账号并确认绑定。手机号和短信码无需发给开发者。")
            }
        }
        if (source.isAcademic && SchoolAccessPolicy.isAcademicLocalLogin(url)) {
            if (routedToSso) return AuthProgress("LOGIN_REQUIRED", "学校认证仍未完成，请在学校页面确认登录。")
            routedToSso = true
            view.loadUrl(WebPolicy.SSO_URL)
            return AuthProgress("NAVIGATING", "正在连接学校统一认证…")
        }
        val kind = SchoolAccessPolicy.credentialOrigin(url) ?: run {
            if (source.isAcademic) {
                val uri = runCatching { URI(url) }.getOrNull()
                if (uri?.host == "jwxt-443.webvpn.fjut.edu.cn" && uri.path.contains("index_initMenu")) {
                    view.loadUrl(source.entryUrl)
                    return AuthProgress("NAVIGATING", "认证完成，正在打开" + source.label + "…")
                }
            }
            return AuthProgress("NONE")
        }
        val inspect = SchoolScripts.evaluate(view, SchoolScripts.login(context, kind, null, options.current.rememberSession)).optString("state")
        if (inspect == "wait") {
            if (kind == "VPN" && source.isAcademic && !routedFromPortal) {
                val portal = SchoolScripts.evaluate(view, """(function(){return JSON.stringify({ready:document.readyState==='complete',
                    portal:!!document.querySelector('a[href*="jwxt"], a[href*="webvpn"]')&&!document.querySelector('#user_login')});})();""")
                if (portal.optBoolean("ready") && portal.optBoolean("portal")) {
                    routedFromPortal = true
                    view.loadUrl(WebPolicy.SSO_URL)
                    return AuthProgress("NAVIGATING", "正在连接学校统一认证…")
                }
            }
            return AuthProgress("NAVIGATING", "正在等待学校登录页面…")
        }
        if (inspect == "manual" || inspect == "rejected") {
            options.update(autoLoginPaused = true)
            return AuthProgress(if (inspect == "manual") "CAPTCHA" else "REJECTED",
                if (inspect == "manual") "学校要求验证码或额外确认，请本人在页面完成；自动登录已暂缓。" else "学校未接受本次登录，请手动核对；自动登录已暂缓。")
        }
        submitted[kind]?.let { start ->
            if (SystemClock.elapsedRealtime() - start < 12_000) return AuthProgress("AUTO_SIGNIN", "正在等待学校认证结果…")
            options.update(autoLoginPaused = true)
            return AuthProgress("LOGIN_REQUIRED", "本次自动登录未完成，已停止重试，请本人确认。")
        }
        if (options.current.autoLoginPaused) return AuthProgress("LOGIN_REQUIRED", "自动登录已暂缓，请本人登录，或在设置中更新本机保存的账号。")
        val account = accountProvider() ?: return AuthProgress("LOGIN_REQUIRED", "请在学校页面登录；也可在设置中选择加密保存账号以便自动重登。")
        val state = SchoolScripts.evaluate(view, SchoolScripts.login(context, kind, account, options.current.rememberSession)).optString("state")
        return when (state) {
            "submitted" -> { submitted[kind] = SystemClock.elapsedRealtime(); AuthProgress("AUTO_SIGNIN", "正在使用本机保存的账号连接学校…") }
            "manual" -> { options.update(autoLoginPaused = true); AuthProgress("CAPTCHA", "学校要求验证码，请本人完成。") }
            "rejected" -> { options.update(autoLoginPaused = true); AuthProgress("REJECTED", "学校未接受登录，请本人核对。") }
            else -> AuthProgress("NAVIGATING", "正在等待学校认证页面…")
        }
    }
}
