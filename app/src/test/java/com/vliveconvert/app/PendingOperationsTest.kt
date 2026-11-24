package com.vliveconvert.app

import com.vliveconvert.app.service.PendingFinalizeEntry
import com.vliveconvert.app.service.PendingOperationsJson
import com.vliveconvert.app.service.PendingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingOperationsTest {

    @Test
    fun snapshotRoundTripPreservesUrisAndFinalizeEntries() {
        val snapshot = PendingSnapshot(
            trashUris = listOf(
                "content://media/external/images/media/11",
                "content://media/external/video/media/12"
            ),
            finalize = listOf(
                PendingFinalizeEntry(
                    uri = "content://media/external/images/media/21",
                    originalName = "IMG_0001.jpg",
                    itemKey = "21:1024:1700000000"
                )
            )
        )

        val restored = PendingOperationsJson.fromJson(PendingOperationsJson.toJson(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun emptySnapshotRoundTripStaysEmpty() {
        val snapshot = PendingSnapshot()

        assertEquals(snapshot, PendingOperationsJson.fromJson(PendingOperationsJson.toJson(snapshot)))
    }

    @Test
    fun corruptJsonReturnsNull() {
        assertNull(PendingOperationsJson.fromJson("not-json"))
        assertNull(PendingOperationsJson.fromJson("[{"))
    }
}
