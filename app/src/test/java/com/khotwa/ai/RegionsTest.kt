package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionsTest {

    private val w = 1080
    private val h = 2400

    @Test
    fun placesOnThreeByThreeGrid() {
        assertEquals("top-left", Regions.placeOf(100, 200, w, h))
        assertEquals("top-center", Regions.placeOf(540, 100, w, h))
        assertEquals("top-right", Regions.placeOf(1000, 100, w, h))
        assertEquals("middle-left", Regions.placeOf(100, 1200, w, h))
        assertEquals("center", Regions.placeOf(540, 1200, w, h))
        assertEquals("middle-right", Regions.placeOf(1000, 1200, w, h))
        assertEquals("bottom-left", Regions.placeOf(100, 2300, w, h))
        assertEquals("bottom-center", Regions.placeOf(540, 2300, w, h))
        assertEquals("bottom-right", Regions.placeOf(1000, 2300, w, h))
        assertEquals("", Regions.placeOf(10, 10, 0, 0))
    }

    @Test
    fun offscreenSides() {
        assertEquals(1, Regions.offscreenSide(2500, 2600, 10, 500, w, h))
        assertEquals(-1, Regions.offscreenSide(-300, -10, 10, 500, w, h))
        assertEquals(0, Regions.offscreenSide(100, 200, 10, 500, w, h))
        assertEquals(0, Regions.offscreenSide(2500, 2600, 1200, 1500, w, h))
    }

    @Test
    fun siteKeepsOnlyTheHost() {
        assertEquals("example.com", Regions.siteOf("https://www.Example.com/path?q=1#x"))
        assertEquals("example.com", Regions.siteOf("example.com"))
        assertEquals("host.org", Regions.siteOf("http://user:pw@host.org:8080/x"))
        assertEquals("sub.domain.co.uk", Regions.siteOf("https://sub.domain.co.uk/#a"))
        assertEquals("موقع.السعودية", Regions.siteOf("https://موقع.السعودية/"))
    }

    @Test
    fun siteRejectsSearchTextAndHints() {
        assertEquals("", Regions.siteOf("Search or type web address"))
        assertEquals("", Regions.siteOf("منحة دراسية في ألمانيا"))
        assertEquals("", Regions.siteOf("localhost"))
        assertEquals("", Regions.siteOf("192.168.1.1"))
        assertEquals("", Regions.siteOf(""))
        assertEquals("", Regions.siteOf("a..b.com"))
        assertEquals("", Regions.siteOf("x".repeat(400) + ".com"))
    }
}
