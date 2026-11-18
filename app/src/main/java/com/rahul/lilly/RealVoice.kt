package com.rahul.lilly

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Asli insaan jaisi ladki ki awaaz — Google Gemini TTS se (wahi free Gemini key).
 *
 * - Jo ek baar bola, uski awaaz phone par save ho jaati hai: agli baar turant, bina internet.
 * - Internet/limit/koi bhi dikkat ho toh Lilly phone ki apni awaaz se bol deti hai (kabhi chup nahi).
 */
class RealVoice(private val ctx: Context) {

    data class VoiceOption(val name: String, val about: String, val male: Boolean = false)

    companion object {
        /** Gemini ki female awaazein */
        val VOICES = listOf(
            VoiceOption("Sulafat", "Warm"),
            VoiceOption("Aoede", "Breezy, relaxed"),
            VoiceOption("Leda", "Youthful"),
            VoiceOption("Achernar", "Soft"),
            VoiceOption("Vindemiatrix", "Gentle"),
            VoiceOption("Despina", "Smooth"),
            VoiceOption("Zephyr", "Bright"),
            VoiceOption("Kore", "Firm, confident"),
            // Ladke ki awaazein
            VoiceOption("Puck", "Upbeat", true),
            VoiceOption("Achird", "Friendly", true),
            VoiceOption("Charon", "Calm, clear", true),
            VoiceOption("Orus", "Firm", true),
            VoiceOption("Fenrir", "Energetic", true),
            VoiceOption("Algieba", "Smooth", true)
        )

        fun isMale(name: String) = VOICES.firstOrNull { it.name == name }?.male == true
        const val DEFAULT = "Sulafat"
        const val OFF = "off"

        private val MODELS = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")
        private const val URL_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val WAIT_MS = 9000L
        private const val MAX_CACHE = 300

        /** Limit/galti ke baad kuch der phone ki awaaz se kaam chalao */
        @Volatile private var pausedUntil = 0L
        @Volatile private var goodModel: String? = null

        /** Pichhli baar real awaaz kyun nahi aayi — screen par dikhane ke liye */
        @Volatile var lastError: String? = null
    }

    private val prefs = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    // Nayi lines (AI jawab) — purani apne aap hat-ti hain
    private val dir = File(ctx.filesDir, "voice").apply { mkdirs() }
    // Roz ki pakki lines — kabhi nahi hat-ti (phone ki safai me bhi nahi)
    private val keepDir = File(ctx.filesDir, "voice-keep").apply { mkdirs() }
    private var player: MediaPlayer? = null
    private var token = 0

    val voice: String get() = prefs.getString("real_voice", DEFAULT) ?: DEFAULT
    private val key: String get() = (prefs.getString("ai_key", "") ?: "").filter { !it.isWhitespace() }

    /** Real awaaz chalu hai aur Gemini key hai */
    fun enabled(): Boolean = voice != OFF && key.isNotEmpty() && !key.startsWith("sk-ant-")

    fun label(): String = if (enabled()) "Real voice: $voice" else "Phone voice"

    fun isMale(): Boolean = isMale(voice)

    /** Kitni pakki lines ban chuki (x / total) */
    fun keptCount(lines: List<String>): Int = lines.count { keepFile(voice, it).exists() }

    /**
     * Bolo. onDone = bolna khatam. onFallback = real awaaz nahi mil payi, phone ki awaaz se bolo.
     * Dono me se sirf ek hi chalega.
     */
    fun speak(
        text: String, onDone: () -> Unit, onFallback: () -> Unit,
        waitMs: Long = WAIT_MS, force: Boolean = false, voiceName: String? = null
    ) {
        stop()
        val my = ++token
        val v = voiceName ?: voice
        val kept = keepFile(v, text)
        if (kept.exists() && kept.length() > 100) {
            play(kept, my, onDone, onFallback)
            return
        }
        val file = cacheFile(v, text)
        if (file.exists() && file.length() > 100) {
            file.setLastModified(System.currentTimeMillis())
            play(file, my, onDone, onFallback)
            return
        }
        if (!force && System.currentTimeMillis() < pausedUntil) { onFallback(); return }

        var settled = false
        // Zyada der lage toh intezaar mat karao — phone ki awaaz se bol do
        val giveUp = Runnable {
            if (!settled && my == token) {
                settled = true
                lastError = "Google took more than ${waitMs / 1000} seconds to make the voice"
                onFallback()
            }
        }
        main.postDelayed(giveUp, waitMs)
        Thread {
            val ok = try { fetch(text, v, file) } catch (e: Exception) {
                lastError = when (e) {
                    is java.net.UnknownHostException -> "No internet connection"
                    is java.net.SocketTimeoutException -> "Google took too long to answer"
                    else -> "${e.javaClass.simpleName}: ${e.message ?: ""}"
                }
                false
            }
            main.post {
                main.removeCallbacks(giveUp)
                if (settled || my != token) return@post // der se aaya: agli baar ke liye save ho gaya
                settled = true
                if (ok) play(file, my, onDone, onFallback) else onFallback()
            }
        }.start()
    }

    @Volatile private var prefetching = false

    /**
     * Roz ki lines ek-ek karke Gemini awaaz me bana ke hamesha ke liye save karo.
     * Dheere-dheere (free limit ka dhyan), limit aaye toh ruk jao — agli baar wahin se aage.
     */
    fun prefetch(vararg lines: String) {
        if (!enabled() || prefetching) return
        val v = voice
        val todo = lines.filter { !keepFile(v, it).exists() }
        if (todo.isEmpty()) return
        prefetching = true
        Thread {
            try {
                for (l in todo.take(25)) { // ek baar me thodi hi (free limit bachi rahe)
                    if (System.currentTimeMillis() < pausedUntil || voice != v) break
                    val f = keepFile(v, l)
                    if (f.exists()) continue
                    // Pehle se bani ho (cache me) toh wahi pakki kar do
                    val c = cacheFile(v, l)
                    if (c.exists() && c.length() > 100 && c.renameTo(f)) continue
                    val ok = try { fetch(l, v, f) } catch (e: Exception) { false }
                    if (!ok) break
                    Thread.sleep(20_000) // free limit: minute me kuch hi
                }
            } finally { prefetching = false }
        }.start()
    }

    fun stop() {
        token++
        val p = player
        player = null
        try { p?.stop() } catch (e: Exception) { }
        try { p?.release() } catch (e: Exception) { }
    }

    // ---------------- Andar ka kaam ----------------

    private fun play(file: File, my: Int, onDone: () -> Unit, onFallback: () -> Unit) {
        if (my != token) return
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { finished(it, my, onDone) }
            p.setOnErrorListener { mp, _, _ ->
                file.delete()
                if (player === mp) player = null
                try { mp.release() } catch (e: Exception) { }
                if (my == token) onFallback()
                true
            }
            p.prepare()
            player = p
            p.start()
        } catch (e: Exception) {
            file.delete()
            if (my == token) onFallback()
        }
    }

    private fun finished(p: MediaPlayer, my: Int, onDone: () -> Unit) {
        if (player === p) player = null
        try { p.release() } catch (e: Exception) { }
        if (my == token) onDone()
    }

    private fun keepFile(voice: String, text: String): File {
        val md = MessageDigest.getInstance("SHA-1").digest("$voice|$text".toByteArray(Charsets.UTF_8))
        return File(keepDir, md.joinToString("") { "%02x".format(it) } + ".wav")
    }

    private fun cacheFile(voice: String, text: String): File {
        val md = MessageDigest.getInstance("SHA-1").digest("$voice|$text".toByteArray(Charsets.UTF_8))
        return File(dir, md.joinToString("") { "%02x".format(it) } + ".wav")
    }

    /** Gemini se awaaz lo aur WAV file me save karo */
    private fun fetch(text: String, voice: String, out: File): Boolean {
        val key = this.key
        if (key.isEmpty()) return false
        val who = if (isMale(voice)) "a young Indian man" else "a young Indian woman"
        val prompt = "Say this in a warm, friendly, natural voice, like $who talking to a friend. " +
            "Speak Hindi words with natural Hindi pronunciation and English words with natural Indian English: $text"
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject().put(
                            "voiceConfig", JSONObject().put(
                                "prebuiltVoiceConfig", JSONObject().put("voiceName", voice)
                            )
                        )
                    )
            ).toString()

        val models = listOfNotNull(goodModel) + MODELS.filter { it != goodModel }
        val errors = ArrayList<String>()
        for (model in models) {
            val c = URL("$URL_BASE/$model:generateContent").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 5000
                c.readTimeout = 20000
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code !in 200..299) {
                    val msg = try {
                        val err = c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                        JSONObject(err).optJSONObject("error")?.optString("message") ?: ""
                    } catch (e: Exception) { "" }
                    errors.add("$model → HTTP $code ${msg.take(160)}")
                    lastError = errors.joinToString("\n")
                    if (code == 401 || code == 403) {
                        pausedUntil = System.currentTimeMillis() + 30 * 60_000L
                        return false
                    }
                    continue // ye model nahi chala (ya iski limit khatam), agla try karo
                }
                val res = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val pcm = audioFrom(res)
                if (pcm == null) {
                    errors.add("$model → no audio in the reply")
                    lastError = errors.joinToString("\n")
                    continue
                }
                goodModel = model
                lastError = null
                writeWav(out, pcm, rateFrom(res))
                trimCache()
                return true
            } finally {
                c.disconnect()
            }
        }
        // Koi model nahi chala: thodi der phone ki awaaz (limit ho toh zyada der)
        val limited = errors.any { "HTTP 429" in it }
        pausedUntil = System.currentTimeMillis() + (if (limited) 15 else 3) * 60_000L
        return false
    }

    private fun audioFrom(res: String): ByteArray? {
        val parts = JSONObject(res).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data")
            if (!data.isNullOrEmpty()) return Base64.decode(data, Base64.DEFAULT)
        }
        return null
    }

    /** mimeType jaise "audio/L16;codec=pcm;rate=24000" se sample rate */
    private fun rateFrom(res: String): Int =
        Regex("""rate=(\d+)""").find(res)?.groupValues?.get(1)?.toIntOrNull() ?: 24000

    private fun writeWav(out: File, pcm: ByteArray, rate: Int) {
        val tmp = File(out.path + ".tmp")
        RandomAccessFile(tmp, "rw").use { f ->
            f.setLength(0)
            fun le32(v: Int) { f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())) }
            fun le16(v: Int) { f.write(byteArrayOf(v.toByte(), (v shr 8).toByte())) }
            f.writeBytes("RIFF"); le32(36 + pcm.size); f.writeBytes("WAVE")
            f.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
            f.writeBytes("data"); le32(pcm.size); f.write(pcm)
        }
        tmp.renameTo(out)
    }

    private fun trimCache() {
        val files = dir.listFiles { f -> f.name.endsWith(".wav") } ?: return
        if (files.size <= MAX_CACHE) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_CACHE).forEach { it.delete() }
    }
}
