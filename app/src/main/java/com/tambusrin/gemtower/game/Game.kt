package com.tambusrin.gemtower.game

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

data class Cell(val x: Int, val y: Int) {
    operator fun plus(o: Cell) = Cell(x + o.x, y + o.y)
}

data class Pos(val x: Float, val y: Float)

enum class CellType { EMPTY, WAYPOINT, ROCK, GEM }
enum class GameState { BUILD_PLACE, BUILD_CHOOSE, WAVE, GAME_OVER, VICTORY }

data class WaveInfo(
    val count: Int,
    val hp: Float,
    val speed: Float,
    val interval: Float,
    val bounty: Int,
    val leak: Int,
    val flying: Boolean,
    val boss: Boolean,
    val label: String,
)

class Tower(cell: Cell, type: GemType, quality: GemQuality) {
    var cell = cell; internal set
    var type = type; private set
    var quality = quality; private set
    var special: SpecialKind? = null; private set
    var specialLevel = 0; internal set
    var stats: GemStats = GemDB.stats(type, quality); private set
    /** 라운드 종료 후 남겨진 보석만 공격함 */
    var kept = false; internal set
    var kills = 0; internal set
    var totalDamage = 0f; internal set
    /** 위치 바꾸기를 이미 썼는지 (특수 보석당 1번) */
    var moved = false; internal set
    internal var cooldown = 0f

    internal fun setGem(t: GemType, q: GemQuality, sp: SpecialKind?) {
        type = t
        quality = q
        special = sp
        specialLevel = 0
        refresh()
    }

    internal fun refresh() {
        stats = special?.let { GemDB.special(it, specialLevel) } ?: GemDB.stats(type, quality)
    }

    /** 히든 보석은 다른 보석 상태에 따라 공격력이 바뀜 */
    internal fun setDamage(d: Float) {
        if (stats.damage != d) stats = stats.copy(damage = d)
    }
}

class Enemy(
    val maxHp: Float,
    val speed: Float,
    val flying: Boolean,
    val boss: Boolean,
    val bounty: Int,
    val leakDamage: Int,
    private val path: List<Pos>,
    val color: Int,
) {
    var x = path.firstOrNull()?.x ?: 0f; private set
    var y = path.firstOrNull()?.y ?: 0f; private set
    var hp = maxHp; internal set
    /** 이동한 거리 (타워 타겟 우선순위) */
    var progress = 0f; private set
    var dead = false; internal set
    private var idx = 1

    internal var slowPct = 0f
    internal var slowTimer = 0f
    internal var poisonDps = 0f
    internal var poisonTimer = 0f
    internal var poisonSrc: Tower? = null

    internal var stunTimer = 0f

    val slowed get() = slowTimer > 0f
    val poisoned get() = poisonTimer > 0f
    val stunned get() = stunTimer > 0f

    /** @return 도착 지점에 도달하면 true */
    internal fun move(dt: Float): Boolean {
        if (stunTimer > 0f) {
            stunTimer -= dt
            return idx >= path.size
        }
        var sp = speed
        if (slowTimer > 0f) {
            slowTimer -= dt
            sp *= 1f - slowPct
        } else slowPct = 0f

        var mv = sp * dt
        while (mv > 0f && idx < path.size) {
            val t = path[idx]
            val dx = t.x - x
            val dy = t.y - y
            val d = sqrt(dx * dx + dy * dy)
            if (d <= mv) {
                x = t.x; y = t.y
                mv -= d; progress += d; idx++
            } else {
                x += dx / d * mv; y += dy / d * mv
                progress += mv; mv = 0f
            }
        }
        return idx >= path.size
    }

    internal fun applySlow(pct: Float, dur: Float) {
        if (slowTimer <= 0f || pct >= slowPct) slowPct = pct
        slowTimer = maxOf(slowTimer, dur)
    }

    internal fun applyPoison(dps: Float, dur: Float, src: Tower) {
        if (poisonTimer <= 0f || dps >= poisonDps) {
            poisonDps = dps
            poisonSrc = src
        }
        poisonTimer = maxOf(poisonTimer, dur)
    }
}

/** 연쇄 번개 등 짧게 보이는 선 효과 */
class Beam(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int) {
    var ttl = DURATION; internal set
    companion object { const val DURATION = 0.18f }
}

class Projectile(val src: Tower, val target: Enemy, var x: Float, var y: Float, val damage: Float, val crit: Boolean) {
    var aimX = target.x; internal set
    var aimY = target.y; internal set
    var done = false; internal set
}

/**
 * 보석 타워 디펜스 게임 로직 (Android 의존성 없음).
 * 흐름: 보석 5개 배치 → 1개 선택(나머지는 바위) → 웨이브 → 반복
 * 좌표 단위는 칸(cell). 칸 (x, y)의 중심이 (x, y).
 */
class Game(private val rng: Random = Random.Default) {
    companion object {
        const val W = 19
        const val H = 19
        const val GEMS_PER_ROUND = 5
        const val MAX_WAVE = 1000          // 최종 웨이브 (화면에는 표시하지 않음)
        const val ENDLESS_FROM = 30        // 이 웨이브 이후로 난이도가 계속 상승
        const val ENDLESS_HP_GROWTH = 1.08f
        const val START_LIVES = 20
        const val ROCK_REMOVE_COST = 10
        const val MOVE_COST = 15
        const val PROJECTILE_SPEED = 14f
        private const val MAX_STEP = 0.02f

        /** 적이 순서대로 거쳐가는 지점 (출발 → 체크포인트 → 도착) */
        val WAYPOINTS = listOf(
            Cell(0, 18), Cell(2, 9), Cell(16, 9), Cell(16, 16), Cell(9, 16), Cell(9, 2), Cell(18, 0),
        )
        private val DIRS = listOf(Cell(0, -1), Cell(1, 0), Cell(0, 1), Cell(-1, 0))
    }

    private val cells = Array(W) { Array(H) { CellType.EMPTY } }
    private val towers = Array(W) { arrayOfNulls<Tower>(H) }

    val enemies = ArrayList<Enemy>()
    val projectiles = ArrayList<Projectile>()
    val beams = ArrayList<Beam>()
    val roundGems = ArrayList<Tower>()

    var groundPath: List<Cell> = emptyList(); private set
    var pathVersion = 0; private set
    private var groundPos: List<Pos> = emptyList()
    private val airPos = WAYPOINTS.map { Pos(it.x.toFloat(), it.y.toFloat()) }

    var state = GameState.BUILD_PLACE; private set
    var wave = 1; private set
    var lives = START_LIVES; private set
    var gold = 0; private set
    var chanceLevel = 0; private set
    var selected: Cell? = null
    /** 위치 바꾸기 모드에서 옮길 보석의 칸 */
    var movingFrom: Cell? = null; private set
    var message = ""; private set
    var messageTimer = 0f; private set
    var currentWave = waveInfo(1); private set
    /** 애니메이션용 누적 게임 시간 */
    var time = 0f; private set

    private var toSpawn = 0
    private var spawnTimer = 0f

    val remainingEnemies get() = enemies.size + toSpawn
    val isBuildState get() = state == GameState.BUILD_PLACE || state == GameState.BUILD_CHOOSE

    init {
        newGame()
    }

    fun cellType(c: Cell) = cells[c.x][c.y]
    fun towerAt(c: Cell) = towers[c.x][c.y]
    fun inBounds(c: Cell) = c.x in 0 until W && c.y in 0 until H

    fun newGame() {
        for (x in 0 until W) for (y in 0 until H) {
            cells[x][y] = CellType.EMPTY
            towers[x][y] = null
        }
        for (wp in WAYPOINTS) cells[wp.x][wp.y] = CellType.WAYPOINT
        enemies.clear()
        projectiles.clear()
        beams.clear()
        roundGems.clear()
        wave = 1
        lives = START_LIVES
        gold = 0
        chanceLevel = 0
        toSpawn = 0
        selected = null
        movingFrom = null
        state = GameState.BUILD_PLACE
        recalcPath()
        showMessage("빈 칸을 눌러 보석 5개를 배치하세요")
    }

    // ------------------------------------------------------------------ 길찾기

    private fun passable(c: Cell) = cells[c.x][c.y].let { it == CellType.EMPTY || it == CellType.WAYPOINT }

    private fun findPath(a: Cell, b: Cell): List<Cell>? {
        val prev = Array(W) { arrayOfNulls<Cell>(H) }
        val seen = Array(W) { BooleanArray(H) }
        val q = ArrayDeque<Cell>()
        q.add(a)
        seen[a.x][a.y] = true
        while (q.isNotEmpty()) {
            val c = q.poll()!!
            if (c == b) break
            for (d in DIRS) {
                val n = c + d
                if (!inBounds(n) || seen[n.x][n.y] || !passable(n)) continue
                seen[n.x][n.y] = true
                prev[n.x][n.y] = c
                q.add(n)
            }
        }
        if (!seen[b.x][b.y]) return null
        val path = ArrayList<Cell>()
        var c = b
        while (c != a) {
            path.add(c)
            c = prev[c.x][c.y]!!
        }
        path.add(a)
        path.reverse()
        return path
    }

    fun computeFullPath(): List<Cell>? {
        val full = ArrayList<Cell>()
        for (i in 0 until WAYPOINTS.size - 1) {
            val seg = findPath(WAYPOINTS[i], WAYPOINTS[i + 1]) ?: return null
            full.addAll(if (i > 0) seg.drop(1) else seg)
        }
        return full
    }

    fun canBuild(c: Cell): Boolean {
        if (!inBounds(c) || cells[c.x][c.y] != CellType.EMPTY) return false
        cells[c.x][c.y] = CellType.ROCK
        val ok = computeFullPath() != null
        cells[c.x][c.y] = CellType.EMPTY
        return ok
    }

    private fun recalcPath() {
        groundPath = computeFullPath() ?: emptyList()
        groundPos = groundPath.map { Pos(it.x.toFloat(), it.y.toFloat()) }
        pathVersion++
    }

    // ------------------------------------------------------------------ 입력

    /** 칸을 눌렀을 때. 배치/이동이 거부되면 false. */
    fun tapCell(c: Cell): Boolean {
        if (movingFrom != null) {
            if (!inBounds(c)) {
                cancelMove()
                return true
            }
            return moveTo(c)
        }
        if (!inBounds(c)) {
            selected = null
            return true
        }
        val ct = cells[c.x][c.y]
        if (state == GameState.BUILD_PLACE && ct == CellType.EMPTY) {
            if (!canBuild(c)) {
                showMessage("적의 길을 완전히 막을 수는 없습니다!")
                return false
            }
            placeGem(c)
            return true
        }
        selected = if (ct == CellType.GEM || ct == CellType.ROCK) c else null
        return true
    }

    // ------------------------------------------------------------------ 위치 바꾸기

    /** 특수 보석만, 보석마다 1번만, 건설 단계에서 골드가 있을 때 */
    fun canStartMove(c: Cell): Boolean {
        if (!isBuildState || !inBounds(c) || gold < MOVE_COST) return false
        val t = towers[c.x][c.y] ?: return false
        return t.special != null && !t.moved
    }

    fun startMove(c: Cell) {
        if (!canStartMove(c)) return
        movingFrom = c
        selected = c
        showMessage("옮길 칸을 누르세요 · 보석/바위를 누르면 자리를 바꿉니다")
    }

    fun cancelMove() {
        movingFrom = null
    }

    /**
     * movingFrom의 보석을 target으로 옮김. 빈 칸이면 이동, 보석·바위면 서로 자리를 바꿈.
     * 보석·바위와 교환할 때는 막힌 칸의 배치가 그대로라 길이 바뀌지 않음.
     * @return 이동에 실패하면 false (골드 차감 없음)
     */
    private fun moveTo(target: Cell): Boolean {
        val from = movingFrom ?: return false
        val t = towers[from.x][from.y]
        if (!isBuildState || t == null || t.special == null || t.moved) {
            movingFrom = null
            return false
        }
        if (target == from) {
            cancelMove()
            showMessage("위치 바꾸기를 취소했습니다")
            return true
        }
        if (gold < MOVE_COST) {
            movingFrom = null
            showMessage("골드가 부족합니다")
            return false
        }
        when (cells[target.x][target.y]) {
            CellType.WAYPOINT -> {
                showMessage("체크포인트에는 놓을 수 없습니다")
                return false
            }
            CellType.EMPTY -> {
                cells[from.x][from.y] = CellType.EMPTY
                cells[target.x][target.y] = CellType.GEM
                if (computeFullPath() == null) {
                    cells[from.x][from.y] = CellType.GEM
                    cells[target.x][target.y] = CellType.EMPTY
                    showMessage("적의 길을 완전히 막을 수는 없습니다!")
                    return false
                }
                towers[from.x][from.y] = null
                towers[target.x][target.y] = t
            }
            CellType.GEM -> {
                val other = towers[target.x][target.y]!!
                // 상대가 특수 보석이면 그 보석도 이동한 것으로 침 (특수 보석은 1번만 이동)
                if (other.special != null && other.moved) {
                    showMessage("${other.stats.name}은(는) 이미 이동해서 자리를 바꿀 수 없습니다")
                    return false
                }
                towers[target.x][target.y] = t
                towers[from.x][from.y] = other
                other.cell = from
                if (other.special != null) other.moved = true
            }
            CellType.ROCK -> {
                cells[target.x][target.y] = CellType.GEM
                cells[from.x][from.y] = CellType.ROCK
                towers[target.x][target.y] = t
                towers[from.x][from.y] = null
            }
        }
        t.cell = target
        t.moved = true
        gold -= MOVE_COST
        movingFrom = null
        selected = target
        recalcPath()
        showMessage("위치를 바꿨습니다 (-${MOVE_COST}G)")
        return true
    }

    // ------------------------------------------------------------------ 보석 / 바위

    internal fun setRock(c: Cell) {
        towers[c.x][c.y] = null
        cells[c.x][c.y] = CellType.ROCK
    }

    internal fun placeGem(
        c: Cell,
        type: GemType = GemType.entries[rng.nextInt(GemType.entries.size)],
        quality: GemQuality = GemDB.rollQuality(chanceLevel, rng),
        hidden: Boolean = rng.nextDouble() < GemDB.HIDDEN_CHANCE,
    ): Tower {
        val t = Tower(c, type, quality)
        if (hidden) t.setGem(type, quality, SpecialKind.URANIUM)
        cells[c.x][c.y] = CellType.GEM
        towers[c.x][c.y] = t
        roundGems.add(t)
        recalcPath()
        if (roundGems.size >= GEMS_PER_ROUND) {
            state = GameState.BUILD_CHOOSE
            selected = null
            showMessage("남길 보석 하나를 눌러 선택하세요")
        }
        if (hidden) showMessage("✦ 히든 보석 「우라늄」 발견! ✦")
        refreshHiddenGems()
        return t
    }

    /** 히든 보석(우라늄) 공격력 = 필드에 있는 다른 모든 보석(이번 라운드 보석 포함) 공격력의 합 */
    internal fun refreshHiddenGems() {
        var sum = 0f
        var any = false
        for (x in 0 until W) for (y in 0 until H) {
            val t = towers[x][y] ?: continue
            if (t.special?.hidden == true) any = true else sum += t.stats.damage
        }
        if (!any) return
        for (x in 0 until W) for (y in 0 until H) {
            val t = towers[x][y] ?: continue
            if (t.special?.hidden == true) t.setDamage(sum)
        }
    }

    fun removeRock(c: Cell) {
        if (!isBuildState || cells[c.x][c.y] != CellType.ROCK || gold < ROCK_REMOVE_COST) return
        gold -= ROCK_REMOVE_COST
        cells[c.x][c.y] = CellType.EMPTY
        if (selected == c) selected = null
        recalcPath()
    }

    /** 필드(남긴 보석 + 이번 라운드 보석)에 이 보석이 있는지. 조합법 창 표시용. */
    fun hasGemOnField(key: GemKey): Boolean {
        for (x in 0 until W) for (y in 0 until H) {
            val t = towers[x][y] ?: continue
            if (t.special == null && t.type == key.type && t.quality == key.quality) return true
        }
        return false
    }

    fun countSame(t: Tower) = roundGems.count { it.special == null && it.type == t.type && it.quality == t.quality }

    fun canCombine(t: Tower, levels: Int): Boolean =
        state == GameState.BUILD_CHOOSE && t in roundGems && t.special == null &&
            t.quality.ordinal + levels <= GemQuality.PERFECT.ordinal &&
            countSame(t) >= (if (levels >= 2) 4 else 2)

    /** 조합법 재료를 찾음. t는 재료 중 하나여야 하며, 나머지는 필드의 다른 보석에서 찾음. */
    fun findRecipeParts(t: Tower, r: Recipe): List<Tower>? {
        if (t.special != null) return null
        val used = ArrayList<Tower>()
        var tUsed = false
        for (part in r.parts) {
            if (!tUsed && t.type == part.type && t.quality == part.quality) {
                used.add(t)
                tUsed = true
                continue
            }
            var found: Tower? = null
            loop@ for (x in 0 until W) for (y in 0 until H) {
                val o = towers[x][y] ?: continue
                if (o === t || o.special != null || used.any { it === o }) continue
                if (o.type == part.type && o.quality == part.quality) {
                    found = o
                    break@loop
                }
            }
            used.add(found ?: return null)
        }
        if (!tUsed) return null
        // 전설 조합법: 필드의 특수 보석 재료 (같은 종류가 여럿이면 강화 단계가 가장 낮은 것을 씀)
        for (sp in r.specialParts) {
            var found: Tower? = null
            for (x in 0 until W) for (y in 0 until H) {
                val o = towers[x][y] ?: continue
                if (o.special != sp || used.any { it === o }) continue
                if (found == null || o.specialLevel < found.specialLevel) found = o
            }
            used.add(found ?: return null)
        }
        return used
    }

    /** 필드에 이 특수 보석이 있는지. 조합법 창 표시용. */
    fun hasSpecialOnField(k: SpecialKind): Boolean {
        for (x in 0 until W) for (y in 0 until H) if (towers[x][y]?.special == k) return true
        return false
    }

    fun keep(t: Tower) = finishRound(t)

    fun combine(t: Tower, levels: Int) {
        if (!canCombine(t, levels)) return
        t.setGem(t.type, GemQuality.entries[t.quality.ordinal + levels], null)
        finishRound(t)
    }

    fun makeSpecial(t: Tower, r: Recipe) {
        if (state != GameState.BUILD_CHOOSE || t !in roundGems) return
        val parts = findRecipeParts(t, r) ?: return
        for (p in parts) if (p !== t && p !in roundGems) setRock(p.cell) // 이미 남긴 재료는 바위가 됨
        t.setGem(t.type, t.quality, r.result)
        showMessage("특수 보석 탄생: ${t.stats.name}")
        finishRound(t)
    }

    private fun finishRound(chosen: Tower) {
        if (state != GameState.BUILD_CHOOSE || chosen !in roundGems) return
        movingFrom = null
        for (g in roundGems) if (g !== chosen) setRock(g.cell)
        roundGems.clear()
        chosen.kept = true
        selected = chosen.cell
        recalcPath()
        startWave()
    }

    fun upgradeSpecial(t: Tower) {
        if (t.special == null || t.special?.hidden == true || !t.kept || !isBuildState || t.specialLevel >= GemDB.MAX_SPECIAL_LEVEL) return
        val cost = GemDB.specialUpgradeCost(t.specialLevel)
        if (gold < cost) return
        gold -= cost
        t.specialLevel++
        t.refresh()
    }

    fun upgradeChance() {
        if (chanceLevel >= GemDB.maxChanceLevel || state == GameState.GAME_OVER || state == GameState.VICTORY) return
        val cost = GemDB.chanceCost(chanceLevel)
        if (gold < cost) return
        gold -= cost
        chanceLevel++
        showMessage("보석 품질 레벨 $chanceLevel")
    }

    internal fun addGold(amount: Int) {
        gold += amount
    }

    // ------------------------------------------------------------------ 스크린샷/테스트용 (debug 소스셋에서만 호출)

    /** 원하는 웨이브·골드·품질 레벨의 빈 건설 단계로 설정 */
    internal fun debugSetup(wave: Int, gold: Int, chanceLevel: Int, lives: Int = START_LIVES) {
        newGame()
        this.wave = wave
        this.gold = gold
        this.chanceLevel = chanceLevel
        this.lives = lives
        message = ""
        messageTimer = 0f
    }

    /** 이미 남겨진(공격하는) 보석을 바로 배치 */
    internal fun debugKeptGem(c: Cell, type: GemType, quality: GemQuality, special: SpecialKind? = null, level: Int = 0): Tower {
        val t = Tower(c, type, quality)
        if (special != null) {
            t.setGem(type, quality, special)
            t.specialLevel = level
            t.refresh()
        }
        t.kept = true
        cells[c.x][c.y] = CellType.GEM
        towers[c.x][c.y] = t
        recalcPath()
        return t
    }

    internal fun debugStartWave() = startWave()

    internal fun debugRock(c: Cell) {
        setRock(c)
        recalcPath()
    }

    /** 이 칸을 막았을 때의 경로 길이 (길이 막히면 -1). 미로 자동 생성용. */
    internal fun pathLengthIfBlocked(c: Cell): Int {
        if (!inBounds(c) || cells[c.x][c.y] != CellType.EMPTY) return -1
        cells[c.x][c.y] = CellType.ROCK
        val len = computeFullPath()?.size ?: -1
        cells[c.x][c.y] = CellType.EMPTY
        return len
    }

    // ------------------------------------------------------------------ 웨이브

    /**
     * 웨이브 구성. 1~30웨이브는 기존 곡선 그대로이고,
     * 30웨이브 이후(무한 모드)는 체력이 웨이브마다 8%씩 계속 오르고 적 수·속도도 천천히 늘어난다.
     * 체력은 1000웨이브에서도 Float 범위(약 3.4e38) 안에 머물도록 성장률을 낮춤 (1000웨이브 보스 ≈ 1e37).
     */
    fun waveInfo(w: Int): WaveInfo {
        val extra = max(0, w - ENDLESS_FROM)                 // 30웨이브를 넘긴 만큼
        val base = min(w, ENDLESS_FROM)
        val hp = (15f * 1.21f.pow(base - 1) + 4f * base) * ENDLESS_HP_GROWTH.pow(extra)
        val count = 10 + min(extra / 10, 15)                 // 10웨이브마다 +1마리, 최대 25마리
        val speedMul = 1f + min(extra * 0.004f, 0.5f)        // 최대 1.5배
        val bounty = 1 + w / 4
        return when {
            w % 10 == 0 -> WaveInfo(1 + extra / 100, hp * 8f, 1.1f * speedMul, 1f, 20 + w, 5, flying = false, boss = true, label = "보스")
            w % 4 == 0 -> WaveInfo(count, hp * 0.8f, 1.6f * speedMul, 1f, bounty, 1, flying = true, boss = false, label = "비행 (미로 무시)")
            w % 5 == 3 -> WaveInfo(count, hp * 0.7f, 1.6f * 1.7f * speedMul, 0.6f, bounty, 1, flying = false, boss = false, label = "고속")
            else -> WaveInfo(count, hp, 1.6f * speedMul, 1f, bounty, 1, flying = false, boss = false, label = "일반")
        }
    }

    private fun startWave() {
        currentWave = waveInfo(wave)
        toSpawn = currentWave.count
        spawnTimer = 0.3f
        state = GameState.WAVE
        showMessage("웨이브 $wave - ${currentWave.label}")
    }

    private fun spawnEnemy() {
        val w = currentWave
        val hue = (wave * 0.137f) % 1f
        val color = if (w.boss) 0xFFA60D1F.toInt() else hsvToArgb(hue * 360f, 0.65f, 0.95f)
        enemies.add(Enemy(w.hp, w.speed, w.flying, w.boss, w.bounty, w.leak, if (w.flying) airPos else groundPos, color))
    }

    private fun endWave() {
        val bonus = 10 + wave * 2
        gold += bonus
        if (wave >= MAX_WAVE) {
            state = GameState.VICTORY
            showMessage("모든 웨이브를 막아냈습니다!")
            return
        }
        wave++
        state = GameState.BUILD_PLACE
        showMessage("웨이브 클리어! +${bonus}G · 보석 5개를 배치하세요")
    }

    private fun leak(e: Enemy) {
        e.dead = true
        if (state != GameState.WAVE) return
        lives -= e.leakDamage
        if (lives <= 0) {
            lives = 0
            state = GameState.GAME_OVER
            showMessage("생명이 모두 소진되었습니다")
        }
    }

    // ------------------------------------------------------------------ 전투

    /** 게임 시간 dt(초)만큼 진행 (배속이 이미 적용된 값) */
    fun update(dt: Float) {
        var remaining = dt
        while (remaining > 0f) {
            val step = min(remaining, MAX_STEP)
            step(step)
            remaining -= step
        }
    }

    /** 실제 시간 기준 UI 타이머 */
    fun tickUi(dt: Float) {
        if (messageTimer > 0f) messageTimer -= dt
        refreshHiddenGems()   // 건설 단계에서도 패널의 공격력 표시가 최신이 되게
    }

    private fun step(dt: Float) {
        time += dt
        refreshHiddenGems()

        if (state == GameState.WAVE && toSpawn > 0) {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                spawnEnemy()
                toSpawn--
                spawnTimer = currentWave.interval
            }
        }

        if (state != GameState.GAME_OVER) {
            for (i in enemies.indices) {
                val e = enemies[i]
                if (e.dead) continue
                if (e.poisonTimer > 0f) {
                    e.poisonTimer -= dt
                    damage(e, e.poisonDps * dt, e.poisonSrc)
                    if (e.poisonTimer <= 0f) e.poisonDps = 0f
                    if (e.dead) continue
                }
                if (e.move(dt)) leak(e)
            }
        }

        if (state == GameState.WAVE) {
            for (x in 0 until W) for (y in 0 until H) {
                val t = towers[x][y] ?: continue
                if (t.kept) updateTower(t, dt)
            }
        }

        for (i in projectiles.indices) {
            val p = projectiles[i]
            if (p.done) continue
            if (!p.target.dead) {
                p.aimX = p.target.x
                p.aimY = p.target.y
            }
            val dx = p.aimX - p.x
            val dy = p.aimY - p.y
            val d = sqrt(dx * dx + dy * dy)
            val mv = PROJECTILE_SPEED * dt
            if (d <= mv) {
                p.x = p.aimX; p.y = p.aimY
                p.done = true
                applyHit(p.src, p.target, p.aimX, p.aimY, p.damage)
            } else {
                p.x += dx / d * mv
                p.y += dy / d * mv
            }
        }

        for (b in beams) b.ttl -= dt

        enemies.removeAll { it.dead }
        projectiles.removeAll { it.done }
        beams.removeAll { it.ttl <= 0f }

        if (state == GameState.WAVE && toSpawn == 0 && enemies.isEmpty()) endWave()
    }

    private fun updateTower(t: Tower, dt: Float) {
        t.cooldown -= dt
        if (t.cooldown > 0f) return
        val s = t.stats
        val r2 = s.range * s.range
        val tx = t.cell.x.toFloat()
        val ty = t.cell.y.toFloat()
        val targets = enemies
            .filter { !it.dead && (it.x - tx).let { dx -> dx * dx } + (it.y - ty).let { dy -> dy * dy } <= r2 }
            .sortedByDescending { it.progress } // 가장 멀리 간 적 우선
            .take(s.targets)
        if (targets.isEmpty()) return
        for (e in targets) {
            var dmg = s.damage
            var crit = false
            if (s.critChance > 0f && rng.nextFloat() < s.critChance) {
                dmg *= s.critMult
                crit = true
            }
            projectiles.add(Projectile(t, e, tx, ty, dmg, crit))
        }
        t.cooldown = s.cooldown
    }

    private fun applyHit(src: Tower, target: Enemy, x: Float, y: Float, dmg: Float) {
        if (!target.dead) hit(src, target, dmg)
        val s = src.stats
        if (s.splashRadius > 0f) {
            val r2 = s.splashRadius * s.splashRadius
            for (e in enemies) {
                if (e.dead || e === target) continue
                val dx = e.x - x
                val dy = e.y - y
                if (dx * dx + dy * dy <= r2) hit(src, e, dmg * s.splashPct)
            }
        }
        if (s.chainCount > 0) chain(src, target, x, y, dmg)
    }

    /** 연쇄 번개: 마지막으로 맞은 위치에서 가장 가까운, 아직 안 맞은 적에게 튕김 */
    private fun chain(src: Tower, first: Enemy, x: Float, y: Float, dmg: Float) {
        val s = src.stats
        val hitList = ArrayList<Enemy>(s.chainCount + 1).apply { add(first) }
        val r2 = s.chainRange * s.chainRange
        var cx = x
        var cy = y
        var d = dmg
        repeat(s.chainCount) {
            var best: Enemy? = null
            var bestD = r2
            for (e in enemies) {
                if (e.dead || hitList.any { it === e }) continue
                val dx = e.x - cx
                val dy = e.y - cy
                val dd = dx * dx + dy * dy
                if (dd <= bestD) {
                    bestD = dd
                    best = e
                }
            }
            val n = best ?: return
            d *= s.chainPct
            beams.add(Beam(cx, cy, n.x, n.y, s.color))
            hit(src, n, d)
            hitList.add(n)
            cx = n.x
            cy = n.y
        }
    }

    private fun hit(src: Tower, e: Enemy, dmg: Float) {
        val s = src.stats
        if (s.slowPct > 0f) e.applySlow(s.slowPct, s.slowDur)
        if (s.poisonDps > 0f) e.applyPoison(s.poisonDps, s.poisonDur, src)
        if (s.stunChance > 0f && rng.nextFloat() < s.stunChance) {
            val dur = if (e.boss) s.stunDur * 0.5f else s.stunDur
            e.stunTimer = maxOf(e.stunTimer, dur)
        }
        damage(e, dmg * (if (e.flying) s.airMult else 1f), src)
        if (s.executePct > 0f && !e.dead && !e.boss && e.hp <= e.maxHp * s.executePct) damage(e, e.hp + 1f, src)
    }

    private fun damage(e: Enemy, dmg: Float, src: Tower?) {
        if (e.dead || dmg <= 0f) return
        val applied = min(e.hp, dmg)
        e.hp -= dmg
        if (src != null) src.totalDamage += applied
        if (e.hp <= 0f) {
            e.dead = true
            if (src != null) src.kills++
            gold += e.bounty + (src?.stats?.bountyBonus ?: 0)
        }
    }

    private fun showMessage(m: String) {
        message = m
        messageTimer = 3.5f
    }
}

/** android.graphics.Color 없이 쓰는 HSV → ARGB (단위 테스트에서도 동작하도록) */
internal fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val c = v * s
    val hp = (h % 360f) / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    fun ch(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}
