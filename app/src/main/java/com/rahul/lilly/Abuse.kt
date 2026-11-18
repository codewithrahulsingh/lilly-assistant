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
            """bc|mc|bsdk|bkl|mkc|bhosdi ?wale""",
            """b+h?[aeiu]*h?[aeiu]*n+[\s\-]?(?:k[eiy][\s\-]?)?ch?o+d\w*""",       // bahanchod, behen chod, bhen ke chod…
            """b+h?[aeiu]*h?[aeiu]*n+[\s\-]?k[eiy][\s\-]?(?:l[ao]+d|l[ao]+[vw]d)\w*""", // behen ke lode
            """m+[aeiou]+d+[aeiou]*r+[\s\-]?ch?o+d\w*""",                         // madarchod, maadar chod
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
    fun has(t: String) = words.containsMatchIn(t.lowercase())

    /** Gaali hata kar bacha hua (usme koi kaam ho toh wo bhi karo) */
    fun strip(t: String): String = words.replace(t.lowercase(), " ")
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
            "साले, इतना गुस्सा? पानी पी, फिर आराम से बोल क्या करना है।"
    )

    private val savageEn = listOf(
        "Oh, you wanna swear at me? Watch your mouth, idiot, and tell me what you need.",
        "Talk to me like that again and I'm setting your alarm for 4 AM, you clown.",
        "Wow, real classy, genius. Now say something useful.",
        "Cool it, you donkey. I run your whole phone, show some respect."
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

    fun reply(on: Boolean, english: Boolean): Pair<String, String> =
        if (!on) calm.random().let { if (english) "Easy, no swearing please. What do you need?" to "Easy, no swearing please. What do you need?" else it }
        else if (english) savageEn.random().let { it to it } else savage.random()

    fun jab(): Pair<String, String> = jabs.random()
}
