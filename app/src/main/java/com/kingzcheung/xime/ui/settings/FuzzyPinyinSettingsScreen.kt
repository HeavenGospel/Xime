package com.kingzcheung.xime.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.FuzzyPinyinHelper
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.PersonalDictManager
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuzzyPinyinSettingsContent(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabledMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            FuzzyPinyinHelper.Group.entries.forEach { g ->
                put(g.id, FuzzyPinyinHelper.isEnabled(context, g))
            }
        }
    }
    var isApplying by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    fun persistLocal(group: FuzzyPinyinHelper.Group, value: Boolean) {
        enabledMap[group.id] = value
        FuzzyPinyinHelper.setEnabled(context, group, value)
        dirty = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("模糊拼音") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                SettingsSection(title = "说明", content = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp, top = 2.dp)
                                .size(20.dp),
                        )
                        Text(
                            text = "开启后，声母/韵母相近的音可以互通（如 zhi≈zi）。" +
                                "写入拼音方案补丁后需要部署才生效；纯五笔方案不受影响。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                })
            }

            item {
                SettingsSection(title = "快捷", content = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                FuzzyPinyinHelper.enableRecommended(context)
                                FuzzyPinyinHelper.Group.entries.forEach { g ->
                                    enabledMap[g.id] = g.recommended
                                }
                                dirty = true
                            },
                            enabled = !isApplying,
                            modifier = Modifier.weight(1f),
                        ) { Text("常用预设") }
                        OutlinedButton(
                            onClick = {
                                FuzzyPinyinHelper.disableAll(context)
                                FuzzyPinyinHelper.Group.entries.forEach { g ->
                                    enabledMap[g.id] = false
                                }
                                dirty = true
                            },
                            enabled = !isApplying,
                            modifier = Modifier.weight(1f),
                        ) { Text("全部关闭") }
                    }
                })
            }

            item {
                SettingsSection(title = "声母", content = {
                    val initials = listOf(
                        FuzzyPinyinHelper.Group.ZH_Z,
                        FuzzyPinyinHelper.Group.CH_C,
                        FuzzyPinyinHelper.Group.SH_S,
                        FuzzyPinyinHelper.Group.N_L,
                        FuzzyPinyinHelper.Group.F_H,
                        FuzzyPinyinHelper.Group.R_L,
                    )
                    initials.forEachIndexed { index, group ->
                        FuzzyToggleRow(
                            title = group.title,
                            subtitle = group.subtitle,
                            checked = enabledMap[group.id] == true,
                            enabled = !isApplying,
                            onCheckedChange = { persistLocal(group, it) },
                        )
                        if (index != initials.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                })
            }

            item {
                SettingsSection(title = "韵母", content = {
                    val finals = listOf(
                        FuzzyPinyinHelper.Group.AN_ANG,
                        FuzzyPinyinHelper.Group.EN_ENG,
                        FuzzyPinyinHelper.Group.IN_ING,
                        FuzzyPinyinHelper.Group.IAN_IANG,
                        FuzzyPinyinHelper.Group.UAN_UANG,
                    )
                    finals.forEachIndexed { index, group ->
                        FuzzyToggleRow(
                            title = group.title,
                            subtitle = group.subtitle,
                            checked = enabledMap[group.id] == true,
                            enabled = !isApplying,
                            onCheckedChange = { persistLocal(group, it) },
                        )
                        if (index != finals.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                })
            }

            item {
                Button(
                    onClick = {
                        if (isApplying) return@Button
                        isApplying = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                FuzzyPinyinHelper.applyToSchemas(context)
                                PersonalDictManager.ensureSchemaPacks(context)
                                KeysConfigHelper.loadConfig(context)
                                KeyboardThemes.reload(context)
                                val engine = RimeEngine.getInstance()
                                val deployed = engine.deploy()
                                if (deployed) {
                                    RimeConfigHelper.storeDeploymentHash(context)
                                }
                                deployed
                            }
                            isApplying = false
                            dirty = false
                            Toast.makeText(
                                context,
                                if (ok) "已应用并部署完成" else "补丁已写入，但部署失败，请到「输入方案」手动部署",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    enabled = !isApplying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("正在部署…")
                    } else {
                        Text(if (dirty) "应用并部署" else "重新应用并部署")
                    }
                }
                Text(
                    text = "部署可能需要数十秒（编译音节表）。完成后即可在拼音方案中验证，例如开启 zh↔z 后输入 zi 应能出「知」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun FuzzyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}
