package com.vliveconvert.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

// 视觉与交互参考：
// - Kyant0/AndroidLiquidGlass (Apache-2.0)：LiquidButton/LiquidSlider 的按压曲线，
//   lens(..., chromaticAberration = true) 的逐通道折射；
// - QWEA0/Liquid-Glass-Android (MIT)：冷暖通道分离的物理色散与按压凸起。
// 两者真实色散依赖 Backdrop/AGSL 采样背景；小尺寸节点在部分 GPU/驱动会触发
// RenderThread SIGSEGV，因此这里用纯 Canvas 光谱膜复现观感，不采样背景。
// 只画在玻璃表面、位于文字/图标下方。

private val DispersionCyan = Color(0xFF33D6F2)
private val DispersionBlue = Color(0xFF6A8CFF)
private val DispersionRose = Color(0xFFFF5F93)
private val DispersionAmber = Color(0xFFFFB13D)

/**
 * 按压色散强度：按下弹到 1，松开快速回落；减少动效时即时切换，保留静态色散。
 * 返回 0..1，绘制层再做钳制。
 */
@Composable
internal fun animateGlassDispersion(active: Boolean): Float {
    val reduceMotion = LocalReduceMotion.current
    val dispersion by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (reduceMotion) snap()
        else spring(dampingRatio = 0.58f, stiffness = 440f),
        label = "glassDispersion"
    )
    return dispersion
}

/**
 * 指尖在玻璃内的位置，用于让按压高光跟手；松手即清空。
 * 图标磁贴的交互在 48dp 代理上，传入 targetSize/visualSize 换算回视觉坐标。
 */
@Composable
internal fun rememberGlassPressPosition(
    interactionSource: MutableInteractionSource,
    targetSize: Dp? = null,
    visualSize: Dp? = null
): Offset? {
    var position by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    LaunchedEffect(interactionSource, targetSize, visualSize, density) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    val raw = interaction.pressPosition
                    position = if (targetSize != null && visualSize != null && targetSize > visualSize) {
                        val inset = with(density) { ((targetSize - visualSize) / 2f).toPx() }
                        raw - Offset(inset, inset)
                    } else {
                        raw
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> position = null
                else -> Unit
            }
        }
    }
    return position
}

/**
 * 液态玻璃按压色散膜。应挂在这些绘制之后、内容之前，并处于形状裁剪内：
 * shadow → clip(shape) → background → border → glassPressDispersion → content。
 *
 * @param radius 玻璃圆角；胶囊传高度一半。
 * @param strength 实色/半透明控件可调低，避免光谱盖过主题色。
 */
internal fun Modifier.glassPressDispersion(
    intensity: Float,
    radius: Dp,
    dark: Boolean,
    touch: Offset? = null,
    strength: Float = 1f
): Modifier {
    val p = (intensity * strength).coerceIn(0f, 1f)
    if (p <= 0.01f) return this
    return drawBehind {
        val w = size.width
        val h = size.height
        val minSide = min(w, h)
        if (minSide <= 0f) return@drawBehind

        val corner = radius.toPx().coerceIn(0f, minSide / 2f)
        val stroke = (1.1f + 0.9f * p).dp.toPx()
        val inset = stroke / 2f + 0.55.dp.toPx()
        val ringW = (w - inset * 2f).coerceAtLeast(0f)
        val ringH = (h - inset * 2f).coerceAtLeast(0f)
        if (ringW <= 0f || ringH <= 0f) return@drawBehind

        // 内部折射晕：冷端在左上、暖端在右下，低透明度，只染玻璃不压内容
        val blob = minSide * 0.62f
        val coolCenter = Offset(w * 0.28f, h * 0.22f)
        val warmCenter = Offset(w * 0.78f, h * 0.84f)
        val glowBlend = if (dark) BlendMode.Plus else BlendMode.SrcOver
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(DispersionCyan.copy(alpha = 0.55f), Color.Transparent),
                center = coolCenter,
                radius = blob
            ),
            radius = blob,
            center = coolCenter,
            alpha = p * if (dark) 0.12f else 0.09f,
            blendMode = glowBlend
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(DispersionRose.copy(alpha = 0.50f), Color.Transparent),
                center = warmCenter,
                radius = blob
            ),
            radius = blob,
            center = warmCenter,
            alpha = p * if (dark) 0.10f else 0.075f,
            blendMode = glowBlend
        )

        // 冷暖分层边缘：上缘偏青蓝、下缘偏玫橙，中段透明；比整圈彩虹更像真实折射
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to DispersionCyan.copy(alpha = 0.92f),
                0.16f to DispersionBlue.copy(alpha = 0.50f),
                0.36f to Color.Transparent,
                0.64f to Color.Transparent,
                0.84f to DispersionRose.copy(alpha = 0.50f),
                1f to DispersionAmber.copy(alpha = 0.92f)
            ),
            topLeft = Offset(inset, inset),
            size = Size(ringW, ringH),
            cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
            style = Stroke(stroke),
            alpha = p * if (dark) 0.92f else 0.82f
        )

        // 红青通道偏移：上冷下暖各错开半像素，形成真正的色散边缘而非双描边
        val dx = 0.55.dp.toPx()
        val dy = 0.35.dp.toPx()
        val channelStroke = 0.9.dp.toPx()
        val channelRadius = CornerRadius((corner - inset).coerceAtLeast(0f))
        drawRoundRect(
            brush = Brush.verticalGradient(
                0.42f to Color.Transparent,
                0.72f to DispersionRose.copy(alpha = 0.75f),
                1f to DispersionAmber
            ),
            topLeft = Offset(inset + dx, inset + dy),
            size = Size(ringW, ringH),
            cornerRadius = channelRadius,
            style = Stroke(channelStroke),
            alpha = p * 0.34f,
            blendMode = glowBlend
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to DispersionCyan,
                0.28f to DispersionBlue.copy(alpha = 0.75f),
                0.58f to Color.Transparent
            ),
            topLeft = Offset(inset - dx, inset - dy),
            size = Size(ringW, ringH),
            cornerRadius = channelRadius,
            style = Stroke(channelStroke),
            alpha = p * 0.34f,
            blendMode = glowBlend
        )

        // 按压焦点高光：跟手落在指尖下，像玻璃被压出一枚透镜
        val focusCenter = touch?.let {
            Offset(it.x.coerceIn(0f, w), it.y.coerceIn(0f, h))
        } ?: Offset(w * 0.36f, h * 0.30f)
        val focusRadius = minSide * if (touch != null) 0.52f else 0.46f
        // 外层柔光先铺开，内层锐光再收束，避免只看到一颗白点
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                center = focusCenter,
                radius = minSide * 0.92f
            ),
            radius = minSide * 0.92f,
            center = focusCenter,
            alpha = p * if (dark) 0.55f else 0.40f
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                center = focusCenter,
                radius = focusRadius
            ),
            radius = focusRadius,
            center = focusCenter,
            alpha = p * if (dark) 0.70f else 0.85f
        )
    }
}
