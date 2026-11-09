package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.home.HomeMediaUi

/**
 * Which of the spotlight's questions a slide answers: the game to go back to, the show to go
 * back to, or the newest game not played yet.
 */
enum class SpotlightSlideKind { CONTINUE_PLAYING, CONTINUE_WATCHING, NEWLY_ADDED }

/**
 * One card of the spotlight. The two game kinds carry [game]; the watching kind carries [media].
 */
data class SpotlightSlideUi(
    val kind: SpotlightSlideKind,
    val game: HomeGameUi? = null,
    val media: HomeMediaUi? = null
) {
    val key: String
        get() = kind.name + (game?.id?.toString() ?: media?.itemId.orEmpty())
}

/**
 * The words the spotlight draws, as resource ids the composing surface owns. [name] names the tile
 * itself in menus; the rest head the slides and fill the empty card.
 */
data class SpotlightTileLabels(
    @StringRes val name: Int,
    @StringRes val continuePlaying: Int,
    @StringRes val continueWatching: Int,
    @StringRes val newlyAdded: Int,
    @StringRes val empty: Int,
    @StringRes val emptyHint: Int
)

/**
 * What the spotlight draws. [index] counts every turn without bound and wraps onto [slides], so a
 * slide arriving or leaving never needs the counter reset.
 */
data class SpotlightTileUi(
    val slides: List<SpotlightSlideUi>,
    val index: Int,
    val labels: SpotlightTileLabels
) {
    val currentIndex: Int
        get() = if (slides.isEmpty()) 0 else index.mod(slides.size)

    val current: SpotlightSlideUi?
        get() = slides.getOrNull(currentIndex)
}

/**
 * The slides a spotlight rotates through, in this order: the last game played, the show left part
 * watched, and [newest] unless it is the game already leading.
 */
fun spotlightSlidesFor(
    continueGame: HomeGameUi?,
    watching: HomeMediaUi?,
    newest: HomeGameUi?
): List<SpotlightSlideUi> = listOfNotNull(
    continueGame?.let { SpotlightSlideUi(SpotlightSlideKind.CONTINUE_PLAYING, game = it) },
    watching?.let { SpotlightSlideUi(SpotlightSlideKind.CONTINUE_WATCHING, media = it) },
    newest?.takeIf { it.id != continueGame?.id }
        ?.let { SpotlightSlideUi(SpotlightSlideKind.NEWLY_ADDED, game = it) }
)

fun HomeTileTargetRef?.isSpotlight(): Boolean =
    (this as? HomeTileTargetRef.Feature)?.kind == FeatureTileKind.SPOTLIGHT
