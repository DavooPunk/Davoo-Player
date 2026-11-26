package com.example

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat as AndroidAudioFormat
import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.DeepSeekConfigurationException
import com.example.ai.DeepSeekLyricsTranslator
import com.example.ai.DeepSeekTestUiState
import com.example.ai.LyricsTranslationCoordinator
import com.example.ai.LyricsTranslationLanguage
import com.example.ai.LyricsTranslationNetworkException
import com.example.ai.LyricsTranslationUiState
import com.example.ai.TranslationEngine
import com.example.data.local.PlaybackPreferencesStore
import com.example.data.local.SonoraBackupManager
import com.example.data.local.SonoraDatabase
import com.example.data.repository.AudioTagParsers
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import com.example.model.AudioFormat
import com.example.model.AudioSettings
import com.example.model.AudioTrack
import com.example.model.FolderGroup
import com.example.model.LyricLine
import com.example.model.LyricsData
import com.example.model.SortOrder
import com.example.playback.AudioEffectsController
import com.example.playback.AudioOutputMonitor
import com.example.playback.PlaybackService
import com.example.playback.RawOutputDeviceCandidate
import com.example.playback.ReplayGainScanner
import com.example.share.LyricsShareClipCalculator
import com.example.share.LyricsShareClipSpec
import com.example.share.ShareCardStyle
import com.example.share.ShareLyricsTextSize
import com.example.share.ShareOutputFormat
import com.example.share.ShareVideoResolution
import com.example.ui.components.EqualizerAndDspSheet
import com.example.ui.components.LyricsBottomSheet
import com.example.ui.components.LyricsShareSheet
import com.example.ui.screens.FolderBrowserTree
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SonoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Features210Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sortOrderAndReplayGainCachePersistCleanlyInPreferencesStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PlaybackPreferencesStore(context)

        // Default sort order is RECENT
        assertEquals(SortOrder.RECENT, store.loadLibrarySortOrder())

        // Persisting TITLE and ARTIST survives re-read
        store.saveLibrarySortOrder(SortOrder.TITLE)
        assertEquals(SortOrder.TITLE, store.loadLibrarySortOrder())
        store.saveLibrarySortOrder(SortOrder.DURATION)
        assertEquals(SortOrder.DURATION, store.loadLibrarySortOrder())

        // Computed ReplayGain batch persistence and clamping
        store.clearComputedReplayGains()
        store.mergeComputedReplayGains(mapOf("t1" to -5.25f, "t2" to 24.0f, "t3" to Float.NaN))
        val map = store.loadComputedReplayGainMap()
        assertEquals(2, map.size)
        assertEquals(-5.25f, store.loadComputedReplayGain("t1"))
        assertEquals(12.0f, store.loadComputedReplayGain("t2"))
        assertNull(store.loadComputedReplayGain("t3"))
    }

    @Test
    fun backupJsonPreservesFadeOnPauseAndLibrarySortOrder() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PlaybackPreferencesStore(context)
        val db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            store.saveAudioSettings(AudioSettings(fadeOnPauseEnabled = true, replayGainEnabled = true))
            store.saveLibrarySortOrder(SortOrder.ARTIST)

            val manager = SonoraBackupManager(context, db, store)
            val backupJson = manager.buildBackupJson()
            assertTrue(backupJson.contains("fadeOnPauseEnabled"))
            assertTrue(backupJson.contains("ARTIST"))

            // Reset local prefs and verify import restores both fields
            store.saveAudioSettings(AudioSettings(fadeOnPauseEnabled = false))
            store.saveLibrarySortOrder(SortOrder.RECENT)
            val result = manager.importFromJsonString(backupJson)
            assertTrue(result.audioSettings.fadeOnPauseEnabled)
            assertTrue(store.loadAudioSettings().fadeOnPauseEnabled)
            assertEquals(SortOrder.ARTIST, store.loadLibrarySortOrder())
        } finally {
            db.close()
        }
    }

    @Test
    fun playbackServiceBundleParsesFadeOnPauseFlag() {
        val bundleEnabled = Bundle().apply {
            putBoolean(PlaybackService.ARG_EQ_ENABLED, true)
            putBoolean(PlaybackService.ARG_FADE_ON_PAUSE, true)
        }
        val parsedOn = PlaybackService.audioSettingsFromBundle(bundleEnabled)
        assertNotNull(parsedOn)
        assertTrue(parsedOn!!.fadeOnPauseEnabled)

        val bundleDefault = Bundle().apply {
            putBoolean(PlaybackService.ARG_EQ_ENABLED, false)
        }
        val parsedOff = PlaybackService.audioSettingsFromBundle(bundleDefault)
        assertNotNull(parsedOff)
        assertFalse(parsedOff!!.fadeOnPauseEnabled)

        assertNull(PlaybackService.audioSettingsFromBundle(Bundle()))
    }

    @Test
    fun replayGainScannerComputesAccurateDbFromRms() {
        // -18 dBFS amplitude (~0.12589) should yield ~0.0 dB gain
        val refGain = ReplayGainScanner.computeReplayGainDbFromRms(0.1258925)
        assertNotNull(refGain)
        assertEquals(0.0f, refGain!!, 0.05f)

        // Loud -8 dBFS signal (~0.3981) should yield ~-10.0 dB attenuation
        val loudGain = ReplayGainScanner.computeReplayGainDbFromRms(0.398107)
        assertNotNull(loudGain)
        assertEquals(-10.0f, loudGain!!, 0.1f)

        // Silence returns null cleanly
        assertNull(ReplayGainScanner.computeReplayGainDbFromRms(0.0))
        assertNull(ReplayGainScanner.computeReplayGainDbFromRms(Double.NaN))
    }

    @Test
    fun audioOutputMonitorPrioritizesUsbDacAndFormatsCapabilities() {
        val selected = AudioOutputMonitor.selectPrimaryAudioOutput(
            listOf(
                RawOutputDeviceCandidate(
                    type = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    productName = "Phone Speaker",
                    sampleRates = listOf(48_000),
                    encodings = listOf(AndroidAudioFormat.ENCODING_PCM_16BIT)
                ),
                RawOutputDeviceCandidate(
                    type = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    productName = "WH-1000XM5",
                    sampleRates = listOf(44_100, 48_000),
                    encodings = listOf(AndroidAudioFormat.ENCODING_PCM_16BIT)
                ),
                RawOutputDeviceCandidate(
                    type = AudioDeviceInfo.TYPE_USB_DEVICE,
                    productName = "FiiO KA11",
                    sampleRates = listOf(44_100, 96_000, 192_000),
                    encodings = listOf(AndroidAudioFormat.ENCODING_PCM_FLOAT)
                )
            )
        )
        assertTrue(selected.isUsbDac)
        assertTrue(selected.isExternal)
        assertEquals("DAC USB", selected.routeLabel)
        assertEquals("FiiO KA11", selected.deviceName)
        assertEquals(192_000, selected.maxSampleRateHz)
        assertEquals("32-bit Float", selected.maxBitDepthLabel)
        assertTrue(selected.shortBadgeLabel.contains("192 kHz"))
    }

    @Test
    fun folderBrowserTreeBuildsHierarchicalTreeAndDirectTracks() {
        val tRoot = AudioTrack(
            id = "f1", contentUri = "u1", title = "Intro", artist = "A", album = "Al",
            albumId = 1L, albumArtUri = null, durationMs = 120_000L, format = AudioFormat.FLAC,
            mimeType = "audio/flac", sizeBytes = 1000L, dateAddedSec = 10L, folderName = "Music",
            filePath = "/storage/emulated/0/Music/intro.flac"
        )
        val tSub = AudioTrack(
            id = "f2", contentUri = "u2", title = "Live", artist = "A", album = "Al",
            albumId = 1L, albumArtUri = null, durationMs = 200_000L, format = AudioFormat.MP3,
            mimeType = "audio/mpeg", sizeBytes = 1000L, dateAddedSec = 10L, folderName = "Rock",
            filePath = "/storage/emulated/0/Music/Rock/live.mp3"
        )
        val folders = listOf(
            FolderGroup("Music", listOf(tRoot), 120_000L, isExcluded = false),
            FolderGroup("Rock", listOf(tSub), 200_000L, isExcluded = false)
        )

        val rootView = FolderBrowserTree.buildView(folders, emptyList())
        assertEquals(1, rootView.subfolders.size)
        assertEquals("Music", rootView.subfolders.first().name)
        assertEquals(1, rootView.subfolders.first().childFolderCount)
        assertEquals(2, rootView.subfolders.first().totalTracks)
        assertNull(rootView.subfolders.first().directFolderGroupName)

        val musicView = FolderBrowserTree.buildView(folders, listOf("Music"))
        assertEquals(1, musicView.directTracks.size)
        assertEquals("Intro", musicView.directTracks.first().title)
        assertEquals(1, musicView.subfolders.size)
        assertEquals("Rock", musicView.subfolders.first().name)
        assertEquals("Rock", musicView.subfolders.first().directFolderGroupName)
        assertEquals(2, musicView.subtreeTracks.size)

        val rockView = FolderBrowserTree.buildView(folders, listOf("Music", "Rock"))
        assertEquals(0, rockView.subfolders.size)
        assertEquals(1, rockView.directTracks.size)
        assertEquals("Live", rockView.directTracks.first().title)
    }

    @Test
    fun shareModelsExposeTextSize1080pAndSupport90sDuration() {
        assertEquals(90_000L, LyricsShareClipCalculator.MAX_CLIP_DURATION_MS)
        assertEquals(1080, ShareVideoResolution.FHD_1080P.width)
        assertEquals(1920, ShareVideoResolution.FHD_1080P.height)
        assertEquals(720, ShareVideoResolution.HD_720P.width)
        assertTrue(ShareLyricsTextSize.LARGE.multiplier > ShareLyricsTextSize.COMPACT.multiplier)
        assertTrue(ShareLyricsTextSize.EXTRA_LARGE.multiplier > ShareLyricsTextSize.LARGE.multiplier)

        val sampleTrack = AudioTrack(
            id = "s1", contentUri = "", title = "Ten Lines", artist = "Davoo", album = "v2.1",
            albumId = 1L, albumArtUri = null, durationMs = 200_000L, format = AudioFormat.FLAC,
            mimeType = "audio/flac", sizeBytes = 1000L, dateAddedSec = 10L, folderName = "Music"
        )
        val defaultSpec = LyricsShareClipSpec(
            track = sampleTrack,
            startMs = 0L,
            endMs = 15_000L,
            isSyncedLyrics = true,
            verses = emptyList()
        )
        assertEquals(ShareLyricsTextSize.LARGE, defaultSpec.textSize)
        assertEquals(ShareVideoResolution.FHD_1080P, defaultSpec.videoResolution)
        assertEquals(ShareCardStyle.DYNAMIC_ARTWORK, defaultSpec.style)
        assertEquals(ShareOutputFormat.VIDEO_MP4, defaultSpec.format)
    }

    @Test
    fun lyricsShareSheetDisplays10LinesTextSizeAnd1080pResolutionChips() {
        val track = AudioTrack(
            id = "share_ui_210",
            contentUri = "",
            title = "High Res Verse",
            artist = "Davoo",
            album = "v2.1.0",
            albumId = 21L,
            albumArtUri = null,
            durationMs = 210_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 15_000_000L,
            dateAddedSec = 500L,
            folderName = "Music"
        )
        val lyrics = LyricsData(
            lines = (1..10).map { idx -> LyricLine(idx * 2_000L, "Línea de prueba $idx") },
            isSynced = true,
            sourceLabel = "Local .lrc"
        )

        composeTestRule.setContent {
            SonoraTheme {
                LyricsShareSheet(
                    track = track,
                    lyrics = lyrics,
                    translatedLyrics = null,
                    initialPositionMs = 2_000L,
                    onStartExport = {},
                    onCancelExport = {},
                    onExportReady = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("share_text_size_large"))
        composeTestRule.onNodeWithTag("share_text_size_large").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_text_size_extra_large").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("share_resolution_fhd_1080p"))
        composeTestRule.onNodeWithTag("share_resolution_fhd_1080p").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_resolution_hd_720p").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasText("Selecciona versos (máx. 1:30)"))
        composeTestRule.onNodeWithText("Selecciona versos (máx. 1:30)").assertIsDisplayed()
    }

    @Test
    fun settingsScreenExposesReplayGainScannerAndFadeOnPauseWhileEqualizerExcludesThem() {
        val settingsState = mutableStateOf(AudioSettings(fadeOnPauseEnabled = false))
        var scannerOpened = false

        composeTestRule.setContent {
            SonoraTheme {
                SettingsScreen(
                    totalTracksCount = 24,
                    isScanning = false,
                    excludedFoldersCount = 0,
                    sdCardFoldersCount = 0,
                    audioSettings = settingsState.value,
                    isSleepTimerActive = false,
                    preferredTranslationEngine = TranslationEngine.GOOGLE,
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
                    bottomContentPadding = PaddingValues(0.dp),
                    replayGainCachedCount = 7,
                    onOpenReplayGainScanner = { scannerOpened = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_replaygain_scanner_row"))
        composeTestRule.onNodeWithTag("settings_replaygain_scanner_row").assertIsDisplayed().performClick()
        assertTrue(scannerOpened)

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_fade_on_pause_switch"))
        composeTestRule.onNodeWithTag("settings_fade_on_pause_switch").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(settingsState.value.fadeOnPauseEnabled)
    }

    @Test
    fun equalizerSheetDoesNotDuplicateSettingsOnlyControls() {
        val controller = AudioEffectsController()
        val settings = AudioSettings(fadeOnPauseEnabled = true)

        composeTestRule.setContent {
            SonoraTheme {
                EqualizerAndDspSheet(
                    settings = settings,
                    bands = controller.getBands(settings),
                    onUpdateSettings = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag("settings_fade_on_pause_switch").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("settings_replaygain_scanner_row").assertCountEquals(0)
    }

    @Test
    fun instrumentalInterludeThresholdsEnforce4sIntro5sBlankGapAndVocalEstimation() {
        // 1. Intro < 4000 ms (3990 ms) -> NO intro note; breath pause (3500 ms < 5000 ms) -> ignored
        //    Exact 5000 ms blank pause -> kept; trailing blank line -> ignored
        val rawShortIntroAndBreath = """
            [00:03.99]Opening line
            [00:07.00]
            [00:10.50]After short breath
            [00:14.00]
            [00:19.00]After real five second break
            [00:24.00]
        """.trimIndent()
        val parsed1 = AudioTagParsers.parseLrcText(rawShortIntroAndBreath, "Test LRC")
        assertNotNull(parsed1)
        val lines1 = parsed1!!.lines
        assertEquals(4, lines1.size)
        assertFalse(lines1[0].isInstrumental)
        assertEquals("Opening line", lines1[0].text)
        assertFalse(lines1[1].isInstrumental)
        assertEquals("After short breath", lines1[1].text)
        assertTrue(lines1[2].isInstrumental)
        assertEquals(14_000L, lines1[2].timestampMs)
        assertFalse(lines1[3].isInstrumental)
        assertEquals("After real five second break", lines1[3].text)

        // 2. Exact 4000 ms intro -> shows intro note at 0 ms
        val rawExactIntro = """
            [00:04.00]Verse at four seconds
            [00:08.00]Next verse
        """.trimIndent()
        val parsedIntro = AudioTagParsers.parseLrcText(rawExactIntro, "Test LRC")!!
        assertTrue(parsedIntro.lines[0].isInstrumental)
        assertEquals(0L, parsedIntro.lines[0].timestampMs)

        // 3. Consecutive vocal lines > 8s apart without empty line:
        //    Long verse (90 chars -> 5850 ms) with 9000 ms gap leaves 3150 ms < 5000 ms -> NO note
        //    Long verse (90 chars -> 5850 ms) with 12000 ms gap leaves 6150 ms >= 5000 ms -> note at +5850 ms
        //    Short verse ("Hello" -> min 2500 ms) with 8500 ms gap leaves 6000 ms >= 5000 ms -> note at +2500 ms
        val longVerse = "A".repeat(90)
        assertEquals(5_850L, AudioTagParsers.estimateVocalDurationMs(longVerse))
        assertEquals(2_500L, AudioTagParsers.estimateVocalDurationMs("Hello"))

        val rawVocalEstimate = """
            [00:01.00]$longVerse
            [00:10.00]$longVerse
            [00:22.00]Hello
            [00:30.50]Final verse
        """.trimIndent()
        val parsedEstimate = AudioTagParsers.parseLrcText(rawVocalEstimate, "Test LRC")!!.lines
        // No intro (1000ms < 4000ms), no interlude after first longVerse (only 3150ms free),
        // interlude after second longVerse at 10_000 + 5_850 = 15_850ms (6150ms free),
        // interlude after "Hello" at 22_000 + 2_500 = 24_500ms (6000ms free).
        val instrTimestamps = parsedEstimate.filter { it.isInstrumental }.map { it.timestampMs }
        assertEquals(listOf(15_850L, 24_500L), instrTimestamps)

        // 4. UI verification: during ignored breath pause (8500ms between 7000ms and 10500ms),
        //    no MusicNote row is displayed for that breath pause and Opening line stays visible.
        val track = AudioTrack(
            id = "breath_pause_track",
            contentUri = "",
            title = "Clean Focus",
            artist = "Davoo",
            album = "v2.1.0",
            albumId = 210L,
            albumArtUri = null,
            durationMs = 40_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 5_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )
        composeTestRule.setContent {
            SonoraTheme {
                LyricsBottomSheet(
                    track = track,
                    lyrics = parsed1,
                    isLoading = false,
                    positionMs = 8_500L,
                    isPlaying = true,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        // Only the real 5s instrumental break at index 2 exists in the whole sheet
        composeTestRule.onAllNodesWithTag("lyrics_instrumental_note_0").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("lyrics_instrumental_note_1").assertCountEquals(0)
        composeTestRule.onNodeWithTag("lyrics_instrumental_note_2").assertIsDisplayed()
    }

    @Test
    fun deepSeekTranslatorSendsSingleStructuredJsonAndMapsHttpErrorsCleanly() = runTest {
        var capturedAuth: String? = null
        var capturedBody: String? = null
        var nextStatusCode = 200
        var nextBody = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "{\"translations\":[{\"index\":0,\"text\":\"Brillas como el alba\"},{\"index\":1,\"text\":\"Bajo el cielo infinito\"}]}"
                  }
                }
              ]
            }
        """.trimIndent()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                capturedAuth = req.header("Authorization")
                val sink = Buffer()
                req.body?.writeTo(sink)
                capturedBody = sink.readUtf8()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(nextStatusCode)
                    .message("OK")
                    .body(nextBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val translator = DeepSeekLyricsTranslator(
            apiKeyProvider = { "sk-deepseek-test-123456" },
            client = fakeClient,
            ioDispatcher = testDispatcher
        )
        val lyrics = LyricsData(
            lines = listOf(
                LyricLine(timestampMs = 4_000L, text = "You shine like the dawn"),
                LyricLine(timestampMs = 8_000L, text = "", isInstrumental = true),
                LyricLine(timestampMs = 15_000L, text = "Under the endless sky")
            ),
            isSynced = true,
            sourceLabel = "Local .lrc"
        )

        val translated = translator.translate(
            trackTitle = "Dawn",
            trackArtist = "Davoo",
            lyrics = lyrics,
            targetLanguage = LyricsTranslationLanguage.SPANISH
        )

        assertEquals("Bearer sk-deepseek-test-123456", capturedAuth)
        assertTrue(capturedBody.orEmpty().contains("\"model\":\"deepseek-chat\""))
        assertTrue(capturedBody.orEmpty().contains("\"temperature\":0.3"))
        assertTrue(capturedBody.orEmpty().contains("\"type\":\"json_object\""))
        assertEquals(3, translated.lines.size)
        assertEquals(4_000L, translated.lines[0].timestampMs)
        assertEquals("Brillas como el alba", translated.lines[0].text)
        assertTrue(translated.lines[1].isInstrumental)
        assertEquals(15_000L, translated.lines[2].timestampMs)
        assertEquals("Bajo el cielo infinito", translated.lines[2].text)
        assertEquals("DeepSeek (Español)", translated.sourceLabel)

        // Verify testConnection success
        nextBody = """{"choices":[{"message":{"role":"assistant","content":"{\"status\":\"ok\"}"}}]}"""
        val pingOk = translator.testConnection("sk-deepseek-test-123456")
        assertTrue(pingOk.isSuccess)

        // Verify HTTP 401 -> DeepSeekConfigurationException
        nextStatusCode = 401
        val err401 = runCatching {
            translator.translate("Dawn", "Davoo", lyrics, LyricsTranslationLanguage.SPANISH)
        }.exceptionOrNull()
        assertTrue(err401 is DeepSeekConfigurationException)
        assertTrue(err401?.message.orEmpty().contains("401"))

        // Verify HTTP 402 -> LyricsTranslationNetworkException with saldo insuficiente
        nextStatusCode = 402
        val err402 = runCatching {
            translator.translate("Dawn", "Davoo", lyrics, LyricsTranslationLanguage.SPANISH)
        }.exceptionOrNull()
        assertTrue(err402 is LyricsTranslationNetworkException)
        assertTrue(err402?.message.orEmpty().contains("Saldo insuficiente", ignoreCase = true))

        // Verify HTTP 429 -> LyricsTranslationNetworkException with límite de peticiones
        nextStatusCode = 429
        val err429 = runCatching {
            translator.translate("Dawn", "Davoo", lyrics, LyricsTranslationLanguage.SPANISH)
        }.exceptionOrNull()
        assertTrue(err429 is LyricsTranslationNetworkException)
        assertTrue(err429?.message.orEmpty().contains("Límite de peticiones", ignoreCase = true))

        // Verify coordinator routes DEEPSEEK and surfaces 402 message
        nextStatusCode = 402
        val coordinator = LyricsTranslationCoordinator(
            scope = this,
            deepSeekTranslator = translator
        )
        coordinator.translate("t_ds", "Dawn", "Davoo", lyrics, LyricsTranslationLanguage.SPANISH, TranslationEngine.DEEPSEEK)
        testScheduler.advanceUntilIdle()
        val uiErr = coordinator.state.value as LyricsTranslationUiState.Error
        assertEquals(TranslationEngine.DEEPSEEK, uiErr.engine)
        assertTrue(uiErr.message.contains("Saldo insuficiente", ignoreCase = true))
    }

    @Test
    fun deepSeekApiKeyPersistsInPreferencesAndIsExcludedFromBackupJson() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PlaybackPreferencesStore(context)
        val db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val secretKey = "sk-deepseek-private-do-not-export-98765"
            store.saveTranslationEngine(TranslationEngine.DEEPSEEK)
            store.saveDeepSeekApiKey(secretKey)
            assertEquals(secretKey, store.loadDeepSeekApiKey())
            assertEquals(TranslationEngine.DEEPSEEK, store.loadTranslationEngine())

            val manager = SonoraBackupManager(context, db, store)
            val backupJson = manager.buildBackupJson()
            assertTrue(backupJson.contains("DEEPSEEK"))
            assertFalse(backupJson.contains(secretKey))
            assertFalse(backupJson.contains("deepseek_api_key"))
        } finally {
            store.saveDeepSeekApiKey("")
            db.close()
        }
    }

    @Test
    fun settingsScreenRendersDeepSeekOptionMaskedInputAndTestConnection() {
        val selectedEngine = mutableStateOf(TranslationEngine.GOOGLE)
        val apiKeyState = mutableStateOf("")
        val testState = mutableStateOf<DeepSeekTestUiState>(DeepSeekTestUiState.Idle)
        var testedDraft: String? = null

        composeTestRule.setContent {
            SonoraTheme {
                SettingsScreen(
                    totalTracksCount = 12,
                    isScanning = false,
                    excludedFoldersCount = 0,
                    sdCardFoldersCount = 0,
                    audioSettings = AudioSettings(),
                    isSleepTimerActive = false,
                    preferredTranslationEngine = selectedEngine.value,
                    onBack = {},
                    onRefreshLibrary = {},
                    onImportFiles = {},
                    onOpenFoldersManager = {},
                    onAddSdCardFolder = {},
                    onOpenEqualizer = {},
                    onOpenSleepTimer = {},
                    onUpdateAudioSettings = {},
                    onSelectTranslationEngine = { selectedEngine.value = it },
                    onOpenAboutAndLegal = {},
                    bottomContentPadding = PaddingValues(0.dp),
                    deepSeekApiKey = apiKeyState.value,
                    deepSeekTestState = testState.value,
                    onSaveDeepSeekApiKey = { apiKeyState.value = it.trim() },
                    onTestDeepSeekConnection = { draft ->
                        testedDraft = draft
                        testState.value = DeepSeekTestUiState.Success("Conexión exitosa con DeepSeek AI")
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_engine_deepseek"))
        composeTestRule.onNodeWithTag("settings_engine_deepseek").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(TranslationEngine.DEEPSEEK, selectedEngine.value)
        composeTestRule.onNodeWithText("Traducción poética y contextual con IA usando tu API de DeepSeek")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_deepseek_api_key_input")
            .performScrollTo()
            .performTextReplacement("sk-live-deepseek-demo-42")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_deepseek_visibility_toggle")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_deepseek_test_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("sk-live-deepseek-demo-42", testedDraft)
        composeTestRule.onNodeWithTag("settings_deepseek_test_success")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun lyricsBottomSheetShowsDeepSeekEngineAndMissingKeyShortcut() {
        val track = AudioTrack(
            id = "ds_sheet_track",
            contentUri = "",
            title = "Poetic Song",
            artist = "Davoo",
            album = "v2.1.0",
            albumId = 99L,
            albumArtUri = null,
            durationMs = 180_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 4_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )
        val lyrics = LyricsData(
            lines = listOf(LyricLine(2_000L, "Dancing in the moonlight")),
            isSynced = true,
            sourceLabel = "Local .lrc"
        )
        val engineState = mutableStateOf(TranslationEngine.GOOGLE)
        var settingsShortcutClicks = 0

        composeTestRule.setContent {
            SonoraTheme {
                LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 2_000L,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {},
                    selectedEngine = engineState.value,
                    onSelectEngine = { engineState.value = it },
                    onTranslateLyrics = { _, _, _ -> },
                    hasDeepSeekApiKey = false,
                    onOpenTranslatorSettings = { settingsShortcutClicks++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lyrics_translate_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_engine_deepseek").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(TranslationEngine.DEEPSEEK, engineState.value)
        composeTestRule.onNodeWithTag("lyrics_deepseek_missing_key_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_deepseek_configure_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, settingsShortcutClicks)
    }
}
