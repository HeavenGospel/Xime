package com.kingzcheung.xime.ui.keyboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.data.EmojiCategory
import com.kingzcheung.xime.data.EmojiData
import com.kingzcheung.xime.data.EmojiPanelMemory
import com.kingzcheung.xime.data.FavoriteStickersStore
import com.kingzcheung.xime.data.RecentUsageStore
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.plugin.core.api.PluginResultItem
import com.kingzcheung.xime.plugin.core.api.PluginIcon
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.emoji.FavoriteStickerImportActivity
import android.view.HapticFeedbackConstants
@Composable
fun EmojiKeyboardLayout(
    onEmojiSelect: (String) -> Unit,
    onImageEmojiSelect: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier,
    /** 分类 tab 切换的振动钩子（emoji 点击/删除经 onEmojiSelect 由调用方统一振动）。 */
    onHapticFeedback: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    // 图标按钮容器色：surface 与 primary 的混合色调（带种子色但不过于强烈）
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.15f
    )

    var selectedTopTabIndex by remember { mutableIntStateOf(0) }
    var selectedSubCategoryIndex by remember { mutableIntStateOf(0) }
    var tabMemoryReady by remember { mutableStateOf(false) }

    val allCategories by ExtensionManager.emojiCategoriesFlow.collectAsStateWithLifecycle()
    val pluginCategories = allCategories.filter { it.isPlugin }
    val builtinCategories = allCategories.filter { !it.isPlugin }

    // 打开面板时再拉一次，覆盖「安装/启用后键盘已在前台」未刷新的情况
    LaunchedEffect(Unit) {
        ExtensionManager.loadEmojiDataFromPlugins(context)
        FavoriteStickersStore.ensureLoaded(context)
    }

    // 最近使用（LRU）：内置 emoji 分区的第一个子分类
    var recentEmojis by remember {
        mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_EMOJIS))
    }
    val recentCategory = EmojiCategory(name = "最近使用", icon = "🕘", emojis = recentEmojis)

    // 收藏表情：顶栏独立 Tab（与 Emoji / 插件表情包同级）
    val favoriteStickers by FavoriteStickersStore.stickersFlow.collectAsStateWithLifecycle()
    val favoritesCategory = remember(favoriteStickers) {
        EmojiCategory(
            name = FavoriteStickersStore.CATEGORY_NAME,
            icon = FavoriteStickersStore.CATEGORY_ICON,
            emojis = emptyList(),
            emojiItems = favoriteStickers.map { s ->
                PluginResultItem(
                    id = s.id,
                    text = "表情",
                    imageUrl = FavoriteStickersStore.absolutePath(context, s),
                )
            },
            layoutColumns = 4,
            layoutItemHeightDp = 72,
        )
    }

    val displayBuiltinCategories = remember(builtinCategories, recentEmojis) {
        listOf(recentCategory) + builtinCategories
    }

    var favoriteMenuId by remember { mutableStateOf<String?>(null) }

    // 顶栏：0=Emoji，1=收藏，2+=插件表情包
    val topTabFavorites = 1
    val topTabPluginBase = 2

    // 按 pluginId 分组插件子分类（用于顶层 tab 和底部子分类 tab）
    val pluginGroupEntries = remember(pluginCategories) {
        pluginCategories.groupBy { it.pluginId ?: it.name }.entries.toList()
    }

    // 恢复上次顶栏 / 子分类
    LaunchedEffect(pluginGroupEntries, displayBuiltinCategories.size) {
        if (tabMemoryReady) return@LaunchedEffect
        val tabKey = EmojiPanelMemory.loadTabKey(context)
        val sub = EmojiPanelMemory.loadSubIndex(context)
        when {
            tabKey == EmojiPanelMemory.TAB_FAVORITES -> {
                selectedTopTabIndex = topTabFavorites
                selectedSubCategoryIndex = 0
            }
            tabKey.startsWith("plugin:") -> {
                val id = tabKey.removePrefix("plugin:")
                val idx = pluginGroupEntries.indexOfFirst { it.key == id }
                if (idx >= 0) {
                    selectedTopTabIndex = topTabPluginBase + idx
                    val last = pluginGroupEntries[idx].value.lastIndex.coerceAtLeast(0)
                    selectedSubCategoryIndex = sub.coerceIn(0, last)
                } else {
                    selectedTopTabIndex = 0
                    selectedSubCategoryIndex = 0
                }
            }
            else -> {
                selectedTopTabIndex = 0
                val last = displayBuiltinCategories.lastIndex.coerceAtLeast(0)
                selectedSubCategoryIndex = sub.coerceIn(0, last)
            }
        }
        tabMemoryReady = true
    }

    // 记住当前选择
    LaunchedEffect(selectedTopTabIndex, selectedSubCategoryIndex, pluginGroupEntries, tabMemoryReady) {
        if (!tabMemoryReady) return@LaunchedEffect
        val tabKey = when (selectedTopTabIndex) {
            0 -> EmojiPanelMemory.TAB_BUILTIN
            topTabFavorites -> EmojiPanelMemory.TAB_FAVORITES
            else -> {
                val g = selectedTopTabIndex - topTabPluginBase
                if (g in pluginGroupEntries.indices) {
                    EmojiPanelMemory.pluginTabKey(pluginGroupEntries[g].key)
                } else {
                    EmojiPanelMemory.TAB_BUILTIN
                }
            }
        }
        EmojiPanelMemory.save(context, tabKey, selectedSubCategoryIndex)
    }

    val favoritesPageIndex = displayBuiltinCategories.size
    val totalPages = displayBuiltinCategories.size + 1 + pluginCategories.size

    // 当前顶层 tab 对应的子分类列表（收藏无子分类）
    val currentSubCategories = when (selectedTopTabIndex) {
        0 -> displayBuiltinCategories
        topTabFavorites -> emptyList()
        else -> {
            val groupIdx = selectedTopTabIndex - topTabPluginBase
            if (groupIdx in pluginGroupEntries.indices) pluginGroupEntries[groupIdx].value
            else emptyList()
        }
    }

    val currentPageIndex = when (selectedTopTabIndex) {
        0 -> selectedSubCategoryIndex.coerceIn(0, maxOf(0, displayBuiltinCategories.lastIndex))
        topTabFavorites -> favoritesPageIndex
        else -> {
            val groupIdx = selectedTopTabIndex - topTabPluginBase
            val startPage = favoritesPageIndex + 1 +
                pluginGroupEntries.take(groupIdx.coerceAtLeast(0)).sumOf { it.value.size }
            val groupSize =
                if (groupIdx in pluginGroupEntries.indices) pluginGroupEntries[groupIdx].value.lastIndex
                else 0
            startPage + selectedSubCategoryIndex.coerceIn(0, maxOf(0, groupSize))
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val emojiColumns = if (isLandscape) 15 else 8

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 导航区：返回按钮 + 顶层 Tab（Emoji / 插件）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = if (isLandscape) 50.dp else 8.dp, end = if (isLandscape) 50.dp else 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconButtonContainer)
                        .tolerantClick { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "返回",
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 顶层 Tab（ClipboardView 样式）
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(iconButtonContainer)
                        .padding(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Emoji 主 Tab
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (selectedTopTabIndex == 0) accentColor.copy(0.4f)
                                    else Color.Transparent
                                )
                                .tolerantClick {
                                    onHapticFeedback?.invoke()
                                    selectedTopTabIndex = 0
                                    selectedSubCategoryIndex = 0
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "😊",
                                fontSize = 14.sp
                            )
                        }

                        // 收藏 Tab（与 Emoji、插件表情包同级）
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (selectedTopTabIndex == topTabFavorites) accentColor.copy(0.4f)
                                    else Color.Transparent
                                )
                                .tolerantClick {
                                    onHapticFeedback?.invoke()
                                    selectedTopTabIndex = topTabFavorites
                                    selectedSubCategoryIndex = 0
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = FavoriteStickersStore.CATEGORY_ICON,
                                fontSize = 14.sp
                            )
                        }

                        // 插件 Tab（按 pluginId 分组，每插件一个顶层 tab）
                        pluginGroupEntries.forEachIndexed { index, (_, subCats) ->
                            val firstCat = subCats.first()
                            val pluginIcon = firstCat.pluginIcon
                            val tabIndex = topTabPluginBase + index
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (selectedTopTabIndex == tabIndex) accentColor.copy(0.4f)
                                        else Color.Transparent
                                    )
                                    .tolerantClick {
                                        onHapticFeedback?.invoke()
                                        selectedTopTabIndex = tabIndex
                                        selectedSubCategoryIndex = 0
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (pluginIcon?.assetName != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(pluginIcon.assetName)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = firstCat.name,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(2.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        text = pluginIcon?.text ?: firstCat.icon,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val pagerState = rememberPagerState(
            initialPage = currentPageIndex,
            pageCount = { totalPages }
        )

        // 外部切换分类时同步到 Pager
        LaunchedEffect(currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }

        // Pager 滑动时同步到外部状态
        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
            val page = pagerState.currentPage
            if (!pagerState.isScrollInProgress && page != currentPageIndex) {
                when {
                    page < displayBuiltinCategories.size -> {
                        selectedTopTabIndex = 0
                        selectedSubCategoryIndex = page
                    }
                    page == favoritesPageIndex -> {
                        selectedTopTabIndex = topTabFavorites
                        selectedSubCategoryIndex = 0
                    }
                    else -> {
                        var remaining = page - favoritesPageIndex - 1
                        for ((groupIdx, entry) in pluginGroupEntries.withIndex()) {
                            if (remaining < entry.value.size) {
                                selectedTopTabIndex = topTabPluginBase + groupIdx
                                selectedSubCategoryIndex = remaining
                                break
                            }
                            remaining -= entry.value.size
                        }
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = if (isLandscape) 50.dp else 4.dp)
                .padding(bottom = 4.dp)
        ) { pageIndex ->
            val category = when {
                pageIndex < displayBuiltinCategories.size -> displayBuiltinCategories[pageIndex]
                pageIndex == favoritesPageIndex -> favoritesCategory
                else -> pluginCategories[pageIndex - favoritesPageIndex - 1]
            }

            val emojiColumns = if (isLandscape) 15 else 8
            if (category.name == FavoriteStickersStore.CATEGORY_NAME) {
                FavoritesStickerGrid(
                    stickers = favoriteStickers,
                    backgroundColor = backgroundColor,
                    textColor = textColor,
                    accentColor = accentColor,
                    onAdd = { FavoriteStickerImportActivity.start(context) },
                    onSend = { path ->
                        if (onImageEmojiSelect != null) {
                            onImageEmojiSelect(path)
                        } else {
                            Toast.makeText(context, "当前无法发送图片表情", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLongPress = { id -> favoriteMenuId = id },
                )
            } else if (category.emojiItems != null) {
                val hasImages = category.emojiItems.any { it.imageUrl != null }
                val defaultCols = if (hasImages) 6 else emojiColumns
                val columns = if (category.layoutColumns > 0) category.layoutColumns else defaultCols
                val itemHeightDp = if (category.layoutItemHeightDp > 0) category.layoutItemHeightDp
                    else (if (hasImages) 60 else 40)

                // 行分组缓存：chunked 每次重组重算会产生大量临时列表，
                // remember 后仅在数据/列数变化时重建
                val emojiRows = remember(category.emojiItems, columns) {
                    category.emojiItems.chunked(columns)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 图片表情行间距与列间距(6dp)对齐；文本表情保持紧凑 2dp
                    verticalArrangement = Arrangement.spacedBy(if (hasImages) 6.dp else 2.dp)
                ) {
                    items(
                        items = emojiRows,
                        // 稳定 key 提升滚动复用率（id 在单分类内唯一）
                        key = { row -> row.firstOrNull()?.id ?: row.hashCode() },
                        contentType = { if (hasImages) "emoji-image-row" else "emoji-text-row" }
                    ) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { item ->
                                PluginEmojiButton(
                                    emojiItem = item,
                                    defaultHeightDp = itemHeightDp,
                                    backgroundColor = backgroundColor,
                                    textColor = textColor,
                                    onClick = {
                                        val imageUrl = item.imageUrl
                                        if (imageUrl != null && onImageEmojiSelect != null) {
                                            onImageEmojiSelect(imageUrl)
                                        } else if (imageUrl != null) {
                                            val copyFallback =
                                                SettingsPreferences.isImageEmojiClipboardFallbackEnabled(context)
                                            if (copyFallback) {
                                                val ok = ClipboardManager.getInstance(context)
                                                    .copyImageToSystemClipboard(imageUrl, item.text)
                                                Toast.makeText(
                                                    context,
                                                    if (ok) "已复制表情，可粘贴发送" else "复制失败",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "当前无法发送图片表情",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            onEmojiSelect(item.insertText ?: item.text)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier
                                    .weight(1f)
                                    .height((itemHeightDp).dp))
                            }
                        }
                    }
                }
            } else if (category.emojis.isEmpty()) {
                // 最近使用为空时的占位提示
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(category.emojis.chunked(emojiColumns)) { rowEmojis ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowEmojis.forEach { emoji ->
                                EmojiButton(
                                    emoji = emoji,
                                    onClick = {
                                        recentEmojis = RecentUsageStore.record(
                                            context, RecentUsageStore.KEY_RECENT_EMOJIS, emoji
                                        )
                                        onEmojiSelect(emoji)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(emojiColumns - rowEmojis.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 底部：子分类 Tab 或留空 + 删除按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = if (isLandscape) 50.dp else 4.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentSubCategories.isNotEmpty()) {
                // 显示当前顶层 tab 的子分类
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    currentSubCategories.forEachIndexed { index, category ->
                        if (category.isPlugin) {
                            // 插件子分类：显示分类名，不用 pluginIcon（那是插件级图标）
                            EmojiCategoryTab(
                                icon = category.name,
                                pluginIcon = null,
                                isSelected = index == selectedSubCategoryIndex,
                                onClick = {
                                    onHapticFeedback?.invoke()
                                    selectedSubCategoryIndex = index
                                },
                                backgroundColor = backgroundColor,
                                textColor = textColor,
                                selectedBackgroundColor = accentColor,
                                modifier = Modifier.widthIn(min = 36.dp)
                            )
                        } else {
                            EmojiCategoryTab(
                                icon = category.icon,
                                pluginIcon = category.pluginIcon,
                                isSelected = index == selectedSubCategoryIndex,
                                onClick = {
                                    onHapticFeedback?.invoke()
                                    selectedSubCategoryIndex = index
                                },
                                backgroundColor = backgroundColor,
                                textColor = textColor,
                                selectedBackgroundColor = accentColor,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            val isFavoritesTab = selectedTopTabIndex == topTabFavorites
            val pluginGroupIdx = selectedTopTabIndex - topTabPluginBase
            val isPluginTab = selectedTopTabIndex >= topTabPluginBase &&
                pluginGroupIdx in pluginGroupEntries.indices
            val currentPluginId = if (isPluginTab) {
                pluginGroupEntries[pluginGroupIdx].key
            } else {
                null
            }

            if (isFavoritesTab || isPluginTab) {
                KeyButton(
                    text = "设置",
                    onClick = {
                        onHapticFeedback?.invoke()
                        val intent = if (isFavoritesTab) {
                            MainActivity.buildOpenSettingsIntent(context, "favorite_stickers")
                        } else {
                            MainActivity.buildOpenSettingsIntent(
                                context,
                                "plugins",
                                pluginManageId = currentPluginId,
                            )
                        }
                        context.startActivity(intent)
                    },
                    backgroundColor = backgroundColor,
                    textColor = textColor,
                    modifier = Modifier.width(48.dp),
                    fontSize = 12.sp,
                )
            } else {
                KeyButton(
                    text = "删除",
                    onClick = { onEmojiSelect("delete") },
                    backgroundColor = backgroundColor,
                    textColor = textColor,
                    modifier = Modifier.width(48.dp),
                    fontSize = 12.sp,
                )
            }
        }

        // 底部留空
        Spacer(modifier = Modifier.height(if (isLandscape) 15.dp else bottomPaddingDp.dp))
    }

    // IME 窗口内不要用 AlertDialog（独立 Window 常被挡/看不见），用面板内浮层
    val menuId = favoriteMenuId
    if (menuId != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(8f)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { favoriteMenuId = null },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) { /* 阻止点击穿透到遮罩关闭 */ }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "移到前面",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            FavoriteStickersStore.moveToFront(context, menuId)
                            favoriteMenuId = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            FavoriteStickersStore.delete(context, menuId)
                            favoriteMenuId = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
                Text(
                    text = "取消",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { favoriteMenuId = null }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }
    } // Box
}

@Composable
private fun FavoritesStickerGrid(
    stickers: List<FavoriteStickersStore.Sticker>,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    onAdd: () -> Unit,
    onSend: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val context = LocalContext.current
    val isLandscape =
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 6 else 4
    val itemHeightDp = 72

    // 首位「+」+ 收藏列表
    val cells: List<Any?> = listOf(null) + stickers
    val rows = cells.chunked(columns)

    if (stickers.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FavoriteAddButton(
                accentColor = accentColor,
                textColor = textColor,
                onClick = onAdd,
                modifier = Modifier.size(itemHeightDp.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "添加自定义表情",
                color = textColor.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            Text(
                text = "支持从相册选择 PNG / JPG / GIF / WebP 等",
                color = textColor.copy(alpha = 0.35f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(rows.size) { rowIndex ->
            val row = rows[rowIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { cell ->
                    if (cell == null) {
                        FavoriteAddButton(
                            accentColor = accentColor,
                            textColor = textColor,
                            onClick = onAdd,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    } else {
                        val sticker = cell as FavoriteStickersStore.Sticker
                        val path = FavoriteStickersStore.absolutePath(context, sticker)
                        val view = LocalView.current
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if ((backgroundColor.red + backgroundColor.green + backgroundColor.blue) / 3f > 0.5f)
                                        Color.White.copy(alpha = 0.8f)
                                    else Color.LightGray.copy(alpha = 0.15f)
                                )
                                .combinedClickable(
                                    onClick = { onSend(path) },
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onLongPress(sticker.id)
                                    }
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(path)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = "收藏表情",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun FavoriteAddButton(
    accentColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .tolerantClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "添加表情",
            tint = accentColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun EmojiCategoryTab(
    icon: String,
    pluginIcon: PluginIcon? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    selectedBackgroundColor: Color = textColor.copy(alpha = 0.15f),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))

            .background(
                if (isSelected) selectedBackgroundColor.copy(0.4f)
                else backgroundColor
            )
            .padding(horizontal = 5.dp)
            .tolerantClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (pluginIcon?.assetName != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(pluginIcon.assetName)
                    .crossfade(true)
                    .build(),
                contentDescription = icon,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = pluginIcon?.text ?: icon,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmojiButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .tolerantClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PluginEmojiButton(
    emojiItem: PluginResultItem,
    onClick: () -> Unit,
    defaultHeightDp: Int = 40,
    backgroundColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isLightTheme =
        (backgroundColor.red + backgroundColor.green + backgroundColor.blue) / 3f > 0.5f
    val buttonBackgroundColor = if (isLightTheme) Color.White.copy(alpha = 0.8f)
    else Color.LightGray.copy(alpha = 0.15f)
    val contentColor = if (isLightTheme) Color.Black else textColor

    Box(
        modifier = modifier
            .height(defaultHeightDp.dp)
            .then(
                if (emojiItem.imageUrl != null) Modifier.aspectRatio(1f)
                else Modifier.fillMaxWidth()
            )
            .clip(RoundedCornerShape(4.dp))
            .background(buttonBackgroundColor)
            .tolerantClick(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (emojiItem.imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(emojiItem.imageUrl)
                    // 高频网格滚动场景：关闭渐显动画，降低加载突发期的重绘压力
                    .crossfade(false)
                    .build(),
                contentDescription = emojiItem.text,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = emojiItem.text,
                fontSize = 12.sp,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}