package com.rahul.lilly

/**
 * Lilly ki yaaddasht — aapke baare me baatein ("mera gym 6 baje hai") aur purane jawab.
 * Phone par hi save hoti hai; AI se sawaal poochhte waqt ye baatein saath bheji jaati hain
 * taaki jawab aapke hisaab se aaye.
 *
 * Isme Android ka kuch nahi hai, isliye iske tests har build par chalte hain.
 */
class Memory(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    data class Fact(val text: String, val at: Long)
    data class Answer(val display: String, val spoken: String, val english: Boolean, val at: Long)

    private val facts = ArrayList<Fact>()
    private val answers = LinkedHashMap<String, Answer>()

    companion object {
        private const val MAX_FACTS = 80
        private const val MAX_ANSWERS = 150
        private const val ANSWER_TTL_MS = 30L * 24 * 60 * 60 * 1000

        /** Aise sawaal jinka jawab badalta rehta hai — inhe save nahi karte */
        private val changing = Regex(
            """\b(time|samay|baje|aaj|today|tonight|abhi|now|kal|tomorrow|yesterday|date|tarikh|weather|mausam|""" +
                """barish|rain|temperature|news|khabar|score|match|price|rate|bhav|stock|share|latest|current|live|""" +
                """this week|is hafte|update|naya|new|kitne din|how many days|battery|traffic)\b"""
        )

        /** Sawaal ko ek jaisa banao: "Hey Lilly, What is GDP?" == "what is gdp" */
        fun key(q: String): String =
            Parser.normalize(Parser.stripWakeWord(q)).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    init { reload() }

    // ---------------- Baatein (facts) ----------------

    fun facts(): List<Fact> = facts.toList()

    /** @return false agar ye baat pehle se yaad thi ya bahut chhoti hai */
    fun remember(textRaw: String): Boolean {
        val text = textRaw.trim().trimEnd('.', '।').replace(Regex("\\s+"), " ")
        if (text.length < 4) return false
        val k = key(text)
        if (facts.any { key(it.text) == k }) return false
        facts.add(Fact(text, clock()))
        while (facts.size > MAX_FACTS) facts.removeAt(0)
        persist()
        return true
    }

    /** "bhool jao ki mera gym 6 baje hai" — milti-julti baatein hatao. @return kitni hatin */
    fun forgetMatching(what: String): Int {
        val k = key(what).split(" ").filter { it.length >= 3 }.toSet()
        if (k.isEmpty()) return 0
        val before = facts.size
        facts.removeAll { f ->
            val fk = key(f.text).split(" ").toSet()
            val overlap = k.count { it in fk }
            overlap >= maxOf(1, (k.size + 1) / 2)
        }
        val removed = before - facts.size
        if (removed > 0) persist()
        return removed
    }

    fun forget(fact: Fact) {
        facts.remove(fact)
        persist()
    }

    /** AI ko bhejne ke liye */
    fun factsForPrompt(): String =
        if (facts.isEmpty()) "" else facts.joinToString("\n") { "- ${it.text}" }

    // ---------------- Purane jawab ----------------

    fun isCacheable(question: String): Boolean {
        val k = key(question)
        return k.split(" ").size >= 3 && !changing.containsMatchIn(k)
    }

    fun cachedAnswer(question: String, english: Boolean): Answer? {
        val a = answers[key(question)] ?: return null
        if (clock() - a.at > ANSWER_TTL_MS || a.english != english) return null
        return a
    }

    fun cacheAnswer(question: String, display: String, spoken: String, english: Boolean) {
        if (!isCacheable(question) || spoken.isBlank()) return
        val k = key(question)
        answers.remove(k)
        answers[k] = Answer(display, spoken, english, clock())
        while (answers.size > MAX_ANSWERS) answers.remove(answers.keys.first())
        persist()
    }

    fun answerCount(): Int = answers.size

    fun clearAnswers() {
        answers.clear()
        persist()
    }

    fun clearAll() {
        facts.clear()
        answers.clear()
        persist()
    }

    // ---------------- Save / load ----------------

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    private fun persist() {
        val sb = StringBuilder()
        for (f in facts) sb.append("f\t").append(f.at).append('\t').append(clean(f.text)).append('\n')
        for ((k, a) in answers) {
            sb.append("a\t").append(a.at).append('\t').append(clean(k)).append('\t')
                .append(if (a.english) "1" else "0").append('\t')
                .append(clean(a.display)).append('\t').append(clean(a.spoken)).append('\n')
        }
        try { save(sb.toString()) } catch (e: Exception) { }
    }

    fun reload() {
        facts.clear()
        answers.clear()
        val data = try { load() } catch (e: Exception) { null } ?: return
        for (line in data.split('\n')) {
            val f = line.split('\t')
            try {
                when (f[0]) {
                    "f" -> facts.add(Fact(f[2], f[1].toLong()))
                    "a" -> answers[f[2]] = Answer(f[4], f[5], f[3] == "1", f[1].toLong())
                }
            } catch (e: Exception) { /* kharab line chhod do */ }
        }
    }
}
