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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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

/** 与搜索/详情共用 [BITWARDEN_PANEL_HEIGHT]（兼容旧引用）。 */
internal val BITWARDEN_EDIT_FORM_HEIGHT = BITWARDEN_PANEL_HEIGHT

/** 聚焦输入时挂在候选栏上方的「当前字段条」高度（类似搜索框一行）。 */
internal const val BITWARDEN_EDIT_FIELD_BAR_HEIGHT = 52

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
    /** 浏览编辑页时为 false：不自动抢焦点，避免一点开就跳进输入态。 */
    autoFocusFields: Boolean = false,
    initialScrollPx: Int = 0,
    onScrollSave: ((Int) -> Unit)? = null,
    /** 删除自定义字段前通知：参数为被删下标，便于钳制字段条聚焦。 */
    onCustomFieldRemoved: ((Int) -> Unit)? = null,
    /** App 选择器打开/关闭：打开时需切键盘并路由按键到搜索框。 */
    onAppPickerActiveChange: ((Boolean) -> Unit)? = null,
) {
    val state by repository.state.collectAsState()
    val editing = state as? BitwardenUiState.Editing
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState(initial = initialScrollPx.coerceAtLeast(0))
    val context = LocalContext.current
    var pickingApp by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var allApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onScrollSave?.invoke(scroll.value)
            // 切到字段条输入时 Form 会 dispose；此处 clear 会清掉字段条刚 bind 的 holder，导致无法输入。
            // holders 由字段条 onDispose / hideBitwardenEdit / release 负责。
            if (pickingApp) onAppPickerActiveChange?.invoke(false)
            BitwardenAppPickerEditTextHolder.editText = null
        }
    }

    LaunchedEffect(initialScrollPx) {
        if (initialScrollPx > 0 && scroll.value != initialScrollPx) {
            scroll.scrollTo(initialScrollPx)
        }
    }

    LaunchedEffect(pickingApp) {
        onAppPickerActiveChange?.invoke(pickingApp)
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
                                    val isNew = editing.existingId == null
                                    scope.launch {
                                        val ok = repository.saveEditing()
                                        withContext(Dispatchers.Main) {
                                            if (ok.isSuccess) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (isNew) "已新增" else "已保存",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                                onClose()
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    ok.exceptionOrNull()?.message
                                                        ?.takeIf { it.isNotBlank() }
                                                        ?: "保存失败",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
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
                        autoFocus = autoFocusFields,
                    )
                    EditLine(
                        hint = "用户名",
                        initial = editing.username,
                        textColor = textColor,
                        field = BitwardenEditField.USERNAME,
                        focusedField = focusedField,
                        onFieldFocus = onFieldFocus,
                        onChanged = { repository.updateEditing(username = it) },
                        autoFocus = autoFocusFields,
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
                            autoFocus = autoFocusFields,
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
                        autoFocus = autoFocusFields,
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
                        autoFocus = autoFocusFields,
                    )
                    EditLine(
                        hint = "备注",
                        initial = editing.notes,
                        textColor = textColor,
                        field = BitwardenEditField.NOTES,
                        focusedField = focusedField,
                        onFieldFocus = onFieldFocus,
                        onChanged = { repository.updateEditing(notes = it) },
                        autoFocus = autoFocusFields,
                    )

                    Text(
                        text = "自定义字段",
                        color = textColor.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                    editing.fields.forEachIndexed { index, field ->
                        key(index) {
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
                                    autoFocus = autoFocusFields,
                                )
                                Text(
                                    text = "删",
                                    color = Color(0xFFE53935),
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            // 删当前/之前索引时，通知上层钳制聚焦下标，避免字段条指到错位行
                                            onCustomFieldRemoved?.invoke(index)
                                            repository.removeCustomField(index)
                                        }
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
                                autoFocus = autoFocusFields,
                            )
                        }
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
    autoFocus: Boolean = false,
) {
    val isFocused = focusedField == field && (
        field != BitwardenEditField.CUSTOM ||
            (customIndex == activeCustomIndex && customIsName == activeCustomIsName)
        )
    val onChangedState = rememberUpdatedState(onChanged)
    val onFieldFocusState = rememberUpdatedState(onFieldFocus)
    val customIndexState = rememberUpdatedState(customIndex)
    val customIsNameState = rememberUpdatedState(customIsName)
    val fieldState = rememberUpdatedState(field)

    key(field, customIndex, customIsName) {
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
                    imeOptions = EditorInfo.IME_ACTION_NEXT
                    showSoftInputOnFocus = false
                    val suppress = booleanArrayOf(false)
                    tag = suppress
                    setText(initial)
                    setSelection(initial.length.coerceAtMost(text?.length ?: 0))
                    onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) {
                            onFieldFocusState.value(
                                fieldState.value,
                                customIndexState.value,
                                customIsNameState.value,
                            )
                        }
                    }
                    setOnClickListener {
                        onFieldFocusState.value(
                            fieldState.value,
                            customIndexState.value,
                            customIsNameState.value,
                        )
                    }
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            if (suppress[0]) return
                            onChangedState.value(s?.toString().orEmpty())
                        }
                    })
                    BitwardenEditFormHolders.bind(
                        fieldState.value,
                        this,
                        customIndexState.value,
                        customIsNameState.value,
                    )
                    if (autoFocus && isFocused) post { requestFocus() }
                }
            },
            update = { et ->
                if (et.text?.toString() != initial) {
                    val suppress = et.tag as? BooleanArray
                    suppress?.set(0, true)
                    try {
                        et.setText(initial)
                        et.setSelection(initial.length.coerceAtMost(et.text?.length ?: 0))
                    } finally {
                        suppress?.set(0, false)
                    }
                }
                BitwardenEditFormHolders.bind(
                    fieldState.value,
                    et,
                    customIndexState.value,
                    customIsNameState.value,
                )
                if (autoFocus && isFocused && !et.hasFocus()) et.post { et.requestFocus() }
            },
            modifier = modifier
                .fillMaxWidth()
                .height(36.dp),
        )
    }
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
                    showSoftInputOnFocus = false
                    isFocusable = true
                    isFocusableInTouchMode = true
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

/**
 * 编辑会话顶栏「当前字段条」。
 * [imeFocused]=true：抢焦点、按键写入本字段；false：展示当前值，点击 [onActivate] 重新进入输入态。
 * 右侧切换：编辑表单 ↔ 键盘（与搜索栏切换按钮同语义）；回车/完成清焦点退回完整编辑页。
 */
@Composable
fun BitwardenEditFieldBar(
    repository: BitwardenVaultRepository,
    field: BitwardenEditField,
    customIndex: Int,
    customIsName: Boolean,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    imeFocused: Boolean = true,
    /** 按键区是否为键盘（决定切换按钮图标）。 */
    keyAreaIsKeyboard: Boolean = false,
    onActivate: (() -> Unit)? = null,
    onToggleKeyArea: (() -> Unit)? = null,
) {
    val state by repository.state.collectAsState()
    val editing = state as? BitwardenUiState.Editing
    // 编辑表单态 → 键盘图标；字段输入/键盘态 → 列表图标回表单
    val showKeyboardIcon = !keyAreaIsKeyboard && !imeFocused

    DisposableEffect(field, customIndex, customIsName, imeFocused) {
        onDispose {
            if (imeFocused) {
                BitwardenEditFormHolders.bind(field, null, customIndex, customIsName)
            }
        }
    }

    val (label, initial) = remember(editing, field, customIndex, customIsName) {
        val e = editing
        when (field) {
            BitwardenEditField.NAME -> "名称" to (e?.name.orEmpty())
            BitwardenEditField.USERNAME -> "用户名" to (e?.username.orEmpty())
            BitwardenEditField.PASSWORD -> "密码" to (e?.password.orEmpty())
            BitwardenEditField.URI -> "网站" to (e?.webUri.orEmpty())
            BitwardenEditField.TOTP -> "TOTP" to (e?.totp.orEmpty())
            BitwardenEditField.NOTES -> "备注" to (e?.notes.orEmpty())
            BitwardenEditField.CUSTOM -> {
                val cf = e?.fields?.getOrNull(customIndex)
                if (customIsName) "字段名" to (cf?.name.orEmpty())
                else "字段值" to (cf?.value.orEmpty())
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(BITWARDEN_EDIT_FIELD_BAR_HEIGHT.dp)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(
                if (!imeFocused && onActivate != null) {
                    Modifier.clickable(onClick = onActivate)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBgColor.copy(alpha = 0.5f))
                .then(
                    if (imeFocused) Modifier.background(accentColor.copy(alpha = 0.12f))
                    else Modifier,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 8.dp),
            )
            // AndroidView 的 factory 只跑一次：切字段时必须重建，否则 TextWatcher 仍写旧字段（名称被污染）
            key(field, customIndex, customIsName) {
                AndroidView(
                    factory = { context ->
                        android.widget.EditText(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setTextColor(textColor.hashCode())
                            setHintTextColor((textColor.copy(alpha = 0.4f)).hashCode())
                            hint = label
                            textSize = 15f
                            isSingleLine = true
                            gravity = Gravity.CENTER_VERTICAL or Gravity.START
                            setPadding(2, 2, 2, 2)
                            imeOptions = EditorInfo.IME_ACTION_DONE
                            showSoftInputOnFocus = false
                            val suppress = booleanArrayOf(false)
                            tag = suppress
                            setText(initial)
                            setSelection(initial.length.coerceAtMost(text?.length ?: 0))
                            addTextChangedListener(object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    if (suppress[0]) return
                                    val text = s?.toString().orEmpty()
                                    when (field) {
                                        BitwardenEditField.NAME -> repository.updateEditing(name = text)
                                        BitwardenEditField.USERNAME -> repository.updateEditing(username = text)
                                        BitwardenEditField.PASSWORD -> repository.updateEditing(password = text)
                                        BitwardenEditField.URI -> repository.updateEditing(webUri = text)
                                        BitwardenEditField.TOTP -> repository.updateEditing(totp = text)
                                        BitwardenEditField.NOTES -> repository.updateEditing(notes = text)
                                        BitwardenEditField.CUSTOM -> {
                                            if (customIsName) repository.updateCustomField(customIndex, name = text)
                                            else repository.updateCustomField(customIndex, value = text)
                                        }
                                    }
                                }
                            })
                            setOnClickListener {
                                if (!imeFocused) onActivate?.invoke()
                            }
                            isFocusable = imeFocused
                            isFocusableInTouchMode = imeFocused
                            isCursorVisible = imeFocused
                            if (imeFocused) {
                                post {
                                    BitwardenEditFormHolders.bind(field, this, customIndex, customIsName)
                                    requestFocus()
                                }
                            }
                        }
                    },
                    update = { et ->
                        if (et.text?.toString() != initial) {
                            val suppress = et.tag as? BooleanArray
                            suppress?.set(0, true)
                            try {
                                et.setText(initial)
                                et.setSelection(initial.length.coerceAtMost(et.text?.length ?: 0))
                            } finally {
                                suppress?.set(0, false)
                            }
                        }
                        et.isFocusable = imeFocused
                        et.isFocusableInTouchMode = imeFocused
                        et.isCursorVisible = imeFocused
                        et.setOnClickListener {
                            if (!imeFocused) onActivate?.invoke()
                        }
                        if (imeFocused) {
                            BitwardenEditFormHolders.bind(field, et, customIndex, customIsName)
                            if (!et.hasFocus()) et.post { et.requestFocus() }
                        } else {
                            et.clearFocus()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(36.dp),
                )
            }
            Icon(
                imageVector = if (showKeyboardIcon) {
                    Icons.Outlined.Keyboard
                } else {
                    Icons.AutoMirrored.Outlined.ViewList
                },
                contentDescription = if (showKeyboardIcon) "切换到键盘" else "返回编辑页",
                tint = accentColor,
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        // 优先走与搜索栏同一套切换；无回调时字段输入态用 onDone 回表单
                        when {
                            onToggleKeyArea != null -> onToggleKeyArea()
                            else -> onDone()
                        }
                    }
                    .padding(4.dp),
            )
        }
    }
}
