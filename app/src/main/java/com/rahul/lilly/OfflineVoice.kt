package com.rahul.lilly

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Phone ke andar chalne wali insaani ladki ki awaaz (Piper "Priyamvada", Hindi) — sherpa-onnx se.
 * Ek baar ~60 MB ka voice pack download, phir hamesha wahi awaaz: bina internet, bina limit, turant.
 */
object OfflineVoice {
    const val PACK_URL = "https://github.com/codewithrahulsingh/lilly-assistant/releases/latest/download/voice-hi-female.zip"
    const val PACK_MB = 60

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var token = 0
    @Volatile private var track: AudioTrack? = null
    private val lock = Any()

    fun dir(ctx: Context) = File(ctx.filesDir, "voicepack")

    private fun model(ctx: Context): File? = dir(ctx).listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull()

    fun installed(ctx: Context): Boolean =
        model(ctx) != null && File(dir(ctx), "tokens.txt").exists() && File(dir(ctx), "espeak-ng-data").isDirectory

    /** Engine ek baar banao (2-3 sec), phir wahi */
    private fun engine(ctx: Context): OfflineTts? {
        tts?.let { return it }
        synchronized(lock) {
            tts?.let { return it }
            val m = model(ctx) ?: return null
            val d = dir(ctx)
            val cfg = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = m.absolutePath,
                        tokens = File(d, "tokens.txt").absolutePath,
                        dataDir = File(d, "espeak-ng-data").absolutePath
                    ),
                    numThreads = 2
                )
            )
            val t = try { OfflineTts(assetManager = null, config = cfg) } catch (e: Throwable) { null }
            tts = t
            return t
        }
    }

    /** App khulte hi engine tayyar kar lo, taaki pehli line me der na ho */
    fun warmUp(ctx: Context) {
        if (!installed(ctx) || tts != null) return
        val app = ctx.applicationContext
        Thread { engine(app) }.start()
    }

    /** Bolo. onDone = khatam; onFail = awaaz nahi bani (tab phone ki awaaz) */
    fun speak(ctx: Context, text: String, speed: Float, onDone: () -> Unit, onFail: () -> Unit) {
        stop()
        val my = ++token
        val app = ctx.applicationContext
        val clean = text.replace(Regex("[*_#`~|<>\\[\\]{}]"), " ").replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) { main.post { onDone() }; return }
        Thread {
            var ok = false
            var t: AudioTrack? = null
            try {
                val e = engine(app) ?: throw IllegalStateException("no voice pack")
                val sr = e.sampleRate()
                val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(maxOf(min, sr * 4 / 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = t
                t.play()
                var total = 0
                // Vakya-vakya ban kar turant bajta hai (poora banne ka intezaar nahi)
                e.generateWithCallback(clean, 0, speed) { samples ->
                    if (my != token) 0 else {
                        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        total += samples.size
                        1
                    }
                }
                // Bacha hua bajne do
                val until = System.currentTimeMillis() + total * 1000L / sr + 1500
                while (my == token && t.playbackHeadPosition < total && System.currentTimeMillis() < until) Thread.sleep(40)
                ok = total > 0
            } catch (e: Throwable) {
                ok = false
            } finally {
                try { t?.stop() } catch (e: Exception) { }
                try { t?.release() } catch (e: Exception) { }
                if (track === t) track = null
            }
            main.post {
                if (my != token) return@post
                if (ok) onDone() else onFail()
            }
        }.start()
    }

    fun stop() {
        token++
        val t = track
        track = null
        try { t?.pause(); t?.flush() } catch (e: Exception) { }
    }

    fun delete(ctx: Context) {
        stop()
        synchronized(lock) {
            try { tts?.release() } catch (e: Throwable) { }
            tts = null
        }
        dir(ctx).deleteRecursively()
    }

    /**
     * Voice pack download + unzip. progress(0..100) main thread par. done(true/false, galti).
     */
    fun download(ctx: Context, progress: (Int) -> Unit, done: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val tmp = File(app.filesDir, "voicepack-new")
            var err = ""
            try {
                tmp.deleteRecursively(); tmp.mkdirs()
                val c = URL(PACK_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Lilly-app")
                if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
                val size = c.contentLengthLong.takeIf { it > 0 } ?: (PACK_MB * 1024L * 1024L)
                var read = 0L
                var lastPct = -1
                val counting = object : java.io.FilterInputStream(c.inputStream) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0) {
                            read += n
                            val pct = (read * 100 / size).toInt().coerceAtMost(99)
                            if (pct != lastPct) { lastPct = pct; main.post { progress(pct) } }
                        }
                        return n
                    }
                }
                ZipInputStream(counting.buffered()).use { zip ->
                    val root = tmp.canonicalPath
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val out = File(tmp, entry.name)
                        // Zip ke andar "../" jaise raste se bahar file na likhi jaaye
                        if (!out.canonicalPath.startsWith(root + File.separator)) continue
                        if (entry.isDirectory) out.mkdirs()
                        else {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { zip.copyTo(it) }
                        }
                    }
                }
                c.disconnect()
                // Pack ke andar ek folder ho toh uski cheezein upar le aao
                val inner = tmp.listFiles()?.singleOrNull { it.isDirectory && it.name != "espeak-ng-data" }
                val src = if (inner != null && tmp.listFiles()?.none { it.name.endsWith(".onnx") } == true) inner else tmp
                stop()
                synchronized(lock) { try { tts?.release() } catch (e: Throwable) { }; tts = null }
                val dst = dir(app)
                dst.deleteRecursively()
                if (!src.renameTo(dst)) throw IllegalStateException("couldn't save the voice")
                tmp.deleteRecursively()
                if (!installed(app)) throw IllegalStateException("voice files missing")
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
                tmp.deleteRecursively()
            }
            val ok = err.isEmpty()
            if (ok) engine(app)
            main.post { done(ok, err) }
        }.start()
    }
}
