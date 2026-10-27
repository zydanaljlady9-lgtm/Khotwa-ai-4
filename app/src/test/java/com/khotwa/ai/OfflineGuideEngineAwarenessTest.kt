package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** يختبر المنطق النقي (plan) دون الحاجة لموارد الترجمة. */
class OfflineGuideEngineAwarenessTest {

    private val login = listOf(
        El(0, "text", "Welcome", "top-center", false),
        El(1, "field-empty", "Email", "center", true),
        El(2, "secret-field", "Password", "center", true),
        El(3, "button", "Sign in", "bottom-center", false),
        El(4, "button", "Create account", "bottom-right", false),
        El(5, "button", "Forgot password?", "bottom-left", false)
    )

    private fun plan(q: String, els: List<El>, auto: Boolean = false, down: Boolean = false, up: Boolean = false) =
        OfflineGuideEngine.plan(q, "Chrome · example.org", els, down, up, false, auto)

    @Test
    fun picksTheNamedButton() {
        val p = plan("تسجيل الدخول", login)
        assertEquals(OfflineGuideEngine.Say.PRESS, p.say)
        assertEquals(3, p.target?.id)
        assertTrue(p.conf >= 0.65)
    }

    @Test
    fun matchesByFunctionNotJustWords() {
        assertEquals(4, plan("اريد انشاء حساب جديد", login).target?.id)
    }

    @Test
    fun matchesEnglishButtonFromArabicSpeech() {
        val els = listOf(
            El(0, "button", "Sign up", "top-right", false),
            El(1, "button", "Learn more", "bottom-center", false)
        )
        assertEquals(0, plan("سجل لي", els).target?.id)
    }

    @Test
    fun explainsTheFirstEmptyFieldWhenAsked() {
        val p = plan("ماذا أكتب في الخانة", login)
        assertEquals(OfflineGuideEngine.Say.FIELD, p.say)
        assertEquals(1, p.target?.id)
        assertEquals(Arabic.FieldKind.EMAIL, p.fieldKind)
    }

    @Test
    fun explainsTheNamedFieldButNeverAsksForPassword() {
        val p = plan("وش اكتب في كلمة المرور", login)
        assertEquals(OfflineGuideEngine.Say.SECRET_FIELD, p.say)
        assertNull(p.target)
    }

    @Test
    fun focusedFieldWinsWhenNoneNamed() {
        val els = listOf(
            El(0, "field-empty", "Full name", "top-center", true),
            El(1, "field-empty", "Phone number", "center", true, true, true)
        )
        assertEquals(1, plan("ماذا اكتب", els).target?.id)
    }

    @Test
    fun answersWhereAmIWithoutTarget() {
        val p = plan("أين أنا؟", login)
        assertEquals(OfflineGuideEngine.Say.WHERE, p.say)
        assertEquals("Chrome · example.org", p.arg)
        assertNull(p.target)
    }

    @Test
    fun scrollCommands() {
        val els = listOf(El(0, "button", "Submit", "bottom-center", false))
        assertEquals("down", plan("مرر للأسفل", els, down = true).scroll)
        assertEquals(OfflineGuideEngine.Say.END_OF_PAGE, plan("مرر للأسفل", els, down = false).say)
        assertEquals("up", plan("اصعد فوق", els, up = true).scroll)
    }

    @Test
    fun autoStepPrefersEmptyFieldThenNextButton() {
        assertEquals(1, plan("", login, auto = true).target?.id)
        val els = listOf(
            El(0, "button", "Back", "top-left", false),
            El(1, "button", "Next", "bottom-right", false)
        )
        assertEquals(1, plan("", els, auto = true).target?.id)
    }

    @Test
    fun autoStepSkipsDisabledButtons() {
        val els = listOf(
            El(0, "button", "Next", "bottom-right", false, enabled = false),
            El(1, "button", "Save", "bottom-left", false)
        )
        assertEquals(1, plan("", els, auto = true).target?.id)
    }

    @Test
    fun doesNotGuessWhenNothingMatches() {
        val p = plan("اريد شيئاً غريباً جداً", login)
        assertNull(p.target)
        assertTrue(p.conf < 0.65)
        assertEquals(OfflineGuideEngine.Say.UNSURE, p.say)
        assertTrue(p.options.contains("Sign in"))
    }

    @Test
    fun uncertainMatchAsksInsteadOfGuessing() {
        val els = listOf(
            El(0, "button", "Upload profile picture", "top-right", false),
            El(1, "button", "Close", "top-left", false)
        )
        val p = plan("تحميل", els)
        // لا نخمّن بثقة من كلمة عربية واحدة أمام نص إنجليزي: نعرض الخيارات ونسأل.
        assertNull(p.target)
        assertEquals(OfflineGuideEngine.Say.UNSURE, p.say)
        assertTrue(p.options.isNotEmpty())
    }

    @Test
    fun emptyScreenSaysNoElements() {
        assertEquals(OfflineGuideEngine.Say.NO_ELEMENTS, plan("اضغط", emptyList()).say)
    }

    @Test
    fun stopWordEndsGuidance() {
        assertTrue(plan("خلاص", login).done)
    }

    @Test
    fun neverTargetsSecretFields() {
        val p = plan("كلمة المرور", listOf(El(0, "secret-field", "Password", "top-center", true, true, true)))
        assertFalse(p.target?.secret == true)
    }
}
