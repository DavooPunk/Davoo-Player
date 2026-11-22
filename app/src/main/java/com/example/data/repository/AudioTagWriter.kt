package com.example.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

data class TrackFileNameParts(
    val baseName: String,
    val extensionWithDot: String,
    val fullFileName: String
)

data class TrackTagEditRequest(
    val track: AudioTrack,
    val newFileNameBase: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val customArtworkUri: String?
)

sealed interface PhysicalTagEditOutcome {
    data class Success(
        val updatedTrack: AudioTrack,
        val physicalTagsWritten: Boolean,
        val renamedFile: Boolean
    ) : PhysicalTagEditOutcome

    data class NeedsWritePermission(
        val intentSender: IntentSender
    ) : PhysicalTagEditOutcome

    data class Failure(
        val reason: String
    ) : PhysicalTagEditOutcome
}

internal data class TagMetadataPayload(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val artworkBytes: ByteArray? = null,
    val artworkMimeType: String = "image/jpeg"
)

object AudioTagWriter {

    private val invalidFileNameChars = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val knownAudioExtensions = setOf(
        ".mp3", ".flac", ".wav", ".m4a", ".aac", ".alac", ".ogg", ".opus"
    )

    fun defaultExtensionFor(format: AudioFormat): String = when (format) {
        AudioFormat.FLAC -> ".flac"
        AudioFormat.MP3 -> ".mp3"
        AudioFormat.WAV -> ".wav"
        AudioFormat.AAC -> ".m4a"
        AudioFormat.OGG -> ".ogg"
    }

    fun splitFileName(track: AudioTrack, context: Context? = null): TrackFileNameParts {
        val rawFromPath = track.filePath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        val rawName = if (rawFromPath.isNotEmpty() && rawFromPath.contains('.')) {
            rawFromPath
        } else {
            queryDisplayName(context, track.contentUri)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "${sanitizeFileBaseName(track.title)}${defaultExtensionFor(track.format)}"
        }

        val dotIdx = rawName.lastIndexOf('.')
        val extCandidate = if (dotIdx > 0 && dotIdx < rawName.lastIndex) {
            rawName.substring(dotIdx)
        } else {
            ""
        }
        val extensionWithDot = if (extCandidate.lowercase() in knownAudioExtensions) {
            extCandidate
        } else {
            defaultExtensionFor(track.format)
        }
        val rawBase = if (extCandidate.lowercase() in knownAudioExtensions && dotIdx > 0) {
            rawName.substring(0, dotIdx)
        } else {
            rawName
        }
        val cleanBase = rawBase.ifBlank { sanitizeFileBaseName(track.title) }
        return TrackFileNameParts(
            baseName = cleanBase,
            extensionWithDot = extensionWithDot,
            fullFileName = "$cleanBase$extensionWithDot"
        )
    }

    fun sanitizeFileBaseName(rawInput: String, extensionWithDot: String = ""): String {
        var stripped = rawInput
            .replace(invalidFileNameChars, "_")
            .trim()
            .trim('.')
            .trim()
        if (extensionWithDot.isNotEmpty() && stripped.endsWith(extensionWithDot, ignoreCase = true)) {
            stripped = stripped.substring(0, stripped.length - extensionWithDot.length).trim().trim('.')
        }
        return stripped.ifBlank { "Audio" }
    }

    fun buildTargetDisplayName(baseName: String, extensionWithDot: String): String {
        val safeExt = if (extensionWithDot.startsWith(".")) extensionWithDot else ".$extensionWithDot"
        val cleanBase = sanitizeFileBaseName(baseName, safeExt)
        return "$cleanBase$safeExt"
    }

    private fun queryDisplayName(context: Context?, uriString: String): String? {
        if (context == null || uriString.isBlank()) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    suspend fun writeTagsAndRename(
        context: Context,
        request: TrackTagEditRequest
    ): PhysicalTagEditOutcome = withContext(Dispatchers.IO) {
        val track = request.track
        var currentUri = runCatching { Uri.parse(track.contentUri) }.getOrNull()
            ?: return@withContext PhysicalTagEditOutcome.Failure("URI de audio inválida")

        if (track.contentUri.startsWith("http://", ignoreCase = true) ||
            track.contentUri.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext PhysicalTagEditOutcome.Failure("Las pistas en streaming no se pueden editar")
        }

        val currentParts = splitFileName(track, context)
        val cleanNewBase = sanitizeFileBaseName(request.newFileNameBase, currentParts.extensionWithDot)
        val targetDisplayName = buildTargetDisplayName(cleanNewBase, currentParts.extensionWithDot)
        val shouldRename = !targetDisplayName.equals(currentParts.fullFileName, ignoreCase = false)

        val artworkPayload = request.customArtworkUri
            ?.takeIf { it.isNotBlank() && it != track.albumArtUri }
            ?.let { loadArtworkBytes(context, Uri.parse(it)) }

        val tagPayload = TagMetadataPayload(
            title = request.title.trim().ifBlank { track.title },
            artist = request.artist.trim().ifBlank { track.artist },
            album = request.album.trim().ifBlank { track.album },
            albumArtist = request.albumArtist.trim(),
            genre = request.genre.trim(),
            year = request.year.coerceAtLeast(0),
            trackNumber = request.trackNumber.coerceAtLeast(0),
            artworkBytes = artworkPayload?.first,
            artworkMimeType = artworkPayload?.second ?: "image/jpeg"
        )

        var currentFilePath = track.filePath
        var renamedFile = false
        var physicalTagsWritten = false

        val isSafOrTree = track.isSafImported || track.isDocumentTreeTrack

        // 1. Write embedded audio tags (MP3 ID3v2 / FLAC Vorbis Comment) when supported
        if (track.format == AudioFormat.MP3 || track.format == AudioFormat.FLAC) {
            val writeRes = writeEmbeddedTagsToUri(context, currentUri, track.format, tagPayload)
            when (writeRes) {
                is EmbedWriteResult.Written -> physicalTagsWritten = true
                is EmbedWriteResult.PermissionRequired -> {
                    return@withContext PhysicalTagEditOutcome.NeedsWritePermission(writeRes.sender)
                }
                is EmbedWriteResult.UnsupportedOrFailed -> {
                    // Proceed cleanly to rename / MediaStore / Room overrides if stream wasn't writable
                }
            }
        }

        // 2. Rename physical file when requested
        if (shouldRename) {
            if (isSafOrTree) {
                val newDocUri = runCatching {
                    DocumentsContract.renameDocument(context.contentResolver, currentUri, targetDisplayName)
                }.getOrElse { err ->
                    if (err is SecurityException) {
                        return@withContext PhysicalTagEditOutcome.Failure(
                            "El proveedor del archivo no concedió permiso de escritura para renombrarlo"
                        )
                    }
                    null
                }
                if (newDocUri != null) {
                    currentUri = newDocUri
                    currentFilePath = if (track.isDocumentTreeTrack) targetDisplayName else currentFilePath
                    renamedFile = true
                }
            } else {
                val renameOutcome = renameMediaStoreTrack(
                    context = context,
                    uri = currentUri,
                    oldFilePath = currentFilePath,
                    targetDisplayName = targetDisplayName
                )
                when (renameOutcome) {
                    is RenameResult.Renamed -> {
                        renamedFile = true
                        currentFilePath = renameOutcome.newFilePath ?: currentFilePath
                    }
                    is RenameResult.PermissionRequired -> {
                        return@withContext PhysicalTagEditOutcome.NeedsWritePermission(renameOutcome.sender)
                    }
                    RenameResult.NotRenamed -> Unit
                }
            }
        }

        // 3. Update MediaStore catalog columns on MediaStore tracks (skipping read-only columns)
        if (!isSafOrTree) {
            val catalogRes = updateMediaStoreCatalogMetadata(context, currentUri, tagPayload)
            if (catalogRes is CatalogUpdateResult.PermissionRequired) {
                return@withContext PhysicalTagEditOutcome.NeedsWritePermission(catalogRes.sender)
            } else if (catalogRes == CatalogUpdateResult.Updated) {
                physicalTagsWritten = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                currentFilePath = queryMediaStorePath(context, currentUri) ?: currentFilePath
            }
        }

        // 4. Notify Android MediaScanner so system & external apps see changes immediately
        val scanPaths = buildList {
            if (track.filePath.startsWith("/")) add(track.filePath)
            if (currentFilePath.startsWith("/") && currentFilePath != track.filePath) add(currentFilePath)
        }.distinct()
        if (scanPaths.isNotEmpty()) {
            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    scanPaths.toTypedArray(),
                    Array(scanPaths.size) { track.mimeType },
                    null
                )
            }
        }

        val updatedTrack = track.copy(
            contentUri = currentUri.toString(),
            title = tagPayload.title,
            artist = tagPayload.artist,
            album = tagPayload.album,
            albumArtist = tagPayload.albumArtist,
            genre = tagPayload.genre,
            year = tagPayload.year,
            trackNumber = tagPayload.trackNumber,
            albumArtUri = request.customArtworkUri ?: track.albumArtUri,
            filePath = currentFilePath
        )

        PhysicalTagEditOutcome.Success(
            updatedTrack = updatedTrack,
            physicalTagsWritten = physicalTagsWritten,
            renamedFile = renamedFile
        )
    }

    private sealed interface EmbedWriteResult {
        data object Written : EmbedWriteResult
        data class PermissionRequired(val sender: IntentSender) : EmbedWriteResult
        data object UnsupportedOrFailed : EmbedWriteResult
    }

    private sealed interface RenameResult {
        data class Renamed(val newFilePath: String?) : RenameResult
        data class PermissionRequired(val sender: IntentSender) : RenameResult
        data object NotRenamed : RenameResult
    }

    private sealed interface CatalogUpdateResult {
        data object Updated : CatalogUpdateResult
        data class PermissionRequired(val sender: IntentSender) : CatalogUpdateResult
        data object Skipped : CatalogUpdateResult
    }

    private fun writeEmbeddedTagsToUri(
        context: Context,
        uri: Uri,
        format: AudioFormat,
        payload: TagMetadataPayload
    ): EmbedWriteResult {
        val tempSource = File.createTempFile("sonora_tag_src_", ".tmp", context.cacheDir)
        val tempDest = File.createTempFile("sonora_tag_dst_", ".tmp", context.cacheDir)
        return try {
            val copiedSource = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempSource).use { out -> input.copyTo(out, 64 * 1024) }
                    true
                } ?: false
            }.getOrDefault(false)
            if (!copiedSource || tempSource.length() <= 0L) return EmbedWriteResult.UnsupportedOrFailed

            val written = when (format) {
                AudioFormat.MP3 -> writeId3v2TagsToFile(tempSource, tempDest, payload)
                AudioFormat.FLAC -> writeFlacTagsToFile(tempSource, tempDest, payload)
                else -> false
            }
            if (!written || tempDest.length() <= 0L) return EmbedWriteResult.UnsupportedOrFailed

            try {
                context.contentResolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                    FileInputStream(tempDest).use { input ->
                        FileOutputStream(pfd.fileDescriptor).use { out ->
                            input.copyTo(out, 64 * 1024)
                            out.flush()
                        }
                    }
                    EmbedWriteResult.Written
                } ?: run {
                    // Fallback to "rw" with explicit truncation
                    context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { out ->
                            out.channel.truncate(0L)
                            FileInputStream(tempDest).use { input -> input.copyTo(out, 64 * 1024) }
                            out.flush()
                        }
                        EmbedWriteResult.Written
                    } ?: EmbedWriteResult.UnsupportedOrFailed
                }
            } catch (sec: SecurityException) {
                extractWritePermissionSender(context, uri, sec)?.let { sender ->
                    EmbedWriteResult.PermissionRequired(sender)
                } ?: EmbedWriteResult.UnsupportedOrFailed
            } catch (_: Throwable) {
                EmbedWriteResult.UnsupportedOrFailed
            }
        } finally {
            runCatching { tempSource.delete() }
            runCatching { tempDest.delete() }
        }
    }

    private fun renameMediaStoreTrack(
        context: Context,
        uri: Uri,
        oldFilePath: String,
        targetDisplayName: String
    ): RenameResult {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, targetDisplayName)
            }
            val updatedRows = context.contentResolver.update(uri, values, null, null)
            if (updatedRows > 0) {
                val resolvedPath = queryMediaStorePath(context, uri)
                    ?: oldFilePath.takeIf { it.startsWith("/") }?.let { old ->
                        File(old).parentFile?.let { File(it, targetDisplayName).absolutePath }
                    }
                return RenameResult.Renamed(resolvedPath)
            }
        } catch (sec: SecurityException) {
            extractWritePermissionSender(context, uri, sec)?.let { sender ->
                return RenameResult.PermissionRequired(sender)
            }
        } catch (_: Throwable) {
            // Try direct file rename pre-Q below if MediaStore column update is unsupported
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && oldFilePath.startsWith("/")) {
            val oldFile = File(oldFilePath)
            val parent = oldFile.parentFile
            if (oldFile.exists() && parent != null) {
                val targetFile = File(parent, targetDisplayName)
                if (targetFile.exists() && targetFile.absolutePath != oldFile.absolutePath) {
                    return RenameResult.NotRenamed
                }
                if (runCatching { oldFile.renameTo(targetFile) }.getOrDefault(false)) {
                    runCatching {
                        val cv = ContentValues().apply {
                            @Suppress("DEPRECATION")
                            put(MediaStore.Audio.Media.DATA, targetFile.absolutePath)
                            put(MediaStore.Audio.Media.DISPLAY_NAME, targetDisplayName)
                        }
                        context.contentResolver.update(uri, cv, null, null)
                    }
                    return RenameResult.Renamed(targetFile.absolutePath)
                }
            }
        }
        return RenameResult.NotRenamed
    }

    private fun updateMediaStoreCatalogMetadata(
        context: Context,
        uri: Uri,
        payload: TagMetadataPayload
    ): CatalogUpdateResult {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, payload.title)
            put(MediaStore.Audio.Media.ARTIST, payload.artist)
            put(MediaStore.Audio.Media.ALBUM, payload.album)
            if (payload.year > 0) put(MediaStore.Audio.Media.YEAR, payload.year)
            if (payload.trackNumber > 0) put(MediaStore.Audio.Media.TRACK, payload.trackNumber)
        }
        return try {
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) CatalogUpdateResult.Updated else CatalogUpdateResult.Skipped
        } catch (sec: SecurityException) {
            extractWritePermissionSender(context, uri, sec)?.let { sender ->
                CatalogUpdateResult.PermissionRequired(sender)
            } ?: CatalogUpdateResult.Skipped
        } catch (_: IllegalArgumentException) {
            // Fallback with minimal guaranteed columns
            runCatching {
                val minimal = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, payload.title)
                }
                if (context.contentResolver.update(uri, minimal, null, null) > 0) {
                    CatalogUpdateResult.Updated
                } else CatalogUpdateResult.Skipped
            }.getOrDefault(CatalogUpdateResult.Skipped)
        } catch (_: Throwable) {
            CatalogUpdateResult.Skipped
        }
    }

    private fun extractWritePermissionSender(
        context: Context,
        uri: Uri,
        sec: SecurityException
    ): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching {
                MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
            }.getOrNull()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
            return sec.userAction.actionIntent.intentSender
        }
        return null
    }

    private fun queryMediaStorePath(context: Context, uri: Uri): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    private fun loadArtworkBytes(context: Context, uri: Uri): Pair<ByteArray, String>? {
        return runCatching {
            val mime = context.contentResolver.getType(uri)?.lowercase()
                ?.takeIf { it == "image/png" || it == "image/jpeg" || it == "image/jpg" }
                ?.let { if (it == "image/jpg") "image/jpeg" else it }
                ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val maxBytes = 4 * 1024 * 1024
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return@use null
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return@runCatching null
            if (bytes.isEmpty()) null else bytes to mime
        }.getOrNull()
    }

    // =========================================================================
    // Pure-Kotlin MP3 ID3v2.3 Writer (preserves unedited frames like USLT/TXXX)
    // =========================================================================

    private val editedId3FrameIds = setOf(
        "TIT2", "TPE1", "TALB", "TPE2", "TCON", "TYER", "TDRC", "TRCK"
    )

    internal fun writeId3v2TagsToFile(
        sourceFile: File,
        destFile: File,
        payload: TagMetadataPayload
    ): Boolean {
        return runCatching {
            var audioOffset = 0L
            val preservedFrames = mutableListOf<Pair<String, ByteArray>>()

            FileInputStream(sourceFile).use { input ->
                val header = ByteArray(10)
                if (readFully(input, header) == 10 &&
                    header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte()
                ) {
                    val majorVersion = header[3].toInt() and 0xFF
                    val flags = header[5].toInt() and 0xFF
                    val tagBodySize = decodeSynchsafeInt(header, 6)
                    val hasFooter = (flags and 0x10) != 0
                    val hasUnsync = (flags and 0x80) != 0
                    if (majorVersion in 3..4 && tagBodySize in 0..(16 * 1024 * 1024)) {
                        audioOffset = 10L + tagBodySize.toLong() + (if (hasFooter) 10L else 0L)
                        if (!hasUnsync && tagBodySize > 0) {
                            val tagBytes = ByteArray(tagBodySize)
                            if (readFully(input, tagBytes) == tagBodySize) {
                                parsePreservedId3Frames(
                                    tagBytes = tagBytes,
                                    majorVersion = majorVersion,
                                    flags = flags,
                                    skipApic = payload.artworkBytes != null,
                                    outFrames = preservedFrames
                                )
                            }
                        }
                    }
                }
            }

            val framesStream = ByteArrayOutputStream()
            writeId3TextFrame(framesStream, "TIT2", payload.title)
            writeId3TextFrame(framesStream, "TPE1", payload.artist)
            writeId3TextFrame(framesStream, "TALB", payload.album)
            if (payload.albumArtist.isNotBlank()) {
                writeId3TextFrame(framesStream, "TPE2", payload.albumArtist)
            }
            if (payload.genre.isNotBlank()) {
                writeId3TextFrame(framesStream, "TCON", payload.genre)
            }
            if (payload.year > 0) {
                writeId3TextFrame(framesStream, "TYER", payload.year.toString())
            }
            if (payload.trackNumber > 0) {
                writeId3TextFrame(framesStream, "TRCK", payload.trackNumber.toString())
            }
            if (payload.artworkBytes != null) {
                writeId3ApicFrame(framesStream, payload.artworkMimeType, payload.artworkBytes)
            }
            for ((frameId, framePayload) in preservedFrames) {
                writeRawId3Frame(framesStream, frameId, framePayload)
            }

            val padding = 256
            val tagBody = framesStream.toByteArray()
            val totalTagBodySize = tagBody.size + padding

            FileOutputStream(destFile).use { out ->
                // ID3v2.3 header
                out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
                out.write(encodeSynchsafeInt(totalTagBodySize))
                out.write(tagBody)
                out.write(ByteArray(padding))

                FileInputStream(sourceFile).use { input ->
                    skipFully(input, audioOffset)
                    input.copyTo(out, 64 * 1024)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun parsePreservedId3Frames(
        tagBytes: ByteArray,
        majorVersion: Int,
        flags: Int,
        skipApic: Boolean,
        outFrames: MutableList<Pair<String, ByteArray>>
    ) {
        var cursor = 0
        // Skip ID3v2.3 / v2.4 extended header if present
        if ((flags and 0x40) != 0 && tagBytes.size >= 4) {
            val extSize = if (majorVersion == 4) decodeSynchsafeInt(tagBytes, 0) else decodeBigEndianInt(tagBytes, 0)
            if (extSize in 4 until tagBytes.size) {
                cursor += extSize
            }
        }
        while (cursor + 10 <= tagBytes.size) {
            if (tagBytes[cursor].toInt() == 0) break // padding reached
            val id = String(tagBytes, cursor, 4, StandardCharsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break
            val frameSize = if (majorVersion == 4) {
                decodeSynchsafeInt(tagBytes, cursor + 4)
            } else {
                decodeBigEndianInt(tagBytes, cursor + 4)
            }
            if (frameSize <= 0 || cursor + 10 + frameSize > tagBytes.size) break
            val shouldSkip = id in editedId3FrameIds || (skipApic && id == "APIC")
            if (!shouldSkip && frameSize <= 4 * 1024 * 1024) {
                val framePayload = tagBytes.copyOfRange(cursor + 10, cursor + 10 + frameSize)
                outFrames.add(id to framePayload)
            }
            cursor += 10 + frameSize
        }
    }

    private fun writeId3TextFrame(out: ByteArrayOutputStream, frameId: String, text: String) {
        if (text.isEmpty()) return
        val encoded = text.toByteArray(Charsets.UTF_16LE)
        val payload = ByteArray(1 + 2 + encoded.size)
        payload[0] = 0x01 // UTF-16 with BOM
        payload[1] = 0xFF.toByte()
        payload[2] = 0xFE.toByte()
        System.arraycopy(encoded, 0, payload, 3, encoded.size)
        writeRawId3Frame(out, frameId, payload)
    }

    private fun writeId3ApicFrame(out: ByteArrayOutputStream, mimeType: String, imageBytes: ByteArray) {
        val mimeBytes = mimeType.toByteArray(StandardCharsets.ISO_8859_1)
        val payload = ByteArrayOutputStream(1 + mimeBytes.size + 3 + imageBytes.size)
        payload.write(0x00) // ISO-8859-1 mime/desc
        payload.write(mimeBytes)
        payload.write(0x00) // mime terminator
        payload.write(0x03) // Front Cover
        payload.write(0x00) // empty description terminator
        payload.write(imageBytes)
        writeRawId3Frame(out, "APIC", payload.toByteArray())
    }

    private fun writeRawId3Frame(out: ByteArrayOutputStream, frameId: String, payload: ByteArray) {
        out.write(frameId.toByteArray(StandardCharsets.ISO_8859_1), 0, 4)
        out.write(encodeBigEndianInt(payload.size))
        out.write(0)
        out.write(0)
        out.write(payload)
    }

    // =========================================================================
    // Pure-Kotlin FLAC Vorbis Comment & Picture Writer
    // =========================================================================

    private val editedVorbisKeys = setOf(
        "TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "GENRE", "DATE", "YEAR", "TRACKNUMBER"
    )

    internal fun writeFlacTagsToFile(
        sourceFile: File,
        destFile: File,
        payload: TagMetadataPayload
    ): Boolean {
        return runCatching {
            val preservedBlocks = mutableListOf<Pair<Int, ByteArray>>()
            val preservedComments = mutableListOf<String>()
            var audioOffset = 0L

            FileInputStream(sourceFile).use { input ->
                val magic = ByteArray(4)
                if (readFully(input, magic) != 4 ||
                    magic[0] != 'f'.code.toByte() ||
                    magic[1] != 'L'.code.toByte() ||
                    magic[2] != 'a'.code.toByte() ||
                    magic[3] != 'C'.code.toByte()
                ) {
                    return false
                }
                audioOffset = 4L

                var isLast = false
                while (!isLast) {
                    val blockHeader = ByteArray(4)
                    if (readFully(input, blockHeader) != 4) return false
                    val h0 = blockHeader[0].toInt() and 0xFF
                    isLast = (h0 and 0x80) != 0
                    val blockType = h0 and 0x7F
                    val blockLen = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                        ((blockHeader[2].toInt() and 0xFF) shl 8) or
                        (blockHeader[3].toInt() and 0xFF)
                    audioOffset += 4L + blockLen.toLong()
                    if (blockLen > 16 * 1024 * 1024) return false
                    val data = ByteArray(blockLen)
                    if (readFully(input, data) != blockLen) return false

                    when (blockType) {
                        1 -> Unit // PADDING replaced cleanly at the end
                        4 -> parsePreservedVorbisComments(data, preservedComments)
                        6 -> if (payload.artworkBytes == null) preservedBlocks.add(blockType to data)
                        else -> preservedBlocks.add(blockType to data)
                    }
                }
            }

            if (preservedBlocks.none { it.first == 0 }) return false // Missing mandatory STREAMINFO

            val vorbisCommentBlock = buildVorbisCommentBlock(payload, preservedComments)
            val blocksToWrite = mutableListOf<Pair<Int, ByteArray>>()
            // STREAMINFO must stay block #0
            blocksToWrite.addAll(preservedBlocks.filter { it.first == 0 })
            blocksToWrite.addAll(preservedBlocks.filter { it.first != 0 })
            blocksToWrite.add(4 to vorbisCommentBlock)
            if (payload.artworkBytes != null) {
                blocksToWrite.add(6 to buildFlacPictureBlock(payload.artworkMimeType, payload.artworkBytes))
            }
            blocksToWrite.add(1 to ByteArray(256))

            FileOutputStream(destFile).use { out ->
                out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                blocksToWrite.forEachIndexed { index, (blockType, data) ->
                    val isLastBlock = index == blocksToWrite.lastIndex
                    val firstByte = (blockType and 0x7F) or (if (isLastBlock) 0x80 else 0x00)
                    out.write(firstByte)
                    out.write((data.size ushr 16) and 0xFF)
                    out.write((data.size ushr 8) and 0xFF)
                    out.write(data.size and 0xFF)
                    out.write(data)
                }
                FileInputStream(sourceFile).use { input ->
                    skipFully(input, audioOffset)
                    input.copyTo(out, 64 * 1024)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun parsePreservedVorbisComments(
        data: ByteArray,
        outComments: MutableList<String>
    ) {
        if (data.size < 8) return
        val vendorLen = decodeLittleEndianInt(data, 0)
        if (vendorLen < 0 || 4 + vendorLen + 4 > data.size) return
        var cursor = 4 + vendorLen
        val count = decodeLittleEndianInt(data, cursor)
        cursor += 4
        if (count < 0 || count > 4096) return
        for (i in 0 until count) {
            if (cursor + 4 > data.size) break
            val len = decodeLittleEndianInt(data, cursor)
            cursor += 4
            if (len < 0 || cursor + len > data.size) break
            val comment = String(data, cursor, len, StandardCharsets.UTF_8)
            val key = comment.substringBefore('=').trim().uppercase()
            if (key !in editedVorbisKeys) {
                outComments.add(comment)
            }
            cursor += len
        }
    }

    private fun buildVorbisCommentBlock(
        payload: TagMetadataPayload,
        preservedComments: List<String>
    ): ByteArray {
        val comments = buildList {
            add("TITLE=${payload.title}")
            add("ARTIST=${payload.artist}")
            add("ALBUM=${payload.album}")
            if (payload.albumArtist.isNotBlank()) add("ALBUMARTIST=${payload.albumArtist}")
            if (payload.genre.isNotBlank()) add("GENRE=${payload.genre}")
            if (payload.year > 0) add("DATE=${payload.year}")
            if (payload.trackNumber > 0) add("TRACKNUMBER=${payload.trackNumber}")
            addAll(preservedComments)
        }
        val out = ByteArrayOutputStream()
        val vendorBytes = "Davoo Player".toByteArray(StandardCharsets.UTF_8)
        out.write(encodeLittleEndianInt(vendorBytes.size))
        out.write(vendorBytes)
        out.write(encodeLittleEndianInt(comments.size))
        for (entry in comments) {
            val bytes = entry.toByteArray(StandardCharsets.UTF_8)
            out.write(encodeLittleEndianInt(bytes.size))
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun buildFlacPictureBlock(mimeType: String, imageBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val mimeBytes = mimeType.toByteArray(StandardCharsets.US_ASCII)
        out.write(encodeBigEndianInt(3)) // Front Cover
        out.write(encodeBigEndianInt(mimeBytes.size))
        out.write(mimeBytes)
        out.write(encodeBigEndianInt(0)) // description length
        out.write(encodeBigEndianInt(0)) // width
        out.write(encodeBigEndianInt(0)) // height
        out.write(encodeBigEndianInt(0)) // color depth
        out.write(encodeBigEndianInt(0)) // indexed colors
        out.write(encodeBigEndianInt(imageBytes.size))
        out.write(imageBytes)
        return out.toByteArray()
    }

    private fun decodeSynchsafeInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)

    private fun encodeSynchsafeInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte()
    )

    private fun decodeBigEndianInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun encodeBigEndianInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun decodeLittleEndianInt(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)

    private fun encodeLittleEndianInt(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                if (input.read() < 0) break
                remaining--
            }
        }
    }
}
