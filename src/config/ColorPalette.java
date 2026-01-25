package config;

import java.awt.AlphaComposite;
import java.awt.Color;

/**
 * Centralized color palette and visual effect constants.
 * 
 * AI CONTEXT:
 * - All colors used in the game are defined here
 * - Pre-created Color objects for performance (avoid repeated object creation)
 * - Organized by visual element type
 * - Includes alpha composites for transparency effects
 */
public class ColorPalette {
    
    // ============================================
    // PARTICLE COLORS
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
    // UI FEEDBACK COLORS
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
    // SHIELD COLORS
    // ============================================
    
    /** Shield glow effect */
    public static final Color SHIELD_GLOW = new Color(136, 192, 208, 50);
    
    /** Shield ring */
    public static final Color SHIELD_RING = new Color(136, 192, 208, 100);
    
    /** Shield core */
    public static final Color SHIELD_CORE = new Color(136, 192, 208, 150);
    
    
    // ============================================
    // PLAYER EFFECTS
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
    // PRIVATE CONSTRUCTOR
    // ============================================
    
    /** Private constructor to prevent instantiation */
    private ColorPalette() {
        throw new AssertionError("ColorPalette is a utility class and should not be instantiated");
    }
}
