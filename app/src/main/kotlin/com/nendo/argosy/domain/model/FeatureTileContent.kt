package com.nendo.argosy.domain.model

/**
 * What the feature tiles on a curated page draw from, read independently of the tile list: the
 * game the continue and spotlight tiles resume, what the RetroAchievements tile shows, and the
 * newest game in the library the spotlight tile offers. Each is null when no tile reading it is on
 * the page.
 */
data class FeatureTileContent(
    val continueGameId: Long?,
    val raSummary: RaTileContent?,
    val newestGameId: Long? = null
)
