package com.rahul.lilly

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * iPhone ki Siri jaisa: kisi bhi app ke upar, screen ke neeche se orb upar aata hai.
 * Iske liye "Display over other apps" permission ON honi chahiye.
 */
class FloatingOrb(private val ctx: Context) : LillyBus.Ui {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var orb: OrbView? = null
    private var heard: TextView? = null
    private var status: TextView? = null
    private val hideRunnable = Runnable { hide() }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun show(): Boolean {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 25_000)
        if (root != null) return true

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.BLACK, Color.argb(235, 10, 8, 24), Color.argb(150, 10, 8, 24), Color.TRANSPARENT)
            )
            setOnClickListener { hide() }
        }
        val h = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            text = LillyBus.lastHeard
        }
        val s = TextView(ctx).apply {
            setTextColor(Color.parseColor("#C9C3EA"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            text = LillyBus.lastStatus
        }
        val o = OrbView(ctx).apply { state = LillyBus.lastOrb }
        panel.addView(h, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(s, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(o, LinearLayout.LayoutParams(dp(170), dp(170)))

        val lp = WindowManager.LayoutParams(
            MATCH_PARENT, WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        return try {
            wm.addView(panel, lp)
            root = panel; orb = o; heard = h; status = s
            LillyBus.ui = this
            // Neeche se upar slide
            panel.translationY = dp(260).toFloat()
            panel.alpha = 0f
            panel.animate().translationY(0f).alpha(1f).setDuration(260).start()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hideSoon() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 900)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        val r = root ?: return
        root = null; orb = null; heard = null; status = null
        if (LillyBus.ui === this) LillyBus.ui = null
        try { wm.removeView(r) } catch (e: Exception) { }
    }

    override fun onStatus(text: String) { status?.text = text }
    override fun onHeard(text: String) { heard?.text = text }
    override fun onOrb(state: OrbView.State) { orb?.state = state }
    override fun onLevel(level: Float) { orb?.level = level }
    override fun onRunning(running: Boolean) { if (!running) hide() }
    override fun onSessionEnd() = hideSoon()
}
