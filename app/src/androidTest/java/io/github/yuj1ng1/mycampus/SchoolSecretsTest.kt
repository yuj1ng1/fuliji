package io.github.yuj1ng1.mycampus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SchoolSecretsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "rongji.synthetic.crypto.test"
    private val preferences get() = context.getSharedPreferences(namespace, 0)
    private val link = "https://hqwx.fjut.edu.cn/agent/app/index.php?i=67&c=entry&do=rechargeelec&m=maihu_yktservice&mh_timestamp=20000101000000&mh_sign=0123456789abcdef0123456789abcdef&mh_openid=synthetic"

    @After fun cleanup() {
        context.deleteSharedPreferences(namespace)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(namespace + ".aes") }
    }

    @Test fun encryptedRecordsSurviveObjectRecreationWithoutPlaintextOrFixedIv() {
        val secrets = SchoolSecrets(context, namespace)
        val account = SchoolAccount("synthetic-user", "dummy-'\"-password-\\-中文")
        secrets.saveAccount(account)
        secrets.savePowerLink(link)
        val first = preferences.getString("account", null)!!
        assertFalse(first.contains(account.username))
        assertFalse(first.contains(account.password))
        assertFalse(preferences.getString("power_link", null)!!.contains("mh_openid"))
        val recreated = SchoolSecrets(context, namespace)
        assertEquals(account.password, recreated.account()!!.password)
        assertEquals(link, recreated.powerLink())
        recreated.saveAccount(account)
        assertNotEquals(first, preferences.getString("account", null))
        recreated.removeAccount()
        assertNull(recreated.account())
        assertEquals(link, recreated.powerLink())
        recreated.removePowerLink()
        assertFalse(recreated.hasPowerLink())
    }

    @Test fun tamperingOrMovingCiphertextToAnotherRecordFailsClosed() {
        val secrets = SchoolSecrets(context, namespace)
        secrets.saveAccount(SchoolAccount("synthetic-user", "dummy-password"))
        val encrypted = preferences.getString("account", null)!!
        preferences.edit().putString("power_link", encrypted).commit()
        assertThrows(IllegalStateException::class.java) { secrets.powerLink() }
        val parts = encrypted.split(':')
        val bytes = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        preferences.edit().putString("account", parts[0] + ":" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).commit()
        assertThrows(IllegalStateException::class.java) { secrets.account() }
    }

    @Test fun powerAccountIsIndependentFromAcademicPassword() {
        val secrets = SchoolSecrets(context, namespace)
        secrets.saveAccount(SchoolAccount("synthetic-academic", "dummy-academic-password"))
        secrets.savePowerAccount(SchoolAccount("synthetic-power", "dummy-power-password"))
        assertEquals("dummy-academic-password", secrets.account()!!.password)
        assertEquals("dummy-power-password", SchoolSecrets(context, namespace).powerAccount()!!.password)
        secrets.removeAccount()
        assertEquals("dummy-power-password", secrets.powerAccount()!!.password)
        secrets.removePowerAccount()
        assertNull(secrets.powerAccount())
    }
}
