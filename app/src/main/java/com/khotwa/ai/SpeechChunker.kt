package com.khotwa.ai

/**
 * يقسّم الشرح الطويل إلى جمل متتابعة تُنطق بسلاسة بلا قطع.
 * محرك النطق في أندرويد يرفض النص الأطول من حدّه (نحو 4000 حرف)، والقطعة القصيرة تبدأ أسرع وتُوقَف أنظف.
 * بلا اعتماد على أندرويد فتُختبر وحدته في CI.
 */
object SpeechChunker {

    private const val MAX = 220
    private val ENDERS = charArrayOf('.', '!', '?', '؟', '۔', '…', '\n', '।')

    fun split(text: String, max: Int = MAX): List<String> {
        val clean = text.replace(Regex("[ \\t]+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        // 1) جمل كاملة مع علامات الترقيم
        val sentences = mutableListOf<String>()
        val cur = StringBuilder()
        for (ch in clean) {
            cur.append(ch)
            if (ch in ENDERS) {
                val s = cur.toString().trim()
                if (s.isNotEmpty()) sentences += s
                cur.setLength(0)
            }
        }
        if (cur.isNotBlank()) sentences += cur.toString().trim()
        // 2) نقسّم الجملة الطويلة جداً، ثم ندمج القصيرة المتجاورة حتى الحد
        val pieces = sentences.flatMap { hardSplit(it, max) }
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (p in pieces) {
            if (buf.isNotEmpty() && buf.length + 1 + p.length > max) {
                out += buf.toString()
                buf.setLength(0)
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(p)
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out
    }

    /** جملة أطول من الحد: نقطعها عند آخر فاصلة أو مسافة قبل الحد. */
    private fun hardSplit(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val out = mutableListOf<String>()
        var rest = s
        while (rest.length > max) {
            var cut = rest.lastIndexOfAny(charArrayOf(',', '،', ';', '؛', ':'), max)
            if (cut < max / 3) cut = rest.lastIndexOf(' ', max)
            if (cut < max / 3) cut = max - 1
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    /** مدة تقريبية لنطق النص، تُستخدم مهلةً احتياطية إن لم يصل إشعار انتهاء النطق. */
    fun estimateMs(text: String): Long = (1500L + text.length * 70L).coerceIn(2500L, 240_000L)
}
