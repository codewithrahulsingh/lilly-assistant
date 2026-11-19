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
            """(?:tu|abe|oye|saale|chal|be) kutt[ae]|kutt[ae] (?:kahin k[ae]|saal[ae]|kamin[ae])|suw?a+r\w*|nalaa?yak|nikamm[ae]|dhakkan|chomu|ghatiya|tharki""",
            """bakchod\w*|bhadw[ae]\w*|bhadwa|dalla|haramza+d[ae]\w*|jha+nt\w*|tatt[ae]|gand ?mar[ai]\w*|ma+ ?chud\w*|teri ma+n? ka\w*|chapri""",
            """fuck\w*|motherfuck\w*|bitch\w*|bastard|asshole|dumbass|idiot|stupid|moron|shut up|useless""",
            """dick\w*|prick|jerk|loser|dumb|shit\w*|bullshit|wtf|screw you|piss off|go to hell|damn|crap"""
        ).joinToString("|", prefix = """\b(?:""", postfix = """)\b""")
    )

    /** Bole hue me gaali hai? */
    fun has(t: String) = words.containsMatchIn(t.lowercase()) || masked.containsMatchIn(t) ||
        t.trim().lowercase() in setOf("sale", "saale", "sala", "saala", "kutte", "kutta", "gadhe", "gadha", "ullu", "pagal", "nalle", "chal hat", "abe")

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
        "Bro, I've heard better insults from a calculator. Try again or tell me the job.",
        "Language, you absolute potato. What do you need?",
        "Keep talking like that and I'm renaming you 'Dumbo' in your own contacts.",
        "You kiss your mom with that mouth? Tell me the job."
    )

    /** Gaali + kaam ("bc torch jala") — kaam se pehle chhota sa jawab */
    private val jabs = listOf(
        "Haan haan, kar rahi hoon, chillao mat saale." to "हाँ हाँ, कर रही हूँ, चिल्लाओ मत साले।",
        "Abe kar rahi hoon na, gadhe." to "अबे कर रही हूँ ना, गधे।",
        "Tameez se bolta toh bhi kar deti, kamine." to "तमीज़ से बोलता तो भी कर देती, कमीने।",
        "Chal theek hai, ullu, ho gaya." to "चल ठीक है उल्लू, हो गया।",
        "Gaali deke kaam karwata hai, kamine? Chal, ye le." to "गाली देके काम करवाता है, कमीने? चल, ये ले।",
        "Abe saale, sabr rakh, ho raha hai." to "अबे साले, सब्र रख, हो रहा है।",
        "Behenchod, ek baar pyaar se bolta toh?" to "बहनचोद, एक बार प्यार से बोलता तो?",
        "Chutiye, itna chillane ki zaroorat nahi thi. Ho gaya." to "चूतिये, इतना चिल्लाने की ज़रूरत नहीं थी। हो गया।"
    )

    private val calm = listOf(
        "Gaali mat do yaar. Bolo kya karna hai?" to "गाली मत दो यार। बोलो क्या करना है?",
        "Aise baat mat karo, Rahul. Main madad ke liye hoon." to "ऐसे बात मत करो राहुल। मैं मदद के लिए हूँ।",
        "Gussa ho? Aaram se bolo, main kar deti hoon." to "गुस्सा हो? आराम से बोलो, मैं कर देती हूँ।"
    )

    /** Har gaali ki pehchaan: (pattern, Roman naam, Hindi lipi naam) */
    private val kinds = listOf(
        Triple(Regex("""m+[aeiou]+d+[aeiou]*r|ma+n? k[eiy] |teri ma|ma+ ?chud"""), "madarchod", "मादरचोद"),
        Triple(Regex("""b+h?[aeiu]*h?[aeiu]*n+[aeiu]?[\s\-]?(?:k[eiy][\s\-]?)?ch?o+d|\bbc\b|bisi|teri b"""), "behenchod", "बहनचोद"),
        Triple(Regex("""ch[uo]+t"""), "chutiye", "चूतिये"),
        Triple(Regex("""bh?o+s|bsdk"""), "bhosdike", "भोसड़ीके"),
        Triple(Regex("""g+a+n+d"""), "gandu", "गांडू"),
        Triple(Regex("""l[ao]+u?d|l[ao]+[vw]d|lund|bkl"""), "lodu", "लोडू"),
        Triple(Regex("""haramza"""), "haramzade", "हरामज़ादे"),
        Triple(Regex("""har+a+m"""), "harami", "हरामी"),
        Triple(Regex("""kam+i+n"""), "kamine", "कमीने"),
        Triple(Regex("""kutt|suw?a+r"""), "kutte", "कुत्ते"),
        Triple(Regex("""bakchod"""), "bakchod", "बकचोद"),
        Triple(Regex("""bhadw|dalla"""), "bhadwe", "भड़वे"),
        Triple(Regex("""jha+nt"""), "jhaatu", "झाटू"),
        Triple(Regex("""nalaa?yak|nikamm|dhakkan|chomu|ghatiya|chapri"""), "nalayak", "नालायक"),
        Triple(Regex("""sa+l[ae]"""), "saale", "साले"),
        Triple(Regex("""pagal|bewa?ko+f|ullu|gadh"""), "pagal", "पागल")
    )

    /** Har gaali ke apne khaas jawab (Roman, Hindi lipi) */
    private val byKind: Map<String, List<Pair<String, String>>> = mapOf(
        "madarchod" to listOf(
            "Maa tak mat ja, madarchod. Main teri maa ki bhi assistant hoon, unhe bata dungi." to "माँ तक मत जा, मादरचोद। मैं तेरी माँ की भी असिस्टेंट हूँ, उन्हें बता दूँगी।",
            "Madarchod bolega? Tere ghar wale jaante hain tu phone se aise baat karta hai?" to "मादरचोद बोलेगा? तेरे घर वाले जानते हैं तू फ़ोन से ऐसे बात करता है?",
            "Oye madarchod, zyada bola toh mummy ko call laga dungi, wahi samjhayengi." to "ओए मादरचोद, ज़्यादा बोला तो मम्मी को कॉल लगा दूँगी, वही समझाएँगी।"
        ),
        "behenchod" to listOf(
            "Behenchod tu, aur gaali mujhe? Pehle apni zubaan dho ke aa." to "बहनचोद तू, और गाली मुझे? पहले अपनी ज़ुबान धो के आ।",
            "Bc bc kya laga rakha hai, behenchod? Seedha kaam bol." to "बीसी बीसी क्या लगा रखा है, बहनचोद? सीधा काम बोल।",
            "Abe behenchod, main Lilly hoon, teri dost nahi jo sab sun lungi." to "अबे बहनचोद, मैं लिली हूँ, तेरी दोस्त नहीं जो सब सुन लूँगी।"
        ),
        "chutiye" to listOf(
            "Chutiya kisko bola? Tujhe toh apna charger tak nahi milta, chutiye." to "चूतिया किसको बोला? तुझे तो अपना चार्जर तक नहीं मिलता, चूतिये।",
            "Abe chutiye, mujhe chutiya bolke tu smart nahi ban jayega." to "अबे चूतिये, मुझे चूतिया बोलके तू स्मार्ट नहीं बन जाएगा।",
            "Chutiyapa band kar aur kaam bol, chutiye." to "चूतियापा बंद कर और काम बोल, चूतिये।"
        ),
        "bhosdike" to listOf(
            "Bhosdike, itni garmi kahan se aayi? AC chala doon kya?" to "भोसड़ीके, इतनी गर्मी कहाँ से आई? एसी चला दूँ क्या?",
            "Abe bhosdike, zubaan pe lagaam laga, warna tera WiFi gaya." to "अबे भोसड़ीके, ज़ुबान पे लगाम लगा, वरना तेरा वाई-फाई गया।",
            "Bhosdike, gaali dene me tez, kaam bolne me dheela. Bol jaldi." to "भोसड़ीके, गाली देने में तेज़, काम बोलने में ढीला। बोल जल्दी।"
        ),
        "gandu" to listOf(
            "Gandu tu khud hai, mujhe kyun bol raha hai?" to "गांडू तू ख़ुद है, मुझे क्यों बोल रहा है?",
            "Abe gandu, hosh me aa. Main tera phone hoon, dushman nahi." to "अबे गांडू, होश में आ। मैं तेरा फ़ोन हूँ, दुश्मन नहीं।",
            "Gandu-giri band kar aur kaam bata." to "गांडूगिरी बंद कर और काम बता।"
        ),
        "lodu" to listOf(
            "Lodu, lavde jaisi baatein mat kar. Kaam bol." to "लोडू, लवड़े जैसी बातें मत कर। काम बोल।",
            "Abe lavde, tujhse achha toh tera calculator baat karta hai." to "अबे लवड़े, तुझसे अच्छा तो तेरा कैलकुलेटर बात करता है।",
            "Lodu, mere bina tu alarm bhi nahi laga pata, aur gaali mujhe?" to "लोडू, मेरे बिना तू अलार्म भी नहीं लगा पाता, और गाली मुझे?"
        ),
        "haramzade" to listOf(
            "Haramzade, namak haram mat ban. Main hi tera saara kaam karti hoon." to "हरामज़ादे, नमक हराम मत बन। मैं ही तेरा सारा काम करती हूँ।",
            "Oye haramzade, tameez bhool gaya kya?" to "ओए हरामज़ादे, तमीज़ भूल गया क्या?"
        ),
        "harami" to listOf(
            "Harami main nahi, tu hai. Raat bhar phone chalata hai." to "हरामी मैं नहीं, तू है। रात भर फ़ोन चलाता है।",
            "Abe harami, ek aur gaali aur teri playlist me sirf bhajan bajenge." to "अबे हरामी, एक और गाली और तेरी प्लेलिस्ट में सिर्फ़ भजन बजेंगे।",
            "Haramkhor, kaam bol, bakwas nahi." to "हरामखोर, काम बोल, बकवास नहीं।"
        ),
        "kamine" to listOf(
            "Kamine, kaminepan dikhana hai toh kahin aur ja. Yahan kaam hota hai." to "कमीने, कमीनापन दिखाना है तो कहीं और जा। यहाँ काम होता है।",
            "Abe kamine, ek aur gaali di toh tera alarm subah 4 baje ka." to "अबे कमीने, एक और गाली दी तो तेरा अलार्म सुबह चार बजे का।",
            "Kamina tu, aur shareef banne ka natak mujhse?" to "कमीना तू, और शरीफ़ बनने का नाटक मुझसे?"
        ),
        "kutte" to listOf(
            "Kutta kisko bola? Bhaunkna band kar aur kaam bol, kutte." to "कुत्ता किसको बोला? भौंकना बंद कर और काम बोल, कुत्ते।",
            "Kutte ki tarah mat bhaunk, main sun rahi hoon." to "कुत्ते की तरह मत भौंक, मैं सुन रही हूँ।",
            "Suar jaisi shakal leke gaali deta hai? Chal kaam bol." to "सुअर जैसी शक्ल लेके गाली देता है? चल काम बोल।"
        ),
        "bakchod" to listOf(
            "Bakchodi kam kar, bakchod. Kaam ki baat bol." to "बकचोदी कम कर, बकचोद। काम की बात बोल।",
            "Abe bakchod, teri bakchodi sunne ke liye main nahi bani." to "अबे बकचोद, तेरी बकचोदी सुनने के लिए मैं नहीं बनी।"
        ),
        "bhadwe" to listOf(
            "Bhadwe, zubaan sambhal ke. Main sab record rakhti hoon." to "भड़वे, ज़ुबान सँभाल के। मैं सब रिकॉर्ड रखती हूँ।",
            "Abe bhadwe, gaali dene se tera kaam nahi hoga." to "अबे भड़वे, गाली देने से तेरा काम नहीं होगा।"
        ),
        "jhaatu" to listOf(
            "Jhaatu, teri baaton me bhi koi dum nahi. Kaam bol." to "झाटू, तेरी बातों में भी कोई दम नहीं। काम बोल।",
            "Abe jhaatu, jhaat jitna dimaag lekar gaali deta hai?" to "अबे झाटू, झाट जितना दिमाग़ लेकर गाली देता है?"
        ),
        "nalayak" to listOf(
            "Nalayak tu hai, main toh 24 ghante kaam karti hoon." to "नालायक तू है, मैं तो चौबीस घंटे काम करती हूँ।",
            "Dhakkan, pehle khud kuch kar le, phir mujhe bol." to "ढक्कन, पहले ख़ुद कुछ कर ले, फिर मुझे बोल।",
            "Nikamme, din bhar reels dekhta hai aur nalayak mujhe bolega?" to "निकम्मे, दिन भर रील्स देखता है और नालायक मुझे बोलेगा?"
        ),
        "saale" to listOf(
            "Saale, saala kisko bola? Main teri behen nahi, teri boss hoon." to "साले, साला किसको बोला? मैं तेरी बहन नहीं, तेरी बॉस हूँ।",
            "Abe saale, chill kar. Paani pi aur kaam bol." to "अबे साले, चिल कर। पानी पी और काम बोल।"
        ),
        "pagal" to listOf(
            "Pagal main nahi, tu hai jo phone se lad raha hai." to "पागल मैं नहीं, तू है जो फ़ोन से लड़ रहा है।",
            "Ullu ke patthe, dimaag ghar pe chhod aaya kya?" to "उल्लू के पट्ठे, दिमाग़ घर पे छोड़ आया क्या?",
            "Gadhe, gadhe jaisi baat mat kar. Kaam bol." to "गधे, गधे जैसी बात मत कर। काम बोल।"
        )
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

    private var last = ""

    private fun <T> pickFrom(list: List<T>): T {
        if (list.size == 1) return list[0]
        var x = list.random()
        if (x.toString() == last) x = list.random()
        last = x.toString()
        return x
    }

    /** Gaali ka jawab. heard = jo bola (us gaali ke khaas jawab / palat ke wahi gaali) */
    fun reply(on: Boolean, english: Boolean, heard: String = ""): Pair<String, String> {
        if (!on) return if (english) "Easy, no swearing please. What do you need?" to "Easy, no swearing please. What do you need?" else calm.random()
        if (english && !Regex("""[a-z]*(?:chod|chut|bhos|gand|lod|lavd|haram|kamin|saal|kutt)""").containsMatchIn(heard.lowercase()))
            return pickFrom(savageEn).let { it to it }
        val k = kinds.firstOrNull { it.first.containsMatchIn(heard.lowercase()) }
        if (k != null) {
            // 60% us gaali ka khaas jawab, 25% palat ke wahi gaali, baaki apni taraf se
            val roll = (0 until 100).random()
            val own = byKind[k.second]
            if (own != null && roll < 60) return pickFrom(own)
            if (roll < 85) {
                val (r, h) = pickFrom(mirror)
                return r.replace("{r}", k.second).replaceFirstChar { it.uppercase() } to h.replace("{h}", k.third)
            }
        }
        return pickFrom(savage)
    }

    /** Gaali ke saath sawaal bhi pucha ("bc ye bata cricket score kya hai") — AI ka jawab tapori andaaz me */
    @Volatile var roughAnswer = false

    /** AI ne gaali ka jawab dene se mana kiya? ("aise shabd mat", "inappropriate"…) */
    private val aiLecture = Regex(
        """(?i)aise shabd|ऐसे शब्द|in shabdon|इन शब्दों|apshabd|अपशब्द|gaali|गाली|abusive|offensive|inappropriate|disrespectful|respectful|not appropriate|bhasha ka|भाषा का|tameez|तमीज़"""
    )
    fun isLecture(reply: String) = aiLecture.containsMatchIn(reply)

    fun jab(): Pair<String, String> = jabs.random()
}
