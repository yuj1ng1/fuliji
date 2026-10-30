package io.github.yuj1ng1.mycampus.web

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.domain.CampusSource
import io.github.yuj1ng1.mycampus.ui.CampusTheme
import io.github.yuj1ng1.mycampus.ui.PowerAccessDialog

class PowerLinkImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain")
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().take(16_384) else ""
        intent.removeExtra(Intent.EXTRA_TEXT)
        setContent {
            CampusTheme {
                PowerAccessDialog(application as CampusApplication, text,
                    onConnected = { CampusWebActivity.open(this, CampusSource.POWER, authorize = true); finish() }, onDismiss = { finish() })
            }
        }
    }
}
