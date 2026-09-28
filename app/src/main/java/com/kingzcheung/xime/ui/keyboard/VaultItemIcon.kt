package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kingzcheung.xime.bitwarden.BitwardenIcons
import com.kingzcheung.xime.bitwarden.VaultItemIconSource
import com.kingzcheung.xime.bitwarden.VaultItemIcons
import com.kingzcheung.xime.bitwarden.VaultLoginItem

@Composable
fun VaultItemIcon(
    item: VaultLoginItem,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val context = LocalContext.current
    val source = remember(item.id, item.uris) {
        VaultItemIcons.resolve(item, context)
    }
    val globe = rememberVectorPainter(BitwardenIcons.Globe)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        when (source) {
            is VaultItemIconSource.AppIcon -> {
                val drawable = remember(source.packageName) {
                    VaultItemIcons.loadAppDrawable(context, source.packageName)
                }
                if (drawable != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(drawable)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = globe,
                        fallback = globe,
                    )
                } else {
                    GlobeFallback(tint = tint, iconSize = size * 0.55f)
                }
            }
            is VaultItemIconSource.WebIcon -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(source.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = globe,
                    error = globe,
                    fallback = globe,
                )
            }
            VaultItemIconSource.Fallback -> {
                GlobeFallback(tint = tint, iconSize = size * 0.55f)
            }
        }
    }
}

@Composable
private fun GlobeFallback(tint: Color, iconSize: Dp) {
    Icon(
        imageVector = BitwardenIcons.Globe,
        contentDescription = null,
        tint = tint.copy(alpha = 0.55f),
        modifier = Modifier.size(iconSize),
    )
}
