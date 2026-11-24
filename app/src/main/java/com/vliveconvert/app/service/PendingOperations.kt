package com.vliveconvert.app.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class PendingFinalizeEntry(
    val uri: String,
    val originalName: String,
    val itemKey: String
)

internal data class PendingSnapshot(
    val trashUris: List<String> = emptyList(),
    val finalize: List<PendingFinalizeEntry> = emptyList()
)

internal object PendingOperationsJson {
    fun toJson(snapshot: PendingSnapshot): String {
        val trash = JSONArray()
        snapshot.trashUris.forEach { trash.put(it) }
        val finalize = JSONArray()
        snapshot.finalize.forEach { entry ->
            finalize.put(JSONObject()
                .put("uri", entry.uri)
                .put("originalName", entry.originalName)
                .put("itemKey", entry.itemKey))
        }
        return JSONObject()
            .put("trashUris", trash)
            .put("finalize", finalize)
            .toString()
    }

    fun fromJson(text: String): PendingSnapshot? = try {
        val root = JSONObject(text)
        val trashJson = root.optJSONArray("trashUris") ?: JSONArray()
        val trash = (0 until trashJson.length()).map { trashJson.getString(it) }
        val finalizeJson = root.optJSONArray("finalize") ?: JSONArray()
        val finalize = (0 until finalizeJson.length()).mapNotNull { index ->
            val item = finalizeJson.optJSONObject(index) ?: return@mapNotNull null
            PendingFinalizeEntry(
                uri = item.optString("uri"),
                originalName = item.optString("originalName"),
                itemKey = item.optString("itemKey")
            )
        }
        PendingSnapshot(trash, finalize)
    } catch (_: Exception) {
        null
    }
}

internal class PendingOperationsStore(private val context: Context) {
    private val target = File(context.filesDir, FILE_NAME)

    fun load(): PendingSnapshot {
        if (!target.exists()) return PendingSnapshot()
        return try {
            PendingOperationsJson.fromJson(target.readText()) ?: PendingSnapshot()
        } catch (_: Exception) {
            PendingSnapshot()
        }
    }

    fun save(snapshot: PendingSnapshot): Boolean = save { snapshot }

    fun save(supplier: () -> PendingSnapshot): Boolean = synchronized(lock) {
        val snapshot = supplier()
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            tmp.writeText(PendingOperationsJson.toJson(snapshot))
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (_: Exception) {
            try { tmp.delete() } catch (_: Exception) {}
            false
        }
    }

    fun clear(): Boolean = save(PendingSnapshot())

    private companion object {
        const val FILE_NAME = "pending_operations.json"
        val lock = Any()
    }
}
