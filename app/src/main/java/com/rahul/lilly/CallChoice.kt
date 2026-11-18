package com.rahul.lilly

import java.util.Locale

/**
 * Ek naam ke kai contact / ek contact ke kai number: Siri jaisa poochho aur jawab samjho.
 * Isme Android ka kuch nahi hai, isliye iske tests har build par chalte hain.
 */
object CallChoice {

    data class Phone(val name: String, val number: String, val label: String, val starred: Boolean = false)

    /** Sirf aakhri 10 ank — "+91 98765 43210" aur "09876543210" ek hi number hai */
    fun digitsKey(n: String): String = n.filter { it.isDigit() }.takeLast(10)

    /** Ek hi number do baar save ho toh ek hi rakho (label wala pehle) */
    fun dedupe(list: List<Phone>): List<Phone> {
        val seen = LinkedHashMap<String, Phone>()
        for (p in list) {
            val k = digitsKey(p.number)
            if (k.length < 3) continue
            val old = seen[k]
            if (old == null || (old.label.isBlank() && p.label.isNotBlank())) seen[k] = p
        }
        return seen.values.toList()
    }

    fun lastDigits(n: String) = n.filter { it.isDigit() }.takeLast(4)

    /** Bolne ke liye naam: emoji / chinh hatao ("Abhishek 🔫🔫" -> "Abhishek") */
    fun speakName(n: String): String =
        n.replace(Regex("[^\\p{L}\\p{N} .'-]"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { n }

    /**
     * Bole gaye naam se kaun-kaun se contact ho sakte hain.
     * 1 naam = seedha call. Zyada = poochho.
     *
     * - "Abhishek" (ek shabd): jitne contacts me "Abhishek" shabd hai, sab — chahe kisi ka naam
     *   bilkul "Abhishek" hi ho (jaise "Abhishek 🔫🔫"), kyunki aapka matlab koi bhi ho sakta hai.
     * - "Abhishek Bhaiya" (poora naam) bilkul mile toh wahi.
     * bonus = jise aap zyada call karte ho wo list me upar.
     */
    fun candidates(targets: List<String>, names: List<String>, bonus: (String) -> Int = { 0 }): List<String> {
        val scores = LinkedHashMap<String, Int>()
        for ((qi, q) in targets.withIndex()) {
            for (name in names.distinct()) {
                val base = Parser.contactScore(q, name)
                if (base <= 0) continue
                val sc = base - qi
                if (sc > (scores[name] ?: 0)) scores[name] = sc
            }
        }
        val best = scores.values.maxOrNull() ?: 0
        if (best < 30) return emptyList()
        val multiWord = Parser.normName(targets.first()).split(" ").size >= 2
        val pool = if (best >= 95 && multiWord) {
            // Poora naam mila ("abhishek badiya sar" = "Abhishek Badiyasar")
            scores.filter { it.value >= best - 2 }.keys
        } else {
            // Jis-jis naam me bola gaya shabd hai (75+), warna sirf barabar wale
            scores.filter { it.value >= minOf(best, 75) }.keys
        }
        return pool.sortedWith(
            compareByDescending<String> { bonus(it) }.thenByDescending { scores[it] ?: 0 }.thenBy { it.lowercase(Locale.ROOT) }
        ).take(25)
    }

    /** Screen par: "1. Mobile …3210" */
    fun numberLine(i: Int, p: Phone) = "${i + 1}. ${p.label.ifBlank { "Number" }} …${lastDigits(p.number)}"

    private val ordinals: List<Pair<Int, Set<String>>> = listOf(
        5 to setOf("sixth", "6th", "six", "chhatha", "chhata", "chhe"),
        6 to setOf("seventh", "7th", "seven", "saatva", "satva", "saat"),
        7 to setOf("eighth", "8th", "eight", "aathva", "athva", "aath"),
        8 to setOf("ninth", "9th", "nine", "nauva", "nau"),
        9 to setOf("tenth", "10th", "ten", "dasva", "das"),
        0 to setOf("first", "1st", "one", "1", "pehla", "pehle", "pehli", "pahla", "pahle", "upar", "upar wala", "ek"),
        1 to setOf("second", "2nd", "two", "2", "doosra", "dusra", "doosri", "dusri", "doosre", "dusre", "do"),
        2 to setOf("third", "3rd", "three", "3", "teesra", "tisra", "teesri", "tisri", "teen"),
        3 to setOf("fourth", "4th", "four", "4", "chautha", "chauthi", "char", "chaar"),
        4 to setOf("fifth", "5th", "five", "5", "paanchva", "panchva", "paanch", "panch")
    )
    // "do" (karo/give) aur "ek" bahut aam shabd hain — sirf chhote jawab me ginti maano
    private val ambiguousOrdinals = setOf(
        "do", "ek", "teen", "char", "chaar", "paanch", "panch", "one", "chhe", "saat", "aath", "nau", "das"
    )
    private val fillerWords = setOf(
        "wala", "wale", "wali", "vala", "vale", "vali", "ko", "call", "karo", "kar", "do", "pe", "par", "the", "one",
        "number", "contact", "se", "baat", "please", "jo", "hai", "wo", "woh", "vo", "mera", "mere", "meri", "my", "to"
    )

    /** Naam se chunna: "bhaiya" -> "Abhishek Bhaiya" (na ki "Bhaiya Hf" / "Dr. … Bhaiya") */
    private fun pickByName(answerWords: List<String>, options: List<Phone>): Int? {
        val plain = answerWords.filter { it !in fillerWords }
        if (plain.isEmpty()) return null
        // "badiya sar" -> "badiyasar" bhi try karo
        val said = plain + plain.zipWithNext { a, b -> a + b }
        val nameWords = options.map { Parser.normName(it.name).split(" ").filter { w -> w.isNotBlank() } }
        // Jo shabd sab naamon me hai ("abhishek") wo farak nahi batata
        val shared = nameWords.reduce { a, b -> a.filter { it in b } }.toSet()
        var bestScore = 0
        var bestUnmatched = Int.MAX_VALUE
        val best = ArrayList<Int>()
        for ((i, words) in nameWords.withIndex()) {
            val own = words.filter { it !in shared }
            var score = 0
            var matched = 0
            for (w in own) {
                when {
                    said.contains(w) -> { score += 2; matched++ }
                    w.length >= 4 && said.any { it.length >= 4 && Parser.levenshtein(it, w) <= 1 } -> { score += 1; matched++ }
                }
            }
            if (score == 0) continue
            val unmatched = own.size - matched
            if (score > bestScore || (score == bestScore && unmatched < bestUnmatched)) {
                bestScore = score; bestUnmatched = unmatched; best.clear(); best.add(i)
            } else if (score == bestScore && unmatched == bestUnmatched) best.add(i)
        }
        return if (best.size == 1) best[0] else null
    }
    private val lastWords = setOf("last", "aakhri", "akhri", "neeche", "niche", "neeche wala", "last wala")
    private val labelWords = mapOf(
        "mobile" to listOf("mobile", "cell", "phone"), "mob" to listOf("mobile"),
        "work" to listOf("work", "office", "company"), "office" to listOf("work", "office", "company"),
        "daftar" to listOf("work", "office"), "home" to listOf("home", "ghar", "landline"),
        "ghar" to listOf("home", "ghar", "landline"), "ghar wala" to listOf("home"),
        "personal" to listOf("personal", "mobile"), "main" to listOf("main"), "other" to listOf("other"),
        "jio" to listOf("jio"), "airtel" to listOf("airtel"), "vi" to listOf("vi", "vodafone"),
        "vodafone" to listOf("vodafone", "vi"), "bsnl" to listOf("bsnl"), "whatsapp" to listOf("whatsapp")
    )

    /**
     * User ne kaun sa option chuna? options = screen par dikhaye gaye (number ya naam).
     * @return option ka index, ya null agar samajh na aaye / ek se zyada mil jaayein
     */
    fun pick(answerRaw: String, options: List<Phone>, byName: Boolean): Int? {
        if (options.isEmpty()) return null
        val t = Parser.normalize(answerRaw).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isBlank()) return null
        val words = t.split(" ")
        val short = words.size <= 3

        if (byName) {
            // "number 5" / "5" = list ka 5th naam
            Regex("""\b(\d{1,2})\b""").find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { n ->
                if (n in 1..options.size) return n - 1
            }
            pickByName(words, options)?.let { return it }
        }

        // 1. Ank: "jo 3210 pe khatam hota hai" / "ending 45"
        val nums = if (byName) emptyList() else Regex("""\d{2,}""").findAll(t).map { it.value }.toList()
        for (n in nums) {
            val hits = options.indices.filter { options[it].number.filter { c -> c.isDigit() }.endsWith(n) }
            if (hits.size == 1) return hits[0]
        }

        // 2. Naam ka koi alag shabd: "Sharma wala" (sirf jab naam se chun rahe ho ya naam alag hon)
        val nameHits = options.indices.filter { idx ->
            val nameWords = Parser.normName(options[idx].name).split(" ").filter { it.length >= 3 }
            val others = options.indices.filter { it != idx }
                .flatMap { Parser.normName(options[it].name).split(" ") }.toSet()
            nameWords.any { it !in others && it in words }
        }
        if (nameHits.size == 1) return nameHits[0]

        // 3. Label: "mobile wala", "office"
        if (!byName) {
            for ((spoken, labels) in labelWords) {
                if (!Regex("""\b$spoken\b""").containsMatchIn(t)) continue
                val hits = options.indices.filter { idx ->
                    val l = options[idx].label.lowercase(Locale.ROOT)
                    labels.any { l.contains(it) }
                }
                if (hits.size == 1) return hits[0]
            }
        }

        // 4. Ginti: "pehla", "second one", "2"
        if (words.any { it in lastWords } || t.contains("last wala")) return options.size - 1
        // Pehle pakke shabd ("second", "doosra"); "one"/"do"/"ek" jaise aam shabd sirf tab jab aur kuch na mile
        for (strong in listOf(true, false)) {
            if (!strong && !short) break
            for ((idx, set) in ordinals) {
                if (idx >= options.size) continue
                val hit = words.any { w -> w in set && (w in ambiguousOrdinals) != strong } ||
                    (strong && set.any { it.contains(" ") && t.contains(it) })
                if (hit) return idx
            }
        }
        return null
    }

    /** Naam ka sirf alag hissa bolne ke liye: "Abhishek Badiyasar" -> "Badiyasar" (sab me "Abhishek" hai) */
    fun shortNames(options: List<Phone>): List<String> {
        val words = options.map { Parser.normName(speakName(it.name)).split(" ").filter { w -> w.isNotBlank() } }
        val shared = if (words.size < 2) emptySet() else words.reduce { a, b -> a.filter { it in b } }.toSet()
        return options.map { o ->
            val parts = speakName(o.name).split(" ").filter { it.isNotBlank() && Parser.normName(it) !in shared }
            if (parts.isEmpty()) speakName(o.name) else parts.joinToString(" ")
        }
    }

    private val moreRe = Regex(
        """^(more|aur|aur batao|aur bolo|aur sunao|aur naam|aage|aage batao|next|next page|show more|tell me more|""" +
            """and more|baaki|baki|baaki batao|baki batao|aur dikhao|more names|any more|aur hai|aur kaun)$"""
    )

    /** "more" / "aur batao" — agle 5 naam */
    fun isMore(answerRaw: String): Boolean = moreRe.matches(Parser.normalize(answerRaw))

    private val cancelRe = Regex(
        """^(cancel|no|nahi|nahin|mat karo|rehne do|rahne do|chhodo|chodo|koi nahi|kisi ko nahi|never ?mind|leave it|stop|bas)$"""
    )

    fun isCancel(answerRaw: String): Boolean = cancelRe.matches(Parser.normalize(answerRaw))
}
