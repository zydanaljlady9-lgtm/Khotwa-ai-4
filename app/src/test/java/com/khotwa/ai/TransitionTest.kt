package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionTest {

    @Test
    fun noPreviousScreenGivesNothing() {
        assertEquals("", Transition.describe(null, "", 0, "k", "s", 1))
    }

    @Test
    fun differentPageIsAnArrival() {
        val t = Transition.describe("a|x|p1", "Home", 1, "a|x|p2", "Signup", 2)
        assertTrue(t, t.contains("Home") && t.contains("Signup"))
    }

    @Test
    fun samePageChangedContent() {
        assertTrue(Transition.describe("k", "p", 1, "k", "p", 2).contains("content changed"))
    }

    @Test
    fun nothingChanged() {
        assertTrue(Transition.describe("k", "p", 5, "k", "p", 5).contains("Nothing changed"))
    }

    @Test
    fun pageInfoWhere() {
        val p = PageInfo("com.android.chrome", "Chrome", "example.org", "الصفحة الرئيسية")
        assertEquals("Chrome · example.org · الصفحة الرئيسية", p.where())
        assertEquals("الصفحة الرئيسية", p.shortWhere())
        assertEquals("Chrome", PageInfo("p", "Chrome", "", "").where())
        assertEquals("Chrome", PageInfo("p", "Chrome", "", "Chrome").where())
        assertEquals("example.org", PageInfo("p", "Chrome", "example.org", "").shortWhere())
    }
}
