package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SaveStateTest {
    @Test
    fun buildPhaseRoundTrips() {
        val g = Game(Random(3))
        g.debugSetup(wave = 7, gold = 123, chanceLevel = 2, lives = 11)
        val kept = g.debugKeptGem(Cell(5, 5), GemType.RUBY, GemQuality.FLAWLESS)
        kept.kills = 9
        kept.totalDamage = 4567f
        g.debugRock(Cell(6, 6))
        g.placeGem(Cell(3, 3), GemType.SAPPHIRE, GemQuality.NORMAL, hidden = false)
        g.placeGem(Cell(3, 4), GemType.EMERALD, GemQuality.CHIPPED, hidden = false)

        val loaded = Game(Random(9))
        assertTrue(loaded.loadState(g.saveState()!!))
        assertEquals(GameState.BUILD_PLACE, loaded.state)
        assertEquals(7, loaded.wave)
        assertEquals(123, loaded.gold)
        assertEquals(2, loaded.chanceLevel)
        assertEquals(11, loaded.lives)
        assertEquals(CellType.ROCK, loaded.cellType(Cell(6, 6)))
        val t = loaded.towerAt(Cell(5, 5))!!
        assertTrue(t.kept)
        assertEquals(9, t.kills)
        assertEquals(4567f, t.totalDamage, 0.01f)
        assertEquals(GemQuality.FLAWLESS, t.quality)
        assertEquals(listOf(Cell(3, 3), Cell(3, 4)), loaded.roundGems.map { it.cell })
        assertEquals(g.groundPath, loaded.groundPath)
    }

    @Test
    fun midWaveRestartsFromWaveStart() {
        val g = Game(Random(4))
        g.debugSetup(wave = 3, gold = 50, chanceLevel = 0)
        g.debugKeptGem(Cell(18, 18), GemType.TOPAZ, GemQuality.NORMAL, SpecialKind.FROST_SPIRE, level = 2)
        assertTrue(g.passRound())
        g.update(5f)   // 전투 진행 중
        val s = g.saveState()!!

        val loaded = Game()
        assertTrue(loaded.loadState(s))
        assertEquals(GameState.WAVE, loaded.state)
        assertEquals(3, loaded.wave)
        assertEquals(1, loaded.passCount)
        assertEquals(loaded.waveInfo(3).count, loaded.remainingEnemies)
        val t = loaded.towerAt(Cell(18, 18))!!
        assertEquals(SpecialKind.FROST_SPIRE, t.special)
        assertEquals(2, t.specialLevel)
    }

    @Test
    fun badDataIsRejectedWithoutChangingGame() {
        val g = Game(Random(1))
        g.debugSetup(wave = 4, gold = 10, chanceLevel = 0)
        assertFalse(g.loadState("garbage"))
        assertFalse(g.loadState("v=1\nstate=BUILD_PLACE\n"))
        assertEquals(4, g.wave)
    }

    @Test
    fun finishedGameHasNoSave() {
        val g = Game(Random(1))
        g.debugSetup(wave = 1, gold = 0, chanceLevel = 0, lives = 1)
        assertNotNull(g.saveState())
        g.debugStartWave()
        g.update(120f) // 공격하는 보석이 없으니 결국 생명 소진
        assertEquals(GameState.GAME_OVER, g.state)
        assertNull(g.saveState())
    }
}
