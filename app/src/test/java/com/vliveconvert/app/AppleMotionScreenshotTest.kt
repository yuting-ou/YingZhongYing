package com.vliveconvert.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.ui.GlassLevel
import com.vliveconvert.app.ui.GlassSamplingBackdrop
import com.vliveconvert.app.ui.HazeGlassCard
import com.vliveconvert.app.ui.glassBackdropSource
import com.vliveconvert.app.ui.largeTitleCollapse
import com.vliveconvert.app.ui.rememberGlassHazeState
import com.vliveconvert.app.ui.shouldLoadAmbientPhoto
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h792dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppleMotionScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun largeTitleCollapseIsContinuousAndReduceMotionSnaps() {
        assertEquals(1f, largeTitleCollapse(0, 0, false), 0.001f)
        assertEquals(0.5f, largeTitleCollapse(0, 120, false), 0.001f)
        assertEquals(0f, largeTitleCollapse(1, 0, false), 0.001f)
        assertEquals(0f, largeTitleCollapse(0, 1, true), 0.001f)
    }

    @Test
    fun ambientPhotoLoadsOnlyWhenEnabledSupportedAndAvailable() {
        assertTrue(shouldLoadAmbientPhoto(enabled = true, compatibility = false, hasUri = true))
        assertFalse(shouldLoadAmbientPhoto(enabled = false, compatibility = false, hasUri = true))
        assertFalse(shouldLoadAmbientPhoto(enabled = true, compatibility = true, hasUri = true))
        assertFalse(shouldLoadAmbientPhoto(enabled = true, compatibility = false, hasUri = false))
    }

    @Test
    fun shotDarkAmbientThickGlass() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(0xFF5B4B8A.toInt())
        }
        compose.setContent {
            VLiveConvertTheme(darkTheme = true, dynamicColor = false) {
                val hazeState = rememberGlassHazeState()
                Box(
                    Modifier.fillMaxSize()
                        .glassBackdropSource(hazeState)
                        .padding(24.dp)
                ) {
                    GlassSamplingBackdrop(bitmap.asImageBitmap())
                    HazeGlassCard(
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxSize().height(120.dp),
                        level = GlassLevel.Thick
                    ) {
                        Text("厚玻璃 · 照片氛围", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File("build/ui-shots").mkdirs()
        File("build/ui-shots/apple_bc_main_dark.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
