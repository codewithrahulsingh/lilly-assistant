package com.rahul.lilly

import android.Manifest
import android.app.KeyguardManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Lilly ka dimaag: Parser se samjhi hui command par phone me kaam karta hai. */
class LillyBrain(private val ctx: Context) {

    /**
     * display = screen par likha, spoken = jo Lilly bolegi, action = bolne ke baad ka kaam.
     * askAi = ye sawal AI se poochhna hai. endChat = baat-cheet yahin khatam.
     */
    data class Reply(
        val display: String,
        val spoken: String,
        val action: (() -> Unit)? = null,
        val askAi: String? = null,
        val endChat: Boolean = false
    )

    private data class Contact(val name: String, val number: String)

    private val prefs get() = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)
    private val audio get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun <T> pick(vararg options: T): T = options.random()

    fun hasWakeWord(t: String) = Parser.hasWakeWord(t)
    fun stripWakeWord(t: String) = Parser.stripWakeWord(t)
    fun looksLikeCommand(t: String) = t.split(" ").count { it.isNotBlank() } >= 2
    fun roman(t: String) = Parser.roman(t)
    fun hasAiKey() = !prefs.getString("ai_key", "").isNullOrBlank()

    /** Phone locked hai ya nahi */
    fun isLocked(): Boolean =
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    // ---------- Main ----------

    fun handle(rawText: String, alternatives: List<String>): Reply {
        // Pehla = sabse pakka; baaki Google ke doosre andaze (galat sune naam pakadne ke liye)
        val texts = (listOf(rawText) + alternatives)
            .map { Parser.stripWakeWord(Parser.roman(it)) }
            .filter { it.isNotBlank() }.distinct()
        if (texts.isEmpty()) return Reply("Kuch samajh nahi aaya", "कुछ समझ नहीं आया", endChat = true)
        val cmds = texts.map { Parser.parse(it) }
        val top = cmds.first()
        val main = if (top !is Cmd.Unknown) top else cmds.firstOrNull {
            it is Cmd.Call || it is Cmd.OpenApp || it is Cmd.PlayMusic || it is Cmd.Torch || it is Cmd.Media
        } ?: top

        return when (main) {
            is Cmd.Call -> callReply(cmds.filterIsInstance<Cmd.Call>().map { it.target }.distinct())
            is Cmd.OpenApp -> openApp(cmds.filterIsInstance<Cmd.OpenApp>().map { it.name }.distinct(), main.orMusic)
            is Cmd.PlayMusic -> playMusic(main.query, main.app)
            is Cmd.Media -> mediaReply(main.action)
            is Cmd.Volume -> volumeReply(main.action)
            is Cmd.Torch -> torchReply(main.on)
            Cmd.Time -> timeReply()
            Cmd.Date -> dateReply()
            Cmd.Battery -> batteryReply()
            is Cmd.Chat -> chatReply(main.topic)
            is Cmd.Unknown -> {
                if (hasAiKey()) Reply("Soch rahi hoon…", "", askAi = texts.first())
                else Reply(
                    "Ye mujhe abhi nahi aata. Har baat ka jawab dene ke liye Lilly app me \"AI baat-cheet\" ON kijiye",
                    "ये मुझे अभी नहीं आता। हर बात का जवाब देने के लिए लिली ऐप में ए आई बातचीत ऑन कीजिए",
                    endChat = true
                )
            }
        }
    }

    // ---------- Baat-cheet (offline) ----------

    private fun chatReply(topic: String): Reply = when (topic) {
        "how" -> pick(
            Reply("Main badhiya hoon, aap batao", "मैं बढ़िया हूँ, आप बताओ"),
            Reply("Ekdum mast! Aap kaise ho?", "एकदम मस्त! आप कैसे हो?"),
            Reply("Achhi hoon Rahul, bolo kya karna hai", "अच्छी हूँ राहुल, बोलो क्या करना है")
        )
        "who" -> Reply("Main Lilly hoon, aapki assistant", "मैं लिली हूँ, आपकी असिस्टेंट")
        "thanks" -> pick(
            Reply("Koi baat nahi 😊", "कोई बात नहीं", endChat = true),
            Reply("Hamesha haazir hoon", "हमेशा हाज़िर हूँ", endChat = true)
        )
        "hello" -> Reply("Hello Rahul! Bolo kya karu?", "हेलो राहुल! बोलो क्या करूँ?")
        "joke" -> pick(
            Reply(
                "Teacher: Homework kyun nahi kiya? Student: Sir, light chali gayi thi. Teacher: Toh mombatti jala lete. Student: Machis nahi thi, kyunki pooja ghar me thi aur main nahaya nahi tha!",
                "टीचर: होमवर्क क्यों नहीं किया? स्टूडेंट: सर, लाइट चली गई थी। टीचर: तो मोमबत्ती जला लेते। स्टूडेंट: माचिस पूजा घर में थी, और मैं नहाया नहीं था!"
            ),
            Reply(
                "Doctor: Aapko aaram ki zaroorat hai. Patient: Par doctor sahab, main toh sarkari office me kaam karta hoon!",
                "डॉक्टर: आपको आराम की ज़रूरत है। पेशेंट: पर डॉक्टर साहब, मैं तो सरकारी ऑफिस में काम करता हूँ!"
            )
        )
        "help" -> Reply(
            "Main call laga sakti hoon, apps khol sakti hoon, gaane chala sakti hoon, torch, volume, time aur battery bata sakti hoon",
            "मैं कॉल लगा सकती हूँ, ऐप्स खोल सकती हूँ, गाने चला सकती हूँ, टॉर्च, वॉल्यूम, टाइम और बैटरी बता सकती हूँ"
        )
        else -> Reply("Theek hai, zaroorat ho toh 'Lilly' bolna", "ठीक है, ज़रूरत हो तो लिली बोलना", endChat = true)
    }

    // ---------- Call ----------

    private fun granted(p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun callReply(targets: List<String>): Reply {
        val target = targets.first()

        // ---- Lock screen security ----
        val locked = isLocked()
        val lockMode = prefs.getString("lock_mode", "fav") ?: "fav"
        if (locked && lockMode == "off") {
            return Reply(
                "Security ke liye lock screen par call band hai. Pehle phone unlock kijiye",
                "सुरक्षा के लिए लॉक स्क्रीन पर कॉल बंद है, पहले फ़ोन अनलॉक कीजिए", endChat = true
            )
        }
        val favOnly = locked && lockMode == "fav"

        val digits = target.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5 && target.count { it.isLetter() } == 0) {
            if (favOnly) return Reply(
                "Lock screen par number bol kar call nahi hoti. Phone unlock kijiye",
                "लॉक स्क्रीन पर नंबर बोलकर कॉल नहीं होती, फ़ोन अनलॉक कीजिए", endChat = true
            )
            return Reply("$digits par call kar rahi hoon", "$digits पर कॉल कर रही हूँ", { startCall(digits) }, endChat = true)
        }
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            return Reply(
                "Pehle Contacts ki permission dijiye", "पहले कॉन्टैक्ट्स की परमिशन दीजिए",
                { openAppSettings() }, endChat = true
            )
        }
        val c = findContact(targets, favOnly)
            ?: return if (favOnly) Reply(
                "Lock screen par sirf favourite (⭐) contacts ko call hoti hai. \"$target\" favourite me nahi mila",
                "लॉक स्क्रीन पर सिर्फ़ फ़ेवरेट कॉन्टैक्ट्स को कॉल होती है। $target फ़ेवरेट में नहीं मिला", endChat = true
            ) else Reply("\"$target\" naam ka contact nahi mila", "$target नाम का कॉन्टैक्ट नहीं मिला", endChat = true)
        return Reply("${c.name} ko call kar rahi hoon", "${c.name} को कॉल कर रही हूँ", { startCall(c.number) }, endChat = true)
    }

    private fun startCall(number: String) {
        // TelecomManager se call lock screen par bhi bina app khole lag jaati hai
        if (granted(Manifest.permission.CALL_PHONE)) {
            try {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.placeCall(Uri.fromParts("tel", number, null), null)
                return
            } catch (e: Exception) { }
        }
        launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(intent) } catch (e: Exception) { }
    }

    /** Saare sune gaye naamon me se sabse achha milne wala contact */
    private fun findContact(queries: List<String>, favOnly: Boolean): Contact? {
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
        for ((qi, q) in queries.withIndex()) {
            for (c in all) {
                // Pehle andaze ko thoda zyada bharosa
                val score = Parser.contactScore(q, c.name) - qi
                if (score > bestScore) {
                    bestScore = score
                    best = c
                }
            }
        }
        return if (bestScore >= 30) best else null
    }

    // ---------- Torch ----------

    private fun torchReply(on: Boolean): Reply {
        val ok = try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) false else { cm.setTorchMode(id, on); true }
        } catch (e: Exception) {
            false
        }
        if (!ok) return Reply("Torch nahi chal payi", "टॉर्च नहीं चल पाई", endChat = true)
        return if (on) Reply("Torch jala di", "टॉर्च जला दी", endChat = true)
        else Reply("Torch band kar di", "टॉर्च बंद कर दी", endChat = true)
    }

    // ---------- Time / date / battery ----------

    private fun timeReply(): Reply {
        val cal = Calendar.getInstance()
        var h = cal.get(Calendar.HOUR)
        if (h == 0) h = 12
        val m = cal.get(Calendar.MINUTE)
        val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val display = "Abhi $h:${"%02d".format(m)} $ampm hue hain"
        val spoken = if (m == 0) "अभी $h बजे हैं" else "अभी $h बजकर $m मिनट हुए हैं"
        return Reply(display, spoken, endChat = true)
    }

    private fun dateReply(): Reply {
        val now = Date()
        val display = "Aaj " + SimpleDateFormat("d MMMM yyyy, EEEE", Locale.ENGLISH).format(now) + " hai"
        val spoken = "आज " + SimpleDateFormat("d MMMM, EEEE", Locale("hi", "IN")).format(now) + " है"
        return Reply(display, spoken, endChat = true)
    }

    private fun batteryReply(): Reply {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val extra = if (charging) ", charge ho rahi hai" else ""
        val extraHi = if (charging) ", चार्ज हो रही है" else ""
        return Reply("Battery $pct% hai$extra", "बैटरी $pct प्रतिशत है$extraHi", endChat = true)
    }

    // ---------- Gaane / Spotify ----------

    private val spotify = "com.spotify.music"
    private val youtube = "com.google.android.youtube"

    private fun installed(pkg: String) = ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun mediaKey(code: Int) {
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) { }
    }

    private fun mediaReply(action: String): Reply = when (action) {
        "pause" -> Reply("Gaana rok diya", "गाना रोक दिया", { mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE) }, endChat = true)
        "next" -> Reply("Agla gaana", "अगला गाना", { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }, endChat = true)
        "previous" -> Reply("Pichla gaana", "पिछला गाना", {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }, endChat = true)
        else -> Reply("Gaana chala rahi hoon", "गाना चला रही हूँ", { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY) }, endChat = true)
    }

    private fun playMusic(query: String, app: String): Reply {
        if (query.isBlank()) return mediaReply("resume")
        val shown = query.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        if (app == "youtube" && installed(youtube)) {
            val i = Intent(Intent.ACTION_SEARCH).setPackage(youtube).putExtra("query", query)
            return Reply("YouTube par $shown chala rahi hoon", "यूट्यूब पर $shown चला रही हूँ", {
                openExternal(i, "YouTube")
            }, endChat = true)
        }
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        if (installed(spotify)) {
            i.setPackage(spotify)
            return Reply("Spotify par $shown chala rahi hoon", "स्पॉटिफाई पर $shown चला रही हूँ", {
                openExternal(i, "Spotify")
            }, endChat = true)
        }
        if (i.resolveActivity(ctx.packageManager) != null) {
            return Reply("$shown chala rahi hoon", "$shown चला रही हूँ", { openExternal(i, "Music") }, endChat = true)
        }
        if (installed(youtube)) {
            val y = Intent(Intent.ACTION_SEARCH).setPackage(youtube).putExtra("query", query)
            return Reply("YouTube par $shown chala rahi hoon", "यूट्यूब पर $shown चला रही हूँ", {
                openExternal(y, "YouTube")
            }, endChat = true)
        }
        return Reply("Gaana chalane ke liye Spotify install kijiye", "गाना चलाने के लिए स्पॉटिफाई इंस्टॉल कीजिए", endChat = true)
    }

    private fun volumeReply(action: String): Reply {
        val s = AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(s)
        val step = (max / 5).coerceAtLeast(1)
        val cur = audio.getStreamVolume(s)
        val target = when (action) {
            "up" -> (cur + step).coerceAtMost(max)
            "down" -> (cur - step).coerceAtLeast(0)
            "max" -> max
            else -> 0
        }
        try { audio.setStreamVolume(s, target, AudioManager.FLAG_SHOW_UI) } catch (e: Exception) { }
        return when (action) {
            "up" -> Reply("Awaaz badha di", "आवाज़ बढ़ा दी", endChat = true)
            "down" -> Reply("Awaaz kam kar di", "आवाज़ कम कर दी", endChat = true)
            "max" -> Reply("Awaaz full kar di", "आवाज़ फुल कर दी", endChat = true)
            else -> Reply("Mute kar diya", "म्यूट कर दिया", endChat = true)
        }
    }

    // ---------- Apps ----------

    private fun isAppAllowed(pkg: String): Boolean {
        val set = prefs.getStringSet("allowed_apps", null) ?: return true
        return pkg in set
    }

    private fun openApp(names: List<String>, orMusic: Boolean): Reply {
        val pm = ctx.packageManager
        val first = Parser.appNames(names.first()).first()
        val special: Intent? = when (first) {
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            else -> null
        }
        if (special != null) {
            val label = first.replaceFirstChar { it.uppercase() }
            return Reply("$label khol rahi hoon", "$label खोल रही हूँ", { openExternal(special, label) }, endChat = true)
        }

        // Har sune gaye naam ko har app ke label se milao, sabse achha match chuno
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        var bestPkg: String? = null
        var bestLabel = ""
        var bestScore = 0
        for ((qi, raw) in names.withIndex()) {
            for (variant in Parser.appNames(raw)) {
                for ((pkg, label) in apps) {
                    // barabar score par chhota naam jeete ("YouTube" > "YouTube Music")
                    val score = Parser.appScore(variant, label) * 100 - qi * 150 - label.length
                    if (Parser.appScore(variant, label) >= 50 && score > bestScore) {
                        bestScore = score
                        bestPkg = pkg
                        bestLabel = label
                    }
                }
            }
        }

        val pkg = bestPkg
        if (pkg == null) {
            if (orMusic) return playMusic(names.first(), "")
            val said = names.first().replaceFirstChar { it.uppercase() }
            return Reply("\"$said\" naam ka app nahi mila", "$said नाम का ऐप नहीं मिला", endChat = true)
        }
        if (!isAppAllowed(pkg)) {
            return Reply(
                "$bestLabel kholne ki permission nahi di hai. Lilly app me allow kijiye",
                "$bestLabel खोलने की परमिशन नहीं दी है", endChat = true
            )
        }
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return Reply("$bestLabel nahi khul paya", "$bestLabel नहीं खुल पाया", endChat = true)
        return Reply("$bestLabel khol rahi hoon", "$bestLabel खोल रही हूँ", { openExternal(intent, bestLabel) }, endChat = true)
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
