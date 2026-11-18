package com.rahul.lilly

/**
 * Alarm / timer / reminder ki baat samajhna — English, Hindi, Hinglish.
 * "subah 6 baje", "sadhe saat", "7:30 pm", "10 minute baad", "in half an hour", "har roz 6 baje"…
 * Isme Android ka kuch nahi hai, isliye iske tests har build par chalte hain.
 */
object TimeParse {

    /**
     * Bola gaya samay. period: "am", "pm", "noon", "night" ya "" (pata nahi — agla aane wala samay maano).
     * inMinutes >= 0 ho toh "abhi se itne minute baad".
     */
    data class When(val hour: Int = -1, val minute: Int = 0, val period: String = "", val inMinutes: Int = -1)

    private val numWords: Map<String, Int> = buildMap {
        fun p(v: Int, vararg w: String) = w.forEach { put(it, v) }
        p(1, "one", "ek"); p(2, "two", "do"); p(3, "three", "teen", "tin"); p(4, "four", "char", "chaar")
        p(5, "five", "paanch", "panch"); p(6, "six", "chhe", "che", "chhah", "chah"); p(7, "seven", "saat", "sat")
        p(8, "eight", "aath", "ath"); p(9, "nine", "nau"); p(10, "ten", "das", "dus")
        p(11, "eleven", "gyarah", "gyara", "gyaarah"); p(12, "twelve", "barah", "bara", "baarah")
        p(15, "fifteen", "pandrah", "pandra"); p(20, "twenty", "bees", "bis"); p(25, "twenty five", "pachchis", "pachis")
        p(30, "thirty", "tees", "tis"); p(40, "forty", "chalis", "chaalis"); p(45, "forty five", "paintalis", "pentalis")
        p(50, "fifty", "pachas", "pachaas")
    }
    private const val NUM = """\d{1,3}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty five|twenty|thirty|forty five|forty|fifty|ek|do|teen|tin|char|chaar|paanch|panch|chhe|che|chhah|chah|saat|sat|aath|ath|nau|das|dus|gyarah|gyara|gyaarah|barah|bara|baarah|pandrah|pandra|bees|bis|pachchis|pachis|tees|tis|chalis|chaalis|paintalis|pentalis|pachas|pachaas"""

    private fun num(s: String?): Int? = s?.trim()?.let { it.toIntOrNull() ?: numWords[it] }

    private val MIN = """minutes?|mins?|minat|mint|minute|minton"""
    private val HOUR = """hours?|hrs?|ghante|ghanta|ghanton"""
    private val SEC = """seconds?|secs?|second|sekand|sekind"""

    /** "10 minute baad" / "in half an hour" / "dedh ghante me" -> minute */
    fun relativeMinutes(t: String): Int? {
        val after = """(?:baad|bad|me|mein|mai|later|from now)"""
        Regex("""\b(?:in|after)\s+(?:an?\s+)?(?:half\s+an?\s+hour|half\s+hour)\b""").find(t)?.let { return 30 }
        Regex("""\b(?:aadhe|adhe|aadha|adha)\s+(?:$HOUR)\s+$after\b""").find(t)?.let { return 30 }
        Regex("""\b(?:dedh|derh)\s+(?:$HOUR)\s+$after\b""").find(t)?.let { return 90 }
        Regex("""\b(?:dhai|dhaai)\s+(?:$HOUR)\s+$after\b""").find(t)?.let { return 150 }
        Regex("""\b(?:in|after)\s+an?\s+hour\b""").find(t)?.let { return 60 }
        // "in 1 hour 30 minutes" / "1 ghante 20 minute baad"
        Regex("""\b(?:in|after)?\s*($NUM)\s+(?:$HOUR)(?:\s+(?:and\s+)?($NUM)\s+(?:$MIN))?\s*(?:$after)?\b""").find(t)?.let { m ->
            val leadIn = m.value.trim().startsWith("in") || m.value.trim().startsWith("after")
            val trail = Regex("""\b(?:baad|bad|me|mein|mai|later|from now)\b""").containsMatchIn(m.value)
            if (leadIn || trail) {
                val h = num(m.groupValues[1]) ?: return@let
                return h * 60 + (num(m.groupValues[2]) ?: 0)
            }
        }
        Regex("""\b(?:in|after)\s+($NUM)\s+(?:$MIN)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return it } }
        Regex("""\b($NUM)\s+(?:$MIN)\s+$after\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return it } }
        return null
    }

    /** Timer ki lambai seconds me: "10 minute", "1 hour 30 minutes", "30 seconds", "aadha ghanta" */
    fun durationSeconds(t: String): Int? {
        var total = 0
        var found = false
        Regex("""\b(?:half\s+an?\s+hour|aadha\s+ghanta|adha\s+ghanta|aadhe\s+ghante|adhe\s+ghante)\b""").find(t)?.let { total += 1800; found = true }
        Regex("""\b(?:dedh|derh)\s+(?:$HOUR)\b""").find(t)?.let { total += 5400; found = true }
        Regex("""\b(?:an?|ek)\s+(?:hour|ghanta|ghante)\b""").find(t)?.let { if (!found) { total += 3600; found = true } }
        Regex("""\b($NUM)\s+(?:$HOUR)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { total += it * 3600; found = true } }
        Regex("""\b($NUM)\s+(?:$MIN)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { total += it * 60; found = true } }
        Regex("""\b($NUM)\s+(?:$SEC)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { total += it; found = true } }
        return if (found && total > 0) total else null
    }

    private fun period(t: String): String = when {
        Regex("""\b(am|a m|subah|subha|savere|sawere|morning|tomorrow morning)\b""").containsMatchIn(t) -> "am"
        Regex("""\b(dopahar|dopehar|dupahar|afternoon|noon)\b""").containsMatchIn(t) -> "noon"
        Regex("""\b(pm|p m|shaam|sham|evening)\b""").containsMatchIn(t) -> "pm"
        Regex("""\b(raat|rat|night|tonight)\b""").containsMatchIn(t) -> "night"
        else -> ""
    }

    /** Ghadi ka samay: "6 baje", "7:30 pm", "sadhe saat", "quarter to 9", "6 baj kar 20 minute" */
    fun clock(t: String): When? {
        val per = period(t)
        val baje = """(?:baje|bje|baj|bajke|bajkar|baj kar|baj ke|o clock|oclock|o'clock)"""

        // 7:30 / 07:30 / 19:45
        Regex("""\b(\d{1,2}):(\d{2})\b""").find(t)?.let { m ->
            return When(m.groupValues[1].toInt(), m.groupValues[2].toInt(), per)
        }
        // "half past 7" / "quarter past 7" / "quarter to 9"
        Regex("""\bhalf past ($NUM)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return When(it, 30, per) } }
        Regex("""\bquarter past ($NUM)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return When(it, 15, per) } }
        Regex("""\bquarter to ($NUM)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return When(if (it == 1) 12 else it - 1, 45, per) } }
        // "sadhe saat" (7:30), "sava aath" (8:15), "paune nau" (8:45), "dedh" (1:30), "dhai" (2:30)
        Regex("""\b(?:sadhe|saadhe|sade|saade)\s+($NUM)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return When(it, 30, per) } }
        Regex("""\b(?:sava|savaa|sawa)\s+($NUM)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return When(it, 15, per) } }
        Regex("""\b(?:paune|pone)\s+($NUM)\b""").find(t)?.let { m -> num(m.groupValues[1])?.let { return When(if (it == 1) 12 else it - 1, 45, per) } }
        if (Regex("""\b(?:dedh|derh)\s+$baje""").containsMatchIn(t)) return When(1, 30, per)
        if (Regex("""\b(?:dhai|dhaai)\s+$baje""").containsMatchIn(t)) return When(2, 30, per)
        // "6 baj kar 20 minute" / "6 baje 20 minute"
        Regex("""\b($NUM)\s+$baje\s+($NUM)\s+(?:$MIN)\b""").find(t)?.let { m ->
            val h = num(m.groupValues[1]); val mi = num(m.groupValues[2])
            if (h != null && mi != null) return When(h, mi, per)
        }
        // "6 baje" / "6 o'clock"
        Regex("""\b($NUM)\s+$baje""").find(t)?.let { m -> num(m.groupValues[1])?.let { return When(it, 0, per) } }
        // "7 am" / "7am" / "7 30 pm" / "seven thirty pm"
        Regex("""\b($NUM)\s*(?:($NUM)\s*)?(?:am|pm|a m|p m)\b""").find(t)?.let { m ->
            val h = num(m.groupValues[1])
            if (h != null) return When(h, num(m.groupValues[2]) ?: 0, per)
        }
        // "7 30" (bina chinh ke, Google aksar aise likhta hai) — sirf alarm ke context me bulaya jaata hai
        Regex("""\b(\d{1,2})\s+(\d{2})\b""").find(t)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) return When(h, mi, per)
        }
        // "alarm for 6" / "6 ka alarm" / "subah 6 ka"
        Regex("""\b(?:for|at|pe|par)\s+(\d{1,2})\b""").find(t)?.let { m -> return When(m.groupValues[1].toInt(), 0, per) }
        Regex("""\b(\d{1,2})\s+(?:ka|ki|ke)\b""").find(t)?.let { m -> return When(m.groupValues[1].toInt(), 0, per) }
        if (per.isNotEmpty()) {
            Regex("""\b(\d{1,2})\b""").find(t)?.let { m -> return When(m.groupValues[1].toInt(), 0, per) }
        }
        return null
    }

    /** Relative ho toh pehle wahi, warna ghadi ka samay */
    fun parse(t: String): When? {
        relativeMinutes(t)?.let { return When(inMinutes = it) }
        val c = clock(t) ?: return null
        if (c.hour !in 0..23 || c.minute !in 0..59) return null
        return c
    }

    /** "har roz" / "daily" = "daily", "weekdays" / "monday to friday" = "weekdays" */
    fun repeat(t: String): String = when {
        Regex("""\b(har roz|har din|roz|rozana|daily|every day|everyday|every morning|har subah)\b""").containsMatchIn(t) -> "daily"
        Regex("""\b(weekdays?|monday to friday|mon to fri|somvar se shukravar|working days)\b""").containsMatchIn(t) -> "weekdays"
        else -> ""
    }

    /**
     * Asli ghante-minute (24 ghante wali ghadi). nowH/nowM = abhi ka samay.
     * Period na bola ho toh agla aane wala samay: abhi 3 PM hai aur "6 baje" bola toh 6 PM.
     */
    fun resolve(w: When, nowH: Int, nowM: Int): Pair<Int, Int> {
        if (w.inMinutes >= 0) {
            val total = (nowH * 60 + nowM + w.inMinutes) % (24 * 60)
            return Pair(total / 60, total % 60)
        }
        var h = w.hour
        val m = w.minute
        if (h > 12) return Pair(h % 24, m)
        when (w.period) {
            "am" -> h %= 12
            "pm" -> h = h % 12 + 12
            "noon" -> h = if (h == 12) 12 else if (h in 1..5) h + 12 else h
            "night" -> h = if (h == 12) 0 else if (h in 6..11) h + 12 else h
            else -> {
                val now = nowH * 60 + nowM
                val am = (h % 12) * 60 + m
                val pm = am + 12 * 60
                h = if (am > now) h % 12 else if (pm > now) h % 12 + 12 else h % 12
            }
        }
        return Pair(h, m)
    }

    /** "6:05 AM" */
    fun label(h: Int, m: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${m.toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    }

    /** "सुबह 6:05" */
    fun labelHi(h: Int, m: Int): String {
        val part = when (h) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..19 -> "शाम"; else -> "रात" }
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0) "$part $h12 बजे" else "$part $h12 बजकर $m मिनट"
    }

    /** Timer bolne ke liye: "10 minutes" / "1 hour 30 minutes" / "45 seconds" */
    fun durationLabel(sec: Int): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h hour" + if (h > 1) "s" else "")
        if (m > 0) parts.add("$m minute" + if (m > 1) "s" else "")
        if (s > 0) parts.add("$s second" + if (s > 1) "s" else "")
        return parts.joinToString(" ")
    }

    fun durationLabelHi(sec: Int): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h घंटे")
        if (m > 0) parts.add("$m मिनट")
        if (s > 0) parts.add("$s सेकंड")
        return parts.joinToString(" ")
    }
}
