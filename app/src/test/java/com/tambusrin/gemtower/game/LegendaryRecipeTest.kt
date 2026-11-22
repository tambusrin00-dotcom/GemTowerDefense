package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 특수 보석을 재료로 쓰는 전설 조합법 */
class LegendaryRecipeTest {
    private val absoluteZero = GemDB.recipeFor(SpecialKind.ABSOLUTE_ZERO)

    private fun freeCells(g: Game): List<Cell> {
        val onPath = g.groundPath.toSet()
        val out = ArrayList<Cell>()
        for (x in 0 until Game.W) for (y in 0 until Game.H) {
            val c = Cell(x, y)
            if (c !in onPath && g.cellType(c) == CellType.EMPTY) out.add(c)
        }
        return out
    }

    /** 무결점 사파이어 1개 + 잡석 4개를 이번 라운드 보석으로 놓음 */
    private fun placeRound(g: Game, cells: List<Cell>): Tower {
        val sapphire = g.placeGem(cells[0], GemType.SAPPHIRE, GemQuality.FLAWLESS, hidden = false)
        for (i in 1..4) g.placeGem(cells[i], GemType.RUBY, GemQuality.CHIPPED, hidden = false)
        return sapphire
    }

    @Test
    fun legendaryUsesSpecialGemsFromTheField() {
        val g = Game(Random(11))
        val cells = freeCells(g)
        val frost = g.debugKeptGem(cells[10], GemType.SAPPHIRE, GemQuality.NORMAL, SpecialKind.FROST_SPIRE, 3)
        val frostLow = g.debugKeptGem(cells[11], GemType.SAPPHIRE, GemQuality.NORMAL, SpecialKind.FROST_SPIRE, 1)
        val prison = g.debugKeptGem(cells[12], GemType.SAPPHIRE, GemQuality.NORMAL, SpecialKind.CRYSTAL_PRISON)
        val sapphire = placeRound(g, cells)

        assertEquals(3, absoluteZero.tier)
        val parts = g.findRecipeParts(sapphire, absoluteZero)
        assertNotNull(parts)
        assertTrue("강화 단계가 낮은 서리 첨탑을 재료로 씀", parts!!.any { it === frostLow } && parts.none { it === frost })

        g.makeSpecial(sapphire, absoluteZero)
        assertEquals(SpecialKind.ABSOLUTE_ZERO, sapphire.special)
        assertTrue(sapphire.kept)
        assertEquals(CellType.ROCK, g.cellType(frostLow.cell))   // 재료 특수 보석은 바위가 됨
        assertEquals(CellType.ROCK, g.cellType(prison.cell))
        assertEquals(SpecialKind.FROST_SPIRE, g.towerAt(frost.cell)?.special)  // 다른 서리 첨탑은 그대로
    }

    @Test
    fun missingSpecialIngredientBlocksRecipe() {
        val g = Game(Random(12))
        val cells = freeCells(g)
        g.debugKeptGem(cells[10], GemType.SAPPHIRE, GemQuality.NORMAL, SpecialKind.FROST_SPIRE)
        val sapphire = placeRound(g, cells)
        assertNull(g.findRecipeParts(sapphire, absoluteZero))
        assertTrue(g.hasSpecialOnField(SpecialKind.FROST_SPIRE))
        assertTrue(!g.hasSpecialOnField(SpecialKind.CRYSTAL_PRISON))
    }

    @Test
    fun specialGemShowsWhereItIsUsed() {
        assertEquals(listOf(SpecialKind.ABSOLUTE_ZERO), GemDB.recipesUsing(SpecialKind.CRYSTAL_PRISON).map { it.result })
        assertEquals("절대영도 = 서리 첨탑 + 수정 감옥 + 무결점 사파이어", GemDB.recipeText(absoluteZero))
    }
}
