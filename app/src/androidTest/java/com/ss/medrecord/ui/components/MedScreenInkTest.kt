package com.ss.medrecord.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ink a screen body inherits when a call site does not name a colour.
 *
 * This exists because of a real bug: [MedScreen]'s Scaffold is transparent so
 * the page's glow shows through, and Scaffold derives its content colour from
 * its container - contentColorFor(Transparent) being unspecified, which Text
 * resolves to black. Every Text that named its own colour looked right, so the
 * damage was invisible in review and showed up only as a visit's date, doctor
 * and patient reading as blank rows in dark mode while their labels were fine.
 *
 * Asserting the composition local rather than reading pixels: the invariant is
 * "an unstyled Text on a page is legible", and this is the value that decides
 * it for every screen at once.
 */
@RunWith(AndroidJUnit4::class)
class MedScreenInkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aScreenBodyInheritsThePageInkInDark() = assertPageInk(dark = true)

    @Test
    fun aScreenBodyInheritsThePageInkInLight() = assertPageInk(dark = false)

    private fun assertPageInk(dark: Boolean) {
        var inherited = Color.Unspecified
        var expected = Color.Unspecified

        composeRule.setContent {
            MedRecordTheme(darkTheme = dark) {
                MedScreen {
                    inherited = LocalContentColor.current
                    expected = MedTheme.colors.textPrimary
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(expected, inherited)
        // Both halves of the old failure: unspecified is what Scaffold handed
        // down, black is what Text made of it.
        assertNotEquals(Color.Unspecified, inherited)
        if (dark) assertNotEquals(Color.Black, inherited)
    }
}
