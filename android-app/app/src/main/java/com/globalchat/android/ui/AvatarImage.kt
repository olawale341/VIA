package com.globalchat.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun AvatarImage(
    avatarUrl: String?,
    avatarBase64: String? = null,
    isGroup: Boolean = false,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val p = LocalWaPalette.current
    val context = LocalContext.current
    val localBmp = remember(avatarBase64) { MediaHelper.decodeBase64ToImage(avatarBase64) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isGroup) Color(0xFF005C4B) else p.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isGroup) Icons.Default.Groups else Icons.Default.Person,
            contentDescription = null,
            tint = if (isGroup) Color.White else p.textSecondary,
            modifier = Modifier.size(size * 0.54f)
        )

        if (localBmp != null) {
            Image(
                bitmap = localBmp,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}