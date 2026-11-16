package com.rahul.lilly

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private enum class Mode { WAKE, COMMAND, SPEAKING, PAUSED }

    private lateinit var orb: OrbView
    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var brain: LillyBrain

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mode = Mode.PAUSED
    private var afterSpeak: (() -> Unit)? = null
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private val wakeRunnable = Runnable { if (mode != Mode.SPEAKING) listen(Mode.WAKE) }

    private val perms = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildUi()
        brain = LillyBrain(this)
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) setupVoice() }
        askPermissions()
    }

    // ---------------- UI ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val title = TextView(this).apply {
            text = "Lilly"
            setTextColor(Color.WHITE)
            textSize = 36f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        root.addView(title, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = dp(72)
        })

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#A8A8C0"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            text = "Shuru ho rahi hoon…"
        }
        root.addView(status, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = dp(128)
        })

        heard = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(dp(28), 0, dp(28), 0)
        }
        root.addView(heard, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply {
            bottomMargin = dp(80)
        })

        orb = OrbView(this)
        root.addView(orb, FrameLayout.LayoutParams(dp(260), dp(260), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(36)
        })
        orb.setOnClickListener {
            if (mode == Mode.WAKE || mode == Mode.PAUSED) activate()
        }

        setContentView(root)
    }

    // ---------------- Permissions & lifecycle ----------------

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasMic()) startLoop() else status.text = "Lilly ko sunne ke liye Mic permission chahiye"
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        startLoop()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (e: Exception) { }
        tts?.stop()
        afterSpeak = null
        mode = Mode.PAUSED
        orb.state = OrbView.State.IDLE
        super.onPause()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        super.onDestroy()
    }

    private fun startLoop() {
        if (resumed && hasMic() && mode == Mode.PAUSED) listen(Mode.WAKE)
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
        runOnUiThread {
            val next = afterSpeak
            afterSpeak = null
            next?.invoke()
        }
    }

    private fun say(display: String, spoken: String, then: (() -> Unit)? = null) {
        handler.removeCallbacks(wakeRunnable)
        try { recognizer?.cancel() } catch (e: Exception) { }
        mode = Mode.SPEAKING
        status.text = display
        orb.state = OrbView.State.SPEAKING
        val t = tts
        if (!ttsReady || t == null) {
            handler.postDelayed({ then?.invoke() }, 1200)
            return
        }
        afterSpeak = then
        t.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "lilly-" + System.nanoTime())
    }

    // ---------------- Listening (speech recognition) ----------------

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (recognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                status.text = "Is phone me speech recognition nahi mila. Google app install/update karein."
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
        if (!resumed || !hasMic()) return
        val r = ensureRecognizer() ?: return
        handler.removeCallbacks(wakeRunnable)
        mode = newMode
        if (newMode == Mode.COMMAND) {
            orb.state = OrbView.State.LISTENING
            status.text = "Sun rahi hoon…"
        } else {
            orb.state = OrbView.State.IDLE
            status.text = "\"Lilly\" boliye ya orb dabaiye"
        }
        try {
            r.cancel()
            r.startListening(recognizerIntent(newMode == Mode.COMMAND))
        } catch (e: Exception) {
            restartLater(800)
        }
    }

    private fun restartLater(delay: Long = 250) {
        handler.removeCallbacks(wakeRunnable)
        handler.postDelayed(wakeRunnable, delay)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            if (mode == Mode.COMMAND) orb.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            if (!resumed) return
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
            if (mode == Mode.COMMAND && !t.isNullOrBlank()) heard.text = t
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
        heard.text = ""
        say("Haan bolo", "हाँ बोलो") { listen(Mode.COMMAND) }
    }

    private fun runCommand(text: String, alternatives: List<String>) {
        heard.text = "“$text”"
        val reply = brain.handle(text, alternatives)
        say(reply.display, reply.spoken) {
            reply.action?.invoke()
            if (resumed) restartLater(if (reply.action != null) 1500 else 300)
        }
    }
}
