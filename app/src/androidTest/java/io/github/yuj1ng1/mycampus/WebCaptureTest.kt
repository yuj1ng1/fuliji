package io.github.yuj1ng1.mycampus

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.domain.*
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebCaptureTest {
    private fun extract(html: String, source: CampusSource = CampusSource.COURSES): Capture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val script = context.assets.open("capture.js").bufferedReader().use { it.readText() }
            .replace("__CAMPUS_SOURCE__", source.name)
        val done = CountDownLatch(1)
        val result = AtomicReference<Capture>()
        val failure = AtomicReference<Throwable>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: WebView
            scenario.onActivity { activity ->
                view = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(web: WebView, url: String?) {
                            web.evaluateJavascript(script) { encoded ->
                                try {
                                    val raw = JSONTokener(encoded).nextValue() as String
                                    result.set(CaptureReader.read(source, source.entryUrl, raw))
                                } catch (problem: Throwable) { failure.set(problem) }
                                done.countDown()
                            }
                        }
                    }
                }
                activity.setContentView(view)
                view.loadDataWithBaseURL(source.entryUrl, html, "text/html", "UTF-8", null)
            }
            assertTrue("WebView callback timed out", done.await(30, TimeUnit.SECONDS))
            scenario.onActivity { view.destroy() }
            failure.get()?.let { throw AssertionError("Actual WebView extractor failed: " + it.message, it) }
            return requireNotNull(result.get())
        }
    }

    @Test fun packagedScriptReadsActualWebViewDocument() {
        val capture = extract("""<!doctype html><meta name="viewport" content="width=device-width">
            <table id="kbgrid_table_0"><caption>2026-2027学年第1学期</caption>
            <tr><td id="2-1"><div class="timetable_con">合成课程A<br>(1-2节)1-16周<br>示例教室<br>示例教师</div></td></tr></table>""")
        assertEquals(1, capture.rows.size)
        assertTrue(capture.rows.single().title.contains("合成课程A"))
    }

    private val schoolArrayOverrides = """<script>
        Array.prototype.filter=function(callback){var result=[];for(var i=0;i<this.length;i++){if(callback(i,this[i]))result.push(this[i]);}return result;};
        Array.prototype.some=function(callback){for(var i=0;i<this.length;i++){if(callback(i,this[i]))return true;}return false;};
        </script>"""

    @Test fun schoolArrayPrototypeOverridesCannotHideCourses() {
        val capture = extract("""<!doctype html>$schoolArrayOverrides
            <table id="kbgrid_table_0"><caption>2026-2027学年第1学期</caption>
            <tr><td id="2-1"><div class="timetable_con">合成课程A<br>(1-2节)1-16周<br>示例教室<br>示例教师</div></td>
            <td id="3-1"><div class="timetable_con">合成课程B<br>(3-4节)5-7周(单)<br>示例教室<br>示例教师</div></td></tr>
            <tr><td>实践课程：合成实践/2周/无固定节次</td></tr></table>""")
        assertEquals(3, capture.rows.size)
        assertTrue(capture.rows[0].title.contains("合成课程A"))
        assertTrue(capture.rows[1].title.contains("合成课程B"))
        assertEquals("实践与其他课程", capture.rows[2].title)
    }

    @Test fun schoolArrayOverridesDoNotDropFirstUsageDay() {
        val capture = extract("""<!doctype html>$schoolArrayOverrides
            <div id="tab4"><input id="picker-addr-view" value="合成示例宿舍"><ul class="table">
            <li>日期 用量</li><li>2026-10-29 0</li><li>2026-10-28 1.25</li></ul></div>""", CampusSource.POWER_USAGE)
        assertEquals(2, capture.rows.size)
        assertEquals("0", capture.rows.last().detail)
    }

    @Test fun mobileInlineParagraphsKeepCourseFieldsSeparated() {
        val capture = extract("""<!doctype html>$schoolArrayOverrides<style>p{display:inline}</style>
            <table id="kbgrid_table_0"><caption>2026-2027学年第1学期</caption><tr><td id="2-1">
            <div class="timetable_con"><span class="title">合成课程A</span><p>(1-2节)1-16周</p><p>示例教室</p><font></font><p>示例教师</p></div>
            <div class="timetable_con"><span class="title">合成课程B</span><p>(3-4节)5-7周(单)</p><p>另一示例教室</p><p>另一示例教师</p></div>
            </td></tr></table>""")
        assertEquals(2, capture.rows.size)
        assertEquals("星期二 · 合成课程A", capture.rows[0].title)
        assertTrue(capture.rows[0].detail.contains("示例教室\n示例教师"))
        assertTrue(capture.rows[1].detail.contains("(3-4节)5-7周(单)"))
    }
}
