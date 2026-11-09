package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SPOTLIGHT_GAME_ID = 31L

class HomeSpotlightInputTest {

    private val game = HomeGameUi(
        id = SPOTLIGHT_GAME_ID,
        title = "Game",
        platformId = 1L,
        platformSlug = "snes",
        platformDisplayName = "Super Nintendo",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true
    )
    private val show = HomeMediaUi(
        itemId = "show-1",
        title = "Show",
        subtitle = "S1:E2",
        posterUrl = "https://media.example/poster"
    )

    private fun state(continueGameId: Long? = null, signedIn: Boolean = false) = HomeUiState(
        layoutKind = HomeLayoutKind.CUSTOM_GRID,
        tileGames = mapOf(SPOTLIGHT_GAME_ID to game),
        continueGameId = continueGameId,
        isMediaSignedIn = signedIn,
        continueWatchingMedia = listOf(show),
        customGrid = CustomGridState(
            tiles = listOf(
                HomeTile(
                    id = 1L,
                    pageIndex = 0,
                    rect = TileRect(0, 0, 2, 2),
                    target = HomeTileTargetRef.Feature(FeatureTileKind.SPOTLIGHT)
                )
            )
        )
    )

    private fun handler(state: HomeUiState): Pair<HomeInputHandler, HomeInputActions> {
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        every { actions.engageFocusedTile() } returns false
        val handler = HomeInputHandler(
            actions,
            isDefaultView = true,
            onGameSelect = {},
            onNavigateToDefault = {},
            onDrawerToggle = {}
        )
        return handler to actions
    }

    @Test
    fun `confirm on a game card plays that exact game`() {
        val (handler, actions) = handler(state(continueGameId = SPOTLIGHT_GAME_ID))

        handler.onConfirm()

        verify(exactly = 1) { actions.activateExactGame(game) }
        verify(exactly = 0) { actions.playSpotlightMedia(any()) }
    }

    @Test
    fun `confirm on a show card plays the show`() {
        val (handler, actions) = handler(state(signedIn = true))

        handler.onConfirm()

        verify(exactly = 1) { actions.playSpotlightMedia(show) }
        verify(exactly = 0) { actions.activateExactGame(any()) }
    }

    @Test
    fun `confirm on an empty spotlight opens the library`() {
        val (handler, actions) = handler(state())

        handler.onConfirm()

        verify(exactly = 1) { actions.navigateToLibrary(null, null) }
    }

    @Test
    fun `the secondary button turns to the next card`() {
        val (handler, actions) = handler(state(continueGameId = SPOTLIGHT_GAME_ID, signedIn = true))

        assertTrue(handler.onSecondaryAction().handled)
        verify(exactly = 1) { actions.advanceSpotlight() }
    }
}
