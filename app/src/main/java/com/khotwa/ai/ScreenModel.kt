package com.khotwa.ai

import android.graphics.Rect

/**
 * عنصر على الشاشة كما تراه خُطوة: رقمه ونوعه واسمه ومكانه. نصوص وأماكن فقط، لا محتوى حقول.
 * الأنواع بالإنجليزية كما في تعليمات الذكاء: button, text, option, option-checked, field-empty,
 * field-filled, secret-field, dropdown, image-button, image.
 */
data class El(
    val id: Int,
    val kind: String,
    val label: String,
    val place: String,
    val editable: Boolean,
    val enabled: Boolean = true,
    val focused: Boolean = false
) {
    /** خانة كلمة مرور أو رمز: لا نقترحها ولا نقرأ منها شيئاً. */
    val secret: Boolean get() = kind == "secret-field"

    /** عنصر يستطيع المستخدم الضغط عليه أو الكتابة فيه. */
    val interactive: Boolean
        get() = editable || kind.startsWith("button") || kind == "image-button" || kind.startsWith("option") || kind == "dropdown"
}

/** أين يقف المستخدم: التطبيق والموقع وعنوان الصفحة. */
data class PageInfo(
    val pkg: String,
    val appName: String,
    /** نطاق الموقع فقط (بلا مسار ولا استعلام) أو فارغ إن لم يكن متصفحاً. */
    val site: String,
    val title: String
) {
    /** مفتاح الصفحة: يتغير عند الانتقال بين تطبيق أو موقع أو صفحة، لا عند التمرير فقط. */
    val key: String get() = "$pkg|$site|$title"

    /** «Chrome · example.org · عنوان الصفحة». */
    fun where(): String {
        val parts = mutableListOf(appName)
        if (site.isNotEmpty()) parts += site
        if (title.isNotEmpty() && title != appName) parts += title
        return parts.joinToString(" · ")
    }

    /** وصف قصير للصفحة نفسها. */
    fun shortWhere(): String = when {
        title.isNotEmpty() -> title
        site.isNotEmpty() -> site
        else -> appName
    }
}

/**
 * ما تراه خُطوة على الشاشة الآن: أين أنت، وأسماء ونوع ومكان كل عنصر، وحالة التمرير، وما خارج الشاشة.
 * يُرسل للذكاء بالإنجليزية (لغة التعليمات)، ويُعرض للمستخدم بلغته.
 */
class ScreenModel(
    val page: PageInfo,
    val els: List<El>,
    /** سطر لكل عنصر بالشكل «رقم: [نوع] نص @مكان». */
    val lines: List<String>,
    /** مستطيل كل عنصر (مقصوص على حدود الشاشة) بنفس ترتيب els. */
    val rects: List<Rect>,
    val canScrollDown: Boolean,
    val canScrollUp: Boolean,
    /** وصف عناصر تفاعلية خارج الشاشة. */
    val offscreen: List<String>,
    val interactiveCount: Int,
    val takenAt: Long
) {
    val pkg: String get() = page.pkg
    val appName: String get() = page.appName
    val pageKey: String get() = page.key
    fun where(): String = page.where()
    fun shortWhere(): String = page.shortWhere()

    /** بصمة المحتوى: تتغير مع أي تغيّر في العناصر أو أماكنها. */
    val fingerprint: Int = lines.hashCode() * 31 + offscreen.hashCode()

    /** الشاشة تكشف القليل جداً من عناصرها للقراءة (ألعاب أو صفحات مرسومة بالكامل). */
    val weak: Boolean get() = interactiveCount < 2 && lines.size < 5

    /** الكتلة النصية التي تُقدَّم للذكاء الاصطناعي قبل كلام المستخدم. */
    fun contextBlock(): String {
        val sb = StringBuilder()
        sb.append("App: ").append(appName).append(" (").append(pkg).append(")\n")
        if (page.site.isNotEmpty()) sb.append("Site: ").append(page.site).append('\n')
        if (page.title.isNotEmpty()) sb.append("Page title: ").append(page.title).append('\n')
        sb.append("Scrolling: ").append(scrollText()).append('\n')
        sb.append("Screen elements (number: [kind] text @place):\n")
        sb.append(if (lines.isEmpty()) "(no readable elements)" else lines.joinToString("\n"))
        if (offscreen.isNotEmpty()) {
            sb.append("\nOff-screen elements (need scrolling; do not pick a number for them):\n")
            sb.append(offscreen.joinToString("\n"))
        }
        if (weak) sb.append("\n(Note: this screen exposes very little to read; rely on the image if attached.)")
        return sb.toString()
    }

    private fun scrollText(): String = when {
        canScrollDown && canScrollUp -> "possible up and down"
        canScrollDown -> "possible down (more content below)"
        canScrollUp -> "possible up only (at the end of the page)"
        else -> "no scrolling"
    }
}

/** وصف ما حدث بعد ضغطة المستخدم بالإنجليزية، لأنه يُرسل للذكاء الاصطناعي. */
object Transition {
    fun describe(
        prevKey: String?, prevShort: String, prevFingerprint: Int,
        curKey: String, curShort: String, curFingerprint: Int
    ): String {
        if (prevKey == null) return ""
        return when {
            prevKey != curKey -> "We moved from \"$prevShort\" to \"$curShort\"."
            prevFingerprint != curFingerprint -> "We are still on the same page but its content changed."
            else -> "Nothing changed on the screen (the tap may not have worked, or the page is still loading)."
        }
    }
}
