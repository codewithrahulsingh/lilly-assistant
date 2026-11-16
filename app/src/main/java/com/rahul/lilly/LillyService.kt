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
import java.util.Locale

/**
 * Lilly ka asli engine. Foreground service me chalta hai, isliye
 * app band ho ya phone lock ho, tab bhi "Lilly" sunti rehti hai.
 */
class LillyService : Service() {

    companion object {
        const val ACTION_STOP = "com.rahul.lilly.STOP"
        const val ACTION_ACTIVATE = "com.rahul.lilly.ACTIVATE"
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
            listen(Mode.WAKE)
        }
        if (intent?.action == ACTION_ACTIVATE) activate()
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

    private fun setupVoice() {
        val t = tts ?: return
        val voices = try { t.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        val hindi = voices.filter { it.locale.language == "hi" }
        val offline = hindi.filter { !it.isNetworkConnectionRequired }.ifEmpty { hindi }
        val female = offline.firstOrNull { it.name.contains("female", ignoreCase = true) }
            ?: offline.firstOrNull { it.name.contains("hia") || it.name.contains("hic") }
            ?: offline.firstOrNull()
        if (female != null) t.voice = female else t.language = Locale("hi", "IN")
        t.setPitch(1.15f)
        t.setSpeechRate(1.0f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = finishSpeaking()
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = finishSpeaking()
        })
        ttsReady = true
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

    private fun listen(newMode: Mode) {
        if (mode == Mode.STOPPED && !started) return
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
            when (mode) {
                Mode.COMMAND ->
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        say("Kuch sunai nahi diya", "कुछ सुनाई नहीं दिया") { listen(Mode.WAKE) }
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
                if (brain.looksLikeCommand(rest)) runCommand(rest, list) else activate()
            }
            Mode.COMMAND -> {
                val text = list.firstOrNull { it.isNotBlank() }
                if (text == null) {
                    listen(Mode.WAKE)
                    return
                }
                runCommand(brain.stripWakeWord(text).ifBlank { text }, list)
            }
            else -> {}
        }
    }

    private fun activate() {
        if (mode == Mode.SPEAKING || mode == Mode.COMMAND) return
        LillyBus.heard("")
        say("Haan bolo", "हाँ बोलो") { listen(Mode.COMMAND) }
    }

    private fun runCommand(text: String, alternatives: List<String>) {
        LillyBus.heard("“$text”")
        val reply = brain.handle(text, alternatives)
        say(reply.display, reply.spoken) {
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
