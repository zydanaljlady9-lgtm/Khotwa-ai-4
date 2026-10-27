package com.khotwa.ai

/** قواعد الحماية: ما لا نقرؤه ولا نرسله للذكاء الاصطناعي ولا ننطقه. بلا اعتماد على أندرويد. */
object Safety {

    private val APP_KEYWORDS = listOf("bank", "wallet", "pay", "authenticator", "password", "vault", "otp")
    private val SITE_KEYWORDS = listOf("bank", "paypal", "wallet", "authenticator", "otp")

    /** تطبيقات البنوك والمحافظ والمصادقة وكلمات المرور: مستثناة تماماً. */
    fun isProtectedPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return APP_KEYWORDS.any { p.contains(it) }
    }

    /** مواقع البنوك والمحافظ في المتصفح: مستثناة أيضاً. */
    fun isProtectedSite(host: String): Boolean {
        val h = host.lowercase()
        return h.isNotEmpty() && SITE_KEYWORDS.any { h.contains(it) }
    }

    private val LONG_DIGITS = Regex("\\p{Nd}[\\p{Nd}\\s-]{10,}\\p{Nd}")

    /** يخفي الأرقام الطويلة (بطاقات وحسابات) وأرقاماً بطول رمز تحقق (5 إلى 8 خانات) من أي نص يُقرأ من الشاشة. */
    fun redact(s: String): String {
        if (s.isEmpty()) return s
        val t = LONG_DIGITS.replace(s, "[number]")
        val trimmed = t.trim()
        if (trimmed.length in 5..8 && trimmed.all { it.isDigit() }) return "[code]"
        return t
    }
}
