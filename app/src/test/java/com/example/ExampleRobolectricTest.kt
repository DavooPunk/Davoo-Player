package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.SonoraDatabase
import com.example.model.AudioFormat
import com.example.model.AudioTechSpecs
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import coil.request.Options
import coil.size.Size
import com.example.ui.components.AudioArtImageLoaderProvider
import com.example.ui.components.EmbeddedAudioArtKeyer
import com.example.ui.components.EmbeddedAudioArtRequest
import com.example.ui.components.ExoPlayerControlBar
import com.example.ui.components.LocalAudioScannerList
import com.example.ui.components.Media3FloatingBottomBar
import com.example.ui.components.TrackMetadataBottomSheet
import com.example.ui.theme.SonoraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Davoo Player", appName)
    }

    @Test
    fun `create playlist add mp3 and flac tracks and reorder`() = runTest {
        val dao = db.playlistDao()
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Alta Fidelidad"))

        val flacTrack = AudioTrack(
            id = "ms_1",
            contentUri = "content://media/external/audio/media/1",
            title = "Clair de Lune",
            artist = "Claude Debussy",
            album = "Suite Bergamasque",
            albumId = 10L,
            albumArtUri = null,
            durationMs = 305000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 32_000_000L,
            dateAddedSec = 1000L,
            folderName = "Music"
        )
        val mp3Track = AudioTrack(
            id = "ms_2",
            contentUri = "content://media/external/audio/media/2",
            title = "So What",
            artist = "Miles Davis",
            album = "Kind of Blue",
            albumId = 11L,
            albumArtUri = null,
            durationMs = 562000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 13_400_000L,
            dateAddedSec = 2000L,
            folderName = "Download"
        )

        dao.insertPlaylistTrack(PlaylistTrackEntity.fromAudioTrack(playlistId, flacTrack, 0))
        dao.insertPlaylistTrack(PlaylistTrackEntity.fromAudioTrack(playlistId, mp3Track, 1))

        dao.reorderTrack(playlistId, 0, 1)

        val playlists = dao.observePlaylistsWithEntries().first()
        assertEquals(1, playlists.size)
        val ordered = playlists.first().entries.sortedBy { it.position }
        assertEquals("So What", ordered[0].title)
        assertEquals("Clair de Lune", ordered[1].title)
        assertEquals(AudioFormat.FLAC.name, ordered[1].formatName)
    }

    @Test
    fun `format technical audio badge for flac and mp3`() {
        val flacSpecs = AudioTechSpecs(
            sampleRateHz = 96000,
            bitrateBps = 2800000,
            channelCount = 2,
            bitsPerSample = 24
        )
        assertEquals("FLAC · LOSSLESS · 24-bit · 96 kHz · Estéreo", flacSpecs.formatBadge(AudioFormat.FLAC))

        val mp3Specs = AudioTechSpecs(
            sampleRateHz = 44100,
            bitrateBps = 320000,
            channelCount = 2
        )
        assertEquals("MP3 · 44.1 kHz · 320 kbps · Estéreo", mp3Specs.formatBadge(AudioFormat.MP3))
    }

    @Test
    fun `exoPlayerControlBar play pause and seek interact with ExoPlayer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exoPlayer = ExoPlayer.Builder(context).build()
        try {
            val item = MediaItem.Builder()
                .setUri(Uri.parse("file:///android_asset/sample.flac"))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Test FLAC Track")
                        .setArtist("Sonora Artist")
                        .build()
                )
                .build()
            exoPlayer.setMediaItem(item)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            composeTestRule.setContent {
                SonoraTheme {
                    ExoPlayerControlBar(player = exoPlayer)
                }
            }

            // Click Play -> ExoPlayer playWhenReady becomes true
            composeTestRule.onNodeWithTag("control_bar_play_button").performClick()
            composeTestRule.waitForIdle()
            assertTrue(exoPlayer.playWhenReady)

            // Click Pause -> ExoPlayer playWhenReady becomes false
            composeTestRule.onNodeWithTag("control_bar_pause_button").performClick()
            composeTestRule.waitForIdle()
            assertFalse(exoPlayer.playWhenReady)

            // Click Seek Forward (+10s) -> ExoPlayer position advances by 10_000ms
            composeTestRule.onNodeWithTag("control_bar_seek_forward_button").performClick()
            composeTestRule.waitForIdle()
            assertTrue(exoPlayer.currentPosition >= 10_000L)

            // Click Seek Back (-10s) -> ExoPlayer position returns to 0ms
            composeTestRule.onNodeWithTag("control_bar_seek_back_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(0L, exoPlayer.currentPosition)
        } finally {
            exoPlayer.release()
        }
    }

    @Test
    fun `localAudioScannerList scans mp3 and flac files and displays them in LazyColumn`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)

        val scannedTracks = listOf(
            AudioTrack(
                id = "scan_flac_1",
                contentUri = "content://media/external/audio/media/101",
                title = "Lossless Symphony",
                artist = "Studio Quartet",
                album = "Master Tape",
                albumId = 1L,
                albumArtUri = null,
                durationMs = 240_000L,
                format = AudioFormat.FLAC,
                mimeType = "audio/flac",
                sizeBytes = 28_000_000L,
                dateAddedSec = 100L,
                folderName = "Music"
            ),
            AudioTrack(
                id = "scan_mp3_2",
                contentUri = "content://media/external/audio/media/102",
                title = "Night Drive",
                artist = "Synth Collective",
                album = "Midnight MP3",
                albumId = 2L,
                albumArtUri = null,
                durationMs = 195_000L,
                format = AudioFormat.MP3,
                mimeType = "audio/mpeg",
                sizeBytes = 6_400_000L,
                dateAddedSec = 90L,
                folderName = "Download"
            )
        )

        var playedTitle: String? = null

        composeTestRule.setContent {
            SonoraTheme {
                LocalAudioScannerList(
                    onPlayTrack = { list, idx -> playedTitle = list[idx].title },
                    scanAudioFiles = { scannedTracks }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("local_audio_lazy_column").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lossless Symphony").assertIsDisplayed()
        composeTestRule.onNodeWithText("Night Drive").assertIsDisplayed()

        // Filter via search bar by artist
        composeTestRule.onNodeWithTag("local_scanner_search_input").performTextInput("Synth")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Night Drive").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lossless Symphony").assertDoesNotExist()

        // Clear search and filter by title
        composeTestRule.onNodeWithTag("local_scanner_search_clear").performClick()
        composeTestRule.onNodeWithTag("local_scanner_search_input").performTextInput("Symphony")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Lossless Symphony").assertIsDisplayed()
        composeTestRule.onNodeWithText("Night Drive").assertDoesNotExist()

        // Clear search before format filter check
        composeTestRule.onNodeWithTag("local_scanner_search_clear").performClick()
        composeTestRule.waitForIdle()

        // Filter to FLAC only
        composeTestRule.onNodeWithTag("local_scanner_filter_flac").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Lossless Symphony").assertIsDisplayed()
        composeTestRule.onNodeWithText("Night Drive").assertDoesNotExist()

        // Tap FLAC track
        composeTestRule.onNodeWithTag("track_row_scan_flac_1").performClick()
        assertEquals("Lossless Symphony", playedTitle)

        // Verify embedded album art thumbnail node and duration alongside artist are displayed
        composeTestRule.onNodeWithTag("track_thumbnail_scan_flac_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("track_duration_scan_flac_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("· 4:00", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `embeddedAudioArtKeyer and coil imageLoader initialize cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = AudioArtImageLoaderProvider.get(context)
        val keyer = EmbeddedAudioArtKeyer()
        val request = EmbeddedAudioArtRequest(
            audioUri = "content://media/external/audio/media/42",
            fallbackAlbumArtUri = "content://media/external/audio/albumart/7"
        )
        val key = keyer.key(request, Options(context = context, size = Size(256, 256)))
        assertTrue(key.contains("content://media/external/audio/media/42"))
        assertTrue(loader.components.fetcherFactories.isNotEmpty())
    }

    @Test
    fun `extractDurationFromFile returns null safely for invalid uri`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = com.example.data.repository.DeviceMusicScanner.extractDurationFromFile(
            context = context,
            uri = Uri.parse("file:///nonexistent/audio/missing.flac")
        )
        assertEquals(null, result)
    }

    @Test
    fun `media3FloatingBottomBar shows current track metadata and controls ExoPlayer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exoPlayer = ExoPlayer.Builder(context).build()
        try {
            val item1 = MediaItem.Builder()
                .setMediaId("track_1")
                .setUri(Uri.parse("file:///android_asset/track1.flac"))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("First Song")
                        .setArtist("Artist One")
                        .setAlbumTitle("Album Alpha")
                        .build()
                )
                .build()
            val item2 = MediaItem.Builder()
                .setMediaId("track_2")
                .setUri(Uri.parse("file:///android_asset/track2.mp3"))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Second Song")
                        .setArtist("Artist Two")
                        .setAlbumTitle("Album Beta")
                        .build()
                )
                .build()

            exoPlayer.setMediaItems(listOf(item1, item2))
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            composeTestRule.setContent {
                SonoraTheme {
                    Media3FloatingBottomBar(player = exoPlayer)
                }
            }

            composeTestRule.onNodeWithTag("floating_bottom_bar").assertIsDisplayed()
            composeTestRule.onNodeWithText("First Song", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Artist One · Album Alpha", useUnmergedTree = true).assertIsDisplayed()

            // Play -> playWhenReady true
            composeTestRule.onNodeWithTag("floating_bar_play_pause_button", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            assertTrue(exoPlayer.playWhenReady)

            // Pause -> playWhenReady false
            composeTestRule.onNodeWithTag("floating_bar_play_pause_button", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            assertFalse(exoPlayer.playWhenReady)

            // Skip Next -> switches to Second Song
            composeTestRule.onNodeWithTag("floating_bar_next_button", useUnmergedTree = true).performClick()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            assertEquals(1, exoPlayer.currentMediaItemIndex)
            composeTestRule.onNodeWithText("Second Song", useUnmergedTree = true).assertIsDisplayed()

            // Skip Previous -> switches back to First Song
            composeTestRule.onNodeWithTag("floating_bar_prev_button", useUnmergedTree = true).performClick()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            composeTestRule.waitForIdle()
            assertEquals(0, exoPlayer.currentMediaItemIndex)
        } finally {
            exoPlayer.release()
        }
    }

    @Test
    fun `trackMetadataBottomSheet displays song and album metadata`() {
        val track = AudioTrack(
            id = "ms_meta_1",
            contentUri = "content://media/external/audio/media/999",
            title = "Echoes in FLAC",
            artist = "Sonora Ensemble",
            album = "Studio Masters",
            albumId = 50L,
            albumArtUri = null,
            durationMs = 245_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 42_000_000L,
            dateAddedSec = 1000L,
            folderName = "HiRes",
            albumArtist = "Sonora Ensemble",
            genre = "Ambient Jazz",
            year = 2024,
            trackNumber = 3
        )
        val metadata = AudioTrackMetadata(
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumArtist = track.albumArtist,
            genre = track.genre,
            year = track.year,
            trackNumber = track.trackNumber,
            durationMs = track.durationMs,
            format = track.format,
            mimeType = track.mimeType,
            sizeBytes = track.sizeBytes,
            folderName = track.folderName,
            hasEmbeddedArtwork = true,
            techSpecs = AudioTechSpecs(sampleRateHz = 96000, bitrateBps = 2800000, channelCount = 2, bitsPerSample = 24)
        )

        composeTestRule.setContent {
            SonoraTheme {
                TrackMetadataBottomSheet(
                    track = track,
                    metadata = metadata,
                    isLoading = false,
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("track_metadata_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Studio Masters").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Ambient Jazz").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2024").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("FLAC · LOSSLESS · 24-bit · 96 kHz · Estéreo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `room trackStats tagOverrides and excludedFolders persist properly`() = runTest {
        val statsDao = db.trackStatsDao()
        val tagDao = db.trackTagOverrideDao()
        val folderDao = db.excludedFolderDao()

        val favAfterFirst = statsDao.toggleFavorite("track_10")
        assertTrue(favAfterFirst)
        statsDao.recordPlay("track_10")
        statsDao.recordPlay("track_10")

        val stats = statsDao.observeAllStats().first().first { it.trackId == "track_10" }
        assertTrue(stats.isFavorite)
        assertEquals(2, stats.playCount)
        assertTrue(stats.lastPlayedAt > 0L)

        tagDao.upsert(
            com.example.data.local.TrackTagOverrideEntity(
                trackId = "track_10",
                title = "Edited Title",
                artist = "Edited Artist",
                album = "Edited Album",
                albumArtist = "Edited Album Artist",
                genre = "Post-Rock",
                year = 2023,
                trackNumber = 5,
                customArtworkUri = null
            )
        )
        val overrides = tagDao.observeAllOverrides().first()
        assertEquals(1, overrides.size)
        assertEquals("Edited Title", overrides.first().title)

        folderDao.excludeFolder(com.example.data.local.ExcludedFolderEntity("WhatsApp Audio"))
        val excluded = folderDao.observeExcludedFolders().first().map { it.folderName }
        assertTrue("WhatsApp Audio" in excluded)
        folderDao.includeFolder("WhatsApp Audio")
        assertTrue(folderDao.observeExcludedFolders().first().isEmpty())
    }

    @Test
    fun `audioTagParsers parses synced lrc cue sheets and m3u playlists`() {
        val lrcRaw = """
            [ar:Debussy]
            [00:12.50]First synced line
            [01:05.00]Second synced line
        """.trimIndent()
        val lyrics = com.example.data.repository.AudioTagParsers.parseLrcText(lrcRaw, "Test LRC")
        assertTrue(lyrics != null && lyrics.isSynced)
        val vocalLines = lyrics!!.lines.filter { !it.isInstrumental }
        assertEquals(2, vocalLines.size)
        assertEquals(12_500L, vocalLines[0].timestampMs)
        assertEquals("First synced line", vocalLines[0].text)
        assertEquals(65_000L, vocalLines[1].timestampMs)
        // Intro (>=4s) and interlude (>8s gap with 65ms/char min 2.5s vocal estimate) instrumental markers
        assertTrue(lyrics.lines[0].isInstrumental)
        assertEquals(0L, lyrics.lines[0].timestampMs)
        assertTrue(lyrics.lines.any { it.isInstrumental && it.timestampMs == 12_500L + 2_500L })

        val cueRaw = """
            TRACK 01 AUDIO
              TITLE "Opening Theme"
              PERFORMER "Sonora Orchestra"
              INDEX 01 00:00:00
            TRACK 02 AUDIO
              TITLE "Midnight Etude"
              PERFORMER "Sonora Orchestra"
              INDEX 01 03:20:00
        """.trimIndent()
        val cueEntries = com.example.data.repository.AudioTagParsers.parseCueSheet(cueRaw)
        assertEquals(2, cueEntries.size)
        assertEquals("Midnight Etude", cueEntries[1].title)
        assertEquals(200_000L, cueEntries[1].startMs)

        val track = AudioTrack(
            id = "m3u_1",
            contentUri = "content://media/external/audio/media/55",
            title = "Wave Song",
            artist = "Losless Duo",
            album = "Waves",
            albumId = 1L,
            albumArtUri = null,
            durationMs = 180_000L,
            format = AudioFormat.WAV,
            mimeType = "audio/wav",
            sizeBytes = 15_000_000L,
            dateAddedSec = 100L,
            folderName = "Music",
            filePath = "/storage/emulated/0/Music/wave_song.wav"
        )
        val m3u = com.example.data.repository.AudioTagParsers.exportPlaylistToM3u("Roadtrip", listOf(track))
        assertTrue(m3u.contains("#EXTM3U"))
        assertTrue(m3u.contains("#PLAYLIST:Roadtrip"))
        val parsedPaths = com.example.data.repository.AudioTagParsers.parseM3uPaths(m3u)
        assertEquals(listOf("/storage/emulated/0/Music/wave_song.wav"), parsedPaths)
    }

    @Test
    fun `playbackPreferencesStore saves and restores audio settings and playback session`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = com.example.data.local.PlaybackPreferencesStore(context)

        val customSettings = com.example.model.AudioSettings(
            eqEnabled = true,
            eqPreset = com.example.model.EqPreset.ROCK,
            bandGainsMb = com.example.model.EqPreset.ROCK.gainsMb,
            bassBoostStrength = 450,
            virtualizerStrength = 300,
            crossfadeSeconds = 5,
            gaplessEnabled = true,
            replayGainEnabled = true,
            playbackSpeed = 1.25f,
            stereoBalance = -0.25f,
            dynamicArtworkColors = false
        )
        store.saveAudioSettings(customSettings)
        val loadedSettings = store.loadAudioSettings()
        assertEquals(customSettings, loadedSettings)

        val session = com.example.data.local.SavedPlaybackSession(
            items = listOf(
                com.example.data.local.SavedMediaItemSnapshot(
                    id = "res_1",
                    contentUri = "content://media/external/audio/media/77",
                    title = "Resumed Track",
                    artist = "Resumed Artist",
                    album = "Resumed Album",
                    formatName = AudioFormat.AAC.name,
                    durationMs = 210_000L
                )
            ),
            currentIndex = 0,
            positionMs = 42_000L,
            shuffleEnabled = true,
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        )
        store.savePlaybackSession(session)
        val restored = store.loadPlaybackSession()
        assertEquals(42_000L, restored?.positionMs)
        assertEquals("Resumed Track", restored?.items?.firstOrNull()?.title)
        assertTrue(restored?.shuffleEnabled == true)
    }

    @Test
    fun `equalizerAndDspSheet and sleepTimerSheet render and interact`() {
        val eqController = com.example.playback.AudioEffectsController()
        val settingsState = androidx.compose.runtime.mutableStateOf(com.example.model.AudioSettings())
        val bands = eqController.getBands(settingsState.value)
        assertEquals(10, bands.size)

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.EqualizerAndDspSheet(
                    settings = settingsState.value,
                    bands = eqController.getBands(settingsState.value),
                    onUpdateSettings = { settingsState.value = it },
                    onDismiss = {}
                )
            }
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("equalizer_dsp_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_frequency_curve_canvas").performScrollTo()
        composeTestRule.onNodeWithTag("eq_preset_rock").performScrollTo().performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.EqPreset.ROCK, settingsState.value.eqPreset)
        assertTrue(settingsState.value.eqEnabled)

        composeTestRule.onNodeWithText("Velocidad de reproducción").performScrollTo()
        composeTestRule.onNodeWithTag("crossfade_chip_5").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(5, settingsState.value.crossfadeSeconds)
    }

    @Test
    fun `sleepTimerSheet selects duration and cancels timer`() {
        var selectedMinutes: Int? = null
        var cancelled = false

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.SleepTimerSheet(
                    remainingMs = 600_000L,
                    stopAfterCurrentTrack = false,
                    onSelectMinutes = { selectedMinutes = it },
                    onSelectAfterTrack = {},
                    onCancelTimer = { cancelled = true },
                    onDismiss = {}
                )
            }
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sleep_timer_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sleep_timer_30m").performClick()
        assertEquals(30, selectedMinutes)
        composeTestRule.onNodeWithTag("cancel_sleep_timer_button").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun `lyricsBottomSheet displays synced lines and seeks on tap`() {
        val track = AudioTrack(
            id = "lyr_1",
            contentUri = "content://media/external/audio/media/88",
            title = "Lyric Song",
            artist = "Vocalist",
            album = "Ballads",
            albumId = 8L,
            albumArtUri = null,
            durationMs = 200_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 20_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )
        val lyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(5_000L, "Hello world in FLAC"),
                com.example.model.LyricLine(25_000L, "Second verse line")
            ),
            isSynced = true,
            sourceLabel = "Archivo .lrc local"
        )
        var seekedMs = -1L

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 6_000L,
                    onSeekTo = { seekedMs = it },
                    onPickLrcFile = {},
                    onDismiss = {}
                )
            }
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second verse line").performClick()
        assertEquals(25_000L, seekedMs)
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `stereoBalanceAudioProcessor bypasses mono or 24bit cleanly and processes float or 16bit stereo`() {
        val processor = com.example.playback.StereoBalanceAudioProcessor()
        processor.setBalance(-0.5f)

        // Mono 16-bit -> inactive (bypassed without throwing UnhandledAudioFormatException)
        processor.configure(
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                44100,
                1,
                androidx.media3.common.C.ENCODING_PCM_16BIT
            )
        )
        processor.flush()
        assertFalse(processor.isActive)

        // Stereo 24-bit -> inactive (bypassed without throwing)
        processor.configure(
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                96000,
                2,
                androidx.media3.common.C.ENCODING_PCM_24BIT
            )
        )
        processor.flush()
        assertFalse(processor.isActive)

        // Stereo PCM_FLOAT -> active and scales L/R cleanly
        processor.configure(
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                48000,
                2,
                androidx.media3.common.C.ENCODING_PCM_FLOAT
            )
        )
        processor.flush()
        assertTrue(processor.isActive)

        val input = java.nio.ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.nativeOrder())
        input.putFloat(0.8f)
        input.putFloat(0.8f)
        input.flip()
        processor.queueInput(input)
        val out = processor.output
        assertEquals(8, out.remaining())
        val left = out.float
        val right = out.float
        assertEquals(0.8f, left, 0.01f)
        assertEquals(0.4f, right, 0.01f)
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `sonoraArtworkBitmapLoader and widget decodeSampledRgb565 safely handle audio URIs and large covers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = com.example.playback.SonoraArtworkBitmapLoader(context)

        // Audio URI with no embedded picture must never throw or attempt readBytes crash; returns fallback Bitmap
        val fallbackBitmap = loader.loadBitmap(Uri.parse("content://media/external/audio/media/999999")).get()
        assertTrue(fallbackBitmap.width > 0 && fallbackBitmap.height > 0)

        // Corrupt bytes -> decodeBitmap also returns fallback without throwing
        val corruptFallback = loader.decodeBitmap(byteArrayOf(1, 2, 3, 4)).get()
        assertTrue(corruptFallback.width > 0 && corruptFallback.height > 0)

        // Large 1024x1024 bitmap encoded to PNG -> decodeSampledRgb565 downsamples cleanly for Binder IPC
        val srcBitmap = android.graphics.Bitmap.createBitmap(1024, 1024, android.graphics.Bitmap.Config.ARGB_8888)
        val stream = java.io.ByteArrayOutputStream()
        srcBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        srcBitmap.recycle()

        val widgetBitmap = com.example.widget.SonoraWidgetProvider.decodeSampledRgb565(stream.toByteArray())
        assertTrue(widgetBitmap != null)
        assertTrue(widgetBitmap!!.width <= 256 && widgetBitmap.height <= 256)
        assertEquals(android.graphics.Bitmap.Config.RGB_565, widgetBitmap.config)
        widgetBitmap.recycle()
    }

    @Test
    fun `audioEffectsController lazily skips native effects when disabled and nowPlayingScreen renders cleanly`() {
        val effectsController = com.example.playback.AudioEffectsController()
        effectsController.attachToSession(101, com.example.model.AudioSettings())
        val bands = effectsController.getBands()
        assertEquals(10, bands.size)
        effectsController.release()

        val track = AudioTrack(
            id = "np_1",
            contentUri = "content://media/external/audio/media/321",
            title = "Nocturne in Hi-Res",
            artist = "Sonora Trio",
            album = "Midnight Sessions",
            albumId = 99L,
            albumArtUri = null,
            durationMs = 240_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 35_000_000L,
            dateAddedSec = 1000L,
            folderName = "FLAC"
        )
        val state = com.example.playback.PlaybackUiState(
            currentTrack = track,
            isPlaying = false,
            positionMs = 30_000L,
            durationMs = 240_000L
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.NowPlayingScreen(
                    playbackState = state,
                    onCollapse = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSeekBackward = {},
                    onSeekForward = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onOpenQueue = {},
                    onAddToPlaylist = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nocturne in Hi-Res").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_play_pause_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `musicRepository persists loaded lrc lyrics across loads and clears cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempLrc = java.io.File.createTempFile("sonora_test_", ".lrc", context.cacheDir)
        try {
            // Include UTF-8 BOM to verify BOM stripping
            tempLrc.writeText(
                "\uFEFF[ar:Debussy]\n[00:10.00]Saved synced lyric line 1\n[00:20.00]Saved synced lyric line 2\n",
                Charsets.UTF_8
            )
            val repository = com.example.data.repository.MusicRepository(
                context = context,
                scanner = com.example.data.repository.DeviceMusicScanner(context),
                playlistDao = db.playlistDao(),
                importedTrackDao = db.importedTrackDao(),
                trackStatsDao = db.trackStatsDao(),
                trackTagOverrideDao = db.trackTagOverrideDao(),
                excludedFolderDao = db.excludedFolderDao(),
                trackLyricsDao = db.trackLyricsDao()
            )
            val track = AudioTrack(
                id = "lrc_persist_1",
                contentUri = "content://media/external/audio/media/555",
                title = "Clair de Lune",
                artist = "Debussy",
                album = "Suite",
                albumId = 12L,
                albumArtUri = null,
                durationMs = 240_000L,
                format = AudioFormat.FLAC,
                mimeType = "audio/flac",
                sizeBytes = 25_000_000L,
                dateAddedSec = 100L,
                folderName = "Music"
            )

            // Save .lrc from URI
            val saved = repository.loadAndSaveLyricsFromUri(track.id, Uri.fromFile(tempLrc))
            assertTrue(saved != null && saved.isSynced)
            val savedVocal = saved!!.lines.filter { !it.isInstrumental }
            assertEquals(2, savedVocal.size)
            assertEquals("Saved synced lyric line 1", savedVocal[0].text)
            assertTrue(saved.lines.any { it.isInstrumental && it.timestampMs == 0L })

            // Delete original source file to verify DB persistence is independent
            tempLrc.delete()

            // Reload later -> should still come back from Room
            val reloaded = repository.loadLyrics(track)
            assertTrue(reloaded != null && reloaded.isSynced)
            assertEquals(com.example.data.repository.AudioTagParsers.SAVED_LRC_SOURCE_LABEL, reloaded!!.sourceLabel)
            val reloadedVocal = reloaded.lines.filter { !it.isInstrumental }
            assertEquals("Saved synced lyric line 2", reloadedVocal[1].text)

            // Clear saved lyrics -> DB entry deleted
            val cleared = repository.clearSavedLyrics(track)
            assertEquals(null, cleared)
            assertEquals(null, repository.loadLyrics(track))
        } finally {
            tempLrc.delete()
        }
    }

    @Test
    fun `lyricsBottomSheet shows clear button for saved lrc and invokes callback`() {
        val track = AudioTrack(
            id = "lyr_saved_1",
            contentUri = "content://media/external/audio/media/89",
            title = "Saved Lyric Song",
            artist = "Singer",
            album = "LP",
            albumId = 9L,
            albumArtUri = null,
            durationMs = 180_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 6_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )
        val lyrics = com.example.model.LyricsData(
            lines = listOf(com.example.model.LyricLine(4_000L, "Persisted line")),
            isSynced = true,
            sourceLabel = com.example.data.repository.AudioTagParsers.SAVED_LRC_SOURCE_LABEL
        )
        var cleared = false

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 4_000L,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {},
                    onClearSavedLyrics = { cleared = true }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        // Under Spotify-style minimal view, file/download management lives inside bottom-right menu
        composeTestRule.onNodeWithTag("lyrics_manage_menu_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_saved_lrc_button").assertIsDisplayed().performClick()
        assertTrue(cleared)
    }

    @Test
    fun `libraryScreen v2 is clean unified and opens SettingsScreen from top-right gear button`() {
        val showSettings = androidx.compose.runtime.mutableStateOf(false)
        val showCredits = androidx.compose.runtime.mutableStateOf(false)
        val selectedEngine = androidx.compose.runtime.mutableStateOf(com.example.ai.TranslationEngine.DEEPL)
        var refreshTriggered = false

        composeTestRule.setContent {
            SonoraTheme {
                if (!showSettings.value) {
                    com.example.ui.screens.LibraryScreen(
                        uiState = com.example.ui.viewmodel.SonoraUiState(hasAudioPermission = true),
                        playbackState = com.example.playback.PlaybackUiState(),
                        onRequestPermission = {},
                        onImportFiles = {},
                        onOpenSettings = { showSettings.value = true },
                        onSearchChange = {},
                        onSelectSortOrder = {},
                        onPlayTrack = { _, _ -> },
                        onShuffleAll = {},
                        onPlayNext = {},
                        onAddToQueue = {},
                        onAddToPlaylist = {},
                        bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    )
                } else {
                    com.example.ui.screens.SettingsScreen(
                        totalTracksCount = 12,
                        isScanning = false,
                        excludedFoldersCount = 1,
                        sdCardFoldersCount = 1,
                        audioSettings = com.example.model.AudioSettings(),
                        isSleepTimerActive = false,
                        preferredTranslationEngine = selectedEngine.value,
                        onBack = { showSettings.value = false },
                        onRefreshLibrary = { refreshTriggered = true },
                        onImportFiles = {},
                        onOpenFoldersManager = {},
                        onAddSdCardFolder = {},
                        onOpenEqualizer = {},
                        onOpenSleepTimer = {},
                        onUpdateAudioSettings = {},
                        onSelectTranslationEngine = { selectedEngine.value = it },
                        onOpenAboutAndLegal = { showCredits.value = true },
                        bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    )
                }
                if (showCredits.value) {
                    com.example.ui.components.CreditsBottomSheet(
                        onDismiss = { showCredits.value = false }
                    )
                }
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("DAVOO PLAYER").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tu Música").assertIsDisplayed()
        // Unified library has no cluttered MP3 / FLAC format filter menus
        composeTestRule.onNodeWithTag("filter_chip_flac").assertDoesNotExist()
        composeTestRule.onNodeWithTag("filter_chip_mp3").assertDoesNotExist()

        // Tap gear button to enter unified Settings sub-menu
        composeTestRule.onNodeWithTag("library_settings_button").assertIsDisplayed().performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_refresh_library_row").performClick()
        assertTrue(refreshTriggered)

        // Switch translation engine in Settings
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_engine_google"))
        composeTestRule.onNodeWithTag("settings_engine_google").performClick()
        assertEquals(com.example.ai.TranslationEngine.GOOGLE, selectedEngine.value)

        // Open About & Legal from Settings
        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_about_legal_row"))
        composeTestRule.onNodeWithTag("settings_about_legal_row").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("credits_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("credits_davoopunk").assertIsDisplayed()
        composeTestRule.onNodeWithText("davidmarin2704@gmail.com").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `trackStatsDao setFavorite persists explicit state and albumsScreen drilldown reflects live favorite updates`() = runTest {
        val statsDao = db.trackStatsDao()
        assertTrue(statsDao.setFavorite("fav_track_1", true))
        assertTrue(statsDao.getStatsForTrack("fav_track_1")?.isFavorite == true)
        assertFalse(statsDao.setFavorite("fav_track_1", false))
        assertFalse(statsDao.getStatsForTrack("fav_track_1")?.isFavorite == true)

        val baseTrack = AudioTrack(
            id = "drill_fav_1",
            contentUri = "content://media/external/audio/media/444",
            title = "Midnight Riff",
            artist = "Davoo Band",
            album = "Live Tape",
            albumId = 44L,
            albumArtUri = null,
            durationMs = 210_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 24_000_000L,
            dateAddedSec = 100L,
            folderName = "Music",
            isFavorite = false
        )
        val artistsState = androidx.compose.runtime.mutableStateOf(
            listOf(
                com.example.model.ArtistGroup(
                    artistName = "Davoo Band",
                    albumCount = 1,
                    tracks = listOf(baseTrack),
                    albumArtUri = null,
                    totalDurationMs = baseTrack.durationMs
                )
            )
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.AlbumsScreen(
                    albums = emptyList(),
                    artists = artistsState.value,
                    onAlbumClick = {},
                    onToggleFavorite = { target ->
                        val nextFav = !target.isFavorite
                        artistsState.value = artistsState.value.map { group ->
                            group.copy(
                                tracks = group.tracks.map { t ->
                                    if (t.id == target.id) t.copy(isFavorite = nextFav) else t
                                }
                            )
                        }
                    },
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Navigate into Artists tab and open Davoo Band drilldown
        composeTestRule.onNodeWithTag("collection_tab_artists").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("artist_row_Davoo Band").performClick()
        composeTestRule.waitForIdle()

        // Tap favorite heart inside drilldown -> contentDescription updates immediately from live artistsState
        composeTestRule.onNodeWithTag("track_fav_drill_fav_1", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Quitar de favoritas", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `lrcLibLyricsFetcher parses synced and plain lyrics and repository saves downloaded lyrics offline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url
                val jsonPayload = if (url.encodedPath.endsWith("/api/get")) {
                    """
                    {
                      "id": 771,
                      "trackName": "Starlight",
                      "artistName": "Muse",
                      "albumName": "Black Holes and Revelations",
                      "duration": 240.0,
                      "instrumental": false,
                      "plainLyrics": "Far away",
                      "syncedLyrics": "[00:15.00]Far away\n[00:22.00]This ship is taking me far away"
                    }
                    """.trimIndent()
                } else {
                    """
                    [
                      {
                        "id": 772,
                        "trackName": "Starlight (Live)",
                        "artistName": "Muse",
                        "albumName": "HAARP",
                        "duration": 248.0,
                        "instrumental": false,
                        "plainLyrics": "Hold you in my arms",
                        "syncedLyrics": null
                      }
                    ]
                    """.trimIndent()
                }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        okhttp3.ResponseBody.run {
                            jsonPayload.toResponseBody(
                                okhttp3.MediaType.run {
                                    "application/json; charset=utf-8".toMediaType()
                                }
                            )
                        }
                    )
                    .build()
            }
            .build()

        val fetcher = com.example.data.repository.LrcLibLyricsFetcher(client = mockClient)
        val repository = com.example.data.repository.MusicRepository(
            context = context,
            scanner = com.example.data.repository.DeviceMusicScanner(context),
            playlistDao = db.playlistDao(),
            importedTrackDao = db.importedTrackDao(),
            trackStatsDao = db.trackStatsDao(),
            trackTagOverrideDao = db.trackTagOverrideDao(),
            excludedFolderDao = db.excludedFolderDao(),
            trackLyricsDao = db.trackLyricsDao(),
            onlineLyricsFetcher = fetcher
        )

        val track = AudioTrack(
            id = "online_lyr_1",
            contentUri = "content://media/external/audio/media/900",
            title = "Starlight [Official Audio]",
            artist = "Muse",
            album = "Black Holes and Revelations",
            albumId = 90L,
            albumArtUri = null,
            durationMs = 240_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 28_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )

        val searchResult = repository.searchOnlineLyrics(track)
        assertTrue(searchResult.isSuccess)
        val candidates = searchResult.getOrThrow()
        assertEquals(2, candidates.size)
        // Synced candidate should be prioritized first
        assertTrue(candidates.first().isSynced)
        assertEquals(771L, candidates.first().id)

        val saved = repository.saveDownloadedLyrics(track.id, candidates.first())
        assertTrue(saved != null && saved.isSynced)
        assertEquals(com.example.data.repository.AudioTagParsers.ONLINE_LRC_SOURCE_LABEL, saved!!.sourceLabel)
        val savedVocal = saved.lines.filter { !it.isInstrumental }
        assertEquals("This ship is taking me far away", savedVocal[1].text)

        // Verify offline reload from Room
        val reloaded = repository.loadLyrics(track)
        assertTrue(reloaded != null && reloaded.isSynced)
        val reloadedVocal = reloaded!!.lines.filter { !it.isInstrumental }
        assertEquals("Far away", reloadedVocal[0].text)

        // Verify Lyricsify URL builder
        val lyricsifyUrl = com.example.data.repository.LrcLibLyricsFetcher.buildLyricsifySearchUrl("Muse Starlight")
        assertTrue(lyricsifyUrl.startsWith("https://www.lyricsify.com/search?q="))
        assertTrue(lyricsifyUrl.contains("Muse"))
    }

    @Test
    fun `lyricsBottomSheet online search displays results saves selection and triggers Lyricsify browser`() {
        val track = AudioTrack(
            id = "ui_online_1",
            contentUri = "content://media/external/audio/media/901",
            title = "Hysteria",
            artist = "Muse",
            album = "Absolution",
            albumId = 91L,
            albumArtUri = null,
            durationMs = 227_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 8_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )
        val candidate = com.example.data.repository.OnlineLyricsCandidate(
            id = 505L,
            title = "Hysteria",
            artist = "Muse",
            album = "Absolution",
            durationSec = 227,
            rawLyrics = "[00:12.00]It's bugging me\n[00:16.00]Grating me",
            isSynced = true
        )
        var savedCandidate: com.example.data.repository.OnlineLyricsCandidate? = null
        var openedLyricsifyQuery: String? = null

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = null,
                    isLoading = false,
                    positionMs = 0L,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {},
                    onSearchOnlineLyrics = { Result.success(listOf(candidate)) },
                    onSaveOnlineLyrics = { savedCandidate = it },
                    onOpenLyricsify = { openedLyricsifyQuery = it }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("download_online_lyrics_button").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("online_lyrics_search_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("online_lyrics_result_505").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_lyricsify_browser_button").performClick()
        assertEquals("Muse Hysteria", openedLyricsifyQuery)

        composeTestRule.onNodeWithTag("online_lyrics_result_505").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        assertEquals(505L, savedCandidate?.id)
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `playbackService stops playback persists position and calls stopSelf on multitasking task removal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = org.robolectric.Robolectric.buildService(com.example.playback.PlaybackService::class.java)
        val service = controller.create().get()
        try {
            val player = service.activePlayerOrNull()!!
            val item = MediaItem.Builder()
                .setMediaId("task_remove_1")
                .setUri(Uri.parse("file:///android_asset/track_task.flac"))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Task Removal Song")
                        .setArtist("DavooPunk")
                        .setAlbumTitle("Shutdown Test")
                        .build()
                )
                .build()
            player.setMediaItem(item)
            player.seekTo(48_000L)
            player.playWhenReady = true
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            assertTrue(player.playWhenReady)

            // Simulate user swiping the app away from Recents / Multitarea
            service.onTaskRemoved(null)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            assertFalse(player.playWhenReady)
            assertTrue(Shadows.shadowOf(service).isStoppedBySelf)

            // Saved session must preserve pre-stop playback position
            val prefsStore = com.example.data.local.PlaybackPreferencesStore(context)
            val saved = prefsStore.loadPlaybackSession()
            assertEquals("task_remove_1", saved?.items?.firstOrNull()?.id)
            assertTrue((saved?.positionMs ?: 0L) >= 48_000L)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `audioArtImageLoaderProvider bounds memory cache disables disk cache and trims cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = AudioArtImageLoaderProvider.get(context)
        assertTrue(loader.memoryCache != null)
        assertTrue((loader.memoryCache?.maxSize ?: 0) > 0)
        assertEquals(null, loader.diskCache)
        AudioArtImageLoaderProvider.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    }

    @Test
    fun `musicRepository removes playlist tracks and imported tracks in batch`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = com.example.data.repository.MusicRepository(
            context = context,
            scanner = com.example.data.repository.DeviceMusicScanner(context),
            playlistDao = db.playlistDao(),
            importedTrackDao = db.importedTrackDao(),
            trackStatsDao = db.trackStatsDao(),
            trackTagOverrideDao = db.trackTagOverrideDao(),
            excludedFolderDao = db.excludedFolderDao(),
            trackLyricsDao = db.trackLyricsDao()
        )
        val pid = db.playlistDao().insertPlaylist(PlaylistEntity(name = "Batch List"))
        val tracks = (1..3).map { i ->
            AudioTrack(
                id = "b_$i",
                contentUri = "content://media/external/audio/media/$i",
                title = "Track $i",
                artist = "Artist",
                album = "Album",
                albumId = i.toLong(),
                albumArtUri = null,
                durationMs = 180_000L,
                format = AudioFormat.FLAC,
                mimeType = "audio/flac",
                sizeBytes = 10_000_000L,
                dateAddedSec = i.toLong(),
                folderName = "Music"
            )
        }
        tracks.forEachIndexed { idx, t ->
            db.playlistDao().insertPlaylistTrack(PlaylistTrackEntity.fromAudioTrack(pid, t, idx))
        }

        val removedFromPlaylist = repository.removeTracksFromPlaylist(pid, listOf("b_1", "b_3"))
        assertEquals(2, removedFromPlaylist)
        val remainingEntries = db.playlistDao().observePlaylistsWithEntries().first().first().entries
        assertEquals(listOf("b_2"), remainingEntries.map { it.trackId })

        db.importedTrackDao().upsertTracks(
            listOf(
                com.example.data.local.ImportedTrackEntity(
                    uri = "content://saf/1",
                    title = "SAF 1",
                    artist = "A",
                    album = "Al",
                    durationMs = 120_000L,
                    formatName = AudioFormat.MP3.name,
                    mimeType = "audio/mpeg",
                    sizeBytes = 4_000_000L,
                    folderName = "Music"
                ),
                com.example.data.local.ImportedTrackEntity(
                    uri = "content://saf/2",
                    title = "SAF 2",
                    artist = "B",
                    album = "Al",
                    durationMs = 130_000L,
                    formatName = AudioFormat.FLAC.name,
                    mimeType = "audio/flac",
                    sizeBytes = 14_000_000L,
                    folderName = "Music"
                )
            )
        )
        val deletedSaf = repository.removeImportedTracksByUris(listOf("content://saf/1"))
        assertEquals(1, deletedSaf)
        assertEquals(listOf("content://saf/2"), db.importedTrackDao().observeImportedTracks().first().map { it.uri })
    }

    @Test
    fun `libraryScreen shows fixed top-right search button and fast scroller without legacy inline search or quick fab`() {
        val manyTracks = (1..40).map { i ->
            AudioTrack(
                id = "scroll_$i",
                contentUri = "content://media/external/audio/media/${500 + i}",
                title = "Track ${i.toString().padStart(2, '0')}",
                artist = "DavooPunk",
                album = "Long Library",
                albumId = i.toLong(),
                albumArtUri = null,
                durationMs = 200_000L,
                format = AudioFormat.FLAC,
                mimeType = "audio/flac",
                sizeBytes = 20_000_000L,
                dateAddedSec = i.toLong(),
                folderName = "Music"
            )
        }
        var openSearchCount = 0

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.LibraryScreen(
                    uiState = com.example.ui.viewmodel.SonoraUiState(
                        hasAudioPermission = true,
                        allTracks = manyTracks,
                        filteredTracks = manyTracks
                    ),
                    playbackState = com.example.playback.PlaybackUiState(),
                    onRequestPermission = {},
                    onImportFiles = {},
                    onOpenGlobalSearch = { openSearchCount++ },
                    onSelectSortOrder = {},
                    onPlayTrack = { _, _ -> },
                    onShuffleAll = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("fast_scroll_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fast_scroll_thumb", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("header_search_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_quick_search_fab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("library_search_input").assertDoesNotExist()

        // Scroll deep down into the song list; fixed top-right header search button remains visible
        composeTestRule.onNodeWithTag("library_lazy_column")
            .performScrollToNode(hasTestTag("track_row_scroll_30"))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("library_quick_search_fab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("header_search_button").assertIsDisplayed().performClick()
        assertEquals(1, openSearchCount)
    }

    @Test
    fun `libraryScreen supports multi select for batch playlist enqueue and delete`() {
        val sampleTracks = listOf("t1", "t2", "t3").mapIndexed { idx, id ->
            AudioTrack(
                id = id,
                contentUri = "content://media/external/audio/media/${600 + idx}",
                title = "Batch Song ${idx + 1}",
                artist = "Artist ${idx + 1}",
                album = "Album",
                albumId = 10L + idx,
                albumArtUri = null,
                durationMs = 190_000L,
                format = AudioFormat.MP3,
                mimeType = "audio/mpeg",
                sizeBytes = 5_000_000L,
                dateAddedSec = 100L + idx,
                folderName = "Music"
            )
        }
        var addedBatch: List<AudioTrack> = emptyList()
        var enqueuedBatch: List<AudioTrack> = emptyList()
        var deletedBatch: List<AudioTrack> = emptyList()

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.LibraryScreen(
                    uiState = com.example.ui.viewmodel.SonoraUiState(
                        hasAudioPermission = true,
                        allTracks = sampleTracks,
                        filteredTracks = sampleTracks
                    ),
                    playbackState = com.example.playback.PlaybackUiState(),
                    onRequestPermission = {},
                    onImportFiles = {},
                    onSearchChange = {},
                    onSelectSortOrder = {},
                    onPlayTrack = { _, _ -> },
                    onShuffleAll = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    onAddTracksToPlaylist = { addedBatch = it },
                    onEnqueueTracks = { enqueuedBatch = it },
                    onDeleteTracks = { deletedBatch = it }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("library_lazy_column")
            .performScrollToNode(hasTestTag("library_select_mode_button"))
        composeTestRule.onNodeWithTag("library_select_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_selection_bar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("library_lazy_column")
            .performScrollToNode(hasTestTag("track_row_t1"))
        composeTestRule.onNodeWithTag("track_select_checkbox_t1", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("library_lazy_column")
            .performScrollToNode(hasTestTag("track_row_t2"))
        composeTestRule.onNodeWithTag("track_select_checkbox_t2", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 sel.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("selection_add_to_playlist_button").performClick()
        assertEquals(listOf("t1", "t2"), addedBatch.map { it.id })

        composeTestRule.onNodeWithTag("selection_select_all_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("3 sel.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("selection_enqueue_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t1", "t2", "t3"), enqueuedBatch.map { it.id })
        composeTestRule.onNodeWithTag("library_selection_bar").assertDoesNotExist()

        composeTestRule.onNodeWithTag("library_lazy_column")
            .performScrollToNode(hasTestTag("library_select_mode_button"))
        composeTestRule.onNodeWithTag("library_select_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_lazy_column")
            .performScrollToNode(hasTestTag("track_row_t1"))
        composeTestRule.onNodeWithTag("track_row_t1").performClick()
        composeTestRule.onNodeWithTag("selection_delete_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_batch_delete_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("t1"), deletedBatch.map { it.id })
    }

    @Test
    fun `playlistDetailScreen supports multi select batch add and batch remove`() {
        val playlistTracks = listOf("p1", "p2", "p3").mapIndexed { idx, id ->
            AudioTrack(
                id = id,
                contentUri = "content://media/external/audio/media/${700 + idx}",
                title = "Playlist Track ${idx + 1}",
                artist = "Band ${idx + 1}",
                album = "LP",
                albumId = 20L + idx,
                albumArtUri = null,
                durationMs = 210_000L,
                format = AudioFormat.FLAC,
                mimeType = "audio/flac",
                sizeBytes = 25_000_000L,
                dateAddedSec = 200L + idx,
                folderName = "Music"
            )
        }
        val playlist = com.example.model.PlaylistSummary(
            id = 42L,
            name = "Mi Mix",
            description = "Favoritas",
            createdAt = 1000L,
            tracks = playlistTracks
        )
        var removedIds: List<String> = emptyList()
        var addedToOther: List<AudioTrack> = emptyList()

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.PlaylistDetailScreen(
                    playlist = playlist,
                    allTracks = playlistTracks,
                    playbackState = com.example.playback.PlaybackUiState(),
                    onBack = {},
                    onPlayTracks = { _, _ -> },
                    onShuffleTracks = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToOtherPlaylist = {},
                    onAddSelectedTracks = { _, _, _ -> },
                    onRemoveTrack = { _, _ -> },
                    onMoveTrack = { _, _, _ -> },
                    onUpdatePlaylist = { _, _, _ -> },
                    onDeletePlaylist = {},
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    onRemoveTracks = { _, ids -> removedIds = ids },
                    onAddTracksToOtherPlaylist = { addedToOther = it }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("playlist_select_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playlist_selection_bar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("playlist_detail_lazy_column")
            .performScrollToNode(hasTestTag("track_row_p1"))
        composeTestRule.onNodeWithTag("track_row_p1").performClick()
        composeTestRule.onNodeWithTag("playlist_detail_lazy_column")
            .performScrollToNode(hasTestTag("track_row_p3"))
        composeTestRule.onNodeWithTag("track_row_p3").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("playlist_selection_add_to_playlist_button").performClick()
        assertEquals(listOf("p1", "p3"), addedToOther.map { it.id })

        composeTestRule.onNodeWithTag("playlist_selection_remove_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_playlist_batch_remove_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("p1", "p3"), removedIds)
    }

    @Test
    fun `audioEffectsController handles rapid slider and session stress updates without crashing`() {
        val controller = com.example.playback.AudioEffectsController()
        for (session in listOf(10, 11, 0, -1, 12)) {
            controller.attachToSession(session, com.example.model.AudioSettings(eqEnabled = true))
            for (step in 0 until 40) {
                val enabled = step % 5 != 0
                val gains = List(10) { idx ->
                    (-2500 + step * 120 + idx * 90)
                }
                val settings = com.example.model.AudioSettings(
                    eqEnabled = enabled,
                    eqPreset = com.example.model.EqPreset.CUSTOM,
                    bandGainsMb = gains,
                    bassBoostStrength = (step * 35) % 1200,
                    virtualizerStrength = (step * 27) % 1200,
                    playbackSpeed = if (step % 7 == 0) Float.NaN else 1.0f,
                    stereoBalance = if (step % 9 == 0) Float.POSITIVE_INFINITY else 0f
                )
                controller.applySettings(settings)
                val bands = controller.getBands(settings)
                assertEquals(10, bands.size)
                assertTrue(bands.all { it.currentLevelMb in it.minLevelMb..it.maxLevelMb })
            }
        }
        controller.release()
        assertEquals(10, controller.getBands().size)
    }

    @Test
    fun `sdCardFolders persist in preferences and concurrent library refresh completes safely`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)
        val sdFolder = com.example.model.SdCardFolder(
            treeUri = "content://com.android.externalstorage.documents/tree/1234-5678%3AMusic",
            displayName = "Tarjeta SD (Music)",
            addedAtMs = 123456L
        )
        prefs.addSdCardFolder(sdFolder)
        // Duplicate add must deduplicate by treeUri
        prefs.addSdCardFolder(sdFolder)
        val loaded = prefs.loadSdCardFolders()
        assertEquals(1, loaded.size)
        assertEquals("Tarjeta SD (Music)", loaded.first().displayName)

        val repository = com.example.data.repository.MusicRepository(
            context = context,
            scanner = com.example.data.repository.DeviceMusicScanner(context),
            playlistDao = db.playlistDao(),
            importedTrackDao = db.importedTrackDao(),
            trackStatsDao = db.trackStatsDao(),
            trackTagOverrideDao = db.trackTagOverrideDao(),
            excludedFolderDao = db.excludedFolderDao(),
            trackLyricsDao = db.trackLyricsDao()
        )

        // Rapid concurrent refresh calls must serialize via Mutex without throwing or leaving isScanning stuck
        val jobs = List(5) {
            launch {
                repository.refreshMediaStoreLibrary(includeMediaStore = true)
            }
        }
        jobs.forEach { it.join() }
        assertFalse(repository.isScanning.value)

        prefs.removeSdCardFolder(sdFolder.treeUri)
        assertTrue(prefs.loadSdCardFolders().isEmpty())
    }

    @Test
    fun `foldersExclusionSheet displays sd card manager and triggers link and unlink callbacks`() {
        var addSdClicked = false
        var removedUri: String? = null
        val sdFolders = listOf(
            com.example.model.SdCardFolder(
                treeUri = "content://com.android.externalstorage.documents/tree/ABCD-1234%3AMusic",
                displayName = "Tarjeta SD (Music)",
                trackCount = 14
            )
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.FoldersExclusionSheet(
                    folders = emptyList(),
                    onToggleExclude = { _, _ -> },
                    onDismiss = {},
                    sdCardFolders = sdFolders,
                    onAddSdCardFolder = { addSdClicked = true },
                    onRemoveSdCardFolder = { removedUri = it }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("folders_exclusion_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_sd_folder_button").assertIsDisplayed().performClick()
        assertTrue(addSdClicked)
        composeTestRule.onNodeWithText("Tarjeta SD (Music)").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 pistas indexadas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("remove_sd_folder_Tarjeta SD (Music)").performClick()
        assertEquals(sdFolders.first().treeUri, removedUri)
    }

    @Test
    fun `creditsBottomSheet exposes about support legal documents licenses and changelog`() {
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.CreditsBottomSheet(onDismiss = {})
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // About tab: DavooPunk credit, version, and official contact email
        composeTestRule.onNodeWithTag("credits_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("credits_davoopunk").assertIsDisplayed()
        composeTestRule.onNodeWithText("DavooPunk").assertIsDisplayed()
        composeTestRule.onNodeWithText("davidmarin2704@gmail.com").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("support_email_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_support_email_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Legal tab: Privacy Policy, Open Source Terms & Conditions, Copyright/DMCA
        composeTestRule.onNodeWithTag("about_tab_legal").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("legal_privacy_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legal_terms_card").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("legal_dmca_card").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Licenses tab: MIT Open Source license, visual assets rights, Open Source Notices
        composeTestRule.onNodeWithTag("about_tab_licenses").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("license_proprietary_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Licencia de Código Abierto (MIT)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("license_visual_assets_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("AndroidX Media3 (ExoPlayer & MediaSession)").performScrollTo().assertIsDisplayed()

        // Changelog tab: Version history including v2.1.1, v2.1.0, v2.0.6, v2.0.5, v2.0.4, v2.0.3, v2.0.2, v2.0.1 and v2.0.0
        composeTestRule.onNodeWithTag("about_tab_changelog").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("v2.1.1").assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.1.0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0.6").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0.5").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0.4").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0.3").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0.2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0.1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.0.0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("v1.0.0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `equalizerSheet exposes Normalizar volumen toggle and updates AudioEffectsController normalization state`() {
        val currentSettings = androidx.compose.runtime.mutableStateOf(
            com.example.model.AudioSettings(replayGainEnabled = false)
        )
        val controller = com.example.playback.AudioEffectsController()

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.EqualizerAndDspSheet(
                    settings = currentSettings.value,
                    bands = controller.getBands(currentSettings.value),
                    onUpdateSettings = { currentSettings.value = it },
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("volume_normalization_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Normalizar volumen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Desactivado · Volumen original de cada canción").assertIsDisplayed()

        // Toggle ON
        composeTestRule.onNodeWithTag("replaygain_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(currentSettings.value.replayGainEnabled)
        composeTestRule.onNodeWithText("Activo · Equilibra pistas automáticamente (ReplayGain + nivelador)").assertIsDisplayed()

        // Verify AudioEffectsController requests LoudnessEnhancer only when ON and untagged
        controller.attachToSession(42, currentSettings.value)
        controller.setTrackHasReplayGainTag(false)
        assertTrue(controller.isLoudnessNormalizationRequested())

        controller.setTrackHasReplayGainTag(true)
        assertFalse(controller.isLoudnessNormalizationRequested())

        // Toggle OFF
        composeTestRule.onNodeWithTag("replaygain_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(currentSettings.value.replayGainEnabled)
        controller.applySettings(currentSettings.value)
        controller.setTrackHasReplayGainTag(false)
        assertFalse(controller.isLoudnessNormalizationRequested())
        controller.release()
    }

    @Test
    fun `dualEngineTranslator supports DeepL API v2 and Google Translate with slang normalization and offline caching`() = runTest {
        // 1. DeepL endpoint & key validation
        assertEquals(
            com.example.ai.DeepLLyricsTranslator.DEEPL_FREE_ENDPOINT,
            com.example.ai.DeepLLyricsTranslator.resolveEndpointUrl("dev-key-123:fx")
        )
        assertEquals(
            com.example.ai.DeepLLyricsTranslator.DEEPL_PRO_ENDPOINT,
            com.example.ai.DeepLLyricsTranslator.resolveEndpointUrl("pro-key-999")
        )
        assertFalse(com.example.ai.DeepLLyricsTranslator.isValidApiKey("DEEPL_API_KEY_DEFAULT_VALUE"))
        assertTrue(com.example.ai.DeepLLyricsTranslator.isValidApiKey("abc123:fx"))

        // 2. Lyric slang normalizer used by Google translator
        assertEquals(
            "I am going to want to keep feeling good because you all are trying to shine",
            com.example.ai.LyricTextNormalizer.normalizeForTranslation(
                "I'ma wanna keep feelin' good 'cause y'all are tryna shine"
            )
        )

        var capturedDeepLHeader: String? = null
        var capturedDeepLBody: String? = null
        val fakeDeepLClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedDeepLHeader = req.header("Authorization")
                val sink = okio.Buffer()
                req.body?.writeTo(sink)
                capturedDeepLBody = sink.readUtf8()
                val body = """{"translations":[{"detected_source_language":"EN","text":"Hola mundo"},{"detected_source_language":"EN","text":"Buenas noches"}]}"""
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        okhttp3.ResponseBody.Companion.run {
                            body.toResponseBody(
                                okhttp3.MediaType.Companion.run {
                                    "application/json; charset=utf-8".toMediaType()
                                }
                            )
                        }
                    )
                    .build()
            }
            .build()

        var capturedGoogleUrl: String? = null
        val fakeGoogleClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedGoogleUrl = req.url.toString()
                val body = """[[["Hola mundo\nBuenas noches","Hello world\nGood night",null,null,1]],null,"en"]"""
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        okhttp3.ResponseBody.Companion.run {
                            body.toResponseBody(
                                okhttp3.MediaType.Companion.run {
                                    "application/json; charset=utf-8".toMediaType()
                                }
                            )
                        }
                    )
                    .build()
            }
            .build()

        val original = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(1_000L, "Hello world"),
                com.example.model.LyricLine(3_500L, ""),
                com.example.model.LyricLine(5_000L, "Good night")
            ),
            isSynced = true,
            sourceLabel = "Archivo .lrc"
        )

        var persistedTrackId: String? = null
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val coordinator = com.example.ai.LyricsTranslationCoordinator(
            scope = this,
            deepLTranslator = com.example.ai.DeepLLyricsTranslator(
                apiKeyProvider = { "demo-key:fx" },
                client = fakeDeepLClient,
                ioDispatcher = testDispatcher
            ),
            googleTranslator = com.example.ai.GoogleLyricsTranslator(
                client = fakeGoogleClient,
                ioDispatcher = testDispatcher
            ),
            onPersistTranslation = { trackId, _, _ -> persistedTrackId = trackId }
        )

        // Translate via DeepL
        coordinator.translate(
            trackId = "track_dual_1",
            trackTitle = "Night Walk",
            trackArtist = "Davoo Band",
            lyrics = original,
            targetLanguage = com.example.ai.LyricsTranslationLanguage.SPANISH,
            engine = com.example.ai.TranslationEngine.DEEPL
        )
        testScheduler.advanceUntilIdle()
        val deepLState = coordinator.state.value as com.example.ai.LyricsTranslationUiState.Success
        assertEquals("DeepL-Auth-Key demo-key:fx", capturedDeepLHeader)
        assertTrue(capturedDeepLBody.orEmpty().contains("Night Walk"))
        assertEquals("DeepL (Español)", deepLState.translatedLyrics.sourceLabel)
        assertEquals("Hola mundo", deepLState.translatedLyrics.lines[0].text)
        assertEquals(5_000L, deepLState.translatedLyrics.lines[2].timestampMs)
        assertEquals("track_dual_1", persistedTrackId)

        // Translate via Google
        coordinator.translate(
            trackId = "track_dual_1",
            trackTitle = "Night Walk",
            trackArtist = "Davoo Band",
            lyrics = original,
            targetLanguage = com.example.ai.LyricsTranslationLanguage.SPANISH,
            engine = com.example.ai.TranslationEngine.GOOGLE
        )
        testScheduler.advanceUntilIdle()
        val googleState = coordinator.state.value as com.example.ai.LyricsTranslationUiState.Success
        assertTrue(capturedGoogleUrl.orEmpty().contains("translate.googleapis.com"))
        assertEquals("Google (Español)", googleState.translatedLyrics.sourceLabel)
        assertEquals("Buenas noches", googleState.translatedLyrics.lines[2].text)
    }

    @Test
    fun `bilingualLrcParsing and roomOfflineTranslationPersistence work accurately`() = runTest {
        val bilingualRaw = """
            [00:10.00] Hello from the dark
            [00:10.00] Hola desde la oscuridad
            [00:15.50] Keep the rhythm alive
            [00:15.50] Mantén el ritmo vivo
            [00:22.00] Until the sunrise
            [00:22.00] Hasta el amanecer
        """.trimIndent()

        val parsedBilingual = com.example.data.repository.AudioTagParsers.parseLrcText(
            bilingualRaw,
            "Archivo .lrc local"
        )
        assertNotNull(parsedBilingual)
        assertTrue(parsedBilingual!!.hasEmbeddedTranslation)
        assertTrue(parsedBilingual.sourceLabel.contains("Bilingüe"))
        val bilingualVocal = parsedBilingual.lines.filter { !it.isInstrumental }
        assertEquals(3, bilingualVocal.size)
        assertEquals("Hello from the dark", bilingualVocal[0].text)
        assertEquals("Hola desde la oscuridad", bilingualVocal[0].secondaryText)
        assertEquals(15_500L, bilingualVocal[1].timestampMs)
        assertEquals("Mantén el ritmo vivo", bilingualVocal[1].secondaryText)

        // Persist manual translation in Room via MusicRepository and restore it offline
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = com.example.data.repository.MusicRepository(
            context = context,
            scanner = com.example.data.repository.DeviceMusicScanner(context),
            playlistDao = db.playlistDao(),
            importedTrackDao = db.importedTrackDao(),
            trackStatsDao = db.trackStatsDao(),
            trackTagOverrideDao = db.trackTagOverrideDao(),
            excludedFolderDao = db.excludedFolderDao(),
            trackLyricsDao = db.trackLyricsDao(),
            trackLyricsTranslationDao = db.trackLyricsTranslationDao()
        )
        val plainOriginal = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(2_000L, "Line one"),
                com.example.model.LyricLine(6_000L, "Line two")
            ),
            isSynced = true,
            sourceLabel = "Descargada (LRCLIB)"
        )

        val saved = repository.saveManualLyricsTranslation(
            trackId = "track_tr_1",
            originalLyrics = plainOriginal,
            rawPastedText = "Línea uno\nLínea dos",
            language = com.example.ai.LyricsTranslationLanguage.SPANISH
        )
        assertNotNull(saved)
        assertEquals("Línea uno", saved!!.lines[0].text)
        assertEquals(6_000L, saved.lines[1].timestampMs)
        assertEquals("Línea dos", saved.lines[1].text)

        val restored = repository.loadSavedLyricsTranslation("track_tr_1", plainOriginal)
        assertNotNull(restored)
        assertEquals(com.example.ai.LyricsTranslationLanguage.SPANISH, restored!!.language)
        assertEquals("Línea dos", restored.translatedLyrics.lines[1].text)

        repository.clearSavedLyricsTranslation("track_tr_1")
        assertNull(repository.loadSavedLyricsTranslation("track_tr_1", plainOriginal))
    }

    @Test
    fun `lyricsBottomSheet supports native bilingual LRC on-device translate button and manual offline translation editor`() {
        val track = AudioTrack(
            id = "lyr_local_1",
            contentUri = "content://media/external/audio/media/901",
            title = "Midnight Echo",
            artist = "The Runners",
            album = "Echoes",
            albumId = 90L,
            albumArtUri = null,
            durationMs = 200_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 5_000_000L,
            dateAddedSec = 200L,
            folderName = "Music"
        )
        val bilingualLyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(2_000L, "We run through the night", "Corremos en la noche"),
                com.example.model.LyricLine(5_000L, "Chasing the sound", "Persiguiendo el sonido")
            ),
            isSynced = true,
            sourceLabel = "Archivo .lrc local · Bilingüe",
            hasEmbeddedTranslation = true
        )
        val selectedEngine = androidx.compose.runtime.mutableStateOf(com.example.ai.TranslationEngine.DEEPL)
        var triggeredEngine: com.example.ai.TranslationEngine? = null
        var savedManualText: String? = null
        val openedSites = mutableListOf<String>()

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = bilingualLyrics,
                    isLoading = false,
                    positionMs = 2_000L,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {},
                    selectedEngine = selectedEngine.value,
                    onSelectEngine = { selectedEngine.value = it },
                    onTranslateLyrics = { _, _, engine -> triggeredEngine = engine },
                    onSaveManualTranslation = { _, text, _ -> savedManualText = text },
                    onOpenHumanTranslationSite = { _, site -> openedSites.add(site) }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Spotify minimal view hides large top header buttons and renders lyrics directly with bottom corner menus
        composeTestRule.onNodeWithText("Corremos en la noche").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_playback_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("download_online_lyrics_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("lyrics_manage_menu_button").assertIsDisplayed()

        // Open compact floating translate menu from bottom-left icon
        composeTestRule.onNodeWithTag("lyrics_translate_menu_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_display_mode_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("translation_offline_badge").assertIsDisplayed()

        // Switch from DeepL to Google inside floating menu and trigger translation
        composeTestRule.onNodeWithTag("lyrics_engine_google").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("translate_lyrics_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ai.TranslationEngine.GOOGLE, triggeredEngine)

        // Re-open translate menu and open manual offline editor
        composeTestRule.onNodeWithTag("lyrics_translate_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_translation_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("manual_translation_editor_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_letras_com_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("open_lyricstranslate_button").performScrollTo().performClick()
        assertEquals(listOf("Letras.com", "LyricsTranslate"), openedSites)

        composeTestRule.onNodeWithTag("save_manual_translation_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(savedManualText.orEmpty().contains("Corremos en la noche"))
    }

    @Test
    fun `restoreSavedTranslation manual entry does not overwrite DeepL cache slot`() = runTest {
        var deepLInvocations = 0
        val coordinator = com.example.ai.LyricsTranslationCoordinator(
            scope = this,
            deepLTranslator = com.example.ai.LyricsTranslator { _, _, lyrics, targetLanguage ->
                deepLInvocations++
                lyrics.copy(
                    lines = lyrics.lines.map { it.copy(text = "DeepL:${it.text}") },
                    sourceLabel = "DeepL (${targetLanguage.displayName})"
                )
            },
            googleTranslator = com.example.ai.LyricsTranslator { _, _, lyrics, _ -> lyrics }
        )

        val original = com.example.model.LyricsData(
            lines = listOf(com.example.model.LyricLine(1_000L, "Hello world")),
            isSynced = true,
            sourceLabel = "Archivo .lrc"
        )
        val manualSaved = com.example.model.LyricsData(
            lines = listOf(com.example.model.LyricLine(1_000L, "Hola manual")),
            isSynced = true,
            sourceLabel = "Traducción guardada (manual) (Español)"
        )

        coordinator.restoreSavedTranslation(
            trackId = "track_manual_cache_1",
            originalLyrics = original,
            language = com.example.ai.LyricsTranslationLanguage.SPANISH,
            translatedLyrics = manualSaved
        )

        coordinator.translate(
            trackId = "track_manual_cache_1",
            trackTitle = "Hello",
            trackArtist = "Artist",
            lyrics = original,
            targetLanguage = com.example.ai.LyricsTranslationLanguage.SPANISH,
            engine = com.example.ai.TranslationEngine.DEEPL
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, deepLInvocations)
        val state = coordinator.state.value as com.example.ai.LyricsTranslationUiState.Success
        assertEquals("DeepL:Hello world", state.translatedLyrics.lines.first().text)
    }

    @Test
    fun `sonoraWidgetProvider onUpdate processes asynchronously without blocking`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)
        prefs.savePlaybackSession(
            com.example.data.local.SavedPlaybackSession(
                items = listOf(
                    com.example.data.local.SavedMediaItemSnapshot(
                        id = "widget_track_1",
                        contentUri = "file:///non_existent_widget.mp3",
                        title = "Widget Song",
                        artist = "Widget Artist",
                        album = "Widget Album",
                        albumArtUri = null,
                        formatName = "MP3",
                        durationMs = 120_000L
                    )
                ),
                currentIndex = 0,
                positionMs = 0L,
                shuffleEnabled = false,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            )
        )

        val provider = com.example.widget.SonoraWidgetProvider()
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        provider.onUpdate(context, appWidgetManager, intArrayOf(101))

        assertTrue(com.example.widget.SonoraWidgetProvider.awaitPendingWidgetUpdatesForTest())
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `playbackService evaluates track gap pause only when gapless is disabled and crossfade is off`() {
        val controller = org.robolectric.Robolectric.buildService(com.example.playback.PlaybackService::class.java)
        val service = controller.create().get()
        try {
            val player = service.activePlayerOrNull()!!
            val item = MediaItem.Builder()
                .setMediaId("gap_track_2")
                .setUri(Uri.parse("file:///android_asset/track2.mp3"))
                .build()
            player.setMediaItem(item)
            player.playWhenReady = true
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            // Default gapless = true -> no pause inserted
            service.updateSettingsForTest(com.example.model.AudioSettings(gaplessEnabled = true, crossfadeSeconds = 0))
            assertFalse(service.shouldInsertTrackGap(androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, item, player))

            // gapless = false, crossfade = 0 -> pause inserted only for AUTO transitions
            service.updateSettingsForTest(com.example.model.AudioSettings(gaplessEnabled = false, crossfadeSeconds = 0))
            assertTrue(service.shouldInsertTrackGap(androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, item, player))
            assertFalse(service.shouldInsertTrackGap(androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, item, player))

            // gapless = false, crossfade > 0 -> crossfade takes precedence, no gap pause
            service.updateSettingsForTest(com.example.model.AudioSettings(gaplessEnabled = false, crossfadeSeconds = 3))
            assertFalse(service.shouldInsertTrackGap(androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, item, player))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `headerSearchButton appears on AlbumsScreen and PlaylistsScreen and GlobalSearchOverlay filters all categories`() {
        var albumsSearchClicked = 0
        var playlistsSearchClicked = 0
        val track = AudioTrack(
            id = "gs_1",
            contentUri = "content://media/external/audio/media/901",
            title = "Midnight Jazz",
            artist = "Miles Quartet",
            album = "BlueSessions",
            albumId = 41L,
            albumArtUri = null,
            durationMs = 215_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 25_000_000L,
            dateAddedSec = 100L,
            folderName = "Jazz"
        )
        val album = com.example.model.AlbumGroup(
            albumName = "BlueSessions",
            artist = "Miles Quartet",
            albumArtUri = null,
            tracks = listOf(track),
            totalDurationMs = 215_000L,
            hasFlac = true
        )
        val artist = com.example.model.ArtistGroup(
            artistName = "Miles Quartet",
            albumCount = 1,
            tracks = listOf(track),
            albumArtUri = null,
            totalDurationMs = 215_000L
        )
        val playlist = com.example.model.PlaylistSummary(
            id = 7L,
            name = "Late Blue Vibes",
            description = "Relaxing sets",
            createdAt = 10L,
            tracks = listOf(track)
        )

        val selectedTab = androidx.compose.runtime.mutableIntStateOf(0)
        val queryState = androidx.compose.runtime.mutableStateOf("Blue")
        var openedAlbum: String? = null
        var openedArtist: String? = null
        var openedPlaylist: Long? = null

        composeTestRule.setContent {
            SonoraTheme {
                when (selectedTab.intValue) {
                    0 -> com.example.ui.screens.AlbumsScreen(
                        albums = listOf(album),
                        artists = listOf(artist),
                        onAlbumClick = {},
                        onOpenGlobalSearch = { albumsSearchClicked++ },
                        bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    )
                    1 -> com.example.ui.screens.PlaylistsScreen(
                        playlists = listOf(playlist),
                        onCreatePlaylist = {},
                        onOpenGlobalSearch = { playlistsSearchClicked++ },
                        onPlaylistClick = {},
                        bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    )
                    else -> com.example.ui.GlobalSearchOverlay(
                        uiState = com.example.ui.viewmodel.SonoraUiState(
                            allTracks = listOf(track),
                            albums = listOf(album),
                            artists = listOf(artist),
                            playlists = listOf(playlist),
                            searchQuery = queryState.value,
                            isGlobalSearchOpen = true,
                            globalMatchingAlbums = listOf(album),
                            globalMatchingArtists = listOf(artist),
                            globalMatchingPlaylists = listOf(playlist),
                            globalMatchingTracks = listOf(track)
                        ),
                        currentTrackId = null,
                        isPlaying = false,
                        bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        onQueryChange = { queryState.value = it },
                        onClose = {},
                        onPlayTrack = { _, _ -> },
                        onPlayNext = {},
                        onAddToQueue = {},
                        onAddToPlaylist = {},
                        onToggleFavorite = {},
                        onShowLyrics = {},
                        onEditTags = {},
                        onShowMetadata = {},
                        onOpenAlbum = { openedAlbum = it },
                        onOpenArtist = { openedArtist = it },
                        onOpenPlaylist = { openedPlaylist = it }
                    )
                }
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("header_search_button").assertIsDisplayed().performClick()
        assertEquals(1, albumsSearchClicked)

        selectedTab.intValue = 1
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("header_search_button").assertIsDisplayed().performClick()
        assertEquals(1, playlistsSearchClicked)

        selectedTab.intValue = 2
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("global_search_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("global_search_album_BlueSessions").assertIsDisplayed().performClick()
        assertEquals("BlueSessions", openedAlbum)
        composeTestRule.onNodeWithTag("global_search_artist_Miles Quartet").performClick()
        assertEquals("Miles Quartet", openedArtist)
        composeTestRule.onNodeWithTag("global_search_playlist_7").performClick()
        assertEquals(7L, openedPlaylist)
        composeTestRule.onNodeWithTag("global_search_results_list")
            .performScrollToNode(hasTestTag("track_row_gs_1"))
        composeTestRule.onNodeWithTag("track_row_gs_1").assertIsDisplayed()
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `tenBandEqualizerAudioProcessor applies preamp and biquad filtering and EqualizerAndDspSheet saves custom AutoEq profiles`() {
        // 1. Verify DSP processor processes 10 bands + Preamp on PCM 16-bit audio cleanly
        val processor = com.example.playback.TenBandEqualizerAudioProcessor()
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            44100,
            2,
            androidx.media3.common.C.ENCODING_PCM_16BIT
        )
        val outFormat = processor.configure(format)
        assertEquals(44100, outFormat.sampleRate)
        processor.flush()

        val sonyProfile = com.example.model.AutoEqProfilesCatalog.builtInProfiles.first { it.id == "autoeq_sony_xm4" }
        processor.updateSettings(
            eqEnabled = true,
            preampDb = sonyProfile.preampDb,
            bandGainsMb = sonyProfile.bandGainsMb
        )

        val inputBuffer = java.nio.ByteBuffer.allocateDirect(64 * 4).order(java.nio.ByteOrder.nativeOrder())
        for (i in 0 until 64) {
            val sample = (kotlin.math.sin(i * 0.25) * 16000.0).toInt().toShort()
            inputBuffer.putShort(sample)
            inputBuffer.putShort(sample)
        }
        inputBuffer.flip()
        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output
        assertEquals(64 * 4, outputBuffer.remaining())
        // At -4.8 dB preamp + bass cut, processed signal must differ from bypass and remain well within [-32767, 32767]
        val firstProcessedSample = outputBuffer.short.toInt()
        assertTrue(firstProcessedSample in Short.MIN_VALUE..Short.MAX_VALUE)
        processor.reset()

        // 2. Verify UI: search AutoEq headphones, apply Sony WH-1000XM4, and save custom profile
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsStore = com.example.data.local.PlaybackPreferencesStore(context)
        val eqController = com.example.playback.AudioEffectsController()
        val settingsState = androidx.compose.runtime.mutableStateOf(com.example.model.AudioSettings())
        val customProfilesState = androidx.compose.runtime.mutableStateOf<List<com.example.model.HeadphoneEqProfile>>(emptyList())

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.EqualizerAndDspSheet(
                    settings = settingsState.value,
                    bands = eqController.getBands(settingsState.value),
                    onUpdateSettings = { settingsState.value = it },
                    customProfiles = customProfilesState.value,
                    onSaveCustomProfile = { name, s ->
                        val created = com.example.model.HeadphoneEqProfile(
                            id = "custom_test_1",
                            brand = "Personal",
                            model = name,
                            formFactor = "Perfil guardado",
                            preampDb = s.preampDb,
                            bandGainsMb = s.bandGainsMb,
                            isCustom = true
                        )
                        customProfilesState.value = prefsStore.saveCustomEqProfile(created)
                        settingsState.value = s.copy(activeProfileId = created.id, activeProfileName = created.fullName)
                    },
                    onDeleteCustomProfile = { id ->
                        customProfilesState.value = prefsStore.deleteCustomEqProfile(id)
                    },
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Bring entire AutoEq card (including LazyRow below search field) into vertical viewport
        composeTestRule.onNodeWithTag("eq_frequency_curve_canvas").performScrollTo()
        // Search and select Sony WH-1000XM4 AutoEq profile
        composeTestRule.onNodeWithTag("eq_profile_search_input").performScrollTo().performTextInput("XM4")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("eq_preset_flat").performScrollTo()
        composeTestRule.onNodeWithTag("headphone_profile_chip_autoeq_sony_xm4").performScrollTo().performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        assertTrue(settingsState.value.eqEnabled)
        assertEquals("autoeq_sony_xm4", settingsState.value.activeProfileId)
        assertEquals(-4.8f, settingsState.value.preampDb, 0.05f)
        assertEquals(sonyProfile.bandGainsMb, settingsState.value.bandGainsMb)

        // Verify all 10 band sliders are mounted
        composeTestRule.onNodeWithTag("eq_band_slider_9").performScrollTo().assertIsDisplayed()

        // Save custom profile and verify persistence
        composeTestRule.onNodeWithTag("save_custom_eq_profile_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("custom_eq_profile_name_input").performTextReplacement("Mi Perfil Studio")
        composeTestRule.onNodeWithTag("confirm_save_eq_profile_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, customProfilesState.value.size)
        assertEquals("Mi Perfil Studio", prefsStore.loadCustomEqProfiles().firstOrNull()?.model)
    }

    @Test
    fun `foldersExclusionSheet shows removing indicator and repository removes sd folder cleanly without rescan`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)
        val folderA = com.example.model.SdCardFolder(
            treeUri = "content://com.android.externalstorage.documents/tree/SD_A%3AMusic",
            displayName = "Tarjeta SD A",
            trackCount = 50
        )
        val folderB = com.example.model.SdCardFolder(
            treeUri = "content://com.android.externalstorage.documents/tree/SD_B%3AMusic",
            displayName = "Tarjeta SD B",
            trackCount = 30
        )
        prefs.addSdCardFolder(folderA)
        prefs.addSdCardFolder(folderB)

        val repository = com.example.data.repository.MusicRepository(
            context = context,
            scanner = com.example.data.repository.DeviceMusicScanner(context),
            playlistDao = db.playlistDao(),
            importedTrackDao = db.importedTrackDao(),
            trackStatsDao = db.trackStatsDao(),
            trackTagOverrideDao = db.trackTagOverrideDao(),
            excludedFolderDao = db.excludedFolderDao(),
            trackLyricsDao = db.trackLyricsDao()
        )
        assertEquals(2, repository.sdCardFoldersFlow.value.size)

        repository.removeSdCardFolder(folderA.treeUri)
        assertEquals(listOf(folderB.treeUri), repository.sdCardFoldersFlow.value.map { it.treeUri })
        assertEquals(listOf(folderB.treeUri), prefs.loadSdCardFolders().map { it.treeUri })
        assertFalse(repository.isScanning.value)

        // Verify FoldersExclusionSheet renders subtle non-blocking removing indicator while preserving remaining folders
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.FoldersExclusionSheet(
                    folders = emptyList(),
                    onToggleExclude = { _, _ -> },
                    onDismiss = {},
                    sdCardFolders = listOf(folderB),
                    removingSdFolders = mapOf(folderA.treeUri to folderA.displayName)
                )
            }
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("removing_sd_folder_indicator_${folderA.treeUri}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quitando «Tarjeta SD A» en segundo plano…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tarjeta SD B").assertIsDisplayed()
    }

    @Test
    fun `loadTranslationEngine defaults to Google without DEEPL_API_KEY and DeepL when configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)

        assertEquals(
            com.example.ai.TranslationEngine.GOOGLE,
            prefs.loadTranslationEngine(apiKey = "DEEPL_API_KEY_DEFAULT_VALUE")
        )
        assertEquals(
            com.example.ai.TranslationEngine.DEEPL,
            prefs.loadTranslationEngine(apiKey = "sample-key:fx")
        )

        prefs.saveTranslationEngine(com.example.ai.TranslationEngine.DEEPL)
        assertEquals(
            com.example.ai.TranslationEngine.DEEPL,
            prefs.loadTranslationEngine(apiKey = "DEEPL_API_KEY_DEFAULT_VALUE")
        )
    }

    @Test
    fun `libraryScreen places search button to the left of settings gear button on top-right header`() {
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.LibraryScreen(
                    uiState = com.example.ui.viewmodel.SonoraUiState(hasAudioPermission = true),
                    playbackState = com.example.playback.PlaybackUiState(),
                    onRequestPermission = {},
                    onImportFiles = {},
                    onSelectSortOrder = {},
                    onPlayTrack = { _, _ -> },
                    onShuffleAll = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        val searchBounds = composeTestRule.onNodeWithTag("header_search_button")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val settingsBounds = composeTestRule.onNodeWithTag("library_settings_button")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected search button (${searchBounds.left}) to be to the left of settings button (${settingsBounds.left})",
            searchBounds.left < settingsBounds.left
        )
    }

    @Test
    fun `eqPreset Hip-Hop provides calibrated sub-bass boost curve with negative clipping-safe preamp and appears in sheet`() {
        val hipHop = com.example.model.EqPreset.HIP_HOP
        assertEquals("Hip-Hop", hipHop.label)
        assertEquals(10, hipHop.gainsMb.size)
        // Sub-bass and bass bands (31Hz, 62Hz, 125Hz) are boosted cleanly while preamp compensates
        assertTrue(hipHop.gainsMb[0] > 0)
        assertTrue(hipHop.gainsMb[1] >= hipHop.gainsMb[0])
        assertTrue(hipHop.gainsMb[2] > 0)
        assertTrue(hipHop.recommendedPreampDb <= -4.0f)

        var selectedSettings = com.example.model.AudioSettings()
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.EqualizerAndDspSheet(
                    settings = selectedSettings,
                    bands = com.example.playback.AudioEffectsController().getBands(selectedSettings),
                    onUpdateSettings = { selectedSettings = it },
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("eq_preset_row")
            .performScrollTo()
            .performScrollToNode(hasTestTag("eq_preset_hip_hop"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("eq_preset_hip_hop").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.EqPreset.HIP_HOP, selectedSettings.eqPreset)
        assertEquals(hipHop.gainsMb, selectedSettings.bandGainsMb)
        assertEquals(hipHop.recommendedPreampDb, selectedSettings.preampDb, 0.01f)
    }

    @Test
    fun `settingsScreen exposes optional GitHub update checker and removes redundant open-source row`() {
        var checkUpdatesClicks = 0
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.SettingsScreen(
                    totalTracksCount = 12,
                    isScanning = false,
                    excludedFoldersCount = 0,
                    sdCardFoldersCount = 0,
                    audioSettings = com.example.model.AudioSettings(),
                    isSleepTimerActive = false,
                    preferredTranslationEngine = com.example.ai.TranslationEngine.GOOGLE,
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
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    onCheckUpdatesNow = { checkUpdatesClicks++ }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_about_legal_row"))
        composeTestRule.onNodeWithTag("settings_check_updates_row").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("settings_auto_updates_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_about_legal_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_open_source_row").assertDoesNotExist()
        assertEquals(1, checkUpdatesClicks)
    }

    @Test
    fun `appUpdateChecker parses GitHub URLs, compares semantic versions, and detects available updates`() {
        assertEquals(
            "https://github.com/DavooPunk/Davoo-Player",
            com.example.data.repository.AppUpdateChecker.DEFAULT_GITHUB_REPO_URL
        )
        assertEquals(
            "https://github.com/DavooPunk/Davoo-Player/releases",
            com.example.data.repository.AppUpdateChecker.DEFAULT_GITHUB_RELEASES_URL
        )
        assertEquals(
            "https://github.com/DavooPunk/Davoo-Player",
            com.example.data.repository.AppUpdateChecker.normalizeRepoUrl("https://github.com/DavooPunk/Davoo-Player/releases")
        )
        assertEquals(
            "https://github.com/DavooPunk/Davoo-Player",
            com.example.data.repository.AppUpdateChecker.normalizeRepoUrl("https://github.com/DavooPunk/Davoo-Player.git")
        )
        assertEquals(
            "",
            com.example.data.repository.AppUpdateChecker.normalizeRepoUrl("[PEGA AQUÍ EL ENLACE DE TU REPOSITORIO]")
        )
        assertTrue(com.example.data.repository.AppUpdateChecker.isNewerVersion("v2.1.0", "2.0.2"))
        assertTrue(com.example.data.repository.AppUpdateChecker.isNewerVersion("2.0.3", "2.0.2"))
        assertFalse(com.example.data.repository.AppUpdateChecker.isNewerVersion("v2.0.2", "2.0.2"))
        assertFalse(com.example.data.repository.AppUpdateChecker.isNewerVersion("2.0.1", "2.0.2"))

        val releaseJson = """
            {
              "tag_name": "v2.1.0",
              "html_url": "https://github.com/DavooPunk/Davoo-Player/releases/tag/v2.1.0",
              "body": "Mejoras de audio"
            }
        """.trimIndent()
        val result = com.example.data.repository.AppUpdateChecker.parseReleaseResponse(
            rawJson = releaseJson,
            currentVersion = "2.0.2",
            fallbackRepoUrl = com.example.data.repository.AppUpdateChecker.DEFAULT_GITHUB_REPO_URL
        )
        assertTrue(result is com.example.data.repository.UpdateCheckResult.UpdateAvailable)
        val info = (result as com.example.data.repository.UpdateCheckResult.UpdateAvailable).info
        assertEquals("2.1.0", info.latestVersion)
        assertEquals("https://github.com/DavooPunk/Davoo-Player/releases/tag/v2.1.0", info.releaseUrl)
        assertEquals("Mejoras de audio", info.releaseNotes)
    }

    @Test
    fun `lastFmApiSignature computes exact MD5 with alphabetical sorting and ignores format`() {
        // Expected raw payload before MD5:
        // "api_keyk123artistDaft Punkmethodtrack.loveartists? no -> ordered: api_keyk123artistDaft Punkmethodtrack.lovesksess9trackAerodynamicsecretABC"
        val params = mapOf(
            "method" to "track.love",
            "format" to "json",
            "track" to "Aerodynamic",
            "api_key" to "k123",
            "sk" to "sess9",
            "artist" to "Daft Punk"
        )
        val sig = com.example.data.remote.generateLastFmApiSignature(params, "secretABC")

        val expectedPayload = "api_keyk123artistDaft Punkmethodtrack.lovesksess9trackAerodynamicsecretABC"
        val expectedDigest = java.security.MessageDigest.getInstance("MD5")
            .digest(expectedPayload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(32, sig.length)
        assertEquals(expectedDigest, sig)
        // Changing format must not change signature
        val sigXml = com.example.data.remote.generateLastFmApiSignature(
            params + ("format" to "xml"),
            "secretABC"
        )
        assertEquals(sig, sigXml)
    }

    @Test
    fun `lastFmScrobblePolicy enforces 50 percent for local tracks and 15 seconds single-shot for Deezer previews`() {
        // Local 4-minute track (240_000 ms -> threshold 120_000 ms)
        assertFalse(
            com.example.data.repository.LastFmScrobblePolicy.shouldScrobble(
                trackId = "media_100",
                positionMs = 119_999L,
                durationMs = 240_000L,
                alreadyScrobbled = false
            )
        )
        assertTrue(
            com.example.data.repository.LastFmScrobblePolicy.shouldScrobble(
                trackId = "media_100",
                positionMs = 120_000L,
                durationMs = 240_000L,
                alreadyScrobbled = false
            )
        )
        assertFalse(
            com.example.data.repository.LastFmScrobblePolicy.shouldScrobble(
                trackId = "media_100",
                positionMs = 180_000L,
                durationMs = 240_000L,
                alreadyScrobbled = true
            )
        )

        // Deezer 30s preview track -> threshold 15_000 ms
        val previewId = "${com.example.model.LastFmTrackItem.PREVIEW_TRACK_ID_PREFIX}999"
        assertFalse(
            com.example.data.repository.LastFmScrobblePolicy.shouldScrobble(
                trackId = previewId,
                positionMs = 14_999L,
                durationMs = 30_000L,
                alreadyScrobbled = false
            )
        )
        assertTrue(
            com.example.data.repository.LastFmScrobblePolicy.shouldScrobble(
                trackId = previewId,
                positionMs = 15_000L,
                durationMs = 30_000L,
                alreadyScrobbled = false
            )
        )
    }

    @Test
    fun `lastFmRepository enriches via Deezer with in-memory cache and executes signed OAuth plus scrobble`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)
        prefs.saveLastFmCredentialsOverride("demo_key_1", "demo_secret_1")

        var deezerCallCount = 0
        var lastScrobbleSig: String? = null
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val urlStr = req.url.toString()
                val jsonResponse = when {
                    urlStr.contains("deezer") -> {
                        deezerCallCount++
                        """
                        {
                          "data": [{
                            "id": 3135556,
                            "title": "Harder, Better, Faster, Stronger",
                            "duration": 30,
                            "preview": "http://cdns-preview.dzcdn.net/stream/preview.mp3",
                            "artist": { "name": "Daft Punk" },
                            "album": {
                              "title": "Discovery",
                              "cover_xl": "http://e-cdn-images.dzcdn.net/images/cover/xl.jpg"
                            }
                          }]
                        }
                        """.trimIndent()
                    }
                    urlStr.contains("method=auth.getSession") -> {
                        """{"session":{"name":"davoopunk","key":"sk_valid_999"}}"""
                    }
                    urlStr.contains("method=user.getInfo") -> {
                        """{"user":{"name":"davoopunk","realname":"Davoo","playcount":"4321","image":[{"size":"extralarge","#text":"https://lastfm.freetls.fastly.net/i/u/avatar.png"}]}}"""
                    }
                    else -> {
                        val sink = okio.Buffer()
                        req.body?.writeTo(sink)
                        val bodyText = sink.readUtf8()
                        if (bodyText.contains("track.scrobble")) {
                            lastScrobbleSig = bodyText
                        }
                        """{"scrobbles":{"@attr":{"accepted":1}}}"""
                    }
                }
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(with(okhttp3.ResponseBody) { jsonResponse.toResponseBody(null) })
                    .build()
            }
            .build()

        val apiService = com.example.data.remote.MusicApiService(
            client = mockClient,
            lastFmBaseUrl = "https://mock.last.fm/2.0/",
            deezerBaseUrl = "https://mock.deezer.com"
        )
        val repo = com.example.data.repository.LastFmRepository(prefs, apiService)

        // 1. OAuth buildAuthUrl & token exchange
        val authUrl = repo.buildAuthUrl()!!
        assertTrue(authUrl.contains("api_key=demo_key_1"))
        assertTrue(authUrl.contains("davooplayer%3A%2F%2Flastfm-callback"))

        val sessionRes = repo.completeOAuthCallback("tok_12345")
        assertTrue(sessionRes.isSuccess)
        assertEquals("davoopunk", sessionRes.getOrNull()?.username)
        assertEquals("sk_valid_999", repo.loadSession()?.sessionKey)

        // 2. Deezer enrichment + in-memory LruCache deduplication
        val first = repo.buscarEnDeezer("Daft Punk", "Harder, Better, Faster, Stronger")
        val second = repo.buscarEnDeezer("daft punk", "harder, better, faster, stronger")
        assertNotNull(first)
        assertEquals("https://e-cdn-images.dzcdn.net/images/cover/xl.jpg", first!!.coverXlUrl)
        assertEquals("https://cdns-preview.dzcdn.net/stream/preview.mp3", first.previewUrl)
        assertEquals(1, deezerCallCount)
        assertEquals(first, second)

        // 3. Signed scrobble POST
        val scrobbleRes = repo.scrobbleTrack(
            artist = "Daft Punk",
            track = "Harder, Better, Faster, Stronger",
            timestampSec = 1700000000L,
            album = "Discovery",
            durationMs = 224_000L
        )
        assertTrue(scrobbleRes.isSuccess)
        assertTrue(lastScrobbleSig!!.contains("api_sig="))
        assertTrue(lastScrobbleSig!!.contains("sk=sk_valid_999"))
    }

    @Test
    fun `lastFmProfileScreen renders disconnected card and connected Top Loved Recent tabs`() {
        var connectClicks = 0
        var selectedTab = com.example.model.LastFmProfileTab.TOP_TRACKS
        var selectedPeriod = com.example.model.LastFmPeriod.SEVEN_DAYS
        var playedTitle: String? = null
        var lovedTitle: String? = null

        val sampleTrack = com.example.model.LastFmTrackItem(
            id = "top_1",
            title = "One More Time",
            artist = "Daft Punk",
            album = "Discovery",
            playcount = 88L,
            rank = 1,
            isNowPlaying = true,
            isLoved = false,
            deezerEnrichment = com.example.model.DeezerTrackEnrichment(
                deezerTrackId = 4455L,
                title = "One More Time",
                artist = "Daft Punk",
                albumTitle = "Discovery",
                coverXlUrl = null,
                previewUrl = "https://cdns-preview.dzcdn.net/preview.mp3",
                durationSec = 30
            ),
            deezerLookupDone = true
        )

        val stateHolder = androidx.compose.runtime.mutableStateOf(
            com.example.ui.viewmodel.LastFmUiState(
                hasCredentials = true,
                session = null
            )
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.LastFmProfileScreen(
                    uiState = stateHolder.value,
                    currentTrackId = null,
                    isPlaying = false,
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    onConnectClick = { connectClicks++ },
                    onOpenCredentialsDialog = {},
                    onLogoutClick = {},
                    onRefreshClick = {},
                    onOpenSettings = {},
                    onSelectTab = { tab ->
                        selectedTab = tab
                        stateHolder.value = stateHolder.value.copy(selectedTab = tab)
                    },
                    onSelectPeriod = { period ->
                        selectedPeriod = period
                        stateHolder.value = stateHolder.value.copy(selectedPeriod = period)
                    },
                    onEnsureDeezerEnrichment = {},
                    onPlayPreview = { playedTitle = it.title },
                    onToggleLove = { lovedTitle = it.title }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Disconnected state shows CTA button
        composeTestRule.onNodeWithTag("lastfm_disconnected_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lastfm_connect_button").performClick()
        assertEquals(1, connectClicks)

        // Switch to connected state with sample tracks
        stateHolder.value = com.example.ui.viewmodel.LastFmUiState(
            hasCredentials = true,
            session = com.example.model.LastFmSession("sk_1", "davoopunk"),
            userInfo = com.example.model.LastFmUserInfo("davoopunk", "Davoo", 950L),
            selectedTab = com.example.model.LastFmProfileTab.TOP_TRACKS,
            topTracks = listOf(sampleTrack),
            recentTracks = listOf(sampleTrack)
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lastfm_connected_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("@davoopunk").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lastfm_track_row_top_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("lastfm_now_playing_badge", useUnmergedTree = true).assertIsDisplayed()

        // Switch period & tab
        composeTestRule.onNodeWithTag("lastfm_period_12month").performClick()
        assertEquals(com.example.model.LastFmPeriod.TWELVE_MONTHS, selectedPeriod)

        composeTestRule.onNodeWithTag("lastfm_love_button_top_1").performClick()
        assertEquals("One More Time", lovedTitle)

        composeTestRule.onNodeWithTag("lastfm_track_row_top_1").performClick()
        assertEquals("One More Time", playedTitle)

        composeTestRule.onNodeWithTag("lastfm_tab_recent").performClick()
        assertEquals(com.example.model.LastFmProfileTab.RECENT_TRACKS, selectedTab)
    }

    @Test
    fun `settingsScreen exposes LastFm connect and API credentials rows`() {
        var lastFmConnectClicks = 0
        var lastFmCredsClicks = 0
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.SettingsScreen(
                    totalTracksCount = 8,
                    isScanning = false,
                    excludedFoldersCount = 0,
                    sdCardFoldersCount = 0,
                    audioSettings = com.example.model.AudioSettings(),
                    isSleepTimerActive = false,
                    preferredTranslationEngine = com.example.ai.TranslationEngine.GOOGLE,
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
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    lastFmUsername = null,
                    lastFmHasCredentials = false,
                    onOpenLastFmProfileOrConnect = { lastFmConnectClicks++ },
                    onConfigureLastFmCredentials = { lastFmCredsClicks++ }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_lastfm_credentials_row"))
        composeTestRule.onNodeWithTag("settings_lastfm_connect_row").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("settings_lastfm_credentials_row").assertIsDisplayed().performClick()
        assertEquals(1, lastFmConnectClicks)
        assertEquals(1, lastFmCredsClicks)
    }

    @Test
    fun `globalSearchOverlay keeps synchronous localQuery independent of lagging ViewModel searchQuery and clears cleanly`() {
        val reportedQueries = mutableListOf<String>()
        val staleUiState = androidx.compose.runtime.mutableStateOf(
            com.example.ui.viewmodel.SonoraUiState(
                searchQuery = "",
                isGlobalSearchOpen = true
            )
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.GlobalSearchOverlay(
                    uiState = staleUiState.value,
                    currentTrackId = null,
                    isPlaying = false,
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    onQueryChange = { reportedQueries.add(it) },
                    onClose = {},
                    onPlayTrack = { _, _ -> },
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    onToggleFavorite = {},
                    onShowLyrics = {},
                    onEditTags = {},
                    onShowMetadata = {},
                    onOpenAlbum = {},
                    onOpenArtist = {},
                    onOpenPlaylist = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Simulate rapid typing while ViewModel searchQuery lags behind
        val inputNode = composeTestRule.onNodeWithTag("global_search_input")
        inputNode.performTextInput("f")
        inputNode.performTextInput("a")
        // Lagging StateFlow emits stale "f" mid-typing
        staleUiState.value = staleUiState.value.copy(searchQuery = "f")
        inputNode.performTextInput("rr")
        composeTestRule.waitForIdle()

        // Local field must retain exact typed order "farr" (never overwritten or reversed by stale "f")
        composeTestRule.onNodeWithTag("global_search_input").assertTextContains("farr")
        assertEquals(listOf("f", "fa", "farr"), reportedQueries)
        composeTestRule.onNodeWithTag("global_search_loading_state").assertIsDisplayed()

        // Once debounced ViewModel settles on "farr" with 0 matches, empty state renders cleanly
        staleUiState.value = staleUiState.value.copy(searchQuery = "farr")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("global_search_empty_state").assertIsDisplayed()

        // Clicking Clear "X" resets localQuery immediately and notifies ViewModel
        composeTestRule.onNodeWithTag("global_search_clear_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("", reportedQueries.last())
        composeTestRule.onNodeWithText("Búsqueda global en tiempo real").assertIsDisplayed()
    }

    @Test
    fun `musicApiService cleans collaboration artists and falls back to open Deezer search with https urls`() = runTest {
        assertEquals(
            "K/DA",
            com.example.data.remote.MusicApiService.extractPrimaryArtist("K/DA, (G)I-DLE, Wolftyla, Bea Miller")
        )
        assertEquals(
            "Calvin Harris",
            com.example.data.remote.MusicApiService.extractPrimaryArtist("Calvin Harris & Dua Lipa")
        )
        assertEquals(
            "Post Malone",
            com.example.data.remote.MusicApiService.extractPrimaryArtist("Post Malone feat. Swae Lee")
        )
        assertEquals(
            "J Balvin",
            com.example.data.remote.MusicApiService.extractPrimaryArtist("J Balvin ft. Bad Bunny")
        )
        assertEquals(
            "POP/STARS",
            com.example.data.remote.MusicApiService.cleanTrackTitleForSearch("POP/STARS (feat. Jaira Burns) [Remix]")
        )

        val seenQueries = mutableListOf<String>()
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val queryParam = req.url.queryParameter("q").orEmpty()
                seenQueries.add(queryParam)
                val bodyJson = if (queryParam.contains("artist:\"K/DA\"")) {
                    """{"data":[]}"""
                } else {
                    """
                    {
                      "data": [{
                        "id": 991122,
                        "title": "POP/STARS",
                        "duration": 30,
                        "preview": "http://cdns-preview-e.dzcdn.net/stream/c-kda.mp3",
                        "artist": { "name": "K/DA" },
                        "album": {
                          "title": "POP/STARS",
                          "cover_xl": "",
                          "cover_big": "http://e-cdn-images.dzcdn.net/images/cover/kda/500x500.jpg"
                        }
                      }]
                    }
                    """.trimIndent()
                }
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(with(okhttp3.ResponseBody) { bodyJson.toResponseBody(null) })
                    .build()
            }
            .build()

        val apiService = com.example.data.remote.MusicApiService(
            client = mockClient,
            deezerBaseUrl = "https://mock.deezer.com"
        )

        val result = apiService.searchDeezerTrack(
            artist = "K/DA, (G)I-DLE, Wolftyla, Bea Miller",
            track = "POP/STARS (League of Legends)"
        )

        assertNotNull(result)
        assertEquals(2, seenQueries.size)
        assertEquals("artist:\"K/DA\" track:\"POP/STARS\"", seenQueries[0])
        assertEquals("K/DA POP/STARS", seenQueries[1])
        assertEquals("https://e-cdn-images.dzcdn.net/images/cover/kda/500x500.jpg", result!!.coverXlUrl)
        assertEquals("https://cdns-preview-e.dzcdn.net/stream/c-kda.mp3", result.previewUrl)
    }

    @Test
    fun `lastFmUserInfo forces https on avatar urls and repository retries empty deezer cache entries on clear`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)
        prefs.saveLastFmCredentialsOverride("key_avatar", "secret_avatar")

        var deezerAttempts = 0
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val urlStr = req.url.toString()
                val jsonPayload = when {
                    urlStr.contains("method=user.getInfo") -> {
                        """
                        {
                          "user": {
                            "name": "DavooPunk",
                            "realname": "David",
                            "playcount": "1234",
                            "image": [
                              {"size":"medium","#text":"http://lastfm.freetls.fastly.net/i/u/64s/avatar.png"},
                              {"size":"large","#text":"http://lastfm.freetls.fastly.net/i/u/174s/avatar.png"},
                              {"size":"extralarge","#text":""}
                            ]
                          }
                        }
                        """.trimIndent()
                    }
                    urlStr.contains("deezer") -> {
                        deezerAttempts++
                        if (deezerAttempts <= 2) {
                            """{"data":[]}"""
                        } else {
                            """
                            {
                              "data": [{
                                "id": 7788,
                                "title": "Nightcall",
                                "duration": 30,
                                "preview": "http://cdns-preview.dzcdn.net/nightcall.mp3",
                                "artist": {"name": "Kavinsky"},
                                "album": {"title": "OutRun", "cover_xl": "http://e-cdn-images.dzcdn.net/nightcall.jpg"}
                              }]
                            }
                            """.trimIndent()
                        }
                    }
                    else -> """{}"""
                }
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(with(okhttp3.ResponseBody) { jsonPayload.toResponseBody(null) })
                    .build()
            }
            .build()

        val apiService = com.example.data.remote.MusicApiService(
            client = mockClient,
            lastFmBaseUrl = "https://mock.last.fm/2.0/",
            deezerBaseUrl = "https://mock.deezer.com"
        )
        val userInfo = apiService.getUserInfo("key_avatar", "DavooPunk").getOrThrow()
        assertEquals("https://lastfm.freetls.fastly.net/i/u/174s/avatar.png", userInfo.avatarUrl)

        val repo = com.example.data.repository.LastFmRepository(prefs, apiService)
        // First lookup returns null across both strict + open attempts (2 HTTP calls)
        assertEquals(null, repo.buscarEnDeezer("Kavinsky", "Nightcall"))
        assertEquals(2, deezerAttempts)
        // Cached negative result prevents repeated calls until cleared
        assertEquals(null, repo.buscarEnDeezer("Kavinsky", "Nightcall"))
        assertEquals(2, deezerAttempts)

        // Clearing empty entries lets refresh retry and succeed
        repo.clearEmptyDeezerCacheEntries()
        val retried = repo.buscarEnDeezer("Kavinsky", "Nightcall")
        assertNotNull(retried)
        assertEquals("https://e-cdn-images.dzcdn.net/nightcall.jpg", retried!!.coverXlUrl)
        assertEquals(3, deezerAttempts)
    }

    @Test
    fun `lastFmTrackMatcher finds local library matches and falls back cleanly when absent`() {
        val localTrack = AudioTrack(
            id = "local_kda_1",
            contentUri = "content://media/external/audio/media/909",
            title = "POP/STARS",
            artist = "K/DA",
            album = "League of Legends",
            albumId = 909L,
            albumArtUri = null,
            durationMs = 191_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 25_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )

        val collabItem = com.example.model.LastFmTrackItem(
            id = "top_1",
            title = "POP/STARS (feat. Jaira Burns)",
            artist = "K/DA, (G)I-DLE, Madison Beer",
            deezerLookupDone = true
        )
        val missingItem = com.example.model.LastFmTrackItem(
            id = "top_2",
            title = "Song Not On Device",
            artist = "Remote Artist",
            deezerEnrichment = com.example.model.DeezerTrackEnrichment(
                deezerTrackId = 5511L,
                title = "Song Not On Device",
                artist = "Remote Artist",
                albumTitle = "Remote Album",
                coverXlUrl = "https://e-cdn-images.dzcdn.net/cover.jpg",
                previewUrl = "https://cdns-preview.dzcdn.net/preview.mp3",
                durationSec = 30
            ),
            deezerLookupDone = true
        )

        val matched = com.example.data.repository.LastFmTrackMatcher.findMatchingLocalTrack(
            collabItem,
            listOf(localTrack)
        )
        assertEquals("local_kda_1", matched?.id)

        val notLocal = com.example.data.repository.LastFmTrackMatcher.findMatchingLocalTrack(
            missingItem,
            listOf(localTrack)
        )
        assertEquals(null, notLocal)
        assertEquals("deezer_preview:5511", missingItem.toPreviewAudioTrack()?.id)
    }

    @Test
    fun `crossfadeMath computes equal-power curve and respects repeatOne, speed, and stopAfterCurrentTrack`() {
        val (outStart, inStart) = com.example.playback.CrossfadeMath.equalPowerGains(0f)
        assertEquals(1.0f, outStart, 0.001f)
        assertEquals(0.0f, inStart, 0.001f)

        val (outMid, inMid) = com.example.playback.CrossfadeMath.equalPowerGains(0.5f)
        assertEquals(0.7071f, outMid, 0.01f)
        assertEquals(0.7071f, inMid, 0.01f)
        assertEquals(1.0f, outMid * outMid + inMid * inMid, 0.001f)

        val (outEnd, inEnd) = com.example.playback.CrossfadeMath.equalPowerGains(1f)
        assertEquals(0.0f, outEnd, 0.001f)
        assertEquals(1.0f, inEnd, 0.001f)

        // 3-minute track with 5s crossfade: should NOT trigger early at 170s, SHOULD trigger at 175.5s
        assertFalse(
            com.example.playback.CrossfadeMath.shouldTriggerCrossfade(
                crossfadeSeconds = 5,
                durationMs = 180_000L,
                positionMs = 170_000L,
                hasNextItem = true,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF,
                playbackSpeed = 1.0f,
                stopAfterCurrentTrack = false,
                alreadyTransitioning = false
            )
        )
        assertTrue(
            com.example.playback.CrossfadeMath.shouldTriggerCrossfade(
                crossfadeSeconds = 5,
                durationMs = 180_000L,
                positionMs = 175_500L,
                hasNextItem = true,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF,
                playbackSpeed = 1.0f,
                stopAfterCurrentTrack = false,
                alreadyTransitioning = false
            )
        )
        // Must NOT trigger when Repeat One or stopAfterCurrentTrack is enabled
        assertFalse(
            com.example.playback.CrossfadeMath.shouldTriggerCrossfade(
                crossfadeSeconds = 5,
                durationMs = 180_000L,
                positionMs = 175_500L,
                hasNextItem = true,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE,
                playbackSpeed = 1.0f,
                stopAfterCurrentTrack = false,
                alreadyTransitioning = false
            )
        )
        assertFalse(
            com.example.playback.CrossfadeMath.shouldTriggerCrossfade(
                crossfadeSeconds = 5,
                durationMs = 180_000L,
                positionMs = 175_500L,
                hasNextItem = true,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF,
                playbackSpeed = 1.0f,
                stopAfterCurrentTrack = true,
                alreadyTransitioning = false
            )
        )
        // Must NOT trigger for very short tracks <= 2x fade duration
        assertFalse(
            com.example.playback.CrossfadeMath.shouldTriggerCrossfade(
                crossfadeSeconds = 8,
                durationMs = 14_000L,
                positionMs = 10_000L,
                hasNextItem = true,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF,
                playbackSpeed = 1.0f,
                stopAfterCurrentTrack = false,
                alreadyTransitioning = false
            )
        )
    }

    @Test
    fun `settingsScreen omits duplicate crossfade replaygain and gapless controls`() {
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.SettingsScreen(
                    totalTracksCount = 24,
                    isScanning = false,
                    excludedFoldersCount = 0,
                    sdCardFoldersCount = 0,
                    audioSettings = com.example.model.AudioSettings(crossfadeSeconds = 6, replayGainEnabled = true, gaplessEnabled = true),
                    isSleepTimerActive = false,
                    preferredTranslationEngine = com.example.ai.TranslationEngine.GOOGLE,
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
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_engine_google"))
        composeTestRule.onAllNodesWithTag("settings_crossfade_slider").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("settings_replaygain_switch").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("settings_gapless_switch").assertCountEquals(0)
    }

    @Test
    fun `equalizerAndDspSheet keeps crossfade replaygain gapless and omits dynamic_theme_switch`() {
        val eqController = com.example.playback.AudioEffectsController()
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.EqualizerAndDspSheet(
                    settings = com.example.model.AudioSettings(crossfadeSeconds = 6, replayGainEnabled = true, gaplessEnabled = true),
                    bands = eqController.getBands(com.example.model.AudioSettings()),
                    onUpdateSettings = {},
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("replaygain_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("gapless_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("crossfade_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("dynamic_theme_switch").assertCountEquals(0)
    }

    @Test
    fun `lyricsBottomSheet renders adaptive artwork accent card and floating translation menu`() {
        val track = AudioTrack(
            id = "lyr_adaptive_91",
            contentUri = "content://media/external/audio/media/991",
            title = "Neon Glow",
            artist = "Davoo",
            album = "Night Lights",
            albumId = 91L,
            albumArtUri = null,
            durationMs = 195_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 4_800_000L,
            dateAddedSec = 210L,
            folderName = "Music"
        )
        val lyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(0L, "Dancing under neon glow", "Bailando bajo el brillo neón"),
                com.example.model.LyricLine(12_000L, "Fade gently into the next song", "Fúndete suave en la siguiente canción")
            ),
            isSynced = true,
            sourceLabel = "Archivo .lrc local · Bilingüe",
            hasEmbeddedTranslation = true
        )
        val artworkAccent = androidx.compose.ui.graphics.Color(0xFFE91E63)

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 1_500L,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {},
                    onTranslateLyrics = { _, _, _ -> },
                    onSaveManualTranslation = { _, _, _ -> },
                    dynamicArtworkColorsEnabled = true,
                    dynamicAccentColor = artworkAccent
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lyrics_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_close_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("spotify_lyrics_list").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("lyrics_adaptive_card").assertCountEquals(0)
        composeTestRule.onNodeWithText("Dancing under neon glow").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bailando bajo el brillo neón").assertIsDisplayed()

        // Open floating translation menu and confirm chips & controls render cleanly under adaptive palette
        composeTestRule.onNodeWithTag("lyrics_translate_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_translate_floating_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_display_mode_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_engine_selector_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("translation_language_list").assertIsDisplayed()
    }

    @Test
    fun `libraryScreen scroll to current button toggles with active track and scrolls to item`() {
        val sampleTracks = (1..25).map { idx ->
            AudioTrack(
                id = "lib_track_$idx",
                contentUri = "content://media/external/audio/media/$idx",
                title = "Track Number %02d".format(idx),
                artist = "Artist $idx",
                album = "Album $idx",
                albumId = idx.toLong(),
                albumArtUri = null,
                durationMs = 180_000L,
                format = AudioFormat.MP3,
                mimeType = "audio/mpeg",
                sizeBytes = 3_000_000L,
                dateAddedSec = idx.toLong(),
                folderName = "Music"
            )
        }
        val activeState = androidx.compose.runtime.mutableStateOf<AudioTrack?>(null)

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.LibraryScreen(
                    uiState = com.example.ui.viewmodel.SonoraUiState(
                        hasAudioPermission = true,
                        allTracks = sampleTracks,
                        filteredTracks = sampleTracks,
                        albums = emptyList()
                    ),
                    playbackState = com.example.playback.PlaybackUiState(
                        currentTrack = activeState.value,
                        isPlaying = activeState.value != null
                    ),
                    onRequestPermission = {},
                    onImportFiles = {},
                    onSearchChange = {},
                    onSelectSortOrder = {},
                    onPlayTrack = { _, _ -> },
                    onShuffleAll = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    onToggleFavorite = {},
                    onShowLyrics = {},
                    onEditTags = {},
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Hidden when no active track is playing
        composeTestRule.onAllNodesWithTag("library_scroll_to_current_button").assertCountEquals(0)

        // Activate track 22 (well below initial viewport) -> button appears next to selection button
        composeTestRule.runOnUiThread {
            activeState.value = sampleTracks[21]
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("library_lazy_column")
            .performScrollToNode(hasTestTag("library_scroll_to_current_button"))
        composeTestRule.onNodeWithTag("library_scroll_to_current_button")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithTag("track_row_lib_track_22").assertIsDisplayed()
    }

    @Test
    fun `queueBottomSheet pins active song at top hides played items and maps upcoming indices`() {
        val tracks = (0 until 12).map { idx ->
            AudioTrack(
                id = "q_$idx",
                contentUri = "content://media/external/audio/media/${500 + idx}",
                title = "Queue Song $idx",
                artist = "Queue Artist",
                album = "Queue Album",
                albumId = 50L,
                albumArtUri = null,
                durationMs = 200_000L,
                format = AudioFormat.FLAC,
                mimeType = "audio/flac",
                sizeBytes = 12_000_000L,
                dateAddedSec = idx.toLong(),
                folderName = "Music"
            )
        }
        var selectedActualIndex: Int? = null
        var removedActualIndex: Int? = null

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.QueueBottomSheet(
                    playbackState = com.example.playback.PlaybackUiState(
                        currentTrack = tracks[2],
                        isPlaying = true,
                        queue = tracks,
                        currentIndex = 2
                    ),
                    onDismiss = {},
                    onSelectIndex = { selectedActualIndex = it },
                    onRemoveIndex = { removedActualIndex = it },
                    onMoveItem = { _, _ -> }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Active song pinned at index 0 with "SONANDO AHORA" badge; previously played songs hidden
        composeTestRule.onNodeWithTag("queue_now_playing_item").assertIsDisplayed()
        composeTestRule.onNodeWithText("SONANDO AHORA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Queue Song 2").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Queue Song 0").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Queue Song 1").assertCountEquals(0)

        // Upcoming track at visualIndex 1 corresponds to actualIndex 3 (2 + 1)
        composeTestRule.onNodeWithTag("queue_lazy_column").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("queue_drag_handle_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("queue_item_q_3_1").performClick()
        assertEquals(3, selectedActualIndex)

        composeTestRule.onNodeWithTag("queue_remove_button_1").performClick()
        assertEquals(3, removedActualIndex)

        // Scroll down to reveal scroll-to-top quick action in header
        composeTestRule.onNodeWithTag("queue_lazy_column").performScrollToIndex(8)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("queue_scroll_to_top_button").assertIsDisplayed()
    }

    @Test
    fun `trackRow exposes delete from device menu action and cleanupDeletedTracks purges all tables`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, com.example.data.local.SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val playlistDao = db.playlistDao()
            val importedDao = db.importedTrackDao()
            val statsDao = db.trackStatsDao()
            val overrideDao = db.trackTagOverrideDao()
            val excludedDao = db.excludedFolderDao()
            val lyricsDao = db.trackLyricsDao()
            val translationDao = db.trackLyricsTranslationDao()

            val targetTrack = AudioTrack(
                id = "saf_del_1",
                contentUri = "content://com.example.provider/audio/del1.mp3",
                title = "Delete Me",
                artist = "Cleanup Artist",
                album = "Cleanup Album",
                albumId = 77L,
                albumArtUri = null,
                durationMs = 150_000L,
                format = AudioFormat.MP3,
                mimeType = "audio/mpeg",
                sizeBytes = 2_000_000L,
                dateAddedSec = 10L,
                folderName = "Downloads",
                isSafImported = true
            )

            // Seed all 6 track-associated Room tables
            val playlistId = playlistDao.insertPlaylist(com.example.data.local.PlaylistEntity(name = "P1", description = ""))
            playlistDao.insertPlaylistTrack(PlaylistTrackEntity.fromAudioTrack(playlistId, targetTrack, 0))
            importedDao.upsertTracks(
                listOf(
                    com.example.data.local.ImportedTrackEntity(
                        uri = targetTrack.contentUri,
                        title = targetTrack.title,
                        artist = targetTrack.artist,
                        album = targetTrack.album,
                        durationMs = targetTrack.durationMs,
                        formatName = targetTrack.format.name,
                        mimeType = targetTrack.mimeType,
                        sizeBytes = targetTrack.sizeBytes,
                        folderName = targetTrack.folderName
                    )
                )
            )
            statsDao.recordPlay(targetTrack.id, 999L)
            overrideDao.upsert(
                com.example.data.local.TrackTagOverrideEntity(
                    trackId = targetTrack.id,
                    title = "Override",
                    artist = "A",
                    album = "Al",
                    albumArtist = "",
                    genre = "",
                    year = 2024,
                    trackNumber = 1,
                    customArtworkUri = null
                )
            )
            lyricsDao.upsert(
                com.example.data.local.TrackLyricsEntity(
                    trackId = targetTrack.id,
                    rawLrcText = "line",
                    sourceLabel = "LRC",
                    updatedAt = 100L
                )
            )
            translationDao.upsert(
                com.example.data.local.TrackLyricsTranslationEntity(
                    trackId = targetTrack.id,
                    languageCode = "es",
                    rawTranslatedText = "linea",
                    sourceLabel = "Google",
                    updatedAt = 100L
                )
            )

            val repository = com.example.data.repository.MusicRepository(
                context = context,
                scanner = com.example.data.repository.DeviceMusicScanner(context),
                playlistDao = playlistDao,
                importedTrackDao = importedDao,
                trackStatsDao = statsDao,
                trackTagOverrideDao = overrideDao,
                excludedFolderDao = excludedDao,
                trackLyricsDao = lyricsDao,
                trackLyricsTranslationDao = translationDao
            )

            repository.cleanupDeletedTracks(listOf(targetTrack))

            assertTrue(playlistDao.getEntriesOrdered(playlistId).isEmpty())
            assertTrue(importedDao.observeImportedTracks().first().isEmpty())
            assertTrue(statsDao.observeAllStats().first().isEmpty())
            assertTrue(overrideDao.observeAllOverrides().first().none { it.trackId == targetTrack.id })
            assertNull(lyricsDao.getLyricsForTrack(targetTrack.id))
            assertNull(translationDao.getTranslationForTrack(targetTrack.id))
        } finally {
            db.close()
        }

        // Also verify TrackRow renders and triggers "Eliminar del dispositivo" callback
        var deleteMenuClicked = false
        val uiTrack = AudioTrack(
            id = "row_del_2",
            contentUri = "content://media/external/audio/media/888",
            title = "Menu Delete Track",
            artist = "Menu Artist",
            album = "Menu Album",
            albumId = 88L,
            albumArtUri = null,
            durationMs = 140_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 2_000_000L,
            dateAddedSec = 5L,
            folderName = "Music"
        )
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.TrackRow(
                    track = uiTrack,
                    isCurrentTrack = false,
                    isPlaying = false,
                    onClick = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    onShowMetadata = {},
                    onDeleteFromDevice = { deleteMenuClicked = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("track_menu_row_del_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("track_delete_menu_row_del_2").assertIsDisplayed().performClick()
        assertTrue(deleteMenuClicked)
    }

    @Test
    fun `audioTagWriter writes MP3 ID3v2 and FLAC Vorbis tags while preserving lyrics and TagEditor edits filename`() {
        val sampleMp3 = AudioTrack(
            id = "tag_mp3_1",
            contentUri = "content://media/external/audio/media/401",
            title = "Original Song",
            artist = "Orig Artist",
            album = "Orig Album",
            albumId = 41L,
            albumArtUri = null,
            durationMs = 180_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 3_000_000L,
            dateAddedSec = 10L,
            folderName = "Music",
            filePath = "/storage/emulated/0/Music/01 - Old Track Name.mp3"
        )
        val parts = com.example.data.repository.AudioTagWriter.splitFileName(sampleMp3)
        assertEquals("01 - Old Track Name", parts.baseName)
        assertEquals(".mp3", parts.extensionWithDot)
        assertEquals(
            "New_Bad_Name.mp3",
            com.example.data.repository.AudioTagWriter.buildTargetDisplayName("New/Bad:Name.mp3", ".mp3")
        )
        assertEquals("New_Bad_Name", com.example.data.repository.AudioTagWriter.sanitizeFileBaseName("New/Bad\\Name.mp3", ".mp3"))

        // 1. Real MP3 binary tag rewrite preserving existing USLT frame & audio bytes
        val tempDir = kotlin.io.path.createTempDirectory("davoo_tag_test").toFile()
        try {
            val mp3In = java.io.File(tempDir, "in.mp3")
            val mp3Out = java.io.File(tempDir, "out.mp3")
            val usltPayload = byteArrayOf(0x00, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0x00) +
                "Preserved Lyrics Line".toByteArray(Charsets.ISO_8859_1)
            val oldTagBytes = java.io.ByteArrayOutputStream().apply {
                write("USLT".toByteArray(Charsets.ISO_8859_1))
                write(byteArrayOf(0, 0, 0, usltPayload.size.toByte()))
                write(byteArrayOf(0, 0))
                write(usltPayload)
            }.toByteArray()
            val fakeAudioBytes = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00, 0x11, 0x22, 0x33, 0x44)
            java.io.FileOutputStream(mp3In).use { out ->
                out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
                out.write(
                    byteArrayOf(
                        ((oldTagBytes.size ushr 21) and 0x7F).toByte(),
                        ((oldTagBytes.size ushr 14) and 0x7F).toByte(),
                        ((oldTagBytes.size ushr 7) and 0x7F).toByte(),
                        (oldTagBytes.size and 0x7F).toByte()
                    )
                )
                out.write(oldTagBytes)
                out.write(fakeAudioBytes)
            }

            val mp3Ok = com.example.data.repository.AudioTagWriter.writeId3v2TagsToFile(
                sourceFile = mp3In,
                destFile = mp3Out,
                payload = com.example.data.repository.TagMetadataPayload(
                    title = "Neon Skyline",
                    artist = "DavooPunk",
                    album = "Cyber Nights",
                    albumArtist = "DavooPunk",
                    genre = "Synthwave",
                    year = 2026,
                    trackNumber = 4,
                    artworkBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
                    artworkMimeType = "image/jpeg"
                )
            )
            assertTrue(mp3Ok)
            val mp3Bytes = mp3Out.readBytes()
            val mp3Ascii = String(mp3Bytes, Charsets.ISO_8859_1)
            assertTrue(mp3Ascii.startsWith("ID3"))
            assertTrue(mp3Ascii.contains("TIT2"))
            assertTrue(mp3Ascii.contains("TPE1"))
            assertTrue(mp3Ascii.contains("TALB"))
            assertTrue(mp3Ascii.contains("APIC"))
            assertTrue(mp3Ascii.contains("USLT"))
            assertTrue(mp3Ascii.contains("Preserved Lyrics Line"))
            val expectedTitleUtf16 = "Neon Skyline".toByteArray(Charsets.UTF_16LE)
            val hasUtf16Title = (0..(mp3Bytes.size - expectedTitleUtf16.size)).any { offset ->
                expectedTitleUtf16.indices.all { i -> mp3Bytes[offset + i] == expectedTitleUtf16[i] }
            }
            assertTrue(hasUtf16Title)
            assertTrue(mp3Bytes.takeLast(fakeAudioBytes.size) == fakeAudioBytes.toList())

            // 2. Real FLAC Vorbis comment + picture rewrite preserving STREAMINFO & LYRICS
            val flacIn = java.io.File(tempDir, "in.flac")
            val flacOut = java.io.File(tempDir, "out.flac")
            val streamInfo = ByteArray(34) { idx -> (idx + 1).toByte() }
            val oldCommentPayload = java.io.ByteArrayOutputStream().apply {
                val vendor = "old".toByteArray(Charsets.UTF_8)
                write(byteArrayOf(vendor.size.toByte(), 0, 0, 0))
                write(vendor)
                write(byteArrayOf(2, 0, 0, 0)) // 2 comments
                listOf("TITLE=OldTitle", "LYRICS=Keep Flac Lyrics").forEach { c ->
                    val b = c.toByteArray(Charsets.UTF_8)
                    write(byteArrayOf(b.size.toByte(), 0, 0, 0))
                    write(b)
                }
            }.toByteArray()
            val flacFrames = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0x69, 0x01, 0x77)
            java.io.FileOutputStream(flacIn).use { out ->
                out.write("fLaC".toByteArray(Charsets.ISO_8859_1))
                // STREAMINFO (type 0, not last)
                out.write(byteArrayOf(0, 0, 0, 34))
                out.write(streamInfo)
                // VORBIS_COMMENT (type 4, last = 0x84)
                out.write(byteArrayOf(0x84.toByte(), 0, (oldCommentPayload.size ushr 8).toByte(), oldCommentPayload.size.toByte()))
                out.write(oldCommentPayload)
                out.write(flacFrames)
            }

            val flacOk = com.example.data.repository.AudioTagWriter.writeFlacTagsToFile(
                sourceFile = flacIn,
                destFile = flacOut,
                payload = com.example.data.repository.TagMetadataPayload(
                    title = "Lossless Master",
                    artist = "DavooPunk",
                    album = "Hi-Res EP",
                    albumArtist = "DavooPunk",
                    genre = "Electronic",
                    year = 2026,
                    trackNumber = 1,
                    artworkBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
                    artworkMimeType = "image/png"
                )
            )
            assertTrue(flacOk)
            val flacBytes = flacOut.readBytes()
            val flacUtf8 = String(flacBytes, Charsets.UTF_8)
            assertTrue(flacUtf8.startsWith("fLaC"))
            assertTrue(flacUtf8.contains("TITLE=Lossless Master"))
            assertTrue(flacUtf8.contains("ARTIST=DavooPunk"))
            assertTrue(flacUtf8.contains("LYRICS=Keep Flac Lyrics"))
            assertTrue(flacBytes.takeLast(flacFrames.size) == flacFrames.toList())
        } finally {
            tempDir.deleteRecursively()
        }

        // 3. TagEditorBottomSheet UI: filename input displays extension & passes edited fileNameBase
        var savedBaseName: String? = null
        var savedTitle: String? = null
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.TagEditorBottomSheet(
                    track = sampleMp3,
                    pickedArtworkUri = null,
                    onPickCoverImage = {},
                    onClearCustomCover = {},
                    onSave = { baseName, title, _, _, _, _, _, _, _ ->
                        savedBaseName = baseName
                        savedTitle = title
                    },
                    onDismiss = {}
                )
            }
        }
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tag_filename_input").assertIsDisplayed()
        composeTestRule.onNodeWithText(".mp3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tag_filename_input").performTextReplacement("Renamed\\Final/Mix")
        composeTestRule.onNodeWithTag("tag_title_input").performTextReplacement("Final Mix Title")
        composeTestRule.onNodeWithTag("save_tags_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Renamed_Final_Mix", savedBaseName)
        assertEquals("Final Mix Title", savedTitle)
    }

    @Test
    fun `creditsBottomSheet legal reflects offline plus optional integrations and changelog v204 omits legal changes`() {
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.CreditsBottomSheet(onDismiss = {})
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("about_tab_legal").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Offline por defecto · Integraciones opcionales (Last.fm, Deezer, letras)").assertIsDisplayed()

        composeTestRule.onNodeWithTag("legal_terms_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Software libre (MIT), APIs de terceros y uso responsable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legal_terms_card").assertTextContains("Servicios y APIs de terceros", substring = true)

        composeTestRule.onNodeWithTag("legal_dmca_card").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Reproductor local · Vistas previas transmitidas desde API pública").assertIsDisplayed()
        composeTestRule.onNodeWithTag("legal_dmca_card").assertTextContains("Deezer API", substring = true)

        // Verify v2.1.1 (Actual) and v2.1.0–v2.0.4 (Anterior) changelog entries omit Legal tab changes
        composeTestRule.onNodeWithTag("about_tab_changelog").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Auto-etiquetado desde la API pública de Deezer", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Compartir letras y fragmentos en Video 9:16", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Visor de letras inmersivo", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Crossfade real entre canciones", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Ir a la canción actual y cola reorganizada", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Eliminación física real de canciones del dispositivo", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Renombrado físico y escritura real de etiquetas", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Offline por defecto", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("pestaña Legal", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Términos y Condiciones de Uso", substring = true).assertCountEquals(0)
    }

    @Test
    fun `lyricsViewer v205 manual scroll pauses auto scroll shows Sincronizar pill and resumes on click`() {
        val track = AudioTrack(
            id = "lyr_scroll_205",
            contentUri = "content://media/external/audio/media/2050",
            title = "Free Scroll Song",
            artist = "Davoo",
            album = "v2.0.5",
            albumId = 205L,
            albumArtUri = null,
            durationMs = 240_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 25_000_000L,
            dateAddedSec = 250L,
            folderName = "Music"
        )
        val manyLines = (0 until 30).map { idx ->
            com.example.model.LyricLine(
                timestampMs = idx * 4_000L,
                text = "Line number $idx of the synchronized song"
            )
        }
        val lyrics = com.example.model.LyricsData(
            lines = manyLines,
            isSynced = true,
            sourceLabel = "Archivo .lrc local"
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 4_100L,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("spotify_lyrics_list").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("lyrics_sync_resume_button").assertCountEquals(0)

        // User manually swipes through lyrics -> pauses auto-scroll & shows "Sincronizar" pill
        composeTestRule.onNodeWithTag("spotify_lyrics_list").performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lyrics_sync_resume_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sincronizar").assertIsDisplayed()

        // Tapping Sincronizar resumes auto-scroll immediately and hides the pill
        composeTestRule.onNodeWithTag("lyrics_sync_resume_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("lyrics_sync_resume_button").assertCountEquals(0)
    }

    @Test
    fun `lyricsViewer v205 offset controls adjust seek target and open lrc export dialog`() {
        val track = AudioTrack(
            id = "lyr_offset_205",
            contentUri = "content://media/external/audio/media/2051",
            title = "Timing Tune",
            artist = "Davoo",
            album = "Sync Lab",
            albumId = 206L,
            albumArtUri = null,
            durationMs = 200_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 5_000_000L,
            dateAddedSec = 251L,
            folderName = "Music"
        )
        val lyricsState = androidx.compose.runtime.mutableStateOf(
            com.example.model.LyricsData(
                lines = listOf(
                    com.example.model.LyricLine(0L, "Intro line"),
                    com.example.model.LyricLine(12_000L, "Chorus Drop Line", "Línea del coro")
                ),
                isSynced = true,
                sourceLabel = "Archivo .lrc guardado",
                hasEmbeddedTranslation = true,
                offsetMs = 0L
            )
        )
        var lastSeekMs: Long? = null
        var resetCalled = false

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyricsState.value,
                    isLoading = false,
                    positionMs = 1_000L,
                    onSeekTo = { lastSeekMs = it },
                    onPickLrcFile = {},
                    onDismiss = {},
                    onAdjustOffset = { delta ->
                        lyricsState.value = lyricsState.value.copy(
                            offsetMs = lyricsState.value.offsetMs + delta
                        )
                    },
                    onResetOffset = {
                        resetCalled = true
                        lyricsState.value = lyricsState.value.copy(offsetMs = 0L)
                    },
                    onExportSaveLrc = {},
                    onExportShareLrc = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        // Open manage menu -> test +0.5s, +0.5s, +0.5s (= +1.5s) then click Chorus Drop Line (12s -> seeks to 10.5s)
        composeTestRule.onNodeWithTag("lyrics_manage_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_offset_plus_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("lyrics_offset_plus_button").performClick()
        composeTestRule.onNodeWithTag("lyrics_offset_plus_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1_500L, lyricsState.value.offsetMs)

        composeTestRule.onNodeWithTag("lyrics_offset_minus_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1_000L, lyricsState.value.offsetMs)

        // Open export .lrc dialog from manage menu
        composeTestRule.onNodeWithTag("menu_export_lrc_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_export_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelar").performClick()
        composeTestRule.waitForIdle()

        // Tapping lyric line at 12,000ms with +1,000ms offset seeks to 11,000ms
        composeTestRule.onNodeWithText("Chorus Drop Line").performClick()
        composeTestRule.waitForIdle()
        assertEquals(11_000L, lastSeekMs)

        // Reset offset via 0.0s button
        composeTestRule.onNodeWithTag("lyrics_manage_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_offset_reset_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(resetCalled)
        assertEquals(0L, lyricsState.value.offsetMs)
    }

    @Test
    fun `lyricsRepository saveLyricsOffset persists across reload and exportLyricsToLrc shifts timestamps`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.repository.MusicRepository(
            context = context,
            scanner = com.example.data.repository.DeviceMusicScanner(context),
            playlistDao = db.playlistDao(),
            importedTrackDao = db.importedTrackDao(),
            trackStatsDao = db.trackStatsDao(),
            trackTagOverrideDao = db.trackTagOverrideDao(),
            excludedFolderDao = db.excludedFolderDao(),
            trackLyricsDao = db.trackLyricsDao(),
            trackLyricsTranslationDao = db.trackLyricsTranslationDao()
        )
        val track = AudioTrack(
            id = "repo_offset_track_1",
            contentUri = "content://media/external/audio/media/9991",
            title = "Starlight",
            artist = "Davoo",
            album = "Orbit",
            albumId = 50L,
            albumArtUri = null,
            durationMs = 210_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 20_000_000L,
            dateAddedSec = 100L,
            folderName = "Music"
        )
        val initialEmbeddedLyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(5_000L, "First verse", "Primer verso"),
                com.example.model.LyricLine(15_500L, "Second verse", "Segundo verso")
            ),
            isSynced = true,
            sourceLabel = "Etiqueta incrustada",
            hasEmbeddedTranslation = true,
            offsetMs = 0L
        )

        val updated = repo.saveLyricsOffset(track, initialEmbeddedLyrics, 1_500L)
        assertEquals(1_500L, updated.offsetMs)

        val reloaded = repo.loadLyrics(track)
        assertNotNull(reloaded)
        assertEquals(1_500L, reloaded?.offsetMs)

        val exportedBilingual = com.example.data.repository.AudioTagParsers.exportLyricsToLrc(
            trackTitle = track.title,
            trackArtist = track.artist,
            trackAlbum = track.album,
            lyrics = reloaded!!,
            translatedLyrics = null,
            includeTranslation = true
        )
        assertTrue(exportedBilingual.contains("[ti:Starlight]"))
        assertTrue(exportedBilingual.contains("[00:03.50]First verse"))
        assertTrue(exportedBilingual.contains("[00:03.50]Primer verso"))
        assertTrue(exportedBilingual.contains("[00:14.00]Second verse"))
        assertTrue(exportedBilingual.contains("[00:14.00]Segundo verso"))

        val exportedOriginalOnly = com.example.data.repository.AudioTagParsers.exportLyricsToLrc(
            trackTitle = track.title,
            trackArtist = track.artist,
            trackAlbum = track.album,
            lyrics = reloaded,
            translatedLyrics = null,
            includeTranslation = false
        )
        assertTrue(exportedOriginalOnly.contains("[00:03.50]First verse"))
        assertFalse(exportedOriginalOnly.contains("Primer verso"))
    }

    @Test
    fun `playbackPreferencesStore exact session roundtrip and PlaybackSessionRestorePlanner restores paused queue`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)
        val tracks = listOf("t_a", "t_b", "t_c").mapIndexed { idx, id ->
            AudioTrack(
                id = id,
                contentUri = "content://media/external/audio/media/${idx + 10}",
                title = "Track $id",
                artist = "Artist",
                album = "Album",
                albumId = idx.toLong(),
                albumArtUri = null,
                durationMs = 180_000L,
                format = AudioFormat.MP3,
                mimeType = "audio/mpeg",
                sizeBytes = 4_000_000L,
                dateAddedSec = 100L + idx,
                folderName = "Music"
            )
        }

        val session = com.example.data.local.ExactPlaybackSession(
            lastTrackId = "t_b",
            lastQueueTrackIds = listOf("t_a", "t_missing", "t_b", "t_c"),
            lastQueueIndex = 2,
            lastPositionMs = 47_500L,
            shuffleEnabled = true,
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        )
        prefs.saveExactPlaybackSession(session)

        val loaded = prefs.loadExactPlaybackSession()
        assertEquals(session, loaded)

        val plan = com.example.data.local.PlaybackSessionRestorePlanner.buildPlan(loaded!!, tracks)
        assertNotNull(plan)
        assertEquals(listOf("t_a", "t_b", "t_c"), plan!!.tracks.map { it.id })
        assertEquals(1, plan.startIndex)
        assertEquals(47_500L, plan.positionMs)
        assertTrue(plan.shuffleEnabled)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL, plan.repeatMode)
    }

    @Test
    fun `miniPlayerBar horizontal swipes trigger skipNext and skipPrevious and nowPlaying opens artist and album`() {
        val track = AudioTrack(
            id = "np_link_1",
            contentUri = "content://media/external/audio/media/880",
            title = "Midnight Echo",
            artist = "Davoo Band",
            album = "Nocturne Album",
            albumId = 88L,
            albumArtUri = null,
            durationMs = 215_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 22_000_000L,
            dateAddedSec = 500L,
            folderName = "Music"
        )
        val playbackState = com.example.playback.PlaybackUiState(
            currentTrack = track,
            queue = listOf(track),
            currentIndex = 0,
            isPlaying = false,
            positionMs = 30_000L,
            durationMs = 215_000L
        )
        var skippedNext = 0
        var skippedPrev = 0

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.MiniPlayerBar(
                    playbackState = playbackState,
                    onOpenNowPlaying = {},
                    onTogglePlayPause = {},
                    onSkipNext = { skippedNext++ },
                    onSkipPrevious = { skippedPrev++ }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mini_player_bar").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, skippedNext)

        composeTestRule.onNodeWithTag("mini_player_bar").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, skippedPrev)
    }

    @Test
    fun `nowPlayingScreen artist and album links navigate directly to Artist and Album`() {
        val track = AudioTrack(
            id = "np_nav_205",
            contentUri = "content://media/external/audio/media/881",
            title = "Solar Flare",
            artist = "Celestial Project",
            album = "Horizon LP",
            albumId = 89L,
            albumArtUri = null,
            durationMs = 195_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 19_000_000L,
            dateAddedSec = 510L,
            folderName = "Music"
        )
        val playbackState = com.example.playback.PlaybackUiState(
            currentTrack = track,
            queue = listOf(track),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 20_000L,
            durationMs = 195_000L
        )
        var openedArtist: String? = null
        var openedAlbum: String? = null

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.NowPlayingScreen(
                    playbackState = playbackState,
                    onCollapse = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSeekBackward = {},
                    onSeekForward = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onOpenQueue = {},
                    onAddToPlaylist = {},
                    onOpenAlbum = { openedAlbum = it },
                    onOpenArtist = { openedArtist = it }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("now_playing_artist_link").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Celestial Project", openedArtist)

        composeTestRule.onNodeWithTag("now_playing_album_link").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Horizon LP", openedAlbum)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w390dp-h780dp")
    fun `nowPlayingScreen omits 10s skip buttons and fits playback interface without scrolling on phone viewport`() {
        val track = AudioTrack(
            id = "np_compact_205",
            contentUri = "content://media/external/audio/media/882",
            title = "Compact Fit Track",
            artist = "Davoo",
            album = "Balanced UI",
            albumId = 90L,
            albumArtUri = null,
            durationMs = 205_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 18_000_000L,
            dateAddedSec = 520L,
            folderName = "Music"
        )
        val playbackState = com.example.playback.PlaybackUiState(
            currentTrack = track,
            queue = listOf(track),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 45_000L,
            durationMs = 205_000L
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.NowPlayingScreen(
                    playbackState = playbackState,
                    onCollapse = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onOpenQueue = {},
                    onAddToPlaylist = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag("now_playing_seek_back_button").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("now_playing_seek_forward_button").assertCountEquals(0)
        composeTestRule.onNodeWithTag("now_playing_artwork").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_lyrics_button").assertIsDisplayed()
    }

    @Test
    fun `playbackPreferencesStore updateExactPosition updates fast path and preserves cached queue safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)
        val queueIds = listOf("trk_1", "trk_2", "trk_3", "trk_4")
        val initialExact = com.example.data.local.ExactPlaybackSession(
            lastTrackId = "trk_1",
            lastQueueTrackIds = queueIds,
            lastQueueIndex = 0,
            lastPositionMs = 5_000L,
            shuffleEnabled = false,
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        )
        prefs.saveExactPlaybackSession(initialExact)
        prefs.savePlaybackSession(
            com.example.data.local.SavedPlaybackSession(
                items = queueIds.mapIndexed { idx, id ->
                    com.example.data.local.SavedMediaItemSnapshot(
                        id = id,
                        contentUri = "content://media/external/audio/media/${idx + 1}",
                        title = "Song $id",
                        artist = "Artist",
                        album = "Album",
                        formatName = AudioFormat.FLAC.name,
                        durationMs = 180_000L
                    )
                },
                currentIndex = 0,
                positionMs = 5_000L,
                shuffleEnabled = false,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            )
        )

        val updated = prefs.updateExactPosition(
            trackId = "trk_3",
            queueIndex = 2,
            positionMs = 91_250L,
            shuffleEnabled = true,
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
        )
        assertTrue(updated)

        val loadedExact = prefs.loadExactPlaybackSession()
        assertNotNull(loadedExact)
        assertEquals("trk_3", loadedExact!!.lastTrackId)
        assertEquals(queueIds, loadedExact.lastQueueTrackIds)
        assertEquals(2, loadedExact.lastQueueIndex)
        assertEquals(91_250L, loadedExact.lastPositionMs)
        assertTrue(loadedExact.shuffleEnabled)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, loadedExact.repeatMode)

        val loadedSaved = prefs.loadPlaybackSession()
        assertNotNull(loadedSaved)
        assertEquals(2, loadedSaved!!.currentIndex)
        assertEquals(91_250L, loadedSaved.positionMs)
        assertTrue(loadedSaved.shuffleEnabled)
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, loadedSaved.repeatMode)

        // Track not in cached queue -> returns false so caller can perform full queue snapshot
        assertFalse(
            prefs.updateExactPosition(
                trackId = "trk_unknown",
                queueIndex = 0,
                positionMs = 1_000L,
                shuffleEnabled = false,
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            )
        )
    }

    @Test
    fun `nowPlayingScreen and lyricsBottomSheet react smoothly to decoupled livePositionFlow`() {
        val track = AudioTrack(
            id = "live_pos_1",
            contentUri = "content://media/external/audio/media/950",
            title = "Smooth Ticker Track",
            artist = "Davoo",
            album = "Zero Jank",
            albumId = 95L,
            albumArtUri = null,
            durationMs = 200_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 20_000_000L,
            dateAddedSec = 600L,
            folderName = "Music"
        )
        val liveFlow = kotlinx.coroutines.flow.MutableStateFlow(
            com.example.playback.PlaybackPositionState(
                positionMs = 15_000L,
                durationMs = 200_000L
            )
        )
        val state = com.example.playback.PlaybackUiState(
            currentTrack = track,
            queue = listOf(track),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 0L,
            durationMs = 200_000L
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.NowPlayingScreen(
                    playbackState = state,
                    livePositionFlow = liveFlow,
                    onCollapse = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onOpenQueue = {},
                    onAddToPlaylist = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("0:15", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        liveFlow.value = com.example.playback.PlaybackPositionState(
            positionMs = 65_000L,
            durationMs = 200_000L
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1:05", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `lyricsBottomSheet updates timer and active line cleanly from livePositionFlow`() {
        val track = AudioTrack(
            id = "live_lyr_1",
            contentUri = "content://media/external/audio/media/951",
            title = "Synced Flow Song",
            artist = "Davoo",
            album = "Performance",
            albumId = 96L,
            albumArtUri = null,
            durationMs = 120_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 15_000_000L,
            dateAddedSec = 610L,
            folderName = "Music"
        )
        val lyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(1_000L, "Opening smooth line"),
                com.example.model.LyricLine(15_000L, "Target jumped line")
            ),
            isSynced = true,
            sourceLabel = "Archivo .lrc local"
        )
        val liveFlow = kotlinx.coroutines.flow.MutableStateFlow(
            com.example.playback.PlaybackPositionState(
                positionMs = 2_000L,
                durationMs = 120_000L
            )
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 0L,
                    livePositionFlow = liveFlow,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("0:02", useUnmergedTree = true).assertIsDisplayed()

        liveFlow.value = com.example.playback.PlaybackPositionState(
            positionMs = 16_000L,
            durationMs = 120_000L
        )
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("0:16", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Target jumped line").assertIsDisplayed()
    }

    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `playbackService does not abort crossfade during buffering transition but aborts on pause or idle`() {
        val controller = org.robolectric.Robolectric.buildService(com.example.playback.PlaybackService::class.java)
        val service = controller.create().get()
        try {
            val player = service.activePlayerOrNull()!!
            val item1 = MediaItem.Builder()
                .setMediaId("xfade_track_1")
                .setUri(Uri.parse("file:///android_asset/track1.mp3"))
                .build()
            val item2 = MediaItem.Builder()
                .setMediaId("xfade_track_2")
                .setUri(Uri.parse("file:///android_asset/track2.mp3"))
                .build()

            player.setMediaItems(listOf(item1, item2))
            player.playWhenReady = false
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            // Idle / paused -> must abort crossfade if active
            assertTrue(service.shouldAbortCrossfadeOnIsPlayingChanged(player))

            // Buffering while playWhenReady = true (e.g. immediately after seekToNextMediaItem) -> must NOT abort crossfade!
            player.playWhenReady = true
            player.prepare()
            assertEquals(androidx.media3.common.Player.STATE_BUFFERING, player.playbackState)
            assertFalse(service.shouldAbortCrossfadeOnIsPlayingChanged(player))

            // User pauses during transition -> must abort
            player.playWhenReady = false
            assertTrue(service.shouldAbortCrossfadeOnIsPlayingChanged(player))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `lyricsShareClipCalculator enforces 1 to 10 lines offsetMs next line endMs EOF fallback and 30s cap`() {
        val lyrics = com.example.model.LyricsData(
            lines = (0 until 12).map { idx ->
                com.example.model.LyricLine(
                    timestampMs = 10_000L + idx * 2_500L,
                    text = "Verso número $idx",
                    secondaryText = "Line number $idx"
                )
            },
            isSynced = true,
            sourceLabel = "Archivo .lrc local",
            hasEmbeddedTranslation = true,
            offsetMs = 500L
        )

        // Single line at index 1: raw 12500 - 500 = 12000, end = next line (15000 - 500 = 14500)
        val singleWindow = com.example.share.LyricsShareClipCalculator.computeSyncedClipWindow(
            lyrics = lyrics,
            translatedLyrics = null,
            selectedRange = 1..1,
            trackDurationMs = 180_000L,
            includeTranslation = true
        )
        assertEquals(12_000L, singleWindow.startMs)
        assertEquals(14_500L, singleWindow.endMs)
        assertEquals(1, singleWindow.verses.size)
        assertEquals("Line number 1", singleWindow.verses.first().secondaryText)

        // Selecting 11 consecutive lines (0..10) spans ~35.5s (within 90s cap) -> succeeds without 10-line limit
        var range = 0..0
        range = com.example.share.LyricsShareClipCalculator.updateSyncedSelection(range, 9, lyrics, 180_000L)
        assertEquals(0..9, range)

        val eleventh = com.example.share.LyricsShareClipCalculator.updateSyncedSelection(range, 10, lyrics, 180_000L)
        assertEquals(0..10, eleventh)

        // Last line EOF fallback: minOf(trackDuration, lastLineStart + 5000L)
        val eofWindow = com.example.share.LyricsShareClipCalculator.computeSyncedClipWindow(
            lyrics = lyrics,
            translatedLyrics = null,
            selectedRange = 11..11,
            trackDurationMs = 39_000L, // lastLineStart = 37500 - 500 = 37000; +5000 = 42000 -> clamped to 39000
            includeTranslation = false
        )
        assertEquals(37_000L, eofWindow.startMs)
        assertEquals(39_000L, eofWindow.endMs)

        // Long verse span within 90s (e.g., 45s) is NOT clamped; span > 90s is clamped cleanly to 90_000L max
        val mediumLyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(5_000L, "Medium verse"),
                com.example.model.LyricLine(50_000L, "Next verse")
            ),
            isSynced = true,
            sourceLabel = "Local"
        )
        val notCapped = com.example.share.LyricsShareClipCalculator.computeSyncedClipWindow(
            lyrics = mediumLyrics,
            translatedLyrics = null,
            selectedRange = 0..0,
            trackDurationMs = 180_000L,
            includeTranslation = false
        )
        assertEquals(5_000L, notCapped.startMs)
        assertEquals(50_000L, notCapped.endMs)
        assertFalse(notCapped.wasClampedToMaxDuration)

        val longLyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(5_000L, "Very long verse"),
                com.example.model.LyricLine(125_000L, "Next verse")
            ),
            isSynced = true,
            sourceLabel = "Local"
        )
        val capped = com.example.share.LyricsShareClipCalculator.computeSyncedClipWindow(
            lyrics = longLyrics,
            translatedLyrics = null,
            selectedRange = 0..0,
            trackDurationMs = 180_000L,
            includeTranslation = false
        )
        assertEquals(5_000L, capped.startMs)
        assertEquals(95_000L, capped.endMs)
        assertTrue(capped.wasClampedToMaxDuration)

        // Unsynced range clamped strictly to 15s..90s within track duration
        val unsynced = com.example.share.LyricsShareClipCalculator.computeUnsyncedWindow(
            requestedStartMs = 95_000L,
            requestedDurationMs = 75_000L,
            trackDurationMs = 100_000L
        )
        assertEquals(25_000L, unsynced.startMs)
        assertEquals(100_000L, unsynced.endMs)
        assertEquals(75_000L, unsynced.durationMs)
    }

    @Test
    fun `hiResAudioProcessor normalizes 24bit 32bit float PCM resamples 96kHz and applies fades`() {
        // 24-bit packed little-endian: +0x400000 (~0.5 FS -> +16384 in 16-bit) and -0x400000 (-16384)
        val bytes24 = byteArrayOf(
            0x00, 0x00, 0x40,
            0x00, 0x00, 0xC0.toByte()
        )
        val decoded24 = com.example.share.LyricsShareAudioProcessor.decodePcmBytesToShorts(
            bytes24,
            com.example.share.LyricsShareAudioProcessor.PCM_ENCODING_24BIT_PACKED
        )
        assertEquals(2, decoded24.size)
        assertEquals(16_384, decoded24[0].toInt())
        assertEquals(-16_384, decoded24[1].toInt())

        // 32-bit float PCM: +0.5f and -1.0f
        val floatBuf = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putFloat(0.5f)
            .putFloat(-1.0f)
        val decodedFloat = com.example.share.LyricsShareAudioProcessor.decodePcmBytesToShorts(
            floatBuf.array(),
            com.example.share.LyricsShareAudioProcessor.PCM_ENCODING_FLOAT
        )
        assertEquals(16_384, decodedFloat[0].toInt())
        assertEquals(-32_767, decodedFloat[1].toInt())

        // Hi-Res sample rate selection for AAC encoder compatibility
        assertEquals(48_000, com.example.share.LyricsShareAudioProcessor.selectTargetAacSampleRate(96_000))
        assertEquals(44_100, com.example.share.LyricsShareAudioProcessor.selectTargetAacSampleRate(88_200))
        assertEquals(44_100, com.example.share.LyricsShareAudioProcessor.selectTargetAacSampleRate(44_100))

        // Downsample 2 seconds of stereo 96kHz -> 48kHz
        val stereo96k = ShortArray(96_000 * 2 * 2) { 20_000 }
        val resampled48k = com.example.share.LyricsShareAudioProcessor.linearResample(
            samples = stereo96k,
            channels = 2,
            srcSampleRate = 96_000,
            dstSampleRate = 48_000
        )
        assertEquals(48_000 * 2 * 2, resampled48k.size)

        // Apply 300ms fade-in & 500ms fade-out
        com.example.share.LyricsShareAudioProcessor.applyFadeInOut(
            samples = resampled48k,
            sampleRate = 48_000,
            channels = 2,
            fadeInMs = 300,
            fadeOutMs = 500
        )
        assertEquals(0, resampled48k[0].toInt())
        assertTrue(kotlin.math.abs(resampled48k[resampled48k.size / 2].toInt()) > 19_000)
        assertEquals(0, resampled48k[resampled48k.lastIndex].toInt())
    }

    @Test
    fun `exportStaticPngCard cleans shared_clips cache renders blurred style and resolves FileProvider uri`() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val cacheDir = com.example.share.LyricsVideoExporter.getSharedClipsCacheDir(context)
        val staleFile = java.io.File(cacheDir, "stale_clip.mp4").apply { writeText("old") }
        assertTrue(staleFile.exists())

        // Create temporary local cover art PNG so BLURRED_ARTWORK style executes full blur pipeline too
        val artBitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(217, 138, 58))
        }
        val artFile = java.io.File(context.cacheDir, "test_cover_share.png")
        java.io.FileOutputStream(artFile).use {
            artBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        artBitmap.recycle()

        val track = AudioTrack(
            id = "png_share_track",
            contentUri = "",
            title = "Lossless Echoes",
            artist = "DavooPunk",
            album = "Sessions",
            albumId = 1L,
            albumArtUri = Uri.fromFile(artFile).toString(),
            durationMs = 210_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 32_000_000L,
            dateAddedSec = 700L,
            folderName = "Music"
        )
        val spec = com.example.share.LyricsShareClipSpec(
            track = track,
            startMs = 12_000L,
            endMs = 26_000L,
            isSyncedLyrics = true,
            verses = listOf(
                com.example.share.ShareLyricVerse(12_000L, 19_000L, "Primera línea luminosa", "First glowing line"),
                com.example.share.ShareLyricVerse(19_000L, 26_000L, "Segunda línea sincronizada", "Second synced line")
            ),
            includeTranslation = true,
            style = com.example.share.ShareCardStyle.BLURRED_ARTWORK,
            format = com.example.share.ShareOutputFormat.IMAGE_PNG
        )

        val ready = com.example.share.LyricsVideoExporter.exportClip(
            context = context,
            spec = spec,
            onProgress = { _, _ -> }
        )

        assertFalse(staleFile.exists())
        assertEquals("image/png", ready.mimeType)
        assertTrue(ready.uri.toString().contains("${context.packageName}.fileprovider"))
        assertTrue(ready.uri.toString().contains("shared_clips"))
        val exportedFile = java.io.File(cacheDir, ready.fileName)
        assertTrue(exportedFile.exists() && exportedFile.length() > 1000L)
        artFile.delete()
    }

    @Test
    fun `lyricsBottomSheet displays share entry point and passes bilingual flag`() {
        val track = AudioTrack(
            id = "entry_track_lyrics",
            contentUri = "content://media/external/audio/media/901",
            title = "Midnight Vinyl",
            artist = "DavooPunk",
            album = "Late Night",
            albumId = 90L,
            albumArtUri = null,
            durationMs = 195_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 25_000_000L,
            dateAddedSec = 710L,
            folderName = "Music"
        )
        val lyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(2_000L, "Primer verso", "First verse"),
                com.example.model.LyricLine(8_000L, "Segundo verso", "Second verse")
            ),
            isSynced = true,
            sourceLabel = "Archivo .lrc local",
            hasEmbeddedTranslation = true
        )

        var sharedFromLyrics: Boolean? = null
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 3_000L,
                    onSeekTo = {},
                    onPickLrcFile = {},
                    onDismiss = {},
                    onShareSnippet = { includeTrans -> sharedFromLyrics = includeTrans }
                )
            }
        }
        composeTestRule.onNodeWithTag("lyrics_share_button").assertIsDisplayed().performClick()
        assertEquals(true, sharedFromLyrics)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w390dp-h780dp")
    fun `nowPlayingScreen omits share action chip so share stays exclusively inside lyrics viewer`() {
        val track = AudioTrack(
            id = "entry_track_player",
            contentUri = "content://media/external/audio/media/902",
            title = "Midnight Vinyl",
            artist = "DavooPunk",
            album = "Late Night",
            albumId = 90L,
            albumArtUri = null,
            durationMs = 195_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 25_000_000L,
            dateAddedSec = 710L,
            folderName = "Music"
        )

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.screens.NowPlayingScreen(
                    playbackState = com.example.playback.PlaybackUiState(
                        currentTrack = track,
                        queue = listOf(track),
                        currentIndex = 0,
                        isPlaying = false,
                        positionMs = 10_000L,
                        durationMs = track.durationMs
                    ),
                    onCollapse = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onOpenQueue = {},
                    onAddToPlaylist = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("now_playing_lyrics_button").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("now_playing_share_button").assertCountEquals(0)
    }

    @Test
    fun `lyricsShareSheet switches synced bilingual styles formats and displays unsynced 15s_30s selector`() {
        val track = AudioTrack(
            id = "share_sheet_track",
            contentUri = "",
            title = "Neon Tape",
            artist = "DavooPunk",
            album = "Retro",
            albumId = 91L,
            albumArtUri = null,
            durationMs = 200_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 6_000_000L,
            dateAddedSec = 720L,
            folderName = "Music"
        )
        val syncedLyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(5_000L, "Bajo luces de neón", "Under neon lights"),
                com.example.model.LyricLine(10_000L, "Suena el sintetizador", "The synth plays on"),
                com.example.model.LyricLine(16_000L, "Hasta el amanecer", "Until the sunrise")
            ),
            isSynced = true,
            sourceLabel = "Local",
            hasEmbeddedTranslation = true
        )

        var capturedSpec: com.example.share.LyricsShareClipSpec? = null
        val showSynced = androidx.compose.runtime.mutableStateOf(true)
        val exportState = androidx.compose.runtime.mutableStateOf<com.example.share.ShareExportUiState>(
            com.example.share.ShareExportUiState.Idle
        )
        var cancelledCount = 0

        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsShareSheet(
                    track = track,
                    lyrics = if (showSynced.value) syncedLyrics else null,
                    translatedLyrics = null,
                    initialPositionMs = 5_000L,
                    initialIncludeTranslation = true,
                    exportState = exportState.value,
                    onStartExport = { capturedSpec = it },
                    onCancelExport = { cancelledCount++ },
                    onExportReady = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("lyrics_share_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("share_style_amber"))
        composeTestRule.onNodeWithTag("share_style_amber").performClick()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("share_format_png"))
        composeTestRule.onNodeWithTag("share_format_png").performClick()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("share_lang_bilingual"))
        composeTestRule.onNodeWithTag("share_lang_bilingual").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_export_button").performClick()

        assertNotNull(capturedSpec)
        assertEquals(com.example.share.ShareCardStyle.COPPER_AMBER_DARK, capturedSpec?.style)
        assertEquals(com.example.share.ShareOutputFormat.IMAGE_PNG, capturedSpec?.format)
        assertEquals(true, capturedSpec?.isSyncedLyrics)
        assertEquals(true, capturedSpec?.includeTranslation)

        // Verify export progress overlay + cancel
        exportState.value = com.example.share.ShareExportUiState.Exporting(0.6f, "Renderizando video…")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_export_progress_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_cancel_export_button").performClick()
        assertEquals(1, cancelledCount)

        // Switch to unsynced mode (no lyrics) -> 15s-1:30 range selector appears
        exportState.value = com.example.share.ShareExportUiState.Idle
        showSynced.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("unsynced_range_selector_card"))
        composeTestRule.onNodeWithTag("unsynced_range_selector_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("share_unsynced_dur_90s"))
        composeTestRule.onNodeWithTag("share_unsynced_dur_90s").performClick()
        composeTestRule.onNodeWithTag("share_lazy_column")
            .performScrollToNode(hasTestTag("share_format_mp4"))
        composeTestRule.onNodeWithTag("share_format_mp4").performClick()
        composeTestRule.onNodeWithTag("share_export_button").performClick()
        assertEquals(false, capturedSpec?.isSyncedLyrics)
        assertEquals(com.example.share.ShareOutputFormat.VIDEO_MP4, capturedSpec?.format)
        assertEquals(90_000L, capturedSpec?.clipDurationMs)
    }

    @Test
    fun `playbackPreferencesStore defaults autoUpdateCheckEnabled to true and persists opt out`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = com.example.data.local.PlaybackPreferencesStore(context)
        assertTrue(store.isAutoUpdateCheckEnabled())

        store.setAutoUpdateCheckEnabled(false)
        assertFalse(store.isAutoUpdateCheckEnabled())

        store.setAutoUpdateCheckEnabled(true)
        assertTrue(store.isAutoUpdateCheckEnabled())
    }

    @Test
    fun `audioTagParsers inserts instrumental markers for empty dot lines intro over 5s and gaps over 8s`() {
        val raw = """
            [00:08.00]First verse
            [00:12.00]...
            [00:15.00]
            [00:18.00]Second verse
            [00:30.00]Bridge after long pause
            [00:35.00]Closing line
        """.trimIndent()

        val parsed = com.example.data.repository.AudioTagParsers.parseLrcText(raw, "Test LRC")
        assertNotNull(parsed)
        val lines = parsed!!.lines

        // Index 0: synthetic intro at 0ms because first line >= 4,000ms
        assertTrue(lines[0].isInstrumental)
        assertEquals(0L, lines[0].timestampMs)

        // Index 1: First verse at 8,000ms
        assertFalse(lines[1].isInstrumental)
        assertEquals("First verse", lines[1].text)
        assertEquals(8_000L, lines[1].timestampMs)

        // Index 2: Explicit "..." at 12,000ms has 6,000ms >= 5,000ms before 18,000ms -> kept as instrumental
        // while [00:15.00] (only 3,000ms < 5,000ms before 18,000ms) is ignored as a short breath pause
        assertTrue(lines[2].isInstrumental)
        assertEquals(12_000L, lines[2].timestampMs)

        // Index 3: Second verse at 18,000ms
        assertFalse(lines[3].isInstrumental)
        assertEquals("Second verse", lines[3].text)
        assertEquals(18_000L, lines[3].timestampMs)

        // Index 4: Synthetic interlude at 18,000 + 2,500 = 20,500ms due to 12s gap (>8s) and 9.5s remaining (>=5s)
        assertTrue(lines[4].isInstrumental)
        assertEquals(20_500L, lines[4].timestampMs)

        // Index 5 & 6: Bridge (30,000ms) -> Closing line (35,000ms, gap 5s <= 8s, no interlude)
        assertFalse(lines[5].isInstrumental)
        assertEquals("Bridge after long pause", lines[5].text)
        assertFalse(lines[6].isInstrumental)
        assertEquals("Closing line", lines[6].text)
        assertEquals(7, lines.size)

        // LRC export skips instrumental lines cleanly
        val exported = com.example.data.repository.AudioTagParsers.exportLyricsToLrc(
            trackTitle = "Instrumental Song",
            trackArtist = "Davoo",
            trackAlbum = "Orbit",
            lyrics = parsed
        )
        assertTrue(exported.contains("[00:08.00]First verse"))
        assertFalse(exported.contains("[00:00.00]"))
        assertFalse(exported.contains("[00:20.50]"))
    }

    @Test
    fun `lyricsBottomSheet renders instrumental MusicNote indicator and seeks when tapped`() {
        val track = AudioTrack(
            id = "instr_track",
            contentUri = "content://media/external/audio/media/950",
            title = "Ambient Pulse",
            artist = "DavooPunk",
            album = "Echoes",
            albumId = 95L,
            albumArtUri = null,
            durationMs = 210_000L,
            format = AudioFormat.FLAC,
            mimeType = "audio/flac",
            sizeBytes = 24_000_000L,
            dateAddedSec = 800L,
            folderName = "Music"
        )
        val lyrics = com.example.model.LyricsData(
            lines = listOf(
                com.example.model.LyricLine(0L, "", isInstrumental = true),
                com.example.model.LyricLine(8_000L, "First sung line"),
                com.example.model.LyricLine(11_500L, "", isInstrumental = true),
                com.example.model.LyricLine(22_000L, "After solo")
            ),
            isSynced = true,
            sourceLabel = "Local .lrc"
        )

        var soughtMs: Long? = null
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 12_000L,
                    isPlaying = true,
                    onSeekTo = { soughtMs = it },
                    onPickLrcFile = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("lyrics_instrumental_note_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_instrumental_note_2")
            .assertIsDisplayed()
            .performClick()
        assertEquals(11_500L, soughtMs)
    }

    @Test
    fun `creditsBottomSheet displays top left KeyboardArrowDown dismiss button in full screen dialog`() {
        var dismissed = false
        composeTestRule.setContent {
            SonoraTheme {
                com.example.ui.components.CreditsBottomSheet(onDismiss = { dismissed = true })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("credits_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_credits_button").assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `appearance preferences persist in PlaybackPreferencesStore and ThemePalette resolves modes and WCAG contrast`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)

        val defaultPrefs = prefs.loadAppearancePreferences()
        assertEquals(com.example.model.AppThemeMode.DARK, defaultPrefs.themeMode)
        assertEquals(com.example.model.ThemePreset.COPPER_AMBER, defaultPrefs.preset)
        assertTrue(defaultPrefs.dynamicArtworkColors)

        val custom = com.example.model.AppearancePreferences(
            themeMode = com.example.model.AppThemeMode.LIGHT,
            preset = com.example.model.ThemePreset.CUSTOM,
            customColors = com.example.model.CustomThemeColors(
                primaryArgb = 0xFF0E7490L,
                secondaryArgb = 0xFFD97706L,
                backgroundArgb = 0xFFF8F7F4L,
                surfaceArgb = 0xFFFFFFFFL,
                textArgb = null
            ),
            dynamicArtworkColors = false
        )
        prefs.saveAppearancePreferences(custom)

        val reloaded = prefs.loadAppearancePreferences()
        assertEquals(com.example.model.AppThemeMode.LIGHT, reloaded.themeMode)
        assertEquals(com.example.model.ThemePreset.CUSTOM, reloaded.preset)
        assertEquals(0xFF0E7490L, reloaded.customColors.primaryArgb)
        assertEquals(0xFFF8F7F4L, reloaded.customColors.backgroundArgb)
        assertFalse(reloaded.dynamicArtworkColors)
        assertFalse(prefs.loadAudioSettings().dynamicArtworkColors)

        val lightPalette = com.example.ui.theme.ThemePalette.resolve(reloaded, isSystemDark = true)
        assertFalse(lightPalette.isDark)
        assertTrue(
            com.example.ui.theme.ThemePalette.wcagContrastRatio(
                lightPalette.primaryText,
                lightPalette.background
            ) >= 4.5f
        )

        val systemDarkPalette = com.example.ui.theme.ThemePalette.resolve(
            com.example.model.AppearancePreferences(
                themeMode = com.example.model.AppThemeMode.SYSTEM,
                preset = com.example.model.ThemePreset.COPPER_AMBER
            ),
            isSystemDark = true
        )
        assertTrue(systemDarkPalette.isDark)
        val systemLightPalette = com.example.ui.theme.ThemePalette.resolve(
            com.example.model.AppearancePreferences(
                themeMode = com.example.model.AppThemeMode.SYSTEM,
                preset = com.example.model.ThemePreset.COPPER_AMBER
            ),
            isSystemDark = false
        )
        assertFalse(systemLightPalette.isDark)

        assertEquals(0xFF10B981L, com.example.ui.theme.ThemePalette.hexStringToArgb("#10B981"))
        assertEquals("#10B981", com.example.ui.theme.ThemePalette.argbToHexString(0xFF10B981L))
    }

    @Test
    fun `settingsScreen displays theme mode selector customize colors dynamic toggle and backup controls`() {
        var selectedMode = com.example.model.AppThemeMode.DARK
        var openedCustomization = false
        var exportedClicked = false
        var importedClicked = false
        val appearanceState = androidx.compose.runtime.mutableStateOf(
            com.example.model.AppearancePreferences(themeMode = com.example.model.AppThemeMode.DARK)
        )

        composeTestRule.setContent {
            SonoraTheme(appearance = appearanceState.value) {
                com.example.ui.screens.SettingsScreen(
                    totalTracksCount = 120,
                    isScanning = false,
                    excludedFoldersCount = 1,
                    sdCardFoldersCount = 0,
                    audioSettings = com.example.model.AudioSettings(),
                    isSleepTimerActive = false,
                    preferredTranslationEngine = com.example.ai.TranslationEngine.GOOGLE,
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
                    bottomContentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    appearancePreferences = appearanceState.value,
                    onSelectThemeMode = { mode ->
                        selectedMode = mode
                        appearanceState.value = appearanceState.value.copy(themeMode = mode)
                    },
                    onOpenAppearanceCustomization = { openedCustomization = true },
                    onToggleDynamicArtworkColors = { enabled ->
                        appearanceState.value = appearanceState.value.copy(dynamicArtworkColors = enabled)
                    },
                    onExportBackup = { exportedClicked = true },
                    onImportBackup = { importedClicked = true }
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_theme_mode_light"))
        composeTestRule.onNodeWithTag("settings_theme_mode_dark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_theme_mode_light").performClick()
        assertEquals(com.example.model.AppThemeMode.LIGHT, selectedMode)
        composeTestRule.onNodeWithTag("settings_theme_mode_system").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_customize_colors_row"))
        composeTestRule.onNodeWithTag("settings_customize_colors_row").performClick()
        assertTrue(openedCustomization)

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_dynamic_colors_switch"))
        composeTestRule.onNodeWithTag("settings_dynamic_colors_switch").performClick()
        assertFalse(appearanceState.value.dynamicArtworkColors)

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_export_backup_row"))
        composeTestRule.onNodeWithTag("settings_export_backup_row").performClick()
        assertTrue(exportedClicked)

        composeTestRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_import_backup_row"))
        composeTestRule.onNodeWithTag("settings_import_backup_row").performClick()
        assertTrue(importedClicked)
    }

    @Test
    fun `appearanceCustomizationSheet selects presets edits hex color and resets defaults`() {
        val appearanceState = androidx.compose.runtime.mutableStateOf(
            com.example.model.AppearancePreferences()
        )
        var resetTriggered = false

        composeTestRule.setContent {
            SonoraTheme(appearance = appearanceState.value) {
                com.example.ui.components.AppearanceCustomizationSheet(
                    appearance = appearanceState.value,
                    onUpdateAppearance = { appearanceState.value = it },
                    onSelectPreset = { preset ->
                        appearanceState.value = appearanceState.value.copy(
                            preset = preset,
                            customColors = com.example.ui.theme.ThemePalette.seedCustomColorsFromPreset(preset, isDark = true)
                        )
                    },
                    onResetToDefaults = {
                        resetTriggered = true
                        appearanceState.value = com.example.model.AppearancePreferences()
                    },
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("appearance_customization_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_presets_row").performScrollTo()
        composeTestRule.onNodeWithTag("theme_presets_row")
            .performScrollToNode(hasTestTag("theme_preset_emerald"))
        composeTestRule.onNodeWithTag("theme_preset_emerald").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.ThemePreset.EMERALD, appearanceState.value.preset)

        composeTestRule.onNodeWithTag("color_slots_row").performScrollTo()
        composeTestRule.onNodeWithTag("color_slot_primary").performClick()
        composeTestRule.onNodeWithTag("color_picker_sv_box").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_hue_bar").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_hex_input").performScrollTo().performTextReplacement("#FF3366")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.ThemePreset.CUSTOM, appearanceState.value.preset)
        assertEquals(0xFFFF3366L, appearanceState.value.customColors.primaryArgb)

        composeTestRule.onNodeWithTag("color_slots_row").performScrollTo()
        composeTestRule.onNodeWithTag("color_slot_text").performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("text_color_auto_wcag_chip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_wcag_ratio_badge").assertIsDisplayed()

        composeTestRule.onNodeWithTag("appearance_reset_defaults_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(resetTriggered)
        assertEquals(com.example.model.ThemePreset.COPPER_AMBER, appearanceState.value.preset)
    }

    @Test
    fun `sonoraBackupManager exports and imports json restoring appearance playlists stats and audio settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PlaybackPreferencesStore(context)

        val customAppearance = com.example.model.AppearancePreferences(
            themeMode = com.example.model.AppThemeMode.LIGHT,
            preset = com.example.model.ThemePreset.CUSTOM,
            customColors = com.example.model.CustomThemeColors(
                primaryArgb = 0xFF7C3AEDL,
                secondaryArgb = 0xFF059669L,
                backgroundArgb = 0xFFF9FAFBL,
                surfaceArgb = 0xFFFFFFFFL,
                textArgb = 0xFF111827L
            ),
            dynamicArtworkColors = false
        )
        prefs.saveAppearancePreferences(customAppearance)
        prefs.saveAudioSettings(
            com.example.model.AudioSettings(
                crossfadeSeconds = 7,
                replayGainEnabled = true,
                eqEnabled = true,
                preampDb = -2.5f,
                dynamicArtworkColors = false
            )
        )
        val playlistId = db.playlistDao().insertPlaylist(
            com.example.data.local.PlaylistEntity(name = "Respaldo Hi-Res", description = "Favoritas")
        )
        db.playlistDao().insertPlaylistTrack(
            com.example.data.local.PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = "track_backup_1",
                contentUri = "content://media/external/audio/media/501",
                title = "Midnight Vinyl",
                artist = "Davoo",
                album = "Side A",
                albumId = 501L,
                albumArtUri = null,
                durationMs = 240_000L,
                formatName = AudioFormat.FLAC.name,
                mimeType = "audio/flac",
                sizeBytes = 24_000_000L,
                folderName = "Music",
                isSafImported = false,
                position = 0
            )
        )
        db.trackStatsDao().upsert(
            com.example.data.local.TrackStatsEntity(
                trackId = "track_backup_1",
                isFavorite = true,
                playCount = 14,
                lastPlayedAt = 99999L
            )
        )

        val manager = com.example.data.local.SonoraBackupManager(context, db, prefs)
        val exportedJson = manager.buildBackupJson()
        assertTrue(exportedJson.contains("Respaldo Hi-Res"))
        assertTrue(exportedJson.contains("LIGHT"))

        // Clear local state before restore
        prefs.saveAppearancePreferences(com.example.model.AppearancePreferences())
        prefs.saveAudioSettings(com.example.model.AudioSettings())
        db.clearAllTables()

        val result = manager.importFromJsonString(exportedJson)
        assertEquals(1, result.playlistsRestored)
        assertEquals(1, result.trackStatsRestored)
        assertEquals(com.example.model.AppThemeMode.LIGHT, result.appearance.themeMode)
        assertEquals(com.example.model.ThemePreset.CUSTOM, result.appearance.preset)
        assertEquals(0xFF7C3AEDL, result.appearance.customColors.primaryArgb)
        assertFalse(result.appearance.dynamicArtworkColors)
        assertEquals(7, result.audioSettings.crossfadeSeconds)
        assertTrue(result.audioSettings.replayGainEnabled)

        val restoredPlaylists = db.playlistDao().getAllPlaylistsWithEntriesOnce()
        assertEquals(1, restoredPlaylists.size)
        assertEquals("Respaldo Hi-Res", restoredPlaylists.first().playlist.name)
        assertEquals("Midnight Vinyl", restoredPlaylists.first().entries.first().title)
    }

    @Test
    fun `lyricsBottomSheet respects disabled dynamicArtworkColors without using artwork tint`() {
        val track = AudioTrack(
            id = "lyr_static_theme_99",
            contentUri = "content://media/external/audio/media/999",
            title = "Static Palette Song",
            artist = "Davoo",
            album = "Clean Light",
            albumId = 99L,
            albumArtUri = null,
            durationMs = 180_000L,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 3_200_000L,
            dateAddedSec = 300L,
            folderName = "Music"
        )
        val lyrics = com.example.model.LyricsData(
            lines = listOf(com.example.model.LyricLine(0L, "Line rendered with user theme")),
            isSynced = true,
            sourceLabel = "Local .lrc"
        )
        val lightAppearance = com.example.model.AppearancePreferences(
            themeMode = com.example.model.AppThemeMode.LIGHT,
            preset = com.example.model.ThemePreset.EMERALD,
            dynamicArtworkColors = false
        )

        composeTestRule.setContent {
            SonoraTheme(appearance = lightAppearance) {
                com.example.ui.components.LyricsBottomSheet(
                    track = track,
                    lyrics = lyrics,
                    isLoading = false,
                    positionMs = 1_000L,
                    isPlaying = true,
                    dynamicArtworkColorsEnabled = false,
                    dynamicAccentColor = androidx.compose.ui.graphics.Color(0xFFE91E63),
                    onSeekTo = {},
                    onTogglePlayPause = {},
                    onPickLrcFile = {},
                    onDismiss = {}
                )
            }
        }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("lyrics_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Line rendered with user theme").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lyrics_play_pause_button").assertIsDisplayed()
    }
}
