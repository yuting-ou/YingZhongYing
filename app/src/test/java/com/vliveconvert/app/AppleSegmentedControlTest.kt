package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.ui.AppleSegmentedControl
import com.vliveconvert.app.ui.theme.VLiveConvertTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class AppleSegmentedControlTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exposesSelectionAndKeepsTouchTargetAtLargeFont() {
        val selected = mutableIntStateOf(0)
        var clicks = 0
        compose.setContent {
            VLiveConvertTheme(dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    AppleSegmentedControl(
                        options = listOf("列表", "网格"),
                        selectedIndex = selected.intValue,
                        onSelect = {
                            selected.intValue = it
                            clicks++
                        }
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("列表")
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("网格")
            .assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.waitForIdle()

        assertEquals(1, clicks)
        assertEquals(1, selected.intValue)
        compose.onNodeWithText("网格").assertIsSelected()
    }
}
