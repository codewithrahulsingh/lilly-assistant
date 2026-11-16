package com.rahul.lilly

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Khaali listener: iska kaam sirf itna hai ki user "Notification access" de sake.
 * Us permission se Lilly music apps (Spotify) ko seedha "ye gaana chalao" bol paati hai.
 * Lilly notifications padhti ya kahin bhejti nahi hai.
 */
class MediaAccess : NotificationListenerService()

/** Gaana sirf search hokar na ruke, sach me bajne lage — iske tareeke. */
object MusicControl {
    private val main = Handler(Looper.getMainLooper())

    fun hasAccess(ctx: Context): Boolean {
        val list = try {
            Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        } catch (e: Exception) { null } ?: return false
        return list.split(":").any { it.startsWith(ctx.packageName + "/") }
    }

    private fun controller(ctx: Context, pkg: String): MediaController? = try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm.getActiveSessions(ComponentName(ctx, MediaAccess::class.java)).firstOrNull { it.packageName == pkg }
    } catch (e: Exception) {
        null
    }

    private fun isPlaying(c: MediaController?): Boolean {
        val st = c?.playbackState?.state ?: return false
        return st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
    }

    /**
     * App khulne ke baad uske media session ko "playFromSearch" bhejo.
     * App ko khulne me waqt lagta hai, isliye kuch second tak koshish karte hain.
     */
    fun playFromSearch(ctx: Context, pkg: String, query: String) {
        if (!hasAccess(ctx)) return
        val app = ctx.applicationContext
        var attempt = 0
        var sent = 0
        val step = object : Runnable {
            override fun run() {
                attempt++
                val c = controller(app, pkg)
                if (c == null) {
                    if (attempt < 12) main.postDelayed(this, 700)
                    return
                }
                // Ek baar bhej chuke aur bajna shuru ho gaya, ya do baar koshish ho gayi: bas
                if (sent >= 2 || (sent > 0 && isPlaying(c))) return
                try {
                    val extras = Bundle()
                    extras.putString(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    c.transportControls.playFromSearch(query, extras)
                } catch (e: Exception) { }
                sent++
                if (sent < 2) main.postDelayed(this, 3000)
            }
        }
        main.postDelayed(step, 1500)
    }

    /**
     * YouTube par pehla result dhoondo. Video ID mil jaaye toh YouTube us video ko seedha chala deta hai
     * (sirf search page par nahi rukta). Network call hai — background thread se bulao.
     */
    fun firstYouTubeVideo(query: String): String? {
        var c: HttpURLConnection? = null
        return try {
            val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            )
            c.setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
            if (c.responseCode != 200) return null
            val html = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            findVideoId(html)
        } catch (e: Exception) {
            null
        } finally {
            try { c?.disconnect() } catch (e: Exception) { }
        }
    }

    private val videoRe = Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"")
    private val anyIdRe = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /** Search page ke HTML se pehle asli video ka ID (ads/shorts chhodkar) */
    fun findVideoId(html: String): String? =
        (videoRe.find(html) ?: anyIdRe.find(html))?.groupValues?.get(1)
}
