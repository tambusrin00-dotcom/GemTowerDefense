package com.tambusrin.gemtower

import android.content.Context

/** 이어 하기용 진행 상태 (기기 안에만 저장) */
class SaveStore(context: Context) {
    private val prefs = context.getSharedPreferences("save", Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY, null)

    fun save(s: String) {
        prefs.edit().putString(KEY, s).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "game"
    }
}
