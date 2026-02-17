package config;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.*;

/**
 * Centralized font palette — loads the custom display font and derives all
 * size variants used throughout the game.  Falls back to Arial automatically
 * if the custom font file cannot be loaded.
 *
 * TO SWAP FONTS: Change {@link #FONT_PATH} to point at a different .ttf/.otf
 * file and (optionally) adjust {@link #FALLBACK_NAME}.
 */
public class FontPalette {

    // ─── CONFIGURATION ───────────────────────────────────────────────────
    /** Path to the custom font (relative to the project / JAR root). */
    public static String FONT_PATH = "Fonts/Inlanders Demo.otf";

    /** System font to fall back on when the custom file is missing. */
    public static final String FALLBACK_NAME = "Arial";

    /** Secondary fallback for cinematic / Impact-like usage. */
    public static final String CINEMATIC_FALLBACK = "Impact";

    /** Path to the body / small-text font (Arial Black). */
    public static String BODY_FONT_PATH = "Fonts/Arial Black.ttf";

    /** System font to fall back on when body font file is missing. */
    public static final String BODY_FALLBACK_NAME = "Arial";

    // ─── BASE FONTS ──────────────────────────────────────────────────────
    private static Font baseFont;          // custom display font at size 1
    private static Font bodyFont;          // Arial Black for small/body text
    private static Font cinematicBaseFont; // for boss intro — same custom, or Impact
    private static boolean initialized = false;
    private static boolean usingFallback = false;

    // ─── TITLE FONTS (Inlanders) ────────────────────────────────────────
    public static Font TITLE_LARGE;    // 84 — loading title, game over/win
    public static Font TITLE;          // 72 — main menu title
    public static Font TITLE_MEDIUM;   // 60 — screen titles
    public static Font SUBTITLE;       // 36 — subtitles

    // ─── BODY / UI FONTS (Arial Black) ───────────────────────────────────
    public static Font LARGE_32;       // 32 — game over stats heading
    public static Font LARGE;          // 28 — stat cards, money
    public static Font MEDIUM;         // 24 plain — loading text, descriptions
    public static Font MEDIUM_BOLD;    // 24 bold — splash, mode info
    public static Font SMALL;          // 20 plain — general text
    public static Font INFO;           // 18 plain — info text
    public static Font TINY;           // 18 bold — version, progress %
    public static Font XS_16;          // 16 bold — small labels
    public static Font XS_13;          // 13 plain — key hints
    public static Font XS_12;          // 12 bold — missile count
    public static Font XS_11;          // 11 plain — tiny text

    // ─── CINEMATIC / BOSS INTRO FONTS ────────────────────────────────────
    public static Font CINEMATIC_72;   // boss name large
    public static Font CINEMATIC_60;   // "WARNING", stage banner
    public static Font CINEMATIC_48;   // VS text
    public static Font CINEMATIC_36;   // subtitle plates
    public static Font CINEMATIC_28;   // secondary text

    // ─── SPECIAL STYLES ──────────────────────────────────────────────────
    public static Font MONO_11;        // monospaced debug overlay
    public static Font ITALIC_18;      // italic hints

    // ─── INITIALIZER ─────────────────────────────────────────────────────
    /**
     * Call once during startup (e.g. from AssetLoader or Game init).
     * Safe to call multiple times — only the first call loads.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // ── Try loading the custom font ──────────────────────────────────
        baseFont = loadFontFile(FONT_PATH);
        if (baseFont != null) {
            // Register with the graphics environment so Java can derive styles
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(baseFont);
            System.out.println("[FontPalette] Custom font loaded: " + baseFont.getFontName());
            usingFallback = false;
        } else {
            System.out.println("[FontPalette] Custom font not found — falling back to " + FALLBACK_NAME);
            baseFont = new Font(FALLBACK_NAME, Font.PLAIN, 1);
            usingFallback = true;
        }

        // ── Load body font (Arial Black) ─────────────────────────────────
        bodyFont = loadFontFile(BODY_FONT_PATH);
        if (bodyFont != null) {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(bodyFont);
            System.out.println("[FontPalette] Body font loaded: " + bodyFont.getFontName());
        } else {
            System.out.println("[FontPalette] Body font not found — falling back to " + BODY_FALLBACK_NAME);
            bodyFont = new Font(BODY_FALLBACK_NAME, Font.PLAIN, 1);
        }

        // ── Cinematic base (same custom font, or Impact fallback) ────────
        cinematicBaseFont = usingFallback
            ? new Font(CINEMATIC_FALLBACK, Font.PLAIN, 1)
            : baseFont;

        // ── Title / display sizes (Inlanders) ───────────────────────────
        TITLE_LARGE  = derive(Font.BOLD, 84);
        TITLE        = derive(Font.BOLD, 72);
        TITLE_MEDIUM = derive(Font.BOLD, 60);
        SUBTITLE     = derive(Font.BOLD, 36);
        LARGE_32     = deriveBody(Font.BOLD, 32);
        LARGE        = deriveBody(Font.BOLD, 28);

        // ── Body / small sizes (Arial Black) ────────────────────────────
        MEDIUM       = deriveBody(Font.PLAIN, 24);
        MEDIUM_BOLD  = deriveBody(Font.BOLD, 24);
        SMALL        = deriveBody(Font.PLAIN, 20);
        INFO         = deriveBody(Font.PLAIN, 18);
        TINY         = deriveBody(Font.BOLD, 18);
        XS_16        = deriveBody(Font.BOLD, 16);
        XS_13        = deriveBody(Font.PLAIN, 13);
        XS_12        = deriveBody(Font.BOLD, 12);
        XS_11        = deriveBody(Font.PLAIN, 11);

        // Cinematic
        CINEMATIC_72 = deriveCinematic(Font.BOLD, 72);
        CINEMATIC_60 = deriveCinematic(Font.BOLD, 60);
        CINEMATIC_48 = deriveCinematic(Font.BOLD, 48);
        CINEMATIC_36 = deriveCinematic(Font.BOLD, 36);
        CINEMATIC_28 = deriveCinematic(Font.BOLD, 28);

        // Special
        MONO_11   = new Font("Monospaced", Font.PLAIN, 11);
        ITALIC_18 = deriveBody(Font.ITALIC, 18);
    }

    // ─── PUBLIC HELPERS ──────────────────────────────────────────────────

    /**
     * Get a one-off size/style variant — uses Arial Black (body font)
     * so that numbers and digits always render correctly.
     */
    public static Font get(int style, float size) {
        ensureInit();
        return bodyFont.deriveFont(style, size);
    }

    /**
     * Get a one-off size/style variant of the body font (Arial Black).
     */
    public static Font getBody(int style, float size) {
        ensureInit();
        return bodyFont.deriveFont(style, size);
    }

    /**
     * Get a one-off size/style variant of the display font (Inlanders).
     * Use ONLY for text that is guaranteed to contain NO digits (0-9),
     * e.g. screen titles, button labels, headers.
     */
    public static Font getDisplay(int style, float size) {
        ensureInit();
        return baseFont.deriveFont(style, size);
    }

    /** Same as {@link #get} but from the cinematic (Impact-like) base. */
    public static Font getCinematic(int style, float size) {
        ensureInit();
        return cinematicBaseFont.deriveFont(style, size);
    }

    /** True if the custom font could not be loaded. */
    public static boolean isFallback() { return usingFallback; }

    /**
     * Hot-swap the font at runtime (e.g. from a settings screen).
     * Pass the path to a .ttf / .otf file.  Returns true on success.
     */
    public static boolean setFont(String newPath) {
        Font test = loadFontFile(newPath);
        if (test == null) return false;
        FONT_PATH = newPath;
        initialized = false;   // force re-init
        init();
        return true;
    }

    // ─── INTERNALS ───────────────────────────────────────────────────────

    private static void ensureInit() {
        if (!initialized) init();
    }

    private static Font derive(int style, float size) {
        return baseFont.deriveFont(style, size);
    }

    private static Font deriveBody(int style, float size) {
        return bodyFont.deriveFont(style, size);
    }

    private static Font deriveCinematic(int style, float size) {
        return cinematicBaseFont.deriveFont(style, size);
    }

    /**
     * Attempt to load a .ttf/.otf from classpath first, then filesystem.
     * Returns null on failure (never throws).
     */
    private static Font loadFontFile(String path) {
        // Normalize
        String normalized = path.replace("\\", "/");
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.startsWith("../")) normalized = normalized.substring(3);

        // 1. Try classpath resource (JAR-safe)
        InputStream stream = FontPalette.class.getResourceAsStream("/" + normalized);
        if (stream != null) {
            try {
                Font f = Font.createFont(Font.TRUETYPE_FONT, stream);
                stream.close();
                return f;
            } catch (FontFormatException | IOException e) {
                System.err.println("[FontPalette] Failed to parse font resource: " + e.getMessage());
            }
        }

        // 2. Try filesystem
        File file = new File(normalized);
        if (!file.exists()) file = new File(path);
        if (file.exists()) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, file);
            } catch (FontFormatException | IOException e) {
                System.err.println("[FontPalette] Failed to parse font file: " + e.getMessage());
            }
        }

        return null;
    }

    private FontPalette() { throw new AssertionError("Utility class"); }
}
