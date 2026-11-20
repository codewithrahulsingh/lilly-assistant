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

    /** command != null: AI ne baat ko phone ki command samjha (jaise "call rohit") */
    /** memory != null: user ne apne baare me koi pakki baat batayi, use yaad rakhna hai */
    data class Answer(
        val spoken: String, val display: String, val command: String? = null, val memory: String? = null,
        /** Speech recognition ki galat spelling -> sahi (Lilly seekh ke aage khud sudharti hai) */
        val fixes: Map<String, String> = emptyMap()
    )

    private const val FIX_RULE =
        " The user's message comes from phone speech recognition, which often mis-spells Hinglish/English words " +
            "(e.g. 'misi call' = 'missed call', 'chek' = 'check', 'vi fi' = 'WiFi', 'notifikeshan' = 'notifications'). " +
            "Understand what they meant. If you see such clear speech-recognition mistakes, add one extra final line exactly like: " +
            "FIX: wrong words=right words; other wrong=other right (lowercase, max 5, only obvious mis-hearings, never names or slang)."

    private const val MEMORY_RULE =
        " If the user tells you a lasting personal fact about themselves or people in their life " +
            "(a birthday, a preference, a routine, a relationship, a name, where they live or work), " +
            "add at the very end: |||MEM: <that fact as one short English sentence about Rahul>. " +
            "Only for facts worth remembering for months, never for passing moods or questions."

    /** Pehle se yaad baatein system prompt me */
    private fun systemFor(facts: String): String {
        val known = if (facts.isBlank()) "" else
            "\n\nThings you remember about Rahul (use them naturally when relevant; " +
                "don't list them unless asked):\n$facts"
        val base = if (male) SYSTEM.replace(FEMALE_LINE, MALE_LINE).replace("with a warm, friendly female voice", "with a warm, friendly male voice") else SYSTEM
        val rough = if (Abuse.roughAnswer) "\n\nRahul has turned on roast mode and is talking rough: answer the question correctly, " +
            "but in a cheeky, rough Mumbai-tapori Hinglish style, playfully roasting him (slang like abe, saale, bhai is fine). Keep it short." else ""
        return base + MEMORY_RULE + FIX_RULE + known + rough
    }

    /** Rahul ne ladke ki awaaz chuni ho toh AI bhi ladke ki tarah bole */
    @Volatile var male = false
    private const val FEMALE_LINE =
        "You are a woman: in Hindi always use feminine forms (sakti hoon, karti hoon, rahi hoon, gayi), never masculine. "
    private const val MALE_LINE =
        "You are a man: in Hindi always use masculine forms (sakta hoon, karta hoon, raha hoon, gaya, karunga), never feminine. "

    private const val SYSTEM =
        "You are Lilly, Rahul's personal voice assistant, with a warm, friendly female voice. " +
            "You are a woman: in Hindi always use feminine forms (sakti hoon, karti hoon, rahi hoon, gayi), never masculine. " +
            "You live inside Rahul's phone and CAN do phone actions through CMD (below) — never say you can't access the phone or WhatsApp. " +
            "Never mention APIs, keys, models, servers or that you are an AI program. " +
            "Your reply is read aloud, so answer directly in one to three short sentences. " +
            "Never use markdown, lists, emojis or links. " +
            "Reply in the same language as the user's latest message, and use EXACTLY this format: " +
            "two parts separated by |||. " +
            "If the user wrote in English: the reply in plain English, then |||, then the same English reply again. " +
            "If the user wrote in Hindi or Hinglish (Hindi in Roman letters): the reply in simple Hindi in Devanagari " +
            "script (for speaking), then |||, then the same reply in Roman Hinglish (for the screen). " +
            "Example 1: I'm doing well, how about you?|||I'm doing well, how about you? " +
            "Example 2: मैं ठीक हूँ, आप बताओ?|||Main theek hoon, aap batao? " +
            "IMPORTANT: if the message asks the phone to DO something, do not use that format. Instead reply with " +
            "only one line: CMD: <command>, written in simple English using one of these forms: " +
            "call <contact name> | call <contact name> on speaker | open <app name> | play <song or artist> | " +
            "play <song or artist> on spotify | play <video> on youtube | pause | next song | previous song | " +
            "volume up | volume down | mute | torch on | torch off | what time is it | what is the date | battery | " +
            "send a whatsapp to <contact name> saying <message> | whatsapp call <contact name> | whatsapp video call <contact name> | " +
            "navigate to <place> | nearest <kind of place> | what's the weather | will it rain tomorrow | " +
            "set an alarm for <time> | set a timer for <minutes> minutes | remind me at <time> to <task> | " +
            "read my notifications | do not disturb on | do not disturb off | brightness up | brightness down | " +
            "phone silent | vibrate mode | open wifi settings | open bluetooth settings | good morning. " +
            "For WhatsApp messages keep the message in the user's own words and language (Roman letters). " +
            "Keep contact, app and song names exactly as the user said them, in Roman letters. " +
            "Examples: 'boss ko ghanti maaro' -> CMD: call boss. 'kuch romantic sunao spotify pe' -> CMD: play romantic songs on spotify. " +
            "'andhera hai roshni karo' -> CMD: torch on. " +
            "'whatsapp par nandu ko bol do paise dal de' -> CMD: send a whatsapp to nandu saying paise dal de. " +
            "'kal subah jaldi uthana hai 6 baje' -> CMD: set an alarm for 6 am. Use CMD only for these phone actions."

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
            err?.code == 0 -> Pair("No AI key is set. Add one in AI chat in the Lilly app", "ए आई की नहीं डाली है, लिली ऐप में डालिए")
            err?.code == 402 -> Pair("The second AI's balance or free limit is used up (402)", "दूसरी ए आई की लिमिट खत्म हो गई है")
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

    /** Doosri AI service jo OpenAI jaisa format samjhe (jaise freemodel.in) */
    data class Custom(val url: String, val key: String, val model: String = "", val shareMemory: Boolean = false)

    const val CUSTOM_DEFAULT_URL = "https://freemodel.in/v1"

    fun customFrom(p: android.content.SharedPreferences): Custom? {
        val key = (p.getString("ai2_key", "") ?: "").filter { !it.isWhitespace() }
        if (key.isBlank()) return null
        return Custom(
            (p.getString("ai2_url", CUSTOM_DEFAULT_URL) ?: CUSTOM_DEFAULT_URL).trim().ifBlank { CUSTOM_DEFAULT_URL },
            key, (p.getString("ai2_model", "") ?: "").trim(), p.getBoolean("ai2_memory", false)
        )
    }

    /**
     * Pehli AI (Gemini/Claude) aur doosri (custom) — jo pehle set ho (ai_order) wo pehle;
     * wo na chale (limit, galat key, internet, server) toh doosri.
     */
    fun askWithFallback(
        p: android.content.SharedPreferences, history: List<Turn>, question: String, facts: String, english: Boolean?
    ): Answer {
        val main = (p.getString("ai_key", "") ?: "").filter { !it.isWhitespace() }
        val custom = customFrom(p)
        val tries = ArrayList<() -> Answer>()
        if (main.isNotBlank()) tries.add { ask(main, history, question, facts, english) }
        if (custom != null) tries.add {
            // Aapki yaaddasht doosri service ko tabhi jaati hai jab aap allow karo
            ask("", history, question, if (custom.shareMemory) facts else "", english, custom)
        }
        if (p.getString("ai_order", "main") == "custom") tries.reverse()
        if (tries.isEmpty()) throw AiError(0, "no key")
        var last: Exception? = null
        for (t in tries) {
            try { return t() } catch (e: Exception) { last = e }
        }
        throw last ?: AiError(0, "no key")
    }

    /** english: user ne abhi English me bola (true) / Hindi-Hinglish me (false) / pata nahi (null) */
    fun ask(
        keyRaw: String, history: List<Turn>, question: String, facts: String = "", english: Boolean? = null,
        custom: Custom? = null
    ): Answer {
        // Copy-paste me aayi space / nayi line hata do (warna request hi nahi jaati)
        val key = keyRaw.filter { !it.isWhitespace() }
        val lang = when (english) {
            true -> "\n\nRight now Rahul is speaking ENGLISH: reply in English (both parts in English), even if earlier messages were Hindi."
            false -> "\n\nRight now Rahul is speaking Hindi/Hinglish: reply in Hindi (Devanagari part, then Roman Hinglish part)."
            null -> ""
        }
        val system = systemFor(facts) + lang
        var raw = when {
            custom != null -> openAi(custom, history, question, system)
            key.startsWith("sk-ant-") -> anthropic(key, history, question, system)
            else -> gemini(key, history, question, system)
        }
        // "FIX: misi call=missed call; chek=check" — galat sune shabd, seekhne ke liye
        val fixes = LinkedHashMap<String, String>()
        Regex("""(?:\|\|\|)?\s*FIX\s*:\s*(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(raw)?.let { m ->
            for (pair in m.groupValues[1].split(';')) {
                val kv = pair.split('=')
                if (kv.size != 2) continue
                val w = kv[0].trim().trim('"', '\'', '`').lowercase()
                val rgt = kv[1].trim().trim('"', '\'', '`', '.').lowercase()
                if (w.isNotBlank() && rgt.isNotBlank() && w != rgt) fixes[w] = rgt
            }
            raw = raw.substring(0, m.range.first) + raw.substring(m.range.last + 1)
        }
        // "|||MEM: Rahul's gym is at 6 pm" — yaad rakhne wali baat alag nikaalo
        var memory: String? = null
        Regex("""(?:\|\|\|)?\s*MEM\s*:\s*(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(raw)?.let { m ->
            memory = m.groupValues[1].trim().trim('"', '.', '`').ifBlank { null }
            raw = raw.substring(0, m.range.first)
        }
        // "CMD: call rohit" — phone ka kaam
        Regex("""(?:^|\|\|\||\n)\s*\**CMD\**\s*:\s*(.+)""", RegexOption.IGNORE_CASE).find(raw.trim())?.let { m ->
            val cmd = m.groupValues[1].lines().first().trim().trim('"', '\'', '.', '`').trim()
            if (cmd.isNotBlank()) return Answer("", "", command = cmd, fixes = fixes)
        }
        val parts = raw.trim().split("|||")
        val spoken = parts[0].trim()
        val display = (parts.getOrNull(1)?.trim()).takeUnless { it.isNullOrBlank() } ?: Parser.roman(spoken)
        if (spoken.isBlank()) throw IllegalStateException("khali jawab")
        return Answer(spoken, display, memory = memory, fixes = fixes)
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

    /** Custom ne kaunsa model chuna (jab aapne naam na diya ho) */
    @Volatile var customModelUsed: String = ""
    @Volatile private var pickedFor: Pair<String, String>? = null

    /** OpenAI jaisa format: POST {url}/chat/completions, "Authorization: Bearer key" */
    private fun openAi(c: Custom, history: List<Turn>, question: String, system: String): String {
        val base = c.url.trimEnd('/')
        val headers = mapOf("Authorization" to "Bearer ${c.key}")
        val model = c.model.ifBlank { pickModel(base, headers) }
        customModelUsed = model
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (t in history) messages.put(JSONObject().put("role", if (t.role == "assistant") "assistant" else "user").put("content", t.text))
        messages.put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject().put("model", model).put("messages", messages)
            .put("max_tokens", 1000).put("temperature", 0.7)
        val res = post("$base/chat/completions", headers, body.toString())
        val content = JSONObject(res).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
        // Kuch models apni "soch" <think>…</think> me likhte hain — wo bolna nahi
        return content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    }

    /** Model naam na diya ho toh service ki list me se tez wala chuno */
    private fun pickModel(base: String, headers: Map<String, String>): String {
        pickedFor?.let { if (it.first == base) return it.second }
        val res = post("$base/models", headers, null)
        val ids = ArrayList<String>()
        val arr = JSONObject(res).optJSONArray("data") ?: JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
        val skip = listOf("embed", "image", "tts", "whisper", "audio", "vision-only", "moderation", "rerank", "dall")
        val usable = ids.filter { id -> skip.none { id.lowercase().contains(it) } }
        val fast = listOf("flash", "mini", "turbo", "lite", "luna", "chat", "small")
        val pick = usable.firstOrNull { id -> fast.any { id.lowercase().contains(it) } } ?: usable.firstOrNull()
            ?: throw AiError(404, "no model in the list")
        pickedFor = Pair(base, pick)
        return pick
    }

    private fun anthropic(key: String, history: List<Turn>, question: String, system: String): String {
        val messages = JSONArray()
        for (t in history) messages.put(JSONObject().put("role", t.role).put("content", t.text))
        messages.put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject()
            .put("model", "claude-haiku-5-5")
            .put("max_tokens", 400)
            .put("system", system)
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

    private fun gemini(key: String, history: List<Turn>, question: String, system: String): String {
        fun part(role: String, text: String) = JSONObject()
            .put("role", role)
            .put("parts", JSONArray().put(JSONObject().put("text", text)))
        val contents = JSONArray()
        for (t in history) contents.put(part(if (t.role == "assistant") "model" else "user", t.text))
        contents.put(part("user", question))
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
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
