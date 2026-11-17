package com.rahul.lilly

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Gaana sirf search hokar na ruke, sach me bajne lage. */
object MusicControl {

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
