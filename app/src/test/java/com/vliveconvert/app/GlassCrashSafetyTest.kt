package com.vliveconvert.app

import android.app.ApplicationExitInfo
import com.vliveconvert.app.ui.GlassCrashSafety
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassCrashSafetyTest {

    @Test
    fun recentUnhandledNativeCrashEnablesCompatibility() {
        val now = 2_000_000L
        assertTrue(
            GlassCrashSafety.shouldAutoEnable(
                reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
                timestamp = now - 60_000L,
                now = now,
                lastHandled = 0L
            )
        )
    }

    @Test
    fun handledCrashDoesNotEnableAgain() {
        val now = 2_000_000L
        val crash = now - 60_000L
        assertFalse(
            GlassCrashSafety.shouldAutoEnable(
                reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
                timestamp = crash,
                now = now,
                lastHandled = crash
            )
        )
    }

    @Test
    fun oldOrNonNativeCrashIsIgnored() {
        val now = 2_000_000L
        assertFalse(
            GlassCrashSafety.shouldAutoEnable(
                reason = ApplicationExitInfo.REASON_CRASH,
                timestamp = now - 60_000L,
                now = now,
                lastHandled = 0L
            )
        )
        assertFalse(
            GlassCrashSafety.shouldAutoEnable(
                reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
                timestamp = now - 20 * 60 * 1000L,
                now = now,
                lastHandled = 0L
            )
        )
    }
}
