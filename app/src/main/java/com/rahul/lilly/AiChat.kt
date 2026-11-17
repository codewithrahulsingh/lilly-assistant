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
        "You are Lilly, Rahul's personal voice assistant, with a warm, friendly female voice. " +
            "Your reply is read aloud, so answer directly in one to three short sentences. " +
            "Never use markdown, lists, emojis or links. " +
            "Reply in the same language as the user's latest message, and use EXACTLY this format: " +
            "two parts separated by |||. " +
            "If the user wrote in English: the reply in plain English, then |||, then the same English reply again. " +
            "If the user wrote in Hindi or Hinglish (Hindi in Roman letters): the reply in simple Hindi in Devanagari " +
            "script (for speaking), then |||, then the same reply in Roman Hinglish (for the screen). " +
            "Example 1: I'm doing well, how about you?|||I'm doing well, how about you? " +
            "Example 2: मैं ठीक हूँ, आप बताओ?|||Main theek hoon, aap batao?"

    /** code: HTTP status (0 = internet/connection), detail: server ka apna message */
    class AiError(val code: Int, val detail: String) : Exception("HTTP $code $detail")

    /** Galti ko aisi line me badlo jo Lilly bol sake: (screen par, bolne ke liye) */
    fun explain(e: Throwable?): Pair<String, String> {
        val err = e as? AiError
        val d = err?.detail ?: ""
        return when {
            e is java.net.UnknownHostException || e is java.net.ConnectException ->
                Pair("No internet connection", "इंटरनेट नहीं चल रहा")
            e is java.net.SocketTimeoutException ->
                Pair("The AI took too long to answer, please try again", "ए आई ने जवाब देने में बहुत देर कर दी, दोबारा बोलिए")
            err == null -> Pair("I couldn't get an answer from the AI (${e?.javaClass?.simpleName})", "ए आई से जवाब नहीं मिला")
            err.code == 429 ->
                Pair("The free AI limit is used up for now, please try again in a little while", "ए आई की फ्री लिमिट अभी खत्म है, थोड़ी देर बाद बोलिए")
            err.code == 401 || err.code == 403 || d.contains("API key", true) ->
                Pair("The AI key is wrong or disabled (${err.code}). Please paste the key again in the app", "ए आई की गलत है। ऐप में की दोबारा पेस्ट कीजिए")
            err.code == 404 -> Pair("No AI model is available for this key (404)", "इस की पर कोई ए आई मॉडल नहीं मिला")
            err.code >= 500 -> Pair("The AI server is busy right now (${err.code}), please try again", "ए आई सर्वर अभी बिज़ी है, दोबारा बोलिए")
            else -> Pair("AI error ${err.code}: ${d.take(120)}", "ए आई से जवाब नहीं मिला")
        }
    }

    fun ask(keyRaw: String, history: List<Turn>, question: String): Answer {
        // Copy-paste me aayi space / nayi line hata do (warna request hi nahi jaati)
        val key = keyRaw.filter { !it.isWhitespace() }
        val raw = if (key.startsWith("sk-ant-")) anthropic(key, history, question) else gemini(key, history, question)
        val parts = raw.trim().split("|||")
        val spoken = parts[0].trim()
        val display = (parts.getOrNull(1)?.trim()).takeUnless { it.isNullOrBlank() } ?: Parser.roman(spoken)
        if (spoken.isBlank()) throw IllegalStateException("khali jawab")
        return Answer(spoken, display)
    }

    /** body == null toh GET */
    private fun post(url: String, headers: Map<String, String>, body: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 25000
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
                throw AiError(code, msg ?: "")
            }
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
        val res = geminiCall(key, body.toString())
        val parts = JSONObject(res).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
        }
        return sb.toString()
    }

    private const val GEMINI = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Jo model pichhli baar chala tha, wahi pehle try hota hai */
    @Volatile private var goodModel: String? = null

    /**
     * Gemini ke model naam badalte rehte hain aur free tier me har model nahi milta.
     * Isliye ek ke baad ek try karo; koi na chale toh key par jo models hain unki list se chuno.
     */
    private fun geminiCall(key: String, body: String): String {
        val headers = mapOf("x-goog-api-key" to key)
        val tried = LinkedHashSet<String>()
        var last: AiError? = null
        fun attempt(model: String): String? {
            if (!tried.add(model)) return null
            return try {
                val r = post("$GEMINI/$model:generateContent", headers, body)
                goodModel = model
                r
            } catch (e: AiError) {
                // Key hi galat ho toh doosre model try karne ka fayda nahi
                if (e.code == 401 || e.code == 403 || e.detail.contains("API key", true)) throw e
                last = e
                null
            }
        }
        val first = listOfNotNull(
            goodModel, "gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest", "gemini-2.5-flash-lite"
        )
        for (m in first) attempt(m)?.let { return it }
        for (m in listModels(headers).take(4)) attempt(m)?.let { return it }
        throw last ?: AiError(404, "")
    }

    /** Is key par chalne wale "flash" text models, naye pehle */
    private fun listModels(headers: Map<String, String>): List<String> {
        val res = try { post("$GEMINI?pageSize=200", headers, null) } catch (e: Exception) { return emptyList() }
        val arr = JSONObject(res).optJSONArray("models") ?: return emptyList()
        val skip = listOf("live", "image", "tts", "audio", "embedding", "robotics", "native", "computer")
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            val name = m.optString("name").removePrefix("models/")
            if ("generateContent" in methods && "flash" in name && skip.none { it in name }) out.add(name)
        }
        fun rank(n: String) = (if ("preview" in n || "exp" in n) 2 else 0) + (if ("lite" in n) 1 else 0)
        return out.sortedWith(compareBy<String> { rank(it) }.thenByDescending { it })
    }
}
