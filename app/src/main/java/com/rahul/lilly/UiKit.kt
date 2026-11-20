package com.rahul.lilly

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Naye design ke chhote hisse: font, rang, icon, switch, aurora background. */
object Ui {
    val INK = Color.parseColor("#07040F")
    val VIOLET = Color.parseColor("#8B5CF6")
    val DEEP = Color.parseColor("#6D28D9")
    val ORCHID = Color.parseColor("#E879F9")
    val MINT = Color.parseColor("#5EEAD4")
    val MIST = Color.parseColor("#C9C2E6")
    val DIM = Color.parseColor("#8A83A8")
    val CARD = Color.argb(20, 255, 255, 255)
    val EDGE = Color.argb(30, 255, 255, 255)

    private val cache = HashMap<Int, Typeface>()
    @Volatile private var iconFont: Typeface? = null

    /** Google Material icons (gol kone wale) — naam se */
    val SYM = mapOf(
        "home" to "\ue88a", "tune" to "\ue429", "mic" to "\ue029", "record_voice_over" to "\ue91f", "play_circle" to "\ue1c4",
        "download" to "\uf090", "hearing" to "\ue023", "translate" to "\ue8e2", "science" to "\uea4b", "spellcheck" to "\ue8ce",
        "music_note" to "\ue405", "graphic_eq" to "\ue1b8", "headphones" to "\uf01f", "help" to "\ue887", "notifications" to "\ue7f4",
        "keyboard" to "\ue312", "lock" to "\ue897", "credit_card" to "\ue870", "bolt" to "\uea0b", "place" to "\ue55f",
        "check_circle" to "\ue86c", "smart_toy" to "\uf06c", "auto_awesome" to "\ue65f", "power" to "\ue63c", "psychology" to "\uea4a",
        "smartphone" to "\ue32c", "lock_open" to "\ue898", "battery_charging_full" to "\ue1a3", "notifications_off" to "\ue7f6",
        "lightbulb" to "\ue0f0", "wifi_off" to "\ue648", "shield" to "\ue9e0", "system_update" to "\ue62a", "palette" to "\ue40a",
        "fingerprint" to "\ue90d", "edit_note" to "\ue745", "sentiment_very_dissatisfied" to "\ue814", "call" to "\ue0b0",
        "chat" to "\ue0b7", "queue_music" to "\ue03d", "touch_app" to "\ue913", "alarm" to "\ue855", "battery_full" to "\ue1a4", "event" to "\ue878", "today" to "\ue8df"
    )

    fun icons(ctx: Context): Typeface = iconFont ?: try {
        Typeface.createFromAsset(ctx.assets, "fonts/MaterialIconsRound-Regular.otf")
    } catch (e: Exception) { Typeface.DEFAULT }.also { iconFont = it }

    /** Sora font, wajan ke saath (300 halka, 400, 500, 600 mota) */
    fun font(ctx: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        try {
            if (Build.VERSION.SDK_INT >= 26)
                Typeface.Builder(ctx.assets, "fonts/Sora.ttf").setFontVariationSettings("'wght' $weight").build()
            else Typeface.createFromAsset(ctx.assets, "fonts/Sora.ttf")
        } catch (e: Exception) {
            Typeface.create(if (weight >= 500) "sans-serif-medium" else if (weight <= 300) "sans-serif-light" else "sans-serif", Typeface.NORMAL)
        }
    }
}

/** Peeche ki halki begni roshni (aurora) */
class AuroraView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        c.drawColor(Ui.INK)
        val w = width.toFloat(); val h = height.toFloat()
        fun blob(x: Float, y: Float, r: Float, col: Int) {
            p.shader = RadialGradient(x, y, r, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        blob(w * 0.5f, h * 0.36f, w * 0.75f, Color.argb(120, 139, 92, 246))
        blob(w * 0.15f, h * 0.48f, w * 0.5f, Color.argb(70, 232, 121, 249))
        blob(w * 0.9f, h * 0.42f, w * 0.45f, Color.argb(50, 94, 234, 212))
        blob(w * 0.5f, 0f, w * 0.6f, Color.argb(60, 109, 40, 217))
    }
}

/** Saade line wale icon (emoji nahi) */
class IconView(ctx: Context, var kind: String, var color: Int = Ui.MIST, private val stroke: Float = 1.9f) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val r = RectF()

    fun set(k: String) { kind = k; invalidate() }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save()
        c.translate((width - 24 * s) / 2, (height - 24 * s) / 2)
        c.scale(s, s)
        p.color = color
        p.strokeWidth = stroke
        p.style = Paint.Style.STROKE
        path.reset()
        when (kind) {
            "mic" -> {
                r.set(9f, 3f, 15f, 14f); c.drawRoundRect(r, 3f, 3f, p)
                path.moveTo(5f, 11f); path.cubicTo(5f, 20.5f, 19f, 20.5f, 19f, 11f); c.drawPath(path, p)
                c.drawLine(12f, 18f, 12f, 21f, p)
            }
            "home" -> {
                // Gol kono wala ghar + darwaza
                path.moveTo(4.5f, 10.2f); path.lineTo(11.2f, 4.6f); path.quadTo(12f, 4f, 12.8f, 4.6f); path.lineTo(19.5f, 10.2f)
                path.lineTo(19.5f, 18.5f); path.quadTo(19.5f, 20f, 18f, 20f); path.lineTo(6f, 20f); path.quadTo(4.5f, 20f, 4.5f, 18.5f); path.close()
                c.drawPath(path, p)
                path.reset(); path.moveTo(9.8f, 20f); path.lineTo(9.8f, 15f); path.quadTo(9.8f, 14f, 10.8f, 14f); path.lineTo(13.2f, 14f)
                path.quadTo(14.2f, 14f, 14.2f, 15f); path.lineTo(14.2f, 20f); c.drawPath(path, p)
            }
            "sliders" -> {
                c.drawLine(4f, 7f, 20f, 7f, p); c.drawLine(4f, 17f, 20f, 17f, p)
                p.style = Paint.Style.FILL; val fill = p.color
                p.color = Ui.INK; c.drawCircle(9f, 7f, 2.6f, p); c.drawCircle(15f, 17f, 2.6f, p)
                p.color = fill; p.style = Paint.Style.STROKE
                c.drawCircle(9f, 7f, 2.6f, p); c.drawCircle(15f, 17f, 2.6f, p)
            }
            "gear" -> {
                c.drawCircle(12f, 12f, 3.2f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    c.drawLine((12 + 6.5 * Math.cos(a)).toFloat(), (12 + 6.5 * Math.sin(a)).toFloat(),
                        (12 + 9 * Math.cos(a)).toFloat(), (12 + 9 * Math.sin(a)).toFloat(), p)
                }
                c.drawCircle(12f, 12f, 6.5f, p)
            }
            "play" -> { p.style = Paint.Style.FILL; path.moveTo(8f, 5f); path.lineTo(19f, 12f); path.lineTo(8f, 19f); path.close(); c.drawPath(path, p) }
            "pause" -> { p.style = Paint.Style.FILL; r.set(7f, 5f, 10.5f, 19f); c.drawRoundRect(r, 1f, 1f, p); r.set(13.5f, 5f, 17f, 19f); c.drawRoundRect(r, 1f, 1f, p) }
            "next" -> { p.style = Paint.Style.FILL; path.moveTo(5f, 5f); path.lineTo(15f, 12f); path.lineTo(5f, 19f); path.close(); c.drawPath(path, p); r.set(16f, 5f, 18.5f, 19f); c.drawRect(r, p) }
            "prev" -> { p.style = Paint.Style.FILL; path.moveTo(19f, 5f); path.lineTo(9f, 12f); path.lineTo(19f, 19f); path.close(); c.drawPath(path, p); r.set(5.5f, 5f, 8f, 19f); c.drawRect(r, p) }
            "chev" -> { path.moveTo(9f, 6f); path.lineTo(15f, 12f); path.lineTo(9f, 18f); c.drawPath(path, p) }
            "call" -> {
                path.moveTo(5f, 4f); path.lineTo(9f, 4f); path.lineTo(10.5f, 8.5f); path.lineTo(8.2f, 10f)
                path.cubicTo(9.3f, 12.4f, 11.6f, 14.7f, 14f, 15.8f); path.lineTo(15.5f, 13.5f); path.lineTo(20f, 15f); path.lineTo(20f, 19f)
                path.cubicTo(20f, 19.6f, 19.6f, 20f, 19f, 20f); path.cubicTo(11f, 19.5f, 4.5f, 13f, 4f, 5f); path.cubicTo(4f, 4.4f, 4.4f, 4f, 5f, 4f)
                c.drawPath(path, p)
            }
            "chat" -> {
                r.set(3.5f, 4.5f, 20.5f, 16.5f); c.drawRoundRect(r, 4f, 4f, p)
                path.moveTo(8f, 16.5f); path.lineTo(7f, 20.5f); path.lineTo(12f, 16.5f); c.drawPath(path, p)
            }
            "music" -> {
                path.moveTo(9f, 17f); path.lineTo(9f, 5f); path.lineTo(19f, 3.5f); path.lineTo(19f, 15.5f); c.drawPath(path, p)
                c.drawCircle(6.6f, 17f, 2.4f, p); c.drawCircle(16.6f, 15.5f, 2.4f, p)
            }
        }
        c.restore()
    }
}

/** ON/OFF switch — begni chamak ke saath */
class GlowSwitch(ctx: Context, initial: Boolean, private val onChange: (Boolean) -> Unit) : View(ctx) {
    var checked = initial
        private set
    private var pos = if (initial) 1f else 0f
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init {
        isClickable = true
        contentDescription = "switch"
    }

    /** Bahar se haal badalna (bina onChange chalaye) */
    fun setChecked(on: Boolean, animate: Boolean = true) {
        if (on == checked) return
        checked = on
        slide(animate)
    }

    private fun slide(animate: Boolean) {
        val to = if (checked) 1f else 0f
        if (!animate) { pos = to; invalidate(); return }
        ValueAnimator.ofFloat(pos, to).apply {
            duration = 200; interpolator = DecelerateInterpolator()
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((46 * d).toInt(), (28 * d).toInt())

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            checked = !checked
            slide(true)
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            onChange(checked)
            performClick()
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h)
        p.shader = null
        p.color = blend(Color.argb(46, 255, 255, 255), Color.WHITE, pos)
        c.drawRoundRect(r, h / 2, h / 2, p)
        val rad = h / 2 - 3 * d
        val cx = 3 * d + rad + (w - 2 * (3 * d + rad)) * pos
        if (pos > 0.5f) {
            p.color = Color.argb((90 * pos).toInt(), 167, 96, 250)
            c.drawCircle(cx, h / 2, rad + 3 * d, p)
        }
        p.color = blend(Color.WHITE, Ui.DEEP, pos)
        c.drawCircle(cx, h / 2, rad, p)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )
}

/** Dock ka mic: chamakta begni-gulabi gola, upar halki chamak, peeche roshni */
class MicButton(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = IconView(ctx, "mic", Color.WHITE, 2f)
    var glow = 0f
        set(v) { field = v; invalidate() }
    private val pulse = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
        duration = 2600; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { glow = it.animatedValue as Float }
    }

    init { isClickable = true; contentDescription = "Talk to Lilly"; setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); pulse.start() }
    override fun onDetachedFromWindow() { pulse.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 9 * d
        // Peeche ki roshni (dheere saans jaisi)
        p.shader = RadialGradient(cx, cy, r + 9 * d, intArrayOf(Color.argb((110 + 70 * glow).toInt(), 192, 132, 252), Color.TRANSPARENT), floatArrayOf(0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r + 9 * d, p)
        // Gola: upar-baayein halka lavender -> begni -> neeche-daayein gulabi
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(Color.parseColor("#C4B5FD"), Color.parseColor("#8B5CF6"), Color.parseColor("#7C3AED"), Color.parseColor("#D946EF")),
            floatArrayOf(0f, 0.38f, 0.68f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        // Upar ki sheeshe jaisi chamak
        p.shader = LinearGradient(cx, cy - r, cx, cy, Color.argb(90, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r - 1.5f * d, p)
        // Patla safed kinara
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 1 * d; p.color = Color.argb(90, 255, 255, 255)
        c.drawCircle(cx, cy, r - 0.5f * d, p); p.style = Paint.Style.FILL
        // Mic
        val s = (r * 0.92f).toInt()
        c.save(); c.translate(cx - s / 2f, cy - s / 2f)
        icon.layout(0, 0, s, s); icon.draw(c)
        c.restore()
    }
}
