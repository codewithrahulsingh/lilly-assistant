package com.rahul.lilly

import android.animation.ValueAnimator
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

    fun hide() = main.post { animateOut() }

    // ---------------- Banana ----------------

    private fun build(ctx: Context, sheet: ChoiceSheet) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val frame = FrameLayout(ctx).apply { setPadding(dp(ctx, 10), dp(ctx, 34), dp(ctx, 10), dp(ctx, 10)) }
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 30).toFloat()
                setColor(Color.argb(242, 22, 20, 34))
                setStroke(dp(ctx, 1), Color.argb(46, 255, 255, 255))
            }
            if (Build.VERSION.SDK_INT >= 21) { elevation = dp(ctx, 18).toFloat(); clipToOutline = true }
            setPadding(0, dp(ctx, 14), 0, dp(ctx, 8))
        }
        frame.addView(c, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        fill(ctx, c, sheet)

        // Upar swipe = band (cancel)
        var downY = 0f
        c.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; false }
                MotionEvent.ACTION_MOVE -> { val dy = e.rawY - downY; if (dy < 0) v.translationY = dy; false }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - downY
                    if (dy < -dp(ctx, 60)) { onPick?.invoke("cancel"); animateOut() } else v.animate().translationY(0f).setDuration(150).start()
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
        ).apply { gravity = Gravity.TOP }
        try {
            wm.addView(frame, lp)
        } catch (e: Exception) { return }
        this.wm = wm; root = frame; card = c; shown = sheet
        // Upar se halka sa uchhal ke aana
        c.translationY = -dp(ctx, 220).toFloat()
        c.alpha = 0f
        c.scaleX = 0.94f; c.scaleY = 0.94f
        c.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(0.9f)).setDuration(380).start()
    }

    private fun swapContent(ctx: Context, sheet: ChoiceSheet) {
        val c = card ?: return
        shown = sheet
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
            c.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
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
            text = if (sheet.more > 0) "Say a number, a name or “more”" else "Say a number or a name"
            setTextColor(Color.argb(120, 235, 235, 245)); textSize = 12f
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (sheet.more > 0) foot.addView(chip(ctx, "Show ${sheet.more} more") { onPick?.invoke("more") })
        c.addView(foot, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun chip(ctx: Context, label: String, tap: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextColor(Color.parseColor("#8AB4FF"))
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 138, 180, 255)),
            GradientDrawable().apply { cornerRadius = dp(ctx, 18).toFloat(); setColor(Color.argb(38, 138, 180, 255)) }, null)
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
            setPadding(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9))
            background = RippleDrawable(ColorStateList.valueOf(Color.argb(50, 255, 255, 255)), null,
                GradientDrawable().apply { setColor(Color.WHITE) })
            isClickable = true
            setOnClickListener { v ->
                // Chuna hua row chamke, phir wahi jaise bola
                v.setBackgroundColor(Color.argb(45, 52, 199, 89))
                main.postDelayed({ onPick?.invoke(it.number.toString()) }, 120)
            }
        }
        val col = Color.parseColor(palette[abs(it.name.hashCode()) % palette.size])
        val av = TextView(ctx).apply {
            text = initials(it.name)
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(col, darker(col))).apply { shape = GradientDrawable.OVAL }
        }
        r.addView(av, LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42)))
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
            background = GradientDrawable().apply { cornerRadius = dp(ctx, 10).toFloat(); setColor(Color.argb(36, 255, 255, 255)) }
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

    // ---------------- Band ----------------

    private fun animateOut() {
        val c = card ?: return hideNow()
        val ctx = c.context
        c.animate().translationY(-dp(ctx, 200).toFloat()).alpha(0f).setDuration(220).withEndAction { hideNow() }.start()
    }

    private fun hideNow() {
        val r = root ?: return
        root = null; card = null; shown = null
        try { wm?.removeView(r) } catch (e: Exception) { }
    }
}
