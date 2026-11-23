package com.tambusrin.gemtower

import android.content.Context
import com.tambusrin.gemtower.game.GameRecord
import com.tambusrin.gemtower.game.Leaderboard

/** 상위 10개 기록을 기기 안에만 저장 */
class RecordStore(context: Context) {
    private val prefs = context.getSharedPreferences("records", Context.MODE_PRIVATE)

    fun load(): List<GameRecord> = Leaderboard.decode(prefs.getString(KEY, null))

    fun save(list: List<GameRecord>) {
        prefs.edit().putString(KEY, Leaderboard.encode(list)).apply()
    }

    private companion object {
        const val KEY = "top10"
    }
}
