package com.vliveconvert.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.ui.motion.MotionTokens

@Composable
internal fun <T> StateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "state",
    animateSize: Boolean = true,
    content: @Composable AnimatedContentScope.(T) -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val fade: FiniteAnimationSpec<Float> = if (reduceMotion) snap()
            else tween(MotionTokens.BaseMs, easing = EaseOutStrong)
            (fadeIn(fade) togetherWith fadeOut(fade))
                .using(MotionTokens.stateSizeTransform(reduceMotion || !animateSize))
        },
        label = label
    ) { content(it) }
}

internal fun shouldAnimateSkeleton(
    reduceMotion: Boolean,
    scrollPaused: Boolean,
    compatibility: Boolean = false
): Boolean = MotionTokens.motionBudgetAllowsInfinite(reduceMotion, scrollPaused, compatibility)

@Composable
internal fun GlassLoadingSkeleton(modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val scrollPaused = LocalGlassScrollPaused.current
    val compatibility = LocalGlassCompatibility.current
    val shimmer = if (shouldAnimateSkeleton(reduceMotion, scrollPaused, compatibility)) {
        rememberInfiniteTransition(label = "stateSkeleton").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "stateSkeletonPhase"
        )
    } else null
    val base = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
    val highlight = Color.White.copy(alpha = if (isDarkGlass()) 0.10f else 0.34f)

    Column(
        modifier
            .testTag("glass_loading_skeleton")
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(0.94f, 0.78f, 0.58f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(continuousShape(7.dp))
                    .background(base)
                    .drawBehind {
                        val phase = shimmer?.value ?: return@drawBehind
                        val band = size.width * 0.42f
                        val start = (size.width + band) * phase - band
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                                startX = start,
                                endX = start + band
                            )
                        )
                    }
            )
        }
    }
}

@Composable
internal fun GlassEmptyErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (error) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(4.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(10.dp))
            InlineTextButton(
                text = actionLabel,
                contentColor = MaterialTheme.colorScheme.primary,
                onClick = onAction
            )
        }
    }
}
