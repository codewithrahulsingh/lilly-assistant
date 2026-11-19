package com.rahul.lilly

import android.content.Context
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Spotify ka official "App Remote" — Lilly seedha Spotify ko bolti hai "ye gaana chalao".
 * Na Accessibility helper, na tap. Pehli baar Spotify "Allow" puchta hai (app me "Connect Spotify").
 */
object SpotifyRemote {

    const val CLIENT_ID = "6d2bbb26322a4b60b00a658b2d0169ef"
    const val REDIRECT = "lilly://spotify-callback"

    @Volatile private var remote: SpotifyAppRemote? = null
    @Volatile var lastError: String? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)

    /** Ek baar "Allow" ho chuka hai */
    fun authorized(ctx: Context) = prefs(ctx).getBoolean("spotify_remote_ok", false)

    fun installed(ctx: Context) = try { SpotifyAppRemote.isSpotifyInstalled(ctx) } catch (e: Throwable) { false }

    /** Main thread se bulao. showAuth = pehli baar "Allow" wali screen dikhao (app ke andar se) */
    fun connect(ctx: Context, showAuth: Boolean, done: (SpotifyAppRemote?) -> Unit) {
        remote?.let { if (it.isConnected) { done(it); return } }
        val params = ConnectionParams.Builder(CLIENT_ID).setRedirectUri(REDIRECT).showAuthView(showAuth).build()
        try {
            SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
                override fun onConnected(r: SpotifyAppRemote) {
                    remote = r
                    lastError = null
                    prefs(ctx).edit().putBoolean("spotify_remote_ok", true).apply()
                    done(r)
                }

                override fun onFailure(t: Throwable) {
                    lastError = "${t.javaClass.simpleName}: ${t.message ?: ""}"
                    done(null)
                }
            })
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: ""}"
            done(null)
        }
    }

    /** spotify:track:… / spotify:artist:… chalao. done(true) = baj gaya */
    fun play(ctx: Context, uri: String, showAuth: Boolean = false, done: (Boolean) -> Unit) {
        connect(if (showAuth) ctx else ctx.applicationContext, showAuth) { r ->
            if (r == null) { done(false); return@connect }
            try {
                r.playerApi.play(uri)
                    .setResultCallback { done(true) }
                    .setErrorCallback { t -> lastError = t.message; done(false) }
            } catch (e: Throwable) {
                lastError = e.message
                done(false)
            }
        }
    }

    // ---------------- Control (background thread se bulao: jawab aane tak rukta hai, max 5 sec) ----------------

    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    /** Spotify se jud ke kaam karo. op me result do: done(text/null). Returns null = Spotify se nahi juda */
    private fun <T> blocking(ctx: Context, op: (SpotifyAppRemote, (T?) -> Unit) -> Unit): Pair<Boolean, T?> {
        val latch = java.util.concurrent.CountDownLatch(1)
        var connected = false
        var out: T? = null
        ui.post {
            connect(ctx.applicationContext, false) { r ->
                if (r == null) { latch.countDown(); return@connect }
                connected = true
                try { op(r) { v -> out = v; latch.countDown() } } catch (e: Throwable) { lastError = e.message; latch.countDown() }
            }
        }
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        return connected to out
    }

    /** Abhi kaunsa gaana: "Kesariya — Arijit Singh" */
    fun nowPlaying(ctx: Context): Pair<Boolean, String?> = blocking<String>(ctx) { r, done ->
        r.playerApi.playerState.setResultCallback { st ->
            val t = st.track
            done(t?.let { it.name + (it.artist?.name?.let { a -> " — $a" } ?: "") })
        }.setErrorCallback { done(null) }
    }

    /** Ek simple kaam (shuffle, repeat, seek, like, queue…). true = ho gaya */
    fun run(ctx: Context, action: String, arg: String = ""): Pair<Boolean, Boolean?> = blocking<Boolean>(ctx) { r, done ->
        val p = r.playerApi
        val ok: (Any?) -> Unit = { done(true) }
        val bad = com.spotify.protocol.client.ErrorCallback { t -> lastError = t.message; done(false) }
        when (action) {
            "shuffle_on" -> p.setShuffle(true).setResultCallback { ok(it) }.setErrorCallback(bad)
            "shuffle_off" -> p.setShuffle(false).setResultCallback { ok(it) }.setErrorCallback(bad)
            "repeat_on" -> p.setRepeat(1).setResultCallback { ok(it) }.setErrorCallback(bad)   // 1 = yahi gaana baar-baar
            "repeat_off" -> p.setRepeat(0).setResultCallback { ok(it) }.setErrorCallback(bad)
            "restart" -> p.seekTo(0).setResultCallback { ok(it) }.setErrorCallback(bad)
            "forward" -> p.seekToRelativePosition((arg.toLongOrNull() ?: 10) * 1000).setResultCallback { ok(it) }.setErrorCallback(bad)
            "rewind" -> p.seekToRelativePosition(-(arg.toLongOrNull() ?: 10) * 1000).setResultCallback { ok(it) }.setErrorCallback(bad)
            "pause" -> p.pause().setResultCallback { ok(it) }.setErrorCallback(bad)
            "resume" -> p.resume().setResultCallback { ok(it) }.setErrorCallback(bad)
            "next" -> p.skipNext().setResultCallback { ok(it) }.setErrorCallback(bad)
            "previous" -> p.skipPrevious().setResultCallback { ok(it) }.setErrorCallback(bad)
            "queue", "playuri" -> if (arg.startsWith("spotify:")) {
                (if (action == "queue") p.queue(arg) else p.play(arg)).setResultCallback { ok(it) }.setErrorCallback(bad)
            } else done(false)
            "like" -> p.playerState.setResultCallback { st ->
                val u = st.track?.uri
                if (u == null) done(false) else r.userApi.addToLibrary(u).setResultCallback { ok(it) }.setErrorCallback(bad)
            }.setErrorCallback(bad)
            else -> done(false)
        }
    }

    // ---------------- Gaane ka Spotify link dhoondhna (bina Spotify login ke) ----------------

    private val linkRe = Regex(
        """open\.spotify\.com(?:/|%2F)(?:intl-[a-z]{2}(?:/|%2F))?(track|artist|playlist|album)(?:/|%2F)([A-Za-z0-9]{22})"""
    )

    /**
     * "Kesariya" -> spotify:track:… ; "Arijit Singh" -> spotify:artist:…
     * Web search se pehla Spotify link (background thread par chalao). Na mile toh null.
     */
    fun findUri(query: String, artistFirst: Boolean = false, prefer: String? = null): String? {
        val q = URLEncoder.encode("site:open.spotify.com $query", "UTF-8")
        for (url in listOf("https://html.duckduckgo.com/html/?q=$q", "https://www.bing.com/search?q=$q")) {
            val html = get(url) ?: continue
            val all = linkRe.findAll(html).map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()
            if (all.isEmpty()) continue
            val pick = (prefer?.let { p -> all.firstOrNull { it.first == p } })
                ?: (if (artistFirst) all.firstOrNull { it.first == "artist" } else null)
                ?: all.firstOrNull { it.first == "track" } ?: all.first()
            return "spotify:${pick.first}:${pick.second}"
        }
        return null
    }

    private fun get(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            c.setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
            if (c.responseCode != 200) null
            else URLDecoder.decode(c.inputStream.bufferedReader().use { it.readText() }.replace("%(?![0-9a-fA-F]{2})".toRegex(), "%25"), "UTF-8")
        } catch (e: Exception) {
            null
        } finally {
            try { c?.disconnect() } catch (e: Exception) { }
        }
    }
}
