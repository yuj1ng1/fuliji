package io.github.yuj1ng1.mycampus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeCourseMode { TODAY, NEXT }

class UiPreferences(context: Context, namespace: String = "ui_options") {
    private val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching { HomeCourseMode.valueOf(preferences.getString("home_course_mode", "TODAY").orEmpty()) }.getOrDefault(HomeCourseMode.TODAY))
    val homeCourseMode = mutable.asStateFlow()
    fun chooseHomeCourseMode(mode: HomeCourseMode) {
        check(preferences.edit().putString("home_course_mode", mode.name).commit()) { "无法保存首页样式。" }
        mutable.value = mode
    }
}
