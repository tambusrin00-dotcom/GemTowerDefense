package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.random.Random

class GameTest {

    private fun newGame() = Game(Random(42))

    /** 경로에서 멀리 떨어진 빈 칸들 (배치 테스트용) */
    private fun freeCells(g: Game): List<Cell> {
        val onPath = g.groundPath.toSet()
        val out = ArrayList<Cell>()
        for (x in 0 until Game.W) for (y in 0 until Game.H) {
            val c = Cell(x, y)
            if (c !in onPath && g.cellType(c) == CellType.EMPTY) out.add(c)
        }
        return out
    }

    private fun runUntil(g: Game, maxSeconds: Float = 600f, cond: () -> Boolean) {
        var t = 0f
        while (!cond() && t < maxSeconds) {
            g.update(0.05f)
            t += 0.05f
        }
    }

    @Test
    fun initialPathVisitsAllWaypointsInOrder() {
        val g = newGame()
        val path = g.groundPath
        assertTrue(path.isNotEmpty())
        assertEquals(Game.WAYPOINTS.first(), path.first())
        assertEquals(Game.WAYPOINTS.last(), path.last())
        var idx = 0
        for (c in path) if (idx < Game.WAYPOINTS.size && c == Game.WAYPOINTS[idx]) idx++
        assertEquals(Game.WAYPOINTS.size, idx)
        // 인접한 칸으로만 이동
        for (i in 1 until path.size) {
            val d = kotlin.math.abs(path[i].x - path[i - 1].x) + kotlin.math.abs(path[i].y - path[i - 1].y)
            assertEquals(1, d)
        }
    }

    @Test
    fun cannotFullyBlockPath() {
        val g = newGame()
        // 출발점 (0,18)의 이웃은 (1,18)과 (0,17)뿐
        g.setRock(Cell(1, 18))
        assertFalse(g.canBuild(Cell(0, 17)))
        assertFalse(g.tapCell(Cell(0, 17)))
        assertEquals(CellType.EMPTY, g.cellType(Cell(0, 17)))
        assertFalse(g.canBuild(Game.WAYPOINTS[2])) // 체크포인트에는 못 지음
    }

    @Test
    fun placingFiveGemsThenKeepingOneStartsWave() {
        val g = newGame()
        val cells = freeCells(g).take(5)
        cells.forEach { assertTrue(g.tapCell(it)) }
        assertEquals(GameState.BUILD_CHOOSE, g.state)
        assertEquals(5, g.roundGems.size)

        val chosen = g.roundGems[2]
        g.keep(chosen)
        assertEquals(GameState.WAVE, g.state)
        assertTrue(chosen.kept)
        assertEquals(CellType.GEM, g.cellType(chosen.cell))
        assertEquals(4, cells.count { g.cellType(it) == CellType.ROCK })
        assertTrue(g.roundGems.isEmpty())
    }

    @Test
    fun combineTwoSameGemsUpgradesQuality() {
        val g = newGame()
        val cells = freeCells(g)
        val a = g.placeGem(cells[0], GemType.RUBY, GemQuality.CHIPPED)
        g.placeGem(cells[1], GemType.RUBY, GemQuality.CHIPPED)
        g.placeGem(cells[2], GemType.TOPAZ, GemQuality.CHIPPED)
        g.placeGem(cells[3], GemType.EMERALD, GemQuality.CHIPPED)
        val e = g.placeGem(cells[4], GemType.SAPPHIRE, GemQuality.CHIPPED)
        assertTrue(g.canCombine(a, 1))
        assertFalse(g.canCombine(a, 2))
        assertFalse(g.canCombine(e, 1))

        g.combine(a, 1)
        assertEquals(GemQuality.FLAWED, a.quality)
        assertTrue(a.kept)
        assertEquals(GameState.WAVE, g.state)
    }

    @Test
    fun recipeUsesKeptGemsAndTurnsThemToRock() {
        val g = newGame()
        val cells = freeCells(g)
        // 재료 하나를 이전 라운드에서 남겨둔 상황
        val diamond = g.placeGem(cells[0], GemType.DIAMOND, GemQuality.FLAWED)
        repeat(4) { g.placeGem(cells[1 + it], GemType.TOPAZ, GemQuality.FLAWLESS) }
        g.keep(diamond)
        runUntil(g) { g.state != GameState.WAVE }
        assertTrue(g.state == GameState.BUILD_PLACE || g.state == GameState.GAME_OVER)
        if (g.state != GameState.BUILD_PLACE) return

        val sapphire = g.placeGem(cells[5], GemType.SAPPHIRE, GemQuality.NORMAL)
        g.placeGem(cells[6], GemType.AMETHYST, GemQuality.CHIPPED)
        g.placeGem(cells[7], GemType.RUBY, GemQuality.CHIPPED)
        g.placeGem(cells[8], GemType.RUBY, GemQuality.CHIPPED)
        g.placeGem(cells[9], GemType.RUBY, GemQuality.CHIPPED)

        val frost = GemDB.recipes.first { it.result == SpecialKind.FROST_SPIRE }
        assertNotNull(g.findRecipeParts(sapphire, frost))
        assertNull(g.findRecipeParts(sapphire, GemDB.recipes.first { it.result == SpecialKind.VOID_EYE }))

        g.makeSpecial(sapphire, frost)
        assertEquals(SpecialKind.FROST_SPIRE, sapphire.special)
        assertEquals(CellType.ROCK, g.cellType(diamond.cell)) // 남겨둔 재료는 바위가 됨
        assertEquals(CellType.ROCK, g.cellType(cells[6]))
        assertEquals(GameState.WAVE, g.state)
    }

    @Test
    fun enemiesLeakWithoutDefenseAndWaveEnds() {
        val g = newGame()
        // 경로에서 먼 구석에 약한 보석 배치 → 적을 거의 못 막음
        val far = listOf(Cell(18, 18), Cell(17, 18), Cell(18, 17), Cell(17, 17), Cell(16, 18))
        far.forEach { g.placeGem(it, GemType.RUBY, GemQuality.CHIPPED) }
        g.keep(g.roundGems[0])
        runUntil(g) { g.state != GameState.WAVE }
        assertEquals(GameState.BUILD_PLACE, g.state)
        assertEquals(2, g.wave)
        assertTrue("leaked enemies should cost lives", g.lives < Game.START_LIVES)
        assertTrue(g.gold >= 10 + 2) // 웨이브 보너스
    }

    @Test
    fun strongTowerOnPathKillsEnemiesAndEarnsGold() {
        val g = newGame()
        // 경로 옆 칸에 강한 보석 + 나머지는 먼 곳
        val path = g.groundPath.toSet()
        val besidePath = g.groundPath.drop(5).flatMap {
            listOf(Cell(it.x + 1, it.y), Cell(it.x - 1, it.y), Cell(it.x, it.y + 1), Cell(it.x, it.y - 1))
        }.first { g.inBounds(it) && it !in path && g.canBuild(it) }
        val strong = g.placeGem(besidePath, GemType.DIAMOND, GemQuality.PERFECT)
        val others = freeCells(g).filter { it != besidePath }.takeLast(4)
        others.forEach { g.placeGem(it, GemType.RUBY, GemQuality.CHIPPED) }
        g.keep(strong)
        runUntil(g) { g.state != GameState.WAVE }

        assertEquals(GameState.BUILD_PLACE, g.state)
        assertEquals(Game.START_LIVES, g.lives)
        assertEquals(10, strong.kills)
        assertTrue(strong.totalDamage > 0f)
        assertEquals(10 * 1 + 12, g.gold) // 처치 보상 10 + 웨이브 보너스 12
    }

    @Test
    fun removingRockCostsGoldAndReopensCell() {
        val g = newGame()
        val far = listOf(Cell(18, 18), Cell(17, 18), Cell(18, 17), Cell(17, 17), Cell(16, 18))
        far.forEach { g.placeGem(it, GemType.RUBY, GemQuality.CHIPPED) }
        g.keep(g.roundGems[0])
        runUntil(g) { g.state != GameState.WAVE }
        val goldBefore = g.gold
        g.removeRock(far[1])
        assertEquals(CellType.EMPTY, g.cellType(far[1]))
        assertEquals(goldBefore - Game.ROCK_REMOVE_COST, g.gold)
    }

    @Test
    fun chanceUpgradeChangesQualityRolls() {
        val g = newGame()
        g.addGold(1000)
        repeat(GemDB.maxChanceLevel) { g.upgradeChance() }
        assertEquals(GemDB.maxChanceLevel, g.chanceLevel)
        val rng = Random(1)
        assertTrue((1..2000).any { GemDB.rollQuality(GemDB.maxChanceLevel, rng) == GemQuality.PERFECT })
        repeat(200) { assertEquals(GemQuality.CHIPPED, GemDB.rollQuality(0, rng)) }
    }

    @Test
    fun bossAndFlyingWaves() {
        val g = newGame()
        assertTrue(g.waveInfo(10).boss)
        assertEquals(1, g.waveInfo(10).count)
        assertTrue(g.waveInfo(4).flying)
        assertFalse(g.waveInfo(20).flying)
        assertTrue(g.waveInfo(30).hp > g.waveInfo(29).hp)
    }

    @Test
    fun lateWavesKeepGettingHarderUpToFinalWave() {
        val g = newGame()
        // 1~30웨이브는 기존 곡선 그대로 (일반 웨이브 적 10마리)
        assertEquals(10, g.waveInfo(29).count)
        for (w in 31..Game.MAX_WAVE) {
            val info = g.waveInfo(w)
            assertTrue("wave $w hp must be finite", info.hp.isFinite() && info.hp > 0f)
            assertTrue(info.count >= 1)
            // 웨이브 종류 주기(20) 단위로 같은 종류끼리 비교하면 항상 더 강함
            if (w > 50) assertTrue("wave $w", info.hp > g.waveInfo(w - 20).hp)
        }
        assertTrue(g.waveInfo(200).count > g.waveInfo(31).count || g.waveInfo(200).speed > g.waveInfo(31).speed)
    }

    @Test
    fun gameContinuesAfterWave30() {
        val g = newGame()
        g.debugSetup(wave = 30, gold = 0, chanceLevel = 0, lives = 20)
        g.debugStartWave()
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) { g.update(0.05f); time += 0.05f }
        assertEquals(GameState.BUILD_PLACE, g.state)
        assertEquals(31, g.wave)
    }

    @Test
    fun hpDoublesEveryFifthWaveAfter30() {
        val g = newGame()
        assertEquals(201, Game.MAX_WAVE)
        // 34, 35는 둘 다 일반 웨이브: 8% 상승 + 2배
        assertEquals(1.08f * 2f, g.waveInfo(35).hp / g.waveInfo(34).hp, 0.001f)
        // 37, 39는 둘 다 일반 웨이브이고 사이에 5의 배수가 없음: 8%씩만 상승
        assertEquals(1.08f * 1.08f, g.waveInfo(39).hp / g.waveInfo(37).hp, 0.001f)
        // 30웨이브까지는 2배 적용 없음
        assertEquals(15f * 1.21f.pow(28) + 4f * 29, g.waveInfo(29).hp, 0.01f)
        assertTrue(g.waveInfo(200).hp.isFinite())
    }

    @Test
    fun finalBossHasTotalHpOfAllPreviousEnemies() {
        val g = newGame()
        val total = (1..200).sumOf { w -> g.waveInfo(w).let { it.count.toDouble() * it.hp } }
        val boss = g.waveInfo(201)
        assertEquals(1, boss.count)
        assertTrue(boss.boss)
        assertEquals("최종 보스", boss.label)
        assertEquals(total.toFloat(), boss.hp, total.toFloat() * 1e-5f)
        assertTrue(boss.hp.isFinite())
    }

    @Test
    fun wave200LeadsToFinalBoss() {
        val g = newGame()
        g.debugSetup(wave = 200, gold = 0, chanceLevel = 0, lives = 20)
        g.debugStartWave()
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) { g.update(0.05f); time += 0.05f }
        assertEquals(GameState.BUILD_PLACE, g.state)
        assertEquals(201, g.wave)
    }

    @Test
    fun finalBossLeakIsGameOver() {
        val g = newGame()
        g.debugSetup(wave = Game.MAX_WAVE, gold = 0, chanceLevel = 0, lives = 20)
        g.debugStartWave()
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) { g.update(0.05f); time += 0.05f }
        assertEquals(GameState.GAME_OVER, g.state)
    }

    @Test
    fun killingFinalBossIsVictory() {
        val g = newGame()
        g.debugSetup(wave = Game.MAX_WAVE, gold = 0, chanceLevel = 0, lives = 20)
        val path = g.groundPath.toSet()
        val beside = g.groundPath.drop(5).flatMap {
            listOf(Cell(it.x + 1, it.y), Cell(it.x - 1, it.y), Cell(it.x, it.y + 1), Cell(it.x, it.y - 1))
        }.first { g.inBounds(it) && it !in path && g.canBuild(it) }
        g.debugKeptGem(beside, GemType.DIAMOND, GemQuality.FLAWLESS)
        g.debugStartWave()
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) {
            g.enemies.forEach { if (it.hp > 1f) it.hp = 1f }  // 한 방에 죽도록
            g.update(0.05f); time += 0.05f
        }
        assertEquals(GameState.VICTORY, g.state)
    }
}
