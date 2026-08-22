package com.example.unilink.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random

class AuraParticlesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val random = Random()
    private val particles = mutableListOf<Particle>()
    private val particleCount = 25
    private var isPremium = false
    
    fun setPremiumMode(enabled: Boolean) {
        this.isPremium = enabled
        // Refresh particles with premium colors if enabled
        if (width > 0 && height > 0) {
            onSizeChanged(width, height, width, height)
        }
    }
    
    private data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedX: Float,
        var speedY: Float,
        var alpha: Int,
        val color: Int
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        particles.clear()
        val colors = if (isPremium) {
            intArrayOf(
                Color.parseColor("#4D22D3EE"), // Brighter Cyan
                Color.parseColor("#4DEC4899"), // Brighter Pink
                Color.parseColor("#4D8B5CF6"), // Brighter Purple
                Color.parseColor("#4DFACC15")  // Gold for Premium
            )
        } else {
            intArrayOf(
                Color.parseColor("#3322D3EE"),
                Color.parseColor("#33EC4899"),
                Color.parseColor("#228B5CF6")
            )
        }
        for (i in 0 until (if (isPremium) 40 else 25)) {
            particles.add(createParticle(w.toFloat(), h.toFloat(), colors))
        }
    }

    private fun createParticle(w: Float, h: Float, colors: IntArray): Particle {
        return Particle(
            x = random.nextFloat() * w,
            y = random.nextFloat() * h,
            radius = random.nextFloat() * 150f + 50f,
            speedX = (random.nextFloat() - 0.5f) * 0.8f,
            speedY = (random.nextFloat() - 0.5f) * 0.8f,
            alpha = random.nextInt(40) + 10,
            color = colors[random.nextInt(colors.size)]
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        particles.forEach { p ->
            p.x += p.speedX
            p.y += p.speedY
            
            // Boundary bounce
            if (p.x < -p.radius) p.x = width + p.radius
            if (p.x > width + p.radius) p.x = -p.radius
            if (p.y < -p.radius) p.y = height + p.radius
            if (p.y > height + p.radius) p.y = -p.radius
            
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
        
        invalidate() // Keep animating
    }
}
