package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ultra-lightweight, zero-lag vertical fast-scroll bar optimized for low-end Android devices:
 * - Positions thumb and section bubble via `graphicsLayer { translationY }` (zero recomposition per scroll frame).
 * - Conflates rapid drag events through a single `MutableStateFlow` so `scrollToItem` never spawns overlapping coroutines.
 * - Uses flat GPU-friendly shapes with no per-frame elevation/shadow re-rasterization.
 * - Single unified pointerInput handler for tap and drag without restarting on list updates.
 */
@Composable
fun LazyColumnFastScroller(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    headerItemCount: Int = 0,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 12.dp,
    bubbleLabelForTrackIndex: ((Int) -> String?)? = null
) {
    val isScrollable by remember(listState, itemCount) {
        derivedStateOf {
            itemCount > 0 && (listState.canScrollForward || listState.canScrollBackward)
        }
    }
    if (!isScrollable) return

    val density = LocalDensity.current
    val thumbHeightDp = 48.dp
    val thumbHeightPx = remember(density) { with(density) { thumbHeightDp.toPx() } }

    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var activeTrackIndex by remember { mutableIntStateOf(0) }

    // Single conflated scroll target channel so dragging across hundreds of items never queues laggy coroutines
    val scrollTargetFlow = remember { MutableStateFlow(-1) }
    LaunchedEffect(listState) {
        scrollTargetFlow.collectLatest { targetLazyIdx ->
            if (targetLazyIdx >= 0) {
                listState.scrollToItem(targetLazyIdx)
            }
        }
    }

    val currentItemCount by rememberUpdatedState(itemCount)
    val currentHeaderCount by rememberUpdatedState(headerItemCount)
    val currentBubbleProvider by rememberUpdatedState(bubbleLabelForTrackIndex)

    // Evaluated ONLY inside graphicsLayer (draw phase) -> zero recomposition during normal scroll!
    val scrollFractionState = remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val visibleCount = layoutInfo.visibleItemsInfo.size
            val hdr = currentHeaderCount
            val maxFirstIndex = (total - visibleCount).coerceAtLeast(1)
            val currentFirst = (listState.firstVisibleItemIndex - hdr).coerceAtLeast(0)
            val adjustedMax = (maxFirstIndex - hdr).coerceAtLeast(1)
            (currentFirst.toFloat() / adjustedMax.toFloat()).coerceIn(0f, 1f)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val thumbIdleBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val railColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val thumbShape = remember { RoundedCornerShape(100.dp) }
    val barShape = remember { RoundedCornerShape(50) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = topPadding, bottom = bottomPadding)
            .onSizeChanged { size ->
                val h = size.height.toFloat()
                if (h > 0f && abs(h - trackHeightPx) > 1f) {
                    trackHeightPx = h
                }
            }
            .testTag("fast_scroll_bar")
    ) {
        // Floating section / position bubble (only mounted while actively dragging)
        if (isDragging) {
            val customLabel = currentBubbleProvider?.invoke(activeTrackIndex)
            val displayText = if (!customLabel.isNullOrBlank()) {
                "$customLabel · ${activeTrackIndex + 1}/$itemCount"
            } else {
                "${activeTrackIndex + 1} / $itemCount"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 34.dp)
                    .graphicsLayer {
                        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        translationY = (dragFraction * travel).coerceIn(0f, travel)
                    }
                    .clip(thumbShape)
                    .background(primaryColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("fast_scroll_bubble")
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelLarge,
                    color = onPrimaryColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Single unified touch target for both tap-to-jump and smooth vertical drag
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(34.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    fun updateFromY(yPx: Float) {
                        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val frac = ((yPx - thumbHeightPx * 0.5f) / travel).coerceIn(0f, 1f)
                        dragFraction = frac
                        val maxTrackIdx = (currentItemCount - 1).coerceAtLeast(0)
                        val targetTrackIdx = (frac * maxTrackIdx).roundToInt().coerceIn(0, maxTrackIdx)
                        if (targetTrackIdx != activeTrackIndex) {
                            activeTrackIndex = targetTrackIdx
                        }
                        val targetLazyIdx = (currentHeaderCount + targetTrackIdx).coerceAtLeast(0)
                        if (scrollTargetFlow.value != targetLazyIdx) {
                            scrollTargetFlow.value = targetLazyIdx
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        updateFromY(down.position.y)

                        var pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                break
                            }
                            if (change.positionChange().y != 0f) {
                                change.consume()
                                updateFromY(change.position.y)
                            }
                            pointerId = change.id
                        }
                        isDragging = false
                        scrollTargetFlow.value = -1
                    }
                },
            contentAlignment = Alignment.TopEnd
        ) {
            // Lightweight vertical guide rail
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(barShape)
                    .background(railColor)
            )

            // Lightweight GPU-translated draggable pill thumb (zero recomposition per scroll frame)
            Box(
                modifier = Modifier
                    .padding(end = 1.dp)
                    .graphicsLayer {
                        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val frac = if (isDragging) dragFraction else scrollFractionState.value
                        translationY = (frac * travel).coerceIn(0f, travel)
                        alpha = if (isDragging || listState.isScrollInProgress) 1f else 0.78f
                    }
                    .width(if (isDragging) 14.dp else 11.dp)
                    .height(thumbHeightDp)
                    .clip(thumbShape)
                    .background(if (isDragging) primaryColor else thumbIdleBg)
                    .testTag("fast_scroll_thumb"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val dotColor = if (isDragging) onPrimaryColor else primaryColor
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(2.dp)
                                .clip(barShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        }
    }
}
