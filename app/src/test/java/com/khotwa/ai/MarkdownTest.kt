package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun headingsAndParagraphs() {
        val b = Markdown.parse("## العنوان\n\nنص عادي\nسطر ثان")
        assertEquals(2, b.size)
        assertEquals(2, (b[0] as MdBlock.Heading).level)
        assertEquals("نص عادي\nسطر ثان", (b[1] as MdBlock.Para).spans.joinToString("") { it.text })
    }

    @Test
    fun bulletAndNumberedLists() {
        val b = Markdown.parse("- one\n- two\n\n1. first\n2. second\n3. third")
        val bl = b[0] as MdBlock.Bullets
        assertEquals(2, bl.items.size)
        val nl = b[1] as MdBlock.Numbered
        assertEquals(1, nl.start)
        assertEquals(3, nl.items.size)
    }

    @Test
    fun arabicIndicDigitsStillMakeAList() {
        val b = Markdown.parse("١. أولاً\n٢. ثانياً")
        assertTrue(b[0] is MdBlock.Numbered)
        assertEquals(2, (b[0] as MdBlock.Numbered).items.size)
    }

    @Test
    fun fencedCodeBlock() {
        val b = Markdown.parse("قبل\n```kotlin\nval a = 1\n  val b = 2\n```\nبعد")
        val c = b[1] as MdBlock.Code
        assertEquals("kotlin", c.lang)
        assertEquals("val a = 1\n  val b = 2", c.code)
        assertEquals(3, b.size)
    }

    @Test
    fun unclosedFenceDuringStreamingIsCodeToTheEnd() {
        val b = Markdown.parse("```\nstill streaming")
        assertEquals("still streaming", (b.single() as MdBlock.Code).code)
    }

    @Test
    fun boldItalicAndInlineCode() {
        val s = Markdown.inline("هذا **مهم** و *مائل* و `code`")
        assertTrue(s.any { it.bold && it.text == "مهم" })
        assertTrue(s.any { it.italic && it.text == "مائل" })
        assertTrue(s.any { it.code && it.text == "code" })
    }

    @Test
    fun nestedBoldInsideLinkKeepsUrl() {
        val s = Markdown.inline("[**open**](https://example.org)")
        assertEquals("https://example.org", s.single().link)
        assertTrue(s.single().bold)
    }

    @Test
    fun onlyHttpLinksAreAccepted() {
        val s = Markdown.inline("[x](javascript:alert(1))")
        assertTrue(s.all { it.link == null })
        assertEquals("[x](javascript:alert(1))", s.joinToString("") { it.text })
    }

    @Test
    fun literalAsterisksAndSnakeCaseAreUntouched() {
        assertEquals("2 * 3 * 4", Markdown.inline("2 * 3 * 4").joinToString("") { it.text })
        val sn = Markdown.inline("use my_var_name here")
        assertEquals("use my_var_name here", sn.joinToString("") { it.text })
        assertFalse(sn.any { it.italic })
    }

    @Test
    fun unclosedMarkersStayLiteral() {
        assertEquals("**bold", Markdown.inline("**bold").joinToString("") { it.text })
    }

    @Test
    fun quoteAndRule() {
        val b = Markdown.parse("> مقتبس\n\n---")
        assertTrue(b[0] is MdBlock.Quote)
        assertTrue(b[1] is MdBlock.Rule)
    }

    @Test
    fun plainTextForSpeechHasNoSymbols() {
        val p = Markdown.plain("## عنوان\n**مهم** و `كود`\n- نقطة")
        assertFalse(p.contains("*") || p.contains("#") || p.contains("`"))
        assertTrue(p.contains("مهم") && p.contains("نقطة"))
    }
}
