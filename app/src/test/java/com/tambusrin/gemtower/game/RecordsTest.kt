package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecordsTest {
    private fun rec(d: Double = 100.0, b: Double = 10.0, t: Double = 100.0, l: Int = 5, g: Int = 50, w: Int = 10) =
        GameRecord(d, b, t, l, g, w, false)

    @Test
    fun rankingAppliesEachCriterionInOrder() {
        val base = rec()
        // 앞 기준이 뒤 기준보다 우선: 뒤 기준은 일부러 불리하게 둠
        assertTrue(Leaderboard.ranking.compare(rec(d = 101.0, b = 99.0, t = 999.0, l = 0, g = 0), base) < 0)
        assertTrue(Leaderboard.ranking.compare(rec(w = 11, b = 99.0, t = 999.0, l = 0, g = 0), base) < 0)
        assertTrue(Leaderboard.ranking.compare(rec(b = 9.0, t = 999.0, l = 0, g = 0), base) < 0)
        assertTrue(Leaderboard.ranking.compare(rec(t = 99.0, l = 0, g = 0), base) < 0)
        assertTrue(Leaderboard.ranking.compare(rec(l = 6, g = 0), base) < 0)
        assertTrue(Leaderboard.ranking.compare(rec(g = 51), base) < 0)
        assertEquals(0, Leaderboard.ranking.compare(rec(), base))
    }

    @Test
    fun keepsOnlyTopTenAndReportsRank() {
        var list = emptyList<GameRecord>()
        for (i in 1..10) list = Leaderboard.add(list, rec(d = i * 10.0)).first
        val (l1, r1) = Leaderboard.add(list, rec(d = 1.0))
        assertNull(r1)
        assertEquals(10, l1.size)
        val (l2, r2) = Leaderboard.add(list, rec(d = 55.0))
        assertEquals(6, r2)
        assertEquals(10, l2.size)
        assertEquals(20.0, l2.last().bossDamage, 0.0)
    }

    @Test
    fun identicalRecordRanksBelowEarlierOne() {
        val (_, rank) = Leaderboard.add(listOf(rec()), rec())
        assertEquals(2, rank)
    }

    @Test
    fun encodeDecodeRoundTripAndSkipsCorruptLines() {
        val list = listOf(GameRecord.of(2.67e21, 12.34, 567.89, 3, 120, 201, true), rec())
        assertEquals(list, Leaderboard.decode(Leaderboard.encode(list)))
        val corrupt = "garbage\n" + Leaderboard.encode(listOf(rec())) + "\nNaN,1,1,1,1,1,false"
        assertEquals(listOf(rec()), Leaderboard.decode(corrupt))
        assertTrue(Leaderboard.decode(null).isEmpty())
    }

    @Test
    fun formats() {
        assertEquals("9,999", Leaderboard.formatDamage(9999.0))
        assertEquals("1.50만", Leaderboard.formatDamage(15000.0))
        assertEquals("26.72해", Leaderboard.formatDamage(2.672e21))
        assertEquals("1:05.5", Leaderboard.formatTime(65.5))
        assertEquals("1:01:01", Leaderboard.formatTime(3661.0))
    }

    @Test
    fun shareTextHasScoreAndDownloadLink() {
        val url = "https://play.google.com/store/apps/details?id=com.tambusrin.gemtower"
        // 최종 보스까지 못 간 판: 보스 피해·시간은 빼고 도달 웨이브와 총 시간만
        val s = Leaderboard.shareText(GameRecord(0.0, 0.0, 3661.0, 7, 120, 42, false), url)
        assertTrue(s.contains("웨이브: 42"))
        assertFalse(s.contains("보스 피해"))
        assertTrue(s.contains("1:01:01"))
        assertTrue(s.contains("120G"))
        assertTrue(s.endsWith(url))
        // 최종 보스 판
        val f = Leaderboard.shareText(GameRecord(15000.0, 65.5, 3661.0, 7, 120, Game.MAX_WAVE, false), url)
        assertTrue(f.contains("최종 보스 피해: 1.50만"))
        assertTrue(f.contains("1:05.5"))
        assertTrue(Leaderboard.shareText(GameRecord(1.0, 1.0, 1.0, 1, 1, 201, true), url).contains("클리어"))
    }

    @Test
    fun gameReportsResultOnceWhenFinished() {
        val g = Game(Random(3))
        val out = ArrayList<GameRecord>()
        g.onFinished = { out.add(it) }
        g.debugSetup(wave = Game.MAX_WAVE, gold = 7, chanceLevel = 0, lives = 20)
        g.debugStartWave()
        var time = 0f
        while (time < 400f) { g.update(0.05f); time += 0.05f }
        assertEquals(GameState.GAME_OVER, g.state)
        assertEquals(1, out.size)
        val r = out[0]
        assertEquals(0, r.lives)
        assertEquals(Game.MAX_WAVE, r.wave)
        assertFalse(r.cleared)
        assertEquals(0.0, r.bossDamage, 0.0)
        assertTrue(r.bossTime > 0.0)
        assertEquals(r.bossTime, r.totalTime, 0.0)   // 보스 웨이브만 치름
    }

    @Test
    fun bossDamageIsCounted() {
        val g = Game(Random(3))
        var result: GameRecord? = null
        g.onFinished = { result = it }
        g.debugSetup(wave = Game.MAX_WAVE, gold = 0, chanceLevel = 0, lives = 20)
        val path = g.groundPath.toSet()
        val beside = g.groundPath.drop(5).flatMap {
            listOf(Cell(it.x + 1, it.y), Cell(it.x - 1, it.y), Cell(it.x, it.y + 1), Cell(it.x, it.y - 1))
        }.first { g.inBounds(it) && it !in path && g.canBuild(it) }
        g.debugKeptGem(beside, GemType.DIAMOND, GemQuality.FLAWLESS)
        g.debugStartWave()
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) {
            g.enemies.forEach { if (it.hp > 5f) it.hp = 5f }
            g.update(0.05f); time += 0.05f
        }
        assertNotNull(result)
        assertTrue(result!!.cleared)
        assertEquals(5.0, result!!.bossDamage, 0.0)
    }

    @Test
    fun earlierBossWavesDoNotCount() {
        val g = Game(Random(3))
        var result: GameRecord? = null
        g.onFinished = { result = it }
        g.debugSetup(wave = 10, gold = 0, chanceLevel = 0, lives = 1)
        val path = g.groundPath.toSet()
        val beside = g.groundPath.drop(5).flatMap {
            listOf(Cell(it.x + 1, it.y), Cell(it.x - 1, it.y), Cell(it.x, it.y + 1), Cell(it.x, it.y - 1))
        }.first { g.inBounds(it) && it !in path && g.canBuild(it) }
        g.debugKeptGem(beside, GemType.RUBY, GemQuality.CHIPPED)
        g.debugStartWave()   // 10웨이브 보스: 맞기는 하지만 최종 보스가 아니라 기록에 안 들어감
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) { g.update(0.05f); time += 0.05f }
        assertNotNull(result)
        assertEquals(10, result!!.wave)
        assertEquals(0.0, result!!.bossDamage, 0.0)
        assertEquals(0.0, result!!.bossTime, 0.0)
        assertTrue(result!!.totalTime > 0.0)
    }
}
