package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.service.PerfPreset
import com.vliveconvert.app.ui.GlassPresetSelector
import com.vliveconvert.app.ui.presetTextColor
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlassPresetSelectorUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun presetTextColorChoosesHigherContrastDuringTransition() {
        val white = Color.White
        val blue = Color.Blue
        val black = Color.Black
        assertEquals(black, presetTextColor(0f, white, blue, black, white))
        assertEquals(white, presetTextColor(1f, white, blue, black, white))
        val chosen = presetTextColor(0.5f, white, blue, black, white)
        assertTrue(chosen == black || chosen == white)
        assertEquals(black, chosen)
    }

    @Test
    fun selectedPresetHasSelectionSemanticsAndLargeTouchTarget() {
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                GlassPresetSelector(selected = PerfPreset.Balanced, onSelect = {})
            }
        }

        val selected = compose.onNodeWithContentDescription("转换预设均衡")
        selected.assertHasClickAction()
            .assertIsSelected()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("转换预设省电").assertIsNotSelected()
    }

    @Test
    fun customValuesHaveNoSelectedPresetAndClickEmitsPreset() {
        var picked: PerfPreset? = null
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                GlassPresetSelector(selected = null, onSelect = { picked = it })
            }
        }

        compose.onNodeWithContentDescription("转换预设性能").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(PerfPreset.Performance, picked) }
    }
}
