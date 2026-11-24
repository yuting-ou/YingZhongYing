package com.vliveconvert.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.service.ConvertCenter
import com.vliveconvert.app.service.PendingFinalizeEntry
import com.vliveconvert.app.service.PendingResumeAction
import com.vliveconvert.app.ui.ConvertItem
import com.vliveconvert.app.service.PendingOperationsStore
import com.vliveconvert.app.service.PendingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingOperationsStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<VliveApp>()

    private fun snapshot(seq: Int) = PendingSnapshot(
        trashUris = listOf("content://media/external/images/media/$seq"),
        finalize = listOf(
            PendingFinalizeEntry(
                uri = "content://media/external/images/media/${seq + 100}",
                originalName = "IMG_$seq.jpg",
                itemKey = "$seq:1024:1700000000"
            )
        )
    )

    private fun clearMemory() {
        ConvertCenter.pendingTrashUris.clear()
        ConvertCenter.pendingFinalize.clear()
        ConvertCenter.pendingTrashCount = 0
        ConvertCenter.items.clear()
        ConvertCenter.isConverting = false
        ConvertCenter.isFinalizing = false
    }

    @Before
    fun reset() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        clearMemory()
        PendingOperationsStore(context).clear()
    }

    @After
    fun tearDown() {
        clearMemory()
        PendingOperationsStore(context).clear()
        Dispatchers.resetMain()
    }

    @Test
    fun saveThenLoadRestoresSnapshot() {
        val store = PendingOperationsStore(context)
        store.clear()

        assertTrue(store.save(snapshot(1)))
        assertEquals(snapshot(1), store.load())
    }

    @Test
    fun secondSaveReplacesFirstSnapshot() {
        val store = PendingOperationsStore(context)
        store.save(snapshot(1))

        assertTrue(store.save(snapshot(2)))
        assertEquals(snapshot(2), store.load())
    }

    @Test
    fun clearRemovesPersistedSnapshot() {
        val store = PendingOperationsStore(context)
        store.save(snapshot(1))

        store.clear()

        assertEquals(PendingSnapshot(), store.load())
    }

    @Test
    fun corruptFileDegradesToEmptySnapshot() {
        val store = PendingOperationsStore(context)
        store.clear()
        File(context.filesDir, "pending_operations.json").writeText("broken")

        assertEquals(PendingSnapshot(), store.load())
    }

    @Test
    fun failedWriteLeavesPreviousSnapshotIntact() {
        val store = PendingOperationsStore(context)
        store.clear()
        assertTrue(store.save(snapshot(1)))
        val filesDir = context.filesDir
        val oldWritable = filesDir.setWritable(false, false)
        try {
            if (!oldWritable) return
            store.save(snapshot(2))
        } finally {
            filesDir.setWritable(true, false)
        }

        assertEquals(snapshot(1), store.load())
    }

    @Test
    fun persistClearMemoryAndRestoreRecoversPendingOperations() {
        ConvertCenter.pendingTrashUris.add(Uri.parse("content://media/external/images/media/1"))
        ConvertCenter.pendingTrashUris.add(Uri.parse("content://media/external/video/media/2"))
        ConvertCenter.pendingFinalize.add(ConvertCenter.FinalizeEntry(
            Uri.parse("content://media/external/images/media/101"), "IMG_1.jpg", "1:1024:1700000000"))
        assertTrue(ConvertCenter.persistPendingOperations(context))
        clearMemory()

        val restored = runBlocking { ConvertCenter.restorePendingOperations(context) }

        assertEquals(2, restored)
        assertEquals(2, ConvertCenter.pendingTrashUris.size)
        assertEquals(1, ConvertCenter.pendingFinalize.size)
        assertEquals(2, ConvertCenter.pendingTrashCount)
    }

    @Test
    fun restoringTwiceDoesNotDuplicatePendingOperations() {
        ConvertCenter.pendingTrashUris.add(Uri.parse("content://media/external/images/media/1"))
        ConvertCenter.pendingFinalize.add(ConvertCenter.FinalizeEntry(
            Uri.parse("content://media/external/images/media/101"), "IMG_1.jpg", "1:1024:1700000000"))
        ConvertCenter.persistPendingOperations(context)
        clearMemory()

        runBlocking { ConvertCenter.restorePendingOperations(context) }
        runBlocking { ConvertCenter.restorePendingOperations(context) }

        assertEquals(1, ConvertCenter.pendingTrashUris.size)
        assertEquals(1, ConvertCenter.pendingFinalize.size)
    }

    @Test
    fun checkpointRemovesOnlyProcessedFinalizeEntry() {
        ConvertCenter.pendingTrashUris.add(Uri.parse("content://media/external/images/media/1"))
        ConvertCenter.pendingFinalize.add(ConvertCenter.FinalizeEntry(
            Uri.parse("content://media/external/images/media/101"), "IMG_1.jpg", "key-1"))
        ConvertCenter.pendingFinalize.add(ConvertCenter.FinalizeEntry(
            Uri.parse("content://media/external/images/media/102"), "IMG_2.jpg", "key-2"))
        ConvertCenter.persistPendingOperations(context)

        ConvertCenter.checkpointPendingFinalize(context, "key-1")

        assertEquals(listOf("key-2"), ConvertCenter.pendingFinalize.map { it.itemKey })
        val saved = PendingOperationsStore(context).load()
        assertEquals(listOf("key-2"), saved.finalize.map { it.itemKey })
        assertEquals(1, saved.trashUris.size)
    }

    @Test
    fun restoreCanSkipTrashWhenActivityHasSavedDialogState() {
        PendingOperationsStore(context).save(snapshot(1))

        val restored = runBlocking { ConvertCenter.restorePendingOperations(context, restoreTrash = false) }

        assertEquals(0, restored)
        assertTrue(ConvertCenter.pendingTrashUris.isEmpty())
        assertEquals(1, ConvertCenter.pendingFinalize.size)
    }

    @Test
    fun checkpointFailureKeepsBothMemoryAndDiskEntry() {
        ConvertCenter.pendingFinalize.add(ConvertCenter.FinalizeEntry(
            Uri.parse("content://media/external/images/media/101"), "IMG_1.jpg", "key-1"))
        ConvertCenter.persistPendingOperations(context)
        val filesDir = context.filesDir
        val oldWritable = filesDir.setWritable(false, false)
        try {
            if (!oldWritable) return
            assertFalse(ConvertCenter.checkpointPendingFinalize(context, "key-1"))
        } finally {
            filesDir.setWritable(true, false)
        }

        assertEquals(listOf("key-1"), ConvertCenter.pendingFinalize.map { it.itemKey })
        assertEquals(listOf("key-1"), PendingOperationsStore(context).load().finalize.map { it.itemKey })
    }

    @Test
    fun pendingResumePrefersDeleteConfirmationThenFinalizeOnly() {
        assertEquals(PendingResumeAction.NONE, ConvertCenter.pendingResumeAction())

        ConvertCenter.pendingFinalize.add(ConvertCenter.FinalizeEntry(
            Uri.parse("content://media/external/images/media/101"), "IMG_1.jpg", "key-1"))
        assertEquals(PendingResumeAction.FINALIZE, ConvertCenter.pendingResumeAction())

        ConvertCenter.pendingTrashUris.add(Uri.parse("content://media/external/images/media/1"))
        assertEquals(PendingResumeAction.DELETE, ConvertCenter.pendingResumeAction())
    }

    @Test
    fun finalizingBlocksStartingANewConversionBatch() {
        ConvertCenter.items.add(ConvertItem(item = MediaItem(
            id = 1,
            path = "/sdcard/IMG_1.jpg",
            name = "IMG_1.jpg",
            bucketId = 1,
            dateTaken = 0,
            dateModified = 0,
            size = 1
        )))
        ConvertCenter.isFinalizing = true

        assertFalse(ConvertCenter.start(context))
        assertFalse(ConvertCenter.isConverting)
    }
}
