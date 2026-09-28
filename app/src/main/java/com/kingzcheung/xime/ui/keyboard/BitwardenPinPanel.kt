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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.service.BitwardenPinEditTextHolder

internal const val BITWARDEN_PIN_PANEL_HEIGHT = 160

@Composable
fun BitwardenPinPanel(
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    error: String?,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { BitwardenPinEditTextHolder.editText = null }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(BITWARDEN_PIN_PANEL_HEIGHT.dp)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBgColor.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.height(18.dp).width(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "输入 PIN 解锁密码库",
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = "取消",
                        color = textColor.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable(onClick = onCancel)
                            .padding(vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AndroidView(
                    factory = { context ->
                        android.widget.EditText(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setTextColor(textColor.hashCode())
                            setHintTextColor((textColor.copy(alpha = 0.4f)).hashCode())
                            hint = "4–8 位数字"
                            textSize = 18f
                            isSingleLine = true
                            inputType = EditorInfo.TYPE_CLASS_NUMBER or
                                EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
                            gravity = Gravity.CENTER
                            transformationMethod =
                                android.text.method.PasswordTransformationMethod.getInstance()
                            setPadding(8, 8, 8, 8)
                            imeOptions = EditorInfo.IME_ACTION_DONE
                            addTextChangedListener(object : android.text.TextWatcher {
                                override fun beforeTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    count: Int,
                                    after: Int,
                                ) {}
                                override fun onTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    before: Int,
                                    count: Int,
                                ) {}
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    val digits = s?.toString()?.filter { it.isDigit() }.orEmpty()
                                        .take(8)
                                    if (s?.toString() != digits) {
                                        s?.replace(0, s.length, digits)
                                        return
                                    }
                                    pin = digits
                                }
                            })
                            setOnEditorActionListener { _, actionId, _ ->
                                if (actionId == EditorInfo.IME_ACTION_DONE && pin.length >= 4) {
                                    onSubmit(pin)
                                    true
                                } else false
                            }
                            BitwardenPinEditTextHolder.editText = this
                            post { requestFocus() }
                        }
                    },
                    update = { et ->
                        BitwardenPinEditTextHolder.editText = et
                        if (!et.hasFocus()) et.post { et.requestFocus() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardBgColor.copy(alpha = 0.85f)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = error.orEmpty(),
                        color = Color(0xFFE53935),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "确认",
                        color = if (pin.length >= 4) accentColor else accentColor.copy(alpha = 0.35f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(enabled = pin.length >= 4) { onSubmit(pin) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
