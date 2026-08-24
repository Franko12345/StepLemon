package com.stepwatch.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * Triple-ring donut chart for the Today screen. Three concentric rings:
 *  - outer (largest value): Stretch goal
 *  - middle: Daily goal
 *  - inner (smallest): Minimum goal
 * Each ring fills proportionally to (steps / goal), clamped to [0, 1].
 * Center shows big step count and "steps today".
 */
class TripleDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3322C55E")  // very dim lime
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    var steps: Long = 0L
        set(v) { field = v; invalidate() }

    var goalMin: Int = 3000
    var goalDaily: Int = 10000
    var goalStretch: Int = 15000

    private val ringColors = intArrayOf(
        Color.parseColor("#9CCC65"),   // min: lime
        Color.parseColor("#D4E157"),   // daily: lemon yellow
        Color.parseColor("#F472B6")    // stretch: pink
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val padding = 12f
        val stroke = 22f
        val radii = floatArrayOf(
            (Math.min(width, height) / 2f) - padding - stroke * 0.5f,
            (Math.min(width, height) / 2f) - padding - stroke * 1.7f,
            (Math.min(width, height) / 2f) - padding - stroke * 2.9f
        )

        val goals = intArrayOf(goalMin, goalDaily, goalStretch)
        val rect = RectF()
        for (i in 0..2) {
            val r = radii[i]
            rect.set(cx - r, cy - r, cx + r, cy + r)
            trackPaint.color = Color.parseColor("#33FFFFFF")
            canvas.drawArc(rect, -90f, 360f, false, trackPaint)

            val ratio = if (goals[i] > 0) (steps.toFloat() / goals[i]).coerceIn(0f, 1f) else 0f
            progressPaint.color = ringColors[i]
            progressPaint.strokeWidth = stroke
            canvas.drawArc(rect, -90f, 360f * ratio, false, progressPaint)
        }
    }
}