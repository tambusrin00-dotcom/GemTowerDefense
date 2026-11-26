package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PassRoundTest {
    private fun gameWithGem(): Pair<Game, Tower> {
        val g = Game(Random(5))
        g.debugSetup(wave = 5, gold = 0, chanceLevel = 0)
        return g to g.debugKeptGem(Cell(18, 18), GemType.EMERALD, GemQuality.NORMAL)
    }

    @Test
    fun passRaisesAttackAndStartsWave() {
        val (g, t) = gameWithGem()
        val base = GemDB.stats(GemType.EMERALD, GemQuality.NORMAL)
        assertTrue(g.passRound())
        assertEquals(GameState.WAVE, g.state)
        assertEquals(1, g.passCount)
        assertEquals(base.damage * 1.01f, t.stats.damage, 0.001f)
        assertEquals(base.poisonDps * 1.01f, t.stats.poisonDps, 0.001f)
    }

    @Test
    fun cannotPassWithoutGemsAfterPlacingOrDuringWave() {
        assertFalse(Game(Random(1)).passRound())
        val (g, _) = gameWithGem()
        g.placeGem(Cell(17, 18))
        assertFalse(g.passRound())
        val (g2, _) = gameWithGem()
        g2.passRound()
        assertFalse(g2.passRound())
        assertEquals(1, g2.passCount)
    }

    @Test
    fun bonusAppliesToLaterGemsAndResets() {
        val (g, _) = gameWithGem()
        g.passRound()
        val later = g.placeGem(Cell(17, 18), GemType.RUBY, GemQuality.FLAWED, hidden = false)
        assertEquals(GemQuality.FLAWED.baseDamage * 1.01f, later.stats.damage, 0.001f)
        g.newGame()
        assertEquals(0, g.passCount)
    }
}
