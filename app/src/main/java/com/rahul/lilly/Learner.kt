package com.rahul.lilly

/**
 * Lilly ki apni seekh — phone par hi save hoti hai, internet nahi chahiye.
 *
 * 1. Phrases: "good night" -> "turn off the torch"
 *    - user khud sikhaye ("When I say good night, turn off the torch")
 *    - AI ne kisi nayi baat ko command samjha ho
 *    - ek command fail hui aur user ne turant doosre shabdon me wahi kaam karwa liya
 * 2. Contacts / apps: bola gaya naam -> asli contact / app (2 baar sahi chalne par pakka)
 * 3. Kaun sa contact / app zyada use hota hai — barabar match par wahi jeete
 * 4. Gaane ke liye pasandida app (Spotify / YouTube)
 *
 * Isme Android ka kuch nahi hai, isliye iske tests har build par chalte hain.
 */
class Learner(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private class Entry(val value: String, var label: String, var count: Int, var taught: Boolean)

    private val phrases = LinkedHashMap<String, Entry>()
    private val contacts = LinkedHashMap<String, Entry>()
    private val apps = LinkedHashMap<String, Entry>()
    private val numbers = LinkedHashMap<String, Entry>()
    private val usage = HashMap<String, Int>()

    var musicApp: String? = null
        private set

    private var lastFailure: String? = null
    private var lastFailureAt = 0L

    init { reload() }

    companion object {
        private const val MAX_EACH = 200
        private const val FAIL_WINDOW_MS = 60_000L

        /** Ek hi baat ke alag roop ("Hey Lilly, Good Night!" / "good night") ek hi key ban jaayein */
        fun key(t: String): String = Parser.normalize(Parser.stripWakeWord(t))
    }

    // ---------------- Phrases ----------------

    /** Seekha hua phrase ho toh uski jagah asli command */
    fun rewrite(text: String): String? = phrases[key(text)]?.value

    fun teach(from: String, to: String, taught: Boolean = true): Boolean {
        val k = key(from)
        val v = key(to)
        if (k.isBlank() || v.isBlank() || k == v) return false
        phrases.remove(k)
        phrases[k] = Entry(v, "", 1, taught)
        trim(phrases)
        persist()
        return true
    }

    /** Command fail hui (naam/app nahi mila, samajh nahi aaya) */
    fun noteFailure(text: String) {
        lastFailure = key(text)
        lastFailureAt = clock()
    }

    /**
     * Command chal gayi. Agar thodi der pehle koi command fail hui thi, toh maan lo user ne
     * wahi baat doosre shabdon me kahi — agli baar pehle wale shabd bhi chalenge.
     * @return true agar kuch naya seekha
     */
    fun noteSuccess(text: String): Boolean {
        val f = lastFailure
        lastFailure = null
        if (f == null || clock() - lastFailureAt > FAIL_WINDOW_MS) return false
        if (phrases[f]?.taught == true) return false
        return teach(f, text, taught = false)
    }

    // ---------------- Contacts ----------------

    /** Pakka seekha hua contact (sikhaya gaya ya 2+ baar sahi chala) */
    fun contactFor(spoken: String): String? {
        val e = contacts[key(spoken)] ?: return null
        return if (e.taught || e.count >= 2) e.value else null
    }

    fun rememberContact(spoken: String, name: String) {
        remember(contacts, key(spoken), name, name)
        bump("c:$name")
    }

    /** Jo contact zyada call hota hai use thoda aage rakho (0..12) */
    fun contactBonus(name: String): Int = minOf((usage["c:$name"] ?: 0) * 3, 12)

    // ---------------- Kai number wale contact ----------------

    /** Is contact ke kai number me se jo aap 2+ baar chun chuke ho (aakhri 10 ank) */
    fun numberFor(contact: String): String? {
        val e = numbers[contact.lowercase()] ?: return null
        return if (e.taught || e.count >= 2) e.value else null
    }

    fun rememberNumber(contact: String, digits: String, label: String) {
        remember(numbers, contact.lowercase(), digits, "$contact · $label …${digits.takeLast(4)}")
        persist()
    }

    // ---------------- Apps ----------------

    fun appFor(spoken: String): String? {
        val e = apps[key(spoken)] ?: return null
        return if (e.taught || e.count >= 2) e.value else null
    }

    fun rememberApp(spoken: String, pkg: String, label: String) {
        remember(apps, key(spoken), pkg, label)
        bump("a:$pkg")
    }

    fun appBonus(pkg: String): Int = minOf((usage["a:$pkg"] ?: 0) * 3, 12)

    // ---------------- Music ----------------

    fun setMusicApp(app: String) {
        if (app.isBlank() || app == musicApp) return
        musicApp = app
        persist()
    }

    // ---------------- Dekhna / bhoolna (app ki screen ke liye) ----------------

    data class Item(val kind: String, val key: String, val text: String)

    fun items(): List<Item> {
        val out = ArrayList<Item>()
        for ((k, e) in phrases.entries.reversed()) {
            val how = if (e.taught) "taught" else "learned"
            out.add(Item("p", k, "“$k” → ${e.value} ($how)"))
        }
        for ((k, e) in contacts.entries.reversed()) {
            if (e.count >= 2 || e.taught) out.add(Item("c", k, "Call “$k” → ${e.label} (${e.count}×)"))
        }
        for ((k, e) in apps.entries.reversed()) {
            if (e.count >= 2 || e.taught) out.add(Item("a", k, "Open “$k” → ${e.label} (${e.count}×)"))
        }
        for ((k, e) in numbers.entries.reversed()) {
            if (e.count >= 2 || e.taught) out.add(Item("n", k, "Number for ${e.label} (${e.count}×)"))
        }
        musicApp?.let { out.add(Item("m", it, "Songs play on ${it.replaceFirstChar { c -> c.uppercase() }}")) }
        return out
    }

    fun forget(item: Item) {
        when (item.kind) {
            "p" -> phrases.remove(item.key)
            "c" -> contacts.remove(item.key)
            "a" -> apps.remove(item.key)
            "n" -> numbers.remove(item.key)
            "m" -> musicApp = null
        }
        persist()
    }

    fun clear() {
        phrases.clear(); contacts.clear(); apps.clear(); numbers.clear(); usage.clear()
        musicApp = null
        lastFailure = null
        persist()
    }

    // ---------------- Save / load ----------------

    private fun remember(map: LinkedHashMap<String, Entry>, k: String, value: String, label: String) {
        if (k.isBlank()) return
        val e = map.remove(k)
        if (e != null && e.value == value) {
            e.count++
            e.label = label
            map[k] = e // naya use = list ke ant me (sabse taaza)
        } else {
            map[k] = Entry(value, label, 1, false)
        }
        trim(map)
    }

    private fun bump(k: String) {
        usage[k] = (usage[k] ?: 0) + 1
        if (usage.size > MAX_EACH * 2) {
            usage.entries.minByOrNull { it.value }?.let { usage.remove(it.key) }
        }
        persist()
    }

    private fun trim(map: LinkedHashMap<String, Entry>) {
        while (map.size > MAX_EACH) map.remove(map.keys.first())
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    private fun persist() {
        val sb = StringBuilder()
        fun line(vararg f: String) { sb.append(f.joinToString("\t") { clean(it) }).append('\n') }
        for ((k, e) in phrases) line("p", k, e.value, e.label, e.count.toString(), if (e.taught) "1" else "0")
        for ((k, e) in contacts) line("c", k, e.value, e.label, e.count.toString(), if (e.taught) "1" else "0")
        for ((k, e) in apps) line("a", k, e.value, e.label, e.count.toString(), if (e.taught) "1" else "0")
        for ((k, e) in numbers) line("n", k, e.value, e.label, e.count.toString(), if (e.taught) "1" else "0")
        for ((k, n) in usage) line("u", k, n.toString())
        musicApp?.let { line("m", it) }
        try { save(sb.toString()) } catch (e: Exception) { }
    }

    /** Doosri jagah (app ki screen) se badlaav hua ho toh dobara padho */
    fun reload() {
        phrases.clear(); contacts.clear(); apps.clear(); numbers.clear(); usage.clear()
        musicApp = null
        val data = try { load() } catch (e: Exception) { null } ?: return
        for (raw in data.split('\n')) {
            val f = raw.split('\t')
            try {
                when (f[0]) {
                    "p", "c", "a", "n" -> {
                        val e = Entry(f[2], f[3], f[4].toInt(), f[5] == "1")
                        when (f[0]) {
                            "p" -> phrases[f[1]] = e
                            "c" -> contacts[f[1]] = e
                            "n" -> numbers[f[1]] = e
                            else -> apps[f[1]] = e
                        }
                    }
                    "u" -> usage[f[1]] = f[2].toInt()
                    "m" -> musicApp = f[1]
                }
            } catch (e: Exception) { /* kharab line chhod do */ }
        }
    }
}
