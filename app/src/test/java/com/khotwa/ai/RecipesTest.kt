package com.khotwa.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipesTest {

    private val settingsHome = listOf(
        El(0, "text", "Settings", "top-center", false),
        El(1, "button", "Connections", "middle-center", false),
        El(2, "button", "Display", "bottom-center", false)
    )

    private val wifi = listOf(
        El(0, "button", "Wi-Fi", "top-center", false),
        El(1, "button", "Bluetooth", "middle-center", false)
    )

    @Test
    fun firstStepIsFoundOnTheHomeScreen() {
        val h = Recipes.match("اريد الواي فاي", "com.android.settings", settingsHome)
        assertEquals(1, h?.el?.id)
        assertEquals(1, h?.step)
        assertEquals(2, h?.total)
    }

    @Test
    fun recipeAdvancesWhenThePageChanges() {
        val h = Recipes.match("اريد الواي فاي", "com.android.settings", wifi)
        assertEquals(0, h?.el?.id)
        assertEquals(2, h?.step)
    }

    @Test
    fun wrongAppGivesNothing() {
        assertNull(Recipes.match("اريد الواي فاي", "com.whatsapp", settingsHome))
    }

    @Test
    fun unrelatedQuestionGivesNothing() {
        assertNull(Recipes.match("ما الطقس اليوم", "com.android.settings", settingsHome))
    }

    @Test
    fun secretAndDisabledElementsAreNeverChosen() {
        val els = listOf(
            El(0, "button", "Wi-Fi", "top-center", false, enabled = false),
            El(1, "secret-field", "Wi-Fi password", "middle-center", true)
        )
        assertNull(Recipes.match("واي فاي", "com.android.settings", els))
    }
}
