package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vliveconvert.app.service.GlassQuality
import com.vliveconvert.app.ui.GlassLevel
import com.vliveconvert.app.ui.GlassSafety
import com.vliveconvert.app.ui.LocalGlassQuality
import com.vliveconvert.app.ui.LocalGlassScrollPaused
import com.vliveconvert.app.ui.glassFill
import com.vliveconvert.app.ui.glassLevelOptics
import com.vliveconvert.app.ui.glassOptics
import com.vliveconvert.app.ui.shouldAnimateSkeleton
import com.vliveconvert.app.ui.shouldAnimateThumbnailPulse
import com.vliveconvert.app.ui.isEmulatorBuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 玻璃光学成本系数（画质档 × 滚动降成本）的纯组合逻辑合同。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlassOpticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun optics(q: GlassQuality, paused: Boolean): Triple<Float, Float, Boolean> {
        var result: Triple<Float, Float, Boolean>? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalGlassQuality provides q,
                LocalGlassScrollPaused provides paused
            ) { result = glassOptics() }
        }
        compose.waitForIdle()
        return result!!
    }

    @Test
    fun emulatorBuildsUseGlassFallback() {
        assertTrue(isEmulatorBuild("Android/sdk_phone64_arm64/emu64a:15", "Android SDK built for arm64", "sdk_phone64_arm64"))
        assertFalse(isEmulatorBuild("vivo/PD2454", "V2454DA", "PD2454"))
    }

    @Test
    fun standardUnpausedIsFullCostZeroRegression() {
        assertEquals(Triple(1f, 1f, true), optics(GlassQuality.Standard, paused = false))
    }

    @Test
    fun saverDropsLensEntirelyButKeepsBlur() {
        // 去折射保模糊：lens=0（渲染层据此整体跳过 lens），blur 保留降半径
        assertEquals(Triple(0.6f, 0f, false), optics(GlassQuality.Saver, paused = false))
    }

    @Test
    fun scrollPauseScalesCostWithoutDroppingGlass() {
        // 滚动降成本：模糊/折射降档、色散关，但都不归零——玻璃观感不丢
        assertEquals(Triple(0.5f, 0.8f, false), optics(GlassQuality.Standard, paused = true))
    }

    @Test
    fun saverPausedStacksMultiplicatively() {
        // 流畅档 × 滚动暂停：两个系数相乘，折射仍整体关闭
        assertEquals(Triple(0.3f, 0f, false), optics(GlassQuality.Saver, paused = true))
    }

    @Test
    fun noProviderDefaultsToFullCost() {
        // 未提供档位/滚动信号的页面（如启动页）必须等价于标准档不滚动
        var result: Triple<Float, Float, Boolean>? = null
        compose.setContent { result = glassOptics() }
        compose.waitForIdle()
        assertEquals(Triple(1f, 1f, true), result)
    }

    @Test
    fun thinNeverSelectsBackdrop() {
        assertFalse(GlassSafety.allowsBackdrop(GlassLevel.Thin, 240f, hardwareAvailable = true))
    }

    @Test
    fun materialLevelsRaiseOpacityAndDimmingMonotonically() {
        val thin = glassFill(GlassLevel.Thin, dark = false, hazeEnabled = true, dense = false)
        val regular = glassFill(GlassLevel.Regular, dark = false, hazeEnabled = true, dense = false)
        val thick = glassFill(GlassLevel.Thick, dark = false, hazeEnabled = true, dense = false)
        assertTrue(thin.top < regular.top && regular.top < thick.top)
        assertTrue(thin.bottom < regular.bottom && regular.bottom < thick.bottom)
        assertTrue(thin.dimming < regular.dimming && regular.dimming < thick.dimming)
    }

    @Test
    fun regularMaterialPreservesLegacyHazeFill() {
        val light = glassFill(GlassLevel.Regular, dark = false, hazeEnabled = true, dense = false)
        val dark = glassFill(GlassLevel.Regular, dark = true, hazeEnabled = true, dense = false)
        assertEquals(0.30f, light.top, 0.001f)
        assertEquals(0.10f, light.bottom, 0.001f)
        assertEquals(0.56f, dark.top, 0.001f)
        assertEquals(0.42f, dark.bottom, 0.001f)
    }

    @Test
    fun thickMaterialIsMoreDiffusedButLessRefracted() {
        val regular = glassLevelOptics(GlassLevel.Regular)
        val thick = glassLevelOptics(GlassLevel.Thick)
        assertTrue(thick.blurDp > regular.blurDp)
        assertTrue(thick.refractionAmountDp < regular.refractionAmountDp)
        assertTrue(thick.highlightAlpha > regular.highlightAlpha)
    }

    @Test
    fun bcMaterialEdgeDispersionIsSemanticNotRainbow() {
        val thin = glassLevelOptics(GlassLevel.Thin)
        val regular = glassLevelOptics(GlassLevel.Regular)
        val thick = glassLevelOptics(GlassLevel.Thick)
        assertEquals(0f, thin.edgeDispersion, 0.001f)
        assertTrue(regular.edgeDispersion in 0.25f..0.45f)
        assertTrue(thick.edgeDispersion in 0.55f..0.75f)
        assertTrue(thick.edgeDispersion > regular.edgeDispersion)
    }

    @Test
    fun scrollAndReduceMotionStopInfinitePlaceholderMotion() {
        assertTrue(shouldAnimateThumbnailPulse(reduceMotion = false, scrollPaused = false))
        assertFalse(shouldAnimateThumbnailPulse(reduceMotion = true, scrollPaused = false))
        assertFalse(shouldAnimateThumbnailPulse(reduceMotion = false, scrollPaused = true))
        assertTrue(shouldAnimateSkeleton(reduceMotion = false, scrollPaused = false))
        assertFalse(shouldAnimateSkeleton(reduceMotion = true, scrollPaused = false))
        assertFalse(shouldAnimateSkeleton(reduceMotion = false, scrollPaused = true))
    }
}