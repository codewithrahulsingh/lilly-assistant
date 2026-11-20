package com.rahul.lilly

import java.util.Locale

/** Lilly kya samjhi — har command ka ek type */
sealed class Cmd {
    data class Call(val target: String, val speaker: Boolean = false) : Cmd()
    data class Torch(val on: Boolean) : Cmd()
    object Time : Cmd()
    object Date : Cmd()
    object Battery : Cmd()
    /** orMusic = true: "X chalao" — app na mile toh X ko gaana maan lo */
    data class OpenApp(val name: String, val orMusic: Boolean = false) : Cmd()
    /** app: "" (jo pasand ho), "spotify" ya "youtube" */
    data class PlayMusic(val query: String, val app: String = "") : Cmd()
    /** action: pause, resume, next, previous */
    data class Media(val action: String) : Cmd()
    /** action: up, down, mute, max */
    data class Volume(val action: String) : Cmd()
    /** topic: how, who, thanks, bye, hello, help, joke */
    data class Chat(val topic: String) : Cmd()
    /** "When I say good night, turn off the torch" — phrase sikhana */
    data class Teach(val phrase: String, val action: String) : Cmd()
    /** "Yaad rakhna ki mera gym 6 baje hai" */
    data class Remember(val fact: String) : Cmd()
    /** "Bhool jao ki mera gym 6 baje hai" */
    data class ForgetFact(val what: String) : Cmd()
    /** "Mere baare me kya yaad hai?" */
    object Recall : Cmd()
    /** Yaad me se ek khaas baat ("meri DOB kya hai") */
    data class RecallAbout(val words: List<String>, val whose: String?) : Cmd()
    /** label = reminder ka kaam ("call mom"), repeat = "", "daily", "weekdays" */
    data class Alarm(val at: TimeParse.When, val label: String = "", val repeat: String = "") : Cmd()
    /** Samay nahi bola — poochho */
    data class AlarmAsk(val label: String = "", val timer: Boolean = false) : Cmd()
    data class Timer(val seconds: Int) : Cmd()
    object ShowAlarms : Cmd()
    object StopAlarm : Cmd()
    data class Unknown(val text: String) : Cmd()
}

/**
 * Suni hui baat ko samajhne ka poora logic. Isme Android ka kuch nahi hai,
 * isliye iske tests (ParserTest) har build par apne aap chalte hain.
 */
object Parser {

    private val ic = setOf(RegexOption.IGNORE_CASE)
    private const val DEV_START = 'ऀ'
    private const val DEV_END = 'ॿ'

    private fun hasDevanagari(s: String) = s.any { it in DEV_START..DEV_END }

    // ---------- Wake word ----------

    private val wakeRegex = Regex(
        // "leli" / "lili" hataye: Hindi ka "le li" bahut aam hai aur galti se Lilly jaag jaati thi
        """\b(lilly|lily|lilli|lilie|leely|lilee)\b|लिली|लीली|लिल्ली""", ic
    )
    private val leadFiller = Regex("""^[\s,.!?]*(?:(?:hey|hi|hello|ok|okay|हे|हाय)\b)?[\s,.!?]*""", ic)

    fun hasWakeWord(t: String) = wakeRegex.containsMatchIn(t)

    /** "Lilly" vakya ke shuru me (pehle 3 shabdon me) — beech me aaya "lily" bulawa nahi hai */
    fun startsWithWake(t: String): Boolean {
        val first = t.trim().split(Regex("\\s+")).take(3).joinToString(" ")
        return wakeRegex.containsMatchIn(first)
    }

    fun stripWakeWord(t: String): String =
        t.replace(wakeRegex, " ").replace(leadFiller, "").replace(Regex("\\s+"), " ").trim()

    // ---------- Hindi lipi -> Roman (Hinglish) ----------

    private val hiWords = mapOf(
        "ओपन" to "open", "खोलो" to "kholo", "खोल" to "khol", "खोलिए" to "kholiye", "खोलें" to "kholein",
        "दो" to "do", "करो" to "karo", "कर" to "kar", "करना" to "karna", "करें" to "karein", "कीजिए" to "kijiye",
        "को" to "ko", "से" to "se", "पर" to "par", "पे" to "pe", "की" to "ki", "का" to "ka", "के" to "ke",
        "में" to "mein", "मे" to "me", "और" to "aur", "एक" to "ek", "मुझे" to "mujhe", "कोई" to "koi",
        "कॉल" to "call", "काल" to "call", "फोन" to "phone", "लगाओ" to "lagao",
        "लगा" to "laga", "मिलाओ" to "milao", "डायल" to "dial",
        "टॉर्च" to "torch", "टोर्च" to "torch", "फ्लैशलाइट" to "flashlight", "फ्लैश" to "flash",
        "जलाओ" to "jalao", "जला" to "jala", "बंद" to "band", "बुझाओ" to "bujhao", "बुझा" to "bujha",
        "ऑन" to "on", "ऑफ" to "off", "चालू" to "chalu",
        "टाइम" to "time", "समय" to "samay", "बजे" to "baje", "कितने" to "kitne", "कितनी" to "kitni",
        "क्या" to "kya", "है" to "hai", "हैं" to "hain", "हुआ" to "hua", "तारीख" to "tarikh",
        "डेट" to "date", "आज" to "aaj", "दिन" to "din", "बैटरी" to "battery", "चार्ज" to "charge",
        "चलाओ" to "chalao", "चला" to "chala", "स्टार्ट" to "start", "लॉन्च" to "launch", "प्लीज" to "please",
        "ऐप" to "app", "एप" to "app", "एप्लीकेशन" to "app",
        "प्ले" to "play", "गाना" to "gaana", "गाने" to "gaane", "सॉन्ग" to "song", "सॉन्ग्स" to "songs",
        "म्यूजिक" to "music", "बजाओ" to "bajao", "बजा" to "baja", "सुनाओ" to "sunao", "सुना" to "suna",
        "पॉज" to "pause", "पॉज़" to "pause", "स्टॉप" to "stop", "रोको" to "roko", "रोक" to "rok", "रुको" to "ruko",
        "नेक्स्ट" to "next", "अगला" to "agla", "अगली" to "agli", "पिछला" to "pichla", "पिछली" to "pichli",
        "प्रीवियस" to "previous", "रिज्यूम" to "resume", "स्किप" to "skip",
        "वॉल्यूम" to "volume", "आवाज" to "awaaz", "साउंड" to "sound", "बढ़ाओ" to "badhao", "बढ़ा" to "badha",
        "कम" to "kam", "तेज" to "tez", "धीमा" to "dheema", "धीमी" to "dheemi", "म्यूट" to "mute",
        "फुल" to "full", "अप" to "up", "डाउन" to "down",
        "यूट्यूब" to "youtube", "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp",
        "वॉट्सएप" to "whatsapp", "व्हाट्सप्प" to "whatsapp", "इंस्टाग्राम" to "instagram", "इंस्टा" to "instagram",
        "कैमरा" to "camera", "सेटिंग" to "settings", "सेटिंग्स" to "settings", "फेसबुक" to "facebook",
        "गूगल" to "google", "क्रोम" to "chrome", "मैप" to "maps", "मैप्स" to "maps", "गैलरी" to "gallery",
        "फोटो" to "photos", "फोटोज" to "photos", "स्नैपचैट" to "snapchat", "टेलीग्राम" to "telegram",
        "स्पॉटिफाई" to "spotify", "स्पॉटिफ़ाई" to "spotify", "स्पोटिफाई" to "spotify",
        "कैलकुलेटर" to "calculator", "कैलेंडर" to "calendar", "मैसेज" to "messages",
        "मैसेजेस" to "messages", "जीमेल" to "gmail", "स्टोर" to "store", "पेटीएम" to "paytm",
        "फोनपे" to "phonepe", "घड़ी" to "clock", "क्लॉक" to "clock", "ट्विटर" to "twitter",
        "अमेजन" to "amazon", "फ्लिपकार्ट" to "flipkart", "नेटफ्लिक्स" to "netflix",
        "कैसी" to "kaisi", "कैसे" to "kaise", "हो" to "ho", "कौन" to "kaun", "तुम" to "tum",
        "तुम्हारा" to "tumhara", "नाम" to "naam", "धन्यवाद" to "dhanyavad", "शुक्रिया" to "shukriya",
        "थैंक" to "thank", "थैंक्स" to "thanks", "थैंक्यू" to "thank you", "यू" to "you", "बाय" to "bye",
        "बताओ" to "batao", "बता" to "bata", "वक्त" to "waqt", "लाइट" to "light", "बदलो" to "badlo",
        "दूसरा" to "doosra", "नहीं" to "nahi", "हाँ" to "haan", "हां" to "haan", "मेरा" to "mera", "मेरी" to "meri",
        "मेरे" to "mere", "आप" to "aap", "बात" to "baat", "कराओ" to "karao", "सर्च" to "search",
        "अलार्म" to "alarm", "टाइमर" to "timer", "मिनट" to "minute", "घंटे" to "ghante", "घंटा" to "ghanta",
        "बाद" to "baad", "सुबह" to "subah", "शाम" to "shaam", "रात" to "raat", "दोपहर" to "dopahar",
        "साढ़े" to "sadhe", "सवा" to "sava", "पौने" to "paune", "डेढ़" to "dedh", "ढाई" to "dhai",
        "दिलाना" to "dilana", "दिला" to "dila", "जगाना" to "jagana", "रोज" to "roz", "रोज़" to "roz",
        "शून्य" to "shunya", "स्पीकर" to "speaker", "याद" to "yaad", "रखना" to "rakhna", "रखो" to "rakho", "रखिए" to "rakhiye",
        "भूल" to "bhool", "जाओ" to "jao", "बारे" to "baare", "पता" to "pata", "जानती" to "jaanti",
        "लिली" to "lilly", "लीली" to "lilly", "हे" to "hey", "हाय" to "hi", "हेलो" to "hello", "हैलो" to "hello"
    )

    /** "ओपन यूट्यूब" -> "open youtube". Roman text jaisa hai waisa rehta hai. */
    fun roman(t: String): String {
        if (!hasDevanagari(t)) return fixSpelling(t.trim())
        return fixSpelling(t.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { w ->
            val core = w.trim(',', '.', '?', '!', '।').replace("़", "")
            hiWords[core] ?: if (hasDevanagari(core)) transliterate(core) else core
        })
    }

    /** Google / Hindi lipi se aaye galat spelling sahi: "vi-fi" -> WiFi, "watsap" -> WhatsApp */
    private val spellings: List<Pair<Regex, String>> = listOf(
        """\b(?:vi|wi|wai|vai|vaai|waai|why|bai)[\s\-‑]?(?:fi|fai|faai|phi|phai|fy)\b""" to "WiFi",
        """\bw\s?l\s?a\s?n\b""" to "WiFi",
        """\b(?:blue|blu|bloo|bluu)[\s\-]?(?:tooth|tuth|tut|tooth|tuuth|toot)\b""" to "Bluetooth",
        """\bhot[\s\-]?(?:spot|spat|sport)\b""" to "hotspot",
        """\b(?:whats|wats|vats|vots|whts|votts|vhats)[\s\-]?(?:app|ap|aap|aip|aep|aipp|epp)\b""" to "WhatsApp",
        """\b(?:spoti|spot)[\s\-]?(?:fy|fai|faai|phy|fi)\b""" to "Spotify",
        """\b(?:you|yu|yoo)[\s\-]?(?:tube|tub|toob|tyub|tuub)\b""" to "YouTube",
        """\binsta[\s\-]?(?:gram|graam)\b""" to "Instagram",
        """\b(?:tourch|torche|tarch|tauch)\b""" to "torch",
        """\bflash[\s\-]?light\b""" to "flashlight",
        """\b(?:volumn|valume|volyum|vollume)\b""" to "volume",
        """\b(?:alaram|alarum|allarm)\b""" to "alarm",
        """\b(?:calender|kalendar|calandar)\b""" to "calendar",
        """\b(?:mobile|mobail)\s+(?:deta|daata)\b""" to "mobile data",
        """\b(?:aeroplane|airoplane|eroplane)\s+mode\b""" to "airplane mode",
        """\b(?:lokeshan|location|lokation)\b""" to "location",
        // Hindi recognizer English shabd ko Hindi jaisa likh deta hai: "misi call" = missed call
        """\b(?:mis|misi|missi|misd|mist|misst|mised|misde|miss|missd|missed)\s*(?:call|calls|kal|kaal|kol|col|cal|kall)s?\b""" to "missed call",
        """\b(?:ri|re|ree)\s?(?:dial|dail|dayal|daayal)\b""" to "redial",
        """\b(?:call|kal|kaal)\s*(?:bek|baik|baek|back)\b""" to "call back",
        """\b(?:notifi|notify|notifik|notifikeshan|notification|notifications|notifikeshans)\w*\b""" to "notifications",
        """\b(?:brait|bright|brite)\s*(?:nes|ness|nis)\b""" to "brightness",
        """\b(?:baitri|betri|bettery|battry|baitry|batri)\b""" to "battery",
        """\b(?:mesej|massage|mesage|maseg|messej)\b""" to "message",
        """\b(?:reminder|remaindar|riminder|remainder)\b""" to "reminder",
        """\b(?:skrin\s?shot|screen\s?shot)\b""" to "screenshot",
        """\b(?:sailent|silant|sailant)\b""" to "silent",
        """\b(?:vaibret|vibret|vaibrate)\b""" to "vibrate",
        """\b(?:chek|chk|chak)\b""" to "check",
        """\b(?:notifcation|notifaction|notificasion|notificaton)s?\b""" to "notifications"
    ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }

    fun fixSpelling(t: String): String {
        var s = t
        // Pehle se sahi likha ho ("youtube") toh waisa hi rehne do
        for ((re, to) in spellings) s = re.replace(s) { m -> if (m.value.equals(to, true)) m.value else to }
        // Rahul ki apni / Lilly ki seekhi hui spellings
        for ((re, to) in custom) s = re.replace(s, to)
        return s
    }

    @Volatile private var custom: List<Pair<Regex, String>> = emptyList()

    /** Seekhi hui spellings (galat -> sahi) set karo — SpellBook bulata hai */
    fun setCustomSpellings(map: Map<String, String>) {
        custom = map.entries.sortedByDescending { it.key.length }.mapNotNull { (w, r) ->
            try { Regex("""(?<![\p{L}\p{N}])""" + Regex.escape(w).replace(" ", "\\E\\s+\\Q") + """(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE) to r } catch (e: Exception) { null }
        }
    }

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l"
    )
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऋ' to "ri", 'ऑ' to "o"
    )
    private val matras = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e",
        'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ृ' to "ri", 'ॉ' to "o"
    )
    private const val HALANT = '्'

    fun transliterate(input: String): String {
        val s = input.replace("़", "") // nukta hatao
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val next = s.getOrNull(i + 1)
            val cons = consonants[ch]
            when {
                cons != null -> {
                    out.append(cons)
                    when {
                        next != null && matras.containsKey(next) -> { out.append(matras[next]); i++ }
                        next == HALANT -> i++
                        next == null || next !in DEV_START..DEV_END -> { /* shabd ka ant: 'a' nahi */ }
                        next == 'ं' || next == 'ँ' -> out.append("a")
                        else -> out.append("a")
                    }
                }
                vowels.containsKey(ch) -> out.append(vowels[ch])
                matras.containsKey(ch) -> out.append(matras[ch])
                ch == HALANT -> {}
                ch == 'ं' || ch == 'ँ' -> out.append("n")
                ch == 'ः' -> out.append("h")
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }

    // ---------- Naam milana ----------

    fun normName(s: String): String {
        val latin = if (hasDevanagari(s)) roman(s) else s
        return latin.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Vowels hata kar sirf consonants — "Raahul" aur "Rahul" ko match karne ke liye */
    fun skeleton(s: String) =
        s.replace(Regex("[aeiouyh]"), "").replace(Regex("(.)\\1+"), "$1")

    private fun appSkeleton(s: String) =
        skeleton(s.replace("ph", "f").replace('w', 'v').replace('z', 's').replace('c', 'k').replace('q', 'k'))

    fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1, dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** Contact ka naam bole gaye naam se kitna milta hai (0 = bilkul nahi) */
    fun contactScore(query: String, name: String): Int {
        val q = normName(query)
        val n = normName(name)
        if (q.isBlank() || n.isBlank()) return 0
        val words = n.split(" ")
        val qWords = q.split(" ")
        // Google naam tod deta hai: "badiya sar" = "badiyasar"
        val qj = q.replace(" ", "")
        val nj = n.replace(" ", "")
        return when {
            n == q -> 100
            qj == nj -> 98
            qj.length >= 8 && levenshtein(qj, nj) <= 1 -> 92
            qj.length >= 6 && nj.startsWith(qj) && qWords.size >= 2 -> 86
            n.startsWith("$q ") -> 90
            words.any { it == q } -> 80
            qWords.all { qw -> words.any { it == qw } } -> 75
            n.startsWith(q) && q.length >= 3 -> 70
            n.contains(q) && q.length >= 3 -> 55
            q.length >= 4 && words.any { levenshtein(it, qWords.first()) <= 1 } -> 45
            skeleton(q).length >= 2 && skeleton(words.first()) == skeleton(qWords.first()) -> 35
            else -> 0
        }
    }

    /** App ka label bole gaye naam se kitna milta hai (0 = bilkul nahi) */
    fun appScore(spoken: String, label: String): Int {
        val lab = normName(label)
        val t = normName(spoken).replace(" ", "")
        val l = lab.replace(" ", "")
        if (t.isBlank() || l.isBlank()) return 0
        val words = lab.split(" ")
        val tol = if (t.length >= 8) 2 else 1
        return when {
            l == t -> 100
            words.any { it == t } -> 88
            t.length >= 3 && l.startsWith(t) -> 85
            t.length >= 4 && l.contains(t) -> 75
            l.length >= 4 && t.startsWith(l) -> 70
            t.length >= 4 && levenshtein(t, l) <= tol -> 68
            t.length >= 4 && words.any { it.length >= 4 && levenshtein(t, it) <= 1 } -> 62
            appSkeleton(t).length >= 3 && appSkeleton(t) == appSkeleton(l) -> 55
            appSkeleton(t).length >= 3 && words.any { appSkeleton(it) == appSkeleton(t) } -> 50
            else -> 0
        }
    }

    // ---------- Apps ----------

    private val appAliases = mapOf(
        "you tube" to "youtube", "u tube" to "youtube", "whats app" to "whatsapp", "what's app" to "whatsapp",
        "insta" to "instagram", "setting" to "settings", "map" to "maps", "google map" to "maps",
        "google maps" to "maps", "phone pe" to "phonepe", "phone pay" to "phonepe", "phonepay" to "phonepe",
        "g pay" to "gpay", "google pay" to "gpay", "google pe" to "gpay", "gpe" to "gpay", "pay tm" to "paytm", "snap chat" to "snapchat",
        "snap" to "snapchat", "fb" to "facebook", "face book" to "facebook", "play store" to "play store",
        "message" to "messages", "photo" to "photos", "spotify music" to "spotify", "x" to "x"
    )

    /** Ek naam ke liye try karne layak saare roop: "gpay" -> [gpay, google pay] */
    fun appNames(name: String): List<String> {
        val n = name.lowercase(Locale.ROOT).trim()
        val out = mutableListOf(appAliases[n] ?: n)
        if (out[0] != n) out.add(n)
        if (out[0] == "gpay") out.add("google pay")
        if (out[0] == "maps") out.add("google maps")
        if (out[0] == "messages") out.add("message")
        if (out[0] == "photos") out.add("gallery")
        if (out[0] == "gallery") out.add("photos")
        if (out[0] == "clock") out.add("alarm")
        return out.distinct()
    }

    private const val OPEN_VERBS =
        "kholo|khol do|kholdo|kholiye|kholein|kholna|open karo|open kar do|open kardo|open karna|open"
    private const val RUN_VERBS = "chalao|chala do|chalu karo|start karo|shuru karo|launch karo"
    private val openPrefix = Regex(
        """^(?:open up|open|launch|start|run|go to|take me to|bring up|kholo)\s+(?:karo\s+|kar do\s+)?(.+?)(?:\s+(?:karo|kar do|kardo|kijiye))?$""", ic
    )
    private val openSuffix = Regex("""^(.+?)\s+(?:ko\s+)?(?:$OPEN_VERBS)(?:\s+please)?$""", ic)
    private val runSuffix = Regex("""^(.+?)\s+(?:ko\s+)?(?:$RUN_VERBS)(?:\s+please)?$""", ic)

    private fun cleanAppName(raw: String): String {
        var n = raw.trim().lowercase(Locale.ROOT)
        for (p in listOf("the ", "my ", "mera ", "meri ", "ye ", "wo ", "zara ", "jara ", "please ", "up ")) n = n.removePrefix(p)
        for (sfx in listOf(" please", " ko", " application", " app", " wala", " wali")) n = n.removeSuffix(sfx)
        return n.trim()
    }

    // ---------- Call ----------

    private const val CALL_WORDS = "call|phone|fone|fon|dial|ring"
    private const val DO_WORDS = "karo|kar do|kardo|lagao|laga do|kijiye|karna|karein|milao|mila do|please"
    private val callPatterns = listOf(
        Regex("""^(.+?)\s+(?:ko|se)\s+(?:$CALL_WORDS)(?:\s+.*)?$""", ic),
        Regex("""^(\+?\d{5,})\s+(?:pe|par|per|on)\s+(?:$CALL_WORDS)(?:\s+.*)?$""", ic),
        Regex("""^(?:please\s+)?(?:$CALL_WORDS)\s+(?:karo\s+|kar do\s+|lagao\s+|to\s+)?(.+?)(?:\s+ko)?(?:\s+(?:$DO_WORDS))?$""", ic),
        Regex("""^(.+?)\s+(?:$CALL_WORDS)\s+(?:$DO_WORDS)$""", ic),
        Regex("""^(?:make|place)\s+a\s+(?:phone\s+)?call\s+to\s+(.+)$""", ic),
        Regex("""^give\s+(.+?)\s+a\s+(?:call|ring)$""", ic),
        Regex("""^(?:connect me (?:to|with)|get me)\s+(.+)$""", ic),
        Regex("""^(.+?)\s+se\s+(?:meri\s+)?baat\s+(?:karao|karwao|karva do|kara do|karwa do|karni hai|karna hai)$""", ic)
    )

    private fun cleanCallName(raw: String): String {
        var n = raw.trim()
        for (p in listOf("please ", "up ", "to ", "my ", "mere ", "meri ", "mera ", "apne ", "apni ")) n = n.removePrefix(p)
        for (sfx in listOf(" ko", " on speaker", " ji ko")) n = n.removeSuffix(sfx)
        return n.trim()
    }

    // ---------- Music ----------

    private const val SONG = "gaana|gana|gaane|gane|song|songs|music|track"
    private const val PLAY_VERBS =
        "chalao|chala do|bajao|baja do|play karo|play kar do|play kardo|lagao|laga do|sunao|suna do|play|shuru karo"
    private val nextRe = Regex(
        """^(?:play\s+)?(?:the\s+)?(?:next|skip|agla|agli|doosra|dusra)(?:\s+(?:this|the|to the next))?(?:\s+(?:$SONG|wala|vala|one))?(?:\s+(?:$PLAY_VERBS|karo|kar do|please))*$""" +
            """|^(?:change|switch)\s+(?:the\s+|this\s+)?(?:$SONG)$|^(?:$SONG)\s+(?:badlo|badal do|change karo|change kar do)$""", ic
    )
    private val prevRe = Regex(
        """^(?:play\s+)?(?:the\s+)?(?:previous|pichla|pichhla|pichli|pichhli|last|back)(?:\s+(?:$SONG|wala|vala|one))?(?:\s+(?:$PLAY_VERBS|karo|kar do|please))*$""", ic
    )
    private val pauseRe = Regex(
        """^(?:pause|stop|ruko|roko|rok do|ruk jao)(?:\s+(?:karo|kar do|the|it|this|playing|$SONG|spotify|please))*$""" +
            """|^(?:$SONG|spotify)\s+(?:ko\s+)?(?:pause|stop|band|bandh|roko|rok do|ruko)(?:\s+(?:karo|kar do|kardo|please))*$""", ic
    )
    private val resumeRe = Regex(
        """^(?:resume|continue|play)(?:\s+(?:karo|kar do|the|it|$SONG|please))*$""" +
            """|^(?:koi\s+|kuch\s+|ek\s+)?(?:$SONG|spotify)\s+(?:$PLAY_VERBS|resume karo|chalu karo)(?:\s+please)?$""", ic
    )
    private val playPatterns = listOf(
        Regex("""^(?:play|put on|listen to)\s+(.+)$""", ic),
        Regex("""^(.+?)\s+(?:sunna hai|sunna chahta hoon|sunna chahti hoon)$""", ic),
        Regex("""^(?:spotify\s+(?:par|pe|me|mein|on|se)\s+)?(.+?)\s+(?:$SONG)\s+(?:$PLAY_VERBS)(?:\s+please)?$""", ic),
        Regex("""^spotify\s+(?:par|pe|me|mein|on|se)\s+(.+?)\s+(?:$PLAY_VERBS)(?:\s+please)?$""", ic),
        Regex("""^(.+?)\s+(?:bajao|baja do|sunao|suna do|play karo|play kar do|play kardo)(?:\s+please)?$""", ic)
    )

    private val onAppPatterns = listOf(
        Regex("""^(?:open\s+)?(spotify|youtube)\s+(?:and|aur)\s+(?:play\s+)?(.+?)(?:\s+(?:$PLAY_VERBS))?$""", ic) to false,
        Regex("""^(spotify|youtube)\s+(?:par|pe|me|mein|on|se)\s+(.+?)\s+(?:$PLAY_VERBS|search karo|dhundo|dikhao|dikha do|dekhna hai|dekhni hai|dekhao|lagao)(?:\s+please)?$""", ic) to false,
        Regex("""^(?:play|search for|search|find|look up)\s+(.+?)\s+(?:on|in)\s+(spotify|youtube)$""", ic) to true,
        Regex("""^(.+?)\s+(spotify|youtube)\s+(?:par|pe|me|mein)\s+(?:$PLAY_VERBS)(?:\s+please)?$""", ic) to true
    )

    private fun cleanSongQuery(raw: String): String {
        var q = raw.trim().lowercase(Locale.ROOT)
        q = q.replace(Regex("""\s+(?:on|in|par|pe|me|mein|se)\s+spotify$"""), "")
        q = q.replace(Regex("""^spotify\s+(?:par|pe|me|mein|on|se)\s+"""), "")
        for (p in listOf("mujhe ", "ek ", "koi ", "the song ", "song ", "gaana ", "gana ")) q = q.removePrefix(p)
        q = q.replace(Regex("""\s+(?:$SONG)$"""), "")
        for (sfx in listOf(" ka", " ke", " ki", " wala", " wale", " wali", " ko")) q = q.removeSuffix(sfx)
        q = q.trim()
        return if (q in setOf("koi", "kuch", "kuchh", "something", "some", "any", "achha", "accha", "it")) "" else q
    }

    // ---------- Bhasha: English ya Hindi/Hinglish ----------

    /** Ye shabd sirf Hindi/Hinglish me aate hain; inme se ek bhi mile toh vakya Hindi maana jaata hai */
    private val hindiMarkers = setOf(
        "karo", "kar", "kardo", "karna", "karni", "kijiye", "karein", "karu", "karun", "karao",
        "kholo", "khol", "kholna", "kholiye", "chalao", "chala", "chalu", "bajao", "baja", "lagao", "laga",
        "sunao", "suna", "sunna", "jalao", "jala", "bujhao", "bujha", "band", "bandh", "batao", "bata",
        "badhao", "badha", "ghatao", "roko", "ruko", "rok", "badlo", "milao",
        "hai", "hain", "hoon", "ho", "tha", "thi", "hua", "hui", "gaya", "gayi", "raha", "rahi", "rahe",
        "kya", "kaun", "kaise", "kaisi", "kaisa", "kitna", "kitni", "kitne", "kab", "kahan", "kyun", "kyu",
        "ko", "ka", "ki", "ke", "se", "par", "pe", "mein", "aur",
        "mujhe", "mera", "meri", "mere", "tum", "aap", "tumhara", "aapka", "apna", "apni",
        "nahi", "nahin", "haan", "theek", "thik", "accha", "achha", "abhi", "aaj", "kal", "zara", "jara",
        "gaana", "gana", "gaane", "gane", "awaaz", "awaz", "kam", "tez", "dheema", "dheemi", "wala", "wali",
        "baje", "samay", "waqt", "tarikh", "tareekh", "din", "shukriya", "dhanyavad", "namaste", "namaskar",
        "kuch", "kuchh", "koi", "ek", "bolo", "dena", "dijiye", "chahiye", "wo", "woh", "ye", "yeh",
        "pichla", "pichli", "agla", "agli", "doosra", "dusra", "baat", "chutkula", "madad", "poori", "puri"
    )

    /**
     * true = user ne English me bola. Google aksar English ko bhi Hindi lipi me likh deta hai
     * ("ओपन यूट्यूब"), isliye lipi nahi, shabd dekhe jaate hain.
     */
    fun isEnglish(input: String): Boolean {
        val words = roman(stripWakeWord(input)).lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9' ]"), " ").split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        return words.none { it in hindiMarkers }
    }

    // ---------- Sikhana ----------

    private val teachHi = Regex(
        """^(?:jab|jab bhi)\s+(?:main|mai|mein|me|hum)\s+(.+?)\s+(?:bolu|bolun|boloon|bolu na|bolta hoon|bolti hoon|bolunga|kahu|kahun|kahoon)""" +
            """(?:\s+(?:to|toh|tab|tab tum|to tum|toh tum))?\s+(.+)$"""
    )
    private val teachEn = Regex("""^(?:when|whenever|if)\s+i\s+(?:say|tell you)\s+(.+)$""")
    private val actionStarters = setOf(
        "call", "phone", "dial", "ring", "open", "launch", "start", "play", "pause", "stop", "resume",
        "turn", "switch", "skip", "next", "previous", "mute", "unmute", "volume", "torch", "flashlight",
        "what", "what's", "tell", "show", "increase", "decrease", "lower", "raise", "go", "put", "listen", "search", "battery"
    )

    private fun isAction(a: String): Boolean {
        val c = parse(a)
        return c !is Cmd.Unknown && c !is Cmd.Teach && c !is Cmd.Chat
    }

    private fun teachCmd(t: String): Cmd.Teach? {
        teachHi.find(t)?.let { m ->
            val phrase = m.groupValues[1].trim()
            val action = m.groupValues[2].trim()
            val stripped = action.replace(Regex("""\s+(?:karna|kar dena|kardena|karo|kar do)$"""), "").trim()
            val best = listOf(action, stripped).firstOrNull { it.isNotBlank() && isAction(it) } ?: action
            if (phrase.isNotBlank()) return Cmd.Teach(phrase, best)
        }
        teachEn.find(t)?.let { m ->
            val w = m.groupValues[1].split(" ").filter { it.isNotBlank() }
            var fallback: Cmd.Teach? = null
            for (k in 1 until w.size) {
                var rest = w.subList(k, w.size)
                if (rest.first() in setOf("then", "to", "you", "please", "and")) {
                    rest = rest.drop(1).let { r -> if (r.firstOrNull() == "should") r.drop(1) else r }
                }
                if (rest.isEmpty()) continue
                val action = rest.joinToString(" ")
                if (!isAction(action)) continue
                val teach = Cmd.Teach(w.subList(0, k).joinToString(" "), action)
                // Pehla aisa toota jaha command kisi kaam wale shabd se shuru ho ("turn", "call", "play"…)
                if (rest.first() in actionStarters || w[k] == "then") return teach
                if (fallback == null) fallback = teach
            }
            if (fallback != null) return fallback
        }
        return null
    }

    // ---------- Alarm / timer / reminder ----------

    private val alarmWords = Regex("""\b(alarm|alaram|alarum|alarms|jagana|jaga dena|jaga do|utha dena|utha do|uthana|wake me)\b""")
    private val timerWords = Regex("""\b(timer|countdown)\b""")
    private val remindWords = Regex("""\b(remind|reminder|yaad dila\w*|yaad karana|yaad karwana)\b""")

    private fun reminderLabel(t: String): String {
        Regex("""^remind me\s+(?:at|in|after|by)\s+.+?\s+(?:to|about|that)\s+(.+)$""").find(t)?.let { return it.groupValues[1].trim() }
        Regex("""^(?:set a\s+)?remind(?:er)?\s+(?:me\s+)?(?:to|about|that|for)\s+(.+?)(?:\s+(?:at|in|after|by|tomorrow|today|tonight|every)\b.*)?$""").find(t)?.let {
            return it.groupValues[1].trim()
        }
        Regex("""\byaad\s+(?:dila\w*|karana|karwana)\s+(?:ki|ke|that)\s+(.+)$""").find(t)?.let { return it.groupValues[1].trim() }
        Regex("""^(?:mujhe\s+)?(.+?)\s+(?:ke liye|ki liye|ka reminder|ki yaad)\s""").find(t)?.let { return it.groupValues[1].trim() }
        return ""
    }

    private fun alarmCmd(t: String): Cmd? {
        val isTimer = timerWords.containsMatchIn(t)
        val isAlarm = alarmWords.containsMatchIn(t)
        val isRemind = remindWords.containsMatchIn(t)
        if (!isTimer && !isAlarm && !isRemind) return null
        val time = TimeParse.parse(t)
        if ((isAlarm || isTimer) && time == null &&
            Regex("""\b(stop|band|bandh|dismiss|cancel|hatao|hata do|off|snooze|delete|remove|chup)\b""").containsMatchIn(t)
        ) return Cmd.StopAlarm
        if (isAlarm && time == null &&
            Regex("""\b(show|list|dikhao|dikha do|kitne|which|what|kaun se|kaunse|check|my alarms)\b""").containsMatchIn(t)
        ) return Cmd.ShowAlarms
        if (isTimer && !isAlarm) {
            val sec = TimeParse.durationSeconds(t) ?: return Cmd.AlarmAsk(timer = true)
            return Cmd.Timer(sec)
        }
        val label = if (isRemind) reminderLabel(t) else ""
        // "10 minute ka alarm" = 10 minute baad
        val w = time ?: TimeParse.durationSeconds(t)?.let { TimeParse.When(inMinutes = maxOf(1, it / 60)) }
            ?: return Cmd.AlarmAsk(label)
        // "Wake me up at 5:30" / "5 baje jaga dena" — jagana matlab subah
        val waking = Regex("""\b(wake me|jagana|jaga dena|jaga do|utha dena|utha do|uthana)\b""").containsMatchIn(t)
        if (waking && w.inMinutes < 0 && w.period.isEmpty() && w.hour in 1..11) return Cmd.Alarm(w.copy(period = "am"), label, TimeParse.repeat(t))
        return Cmd.Alarm(w, label, TimeParse.repeat(t))
    }

    // ---------- Speaker ----------

    private fun stripSpeaker(t: String): String = t
        .replace(Regex("""\b(?:on|with|in|using)\s+(?:the\s+)?speaker\s*(?:phone|mode)?\b"""), " ")
        .replace(Regex("""\b(?:and\s+)?(?:put|turn)\s+(?:it\s+)?on\s+speaker\b"""), " ")
        .replace(Regex("""\bspeaker\s*(?:phone|mode)?\s+(?:pe|par|per|me|mein|on)(?:\s+(?:karke|kar ke|rakh ke|rakhkar|daal ke|dalke|daalkar))?\b"""), " ")
        .replace(Regex("""\bspeaker\s+(?:on|chalu)\s+(?:karke|kar ke)\b"""), " ")
        .replace(Regex("""\bspeaker\s*(?:phone)?\s*$"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    // ---------- Yaaddasht ----------

    private val rememberPatterns = listOf(
        Regex("""^(?:remember|don'?t forget)\s+(?:that\s+)?(.{3,})$"""),
        Regex("""^(?:yaad\s+(?:rakhna|rakho|rakhiye|rakh lo|rakh lena|kar lo))\s+(?:ki\s+|that\s+)?(.{3,})$"""),
        Regex("""^(.{3,}?)\s+(?:ye\s+|yeh\s+|is baat ko\s+)?yaad\s+(?:rakhna|rakho|rakhiye|rakh lo|rakh lena)$"""),
        Regex("""^note\s+(?:karo|kar lo|kar lena|kijiye|down|this)\s+(?:ki\s+|that\s+)?(.{3,})$""")
    )
    private val forgetPatterns = listOf(
        Regex("""^forget\s+(?:that\s+|about\s+)?(.{3,})$"""),
        Regex("""^(?:bhool|bhul)\s+(?:jao|jaao|jaiye|jana)\s+(?:ki\s+)?(.{3,})$"""),
        Regex("""^(.{3,}?)\s+(?:wali baat\s+)?(?:bhool|bhul)\s+(?:jao|jaao|jaiye|jana)$""")
    )
    private val recallRe = Regex(
        """what do you (?:remember|know) about me|what have you remembered|""" +
            """mere baare (?:me|mein) (?:kya|kya kya) (?:yaad hai|jaanti ho|janti ho|pata hai)|tumhe (?:kya|kya kya) yaad hai"""
    )
    private val notAFact = setOf("it", "this", "that", "everything", "all", "sab", "sab kuch", "kuch nahi", "me", "mujhe")

    private fun memoryCmd(t: String): Cmd? {
        if (recallRe.containsMatchIn(t)) return Cmd.Recall
        for (re in rememberPatterns) {
            val f = re.find(t)?.groupValues?.get(1)?.trim() ?: continue
            if (f !in notAFact) return Cmd.Remember(f)
        }
        for (re in forgetPatterns) {
            val f = re.find(t)?.groupValues?.get(1)?.trim() ?: continue
            if (f !in notAFact) return Cmd.ForgetFact(f)
        }
        return null
    }

    // ---------- Main: text -> Cmd ----------

    /**
     * Normalize kiye text ka hissa asli bole gaye roop me: "main 10 minute mein aa raha hoon"
     * -> jaisa bola tha (Hindi lipi ho toh Hindi lipi me, capital letters samet).
     */
    fun originalSpan(original: String, extracted: String): String {
        val orig = original.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        fun clean(x: String) = roman(x).lowercase(Locale.ROOT).replace('’', '\'').replace(Regex("[^a-z0-9']"), "")
        val norm = orig.map { clean(it) }
        val ext = extracted.split(" ").filter { it.isNotBlank() }.map { it.replace(Regex("[^a-z0-9']"), "") }
        if (ext.isEmpty() || ext.size > orig.size) return extracted
        for (i in 0..orig.size - ext.size) {
            if (ext.indices.all { norm[i + it] == ext[it] }) {
                return orig.subList(i, i + ext.size).joinToString(" ").trim(',', ';', '।')
            }
        }
        return extracted
    }

    // ---------- Bole gaye number ----------

    private val digitWords = mapOf(
        "zero" to "0", "shunya" to "0", "sunya" to "0", "shuny" to "0", "sunny" to "0", "shunye" to "0", "shoonya" to "0", "sifar" to "0",
        "one" to "1", "ek" to "1", "two" to "2", "do" to "2", "three" to "3", "teen" to "3", "tin" to "3",
        "four" to "4", "char" to "4", "chaar" to "4", "five" to "5", "paanch" to "5", "panch" to "5",
        "six" to "6", "chhe" to "6", "che" to "6", "chah" to "6", "chhah" to "6", "seven" to "7", "saat" to "7", "sat" to "7",
        "eight" to "8", "aath" to "8", "ath" to "8", "nine" to "9", "nau" to "9"
    )
    private val repeatWords = mapOf("double" to 2, "dabal" to 2, "dubal" to 2, "triple" to 3, "tripal" to 3, "tipal" to 3)

    /**
     * Phone number bolke: "nine eight seven six five four three two one zero" / "नौ आठ सात…" /
     * "double five" / "98765 43210" -> "9876543210". Sirf lambi ginti (5+ ank) badalti hai,
     * taaki "do" (karo) / "ek" jaise aam shabd na chhede.
     */
    fun spokenDigits(t: String): String {
        val w = t.split(" ")
        val out = ArrayList<String>()
        var i = 0
        while (i < w.size) {
            // Yahan se ginti ka silsila kitna lamba hai?
            var j = i
            val digits = StringBuilder()
            var tokens = 0
            var words = 0
            while (j < w.size) {
                val tok = w[j]
                val rep = repeatWords[tok]
                if (rep != null && j + 1 < w.size) {
                    val d = digitWords[w[j + 1]] ?: w[j + 1].takeIf { it.length == 1 && it[0].isDigit() }
                    if (d != null) { repeat(rep) { digits.append(d) }; tokens += 2; words++; j += 2; continue }
                }
                val d = digitWords[tok] ?: tok.takeIf { it.isNotEmpty() && it.length <= 6 && it.all { c -> c.isDigit() } }
                if (d == null) break
                if (tok in digitWords) words++
                digits.append(d)
                tokens++
                j++
            }
            if (tokens >= 2 && digits.length >= 5 && (words >= 4 || words == 0 || digits.length >= 8)) {
                out.add(digits.toString())
                i = j
            } else {
                out.add(w[i])
                i++
            }
        }
        return out.joinToString(" ")
    }

    private val politeLead = Regex(
        """^(?:(?:please|pls|plz|kindly|ok|okay|can you|could you|would you|will you|can u|i want you to|i want to|""" +
            """i would like to|i'd like to|i need you to|i need to|go ahead and|kya tum|kya aap|zara|jara)\s+)+"""
    )
    private val politeTail = Regex("""(?:\s+(?:please|pls|plz|for me|right now|zara|jara|na))+$""")

    /** Chhote akshar, viraam chinh hatao, "please / can you / zara" jaise shabd hatao */
    fun normalize(input: String): String {
        val t = spokenDigits(
            roman(input).lowercase(Locale.ROOT).replace('’', '\'')
                .replace(Regex("[,.!?।]"), " ").replace(Regex("\\s+"), " ").trim()
        )
        val core = t.replace(politeLead, "").replace(politeTail, "").trim()
        return core.ifBlank { t }
    }

    fun parse(input: String): Cmd {
        val t = normalize(input)
        if (t.isBlank()) return Cmd.Unknown("")
        Actions.upiSave(roman(input))?.let { return it }
        // Janamdin / DOB — "date" shabd ki wajah se aaj ki tareekh na bata de
        if (!Regex("""\b(yaad|remember|note|bhool|forget)\b""").containsMatchIn(t)) Smart.personal(t)?.let { return it }
        return Smart.refine(t, parseRules(input, t))
    }

    private fun parseRules(input: String, t: String): Cmd {

        // Sikhana: "jab main boss bolun to Rohit ko call karna" / "when I say good night turn off the torch"
        teachCmd(t)?.let { return it }

        Extras.note(t)?.let { return it }
        Extras.sleep(t)?.let { return it }
        Actions.early(t)?.let { return it }

        // Yaaddasht: "yaad rakhna ki…" / "remember that…" / "bhool jao ki…" / "mere baare me kya yaad hai"
        memoryCmd(t)?.let { return it }

        // Alarm / timer / reminder
        alarmCmd(t)?.let { return it }

        // Call log, list/notes, calendar
        Extras.parse(t)?.let { return it }
        Actions.parse(t)?.let { return it }

        // WhatsApp, rasta, notifications, good morning, mausam, phone settings
        MoreCommands.parse(t)?.let { return it }

        // "Rohit ko speaker pe call karo" / "call Rohit on speaker"
        if (Regex("""\bspeaker""").containsMatchIn(t)) {
            val stripped = stripSpeaker(t)
            if (stripped != t && stripped.isNotBlank()) {
                val c = parse(stripped)
                if (c is Cmd.Call) return c.copy(speaker = true)
            }
        }

        // Torch
        if (Regex("""\b(torch|flash ?light|flash)\b|\b(turn|switch) (on|off) (the )?lights?\b|\blights? (on|off|jalao|jala do|bujhao|bujha do|band)\b""").containsMatchIn(t)) {
            val off = Regex("""\b(off|band|bandh|bujha|bujhao|bujha do)\b""").containsMatchIn(t)
            return Cmd.Torch(!off)
        }

        // Volume
        if (Regex("""\b(unmute|louder|turn it up)\b""").containsMatchIn(t)) return Cmd.Volume("up")
        if (Regex("""\b(quieter|softer|turn it down)\b""").containsMatchIn(t)) return Cmd.Volume("down")
        if (Regex("""\b(mute|silent)\b""").containsMatchIn(t)) return Cmd.Volume("mute")
        if (Regex("""\b(volume|awaaz|awaz|awaj|aawaz|aawaj|sound)\b""").containsMatchIn(t)) {
            return when {
                Regex("""\b(full|max|maximum|poori|puri)\b""").containsMatchIn(t) -> Cmd.Volume("max")
                Regex("""\b(up|badha|badhao|badha do|tez|zyada|jyada|increase|raise|higher|more|high|upar)\b""").containsMatchIn(t) -> Cmd.Volume("up")
                Regex("""\b(down|kam|dheema|dheemi|dhima|dhimi|decrease|reduce|lower|less|ghata|ghatao|low|neeche|niche)\b""").containsMatchIn(t) -> Cmd.Volume("down")
                else -> Cmd.Unknown(t)
            }
        }

        // "ek chutkula sunao" gaana na ban jaaye, isliye music se pehle
        if (Regex("""\b(joke|jokes|chutkula|chutkule|make me laugh|hasao|hansao)\b""").containsMatchIn(t)) return Cmd.Chat("joke")

        // Gaane ka control
        // Aur tareeke: "gaana band karo", "music roko", "aage wala gaana", "gaana wapas chalao"
        if (Regex("""^(?:$SONG|spotify)\s+(?:band|bandh|off|rok|roko|rok do)\s*(?:karo|kar do|kardo|do)?$|^(?:stop|pause)\s+(?:the\s+)?(?:music|song|songs|$SONG)$|^(?:chup|chup karo|bas karo|bas)$""", RegexOption.IGNORE_CASE).matches(t)) return Cmd.Media("pause")
        if (Regex("""^(?:aage|agle|next)\s+(?:wala|vala)\s+(?:$SONG)(?:\s+(?:chalao|lagao|bajao|karo))?$|^skip(?:\s+(?:this|it|karo|kar do))*$|^(?:ye|yeh|is)\s+(?:$SONG)\s+(?:skip|badlo|badal do|change)\s*(?:karo|kar do)?$""", RegexOption.IGNORE_CASE).matches(t)) return Cmd.Media("next")
        if (Regex("""^(?:peeche|pichhe|piche)\s+(?:wala|vala)\s+(?:$SONG)(?:\s+(?:chalao|lagao|bajao|karo))?$""", RegexOption.IGNORE_CASE).matches(t)) return Cmd.Media("previous")
        if (Regex("""^(?:$SONG|spotify)\s+(?:wapas|vapas|phir|fir)\s+(?:chalao|chalu karo|lagao|bajao|start karo)$|^(?:unpause|resume (?:the )?(?:music|song))$""", RegexOption.IGNORE_CASE).matches(t)) return Cmd.Media("resume")
        if (nextRe.matches(t)) return Cmd.Media("next")
        if (prevRe.matches(t)) return Cmd.Media("previous")
        if (pauseRe.matches(t)) return Cmd.Media("pause")
        if (resumeRe.matches(t)) return Cmd.Media("resume")

        // "YouTube par X chalao" / "play X on Spotify"
        for ((re, queryFirst) in onAppPatterns) {
            val m = re.find(t) ?: continue
            val app = (if (queryFirst) m.groupValues[2] else m.groupValues[1]).lowercase(Locale.ROOT)
            val q = cleanSongQuery(if (queryFirst) m.groupValues[1] else m.groupValues[2])
            return Cmd.PlayMusic(q, if (app == "youtube") "youtube" else "spotify")
        }

        // "X kholo" / "open X" — call se pehle, taaki "phone pe kholo" call na ban jaaye
        (openSuffix.find(t) ?: openPrefix.find(t))?.let { m ->
            val name = cleanAppName(m.groupValues[1])
            if (name.isNotBlank()) return Cmd.OpenApp(name)
        }

        // Call
        for (p in callPatterns) {
            val m = p.find(t) ?: continue
            val name = cleanCallName(m.groupValues[1])
            if (name.isNotBlank() && name !in setOf("karo", "kar do", "lagao", "me", "a")) return Cmd.Call(name)
        }

        // Gaana chalana
        for (p in playPatterns) {
            val m = p.find(t) ?: continue
            val app = if (Regex("""\bspotify\b""").containsMatchIn(t)) "spotify" else ""
            return Cmd.PlayMusic(cleanSongQuery(m.groupValues[1]), app)
        }

        if (Regex("""\b(battery|charge|charging)\b""").containsMatchIn(t)) return Cmd.Battery
        if (Regex("""\b(time|samay|waqt|vakt|baje|bje|baj gaye|baj rahe)\b""").containsMatchIn(t)) return Cmd.Time
        if (Regex("""\b(date|tarikh|tareekh|taarikh|kaun sa din|konsa din|kya din|what day|which day|day is it|day today|kaun sa vaar)\b""").containsMatchIn(t)) return Cmd.Date

        // "X chalao" — pehle app, na mile toh gaana
        runSuffix.find(t)?.let { m ->
            val name = cleanAppName(m.groupValues[1])
            if (name.isNotBlank()) return Cmd.OpenApp(name, orMusic = true)
        }

        // Chhoti baat-cheet
        if (Regex("""kaisi ho|kaise ho|how are you|how are u|how r u|how're you|how do you do|what'?s up|kya haal|sab theek""").containsMatchIn(t)) return Cmd.Chat("how")
        if (Regex("""kaun ho|your name|tumhara naam|aapka naam|who are you|who r u""").containsMatchIn(t)) return Cmd.Chat("who")
        if (Regex("""thank|shukriya|dhanyavad""").containsMatchIn(t)) return Cmd.Chat("thanks")
        if (Regex("""^(bye|good ?bye|tata|so jao|chup|chup raho|bas|bas karo|band karo|band kar do|kuch nahi|kuchh nahi|rehne do|cancel|nahi|no|nothing|never ?mind|that'?s all|that is all|leave it|go to sleep|be quiet|chhodo|chodo|jane do)$""").containsMatchIn(t)) return Cmd.Chat("bye")
        if (Regex("""^(hello|hi+|hey|hello there|namaste|namaskar|good (morning|afternoon|evening|night)|ram ram)$""").containsMatchIn(t)) return Cmd.Chat("hello")
        if (Regex("""kya kar sakti|kya kya kar|what can you do|\bhelp\b|madad""").containsMatchIn(t)) return Cmd.Chat("help")

        return Cmd.Unknown(t)
    }
}
