package com.vliveconvert.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.ui.ConvertItem
import com.vliveconvert.app.ui.MainScreen
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 模拟壁纸取色（绿色主色，刻意远离品牌蓝）渲染「转换中」列表：
 * 状态文字 / 缩略图外圈 / 行内进度条都必须取自 MaterialTheme.primary。
 * 供人工过目（产物 build/ui-shots/monet_busy_primary.png）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h792dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonetBusyColorScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun item(id: Long, status: String, done: Boolean = false) = ConvertItem(
        item = MediaItem(
            id, "/sdcard/DCIM/Camera/IMG_$id.jpg", "IMG_$id.jpg",
            1L, 1_754_000_000_000L + id, 1_754_000_000L + id, 1024L * 1024 * 3),
        status = status,
        done = done
    )

    @Test
    fun convertingAccentsFollowThemePrimary() {
        val monetPrimary = Color(0xFF1C7C54)
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(primary = monetPrimary),
                    typography = MaterialTheme.typography
                ) {
                    MainScreen(
                        items = listOf(
                            item(1, "待转换"),
                            item(2, "转换中… 42%"),
                            item(3, "完成：已导出到 DCIM/Camera", done = true)
                        ),
                        appName = "影中影",
                        statusText = "",
                        isConverting = true,
                        finishedBeat = false,
                        progress = 0.42f,
                        progressDetail = "正在转换 2/3 · IMG_2.jpg",
                        pendingRestoreCount = 0,
                        onRestoreOriginals = {},
                        locationMissing = false,
                        onGrantLocation = {},
                        reconvertCount = 0,
                        onReconvertLostGps = {},
                        onReconvertItem = {},
                        outputRelPath = "Pictures/VLiveConvert",
                        moveToCamera = true,
                        deleteOriginal = true,
                        onOpenSettings = {},
                        onShowStatusDetail = {},
                        onCancelConvert = {},
                        onAddMore = {},
                        onStartConvert = {},
                        onClearAll = {},
                        failedCount = 0,
                        onRetryFailed = {},
                        onShareItem = {},
                        onRemove = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val view = compose.activity.window.decorView
        val w = if (view.width > 0) view.width else 1080
        val h = if (view.height > 0) view.height else 2376
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/ui-shots").apply { mkdirs() }
        File(dir, "monet_busy_primary.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
