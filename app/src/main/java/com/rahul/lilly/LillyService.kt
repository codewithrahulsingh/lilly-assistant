package com.rahul.lilly

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Lilly ka asli engine. Foreground service me chalta hai, isliye
 * app band ho ya phone lock ho, tab bhi "Lilly" sunti rehti hai.
 */
class LillyService : Service() {

    companion object {
        const val ACTION_STOP = "com.rahul.lilly.STOP"
        const val ACTION_ACTIVATE = "com.rahul.lilly.ACTIVATE"
        const val ACTION_NEXT_VOICE = "com.rahul.lilly.NEXT_VOICE"
        const val ACTION_TEST_VOICE = "com.rahul.lilly.TEST_VOICE"
        private const val START_CHANNEL = "lilly_start"
        private const val START_NOTIF_ID = 8

        /** Jab background se app seedha na khul paaye, tab ek tap wala notification */
        fun notifyOpen(ctx: Context, label: String, intent: Intent) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("lilly_open", "Open app", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                }
            )
            val pi = PendingIntent.getActivity(
                ctx, 5, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, "lilly_open")
                .setSmallIcon(R.drawable.ic_stat_lilly)
                .setContentTitle("Tap to open $label")
                .setContentText("Lilly")
                .setColor(0xFF7B2FF7.toInt())
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(30_000)
                .build()
            try { nm.notify(10, n) } catch (e: Exception) { }
        }

        /** Jab Lilly background se khud shuru na ho paaye (jaise phone restart), tab tap karke chalu karne wala notification */
        fun notifyTapToStart(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(START_CHANNEL, "Start Lilly", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val open = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, START_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lilly)
                .setContentTitle("Lilly is asleep")
                .setContentText("Tap and Lilly will start listening again")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try { nm.notify(START_NOTIF_ID, n) } catch (e: Exception) { }
        }
        const val CHANNEL = "lilly_bg"
        private const val NOTIF_ID = 7

        fun start(ctx: Context, action: String? = null) {
            val i = Intent(ctx, LillyService::class.java).setAction(action)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, LillyService::class.java).setAction(ACTION_STOP))
        }
    }

    private enum class Mode { WAKE, COMMAND, SPEAKING, STOPPED }

    private lateinit var brain: LillyBrain
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mode = Mode.STOPPED
    private var afterSpeak: (() -> Unit)? = null
    private var started = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastEvent = 0L
    private var voskModel: Model? = null
    private var voskService: SpeechService? = null
    private var voskRec: Recognizer? = null
    private var voskLoading = false
    private var floating: FloatingOrb? = null
    private var retries = 0
    private var followUp = false
    private var hasFocus = false
    private val aiHistory = ArrayList<AiChat.Turn>()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    /** Lilly sunte waqt gaana thodi der ke liye ruk jaata hai (Siri jaisa) */
    @Suppress("DEPRECATION")
    private fun holdAudioFocus(hold: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (hold && !hasFocus) {
                hasFocus = am.requestAudioFocus(
                    focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else if (!hold && hasFocus) {
                am.abandonAudioFocus(focusListener)
                hasFocus = false
            }
        } catch (e: Exception) { }
    }
    private var voiceList: List<Voice> = emptyList()

    // ---------------- Mic ki "beep" chup ----------------
    // Google ka speech recognizer har baar sunna shuru/khatam karte waqt beep bajata hai.
    // Us pal ke liye awaaz mute kar dete hain, phir turant wapas chalu.
    private var mutedStreams: List<Int> = emptyList()
    private val unmuteRunnable = Runnable { muteBeep(false) }

    private fun muteBeep(on: Boolean) {
        handler.removeCallbacks(unmuteRunnable)
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (on) {
            if (mutedStreams.isEmpty()) {
                val want = arrayListOf(AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION)
                // Gaana baj raha ho (aur Lilly ne roka na ho) toh use mat chhedo
                val musicPlaying = try { am.isMusicActive } catch (e: Exception) { false }
                if (hasFocus || !musicPlaying) want.add(AudioManager.STREAM_MUSIC)
                val done = ArrayList<Int>()
                for (st in want) {
                    try {
                        // Jo user ne khud mute kiya hai use hum baad me unmute na kar dein
                        if (!am.isStreamMute(st)) {
                            am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, 0)
                            done.add(st)
                        }
                    } catch (e: Exception) { }
                }
                mutedStreams = done
                prefs.edit().putString("muted_streams", done.joinToString(",")).apply()
            }
            // Kuch bhi atke, 3 sec me awaaz wapas aa hi jaaye
            handler.postDelayed(unmuteRunnable, 3000)
        } else if (mutedStreams.isNotEmpty()) {
            for (st in mutedStreams) {
                try { am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) { }
            }
            mutedStreams = emptyList()
            prefs.edit().remove("muted_streams").apply()
        }
    }

    private fun unmuteSoon(delay: Long) {
        handler.removeCallbacks(unmuteRunnable)
        handler.postDelayed(unmuteRunnable, delay)
    }

    /** Pichhli baar app achanak band hua ho aur awaaz mute reh gayi ho toh wapas chalu karo */
    private fun restoreMutedStreams() {
        val saved = prefs.getString("muted_streams", null) ?: return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        for (part in saved.split(",")) {
            val st = part.trim().toIntOrNull() ?: continue
            try { am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) { }
        }
        prefs.edit().remove("muted_streams").apply()
    }
    private val prefs by lazy { getSharedPreferences("lilly", Context.MODE_PRIVATE) }

    /** Recognizer kabhi-kabhi atak jaata hai — har 10 sec dekho, 20 sec se chup ho toh naya bana do */
    private val watchdog: Runnable = Runnable {
        if (mode == Mode.WAKE && System.currentTimeMillis() - lastEvent > 20_000) {
            stopVosk()
            try { recognizer?.destroy() } catch (e: Exception) { }
            recognizer = null
            listen(Mode.WAKE)
        } else if (mode == Mode.COMMAND && System.currentTimeMillis() - lastEvent > 15_000) {
            try { recognizer?.destroy() } catch (e: Exception) { }
            recognizer = null
            endSession()
            listen(Mode.WAKE)
        }
        scheduleWatchdog()
    }
    private fun scheduleWatchdog() {
        if (mode != Mode.STOPPED) handler.postDelayed(watchdogRef(), 10_000)
    }
    private fun watchdogRef(): Runnable = watchdog
    private val handler = Handler(Looper.getMainLooper())
    private val wakeRunnable: Runnable = Runnable {
        if (mode == Mode.SPEAKING || mode == Mode.STOPPED) return@Runnable
        // Phone call chal rahi ho toh mic mat chhedo, thodi der baad dobara dekho
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION) {
            LillyBus.status("On a call…")
            handler.postDelayed(wakeRunnableRef(), 3000)
        } else listen(Mode.WAKE)
    }
    private fun wakeRunnableRef(): Runnable = wakeRunnable

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!goForeground()) {
            notifyTapToStart(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            brain = LillyBrain(this)
            english = prefs.getBoolean("reply_en", false)
            restoreMutedStreams()
            initTts(useGoogle = true)
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lilly::listen").apply {
                setReferenceCounted(false)
                acquire()
            }
            LillyBus.running = true
            getSystemService(NotificationManager::class.java).cancel(START_NOTIF_ID)
            loadWakeModel()
            listen(Mode.WAKE)
            scheduleWatchdog()
        }
        when (intent?.action) {
            ACTION_ACTIVATE -> activate(fromTap = true)
            ACTION_NEXT_VOICE -> nextVoice()
            ACTION_TEST_VOICE -> testVoice()
        }
        return START_STICKY
    }

    // ---------------- Notification ----------------

    private fun goForeground(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL, "Running in background (you can turn this off)", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
        try { nm.deleteNotificationChannel("lilly_listening") } catch (e: Exception) { }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LillyService::class.java).setAction(ACTION_STOP), flags
        )
        val talk = PendingIntent.getService(
            this, 2, Intent(this, LillyService::class.java).setAction(ACTION_ACTIVATE), flags
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lilly)
            .setContentTitle("Lilly is listening")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .addAction(Notification.Action.Builder(null, "Talk", talk).build())
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun orbBitmap(): Bitmap {
        val size = 128
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            size * 0.38f, size * 0.36f, size * 0.7f,
            intArrayOf(0xFF7FE9FF.toInt(), 0xFF7B2FF7.toInt(), 0xFFF107A3.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        return b
    }

    // ---------------- Voice (TTS) ----------------

    /**
     * Sabse natural Hindi female awaaz chuno. Google ki "network" (neural) awaazein
     * offline wali se kaafi zyada insaan jaisi lagti hain, isliye unhe pehle rakha hai.
     */
    /** Google ka TTS engine sabse natural Hindi awaaz deta hai, isliye pehle wahi try karo */
    private fun initTts(useGoogle: Boolean) {
        val listener = TextToSpeech.OnInitListener { st ->
            handler.post {
                if (mode == Mode.STOPPED) return@post
                if (st == TextToSpeech.SUCCESS) setupVoice()
                else if (useGoogle) {
                    try { tts?.shutdown() } catch (e: Exception) { }
                    initTts(useGoogle = false)
                }
            }
        }
        tts = try {
            if (useGoogle) TextToSpeech(this, listener, "com.google.android.tts") else TextToSpeech(this, listener)
        } catch (e: Exception) {
            TextToSpeech(this, listener)
        }
    }

    private fun rankVoices(t: TextToSpeech): List<Voice> {
        val all = try { t.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        val hindi = all.filter { it.locale.language == "hi" && !it.features.contains("notInstalled") }
        fun score(v: Voice): Int {
            var sc = v.quality
            if (v.isNetworkConnectionRequired) sc += 150
            val n = v.name.lowercase(Locale.ROOT)
            if ("female" in n) sc += 100
            if ("-hia-" in n || n.endsWith("-hia") || "-hic-" in n || n.endsWith("-hic")) sc += 80
            if ("male" in n && "female" !in n) sc -= 100
            if ("-hid" in n || "-hie" in n) sc -= 60
            return sc
        }
        return hindi.sortedByDescending { score(it) }
    }

    private fun applyVoice(t: TextToSpeech, v: Voice?) {
        if (v != null) t.voice = v else t.language = Locale("hi", "IN")
        // Neural awaaz ko normal pitch par rakho, warna robotic lagti hai
        val neural = v?.isNetworkConnectionRequired == true || (v?.quality ?: 0) >= Voice.QUALITY_HIGH
        t.setPitch(if (neural) 1.0f else 1.08f)
        t.setSpeechRate(if (neural) 1.0f else 0.95f)
    }

    private fun setupVoice() {
        val t = tts ?: return
        voiceList = rankVoices(t)
        prefs.edit().putBoolean("has_hi_voice", voiceList.isNotEmpty()).apply()
        val saved = prefs.getString("voice", null)
        val chosen = voiceList.firstOrNull { it.name == saved } ?: voiceList.firstOrNull()
        applyVoice(t, chosen)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = finishSpeaking(id)
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = finishSpeaking(id)
            override fun onError(id: String?, errorCode: Int) {
                // Network awaaz fail ho (internet nahi) toh offline awaaz par aa jao
                if (tts?.voice?.isNetworkConnectionRequired == true) {
                    handler.post {
                        val offline = voiceList.firstOrNull { !it.isNetworkConnectionRequired }
                        tts?.let { applyVoice(it, offline) }
                    }
                }
                finishSpeaking(id)
            }
        })
        ttsReady = true
    }

    private fun voiceLabel(): String {
        val v = tts?.voice ?: return "default"
        val idx = voiceList.indexOfFirst { it.name == v.name } + 1
        val kind = if (v.isNetworkConnectionRequired) "natural (online)" else "offline"
        return "Voice $idx/${voiceList.size} — $kind"
    }

    private fun nextVoice() {
        val t = tts ?: return
        if (voiceList.isEmpty()) {
            sayBi("No Hindi voice found on this phone. Please download Google voice data in Settings", "हिंदी आवाज़ नहीं मिली")
            return
        }
        val cur = voiceList.indexOfFirst { it.name == t.voice?.name }
        val next = voiceList[(cur + 1) % voiceList.size]
        prefs.edit().putString("voice", next.name).apply()
        applyVoice(t, next)
        testVoice()
    }

    private fun testVoice() {
        val back = mode == Mode.WAKE || mode == Mode.STOPPED
        val sample = if (english) "Hello, I'm Lilly. How does this voice sound?" else "नमस्ते, मैं लिली हूँ। ये आवाज़ कैसी लगी?"
        say(voiceLabel(), sample) { if (back) listen(Mode.WAKE) }
    }

    private var currentUtterance: String? = null
    private val speakTimeout = Runnable { finishSpeaking(currentUtterance) }

    private fun finishSpeaking(id: String?) {
        handler.post {
            if (mode == Mode.STOPPED) return@post
            // Purane vakya ka signal ho toh ignore karo (warna Lilly apni hi awaaz sunne lagti hai)
            if (id == null || id != currentUtterance) return@post
            currentUtterance = null
            handler.removeCallbacks(speakTimeout)
            val next = afterSpeak
            afterSpeak = null
            if (mode == Mode.SPEAKING) mode = Mode.WAKE
            if (next != null) next.invoke() else restartLater(200)
        }
    }

    /** Pichhli baar user ne English me bola tha ya Hindi/Hinglish me — Lilly usi bhasha me bolti hai */
    private var english = false

    private fun noteLanguage(heard: String) {
        english = Parser.isEnglish(heard)
        prefs.edit().putBoolean("reply_en", english).apply()
    }

    /** Screen par hamesha English; bolna user ki bhasha me (English ya Hindi) */
    private fun sayBi(en: String, hi: String, then: (() -> Unit)? = null) {
        // Bolne ke liye emoji / chinh hata do
        val enSpoken = en.replace(Regex("[^\\p{L}\\p{N} .,!?:'%-]"), " ").replace("%", " percent").trim()
        say(en, if (english || hi.isBlank()) enSpoken else hi, then)
    }

    private fun say(display: String, spoken: String, then: (() -> Unit)? = null) {
        if (mode == Mode.STOPPED) return
        handler.removeCallbacks(wakeRunnable)
        try { recognizer?.cancel() } catch (e: Exception) { }
        stopVosk()
        mode = Mode.SPEAKING
        LillyBus.status(display)
        LillyBus.orb(OrbView.State.SPEAKING)
        if (mutedStreams.isNotEmpty()) {
            // Recognizer ki "khatam" wali beep nikal jaane do, phir awaaz kholkar bolo
            handler.postDelayed({
                muteBeep(false)
                say(display, spoken, then)
            }, 250)
            return
        }
        val t = tts
        if (!ttsReady || t == null) {
            handler.postDelayed({
                if (mode == Mode.SPEAKING) mode = Mode.WAKE
                if (then != null) then.invoke() else restartLater(200)
            }, 1200)
            return
        }
        afterSpeak = then
        val id = "lilly-" + System.nanoTime()
        currentUtterance = id
        handler.removeCallbacks(speakTimeout)
        val result = t.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            finishSpeaking(id)
        } else {
            // Engine "khatam" na bataye toh bhi aage badho: lambai ke hisaab se intezaar
            handler.postDelayed(speakTimeout, (4000L + spoken.length * 130L).coerceAtMost(30_000L))
        }
    }

    // ---------------- Listening ----------------

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (recognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                LillyBus.status("Speech recognition isn't available on this phone. Please install or update the Google app.")
                return null
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        }
        return recognizer
    }

    private fun recognizerIntent(command: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // "auto": Hindi recognizer (jo Hinglish aur English shabd bhi samajhta hai) + English ko doosri bhasha ki tarah
        val pref = prefs.getString("lang", "auto") ?: "auto"
        val lang = if (pref == "en") "en-IN" else "hi-IN"
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        if (pref == "auto") {
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN"))
        }
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        if (command) {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        }
    }

    // ---------------- Offline wake word (Vosk) ----------------

    private fun loadWakeModel() {
        if (voskModel != null || voskLoading) return
        voskLoading = true
        StorageService.unpack(this, "model-en-in", "model",
            { model ->
                voskLoading = false
                voskModel = model
                if (mode == Mode.WAKE) listen(Mode.WAKE)
            },
            { _ ->
                // Model na chale toh purana tareeka (Google recognizer loop) chalta rahega
                voskLoading = false
            })
    }

    private val wakeGrammar =
        "[\"hey lily\", \"hi lily\", \"hello lily\", \"hey\", \"hi\", \"hello\", \"lily\", \"[unk]\"]"

    private fun startVosk(): Boolean {
        val model = voskModel ?: return false
        stopVosk()
        return try {
            val rec = Recognizer(model, 16000.0f, wakeGrammar)
            voskRec = rec
            voskService = SpeechService(rec, 16000.0f).also { it.startListening(voskListener) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopVosk() {
        val v = voskService
        voskService = null
        if (v != null) {
            try { v.stop() } catch (e: Exception) { }
            try { v.shutdown() } catch (e: Exception) { }
        }
        val r = voskRec
        voskRec = null
        try { r?.close() } catch (e: Exception) { }
    }

    private fun isWakeText(json: String?, key: String): Boolean {
        if (json.isNullOrBlank()) return false
        val text = try { JSONObject(json).optString(key) } catch (e: Exception) { "" }
        // Bolte-bolte (partial) sirf "hey lily" par jaago; vakya poora hone par akela "lily" bhi chalega
        val re = if (key == "partial") Regex("""\b(hey|hi|hello)\s+lil+y\b""") else Regex("""\blil+y\b""")
        return re.containsMatchIn(text)
    }

    private val voskListener = object : org.vosk.android.RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            lastEvent = System.currentTimeMillis()
            // Poora vakya khatam hone ka intezaar nahi — "hey lily" sunte hi jaag jao
            if (mode == Mode.WAKE && isWakeText(hypothesis, "partial")) {
                stopVosk()
                activate(fromTap = false)
            }
        }
        override fun onResult(hypothesis: String?) {
            lastEvent = System.currentTimeMillis()
            if (mode == Mode.WAKE && isWakeText(hypothesis, "text")) {
                stopVosk()
                activate(fromTap = false)
            }
        }
        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(exception: Exception?) { restartLater(1500) }
        override fun onTimeout() { restartLater(300) }
    }

    // ---------------- Listening ----------------

    private fun listen(newMode: Mode) {
        if (mode == Mode.STOPPED && !started) return
        if (newMode == Mode.WAKE && voskModel != null) {
            handler.removeCallbacks(wakeRunnable)
            try { recognizer?.cancel() } catch (e: Exception) { }
            mode = Mode.WAKE
            LillyBus.orb(OrbView.State.IDLE)
            LillyBus.status("Say \"Lilly\" or \"Hey Lilly\"")
            lastEvent = System.currentTimeMillis()
            if (!startVosk()) restartLater(1500)
            return
        }
        stopVosk()
        val r = ensureRecognizer() ?: return
        handler.removeCallbacks(wakeRunnable)
        mode = newMode
        if (newMode == Mode.COMMAND) {
            LillyBus.orb(OrbView.State.LISTENING)
            LillyBus.status("Listening…")
        } else {
            LillyBus.orb(OrbView.State.IDLE)
            LillyBus.status("Say \"Lilly\" or \"Hey Lilly\"")
        }
        lastEvent = System.currentTimeMillis()
        try {
            r.cancel()
            muteBeep(true)
            r.startListening(recognizerIntent(newMode == Mode.COMMAND))
        } catch (e: Exception) {
            muteBeep(false)
            restartLater(800)
        }
    }

    private fun restartLater(delay: Long = 250) {
        if (mode == Mode.STOPPED) return
        handler.removeCallbacks(wakeRunnable)
        handler.postDelayed(wakeRunnable, delay)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { unmuteSoon(350) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            if (mode == Mode.COMMAND) LillyBus.level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { muteBeep(true) }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            lastEvent = System.currentTimeMillis()
            unmuteSoon(450)
            when (mode) {
                Mode.COMMAND ->
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        if (followUp) {
                            followUp = false
                            endSession()
                            listen(Mode.WAKE)
                        } else if (retries < 1) {
                            retries++
                            listen(Mode.COMMAND)
                        } else {
                            sayBi("I didn't hear anything", "कुछ सुनाई नहीं दिया") { endSession(); listen(Mode.WAKE) }
                        }
                    } else { endSession(); restartLater(600) }
                Mode.WAKE -> restartLater(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000 else 250)
                else -> {}
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (mode == Mode.COMMAND && !t.isNullOrBlank()) {
                lastEvent = System.currentTimeMillis()
                LillyBus.heard(brain.roman(t))
            }
        }

        override fun onResults(results: Bundle?) {
            lastEvent = System.currentTimeMillis()
            unmuteSoon(450)
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
            handleResults(list)
        }
    }

    private fun handleResults(list: List<String>) {
        when (mode) {
            Mode.WAKE -> {
                val hit = list.firstOrNull { brain.hasWakeWord(it) }
                if (hit == null) {
                    restartLater()
                    return
                }
                val rest = brain.stripWakeWord(hit)
                if (brain.looksLikeCommand(rest)) { showOverlay(); runCommand(rest, list) } else activate(fromTap = false)
            }
            Mode.COMMAND -> {
                val text = list.firstOrNull { it.isNotBlank() }
                if (text == null) {
                    endSession()
                    listen(Mode.WAKE)
                    return
                }
                runCommand(brain.stripWakeWord(text).ifBlank { text }, list)
            }
            else -> {}
        }
    }

    private var tone: ToneGenerator? = null

    /** Siri jaisa: chhoti si "ting", orb chamakta hai aur turant sunna shuru */
    private fun activate(fromTap: Boolean) {
        if (mode == Mode.SPEAKING || mode == Mode.COMMAND) return
        stopVosk()
        LillyBus.heard("")
        if (!fromTap) showOverlay()
        retries = 0
        followUp = false
        aiHistory.clear()
        holdAudioFocus(true)
        sayBi("Yes Rahul, how can I help?", "हाँ राहुल, बोलो क्या करूँ") { listen(Mode.COMMAND) }
    }

    // ---------------- Siri jaisa popup (lock screen par bhi) ----------------

    private val liveChannel = "lilly_live"
    private val liveId = 9

    private fun showOverlay() {
        if (LillyBus.ui != null) return // Lilly ki screen pehle se khuli hai
        if (Settings.canDrawOverlays(this)) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (brain.isLocked() || !pm.isInteractive) {
                // Lock screen / screen band: popup seedha khulta hai aur screen jagata hai
                // Saath me neeche wala full-screen notification bhi jaata hai, taaki ek na chale toh doosra chale
                try {
                    startActivity(
                        Intent(this, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) { }
            } else if (Build.VERSION.SDK_INT >= 26) {
                // Home screen / koi bhi app: neeche se orb
                val f = floating ?: FloatingOrb(this).also { floating = it }
                if (f.show()) return
            }
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(liveChannel, "Lilly popup", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val pi = PendingIntent.getActivity(
            this, 4,
            Intent(this, OverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, liveChannel)
            .setSmallIcon(R.drawable.ic_stat_lilly)
            .setContentTitle("Lilly")
            .setContentText("Listening…")
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000)
            .build()
        try { nm.notify(liveId, n) } catch (e: Exception) { }
    }

    private fun endSession() {
        getSystemService(NotificationManager::class.java).cancel(liveId)
        LillyBus.endSession()
        floating?.hideSoon()
        holdAudioFocus(false)
        followUp = false
    }

    private fun runCommand(rawText: String, alternatives: List<String>) {
        val text = brain.roman(rawText) // screen par Roman lipi me dikhao
        noteLanguage(rawText)
        LillyBus.heard("“$text”")
        LillyBus.ui?.keepAlive()
        followUp = false
        val reply = brain.handle(text, alternatives)
        val question = reply.askAi
        if (question != null) {
            askAi(question)
            return
        }
        sayBi(reply.display, reply.spoken) {
            if (reply.endChat || reply.action != null) {
                endSession()
                reply.action?.invoke()
                // Call chal rahi ho toh mic chhod do, call khatam hone ke baad phir sunna shuru
                restartLater(if (reply.action != null) 4000 else 300)
            } else {
                // Baat-cheet chal rahi hai: bina "Lilly" bole agla jawab suno
                continueChat()
            }
        }
    }

    private fun continueChat() {
        followUp = true
        retries = 1
        LillyBus.ui?.keepAlive()
        listen(Mode.COMMAND)
    }

    /** Normal baat-cheet: sawal AI ko bhejo, jawab bol kar sunao */
    private fun askAi(question: String) {
        handler.removeCallbacks(wakeRunnable)
        try { recognizer?.cancel() } catch (e: Exception) { }
        mode = Mode.SPEAKING
        LillyBus.status("Thinking…")
        LillyBus.orb(OrbView.State.SPEAKING)
        LillyBus.ui?.keepAlive()
        val key = prefs.getString("ai_key", "") ?: ""
        val history = ArrayList(aiHistory)
        Thread {
            var failure: Throwable? = null
            val result = try { AiChat.ask(key, history, question) } catch (e: Exception) { failure = e; null }
            handler.post {
                if (mode == Mode.STOPPED) return@post
                if (result == null) {
                    // Asli wajah batao (limit, galat key, internet…), taaki pata chale kya theek karna hai
                    val why = AiChat.explain(failure)
                    sayBi(why.first, why.second) { endSession(); listen(Mode.WAKE) }
                    return@post
                }
                aiHistory.add(AiChat.Turn("user", question))
                aiHistory.add(AiChat.Turn("assistant", result.spoken))
                while (aiHistory.size > 8) aiHistory.removeAt(0)
                say(result.display, result.spoken) { continueChat() }
            }
        }.start()
    }

    // ---------------- Cleanup ----------------

    private fun shutdown() {
        mode = Mode.STOPPED
        started = false
        handler.removeCallbacksAndMessages(null)
        muteBeep(false)
        currentUtterance = null
        stopVosk()
        floating?.hide()
        holdAudioFocus(false)
        tone?.release()
        tone = null
        getSystemService(NotificationManager::class.java).cancel(liveId)
        try { recognizer?.cancel() } catch (e: Exception) { }
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        LillyBus.orb(OrbView.State.IDLE)
        LillyBus.status("Lilly is off. Tap the orb to start.")
        LillyBus.running = false
    }

    override fun onDestroy() {
        if (mode != Mode.STOPPED) shutdown()
        super.onDestroy()
    }
}
