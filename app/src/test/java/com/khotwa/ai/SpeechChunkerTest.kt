package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunkerTest {

    @Test
    fun emptyGivesNothing() {
        assertTrue(SpeechChunker.split("   ").isEmpty())
    }

    @Test
    fun shortTextStaysInOneChunk() {
        assertEquals(listOf("اضغط على الزر. ثم انتظر."), SpeechChunker.split("اضغط على الزر. ثم انتظر."))
    }

    @Test
    fun longTextIsSplitIntoBoundedChunksWithoutLosingWords() {
        val sentence = "هذه جملة شرح قصيرة تتكرر كثيراً. "
        val text = sentence.repeat(60)
        val chunks = SpeechChunker.split(text)
        assertTrue(chunks.size > 3)
        assertTrue(chunks.all { it.length <= 220 })
        assertEquals(text.trim().split(" ").filter { it.isNotEmpty() }, chunks.joinToString(" ").split(" ").filter { it.isNotEmpty() })
    }

    @Test
    fun oneHugeSentenceIsStillSplit() {
        val text = (1..400).joinToString(" ") { "كلمة$it" }
        val chunks = SpeechChunker.split(text)
        assertTrue(chunks.size > 5)
        assertTrue(chunks.all { it.length <= 220 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun nothingExceedsTheEngineInputLimit() {
        val chunks = SpeechChunker.split("a".repeat(9000))
        assertTrue(chunks.all { it.length <= 220 })
    }

    @Test
    fun fallbackDurationGrowsWithTextAndIsBounded() {
        assertTrue(SpeechChunker.estimateMs("x".repeat(1000)) > SpeechChunker.estimateMs("x".repeat(100)))
        assertTrue(SpeechChunker.estimateMs("x".repeat(100_000)) <= 240_000L)
        assertTrue(SpeechChunker.estimateMs("") >= 2500L)
    }

    @Test
    fun chatTokenBudgetGrowsWithStyle() {
        assertTrue(AiClient.chatMaxTokens("detailed") > AiClient.chatMaxTokens("balanced"))
        assertTrue(AiClient.chatMaxTokens("balanced") > AiClient.chatMaxTokens("short"))
        assertEquals(AiClient.chatMaxTokens("balanced"), AiClient.chatMaxTokens("unknown"))
    }
}
