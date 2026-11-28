package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.ui.GlassEmptyErrorState
import com.vliveconvert.app.ui.GlassLoadingSkeleton
import com.vliveconvert.app.ui.LocalReduceMotion
import com.vliveconvert.app.ui.StateCrossfade
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StateUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private enum class ScreenState { Loading, Empty, Error, Success }

    @Test
    fun stateCrossfade_keepsOnlyTargetStateAfterTransition() {
        val state = mutableStateOf(ScreenState.Loading)
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                StateCrossfade(targetState = state.value) { current ->
                    Box(Modifier.size(if (current == ScreenState.Success) 180.dp else 120.dp)) {
                        Text(
                            when (current) {
                                ScreenState.Loading -> "加载中"
                                ScreenState.Empty -> "空态"
                                ScreenState.Error -> "错误态"
                                ScreenState.Success -> "成功态"
                            }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("加载中").assertExists()

        compose.runOnIdle { state.value = ScreenState.Empty }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("空态").assertExists()
        compose.onNodeWithText("加载中").assertDoesNotExist()

        compose.runOnIdle { state.value = ScreenState.Error }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("错误态").assertExists()
        compose.onNodeWithText("空态").assertDoesNotExist()

        compose.runOnIdle { state.value = ScreenState.Success }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("成功态").assertExists()
        compose.onNodeWithText("错误态").assertDoesNotExist()
    }

    @Test
    fun emptyErrorStateExposesHeadingStateAndAccessibleAction() {
        var clicks = 0
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                GlassEmptyErrorState(
                    title = "扫描相册失败",
                    description = "请检查存储权限",
                    error = true,
                    actionLabel = "重新扫描",
                    onAction = { clicks++ }
                )
            }
        }
        compose.waitForIdle()

        val title = compose.onNodeWithText("扫描相册失败").fetchSemanticsNode()
        org.junit.Assert.assertEquals(
            "错误",
            title.config[SemanticsProperties.StateDescription]
        )
        org.junit.Assert.assertTrue(
            "状态标题应作为读屏标题",
            title.config.contains(SemanticsProperties.Heading)
        )
        compose.onNodeWithText("重新扫描")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        org.junit.Assert.assertEquals(1, clicks)
    }

    @Test
    fun reducedMotion_snapsStateAndRendersStaticSkeleton() {
        val state = mutableStateOf(ScreenState.Loading)
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    StateCrossfade(targetState = state.value) { current ->
                        Text(if (current == ScreenState.Loading) "加载中" else "空态")
                    }
                    GlassLoadingSkeleton()
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        val skeleton = compose.onNodeWithTag("glass_loading_skeleton").fetchSemanticsNode()
        org.junit.Assert.assertEquals(
            ProgressBarRangeInfo.Indeterminate,
            skeleton.config[SemanticsProperties.ProgressBarRangeInfo]
        )

        compose.runOnIdle { state.value = ScreenState.Empty }
        compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()
        compose.onNodeWithText("空态").assertExists()
        compose.onNodeWithText("加载中").assertDoesNotExist()
    }
}
