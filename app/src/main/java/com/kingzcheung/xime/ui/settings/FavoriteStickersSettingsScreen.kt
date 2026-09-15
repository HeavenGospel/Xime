package com.kingzcheung.xime.ui.settings

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kingzcheung.xime.data.FavoriteStickersStore
import com.kingzcheung.xime.ui.emoji.FavoriteStickerImportActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteStickersSettingsContent(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    FavoriteStickersStore.ensureLoaded(context)
    val stickers by FavoriteStickersStore.stickersFlow.collectAsStateWithLifecycle()

    var showImportExportDialog by remember { mutableStateOf(false) }
    var showImportModeDialog by remember { mutableStateOf(false) }
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportInProgress by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportInProgress = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    FavoriteStickersStore.exportBundleZip(context)
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: error("无法写入文件")
                Toast.makeText(context, "已导出 ${stickers.size} 个收藏表情", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                exportInProgress = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: error("无法读取文件")
                pendingImportBytes = bytes
                showImportModeDialog = true
            } catch (e: Exception) {
                Toast.makeText(context, "读取失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchExportFile() {
        if (exportInProgress) return
        if (stickers.isEmpty()) {
            Toast.makeText(context, "暂无收藏表情可导出", Toast.LENGTH_SHORT).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        exportLauncher.launch("xime-stickers-$stamp.zip")
    }

    fun doImport(replace: Boolean) {
        val bytes = pendingImportBytes ?: return
        pendingImportBytes = null
        showImportModeDialog = false
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    FavoriteStickersStore.importBundleZip(context, bytes, replace)
                }
                val skipHint = if (result.skipped > 0) "，跳过 ${result.skipped} 项" else ""
                Toast.makeText(
                    context,
                    "已导入 ${result.count} 个表情$skipHint",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareExport() {
        if (stickers.isEmpty()) {
            Toast.makeText(context, "暂无收藏表情可导出", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            exportInProgress = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    FavoriteStickersStore.exportBundleZip(context)
                }
                val dir = File(context.filesDir, "share").also { it.mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "xime-stickers-$stamp.zip")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "曦码收藏表情")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享收藏表情"))
            } catch (e: Exception) {
                Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                exportInProgress = false
            }
        }
    }

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
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showImportExportDialog = true }) {
                        Text("导入/导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "长按拖动调整顺序；右上角可导入/导出",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "add") {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable {
                                FavoriteStickerImportActivity.startFromApp(context)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "添加",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = "添加",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = stickers,
                    key = { _, sticker -> sticker.id },
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
                                    },
                                )
                            },
                    ) {
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
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
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportExportDialog) {
        AlertDialog(
            onDismissRequest = { showImportExportDialog = false },
            title = { Text("导入 / 导出") },
            text = {
                Column {
                    ImportExportDialogRow(
                        icon = Icons.TwoTone.FileDownload,
                        title = "导出为文件",
                        subtitle = if (exportInProgress) "正在打包…" else "zip 包，含全部收藏表情",
                        onClick = {
                            showImportExportDialog = false
                            launchExportFile()
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    ImportExportDialogRow(
                        icon = Icons.TwoTone.Share,
                        title = "分享",
                        subtitle = "通过微信 / 文件管理等发送 zip",
                        onClick = {
                            showImportExportDialog = false
                            shareExport()
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    ImportExportDialogRow(
                        icon = Icons.TwoTone.FileUpload,
                        title = "从文件导入",
                        subtitle = "导入他人分享的收藏表情 zip",
                        onClick = {
                            showImportExportDialog = false
                            importLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed", "*/*"),
                            )
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportExportDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    if (showImportModeDialog && pendingImportBytes != null) {
        AlertDialog(
            onDismissRequest = {
                showImportModeDialog = false
                pendingImportBytes = null
            },
            title = { Text("导入方式") },
            text = {
                Text("覆盖：清空现有收藏后导入。\n合并：保留本地表情，导入项置顶（同 id 以文件为准）。")
            },
            confirmButton = {
                TextButton(onClick = { doImport(replace = true) }) {
                    Text("覆盖")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { doImport(replace = false) }) {
                        Text("合并")
                    }
                    TextButton(
                        onClick = {
                            showImportModeDialog = false
                            pendingImportBytes = null
                        },
                    ) {
                        Text("取消")
                    }
                }
            },
        )
    }
}

@Composable
private fun ImportExportDialogRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
