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
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
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
        val endChat: Boolean = false,
        /** Kaam nahi hua (naam/app nahi mila, samajh nahi aaya) — seekhne ke liye */
        val failed: Boolean = false
    )

    private val prefs get() = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)
    private val audio get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Lilly ki yaaddasht: aapke baare me baatein + purane jawab (phone par hi save) */
    val memory = Memory(
        load = { prefs.getString("memory", null) },
        save = { prefs.edit().putString("memory", it).apply() }
    )

    /** "Kaun sa number?" poochha hai aur jawab ka intezaar hai */
    private class PendingCall(
        val target: String, val options: List<CallChoice.Phone>, val byName: Boolean,
        val speaker: Boolean, val favOnly: Boolean, val at: Long, var asked: Int = 0
    )
    private var pendingCall: PendingCall? = null

    /** Lilly ki seekh (phone par hi save) */
    val learner = Learner(
        load = { prefs.getString("learned", null) },
        save = { prefs.edit().putString("learned", it).apply() }
    )

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

    fun handle(rawText: String, alternatives: List<String>, allowAi: Boolean = true): Reply {
        learner.reload() // app ki screen se kuch bhulaya gaya ho toh
        memory.reload()
        // Pehla = sabse pakka; baaki Google ke doosre andaze (galat sune naam pakadne ke liye)
        val heard = (listOf(rawText) + alternatives)
            .map { Parser.stripWakeWord(Parser.roman(it)) }
            .filter { it.isNotBlank() }.distinct()
        if (heard.isEmpty()) return Reply("Sorry, I didn't get that", "कुछ समझ नहीं आया", endChat = true, failed = true)
        answerPendingCall(heard)?.let { return it }
        // Seekhe hue phrase ("good night") ko asli command ("turn off the torch") se badlo
        val texts = heard.map { learner.rewrite(it) ?: it }.distinct()
        val reply = decide(texts, allowAi)
        if (reply.failed) learner.noteFailure(heard.first())
        else if (reply.action != null) learner.noteSuccess(texts.first())
        return reply
    }

    /**
     * AI ne kisi nayi baat ko phone ki command samjha ("boss ko ghanti maaro" -> "call boss").
     * Chal gayi toh yaad rakho — agli baar bina AI ke, turant.
     */
    fun handleFromAi(question: String, command: String): Reply {
        val r = handle(command, emptyList(), allowAi = false)
        if (r.action != null && !r.failed) learner.teach(question, command, taught = false)
        return r
    }

    private fun decide(texts: List<String>, allowAi: Boolean): Reply {
        val cmds = texts.map { Parser.parse(it) }
        val top = cmds.first()
        val main = if (top !is Cmd.Unknown) top else cmds.firstOrNull {
            it is Cmd.Call || it is Cmd.OpenApp || it is Cmd.PlayMusic || it is Cmd.Torch || it is Cmd.Media
        } ?: top

        return when (main) {
            is Cmd.Call -> callReply(cmds.filterIsInstance<Cmd.Call>().map { it.target }.distinct(), main.speaker)
            is Cmd.Remember -> {
                if (memory.remember(main.fact)) Reply("Okay, I'll remember: ${main.fact}", "ठीक है, याद रख लिया", endChat = true)
                else Reply("I already remember that", "ये मुझे पहले से याद है", endChat = true)
            }
            is Cmd.ForgetFact -> {
                if (memory.forgetMatching(main.what) > 0) Reply("Done, I've forgotten that", "ठीक है, भूल गई", endChat = true)
                else Reply("I don't have anything like that saved", "ऐसी कोई बात मुझे याद नहीं है", endChat = true)
            }
            Cmd.Recall -> recallReply()
            is Cmd.OpenApp -> openApp(cmds.filterIsInstance<Cmd.OpenApp>().map { it.name }.distinct(), main.orMusic)
            is Cmd.PlayMusic -> playMusic(main.query, main.app)
            is Cmd.Media -> mediaReply(main.action)
            is Cmd.Volume -> volumeReply(main.action)
            is Cmd.Torch -> torchReply(main.on)
            Cmd.Time -> timeReply()
            Cmd.Date -> dateReply()
            Cmd.Battery -> batteryReply()
            is Cmd.Chat -> chatReply(main.topic)
            is Cmd.Teach -> teachReply(main)
            is Cmd.Unknown -> {
                // Ye sawaal pehle poochha tha? Saved jawab turant (bina internet, bina limit)
                val cached = if (allowAi) memory.cachedAnswer(texts.first(), Parser.isEnglish(texts.first())) else null
                if (cached != null) Reply(cached.display, cached.spoken)
                else if (allowAi && hasAiKey()) Reply("Thinking…", "", askAi = texts.first())
                else Reply(
                    if (allowAi) "I can't do that yet. Turn on AI chat in the Lilly app and I can answer anything"
                    else "I understood, but I couldn't do that on the phone",
                    "ये मुझे अभी नहीं आता",
                    endChat = true, failed = true
                )
            }
        }
    }

    // ---------- Yaaddasht ----------

    private fun recallReply(): Reply {
        val f = memory.facts()
        if (f.isEmpty()) return Reply(
            "I don't have anything saved about you yet. Say: remember that…",
            "अभी आपके बारे में कुछ याद नहीं है। ऐसे बोलिए: याद रखना कि…", endChat = true
        )
        val top = f.takeLast(5).reversed()
        val list = top.joinToString("\n") { "• ${it.text}" }
        val more = if (f.size > 5) "\n…and ${f.size - 5} more in the Lilly app" else ""
        return Reply(
            "Here's what I remember:\n$list$more",
            "मुझे ये याद है: " + top.joinToString("। ") { it.text },
            endChat = true
        )
    }

    // ---------- Sikhana ----------

    private fun teachReply(t: Cmd.Teach): Reply {
        val action = Parser.parse(t.action)
        if (action is Cmd.Unknown || action is Cmd.Teach || t.phrase.isBlank()) {
            return Reply(
                "I didn't understand what to do. Try: When I say good night, turn off the torch",
                "मुझे समझ नहीं आया क्या करना है। ऐसे बोलिए: जब मैं गुड नाइट बोलूँ तो टॉर्च बंद करना",
                endChat = true, failed = true
            )
        }
        learner.teach(t.phrase, t.action)
        return Reply(
            "Got it! When you say “${t.phrase}”, I'll ${t.action}",
            "ठीक है, याद रख लिया। जब आप ${t.phrase} बोलेंगे, मैं वो कर दूँगी",
            endChat = true
        )
    }

    // ---------- Baat-cheet (offline) ----------

    private fun chatReply(topic: String): Reply = when (topic) {
        "how" -> pick(
            Reply("I'm doing great, how about you?", "मैं बढ़िया हूँ, आप बताओ"),
            Reply("Feeling awesome! How are you?", "एकदम मस्त! आप कैसे हो?"),
            Reply("I'm good, Rahul. What can I do for you?", "अच्छी हूँ राहुल, बोलो क्या करना है")
        )
        "who" -> Reply("I'm Lilly, your assistant", "मैं लिली हूँ, आपकी असिस्टेंट")
        "thanks" -> pick(
            Reply("You're welcome!", "कोई बात नहीं", endChat = true),
            Reply("Always here for you", "हमेशा हाज़िर हूँ", endChat = true)
        )
        "hello" -> Reply("Hello Rahul! What can I do for you?", "हेलो राहुल! बोलो क्या करूँ?")
        "joke" -> pick(
            Reply(
                "Teacher: Why didn't you do your homework? Student: Sir, the power went out. Teacher: You could have lit a candle. Student: The matches were in the prayer room, and I hadn't taken a bath!",
                "टीचर: होमवर्क क्यों नहीं किया? स्टूडेंट: सर, लाइट चली गई थी। टीचर: तो मोमबत्ती जला लेते। स्टूडेंट: माचिस पूजा घर में थी, और मैं नहाया नहीं था!"
            ),
            Reply(
                "Doctor: You need some rest. Patient: But doctor, I already work in a government office!",
                "डॉक्टर: आपको आराम की ज़रूरत है। पेशेंट: पर डॉक्टर साहब, मैं तो सरकारी ऑफिस में काम करता हूँ!"
            )
        )
        "help" -> Reply(
            "I can make calls, open apps, play songs, control the torch and volume, and tell you the time and battery",
            "मैं कॉल लगा सकती हूँ, ऐप्स खोल सकती हूँ, गाने चला सकती हूँ, टॉर्च, वॉल्यूम, टाइम और बैटरी बता सकती हूँ"
        )
        else -> Reply("Okay, just say Lilly when you need me", "ठीक है, ज़रूरत हो तो लिली बोलना", endChat = true)
    }

    // ---------- Call ----------

    private fun granted(p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun callReply(targets: List<String>, speaker: Boolean): Reply {
        pendingCall = null
        val target = targets.first()

        // ---- Lock screen security ----
        val locked = isLocked()
        val lockMode = prefs.getString("lock_mode", "fav") ?: "fav"
        if (locked && lockMode == "off") {
            return Reply(
                "For security, calls are off on the lock screen. Please unlock your phone first",
                "सुरक्षा के लिए लॉक स्क्रीन पर कॉल बंद है, पहले फ़ोन अनलॉक कीजिए", endChat = true
            )
        }
        val favOnly = locked && lockMode == "fav"
        val onSpk = if (speaker) " on speaker" else ""
        val onSpkHi = if (speaker) " स्पीकर पर" else ""

        val digits = target.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5 && target.count { it.isLetter() } == 0) {
            if (favOnly) return Reply(
                "I can't dial a spoken number on the lock screen. Please unlock your phone",
                "लॉक स्क्रीन पर नंबर बोलकर कॉल नहीं होती, फ़ोन अनलॉक कीजिए", endChat = true
            )
            return Reply("Calling $digits$onSpk", "$digits पर$onSpkHi कॉल कर रही हूँ", { startCall(digits, speaker) }, endChat = true)
        }
        if (!granted(Manifest.permission.READ_CONTACTS)) {
            return Reply(
                "Please allow the Contacts permission first", "पहले कॉन्टैक्ट्स की परमिशन दीजिए",
                { openAppSettings() }, endChat = true
            )
        }
        val rows = loadPhones(favOnly)
        val notFound = if (favOnly) Reply(
            "On the lock screen I can only call your favourite contacts. $target is not in favourites",
            "लॉक स्क्रीन पर सिर्फ़ फ़ेवरेट कॉन्टैक्ट्स को कॉल होती है। $target फ़ेवरेट में नहीं मिला", endChat = true
        ) else Reply("I couldn't find a contact named $target", "$target नाम का कॉन्टैक्ट नहीं मिला", endChat = true, failed = true)
        if (rows.isEmpty()) return notFound

        // Pehle se seekha hua naam ("rohit" -> "Rohit Sharma", 2+ baar chuna gaya)
        val learned = targets.firstNotNullOfOrNull { learner.contactFor(it) }
        if (learned != null && rows.any { it.name == learned }) return callByName(target, learned, rows, speaker, favOnly)

        // Kaun-kaun se contact ho sakte hain ("Abhishek" -> 16 Abhishek)
        val tied = CallChoice.candidates(targets, rows.map { it.name }) { learner.contactBonus(it) }
        if (tied.isEmpty()) return notFound
        if (tied.size == 1) return callByName(target, tied[0], rows, speaker, favOnly)

        val options = tied.map { n -> rows.first { it.name == n } }
        val pc = PendingCall(target, options, byName = true, speaker = speaker, favOnly = favOnly, at = System.currentTimeMillis())
        pendingCall = pc
        return askChoice(pc)
    }

    private fun callByName(target: String, name: String, rows: List<CallChoice.Phone>, speaker: Boolean, favOnly: Boolean): Reply {
        val nums = CallChoice.dedupe(rows.filter { it.name == name })
        if (nums.size == 1) return callNumber(target, nums[0], speaker, chosen = false)
        // Pehle kai baar chuna hua number
        learner.numberFor(name)?.let { known ->
            nums.firstOrNull { CallChoice.digitsKey(it.number) == known }?.let { return callNumber(target, it, speaker, chosen = false) }
        }
        val pc = PendingCall(target, nums.take(5), byName = false, speaker = speaker, favOnly = favOnly, at = System.currentTimeMillis())
        pendingCall = pc
        return askChoice(pc)
    }

    private val ordinalHi = listOf("पहला", "दूसरा", "तीसरा", "चौथा", "पाँचवाँ")
    private val ordinalEn = listOf("first", "second", "third", "fourth", "fifth")

    /** Siri jaisa: screen par list, aur bol kar poochho */
    private fun askChoice(pc: PendingCall): Reply {
        val o = pc.options
        if (pc.byName) {
            val shown = o.take(10)
            val lines = shown.mapIndexed { i, p -> "${i + 1}. ${p.name}" }.joinToString("\n")
            val more = if (o.size > shown.size) "\n…and ${o.size - shown.size} more" else ""
            val who = pc.target.replaceFirstChar { it.uppercase() }
            val names = o.map { CallChoice.speakName(it.name) }
            if (o.size <= 3) {
                return Reply(
                    "I found ${o.size} contacts for “$who”:\n$lines\nWhich one?",
                    "${o.size} कॉन्टैक्ट मिले: " + names.joinToString(", या ") + "। किसको कॉल करूँ?",
                    endChat = false
                )
            }
            // Bahut saare: sab bolna lamba hoga — screen par list, bolo sirf kuch naam
            val ex = names.take(3)
            return Reply(
                "I found ${o.size} contacts named “$who”. Which one?\n$lines$more\n" +
                    "Say the second name (like “${ex[1].substringAfter(' ', ex[1])}”) or a number.",
                "$who नाम के ${o.size} कॉन्टैक्ट हैं। कौन सा? जैसे ${ex[0]}, ${ex[1]}, या ${ex[2]}। दूसरा नाम बोलिए।",
                endChat = false
            )
        }
        val name = o.first().name
        val lines = o.mapIndexed { i, p -> CallChoice.numberLine(i, p) }.joinToString("\n")
        // Do number ka label same ho ("Mobile", "Mobile") toh aakhri ank bhi bolo
        val sameLabels = o.map { it.label.lowercase() }.distinct().size < o.size
        fun spokenOpt(i: Int, p: CallChoice.Phone, hi: Boolean): String {
            val ord = if (hi) ordinalHi.getOrElse(i) { "${i + 1}" } else ordinalEn.getOrElse(i) { "${i + 1}" }
            val end = CallChoice.lastDigits(p.number).toCharArray().joinToString(" ")
            val lab = p.label.ifBlank { "number" }
            return if (sameLabels || p.label.isBlank()) {
                if (hi) "$ord $lab, जो $end पर खत्म होता है" else "$ord, $lab ending $end"
            } else "$ord $lab"
        }
        return Reply(
            "$name has ${o.size} numbers:\n$lines\nWhich one?",
            "${CallChoice.speakName(name)} के ${o.size} नंबर हैं: " + o.mapIndexed { i, p -> spokenOpt(i, p, true) }.joinToString(", ") + "। किस पर कॉल करूँ?",
            endChat = false
        )
    }

    /** "Kaun sa?" ka jawab aaya ho toh wahi sambhalo; nahi toh null (normal command) */
    private fun answerPendingCall(heard: List<String>): Reply? {
        val pc = pendingCall ?: return null
        if (System.currentTimeMillis() - pc.at > 60_000) { pendingCall = null; return null }
        val ans = heard.first()
        if (CallChoice.isCancel(ans)) {
            pendingCall = null
            return Reply("Okay, I won't call", "ठीक है, कॉल नहीं करती", endChat = true)
        }
        val idx = heard.firstNotNullOfOrNull { CallChoice.pick(it, pc.options, pc.byName) }
        if (idx != null) {
            pendingCall = null
            val chosen = pc.options[idx]
            return if (pc.byName) {
                callByName(pc.target, chosen.name, loadPhones(pc.favOnly), pc.speaker, pc.favOnly)
            } else callNumber(pc.target, chosen, pc.speaker, chosen = true)
        }
        // Koi nayi command boli ("time kya hua") toh wahi karo
        val cmd = Parser.parse(learner.rewrite(ans) ?: ans)
        if (cmd !is Cmd.Unknown && cmd !is Cmd.Chat) { pendingCall = null; return null }
        if (pc.asked < 1) {
            pc.asked++
            val r = askChoice(pc)
            val hint = if (pc.byName) "Say the name, or first / second." else "Say first, second, or the last digits."
            val hintHi = if (pc.byName) "नाम बोलिए, या पहला, दूसरा।" else "पहला, दूसरा, या आख़िरी अंक बोलिए।"
            return Reply("Sorry, I didn't get that. ${r.display}\n($hint)", "माफ़ कीजिए, समझ नहीं आया। $hintHi", endChat = false)
        }
        pendingCall = null
        return Reply("Okay, let's leave it for now", "ठीक है, अभी रहने देते हैं", endChat = true)
    }

    private fun callNumber(target: String, p: CallChoice.Phone, speaker: Boolean, chosen: Boolean): Reply {
        val onSpk = if (speaker) " on speaker" else ""
        val onSpkHi = if (speaker) " स्पीकर पर" else ""
        val lab = if (chosen && p.label.isNotBlank()) " (${p.label})" else ""
        return Reply("Calling ${p.name}$lab$onSpk", "${CallChoice.speakName(p.name)} को$onSpkHi कॉल कर रही हूँ", {
            learner.rememberContact(target, p.name)
            learner.rememberNumber(p.name, CallChoice.digitsKey(p.number), p.label.ifBlank { "Number" })
            startCall(p.number, speaker)
        }, endChat = true)
    }

    /** Saare contacts ke number, label ke saath (Mobile / Work / Home) */
    private fun loadPhones(favOnly: Boolean): List<CallChoice.Phone> {
        val out = ArrayList<CallChoice.Phone>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
        )
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC"
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val n = cur.getString(0) ?: continue
                    val num = cur.getString(1) ?: continue
                    val starred = cur.getInt(2) == 1
                    if (favOnly && !starred) continue
                    val label = try {
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(ctx.resources, cur.getInt(3), cur.getString(4)).toString()
                    } catch (e: Exception) { "" }
                    out.add(CallChoice.Phone(n, num, label, starred))
                }
            }
        } catch (e: Exception) { }
        return out
    }

    private fun startCall(number: String, speaker: Boolean = false) {
        // TelecomManager se call lock screen par bhi bina app khole lag jaati hai
        if (granted(Manifest.permission.CALL_PHONE)) {
            try {
                val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val extras = Bundle()
                if (speaker) extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
                tm.placeCall(Uri.fromParts("tel", number, null), extras)
                if (speaker) forceSpeaker()
                return
            } catch (e: Exception) { }
        }
        launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
    }

    private val speakerHandler = Handler(Looper.getMainLooper())

    /**
     * Kuch phones ka dialer "speaker se shuru karo" wala signal nahi maanta,
     * isliye call lagne ke baad khud bhi speaker on karte hain, aur call khatam hone par wapas.
     */
    private fun forceSpeaker() {
        var tries = 0
        var setByUs = false
        val step = object : Runnable {
            override fun run() {
                tries++
                val inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
                if (inCall && tries <= 4) {
                    setSpeaker(true)
                    setByUs = true
                }
                if (inCall || tries <= 4) {
                    // Call chal rahi hai: har 5 sec dekho (zyada se zyada ~3 ghante)
                    if (tries < 2200) speakerHandler.postDelayed(this, if (tries <= 4) 1500 else 5000)
                } else if (setByUs) {
                    setSpeaker(false)
                }
            }
        }
        speakerHandler.postDelayed(step, 1500)
    }

    @Suppress("DEPRECATION")
    private fun setSpeaker(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (on) {
                    audio.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { audio.setCommunicationDevice(it) }
                } else audio.clearCommunicationDevice()
            }
        } catch (e: Exception) { }
        try { audio.isSpeakerphoneOn = on } catch (e: Exception) { }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(intent) } catch (e: Exception) { }
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
        if (!ok) return Reply("I couldn't control the torch", "टॉर्च नहीं चल पाई", endChat = true)
        return if (on) Reply("Torch is on", "टॉर्च जला दी", endChat = true)
        else Reply("Torch is off", "टॉर्च बंद कर दी", endChat = true)
    }

    // ---------- Time / date / battery ----------

    private fun timeReply(): Reply {
        val cal = Calendar.getInstance()
        var h = cal.get(Calendar.HOUR)
        if (h == 0) h = 12
        val m = cal.get(Calendar.MINUTE)
        val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val display = "It's $h:${"%02d".format(m)} $ampm"
        val spoken = if (m == 0) "अभी $h बजे हैं" else "अभी $h बजकर $m मिनट हुए हैं"
        return Reply(display, spoken, endChat = true)
    }

    private fun dateReply(): Reply {
        val now = Date()
        val display = "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(now)
        val spoken = "आज " + SimpleDateFormat("d MMMM, EEEE", Locale("hi", "IN")).format(now) + " है"
        return Reply(display, spoken, endChat = true)
    }

    private fun batteryReply(): Reply {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val extra = if (charging) " and charging" else ""
        val extraHi = if (charging) ", चार्ज हो रही है" else ""
        return Reply("Battery is at $pct percent$extra", "बैटरी $pct प्रतिशत है$extraHi", endChat = true)
    }

    // ---------- Gaane / Spotify ----------

    private val spotify = "com.spotify.music"
    private val youtube = "com.google.android.youtube"
    private val ytMusic = "com.google.android.apps.youtube.music"

    private fun installed(pkg: String) = ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun mediaKey(code: Int) {
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) { }
    }

    private fun mediaReply(action: String): Reply = when (action) {
        "pause" -> Reply("Paused", "गाना रोक दिया", { mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE) }, endChat = true)
        "next" -> Reply("Next song", "अगला गाना", { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }, endChat = true)
        "previous" -> Reply("Previous song", "पिछला गाना", {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }, endChat = true)
        else -> Reply("Playing music", "गाना चला रही हूँ", { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY) }, endChat = true)
    }

    private fun playMusic(query: String, app: String): Reply {
        if (query.isBlank()) return mediaReply("resume")
        val shown = query.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        // Jo app bola wahi; na bola ho toh jo aapne pichhli baar khud chuna tha
        val want = app.ifBlank { learner.musicApp ?: "" }
        val remember = { if (app.isNotBlank()) learner.setMusicApp(app) }

        if (want == "spotify" && installed(spotify)) return spotifyReply(query, shown, remember)
        if (want == "youtube" && installed(youtube)) return youtubeReply(query, shown, remember)
        if (app == "spotify") {
            return Reply("Spotify isn't installed on this phone", "इस फ़ोन में स्पॉटिफाई नहीं है", endChat = true, failed = true)
        }
        if (installed(youtube)) return youtubeReply(query, shown, remember)
        if (installed(ytMusic)) {
            // YouTube Music ye intent milte hi gaana chala deta hai
            val i = searchIntent(query).setPackage(ytMusic)
            return Reply("Playing $shown on YouTube Music", "यूट्यूब म्यूज़िक पर $shown चला रही हूँ", {
                openExternal(i, "YouTube Music")
            }, endChat = true)
        }
        if (installed(spotify)) return spotifyReply(query, shown, remember)
        val i = searchIntent(query)
        if (i.resolveActivity(ctx.packageManager) != null) {
            return Reply("Playing $shown", "$shown चला रही हूँ", { openExternal(i, "Music") }, endChat = true)
        }
        return Reply("Please install Spotify or YouTube to play songs", "गाना चलाने के लिए स्पॉटिफाई इंस्टॉल कीजिए", endChat = true, failed = true)
    }

    private fun searchIntent(query: String) = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        .putExtra(SearchManager.QUERY, query)
        .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")

    /**
     * Spotify: "play from search" bhejo. Spotify band ho toh pehli baar aksar sirf search dikhata hai,
     * isliye khulne ke baad dekhte hain gaana baja ya nahi — nahi baja toh wahi dobara bhejte hain.
     */
    private fun spotifyReply(query: String, shown: String, remember: () -> Unit): Reply =
        Reply("Playing $shown on Spotify", "स्पॉटिफाई पर $shown चला रही हूँ", {
            remember()
            val i = searchIntent(query).setPackage(spotify)
            openExternal(i, "Spotify")
            val h = Handler(Looper.getMainLooper())
            for (delay in listOf(3500L, 7500L)) {
                h.postDelayed({
                    if (!audio.isMusicActive && Settings.canDrawOverlays(ctx)) {
                        try { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                    }
                }, delay)
            }
        }, endChat = true)

    /** YouTube: pehla result dhoondh kar seedha wahi video chalao (sirf search par na ruke) */
    private fun youtubeReply(query: String, shown: String, remember: () -> Unit = {}): Reply =
        Reply("Playing $shown on YouTube", "यूट्यूब पर $shown चला रही हूँ", {
            remember()
            Thread {
                val id = MusicControl.firstYouTubeVideo("$query song")
                Handler(Looper.getMainLooper()).post {
                    val intent = if (id != null) {
                        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).setPackage(youtube)
                    } else {
                        // Internet/ID na mile toh kam se kam search khol do
                        Intent(Intent.ACTION_SEARCH).setPackage(youtube).putExtra("query", query)
                    }
                    openExternal(intent, "YouTube")
                }
            }.start()
        }, endChat = true)

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
            "up" -> Reply("Volume up", "आवाज़ बढ़ा दी", endChat = true)
            "down" -> Reply("Volume down", "आवाज़ कम कर दी", endChat = true)
            "max" -> Reply("Volume at maximum", "आवाज़ फुल कर दी", endChat = true)
            else -> Reply("Muted", "म्यूट कर दिया", endChat = true)
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
            return Reply("Opening $label", "$label खोल रही हूँ", { openExternal(special, label) }, endChat = true)
        }

        // Har sune gaye naam ko har app ke label se milao, sabse achha match chuno
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        var bestPkg: String? = null
        var bestLabel = ""
        var bestScore = 0
        // Pehle se seekha hua app ("insta" -> Instagram)
        names.firstNotNullOfOrNull { learner.appFor(it) }?.let { known ->
            for ((p, l) in apps) {
                if (p == known) { bestPkg = p; bestLabel = l; bestScore = Int.MAX_VALUE; break }
            }
        }
        for ((qi, raw) in names.withIndex()) {
            for (variant in Parser.appNames(raw)) {
                for ((pkg, label) in apps) {
                    // barabar score par chhota naam jeete ("YouTube" > "YouTube Music")
                    val score = Parser.appScore(variant, label) * 100 - qi * 150 - label.length + learner.appBonus(pkg) * 100
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
            return Reply("I couldn't find an app called $said", "$said नाम का ऐप नहीं मिला", endChat = true, failed = true)
        }
        if (!isAppAllowed(pkg)) {
            return Reply(
                "$bestLabel is not allowed. You can allow it in the Lilly app",
                "$bestLabel खोलने की परमिशन नहीं दी है", endChat = true
            )
        }
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return Reply("I couldn't open $bestLabel", "$bestLabel नहीं खुल पाया", endChat = true)
        val said = names.first()
        val label = bestLabel
        return Reply("Opening $label", "$label खोल रही हूँ", {
            learner.rememberApp(said, pkg, label)
            openExternal(intent, label)
        }, endChat = true)
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
