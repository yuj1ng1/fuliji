package io.github.yuj1ng1.mycampus.web

import android.content.Context
import android.webkit.WebView
import io.github.yuj1ng1.mycampus.data.SchoolAccount
import io.github.yuj1ng1.mycampus.domain.CampusSource
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SchoolScripts {
    fun capture(context: Context, source: CampusSource): String = context.assets.open("capture.js").bufferedReader().use { it.readText() }
        .replace("__CAMPUS_SOURCE__", source.name)

    fun login(context: Context, kind: String, account: SchoolAccount?, remember: Boolean): String {
        require(kind in setOf("VPN", "SSO"))
        val script = context.assets.open("login.js").bufferedReader().use { it.readText() }
        val config = JSONObject().put("kind", kind).put("username", account?.username.orEmpty()).put("password", account?.password.orEmpty())
            .put("remember", remember).put("inspectOnly", account == null)
        return "(" + script + ")(" + config.toString() + ");"
    }

    fun query(context: Context, source: CampusSource, scope: String?): String {
        val term = scope?.let { Regex("(\\d{4})[-—](\\d{4}).*?第?([123])学期").find(it) }
        val config = JSONObject().put("source", source.name)
            .put("year", term?.let { it.groupValues[1] + "-" + it.groupValues[2] }.orEmpty())
            .put("term", term?.groupValues?.get(3).orEmpty()).put("room", if (source.isAcademic) "" else scope.orEmpty())
        val script = context.assets.open("query.js").bufferedReader().use { it.readText() }
        return "(" + script + ")(" + config.toString() + ");"
    }

    fun powerBinding(context: Context, account: SchoolAccount?): String {
        val script = context.assets.open("power-binding.js").bufferedReader().use { it.readText() }
        val config = JSONObject().put("inspectOnly", account == null)
            .put("username", account?.username.orEmpty()).put("password", account?.password.orEmpty())
        return "(" + script + ")(" + config.toString() + ");"
    }

    fun powerViewport(context: Context): String = context.assets.open("power-viewport.js").bufferedReader().use { it.readText() }

    fun sameAcademicScope(first: String, second: String): Boolean {
        fun identity(value: String) = Regex("(\\d{4})[-—](\\d{4}).*?第?([123])学期").find(value)?.groupValues?.drop(1)
        val one = identity(first)
        return one != null && one == identity(second)
    }

    /** Call on Main. The page has no native JS bridge. */
    suspend fun evaluate(view: WebView, script: String): JSONObject = suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript(script) { encoded ->
            if (!continuation.isActive) return@evaluateJavascript
            try {
                val raw = JSONTokener(encoded).nextValue() as? String ?: error("学校页面暂时无法读取。")
                continuation.resume(JSONObject(raw))
            } catch (_: Exception) { continuation.resumeWithException(IllegalStateException("学校页面暂时无法读取。")) }
        }
    }
}
