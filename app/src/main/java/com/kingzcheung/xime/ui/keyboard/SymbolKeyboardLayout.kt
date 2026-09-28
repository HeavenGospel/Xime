package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.data.RecentUsageStore
import com.kingzcheung.xime.data.SymbolCategory
import com.kingzcheung.xime.data.SymbolData
import kotlinx.coroutines.launch

@Composable
fun SymbolKeyboardLayout(
    onSelect: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier,
    /** 分类 tab / 功能键振动钩子（符号点击经 onSelect 由调用方统一振动）。 */
    onHapticFeedback: (() -> Unit)? = null,
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
    val sideKeyWidth = if (isLandscape) 72.dp else 68.dp
    val tabBarHeight = 56.dp

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { displayCategories.size }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = if (isLandscape) 50.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        .padding(bottom = 4.dp)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val category = displayCategories[page]
                        // 竖屏 4 列 × 可视 4 行；横屏 8 列 × 4 行
                        val columns = if (isLandscape) 8 else 4
                        val visibleRows = 4
                        val rowGap = 4.dp

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
                                val rowHeight = (
                                    maxHeight - rowGap * (visibleRows - 1)
                                    ) / visibleRows
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(rowGap)
                                ) {
                                    category.symbols.chunked(columns).forEach { rowSymbols ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rowHeight),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            rowSymbols.forEach { symbol ->
                                                SymbolButton(
                                                    symbol = symbol,
                                                    onClick = {
                                                        recentSymbols = RecentUsageStore.record(
                                                            context,
                                                            RecentUsageStore.KEY_RECENT_SYMBOLS,
                                                            symbol,
                                                        )
                                                        onSelect(symbol)
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight(),
                                                    textColor = textColor,
                                                    backgroundColor = keyBgColor,
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

                // 底部分类切换（增高）
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
                            backgroundColor = backgroundColor,
                            textColor = textColor,
                            selectedBackgroundColor = accentColor,
                            modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp)
                        )
                    }
                }
            }

            // 右侧操作列：由上到下 返回 / 空格 / 删除 / 换行（底行对齐分类栏）
            Column(
                modifier = Modifier
                    .width(sideKeyWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SymbolSideKey(
                    text = "返回",
                    onClick = { onSelect("back") },
                    textColor = textColor,
                    backgroundColor = keyBgColor,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                SymbolSideKey(
                    text = "空格",
                    onClick = { onSelect("space") },
                    textColor = textColor,
                    backgroundColor = keyBgColor,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                SymbolSideKey(
                    text = "删除",
                    onClick = { onSelect("delete") },
                    textColor = textColor,
                    backgroundColor = keyBgColor,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                SymbolSideKey(
                    text = "换行",
                    onClick = { onSelect("enter") },
                    textColor = textColor,
                    backgroundColor = keyBgColor,
                    modifier = Modifier.height(tabBarHeight).fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(bottomPaddingDp.dp))
    }
}

@Composable
private fun SymbolSideKey(
    text: String,
    onClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed) androidx.compose.ui.graphics.lerp(backgroundColor, Color.Black, 0.2f)
                else backgroundColor
            )
            .tolerantClick(
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun SymbolButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    backgroundColor: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed) androidx.compose.ui.graphics.lerp(backgroundColor, Color.Black, 0.2f)
                else backgroundColor
            )
            .tolerantClick(
                showRipple = false,
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            color = textColor,
            fontFamily = AppFonts.keyFontFamily
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) selectedBackgroundColor
                else backgroundColor
            )
            .tolerantClick(onClick = onClick)
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
