package com.kingzcheung.xime.ui.keyboard

import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.bitwarden.BitwardenIcons
import com.kingzcheung.xime.bitwarden.BitwardenTotp
import com.kingzcheung.xime.bitwarden.BitwardenUiState
import com.kingzcheung.xime.bitwarden.BitwardenVaultRepository
import com.kingzcheung.xime.bitwarden.VaultLoginItem
import com.kingzcheung.xime.service.BitwardenSearchEditTextHolder
import kotlinx.coroutines.flow.distinctUntilChanged

/** 搜索 / 详情 / 编辑共用高度，与 XimeInputMethodService 撑高一致。 */
internal const val BITWARDEN_PANEL_HEIGHT = 280

@Composable
fun BitwardenSearchPanel(
    repository: BitwardenVaultRepository,
    packageName: String?,
    isFocused: Boolean,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onFocusChange: (Boolean) -> Unit,
    onFillUsername: (String) -> Unit,
    onFillPassword: (String) -> Unit,
    onFillTotp: (String) -> Unit,
    onOpenDetail: (VaultLoginItem) -> Unit,
    onAdd: () -> Unit,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()

    LaunchedEffect(packageName) {
        repository.refreshHost(packageName)
    }

    DisposableEffect(Unit) {
        onDispose { BitwardenSearchEditTextHolder.editText = null }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(BITWARDEN_PANEL_HEIGHT.dp)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBgColor.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            when (val s = state) {
                is BitwardenUiState.Unlocked -> {
                    val query = s.query
                    val pinnedIds = s.pinnedIds
                    val items = remember(s.items, s.host.packageName, s.host.appLabel, query, pinnedIds) {
                        s.items
                            .filter { it.matchesQuery(query) }
                            .sortedWith(
                                compareByDescending<VaultLoginItem> {
                                    if (it.id in pinnedIds || it.favorite) 1 else 0
                                }.thenByDescending { it.hostMatchScore(s.host) },
                            )
                    }
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = repository.listScrollIndex,
                        initialFirstVisibleItemScrollOffset = repository.listScrollOffset,
                    )
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }
                            .distinctUntilChanged()
                            .collect { (index, offset) ->
                                repository.setListScroll(index, offset)
                            }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = BitwardenIcons.Shield,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AndroidView(
                                factory = { context ->
                                    android.widget.EditText(context).apply {
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        setTextColor(textColor.hashCode())
                                        setHintTextColor((textColor.copy(alpha = 0.4f)).hashCode())
                                        hint = "搜索名称 / 账号 / 备注"
                                        textSize = 15f
                                        isSingleLine = true
                                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                                        setPadding(4, 2, 4, 2)
                                        imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
                                            EditorInfo.IME_ACTION_NONE
                                        val initial = (
                                            repository.state.value as? BitwardenUiState.Unlocked
                                            )?.query.orEmpty()
                                        setText(initial)
                                        setSelection(initial.length)
                                        onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                                            onFocusChange(hasFocus)
                                        }
                                        setOnClickListener { onFocusChange(true) }
                                        addTextChangedListener(SimpleTextWatcher { text ->
                                            repository.setQuery(text)
                                        })
                                        BitwardenSearchEditTextHolder.editText = this
                                        if (isFocused) post { requestFocus() }
                                    }
                                },
                                update = { et ->
                                    if (isFocused && !et.hasFocus()) et.post { et.requestFocus() }
                                    val want = s.query
                                    if (et.text?.toString() != want) {
                                        et.setText(want)
                                        et.setSelection(want.length)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                            )
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "添加",
                                tint = accentColor,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable(onClick = onAdd)
                                    .padding(4.dp),
                            )
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = "同步",
                                tint = if (s.loading) accentColor.copy(alpha = 0.4f) else accentColor,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable(enabled = !s.loading, onClick = onSync)
                                    .padding(4.dp),
                            )
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "设置",
                                tint = accentColor,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable(onClick = onOpenSettings)
                                    .padding(4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (items.isEmpty()) {
                            Text(
                                text = if (query.isBlank()) "无条目，点 + 添加" else "无匹配「$query」",
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(items, key = { it.id }) { item ->
                                    SearchResultRow(
                                        item = item,
                                        // 仅精确包名高亮；模糊匹配只参与置顶排序
                                        matchedApp = item.matchesPackage(s.host.packageName),
                                        textColor = textColor,
                                        accentColor = accentColor,
                                        cardBgColor = cardBgColor,
                                        onOpenDetail = { onOpenDetail(item) },
                                        onFillUsername = { onFillUsername(item.username) },
                                        onFillPassword = { onFillPassword(item.password) },
                                        onFillTotp = {
                                            val code = item.totp?.let { BitwardenTotp.code(it) }
                                            if (!code.isNullOrBlank()) onFillTotp(code)
                                        },
                                        pinned = item.id in pinnedIds || item.favorite,
                                        onTogglePin = { repository.togglePinned(item.id) },
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("保险库未解锁", color = textColor, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "请到设置 → Bitwarden 输入主密码解锁后再搜索。",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                        TextButton(onClick = onOpenSettings) {
                            Text("前往设置", color = accentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: VaultLoginItem,
    matchedApp: Boolean,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onOpenDetail: () -> Unit,
    onFillUsername: () -> Unit,
    onFillPassword: () -> Unit,
    onFillTotp: () -> Unit,
    pinned: Boolean,
    onTogglePin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (matchedApp) accentColor.copy(alpha = 0.18f)
                else cardBgColor.copy(alpha = 0.9f),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaultItemIcon(
            item = item,
            tint = textColor,
            size = 36.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenDetail),
        ) {
            Text(
                text = item.name.ifBlank { "(未命名)" },
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.username.isNotBlank()) {
                Text(
                    text = item.username,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionIcon(
                icon = Icons.Outlined.Person,
                contentDescription = "填账号",
                accentColor = accentColor,
                enabled = item.username.isNotBlank(),
                onClick = onFillUsername,
            )
            ActionIcon(
                icon = Icons.Outlined.Key,
                contentDescription = "填密码",
                accentColor = accentColor,
                enabled = item.password.isNotBlank(),
                onClick = onFillPassword,
            )
            if (!item.totp.isNullOrBlank()) {
                ActionIcon(
                    icon = Icons.Outlined.Timer,
                    contentDescription = "填验证码",
                    accentColor = accentColor,
                    onClick = onFillTotp,
                )
            }
            // 置顶：未置顶淡色描边感；已置顶实心 accent 底 + 反色图标，对比更强
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (pinned) accentColor
                        else accentColor.copy(alpha = 0.12f),
                    )
                    .clickable(onClick = onTogglePin),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = if (pinned) "取消置顶" else "置顶",
                    tint = if (pinned) Color.White else accentColor.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    accentColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = if (enabled) 0.22f else 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) accentColor else accentColor.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private class SimpleTextWatcher(
    private val onChanged: (String) -> Unit,
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {
        onChanged(s?.toString().orEmpty())
    }
}
