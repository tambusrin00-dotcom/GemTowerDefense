package com.tambusrin.gemtower

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import com.tambusrin.gemtower.game.Beam
import com.tambusrin.gemtower.game.Cell
import com.tambusrin.gemtower.game.CellType
import com.tambusrin.gemtower.game.Game
import com.tambusrin.gemtower.game.GameState
import com.tambusrin.gemtower.game.GemDB
import com.tambusrin.gemtower.game.GemKey
import com.tambusrin.gemtower.game.GemQuality
import com.tambusrin.gemtower.game.Tower
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 게임 화면: Canvas 2D 렌더링 + 터치 처리. 게임 루프는 Choreographer로 메인 스레드에서 돌림. */
class GameView(context: Context) : View(context) {

    val game = Game()

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var speed = 1
    private var running = false
    private var lastFrameNanos = 0L
    private val frameCallback = Choreographer.FrameCallback { onFrame(it) }

    // ---- 레이아웃 ----
    private var safeL = 0f
    private var safeT = 0f
    private var safeR = 0f
    private var safeB = 0f
    private var cs = 1f          // 칸 크기(px)
    private var ox = 0f          // 보드 원점
    private var oy = 0f
    private val boardRect = RectF()   // 보드 전체 (화면 밖으로 나갈 수 있음)
    private val viewRect = RectF()    // 보드를 보여 주는 창
    private var minCs = 1f
    private var maxCs = 1f
    private var zoomInit = false
    private var panelHidden = false
    private var startZoomFit = false
    private val sideRect = RectF()   // 패널 숨김 때 간략 버튼 스크롤 영역
    private var sideScroll = 0f
    private var downSideScroll = 0f
    private var downInSide = false
    private val panelRect = RectF()
    private val scrollRect = RectF()
    private var scrollY = 0f
    private var contentHeight = 0f

    // ---- UI 상태 ----
    private var showRecipes = false
    private var recipeScroll = 0f
    private var downRecipeScroll = 0f
    private var downInRecipe = false
    private var newGameArmedUntil = 0L
    private var flashCell: Cell? = null
    private var flashTime = 0f

    private class Button(val rect: RectF, val enabled: Boolean, val action: () -> Unit)
    private val buttons = ArrayList<Button>()

    // ---- 터치 ----
    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragging = false
    private var downInScroll = false
    private var downInBoard = false
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector) = downInBoard
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomAt(d.focusX, d.focusY, d.scaleFactor)
            dragging = true
            return true
        }
    })

    // ---- 페인트 ----
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tmpRect = RectF()
    private val tmpPath = Path()
    private val routePath = Path()
    private var routeVersion = -1
    private var routeCs = -1f
    private var routeOx = 0f
    private var routeOy = 0f
    private val layoutCache = HashMap<String, StaticLayout>()

    private companion object {
        const val BG = 0xFF0E1117.toInt()
        const val BOARD = 0xFF1C2028.toInt()
        const val TILE_A = 0xFF4F5E4A.toInt()
        const val TILE_B = 0xFF45533F.toInt()
        const val PANEL = 0xF0151922.toInt()
        const val BTN = 0xFF34405A.toInt()
        const val BTN_DISABLED = 0xFF242833.toInt()
        const val BTN_GREEN = 0xFF2E7D4F.toInt()
        const val BTN_GOLD = 0xFF9A7420.toInt()
        const val BTN_PURPLE = 0xFF6A3FA0.toInt()
        const val BTN_RED = 0xFF9A3030.toInt()
        const val GOLD = 0xFFFFD866.toInt()
        const val TEXT_DIM = 0xFFC5CBD6.toInt()
        const val BTN_TEAL = 0xFF237A80.toInt()
        const val MOVE_COLOR = 0xFF5CE6F0.toInt()
        const val OWNED = 0xFF6BF08A.toInt()
        const val MISSING = 0xFF7A8290.toInt()
        const val DEFAULT_CELL_DP = 34f
        const val SIDE_BTN = 0xCC34405A.toInt()
    }

    init {
        isFocusable = true
        keepScreenOn = true
    }

    /** 스크린샷용 */
    internal fun debugShowRecipes() {
        showRecipes = true
        recipeScroll = 0f
    }

    /** 스크린샷용: 처음부터 보드 전체가 보이는 배율로 시작 */
    internal fun debugStartZoomFit() {
        startZoomFit = true
    }

    internal fun debugSetSpeed(s: Int) {
        speed = s
    }

    // =================================================================== 루프

    fun resume() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun onFrame(nanos: Long) {
        if (!running) return
        if (lastFrameNanos != 0L) {
            val dt = ((nanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
            game.update(dt * speed)
            game.tickUi(dt)
            if (flashTime > 0f) flashTime -= dt
        }
        lastFrameNanos = nanos
        invalidate()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    // =================================================================== 레이아웃

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        safeL = i.left.toFloat(); safeT = i.top.toFloat(); safeR = i.right.toFloat(); safeB = i.bottom.toFloat()
        computeLayout()
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeLayout()
    }

    private fun computeLayout() {
        if (width == 0 || height == 0) return
        val pad = dp(8f)
        val l = safeL + pad
        val t = safeT + pad
        val r = width - safeR - pad
        val b = height - safeB - pad
        // 오른쪽 패널은 고정 폭, 나머지는 보드를 보여 주는 창(스크롤·확대 가능)
        if (panelHidden) {
            viewRect.set(l, t, r, b)
            panelRect.setEmpty()
            scrollRect.setEmpty()
        } else {
            val panelW = ((r - l) * 0.36f).coerceIn(min(dp(240f), (r - l) * 0.45f), dp(340f))
            viewRect.set(l, t, r - panelW - pad, b)
            panelRect.set(viewRect.right + pad, t, r, b)
        }

        // 최소 배율 = 보드 전체가 창에 들어옴, 기본 배율 = 칸이 손가락으로 누르기 좋은 크기
        minCs = min(viewRect.width() / (Game.W + 0.6f), viewRect.height() / (Game.H + 0.6f))
        maxCs = max(minCs, dp(72f))
        if (!zoomInit) {
            cs = if (startZoomFit) minCs else dp(DEFAULT_CELL_DP).coerceIn(minCs, maxCs)
            ox = viewRect.centerX() - cs * Game.W / 2f
            oy = viewRect.centerY() - cs * Game.H / 2f
            zoomInit = true
        } else {
            val n = cs.coerceIn(minCs, maxCs)
            if (n != cs) zoomAt(viewRect.centerX(), viewRect.centerY(), n / cs)
        }
        clampPan()
    }

    /** 보드가 창 밖으로 완전히 밀려나지 않게 위치 보정. 창보다 작으면 가운데 정렬. */
    private fun clampPan() {
        val m = cs * 0.3f
        val bw = cs * Game.W
        val bh = cs * Game.H
        ox = if (bw + 2 * m <= viewRect.width()) viewRect.centerX() - bw / 2f
        else ox.coerceIn(viewRect.right - m - bw, viewRect.left + m)
        oy = if (bh + 2 * m <= viewRect.height()) viewRect.centerY() - bh / 2f
        else oy.coerceIn(viewRect.bottom - m - bh, viewRect.top + m)
        boardRect.set(ox, oy, ox + bw, oy + bh)
    }

    /** (fx, fy) 화면 지점을 고정한 채 factor 배 확대/축소 */
    private fun zoomAt(fx: Float, fy: Float, factor: Float) {
        val n = (cs * factor).coerceIn(minCs, maxCs)
        if (n == cs) return
        ox = fx - (fx - ox) * n / cs
        oy = fy - (fy - oy) * n / cs
        cs = n
        clampPan()
    }

    private fun togglePanel() {
        panelHidden = !panelHidden
        scrollY = 0f
        computeLayout()
    }

    /** 보드 오른쪽 위 버튼 열: 패널 토글 + 확대/축소. 패널을 숨기면 속도·조합법·품질·선택 행동 버튼도 간략하게 표시. */
    private fun drawSideControls(c: Canvas) {
        sideRect.setEmpty()
        if (showRecipes) return
        val gap = dp(6f)
        val zoomIn = { zoomAt(viewRect.centerX(), viewRect.centerY(), 1.35f) }
        val zoomOut = { zoomAt(viewRect.centerX(), viewRect.centerY(), 1f / 1.35f) }
        val zoomFit = { zoomAt(viewRect.centerX(), viewRect.centerY(), minCs / cs) }

        if (!panelHidden) {
            val s = dp(38f)
            val x = viewRect.right - dp(8f) - s
            var y = viewRect.top + dp(8f)
            button(c, RectF(x, y, x + s, y + s), "»", color = SIDE_BTN, action = ::togglePanel)
            y += s + gap * 2
            button(c, RectF(x, y, x + s, y + s), "+", enabled = cs < maxCs, color = SIDE_BTN, action = zoomIn)
            y += s + gap
            button(c, RectF(x, y, x + s, y + s), "−", enabled = cs > minCs, color = SIDE_BTN, action = zoomOut)
            y += s + gap
            button(c, RectF(x, y, x + s, y + s), "전체", enabled = cs > minCs, color = SIDE_BTN, action = zoomFit)
            return
        }

        val w = dp(116f)
        val bh = dp(34f)
        val x = viewRect.right - dp(8f) - w
        var y = viewRect.top + dp(8f)
        // "패널 보기"는 고정, 그 아래는 화면보다 길면 위아래로 스크롤
        button(c, RectF(x, y, x + w, y + bh), "« 패널 보기", color = SIDE_BTN, action = ::togglePanel)
        y += bh + gap
        sideRect.set(x - dp(4f), y, viewRect.right, viewRect.bottom - dp(4f))
        val top = y
        val dy = -sideScroll
        c.save()
        c.clipRect(sideRect)
        c.translate(0f, dy)
        fun btn(l: Float, r: Float, label: String, enabled: Boolean, color: Int, action: () -> Unit) =
            button(c, RectF(l, y, r, y + bh), label, enabled, color, clip = sideRect, dy = dy, action = action)
        fun row(label: String, enabled: Boolean, color: Int, action: () -> Unit) {
            btn(x, x + w, label, enabled, color, action)
            y += bh + gap
        }

        y = drawSideStatus(c, x, y, w) + gap
        // 확대/축소는 한 줄에 3개
        val bw3 = (w - gap * 2) / 3f
        btn(x, x + bw3, "+", cs < maxCs, SIDE_BTN, zoomIn)
        btn(x + bw3 + gap, x + bw3 * 2 + gap, "−", cs > minCs, SIDE_BTN, zoomOut)
        btn(x + (bw3 + gap) * 2, x + w, "전체", cs > minCs, SIDE_BTN, zoomFit)
        y += bh + gap
        val bw2 = (w - gap) / 2f
        btn(x, x + bw2, "${speed}x", true, if (speed > 1) BTN_GOLD else SIDE_BTN) { speed = speed % 3 + 1 }
        btn(x + bw2 + gap, x + w, "조합법", true, SIDE_BTN) { showRecipes = true }
        y += bh + gap * 2

        qualityAct()?.let { row(it.short, it.enabled, it.color, it.action) }
        game.selected?.let { sel -> for (a in selectionActs(sel)) row(a.short, a.enabled, a.color, a.action) }
        c.restore()

        // 스크롤 범위 보정 + 스크롤바
        val contentH = y - gap - top
        val maxScroll = max(0f, contentH - sideRect.height())
        sideScroll = sideScroll.coerceIn(0f, maxScroll)
        // 버튼이 없는 아래쪽 빈 곳을 끌면 보드가 움직이도록 터치 영역을 내용 높이까지만
        sideRect.bottom = min(sideRect.bottom, top + contentH)
        if (maxScroll > 0f) {
            val trackH = sideRect.height()
            val thumbH = max(dp(24f), trackH * trackH / contentH)
            val thumbY = sideRect.top + (trackH - thumbH) * (sideScroll / maxScroll)
            fill.color = 0x88FFFFFF.toInt()
            c.drawRoundRect(x - dp(4f), thumbY, x - dp(1.5f), thumbY + thumbH, dp(2f), dp(2f), fill)
        }
    }

    /** 품질 업그레이드 행동 (최대 레벨이면 null) */
    private fun qualityAct(): Act? {
        if (game.chanceLevel >= GemDB.maxChanceLevel) return null
        val cost = GemDB.chanceCost(game.chanceLevel)
        val ok = game.gold >= cost && game.state != GameState.GAME_OVER && game.state != GameState.VICTORY
        return Act("품질 업그레이드 (${cost}G)", "품질↑ ${cost}G", ok, BTN_GOLD) { game.upgradeChance() }
    }

    private fun cx(x: Float) = ox + (x + 0.5f) * cs
    private fun cy(y: Float) = oy + (y + 0.5f) * cs

    // =================================================================== 그리기

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG)
        buttons.clear()
        if (cs <= 1f) computeLayout()

        canvas.save()
        canvas.clipRect(viewRect)
        drawBoard(canvas)
        drawEntities(canvas)
        drawSelection(canvas)
        drawSideControls(canvas)
        drawBoardOverlay(canvas)
        canvas.restore()
        if (!panelHidden) drawPanel(canvas)
    }

    private fun drawBoard(c: Canvas) {
        fill.color = BOARD
        tmpRect.set(boardRect.left - cs * 0.3f, boardRect.top - cs * 0.3f, boardRect.right + cs * 0.3f, boardRect.bottom + cs * 0.3f)
        c.drawRoundRect(tmpRect, cs * 0.3f, cs * 0.3f, fill)

        val inset = cs * 0.04f
        for (x in 0 until Game.W) for (y in 0 until Game.H) {
            fill.color = if ((x + y) % 2 == 0) TILE_A else TILE_B
            c.drawRect(ox + x * cs + inset, oy + y * cs + inset, ox + (x + 1) * cs - inset, oy + (y + 1) * cs - inset, fill)
        }

        // 적의 경로 (흐르는 점선)
        if (routeVersion != game.pathVersion || routeCs != cs || routeOx != ox || routeOy != oy) {
            routeOx = ox
            routeOy = oy
            routePath.reset()
            game.groundPath.forEachIndexed { i, p ->
                if (i == 0) routePath.moveTo(cx(p.x.toFloat()), cy(p.y.toFloat()))
                else routePath.lineTo(cx(p.x.toFloat()), cy(p.y.toFloat()))
            }
            routeVersion = game.pathVersion
            routeCs = cs
        }
        pathPaint.strokeWidth = cs * 0.14f
        pathPaint.color = 0x55FFD24D
        pathPaint.pathEffect = null
        c.drawPath(routePath, pathPaint)
        pathPaint.strokeWidth = cs * 0.08f
        pathPaint.color = 0xCCFFE07A.toInt()
        pathPaint.pathEffect = DashPathEffect(floatArrayOf(cs * 0.25f, cs * 0.35f), -game.time * cs * 2f)
        c.drawPath(routePath, pathPaint)
        pathPaint.pathEffect = null

        // 체크포인트
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT_BOLD
        val wps = Game.WAYPOINTS
        wps.forEachIndexed { i, wp ->
            fill.color = when (i) {
                0 -> 0xFF33D45A.toInt()
                wps.size - 1 -> 0xFFFF4545.toInt()
                else -> 0xFF4D8CFF.toInt()
            }
            val px = cx(wp.x.toFloat())
            val py = cy(wp.y.toFloat())
            c.drawCircle(px, py, cs * 0.42f, fill)
            val label = when (i) { 0 -> "출발"; wps.size - 1 -> "도착"; else -> i.toString() }
            text.color = Color.WHITE
            text.textSize = if (label.length > 1) cs * 0.32f else cs * 0.5f
            c.drawText(label, px, py - (text.descent() + text.ascent()) / 2f, text)
        }
        text.typeface = Typeface.DEFAULT
    }

    private fun drawEntities(c: Canvas) {
        // 바위와 보석
        for (x in 0 until Game.W) for (y in 0 until Game.H) {
            val cell = Cell(x, y)
            when (game.cellType(cell)) {
                CellType.ROCK -> drawRock(c, x, y)
                CellType.GEM -> game.towerAt(cell)?.let { drawTower(c, it) }
                else -> {}
            }
        }

        // 적
        for (e in game.enemies) {
            val px = cx(e.x)
            val py = cy(e.y)
            val r = cs * if (e.boss) 0.42f else 0.25f
            var col = e.color
            if (e.slowed) col = blend(col, 0xFF66B3FF.toInt(), 0.55f)
            if (e.poisoned) col = blend(col, 0xFF4DFF33.toInt(), 0.45f)
            if (e.flying) {
                fill.color = 0x55000000
                c.drawOval(px - r * 0.8f, py + cs * 0.25f, px + r * 0.8f, py + cs * 0.4f, fill) // 그림자
                val bob = sin(game.time * 8f + e.progress) * cs * 0.05f
                fill.color = darken(col, 0.75f)
                c.drawOval(px - r * 1.9f, py - r * 0.35f + bob, px - r * 0.3f, py + r * 0.35f + bob, fill)
                c.drawOval(px + r * 0.3f, py - r * 0.35f + bob, px + r * 1.9f, py + r * 0.35f + bob, fill)
                fill.color = col
                c.drawCircle(px, py, r * 0.85f, fill)
            } else {
                fill.color = col
                c.drawCircle(px, py, r, fill)
                stroke.color = 0xAA000000.toInt()
                stroke.strokeWidth = cs * if (e.boss) 0.08f else 0.04f
                c.drawCircle(px, py, r, stroke)
            }
            if (e.stunned) {
                // 기절: 수정 고리
                stroke.color = 0xFFB3FFF2.toInt()
                stroke.strokeWidth = cs * 0.06f
                c.drawCircle(px, py, r * 1.3f, stroke)
            }
            // 체력바
            val w = r * 2.4f
            val by = py - r - cs * 0.18f
            fill.color = Color.BLACK
            c.drawRect(px - w / 2 - 1, by - 1, px + w / 2 + 1, by + cs * 0.1f + 1, fill)
            val f = (e.hp / e.maxHp).coerceIn(0f, 1f)
            fill.color = blend(0xFFFF3333.toInt(), 0xFF4DFF4D.toInt(), f)
            c.drawRect(px - w / 2, by, px - w / 2 + w * f, by + cs * 0.1f, fill)
        }

        // 연쇄 번개
        for (b in game.beams) {
            val a = (b.ttl / Beam.DURATION).coerceIn(0f, 1f)
            stroke.color = b.color
            stroke.alpha = (a * 255).toInt()
            stroke.strokeWidth = cs * 0.09f
            c.drawLine(cx(b.x1), cy(b.y1), cx(b.x2), cy(b.y2), stroke)
            stroke.color = Color.WHITE
            stroke.alpha = (a * 255).toInt()
            stroke.strokeWidth = cs * 0.035f
            c.drawLine(cx(b.x1), cy(b.y1), cx(b.x2), cy(b.y2), stroke)
        }
        stroke.alpha = 255

        // 투사체
        for (p in game.projectiles) {
            fill.color = if (p.crit) Color.WHITE else p.src.stats.color
            c.drawCircle(cx(p.x), cy(p.y), cs * if (p.crit) 0.13f else 0.09f, fill)
        }
    }

    private fun drawRock(c: Canvas, x: Int, y: Int) {
        val l = ox + x * cs
        val t = oy + y * cs
        fill.color = 0xFF5E5A55.toInt()
        tmpRect.set(l + cs * 0.1f, t + cs * 0.1f, l + cs * 0.9f, t + cs * 0.9f)
        c.drawRoundRect(tmpRect, cs * 0.15f, cs * 0.15f, fill)
        fill.color = 0xFF7A756F.toInt()
        tmpRect.set(l + cs * 0.18f, t + cs * 0.16f, l + cs * 0.75f, t + cs * 0.55f)
        c.drawRoundRect(tmpRect, cs * 0.12f, cs * 0.12f, fill)
    }

    private fun drawTower(c: Canvas, t: Tower) {
        val px = cx(t.cell.x.toFloat())
        val py = cy(t.cell.y.toFloat())
        val half = cs * 0.43f

        fill.color = 0xFF2E313B.toInt()
        tmpRect.set(px - half, py - half, px + half, py + half)
        c.drawRoundRect(tmpRect, cs * 0.12f, cs * 0.12f, fill)

        val sp = t.special
        if (sp == null) {
            val qi = t.quality.ordinal
            val r = cs * (0.19f + 0.045f * qi)
            val bob = if (t.kept) 0f else sin(game.time * 3f + t.cell.x) * cs * 0.04f
            val gy = py - cs * 0.04f + bob
            tmpPath.reset()
            tmpPath.moveTo(px, gy - r * 1.25f)
            tmpPath.lineTo(px + r, gy)
            tmpPath.lineTo(px, gy + r * 1.25f)
            tmpPath.lineTo(px - r, gy)
            tmpPath.close()
            fill.color = t.stats.color
            c.drawPath(tmpPath, fill)
            // 윗면 하이라이트
            tmpPath.reset()
            tmpPath.moveTo(px, gy - r * 1.25f)
            tmpPath.lineTo(px + r * 0.55f, gy - r * 0.3f)
            tmpPath.lineTo(px - r * 0.55f, gy - r * 0.3f)
            tmpPath.close()
            fill.color = blend(t.stats.color, Color.WHITE, 0.5f)
            c.drawPath(tmpPath, fill)
            // 품질 점
            fill.color = GOLD
            val n = qi + 1
            val gap = cs * 0.13f
            val startX = px - gap * (n - 1) / 2f
            for (i in 0 until n) c.drawCircle(startX + gap * i, py + cs * 0.33f, cs * 0.045f, fill)
        } else {
            fill.color = darken(t.stats.color, 0.4f)
            c.drawCircle(px, py, cs * 0.38f, fill)
            fill.color = t.stats.color
            c.drawCircle(px, py, cs * 0.25f, fill)
            fill.color = Color.WHITE
            for (i in 0 until 3) {
                val a = game.time * 2.5f + i * 2.0944f
                c.drawCircle(px + cos(a) * cs * 0.33f, py + sin(a) * cs * 0.33f, cs * 0.06f, fill)
            }
            fill.color = 0xFFFF8C33.toInt()
            for (i in 0 until t.specialLevel) c.drawCircle(px - cs * 0.3f + i * cs * 0.15f, py + cs * 0.36f, cs * 0.045f, fill)
        }

        if (!t.kept) {
            val combinable = game.state == GameState.BUILD_CHOOSE && game.countSame(t) >= 2
            stroke.color = if (combinable) 0xFF66FF77.toInt() else 0xCCFFFFFF.toInt()
            stroke.strokeWidth = cs * 0.05f
            val pulse = if (game.state == GameState.BUILD_CHOOSE) (sin(game.time * 5f) * 0.5f + 0.5f) * cs * 0.04f else 0f
            tmpRect.set(px - half - pulse, py - half - pulse, px + half + pulse, py + half + pulse)
            c.drawRoundRect(tmpRect, cs * 0.12f, cs * 0.12f, stroke)
        }
    }

    private fun drawSelection(c: Canvas) {
        val sel = game.selected ?: return
        val ct = game.cellType(sel)
        if (ct != CellType.GEM && ct != CellType.ROCK) {
            game.selected = null
            return
        }
        val px = cx(sel.x.toFloat())
        val py = cy(sel.y.toFloat())
        game.towerAt(sel)?.let { t ->
            val r = t.stats.range * cs
            c.save()
            c.clipRect(boardRect) // 사거리 원이 보드 밖으로 나가지 않게
            fill.color = 0x1AFFFFFF
            c.drawCircle(px, py, r, fill)
            stroke.color = 0x99FFFFFF.toInt()
            stroke.strokeWidth = dp(1.5f)
            c.drawCircle(px, py, r, stroke)
            c.restore()
        }
        stroke.color = GOLD
        stroke.strokeWidth = dp(2.5f)
        tmpRect.set(ox + sel.x * cs, oy + sel.y * cs, ox + (sel.x + 1) * cs, oy + (sel.y + 1) * cs)
        c.drawRoundRect(tmpRect, cs * 0.1f, cs * 0.1f, stroke)

        // 위치 바꾸기 중: 옮길 보석을 하늘색으로 깜빡임
        val mv = game.movingFrom ?: return
        val pulse = sin(game.time * 6f) * 0.5f + 0.5f
        fill.color = MOVE_COLOR
        fill.alpha = (60 + pulse * 70).toInt()
        tmpRect.set(ox + mv.x * cs, oy + mv.y * cs, ox + (mv.x + 1) * cs, oy + (mv.y + 1) * cs)
        c.drawRoundRect(tmpRect, cs * 0.1f, cs * 0.1f, fill)
        fill.alpha = 255
        stroke.color = MOVE_COLOR
        stroke.strokeWidth = dp(3f)
        c.drawRoundRect(tmpRect, cs * 0.1f, cs * 0.1f, stroke)
    }

    private fun drawBoardOverlay(c: Canvas) {
        // 배치 불가 칸 깜빡임
        val fc = flashCell
        if (fc != null && flashTime > 0f) {
            fill.color = Color.argb((flashTime / 0.6f * 160).toInt().coerceIn(0, 255), 255, 40, 40)
            c.drawRect(ox + fc.x * cs, oy + fc.y * cs, ox + (fc.x + 1) * cs, oy + (fc.y + 1) * cs, fill)
        }

        // 메시지
        if (game.messageTimer > 0f && game.message.isNotEmpty()) {
            text.textSize = dp(15f)
            text.typeface = Typeface.DEFAULT_BOLD
            text.textAlign = Paint.Align.CENTER
            val tw = text.measureText(game.message)
            val mx = viewRect.centerX()
            val my = viewRect.top + dp(22f)
            val alpha = (game.messageTimer.coerceAtMost(0.5f) / 0.5f)
            fill.color = Color.argb((alpha * 200).toInt(), 0, 0, 0)
            tmpRect.set(mx - tw / 2 - dp(12f), my - dp(17f), mx + tw / 2 + dp(12f), my + dp(9f))
            c.drawRoundRect(tmpRect, dp(10f), dp(10f), fill)
            text.color = Color.argb((alpha * 255).toInt(), 255, 240, 160)
            c.drawText(game.message, mx, my, text)
            text.typeface = Typeface.DEFAULT
        }

        // 게임 종료
        if (game.state == GameState.GAME_OVER || game.state == GameState.VICTORY) {
            fill.color = 0xAA000000.toInt()
            c.drawRect(viewRect, fill)
            text.textAlign = Paint.Align.CENTER
            text.typeface = Typeface.DEFAULT_BOLD
            text.textSize = dp(44f)
            text.color = if (game.state == GameState.VICTORY) GOLD else Color.WHITE
            c.drawText(if (game.state == GameState.VICTORY) "승리!" else "게임 오버", viewRect.centerX(), viewRect.centerY() - dp(10f), text)
            text.typeface = Typeface.DEFAULT
            val bw = dp(160f)
            val r = RectF(viewRect.centerX() - bw / 2, viewRect.centerY() + dp(16f), viewRect.centerX() + bw / 2, viewRect.centerY() + dp(60f))
            button(c, r, "다시 하기", color = BTN_GREEN) { game.newGame(); scrollY = 0f }
        }

        if (showRecipes) drawRecipeWindow(c)
    }

    private fun drawRecipeWindow(c: Canvas) {
        fill.color = 0xF0101318.toInt()
        c.drawRoundRect(viewRect, dp(12f), dp(12f), fill)
        val x = viewRect.left + dp(20f)
        val w = viewRect.width() - dp(40f)
        val top = viewRect.top + dp(12f)
        val bottom = viewRect.bottom - dp(12f)

        c.save()
        c.clipRect(viewRect.left, top, viewRect.right, bottom)
        c.translate(0f, -recipeScroll)
        var y = top + dp(10f)
        y += drawWrapped(c, "특수 조합법 (${GemDB.recipes.size}종)", x, y, w, dp(19f), GOLD, bold = true) + dp(6f)
        y += drawWrapped(c, "재료 보석이 필드에 모두 있고 그중 하나가 이번 라운드 보석이면, 선택 단계에서 그 보석을 눌러 합성할 수 있습니다. 이미 남긴 재료 보석은 바위가 됩니다.", x, y, w, dp(12f), TEXT_DIM) + dp(4f)
        y += drawWrapped(c, "초록색 = 지금 필드에 있는 재료", x, y, w, dp(12f), OWNED, bold = true) + dp(10f)

        for (tier in 0..2) {
            val list = GemDB.recipes.filter { it.tier == tier }
            if (list.isEmpty()) continue
            val tierDesc = when (tier) { 0 -> "일반 보석이 핵심 재료"; 1 -> "무결점 보석이 핵심 재료"; else -> "완벽한 보석이 핵심 재료" }
            y += drawWrapped(c, "━ ${GemDB.tierNames[tier]} · $tierDesc", x, y, w, dp(14f), Color.WHITE, bold = true) + dp(8f)
            for (r in list) {
                val s = GemDB.special(r.result)
                val owned = r.parts.map { game.hasGemOnField(it) }
                val ready = owned.all { it }
                y += drawWrapped(c, "◆ ${r.result.label}" + if (ready) "  ✓ 재료 모두 있음" else "", x, y, w, dp(15f), s.color, bold = true) + dp(2f)

                val sb = SpannableStringBuilder()
                r.parts.forEachIndexed { i, p ->
                    if (i > 0) sb.append(" + ")
                    val start = sb.length
                    sb.append(p.toString())
                    sb.setSpan(ForegroundColorSpan(if (owned[i]) OWNED else MISSING), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val key = r.result.name + owned.joinToString("") { if (it) "1" else "0" }
                y += drawWrapped(c, sb, x + dp(16f), y, w - dp(16f), dp(13f), MISSING, key = key) + dp(1f)
                y += drawWrapped(c, s.desc, x + dp(16f), y, w - dp(16f), dp(12f), TEXT_DIM) + dp(10f)
            }
            y += dp(4f)
        }
        y += drawWrapped(c, "위아래로 밀어서 스크롤 · 누르면 닫힘", x, y, w, dp(12f), 0xFF8A93A3.toInt())
        c.restore()

        val contentH = y + dp(10f) - top
        val maxScroll = max(0f, contentH - (bottom - top))
        recipeScroll = recipeScroll.coerceIn(0f, maxScroll)
        if (maxScroll > 0f) {
            val trackH = bottom - top
            val thumbH = max(dp(24f), trackH * trackH / contentH)
            val thumbY = top + (trackH - thumbH) * (recipeScroll / maxScroll)
            fill.color = 0x55FFFFFF
            c.drawRoundRect(viewRect.right - dp(7f), thumbY, viewRect.right - dp(3f), thumbY + thumbH, dp(2f), dp(2f), fill)
        }
    }

    // =================================================================== 패널

    private fun drawPanel(c: Canvas) {
        fill.color = PANEL
        c.drawRoundRect(panelRect, dp(12f), dp(12f), fill)

        val pad = dp(12f)
        val x = panelRect.left + pad
        val w = panelRect.width() - pad * 2
        var y = panelRect.top + pad

        y += drawWrapped(c, "보석 타워 디펜스", x, y, w, dp(17f), GOLD, bold = true) + dp(4f)
        y += drawWrapped(c, "웨이브 ${game.wave} / ${Game.MAX_WAVE}", x, y, w, dp(14f), Color.WHITE, bold = true) + dp(2f)
        y += drawWrapped(c, "생명 ${game.lives}    골드 ${game.gold}G", x, y, w, dp(14f), 0xFFFFE9A8.toInt(), bold = true) + dp(8f)

        // 하단 고정 버튼 영역
        val bh = dp(38f)
        val gap = dp(6f)
        val footerTop = panelRect.bottom - pad - bh * 2 - gap
        val bw3 = (w - gap * 2) / 3f
        for (i in 1..3) {
            val l = x + (i - 1) * (bw3 + gap)
            button(c, RectF(l, footerTop, l + bw3, footerTop + bh), "${i}x", color = if (speed == i) BTN_GOLD else BTN) { speed = i }
        }
        val bw2 = (w - gap) / 2f
        val row2 = footerTop + bh + gap
        button(c, RectF(x, row2, x + bw2, row2 + bh), if (showRecipes) "조합법 닫기" else "조합법") { showRecipes = !showRecipes }
        val armed = System.currentTimeMillis() < newGameArmedUntil
        button(c, RectF(x + bw2 + gap, row2, x + w, row2 + bh), if (armed) "정말 새 게임?" else "새 게임", color = if (armed) BTN_RED else BTN) {
            if (System.currentTimeMillis() < newGameArmedUntil) {
                newGameArmedUntil = 0L
                game.newGame()
                scrollY = 0f
            } else newGameArmedUntil = System.currentTimeMillis() + 3000L
        }

        // 스크롤 영역
        scrollRect.set(panelRect.left, y, panelRect.right, footerTop - gap)
        contentHeight = drawPanelBody(c, x, w)
        val maxScroll = max(0f, contentHeight - scrollRect.height())
        scrollY = scrollY.coerceIn(0f, maxScroll)
        if (maxScroll > 0f) {
            // 스크롤바
            val trackH = scrollRect.height()
            val thumbH = max(dp(24f), trackH * scrollRect.height() / contentHeight)
            val thumbY = scrollRect.top + (trackH - thumbH) * (scrollY / maxScroll)
            fill.color = 0x55FFFFFF
            c.drawRoundRect(panelRect.right - dp(5f), thumbY, panelRect.right - dp(2f), thumbY + thumbH, dp(2f), dp(2f), fill)
        }
    }

    /** 스크롤되는 패널 본문. @return 콘텐츠 높이 */
    private fun drawPanelBody(c: Canvas, x: Float, w: Float): Float {
        c.save()
        c.clipRect(scrollRect)
        c.translate(0f, scrollRect.top - scrollY)
        var y = 0f
        val bh = dp(38f)
        val gap = dp(6f)

        // 선택한 보석의 행동 버튼이 가장 먼저 보이도록 맨 위에 배치
        if (game.selected != null) {
            y = drawSelectionInfo(c, x, y, w) + dp(4f)
            y = divider(c, x, y, w)
        }

        y += drawWrapped(c, stateText(), x, y, w, dp(13f), TEXT_DIM) + dp(10f)
        y = divider(c, x, y, w)

        // 품질 확률
        y += drawWrapped(c, "보석 품질 레벨 ${game.chanceLevel}", x, y, w, dp(14f), Color.WHITE, bold = true) + dp(2f)
        y += drawWrapped(c, GemDB.chanceText(game.chanceLevel), x, y, w, dp(12f), TEXT_DIM) + dp(6f)
        qualityAct()?.let { a ->
            button(c, RectF(x, y, x + w, y + bh), a.label, a.enabled, a.color, scrolled = true, action = a.action)
            y += bh + gap
        }
        if (game.selected == null) {
            y = divider(c, x, y + dp(4f), w)
            y += drawWrapped(c, "보석이나 바위를 눌러 정보를 확인하세요.", x, y, w, dp(12f), 0xFF8A93A3.toInt())
        }

        c.restore()
        return y + dp(8f)
    }

    private fun drawSelectionInfo(c: Canvas, x: Float, y0: Float, w: Float): Float {
        var y = y0
        val bh = dp(38f)
        val gap = dp(6f)
        val sel = game.selected ?: return y
        val rock = game.cellType(sel) == CellType.ROCK
        val t = if (rock) null else game.towerAt(sel) ?: return y

        if (t == null) {
            y += drawWrapped(c, "바위", x, y, w, dp(15f), Color.WHITE, bold = true) + dp(2f)
            y += drawWrapped(c, "적의 길을 막는 장애물입니다.", x, y, w, dp(12f), TEXT_DIM) + dp(6f)
        } else {
            y += drawWrapped(c, t.stats.name, x, y, w, dp(15f), t.stats.color, bold = true) + dp(6f)
        }
        for (a in selectionActs(sel)) {
            button(c, RectF(x, y, x + w, y + bh), a.label, a.enabled, a.color, scrolled = true, action = a.action)
            y += bh + gap
        }
        if (t == null) return y - gap
        if (game.movingFrom == sel) {
            y += drawWrapped(c, "옮길 칸을 누르세요. (보석당 1번만)\n빈 칸 → 이동 · 보석/바위 → 자리 교환", x, y, w, dp(12f), MOVE_COLOR, bold = true) + dp(6f)
        }

        for (line in GemDB.describe(t.stats)) y += drawWrapped(c, line, x, y, w, dp(12f), TEXT_DIM)
        if (t.kept) y += drawWrapped(c, "처치 ${t.kills} · 누적 피해 ${t.totalDamage.toInt()}", x, y, w, dp(12f), 0xFFA8E6B0.toInt())
        if (t.special == null) {
            val uses = GemDB.recipesUsing(GemKey(t.type, t.quality))
            if (uses.isNotEmpty()) {
                y += dp(4f)
                y += drawWrapped(c, "재료로 쓰임: " + uses.joinToString(", ") { it.result.label }, x, y, w, dp(12f), 0xFFD9B3FF.toInt())
            }
        }
        return y
    }

    /** 행동 버튼: label = 패널용, short = 패널을 숨겼을 때 보드 옆 작은 버튼용 */
    private class Act(val label: String, val short: String, val enabled: Boolean, val color: Int, val action: () -> Unit)

    /** 선택한 보석/바위로 지금 할 수 있는 행동 (패널과 간략 버튼이 같이 사용) */
    private fun selectionActs(sel: Cell): List<Act> {
        val out = ArrayList<Act>()
        if (game.cellType(sel) == CellType.ROCK) {
            val cost = Game.ROCK_REMOVE_COST
            out += Act("바위 제거 (${cost}G)", "바위 제거 ${cost}G", game.isBuildState && game.gold >= cost, BTN) { game.removeRock(sel) }
            return out
        }
        val t = game.towerAt(sel) ?: return out

        if (t.kept && t.special != null && t.specialLevel < GemDB.MAX_SPECIAL_LEVEL) {
            val cost = GemDB.specialUpgradeCost(t.specialLevel)
            out += Act("특수 보석 강화 (${cost}G)", "강화 ${cost}G", game.isBuildState && game.gold >= cost, BTN_PURPLE) { game.upgradeSpecial(t) }
        }
        if (game.state == GameState.BUILD_CHOOSE && t in game.roundGems) {
            out += Act("이 보석 남기기", "남기기", true, BTN_GREEN) { game.keep(t) }
            for (levels in 1..2) {
                if (!game.canCombine(t, levels)) continue
                val q = GemQuality.entries[t.quality.ordinal + levels]
                val name = if (levels == 1) "합성" else "대합성"
                out += Act("$name → ${q.label} ${t.type.label}", "$name → ${q.label}", true, BTN_GOLD) { game.combine(t, levels) }
            }
            for (r in GemDB.recipes) {
                if (game.findRecipeParts(t, r) == null) continue
                out += Act("특수 합성 → ${r.result.label}", r.result.label, true, BTN_PURPLE) { game.makeSpecial(t, r) }
            }
        }
        if (game.isBuildState && t.special != null) {
            out += when {
                game.movingFrom == sel -> Act("이동 취소", "이동 취소", true, BTN_RED) { game.cancelMove() }
                t.moved -> Act("위치 바꾸기 사용함", "이동 사용함", false, BTN) {}
                else -> Act("위치 바꾸기 (${Game.MOVE_COST}G)", "이동 ${Game.MOVE_COST}G", game.canStartMove(sel), BTN_TEAL) { game.startMove(sel) }
            }
        }
        return out
    }

    /** 패널을 숨겼을 때 간략 버튼 열에 보이는 웨이브/생명/골드 정보. @return 다음 y */
    private fun drawSideStatus(c: Canvas, x: Float, y0: Float, w: Float): Float {
        class Line(val s: String, val size: Float, val color: Int, val bold: Boolean)
        val lines = ArrayList<Line>()
        lines += Line("웨이브 ${game.wave}/${Game.MAX_WAVE}", dp(14f), Color.WHITE, true)
        lines += Line("생명 ${game.lives} · ${game.gold}G", dp(13f), 0xFFFFE9A8.toInt(), true)
        val hint = when {
            game.movingFrom != null -> "옮길 칸을 누르세요"
            game.state == GameState.BUILD_PLACE -> "보석 배치 ${game.roundGems.size}/${Game.GEMS_PER_ROUND}\n다음: ${game.waveInfo(game.wave).label}"
            game.state == GameState.BUILD_CHOOSE -> "남길 보석 선택\n다음: ${game.waveInfo(game.wave).label}"
            game.state == GameState.WAVE -> "전투 · ${game.currentWave.label}\n남은 적 ${game.remainingEnemies}"
            game.state == GameState.VICTORY -> "승리!"
            else -> "패배"
        }
        lines += Line(hint, dp(12f), if (game.movingFrom != null) MOVE_COLOR else TEXT_DIM, game.movingFrom != null)
        game.selected?.let { sel ->
            val t = game.towerAt(sel)
            if (t != null) lines += Line(t.stats.name, dp(12f), t.stats.color, true)
            else if (game.cellType(sel) == CellType.ROCK) lines += Line("바위", dp(12f), Color.WHITE, true)
        }

        val pad = dp(8f)
        val tw = w - pad * 2
        val layouts = lines.map { wrappedLayout(it.s, tw, it.size, it.bold) }
        val h = layouts.sumOf { it.height } + pad * 2 + dp(2f) * (lines.size - 1)
        tmpRect.set(x, y0, x + w, y0 + h)
        fill.color = 0xCC0E1117.toInt()
        c.drawRoundRect(tmpRect, dp(8f), dp(8f), fill)
        var y = y0 + pad
        lines.forEach { l -> y += drawWrapped(c, l.s, x + pad, y, tw, l.size, l.color, l.bold) + dp(2f) }
        return y0 + h
    }

    private fun stateText(): String = when (game.state) {
        GameState.BUILD_PLACE ->
            "[건설] 보석 배치 ${game.roundGems.size}/${Game.GEMS_PER_ROUND}\n빈 칸을 눌러 무작위 보석을 놓으세요. 보석과 바위로 미로를 만들어 적의 길을 늘리세요.\n다음 웨이브: ${game.waveInfo(game.wave).label}"
        GameState.BUILD_CHOOSE ->
            "[선택] 테두리가 빛나는 이번 라운드 보석 중 하나를 눌러 남기세요. 나머지는 바위가 됩니다.\n같은 보석 2개 → 합성, 4개 → 대합성 (초록 테두리)"
        GameState.WAVE ->
            "[전투] 웨이브 ${game.wave} (${game.currentWave.label})\n남은 적 ${game.remainingEnemies} · 경로 ${game.groundPath.size}칸"
        GameState.GAME_OVER -> "웨이브 ${game.wave}에서 패배했습니다."
        GameState.VICTORY -> "축하합니다! 모든 웨이브를 클리어했습니다!"
    }

    private fun divider(c: Canvas, x: Float, y: Float, w: Float): Float {
        fill.color = 0x22FFFFFF
        c.drawRect(x, y, x + w, y + dp(1f), fill)
        return y + dp(10f)
    }

    // =================================================================== UI 도우미

    /**
     * 버튼을 그리고 터치 영역을 등록. scrolled=true면 패널 스크롤 영역 안의 좌표.
     * clip/dy: 다른 스크롤 영역 안의 버튼 (화면 좌표 = r + dy, clip 밖은 터치 안 됨).
     */
    private fun button(
        c: Canvas, r: RectF, label: String, enabled: Boolean = true, color: Int = BTN,
        scrolled: Boolean = false, clip: RectF? = null, dy: Float = 0f, action: () -> Unit,
    ) {
        fill.color = if (enabled) color else BTN_DISABLED
        c.drawRoundRect(r, dp(8f), dp(8f), fill)
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = dp(14f)
        val maxW = r.width() - dp(10f)
        val tw = text.measureText(label)
        if (tw > maxW) text.textSize *= maxW / tw
        text.color = if (enabled) Color.WHITE else 0xFF6A707C.toInt()
        c.drawText(label, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2f, text)
        text.typeface = Typeface.DEFAULT

        val hit = RectF(r)
        if (scrolled) {
            hit.offset(0f, scrollRect.top - scrollY)
            if (!hit.intersect(scrollRect)) return
        } else if (clip != null) {
            hit.offset(0f, dy)
            if (!hit.intersect(clip)) return
        }
        buttons.add(Button(hit, enabled, action))
    }

    /** 줄바꿈 텍스트 (색 span 지원). @return 높이 */
    private fun drawWrapped(
        c: Canvas, s: CharSequence, x: Float, y: Float, w: Float, size: Float, color: Int,
        bold: Boolean = false, key: String = s.toString(),
    ): Float {
        if (w <= 0f) return 0f
        val layout = wrappedLayout(s, w, size, bold, key)
        layout.paint.color = color
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return layout.height.toFloat()
    }

    /** 줄바꿈 레이아웃 (캐시). 그리기 전에 높이를 미리 알아야 할 때도 사용. */
    private fun wrappedLayout(s: CharSequence, w: Float, size: Float, bold: Boolean, key: String = s.toString()): StaticLayout {
        val cacheKey = "$size|$bold|${w.toInt()}|$key"
        return layoutCache.getOrPut(cacheKey) {
            if (layoutCache.size > 300) layoutCache.clear()
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            StaticLayout.Builder.obtain(s, 0, s.length, p, w.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .build()
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val u = 1f - t
        return Color.argb(
            255,
            (Color.red(a) * u + Color.red(b) * t).toInt(),
            (Color.green(a) * u + Color.green(b) * t).toInt(),
            (Color.blue(a) * u + Color.blue(b) * t).toInt(),
        )
    }

    private fun darken(a: Int, f: Float) =
        Color.argb(255, (Color.red(a) * f).toInt(), (Color.green(a) * f).toInt(), (Color.blue(a) * f).toInt())

    // =================================================================== 터치

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                downScroll = scrollY
                dragging = false
                downInScroll = !showRecipes && scrollRect.contains(e.x, e.y)
                downInRecipe = showRecipes && viewRect.contains(e.x, e.y)
                downInSide = sideRect.contains(e.x, e.y)
                downSideScroll = sideScroll
                downInBoard = !showRecipes && !downInSide && viewRect.contains(e.x, e.y)
                downRecipeScroll = recipeScroll
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // 손가락 수가 바뀌면 기준점을 다시 잡아 보드가 튀지 않게
                dragging = true
                val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
                focal(e, skip)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(e.y - downY) > touchSlop || abs(e.x - downX) > touchSlop)) dragging = true
                // 범위는 그릴 때 보정
                if (dragging && downInScroll) scrollY = downScroll - (e.y - downY)
                if (dragging && downInRecipe) recipeScroll = downRecipeScroll - (e.y - downY)
                if (dragging && downInSide) sideScroll = downSideScroll - (e.y - downY)
                val px = lastX
                val py = lastY
                focal(e, -1)
                if (dragging && downInBoard) {
                    ox += lastX - px
                    oy += lastY - py
                    clampPan()
                }
            }
            MotionEvent.ACTION_UP -> if (!dragging) handleTap(e.x, e.y)
        }
        return true
    }

    /** 손가락들의 평균 위치를 lastX/lastY에 저장 (skip 번째 포인터 제외) */
    private fun focal(e: MotionEvent, skip: Int) {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sx += e.getX(i); sy += e.getY(i); n++
        }
        if (n > 0) { lastX = sx / n; lastY = sy / n }
    }

    private fun handleTap(x: Float, y: Float) {
        if (showRecipes) {
            // 조합법 창이 떠 있으면 버튼 외의 터치는 창을 닫음
            val b = buttons.lastOrNull { it.rect.contains(x, y) }
            if (b != null && b.enabled && !viewRect.contains(x, y)) b.action() else showRecipes = false
            return
        }
        for (i in buttons.indices.reversed()) {
            val b = buttons[i]
            if (b.rect.contains(x, y)) {
                if (b.enabled) b.action()
                return
            }
        }
        if (viewRect.contains(x, y) && boardRect.contains(x, y)) {
            val cell = Cell(((x - ox) / cs).toInt(), ((y - oy) / cs).toInt())
            if (!game.inBounds(cell)) return
            if (game.state == GameState.GAME_OVER || game.state == GameState.VICTORY) return
            if (!game.tapCell(cell)) {
                flashCell = cell
                flashTime = 0.6f
            }
            if (game.selected != null) scrollY = 0f
        } else if (!panelRect.contains(x, y)) {
            game.cancelMove()
            game.selected = null
        }
    }
}
