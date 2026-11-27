package com.vliveconvert.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vliveconvert.app.ui.motion.MotionTokens

@Composable
internal fun AppleSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val reduceMotion = LocalReduceMotion.current
    val dark = isDarkGlass()
    Row(
        modifier
            .testTag("apple_segmented_control")
            .fillMaxWidth()
            .clip(continuousShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressScale by rememberPressScale(interaction, pressedScale = 0.96f)
            val background by animateColorAsState(
                targetValue = if (selected) {
                    if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.92f)
                } else Color.Transparent,
                animationSpec = if (reduceMotion) snap() else tween(MotionTokens.FastMs),
                label = "segmentBackground"
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(continuousShape(10.dp))
                    .background(background)
                    .border(
                        width = if (selected) 1.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        else Color.Transparent,
                        shape = continuousShape(10.dp)
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(index) }
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
