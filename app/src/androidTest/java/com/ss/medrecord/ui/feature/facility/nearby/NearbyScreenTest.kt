package com.ss.medrecord.ui.feature.facility.nearby

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.domain.model.NearbyPlace
import com.ss.medrecord.ui.theme.MedRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The nearby screen's three blocked states and its results.
 *
 * These matter more than most UI tests because every blocker here is reached
 * through something outside the app - a missing build key, a permission dialog,
 * a system toggle - and getting the wrong one on screen sends the user to the
 * wrong settings page. Driving the stateless screen directly is also the only
 * way to see these without an API key, a location fix and a signed-in account.
 */
@RunWith(AndroidJUnit4::class)
class NearbyScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aBuildWithNoApiKeySaysSoAndOffersNothingToTap() {
        setContent(NearbyUiState(isMapsConfigured = false, hasLocationPermission = true))

        composeRule.onNodeWithText("Search is unavailable").assertIsDisplayed()
        // Nothing the user can do about it, so no button is offered.
        composeRule.onNodeWithText("Allow location").assertDoesNotExist()
    }

    @Test
    fun aMissingPermissionExplainsWhatTheLocationIsUsedFor() {
        // The promise the manifest comment makes has to actually reach the user
        // at the moment they are deciding.
        setContent(NearbyUiState(hasLocationPermission = false))

        composeRule.onNodeWithText("Location needed to search").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Approximate location only, used to rank clinics by distance. It is never " +
                "saved, never synced and never leaves this device except as a search radius.",
        ).assertIsDisplayed()
    }

    @Test
    fun grantingLocationAsksForThePermissionRatherThanOpeningSettings() {
        val events = mutableListOf<NearbyEvent>()
        setContent(NearbyUiState(hasLocationPermission = false), events::add)

        composeRule.onNodeWithText("Allow location").performClick()

        assertTrue(events.contains(NearbyEvent.GrantLocationClicked))
    }

    @Test
    fun locationSwitchedOffSendsTheUserToSystemSettingsInstead() {
        // Distinct from the permission case: the app cannot fix this one with a
        // dialog, and prompting for permission again would achieve nothing.
        val events = mutableListOf<NearbyEvent>()
        setContent(
            NearbyUiState(hasLocationPermission = true, isLocationEnabled = false),
            events::add,
        )

        composeRule.onNodeWithText("Location is switched off").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performClick()

        assertTrue(events.contains(NearbyEvent.EnableLocationClicked))
    }

    @Test
    fun resultsShowNameAddressAndDistance() {
        setContent(searchedState())

        composeRule.onNodeWithText("Sunrise Hospital").assertIsDisplayed()
        composeRule.onNodeWithText("44 Residency Road, Bengaluru").assertIsDisplayed()
        composeRule.onNodeWithText("420 m").assertIsDisplayed()
        composeRule.onNodeWithText("2.3 km").assertIsDisplayed()
    }

    @Test
    fun savingAResultAsksToSaveThatPlace() {
        val events = mutableListOf<NearbyEvent>()
        setContent(searchedState(), events::add)

        composeRule.onAllNodes(hasText("Save"))[0].performClick()

        val saved = events.filterIsInstance<NearbyEvent.SaveClicked>().single()
        assertEquals("p1", saved.place.placeId)
    }

    @Test
    fun anEmptySearchSaysSoAndMentionsTheNetwork() {
        // The one screen in the app that needs a connection, so "no results"
        // has to be distinguishable from "you are offline".
        setContent(
            NearbyUiState(hasLocationPermission = true, hasSearched = true, results = emptyList()),
        )

        composeRule.onNodeWithText("Nothing found nearby").assertIsDisplayed()
    }

    @Test
    fun beforeSearchingThePrivacyPromiseIsAlreadyVisible() {
        setContent(NearbyUiState(hasLocationPermission = true))

        composeRule.onNodeWithText("Search around you").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Your location is used to rank results and is never saved or synced.",
        ).assertIsDisplayed()
    }

    private fun setContent(
        state: NearbyUiState,
        onEvent: (NearbyEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            MedRecordTheme {
                NearbyScreen(state = state, onEvent = onEvent)
            }
        }
    }

    private fun searchedState() = NearbyUiState(
        hasLocationPermission = true,
        hasSearched = true,
        origin = Coordinates(12.97, 77.59),
        results = listOf(
            NearbyPlace(
                placeId = "p1",
                name = "Sunrise Hospital",
                address = "44 Residency Road, Bengaluru",
                coordinates = Coordinates(12.97, 77.60),
                distanceMetres = 420.0,
            ),
            NearbyPlace(
                placeId = "p2",
                name = "City Care Clinic",
                address = "12 MG Road, Bengaluru",
                coordinates = Coordinates(12.98, 77.61),
                distanceMetres = 2300.0,
            ),
        ),
    )
}
