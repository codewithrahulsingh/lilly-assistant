package com.rahul.lilly

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Rahul ki awaaz ki "pehchaan" (voice print): sikhate waqt ke 5 namoone + roz ke istemaal se seekhe hue.
 * Naya "Lilly" aaye toh in sab se milaan karke score (0..1) nikaalo. Sab phone me — kahin bheja nahi jaata.
 */
class VoicePrint(
    val enrolled: MutableList<FloatArray> = ArrayList(),
    val learned: MutableList<FloatArray> = ArrayList()
) {
    companion object {
        const val MAX_LEARNED = 12

        fun threshold(level: String) = when (level) {
            "strict" -> 0.56f
            "relaxed" -> 0.38f
            else -> 0.46f
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dot = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            if (na <= 0 || nb <= 0) return 0f
            return (dot / (sqrt(na) * sqrt(nb))).toFloat()
        }

        fun mean(list: List<FloatArray>): FloatArray? {
            if (list.isEmpty()) return null
            val out = FloatArray(list[0].size)
            for (e in list) for (i in out.indices) out[i] += e[i] / list.size
            return out
        }

        fun fromJson(s: String?): VoicePrint {
            val p = VoicePrint()
            if (s.isNullOrBlank()) return p
            try {
                val o = JSONObject(s)
                fun read(k: String, into: MutableList<FloatArray>) {
                    val a = o.optJSONArray(k) ?: return
                    for (i in 0 until a.length()) {
                        val v = a.getJSONArray(i)
                        into.add(FloatArray(v.length()) { v.getDouble(it).toFloat() })
                    }
                }
                read("enrolled", p.enrolled)
                read("learned", p.learned)
            } catch (e: Exception) { }
            return p
        }
    }

    val ready: Boolean get() = enrolled.size >= 3

    /** Score: sikhaye namoono ke "ausat" se milaan + sabse milte namoone ka aadha-aadha */
    fun score(e: FloatArray): Float {
        val m = mean(enrolled) ?: return 0f
        val best = (enrolled + learned).maxOf { cosine(e, it) }
        return 0.5f * cosine(e, m) + 0.5f * best
    }

    /** Pakka Rahul tha (score bahut accha) — namoona yaad rakho, dheere-dheere aur behtar */
    fun learn(e: FloatArray, score: Float, level: String): Boolean {
        if (score < threshold(level) + 0.12f) return false
        learned.add(e)
        while (learned.size > MAX_LEARNED) learned.removeAt(0)
        return true
    }

    fun toJson(): String {
        fun arr(l: List<FloatArray>) = JSONArray().also { a -> l.forEach { e -> a.put(JSONArray().also { v -> e.forEach { v.put(it.toDouble()) } }) } }
        return JSONObject().put("enrolled", arr(enrolled)).put("learned", arr(learned)).toString()
    }
}

/** Jaagne ka faisla: ek baar thoda kam aaye aur turant dobara "Lilly" bolo toh thodi chhoot */
class WakeGate(private val now: () -> Long = { System.currentTimeMillis() }) {
    private var nearMissAt = 0L

    fun decide(score: Float, level: String): Boolean {
        val th = VoicePrint.threshold(level)
        if (score >= th) { nearMissAt = 0; return true }
        val t = now()
        if (nearMissAt > 0 && t - nearMissAt < 6000 && score >= th - 0.06f) { nearMissAt = 0; return true }
        if (score >= th - 0.10f) nearMissAt = t
        return false
    }
}
