package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat as AndroidAudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.R
import com.example.data.local.ImportedTrackEntity
import com.example.model.AudioFormat
import com.example.model.AudioTechSpecs
import com.example.model.AudioTrack
import com.example.model.AudioTrackMetadata
import com.example.model.SdCardFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class DeviceMusicScanner(private val context: Context) {

    private data class AlbumMetadataSnapshot(
        val artist: String = "",
        val year: Int = 0
    )

    private data class RetrieverTags(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val year: Int = 0,
        val trackNumber: Int = 0,
        val durationMs: Long = 0L,
        val hasEmbeddedArtwork: Boolean = false
    )

    suspend fun scanMediaStoreAudio(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        val seenIds = HashSet<String>()
        val seenUris = HashSet<String>()
        val albumMetadataMap = queryAlbumMetadataMap()

        val volumesToScan: List<Pair<String?, Uri>> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mounted = runCatching { MediaStore.getExternalVolumeNames(context).toList() }
                .getOrDefault(emptyList())
                .filter { it.isNotBlank() }
            if (mounted.isNotEmpty()) {
                // Primary volume first so ms_<id> ids stay canonical before any secondary SD volumes
                val ordered = mounted.sortedBy { if (it == MediaStore.VOLUME_EXTERNAL_PRIMARY) 0 else 1 }
                ordered.map { vol -> vol to MediaStore.Audio.Media.getContentUri(vol) }
            } else {
                listOf(MediaStore.VOLUME_EXTERNAL to MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
            }
        } else {
            listOf(null to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA,
            pathColumn
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.ALBUM_ARTIST)
            projectionList.add(MediaStore.Audio.Media.GENRE)
        }

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownAlbum = context.getString(R.string.unknown_album)

        for ((volumeName, collection) in volumesToScan) {
            coroutineContext.ensureActive()
            val isRemovableVolume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                volumeName != null &&
                volumeName != MediaStore.VOLUME_EXTERNAL_PRIMARY &&
                volumeName != MediaStore.VOLUME_EXTERNAL
            val albumArtBaseUri = if (volumeName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri.parse("content://media/$volumeName/audio/albumart")
            } else {
                Uri.parse("content://media/external/audio/albumart")
            }

            runCatching {
                context.contentResolver.query(
                    collection,
                    projectionList.toTypedArray(),
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (idCol < 0) return@use
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                    val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    val isMusicCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
                    val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                    val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                    val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                    } else -1
                    val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    } else -1
                    @Suppress("DEPRECATION")
                    val dataPathCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val pathCol = cursor.getColumnIndex(pathColumn)

                    var rowCounter = 0
                    while (cursor.moveToNext()) {
                        if (++rowCounter % 32 == 0) {
                            coroutineContext.ensureActive()
                        }
                        runCatching {
                            val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty().lowercase() else ""
                            val displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol).orEmpty() else ""
                            val format = detectSupportedFormat(mime, displayName) ?: return@runCatching

                            val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                            val id = cursor.getLong(idCol)
                            val contentUri = ContentUris.withAppendedId(collection, id)
                            val contentUriStr = contentUri.toString()
                            val trackId = if (isRemovableVolume) "ms_${volumeName}_$id" else "ms_$id"
                            if (contentUriStr in seenUris || trackId in seenIds) return@runCatching

                            val rawTitle = if (titleCol >= 0) cursor.getString(titleCol)?.trim().orEmpty() else ""
                            val rawArtist = if (artistCol >= 0) cursor.getString(artistCol)?.trim().orEmpty() else ""
                            val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol)?.trim().orEmpty() else ""
                            val missingCoreTags = rawTitle.isEmpty() ||
                                rawArtist.isEmpty() || rawArtist == "<unknown>" ||
                                rawAlbum.isEmpty() || rawAlbum == "<unknown>" ||
                                durationMs <= 0L

                            val fallbackTags = if (missingCoreTags) {
                                readRetrieverTags(contentUri)
                            } else null

                            val resolvedDurationMs = when {
                                durationMs > 0L -> durationMs
                                fallbackTags != null && fallbackTags.durationMs > 0L -> fallbackTags.durationMs
                                else -> 0L
                            }
                            val isMusic = if (isMusicCol >= 0) cursor.getInt(isMusicCol) != 0 else true
                            // Skip tiny notification/UI sounds unless explicitly tagged as music
                            if (!isMusic && resolvedDurationMs in 1..4999) return@runCatching

                            val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                            val albumSnapshot = albumMetadataMap[albumId]
                            val albumArtUri = if (albumId > 0L) {
                                ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                            } else null

                            val cleanTitle = rawTitle.ifEmpty {
                                fallbackTags?.title?.takeIf { it.isNotBlank() }
                                    ?: displayName.substringBeforeLast('.').ifEmpty { "Pista $id" }
                            }

                            val cleanArtist = when {
                                rawArtist.isNotEmpty() && rawArtist != "<unknown>" -> rawArtist
                                !fallbackTags?.artist.isNullOrBlank() -> fallbackTags!!.artist!!
                                else -> unknownArtist
                            }

                            val cleanAlbum = when {
                                rawAlbum.isNotEmpty() && rawAlbum != "<unknown>" -> rawAlbum
                                !fallbackTags?.album.isNullOrBlank() -> fallbackTags!!.album!!
                                else -> unknownAlbum
                            }

                            val rawAlbumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol)?.trim().orEmpty() else ""
                            val cleanAlbumArtist = rawAlbumArtist
                                .takeIf { it.isNotEmpty() && it != "<unknown>" }
                                ?: fallbackTags?.albumArtist
                                ?: albumSnapshot?.artist?.takeIf { it.isNotEmpty() && it != "<unknown>" }
                                ?: ""

                            val rawGenre = if (genreCol >= 0) cursor.getString(genreCol)?.trim().orEmpty() else ""
                            val cleanGenre = rawGenre.ifEmpty { fallbackTags?.genre.orEmpty() }

                            val rawYear = if (yearCol >= 0) cursor.getInt(yearCol) else 0
                            val cleanYear = when {
                                rawYear > 0 -> rawYear
                                (fallbackTags?.year ?: 0) > 0 -> fallbackTags!!.year
                                (albumSnapshot?.year ?: 0) > 0 -> albumSnapshot!!.year
                                else -> 0
                            }

                            val rawTrack = if (trackCol >= 0) cursor.getInt(trackCol) else 0
                            val cleanTrackNumber = when {
                                rawTrack > 0 -> rawTrack % 1000
                                (fallbackTags?.trackNumber ?: 0) > 0 -> fallbackTags!!.trackNumber
                                else -> 0
                            }

                            val rawPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                            val filePath = if (dataPathCol >= 0) cursor.getString(dataPathCol).orEmpty() else ""
                            val extractedFolder = extractFolderName(rawPath.ifEmpty { filePath })
                            val folderName = if (isRemovableVolume) {
                                if (extractedFolder == "Música" || extractedFolder.isBlank()) "Tarjeta SD" else "SD · $extractedFolder"
                            } else {
                                extractedFolder
                            }
                            val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
                            val dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol).coerceAtLeast(0L) else 0L

                            seenIds.add(trackId)
                            seenUris.add(contentUriStr)
                            tracks.add(
                                AudioTrack(
                                    id = trackId,
                                    contentUri = contentUriStr,
                                    title = cleanTitle,
                                    artist = cleanArtist,
                                    album = cleanAlbum,
                                    albumId = albumId,
                                    albumArtUri = albumArtUri,
                                    durationMs = resolvedDurationMs.coerceAtLeast(0L),
                                    format = format,
                                    mimeType = mime.ifEmpty { defaultMimeFor(format) },
                                    sizeBytes = sizeBytes,
                                    dateAddedSec = dateAddedSec,
                                    folderName = folderName,
                                    isSafImported = false,
                                    albumArtist = cleanAlbumArtist,
                                    genre = cleanGenre,
                                    year = cleanYear,
                                    trackNumber = cleanTrackNumber,
                                    filePath = filePath
                                )
                            )
                        }
                    }
                }
            }
        }
        tracks
    }

    fun resolveTreeFolderDisplayName(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull().orEmpty()
        val queriedName = runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.trim() else null
            }
        }.getOrNull()

        val rawLabel = queriedName?.takeIf { it.isNotBlank() }
            ?: docId.substringAfter(':', docId).substringAfterLast('/').ifBlank { "Tarjeta SD" }
        val isRemovableRoot = docId.contains(':') && !docId.startsWith("primary:")
        return if (isRemovableRoot && !rawLabel.contains("SD", ignoreCase = true)) {
            "Tarjeta SD ($rawLabel)"
        } else {
            rawLabel
        }
    }

    suspend fun scanDocumentTreeFolders(
        folders: List<SdCardFolder>
    ): Pair<List<AudioTrack>, Map<String, Int>> = withContext(Dispatchers.IO) {
        if (folders.isEmpty()) return@withContext (emptyList<AudioTrack>() to emptyMap())
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownAlbum = context.getString(R.string.unknown_album)
        val discovered = mutableListOf<AudioTrack>()
        val countsByTreeUri = mutableMapOf<String, Int>()
        val seenIds = HashSet<String>()

        val childProjection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        for (rootFolder in folders) {
            coroutineContext.ensureActive()
            val treeUri = runCatching { Uri.parse(rootFolder.treeUri) }.getOrNull() ?: continue
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: continue

            var rootCount = 0
            val queue = ArrayDeque<Triple<String, String, Int>>()
            val visitedDirs = HashSet<String>()
            queue.add(Triple(rootDocId, rootFolder.displayName, 0))

            while (queue.isNotEmpty() && rootCount < MAX_SD_TREE_TRACKS) {
                coroutineContext.ensureActive()
                val (dirDocId, dirLabel, depth) = queue.removeFirst()
                if (!visitedDirs.add(dirDocId) || depth > MAX_SD_TREE_DEPTH) continue

                val childrenUri = runCatching {
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
                }.getOrNull() ?: continue

                runCatching {
                    context.contentResolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        if (idIdx < 0) return@use

                        var counter = 0
                        while (cursor.moveToNext() && rootCount < MAX_SD_TREE_TRACKS) {
                            if (++counter % 24 == 0) coroutineContext.ensureActive()
                            runCatching {
                                val childDocId = cursor.getString(idIdx) ?: return@runCatching
                                val childName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                                val childMime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""

                                if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                    // Skip hidden dot-folders (e.g. .thumbnails, .trash)
                                    if (!childName.startsWith(".")) {
                                        val nextLabel = childName.ifBlank { dirLabel }
                                        queue.add(Triple(childDocId, nextLabel, depth + 1))
                                    }
                                    return@runCatching
                                }

                                val format = detectSupportedFormat(childMime, childName) ?: return@runCatching
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                                val docUriStr = docUri.toString()
                                val trackId = "sd_${stableHash12(docUriStr)}"
                                if (!seenIds.add(trackId)) return@runCatching

                                val tags = readRetrieverTags(docUri)
                                val sizeBytes = if (sizeIdx >= 0) cursor.getLong(sizeIdx).coerceAtLeast(0L) else 0L
                                val modifiedSec = if (modIdx >= 0) {
                                    (cursor.getLong(modIdx) / 1000L).coerceAtLeast(0L)
                                } else 0L

                                val cleanTitle = tags.title?.takeIf { it.isNotBlank() }
                                    ?: childName.substringBeforeLast('.').ifEmpty { "Pista SD" }
                                val cleanArtist = tags.artist?.takeIf { it.isNotBlank() } ?: unknownArtist
                                val cleanAlbum = tags.album?.takeIf { it.isNotBlank() } ?: unknownAlbum
                                val folderBadge = if (dirLabel.startsWith("SD", ignoreCase = true) ||
                                    dirLabel.contains("Tarjeta SD", ignoreCase = true)
                                ) {
                                    dirLabel
                                } else {
                                    "SD · $dirLabel"
                                }

                                discovered.add(
                                    AudioTrack(
                                        id = trackId,
                                        contentUri = docUriStr,
                                        title = cleanTitle,
                                        artist = cleanArtist,
                                        album = cleanAlbum,
                                        albumId = 0L,
                                        albumArtUri = if (tags.hasEmbeddedArtwork) docUriStr else null,
                                        durationMs = tags.durationMs.coerceAtLeast(0L),
                                        format = format,
                                        mimeType = childMime.ifBlank { defaultMimeFor(format) },
                                        sizeBytes = sizeBytes,
                                        dateAddedSec = modifiedSec,
                                        folderName = folderBadge,
                                        isSafImported = false,
                                        albumArtist = tags.albumArtist.orEmpty(),
                                        genre = tags.genre.orEmpty(),
                                        year = tags.year,
                                        trackNumber = tags.trackNumber,
                                        filePath = childName,
                                        isDocumentTreeTrack = true,
                                        sourceTreeUri = rootFolder.treeUri
                                    )
                                )
                                rootCount++
                            }
                        }
                    }
                }
            }
            countsByTreeUri[rootFolder.treeUri] = rootCount
        }
        discovered to countsByTreeUri
    }

    private fun stableHash12(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun queryAlbumMetadataMap(): Map<Long, AlbumMetadataSnapshot> {
        val result = mutableMapOf<Long, AlbumMetadataSnapshot>()
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.FIRST_YEAR
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Albums._ID)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Albums.FIRST_YEAR)
                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val artist = if (artistCol >= 0) cursor.getString(artistCol)?.trim().orEmpty() else ""
                    val year = if (yearCol >= 0) cursor.getInt(yearCol) else 0
                    result[id] = AlbumMetadataSnapshot(artist = artist, year = year)
                }
            }
        }
        return result
    }

    suspend fun inspectSafUris(uris: List<Uri>): List<ImportedTrackEntity> = withContext(Dispatchers.IO) {
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownAlbum = context.getString(R.string.unknown_album)
        val imported = mutableListOf<ImportedTrackEntity>()

        uris.forEach { uri ->
            runCatching {
                var displayName = ""
                var sizeBytes = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx).orEmpty()
                        if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
                val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
                val format = detectSupportedFormat(mime, displayName) ?: return@runCatching

                val tags = readRetrieverTags(uri)
                val title = tags.title?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.').ifEmpty { "Pista importada" }
                val artist = tags.artist?.takeIf { it.isNotBlank() } ?: unknownArtist
                val album = tags.album?.takeIf { it.isNotBlank() } ?: unknownAlbum
                val durationMs = tags.durationMs

                imported.add(
                    ImportedTrackEntity(
                        uri = uri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs.coerceAtLeast(0L),
                        formatName = format.name,
                        mimeType = mime.ifEmpty {
                            if (format == AudioFormat.FLAC) "audio/flac" else "audio/mpeg"
                        },
                        sizeBytes = sizeBytes.coerceAtLeast(0L),
                        folderName = "Archivos importados"
                    )
                )
            }
        }
        imported
    }

    suspend fun extractTrackMetadata(track: AudioTrack): AudioTrackMetadata = withContext(Dispatchers.IO) {
        val uri = Uri.parse(track.contentUri)
        val tags = readRetrieverTags(uri)
        val specs = extractTechSpecs(track.contentUri)
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownAlbum = context.getString(R.string.unknown_album)

        val resolvedTitle = tags.title?.takeIf { it.isNotBlank() } ?: track.title
        val resolvedArtist = tags.artist?.takeIf { it.isNotBlank() }
            ?: track.artist.takeIf { it.isNotBlank() }
            ?: unknownArtist
        val resolvedAlbum = tags.album?.takeIf { it.isNotBlank() }
            ?: track.album.takeIf { it.isNotBlank() }
            ?: unknownAlbum
        val resolvedAlbumArtist = tags.albumArtist?.takeIf { it.isNotBlank() }
            ?: track.albumArtist.takeIf { it.isNotBlank() }
            ?: resolvedArtist
        val resolvedGenre = tags.genre?.takeIf { it.isNotBlank() } ?: track.genre
        val resolvedYear = if (tags.year > 0) tags.year else track.year
        val resolvedTrackNumber = if (tags.trackNumber > 0) tags.trackNumber else track.trackNumber
        val resolvedDurationMs = if (tags.durationMs > 0L) tags.durationMs else track.durationMs
        val replayGainDb = track.replayGainDb ?: AudioTagParsers.extractReplayGainDb(context, uri)

        AudioTrackMetadata(
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedAlbum,
            albumArtist = resolvedAlbumArtist,
            genre = resolvedGenre,
            year = resolvedYear,
            trackNumber = resolvedTrackNumber,
            durationMs = resolvedDurationMs,
            format = track.format,
            mimeType = track.mimeType,
            sizeBytes = track.sizeBytes,
            folderName = track.folderName,
            hasEmbeddedArtwork = tags.hasEmbeddedArtwork,
            techSpecs = specs,
            replayGainDb = replayGainDb
        )
    }

    suspend fun extractTechSpecs(uriString: String): AudioTechSpecs = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        var sampleRate: Int? = null
        var channels: Int? = null
        var bitrate: Int? = null
        var bitsPerSample: Int? = null

        val retriever = MediaMetadataRetriever()
        try {
            runCatching {
                retriever.setDataSource(context, uri)
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                    bitsPerSample = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                }
            }
        } finally {
            runCatching { retriever.release() }
        }

        val extractor = MediaExtractor()
        try {
            runCatching {
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        if (sampleRate == null && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (bitrate == null && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                        }
                        if (bitsPerSample == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ) {
                            bitsPerSample = when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                                AndroidAudioFormat.ENCODING_PCM_16BIT -> 16
                                AndroidAudioFormat.ENCODING_PCM_FLOAT -> 32
                                else -> null
                            }
                        }
                        break
                    }
                }
            }
        } finally {
            runCatching { extractor.release() }
        }

        AudioTechSpecs(
            sampleRateHz = sampleRate,
            bitrateBps = bitrate,
            channelCount = channels,
            bitsPerSample = bitsPerSample
        )
    }

    private fun readRetrieverTags(uri: Uri): RetrieverTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()
            val yearRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val year = parseYear(yearRaw)
            val trackRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val trackNumber = parseTrackNumber(trackRaw)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L) ?: 0L
            val hasEmbedded = runCatching { retriever.embeddedPicture != null }.getOrDefault(false)

            RetrieverTags(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                year = year,
                trackNumber = trackNumber,
                durationMs = durationMs,
                hasEmbeddedArtwork = hasEmbedded
            )
        } catch (_: Throwable) {
            RetrieverTags()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun parseYear(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val match = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(raw)
        return match?.value?.toIntOrNull() ?: 0
    }

    private fun parseTrackNumber(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val beforeSlash = raw.substringBefore('/').trim()
        return beforeSlash.toIntOrNull()?.takeIf { it > 0 } ?: 0
    }

    private fun defaultMimeFor(format: AudioFormat): String = when (format) {
        AudioFormat.FLAC -> "audio/flac"
        AudioFormat.MP3 -> "audio/mpeg"
        AudioFormat.WAV -> "audio/wav"
        AudioFormat.AAC -> "audio/mp4"
        AudioFormat.OGG -> "audio/ogg"
    }

    private fun detectSupportedFormat(mimeType: String, displayName: String): AudioFormat? {
        val lowerName = displayName.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime == "audio/flac" ||
                lowerMime == "audio/x-flac" ||
                lowerMime == "application/flac" ||
                lowerName.endsWith(".flac") -> AudioFormat.FLAC

            lowerMime == "audio/mpeg" ||
                lowerMime == "audio/mp3" ||
                lowerMime == "audio/x-mp3" ||
                lowerMime == "audio/mpeg3" ||
                lowerName.endsWith(".mp3") -> AudioFormat.MP3

            lowerMime == "audio/wav" ||
                lowerMime == "audio/x-wav" ||
                lowerMime == "audio/wave" ||
                lowerName.endsWith(".wav") -> AudioFormat.WAV

            lowerMime == "audio/mp4" ||
                lowerMime == "audio/aac" ||
                lowerMime == "audio/x-m4a" ||
                lowerMime == "audio/m4a" ||
                lowerName.endsWith(".m4a") ||
                lowerName.endsWith(".aac") ||
                lowerName.endsWith(".alac") -> AudioFormat.AAC

            lowerMime == "audio/ogg" ||
                lowerMime == "audio/opus" ||
                lowerMime == "application/ogg" ||
                lowerName.endsWith(".ogg") ||
                lowerName.endsWith(".opus") -> AudioFormat.OGG

            else -> null
        }
    }

    private fun extractFolderName(rawPath: String): String {
        if (rawPath.isBlank()) return "Música"
        val trimmed = rawPath.trimEnd('/')
        return if (trimmed.contains('/')) {
            val folder = File(trimmed).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    trimmed.substringAfterLast('/')
                } else {
                    it.parentFile?.name ?: "Música"
                }
            }
            folder.ifBlank { "Música" }
        } else {
            trimmed
        }
    }

    companion object {
        private const val MAX_SD_TREE_DEPTH = 16
        private const val MAX_SD_TREE_TRACKS = 5000
        private val durationCache = LruCache<String, Long>(256)
        private val durationExtractorSemaphore = kotlinx.coroutines.sync.Semaphore(permits = 2)
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun evictDurationsAsync(uris: Collection<String>) {
            if (uris.isEmpty()) return
            val targets = uris.toList()
            cleanupScope.launch {
                targets.forEach { uri -> durationCache.remove(uri) }
            }
        }

        fun trimMemory(level: Int) {
            when {
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> durationCache.evictAll()
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> durationCache.trimToSize(64)
            }
        }

        suspend fun extractDurationFromFile(context: Context, uri: Uri): Long? {
            val key = uri.toString()
            if (key.isBlank()) return null
            durationCache.get(key)?.let { return it }
            return withContext(Dispatchers.IO) {
                durationCache.get(key)?.let { return@withContext it }
                durationExtractorSemaphore.withPermit {
                    durationCache.get(key)?.let { return@withPermit it }
                    extractDurationWithRetriever(context, uri)?.also { duration ->
                        if (duration > 0L) {
                            durationCache.put(key, duration)
                        }
                    }
                }
            }
        }

        private fun extractDurationWithRetriever(context: Context, uri: Uri): Long? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
}
