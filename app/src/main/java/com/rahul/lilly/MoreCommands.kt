package com.rahul.lilly

/** WhatsApp: kind = "msg", "call" ya "video". text khaali = poochho kya bhejna hai */
data class WhatsAppCmd(val target: String, val text: String = "", val kind: String = "msg") : Cmd()

/** Rasta: dest = jagah ya "home"/"office"; mode = "d" (gaadi), "w" (paidal), "l" (bike) */
data class NavigateCmd(val dest: String, val mode: String = "d") : Cmd()

/** Paas me: "nearest petrol pump" */
data class NearbyCmd(val what: String) : Cmd()

/** "Mera ghar Malviya Nagar Jaipur hai" — kind = "home" / "office" */
data class SaveAddressCmd(val kind: String, val address: String) : Cmd()

/** "Kiska message aaya?" — app = "" (sab) ya "whatsapp" */
data class NotificationsCmd(val app: String = "") : Cmd()

/** "Good morning" / "Good night" */
data class RoutineCmd(val kind: String) : Cmd()

/** "Mausam kaisa hai" — day = "today" / "tomorrow" */
data class WeatherCmd(val day: String = "today") : Cmd()

/**
 * Phone settings. what: "dnd" (on/off), "brightness" (up/down/max/min/0-100), "ringer" (silent/vibrate/normal),
 * "panel" (wifi/bluetooth/data/hotspot/location/airplane/display/sound/battery/settings)
 */
data class SettingCmd(val what: String, val value: String) : Cmd()

/**
 * Naye commands: WhatsApp, rasta, notifications, routine, mausam, settings.
 * Isme Android ka kuch nahi hai, isliye iske tests har build par chalte hain.
 */
object MoreCommands {

    fun parse(t: String): Cmd? =
        whatsapp(t) ?: plainMessage(t) ?: address(t) ?: navigation(t) ?: notifications(t) ?: routine(t) ?: weather(t) ?: settings(t)

    // ---------------- WhatsApp ----------------

    private const val WA = """(?:whatsapp|whats app|watsapp|wats app|whatsap)"""
    private const val ON = """(?:pe|par|per|me|mein|on|se)"""
    private const val MSG = """(?:message|messages|msg|msgs|massage|mesej|text|sms)"""
    private const val SAY = """(?:bolo|bol do|boldo|likho|likh do|$MSG karo|$MSG kar do|$MSG kardo|$MSG bhejo|$MSG bhej do|$MSG send karo|bhejo|bhej do|send karo|send kar do|keh do|kah do|batao|bata do)"""

    private fun cleanTarget(s: String): String {
        var n = s.trim()
        for (p in listOf("to ", "my ", "mere ", "meri ", "mera ", "apne ", "apni ")) n = n.removePrefix(p)
        for (x in listOf(" ko", " ji ko", " on whatsapp")) n = n.removeSuffix(x)
        return n.trim()
    }

    private fun cleanText(s: String): String =
        s.trim().removePrefix("ki ").removePrefix("that ").removePrefix("saying ").trim()

    private fun whatsapp(t: String): Cmd? {
        if (!Regex("""\b$WA\b""").containsMatchIn(t)) return null
        // Calls
        val video = Regex("""\bvideo\b""").containsMatchIn(t)
        val kind = if (video) "video" else "call"
        val callPatterns = listOf(
            Regex("""^(.+?)\s+(?:ko|se)\s+$WA\s+(?:$ON\s+)?(?:video\s+|voice\s+)?call(?:\s+.*)?$"""),
            Regex("""^(?:make a |start a |do a )?$WA\s+(?:video\s+|voice\s+)?call\s+(?:to\s+|with\s+)?(.+)$"""),
            Regex("""^(?:video\s+|voice\s+)?call\s+(.+?)\s+(?:on|in|through|via)\s+$WA$"""),
            Regex("""^(.+?)\s+(?:ko|se)\s+(?:video\s+|voice\s+)?call\s+(?:karo|kar do|lagao|laga do)?\s*$WA\s*$ON?\s*(?:se|pe|par)?.*$""")
        )
        for (re in callPatterns) {
            val m = re.find(t) ?: continue
            val who = cleanTarget(m.groupValues[1])
            if (who.isNotBlank()) return WhatsAppCmd(who, kind = kind)
        }
        // "WhatsApp par Nandu ko messages karo paise dal de" (WhatsApp pehle, naam baad me)
        Regex("""^$WA\s+(?:$ON\s+)?(.+?)\s+ko\s+(?:$SAY|$MSG)\s*(?:ki\s+|that\s+)?(.*)$""").find(t)?.let { m ->
            val who = cleanTarget(m.groupValues[1])
            if (who.isNotBlank()) return WhatsAppCmd(who, cleanText(m.groupValues[2]), "msg")
        }
        // Message ke saath text
        val msgPatterns: List<Pair<Regex, Boolean>> = listOf( // Boolean: text pehle group me?
            Regex("""^(?:send\s+(?:a\s+)?)?$WA\s+(?:message\s+|msg\s+|text\s+)?(?:to\s+)?(.+?)\s+(?:saying|that|ki)\s+(.+)$""") to false,
            Regex("""^(?:send|text|message|tell|msg)\s+(.+?)\s+(?:on|in|through|via)\s+$WA\s+(?:saying\s+|that\s+)?(.+)$""") to false,
            Regex("""^(.+?)\s+ko\s+$WA\s+(?:$ON\s+)?$SAY\s+(?:ki\s+)?(.+)$""") to false,
            Regex("""^(.+?)\s+ko\s+$WA\s+(?:$ON\s+)?$MSG\s+(?:karo|bhejo|kar do|bhej do)\s+(?:ki\s+)?(.+)$""") to false,
            Regex("""^(.+?)\s+ko\s+(?:$SAY|$MSG)\s+$WA\s+(?:$ON\s+)?(?:ki\s+)?(.+)$""") to false,
            Regex("""^(.+?)\s+ko\s+$WA\s+(?:$ON\s+)?(.+?)\s+$SAY$""") to false
        )
        for ((re, _) in msgPatterns) {
            val m = re.find(t) ?: continue
            val who = cleanTarget(m.groupValues[1])
            val text = cleanText(m.groupValues[2])
            if (who.isNotBlank() && text.isNotBlank()) return WhatsAppCmd(who, text, "msg")
        }
        // Text nahi bola: "Rohit ko WhatsApp karo" / "send a WhatsApp to Rohit"
        val noText = listOf(
            Regex("""^(.+?)\s+ko\s+$WA\s+(?:$ON\s+)?(?:karo|kar do|message karo|msg karo|bhejo|bhej do|message|msg|$SAY)$"""),
            Regex("""^(?:send\s+(?:a\s+)?)?$WA\s+(?:message\s+|msg\s+)?to\s+(.+)$"""),
            Regex("""^(?:message|text|msg)\s+(.+?)\s+(?:on|in)\s+$WA$""")
        )
        for (re in noText) {
            val m = re.find(t) ?: continue
            val who = cleanTarget(m.groupValues[1])
            if (who.isNotBlank()) return WhatsAppCmd(who, "", "msg")
        }
        return null
    }

    /**
     * WhatsApp shabd ke bina: "Nandu ko message karo ki paise dal de" / "text mom that I'm late" —
     * India me "message" ka matlab aksar WhatsApp hi hota hai.
     */
    private fun plainMessage(t: String): Cmd? {
        Regex("""^(.+?)\s+ko\s+$SAY\s+(?:ki\s+|that\s+)(.+)$""").find(t)?.let { m ->
            val who = cleanTarget(m.groupValues[1])
            if (who.isNotBlank()) return WhatsAppCmd(who, cleanText(m.groupValues[2]), "msg")
        }
        Regex("""^(.+?)\s+ko\s+$MSG\s+(?:karo|kar do|bhejo|bhej do)$""").find(t)?.let { m ->
            val who = cleanTarget(m.groupValues[1])
            if (who.isNotBlank()) return WhatsAppCmd(who, "", "msg")
        }
        Regex("""^(?:send\s+(?:a\s+)?)?(?:message|text|msg)\s+(?:to\s+)?(.+?)\s+(?:saying|that)\s+(.+)$""").find(t)?.let { m ->
            val who = cleanTarget(m.groupValues[1])
            if (who.isNotBlank()) return WhatsAppCmd(who, cleanText(m.groupValues[2]), "msg")
        }
        return null
    }

    // ---------------- Address / rasta ----------------

    private fun place(s: String): String {
        val p = s.trim().removePrefix("the ").removePrefix("my ").removePrefix("mere ").removePrefix("apne ").trim()
        return when (p) {
            "ghar", "home", "house", "apna ghar", "mera ghar", "ghr" -> "home"
            "office", "work", "daftar", "dafter", "workplace", "my office", "mera office" -> "office"
            else -> p
        }
    }

    private fun address(t: String): Cmd? {
        Regex("""^(?:my\s+)?(home|office|work)\s+(?:address\s+)?is\s+(?:at\s+|in\s+)?(.{4,})$""").find(t)?.let {
            return SaveAddressCmd(place(it.groupValues[1]), it.groupValues[2].trim())
        }
        Regex("""^(?:save|set)\s+(?:my\s+)?(home|office|work)\s+(?:address\s+)?(?:as|to)\s+(.{4,})$""").find(t)?.let {
            return SaveAddressCmd(place(it.groupValues[1]), it.groupValues[2].trim())
        }
        Regex("""^mera\s+(ghar|office|daftar)\s+(?:ka address\s+)?(.{4,}?)\s+(?:me hai|mein hai|pe hai|par hai|hai)$""").find(t)?.let {
            return SaveAddressCmd(place(it.groupValues[1]), it.groupValues[2].trim())
        }
        Regex("""^(?:my\s+city\s+is|i\s+live\s+in|set\s+my\s+city\s+(?:to|as))\s+(.{3,})$""").find(t)?.let {
            return SaveAddressCmd("city", it.groupValues[1].trim())
        }
        Regex("""^(?:mera\s+(?:shehar|shahar|sheher|city)|main)\s+(.{3,}?)\s+(?:hai|me rehta hoon|mein rehta hoon|me rehti hoon|mein rehti hoon)$""").find(t)?.let {
            if (!t.startsWith("main ") || Regex("""reh(ta|ti) hoon$""").containsMatchIn(t)) return SaveAddressCmd("city", it.groupValues[1].trim())
        }
        return null
    }

    private fun mode(t: String): String = when {
        Regex("""\b(paidal|walk|walking|on foot|pedal)\b""").containsMatchIn(t) -> "w"
        Regex("""\b(bike|scooty|scooter|two wheeler|motorcycle|motorbike)\b""").containsMatchIn(t) -> "l"
        else -> "d"
    }

    private val modeWords = Regex("""\s+(?:by|on|via)\s+(?:walk|foot|bike|car|scooty)\b|\s+(?:paidal|bike se|car se|gaadi se|scooty se)\b|\s+walking\b""")

    private fun navigation(t: String): Cmd? {
        val m = mode(t)
        val s = t.replace(modeWords, "").trim()
        // Paas me: "nearest petrol pump", "petrol pump near me", "paas me ATM"
        Regex("""^(?:find\s+(?:a\s+|the\s+)?|show\s+(?:me\s+)?)?(?:nearest|nearby|closest)\s+(.+)$""").find(s)?.let { return NearbyCmd(it.groupValues[1].trim()) }
        Regex("""^(?:find\s+(?:a\s+|the\s+)?|show\s+(?:me\s+)?)?(.+?)\s+(?:near me|nearby|around me|close by)$""").find(s)?.let { return NearbyCmd(it.groupValues[1].trim()) }
        Regex("""^(?:sabse\s+)?(?:paas|pass|nazdeek|najdeek|nazdik|najdik|aas paas)\s+(?:me\s+|mein\s+|ka\s+|ki\s+|ke\s+|wala\s+|wali\s+)?(.+?)(?:\s+(?:kahan hai|kaha hai|batao|dikhao|dhundo))?$""").find(s)?.let { return NearbyCmd(it.groupValues[1].trim()) }
        Regex("""^(.+?)\s+(?:paas me|paas mein|aas paas|nazdeek)\s+(?:kahan|kaha)\s+(?:hai|milega|milegi)$""").find(s)?.let { return NearbyCmd(it.groupValues[1].trim()) }
        // Rasta
        val patterns = listOf(
            Regex("""^(?:navigate|navigation|start navigation|get directions|show directions|directions|direction|route|take me|drive me|guide me)\s+(?:to\s+|for\s+|me to\s+)?(.+)$"""),
            Regex("""^how (?:do i|to|can i) (?:get|go|reach) (?:to\s+)?(.+)$"""),
            Regex("""^how far is\s+(.+)$"""),
            Regex("""^(.+?)\s+(?:ka|ki|ke)\s+(?:rasta|raasta|route|direction|directions|navigation)(?:\s+(?:dikhao|batao|bata do|dikha do|chalu karo|lagao|kholo|chahiye))?$"""),
            Regex("""^(.+?)\s+(?:kaise|kese)\s+(?:jaun|jau|jaye|jayen|jaayein|pahunchu|pahuchu|jana hai|jaate hain)$"""),
            Regex("""^(?:mujhe\s+)?(.+?)\s+(?:le chalo|le chalna|jana hai)$"""),
            Regex("""^(.+?)\s+(?:kitni door|kitna door|kitni dur|kitna dur)\s+hai$""")
        )
        for (re in patterns) {
            val x = re.find(s) ?: continue
            val dest = place(x.groupValues[1])
            if (dest.isNotBlank()) return NavigateCmd(dest, m)
        }
        return null
    }

    // ---------------- Notifications / routine / mausam ----------------

    private fun notifications(t: String): Cmd? {
        val hit = Regex(
            """kiska message|kis ka message|kiske message|koi message aaya|koi naya message|message aaya kya|""" +
                """read (?:my |the )?(?:notifications|messages)|(?:any|new) (?:new )?messages|who (?:messaged|texted) me|""" +
                """what are my notifications|notifications? (?:padho|batao|sunao|read)|""" +
                """(?:my|meri|mere) notifications?|kya notification|kaun sa notification"""
        ).containsMatchIn(t)
        if (!hit) return null
        return NotificationsCmd(if (Regex("""\b$WA\b""").containsMatchIn(t)) "whatsapp" else "")
    }

    private fun routine(t: String): Cmd? {
        if (Regex("""^(?:good morning|gud morning|gm|suprabhat|shubh prabhat|morning)(?: lilly)?$|^(?:brief me|my day|daily briefing|aaj ka plan|aaj ka din kaisa hai|how(?:'s| is) my day)$""").containsMatchIn(t)) return RoutineCmd("morning")
        if (Regex("""^(?:good night|gud night|gn|shubh ratri|shubh raatri)(?: lilly)?$|^(?:so raha hoon|so rahi hoon|sone ja raha hoon|sone ja rahi hoon|i'?m going to sleep|going to bed)$""").containsMatchIn(t)) return RoutineCmd("night")
        return null
    }

    private fun weather(t: String): Cmd? {
        if (!Regex("""\b(weather|mausam|mosam|temperature|temp|barish|baarish|rain|raining|garmi|thand|thandi|umbrella|chhata|forecast|humidity)\b""").containsMatchIn(t)) return null
        // "garmi" / "thand" sirf sawaal ho toh ("kitni garmi hai")
        if (Regex("""\b(garmi|thand|thandi)\b""").containsMatchIn(t) && !Regex("""\b(kitni|kitna|hai kya|hogi|padegi|today|aaj|kal)\b""").containsMatchIn(t)) return null
        return WeatherCmd(if (Regex("""\b(tomorrow|kal)\b""").containsMatchIn(t)) "tomorrow" else "today")
    }

    // ---------------- Phone settings ----------------

    private val offWords = Regex("""\b(off|band|bandh|hatao|hata do|disable|deactivate|turn off|switch off|nikalo)\b""")

    private fun settings(t: String): Cmd? {
        // Payment apps Accessibility helper se rukte hain — "payment mode" = helper band
        if (Regex("""\b(payment mode|payment karna hai|paise bhejne hai|helper (?:band|off|hatao|band karo)|turn off (?:the )?helper)\b""").containsMatchIn(t)) {
            return SettingCmd("helper", "off")
        }
        if (Regex("""\b(do not disturb|dont disturb|don't disturb|dnd|disturb mat karo|disturb na karo)\b""").containsMatchIn(t)) {
            return SettingCmd("dnd", if (offWords.containsMatchIn(t)) "off" else "on")
        }
        if (Regex("""\b(brightness|screen ki roshni|chamak|screen light|bright)\b""").containsMatchIn(t)) {
            Regex("""\b(\d{1,3})\s*(?:percent|%|pratishat)?""").find(t)?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it in 0..100) return SettingCmd("brightness", it.toString())
            }
            return SettingCmd(
                "brightness", when {
                    Regex("""\b(full|max|maximum|poori|puri|sabse zyada)\b""").containsMatchIn(t) -> "max"
                    Regex("""\b(min|minimum|lowest|sabse kam)\b""").containsMatchIn(t) -> "min"
                    Regex("""\b(down|kam|dim|lower|decrease|reduce|ghata|ghatao|low|halki)\b""").containsMatchIn(t) -> "down"
                    else -> "up"
                }
            )
        }
        if (Regex("""\b(silent mode|phone silent|silent karo|silent kar do|silent par|silent pe|put .*on silent|ringer off)\b""").containsMatchIn(t)) {
            return SettingCmd("ringer", if (Regex("""\b(off|hatao|band karo silent|normal)\b""").containsMatchIn(t) && !Regex("""ringer off""").containsMatchIn(t)) "normal" else "silent")
        }
        if (Regex("""\b(vibrate|vibration|vibrate mode)\b""").containsMatchIn(t)) {
            return SettingCmd("ringer", if (offWords.containsMatchIn(t)) "normal" else "vibrate")
        }
        if (Regex("""\b(normal mode|ringer on|ring mode|general mode)\b""").containsMatchIn(t)) return SettingCmd("ringer", "normal")
        val panels = listOf(
            "wifi" to """\b(wifi|wi fi|wi-fi|wai fai|wifai)\b""",
            "bluetooth" to """\b(bluetooth|blue tooth|bt)\b""",
            "hotspot" to """\b(hotspot|hot spot|tethering)\b""",
            "data" to """\b(mobile data|data on|data off|internet on|internet off|net on|net off|net band|data band)\b""",
            "airplane" to """\b(airplane|aeroplane|flight mode|flight)\b""",
            "location" to """\b(location|gps)\b""",
            "battery" to """\b(battery saver|power saving|battery settings)\b""",
            "display" to """\b(display settings|screen settings)\b""",
            "sound" to """\b(sound settings|ringtone settings)\b"""
        )
        for ((name, re) in panels) if (Regex(re).containsMatchIn(t)) {
            // "wifi band karo" -> wifi:off, "bluetooth chalu karo" -> bluetooth:on (Lilly helper switch daba deta hai)
            val state = when {
                offWords.containsMatchIn(t) -> ":off"
                Regex("""\b(on|chalu|chaalu|chalao|start|enable|activate|jalao|laga do|lagao|connect)\b""").containsMatchIn(t) -> ":on"
                else -> ""
            }
            return SettingCmd("panel", if (name in setOf("wifi", "bluetooth", "location", "airplane")) name + state else name)
        }
        if (Regex("""^(?:open\s+)?(?:phone\s+)?settings?(?:\s+(?:kholo|open karo|khol do))?$""").containsMatchIn(t)) return SettingCmd("panel", "settings")
        return null
    }
}
