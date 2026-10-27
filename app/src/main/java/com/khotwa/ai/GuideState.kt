package com.khotwa.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** مراحل المساعد الصوتي، تنعكس على شكل الزر العائم. */
enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

/** حالة مشتركة بين الخدمة والواجهة (نفس العملية). */
object GuideState {
    var serviceConnected by mutableStateOf(false)
    var micGranted by mutableStateOf(false)
    var consent by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var phase by mutableStateOf(Phase.IDLE)
    var language by mutableStateOf("en-US")
    var speakReplies by mutableStateOf(false)
    var engine by mutableStateOf("")
    var hasKey by mutableStateOf(false)
    var vision by mutableStateOf(false)

    /** system | dark | light */
    var themeMode by mutableStateOf("system")

    /** short | balanced | detailed */
    var answerStyle by mutableStateOf("balanced")

    /** تعليمات يكتبها المستخدم لأسلوب إجابة المساعد. */
    var customInstructions by mutableStateOf("")
    var lastResult by mutableStateOf("")
    var screenLines by mutableStateOf<List<String>>(emptyList())

    /** أين تقف الآن: «Chrome · example.org · عنوان الصفحة». */
    var where by mutableStateOf("")

    /** هل تطبيق خُطوة ظاهر الآن؟ (الزر العائم يختفي داخله). */
    @Volatile
    var appForeground = false
}

/** تخزين بسيط للإعدادات. */
object Prefs {
    fun sp(c: Context) = c.getSharedPreferences("khotwa", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val s = sp(c)
        GuideState.consent = s.getBoolean("consent", false)
        L.init(c)
        GuideState.language = s.getString("lang", null) ?: L.current.voices.first().first
        GuideState.speakReplies = s.getBoolean("speak", false)
        GuideState.vision = s.getBoolean("vision", false)
        GuideState.themeMode = s.getString("theme", "system") ?: "system"
        GuideState.answerStyle = s.getString("style", "balanced") ?: "balanced"
        GuideState.customInstructions = s.getString("custom", "") ?: ""
        AiClient.chatStyle = GuideState.answerStyle
        AiClient.chatCustom = GuideState.customInstructions
        K.dark = resolveDark(GuideState.themeMode, systemDark(c))
        val k = s.getString("apikey", "") ?: ""
        AiClient.userKey = k
        GuideState.hasKey = k.isNotBlank()
    }

    fun apiKey(c: Context): String = sp(c).getString("apikey", "") ?: ""

    fun setApiKey(c: Context, key: String) {
        val k = key.trim()
        sp(c).edit().putString("apikey", k).apply()
        AiClient.userKey = k
        GuideState.hasKey = k.isNotBlank()
    }

    fun setConsent(c: Context, value: Boolean) {
        GuideState.consent = value
        sp(c).edit().putBoolean("consent", value).apply()
    }

    fun setLanguage(c: Context, tag: String) {
        GuideState.language = tag
        sp(c).edit().putString("lang", tag).apply()
    }

    fun setVision(c: Context, value: Boolean) {
        GuideState.vision = value
        sp(c).edit().putBoolean("vision", value).apply()
    }

    private fun systemDark(c: Context): Boolean =
        (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    fun setTheme(c: Context, mode: String) {
        GuideState.themeMode = mode
        sp(c).edit().putString("theme", mode).apply()
        K.dark = resolveDark(mode, systemDark(c))
    }

    fun setAnswerStyle(c: Context, style: String) {
        GuideState.answerStyle = style
        AiClient.chatStyle = style
        sp(c).edit().putString("style", style).apply()
    }

    fun setCustomInstructions(c: Context, text: String) {
        val t = text.take(600)
        GuideState.customInstructions = t
        AiClient.chatCustom = t
        sp(c).edit().putString("custom", t).apply()
    }

    /** يمسح كل ما حفظه التطبيق عن المحادثات الصوتية والأسماء التي علّمها المستخدم. */
    fun clearGuideData(c: Context) {
        sp(c).edit().remove("guide_hist").remove("aliases").apply()
        Aliases.load("")
    }

    fun setSpeak(c: Context, value: Boolean) {
        GuideState.speakReplies = value
        sp(c).edit().putBoolean("speak", value).apply()
    }

    fun buttonPos(c: Context): Pair<Int, Int>? {
        val s = sp(c)
        return if (s.contains("bx") && s.contains("by")) Pair(s.getInt("bx", 0), s.getInt("by", 0)) else null
    }

    fun saveButtonPos(c: Context, x: Int, y: Int) {
        sp(c).edit().putInt("bx", x).putInt("by", y).apply()
    }
}
