package io.github.yuj1ng1.mycampus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.domain.PaymentCandidate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit two-install probe using only a synthetic ledger record. Never clears app data. */
@RunWith(AndroidJUnit4::class)
class UpgradeProbeTest {
    @Test fun ledgerSurvivesCoverInstall() = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("upgrade_stage")
        assumeTrue(stage in setOf("seed", "verify"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication
        val id = "synthetic-cover-install-probe"
        if (stage == "seed") {
            app.repository.saveCandidate(PaymentCandidate(id, "合成升级验证", "EXPENSE", 4321, 1234))
            app.repository.confirmExpense(id, 4321, "合成升级验证")
        }
        val saved = app.repository.ledger.first().single { it.id == id }
        assertEquals(4321L, saved.amountCents)
        assertEquals("CONFIRMED", saved.status)
        assertEquals("合成升级验证", saved.note)
        if (stage == "verify") app.repository.deleteEntry(id)
    }
}
