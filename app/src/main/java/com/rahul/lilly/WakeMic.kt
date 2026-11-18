package com.rahul.lilly

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener

/**
 * "Lilly" sunne wala mic (Vosk ki SpeechService jaisa), par aakhri kuch second ki awaaz bhi rakhta hai —
 * taaki jaagne se pehle check ho sake ki "Lilly" Rahul ki hi awaaz me bola gaya (Voice match).
 */
class WakeMic(
    private val rec: Recognizer,
    private val listener: RecognitionListener,
    private val main: Handler
) {
    companion object {
        const val RATE = 16000
        private const val RING = RATE * 4 // aakhri 4 second
    }

    @Volatile private var running = false
    private var thread: Thread? = null
    private val ring = FloatArray(RING)
    private var fed = 0L // shuru se kitne samples Vosk ko diye
    private val lock = Any()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val ar = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE / 2 * 2))
        } catch (e: Exception) { return false }
        if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return false }
        running = true
        thread = Thread {
            val buf = ShortArray(RATE / 10) // 0.1 second
            try {
                ar.startRecording()
                while (running) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n < 0) { main.post { listener.onError(Exception("mic read $n")) }; break }
                    if (n == 0) continue
                    synchronized(lock) {
                        for (i in 0 until n) ring[((fed + i) % RING).toInt()] = buf[i] / 32768f
                        fed += n
                    }
                    if (!running) break
                    val final = rec.acceptWaveForm(buf, n)
                    val json = if (final) rec.result else rec.partialResult
                    if (!running) break
                    main.post { if (running) { if (final) listener.onResult(json) else listener.onPartialResult(json) } }
                }
            } catch (e: Exception) {
                if (running) main.post { listener.onError(e) }
            } finally {
                try { ar.stop() } catch (e: Exception) { }
                ar.release()
            }
        }.also { it.start() }
        return true
    }

    fun stop() {
        running = false
        try { thread?.join(600) } catch (e: Exception) { }
        thread = null
    }

    /**
     * Jo vakya abhi suna (Vosk JSON me har shabd ka start/end second) uski awaaz.
     * Samay na mile toh aakhri 1.6 second.
     */
    fun clipFor(json: String?): FloatArray {
        var startS = -1.0
        var endS = -1.0
        try {
            val arr = json?.let { JSONObject(it).optJSONArray("result") }
            if (arr != null && arr.length() > 0) {
                startS = arr.getJSONObject(0).optDouble("start", -1.0)
                endS = arr.getJSONObject(arr.length() - 1).optDouble("end", -1.0)
            }
        } catch (e: Exception) { }
        synchronized(lock) {
            val oldest = maxOf(0L, fed - RING)
            var from: Long
            var to: Long
            val tEnd = (endS * RATE).toLong() + RATE / 4
            // Vosk ka samay hamare ring me baith-ta ho tabhi maano
            if (startS >= 0 && endS > startS && tEnd > oldest + RATE / 2 && tEnd <= fed + RATE) {
                from = (startS * RATE).toLong() - RATE / 5
                to = minOf(tEnd, fed)
            } else {
                to = fed
                from = fed - (RATE * 1.6).toLong()
            }
            // Kam se kam 1.2 second (chhoti awaaz se pehchaan kamzor)
            if (to - from < RATE * 1.2) from = to - (RATE * 1.2).toLong()
            from = from.coerceIn(oldest, fed)
            to = to.coerceIn(from, fed)
            val out = FloatArray((to - from).toInt())
            for (i in out.indices) out[i] = ring[((from + i) % RING).toInt()]
            return out
        }
    }
}
