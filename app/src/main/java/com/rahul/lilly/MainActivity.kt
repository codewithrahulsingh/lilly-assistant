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
    private lateinit var matchValue: TextView
    private lateinit var abuseValue: TextView
    private lateinit var musicValue: TextView
    private lateinit var spotifyConnValue: TextView
    private lateinit var mediaCtlValue: TextView
    private lateinit var headValue: TextView
    private lateinit var smartValue: TextView
    private lateinit var routinesValue: TextView
    private lateinit var upiValue: TextView

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
        if (prefs.getBoolean("ui_classic", false)) buildClassicUi() else buildModernUi()
    }

    /** Purana design (v8.2 tak) — Settings > Appearance se wapas aa sakte hain */
    private fun buildClassicUi() {
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

        val r0 = row(col)
        matchValue = card(r0, "🔐", "Only my voice") { voiceMatchSetup() }
        card(r0, "🧪", "Test voice match") { testVoiceMatch() }.text = "Say \"Lilly\" and see the score"

        val r1 = row(col)
        lockValue = card(r1, "🔒", "Lock screen call") { chooseLockMode() }
        card(r1, "🎙", "Choose voice") { chooseRealVoice() }
            .text = "Pick one — it stays fixed"

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
        card(r12, "📴", "Use without internet") { offlineHelp() }.text = "What works offline + setup"

        val r13 = row(col)
        abuseValue = card(r13, "🤬", "Gaali mode") { abuseSetup() }
        card(r13, "🗣", "My spellings") { showSpellings() }.text = "Lilly learns & fixes words"

        val r14 = row(col)
        musicValue = card(r14, "🎵", "Music app") { chooseMusicApp() }
        card(r14, "▶️", "Which apps play by voice") { musicHelp() }.text = "No helper needed"

        val r15 = row(col)
        spotifyConnValue = card(r15, "🟢", "Connect Spotify") { connectSpotify() }
        card(r15, "🎧", "Test Spotify play") { testSpotifyPlay() }.text = "Plays Kesariya"

        val r19 = row(col)
        routinesValue = card(r19, "⚡", "Routines & habits") { routinesSetup() }
        upiValue = card(r19, "💳", "UPI contacts") { upiSetup() }

        val r18 = row(col)
        smartValue = card(r18, "✍️", "Smart listening") { smartSetup() }

        val r17 = row(col)
        headValue = card(r17, "🎧", "Headphone mode") { headphoneSetup() }
        card(r17, "💬", "Reply without opening") { replyHelp() }.text = "WhatsApp & more"

        val r16 = row(col)
        mediaCtlValue = card(r16, "🎛", "Music control") { mediaCtlSetup() }
        card(r16, "🎶", "Song name now") { songNow() }.text = "Shows what's playing"

        val r20 = row(col)
        card(r20, "✨", "New design") { switchDesign(classic = false) }.text = "Tap to try the new look"

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

    // ================= Naya design (v8.4): Night glass + Live board =================

    private var pages: List<View> = emptyList()
    private var currentTab = 0
    private var youBubble: TextView? = null
    private var dockHome: IconView? = null
    private var dockGear: IconView? = null
    /** Home ke do widget — dabaa ke rakho toh badal sakte ho kya dikhe */
    private class Widget(val pref: String, val def: String, val card: LinearLayout, val icon: TextView, val title: TextView, val num: TextView, val sub: TextView)
    private val widgets = mutableListOf<Widget>()
    private val widgetTypes = listOf("missed" to "Missed calls", "messages" to "New messages", "alarm" to "Next alarm",
        "battery" to "Battery", "event" to "Next event", "date" to "Today")
    private var npTitle: TextView? = null
    private var npSub: TextView? = null
    private var npIcon: android.widget.ImageView? = null
    private var npPlay: IconView? = null
    private var powerDot: View? = null
    private val switchBinds = mutableListOf<Pair<GlowSwitch, () -> Boolean>>()
    private val tileBinds = mutableListOf<Pair<LinearLayout, () -> Boolean>>()
    private val live = android.os.Handler(android.os.Looper.getMainLooper())
    private val liveTick = object : Runnable {
        override fun run() { refreshLive(); live.postDelayed(this, 2500) }
    }

    private fun tf(w: Int) = Ui.font(this, w)

    /** Material icon (naam se) */
    private fun sym(name: String, size: Float, color: Int) = TextView(this).apply {
        text = Ui.SYM[name] ?: ""
        typeface = Ui.icons(this@MainActivity)
        textSize = size; setTextColor(color); gravity = Gravity.CENTER; includeFontPadding = false
    }

    /** App jaisa icon: rang bhara gol-kona squircle + safed nishaan */
    private fun appTile(name: String, tint: Int, sizeDp: Int): View {
        val hsv = FloatArray(3); Color.colorToHSV(tint, hsv)
        val light = Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 0.75f), minOf(1f, hsv[2] * 1.3f + 0.1f)))
        return sym(name, sizeDp * 0.56f, Color.WHITE).apply {
            background = android.graphics.drawable.LayerDrawable(arrayOf(
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(light, tint)).apply { cornerRadius = dp(sizeDp) * 0.28f },
                GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(50, 255, 255, 255), Color.TRANSPARENT)).apply { cornerRadius = dp(sizeDp) * 0.28f }
            )).apply { setLayerInset(1, 0, 0, 0, dp(sizeDp) / 2) }
        }
    }

    private fun txt(text: String, size: Float, color: Int, weight: Int = 400) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); typeface = tf(weight)
        includeFontPadding = false
        setLineSpacing(0f, 1.12f)
    }

    private fun glassBg(radius: Int, fill: Int = Ui.CARD, edge: Int = Ui.EDGE) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat(); setColor(fill); setStroke(dp(1), edge)
    }

    private fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
            false
        }
    }

    private fun appIcon(pkg: String): android.graphics.drawable.Drawable? =
        try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    private fun buildModernUi() {
        window.statusBarColor = Ui.INK
        window.navigationBarColor = Ui.INK
        val root = android.widget.FrameLayout(this)
        root.addView(AuroraView(this), android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val holder = android.widget.FrameLayout(this)
        root.addView(holder, android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        switchBinds.clear(); tileBinds.clear()
        pages = listOf(homePage(), settingsPage())
        pages.forEach { holder.addView(it, android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
        // Neeche dhundhla parda: scroll hota content dock ke peeche dheere gayab ho
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(250, 7, 4, 15), Color.argb(200, 7, 4, 15), Color.argb(0, 7, 4, 15)))
            isClickable = false
        }, android.widget.FrameLayout.LayoutParams(MATCH_PARENT, dp(124), Gravity.BOTTOM))
        root.addView(dock(), android.widget.FrameLayout.LayoutParams(MATCH_PARENT, dp(72)).apply {
            gravity = Gravity.BOTTOM
            setMargins(dp(22), 0, dp(22), dp(18))
        })
        setContentView(root)
        showTab(0, animate = false)
    }

    private fun scrollPage(col: LinearLayout) = ScrollView(this).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        addView(col)
    }

    private fun pageCol() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(120)) // neeche dock ki jagah
    }

    // ---------------- Home ----------------

    private fun homePage(): View {
        val col = pageCol()

        // Upar: namaste + Lilly | sunne ka haal (dabao = ON/OFF)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        names.addView(txt(when (hour) { in 5..11 -> "Good morning, Rahul"; in 12..16 -> "Good afternoon, Rahul"; in 17..21 -> "Good evening, Rahul"; else -> "Hello, Rahul" }, 13f, Ui.DIM))
        names.addView(txt("Lilly", 28f, Color.WHITE, 600).apply { letterSpacing = -0.02f; setPadding(0, dp(4), 0, 0) })
        top.addView(names, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(14), dp(8))
            background = glassBg(20)
            setOnClickListener { togglePower() }
        }
        powerDot = View(this)
        pill.addView(powerDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })
        power = txt("", 12.5f, Color.WHITE, 500)
        pill.addView(power)
        pressable(pill)
        top.addView(pill)
        col.addView(top)

        // Lilly ki baat — bade akshar (Siri jaisa)
        status = txt("", 24f, Color.WHITE, 300).apply { letterSpacing = -0.01f; setPadding(0, dp(22), 0, 0); maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }
        col.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        heard = txt("", 14f, Ui.MIST, 400).apply { setPadding(0, dp(12), 0, 0); visibility = View.GONE }
        youBubble = heard
        col.addView(heard)

        orb = OrbView(this).apply { setOnClickListener { sendToEngine(LillyService.ACTION_ACTIVATE) } }
        orb.minimumHeight = dp(130)
        // Bachi jagah orb le leta hai — screen par scroll nahi aata
        col.addView(orb, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(4); bottomMargin = dp(4) })

        // Bolne layak baatein (dabao = jaise boli)
        val chipsScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            // Kinaron par dhundhla ho kar gayab (kat-te nahi)
            isHorizontalFadingEdgeEnabled = true; setFadingEdgeLength(dp(28))
            clipToPadding = false; setPadding(dp(20), 0, dp(20), 0)
        }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Read notifications" to "notifications check karo", "Missed calls" to "missed call kitni hai",
            "Play music" to "gaana chalao", "Alarm at 7 AM" to "subah 7 baje ka alarm lagao", "Today's weather" to "aaj mausam kaisa hai"
        ).forEach { (t, cmd) ->
            val ch = txt(t, 13f, Color.parseColor("#EDE9FE"), 400).apply {
                setPadding(dp(15), dp(10), dp(15), dp(10))
                background = glassBg(20, Color.argb(22, 255, 255, 255), Color.argb(40, 255, 255, 255))
                setOnClickListener { runCommand(cmd) }
            }
            pressable(ch)
            chips.addView(ch, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(8) })
        }
        chipsScroll.addView(chips)
        col.addView(chipsScroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10); leftMargin = -dp(20); rightMargin = -dp(20) })

        // Live board: 2 widget (lamba dabao = badlo)
        widgets.clear()
        val two = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        two.addView(widgetCard("widget_left", "missed"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(5) })
        two.addView(widgetCard("widget_right", "messages"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { leftMargin = dp(5) })
        col.addView(two, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(18) })

        col.addView(nowPlayingCard(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        return scrollPage(col)
    }

    private fun widgetCard(pref: String, def: String): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(24, Color.argb(150, 18, 14, 30), Color.argb(36, 255, 255, 255))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val ic = sym("call", 16f, Ui.DIM)
        head.addView(ic, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(6) })
        val title = txt("", 12f, Ui.DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        head.addView(title)
        c.addView(head)
        val n = txt("–", 30f, Color.WHITE, 600).apply { letterSpacing = -0.03f; setPadding(0, dp(10), 0, dp(2)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        c.addView(n)
        val s = txt("", 12f, Ui.MIST).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        c.addView(s)
        val w = Widget(pref, def, c, ic, title, n, s)
        widgets.add(w)
        c.setOnClickListener { widgetTap(type(w)) }
        c.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            chooseWidget(w); true
        }
        pressable(c)
        fillWidget(w)
        return c
    }

    private fun type(w: Widget) = prefs.getString(w.pref, w.def) ?: w.def

    private fun chooseWidget(w: Widget) {
        val cur = widgetTypes.indexOfFirst { it.first == type(w) }
        AlertDialog.Builder(this)
            .setTitle("Show in this widget")
            .setSingleChoiceItems(widgetTypes.map { it.second }.toTypedArray(), cur) { d, which ->
                prefs.edit().putString(w.pref, widgetTypes[which].first).apply()
                d.dismiss()
                fillWidget(w)
                if (widgetTypes[which].first == "event" && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 78)
            }.show()
    }

    private fun widgetTap(t: String) {
        when (t) {
            "missed" -> if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 77) else runCommand("missed call kitni hai")
            "messages" -> if (!prefs.getBoolean("read_notifs", false) || !MediaCtl.allowed(this)) notifSetup() else runCommand("notifications check karo")
            "alarm" -> tryStart(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
            "battery" -> tryStart(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            "event", "date" -> if (t == "event" && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 78)
                else tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis())))
        }
    }

    private fun fillWidget(w: Widget) {
        val t = type(w)
        w.title.text = widgetTypes.firstOrNull { it.first == t }?.second ?: ""
        w.icon.text = Ui.SYM[when (t) { "missed" -> "call"; "messages" -> "chat"; "alarm" -> "alarm"; "battery" -> "battery_full"; "event" -> "event"; else -> "today" }] ?: ""
        w.num.textSize = 30f
        when (t) {
            "missed" -> if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                val names = ArrayList<String>()
                var n = 0
                try {
                    contentResolver.query(android.provider.CallLog.Calls.CONTENT_URI,
                        arrayOf(android.provider.CallLog.Calls.CACHED_NAME, android.provider.CallLog.Calls.NUMBER),
                        "${android.provider.CallLog.Calls.TYPE}=? AND ${android.provider.CallLog.Calls.DATE}>?",
                        arrayOf(android.provider.CallLog.Calls.MISSED_TYPE.toString(), (System.currentTimeMillis() - 86_400_000L).toString()),
                        android.provider.CallLog.Calls.DATE + " DESC")?.use { cur ->
                        while (cur.moveToNext()) {
                            n++
                            val nm = (cur.getString(0)?.takeIf { it.isNotBlank() } ?: cur.getString(1) ?: "").split(" ").first()
                            if (names.size < 3 && nm.isNotBlank() && nm !in names) names.add(nm)
                        }
                    }
                } catch (e: Exception) { }
                w.num.text = n.toString()
                w.sub.text = if (n == 0) "None today" else names.joinToString(", ")
            } else { w.num.text = "–"; w.sub.text = "Tap to allow" }
            "messages" -> if (prefs.getBoolean("read_notifs", false) && MediaCtl.allowed(this)) {
                val list = Inbox.recent("", 86_400_000L)
                w.num.text = list.size.toString()
                w.sub.text = if (list.isEmpty()) "All caught up" else list.map { it.app }.distinct().take(3).joinToString(", ")
            } else { w.num.text = "–"; w.sub.text = "Tap to turn on" }
            "alarm" -> {
                val next = try { (getSystemService(ALARM_SERVICE) as android.app.AlarmManager).nextAlarmClock?.triggerTime } catch (e: Exception) { null }
                if (next == null) { w.num.text = "Off"; w.sub.text = "No alarm set" }
                else {
                    w.num.text = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(next))
                    val days = ((next - startOfDay()) / 86_400_000L).toInt()
                    w.sub.text = when (days) { 0 -> "Today"; 1 -> "Tomorrow"; else -> java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.ENGLISH).format(java.util.Date(next)) }
                }
            }
            "battery" -> {
                val i = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val lvl = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val sc = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
                val st = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                w.num.text = if (lvl >= 0) "${lvl * 100 / sc.coerceAtLeast(1)}%" else "–"
                w.sub.text = when (st) {
                    android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    else -> "On battery"
                }
            }
            "event" -> if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                var title: String? = null; var at = 0L
                try {
                    val now = System.currentTimeMillis()
                    val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
                    android.content.ContentUris.appendId(uri, now); android.content.ContentUris.appendId(uri, now + 7 * 86_400_000L)
                    contentResolver.query(uri.build(), arrayOf(android.provider.CalendarContract.Instances.TITLE, android.provider.CalendarContract.Instances.BEGIN),
                        "${android.provider.CalendarContract.Instances.BEGIN}>=?", arrayOf(now.toString()),
                        android.provider.CalendarContract.Instances.BEGIN + " ASC")?.use { c -> if (c.moveToFirst()) { title = c.getString(0); at = c.getLong(1) } }
                } catch (e: Exception) { }
                if (title == null) { w.num.text = "Free"; w.sub.text = "Nothing this week" }
                else {
                    w.num.textSize = 19f
                    w.num.text = title
                    w.sub.text = java.text.SimpleDateFormat("EEE, h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(at))
                }
            } else { w.num.text = "–"; w.sub.text = "Tap to allow calendar" }
            else -> {
                val d = java.util.Date()
                w.num.text = java.text.SimpleDateFormat("d", java.util.Locale.ENGLISH).format(d)
                w.sub.text = java.text.SimpleDateFormat("EEEE, MMM", java.util.Locale.ENGLISH).format(d)
            }
        }
    }

    private fun startOfDay(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun nowPlayingCard(): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(200, 46, 17, 90), Color.argb(150, 18, 14, 30))).apply {
                cornerRadius = dp(24).toFloat(); setStroke(dp(1), Color.argb(46, 216, 180, 254))
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { if (!MediaCtl.allowed(this@MainActivity)) mediaCtlSetup() }
        }
        npIcon = android.widget.ImageView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#F472B6"), Ui.DEEP)).apply { cornerRadius = dp(14).toFloat() }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        c.addView(npIcon, LinearLayout.LayoutParams(dp(52), dp(52)))
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
        npTitle = txt("Nothing playing", 14.5f, Color.WHITE, 500).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        npSub = txt("Say “gaana chalao”", 12f, Ui.DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0) }
        t.addView(npTitle); t.addView(npSub)
        c.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        fun ctl(kind: String, size: Int, action: () -> Unit) = IconView(this, kind, Ui.MIST).apply {
            setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); action() }
        }.also { c.addView(it, LinearLayout.LayoutParams(dp(size), dp(size))) }
        ctl("prev", 34) { media("previous") }
        val play = android.widget.FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                val playing = MediaCtl.current(this@MainActivity)?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                if (MediaCtl.current(this@MainActivity) == null) runCommand("gaana chalao") else media(if (playing) "pause" else "resume")
            }
        }
        npPlay = IconView(this, "play", Color.BLACK)
        play.addView(npPlay, android.widget.FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        c.addView(play, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(6); rightMargin = dp(6) })
        ctl("next", 34) { media("next") }
        pressable(c)
        return c
    }

    private fun media(action: String) {
        if (!MediaCtl.allowed(this)) { mediaCtlSetup(); return }
        val r = MediaCtl.run(this, action, "")
        if (r == null) android.widget.Toast.makeText(this, "Nothing is playing. Say “gaana chalao”.", android.widget.Toast.LENGTH_SHORT).show()
        live.postDelayed({ refreshLive() }, 400)
    }

    /** Home ke live cards: missed calls, messages, chal raha gaana */
    private fun refreshLive() {
        if (widgets.isEmpty()) return
        widgets.forEach { fillWidget(it) }
        // Chal raha gaana
        val ctl = if (MediaCtl.allowed(this)) MediaCtl.current(this) else null
        val md = ctl?.metadata
        val title = md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (ctl != null && title != null) {
            val artist = md!!.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(ctl.packageName, 0)).toString() } catch (e: Exception) { "" }
            npTitle?.text = title
            npSub?.text = listOf(artist, app).filter { it.isNotBlank() }.joinToString(" · ")
            val art = md!!.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            if (art != null) { npIcon?.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.BLACK) }; npIcon?.setPadding(0, 0, 0, 0); npIcon?.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; npIcon?.clipToOutline = true; npIcon?.setImageBitmap(art) }
            else { npIcon?.background = null; npIcon?.setPadding(dp(2), dp(2), dp(2), dp(2)); npIcon?.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; npIcon?.setImageDrawable(appIcon(ctl.packageName)) }
            npPlay?.set(if (ctl.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) "pause" else "play")
        } else {
            npTitle?.text = "Nothing playing"
            npSub?.text = if (MediaCtl.allowed(this)) "Tap play or say “gaana chalao”" else "Tap to allow music control"
            // Sirf Spotify ka logo — peeche ka rangin dabba nahi
            npIcon?.background = null
            npIcon?.setPadding(dp(2), dp(2), dp(2), dp(2))
            npIcon?.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            npIcon?.setImageDrawable(appIcon("com.spotify.music"))
            npPlay?.set("play")
        }
    }

    private fun togglePower() {
        if (LillyBus.running) {
            prefs.edit().putBoolean("enabled", false).apply()
            LillyService.stop(this)
        } else {
            prefs.edit().putBoolean("enabled", true).apply()
            if (hasMic()) startEngine() else askPermissions()
        }
    }

    /** Button dabao = jaise wahi bola */
    private fun runCommand(text: String) {
        if (!hasMic()) { askPermissions(); return }
        prefs.edit().putBoolean("enabled", true).apply()
        try { LillyService.start(this, LillyService.ACTION_RUN, text) } catch (e: Exception) { }
    }

    private fun songNow() {
        val n = if (MediaCtl.allowed(this)) MediaCtl.nowPlaying(this) else null
        android.widget.Toast.makeText(this, n ?: "Nothing playing (or Music control is off)", android.widget.Toast.LENGTH_LONG).show()
    }

    // ---------------- Dock ----------------

    private var dockHomeBox: TextView? = null
    private var dockGearBox: TextView? = null
    private var dockOrb: OrbView? = null

    private fun dock(): View {
        val bar = android.widget.FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(200, 38, 24, 70), Color.argb(230, 20, 12, 40))).apply {
                cornerRadius = dp(36).toFloat(); setStroke(dp(1), Color.argb(55, 216, 180, 254))
            }
            elevation = dp(10).toFloat()
        }
        fun tab(name: String, g: Int, tap: () -> Unit) = sym(name, 27f, Color.WHITE).apply {
            setOnClickListener { tap() }
        }.also {
            bar.addView(it, android.widget.FrameLayout.LayoutParams(dp(64), dp(48), g or Gravity.CENTER_VERTICAL).apply {
                if (g == Gravity.START) leftMargin = dp(24) else rightMargin = dp(24)
            })
        }
        dockHomeBox = tab("home", Gravity.START) { showTab(0) }
        dockGearBox = tab("tune", Gravity.END) { showTab(1) }
        // Beech me wahi ghoomta orb (upar wale jaisa) — dabao = Lilly sune
        dockOrb = OrbView(this).apply {
            contentDescription = "Talk to Lilly"
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                showTab(0); sendToEngine(LillyService.ACTION_ACTIVATE)
            }
        }
        pressable(dockOrb!!)
        bar.addView(dockOrb, android.widget.FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER))
        bar.clipChildren = false
        return bar
    }

    private fun showTab(i: Int, animate: Boolean = true) {
        if (pages.isEmpty()) return
        val changed = currentTab != i
        currentTab = i
        pages.forEachIndexed { k, p ->
            if (k == i) {
                p.visibility = View.VISIBLE
                if (animate && changed) { p.alpha = 0f; p.animate().alpha(1f).setDuration(180).start() }
            } else p.visibility = View.GONE
        }
        dockHomeBox?.setTextColor(if (i == 0) Color.WHITE else Ui.DIM)
        dockGearBox?.setTextColor(if (i == 1) Color.WHITE else Ui.DIM)
        val pillOn = { GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.argb(34, 255, 255, 255)) } }
        dockHomeBox?.background = if (i == 0) pillOn() else null
        dockGearBox?.background = if (i == 1) pillOn() else null
        if (animate && changed) window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (pages.isNotEmpty() && currentTab != 0) showTab(0) else super.onBackPressed()
    }

    // ---------------- Settings ----------------

    private fun groupTitle(col: LinearLayout, t: String) {
        col.addView(txt(t, 13f, Ui.DIM, 500).apply { setPadding(dp(6), dp(24), 0, dp(10)) })
    }

    private fun group(col: LinearLayout, title: String): LinearLayout {
        groupTitle(col, title)
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(22, Color.argb(150, 18, 14, 30), Color.argb(26, 255, 255, 255))
            setPadding(0, dp(2), 0, dp(2))
        }
        col.addView(g, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return g
    }

    /**
     * Ek line: icon · naam · haal · (switch ya ›). value TextView lautata hai.
     * icon = emoji ya app ka package (asli app icon dikhega)
     */
    private fun item(
        g: LinearLayout, icon: String, tint: Int, title: String,
        toggle: Pair<() -> Boolean, (Boolean) -> Unit>? = null, onTap: () -> Unit
    ): TextView {
        if (g.childCount > 0) g.addView(View(this).apply { setBackgroundColor(Color.argb(18, 255, 255, 255)) },
            LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { leftMargin = dp(60) })
        val value = txt("", 12f, Ui.DIM).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0) }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minimumHeight = dp(60)
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(40, 192, 132, 252)), null,
                GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(20).toFloat() })
            setOnClickListener { onTap() }
        }
        val pkgIcon = if (icon.contains('.')) appIcon(icon) else null
        if (pkgIcon != null) {
            r.addView(android.widget.ImageView(this).apply { setImageDrawable(pkgIcon) }, LinearLayout.LayoutParams(dp(38), dp(38)))
        } else {
            r.addView(appTile(if (icon.contains('.')) "queue_music" else icon, tint, 38), LinearLayout.LayoutParams(dp(38), dp(38)))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(10), 0) }
        texts.addView(txt(title, 14.5f, Color.WHITE))
        texts.addView(value)
        r.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (toggle != null) {
            val sw = GlowSwitch(this, toggle.first()) { on -> toggle.second(on); refreshCards() }
            switchBinds.add(sw to toggle.first)
            r.addView(sw)
        } else r.addView(IconView(this, "chev", Color.argb(110, 255, 255, 255), 2f), LinearLayout.LayoutParams(dp(16), dp(16)))
        g.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return value
    }

    /** Upar ki badi ON/OFF tiles */
    private fun tile(row: LinearLayout, icon: String, title: String, state: () -> Boolean, set: (Boolean) -> Unit, onTap: () -> Unit): TextView {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            minimumHeight = dp(118)
            setOnClickListener { onTap() }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(sym(icon, 26f, Color.WHITE).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val sw = GlowSwitch(this, state()) { on -> set(on); refreshCards() }
        switchBinds.add(sw to state)
        head.addView(sw)
        t.addView(head)
        t.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        t.addView(txt(title, 14.5f, Color.WHITE, 500).apply { setPadding(0, dp(14), 0, 0) })
        val value = txt("", 11.5f, Ui.MIST).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0) }
        t.addView(value)
        tileBinds.add(t to state)
        pressable(t)
        row.addView(t, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
        return value
    }

    private fun paintTile(t: LinearLayout, on: Boolean) {
        t.background = if (on) GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(150, 139, 92, 246), Color.argb(80, 232, 121, 249))).apply {
            cornerRadius = dp(24).toFloat(); setStroke(dp(1), Color.argb(100, 233, 213, 255))
        } else glassBg(24, Color.argb(150, 18, 14, 30), Color.argb(30, 255, 255, 255))
    }

    private fun flip(key: String, def: Boolean) = Pair({ prefs.getBoolean(key, def) }, { on: Boolean -> prefs.edit().putBoolean(key, on).apply() })

    private fun settingsPage(): View {
        val col = pageCol()
        col.addView(txt("Settings", 30f, Color.WHITE, 300).apply { letterSpacing = -0.02f; setPadding(dp(4), dp(10), 0, dp(14)) })

        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(r1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { leftMargin = -dp(5); rightMargin = -dp(5) })
        col.addView(r2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { leftMargin = -dp(5); rightMargin = -dp(5) })
        matchValue = tile(r1, "fingerprint", "Only my voice", { VoiceMatch.active(this) }, { on ->
            if (on && !(VoiceMatch.voicePrint(this).ready && VoiceMatch.modelReady(this))) voiceMatchSetup()
            else VoiceMatch.setOn(this, on)
        }) { voiceMatchSetup() }
        val (sg, ss) = flip("smart_listen", true)
        smartValue = tile(r1, "hearing", "Smart listening", sg, ss) { smartSetup() }
        val (ag, aset) = flip("abuse_mode", false)
        abuseValue = tile(r2, "sentiment_very_dissatisfied", "Gaali mode", ag, aset) { abuseSetup() }
        headValue = tile(r2, "headphones", "Headphone mode", { prefs.getBoolean("headphone_mode", true) },
            { on -> prefs.edit().putBoolean("headphone_mode", on).apply() }) { headphoneSetup() }

        val v = group(col, "Voice")
        realValue = item(v, "record_voice_over", Color.parseColor("#6D28D9"), "Lilly's voice") { chooseRealVoice() }
        item(v, "play_circle", Color.parseColor("#7C3AED"), "Hear voice") { sendToEngine(LillyService.ACTION_TEST_VOICE) }.text = "Test the current voice"
        voiceValue = item(v, "download", Color.parseColor("#4C1D95"), "Natural voice pack") { downloadVoice() }
        wakeValue = item(v, "hearing", Color.parseColor("#7C3AED"), "Wake-up sensitivity") { chooseSensitivity() }
        langValue = item(v, "translate", Color.parseColor("#9333EA"), "Listening language") { chooseLanguage() }
        item(v, "science", Color.parseColor("#5B21B6"), "Test voice match") { testVoiceMatch() }.text = "Say \"Lilly\" and see the score"
        item(v, "spellcheck", Color.parseColor("#6D28D9"), "My spellings") { showSpellings() }.text = "Lilly learns & fixes words"

        val m = group(col, "Music")
        spotifyConnValue = item(m, "com.spotify.music", Color.parseColor("#15803D"), "Spotify") { connectSpotify() }
        val musicPkg = musicApps.firstOrNull { it.first == (prefs.getString("music_app", "") ?: "") }?.third ?: ""
        musicValue = item(m, musicPkg.ifBlank { "queue_music" }, Color.parseColor("#16A34A"), "Music app") { chooseMusicApp() }
        mediaCtlValue = item(m, "graphic_eq", Color.parseColor("#0F766E"), "Music control") { mediaCtlSetup() }
        item(m, "music_note", Color.parseColor("#166534"), "Test Spotify play") { testSpotifyPlay() }.text = "Plays Kesariya"
        item(m, "help", Color.parseColor("#334155"), "Which apps play by voice") { musicHelp() }.text = "No helper needed"

        val c = group(col, "Calls & messages")
        val (ng, nset) = flip("read_notifs", false)
        notifValue = item(c, "notifications", Color.parseColor("#2563EB"), "Read my notifications", ng to { on: Boolean ->
            nset(on); if (on && !MediaCtl.allowed(this)) notifSetup()
        }) { notifSetup() }
        item(c, "com.whatsapp", Color.parseColor("#15803D"), "Reply without opening") { replyHelp() }.text = "WhatsApp & more"
        scriptValue = item(c, "keyboard", Color.parseColor("#1D4ED8"), "WhatsApp typing") { chooseScript() }
        lockValue = item(c, "lock", Color.parseColor("#1E40AF"), "Lock screen call") { chooseLockMode() }
        upiValue = item(c, "credit_card", Color.parseColor("#D97706"), "UPI contacts") { upiSetup() }

        val a = group(col, "Automation")
        routinesValue = item(a, "bolt", Color.parseColor("#D97706"), "Routines & habits") { routinesSetup() }
        placesValue = item(a, "place", Color.parseColor("#B45309"), "Home, office & city") { showPlaces() }
        allowedValue = item(a, "check_circle", Color.parseColor("#15803D"), "Apps Lilly can open") { chooseApps() }
        spotifyValue = item(a, "smart_toy", Color.parseColor("#475569"), "Lilly helper") { spotifySetup() }

        val ai = group(col, "AI & memory")
        aiValue = item(ai, "auto_awesome", Color.parseColor("#BE185D"), "AI chat") { aiSetup() }
        ai2Value = item(ai, "power", Color.parseColor("#9D174D"), "Second AI") { ai2Setup() }
        memoryValue = item(ai, "edit_note", Color.parseColor("#DB2777"), "What Lilly remembers") { showMemory() }
        learnedValue = item(ai, "psychology", Color.parseColor("#A21CAF"), "What Lilly learned") { showLearned() }

        val p = group(col, "Phone & permissions")
        appsValue = item(p, "smartphone", Color.parseColor("#334155"), "Orb on every screen + apps") { appsAnywhere() }
        lockPopupValue = item(p, "lock_open", Color.parseColor("#334155"), "Orb on lock screen") { lockPopupSetup() }
        batteryValue = item(p, "battery_charging_full", Color.parseColor("#334155"), "Run in background") { batterySettings() }
        item(p, "notifications_off", Color.parseColor("#334155"), "Hide notification") { hideNotification() }.text = "Tap to turn it off"

        val h = group(col, "Help")
        item(h, "lightbulb", Color.parseColor("#7C3AED"), "What you can say") { showHelp() }.text = "All commands"
        item(h, "wifi_off", Color.parseColor("#475569"), "Use without internet") { offlineHelp() }.text = "What works offline + setup"
        item(h, "shield", Color.parseColor("#0369A1"), "Security") { showSecurity() }.text = "How safe Lilly is"
        updateValue = item(h, "system_update", Color.parseColor("#6D28D9"), "Update Lilly") { Updater.check(this, userAsked = true) { refreshCards() } }
        item(h, "palette", Color.parseColor("#9333EA"), "Design") { chooseDesign() }.text = "New — tap to go back to the old look"

        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        col.addView(txt("Lilly $ver", 12f, Ui.DIM).apply { gravity = Gravity.CENTER; setPadding(0, dp(26), 0, 0) },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return scrollPage(col)
    }

    /** Switch aur tiles ko asli haal ke saath milao */
    private fun refreshSwitches() {
        switchBinds.forEach { (sw, get) -> sw.setChecked(get()) }
        tileBinds.forEach { (t, get) -> paintTile(t, get()) }
    }

    // ---------------- Design badalna ----------------

    private fun chooseDesign() {
        AlertDialog.Builder(this)
            .setTitle("Design")
            .setSingleChoiceItems(arrayOf("New", "Classic (old, all cards on one page)"),
                if (prefs.getBoolean("ui_classic", false)) 1 else 0) { d, w ->
                d.dismiss()
                switchDesign(classic = w == 1)
            }.show()
    }

    private fun switchDesign(classic: Boolean) {
        prefs.edit().putBoolean("ui_classic", classic).apply()
        recreate()
    }

    private fun refreshCards() {
        aiValue.text = if (prefs.getString("ai_key", "").isNullOrBlank()) "OFF — turn on to get answers to anything" else "ON ✓"
        lockPopupValue.text = if (canFullScreen()) "Allowed ✓" else "Tap to allow"
        val key = prefs.getString("ai_key", "") ?: ""
        val rv = prefs.getString("real_voice", RealVoice.DEFAULT) ?: RealVoice.DEFAULT
        val src = prefs.getString("voice_source", null)
        realValue.text = when {
            src == "cartesia" && !prefs.getString("cartesia_key", "").isNullOrBlank() -> {
                val lines = CommonLines.ALL.map { if (prefs.getBoolean("cartesia_male", false)) Persona.masculine(it) else it }.distinct()
                val n = RealVoice(this).keptCount(lines)
                "🔒 ${prefs.getString("cartesia_name", "Cartesia")} (Cartesia) — ${n * 100 / lines.size.coerceAtLeast(1)}% lines saved"
            }
            src == "gemini" && !(key.isBlank() || key.startsWith("sk-ant-")) && rv != RealVoice.OFF -> {
                val lines = CommonLines.ALL.map { if (RealVoice.isMale(rv)) Persona.masculine(it) else it }.distinct()
                val n = RealVoice(this).keptCount(lines)
                "🔒 $rv — ${n * 100 / lines.size.coerceAtLeast(1)}% of everyday lines saved on phone"
            }
            OfflineVoice.installed(this) && src != "phone" && OfflineVoice.lastError != null -> "⚠️ Problem — tap to see"
            OfflineVoice.installed(this) && src != "phone" -> "🔒 ${OfflineVoice.chosen(this)?.name} — fixed, works offline"
            !OfflineVoice.installed(this) -> "Tap to download Lilly's human voice"
            src == "phone" -> "Phone voice"
            key.isBlank() || key.startsWith("sk-ant-") -> "Tap to get a human voice"
            rv == RealVoice.OFF -> "Phone voice"
            else -> "Gemini voice: $rv (online)"
        }
        val mem = memory()
        val nf = mem.facts().size
        val na = mem.answerCount()
        memoryValue.text = if (nf == 0 && na == 0) "Nothing yet — tap to see how" else "$nf things about you · $na saved answers"
        routinesValue.text = "${jsonCount("routines")} routines · habits ${if (prefs.getBoolean("habit_suggest", true)) "ON" else "OFF"}"
        upiValue.text = "${jsonCount("upi_ids")} saved — \"Rahul ko 500 bhejo\""
        smartValue.text = if (prefs.getBoolean("smart_listen", true)) "ON ✓ writes exactly what you say" else "OFF — phone's own recognizer"
        headValue.text = when {
            !prefs.getBoolean("headphone_mode", true) -> "OFF"
            prefs.getBoolean("headphone_music", false) -> "ON ✓ plays music when plugged in"
            else -> "ON ✓ asks what to do when plugged in"
        }
        mediaCtlValue.text = if (MediaCtl.allowed(this)) "ON ✓ all music apps" else "Tap to allow (no helper needed)"
        spotifyConnValue.text = if (SpotifyRemote.authorized(this)) "Connected ✓ plays songs directly" else "Tap once — then songs play directly"
        musicValue.text = musicApps.firstOrNull { it.first == (prefs.getString("music_app", "") ?: "") }?.second?.let { "$it" } ?: "Auto (last one you used)"
        abuseValue.text = if (prefs.getBoolean("abuse_mode", false)) "ON 😈 swears back at you" else "OFF — polite replies"
        matchValue.text = when {
            VoiceMatch.active(this) -> "ON ✓ wakes only for you (" + VoiceMatch.level(this).replaceFirstChar { it.uppercase() } + ")"
            VoiceMatch.voicePrint(this).ready -> "OFF — tap to turn on"
            else -> "Tap to teach Lilly your voice"
        }
        wakeValue.text = sensLabel(prefs.getString("wake_sens", "normal") ?: "normal")
        val helper = LillyAccess.enabled(this)
        spotifyValue.text = when {
            LillyAccess.autoMode(this) -> "AUTO ✓ on only when needed — payments OK"
            helper -> "ON ✓ Spotify, WhatsApp send, speaker"
            else -> "OFF — tap to turn on"
        }
        notifValue.text = when {
            !prefs.getBoolean("read_notifs", false) -> "OFF"
            !MediaCtl.allowed(this) && !helper -> "Needs Music control — tap"
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
        refreshSwitches()
        refreshLive()
    }

    override fun onStatus(text: String) = runOnUiThread { status.text = text }
    override fun onHeard(text: String) = runOnUiThread {
        heard.text = text
        if (youBubble != null) {
            val empty = text.isBlank() || Regex("""^\W*(nothing|kuch nahi)?\W*$""", RegexOption.IGNORE_CASE).matches(text)
            heard.visibility = if (empty) View.GONE else View.VISIBLE
            heard.text = if (text.isBlank() || text.startsWith("You said")) text else "You said " + text.trim()
        }
    }
    override fun onOrb(state: OrbView.State) = runOnUiThread { orb.state = state; dockOrb?.state = state }
    override fun onLevel(level: Float) { orb.level = level; dockOrb?.level = level }
    override fun onRunning(running: Boolean) = runOnUiThread {
        powerDot?.let { dot ->
            power.text = if (running) "Listening" else "Off — tap to start"
            dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (running) Color.parseColor("#4ADE80") else Color.parseColor("#6B6488")) }
            return@runOnUiThread
        }
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
                    "3. Come back and use \"Choose voice\" to listen to the voices and pick your favourite\n\n" +
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

    /** Awaaz kahan se: offline (hamesha same) / Gemini (online) / phone */
    private fun chooseRealVoice() {
        val cur = OfflineVoice.chosen(this)
        val cn = if (prefs.getString("voice_source", null) == "cartesia") prefs.getString("cartesia_name", null) else null
        val labels = arrayOf(
            "⭐ Cartesia voices — Siya & 30+ Hindi/Hinglish voices" + (cn?.let { " · now: $it" } ?: ""),
            "Gemini human voices (paused for now)",
            "Offline voices — Priyamvada / Pratham (paused for now)" + (cur?.let { " · ${it.name} downloaded" } ?: ""),
            "Phone voice — the phone's own text-to-speech"
        )
        AlertDialog.Builder(this)
            .setTitle("🎤 Lilly's voice")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> cartesiaSetup()
                    1 -> chooseGeminiVoice()
                    2 -> pickOfflineVoice()
                    else -> useVoice("phone")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** 5 insaani awaazein. Sun ke chuno — "Keep" dabate hi wahi pakki, Lilly khud kabhi nahi badalti */
    private fun pickOfflineVoice() {
        val cur = OfflineVoice.chosen(this)
        val list = OfflineVoice.CHOICES
        val labels = list.map { v ->
            val mark = if (cur?.id == v.id && prefs.getString("voice_source", null) != "phone") "🔒 " else ""
            val dl = if (OfflineVoice.packInstalled(this, v.pack)) "" else "  (download ${OfflineVoice.packMb(v.pack)} MB once)"
            "$mark${v.name} — ${v.about}$dl"
        }.toTypedArray()
        val b = AlertDialog.Builder(this)
            .setTitle("🎤 Pick Lilly's voice")
            .setItems(labels) { _, which ->
                val v = list[which]
                if (OfflineVoice.packInstalled(this, v.pack)) previewVoice(v) else downloadOfflineVoice(v)
            }
            .setNegativeButton("Close", null)
        val installedPacks = list.map { it.pack }.distinct().filter { OfflineVoice.packInstalled(this, it) }
        if (installedPacks.isNotEmpty()) b.setNeutralButton("Free up space") { _, _ -> deleteVoicePacks(installedPacks) }
        b.show()
    }

    private fun deleteVoicePacks(packs: List<String>) {
        val names = packs.map { p -> (OfflineVoice.CHOICES.firstOrNull { it.pack == p }?.name ?: p) + " (${OfflineVoice.packMb(p)} MB)" }
        AlertDialog.Builder(this).setTitle("Delete which voice download?")
            .setItems(names.toTypedArray()) { _, w ->
                OfflineVoice.delete(this, packs[w])
                if (!OfflineVoice.installed(this)) prefs.edit().remove("voice_source").apply()
                refreshCards()
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** Ye awaaz suna do, phir "Keep" ya "Another" */
    private fun previewVoice(v: OfflineVoice.VoiceChoice) {
        val wait = AlertDialog.Builder(this).setTitle("🎧 ${v.name}")
            .setMessage("Lilly is speaking… (the first time can take a few seconds)")
            .setCancelable(false).show()
        val line = if (v.male) "नमस्ते राहुल, मैं आपकी मदद के लिए हमेशा तैयार रहूँगा। बताइए, क्या करना है? Hi Rahul, I'm ready."
        else "नमस्ते राहुल, मैं लिली हूँ। मैं आपकी मदद के लिए हमेशा तैयार रहूँगी। Hi Rahul, I'm Lilly."
        OfflineVoice.speak(this, line, prefs.getFloat("voice_speed", 1.0f),
            onDone = {
                try { wait.dismiss() } catch (e: Exception) { }
                if (isFinishing) return@speak
                AlertDialog.Builder(this).setTitle("Keep ${v.name}?")
                    .setMessage("If you keep this voice, Lilly will always speak in it" +
                        (if (v.male) " and talk like a boy (karunga, raha hoon)." else " and talk like a girl (karungi, rahi hoon).") +
                        " It won't change by itself — only if you pick a different one here.")
                    .setPositiveButton("Keep ${v.name} 🔒") { _, _ ->
                        OfflineVoice.choose(this, v.id)
                        prefs.edit().putString("voice_source", "offline").apply()
                        refreshCards()
                        android.widget.Toast.makeText(this, "${v.name} is now Lilly's voice 🔒", android.widget.Toast.LENGTH_LONG).show()
                    }
                    .setNeutralButton("Hear again") { _, _ -> previewVoice(v) }
                    .setNegativeButton("Try another") { _, _ -> pickOfflineVoice() }
                    .show()
            },
            onFail = {
                try { wait.dismiss() } catch (e: Exception) { }
                if (isFinishing) return@speak
                AlertDialog.Builder(this).setTitle("${v.name} didn't play ✗")
                    .setMessage("Reason:\n${OfflineVoice.lastError ?: "unknown"}\n\nPlease send a screenshot of this.")
                    .setPositiveButton("Download again") { _, _ -> downloadOfflineVoice(v) }
                    .setNegativeButton("Close", null).show()
            },
            voiceId = v.id)
    }

    /** Payment apps ke liye helper band */
    private fun turnOffHelper() {
        val off = LillyAccess.turnOff()
        refreshCards()
        if (off) {
            AlertDialog.Builder(this)
                .setTitle("Helper is off ✓")
                .setMessage(
                    "Payment and bank apps should work now. If one still shows the warning, close it fully (remove from recent apps) and open it again.\n\n" +
                        "Lilly still does everything else. Spotify will only search, and WhatsApp will fill in your message for you to tap Send."
                )
                .setPositiveButton("OK", null).show()
        } else {
            android.widget.Toast.makeText(this, "Turn off \"Lilly helper\" here", android.widget.Toast.LENGTH_LONG).show()
            tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun useVoice(src: String) {
        prefs.edit().putString("voice_source", src).apply()
        refreshCards()
        sendToEngine(LillyService.ACTION_TEST_VOICE)
    }

    private fun downloadOfflineVoice(v: OfflineVoice.VoiceChoice = OfflineVoice.CHOICES.first()) {
        val mb = OfflineVoice.packMb(v.pack)
        val text = label("Starting…", 15f, Color.WHITE).apply { setPadding(dp(24), dp(12), dp(24), dp(4)) }
        val d = AlertDialog.Builder(this)
            .setTitle("Downloading ${v.name}'s voice")
            .setView(text)
            .setCancelable(false)
            .setNegativeButton("Hide", null)
            .show()
        OfflineVoice.download(this, v.pack,
            progress = { p -> text.text = "$p%  (about $mb MB — keep the internet on, Wi-Fi is best)" },
            done = { ok, err ->
                try { d.dismiss() } catch (e: Exception) { }
                if (isFinishing) return@download
                if (ok) previewVoice(v)
                else {
                    AlertDialog.Builder(this).setTitle("Download didn't finish ✗")
                        .setMessage("Reason: $err\n\nCheck the internet and try again.")
                        .setPositiveButton("Try again") { _, _ -> downloadOfflineVoice(v) }
                        .setNegativeButton("Close", null).show()
                }
            })
    }

    private fun chooseGeminiVoice() {
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
        val list = RealVoice.VOICES
        val cur = if (prefs.getString("voice_source", null) == "gemini") prefs.getString("real_voice", null) else null
        val labels = list.map { v ->
            (if (v.name == cur) "🔒 " else "") + (if (v.male) "👨 " else "👩 ") + "${v.name} — ${v.about}"
        }
        AlertDialog.Builder(this)
            .setTitle("Pick Lilly's voice (tap to hear)")
            .setItems(labels.toTypedArray()) { _, which -> previewRealVoice(list[which].name, ask = true) }
            .setNegativeButton("Close", null)
            .setNeutralButton("About") { _, _ ->
                AlertDialog.Builder(this).setTitle("About the voice").setMessage(
                    "• Made by Google Gemini with your free Gemini key — speaks Hindi, English and Hinglish naturally\n" +
                        "• Once you keep a voice it never changes by itself\n" +
                        "• Lilly's everyday lines (\"Yes Rahul, how can I help?\", calling, alarms, torch…) are slowly saved on the phone in this voice, so they play instantly, even without internet\n" +
                        "• New sentences (like AI answers) need internet and take 1–3 seconds\n" +
                        "• If Gemini's free limit runs out or there's no internet, Priyamvada (girl) / Pratham (boy) fill in for new sentences, if downloaded\n" +
                        "• The text Lilly speaks is sent to Google to make the voice"
                ).setPositiveButton("OK", null).show()
            }
            .show()
    }

    // ---------------- Cartesia (Siya…) ----------------

    private fun cartesiaSetup() {
        val key = prefs.getString("cartesia_key", "") ?: ""
        if (key.isBlank()) { askCartesiaKey(); return }
        val wait = AlertDialog.Builder(this).setTitle("Loading Hindi voices…").setMessage("Getting the list from Cartesia").show()
        Thread {
            val voices = ArrayList<org.json.JSONObject>()
            var err = ""
            try {
                var after: String? = null
                for (page in 0 until 3) {
                    val path = "/voices?language=hi&limit=100" + (after?.let { "&starting_after=$it" } ?: "")
                    val (code, bytes, e) = RealVoice.cartesiaCall(key.trim(), path, null)
                    if (code !in 200..299 || bytes == null) { err = "HTTP $code $e"; break }
                    val o = org.json.JSONObject(String(bytes))
                    val arr = o.optJSONArray("data") ?: break
                    for (i in 0 until arr.length()) voices.add(arr.getJSONObject(i))
                    if (!o.optBoolean("has_more", false)) break
                    after = o.optString("next_page").ifBlank { null } ?: break
                }
            } catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            runOnUiThread {
                try { wait.dismiss() } catch (e: Exception) { }
                if (isFinishing) return@runOnUiThread
                if (voices.isEmpty()) {
                    AlertDialog.Builder(this).setTitle("Couldn't load voices ✗")
                        .setMessage("Reason: ${err.ifBlank { "no Hindi voices returned" }}\n\nIf it says 401/403, the key is wrong — paste it again.")
                        .setPositiveButton("Change key") { _, _ -> askCartesiaKey() }
                        .setNegativeButton("Close", null).show()
                    return@runOnUiThread
                }
                // Siya (aapki bookmark) sabse upar, phir ladkiyan, phir ladke
                val sorted = voices.sortedWith(compareBy(
                    { if (it.optString("name").contains("siya", true)) 0 else 1 },
                    { if (it.optString("gender") == "feminine") 0 else 1 },
                    { it.optString("name") }
                ))
                val cur = prefs.getString("cartesia_voice", null)
                val labels = sorted.map { v ->
                    val g = if (v.optString("gender") == "masculine") "👨" else "👩"
                    val lock = if (v.optString("id") == cur && prefs.getString("voice_source", null) == "cartesia") "🔒 " else ""
                    val d = v.optString("description").take(60)
                    "$lock$g ${v.optString("name")}" + if (d.isNotBlank()) " — $d" else ""
                }.toTypedArray()
                AlertDialog.Builder(this).setTitle("Cartesia Hindi voices (tap to hear)")
                    .setItems(labels) { _, w ->
                        val v = sorted[w]
                        previewCartesia(v.optString("id"), v.optString("name"), v.optString("gender") == "masculine")
                    }
                    .setNeutralButton("Change key") { _, _ -> askCartesiaKey() }
                    .setNegativeButton("Close", null).show()
            }
        }.start()
    }

    private fun askCartesiaKey() {
        val input = android.widget.EditText(this).apply {
            hint = "sk_car_…"
            setSingleLine()
            setText(prefs.getString("cartesia_key", ""))
        }
        val box = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        AlertDialog.Builder(this).setTitle("Cartesia API key")
            .setMessage("1. Open play.cartesia.ai/keys (you're already logged in)\n2. Create a new key and copy it\n3. Paste it here\n\nThe key stays only on this phone.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val k = input.text.toString().filter { !it.isWhitespace() }
                prefs.edit().putString("cartesia_key", k).apply()
                if (k.isNotBlank()) cartesiaSetup()
            }
            .setNeutralButton("Open keys page") { _, _ -> tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.cartesia.ai/keys"))) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun previewCartesia(id: String, name: String, male: Boolean) {
        preview?.stop()
        val rv = RealVoice(this)
        preview = rv
        android.widget.Toast.makeText(this, "Loading $name…", android.widget.Toast.LENGTH_SHORT).show()
        val line = if (male) "हाय राहुल, मैं $name हूँ। आपका हर काम मैं फटाफट कर दूँगा। Hi Rahul, kaisi lagi meri awaaz?"
        else "हाय राहुल, मैं $name हूँ। आपका हर काम मैं फटाफट कर दूँगी। Hi Rahul, kaisi lagi meri awaaz?"
        rv.speak(line,
            onDone = {
                if (isFinishing || preview !== rv) return@speak
                AlertDialog.Builder(this).setTitle("Keep $name?")
                    .setMessage("Lilly will always speak in this voice" + (if (male) " and talk like a boy." else " and talk like a girl.") +
                        "\n\nGemini and offline voices stay paused. If Cartesia fails (no internet / credits over), the phone's voice fills in.")
                    .setPositiveButton("Keep $name 🔒") { _, _ ->
                        prefs.edit().putString("cartesia_voice", id).putString("cartesia_name", name)
                            .putBoolean("cartesia_male", male).putString("voice_source", "cartesia").apply()
                        refreshCards()
                        sendToEngine(LillyService.ACTION_TEST_VOICE)
                        android.widget.Toast.makeText(this, "$name is now Lilly's voice 🔒", android.widget.Toast.LENGTH_LONG).show()
                    }
                    .setNeutralButton("Hear again") { _, _ -> previewCartesia(id, name, male) }
                    .setNegativeButton("Try another") { _, _ -> cartesiaSetup() }
                    .show()
            },
            onFallback = {
                if (isFinishing || preview !== rv) return@speak
                AlertDialog.Builder(this).setTitle("$name didn't load ✗")
                    .setMessage("Reason:\n${RealVoice.lastError ?: "unknown"}\n\nPlease send a screenshot of this.")
                    .setPositiveButton("Change key") { _, _ -> askCartesiaKey() }
                    .setNegativeButton("Close", null).show()
            },
            waitMs = 25_000L, force = true, voiceName = id)
    }

    /** Gemini awaaz pakki karo + bachav wali offline awaaz offer karo */
    private fun keepGeminiVoice(name: String) {
        prefs.edit().putString("real_voice", name).putString("voice_source", "gemini").apply()
        refreshCards()
        sendToEngine(LillyService.ACTION_TEST_VOICE)
        val male = RealVoice.isMale(name)
        val backup = OfflineVoice.CHOICES.first { it.male == male }
        if (OfflineVoice.packInstalled(this, backup.pack)) {
            android.widget.Toast.makeText(this, "$name is now Lilly's voice 🔒", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle("$name is now Lilly's voice 🔒")
            .setMessage(
                "When there's no internet or Gemini's free limit runs out, Lilly needs a backup voice for new sentences.\n\n" +
                    "Download ${backup.name} (${OfflineVoice.packMb(backup.pack)} MB, one time) as the backup? Otherwise the phone's voice is used."
            )
            .setPositiveButton("Download") { _, _ -> downloadBackup(backup) }
            .setNegativeButton("Not now", null).show()
    }

    private fun downloadBackup(v: OfflineVoice.VoiceChoice) {
        android.widget.Toast.makeText(this, "Downloading ${v.name} in the background…", android.widget.Toast.LENGTH_SHORT).show()
        OfflineVoice.download(this, v.pack, progress = { }, done = { ok, err ->
            if (isFinishing) return@download
            android.widget.Toast.makeText(this, if (ok) "Backup voice ready ✓" else "Backup download failed: $err", android.widget.Toast.LENGTH_LONG).show()
            refreshCards()
        })
    }

    private var preview: RealVoice? = null

    /** Chuni hui awaaz yahin suna do; na chale toh asli wajah dikhao */
    private fun previewRealVoice(name: String, ask: Boolean = false) {
        preview?.stop()
        if (name == RealVoice.OFF) return
        val rv = RealVoice(this)
        preview = rv
        android.widget.Toast.makeText(this, "Loading $name… (first time can take a few seconds)", android.widget.Toast.LENGTH_SHORT).show()
        val male = RealVoice.isMale(name)
        rv.speak(
            if (male) "Hi Rahul, main $name hoon. Aapka kaam main jaldi se kar dunga. Kaisi lagi meri awaaz?"
            else "Hi Rahul, main $name hoon. Aapka kaam main jaldi se kar dungi. Kaisi lagi meri awaaz?",
            onDone = {
                if (!ask || isFinishing || preview !== rv) return@speak
                AlertDialog.Builder(this).setTitle("Keep $name?")
                    .setMessage("Lilly will always speak in this voice" +
                        (if (male) " and talk like a boy (karunga, raha hoon)." else " and talk like a girl (karungi, rahi hoon).") +
                        " It won't change by itself.")
                    .setPositiveButton("Keep $name 🔒") { _, _ -> keepGeminiVoice(name) }
                    .setNeutralButton("Hear again") { _, _ -> previewRealVoice(name, ask = true) }
                    .setNegativeButton("Try another") { _, _ -> chooseGeminiVoice() }
                    .show()
            },
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
            waitMs = 25_000L, force = true, voiceName = name
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
                    "• Needs \"Music control\" to be on (no helper needed)\n\n" +
                    "It's ${if (on) "ON" else "OFF"} now."
            )
            .setPositiveButton(if (on) "Turn off" else "Turn on") { _, _ ->
                prefs.edit().putBoolean("read_notifs", !on).apply()
                refreshCards()
                if (!on && !MediaCtl.allowed(this)) mediaCtlSetup()
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
        val canAuto = LillyAccess.canAutoSwitch(this)
        if (canAuto) {
            val auto = prefs.getBoolean("helper_auto", false)
            AlertDialog.Builder(this).setTitle("🤖 Lilly helper — Auto mode")
                .setMessage(
                    (if (auto) "Auto mode is ON ✓\n\n" else "Auto mode is ready to use.\n\n") +
                        "Lilly turns the helper ON only for the few seconds she needs it (Spotify play, WhatsApp send, speaker, WiFi switch) " +
                        "and turns it OFF right after.\n\nSo PhonePe, GPay, Paytm and bank apps work normally the rest of the time."
                )
                .setPositiveButton(if (auto) "Turn auto mode OFF" else "Turn auto mode ON") { _, _ ->
                    prefs.edit().putBoolean("helper_auto", !auto).apply()
                    if (!auto) LillyAccess.setSystem(this, false) // abhi OFF, zaroorat par khud ON
                    refreshCards()
                }
                .setNegativeButton("Close", null).show()
            return
        }
        if (LillyAccess.enabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("Lilly helper is ON ✓")
                .setMessage(
                    "Lilly can now press buttons for you, only right after you ask:\n" +
                        "• Spotify: taps the first song so it plays\n• WhatsApp: taps Send / Call\n• Calls: taps Speaker\n\n" +
                        "⚠️ PhonePe, GPay, Paytm and bank apps block payments while any helper like this is on " +
                        "(it's their fraud protection). Turn it off before paying."
                )
                .setNegativeButton("Turn off (for payments)") { _, _ -> turnOffHelper() }
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
                    "• ⚠️ While it's on, payment and bank apps (PhonePe, GPay, Paytm…) will show \"suspicious app\" and block payments. " +
                    "Without it, Lilly still searches Spotify and fills the WhatsApp message — you just tap Play / Send yourself.\n" +
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
            .setNegativeButton("Auto mode (no payment problem)") { _, _ -> autoHelperHelp() }
            .setNeutralButton("App info") { _, _ ->
                tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
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

    /** Ek baar poochho: insaani awaaz download karein? (Gemini wali baar-baar badalti hai) */
    private fun offerOfflineVoice() {
        if (OfflineVoice.installed(this) || prefs.getBoolean("offline_voice_offered", false)) return
        prefs.edit().putBoolean("offline_voice_offered", true).apply()
        AlertDialog.Builder(this)
            .setTitle("🎤 Give Lilly one fixed human voice?")
            .setMessage(
                "Right now Lilly's online voice switches to the phone's robot voice when the internet is slow or the free limit is used up.\n\n" +
                    "Pick a girl's or a boy's human voice (one-time download) and she will always speak in that same voice — no internet, no limit."
            )
            .setPositiveButton("Pick a voice") { _, _ -> pickOfflineVoice() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun offlineHelp() {
        AlertDialog.Builder(this)
            .setTitle("📴 Use Lilly without internet")
            .setMessage(
                "Works offline: \"Lilly\" wake word, calls, apps, alarms, timers, torch, volume, time, battery, settings, and the offline human voice.\n\n" +
                    "Needs internet: AI answers, weather, WhatsApp sending, maps, YouTube.\n\n" +
                    "To understand your commands without internet, the phone needs Google's offline speech packs:\n" +
                    "1. Tap \"Open\" below (or Settings → Google → Voice)\n" +
                    "2. Offline speech recognition → download Hindi (India) and English (India)\n\n" +
                    "Lilly switches to them automatically when there's no internet."
            )
            .setPositiveButton("Open") { _, _ ->
                if (!tryStart(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))) tryStart(Intent(Settings.ACTION_SETTINGS))
            }
            .setNegativeButton("Close", null)
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
                    "✨  NEW: \"Torch jalao aur volume badhao\" — two things at once",
                    "✨  NEW: after small jobs (torch, time…) just keep talking — no need to say Lilly again",
                    "📵  \"Kisne call kiya tha?\" / \"Missed calls\" / \"Call back\" / \"Redial\"",
                    "💬  \"Rahul ka message padho\" → \"Reply karo ki 5 minute mein aata hoon\" (WhatsApp khule bina)",
                    "🎶  \"Sad gaane chalao\" / \"Workout ke gaane\" / \"Kesariya jaisa gaana chalao\"",
                    "⚡  \"Routine banao office mode jisme silent karo aur WiFi on karo\" → \"Office mode\"",
                    "📍  \"Ye mera ghar hai\" → \"Ghar pahunch ke yaad dilana ki doodh lena hai\"",
                    "💳  \"Rahul ki UPI ID rahul@okaxis hai\" → \"Rahul ko 500 bhejo\" (PIN aap daalo)",
                    "📸  \"Selfie lo\" / \"Photo khicho\" / \"Video record karo\" / \"Screenshot lo\" / \"Auto rotate band karo\"",
                    "🛒  \"Amazon pe charger search karo\" / \"Zomato se pizza\" / \"Uber book karo airport ke liye\"",
                    "😴  \"30 minute baad gaana band karo\" / \"Sleep timer band karo\"",
                    "🎵  \"Gaana band karo\" / \"Gaana wapas chalao\" / \"Agla gaana\" / \"Pichla gaana\"",
                    "🎵  \"Ye kaunsa gaana hai?\" / \"Ye gaana like karo\" / \"Iske baad Kesariya lagao\"",
                    "🎵  \"Shuffle on karo\" / \"Is gaane ko repeat karo\" / \"Gaana shuru se chalao\" / \"30 second aage karo\"",
                    "🎵  \"Arijit Singh ke gaane chalao\" / \"Liked songs chalao\" / \"Spotify par workout playlist chalao\"",
                    "🛒  \"List me doodh aur bread jodo\" / \"Meri list padho\" / \"Doodh list se hatao\" / \"List saaf karo\"",
                    "📝  \"Note karo ki gas ka bill bharna hai\" / \"Mere notes padho\"",
                    "📅  \"Kal ki meeting kya hai?\" / \"Aaj ka schedule batao\"",
                    "📅  \"Kal 4 baje Ankit ke saath meeting daalo\"",
                    "✉️  \"Rahul ko SMS karo ki main aa raha hoon\"",
                    "🎂  \"Meri date of birth 12 June 1998 hai\" / \"Meri DOB kya hai?\"",
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

    /** Ek baar computer se permission — phir helper sirf zaroorat par khud ON/OFF */
    private fun autoHelperHelp() {
        val cmd = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        AlertDialog.Builder(this).setTitle("🤖 Auto mode — one-time setup")
            .setMessage(
                "Android only lets an app switch its helper on/off by itself after a one-time permission from a computer (USB). " +
                    "After that Lilly keeps the helper OFF and turns it ON only for a few seconds when you ask for Spotify / WhatsApp / speaker — so payment apps stay happy.\n\n" +
                    "On your phone:\n1. Settings → About phone → tap \"Build number\" 7 times (Developer options ON)\n" +
                    "2. Settings → Developer options → turn ON \"USB debugging\"\n\n" +
                    "On a computer (Windows/Mac):\n3. Download \"Platform-tools\" from developer.android.com/tools/releases/platform-tools and unzip\n" +
                    "4. Connect the phone by USB, tap \"Allow\" on the phone\n" +
                    "5. In the platform-tools folder open a terminal and run:\n\n$cmd\n\n" +
                    "6. Open Lilly → Lilly helper → \"Turn auto mode ON\"\n\nYou can turn USB debugging off afterwards."
            )
            .setPositiveButton("Copy command") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("adb", cmd))
                android.widget.Toast.makeText(this, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null).show()
    }

    /** (code, naam, package) — Lilly inme gaana chala sakti hai */
    private val musicApps = listOf(
        Triple("", "Auto (last one you used)", ""),
        Triple("ytmusic", "YouTube Music ⭐ plays hands-free", "com.google.android.apps.youtube.music"),
        Triple("youtube", "YouTube ⭐ plays hands-free", "com.google.android.youtube"),
        Triple("spotify", "Spotify", "com.spotify.music"),
        Triple("jiosaavn", "JioSaavn", "com.jio.media.jiobeats"),
        Triple("wynk", "Wynk Music", "com.bsbportal.music"),
        Triple("gaana", "Gaana", "com.gaana"),
        Triple("amazon", "Amazon Music", "com.amazon.mp3"),
        Triple("apple", "Apple Music", "com.apple.android.music")
    ).map { Triple(it.first, it.second, it.third) }

    private fun isInstalled(pkg: String) = pkg.isBlank() || try { packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

    private fun chooseMusicApp() {
        val list = musicApps.filter { isInstalled(it.third) }
        val cur = prefs.getString("music_app", "") ?: ""
        AlertDialog.Builder(this).setTitle("🎵 Play songs in")
            .setSingleChoiceItems(list.map { it.second }.toTypedArray(), list.indexOfFirst { it.first == cur }.coerceAtLeast(0)) { d, w ->
                prefs.edit().putString("music_app", list[w].first).apply()
                refreshCards(); d.dismiss()
            }.show()
    }

    /** Spotify ka official connection — ek baar login + "Agree" */
    private fun connectSpotify() {
        if (!SpotifyRemote.installed(this)) {
            android.widget.Toast.makeText(this, "Install Spotify and log in first", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        // Pehle Spotify ka asli "Allow" (auth flow), phir App Remote jodo
        val req = com.spotify.sdk.android.auth.AuthorizationRequest.Builder(
            SpotifyRemote.CLIENT_ID, com.spotify.sdk.android.auth.AuthorizationResponse.Type.TOKEN, SpotifyRemote.REDIRECT
        ).setScopes(arrayOf("app-remote-control", "streaming", "user-modify-playback-state", "user-read-playback-state"))
            .setShowDialog(true).build()
        try {
            com.spotify.sdk.android.auth.AuthorizationClient.openLoginActivity(this, SPOTIFY_AUTH, req)
        } catch (e: Exception) {
            spotifyFailed("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private val SPOTIFY_AUTH = 4242

    @Deprecated("Activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SPOTIFY_AUTH) return
        val res = com.spotify.sdk.android.auth.AuthorizationClient.getResponse(resultCode, data)
        when (res.type) {
            com.spotify.sdk.android.auth.AuthorizationResponse.Type.TOKEN -> {
                android.widget.Toast.makeText(this, "Spotify allowed ✓ connecting…", android.widget.Toast.LENGTH_SHORT).show()
                SpotifyRemote.connect(this, true) { r ->
                    refreshCards()
                    if (isFinishing) return@connect
                    if (r != null) AlertDialog.Builder(this).setTitle("Spotify connected ✓")
                        .setMessage("Now say \"Lilly, Spotify par Kesariya chalao\" — the song starts by itself, no helper needed.")
                        .setPositiveButton("Test now") { _, _ -> testSpotifyPlay() }
                        .setNegativeButton("OK", null).show()
                    else spotifyFailed(SpotifyRemote.lastError ?: "unknown")
                }
            }
            com.spotify.sdk.android.auth.AuthorizationResponse.Type.ERROR -> spotifyFailed("Login error: ${res.error}")
            else -> spotifyFailed("Login was cancelled (${res.type})")
        }
    }

    private fun spotifyFailed(why: String) {
        if (isFinishing) return
        AlertDialog.Builder(this).setTitle("Spotify didn't connect ✗")
            .setMessage("Reason:\n$why\n\nCheck in the Spotify developer dashboard:\n" +
                "• Redirect URI is exactly lilly://spotify-callback\n" +
                "• Android package com.rahul.lilly with the SHA1 is saved\n" +
                "• Your Spotify email is added in User Management\n\nPlease send a screenshot of this.")
            .setPositiveButton("Try again") { _, _ -> connectSpotify() }
            .setNegativeButton("Close", null).show()
    }

    private fun testSpotifyPlay() {
        android.widget.Toast.makeText(this, "Finding Kesariya…", android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            val uri = SpotifyRemote.findUri("Kesariya Arijit Singh")
            runOnUiThread {
                if (uri == null) {
                    AlertDialog.Builder(this).setTitle("Couldn't find the song").setMessage("Check the internet and try again.")
                        .setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }
                SpotifyRemote.play(this, uri, showAuth = true) { ok ->
                    if (isFinishing) return@play
                    if (ok) android.widget.Toast.makeText(this, "Playing on Spotify ✓", android.widget.Toast.LENGTH_SHORT).show()
                    else AlertDialog.Builder(this).setTitle("Spotify didn't play ✗")
                        .setMessage("Reason:\n${SpotifyRemote.lastError ?: "unknown"}\n\nTap \"Connect Spotify\" first if you haven't.")
                        .setPositiveButton("Connect Spotify") { _, _ -> connectSpotify() }
                        .setNegativeButton("Close", null).show()
                }
            }
        }.start()
    }

    private fun jsonCount(key: String): Int = try { org.json.JSONObject(prefs.getString(key, "{}") ?: "{}").length() } catch (e: Exception) { 0 }

    private fun routinesSetup() {
        val o = try { org.json.JSONObject(prefs.getString("routines", "{}") ?: "{}") } catch (e: Exception) { org.json.JSONObject() }
        val names = o.keys().asSequence().toList()
        val items = names.map { n -> val a = o.getJSONArray(n); "⚡ $n: " + (0 until a.length()).joinToString(", ") { a.getString(it) } }.toMutableList()
        val habit = prefs.getBoolean("habit_suggest", true)
        items.add("🔁 Habit suggestions: ${if (habit) "ON — tap to turn off" else "OFF — tap to turn on"}")
        AlertDialog.Builder(this).setTitle("⚡ Routines & habits")
            .setItems(items.toTypedArray()) { _, w ->
                if (w == names.size) {
                    prefs.edit().putBoolean("habit_suggest", !habit).apply(); refreshCards(); routinesSetup(); return@setItems
                }
                val n = names[w]
                AlertDialog.Builder(this).setTitle("Delete routine \"$n\"?")
                    .setPositiveButton("Delete") { _, _ -> o.remove(n); prefs.edit().putString("routines", o.toString()).apply(); refreshCards(); routinesSetup() }
                    .setNegativeButton("Cancel", null).show()
            }
            .setPositiveButton("Add routine") { _, _ -> addRoutine() }
            .setNeutralButton("How") { _, _ ->
                AlertDialog.Builder(this).setTitle("Routines").setMessage(
                    "A routine runs many jobs with one phrase.\n\nBy voice: \"Routine banao office mode jisme silent karo aur WiFi on karo\"\n" +
                        "Then just say: \"Lilly, office mode\".\n\n" +
                        "Habits: when you do the same thing at about the same time on 3+ days (like a gym playlist at 7 am), " +
                        "Lilly asks \"should I do it?\" when you call her. Say haan or nahi."
                ).setPositiveButton("OK", null).show()
            }
            .setNegativeButton("Close", null).show()
    }

    private fun addRoutine() {
        val name = android.widget.EditText(this).apply { hint = "Name, e.g. office mode"; setSingleLine() }
        val steps = android.widget.EditText(this).apply { hint = "Jobs, one per line:\nsilent karo\nwifi on karo\noffice ka rasta dikhao"; minLines = 4 }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(name); addView(steps) }
        AlertDialog.Builder(this).setTitle("New routine").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim().lowercase()
                val list = steps.text.toString().split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                if (n.isNotBlank() && list.isNotEmpty()) {
                    val o = try { org.json.JSONObject(prefs.getString("routines", "{}") ?: "{}") } catch (e: Exception) { org.json.JSONObject() }
                    o.put(n, org.json.JSONArray(list))
                    prefs.edit().putString("routines", o.toString()).apply()
                    refreshCards()
                }
                routinesSetup()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun upiSetup() {
        val o = try { org.json.JSONObject(prefs.getString("upi_ids", "{}") ?: "{}") } catch (e: Exception) { org.json.JSONObject() }
        val names = o.keys().asSequence().toList()
        val b = AlertDialog.Builder(this).setTitle("💳 UPI contacts")
        if (names.isEmpty()) b.setMessage(
            "Save a person's UPI ID once, then say \"Lilly, Rahul ko 500 bhejo\". Lilly opens your UPI app with the name and amount filled — you check it and enter your PIN yourself. Lilly never sees the PIN.\n\n" +
                "Or say: \"Rahul ki UPI ID rahul@okaxis hai\"."
        ) else b.setItems(names.map { "$it  →  ${o.getString(it)}" }.toTypedArray()) { _, w ->
            val n = names[w]
            AlertDialog.Builder(this).setTitle("Delete $n?")
                .setPositiveButton("Delete") { _, _ -> o.remove(n); prefs.edit().putString("upi_ids", o.toString()).apply(); refreshCards(); upiSetup() }
                .setNegativeButton("Cancel", null).show()
        }
        b.setPositiveButton("Add") { _, _ ->
            val name = android.widget.EditText(this).apply { hint = "Name, e.g. Rahul"; setSingleLine() }
            val vpa = android.widget.EditText(this).apply { hint = "UPI ID, e.g. 9876543210@ybl"; setSingleLine() }
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(name); addView(vpa) }
            AlertDialog.Builder(this).setTitle("Add UPI contact").setView(box)
                .setPositiveButton("Save") { _, _ ->
                    val n = name.text.toString().trim().lowercase()
                    val v = vpa.text.toString().trim().lowercase()
                    if (n.isNotBlank() && Regex("""^[a-z0-9._\-]+@[a-z0-9.\-]+$""").matches(v)) {
                        o.put(n, v); prefs.edit().putString("upi_ids", o.toString()).apply(); refreshCards()
                    } else android.widget.Toast.makeText(this, "UPI ID looks wrong (needs @)", android.widget.Toast.LENGTH_LONG).show()
                    upiSetup()
                }.setNegativeButton("Cancel", null).show()
        }.setNegativeButton("Close", null).show()
    }

    private fun smartSetup() {
        val on = prefs.getBoolean("smart_listen", true)
        AlertDialog.Builder(this).setTitle("✍️ Smart listening")
            .setMessage(
                "ON: Lilly records your command and Gemini writes it exactly — correct Hinglish in English letters " +
                    "(\"missed call\", \"WiFi\", \"notifications check karo\") and your contacts' names spelled right.\n\n" +
                    "• Uses your free Gemini key, adds about 1 second\n" +
                    "• No internet / free limit over → Lilly automatically uses the phone's normal recognizer\n" +
                    "• Your command audio goes to Google (as it does with the normal recognizer too)\n\n" +
                    "It's ${if (on) "ON" else "OFF"} now."
            )
            .setPositiveButton(if (on) "Turn off" else "Turn on") { _, _ ->
                prefs.edit().putBoolean("smart_listen", !on).apply(); refreshCards()
            }
            .setNegativeButton("Close", null).show()
    }

    private fun headphoneSetup() {
        val codes = listOf("ask", "music", "off")
        val labels = arrayOf(
            "Ask me — Lilly says \"Headphones connected, what can I do?\" and listens",
            "Play my music automatically",
            "Off — do nothing"
        )
        val cur = when {
            !prefs.getBoolean("headphone_mode", true) -> 2
            prefs.getBoolean("headphone_music", false) -> 1
            else -> 0
        }
        AlertDialog.Builder(this).setTitle("🎧 When earphones / Bluetooth connect")
            .setSingleChoiceItems(labels, cur) { d, w ->
                prefs.edit().putBoolean("headphone_mode", codes[w] != "off").putBoolean("headphone_music", codes[w] == "music").apply()
                refreshCards(); d.dismiss()
            }.show()
    }

    private fun replyHelp() {
        val ready = MediaCtl.allowed(this) && prefs.getBoolean("read_notifs", false)
        AlertDialog.Builder(this).setTitle("💬 Reply without opening WhatsApp")
            .setMessage(
                (if (ready) "Ready ✓\n\n" else "") +
                    "When a message arrives:\n• \"Lilly, Rahul ka message padho\" / \"kiska message aaya?\"\n" +
                    "• then \"reply karo ki 5 minute mein aata hoon\" → Lilly reads it back → say \"haan\" → sent, WhatsApp never opens\n" +
                    "• Or straight away: \"Ankit ko reply karo ki ok\"\n\n" +
                    "Works for WhatsApp, Telegram, Instagram and SMS apps that show a Reply button in the notification. No Lilly helper needed, so payments are fine.\n\n" +
                    "Needs: 🎛 Music control ON + 🔔 Read my notifications ON. Messages stay only in memory."
            )
            .setPositiveButton(if (ready) "OK" else "Turn on") { _, _ ->
                if (!ready) {
                    prefs.edit().putBoolean("read_notifs", true).apply()
                    refreshCards()
                    if (!MediaCtl.allowed(this)) mediaCtlSetup()
                }
            }
            .setNegativeButton("Close", null).show()
    }

    private fun mediaCtlSetup() {
        AlertDialog.Builder(this).setTitle("🎛 Music control")
            .setMessage(
                (if (MediaCtl.allowed(this)) "ON ✓\n\n" else "") +
                    "Lets Lilly control whatever song is playing — in Spotify, YouTube Music, JioSaavn, Wynk and others — without the Lilly helper:\n\n" +
                    "• \"Ye kaunsa gaana hai?\"\n• \"Gaana shuru se chalao\" / \"30 second aage karo\"\n" +
                    "• \"Shuffle on karo\" / \"Repeat karo\" / \"Ye gaana like karo\" (if the app has those buttons)\n" +
                    "• Spotify songs start more reliably\n\n" +
                    "Android calls this \"Notification access\". Lilly uses it only for the music player — she doesn't read or save your notifications.\n\n" +
                    "Steps: tap Open → find \"Lilly music control\" → turn ON → Allow."
            )
            .setPositiveButton("Open") { _, _ -> tryStart(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
            .setNegativeButton("Close", null).show()
    }

    private fun musicHelp() {
        AlertDialog.Builder(this).setTitle("▶️ Playing songs by voice")
            .setMessage(
                "Without any helper or permission:\n\n" +
                    "✅ YouTube Music — finds the song and starts it directly\n" +
                    "✅ YouTube — opens the exact video and plays\n" +
                    "🟡 Spotify — Lilly opens Spotify first, then asks it to play. Many phones play; some only show the search (Spotify's own limit for other apps)\n" +
                    "🟡 JioSaavn / Wynk / Gaana / Amazon / Apple Music — uses Android's standard \"play from search\"; works if the app supports it\n\n" +
                    "Say: \"YouTube Music par Kesariya chalao\", \"JioSaavn pe Arijit ke gaane\", or pick a Music app here and just say \"Kesariya chalao\"."
            ).setPositiveButton("OK", null).show()
    }

    private fun abuseSetup() {
        val on = prefs.getBoolean("abuse_mode", false)
        AlertDialog.Builder(this).setTitle("🤬 Gaali mode")
            .setMessage(
                "When ON: if you swear at Lilly, she swears back in Hindi/English (strong words!) — and still does any job you asked, like \"bc torch jala\".\n\n" +
                    "When OFF: she calmly asks you not to swear.\n\n" +
                    "Either way these replies come from the phone itself, not the AI, so she never refuses.\n\n" +
                    "You can also say: \"Lilly, gaali mode on\" / \"gaali mode band karo\".\n\n" +
                    "Tip: keep it OFF when other people (or kids) are around."
            )
            .setPositiveButton(if (on) "Turn OFF" else "Turn ON") { _, _ ->
                prefs.edit().putBoolean("abuse_mode", !on).apply(); refreshCards()
            }
            .setNegativeButton("Close", null).show()
    }

    /** Meri spellings: Lilly ki seekhi hui + apni jodi hui (galat -> sahi) */
    private fun showSpellings() {
        val book = SpellBook.load(prefs.getString("spell_learned", null))
        val items = book.entries.map { "${it.key}  →  ${it.value}" }.reversed()
        val b = AlertDialog.Builder(this).setTitle("🗣 My spellings (${book.size})")
        if (items.isEmpty()) b.setMessage(
            "Lilly learns these by herself: when Google writes a word wrong and the AI works out what you meant " +
                "(\"misi call\" → \"missed call\"), she saves it and fixes it next time — even without internet.\n\n" +
                "You can also add your own. Built-in fixes: vi-fi → WiFi, watsap → WhatsApp, chek → check, misi call → missed call, and more."
        ) else b.setItems(items.toTypedArray()) { _, w ->
            val key = book.keys.reversed().toList()[w]
            AlertDialog.Builder(this).setTitle("Delete \"$key\"?")
                .setPositiveButton("Delete") { _, _ ->
                    book.remove(key)
                    prefs.edit().putString("spell_learned", SpellBook.save(book)).apply()
                    Parser.setCustomSpellings(book)
                    showSpellings()
                }.setNegativeButton("Cancel", null).show()
        }
        b.setPositiveButton("Add") { _, _ -> addSpelling() }
            .setNegativeButton("Close", null).show()
    }

    private fun addSpelling() {
        val wrong = android.widget.EditText(this).apply { hint = "What Lilly writes (wrong), e.g. baidya sar"; setSingleLine() }
        val right = android.widget.EditText(this).apply { hint = "What you said (right), e.g. badiya sir"; setSingleLine() }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(wrong); addView(right)
        }
        AlertDialog.Builder(this).setTitle("Add a spelling").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val w = wrong.text.toString().trim().lowercase()
                val r = right.text.toString().trim()
                if (w.length >= 2 && r.isNotBlank() && !w.equals(r, true)) {
                    val book = SpellBook.load(prefs.getString("spell_learned", null))
                    book[w] = r
                    prefs.edit().putString("spell_learned", SpellBook.save(book)).apply()
                    Parser.setCustomSpellings(book)
                    android.widget.Toast.makeText(this, "Saved: $w → $r", android.widget.Toast.LENGTH_SHORT).show()
                }
                showSpellings()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ---------------- Voice match (sirf meri awaaz) ----------------

    private fun pauseEngineMic(pause: Boolean) {
        if (!LillyBus.running) return
        try { LillyService.start(this, if (pause) LillyService.ACTION_PAUSE_MIC else LillyService.ACTION_RESUME_MIC) } catch (e: Exception) { }
    }

    private fun voiceMatchSetup() {
        if (!hasMic()) { askPermissions(); return }
        if (!VoiceMatch.modelReady(this)) {
            AlertDialog.Builder(this).setTitle("🔐 Only my voice")
                .setMessage(
                    "Like Siri's Voice Match: Lilly will wake up only when YOU say \"Lilly\" — not for the TV, friends or family.\n\n" +
                        "• One-time download (${VoiceMatch.MODEL_MB} MB), then works without internet\n" +
                        "• You say \"Lilly\" 5 times to teach her your voice\n" +
                        "• Your voice never leaves the phone\n" +
                        "• She keeps learning your voice a little every time you use her"
                )
                .setPositiveButton("Download & start") { _, _ -> downloadVoiceMatch() }
                .setNegativeButton("Not now", null).show()
            return
        }
        val p = VoiceMatch.voicePrint(this)
        if (!p.ready) { enrollVoice(); return }
        val on = VoiceMatch.active(this)
        val items = arrayOf(
            if (on) "Turn OFF (wake for anyone)" else "Turn ON (wake only for me)",
            "Strictness: " + VoiceMatch.level(this).replaceFirstChar { it.uppercase() },
            "Test my voice",
            "Teach my voice again",
            "Delete my voice"
        )
        AlertDialog.Builder(this).setTitle("🔐 Only my voice")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> { VoiceMatch.setOn(this, !on); refreshCards(); pauseEngineMic(false) }
                    1 -> chooseMatchLevel()
                    2 -> testVoiceMatch()
                    3 -> enrollVoice()
                    else -> AlertDialog.Builder(this).setTitle("Delete your voice?")
                        .setMessage("Lilly will wake up for anyone again.")
                        .setPositiveButton("Delete") { _, _ -> VoiceMatch.reset(this); refreshCards() }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Close", null).show()
    }

    private fun chooseMatchLevel() {
        val codes = arrayOf("relaxed", "normal", "strict")
        val labels = arrayOf(
            "Relaxed — rarely misses you, may sometimes wake for a similar voice",
            "Normal (recommended)",
            "Strict — never wakes for others, you may need to say \"Lilly\" twice sometimes"
        )
        AlertDialog.Builder(this).setTitle("How strict?")
            .setSingleChoiceItems(labels, codes.indexOf(VoiceMatch.level(this)).coerceAtLeast(0)) { d, w ->
                prefs.edit().putString("voice_match_level", codes[w]).apply()
                refreshCards()
                d.dismiss()
            }.show()
    }

    private fun downloadVoiceMatch() {
        val text = label("Starting…", 15f, Color.WHITE).apply { setPadding(dp(24), dp(12), dp(24), dp(4)) }
        val d = AlertDialog.Builder(this).setTitle("Downloading voice match").setView(text)
            .setCancelable(false).setNegativeButton("Hide", null).show()
        VoiceMatch.download(this,
            progress = { pct -> runOnUiThread { text.text = "$pct%  (about ${VoiceMatch.MODEL_MB} MB)" } },
            done = { ok, err ->
                runOnUiThread {
                    try { d.dismiss() } catch (e: Exception) { }
                    if (isFinishing) return@runOnUiThread
                    if (ok) enrollVoice()
                    else AlertDialog.Builder(this).setTitle("Download didn't finish ✗")
                        .setMessage("Reason: $err\n\nCheck the internet and try again.")
                        .setPositiveButton("Try again") { _, _ -> downloadVoiceMatch() }
                        .setNegativeButton("Close", null).show()
                }
            })
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun recordClip(ms: Int): FloatArray {
        val rate = 16000
        val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val ar = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        val total = rate * ms / 1000
        val out = FloatArray(total)
        val buf = ShortArray(rate / 10)
        var got = 0
        try {
            ar.startRecording()
            while (got < total) {
                val n = ar.read(buf, 0, minOf(buf.size, total - got))
                if (n <= 0) break
                for (i in 0 until n) out[got + i] = buf[i] / 32768f
                got += n
            }
        } finally {
            try { ar.stop() } catch (e: Exception) { }
            ar.release()
        }
        return if (got == total) out else out.copyOf(got)
    }

    private val enrollWords = listOf("Lilly", "Hey Lilly", "Lilly", "Hey Lilly", "Lilly")

    /** 5 baar "Lilly" bulwa kar awaaz ki pehchaan banao */
    private fun enrollVoice() {
        if (!hasMic()) { askPermissions(); return }
        pauseEngineMic(true)
        val msg = label("", 17f, Color.WHITE).apply { setPadding(dp(24), dp(16), dp(24), dp(8)) }
        val d = AlertDialog.Builder(this).setTitle("🔐 Teach Lilly your voice").setView(msg)
            .setCancelable(false)
            .setNegativeButton("Cancel", null).show()
        val samples = ArrayList<FloatArray>()
        var cancelled = false
        d.setOnDismissListener { cancelled = true; if (!isFinishing && samples.size < 3) pauseEngineMic(false) }
        fun step(i: Int, retry: Boolean) {
            if (cancelled) return
            if (i >= enrollWords.size) { d.setOnDismissListener(null); d.dismiss(); finishEnroll(samples); return }
            msg.text = (if (retry) "Didn't catch that clearly — once more.\n\n" else "") +
                "Step ${i + 1} of ${enrollWords.size}\n\nGet ready… say in your normal voice:\n\n🎙  \"${enrollWords[i]}\""
            msg.postDelayed({
                if (cancelled) return@postDelayed
                msg.text = "Step ${i + 1} of ${enrollWords.size}\n\n🔴 Listening… say:  \"${enrollWords[i]}\""
                Thread {
                    val raw = try { recordClip(2200) } catch (e: Exception) { FloatArray(0) }
                    val clip = VoiceMatch.trim(raw)
                    val e = if (VoiceMatch.loudEnough(clip) || VoiceMatch.loudEnough(raw))
                        VoiceMatch.embed(this, if (clip.size >= 12000) clip else raw) else null
                    runOnUiThread {
                        if (cancelled || isFinishing) return@runOnUiThread
                        if (e == null) step(i, true)
                        else { samples.add(e); step(i + 1, false) }
                    }
                }.start()
            }, 1200)
        }
        step(0, false)
    }

    private fun finishEnroll(samples: List<FloatArray>) {
        // Jo namoona baaki sab se mel nahi khaata (khaansi, shor) use hata do
        val keep = samples.filter { a ->
            val others = samples.filter { it !== a }
            others.isEmpty() || others.map { VoicePrint.cosine(a, it) }.average() >= 0.25
        }
        if (keep.size < 3) {
            pauseEngineMic(false)
            AlertDialog.Builder(this).setTitle("Let's try again")
                .setMessage("The recordings were too different from each other (noise or different voices). Find a quiet place and say \"Lilly\" in your normal voice.")
                .setPositiveButton("Try again") { _, _ -> enrollVoice() }
                .setNegativeButton("Close", null).show()
            return
        }
        val p = VoicePrint(keep.toMutableList())
        VoiceMatch.save(this, p)
        VoiceMatch.setOn(this, true)
        pauseEngineMic(false)
        refreshCards()
        AlertDialog.Builder(this).setTitle("Done ✓ Lilly knows your voice")
            .setMessage("Now Lilly wakes up only when you say \"Lilly\".\n\nTry \"Test voice match\" to see your score, or ask someone else to say \"Lilly\" — she should stay quiet.\n\nIf she sometimes misses you, set Strictness to Relaxed. She also keeps learning your voice as you use her.")
            .setPositiveButton("Test now") { _, _ -> testVoiceMatch() }
            .setNegativeButton("OK", null).show()
    }

    private fun testVoiceMatch() {
        if (!hasMic()) { askPermissions(); return }
        val p = VoiceMatch.voicePrint(this)
        if (!VoiceMatch.modelReady(this) || !p.ready) { voiceMatchSetup(); return }
        pauseEngineMic(true)
        val msg = label("Get ready… then say \"Lilly\"", 17f, Color.WHITE).apply { setPadding(dp(24), dp(16), dp(24), dp(8)) }
        val d = AlertDialog.Builder(this).setTitle("🧪 Test voice match").setView(msg)
            .setPositiveButton("Close", null).show()
        d.setOnDismissListener { pauseEngineMic(false) }
        msg.postDelayed({
            msg.text = "🔴 Listening… say \"Lilly\""
            Thread {
                val raw = try { recordClip(2200) } catch (e: Exception) { FloatArray(0) }
                val clip = VoiceMatch.trim(raw)
                val e = if (VoiceMatch.loudEnough(raw)) VoiceMatch.embed(this, if (clip.size >= 12000) clip else raw) else null
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (e == null) { msg.text = "Didn't hear anything clearly. Close and try again."; return@runOnUiThread }
                    val sc = p.score(e)
                    val need = VoicePrint.threshold(VoiceMatch.level(this))
                    msg.text = "Match: ${(sc * 100).toInt()}%   (needs ${(need * 100).toInt()}%)\n\n" +
                        if (sc >= need) "✓ That's you — Lilly will wake up." else "✗ Lilly would NOT wake up for this voice."
                }
            }.start()
        }, 1200)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Lilly ne kaha "permission chahiye" (call log / calendar) — yahin poochh lo
        intent?.getStringExtra("ask_perm")?.let { perm ->
            intent.removeExtra("ask_perm")
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(perm), 7)
        }
        LillyBus.ui = this
        LillyBus.launcher = myLauncher
        // Nayi version aayi ho toh batao (6 ghante me ek baar, chupchaap)
        Updater.check(this, userAsked = false) { refreshCards() }
        OfflineVoice.cleanupOld(this)
        offerOfflineVoice()
        status.text = LillyBus.lastStatus.ifBlank { "Starting up…" }
        onHeard(LillyBus.lastHeard)
        orb.state = LillyBus.lastOrb
        onRunning(LillyBus.running)
        refreshCards()
        if (widgets.isNotEmpty()) { live.removeCallbacks(liveTick); live.postDelayed(liveTick, 2500) }
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
        live.removeCallbacks(liveTick)
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
