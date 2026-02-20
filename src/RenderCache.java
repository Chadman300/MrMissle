import java.awt.*;

/**
 * Centralized cache for frequently allocated rendering objects.
 * Eliminates hundreds of per-frame allocations of AlphaComposite, BasicStroke,
 * and Color objects that cause massive GC pressure and frame drops.
 *
 * Usage examples:
 *   g.setComposite(RenderCache.getAlpha(0.5f));  // instead of AlphaComposite.getInstance(...)
 *   g.setStroke(RenderCache.getStroke(2f));        // instead of new BasicStroke(2f)
 */
public final class RenderCache {

    // ── AlphaComposite cache (101 entries: 0.00, 0.01, ..., 1.00) ──────────
    private static final AlphaComposite[] ALPHA_SRC_OVER = new AlphaComposite[101];
    static {
        for (int i = 0; i <= 100; i++) {
            ALPHA_SRC_OVER[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / 100f);
        }
    }

    /**
     * Return a cached SRC_OVER AlphaComposite for the given alpha (0.0–1.0).
     * Rounds to nearest 0.01; values outside range are clamped.
     */
    public static AlphaComposite getAlpha(float alpha) {
        int idx = Math.round(alpha * 100f);
        if (idx < 0) idx = 0;
        else if (idx > 100) idx = 100;
        return ALPHA_SRC_OVER[idx];
    }

    /** Convenience: index directly (0–100) */
    public static AlphaComposite getAlphaByIndex(int index) {
        if (index < 0) index = 0;
        else if (index > 100) index = 100;
        return ALPHA_SRC_OVER[index];
    }

    // ── BasicStroke cache (half-integer widths 0.0, 0.5, 1.0, …, 10.0) ────
    private static final BasicStroke[] STROKE_CACHE = new BasicStroke[21]; // 0..10 in 0.5 steps
    static {
        for (int i = 0; i < 21; i++) {
            STROKE_CACHE[i] = new BasicStroke(i * 0.5f);
        }
    }

    /**
     * Return a cached BasicStroke for the given width.
     * Supported widths: 0.0, 0.5, 1.0, 1.5, …, 10.0
     * Falls back to a new BasicStroke for unsupported widths.
     */
    public static BasicStroke getStroke(float width) {
        int idx = Math.round(width * 2f);
        if (idx >= 0 && idx < STROKE_CACHE.length) {
            return STROKE_CACHE[idx];
        }
        return new BasicStroke(width); // Rare fallback for exotic widths
    }

    // ── Common strokes ─────────────────────────────────────────────────────
    public static final BasicStroke STROKE_0 = STROKE_CACHE[0];
    public static final BasicStroke STROKE_1 = STROKE_CACHE[2];  // 1.0f
    public static final BasicStroke STROKE_1_5 = STROKE_CACHE[3]; // 1.5f
    public static final BasicStroke STROKE_2 = STROKE_CACHE[4];  // 2.0f
    public static final BasicStroke STROKE_2_5 = STROKE_CACHE[5]; // 2.5f
    public static final BasicStroke STROKE_3 = STROKE_CACHE[6];  // 3.0f
    public static final BasicStroke STROKE_4 = STROKE_CACHE[8];  // 4.0f
    public static final BasicStroke STROKE_5 = STROKE_CACHE[10]; // 5.0f

    // ── Common AlphaComposites ──────────────────────────────────────────────
    public static final AlphaComposite ALPHA_FULL   = ALPHA_SRC_OVER[100]; // 1.0
    public static final AlphaComposite ALPHA_NONE   = ALPHA_SRC_OVER[0];   // 0.0
    public static final AlphaComposite ALPHA_HALF   = ALPHA_SRC_OVER[50];  // 0.5
    public static final AlphaComposite ALPHA_THIRD  = ALPHA_SRC_OVER[30];  // 0.3
    public static final AlphaComposite ALPHA_LIGHT  = ALPHA_SRC_OVER[20];  // 0.2
    public static final AlphaComposite ALPHA_FAINT  = ALPHA_SRC_OVER[10];  // 0.1

    // ── Cached common colors that are used per-frame in many places ────────
    public static final Color BLACK_0   = new Color(0, 0, 0, 0);     // fully transparent
    public static final Color BLACK_100 = new Color(0, 0, 0, 100);
    public static final Color BLACK_120 = new Color(0, 0, 0, 120);
    public static final Color BLACK_140 = new Color(0, 0, 0, 140);
    public static final Color BLACK_150 = new Color(0, 0, 0, 150);
    public static final Color BLACK_180 = new Color(0, 0, 0, 180);
    public static final Color BLACK_200 = new Color(0, 0, 0, 200);
    public static final Color ICE_BLUE = new Color(136, 192, 208);
    public static final Color ICY_WHITE = new Color(200, 235, 255);
    public static final Color GREEN_TRACK = new Color(180, 255, 180);

    // Common grays used extensively in menus/HUD
    public static final Color GRAY_60    = new Color(60, 60, 60);
    public static final Color GRAY_80    = new Color(80, 80, 80);
    public static final Color GRAY_100   = new Color(100, 100, 100);
    public static final Color GRAY_120   = new Color(120, 120, 120);
    public static final Color GRAY_150   = new Color(150, 150, 150);
    public static final Color GRAY_200   = new Color(200, 200, 200);
    public static final Color SLATE_180_190_200 = new Color(180, 190, 200);
    public static final Color SLATE_200_200_210 = new Color(200, 200, 210);
    public static final Color DARK_40_45_55     = new Color(40, 45, 55);
    public static final Color DARK_20_20_30_200 = new Color(20, 20, 30, 200);
    public static final Color DARK_40_40_50_180 = new Color(40, 40, 50, 180);
    public static final Color TAN_85_75_45_200  = new Color(85, 75, 45, 200);
    public static final Color TAN_180_170_130_140 = new Color(180, 170, 130, 140);
    public static final Color GREEN_50_200_80   = new Color(50, 200, 80);
    public static final Color GREEN_50_150_50   = new Color(50, 150, 50);
    public static final Color GREEN_100_200_100 = new Color(100, 200, 100);
    public static final Color ORANGE_255_165_0  = new Color(255, 165, 0);
    public static final Color BLUE_100_200_255  = new Color(100, 200, 255);
    public static final Color BLUE_100_180_255  = new Color(100, 180, 255);
    public static final Color BLUE_80_180_255   = new Color(80, 180, 255);
    public static final Color BLUE_150_200_255  = new Color(150, 200, 255);
    public static final Color BLUE_120_200_255  = new Color(120, 200, 255);
    public static final Color RED_255_50_50     = new Color(255, 50, 50);
    public static final Color RED_255_80_80     = new Color(255, 80, 80);
    public static final Color RED_255_100_100   = new Color(255, 100, 100);
    public static final Color RED_255_120_120   = new Color(255, 120, 120);
    public static final Color WARM_255_240_200  = new Color(255, 240, 200);
    public static final Color WARM_255_180_100  = new Color(255, 180, 100);
    public static final Color WARM_255_150_80   = new Color(255, 150, 80);
    public static final Color WARM_255_160_80   = new Color(255, 160, 80);
    public static final Color WARM_255_150_60   = new Color(255, 150, 60);
    public static final Color CREAM_255_255_220 = new Color(255, 255, 220);
    public static final Color WHITE_180         = new Color(255, 255, 255, 180);
    public static final Color GREEN_163_210_140 = new Color(163, 210, 140);

    // Fallback bullet colors (only used when sprites fail to load)
    public static final Color BULLET_YELLOW = new Color(255, 220, 0);
    public static final Color BULLET_BLUE = new Color(0, 100, 255);
    public static final Color BULLET_PINK = new Color(255, 50, 200);
    public static final Color BULLET_GREEN = new Color(50, 255, 100);
    public static final Color BULLET_CYAN = new Color(0, 255, 255);
    public static final Color BULLET_PURPLE = new Color(200, 50, 255);
    public static final Color BULLET_WAVE_PURPLE = new Color(180, 0, 255);
    public static final Color BULLET_RED = new Color(255, 50, 50);
    public static final Color ICON_UNSELECTED = new Color(180, 185, 200);

    // ── Private constructor ─────────────────────────────────────────────────
    private RenderCache() {
        throw new AssertionError("RenderCache is a utility class");
    }
}
