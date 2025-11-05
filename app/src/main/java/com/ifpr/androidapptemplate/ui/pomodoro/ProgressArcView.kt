package com.ifpr.androidapptemplate.ui.pomodoro

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ifpr.androidapptemplate.R

class ProgressArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint da trilha (barra constante 360° ao fundo)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
        alpha = 60 // levemente translúcido
        strokeWidth = dp(8f)
    }

    // Paint do progresso por cima da trilha
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.pomotiva_secondary) // cor de destaque
        strokeWidth = dp(8f)
    }

    private val arcBounds = RectF()

    // Progresso em [0f..1f]
    private var progress: Float = 0f

    // Ângulo inicial em graus (12h = -90)
    private var startAngleDeg: Float = -90f

    private var animator: ValueAnimator? = null

    fun setStrokeWidthDp(widthDp: Float) {
        val px = dp(widthDp)
        arcPaint.strokeWidth = px
        trackPaint.strokeWidth = px
        invalidate()
    }

    fun setColorRes(colorRes: Int) {
        arcPaint.color = ContextCompat.getColor(context, colorRes)
        invalidate()
    }

    fun setProgressFraction(fraction: Float, animate: Boolean = true) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (animate) animateTo(clamped) else {
            progress = clamped
            invalidate()
        }
    }

    fun setProgressByMillis(totalMillis: Long, remainingMillis: Long, animate: Boolean = true) {
        val total = if (totalMillis <= 0L) 1L else totalMillis
        val done = (total - remainingMillis).coerceAtLeast(0L).coerceAtMost(total)
        val fraction = done.toFloat() / total.toFloat()
        setProgressFraction(fraction, animate)
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 400 // ms, animação suave
            addUpdateListener { va ->
                progress = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = arcPaint.strokeWidth / 2f
        val size = minOf(w, h).toFloat()
        arcBounds.set(inset, inset, size - inset, size - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // trilha completa ao fundo
        canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)
        // arco de progresso por cima
        val sweep = 360f * progress
        canvas.drawArc(arcBounds, startAngleDeg, sweep, false, arcPaint)
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}

