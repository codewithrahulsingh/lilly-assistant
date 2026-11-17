package com.rahul.lilly

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Siri jaisa glowing orb jo sunte/bolte waqt animate hota hai. */
class OrbView(context: Context) : View(context) {

    enum class State { IDLE, LISTENING, SPEAKING }

    var state = State.IDLE
    /** Mic ki awaaz ka level (0..1), sunte waqt orb isse fulta hai. */
    var level = 0f

    private var smooth = 0f
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val colors = intArrayOf(
        0xFF7B2FF7.toInt(), // purple
        0xFFF107A3.toInt(), // pink
        0xFF00C6FF.toInt(), // cyan
        0xFF2AF598.toInt()  // green
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { tick() }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        contentDescription = "Activate Lilly"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun tick() {
        val speed = when (state) {
            State.IDLE -> 0.012f
            State.LISTENING -> 0.04f
            State.SPEAKING -> 0.06f
        }
        phase += speed
        val target = when (state) {
            State.IDLE -> 0.08f + 0.05f * sin(phase * 2f)
            State.LISTENING -> 0.3f + level * 0.7f
            State.SPEAKING -> 0.45f + 0.35f * abs(sin(phase * 3f))
        }
        smooth += (target - smooth) * 0.15f
        if (state != State.LISTENING) level *= 0.9f
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f
        if (maxR <= 0f) return
        val base = maxR * (0.40f + 0.14f * smooth)

        // Bahar ki halki roshni
        paint.xfermode = null
        val glowAlpha = (50 + 150 * smooth).toInt().coerceIn(0, 255)
        paint.shader = RadialGradient(
            cx, cy, maxR,
            intArrayOf(Color.argb(glowAlpha, 150, 80, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, maxR, paint)

        // Rang-birange ghoomte hue blobs
        paint.xfermode = screen
        for (i in colors.indices) {
            val a = phase * (1f + i * 0.35f) + i * 1.5708f
            val off = base * 0.30f * (0.35f + smooth)
            val bx = cx + cos(a) * off
            val by = cy + sin(a * 1.3f) * off
            val r = base * (0.72f + 0.15f * sin(phase * 2f + i))
            paint.shader = RadialGradient(
                bx, by, r,
                intArrayOf(colors[i], colors[i] and 0x00FFFFFF),
                null, Shader.TileMode.CLAMP
            )
            c.drawCircle(bx, by, r, paint)
        }

        // Beech ki chamak
        paint.xfermode = null
        paint.shader = RadialGradient(
            cx - base * 0.2f, cy - base * 0.25f, base * 0.6f,
            intArrayOf(Color.argb(140, 255, 255, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, base * 0.9f, paint)
    }
}
