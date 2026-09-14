package com.kingzcheung.xime.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.DictEntry
import com.kingzcheung.xime.viewmodel.CustomPhraseUiState
import com.kingzcheung.xime.viewmodel.CustomPhraseViewModel
import com.kingzcheung.xime.viewmodel.PersonalDictUiState
import com.kingzcheung.xime.viewmodel.PersonalDictViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PersonalDictViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedDictTab by remember { mutableIntStateOf(0) }
    var showSchemaMenu by remember { mutableStateOf(false) }
    val customPhraseVM: CustomPhraseViewModel = viewModel(key = "dict_custom_phrase")
    val customPhraseState by customPhraseVM.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.selectedSchema) { customPhraseVM.setSchema(uiState.selectedSchema) }
    LaunchedEffect(Unit) {
        viewModel.toast.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    val importUserDictLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importUserDict(uri)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val schema = uiState.availableSchemas.find { it.schemaId == uiState.selectedSchema }
                    Text("词库管理 - ${schema?.name ?: uiState.selectedSchema}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.clickable { showSchemaMenu = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val schema = uiState.availableSchemas.find { it.schemaId == uiState.selectedSchema }
                        Text(schema?.name ?: uiState.selectedSchema, style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showSchemaMenu,
                        onDismissRequest = { showSchemaMenu = false },
                        offset = DpOffset(0.dp, 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        for (s in uiState.availableSchemas) {
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = { showSchemaMenu = false; viewModel.selectSchema(s.schemaId) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            // 个人词库已改为只读，仅自定义短语支持增删改
            if (selectedDictTab == 0) {
                FloatingActionButton(
                    onClick = { customPhraseVM.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else if (selectedDictTab == 1) {
                FloatingActionButton(
                    onClick = {
                        if (!uiState.isImporting) {
                            importUserDictLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.UploadFile, contentDescription = "导入个人词库", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                TabButton("自定义短语", selected = selectedDictTab == 0, onClick = { selectedDictTab = 0 }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TabButton("个人词库", selected = selectedDictTab == 1, onClick = { selectedDictTab = 1 }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TabButton("方案词库", selected = selectedDictTab == 2, onClick = { selectedDictTab = 2 }, modifier = Modifier.weight(1f))
            }
            when (selectedDictTab) {
                0 -> CustomPhraseTabContent(viewModel = customPhraseVM, uiState = customPhraseState)
                1 -> SchemaDictContent(viewModel = viewModel, uiState = uiState, onImport = {
                    importUserDictLauncher.launch(arrayOf("*/*"))
                })
                2 -> SchemaDictBrowserPanel()
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SchemaDictContent(
    viewModel: PersonalDictViewModel,
    uiState: PersonalDictUiState,
    onImport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onImport,
                enabled = !uiState.isImporting,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("导入")
            }
            OutlinedButton(
                onClick = { viewModel.exportUserDict() },
                enabled = uiState.hasExportableUserDb && !uiState.isExporting,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("导出")
            }
        }
        Text(
            text = "导出当前方案的 *.userdb.txt（zip 到 Downloads）。右上角可切换方案。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                BasicTextField(value = uiState.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        Box { if (uiState.searchQuery.isEmpty()) Text("搜索", color = MaterialTheme.colorScheme.onSurfaceVariant); innerTextField() }
                    })
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearSearch() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Text(
                buildString {
                    append("共 ${uiState.entries.size} 条")
                    if (uiState.fromUserDb) append("（自造词 / userdb）")
                    if (uiState.searchQuery.isNotEmpty()) append("，搜索结果 ${uiState.filteredEntries.size} 条")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (uiState.entries.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    UsageHint(onImport = onImport)
                }
            } else if (uiState.filteredEntries.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("未找到匹配条目", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                    itemsIndexed(items = uiState.filteredEntries,
                        key = { i, e -> "${e.word}_${e.code}_$i" }) { _, entry ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.word, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(entry.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageHint(onImport: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("暂无词条", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "三个标签别搞混：\n" +
                "· 自定义短语：你手写的短句/缩写（可增删）\n" +
                "· 个人词库：打字练出来的自造词（*.userdb.txt）\n" +
                "· 方案词库：方案自带大词库（与导入无关，显示 5 万是上限截断）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "可导入 *.userdb.txt 或 user_*.dict.yaml（如 pinyin_simp.userdb.txt）。\n" +
                "每个方案各有一份；薄荷和简体拼音不会互相覆盖。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onImport) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("导入个人词库")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "自定义短语的增删改 → 切换「自定义短语」标签页",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri("https://ime.ximei.me/features/dictionary.html") },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CustomPhraseTabContent(
    viewModel: CustomPhraseViewModel,
    uiState: CustomPhraseUiState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                BasicTextField(value = uiState.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        Box { if (uiState.searchQuery.isEmpty()) Text("搜索", color = MaterialTheme.colorScheme.onSurfaceVariant); innerTextField() }
                    })
                if (uiState.searchQuery.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.clearSearch() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (uiState.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CustomPhraseEmptyHint()
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(uiState.filteredEntries, key = { i, _ -> "cp_$i" }) { i, entry ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.word, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                Text(entry.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                if (entry.weight != null) {
                                    Text("权重: ${entry.weight}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            IconButton(onClick = {
                                viewModel.setEditing(i, entry)
                                viewModel.showEditDialog()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteEntry(i) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        PhraseEditDialog(
            title = "添加快捷短语",
            word = uiState.editWord,
            code = uiState.editCode,
            weight = uiState.editWeight,
            onWordChange = viewModel::setEditWord,
            onCodeChange = viewModel::setEditCode,
            onWeightChange = viewModel::setEditWeight,
            onConfirm = { viewModel.addEntry(uiState.editWord, uiState.editCode, uiState.editWeight.toIntOrNull()); viewModel.hideAddDialog() },
            onDismiss = viewModel::hideAddDialog,
        )
    }
    if (uiState.showEditDialog) {
        PhraseEditDialog(
            title = "编辑快捷短语",
            word = uiState.editWord,
            code = uiState.editCode,
            weight = uiState.editWeight,
            onWordChange = viewModel::setEditWord,
            onCodeChange = viewModel::setEditCode,
            onWeightChange = viewModel::setEditWeight,
            onConfirm = { viewModel.updateEntry(uiState.editIndex, uiState.editWord, uiState.editCode, uiState.editWeight.toIntOrNull()); viewModel.hideEditDialog() },
            onDismiss = viewModel::hideEditDialog,
        )
    }
}

@Composable
private fun CustomPhraseEmptyHint() {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("暂无自定义短语", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("点击右下角 + 添加",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PhraseEditDialog(
    title: String,
    word: String,
    code: String,
    weight: String,
    onWordChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 20.dp))
            OutlinedTextField(value = word, onValueChange = onWordChange,
                label = { Text("短语") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = code, onValueChange = onCodeChange,
                label = { Text("编码") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = weight, onValueChange = onWeightChange,
                label = { Text("权重（可选，越大越优先）") }, shape = RoundedCornerShape(12.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.clickable(enabled = word.isNotBlank() && code.isNotBlank(), onClick = onConfirm),
                    shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary
                ) {
                    Text("确定", modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
