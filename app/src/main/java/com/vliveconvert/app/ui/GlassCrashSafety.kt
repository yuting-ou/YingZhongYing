package com.vliveconvert.app.ui

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import com.vliveconvert.app.service.PerfSettings

internal object GlassCrashSafety {
    private const val RECOVERY_WINDOW_MS = 15 * 60 * 1000L
    private const val KEY_LAST_NATIVE_CRASH = "glass_compat_last_native_crash"

    fun recoverIfNeeded(
        context: Context,
        settings: PerfSettings
    ): Pair<PerfSettings, Boolean> {
        if (settings.glassCompatibility) return settings to false
        val prefs = context.getSharedPreferences("vliveconvert", Context.MODE_PRIVATE)
        val lastHandled = prefs.getLong(KEY_LAST_NATIVE_CRASH, 0L)
        val now = System.currentTimeMillis()
        val manager = context.getSystemService(ActivityManager::class.java) ?: return settings to false
        val latestNativeCrash = try {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .filter { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }
                .maxByOrNull { it.timestamp }
        } catch (_: Exception) {
            null
        } ?: return settings to false

        if (!shouldAutoEnable(
                reason = latestNativeCrash.reason,
                timestamp = latestNativeCrash.timestamp,
                now = now,
                lastHandled = lastHandled
            )
        ) {
            return settings to false
        }

        prefs.edit().putLong(KEY_LAST_NATIVE_CRASH, latestNativeCrash.timestamp).apply()
        return settings.copy(glassCompatibility = true) to true
    }

    internal fun shouldAutoEnable(
        reason: Int,
        timestamp: Long,
        now: Long,
        lastHandled: Long,
        windowMs: Long = RECOVERY_WINDOW_MS
    ): Boolean =
        reason == ApplicationExitInfo.REASON_CRASH_NATIVE &&
            timestamp > 0L &&
            timestamp <= now &&
            now - timestamp <= windowMs &&
            timestamp != lastHandled
}
