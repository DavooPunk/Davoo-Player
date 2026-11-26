package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class DeezerTagSuggestion(
    val deezerTrackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val coverXlUrl: String? = null,
    val durationSec: Int = 0
)

@Immutable
data class BatchItemOutcome(
    val trackTitle: String,
    val detail: String,
    val isSuccess: Boolean
)

@Immutable
data class BatchAutoTagUiState(
    val isVisible: Boolean = false,
    val isRunning: Boolean = false,
    val waitingForWritePermission: Boolean = false,
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val currentTrackLabel: String = "",
    val onlyMissingMetadata: Boolean = true,
    val overwriteArtwork: Boolean = true,
    val targetTracks: List<AudioTrack> = emptyList(),
    val completed: Boolean = false,
    val recentResults: List<BatchItemOutcome> = emptyList()
)

@Immutable
data class BatchLyricsUiState(
    val isVisible: Boolean = false,
    val isRunning: Boolean = false,
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val syncedDownloadedCount: Int = 0,
    val plainDownloadedCount: Int = 0,
    val skippedAlreadySyncedCount: Int = 0,
    val notFoundCount: Int = 0,
    val currentTrackLabel: String = "",
    val syncedOnly: Boolean = true,
    val overwriteExisting: Boolean = false,
    val targetTracks: List<AudioTrack> = emptyList(),
    val completed: Boolean = false,
    val isExportingFolder: Boolean = false,
    val recentResults: List<BatchItemOutcome> = emptyList()
)
