package com.stepwatch.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Simple weekly bar chart. Each bar = steps for a day, capped by the largest
 * day's value (so the tallest bar fills the area).
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val label: String, val value: Float)

    var bars: List<Bar> = emptyList()
        set(v) { field = v; invalidate() }

    var targetLine: Float? = null
        set(v) { field = v; invalidate() }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F472B6")
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CCC65")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8FA398")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val padTop = 30f
        val padBottom = 70f
        val padHoriz = 40f
        val chartH = height - padTop - padBottom
        val chartW = width - 2 * padHoriz

        val maxVal = max(bars.maxOf { it.value }, 1f)
        val target = targetLine ?: 0f
        val yScale = chartH / max(maxVal, target)

        // Target dashed line
        if (target > 0) {
            val y = padTop + chartH - target * yScale
            canvas.drawLine(padHoriz, y, width - padHoriz, y, targetPaint)
        }

        val n = bars.size
        val slot = chartW / n
        val barWidth = slot * 0.5f
        for (i in 0 until n) {
            val v = bars[i].value
            val h = v * yScale
            val cx = padHoriz + slot * i + slot / 2f
            val left = cx - barWidth / 2f
            val right = cx + barWidth / 2f
            val top = padTop + chartH - h
            val bottom = padTop + chartH
            // Color: lime if hit target, pink otherwise
            barPaint.color = if (v >= target && target > 0) Color.parseColor("#9CCC65")
                             else Color.parseColor("#F472B6")
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, 8f, 8f, barPaint)
            // Label
            canvas.drawText(bars[i].label, cx, bottom + 40f, labelPaint)
        }
    }
}