package com.vliveconvert.app.ui

import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberAmbientGlassPhoto(enabled: Boolean, uri: Uri?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    return produceState<ImageBitmap?>(null, enabled, uri, context) {
        value = null
        if (!enabled || uri == null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(640, 640), null).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }.value
}

@Composable
internal fun GlassSamplingBackdrop(image: ImageBitmap?) {
    val dark = isDarkGlass()
    Box(Modifier.fillMaxSize()) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier.fillMaxSize().background(
                    Color.Black.copy(alpha = if (dark) 0.38f else 0.24f)
                )
            )
        } else {
            Box(Modifier.fillMaxSize().glassWallpaper())
        }
        Box(Modifier.fillMaxSize().glassSamplingTexture(dark))
    }
}
