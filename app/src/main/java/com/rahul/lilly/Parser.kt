package com.rahul.lilly

import java.util.Locale

/** Lilly kya samjhi — har command ka ek type */
sealed class Cmd {
    data class Call(val target: String) : Cmd()
    data class Torch(val on: Boolean) : Cmd()
    object Time : Cmd()
    object Date : Cmd()
    object Battery : Cmd()
    /** orMusic = true: "X chalao" — app na mile toh X ko gaana maan lo */
    data class OpenApp(val name: String, val orMusic: Boolean = false) : Cmd()
    /** app: "" (Spotify/default) ya "youtube" */
    data class PlayMusic(val query: String, val app: String = "") : Cmd()
    /** action: pause, resume, next, previous */
    data class Media(val action: String) : Cmd()
    /** action: up, down, mute, max */
    data class Volume(val action: String) : Cmd()
    /** topic: how, who, thanks, bye, hello, help, joke */
    data class Chat(val topic: String) : Cmd()
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
        """\b(lilly|lily|lili|lilli|lilie|leely|leli|lilee)\b|लिली|लीली|लिल्ली""", ic
    )
    private val leadFiller = Regex("""^[\s,.!?]*(?:(?:hey|hi|hello|ok|okay|हे|हाय)\b)?[\s,.!?]*""", ic)

    fun hasWakeWord(t: String) = wakeRegex.containsMatchIn(t)

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
        "लिली" to "lilly", "लीली" to "lilly", "हे" to "hey", "हाय" to "hi", "हेलो" to "hello", "हैलो" to "hello"
    )

    /** "ओपन यूट्यूब" -> "open youtube". Roman text jaisa hai waisa rehta hai. */
    fun roman(t: String): String {
        if (!hasDevanagari(t)) return t.trim()
        return t.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { w ->
            val core = w.trim(',', '.', '?', '!', '।').replace("़", "")
            hiWords[core] ?: if (hasDevanagari(core)) transliterate(core) else core
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
        return when {
            n == q -> 100
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
        """^(?:please\s+)?(?:open|launch|start|kholo)\s+(?:karo\s+|kar do\s+)?(.+?)(?:\s+(?:karo|kar do|kardo|kijiye|please))?$""", ic
    )
    private val openSuffix = Regex("""^(.+?)\s+(?:ko\s+)?(?:$OPEN_VERBS)(?:\s+please)?$""", ic)
    private val runSuffix = Regex("""^(.+?)\s+(?:ko\s+)?(?:$RUN_VERBS)(?:\s+please)?$""", ic)

    private fun cleanAppName(raw: String): String {
        var n = raw.trim().lowercase(Locale.ROOT)
        for (p in listOf("the ", "my ", "mera ", "meri ", "ye ", "wo ", "zara ", "jara ", "please ")) n = n.removePrefix(p)
        for (sfx in listOf(" please", " ko", " application", " app", " wala", " wali")) n = n.removeSuffix(sfx)
        return n.trim()
    }

    // ---------- Call ----------

    private const val CALL_WORDS = "call|phone|fone|fon|dial|ring"
    private const val DO_WORDS = "karo|kar do|kardo|lagao|laga do|kijiye|karna|karein|milao|mila do|please"
    private val callPatterns = listOf(
        Regex("""^(.+?)\s+(?:ko|se)\s+(?:$CALL_WORDS)(?:\s+.*)?$""", ic),
        Regex("""^(?:please\s+)?(?:$CALL_WORDS)\s+(?:karo\s+|kar do\s+|lagao\s+|to\s+)?(.+?)(?:\s+ko)?(?:\s+(?:$DO_WORDS))?$""", ic),
        Regex("""^(.+?)\s+(?:$CALL_WORDS)\s+(?:$DO_WORDS)$""", ic)
    )

    // ---------- Music ----------

    private const val SONG = "gaana|gana|gaane|gane|song|songs|music|track"
    private const val PLAY_VERBS =
        "chalao|chala do|bajao|baja do|play karo|play kar do|play kardo|lagao|laga do|sunao|suna do|play|shuru karo"
    private val nextRe = Regex(
        """^(?:play\s+)?(?:the\s+)?(?:next|skip|agla|agli)(?:\s+(?:$SONG|wala|vala|one))?(?:\s+(?:$PLAY_VERBS|karo|kar do|please))*$""", ic
    )
    private val prevRe = Regex(
        """^(?:play\s+)?(?:the\s+)?(?:previous|pichla|pichhla|pichli|pichhli|last|back)(?:\s+(?:$SONG|wala|vala|one))?(?:\s+(?:$PLAY_VERBS|karo|kar do|please))*$""", ic
    )
    private val pauseRe = Regex(
        """^(?:pause|stop|ruko|roko|rok do|ruk jao)(?:\s+(?:karo|kar do|the|it|$SONG|spotify|please))*$""" +
            """|^(?:$SONG|spotify)\s+(?:ko\s+)?(?:pause|stop|band|bandh|roko|rok do|ruko)(?:\s+(?:karo|kar do|kardo|please))*$""", ic
    )
    private val resumeRe = Regex(
        """^(?:resume|continue|play)(?:\s+(?:karo|kar do|the|it|$SONG|please))*$""" +
            """|^(?:koi\s+|kuch\s+|ek\s+)?(?:$SONG|spotify)\s+(?:$PLAY_VERBS|resume karo|chalu karo)(?:\s+please)?$""", ic
    )
    private val playPatterns = listOf(
        Regex("""^(?:please\s+)?play\s+(.+)$""", ic),
        Regex("""^(?:spotify\s+(?:par|pe|me|mein|on|se)\s+)?(.+?)\s+(?:$SONG)\s+(?:$PLAY_VERBS)(?:\s+please)?$""", ic),
        Regex("""^spotify\s+(?:par|pe|me|mein|on|se)\s+(.+?)\s+(?:$PLAY_VERBS)(?:\s+please)?$""", ic),
        Regex("""^(.+?)\s+(?:bajao|baja do|sunao|suna do|play karo|play kar do|play kardo)(?:\s+please)?$""", ic)
    )

    private val onAppPatterns = listOf(
        Regex("""^(?:open\s+)?(spotify|youtube)\s+(?:and|aur)\s+(?:play\s+)?(.+?)(?:\s+(?:$PLAY_VERBS))?$""", ic) to false,
        Regex("""^(spotify|youtube)\s+(?:par|pe|me|mein|on|se)\s+(.+?)\s+(?:$PLAY_VERBS|search karo|dhundo|dikhao|dikha do)(?:\s+please)?$""", ic) to false,
        Regex("""^(?:please\s+)?play\s+(.+?)\s+(?:on|in)\s+(spotify|youtube)$""", ic) to true,
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

    // ---------- Main: text -> Cmd ----------

    fun parse(input: String): Cmd {
        val t = roman(input).lowercase(Locale.ROOT)
            .replace(Regex("[,.!?।]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isBlank()) return Cmd.Unknown("")

        // Torch
        if (Regex("""\b(torch|flash ?light|flash)\b""").containsMatchIn(t)) {
            val off = Regex("""\b(off|band|bandh|bujha|bujhao|bujha do)\b""").containsMatchIn(t)
            return Cmd.Torch(!off)
        }

        // Volume
        if (Regex("""\bmute\b""").containsMatchIn(t)) return Cmd.Volume("mute")
        if (Regex("""\b(volume|awaaz|awaz|awaj|aawaz|aawaj|sound)\b""").containsMatchIn(t)) {
            return when {
                Regex("""\b(full|max|maximum|poori|puri)\b""").containsMatchIn(t) -> Cmd.Volume("max")
                Regex("""\b(up|badha|badhao|badha do|tez|zyada|jyada|increase|high|upar)\b""").containsMatchIn(t) -> Cmd.Volume("up")
                Regex("""\b(down|kam|dheema|dheemi|dhima|dhimi|decrease|ghata|ghatao|low|neeche|niche)\b""").containsMatchIn(t) -> Cmd.Volume("down")
                else -> Cmd.Unknown(t)
            }
        }

        // Gaane ka control
        if (nextRe.matches(t)) return Cmd.Media("next")
        if (prevRe.matches(t)) return Cmd.Media("previous")
        if (pauseRe.matches(t)) return Cmd.Media("pause")
        if (resumeRe.matches(t)) return Cmd.Media("resume")

        // "YouTube par X chalao" / "play X on Spotify"
        for ((re, queryFirst) in onAppPatterns) {
            val m = re.find(t) ?: continue
            val app = (if (queryFirst) m.groupValues[2] else m.groupValues[1]).lowercase(Locale.ROOT)
            val q = cleanSongQuery(if (queryFirst) m.groupValues[1] else m.groupValues[2])
            return Cmd.PlayMusic(q, if (app == "youtube") "youtube" else "")
        }

        // "X kholo" / "open X" — call se pehle, taaki "phone pe kholo" call na ban jaaye
        (openSuffix.find(t) ?: openPrefix.find(t))?.let { m ->
            val name = cleanAppName(m.groupValues[1])
            if (name.isNotBlank()) return Cmd.OpenApp(name)
        }

        // Call
        for (p in callPatterns) {
            val m = p.find(t) ?: continue
            val name = m.groupValues[1].trim().removePrefix("please ").trim()
            if (name.isNotBlank() && name !in setOf("karo", "kar do", "lagao", "me", "a")) return Cmd.Call(name)
        }

        // Gaana chalana
        for (p in playPatterns) {
            val m = p.find(t) ?: continue
            return Cmd.PlayMusic(cleanSongQuery(m.groupValues[1]))
        }

        if (Regex("""\b(battery|charge|charging)\b""").containsMatchIn(t)) return Cmd.Battery
        if (Regex("""\b(time|samay|baje|bje|baj gaye|baj rahe)\b""").containsMatchIn(t)) return Cmd.Time
        if (Regex("""\b(date|tarikh|tareekh|taarikh|kaun sa din|konsa din|kya din)\b""").containsMatchIn(t)) return Cmd.Date

        // "X chalao" — pehle app, na mile toh gaana
        runSuffix.find(t)?.let { m ->
            val name = cleanAppName(m.groupValues[1])
            if (name.isNotBlank()) return Cmd.OpenApp(name, orMusic = true)
        }

        // Chhoti baat-cheet
        if (Regex("""kaisi ho|kaise ho|how are you|kya haal""").containsMatchIn(t)) return Cmd.Chat("how")
        if (Regex("""kaun ho|your name|tumhara naam|aapka naam|who are you""").containsMatchIn(t)) return Cmd.Chat("who")
        if (Regex("""thank|shukriya|dhanyavad""").containsMatchIn(t)) return Cmd.Chat("thanks")
        if (Regex("""^(bye|good ?bye|tata|so jao|chup|chup raho|bas|bas karo|band karo|band kar do|kuch nahi|kuchh nahi|rehne do|cancel|nahi)$""").containsMatchIn(t)) return Cmd.Chat("bye")
        if (Regex("""^(hello|hi|hey|namaste|namaskar)$""").containsMatchIn(t)) return Cmd.Chat("hello")
        if (Regex("""kya kar sakti|kya kya kar|what can you do|\bhelp\b|madad""").containsMatchIn(t)) return Cmd.Chat("help")
        if (Regex("""\b(joke|chutkula|chutkule)\b""").containsMatchIn(t)) return Cmd.Chat("joke")

        return Cmd.Unknown(t)
    }
}
