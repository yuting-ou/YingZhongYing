package com.vliveconvert.app

import androidx.compose.animation.core.SnapSpec
import androidx.compose.ui.unit.IntSize
import com.vliveconvert.app.ui.motion.MotionTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {

    @Test
    fun durationsAreStableAndBrief() {
        assertEquals(120, MotionTokens.FastMs)
        assertEquals(180, MotionTokens.BaseMs)
        assertEquals(240, MotionTokens.SlowMs)
        assertEquals(340, MotionTokens.CompletionMs)
    }

    @Test
    fun springTiersMatchInteractionWeight() {
        assertEquals(0.72f, MotionTokens.Snappy.dampingRatio, 0.001f)
        assertEquals(620f, MotionTokens.Snappy.stiffness, 0.001f)
        assertEquals(0.82f, MotionTokens.Standard.dampingRatio, 0.001f)
        assertEquals(420f, MotionTokens.Standard.stiffness, 0.001f)
        assertEquals(0.90f, MotionTokens.Gentle.dampingRatio, 0.001f)
        assertEquals(300f, MotionTokens.Gentle.stiffness, 0.001f)
    }

    @Test
    fun reducedStateSizeTransformUsesSnap() {
        val spec = MotionTokens.stateSizeTransform(reduceMotion = true)
            .createAnimationSpec(IntSize.Zero, IntSize(100, 100))
        assertTrue("减少动效的尺寸切换必须瞬时", spec is SnapSpec<*>)
    }
}
