package io.github.yuj1ng1.mycampus

import android.content.Context
import android.graphics.Bitmap
import android.webkit.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.sync.SchoolAutoSync
import io.github.yuj1ng1.mycampus.web.SchoolSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Cold WebView sessions and all school responses are synthetic; no live credentials are used. */
@RunWith(AndroidJUnit4::class)
class PowerSessionRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "synthetic-power-recovery"
    private class Site {
        val service = SyntheticPowerSite()
        val navigations = CopyOnWriteArrayList<String>()
        var mode = "bootstrap"
        val expired = CampusSource.POWER.entryUrl + "&mh_timestamp=20000101000000&mh_sign=1111111111111111&mh_openid=synthetic-old"
        val fresh = CampusSource.POWER.entryUrl + "&mh_timestamp=20000102000000&mh_sign=2222222222222222&mh_openid=synthetic-fresh"
        private val binding = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <h1>用户身份认证</h1><input id="studentid"><input id="password" type="password">
            <input id="mobile"><input id="code"><button onclick="throw Error('Binding must never be submitted')">立即绑定</button>"""
        private val vpn = """<!doctype html><h1>学校WebVPN</h1><input id="user_login"><input id="user_password" type="password"><button type="submit">登录</button>"""
        fun createView(context: Context): WebView = object : WebView(context) {
            override fun setWebViewClient(client: WebViewClient) {
                val queries = service.client(client, true)
                super.setWebViewClient(object : WebViewClient() {
                    override fun onPageStarted(v: WebView, u: String?, i: Bitmap?) = client.onPageStarted(v, u, i)
                    override fun onPageFinished(v: WebView, u: String?) = client.onPageFinished(v, u)
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = client.shouldOverrideUrlLoading(v, r)
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse {
                        if (r.url.getQueryParameter("op") != null) return queries.shouldInterceptRequest(v, r)!!
                        if (!r.isForMainFrame) return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                        val signature = r.url.getQueryParameter("mh_sign")
                        val page = when {
                            r.url.host == "webvpn.fjut.edu.cn" -> { navigations += "vpn"; vpn }
                            signature == "1111111111111111" -> { navigations += "expired"; binding }
                            signature == "2222222222222222" -> { navigations += "fresh"; service.html(true) }
                            else -> {
                                navigations += "stable"
                                when (mode) {
                                    "binding" -> binding
                                    "vpn" -> "<script>location.replace('https://webvpn.fjut.edu.cn/')</script>"
                                    else -> "<script>location.replace('${fresh}')</script>"
                                }
                            }
                        }
                        return WebResourceResponse("text/html", "UTF-8", page.byteInputStream())
                    }
                })
            }
        }
    }

    private fun withEngine(block: suspend (SchoolSyncEngine, SchoolPreferences, SchoolSecrets, Site) -> Unit): Unit = runBlocking {
        val options = SchoolPreferences(context, namespace)
        val secrets = SchoolSecrets(context, namespace + ".secrets")
        val site = Site()
        try { block(SchoolSyncEngine(context, options, secrets, site::createView), options, secrets, site) }
        finally { context.deleteSharedPreferences(namespace); context.deleteSharedPreferences(namespace + ".secrets") }
    }

    @Test fun expiredSavedLinkDoesNotPreventColdStableEntryFromRefreshingBothDatasets() = withEngine { engine, _, secrets, site ->
        secrets.savePowerLink(site.expired)
        val balance = engine.fetch(CampusSource.POWER, site.service.room)
        val usage = engine.fetch(CampusSource.POWER_USAGE, site.service.room)
        assertEquals("9.75 度", balance.rows.single().detail)
        assertEquals(3, usage.rows.size)
        assertEquals(listOf("stable", "fresh", "stable", "fresh"), site.navigations.toList())
        assertEquals(listOf("getelec", "getelecday"), site.service.requests.toList())
        assertEquals(site.fresh, secrets.powerLink())
    }

    @Test fun optedInAuthorizedLinkRemainsABoundedFallbackForAStableEntryRequiringBinding() = withEngine { engine, _, secrets, site ->
        site.mode = "binding"
        secrets.savePowerLink(site.fresh)
        assertEquals(site.service.room, engine.fetch(CampusSource.POWER, site.service.room).scope)
        assertEquals(listOf("stable", "fresh"), site.navigations.toList())
    }

    @Test fun expiredFallbackStopsWithoutBindingAndPreservesBothPreviousSnapshots() = withEngine { engine, options, secrets, site ->
        site.mode = "binding"
        secrets.savePowerLink(site.expired)
        val database = Room.inMemoryDatabaseBuilder(context, CampusDatabase::class.java).build()
        val repository = CampusRepository(database)
        try {
            for (source in listOf(CampusSource.POWER, CampusSource.POWER_USAGE)) {
                val rows = listOf(DisplayRow(site.service.room, "合成旧数据"))
                repository.saveCapture(source, Capture(site.service.room, rows, CaptureReader.encode(rows), false))
            }
            val before = repository.snapshots.first()
            SchoolAutoSync(context, repository, options, secrets, engine::fetch).refresh(only = CampusSource.POWER)
            assertEquals(before, repository.snapshots.first())
            assertEquals(listOf("stable", "expired", "stable"), site.navigations.toList())
            assertTrue(site.service.requests.isEmpty())
            assertTrue(repository.connections.first().all { it.state == "NEEDS_PHONE_VERIFICATION" })
        } finally { database.close() }
    }

    @Test fun networkChangeRetriesAfterVpnFailureWithoutOpeningTheManualQuery() = withEngine { engine, options, secrets, site ->
        site.mode = "vpn"
        secrets.savePowerLink(site.expired)
        val database = Room.inMemoryDatabaseBuilder(context, CampusDatabase::class.java).build()
        val repository = CampusRepository(database)
        var network = 1L
        try {
            val old = SavedSnapshot("POWER|${site.service.room}", "POWER", site.service.room,
                CaptureReader.encode(listOf(DisplayRow(site.service.room, "77.70 度"))), 1, 1)
            database.dao().putSnapshot(old)
            val sync = SchoolAutoSync(context, repository, options, secrets, engine::fetch, { network })
            sync.onForeground()
            assertEquals(old, repository.snapshots.first().single())
            assertTrue(repository.connections.first().all { it.state == "NEEDS_VPN" })
            val requests = site.navigations.size
            sync.onForeground()
            assertEquals(requests, site.navigations.size)
            site.mode = "bootstrap"
            network = 2L
            sync.onForeground()
            assertEquals(2, repository.snapshots.first().size)
            assertEquals(listOf("getelec", "getelecday"), site.service.requests.toList())
            assertTrue(repository.connections.first().all { it.state == "SUCCESS" })
        } finally { database.close() }
    }
}
