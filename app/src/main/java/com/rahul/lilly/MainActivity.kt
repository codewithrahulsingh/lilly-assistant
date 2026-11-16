package com.rahul.lilly

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Lilly ki home screen: orb + controls. Sunne ka kaam LillyService karti hai. */
class MainActivity : Activity(), LillyBus.Ui {

    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var power: TextView
    private lateinit var lockValue: TextView
    private lateinit var appsValue: TextView
    private lateinit var batteryValue: TextView
    private lateinit var langValue: TextView

    private val prefs by lazy { getSharedPreferences("lilly", MODE_PRIVATE) }
    private val myLauncher: (Intent) -> Boolean = { i ->
        try { startActivity(i); true } catch (e: Exception) { false }
    }

    private val purple = Color.parseColor("#B79CFF")
    private val dim = Color.parseColor("#9A97B8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#0A0816")
        window.navigationBarColor = Color.BLACK
        buildUi()
        askPermissions()
    }

    // ---------------- UI ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** Ek control card. Value wala TextView lautata hai taaki baad me badla ja sake. */
    private fun card(row: LinearLayout, emoji: String, title: String, onTap: () -> Unit): TextView {
        val value = label("", 13f, purple)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.parseColor("#17142B"), 22, Color.parseColor("#2B2750"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            setOnClickListener { onTap() }
            addView(label(emoji, 24f, Color.WHITE))
            addView(label(title, 15f, Color.WHITE, true).apply { setPadding(0, dp(10), 0, dp(4)) })
            addView(value)
        }
        row.addView(box, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
            setMargins(dp(6), dp(6), dp(6), dp(6))
        })
        return value
    }

    private fun row(parent: LinearLayout): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        parent.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return r
    }

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(20), dp(14), dp(28))
        }

        // ---- Header ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("Lilly", 34f, Color.WHITE).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        titles.addView(label("Aapki personal assistant", 13f, dim))
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        power = label("", 14f, Color.WHITE, true).apply {
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener {
                if (LillyBus.running) {
                    prefs.edit().putBoolean("enabled", false).apply()
                    LillyService.stop(this@MainActivity)
                } else {
                    prefs.edit().putBoolean("enabled", true).apply()
                    if (hasMic()) startEngine() else askPermissions()
                }
            }
        }
        header.addView(power)
        col.addView(header)

        // ---- Orb ----
        orb = OrbView(this).apply {
            setOnClickListener { sendToEngine(LillyService.ACTION_ACTIVATE) }
        }
        col.addView(orb, LinearLayout.LayoutParams(dp(230), dp(230)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })

        status = label("", 16f, Color.parseColor("#D9D4F5")).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
        }
        col.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        heard = label("", 20f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            minHeight = dp(34)
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }
        col.addView(heard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        col.addView(label("Boliye “Hey Lilly” ya orb dabaiye", 13f, dim).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(18))
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ---- Controls ----
        col.addView(label("CONTROLS", 12f, dim, true).apply {
            letterSpacing = 0.15f
            setPadding(dp(10), 0, 0, dp(6))
        })

        val r1 = row(col)
        lockValue = card(r1, "🔒", "Lock screen call") { chooseLockMode() }
        card(r1, "🎙", "Awaaz badlo") { sendToEngine(LillyService.ACTION_NEXT_VOICE) }
            .text = "Agli awaaz try karo"

        val r2 = row(col)
        card(r2, "▶️", "Awaaz suno") { sendToEngine(LillyService.ACTION_TEST_VOICE) }
            .text = "Abhi wali awaaz test"
        card(r2, "⬇️", "Natural awaaz") { openTtsSettings() }
            .text = "Hindi voice download"

        val r3 = row(col)
        appsValue = card(r3, "📱", "Har screen par orb + apps") { appsAnywhere() }
        batteryValue = card(r3, "🔋", "Background me chalna") { batterySettings() }

        val r4 = row(col)
        card(r4, "💡", "Kya bol sakte ho") { showHelp() }.text = "Commands dekho"
        card(r4, "🛡", "Security") { showSecurity() }.text = "Lilly kitni safe hai"

        val r5 = row(col)
        langValue = card(r5, "🗣", "Sunne ki bhasha") { chooseLanguage() }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0A0816"), Color.parseColor("#140B26"), Color.BLACK)
            )
            addView(col)
        }
        setContentView(scroll)
    }

    private fun refreshCards() {
        langValue.text = langLabel(prefs.getString("lang", "hi") ?: "hi")
        lockValue.text = lockLabel(prefs.getString("lock_mode", "fav") ?: "fav")
        appsValue.text = if (Settings.canDrawOverlays(this)) "ON ✓" else "OFF — tap karke ON karein"
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        batteryValue.text =
            if (pm.isIgnoringBatteryOptimizations(packageName)) "Allowed ✓" else "Rukawat hai — tap karein"
    }

    override fun onStatus(text: String) = runOnUiThread { status.text = text }
    override fun onHeard(text: String) = runOnUiThread { heard.text = text }
    override fun onOrb(state: OrbView.State) = runOnUiThread { orb.state = state }
    override fun onLevel(level: Float) { orb.level = level }
    override fun onRunning(running: Boolean) = runOnUiThread {
        power.text = if (running) "●  ON" else "○  OFF"
        power.background = rounded(
            if (running) Color.parseColor("#3A1F7A") else Color.parseColor("#1C1A2E"), 50,
            if (running) Color.parseColor("#7B2FF7") else Color.parseColor("#34314F")
        )
    }

    // ---------------- Controls ----------------

    private val lockModes = listOf("off", "fav", "all")
    private fun lockLabel(m: String) = when (m) {
        "off" -> "Band (sabse safe)"
        "all" -> "Sab contacts (kam safe)"
        else -> "Sirf favourite ⭐"
    }

    private fun chooseLockMode() {
        val cur = lockModes.indexOf(prefs.getString("lock_mode", "fav") ?: "fav")
        AlertDialog.Builder(this)
            .setTitle("Phone lock ho tab Lilly kisko call kar sake?")
            .setSingleChoiceItems(lockModes.map { lockLabel(it) }.toTypedArray(), cur) { d, which ->
                prefs.edit().putString("lock_mode", lockModes[which]).apply()
                refreshCards()
                d.dismiss()
            }
            .show()
    }

    private fun langLabel(l: String) =
        if (l == "en") "English (Indian)" else "Hindi + English (Hinglish)"

    private fun chooseLanguage() {
        val codes = listOf("hi", "en")
        val cur = codes.indexOf(prefs.getString("lang", "hi") ?: "hi")
        AlertDialog.Builder(this)
            .setTitle("Lilly aapki baat kis bhasha me sune?")
            .setSingleChoiceItems(codes.map { langLabel(it) }.toTypedArray(), cur) { d, which ->
                prefs.edit().putString("lang", codes[which]).apply()
                refreshCards()
                d.dismiss()
            }
            .show()
    }

    private fun appsAnywhere() {
        if (Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("ON hai ✓")
                .setMessage("Ab \"Hey Lilly\" bolne par kisi bhi app ke upar neeche Siri jaisa orb aayega, aur apps seedha khulenge.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Har screen par Siri jaisa orb")
            .setMessage(
                "Kisi bhi app ke upar neeche Siri jaisa orb dikhane aur apps seedha kholne ke liye " +
                    "\"Display over other apps\" ON karna zaroori hai.\n\n" +
                    "Agar \"App was denied access\" aaye:\n" +
                    "1. \"App info\" dabaiye\n" +
                    "2. Upar ⋮ (3 dots) → \"Allow restricted settings\"\n" +
                    "3. Wapas aakar \"Permission\" dabaiye aur ON kijiye\n\n" +
                    "OFF rehne par bhi apps khulte hain: lock screen par popup se (unlock ke baad), " +
                    "warna ek tap wale notification se."
            )
            .setPositiveButton("Permission") { _, _ ->
                tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            .setNeutralButton("App info") { _, _ ->
                tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Rehne do", null)
            .show()
    }

    private fun batterySettings() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } else {
            tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Pehle boliye \"Hey Lilly\", phir:")
            .setItems(
                arrayOf(
                    "📞  \"Rahul ko call karo\"",
                    "📞  \"Call Mummy\"",
                    "📱  \"YouTube kholo\" / \"WhatsApp open karo\"",
                    "📷  \"Camera kholo\"",
                    "🔦  \"Torch jalao\" / \"Torch band karo\"",
                    "🕒  \"Kitne baje hain\"",
                    "📅  \"Aaj ki date kya hai\"",
                    "💬  \"Kaisi ho\" / \"Thank you\""
                ), null
            )
            .setPositiveButton("OK", null).show()
    }

    private fun showSecurity() {
        AlertDialog.Builder(this)
            .setTitle("🛡 Security")
            .setMessage(
                "• \"Hey Lilly\" phone ke andar hi (offline) pehchana jaata hai\n" +
                    "• Lilly ke paas Internet permission nahi hai\n" +
                    "• Lock screen par call sirf aapki chuni hui list ko\n" +
                    "• Lock screen se app kholne par pehle unlock maanga jaata hai\n" +
                    "• App data ka backup band hai"
            )
            .setPositiveButton("OK", null).show()
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
        for (i in tries) if (tryStart(i)) return
    }

    private fun tryStart(i: Intent): Boolean = try { startActivity(i); true } catch (e: Exception) { false }

    // ---------------- Lifecycle ----------------

    override fun onResume() {
        super.onResume()
        LillyBus.ui = this
        LillyBus.launcher = myLauncher
        status.text = LillyBus.lastStatus.ifBlank { "Shuru ho rahi hoon…" }
        heard.text = LillyBus.lastHeard
        orb.state = LillyBus.lastOrb
        onRunning(LillyBus.running)
        refreshCards()
        if (hasMic() && prefs.getBoolean("enabled", true) && !LillyBus.running) startEngine()
        if (missingPermissions().isEmpty()) askSpecialPermissions()
    }

    override fun onPause() {
        if (LillyBus.ui === this) LillyBus.ui = null
        if (LillyBus.launcher === myLauncher) LillyBus.launcher = null
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
        if (hasMic()) startEngine() else status.text = "Lilly ko sunne ke liye Mic permission chahiye"
    }

    /** Battery optimization se chhoot — ek hi baar poochha jaata hai */
    private fun askSpecialPermissions() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("asked_battery", false)) {
            prefs.edit().putBoolean("asked_battery", true).apply()
            tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }
}
