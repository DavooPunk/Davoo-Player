package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Immutable
import com.example.model.AlbumGroup
import com.example.model.ArtistGroup
import com.example.model.AudioFormat
import com.example.model.AudioTrack
import com.example.model.CollectionTab
import com.example.model.FolderGroup
import com.example.model.GenreGroup
import com.example.playback.PlaybackUiState
import com.example.ui.components.AudioAlbumArtThumbnail
import com.example.ui.components.FormatBadge
import com.example.ui.components.LazyColumnFastScroller
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDuration

private enum class DrilldownSource { ARTIST, GENRE, FOLDER }

private data class CollectionDrilldownKey(
    val source: DrilldownSource,
    val name: String
)

private data class CollectionDrilldown(
    val title: String,
    val subtitle: String,
    val tracks: List<AudioTrack>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    albums: List<AlbumGroup>,
    artists: List<ArtistGroup> = emptyList(),
    genres: List<GenreGroup> = emptyList(),
    folders: List<FolderGroup> = emptyList(),
    playbackState: PlaybackUiState = PlaybackUiState(),
    onAlbumClick: (AlbumGroup) -> Unit,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit = { _, _ -> },
    onShuffleTracks: (List<AudioTrack>) -> Unit = {},
    onPlayNext: (AudioTrack) -> Unit = {},
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    onToggleFavorite: (AudioTrack) -> Unit = {},
    onShowLyrics: (AudioTrack) -> Unit = {},
    onEditTags: (AudioTrack) -> Unit = {},
    onShowMetadata: (AudioTrack) -> Unit = {},
    onDeleteTrack: ((AudioTrack) -> Unit)? = null,
    onToggleFolderExclude: (folderName: String, currentlyExcluded: Boolean) -> Unit = { _, _ -> },
    onOpenGlobalSearch: () -> Unit = {},
    requestedArtistName: String? = null,
    onConsumeRequestedArtist: () -> Unit = {},
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(CollectionTab.ALBUMS) }
    var activeDrilldownKey by remember { mutableStateOf<CollectionDrilldownKey?>(null) }
    var currentFolderPath by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(requestedArtistName) {
        if (!requestedArtistName.isNullOrBlank()) {
            selectedTab = CollectionTab.ARTISTS
            activeDrilldownKey = CollectionDrilldownKey(
                source = DrilldownSource.ARTIST,
                name = requestedArtistName
            )
            onConsumeRequestedArtist()
        }
    }

    val activeDrilldown = activeDrilldownKey?.let { key ->
        when (key.source) {
            DrilldownSource.ARTIST -> artists.find { it.artistName == key.name }?.let { artist ->
                CollectionDrilldown(
                    title = artist.artistName,
                    subtitle = "${artist.albumCount} álbumes · ${artist.tracks.size} pistas · ${formatDuration(artist.totalDurationMs)}",
                    tracks = artist.tracks
                )
            }
            DrilldownSource.GENRE -> genres.find { it.genreName == key.name }?.let { genre ->
                CollectionDrilldown(
                    title = genre.genreName,
                    subtitle = "${genre.tracks.size} canciones · ${formatDuration(genre.totalDurationMs)}",
                    tracks = genre.tracks
                )
            }
            DrilldownSource.FOLDER -> folders.find { it.folderName == key.name && !it.isExcluded }?.let { folder ->
                CollectionDrilldown(
                    title = folder.folderName,
                    subtitle = "${folder.tracks.size} pistas · ${formatDuration(folder.totalDurationMs)}",
                    tracks = folder.tracks
                )
            }
        }
    }

    if (activeDrilldown != null) {
        BackHandler { activeDrilldownKey = null }
        val detail = activeDrilldown
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(detail.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = detail.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { activeDrilldownKey = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a colección")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
            val drilldownListState = rememberLazyListState()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = drilldownListState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "drilldown_actions") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onPlayTracks(detail.tracks, 0) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reproducir")
                            }
                            OutlinedButton(
                                onClick = { onShuffleTracks(detail.tracks) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aleatorio")
                            }
                        }
                    }
                    itemsIndexed(detail.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                        val isCurrent = playbackState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            isCurrentTrack = isCurrent,
                            isPlaying = isCurrent && playbackState.isPlaying,
                            onClick = { onPlayTracks(detail.tracks, index) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onShowMetadata = { onShowMetadata(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onShowLyrics = { onShowLyrics(track) },
                            onEditTags = { onEditTags(track) },
                            onDeleteFromDevice = onDeleteTrack?.let { cb -> { cb(track) } }
                        )
                    }
                }
                LazyColumnFastScroller(
                    listState = drilldownListState,
                    itemCount = detail.tracks.size,
                    headerItemCount = 1,
                    bottomPadding = bottomContentPadding.calculateBottomPadding() + 16.dp,
                    bubbleLabelForTrackIndex = { idx ->
                        detail.tracks.getOrNull(idx)?.title?.firstOrNull()?.uppercaseChar()?.toString()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("albums_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "COLECCIÓN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val heading = when (selectedTab) {
                    CollectionTab.ALBUMS -> "Álbumes (${albums.size})"
                    CollectionTab.ARTISTS -> "Artistas (${artists.size})"
                    CollectionTab.GENRES -> "Géneros (${genres.size})"
                    CollectionTab.FOLDERS -> "Carpetas (${folders.size})"
                }
                Text(
                    text = heading,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            Surface(
                onClick = onOpenGlobalSearch,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("header_search_button")
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Buscar en toda la biblioteca",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(CollectionTab.entries, key = { it.name }, contentType = { "collection_tab" }) { tab ->
                val isSelected = selectedTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    label = { Text(tab.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("collection_tab_${tab.name.lowercase()}")
                )
            }
        }

        when (selectedTab) {
            CollectionTab.ALBUMS -> {
                if (albums.isEmpty()) {
                    EmptyCollectionPlaceholder(
                        icon = Icons.Rounded.Album,
                        title = "Sin álbumes detectados todavía",
                        subtitle = "Escanea o importa pistas desde la pestaña Biblioteca."
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 156.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums, key = { it.albumName }, contentType = { "album_card" }) { album ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onAlbumClick(album) }
                                    .testTag("album_card_${album.albumName}"),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                    ) {
                                        val representativeTrack = album.tracks.firstOrNull()
                                        AudioAlbumArtThumbnail(
                                            audioUri = representativeTrack?.contentUri.orEmpty(),
                                            albumArtUri = album.albumArtUri,
                                            contentDescription = album.albumName,
                                            cornerRadius = 14.dp,
                                            thumbnailSizePx = 384,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (album.hasFlac) {
                                            FormatBadge(
                                                format = AudioFormat.FLAC,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = album.albumName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                repeatDelayMillis = 1500,
                                                velocity = 30.dp
                                            )
                                    )
                                    val albumMetaParts = buildList {
                                        if (album.artist.isNotBlank()) add(album.artist)
                                        album.year?.let { add(it.toString()) }
                                        add("${album.tracks.size} pistas")
                                    }
                                    Text(
                                        text = albumMetaParts.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CollectionTab.ARTISTS -> {
                if (artists.isEmpty()) {
                    EmptyCollectionPlaceholder(
                        icon = Icons.Rounded.Person,
                        title = "Sin artistas aún",
                        subtitle = "Tus artistas aparecerán aquí organizados automáticamente."
                    )
                } else {
                    val artistsListState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = artistsListState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(artists, key = { it.artistName }, contentType = { "artist_row" }) { artist ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            activeDrilldownKey = CollectionDrilldownKey(
                                                source = DrilldownSource.ARTIST,
                                                name = artist.artistName
                                            )
                                        }
                                        .testTag("artist_row_${artist.artistName}"),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val rep = artist.tracks.firstOrNull()
                                        AudioAlbumArtThumbnail(
                                            audioUri = rep?.contentUri.orEmpty(),
                                            albumArtUri = artist.albumArtUri,
                                            contentDescription = artist.artistName,
                                            cornerRadius = 26.dp,
                                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.artistName,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        repeatDelayMillis = 1500,
                                                        velocity = 30.dp
                                                    )
                                            )
                                            Text(
                                                text = "${artist.albumCount} álbumes · ${artist.tracks.size} canciones · ${formatDuration(artist.totalDurationMs)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        LazyColumnFastScroller(
                            listState = artistsListState,
                            itemCount = artists.size,
                            bottomPadding = bottomContentPadding.calculateBottomPadding() + 16.dp,
                            bubbleLabelForTrackIndex = { idx ->
                                artists.getOrNull(idx)?.artistName?.firstOrNull()?.uppercaseChar()?.toString()
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }

            CollectionTab.GENRES -> {
                if (genres.isEmpty()) {
                    EmptyCollectionPlaceholder(
                        icon = Icons.Rounded.Category,
                        title = "Sin géneros etiquetados",
                        subtitle = "Los géneros de tus etiquetas MP3 y FLAC aparecerán aquí."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(genres, key = { it.genreName }, contentType = { "genre_row" }) { genre ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        activeDrilldownKey = CollectionDrilldownKey(
                                            source = DrilldownSource.GENRE,
                                            name = genre.genreName
                                        )
                                    }
                                    .testTag("genre_row_${genre.genreName}"),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Category,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = genre.genreName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .basicMarquee(
                                                    iterations = Int.MAX_VALUE,
                                                    repeatDelayMillis = 1500,
                                                    velocity = 30.dp
                                                )
                                        )
                                        Text(
                                            text = "${genre.tracks.size} canciones · ${formatDuration(genre.totalDurationMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CollectionTab.FOLDERS -> {
                if (folders.isEmpty()) {
                    EmptyCollectionPlaceholder(
                        icon = Icons.Rounded.Folder,
                        title = "Sin carpetas detectadas",
                        subtitle = "Escanea el dispositivo para explorar por carpetas."
                    )
                } else {
                    BackHandler(enabled = currentFolderPath.isNotEmpty()) {
                        currentFolderPath = currentFolderPath.dropLast(1)
                    }
                    val folderSnapshot = remember(folders, currentFolderPath) {
                        FolderBrowserTree.buildView(folders, currentFolderPath)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (currentFolderPath.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .testTag("folder_breadcrumbs_row"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    onClick = { currentFolderPath = emptyList() },
                                    shape = RoundedCornerShape(100.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.testTag("folder_breadcrumb_root")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Home,
                                            contentDescription = "Ir a carpeta raíz",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Raíz",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                currentFolderPath.forEachIndexed { idx, segment ->
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val isLast = idx == currentFolderPath.lastIndex
                                    Surface(
                                        onClick = {
                                            if (!isLast) {
                                                currentFolderPath = currentFolderPath.take(idx + 1)
                                            }
                                        },
                                        shape = RoundedCornerShape(100.dp),
                                        color = if (isLast) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.testTag("folder_breadcrumb_$idx")
                                    ) {
                                        Text(
                                            text = segment,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isLast) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (folderSnapshot.subfolders.isEmpty() && folderSnapshot.directTracks.isEmpty()) {
                            EmptyCollectionPlaceholder(
                                icon = Icons.Rounded.FolderOff,
                                title = "Carpeta vacía o excluida",
                                subtitle = "Vuelve atrás para explorar otras carpetas."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("folders_browser_list")
                            ) {
                                if (currentFolderPath.isNotEmpty() && folderSnapshot.subtreeTracks.isNotEmpty()) {
                                    item(key = "folder_subtree_actions") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = { onPlayTracks(folderSnapshot.subtreeTracks, 0) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("folder_play_all_button")
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Reproducir (${folderSnapshot.subtreeTracks.size})")
                                            }
                                            OutlinedButton(
                                                onClick = { onShuffleTracks(folderSnapshot.subtreeTracks) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("folder_shuffle_all_button")
                                            ) {
                                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Aleatorio")
                                            }
                                        }
                                    }
                                }

                                items(
                                    items = folderSnapshot.subfolders,
                                    key = { "dir_${it.nextPathSegments.joinToString("/")}" },
                                    contentType = { "folder_row" }
                                ) { node ->
                                    val rowTag = node.directFolderGroupName ?: node.name
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(enabled = !node.isExcluded) {
                                                currentFolderPath = node.nextPathSegments
                                            }
                                            .testTag("folder_row_$rowTag"),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (node.isExcluded) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
                                                contentDescription = null,
                                                tint = if (node.isExcluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = node.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .basicMarquee(
                                                            iterations = Int.MAX_VALUE,
                                                            repeatDelayMillis = 1500,
                                                            velocity = 30.dp
                                                        )
                                                )
                                                val meta = buildList {
                                                    if (node.childFolderCount > 0) {
                                                        add("${node.childFolderCount} subcarpetas")
                                                    }
                                                    add("${node.totalTracks} canciones")
                                                    add(
                                                        if (node.isExcluded) "Oculta"
                                                        else formatDuration(node.totalDurationMs)
                                                    )
                                                }.joinToString(" · ")
                                                Text(
                                                    text = meta,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (node.directFolderGroupName != null) {
                                                Switch(
                                                    checked = !node.isExcluded,
                                                    onCheckedChange = {
                                                        onToggleFolderExclude(node.directFolderGroupName, node.isExcluded)
                                                    }
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                    contentDescription = "Abrir subcarpeta",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                itemsIndexed(
                                    items = folderSnapshot.directTracks,
                                    key = { _, track -> "folder_track_${track.id}" },
                                    contentType = { _, _ -> "track" }
                                ) { index, track ->
                                    val isCurrent = playbackState.currentTrack?.id == track.id
                                    TrackRow(
                                        track = track,
                                        isCurrentTrack = isCurrent,
                                        isPlaying = isCurrent && playbackState.isPlaying,
                                        onClick = { onPlayTracks(folderSnapshot.directTracks, index) },
                                        onPlayNext = { onPlayNext(track) },
                                        onAddToQueue = { onAddToQueue(track) },
                                        onAddToPlaylist = { onAddToPlaylist(track) },
                                        onShowMetadata = { onShowMetadata(track) },
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        onShowLyrics = { onShowLyrics(track) },
                                        onEditTags = { onEditTags(track) },
                                        onDeleteFromDevice = onDeleteTrack?.let { cb -> { cb(track) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCollectionPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: AlbumGroup?,
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onPlayTrack: (List<AudioTrack>, Int) -> Unit,
    onShuffleAlbum: (List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    bottomContentPadding: PaddingValues,
    onToggleFavorite: (AudioTrack) -> Unit = {},
    onShowLyrics: (AudioTrack) -> Unit = {},
    onEditTags: (AudioTrack) -> Unit = {},
    onShowMetadata: (AudioTrack) -> Unit = {},
    onDeleteTrack: ((AudioTrack) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(album?.albumName ?: "Álbum", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("album_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (album == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Álbum no encontrado", style = MaterialTheme.typography.bodyLarge)
            }
            return
        }

        val albumListState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = albumListState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "album_hero") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val representativeTrack = album.tracks.firstOrNull()
                        AudioAlbumArtThumbnail(
                            audioUri = representativeTrack?.contentUri.orEmpty(),
                            albumArtUri = album.albumArtUri,
                            contentDescription = album.albumName,
                            cornerRadius = 22.dp,
                            thumbnailSizePx = 512,
                            modifier = Modifier.size(190.dp)
                        )
                        Text(
                            text = album.albumName,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        val heroMeta = buildList {
                            if (album.artist.isNotBlank()) add(album.artist)
                            album.year?.let { add(it.toString()) }
                            album.genre?.let { add(it) }
                            add("${album.tracks.size} canciones")
                            add(formatDuration(album.totalDurationMs))
                        }
                        Text(
                            text = heroMeta.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onPlayTrack(album.tracks, 0) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reproducir")
                            }
                            OutlinedButton(
                                onClick = { onShuffleAlbum(album.tracks) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aleatorio")
                            }
                        }
                    }
                }

                itemsIndexed(album.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                    val isCurrent = playbackState.currentTrack?.id == track.id
                    TrackRow(
                        track = track,
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && playbackState.isPlaying,
                        onClick = { onPlayTrack(album.tracks, index) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onShowMetadata = { onShowMetadata(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onShowLyrics = { onShowLyrics(track) },
                        onEditTags = { onEditTags(track) },
                        onDeleteFromDevice = onDeleteTrack?.let { cb -> { cb(track) } }
                    )
                }
            }
            LazyColumnFastScroller(
                listState = albumListState,
                itemCount = album.tracks.size,
                headerItemCount = 1,
                bottomPadding = bottomContentPadding.calculateBottomPadding() + 16.dp,
                bubbleLabelForTrackIndex = { idx ->
                    album.tracks.getOrNull(idx)?.title?.firstOrNull()?.uppercaseChar()?.toString()
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Immutable
data class FolderSubdirectoryNode(
    val name: String,
    val nextPathSegments: List<String>,
    val totalTracks: Int,
    val totalDurationMs: Long,
    val childFolderCount: Int,
    val directFolderGroupName: String?,
    val isExcluded: Boolean,
    val subtreeTracks: List<AudioTrack>
)

@Immutable
data class FolderBrowserSnapshot(
    val currentSegments: List<String>,
    val subfolders: List<FolderSubdirectoryNode>,
    val directTracks: List<AudioTrack>,
    val subtreeTracks: List<AudioTrack>
)

object FolderBrowserTree {
    private val emulatedRootRegex = Regex("^/(?:storage/emulated/\\d+|storage/self/primary|mnt/sdcard|sdcard)/+", RegexOption.IGNORE_CASE)
    private val genericStoragePrefixRegex = Regex("^/storage/+", RegexOption.IGNORE_CASE)

    fun normalizeSegments(track: AudioTrack): List<String> {
        val rawPath = track.filePath.trim().replace('\\', '/')
        if (rawPath.isNotEmpty() && rawPath.contains('/')) {
            val dirPath = rawPath.substringBeforeLast('/', "")
            val stripped = dirPath
                .replace(emulatedRootRegex, "")
                .replace(genericStoragePrefixRegex, "")
                .trim('/')
            val segments = stripped.split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) return segments
        }
        return listOf(track.folderName.ifBlank { "Música" })
    }

    fun buildView(
        folders: List<FolderGroup>,
        currentSegments: List<String>
    ): FolderBrowserSnapshot {
        val directTracks = ArrayList<AudioTrack>()
        val subtreeTracks = ArrayList<AudioTrack>()

        class MutableBucket {
            var totalTracks = 0
            var totalDurationMs = 0L
            val childSubdirs = LinkedHashSet<String>()
            val folderGroupNames = LinkedHashSet<String>()
            var allExcluded = true
            val playableTracks = ArrayList<AudioTrack>()
        }

        val buckets = LinkedHashMap<String, MutableBucket>()

        for (group in folders) {
            for (track in group.tracks) {
                val segments = normalizeSegments(track)
                if (segments.size < currentSegments.size) continue
                if (currentSegments.isNotEmpty() && segments.subList(0, currentSegments.size) != currentSegments) {
                    continue
                }

                if (segments.size == currentSegments.size) {
                    if (!group.isExcluded) {
                        directTracks.add(track)
                        subtreeTracks.add(track)
                    }
                } else {
                    val childName = segments[currentSegments.size]
                    val bucket = buckets.getOrPut(childName) { MutableBucket() }
                    bucket.totalTracks++
                    bucket.totalDurationMs += track.durationMs.coerceAtLeast(0L)
                    if (!group.isExcluded) {
                        bucket.allExcluded = false
                        bucket.playableTracks.add(track)
                        subtreeTracks.add(track)
                    }
                    if (segments.size > currentSegments.size + 1) {
                        bucket.childSubdirs.add(segments[currentSegments.size + 1])
                    } else {
                        bucket.folderGroupNames.add(group.folderName)
                    }
                }
            }
        }

        val subfolderNodes = buckets.entries
            .sortedBy { it.key.lowercase() }
            .map { (name, bucket) ->
                val directGroup = if (bucket.childSubdirs.isEmpty() && bucket.folderGroupNames.size == 1) {
                    bucket.folderGroupNames.first()
                } else null
                FolderSubdirectoryNode(
                    name = name,
                    nextPathSegments = currentSegments + name,
                    totalTracks = bucket.totalTracks,
                    totalDurationMs = bucket.totalDurationMs,
                    childFolderCount = bucket.childSubdirs.size,
                    directFolderGroupName = directGroup,
                    isExcluded = directGroup != null && bucket.allExcluded,
                    subtreeTracks = bucket.playableTracks
                )
            }

        return FolderBrowserSnapshot(
            currentSegments = currentSegments,
            subfolders = subfolderNodes,
            directTracks = directTracks,
            subtreeTracks = subtreeTracks
        )
    }
}
