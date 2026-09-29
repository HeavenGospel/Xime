package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.PredictionManager
import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState
import kotlin.math.roundToInt

@Immutable
data class CandidateBarVisuals(
    val backgroundColor: Color,
    val textColor: Color,
    val dividerColor: Color,
    val accentColor: Color = Color(0xFF1A73E8),
    val selectedTextColor: Color = Color(0xFF1A73E8),
    val isDarkTheme: Boolean = false,
)

data class CandidateBarCallbacks(
    val onCandidateSelect: (Int) -> Unit,
    val onLogoClick: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    val onHideKeyboard: (() -> Unit)? = null,
    val onShowMoreCandidates: (() -> Unit)? = null,
    val onClearAssociation: (() -> Unit)? = null,
    val onInputTextClick: (() -> Unit)? = null,
    val onAssociationSelect: ((Int) -> Unit)? = null,
    // 长按候选：抛事件给宿主（键盘视图内弹确认覆盖层，不弹独立窗口——
    // 焦点型弹窗会抢焦点导致 IME 被系统收起）。
    val onCandidateLongPress: ((Int) -> Unit)? = null,
    /** 候选栏按宽度截断后实际展示的条数，供「更多」页跳过已展示项。 */
    val onBarVisibleCandidateCount: ((Int) -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CandidateBar(
    state: CandidateBarState,
    page: KeyboardPage = KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.FULL),
    toolbarActions: List<ToolbarAction> = emptyList(),
    visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks,
    inlineSuggestions: List<*> = listOf<Any>(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    isFloatingMode: Boolean = false,
    isVoiceSticky: Boolean = false,
    voiceAmplitude: Float = 0f,
    voiceSpectrum: FloatArray = FloatArray(16),
    voiceRecognitionState: RecognitionState = RecognitionState.IDLE,
    voicePluginName: String = "",
) {
    val configuration = LocalConfiguration.current
    val isLandscape = !isFloatingMode && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape) 50.dp else 8.dp
    val context = LocalContext.current

    // Idle 工具栏横向滚动（仅内容仍超出时启用；默认对齐由下方自适应居中逻辑处理）
    val toolbarScrollState = rememberScrollState()

    // M3 角色色：图标按钮背景用 surface 与 primary 的混合色调（带种子色但不过于强烈），
    // 按压态用 onSurface 12% state layer
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.15f
    )
    val iconButtonTint = MaterialTheme.colorScheme.onSurfaceVariant
    val showComments = SettingsPreferences.showCandidateComments(context)
    val inputTextLocation = SettingsPreferences.getInputTextLocation(context)
    val showInputBoxStyle = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX
    val candidateTextSize = SettingsPreferences.getCandidateTextSize(context)
    val candidateFontFamily = AppFonts.candidateFontFamily
    val commentFontFamily = AppFonts.commentFontFamily

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    // CandidateItem：horizontal 4.dp × 2
    val itemPaddingPx = with(density) { 8.dp.toPx() }
    val spacingPx = with(density) { 4.dp.toPx() }
    // 测量与实绘（表情/扩展字/Medium 字重）常有偏差，留余量避免末词被裁切
    val fitSafetyPx = with(density) { 4.dp.toPx() }

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    // 与 Column.padding(horizontal) 一致，勿再额外扣 16dp
    val horizontalPadPx = with(density) { horizontalPadding.toPx() * 2 }
    // 右侧仅「翻页/清空」：Spacer(4)+宽30；打字态不会同时出现收起键盘
    val rightSidePx = with(density) {
        when (state) {
            is CandidateBarState.Idle -> {
                var w = 0f
                if (callbacks.onHideKeyboard != null) w += 4.dp.toPx() + 32.dp.toPx()
                w
            }
            is CandidateBarState.AssociationOnly -> {
                // 联想（含 @ 域名）：只留清空，不截断进翻页；栏内可左滑看完
                var w = 0f
                if (callbacks.onClearAssociation != null) w += 4.dp.toPx() + 1.dp.toPx() + 30.dp.toPx()
                w
            }
            else -> {
                // 预留翻页键，避免装完后又挤掉末词
                if (callbacks.onShowMoreCandidates != null) 34.dp.toPx() else 0f
            }
        }
    }
    // 优先用 LazyRow 实测宽度；首帧用屏宽估算
    var measuredRowWidthPx by remember { mutableFloatStateOf(0f) }
    val estimatedRowWidthPx = (screenWidthPx - horizontalPadPx - rightSidePx).coerceAtLeast(0f)
    val lazyRowWidthPx = (if (measuredRowWidthPx > 0f) measuredRowWidthPx else estimatedRowWidthPx)
        .coerceAtLeast(0f)

    val measureCandidateWidth: (String, Float) -> Float = remember(textMeasurer, candidateFontFamily) {
        { text, sizeSp ->
            textMeasurer.measure(
                text = AnnotatedString(text),
                // 与首选项 Medium 对齐，略偏保守，减少表情/扩展字量窄后裁切
                style = TextStyle(
                    fontSize = sizeSp.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = candidateFontFamily,
                ),
            ).size.width.toFloat()
        }
    }

    val displayCandidates: List<String>
    val displayAssociation: List<String>
    val displayComments: List<String>
    val hasAnyMore: Boolean
    val showInputTextRow: Boolean
    val showLeftIcon: Boolean

    when (val s = state) {
        is CandidateBarState.Idle -> {
            displayCandidates = emptyList()
            displayAssociation = emptyList()
            displayComments = emptyList()
            hasAnyMore = false
            showLeftIcon = true
        }
        is CandidateBarState.ChineseCandidates -> {
            // 候选栏只放一屏内完整可见的词；超出的进「更多」，不横向滑动。
            val fitted = remember(
                s.candidates, s.comments, showComments, lazyRowWidthPx, fitSafetyPx,
                candidateTextSize, itemPaddingPx, spacingPx, measureCandidateWidth,
            ) {
                fitCandidatesToWidth(
                    candidates = s.candidates,
                    comments = s.comments,
                    showComments = showComments,
                    availablePx = (lazyRowWidthPx - fitSafetyPx).coerceAtLeast(0f),
                    itemPaddingPx = itemPaddingPx,
                    spacingPx = spacingPx,
                    measureText = measureCandidateWidth,
                    candidateTextSize = candidateTextSize.toFloat(),
                )
            }
            displayCandidates = fitted.texts
            displayComments = fitted.comments
            hasAnyMore = fitted.texts.size < s.candidates.size || s.hasMore
            showLeftIcon = false
            displayAssociation = remember(
                s.associationCandidates, fitted.texts, s.inputText, measureCandidateWidth,
                lazyRowWidthPx, fitSafetyPx, candidateTextSize, itemPaddingPx, spacingPx,
                showComments, fitted.comments, density, textMeasurer, commentFontFamily,
            ) {
                val assocAvail = (lazyRowWidthPx - fitSafetyPx).coerceAtLeast(0f)
                if (fitted.texts.isEmpty()) {
                    fitCandidatesToWidth(
                        candidates = s.associationCandidates,
                        comments = emptyList(),
                        showComments = false,
                        availablePx = assocAvail,
                        itemPaddingPx = itemPaddingPx,
                        spacingPx = spacingPx,
                        measureText = measureCandidateWidth,
                        candidateTextSize = candidateTextSize.toFloat(),
                    ).texts
                } else {
                    val regularWidthPx = fitted.texts.mapIndexed { i, c ->
                        var w = measureCandidateWidth(c, candidateTextSize.toFloat()) + itemPaddingPx
                        val comment = fitted.comments.getOrElse(i) { "" }
                        if (showComments && comment.isNotEmpty()) {
                            w += with(density) { 3.dp.toPx() } +
                                textMeasurer.measure(
                                    text = AnnotatedString(comment),
                                    style = TextStyle(
                                        fontSize = (candidateTextSize * 11f / 19f).sp,
                                        fontFamily = commentFontFamily,
                                    ),
                                ).size.width.toFloat()
                        }
                        w
                    }.sum() +
                        (fitted.texts.size - 1).coerceAtLeast(0) * spacingPx
                    val dividerWidthPx = with(density) { 9.dp.toPx() }
                    val availablePx = assocAvail - regularWidthPx - dividerWidthPx
                    fitCandidatesToWidth(
                        candidates = s.associationCandidates,
                        comments = emptyList(),
                        showComments = false,
                        availablePx = availablePx,
                        itemPaddingPx = itemPaddingPx,
                        spacingPx = spacingPx,
                        measureText = measureCandidateWidth,
                        candidateTextSize = candidateTextSize.toFloat(),
                    ).texts
                }
            }
        }
        is CandidateBarState.AssociationOnly -> {
            // @ 邮箱域名等纯联想：完整列表可左滑，不走「更多」
            displayCandidates = emptyList()
            displayAssociation = s.candidates
            hasAnyMore = false
            showLeftIcon = false
            displayComments = s.comments
        }
        is CandidateBarState.EnglishCandidates -> {
            val fitted = remember(
                s.candidates, s.comments, showComments, lazyRowWidthPx, fitSafetyPx,
                candidateTextSize, itemPaddingPx, spacingPx, measureCandidateWidth,
            ) {
                fitCandidatesToWidth(
                    candidates = s.candidates,
                    comments = s.comments,
                    showComments = showComments,
                    availablePx = (lazyRowWidthPx - fitSafetyPx).coerceAtLeast(0f),
                    itemPaddingPx = itemPaddingPx,
                    spacingPx = spacingPx,
                    measureText = measureCandidateWidth,
                    candidateTextSize = candidateTextSize.toFloat(),
                )
            }
            displayCandidates = fitted.texts
            displayComments = fitted.comments
            displayAssociation = emptyList()
            hasAnyMore = fitted.texts.size < s.candidates.size
            showLeftIcon = false
        }
        is CandidateBarState.ClipboardDisplay -> {
            displayCandidates = s.candidates.take(20)
            displayComments = emptyList()
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = true
        }
        is CandidateBarState.Calculator -> {
            displayCandidates = s.candidates.take(20)
            displayComments = s.comments
            displayAssociation = emptyList()
            hasAnyMore = false
            showLeftIcon = false
        }
    }
    showInputTextRow = when (page) {
        is KeyboardPage.Overlay -> page.route !is OverlayRoute.Clipboard &&
            page.route !is OverlayRoute.Bitwarden
        else -> true
    }

    // 同步栏上实际条数，供「更多」页 drop 已展示项
    SideEffect {
        val visible = when (state) {
            is CandidateBarState.AssociationOnly -> displayAssociation.size
            else -> displayCandidates.size
        }
        callbacks.onBarVisibleCandidateCount?.invoke(visible)
    }

    val candidateListState = rememberLazyListState()
    // 纯联想可横滑；打字候选禁止横滑（溢出走翻页）
    val allowCandidateSwipe = state is CandidateBarState.AssociationOnly ||
        (displayCandidates.isEmpty() && displayAssociation.isNotEmpty())
    LaunchedEffect(displayCandidates, displayAssociation) {
        candidateListState.scrollToItem(0)
    }

    // 编码气泡：候选栏内计算编码文本后回写此状态，供 Column 的 drawBehind 读取绘制。
    // drawBehind 在下一帧读取最新值，无需同步；初始值取自当前 state 保证首帧即显示。
    var preeditBubbleText by remember(state) {
        mutableStateOf((state as? CandidateBarState.ChineseCandidates)?.preeditText
            ?: (state as? CandidateBarState.ChineseCandidates)?.inputText ?: "")
    }
    val showPreeditBubble = showInputTextRow && preeditBubbleText.isNotEmpty() && !showInputBoxStyle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .drawPreeditBubble(
                text = preeditBubbleText,
                enabled = showPreeditBubble,
                bubbleColor = visuals.backgroundColor,
                textColor = visuals.textColor
            )
            .background(visuals.backgroundColor)
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.Center
    ) {
        if (isVoiceSticky) {
            // 常驻语音模式：候选栏显示语音引擎名 + 频谱
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (voicePluginName.isNotEmpty()) {
                    Text(
                        text = voicePluginName,
                        color = visuals.textColor.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AudioSpectrumAnimation(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 2.dp),
                        isActive = voiceRecognitionState == RecognitionState.LISTENING ||
                            voiceRecognitionState == RecognitionState.PROCESSING,
                        amplitude = voiceAmplitude,
                        spectrum = voiceSpectrum,
                        barWidthFactor = 4f,
                        barCount = 16,
                        spacingRatio = 1.6f,
                        heightScale = 0.6f
                    )
                }
            }
            return@Column
        }

        val displayText = (state as? CandidateBarState.ChineseCandidates)?.preeditText
            ?: (state as? CandidateBarState.ChineseCandidates)?.inputText ?: ""
        // 编码显示已改为候选栏顶部的悬浮气泡（drawBehind 绘制，见 drawPreeditBubble），
        // 栏内不再为编码保留布局空间——打字态与联想态的候选行共用同一垂直位置。
        preeditBubbleText = displayText

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLeftIcon) {
                when (state) {
                    is CandidateBarState.Idle -> {
                        if (page is KeyboardPage.Overlay && page.route is OverlayRoute.SchemaList && callbacks.onBack != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(iconButtonContainer)
                                    .clickable { callbacks.onBack() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "返回菜单",
                                    tint = visuals.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(iconButtonContainer)
                                    .clickable { callbacks.onLogoClick?.invoke() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = if (visuals.isDarkTheme) R.drawable.logo_dark else R.drawable.logo),
                                    contentDescription = "曦码 Logo",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    is CandidateBarState.ClipboardDisplay -> {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "剪切板",
                                tint = visuals.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            if (inlineSuggestions.isNotEmpty()) {
                inlineSuggestions.forEachIndexed { index, suggestion ->
                    InlineSuggestionView(
                        suggestion = suggestion,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(180.dp),
                    )
                    if (index < inlineSuggestions.lastIndex) {
                        InlineSuggestionDivider(color = visuals.dividerColor)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp)
                        .background(visuals.dividerColor),
                )
            }

            LazyRow(
                modifier = (if (state is CandidateBarState.Idle) Modifier else Modifier.weight(1f))
                    .onSizeChanged { size ->
                        if (size.width > 0 && measuredRowWidthPx != size.width.toFloat()) {
                            measuredRowWidthPx = size.width.toFloat()
                        }
                    },
                state = candidateListState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = allowCandidateSwipe,
            ) {
                itemsIndexed(displayCandidates, key = { index, _ -> index }) { index, candidate ->
                    CandidateItem(
                        text = candidate,
                        index = index,
                        onClick = { callbacks.onCandidateSelect(index) },
                        onLongClick = if (callbacks.onCandidateLongPress != null) {
                            { callbacks.onCandidateLongPress(index) }
                        } else null,
                        textColor = visuals.textColor,
                        comment = if (showComments) displayComments.getOrElse(index) { "" } else "",
                        isSelected = index == 0,
                        accentColor = visuals.accentColor,
                        selectedTextColor = visuals.selectedTextColor,
                        fontSize = candidateTextSize.sp,
                        candidateFontFamily = candidateFontFamily,
                        commentFontFamily = commentFontFamily
                    )
                }

                // 仅当左侧存在打字候选时才需要分隔线；纯联想态（无打字候选）下
                // 该竖线会孤悬列表最左缘，属多余元素。
                // 注意：分隔线在条件内，联想词 items 必须在条件外——纯联想态
                // displayCandidates 为空，若一并包进条件会导致联想词整个不渲染。
                if (displayCandidates.isNotEmpty() && displayAssociation.isNotEmpty()) {
                    item(key = "divider") {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(visuals.dividerColor.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                itemsIndexed(displayAssociation, key = { index, _ -> "assoc-$index" }) { index, candidate ->
                    val assocState = state as? CandidateBarState.AssociationOnly
                    CandidateItem(
                        text = candidate,
                        index = -1,
                        onClick = { callbacks.onAssociationSelect?.invoke(index) },
                        textColor = visuals.textColor,
                        comment = displayComments.getOrElse(index) { "" },
                        isSelected = assocState?.highlightIndex == index,
                        accentColor = visuals.accentColor,
                        selectedTextColor = visuals.selectedTextColor,
                        fontSize = candidateTextSize.sp,
                        candidateFontFamily = candidateFontFamily,
                        commentFontFamily = commentFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                state is CandidateBarState.Idle -> {
                    val toolbarAlignment = SettingsPreferences.getToolbarAlignment(context)
                    // 自适应居中：优先收紧图标间距让全部按钮完整可见并居中；
                    // 仍放不下才横向滚动，并按对齐方式给出初始偏移（居中≈对半），不再记忆滚动位置。
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth()
                    ) {
                        val buttonCount = toolbarActions.size
                        val buttonSize = 32.dp
                        val defaultHPad = 5.dp
                        val minHPad = 0.dp
                        val hPad: Dp = remember(buttonCount, maxWidth) {
                            if (buttonCount <= 0) return@remember defaultHPad
                            val atDefault = buttonSize * buttonCount + defaultHPad * 2 * buttonCount
                            if (atDefault <= maxWidth) {
                                defaultHPad
                            } else {
                                val remaining = maxWidth - buttonSize * buttonCount
                                if (remaining <= 0.dp) minHPad
                                else (remaining / (2f * buttonCount)).coerceIn(minHPad, defaultHPad)
                            }
                        }
                        val contentWidth = buttonSize * buttonCount + hPad * 2 * buttonCount
                        val needsScroll = buttonCount > 0 && contentWidth > maxWidth

                        LaunchedEffect(needsScroll, toolbarAlignment, buttonCount, maxWidth, hPad) {
                            if (!needsScroll) {
                                if (toolbarScrollState.value != 0) toolbarScrollState.scrollTo(0)
                                return@LaunchedEffect
                            }
                            // 等出可滚动范围后再定位；最多等几帧，避免 maxValue 一直为 0 时挂死
                            var maxScroll = toolbarScrollState.maxValue
                            var frames = 0
                            while (maxScroll <= 0 && frames < 8) {
                                withFrameNanos { }
                                maxScroll = toolbarScrollState.maxValue
                                frames++
                            }
                            if (maxScroll <= 0) return@LaunchedEffect
                            val pitchPx = with(density) { (buttonSize + hPad * 2).roundToPx().coerceAtLeast(1) }
                            val target = when (toolbarAlignment) {
                                SettingsPreferences.TOOLBAR_ALIGN_END -> maxScroll
                                SettingsPreferences.TOOLBAR_ALIGN_CENTER -> {
                                    val raw = maxScroll / 2f
                                    // 对齐到按钮节距，避免左右边缘各切半个图标
                                    ((raw / pitchPx).roundToInt() * pitchPx).coerceIn(0, maxScroll)
                                }
                                else -> 0
                            }
                            if (toolbarScrollState.value != target) {
                                toolbarScrollState.scrollTo(target)
                            }
                        }

                        val arrangement = when {
                            needsScroll -> Arrangement.Start
                            toolbarAlignment == SettingsPreferences.TOOLBAR_ALIGN_START -> Arrangement.Start
                            toolbarAlignment == SettingsPreferences.TOOLBAR_ALIGN_END -> Arrangement.End
                            else -> Arrangement.Center
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = arrangement,
                        ) {
                            Row(
                                modifier = if (needsScroll) {
                                    Modifier.horizontalScroll(toolbarScrollState)
                                } else {
                                    Modifier
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (toolbarActions.isNotEmpty()) {
                                    toolbarActions.forEach { action ->
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = hPad)
                                                .size(buttonSize)
                                                .combinedClickable(
                                                    interactionSource = interactionSource,
                                                    indication = null,
                                                    onClick = action.onClick,
                                                    onLongClick = action.onLongClick,
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ToolbarButtonIcon(
                                                item = action.item,
                                                tint = when {
                                                    action.isActive -> visuals.accentColor
                                                    isPressed -> iconButtonTint.copy(alpha = 0.6f)
                                                    else -> iconButtonTint
                                                },
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (callbacks.onHideKeyboard != null) {
                        val hideKeyboardInteractionSource = remember { MutableInteractionSource() }
                        val isHideKeyboardPressed by hideKeyboardInteractionSource.collectIsPressedAsState()

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = hideKeyboardInteractionSource,
                                    indication = null,
                                    onClick = { callbacks.onHideKeyboard() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "收起键盘",
                                tint = if (isHideKeyboardPressed) iconButtonTint.copy(alpha = 0.6f) else iconButtonTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                displayAssociation.isNotEmpty() && callbacks.onClearAssociation != null -> {
                    val clearInteractionSource = remember { MutableInteractionSource() }
                    val isClearPressed by clearInteractionSource.collectIsPressedAsState()

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(visuals.dividerColor).padding(end = 1.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isClearPressed) (if (visuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
                                    alpha = 0.1f
                                ))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = clearInteractionSource,
                                indication = null,
                                onClick = { callbacks.onClearAssociation() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "清空",
                            color = if (isClearPressed) visuals.textColor.copy(alpha = 0.6f) else visuals.textColor,
                            fontSize = 11.sp
                        )
                    }
                }
                hasAnyMore && callbacks.onShowMoreCandidates != null ||
                    (page is KeyboardPage.Overlay && page.route is OverlayRoute.CandidatePage &&
                        callbacks.onBack != null) -> {
                    val moreInteractionSource = remember { MutableInteractionSource() }
                    val isMorePressed by moreInteractionSource.collectIsPressedAsState()
                    val isExpanded =
                        page is KeyboardPage.Overlay && page.route is OverlayRoute.CandidatePage

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isMorePressed) (if (visuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
                                    alpha = 0.1f
                                ))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = moreInteractionSource,
                                indication = null,
                                onClick = {
                                    if (isExpanded) callbacks.onBack?.invoke()
                                    else callbacks.onShowMoreCandidates?.invoke()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isExpanded) "收起" else "更多",
                            color = if (isMorePressed) visuals.textColor.copy(alpha = 0.6f) else visuals.textColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CandidateItem(
    text: String,
    index: Int,
    onClick: () -> Unit,
    textColor: Color,
    comment: String = "",
    isSelected: Boolean = false,
    accentColor: Color = Color(0xFF1A73E8),
    selectedTextColor: Color = Color(0xFF1A73E8),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
    candidateFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    commentFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = if (isSelected) selectedTextColor else textColor,
            fontSize = fontSize,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontFamily = candidateFontFamily
        )
        if (comment.isNotEmpty()) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = comment,
                color = if (isSelected) selectedTextColor.copy(alpha = 0.6f) else textColor.copy(alpha = 0.5f),
                fontSize = (fontSize.value * 11f / 19f).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                fontFamily = commentFontFamily
            )
        }
    }
}

/**
 * 编码悬浮气泡：在候选栏顶部之上（栏外）绘制一个圆角胶囊气泡显示当前拼音编码。
 *
 * 参考 SwipeBubble 的锚定方式，但为纯绘制实现：
 * - 锚定宿主（候选栏 Column）左上角，气泡体向上悬浮于栏外空间；
 * - drawBehind 绘制不参与布局、不拦截触摸事件——候选栏上方的快捷发送表单/
 *   手写区等 UI 不受任何布局影响；
 * - IME 窗口为 MATCH_PARENT 全屏（onConfigureWindow），栏外绘制不会被窗口裁剪。
 *
 * 视觉：浅色模式近白底/深色模式深灰底的圆角胶囊（92% 不透明），无边框无阴影；
 * 编码文字在气泡内垂直居中；气泡与候选栏顶部之间留 2dp 间隙；宽度自适应，
 * 超出宿主右缘时左移钳制。
 *
 * 注意：不能使用传入的主题背景色——CandidateBarVisuals.backgroundColor 为
 * Color.Transparent（真实背景由外层绘制），以其合成会导致气泡无底色、
 * 文字与 app 内容混叠不可读。此处以候选文字亮度推断深浅模式取对比底色。
 */
private fun Modifier.drawPreeditBubble(
    text: String,
    enabled: Boolean,
    bubbleColor: Color,
    textColor: Color
): Modifier = composed {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 4.dp.toPx() }
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }
    val verticalPaddingPx = with(density) { 3.dp.toPx() }
    val bubbleBottomGapPx = with(density) { 2.dp.toPx() }
    val screenMarginPx = with(density) { 4.dp.toPx() }
    val textSizePx = with(density) { 12.sp.toPx() }

    // 气泡基色：优先用传入的主题背景色；其为全透明（CandidateBarVisuals 传
    // Color.Transparent，真实背景由外层绘制）时按候选文字亮度推导，
    // 保证浅色模式近白/深色模式深灰的可读对比。
    val isDarkTheme = textColor.luminance() > 0.5f
    val bubbleBaseColor = if (bubbleColor.alpha > 0.01f) {
        bubbleColor
    } else {
        if (isDarkTheme) Color(0xFF2D2F31) else Color(0xFFFAFAFA)
    }
    // 半透明：62% 不透明度
    val bubbleBgColor = bubbleBaseColor.copy(alpha = 0.62f)

    // 文本画笔：与 SwipeBubble 同款 nativeCanvas 绘制方式
    val bubbleTextPaint = remember(textColor, textSizePx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            color = textColor.copy(alpha = 0.9f).toArgb()
        }
    }

    drawBehind {
        if (!enabled || text.isEmpty()) return@drawBehind

        val fontMetrics = bubbleTextPaint.fontMetrics
        val textWidth = bubbleTextPaint.measureText(text)
        // 文本实际渲染高度以可见字形区间（ascent..descent）计，避免 lineHeight 参与导致偏移
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val bubbleWidth = textWidth + horizontalPaddingPx * 2
        val bubbleHeight = textHeight + verticalPaddingPx * 2

        // 气泡贴候选栏左缘，右向延伸；超出宿主右缘时整体左移钳制。
        val clampedLeft = maxOf(
            screenMarginPx,
            minOf(0f, size.width - bubbleWidth - screenMarginPx).coerceAtLeast(screenMarginPx / 4f)
        )
        // 底部间隙：气泡底缘距候选栏顶缘 1dp
        val top = -bubbleBottomGapPx - bubbleHeight

        // 气泡主体
        drawRoundRect(
            color = bubbleBgColor,
            topLeft = Offset(clampedLeft, top),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(cornerRadiusPx)
        )

        // 文本垂直居中：基线 = 气泡顶 + (气泡高 - (ascent + descent)) / 2，
        // ascent/descent 均为负/正相对基线的偏移，该式把字形区中点对准气泡中点。
        drawIntoCanvas { composeCanvas ->
            val baselineY = top + (bubbleHeight - (fontMetrics.ascent + fontMetrics.descent)) / 2f
            composeCanvas.nativeCanvas.drawText(text, clampedLeft + horizontalPaddingPx, baselineY, bubbleTextPaint)
        }
    }
}

private data class FittedCandidates(
    val texts: List<String>,
    val comments: List<String>,
)

/** 按可用宽度装入完整候选（含词注），装不下的留给「更多」。至少保留第 1 个。 */
private fun fitCandidatesToWidth(
    candidates: List<String>,
    comments: List<String>,
    showComments: Boolean,
    availablePx: Float,
    itemPaddingPx: Float,
    spacingPx: Float,
    measureText: (String, Float) -> Float,
    candidateTextSize: Float,
): FittedCandidates {
    if (candidates.isEmpty() || availablePx <= 0f) {
        return FittedCandidates(emptyList(), emptyList())
    }
    val commentSize = candidateTextSize * 11f / 19f
    val commentGapPx = itemPaddingPx * 0.375f // ≈ 3.dp when itemPadding=8.dp
    var usedPx = 0f
    val texts = mutableListOf<String>()
    val fittedComments = mutableListOf<String>()
    for (i in candidates.indices) {
        val text = candidates[i]
        val comment = comments.getOrElse(i) { "" }
        var width = measureText(text, candidateTextSize) + itemPaddingPx
        if (showComments && comment.isNotEmpty()) {
            width += commentGapPx + measureText(comment, commentSize)
        }
        if (texts.isNotEmpty()) width += spacingPx
        // 非首词：装不下就不放，避免最右侧半截字
        if (texts.isNotEmpty() && usedPx + width > availablePx + 0.5f) break
        usedPx += width
        texts.add(text)
        fittedComments.add(comment)
        // 首词已超宽：只留这一个，其余进更多
        if (i == 0 && usedPx > availablePx + 0.5f) break
    }
    return FittedCandidates(texts, fittedComments)
}

