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
    private lateinit var allowedValue: TextView
    private lateinit var voiceValue: TextView
    private lateinit var aiValue: TextView
    private lateinit var lockPopupValue: TextView
    private lateinit var learnedValue: TextView
    private lateinit var realValue: TextView
    private lateinit var memoryValue: TextView
    private lateinit var wakeValue: TextView
    private lateinit var spotifyValue: TextView
    private lateinit var updateValue: TextView
    private lateinit var notifValue: TextView
    private lateinit var scriptValue: TextView
    private lateinit var placesValue: TextView
    private lateinit var ai2Value: TextView

    private val prefs by lazy { getSharedPreferences("lilly", MODE_PRIVATE) }
    private var setupShown = false
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
        titles.addView(label("Your personal assistant", 13f, dim))
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

        col.addView(label("Say “Lilly” or “Hey Lilly”", 13f, dim).apply {
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
        card(r1, "🎙", "Change voice") { sendToEngine(LillyService.ACTION_NEXT_VOICE) }
            .text = "Try the next voice"

        val r2 = row(col)
        card(r2, "▶️", "Hear voice") { sendToEngine(LillyService.ACTION_TEST_VOICE) }
            .text = "Test the current voice"
        voiceValue = card(r2, "⬇️", "Natural voice") { downloadVoice() }

        val r3 = row(col)
        appsValue = card(r3, "📱", "Orb on every screen + apps") { appsAnywhere() }
        batteryValue = card(r3, "🔋", "Run in background") { batterySettings() }

        val r4 = row(col)
        card(r4, "💡", "What you can say") { showHelp() }.text = "See commands"
        card(r4, "🛡", "Security") { showSecurity() }.text = "How safe Lilly is"

        val r5 = row(col)
        langValue = card(r5, "🗣", "Listening language") { chooseLanguage() }
        card(r5, "🔕", "Hide notification") { hideNotification() }.text = "Tap to turn it off"

        val r6 = row(col)
        allowedValue = card(r6, "✅", "Apps Lilly can open") { chooseApps() }
        aiValue = card(r6, "🤖", "AI chat") { aiSetup() }

        val r7 = row(col)
        lockPopupValue = card(r7, "🔓", "Orb on lock screen") { lockPopupSetup() }
        learnedValue = card(r7, "🧠", "What Lilly learned") { showLearned() }

        val r8 = row(col)
        realValue = card(r8, "🎤", "Real human voice") { chooseRealVoice() }
        memoryValue = card(r8, "📒", "What Lilly remembers") { showMemory() }

        val r9 = row(col)
        wakeValue = card(r9, "👂", "Wake-up sensitivity") { chooseSensitivity() }
        spotifyValue = card(r9, "🤖", "Lilly helper") { spotifySetup() }

        val r10 = row(col)
        updateValue = card(r10, "⬆️", "Update Lilly") { Updater.check(this, userAsked = true) { refreshCards() } }
        notifValue = card(r10, "🔔", "Read my notifications") { notifSetup() }

        val r11 = row(col)
        scriptValue = card(r11, "💬", "WhatsApp typing") { chooseScript() }
        placesValue = card(r11, "📍", "Home, office & city") { showPlaces() }

        val r12 = row(col)
        ai2Value = card(r12, "🔌", "Second AI (freemodel etc.)") { ai2Setup() }

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
        aiValue.text = if (prefs.getString("ai_key", "").isNullOrBlank()) "OFF — turn on to get answers to anything" else "ON ✓"
        lockPopupValue.text = if (canFullScreen()) "Allowed ✓" else "Tap to allow"
        val key = prefs.getString("ai_key", "") ?: ""
        val rv = prefs.getString("real_voice", RealVoice.DEFAULT) ?: RealVoice.DEFAULT
        realValue.text = when {
            key.isBlank() || key.startsWith("sk-ant-") -> "Needs a free Gemini key — tap"
            rv == RealVoice.OFF -> "OFF — using phone voice"
            else -> "ON ✓ $rv"
        }
        val mem = memory()
        val nf = mem.facts().size
        val na = mem.answerCount()
        memoryValue.text = if (nf == 0 && na == 0) "Nothing yet — tap to see how" else "$nf things about you · $na saved answers"
        wakeValue.text = sensLabel(prefs.getString("wake_sens", "normal") ?: "normal")
        val helper = LillyAccess.enabled(this)
        spotifyValue.text = if (helper) "ON ✓ Spotify, WhatsApp send, speaker" else "OFF — tap to turn on"
        notifValue.text = when {
            !prefs.getBoolean("read_notifs", false) -> "OFF"
            !helper -> "Needs Lilly helper — tap"
            else -> "ON ✓"
        }
        scriptValue.text = if (prefs.getString("msg_script", "spoken") == "roman") "Hinglish (Roman letters)" else "As you speak (हिंदी / English)"
        ai2Value.text = when {
            (prefs.getString("ai2_key", "") ?: "").isBlank() -> "Not set — tap to add"
            prefs.getString("ai_order", "main") == "custom" -> "ON ✓ used first"
            else -> "ON ✓ backup when Gemini fails"
        }
        val places = listOf("home_address", "office_address", "city").count { !prefs.getString(it, null).isNullOrBlank() }
        placesValue.text = if (places == 0) "Not set — tap to see how" else "$places saved"
        updateValue.text = Updater.status(this)
        val learnedCount = learner().items().size
        learnedValue.text = if (learnedCount == 0) "Nothing yet — tap to see how" else "$learnedCount things learned"
        val allowed = prefs.getStringSet("allowed_apps", null)
        allowedValue.text = if (allowed == null) "All apps allowed" else "${allowed.size} apps allowed"
        voiceValue.text =
            if (prefs.getBoolean("has_hi_voice", true)) "Download a better voice" else "No Hindi voice found — download"
        langValue.text = langLabel(prefs.getString("lang", "auto") ?: "auto")
        lockValue.text = lockLabel(prefs.getString("lock_mode", "fav") ?: "fav")
        appsValue.text = if (Settings.canDrawOverlays(this)) "ON ✓" else "OFF — tap to turn on"
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        batteryValue.text =
            if (pm.isIgnoringBatteryOptimizations(packageName)) "Allowed ✓" else "Restricted — tap to fix"
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
        "off" -> "Off (safest)"
        "all" -> "All contacts (less safe)"
        else -> "Favourites only ⭐"
    }

    private fun chooseLockMode() {
        val cur = lockModes.indexOf(prefs.getString("lock_mode", "fav") ?: "fav")
        AlertDialog.Builder(this)
            .setTitle("Who can Lilly call while the phone is locked?")
            .setSingleChoiceItems(lockModes.map { lockLabel(it) }.toTypedArray(), cur) { d, which ->
                prefs.edit().putString("lock_mode", lockModes[which]).apply()
                refreshCards()
                d.dismiss()
            }
            .show()
    }

    private fun canFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

    private fun lockPopupSetup() {
        if (canFullScreen()) {
            AlertDialog.Builder(this)
                .setTitle("Orb on lock screen")
                .setMessage(
                    "This permission is already allowed. If the orb still doesn't appear on the lock screen, " +
                        "also turn on \"Orb on every screen + apps\"."
                )
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Orb on lock screen")
            .setMessage("On the next screen, allow \"Full screen notifications\" for Lilly. The orb will then open by itself even when the phone is locked.")
            .setPositiveButton("Open") { _, _ ->
                val ok = Build.VERSION.SDK_INT >= 34 && tryStart(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
                if (!ok) tryStart(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
            .setNegativeButton("Not now", null).show()
    }

    /** Normal baat-cheet ke liye AI key (Gemini free ya Anthropic) */
    private fun aiSetup() {
        val input = android.widget.EditText(this).apply {
            hint = "Paste your AI key here"
            setSingleLine(true)
            setText(prefs.getString("ai_key", ""))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = android.widget.FrameLayout(this).apply {
            setPadding(dp(22), dp(4), dp(22), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("🤖 AI chat")
            .setMessage(
                "With this, Lilly can answer anything you say (questions, small talk, information).\n\n" +
                    "You need an AI key:\n" +
                    "• Google Gemini — free: tap \"Get key\", sign in with Google, tap \"Create API key\", then copy the key and paste it here\n" +
                    "• Or an Anthropic (Claude) key, which starts with sk-ant-\n\n" +
                    "Note: when this is on, the question you ask is sent over the internet to that AI company. " +
                    "Commands like calls, apps and songs stay on your phone."
            )
            .setView(box)
            .setPositiveButton("Save & test") { _, _ ->
                val key = input.text.toString().filter { !it.isWhitespace() }
                prefs.edit().putString("ai_key", key).apply()
                refreshCards()
                if (key.isNotEmpty()) testAiKey(key)
            }
            .setNeutralButton("Get key") { _, _ ->
                tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
            }
            .setNegativeButton("Turn off") { _, _ ->
                prefs.edit().remove("ai_key").apply()
                refreshCards()
            }
            .show()
    }

    /** Key save hote hi ek chhota sawal bhej kar dekho ki AI sach me jawab de raha hai */
    private fun testAiKey(key: String) {
        android.widget.Toast.makeText(this, "Testing the AI key…", android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            var failure: Throwable? = null
            val answer = try { AiChat.ask(key, emptyList(), "Say hello") } catch (e: Exception) { failure = e; null }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val b = AlertDialog.Builder(this)
                if (answer != null) {
                    b.setTitle("AI is working ✓").setMessage("Lilly's reply:\n" + answer.display)
                } else {
                    val detail = (failure as? AiChat.AiError)?.let { "\n\nServer: HTTP ${it.code} ${it.detail}" } ?: ""
                    b.setTitle("AI didn't work ✗").setMessage(AiChat.explain(failure).first + detail)
                }
                b.setPositiveButton("OK", null).show()
            }
        }.start()
    }

    /** User chunta hai ki Lilly voice se kaunse apps khol sakti hai */
    private fun chooseApps() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val saved = prefs.getStringSet("allowed_apps", null)
        val checked = BooleanArray(apps.size) { saved == null || apps[it].first in saved }
        AlertDialog.Builder(this)
            .setTitle("Which apps can Lilly open by voice?")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Save") { _, _ ->
                val set = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()
                prefs.edit().putStringSet("allowed_apps", set).apply()
                refreshCards()
            }
            .setNeutralButton("Allow all") { _, _ ->
                prefs.edit().remove("allowed_apps").apply()
                refreshCards()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadVoice() {
        AlertDialog.Builder(this)
            .setTitle("A better female voice")
            .setMessage(
                "Lilly uses Google's Hindi voice, which speaks both Hindi and English. For the best voice:\n\n" +
                    "1. Tap \"Download voice\" and download Hindi (India)\n" +
                    "2. Update \"Speech Recognition & Synthesis\" (Google) in the Play Store\n" +
                    "3. Come back and use \"Change voice\" to listen to the voices and pick your favourite\n\n" +
                    "When the internet is on, Lilly picks Google's most natural (online) voice by itself."
            )
            .setPositiveButton("Download voice") { _, _ ->
                val ok = tryStart(
                    Intent("android.speech.tts.engine.INSTALL_TTS_DATA").setPackage("com.google.android.tts")
                )
                if (!ok) openTtsSettings()
            }
            .setNeutralButton("Play Store") { _, _ ->
                if (!tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts")))) {
                    openTtsSettings()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun hideNotification() {
        AlertDialog.Builder(this)
            .setTitle("Hide the notification")
            .setMessage(
                "Turn the switch off on the next screen. The notification will disappear and " +
                    "Lilly will keep running in the background.\n\n" +
                    "(Android doesn't let an app remove this notification itself, so you need to turn it off once.)"
            )
            .setPositiveButton("Open") { _, _ ->
                val ok = Build.VERSION.SDK_INT >= 26 && tryStart(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, LillyService.CHANNEL)
                )
                if (!ok) tryStart(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun langLabel(l: String) =
        when (l) {
            "en" -> "English only"
            "hi" -> "Hindi + Hinglish"
            else -> "Auto: English + Hindi + Hinglish"
        }

    private fun chooseLanguage() {
        val codes = listOf("auto", "hi", "en")
        val cur = codes.indexOf(prefs.getString("lang", "auto") ?: "auto")
        AlertDialog.Builder(this)
            .setTitle("Which language should Lilly listen for?")
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
                .setTitle("It's on ✓")
                .setMessage("Now when you say \"Hey Lilly\", the orb appears at the bottom over any app, and apps open directly.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Orb on every screen")
            .setMessage(
                "Without this, the orb can't open by itself; you only get a notification.\n\n" +
                    "With it on, the orb appears at the bottom of both the lock screen and the home screen " +
                    "as soon as you say \"Lilly\".\n\n" +
                    "If you see \"App was denied access\":\n" +
                    "1. Tap \"App info\"\n" +
                    "2. Tap ⋮ (3 dots) at the top → \"Allow restricted settings\"\n" +
                    "3. Come back, tap \"Permission\" and turn it on\n\n" +
                    "Apps still open with this off: from the popup on the lock screen (after unlocking), " +
                    "or from a one-tap notification."
            )
            .setPositiveButton("Permission") { _, _ ->
                tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            .setNeutralButton("App info") { _, _ ->
                tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun chooseRealVoice() {
        val key = prefs.getString("ai_key", "") ?: ""
        if (key.isBlank() || key.startsWith("sk-ant-")) {
            AlertDialog.Builder(this)
                .setTitle("🎤 Real human voice")
                .setMessage(
                    "Lilly can speak in a natural, human-like woman's voice using Google Gemini — " +
                        "the same free Gemini key used for AI chat.\n\nAdd your Gemini key in \"AI chat\" first."
                )
                .setPositiveButton("Add key") { _, _ -> aiSetup() }
                .setNegativeButton("Not now", null).show()
            return
        }
        val codes = listOf(RealVoice.OFF) + RealVoice.VOICES.map { it.name }
        val labels = listOf("Off — use the phone's voice") + RealVoice.VOICES.map { "${it.name} — ${it.about}" }
        val cur = codes.indexOf(prefs.getString("real_voice", RealVoice.DEFAULT) ?: RealVoice.DEFAULT)
        AlertDialog.Builder(this)
            .setTitle("Choose Lilly's voice (tap to hear)")
            .setSingleChoiceItems(labels.toTypedArray(), cur) { _, which ->
                prefs.edit().putString("real_voice", codes[which]).apply()
                refreshCards()
                previewRealVoice(codes[which])
            }
            .setPositiveButton("Done", null)
            .setNeutralButton("About") { _, _ ->
                AlertDialog.Builder(this).setTitle("About the real voice").setMessage(
                    "• Made by Google Gemini, so it needs internet the first time a sentence is spoken\n" +
                        "• Every sentence is saved on the phone, so repeated lines (like \"Yes Rahul, how can I help?\") play instantly, even offline\n" +
                        "• New sentences (like AI answers) can take 1–3 seconds\n" +
                        "• If the internet is slow or the free limit runs out, Lilly uses the phone's voice for a while, so she never goes silent\n" +
                        "• The text Lilly speaks is sent to Google to make the voice"
                ).setPositiveButton("OK", null).show()
            }
            .show()
    }

    private var preview: RealVoice? = null

    /** Chuni hui awaaz yahin suna do; na chale toh asli wajah dikhao */
    private fun previewRealVoice(name: String) {
        preview?.stop()
        if (name == RealVoice.OFF) return
        val rv = RealVoice(this)
        preview = rv
        android.widget.Toast.makeText(this, "Loading $name… (first time can take a few seconds)", android.widget.Toast.LENGTH_SHORT).show()
        rv.speak(
            "Hi Rahul, I'm Lilly, and this is the $name voice. Kaisi lagi meri awaaz?",
            onDone = { },
            onFallback = {
                if (isFinishing || preview !== rv) return@speak
                AlertDialog.Builder(this)
                    .setTitle("Real voice didn't load ✗")
                    .setMessage(
                        "So Lilly would use the phone's voice, which sounds the same for every choice.\n\n" +
                            "Reason:\n${RealVoice.lastError ?: "unknown"}\n\nPlease send a screenshot of this."
                    )
                    .setPositiveButton("OK", null).show()
            },
            waitMs = 25_000L, force = true
        )
    }

    override fun onStop() {
        preview?.stop()
        super.onStop()
    }

    private fun memory() = Memory(
        load = { prefs.getString("memory", null) },
        save = { prefs.edit().putString("memory", it).apply() }
    )

    private val memoryHelp =
        "Lilly remembers things about you and uses them in her AI answers.\n\n" +
            "• Tell her: \"Remember that my gym is at 6 pm\" / \"Yaad rakhna ki mummy ka birthday 12 June ko hai\"\n" +
            "• Ask: \"What do you remember about me?\" / \"Mere baare mein kya yaad hai?\"\n" +
            "• Remove: \"Forget my gym timing\" / \"Gym wali baat bhool jao\"\n" +
            "• With AI chat on, she also notes lasting facts you mention while chatting\n" +
            "• Answers to general questions are saved, so asking again is instant and works offline " +
            "(not for time, news, weather and other things that change)\n\n" +
            "Saved on this phone. When you ask the AI something, these facts are sent along so the answer fits you."

    private fun showMemory() {
        val m = memory()
        val facts = m.facts().reversed()
        val b = AlertDialog.Builder(this).setTitle("📒 What Lilly remembers")
        if (facts.isEmpty() && m.answerCount() == 0) {
            b.setMessage(memoryHelp).setPositiveButton("OK", null).show()
            return
        }
        val rows = facts.map { "• ${it.text}" } + "💾 ${m.answerCount()} saved answers (tap to clear)"
        b.setItems(rows.toTypedArray()) { _, which ->
            if (which == facts.size) {
                AlertDialog.Builder(this).setTitle("Clear saved answers?")
                    .setPositiveButton("Clear") { _, _ -> m.clearAnswers(); refreshCards() }
                    .setNegativeButton("Cancel", null).show()
                return@setItems
            }
            val f = facts[which]
            AlertDialog.Builder(this).setTitle("Forget this?").setMessage(f.text)
                .setPositiveButton("Forget") { _, _ -> m.forget(f); refreshCards(); showMemory() }
                .setNegativeButton("Keep", null).show()
        }
            .setPositiveButton("Close", null)
            .setNeutralButton("How it works") { _, _ ->
                AlertDialog.Builder(this).setTitle("How Lilly remembers").setMessage(memoryHelp).setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Forget all") { _, _ ->
                AlertDialog.Builder(this).setTitle("Forget everything Lilly remembers?")
                    .setPositiveButton("Forget all") { _, _ -> m.clearAll(); refreshCards() }
                    .setNegativeButton("Cancel", null).show()
            }
            .show()
    }

    /** Doosri AI: freemodel.in jaisi koi bhi OpenAI-compatible service */
    private fun ai2Setup() {
        fun field(hint: String, value: String, secret: Boolean = false) = android.widget.EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
            setText(value)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                (if (secret) android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_URI)
        }
        val url = field("Base URL", prefs.getString("ai2_url", AiChat.CUSTOM_DEFAULT_URL) ?: AiChat.CUSTOM_DEFAULT_URL)
        val key = field("API key", prefs.getString("ai2_key", "") ?: "", secret = true)
        val model = field("Model (optional — leave empty to auto-pick)", prefs.getString("ai2_model", "") ?: "")
        val first = android.widget.CheckBox(this).apply {
            text = "Use this first (Gemini becomes the backup)"
            isChecked = prefs.getString("ai_order", "main") == "custom"
        }
        val mem = android.widget.CheckBox(this).apply {
            text = "Also send what Lilly remembers about me"
            isChecked = prefs.getBoolean("ai2_memory", false)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), 0)
            listOf(url, key, model, first, mem).forEach { addView(it) }
        }
        AlertDialog.Builder(this)
            .setTitle("🔌 Second AI")
            .setMessage(
                "Any service that works like OpenAI's API (for example freemodel.in). " +
                    "By default it's a backup: Lilly uses Gemini first and switches to this one if Gemini fails.\n\n" +
                    "Your questions go to this service. Things Lilly remembers about you are sent only if you tick the box."
            )
            .setView(box)
            .setPositiveButton("Save & test") { _, _ ->
                prefs.edit()
                    .putString("ai2_url", url.text.toString().trim().ifBlank { AiChat.CUSTOM_DEFAULT_URL })
                    .putString("ai2_key", key.text.toString().filter { !it.isWhitespace() })
                    .putString("ai2_model", model.text.toString().trim())
                    .putString("ai_order", if (first.isChecked) "custom" else "main")
                    .putBoolean("ai2_memory", mem.isChecked)
                    .apply()
                refreshCards()
                testAi2()
            }
            .setNeutralButton("Remove") { _, _ ->
                prefs.edit().remove("ai2_key").remove("ai2_model").putString("ai_order", "main").apply()
                refreshCards()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testAi2() {
        val c = AiChat.customFrom(prefs) ?: return
        android.widget.Toast.makeText(this, "Testing the second AI…", android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            var failure: Throwable? = null
            val answer = try { AiChat.ask("", emptyList(), "Say hello", custom = c) } catch (e: Exception) { failure = e; null }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val b = AlertDialog.Builder(this)
                if (answer != null) {
                    b.setTitle("Second AI is working ✓")
                        .setMessage("Model: ${AiChat.customModelUsed}\n\nLilly's reply:\n" + answer.display)
                } else {
                    val detail = (failure as? AiChat.AiError)?.let { "\n\nServer: HTTP ${it.code} ${it.detail}" } ?: ""
                    b.setTitle("Second AI didn't work ✗").setMessage(AiChat.explain(failure).first + detail)
                }
                b.setPositiveButton("OK", null).show()
            }
        }.start()
    }

    private fun notifSetup() {
        val on = prefs.getBoolean("read_notifs", false)
        AlertDialog.Builder(this)
            .setTitle("🔔 Read my notifications")
            .setMessage(
                "When this is on, Lilly keeps your latest notifications (like WhatsApp messages) in memory, " +
                    "so you can ask \"Kiska message aaya?\" or \"Read my notifications\".\n\n" +
                    "• Kept only in memory, never saved or sent anywhere, cleared when Lilly stops\n" +
                    "• Needs \"Lilly helper\" to be on\n\n" +
                    "It's ${if (on) "ON" else "OFF"} now."
            )
            .setPositiveButton(if (on) "Turn off" else "Turn on") { _, _ ->
                prefs.edit().putBoolean("read_notifs", !on).apply()
                refreshCards()
                if (!on && !LillyAccess.enabled(this)) spotifySetup()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseScript() {
        val codes = listOf("spoken", "roman")
        val labels = listOf("As you speak — Hindi script for Hindi (मैं आ रहा हूँ), English for English", "Hinglish in Roman letters (main aa raha hoon)")
        val cur = codes.indexOf(prefs.getString("msg_script", "spoken") ?: "spoken")
        AlertDialog.Builder(this)
            .setTitle("How should Lilly type WhatsApp messages?")
            .setSingleChoiceItems(labels.toTypedArray(), cur) { d, which ->
                prefs.edit().putString("msg_script", codes[which]).apply()
                refreshCards(); d.dismiss()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showPlaces() {
        val home = prefs.getString("home_address", null)
        val office = prefs.getString("office_address", null)
        val city = prefs.getString("city", null)
        AlertDialog.Builder(this)
            .setTitle("📍 Home, office & city")
            .setMessage(
                "Home: ${home ?: "not set"}\nOffice: ${office ?: "not set"}\nCity (for weather): ${city ?: "not set — uses your location"}\n\n" +
                    "To set, just tell Lilly:\n• \"My home address is Malviya Nagar, Jaipur\"\n• \"Mera office C-Scheme Jaipur mein hai\"\n• \"My city is Jaipur\"\n\n" +
                    "Then: \"Ghar ka rasta dikhao\", \"Take me to office\", \"Mausam kaisa hai\"."
            )
            .setPositiveButton("OK", null)
            .setNegativeButton("Clear all") { _, _ ->
                prefs.edit().remove("home_address").remove("office_address").remove("city").apply(); refreshCards()
            }
            .show()
    }

    private fun spotifySetup() {
        if (LillyAccess.enabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Lilly helper is ON ✓")
                .setMessage(
                    "Lilly can now press buttons for you, only right after you ask:\n" +
                        "• Spotify: taps the first song so it plays\n• WhatsApp: taps Send / Call\n• Calls: taps Speaker\n\n" +
                        "To turn it off: Settings → Accessibility → Lilly helper."
                )
                .setPositiveButton("OK", null)
                .setNeutralButton("Settings") { _, _ -> tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("🤖 Lilly helper")
            .setMessage(
                "Some apps don't let other apps finish the job — Spotify only searches, WhatsApp only fills in the message, " +
                    "and some phones ignore \"call on speaker\". With this on, Lilly presses that last button for you:\n" +
                    "• Spotify: the first song   • WhatsApp: Send / Call   • Calls: Speaker\n\n" +
                    "• She only presses a button right after you ask\n" +
                    "• Notifications are read only if you also turn on \"Read my notifications\"\n" +
                    "• Nothing is saved or sent anywhere\n\n" +
                    "Steps:\n" +
                    "1. Tap \"Open settings\" → Accessibility\n" +
                    "2. Find \"Lilly helper\" (may be under \"Downloaded apps\" / \"Installed apps\") → turn ON\n\n" +
                    "If it is greyed out or says \"Restricted setting\":\n" +
                    "1. Tap \"App info\" below\n" +
                    "2. Tap ⋮ (3 dots) at the top → \"Allow restricted settings\"\n" +
                    "3. Come back and do the steps again"
            )
            .setPositiveButton("Open settings") { _, _ -> tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("App info") { _, _ ->
                tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun sensLabel(s: String) = when (s) {
        "low" -> "Low — only a clear “Hey Lilly”"
        "high" -> "High — wakes faster, more mistakes"
        else -> "Normal (recommended)"
    }

    private fun chooseSensitivity() {
        val codes = listOf("low", "normal", "high")
        val cur = codes.indexOf(prefs.getString("wake_sens", "normal") ?: "normal")
        AlertDialog.Builder(this)
            .setTitle("How easily should Lilly wake up?")
            .setSingleChoiceItems(codes.map { sensLabel(it) }.toTypedArray(), cur) { d, which ->
                prefs.edit().putString("wake_sens", codes[which]).apply()
                refreshCards()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun learner() = Learner(
        load = { prefs.getString("learned", null) },
        save = { prefs.edit().putString("learned", it).apply() }
    )

    private val teachHelp =
        "Lilly learns by herself as you use her:\n\n" +
            "• Names you call and apps you open often are matched first\n" +
            "• If a command fails and you say it another way, she remembers the first way too\n" +
            "• With AI chat on, new ways of saying things are understood once and then remembered\n" +
            "• \"Play … on Spotify\" makes Spotify your default for songs\n\n" +
            "You can also teach her directly:\n" +
            "\"When I say good night, turn off the torch\"\n" +
            "\"Jab main boss bolun to Rohit ko call karna\"\n\n" +
            "Everything she learns stays on this phone."

    private fun showLearned() {
        val l = learner()
        val items = l.items()
        val b = AlertDialog.Builder(this).setTitle("🧠 What Lilly learned")
        if (items.isEmpty()) {
            b.setMessage(teachHelp).setPositiveButton("OK", null).show()
            return
        }
        b.setItems(items.map { it.text }.toTypedArray()) { _, which ->
            val item = items[which]
            AlertDialog.Builder(this)
                .setTitle("Forget this?")
                .setMessage(item.text)
                .setPositiveButton("Forget") { _, _ -> l.forget(item); refreshCards(); showLearned() }
                .setNegativeButton("Keep", null).show()
        }
            .setPositiveButton("Close", null)
            .setNeutralButton("How it learns") { _, _ ->
                AlertDialog.Builder(this).setTitle("How Lilly learns").setMessage(teachHelp).setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Forget all") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Forget everything Lilly learned?")
                    .setPositiveButton("Forget all") { _, _ -> l.clear(); refreshCards() }
                    .setNegativeButton("Cancel", null).show()
            }
            .show()
    }

    private fun openBatterySettings() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            if (!tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))))
                tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } else {
            tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    /** Har company ki "Auto-start" wali screen alag jagah hoti hai */
    private fun openAutoStart() {
        val screens = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
        )
        for ((pkg, cls) in screens) {
            if (tryStart(Intent().setClassName(pkg, cls))) return
        }
        android.widget.Toast.makeText(this, "Open “App info” → Battery / Auto-launch and allow Lilly", android.widget.Toast.LENGTH_LONG).show()
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun batterySettings() {
        val brand = Build.MANUFACTURER.lowercase()
        val lock = when {
            "xiaomi" in brand || "redmi" in brand || "poco" in brand ->
                "Open recent apps, long-press the Lilly card and tap the 🔒 lock."
            "samsung" in brand -> "Open recent apps, tap the Lilly icon on top of its card → \"Keep open\"."
            "vivo" in brand || "iqoo" in brand -> "Open recent apps and swipe the Lilly card down to lock it."
            "oppo" in brand || "realme" in brand || "oneplus" in brand ->
                "Open recent apps, tap ⋮ (or long-press) on the Lilly card → \"Lock\"."
            else -> "Open recent apps and look for a lock option on the Lilly card (long-press or ⋮)."
        }
        AlertDialog.Builder(this)
            .setTitle("Keep Lilly running")
            .setMessage(
                "When you clear Lilly from recent apps, your phone (${Build.MANUFACTURER}) may fully close her to save battery. " +
                    "Then she can't hear \"Lilly\" until you open the app again.\n\n" +
                    "Do these 3 things once:\n\n" +
                    "1. Battery → set Lilly to \"Unrestricted\" / \"No restrictions\" / \"Allow background activity\"\n\n" +
                    "2. Auto-start / Auto-launch → turn ON for Lilly\n\n" +
                    "3. Lock Lilly in recent apps: $lock\n" +
                    "After this, \"Clear all\" won't close her.\n\n" +
                    "If your phone still closes Lilly, you'll get a notification — tap it and she starts listening again."
            )
            .setPositiveButton("Battery") { _, _ -> openBatterySettings() }
            .setNeutralButton("Auto-start") { _, _ -> openAutoStart() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Say \"Lilly\" or \"Hey Lilly\", then (English, Hindi or Hinglish):")
            .setItems(
                arrayOf(
                    "📞  \"Rahul ko call karo\"",
                    "📞  \"Call Mummy\" / \"मम्मी को फोन लगाओ\"",
                    "🔊  \"Call Rohit on speaker\" / \"Rohit ko speaker pe call karo\"",
                    "🔢  Two numbers? Lilly asks — say \"first\", \"mobile\" or the last digits",
                    "📱  \"Open YouTube\" / \"WhatsApp kholo\"",
                    "🎵  \"Play Kesariya\" / \"Arijit Singh ke gaane chalao\"",
                    "🎵  \"Play Kesariya on YouTube\" / \"YouTube par Kesariya chalao\"",
                    "🎵  \"Play Kesariya on Spotify\" / \"Spotify par Kesariya chalao\"",
                    "💬  \"Rohit ko WhatsApp pe bolo main 10 minute mein aa raha hoon\"",
                    "💬  \"Send a WhatsApp to mom saying I'll be late\" / \"Rohit ko WhatsApp video call karo\"",
                    "🗺  \"Ghar ka rasta dikhao\" / \"Nearest petrol pump\" / \"Navigate to Jaipur airport\"",
                    "🔔  \"Kiska message aaya?\" / \"Read my notifications\"",
                    "☀️  \"Good morning\" / \"Mausam kaisa hai\" / \"Will it rain tomorrow?\"",
                    "⚙️  \"DND on karo\" / \"Brightness kam karo\" / \"Phone silent karo\" / \"WiFi kholo\"",
                    "📺  \"YouTube pe cricket highlights chalao\"",
                    "🔢  \"Call nine eight seven six…\" / \"नौ आठ सात… पर कॉल करो\"",
                    "⏰  \"Set an alarm for 6 am\" / \"Subah sadhe 6 baje ka alarm\" / \"Har roz 7 baje\"",
                    "⏰  \"Wake me up in 20 minutes\" / \"10 minute ka timer\"",
                    "⏰  \"Remind me at 5 pm to call mom\" / \"5 baje yaad dilana ki dawai leni hai\"",
                    "⏯  \"Pause\" / \"Next song\" / \"Gaana band karo\"",
                    "🔊  \"Volume up\" / \"Awaaz kam karo\"",
                    "🔋  \"How much battery is left\" / \"Battery kitni hai\"",
                    "📷  \"Open camera\" / \"Camera kholo\"",
                    "🔦  \"Turn on the torch\" / \"Torch band karo\"",
                    "🕒  \"What time is it\" / \"Kitne baje hain\"",
                    "📅  \"What's the date\" / \"Aaj ki date kya hai\"",
                    "💬  \"How are you\" / \"Kaisi ho\" / \"Thank you\"",
                    "📒  \"Remember that my gym is at 6 pm\" / \"Yaad rakhna ki…\"",
                    "📒  \"What do you remember about me?\"",
                    "🧠  \"When I say good night, turn off the torch\"",
                    "🧠  \"Jab main boss bolun to Rohit ko call karna\""
                ), null
            )
            .setPositiveButton("OK", null).show()
    }

    private fun showSecurity() {
        AlertDialog.Builder(this)
            .setTitle("🛡 Security")
            .setMessage(
                "• \"Hey Lilly\" is recognised on the phone itself (offline)\n" +
                    "• Your commands are turned into text by your phone's Google speech service\n" +
                    "• Questions go to an AI company only if you turn on \"AI chat\"; with a Gemini key, the real voice sends what Lilly says to Google; playing a song on YouTube looks it up online\n" +
                    "• Things Lilly remembers about you stay on the phone, and are sent along with AI questions\n" +
                    "• Notifications are read only if you turn on \"Read my notifications\", kept in memory only\n" +
                    "• Weather uses your approximate location with Open-Meteo (no account)\n" +
                    "• On the lock screen, calls go only to the list you chose\n" +
                    "• Opening an app from the lock screen asks you to unlock first\n" +
                    "• App data backup is off"
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
        // Nayi version aayi ho toh batao (6 ghante me ek baar, chupchaap)
        Updater.check(this, userAsked = false) { refreshCards() }
        status.text = LillyBus.lastStatus.ifBlank { "Starting up…" }
        heard.text = LillyBus.lastHeard
        orb.state = LillyBus.lastOrb
        onRunning(LillyBus.running)
        refreshCards()
        if (hasMic() && prefs.getBoolean("enabled", true) && !LillyBus.running) startEngine()
        if (missingPermissions().isEmpty()) {
            askSpecialPermissions()
            // Sabse zaroori setup: iske bina orb apne aap nahi khulta
            if (!setupShown && !Settings.canDrawOverlays(this)) {
                setupShown = true
                appsAnywhere()
            }
        }
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
            status.text = "Lilly couldn't start: ${e.message}"
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
        // Mausam aur "good morning" ke liye (sirf andaazan jagah)
        wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        return wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun askPermissions() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasMic()) startEngine() else status.text = "Lilly needs the Microphone permission to hear you"
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
