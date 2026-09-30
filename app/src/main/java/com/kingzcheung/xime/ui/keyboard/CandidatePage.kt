package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CandidatePageState(
    val candidates: List<String>,
    val candidateComments: List<String> = emptyList(),
    val associationCandidates: List<String> = emptyList(),
    /** 候选栏按宽度实际展示的条数；更多列表从此下标之后开始。 */
    val barVisibleCount: Int = -1,
    val backgroundColor: Color,
    val textColor: Color,
    /** 右侧功能键底色（与拼音页回车/删除同一套 specialKey）。 */
    val sideKeyBgColor: Color = textColor.copy(alpha = 0.12f),
    val sideKeyFgColor: Color = textColor,
    val bottomPaddingDp: Int = 0,
)

data class CandidatePageCallbacks(
    val onCandidateSelect: (index: Int, text: String, comment: String) -> Unit,
    val onAssociationSelect: ((Int) -> Unit)? = null,
    val onLoadAllCandidates: (suspend () -> List<Pair<String, String>>)? = null,
    val onDelete: (() -> Unit)? = null,
    val onEnter: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
)

/**
 * 更多候选页：栏上未展示的词按可用区域铺满分页；
 * 候选区用 VerticalPager 跟手上下滑翻页，右侧上/下键带动画翻页。
 */
@Composable
fun CandidatePage(
    state: CandidatePageState,
    callbacks: CandidatePageCallbacks,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val candidateFontFamily = AppFonts.candidateFontFamily
    val commentFontFamily = AppFonts.commentFontFamily
    val sideWidth = if (isLandscape) 64.dp else 56.dp

    var allItems by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var indexBase by remember { mutableIntStateOf(0) }
    var assocMode by remember { mutableStateOf(false) }
    // 有栏上种子候选时不转圈；仅在完全无数据、只能等全量时才 loading
    var loading by remember { mutableStateOf(false) }
    var dataEpoch by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(
        state.candidates,
        state.candidateComments,
        state.associationCandidates,
        state.barVisibleCount,
        callbacks.onLoadAllCandidates,
    ) {
        dataEpoch++
        val barSkip = if (state.barVisibleCount >= 0) state.barVisibleCount else state.candidates.size
        indexBase = barSkip
        assocMode = state.candidates.isEmpty() && state.associationCandidates.isNotEmpty()

        // 立刻用当前页已有候选（去掉栏上已显示）铺第一屏，避免转圈
        val seed: List<Pair<String, String>> = if (assocMode) {
            state.associationCandidates
                .drop(barSkip.coerceAtLeast(0))
                .map { it to "" }
        } else {
            state.candidates
                .drop(barSkip.coerceAtLeast(0))
                .mapIndexed { i, text ->
                    text to state.candidateComments.getOrElse(barSkip + i) { "" }
                }
        }
        allItems = seed

        if (assocMode) {
            loading = false
            return@LaunchedEffect
        }
        val loader = callbacks.onLoadAllCandidates
        if (loader == null) {
            loading = false
            return@LaunchedEffect
        }
        // 无种子才转圈；有种子则后台静默补全
        loading = seed.isEmpty()
        val loaded = withContext(Dispatchers.Default) { loader() }
        val full = if (loaded.size > barSkip) loaded.drop(barSkip) else emptyList()
        if (full.isNotEmpty()) {
            allItems = full
        } else if (seed.isEmpty()) {
            allItems = emptyList()
        }
        loading = false
    }

    val hSpacingPx = with(density) { 4.dp.toPx() }
    val vSpacingPx = with(density) { 4.dp.toPx() }
    val itemHPadPx = with(density) { 20.dp.toPx() }
    val itemVPadPx = with(density) { 16.dp.toPx() }
    val commentGapPx = with(density) { 4.dp.toPx() }
    val indicatorReservePx = with(density) { 28.dp.toPx() }
    val itemHeightPx = with(density) { 18.sp.toPx() + itemVPadPx }

    fun measureItemWidth(text: String, comment: String): Float {
        val textW = textMeasurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = candidateFontFamily,
            ),
        ).size.width.toFloat()
        val commentW = if (comment.isEmpty()) 0f else {
            commentGapPx + textMeasurer.measure(
                text = AnnotatedString(comment),
                style = TextStyle(fontSize = 11.sp, fontFamily = commentFontFamily),
            ).size.width.toFloat()
        }
        return textW + commentW + itemHPadPx
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(state.backgroundColor)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = if (isLandscape) 50.dp else 4.dp)
                .padding(top = 4.dp),
        ) {
            val sidePx = with(density) { sideWidth.toPx() + 4.dp.toPx() }
            val contentW = (constraints.maxWidth.toFloat() - sidePx).coerceAtLeast(0f)
            // 左右各 4.dp padding
            val padH = with(density) { 8.dp.toPx() }
            val padV = with(density) { 8.dp.toPx() }
            val availW = (contentW - padH).coerceAtLeast(0f)
            val availH = (constraints.maxHeight.toFloat() - padV).coerceAtLeast(0f)

            val pageStarts = remember(allItems, availW, availH, loading) {
                if (loading || allItems.isEmpty() || availW <= 0f || availH <= 0f) {
                    intArrayOf(0)
                } else {
                    packCandidatePageStarts(
                        items = allItems,
                        availableWidthPx = availW,
                        availableHeightPx = availH,
                        hSpacingPx = hSpacingPx,
                        vSpacingPx = vSpacingPx,
                        itemHeightPx = itemHeightPx,
                        indicatorReservePx = indicatorReservePx,
                        measureWidth = ::measureItemWidth,
                    )
                }
            }
            val pageCount = (pageStarts.size - 1).coerceAtLeast(0)
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { pageCount.coerceAtLeast(1) },
            )
            val safePage = pagerState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            val hasPrevPage = safePage > 0
            val hasNextPage = safePage < pageCount - 1

            LaunchedEffect(dataEpoch) {
                if (pagerState.currentPage != 0) {
                    pagerState.scrollToPage(0)
                }
            }
            LaunchedEffect(pageCount) {
                if (pageCount > 0 && pagerState.currentPage >= pageCount) {
                    pagerState.scrollToPage(pageCount - 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    when {
                        loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = state.textColor.copy(alpha = 0.45f),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                        pageCount == 0 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "没有更多候选",
                                    color = state.textColor.copy(alpha = 0.45f),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        else -> {
                            // 页码叠在候选区底部，Pager 占满整高；装页逻辑已预留 indicatorReserve，
                            // 避免像 weight+页码外置那样把最底一行裁掉。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                VerticalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 1,
                                    userScrollEnabled = pageCount > 1,
                                ) { page ->
                                    val pageOffset = pageStarts[page]
                                    val pageEnd = pageStarts[page + 1]
                                    val pageItems = allItems.subList(pageOffset, pageEnd)
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        pageItems.forEachIndexed { index, (candidate, comment) ->
                                            val absolute = indexBase + pageOffset + index
                                            CandidatePageItem(
                                                text = candidate,
                                                comment = comment,
                                                onClick = {
                                                    if (assocMode) {
                                                        callbacks.onAssociationSelect?.invoke(absolute)
                                                    } else {
                                                        callbacks.onCandidateSelect(
                                                            absolute,
                                                            candidate,
                                                            comment,
                                                        )
                                                    }
                                                },
                                                textColor = state.textColor,
                                                candidateFontFamily = candidateFontFamily,
                                                commentFontFamily = commentFontFamily,
                                            )
                                        }
                                    }
                                }
                                if (pageCount > 1) {
                                    Text(
                                        text = "${safePage + 1} / $pageCount",
                                        color = state.textColor.copy(alpha = 0.45f),
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp)
                                            .fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                CandidatePageSideColumn(
                    sideWidth = sideWidth,
                    hasPrevPage = hasPrevPage,
                    hasNextPage = hasNextPage,
                    onPrev = {
                        if (hasPrevPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(safePage - 1)
                            }
                        }
                    },
                    onNext = {
                        if (hasNextPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(safePage + 1)
                            }
                        }
                    },
                    sideKeyBgColor = state.sideKeyBgColor,
                    sideKeyFgColor = state.sideKeyFgColor,
                    onDelete = callbacks.onDelete,
                    onEnter = callbacks.onEnter,
                    onBack = callbacks.onBack,
                )
            }
        }

        Spacer(modifier = Modifier.height(state.bottomPaddingDp.dp))
    }
}

@Composable
private fun CandidatePageSideColumn(
    sideWidth: Dp,
    hasPrevPage: Boolean,
    hasNextPage: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    sideKeyBgColor: Color,
    sideKeyFgColor: Color,
    onDelete: (() -> Unit)?,
    onEnter: (() -> Unit)?,
    onBack: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .width(sideWidth)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CandidateSideKey(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = "上一页",
            backgroundColor = sideKeyBgColor,
            contentColor = sideKeyFgColor,
            enabled = hasPrevPage,
            onClick = onPrev,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        CandidateSideKey(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "下一页",
            backgroundColor = sideKeyBgColor,
            contentColor = sideKeyFgColor,
            enabled = hasNextPage,
            onClick = onNext,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        CandidateSideKey(
            icon = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "删除",
            backgroundColor = sideKeyBgColor,
            contentColor = sideKeyFgColor,
            enabled = onDelete != null,
            onClick = { onDelete?.invoke() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        CandidateSideKey(
            icon = Icons.AutoMirrored.Filled.KeyboardReturn,
            contentDescription = "回车",
            label = "确定",
            backgroundColor = sideKeyBgColor,
            contentColor = sideKeyFgColor,
            enabled = onEnter != null || onBack != null,
            onClick = {
                if (onEnter != null) onEnter.invoke()
                else onBack?.invoke()
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * 按 FlowRow 规则把候选装进若干页，返回每页起点下标（末尾附 items.size）。
 * 多页时预留页码条高度，避免最后一行被挤出可视区。
 */
internal fun packCandidatePageStarts(
    items: List<Pair<String, String>>,
    availableWidthPx: Float,
    availableHeightPx: Float,
    hSpacingPx: Float,
    vSpacingPx: Float,
    itemHeightPx: Float,
    indicatorReservePx: Float,
    measureWidth: (text: String, comment: String) -> Float,
): IntArray {
    if (items.isEmpty()) return intArrayOf(0)

    fun pack(reserveIndicator: Boolean): IntArray {
        val heightBudget = (availableHeightPx - if (reserveIndicator) indicatorReservePx else 0f)
            .coerceAtLeast(itemHeightPx)
        val starts = ArrayList<Int>(8)
        starts.add(0)
        var rowWidth = 0f
        var usedHeight = itemHeightPx
        var rowHasItem = false

        fun breakPage(at: Int) {
            if (starts.last() != at) starts.add(at)
            rowWidth = 0f
            usedHeight = itemHeightPx
            rowHasItem = false
        }

        items.forEachIndexed { index, (text, comment) ->
            val w = measureWidth(text, comment).coerceAtMost(availableWidthPx)
            val needNewRow = rowHasItem && rowWidth + hSpacingPx + w > availableWidthPx + 0.5f
            if (needNewRow) {
                val nextHeight = usedHeight + vSpacingPx + itemHeightPx
                if (nextHeight > heightBudget + 0.5f) {
                    breakPage(index)
                    rowWidth = w
                    rowHasItem = true
                } else {
                    usedHeight = nextHeight
                    rowWidth = w
                    rowHasItem = true
                }
            } else {
                rowWidth = if (rowHasItem) rowWidth + hSpacingPx + w else w
                rowHasItem = true
                if (usedHeight > heightBudget + 0.5f && index > starts.last()) {
                    breakPage(index)
                    rowWidth = w
                    rowHasItem = true
                }
            }
        }
        starts.add(items.size)
        return starts.toIntArray()
    }

    var result = pack(reserveIndicator = false)
    if (result.size > 2) {
        result = pack(reserveIndicator = true)
    }
    return result
}

@Composable
private fun CandidateSideKey(
    icon: ImageVector,
    contentDescription: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val fg = if (enabled) contentColor else contentColor.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed && enabled) backgroundColor.copy(alpha = 0.85f)
                else backgroundColor
            )
            .tolerantClick(
                enabled = enabled,
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
            if (label != null) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun CandidatePageItem(
    text: String,
    comment: String,
    onClick: () -> Unit,
    textColor: Color,
    candidateFontFamily: androidx.compose.ui.text.font.FontFamily,
    commentFontFamily: androidx.compose.ui.text.font.FontFamily,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed) textColor.copy(alpha = 0.12f) else Color.Transparent
            )
            .tolerantClick(
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
            fontFamily = candidateFontFamily,
        )
        if (comment.isNotEmpty()) {
            Text(
                text = comment,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                fontFamily = commentFontFamily,
            )
        }
    }
}
