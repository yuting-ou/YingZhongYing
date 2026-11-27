package com.vliveconvert.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vliveconvert.app.ui.RepairBackHandler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepairBackHandlerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun disabledChildBackFallsThroughToParent() {
        val childEnabled = mutableStateOf(true)
        var parentBacks = 0
        compose.setContent {
            BackHandler { parentBacks++ }
            BackHandler(enabled = childEnabled.value) { }
        }
        compose.waitForIdle()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(0, parentBacks)
        compose.runOnIdle { childEnabled.value = false }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, parentBacks)
    }

    @Test
    fun busyRepairConsumesBackAndIdleRepairReturns() {
        val busy = mutableStateOf(true)
        var backs = 0
        compose.setContent {
            RepairBackHandler(busy = busy.value, onBack = { backs++ })
        }
        compose.waitForIdle()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertEquals("修复写入中返回键只能被消费", 0, backs)

        compose.runOnIdle { busy.value = false }
        compose.waitForIdle()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertEquals("修复完成后返回键恢复", 1, backs)
    }
}
