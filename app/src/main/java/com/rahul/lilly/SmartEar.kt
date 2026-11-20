package com.rahul.lilly

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

/**
 * "Smart listening": Lilly khud awaaz record karti hai aur Gemini se likhwati hai —
 * Hinglish bilkul sahi Roman me ("missed call", "WiFi", "notifications check karo"), aapke contacts ke naam sahi spelling me.
 * Internet / limit na ho toh Lilly purane Google recognizer par laut aati hai.
 */
object SmartEar {

    private const val RATE = 16000
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile var lastError: String? = null
    @Volatile private var pausedUntil = 0L
    @Volatile private var goodModel: String? = null
    private val MODELS = listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.0-flash")

    fun available(): Boolean = System.currentTimeMillis() > pausedUntil

    /** Thodi der Google recognizer se kaam chalao (limit / galti ke baad) */
    fun pause(minutes: Int) { pausedUntil = System.currentTimeMillis() + minutes * 60_000L }

    fun stop() { running = false }

    /**
     * Bolna shuru hone ka intezaar, phir chup hote hi band (max 9 sec).
     * level(0..1) awaaz ki lehar; done(pcm) — null = kuch bola hi nahi
     */
    @SuppressLint("MissingPermission")
    fun record(level: (Float) -> Unit, started: () -> Unit, done: (ShortArray?) -> Unit) {
        running = true
        Thread {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val ar = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE))
            } catch (e: Exception) { null }
            if (ar == null || ar.state != AudioRecord.STATE_INITIALIZED) {
                ar?.release(); lastError = "mic busy"; main.post { done(null) }; return@Thread
            }
            val max = RATE * 9
            val out = ShortArray(max)
            var pos = 0
            val pre = ArrayDeque<ShortArray>() // bolne se pehle ke 300 ms
            val buf = ShortArray(RATE / 50) // 20 ms
            var speech = false
            var speechMs = 0
            var silenceMs = 0
            var waitedMs = 0
            var noise = 0.0
            var frames = 0
            try {
                ar.startRecording()
                while (running) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n <= 0) break
                    var sum = 0.0
                    for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                    val rms = sqrt(sum / n)
                    frames++
                    // Pehle 300 ms me kamre ka shor naapo
                    if (frames <= 15) { noise = (noise * (frames - 1) + rms) / frames; continue }
                    val loud = rms > maxOf(450.0, noise * 2.8)
                    if (frames % 3 == 0) { val lv = (rms / 4000.0).toFloat().coerceIn(0f, 1f); main.post { level(lv) } }
                    if (!speech) {
                        pre.addLast(buf.copyOf(n))
                        if (pre.size > 15) pre.removeFirst()
                        waitedMs += 20
                        if (loud) {
                            speechMs += 20
                            if (speechMs >= 60) {
                                speech = true
                                for (f in pre) { val k = minOf(f.size, max - pos); System.arraycopy(f, 0, out, pos, k); pos += k }
                                pre.clear()
                                main.post { started() }
                            }
                        } else speechMs = 0
                        if (waitedMs > 6000) break // 6 sec tak kuch nahi bola
                    } else {
                        val k = minOf(n, max - pos)
                        System.arraycopy(buf, 0, out, pos, k); pos += k
                        silenceMs = if (loud) 0 else silenceMs + 20
                        if (silenceMs >= 900 || pos >= max) break
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
            } finally {
                try { ar.stop() } catch (e: Exception) { }
                ar.release()
            }
            val pcm = if (speech && running) out.copyOf(pos) else null
            running = false
            main.post { done(pcm) }
        }.start()
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val data = ByteArray(pcm.size * 2)
        for (i in pcm.indices) { data[i * 2] = (pcm[i].toInt() and 0xff).toByte(); data[i * 2 + 1] = (pcm[i].toInt() shr 8 and 0xff).toByte() }
        val o = ByteArrayOutputStream()
        fun s(x: String) = o.write(x.toByteArray())
        fun i32(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff); o.write(v shr 16 and 0xff); o.write(v shr 24 and 0xff) }
        fun i16(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff) }
        s("RIFF"); i32(36 + data.size); s("WAVE"); s("fmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        s("data"); i32(data.size); o.write(data)
        return o.toByteArray()
    }

    @Volatile private var namesCache: String? = null
    @Volatile private var namesAt = 0L

    /** Contacts ke naam (spelling sahi likhne ke liye), max 150 */
    private fun names(ctx: Context): String {
        if (namesCache != null && System.currentTimeMillis() - namesAt < 30 * 60_000L) return namesCache!!
        val set = LinkedHashSet<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                null, null, ContactsContract.Contacts.TIMES_CONTACTED + " DESC"
            )?.use { c -> while (c.moveToNext() && set.size < 150) c.getString(0)?.let { n -> if (n.any { it.isLetter() }) set.add(n.replace(Regex("[^\\p{L} ]"), " ").replace(Regex("\\s+"), " ").trim()) } }
        } catch (e: Exception) { }
        namesCache = set.filter { it.isNotBlank() }.joinToString(", ")
        namesAt = System.currentTimeMillis()
        return namesCache!!
    }

    /** Gemini se likhwao (background thread). null = nahi ho paya */
    fun transcribe(ctx: Context, key: String, pcm: ShortArray, hint: String? = null): String? {
        val names = names(ctx)
        val prompt = "Transcribe this short voice command spoken to a phone assistant named Lilly by an Indian user (Hindi, English or Hinglish). " +
            "Write ONLY the exact words spoken, in Roman (Latin) script: English words in correct English spelling " +
            "(WhatsApp, WiFi, Bluetooth, Spotify, YouTube, missed call, notifications, check, alarm), Hindi words in simple Hinglish " +
            "(karo, kya, hai, gaana, chalao, bhejo, ki). Do not translate, do not answer, no quotes, no punctuation at the end. " +
            (if (names.isNotBlank()) "Names the user may say (use this spelling if heard): $names. " else "") +
            (if (hint != null) "Context: $hint " else "") +
            "If nothing clear was said, output EMPTY."
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav")
                    .put("data", Base64.encodeToString(wav(pcm), Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", 120))
        val models = listOfNotNull(goodModel) + MODELS.filter { it != goodModel }
        for (m in models) {
            val b = JSONObject(body.toString())
            if (m.startsWith("gemini-2.5")) b.getJSONObject("generationConfig").put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 5000
                c.readTimeout = 9000
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.outputStream.use { it.write(b.toString().toByteArray()) }
                val code = c.responseCode
                if (code !in 200..299) {
                    lastError = "HTTP $code"
                    if (code == 429) { pause(15); return null }
                    if (code == 401 || code == 403) { pause(60); return null }
                    continue
                }
                val res = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val text = res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?.let { p -> (0 until p.length()).joinToString("") { p.getJSONObject(it).optString("text") } }?.trim() ?: ""
                goodModel = m
                lastError = null
                val clean = text.lines().firstOrNull { it.isNotBlank() }?.trim()?.trim('"', '.', '`')?.trim() ?: ""
                return if (clean.isBlank() || clean.equals("EMPTY", true)) "" else clean
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName
                if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException) { pause(2); return null }
            } finally { c.disconnect() }
        }
        pause(3)
        return null
    }
}
