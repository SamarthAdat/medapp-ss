package com.ss.medrecord.ui.feature.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.domain.model.DashboardSnapshot
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.ui.theme.MedRecordTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dashboard's bottom bar against the system navigation bar.
 *
 * The app draws edge to edge, and Scaffold places a bottom bar at the bottom of
 * the *window* rather than above the system bars - so on a gesture-navigation
 * device the home handle was drawn straight across Today, History, the add
 * button, Meds and Files. Nothing about that is visible in a screenshot test
 * with no insets, which is why this one runs against a real activity.
 *
 * Skipped rather than passed on a device with no navigation inset: there is
 * nothing to clear there, and asserting it anyway would make the test look
 * like it was checking something it was not.
 */
@RunWith(AndroidJUnit4::class)
class HomeBottomBarInsetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theBottomBarClearsTheSystemNavigationBar() {
        composeRule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }

        var navigationInset: Dp = 0.dp
        composeRule.setContent {
            MedRecordTheme(darkTheme = true) {
                navigationInset = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        dashboard = DashboardSnapshot(
                            patients = listOf(
                                Patient(
                                    patientId = "p1",
                                    userId = "u1",
                                    name = "Asha Rao",
                                    relationship = Relationship.SELF,
                                    createdAt = 0L,
                                    updatedAt = 0L,
                                ),
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }
        composeRule.waitForIdle()

        assumeTrue("No navigation inset on this device", navigationInset > 0.dp)

        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        val lastItem = composeRule.onNodeWithText("Files").getUnclippedBoundsInRoot()

        assertTrue(
            "Bottom bar label ends at ${lastItem.bottom}, inside the " +
                "$navigationInset navigation inset at the bottom of $window",
            lastItem.bottom <= window.bottom - navigationInset,
        )
    }
}
