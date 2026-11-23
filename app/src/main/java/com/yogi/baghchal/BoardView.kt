package com.yogi.baghchal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

/** The wooden board, the pieces and the hints. Moves glide; captured goats fade away. */
class BoardView(ctx: Context) : View(ctx) {
    var board = Board()
    var selected = -1
    var hints: List<Move> = emptyList()
    var lastMove: Move? = null
    var onPoint: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val wood = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5A3A1E.toInt(); strokeCap = Paint.Cap.ROUND }
    private val node = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5A3A1E.toInt() }
    private val piece = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    // animation of the last move
    private var animFrom = -1
    private var animTo = -1
    private var animOver = -1
    private var t = 1f

    override fun onMeasure(w: Int, h: Int) {
        val size = minOf(MeasureSpec.getSize(w), if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else MeasureSpec.getSize(h))
        setMeasuredDimension(size, size)
    }

    private fun gap() = width / 5.6f
    private fun ox() = (width - gap() * 4) / 2
    private fun px(p: Int) = ox() + (p % 5) * gap()
    private fun py(p: Int) = ox() + (p / 5) * gap()

    fun animate(m: Move) {
        animFrom = m.from
        animTo = m.to
        animOver = m.over
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (m.from < 0) 260 else 320
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        // board: warm wood with a soft vignette
        wood.shader = LinearGradient(0f, 0f, w, w, 0xFFE2B57A.toInt(), 0xFFC58B4E.toInt(), Shader.TileMode.CLAMP)
        rect.set(0f, 0f, w, w)
        c.drawRoundRect(rect, 22 * d, 22 * d, wood)
        wood.shader = RadialGradient(w / 2, w / 2, w * 0.75f, 0x00000000, 0x33000000, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 22 * d, 22 * d, wood)
        // grain
        line.strokeWidth = 1f
        line.color = 0x18000000
        var y = 6 * d
        while (y < w) { c.drawLine(0f, y, w, y + 8 * d, line); y += 11 * d }
        // lines
        line.color = 0xFF5A3A1E.toInt()
        line.strokeWidth = 3.2f * d
        for (p in 0 until 25) for (q in Rules.adj[p]) if (q > p) c.drawLine(px(p), py(p), px(q), py(q), line)
        for (p in 0 until 25) c.drawCircle(px(p), py(p), 5 * d, node)
        // last move trail
        lastMove?.let { m ->
            if (m.from >= 0) {
                hint.color = 0x55FFFFFF
                hint.strokeWidth = 6 * d
                hint.strokeCap = Paint.Cap.ROUND
                c.drawLine(px(m.from), py(m.from), px(m.to), py(m.to), hint)
            }
        }
        // hints: where the selected piece (or a new goat) can go
        for (m in hints) {
            hint.color = if (m.over >= 0) 0xAAE53935.toInt() else 0x8833AA55.toInt()
            c.drawCircle(px(m.to), py(m.to), (if (m.over >= 0) 16 else 12) * d, hint)
        }
        val r = gap() * 0.36f
        emoji.textSize = r * 1.25f
        for (p in 0 until 25) {
            val v = board.cells[p]
            if (v == Rules.EMPTY && !(p == animOver && t < 1f)) continue
            var x = px(p)
            var yy = py(p)
            var alpha = 255
            var kind = v
            if (p == animTo && t < 1f) {
                if (animFrom >= 0) {
                    x = px(animFrom) + (px(animTo) - px(animFrom)) * t
                    yy = py(animFrom) + (py(animTo) - py(animFrom)) * t
                } else {
                    alpha = (255 * t).toInt()
                }
            }
            if (p == animOver && t < 1f) { kind = Rules.GOAT; alpha = (255 * (1 - t)).toInt() }
            drawPiece(c, x, yy, r, kind, alpha, p == selected)
        }
    }

    private fun drawPiece(c: Canvas, x: Float, y: Float, r: Float, kind: Int, alpha: Int, sel: Boolean) {
        // shadow
        piece.shader = null
        piece.color = Color.argb(alpha / 3, 0, 0, 0)
        c.drawCircle(x + 2 * d, y + 3 * d, r, piece)
        // disc
        val (top, bottom) = if (kind == Rules.TIGER) 0xFFFFB547.toInt() to 0xFFE0661A.toInt() else 0xFFFFFFFF.toInt() to 0xFFD8D2C4.toInt()
        piece.shader = LinearGradient(x, y - r, x, y + r, top, bottom, Shader.TileMode.CLAMP)
        piece.alpha = alpha
        c.drawCircle(x, y, r, piece)
        piece.shader = null
        piece.style = Paint.Style.STROKE
        piece.strokeWidth = (if (sel) 4 else 2) * d
        piece.color = if (sel) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha, 90, 58, 30)
        c.drawCircle(x, y, r, piece)
        piece.style = Paint.Style.FILL
        emoji.alpha = alpha
        c.drawText(if (kind == Rules.TIGER) "🐯" else "🐐", x, y + emoji.textSize * 0.36f, emoji)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            var best = -1
            var bd = gap() * 0.5f
            for (p in 0 until 25) {
                val dist = hypot(e.x - px(p), e.y - py(p))
                if (dist < bd) { bd = dist; best = p }
            }
            if (best >= 0) onPoint?.invoke(best)
        }
        return true
    }
}
