package io.github.yuj1ng1.mycampus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.LedgerEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit two-install probe using only a synthetic ledger record. Never clears app data. */
@RunWith(AndroidJUnit4::class)
class UpgradeProbeTest {
    @Test fun ledgerSurvivesCoverInstall(): Unit = runBlocking {
        val stage = InstrumentationRegistry.getArguments().getString("upgrade_stage")
        assumeTrue(stage in setOf("seed", "verify"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CampusApplication
        val id = "synthetic-cover-install-probe"
        val draftId = "synthetic-cover-legacy-draft"
        if (stage == "seed") {
            app.database.dao().insertEntry(LedgerEntry(id, "手动", "EXPENSE", 4321, 1234, "USER", "合成升级验证", "CONFIRMED"))
            app.database.dao().insertEntry(LedgerEntry(draftId, "旧版合成", "EXPENSE", 540, 1235, "NOTIFICATION", "", "DRAFT"))
        }
        val saved = app.repository.ledger.first().single { it.id == id }
        assertEquals(4321L, saved.amountCents)
        assertEquals("CONFIRMED", saved.status)
        assertEquals("合成升级验证", saved.note)
        val draft = app.repository.ledger.first().single { it.id == draftId }
        assertEquals("DRAFT", draft.status)
        assertEquals(540L, draft.amountCents)
        assertEquals(1235L, draft.occurredAt)
        if (stage == "verify") {
            app.repository.confirmExpense(draftId, 600, "合成手动核对")
            assertEquals("CONFIRMED", app.repository.ledger.first().single { it.id == draftId }.status)
            app.repository.deleteEntry(id)
            app.repository.deleteEntry(draftId)
        }
    }
}
