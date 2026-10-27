package com.khotwa.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiKeyTest {

    private val key = "AQ.TestKeyNotReal_123"

    @Test
    fun newGoogleKeysUseTheNativeEndpointWithOneHeader() {
        val eps = AiClient.endpointsForKey(key, "key")
        assertEquals(2, eps.size)
        eps.forEach { ep ->
            assertEquals("gemini", ep.kind)
            assertEquals(key, ep.headers["x-goog-api-key"])
            // ترويسة واحدة فقط: إرسال المفتاح مرتين يسبب «Multiple authentication credentials».
            assertEquals(1, ep.headers.size)
            assertFalse(ep.headers.containsKey("Authorization"))
            assertTrue(ep.url, ep.url.startsWith("https://generativelanguage.googleapis.com/v1beta/models/"))
            assertTrue(ep.url.endsWith(":generateContent"))
            assertFalse(ep.url.contains("key="))
        }
    }

    @Test
    fun legacyAndOtherKeysStillRoute() {
        assertEquals("openai", AiClient.endpointsForKey("AIzaSyTest", "key").first().kind)
        assertEquals("anthropic", AiClient.endpointsForKey("sk-ant-test", "key").first().kind)
        assertTrue(AiClient.endpointsForKey("whatever", "key").isEmpty())
        assertTrue(AiClient.endpointsForKey("   ", "key").isEmpty())
    }

    @Test
    fun claudeUsesTheCurrentHaikuModel() {
        assertEquals("claude-haiku-5-5", AiClient.endpointsForKey("sk-ant-test", "key").first().model)
    }

    @Test
    fun requestBodyHasNativeShape() {
        val ep = AiClient.endpointsForKey(key, "key").first()
        val body = JSONObject(
            AiClient.requestBody(
                ep, "SYS",
                listOf(Turn("user", "hi"), Turn("assistant", "hello"), Turn("user", "next")),
                false, 321
            )
        )
        assertEquals("SYS", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val c = body.getJSONArray("contents")
        assertEquals(3, c.length())
        assertEquals("user", c.getJSONObject(0).getString("role"))
        assertEquals("model", c.getJSONObject(1).getString("role"))
        assertEquals(321, body.getJSONObject("generationConfig").getInt("maxOutputTokens"))
        assertEquals(0, body.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget"))
    }

    @Test
    fun thinkingIsOnlyDisabledOnTheFirstEndpoint() {
        val second = AiClient.endpointsForKey(key, "key")[1]
        val body = JSONObject(AiClient.requestBody(second, "S", listOf(Turn("user", "x")), false, 50))
        assertFalse(body.getJSONObject("generationConfig").has("thinkingConfig"))
    }

    @Test
    fun consecutiveSameRoleTurnsAreMergedAndHistoryStartsWithUser() {
        val ep = AiClient.endpointsForKey(key, "key").first()
        val body = JSONObject(
            AiClient.requestBody(ep, "S", listOf(Turn("assistant", "old"), Turn("user", "a"), Turn("user", "b")), false, 50)
        )
        val c = body.getJSONArray("contents")
        assertEquals(1, c.length())
        assertEquals("a\nb", c.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test
    fun imagesBecomeInlineData() {
        val ep = AiClient.endpointsForKey(key, "key").first()
        val body = JSONObject(AiClient.requestBody(ep, "S", listOf(Turn("user", "see", "QUJD")), false, 50))
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals("image/jpeg", parts.getJSONObject(1).getJSONObject("inlineData").getString("mimeType"))
        assertEquals("QUJD", parts.getJSONObject(1).getJSONObject("inlineData").getString("data"))
    }

    @Test
    fun responseTextJoinsPartsAndSkipsThoughts() {
        val j = JSONObject(
            """{"candidates":[{"content":{"parts":[{"text":"hidden","thought":true},{"text":"Hello "},{"text":"world"}]}}]}"""
        )
        assertEquals("Hello world", AiClient.geminiText(j))
    }

    @Test
    fun blockedOrEmptyResponseGivesBlank() {
        assertEquals("", AiClient.geminiText(JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}""")))
    }

    @Test
    fun apiErrorsBecomeFailures() {
        var failed = false
        try {
            AiClient.geminiText(JSONObject("""{"error":{"code":401,"message":"API key not valid"}}"""))
        } catch (e: IllegalStateException) {
            failed = e.message == "API key not valid"
        }
        assertTrue(failed)
        assertNull(null)
    }
}
