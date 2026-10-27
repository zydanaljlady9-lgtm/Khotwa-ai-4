package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTest {

    @Test
    fun parseChat_plainText() {
        val r = AiClient.parseChat("Hello **there**", "X")
        assertEquals("Hello there", r.text)
        assertTrue(r.actions.isEmpty())
        assertEquals("X", r.engine)
    }

    @Test
    fun parseChat_withActions() {
        val raw = "Open the site.\n###ACTIONS [{\"type\":\"open_url\",\"url\":\"https://a.org\",\"label\":\"Open\"}," +
            "{\"type\":\"start_guide\",\"goal\":\"apply\",\"label\":\"Go\"}]"
        val r = AiClient.parseChat(raw, "X")
        assertEquals("Open the site.", r.text)
        assertEquals(2, r.actions.size)
        assertEquals("open_url", r.actions[0].type)
        assertEquals("https://a.org", r.actions[0].url)
        assertEquals("start_guide", r.actions[1].type)
        assertEquals("apply", r.actions[1].goal)
    }

    @Test
    fun parseChat_malformedActions() {
        val raw = "Sure.\n###ACTIONS [{\"open_url\":\"https://b.org\"},{\"start_guide\":{\"goal\":\"fill form\"}},{\"type\":\"hack\"}]"
        val r = AiClient.parseChat(raw, "X")
        assertEquals(2, r.actions.size)
        assertEquals("open_url", r.actions[0].type)
        assertEquals("https://b.org", r.actions[0].url)
        assertEquals("start_guide", r.actions[1].type)
        assertEquals("fill form", r.actions[1].goal)
    }

    @Test
    fun parseChat_brokenJsonAfterMarkIsIgnored() {
        val r = AiClient.parseChat("Text\n###ACTIONS [{oops", "X")
        assertEquals("Text", r.text)
        assertTrue(r.actions.isEmpty())
    }

    @Test
    fun parseChat_jsonFallback() {
        val r = AiClient.parseChat("{\"reply\":\"Hi\",\"actions\":[{\"type\":\"ask_mic\"}]}", "X")
        assertEquals("Hi", r.text)
        assertEquals("ask_mic", r.actions.single().type)
    }

    @Test
    fun parseGuide_codeFenceAndMissingConfidence() {
        val r = AiClient.parseGuide("```json\n{\"say\":\"Tap Next\",\"target_id\":3,\"done\":false}\n```", "X")
        assertEquals("Tap Next", r.say)
        assertEquals(3, r.targetId)
        assertEquals(0.8, r.confidence, 1e-9)
        assertFalse(r.done)
    }

    @Test
    fun parseGuide_nullTarget() {
        val r = AiClient.parseGuide("{\"say\":\"Done\",\"target_id\":null,\"confidence\":0.95,\"done\":true}", "X")
        assertNull(r.targetId)
        assertEquals(0.95, r.confidence, 1e-9)
        assertTrue(r.done)
    }

    @Test
    fun parseGuide_noJson() {
        val r = AiClient.parseGuide("Just tap the blue button", "X")
        assertEquals("Just tap the blue button", r.say)
        assertNull(r.targetId)
    }

    @Test
    fun clean_stripsThinking() {
        assertEquals("answer", AiClient.clean("<think>hidden</think>answer"))
        assertEquals("partial", AiClient.clean("partial<think>still thinking"))
    }

    @Test
    fun visibleChat_hidesMarkerWhileStreaming() {
        assertEquals("Hello", AiClient.visibleChat("Hello\n###ACT"))
        assertEquals("Hello", AiClient.visibleChat("Hello\n###ACTIONS [{\"type\""))
        assertEquals("Title", AiClient.visibleChat("## Title"))
    }

    @Test
    fun retryAfter_sources() {
        assertEquals(12L, AiClient.retryAfter("12", ""))
        assertEquals(40L, AiClient.retryAfter(null, "{\"error\":{\"retry_after\":40}}"))
        assertEquals(7L, AiClient.retryAfter(null, "Free limit: try again in 7s"))
        assertEquals(30L, AiClient.retryAfter(null, "nothing"))
    }
}
