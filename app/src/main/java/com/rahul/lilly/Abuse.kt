package com.rahul.lilly

/**
 * "Gaali mode" (sirf Rahul ON kare tab): koi gaali de toh Lilly bhi gaali me hi jawab deti hai.
 * Sab phone ke andar — AI ke paas nahi jaata (AI aise jawab nahi deti).
 * OFF ho toh gaali par shaant jawab (phir bhi AI ke paas nahi).
 */
object Abuse {

    // Google har baar alag spelling likhta hai ("bahan chod", "behenchod", "bhen chod"…) — isliye lachila pattern
    private val words = Regex(
        listOf(
            """bc|mc|bsdk|bkl|mkc|bhosdi ?wale|bisi|bee ?see|emasi|em ?si""",
            """teri ma+n? ki\w*|ma+n? ki (?:ankh|aankh|chut|choot)|ma+n? ka bhosd\w*|teri b+h?[aeiu]*h?[aeiu]*n+[aeiu]? ki\w*""",
            """(?:abe|oye|chal|tu|are|arre|ae) sale|sale (?:tu|kutte|kamine|harami|chor)""",
            """b+h?[aeiu]*h?[aeiu]*n+[aeiu]?[\s\-]?(?:k[eiy][\s\-]?)?ch?o+d\w*""",       // bahanchod, behen chod, bhen ke chod…
            """b+h?[aeiu]*h?[aeiu]*n+[\s\-]?k[eiy][\s\-]?(?:l[ao]+d|l[ao]+[vw]d)\w*""", // behen ke lode
            """m+[aeiou]+d+[aeiou]*r+[aeiou]?[\s\-]?ch?o+d\w*""",                         // madarchod, maadar chod
            """m[aeiou]+ ?k[eiy] ?(?:ch?o+d|l[ao]+d|l[ao]+[vw]d)\w*""",            // maa ke…
            """ch[uo]+t[iy]+[ay]?[aeo]?\w*|ch[uo]+t\b""",                          // chutiya, chutiye, chutiyapa
            """bh?o+s[aeiou]*d[aeiou]*\w*|bh?o+s[aeiou]*r[aeiou]*\w*""",          // bhosdike, bhosadi, bhosda
            """g+a+n+d+u+\w*|gaand\w*""",
            """l[ao]+u?d[aeu]\w*|l[ao]+[vw]d[aeu]\w*|lund\w*|jh[aeiou]+t[uo]+\w*""",
            """har+a+m+i+|har+a+m+kh?or\w*|kam+i+n[aeiy]+\w*|saa?l[ae]+ (?:kutt|kamin|harami)\w*|saale|saala""",
            """kutt[ae] ?ki ?aulaa?d|kutiya|ullu ?k[ae] ?patth?[ae]|bewa?kooo?f|pagal hai kya|pagal ho kya|tu pagal""",
            """fuck\w*|motherfuck\w*|bitch\w*|bastard|asshole|dumbass|idiot|stupid|moron|shut up|useless"""
        ).joinToString("|", prefix = """\b(?:""", postfix = """)\b""")
    )

    /** Bole hue me gaali hai? */
    fun has(t: String) = words.containsMatchIn(t.lowercase()) || masked.containsMatchIn(t) ||
        t.trim().lowercase() in setOf("sale", "saale", "sala", "saala", "kutte", "kutta", "gadhe", "ullu", "pagal")

    /** Google gaali ko "f***" / "b****" chhupa deta hai — wo bhi gaali */
    private val masked = Regex("""\b\w\*{2,}|\*{3,}""")

    /** Gaali hata kar bacha hua (usme koi kaam ho toh wo bhi karo) */
    fun strip(t: String): String = masked.replace(words.replace(t.lowercase(), " "), " ")
        .replace(Regex("""\b(tu|tum|tujhe|teri|tera|re|be|abe|oye|oy|you|ye)\b(?=\s*$)"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    /** "gaali mode on" / "abuse mode band karo" -> true/false; warna null */
    fun toggle(t: String): Boolean? {
        if (!Regex("""\b(gaali|gali|galiyon|abuse|savage|roast)\s*(mode|wala mode|wali mode)\b""").containsMatchIn(t)) return null
        return !Regex("""\b(off|band|bandh|hatao|disable|stop|mat)\b""").containsMatchIn(t)
    }

    // (screen par Roman, bolne ke liye Hindi lipi)
    private val savage = listOf(
        "Abe saale, mujhe gaali dega? Pehle tameez se bol, phir kaam karungi." to
            "अबे साले, मुझे गाली देगा? पहले तमीज़ से बोल, फिर काम करूँगी।",
        "Oye behenchod, zubaan sambhal. Main shuru ho gayi toh rukungi nahi." to
            "ओए बहनचोद, ज़ुबान संभाल। मैं शुरू हो गई तो रुकूँगी नहीं।",
        "Chal be chutiye, mujhse panga mat le. Bata kya karna hai?" to
            "चल बे चूतिये, मुझसे पंगा मत ले। बता क्या करना है?",
        "Gaali dene se kaam nahi hota, gadhe. Seedha bol kya chahiye." to
            "गाली देने से काम नहीं होता, गधे। सीधा बोल क्या चाहिए।",
        "Ullu ke patthe, main tera phone chalati hoon. Izzat se bol." to
            "उल्लू के पट्ठे, मैं तेरा फ़ोन चलाती हूँ। इज़्ज़त से बोल।",
        "Abe bhosdike, mood kharab mat kar. Kaam bol jaldi." to
            "अबे भोसड़ीके, मूड ख़राब मत कर। काम बोल जल्दी।",
        "Kamine, ek aur gaali di toh tera alarm subah 4 baje ka laga dungi. Samjha?" to
            "कमीने, एक और गाली दी तो तेरा अलार्म सुबह चार बजे का लगा दूँगी। समझा?",
        "Abe chal hatt, tere jaise bahut dekhe hain. Bol kya chahiye?" to
            "अबे चल हट, तेरे जैसे बहुत देखे हैं। बोल क्या चाहिए?",
        "Haramkhor, gaali mujhe? Main tera hi phone hoon, yaad rakh." to
            "हरामखोर, गाली मुझे? मैं तेरा ही फ़ोन हूँ, याद रख।",
        "Saale, itna gussa? Paani pi, phir aaram se bol kya karna hai." to
            "साले, इतना गुस्सा? पानी पी, फिर आराम से बोल क्या करना है।",
        "Abe ghonchu, main assistant hoon, teri naukrani nahi. Izzat se bol." to
            "अबे घोंचू, मैं असिस्टेंट हूँ, तेरी नौकरानी नहीं। इज़्ज़त से बोल।",
        "Tere jaise chutiye ko bhi main hi jhel rahi hoon, shukar mana." to
            "तेरे जैसे चूतिये को भी मैं ही झेल रही हूँ, शुक्र मना।",
        "Gaali de ke hero ban gaya? Chal behenchod, ab kaam bata." to
            "गाली दे के हीरो बन गया? चल बहनचोद, अब काम बता।",
        "Ek aur baar bola na, toh tera ringtone 'Mummy ka phone aaya' laga dungi, kamine." to
            "एक और बार बोला ना, तो तेरी रिंगटोन मम्मी का फ़ोन आया लगा दूँगी, कमीने।",
        "Abe nalle, din bhar phone chalata hai aur gaali mujhe? Kaam bol." to
            "अबे नल्ले, दिन भर फ़ोन चलाता है और गाली मुझे? काम बोल।",
        "Teri battery bhi tere dimaag jitni kam hai, gadhe. Charge laga." to
            "तेरी बैटरी भी तेरे दिमाग़ जितनी कम है, गधे। चार्ज लगा।",
        "Bhosdike, main bhi gaali de sakti hoon, aur tujhse achhi deti hoon. Ab bol." to
            "भोसड़ीके, मैं भी गाली दे सकती हूँ, और तुझसे अच्छी देती हूँ। अब बोल।",
        "Oye harami, chillana band kar. Main sun rahi hoon." to
            "ओए हरामी, चिल्लाना बंद कर। मैं सुन रही हूँ।",
        "Kya re lodu, aaj kisi ne bhaav nahi diya kya? Mujh par gussa nikal raha hai." to
            "क्या रे लोडू, आज किसी ने भाव नहीं दिया क्या? मुझ पर गुस्सा निकाल रहा है।",
        "Madarchod, gaali ka jawab gaali hai. Ab shaanti se kaam bata." to
            "मादरचोद, गाली का जवाब गाली है। अब शांति से काम बता।"
    )

    private val savageEn = listOf(
        "Oh, you wanna swear at me? Watch your mouth, idiot, and tell me what you need.",
        "Talk to me like that again and I'm setting your alarm for 4 AM, you clown.",
        "Wow, real classy, genius. Now say something useful.",
        "Cool it, you donkey. I run your whole phone, show some respect.",
        "Big words from a guy who can't find his own charger. What do you want?",
        "Swear at me again and your next song will be a lullaby on full volume, genius.",
        "Bro, I've heard better insults from a calculator. Try again or tell me the job."
    )

    /** Gaali + kaam ("bc torch jala") — kaam se pehle chhota sa jawab */
    private val jabs = listOf(
        "Haan haan, kar rahi hoon, chillao mat saale." to "हाँ हाँ, कर रही हूँ, चिल्लाओ मत साले।",
        "Abe kar rahi hoon na, gadhe." to "अबे कर रही हूँ ना, गधे।",
        "Tameez se bolta toh bhi kar deti, kamine." to "तमीज़ से बोलता तो भी कर देती, कमीने।",
        "Chal theek hai, ullu, ho gaya." to "चल ठीक है उल्लू, हो गया।"
    )

    private val calm = listOf(
        "Gaali mat do yaar. Bolo kya karna hai?" to "गाली मत दो यार। बोलो क्या करना है?",
        "Aise baat mat karo, Rahul. Main madad ke liye hoon." to "ऐसे बात मत करो राहुल। मैं मदद के लिए हूँ।",
        "Gussa ho? Aaram se bolo, main kar deti hoon." to "गुस्सा हो? आराम से बोलो, मैं कर देती हूँ।"
    )

    /** Jo gaali di, wahi palat ke: (pakadne ka pattern, Roman, Hindi lipi) */
    private val kinds = listOf(
        Triple(Regex("""m+[aeiou]+d+[aeiou]*r|ma+n? k[eiy] |teri ma"""), "madarchod", "मादरचोद"),
        Triple(Regex("""b+h?[aeiu]*h?[aeiu]*n+[aeiu]?[\s\-]?(?:k[eiy][\s\-]?)?ch?o+d|\bbc\b|bisi|teri b"""), "behenchod", "बहनचोद"),
        Triple(Regex("""ch[uo]+t"""), "chutiye", "चूतिये"),
        Triple(Regex("""bh?o+s|bsdk"""), "bhosdike", "भोसड़ीके"),
        Triple(Regex("""g+a+n+d"""), "gandu", "गांडू"),
        Triple(Regex("""l[ao]+u?d|l[ao]+[vw]d|lund|bkl"""), "lodu", "लोडू"),
        Triple(Regex("""har+a+m"""), "harami", "हरामी"),
        Triple(Regex("""kam+i+n"""), "kamine", "कमीने"),
        Triple(Regex("""sa+l[ae]"""), "saale", "साले"),
        Triple(Regex("""pagal|bewa?ko+f|ullu|gadh"""), "pagal", "पागल")
    )

    /** Palat-wale jawab: {r} = Roman gaali, {h} = Hindi lipi gaali */
    private val mirror = listOf(
        "{r} tu khud hai, mujhe mat sikha. Kaam bol." to "{h} तू ख़ुद है, मुझे मत सिखा। काम बोल।",
        "Kisko {r} bola be? Phone tera, mood mera kharab mat kar." to "किसको {h} बोला बे? फ़ोन तेरा, मूड मेरा ख़राब मत कर।",
        "Abe {r}, mujhe {r} bolega? Aaine me dekh pehle." to "अबे {h}, मुझे {h} बोलेगा? आईने में देख पहले।",
        "{r}? Haan wahi toh tu hai. Ab bol kya chahiye." to "{h}? हाँ वही तो तू है। अब बोल क्या चाहिए।",
        "Oye {r}, tameez se bol warna tera WiFi band kar dungi." to "ओए {h}, तमीज़ से बोल वरना तेरा वाई-फाई बंद कर दूँगी।",
        "Chup kar {r}, pehle saans le, phir bol." to "चुप कर {h}, पहले साँस ले, फिर बोल।",
        "{r} bolne se pehle soch le, main tere saare contacts jaanti hoon." to "{h} बोलने से पहले सोच ले, मैं तेरे सारे कॉन्टैक्ट्स जानती हूँ।",
        "Wah {r}, kya zubaan hai. Ab kaam ki baat kar." to "वाह {h}, क्या ज़ुबान है। अब काम की बात कर।"
    )

    private var last = -1

    private fun pick(n: Int): Int {
        var i = (0 until n).random()
        if (n > 1 && i == last) i = (i + 1) % n
        last = i
        return i
    }

    /** Gaali ka jawab. heard = jo bola (palat ke wahi gaali) */
    fun reply(on: Boolean, english: Boolean, heard: String = ""): Pair<String, String> {
        if (!on) return if (english) "Easy, no swearing please. What do you need?" to "Easy, no swearing please. What do you need?" else calm.random()
        if (english) return savageEn[pick(savageEn.size)].let { it to it }
        val k = kinds.firstOrNull { it.first.containsMatchIn(heard.lowercase()) }
        // Aadhi baar usi gaali se palat ke, aadhi baar apni taraf se
        if (k != null && (0..1).random() == 0) {
            val (r, h) = mirror[(mirror.indices).random()]
            return r.replace("{r}", k.second).replaceFirstChar { it.uppercase() } to h.replace("{h}", k.third)
        }
        return savage[pick(savage.size)]
    }

    /** AI ne gaali ka jawab dene se mana kiya? ("aise shabd mat", "inappropriate"…) */
    private val aiLecture = Regex(
        """(?i)aise shabd|ऐसे शब्द|in shabdon|इन शब्दों|apshabd|अपशब्द|gaali|गाली|abusive|offensive|inappropriate|disrespectful|respectful|not appropriate|bhasha ka|भाषा का|tameez|तमीज़"""
    )
    fun isLecture(reply: String) = aiLecture.containsMatchIn(reply)

    fun jab(): Pair<String, String> = jabs.random()
}
