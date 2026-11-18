package com.rahul.lilly

/**
 * Siri jaisi samajh: shabdon ka kram kuch bhi ho, app (Spotify / YouTube / WhatsApp) + kaam
 * (chalao, call, message) + naam/gaana pakad lo. Parser ke niyam na chalein ya galat pakdein toh ye sudharta hai.
 * Sab phone ke andar — bina internet, bina AI.
 */
object Smart {

    private val fillers = setOf(
        "please", "plz", "pls", "zara", "jara", "mujhe", "muje", "mere", "mera", "meri", "liye", "ke", "ka", "ki", "ko",
        "par", "pe", "per", "pr", "mein", "me", "on", "in", "of", "the", "a", "an", "to", "from", "for", "with", "se",
        "karo", "kar", "kr", "do", "de", "dena", "dijiye", "lagao", "laga", "lagado", "chalao", "chala", "chalado",
        "bajao", "baja", "bajado", "sunao", "suna", "sunado", "play", "open", "kholo", "khol", "start", "karke", "aur",
        "and", "then", "phir", "fir", "app", "ek", "koi", "kuch", "accha", "achha", "acha", "sa", "si", "some", "song",
        "songs", "gaana", "gana", "gaane", "gane", "gaanaa", "gaanaa", "music", "track", "tracks", "video", "videos",
        "sunna", "sunne", "sun", "chahta", "chahti", "hoon", "hun", "hu", "hai", "hain", "wala", "wale", "waala",
        "waale", "spotify", "youtube", "yt", "latest", "new", "naya", "naye", "nayi", "playlist", "search", "dhundo",
        "dikhao", "dekhna", "dekhni", "dekhao", "lgao", "chalu", "now", "can", "you", "i", "want", "listen", "watch"
    )

    private val waWord = Regex("""\b(whatsapp|whatsap|watsapp|watsap|whats app|wa)\b""")
    private val musicWord = Regex("""\b(spotify|youtube|gaana|gana|gaane|gane|song|songs|music|bajao|bajado|sunao|playlist)\b""")
    private val appWord = Regex("""\b(spotify|youtube|yt)\b""")

    /** Shuru aur aakhir ke bhar-ti shabd hatao, beech ke gaane/naam ke shabd chhodo */
    fun edgeStrip(s: String): String {
        val w = s.split(" ").filter { it.isNotBlank() }.toMutableList()
        while (w.isNotEmpty() && w.first() in fillers) w.removeAt(0)
        while (w.isNotEmpty() && w.last() in fillers) w.removeAt(w.size - 1)
        return w.joinToString(" ")
    }

    fun refine(t: String, c: Cmd): Cmd {
        val loose = c is Cmd.Unknown || c is Cmd.Call || c is Cmd.Chat || c is Cmd.PlayMusic || c is Cmd.OpenApp
        if (waWord.containsMatchIn(t) && loose) whatsapp(t, c)?.let { return it }
        if (musicWord.containsMatchIn(t)) music(t, c)?.let { return it }
        return c
    }

    // ---------------- Gaane ----------------

    private fun music(t: String, c: Cmd): Cmd? {
        // Gaana roko / agla / awaaz — inhe mat chhedo
        if (c is Cmd.Media || c is Cmd.Volume || c is Cmd.Chat || c is Cmd.Alarm || c is Cmd.Timer) return null
        if (c is Cmd.OpenApp && !Regex("""\b(aur|and|then|phir|fir)\b""").containsMatchIn(t)) return null
        val app = when {
            Regex("""\b(youtube|yt)\b""").containsMatchIn(t) -> "youtube"
            Regex("""\bspotify\b""").containsMatchIn(t) -> "spotify"
            else -> ""
        }
        if (c is Cmd.PlayMusic) {
            // Parser ka gaana saaf hai (app ke shabd nahi) toh wahi
            if (!appWord.containsMatchIn(c.query) && !Regex("""\b(kholo|open|aur|and)\b""").containsMatchIn(c.query)) {
                val q = edgeStrip(c.query) // "accha sa gaana" -> koi bhi gaana
                return c.copy(query = q, app = c.app.ifBlank { app })
            }
        } else if (c !is Cmd.Unknown && c !is Cmd.OpenApp) return null
        // "spotify kholo aur kesariya bajao" -> "aur" ke baad wala hissa
        var rest = t
        Regex("""\b(?:kholo|khol do|open|start|chalu karo)\b.*?\b(?:aur|and|then|phir|fir)\b\s*(.+)""").find(t)?.let { rest = it.groupValues[1] }
        rest = rest.replace(appWord, " ").replace(Regex("""\s+"""), " ").trim()
        val q = edgeStrip(rest)
        return Cmd.PlayMusic(q, app)
    }

    // ---------------- WhatsApp ----------------

    private val callWord = Regex("""\b(call|ghanti|phone|calling|dial)\b""")
    private val msgWord = Regex("""\b(message|messages|msg|massage|bhejo|bhej|send|text|likho|likh|bolo|bol do|keh do|kaho)\b""")
    private val split = Regex("""\s(?:ki|that|saying|bolo|bol do|likho|likh do|keh do|kaho)\s""")

    private fun stripWa(s: String): String = s.replace(waWord, " ")
        .replace(Regex("""\b(video|voice|call|calling|ghanti|phone|dial|message|messages|msg|massage|bhejo|bhej|send|text|likho)\b"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    private fun whatsapp(t: String, c: Cmd): Cmd? {
        val video = Regex("""\bvideo\b""").containsMatchIn(t)
        if (video || callWord.containsMatchIn(t)) {
            val name = edgeStrip(stripWa(t))
            if (name.isBlank()) return null
            return WhatsAppCmd(name, "", if (video) "video" else "call")
        }
        val isMsg = msgWord.containsMatchIn(t) || Regex("""\b(ki|that|saying)\b""").containsMatchIn(t)
        if (!isMsg) {
            if (c is Cmd.OpenApp) return null
            // "whatsapp rahul hi" — pehla shabd naam, baaki message
            val words = edgeStrip(t.replace(waWord, " ").replace(Regex("""\s+"""), " ").trim()).split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return null
            return WhatsAppCmd(words.first(), words.drop(1).joinToString(" "), "msg")
        }
        // "send hi to ankit on whatsapp"
        Regex("""\bsend\s+(.+?)\s+to\s+(\S+(?:\s\S+)?)""").find(t.replace(waWord, " ").replace(Regex("""\s+"""), " "))?.let { m ->
            val n = edgeStrip(m.groupValues[2])
            val x = m.groupValues[1].trim()
            if (n.isNotBlank() && x !in setOf("message", "a message", "msg", "text")) return WhatsAppCmd(n, x, "msg")
        }
        // Message ka hissa: "ki / that / saying / bolo" ke baad
        var who = t
        var text = ""
        split.find(" $t ")?.let { m ->
            val at = m.range.first
            val padded = " $t "
            who = padded.substring(0, at).trim()
            text = padded.substring(m.range.last + 1).trim()
        }
        // Naam: "X ko" sabse pakka, phir "to X"
        val name = Regex("""(?:^|\s)((?:\S+\s){0,2}?\S+)\s+ko\b""").find(stripWa(who))?.groupValues?.get(1)?.let { edgeStrip(it) }
            ?: Regex("""\bto\s+(\S+(?:\s\S+)?)""").find(stripWa(who))?.groupValues?.get(1)?.let { edgeStrip(it) }
            ?: edgeStrip(stripWa(who))
        if (name.isBlank()) return null
        // "whatsapp par rahul ko hello bolo" — naam ke baad bacha hua bhi message
        if (text.isBlank()) {
            val after = Regex("""\b${Regex.escape(name)}\s+(?:ko\s+)?(.+)""").find(stripWa(who))?.groupValues?.get(1) ?: ""
            text = edgeStrip(after)
        }
        return WhatsAppCmd(name, text, "msg")
    }

    // ---------------- Phone ka kaam? ----------------

    private val actionWord = Regex(
        """\b(call|ghanti|dial|whatsapp|whatsap|watsapp|message|msg|sms|spotify|youtube|gaana|gana|gaane|gane|song|songs|music|""" +
            """play|chalao|bajao|sunao|lagao|kholo|open|alarm|timer|reminder|yaad dila|torch|volume|wifi|bluetooth|brightness|navigate|rasta)\b"""
    )

    /** Bola hua phone ka koi kaam lagta hai (AI ki baat-cheet nahi) */
    fun looksLikeAction(t: String) = actionWord.containsMatchIn(t.lowercase())

    private val refusal = Regex(
        """(?i)\b(can'?t|cannot|unable|not able|don'?t have access|no access|api|as an ai|language model)\b|""" +
            """nahi kar sakt|नहीं कर सकत|एक्सेस नहीं|access nahi|एपीआई|ए पी आई"""
    )

    /** AI ne "main ye nahi kar sakti / API…" jaisa jawab diya? */
    fun isRefusal(reply: String) = refusal.containsMatchIn(reply)

    // ---------------- Apni baatein (janamdin) ----------------

    private val dobWord = """(?:date of birth|dob|d o b|birth ?date|birthday|bday|janamdin|janm ?din|janam ?din|janm tithi|janam tithi|janm ki tarikh|janam ki tarikh|paida)"""

    /**
     * "meri DOB kya hai" -> yaad se batao (aaj ki date nahi!)
     * "meri date of birth 12 june 1998 hai" -> yaad rakh lo
     */
    fun personal(t: String): Cmd? {
        if (!Regex("""\b$dobWord\b""").containsMatchIn(t)) return null
        // Kisi aur ka ("mummy ka birthday") — Memory me jaisa bola waisa
        val whose = Regex("""\b(\w+)\s+(?:ka|ki|ke|'s)\s+$dobWord""").find(t)?.groupValues?.get(1)
            ?.takeIf { it !in setOf("meri", "mera", "mere", "my", "apni", "apna") }
        val hasDate = Regex("""\b\d{1,2}\b|\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\w*\b|\b(19|20)\d\d\b""").containsMatchIn(t)
        val asking = Regex("""\b(kya|kab|kitni|kaun|what|when|which|batao|bata|bolo|tell|yaad hai|pata)\b""").containsMatchIn(t) || !hasDate
        if (hasDate && !Regex("""\b(kya hai|kab hai|what is|when is)\b""").containsMatchIn(t)) {
            val clean = t.replace(Regex("""\b$dobWord\b"""), " ").replace(Regex("""\s+"""), " ")
            val month = """(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"""
            val date = listOf(
                Regex("""\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?$month(?:\s+\d{4})?"""),
                Regex("""$month\s+\d{1,2}(?:st|nd|rd|th)?(?:\s*,?\s*\d{4})?"""),
                Regex("""\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?""")
            ).firstNotNullOfOrNull { it.find(clean)?.value } ?: return null
            val who = whose?.let { "$it's" } ?: "My"
            return Cmd.Remember("$who date of birth is $date")
        }
        if (asking) return Cmd.RecallAbout(listOfNotNull(whose, "birth", "birthday", "dob", "janamdin"), whose)
        return null
    }
}
