package com.khotwa.ai

/**
 * أماكن العناصر على الشاشة: شبكة 3×3 بأسماء ثابتة بالإنجليزية (تُمرَّر للذكاء الاصطناعي)،
 * ويحوّلها الواجهة إلى لغة المستخدم عبر placeKey. واستخراج نطاق الموقع من شريط العنوان.
 */
object Regions {

    /** مفتاح مكان: top-left … bottom-right، أو center. */
    fun placeOf(cx: Int, cy: Int, w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return ""
        val col = when {
            cx < w / 3 -> 0
            cx < 2 * w / 3 -> 1
            else -> 2
        }
        val row = when {
            cy < h / 3 -> 0
            cy < 2 * h / 3 -> 1
            else -> 2
        }
        if (row == 1 && col == 1) return "center"
        return arrayOf("top", "middle", "bottom")[row] + "-" + arrayOf("left", "center", "right")[col]
    }

    /** اسم المكان للإنسان بلغته: يُختار المورد المناسب من strings.xml. */
    fun placeName(place: String): String = when (place) {
        "top-left" -> L.s(R.string.place_top_left)
        "top-center" -> L.s(R.string.place_top_center)
        "top-right" -> L.s(R.string.place_top_right)
        "middle-left" -> L.s(R.string.place_middle_left)
        "middle-right" -> L.s(R.string.place_middle_right)
        "bottom-left" -> L.s(R.string.place_bottom_left)
        "bottom-center" -> L.s(R.string.place_bottom_center)
        "bottom-right" -> L.s(R.string.place_bottom_right)
        "center" -> L.s(R.string.place_center)
        else -> ""
    }

    /** -1 إن كان العنصر فوق الشاشة، و+1 إن كان تحتها، و0 إن كان داخلها رأسياً. */
    fun offscreenSide(top: Int, bottom: Int, left: Int, right: Int, w: Int, h: Int): Int {
        if (right <= 0 || left >= w) return 0
        return when {
            top >= h -> 1
            bottom <= 0 -> -1
            else -> 0
        }
    }

    /**
     * نطاق الموقع فقط من نص شريط العنوان: بلا بروتوكول ولا مسار ولا استعلام ولا منفذ.
     * يعيد فارغاً إن لم يكن رابطاً (كلمات بحث يكتبها المستخدم أو تلميحات)، كي لا نسرّب ما يكتبه أبداً.
     */
    fun siteOf(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty() || s.length > 300) return ""
        if (s.any { it.isWhitespace() }) return ""
        val schemeEnd = s.indexOf("://")
        if (schemeEnd in 1..10) s = s.substring(schemeEnd + 3)
        val cut = s.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (cut >= 0) s = s.substring(0, cut)
        val at = s.lastIndexOf('@')
        if (at >= 0) s = s.substring(at + 1)
        val colon = s.indexOf(':')
        if (colon >= 0) s = s.substring(0, colon)
        s = s.lowercase().removePrefix("www.")
        if (s.isEmpty() || !s.contains('.') || s.startsWith(".") || s.endsWith(".") || s.contains("..")) return ""
        if (!s.all { it.isLetterOrDigit() || it == '-' || it == '.' }) return ""
        val tld = s.substringAfterLast('.')
        if (tld.length < 2 || !(tld.all { it.isLetter() } || tld.startsWith("xn--"))) return ""
        return s
    }
}
