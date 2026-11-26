package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.LastFmPeriod
import com.example.model.LastFmProfileTab
import com.example.model.LastFmTrackItem
import com.example.ui.components.AudioAlbumArtThumbnail
import com.example.ui.components.RemoteArtImageLoaderProvider
import com.example.ui.viewmodel.LastFmUiState

@Composable
fun LastFmProfileScreen(
    uiState: LastFmUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    bottomContentPadding: PaddingValues,
    onConnectClick: () -> Unit,
    onOpenCredentialsDialog: () -> Unit,
    onLogoutClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTab: (LastFmProfileTab) -> Unit,
    onSelectPeriod: (LastFmPeriod) -> Unit,
    onEnsureDeezerEnrichment: (LastFmTrackItem) -> Unit,
    onPlayPreview: (LastFmTrackItem) -> Unit,
    onToggleLove: (LastFmTrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTracks = when (uiState.selectedTab) {
        LastFmProfileTab.TOP_TRACKS -> uiState.topTracks
        LastFmProfileTab.LOVED_TRACKS -> uiState.lovedTracks
        LastFmProfileTab.RECENT_TRACKS -> uiState.recentTracks
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("lastfm_profile_screen")
    ) {
        // Header fijo estilo Davoo Player
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LAST.FM & DEEZER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Perfil Last.fm",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenCredentialsDialog,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lastfm_credentials_button")
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = "Configurar claves API de Last.fm",
                            tint = if (uiState.hasCredentials) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (uiState.session != null) {
                    Surface(
                        onClick = onRefreshClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lastfm_refresh_button")
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Actualizar perfil Last.fm",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onOpenSettings,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lastfm_settings_button")
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("lastfm_tracks_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "account_card") {
                val session = uiState.session
                if (session == null) {
                    DisconnectedLastFmCard(
                        hasCredentials = uiState.hasCredentials,
                        isAuthenticating = uiState.isAuthenticating,
                        errorMessage = uiState.errorMessage,
                        onConnectClick = onConnectClick,
                        onOpenCredentialsDialog = onOpenCredentialsDialog
                    )
                } else {
                    ConnectedLastFmCard(
                        username = session.username,
                        realName = uiState.userInfo?.realName.orEmpty(),
                        playcount = uiState.userInfo?.playcount ?: 0L,
                        avatarUrl = uiState.userInfo?.avatarUrl ?: session.avatarUrl,
                        onLogoutClick = onLogoutClick
                    )
                }
            }

            if (uiState.session != null) {
                item(key = "tab_selector") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LastFmProfileTab.entries.forEach { tab ->
                            val selected = uiState.selectedTab == tab
                            val tabTag = when (tab) {
                                LastFmProfileTab.TOP_TRACKS -> "lastfm_tab_top"
                                LastFmProfileTab.LOVED_TRACKS -> "lastfm_tab_loved"
                                LastFmProfileTab.RECENT_TRACKS -> "lastfm_tab_recent"
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTab(tab) },
                                label = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(tabTag)
                            )
                        }
                    }
                }

                if (uiState.selectedTab == LastFmProfileTab.TOP_TRACKS) {
                    item(key = "period_selector") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(LastFmPeriod.entries, key = { it.apiValue }, contentType = { "lastfm_period_chip" }) { period ->
                                val selected = uiState.selectedPeriod == period
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectPeriod(period) },
                                    label = { Text(period.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.testTag("lastfm_period_${period.apiValue}")
                                )
                            }
                        }
                    }
                }

                if (uiState.errorMessage != null) {
                    item(key = "error_banner") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRefreshClick) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoadingTab && activeTracks.isEmpty()) {
                    item(key = "loading_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (activeTracks.isEmpty()) {
                    item(key = "empty_tab_state") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lastfm_empty_state")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Sin canciones en esta sección",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Escucha música en Davoo Player con scrobbling activo o actualiza tu perfil.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = activeTracks,
                        key = { "${uiState.selectedTab.name}_${it.id}" },
                        contentType = { "lastfm_track_row" }
                    ) { item ->
                        LaunchedEffect(item.lookupKey, item.deezerLookupDone) {
                            if (!item.deezerLookupDone) {
                                onEnsureDeezerEnrichment(item)
                            }
                        }

                        val previewTrackId = item.deezerEnrichment?.let {
                            "${LastFmTrackItem.PREVIEW_TRACK_ID_PREFIX}${it.deezerTrackId}"
                        }
                        val isPlayingThisPreview = previewTrackId != null &&
                            currentTrackId == previewTrackId &&
                            isPlaying

                        LastFmTrackRow(
                            item = item,
                            showRank = uiState.selectedTab == LastFmProfileTab.TOP_TRACKS,
                            isPlayingPreview = isPlayingThisPreview,
                            onPlayPreview = { onPlayPreview(item) },
                            onToggleLove = { onToggleLove(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectedLastFmCard(
    hasCredentials: Boolean,
    isAuthenticating: Boolean,
    errorMessage: String?,
    onConnectClick: () -> Unit,
    onOpenCredentialsDialog: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lastfm_disconnected_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Conecta tu cuenta Last.fm",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scrobbling en vivo, Favoritas, Top Canciones y vistas previas HD con Deezer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAuthenticating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Verificando sesión con Last.fm…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onConnectClick,
                    enabled = !isAuthenticating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lastfm_connect_button")
                ) {
                    Text(
                        text = "Conectar con Last.fm",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onOpenCredentialsDialog,
                    modifier = Modifier.testTag("lastfm_setup_credentials_button")
                ) {
                    Text(
                        text = if (hasCredentials) "Editar API" else "API Key",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedLastFmCard(
    username: String,
    realName: String,
    playcount: Long,
    avatarUrl: String?,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val remoteLoader = remember(context) { RemoteArtImageLoaderProvider.get(context) }
    val normalizedAvatarUrl = remember(avatarUrl) {
        avatarUrl?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            if (raw.startsWith("http://", ignoreCase = true)) {
                "https://" + raw.substring(7)
            } else raw
        }
    }
    val avatarRequest = remember(context, normalizedAvatarUrl) {
        ImageRequest.Builder(context)
            .data(normalizedAvatarUrl)
            .size(160)
            .crossfade(true)
            .build()
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lastfm_connected_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarRequest,
                imageLoader = remoteLoader,
                contentDescription = "Avatar de $username",
                placeholder = painterResource(id = R.drawable.img_default_album_art),
                error = painterResource(id = R.drawable.img_default_album_art),
                fallback = painterResource(id = R.drawable.img_default_album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .testTag("lastfm_profile_avatar")
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Scrobbling activo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val metaParts = buildList {
                    if (realName.isNotBlank() && !realName.equals(username, ignoreCase = true)) {
                        add(realName)
                    }
                    if (playcount > 0L) {
                        add("$playcount scrobbles")
                    } else {
                        add("Cuenta sincronizada con Last.fm")
                    }
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onLogoutClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lastfm_logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Cerrar sesión de Last.fm",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LastFmTrackRow(
    item: LastFmTrackItem,
    showRank: Boolean,
    isPlayingPreview: Boolean,
    onPlayPreview: () -> Unit,
    onToggleLove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (item.isNowPlaying || isPlayingPreview) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = when {
            isPlayingPreview -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            item.isNowPlaying -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlayPreview)
            .testTag("lastfm_track_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showRank && item.rank > 0) {
                Text(
                    text = "${item.rank}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(26.dp)
                )
            }

            Box(contentAlignment = Alignment.Center) {
                AudioAlbumArtThumbnail(
                    audioUri = "",
                    albumArtUri = item.effectiveArtworkUrl,
                    contentDescription = item.title,
                    cornerRadius = 10.dp,
                    thumbnailSizePx = 144,
                    crossfade = true,
                    modifier = Modifier.size(54.dp)
                )
                if (item.previewUrl != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlayingPreview) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                contentDescription = "Reproducir preview de Deezer",
                                tint = if (isPlayingPreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isPlayingPreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                repeatDelayMillis = 1500,
                                velocity = 30.dp
                            )
                    )
                    if (item.isNowPlaying) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.testTag("lastfm_now_playing_badge")
                        ) {
                            Text(
                                text = "En vivo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val subtitleParts = buildList {
                    add(item.artist)
                    val resolvedAlbum = item.deezerEnrichment?.albumTitle?.takeIf { it.isNotBlank() }
                        ?: item.album.takeIf { it.isNotBlank() }
                    if (resolvedAlbum != null) add(resolvedAlbum)
                }
                Text(
                    text = subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val metaLine = buildList {
                    if (item.playcount > 0L) add("${item.playcount} reprod.")
                    if (item.dateLabel.isNotBlank()) add(item.dateLabel)
                    if (item.previewUrl != null) {
                        add("Preview 30s")
                    } else if (!item.deezerLookupDone) {
                        add("Buscando en Deezer…")
                    }
                }.joinToString(" · ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.previewUrl != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onToggleLove,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lastfm_love_button_${item.id}")
            ) {
                Icon(
                    imageVector = if (item.isLoved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (item.isLoved) "Quitar Me gusta en Last.fm" else "Marcar Me gusta en Last.fm",
                    tint = if (item.isLoved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
