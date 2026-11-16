package com.rahul.lilly

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/** Sirf screen: orb aur text dikhati hai. Sunne ka kaam LillyService karti hai. */
class MainActivity : Activity(), LillyBus.Ui {

    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var toggle: TextView

    private val prefs by lazy { getSharedPreferences("lilly", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildUi()
        askPermissions()
    }

    // ---------------- UI ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val title = TextView(this).apply {
            text = "Lilly"
            setTextColor(Color.WHITE)
            textSize = 36f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        root.addView(title, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = dp(72)
        })

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#A8A8C0"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
        }
        root.addView(status, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = dp(128)
        })

        toggle = TextView(this).apply {
            setTextColor(Color.parseColor("#C9B6FF"))
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                if (LillyBus.running) {
                    prefs.edit().putBoolean("enabled", false).apply()
                    LillyService.stop(this@MainActivity)
                } else {
                    prefs.edit().putBoolean("enabled", true).apply()
                    startEngine()
                }
            }
        }
        root.addView(toggle, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(24); rightMargin = dp(8)
        })

        val settings = TextView(this).apply {
            text = "⚙ Settings"
            setTextColor(Color.parseColor("#C9B6FF"))
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { showSettings() }
        }
        root.addView(settings, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(24); leftMargin = dp(8)
        })

        heard = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(dp(28), 0, dp(28), 0)
        }
        root.addView(heard, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply {
            bottomMargin = dp(80)
        })

        orb = OrbView(this)
        root.addView(orb, FrameLayout.LayoutParams(dp(260), dp(260), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(36)
        })
        orb.setOnClickListener {
            if (!hasMic()) { askPermissions(); return@setOnClickListener }
            prefs.edit().putBoolean("enabled", true).apply()
            LillyService.start(this, LillyService.ACTION_ACTIVATE)
        }

        setContentView(root)
    }

    override fun onStatus(text: String) = runOnUiThread { status.text = text }
    override fun onHeard(text: String) = runOnUiThread { heard.text = text }
    override fun onOrb(state: OrbView.State) = runOnUiThread { orb.state = state }
    override fun onLevel(level: Float) { orb.level = level }
    override fun onRunning(running: Boolean) = runOnUiThread {
        toggle.text = if (running) "● ON — band karein" else "○ OFF — chalu karein"
    }

    // ---------------- Settings ----------------

    private val lockModes = listOf("off", "fav", "all")
    private fun lockLabel(m: String) = when (m) {
        "off" -> "Band (sabse safe)"
        "all" -> "Sab contacts (kam safe)"
        else -> "Sirf favourite ⭐ contacts"
    }

    private fun showSettings() {
        val mode = prefs.getString("lock_mode", "fav") ?: "fav"
        val items = arrayOf(
            "🔒 Lock screen par call: ${lockLabel(mode)}",
            "🎙 Agli awaaz try karo",
            "▶ Abhi wali awaaz suno",
            "⬇ Zyada natural Hindi awaaz download karo"
        )
        AlertDialog.Builder(this)
            .setTitle("Lilly Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseLockMode()
                    1 -> sendToEngine(LillyService.ACTION_NEXT_VOICE)
                    2 -> sendToEngine(LillyService.ACTION_TEST_VOICE)
                    3 -> openTtsSettings()
                }
            }
            .setNegativeButton("Band karo", null)
            .show()
    }

    private fun chooseLockMode() {
        val cur = lockModes.indexOf(prefs.getString("lock_mode", "fav") ?: "fav")
        AlertDialog.Builder(this)
            .setTitle("Phone lock ho tab Lilly kisko call kar sake?")
            .setSingleChoiceItems(lockModes.map { lockLabel(it) }.toTypedArray(), cur) { d, which ->
                prefs.edit().putString("lock_mode", lockModes[which]).apply()
                d.dismiss()
            }
            .show()
    }

    private fun sendToEngine(action: String) {
        if (!hasMic()) { askPermissions(); return }
        prefs.edit().putBoolean("enabled", true).apply()
        try { LillyService.start(this, action) } catch (e: Exception) { }
    }

    private fun openTtsSettings() {
        val tries = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent("android.speech.tts.engine.INSTALL_TTS_DATA"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in tries) {
            try { startActivity(i); return } catch (e: Exception) { }
        }
    }

    // ---------------- Lifecycle ----------------

    override fun onResume() {
        super.onResume()
        LillyBus.ui = this
        status.text = LillyBus.lastStatus.ifBlank { "Shuru ho rahi hoon…" }
        heard.text = LillyBus.lastHeard
        orb.state = LillyBus.lastOrb
        onRunning(LillyBus.running)
        if (hasMic() && prefs.getBoolean("enabled", true) && !LillyBus.running) startEngine()
        if (missingPermissions().isEmpty()) askSpecialPermissions()
    }

    override fun onPause() {
        if (LillyBus.ui === this) LillyBus.ui = null
        super.onPause()
    }

    private fun startEngine() {
        try {
            LillyService.start(this)
        } catch (e: Exception) {
            status.text = "Lilly shuru nahi ho payi: ${e.message}"
        }
    }

    // ---------------- Permissions ----------------

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun missingPermissions(): List<String> {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        return wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun askPermissions() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasMic()) {
            startEngine()
        } else {
            status.text = "Lilly ko sunne ke liye Mic permission chahiye"
        }
    }

    /**
     * Battery optimization se chhoot — taaki phone Lilly ko background me band na kare.
     * (Security ke liye "Display over other apps" jaisi restricted permission nahi maangi jaati.)
     */
    private fun askSpecialPermissions() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("asked_battery", false)) {
            prefs.edit().putBoolean("asked_battery", true).apply()
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) { }
        }
    }
}
