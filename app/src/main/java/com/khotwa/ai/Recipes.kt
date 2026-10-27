package com.khotwa.ai

/**
 * وصفة: لتطبيق أو إعداد معيّن، ومتى تُشغَّل، وخطواتها. كل خطوة قائمة كلمات (عربية وإنجليزية)،
 * يكفي أن يحتوي اسم أي عنصر على واحدة منها ليُعدّ الزر المطلوب.
 * الوصفات تعمل بلا شبكة ولا ذكاء، فتكون فورية ودقيقة في ما تغطيه. ويمكن توسيعها بإضافة سطر هنا.
 */
data class Recipe(
    val pkgs: List<String>,
    val triggers: List<String>,
    val steps: List<List<String>>
)

object Recipes {

    /** نتيجة: العنصر الذي يُشار إليه، ورقم الخطوة من عدد الخطوات. */
    data class Hit(val el: El, val step: Int, val total: Int)

    private const val SETTINGS = "com.android.settings"
    private const val SAMSUNG_SETTINGS = "com.samsung.android.app.settings"

    val ALL: List<Recipe> = listOf(
        Recipe(
            pkgs = listOf(SETTINGS, SAMSUNG_SETTINGS),
            triggers = listOf("واي فاي", "wifi", "wi-fi", "شبكة wi", "الانترنت اللاسلكي"),
            steps = listOf(
                listOf("connections", "network", "الاتصالات", "الشبكة والانترنت", "الشبكة والإنترنت", "الشبكه"),
                listOf("wi-fi", "wifi", "واي فاي", "الواي فاي")
            )
        ),
        Recipe(
            pkgs = listOf(SETTINGS, SAMSUNG_SETTINGS),
            triggers = listOf("بلوتوث", "bluetooth"),
            steps = listOf(
                listOf("connections", "الاتصالات"),
                listOf("bluetooth", "بلوتوث")
            )
        ),
        Recipe(
            pkgs = listOf("com.whatsapp"),
            triggers = listOf("رسالة", "ارسل", "أرسل", "message", "send"),
            steps = listOf(
                listOf("new chat", "محادثة جديدة", "start chat", "رسالة جديدة")
            )
        ),
        Recipe(
            pkgs = listOf("com.google.android.gm"),
            triggers = listOf("ايميل", "إيميل", "بريد", "email", "mail", "compose"),
            steps = listOf(
                listOf("compose", "إنشاء", "كتابة", "جديد")
            )
        ),
        Recipe(
            pkgs = listOf("com.android.chrome"),
            triggers = listOf("تبويب جديد", "new tab"),
            steps = listOf(
                listOf("more options", "menu", "القائمة", "المزيد"),
                listOf("new tab", "تبويب جديد")
            )
        ),
        Recipe(
            pkgs = listOf("com.google.android.youtube"),
            triggers = listOf("ابحث", "بحث", "search", "ابحث عن"),
            steps = listOf(
                listOf("search", "بحث")
            )
        )
    )

    private fun n(s: String): String = Arabic.normalize(s).lowercase()

    /**
     * يجد الخطوة الأولى التي لم تتحقق بعد على الشاشة الحالية.
     * لا يحفظ حالة: كل شاشة تحمل الخطوة التالية، فتتقدم الوصفة تلقائياً مع تغيّر الصفحات.
     */
    fun match(question: String, pkg: String, els: List<El>): Hit? {
        val q = n(question)
        if (q.isBlank()) return null
        val recipe = ALL.firstOrNull { r ->
            pkg in r.pkgs && r.triggers.any { t -> q.contains(n(t)) }
        } ?: return null
        val candidates = els.filter { it.interactive && it.enabled && !it.secret }
        for ((i, keywords) in recipe.steps.withIndex()) {
            val el = candidates.firstOrNull { e ->
                val label = n(e.label)
                keywords.any { k -> label.isNotEmpty() && label.contains(n(k)) }
            }
            if (el != null) return Hit(el, i + 1, recipe.steps.size)
        }
        return null
    }
}
