package io.github.yuj1ng1.mycampus

import android.content.Intent
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.web.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerReadSaveTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun verifySwitch(entry: CampusSource, target: CampusSource, targetTab: String) {
        val site = SyntheticPowerSite()
        val app = context.applicationContext as CampusApplication
        val id = target.name + "|" + site.room
        SchoolBrowserLease.enter()
        try {
            val intent = Intent(context, CampusWebActivity::class.java).putExtra("source", entry.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<CampusWebActivity>(intent).use { scenario ->
                lateinit var view: WebView
                scenario.onActivity { activity ->
                    view = activity.javaClass.getDeclaredField("schoolView").apply { isAccessible = true }.get(activity) as WebView
                    view.stopLoading()
                    view.webViewClient = site.client(view.webViewClient)
                    view.loadUrl(entry.entryUrl.substringBefore('#') + "&test_fixture=" + entry.name)
                }
                ui.waitUntil(15_000) { ui.onAllNodesWithText(entry.label + " · hqwx.fjut.edu.cn").fetchSemanticsNodes().isNotEmpty() &&
                    runCatching { ui.onNodeWithText("读取并保存").assertIsEnabled(); true }.getOrDefault(false) }
                scenario.onActivity { view.evaluateJavascript("document.querySelector('a[href=\"#$targetTab\"]').click();", null) }
                ui.waitUntil(15_000) { ui.onAllNodesWithText(target.label + " · hqwx.fjut.edu.cn").fetchSemanticsNodes().isNotEmpty() &&
                    runCatching { ui.onNodeWithText("读取并保存").assertIsEnabled(); true }.getOrDefault(false) }
                ui.onNodeWithText("读取并保存").performClick()
                ui.waitUntil(10_000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
                val saved = runBlocking { app.repository.snapshots.first() }.single { it.id == id }
                assertEquals(target.name, saved.source)
                val rows = CaptureReader.decode(saved.payload)
                if (target == CampusSource.POWER_USAGE) {
                    assertEquals(2, rows.size)
                    assertEquals("0", rows.first().detail)
                    assertEquals("5.50", rows.last().detail)
                } else assertEquals("88.88 度", rows.single().detail)
                assertTrue(site.requests.isEmpty())
            }
        } finally {
            runBlocking { app.database.openHelper.writableDatabase.execSQL("DELETE FROM campus_snapshots WHERE id = ?", arrayOf(id)) }
            SchoolBrowserLease.leave()
        }
    }
    @Test fun balanceEntryCanSaveVisibleUsageTab() = verifySwitch(CampusSource.POWER, CampusSource.POWER_USAGE, "tab4")
    @Test fun usageEntryCanSaveVisibleBalanceTab() = verifySwitch(CampusSource.POWER_USAGE, CampusSource.POWER, "tab1")
}
