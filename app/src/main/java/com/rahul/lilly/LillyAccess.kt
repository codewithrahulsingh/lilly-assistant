package com.rahul.lilly

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lilly ke "haath": jab aap kehte ho tabhi, kuch chuni hui jagah button dabana —
 * - Spotify: search ke baad pehle gaane par tap (gaana bajne lage)
 * - WhatsApp: message ka "Send" / "Voice call" / "Video call"
 * - Phone ki call screen: "Speaker"
 * Aur agar aap "Read my notifications" ON karo, toh naye notifications yaad rakhna (sirf RAM me,
 * phone band/Lilly band hote hi mit jaate hain) taaki "kiska message aaya?" ka jawab de sake.
 */
class LillyAccess : AccessibilityService() {

    data class Note(val app: String, val pkg: String, val title: String, val text: String, val at: Long)

    private class Job(
        val kind: String, // spotify, wa_send, wa_call, wa_video, speaker
        val query: String,
        val until: Long
    )

    companion object {
        @Volatile private var instance: LillyAccess? = null
        @Volatile private var job: Job? = null
        private const val SPOTIFY = "com.spotify.music"
        private val WA = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val notes = ArrayList<Note>()

        /** Accessibility me Lilly ON hai? */
        fun enabled(ctx: Context): Boolean {
            if (instance != null) return true
            val list = try {
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            } catch (e: Exception) { null } ?: return false
            val me = ComponentName(ctx, LillyAccess::class.java).flattenToString()
            return list.split(':').any { it.equals(me, ignoreCase = true) }
        }

        private fun start(kind: String, query: String, ms: Long) {
            job = Job(kind, query, System.currentTimeMillis() + ms)
            val inst = instance
            if (inst != null) { inst.startTrying(); return }
            // Auto mode: helper sirf is kaam ke liye kuch second ON (payment apps ke liye baaki time OFF)
            val a = app ?: return
            if (autoMode(a) && setSystem(a, true)) {
                autoOn = true
                watchAuto()
            }
        }

        /** Service ka app context (LillyService set karta hai) */
        @Volatile var app: Context? = null
        @Volatile private var autoOn = false
        private val ui = Handler(Looper.getMainLooper())

        /** adb se "WRITE_SECURE_SETTINGS" mila hai — Lilly helper ko khud on/off kar sakti hai */
        fun canAutoSwitch(ctx: Context) =
            ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED

        fun autoMode(ctx: Context) =
            ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE).getBoolean("helper_auto", false) && canAutoSwitch(ctx)

        /** Brain ke liye: helper abhi ON hai, ya zaroorat par khud ON ho jayega */
        fun usable(ctx: Context) = enabled(ctx) || autoMode(ctx)

        /** Accessibility list me Lilly helper jodo / hatao (sirf WRITE_SECURE_SETTINGS ke saath) */
        fun setSystem(ctx: Context, on: Boolean): Boolean = try {
            val cr = ctx.contentResolver
            val me = ComponentName(ctx, LillyAccess::class.java).flattenToString()
            val cur = (Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(':').filter { it.isNotBlank() && !it.equals(me, true) }
            val next = if (on) cur + me else cur
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next.joinToString(":"))
            if (on) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: Exception) { false }

        /** Kaam ho gaya / time khatam — auto mode me helper wapas OFF */
        private fun watchAuto() {
            ui.removeCallbacksAndMessages(null)
            val tick = object : Runnable {
                override fun run() {
                    val j = job
                    val done = j == null || System.currentTimeMillis() > j.until
                    if (!done) { ui.postDelayed(this, 1000); return }
                    // Spotify ke "play dabao" wale check ke liye thoda ruk ke band karo
                    ui.postDelayed({
                        if (job == null || System.currentTimeMillis() > (job?.until ?: 0)) {
                            job = null
                            val a = app
                            if (autoOn && a != null) { setSystem(a, false); autoOn = false }
                        }
                    }, 4000)
                }
            }
            ui.postDelayed(tick, 1500)
        }

        /** Spotify me ye gaana search hua hai — results aate hi pehle gaane par tap */
        fun playOnSpotify(query: String) = start("spotify", query, 15_000)

        /** WhatsApp chat khulte hi "Send" dabao */
        fun sendWhatsApp() = start("wa_send", "", 12_000)

        /** WhatsApp chat me "Voice call" / "Video call" dabao */
        fun callOnWhatsApp(video: Boolean) = start(if (video) "wa_video" else "wa_call", "", 12_000)

        /**
         * Helper band karo (payment apps ke liye). Android app ko khud ON karne nahi deta,
         * par band khud kar sakta hai. @return true agar band hua
         */
        fun turnOff(): Boolean {
            val i = instance ?: return false
            job = null
            return try { i.disableSelf(); true } catch (e: Exception) { false }
        }

        /** Settings me WiFi / Bluetooth / Location / Airplane ka switch on ya off karo */
        fun flipSwitch(name: String, on: Boolean) = start("switch", "$name|$on", 12_000)

        /** Screenshot / lock jaise system kaam (Android 9+) */
        fun global(action: Int) {
            val i = instance
            if (i != null) { i.performGlobalAction(action); return }
            start("global", action.toString(), 8_000)
        }

        /** Camera ka shutter / record button dabao */
        fun pressShutter(video: Boolean) = start(if (video) "record" else "shutter", "", 12_000)

        /** Call screen par "Speaker" dabao */
        fun pressSpeaker() = start("speaker", "", 15_000)

        /** Naye notifications (sabse naya pehle). app = "" sab, ya "whatsapp" */
        fun recentNotes(app: String, sinceMs: Long): List<Note> = synchronized(notes) {
            val now = System.currentTimeMillis()
            notes.filter { now - it.at <= sinceMs && (app.isBlank() || it.pkg.contains(app) || it.app.lowercase().contains(app)) }
                .reversed()
        }

        fun clearNotes(app: String) = synchronized(notes) {
            notes.removeAll { app.isBlank() || it.pkg.contains(app) || it.app.lowercase().contains(app) }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var tries = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Auto mode me kaam ke liye ON hua — turant kaam shuru
        if (job != null) startTrying()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        synchronized(notes) { notes.clear() }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            keepNotification(event)
            return
        }
        if (active()) {
            main.removeCallbacks(tapRunnable)
            main.postDelayed(tapRunnable, 350)
        }
    }

    // ---------------- Notifications (sirf user ne ON kiya ho) ----------------

    private fun keepNotification(event: AccessibilityEvent) {
        val prefs = getSharedPreferences("lilly", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("read_notifs", false)) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) return
        val n = event.parcelableData as? Notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return // gaana, download, call — chhodo
        if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS ||
            n.category == Notification.CATEGORY_SERVICE || n.category == Notification.CATEGORY_SYSTEM) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
        synchronized(notes) {
            notes.removeAll { it.pkg == pkg && it.title == title && it.text == text }
            notes.add(Note(app, pkg, title, text.take(300), System.currentTimeMillis()))
            while (notes.size > 40) notes.removeAt(0)
        }
    }

    // ---------------- Button dabana ----------------

    private fun active(): Boolean {
        val j = job ?: return false
        if (System.currentTimeMillis() > j.until) { job = null; return false }
        return true
    }

    private val tapRunnable = Runnable { tryTap() }

    private fun startTrying() {
        tries = 0
        main.removeCallbacksAndMessages(null)
        val loop = object : Runnable {
            override fun run() {
                if (!active()) return
                tryTap()
                if (++tries < 25) main.postDelayed(this, 600)
            }
        }
        main.postDelayed(loop, 900)
    }

    private fun tryTap() {
        val j = job ?: return
        if (!active()) return
        if (j.kind == "global") { job = null; performGlobalAction(j.query.toIntOrNull() ?: return); return }
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == packageName) return
        when (j.kind) {
            "spotify" -> {
                if (pkg != SPOTIFY) return
                val row = findSongRow(root, j.query) ?: return
                if (row.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    job = null
                    main.postDelayed({ pressPlayIfSilent() }, 2500)
                }
            }
            "wa_send" -> {
                if (pkg !in WA) return
                val send = root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull()
                    ?: byLabel(root, setOf("send", "भेजें", "भेजे", "bhejen"))
                if (clickable(send)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) job = null
            }
            "wa_call", "wa_video" -> {
                if (pkg !in WA) return
                val names = if (j.kind == "wa_video") setOf("video call", "वीडियो कॉल") else setOf("voice call", "call", "वॉइस कॉल", "कॉल")
                val btn = byLabel(root, names) ?: return
                if (clickable(btn)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) job = null
            }
            "switch" -> {
                val name = j.query.substringBefore('|')
                val on = j.query.substringAfter('|') == "true"
                val re = when (name) {
                    "wifi" -> Regex("""(?i)\bwi[\-‑ ]?fi\b|wlan|वाई""")
                    "bluetooth" -> Regex("""(?i)bluetooth|ब्लूटूथ""")
                    "location" -> Regex("""(?i)location|स्थान|लोकेशन""")
                    else -> Regex("""(?i)airplane|aeroplane|flight|हवाई""")
                }
                // Jis switch ke sabse paas naam likha ho (doosre switch ki line ka naam na pakde)
                val sw = findAll(root) { n -> n.isVisibleToUser && n.isCheckable }
                    .mapNotNull { n -> matchDepth(n, re)?.let { n to it } }
                    .minByOrNull { it.second }?.first ?: return
                if (sw.isChecked == on) { job = null; main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 700); return }
                if (clickable(sw)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    job = null
                    main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1200)
                }
            }
            "shutter", "record" -> {
                if (!Regex("camera|cam|snap|gcam", RegexOption.IGNORE_CASE).containsMatchIn(pkg)) return
                val re = if (j.kind == "record") Regex("(?i)^(?:record|start record|start video|video|record video|shutter|रिकॉर्ड)")
                else Regex("(?i)^(?:shutter|take photo|take picture|capture|photo|camera button|shoot|click|शटर|फ़ोटो लें)")
                val btn = findAll(root) { n ->
                    val l = ((n.contentDescription ?: n.text)?.toString() ?: "").trim()
                    n.isVisibleToUser && re.containsMatchIn(l)
                }.firstOrNull() ?: return
                if (clickable(btn)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) job = null
            }
            "speaker" -> {
                if (!Regex("dialer|incall|telecom|phone|contacts|call", RegexOption.IGNORE_CASE).containsMatchIn(pkg)) return
                val spk = findAll(root) { n ->
                    val l = ((n.contentDescription ?: n.text)?.toString() ?: "").trim().lowercase()
                    n.isVisibleToUser && (l == "speaker" || l == "speakerphone" || l == "loudspeaker" ||
                        l == "स्पीकर" || l.startsWith("speaker") || l.contains("speaker,"))
                }.firstOrNull() ?: return
                if (spk.isCheckable && spk.isChecked) { job = null; return }
                if (clickable(spk)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) job = null
            }
        }
    }

    private fun pressPlayIfSilent() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.isMusicActive) return
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (root.packageName?.toString() != SPOTIFY) return
        val play = findAll(root) { n ->
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            (d == "play" || d.startsWith("play ") || d.contains("shuffle play")) && n.isVisibleToUser
        }.firstOrNull()
        clickable(play)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Spotify search results me har gaane ke neeche "Song • Artist" likha hota hai.
     * Sabse upar wala aisa row chuno. Na mile toh jis row me gaane ka naam ho wo.
     */
    private fun findSongRow(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val songRe = Regex("""^(?:e\s*)?(?:song|gaana|गाना|canción|chanson)\s*[•·]""", RegexOption.IGNORE_CASE)
        val songLabels = findAll(root) { n ->
            val t = n.text?.toString()?.trim() ?: return@findAll false
            n.isVisibleToUser && songRe.containsMatchIn(t)
        }
        topMost(songLabels.mapNotNull { clickable(it) })?.let { return it }

        val q = Parser.normName(query).split(" ").filter { it.length >= 3 }
        if (q.isEmpty()) return null
        // Search box ke neeche wale hi (search box me bhi wahi naam likha hota hai)
        val r = android.graphics.Rect()
        val boxBottom = findAll(root) { it.isEditable }.maxOfOrNull { it.getBoundsInScreen(r); r.bottom } ?: 0
        val byName = findAll(root) { n ->
            val t = Parser.normName(n.text?.toString() ?: "")
            n.isVisibleToUser && !n.isEditable && t.isNotBlank() && q.all { it in t }
        }.filter { n -> n.getBoundsInScreen(r); r.top > boxBottom }
        return topMost(byName.mapNotNull { clickable(it) }.filter { !it.isEditable && run { it.getBoundsInScreen(r); r.top >= boxBottom } })
    }

    /** Switch ka naam kitni door likha hai: 0 = khud, 1 = uski line, 2-3 = upar. Na mile toh null */
    private fun matchDepth(n: AccessibilityNodeInfo, re: Regex): Int? {
        val own = listOfNotNull(n.text, n.contentDescription).joinToString(" ")
        if (re.containsMatchIn(own)) return 0
        var p = n.parent
        var depth = 1
        while (p != null && depth <= 3) {
            // Is line me koi aur switch bhi ho toh ye naam uska bhi ho sakta hai — aage mat badho
            val switches = findAll(p) { it.isCheckable }.size
            val texts = findAll(p) { it.text != null || it.contentDescription != null }
                .joinToString(" ") { listOfNotNull(it.text, it.contentDescription).joinToString(" ") }
            if (re.containsMatchIn(texts)) return if (switches > 1) depth + 10 else depth
            p = p.parent; depth++
        }
        return null
    }

    /** Button jiska naam (contentDescription / text) inme se ho */
    private fun byLabel(root: AccessibilityNodeInfo, names: Set<String>): AccessibilityNodeInfo? =
        findAll(root) { n ->
            val l = ((n.contentDescription ?: n.text)?.toString() ?: "").trim().lowercase()
            n.isVisibleToUser && l in names
        }.firstOrNull()

    private fun clickable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = n
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun topMost(list: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val r = android.graphics.Rect()
        return list.minByOrNull { it.getBoundsInScreen(r); r.top }
    }

    private fun findAll(root: AccessibilityNodeInfo, test: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var seen = 0
        while (stack.isNotEmpty() && seen < 2500) {
            val n = stack.removeLast()
            seen++
            if (test(n)) out.add(n)
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.add(it) }
        }
        return out
    }
}
