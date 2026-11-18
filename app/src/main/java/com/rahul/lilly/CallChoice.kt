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

    /** Screen par: "1. Mobile …3210" */
    fun numberLine(i: Int, p: Phone) = "${i + 1}. ${p.label.ifBlank { "Number" }} …${lastDigits(p.number)}"

    private val ordinals: List<Pair<Int, Set<String>>> = listOf(
        0 to setOf("first", "1st", "one", "1", "pehla", "pehle", "pehli", "pahla", "pahle", "upar", "upar wala", "ek"),
        1 to setOf("second", "2nd", "two", "2", "doosra", "dusra", "doosri", "dusri", "doosre", "dusre", "do"),
        2 to setOf("third", "3rd", "three", "3", "teesra", "tisra", "teesri", "tisri", "teen"),
        3 to setOf("fourth", "4th", "four", "4", "chautha", "chauthi", "char", "chaar"),
        4 to setOf("fifth", "5th", "five", "5", "paanchva", "panchva", "paanch", "panch")
    )
    // "do" (karo/give) aur "ek" bahut aam shabd hain — sirf chhote jawab me ginti maano
    private val ambiguousOrdinals = setOf("do", "ek", "teen", "char", "chaar", "paanch", "panch", "one")
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

        // 1. Ank: "jo 3210 pe khatam hota hai" / "ending 45"
        val nums = Regex("""\d{2,}""").findAll(t).map { it.value }.toList()
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

    private val cancelRe = Regex(
        """^(cancel|no|nahi|nahin|mat karo|rehne do|rahne do|chhodo|chodo|koi nahi|kisi ko nahi|never ?mind|leave it|stop|bas)$"""
    )

    fun isCancel(answerRaw: String): Boolean = cancelRe.matches(Parser.normalize(answerRaw))
}
