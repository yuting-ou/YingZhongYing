package com.vliveconvert.app.ui

enum class GlassLevel { Thin, Regular, Thick }

internal object GlassSafety {
    const val MinBackdropShortSideDp = 96f

    fun canUseBackdrop(shortSideDp: Float): Boolean =
        shortSideDp.isFinite() && shortSideDp >= MinBackdropShortSideDp

    fun resolveLevel(dense: Boolean, liquid: Boolean, level: GlassLevel?): GlassLevel =
        level ?: if (dense) GlassLevel.Thick else GlassLevel.Regular

    fun allowsBackdrop(
        level: GlassLevel,
        shortSideDp: Float,
        hardwareAvailable: Boolean
    ): Boolean =
        hardwareAvailable && level != GlassLevel.Thin && canUseBackdrop(shortSideDp)
}
