package com.kingzcheung.xime.ui.emoji

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class StickerCropRatio(val label: String, val aspect: Float?) {
    FREE("自由", null),
    R1_1("1:1", 1f),
    R4_3("4:3", 4f / 3f),
    R3_4("3:4", 3f / 4f),
    R16_9("16:9", 16f / 9f),
    R9_16("9:16", 9f / 16f),
}

private enum class CropHandle {
    Move, L, T, R, B, TL, TR, BR, BL
}

@Composable
fun FavoriteStickerCropScreen(
    uri: Uri,
    index: Int,
    total: Int,
    onCancelAll: () -> Unit,
    onSkipOriginal: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val handleSizeDp = 28.dp
    val handleSizePx = with(density) { handleSizeDp.toPx() }
    val minCropPx = with(density) { 72.dp.toPx() }

    var source by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(uri) { mutableStateOf(false) }
    var rotation by remember(uri) { mutableIntStateOf(0) }
    // 相对「刚好装进画幅」：1 = 不放大到超出原图画幅
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(uri) { mutableFloatStateOf(0f) }
    var ratio by remember { mutableStateOf(StickerCropRatio.FREE) }
    var exporting by remember { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(Size(1f, 1f)) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    val viewportLatest = rememberUpdatedState(viewportSize)
    val cropLatest = rememberUpdatedState(cropRect)
    val scaleLatest = rememberUpdatedState(scale)
    val offsetLatest = rememberUpdatedState(Offset(offsetX, offsetY))
    val ratioLatest = rememberUpdatedState(ratio)
    val rotationLatest = rememberUpdatedState(rotation)

    LaunchedEffect(uri) {
        loadError = false
        source = null
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotation = 0
        cropRect = Rect.Zero
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(req)
                (result as? SuccessResult)?.drawable?.toBitmap()
            }.getOrNull()
        }
        if (bmp == null) loadError = true else source = bmp
    }

    fun applyImageClamp(
        bmp: Bitmap,
        nextScale: Float = scaleLatest.value,
        nextOx: Float = offsetLatest.value.x,
        nextOy: Float = offsetLatest.value.y,
        crop: Rect = cropLatest.value,
        vp: Size = viewportLatest.value,
    ) {
        val clamped = clampImageTransform(
            bmp,
            rotationLatest.value,
            nextScale,
            nextOx,
            nextOy,
            crop,
            vp.width,
            vp.height
        )
        scale = clamped.scale
        offsetX = clamped.offsetX
        offsetY = clamped.offsetY
    }

    fun applyCrop(
        bmp: Bitmap,
        handle: CropHandle,
        drag: Offset,
        vw: Float,
        vh: Float,
    ) {
        val next = resizeCropRect(
            cropLatest.value,
            handle,
            drag,
            viewportBounds(vw, vh),
            minCropPx,
            ratioLatest.value.aspect
        )
        cropRect = next
        applyImageClamp(bmp, crop = next, vp = Size(vw, vh))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancelAll) {
                Text("取消", color = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (total > 1) "编辑 ${index + 1}/$total" else "编辑表情",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                enabled = source != null && !exporting,
                onClick = {
                    val bmp = source ?: return@TextButton
                    if (exporting) return@TextButton
                    exporting = true
                    val rot = rotation
                    val sc = scale
                    val ox = offsetX
                    val oy = offsetY
                    val crop = cropLatest.value
                    val vp = viewportLatest.value
                    scope.launch {
                        val out = withContext(Dispatchers.Default) {
                            cropExport(bmp, rot, sc, ox, oy, crop, vp)
                        }
                        exporting = false
                        if (out != null) onConfirm(out)
                    }
                }
            ) {
                Text(if (exporting) "处理中" else "完成", color = Color(0xFF4CAF50))
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val vw = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val vh = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            LaunchedEffect(vw, vh) { viewportSize = Size(vw, vh) }

            LaunchedEffect(ratio, vw, vh, source) {
                val bmp = source ?: return@LaunchedEffect
                val nextCrop = defaultCropRect(vw, vh, ratio.aspect)
                cropRect = nextCrop
                val clamped = clampImageTransform(
                    bmp, rotation, 1f, 0f, 0f, nextCrop, vw, vh
                )
                scale = clamped.scale
                offsetX = clamped.offsetX
                offsetY = clamped.offsetY
            }

            val bmp = source
            when {
                loadError -> Text(
                    "无法加载图片",
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center)
                )
                bmp == null -> Text(
                    "加载中…",
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    val rotatedSize = rotatedBitmapSize(bmp, rotation)
                    val fit = containScale(rotatedSize.width, rotatedSize.height, vw, vh)
                    val displayScale = fit * scale

                    Box(modifier = Modifier.fillMaxSize()) {
                        // 图片：双指缩放 / 单指拖移，且不得放大超过原画画幅
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = displayScale
                                    scaleY = displayScale
                                    translationX = offsetX
                                    translationY = offsetY
                                    rotationZ = rotation.toFloat()
                                }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(uri, vw, vh) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val crop = cropLatest.value
                                        if (crop.width <= 1f) return@detectTransformGestures
                                        applyImageClamp(
                                            bmp,
                                            nextScale = scaleLatest.value * zoom,
                                            nextOx = offsetLatest.value.x + pan.x,
                                            nextOy = offsetLatest.value.y + pan.y,
                                            crop = crop,
                                            vp = Size(vw, vh)
                                        )
                                    }
                                }
                        )

                        CropDimOverlay(cropRect)

                        if (cropRect.width > 1f && cropRect.height > 1f) {
                            // 只在边角把手上接管手势，框内留给图片拖移/缩放
                            val handles = listOf(
                                CropHandle.TL to Offset(cropRect.left, cropRect.top),
                                CropHandle.T to Offset(cropRect.center.x, cropRect.top),
                                CropHandle.TR to Offset(cropRect.right, cropRect.top),
                                CropHandle.R to Offset(cropRect.right, cropRect.center.y),
                                CropHandle.BR to Offset(cropRect.right, cropRect.bottom),
                                CropHandle.B to Offset(cropRect.center.x, cropRect.bottom),
                                CropHandle.BL to Offset(cropRect.left, cropRect.bottom),
                                CropHandle.L to Offset(cropRect.left, cropRect.center.y),
                            )
                            handles.forEach { (handle, center) ->
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (center.x - handleSizePx / 2f).roundToInt(),
                                                (center.y - handleSizePx / 2f).roundToInt()
                                            )
                                        }
                                        .size(handleSizeDp)
                                        .pointerInput(uri, handle, ratio) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                applyCrop(bmp, handle, drag, vw, vh)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StickerCropRatio.entries.forEach { item ->
                val selected = ratio == item
                Text(
                    text = item.label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
                        .clickable { ratio = item }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSkipOriginal) {
                Text("原图", color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        rotation = (rotation + 90) % 360
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "旋转",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(64.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun CropDimOverlay(crop: Rect) {
    if (crop.width <= 0f || crop.height <= 0f) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scrim = Color.Black.copy(alpha = 0.55f)
        drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, crop.top))
        drawRect(
            scrim,
            topLeft = Offset(0f, crop.bottom),
            size = Size(size.width, size.height - crop.bottom)
        )
        drawRect(
            scrim,
            topLeft = Offset(0f, crop.top),
            size = Size(crop.left, crop.height)
        )
        drawRect(
            scrim,
            topLeft = Offset(crop.right, crop.top),
            size = Size(size.width - crop.right, crop.height)
        )
        drawRect(
            Color.White,
            topLeft = Offset(crop.left, crop.top),
            size = Size(crop.width, crop.height),
            style = Stroke(width = 2f)
        )
        val hs = 14f
        val points = listOf(
            Offset(crop.left, crop.top),
            Offset(crop.right, crop.top),
            Offset(crop.right, crop.bottom),
            Offset(crop.left, crop.bottom),
            Offset(crop.center.x, crop.top),
            Offset(crop.center.x, crop.bottom),
            Offset(crop.left, crop.center.y),
            Offset(crop.right, crop.center.y),
        )
        points.forEach { c ->
            drawRect(
                Color.White,
                topLeft = Offset(c.x - hs / 2f, c.y - hs / 2f),
                size = Size(hs, hs)
            )
        }
    }
}

private data class ImageTransform(val scale: Float, val offsetX: Float, val offsetY: Float)

private data class SizeF(val width: Float, val height: Float)

private fun rotatedBitmapSize(bmp: Bitmap, rotation: Int): SizeF {
    return if (rotation % 180 == 0) {
        SizeF(bmp.width.toFloat(), bmp.height.toFloat())
    } else {
        SizeF(bmp.height.toFloat(), bmp.width.toFloat())
    }
}

private fun containScale(imgW: Float, imgH: Float, vw: Float, vh: Float): Float {
    return min(vw / imgW.coerceAtLeast(1f), vh / imgH.coerceAtLeast(1f))
}

private fun viewportBounds(vw: Float, vh: Float): Rect {
    val margin = min(vw, vh) * 0.04f
    return Rect(margin, margin, vw - margin, vh - margin)
}

private fun defaultCropRect(vw: Float, vh: Float, aspect: Float?): Rect {
    val bounds = viewportBounds(vw, vh)
    val maxW = bounds.width
    val maxH = bounds.height
    val (cw, ch) = if (aspect == null || aspect <= 0f) {
        maxW * 0.92f to maxH * 0.92f
    } else {
        var w = maxW * 0.92f
        var h = w / aspect
        if (h > maxH * 0.92f) {
            h = maxH * 0.92f
            w = h * aspect
        }
        w to h
    }
    val left = bounds.left + (maxW - cw) / 2f
    val top = bounds.top + (maxH - ch) / 2f
    return Rect(left, top, left + cw, top + ch)
}

private fun resizeCropRect(
    crop: Rect,
    handle: CropHandle,
    drag: Offset,
    bounds: Rect,
    minSize: Float,
    aspect: Float?,
): Rect {
    var l = crop.left
    var t = crop.top
    var r = crop.right
    var b = crop.bottom

    fun clampFree() {
        l = l.coerceIn(bounds.left, r - minSize)
        t = t.coerceIn(bounds.top, b - minSize)
        r = r.coerceIn(l + minSize, bounds.right)
        b = b.coerceIn(t + minSize, bounds.bottom)
    }

    when (handle) {
        CropHandle.Move -> {
            val w = r - l
            val h = b - t
            l = (l + drag.x).coerceIn(bounds.left, bounds.right - w)
            t = (t + drag.y).coerceIn(bounds.top, bounds.bottom - h)
            r = l + w
            b = t + h
        }
        CropHandle.L -> {
            l += drag.x
            if (aspect != null && aspect > 0f) {
                val w = (r - l).coerceAtLeast(minSize)
                val h = w / aspect
                val cy = (t + b) / 2f
                t = cy - h / 2f
                b = cy + h / 2f
                l = r - w
            }
            clampFree()
        }
        CropHandle.R -> {
            r += drag.x
            if (aspect != null && aspect > 0f) {
                val w = (r - l).coerceAtLeast(minSize)
                val h = w / aspect
                val cy = (t + b) / 2f
                t = cy - h / 2f
                b = cy + h / 2f
                r = l + w
            }
            clampFree()
        }
        CropHandle.T -> {
            t += drag.y
            if (aspect != null && aspect > 0f) {
                val h = (b - t).coerceAtLeast(minSize)
                val w = h * aspect
                val cx = (l + r) / 2f
                l = cx - w / 2f
                r = cx + w / 2f
                t = b - h
            }
            clampFree()
        }
        CropHandle.B -> {
            b += drag.y
            if (aspect != null && aspect > 0f) {
                val h = (b - t).coerceAtLeast(minSize)
                val w = h * aspect
                val cx = (l + r) / 2f
                l = cx - w / 2f
                r = cx + w / 2f
                b = t + h
            }
            clampFree()
        }
        CropHandle.TL, CropHandle.TR, CropHandle.BR, CropHandle.BL -> {
            if (aspect == null || aspect <= 0f) {
                when (handle) {
                    CropHandle.TL -> {
                        l += drag.x; t += drag.y
                    }
                    CropHandle.TR -> {
                        r += drag.x; t += drag.y
                    }
                    CropHandle.BR -> {
                        r += drag.x; b += drag.y
                    }
                    CropHandle.BL -> {
                        l += drag.x; b += drag.y
                    }
                    else -> Unit
                }
                clampFree()
            } else {
                val (anchorX, anchorY) = when (handle) {
                    CropHandle.TL -> r to b
                    CropHandle.TR -> l to b
                    CropHandle.BR -> l to t
                    else -> r to t
                }
                val dx = when (handle) {
                    CropHandle.TL, CropHandle.BL -> -drag.x
                    else -> drag.x
                }
                val dy = when (handle) {
                    CropHandle.TL, CropHandle.TR -> -drag.y
                    else -> drag.y
                }
                val dominant = if (abs(dx) * aspect >= abs(dy)) dx else dy * aspect
                var w = (r - l + dominant).coerceAtLeast(minSize)
                var h = w / aspect
                if (h < minSize) {
                    h = minSize
                    w = h * aspect
                }
                when (handle) {
                    CropHandle.TL -> {
                        r = anchorX; b = anchorY; l = r - w; t = b - h
                    }
                    CropHandle.TR -> {
                        l = anchorX; b = anchorY; r = l + w; t = b - h
                    }
                    CropHandle.BR -> {
                        l = anchorX; t = anchorY; r = l + w; b = t + h
                    }
                    else -> {
                        r = anchorX; t = anchorY; l = r - w; b = t + h
                    }
                }
                if (l < bounds.left) {
                    val d = bounds.left - l; l += d; r += d
                }
                if (r > bounds.right) {
                    val d = r - bounds.right; l -= d; r -= d
                }
                if (t < bounds.top) {
                    val d = bounds.top - t; t += d; b += d
                }
                if (b > bounds.bottom) {
                    val d = b - bounds.bottom; t -= d; b -= d
                }
                l = l.coerceAtLeast(bounds.left)
                t = t.coerceAtLeast(bounds.top)
                r = r.coerceAtMost(bounds.right)
                b = b.coerceAtMost(bounds.bottom)
                val maxW = (r - l).coerceAtLeast(1f)
                val maxH = (b - t).coerceAtLeast(1f)
                if (maxW / aspect <= maxH) {
                    h = maxW / aspect
                    val cy = (t + b) / 2f
                    t = cy - h / 2f
                    b = cy + h / 2f
                } else {
                    w = maxH * aspect
                    val cx = (l + r) / 2f
                    l = cx - w / 2f
                    r = cx + w / 2f
                }
            }
        }
    }
    return Rect(l, t, r, b)
}

/** 上限 scale=1（原图装进画幅）；下限铺满裁剪框；平移不露空。 */
private fun clampImageTransform(
    bmp: Bitmap,
    rotation: Int,
    userScale: Float,
    offsetX: Float,
    offsetY: Float,
    crop: Rect,
    vw: Float,
    vh: Float,
): ImageTransform {
    val size = rotatedBitmapSize(bmp, rotation)
    val fit = containScale(size.width, size.height, vw, vh)
    if (fit <= 0f || crop.width <= 1f || crop.height <= 1f) {
        return ImageTransform(userScale.coerceIn(0.15f, 1f), offsetX, offsetY)
    }

    val minScaleX = crop.width / (size.width * fit)
    val minScaleY = crop.height / (size.height * fit)
    val minScale = max(minScaleX, minScaleY).coerceAtMost(1f)
    val scale = userScale.coerceIn(minScale, 1f)
    val dispW = size.width * fit * scale
    val dispH = size.height * fit * scale
    val cropCx = crop.center.x - vw / 2f
    val cropCy = crop.center.y - vh / 2f
    val maxOx = ((dispW - crop.width) / 2f).coerceAtLeast(0f)
    val maxOy = ((dispH - crop.height) / 2f).coerceAtLeast(0f)
    val ox = offsetX.coerceIn(cropCx - maxOx, cropCx + maxOx)
    val oy = offsetY.coerceIn(cropCy - maxOy, cropCy + maxOy)
    return ImageTransform(scale, ox, oy)
}

private fun cropExport(
    source: Bitmap,
    rotation: Int,
    userScale: Float,
    offsetX: Float,
    offsetY: Float,
    crop: Rect,
    viewport: Size,
): Bitmap? {
    if (crop.width <= 1f || crop.height <= 1f) return null
    val vw = viewport.width.coerceAtLeast(1f)
    val vh = viewport.height.coerceAtLeast(1f)

    val rotated = if (rotation % 360 == 0) {
        source
    } else {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
    }

    val fit = containScale(rotated.width.toFloat(), rotated.height.toFloat(), vw, vh)
    val totalScale = fit * userScale

    val m2 = Matrix()
    m2.postTranslate(-rotated.width / 2f, -rotated.height / 2f)
    m2.postScale(totalScale, totalScale)
    m2.postTranslate(vw / 2f + offsetX, vh / 2f + offsetY)
    val inv2 = Matrix()
    if (!m2.invert(inv2)) {
        if (rotated !== source) rotated.recycle()
        return null
    }
    val pts2 = floatArrayOf(
        crop.left, crop.top,
        crop.right, crop.top,
        crop.right, crop.bottom,
        crop.left, crop.bottom
    )
    inv2.mapPoints(pts2)
    var minX = pts2[0]
    var maxX = pts2[0]
    var minY = pts2[1]
    var maxY = pts2[1]
    for (i in 0 until 4) {
        minX = min(minX, pts2[i * 2])
        maxX = max(maxX, pts2[i * 2])
        minY = min(minY, pts2[i * 2 + 1])
        maxY = max(maxY, pts2[i * 2 + 1])
    }

    val left = minX.toInt().coerceIn(0, rotated.width - 1)
    val top = minY.toInt().coerceIn(0, rotated.height - 1)
    val right = maxX.toInt().coerceIn(left + 1, rotated.width)
    val bottom = maxY.toInt().coerceIn(top + 1, rotated.height)
    val w = (right - left).coerceAtLeast(1)
    val h = (bottom - top).coerceAtLeast(1)

    return try {
        val cropped = Bitmap.createBitmap(rotated, left, top, w, h)
        if (rotated !== source) rotated.recycle()
        cropped
    } catch (_: Exception) {
        if (rotated !== source) rotated.recycle()
        null
    }
}
