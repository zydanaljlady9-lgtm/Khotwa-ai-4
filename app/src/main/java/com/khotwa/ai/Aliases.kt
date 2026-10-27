package com.khotwa.ai

/**
 * أسماء يعلّمها المستخدم للأزرار: «هذا اسمه الدفع» بعد أن يشير التطبيق إلى زر.
 * تُحفظ محلياً على الجهاز ولا تُرسل إلا كجملة توضيحية داخل السؤال.
 */
object Aliases {

    private val map = LinkedHashMap<String, String>()
    private const val MAX = 60

    private val PREFIXES = listOf(
        "هذا الزر اسمه", "هذا اسمه", "هذه اسمها", "اسمه", "اسمها", "سمه", "سميه", "سميها",
        "سمي هذا الزر", "سمي هذا", "سمّ هذا الزر", "سمّ هذا",
        "this is called", "this is", "call it", "call this", "name it"
    )

    private fun n(s: String): String = Arabic.normalize(s).lowercase().trim()

    /** تحميل من النص المحفوظ: سطر لكل اسم بالشكل «الاسم<TAB>الزر». */
    fun load(raw: String) {
        map.clear()
        raw.lines().forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) map[parts[0]] = parts[1]
        }
    }

    fun serialize(): String = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun add(name: String, label: String): Boolean {
        val key = n(name)
        if (key.length !in 2..30 || label.isBlank()) return false
        map.remove(key)
        map[key] = label.trim()
        while (map.size > MAX) map.remove(map.keys.first())
        return true
    }

    /** هل يحتوي الكلام على تعليم: «هذا اسمه كذا»؟ يعيد الاسم، أو null. */
    fun teachName(question: String): String? {
        val q = n(question)
        for (p in PREFIXES) {
            val pn = n(p)
            if (q.startsWith(pn)) {
                val rest = q.removePrefix(pn).trim().trim('"', '«', '»', '.', '،', ' ')
                if (rest.length in 2..30) return rest
            }
        }
        return null
    }

    /** يضيف إلى السؤال توضيحاً إن ذكر المستخدم اسماً علّمه: «الدفع» ← «الدفع (الزر المسمّى: Pay)». */
    fun expand(question: String): String {
        if (map.isEmpty()) return question
        val q = n(question)
        val hit = map.entries.firstOrNull { q.contains(it.key) } ?: return question
        return "$question (the element the user calls \"${hit.key}\" is labelled \"${hit.value}\")"
    }

    internal fun labelFor(name: String): String? = map[n(name)]
}
