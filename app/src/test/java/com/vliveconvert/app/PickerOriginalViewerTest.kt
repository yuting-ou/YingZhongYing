package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.vliveconvert.app.picker.AlbumInfo
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.picker.MediaRepo
import com.vliveconvert.app.picker.PickerScanner
import com.vliveconvert.app.picker.PickerScreen
import com.vliveconvert.app.picker.shouldDismissViewer
import com.vliveconvert.app.ui.LocalReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 放大镜入口：只看原图，不改勾选；查看器里可以连续勾选再返回。
 *
 * 视口必须是真机量级：Robolectric 默认的 320x470dp 比任何在售手机都矮，
 * 首行格子的放大镜热区会落进底部悬浮 Dock 的「添加」按钮之下（点击被 CTA 截走）。
 * 旧断言能过只是因为 12dp 顶距恰好让热区中心差 3dp 躲开按钮——属偶然通过。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h792dp-xxhdpi")
class PickerOriginalViewerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun media(id: Long) = MediaItem(
        id = id,
        path = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
        name = "IMG_$id.jpg",
        bucketId = 7L,
        dateTaken = 1_754_000_000_000L + id * 60_000L,
        dateModified = 1_754_000_000_000L + id,
        size = 1024L
    )

    private fun render(
        items: List<MediaItem>,
        reduceMotion: Boolean = false,
        onParentBack: () -> Unit = {}
    ) {
        val bucket = 7L
        val scanner = PickerScanner(MediaRepo(compose.activity.contentResolver))
        val state = scanner.stateOf(bucket)
        state.results.addAll(items)
        state.total = items.size
        state.doneCount.set(items.size)
        state.completed = true

        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                BackHandler(onBack = onParentBack)
                PickerScreen(
                    albums = listOf(AlbumInfo(bucket, "Camera", items.size, items.first().id, 0L)),
                    scanner = scanner,
                    scannerScope = CoroutineScope(Dispatchers.Unconfined),
                    onBack = {},
                    onConfirm = {}
                )
            }
        }
    }

    @Test
    fun magnifierOpensViewerWithoutSelectingAndViewerCanToggle() {
        render(listOf(media(1), media(2)))

        compose.onNodeWithContentDescription("查看 IMG_1.jpg 原图").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("IMG_1.jpg").assertExists()
        // 网格按拍摄时间倒序，IMG_2 在前：点 IMG_1 是第 2 张
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithText("已选 0 张").assertExists()

        compose.onNodeWithText("选择这张").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("已选 · 取消选择").assertExists()

        compose.onNodeWithContentDescription("关闭预览").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("已选 1 张").assertExists()
        assertTrue(
            "查看器应已关闭",
            compose.onAllNodesWithContentDescription("关闭预览").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun opensAtTappedPhoto() {
        render(listOf(media(1), media(2)))

        compose.onNodeWithContentDescription("查看 IMG_2.jpg 原图").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("IMG_2.jpg").assertExists()
        compose.onNodeWithText("1 / 2").assertExists()
    }

    @Test
    fun sharedTransition_canBeInterruptedByClose() {
        render(listOf(media(1), media(2)))

        compose.onNodeWithContentDescription("查看 IMG_1.jpg 原图").performClick()
        compose.onNodeWithContentDescription("关闭预览").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        assertTrue(
            "快速关闭后查看器必须彻底退出",
            compose.onAllNodesWithContentDescription("关闭预览").fetchSemanticsNodes().isEmpty()
        )
        compose.onNodeWithContentDescription("查看 IMG_1.jpg 原图").assertExists()
        compose.onNodeWithText("2 张双文件实况").assertExists()
    }

    @Test
    fun secondBackDuringViewerExit_reachesParentImmediately() {
        var parentBacks = 0
        render(listOf(media(1)), onParentBack = { parentBacks++ })

        compose.onNodeWithContentDescription("查看 IMG_1.jpg 原图").performClick()
        compose.waitForIdle()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        assertEquals("查看器退场的 120ms 内不得再吞一次返回", 1, parentBacks)
        assertTrue(
            "查看器应已关闭",
            compose.onAllNodesWithContentDescription("关闭预览").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun viewerDismissalUsesDistanceOrVelocity() {
        assertTrue(shouldDismissViewer(200f, 1000f, 0f))
        assertTrue(shouldDismissViewer(20f, 1000f, 1200f))
        assertFalse(shouldDismissViewer(20f, 1000f, 100f))
        assertFalse(shouldDismissViewer(20f, 0f, 1200f))
    }

    @Test
    fun dragDownDismissesViewer() {
        render(listOf(media(1)))
        compose.onNodeWithContentDescription("查看 IMG_1.jpg 原图").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("original_viewer").performTouchInput {
            swipe(
                start = Offset(center.x, 80f),
                end = Offset(center.x, height - 80f),
                durationMillis = 600
            )
        }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()

        assertTrue(
            "下拉超过阈值后查看器必须关闭",
            compose.onAllNodesWithContentDescription("关闭预览").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun reducedMotion_opensViewerWithoutWaitingForTransition() {
        render(listOf(media(1)), reduceMotion = true)

        compose.onNodeWithContentDescription("查看 IMG_1.jpg 原图").performClick()
        compose.mainClock.advanceTimeBy(1)
        compose.waitForIdle()

        compose.onNodeWithText("IMG_1.jpg").assertExists()
        compose.onNodeWithText("选择这张").performClick()
        compose.onNodeWithContentDescription("关闭预览").performClick()
        compose.mainClock.advanceTimeBy(1)
        compose.waitForIdle()
        compose.onNodeWithText("已选 1 张").assertExists()
    }
}
