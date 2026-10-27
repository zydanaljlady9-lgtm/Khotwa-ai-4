package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTest {

    @Test
    fun normalizeStripsMarksAndUnifiesLetters() {
        assertEquals("الاسلام", Arabic.normalize("الإسلام"))
        assertEquals("مدرسه", Arabic.normalize("مَدْرَسَة"))
        assertEquals("علي", Arabic.normalize("على"))
        assertEquals("طلب 123", Arabic.normalize("طلب ١٢٣"))
        assertEquals("sign in", Arabic.normalize("  Sign-In!  "))
    }

    @Test
    fun tokensDropArticlesAndFiller() {
        assertEquals(listOf("تسجيل", "دخول"), Arabic.tokens("التسجيل والدخول"))
        val t = Arabic.tokens("اضغط على زر التسجيل")
        assertEquals(listOf("تسجيل"), t)
        assertEquals(listOf("بحث"), Arabic.tokens("للبحث"))
    }

    @Test
    fun similarityFullPhraseInQuery() {
        assertTrue(Arabic.similarity("اضغط على تسجيل الدخول", "تسجيل الدخول") >= 0.99)
        assertTrue(Arabic.similarity("اريد فتح الاعدادات", "الإعدادات") >= 0.99)
    }

    @Test
    fun similarityByFunctionAcrossLanguages() {
        assertTrue(Arabic.similarity("ابحث عن منحة", "بحث") >= 0.8)
        assertTrue(Arabic.similarity("التالي", "Next") >= 0.8)
        assertTrue(Arabic.similarity("ارجع", "Back") >= 0.8)
        assertTrue(Arabic.similarity("سجل", "Sign up") >= 0.8)
        assertTrue(Arabic.similarity("اريد انشاء حساب", "Create account") >= 0.8)
        assertTrue(Arabic.similarity("ارسل الطلب", "Submit") >= 0.8)
    }

    @Test
    fun similarityRejectsUnrelated() {
        assertTrue(Arabic.similarity("اضغط ابحث", "إلغاء") < 0.4)
        assertTrue(Arabic.similarity("تسجيل الدخول", "سياسة الخصوصية") < 0.4)
        assertEquals(0.0, Arabic.similarity("", "زر"), 0.0)
        assertEquals(0.0, Arabic.similarity("زر", ""), 0.0)
    }

    @Test
    fun longLabelNeedsMoreThanOneWord() {
        assertTrue(Arabic.similarity("اريد الشروط", "أوافق على الشروط والأحكام وسياسة الخصوصية") <= 0.5)
    }

    @Test
    fun intentsAreDetected() {
        assertTrue(Arabic.Intent.LOGIN in Arabic.intentsOf("تسجيل الدخول"))
        assertTrue(Arabic.Intent.LOGIN in Arabic.intentsOf("Sign in"))
        assertTrue(Arabic.Intent.REGISTER in Arabic.intentsOf("إنشاء حساب"))
        assertTrue(Arabic.Intent.REGISTER in Arabic.intentsOf("Sign up"))
        assertTrue(Arabic.Intent.NEXT in Arabic.intentsOf("التالي"))
        assertTrue(Arabic.Intent.SUBMIT in Arabic.intentsOf("تقديم الطلب"))
        val bare = Arabic.intentsOf("تسجيل")
        assertTrue(Arabic.Intent.LOGIN in bare && Arabic.Intent.REGISTER in bare)
        assertFalse(Arabic.Intent.REGISTER in Arabic.intentsOf("تسجيل الدخول"))
        assertTrue(Arabic.intentsOf("").isEmpty())
    }

    @Test
    fun whereAmI() {
        assertTrue(Arabic.isWhereAmI("أين أنا الآن؟"))
        assertTrue(Arabic.isWhereAmI("وين انا"))
        assertTrue(Arabic.isWhereAmI("في اي صفحة انا"))
        assertTrue(Arabic.isWhereAmI("Where am I?"))
        assertFalse(Arabic.isWhereAmI("اضغط على التالي"))
    }

    @Test
    fun fieldKinds() {
        assertEquals(Arabic.FieldKind.EMAIL, Arabic.fieldKindOf("البريد الإلكتروني"))
        assertEquals(Arabic.FieldKind.EMAIL, Arabic.fieldKindOf("Email address"))
        assertEquals(Arabic.FieldKind.PHONE, Arabic.fieldKindOf("Phone number"))
        assertEquals(Arabic.FieldKind.PHONE, Arabic.fieldKindOf("رقم الجوال"))
        assertEquals(Arabic.FieldKind.NAME, Arabic.fieldKindOf("الاسم الكامل"))
        assertEquals(Arabic.FieldKind.PASSWORD, Arabic.fieldKindOf("حقل كلمة مرور"))
        assertEquals(Arabic.FieldKind.PASSWORD, Arabic.fieldKindOf("Password"))
        assertEquals(Arabic.FieldKind.ADDRESS, Arabic.fieldKindOf("الرمز البريدي"))
        assertEquals(Arabic.FieldKind.ID, Arabic.fieldKindOf("رقم الهوية"))
        assertEquals(Arabic.FieldKind.CARD, Arabic.fieldKindOf("رقم البطاقة الائتمانية"))
        assertEquals(Arabic.FieldKind.OTP, Arabic.fieldKindOf("رمز التحقق"))
        assertEquals(Arabic.FieldKind.BIRTH, Arabic.fieldKindOf("تاريخ الميلاد"))
        assertEquals(Arabic.FieldKind.OTHER, Arabic.fieldKindOf("خانة عشوائية"))
    }

    @Test
    fun secretFieldsAreRecognisedSoTheyAreNeverExplained() {
        assertEquals(Arabic.FieldKind.PASSWORD, Arabic.fieldKindOf("كلمة المرور"))
        assertEquals(Arabic.FieldKind.OTP, Arabic.fieldKindOf("OTP code"))
        assertEquals(Arabic.FieldKind.CARD, Arabic.fieldKindOf("Card number"))
    }

    @Test
    fun combiningMarksSurviveNormalisation() {
        assertEquals("नमस्ते", Arabic.normalize("नमस्ते"))
    }
}
