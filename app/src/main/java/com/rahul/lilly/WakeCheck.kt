package com.rahul.lilly

/**
 * "Lilly" sach me bola gaya ya nahi — galti se jaagna rokne ke liye.
 *
 * Offline wake-word model sirf kuch shabd jaanta hai, isliye wo aas-paas ki har awaaz ko
 * "lily" bana deta hai. Yahan har shabd ka bharosa (confidence) aur vakya ki lambai dekhte hain:
 * asli bulawa chhota hota hai ("Hey Lilly" / "Lilly"), TV ya baat-cheet lambi.
 */
object WakeCheck {

    data class Word(val word: String, val conf: Double)

    private val objRe = Regex("""\{[^{}]*\}""")
    private val confRe = Regex(""""conf"\s*:\s*([0-9.eE+-]+)""")
    private val wordRe = Regex(""""word"\s*:\s*"([^"]*)"""")
    private val textRe = Regex(""""text"\s*:\s*"([^"]*)"""")
    private val lilyRe = Regex("""^lil+y$""")
    private val greet = setOf("hey", "hi", "hello")

    /** Vosk ka JSON ({"result":[{"conf":..,"word":..}], "text":..}) -> shabd aur bharosa */
    fun words(json: String): List<Word> {
        val out = ArrayList<Word>()
        for (m in objRe.findAll(json)) {
            val w = wordRe.find(m.value)?.groupValues?.get(1) ?: continue
            val c = confRe.find(m.value)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            out.add(Word(w.lowercase(), c))
        }
        if (out.isEmpty()) {
            // Shabd-wise jaankari na ho toh sirf text
            val t = textRe.find(json)?.groupValues?.get(1) ?: ""
            t.split(" ").filter { it.isNotBlank() }.forEach { out.add(Word(it.lowercase(), 1.0)) }
        }
        return out
    }

    /**
     * sensitivity: "low" (sirf saaf "Hey Lilly"), "normal", "high" (jaldi jaagti hai, galti bhi zyada)
     */
    fun accept(json: String?, sensitivity: String): Boolean {
        if (json.isNullOrBlank()) return false
        val w = words(json)
        val i = w.indexOfFirst { lilyRe.matches(it.word) }
        if (i < 0) return false
        val lily = w[i]
        val hey = w.getOrNull(i - 1)?.takeIf { it.word in greet }
        // Lilly ke aas-paas kitni aur awaaz thi (lamba vakya = shayad TV / baat-cheet)
        val extra = w.size - 1 - (if (hey != null) 1 else 0)
        return when (sensitivity) {
            "high" -> lily.conf >= 0.55 && extra <= 2
            "low" -> hey != null && hey.conf >= 0.7 && lily.conf >= 0.9 && extra == 0
            else -> extra <= 1 && (
                (hey != null && lily.conf >= 0.75 && hey.conf >= 0.5) ||
                    (hey == null && lily.conf >= 0.92)
                )
        }
    }
}
