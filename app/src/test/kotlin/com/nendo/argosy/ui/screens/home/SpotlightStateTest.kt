package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.SPOTLIGHT_TILE_MIN_SPAN
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.minimumSpanFor
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.SpotlightSlideKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PLAYED_GAME_ID = 11L
private const val NEWEST_GAME_ID = 22L

class SpotlightStateTest {

    private val played = game(PLAYED_GAME_ID)
    private val newest = game(NEWEST_GAME_ID)
    private val show = HomeMediaUi(
        itemId = "show-1",
        title = "Show",
        subtitle = "S1:E2",
        posterUrl = "https://media.example/poster"
    )

    private fun spotlightState(
        continueGameId: Long? = PLAYED_GAME_ID,
        newestGameId: Long? = NEWEST_GAME_ID,
        signedIn: Boolean = false,
        page: Int = 0
    ) = HomeUiState(
        layoutKind = HomeLayoutKind.CUSTOM_GRID,
        tileGames = mapOf(PLAYED_GAME_ID to played, NEWEST_GAME_ID to newest),
        continueGameId = continueGameId,
        spotlightNewestGameId = newestGameId,
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
            ),
            page = page,
            cell = GridCell(0, 0),
            columns = 4,
            rows = 4
        )
    )

    @Test
    fun `slides run from the game played to the show watched to the newest game`() {
        val kinds = spotlightState(signedIn = true).spotlightSlides.map { it.kind }

        assertEquals(
            listOf(
                SpotlightSlideKind.CONTINUE_PLAYING,
                SpotlightSlideKind.CONTINUE_WATCHING,
                SpotlightSlideKind.NEWLY_ADDED
            ),
            kinds
        )
    }

    @Test
    fun `the show is left out while the media server is signed out`() {
        val kinds = spotlightState(signedIn = false).spotlightSlides.map { it.kind }

        assertEquals(
            listOf(SpotlightSlideKind.CONTINUE_PLAYING, SpotlightSlideKind.NEWLY_ADDED),
            kinds
        )
    }

    @Test
    fun `the newest game is not repeated when it is the game being played`() {
        val kinds = spotlightState(newestGameId = PLAYED_GAME_ID).spotlightSlides.map { it.kind }

        assertEquals(listOf(SpotlightSlideKind.CONTINUE_PLAYING), kinds)
    }

    @Test
    fun `the focused spotlight answers with the game on the slide showing`() {
        val state = spotlightState()

        assertEquals(played, state.focusedTileGame)
        assertEquals(newest, state.copy(spotlightIndex = 1).focusedTileGame)
        assertEquals(played, state.copy(spotlightIndex = 2).focusedTileGame)
    }

    @Test
    fun `a show slide answers with the show and no game`() {
        val state = spotlightState(signedIn = true).copy(spotlightIndex = 1)

        assertEquals(show, state.currentSpotlightSlide?.media)
        assertNull(state.focusedTileGame)
    }

    @Test
    fun `rotation runs only with two slides on the page showing`() {
        assertTrue(spotlightState().rotatesSpotlight)
        assertFalse(spotlightState(newestGameId = null).rotatesSpotlight)
        assertFalse(spotlightState(page = 1).rotatesSpotlight)
    }

    @Test
    fun `a spotlight tile is never smaller than its floor`() {
        assertEquals(
            SPOTLIGHT_TILE_MIN_SPAN,
            minimumSpanFor(HomeTileTargetRef.Feature(FeatureTileKind.SPOTLIGHT))
        )
        assertEquals(1, minimumSpanFor(HomeTileTargetRef.Feature(FeatureTileKind.CONTINUE)))
    }

    private fun game(id: Long) = HomeGameUi(
        id = id,
        title = "Game $id",
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
}
