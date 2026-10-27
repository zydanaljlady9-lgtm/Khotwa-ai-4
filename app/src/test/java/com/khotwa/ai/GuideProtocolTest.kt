package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideProtocolTest {

    @Test
    fun parsesFullReply() {
        val r = AiClient.parseGuide(
            """{"say":"Tap Next at the bottom right","target_id":7,"confidence":0.92,"done":false,"scroll":null,"step":2,"total":5}""",
            "E"
        )
        assertEquals("Tap Next at the bottom right", r.say)
        assertEquals(7, r.targetId)
        assertEquals(0.92, r.confidence, 0.0001)
        assertEquals(false, r.done)
        assertNull(r.scroll)
        assertEquals(2, r.step)
        assertEquals(5, r.total)
    }

    @Test
    fun scrollAcceptsArabicWords() {
        assertEquals("down", AiClient.parseGuide("""{"say":"a","scroll":"للأسفل"}""", "E").scroll)
        assertEquals("up", AiClient.parseGuide("""{"say":"a","scroll":"فوق"}""", "E").scroll)
        assertNull(AiClient.parseGuide("""{"say":"a","scroll":"sideways"}""", "E").scroll)
    }

    @Test
    fun badNumbersAreIgnored() {
        val r = AiClient.parseGuide("""{"say":"a","target_id":-1,"step":0,"total":500}""", "E")
        assertNull(r.targetId)
        assertNull(r.step)
        assertNull(r.total)
    }

    @Test
    fun rescuesBrokenJson() {
        val r2 = AiClient.parseGuide("""{"say":"Tap the button","target_id":4 "confidence":0.9""", "E")
        assertEquals("Tap the button", r2.say)
        assertEquals(4, r2.targetId)
    }

    @Test
    fun plainTextIsKept() {
        assertEquals("Tap the blue button", GuideProtocol.rescue("Tap the blue button", "E").say)
    }

    @Test
    fun unreadableJunkGivesBlankSoCallerCanLocalise() {
        val junk = GuideProtocol.rescue("{\"target_id\": 3", "E")
        assertTrue(junk.say.isBlank())
        assertNull(junk.targetId)
    }

    @Test
    fun historyKeepsStepAndTarget() {
        val t = AiClient.historyText(AiReply("Hello", 4, 0.9, false, "E", null, 2, 6))
        val back = AiClient.parseGuide(t, "E")
        assertEquals("Hello", back.say)
        assertEquals(4, back.targetId)
        assertEquals(2, back.step)
        assertEquals(6, back.total)
    }
}
