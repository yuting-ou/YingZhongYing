package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.ui.ConvertItem
import com.vliveconvert.app.ui.LocalReduceMotion
import com.vliveconvert.app.ui.MainScreen
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 主队列 列表/网格 双模式：切换不丢队列内容、偏好持久化、
 * 网格卡片保留状态语义（名称+状态可被读屏读到）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenGridModeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun items(n: Int) = (1..n).map { i ->
        ConvertItem(item = MediaItem(
            i.toLong(), "/sdcard/DCIM/Camera/IMG_$i.jpg", "IMG_$i.jpg",
            1L, 1_754_000_000_000L + i, 1_754_000_000L + i, 1024L * 1024 * 3))
    }

    @Composable
    private fun TestMainScreen(
        queue: List<ConvertItem>,
        converting: Boolean,
        reduceMotion: Boolean = false
    ) {
        VLiveConvertTheme {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                MainScreen(
                    items = queue,
                    appName = "影中影",
                    statusText = "",
                    isConverting = converting,
                    finishedBeat = false,
                    progress = if (converting) 0.5f else 0f,
                    progressDetail = "",
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
                    failedCount = queue.count { it.failed },
                    onRetryFailed = {},
                    onShareItem = {},
                    onRemove = {}
                )
            }
        }
    }

    private fun setContent(n: Int) {
        compose.setContent { TestMainScreen(items(n), converting = false) }
        compose.waitForIdle()
    }

    private fun prefs() = ApplicationProvider.getApplicationContext<VliveApp>()
        .getSharedPreferences("vliveconvert", 0)

    @Test
    fun toggleToGridKeepsItemsAndPersistsPreference() {
        prefs().edit().remove("main_view_mode").commit()
        setContent(5)
        // 默认列表：行名文本可见，网格卡片语义不存在
        compose.onNodeWithText("IMG_1.jpg").assertExists()

        compose.onNodeWithText("网格").performClick()
        compose.waitForIdle()
        assertEquals("grid", prefs().getString("main_view_mode", null))
        // 网格卡片语义 =「名称，状态」
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("IMG_1.jpg，待转换")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodesWithContentDescription("IMG_1.jpg，待转换")
            .fetchSemanticsNodes().isNotEmpty())
        // 队列内容不丢：5 张都在（语义计数）
        assertEquals(5, compose.onAllNodesWithContentDescription("IMG_", substring = true)
            .fetchSemanticsNodes().size)

        // 切回列表：行名文本回来，网格卡片语义消失
        compose.onNodeWithText("列表").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("IMG_1.jpg").assertExists()
        assertTrue(compose.onAllNodesWithContentDescription("IMG_1.jpg，待转换")
            .fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun gridPreferenceSurvivesRecomposition() {
        prefs().edit().putString("main_view_mode", "grid").commit()
        setContent(3)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("IMG_1.jpg，待转换")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("偏好为 grid 时应直接以网格呈现",
            compose.onAllNodesWithContentDescription("IMG_2.jpg，待转换")
                .fetchSemanticsNodes().isNotEmpty())
        // 清理，避免污染其它用例的默认视图
        prefs().edit().remove("main_view_mode").commit()
    }

    @Test
    fun gridConversionShowsBusyAndTerminalFeedback() {
        prefs().edit().putString("main_view_mode", "grid").commit()
        var queue by mutableStateOf(items(1))
        var converting by mutableStateOf(false)
        compose.setContent {
            TestMainScreen(queue = queue, converting = converting, reduceMotion = true)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("IMG_1.jpg，待转换").assertExists()

        compose.runOnIdle {
            queue = listOf(queue.first().copy(status = "转换中…"))
            converting = true
        }
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        compose.onNodeWithTag("grid_convert_busy").assertExists()
        compose.onNodeWithContentDescription("IMG_1.jpg，转换中…").assertExists()

        compose.runOnIdle {
            queue = listOf(queue.first().copy(status = "完成", done = true))
            converting = false
        }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.onNodeWithTag("grid_convert_busy").assertDoesNotExist()
        compose.onNodeWithTag("grid_convert_badge").assertExists()
        compose.onNodeWithContentDescription("IMG_1.jpg，完成").assertExists()

        prefs().edit().remove("main_view_mode").commit()
    }

    @Test
    fun largeBatchLimitsAnimatedGridSpinners() {
        prefs().edit().putString("main_view_mode", "grid").commit()
        val queue = items(24).map { it.copy(status = "转换中…") }
        compose.setContent {
            TestMainScreen(queue = queue, converting = true, reduceMotion = false)
        }
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("动态转换进度")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "24 张同时忙碌时最多一个动态转圈，实际=" +
                compose.onAllNodesWithContentDescription("动态转换进度")
                    .fetchSemanticsNodes().size,
            compose.onAllNodesWithContentDescription("动态转换进度")
                .fetchSemanticsNodes().size <= 1
        )
        prefs().edit().remove("main_view_mode").commit()
    }
}