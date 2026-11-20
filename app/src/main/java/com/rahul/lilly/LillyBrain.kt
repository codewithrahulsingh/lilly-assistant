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
import android.provider.AlarmClock
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
        val failed: Boolean = false,
        /** English me bolne ke liye chhota roop (display lamba ho tab) */
        val spokenEn: String? = null,
        /** Internet wala kaam (mausam…): service "Ek second" bol kar background me chalaati hai */
        val later: (() -> Reply)? = null,
        /** Chhota kaam (torch, volume, time…) — jawab ke baad thodi der bina "Lilly" ke sunti rahe */
        val stay: Boolean = false
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
        val speaker: Boolean, val favOnly: Boolean, var at: Long, var asked: Int = 0, var page: Int = 0,
        /** "call", "wa_msg", "wa_call", "wa_video" */
        val action: String = "call", val text: String = ""
    )

    /** WhatsApp: kya likhna hai poochha / bhejne se pehle "bhej doon?" */
    private class PendingWa(val target: String, val phone: CallChoice.Phone, var text: String, var at: Long)
    private var waAskText: PendingWa? = null
    private var waConfirm: PendingWa? = null

    /** User ne asal me kya bola (Hindi lipi samet) — WhatsApp message jaisa bola waisa likhne ke liye */
    private var lastOriginal = ""
    private var pendingCall: PendingCall? = null

    /** "Kaun sa?" poochh kar jawab ka intezaar — service chup rehne par session band nahi karegi */
    fun awaitingChoice(): Boolean {
        val now = System.currentTimeMillis()
        val pc = pendingCall
        if (pc != null && now - pc.at < 90_000) return true
        val w = waAskText ?: waConfirm ?: return false
        return now - w.at < 90_000
    }

    fun cancelChoice() { pendingCall = null; waAskText = null; waConfirm = null }

    /** Chup rehne par halka sa yaad dilana */
    fun choicePrompt(): Reply {
        val pc = pendingCall
        val more = pc != null && pc.byName && (pc.page + 1) * PAGE < pc.options.size
        return if (more) Reply("Say a number, or say more", "नंबर बोलिए, या 'और' बोलिए", endChat = false)
        else Reply("Say a number, or the name", "नंबर या नाम बोलिए", endChat = false)
    }

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
    fun hasAiKey() = !prefs.getString("ai_key", "").isNullOrBlank() || !prefs.getString("ai2_key", "").isNullOrBlank()

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
        lastOriginal = Parser.stripWakeWord(alternatives.firstOrNull { it.isNotBlank() } ?: rawText)
        answerPendingWa(heard)?.let { return it }
        answerPendingCall(heard)?.let { return it }
        answerPendingAlarm(heard)?.let { return it }
        answerPendingReply(heard)?.let { return it }
        answerAfterRead(heard)?.let { return it }
        // Google ke kisi bhi andaze me gaali ho toh pakdo
        abuseReply(heard.firstOrNull { Abuse.has(Parser.normalize(it)) || Abuse.toggle(Parser.normalize(it)) != null } ?: heard.first())?.let { return it }
        // Seekhe hue phrase ("good night") ko asli command ("turn off the torch") se badlo
        val texts = heard.map { learner.rewrite(it) ?: it }.distinct()
        val reply = twoAtOnce(texts.first()) ?: decide(texts, allowAi)
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

    /**
     * Gaali mode: "gaali mode on/off"; gaali aaye toh AI ke paas nahi — mode ON ho toh gaali me jawab,
     * OFF ho toh shaant jawab. Gaali ke saath koi kaam bhi bola ho ("bc torch jala") toh kaam bhi.
     */
    private fun abuseReply(text: String): Reply? {
        val t = Parser.normalize(text)
        Abuse.toggle(t)?.let { on ->
            prefs.edit().putBoolean("abuse_mode", on).apply()
            return if (on) Reply("Gaali mode ON 😈 — ab gaali doge toh waisa hi jawab milega", "गाली मोड ऑन। अब गाली दोगे तो वैसा ही जवाब मिलेगा", endChat = true)
            else Reply("Gaali mode OFF — ab tameez se", "गाली मोड बंद। अब तमीज़ से बात करूँगी", endChat = true)
        }
        if (!Abuse.has(t)) return null
        val on = prefs.getBoolean("abuse_mode", false)
        val en = Parser.isEnglish(text)
        val rest = Abuse.strip(t)
        if (rest.split(" ").count { it.isNotBlank() } >= 1) {
            val c = Parser.parse(rest)
            if (c !is Cmd.Unknown && c !is Cmd.Chat) {
                val r = decide(listOf(rest), false)
                if (!on || r.failed) return r
                val (d, sp) = Abuse.jab()
                return r.copy(display = d + "\n" + r.display, spoken = sp + " " + r.spoken, spokenEn = null)
            }
        }
        // Gaali ke saath koi sawaal ("bc ye bata cricket score kya hai") — AI se jawab, tapori andaaz me
        if (on && rest.split(" ").count { it.length > 1 } >= 3 && hasAiKey()) {
            Abuse.roughAnswer = true
            return Reply("Thinking…", "", askAi = rest)
        }
        val (d, sp) = Abuse.reply(on, en, t)
        return Reply(d, sp, endChat = false)
    }

    /** "torch jalao aur volume badhao" — dono kaam ek ke baad ek */
    private fun twoAtOnce(text: String): Reply? {
        val (a, b) = Extras.splitTwo(Parser.normalize(text)) { Parser.parse(it) } ?: return null
        val ra = decide(listOf(a), false)
        val rb = decide(listOf(b), false)
        fun done(r: Reply) = !r.failed && r.askAi == null && r.later == null && (r.endChat || r.action != null)
        if (!done(ra) || !done(rb)) return null
        val main = Handler(Looper.getMainLooper())
        return Reply(
            ra.display + "\n" + rb.display, ra.spoken + "। " + rb.spoken,
            action = {
                ra.action?.invoke()
                rb.action?.let { main.postDelayed({ it() }, 900) }
            },
            endChat = true,
            spokenEn = listOfNotNull(ra.spokenEn ?: ra.display, rb.spokenEn ?: rb.display).joinToString(". ")
        )
    }

    private fun decide(texts: List<String>, allowAi: Boolean): Reply {
        val r = decideInner(texts, allowAi)
        val c = Parser.parse(texts.first())
        val quick = c is Cmd.Torch || c is Cmd.Volume || c == Cmd.Time || c == Cmd.Date || c == Cmd.Battery ||
            (c is ListCmd) || (c is CalendarCmd && c.op == "read") || (c is CallLogCmd && (c.kind == "missed" || c.kind == "last"))
        return if (quick && !r.failed && r.askAi == null) r.copy(stay = true) else r
    }

    private fun decideInner(texts: List<String>, allowAi: Boolean): Reply {
        val cmds = texts.map { Parser.parse(it) }
        val top = cmds.first()
        // Google ke doosre andazon me se jo pehla asli kaam samjhe (Siri jaisa) — AI tak tabhi jaao jab koi na samjhe
        val main = if (top !is Cmd.Unknown && top !is Cmd.Chat) top else cmds.firstOrNull {
            it !is Cmd.Unknown && it !is Cmd.Chat && it !is Cmd.Teach && it !is Cmd.Remember
        } ?: top

        return when (main) {
            is Cmd.Call -> callReply(cmds.filterIsInstance<Cmd.Call>().map { it.target }.distinct(), main.speaker)
            is WhatsAppCmd -> {
                val text = if (main.text.isBlank()) "" else messageText(main.text)
                callReply(
                    cmds.filterIsInstance<WhatsAppCmd>().map { it.target }.distinct(), false,
                    action = if (main.kind == "msg") "wa_msg" else "wa_${main.kind}", text = text
                )
            }
            is Cmd.Remember -> {
                if (memory.remember(main.fact)) Reply("Okay, I'll remember: ${main.fact}", "ठीक है, याद रख लिया", endChat = true)
                else Reply("I already remember that", "ये मुझे पहले से याद है", endChat = true)
            }
            is Cmd.ForgetFact -> {
                if (memory.forgetMatching(main.what) > 0) Reply("Done, I've forgotten that", "ठीक है, भूल गई", endChat = true)
                else Reply("I don't have anything like that saved", "ऐसी कोई बात मुझे याद नहीं है", endChat = true)
            }
            Cmd.Recall -> recallReply()
            is Cmd.RecallAbout -> recallAbout(main)
            is CallLogCmd -> callLogReply(main.kind)
            is MusicCmd -> musicCmdReply(main)
            is ListCmd -> listReply(main)
            is CalendarCmd -> calendarReply(main)
            is NavigateCmd -> navigateReply(main)
            is NearbyCmd -> nearbyReply(main.what)
            is SaveAddressCmd -> saveAddressReply(main)
            is NotificationsCmd -> if (MediaCtl.allowed(ctx)) inboxReply(main.app, main.who) else notificationsReply(main.app)
            is ReplyCmd -> replyCmdReply(main)
            is RoutineCmd -> routineReply(main.kind)
            is WeatherCmd -> Reply("One second…", "एक सेकंड", later = { weatherReply(main.day) })
            is SettingCmd -> settingReply(main)
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
            is Cmd.Alarm -> alarmReply(main)
            is Cmd.Timer -> timerReply(main.seconds)
            is Cmd.AlarmAsk -> {
                pendingAlarm = main
                pendingAlarmAt = System.currentTimeMillis()
                when {
                    main.timer -> Reply("For how long?", "कितनी देर का टाइमर?", endChat = false)
                    main.label.isNotBlank() -> Reply("At what time should I remind you?", "किस समय याद दिलाऊँ?", endChat = false)
                    else -> Reply("For what time?", "कितने बजे का अलार्म लगाऊँ?", endChat = false)
                }
            }
            Cmd.ShowAlarms -> Reply("Here are your alarms", "ये रहे आपके अलार्म", {
                openExternal(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Clock")
            }, endChat = true)
            Cmd.StopAlarm -> Reply("Okay", "ठीक है", {
                val i = Intent(AlarmClock.ACTION_DISMISS_ALARM)
                if (i.resolveActivity(ctx.packageManager) != null) openExternal(i, "Clock")
                else openExternal(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Clock")
            }, endChat = true)
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

    // ---------- Rasta / jagah ----------

    private val mapsPkg = "com.google.android.apps.maps"

    private fun navigateReply(n: NavigateCmd): Reply {
        val saved = when (n.dest) {
            "home" -> prefs.getString("home_address", null)
            "office" -> prefs.getString("office_address", null)
            else -> null
        }
        val target = saved ?: when (n.dest) { "home" -> "Home"; "office" -> "Work"; else -> n.dest }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(target)}&mode=${n.mode}"))
        if (installed(mapsPkg)) i.setPackage(mapsPkg)
        else i.data = Uri.parse("geo:0,0?q=${Uri.encode(target)}")
        val name = when (n.dest) { "home" -> "home"; "office" -> "the office"; else -> n.dest.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } }
        val nameHi = when (n.dest) { "home" -> "घर"; "office" -> "ऑफ़िस"; else -> n.dest }
        val how = when (n.mode) { "w" -> " on foot"; "l" -> " by bike"; else -> "" }
        val tip = if (saved == null && (n.dest == "home" || n.dest == "office"))
            "\n(Tip: say “My ${if (n.dest == "home") "home" else "office"} address is …” once, so I always find it.)" else ""
        return Reply("Starting directions to $name$how$tip", "$nameHi का रास्ता दिखा रही हूँ", { openExternal(i, "Maps") }, endChat = true)
    }

    private fun nearbyReply(what: String): Reply {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(what)}"))
        if (installed(mapsPkg)) i.setPackage(mapsPkg)
        return Reply("Here's the nearest $what", "पास में $what दिखा रही हूँ", { openExternal(i, "Maps") }, endChat = true)
    }

    private fun saveAddressReply(a: SaveAddressCmd): Reply {
        val key = when (a.kind) { "home" -> "home_address"; "office" -> "office_address"; else -> "city" }
        val value = Parser.originalSpan(lastOriginal, a.address).trim()
        prefs.edit().putString(key, value).apply()
        return when (a.kind) {
            "home" -> Reply("Saved your home address: $value", "घर का पता याद रख लिया", endChat = true)
            "office" -> Reply("Saved your office address: $value", "ऑफ़िस का पता याद रख लिया", endChat = true)
            else -> Reply("Okay, your city is $value", "ठीक है, आपका शहर $value", endChat = true)
        }
    }

    // ---------- Notifications ----------

    // ---------- Messages: padhna + bina WhatsApp khole reply (notification ke "Reply" se) ----------

    private fun inboxReply(app: String, who: String): Reply {
        if (!prefs.getBoolean("read_notifs", false)) return Reply(
            "To read your messages, turn on “Read my notifications” in the Lilly app",
            "मैसेज पढ़ने के लिए लिली ऐप में रीड माय नोटिफिकेशन्स ऑन कीजिए", endChat = true
        )
        var list = Inbox.recent(app, 12 * 60 * 60 * 1000L)
        if (who.isNotBlank()) {
            val w = Parser.normName(who)
            list = list.filter { Parser.normName(it.title).contains(w) }
            if (list.isEmpty()) return Reply("No new message from $who", "${CallChoice.speakName(who)} का कोई नया मैसेज नहीं है", endChat = true)
        }
        if (list.isEmpty()) return Reply(
            if (app.isBlank()) "No new messages" else "No new WhatsApp messages",
            if (app.isBlank()) "कोई नया मैसेज नहीं है" else "व्हाट्सऐप पर कोई नया मैसेज नहीं है", endChat = true
        )
        val top = list.take(5)
        val lines = top.joinToString("\n") { "• ${it.app}: ${it.title}${if (it.text.isNotBlank()) " — " + it.text else ""}" }
        fun say(n: Inbox.Msg, hi: Boolean): String {
            val w = CallChoice.speakName(n.title)
            val body = n.text.take(160)
            return if (hi) "$w: $body" else "$w says: $body"
        }
        val first = top.first()
        lastReadFrom = first.title
        val missed = isCallNote(first)
        val canReply = first.reply != null
        val canCall = missed || first.title.isNotBlank()
        // Padhne ke baad poochho: jawab dena hai ya call karun? (Siri jaisa)
        val askEn: String
        val askHi: String
        val w = CallChoice.speakName(first.title)
        when {
            missed -> { askEn = "Should I call ${first.title} back?"; askHi = "$w को वापस कॉल करूँ?" }
            canReply -> { askEn = "Reply to ${first.title}, or call?"; askHi = "$w को जवाब देना है या कॉल करूँ?" }
            canCall -> { askEn = "Should I call ${first.title}?"; askHi = "$w को कॉल करूँ?" }
            else -> { askEn = ""; askHi = "" }
        }
        if (askEn.isNotBlank()) afterRead = AfterRead(first, System.currentTimeMillis())
        return Reply(
            "${list.size} new:\n$lines" + if (askEn.isNotBlank()) "\n\n$askEn" else "",
            top.joinToString("। ") { say(it, true) } + if (askHi.isNotBlank()) "। $askHi" else "",
            endChat = askEn.isBlank(),
            spokenEn = top.joinToString(". ") { say(it, false) } + if (askEn.isNotBlank()) ". $askEn" else ""
        )
    }

    private var lastReadFrom: String? = null

    private data class AfterRead(val msg: Inbox.Msg, val at: Long)
    private var afterRead: AfterRead? = null

    /** Missed call wala notification? (dialer / phone app ka) */
    private fun isCallNote(m: Inbox.Msg): Boolean =
        Regex("""dialer|incallui|telecom|\.phone|contacts""").containsMatchIn(m.pkg) ||
            Regex("""(?i)missed call|मिस्ड कॉल""").containsMatchIn(m.text + " " + m.title)

    /** Message padhne ke baad: "haan / reply" -> kya bhejun; "call" -> call; "nahi" -> band */
    private fun answerAfterRead(heard: List<String>): Reply? {
        val a = afterRead ?: return null
        afterRead = null
        if (System.currentTimeMillis() - a.at > 45_000) return null
        val n = Parser.normalize(heard.first())
        val m = a.msg
        val who = CallChoice.speakName(m.title)
        val missed = isCallNote(m)
        val no = Regex("""^(?:no|nope|nahi|nahin|na|nai|mat|cancel|rehne do|chhodo|chodo|baad me|baad mein|band karo|kuch nahi|not now)$""").matches(n)
        if (no) return Reply("Okay", "ठीक है", endChat = true)
        val wantsCall = Regex("""\b(?:call|phone|ghanti|dial|baat karao|baat karni)\b""").containsMatchIn(n)
        val video = Regex("""\bvideo\b""").containsMatchIn(n)
        val wa = Regex("""\b(?:whatsapp|whatsap|watsapp)\b""").containsMatchIn(n) || (m.pkg.contains("whatsapp") && Regex("""\bwhatsapp\b""").containsMatchIn(n))
        val isYes = yesRe.matches(n) || Regex("""^(?:haan|han|ha|haa|yes|ji|ok|okay|theek hai|sure)\b""").containsMatchIn(n)
        val wantsReply = Regex("""\b(?:reply|replay|jawab|javab|message|msg|likho|bhejo|bolo)\b""").containsMatchIn(n)
        val target = m.title.replace(Regex("""^.*?:\s*"""), "").trim().ifBlank { m.title } // "Family: Rahul" -> Rahul
        // Call: missed call par "haan" = call back; ya "call karo"
        if (wantsCall || (missed && isYes)) {
            val digits = target.filter { it.isDigit() }
            if (digits.length >= 7 && target.count { it.isLetter() } == 0)
                return callNumber(target, CallChoice.Phone(target, digits, "", false), speaker = false, chosen = true)
            return if (wa || video) callReply(listOf(target), false, action = if (video) "wa_video" else "wa_call")
            else callReply(listOf(target), false)
        }
        if ((isYes || wantsReply) && m.reply != null) {
            // "haan, bol do ki aa raha hoon" — usi saans me message bhi ho toh seedha bhejo
            val inline = Regex("""\b(?:ki|that|bolo|likho)\s+(.+)$""").find(n)?.groupValues?.get(1)
            if (inline != null && inline.split(" ").size >= 2) {
                val text = messageText(inline)
                return if (Inbox.send(ctx, m, text)) Reply("Sent to ${m.title}: “$text” ✓", "$who को भेज दिया: $text", endChat = true)
                else Reply("Couldn't send from here", "यहाँ से नहीं भेज पाई", endChat = true, failed = true)
            }
            pendingReply = PendingReply(m, "", System.currentTimeMillis(), direct = true)
            return Reply("What should I send to ${m.title}?", "$who को क्या भेजूँ? बोलिए", endChat = false)
        }
        if (isYes && m.reply == null) {
            // Reply button nahi (jaise Instagram) — WhatsApp/SMS se chat kholo
            return decideInner(listOf("$target ko message bhejo"), false)
        }
        return null // kuch aur bola — normal command ki tarah samjho
    }

    private data class PendingReply(val msg: Inbox.Msg, var text: String, var at: Long, val direct: Boolean = false)
    private var pendingReply: PendingReply? = null

    private fun replyCmdReply(c: ReplyCmd): Reply {
        if (!MediaCtl.allowed(ctx) || !prefs.getBoolean("read_notifs", false)) {
            // Purana tareeka: WhatsApp chat khol ke message likh do
            if (c.who.isNotBlank()) return decideInner(listOf("whatsapp ${c.who} ko message bhejo ki ${c.text}"), false)
            return Reply("To reply without opening WhatsApp, turn on Music control and Read my notifications in the Lilly app",
                "बिना व्हाट्सऐप खोले जवाब के लिए लिली ऐप में म्यूज़िक कंट्रोल और रीड माय नोटिफिकेशन्स ऑन कीजिए", endChat = true)
        }
        val m = Inbox.findReply(c.who.ifBlank { lastReadFrom ?: "" }) ?: Inbox.findReply(c.who)
            ?: return if (c.who.isNotBlank() && c.text.isNotBlank())
                decideInner(listOf("whatsapp ${c.who} ko message bhejo ki ${c.text}"), false)
            else Reply("There's no new message to reply to", "जवाब देने के लिए कोई नया मैसेज नहीं है", endChat = true)
        val p = PendingReply(m, if (c.text.isBlank()) "" else messageText(c.text), System.currentTimeMillis())
        pendingReply = p
        val who = CallChoice.speakName(m.title)
        if (p.text.isBlank()) return Reply("What should I reply to ${m.title}?", "$who को क्या जवाब दूँ?", endChat = false)
        return Reply("Reply to ${m.title}:\n“${p.text}”\n\nSay yes to send.", "$who को भेजूँ: ${p.text}?", endChat = false,
            spokenEn = "Reply to $who: ${p.text}?")
    }

    private fun answerPendingReply(heard: List<String>): Reply? {
        val p = pendingReply ?: return null
        if (System.currentTimeMillis() - p.at > 60_000) { pendingReply = null; return null }
        val n = Parser.normalize(heard.first())
        if (Regex("""^(no|nahi|nahin|na|cancel|mat bhejo|rehne do|chhodo)$""").matches(n)) {
            pendingReply = null
            return Reply("Okay, not sending", "ठीक है, नहीं भेजती", endChat = true)
        }
        if (p.text.isBlank() && p.direct) {
            // "Kya bhejun?" ke baad jo bola wahi seedha bhej do, aur bata do kya gaya
            pendingReply = null
            val text = messageText(Parser.stripWakeWord(heard.first()))
            val who = CallChoice.speakName(p.msg.title)
            return if (Inbox.send(ctx, p.msg, text)) Reply("Sent to ${p.msg.title}: “$text” ✓", "$who को भेज दिया: $text", endChat = true)
            else Reply("Couldn't send from here — opening the chat", "यहाँ से नहीं भेज पाई", endChat = true, failed = true)
        }
        if (p.text.isBlank()) {
            p.text = messageText(Parser.stripWakeWord(heard.first()))
            p.at = System.currentTimeMillis()
            val who = CallChoice.speakName(p.msg.title)
            return Reply("Reply to ${p.msg.title}:\n“${p.text}”\n\nSay yes to send.", "$who को भेजूँ: ${p.text}?", endChat = false)
        }
        if (yesRe.matches(n)) {
            pendingReply = null
            val ok = Inbox.send(ctx, p.msg, p.text)
            return if (ok) Reply("Sent to ${p.msg.title} ✓", "${CallChoice.speakName(p.msg.title)} को भेज दिया", endChat = true)
            else Reply("That message can't be replied from here any more. Opening WhatsApp", "यहाँ से जवाब नहीं जा पाया", endChat = true, failed = true)
        }
        // Naya message bola — wahi rakh ke dobara poochho
        p.text = messageText(Parser.stripWakeWord(heard.first()))
        p.at = System.currentTimeMillis()
        return Reply("Reply to ${p.msg.title}:\n“${p.text}”\n\nSay yes to send.", "${CallChoice.speakName(p.msg.title)} को भेजूँ: ${p.text}?", endChat = false)
    }

    // ---------- Sleep timer ----------

    private val sleepHandler = Handler(Looper.getMainLooper())
    private val sleepStop = Runnable {
        mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        if (MediaCtl.allowed(ctx)) try { MediaCtl.run(ctx, "pause") } catch (e: Exception) { }
    }

    private fun sleepReply(c: MusicCmd): Reply {
        sleepHandler.removeCallbacks(sleepStop)
        if (c.action == "sleep_cancel") return Reply("Sleep timer cancelled", "स्लीप टाइमर हटा दिया", endChat = true)
        val sec = c.arg.toIntOrNull() ?: 1800
        sleepHandler.postDelayed(sleepStop, sec * 1000L)
        return Reply("Okay, music will stop in ${TimeParse.durationLabel(sec)}", "ठीक है, ${TimeParse.durationLabelHi(sec)} बाद गाना बंद कर दूँगी", endChat = true)
    }

    private fun notificationsReply(app: String): Reply {
        if (!LillyAccess.usable(ctx) || !prefs.getBoolean("read_notifs", false)) {
            return Reply(
                "To read your notifications, turn on “Read my notifications” in the Lilly app",
                "नोटिफिकेशन पढ़ने के लिए लिली ऐप में रीड माय नोटिफिकेशन्स ऑन कीजिए", endChat = true
            )
        }
        val list = LillyAccess.recentNotes(app, 6 * 60 * 60 * 1000L)
        if (list.isEmpty()) {
            return Reply(
                if (app.isBlank()) "No new notifications" else "No new WhatsApp messages",
                if (app.isBlank()) "कोई नया नोटिफिकेशन नहीं है" else "व्हाट्सऐप पर कोई नया मैसेज नहीं है", endChat = true
            )
        }
        val top = list.take(5)
        val lines = top.joinToString("\n") { "• ${it.app}: ${it.title}${if (it.text.isNotBlank()) " — " + it.text else ""}" }
        fun say(n: LillyAccess.Note, hi: Boolean): String {
            val who = CallChoice.speakName(n.title)
            val body = CallChoice.speakName(n.text).take(140)
            return if (hi) "${n.app} पर $who: $body" else "${n.app}, $who: $body"
        }
        val more = if (list.size > 5) list.size - 5 else 0
        LillyAccess.clearNotes(app)
        return Reply(
            "You have ${list.size} new:\n$lines" + if (more > 0) "\n…and $more more" else "",
            "आपके ${list.size} नए नोटिफिकेशन हैं। " + top.joinToString("। ") { say(it, true) },
            endChat = true,
            spokenEn = "You have ${list.size} new. " + top.joinToString(". ") { say(it, false) }
        )
    }

    // ---------- Good morning / good night ----------

    private fun routineReply(kind: String): Reply {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val time = TimeParse.label(h, cal.get(Calendar.MINUTE))
        val day = SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(Date())
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val next = try { am.nextAlarmClock?.triggerTime } catch (e: Exception) { null }
        val nextLine = next?.let {
            val c = Calendar.getInstance().apply { timeInMillis = it }
            val l = TimeParse.label(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            val tomorrow = c.get(Calendar.DAY_OF_YEAR) != cal.get(Calendar.DAY_OF_YEAR)
            Pair("Your next alarm is at $l${if (tomorrow) " tomorrow" else ""}.", "अगला अलार्म ${TimeParse.labelHi(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))} का है।")
        }
        if (kind == "night") {
            return Reply(
                "Good night Rahul! ${nextLine?.first ?: "No alarm is set."}",
                "गुड नाइट राहुल! ${nextLine?.second ?: "कोई अलार्म नहीं लगा है।"}",
                endChat = true
            )
        }
        val hello = when (h) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        val helloHi = when (h) { in 4..11 -> "सुप्रभात"; in 12..16 -> "नमस्ते"; else -> "शुभ संध्या" }
        val rem = todaysReminders()
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return Reply("One second…", "एक सेकंड", later = {
            val w = try { Weather.fetch(ctx) } catch (e: Exception) { null }
            val en = StringBuilder("$hello Rahul! It's $time, $day. ")
            val hi = StringBuilder("$helloHi राहुल! अभी ${TimeParse.labelHi(h, cal.get(Calendar.MINUTE))} हैं। ")
            if (w != null) { en.append(Weather.line(w, "today", false)).append(' '); hi.append(Weather.line(w, "today", true)).append(' ') }
            if (rem.isNotEmpty()) {
                en.append("Reminders today: ").append(rem.joinToString(", ") { "${it.first} ${it.second}" }).append(". ")
                hi.append("आज के रिमाइंडर: ").append(rem.joinToString(", ") { "${it.first} ${it.second}" }).append("। ")
            }
            nextLine?.let { en.append(it.first).append(' '); hi.append(it.second).append(' ') }
            en.append("Battery is at $pct percent.")
            hi.append("बैटरी $pct प्रतिशत है।")
            Reply(en.toString().trim(), hi.toString().trim(), endChat = true)
        })
    }

    /** Lilly ke lagaye aaj ke reminders: (samay, kaam) */
    private fun todaysReminders(): List<Pair<String, String>> {
        val now = System.currentTimeMillis()
        val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis
        return (prefs.getString("reminders", "") ?: "").split('\n').mapNotNull { line ->
            val at = line.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
            if (at < now || at > end) return@mapNotNull null
            val c = Calendar.getInstance().apply { timeInMillis = at }
            Pair(TimeParse.label(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), line.substringAfter('|'))
        }
    }

    private fun saveReminder(h: Int, m: Int, label: String) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val now = System.currentTimeMillis()
        val keep = (prefs.getString("reminders", "") ?: "").split('\n')
            .filter { (it.substringBefore('|').toLongOrNull() ?: 0) > now }.takeLast(30)
        prefs.edit().putString("reminders", (keep + "${c.timeInMillis}|${label.replace('\n', ' ')}").joinToString("\n")).apply()
    }

    // ---------- Mausam ----------

    private fun weatherReply(day: String): Reply = try {
        val w = Weather.fetch(ctx)
        Reply(Weather.line(w, day, false), Weather.line(w, day, true), endChat = true)
    } catch (e: Weather.NeedLocation) {
        Reply(
            "I need your location for the weather. Allow Location in the Lilly app, or say: my city is Jaipur",
            "मौसम के लिए लोकेशन चाहिए। लिली ऐप में लोकेशन अलाउ कीजिए, या बोलिए: मेरा शहर जयपुर है",
            endChat = true, failed = true
        )
    } catch (e: Exception) {
        Reply("I couldn't get the weather right now. Is the internet on?", "अभी मौसम नहीं मिल पाया, इंटरनेट चेक कीजिए", endChat = true)
    }

    // ---------- Phone settings ----------

    /**
     * "WiFi band karo": Android apps ko khud switch badalne nahi deta — Lilly switch wala panel kholti hai
     * aur "Lilly helper" ON ho toh switch khud daba deti hai.
     */
    private fun switchReply(name: String, want: String): Reply? {
        if (want.isBlank()) return null
        val on = want == "on"
        val label = mapOf("wifi" to "WiFi", "bluetooth" to "Bluetooth", "location" to "Location", "airplane" to "Airplane mode")[name] ?: return null
        val labelHi = mapOf("wifi" to "वाई-फाई", "bluetooth" to "ब्लूटूथ", "location" to "लोकेशन", "airplane" to "एयरप्लेन मोड")[name] ?: label
        if (name == "wifi") {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val now = try { wm?.isWifiEnabled } catch (e: Exception) { null }
            if (now == on) return Reply("$label is already ${if (on) "on" else "off"}", "$labelHi पहले से ${if (on) "ऑन" else "बंद"} है", endChat = true)
        }
        val intent = if (name == "wifi" && Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
        else if (name == "location") Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) else panelIntent(name)
        val helper = LillyAccess.usable(ctx)
        return if (helper) Reply(
            "Turning $label ${if (on) "on" else "off"}",
            "$labelHi ${if (on) "ऑन" else "बंद"} कर रही हूँ",
            {
                LillyAccess.flipSwitch(name, on)
                openExternal(intent, "Settings")
            }, endChat = true
        ) else Reply(
            "I've opened $label — tap the switch to turn it ${if (on) "on" else "off"}. (Turn on \"Lilly helper\" in the app and I'll do it myself.)",
            "$labelHi का स्विच खोल दिया है, ${if (on) "ऑन" else "बंद"} कर लीजिए",
            { openExternal(intent, "Settings") }, endChat = true
        )
    }

    private fun panelIntent(name: String): Intent {
        val q = Build.VERSION.SDK_INT >= 29
        return when (name) {
            "wifi" -> Intent(if (q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)
            "data" -> Intent(if (q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "hotspot" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
    }

    private fun settingReply(c: SettingCmd): Reply {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val policyOk = try { nm.isNotificationPolicyAccessGranted } catch (e: Exception) { false }
        val askPolicy = Reply(
            "I need one permission for that. I'm opening it — turn on Lilly there, then ask me again",
            "इसके लिए एक परमिशन चाहिए। खोल रही हूँ, वहाँ लिली ऑन करके फिर से बोलिए",
            { openExternal(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "Settings") }, endChat = true
        )
        when (c.what) {
            "dnd" -> {
                if (!policyOk) return askPolicy
                val on = c.value == "on"
                return Reply(if (on) "Do Not Disturb is on" else "Do Not Disturb is off", if (on) "डू नॉट डिस्टर्ब ऑन कर दिया" else "डू नॉट डिस्टर्ब बंद कर दिया", {
                    try {
                        nm.setInterruptionFilter(
                            if (on) android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                        )
                    } catch (e: Exception) { }
                }, endChat = true)
            }
            "ringer" -> {
                if (c.value == "silent" && !policyOk) return askPolicy
                val (mode, en, hi) = when (c.value) {
                    "silent" -> Triple(AudioManager.RINGER_MODE_SILENT, "Phone is on silent", "फ़ोन साइलेंट कर दिया")
                    "vibrate" -> Triple(AudioManager.RINGER_MODE_VIBRATE, "Phone is on vibrate", "फ़ोन वाइब्रेट पर कर दिया")
                    else -> Triple(AudioManager.RINGER_MODE_NORMAL, "Ringer is back on", "रिंगर वापस चालू कर दिया")
                }
                return Reply(en, hi, { try { audio.ringerMode = mode } catch (e: Exception) { } }, endChat = true)
            }
            "helper" -> {
                return if (LillyAccess.turnOff()) Reply(
                    "Helper is off. Payment apps will work now",
                    "हेल्पर बंद कर दिया, अब पेमेंट ऐप चलेंगे", endChat = true
                ) else Reply(
                    "Helper is already off. Payment apps should work",
                    "हेल्पर पहले से बंद है, पेमेंट ऐप चलने चाहिए", endChat = true
                )
            }
            "brightness" -> {
                if (!Settings.System.canWrite(ctx)) {
                    return Reply(
                        "To change brightness I need one permission. I'm opening it — allow Lilly there, then ask again",
                        "ब्राइटनेस बदलने के लिए एक परमिशन चाहिए। खोल रही हूँ, वहाँ लिली को अलाउ करके फिर से बोलिए",
                        { openExternal(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)), "Settings") },
                        endChat = true
                    )
                }
                val cr = ctx.contentResolver
                val cur = try { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { 128 }
                val target = when (c.value) {
                    "max" -> 255
                    "min" -> 8
                    "up" -> (cur + 64).coerceAtMost(255)
                    "down" -> (cur - 64).coerceAtLeast(8)
                    else -> ((c.value.toIntOrNull() ?: 50) * 255 / 100).coerceIn(5, 255)
                }
                val pct = target * 100 / 255
                return Reply("Brightness set to $pct%", "ब्राइटनेस $pct प्रतिशत कर दी", {
                    try {
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target)
                    } catch (e: Exception) { }
                }, endChat = true)
            }
            else -> {
                val name = c.value.substringBefore(':')
                val want = c.value.substringAfter(':', "")
                switchReply(name, want)?.let { return it }
                val label = mapOf(
                    "wifi" to "Wi-Fi", "data" to "mobile data", "bluetooth" to "Bluetooth", "hotspot" to "hotspot",
                    "airplane" to "airplane mode", "location" to "location", "battery" to "battery saver",
                    "display" to "display", "sound" to "sound"
                )[name] ?: "settings"
                val switchy = name in setOf("wifi", "data", "bluetooth", "hotspot", "airplane", "location")
                return Reply(
                    if (switchy) "Android doesn't let apps flip the $label switch themselves, so I've opened it for you" else "Opening $label settings",
                    if (switchy) "$label का स्विच खोल दिया है, वहाँ से ऑन या ऑफ़ कर लीजिए" else "$label सेटिंग्स खोल रही हूँ",
                    { openExternal(panelIntent(name), "Settings") }, endChat = true
                )
            }
        }
    }

    // ---------- Alarm / timer / reminder ----------

    private var pendingAlarm: Cmd.AlarmAsk? = null
    private var pendingAlarmAt = 0L

    /** "Kitne baje ka?" poochha tha — jawab me samay aaya ho toh lagao */
    private fun answerPendingAlarm(heard: List<String>): Reply? {
        val pa = pendingAlarm ?: return null
        if (System.currentTimeMillis() - pendingAlarmAt > 60_000) { pendingAlarm = null; return null }
        pendingAlarm = null
        val t = Parser.normalize(heard.first())
        if (CallChoice.isCancel(t)) return Reply("Okay", "ठीक है", endChat = true)
        if (pa.timer) {
            val sec = heard.firstNotNullOfOrNull { TimeParse.durationSeconds(Parser.normalize(it)) } ?: return null
            return timerReply(sec)
        }
        val w = heard.firstNotNullOfOrNull { h ->
            val n = Parser.normalize(h)
            TimeParse.parse(n) ?: TimeParse.durationSeconds(n)?.let { TimeParse.When(inMinutes = maxOf(1, it / 60)) }
        } ?: return null
        return alarmReply(Cmd.Alarm(w, pa.label, TimeParse.repeat(t)))
    }

    private fun alarmReply(a: Cmd.Alarm): Reply {
        val cal = Calendar.getInstance()
        val (h, m) = TimeParse.resolve(a.at, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .putExtra(AlarmClock.EXTRA_MESSAGE, a.label.ifBlank { "Lilly" })
        val days = when (a.repeat) {
            "daily" -> arrayListOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            )
            "weekdays" -> arrayListOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
            else -> null
        }
        if (days != null) i.putExtra(AlarmClock.EXTRA_DAYS, days)
        if (i.resolveActivity(ctx.packageManager) == null) {
            return Reply("I couldn't find a clock app on this phone", "इस फ़ोन में घड़ी वाला ऐप नहीं मिला", endChat = true, failed = true)
        }
        val time = TimeParse.label(h, m)
        val timeHi = TimeParse.labelHi(h, m)
        val rep = when (a.repeat) { "daily" -> " every day"; "weekdays" -> " on weekdays"; else -> "" }
        val repHi = when (a.repeat) { "daily" -> "रोज़ "; "weekdays" -> "सोमवार से शुक्रवार "; else -> "" }
        val inText = if (a.at.inMinutes >= 0) " (in ${TimeParse.durationLabel(a.at.inMinutes * 60)})" else ""
        val (en, hi) = if (a.label.isNotBlank()) {
            Pair("Okay, I'll remind you$rep at $time$inText: ${a.label}", "ठीक है, ${repHi}$timeHi याद दिला दूँगी")
        } else {
            Pair("Alarm set for $time$rep$inText", "${repHi}$timeHi का अलार्म लगा दिया")
        }
        return Reply(en, hi, {
            if (a.label.isNotBlank()) saveReminder(h, m, a.label)
            openExternal(i, "Clock")
        }, endChat = true)
    }

    private fun timerReply(sec: Int): Reply {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Lilly")
        if (i.resolveActivity(ctx.packageManager) == null) {
            return Reply("I couldn't find a clock app on this phone", "इस फ़ोन में घड़ी वाला ऐप नहीं मिला", endChat = true, failed = true)
        }
        return Reply(
            "Timer set for ${TimeParse.durationLabel(sec)}",
            "${TimeParse.durationLabelHi(sec)} का टाइमर लगा दिया",
            { openExternal(i, "Clock") }, endChat = true
        )
    }

    // ---------- Yaaddasht ----------

    private fun recallAbout(c: Cmd.RecallAbout): Reply {
        val keys = c.words.map { it.lowercase() } + listOf("janmdin", "janm din", "bday", "date of birth", "जन्म", "जन्मदिन")
        val hit = memory.facts().lastOrNull { f ->
            val x = f.text.lowercase()
            keys.any { it != c.whose && x.contains(it) } && (c.whose == null || x.contains(c.whose))
        }
        if (hit != null) return Reply(hit.text, hit.text, endChat = true)
        val who = c.whose?.let { "$it's" } ?: "your"
        return Reply(
            "I don't know $who date of birth yet. Tell me once: \"${if (c.whose == null) "My" else "${c.whose}'s"} date of birth is 12 June 1998\"",
            if (c.whose == null) "मुझे आपकी जन्मतिथि अभी नहीं पता। एक बार बोलिए: मेरी डेट ऑफ़ बर्थ बारह जून उन्नीस सौ अट्ठानवे है"
            else "मुझे ${c.whose} की जन्मतिथि अभी नहीं पता। एक बार बता दीजिए, मैं याद रख लूँगी",
            endChat = true
        )
    }

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

    private fun callReply(targets: List<String>, speaker: Boolean, action: String = "call", text: String = ""): Reply {
        pendingCall = null
        waAskText = null
        waConfirm = null
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
            if (action != "call") return finishContact(target, CallChoice.Phone(digits, digits, ""), speaker, false, action, text)
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
        if (learned != null && rows.any { it.name == learned }) return callByName(target, learned, rows, speaker, favOnly, action, text)

        // Kaun-kaun se contact ho sakte hain ("Abhishek" -> 16 Abhishek)
        val tied = CallChoice.candidates(targets, rows.map { it.name }) { learner.contactBonus(it) }
        if (tied.isEmpty()) return notFound
        if (tied.size == 1) return callByName(target, tied[0], rows, speaker, favOnly, action, text)
        // Sab milte-julte naamon ka number ek hi hai ("Kunal Bhai" do baar save) — bina poochhe seedha
        CallChoice.distinctNumbers(tied, rows).singleOrNull()?.let { return finishContact(target, it, speaker, false, action, text) }
        val unique = CallChoice.collapseSameNumbers(tied, rows)
        if (unique.size == 1) return callByName(target, unique[0], rows, speaker, favOnly, action, text)

        val options = unique.map { n -> rows.first { it.name == n } }
        val pc = PendingCall(
            target, options, byName = true, speaker = speaker, favOnly = favOnly, at = System.currentTimeMillis(),
            action = action, text = text
        )
        pendingCall = pc
        return askChoice(pc)
    }

    private fun callByName(
        target: String, name: String, rows: List<CallChoice.Phone>, speaker: Boolean, favOnly: Boolean,
        action: String = "call", text: String = ""
    ): Reply {
        var nums = CallChoice.dedupe(rows.filter { it.name == name })
        // WhatsApp: jis number par WhatsApp hai wahi
        if (action != "call" && nums.size > 1) {
            val wa = whatsappNumbers()
            val onWa = nums.filter { CallChoice.digitsKey(it.number) in wa }
            if (onWa.isNotEmpty()) nums = onWa
        }
        if (nums.size == 1) return finishContact(target, nums[0], speaker, false, action, text)
        // Pehle kai baar chuna hua number
        learner.numberFor(name)?.let { known ->
            nums.firstOrNull { CallChoice.digitsKey(it.number) == known }?.let { return finishContact(target, it, speaker, false, action, text) }
        }
        val pc = PendingCall(
            target, nums.take(5), byName = false, speaker = speaker, favOnly = favOnly, at = System.currentTimeMillis(),
            action = action, text = text
        )
        pendingCall = pc
        return askChoice(pc)
    }

    private val PAGE = 5

    private val ordinalHi = listOf("पहला", "दूसरा", "तीसरा", "चौथा", "पाँचवाँ")
    private val ordinalEn = listOf("first", "second", "third", "fourth", "fifth")

    /** Siri jaisa: screen par list, aur bol kar poochho */
    private fun askChoice(pc: PendingCall): Reply {
        val o = pc.options
        if (pc.byName) {
            val who = CallChoice.speakName(pc.target).replaceFirstChar { it.uppercase() }
            if (o.size <= 3) {
                val lines = o.mapIndexed { i, p -> "${i + 1}. ${p.name}" }.joinToString("\n")
                val names = o.map { CallChoice.speakName(it.name) }
                return Reply(
                    "I found ${o.size} contacts for “$who”:\n$lines\nWhich one?",
                    "${o.size} कॉन्टैक्ट मिले: " + names.joinToString(", या ") + "। ${whoVerbHi(pc.action)}",
                    endChat = false
                )
            }
            // Bahut saare: 5-5 karke. "more" / "aur" bolne par agle 5
            val from = pc.page * PAGE
            val page = o.subList(from, minOf(from + PAGE, o.size))
            val short = CallChoice.shortNames(o)
            val lines = page.indices.joinToString("\n") { i -> "${from + i + 1}. ${page[i].name}" }
            val spokenList = page.indices.joinToString(". ") { i -> "${from + i + 1}, ${short[from + i]}" }
            val left = o.size - (from + page.size)
            val head = if (pc.page == 0) "I found ${o.size} contacts named $who. " else ""
            val headHi = if (pc.page == 0) "$who नाम के ${o.size} कॉन्टैक्ट हैं। " else ""
            val tail = if (left > 0) "Say a number, or say more." else "Say a number."
            val tailHi = if (left > 0) "नंबर बोलिए, या 'और' बोलिए।" else "नंबर बोलिए।"
            val moreLine = if (left > 0) "\n…$left more — say “more”" else ""
            return Reply(
                "$head\n$lines$moreLine\n$tail".trim(),
                "$headHi$spokenList। $tailHi",
                endChat = false,
                spokenEn = "$head$spokenList. $tail"
            )
        }
        val name = o.first().name
        val lines = o.mapIndexed { i, p -> CallChoice.numberLine(i, p) }.joinToString("\n")
        // Do number ka label same ho ("Mobile", "Mobile") toh aakhri ank bhi bolo
        val sameLabels = o.map { it.label.lowercase() }.distinct().size < o.size
        // Number padh kar nahi sunate (lamba aur bekaar) — sirf "pehla Mobile, doosra Work"; ank screen par
        fun spokenOpt(i: Int, p: CallChoice.Phone, hi: Boolean): String {
            val ord = if (hi) ordinalHi.getOrElse(i) { "${i + 1}" } else ordinalEn.getOrElse(i) { "${i + 1}" }
            return if (sameLabels || p.label.isBlank()) ord else "$ord ${p.label}"
        }
        return Reply(
            "$name has ${o.size} numbers:\n$lines\nWhich one?",
            spokenEn = "${CallChoice.speakName(name)} has ${o.size} numbers: " +
                o.mapIndexed { i, p -> spokenOpt(i, p, false) }.joinToString(", ") + ". Which one?",
            spoken =
            "${CallChoice.speakName(name)} के ${o.size} नंबर हैं: " + o.mapIndexed { i, p -> spokenOpt(i, p, true) }.joinToString(", ") + "। किस पर ${if (pc.action == "wa_msg") "भेजूँ" else "कॉल करूँ"}?",
            endChat = false
        )
    }

    /** "Kaun sa?" ka jawab aaya ho toh wahi sambhalo; nahi toh null (normal command) */
    private fun answerPendingCall(heard: List<String>): Reply? {
        val pc = pendingCall ?: return null
        if (System.currentTimeMillis() - pc.at > 60_000) { pendingCall = null; return null }
        val ans = heard.first()
        pc.at = System.currentTimeMillis()
        if (CallChoice.isCancel(ans)) {
            pendingCall = null
            return Reply("Okay, I won't call", "ठीक है, कॉल नहीं करती", endChat = true)
        }
        // "more" / "aur batao" — agle 5 naam
        if (pc.byName && heard.any { CallChoice.isMore(it) }) {
            if ((pc.page + 1) * PAGE < pc.options.size) {
                pc.page++
                return askChoice(pc)
            }
            return Reply("That's all of them. Say a number.", "बस इतने ही हैं। नंबर बोलिए।", endChat = false)
        }
        val idx = heard.firstNotNullOfOrNull { CallChoice.pick(it, pc.options, pc.byName) }
        if (idx != null) {
            pendingCall = null
            val chosen = pc.options[idx]
            return if (pc.byName) {
                callByName(pc.target, chosen.name, loadPhones(pc.favOnly), pc.speaker, pc.favOnly, pc.action, pc.text)
            } else finishContact(pc.target, chosen, pc.speaker, true, pc.action, pc.text)
        }
        // Koi nayi command boli ("time kya hua") toh wahi karo
        val cmd = Parser.parse(learner.rewrite(ans) ?: ans)
        if (cmd !is Cmd.Unknown && cmd !is Cmd.Chat) { pendingCall = null; return null }
        if (pc.asked < 2) {
            pc.asked++
            val r = askChoice(pc)
            val hint = if (pc.byName) "Say the name, or first / second." else "Say first, second, or the last digits."
            val hintHi = if (pc.byName) "नाम बोलिए, या पहला, दूसरा।" else "पहला, दूसरा, या आख़िरी अंक बोलिए।"
            return Reply("Sorry, I didn't get that. ${r.display}\n($hint)", "माफ़ कीजिए, समझ नहीं आया। $hintHi", endChat = false)
        }
        pendingCall = null
        return Reply("Okay, let's leave it for now", "ठीक है, अभी रहने देते हैं", endChat = true)
    }

    private fun whoVerbHi(action: String) = if (action == "wa_msg") "किसको मैसेज भेजूँ?" else "किसको कॉल करूँ?"

    /** Contact (aur number) mil gaya — ab jo kaam karna tha wo */
    private fun finishContact(
        target: String, p: CallChoice.Phone, speaker: Boolean, chosen: Boolean, action: String, text: String
    ): Reply = when (action) {
        "wa_msg" -> {
            val w = PendingWa(target, p, text, System.currentTimeMillis())
            if (text.isBlank()) {
                waAskText = w
                Reply("What should I send to ${p.name}?", "${CallChoice.speakName(p.name)} को क्या भेजूँ?", endChat = false)
            } else confirmWa(w)
        }
        "wa_sms" -> {
            val num = p.number
            Reply("SMS to ${p.name}: “$text” — tap Send", "${CallChoice.speakName(p.name)} को एसएमएस लिख दिया है, सेंड दबाइए", {
                learner.rememberContact(target, p.name)
                openExternal(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))).putExtra("sms_body", text), "Messages")
            }, endChat = true)
        }
        "wa_call" -> waCallReply(target, p, video = false)
        "wa_video" -> waCallReply(target, p, video = true)
        else -> callNumber(target, p, speaker, chosen)
    }

    // ---------- WhatsApp ----------

    private val yesRe = Regex(
        """^(yes|yeah|yep|yup|ya|haan|han|ha|haa|haan ji|ji haan|ji|send|send it|send kar do|send karo|bhej do|bhejo|bhej de|""" +
            """ok|okay|theek hai|thik hai|sure|kar do|done|correct|sahi hai|perfect|go ahead)$"""
    )

    /** Script setting: "spoken" (jaisa bola — Hindi lipi bhi) ya "roman" (Hinglish) */
    private fun messageText(fromParse: String): String {
        val raw = if (prefs.getString("msg_script", "spoken") == "roman") fromParse
        else Parser.originalSpan(lastOriginal, fromParse)
        return raw.trim().replaceFirstChar { it.uppercase() }
    }

    private fun confirmWa(w: PendingWa): Reply {
        waConfirm = w
        w.at = System.currentTimeMillis()
        val who = CallChoice.speakName(w.phone.name)
        return Reply(
            "Send to ${w.phone.name}:\n“${w.text}”\n\nSay yes to send, or say the message again.",
            "$who को भेजूँ: ${w.text}?",
            endChat = false,
            spokenEn = "Send to $who: ${w.text}?"
        )
    }

    /** "Kya bhejun?" / "Bhej doon?" ka jawab */
    private fun answerPendingWa(heard: List<String>): Reply? {
        val now = System.currentTimeMillis()
        val ans = heard.first()
        val n = Parser.normalize(ans)
        waAskText?.let { w ->
            waAskText = null
            if (now - w.at > 90_000) return null
            if (CallChoice.isCancel(ans)) return Reply("Okay, not sending", "ठीक है, नहीं भेजती", endChat = true)
            w.text = (if (prefs.getString("msg_script", "spoken") == "roman") n else lastOriginal.ifBlank { ans })
                .trim().replaceFirstChar { it.uppercase() }
            return confirmWa(w)
        }
        val w = waConfirm ?: return null
        if (now - w.at > 90_000) { waConfirm = null; return null }
        if (heard.any { yesRe.matches(Parser.normalize(it)) }) {
            waConfirm = null
            return sendWa(w)
        }
        if (CallChoice.isCancel(ans)) {
            waConfirm = null
            return Reply("Okay, not sending", "ठीक है, नहीं भेजती", endChat = true)
        }
        // Koi nayi command ("time kya hua") ho toh wahi; warna isi ko naya message maano
        val cmd = Parser.parse(learner.rewrite(ans) ?: ans)
        if (cmd !is Cmd.Unknown && cmd !is Cmd.Chat) { waConfirm = null; return null }
        w.text = (if (prefs.getString("msg_script", "spoken") == "roman") n else lastOriginal.ifBlank { ans })
            .trim().replaceFirstChar { it.uppercase() }
        return confirmWa(w)
    }

    private fun waPackage(): String? = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { installed(it) }

    /** WhatsApp ke liye number: desh ka code ke saath, bina + ke (10 ank = India, 91 jodo) */
    private fun waNumber(n: String): String {
        val d = n.filter { it.isDigit() }
        return when {
            n.trim().startsWith("+") -> d
            d.length == 10 -> "91$d"
            d.length == 11 && d.startsWith("0") -> "91" + d.drop(1)
            d.startsWith("00") -> d.drop(2)
            else -> d
        }
    }

    /** Kin numbers par WhatsApp hai (aakhri 10 ank) */
    private fun whatsappNumbers(): Set<String> {
        val out = HashSet<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, arrayOf(ContactsContract.Data.DATA1),
                ContactsContract.Data.MIMETYPE + " IN (?, ?)",
                arrayOf("vnd.android.cursor.item/vnd.com.whatsapp.profile", "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"),
                null
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out.add(CallChoice.digitsKey(it.substringBefore('@'))) } }
        } catch (e: Exception) { }
        return out
    }

    private fun sendWa(w: PendingWa): Reply {
        val pkg = waPackage() ?: return Reply("WhatsApp isn't installed on this phone", "इस फ़ोन में व्हाट्सऐप नहीं है", endChat = true, failed = true)
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=${waNumber(w.phone.number)}&text=" + Uri.encode(w.text))
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
        val who = CallChoice.speakName(w.phone.name)
        // Is insaan ka message abhi aaya hua hai? Toh notification ke "Reply" se seedha bhejo — WhatsApp khule bina, Send dabaye bina
        if (MediaCtl.allowed(ctx)) {
            val m = Inbox.findReply(w.phone.name) ?: Inbox.findReply(w.target)
            if (m != null && m.pkg.contains("whatsapp") && Inbox.send(ctx, m, w.text)) {
                if (w.phone.name.any { it.isLetter() }) learner.rememberContact(w.target, w.phone.name)
                return Reply("Sent to ${w.phone.name}: “${w.text}” ✓", "$who को भेज दिया: ${w.text}", endChat = true)
            }
        }
        val auto = LillyAccess.usable(ctx)
        return Reply(
            if (auto) "Sending to ${w.phone.name}" else "Your message to ${w.phone.name} is ready in WhatsApp — tap Send.\n(WhatsApp lets other apps send by themselves only as a reply to a message that just arrived.)",
            if (auto) "$who को भेज रही हूँ" else "मैसेज लिख दिया है, भेजने के लिए सेंड दबाइए",
            {
                if (w.phone.name.any { it.isLetter() }) learner.rememberContact(w.target, w.phone.name)
                if (auto) LillyAccess.sendWhatsApp()
                openExternal(intent, "WhatsApp")
            }, endChat = true
        )
    }

    /** WhatsApp voice / video call: contacts me WhatsApp ki "call" wali entry se seedha */
    private fun waCallReply(target: String, p: CallChoice.Phone, video: Boolean): Reply {
        val pkg = waPackage() ?: return Reply("WhatsApp isn't installed on this phone", "इस फ़ोन में व्हाट्सऐप नहीं है", endChat = true, failed = true)
        val mime = if (video) "vnd.android.cursor.item/vnd.com.whatsapp.video.call" else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        val key = CallChoice.digitsKey(p.number)
        var dataId: Long? = null
        try {
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1, ContactsContract.Data.DATA3, ContactsContract.Data.DISPLAY_NAME),
                ContactsContract.Data.MIMETYPE + "=?", arrayOf(mime), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val digits = ((c.getString(1) ?: "") + " " + (c.getString(2) ?: "")).filter { it.isDigit() || it == ' ' }
                    if (digits.split(' ').any { it.length >= 10 && CallChoice.digitsKey(it) == key }) { dataId = c.getLong(0); break }
                }
            }
        } catch (e: Exception) { }
        val kind = if (video) "video call" else "WhatsApp call"
        val kindHi = if (video) "वीडियो कॉल" else "व्हाट्सऐप कॉल"
        val who = CallChoice.speakName(p.name)
        val id = dataId
        if (id != null) {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse("content://com.android.contacts/data/$id"), mime)
                .setPackage(pkg)
            return Reply("Starting a $kind with ${p.name}", "$who को $kindHi कर रही हूँ", {
                learner.rememberContact(target, p.name)
                openExternal(i, "WhatsApp")
            }, endChat = true)
        }
        // Contacts me entry nahi: chat kholo, aur (helper ON ho toh) call button dabao
        val chat = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=${waNumber(p.number)}")).setPackage(pkg)
        val auto = LillyAccess.usable(ctx)
        return Reply(
            if (auto) "Starting a $kind with ${p.name}" else "I've opened ${p.name}'s chat — tap the call button",
            if (auto) "$who को $kindHi कर रही हूँ" else "चैट खोल दी है, कॉल बटन दबाइए",
            {
                if (auto) LillyAccess.callOnWhatsApp(video)
                openExternal(chat, "WhatsApp")
            }, endChat = true
        )
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
                if (speaker) {
                    // Call screen par "Speaker" button khud dabao (har phone par chalta hai, helper ON ho toh)
                    if (LillyAccess.usable(ctx)) LillyAccess.pressSpeaker()
                    forceSpeaker()
                }
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

    /** Spotify connect hai aur abhi Spotify hi baj raha/chuna hai — tab App Remote se pakka control */
    private fun spotifyInControl(): Boolean = SpotifyRemote.authorized(ctx) && installed(spotify) &&
        ((prefs.getString("music_app", "") ?: "").let { it == "spotify" || it.isBlank() } || learner.musicApp == "spotify")

    private fun musicCmdReply(c: MusicCmd): Reply {
        if (c.action == "sleep" || c.action == "sleep_cancel") return sleepReply(c)
        val (en, hi) = when (c.action) {
            "shuffle_on" -> "Shuffle on" to "शफ़ल ऑन कर दिया"
            "shuffle_off" -> "Shuffle off" to "शफ़ल बंद कर दिया"
            "repeat_on" -> "Repeating this song" to "ये गाना बार-बार चलेगा"
            "repeat_off" -> "Repeat off" to "रिपीट बंद कर दिया"
            "restart" -> "Playing from the start" to "गाना शुरू से चला दिया"
            "forward" -> "Skipped ahead ${c.arg} seconds" to "${c.arg} सेकंड आगे कर दिया"
            "rewind" -> "Went back ${c.arg} seconds" to "${c.arg} सेकंड पीछे कर दिया"
            "like" -> "Saved to your Liked Songs" to "गाना लाइक कर दिया"
            "queue" -> "Added ${c.arg} to the queue" to "${c.arg} अगला चलेगा"
            "liked" -> "Playing your Liked Songs" to "आपके लाइक किए गाने चला रही हूँ"
            else -> "" to ""
        }
        // Koi doosra app baj raha hai (YouTube Music, JioSaavn…) — Android ke music control se
        val other = MediaCtl.allowed(ctx) && MediaCtl.current(ctx)?.packageName.let { it != null && it != spotify }
        if (SpotifyRemote.authorized(ctx) && installed(spotify) && !other) {
            return Reply("One second…", "एक सेकंड", later = { spotifyMusicWork(c, en, hi).let { r -> if (r.failed && MediaCtl.allowed(ctx)) mediaWork(c, en, hi) else r } })
        }
        if (MediaCtl.allowed(ctx)) return Reply("One second…", "एक सेकंड", later = { mediaWork(c, en, hi) })
        return musicFallback(c, en, hi)
    }

    /** Android music control (har music app ke liye, bina helper) */
    private fun mediaWork(c: MusicCmd, en: String, hi: String): Reply {
        if (c.action == "nowplaying") {
            val n = MediaCtl.nowPlaying(ctx)
            return if (n != null) Reply("This is $n", "ये है $n", endChat = true)
            else Reply("Nothing is playing right now", "अभी कोई गाना नहीं चल रहा", endChat = true)
        }
        if (c.action == "liked") return playMusic("liked songs", "")
        if (c.action == "queue") return Reply("Adding to the queue works with Spotify — tap Connect Spotify in the Lilly app",
            "क्यू में डालना स्पॉटिफाई कनेक्ट करने पर होगा", endChat = true, failed = true)
        return when (MediaCtl.run(ctx, c.action, c.arg)) {
            true -> Reply(en, hi, endChat = true)
            false -> Reply("This music app doesn't let me do that", "ये म्यूज़िक ऐप ये करने नहीं देता", endChat = true, failed = true)
            null -> Reply("Nothing is playing right now", "अभी कोई गाना नहीं चल रहा", endChat = true, failed = true)
        }
    }

    private fun spotifyMusicWork(c: MusicCmd, en: String, hi: String): Reply {
                if (c.action == "nowplaying") {
                    val (conn, name) = SpotifyRemote.nowPlaying(ctx)
                    return if (conn && name != null) Reply("This is $name", "ये है $name", endChat = true)
                    else Reply("Nothing is playing on Spotify", "स्पॉटिफाई पर अभी कुछ नहीं चल रहा", endChat = true)
                }
                val arg = when (c.action) {
                    "queue" -> SpotifyRemote.findUri(c.arg, prefer = "track") ?: ""
                    else -> c.arg
                }
                val (conn, ok) = when (c.action) {
                    "liked" -> SpotifyRemote.run(ctx, "playuri", "spotify:collection:tracks").let { r ->
                        if (r.second == true) r else SpotifyRemote.run(ctx, "playuri", "spotify:collection")
                    }
                    else -> SpotifyRemote.run(ctx, c.action, arg)
                }
                return if (conn && ok == true) Reply(en, hi, endChat = true)
                else if (!conn) Reply("I couldn't reach Spotify. Open the Lilly app and tap Connect Spotify", "स्पॉटिफाई से नहीं जुड़ पाई, लिली ऐप में कनेक्ट स्पॉटिफाई दबाइए", endChat = true, failed = true)
                else Reply("Spotify didn't do that${SpotifyRemote.lastError?.let { " ($it)" } ?: ""}", "स्पॉटिफाई ने ये नहीं किया", endChat = true, failed = true)
    }

    /** Spotify connect nahi: jo media buttons se ho sakta hai wo karo */
    private fun musicFallback(c: MusicCmd, en: String, hi: String): Reply {
        return when (c.action) {
            "restart" -> Reply(en, hi, { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, endChat = true)
            "forward" -> Reply(en, hi, { mediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }, endChat = true)
            "rewind" -> Reply(en, hi, { mediaKey(KeyEvent.KEYCODE_MEDIA_REWIND) }, endChat = true)
            "liked" -> playMusic("liked songs", "")
            "queue" -> playMusic(c.arg, "")
            else -> Reply(
                "I can do that on Spotify once you tap \"Connect Spotify\" in the Lilly app",
                "ये स्पॉटिफाई कनेक्ट करने के बाद होगा, लिली ऐप में कनेक्ट स्पॉटिफाई दबाइए", endChat = true, failed = true
            )
        }
    }

    private fun mediaReply(action: String): Reply = if (spotifyInControl()) {
        // Spotify ko seedha bolo (media button kabhi-kabhi doosre app ko chala deta hai); na chale toh button
        val (en, hi) = when (action) {
            "pause" -> "Paused" to "गाना रोक दिया"
            "next" -> "Next song" to "अगला गाना"
            "previous" -> "Previous song" to "पिछला गाना"
            else -> "Playing music" to "गाना चला रही हूँ"
        }
        Reply(en, hi, {
            val code = when (action) {
                "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE; "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; else -> KeyEvent.KEYCODE_MEDIA_PLAY
            }
            Thread {
                val (conn, ok) = SpotifyRemote.run(ctx, action)
                if (!conn || ok != true) Handler(Looper.getMainLooper()).post { mediaKey(code) }
            }.start()
        }, endChat = true)
    } else mediaReplyKeys(action)

    private fun mediaReplyKeys(action: String): Reply = when (action) {
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
        val want = app.ifBlank { prefs.getString("music_app", null)?.takeIf { it.isNotBlank() } ?: learner.musicApp ?: "" }
        val remember = { if (app.isNotBlank()) learner.setMusicApp(app) }

        // Doosre music apps (jo naam se bola, ya pehle chuna tha)
        otherMusicApps[want]?.let { (pkg, label) ->
            if (installed(pkg)) return if (want == "ytmusic") ytMusicReply(query, shown, remember) else Reply("Playing $shown on $label", "$label पर $shown चला रही हूँ", {
                remember()
                openExternal(searchIntent(query).setPackage(pkg), label)
            }, endChat = true)
            if (app.isNotBlank()) return Reply("$label isn't installed on this phone", "इस फ़ोन में $label नहीं है", endChat = true, failed = true)
        }
        if (want == "spotify" && installed(spotify)) return spotifyReply(query, shown, remember)
        if (want == "youtube" && installed(youtube)) return youtubeReply(query, shown, remember, asVideo = app == "youtube")
        if (app == "spotify") {
            return Reply("Spotify isn't installed on this phone", "इस फ़ोन में स्पॉटिफाई नहीं है", endChat = true, failed = true)
        }
        if (installed(youtube)) return youtubeReply(query, shown, remember)
        if (installed(ytMusic)) return ytMusicReply(query, shown, remember)
        if (installed(spotify)) return spotifyReply(query, shown, remember)
        val i = searchIntent(query)
        if (i.resolveActivity(ctx.packageManager) != null) {
            return Reply("Playing $shown", "$shown चला रही हूँ", { openExternal(i, "Music") }, endChat = true)
        }
        return Reply("Please install Spotify or YouTube to play songs", "गाना चलाने के लिए स्पॉटिफाई इंस्टॉल कीजिए", endChat = true, failed = true)
    }

    /** app code -> (package, naam) */
    private val otherMusicApps = mapOf(
        "ytmusic" to ("com.google.android.apps.youtube.music" to "YouTube Music"),
        "jiosaavn" to ("com.jio.media.jiobeats" to "JioSaavn"),
        "wynk" to ("com.bsbportal.music" to "Wynk Music"),
        "gaana" to ("com.gaana" to "Gaana"),
        "amazon" to ("com.amazon.mp3" to "Amazon Music"),
        "apple" to ("com.apple.android.music" to "Apple Music")
    )

    /** YouTube Music: pehle gaane ki video ID dhoondo, seedha wahi chalao (bina helper, bina tap) */
    private fun ytMusicReply(query: String, shown: String, remember: () -> Unit): Reply =
        Reply("Playing $shown on YouTube Music", "यूट्यूब म्यूज़िक पर $shown चला रही हूँ", {
            remember()
            Thread {
                val id = MusicControl.firstYouTubeVideo("$query song")
                Handler(Looper.getMainLooper()).post {
                    val i = if (id != null) Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=$id")).setPackage(ytMusic)
                    else searchIntent(query).setPackage(ytMusic)
                    openExternal(i, "YouTube Music")
                }
            }.start()
        }, endChat = true)

    private fun searchIntent(query: String) = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        .putExtra(SearchManager.QUERY, query)
        .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")

    /**
     * Spotify: "play from search" bhejo. Spotify band ho toh pehli baar aksar sirf search dikhata hai,
     * isliye khulne ke baad dekhte hain gaana baja ya nahi — nahi baja toh wahi dobara bhejte hain.
     */
    private fun spotifyReply(query: String, shown: String, remember: () -> Unit): Reply {
        val old = spotifyOldWay(query, shown, remember)
        // Spotify ka official App Remote (ek baar "Connect Spotify" kiya ho): seedha gaana, bina helper
        if (SpotifyRemote.authorized(ctx)) {
            return Reply("Playing $shown on Spotify", "स्पॉटिफाई पर $shown चला रही हूँ", {
                remember()
                val main = Handler(Looper.getMainLooper())
                val said = lastOriginal.lowercase()
                val artist = Regex("""\bke (?:gaane|gane|songs|gaano)\b|\bsongs? (?:of|by)\b|\bsongs\b""").containsMatchIn(said)
                val playlist = Regex("""\bplaylist\b""").containsMatchIn(said)
                Thread {
                    val uri = SpotifyRemote.findUri(query + if (playlist) " playlist" else "", artistFirst = artist, prefer = if (playlist) "playlist" else null)
                    main.post {
                        if (uri == null) { old.action?.invoke(); return@post }
                        SpotifyRemote.play(ctx, uri) { ok -> if (!ok) old.action?.invoke() }
                    }
                }.start()
            }, endChat = true)
        }
        return old
    }

    private fun spotifyOldWay(query: String, shown: String, remember: () -> Unit): Reply {
        val old = spotifyOldWay2(query, shown, remember)
        // Spotify ka "session" zinda ho toh Android ke music control se seedha "dhoondh ke chalao"
        if (MediaCtl.allowed(ctx)) return Reply("Playing $shown on Spotify", "स्पॉटिफाई पर $shown चला रही हूँ", {
            remember()
            if (!MediaCtl.playFromSearch(ctx, spotify, query)) old.action?.invoke()
            else Handler(Looper.getMainLooper()).postDelayed({ if (!audio.isMusicActive) old.action?.invoke() }, 4000)
        }, endChat = true)
        return old
    }

    private fun spotifyOldWay2(query: String, shown: String, remember: () -> Unit): Reply {
        if (LillyAccess.usable(ctx)) {
            // Accessibility ON: Spotify search kholo, results aate hi pehle gaane par tap
            return Reply("Playing $shown on Spotify", "स्पॉटिफाई पर $shown चला रही हूँ", {
                remember()
                LillyAccess.playOnSpotify(query)
                openExternal(searchIntent(query).setPackage(spotify), "Spotify")
            }, endChat = true)
        }
        return spotifyWithoutTap(query, shown, remember)
    }

    private fun spotifyWithoutTap(query: String, shown: String, remember: () -> Unit): Reply =
        Reply(
            "Playing $shown on Spotify. If it only shows the search, tap the song once — or set YouTube Music as your music app for hands-free play",
            "स्पॉटिफाई पर $shown ढूँढ रही हूँ", {
            remember()
            val i = searchIntent(query).setPackage(spotify)
            val h = Handler(Looper.getMainLooper())
            // Spotify band ho toh "play from search" sirf search dikhata hai — pehle Spotify jagao, phir bhejo
            val running = audio.isMusicActive
            val launch = ctx.packageManager.getLaunchIntentForPackage(spotify)
            if (!running && launch != null) {
                openExternal(launch, "Spotify")
                h.postDelayed({ try { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { openExternal(Intent(i), "Spotify") } }, 2500)
            } else openExternal(i, "Spotify")
            for (delay in listOf(5500L, 9000L)) {
                h.postDelayed({
                    if (!audio.isMusicActive && Settings.canDrawOverlays(ctx)) {
                        try { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                    }
                }, delay)
            }
        }, endChat = true)

    /** YouTube: pehla result dhoondh kar seedha wahi video chalao (sirf search par na ruke) */
    private fun youtubeReply(query: String, shown: String, remember: () -> Unit = {}, asVideo: Boolean = false): Reply =
        Reply("Playing $shown on YouTube", "यूट्यूब पर $shown चला रही हूँ", {
            remember()
            Thread {
                // "YouTube pe cricket highlights" = video; bina app bole gaana maango toh "song" jodo
                val id = MusicControl.firstYouTubeVideo(if (asVideo) query else "$query song")
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

    // ---------- Permission (Lilly app me "Allow") ----------

    private fun needPermission(perm: String, what: String, whatHi: String): Reply =
        Reply("I need permission to read your $what. Opening Lilly — tap Allow, then ask me again.",
            "इसके लिए परमिशन चाहिए। लिली ऐप खोल रही हूँ, अलाउ दबाकर फिर से बोलिए", {
                openExternal(Intent(ctx, MainActivity::class.java).putExtra("ask_perm", perm), "Lilly")
            }, endChat = true)

    // ---------- Call log ----------

    private data class LogCall(val name: String, val number: String, val type: Int, val at: Long)

    private fun recentCalls(limit: Int = 30): List<LogCall> {
        val out = ArrayList<LogCall>()
        val c = try {
            ctx.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.CACHED_NAME, android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.TYPE, android.provider.CallLog.Calls.DATE),
                null, null, android.provider.CallLog.Calls.DATE + " DESC"
            )
        } catch (e: Exception) { null } ?: return out
        c.use {
            while (it.moveToNext() && out.size < limit) {
                out.add(LogCall(it.getString(0) ?: "", it.getString(1) ?: "", it.getInt(2), it.getLong(3)))
            }
        }
        return out
    }

    private fun whenLabel(at: Long): Pair<String, String> {
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val today = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY); val m = c.get(Calendar.MINUTE)
        val sameDay = c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        return if (sameDay) Pair("at ${TimeParse.label(h, m)}", "${TimeParse.labelHi(h, m)} बजे")
        else Pair("yesterday or earlier", "पहले")
    }

    private fun callLogReply(kind: String): Reply {
        if (!granted(Manifest.permission.READ_CALL_LOG)) return needPermission(Manifest.permission.READ_CALL_LOG, "call history", "कॉल हिस्ट्री")
        val calls = recentCalls()
        val MISSED = android.provider.CallLog.Calls.MISSED_TYPE
        val IN = android.provider.CallLog.Calls.INCOMING_TYPE
        val OUT = android.provider.CallLog.Calls.OUTGOING_TYPE
        fun who(l: LogCall) = l.name.ifBlank { "an unknown number" }
        fun whoHi(l: LogCall) = if (l.name.isBlank()) "अनजान नंबर" else CallChoice.speakName(l.name)
        when (kind) {
            "missed" -> {
                val day = System.currentTimeMillis() - 24 * 3600_000L
                val m = calls.filter { it.type == MISSED && it.at > day }
                if (m.isEmpty()) return Reply("No missed calls today", "आज कोई मिस्ड कॉल नहीं है", endChat = true)
                val top = m.take(4)
                return Reply(
                    "${m.size} missed call${if (m.size > 1) "s" else ""}:\n" + top.joinToString("\n") { "• ${who(it)} ${whenLabel(it.at).first}" },
                    "${m.size} मिस्ड कॉल: " + top.joinToString(", ") { "${whoHi(it)} ${whenLabel(it.at).second}" } + "। वापस कॉल करना हो तो बोलिए: कॉल बैक",
                    endChat = true,
                    spokenEn = "${m.size} missed call${if (m.size > 1) "s" else ""}: " + top.joinToString(", ") { "${who(it)} ${whenLabel(it.at).first}" } + ". Say call back to return the call."
                )
            }
            "last" -> {
                val l = calls.firstOrNull() ?: return Reply("Your call history is empty", "कॉल हिस्ट्री खाली है", endChat = true)
                val (en, hi) = when (l.type) {
                    OUT -> "You called" to "आपने कॉल किया था"
                    MISSED -> "Missed call from" to "मिस्ड कॉल"
                    else -> "Call from" to "कॉल आया था"
                }
                return Reply("$en ${who(l)} ${whenLabel(l.at).first}", "${whoHi(l)} — $hi, ${whenLabel(l.at).second}", endChat = true)
            }
            else -> {
                val l = (if (kind == "redial") calls.firstOrNull { it.type == OUT }
                else calls.firstOrNull { it.type == MISSED || it.type == IN })
                    ?: return Reply("I couldn't find that call", "वो कॉल नहीं मिली", endChat = true, failed = true)
                if (l.number.isBlank()) return Reply("That number is hidden, so I can't call back", "नंबर छुपा हुआ है, कॉल नहीं हो सकती", endChat = true)
                return callNumber(l.name.ifBlank { l.number }, CallChoice.Phone(l.name.ifBlank { "that number" }, l.number, "", false), false, false)
            }
        }
    }

    // ---------- List / notes ----------

    private fun listItems(list: String): MutableList<String> {
        val raw = prefs.getString("list_$list", null) ?: return ArrayList()
        return try {
            val a = org.json.JSONArray(raw)
            MutableList(a.length()) { a.getString(it) }
        } catch (e: Exception) { ArrayList() }
    }

    private fun saveList(list: String, items: List<String>) {
        prefs.edit().putString("list_$list", org.json.JSONArray(items).toString()).apply()
    }

    private fun listReply(c: ListCmd): Reply {
        val items = listItems(c.list)
        val name = if (c.list == "notes") "notes" else "list"
        val nameHi = if (c.list == "notes") "नोट्स" else "लिस्ट"
        return when (c.op) {
            "add" -> {
                val parts = if (c.list == "notes") listOf(c.item)
                else c.item.split(Regex("""\s*(?:,|\baur\b|\band\b)\s*""")).map { it.trim() }.filter { it.isNotBlank() }
                val txt = parts.map { if (c.list == "notes") messageText(it) else it }
                items.addAll(txt.filter { t -> items.none { it.equals(t, true) } })
                saveList(c.list, items)
                Reply("Added to your $name: ${txt.joinToString(", ")}", "$nameHi में जोड़ दिया: ${txt.joinToString(", ")}", endChat = true)
            }
            "read" -> if (items.isEmpty()) Reply("Your $name is empty", "आपकी $nameHi खाली है", endChat = true)
            else Reply(
                "Your $name (${items.size}):\n" + items.joinToString("\n") { "• $it" },
                "आपकी $nameHi में ${items.size} चीज़ें हैं: " + items.take(10).joinToString(", "),
                endChat = true,
                spokenEn = "Your $name has ${items.size}: " + items.take(10).joinToString(", ")
            )
            "clear" -> { saveList(c.list, emptyList()); Reply("Cleared your $name", "$nameHi साफ़ कर दी", endChat = true) }
            else -> {
                val q = c.item.lowercase()
                val hit = items.firstOrNull { it.lowercase() == q } ?: items.firstOrNull { it.lowercase().contains(q) || q.contains(it.lowercase()) }
                if (hit == null) Reply("“${c.item}” isn't on your $name", "${c.item} $nameHi में नहीं है", endChat = true)
                else { items.remove(hit); saveList(c.list, items); Reply("Removed $hit", "$hit हटा दिया", endChat = true) }
            }
        }
    }

    // ---------- Calendar ----------

    private fun calendarReply(c: CalendarCmd): Reply {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (c.day == "tomorrow") add(Calendar.DAY_OF_YEAR, 1)
        }
        val dayEn = if (c.day == "tomorrow") "tomorrow" else "today"
        val dayHi = if (c.day == "tomorrow") "कल" else "आज"
        if (c.op == "add") {
            val w = c.at
            val begin = (start.clone() as Calendar).apply {
                if (w != null && w.inMinutes >= 0) { timeInMillis = System.currentTimeMillis() + w.inMinutes * 60_000L }
                else if (w != null && w.hour >= 0) {
                    var h = w.hour % 12
                    if (w.period == "pm" || (w.period.isBlank() && w.hour in 1..7)) h += 12 // "4 baje meeting" = shaam 4
                    if (w.period == "am" && w.hour == 12) h = 0
                    set(Calendar.HOUR_OF_DAY, if (w.hour == 12 && w.period != "am") 12 else h)
                    set(Calendar.MINUTE, w.minute)
                } else set(Calendar.HOUR_OF_DAY, 10)
            }
            val i = Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(android.provider.CalendarContract.Events.TITLE, c.title.replaceFirstChar { it.uppercase() })
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.timeInMillis)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, begin.timeInMillis + 3600_000L)
            val h = begin.get(Calendar.HOUR_OF_DAY); val m = begin.get(Calendar.MINUTE)
            val tl = if (w != null) " at ${TimeParse.label(h, m)}" else ""
            val tlHi = if (w != null) " ${TimeParse.labelHi(h, m)} बजे" else ""
            return Reply("${c.title} $dayEn$tl — tap Save", "$dayHi$tlHi की ${c.title} कैलेंडर में भर दी है, सेव दबाइए",
                { openExternal(i, "Calendar") }, endChat = true)
        }
        if (!granted(Manifest.permission.READ_CALENDAR)) return needPermission(Manifest.permission.READ_CALENDAR, "calendar", "कैलेंडर")
        val end = start.timeInMillis + 24 * 3600_000L
        val events = ArrayList<Pair<String, Long>>()
        try {
            val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().let {
                android.content.ContentUris.appendId(it, start.timeInMillis); android.content.ContentUris.appendId(it, end); it.build()
            }
            ctx.contentResolver.query(uri, arrayOf(android.provider.CalendarContract.Instances.TITLE,
                android.provider.CalendarContract.Instances.BEGIN, android.provider.CalendarContract.Instances.ALL_DAY),
                null, null, android.provider.CalendarContract.Instances.BEGIN + " ASC")?.use {
                while (it.moveToNext() && events.size < 8) events.add((it.getString(0) ?: "Event") to (if (it.getInt(2) == 1) -1L else it.getLong(1)))
            }
        } catch (e: Exception) { }
        if (events.isEmpty()) return Reply("Nothing on your calendar $dayEn", "$dayHi कैलेंडर में कुछ नहीं है", endChat = true)
        fun t(at: Long, hi: Boolean): String {
            if (at < 0) return if (hi) "पूरा दिन" else "all day"
            val k = Calendar.getInstance().apply { timeInMillis = at }
            return if (hi) TimeParse.labelHi(k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE)) + " बजे"
            else TimeParse.label(k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE))
        }
        return Reply(
            "${events.size} on your calendar $dayEn:\n" + events.joinToString("\n") { "• ${t(it.second, false)} — ${it.first}" },
            "$dayHi ${events.size} चीज़ें हैं: " + events.take(5).joinToString(", ") { "${t(it.second, true)} ${it.first}" },
            endChat = true,
            spokenEn = "You have ${events.size} $dayEn: " + events.take(5).joinToString(", ") { "${it.first} at ${t(it.second, false)}" }
        )
    }

    private fun openAppSettings() {
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
    }
}
