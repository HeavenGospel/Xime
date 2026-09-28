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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kingzcheung.xime.bitwarden.BitwardenUiState
import com.kingzcheung.xime.bitwarden.BitwardenVaultRepository
import com.kingzcheung.xime.bitwarden.InstalledAppInfo
import com.kingzcheung.xime.bitwarden.InstalledApps
import com.kingzcheung.xime.service.BitwardenAppPickerEditTextHolder
import com.kingzcheung.xime.service.BitwardenEditField
import com.kingzcheung.xime.service.BitwardenEditFormHolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 与搜索/详情共用 [BITWARDEN_PANEL_HEIGHT]。 */
internal val BITWARDEN_EDIT_FORM_HEIGHT = BITWARDEN_PANEL_HEIGHT

@Composable
fun BitwardenEditFormArea(
    repository: BitwardenVaultRepository,
    focusedField: BitwardenEditField,
    customIndex: Int,
    customIsName: Boolean,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onClose: () -> Unit,
    onFieldFocus: (BitwardenEditField, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val editing = state as? BitwardenUiState.Editing
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val context = LocalContext.current
    var pickingApp by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            BitwardenEditFormHolders.clear()
            BitwardenAppPickerEditTextHolder.editText = null
        }
    }

    LaunchedEffect(pickingApp) {
        if (pickingApp && !appsLoaded) {
            allApps = withContext(Dispatchers.IO) {
                InstalledApps.loadLaunchable(context.packageManager)
            }
            appsLoaded = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(BITWARDEN_EDIT_FORM_HEIGHT.dp)
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
            if (editing == null) {
                Text("无编辑中的条目", color = textColor.copy(alpha = 0.6f), fontSize = 13.sp)
                return@Column
            }
            if (pickingApp) {
                AppPickerPanel(
                    apps = InstalledApps.filter(allApps, appQuery),
                    query = appQuery,
                    loading = !appsLoaded,
                    textColor = textColor,
                    accentColor = accentColor,
                    cardBgColor = cardBgColor,
                    onQueryChange = { appQuery = it },
                    onSelect = { app ->
                        repository.updateEditing(appPackage = app.packageName)
                        if (editing.name.isBlank() ||
                            editing.name == editing.host.appLabel ||
                            editing.name == editing.host.packageName
                        ) {
                            repository.updateEditing(name = app.label)
                        }
                        pickingApp = false
                        appQuery = ""
                    },
                    onClear = {
                        repository.clearEditingAppPackage()
                        pickingApp = false
                        appQuery = ""
                    },
                    onCancel = {
                        pickingApp = false
                        appQuery = ""
                    },
                )
                return@Column
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editing.existingId == null) "添加登录" else "编辑登录",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "取消",
                            color = textColor.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable(onClick = onClose)
                                .padding(vertical = 2.dp),
                        )
                        Text(
                            text = if (editing.saving) "保存中…" else "保存",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(enabled = !editing.saving) {
                                    scope.launch {
                                        val ok = repository.saveEditing()
                                        if (ok.isSuccess) onClose()
                                    }
                                }
                                .padding(vertical = 2.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                ) {
                    EditLine(
                        hint = "名称",
                        initial = editing.name,
                        textColor = textColor,
                        field = BitwardenEditField.NAME,
                        focusedField = focusedField,
                        onFieldFocus = onFieldFocus,
                        onChanged = { repository.updateEditing(name = it) },
                    )
                    EditLine(
                        hint = "用户名",
                        initial = editing.username,
                        textColor = textColor,
                        field = BitwardenEditField.USERNAME,
                        focusedField = focusedField,
                        onFieldFocus = onFieldFocus,
                        onChanged = { repository.updateEditing(username = it) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditLine(
                            hint = "密码",
                            initial = editing.password,
                            textColor = textColor,
                            field = BitwardenEditField.PASSWORD,
                            focusedField = focusedField,
                            onFieldFocus = onFieldFocus,
                            onChanged = { repository.updateEditing(password = it) },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "生成",
                            color = accentColor,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { repository.regeneratePassword() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    EditLine(
                        hint = "网站（https:// 或域名）",
                        initial = editing.webUri,
                        textColor = textColor,
                        field = BitwardenEditField.URI,
                        focusedField = focusedField,
                        onFieldFocus = onFieldFocus,
                        onChanged = { repository.updateEditing(webUri = it) },
                    )
                    AppLinkRow(
                        packageName = editing.appPackage,
                        textColor = textColor,
                        accentColor = accentColor,
                        cardBgColor = cardBgColor,
                        onPick = { pickingApp = true },
                        onClear = { repository.clearEditingAppPackage() },
                    )
                    EditLine(
                        hint = "验证器密钥 / otpauth（TOTP）",
                        initial = editing.totp,
                        textColor = textColor,
                        field = BitwardenEditField.TOTP,
                        focusedField = focusedField,
                        onFieldFocus = onFieldFocus,
                        onChanged = { repository.updateEditing(totp = it) },
                    )
                    EditLine(
                        hint = "备注",
                        initial = editing.notes,
                        textColor = textColor,
                        field = BitwardenEditField.NOTES,
                        focusedField = focusedField,
                        onFieldFocus = onFieldFocus,
                        onChanged = { repository.updateEditing(notes = it) },
                    )

                    Text(
                        text = "自定义字段",
                        color = textColor.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                    editing.fields.forEachIndexed { index, field ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EditLine(
                                hint = "字段名",
                                initial = field.name,
                                textColor = textColor,
                                field = BitwardenEditField.CUSTOM,
                                focusedField = focusedField,
                                customIndex = index,
                                customIsName = true,
                                activeCustomIndex = customIndex,
                                activeCustomIsName = customIsName,
                                onFieldFocus = onFieldFocus,
                                onChanged = { repository.updateCustomField(index, name = it) },
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "删",
                                color = Color(0xFFE53935),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { repository.removeCustomField(index) }
                                    .padding(horizontal = 6.dp),
                            )
                        }
                        EditLine(
                            hint = if (field.type == 1) "隐藏值" else "字段值",
                            initial = field.value,
                            textColor = textColor,
                            field = BitwardenEditField.CUSTOM,
                            focusedField = focusedField,
                            customIndex = index,
                            customIsName = false,
                            activeCustomIndex = customIndex,
                            activeCustomIsName = customIsName,
                            onFieldFocus = onFieldFocus,
                            onChanged = { repository.updateCustomField(index, value = it) },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "+ 文本字段",
                            color = accentColor,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { repository.addCustomField(0) },
                        )
                        Text(
                            text = "+ 隐藏字段",
                            color = accentColor,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { repository.addCustomField(1) },
                        )
                    }

                    if (!editing.error.isNullOrBlank()) {
                        Text(
                            text = editing.error,
                            color = Color(0xFFE53935),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(editing?.password) {
        val et = BitwardenEditFormHolders.password
        val pwd = editing?.password.orEmpty()
        if (et != null && et.text?.toString() != pwd) {
            et.setText(pwd)
            et.setSelection(pwd.length)
        }
    }
}

@Composable
private fun EditLine(
    hint: String,
    initial: String,
    textColor: Color,
    field: BitwardenEditField,
    focusedField: BitwardenEditField,
    onFieldFocus: (BitwardenEditField, Int, Boolean) -> Unit,
    onChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    customIndex: Int = 0,
    customIsName: Boolean = true,
    activeCustomIndex: Int = 0,
    activeCustomIsName: Boolean = true,
) {
    val isFocused = focusedField == field && (
        field != BitwardenEditField.CUSTOM ||
            (customIndex == activeCustomIndex && customIsName == activeCustomIsName)
        )
    AndroidView(
        factory = { context ->
            android.widget.EditText(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(textColor.hashCode())
                setHintTextColor((textColor.copy(alpha = 0.4f)).hashCode())
                this.hint = hint
                textSize = 14f
                isSingleLine = true
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(2, 4, 2, 4)
                setText(initial)
                setSelection(initial.length)
                imeOptions = EditorInfo.IME_ACTION_NEXT
                onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onFieldFocus(field, customIndex, customIsName)
                }
                setOnClickListener { onFieldFocus(field, customIndex, customIsName) }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        onChanged(s?.toString().orEmpty())
                    }
                })
                BitwardenEditFormHolders.bind(field, this, customIndex, customIsName)
                if (isFocused) post { requestFocus() }
            }
        },
        update = { et ->
            BitwardenEditFormHolders.bind(field, et, customIndex, customIsName)
            if (isFocused && !et.hasFocus()) et.post { et.requestFocus() }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
    )
}

@Composable
private fun AppLinkRow(
    packageName: String,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(packageName) {
        if (packageName.isBlank()) "" else {
            try {
                val ai = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(ai)?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "关联 App",
            color = textColor.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(cardBgColor.copy(alpha = 0.7f))
                .clickable(onClick = onPick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        packageName.isBlank() -> "未关联（点此选择）"
                        label.isNotBlank() -> label
                        else -> packageName
                    },
                    color = textColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (packageName.isNotBlank()) {
                    Text(
                        text = packageName,
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = if (packageName.isBlank()) "选择" else "更换",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (packageName.isNotBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "清除",
                    color = textColor.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onClear),
                )
            }
        }
    }
}

@Composable
private fun AppPickerPanel(
    apps: List<InstalledAppInfo>,
    query: String,
    loading: Boolean,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onQueryChange: (String) -> Unit,
    onSelect: (InstalledAppInfo) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "选择关联 App",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "清除关联",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onClear),
                )
                Text(
                    text = "返回",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onCancel),
                )
            }
        }
        AndroidView(
            factory = { ctx ->
                android.widget.EditText(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setTextColor(textColor.hashCode())
                    setHintTextColor((textColor.copy(alpha = 0.4f)).hashCode())
                    hint = "搜索应用名 / 包名"
                    textSize = 14f
                    isSingleLine = true
                    setPadding(2, 6, 2, 6)
                    setText(query)
                    setSelection(query.length)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            onQueryChange(s?.toString().orEmpty())
                        }
                    })
                    BitwardenAppPickerEditTextHolder.editText = this
                    post { requestFocus() }
                }
            },
            update = { et ->
                BitwardenAppPickerEditTextHolder.editText = et
                if (et.text?.toString() != query) {
                    et.setText(query)
                    et.setSelection(query.length)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )
        DisposableEffect(Unit) {
            onDispose { BitwardenAppPickerEditTextHolder.editText = null }
        }
        if (loading) {
            Text(
                text = "正在读取本机应用…",
                color = textColor.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (apps.isEmpty()) {
            Text(
                text = if (query.isBlank()) "未找到可启动应用" else "无匹配「$query」",
                color = textColor.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBgColor.copy(alpha = 0.75f))
                            .clickable { onSelect(app) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app.icon != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(app.icon)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.2f)),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                color = textColor,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.packageName,
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
