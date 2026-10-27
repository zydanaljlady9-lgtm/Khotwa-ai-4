package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasesTest {

    @Test
    fun teachingPhrasesYieldTheName() {
        assertEquals("الدفع", Aliases.teachName("هذا اسمه الدفع"))
        assertEquals("الزر الاخضر", Aliases.teachName("هذا الزر اسمه الزر الأخضر"))
        assertEquals("pay", Aliases.teachName("call it pay"))
    }

    @Test
    fun ordinaryQuestionsAreNotTeaching() {
        assertNull(Aliases.teachName("اين الزر"))
        assertNull(Aliases.teachName("اسمه"))
    }

    @Test
    fun saveAndExpand() {
        assertTrue(Aliases.add("الدفع", "Pay now"))
        val q = Aliases.expand("اضغط الدفع")
        assertTrue(q, q.contains("Pay now"))
        assertEquals("أين أنا", Aliases.expand("أين أنا"))
    }

    @Test
    fun roundTripThroughStorage() {
        Aliases.add("سجل", "Sign in")
        val saved = Aliases.serialize()
        Aliases.load("")
        Aliases.load(saved)
        assertEquals("Sign in", Aliases.labelFor("سجل"))
    }

    @Test
    fun rejectsTooShortOrBlankNames() {
        assertFalse(Aliases.add("x", "Pay"))
        assertFalse(Aliases.add("الدفع", "  "))
    }
}
