package com.kingzcheung.xime.ui.settings

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kingzcheung.xime.data.FavoriteStickersStore
import com.kingzcheung.xime.ui.emoji.FavoriteStickerImportActivity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteStickersSettingsContent(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    FavoriteStickersStore.ensureLoaded(context)
    val stickers by FavoriteStickersStore.stickersFlow.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                FavoriteStickersStore.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val columns = 4
    var cellWidthPx by remember { mutableFloatStateOf(1f) }
    var cellHeightPx by remember { mutableFloatStateOf(1f) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragFromIndex by remember { mutableIntStateOf(-1) }
    var dragHoverIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    fun resetDrag() {
        draggingId = null
        dragFromIndex = -1
        dragHoverIndex = -1
        dragOffsetX = 0f
        dragOffsetY = 0f
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("收藏表情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "长按拖动调整顺序，点右上角删除",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "add") {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                FavoriteStickerImportActivity.startFromApp(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "添加",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "添加",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = stickers,
                    key = { _, sticker -> sticker.id }
                ) { index, sticker ->
                    val isDragging = draggingId == sticker.id
                    val path = FavoriteStickersStore.absolutePath(context, sticker)
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .onSizeChanged { size ->
                                cellWidthPx = size.width.toFloat()
                                cellHeightPx = size.height.toFloat()
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset {
                                if (isDragging) {
                                    IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt())
                                } else {
                                    IntOffset.Zero
                                }
                            }
                            .graphicsLayer {
                                alpha = when {
                                    isDragging -> 0.92f
                                    draggingId != null && dragHoverIndex == index -> 0.45f
                                    else -> 1f
                                }
                                scaleX = if (isDragging) 1.06f else 1f
                                scaleY = if (isDragging) 1.06f else 1f
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .pointerInput(sticker.id, stickers.size, columns) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragFromIndex = index
                                        dragHoverIndex = index
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        draggingId = sticker.id
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    },
                                    onDragCancel = { resetDrag() },
                                    onDragEnd = {
                                        val from = dragFromIndex
                                        val to = dragHoverIndex
                                        resetDrag()
                                        if (from >= 0 && to >= 0 && from != to) {
                                            FavoriteStickersStore.move(context, from, to)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX += dragAmount.x
                                        dragOffsetY += dragAmount.y
                                        val w = cellWidthPx.coerceAtLeast(1f)
                                        val h = cellHeightPx.coerceAtLeast(1f)
                                        // 格子间距约 10dp，按单元格尺寸估目标格
                                        val gapX = w * 0.12f
                                        val gapY = h * 0.12f
                                        val colDelta = ((dragOffsetX) / (w + gapX)).roundToInt()
                                        val rowDelta = ((dragOffsetY) / (h + gapY)).roundToInt()
                                        val fromCol = dragFromIndex % columns
                                        val fromRow = dragFromIndex / columns
                                        val targetCol = (fromCol + colDelta).coerceIn(0, columns - 1)
                                        val targetRow = (fromRow + rowDelta).coerceAtLeast(0)
                                        val target = (targetRow * columns + targetCol)
                                            .coerceIn(0, stickers.lastIndex)
                                        if (target != dragHoverIndex) {
                                            dragHoverIndex = target
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                    }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                                .clickable {
                                    FavoriteStickersStore.delete(context, sticker.id)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

        }
    }
}
