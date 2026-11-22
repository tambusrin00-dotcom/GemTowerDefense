package com.tambusrin.gemtower.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpecialGemTest {

    @Test
    fun everySpecialHasExactlyOneValidRecipe() {
        assertEquals(13, GemDB.recipes.size)
        for (k in SpecialKind.entries.filterNot { it.hidden }) {  // 히든 보석은 조합법 없음
            assertEquals("recipe count for $k", 1, GemDB.recipes.count { it.result == k })
        }
        // 재료 조합이 같은 조합법이 없어야 함
        assertEquals(GemDB.recipes.size, GemDB.recipes.map { it.parts.toSet() }.toSet().size)
        for (r in GemDB.recipes) {
            assertEquals(3, r.parts.size)
            assertEquals("parts must be distinct types: $r", 3, r.parts.map { it.type }.toSet().size)
            assertTrue(GemDB.special(r.result).desc.isNotEmpty())
        }
        assertEquals(listOf(8, 3, 2), (0..2).map { t -> GemDB.recipes.count { it.tier == t } })
    }

    @Test
    fun higherTierSpecialsAreStronger() {
        // 한 대당 피해 + 독 총량
        fun power(k: SpecialKind) = GemDB.special(k).let { it.damage + it.poisonDps * it.poisonDur }
        fun maxDmg(tier: Int) = GemDB.recipes.filter { it.tier == tier }.maxOf { power(it.result) }
        fun minDmg(tier: Int) = GemDB.recipes.filter { it.tier == tier }.minOf { power(it.result) }
        assertTrue(minDmg(1) > maxDmg(0))
        assertTrue(minDmg(2) > maxDmg(1))
    }

    @Test
    fun usedAsIngredientLookup() {
        val uses = GemDB.recipesUsing(GemKey(GemType.SAPPHIRE, GemQuality.NORMAL)).map { it.result }.toSet()
        assertEquals(setOf(SpecialKind.FROST_SPIRE, SpecialKind.CRYSTAL_PRISON, SpecialKind.NEBULA_CANNON, SpecialKind.THUNDER_HAMMER), uses)
    }

    /** 경로 바로 옆에 특수 보석 하나를 두고 웨이브를 시작한 게임 */
    private fun gameWithSpecial(kind: SpecialKind, seed: Int = 7): Pair<Game, Tower> {
        val g = Game(Random(seed))
        val path = g.groundPath.toSet()
        val beside = g.groundPath.drop(5).flatMap {
            listOf(Cell(it.x + 1, it.y), Cell(it.x - 1, it.y), Cell(it.x, it.y + 1), Cell(it.x, it.y - 1))
        }.first { g.inBounds(it) && it !in path && g.canBuild(it) }
        val t = g.placeGem(beside, GemType.RUBY, GemQuality.CHIPPED)
        val far = listOf(Cell(18, 18), Cell(17, 18), Cell(18, 17), Cell(17, 17)).filter { it != beside }
        far.take(4).forEach { g.placeGem(it, GemType.RUBY, GemQuality.CHIPPED) }
        t.setGem(t.type, t.quality, kind)
        g.keep(t)
        return g to t
    }

    @Test
    fun stormSpireChainsBetweenEnemies() {
        val (g, t) = gameWithSpecial(SpecialKind.STORM_SPIRE)
        var sawBeam = false
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) {
            g.update(0.05f)
            if (g.beams.isNotEmpty()) sawBeam = true
            time += 0.05f
        }
        assertTrue("chain lightning should produce beams", sawBeam)
        assertTrue(t.kills > 0)
    }

    @Test
    fun goldenIdolGivesBonusGold() {
        val (g, t) = gameWithSpecial(SpecialKind.GOLDEN_IDOL)
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f) {
            g.update(0.05f); time += 0.05f
        }
        assertEquals(10, t.kills)
        // 처치당 (기본 1 + 보너스 2) x 10 + 웨이브 보너스 12
        assertEquals(10 * 3 + 12, g.gold)
    }

    @Test
    fun crystalPrisonStunsEnemies() {
        val (g, _) = gameWithSpecial(SpecialKind.CRYSTAL_PRISON)
        var sawStun = false
        var time = 0f
        while (g.state == GameState.WAVE && time < 300f && !sawStun) {
            g.enemies.forEach { if (it.hp > 0f) it.hp = 1e6f } // 한 방에 죽지 않게
            g.update(0.05f)
            if (g.enemies.any { it.stunned }) sawStun = true
            time += 0.05f
        }
        assertTrue(sawStun)
    }

    @Test
    fun bloodStoneExecutesLowHpEnemy() {
        val (g, t) = gameWithSpecial(SpecialKind.BLOOD_STONE)
        // 스폰된 첫 적의 체력을 처형 기준 바로 위로 맞춰두고, 한 대 맞으면 처형되는지 확인
        var time = 0f
        while (g.enemies.isEmpty() && time < 5f) { g.update(0.02f); time += 0.02f }
        val e = g.enemies.first()
        e.hp = e.maxHp * 0.16f
        while (!e.dead && g.state == GameState.WAVE && time < 300f) { g.update(0.02f); time += 0.02f }
        assertTrue(e.dead)
        assertTrue(t.kills >= 1)
    }

    @Test
    fun specialUpgradeScalesDamage() {
        val base = GemDB.special(SpecialKind.STAR_HEART)
        val up = GemDB.special(SpecialKind.STAR_HEART, 2)
        assertEquals(base.damage * 1.7f * 1.7f, up.damage, 0.01f)
        assertEquals(base.chainCount, up.chainCount)
    }
}
