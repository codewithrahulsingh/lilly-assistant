package com.rahul.lilly

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Phone ke andar chalne wali insaani ladki ki awaaz (sherpa-onnx). Ek baar download, phir hamesha wahi awaaz:
 * bina internet, bina limit. Jo awaaz aap chuno wahi pakki — Lilly khud kabhi nahi badalti.
 *
 * Packs: "piper" = Priyamvada (ladki), "pratham" = Pratham (ladka). Dono Hindi + English.
 */
object OfflineVoice {

    data class VoiceChoice(
        val id: String, val name: String, val about: String, val pack: String, val sid: Int, val male: Boolean = false
    )

    /** Rahul ki pasand: ladki (Priyamvada) ya ladka (Pratham). Ladke ki awaaz chuno toh Lilly ladke ki tarah baat karti hai */
    val CHOICES = listOf(
        VoiceChoice("piper", "Priyamvada", "Girl's voice — talks like a girl (karungi, rahi hoon)", "piper", 0),
        VoiceChoice("pratham", "Pratham", "Boy's voice — talks like a boy (karunga, raha hoon)", "pratham", 0, male = true)
    )

    private const val BASE = "https://github.com/codewithrahulsingh/lilly-assistant/releases/download/voices/"
    fun packUrl(pack: String) = BASE + when (pack) {
        "kokoro" -> "voice-kokoro.zip"
        "pratham" -> "voice-hi-male.zip"
        else -> "voice-hi-female.zip"
    }
    fun packMb(pack: String) = if (pack == "kokoro") 125 else 64

    /** Chuni hui awaaz ladke ki hai? (tab baatein bhi ladke wali) */
    fun isMale(ctx: Context): Boolean = chosen(ctx)?.male == true

    /** Purana Kokoro pack (ab list me nahi) — jagah khali karo */
    fun cleanupOld(ctx: Context) {
        val k = dir(ctx, "kokoro")
        if (k.exists()) Thread { k.deleteRecursively() }.start()
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var engine: OfflineTts? = null
    @Volatile private var enginePack: String? = null
    @Volatile private var token = 0
    @Volatile private var track: AudioTrack? = null
    private val lock = Any()

    /** Pichhli baar offline awaaz kyun nahi chali — app me dikhane ke liye */
    @Volatile var lastError: String? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)

    fun dir(ctx: Context, pack: String) = File(ctx.filesDir, when (pack) {
        "kokoro" -> "voicepack-kokoro"
        "pratham" -> "voicepack-pratham"
        else -> "voicepack"
    })

    fun packInstalled(ctx: Context, pack: String): Boolean {
        val d = dir(ctx, pack)
        if (!File(d, "tokens.txt").exists() || !File(d, "espeak-ng-data").isDirectory) return false
        if (d.listFiles { f -> f.name.endsWith(".onnx") }.isNullOrEmpty()) return false
        return pack != "kokoro" || File(d, "voices.bin").exists()
    }

    /** Chuni hui awaaz (pakki). Pehle se kuch na chuna ho toh jo pack download hai uski pehli awaaz */
    fun chosen(ctx: Context): VoiceChoice? {
        val id = prefs(ctx).getString("offline_voice", null)
        CHOICES.firstOrNull { it.id == id && packInstalled(ctx, it.pack) }?.let { return it }
        return CHOICES.firstOrNull { packInstalled(ctx, it.pack) }
    }

    fun installed(ctx: Context): Boolean = chosen(ctx) != null

    fun choose(ctx: Context, id: String) {
        prefs(ctx).edit().putString("offline_voice", id).apply()
    }

    private fun build(ctx: Context, pack: String): OfflineTts? {
        val d = dir(ctx, pack)
        val onnx = d.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull() ?: return null
        val espeak = File(d, "espeak-ng-data").absolutePath
        val tokens = File(d, "tokens.txt").absolutePath
        val model = if (pack == "kokoro") OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(
                model = onnx.absolutePath, voices = File(d, "voices.bin").absolutePath,
                tokens = tokens, dataDir = espeak, lang = "hi"
            ),
            numThreads = 4
        ) else OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = onnx.absolutePath, tokens = tokens, dataDir = espeak),
            numThreads = 2
        )
        return try { OfflineTts(assetManager = null, config = OfflineTtsConfig(model = model)) } catch (e: Throwable) {
            lastError = "Voice engine didn't start: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
            null
        }
    }

    /** Engine ek baar banao (kuch second), phir wahi */
    private fun engineFor(ctx: Context, pack: String): OfflineTts? {
        if (enginePack == pack) engine?.let { return it }
        synchronized(lock) {
            if (enginePack == pack) engine?.let { return it }
            try { engine?.release() } catch (e: Throwable) { }
            engine = null
            enginePack = null
            val t = build(ctx, pack) ?: return null
            engine = t
            enginePack = pack
            return t
        }
    }

    /** App khulte hi engine tayyar kar lo, taaki pehli line me der na ho */
    fun warmUp(ctx: Context) {
        val c = chosen(ctx) ?: return
        if (enginePack == c.pack && engine != null) return
        val app = ctx.applicationContext
        Thread { engineFor(app, c.pack) }.start()
    }

    private fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    /** Bolo (chuni hui awaaz me, ya `voiceId` ho toh us awaaz me — sunke dekhne ke liye) */
    fun speak(
        ctx: Context, text: String, speed: Float, onDone: () -> Unit, onFail: () -> Unit, voiceId: String? = null
    ) {
        stop()
        val my = ++token
        val app = ctx.applicationContext
        val v = (voiceId?.let { id -> CHOICES.firstOrNull { it.id == id && packInstalled(app, it.pack) } }) ?: chosen(app)
        val clean = text.replace(Regex("[*_#`~|<>\\[\\]{}]"), " ").replace(Regex("\\s+"), " ").trim()
        if (v == null) { lastError = "No offline voice downloaded"; main.post { onFail() }; return }
        if (clean.isBlank()) { main.post { onDone() }; return }
        Thread {
            var ok = false
            var t: AudioTrack? = null
            try {
                val e = engineFor(app, v.pack) ?: throw IllegalStateException(lastError ?: "voice engine missing")
                val sr = e.sampleRate()
                val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                val at = AudioTrack.Builder()
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
                t = at
                track = at
                at.play()
                var total = 0
                val write: (FloatArray) -> Int = { samples ->
                    if (my != token) 0 else {
                        at.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        total += samples.size
                        1
                    }
                }
                // Vakya-vakya ban kar turant bajta hai (poora banne ka intezaar nahi)
                if (v.pack == "kokoro") {
                    // Hindi lipi = Hindi uchcharan, warna English — awaaz wahi rehti hai
                    val lang = if (hasDevanagari(clean)) "hi" else "en-us"
                    e.generateWithConfigAndCallback(
                        clean, GenerationConfig(sid = v.sid, speed = speed, extra = mapOf("lang" to lang)), write
                    )
                } else {
                    e.generateWithCallback(clean, 0, speed, write)
                }
                val until = System.currentTimeMillis() + total * 1000L / sr + 1500
                while (my == token && at.playbackHeadPosition < total && System.currentTimeMillis() < until) Thread.sleep(40)
                ok = total > 0
                if (ok) lastError = null else if (my == token) lastError = "The voice made no sound for this text"
            } catch (e: Throwable) {
                ok = false
                lastError = "${e.javaClass.simpleName}: ${e.message ?: ""}"
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

    fun delete(ctx: Context, pack: String) {
        stop()
        synchronized(lock) {
            if (enginePack == pack) {
                try { engine?.release() } catch (e: Throwable) { }
                engine = null
                enginePack = null
            }
        }
        dir(ctx, pack).deleteRecursively()
    }

    /** Voice pack download + unzip. progress(0..100) main thread par. done(true/false, galti) */
    fun download(ctx: Context, pack: String, progress: (Int) -> Unit, done: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val tmp = File(app.filesDir, "voicepack-new-$pack")
            var err = ""
            try {
                tmp.deleteRecursively(); tmp.mkdirs()
                val c = URL(packUrl(pack)).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "Lilly-app")
                if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
                val size = c.contentLengthLong.takeIf { it > 0 } ?: (packMb(pack) * 1024L * 1024L)
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
                val inner = tmp.listFiles()?.singleOrNull { it.isDirectory && it.name != "espeak-ng-data" }
                val src = if (inner != null && tmp.listFiles()?.none { it.name.endsWith(".onnx") } == true) inner else tmp
                stop()
                synchronized(lock) {
                    if (enginePack == pack) {
                        try { engine?.release() } catch (e: Throwable) { }
                        engine = null
                        enginePack = null
                    }
                }
                val dst = dir(app, pack)
                dst.deleteRecursively()
                if (!src.renameTo(dst)) throw IllegalStateException("couldn't save the voice")
                tmp.deleteRecursively()
                if (!packInstalled(app, pack)) throw IllegalStateException("voice files missing")
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
                tmp.deleteRecursively()
            }
            val ok = err.isEmpty()
            if (ok) engineFor(app, pack)
            main.post { done(ok, err) }
        }.start()
    }
}
