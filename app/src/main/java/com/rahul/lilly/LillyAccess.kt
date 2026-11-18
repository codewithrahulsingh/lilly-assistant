package com.rahul.lilly

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Spotify me gaana sach me chalane ke liye: Lilly Spotify me gaana search karti hai,
 * phir ye service search results me pehle "Song" par khud tap kar deti hai.
 *
 * Sirf Spotify ki screen dekhti hai (accessibility config me packageNames = Spotify),
 * aur sirf tab kuch karti hai jab Lilly ne abhi-abhi gaana chalane ko kaha ho.
 */
class LillyAccess : AccessibilityService() {

    companion object {
        @Volatile private var instance: LillyAccess? = null
        @Volatile private var wanted: String? = null
        @Volatile private var deadline = 0L
        @Volatile private var tapped = false
        private const val SPOTIFY = "com.spotify.music"

        /** Accessibility me Lilly ON hai? */
        fun enabled(ctx: Context): Boolean {
            if (instance != null) return true
            val list = try {
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            } catch (e: Exception) { null } ?: return false
            val me = ComponentName(ctx, LillyAccess::class.java).flattenToString()
            return list.split(':').any { it.equals(me, ignoreCase = true) }
        }

        /** Lilly ne Spotify me ye gaana search kiya hai — results aate hi pehle gaane par tap karo */
        fun playOnSpotify(query: String) {
            wanted = query
            deadline = System.currentTimeMillis() + 15_000
            tapped = false
            instance?.startTrying()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var tries = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != SPOTIFY) return
        if (active()) main.postDelayed({ tryTap() }, 400)
    }

    private fun active(): Boolean = wanted != null && !tapped && System.currentTimeMillis() < deadline

    private fun startTrying() {
        tries = 0
        main.removeCallbacksAndMessages(null)
        val loop = object : Runnable {
            override fun run() {
                if (!active()) return
                tryTap()
                if (++tries < 20) main.postDelayed(this, 700)
            }
        }
        main.postDelayed(loop, 1200)
    }

    /** Search results me pehla gaana dhoondh kar tap */
    private fun tryTap() {
        if (!active()) return
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (root.packageName?.toString() != SPOTIFY) return
        val target = findSongRow(root) ?: return
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            tapped = true
            // Gaane ki jagah artist/album khul gaya ho toh wahan "Play" dabao
            main.postDelayed({ pressPlayIfSilent() }, 2500)
        }
    }

    private fun pressPlayIfSilent() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.isMusicActive) { wanted = null; return }
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (root.packageName?.toString() != SPOTIFY) return
        val play = findAll(root) { n ->
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            (d == "play" || d.startsWith("play ") || d.contains("shuffle play")) && n.isVisibleToUser
        }.firstOrNull()
        clickable(play)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        wanted = null
    }

    /**
     * Spotify search results me har gaane ke neeche "Song • Artist" likha hota hai.
     * Sabse upar wala aisa row chuno. Na mile toh jis row me gaane ka naam ho wo.
     */
    private fun findSongRow(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val songRe = Regex("""^(?:e\s*)?(?:song|gaana|गाना|canción|chanson)\s*[•·]""", RegexOption.IGNORE_CASE)
        val songLabels = findAll(root) { n ->
            val t = n.text?.toString()?.trim() ?: return@findAll false
            n.isVisibleToUser && songRe.containsMatchIn(t)
        }
        topMost(songLabels.mapNotNull { clickable(it) })?.let { return it }

        // Dusra tareeka: gaane ke naam wala row (search box ke alawa)
        val q = Parser.normName(wanted ?: return null).split(" ").filter { it.length >= 3 }
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
