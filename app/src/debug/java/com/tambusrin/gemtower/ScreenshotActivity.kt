package com.tambusrin.gemtower

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.tambusrin.gemtower.game.Cell
import com.tambusrin.gemtower.game.CellType
import com.tambusrin.gemtower.game.Game
import com.tambusrin.gemtower.game.GameRecord
import com.tambusrin.gemtower.game.GemQuality
import com.tambusrin.gemtower.game.GemQuality.CHIPPED
import com.tambusrin.gemtower.game.GemQuality.FLAWED
import com.tambusrin.gemtower.game.GemQuality.FLAWLESS
import com.tambusrin.gemtower.game.GemQuality.NORMAL
import com.tambusrin.gemtower.game.GemType
import com.tambusrin.gemtower.game.GemType.AMETHYST
import com.tambusrin.gemtower.game.GemType.DIAMOND
import com.tambusrin.gemtower.game.GemType.EMERALD
import com.tambusrin.gemtower.game.GemType.RUBY
import com.tambusrin.gemtower.game.GemType.SAPPHIRE
import com.tambusrin.gemtower.game.GemType.TOPAZ
import com.tambusrin.gemtower.game.SpecialKind
import kotlin.random.Random

/**
 * 스토어 스크린샷 촬영용 화면 (debug 빌드에만 포함됨).
 * adb shell am start -n com.tambusrin.gemtower/.ScreenshotActivity --es scene maze|choose|recipes|boss
 */
class ScreenshotActivity : Activity() {
    private lateinit var view: GameView

    private data class Spec(val type: GemType, val q: GemQuality, val special: SpecialKind? = null, val level: Int = 0)

    // 실제 게임처럼 라운드마다 보석 1개 + 바위 4개를 놓는 순서
    private val lateGems = listOf(
        Spec(RUBY, NORMAL), Spec(SAPPHIRE, NORMAL, SpecialKind.FROST_SPIRE, 1), Spec(TOPAZ, FLAWED),
        Spec(AMETHYST, NORMAL), Spec(TOPAZ, NORMAL, SpecialKind.STORM_SPIRE, 1), Spec(EMERALD, NORMAL),
        Spec(DIAMOND, FLAWLESS), Spec(RUBY, NORMAL, SpecialKind.SUNBURST_CORE, 1), Spec(AMETHYST, FLAWLESS),
        Spec(SAPPHIRE, FLAWLESS), Spec(EMERALD, FLAWLESS), Spec(AMETHYST, FLAWLESS, SpecialKind.NEBULA_CANNON),
        Spec(TOPAZ, FLAWLESS), Spec(DIAMOND, NORMAL),
    )
    private val earlyGems = listOf(
        Spec(RUBY, FLAWED), Spec(AMETHYST, FLAWED), Spec(EMERALD, NORMAL, SpecialKind.VENOM_FANG),
        Spec(TOPAZ, FLAWED), Spec(DIAMOND, FLAWED), Spec(RUBY, NORMAL), Spec(SAPPHIRE, FLAWED), Spec(AMETHYST, CHIPPED),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        view = GameView(this)
        view.debugStartZoomFit()
        view.debugSkipTitle(keepRecords = intent.getStringExtra("scene") == "finale")
        setContentView(view)
        setup(intent.getStringExtra("scene") ?: "maze")
    }

    private fun setup(scene: String) {
        val g = view.game
        val rng = Random(2026)
        when (scene) {
            "finale" -> {
                // 기록 저장 확인용: 탑 없이 최종 보스를 흘려보내 게임 오버
                g.debugSetup(wave = Game.MAX_WAVE, gold = 77, chanceLevel = 0, lives = 20)
                view.debugSetSpeed(3)
                g.debugStartWave()
            }
            "records" -> view.debugShowRecords(
                List(10) { i ->
                    GameRecord.of(8.4e9 / (1 shl i), 96.0 + i * 7, 1420.0 - i * 60, 18 - i, 240 - i * 13, 62 - i * 4, false)
                },
                highlight = 2,
            )
            "maze" -> {
                g.debugSetup(wave = 17, gold = 85, chanceLevel = 6, lives = 18)
                buildMaze(g, 70, lateGems, rng)
                g.selected = findSpecial(g, SpecialKind.STORM_SPIRE)
                view.debugSetSpeed(2)
                g.debugStartWave()
            }
            "boss" -> {
                g.debugSetup(wave = 20, gold = 140, chanceLevel = 7, lives = 15)
                buildMaze(g, 70, lateGems, rng)
                // 기절/연쇄 효과가 보이도록 특수 보석 추가
                bestCells(g, 2, rng).forEachIndexed { i, c ->
                    if (i == 0) g.debugKeptGem(c, DIAMOND, FLAWLESS, SpecialKind.THUNDER_HAMMER, 1)
                    else g.debugKeptGem(c, SAPPHIRE, NORMAL, SpecialKind.CRYSTAL_PRISON, 2)
                }
                g.selected = findSpecial(g, SpecialKind.THUNDER_HAMMER)
                view.debugSetSpeed(3)
                g.debugStartWave()
            }
            "legend" -> {
                // 전설 조합(절대영도)이 가능한 장면: 서리 첨탑 + 수정 감옥이 필드에, 무결점 사파이어가 이번 라운드에
                g.debugSetup(wave = 22, gold = 150, chanceLevel = 7, lives = 16)
                buildMaze(g, 60, lateGems, rng)
                bestCells(g, 2, rng).forEachIndexed { i, c ->
                    g.debugKeptGem(c, SAPPHIRE, NORMAL, if (i == 0) SpecialKind.FROST_SPIRE else SpecialKind.CRYSTAL_PRISON, 2)
                }
                var s: Cell? = null
                listOf(SAPPHIRE, RUBY, TOPAZ, EMERALD, DIAMOND).forEachIndexed { i, type ->
                    val c = bestCells(g, 1, rng).first()
                    g.placeGem(c, type, if (i == 0) FLAWLESS else FLAWED, hidden = false)
                    if (i == 0) s = c
                }
                g.selected = s
            }
            "hidden" -> {
                // 히든 보석(우라늄)이 이번 라운드에 나온 장면
                g.debugSetup(wave = 12, gold = 80, chanceLevel = 5, lives = 19)
                buildMaze(g, 50, earlyGems, rng)
                var u: Cell? = null
                listOf(RUBY, TOPAZ, EMERALD, DIAMOND, SAPPHIRE).forEachIndexed { i, type ->
                    val c = bestCells(g, 1, rng).first()
                    g.placeGem(c, type, NORMAL, hidden = i == 2)
                    if (i == 2) u = c
                }
                g.selected = u
            }
            "choose", "recipes" -> {
                g.debugSetup(wave = 9, gold = 64, chanceLevel = 4, lives = 19)
                buildMaze(g, 40, earlyGems, rng)
                // 이번 라운드 보석: 같은 토파즈 2개(합성 가능), 사파이어(수정 감옥 재료 완성)
                val round = listOf(
                    Spec(RUBY, CHIPPED), Spec(TOPAZ, NORMAL), Spec(TOPAZ, NORMAL), Spec(EMERALD, FLAWED), Spec(SAPPHIRE, NORMAL),
                )
                var sapphire: Cell? = null
                for (s in round) {
                    val c = bestCells(g, 1, rng).first()
                    g.placeGem(c, s.type, s.q)
                    if (s.type == SAPPHIRE) sapphire = c
                }
                g.selected = sapphire
                if (scene == "recipes") view.debugShowRecipes()
            }
        }
    }

    /** 경로를 가장 길게 만드는 칸 n개 (동점이면 무작위) */
    private fun bestCells(g: Game, n: Int, rng: Random): List<Cell> {
        val out = ArrayList<Cell>()
        repeat(n) {
            var best = -1
            val ties = ArrayList<Cell>()
            for (c in g.groundPath) {
                if (c in out || g.cellType(c) != CellType.EMPTY) continue
                val len = g.pathLengthIfBlocked(c)
                if (len > best) { best = len; ties.clear(); ties.add(c) } else if (len == best) ties.add(c)
            }
            if (ties.isNotEmpty()) out.add(ties[rng.nextInt(ties.size)])
        }
        return out
    }

    /** 플레이어처럼 경로를 늘리는 칸을 골라 막음. 5번째마다 보석, 나머지는 바위. */
    private fun buildMaze(g: Game, blocks: Int, gems: List<Spec>, rng: Random) {
        var gi = 0
        for (i in 0 until blocks) {
            val c = bestCells(g, 1, rng).firstOrNull() ?: return
            if (i % 5 == 4 && gi < gems.size) {
                val s = gems[gi++]
                g.debugKeptGem(c, s.type, s.q, s.special, s.level)
            } else {
                g.debugRock(c)
            }
        }
    }

    private fun findSpecial(g: Game, k: SpecialKind): Cell? {
        for (x in 0 until Game.W) for (y in 0 until Game.H) {
            val c = Cell(x, y)
            if (g.towerAt(c)?.special == k) return c
        }
        return null
    }

    override fun onResume() {
        super.onResume()
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        view.resume()
    }

    override fun onPause() {
        view.pause()
        super.onPause()
    }
}
