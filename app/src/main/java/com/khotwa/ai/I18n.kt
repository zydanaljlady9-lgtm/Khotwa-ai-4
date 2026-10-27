package com.khotwa.ai

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** لغة واجهة يدعمها خُطوة، مع لهجات التعرف على الكلام المتاحة لها. */
data class Lang(
    val code: String,
    val native: String,
    val english: String,
    val rtl: Boolean,
    val voices: List<Pair<String, String>>
)

/**
 * نظام اللغات: واجهة بتسع لغات تتبدّل فوراً دون إعادة تشغيل.
 * النصوص في res/values-xx/strings.xml، وهذا الكائن يختار المورد المناسب ويُعلم Compose بالتغيير.
 */
object L {
    val ALL = listOf(
        Lang(
            "ar", "العربية", "Arabic", true,
            listOf(
                "ar-SA" to "السعودية والخليج", "ar-YE" to "اليمن", "ar-EG" to "مصر",
                "ar-AE" to "الإمارات", "ar-JO" to "الشام", "ar-IQ" to "العراق", "ar-MA" to "المغرب العربي",
                "ar-DZ" to "الجزائر", "ar-TN" to "تونس", "ar-LB" to "لبنان", "ar-QA" to "قطر", "ar-OM" to "عُمان", "ar-SY" to "سوريا"
            )
        ),
        Lang("en", "English", "English", false, listOf("en-US" to "United States", "en-GB" to "United Kingdom", "en-IN" to "India", "en-AU" to "Australia", "en-CA" to "Canada", "en-NZ" to "New Zealand", "en-ZA" to "South Africa", "en-IE" to "Ireland")),
        Lang("fr", "Français", "French", false, listOf("fr-FR" to "France", "fr-CA" to "Canada", "fr-BE" to "Belgique", "fr-CH" to "Suisse", "fr-MA" to "Maroc")),
        Lang("es", "Español", "Spanish", false, listOf("es-ES" to "España", "es-MX" to "México", "es-US" to "Estados Unidos", "es-AR" to "Argentina", "es-CO" to "Colombia", "es-CL" to "Chile")),
        Lang("pt", "Português", "Portuguese", false, listOf("pt-BR" to "Brasil", "pt-PT" to "Portugal", "pt-AO" to "Angola")),
        Lang("tr", "Türkçe", "Turkish", false, listOf("tr-TR" to "Türkiye", "tr-CY" to "Kıbrıs")),
        Lang("id", "Bahasa Indonesia", "Indonesian", false, listOf("id-ID" to "Indonesia", "id-MY" to "Malaysia")),
        Lang("hi", "हिन्दी", "Hindi", false, listOf("hi-IN" to "भारत")),
        Lang("ur", "اردو", "Urdu", true, listOf("ur-PK" to "پاکستان", "ur-IN" to "بھارت"))
    )

    /** كود لغة الواجهة الحالي. قراءته داخل Composable تجعل الشاشة تتحدّث عند تغييره. */
    var code by mutableStateOf("en")
        private set

    @Volatile
    private var res: Resources? = null

    val current: Lang get() = ALL.firstOrNull { it.code == code } ?: ALL[1]

    val locale: Locale get() = Locale.forLanguageTag(code)

    fun byCode(c: String): Lang? = ALL.firstOrNull { it.code == c }

    /** أول تشغيل: اللغة المحفوظة، وإلا لغة الهاتف إن كانت مدعومة، وإلا الإنجليزية. */
    fun init(c: Context) {
        val sp = Prefs.sp(c)
        var saved = sp.getString("ui", null)
        if (saved == null) {
            // ترقية من الإصدارات القديمة: كانت تحفظ لهجة الصوت فقط (مثل ar-YE) والواجهة عربية.
            val oldVoice = sp.getString("lang", null)
            saved = if (oldVoice != null) oldVoice.substringBefore('-') else systemLanguage()
            sp.edit().putString("ui", saved).apply()
        }
        apply(c, byCode(saved)?.code ?: "en")
    }

    fun systemLanguage(): String {
        val sys = Resources.getSystem().configuration.locales
        for (i in 0 until sys.size()) {
            var l = sys[i].language
            if (l == "in") l = "id"
            if (byCode(l) != null) return l
        }
        return "en"
    }

    /** يغيّر لغة الواجهة ويختار لهجة صوت مناسبة لها إن لزم. */
    fun set(c: Context, newCode: String) {
        val lang = byCode(newCode) ?: return
        Prefs.sp(c).edit().putString("ui", lang.code).apply()
        if (!GuideState.language.startsWith(lang.code)) {
            Prefs.setLanguage(c, lang.voices.first().first)
        }
        apply(c, lang.code)
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                c.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    LocaleList.forLanguageTags(lang.code)
            } catch (_: Throwable) {
            }
        }
    }

    private fun apply(c: Context, newCode: String) {
        val cfg = Configuration(c.applicationContext.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(newCode))
        cfg.setLayoutDirection(Locale.forLanguageTag(newCode))
        res = c.applicationContext.createConfigurationContext(cfg).resources
        code = newCode
    }

    /** نص مترجم. */
    fun s(id: Int): String {
        @Suppress("UNUSED_VARIABLE") val track = code
        return try {
            res?.getString(id) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    /** نص مترجم مع قيم (مثل %1$s). */
    fun f(id: Int, vararg args: Any): String {
        val pattern = s(id)
        return try {
            String.format(locale, pattern, *args)
        } catch (_: Throwable) {
            pattern
        }
    }

    /** اسم اللغة بالإنجليزية لتعليمات الذكاء الاصطناعي. */
    fun aiLanguage(): String = if (code == "ar") "Arabic (simple Modern Standard Arabic that every Arab understands)" else current.english
}
