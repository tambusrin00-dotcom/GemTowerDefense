package com.tambusrin.gemtower.game

import java.util.Locale
import kotlin.math.floor
import kotlin.math.round

/** 한 판의 결과. 화면에 보이는 단위로 반올림해 저장하므로, 보이는 값이 같으면 다음 기준으로 넘어간다. */
data class GameRecord(
    val bossDamage: Double,   // 보스에게 준 총 피해 (정수)
    val bossTime: Double,     // 보스 웨이브 전투 시간 합 (초, 0.1초 단위)
    val totalTime: Double,    // 모든 웨이브 전투 시간 합 (초, 0.1초 단위)
    val lives: Int,
    val gold: Int,
    val wave: Int,
    val cleared: Boolean,
) {
    companion object {
        fun of(bossDamage: Double, bossTime: Double, totalTime: Double, lives: Int, gold: Int, wave: Int, cleared: Boolean) =
            GameRecord(floor(bossDamage), tenths(bossTime), tenths(totalTime), lives, gold, wave, cleared)

        private fun tenths(s: Double) = round(s * 10.0) / 10.0
    }
}

object Leaderboard {
    const val SIZE = 10

    /** 보스 피해↑ > 보스 시간↓ > 총 시간↓ > 생명↑ > 골드↑ */
    val ranking: Comparator<GameRecord> = compareByDescending<GameRecord> { it.bossDamage }
        .thenBy { it.bossTime }
        .thenBy { it.totalTime }
        .thenByDescending { it.lives }
        .thenByDescending { it.gold }

    /** 기록을 추가한 새 목록과 순위(1부터, 10위 밖이면 null). 완전히 같은 기록은 먼저 세운 쪽이 위. */
    fun add(list: List<GameRecord>, r: GameRecord): Pair<List<GameRecord>, Int?> {
        val sorted = (list + r).sortedWith(ranking).take(SIZE)
        val i = sorted.indexOfFirst { it === r }
        return sorted to (if (i >= 0) i + 1 else null)
    }

    fun encode(list: List<GameRecord>): String = list.joinToString("\n") {
        "${it.bossDamage},${it.bossTime},${it.totalTime},${it.lives},${it.gold},${it.wave},${it.cleared}"
    }

    /** 손상된 줄은 건너뛴다 */
    fun decode(s: String?): List<GameRecord> {
        if (s.isNullOrBlank()) return emptyList()
        return s.lineSequence().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 7) return@mapNotNull null
            val dmg = p[0].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return@mapNotNull null
            val boss = p[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return@mapNotNull null
            val total = p[2].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return@mapNotNull null
            val lives = p[3].toIntOrNull() ?: return@mapNotNull null
            val gold = p[4].toIntOrNull() ?: return@mapNotNull null
            val wave = p[5].toIntOrNull() ?: return@mapNotNull null
            val cleared = p[6].toBooleanStrictOrNull() ?: return@mapNotNull null
            GameRecord(dmg, boss, total, lives, gold, wave, cleared)
        }.toList().sortedWith(ranking).take(SIZE)
    }

    /** 공유할 1위 기록 문구. 끝에 다운로드 링크가 붙는다. */
    fun shareText(r: GameRecord, downloadUrl: String): String = buildString {
        appendLine("💎 보석 타워 디펜스 내 1위 기록!")
        appendLine(if (r.cleared) "🏆 최종 보스 처치, 게임 클리어" else "🌊 도달 웨이브: ${r.wave}")
        appendLine("⚔️ 보스 피해: ${formatDamage(r.bossDamage)}")
        appendLine("⏱️ 보스 시간: ${formatTime(r.bossTime)} · 총 시간: ${formatTime(r.totalTime)}")
        appendLine("❤️ 남은 생명: ${r.lives} · 💰 남은 골드: ${r.gold}G")
        appendLine()
        appendLine("이 기록을 깰 수 있을까요? 지금 다운로드 👇")
        append(downloadUrl)
    }

    /** 1만 미만은 그대로, 그 이상은 만·억·조·경·해 단위 */
    fun formatDamage(d: Double): String {
        val units = listOf(1e20 to "해", 1e16 to "경", 1e12 to "조", 1e8 to "억", 1e4 to "만")
        for ((v, u) in units) if (d >= v) return String.format(Locale.US, "%.2f%s", d / v, u)
        return String.format(Locale.US, "%,d", d.toLong())
    }

    /** 분:초.0 (1시간 이상이면 시:분:초) */
    fun formatTime(sec: Double): String {
        val t = round(sec * 10.0).toLong()
        val s = t / 10
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%d:%02d.%d", s / 60, s % 60, t % 10)
    }
}
