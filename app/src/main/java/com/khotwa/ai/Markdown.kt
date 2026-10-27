package com.khotwa.ai

/** مقطع نص داخل فقرة بتنسيقه. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null
)

/** كتل المحتوى كما يعرضها المحادثة. */
sealed class MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock()
    data class Para(val spans: List<MdSpan>) : MdBlock()
    data class Bullets(val items: List<List<MdSpan>>) : MdBlock()
    data class Numbered(val start: Int, val items: List<List<MdSpan>>) : MdBlock()
    data class Quote(val spans: List<MdSpan>) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    object Rule : MdBlock()
}

/**
 * محلل Markdown خفيف ومتسامح مع الردود غير المكتملة أثناء البث (سياج كود بلا إغلاق، رموز بلا نهاية).
 * بلا اعتماد على أندرويد فتُختبر وحدته في CI. الروابط http و https فقط.
 */
object Markdown {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val BULLET = Regex("^[-*+•]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\p{Nd}{1,3})[.)]\\s+(.*)$")

    fun parse(src: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val lines = src.replace("\r\n", "\n").split('\n')
        val para = StringBuilder()

        fun flushPara() {
            if (para.isNotEmpty()) {
                out += MdBlock.Para(inline(para.toString()))
                para.setLength(0)
            }
        }

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val t = raw.trim()
            if (t.startsWith("```")) {
                flushPara()
                val lang = t.removePrefix("```").trim().take(20)
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i].trimEnd())
                    i++
                }
                out += MdBlock.Code(lang, code.toString())
                i++ // سطر الإغلاق إن وُجد
                continue
            }
            if (t.isEmpty()) {
                flushPara()
                i++
                continue
            }
            val h = HEADING.matchEntire(t)
            if (h != null) {
                flushPara()
                out += MdBlock.Heading(h.groupValues[1].length, inline(h.groupValues[2]))
                i++
                continue
            }
            if (RULE.matches(t)) {
                flushPara()
                out += MdBlock.Rule
                i++
                continue
            }
            if (BULLET.matches(t)) {
                flushPara()
                val items = mutableListOf<List<MdSpan>>()
                while (i < lines.size) {
                    val m = BULLET.matchEntire(lines[i].trim()) ?: break
                    items += inline(m.groupValues[1])
                    i++
                }
                out += MdBlock.Bullets(items)
                continue
            }
            if (NUMBERED.matches(t)) {
                flushPara()
                val first = NUMBERED.matchEntire(t)!!
                val start = first.groupValues[1].toIntOrNull() ?: 1
                val items = mutableListOf<List<MdSpan>>()
                while (i < lines.size) {
                    val m = NUMBERED.matchEntire(lines[i].trim()) ?: break
                    items += inline(m.groupValues[2])
                    i++
                }
                out += MdBlock.Numbered(start, items)
                continue
            }
            if (t.startsWith(">")) {
                flushPara()
                val sb = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                out += MdBlock.Quote(inline(sb.toString()))
                continue
            }
            if (para.isNotEmpty()) para.append('\n')
            para.append(t)
            i++
        }
        flushPara()
        return out
    }

    // ───────────── التنسيق داخل الفقرة ─────────────

    fun inline(s: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        parseInto(s, bold = false, italic = false, link = null, out = out)
        return merge(out)
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit()

    private fun parseInto(s: String, bold: Boolean, italic: Boolean, link: String?, out: MutableList<MdSpan>) {
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                out += MdSpan(sb.toString(), bold, italic, false, link)
                sb.setLength(0)
            }
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // كود مضمّن
            if (c == '`') {
                val j = s.indexOf('`', i + 1)
                if (j > i + 1) {
                    flush()
                    out += MdSpan(s.substring(i + 1, j), bold, italic, true, link)
                    i = j + 1
                    continue
                }
            }
            // خط عريض
            if (c == '*' && s.startsWith("**", i)) {
                val j = s.indexOf("**", i + 2)
                if (j > i + 2 && !s[i + 2].isWhitespace()) {
                    flush()
                    parseInto(s.substring(i + 2, j), true, italic, link, out)
                    i = j + 2
                    continue
                }
            }
            // خط مائل بنجمة
            if (c == '*' && !s.startsWith("**", i) && i + 1 < s.length && !s[i + 1].isWhitespace()) {
                var j = i + 1
                var found = -1
                while (j < s.length) {
                    if (s[j] == '*' && !s[j - 1].isWhitespace() && !s.startsWith("**", j)) {
                        found = j
                        break
                    }
                    j++
                }
                if (found > i + 1) {
                    flush()
                    parseInto(s.substring(i + 1, found), bold, true, link, out)
                    i = found + 1
                    continue
                }
            }
            // خط مائل بشرطة سفلية عند حدود الكلمة فقط (لا يمس snake_case)
            if (c == '_' && (i == 0 || !isWordChar(s[i - 1])) && i + 1 < s.length && !s[i + 1].isWhitespace() && s[i + 1] != '_') {
                var j = i + 1
                var found = -1
                while (j < s.length) {
                    if (s[j] == '_' && !s[j - 1].isWhitespace() && (j + 1 >= s.length || !isWordChar(s[j + 1]))) {
                        found = j
                        break
                    }
                    j++
                }
                if (found > i + 1) {
                    flush()
                    parseInto(s.substring(i + 1, found), bold, true, link, out)
                    i = found + 1
                    continue
                }
            }
            // رابط [نص](https://…)
            if (c == '[') {
                val close = s.indexOf("](", i + 1)
                if (close > i) {
                    val end = s.indexOf(')', close + 2)
                    if (end > close + 2) {
                        val url = s.substring(close + 2, end).trim()
                        if (url.startsWith("https://") || url.startsWith("http://")) {
                            flush()
                            parseInto(s.substring(i + 1, close), bold, italic, url, out)
                            i = end + 1
                            continue
                        }
                    }
                }
            }
            sb.append(c)
            i++
        }
        flush()
    }

    /** يدمج المقاطع المتجاورة المتماثلة التنسيق. */
    private fun merge(spans: List<MdSpan>): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        for (s in spans) {
            val last = out.lastOrNull()
            if (last != null && last.bold == s.bold && last.italic == s.italic && last.code == s.code && last.link == s.link) {
                out[out.size - 1] = last.copy(text = last.text + s.text)
            } else {
                out += s
            }
        }
        return out
    }

    /** نص بلا رموز تنسيق: للقراءة بصوت عالٍ والنسخ المبسط. */
    fun plain(src: String): String = parse(src).joinToString("\n") { b ->
        when (b) {
            is MdBlock.Heading -> b.spans.joinToString("") { it.text }
            is MdBlock.Para -> b.spans.joinToString("") { it.text }
            is MdBlock.Quote -> b.spans.joinToString("") { it.text }
            is MdBlock.Bullets -> b.items.joinToString("\n") { it.joinToString("") { s -> s.text } }
            is MdBlock.Numbered -> b.items.mapIndexed { i, it -> "${b.start + i}. " + it.joinToString("") { s -> s.text } }.joinToString("\n")
            is MdBlock.Code -> b.code
            MdBlock.Rule -> ""
        }
    }.trim()
}
