package com.rahul.lilly

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Siri jaisa popup: "Lilly" bolte hi screen jagti hai (lock screen par bhi),
 * neeche orb aur jo aapne bola wo dikhta hai. Baat khatam hote hi apne aap band.
 */
class OverlayActivity : Activity(), LillyBus.Ui {

    private lateinit var orb: OrbView
    private lateinit var heard: TextView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val myLauncher: (Intent) -> Boolean = { launch(it) }

    /** App kholna — phone lock ho toh pehle fingerprint/PIN maangta hai */
    private fun launch(intent: Intent): Boolean {
        handler.removeCallbacksAndMessages(null)
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= 26) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { go(intent) }
                override fun onDismissCancelled() { finish() }
                override fun onDismissError() { finish() }
            })
        } else go(intent)
        return true
    }

    private fun go(intent: Intent) {
        try { startActivity(intent) } catch (e: Exception) { }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            setOnClickListener { finish() }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.BLACK, Color.argb(230, 10, 8, 24), Color.TRANSPARENT)
            )
            isClickable = true
        }

        heard = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            setTextColor(Color.parseColor("#B9B3D9"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        orb = OrbView(this)

        panel.addView(heard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        panel.addView(orb, LinearLayout.LayoutParams(dp(200), dp(200)))

        root.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        LillyBus.ui = this
        LillyBus.launcher = myLauncher
        heard.text = LillyBus.lastHeard
        status.text = LillyBus.lastStatus
        orb.state = LillyBus.lastOrb
        // Popup khul gaya, ab uska notification nahi chahiye
        getSystemService(NotificationManager::class.java).cancel(9)
        // Kuch na ho toh 25 sec me khud band
        handler.postDelayed({ finish() }, 25_000)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        if (LillyBus.ui === this) LillyBus.ui = null
        if (LillyBus.launcher === myLauncher) LillyBus.launcher = null
        super.onPause()
    }

    override fun onStatus(text: String) = runOnUiThread { status.text = text }
    override fun onHeard(text: String) = runOnUiThread { heard.text = text }
    override fun onOrb(state: OrbView.State) = runOnUiThread { orb.state = state }
    override fun onLevel(level: Float) { orb.level = level }
    override fun onRunning(running: Boolean) { if (!running) runOnUiThread { finish() } }
    override fun keepAlive() {
        runOnUiThread {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ finish() }, 40_000)
        }
    }
    override fun onSessionEnd() {
        runOnUiThread { handler.postDelayed({ finish() }, 900) }
    }
}
