package com.vliveconvert.app

import com.vliveconvert.app.ui.normalizedSliderProgress
import com.vliveconvert.app.ui.sliderThumbCenterX
import com.vliveconvert.app.ui.sliderTooltipTopPx
import com.vliveconvert.app.ui.sliderTickFractions
import com.vliveconvert.app.ui.sliderVelocityStretch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSliderGeometryTest {

    @Test
    fun progressNormalizesAndClamps() {
        assertEquals(0f, normalizedSliderProgress(-5f, 1f..8f), 0.0001f)
        assertEquals(4f / 7f, normalizedSliderProgress(5f, 1f..8f), 0.0001f)
        assertEquals(1f, normalizedSliderProgress(99f, 1f..8f), 0.0001f)
        assertEquals(0f, normalizedSliderProgress(5f, 3f..3f), 0.0001f)
    }

    @Test
    fun ticksAreEmptyForContinuousAndEvenlySpacedForSteps() {
        assertTrue(sliderTickFractions(0).isEmpty())
        assertEquals(7, sliderTickFractions(6).size)
        assertEquals(0f, sliderTickFractions(6).first(), 0.0001f)
        assertEquals(0.5f, sliderTickFractions(6)[3], 0.0001f)
        assertEquals(1f, sliderTickFractions(6).last(), 0.0001f)
    }

    @Test
    fun thumbCenterStaysInsideTrack() {
        assertEquals(15f, sliderThumbCenterX(300f, 30f, 0f), 0.0001f)
        assertEquals(150f, sliderThumbCenterX(300f, 30f, 0.5f), 0.0001f)
        assertEquals(285f, sliderThumbCenterX(300f, 30f, 1f), 0.0001f)
        assertEquals(10f, sliderThumbCenterX(20f, 30f, 0.5f), 0.0001f)
    }

    @Test
    fun tooltipSitsAboveThumbWithRequestedGap() {
        assertEquals(
            0f,
            sliderTooltipTopPx(
                containerHeightPx = 160f,
                sliderHeightPx = 96f,
                thumbPx = 56f,
                tooltipHeightPx = 64f,
                gapPx = 20f
            ),
            0.0001f
        )
        assertEquals(
            16f,
            sliderTooltipTopPx(
                containerHeightPx = 176f,
                sliderHeightPx = 96f,
                thumbPx = 56f,
                tooltipHeightPx = 64f,
                gapPx = 20f
            ),
            0.0001f
        )
    }

    @Test
    fun velocityStretchIsSymmetricAndClamped() {
        assertEquals(1f to 1f, sliderVelocityStretch(0f))
        val fast = sliderVelocityStretch(20_000f)
        assertEquals(1.1f, fast.first, 0.0001f)
        assertEquals(0.94f, fast.second, 0.0001f)
        assertEquals(fast, sliderVelocityStretch(-20_000f))
    }
}
