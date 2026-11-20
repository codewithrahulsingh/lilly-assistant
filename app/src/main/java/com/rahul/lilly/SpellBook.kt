package com.rahul.lilly

import org.json.JSONObject

/**
 * Lilly ki "apni training": Google galat likhe ("misi call") aur AI samjha de ki matlab "missed call" tha —
 * toh Lilly ye jodi yaad rakhti hai. Agli baar bina AI/internet ke khud sahi kar leti hai.
 * Rahul app me khud bhi jod/hata sakte hain.
 */
object SpellBook {

    /** Aise aam shabd kabhi "galat" maan ke mat badlo */
    private val never = setOf(
        "kal", "main", "mai", "me", "hai", "ha", "na", "to", "ki", "ka", "ke", "ko", "se", "aur", "ya", "the", "a", "an", "is",
        "it", "in", "on", "of", "and", "or", "lilly", "lily", "haan", "nahi", "ok", "karo", "kar", "do", "de"
    )

    fun load(json: String?): MutableMap<String, String> {
        val out = LinkedHashMap<String, String>()
        if (json.isNullOrBlank()) return out
        try {
            val o = JSONObject(json)
            for (k in o.keys()) out[k] = o.getString(k)
        } catch (e: Exception) { }
        return out
    }

    fun save(map: Map<String, String>): String = JSONObject(map as Map<*, *>).toString()

    /** AI ki batayi jodi theek hai? (sach me bole gaye shabd, chhote aam shabd nahi, naam jaise nahi) */
    fun acceptable(wrong: String, right: String, said: String): Boolean {
        val w = wrong.trim().lowercase()
        val r = right.trim().lowercase()
        if (w.length < 3 || r.isBlank() || w == r || w in never) return false
        if (w.split(" ").size > 3 || r.split(" ").size > 4) return false
        if (!Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(w) + """(?![\p{L}\p{N}])""").containsMatchIn(said.lowercase())) return false
        if (w.any { it.isDigit() } || r.any { it.isDigit() }) return false
        return true
    }
}
