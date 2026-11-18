package com.rahul.lilly

/**
 * Ladke ki awaaz chuni ho toh Lilly ki apni baaton ko ladke wali Hindi me badlo:
 * "kar rahi hoon" -> "kar raha hoon", "karungi" -> "karunga", "समझ नहीं पाई" -> "समझ नहीं पाया".
 * Sirf "main" (pehla vyakti) wale roop badalte hain — "battery khatam hogi" jaisi baatein waisi hi rehti hain.
 */
object Persona {

    private const val END = """(?![\p{L}\p{M}])"""

    private val rules: List<Pair<Regex, String>> = listOf(
        // ---- Devanagari ----
        Regex("""हुई (हूँ|हूं)""") to "हुआ $1",
        Regex("""ई (हूँ|हूं)""") to "या $1",               // गई हूँ -> गया हूँ
        Regex("""(?<!लिल)ी (हूँ|हूं|थी)""") to "ा $1",             // रही हूँ, करती हूँ, सकती हूँ
        Regex("""(ू|ऊ)(ँ|ं)गी$END""") to "$1$2गा",          // करूँगी, बताऊँगी, लगाऊंगी
        Regex("""नहीं पाई$END""") to "नहीं पाया",
        Regex("""पाई नहीं""") to "पाया नहीं",
        Regex("""मैं समझी$END""") to "मैं समझा",
        Regex("""ा थी$END""") to "ा था",                     // रही थी -> रहा था
        // ---- Roman Hinglish ----
        Regex("""(?i)\b(?!(?:wahi|yahi|abhi|sahi|bhi|nahi|hi|lili|lilli)\b)(\w+?)i (hoon|hun|hu|hoo)\b""") to "$1a $2",
        Regex("""(?i)\b(\w+)ungi\b""") to "$1unga",       // karungi, bataungi
        Regex("""(?i)\b(\w+)oongi\b""") to "$1oonga",
        Regex("""(?i)\bnahi (paayi|payi|pai)\b""") to "nahi paaya",
        Regex("""(?i)\bmain samjhi\b""") to "main samjha"
    )

    fun masculine(text: String): String {
        var s = text
        for ((re, to) in rules) s = re.replace(s, to)
        return s
    }
}
