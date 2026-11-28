package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.ui.LocalReduceMotion
import com.vliveconvert.app.ui.animateGlassDispersion
import com.vliveconvert.app.ui.rememberPressScale
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
class GlassPressFeedbackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun harness(reduceMotion: Boolean, onValue: (Float) -> Unit): MutableInteractionSource {
        val source = MutableInteractionSource()
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    val pressed by source.collectIsPressedAsState()
                    val dispersion = animateGlassDispersion(pressed)
                    SideEffect { onValue(dispersion) }
                    Box(Modifier.size(48.dp))
                }
            }
        }
        return source
    }

    @Test
    fun pressRaisesDispersionAndReleaseDropsIt() {
        var latest = -1f
        val source = harness(reduceMotion = false) { latest = it }
        val press = PressInteraction.Press(Offset.Zero)

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { source.tryEmit(press) }
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertTrue("按下后应接近 1，实际 $latest", latest > 0.95f) }

        compose.runOnIdle { source.tryEmit(PressInteraction.Release(press)) }
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertTrue("松开后应接近 0，实际 $latest", latest < 0.05f) }
    }

    @Test
    fun pressScaleUsesPressSpringAndCancelReturnsToRest() {
        val source = MutableInteractionSource()
        var latest = 1f
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                val scale = rememberPressScale(source, pressedScale = 0.96f)
                val observed = scale.value
                SideEffect { latest = observed }
                Box(Modifier.size(48.dp))
            }
        }
        compose.waitForIdle()
        val press = PressInteraction.Press(Offset.Zero)

        var emitted = false
        compose.runOnIdle { emitted = source.tryEmit(press) }
        assertTrue("交互源必须接受 Press", emitted)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertEquals("按下后 scale=$latest", 0.96f, latest, 0.01f) }

        compose.runOnIdle { source.tryEmit(PressInteraction.Cancel(press)) }
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertEquals("取消后必须回到稳定 1", 1f, latest, 0.01f) }
    }

    @Test
    fun reduceMotionSnapsDispersionWithoutSpring() {
        var latest = -1f
        val source = harness(reduceMotion = true) { latest = it }
        val press = PressInteraction.Press(Offset.Zero)

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { source.tryEmit(press) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(1f, latest, 0.001f) }

        compose.runOnIdle { source.tryEmit(PressInteraction.Release(press)) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(0f, latest, 0.001f) }
    }
}
