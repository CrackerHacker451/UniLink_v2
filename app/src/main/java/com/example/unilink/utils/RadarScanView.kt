package com.example.unilink.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import com.example.unilink.R
import kotlin.math.cos
import kotlin.math.sin

class RadarScanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var rippleCount = 3
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val accentColor: Int by lazy {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.accentColor, typedValue, true)
        typedValue.data
    }

    // Static radar blips (angle in rad, radius factor)
    private val blips = listOf(
        Pair(0.75f, 0.35f),
        Pair(2.1f, 0.65f),
        Pair(3.8f, 0.50f),
        Pair(5.2f, 0.80f),
        Pair(1.4f, 0.72f)
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    fun startAnimation() {
        if (animator?.isRunning == true) return

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = (minOf(width, height) / 2f) - 10f

        if (maxRadius <= 0) return

        // 1. Draw Static Concentric Radar Guide Rings
        ringPaint.color = accentColor
        ringPaint.alpha = 40
        canvas.drawCircle(centerX, centerY, maxRadius * 0.33f, ringPaint)
        canvas.drawCircle(centerX, centerY, maxRadius * 0.66f, ringPaint)
        canvas.drawCircle(centerX, centerY, maxRadius, ringPaint)

        // 2. Draw Crosshair Grid Lines
        gridPaint.color = accentColor
        gridPaint.alpha = 30
        canvas.drawLine(centerX - maxRadius, centerY, centerX + maxRadius, centerY, gridPaint)
        canvas.drawLine(centerX, centerY - maxRadius, centerX, centerY + maxRadius, gridPaint)

        // 3. Draw Expanding Ripple Waves
        ripplePaint.color = accentColor
        for (i in 0 until rippleCount) {
            val rippleProgress = (progress + i.toFloat() / rippleCount) % 1f
            val radius = maxRadius * rippleProgress
            val alpha = ((1f - rippleProgress) * 200).toInt().coerceIn(0, 255)

            ripplePaint.alpha = alpha
            canvas.drawCircle(centerX, centerY, radius, ripplePaint)
        }

        // 4. Draw Student Signal Blips
        blips.forEach { (angle, rFactor) ->
            val bx = centerX + (maxRadius * rFactor * cos(angle))
            val by = centerY + (maxRadius * rFactor * sin(angle))

            // Glowing blip core
            blipPaint.color = "#22D3EE".toColorInt()
            blipPaint.alpha = 220
            canvas.drawCircle(bx, by, 6f, blipPaint)

            // Blip halo
            blipPaint.alpha = 80
            canvas.drawCircle(bx, by, 12f, blipPaint)
        }
    }
}
