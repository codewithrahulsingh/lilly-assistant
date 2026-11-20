package com.rahul.lilly

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Lilly ki "aadat" yaad: aap roz lagbhag ek hi time par jo karte ho (gym playlist, mummy ko call…),
 * 3+ alag dino me hua toh agli baar khud poochhti hai "chalaun?". Sab phone me hi.
 */
object Habits {
    data class Use(val text: String, val hour: Int, val at: Long)

    fun load(json: String?): MutableList<Use> {
        val out = ArrayList<Use>()
        if (json.isNullOrBlank()) return out
        try {
            val a = JSONArray(json)
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); out.add(Use(o.getString("t"), o.getInt("h"), o.getLong("a"))) }
        } catch (e: Exception) { }
        return out
    }

    fun save(list: List<Use>): String = JSONArray().also { a ->
        list.takeLast(300).forEach { a.put(JSONObject().put("t", it.text).put("h", it.hour).put("a", it.at)) }
    }.toString()

    /** Aaj is waqt ki aadat (agar ho): wahi text jo aap bolte ho */
    fun suggest(list: List<Use>, now: Long, lastSuggestAt: Long): String? {
        if (now - lastSuggestAt < 6 * 3600_000L) return null
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val h = c.get(Calendar.HOUR_OF_DAY)
        val recent = list.filter { now - it.at < 21 * 24 * 3600_000L && Math.abs(it.hour - h) <= 1 }
        val byText = recent.groupBy { it.text }
        val best = byText.entries.map { (t, uses) ->
            val days = uses.map { it.at / (24 * 3600_000L) }.toSet().size
            val doneJustNow = list.any { it.text == t && now - it.at < 2 * 3600_000L }
            Triple(t, days, doneJustNow)
        }.filter { it.second >= 3 && !it.third }.maxByOrNull { it.second }
        return best?.first
    }
}
