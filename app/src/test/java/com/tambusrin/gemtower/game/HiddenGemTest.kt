package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HiddenGemTest {
    private fun freeCells(g: Game): List<Cell> {
        val onPath = g.groundPath.toSet()
        val out = ArrayList<Cell>()
        for (x in 0 until Game.W) for (y in 0 until Game.H) {
            val c = Cell(x, y)
            if (c !in onPath && g.cellType(c) == CellType.EMPTY) out.add(c)
        }
        return out
    }

    @Test
    fun chanceIsOneInHundredMillion() {
        // 0.000001% = 0.00000001
        assertEquals(1e-8, GemDB.HIDDEN_CHANCE, 0.0)
        assertTrue(SpecialKind.URANIUM.hidden)
        assertEquals("우라늄", SpecialKind.URANIUM.label)
        assertTrue(GemDB.recipes.none { it.result.hidden })
    }

    @Test
    fun uraniumDamageIsSumOfAllOtherGems() {
        val g = Game(Random(3))
        val cells = freeCells(g)
        val ruby = g.debugKeptGem(cells[0], GemType.RUBY, GemQuality.NORMAL)
        val storm = g.debugKeptGem(cells[1], GemType.TOPAZ, GemQuality.NORMAL, SpecialKind.STORM_SPIRE, 2)
        val u = g.placeGem(cells[2], GemType.DIAMOND, GemQuality.CHIPPED, hidden = true)
        assertEquals(SpecialKind.URANIUM, u.special)
        assertEquals("우라늄", u.stats.name)
        assertEquals(ruby.stats.damage + storm.stats.damage, u.stats.damage, 0.01f)

        // 보석이 늘면 공격력도 즉시 늘어남
        val emerald = g.placeGem(cells[3], GemType.EMERALD, GemQuality.FLAWLESS, hidden = false)
        g.tickUi(0.016f)
        assertEquals(ruby.stats.damage + storm.stats.damage + emerald.stats.damage, u.stats.damage, 0.01f)
    }

    @Test
    fun uraniumIsNotCombinableUpgradeableOrRecipePart() {
        val g = Game(Random(5))
        val cells = freeCells(g)
        val u = g.placeGem(cells[0], GemType.SAPPHIRE, GemQuality.NORMAL, hidden = true)
        val s = g.placeGem(cells[1], GemType.SAPPHIRE, GemQuality.NORMAL, hidden = false)
        assertEquals(1, g.countSame(s))                       // 우라늄은 같은 보석으로 세지 않음
        for (r in GemDB.recipes) assertNull(g.findRecipeParts(u, r))

        repeat(3) { g.placeGem(cells[2 + it], GemType.RUBY, GemQuality.CHIPPED, hidden = false) }
        g.keep(u)
        assertTrue(u.kept)
        g.debugSetup(wave = g.wave, gold = 10_000, chanceLevel = 0, lives = 20)
        g.upgradeSpecial(u)
        assertEquals(0, u.specialLevel)                       // 강화 불가
    }

    @Test
    fun hiddenGemsAppearAtRoughlyTheRightRate() {
        // 확률 자체만 검증 (1e-8이라 실제 배치로는 거의 안 나옴)
        val rng = Random(1)
        var hits = 0
        repeat(1_000_000) { if (rng.nextDouble() < GemDB.HIDDEN_CHANCE) hits++ }
        assertTrue("hits=$hits", hits <= 2)
    }
}
