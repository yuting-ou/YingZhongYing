package com.vliveconvert.app

import com.vliveconvert.app.picker.MediaItem
import com.vliveconvert.app.service.mergeClearedUndo
import com.vliveconvert.app.service.mergeRestored
import com.vliveconvert.app.ui.ConvertItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueMergeScaleTest {

    private fun entry(id: Long) = ConvertItem(
        item = MediaItem(
            id = id,
            path = "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg",
            name = "IMG_$id.jpg",
            bucketId = 1L,
            dateTaken = 1_754_000_000_000L + id,
            dateModified = 1_754_000_000L + id,
            size = 1024L + id
        )
    )

    private class IterationCountingList<T>(
        private val delegate: List<T>
    ) : List<T> by delegate {
        var iterations = 0
            private set

        override fun iterator(): Iterator<T> {
            iterations++
            return delegate.iterator()
        }
    }

    @Test
    fun restoreMergeTraversesCurrentHistoryOnlyConstantTimes() {
        val current = IterationCountingList((1L..20_000L).map(::entry))
        val parsed = listOf(entry(20_001), entry(20_002), entry(20_003))

        val merged = mergeRestored(parsed, current)

        assertEquals(20_003, merged.size)
        assertTrue(
            "恢复合并把当前队列重扫了 ${current.iterations} 次；应为常数次而不是随历史数量增长",
            current.iterations <= 2
        )
    }

    @Test
    fun undoMergeTraversesCurrentQueueOnlyConstantTimes() {
        val current = IterationCountingList(listOf(entry(20_001)))
        val cleared = (1L..20_000L).map(::entry)

        val merged = mergeClearedUndo(cleared, current)

        assertEquals(20_001, merged.size)
        assertEquals(1L, merged.first().item.id)
        assertEquals(20_001L, merged.last().item.id)
        assertTrue(
            "撤销合并把当前队列重扫了 ${current.iterations} 次；应为常数次而不是随清空数量增长",
            current.iterations <= 2
        )
    }
}
