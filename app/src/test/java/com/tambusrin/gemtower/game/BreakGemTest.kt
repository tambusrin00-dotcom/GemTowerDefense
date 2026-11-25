package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BreakGemTest {
    @Test
    fun keptGemBreaksForFree() {
        val g = Game(Random(5))
        g.debugSetup(wave = 5, gold = 30, chanceLevel = 0)
        val c = Cell(18, 18)
        g.debugKeptGem(c, GemType.RUBY, GemQuality.NORMAL)
        assertTrue(g.canBreakGem(c))
        assertTrue(g.breakGem(c))
        assertNull(g.towerAt(c))
        assertEquals(CellType.EMPTY, g.cellType(c))
        assertEquals(30, g.gold)
        assertFalse(g.breakGem(c))
    }

    @Test
    fun cannotBreakRoundGemsOrDuringWave() {
        val g = Game(Random(5))
        g.debugSetup(wave = 5, gold = 0, chanceLevel = 0)
        val kept = Cell(18, 18)
        g.debugKeptGem(kept, GemType.RUBY, GemQuality.NORMAL)
        val round = Cell(17, 18)
        g.placeGem(round, GemType.TOPAZ, GemQuality.CHIPPED, hidden = false)
        assertFalse(g.canBreakGem(round))
        g.debugStartWave()
        assertFalse(g.canBreakGem(kept))
    }

    @Test
    fun breakingUpdatesUraniumDamage() {
        val g = Game(Random(5))
        g.debugSetup(wave = 5, gold = 0, chanceLevel = 0)
        val ruby = Cell(18, 18)
        g.debugKeptGem(ruby, GemType.RUBY, GemQuality.NORMAL)
        val u = g.debugKeptGem(Cell(16, 18), GemType.RUBY, GemQuality.NORMAL, SpecialKind.URANIUM)
        g.refreshHiddenGems()
        assertTrue(u.stats.damage > 0f)
        g.breakGem(ruby)
        assertEquals(0f, u.stats.damage, 0.001f)
    }
}
