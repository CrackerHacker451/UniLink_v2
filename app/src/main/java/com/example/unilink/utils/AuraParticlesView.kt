package com.example.unilink.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class AuraParticlesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gradientPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isPremium = false

    fun setPremiumMode(enabled: Boolean) {
        this.isPremium = enabled
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val theme = ThemeUtils.getSelectedTheme(context)
        val (c1, c2) = when (theme) {
            "Light" -> Pair("#1A2563EB", "#1A6366F1")
            "Teal" -> Pair("#2E10B981", "#2E06B6D4")
            "Indigo" -> Pair("#2E8B5CF6", "#2E6366F1")
            "Rose" -> Pair("#2EEC4899", "#2EF43F5E")
            "Amber" -> Pair("#2EF59E0B", "#2EFB923C")
            else -> Pair("#2E06B6D4", "#2E8B5CF6")
        }

        // Top-right soft ambient glow
        val shader1 = RadialGradient(
            w * 0.85f, -h * 0.1f, w * 0.7f,
            intArrayOf(Color.parseColor(c1), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        gradientPaint1.shader = shader1

        // Bottom-left subtle ambient glow
        val shader2 = RadialGradient(
            w * 0.15f, h * 0.6f, w * 0.6f,
            intArrayOf(Color.parseColor(c2), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        gradientPaint2.shader = shader2
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, gradientPaint1)
        canvas.drawRect(0f, 0f, w, h, gradientPaint2)
    }
}
