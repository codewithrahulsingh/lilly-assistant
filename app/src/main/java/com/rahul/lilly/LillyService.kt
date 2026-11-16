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
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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

        /** Jab Lilly background se khud shuru na ho paaye (jaise phone restart), tab tap karke chalu karne wala notification */
        fun notifyTapToStart(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(START_CHANNEL, "Lilly shuru karein", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val open = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, START_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lilly)
                .setContentTitle("Lilly so rahi hai")
                .setContentText("Tap karein, Lilly phir se sunna shuru kar degi")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try { nm.notify(START_NOTIF_ID, n) } catch (e: Exception) { }
        }
        private const val CHANNEL = "lilly_listening"
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
    private var voskLoading = false
    private var voiceList: List<Voice> = emptyList()
    private val prefs by lazy { getSharedPreferences("lilly", Context.MODE_PRIVATE) }

    /** Recognizer kabhi-kabhi atak jaata hai — har 10 sec dekho, 20 sec se chup ho toh naya bana do */
    private val watchdog: Runnable = Runnable {
        if (mode == Mode.WAKE && System.currentTimeMillis() - lastEvent > 20_000) {
            stopVosk()
            try { recognizer?.destroy() } catch (e: Exception) { }
            recognizer = null
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
            LillyBus.status("Call chal rahi hai…")
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
            tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) handler.post { setupVoice() } }
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
            NotificationChannel(CHANNEL, "Lilly sun rahi hai", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
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
            .setContentTitle("Lilly sun rahi hai")
            .setContentText("\"Lilly\" boliye — lock screen par bhi")
            .setContentIntent(open)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "Baat karo", talk).build())
            .addAction(Notification.Action.Builder(null, "Band karo", stop).build())
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

    // ---------------- Voice (TTS) ----------------

    /**
     * Sabse natural Hindi female awaaz chuno. Google ki "network" (neural) awaazein
     * offline wali se kaafi zyada insaan jaisi lagti hain, isliye unhe pehle rakha hai.
     */
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
        val saved = prefs.getString("voice", null)
        val chosen = voiceList.firstOrNull { it.name == saved } ?: voiceList.firstOrNull()
        applyVoice(t, chosen)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = finishSpeaking()
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = finishSpeaking()
            override fun onError(id: String?, errorCode: Int) {
                // Network awaaz fail ho (internet nahi) toh offline awaaz par aa jao
                if (tts?.voice?.isNetworkConnectionRequired == true) {
                    handler.post {
                        val offline = voiceList.firstOrNull { !it.isNetworkConnectionRequired }
                        tts?.let { applyVoice(it, offline) }
                    }
                }
                finishSpeaking()
            }
        })
        ttsReady = true
    }

    private fun voiceLabel(): String {
        val v = tts?.voice ?: return "default"
        val idx = voiceList.indexOfFirst { it.name == v.name } + 1
        val kind = if (v.isNetworkConnectionRequired) "natural (online)" else "offline"
        return "Awaaz $idx/${voiceList.size} — $kind"
    }

    private fun nextVoice() {
        val t = tts ?: return
        if (voiceList.isEmpty()) {
            say("Phone me Hindi awaaz nahi mili. Settings me Google voice data download kijiye", "हिंदी आवाज़ नहीं मिली")
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
        say(voiceLabel(), "नमस्ते, मैं लिली हूँ। ये आवाज़ कैसी लगी?") { if (back) listen(Mode.WAKE) }
    }

    private fun finishSpeaking() {
        handler.post {
            val next = afterSpeak
            afterSpeak = null
            next?.invoke()
        }
    }

    private fun say(display: String, spoken: String, then: (() -> Unit)? = null) {
        if (mode == Mode.STOPPED) return
        handler.removeCallbacks(wakeRunnable)
        try { recognizer?.cancel() } catch (e: Exception) { }
        stopVosk()
        mode = Mode.SPEAKING
        LillyBus.status(display)
        LillyBus.orb(OrbView.State.SPEAKING)
        val t = tts
        if (!ttsReady || t == null) {
            handler.postDelayed({ then?.invoke() }, 1200)
            return
        }
        afterSpeak = then
        t.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "lilly-" + System.nanoTime())
    }

    // ---------------- Listening ----------------

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (recognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                LillyBus.status("Is phone me speech recognition nahi mila. Google app install/update karein.")
                return null
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        }
        return recognizer
    }

    private fun recognizerIntent(command: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        if (command) putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
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

    private val wakeGrammar = "[\"lily\", \"lilly\", \"hey lily\", \"[unk]\"]"

    private fun startVosk(): Boolean {
        val model = voskModel ?: return false
        stopVosk()
        return try {
            val rec = Recognizer(model, 16000.0f, wakeGrammar)
            voskService = SpeechService(rec, 16000.0f).also { it.startListening(voskListener) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopVosk() {
        val v = voskService ?: return
        voskService = null
        try { v.stop() } catch (e: Exception) { }
        try { v.shutdown() } catch (e: Exception) { }
    }

    private fun isWakeText(json: String?, key: String): Boolean {
        if (json.isNullOrBlank()) return false
        val text = try { JSONObject(json).optString(key) } catch (e: Exception) { "" }
        return Regex("""\blil+y\b""").containsMatchIn(text)
    }

    private val voskListener = object : org.vosk.android.RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            lastEvent = System.currentTimeMillis()
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
            LillyBus.status("\"Lilly\" boliye ya orb dabaiye")
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
            LillyBus.status("Sun rahi hoon…")
        } else {
            LillyBus.orb(OrbView.State.IDLE)
            LillyBus.status("\"Lilly\" boliye ya orb dabaiye")
        }
        lastEvent = System.currentTimeMillis()
        try {
            r.cancel()
            r.startListening(recognizerIntent(newMode == Mode.COMMAND))
        } catch (e: Exception) {
            restartLater(800)
        }
    }

    private fun restartLater(delay: Long = 250) {
        if (mode == Mode.STOPPED) return
        handler.removeCallbacks(wakeRunnable)
        handler.postDelayed(wakeRunnable, delay)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            if (mode == Mode.COMMAND) LillyBus.level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            lastEvent = System.currentTimeMillis()
            when (mode) {
                Mode.COMMAND ->
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        say("Kuch sunai nahi diya", "कुछ सुनाई नहीं दिया") { endSession(); listen(Mode.WAKE) }
                    } else restartLater(600)
                Mode.WAKE -> restartLater(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1000 else 250)
                else -> {}
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (mode == Mode.COMMAND && !t.isNullOrBlank()) LillyBus.heard(t)
        }

        override fun onResults(results: Bundle?) {
            lastEvent = System.currentTimeMillis()
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
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (e: Exception) { }
        LillyBus.orb(OrbView.State.LISTENING)
        handler.postDelayed({ listen(Mode.COMMAND) }, 250)
    }

    // ---------------- Siri jaisa popup (lock screen par bhi) ----------------

    private val liveChannel = "lilly_live"
    private val liveId = 9

    private fun showOverlay() {
        if (LillyBus.ui != null) return // Lilly ki screen pehle se khuli hai
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
            .setContentText("Sun rahi hoon…")
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
    }

    private fun runCommand(text: String, alternatives: List<String>) {
        LillyBus.heard("“$text”")
        val reply = brain.handle(text, alternatives)
        say(reply.display, reply.spoken) {
            endSession()
            reply.action?.invoke()
            // Call chal rahi ho toh mic chhod do, call khatam hone ke baad phir sunna shuru
            restartLater(if (reply.action != null) 4000 else 300)
        }
    }

    // ---------------- Cleanup ----------------

    private fun shutdown() {
        mode = Mode.STOPPED
        started = false
        handler.removeCallbacksAndMessages(null)
        stopVosk()
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
        LillyBus.status("Lilly band hai. Orb dabakar chalu karein.")
        LillyBus.running = false
    }

    override fun onDestroy() {
        if (mode != Mode.STOPPED) shutdown()
        super.onDestroy()
    }
}
