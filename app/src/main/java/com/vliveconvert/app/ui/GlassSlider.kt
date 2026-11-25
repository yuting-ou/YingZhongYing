package com.vliveconvert.app.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.math.abs

internal fun normalizedSliderProgress(
    value: Float,
    range: ClosedFloatingPointRange<Float>
): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f || !span.isFinite()) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

internal fun sliderTickFractions(steps: Int): List<Float> {
    if (steps <= 0) return emptyList()
    return List(steps + 1) { it.toFloat() / steps }
}

internal fun sliderThumbCenterX(widthPx: Float, thumbPx: Float, progress: Float): Float {
    if (widthPx <= 0f) return 0f
    if (thumbPx >= widthPx) return widthPx / 2f
    val travel = widthPx - thumbPx
    return thumbPx / 2f + travel * progress.coerceIn(0f, 1f)
}

internal fun sliderTooltipTopPx(
    containerHeightPx: Float,
    sliderHeightPx: Float,
    thumbPx: Float,
    tooltipHeightPx: Float,
    gapPx: Float
): Float {
    val sliderTop = (containerHeightPx - sliderHeightPx).coerceAtLeast(0f)
    val thumbTop = sliderTop + (sliderHeightPx - thumbPx).coerceAtLeast(0f) / 2f
    return (thumbTop - gapPx - tooltipHeightPx).coerceAtLeast(0f)
}

internal fun sliderVelocityStretch(velocityPxPerSecond: Float): Pair<Float, Float> {
    val amount = (abs(velocityPxPerSecond) / 8_000f).coerceIn(0f, 1f)
    return (1f + 0.10f * amount) to (1f - 0.06f * amount)
}

// 小尺寸节点不挂 Backdrop；有 Haze Glass 时采样真实背景，无 GPU/失效时回退玻璃膜。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    contentDescription: String,
    valueLabel: String,
    showTicks: Boolean = steps > 0
) {
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(valueRange),
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else spring(dampingRatio = 0.9f, stiffness = 420f),
        label = "glassSliderValue"
    )
    val shown = if (dragged || reduceMotion) value else animated
    val progress = normalizedSliderProgress(shown, valueRange)
    val press = animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
        else spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "glassSliderPress"
    )
    val dispersion = animateGlassDispersion(enabled && (pressed || dragged))
    var velocity by remember { mutableFloatStateOf(0f) }
    val velocityTracker = remember { VelocityTracker() }
    LaunchedEffect(dragged, reduceMotion) {
        if (!dragged || reduceMotion) velocity = 0f
    }

    BoxWithConstraints(
        modifier.fillMaxWidth().height(80.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        val thumbPx = with(LocalDensity.current) { 28.dp.toPx() }
        val density = LocalDensity.current
        val tooltipWidthPx = with(density) { 140.dp.toPx() }
        val tooltipHeightPx = with(density) { 32.dp.toPx() }
        val tooltipGapPx = with(density) { 10.dp.toPx() }
        val tooltipX = (
            sliderThumbCenterX(constraints.maxWidth.toFloat(), thumbPx, progress) -
                tooltipWidthPx / 2f
            ).coerceIn(0f, (constraints.maxWidth - tooltipWidthPx).coerceAtLeast(0f))
        val tooltipY = sliderTooltipTopPx(
            containerHeightPx = constraints.maxHeight.toFloat(),
            sliderHeightPx = with(density) { 48.dp.toPx() },
            thumbPx = thumbPx,
            tooltipHeightPx = tooltipHeightPx,
            gapPx = tooltipGapPx
        )
        val tooltipVisible = enabled && (dragged || pressed)
        val tooltipAlpha = animateFloatAsState(
            targetValue = if (tooltipVisible) 1f else 0f,
            animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
            else spring(dampingRatio = 0.82f, stiffness = 560f),
            label = "glassSliderTooltip"
        )

        Slider(
            value = shown.coerceIn(valueRange),
            onValueChange = { if (enabled) onValueChange(it) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(interaction) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                        }
                        velocity = if (reduceMotion) 0f else velocityTracker.calculateVelocity().x
                    }
                }
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = valueLabel
                },
            thumb = {
                GlassSliderThumb(
                    enabled = enabled,
                    press = press,
                    dispersion = dispersion,
                    velocity = { velocity }
                )
            },
            track = {
                GlassSliderTrack(
                    progress = progress,
                    steps = if (showTicks) steps else 0,
                    active = dragged && enabled,
                    enabled = enabled
                )
            }
        )

        if (tooltipAlpha.value > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { tooltipX.toDp() },
                        y = with(density) { tooltipY.toDp() }
                    )
                    .graphicsLayer {
                        alpha = tooltipAlpha.value
                        val scale = if (reduceMotion) 1f else 0.92f + 0.08f * tooltipAlpha.value
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Box(
                    Modifier
                        .size(width = 140.dp, height = 32.dp)
                        .glassSurface(14.dp)
                        .clip(continuousShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        valueLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassSliderThumb(
    enabled: Boolean,
    press: androidx.compose.runtime.State<Float>,
    dispersion: Float,
    velocity: () -> Float
) {
    val reduceMotion = LocalReduceMotion.current
    val dark = isDarkGlass()
    val shape = CircleShape
    val base = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
    val chromatic = glassOptics().third
    val haze = LocalGlassHazeState.current
    val hazeOn = haze != null && hazeAvailable()
    val glassStyle = remember(dark, base, chromatic) {
        GlassStyle.regular.then {
            shape(RoundedCornerShape(14.dp))
            tint(base.copy(alpha = 0.10f))
            chromaticAberrationStrength(if (chromatic) 0.04f else 0f)
        }
    }
    Box(
        Modifier
            .size(28.dp)
            .graphicsLayer {
                val pressValue = press.value
                val stretch = if (reduceMotion) 1f to 1f else sliderVelocityStretch(velocity())
                val scale = if (enabled) 1f + 0.18f * pressValue else 0.92f
                scaleX = scale * stretch.first
                scaleY = scale * stretch.second
                alpha = if (enabled) 1f else 0.38f
            }
            .shadow(
                8.dp,
                shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.26f else 0.10f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.20f else 0.08f)
            )
            .clip(shape)
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
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (hazeOn) 0.18f else if (dark) 0.30f else 0.58f),
                        base.copy(alpha = (if (dark) 0.88f else 0.76f) * if (hazeOn) 0.34f else 1f),
                        base.copy(alpha = (if (dark) 0.62f else 0.48f) * if (hazeOn) 0.30f else 1f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.88f), Color.White.copy(alpha = 0.16f))
                ),
                shape
            )
            .glassPressDispersion(
                intensity = dispersion,
                radius = 14.dp,
                dark = dark,
                chromatic = chromatic,
                strength = if (enabled) 1f else 0f
            )
    )
}

@Composable
private fun GlassSliderTrack(
    progress: Float,
    steps: Int,
    active: Boolean,
    enabled: Boolean
) {
    val reduceMotion = LocalReduceMotion.current
    val dark = isDarkGlass()
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.20f else 0.12f)
    val inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.34f else 0.24f)
    val disabledColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
    val ticks = sliderTickFractions(steps)
    val phase = if (active && !reduceMotion) {
        rememberInfiniteTransition(label = "glassSliderSheen").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)),
            label = "glassSliderSheenPhase"
        )
    } else null

    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = if (dark) 0.16f else 0.05f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2f),
            size = Size(size.width, 2f),
            cornerRadius = CornerRadius(1f)
        )
        val filled = size.width * progress.coerceIn(0f, 1f)
        if (filled > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(primary, tertiary),
                    startX = 0f,
                    endX = size.width.coerceAtLeast(1f)
                ),
                size = Size(filled, size.height),
                cornerRadius = CornerRadius(radius)
            )
            if (phase != null) {
                val band = size.width * 0.32f
                val x = (filled + band) * phase.value - band
                clipRect(0f, 0f, filled, size.height) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.42f),
                                Color.Transparent
                            ),
                            startX = x,
                            endX = x + band
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = Size(band, size.height),
                        cornerRadius = CornerRadius(radius)
                    )
                }
            }
        }
        ticks.forEachIndexed { index, fraction ->
            val x = size.width * fraction
            val activeTick = fraction <= progress + 0.0001f
            drawCircle(
                color = if (activeTick) Color.White.copy(alpha = 0.88f)
                else inactiveTickColor,
                radius = if (index == ticks.size / 2) 2.2f else 1.5f,
                center = androidx.compose.ui.geometry.Offset(x, radius)
            )
        }
        if (!enabled) {
            drawRoundRect(
                color = disabledColor,
                size = size,
                cornerRadius = CornerRadius(radius)
            )
        }
    }
}
