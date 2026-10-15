package com.ntuli.apexledger.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Simple donut chart, matching the Ticket Completion donut on the website's Reports page. */
class DonutChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var values: List<Float> = emptyList()
    private var colors: List<Int> = emptyList()

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = androidx.core.content.ContextCompat.getColor(context, com.ntuli.apexledger.R.color.gray_200)
    }
    private val rect = RectF()
    private var centerBig = ""
    private var centerSmall = ""
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF64748B.toInt(); textAlign = Paint.Align.CENTER
    }

    fun setCenterText(big: String, small: String) {
        centerBig = big
        centerSmall = small
        invalidate()
    }

    fun setData(values: List<Float>, colors: List<Int>) {
        this.values = values
        this.colors = colors
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (180 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = minOf(width, height) * 0.22f
        arcPaint.strokeWidth = stroke
        emptyPaint.strokeWidth = stroke
        val half = stroke / 2f
        val diameter = minOf(width, height).toFloat()
        val left = (width - diameter) / 2f + half
        val top = (height - diameter) / 2f + half
        rect.set(left, top, left + diameter - stroke, top + diameter - stroke)

        val total = values.sum()
        if (total <= 0f) {
            canvas.drawArc(rect, 0f, 360f, false, emptyPaint)
            drawCenter(canvas)
            return
        }
        var start = -90f
        for (i in values.indices) {
            val sweep = values[i] / total * 360f
            arcPaint.color = colors[i]
            canvas.drawArc(rect, start, sweep, false, arcPaint)
            start += sweep
        }
        drawCenter(canvas)
    }

    private fun drawCenter(canvas: Canvas) {
        if (centerBig.isEmpty()) return
        bigPaint.color = androidx.core.content.ContextCompat.getColor(context, com.ntuli.apexledger.R.color.navy)
        smallPaint.color = androidx.core.content.ContextCompat.getColor(context, com.ntuli.apexledger.R.color.gray_500)
        val d = resources.displayMetrics.density
        bigPaint.textSize = 26f * d
        smallPaint.textSize = 12f * d
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText(centerBig, cx, cy + 4f * d, bigPaint)
        canvas.drawText(centerSmall, cx, cy + 22f * d, smallPaint)
    }
}
