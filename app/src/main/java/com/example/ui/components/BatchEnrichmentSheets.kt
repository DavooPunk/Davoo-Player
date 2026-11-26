package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BatchAutoTagUiState
import com.example.model.BatchItemOutcome
import com.example.model.BatchLyricsUiState
import com.example.model.ReplayGainScanUiState

@Composable
fun BatchAutoTagSheet(
    uiState: BatchAutoTagUiState,
    onToggleOnlyMissing: (Boolean) -> Unit,
    onToggleOverwriteArtwork: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler {
        if (!uiState.isRunning) onDismiss()
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag("batch_autotag_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text("Auto-etiquetado Deezer (Lotes)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${uiState.targetTracks.size} pistas locales seleccionadas · API pública (sin ARL)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!uiState.isRunning) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar auto-etiquetado")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (!uiState.isRunning && !uiState.completed) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Solo pistas con datos incompletos", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Omite canciones que ya tengan carátula, artista, álbum, año y número de pista",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.onlyMissingMetadata,
                                onCheckedChange = onToggleOnlyMissing,
                                modifier = Modifier.testTag("batch_autotag_only_missing_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Incrustar carátulas HD (1000×1000)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Guarda la carátula dentro del archivo MP3/FLAC y en la biblioteca",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.overwriteArtwork,
                                onCheckedChange = onToggleOverwriteArtwork,
                                modifier = Modifier.testTag("batch_autotag_artwork_switch")
                            )
                        }
                    }
                }

                Button(
                    onClick = onStart,
                    enabled = uiState.targetTracks.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("batch_autotag_start_button")
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Autocompletar metadatos ahora", fontWeight = FontWeight.SemiBold)
                }
            } else {
                val total = uiState.totalCount.coerceAtLeast(1)
                val progress = (uiState.processedCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    uiState.waitingForWritePermission -> "Esperando permiso de escritura del sistema…"
                                    uiState.isRunning -> "Procesando ${uiState.processedCount} / ${uiState.totalCount}"
                                    else -> "Auto-etiquetado finalizado"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_autotag_progress")
                        )
                        if (uiState.currentTrackLabel.isNotBlank() && uiState.isRunning) {
                            Text(
                                text = uiState.currentTrackLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BatchStatPill("Actualizadas", uiState.updatedCount, MaterialTheme.colorScheme.primary)
                            BatchStatPill("Omitidas", uiState.skippedCount, MaterialTheme.colorScheme.secondary)
                            BatchStatPill("Sin coincidencia", uiState.failedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (uiState.recentResults.isNotEmpty()) {
                    BatchOutcomeList(items = uiState.recentResults)
                }

                if (uiState.isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("batch_autotag_cancel_button")
                    ) {
                        Text("Detener proceso")
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("batch_autotag_done_button")
                    ) {
                        Text("Listo")
                    }
                }
            }
        }
    }
}

@Composable
fun BatchLyricsSheet(
    uiState: BatchLyricsUiState,
    hasDownloadedExportableLyrics: Boolean,
    onToggleSyncedOnly: (Boolean) -> Unit,
    onToggleOverwrite: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onExportToFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler {
        if (!uiState.isRunning && !uiState.isExportingFolder) onDismiss()
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag("batch_lyrics_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text("Descargar letras (.lrc) por lotes", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${uiState.targetTracks.size} pistas · Fuente abierta LRCLIB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!uiState.isRunning && !uiState.isExportingFolder) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar descarga de letras")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (!uiState.isRunning && !uiState.completed) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Solo letras sincronizadas (.lrc)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Prioriza letras con marcas de tiempo [mm:ss] para karaoke y scroll automático",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.syncedOnly,
                                onCheckedChange = onToggleSyncedOnly,
                                modifier = Modifier.testTag("batch_lyrics_synced_only_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sobrescribir letras existentes", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Vuelve a buscar incluso si la canción ya tiene letra guardada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.overwriteExisting,
                                onCheckedChange = onToggleOverwrite,
                                modifier = Modifier.testTag("batch_lyrics_overwrite_switch")
                            )
                        }
                    }
                }

                Button(
                    onClick = onStart,
                    enabled = uiState.targetTracks.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("batch_lyrics_start_button")
                ) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buscar y guardar letras (.lrc)", fontWeight = FontWeight.SemiBold)
                }
            } else {
                val total = uiState.totalCount.coerceAtLeast(1)
                val progress = (uiState.processedCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isRunning) {
                                    "Consultando LRCLIB (${uiState.processedCount} / ${uiState.totalCount})"
                                } else {
                                    "Descarga de letras finalizada"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_lyrics_progress")
                        )
                        if (uiState.currentTrackLabel.isNotBlank() && uiState.isRunning) {
                            Text(
                                text = uiState.currentTrackLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BatchStatPill(".lrc sinc.", uiState.syncedDownloadedCount, MaterialTheme.colorScheme.primary)
                            if (!uiState.syncedOnly || uiState.plainDownloadedCount > 0) {
                                BatchStatPill("Planas", uiState.plainDownloadedCount, MaterialTheme.colorScheme.secondary)
                            }
                            BatchStatPill("Omitidas", uiState.skippedAlreadySyncedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                            BatchStatPill("Sin letra", uiState.notFoundCount, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (uiState.recentResults.isNotEmpty()) {
                    BatchOutcomeList(items = uiState.recentResults)
                }

                if (uiState.completed && hasDownloadedExportableLyrics) {
                    OutlinedButton(
                        onClick = onExportToFolder,
                        enabled = !uiState.isExportingFolder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("batch_lyrics_export_folder_button")
                    ) {
                        if (uiState.isExportingFolder) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exportando archivos .lrc…")
                        } else {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exportar archivos .lrc a carpeta")
                        }
                    }
                }

                if (uiState.isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("batch_lyrics_cancel_button")
                    ) {
                        Text("Detener descarga")
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("batch_lyrics_done_button")
                    ) {
                        Text("Listo")
                    }
                }
            }
        }
    }
}

@Composable
fun ReplayGainScanSheet(
    uiState: ReplayGainScanUiState,
    onToggleOnlyMissing: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler {
        if (!uiState.isRunning) onDismiss()
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag("replaygain_scan_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text("Escáner ReplayGain", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${uiState.cachedTracksCount} canciones calibradas · Objetivo -18 dBFS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!uiState.isRunning) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar escáner ReplayGain")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (!uiState.isRunning && !uiState.completed) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Calcula la ganancia de sonoridad real de tus archivos MP3/FLAC locales por muestreo rápido sin modificar los archivos en disco.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Solo canciones sin ReplayGain", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Omite pistas con etiqueta embebida o medición guardada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.onlyMissing,
                                onCheckedChange = onToggleOnlyMissing,
                                modifier = Modifier.testTag("replaygain_only_missing_switch")
                            )
                        }
                    }
                }

                Button(
                    onClick = onStart,
                    enabled = uiState.totalCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("replaygain_scan_start_button")
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analizar sonoridad (${uiState.totalCount})", fontWeight = FontWeight.SemiBold)
                }
            } else {
                val total = uiState.totalCount.coerceAtLeast(1)
                val progress = (uiState.processedCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.isRunning) {
                                    "Analizando ${uiState.processedCount} / ${uiState.totalCount}"
                                } else {
                                    "Análisis de sonoridad completado"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("replaygain_scan_progress")
                        )
                        if (uiState.currentTrackLabel.isNotBlank() && uiState.isRunning) {
                            Text(
                                text = uiState.currentTrackLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BatchStatPill("Analizadas", uiState.analyzedCount, MaterialTheme.colorScheme.primary)
                            BatchStatPill("Omitidas", uiState.skippedCount, MaterialTheme.colorScheme.secondary)
                            BatchStatPill("Errores", uiState.failedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (uiState.isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("replaygain_scan_cancel_button")
                    ) {
                        Text("Detener análisis")
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("replaygain_scan_done_button")
                    ) {
                        Text("Listo")
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchStatPill(
    label: String,
    count: Int,
    accent: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BatchOutcomeList(items: List<BatchItemOutcome>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { idx, item -> "${idx}_${item.trackTitle}_${item.detail}" },
            contentType = { _, _ -> "batch_outcome" }
        ) { _, outcome ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (outcome.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (outcome.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = outcome.trackTitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = outcome.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
