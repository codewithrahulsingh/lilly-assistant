package com.rahul.lilly

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Lilly list padh rahi ho aur aap beech me "teen" / "third" bol do — turant pakdo (Siri jaisa beech me bolna).
 * Phone ke andar (Vosk), sirf ginti wale shabd; echo-cancel wala mic taaki Lilly ki apni awaaz na pakde.
 */
class BargeIn(private val model: Model, private val onNumber: (Int) -> Unit) {

    companion object {
        private const val RATE = 16000
        /** Shabd -> ginti (Hindi ginti English model me aise sunai deti hai: ek≈egg, paanch≈punch) */
        val WORDS = mapOf(
            "one" to 1, "first" to 1, "egg" to 1,
            "two" to 2, "second" to 2, "do" to 2,
            "three" to 3, "third" to 3, "teen" to 3,
            "four" to 4, "fourth" to 4, "char" to 4,
            "five" to 5, "fifth" to 5, "punch" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
        )
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val grammar = (WORDS.keys + listOf("number", "[unk]")).joinToString(",", "[", "]") { "\"$it\"" }
            val rec = try { Recognizer(model, RATE.toFloat(), grammar).also { try { it.setWords(true) } catch (e: Exception) { } } } catch (e: Exception) { null }
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            // VOICE_COMMUNICATION: phone ka echo-cancel chalta hai (speaker ki awaaz mic se hatati hai)
            val ar = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE))
            } catch (e: Exception) { null }
            if (rec == null || ar == null || ar.state != AudioRecord.STATE_INITIALIZED) {
                ar?.release(); try { rec?.close() } catch (e: Exception) { }; running = false; return@Thread
            }
            val aec = try { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(ar.audioSessionId)?.apply { enabled = true } else null } catch (e: Exception) { null }
            val ns = try { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(ar.audioSessionId)?.apply { enabled = true } else null } catch (e: Exception) { null }
            val buf = ShortArray(RATE / 10)
            try {
                ar.startRecording()
                while (running) {
                    val n = ar.read(buf, 0, buf.size)
                    if (n <= 0) break
                    if (rec.acceptWaveForm(buf, n)) {
                        val hit = best(rec.result)
                        if (hit != null && running) { running = false; main.post { onNumber(hit) }; break }
                    }
                }
            } catch (e: Exception) {
            } finally {
                try { ar.stop() } catch (e: Exception) { }
                ar.release()
                try { aec?.release() } catch (e: Exception) { }
                try { ns?.release() } catch (e: Exception) { }
                try { rec.close() } catch (e: Exception) { }
            }
        }.also { it.start() }
    }

    /** Pakka (bharosa >= 0.9) aur akela ginti shabd ho tabhi — Lilly ki apni awaaz ke tukde na pakde */
    private fun best(json: String?): Int? {
        return try {
            val arr = JSONObject(json ?: "{}").optJSONArray("result") ?: return null
            val words = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("word") != "[unk]" }
            val nums = words.filter { WORDS.containsKey(it.optString("word")) }
            if (nums.size != 1 || words.size > 2) null
            else nums[0].takeIf { it.optDouble("conf", 0.0) >= 0.9 }?.let { WORDS[it.optString("word")] }
        } catch (e: Exception) { null }
    }

    fun stop() {
        running = false
        thread = null
    }
}
