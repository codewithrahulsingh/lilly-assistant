package com.rahul.lilly

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Normal baat-cheet ke jawab. Sirf tab chalta hai jab user ne app me apni AI key daali ho.
 * Key "sk-ant-" se shuru ho toh Anthropic (Claude), warna Google Gemini.
 */
object AiChat {

    /** role = "user" ya "assistant" */
    data class Turn(val role: String, val text: String)

    data class Answer(val spoken: String, val display: String)

    private const val SYSTEM =
        "Tum Lilly ho — Rahul ki personal voice assistant, ek dostana ladki ki awaaz. " +
            "Tumhara jawab zor se bol kar sunaya jaayega, isliye 1 se 3 chhote vakya me, seedha aur garmjoshi se jawab do. " +
            "Koi markdown, list, emoji ya link mat likho. " +
            "Jawab THEEK is format me do, do hisse ||| se alag: pehle saral Hindi Devanagari lipi me (bolne ke liye), " +
            "phir wahi baat Roman Hinglish me (screen par dikhane ke liye). " +
            "Udaharan: मैं ठीक हूँ, आप बताओ?|||Main theek hoon, aap batao?"

    fun ask(key: String, history: List<Turn>, question: String): Answer {
        val raw = if (key.startsWith("sk-ant-")) anthropic(key, history, question) else gemini(key, history, question)
        val parts = raw.trim().split("|||")
        val spoken = parts[0].trim()
        val display = (parts.getOrNull(1)?.trim()).takeUnless { it.isNullOrBlank() } ?: Parser.roman(spoken)
        if (spoken.isBlank()) throw IllegalStateException("khali jawab")
        return Answer(spoken, display)
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 25000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    private fun anthropic(key: String, history: List<Turn>, question: String): String {
        val messages = JSONArray()
        for (t in history) messages.put(JSONObject().put("role", t.role).put("content", t.text))
        messages.put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject()
            .put("model", "claude-haiku-5-5")
            .put("max_tokens", 400)
            .put("system", SYSTEM)
            .put("messages", messages)
        val res = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
            body.toString()
        )
        val content = JSONObject(res).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        return sb.toString()
    }

    private fun gemini(key: String, history: List<Turn>, question: String): String {
        fun part(role: String, text: String) = JSONObject()
            .put("role", role)
            .put("parts", JSONArray().put(JSONObject().put("text", text)))
        val contents = JSONArray()
        for (t in history) contents.put(part(if (t.role == "assistant") "model" else "user", t.text))
        contents.put(part("user", question))
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1500))
        val res = post(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            mapOf("x-goog-api-key" to key),
            body.toString()
        )
        val parts = JSONObject(res).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
    }
}
