package com.nendo.argosy.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberResolvedBackgroundPath
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import com.nendo.argosy.ui.screens.media.components.MediaProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Which of the spotlight's questions a slide answers: the game to go back to, the show to go
 * back to, or the newest game in the library.
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
 * watched, and the newest game in the library unless that is the game already leading.
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

@StringRes
private fun SpotlightSlideKind.headingRes(labels: SpotlightTileLabels): Int = when (this) {
    SpotlightSlideKind.CONTINUE_PLAYING -> labels.continuePlaying
    SpotlightSlideKind.CONTINUE_WATCHING -> labels.continueWatching
    SpotlightSlideKind.NEWLY_ADDED -> labels.newlyAdded
}

@Composable
private fun SpotlightSlideKind.accent(): Color {
    val semantic = LocalLauncherTheme.current.semanticColors
    return when (this) {
        SpotlightSlideKind.CONTINUE_PLAYING -> LocalArgosyTheme.current.focusAccent
        SpotlightSlideKind.CONTINUE_WATCHING -> semantic.info
        SpotlightSlideKind.NEWLY_ADDED -> semantic.success
    }
}

@Composable
internal fun SpotlightTileBox(
    placement: Modifier,
    rect: TileRect,
    spotlight: SpotlightTileUi,
    isFocused: Boolean,
    isOverlapped: Boolean,
    editModeLabel: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val boxArtStyle = LocalBoxArtStyle.current
    val shape = RoundedCornerShape(boxArtStyle.cornerRadiusDp)
    val current = spotlight.current
    val gradientColors = current?.game?.gradientColors ?: current?.media?.gradientColors
    val accent = boxArtStyle.accentColor ?: theme.focusAccent
    val borderBrush = wideTileBorderBrush(
        style = boxArtStyle.borderStyle,
        gradientColors = gradientColors,
        accent = accent,
        secondary = boxArtStyle.secondaryColor ?: accent
    ).takeIf { isFocused && boxArtStyle.borderThicknessDp.value > 0f }
    val reducedMotion = LocalMotionTier.current == MotionTier.Reduced

    Box(modifier = placement) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .boxArtFrame(
                    isFocused = isFocused,
                    focusScale = focusScaleForSpan(rect),
                    alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null,
                    artworkGradient = gradientColors,
                    background = SolidColor(theme.surfaceRaised)
                )
                .then(
                    if (borderBrush != null) {
                        Modifier.border(boxArtStyle.borderThicknessDp, borderBrush, shape)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (onLongClick == null) {
                        Modifier.clickableNoFocus(onClick = onClick)
                    } else {
                        Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
                    }
                )
        ) {
            if (current == null) {
                SpotlightEmpty(labels = spotlight.labels)
            } else {
                val fadeMs = if (reducedMotion) 0 else ComponentDefaults.SpotlightTile.crossfadeMs
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs)) },
                    contentKey = { it.key },
                    label = "spotlightSlide",
                    modifier = Modifier.fillMaxSize()
                ) { slide ->
                    SpotlightSlide(
                        slide = slide,
                        labels = spotlight.labels,
                        reducedMotion = reducedMotion,
                        onCoverLoaded = onCoverLoaded
                    )
                }
                if (spotlight.slides.size > 1) {
                    SpotlightDots(
                        count = spotlight.slides.size,
                        selected = spotlight.currentIndex,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Dimens.spacingMd)
                    )
                }
            }
        }
        if (editModeLabel != null && isFocused) {
            TileModeTab(label = editModeLabel, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun SpotlightSlide(
    slide: SpotlightSlideUi,
    labels: SpotlightTileLabels,
    reducedMotion: Boolean,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val game = slide.game
    val media = slide.media
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            game != null -> SpotlightGameArt(
                game = game,
                reducedMotion = reducedMotion,
                onCoverLoaded = onCoverLoaded
            )
            media != null -> SpotlightArt(
                model = (media.backdropUrl?.takeIf { it.isNotBlank() } ?: media.posterUrl)
                    .takeIf { it.isNotBlank() },
                reducedMotion = reducedMotion,
                onLoaded = null
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            theme.surfaceBase.copy(alpha = ComponentDefaults.SpotlightTile.scrimTopAlpha),
                            theme.surfaceBase.copy(alpha = ComponentDefaults.SpotlightTile.scrimBottomAlpha)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Text(
                text = stringResource(slide.kind.headingRes(labels)),
                style = MaterialTheme.typography.labelLarge,
                color = slide.kind.accent(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = game?.title ?: media?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = game?.platformDisplayName ?: media?.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (media != null && media.progressFraction > 0f) {
                MediaProgressBar(
                    fraction = media.progressFraction,
                    modifier = Modifier.padding(top = Dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun SpotlightGameArt(
    game: HomeGameUi,
    reducedMotion: Boolean,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?
) {
    val background = rememberResolvedBackgroundPath(game.id, game.backgroundPath)
    val cover = rememberResolvedCoverPath(game.id, game.coverPath)
    if (background != null) {
        SpotlightArt(
            model = rememberFileImageModel(background),
            reducedMotion = reducedMotion,
            onLoaded = null
        )
    } else {
        SpotlightArt(
            model = rememberFileImageModel(cover),
            reducedMotion = reducedMotion,
            onLoaded = { bitmap -> onCoverLoaded?.invoke(game.id, bitmap) }
        )
    }
}

@Composable
private fun SpotlightArt(
    model: Any?,
    reducedMotion: Boolean,
    onLoaded: ((Bitmap) -> Unit)?
) {
    if (model == null) return
    val zoom: State<Float>? = if (reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "spotlightKenBurns").animateFloat(
            initialValue = 1f,
            targetValue = ComponentDefaults.SpotlightTile.kenBurnsScale,
            animationSpec = infiniteRepeatable(
                animation = tween(ComponentDefaults.SpotlightTile.kenBurnsMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "spotlightKenBurnsScale"
        )
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = zoom?.value ?: 1f
                scaleX = scale
                scaleY = scale
            },
        onSuccess = { state ->
            val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) onLoaded?.invoke(bitmap)
        }
    )
}

@Composable
private fun SpotlightDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .size(if (isSelected) Dimens.dotLg else Dimens.dotSm)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            theme.textPrimary
                        } else {
                            theme.textPrimary.copy(alpha = ComponentDefaults.SpotlightTile.dotIdleAlpha)
                        }
                    )
            )
        }
    }
}

@Composable
private fun SpotlightEmpty(labels: SpotlightTileLabels) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = theme.textDim,
            modifier = Modifier.size(Dimens.iconXl)
        )
        Text(
            text = stringResource(labels.empty),
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(labels.emptyHint),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
