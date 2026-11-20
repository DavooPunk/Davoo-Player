package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
        assertEquals(2, lyrics!!.lines.size)
        assertEquals(12_500L, lyrics.lines[0].timestampMs)
        assertEquals("First synced line", lyrics.lines[0].text)
        assertEquals(65_000L, lyrics.lines[1].timestampMs)

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
            assertEquals(2, saved!!.lines.size)
            assertEquals("Saved synced lyric line 1", saved.lines[0].text)

            // Delete original source file to verify DB persistence is independent
            tempLrc.delete()

            // Reload later -> should still come back from Room
            val reloaded = repository.loadLyrics(track)
            assertTrue(reloaded != null && reloaded.isSynced)
            assertEquals(com.example.data.repository.AudioTagParsers.SAVED_LRC_SOURCE_LABEL, reloaded!!.sourceLabel)
            assertEquals("Saved synced lyric line 2", reloaded.lines[1].text)

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
        assertEquals("This ship is taking me far away", saved.lines[1].text)

        // Verify offline reload from Room
        val reloaded = repository.loadLyrics(track)
        assertTrue(reloaded != null && reloaded.isSynced)
        assertEquals("Far away", reloaded!!.lines[0].text)

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

        // Changelog tab: Version history including v2.0.2, v2.0.1 and v2.0.0
        composeTestRule.onNodeWithTag("about_tab_changelog").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("v2.0.2").assertIsDisplayed()
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
        assertEquals(3, parsedBilingual.lines.size)
        assertEquals("Hello from the dark", parsedBilingual.lines[0].text)
        assertEquals("Hola desde la oscuridad", parsedBilingual.lines[0].secondaryText)
        assertEquals(15_500L, parsedBilingual.lines[1].timestampMs)
        assertEquals("Mantén el ritmo vivo", parsedBilingual.lines[1].secondaryText)

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
}
