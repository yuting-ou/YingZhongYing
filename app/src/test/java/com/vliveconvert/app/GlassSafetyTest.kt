package com.vliveconvert.app

import com.vliveconvert.app.ui.GlassLevel
import com.vliveconvert.app.ui.GlassSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSafetyTest {

    @Test
    fun backdropRequiresAtLeastNinetySixDpShortSide() {
        assertFalse(GlassSafety.canUseBackdrop(95.99f))
        assertTrue(GlassSafety.canUseBackdrop(96f))
        assertTrue(GlassSafety.canUseBackdrop(140f))
    }

    @Test
    fun levelResolutionPreservesExistingCallers() {
        assertEquals(GlassLevel.Regular, GlassSafety.resolveLevel(dense = false, liquid = false, level = null))
        assertEquals(GlassLevel.Thick, GlassSafety.resolveLevel(dense = true, liquid = false, level = null))
        assertEquals(GlassLevel.Thin, GlassSafety.resolveLevel(dense = false, liquid = false, level = GlassLevel.Thin))
        assertEquals(GlassLevel.Thick, GlassSafety.resolveLevel(dense = false, liquid = true, level = GlassLevel.Thick))
    }

    @Test
    fun thinNeverAllowsBackdrop() {
        assertFalse(GlassSafety.allowsBackdrop(GlassLevel.Thin, shortSideDp = 300f, hardwareAvailable = true))
        assertTrue(GlassSafety.allowsBackdrop(GlassLevel.Regular, shortSideDp = 96f, hardwareAvailable = true))
        assertFalse(GlassSafety.allowsBackdrop(GlassLevel.Thick, shortSideDp = 95f, hardwareAvailable = true))
        assertFalse(GlassSafety.allowsBackdrop(GlassLevel.Thick, shortSideDp = 200f, hardwareAvailable = false))
    }
}
