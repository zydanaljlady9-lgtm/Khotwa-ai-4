package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {

    @Test
    fun protectedPackages() {
        assertTrue(Safety.isProtectedPackage("com.mybank.app"))
        assertTrue(Safety.isProtectedPackage("com.samsung.android.spay"))
        assertTrue(Safety.isProtectedPackage("com.google.android.apps.authenticator2"))
        assertTrue(Safety.isProtectedPackage("com.x.PASSWORD.manager"))
        assertFalse(Safety.isProtectedPackage("com.android.chrome"))
        assertFalse(Safety.isProtectedPackage("com.whatsapp"))
    }

    @Test
    fun protectedSites() {
        assertTrue(Safety.isProtectedSite("online.mybank.com"))
        assertTrue(Safety.isProtectedSite("www.paypal.com"))
        assertFalse(Safety.isProtectedSite("gov.sa"))
        assertFalse(Safety.isProtectedSite(""))
    }

    @Test
    fun redactsLongNumbersAndCodes() {
        assertEquals("رقم بطاقتك [number] انتهت", Safety.redact("رقم بطاقتك 4111 1111 1111 1111 انتهت"))
        assertEquals("[number]", Safety.redact("4111-1111-1111-1111"))
        assertEquals("[code]", Safety.redact("482913"))
        assertEquals("[code]", Safety.redact("٤٨٢٩١٣"))
        assertEquals("2024", Safety.redact("2024"))
        assertEquals("12:30", Safety.redact("12:30"))
        assertEquals("سجل الآن", Safety.redact("سجل الآن"))
        assertEquals("", Safety.redact(""))
    }
}
