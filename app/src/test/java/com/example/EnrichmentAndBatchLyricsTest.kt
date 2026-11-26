package com.example

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.TranslationEngine
import com.example.data.local.SonoraDatabase
import com.example.data.local.TrackLyricsEntity
import com.example.data.remote.MusicApiService
import com.example.data.repository.DeviceMusicScanner
import com.example.data.repository.LibraryEnrichmentCoordinator
import com.example.data.repository.MusicRepository
import com.example.data.repository.OnlineLyricsCandidate
import com.example.data.repository.OnlineLyricsFetcher
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.AudioTrack
import com.example.model.BatchAutoTagUiState
import com.example.model.BatchItemOutcome
import com.example.model.BatchLyricsUiState
import com.example.model.DeezerTagSuggestion
import com.example.playback.PlaybackUiState
import com.example.ui.components.BatchAutoTagSheet
import com.example.ui.components.BatchLyricsSheet
import com.example.ui.components.TagEditorBottomSheet
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SonoraTheme
import com.example.ui.viewmodel.SonoraUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnrichmentAndBatchLyricsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: SonoraDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `public deezer api search and details fill tags cleanly without arl or cookies`() = runTest {
        val recordedUrls = mutableListOf<String>()
        val recordedCookieHeaders = mutableListOf<String?>()

        val searchJson = """
            {
              "data": [
                {
                  "id": 1001,
                  "title": "Digital Love (Radio Edit)",
                  "duration": 180,
                  "artist": { "name": "Daft Punk" },
                  "album": {
                    "id": 302,
                    "title": "Discovery",
                    "cover_xl": "http://cdn-images.dzcdn.net/images/cover/abc/1000x1000.jpg"
                  }
                },
                {
                  "id": 1002,
                  "title": "Digital Love",
                  "duration": 301,
                  "artist": { "name": "Daft Punk" },
                  "album": {
                    "id": 302,
                    "title": "Discovery",
                    "cover_xl": "https://cdn-images.dzcdn.net/images/cover/abc/1000x1000.jpg"
                  }
                }
              ]
            }
        """.trimIndent()

        val trackJson = """
            {
              "id": 1002,
              "title": "Digital Love",
              "duration": 301,
              "track_position": 3,
              "release_date": "2001-03-12",
              "artist": { "name": "Daft Punk" },
              "album": {
                "id": 302,
                "title": "Discovery",
                "cover_xl": "https://cdn-images.dzcdn.net/images/cover/abc/1000x1000.jpg"
              }
            }
        """.trimIndent()

        val albumJson = """
            {
              "id": 302,
              "title": "Discovery",
              "release_date": "2001-03-12",
              "artist": { "name": "Daft Punk" },
              "genres": {
                "data": [
                  { "id": 113, "name": "Dance" }
                ]
              }
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                recordedUrls += url
                recordedCookieHeaders += req.header("Cookie")

                val payload = when {
                    url.contains("/search") -> searchJson
                    url.contains("/track/1002") -> trackJson
                    url.contains("/album/302") -> albumJson
                    else -> "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val service = MusicApiService(
            client = mockClient,
            ioDispatcher = Dispatchers.Unconfined,
            deezerBaseUrl = "https://api.deezer.com"
        )

        val suggestions = service.searchDeezerTagSuggestions(
            title = "Digital Love",
            artist = "Artista desconocido",
            album = "<unknown>",
            durationSec = 300
        ).getOrThrow()

        assertEquals(2, suggestions.size)
        // Sorted closest to 300s duration first
        assertEquals(1002L, suggestions.first().deezerTrackId)
        assertEquals("Digital Love", suggestions.first().title)
        assertEquals("Daft Punk", suggestions.first().artist)

        val detailed = service.fetchDeezerTrackTagDetails(suggestions.first())
        assertEquals(3, detailed.trackNumber)
        assertEquals(2001, detailed.year)
        assertEquals("Daft Punk", detailed.albumArtist)
        assertEquals("Dance", detailed.genre)
        assertEquals("https://cdn-images.dzcdn.net/images/cover/abc/1000x1000.jpg", detailed.coverXlUrl)

        assertTrue(recordedUrls.all { it.startsWith("https://api.deezer.com/") })
        assertTrue(recordedCookieHeaders.all { it == null })
    }

    @Test
    fun `needsMetadataEnrichment and pickBestLyricsCandidate enforce metadata and synced LRC priorities`() {
        val completeTrack = sampleTrack(
            id = "complete",
            title = "Instant Crush",
            artist = "Daft Punk",
            album = "Random Access Memories",
            albumArtUri = "content://media/external/audio/albumart/5",
            year = 2013,
            trackNumber = 5
        )
        assertFalse(LibraryEnrichmentCoordinator.needsMetadataEnrichment(completeTrack))

        val missingCover = completeTrack.copy(albumArtUri = null)
        val unknownArtist = completeTrack.copy(artist = "Artista desconocido")
        val missingYear = completeTrack.copy(year = 0)
        assertTrue(LibraryEnrichmentCoordinator.needsMetadataEnrichment(missingCover))
        assertTrue(LibraryEnrichmentCoordinator.needsMetadataEnrichment(unknownArtist))
        assertTrue(LibraryEnrichmentCoordinator.needsMetadataEnrichment(missingYear))

        val plainOnly = listOf(
            OnlineLyricsCandidate(
                id = 10L,
                title = "Song",
                artist = "Artist",
                album = "Album",
                durationSec = 210,
                rawLyrics = "Line one\nLine two",
                isSynced = false
            )
        )
        assertNull(
            LibraryEnrichmentCoordinator.pickBestLyricsCandidate(
                candidates = plainOnly,
                trackDurationSec = 210,
                syncedOnly = true
            )
        )
        assertNotNull(
            LibraryEnrichmentCoordinator.pickBestLyricsCandidate(
                candidates = plainOnly,
                trackDurationSec = 210,
                syncedOnly = false
            )
        )

        val mixed = listOf(
            OnlineLyricsCandidate(11L, "Song", "Artist", "Live", 400, "[00:05.00]Too long", true),
            OnlineLyricsCandidate(12L, "Song", "Artist", "Studio", 212, "[00:05.00]Just right", true),
            OnlineLyricsCandidate(13L, "Song", "Artist", "Studio", 210, "Plain", false)
        )
        val picked = LibraryEnrichmentCoordinator.pickBestLyricsCandidate(
            candidates = mixed,
            trackDurationSec = 210,
            syncedOnly = true
        )
        assertEquals(12L, picked?.id)
    }

    @Test
    fun `batch lyrics coordinator downloads synced LRC into Room skips already synced and respects syncedOnly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Pre-seed track 1 with already synced lyrics in Room
        db.trackLyricsDao().upsert(
            TrackLyricsEntity(
                trackId = "t_already_synced",
                rawLrcText = "[00:01.00]Already synced",
                sourceLabel = "Descargada (LRCLIB)",
                updatedAt = 1L
            )
        )

        val fakeFetcher = object : OnlineLyricsFetcher {
            override suspend fun searchLyrics(
                title: String,
                artist: String,
                album: String,
                durationSec: Int?,
                customQuery: String?
            ): Result<List<OnlineLyricsCandidate>> {
                return when (title) {
                    "Starlight" -> Result.success(
                        listOf(
                            OnlineLyricsCandidate(
                                id = 200L,
                                title = "Starlight",
                                artist = "Muse",
                                album = "Black Holes",
                                durationSec = 240,
                                rawLyrics = "[00:12.30]Far away\n[00:16.50]This ship is taking me far away",
                                isSynced = true
                            )
                        )
                    )
                    "Plain Only Track" -> Result.success(
                        listOf(
                            OnlineLyricsCandidate(
                                id = 201L,
                                title = "Plain Only Track",
                                artist = "Artist",
                                album = "Album",
                                durationSec = 190,
                                rawLyrics = "Unsynced verse one\nUnsynced verse two",
                                isSynced = false
                            )
                        )
                    )
                    else -> Result.success(emptyList())
                }
            }
        }

        val repository = MusicRepository(
            context = context,
            scanner = DeviceMusicScanner(context),
            playlistDao = db.playlistDao(),
            importedTrackDao = db.importedTrackDao(),
            trackStatsDao = db.trackStatsDao(),
            trackTagOverrideDao = db.trackTagOverrideDao(),
            excludedFolderDao = db.excludedFolderDao(),
            trackLyricsDao = db.trackLyricsDao(),
            onlineLyricsFetcher = fakeFetcher,
            trackLyricsTranslationDao = db.trackLyricsTranslationDao()
        )

        val coordinator = LibraryEnrichmentCoordinator(
            context = context,
            musicApiService = MusicApiService(),
            repository = repository,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            onRefreshTracks = {},
            onSnackbar = {}
        )

        val tracks = listOf(
            sampleTrack(id = "t_already_synced", title = "Existing", artist = "Band", durationMs = 200_000L),
            sampleTrack(id = "t_needs_lrc", title = "Starlight", artist = "Muse", durationMs = 240_000L),
            sampleTrack(id = "t_plain_only", title = "Plain Only Track", artist = "Artist", durationMs = 190_000L)
        )

        coordinator.openBatchLyrics(tracks)
        assertTrue(coordinator.batchLyricsState.value.syncedOnly)
        coordinator.startBatchLyrics()

        val deadline = System.currentTimeMillis() + 5_000L
        while (!coordinator.batchLyricsState.value.completed && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        val finalState = coordinator.batchLyricsState.value
        assertTrue(finalState.completed)
        assertEquals(3, finalState.processedCount)
        assertEquals(1, finalState.syncedDownloadedCount)
        assertEquals(0, finalState.plainDownloadedCount)
        assertEquals(1, finalState.skippedAlreadySyncedCount)
        assertEquals(1, finalState.notFoundCount)
        assertEquals(1, coordinator.downloadedLrcExportCount.value)

        val persisted = db.trackLyricsDao().getLyricsForTrack("t_needs_lrc")
        assertNotNull(persisted)
        assertTrue(persisted!!.rawLrcText.contains("[00:12.30]Far away"))
        assertNull(db.trackLyricsDao().getLyricsForTrack("t_plain_only"))
    }

    @Test
    fun `tagEditorBottomSheet autocompletes metadata and HD cover from public Deezer suggestion`() {
        val track = sampleTrack(
            id = "tag_1",
            title = "track_01",
            artist = "Artista desconocido",
            album = "Álbum desconocido",
            year = 0,
            trackNumber = 0
        )
        var savedTitle: String? = null
        var savedArtist: String? = null
        var savedAlbum: String? = null
        var savedYear: Int? = null
        var savedTrackNum: Int? = null
        var savedArtwork: String? = null

        val suggestion = DeezerTagSuggestion(
            deezerTrackId = 777L,
            title = "Around the World",
            artist = "Daft Punk",
            album = "Homework",
            albumId = 55L,
            coverXlUrl = "https://cdn-images.dzcdn.net/images/cover/xyz/1000x1000.jpg",
            durationSec = 429
        )
        val detailedSuggestion = suggestion.copy(
            albumArtist = "Daft Punk",
            genre = "Electronic",
            year = 1997,
            trackNumber = 7
        )

        composeTestRule.setContent {
            SonoraTheme {
                TagEditorBottomSheet(
                    track = track,
                    pickedArtworkUri = null,
                    onPickCoverImage = {},
                    onClearCustomCover = {},
                    onSave = { _, title, artist, album, _, _, year, trackNumber, customArtworkUri ->
                        savedTitle = title
                        savedArtist = artist
                        savedAlbum = album
                        savedYear = year
                        savedTrackNum = trackNumber
                        savedArtwork = customArtworkUri
                    },
                    onDismiss = {},
                    onSearchDeezerTags = { _, _ -> Result.success(listOf(suggestion)) },
                    onResolveDeezerTagSuggestion = {
                        detailedSuggestion to "file:///data/user/0/com.example/files/custom_covers/deezer_55.jpg"
                    }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tag_editor_deezer_autofill_button")
            .performScrollTo()
            .performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tag_editor_deezer_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tag_editor_deezer_result_0")
            .performScrollTo()
            .performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tag_editor_deezer_applied_banner")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("tag_title_input").performScrollTo().assertTextContains("Around the World")
        composeTestRule.onNodeWithTag("tag_artist_input").performScrollTo().assertTextContains("Daft Punk")
        composeTestRule.onNodeWithTag("tag_album_input").performScrollTo().assertTextContains("Homework")

        composeTestRule.onNodeWithTag("save_tags_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("Around the World", savedTitle)
        assertEquals("Daft Punk", savedArtist)
        assertEquals("Homework", savedAlbum)
        assertEquals(1997, savedYear)
        assertEquals(7, savedTrackNum)
        assertEquals("file:///data/user/0/com.example/files/custom_covers/deezer_55.jpg", savedArtwork)
    }

    @Test
    fun `batchAutoTagSheet renders options and triggers start`() {
        var autoTagStarted = false
        val tracks = listOf(sampleTrack(id = "b1", title = "Track A", artist = "Artist A"))

        composeTestRule.setContent {
            SonoraTheme {
                BatchAutoTagSheet(
                    uiState = BatchAutoTagUiState(
                        isVisible = true,
                        onlyMissingMetadata = true,
                        overwriteArtwork = true,
                        targetTracks = tracks
                    ),
                    onToggleOnlyMissing = {},
                    onToggleOverwriteArtwork = {},
                    onStart = { autoTagStarted = true },
                    onCancel = {},
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("batch_autotag_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("batch_autotag_only_missing_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("batch_autotag_artwork_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("batch_autotag_start_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(autoTagStarted)
    }

    @Test
    fun `batchLyricsSheet renders completion progress and export folder action`() {
        var lyricsStarted = false
        var exportClicked = false
        val tracks = listOf(sampleTrack(id = "b1", title = "Track A", artist = "Artist A"))

        composeTestRule.setContent {
            SonoraTheme {
                BatchLyricsSheet(
                    uiState = BatchLyricsUiState(
                        isVisible = true,
                        syncedOnly = true,
                        overwriteExisting = false,
                        targetTracks = tracks,
                        completed = true,
                        totalCount = 1,
                        processedCount = 1,
                        syncedDownloadedCount = 1,
                        recentResults = listOf(
                            BatchItemOutcome("Track A", ".lrc sincronizada", true)
                        )
                    ),
                    hasDownloadedExportableLyrics = true,
                    onToggleSyncedOnly = {},
                    onToggleOverwrite = {},
                    onStart = { lyricsStarted = true },
                    onCancel = {},
                    onExportToFolder = { exportClicked = true },
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("batch_lyrics_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("batch_lyrics_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("batch_lyrics_export_folder_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(exportClicked)
        composeTestRule.onNodeWithTag("batch_lyrics_done_button").assertIsDisplayed()
        assertFalse(lyricsStarted)
    }

    @Test
    fun `settingsScreen triggers batch deezer autotag and batch lrc lyrics`() {
        var settingsAutoTagClicked = false
        var settingsLyricsClicked = false

        composeTestRule.setContent {
            SonoraTheme {
                SettingsScreen(
                    totalTracksCount = 24,
                    isScanning = false,
                    excludedFoldersCount = 0,
                    sdCardFoldersCount = 0,
                    audioSettings = AudioSettings(),
                    isSleepTimerActive = false,
                    preferredTranslationEngine = TranslationEngine.GOOGLE,
                    onBack = {},
                    onRefreshLibrary = {},
                    onImportFiles = {},
                    onOpenFoldersManager = {},
                    onAddSdCardFolder = {},
                    onOpenEqualizer = {},
                    onOpenSleepTimer = {},
                    onUpdateAudioSettings = {},
                    onSelectTranslationEngine = {},
                    onOpenAboutAndLegal = {},
                    bottomContentPadding = PaddingValues(0.dp),
                    onOpenBatchAutoTag = { settingsAutoTagClicked = true },
                    onOpenBatchLyrics = { settingsLyricsClicked = true }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_batch_autotag_row"))
        composeTestRule.onNodeWithTag("settings_batch_autotag_row").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_batch_lyrics_row"))
        composeTestRule.onNodeWithTag("settings_batch_lyrics_row").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertTrue(settingsAutoTagClicked)
        assertTrue(settingsLyricsClicked)
    }

    @Test
    fun `libraryScreen selection menu triggers batch deezer autotag and batch lrc lyrics`() {
        val tracks = listOf(
            sampleTrack(id = "sel_1", title = "Veridis Quo", artist = "Daft Punk")
        )
        var batchAutoTaggedCount = 0
        var batchLyricsCount = 0

        composeTestRule.setContent {
            SonoraTheme {
                LibraryScreen(
                    uiState = SonoraUiState(
                        allTracks = tracks,
                        filteredTracks = tracks,
                        hasAudioPermission = true
                    ),
                    playbackState = PlaybackUiState(),
                    onRequestPermission = {},
                    onImportFiles = {},
                    onSelectSortOrder = {},
                    onPlayTrack = { _, _ -> },
                    onShuffleAll = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    bottomContentPadding = PaddingValues(0.dp),
                    onBatchAutoTagTracks = { batchAutoTaggedCount = it.size },
                    onBatchDownloadLyrics = { batchLyricsCount = it.size }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("library_select_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selection_select_all_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("selection_more_actions_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selection_batch_autotag_item").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, batchAutoTaggedCount)

        // Re-enter selection mode for batch lyrics check
        composeTestRule.onNodeWithTag("library_select_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selection_select_all_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selection_more_actions_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selection_batch_lyrics_item").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, batchLyricsCount)
    }

    private fun sampleTrack(
        id: String,
        title: String,
        artist: String,
        album: String = "Discovery",
        albumArtUri: String? = null,
        durationMs: Long = 240_000L,
        year: Int = 2001,
        trackNumber: Int = 1
    ): AudioTrack {
        return AudioTrack(
            id = id,
            contentUri = "content://media/external/audio/media/42",
            title = title,
            artist = artist,
            album = album,
            albumId = 42L,
            albumArtUri = albumArtUri,
            durationMs = durationMs,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 25_000_000L,
            dateAddedSec = 1000L,
            folderName = "Music",
            year = year,
            trackNumber = trackNumber
        )
    }
}
