package com.khotwa.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** يجمع التعرف على الكلام (المايك) والنطق بلغة المستخدم. كل الاستدعاءات من الخيط الرئيسي. */
class VoiceEngine(private val context: Context, private val handler: Handler) {

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onListenError(code: Int)
        fun onSpeakDone()
    }

    var listener: Listener? = null

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsTag = ""

    /** معرّف آخر قطعة في الشرح الحالي: انتهاء النطق يُعلن عند انتهائها فقط. */
    @Volatile
    private var lastUtterance = ""

    /** يضبط لغة النطق على لهجة المستخدم، وإن لم تتوفر فاللغة العامة. */
    private fun applyLanguage(engine: TextToSpeech, tag: String): Boolean {
        ttsTag = tag
        for (loc in listOf(Locale.forLanguageTag(tag), Locale(tag.substringBefore('-')))) {
            val r = try {
                engine.setLanguage(loc)
            } catch (_: Throwable) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) return true
        }
        return false
    }

    init {
        tts = TextToSpeech(context) { status ->
            handler.post {
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    ttsReady = applyLanguage(engine, GuideState.language)
                    engine.setSpeechRate(0.95f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == lastUtterance) handler.post { listener?.onSpeakDone() }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            lastUtterance = ""
                            handler.post { listener?.onSpeakDone() }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            lastUtterance = ""
                            handler.post { listener?.onSpeakDone() }
                        }
                    })
                }
            }
        }
    }

    fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** ينطق النص. يعيد false إن لم يكن النطق بهذه اللغة جاهزاً. */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (ttsTag != GuideState.language) ttsReady = applyLanguage(engine, GuideState.language)
        if (!ttsReady) return false
        val chunks = SpeechChunker.split(text)
        if (chunks.isEmpty()) return false
        return try {
            val base = "u" + System.nanoTime()
            lastUtterance = "$base-${chunks.size - 1}"
            var ok = true
            chunks.forEachIndexed { i, c ->
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (engine.speak(c, mode, null, "$base-$i") != TextToSpeech.SUCCESS) ok = false
            }
            ok
        } catch (_: Throwable) {
            false
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }

    fun startListening(languageTag: String) {
        stopListening()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                handler.post { if (recognizer === r) listener?.onListenError(error) }
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                handler.post {
                    if (recognizer === r) {
                        if (text.isBlank()) listener?.onListenError(SpeechRecognizer.ERROR_NO_MATCH)
                        else listener?.onFinal(text)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) handler.post { if (recognizer === r) listener?.onPartial(text) }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        }
        r.startListening(intent)
    }

    fun stopListening() {
        val r = recognizer ?: return
        recognizer = null
        try {
            r.cancel()
        } catch (_: Throwable) {
        }
        try {
            r.destroy()
        } catch (_: Throwable) {
        }
    }

    fun shutdown() {
        stopListening()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        ttsReady = false
    }
}
