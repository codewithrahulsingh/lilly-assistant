package com.rahul.lilly

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.LayerDrawable
import android.view.animation.LinearInterpolator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Siri jaisa upar se aane wala card: kaun sa contact / kaun sa number. */
data class ChoiceItem(val number: Int, val name: String, val sub: String, val starred: Boolean = false)

data class ChoiceSheet(
    val kind: String,          // call / wa_msg / wa_call / wa_video / sms
    val title: String,         // "Call Abhishek"
    val subtitle: String,      // "12 contacts · say a number or tap"
    val items: List<ChoiceItem>,
    val more: Int              // aur kitne bache ("Show 7 more")
)

object ChoicePopup {

    /** Tap = jaise wahi bola ("3", "more", "cancel") — LillyService set karta hai */
    @Volatile var onPick: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var card: LinearLayout? = null
    private var shown: ChoiceSheet? = null
    private var glow: GlowFrame? = null

    /** Har row ka glass highlight (Lilly jab uska naam bole) */
    private class RowRef(val view: View, val hl: GradientDrawable) { var anim: ValueAnimator? = null; var on = false }
    private val rows = mutableListOf<RowRef>()
    /** Lilly kaun se naam bolegi (rows ke kram me) */
    @Volatile private var spokenNames: List<String> = emptyList()
    private var cueStarts = IntArray(0)
    private val cueRunnables = mutableListOf<Runnable>()


    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun show(ctx0: Context, sheet: ChoiceSheet) {
        val ctx = ctx0.applicationContext
        main.post {
            if (!Settings.canDrawOverlays(ctx)) return@post
            if (root != null && shown?.title == sheet.title) { swapContent(ctx, sheet); return@post }
            hideNow()
            build(ctx, sheet)
        }
    }

    fun hide() { spokenNames = emptyList(); main.post { animateOut() } }

    // ---------------- Banana ----------------

    private fun build(ctx: Context, sheet: ChoiceSheet) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Android 12+ par peeche ki screen dhundhli (asli glass); warna card thoda gaadha taaki padh sake
        val blur = Build.VERSION.SDK_INT >= 31 && try { wm.isCrossWindowBlurEnabled } catch (e: Throwable) { false }
        val frame = FrameLayout(ctx).apply { setPadding(dp(ctx, 8), dp(ctx, 32), dp(ctx, 8), dp(ctx, 10)) }
        val g = GlowFrame(ctx, dp(ctx, 30).toFloat())
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(ctx, blur)
            if (Build.VERSION.SDK_INT >= 21) clipToOutline = true
            setPadding(0, dp(ctx, 14), 0, dp(ctx, 10))
        }
        val inset = dp(ctx, 3)
        g.addView(c, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(inset, inset, inset, inset) })
        frame.addView(g, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        if (Build.VERSION.SDK_INT >= 21) g.elevation = dp(ctx, 14).toFloat()
        fill(ctx, c, sheet)

        // Upar swipe = band (cancel)
        var downY = 0f
        c.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; false }
                MotionEvent.ACTION_MOVE -> { val dy = e.rawY - downY; if (dy < 0) (v.parent as? View)?.translationY = dy; false }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - downY
                    if (dy < -dp(ctx, 60)) { onPick?.invoke("cancel"); animateOut() } else (v.parent as? View)?.animate()?.translationY(0f)?.setDuration(150)?.start()
                    abs(dy) > dp(ctx, 10)
                }
                else -> false
            }
        }

        val lp = WindowManager.LayoutParams(
            MATCH_PARENT, WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            if (blur && Build.VERSION.SDK_INT >= 31) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(ctx, 18)
            }
        }
        try {
            wm.addView(frame, lp)
        } catch (e: Exception) { return }
        this.wm = wm; root = frame; card = c; shown = sheet; glow = g
        // Upar se halka sa uchhal ke aana
        g.translationY = -dp(ctx, 220).toFloat()
        g.alpha = 0f
        g.scaleX = 0.94f; g.scaleY = 0.94f
        g.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(0.9f)).setDuration(380).start()
    }

    private fun swapContent(ctx: Context, sheet: ChoiceSheet) {
        val c = card ?: return
        shown = sheet
        clearCues()
        c.animate().alpha(0.4f).setDuration(110).withEndAction {
            c.removeAllViews()
            fill(ctx, c, sheet)
            c.animate().alpha(1f).setDuration(160).start()
        }.start()
    }

    private fun accent(kind: String) = when (kind) {
        "wa_msg", "wa_call", "wa_video" -> intArrayOf(Color.parseColor("#25D366"), Color.parseColor("#128C7E"))
        "sms" -> intArrayOf(Color.parseColor("#5AC8FA"), Color.parseColor("#007AFF"))
        else -> intArrayOf(Color.parseColor("#34C759"), Color.parseColor("#1F9D55"))
    }

    private fun fill(ctx: Context, c: LinearLayout, sheet: ChoiceSheet) {
        rows.clear()
        // ---- Header ----
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 18), 0, dp(ctx, 18), dp(ctx, 12))
        }
        val icon = TextView(ctx).apply {
            text = when (sheet.kind) { "wa_msg", "sms" -> "💬"; "wa_video" -> "📹"; else -> "📞" }
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, accent(sheet.kind)).apply { shape = GradientDrawable.OVAL }
        }
        head.addView(icon, LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40)))
        val tbox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 12), 0, 0, 0) }
        tbox.addView(TextView(ctx).apply {
            text = sheet.title; setTextColor(Color.WHITE); textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        tbox.addView(TextView(ctx).apply {
            text = sheet.subtitle; setTextColor(Color.argb(160, 235, 235, 245)); textSize = 13f; maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        head.addView(tbox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val close = TextView(ctx).apply {
            text = "✕"; setTextColor(Color.argb(170, 255, 255, 255)); textSize = 15f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(40, 255, 255, 255)) }
            setOnClickListener { onPick?.invoke("cancel"); animateOut() }
        }
        head.addView(close, LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30)))
        c.addView(head)

        // ---- Rows ----
        sheet.items.forEachIndexed { i, it ->
            val row = row(ctx, it, sheet.kind)
            c.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2))
            })
            row.alpha = 0f
            row.translationY = dp(ctx, 8).toFloat()
            row.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 45L).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        }

        // ---- Footer: aur dikhao / hint ----
        val foot = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 14), dp(ctx, 4))
        }
        foot.addView(TextView(ctx).apply {
            text = if (sheet.more > 0) "Say a number or “more”" else "Say a number or a name"
            setTextColor(Color.argb(120, 235, 235, 245)); textSize = 12f
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (sheet.more > 0) foot.addView(chip(ctx, "More (${sheet.more})  ›") { onPick?.invoke("more") })
        c.addView(foot, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun chip(ctx: Context, label: String, tap: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextColor(Color.parseColor("#E9D5FF"))
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(70, 192, 132, 252)),
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.argb(70, 124, 58, 237), Color.argb(70, 236, 72, 153))).apply {
                cornerRadius = dp(ctx, 18).toFloat(); setStroke(dp(ctx, 1), Color.argb(110, 216, 180, 254))
            }, null)
        setOnClickListener { tap() }
    }

    private val palette = listOf("#FF6B6B", "#F7B731", "#20BF6B", "#45AAF2", "#A55EEA", "#FD9644", "#2BCBBA", "#EB3B5A", "#4B7BEC", "#8854D0")

    private fun initials(name: String): String {
        val w = name.split(Regex("\\s+")).filter { p -> p.any { it.isLetter() } }
        val a = w.getOrNull(0)?.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
        val b = w.getOrNull(1)?.firstOrNull { it.isLetter() }?.uppercaseChar()
        return if (b != null) "$a$b" else "$a"
    }

    private fun row(ctx: Context, it: ChoiceItem, kind: String): View {
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 9), dp(ctx, 10), dp(ctx, 9))
            isClickable = true
        }
        // Glass "hover": halka safed sheesha + begni kinara; Lilly naam bole tab chamakta hai
        val hl = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(58, 255, 255, 255), Color.argb(26, 192, 132, 252))).apply {
            cornerRadius = dp(ctx, 20).toFloat()
            setStroke(dp(ctx, 1), Color.argb(150, 216, 180, 254))
            alpha = 0
        }
        val mask = GradientDrawable().apply { cornerRadius = dp(ctx, 20).toFloat(); setColor(Color.WHITE) }
        r.background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 216, 180, 254)), hl, mask)
        val ref = RowRef(r, hl)
        rows.add(ref)
        r.setOnClickListener { _ ->
            // Chuna hua row chamke, phir wahi jaise bola
            clearCues(); light(ref, true)
            main.postDelayed({ onPick?.invoke(it.number.toString()) }, 140)
        }
        val col = Color.parseColor(palette[abs(it.name.hashCode()) % palette.size])
        val av = TextView(ctx).apply {
            text = initials(it.name)
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(col, darker(col))).apply { shape = GradientDrawable.OVAL }
        }
        r.addView(av, LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)))
        val txt = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 12), 0, dp(ctx, 8), 0) }
        txt.addView(TextView(ctx).apply {
            text = (if (it.starred) "★ " else "") + it.name
            setTextColor(Color.WHITE); textSize = 16f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        if (it.sub.isNotBlank()) txt.addView(TextView(ctx).apply {
            text = it.sub; setTextColor(Color.argb(150, 235, 235, 245)); textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        r.addView(txt, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val num = TextView(ctx).apply {
            text = it.number.toString()
            setTextColor(Color.argb(220, 255, 255, 255)); textSize = 13f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 10).toFloat(); setColor(Color.argb(30, 255, 255, 255)); setStroke(dp(ctx, 1), Color.argb(50, 255, 255, 255))
            }
        }
        r.addView(num, LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 26)))
        val act = TextView(ctx).apply {
            text = when (kind) { "wa_msg", "sms" -> "💬"; "wa_video" -> "📹"; else -> "📞" }
            textSize = 15f; gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, accent(kind)).apply { shape = GradientDrawable.OVAL }
        }
        r.addView(act, LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)).apply { leftMargin = dp(ctx, 10) })
        return r
    }

    private fun darker(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] *= 0.72f
        return Color.HSVToColor(hsv)
    }

    // ---------------- Glass ----------------

    /** Sheesha: begni rang ki parat + upar halki chamak + patla safed kinara */
    private fun glass(ctx: Context, blur: Boolean): android.graphics.drawable.Drawable {
        val r = dp(ctx, 28).toFloat()
        val a1 = if (blur) 150 else 228
        val a2 = if (blur) 170 else 238
        val base = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(a1, 46, 24, 86), Color.argb(a2, 22, 14, 44), Color.argb(a1, 40, 20, 70))).apply {
            cornerRadius = r
        }
        val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(46, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(0, 255, 255, 255))).apply {
            cornerRadius = r
        }
        val edge = GradientDrawable().apply {
            cornerRadius = r; setColor(Color.TRANSPARENT); setStroke(dp(ctx, 1), Color.argb(60, 255, 255, 255))
        }
        return LayerDrawable(arrayOf(base, sheen, edge)).apply { setLayerInset(1, 0, 0, 0, dp(ctx, 70)) }
    }

    /** Card ke chaaron taraf ghoomti neon roshni (upar → daayein → neeche → baayein) */
    private class GlowFrame(ctx: Context, private val radius: Float) : FrameLayout(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val rect = RectF()
        private val m = Matrix()
        private var angle = 0f
        private var shader: SweepGradient? = null
        private val paints = listOf(7f to 38, 4.5f to 70, 2.6f to 140, 1.4f to 255).map { (w, a) ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w * d; alpha = a }
        }
        private val spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { angle = it.animatedValue as Float; invalidate() }
        }
        init { setWillNotDraw(false); clipChildren = false; clipToPadding = false }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            shader = SweepGradient(w / 2f, h / 2f, NEON_COLORS, null)
            paints.forEach { it.shader = shader }
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val s = shader ?: return
            m.setRotate(angle, width / 2f, height / 2f)
            s.setLocalMatrix(m)
            val inset = 1.6f * d
            rect.set(inset, inset, width - inset, height - inset)
            for (p in paints) canvas.drawRoundRect(rect, radius - inset, radius - inset, p)
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); spin.start() }
        override fun onDetachedFromWindow() { spin.cancel(); super.onDetachedFromWindow() }

        companion object {
            val NEON_COLORS = intArrayOf(
                Color.parseColor("#7C3AED"), Color.parseColor("#C084FC"), Color.parseColor("#F0ABFC"),
                Color.parseColor("#22D3EE"), Color.parseColor("#6366F1"), Color.parseColor("#A855F7"), Color.parseColor("#7C3AED")
            )
        }
    }

    // ---------------- Naam bolte waqt glass highlight ----------------

    /** Brain batata hai Lilly kaun se naam bolegi (card ki rows ke kram me) */
    fun setSpokenNames(names: List<String>) { spokenNames = names }

    fun wantsCues(): Boolean = spokenNames.isNotEmpty()

    /** Bolna shuru: text me har naam kahan hai, utne time par us row ko chamkao */
    fun beginSpeech(text: String, durationMs: Long) {
        val names = spokenNames
        main.post {
            if (root == null || names.isEmpty() || rows.isEmpty()) return@post
            clearCues()
            val starts = findStarts(text, names)
            cueStarts = starts
            val len = text.length.coerceAtLeast(1)
            val dur = durationMs.coerceIn(800L, 60_000L)
            for (i in starts.indices) {
                if (starts[i] < 0 || i >= rows.size) continue
                val at = dur * starts[i] / len
                val r = Runnable { highlight(i) }
                cueRunnables.add(r); main.postDelayed(r, at)
            }
            val end = Runnable { highlight(-1) }
            cueRunnables.add(end); main.postDelayed(end, dur + 250)
        }
    }

    /** Phone ki awaaz bata deti hai abhi kaun sa akshar bol rahi hai — tab bilkul sahi naam chamkao */
    fun atChar(pos: Int) {
        main.post {
            val st = cueStarts
            if (root == null || st.isEmpty()) return@post
            var idx = -1
            for (i in st.indices) if (st[i] in 0..pos) idx = i
            if (idx >= 0) {
                // Tabhi badlo jab andaaze wala schedule peechhe/aage ho
                cueRunnables.forEach { main.removeCallbacks(it) }; cueRunnables.clear()
                highlight(idx)
            }
        }
    }

    fun endSpeech() = main.post { clearCues(); highlight(-1) }

    /** Peeche se dhundo (shuru me "Rahul naam ke 4 contact" wala naam na pakde) */
    private fun findStarts(text: String, names: List<String>): IntArray {
        val low = text.lowercase()
        val out = IntArray(names.size) { -1 }
        var limit = low.length
        for (i in names.indices.reversed()) {
            val n = names[i].lowercase().trim()
            if (n.isEmpty()) continue
            val at = low.lastIndexOf(n, limit - 1)
            if (at >= 0) { out[i] = at; limit = at }
        }
        // Na mile toh barabar baant do
        if (out.all { it < 0 }) for (i in out.indices) out[i] = (low.length * (i + 1)) / (out.size + 2)
        return out
    }

    private fun highlight(i: Int) {
        rows.forEachIndexed { k, r -> light(r, k == i) }
    }

    private fun light(r: RowRef, on: Boolean) {
        if (r.on == on) return
        r.on = on
        r.anim?.cancel()
        val from = r.hl.alpha
        r.anim = ValueAnimator.ofInt(from, if (on) 255 else 0).apply {
            duration = if (on) 220 else 320
            addUpdateListener { r.hl.alpha = it.animatedValue as Int; r.view.invalidate() }
            start()
        }
        r.view.animate().scaleX(if (on) 1.025f else 1f).scaleY(if (on) 1.025f else 1f)
            .translationZ(if (on) r.view.resources.displayMetrics.density * 6 else 0f).setDuration(220).start()
    }

    private fun clearCues() {
        cueRunnables.forEach { main.removeCallbacks(it) }
        cueRunnables.clear()
        cueStarts = IntArray(0)
    }

    // ---------------- Band ----------------

    private fun animateOut() {
        val c: View = glow ?: card ?: return hideNow()
        val ctx = c.context
        c.animate().translationY(-dp(ctx, 200).toFloat()).alpha(0f).setDuration(220).withEndAction { hideNow() }.start()
    }

    private fun hideNow() {
        val r = root ?: return
        clearCues()
        root = null; card = null; shown = null; glow = null
        rows.clear()
        try { wm?.removeView(r) } catch (e: Exception) { }
    }
}
