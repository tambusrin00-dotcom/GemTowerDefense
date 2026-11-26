package com.tambusrin.gemtower

import android.content.Context
import com.tambusrin.gemtower.game.GameRecord
import com.tambusrin.gemtower.game.Leaderboard

/** 상위 10개 기록을 기기 안에만 저장 */
class RecordStore(context: Context) {
    private val prefs = context.getSharedPreferences("records", Context.MODE_PRIVATE)

    fun load(): List<GameRecord> {
        // 예전 기준(모든 보스 웨이브 피해 합) 기록은 새 기준(최종 보스만)과 비교할 수 없어 지움
        if (prefs.contains(OLD_KEY)) prefs.edit().remove(OLD_KEY).apply()
        return Leaderboard.decode(prefs.getString(KEY, null))
    }

    fun save(list: List<GameRecord>) {
        prefs.edit().putString(KEY, Leaderboard.encode(list)).apply()
    }

    private companion object {
        const val OLD_KEY = "top10"
        /** v2: 보스 피해·시간 = 최종 보스만, 순위에 도달 웨이브 포함 */
        const val KEY = "top10_v2"
    }
}
