package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.vliveconvert.app.picker.AlbumInfo
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.picker.MediaRepo
import com.vliveconvert.app.picker.PickerScanner
import com.vliveconvert.app.picker.PickerScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 流式扫描把「今天」分区插到列表最前时，视口必须停在最新分区上。
 *
 * LazyGrid 默认按原首个可见项的 key 锚定：新分区会被顶到视口上方、藏进
 * 悬浮「选择照片」面板后面（真机实拍复现）。这里断言新日期头位于工具行下方，
 * 而不是被面板盖住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h792dp-xxhdpi")
class PickerStreamingAnchorTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun media(id: Long, time: Long) = MediaItem(
        id = id,
        path = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
        name = "IMG_$id.jpg",
        bucketId = 7L,
        dateTaken = time,
        dateModified = time / 1000,
        size = 1024L
    )

    @Test
    fun newTopSectionStaysBelowHeaderPanel() {
        val bucket = 7L
        val scanner = PickerScanner(MediaRepo(compose.activity.contentResolver))
        val state = scanner.stateOf(bucket)
        // 必须超过一屏：内容不溢出时 LazyGrid 无处可锚，构造不出真机上的遮挡
        val oldItems = (1L..60L).map { media(it, 1_754_000_000_000L + it * 60_000L) }
        state.results.addAll(oldItems)
        state.total = oldItems.size
        state.doneCount.set(oldItems.size)
        state.completed = true

        compose.setContent {
            PickerScreen(
                albums = listOf(AlbumInfo(bucket, "Camera", oldItems.size, oldItems.first().id, 0L)),
                scanner = scanner,
                scannerScope = CoroutineScope(Dispatchers.Unconfined),
                onBack = {},
                onConfirm = {}
            )
        }
        compose.waitForIdle()

        val toolsBottom = compose.onNodeWithText("全选")
            .fetchSemanticsNode().boundsInRoot.bottom

        // 扫描线程在 Main 上逐张追加：模拟「今天」新照片流入
        state.results.add(media(99L, System.currentTimeMillis()))
        compose.waitForIdle()

        val today = compose.onNodeWithText("今天").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "「今天」日期头应位于悬浮面板下方（top=${today.top}px vs 工具行底=${toolsBottom}px）",
            today.top > toolsBottom
        )
    }

    @Test
    fun doesNotYankViewportAfterUserScrolledAway() {
        val bucket = 7L
        val scanner = PickerScanner(MediaRepo(compose.activity.contentResolver))
        val state = scanner.stateOf(bucket)
        val oldItems = (1L..60L).map { media(it, 1_754_000_000_000L + it * 60_000L) }
        state.results.addAll(oldItems)
        state.total = oldItems.size
        state.doneCount.set(oldItems.size)
        state.completed = true

        compose.setContent {
            PickerScreen(
                albums = listOf(AlbumInfo(bucket, "Camera", oldItems.size, oldItems.first().id, 0L)),
                scanner = scanner,
                scannerScope = CoroutineScope(Dispatchers.Unconfined),
                onBack = {},
                onConfirm = {}
            )
        }
        compose.waitForIdle()

        // 用户下滑到旧照片里：分区头（60 张）离屏
        repeat(2) {
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        assertTrue(
            "前置条件：应已滚离顶部分区",
            compose.onAllNodesWithText("60 张").fetchSemanticsNodes().isEmpty()
        )

        state.results.add(media(99L, System.currentTimeMillis()))
        compose.waitForIdle()

        // 用户已离开顶部：新分区不得抢滚动把视口拽回去
        assertTrue(
            "用户已下滑时不应被拽回顶部",
            compose.onAllNodesWithText("今天").fetchSemanticsNodes().isEmpty()
        )
    }
}
