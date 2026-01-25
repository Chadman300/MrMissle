package config;

/**
 * Central configuration file for all game constants.
 * 
 * AI CONTEXT:
 * - This file contains ALL magic numbers used throughout the game
 * - Organized by category for easy discovery
 * - When modifying game balance, look here first
 * - All values are public static final for compile-time optimization
 */
public class GameConfig {
    
    // ============================================
    // DISPLAY SETTINGS
    // ============================================
    
    /** Target frames per second for game loop */
    public static final int TARGET_FPS = 60;
    
    /** Milliseconds per frame at target FPS */
    public static final long MS_PER_FRAME = 1000 / TARGET_FPS;
    
    
    // ============================================
    // PLAYER SETTINGS
    // ============================================
    
    /** Player sprite size in pixels */
    public static final int PLAYER_SIZE = 20;
    
    /** Maximum movement speed (pixels per frame) */
    public static final double PLAYER_MAX_SPEED = 6.0;
    
    /** Movement speed during dash ability */
    public static final double PLAYER_DASH_SPEED = 15.0;
    
    /** Acceleration rate (pixels per frame squared) */
    public static final double PLAYER_ACCELERATION = 0.5;
    
    /** Friction coefficient (0.0 = no friction, 1.0 = instant stop) */
    public static final double PLAYER_FRICTION = 0.85;
    
    /** Duration of dash ability in frames */
    public static final int DASH_DURATION_FRAMES = 15;
    
    /** Invincibility frames after perfect dodge */
    public static final int PERFECT_DODGE_IFRAMES = 8;
    
    /** Shadow offset from player sprite */
    public static final double PLAYER_SHADOW_DISTANCE = 12;
    
    /** Sun angle for shadows (radians, 135 degrees = top-left) */
    public static final double SUN_ANGLE = Math.PI * 0.75;
    
    /** Flicker duration for lucky dodge effect (frames) */
    public static final int LUCKY_DODGE_FLICKER_FRAMES = 15;
    
    
    // ============================================
    // BOSS SETTINGS
    // ============================================
    
    /** Maximum number of hits before boss is defeated */
    public static final int BOSS_MAX_HITS = 3;
    
    /** Duration of vulnerability window (frames, 20 seconds) */
    public static final int BOSS_VULNERABILITY_DURATION = 1200;
    
    /** Invulnerability period at level start (frames, 3 seconds) */
    public static final int BOSS_INVULNERABILITY_DURATION = 180;
    
    /** Death animation duration (frames, 3 seconds) */
    public static final int BOSS_DEATH_ANIMATION_DURATION = 180;
    
    /** Delay before player respawn after non-fatal hit (frames, 1.5 seconds) */
    public static final int BOSS_RESPAWN_DELAY = 90;
    
    /** Boss intro cinematic duration (frames, 2 seconds) */
    public static final int BOSS_INTRO_DURATION = 120;
    
    
    // ============================================
    // COMBAT SETTINGS
    // ============================================
    
    /** Distance threshold for graze detection */
    public static final double GRAZE_DISTANCE = 25.0;
    
    /** Distance threshold for close call graze */
    public static final double CLOSE_CALL_DISTANCE = 15.0;
    
    /** Distance threshold for perfect dodge */
    public static final double PERFECT_DODGE_DISTANCE = 8.0;
    
    /** Combo timeout duration (frames, 3 seconds) */
    public static final int COMBO_TIMEOUT = 180;
    
    
    // ============================================
    // UPGRADE LIMITS
    // ============================================
    
    /** Maximum upgrade level for speed */
    public static final int MAX_SPEED_LEVEL = 10;
    
    /** Maximum upgrade level for bullet slow */
    public static final int MAX_BULLET_SLOW_LEVEL = 50;
    
    /** Maximum upgrade level for lucky dodge */
    public static final int MAX_LUCKY_DODGE_LEVEL = 12;
    
    /** Maximum upgrade level for attack window */
    public static final int MAX_ATTACK_WINDOW_LEVEL = 10;
    
    
    // ============================================
    // PERFORMANCE SETTINGS
    // ============================================
    
    /** Maximum number of particles allowed at once */
    public static final int MAX_PARTICLES = 300;
    
    /** Spatial grid cell size for collision optimization */
    public static final int GRID_CELL_SIZE = 50;
    
    /** Pre-computed inverse of grid cell size */
    public static final double INV_GRID_CELL_SIZE = 1.0 / GRID_CELL_SIZE;
    
    /** Multiplier for grid hash calculation */
    public static final int GRID_WIDTH_MULTIPLIER = 10000;
    
    
    // ============================================
    // UI ANIMATION SETTINGS
    // ============================================
    
    /** State transition speed (0.0-1.0, higher = faster) */
    public static final float STATE_TRANSITION_SPEED = 0.08f;
    
    /** Item unlock notification duration (frames, 3 seconds) */
    public static final int ITEM_UNLOCK_DURATION = 180;
    
    /** Item unlock dismiss animation duration (frames, 0.5 seconds) */
    public static final int ITEM_DISMISS_DURATION = 30;
    
    /** Contract unlock notification duration (frames, 4 seconds) */
    public static final int CONTRACT_UNLOCK_DURATION = 240;
    
    /** Contract unlock dismiss animation duration (frames) */
    public static final int CONTRACT_DISMISS_DURATION = 30;
    
    /** Achievement notification duration (frames, 3 seconds) */
    public static final int ACHIEVEMENT_NOTIFICATION_DURATION = 180;
    
    /** Unpause countdown duration (frames, 3 seconds) */
    public static final int UNPAUSE_COUNTDOWN_DURATION = 180;
    
    
    // ============================================
    // RISK CONTRACT SETTINGS
    // ============================================
    
    /** Names of all risk contracts */
    public static final String[] RISK_CONTRACT_NAMES = {
        "No Contract", 
        "Bullet Storm", 
        "Speed Demon", 
        "Shieldless", 
        "Can't Stop"
    };
    
    /** Descriptions of all risk contracts */
    public static final String[] RISK_CONTRACT_DESCRIPTIONS = {
        "Play normally with no modifiers",
        "Double the bullets, double the money! (2x)",
        "Bullets move 50% faster (1.75x)",
        "Shield item disabled (1.5x)",
        "Can't stop moving (2.5x)"
    };
    
    /** Money multipliers for each risk contract */
    public static final double[] RISK_CONTRACT_MULTIPLIERS = {
        1.0,   // No Contract
        2.0,   // Bullet Storm
        1.75,  // Speed Demon
        1.5,   // Shieldless
        2.5    // Can't Stop
    };
    
    
    // ============================================
    // MATHEMATICAL CONSTANTS
    // ============================================
    
    /** Pre-computed constant: 2 * PI */
    public static final double TWO_PI = Math.PI * 2.0;
    
    /** Pre-computed constant: 1 / sqrt(2) for diagonal movement normalization */
    public static final double INV_SQRT_2 = 1.0 / Math.sqrt(2.0);
    
    
    // ============================================
    // PRIVATE CONSTRUCTOR
    // ============================================
    
    /** Private constructor to prevent instantiation */
    private GameConfig() {
        throw new AssertionError("GameConfig is a utility class and should not be instantiated");
    }
}
