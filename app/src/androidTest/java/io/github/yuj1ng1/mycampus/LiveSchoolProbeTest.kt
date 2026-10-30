package io.github.yuj1ng1.mycampus

import android.webkit.*
import androidx.room.withTransaction
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.data.SchoolAccount
import io.github.yuj1ng1.mycampus.data.SchoolPreferences
import io.github.yuj1ng1.mycampus.data.SchoolSecrets
import io.github.yuj1ng1.mycampus.web.SchoolScripts
import io.github.yuj1ng1.mycampus.web.SchoolSyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Explicit opt-in probe. Runtime secrets arrive over an adb-forwarded loopback socket, never in the APK or files. */
@RunWith(AndroidJUnit4::class)
class LiveSchoolProbeTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun inspectAuthorizedSchoolDocument() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("live_probe") == "yes")
        val payload = ServerSocket(37991, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 90_000
            server.accept().use { socket ->
                socket.soTimeout = 10_000
                val input = DataInputStream(socket.getInputStream())
                val size = input.readInt()
                require(size in 1..8192)
                val bytes = ByteArray(size).also(input::readFully)
                JSONObject(String(bytes, Charsets.UTF_8)).also {
                    require(it.optString("nonce") == args.getString("probe_nonce"))
                }
            }
        }
        val target = payload.optString("url", CampusSource.COURSES.entryUrl)
        require(WebPolicy.isSchoolUrl(target))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (payload.optBoolean("activity")) {
            val app = context.applicationContext as CampusApplication
            val temporaryAccount = payload.optString("username").isNotEmpty()
            if (temporaryAccount) {
                check(!app.schoolSecrets.hasAccount()) { "Probe must not replace a saved account" }
                app.schoolSecrets.saveAccount(SchoolAccount(payload.getString("username"), payload.getString("password")))
                app.schoolOptions.update(autoLoginPaused = false)
            }
            val intent = android.content.Intent(context, io.github.yuj1ng1.mycampus.web.CampusWebActivity::class.java)
                .putExtra("source", "COURSES").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
            ActivityScenario.launch<io.github.yuj1ng1.mycampus.web.CampusWebActivity>(intent).use { scenario ->
                var clicked = false
                repeat(140) { attempt ->
                    if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) return@repeat
                    if (attempt % 10 == 0) scenario.onActivity { activity ->
                        val field = activity.javaClass.getDeclaredField("schoolView").apply { isAccessible = true }
                        val view = field.get(activity) as WebView
                        val uri = android.net.Uri.parse(view.url.orEmpty())
                        println("SCHOOL_PROBE activityHost=" + uri.host + ";path=" + uri.path + ";clicked=" + clicked)
                    }
                    if (!clicked) {
                        try { ui.onNodeWithText("读取并保存", useUnmergedTree = false).assertIsEnabled().performClick(); clicked = true }
                        catch (_: AssertionError) { /* The visible result may still be loading. */ }
                    }
                    if (clicked && scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) return@repeat
                    Thread.sleep(500)
                }
                check(clicked && scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) { "Visible read/save flow did not finish" }
                val saved = runBlocking { app.repository.snapshots.first { list -> list.any { it.source == "COURSES" } } }
                    .first { it.source == "COURSES" }
                println("SCHOOL_PROBE activitySavedRows=" + saved.itemCount)
                check(saved.itemCount > 0)
                // This opt-in probe uses the owned emulator. Remove this one real capture after checking counts.
                runBlocking { app.database.withTransaction { app.database.openHelper.writableDatabase.execSQL("DELETE FROM campus_snapshots WHERE id = ?", arrayOf(saved.id)) } }
            }
            } finally { if (temporaryAccount) app.schoolSecrets.removeAccount() }
            return
        }
        if (payload.optBoolean("engine")) {
            val namespace = "rongji.live.probe"
            val secrets = SchoolSecrets(context, namespace)
            val options = SchoolPreferences(context, namespace + ".options")
            try {
                val source = CampusSource.valueOf(payload.optString("source", "COURSES"))
                if (payload.optString("username").isNotBlank()) secrets.saveAccount(SchoolAccount(payload.getString("username"), payload.getString("password")))
                if (SchoolAccessPolicy.isSignedPowerLink(target)) secrets.savePowerLink(target)
                val engine = SchoolSyncEngine(context, options, secrets)
                val capture = runBlocking { engine.fetch(source, payload.optString("scope").takeIf(String::isNotBlank)) }
                println("SCHOOL_PROBE engineRows=" + capture.rows.size + ";source=" + source.name)
                check(capture.rows.isNotEmpty())
                if (payload.optBoolean("reuseWithoutPassword")) {
                    secrets.removeAccount()
                    val again = runBlocking { engine.fetch(source, capture.scope) }
                    println("SCHOOL_PROBE reusedWithoutPasswordRows=" + again.rows.size)
                    check(again.rows.isNotEmpty())
                }
            } finally {
                secrets.removeAccount(); secrets.removePowerLink()
                context.deleteSharedPreferences(namespace)
                context.deleteSharedPreferences(namespace + ".options")
                java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(namespace + ".aes") }
            }
            return
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: WebView
            scenario.onActivity { activity ->
                view = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    if (payload.optBoolean("desktop")) settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    if (payload.optBoolean("thirdParty")) CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                            return request.isForMainFrame && !WebPolicy.isSchoolUrl(request.url.toString())
                        }
                    }
                }
                activity.setContentView(view)
                view.loadUrl(target)
            }
            val attempts = mutableSetOf<String>()
            var lastSummary = ""
            var success = false
            try {
                repeat(150) {
                    if (success) return@repeat
                    val state = evaluate(scenario, view, """(function(){
                        var t=document.querySelector('#kbgrid_table_0');
                        var inputs=Array.from(document.querySelectorAll('input')).filter(function(e){return e.type!=='hidden'}).map(function(e){return {id:e.id,name:e.name,type:e.type,visible:e.getBoundingClientRect().width>0}});
                        var callbackTypes=[];[document.body].filter(function(a,b){callbackTypes.push(typeof a+','+typeof b);return true;});
                        var block=t&&t.querySelector('.timetable_con');var structure=[];
                        if(block)for(var n=0;n<block.childNodes.length;n++){var child=block.childNodes[n];structure.push({tag:child.nodeType===1?child.tagName:'#text',cls:child.className||'',length:child.textContent.trim().length,hasTime:/节/.test(child.textContent),children:child.childNodes.length});}
                        return JSON.stringify({host:location.hostname,path:location.pathname,filterCallbackTypes:callbackTypes,bodyLength:document.body?document.body.textContent.length:0,
                            table:!!t,cells:t?t.querySelectorAll('td[id]').length:0,blocks:t?t.querySelectorAll('.timetable_con').length:0,
                            structure:structure,inputs:inputs,power:!!document.querySelector('#picker-addr'),loginError:/用户名或密码错误|密码错误|账号.*锁定/.test(document.body?document.body.innerText:'')});
                    })();""")
                    val summary = state.toString()
                    if (summary != lastSummary) { println("SCHOOL_PROBE " + summary); lastSummary = summary }
                    val host = state.optString("host")
                    val path = state.optString("path")
                    if (state.optBoolean("table") && state.optInt("blocks") > 0) {
                        val script = context.assets.open("capture.js").bufferedReader().use { it.readText() }.replace("__CAMPUS_SOURCE__", "COURSES")
                        val raw = evaluate(scenario, view, script)
                        println("SCHOOL_PROBE extractorCells=" + (raw.optJSONArray("cells")?.length() ?: -1) + ";status=" + raw.optString("status"))
                        val array = raw.optJSONArray("cells")
                        var shapeCount = 0
                        if (array != null) for (index in 0 until array.length()) {
                            for (line in array.getJSONObject(index).optString("text").lines()) {
                                if ("节)" in line && shapeCount < 6) {
                                    val shape = line.map { char -> when {
                                        char.isDigit() -> "#"
                                        char in "()（）节周单双-,，" -> char.toString()
                                        char.isWhitespace() -> "_"
                                        char.isLetter() -> "字"
                                        else -> "[" + char.code.toString(16) + "]"
                                    } }.joinToString("")
                                    println("SCHOOL_PROBE ruleShape=" + shape)
                                    shapeCount++
                                }
                            }
                        }
                        val capture = CaptureReader.read(CampusSource.COURSES, CampusSource.COURSES.entryUrl, raw.toString())
                        println("SCHOOL_PROBE parsedRows=" + capture.rows.size)
                        check(capture.rows.isNotEmpty())
                        success = true
                    } else if (host == "jwxt-443.webvpn.fjut.edu.cn" && path.endsWith("login_slogin.html")) {
                        scenario.onActivity { view.loadUrl("https://nids-443.webvpn.fjut.edu.cn/authserver/login?service=https%3A%2F%2Fjwxt-443.webvpn.fjut.edu.cn%2Fsso%2Fjziotlogin") }
                    } else if (host in setOf("webvpn.fjut.edu.cn", "nids-443.webvpn.fjut.edu.cn") && !state.optBoolean("loginError") && host !in attempts) {
                        val kind = SchoolAccessPolicy.credentialOrigin("https://" + host + path + if (host.startsWith("nids-")) "?service=https%3A%2F%2Fjwxt-443.webvpn.fjut.edu.cn%2Fsso%2Fjziotlogin" else "")
                            ?: error("Unapproved login origin")
                        val login = SchoolScripts.login(context, kind, SchoolAccount(payload.optString("username"), payload.optString("password")), false)
                        val result = evaluate(scenario, view, login)
                        if (result.optString("state") == "submitted") attempts.add(host)
                        if (result.optString("state") == "manual") error("School requires manual verification")
                    } else if (host == "jwxt-443.webvpn.fjut.edu.cn" && path.contains("index_initMenu")) {
                        scenario.onActivity { view.loadUrl(CampusSource.COURSES.entryUrl) }
                    } else if (state.optBoolean("power")) {
                        println("SCHOOL_PROBE powerDocumentAvailable=true")
                        success = true
                    }
                    if (!success) Thread.sleep(800)
                }
                check(success) { "Authorized document was not reached; see redacted SCHOOL_PROBE stages" }
            } finally { scenario.onActivity { view.stopLoading(); view.destroy() } }
        }
    }

    private fun evaluate(scenario: ActivityScenario<MainActivity>, view: WebView, script: String): JSONObject {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        scenario.onActivity { view.evaluateJavascript(script) { result.set(it); done.countDown() } }
        check(done.await(10, TimeUnit.SECONDS)) { "WebView evaluation timed out" }
        return JSONObject(JSONTokener(result.get()).nextValue() as String)
    }
}
