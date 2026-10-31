package io.github.yuj1ng1.mycampus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SchoolOptions(val autoRefresh: Boolean = false, val rememberSession: Boolean = true, val autoLoginPaused: Boolean = false,
                         val powerDailyRefresh: Boolean = true)

class SchoolPreferences(context: Context, namespace: String = "school_options") {
    private val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val options = mutable.asStateFlow()
    val current: SchoolOptions get() = mutable.value
    private fun read() = SchoolOptions(preferences.getBoolean("auto_refresh", false), preferences.getBoolean("remember_session", true),
        preferences.getBoolean("auto_login_paused", false), preferences.getBoolean("power_daily_refresh", true))
    fun update(autoRefresh: Boolean = current.autoRefresh, rememberSession: Boolean = current.rememberSession,
               autoLoginPaused: Boolean = current.autoLoginPaused, powerDailyRefresh: Boolean = current.powerDailyRefresh) {
        check(preferences.edit().putBoolean("auto_refresh", autoRefresh).putBoolean("remember_session", rememberSession)
            .putBoolean("auto_login_paused", autoLoginPaused).putBoolean("power_daily_refresh", powerDailyRefresh).commit()) { "无法保存学校连接选项。" }
        mutable.value = read()
    }
}
