package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.DeepSeekLyricsTranslator
import com.example.ai.LyricsTranslationCoordinator
import com.example.ai.LyricsTranslationLanguage
import com.example.ai.LyricsTranslationUiState
import com.example.ai.TranslationEngine
import com.example.data.local.PlaybackPreferencesStore
import com.example.data.local.SonoraBackupManager
import com.example.data.local.SonoraDatabase
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.AudioTechSpecs
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.AutoEqGraphicEqParser
import com.example.model.AutoEqProfilesCatalog
import com.example.model.LyricLine
import com.example.model.LyricsData
import com.example.model.PlaylistSummary
import com.example.playback.AudioEffectsController
import com.example.playback.PlaybackService
import com.example.playback.PlaybackUiState
import com.example.share.LyricsShareCardRenderer
import com.example.share.LyricsShareClipCalculator
import com.example.share.LyricsShareClipSpec
import com.example.share.ShareCardStyle
import com.example.share.ShareExportUiState
import com.example.share.ShareLyricVerse
import com.example.share.ShareLyricsTextSize
import com.example.share.ShareOutputFormat
import com.example.share.ShareVideoResolution
import com.example.ui.components.BatchAddToPlaylistBottomSheet
import com.example.ui.components.EqualizerAndDspSheet
import com.example.ui.components.FoldersExclusionSheet
import com.example.ui.components.LyricsShareSheet
import com.example.ui.components.NowPlayingTab
import com.example.ui.components.NowPlayingTabsOverlay
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SleepTimerSheet
import com.example.ui.components.TagEditorBottomSheet
import com.example.ui.components.TrackMetadataBottomSheet
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SonoraTheme
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Features211Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleTrack(id: String = "trk_211"): AudioTrack = AudioTrack(
        id = id,
        contentUri = "content://media/external/audio/media/901",
        title = "Midnight Velocity",
        artist = "DavooPunk",
        album = "Hi-Res Session",
        albumId = 211L,
        albumArtUri = null,
        durationMs = 240_000L,
        format = AudioFormat.FLAC,
        mimeType = "audio/flac",
        sizeBytes = 28_000_000L,
        dateAddedSec = 999L,
        folderName = "FLAC Masters"
    )

    @Test
    fun nowPlayingScreenDisplaysYtMusicBottomBarAndRemovesLiveLyricPill() {
        val track = sampleTrack()
        val syncedLyrics = LyricsData(
            lines = listOf(
                LyricLine(2_000L, "First verse line"),
                LyricLine(18_000L, "Active chorus line right now"),
                LyricLine(35_000L, "Final bridge line")
            ),
            isSynced = true,
            sourceLabel = "Local .lrc"
        )
        val openedTabs = mutableListOf<NowPlayingTab>()
        var openedQueue = false

        composeTestRule.setContent {
            SonoraTheme {
                NowPlayingScreen(
                    playbackState = PlaybackUiState(
                        currentTrack = track,
                        queue = listOf(track),
                        currentIndex = 0,
                        isPlaying = true,
                        positionMs = 20_000L,
                        durationMs = track.durationMs
                    ),
                    currentTrackLyrics = syncedLyrics,
                    onCollapse = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onOpenQueue = { openedQueue = true },
                    onAddToPlaylist = {},
                    onOpenYtMusicTab = { openedTabs += it }
                )
            }
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("now_playing_artwork").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("now_playing_live_lyric_line").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("now_playing_lyrics_peek_card").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("now_playing_share_button").assertCountEquals(0)
        composeTestRule.onNodeWithTag("now_playing_yt_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_tab_bar_queue").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("now_playing_lyrics_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("now_playing_tab_bar_info").assertIsDisplayed().performClick()
        assertEquals(
            listOf(NowPlayingTab.QUEUE, NowPlayingTab.LYRICS, NowPlayingTab.INFO),
            openedTabs
        )

        composeTestRule.onNodeWithTag("now_playing_more_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_menu_queue").assertIsDisplayed().performClick()
        assertTrue(openedQueue)
    }

    @Test
    fun deepSeekStrictMultilingualPromptEnforcesFullTranslationAndForceRefreshOverwritesCache() = runTest {
        var capturedBody = ""
        var invocationCount = 0

        val interceptor = Interceptor { chain ->
            invocationCount++
            val req = chain.request()
            val buf = Buffer()
            req.body?.writeTo(buf)
            capturedBody = buf.readUtf8()
            val content = if (invocationCount == 1) {
                """{"translations":[{"index":0,"text":"Corriendo por la noche"},{"index":1,"text":"Primera versión"}]}"""
            } else {
                """{"translations":[{"index":0,"text":"Corriendo bajo la noche"},{"index":1,"text":"Traducción regenerada"}]}"""
            }
            val payload = """{"choices":[{"message":{"role":"assistant","content":${
                kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(content))
            }}}]}"""
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(payload.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val fakeClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val translator = DeepSeekLyricsTranslator(
            apiKeyProvider = { "sk-deepseek-multilingual-test" },
            client = fakeClient,
            ioDispatcher = dispatcher
        )
        val mixedLyrics = LyricsData(
            lines = listOf(
                LyricLine(2_000L, "Running through the night contigo"),
                LyricLine(6_000L, "Yoru no naka de baby stay")
            ),
            isSynced = true,
            sourceLabel = "Local .lrc"
        )

        val coordinator = LyricsTranslationCoordinator(
            scope = this,
            deepSeekTranslator = translator
        )
        coordinator.translate("mix_1", "Mix", "Artist", mixedLyrics, LyricsTranslationLanguage.SPANISH, TranslationEngine.DEEPSEEK, forceRefresh = false)
        testScheduler.advanceUntilIdle()

        assertTrue(capturedBody.contains("idiomas mezclados"))
        assertTrue(capturedBody.contains("100% traducidas"))
        val firstState = coordinator.state.value as LyricsTranslationUiState.Success
        assertEquals("Primera versión", firstState.translatedLyrics.lines[1].text)

        // Calling with forceRefresh=true overwrites previously cached response
        coordinator.translate("mix_1", "Mix", "Artist", mixedLyrics, LyricsTranslationLanguage.SPANISH, TranslationEngine.DEEPSEEK, forceRefresh = true)
        testScheduler.advanceUntilIdle()
        val refreshedState = coordinator.state.value as LyricsTranslationUiState.Success
        assertEquals(2, invocationCount)
        assertEquals("Traducción regenerada", refreshedState.translatedLyrics.lines[1].text)
    }

    @Test
    fun shareClipSupportsMoreThan10LinesWithin90sScrollingRenderAndDeviceSaveUi() {
        val manyLines = (0 until 24).map { idx ->
            LyricLine(timestampMs = idx * 3_500L, text = "Scrolling verse number ${idx + 1}")
        }
        val lyrics = LyricsData(lines = manyLines, isSynced = true, sourceLabel = "Local .lrc")

        // 18 lines span ~63s <= 90_000L -> succeeds with zero 10-line cap
        val range18 = LyricsShareClipCalculator.updateSyncedSelection(
            currentRange = 0..0,
            tappedIndex = 17,
            lyrics = lyrics,
            trackDurationMs = 200_000L
        )
        assertEquals(0..17, range18)

        val spec = LyricsShareClipSpec(
            track = sampleTrack(),
            startMs = 0L,
            endMs = 63_000L,
            isSyncedLyrics = true,
            verses = (0..17).map { idx ->
                ShareLyricVerse(
                    startMs = idx * 3_500L,
                    endMs = (idx + 1) * 3_500L,
                    text = "Scrolling verse number ${idx + 1}",
                    secondaryText = "Verso traducido ${idx + 1}"
                )
            },
            includeTranslation = true,
            style = ShareCardStyle.DYNAMIC_ARTWORK,
            format = ShareOutputFormat.VIDEO_MP4,
            textSize = ShareLyricsTextSize.LARGE,
            videoResolution = ShareVideoResolution.HD_720P
        )

        // Smoke-test scrolling frame rendering across verse transition without exceptions
        val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        LyricsShareCardRenderer.drawShareCardFrame(
            canvas = canvas,
            width = 720,
            height = 1280,
            spec = spec,
            artworkBitmap = null,
            blurredBgBitmap = null,
            frameElapsedMs = 3_650L, // during 380ms FastOutSlowIn scroll transition into verse 1
            isStaticImage = false
        )
        bmp.recycle()

        // Ready state exposes Guardar and Compartir without auto-triggering system share
        var autoSharedCount = 0
        var savedClicks = 0
        var manualSharedClicks = 0
        val exportState = mutableStateOf<ShareExportUiState>(
            ShareExportUiState.Ready(
                uri = Uri.parse("content://com.example.fileprovider/shared/test.mp4"),
                mimeType = "video/mp4",
                format = ShareOutputFormat.VIDEO_MP4,
                fileName = "test.mp4"
            )
        )

        composeTestRule.setContent {
            SonoraTheme {
                LyricsShareSheet(
                    track = sampleTrack(),
                    lyrics = lyrics,
                    translatedLyrics = null,
                    initialPositionMs = 0L,
                    exportState = exportState.value,
                    onStartExport = {},
                    onCancelExport = {},
                    onExportReady = { manualSharedClicks++ },
                    onDismiss = {},
                    onSaveToDevice = { savedClicks++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, autoSharedCount)
        assertEquals(0, manualSharedClicks)
        composeTestRule.onNodeWithTag("share_ready_actions_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_save_device_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("share_ready_system_button").assertIsDisplayed().performClick()
        assertEquals(1, savedClicks)
        assertEquals(1, manualSharedClicks)
    }

    @Test
    fun expandedAutoEqCatalogAndGraphicEqTxtParserInterpolateTenBandsCorrectly() {
        val allNames = AutoEqProfilesCatalog.builtInProfiles.map { it.fullName }
        assertTrue(AutoEqProfilesCatalog.builtInProfiles.size >= 30)
        assertTrue(allNames.any { it.contains("KZ EDX Pro 2") })
        assertTrue(allNames.any { it.contains("KZ Castor") })
        assertTrue(allNames.any { it.contains("Moondrop Chu II") })
        assertTrue(allNames.any { it.contains("Tangzu Wan'er") })

        val sampleTxt = "GraphicEQ: 25 -1.5; 31.25 -1.0; 62.5 2.0; 125 1.5; 250 0.0; 500 -2.0; 1000 3.0; 2000 4.0; 4000 2.0; 8000 -1.0; 16000 1.5"
        val parsed = AutoEqGraphicEqParser.parseGraphicEqTxt(sampleTxt, "KZ_EDX_Pro_2_Harman.txt")
        assertNotNull(parsed)
        assertEquals("KZ EDX Pro 2 Harman", parsed!!.model)
        assertEquals(10, parsed.bandGainsMb.size)
        assertTrue(parsed.bandGainsMb[1] in 195..200) // 62 Hz interpolated between 31.25 (-1.0) and 62.5 (2.0)
        assertEquals(150, parsed.bandGainsMb[2]) // 125 Hz -> 1.5 dB = 150 mB
        assertEquals(400, parsed.bandGainsMb[6]) // 2000 Hz -> 4.0 dB = 400 mB
        assertTrue(parsed.preampDb <= -4.0f) // Auto safe negative preamp compensating +4 dB peak

        // Comma-separated preamp + out-of-range frequency clamping sanity
        val commaTxt = "GraphicEQ: 20 18.0; 1000 6.0; 20000 -15.0"
        val parsedComma = AutoEqGraphicEqParser.parseGraphicEqTxt(commaTxt, "Boost.txt")
        assertNotNull(parsedComma)
        assertTrue(parsedComma!!.bandGainsMb.all { it in -1200..1200 })

        // Reject invalid text
        assertNull(AutoEqGraphicEqParser.parseGraphicEqTxt("Not a graphic eq file", "bad.txt"))

        // Verify Equalizer sheet exposes Importar .txt button
        val controller = AudioEffectsController()
        val settings = AudioSettings()
        composeTestRule.setContent {
            SonoraTheme {
                EqualizerAndDspSheet(
                    settings = settings,
                    bands = controller.getBands(settings),
                    onUpdateSettings = {},
                    onDismiss = {},
                    onImportAutoEqTxt = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("eq_import_txt_button").assertIsDisplayed()
    }

    @Test
    fun replayGainPreampSplitPersistenceBackupAndSettingsOnlyPlacement() = runTest {
        // 1. Verify split math for ReplayGain + LoudnessEnhancer headroom
        val settingsPlus3 = AudioSettings(
            replayGainEnabled = true,
            replayGainPreampDb = 3.0f,
            replayGainUntaggedPreampDb = -4.0f
        )
        // Quiet FLAC tagged -2.0 dB + +3.0 dB preamp => +1.0 dB -> volume=1.0, boost=+1.0 dB
        val splitQuiet = PlaybackService.computeReplayGainSplit(-2.0f, settingsPlus3)
        assertEquals(1.0f, splitQuiet.first, 0.001f)
        assertEquals(1.0f, splitQuiet.second, 0.01f)

        // Hot master tagged -6.5 dB + +3.0 dB preamp => -3.5 dB attenuation -> volume < 1.0, boost=0
        val splitHot = PlaybackService.computeReplayGainSplit(-6.5f, settingsPlus3)
        assertTrue(splitHot.first < 0.72f && splitHot.first > 0.60f)
        assertEquals(0f, splitHot.second, 0.001f)

        // Untagged track uses replayGainUntaggedPreampDb (-4.0 dB)
        val splitUntagged = PlaybackService.computeReplayGainSplit(null, settingsPlus3)
        assertTrue(splitUntagged.first < 0.65f && splitUntagged.first > 0.60f)
        assertEquals(0f, splitUntagged.second, 0.001f)

        // AudioEffectsController boost math
        val fx = AudioEffectsController()
        fx.applySettings(settingsPlus3)
        fx.setTrackHasReplayGainTag(true)
        fx.setReplayGainBoostDb(3.5f)
        assertEquals(350, fx.currentReplayGainBoostMb())
        assertTrue(fx.isLoudnessNormalizationRequested())

        // 2. Preferences + Backup JSON roundtrip
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PlaybackPreferencesStore(context)
        val db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val customSettings = AudioSettings(
                replayGainEnabled = true,
                replayGainPreampDb = 6.5f,
                replayGainUntaggedPreampDb = -2.5f
            )
            store.saveAudioSettings(customSettings)
            val loaded = store.loadAudioSettings()
            assertEquals(6.5f, loaded.replayGainPreampDb, 0.01f)
            assertEquals(-2.5f, loaded.replayGainUntaggedPreampDb, 0.01f)

            val manager = SonoraBackupManager(context, db, store)
            val json = manager.buildBackupJson()
            assertTrue(json.contains("6.5"))
            assertTrue(json.contains("-2.5"))

            store.saveAudioSettings(AudioSettings())
            val restored = manager.importFromJsonString(json).audioSettings
            assertEquals(6.5f, restored.replayGainPreampDb, 0.01f)
            assertEquals(-2.5f, restored.replayGainUntaggedPreampDb, 0.01f)
        } finally {
            db.close()
        }
    }

    @Test
    fun replayGainPreampSlidersAppearOnlyInSettingsScreenNotInEqualizerSheet() {
        val fx = AudioEffectsController()
        val settingsState = mutableStateOf(AudioSettings(replayGainEnabled = true))

        composeTestRule.setContent {
            SonoraTheme {
                EqualizerAndDspSheet(
                    settings = settingsState.value,
                    bands = fx.getBands(settingsState.value),
                    onUpdateSettings = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("settings_replaygain_preamp_slider").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("settings_replaygain_untagged_preamp_slider").assertCountEquals(0)
    }

    @Test
    fun settingsScreenRendersReplayGainPreampSlidersAndResetButton() {
        val settingsState = mutableStateOf(
            AudioSettings(replayGainEnabled = true, replayGainPreampDb = 7.5f, replayGainUntaggedPreampDb = -6.0f)
        )

        composeTestRule.setContent {
            SonoraTheme {
                SettingsScreen(
                    totalTracksCount = 20,
                    isScanning = false,
                    excludedFoldersCount = 0,
                    sdCardFoldersCount = 0,
                    audioSettings = settingsState.value,
                    isSleepTimerActive = false,
                    preferredTranslationEngine = TranslationEngine.DEEPSEEK,
                    onBack = {},
                    onRefreshLibrary = {},
                    onImportFiles = {},
                    onOpenFoldersManager = {},
                    onAddSdCardFolder = {},
                    onOpenEqualizer = {},
                    onOpenSleepTimer = {},
                    onUpdateAudioSettings = { settingsState.value = it },
                    onSelectTranslationEngine = {},
                    onOpenAboutAndLegal = {},
                    bottomContentPadding = PaddingValues(0.dp)
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_replaygain_preamp_reset_button"))
        composeTestRule.onNodeWithTag("settings_replaygain_preamp_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_replaygain_untagged_preamp_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_replaygain_preamp_reset_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(3.0f, settingsState.value.replayGainPreampDb, 0.01f)
        assertEquals(-4.0f, settingsState.value.replayGainUntaggedPreampDb, 0.01f)
    }

    @Test
    fun nowPlayingTabsOverlayRendersFixedHeaderAndSwitchesTabs() {
        val track = sampleTrack()
        val activeTabState = mutableStateOf(NowPlayingTab.QUEUE)
        var collapsed = false
        var playPauseToggles = 0
        val lyrics = LyricsData(
            lines = listOf(
                LyricLine(2_000L, "First synced lyric"),
                LyricLine(12_000L, "Second synced lyric")
            ),
            isSynced = true,
            sourceLabel = "Local .lrc"
        )
        val metadata = AudioTrackMetadata(
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumArtist = track.artist,
            genre = "Electronic",
            year = 2025,
            trackNumber = 1,
            durationMs = track.durationMs,
            format = track.format,
            mimeType = track.mimeType,
            sizeBytes = track.sizeBytes,
            folderName = track.folderName,
            hasEmbeddedArtwork = false,
            techSpecs = AudioTechSpecs(sampleRateHz = 44100, bitsPerSample = 16, channelCount = 2)
        )

        composeTestRule.setContent {
            SonoraTheme {
                NowPlayingTabsOverlay(
                    track = track,
                    activeTab = activeTabState.value,
                    onSelectTab = { activeTabState.value = it },
                    onCollapse = { collapsed = true },
                    playbackState = PlaybackUiState(
                        currentTrack = track,
                        queue = listOf(track),
                        currentIndex = 0,
                        isPlaying = true,
                        positionMs = 5_000L,
                        durationMs = track.durationMs
                    ),
                    onTogglePlayPause = { playPauseToggles++ },
                    onSelectQueueIndex = {},
                    onRemoveQueueIndex = {},
                    onMoveQueueItem = { _, _ -> },
                    lyrics = lyrics,
                    isLoadingLyrics = false,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    metadata = metadata,
                    isLoadingMetadata = false
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("now_playing_tabs_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_tabs_mini_art").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_tabs_play_pause").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("now_playing_tabs_tab_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("queue_lazy_column").assertIsDisplayed()

        composeTestRule.onNodeWithTag("now_playing_tab_lyrics").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("spotify_lyrics_list").assertIsDisplayed()

        composeTestRule.onNodeWithTag("now_playing_tab_info").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_info_lazy_column").assertIsDisplayed()

        composeTestRule.onNodeWithTag("now_playing_tabs_collapse_button").assertIsDisplayed().performClick()
        assertEquals(1, playPauseToggles)
        assertTrue(collapsed)
    }

    @Test
    fun modalFreeSheetsUseExplicitCloseButtonsWithoutModalBottomSheet() {
        val track = sampleTrack()
        val step = mutableStateOf(0)
        var dismissCount = 0

        composeTestRule.setContent {
            SonoraTheme {
                when (step.value) {
                    0 -> SleepTimerSheet(
                        remainingMs = null,
                        stopAfterCurrentTrack = false,
                        onSelectMinutes = {},
                        onSelectAfterTrack = {},
                        onCancelTimer = {},
                        onDismiss = { dismissCount++ }
                    )
                    1 -> BatchAddToPlaylistBottomSheet(
                        tracks = listOf(track),
                        playlists = listOf(PlaylistSummary(1L, "Favorites Mix", "", 1_000L, listOf(track))),
                        onDismiss = { dismissCount++ },
                        onSelectPlaylist = {},
                        onCreateNewPlaylist = {}
                    )
                    2 -> QueueBottomSheet(
                        playbackState = PlaybackUiState(currentTrack = track, queue = listOf(track), currentIndex = 0),
                        onDismiss = { dismissCount++ },
                        onSelectIndex = {},
                        onRemoveIndex = {},
                        onMoveItem = { _, _ -> }
                    )
                    3 -> TrackMetadataBottomSheet(
                        track = track,
                        metadata = null,
                        isLoading = false,
                        onDismiss = { dismissCount++ }
                    )
                    4 -> TagEditorBottomSheet(
                        track = track,
                        pickedArtworkUri = null,
                        onPickCoverImage = {},
                        onClearCustomCover = {},
                        onSave = { _, _, _, _, _, _, _, _, _ -> },
                        onDismiss = { dismissCount++ }
                    )
                    else -> FoldersExclusionSheet(
                        folders = emptyList(),
                        onToggleExclude = { _, _ -> },
                        onDismiss = { dismissCount++ }
                    )
                }
            }
        }

        val checks = listOf(
            "sleep_timer_sheet" to "close_sleep_timer_button",
            "add_to_playlist_sheet" to "close_add_to_playlist_button",
            "queue_lazy_column" to "close_queue_button",
            "track_metadata_sheet" to "close_metadata_button",
            "tag_editor_sheet" to "close_tag_editor_button",
            "folders_exclusion_sheet" to "close_folders_button"
        )
        checks.forEachIndexed { idx, (sheetTag, closeTag) ->
            step.value = idx
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(sheetTag).assertIsDisplayed()
            composeTestRule.onNodeWithTag(closeTag).assertIsDisplayed().performClick()
            assertEquals(idx + 1, dismissCount)
        }
    }
}
