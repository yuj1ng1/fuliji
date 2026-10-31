package io.github.yuj1ng1.mycampus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.domain.CampusSource
import io.github.yuj1ng1.mycampus.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerConnectionUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun ordinaryLinkCanBeSavedAndConnectedWithoutSignatureOrAccount() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication
        val previous = app.schoolSecrets.powerLink()
        var connected = false
        try {
            ui.setContent { CampusTheme { PowerAccessDialog(app, CampusSource.POWER.entryUrl, onConnected = { connected = true }, onDismiss = {}) } }
            ui.onNodeWithTag("connect-power-entry").assertIsEnabled().performClick()
            ui.waitUntil(10_000) { connected }
            assertEquals(CampusSource.POWER.entryUrl, app.schoolSecrets.powerLink())
        } finally {
            if (previous == null) app.schoolSecrets.removePowerLink() else app.schoolSecrets.savePowerLink(previous)
        }
    }

    @Test fun invalidEntryStaysDisabledAndDoesNotOpenAnotherHost() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication
        ui.setContent { CampusTheme { PowerAccessDialog(app, "https://example.com/", onConnected = { error("Must not connect") }, onDismiss = {}) } }
        ui.onNodeWithTag("connect-power-entry").assertIsNotEnabled()
        ui.onNodeWithTag("power-entry").performTextReplacement(CampusSource.POWER.entryUrl)
        ui.onNodeWithTag("connect-power-entry").assertIsEnabled()
    }
}
