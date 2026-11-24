package com.vliveconvert.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.vliveconvert.app.service.PerfPreset

internal fun presetTextColor(
    selection: Float,
    base: Color,
    primary: Color,
    onSurface: Color,
    onPrimary: Color
): Color {
    val background = lerp(base, primary, selection.coerceIn(0f, 1f))
    fun contrast(foreground: Color): Float {
        val a = foreground.luminance()
        val b = background.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
    return if (contrast(onSurface) >= contrast(onPrimary)) onSurface else onPrimary
}

@Composable
internal fun GlassPresetSelector(
    selected: PerfPreset?,
    onSelect: (PerfPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PerfPreset.entries.forEach { preset ->
            GlassPresetChip(
                preset = preset,
                selected = selected == preset,
                onClick = { onSelect(preset) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GlassPresetChip(
    preset: PerfPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "presetSelection"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "presetPress"
    )
    val backdrop = LocalGlassBackdrop.current
    val dark = isDarkGlass()
    val (blurScale, lensScale, chromaticOn) = glassOptics()
    val shape = continuousShape(24.dp)
    val base = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
    val primary = MaterialTheme.colorScheme.primary
    val textColor = presetTextColor(
        selection = selection,
        base = base,
        primary = primary,
        onSurface = MaterialTheme.colorScheme.onSurface,
        onPrimary = MaterialTheme.colorScheme.onPrimary
    )

    val visual = modifier
        .height(48.dp)
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .clip(shape)

    val container = if (backdrop != null && hazeAvailable()) {
        visual.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                blur(14.dp.toPx() * blurScale)
                if (lensScale > 0f) {
                    lens(
                        refractionHeight = (18f + 8f * selection).dp.toPx() * lensScale,
                        refractionAmount = (30f + 14f * selection).dp.toPx() * lensScale,
                        depthEffect = true,
                        chromaticAberration = chromaticOn
                    )
                }
            },
            highlight = {
                Highlight.Default.copy(
                    width = 1.1.dp,
                    alpha = if (dark) 0.46f + 0.22f * selection else 0.56f + 0.24f * selection
                )
            },
            shadow = {
                Shadow(
                    radius = 12.dp,
                    color = Color.Black.copy(alpha = if (dark) 0.26f else 0.10f)
                )
            },
            onDrawSurface = {
                drawRect(base.copy(alpha = if (dark) 0.16f else 0.12f))
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            primary.copy(alpha = selection),
                            primary.copy(alpha = selection)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.6f
                    )
                )
            }
        )
    } else {
        visual
            .background(base.copy(alpha = if (dark) 0.78f else 0.68f))
            .background(primary.copy(alpha = selection))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.24f else 0.72f),
                        Color.White.copy(alpha = if (dark) 0.06f else 0.20f)
                    )
                ),
                shape
            )
    }

    Box(
        container
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = "转换预设${preset.label}"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            preset.label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}
