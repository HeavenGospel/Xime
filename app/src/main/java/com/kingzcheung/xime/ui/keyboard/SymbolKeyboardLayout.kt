package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.data.RecentUsageStore
import com.kingzcheung.xime.data.SymbolCategory
import com.kingzcheung.xime.data.SymbolData
import com.kingzcheung.xime.data.SymbolPanelMemory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 符号面板：布局间距对齐数字键盘（左右 4dp、底 8dp、键间视觉 padding），
 * 背景不另铺色，沿用外层键盘主题底，避免与主键盘观感割裂。
 */
@Composable
fun SymbolKeyboardLayout(
    onSelect: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    /** 右侧功能键底色/文字色，与拼音页回车、删除同一套 specialKey。 */
    specialKeyBgColor: Color = keyBgColor,
    specialKeyTextColor: Color = textColor,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
    keyCornerRadius: Dp = 8.dp,
    keySpacingX: Dp? = null,
    keySpacingY: Dp? = null,
    /** 与拼音页回车键文案一致（如「发送」/「换行」）。 */
    enterKeyText: String = "换行",
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier,
    /** 分类 tab / 功能键振动钩子（符号点击经 onSelect 由调用方统一振动）。 */
    onHapticFeedback: (() -> Unit)? = null,
    onKeyPressDown: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    // 最近使用（LRU）：作为第一个分类页，点击符号时置顶记录
    var recentSymbols by remember {
        mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_SYMBOLS))
    }
    val displayCategories = remember(recentSymbols) {
        listOf(SymbolCategory(name = "最近使用", id = "recentSymbols", symbols = recentSymbols)) +
            SymbolData.categories
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()
    // 侧栏略宽于 48，贴近数字页右侧功能列观感，同时仍给 5 列符号留足宽度
    val sideKeyWidth = if (isLandscape) 64.dp else 56.dp
    val tabBarHeight = 56.dp
    val hPad = keySpacingX ?: 2.dp
    val vPad = keySpacingY ?: 2.dp

    val initialPage = remember(displayCategories) {
        val savedId = SymbolPanelMemory.loadCategoryId(context)
        val idx = displayCategories.indexOfFirst { it.id == savedId }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { displayCategories.size }
    )

    // 滑动或点选分类后记住，下次打开恢复
    LaunchedEffect(pagerState, displayCategories) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                displayCategories.getOrNull(page)?.id?.let { id ->
                    SymbolPanelMemory.saveCategoryId(context, id)
                }
            }
    }

    CompositionLocalProvider(
        LocalKeyCornerRadius provides keyCornerRadius,
        LocalKeyVisualPadding provides PaddingValues(horizontal = hPad, vertical = vPad),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                // 不铺实心背景：与数字页一样透出外层键盘主题底
                .padding(
                    start = if (isLandscape) 50.dp else 4.dp,
                    end = if (isLandscape) 50.dp else 4.dp,
                    bottom = 8.dp,
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 左侧：符号网格 + 底部分类栏
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val category = displayCategories[page]
                            // 竖屏 5×4；横屏 10×4。行高取「均分高度」与「列宽」的较小值，尽量接近正方形。
                            val columns = if (isLandscape) 10 else 5
                            val visibleRows = 4

                            if (category.symbols.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无最近使用",
                                        color = textColor.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    // 一屏固定 4 行铺满；列宽由 5 列 + 收窄侧栏决定，整体更接近正方形
                                    val rowHeight = maxHeight / visibleRows
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        category.symbols.chunked(columns).forEach { rowSymbols ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(rowHeight),
                                            ) {
                                                rowSymbols.forEach { symbol ->
                                                    SymbolGridKey(
                                                        text = symbol,
                                                        onClick = {
                                                            recentSymbols = RecentUsageStore.record(
                                                                context,
                                                                RecentUsageStore.KEY_RECENT_SYMBOLS,
                                                                symbol,
                                                            )
                                                            onSelect(symbol)
                                                        },
                                                        backgroundColor = keyBgColor,
                                                        textColor = textColor,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight(),
                                                        shadowEnabled = shadowEnabled,
                                                        shadowElevation = shadowElevation,
                                                        shadowShapeRadius = shadowShapeRadius,
                                                    )
                                                }
                                                repeat(columns - rowSymbols.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 底部分类切换
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tabBarHeight)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayCategories.forEachIndexed { index, category ->
                            SymbolCategoryTab(
                                name = category.name,
                                isSelected = index == pagerState.currentPage,
                                onClick = {
                                    onHapticFeedback?.invoke()
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                // 未选中透出键盘底；选中用强调色
                                backgroundColor = Color.Transparent,
                                textColor = textColor,
                                selectedBackgroundColor = accentColor,
                                modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                // 右侧：返回 / 空格 / 删除 / 回车 —— 键帽样式对齐数字页与拼音页
                Column(
                    modifier = Modifier
                        .width(sideKeyWidth)
                        .fillMaxHeight(),
                ) {
                    IconKeyButton(
                        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                        onClick = { onSelect("back") },
                        backgroundColor = specialKeyBgColor,
                        iconColor = specialKeyTextColor,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onPress = { onKeyPressDown?.invoke("back") },
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    )
                    // 与数字页空格同款：文字键 + 跟随键帽字号缩放
                    KeyButton(
                        text = "空格",
                        onClick = { onSelect("space") },
                        backgroundColor = specialKeyBgColor,
                        textColor = specialKeyTextColor,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onPress = { onKeyPressDown?.invoke("space") },
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    )
                    // 与拼音/数字页删除同款：退格图标 +「清空」上滑提示
                    SwipeableIconKeyButton(
                        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                        onClick = { onSelect("delete") },
                        backgroundColor = specialKeyBgColor,
                        iconColor = specialKeyTextColor,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        swipeText = "清空",
                        onSwipe = { onSelect("clear_composition") },
                        onLongClick = { onSelect("delete") },
                        onPress = { onKeyPressDown?.invoke("delete") },
                        swipeUpLabel = "上滑清空",
                        swipeDownLabel = "下滑撤回",
                        onSwipeUp = { onSelect("clear_all") },
                        onSwipeDown = { onSelect("undo_clear") },
                        onSwipeLeft = { onSelect("clear_composition") },
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    )
                    // 与拼音页回车同款：动态 enterKeyText，跟随键帽字号
                    KeyButton(
                        text = enterKeyText,
                        onClick = { onSelect("enter") },
                        backgroundColor = specialKeyBgColor,
                        textColor = specialKeyTextColor,
                        modifier = Modifier.height(tabBarHeight).fillMaxWidth(),
                        onPress = { onKeyPressDown?.invoke("enter") },
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    )
                }
            }

            if (bottomPaddingDp > 0) {
                Spacer(modifier = Modifier.height(bottomPaddingDp.dp))
            }
        }
    }
}

@Composable
private fun SymbolGridKey(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    // 用 clickable 而非 KeyButton：保留键帽观感，同时把拖动手势留给
    // HorizontalPager（左右翻类）与 verticalScroll（上下翻页）。
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius = LocalKeyCornerRadius.current
    val visualPad = LocalKeyVisualPadding.current
    val density = LocalDensity.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx),
                )
            }
        } else Modifier
    }
    Box(
        modifier = modifier
            .padding(visualPad)
            .then(shadowModifier)
            .clip(shape)
            .background(if (isPressed) backgroundColor.copy(alpha = 0.7f) else backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = (22f * LocalKeycapTextScale.current).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun SymbolCategoryTab(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    selectedBackgroundColor: Color = textColor.copy(alpha = 0.15f),
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isSelected || isPressed) {
                    Modifier.background(
                        when {
                            // 与表情底栏一致：强调色 40% 透明度，避免整块过深
                            isSelected -> selectedBackgroundColor.copy(alpha = 0.4f)
                            else -> textColor.copy(alpha = 0.08f)
                        }
                    )
                } else {
                    Modifier
                }
            )
            .tolerantClick(
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (isSelected) textColor else textColor.copy(alpha = 0.5f)
        )
    }
}
