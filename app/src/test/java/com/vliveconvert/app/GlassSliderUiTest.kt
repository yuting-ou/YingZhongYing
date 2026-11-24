package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.ui.GlassSlider
import com.vliveconvert.app.ui.LocalReduceMotion
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
class GlassSliderUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun semanticsExposeProgressAndSetProgressUpdatesValue() {
        var value by mutableFloatStateOf(5f)
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                GlassSlider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 1f..8f,
                    steps = 6,
                    contentDescription = "转换并发，并发 5 张",
                    valueLabel = "并发 5 张"
                )
            }
        }

        val node = compose.onNodeWithContentDescription("转换并发，并发 5 张")
        node.assertExists()
        val config = node.fetchSemanticsNode().config
        assertTrue(config.contains(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals(1f..8f, config[SemanticsProperties.ProgressBarRangeInfo].range)
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.runOnIdle { assertEquals(7f, value) }
    }

    @Test
    fun tooltipStaysInsideSliderAtRightEndpoint() {
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    Box(Modifier.width(300.dp)) {
                    GlassSlider(
                        value = 8f,
                        onValueChange = {},
                        valueRange = 1f..8f,
                        steps = 6,
                        contentDescription = "转换并发，并发 8 张",
                        valueLabel = "并发 8 张"
                        )
                    }
                }
            }
        }

        val slider = compose.onNodeWithContentDescription("转换并发，并发 8 张")
        slider.performTouchInput {
            down(center)
            moveTo(center + Offset(20f, 0f))
            advanceEventTime(100)
        }
        compose.waitForIdle()
        val sliderBounds = slider.fetchSemanticsNode().boundsInRoot
        val tooltipBounds = compose.onNodeWithText("并发 8 张").fetchSemanticsNode().boundsInRoot
        assertTrue("气泡右缘不能超出滑杆：${tooltipBounds.right} > ${sliderBounds.right}",
            tooltipBounds.right <= sliderBounds.right + 1f)
        assertTrue("气泡左缘不能超出滑杆：${tooltipBounds.left} < ${sliderBounds.left}",
            tooltipBounds.left >= sliderBounds.left - 1f)
    }

    @Test
    fun disabledSliderDoesNotEmitValueChanges() {
        var value by mutableFloatStateOf(5f)
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                GlassSlider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 1f..8f,
                    enabled = false,
                    contentDescription = "转换并发，并发 5 张",
                    valueLabel = "并发 5 张"
                )
            }
        }

        val node = compose.onNodeWithContentDescription("转换并发，并发 5 张")
        val config = node.fetchSemanticsNode().config
        if (config.contains(SemanticsActions.SetProgress)) {
            node.performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        }
        compose.runOnIdle { assertEquals(5f, value) }
    }
}
