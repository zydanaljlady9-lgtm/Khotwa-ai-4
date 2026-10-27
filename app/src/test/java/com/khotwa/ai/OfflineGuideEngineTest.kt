package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGuideEngineTest {

    @Test
    fun parse_lines() {
        val e = OfflineGuideEngine.parse("4: [button] Next")
        assertNotNull(e)
        assertEquals(4, e!!.id)
        assertEquals("Next", e.label)
        assertFalse(e.editable)
        val f = OfflineGuideEngine.parse("2: [field-empty] Email")!!
        assertTrue(f.editable)
        val s = OfflineGuideEngine.parse("3: [secret-field] (unnamed)")!!
        assertTrue(s.secret)
        assertNull(OfflineGuideEngine.parse("garbage"))
    }

    @Test
    fun guide_pointsToNext() {
        val els = listOf("0: [text] Welcome", "1: [button] Cancel", "2: [button] Next")
        val r = OfflineGuideEngine.guide("what now", "com.x", els).getOrThrow()
        assertEquals(2, r.targetId)
    }

    @Test
    fun guide_pointsToBack() {
        val els = listOf("1: [button] Back", "2: [button] Continue")
        val r = OfflineGuideEngine.guide("go back", "com.x", els).getOrThrow()
        assertEquals(1, r.targetId)
    }

    @Test
    fun guide_stop() {
        val r = OfflineGuideEngine.guide("stop", "com.x", listOf("1: [button] Next")).getOrThrow()
        assertTrue(r.done)
    }

    @Test
    fun guide_fieldNeverSecret() {
        val els = listOf("1: [secret-field] Password", "2: [field-empty] Name")
        val r = OfflineGuideEngine.guide("what do I write in this field", "com.x", els).getOrThrow()
        assertEquals(2, r.targetId)
    }

    @Test
    fun chat_goalOffersGuide() {
        val r = OfflineGuideEngine.chat(listOf(Turn("user", "I want to apply for a scholarship")), "")
        assertEquals("start_guide", r.actions.single().type)
    }
}
