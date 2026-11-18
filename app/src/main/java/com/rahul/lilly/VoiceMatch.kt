package com.rahul.lilly

import android.content.Context
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

/**
 * Voice match: Lilly sirf Rahul ki awaaz par jaage (Siri ke "Voice Match" jaisa).
 * Awaaz pehchaanne ka model phone me chalta hai (bina internet); awaaz kahin bheji nahi jaati.
 */
object VoiceMatch {

    private const val MODEL_URL =
        "https://github.com/codewithrahulsingh/lilly-assistant/releases/download/voices/voice-match.onnx"
    const val MODEL_MB = 28

    @Volatile private var extractor: SpeakerEmbeddingExtractor? = null
    private val lock = Any()
    @Volatile var lastScore: Float = -1f
    @Volatile var lastError: String? = null
    @Volatile private var print: VoicePrint? = null
    val gate = WakeGate()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)
    private fun modelFile(ctx: Context) = File(ctx.filesDir, "voicematch.onnx")
    private fun printFile(ctx: Context) = File(ctx.filesDir, "voiceprint.json")

    fun modelReady(ctx: Context) = modelFile(ctx).let { it.exists() && it.length() > 1_000_000 }

    fun voicePrint(ctx: Context): VoicePrint {
        print?.let { return it }
        val p = VoicePrint.fromJson(try { printFile(ctx).readText() } catch (e: Exception) { null })
        print = p
        return p
    }

    fun save(ctx: Context, p: VoicePrint) {
        print = p
        try { printFile(ctx).writeText(p.toJson()) } catch (e: Exception) { }
    }

    /** Voice match chalu hai aur sikhaya ja chuka hai */
    fun active(ctx: Context) = prefs(ctx).getBoolean("voice_match", false) && modelReady(ctx) && voicePrint(ctx).ready

    fun level(ctx: Context) = prefs(ctx).getString("voice_match_level", "normal") ?: "normal"

    fun setOn(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("voice_match", on).apply()

    fun reset(ctx: Context) {
        save(ctx, VoicePrint())
        printFile(ctx).delete()
        setOn(ctx, false)
    }

    private fun engine(ctx: Context): SpeakerEmbeddingExtractor? {
        extractor?.let { return it }
        synchronized(lock) {
            extractor?.let { return it }
            if (!modelReady(ctx)) { lastError = "Voice match model not downloaded"; return null }
            return try {
                SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(model = modelFile(ctx).absolutePath, numThreads = 2))
                    .also { extractor = it }
            } catch (e: Throwable) {
                lastError = "Voice match didn't start: ${e.javaClass.simpleName} ${e.message ?: ""}"
                null
            }
        }
    }

    fun warmUp(ctx: Context) {
        if (!active(ctx) || extractor != null) return
        val app = ctx.applicationContext
        Thread { engine(app) }.start()
    }

    /** Aage-peeche ki khamoshi hatao (sirf bolne wala hissa) */
    fun trim(s: FloatArray, rate: Int = 16000): FloatArray {
        val frame = rate / 50 // 20 ms
        val n = s.size / frame
        if (n < 5) return s
        val rms = FloatArray(n) { f ->
            var sum = 0.0
            for (i in f * frame until (f + 1) * frame) sum += s[i] * s[i]
            sqrt(sum / frame).toFloat()
        }
        val peak = rms.maxOrNull() ?: 0f
        val th = maxOf(0.006f, peak * 0.12f)
        var a = rms.indexOfFirst { it > th }
        var b = rms.indexOfLast { it > th }
        if (a < 0 || b < a) return s
        a = maxOf(0, a - 5); b = minOf(n - 1, b + 5) // 100 ms gaddi
        return s.copyOfRange(a * frame, minOf(s.size, (b + 1) * frame))
    }

    /** Awaaz bahut dheemi / khali toh null */
    fun loudEnough(s: FloatArray): Boolean {
        if (s.size < 8000) return false
        var sum = 0.0
        for (x in s) sum += x * x
        return sqrt(sum / s.size) > 0.004
    }

    fun embed(ctx: Context, samples: FloatArray): FloatArray? {
        val e = engine(ctx) ?: return null
        return try {
            val st = e.createStream()
            st.acceptWaveform(samples, 16000)
            st.inputFinished()
            val out = if (e.isReady(st)) e.compute(st) else null
            st.release()
            if (out == null) lastError = "Too short to recognise the voice"
            out
        } catch (t: Throwable) {
            lastError = "${t.javaClass.simpleName}: ${t.message ?: ""}"
            null
        }
    }

    /**
     * Jaagne wali awaaz Rahul ki hai? (background thread par chalao)
     * Model/print me dikkat ho toh true (Lilly band na pade).
     */
    fun verify(ctx: Context, clip: FloatArray): Boolean {
        val p = voicePrint(ctx)
        val s = trim(clip)
        val e = embed(ctx, if (s.size >= 12000) s else clip) ?: return true
        val score = p.score(e)
        lastScore = score
        val lv = level(ctx)
        val ok = gate.decide(score, lv)
        if (ok && p.learn(e, score, lv)) save(ctx, p)
        return ok
    }

    /** Model download (ek baar). done(true/false, galti) — background se aata hai */
    fun download(ctx: Context, progress: (Int) -> Unit, done: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val tmp = File(app.filesDir, "voicematch.part")
            var err = ""
            try {
                val c = URL(MODEL_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Lilly-app")
                if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
                val size = c.contentLengthLong.takeIf { it > 0 } ?: (MODEL_MB * 1024L * 1024L)
                var read = 0L
                var last = -1
                c.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            val pct = (read * 100 / size).toInt().coerceAtMost(99)
                            if (pct != last) { last = pct; progress(pct) }
                        }
                    }
                }
                c.disconnect()
                if (tmp.length() < 1_000_000) throw IllegalStateException("download incomplete")
                modelFile(app).delete()
                if (!tmp.renameTo(modelFile(app))) throw IllegalStateException("couldn't save")
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
                tmp.delete()
            }
            done(err.isEmpty(), err)
        }.start()
    }
}
