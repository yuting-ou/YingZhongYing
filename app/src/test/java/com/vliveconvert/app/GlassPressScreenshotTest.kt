package com.vliveconvert.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.service.PerfPreset
import com.vliveconvert.app.ui.GlassIconButton
import com.vliveconvert.app.ui.GlassOutlineButton
import com.vliveconvert.app.ui.GlossyButton
import com.vliveconvert.app.ui.GlassPresetSelector
import com.vliveconvert.app.ui.GlassSlider
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 按压色散视觉验收：静息/预设/滑块/图标按钮按下态，产物 build/ui-shots/。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassPressScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setShowcase(darkTheme: Boolean = false) {
        compose.setContent {
            VLiveConvertTheme(darkTheme = darkTheme, dynamicColor = false) {
                var slider by remember { mutableFloatStateOf(5f) }
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 28.dp, vertical = 36.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        GlassPresetSelector(
                            selected = PerfPreset.Balanced,
                            onSelect = {}
                        )
                        GlassSlider(
                            value = slider,
                            onValueChange = { slider = it },
                            valueRange = 1f..8f,
                            steps = 6,
                            contentDescription = "转换并发，并发 5 张",
                            valueLabel = "并发 5 张"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GlassIconButton(
                                icon = Icons.Default.Settings,
                                contentDescription = "设置",
                                onClick = {}
                            )
                            GlassIconButton(
                                icon = Icons.Default.Star,
                                contentDescription = "收藏",
                                onClick = {}
                            )
                            Box(Modifier.width(120.dp)) {
                                GlassOutlineButton(text = "取消转换", onClick = {})
                            }
                        }
                        GlossyButton(text = "开始转换", onClick = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        shadowOf(Looper.getMainLooper()).idle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/ui-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun press(nodeDescription: String) {
        compose.onNodeWithContentDescription(nodeDescription).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
    }

    @Test
    fun shotResting() {
        setShowcase()
        shot("glass_press_resting")
    }

    @Test
    fun shotPresetPressed() {
        setShowcase()
        press("转换预设均衡")
        shot("glass_press_preset")
    }

    @Test
    fun shotPresetUnselectedPressed() {
        setShowcase()
        compose.onNodeWithContentDescription("转换预设省电").performTouchInput {
            down(Offset(width * 0.22f, height * 0.35f))
        }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        shot("glass_press_preset_unselected")
    }

    @Test
    fun shotGearPressedDark() {
        setShowcase(darkTheme = true)
        press("设置")
        shot("glass_press_gear_dark")
    }

    @Test
    fun shotSliderPressed() {
        setShowcase()
        compose.onNodeWithContentDescription("转换并发，并发 5 张").performTouchInput {
            down(Offset(width * (4f / 7f), center.y))
            moveBy(Offset(48f, 0f))
        }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        shot("glass_press_slider")
    }

    @Test
    fun shotGearPressed() {
        setShowcase()
        press("设置")
        shot("glass_press_gear")
    }

    @Test
    fun shotGlossyPressed() {
        setShowcase()
        compose.onNodeWithText("开始转换").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        shot("glass_press_glossy")
    }
}
