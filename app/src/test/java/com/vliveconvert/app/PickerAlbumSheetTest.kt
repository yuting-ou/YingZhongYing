package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.picker.AlbumInfo
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.picker.MediaRepo
import com.vliveconvert.app.picker.PickerScanner
import com.vliveconvert.app.picker.PickerScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.annotation.Config

/**
 * 相册切换面板（玻璃底部面板）：点标题开出面板、列出相册、点选后收起并切换。
 * 旧实现是光秃秃的 DropdownMenuItem，无封面无当前态。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PickerAlbumSheetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var sheetOpen = false
    private var openedWith: Boolean = false

    @Test
    fun albumSheet_opens_listsAlbums_andSwitches() {
        val bucket = 7L
        val scanner = PickerScanner(MediaRepo(compose.activity.contentResolver))
        val state = scanner.stateOf(bucket)
        val items = (1L..6L).map { id ->
            MediaItem(
                id = id, path = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
                name = "IMG_$id.jpg", bucketId = bucket,
                dateTaken = 1_754_000_000_000L + id * 60_000L,
                dateModified = 1_754_000_000L + id, size = 1024L
            )
        }
        state.results.addAll(items)
        state.total = items.size
        state.doneCount.set(items.size)
        state.completed = true

        val albums = listOf(
            AlbumInfo(bucket, "Camera", 6, 1L, 0L),
            AlbumInfo(8L, "Screenshots", 3, 2L, 0L)
        )
        val sheet = mutableStateOf(false)

        compose.setContent {
            PickerScreen(
                albums = albums,
                scanner = scanner,
                scannerScope = CoroutineScope(Dispatchers.Unconfined),
                onBack = {},
                onConfirm = {},
                showAlbumSheet = sheet.value,
                onAlbumSheetChange = {
                    sheet.value = it
                    openedWith = it
                }
            )
        }

        compose.onNodeWithText("Camera · 6 张").performClick()
        compose.waitForIdle()
        assertTrue("点标题必须开出相册面板", sheet.value)
        compose.onNodeWithText("选择相册").assertExists()
        compose.onNodeWithTag("album_chip_7")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("album_chip_8").assertIsNotSelected()
        compose.onNodeWithText("3 张").assertExists()

        compose.onNodeWithTag("album_chip_8").performClick()
        compose.waitForIdle()
        assertEquals("选完必须收起面板", false, sheet.value)
        compose.onNodeWithText("Screenshots · 0 张").assertExists()
    }

    @Test
    fun filterChips_exposeSelectionAndMinimumTouchTarget() {
        val bucket = 7L
        val scanner = PickerScanner(MediaRepo(compose.activity.contentResolver))
        val state = scanner.stateOf(bucket)
        val item = MediaItem(
            id = 1L, path = "/storage/emulated/0/DCIM/Camera/IMG_1.jpg",
            name = "IMG_1.jpg", bucketId = bucket,
            dateTaken = 1_754_000_000_000L, dateModified = 1_754_000_000L, size = 1024L
        )
        state.results.add(item)
        state.total = 1
        state.doneCount.set(1)
        state.completed = true

        compose.setContent {
            PickerScreen(
                albums = listOf(AlbumInfo(bucket, "Camera", 1, item.id, 0L)),
                scanner = scanner,
                scannerScope = CoroutineScope(Dispatchers.Unconfined),
                onBack = {},
                onConfirm = {}
            )
        }
        compose.waitForIdle()

        compose.onNodeWithText("当前相册")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("全部相册").assertIsNotSelected().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("全部相册").assertIsSelected()
        compose.onNodeWithText("当前相册").assertIsNotSelected()
    }

    @Test
    fun albumSheet_dragDownDismisses() {
        val bucket = 7L
        val scanner = PickerScanner(MediaRepo(compose.activity.contentResolver))
        val state = scanner.stateOf(bucket)
        val item = MediaItem(
            id = 1L, path = "/storage/emulated/0/DCIM/Camera/IMG_1.jpg",
            name = "IMG_1.jpg", bucketId = bucket,
            dateTaken = 1_754_000_000_000L, dateModified = 1_754_000_000L, size = 1024L
        )
        state.results.add(item)
        state.total = 1
        state.doneCount.set(1)
        state.completed = true
        val sheet = mutableStateOf(true)

        compose.setContent {
            PickerScreen(
                albums = listOf(AlbumInfo(bucket, "Camera", 1, item.id, 0L)),
                scanner = scanner,
                scannerScope = CoroutineScope(Dispatchers.Unconfined),
                onBack = {},
                onConfirm = {},
                showAlbumSheet = sheet.value,
                onAlbumSheetChange = { sheet.value = it }
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("album_sheet").performTouchInput {
            swipe(
                start = Offset(center.x, 10f),
                end = Offset(center.x, height - 10f),
                durationMillis = 600
            )
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        assertFalse("向下拖过阈值必须关闭相册面板", sheet.value)
    }
}
