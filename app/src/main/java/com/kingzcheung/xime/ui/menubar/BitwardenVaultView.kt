package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.bitwarden.BitwardenUiState
import com.kingzcheung.xime.bitwarden.BitwardenVaultRepository
import com.kingzcheung.xime.bitwarden.VaultLoginItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BitwardenVaultView(
    repository: BitwardenVaultRepository,
    packageName: String?,
    backgroundColor: Color,
    keyTextColor: Color,
    keyBgColor: Color,
    onCommitText: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onRequestAddForm: (() -> Unit)? = null,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val sub = keyTextColor.copy(alpha = 0.65f)

    LaunchedEffect(packageName) {
        repository.refreshHost(packageName)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(bottom = bottomPaddingDp.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                when (state) {
                    is BitwardenUiState.Editing -> repository.cancelEditing()
                    else -> onBack()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = keyTextColor)
            }
            Icon(
                imageVector = com.kingzcheung.xime.bitwarden.BitwardenIcons.Shield,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "密码库",
                    color = keyTextColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                val hostLabel = when (val s = state) {
                    is BitwardenUiState.Unlocked -> s.host.appLabel.ifBlank { s.host.packageName }
                    is BitwardenUiState.Editing -> s.host.appLabel.ifBlank { s.host.packageName }
                    else -> packageName.orEmpty()
                }
                if (hostLabel.isNotBlank()) {
                    Text(
                        text = hostLabel,
                        color = sub,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state is BitwardenUiState.Unlocked) {
                IconButton(onClick = {
                    scope.launch {
                        val result = repository.syncNow()
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                if (result.isSuccess) "同步成功"
                                else result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                                    ?: "同步失败",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }) {
                    Icon(Icons.TwoTone.Sync, contentDescription = "同步", tint = keyTextColor)
                }
                IconButton(onClick = {
                    if (onRequestAddForm != null) onRequestAddForm()
                    else repository.beginCreate(packageName)
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建", tint = keyTextColor)
                }
                IconButton(onClick = { repository.lock() }) {
                    Icon(Icons.Filled.Lock, contentDescription = "锁定", tint = keyTextColor)
                }
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = keyTextColor)
            }
        }
        HorizontalDivider(color = keyTextColor.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .fillMaxHeight()
        ) {
            when (val s = state) {
                is BitwardenUiState.NotConfigured -> GoSettingsHint(
                    title = "尚未配置 Bitwarden",
                    detail = "请到设置 → Bitwarden 密码库填写服务器、邮箱，并用系统键盘输入主密码解锁。",
                    keyTextColor = keyTextColor,
                    sub = sub,
                    accent = accent,
                    onOpenSettings = onOpenSettings,
                )
                is BitwardenUiState.Locked,
                is BitwardenUiState.Error,
                is BitwardenUiState.NeedsTwoFactor -> GoSettingsHint(
                    title = "保险库未解锁",
                    detail = buildString {
                        append("主密码请在设置页输入（输入法面板里无法再用输入法打字）。")
                        val err = (s as? BitwardenUiState.Error)?.message
                        if (!err.isNullOrBlank()) {
                            append("\n\n上次错误：")
                            append(err)
                        }
                        if (s is BitwardenUiState.NeedsTwoFactor) {
                            append("\n\n需要两步验证码，请在设置页继续完成。")
                        }
                    },
                    keyTextColor = keyTextColor,
                    sub = sub,
                    accent = accent,
                    onOpenSettings = onOpenSettings,
                )
                is BitwardenUiState.Unlocked -> UnlockedList(
                    state = s,
                    keyTextColor = keyTextColor,
                    keyBgColor = keyBgColor,
                    sub = sub,
                    accent = accent,
                    onQuery = repository::setQuery,
                    onFilter = repository::setFilterCurrentApp,
                    onFillUsername = { onCommitText(it) },
                    onFillPassword = { onCommitText(it) },
                    onEdit = { repository.beginEdit(it, packageName) },
                )
                is BitwardenUiState.Editing -> EditForm(
                    state = s,
                    keyTextColor = keyTextColor,
                    keyBgColor = keyBgColor,
                    sub = sub,
                    accent = accent,
                    onChange = { name, user, pass, webUri, notes ->
                        repository.updateEditing(
                            name = name,
                            username = user,
                            password = pass,
                            webUri = webUri,
                            notes = notes,
                        )
                    },
                    onRegen = repository::regeneratePassword,
                    onSave = {
                        val isNew = s.existingId == null
                        scope.launch {
                            val ok = repository.saveEditing()
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    when {
                                        ok.isSuccess && isNew -> "已新增"
                                        ok.isSuccess -> "已保存"
                                        else -> ok.exceptionOrNull()?.message
                                            ?.takeIf { it.isNotBlank() } ?: "保存失败"
                                    },
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onCancel = repository::cancelEditing,
                )
                is BitwardenUiState.Viewing -> OverlayDetail(
                    item = s.item,
                    keyTextColor = keyTextColor,
                    sub = sub,
                    accent = accent,
                    onFillUsername = { onCommitText(it) },
                    onFillPassword = { onCommitText(it) },
                    onEdit = { repository.beginEditFromViewing() },
                    onBack = repository::cancelViewing,
                )
            }
        }
    }
}

@Composable
private fun OverlayDetail(
    item: VaultLoginItem,
    keyTextColor: Color,
    sub: Color,
    accent: Color,
    onFillUsername: (String) -> Unit,
    onFillPassword: (String) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(item.name.ifBlank { "(未命名)" }, color = keyTextColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("用户名：${item.username.ifBlank { "—" }}", color = sub, fontSize = 13.sp)
        Text("密码：${if (item.password.isBlank()) "—" else item.password}", color = sub, fontSize = 13.sp)
        if (item.uris.isNotEmpty()) {
            Text("URI：${item.uris.joinToString()}", color = sub, fontSize = 12.sp)
        }
        if (!item.totp.isNullOrBlank()) {
            Text("验证器：已配置", color = sub, fontSize = 12.sp)
        }
        if (!item.notes.isNullOrBlank()) {
            Text("备注：${item.notes}", color = sub, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { if (item.username.isNotBlank()) onFillUsername(item.username) }) {
                Text("填账号", color = accent)
            }
            TextButton(onClick = { if (item.password.isNotBlank()) onFillPassword(item.password) }) {
                Text("填密码", color = accent)
            }
            TextButton(onClick = onEdit) {
                Text("编辑", color = accent)
            }
            TextButton(onClick = onBack) {
                Text("返回", color = sub)
            }
        }
    }
}

@Composable
private fun GoSettingsHint(
    title: String,
    detail: String,
    keyTextColor: Color,
    sub: Color,
    accent: Color,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, color = keyTextColor, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = detail, color = sub, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onOpenSettings) {
            Text(text = "前往设置解锁", color = accent)
        }
    }
}

@Composable
private fun UnlockedList(
    state: BitwardenUiState.Unlocked,
    keyTextColor: Color,
    keyBgColor: Color,
    sub: Color,
    accent: Color,
    onQuery: (String) -> Unit,
    onFilter: (Boolean) -> Unit,
    onFillUsername: (String) -> Unit,
    onFillPassword: (String) -> Unit,
    onEdit: (VaultLoginItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(keyBgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    textStyle = TextStyle(color = keyTextColor, fontSize = 14.sp),
                    cursorBrush = SolidColor(accent),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) {
                            Text(text = "搜索名称 / 用户名 / URI", color = sub, fontSize = 14.sp)
                        }
                        inner()
                    },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "当前应用", color = sub, fontSize = 11.sp)
            Switch(checked = state.filterCurrentApp, onCheckedChange = onFilter)
        }
        if (state.showingFallbackAll) {
            Text(
                text = "当前应用暂无关联条目，已显示全部。可用「+」新建并自动写入 androidapp://",
                color = sub,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (state.loading) {
            Text(
                text = "同步中…",
                color = sub,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (!state.error.isNullOrBlank()) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.visibleItems, key = { it.id }) { item ->
                VaultItemRow(
                    item = item,
                    keyTextColor = keyTextColor,
                    keyBgColor = keyBgColor,
                    sub = sub,
                    accent = accent,
                    onFillUsername = { if (item.username.isNotEmpty()) onFillUsername(item.username) },
                    onFillPassword = { if (item.password.isNotEmpty()) onFillPassword(item.password) },
                    onEdit = { onEdit(item) },
                )
            }
            if (!state.loading && state.visibleItems.isEmpty()) {
                item {
                    Text(
                        text = "没有匹配条目",
                        color = sub,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultItemRow(
    item: VaultLoginItem,
    keyTextColor: Color,
    keyBgColor: Color,
    sub: Color,
    accent: Color,
    onFillUsername: () -> Unit,
    onFillPassword: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(keyBgColor)
            .padding(12.dp)
    ) {
        Text(
            text = item.name,
            color = keyTextColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.username.isNotBlank()) {
            Text(
                text = item.username,
                color = sub,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val uriHint = item.uris.firstOrNull().orEmpty()
        if (uriHint.isNotBlank()) {
            Text(
                text = uriHint,
                color = sub.copy(alpha = 0.8f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallAction("用户名", accent, item.username.isNotBlank(), onFillUsername)
            SmallAction("密码", accent, item.password.isNotBlank(), onFillPassword)
            SmallAction("编辑", sub, true, onEdit)
        }
    }
}

@Composable
private fun SmallAction(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (enabled) color else color.copy(alpha = 0.35f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun EditForm(
    state: BitwardenUiState.Editing,
    keyTextColor: Color,
    keyBgColor: Color,
    sub: Color,
    accent: Color,
    onChange: (String?, String?, String?, String?, String?) -> Unit,
    onRegen: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (state.existingId == null) "新建登录" else "编辑登录",
            color = keyTextColor,
            fontWeight = FontWeight.SemiBold,
        )
        FieldBox(state.name, { onChange(it, null, null, null, null) }, "名称", keyTextColor, keyBgColor, sub, accent)
        FieldBox(state.username, { onChange(null, it, null, null, null) }, "用户名", keyTextColor, keyBgColor, sub, accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                FieldBox(state.password, { onChange(null, null, it, null, null) }, "密码", keyTextColor, keyBgColor, sub, accent)
            }
            IconButton(onClick = onRegen) {
                Icon(Icons.Filled.Refresh, contentDescription = "生成密码", tint = accent)
            }
        }
        FieldBox(state.webUri, { onChange(null, null, null, it, null) }, "网站（https://）", keyTextColor, keyBgColor, sub, accent)
        Text(
            text = if (state.appPackage.isBlank()) {
                "关联 App：未设置（可在键盘编辑页选择）"
            } else {
                "关联 App：${state.appPackage}"
            },
            color = sub,
            fontSize = 12.sp,
        )
        FieldBox(state.notes, { onChange(null, null, null, null, it) }, "备注", keyTextColor, keyBgColor, sub, accent)
        if (!state.error.isNullOrBlank()) {
            Text(text = state.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSave, enabled = !state.saving) {
                Text(text = if (state.saving) "保存中…" else "保存到 Bitwarden", color = accent)
            }
            TextButton(onClick = onCancel) {
                Text(text = "取消", color = sub)
            }
        }
    }
}

@Composable
private fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyTextColor: Color,
    keyBgColor: Color,
    sub: Color,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(keyBgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = keyTextColor, fontSize = 14.sp),
            cursorBrush = SolidColor(accent),
            singleLine = placeholder != "备注",
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, color = sub, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}
