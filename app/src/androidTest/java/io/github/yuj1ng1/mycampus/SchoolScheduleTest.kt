package io.github.yuj1ng1.mycampus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.yuj1ng1.mycampus.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SchoolScheduleTest {
    @get:Rule val ui = createComposeRule()

    @Test fun optInCreatesOnePeriodicWorkAndOptOutCancelsIt() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication
        val before = app.schoolOptions.current
        val work = WorkManager.getInstance(app)
        try {
            app.schoolOptions.update(autoRefresh = false, powerDailyRefresh = false)
            app.schoolSync.configureSchedule()
            ui.setContent { CampusTheme { SchoolConnectionSettings(app) {} } }
            ui.onNodeWithTag("school-auto-refresh").performScrollTo().performClick()
            ui.waitUntil(10_000) {
                work.getWorkInfosForUniqueWork("rongji-school-periodic").get(2, TimeUnit.SECONDS).count { !it.state.isFinished } == 1
            }
            val scheduled = work.getWorkInfosForUniqueWork("rongji-school-periodic").get(5, TimeUnit.SECONDS).single { !it.state.isFinished }
            assertEquals(WorkInfo.State.ENQUEUED, scheduled.state)
            ui.onNodeWithTag("school-auto-refresh").performScrollTo().performClick()
            ui.waitUntil(10_000) { work.getWorkInfosForUniqueWork("rongji-school-periodic").get(2, TimeUnit.SECONDS).all { it.state.isFinished } }
            assertFalse(app.schoolOptions.current.autoRefresh)
        } finally {
            app.schoolOptions.update(before.autoRefresh, before.rememberSession, before.autoLoginPaused, before.powerDailyRefresh)
            app.schoolSync.configureSchedule()
        }
    }

    @Test fun powerDailyRefreshUsesTheSameSingleWorkerWithoutGeneralAutoRefresh() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication
        val before = app.schoolOptions.current
        val work = WorkManager.getInstance(app)
        try {
            app.schoolOptions.update(autoRefresh = false, powerDailyRefresh = true)
            app.schoolSync.configureSchedule()
            app.schoolSync.configureSchedule()
            ui.waitUntil(10_000) { work.getWorkInfosForUniqueWork("rongji-school-periodic").get(2, TimeUnit.SECONDS).count { !it.state.isFinished } == 1 }
            assertTrue(app.schoolOptions.current.powerDailyRefresh)
            app.schoolOptions.update(powerDailyRefresh = false)
            app.schoolSync.configureSchedule()
            ui.waitUntil(10_000) { work.getWorkInfosForUniqueWork("rongji-school-periodic").get(2, TimeUnit.SECONDS).all { it.state.isFinished } }
        } finally {
            app.schoolOptions.update(before.autoRefresh, before.rememberSession, before.autoLoginPaused, before.powerDailyRefresh)
            app.schoolSync.configureSchedule()
        }
    }
}
