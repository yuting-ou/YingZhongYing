package com.vliveconvert.app.ui.motion

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring

internal object MotionTokens {
    val Snappy: SpringSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 620f)
    val Standard: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 420f)
    val Gentle: SpringSpec<Float> = spring(dampingRatio = 0.90f, stiffness = 300f)

    const val FastMs = 120
    const val BaseMs = 180
    const val SlowMs = 240
    const val CompletionMs = 340

    fun stateSizeTransform(reduceMotion: Boolean): SizeTransform =
        if (reduceMotion) SizeTransform { _, _ -> snap() }
        else SizeTransform(clip = false)
}
