package com.kingzcheung.xime.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kingzcheung.xime.settings.KeyCustomizeStore
import com.kingzcheung.xime.settings.KeyGestureConfig
import com.kingzcheung.xime.settings.KeysConfigHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyCustomizeSettingsContent(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val configVersion by KeysConfigHelper.configVersion.collectAsState()

    var isAsciiMode by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var showImportModeDialog by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }

    val rows = remember(configVersion, isAsciiMode) {
        KeysConfigHelper.getKeyRows(isAsciiMode)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = KeysConfigHelper.exportKeyCustomizationsJson(context)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入文件")
            Toast.makeText(context, "已导出按键自定义", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("无法读取文件")
            pendingImportText = text
            showImportModeDialog = true
        } catch (e: Exception) {
            Toast.makeText(context, "读取失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun doImport(replace: Boolean) {
        val text = pendingImportText ?: return
        pendingImportText = null
        showImportModeDialog = false
        try {
            val result = KeysConfigHelper.importKeyCustomizationsJson(context, text, replace)
            Toast.makeText(
                context,
                "已导入（中文 ${result.zhCount} 键 / 英文 ${result.enCount} 键）",
                Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareExport() {
        try {
            val json = KeysConfigHelper.exportKeyCustomizationsJson(context)
            // files-path 已声明；cache 下仅 emoji_cache 可走 FileProvider
            val dir = File(context.filesDir, "share").also { it.mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "xime-keys-$stamp.json")
            file.writeText(json, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "曦码按键自定义")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享按键自定义"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("按键自定义") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = "编辑目标键盘") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !isAsciiMode,
                            onClick = { isAsciiMode = false },
                            label = { Text("中文全键") },
                        )
                        FilterChip(
                            selected = isAsciiMode,
                            onClick = { isAsciiMode = true },
                            label = { Text("英文全键") },
                        )
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "点按下方按键，可改键面字根、上滑/下滑提示与长按选项。字根只改显示，上屏编码仍是字母。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    @Suppress("UNUSED_EXPRESSION")
                    configVersion
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { key ->
                                val gesture = KeysConfigHelper.getKeyGesture(key, isAsciiMode)
                                val hasOverride = KeyCustomizeStore.getOverride(
                                    context,
                                    isAsciiMode,
                                    key,
                                ) != null
                                KeyPreviewChip(
                                    keyId = key,
                                    isAsciiMode = isAsciiMode,
                                    gesture = gesture,
                                    highlighted = hasOverride,
                                    modifier = Modifier.weight(1f),
                                    onClick = { editingKey = key },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "导入 / 导出") {
                    SettingsItem(
                        icon = Icons.TwoTone.FileDownload,
                        title = "导出为文件",
                        subtitle = "保存 JSON，可发给其他曦码用户",
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            exportLauncher.launch("xime-keys-$stamp.json")
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Share,
                        title = "分享",
                        subtitle = "通过微信 / 文件管理等发送",
                        onClick = { shareExport() },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.FileUpload,
                        title = "从文件导入",
                        subtitle = "导入他人分享的按键自定义 JSON",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                    )
                }
            }

            item {
                SettingsSection(title = "键面预设") {
                    KeyCustomizeStore.KEY_FACE_PRESETS.forEachIndexed { index, preset ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        SettingsItem(
                            icon = Icons.TwoTone.TextFields,
                            title = preset.title,
                            subtitle = preset.subtitle,
                            onClick = {
                                KeysConfigHelper.applyKeyFacePreset(
                                    context,
                                    isAsciiMode,
                                    preset.id,
                                )
                            },
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "重置") {
                    SettingsItem(
                        icon = Icons.TwoTone.History,
                        title = "恢复当前键盘默认",
                        subtitle = "清除本页对${if (isAsciiMode) "英文" else "中文"}全键的全部覆盖",
                        onClick = {
                            KeysConfigHelper.clearAllKeyCustomizations(context, isAsciiMode)
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    val key = editingKey
    if (key != null) {
        KeyEditDialog(
            keyId = key,
            isAsciiMode = isAsciiMode,
            onDismiss = { editingKey = null },
            onSaved = { editingKey = null },
        )
    }

    if (showImportModeDialog && pendingImportText != null) {
        AlertDialog(
            onDismissRequest = {
                showImportModeDialog = false
                pendingImportText = null
            },
            title = { Text("导入方式") },
            text = {
                Text("覆盖：用文件整盘替换中/英自定义。\n合并：保留本地其它键，同键以文件为准。")
            },
            confirmButton = {
                TextButton(onClick = { doImport(replace = true) }) {
                    Text("覆盖")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { doImport(replace = false) }) {
                        Text("合并")
                    }
                    TextButton(
                        onClick = {
                            showImportModeDialog = false
                            pendingImportText = null
                        },
                    ) {
                        Text("取消")
                    }
                }
            },
        )
    }
}

@Composable
private fun KeyPreviewChip(
    keyId: String,
    isAsciiMode: Boolean,
    gesture: KeyGestureConfig?,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val main = KeysConfigHelper.getKeyDisplayLabel(keyId, isAsciiMode)
    val hint = gesture?.swipeUp?.label?.takeIf { it.isNotEmpty() }
        ?: gesture?.swipeUp?.value?.takeIf { it.isNotEmpty() }
        ?: ""
    val borderColor = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hint.isNotEmpty()) {
            Text(
                text = hint,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = main,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun KeyEditDialog(
    keyId: String,
    isAsciiMode: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val effective = KeysConfigHelper.getKeyGesture(keyId, isAsciiMode)
    val yamlBase = KeysConfigHelper.getYamlKeyGesture(context, keyId, isAsciiMode)
    val existingOverride = KeyCustomizeStore.getOverride(context, isAsciiMode, keyId)

    fun initialTapLabel(): String =
        existingOverride?.tapLabel
            ?: effective?.tap?.label?.takeIf { it.isNotEmpty() }
            ?: keyId

    fun initialSwipeUp(): String =
        existingOverride?.swipeUp
            ?: effective?.swipeUp?.label?.takeIf { it.isNotEmpty() }
            ?: effective?.swipeUp?.value.orEmpty()

    fun initialSwipeDown(): String =
        existingOverride?.swipeDown
            ?: effective?.swipeDown?.label?.takeIf { it.isNotEmpty() }
            ?: effective?.swipeDown?.value.orEmpty()

    fun initialLongPress(): String =
        existingOverride?.longPress?.joinToString(", ")
            ?: effective?.longPress?.values
                ?.mapNotNull {
                    it.label.takeIf { l -> l.isNotEmpty() }
                        ?: it.value.takeIf { v -> v.isNotEmpty() }
                }
                ?.joinToString(", ")
            ?: ""

    var tapLabel by remember(keyId, isAsciiMode) { mutableStateOf(initialTapLabel()) }
    var swipeUp by remember(keyId, isAsciiMode) { mutableStateOf(initialSwipeUp()) }
    var swipeDown by remember(keyId, isAsciiMode) { mutableStateOf(initialSwipeDown()) }
    var longPressText by remember(keyId, isAsciiMode) { mutableStateOf(initialLongPress()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("编辑按键 ${keyId.uppercase()}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "键面字根只影响显示；上屏仍是字母「${keyId.lowercase()}」。角标/长按用逗号分隔。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = tapLabel,
                    onValueChange = { tapLabel = it },
                    label = { Text("键面字根 / 主文字") },
                    placeholder = { Text("例如：日（空=恢复字母）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = swipeUp,
                    onValueChange = { swipeUp = it },
                    label = { Text("上滑提示") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = swipeDown,
                    onValueChange = { swipeDown = it },
                    label = { Text("下滑提示") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longPressText,
                    onValueChange = { longPressText = it },
                    label = { Text("长按选项") },
                    placeholder = { Text("例如：1, Q, ｜") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val yamlHint = yamlBase?.swipeUp?.label ?: yamlBase?.swipeUp?.value
                if (!yamlHint.isNullOrEmpty()) {
                    Text(
                        text = "YAML 默认上滑：$yamlHint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val longPress = longPressText
                        .split(',', '，', '、', ';', '；')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .take(10)
                    val override = KeyCustomizeStore.KeyOverride(
                        tapLabel = tapLabel.trim(),
                        swipeUp = swipeUp.trim(),
                        swipeDown = swipeDown.trim(),
                        longPress = longPress,
                    )
                    KeysConfigHelper.saveKeyCustomization(context, isAsciiMode, keyId, override)
                    onSaved()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        KeysConfigHelper.clearKeyCustomization(context, isAsciiMode, keyId)
                        onSaved()
                    },
                ) {
                    Text("恢复此键")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}
