package com.kingzcheung.xime.ui.keyboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.bitwarden.BitwardenTotp
import com.kingzcheung.xime.bitwarden.BitwardenUiState
import com.kingzcheung.xime.bitwarden.BitwardenVaultRepository

/**
 * 只读详情：布局高度与搜索/编辑一致；可填入账号密码验证码，可复制字段，可进入编辑或返回搜索。
 */
@Composable
fun BitwardenDetailPanel(
    repository: BitwardenVaultRepository,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onFillUsername: (String) -> Unit,
    onFillPassword: (String) -> Unit,
    onFillTotp: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val viewing = state as? BitwardenUiState.Viewing
    val item = viewing?.item
    val totpCode = remember(item?.totp) {
        item?.totp?.let { BitwardenTotp.code(it) }
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
            if (item == null) {
                Text("无详情", color = textColor.copy(alpha = 0.6f), fontSize = 13.sp)
                return@Column
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "查看详情",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "返回",
                            color = textColor.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable(onClick = onBack)
                                .padding(vertical = 2.dp),
                        )
                        Text(
                            text = "编辑",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(onClick = onEdit)
                                .padding(vertical = 2.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        VaultItemIcon(
                            item = item,
                            tint = textColor,
                            size = 40.dp,
                        )
                        Text(
                            text = item.name.ifBlank { "(未命名)" },
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.name.isNotBlank()) {
                            CopyChip(accentColor) { onCopy(item.name) }
                        }
                    }
                    DetailLine(
                        label = "用户名",
                        value = item.username.ifBlank { "—" },
                        textColor = textColor,
                        accentColor = accentColor,
                        copyValue = item.username.takeIf { it.isNotBlank() },
                        onCopy = onCopy,
                    )
                    DetailLine(
                        label = "密码",
                        value = if (item.password.isBlank()) "—" else item.password,
                        textColor = textColor,
                        accentColor = accentColor,
                        copyValue = item.password.takeIf { it.isNotBlank() },
                        onCopy = onCopy,
                    )
                    DetailLine(
                        label = "URI",
                        value = item.uris.joinToString("\n").ifBlank { "—" },
                        textColor = textColor,
                        accentColor = accentColor,
                        copyValue = item.uris.joinToString("\n").takeIf { it.isNotBlank() },
                        onCopy = onCopy,
                    )
                    if (!item.totp.isNullOrBlank()) {
                        DetailLine(
                            label = "验证器",
                            value = item.totp,
                            textColor = textColor,
                            accentColor = accentColor,
                            copyValue = item.totp,
                            onCopy = onCopy,
                        )
                        DetailLine(
                            label = "当前验证码",
                            value = totpCode ?: "（无法生成）",
                            textColor = accentColor,
                            accentColor = accentColor,
                            copyValue = totpCode,
                            onCopy = onCopy,
                        )
                    }
                    if (!item.notes.isNullOrBlank()) {
                        DetailLine(
                            label = "备注",
                            value = item.notes,
                            textColor = textColor,
                            accentColor = accentColor,
                            copyValue = item.notes,
                            onCopy = onCopy,
                        )
                    }
                    item.fields.forEach { f ->
                        DetailLine(
                            label = f.name.ifBlank { "字段" },
                            value = f.value.ifBlank { "—" },
                            textColor = textColor,
                            accentColor = accentColor,
                            copyValue = f.value.takeIf { it.isNotBlank() },
                            onCopy = onCopy,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailAction("填账号", accentColor) {
                            if (item.username.isNotBlank()) onFillUsername(item.username)
                        }
                        DetailAction("填密码", accentColor) {
                            if (item.password.isNotBlank()) onFillPassword(item.password)
                        }
                        if (!item.totp.isNullOrBlank()) {
                            DetailAction("填验证码", accentColor) {
                                val code = BitwardenTotp.code(item.totp)
                                if (!code.isNullOrBlank()) onFillTotp(code)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    textColor: Color,
    accentColor: Color,
    copyValue: String?,
    onCopy: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = textColor.copy(alpha = 0.5f), fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                color = textColor,
                fontSize = 13.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!copyValue.isNullOrBlank()) {
                CopyChip(accentColor) { onCopy(copyValue) }
            }
        }
    }
}

@Composable
private fun CopyChip(accentColor: Color, onClick: () -> Unit) {
    Text(
        text = "复制",
        color = accentColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accentColor.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun DetailAction(label: String, accentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = label, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
