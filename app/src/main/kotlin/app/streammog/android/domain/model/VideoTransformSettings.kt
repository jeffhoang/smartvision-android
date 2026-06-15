package app.streammog.android.domain.model

data class VideoTransformSettings(
    val zoom: Double = 1.0,
    val rotationDegrees: Int = 0,
    val isMirrored: Boolean = false,
    val showsGrid: Boolean = false,
    val showsHorizon: Boolean = false,
    val colorEnhancement: ColorEnhancementPreset = ColorEnhancementPreset.NATURAL,
    val brightness: Double = 0.0,
    val contrast: Double = 1.0,
    val sharpness: Double = 0.2,
    val applyToStream: Boolean = false,
) {
    enum class ColorEnhancementPreset(
        val displayName: String,
        val contrast: Double,
        val saturation: Double,
        val vibrance: Double,
    ) {
        OFF("Off", contrast = 1.0, saturation = 1.0, vibrance = 0.0),
        NATURAL("Natural", contrast = 1.08, saturation = 1.06, vibrance = 0.25),
        VIVID("Vivid", contrast = 1.14, saturation = 1.12, vibrance = 0.45),
    }

    val streamingTransform: VideoTransformSettings
        get() = if (!applyToStream) identity else copy(
            showsGrid = false,
            showsHorizon = false,
            applyToStream = true,
        )

    val isIdentityTransform: Boolean
        get() = zoom == 1.0 &&
            rotationDegrees == 0 &&
            !isMirrored &&
            colorEnhancement == ColorEnhancementPreset.OFF &&
            brightness == 0.0 &&
            contrast == 1.0 &&
            sharpness == 0.0

    val summary: String
        get() = buildList {
            add(if (zoom == 1.0) "fit" else "%.1fx".format(zoom))
            if (rotationDegrees != 0) add("${rotationDegrees}deg")
            if (isMirrored) add("mirror")
            if (colorEnhancement != ColorEnhancementPreset.OFF) add(colorEnhancement.displayName.lowercase())
            if (brightness != 0.0) add("%s%.2f".format(if (brightness > 0) "bright +" else "bright ", brightness))
            if (contrast != 1.0) add("contrast %.2f".format(contrast))
            if (sharpness > 0) add("sharp %.1f".format(sharpness))
            if (applyToStream) add("stream")
        }.joinToString(" | ")

    companion object {
        val zoomOptions = listOf(1.0, 1.25, 1.5, 2.0)
        val rotationOptions = listOf(0, 90, 180, 270)
        val brightnessRange = -0.30..0.30
        val contrastRange = 0.80..1.40
        val sharpnessRange = 0.0..1.0
        val identity = VideoTransformSettings()
    }
}
