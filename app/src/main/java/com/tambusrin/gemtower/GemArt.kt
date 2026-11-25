package com.tambusrin.gemtower

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.tambusrin.gemtower.game.GemQuality
import com.tambusrin.gemtower.game.SpecialKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * 보석 그림. 기본 보석은 등급마다(깨진 조각 → 원석 → 스텝 컷 → 브릴리언트 윗면 → 완전한 브릴리언트),
 * 조합 보석은 종류마다 다른 모양. 좌표는 칸 크기 s를 1로 한 단위(중심 기준, 약 ±0.42 안).
 */
class GemArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private lateinit var c: Canvas
    private var x = 0f
    private var y = 0f
    private var s = 1f

    private fun u(a: Float) = x + a * s
    private fun v(b: Float) = y + b * s

    // ------------------------------------------------------------ 기본 보석

    fun drawBasic(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, q: GemQuality, time: Float) {
        c = canvas; x = cx; y = cy; s = size
        when (q) {
            GemQuality.CHIPPED -> chipped(color)
            GemQuality.FLAWED -> flawed(color)
            GemQuality.NORMAL -> stepCut(color)
            GemQuality.FLAWLESS -> brilliantTop(color, time)
            GemQuality.PERFECT -> brilliantFull(color, time)
        }
    }

    /** 깨진: 유리 조각처럼 흩어진 날카로운 파편 */
    private fun chipped(k: Int) {
        val shards = arrayOf(
            floatArrayOf(-0.05f, -0.31f, 0.10f, -0.13f, -0.02f, -0.05f, -0.17f, -0.11f),
            floatArrayOf(0.15f, -0.08f, 0.28f, 0.03f, 0.08f, 0.21f, 0.04f, 0.02f),
            floatArrayOf(-0.21f, -0.04f, -0.05f, 0.04f, -0.02f, 0.25f, -0.29f, 0.07f),
            floatArrayOf(0.17f, -0.24f, 0.25f, -0.19f, 0.19f, -0.14f),
            floatArrayOf(0.12f, 0.25f, 0.19f, 0.28f, 0.11f, 0.31f),
            floatArrayOf(-0.27f, -0.20f, -0.21f, -0.17f, -0.26f, -0.13f),
        )
        val edge = blend(k, Color.WHITE, 0.65f)
        for ((i, p) in shards.withIndex()) {
            poly(withAlpha(if (i % 2 == 0) k else blend(k, Color.BLACK, 0.2f), 185), *p)
            outline(withAlpha(edge, 230), 0.018f, *p)
        }
        // 조각 안의 반사광 선
        seg(withAlpha(Color.WHITE, 200), 0.02f, -0.06f, -0.24f, 0.03f, -0.13f)
        seg(withAlpha(Color.WHITE, 170), 0.018f, 0.14f, -0.01f, 0.20f, 0.04f)
        seg(withAlpha(Color.WHITE, 170), 0.018f, -0.20f, 0.02f, -0.10f, 0.08f)
    }

    /** 흠있는: 갈라진 금이 간 거친 원석 */
    private fun flawed(k: Int) {
        val o = floatArrayOf(-0.08f, -0.30f, 0.16f, -0.26f, 0.27f, -0.04f, 0.20f, 0.20f, -0.06f, 0.25f, -0.26f, 0.10f, -0.24f, -0.14f)
        poly(blend(k, Color.BLACK, 0.15f), *o)
        poly(blend(k, Color.WHITE, 0.3f), -0.08f, -0.30f, 0.16f, -0.26f, 0.04f, -0.06f, -0.24f, -0.14f)
        poly(blend(k, Color.BLACK, 0.35f), 0.16f, -0.26f, 0.27f, -0.04f, 0.20f, 0.20f, 0.04f, -0.06f)
        poly(blend(k, Color.BLACK, 0.5f), 0.04f, -0.06f, 0.20f, 0.20f, -0.06f, 0.25f)
        poly(k, -0.24f, -0.14f, 0.04f, -0.06f, -0.06f, 0.25f, -0.26f, 0.10f)
        // 금
        val crack = blend(k, Color.BLACK, 0.75f)
        polyline(crack, 0.03f, -0.14f, -0.22f, -0.04f, -0.09f, -0.11f, 0.03f, 0.01f, 0.15f)
        polyline(crack, 0.02f, -0.04f, -0.09f, 0.06f, -0.12f)
    }

    /** 일반: 모서리를 깎은 스텝 컷(에메랄드 컷) */
    private fun stepCut(k: Int) {
        val w = 0.23f; val h = 0.29f; val e = 0.09f
        val iw = 0.11f; val ih = 0.16f
        // 바깥 → 안쪽 사다리꼴 면 4개 + 모서리 면
        poly(blend(k, Color.WHITE, 0.45f), -w + e, -h, w - e, -h, iw, -ih, -iw, -ih)
        poly(blend(k, Color.BLACK, 0.45f), -w + e, h, w - e, h, iw, ih, -iw, ih)
        poly(blend(k, Color.WHITE, 0.15f), -w, -h + e, -iw, -ih, -iw, ih, -w, h - e)
        poly(blend(k, Color.BLACK, 0.25f), w, -h + e, iw, -ih, iw, ih, w, h - e)
        poly(blend(k, Color.WHITE, 0.3f), -w + e, -h, -iw, -ih, -w, -h + e)
        poly(blend(k, Color.BLACK, 0.1f), w - e, -h, w, -h + e, iw, -ih)
        poly(blend(k, Color.BLACK, 0.2f), -w, h - e, -iw, ih, -w + e, h)
        poly(blend(k, Color.BLACK, 0.55f), w, h - e, w - e, h, iw, ih)
        poly(k, -iw, -ih, iw, -ih, iw, ih, -iw, ih)
        seg(withAlpha(Color.WHITE, 150), 0.02f, -iw + 0.03f, -ih + 0.05f, -iw + 0.03f, ih - 0.05f)
        outline(blend(k, Color.BLACK, 0.6f), 0.012f, -w + e, -h, w - e, -h, w, -h + e, w, h - e, w - e, h, -w + e, h, -w, h - e, -w, -h + e)
    }

    /** 무결점: 위에서 본 라운드 브릴리언트 컷 */
    private fun brilliantTop(k: Int, time: Float) {
        val n = 8
        val ro = 0.30f; val ri = 0.15f
        val gy = -0.03f
        for (i in 0 until n) {
            val a0 = (i * 2 * Math.PI / n - Math.PI / 2).toFloat()
            val a1 = ((i + 1) * 2 * Math.PI / n - Math.PI / 2).toFloat()
            val am = (a0 + a1) / 2f
            val ox0 = cos(a0) * ro; val oy0 = gy + sin(a0) * ro
            val ox1 = cos(a1) * ro; val oy1 = gy + sin(a1) * ro
            val ix = cos(am) * ri; val iy = gy + sin(am) * ri
            val shade = 0.55f + 0.45f * cos(am + 2.2f)   // 왼쪽 위가 밝게
            poly(if (shade > 0.55f) blend(k, Color.WHITE, (shade - 0.55f) * 1.1f) else blend(k, Color.BLACK, (0.55f - shade) * 1.1f),
                ox0, oy0, ox1, oy1, ix, iy)
            // 별 면
            val px = cos(a1) * ri * 0.92f; val py = gy + sin(a1) * ri * 0.92f
            poly(blend(k, if (i % 2 == 0) Color.WHITE else Color.BLACK, 0.22f), ix, iy, ox1, oy1, px, py)
        }
        val table = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = (i * 2 * Math.PI / n - Math.PI / 2 + Math.PI / n).toFloat()
            table[i * 2] = cos(a) * ri; table[i * 2 + 1] = gy + sin(a) * ri
        }
        poly(blend(k, Color.WHITE, 0.2f), *table)
        outline(withAlpha(Color.WHITE, 120), 0.012f, *table)
        sparkle(-0.12f, gy - 0.14f, 0.09f, time * 3f)
    }

    /** 완벽한: 크라운과 파빌리온이 온전한 브릴리언트 컷 + 빛 */
    private fun brilliantFull(k: Int, time: Float) {
        val pulse = sin(time * 3f) * 0.5f + 0.5f
        fill.color = withAlpha(blend(k, Color.WHITE, 0.4f), (40 + 50 * pulse).toInt())
        c.drawCircle(u(0f), v(-0.04f), s * (0.38f + 0.03f * pulse), fill)
        val g = 0.33f
        val top = -0.30f
        val mid = top + g * 0.43f
        val bot = top + g * 1.5f
        fun f(col: Int, vararg p: Float) {
            val q = FloatArray(p.size) { if (it % 2 == 0) p[it] * g else p[it] }
            poly(col, *q)
        }
        f(blend(k, Color.WHITE, 0.3f), -0.57f, top, -0.29f, mid, -1f, mid)
        f(blend(k, Color.WHITE, 0.7f), -0.57f, top, 0.57f, top, 0.29f, mid, -0.29f, mid)
        f(k, 0.57f, top, 1f, mid, 0.29f, mid)
        f(blend(k, Color.BLACK, 0.08f), -1f, mid, -0.29f, mid, 0f, bot)
        f(blend(k, Color.BLACK, 0.27f), -0.29f, mid, 0.29f, mid, 0f, bot)
        f(blend(k, Color.BLACK, 0.47f), 0.29f, mid, 1f, mid, 0f, bot)
        // 파빌리온 결
        seg(withAlpha(Color.WHITE, 90), 0.012f, -0.65f * g, mid, 0f, bot)
        seg(withAlpha(Color.BLACK, 70), 0.012f, 0.65f * g, mid, 0f, bot)
        seg(withAlpha(Color.WHITE, 200), 0.015f, -g, mid, g, mid)
        sparkle(0.20f, -0.30f, 0.11f, time * 2.5f)
        sparkle(-0.24f, 0.06f, 0.07f, time * 2.5f + 2f)
    }

    // ------------------------------------------------------------ 조합 보석

    fun drawSpecial(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, kind: SpecialKind, time: Float) {
        c = canvas; x = cx; y = cy; s = size
        val k = color
        when (kind) {
            SpecialKind.FROST_SPIRE -> frostSpire(k)
            SpecialKind.VENOM_FANG -> venomFang(k, time)
            SpecialKind.SUNBURST_CORE -> sunburst(k, time)
            SpecialKind.VOID_EYE -> voidEye(k, time)
            SpecialKind.STORM_SPIRE -> stormSpire(k, time)
            SpecialKind.GOLDEN_IDOL -> goldenIdol(k)
            SpecialKind.CRYSTAL_PRISON -> crystalPrison(k)
            SpecialKind.BLOOD_STONE -> bloodStone(k, time)
            SpecialKind.SPRAY_SPIRE -> spraySpire(k, time)
            SpecialKind.NEBULA_CANNON -> nebulaCannon(k, time)
            SpecialKind.PLAGUE_LORD -> plagueLord(k, time)
            SpecialKind.THUNDER_HAMMER -> thunderHammer(k, time)
            SpecialKind.STAR_HEART -> starHeart(k, time)
            SpecialKind.CHAOS_RIFT -> chaosRift(k, time)
            SpecialKind.VOLCANO_HEART -> volcano(k, time)
            SpecialKind.ABYSS_TIDE -> abyssTide(k, time)
            SpecialKind.WORLD_TREE -> worldTree(k, time)
            SpecialKind.SKY_ARROW -> skyArrow(k, time)
            SpecialKind.ABSOLUTE_ZERO -> absoluteZero(k, time)
            SpecialKind.THUNDER_GOD -> thunderGod(k, time)
            SpecialKind.GOLDEN_REAPER -> goldenReaper(k, time)
            SpecialKind.URANIUM -> uranium(k, time)
        }
    }

    /** 서리 첨탑: 얼음 결정 기둥 세 개 */
    private fun frostSpire(k: Int) {
        val light = blend(k, Color.WHITE, 0.6f)
        val dark = blend(k, Color.BLACK, 0.25f)
        oval(withAlpha(Color.WHITE, 200), 0f, 0.28f, 0.32f, 0.07f)
        for (side in intArrayOf(-1, 1)) {
            val d = side.toFloat()
            poly(dark, d * 0.10f, 0.27f, d * 0.12f, -0.03f, d * 0.20f, -0.19f, d * 0.28f, -0.03f, d * 0.27f, 0.27f)
            poly(light, d * 0.10f, 0.27f, d * 0.12f, -0.03f, d * 0.20f, -0.19f, d * 0.19f, 0.27f)
        }
        poly(dark, -0.09f, 0.28f, -0.09f, -0.20f, 0f, -0.40f, 0.09f, -0.20f, 0.09f, 0.28f)
        poly(light, -0.09f, 0.28f, -0.09f, -0.20f, 0f, -0.40f, 0f, 0.28f)
        seg(withAlpha(Color.WHITE, 220), 0.02f, -0.04f, -0.22f, -0.04f, 0.18f)
    }

    /** 맹독 송곳니: 독이 떨어지는 두 개의 송곳니 */
    private fun venomFang(k: Int, time: Float) {
        rect.set(u(-0.32f), v(-0.38f), u(0.32f), v(-0.26f))
        fill.color = blend(k, Color.BLACK, 0.55f)
        c.drawRoundRect(rect, s * 0.06f, s * 0.06f, fill)
        val ivory = 0xFFF2EEDC.toInt()
        for (side in intArrayOf(-1, 1)) {
            val d = side.toFloat()
            path.reset()
            mv(d * 0.27f, -0.29f)
            qd(d * 0.27f, 0.05f, d * 0.05f, 0.20f)
            qd(d * 0.12f, -0.05f, d * 0.03f, -0.29f)
            path.close()
            fillPath(ivory)
            strokePath(blend(k, Color.BLACK, 0.3f), 0.018f)
            seg(withAlpha(k, 200), 0.03f, d * 0.20f, -0.22f, d * 0.12f, 0.0f)
        }
        val t = (time * 0.7f) % 1f
        drop(k, 0.05f, 0.24f + t * 0.10f, 0.045f)
        drop(withAlpha(k, 170), -0.06f, 0.26f, 0.03f)
    }

    /** 태양 폭발핵: 회전하는 햇살과 빛나는 핵 */
    private fun sunburst(k: Int, time: Float) {
        val n = 12
        for (i in 0 until n) {
            val a = time * 0.6f + i * (2 * Math.PI / n).toFloat()
            val len = if (i % 2 == 0) 0.40f else 0.32f
            val w = 0.10f
            poly(if (i % 2 == 0) blend(k, Color.WHITE, 0.25f) else blend(k, 0xFFFF6A00.toInt(), 0.4f),
                cos(a - w) * 0.18f, sin(a - w) * 0.18f, cos(a) * len, sin(a) * len, cos(a + w) * 0.18f, sin(a + w) * 0.18f)
        }
        circle(blend(k, 0xFFFF5500.toInt(), 0.35f), 0f, 0f, 0.21f)
        circle(k, 0f, 0f, 0.16f)
        circle(blend(k, Color.WHITE, 0.7f), -0.04f, -0.04f, 0.07f)
    }

    /** 공허의 눈: 세로 동공의 눈 */
    private fun voidEye(k: Int, time: Float) {
        val dark = 0xFF14081F.toInt()
        val ringA = (time * 40f) % 360f
        line.color = withAlpha(k, 150); line.strokeWidth = s * 0.025f
        rect.set(u(-0.38f), v(-0.38f), u(0.38f), v(0.38f))
        for (i in 0 until 3) c.drawArc(rect, ringA + i * 120f, 70f, false, line)
        path.reset()
        mv(-0.36f, 0f); qd(0f, -0.36f, 0.36f, 0f); qd(0f, 0.36f, -0.36f, 0f); path.close()
        fillPath(dark)
        strokePath(blend(k, Color.WHITE, 0.3f), 0.025f)
        circle(k, 0f, 0f, 0.15f)
        circle(blend(k, Color.BLACK, 0.4f), 0f, 0f, 0.10f)
        val blink = 0.035f + 0.015f * sin(time * 2f)
        oval(Color.BLACK, 0f, 0f, blink, 0.12f)
        circle(Color.WHITE, -0.06f, -0.06f, 0.03f)
    }

    /** 폭풍 첨탑: 번개가 치는 오벨리스크 */
    private fun stormSpire(k: Int, time: Float) {
        oval(withAlpha(0xFF8A93A8.toInt(), 220), 0f, -0.32f, 0.24f, 0.07f)
        circle(withAlpha(0xFF8A93A8.toInt(), 220), -0.10f, -0.36f, 0.08f)
        circle(withAlpha(0xFF8A93A8.toInt(), 220), 0.08f, -0.38f, 0.07f)
        poly(blend(k, Color.BLACK, 0.45f), -0.11f, 0.30f, -0.07f, -0.16f, 0f, -0.26f, 0.07f, -0.16f, 0.11f, 0.30f)
        poly(blend(k, Color.BLACK, 0.2f), -0.11f, 0.30f, -0.07f, -0.16f, 0f, -0.26f, 0f, 0.30f)
        circle(k, 0f, -0.08f, 0.035f)
        val flash = ((time * 2.3f) % 1f) < 0.55f
        if (flash) {
            val bolt = blend(k, Color.WHITE, 0.5f)
            poly(bolt, 0.18f, -0.30f, 0.10f, -0.08f, 0.18f, -0.08f, 0.10f, 0.14f, 0.30f, -0.14f, 0.21f, -0.14f, 0.28f, -0.30f)
            poly(bolt, -0.20f, -0.28f, -0.26f, -0.10f, -0.20f, -0.10f, -0.27f, 0.06f, -0.13f, -0.14f, -0.19f, -0.14f, -0.14f, -0.28f)
        }
    }

    /** 황금 우상: 받침대 위의 왕관 쓴 조각상 */
    private fun goldenIdol(k: Int) {
        val dark = blend(k, Color.BLACK, 0.35f)
        val light = blend(k, Color.WHITE, 0.35f)
        rect.set(u(-0.27f), v(0.18f), u(0.27f), v(0.30f))
        fill.color = dark; c.drawRect(rect, fill)
        rect.set(u(-0.27f), v(0.18f), u(0.0f), v(0.30f))
        fill.color = k; c.drawRect(rect, fill)
        poly(dark, -0.16f, 0.18f, -0.10f, -0.09f, 0.10f, -0.09f, 0.16f, 0.18f)
        poly(k, -0.16f, 0.18f, -0.10f, -0.09f, 0f, -0.09f, 0f, 0.18f)
        poly(dark, -0.10f, -0.06f, -0.24f, 0.08f, -0.20f, 0.11f, -0.08f, 0.02f)
        poly(dark, 0.10f, -0.06f, 0.24f, 0.08f, 0.20f, 0.11f, 0.08f, 0.02f)
        circle(k, 0f, -0.20f, 0.11f)
        circle(light, -0.03f, -0.23f, 0.05f)
        poly(light, -0.11f, -0.27f, -0.12f, -0.40f, -0.05f, -0.31f, 0f, -0.42f, 0.05f, -0.31f, 0.12f, -0.40f, 0.11f, -0.27f)
        circle(0xFFE0203A.toInt(), -0.04f, -0.20f, 0.022f)
        circle(0xFFE0203A.toInt(), 0.04f, -0.20f, 0.022f)
        circle(0xFF3FD0FF.toInt(), 0f, 0.04f, 0.035f)
    }

    /** 수정 감옥: 창살에 갇힌 결정 */
    private fun crystalPrison(k: Int) {
        poly(blend(k, Color.WHITE, 0.45f), 0f, -0.28f, 0.13f, -0.02f, 0f, 0.24f, -0.13f, -0.02f)
        poly(blend(k, Color.BLACK, 0.2f), 0f, -0.28f, 0.13f, -0.02f, 0f, 0.24f)
        val bar = 0xFFB8BCC8.toInt()
        rect.set(u(-0.30f), v(-0.40f), u(0.30f), v(0.0f))
        line.color = bar; line.strokeWidth = s * 0.045f
        c.drawArc(rect, 180f, 180f, false, line)
        for (i in 0 until 5) {
            val bx = -0.30f + i * 0.15f
            val topY = if (i == 0 || i == 4) -0.20f else -0.20f - 0.20f * kotlin.math.sqrt(1f - (bx / 0.30f) * (bx / 0.30f))
            seg(bar, 0.035f, bx, topY, bx, 0.28f)
        }
        seg(bar, 0.05f, -0.33f, 0.28f, 0.33f, 0.28f)
        seg(bar, 0.04f, -0.31f, 0.04f, 0.31f, 0.04f)
        circle(0xFFFFCC33.toInt(), 0f, -0.41f, 0.04f)
    }

    /** 피의 결정: 고동치는 하트 컷 보석 */
    private fun bloodStone(k: Int, time: Float) {
        val beat = 1f + 0.06f * maxOf(0f, sin(time * 5f))
        c.save(); c.scale(beat, beat, x, y)
        val pts = floatArrayOf(0f, 0.30f, -0.31f, -0.01f, -0.31f, -0.15f, -0.21f, -0.28f, -0.09f, -0.27f, 0f, -0.17f,
            0.09f, -0.27f, 0.21f, -0.28f, 0.31f, -0.15f, 0.31f, -0.01f)
        val cy0 = -0.07f
        val n = pts.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val t = i.toFloat() / n
            val col = if (t > 0.1f && t < 0.6f) blend(k, Color.WHITE, 0.35f - t * 0.4f) else blend(k, Color.BLACK, 0.15f + kotlin.math.abs(t - 0.75f) * 0.6f)
            poly(col, pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1], 0f, cy0)
        }
        outline(blend(k, Color.BLACK, 0.5f), 0.015f, *pts)
        circle(withAlpha(Color.WHITE, 200), -0.17f, -0.17f, 0.04f)
        c.restore()
    }

    /** 물보라 첨탑: 물줄기를 뿜는 분수 첨탑 */
    private fun spraySpire(k: Int, time: Float) {
        oval(blend(k, Color.BLACK, 0.4f), 0f, 0.26f, 0.32f, 0.08f)
        oval(k, 0f, 0.24f, 0.27f, 0.05f)
        poly(blend(k, Color.BLACK, 0.2f), -0.08f, 0.24f, 0f, -0.14f, 0.08f, 0.24f)
        poly(blend(k, Color.WHITE, 0.35f), -0.08f, 0.24f, 0f, -0.14f, 0f, 0.24f)
        val drop = blend(k, Color.WHITE, 0.55f)
        for (side in intArrayOf(-1, 1)) for (i in 0 until 6) {
            val p = (time * 0.9f + i / 6f + if (side > 0) 0.08f else 0f) % 1f
            val dx = side * 0.30f * p
            val dy = -0.14f - 0.62f * p + 0.70f * p * p
            circle(withAlpha(drop, (255 * (1f - p * 0.5f)).toInt()), dx, dy, 0.035f - p * 0.012f)
        }
        val up = (time * 1.5f) % 1f
        circle(withAlpha(drop, 220), 0f, -0.18f - up * 0.18f, 0.03f)
    }

    /** 성운 포대: 성운 구슬을 겨눈 포 */
    private fun nebulaCannon(k: Int, time: Float) {
        val metal = 0xFF59607A.toInt()
        c.save(); c.rotate(-35f, x, y + 0.12f * s)
        rect.set(u(-0.08f), v(-0.24f), u(0.08f), v(0.12f))
        fill.color = metal; c.drawRoundRect(rect, s * 0.03f, s * 0.03f, fill)
        rect.set(u(-0.11f), v(-0.28f), u(0.11f), v(-0.20f))
        fill.color = blend(metal, Color.WHITE, 0.25f); c.drawRect(rect, fill)
        rect.set(u(-0.08f), v(-0.24f), u(-0.03f), v(0.12f))
        fill.color = blend(metal, Color.WHITE, 0.3f); c.drawRect(rect, fill)
        c.restore()
        rect.set(u(-0.26f), v(0.04f), u(0.26f), v(0.40f))
        fill.color = blend(metal, Color.BLACK, 0.3f)
        c.drawArc(rect, 180f, 180f, true, fill)
        circle(k, 0f, 0.15f, 0.06f)
        // 성운 소용돌이
        val ncx = 0.20f; val ncy = -0.26f
        circle(withAlpha(k, 90), ncx, ncy, 0.15f)
        for (i in 0 until 14) {
            val a = time * 2f + i * 0.9f
            val r = 0.02f + i * 0.009f
            circle(if (i % 2 == 0) blend(k, Color.WHITE, 0.5f) else 0xFFFF7AD9.toInt(), ncx + cos(a) * r, ncy + sin(a) * r * 0.75f, 0.022f)
        }
        circle(Color.WHITE, ncx, ncy, 0.03f)
    }

    /** 역병 군주: 왕관 쓴 해골과 독 거품 */
    private fun plagueLord(k: Int, time: Float) {
        val bone = 0xFFE7E3CF.toInt()
        for (i in 0 until 3) {
            val p = (time * 0.6f + i / 3f) % 1f
            circle(withAlpha(k, (220 * (1f - p)).toInt()), -0.26f + i * 0.26f, 0.30f - p * 0.65f, 0.03f + p * 0.02f)
        }
        circle(bone, 0f, -0.04f, 0.20f)
        rect.set(u(-0.11f), v(0.06f), u(0.11f), v(0.24f))
        fill.color = bone; c.drawRoundRect(rect, s * 0.04f, s * 0.04f, fill)
        val glow = withAlpha(k, (180 + 75 * sin(time * 4f)).toInt())
        circle(0xFF1B1F14.toInt(), -0.08f, -0.03f, 0.055f)
        circle(0xFF1B1F14.toInt(), 0.08f, -0.03f, 0.055f)
        circle(glow, -0.08f, -0.03f, 0.028f)
        circle(glow, 0.08f, -0.03f, 0.028f)
        poly(0xFF1B1F14.toInt(), 0f, 0.04f, 0.03f, 0.09f, -0.03f, 0.09f)
        for (i in 0 until 3) seg(0xFF6E6A5A.toInt(), 0.012f, -0.06f + i * 0.06f, 0.15f, -0.06f + i * 0.06f, 0.24f)
        poly(blend(k, 0xFFB08A2E.toInt(), 0.6f), -0.18f, -0.18f, -0.20f, -0.36f, -0.10f, -0.26f, 0f, -0.40f, 0.10f, -0.26f, 0.20f, -0.36f, 0.18f, -0.18f)
        circle(k, 0f, -0.26f, 0.03f)
        // 흘러내리는 독
        val drip = (time * 0.8f) % 1f
        seg(k, 0.03f, 0.13f, 0.00f, 0.13f, 0.06f + drip * 0.10f)
    }

    /** 천둥 망치: 번개가 튀는 전쟁 망치 */
    private fun thunderHammer(k: Int, time: Float) {
        c.save(); c.rotate(-25f, x, y)
        rect.set(u(-0.035f), v(-0.15f), u(0.035f), v(0.36f))
        fill.color = 0xFF7A5230.toInt(); c.drawRoundRect(rect, s * 0.02f, s * 0.02f, fill)
        for (i in 0 until 3) seg(0xFF4A3018.toInt(), 0.02f, -0.035f, 0.18f + i * 0.05f, 0.035f, 0.21f + i * 0.05f)
        val metal = 0xFF9AA3B5.toInt()
        rect.set(u(-0.26f), v(-0.36f), u(0.26f), v(-0.12f))
        fill.color = blend(metal, Color.BLACK, 0.3f); c.drawRoundRect(rect, s * 0.04f, s * 0.04f, fill)
        rect.set(u(-0.26f), v(-0.36f), u(0.26f), v(-0.26f))
        fill.color = metal; c.drawRoundRect(rect, s * 0.04f, s * 0.04f, fill)
        circle(k, 0f, -0.24f, 0.06f)
        c.restore()
        if (((time * 3f) % 1f) < 0.6f) {
            val sp = blend(k, Color.WHITE, 0.5f)
            polyline(sp, 0.025f, 0.24f, -0.34f, 0.32f, -0.26f, 0.27f, -0.22f, 0.37f, -0.14f)
            polyline(sp, 0.025f, -0.30f, -0.04f, -0.36f, 0.04f, -0.30f, 0.06f, -0.37f, 0.15f)
        }
    }

    /** 별의 심장: 면이 나뉜 별 */
    private fun starHeart(k: Int, time: Float) {
        val rot = sin(time * 1.2f) * 8f
        c.save(); c.rotate(rot, x, y)
        val ro = 0.40f; val ri = 0.17f
        for (i in 0 until 5) {
            val a = (-Math.PI / 2 + i * 2 * Math.PI / 5).toFloat()
            val al = a - (Math.PI / 5).toFloat()
            val ar = a + (Math.PI / 5).toFloat()
            val tx = cos(a) * ro; val ty = sin(a) * ro + 0.02f
            poly(blend(k, Color.WHITE, 0.4f), 0f, 0.02f, cos(al) * ri, sin(al) * ri + 0.02f, tx, ty)
            poly(blend(k, Color.BLACK, 0.25f), 0f, 0.02f, tx, ty, cos(ar) * ri, sin(ar) * ri + 0.02f)
        }
        c.restore()
        // 가운데 작은 하트
        circle(0xFFFF4D7A.toInt(), -0.035f, -0.005f, 0.045f)
        circle(0xFFFF4D7A.toInt(), 0.035f, -0.005f, 0.045f)
        poly(0xFFFF4D7A.toInt(), -0.078f, 0.012f, 0.078f, 0.012f, 0f, 0.09f)
        sparkle(0.28f, -0.30f, 0.08f, time * 3f)
    }

    /** 혼돈의 균열: 공간이 찢어진 틈 */
    private fun chaosRift(k: Int, time: Float) {
        for (i in 0 until 8) {
            val a = time * 1.5f + i * 0.785f
            val r = 0.30f + 0.06f * sin(time * 3f + i)
            circle(withAlpha(if (i % 2 == 0) k else 0xFF6A3DFF.toInt(), 200), cos(a) * r, sin(a) * r, 0.025f)
        }
        val outer = floatArrayOf(0.02f, -0.40f, 0.12f, -0.22f, 0.06f, -0.12f, 0.16f, 0.02f, 0.06f, 0.14f, 0.11f, 0.26f, -0.02f, 0.40f,
            -0.10f, 0.22f, -0.04f, 0.10f, -0.15f, -0.02f, -0.05f, -0.14f, -0.11f, -0.26f)
        poly(withAlpha(k, 230), *outer)
        val inner = FloatArray(outer.size) { outer[it] * if (it % 2 == 0) 0.45f else 0.82f }
        poly(0xFF0A0012.toInt(), *inner)
        val flick = (sin(time * 9f) * 0.5f + 0.5f)
        outline(withAlpha(blend(k, Color.WHITE, 0.6f), (120 + 135 * flick).toInt()), 0.015f, *outer)
        circle(withAlpha(Color.WHITE, 200), 0.0f, (time * 0.4f % 1f) * 0.5f - 0.25f, 0.015f)
    }

    /** 화산의 심장: 용암을 뿜는 화산 */
    private fun volcano(k: Int, time: Float) {
        val rock = 0xFF4A3530.toInt()
        poly(rock, -0.38f, 0.30f, -0.10f, -0.12f, 0.10f, -0.12f, 0.38f, 0.30f)
        poly(blend(rock, Color.WHITE, 0.15f), -0.38f, 0.30f, -0.10f, -0.12f, -0.02f, -0.12f, -0.12f, 0.30f)
        poly(k, -0.10f, -0.12f, 0.10f, -0.12f, 0.06f, -0.06f, 0.0f, 0.10f, -0.03f, -0.03f, -0.08f, 0.04f, -0.06f, -0.06f)
        polyline(k, 0.03f, 0.08f, -0.08f, 0.16f, 0.06f, 0.14f, 0.16f, 0.22f, 0.28f)
        val core = blend(k, 0xFFFFE066.toInt(), 0.5f)
        circle(core, 0f, -0.13f, 0.06f)
        for (i in 0 until 4) {
            val p = (time * 0.9f + i / 4f) % 1f
            val dx = (i - 1.5f) * 0.10f * p
            val dy = -0.15f - 0.45f * p + 0.35f * p * p
            circle(withAlpha(if (i % 2 == 0) k else core, (255 * (1f - p * 0.6f)).toInt()), dx, dy, 0.04f - p * 0.015f)
        }
        circle(withAlpha(0xFF6B6B6B.toInt(), 120), -0.10f, -0.36f, 0.06f)
        circle(withAlpha(0xFF6B6B6B.toInt(), 100), 0.02f, -0.40f, 0.05f)
    }

    /** 심연의 해일: 말려 올라가는 큰 파도 */
    private fun abyssTide(k: Int, time: Float) {
        val bob = sin(time * 2f) * 0.02f
        c.save(); c.translate(0f, bob * s)
        path.reset()
        mv(-0.36f, 0.30f); ln(-0.36f, 0.02f)
        qd(-0.30f, -0.36f, 0.06f, -0.36f)
        qd(0.36f, -0.34f, 0.32f, -0.06f)
        qd(0.14f, -0.22f, 0.04f, -0.08f)
        qd(-0.02f, 0.10f, 0.36f, 0.12f)
        ln(0.36f, 0.30f); path.close()
        fillPath(blend(k, Color.BLACK, 0.35f))
        path.reset()
        mv(-0.36f, 0.02f); qd(-0.30f, -0.36f, 0.06f, -0.36f); qd(0.36f, -0.34f, 0.32f, -0.06f)
        qd(0.20f, -0.26f, 0.02f, -0.24f); qd(-0.22f, -0.20f, -0.36f, 0.20f); path.close()
        fillPath(k)
        path.reset()
        mv(-0.36f, 0.22f); qd(0f, 0.12f, 0.36f, 0.22f); ln(0.36f, 0.30f); ln(-0.36f, 0.30f); path.close()
        fillPath(blend(k, Color.BLACK, 0.55f))
        val foam = 0xFFF0FAFF.toInt()
        for (i in 0 until 5) circle(foam, 0.06f + i * 0.055f, -0.34f + i * 0.055f * (i * 0.25f), 0.035f - i * 0.003f)
        circle(foam, 0.30f, -0.09f, 0.03f)
        c.restore()
    }

    /** 맹독 세계수: 독 열매가 맺힌 나무 */
    private fun worldTree(k: Int, time: Float) {
        val bark = 0xFF5C4130.toInt()
        poly(bark, -0.06f, 0.30f, -0.04f, -0.04f, 0.04f, -0.04f, 0.06f, 0.30f)
        polyline(bark, 0.035f, -0.04f, 0.26f, -0.16f, 0.32f)
        polyline(bark, 0.035f, 0.04f, 0.26f, 0.17f, 0.32f)
        polyline(bark, 0.03f, 0.0f, 0.04f, -0.14f, -0.08f)
        val leaf = blend(k, Color.BLACK, 0.25f)
        circle(leaf, -0.18f, -0.10f, 0.14f)
        circle(leaf, 0.18f, -0.10f, 0.14f)
        circle(k, 0f, -0.22f, 0.18f)
        circle(k, -0.12f, -0.18f, 0.12f)
        circle(blend(k, Color.WHITE, 0.25f), 0.10f, -0.28f, 0.10f)
        val fruit = 0xFFB040E0.toInt()
        val sway = sin(time * 2f) * 0.01f
        circle(fruit, -0.20f + sway, -0.02f, 0.035f)
        circle(fruit, 0.06f + sway, -0.14f, 0.035f)
        circle(fruit, 0.22f + sway, -0.05f, 0.035f)
        val p = (time * 0.7f) % 1f
        drop(withAlpha(fruit, (255 * (1f - p)).toInt()), 0.22f, 0.0f + p * 0.25f, 0.025f)
    }

    /** 천공의 화살: 날개 달린 화살 */
    private fun skyArrow(k: Int, time: Float) {
        c.save(); c.rotate(40f, x, y)
        val trail = (sin(time * 6f) * 0.5f + 0.5f)
        for (i in 0 until 3) circle(withAlpha(k, (80 + 60 * trail).toInt() - i * 20), 0f, 0.34f + i * 0.05f, 0.03f - i * 0.006f)
        rect.set(u(-0.022f), v(-0.24f), u(0.022f), v(0.32f))
        fill.color = blend(k, Color.WHITE, 0.6f); c.drawRect(rect, fill)
        poly(blend(k, Color.WHITE, 0.3f), -0.10f, -0.20f, 0f, -0.42f, 0.10f, -0.20f, 0f, -0.25f)
        poly(blend(k, Color.BLACK, 0.2f), 0f, -0.42f, 0.10f, -0.20f, 0f, -0.25f)
        poly(k, -0.02f, 0.18f, -0.10f, 0.26f, -0.10f, 0.36f, -0.02f, 0.30f)
        poly(k, 0.02f, 0.18f, 0.10f, 0.26f, 0.10f, 0.36f, 0.02f, 0.30f)
        val flap = sin(time * 5f) * 0.03f
        val wing = 0xFFF5F8FF.toInt()
        for (side in intArrayOf(-1, 1)) {
            val d = side.toFloat()
            poly(wing, d * 0.02f, -0.02f, d * 0.30f, -0.14f - flap, d * 0.26f, -0.04f, d * 0.22f, 0.02f, d * 0.16f, 0.06f, d * 0.02f, 0.08f)
            seg(withAlpha(k, 150), 0.012f, d * 0.04f, 0.02f, d * 0.24f, -0.08f - flap)
        }
        c.restore()
    }

    /** 절대영도: 천천히 도는 눈 결정 */
    private fun absoluteZero(k: Int, time: Float) {
        circle(withAlpha(k, 60), 0f, 0f, 0.40f)
        c.save(); c.rotate(time * 12f, x, y)
        val ice = blend(k, Color.WHITE, 0.6f)
        for (i in 0 until 6) {
            val a = (i * Math.PI / 3 - Math.PI / 2).toFloat()
            val ex = cos(a); val ey = sin(a)
            seg(ice, 0.045f, 0f, 0f, ex * 0.38f, ey * 0.38f)
            for (b in floatArrayOf(0.16f, 0.27f)) {
                val bx = ex * b; val by = ey * b
                val l = 0.11f - b * 0.15f
                for (sd in intArrayOf(-1, 1)) {
                    val ba = a + sd * 0.8f
                    seg(ice, 0.03f, bx, by, bx + cos(ba) * l, by + sin(ba) * l)
                }
            }
        }
        val hex = FloatArray(12) { if (it % 2 == 0) cos(it / 2 * Math.PI.toFloat() / 3) * 0.09f else sin(it / 2 * Math.PI.toFloat() / 3) * 0.09f }
        poly(k, *hex)
        c.restore()
        circle(Color.WHITE, 0f, 0f, 0.03f)
    }

    /** 뇌신의 심판: 북 고리(뇌신의 북)와 가운데 번개 */
    private fun thunderGod(k: Int, time: Float) {
        line.color = 0xFFB08A2E.toInt(); line.strokeWidth = s * 0.03f
        c.drawCircle(x, y, s * 0.31f, line)
        for (i in 0 until 8) {
            val a = time * 0.8f + i * (Math.PI / 4).toFloat()
            val dx = cos(a) * 0.31f; val dy = sin(a) * 0.31f
            circle(0xFF8C2A1E.toInt(), dx, dy, 0.07f)
            circle(0xFFE8DCC0.toInt(), dx, dy, 0.05f)
            circle(0xFF8C2A1E.toInt(), dx, dy, 0.015f)
        }
        val flash = ((time * 2f) % 1f) < 0.7f
        val bolt = if (flash) blend(k, Color.WHITE, 0.45f) else k
        poly(bolt, 0.04f, -0.24f, -0.10f, 0.03f, -0.01f, 0.03f, -0.06f, 0.24f, 0.11f, -0.04f, 0.02f, -0.04f, 0.09f, -0.24f)
        if (flash) outline(withAlpha(Color.WHITE, 200), 0.012f, 0.04f, -0.24f, -0.10f, 0.03f, -0.01f, 0.03f, -0.06f, 0.24f, 0.11f, -0.04f, 0.02f, -0.04f, 0.09f, -0.24f)
    }

    /** 황금 사신: 두건 쓴 사신과 황금 낫 */
    private fun goldenReaper(k: Int, time: Float) {
        val robe = 0xFF262033.toInt()
        path.reset()
        mv(0f, -0.28f); qd(0.21f, -0.26f, 0.23f, 0.30f); ln(-0.23f, 0.30f); qd(-0.21f, -0.26f, 0f, -0.28f); path.close()
        fillPath(robe)
        path.reset()
        mv(0f, -0.28f); qd(-0.21f, -0.26f, -0.23f, 0.30f); ln(-0.08f, 0.30f); qd(-0.10f, -0.10f, 0f, -0.28f); path.close()
        fillPath(blend(robe, Color.WHITE, 0.12f))
        oval(0xFF07050C.toInt(), 0f, -0.08f, 0.09f, 0.11f)
        val eye = withAlpha(k, (190 + 65 * sin(time * 3f)).toInt())
        circle(eye, -0.035f, -0.09f, 0.022f)
        circle(eye, 0.035f, -0.09f, 0.022f)
        seg(0xFF6B4A2A.toInt(), 0.035f, 0.27f, -0.34f, 0.10f, 0.36f)
        path.reset()
        mv(0.27f, -0.35f); qd(0.02f, -0.48f, -0.32f, -0.28f); qd(-0.02f, -0.37f, 0.25f, -0.26f); path.close()
        fillPath(k)
        strokePath(blend(k, Color.WHITE, 0.5f), 0.012f)
        circle(blend(k, Color.WHITE, 0.4f), 0.27f, -0.34f, 0.03f)
    }

    /** 우라늄: 맥동하는 초록 빛 + 방사능 표시 */
    private fun uranium(k: Int, time: Float) {
        val pulse = sin(time * 4f) * 0.5f + 0.5f
        circle(withAlpha(k, (50 + pulse * 70).toInt()), 0f, 0f, 0.44f + pulse * 0.06f)
        circle(k, 0f, 0f, 0.34f)
        fill.color = 0xFF1A1F0E.toInt()
        rect.set(u(-0.28f), v(-0.28f), u(0.28f), v(0.28f))
        for (i in 0 until 3) c.drawArc(rect, -120f + i * 120f + time * 30f, 60f, true, fill)
        circle(k, 0f, 0f, 0.09f)
        circle(0xFF1A1F0E.toInt(), 0f, 0f, 0.055f)
    }


    // ------------------------------------------------------------ 강화 별

    /**
     * 강화 단계 별: 꼭짓점 조각 하나가 강화 1단계. max 단계면 별이 모두 채워지고 빛남.
     * (cx, cy) 화면 좌표 중심, r 바깥 반지름(픽셀).
     */
    fun drawUpgradeStar(canvas: Canvas, cx: Float, cy: Float, r: Float, level: Int, max: Int, time: Float) {
        c = canvas; x = cx; y = cy; s = r
        val ri = 0.45f
        val gold = 0xFFFFC93C.toInt()
        val full = level >= max
        if (full) {
            val p = sin(time * 4f) * 0.5f + 0.5f
            circle(withAlpha(gold, (60 + 70 * p).toInt()), 0f, 0f, 1.25f + 0.1f * p)
        }
        val outer = FloatArray(20)
        for (i in 0 until 10) {
            val a = (-Math.PI / 2 + i * Math.PI / 5).toFloat()
            val rr = if (i % 2 == 0) 1f else ri
            outer[i * 2] = cos(a) * rr; outer[i * 2 + 1] = sin(a) * rr
        }
        poly(0xCC1A1C22.toInt(), *outer)
        // 위 꼭짓점부터 시계 방향으로 조각을 채움
        for (i in 0 until 5) {
            if (i >= level) break
            val tip = i * 2
            val left = (tip + 9) % 10
            val right = tip + 1
            val col = if (full) gold else 0xFFFF9A3C.toInt()
            poly(col, 0f, 0f, outer[left * 2], outer[left * 2 + 1], outer[tip * 2], outer[tip * 2 + 1], outer[right * 2], outer[right * 2 + 1])
            poly(blend(col, Color.WHITE, 0.35f), 0f, 0f, outer[left * 2], outer[left * 2 + 1], outer[tip * 2], outer[tip * 2 + 1])
        }
        outline(if (full) 0xFFFFF2B0.toInt() else 0xFF8A8F9C.toInt(), 0.09f, *outer)
        if (full) sparkle(0.75f, -0.75f, 0.45f, time * 3f)
    }
    // ------------------------------------------------------------ 도우미

    private fun poly(col: Int, vararg p: Float) {
        path.reset()
        path.moveTo(u(p[0]), v(p[1]))
        for (i in 2 until p.size step 2) path.lineTo(u(p[i]), v(p[i + 1]))
        path.close()
        fillPath(col)
    }

    private fun outline(col: Int, w: Float, vararg p: Float) {
        path.reset()
        path.moveTo(u(p[0]), v(p[1]))
        for (i in 2 until p.size step 2) path.lineTo(u(p[i]), v(p[i + 1]))
        path.close()
        strokePath(col, w)
    }

    private fun polyline(col: Int, w: Float, vararg p: Float) {
        path.reset()
        path.moveTo(u(p[0]), v(p[1]))
        for (i in 2 until p.size step 2) path.lineTo(u(p[i]), v(p[i + 1]))
        strokePath(col, w)
    }

    private fun seg(col: Int, w: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        line.color = col
        line.strokeWidth = s * w
        c.drawLine(u(x1), v(y1), u(x2), v(y2), line)
    }

    private fun circle(col: Int, a: Float, b: Float, r: Float) {
        fill.color = col
        c.drawCircle(u(a), v(b), r * s, fill)
    }

    private fun oval(col: Int, a: Float, b: Float, rx: Float, ry: Float) {
        fill.color = col
        rect.set(u(a - rx), v(b - ry), u(a + rx), v(b + ry))
        c.drawOval(rect, fill)
    }

    /** 물방울: 위가 뾰족한 원 */
    private fun drop(col: Int, a: Float, b: Float, r: Float) {
        circle(col, a, b, r)
        poly(col, a - r * 0.87f, b - r * 0.5f, a, b - r * 2.2f, a + r * 0.87f, b - r * 0.5f)
    }

    private fun sparkle(a: Float, b: Float, r: Float, phase: Float) {
        val t = (sin(phase) + 1f) / 2f
        val z = r * (0.3f + 0.7f * t)
        val q = z * 0.2f
        poly(withAlpha(Color.WHITE, (120 + 135 * t).toInt()), a, b - z, a + q, b - q, a + z, b, a + q, b + q, a, b + z, a - q, b + q, a - z, b, a - q, b - q)
    }

    private fun mv(a: Float, b: Float) = path.moveTo(u(a), v(b))
    private fun ln(a: Float, b: Float) = path.lineTo(u(a), v(b))
    private fun qd(a: Float, b: Float, c2: Float, d: Float) = path.quadTo(u(a), v(b), u(c2), v(d))

    private fun fillPath(col: Int) {
        fill.color = col
        c.drawPath(path, fill)
    }

    private fun strokePath(col: Int, w: Float) {
        line.color = col
        line.strokeWidth = s * w
        c.drawPath(path, line)
    }

    private companion object {
        fun withAlpha(col: Int, a: Int) = (col and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)

        fun blend(a: Int, b: Int, t: Float): Int {
            val k = 1f - t
            return Color.argb(
                Color.alpha(a),
                (Color.red(a) * k + Color.red(b) * t).toInt(),
                (Color.green(a) * k + Color.green(b) * t).toInt(),
                (Color.blue(a) * k + Color.blue(b) * t).toInt(),
            )
        }
    }
}
