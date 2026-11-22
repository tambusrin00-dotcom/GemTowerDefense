package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoveGemTest {

    private val corner = listOf(Cell(18, 18), Cell(17, 18), Cell(18, 17), Cell(17, 17), Cell(16, 18))

    /** 구석에 보석 5개 → 첫 번째를 특수 보석으로 남기고 웨이브 1을 끝낸 상태 (건설 단계, 골드 있음) */
    private fun afterFirstWave(): Game {
        val g = Game(Random(3))
        corner.forEach { g.placeGem(it, GemType.RUBY, GemQuality.CHIPPED) }
        val t = g.towerAt(corner[0])!!
        t.setGem(t.type, t.quality, SpecialKind.SUNBURST_CORE)
        g.keep(t)
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) { g.update(0.05f); time += 0.05f }
        assertEquals(GameState.BUILD_PLACE, g.state)
        g.addGold(100)
        return g
    }

    @Test
    fun moveToEmptyCellCostsGold() {
        val g = afterFirstWave()
        val tower = g.towerAt(corner[0])!!
        val gold = g.gold
        val target = Cell(10, 12)
        assertEquals(CellType.EMPTY, g.cellType(target))

        g.startMove(corner[0])
        assertEquals(corner[0], g.movingFrom)
        assertTrue(g.tapCell(target))

        assertSame(tower, g.towerAt(target))
        assertEquals(target, tower.cell)
        assertEquals(CellType.GEM, g.cellType(target))
        assertEquals(CellType.EMPTY, g.cellType(corner[0]))
        assertEquals(gold - Game.MOVE_COST, g.gold)
        assertNull(g.movingFrom)
        assertEquals(target, g.selected)
        assertTrue(tower.moved)
    }

    @Test
    fun normalGemsCannotMove() {
        val g = afterFirstWave()
        val normal = g.placeGem(Cell(10, 12), GemType.DIAMOND, GemQuality.PERFECT)
        assertFalse(g.canStartMove(normal.cell))
        g.startMove(normal.cell)
        assertNull(g.movingFrom)
    }

    @Test
    fun specialGemCanMoveOnlyOnce() {
        val g = afterFirstWave()
        g.startMove(corner[0])
        assertTrue(g.tapCell(Cell(10, 12)))
        val gold = g.gold

        assertFalse(g.canStartMove(Cell(10, 12)))
        g.startMove(Cell(10, 12))
        assertNull(g.movingFrom)
        assertEquals(gold, g.gold)
        assertEquals(CellType.GEM, g.cellType(Cell(10, 12)))
    }

    @Test
    fun swapWithRockKeepsPathUnchanged() {
        val g = afterFirstWave()
        val path = g.groundPath
        val rock = corner[1]
        assertEquals(CellType.ROCK, g.cellType(rock))

        g.startMove(corner[0])
        assertTrue(g.tapCell(rock))
        assertEquals(CellType.GEM, g.cellType(rock))
        assertEquals(CellType.ROCK, g.cellType(corner[0]))
        assertEquals(rock, g.towerAt(rock)!!.cell)
        assertEquals(path, g.groundPath)
    }

    @Test
    fun swapWithNormalGem() {
        val g = afterFirstWave()
        val newCell = Cell(10, 12)
        val fresh = g.placeGem(newCell, GemType.TOPAZ, GemQuality.CHIPPED)
        val special = g.towerAt(corner[0])!!

        g.startMove(corner[0])
        assertTrue(g.tapCell(newCell))
        assertSame(special, g.towerAt(newCell))
        assertSame(fresh, g.towerAt(corner[0]))
        assertEquals(newCell, special.cell)
        assertEquals(corner[0], fresh.cell)
    }

    @Test
    fun swapWithAnotherSpecialUsesBothMoves() {
        val g = afterFirstWave()
        val a = g.towerAt(corner[0])!!
        val b = g.placeGem(Cell(10, 12), GemType.SAPPHIRE, GemQuality.CHIPPED)
        b.setGem(b.type, b.quality, SpecialKind.FROST_SPIRE)

        g.startMove(a.cell)
        assertTrue(g.tapCell(b.cell))
        assertTrue(a.moved)
        assertTrue("자리를 바꾼 특수 보석도 이동한 것으로 침", b.moved)
        assertFalse(g.canStartMove(b.cell))
    }

    @Test
    fun cannotSwapWithSpecialThatAlreadyMoved() {
        val g = afterFirstWave()
        val a = g.towerAt(corner[0])!!
        val b = g.placeGem(Cell(10, 12), GemType.SAPPHIRE, GemQuality.CHIPPED)
        b.setGem(b.type, b.quality, SpecialKind.FROST_SPIRE)
        // b를 먼저 한 번 옮김
        g.startMove(b.cell)
        assertTrue(g.tapCell(Cell(11, 12)))
        val gold = g.gold

        g.startMove(a.cell)
        assertFalse(g.tapCell(b.cell))
        assertEquals(corner[0], a.cell)
        assertEquals(Cell(11, 12), b.cell)
        assertFalse(a.moved)
        assertEquals(gold, g.gold)
    }

    @Test
    fun cannotMoveIfItBlocksThePath() {
        val g = afterFirstWave()
        // 출발점 (0,18)의 이웃 중 (1,18)을 막아두고, 보석을 (0,17)로 옮기려 하면 길이 막힘
        g.setRock(Cell(1, 18))
        val gold = g.gold
        g.startMove(corner[0])
        assertFalse(g.tapCell(Cell(0, 17)))
        assertEquals(CellType.EMPTY, g.cellType(Cell(0, 17)))
        assertEquals(CellType.GEM, g.cellType(corner[0]))
        assertEquals(gold, g.gold)
        assertFalse("거부되면 이동 횟수를 쓰지 않음", g.towerAt(corner[0])!!.moved)
        assertEquals("거부된 뒤에도 다른 칸을 고를 수 있어야 함", corner[0], g.movingFrom)
    }

    @Test
    fun cannotMoveOntoWaypoint() {
        val g = afterFirstWave()
        g.startMove(corner[0])
        assertFalse(g.tapCell(Game.WAYPOINTS[3]))
        assertEquals(CellType.WAYPOINT, g.cellType(Game.WAYPOINTS[3]))
    }

    @Test
    fun tappingSameCellCancelsForFree() {
        val g = afterFirstWave()
        val gold = g.gold
        g.startMove(corner[0])
        assertTrue(g.tapCell(corner[0]))
        assertNull(g.movingFrom)
        assertEquals(gold, g.gold)
        assertFalse("취소하면 이동 횟수를 쓰지 않음", g.towerAt(corner[0])!!.moved)
    }

    @Test
    fun cannotMoveDuringWaveOrWithoutGold() {
        val g = Game(Random(3))
        corner.forEach { g.placeGem(it, GemType.RUBY, GemQuality.CHIPPED) }
        val t = g.towerAt(corner[0])!!
        t.setGem(t.type, t.quality, SpecialKind.SUNBURST_CORE)
        assertFalse("골드 0", g.canStartMove(corner[0]))
        g.addGold(100)
        assertTrue(g.canStartMove(corner[0]))
        g.keep(t)
        assertEquals(GameState.WAVE, g.state)
        assertFalse("전투 중", g.canStartMove(corner[0]))
        g.startMove(corner[0])
        assertNull(g.movingFrom)
    }

    @Test
    fun choosingAGemEndsMoveMode() {
        val g = afterFirstWave()
        val cells = listOf(Cell(10, 12), Cell(11, 12), Cell(12, 12), Cell(10, 13), Cell(11, 13))
        cells.forEach { g.placeGem(it, GemType.RUBY, GemQuality.CHIPPED) }
        g.startMove(corner[0])
        g.keep(g.towerAt(cells[0])!!)
        assertNull(g.movingFrom)
    }
}
