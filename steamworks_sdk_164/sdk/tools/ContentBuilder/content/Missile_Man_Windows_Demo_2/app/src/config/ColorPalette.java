package config;

import java.awt.AlphaComposite;
import java.awt.Color;

/**
 * Centralized color palette and visual effect constants.
 * 
 * Military / Rock theme — dark gunmetal backgrounds, afterburner orange &
 * warning-stripe yellow accents, danger reds, cockpit greens.
 */
public class ColorPalette {
    
    // ============================================
    // UI BACKGROUND COLORS — Dark military palette
    // ============================================
    
    /** Deepest background — jet black with blue tint */
    public static final Color BG_DARK = new Color(10, 10, 20);
    
    /** Mid-tone background — dark navy */
    public static final Color BG_MID = new Color(22, 33, 62);
    
    /** Lighter background accent — deep steel blue */
    public static final Color BG_LIGHT = new Color(15, 52, 96);
    
    /** Card / panel interior */
    public static final Color BG_CARD = new Color(18, 22, 38, 220);
    
    /** Card interior when selected / highlighted */
    public static final Color BG_CARD_SELECTED = new Color(30, 40, 70, 230);
    
    /** Overlay for darkening screens (game over, etc.) */
    public static final Color BG_OVERLAY = new Color(5, 5, 15, 180);

    // ============================================
    // ACCENT COLORS — Rock / afterburner theme
    // ============================================
    
    /** Afterburner orange — primary accent */
    public static final Color ACCENT_ORANGE = new Color(255, 120, 30);
    
    /** Warning stripe yellow — secondary accent */
    public static final Color ACCENT_YELLOW = new Color(240, 200, 50);
    
    /** Danger / alert red */
    public static final Color ACCENT_RED = new Color(200, 40, 40);
    
    /** Bright red for critical states */
    public static final Color ACCENT_RED_BRIGHT = new Color(255, 60, 60);
    
    /** Cyan instrument glow */
    public static final Color ACCENT_CYAN = new Color(80, 200, 240);
    
    /** Purple highlight */
    public static final Color ACCENT_PURPLE = new Color(160, 100, 200);
    
    // ============================================
    // TEXT COLORS
    // ============================================
    
    /** Primary text — bright white-blue */
    public static final Color TEXT_PRIMARY = new Color(230, 230, 240);
    
    /** Secondary / dimmed text */
    public static final Color TEXT_DIM = new Color(120, 130, 150);
    
    /** Gold text for money, rewards, highlights */
    public static final Color TEXT_GOLD = new Color(235, 203, 139);
    
    /** Bright white text */
    public static final Color TEXT_WHITE = new Color(255, 255, 255);
    
    /** Dark shadow text */
    public static final Color TEXT_SHADOW = new Color(0, 0, 0, 150);
    
    // ============================================
    // BUTTON COLORS
    // ============================================
    
    /** Button base (unselected) — dark steel */
    public static final Color BUTTON_BASE = new Color(30, 35, 50);
    
    /** Button when selected — lit panel */
    public static final Color BUTTON_SELECTED = new Color(50, 60, 90);
    
    /** Button border — brushed steel */
    public static final Color BORDER_STEEL = new Color(100, 110, 130);
    
    /** Selected button glow border */
    public static final Color BORDER_GLOW = new Color(255, 140, 40, 120);
    
    /** Caution tape stripe color */
    public static final Color CAUTION_STRIPE = new Color(240, 200, 50, 40);
    
    // ============================================
    // MENU BUTTON ACCENT COLORS — per button identity
    // ============================================
    
    /** Select Level button — military green */
    public static final Color BTN_LEVEL = new Color(80, 160, 80);
    public static final Color BTN_LEVEL_SEL = new Color(100, 200, 100);
    
    /** Shop button — afterburner orange */
    public static final Color BTN_SHOP = new Color(220, 140, 50);
    public static final Color BTN_SHOP_SEL = new Color(255, 170, 70);
    
    /** Stats button — instrument cyan */
    public static final Color BTN_STATS = new Color(60, 170, 220);
    public static final Color BTN_STATS_SEL = new Color(80, 200, 250);
    
    /** Achievements button — medal gold */
    public static final Color BTN_ACHIEVE = new Color(200, 170, 50);
    public static final Color BTN_ACHIEVE_SEL = new Color(240, 210, 70);
    
    /** Info button — steel teal */
    public static final Color BTN_INFO = new Color(70, 160, 160);
    public static final Color BTN_INFO_SEL = new Color(90, 200, 200);
    
    /** Settings button — warning red */
    public static final Color BTN_SETTINGS = new Color(180, 60, 60);
    public static final Color BTN_SETTINGS_SEL = new Color(220, 80, 80);
    
    /** Save files button — ammo bronze */
    public static final Color BTN_SAVE = new Color(180, 120, 60);
    public static final Color BTN_SAVE_SEL = new Color(220, 150, 80);
    
    // ============================================
    // STATUS COLORS
    // ============================================
    
    /** Success / positive — cockpit green */
    public static final Color SUCCESS_GREEN = new Color(80, 200, 80);
    
    /** Mission failed stamp red */
    public static final Color MISSION_FAILED_RED = new Color(180, 30, 30);
    
    /** Victory gold */
    public static final Color VICTORY_GOLD = new Color(255, 210, 80);
    
    /** Rank / medal shimmer */
    public static final Color MEDAL_GOLD = new Color(255, 215, 0);
    
    // ============================================
    // SCREEN-SPECIFIC GRADIENTS (as Color arrays for drawAnimatedGradient)
    // ============================================
    
    /** Standard menu background gradient */
    public static final Color[] GRADIENT_MENU = { BG_DARK, BG_MID, BG_LIGHT };
    
    /** Game over — dark with red tint */
    public static final Color[] GRADIENT_GAMEOVER = {
        new Color(20, 8, 8), new Color(40, 15, 15), new Color(50, 20, 25)
    };
    
    /** Victory — dark with gold tint */
    public static final Color[] GRADIENT_VICTORY = {
        new Color(15, 15, 8), new Color(35, 30, 10), new Color(50, 45, 15)
    };
    
    /** Level select — deep dark */
    public static final Color[] GRADIENT_LEVEL_SELECT = {
        new Color(8, 10, 20), new Color(12, 18, 40), new Color(18, 25, 55)
    };
    
    // ============================================
    // WARNING STRIPES & DECORATIONS
    // ============================================
    
    /** Warning stripe dark band */
    public static final Color STRIPE_DARK = new Color(20, 20, 25);
    
    /** Warning stripe light band (yellow) */
    public static final Color STRIPE_LIGHT = new Color(200, 170, 30, 60);
    
    /** Radar sweep green glow */
    public static final Color RADAR_GREEN = new Color(50, 255, 100, 30);
    
    /** Scanline overlay for retro/terminal effect */
    public static final Color SCANLINE = new Color(0, 0, 0, 30);
    
    // ============================================
    // PARTICLE COLORS (gameplay — unchanged)
    // ============================================
    
    /** Impact particle color - bright white */
    public static final Color IMPACT_WHITE = new Color(255, 255, 255);
    
    /** Impact particle color - yellow */
    public static final Color IMPACT_YELLOW = new Color(255, 255, 150);
    
    /** Impact ring color */
    public static final Color IMPACT_RING = new Color(255, 255, 200);
    
    /** Fire particle color - orange */
    public static final Color FIRE_ORANGE = new Color(255, 100, 0);
    
    /** Fire particle color - yellow */
    public static final Color FIRE_YELLOW = new Color(255, 200, 0);
    
    /** Fire particle color - red */
    public static final Color FIRE_RED = new Color(255, 50, 0);
    
    /** Smoke particle color - gray with transparency */
    public static final Color SMOKE_GRAY = new Color(80, 80, 80, 150);
    
    /** Boss fire color - orange */
    public static final Color BOSS_FIRE = new Color(255, 150, 0);
    
    /** Boss fire color - bright */
    public static final Color BOSS_FIRE_BRIGHT = new Color(255, 200, 50);
    
    
    // ============================================
    // UI FEEDBACK COLORS (gameplay — unchanged)
    // ============================================
    
    /** Vulnerability window indicator - gold */
    public static final Color VULNERABILITY_GOLD = new Color(235, 203, 139);
    
    /** Warning/danger indicator - red */
    public static final Color WARNING_RED = new Color(191, 97, 106);
    
    /** Player death effect - red */
    public static final Color PLAYER_DEATH_RED = new Color(191, 97, 106);
    
    /** Dodge success indicator - green */
    public static final Color DODGE_GREEN = new Color(163, 190, 140);
    
    
    // ============================================
    // SHIELD COLORS (gameplay — unchanged)
    // ============================================
    
    /** Shield glow effect */
    public static final Color SHIELD_GLOW = new Color(136, 192, 208, 50);
    
    /** Shield ring */
    public static final Color SHIELD_RING = new Color(136, 192, 208, 100);
    
    /** Shield core */
    public static final Color SHIELD_CORE = new Color(136, 192, 208, 150);
    
    
    // ============================================
    // PLAYER EFFECTS (gameplay — unchanged)
    // ============================================
    
    /** Afterimage trail color */
    public static final Color AFTERIMAGE_COLOR = new Color(200, 220, 255);
    
    
    // ============================================
    // ALPHA COMPOSITES (for transparency)
    // ============================================
    
    /** Fully opaque (alpha = 1.0) */
    public static final AlphaComposite ALPHA_FULL = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f);
    
    /** Half transparent (alpha = 0.5) */
    public static final AlphaComposite ALPHA_HALF = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
    
    /** One third opaque (alpha = 0.3) */
    public static final AlphaComposite ALPHA_THIRD = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);
    
    /** Very transparent (alpha = 0.2) */
    public static final AlphaComposite ALPHA_LIGHT = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f);
    
    /** Almost invisible (alpha = 0.1) */
    public static final AlphaComposite ALPHA_FAINT = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f);
    
    
    // ============================================
    // UTILITY METHODS
    // ============================================
    
    /**
     * Returns a new Color with the same RGB but the given alpha (0–255).
     */
    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
    
    
    // ============================================
    // PRIVATE CONSTRUCTOR
    // ============================================
    
    /** Private constructor to prevent instantiation */
    private ColorPalette() {
        throw new AssertionError("ColorPalette is a utility class and should not be instantiated");
    }
}
