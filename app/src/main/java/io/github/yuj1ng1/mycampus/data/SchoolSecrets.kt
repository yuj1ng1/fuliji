package io.github.yuj1ng1.mycampus.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.yuj1ng1.mycampus.domain.SchoolAccessPolicy
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SchoolAccount(val username: String, val password: String) {
    override fun toString(): String = "SchoolAccount(redacted)"
}

/** Only opt-in secrets entered/shared on this device. No account is supplied by the APK. */
class SchoolSecrets(context: Context, private val namespace: String = "rongji.school.v1") {
    private val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val alias = namespace + ".aes"
    fun hasAccount(): Boolean = preferences.contains("account")
    fun hasPowerLink(): Boolean = preferences.contains("power_link")
    fun hasPowerAccount(): Boolean = preferences.contains("power_account")

    fun account(): SchoolAccount? = read("account")?.let { text ->
        JSONObject(text).let { SchoolAccount(it.getString("username"), it.getString("password")) }
    }
    fun saveAccount(account: SchoolAccount) {
        require(account.username.trim().length in 1..100 && account.password.length in 1..256)
        write("account", JSONObject().put("username", account.username.trim()).put("password", account.password).toString())
    }
    fun removeAccount() { check(preferences.edit().remove("account").commit()) }
    fun powerAccount(): SchoolAccount? = read("power_account")?.let { text ->
        JSONObject(text).let { SchoolAccount(it.getString("username"), it.getString("password")) }
    }
    fun savePowerAccount(account: SchoolAccount) {
        require(account.username.trim().length in 1..100 && account.password.length in 1..256)
        write("power_account", JSONObject().put("username", account.username.trim()).put("password", account.password).toString())
    }
    fun removePowerAccount() { check(preferences.edit().remove("power_account").commit()) }
    fun powerLink(): String? = read("power_link")?.takeIf(SchoolAccessPolicy::isPowerPage)
    fun savePowerLink(link: String) {
        require(link.length <= 8192 && SchoolAccessPolicy.isPowerPage(link)) { "这不是学校后勤水电查询链接。" }
        write("power_link", link.substringBefore('#'))
    }
    fun removePowerLink() { check(preferences.edit().remove("power_link").commit()) }

    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private fun write(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString(name, encoded).commit()) { "本机加密保存失败。" }
    }
    private fun read(name: String): String? {
        val encoded = preferences.getString(name, null) ?: return null
        try {
            val parts = encoded.split(':', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("本机保存的登录信息暂时无法解密，请重新保存。") }
    }
}
