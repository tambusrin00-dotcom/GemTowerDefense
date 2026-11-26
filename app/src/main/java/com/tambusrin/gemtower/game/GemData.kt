package com.tambusrin.gemtower.game

import kotlin.math.pow
import kotlin.random.Random

enum class GemType(val label: String, val color: Int) {
    RUBY("루비", 0xFFF2263A.toInt()),
    SAPPHIRE("사파이어", 0xFF3373FF.toInt()),
    EMERALD("에메랄드", 0xFF26D95A.toInt()),
    TOPAZ("토파즈", 0xFFFFCC26.toInt()),
    AMETHYST("자수정", 0xFFB34DF2.toInt()),
    DIAMOND("다이아몬드", 0xFFEAF4FF.toInt()),
}

enum class GemQuality(val label: String, val baseDamage: Float) {
    CHIPPED("깨진", 8f),
    FLAWED("흠있는", 18f),
    NORMAL("일반", 40f),
    FLAWLESS("무결점", 90f),
    PERFECT("완벽한", 200f),
}

/** hidden = 조합법 없이 아주 낮은 확률로만 나오는 히든 보석 */
enum class SpecialKind(val label: String, val hidden: Boolean = false) {
    // 하급
    FROST_SPIRE("서리 첨탑"),
    VENOM_FANG("맹독 송곳니"),
    SUNBURST_CORE("태양 폭발핵"),
    VOID_EYE("공허의 눈"),
    STORM_SPIRE("폭풍 첨탑"),
    GOLDEN_IDOL("황금 우상"),
    CRYSTAL_PRISON("수정 감옥"),
    BLOOD_STONE("피의 결정"),
    SPRAY_SPIRE("물보라 첨탑"),
    // 중급
    NEBULA_CANNON("성운 포대"),
    PLAGUE_LORD("역병 군주"),
    THUNDER_HAMMER("천둥 망치"),
    // 상급
    STAR_HEART("별의 심장"),
    CHAOS_RIFT("혼돈의 균열"),
    VOLCANO_HEART("화산의 심장"),
    ABYSS_TIDE("심연의 해일"),
    WORLD_TREE("맹독 세계수"),
    SKY_ARROW("천공의 화살"),
    // 전설 (특수 보석 2개 + 무결점 보석)
    ABSOLUTE_ZERO("절대영도"),
    THUNDER_GOD("뇌신의 심판"),
    GOLDEN_REAPER("황금 사신"),
    // 히든 (조합 불가, 보석을 놓을 때 0.000001% 확률)
    URANIUM("우라늄", hidden = true),
}

data class GemStats(
    val name: String,
    val color: Int,
    val damage: Float,
    val range: Float = 3f,
    val cooldown: Float = 1f,
    val splashRadius: Float = 0f,
    val splashPct: Float = 0f,
    val slowPct: Float = 0f,
    val slowDur: Float = 0f,
    val poisonDps: Float = 0f,
    val poisonDur: Float = 0f,
    val targets: Int = 1,
    val critChance: Float = 0f,
    val critMult: Float = 1f,
    val airMult: Float = 1f,
    /** 연쇄 번개: 맞은 적에서 가까운 적에게 튕기는 횟수 */
    val chainCount: Int = 0,
    val chainRange: Float = 0f,
    /** 튕길 때마다 피해 배율 */
    val chainPct: Float = 0f,
    val stunChance: Float = 0f,
    val stunDur: Float = 0f,
    /** 이 보석이 처치한 적이 주는 추가 골드 */
    val bountyBonus: Int = 0,
    /** 체력이 이 비율 이하로 떨어진 적을 즉시 처치 (보스 제외) */
    val executePct: Float = 0f,
    val desc: String = "",
)

data class GemKey(val type: GemType, val quality: GemQuality) {
    override fun toString() = "${quality.label} ${type.label}"
}

/**
 * 조합법. parts = 일반 보석 재료, specialParts = 특수 보석 재료 (전설 조합법만 사용).
 * 일반 보석 재료 중 하나는 반드시 이번 라운드 보석이어야 합성할 수 있음.
 */
data class Recipe(val result: SpecialKind, val parts: List<GemKey>, val specialParts: List<SpecialKind> = emptyList()) {
    /** 0 = 하급, 1 = 중급, 2 = 상급 (가장 높은 재료 등급 기준), 3 = 전설 (특수 보석이 재료) */
    val tier: Int
        get() = if (specialParts.isNotEmpty()) 3 else when (parts.maxOf { it.quality }) {
            GemQuality.PERFECT -> 2
            GemQuality.FLAWLESS -> 1
            else -> 0
        }

    /** 화면 표시용 재료 이름 (특수 보석 먼저) */
    val partLabels: List<String>
        get() = specialParts.map { it.label } + parts.map { it.toString() }
}

/** 보석 수치 / 확률표 / 조합법. 밸런스 조절은 여기서. */
object GemDB {
    // [레벨][깨진, 흠있는, 일반, 무결점, 완벽한] (%)
    private val chanceTable = arrayOf(
        intArrayOf(100, 0, 0, 0, 0),
        intArrayOf(70, 30, 0, 0, 0),
        intArrayOf(60, 30, 10, 0, 0),
        intArrayOf(50, 30, 20, 0, 0),
        intArrayOf(40, 30, 20, 10, 0),
        intArrayOf(30, 30, 30, 10, 0),
        intArrayOf(20, 30, 30, 20, 0),
        intArrayOf(10, 30, 30, 30, 0),
        intArrayOf(5, 25, 30, 30, 10),
    )

    val maxChanceLevel get() = chanceTable.size - 1
    fun chanceCost(level: Int) = 20 + 20 * level

    const val MAX_SPECIAL_LEVEL = 5
    fun specialUpgradeCost(level: Int) = 60 + 60 * level

    /** 보석을 하나 놓을 때 히든 보석(우라늄)이 나올 확률: 0.000001% = 1e-8 */
    const val HIDDEN_CHANCE = 1e-8

    val tierNames = listOf("하급", "중급", "상급", "전설")

    private fun k(t: GemType, q: GemQuality) = GemKey(t, q)
    private val C = GemQuality.CHIPPED
    private val F = GemQuality.FLAWED
    private val N = GemQuality.NORMAL
    private val FL = GemQuality.FLAWLESS
    private val P = GemQuality.PERFECT

    val recipes = listOf(
        // ---- 하급: 일반 + 흠있는/깨진 ----
        Recipe(SpecialKind.FROST_SPIRE, listOf(k(GemType.SAPPHIRE, N), k(GemType.DIAMOND, F), k(GemType.AMETHYST, C))),
        Recipe(SpecialKind.VENOM_FANG, listOf(k(GemType.EMERALD, N), k(GemType.RUBY, F), k(GemType.TOPAZ, C))),
        Recipe(SpecialKind.SUNBURST_CORE, listOf(k(GemType.RUBY, N), k(GemType.TOPAZ, F), k(GemType.DIAMOND, C))),
        Recipe(SpecialKind.VOID_EYE, listOf(k(GemType.AMETHYST, N), k(GemType.SAPPHIRE, F), k(GemType.EMERALD, F))),
        Recipe(SpecialKind.STORM_SPIRE, listOf(k(GemType.TOPAZ, N), k(GemType.SAPPHIRE, F), k(GemType.RUBY, C))),
        Recipe(SpecialKind.GOLDEN_IDOL, listOf(k(GemType.DIAMOND, N), k(GemType.TOPAZ, C), k(GemType.EMERALD, C))),
        Recipe(SpecialKind.CRYSTAL_PRISON, listOf(k(GemType.SAPPHIRE, N), k(GemType.AMETHYST, F), k(GemType.TOPAZ, F))),
        Recipe(SpecialKind.BLOOD_STONE, listOf(k(GemType.RUBY, N), k(GemType.EMERALD, F), k(GemType.AMETHYST, C))),
        Recipe(SpecialKind.SPRAY_SPIRE, listOf(k(GemType.TOPAZ, N), k(GemType.DIAMOND, F), k(GemType.SAPPHIRE, C))),
        // ---- 중급: 무결점 + 일반 ----
        Recipe(SpecialKind.NEBULA_CANNON, listOf(k(GemType.AMETHYST, FL), k(GemType.DIAMOND, N), k(GemType.SAPPHIRE, N))),
        Recipe(SpecialKind.PLAGUE_LORD, listOf(k(GemType.EMERALD, FL), k(GemType.TOPAZ, N), k(GemType.RUBY, N))),
        Recipe(SpecialKind.THUNDER_HAMMER, listOf(k(GemType.DIAMOND, FL), k(GemType.TOPAZ, N), k(GemType.SAPPHIRE, N))),
        // ---- 상급: 완벽한 + 무결점 ----
        Recipe(SpecialKind.STAR_HEART, listOf(k(GemType.DIAMOND, P), k(GemType.RUBY, FL), k(GemType.SAPPHIRE, FL))),
        Recipe(SpecialKind.CHAOS_RIFT, listOf(k(GemType.AMETHYST, P), k(GemType.EMERALD, FL), k(GemType.TOPAZ, FL))),
        Recipe(SpecialKind.VOLCANO_HEART, listOf(k(GemType.RUBY, P), k(GemType.TOPAZ, FL), k(GemType.DIAMOND, FL))),
        Recipe(SpecialKind.ABYSS_TIDE, listOf(k(GemType.SAPPHIRE, P), k(GemType.EMERALD, FL), k(GemType.AMETHYST, FL))),
        Recipe(SpecialKind.WORLD_TREE, listOf(k(GemType.EMERALD, P), k(GemType.SAPPHIRE, FL), k(GemType.DIAMOND, FL))),
        Recipe(SpecialKind.SKY_ARROW, listOf(k(GemType.TOPAZ, P), k(GemType.RUBY, FL), k(GemType.AMETHYST, FL))),
        // ---- 전설: 특수 보석 2개 + 무결점 (재료 특수 보석은 바위가 되고 강화 단계도 사라짐) ----
        Recipe(SpecialKind.ABSOLUTE_ZERO, listOf(k(GemType.SAPPHIRE, FL)), listOf(SpecialKind.FROST_SPIRE, SpecialKind.CRYSTAL_PRISON)),
        Recipe(SpecialKind.THUNDER_GOD, listOf(k(GemType.TOPAZ, FL)), listOf(SpecialKind.STORM_SPIRE, SpecialKind.THUNDER_HAMMER)),
        Recipe(SpecialKind.GOLDEN_REAPER, listOf(k(GemType.DIAMOND, FL)), listOf(SpecialKind.GOLDEN_IDOL, SpecialKind.BLOOD_STONE)),
    )

    fun recipeFor(k: SpecialKind) = recipes.first { it.result == k }

    /** 이 보석이 재료로 들어가는 조합법 */
    fun recipesUsing(key: GemKey) = recipes.filter { key in it.parts }

    /** 이 특수 보석이 재료로 들어가는 전설 조합법 */
    fun recipesUsing(k: SpecialKind) = recipes.filter { k in it.specialParts }

    fun rollQuality(level: Int, rng: Random): GemQuality {
        val row = chanceTable[level.coerceIn(0, maxChanceLevel)]
        val r = rng.nextInt(100)
        var acc = 0
        for (i in row.indices) {
            acc += row[i]
            if (r < acc) return GemQuality.entries[i]
        }
        return GemQuality.CHIPPED
    }

    fun chanceText(level: Int): String {
        val row = chanceTable[level.coerceIn(0, maxChanceLevel)]
        return row.indices.filter { row[it] > 0 }
            .joinToString("  ") { "${GemQuality.entries[it].label} ${row[it]}%" }
    }

    fun stats(t: GemType, q: GemQuality): GemStats {
        val qi = q.ordinal
        val bd = q.baseDamage
        val name = "${q.label} ${t.label}"
        return when (t) {
            GemType.RUBY -> GemStats(name, t.color, bd, splashRadius = 1.2f + 0.1f * qi, splashPct = 0.5f)
            GemType.SAPPHIRE -> GemStats(name, t.color, bd * 0.7f, range = 3.2f, slowPct = 0.2f + 0.06f * qi, slowDur = 2f)
            GemType.EMERALD -> GemStats(name, t.color, bd * 0.6f, cooldown = 0.9f, poisonDps = bd * 0.5f, poisonDur = 4f)
            GemType.TOPAZ -> GemStats(name, t.color, bd * 0.55f, range = 3.2f, targets = 2 + qi / 2)
            GemType.AMETHYST -> GemStats(name, t.color, bd * 0.9f, range = 4.5f + 0.1f * qi, cooldown = 1.3f, airMult = 2f)
            GemType.DIAMOND -> GemStats(name, t.color, bd * 1.3f, cooldown = 0.85f, critChance = 0.2f + 0.04f * qi, critMult = 2.5f)
        }
    }

    fun special(k: SpecialKind, level: Int = 0): GemStats {
        val n = k.label
        val base = when (k) {
            SpecialKind.FROST_SPIRE -> GemStats(n, 0xFF99EBFF.toInt(), 70f, range = 4f, cooldown = 0.8f,
                slowPct = 0.5f, slowDur = 2.5f, splashRadius = 1.5f, splashPct = 0.4f,
                desc = "주변 적을 얼려 크게 둔화시킵니다.")
            SpecialKind.VENOM_FANG -> GemStats(n, 0xFF73FF33.toInt(), 45f, range = 3.8f, cooldown = 0.7f,
                poisonDps = 60f, poisonDur = 4f, targets = 3,
                desc = "여러 적에게 강한 독을 겁니다.")
            SpecialKind.SUNBURST_CORE -> GemStats(n, 0xFFFF8C1A.toInt(), 150f, range = 3.5f, cooldown = 1.1f,
                splashRadius = 2f, splashPct = 0.7f,
                desc = "넓은 범위를 폭발로 태웁니다.")
            SpecialKind.VOID_EYE -> GemStats(n, 0xFF8C40D9.toInt(), 260f, range = 5.5f, cooldown = 1.4f,
                airMult = 2.5f, critChance = 0.2f, critMult = 2f,
                desc = "먼 거리의 적을 강하게 저격합니다.")
            SpecialKind.STORM_SPIRE -> GemStats(n, 0xFFFFF07A.toInt(), 55f, range = 3.8f, cooldown = 0.9f,
                chainCount = 4, chainRange = 2.2f, chainPct = 0.8f,
                desc = "번개가 주변 적에게 4번 튕깁니다.")
            SpecialKind.GOLDEN_IDOL -> GemStats(n, 0xFFFFB31A.toInt(), 40f, range = 3.5f, cooldown = 0.8f,
                targets = 2, bountyBonus = 2,
                desc = "이 보석이 처치한 적은 골드를 2 더 줍니다.")
            SpecialKind.CRYSTAL_PRISON -> GemStats(n, 0xFF7FE0D0.toInt(), 60f, range = 3.5f, cooldown = 1f,
                stunChance = 0.25f, stunDur = 1f, slowPct = 0.3f, slowDur = 2f,
                desc = "적을 수정에 가둬 기절시킵니다.")
            SpecialKind.BLOOD_STONE -> GemStats(n, 0xFFC4163A.toInt(), 90f, range = 3.2f, cooldown = 0.9f,
                splashRadius = 1.3f, splashPct = 0.5f, executePct = 0.15f,
                desc = "빈사 상태의 적을 즉시 처치합니다.")
            SpecialKind.SPRAY_SPIRE -> GemStats(n, 0xFF4FC3F7.toInt(), 50f, range = 3.6f, cooldown = 0.8f,
                targets = 3, slowPct = 0.3f, slowDur = 2f,
                desc = "여러 적에게 물보라를 뿌려 함께 둔화시킵니다.")
            SpecialKind.NEBULA_CANNON -> GemStats(n, 0xFF5A6BFF.toInt(), 380f, range = 6f, cooldown = 1.3f,
                splashRadius = 1.8f, splashPct = 0.6f, airMult = 2.5f,
                desc = "먼 거리를 광역 포격합니다. 공중 유닛에 강합니다.")
            SpecialKind.PLAGUE_LORD -> GemStats(n, 0xFF3FAF3F.toInt(), 160f, range = 4.2f, cooldown = 0.8f,
                poisonDps = 200f, poisonDur = 4f, targets = 4, slowPct = 0.2f, slowDur = 2f,
                desc = "네 명에게 치명적인 역병을 퍼뜨립니다.")
            SpecialKind.THUNDER_HAMMER -> GemStats(n, 0xFFE0E4FF.toInt(), 420f, range = 4f, cooldown = 1.2f,
                chainCount = 3, chainRange = 2.5f, chainPct = 0.75f, stunChance = 0.35f, stunDur = 1.2f,
                critChance = 0.25f, critMult = 2f,
                desc = "연쇄 번개로 여러 적을 기절시킵니다.")
            SpecialKind.STAR_HEART -> GemStats(n, 0xFFFFF2B3.toInt(), 1500f, range = 5f, cooldown = 1f,
                splashRadius = 2.2f, splashPct = 0.6f, critChance = 0.3f, critMult = 3f,
                desc = "별의 힘으로 모든 것을 태웁니다.")
            SpecialKind.CHAOS_RIFT -> GemStats(n, 0xFFE040FB.toInt(), 900f, range = 5.5f, cooldown = 0.7f,
                chainCount = 6, chainRange = 3f, chainPct = 0.85f, poisonDps = 400f, poisonDur = 4f, airMult = 10f,
                desc = "혼돈의 번개가 6번 튕기며 독을 퍼뜨립니다. 공중 유닛에게 10배 피해를 줍니다.")
            SpecialKind.VOLCANO_HEART -> GemStats(n, 0xFFFF5722.toInt(), 1700f, range = 4.5f, cooldown = 1.1f,
                splashRadius = 2.6f, splashPct = 0.8f,
                desc = "화산처럼 폭발해 아주 넓은 범위를 불태웁니다.")
            SpecialKind.ABYSS_TIDE -> GemStats(n, 0xFF2979FF.toInt(), 1100f, range = 5f, cooldown = 0.8f,
                splashRadius = 2f, splashPct = 0.5f, slowPct = 0.6f, slowDur = 3f,
                desc = "거대한 해일로 넓은 범위의 적을 크게 둔화시킵니다.")
            SpecialKind.WORLD_TREE -> GemStats(n, 0xFF00E676.toInt(), 700f, range = 4.8f, cooldown = 0.8f,
                poisonDps = 450f, poisonDur = 4f, targets = 5,
                desc = "다섯 명에게 동시에 맹독을 퍼뜨립니다.")
            SpecialKind.SKY_ARROW -> GemStats(n, 0xFFFFD740.toInt(), 1000f, range = 5.2f, cooldown = 0.6f,
                targets = 6, airMult = 2f,
                desc = "여섯 명을 빠르게 동시에 꿰뚫습니다. 공중 유닛에 강합니다.")
            SpecialKind.ABSOLUTE_ZERO -> GemStats(n, 0xFFD6F6FF.toInt(), 2600f, range = 5f, cooldown = 1f,
                splashRadius = 2.2f, splashPct = 0.6f, slowPct = 0.7f, slowDur = 3f, stunChance = 0.3f, stunDur = 1.5f,
                desc = "모든 것을 얼려 멈춥니다. 넓은 범위를 크게 둔화시키고 기절시킵니다.")
            SpecialKind.THUNDER_GOD -> GemStats(n, 0xFFFFF59D.toInt(), 3000f, range = 5.5f, cooldown = 0.9f,
                chainCount = 8, chainRange = 3f, chainPct = 0.9f, stunChance = 0.35f, stunDur = 1.2f, critChance = 0.3f, critMult = 2.5f,
                desc = "하늘의 번개가 8번 튕기며 적을 기절시킵니다.")
            SpecialKind.GOLDEN_REAPER -> GemStats(n, 0xFFFFD54F.toInt(), 2800f, range = 4.5f, cooldown = 0.8f,
                targets = 2, critChance = 0.3f, critMult = 3f, executePct = 0.25f, bountyBonus = 5,
                desc = "빈사의 적을 거두고, 처치한 적은 골드를 5 더 줍니다.")
            // 공격력은 Game.refreshHiddenGems()가 매 프레임 "필드의 다른 모든 보석 공격력 합"으로 채움
            SpecialKind.URANIUM -> GemStats(n, 0xFF9CFF3A.toInt(), 0f, range = 4.5f, cooldown = 1f,
                desc = "히든 보석! 필드에 있는 다른 모든 보석의 공격력을 합친 만큼 피해를 줍니다.")
        }
        if (level <= 0 || k.hidden) return base
        val m = 1.7f.pow(level)
        return base.copy(
            name = "${base.name} +$level",
            damage = base.damage * m,
            poisonDps = base.poisonDps * m,
            range = base.range + 0.2f * level,
        )
    }

    fun recipeText(r: Recipe) = "${r.result.label} = " + r.partLabels.joinToString(" + ")

    fun describe(s: GemStats): List<String> = buildList {
        add("공격력 ${formatShort(s.damage)} · 사거리 %.1f · 간격 %.2f초".format(s.range, s.cooldown))
        if (s.splashRadius > 0) add("범위 피해 %.0f%% (반경 %.1f)".format(s.splashPct * 100, s.splashRadius))
        if (s.slowPct > 0) add("둔화 %.0f%% (%.1f초)".format(s.slowPct * 100, s.slowDur))
        if (s.poisonDps > 0) add("독 초당 ${formatShort(s.poisonDps)} (%.0f초)".format(s.poisonDur))
        if (s.targets > 1) add("동시 공격 ${s.targets}명")
        if (s.critChance > 0) add("치명타 %.0f%% (x%.1f)".format(s.critChance * 100, s.critMult))
        if (s.airMult > 1f) add("공중 유닛 피해 x%.1f".format(s.airMult))
        if (s.chainCount > 0) add("연쇄 ${s.chainCount}회 (튕길 때마다 %.0f%%)".format(s.chainPct * 100))
        if (s.stunChance > 0) add("기절 %.0f%% 확률 (%.1f초, 보스는 절반)".format(s.stunChance * 100, s.stunDur))
        if (s.bountyBonus > 0) add("처치 시 추가 골드 +${s.bountyBonus}")
        if (s.executePct > 0) add("체력 %.0f%% 이하 즉시 처치 (보스 제외)".format(s.executePct * 100))
        if (s.desc.isNotEmpty()) add(s.desc)
    }
}
