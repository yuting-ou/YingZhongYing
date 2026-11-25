package com.vliveconvert.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
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
import com.vliveconvert.app.service.PerfPreset
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

// 小尺寸节点不挂 Backdrop；有 Haze Glass 时采样真实背景，无 GPU/失效时回退玻璃膜。
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
        targetValue = if (pressed) 1.04f else 1f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "presetPress"
    )
    val dispersion = animateGlassDispersion(pressed)
    val touch = rememberGlassPressPosition(interaction)
    val dark = isDarkGlass()
    val shape = continuousShape(24.dp)
    val base = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
    val primary = MaterialTheme.colorScheme.primary
    val selectedColor = if (dark) primary else lerp(primary, Color.Black, 0.18f)
    val chromatic = glassOptics().third
    val haze = LocalGlassHazeState.current
    val hazeOn = haze != null && hazeAvailable()
    val glassStyle = remember(dark, selected, base, selectedColor, chromatic) {
        GlassStyle.regular.then {
            shape(RoundedCornerShape(24.dp))
            tint(if (selected) selectedColor.copy(alpha = 0.16f) else base.copy(alpha = 0.08f))
            chromaticAberrationStrength(if (chromatic) 0.035f else 0f)
        }
    }
    val textColor = presetTextColor(
        selection = selection,
        base = base,
        primary = selectedColor,
        onSurface = MaterialTheme.colorScheme.onSurface,
        onPrimary = MaterialTheme.colorScheme.onPrimary
    )

    val visual = modifier
        .height(48.dp)
        .shadow(
            10.dp,
            shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.22f else 0.08f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.18f else 0.06f)
        )
        .clip(shape)

    val container = visual
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .then(
            if (hazeOn) {
                Modifier.hazeGlass(
                    input = HazeInput.Sources(haze!!),
                    style = glassStyle,
                    interactionSource = null
                )
            } else {
                Modifier
            }
        )
        .background(base.copy(alpha = if (hazeOn) 0.20f else if (dark) 0.78f else 0.68f))
        .background(selectedColor.copy(alpha = selection * if (hazeOn) 0.86f else 1f))
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
        .glassPressDispersion(
            intensity = dispersion,
            radius = 24.dp,
            dark = dark || selected,
            touch = touch,
            chromatic = chromatic,
            strength = 0.9f
        )

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
