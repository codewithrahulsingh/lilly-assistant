package com.rahul.lilly

/** Call log: "missed" (kisne call kiya), "last" (aakhri call), "back" (jisne kiya use wapas), "redial" (aakhri dial kiya) */
data class CallLogCmd(val kind: String) : Cmd()

/** List: list = "shopping" / "notes"; op = add / read / clear / remove */
data class ListCmd(val list: String, val op: String, val item: String = "") : Cmd()

/** Calendar: op = read / add; day = today / tomorrow; at = samay (add ke liye) */
data class CalendarCmd(val op: String, val day: String, val title: String = "", val at: TimeParse.When? = null) : Cmd()

/** Ek saath do kaam: "torch jalao aur volume badhao" */
object Extras {

    fun parse(t: String): Cmd? = sms(t) ?: callLog(t) ?: list(t) ?: calendar(t)

    /** "note karo ki…" — notes me (yaaddasht se pehle) */
    fun note(t: String): Cmd? =
        Regex("""^(?:note (?:karo|kar lo|kar do|down|that|this)|note|likh lo|write down|take a note|make a note)\s*:?\s+(?:ki\s+|that\s+)?(.+)$""").find(t)
            ?.let { ListCmd("notes", "add", it.groupValues[1].trim()) }

    private fun sms(t: String): Cmd? =
        Regex("""^(?:send |bhejo )?(?:an? )?sms (?:to |bhejo )?(\S+(?:\s\S+)?) (?:saying|that|ki) (.+)$""").find(t)
            ?.let { WhatsAppCmd(it.groupValues[1].trim(), it.groupValues[2].trim(), "sms") }

    // ---------------- Call log ----------------

    private fun callLog(t: String): Cmd? {
        if (Regex("""\b(call ?back|wapas call|vapas call|wapis call|jisne (?:abhi )?call kiya (?:tha )?(?:use|usko|unko|use hi)|return (?:the |that )?call|call (?:them|him|her) back)\b""").containsMatchIn(t))
            return CallLogCmd("back")
        if (Regex("""\b(redial|last number|aakhri number|akhri number|pichla number|last dialed|last dialled|dobara (?:wahi )?(?:number )?(?:call|lagao|milao))\b""").containsMatchIn(t))
            return CallLogCmd("redial")
        if (Regex("""\b(missed calls?|miss calls?|missed call|chhute? (?:hue )?calls?|kisne (?:mujhe )?call kiya|kiska call aaya|kis ka call aaya|kiske call aaye|who called|any (?:missed )?calls|koi call aaya|kitne call aaye)\b""").containsMatchIn(t))
            return CallLogCmd("missed")
        if (Regex("""\b(last call|aakhri call|akhri call|pichli call|recent calls?|call log|call history)\b""").containsMatchIn(t))
            return CallLogCmd("last")
        return null
    }

    // ---------------- List / notes ----------------

    private const val LST = """(?:(?:meri|mera|my|the)\s+)?(?:shopping\s+|grocery\s+|saman\s+ki\s+|saamaan\s+ki\s+)?list"""
    private const val ADD = """(?:jodo|jod do|jod lo|add karo|add kar do|add kardo|add|daalo|daal do|dal do|dalo|likho|likh do|likh lo|me daalo|rakh lo)"""

    private fun list(t: String): Cmd? {
        val notes = Regex("""\bnotes?\b""").containsMatchIn(t)
        val which = if (notes && !Regex("""\blist\b""").containsMatchIn(t)) "notes" else "shopping"
        // Padho
        if (Regex("""^(?:$LST|(?:mere |my )?notes?)\s*(?:padho|padh do|batao|bata do|dikhao|sunao|kya hai|me kya hai|mein kya hai|read karo)?$""").matches(t) ||
            Regex("""\b(?:what'?s|what is) (?:on|in) (?:my )?(?:shopping |grocery )?(list|notes)\b|\bread (?:my |the )?(?:shopping |grocery )?(list|notes)\b""").containsMatchIn(t))
            return ListCmd(which, "read")
        // Saaf
        if (Regex("""\b(?:$LST|notes?)\s+(?:saaf|clear|khali|khaali|delete|mita|mitao)\b|\b(?:clear|delete|empty)\s+(?:my |the )?(?:shopping |grocery )?(?:list|notes)\b""").containsMatchIn(t))
            return ListCmd(which, "clear")
        // Hatao
        Regex("""^(.+?)\s+(?:ko\s+)?(?:list|notes?)\s+se\s+(?:hatao|hata do|nikalo|nikal do|remove karo|delete karo|kaat do)$""").find(t)?.let {
            return ListCmd(which, "remove", it.groupValues[1].trim())
        }
        Regex("""^(?:remove|delete|cross off)\s+(.+?)\s+from\s+(?:my |the )?(?:shopping |grocery )?(?:list|notes)$""").find(t)?.let {
            return ListCmd(which, "remove", it.groupValues[1].trim())
        }
        // Jodo
        Regex("""^$LST\s+(?:me|mein|main|par|pe)\s+(.+?)\s+$ADD$""").find(t)?.let { return ListCmd("shopping", "add", it.groupValues[1].trim()) }
        Regex("""^(.+?)\s+(?:ko\s+)?$LST\s+(?:me|mein|main|par|pe)\s+$ADD$""").find(t)?.let { return ListCmd("shopping", "add", it.groupValues[1].trim()) }
        Regex("""^add\s+(.+?)\s+to\s+(?:my |the )?(?:shopping |grocery )?list$""").find(t)?.let { return ListCmd("shopping", "add", it.groupValues[1].trim()) }
        Regex("""^(?:note (?:karo|kar lo|kar do|down|that|this)|note|likh lo|write down|take a note|make a note)\s*:?\s+(?:ki\s+|that\s+)?(.+)$""").find(t)?.let {
            return ListCmd("notes", "add", it.groupValues[1].trim())
        }
        return null
    }

    // ---------------- Calendar ----------------

    private val calWord = Regex("""\b(calendar|calender|meeting|meetings|event|events|schedule|appointment|appointments)\b""")
    private val addWord = Regex("""\b(daalo|daal do|dal do|dalo|add|laga do|lagao|set karo|schedule karo|fix karo|rakh do|rakho|book karo|create|banao|bana do|put)\b""")

    private fun calendar(t: String): Cmd? {
        if (!calWord.containsMatchIn(t)) return null
        val day = when {
            Regex("""\b(kal|tomorrow|tmrw)\b""").containsMatchIn(t) -> "tomorrow"
            else -> "today"
        }
        if (addWord.containsMatchIn(t)) {
            val at = TimeParse.parse(t)
            val title = Regex("""\b(meeting|appointment|call|class|interview|party|lunch|dinner)\b(?:\s+(?:with|ke saath|ke sath|se)\s+(\w+(?:\s\w+)?))?""").find(t)
                ?.let { m -> listOf(m.groupValues[1].replaceFirstChar { it.uppercase() }, m.groupValues[2].split(" ").first().takeIf { it !in setOf("tomorrow", "today", "kal", "aaj", "at") } ?: "").filter { it.isNotBlank() }.joinToString(" with ") }
                ?: Regex("""(\w+(?:\s\w+)?)\s+(?:ke saath|ke sath|se)\s+meeting""").find(t)?.let { "Meeting with ${it.groupValues[1]}" }
                ?: "Event"
            val who = Regex("""(\w+)\s+(?:ke saath|ke sath)\b""").find(t)?.groupValues?.get(1)
            return CalendarCmd("add", day, if (who != null && title == "Meeting") "Meeting with $who" else title, at)
        }
        return CalendarCmd("read", day)
    }

    // ---------------- Do kaam ek saath ----------------

    private val joiner = Regex("""\s+(?:aur fir|aur phir|and then|aur|and|phir|fir|then|uske baad|uske bad)\s+""")

    /**
     * "torch jalao aur volume badhao" -> do hisse, agar dono alag-alag kaam hain.
     * Message / gaane / yaad wale vakya (jinme "aur" khud ho sakta hai) nahi todte.
     */
    fun splitTwo(t: String, parse: (String) -> Cmd): Pair<String, String>? {
        val whole = parse(t)
        if (whole is WhatsAppCmd || whole is Cmd.Remember || whole is Cmd.Teach || whole is ListCmd ||
            (whole is Cmd.PlayMusic && whole.query.isNotBlank() && !joiner.containsMatchIn(whole.query))) return null
        for (m in joiner.findAll(t)) {
            val a = t.substring(0, m.range.first).trim()
            val b = t.substring(m.range.last + 1).trim()
            if (a.isBlank() || b.isBlank()) continue
            val ca = parse(a)
            val cb = parse(b)
            val real = { c: Cmd -> c !is Cmd.Unknown && c !is Cmd.Chat && c !is Cmd.Teach }
            if (real(ca) && real(cb) && ca != cb) return a to b
        }
        return null
    }
}
