package com.example.studyink.data

import android.content.Context

/** 应用级偏好设置（SharedPreferences，不引入额外依赖） */
class AppSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("studyink_settings", Context.MODE_PRIVATE)

    /** 仅触控笔可书写：开启后手指 / 掌触不产生笔迹 */
    var stylusOnly: Boolean
        get() = prefs.getBoolean(KEY_STYLUS_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_STYLUS_ONLY, value).apply()

    private companion object {
        const val KEY_STYLUS_ONLY = "stylus_only"
    }
}
