package config;

/**
 * Central UI scale utility — provides a global scaling factor for all UI elements.
 * Scale presets: Small (0.85×), Medium (1.0× default), Large (1.2×).
 *
 * Usage:
 *   UIScale.px(300)     — scale a pixel dimension (returns int)
 *   UIScale.pxf(300)    — scale a pixel dimension (returns float)
 *   UIScale.fontSize(20) — scale a font size (clamped to minimum 8)
 *   UIScale.getScale()  — get the current scale factor
 */
public class UIScale {

    // ─── PRESETS ──────────────────────────────────────────────────────────
    public static final int SMALL  = 0;
    public static final int MEDIUM = 1;
    public static final int LARGE  = 2;

    public static final String[] LABELS = {"Small", "Medium", "Large"};

    private static final float[] SCALE_VALUES = {0.85f, 1.0f, 1.2f};

    // ─── STATE ───────────────────────────────────────────────────────────
    private static float scaleFactor = 1.0f;
    private static int   currentPreset = MEDIUM;

    // ─── PUBLIC API ──────────────────────────────────────────────────────

    /** Scale a pixel dimension and return an int (rounded). */
    public static int px(int value) {
        if (scaleFactor == 1.0f) return value;
        return Math.round(value * scaleFactor);
    }

    /** Scale a pixel dimension and return a float. */
    public static float pxf(float value) {
        return value * scaleFactor;
    }

    /** Scale a font size, clamped to a minimum of 8. */
    public static int fontSize(int baseSize) {
        if (scaleFactor == 1.0f) return baseSize;
        return Math.max(8, Math.round(baseSize * scaleFactor));
    }

    /** Scale a font size (float input), clamped to minimum 8. */
    public static float fontSizef(float baseSize) {
        if (scaleFactor == 1.0f) return baseSize;
        return Math.max(8f, baseSize * scaleFactor);
    }

    /** Get the current scale factor (0.85, 1.0, or 1.2). */
    public static float getScale() {
        return scaleFactor;
    }

    /** Get the current preset index (0=Small, 1=Medium, 2=Large). */
    public static int getPreset() {
        return currentPreset;
    }

    /**
     * Set the scale from a preset index (0=Small, 1=Medium, 2=Large).
     * Clears FontPalette caches so fonts are re-derived at the new size.
     */
    public static void setScale(int preset) {
        currentPreset = Math.max(0, Math.min(2, preset));
        scaleFactor = SCALE_VALUES[currentPreset];
        // Re-initialize fonts at the new scale
        FontPalette.reinitialize();
    }

    /** Get the label for the current preset. */
    public static String getCurrentLabel() {
        return LABELS[currentPreset];
    }
}
