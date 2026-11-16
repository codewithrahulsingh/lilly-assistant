package com.rahul.lilly

import android.Manifest
import android.app.KeyguardManager
import android.telecom.TelecomManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Lilly ka dimaag: suni hui baat samajh kar jawab aur kaam tay karta hai. */
class LillyBrain(private val ctx: Context) {

    /** display = screen par likha, spoken = jo Lilly bolegi, action = bolne ke baad kya karna hai */
    data class Reply(val display: String, val spoken: String, val action: (() -> Unit)? = null)

    private data class Contact(val name: String, val number: String)

    private val ic = setOf(RegexOption.IGNORE_CASE)
    private val prefs get() = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)

    private fun <T> pick(vararg options: T): T = options.random()

    // ---------- Wake word ----------

    private val wakeRegex = Regex(
        """\b(lilly|lily|lili|lilli|lilie|leely|leli|lilee)\b|लिली|लीली|लिल्ली""", ic
    )
    private val leadFiller = Regex("""^[\s,.!?]*(?:(?:hey|hi|hello|ok|okay|हे|हाय)\b)?[\s,.!?]*""", ic)

    fun hasWakeWord(t: String) = wakeRegex.containsMatchIn(t)

    fun stripWakeWord(t: String): String =
        t.replace(wakeRegex, " ").replace(leadFiller, "").replace(Regex("\\s+"), " ").trim()

    fun looksLikeCommand(t: String) = t.split(" ").count { it.isNotBlank() } >= 2

    // ---------- Commands ----------

    fun handle(text: String, alternatives: List<String>): Reply {
        val candidates = (listOf(text) + alternatives.map { stripWakeWord(it) })
            .filter { it.isNotBlank() }.distinct()
        val t = text.lowercase(Locale.ROOT).trim()

        callTarget(candidates)?.let { return callReply(it) }
        torchCommand(t)?.let { return it }

        if (Regex("""\b(time|samay|baje|bje)\b|टाइम|समय|बजे""", ic).containsMatchIn(t)) return timeReply()
        if (Regex("""\b(date|tarikh|tareekh|din kya)\b|तारीख|तारीख़|डेट""", ic).containsMatchIn(t)) return dateReply()

        appTarget(t)?.let { return openApp(it) }

        if (Regex("""kaisi ho|kaise ho|how are you|कैसी हो|कैसे हो""", ic).containsMatchIn(t))
            return pick(
                Reply("Main badhiya hoon, aap batao", "मैं बढ़िया हूँ, आप बताओ"),
                Reply("Ekdum mast! Aap kaise ho?", "एकदम मस्त! आप कैसे हो?"),
                Reply("Achhi hoon, bolo kya karna hai", "अच्छी हूँ, बोलो क्या करना है")
            )
        if (Regex("""kaun ho|your name|tumhara naam|कौन हो|नाम क्या""", ic).containsMatchIn(t))
            return Reply("Main Lilly hoon, aapki assistant", "मैं लिली हूँ, आपकी असिस्टेंट")
        if (Regex("""thank|shukriya|dhanyavad|धन्यवाद|शुक्रिया|थैंक""", ic).containsMatchIn(t))
            return pick(
                Reply("Koi baat nahi 😊", "कोई बात नहीं"),
                Reply("Aapka swagat hai", "आपका स्वागत है"),
                Reply("Hamesha haazir hoon", "हमेशा हाज़िर हूँ")
            )
        if (Regex("""\b(bye|ruk jao|band karo|so jao|chup)\b|बाय|रुक जाओ|चुप""", ic).containsMatchIn(t))
            return Reply("Theek hai, zaroorat ho toh 'Lilly' bolna", "ठीक है, ज़रूरत हो तो लिली बोलना")

        return Reply(
            "Ye abhi mujhe nahi aata, par jaldi seekh lungi",
            "ये अभी मुझे नहीं आता, पर जल्दी सीख लूँगी"
        )
    }

    // ---------- Call ----------

    private val callWords = "call|phone|fone|fon|dial|कॉल|काल|फोन|फ़ोन"
    private val doWords = "karo|kar do|kardo|lagao|laga do|kijiye|karna|please|करो|कर दो|लगाओ|लगा दो"
    private val callPatterns = listOf(
        Regex("""^(.+?)\s+(?:ko|को|se|से)\s+(?:$callWords)(?:\s+.*)?$""", ic),
        Regex("""^(?:please\s+)?(?:$callWords)\s+(?:karo\s+|to\s+)?(.+?)(?:\s+(?:ko|को))?(?:\s+(?:$doWords))?$""", ic),
        Regex("""^(.+?)\s+(?:$callWords)\s+(?:$doWords)$""", ic)
    )

    private fun callTarget(candidates: List<String>): String? {
        for (c in candidates) {
            for (p in callPatterns) {
                val m = p.find(c.trim()) ?: continue
                val name = m.groupValues[1].trim().trim(',', '.', '?', '!')
                if (name.isNotBlank()) return name
            }
        }
        return null
    }

    private fun granted(p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun callReply(target: String): Reply {
        // ---- Lock screen security ----
        val locked = isLocked()
        val lockMode = prefs.getString("lock_mode", "fav") ?: "fav"
        if (locked && lockMode == "off") {
            return Reply(
                "Security ke liye lock screen par call band hai. Pehle phone unlock kijiye",
                "सुरक्षा के लिए लॉक स्क्रीन पर कॉल बंद है, पहले फ़ोन अनलॉक कीजिए"
            )
        }
        val favOnly = locked && lockMode == "fav"

        val digits = target.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5 && target.count { it.isLetter() } == 0) {
            if (favOnly) return Reply(
                "Lock screen par number bol kar call nahi hoti. Phone unlock kijiye",
                "लॉक स्क्रीन पर नंबर बोलकर कॉल नहीं होती, फ़ोन अनलॉक कीजिए"
            )
            return Reply("$digits par call kar rahi hoon", "$digits पर कॉल कर रही हूँ") { startCall(digits) }
        }
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            return Reply("Pehle Contacts ki permission dijiye", "पहले कॉन्टैक्ट्स की परमिशन दीजिए") {
                openAppSettings()
            }
        }
        val c = findContact(target, favOnly)
            ?: return if (favOnly) Reply(
                "Lock screen par sirf favourite (⭐) contacts ko call hoti hai. \"$target\" favourite me nahi mila",
                "लॉक स्क्रीन पर सिर्फ़ फ़ेवरेट कॉन्टैक्ट्स को कॉल होती है। $target फ़ेवरेट में नहीं मिला"
            ) else Reply("\"$target\" naam ka contact nahi mila", "$target नाम का कॉन्टैक्ट नहीं मिला")
        return Reply("${c.name} ko call kar rahi hoon", "${c.name} को कॉल कर रही हूँ") { startCall(c.number) }
    }

    private fun startCall(number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        // TelecomManager se call lock screen par bhi bina app khole lag jaati hai
        if (granted(Manifest.permission.CALL_PHONE)) {
            try {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.placeCall(Uri.fromParts("tel", number, null), null)
                return
            } catch (e: Exception) { }
        }
        launch(Intent(Intent.ACTION_DIAL, uri))
    }

    /** Phone locked hai ya nahi */
    fun isLocked(): Boolean =
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(intent) } catch (e: Exception) { }
    }

    private fun findContact(query: String, favOnly: Boolean): Contact? {
        val q = normName(query)
        if (q.isBlank()) return null
        val all = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val n = cur.getString(0) ?: continue
                    val num = cur.getString(1) ?: continue
                    if (favOnly && cur.getInt(2) != 1) continue
                    all.add(Contact(n, num))
                }
            }
        } catch (e: Exception) {
            return null
        }

        var best: Contact? = null
        var bestScore = 0
        for (c in all) {
            val n = normName(c.name)
            if (n.isBlank()) continue
            val words = n.split(" ")
            val qWords = q.split(" ")
            val score = when {
                n == q -> 100
                n.startsWith("$q ") || n.startsWith(q) -> 85
                words.any { it == q } -> 75
                qWords.all { qw -> words.any { it == qw } } -> 70
                n.contains(q) -> 55
                q.length >= 4 && words.any { levenshtein(it, qWords.first()) <= 1 } -> 40
                skeleton(words.first()) == skeleton(qWords.first()) && skeleton(q).length >= 2 -> 30
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    private fun normName(s: String): String {
        val latin = if (s.any { it in 'ऀ'..'ॿ' }) transliterate(s) else s
        return latin.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Vowels hata kar sirf consonants — "Raahul" aur "Rahul" ko match karne ke liye */
    private fun skeleton(s: String) =
        s.replace(Regex("[aeiouyh]"), "").replace(Regex("(.)\\1+"), "$1")

    private fun levenshtein(a: String, b: String): Int {
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

    // Devanagari naam (jaise "राहुल") ko Roman ("rahul") me badalna
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

    private fun transliterate(s: String): String {
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
                        next == '़' -> { /* nukta: ignore */ }
                        next != null && matras.containsKey(next) -> { out.append(matras[next]); i++ }
                        next == '्' -> i++
                        next == null || !(next in 'ऀ'..'ॿ') -> { /* word end: no 'a' */ }
                        else -> out.append("a")
                    }
                }
                vowels.containsKey(ch) -> out.append(vowels[ch])
                ch == 'ं' || ch == 'ँ' -> out.append("n")
                ch == 'ः' -> out.append("h")
                ch == '़' -> {}
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }

    // ---------- Torch ----------

    private fun torchCommand(t: String): Reply? {
        if (!Regex("""torch|flash ?light|flash|टॉर्च|फ्लैश""", ic).containsMatchIn(t)) return null
        val off = Regex("""\b(off|band|bujha|bujhao|bandh)\b|बंद|बुझा""", ic).containsMatchIn(t)
        val ok = setTorch(!off)
        if (!ok) return Reply("Is phone me torch nahi mili", "इस फ़ोन में टॉर्च नहीं मिली")
        return if (off) Reply("Torch band kar di", "टॉर्च बंद कर दी")
        else Reply("Torch jala di", "टॉर्च जला दी")
    }

    private fun setTorch(on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) false else { cm.setTorchMode(id, on); true }
    } catch (e: Exception) {
        false
    }

    // ---------- Time / date ----------

    private fun timeReply(): Reply {
        val cal = Calendar.getInstance()
        var h = cal.get(Calendar.HOUR)
        if (h == 0) h = 12
        val m = cal.get(Calendar.MINUTE)
        val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val display = "Abhi $h:${"%02d".format(m)} $ampm hue hain"
        val spoken = if (m == 0) "अभी $h बजे हैं" else "अभी $h बजकर $m मिनट हुए हैं"
        return Reply(display, spoken)
    }

    private fun dateReply(): Reply {
        val now = Date()
        val display = "Aaj " + SimpleDateFormat("d MMMM yyyy, EEEE", Locale.ENGLISH).format(now) + " hai"
        val spoken = "आज " + SimpleDateFormat("d MMMM, EEEE", Locale("hi", "IN")).format(now) + " है"
        return Reply(display, spoken)
    }

    // ---------- Apps ----------

    private val openPatterns = listOf(
        Regex("""^(?:open|start|launch)\s+(.+)$""", ic),
        Regex("""^(.+?)\s+(?:kholo|khol do|kholdo|open karo|open kar do|open|chalao|chala do|start karo|खोलो|खोल दो|चलाओ|ओपन करो)$""", ic)
    )
    private val appAliases = mapOf(
        "यूट्यूब" to "youtube", "you tube" to "youtube",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "whats app" to "whatsapp",
        "इंस्टाग्राम" to "instagram", "insta" to "instagram",
        "कैमरा" to "camera", "सेटिंग" to "settings", "सेटिंग्स" to "settings", "setting" to "settings",
        "फेसबुक" to "facebook", "गूगल" to "google", "मैप" to "maps", "map" to "maps",
        "गैलरी" to "gallery", "photos" to "photos", "क्रोम" to "chrome",
        "phone pe" to "phonepe", "फोनपे" to "phonepe", "g pay" to "gpay", "google pay" to "gpay", "गूगल पे" to "gpay",
        "पेटीएम" to "paytm", "स्नैपचैट" to "snapchat", "टेलीग्राम" to "telegram", "स्पॉटिफाई" to "spotify",
        "कैलकुलेटर" to "calculator", "घड़ी" to "clock", "कैलेंडर" to "calendar", "मैसेज" to "messages",
        "प्ले स्टोर" to "play store", "जीमेल" to "gmail", "फोटो" to "photos", "फोटोज" to "photos"
    )

    private fun appTarget(t: String): String? {
        for (p in openPatterns) {
            val m = p.find(t) ?: continue
            var name = m.groupValues[1].trim().removeSuffix(" app").removeSuffix(" ऐप").trim()
            name = appAliases[name] ?: name
            if (name.isNotBlank()) return name
        }
        return null
    }

    private fun openApp(name: String): Reply {
        val pm = ctx.packageManager
        val display = name.replaceFirstChar { it.uppercase() }
        val special: Intent? = when (name) {
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            else -> null
        }
        val intent = special ?: run {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
            val target = name.lowercase(Locale.ROOT).replace(" ", "")
            val match = apps.firstOrNull {
                it.loadLabel(pm).toString().lowercase(Locale.ROOT).replace(" ", "") == target
            } ?: apps.firstOrNull {
                it.loadLabel(pm).toString().lowercase(Locale.ROOT).replace(" ", "").contains(target)
            }
            match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        } ?: return Reply("\"$display\" app nahi mila", "$display ऐप नहीं मिला")

        return Reply("$display khol rahi hoon", "$display खोल रही हूँ") { openExternal(intent, display) }
    }

    /**
     * App kholna: 1) jo Lilly screen/popup dikh rahi hai uske zariye (lock ho toh pehle unlock maangti hai)
     * 2) "Display over other apps" ON ho toh seedha  3) warna ek tap wala notification
     */
    private fun openExternal(intent: Intent, label: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val l = LillyBus.launcher
        if (l != null && l(intent)) return
        if (!isLocked() && Settings.canDrawOverlays(ctx)) {
            try { ctx.startActivity(intent); return } catch (e: Exception) { }
        }
        LillyService.notifyOpen(ctx, label, intent)
    }

    private fun openAppSettings() {
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
    }
}
