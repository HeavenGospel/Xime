package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.EmojiEmotions
import androidx.compose.material.icons.twotone.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.SettingsPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutDisplaySettingsContent(
    onBack: () -> Unit,
    onNavigateToFavoriteStickers: () -> Unit = {},
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("布局与显示") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "候选词", content = {
                    val candidateTextSizePref = SettingsPreferences.getCandidateTextSize(context)
                    var candidateTextSize by remember(candidateTextSizePref) {
                        mutableStateOf(candidateTextSizePref.toFloat())
                    }

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "候选字大小",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CandidateTextSizeCard(
                            candidateTextSize = candidateTextSize,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = candidateTextSize,
                            onValueChange = { candidateTextSize = it },
                            onValueChangeFinished = {
                                SettingsPreferences.setCandidateTextSize(context, candidateTextSize.toInt())
                            },
                            valueRange = 12f..22f,
                            steps = 9
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var showComments by remember {
                        mutableStateOf(SettingsPreferences.showCandidateComments(context))
                    }

                    Text(
                        text = "编码注释",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    Text(
                        text = "在候选词旁显示对应的编码（如五笔字根）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommentDisplayCard(
                            title = "显示",
                            isSelected = showComments,
                            showComment = true,
                            onClick = {
                                showComments = true
                                SettingsPreferences.setShowCandidateComments(context, true)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CommentDisplayCard(
                            title = "隐藏",
                            isSelected = !showComments,
                            showComment = false,
                            onClick = {
                                showComments = false
                                SettingsPreferences.setShowCandidateComments(context, false)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var inputTextLocation by remember {
                        mutableStateOf(SettingsPreferences.getInputTextLocation(context))
                    }

                    Text(
                        text = "编码显示",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    Text(
                        text = "选择输入编码的显示位置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CodeDisplayCard(
                            title = "显示在输入框",
                            isSelected = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX,
                            showCodeInInputBox = true,
                            onClick = {
                                inputTextLocation = SettingsPreferences.INPUT_TEXT_INPUT_BOX
                                SettingsPreferences.setInputTextLocation(context, SettingsPreferences.INPUT_TEXT_INPUT_BOX)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CodeDisplayCard(
                            title = "显示在候选栏",
                            isSelected = inputTextLocation == SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR,
                            showCodeInInputBox = false,
                            onClick = {
                                inputTextLocation = SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR
                                SettingsPreferences.setInputTextLocation(context, SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    val pageSizePref = SettingsPreferences.getPageSize(context)
                    val effectiveValue = if (pageSizePref == 0) 20f else pageSizePref.toFloat()
                    var pageSizeSlider by remember(effectiveValue) {
                        mutableStateOf(effectiveValue)
                    }

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "每页候选词数",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${pageSizeSlider.toInt()} 个",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = pageSizeSlider,
                            onValueChange = { pageSizeSlider = it },
                            onValueChangeFinished = {
                                val intValue = pageSizeSlider.toInt()
                                SettingsPreferences.setPageSize(context, intValue)
                            },
                            valueRange = 20f..50f,
                            steps = 29
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "修改后需到方案设置中点击部署才能生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                })
            }

            item {
                SettingsSection(title = "按键手势", content = {
                    var swipeUpEnabled by remember {
                        mutableStateOf(SettingsPreferences.isSwipeUpHintsEnabled(context))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "上滑提示",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "在按键上显示上滑符号提示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = swipeUpEnabled,
                            onCheckedChange = { newValue ->
                                swipeUpEnabled = newValue
                                SettingsPreferences.setSwipeUpHintsEnabled(context, newValue)
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var swipeDownEnabled by remember {
                        mutableStateOf(SettingsPreferences.isSwipeDownHintsEnabled(context))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "下滑提示",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "在按键上显示下滑提示内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = swipeDownEnabled,
                            onCheckedChange = { newValue ->
                                swipeDownEnabled = newValue
                                SettingsPreferences.setSwipeDownHintsEnabled(context, newValue)
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var showPressBubble by remember {
                        mutableStateOf(SettingsPreferences.shouldShowPressBubble(context))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "点按弹出气泡",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "在按键上显示当前按键字符气泡（关闭可减少快速打字卡顿）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showPressBubble,
                            onCheckedChange = { newValue ->
                                showPressBubble = newValue
                                SettingsPreferences.setShowPressBubble(context, newValue)
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var cursorMoveStepSlider by remember {
                        mutableStateOf(SettingsPreferences.getCursorMoveStepDp(context).toFloat())
                    }
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "滑动移光标步进",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${cursorMoveStepSlider.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "数值越小越灵敏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = cursorMoveStepSlider,
                            onValueChange = { cursorMoveStepSlider = it },
                            onValueChangeFinished = {
                                SettingsPreferences.setCursorMoveStepDp(context, cursorMoveStepSlider.toInt())
                            },
                            valueRange = 10f..50f,
                            steps = 39
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var cursorMoveActivationSlider by remember {
                        mutableStateOf(SettingsPreferences.getCursorMoveActivationDp(context).toFloat())
                    }
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "滑动移光标激活距离",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${cursorMoveActivationSlider.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "开始移光标前需滑动的距离",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = cursorMoveActivationSlider,
                            onValueChange = { cursorMoveActivationSlider = it },
                            onValueChangeFinished = {
                                SettingsPreferences.setCursorMoveActivationDp(context, cursorMoveActivationSlider.toInt())
                            },
                            valueRange = 30f..100f,
                            steps = 69
                        )
                    }
                })
            }

            item {
                SettingsSection(title = "工具栏", content = {
                    var toolbarAlignment by remember {
                        mutableStateOf(SettingsPreferences.getToolbarAlignment(context))
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "按钮对齐",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "候选栏空闲时工具栏按钮的水平位置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                SettingsPreferences.TOOLBAR_ALIGN_START to "靠左",
                                SettingsPreferences.TOOLBAR_ALIGN_CENTER to "居中",
                                SettingsPreferences.TOOLBAR_ALIGN_END to "靠右",
                            ).forEach { (value, label) ->
                                val selected = toolbarAlignment == value
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable {
                                            toolbarAlignment = value
                                            SettingsPreferences.setToolbarAlignment(context, value)
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                })
            }

            item {
                SettingsSection(title = "表情包", content = {
                    SettingsItem(
                        icon = Icons.TwoTone.EmojiEmotions,
                        title = "管理收藏表情",
                        subtitle = "添加、删除、拖动排序",
                        onClick = onNavigateToFavoriteStickers,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var imageShareFallback by remember {
                        mutableStateOf(SettingsPreferences.isImageEmojiShareFallbackEnabled(context))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "发图失败时分享到当前应用",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "无法直接插入时，优先打开当前应用分享；对方无入口才弹出系统分享面板。默认开启，可关闭",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = imageShareFallback,
                            onCheckedChange = { newValue ->
                                imageShareFallback = newValue
                                SettingsPreferences.setImageEmojiShareFallbackEnabled(context, newValue)
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    var imageClipboardFallback by remember {
                        mutableStateOf(SettingsPreferences.isImageEmojiClipboardFallbackEnabled(context))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "发图失败时复制到剪贴板",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "分享未成功或不想用分享时，再复制到剪贴板；部分 IM 可能无法粘贴图片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = imageClipboardFallback,
                            onCheckedChange = { newValue ->
                                imageClipboardFallback = newValue
                                SettingsPreferences.setImageEmojiClipboardFallbackEnabled(context, newValue)
                            }
                        )
                    }
                })
            }
        }
    }
}
