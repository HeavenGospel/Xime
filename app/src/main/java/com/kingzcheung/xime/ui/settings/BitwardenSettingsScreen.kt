package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.kingzcheung.xime.bitwarden.BitwardenCrypto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.bitwarden.BitwardenPinMode
import com.kingzcheung.xime.bitwarden.BitwardenPrefs
import com.kingzcheung.xime.bitwarden.BitwardenServerPreset
import com.kingzcheung.xime.bitwarden.BitwardenTwoFactorRequiredException
import com.kingzcheung.xime.bitwarden.BitwardenUiState
import com.kingzcheung.xime.bitwarden.BitwardenVaultRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitwardenSettingsContent(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { BitwardenVaultRepository.getInstance(context) }
    val vaultState by repo.state.collectAsState()
    val scope = rememberCoroutineScope()

    var preset by remember { mutableStateOf(BitwardenPrefs.getPreset(context)) }
    var selfHosted by remember { mutableStateOf(BitwardenPrefs.getSelfHostedBase(context)) }
    var email by remember { mutableStateOf(BitwardenPrefs.getEmail(context)) }
    var masterPassword by remember { mutableStateOf("") }
    var twoFactor by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var need2fa by remember { mutableStateOf(false) }
    var genOpts by remember { mutableStateOf(BitwardenPrefs.getPasswordGeneratorOptions(context)) }
    var genPreview by remember {
        mutableStateOf(BitwardenCrypto.generatePassword(BitwardenPrefs.getPasswordGeneratorOptions(context)))
    }
    var pinMode by remember { mutableStateOf(BitwardenPrefs.getPinMode(context)) }
    var pinTimeoutMin by remember { mutableStateOf(BitwardenPrefs.getPinTimeoutMinutes(context)) }
    var pinNew by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinStatus by remember { mutableStateOf<String?>(null) }

    val unlocked = vaultState is BitwardenUiState.Unlocked ||
        vaultState is BitwardenUiState.Editing ||
        vaultState is BitwardenUiState.Viewing
    val itemCount = (vaultState as? BitwardenUiState.Unlocked)?.items?.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Bitwarden 密码库") },
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
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(
                title = "说明",
                content = {
                    Text(
                        "主密码请在本页解锁（系统键盘可正常输入）。解锁后会话会安全保存在本机，覆盖安装/重启一般无需重登；点「锁定/清除会话」后才需再输主密码。自建请填根地址，例如 https://vault.example.com（不要带 /api）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                },
            )

            SettingsSection(
                title = "状态",
                content = {
                    Text(
                        text = when {
                            unlocked && itemCount != null -> "已解锁 · 已同步 $itemCount 条登录"
                            unlocked -> "已解锁"
                            need2fa || vaultState is BitwardenUiState.NeedsTwoFactor -> "需要两步验证码"
                            vaultState is BitwardenUiState.Error -> "解锁失败"
                            BitwardenPrefs.isConfigured(context) -> "已配置 · 未解锁"
                            else -> "未配置"
                        },
                        modifier = Modifier.padding(16.dp),
                        color = if (unlocked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                },
            )

            SettingsSection(
                title = "服务器",
                content = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        BitwardenServerPreset.entries.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { preset = item }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = preset == item, onClick = { preset = item })
                                Text(item.label, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        if (preset == BitwardenServerPreset.SELF_HOSTED) {
                            OutlinedTextField(
                                value = selfHosted,
                                onValueChange = { selfHosted = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                label = { Text("自建根地址") },
                                placeholder = { Text("https://vault.example.com") },
                                singleLine = true,
                            )
                        }
                    }
                },
            )

            SettingsSection(
                title = "账号与解锁",
                content = {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("邮箱") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                        OutlinedTextField(
                            value = masterPassword,
                            onValueChange = { masterPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("主密码") },
                            singleLine = true,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                Text(
                                    text = if (showPassword) "隐藏" else "显示",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { showPassword = !showPassword }
                                        .padding(8.dp),
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        if (need2fa || vaultState is BitwardenUiState.NeedsTwoFactor) {
                            OutlinedTextField(
                                value = twoFactor,
                                onValueChange = { twoFactor = it.filter { ch -> ch.isDigit() }.take(8) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("两步验证码") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                },
            )

            Button(
                onClick = {
                    BitwardenPrefs.setPreset(context, preset)
                    BitwardenPrefs.setSelfHostedBase(context, selfHosted)
                    BitwardenPrefs.setEmail(context, email)
                    repo.notifyConfigChanged()
                    if (masterPassword.isBlank()) {
                        status = "配置已保存。请填写主密码后点「解锁保险库」。"
                        return@Button
                    }
                    busy = true
                    status = "正在连接并解锁…"
                    scope.launch {
                        val result = if (need2fa || vaultState is BitwardenUiState.NeedsTwoFactor) {
                            repo.submitTwoFactor(twoFactor)
                        } else {
                            repo.unlock(masterPassword)
                        }
                        busy = false
                        result.fold(
                            onSuccess = {
                                need2fa = false
                                twoFactor = ""
                                masterPassword = ""
                                status = "解锁成功。可在键盘工具栏打开密码库使用。"
                            },
                            onFailure = { e ->
                                if (e is BitwardenTwoFactorRequiredException ||
                                    repo.state.value is BitwardenUiState.NeedsTwoFactor
                                ) {
                                    need2fa = true
                                    status = "需要两步验证码，请填写后再次点解锁。"
                                } else {
                                    status = e.message ?: "解锁失败"
                                }
                            },
                        )
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(Icons.Filled.VpnKey, contentDescription = null)
                Text(
                    text = when {
                        busy -> "  处理中…"
                        need2fa || vaultState is BitwardenUiState.NeedsTwoFactor -> "  提交验证码并解锁"
                        else -> "  保存并解锁保险库"
                    },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            OutlinedButton(
                onClick = {
                    repo.lock()
                    need2fa = false
                    status = "已锁定并清除会话。"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("锁定 / 清除会话")
            }

            SettingsSection(
                title = "PIN 保护",
                content = {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "会话保存在本机后，可用短数字 PIN 防止旁人点开工具栏密码库。主密码仍用于首次解锁。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BitwardenPinMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (mode != BitwardenPinMode.OFF && !BitwardenPrefs.hasPin(context)) {
                                            pinStatus = "请先设置 PIN（4–8 位数字）"
                                            return@clickable
                                        }
                                        pinMode = mode
                                        BitwardenPrefs.setPinMode(context, mode)
                                        pinStatus = "已切换为：${mode.label}"
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = pinMode == mode,
                                    onClick = {
                                        if (mode != BitwardenPinMode.OFF && !BitwardenPrefs.hasPin(context)) {
                                            pinStatus = "请先设置 PIN（4–8 位数字）"
                                            return@RadioButton
                                        }
                                        pinMode = mode
                                        BitwardenPrefs.setPinMode(context, mode)
                                        pinStatus = "已切换为：${mode.label}"
                                    },
                                )
                                Text(mode.label, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        if (pinMode == BitwardenPinMode.TIMEOUT) {
                            Text(
                                text = "超时时间",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            BitwardenPrefs.PIN_TIMEOUT_OPTIONS_MIN.forEach { min ->
                                val label = when {
                                    min < 60 -> "${min} 分钟"
                                    min == 60 -> "1 小时"
                                    else -> "${min / 60} 小时"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pinTimeoutMin = min
                                            BitwardenPrefs.setPinTimeoutMinutes(context, min)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = pinTimeoutMin == min,
                                        onClick = {
                                            pinTimeoutMin = min
                                            BitwardenPrefs.setPinTimeoutMinutes(context, min)
                                        },
                                    )
                                    Text(label, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                        Text(
                            text = if (BitwardenPrefs.hasPin(context)) "已设置 PIN（可下方重设）" else "尚未设置 PIN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = pinNew,
                            onValueChange = { pinNew = it.filter { c -> c.isDigit() }.take(8) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("新 PIN（4–8 位数字）") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        OutlinedTextField(
                            value = pinConfirm,
                            onValueChange = { pinConfirm = it.filter { c -> c.isDigit() }.take(8) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("确认 PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    when {
                                        pinNew.length !in 4..8 ->
                                            pinStatus = "PIN 须为 4–8 位数字"
                                        pinNew != pinConfirm ->
                                            pinStatus = "两次输入不一致"
                                        else -> {
                                            if (BitwardenPrefs.setPin(context, pinNew)) {
                                                pinNew = ""
                                                pinConfirm = ""
                                                if (pinMode == BitwardenPinMode.OFF) {
                                                    pinMode = BitwardenPinMode.EVERY_OPEN
                                                    BitwardenPrefs.setPinMode(context, pinMode)
                                                }
                                                pinStatus = "PIN 已保存（${pinMode.label}）"
                                            } else {
                                                pinStatus = "PIN 保存失败"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("保存 PIN")
                            }
                            OutlinedButton(
                                onClick = {
                                    BitwardenPrefs.clearPin(context)
                                    pinMode = BitwardenPinMode.OFF
                                    pinNew = ""
                                    pinConfirm = ""
                                    pinStatus = "已清除 PIN"
                                },
                                modifier = Modifier.weight(1f),
                                enabled = BitwardenPrefs.hasPin(context),
                            ) {
                                Text("清除 PIN")
                            }
                        }
                        if (!pinStatus.isNullOrBlank()) {
                            Text(
                                text = pinStatus!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )

            Text("密码生成器", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "添加/编辑条目点「生成」时使用下列规则。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = genOpts.length.toString(),
                onValueChange = { raw ->
                    val n = raw.filter { it.isDigit() }.toIntOrNull() ?: genOpts.length
                    genOpts = genOpts.copy(length = n.coerceIn(5, 128))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("长度（5–128）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            GenSwitch("小写字母 a-z", genOpts.lowercase) {
                genOpts = genOpts.copy(lowercase = it)
            }
            GenSwitch("大写字母 A-Z", genOpts.uppercase) {
                genOpts = genOpts.copy(uppercase = it)
            }
            GenSwitch("数字", genOpts.numbers) {
                genOpts = genOpts.copy(numbers = it)
            }
            GenSwitch("特殊符号 !@#$%^&*", genOpts.special) {
                genOpts = genOpts.copy(special = it)
            }
            GenSwitch("避开易混字符 (0OIl1)", genOpts.avoidAmbiguous) {
                genOpts = genOpts.copy(avoidAmbiguous = it)
            }
            Text(
                text = "预览：$genPreview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        genPreview = BitwardenCrypto.generatePassword(genOpts)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("预览生成")
                }
                Button(
                    onClick = {
                        BitwardenPrefs.setPasswordGeneratorOptions(context, genOpts)
                        genOpts = BitwardenPrefs.getPasswordGeneratorOptions(context)
                        genPreview = BitwardenCrypto.generatePassword(genOpts)
                        status = "密码生成规则已保存。"
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存规则")
                }
            }

            if (!status.isNullOrBlank()) {
                Text(
                    text = status!!,
                    color = if (unlocked && status!!.contains("成功")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val err = (vaultState as? BitwardenUiState.Error)?.message
            if (!err.isNullOrBlank() && status != err) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GenSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
