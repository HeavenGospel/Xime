package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.ui.keyboard.ToolbarButtonIcon
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ToolbarCustomizeView(
    toolbarButtons: List<String>,
    pluginButtons: List<ToolbarButtonItem.Plugin> = emptyList(),
    keyTextColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    onUpdateToolbarButtons: ((List<String>) -> Unit)?,
    onDismiss: () -> Unit,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier
) {
    val builtinButtons = ToolbarButton.entries.filter { button ->
        if (button == ToolbarButton.HANDWRITING_LOOKUP) {
            com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(LocalContext.current)
        } else true
    }.map { ToolbarButtonItem.Builtin(it) }
    val allButtons = builtinButtons + pluginButtons
    val itemById = remember(allButtons) { allButtons.associateBy { it.id } }

    // 本地有序列表：开关与拖动排序都改它，再回调宿主持久化
    var orderedIds by remember { mutableStateOf(toolbarButtons.toList()) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    // 拖动过程中不要被外部 state 回写打断手势
    LaunchedEffect(toolbarButtons) {
        if (draggingId != null) return@LaunchedEffect
        if (toolbarButtons != orderedIds) {
            orderedIds = toolbarButtons.toList()
        }
    }
    val enabledIds = orderedIds.toSet()

    fun persist(ids: List<String>) {
        orderedIds = ids
        onUpdateToolbarButtons?.invoke(ids)
    }

    fun toggleButton(item: ToolbarButtonItem) {
        if (draggingId != null) return
        val newList = orderedIds.toMutableList()
        if (item.id in enabledIds) {
            newList.remove(item.id)
        } else {
            newList.add(item.id)
        }
        persist(newList)
    }

    fun moveItem(from: Int, to: Int): List<String> {
        if (from == to) return orderedIds
        if (from !in orderedIds.indices || to !in orderedIds.indices) return orderedIds
        val list = orderedIds.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    // 预览项槽位宽度：size 28 + horizontal padding 3*2
    val previewSlotPx = with(density) { 34.dp.toPx() }

    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        keyBgColor,
        accentColor,
        0.25f
    )

    val itemsPerPage = 8
    val pages = allButtons.chunked(itemsPerPage).map { page ->
        page + List(itemsPerPage - page.size) { null }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragHoverIndex by remember { mutableIntStateOf(-1) }
    var dragTotalX by remember { mutableFloatStateOf(0f) }
    var previewViewportWidthPx by remember { mutableIntStateOf(0) }
    val previewScrollState = rememberScrollState()
    val view = LocalView.current
    val orderedIdsLatest by rememberUpdatedState(orderedIds)
    val dragScrollScope = rememberCoroutineScope()

    // 数量变化或可滚动范围更新时滚到末尾；拖动排序时不要抢滚动
    LaunchedEffect(orderedIds.size, previewScrollState.maxValue, draggingId) {
        if (draggingId != null) return@LaunchedEffect
        if (previewScrollState.maxValue > 0) {
            previewScrollState.animateScrollTo(previewScrollState.maxValue)
        }
    }

    fun resetDragState() {
        draggingId = null
        dragStartIndex = -1
        dragHoverIndex = -1
        dragTotalX = 0f
        dragOffsetX = 0f
    }

    fun commitDragReorder() {
        val from = dragStartIndex
        val to = dragHoverIndex
        resetDragState()
        if (from < 0 || to < 0 || from == to) return
        persist(moveItem(from, to))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isLandscape) 50.dp else 8.dp)
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconButtonContainer)
                    .clickable(enabled = draggingId == null) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "确定",
                    tint = keyTextColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                // 浮层跟手：horizontalScroll 会裁切子项 offset，拖动中的按钮画在滚动容器外
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .onSizeChanged { previewViewportWidthPx = it.width }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(state = previewScrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val previewButtons = orderedIds.mapNotNull { itemById[it] }
                        if (previewButtons.isEmpty()) {
                            Text(
                                text = "点击下方添加按钮",
                                fontSize = 11.sp,
                                color = keyTextColor.copy(alpha = 0.45f),
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            previewButtons.forEach { button ->
                                key(button.id) {
                                    val isDragging = draggingId == button.id
                                    val slotShiftX = if (
                                        draggingId != null &&
                                        !isDragging &&
                                        dragStartIndex >= 0 &&
                                        dragHoverIndex >= 0
                                    ) {
                                        val index = orderedIdsLatest.indexOf(button.id)
                                        when {
                                            dragStartIndex < dragHoverIndex &&
                                                index in (dragStartIndex + 1)..dragHoverIndex -> -previewSlotPx
                                            dragStartIndex > dragHoverIndex &&
                                                index in dragHoverIndex until dragStartIndex -> previewSlotPx
                                            else -> 0f
                                        }
                                    } else {
                                        0f
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .offset { IntOffset(slotShiftX.roundToInt(), 0) }
                                            .graphicsLayer {
                                                alpha = when {
                                                    isDragging -> 0.2f
                                                    draggingId != null -> 0.55f
                                                    else -> 1f
                                                }
                                            }
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(iconButtonContainer)
                                            .pointerInput(button.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        val start = orderedIdsLatest.indexOf(button.id)
                                                        if (start < 0) return@detectDragGesturesAfterLongPress
                                                        draggingId = button.id
                                                        dragStartIndex = start
                                                        dragHoverIndex = start
                                                        dragTotalX = 0f
                                                        // 视口内起始 X（内容坐标 - 滚动）
                                                        dragOffsetX =
                                                            start * previewSlotPx - previewScrollState.value
                                                        view.performHapticFeedback(
                                                            android.view.HapticFeedbackConstants.LONG_PRESS
                                                        )
                                                    },
                                                    onDragCancel = { resetDragState() },
                                                    onDragEnd = { commitDragReorder() },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragTotalX += dragAmount.x
                                                        dragOffsetX += dragAmount.x
                                                        val ids = orderedIdsLatest
                                                        if (dragStartIndex < 0 || ids.isEmpty()) {
                                                            return@detectDragGesturesAfterLongPress
                                                        }
                                                        val contentX =
                                                            previewScrollState.value + dragOffsetX + previewSlotPx / 2f
                                                        val target = (contentX / previewSlotPx)
                                                            .roundToInt()
                                                            .coerceIn(0, ids.lastIndex)
                                                        if (target != dragHoverIndex) {
                                                            dragHoverIndex = target
                                                            view.performHapticFeedback(
                                                                android.view.HapticFeedbackConstants.CLOCK_TICK
                                                            )
                                                            val viewport = previewViewportWidthPx
                                                            if (viewport > 0) {
                                                                val itemCenter =
                                                                    (target + 0.5f) * previewSlotPx
                                                                val visStart =
                                                                    previewScrollState.value.toFloat()
                                                                val visEnd = visStart + viewport
                                                                val margin = previewSlotPx
                                                                when {
                                                                    itemCenter < visStart + margin -> {
                                                                        val to = (itemCenter - margin)
                                                                            .roundToInt()
                                                                            .coerceAtLeast(0)
                                                                        dragScrollScope.launch {
                                                                            previewScrollState.scrollTo(to)
                                                                        }
                                                                    }
                                                                    itemCenter > visEnd - margin -> {
                                                                        val to = (itemCenter - viewport + margin)
                                                                            .roundToInt()
                                                                            .coerceAtMost(previewScrollState.maxValue)
                                                                        dragScrollScope.launch {
                                                                            previewScrollState.scrollTo(to)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ToolbarButtonIcon(
                                            item = button,
                                            tint = keyTextColor.copy(0.6f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val draggingButton = draggingId?.let { itemById[it] }
                    if (draggingButton != null) {
                        Box(
                            modifier = Modifier
                                .zIndex(2f)
                                .offset {
                                    IntOffset(
                                        dragOffsetX.roundToInt().coerceIn(
                                            0,
                                            (previewViewportWidthPx - previewSlotPx).roundToInt()
                                                .coerceAtLeast(0)
                                        ),
                                        3
                                    )
                                }
                                .shadow(8.dp, CircleShape)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(iconButtonContainer)
                                .graphicsLayer {
                                    scaleX = 1.15f
                                    scaleY = 1.15f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ToolbarButtonIcon(
                                item = draggingButton,
                                tint = keyTextColor.copy(0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "左右滑动查看 · 长按后可任意拖动排序",
                    fontSize = 10.sp,
                    color = keyTextColor.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(keyBgColor)
                .padding(16.dp, 10.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = draggingId == null
            ) { page ->
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 4
                ) {
                    pages[page].forEach { button ->
                        if (button != null) {
                            val isEnabled = button.id in enabledIds
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (isEnabled) accentColor.copy(0.2f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isEnabled) Color.Transparent
                                            else keyTextColor.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        )
                                        .clickable(enabled = draggingId == null) {
                                            toggleButton(button)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ToolbarButtonIcon(
                                        item = button,
                                        tint = if (isEnabled) accentColor else keyTextColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Text(
                                    text = button.label,
                                    fontSize = 10.sp,
                                    color = keyTextColor.copy(alpha = 0.8f),
                                    lineHeight = 1.sp,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) keyTextColor
                                else keyTextColor.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 15.dp else bottomPaddingDp.dp))
    }
}
