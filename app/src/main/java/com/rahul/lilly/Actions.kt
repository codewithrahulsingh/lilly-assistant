package com.rahul.lilly

/** App ke andar dhoondo / ride: app = amazon, flipkart, myntra, zomato, swiggy, uber, ola, rapido, google, playstore, maps */
data class AppSearchCmd(val app: String, val query: String) : Cmd()

/** Camera: mode = photo / selfie / video */
data class CameraCmd(val mode: String) : Cmd()

/** Phone: screenshot / rotate_on / rotate_off / lock */
data class DeviceCmd(val what: String) : Cmd()

/** UPI: kisi ko paise (PIN aap khud daaloge) */
data class UpiCmd(val target: String, val amount: String) : Cmd()

/** "Rahul ki UPI ID rahul@okaxis hai" */
data class UpiSaveCmd(val name: String, val vpa: String) : Cmd()

/** "Ye mera ghar hai" — abhi ki jagah ko ghar/office maan lo */
data class PlaceSaveCmd(val place: String) : Cmd()

/** "Routine banao office mode jisme silent karo aur WiFi on karo" */
data class RoutineMakeCmd(val name: String, val steps: List<String>) : Cmd()

/** Jagah pahunch ke yaad dilana: place = home / office */
data class PlaceReminderCmd(val place: String, val text: String) : Cmd()

/**
 * Item 5–12 ki samajh: mood wale gaane, app me search, camera, screenshot/rotate/lock, UPI, jagah wala reminder.
 * Routine aur aadat (habit) brain me hain.
 */
object Actions {

    /** Alarm/yaad wale niyam se pehle chalao (jagah wala reminder, UPI) */
    fun early(t: String): Cmd? = placeReminder(t) ?: placeSave(t) ?: routineMake(t) ?: upi(t)

    /** UPI ID save — asli likhawat se (normalize "." hata deta hai) */
    fun upiSave(raw: String): Cmd? {
        val s = raw.lowercase().replace(Regex("""\s+at\s+the\s+rate\s+(?:of\s+)?"""), "@").replace(Regex("""\s*@\s*"""), "@")
        val m = Regex("""^(?:lilly\s+)?(.+?)\s+(?:ki|ka|ke|'s)\s+upi(?:\s+id)?\s+(?:is\s+)?([a-z0-9._\-]+@[a-z0-9.\-]+)(?:\s+(?:hai|save karo|save kar lo|yaad rakho))*\s*[.!]?$""").find(s.trim()) ?: return null
        return UpiSaveCmd(m.groupValues[1].trim(), m.groupValues[2].trim())
    }

    private fun placeSave(t: String): Cmd? {
        val m = Regex("""^(?:ye|yeh|yahi|yahan|this is|abhi main)\s+(?:mera\s+|my\s+|apna\s+)?(ghar|home|office)(?:\s+(?:hai|is|par hoon|pe hoon|me hoon))?$|^(?:save|set)\s+(?:this|this place|current location|here)\s+as\s+(?:my\s+)?(home|office)$|^(ghar|office)\s+(?:ki|ka)\s+(?:location|jagah)\s+(?:yahi|ye|yeh)?\s*(?:save|set)\s*(?:karo|kar lo|kar do)?$""").find(t) ?: return null
        val p = m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        return PlaceSaveCmd(if (p == "ghar" || p == "home") "home" else "office")
    }

    private fun routineMake(t: String): Cmd? {
        val m = Regex("""^(?:routine|shortcut)\s+(?:banao|bana do|create karo|create|add karo|set karo)\s+(.+?)\s+(?:jisme|jismein|which|that|mein|me|:|-)\s+(.+)$|^(?:make|create)\s+(?:a\s+)?routine\s+(.+?)\s+(?:that|which|to)\s+(.+)$""").find(t) ?: return null
        val name = (m.groupValues[1].ifBlank { m.groupValues[3] }).replace(Regex("""\b(?:naam ka|named|called|ka|ki)\b"""), " ").replace(Regex("""\s+"""), " ").trim()
        val body = m.groupValues[2].ifBlank { m.groupValues[4] }
        val steps = body.split(Regex("""\s*,\s*|\s+(?:aur fir|aur phir|and then|aur|and|phir|fir|then)\s+""")).map { it.trim() }.filter { it.isNotBlank() }
        if (name.isBlank() || steps.isEmpty()) return null
        return RoutineMakeCmd(name, steps)
    }

    fun parse(t: String): Cmd? = camera(t) ?: device(t) ?: appSearch(t)

    // ---------------- 8. Jagah pahunch ke yaad ----------------

    private fun placeReminder(t: String): Cmd? {
        val place = Regex("""\b(ghar|home|office|offce|kaam|work|dukaan)\b""").find(t)?.value ?: return null
        val trig = Regex("""\b(?:pahunch|pahuch|pohoch|pohonch|pahunchu|pahunchun|pahunchte|reach|reached|arrive|get)\b""").containsMatchIn(t)
        if (!trig) return null
        val text = Regex("""\b(?:yaad dilana|yaad dila dena|yaad dilao|remind me|bata dena|bolna)\b\s*(?:ki|that|to)?\s*(.+)$""").find(t)?.groupValues?.get(1)?.trim()
            ?: Regex("""^(?:remind me to|yaad dilana ki)\s+(.+?)\s+(?:when|jab)""").find(t)?.groupValues?.get(1)?.trim()
            ?: return null
        val p = if (place in setOf("ghar", "home")) "home" else "office"
        return PlaceReminderCmd(p, text.replace(Regex("""\s*(?:jab|when)\s+.*$"""), "").trim().ifBlank { text })
    }

    // ---------------- 9. UPI ----------------

    private fun upi(t: String): Cmd? {
        val rs = """(?:rupaye|rupaiye|rupay|rupees|rupee|rs|inr|₹)?"""
        Regex("""^(.+?)\s+ko\s+(\d{1,6})\s*$rs\s*(?:bhejo|bhej do|pay karo|pay kar do|de do|transfer karo|send karo|upi karo)$""").find(t)?.let {
            return UpiCmd(it.groupValues[1].trim(), it.groupValues[2])
        }
        Regex("""^(?:pay|send|transfer)\s+(?:rs\s*|₹\s*)?(\d{1,6})\s*$rs\s+to\s+(.+)$""").find(t)?.let {
            return UpiCmd(it.groupValues[2].trim(), it.groupValues[1])
        }
        Regex("""^(?:pay|send money to)\s+(.+?)\s+(?:rs\s*|₹\s*)?(\d{1,6})\s*$rs$""").find(t)?.let {
            return UpiCmd(it.groupValues[1].trim(), it.groupValues[2])
        }
        return null
    }

    // ---------------- 11. Camera ----------------

    private fun camera(t: String): Cmd? {
        if (Regex("""\b(?:selfie|selfi|front camera)\b""").containsMatchIn(t)) return CameraCmd("selfie")
        if (Regex("""\bvideo\s+(?:record|shuru|start|banao|bana do|lo)\b|\b(?:record|start|take)\s+(?:a\s+)?video\b""").containsMatchIn(t)) return CameraCmd("video")
        if (Regex("""\b(?:photo|foto|pic|picture|tasveer|tasvir|click)\s+(?:kheecho|khicho|kheencho|khinch|khich|lo|le lo|click karo|kheech do|le do)\b|\btake\s+(?:a\s+)?(?:photo|picture|pic)\b|^(?:camera\s+)?click\s+karo$""").containsMatchIn(t))
            return CameraCmd("photo")
        return null
    }

    // ---------------- 10. Screenshot / rotate / lock ----------------

    private fun device(t: String): Cmd? {
        if (Regex("""\bscreen\s?shot\b|\bss\s+(?:lo|le lo)\b""").containsMatchIn(t)) return DeviceCmd("screenshot")
        if (Regex("""\b(?:auto[\s-]?rotate|rotation|rotate|screen ghuma)\b""").containsMatchIn(t))
            return DeviceCmd(if (Regex("""\b(off|band|bandh|lock|disable|stop)\b""").containsMatchIn(t)) "rotate_off" else "rotate_on")
        if (Regex("""^(?:phone|screen|mobile)\s+(?:lock|lok)\s*(?:karo|kar do|kardo)?$|^lock\s+(?:the\s+)?(?:phone|screen)$|^screen\s+(?:band|off)\s*(?:karo|kar do)?$""").containsMatchIn(t))
            return DeviceCmd("lock")
        return null
    }

    // ---------------- 12. App ke andar search / ride ----------------

    private val apps = linkedMapOf(
        "amazon" to """amazon|amezon|amazone""",
        "flipkart" to """flipkart|flip kart""",
        "myntra" to """myntra|mintra""",
        "zomato" to """zomato|jomato""",
        "swiggy" to """swiggy|swigy""",
        "blinkit" to """blinkit|blink it""",
        "zepto" to """zepto""",
        "uber" to """uber|ubar""",
        "ola" to """ola""",
        "rapido" to """rapido""",
        "playstore" to """play\s?store|playstore""",
        "google" to """google|gugal"""
    )

    private fun appSearch(t: String): Cmd? {
        val app = apps.entries.firstOrNull { Regex("""\b(?:${it.value})\b""").containsMatchIn(t) }?.key ?: return null
        val appRe = Regex("""\b(?:${apps[app]})\b""")
        // Ride: "uber book karo airport ke liye" / "book an uber to airport" / "ola se station chalo"
        if (app in setOf("uber", "ola", "rapido")) {
            val raw = Regex("""\b(?:to|for)\s+(.+)$""").find(t)?.groupValues?.get(1)
                ?: Regex("""^(.+?)\s+(?:ke liye|tak|jana hai)\b""").find(t)?.groupValues?.get(1) ?: ""
            val dest = raw.replace(appRe, " ")
                .replace(Regex("""\b(?:book|karo|kar do|cab|ride|auto|bike|ek|a|an|mujhe|mere liye|se|par|pe|me)\b"""), " ")
                .replace(Regex("""\s+"""), " ").trim()
            return AppSearchCmd(app, dest)
        }
        val verb = Regex("""\b(?:search|dhundo|dhoondo|dhundho|dikhao|order|mangao|mangwao|kharido|khareedo|buy|find|look for|pe|par|me|mein|on|se)\b""")
        if (!verb.containsMatchIn(t)) return null
        val q = t.replace(appRe, " ")
            .replace(Regex("""\b(?:search|dhundo|dhoondo|dhundho|dikhao|order|mangao|mangwao|kharido|khareedo|buy|find|look for|karo|kar do|kardo|pe|par|me|mein|on|se|from|in|app|ek|mujhe|mere liye|liye|ke|ka|ki|for|please|kholo|open)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        if (q.isBlank()) return null // sirf "amazon kholo" = app kholo
        return AppSearchCmd(app, q)
    }

    // ---------------- 5. Mood wale gaane ----------------

    private val moods = linkedMapOf(
        """sad|dukhi|dard bhare|emotional|breakup|heartbreak""" to "sad hindi songs",
        """romantic|pyaar wale|pyar wale|love""" to "romantic hindi songs",
        """party|dance|nachne wale""" to "party dance songs hindi",
        """workout|gym|exercise""" to "workout gym songs",
        """lofi|lo fi|chill|relax|sukoon|shaant|soothing""" to "lofi chill hindi songs",
        """bhajan|bhakti|devotional|aarti""" to "bhajan",
        """old|purane|90s|retro""" to "old hindi songs 90s",
        """punjabi""" to "punjabi hits",
        """sleep|sone wale|neend""" to "sleep relaxing music"
    )

    /** "sad gaane chalao" -> "sad hindi songs"; "Kesariya jaisa gaana" -> "songs like Kesariya" */
    fun moodQuery(t: String, query: String): String? {
        Regex("""^(.+?)\s+(?:jaisa|jaise|jesa|jese|type ka|type ke|wale type)\b""").find(query.trim())?.let {
            return "songs like ${it.groupValues[1].trim()}"
        }
        Regex("""\bsongs?\s+like\s+(.+)$""").find(t)?.let { return "songs like ${it.groupValues[1].trim()}" }
        val q = query.trim().lowercase()
        if (q.split(" ").size > 3) return null
        for ((re, out) in moods) if (Regex("""^(?:koi\s+|kuch\s+)?(?:$re)(?:\s+(?:wale|vale|type|mood|ke|ka))?$""").matches(q)) return out
        return null
    }
}
