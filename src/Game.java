import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import javax.swing.*;
import config.ColorPalette;
import config.FontPalette;
import config.HUDLayout;
import config.UIScale;
import config.UITheme;

public class Game extends JPanel implements Runnable {
    // Cached identity transform — avoids new AffineTransform() allocations in overlays
    private static final AffineTransform IDENTITY_TX = new AffineTransform();
    // Game constants
    public static final int WIDTH;
    public static final int HEIGHT;
    // World bounds - larger than screen for expanded play area
    public static final int WORLD_WIDTH;
    public static final int WORLD_HEIGHT;
    private static final int FPS = 60;
    
    static {
        // Get screen dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        WIDTH = screenSize.width;
        HEIGHT = screenSize.height;
        WORLD_WIDTH = (int)(WIDTH * 1.15);
        WORLD_HEIGHT = (int)(HEIGHT * 1.15);
    }
    
    // Game state
    private Thread gameThread;
    private boolean running;
    private GameState gameState;
    private int selectedStatItem;
    private int selectedMenuItem; // For main menu navigation
    private int demoOverSelection; // 0 = Play Again, 1 = Quit (demo over screen)
    private int mouseX, mouseY; // Mouse position for UI navigation
    private int lastMouseX = -1, lastMouseY = -1; // Previous mouse position for dead-zone threshold
    private static final int MOUSE_MOVE_THRESHOLD = 8; // Minimum pixels mouse must move to update UI selection
    private boolean mouseActive = false; // Whether mouse is currently controlling selection
    private boolean draggingSlider = false; // Whether currently dragging a settings slider
    private int draggingSliderIndex = -1;   // Which setting index is being dragged
    private boolean mouseEnabled = true; // Track if mouse navigation is active
    private boolean controllerHudMouseDown = false; // Track controller A button for HUD editor drag
    private static final float CONTROLLER_CURSOR_SPEED = 8.0f; // Pixels per frame at full stick deflection
    private static Game instance; // For static access to instance fields (e.g. mouse position)
    private java.awt.Robot robot; // For warping the system cursor to follow controller input
    private Cursor blankCursor; // Hidden cursor for gameplay
    private Cursor defaultCursor; // Normal cursor for menus
    private double levelSelectScroll; // Target scroll position for level select
    private double levelSelectScrollAnimated; // Animated (smooth) scroll position
    private boolean planeTakeoffAnimation; // True when plane is flying up
    private double planeTakeoffTimer; // Animation timer
    private static final int PLANE_TAKEOFF_DURATION = 60; // 1 second
    private double shopScroll; // Target scroll position for shop
    private double shopScrollAnimated; // Animated (smooth) scroll position
    private double statsScroll; // Target scroll position for stats screen
    private double statsScrollAnimated; // Animated (smooth) scroll position
    private double settingsScroll; // Scroll offset for settings menu
    private double scrollCooldown; // Cooldown timer to prevent mouse selection while scrolling
    private double achievementsScroll; // Target scroll position for achievements
    private double achievementsScrollAnimated; // Animated (smooth) scroll position
    private double leaderboardViewScroll; // Target scroll position for leaderboard view
    private double leaderboardViewScrollAnimated; // Animated (smooth) scroll position
    private int selectedLeaderboardDifficulty; // 0=Easy, 1=Hard, 2=Master for leaderboard view tabs
    private GameState shopEnteredFrom; // Track where player came from when entering shop
    private GameState settingsEnteredFrom; // Track where settings was accessed from (MENU or PLAYING when paused)
    
    // Core systems
    private GameData gameData;
    private ShopManager shopManager;
    private Renderer renderer;
    private AchievementManager achievementManager;
    private PassiveUpgradeManager passiveUpgradeManager;
    private ComboSystem comboSystem;
    private SaveManager saveManager;
    private GlobalSaveData globalSaveData;
    private LeaderboardManager leaderboardManager;
    
    // Keybind & controller systems
    public static KeyBindManager keyBindManager;
    private ControllerManager controllerManager;
    public static boolean waitingForKeyBind = false;
    public static int rebindingActionIndex = -1; // Index into controls settings list
    public static boolean rebindingController = false; // True when rebinding a controller button (vs keyboard key)
    
    // Game objects
    private Player player;
    private Boss currentBoss;
    private List<Bullet> bullets;
    private List<Bullet> bulletPool; // Pool for recycling bullets
    private List<Particle> particles;
    private List<Particle> particlePool; // Pool for recycling particles
    private List<Particle> introParticles; // Separate particles for boss intro cinematic (screen-space)
    private List<BeamAttack> beamAttacks;
    
    // Flare system
    private List<Flare> flares;
    private double flareCooldownTimer;
    private static final double FLARE_BASE_COOLDOWN = 720; // 12 seconds at 60fps
    
    // Cached flare colors
    private static final Color FLARE_RED = new Color(255, 70, 30);
    private static final Color FLARE_ORANGE = new Color(255, 120, 40);
    private static final Color FLARE_YELLOW = new Color(255, 200, 60);
    
    // Particle limits for performance
    private static final int MAX_PARTICLES = 500; // Allow room for bomb detonation + impact particles
    private static final int MAX_BULLETS = 500; // Cap bullets for performance
    private static final int BULLET_POOL_PREWARM = 300; // Pre-allocate this many bullets to avoid mid-game lag
    private static final int PARTICLE_POOL_PREWARM = 150; // Pre-allocate this many particles
    private int particleSpawnThrottle = 0; // Throttle particle spawns during high load
    
    // Cached colors for performance
    private static final Color IMPACT_WHITE = new Color(255, 255, 255);
    private static final Color IMPACT_YELLOW = new Color(255, 255, 150);
    private static final Color IMPACT_RING = new Color(255, 255, 200);
    private static final Color FIRE_ORANGE = new Color(255, 100, 0);
    private static final Color FIRE_YELLOW = new Color(255, 200, 0);
    private static final Color FIRE_RED = new Color(255, 50, 0);
    private static final Color SMOKE_GRAY = new Color(80, 80, 80, 150);
    private static final Color BOSS_FIRE = new Color(255, 150, 0);
    private static final Color BOSS_FIRE_BRIGHT = new Color(255, 200, 50);
    private static final Color VULNERABILITY_GOLD = ColorPalette.TEXT_GOLD;
    private static final Color WARNING_RED = ColorPalette.ACCENT_RED;
    private static final Color PLAYER_DEATH_RED = ColorPalette.ACCENT_RED;
    private static final Color DODGE_GREEN = ColorPalette.SUCCESS_GREEN;
    private static final Color JET_TRAIL_COLOR = new Color(255, 150, 0, 200);
    private static final Color ENGINE_GLOW_BLUE = new Color(100, 150, 255, 180);
    private static final Color EXPLOSION_WARM = new Color(255, 200, 100, 200);
    private static final Color SHIELD_BLUE = new Color(136, 192, 208);
    private static final Color GRAZE_BLUE = new Color(100, 200, 255, 200);
    private static final Color GRAZE_GOLD = new Color(255, 215, 0, 255);
    private static final Color GRAZE_GREEN = new Color(150, 255, 150, 220);
    private static final Color SPAWN_CYAN = new Color(100, 200, 255, 220);
    private static final Color METAL_DEBRIS = new Color(160, 160, 170, 200);
    private static final Color SPARK_YELLOW = new Color(255, 220, 100, 220);
    private static final Color LUCKY_CHARM_GOLD = new Color(255, 215, 0, 200);
    private static final Color CRITICAL_HIT_GOLD = ColorPalette.ACCENT_YELLOW;
    private static final Color BOSS_HIT_RED = new Color(255, 80, 80);

    // Cached colors for particle creation (avoid per-event new Color())
    private static final Color SPAWN_GREEN = new Color(50, 200, 80);
    private static final Color BOMB_FLASH = new Color(255, 180, 80, 250);
    private static final Color BOMB_SPARK = new Color(255, 220, 150);
    private static final Color PERFECT_DODGE_FLASH = new Color(255, 255, 200, 200);
    private static final Color FROST_BEAM_ICE = new Color(136, 192, 208);
    private static final Color STARBURST_WARM = new Color(255, 255, 200);

    // Cached math constants
    private static final double TWO_PI = Math.PI * 2;
    
    // Spatial grid for bullet collision optimization
    private static final int GRID_CELL_SIZE = 50;
    private static final int GRID_WIDTH_MULTIPLIER = 10000; // For hash calculation
    private static final double INV_GRID_CELL_SIZE = 1.0 / GRID_CELL_SIZE; // Pre-computed inverse
    private Map<Integer, List<Bullet>> bulletGrid;
    private List<Bullet> nearbyBulletsCache = new ArrayList<>(); // Reusable list for performance
    
    // Thread pool for parallel game updates (bullet + particle processing)
    private static final int THREAD_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private ExecutorService updateThreadPool = Executors.newFixedThreadPool(THREAD_COUNT, r -> {
        Thread t = new Thread(r, "GameUpdate-Worker");
        t.setDaemon(true);
        return t;
    });
    
    // Off-screen render buffer for rendering on game thread (avoids blocking EDT)
    private volatile BufferedImage renderBuffer;
    private volatile BufferedImage displayBuffer;
    private final Object bufferSwapLock = new Object();

    /**
     * Create an optimally-backed BufferedImage. When GPU acceleration is enabled,
     * this returns a hardware-compatible image (managed by the GPU driver, allowing
     * fast VRAM blits). When disabled, falls back to a standard BufferedImage.
     * @param w         width
     * @param h         height
     * @param hasAlpha  true for ARGB (sprites/overlays), false for RGB (opaque buffers)
     */
    public static BufferedImage createOptimalImage(int w, int h, boolean hasAlpha) {
        if (enableGPUAcceleration) {
            try {
                GraphicsConfiguration gc = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
                int transparency = hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE;
                return gc.createCompatibleImage(w, h, transparency);
            } catch (Exception e) {
                // Fallback if anything goes wrong
            }
        }
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        return new BufferedImage(w, h, type);
    }
    
    // Cached debug font (avoid per-frame allocation)
    private Font debugTrackFont;
    
    // Player trail effect
    private double trailSpawnTimer;
    
    // Input
    private boolean[] keys;
    private boolean eKeyPressed; // Track E key state to prevent continuous activation
    
    // Animation
    private double gradientTime;
    
    // Item unlock animation
    private boolean itemUnlockAnimation;
    private boolean itemUnlockDismissing; // True when animation is fading out
    private double itemUnlockTimer;
    private double itemUnlockDismissTimer; // Timer for fade-out animation
    private String unlockedItemName;
    private String unlockedItemDescription; // Description of newly unlocked item
    private boolean showEquipPrompt; // True if should ask to equip item
    private int newItemIndex; // Index of newly unlocked item
    private UIButton[] equipButtons; // [Yes, No] buttons
    private int selectedEquipButton; // 0 = Yes, 1 = No
    private static final int ITEM_UNLOCK_DURATION = 300; // 5 seconds (smooth reveal)
    private static final int ITEM_DISMISS_DURATION = 30; // 0.5 seconds fade out
    
    // Contract unlock animation
    private boolean contractUnlockAnimation;
    private boolean contractUnlockDismissing;
    private double contractUnlockTimer;
    private double contractUnlockDismissTimer;
    private static final int CONTRACT_UNLOCK_DURATION = 360; // 6 seconds (smooth reveal with more info)
    private static final int CONTRACT_DISMISS_DURATION = 30;
    
    // Endless mode unlock animation
    private boolean endlessUnlockAnimation;
    private boolean endlessUnlockDismissing;
    private double endlessUnlockTimer;
    private double endlessUnlockDismissTimer;
    private static final int ENDLESS_UNLOCK_DURATION = 360;
    private static final int ENDLESS_DISMISS_DURATION = 30;
    
    // Passive upgrade unlock animation
    private boolean passiveUnlockAnimation;
    private boolean passiveUnlockDismissing;
    private double passiveUnlockTimer;
    private double passiveUnlockDismissTimer;
    private String unlockedPassiveName;
    private String unlockedPassiveDescription;
    private java.util.Queue<PassiveUpgrade> pendingPassiveUnlocks; // Queue of upgrades to show
    private static final int PASSIVE_UNLOCK_DURATION = 120; // 2 seconds
    private static final int PASSIVE_DISMISS_DURATION = 20; // ~0.33 seconds
    
    // UI Transitions
    private GameState previousState;
    private float stateTransitionProgress; // 0.0 = old state, 1.0 = new state
    private static final float TRANSITION_SPEED = 0.08f; // Speed of state transitions
    
    // Visual effects
    private double screenShakeX;
    private double screenShakeY;
    private double screenShakeIntensity;
    
    // Combo system
    private int dodgeCombo;
    private double comboTimer;
    private static final int COMBO_TIMEOUT = 180; // 3 seconds
    
    // Boss intro cinematics - Anime shonen sequential reveal
    private boolean demoIntroActive; // true when running intro demo from menu (U key)
    private boolean bossIntroActive;
    private double bossIntroTimer;
    private static final int BOSS_INTRO_DURATION = 380; // 6-phase anime sequential reveal
    private String bossIntroText;
    private double bossIntroPlayerX; // Player X position (center â†’ left)
    private double bossIntroPlayerY; // Player Y position (computed from phase)
    private double bossIntroBossX;   // Boss X position (off-screen â†’ right)
    private double bossIntroBossY;   // Boss Y position (computed from phase)
    private double bossIntroVsScale; // VS text scale animation
    private double bossIntroFlash;   // Flash/slash intensity
    private int bossIntroPhase;      // 0=flash, 1=player spotlight, 2=slash transition, 3=boss reveal, 4=VS clash, 5=fade out
    
    // Pause menu
    private boolean isPaused;
    private int selectedPauseItem;
    private boolean unpauseCountdownActive;
    private double unpauseCountdownTimer;
    private static final int UNPAUSE_COUNTDOWN_DURATION = 180; // 3 seconds (60 fps * 3)
    
    // Level confirmation
    private int selectedLevelToStart = -1; // The level player wants to start
    private int selectedConfirmItem = 0; // 0 = Yes, 1 = No
    private boolean isConfirmingResume = false; // Track if confirming resume vs new game
    
    // Saved game state for resume feature
    private boolean hasSavedGame = false;
    private ResumeState savedResumeState; // Serializable state for cross-session resume
    private Player savedPlayer;
    private Boss savedBoss;
    private List<Bullet> savedBullets;
    private List<Particle> savedParticles;
    private List<BeamAttack> savedBeamAttacks;
    private List<DamageNumber> savedDamageNumbers;
    private int savedLevel;
    private int savedRiskContractType;
    private boolean savedRiskContractActive;
    private double savedRiskContractMultiplier;
    private int savedSurvivalTime;
    private boolean savedBossVulnerable;
    private double savedVulnerabilityTimer;
    private double savedInvulnerabilityTimer;
    private int savedBossHitCount;
    private boolean savedTookDamageThisBoss;
    private int savedDodgeCombo;
    private boolean savedShieldActive;
    private int savedShieldHits;
    private double savedComboTimer;
    private boolean savedBossIntroActive;
    private double savedBossIntroTimer;
    private String savedBossIntroText;
    private boolean savedWaitingForRespawn;
    private double savedRespawnDelayTimer;
    private double savedRespawnInvincibilityTimer;
    private boolean savedBossDeathAnimation;
    private double savedDeathAnimationTimer;
    private double savedBossDeathScale;
    private double savedBossDeathRotation;
    private double savedStoppedMovingTimer;
    private double savedGameTimeSeconds;
    
    // Achievement notification
    private List<Achievement> pendingAchievements;
    private double achievementNotificationTimer;
    private static final int ACHIEVEMENT_NOTIFICATION_DURATION = 180; // 3 seconds
    
    // Boss damage numbers
    private List<DamageNumber> damageNumbers;
    
    // Perfect boss tracking (no damage taken)
    private boolean tookDamageThisBoss;
    private int consecutivePerfectBosses;
    private int totalGrazesThisRun;
    
    // Boss mechanics
    private boolean bossVulnerable;
    private double vulnerabilityTimer;
    private double invulnerabilityTimer; // Prevents boss from going vulnerable at level start
    private int bossHitCount; // Number of times boss has been hit (max 3)
    private double bossFlashTimer; // Flash effect when boss takes damage
    private static final int BOSS_MAX_HITS = 3;
    private static final int VULNERABILITY_DURATION = 1200; // 20 second window
    private static final int INVULNERABILITY_DURATION = 180; // 3 seconds at start
    private boolean bossDeathAnimation;
    private double deathAnimationTimer;
    private static final int DEATH_ANIMATION_DURATION = 180; // 3 seconds
    private double bossDeathScale;
    private boolean waitingForRespawn; // Waiting after non-fatal boss hit
    private double respawnDelayTimer; // Timer before respawning player
    private static final int RESPAWN_DELAY = 90; // 1.5 seconds delay
    private double bossDeathRotation;
    
    // Polish effects
    private static final double GRAZE_DISTANCE = 25; // Distance for graze detection
    private static final double CLOSE_CALL_DISTANCE = 15; // Very close graze
    private static final double PERFECT_DODGE_DISTANCE = 8; // Frame-perfect dodge
    private static final double PROXIMITY_WARNING_DISTANCE = 120; // Distance for proximity hum warning
    private int grazeScore = 0; // Accumulate graze score
    private int hitPauseTimer = 0; // Brief pause on impact
    private double screenFlashTimer = 0; // Screen flash on player hit
    private double itemReadyFlickerTimer = 0; // Flicker when item becomes ready
    private double itemCompleteFlashTimer = 0; // Flash when item effect completes
    private boolean wasItemReady = false; // Track previous ready state
    private boolean wasItemActive = false; // Track previous active state
    private double achievementFlashTimer = 0; // Flash when achievement unlocked
    private double bossIntroFlashTimer = 0; // Flash when boss intro appears
    private double countdownFlashTimer = 0; // Flash on each countdown tick
    private double bossHitFlashTimer = 0; // Flash when boss is hit
    private double deathFlashTimer = 0; // Red vignette on player death
    private int lastCountdownSecond = -1; // Track countdown changes
    
    // Type Purge item effect (chromatic screen flash)
    private double typePurgeFlashTimer = 0;
    private Color typePurgeFlashColor = Color.WHITE;
    
    // Perfect Dodge system
    private double perfectDodgeIFrames = 0; // Brief invincibility after perfect dodge
    private static final int PERFECT_DODGE_IFRAMES = 8; // 8 frames of invincibility
    private double perfectDodgeFlashTimer = 0; // Visual flash effect
    
    // Risk Contract system
    private boolean riskContractActive = false;
    private int riskContractType = 0; // 0 = none, 1 = 2x bullets, 2 = faster bullets, 3 = no active items, 4 = can't stop
    private double riskContractMultiplier = 1.0; // Money multiplier from contract
    private int selectedRiskContract = 0; // Currently selected contract in menu
    private static final String[] RISK_CONTRACT_NAMES = {"No Contract", "Bullet Storm", "Speed Demon", "Powerless", "Can't Stop"};
    private static final String[] RISK_CONTRACT_DESCRIPTIONS = {
        "Play normally with no modifiers",
        "Double the bullets, double the money! (2x)",
        "Bullets move 50% faster (1.75x)",
        "All active items disabled (1.5x)",
        "Keep moving or die! (2.5x)"
    };
    private static final double[] RISK_CONTRACT_MULTIPLIERS = {1.0, 2.0, 1.75, 1.5, 2.5};
    
    // Can't Stop contract tracking
    private double stoppedMovingTimer = 0;
    private boolean hasMovedOnce = false; // Track if player has moved at least once
    private static final int STOPPED_GRACE_PERIOD = 60; // 1 second before death
    private static final double MIN_MOVEMENT_SPEED = 0.5; // Minimum speed to count as moving
    
    // Game version
    public static final String GAME_VERSION = "v0.9.0";

    // Demo mode — set to true when building the public demo
    public static final boolean DEMO_MODE = false;
    public static final int DEMO_MAX_LEVEL = 3;
    
    // Campaign and endless mode constants
    public static final int CAMPAIGN_LEVELS = 28;
    public static final int ENDLESS_SLOT = 29; // Journey map slot for endless mode
    
    // Attack Introduction System
    // Each attack intro has: ID, Level it appears, Name, Description, Category
    // Attacks unlock on 1st and 3rd levels (regular + mega boss) of each set
    // Bullet difficulty ranking (easiest to hardest):
    // NORMAL(1), LARGE(3), FAST(4), SPIRAL(6), BOUNCING(7), ACCELERATING(9), 
    // GRENADE(10), WAVE(12), HOMING(13), BOMB(15), NUKE(18)
    private static final String[][] ATTACK_INTROS = {
        // {attackId, level, name, description, category}
        // Level 1 - NORMAL bullets (basic patterns only)
        {"basic_bullets", "1", "Basic Bullets", "Simple bullets fired at you.\nDodge them by moving!", "Pattern"},
        {"spiral_attack", "1", "Spiral Attack", "Bullets spiral outward from the boss.\nFind the gaps between the spirals!", "Pattern"},
        {"circle_attack", "1", "Circle Attack", "Bullets fire in all directions!\nDodge them by moving!", "Pattern"},
        // Level 2 - Targeted pattern (still NORMAL bullets)
        {"aimed_shots", "2", "Aimed Shots", "The boss aims directly at you!\nKeep moving to avoid being hit.", "Targeted"},
        // Level 3 - LARGE bullets + First Mega
        {"large_bullets", "3", "Large Bullets", "Massive bullets that are hard to avoid.\nGive them plenty of room!", "Special"},
        {"mega_burst", "3", "Mega Burst", "Mega bosses unleash massive bursts!\nA cone of danger!", "Mega"},
        // Level 4 - FAST bullets + Wave pattern
        {"fast_bullets", "4", "Fast Bullets", "High-speed bullets aimed at you!\nDodge early before they reach you!", "Targeted"},
        {"wave_attack", "4", "Wave Attack", "Bullets fire in a wave pattern.\nWatch the rhythm and dodge through!", "Pattern"},
        // Level 5 - Random pattern (still using unlocked bullets)
        {"random_spray", "5", "Random Spray", "Bullets scatter randomly!\nStay alert and react quickly.", "Pattern"},
        // Level 6 - SPIRAL bullets + Second Mega
        {"spiral_bullets", "6", "Spiral Bullets", "Bullets that curve as they fly!\nPredict their paths!", "Pattern"},
        {"mega_cross", "6", "Mega Cross", "A deadly cross pattern of bullets!\nStand between the arms to survive!", "Mega"},
        // Level 7 - BOUNCING bullets + Mixed attack
        {"bouncing_bullets", "7", "Bouncing Bullets", "Bullets that bounce off walls!\nWatch out for unexpected rebounds!", "Special"},
        {"mixed_attack", "7", "Mixed Attack", "A combo of spiral and bouncing bullets!\nStay on your toes!", "Mixed"},
        // Level 9 - ACCELERATING bullets + Third Mega
        {"accelerating_bullets", "9", "Accelerating Bullets", "Bullets that speed up over time.\nDodge early before they become too fast!", "Special"},
        {"mega_star", "9", "Mega Star", "Star-shaped bullet patterns!\nFind the gaps in the points!", "Mega"},
        // Level 10 - GRENADE bullets + Beam
        {"grenades", "10", "Grenades", "Explosive projectiles that arc and detonate.\nStay clear of the blast radius!", "Explosive"},
        {"beam_attack", "10", "Beam Attack", "A powerful beam sweeps across the arena.\nMove to the safe zone before it fires!", "Beam"},
        // Level 12 - WAVE bullets + Fourth Mega + Shockwave
        {"wave_bullets", "12", "Wave Bullets", "Bullets that move in a wavy pattern!\nPredict their sine wave motion!", "Pattern"},
        {"shockwave", "12", "Shockwave", "A damaging wave expands from the boss.\nMove away to avoid knockback!", "AOE"},
        {"mega_hex", "12", "Mega Hex", "Hexagonal bullet formations!\nPrecision dodging required!", "Mega"},
        // Level 13 - HOMING bullets + Twirl
        {"homing_bullets", "13", "Homing Bullets", "Bullets that track your position!\nKeep moving to outrun them!", "Special"},
        {"twirl_attack", "13", "Twirl Attack", "The boss spins while circling the arena.\nStay mobile and watch its path!", "Movement"},
        // Level 15 - BOMB bullets + Fifth Mega
        {"bombs", "15", "Bombs", "Large explosive projectiles!\nThey explode into smaller bullets on impact!", "Explosive"},
        {"mega_spiral", "15", "Mega Spiral", "Layered spirals at different speeds!\nNavigate the expanding rings!", "Mega"},
        // Level 18 - NUKE bullets + Sixth Mega
        {"nuke_bombs", "18", "Nuke Bombs", "Massive explosions that fill the screen.\nFind the safe spots quickly!", "Explosive"}
    };
    
    // Map attack IDs to boss pattern types
    // Pattern types: 0=Spiral, 1=Circle, 2=Aimed, 3=Wave, 4=Random, 5=Fast, 6=Large,
    //                7=Mixed, 8=SpiralBullets, 10=Accelerating, 11=WaveBullets,
    //                12=Bombs, 13=Grenades, 14=Nukes, 15=Homing, 16=Bouncing
    private static int getPatternTypeForAttack(String attackId) {
        switch (attackId) {
            case "basic_bullets": return 2;   // Aimed shots (simple version)
            case "spiral_attack": return 0;
            case "circle_attack": return 1;
            case "aimed_shots": return 2;
            case "wave_attack": return 3;
            case "random_spray": return 4;
            case "fast_bullets": return 5;
            case "large_bullets": return 6;
            case "mixed_attack": return 7;
            case "spiral_bullets": return 8;
            case "accelerating_bullets": return 10;
            case "wave_bullets": return 11;
            case "bombs": return 12;
            case "grenades": return 13;
            case "nuke_bombs": return 14;
            case "homing_bullets": return 15;
            case "bouncing_bullets": return 16;
            // Special attacks (beam, shockwave, twirl, mega) don't use pattern types
            default: return -1;
        }
    }
    
    /**
     * Get allowed pattern types for a given level based on ATTACK_INTROS.
     * Only returns bullet pattern types, not special attacks (beam, shockwave, etc.)
     */
    private static java.util.Set<Integer> getAllowedPatternsForLevel(int level) {
        java.util.Set<Integer> patterns = new java.util.HashSet<>();
        for (String[] intro : ATTACK_INTROS) {
            int unlockLevel = Integer.parseInt(intro[1]);
            if (unlockLevel <= level) {
                int patternType = getPatternTypeForAttack(intro[0]);
                if (patternType >= 0) {
                    patterns.add(patternType);
                }
            }
        }
        return patterns;
    }
    
    // Current attack intro being displayed
    private String currentAttackIntroId = null;
    private String currentAttackIntroName = null;
    private String currentAttackIntroDescription = null;
    private String currentAttackIntroCategory = null; // Pattern type/category
    private List<String> pendingAttackIntros = new ArrayList<>(); // Queue of intros to show
    private BufferedImage attackIntroImage = null; // Attack intro image
    private static java.util.Map<String, BufferedImage> attackIntroImageCache = new java.util.HashMap<>(); // Cache loaded images
    
    // Debug menu navigation
    private int selectedDebugOption = 0;
    private static final int DEBUG_OPTION_COUNT = 15; // Total number of debug menu options
    private int debugSetLevelValue = 1; // Value for "Set Unlocked Level" cheat (1-28)
    private int debugLeaderboardLevel = 1; // Value for "Test Leaderboard" level picker (1-28)
    private java.util.Queue<ActiveItem.ItemType> debugItemPopupQueue; // Queue for debug item popup preview
    private boolean debugShowContractAfterItems = false; // Show contract popup after all item popups
    
    // Debug Attack Showcase Mode (for taking screenshots)
    private boolean debugShowcaseMode = false;
    private int debugShowcaseIndex = 0; // Current attack/item being showcased
    private int debugShowcaseTimer = 0; // Timer for cycling attacks
    private boolean debugShowcaseInGameplay = false; // True when showing gameplay, false when showing selection
    private static final int DEBUG_SHOWCASE_INTERVAL = 900; // 15 seconds at 60fps
    private int savedRealLevel = 1; // Save the actual game level before entering showcase
    private ActiveItem savedEquippedItem = null; // Save the equipped item before entering showcase
    private int showcaseTab = 0; // 0 = Attacks, 1 = Items
    private boolean debugShowcaseUnlockAll = false; // Unlock all showcase content for viewing
    
    // Active Items showcase data: {itemTypeName, level, name, description}
    // Ordered by power level (worst to best)
    private static final String[][] ITEM_SHOWCASE = {
        {"LUCKY_CHARM", "3", "Pool of Loot", "Spawn a money circle that lasts 20 seconds!\nStand in it for bonus money. 35 second cooldown"},
        {"SHIELD", "6", "Shield", "Summon 3 orbiting shields that block bullets!\nShields persist until hit. 5s first use, then 20s cooldown"},
        {"BOMBS", "7", "Bombs", "Rain down explosive bombs across the screen!\n6 second cooldown, staggered explosions"},
        {"STUN", "9", "Stun", "Freeze the boss - can't move or shoot!\n10 second cooldown, lasts 1 second"},
        {"IMPULSE", "21", "Impulse", "Push all bullets away from you\n5 second cooldown, instant effect"},
        {"TIME_SLOW", "15", "Time Slow", "Slow bullets, beams & boss by 85%\n10 second cooldown, lasts 4 seconds"},
        {"TYPE_PURGE", "12", "Chromatic Purge", "Erase ALL bullets of a random type\n15 second cooldown, screen flashes their color"},
        {"DASH", "18", "Dash", "Quick dash with invincibility frames\n2 second cooldown, soft aim assist near boss"},
        {"FROST_BEAM", "24", "Frost Beam", "Freeze bullets in a powerful icy beam\n5 second cooldown, lasts 2 seconds"}
    };
    
    // Attack showcase UI
    private int showcaseHoveredButton = -1; // 0 = left arrow, 1 = right arrow, 2 = start button
    private double showcaseCarouselOffset = 0; // Smooth animation offset for carousel sliding
    private double showcaseTargetOffset = 0; // Target offset (0 when settled)
    
    // Game feel effects
    private double hitFreezeFrames = 0; // Freeze frames on boss damage
    private double bossHitCameraHoldTimer = 0; // Hold camera at collision point after boss hit
    private double slowMotionFactor = 1.0; // Slow-motion multiplier (1.0 = normal)
    private double slowMotionTimer = 0; // Timer for slow-motion effect
    private double comboPulseScale = 1.0; // Scale pulse on combo increase
    private double cameraBreathOffset = 0; // Subtle camera breathing
    private double cameraBreathTime = 0; // Time for camera breathing sine wave
    
    // Smooth UI animations
    private double displayedScore = 0; // Animated score display
    private double displayedMoney = 0; // Animated money display
    
    // Death sequence system (replaces old resurrection)
    public boolean deathSequenceActive = false;
    public double deathExplosionX = 0, deathExplosionY = 0;
    public int deathCameraHoldTimer = 0;
    public int cameraPanBackTimer = 0;
    public double cameraPanStartX = 0, cameraPanStartY = 0;
    public int respawnBlinkTimer = 0;
    public boolean playerHidden = false;
    public int missilesUsedThisRun = 0;
    private static final int DEATH_CAMERA_HOLD_FRAMES = 90;
    private static final int CAMERA_PAN_BACK_FRAMES = 60;
    private static final int RESPAWN_BLINK_FRAMES = 180;
    
    // Afterimage trail for player
    private double[] afterimageX = new double[5];
    private double[] afterimageY = new double[5];
    private double[] afterimageAlpha = new double[5];
    private double afterimageTimer = 0;
    
    // Active item effects
    private boolean playerInvincible; // For DASH i-frames
    private boolean shieldActive; // For SHIELD item - 3 orbiting shields
    private int shieldHits; // Number of shields remaining (3 max, decrements on hit)
    private double shieldOrbitAngle = 0; // Rotation angle for orbiting shields
    private boolean shieldFirstUse = true; // Track if this is the first use (5s cooldown vs 20s)
    private double respawnInvincibilityTimer; // Shorter invincibility after respawn
    private double dashSpeedMultiplier; // For DASH item
    private double spawnProtectionX; // X position where spawn protection was activated
    private double spawnProtectionY; // Y position where spawn protection was activated
    private static final double SPAWN_PROTECTION_RADIUS = 150; // Radius player can move before losing protection
    
    // STUN item effect - freezes boss
    private boolean bossStunned = false;
    private int bossStunTimer = 0;
    private double bossStunShakeOffset = 0;
    
    // Dynamic zoom effects for active items
    private double effectZoom = 1.0; // Current zoom from item effects (1.0 = normal)
    private double targetEffectZoom = 1.0; // Target zoom to interpolate towards
    private static final double ZOOM_LERP_SPEED = 0.08; // How fast zoom transitions
    private static final double TIME_SLOW_ZOOM = 1.15; // Zoom in during time slow (larger = more zoom)
    private static final double DASH_ZOOM = 0.92; // Zoom OUT during dash (speed effect)
    private static final double IMPULSE_ZOOM = 1.12; // Zoom in during impulse (push effect)
    private boolean dashZoomActive = false; // Track dash zoom state
    private double dashZoomTimer = 0; // Timer for dash zoom effect (short impulse)
    private boolean impulseZoomActive = false; // Track impulse zoom state
    private double impulseZoomTimer = 0; // Timer for impulse zoom effect
    
    // FROST_BEAM angle - smoothly follows player facing
    private double frostBeamAngle = 0;
    private static final double FROST_BEAM_TURN_SPEED = 0.025; // How fast the beam rotates to follow player (slower for weighty feel)
    // Frost beam animation state - two phase: thin extend, then thicken
    private double frostBeamProgress = 0; // 0 = off, 0-0.3 = extending thin, 0.3-0.6 = thickening, 0.6+ = full
    private boolean frostBeamExtending = false;
    private boolean frostBeamRetracting = false;
    private static final double FROST_BEAM_EXTEND_SPEED = 0.05; // Speed of animation
    private static final double FROST_BEAM_RETRACT_SPEED = 0.08; // Speed of retraction
    private boolean frostBeamShakeTriggered = false; // Track if we've done the mid-animation shake
    private double frostBeamStopDistance = -1; // Distance to first bullet hit (-1 = no hit, beam goes full length)
    private double frostBeamRetractPhase = 0; // 0-1 retraction phase (0 = start, 1 = done)
    
    // BOMB scattered explosion effect
    private java.util.List<double[]> bombExplosionQueue = new java.util.ArrayList<>(); // Each: {x, y, delay}
    private int bombExplosionTimer = 0;
    private static final double BOMB_EXPLOSION_RADIUS = 90; // Radius of each bomb explosion
    
    // LUCKY_CHARM money circle effect - supports multiple circles
    private java.util.List<double[]> moneyCircles = new java.util.ArrayList<>(); // Each: {x, y, timer}
    private static final int MONEY_CIRCLE_DURATION = 1200; // 20 seconds at 60fps (was permanent)
    private static final double MONEY_CIRCLE_RADIUS = 160; // Bigger circle (was 120)
    private static final int MONEY_CIRCLE_BONUS = 1; // Money per tick while standing in circle (reduced)
    
    // === TUTORIAL SYSTEM ===
    public boolean tutorialMode = false;
    public int tutorialStep = 0; // 0-based index into TUTORIAL_STEPS
    public boolean tutorialPopupActive = false;
    public String tutorialPopupTitle = "";
    public String[] tutorialPopupBody = {};
    private boolean tutorialStepCompleted = false;
    public double tutorialPlayerMoveDistance = 0; // Track movement for step 1
    public int tutorialGrazeCount = 0; // Track grazes for step 2
    private boolean tutorialPlayerDied = false; // Track death for step 3
    private boolean tutorialItemUsed = false; // Track item use for step 4
    public int tutorialShieldBlockCount = 0; // Track shield blocks for step 4
    private boolean tutorialShopPurchased = false; // Track actual purchase for step 6
    private boolean tutorialShopVisited = false; // Track shop visit

    private double tutorialPrevPlayerX = 0, tutorialPrevPlayerY = 0; // For movement tracking
    public boolean showTutorialPrompt = false; // First-save tutorial prompt
    public int tutorialPromptSelection = 0; // 0 = Yes, 1 = No
    public boolean tutorialCompleteScreen = false; // Show completion screen with options
    public int tutorialCompleteSelection = 0; // 0 = Leave, 1 = Play Again
    public String tutorialTaskText = ""; // Current task description for task bar
    public double tutorialTaskProgress = 0; // 0.0 to 1.0 progress
    public boolean tutorialTaskHasBar = false; // Whether to show progress bar
    
    // Tutorial showcase return path
    private GameState showcaseEnteredFrom = GameState.MENU;
    
    // Tutorial cinematic slow-down phases
    // 0 = NONE, 1 = SLOWING_IN, 2 = POPUP_SHOWN, 3 = SLOWING_OUT
    private int tutorialSlowdownPhase = 0;
    private int tutorialSlowdownTimer = 0;
    private int tutorialPopupInputDelay = 0; // Frames before popup can be dismissed (120 = 2 seconds)
    
    // Tutorial saved state (to restore after tutorial ends)
    private int tutorialSavedMoney = 0;
    private int tutorialSavedMissiles = 0;
    private int tutorialSavedLevel = 1;
    private ActiveItem tutorialSavedItem = null;
    private int[] tutorialSavedUpgradeLevels = null; // Saved passive upgrade levels
    private int[] tutorialSavedActiveUpgradeLevels = null; // Saved active upgrade levels
    private int tutorialSavedBestRunLevel = 0; // Saved best run level for shop filtering
    
    private static final int TUTORIAL_MOVE_GOAL = 1000;
    private static final int TUTORIAL_GRAZE_GOAL = 5;
    private static final int TUTORIAL_SHIELD_GOAL = 3;
    
    // Tutorial step definitions: {title, body line 1, body line 2, ...}
    // Order: Welcome, Movement, Dodging, Death, Active Items, Defeat Boss, Shop, Settings, Complete
    // Tokens: {MOVE} = movement keys, {USE_ITEM} = use item key, {PAUSE} = pause key
    // Color tokens: {C:GOLD}text{/C}, {C:CYAN}text{/C}, {C:RED}text{/C}, {C:GREEN}text{/C}, {C:ORANGE}text{/C}
    // Key references are rendered dynamically with sprites in drawTutorialPopup
    private static final String[][] TUTORIAL_STEPS = {
        {"WELCOME TO MISSILE MAN!", "Let's learn the basics of the game.", "Press any key to continue through each step."},
        {"MOVEMENT", "Use {MOVE} to move around!", "Fly around the screen to fill the bar."},
        {"DODGING", "Bullets are coming! {C:RED}Dodge{/C} them to survive.", "Fly close to bullets to {C:CYAN}\"Graze\"{/C} them.", "Graze {C:GOLD}5{/C} bullets to continue."},
        {"DEATH & RESPAWN", "Don't worry — if you get hit, you'll {C:GREEN}respawn{/C}!", "In a real game, this costs a {C:ORANGE}missile{/C}.", "Get hit once to continue."},
        {"ACTIVE ITEMS", "Press {USE_ITEM} to activate your {C:CYAN}Shield{/C}!", "A Shield has been equipped for you.", "Block {C:GOLD}3{/C} bullets with your Shield."},
        {"DEFEAT THE BOSS", "Time to defeat the boss!", "The boss has an {C:RED}invulnerability shield{/C}.", "It activates when the game starts, after you {C:ORANGE}hit the boss{/C},", "and after you {C:ORANGE}get hit{/C}. Attack when the {C:CYAN}shield isn't visible{/C}!", "The boss only has {C:GOLD}1 HP{/C}. Fly into it to attack!"},
        {"THE SHOP", "Welcome to the {C:GOLD}Shop{/C}!", "Here you can buy upgrades with money you earn.", "Buy something from the shop to continue."},
        {"TUTORIAL COMPLETE!", "{C:GREEN}Great job!{/C} You're ready for the real thing.", "Your {C:GOLD}\"Training Thrusters\"{/C} achievement has been unlocked!", "Customize controls and more in {C:CYAN}Settings{/C} (pause menu).", "Press any key to return to the menu."}
    };

    // Camera tracking with smooth interpolation
    private double cameraX = 0;
    private double cameraY = 0;
    private static final double CAMERA_SMOOTHING = 0.03; // Smooth camera follow
    private static final double CAMERA_DEADZONE = 50; // Distance from center before camera moves
    private static final double CAMERA_MAX_OFFSET = 150; // Max pixels camera can move from center
    private static final double CAMERA_HORIZONTAL_OFFSET = 0; // Horizontal camera offset in pixels
    private boolean introPanActive = false;
    private double introPanTimer = 0;
    private static final int INTRO_PAN_DURATION = 240; // 4 seconds total (2s boss entrance, 2s pan back)
    private double bossEntranceY = -200; // Boss starts above screen
    
    // Settings
    private int selectedSettingsItem;
    private int selectedSettingsCategory = 0; // 0=Graphics, 1=Audio, 2=Gameplay, 3=Debug, 4=Controls, 5=HUD
    public static HUDLayout hudLayout = HUDLayout.defaultLayout(); // Customizable HUD element positions
    public static boolean isFullscreen = true; // Start in fullscreen by default
    public static boolean enableGradientAnimation = true;
    public static boolean enableGrainEffect = false;
    public static boolean enableParticles = true;
    public static boolean enableShadows = true;
    public static int shadowQuality = 2; // 0=Off, 1=Low (3 layers), 2=Medium (6 layers), 3=High (10 layers)
    public static boolean enableBloom = true;
    public static boolean enableMotionBlur = false;
    public static boolean enableChromaticAberration = true;
    public static boolean enableVignette = true;
    public static boolean enableHitboxes = false; // Debug: show hitboxes for all objects
    public static boolean showTrackName = false; // Debug: show current music track name on screen
    public static int gradientQuality = 1; // 0=Low (1 layer), 1=Medium (2 layers), 2=High (3 layers)
    public static int backgroundMode = 1; // 0=Gradient, 1=Parallax Images, 2=Static Image
    public static double cameraZoom = 1.0; // 0.75 to 1.5 - zoom level during gameplay
    public static boolean enableUIParallax = true; // UI elements shift slightly with camera for depth
    public static int resolutionPreset = 3; // 0=1280x720, 1=1366x768, 2=1600x900, 3=1920x1080, 4=2560x1440, 5=3840x2160
    public static boolean enableVSync = true; // VSync enabled/disabled
    public static int fpsLimit = 1; // 0=30 FPS, 1=60 FPS, 2=120 FPS, 3=144 FPS, 4=Unlimited
    public static boolean enableAntiAliasing = false; // Anti-aliasing disabled by default for performance
    public static int uiScale = 1; // 0=Small (0.85x), 1=Medium (1.0x), 2=Large (1.2x)
    
    // GPU Acceleration settings
    public static boolean gpuAvailable = false; // Detected at startup
    public static boolean enableGPUAcceleration = false; // Master toggle
    public static int gpuPipelineType = 0; // 0=Auto, 1=OpenGL, 2=Direct3D
    public static int bufferStrategyMode = 1; // 0=Double buffer, 1=Triple buffer
    
    // Settings snapshot (for Apply/Revert system)
    public static boolean settingsDirty = false; // True if any setting changed since last apply/snapshot
    public static boolean settingsNeedsRestart = false; // True if a change requires window restart (pipeline, resolution)
    public static boolean showSettingsWarning = false; // True when showing unsaved-changes warning dialog
    public static int settingsWarningSelection = 0; // 0=Apply & Exit, 1=Discard & Exit, 2=Cancel
    public static boolean controlsKeyboardExpanded = true; // Controls tab: keyboard keybinds section expanded
    public static boolean controlsControllerExpanded = true; // Controls tab: controller keybinds section expanded
    // --- Snapshot fields (saved when entering Settings) ---
    private static int snap_resolutionPreset;
    private static boolean snap_enableVSync;
    private static int snap_fpsLimit;
    private static boolean snap_enableAntiAliasing;
    private static int snap_backgroundMode;
    private static boolean snap_enableGradientAnimation;
    private static int snap_gradientQuality;
    private static boolean snap_enableGrainEffect;
    private static boolean snap_enableParticles;
    private static boolean snap_enableShadows;
    private static int snap_shadowQuality;
    private static boolean snap_enableBloom;
    private static boolean snap_enableMotionBlur;
    private static boolean snap_enableChromaticAberration;
    private static boolean snap_enableVignette;
    private static double snap_cameraZoom;
    private static boolean snap_enableUIParallax;
    private static int snap_uiScale;
    private static boolean snap_enableGPUAcceleration;
    private static int snap_gpuPipelineType;
    private static int snap_bufferStrategyMode;
    private static boolean snap_soundEnabled;
    private static float snap_masterVolume;
    private static float snap_sfxVolume;
    private static float snap_uiVolume;
    private static float snap_musicVolume;
    private static boolean snap_spatialAudioEnabled;
    private static int snap_countdownMode;
    private static boolean snap_enableHitboxes;
    private static boolean snap_showTrackName;
    private static int[] snap_keyBinds;
    private static int snap_presetOrdinal;
    private static boolean snap_isFullscreen;
    
    // Sound Manager
    private SoundManager soundManager;
    
    // Quit confirmation
    private double escapeTimer; // Timer for double-tap escape confirmation
    private static final int ESCAPE_TIMEOUT = 120; // 2 seconds to press escape again
    
    // Timer and FPS tracking
    private long gameStartTime; // Time when current game started (in milliseconds)
    private double gameTimeSeconds; // Current game time in seconds
    private int currentFPS;
    private long lastFPSTime;
    private int frameCount;
    private double bossKillTime; // Time when boss was killed

    // Leaderboard screen state
    private int leaderboardScreenTimer; // Frame counter for animation phases
    private boolean leaderboardAnimSkipped; // True when player presses key to skip animation
    private boolean leaderboardReadyToExit; // True when animation is done and waiting for key to exit
    private int leaderboardCompletedLevel; // The level that was just completed (1-indexed)
    private GameMode leaderboardCompletedDifficulty; // The difficulty of the completed level
    private boolean debugSkipUsed; // True if level was skipped via debug T key
    private boolean lbSfxTimeReveal; // Phase A: time reveal sound played
    private boolean lbSfxPanelSlide; // Phase B: panel slide sound played
    private boolean lbSfxResult;     // Phase B: new record / first clear fanfare played
    
    // Loading progress
    private volatile int loadingProgress = 0;
    private volatile int targetLoadingProgress = 0;
    private double displayedLoadingProgress = 0.0;
    private volatile boolean loadingComplete = false;
    
    // Loading-screen window expansion
    private java.awt.Rectangle loadingExpandBounds = null;
    private volatile boolean loadingExpanded = false;
    
    /** Called by App to supply the full-screen bounds for expansion at 40% loaded. */
    public void setLoadingExpandBounds(java.awt.Rectangle bounds) {
        this.loadingExpandBounds = bounds;
    }
    
    // Save system
    private int selectedSaveSlot = 0; // Currently selected save slot index in the list
    private java.util.List<SaveManager.SaveMetadata> saveMetadataCache = new java.util.ArrayList<>(); // Dynamic save metadata
    private int saveSelectScroll = 0; // Scroll offset in pixels for save select
    private double saveSelectScrollAnimated = 0; // Smooth animated scroll
    private boolean deletingSlot = false; // True when delete key is being held
    private int deleteConfirmTimer = 0; // Timer for delete confirmation
    private boolean deleteModeActive = false; // True when accessing save screen from Delete Save menu
    private boolean controllerDeleteActive = false; // True when controller X button initiated delete
    private boolean showAutoSaveIndicator = false; // Show auto-save icon
    private double autoSaveIndicatorTimer = 0; // Timer for auto-save indicator fade
    private static final int AUTO_SAVE_INDICATOR_DURATION = 120; // 2 seconds
    
    // Game mode selection (shown when creating a new save)
    private int pendingSaveSlot = -1; // Slot waiting for mode selection (-1 = none)
    private int selectedGameModeIndex = 1; // 0=EASY, 1=HARD, 2=MASTER (default to HARD)
    
    // Save name input
    private StringBuilder saveNameInput = new StringBuilder("New Game");
    private int saveNameCursorPos = 0; // Cursor position in the name string
    private int saveNameCursorBlink = 0; // Blink timer for cursor
    private int onScreenKbRow = 0; // On-screen keyboard row (0-3)
    private int onScreenKbCol = 0; // On-screen keyboard column (0-9)
    private static final String[] ON_SCREEN_KB_ROWS = {
        "ABCDEFGHIJ",
        "KLMNOPQRST",
        "UVWXYZ0123",
        "456789 \u2190\u23CE"  // space, backspace (â†), confirm (âŽ)
    };
    private static final int MAX_SAVE_NAME_LENGTH = 20;
    
    /** Detect whether the system has GPU-accelerated graphics support. */
    public static void detectGPU() {
        try {
            java.awt.GraphicsConfiguration gc = java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            boolean hwAccelerated = gc.getImageCapabilities().isAccelerated();
            System.out.println("[GPU] ImageCapabilities.isAccelerated() = " + hwAccelerated);
            System.out.println("[GPU] GraphicsDevice: " + gc.getDevice().getIDstring());
            gpuAvailable = hwAccelerated;
            if (!gpuAvailable) {
                // Some drivers report false even when acceleration is available.
                // Check if OpenGL or D3D pipeline can be used as a fallback indicator.
                String os = System.getProperty("os.name", "").toLowerCase();
                gpuAvailable = os.contains("win") || os.contains("mac"); // Most desktop OS have GPU support
                System.out.println("[GPU] Fallback detection (OS=" + os + "): gpuAvailable=" + gpuAvailable);
            }
            System.out.println("[GPU] Final result: gpuAvailable=" + gpuAvailable);
        } catch (Exception e) {
            gpuAvailable = false;
            System.out.println("[GPU] Detection failed: " + e.getMessage());
        }
    }
    
    /**
     * Returns how many GPU-specific settings are shown at the top of the Graphics tab.
     * 0 = no GPU detected, 1 = GPU detected but acceleration off (just master toggle),
     * 3 = GPU detected and acceleration on (toggle + pipeline + buffer mode).
     */
    public static int getGPUSettingsOffset() {
        if (!gpuAvailable) return 0;
        return enableGPUAcceleration ? 3 : 1;
    }
    
    /**
     * Save GPU settings to a lightweight config file so they can be read
     * before AWT initialization on next launch (JVM pipeline flags must be
     * set before any window is created).
     */
    public static void saveGPUConfig() {
        try {
            java.io.File configDir = new java.io.File("config");
            if (!configDir.exists()) configDir.mkdirs();
            java.util.Properties props = new java.util.Properties();
            props.setProperty("enableGPUAcceleration", String.valueOf(enableGPUAcceleration));
            props.setProperty("gpuPipelineType", String.valueOf(gpuPipelineType));
            props.setProperty("bufferStrategyMode", String.valueOf(bufferStrategyMode));
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream("config/gpu.properties")) {
                props.store(fos, "GPU Acceleration Settings — read before AWT init on startup");
            }
            System.out.println("[GPU] Settings saved: enabled=" + enableGPUAcceleration 
                + ", pipeline=" + (gpuPipelineType == 0 ? "Auto" : gpuPipelineType == 1 ? "OpenGL" : "Direct3D")
                + ", bufferMode=" + (bufferStrategyMode == 0 ? "Double" : "Triple")
                + " — RESTART REQUIRED for pipeline changes to take effect");
        } catch (Exception e) {
            System.err.println("[GPU] Failed to save config: " + e.getMessage());
        }
        
        // Sync GPU settings to global save and propagate to all save slots
        if (instance != null && instance.globalSaveData != null) {
            instance.globalSaveData.enableGPUAcceleration = enableGPUAcceleration;
            instance.globalSaveData.gpuPipelineType = gpuPipelineType;
            instance.globalSaveData.bufferStrategyMode = bufferStrategyMode;
            instance.saveManager.saveGlobal(instance.globalSaveData);
            instance.saveManager.propagateGPUToAllSaves(enableGPUAcceleration, gpuPipelineType, bufferStrategyMode);
        }
    }
    
    public Game() {
        instance = this;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        setDoubleBuffered(true); // Enable double buffering for smoother rendering
        
        // Initialize Robot for controller cursor warping
        try { robot = new java.awt.Robot(); } catch (AWTException e) { robot = null; }
        
        // Create blank cursor for hiding during gameplay
        blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 
            new Point(0, 0), "blank");
        defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
        setCursor(defaultCursor);
        
        // Initialize systems
        keys = new boolean[256];
        bullets = new ArrayList<>(MAX_BULLETS);
        bulletPool = new ArrayList<>(BULLET_POOL_PREWARM);
        particles = new ArrayList<>(MAX_PARTICLES);
        particlePool = new ArrayList<>(PARTICLE_POOL_PREWARM);
        introParticles = new ArrayList<>(64);
        beamAttacks = new ArrayList<>(8);
        flares = new ArrayList<>(16);
        flareCooldownTimer = 300; // 5s grace period at start
        bulletGrid = new HashMap<>(256);
        gameData = new GameData();
        shopManager = new ShopManager(gameData);
        achievementManager = new AchievementManager();
        passiveUpgradeManager = new PassiveUpgradeManager();
        shopManager.setPassiveUpgradeManager(passiveUpgradeManager); // Connect passive upgrades to shop
        comboSystem = new ComboSystem();
        saveManager = new SaveManager(); // Initialize save manager
        leaderboardManager = new LeaderboardManager(); // Initialize leaderboard system
        
        // Load or create global save data
        globalSaveData = saveManager.loadGlobal();
        if (globalSaveData == null) {
            globalSaveData = saveManager.createInitialGlobalSave();
        } else {
            // Apply global GPU settings (override whatever gpu.properties loaded)
            enableGPUAcceleration = globalSaveData.enableGPUAcceleration;
            gpuPipelineType = globalSaveData.gpuPipelineType;
            bufferStrategyMode = globalSaveData.bufferStrategyMode;
        }
        
        // Load leaderboard records from global save
        leaderboardManager.loadFromGlobal(globalSaveData);
        
        pendingAchievements = new ArrayList<>(8);
        damageNumbers = new ArrayList<>(64);
        soundManager = SoundManager.getInstance();

        // Pre-warm object pools to prevent allocation spikes during gameplay
        prewarmPools();
        
        // Initialize keybind and controller systems
        keyBindManager = new KeyBindManager();
        controllerManager = new ControllerManager(keyBindManager);
        
        // Initial state - start with loading screen
        gameState = GameState.LOADING;
        selectedStatItem = 0;
        selectedMenuItem = 0;
        settingsScroll = 0;
        selectedSettingsItem = -1; // Start with tabs selected
        selectedSettingsCategory = 0;
        gradientTime = 0;
        itemUnlockAnimation = false;
        itemUnlockDismissing = false;
        itemUnlockTimer = 0;
        itemUnlockDismissTimer = 0;
        showEquipPrompt = false;
        newItemIndex = -1;
        selectedEquipButton = 0;
        
        // Create equip item buttons
        equipButtons = new UIButton[2];
        int buttonWidth = 200;
        int buttonHeight = 60;
        int buttonY = HEIGHT / 2 + 120;
        int spacing = 30;
        int totalWidth = (buttonWidth * 2) + spacing;
        int startX = (WIDTH - totalWidth) / 2;
        
        equipButtons[0] = new UIButton("Yes", startX, buttonY, buttonWidth, buttonHeight,
            ColorPalette.BORDER_STEEL, ColorPalette.SUCCESS_GREEN); // Green for yes
        equipButtons[1] = new UIButton("No", startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight,
            ColorPalette.BORDER_STEEL, ColorPalette.ACCENT_RED); // Red for no
        contractUnlockAnimation = false;
        contractUnlockDismissing = false;
        contractUnlockTimer = 0;
        contractUnlockDismissTimer = 0;
        passiveUnlockAnimation = false;
        passiveUnlockDismissing = false;
        passiveUnlockTimer = 0;
        passiveUnlockDismissTimer = 0;
        unlockedPassiveName = "";
        unlockedPassiveDescription = "";
        pendingPassiveUnlocks = new java.util.LinkedList<>();
        debugItemPopupQueue = new java.util.LinkedList<>();
        previousState = GameState.MENU;
        stateTransitionProgress = 1.0f;
        unlockedItemName = "";
        unlockedItemDescription = "";
        
        // Initialize scroll positions (ensure level select starts at level 1)
        levelSelectScroll = 1;
        levelSelectScrollAnimated = 1;
        planeTakeoffAnimation = false;
        planeTakeoffTimer = 0;
        shopScroll = 0;
        shopScrollAnimated = 0;
        statsScroll = 0;
        statsScrollAnimated = 0;
        achievementsScroll = 0;
        achievementsScrollAnimated = 0;
        
        screenShakeX = 0;
        screenShakeY = 0;
        trailSpawnTimer = 0;
        screenShakeIntensity = 0;
        dodgeCombo = 0;
        comboTimer = 0;
        bossVulnerable = false;
        vulnerabilityTimer = 0;
        isPaused = false;
        selectedPauseItem = 0;
        unpauseCountdownActive = false;
        unpauseCountdownTimer = 0;
        bossIntroActive = false;
        bossIntroTimer = 0;
        achievementNotificationTimer = 0;
        achievementFlashTimer = 0;
        bossIntroFlashTimer = 0;
        countdownFlashTimer = 0;
        bossHitFlashTimer = 0;
        lastCountdownSecond = -1;
        tookDamageThisBoss = false;
        consecutivePerfectBosses = 0;
        totalGrazesThisRun = 0;
        missilesUsedThisRun = 0;
        deathSequenceActive = false;
        playerHidden = false;
        respawnBlinkTimer = 0;
        
        // Sync sound settings with soundManager
        soundManager.setMasterVolume(gameData.getMasterVolume());
        soundManager.setSfxVolume(gameData.getSfxVolume());
        soundManager.setUiVolume(gameData.getUiVolume());
        soundManager.setMusicVolume(gameData.getMusicVolume());
        soundManager.setSoundEnabled(gameData.isSoundEnabled());
        soundManager.setSpatialAudioEnabled(gameData.isSpatialAudioEnabled());
        
        // Initialize game feel effects
        hitFreezeFrames = 0;
        bossHitCameraHoldTimer = 0;
        slowMotionFactor = 1.0;
        slowMotionTimer = 0;
        comboPulseScale = 1.0;
        cameraBreathOffset = 0;
        cameraBreathTime = 0;
        displayedScore = 0;
        displayedMoney = 0;
        for (int i = 0; i < afterimageX.length; i++) {
            afterimageX[i] = 0;
            afterimageY[i] = 0;
            afterimageAlpha[i] = 0;
        }
        
        // Setup input
        setFocusTraversalKeysEnabled(false); // Prevent TAB from being consumed
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Switch to keyboard mode on any key press
                if (keyBindManager != null) keyBindManager.onKeyboardInput();
                if (e.getKeyCode() < keys.length) {
                    keys[e.getKeyCode()] = true;
                }
                handleKeyPress(e);
            }
            
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() < keys.length) {
                    keys[e.getKeyCode()] = false;
                }
                // Reset item key tracking on release (USE_ITEM action key)
                if (keyBindManager != null && keyBindManager.isAction(KeyBindManager.Action.USE_ITEM, e.getKeyCode())) {
                    eKeyPressed = false;
                } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    eKeyPressed = false;
                }
                // Reset delete hold when DELETE key is released
                if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) && gameState == GameState.SAVE_SELECT) {
                    deletingSlot = false;
                    deleteConfirmTimer = 0;
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {
                if (gameState == GameState.NAME_INPUT) {
                    char c = e.getKeyChar();
                    // Allow printable characters (letters, digits, spaces, basic punctuation)
                    if (c >= 32 && c < 127 && c != '\n' && c != '\r') {
                        if (saveNameInput.length() < MAX_SAVE_NAME_LENGTH) {
                            saveNameInput.insert(saveNameCursorPos, c);
                            saveNameCursorPos++;
                            saveNameCursorBlink = 0;
                        }
                    }
                }
            }
        });
        
        // Add mouse listeners for UI navigation
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // Transform mouse coordinates from screen space to game space (stretch-fill)
                double scaleX = (double) getWidth() / WIDTH;
                double scaleY = (double) getHeight() / HEIGHT;
                
                int newMouseX = (int) (e.getX() / scaleX);
                int newMouseY = (int) (e.getY() / scaleY);
                
                // Only update UI selection if mouse moved beyond threshold
                // This prevents mouse from overriding keyboard navigation on tiny/accidental moves
                if (lastMouseX < 0 || Math.abs(newMouseX - lastMouseX) + Math.abs(newMouseY - lastMouseY) >= MOUSE_MOVE_THRESHOLD) {
                    lastMouseX = newMouseX;
                    lastMouseY = newMouseY;
                    mouseX = newMouseX;
                    mouseY = newMouseY;
                    mouseActive = true;
                    handleMouseMove();
                }
            }
            
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                // Transform mouse coordinates from screen space to game space (stretch-fill)
                double scaleX = (double) getWidth() / WIDTH;
                double scaleY = (double) getHeight() / HEIGHT;
                
                mouseX = (int) (e.getX() / scaleX);
                mouseY = (int) (e.getY() / scaleY);
                
                // Slider drag in settings
                if (draggingSlider && draggingSliderIndex >= 0 && gameState == GameState.SETTINGS && renderer != null) {
                    float progress = renderer.getSliderTrackClick(draggingSliderIndex, mouseX, mouseY);
                    if (progress < 0) {
                        // Mouse moved off vertically — clamp to track horizontally
                        progress = renderer.getSliderTrackProgress(draggingSliderIndex, mouseX);
                    }
                    if (progress >= 0) {
                        setSliderValue(draggingSliderIndex, progress);
                    }
                    return;
                }
                
                // Delegate to HUD layout editor when on HUD tab
                if (gameState == GameState.SETTINGS && selectedSettingsCategory == 5 && renderer != null) {
                    renderer.hudLayoutEditor.handleMouseDragged(mouseX, mouseY, hudLayout);
                }
            }
        });
        
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                // Transform mouse coordinates from screen space to game space (stretch-fill)
                double scaleX = (double) getWidth() / WIDTH;
                double scaleY = (double) getHeight() / HEIGHT;
                mouseX = (int) (e.getX() / scaleX);
                mouseY = (int) (e.getY() / scaleY);

                // Tutorial popup dismiss — any mouse click dismisses the popup
                if (tutorialMode && tutorialPopupActive) {
                    if (tutorialPopupInputDelay > 0) return; // 2-second buffer
                    tutorialPopupActive = false;
                    renderer.tutorialPopupActive = false;
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 2;

                    if (gameState == GameState.PLAYING) {
                        // Reset per-step tracking for the next step
                        tutorialPlayerMoveDistance = 0;
                        tutorialGrazeCount = 0;
                        tutorialPlayerDied = false;
                        tutorialItemUsed = false;
                        tutorialShieldBlockCount = 0;
                        tutorialShopPurchased = false;
                        tutorialShopVisited = false;
                        if (player != null) {
                            tutorialPrevPlayerX = player.getX();
                            tutorialPrevPlayerY = player.getY();
                        }

                        // For popup-only steps (Welcome, Complete), advance immediately
                        if (tutorialStep == 0 || tutorialStep == 7) {
                            advanceTutorialStep();
                        }

                        // Resume normal speed
                        tutorialSlowdownPhase = 3; // SLOWING_OUT
                        tutorialSlowdownTimer = 30;
                    }
                    return;
                }

                // Tutorial complete screen — handle LEAVE / REPLAY button clicks
                if (tutorialMode && tutorialCompleteScreen && e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    int panelW = config.UIScale.px(520);
                    int panelH = config.UIScale.px(320);
                    int panelX = (WIDTH - panelW) / 2;
                    int panelY = (HEIGHT - panelH) / 2;
                    int btnW = config.UIScale.px(140);
                    int btnH = config.UIScale.px(45);
                    int btnY = panelY + config.UIScale.px(220);
                    int btnGap = config.UIScale.px(30);
                    int leaveX = panelX + (panelW / 2) - btnW - btnGap / 2;
                    int againX = panelX + (panelW / 2) + btnGap / 2;

                    if (mouseX >= leaveX && mouseX <= leaveX + btnW &&
                        mouseY >= btnY && mouseY <= btnY + btnH) {
                        // LEAVE
                        completeTutorial();
                        screenShakeIntensity = 5;
                        return;
                    } else if (mouseX >= againX && mouseX <= againX + btnW &&
                               mouseY >= btnY && mouseY <= btnY + btnH) {
                        // REPLAY
                        completeTutorial();
                        startTutorial();
                        screenShakeIntensity = 5;
                        return;
                    }
                    // Clicked outside buttons — just ignore
                    return;
                }

                // Dismiss attack intro on any mouse button
                if (gameState == GameState.ATTACK_INTRO) {
                    dismissAttackIntro();
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                    return;
                }

                // Skip countdown on any mouse click
                if (gameState == GameState.PLAYING && unpauseCountdownActive) {
                    unpauseCountdownActive = false;
                    lastCountdownSecond = -1;
                    soundManager.playSound(SoundManager.Sound.COUNTDOWN_GO);
                    screenShakeIntensity = 2;
                    return;
                }

                // Handle press for responsive feel
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    // Check for slider track click to start drag
                    if (gameState == GameState.SETTINGS && renderer != null) {
                        UIButton[] buttons = renderer.getSettingsButtons();
                        if (buttons != null) {
                            for (int i = 0; i < buttons.length; i++) {
                                if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                                    float progress = renderer.getSliderTrackClick(i, mouseX, mouseY);
                                    if (progress >= 0) {
                                        draggingSlider = true;
                                        draggingSliderIndex = i;
                                        selectedSettingsItem = i;
                                        setSliderValue(i, progress);
                                        return;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    handleMouseClick(e);
                }
            }
            
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                // Stop slider drag
                if (draggingSlider) {
                    draggingSlider = false;
                    draggingSliderIndex = -1;
                }
                // Stop delete hold when mouse is released in save select
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1 && gameState == GameState.SAVE_SELECT) {
                    deletingSlot = false;
                    deleteConfirmTimer = 0;
                }
                // Release drag in HUD layout editor
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1 && gameState == GameState.SETTINGS 
                    && selectedSettingsCategory == 5 && renderer != null) {
                    renderer.hudLayoutEditor.handleMouseReleased(hudLayout);
                }
            }
        });
        
        // Add mouse wheel listener for scrolling in menus
        addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                handleMouseWheel(e);
            }
        });
        
        // Initialize fonts early so the loading screen can use them
        FontPalette.init();
        
        // Start loading assets in background thread
        startAssetLoading();
    }
    
    private void handleKeyPress(KeyEvent e) {
        int key = e.getKeyCode();
        
        // Keybind rebinding intercept â€” capture the pressed key when waiting
        if (waitingForKeyBind && gameState == GameState.SETTINGS && selectedSettingsCategory == 4) {
            if (key == KeyEvent.VK_ESCAPE) {
                // Cancel rebinding
                waitingForKeyBind = false;
                rebindingActionIndex = -1;
                rebindingController = false;
                soundManager.playSound(SoundManager.Sound.UI_CANCEL);
            } else if (!rebindingController && !KeyBindManager.isReservedKey(key)) {
                // Bind the key to the action (keyboard section only)
                int actionIndex = rebindingActionIndex - 1;
                KeyBindManager.Action[] actions = KeyBindManager.Action.values();
                if (actionIndex >= 0 && actionIndex < actions.length) {
                    keyBindManager.setKey(actions[actionIndex], key);
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                }
                waitingForKeyBind = false;
                rebindingActionIndex = -1;
                rebindingController = false;
            }
            return; // Consume the key event
        }
        
        // Global F11 fullscreen toggle - works in all states
        if (key == KeyEvent.VK_F11) {
            toggleFullscreen();
            screenShakeIntensity = 3;
            return;
        }
        
        switch (gameState) {
            case SAVE_SELECT:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    selectedSaveSlot = Math.max(0, selectedSaveSlot - 1);
                    ensureSaveSlotVisible();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                    deletingSlot = false;
                    deleteConfirmTimer = 0;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    int maxIndex = saveMetadataCache.size(); // existing saves + "New Save" button
                    selectedSaveSlot = Math.min(maxIndex, selectedSaveSlot + 1);
                    ensureSaveSlotVisible();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                    deletingSlot = false;
                    deleteConfirmTimer = 0;
                }
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    if (selectedSaveSlot < saveMetadataCache.size()) {
                        // Load existing save
                        SaveManager.SaveMetadata meta = saveMetadataCache.get(selectedSaveSlot);
                        int slot = meta.slotNumber;
                        loadSaveSlot(slot, meta.saveName);
                    } else {
                        // "New Save" button â€” go to mode selection
                        pendingSaveSlot = saveManager.getNextAvailableSlot();
                        selectedGameModeIndex = 1; // Default to HARD
                        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                        screenShakeIntensity = 5;
                        transitionToState(GameState.MODE_SELECT);
                    }
                }
                else if (key == KeyEvent.VK_DELETE || key == KeyEvent.VK_BACK_SPACE) {
                    if (selectedSaveSlot < saveMetadataCache.size()) {
                        SaveManager.SaveMetadata meta = saveMetadataCache.get(selectedSaveSlot);
                        int slot = meta.slotNumber;
                        if (saveManager.saveExists(slot)) {
                            if (!deletingSlot) {
                                deletingSlot = true;
                                deleteConfirmTimer = 0;
                                soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                            } else if (deleteConfirmTimer >= 60) {
                                saveManager.delete(slot);
                                refreshSaveMetadata();
                                if (selectedSaveSlot > saveMetadataCache.size()) {
                                    selectedSaveSlot = saveMetadataCache.size();
                                }
                                deletingSlot = false;
                                deleteConfirmTimer = 0;
                                soundManager.playSound(SoundManager.Sound.BOSS_HIT);
                                screenShakeIntensity = 5;
                            }
                        }
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    if (escapeTimer > 0) {
                        System.exit(0);
                    } else {
                        escapeTimer = ESCAPE_TIMEOUT;
                        screenShakeIntensity = 3;
                    }
                }
                break;
                
            case MODE_SELECT:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    selectedGameModeIndex = Math.max(0, selectedGameModeIndex - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    selectedGameModeIndex = Math.min(2, selectedGameModeIndex + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    // Go to name input screen
                    saveNameInput = new StringBuilder("Save " + pendingSaveSlot);
                    saveNameCursorPos = saveNameInput.length();
                    saveNameCursorBlink = 0;
                    onScreenKbRow = 0;
                    onScreenKbCol = 0;
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                    transitionToState(GameState.NAME_INPUT);
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    // Go back to save select
                    pendingSaveSlot = -1;
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                    screenShakeIntensity = 3;
                    transitionToState(GameState.SAVE_SELECT);
                }
                break;
                
            case NAME_INPUT:
                if (key == KeyEvent.VK_ENTER) {
                    // Confirm the name and create the save
                    confirmSaveName();
                } else if (key == KeyEvent.VK_ESCAPE) {
                    // Go back to mode select
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                    screenShakeIntensity = 3;
                    transitionToState(GameState.MODE_SELECT);
                } else if (key == KeyEvent.VK_BACK_SPACE) {
                    // Delete character before cursor
                    if (saveNameCursorPos > 0) {
                        saveNameInput.deleteCharAt(saveNameCursorPos - 1);
                        saveNameCursorPos--;
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_DELETE) {
                    // Delete character at cursor
                    if (saveNameCursorPos < saveNameInput.length()) {
                        saveNameInput.deleteCharAt(saveNameCursorPos);
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_LEFT) {
                    if (saveNameCursorPos > 0) {
                        saveNameCursorPos--;
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_RIGHT) {
                    if (saveNameCursorPos < saveNameInput.length()) {
                        saveNameCursorPos++;
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_UP) {
                    // Navigate on-screen keyboard
                    onScreenKbRow = Math.max(0, onScreenKbRow - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                } else if (key == KeyEvent.VK_DOWN) {
                    onScreenKbRow = Math.min(ON_SCREEN_KB_ROWS.length - 1, onScreenKbRow + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                }
                break;
                
            case MENU:
                // Tutorial prompt intercepts all input
                if (showTutorialPrompt) {
                    if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                        tutorialPromptSelection = 1 - tutorialPromptSelection;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                        showTutorialPrompt = false;
                        if (tutorialPromptSelection == 0) {
                            // Yes — start tutorial
                            startTutorial();
                        } else {
                            // No — dismiss
                            soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                        }
                        screenShakeIntensity = 3;
                    } else if (key == KeyEvent.VK_ESCAPE) {
                        showTutorialPrompt = false;
                        soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                        screenShakeIntensity = 2;
                    }
                    break;
                }
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    selectedMenuItem = Math.max(0, selectedMenuItem - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    int maxItem = DEMO_MODE ? 6 : 7; // Hide Save Files in demo
                    selectedMenuItem = Math.min(maxItem, selectedMenuItem + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                    screenShakeIntensity = 5;
                    // New order: Select Level, Shop, Stats, Achievements, Leaderboard, Game Info, Settings, Save Files
                    switch (selectedMenuItem) {
                        case 0: transitionToState(GameState.LEVEL_SELECT); break;
                        case 1: shopEnteredFrom = GameState.MENU; transitionToState(GameState.SHOP); break;
                        case 2: transitionToState(GameState.STATS); break;
                        case 3: transitionToState(GameState.ACHIEVEMENTS); break;
                        case 4: transitionToState(GameState.LEADERBOARD_VIEW); break;
                        case 5: transitionToState(GameState.INFO); break;
                        case 6: settingsEnteredFrom = GameState.MENU; snapshotSettings(); transitionToState(GameState.SETTINGS); break;
                        case 7: if (!DEMO_MODE) transitionToState(GameState.SAVE_SELECT); break; // New: Save Files
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    // Double-tap escape to quit
                    if (escapeTimer > 0) {
                        // Second press - quit
                        System.exit(0);
                    } else {
                        // First press - start timer
                        escapeTimer = ESCAPE_TIMEOUT;
                        screenShakeIntensity = 3;
                    }
                }
                // Legacy hotkeys still work
                else if (key == KeyEvent.VK_I) { transitionToState(GameState.INFO); screenShakeIntensity = 5; }
                else if (key == KeyEvent.VK_P) { shopEnteredFrom = GameState.MENU; transitionToState(GameState.SHOP); screenShakeIntensity = 5; }
                else if (key == KeyEvent.VK_O) { transitionToState(GameState.SETTINGS); screenShakeIntensity = 5; }
                // Debug menu shortcut
                else if (key == KeyEvent.VK_F3) { transitionToState(GameState.DEBUG); screenShakeIntensity = 5; }
                // Debug attack showcase mode (F10)
                else if (key == KeyEvent.VK_F10) { startDebugShowcase(); screenShakeIntensity = 5; }
                // Demo boss intro cinematic (U key)
                else if (key == KeyEvent.VK_U) { startDemoIntro(); }
                break;
                
            case STATS:
                // Only show unlocked upgrades (excluding Extra Missiles which is always last)
                java.util.List<PassiveUpgrade> visibleUpgrades = getVisibleShopUpgrades();
                // Total items: Active Item(0) + visible upgrades(1..N) + Extra Missiles(N+1)
                int maxStatItems = 1 + visibleUpgrades.size() + 1;
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { 
                    selectedStatItem = Math.max(0, selectedStatItem - 1);
                    updateStatsScroll();
                    screenShakeIntensity = 1; 
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { 
                    selectedStatItem = Math.min(maxStatItems - 1, selectedStatItem + 1);
                    updateStatsScroll();
                    screenShakeIntensity = 1; 
                }
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                    if (selectedStatItem == 0) {
                        if (hasSavedGame) {
                            soundManager.playSound(SoundManager.Sound.UI_ERROR);
                            screenShakeIntensity = 3;
                        } else {
                            int idx = renderer.getStatsActiveItemDisplayIndex();
                            if (idx > 0) {
                                idx--;
                                renderer.setStatsActiveItemDisplayIndex(idx);
                                autoEquipStatsItem(idx);
                            }
                            screenShakeIntensity = 2;
                        }
                    } else if (selectedStatItem >= 1 && selectedStatItem <= visibleUpgrades.size()) {
                        if (hasSavedGame) {
                            soundManager.playSound(SoundManager.Sound.UI_ERROR);
                            screenShakeIntensity = 3;
                        } else {
                            PassiveUpgrade upgrade = visibleUpgrades.get(selectedStatItem - 1);
                            if (upgrade.getActiveLevel() > 0) {
                                upgrade.setActiveLevel(upgrade.getActiveLevel() - 1);
                                screenShakeIntensity = 2;
                            }
                        }
                    }
                    // Extra Missiles (last item) is read-only
                }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    if (selectedStatItem == 0) {
                        if (hasSavedGame) {
                            soundManager.playSound(SoundManager.Sound.UI_ERROR);
                            screenShakeIntensity = 3;
                        } else {
                            int idx = renderer.getStatsActiveItemDisplayIndex();
                            if (idx < 8) {
                                idx++;
                                renderer.setStatsActiveItemDisplayIndex(idx);
                                autoEquipStatsItem(idx);
                            }
                            screenShakeIntensity = 2;
                        }
                    } else if (selectedStatItem >= 1 && selectedStatItem <= visibleUpgrades.size()) {
                        if (hasSavedGame) {
                            soundManager.playSound(SoundManager.Sound.UI_ERROR);
                            screenShakeIntensity = 3;
                        } else {
                            PassiveUpgrade upgrade = visibleUpgrades.get(selectedStatItem - 1);
                            if (upgrade.getActiveLevel() < upgrade.getCurrentLevel()) {
                                upgrade.setActiveLevel(upgrade.getActiveLevel() + 1);
                                screenShakeIntensity = 2;
                            }
                        }
                    }
                    // Extra Missiles (last item) is read-only
                }
                else if (key == KeyEvent.VK_ESCAPE) { transitionToState(GameState.MENU); screenShakeIntensity = 3; }
                break;
                
            case SETTINGS:
                // Warning dialog navigation intercepts all keys except ESC
                if (showSettingsWarning) {
                    if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                        settingsWarningSelection = Math.max(0, settingsWarningSelection - 1);
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                        settingsWarningSelection = Math.min(2, settingsWarningSelection + 1);
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                        // Confirm current warning selection
                        confirmWarningSelection();
                    } else if (key == KeyEvent.VK_ESCAPE) {
                        // ESC in warning dialog = Cancel
                        soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                        showSettingsWarning = false;
                        screenShakeIntensity = 3;
                    }
                    break;
                }
                mouseActive = false; // Keyboard takes priority over mouse selection
                clampSettingsItem();
                // HUD tab: suppress item navigation (editor handles its own interaction)
                boolean hudTabActive = (selectedSettingsCategory == 5);
                if ((key == KeyEvent.VK_UP || key == KeyEvent.VK_W) && !hudTabActive) { 
                    if (selectedSettingsItem == 0) {
                        // Move from first item to tabs
                        selectedSettingsItem = -1;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (selectedSettingsItem > 0) {
                        selectedSettingsItem = Math.max(0, selectedSettingsItem - 1); 
                        skipCollapsedControlsItems(-1);
                        ensureSettingsItemVisible();
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                }
                else if ((key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) && !hudTabActive) {
                    if (selectedSettingsItem == -1) {
                        // Move from tabs to first item
                        selectedSettingsItem = 0;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else {
                        int maxItems = getMaxSettingsItems();
                        selectedSettingsItem = Math.min(maxItems, selectedSettingsItem + 1);
                        skipCollapsedControlsItems(1);
                        ensureSettingsItemVisible();
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                }
                else if (key == KeyEvent.VK_LEFT) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to previous tab
                        selectedSettingsCategory = (selectedSettingsCategory + 5) % 6;
                        settingsScroll = 0;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, -1)) {
                            markSettingsDirty();
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 2;
                        }
                    }
                }
                else if (key == KeyEvent.VK_RIGHT) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to next tab
                        selectedSettingsCategory = (selectedSettingsCategory + 1) % 6;
                        settingsScroll = 0;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, 1)) {
                            markSettingsDirty();
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 2;
                        }
                    }
                }
                else if (key == KeyEvent.VK_A) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to previous tab
                        selectedSettingsCategory = (selectedSettingsCategory + 5) % 6;
                        settingsScroll = 0;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, -1)) {
                            markSettingsDirty();
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 2;
                        }
                    }
                }
                else if (key == KeyEvent.VK_D) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to next tab
                        selectedSettingsCategory = (selectedSettingsCategory + 1) % 6;
                        settingsScroll = 0;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, 1)) {
                            markSettingsDirty();
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 2;
                        }
                    }
                }
                else if (key == KeyEvent.VK_SPACE && !hudTabActive) {
                    if (selectedSettingsItem >= 0) {
                        toggleSetting(selectedSettingsItem);
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        screenShakeIntensity = 3;
                    }
                }
                else if (key == KeyEvent.VK_TAB) {
                    // Release any controller HUD drag before switching tabs
                    if (controllerHudMouseDown && renderer != null) {
                        renderer.hudLayoutEditor.handleMouseReleased(hudLayout);
                        controllerHudMouseDown = false;
                    }
                    // Switch category and move to tabs
                    selectedSettingsCategory = (selectedSettingsCategory + 1) % 6;
                    settingsScroll = 0;
                    clampSettingsItem();
                    selectedSettingsItem = -1;
                    soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_R) {
                    // Reset current tab to defaults
                    resetCurrentTabToDefaults();
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_ENTER) {
                    // Apply settings
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    applySettings();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_ESCAPE) { 
                    if (settingsDirty) {
                        // Show unsaved changes warning
                        soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                        showSettingsWarning = true;
                        settingsWarningSelection = 0;
                    } else {
                        // No changes - just exit
                        soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                        if (settingsEnteredFrom == GameState.PLAYING) {
                            isPaused = true;
                            gameState = GameState.PLAYING;
                        } else {
                            transitionToState(GameState.MENU);
                        }

                    }
                    screenShakeIntensity = 3; 
                }
                break;
                
            case INFO:
                if (key == KeyEvent.VK_ESCAPE) {
                    transitionToState(GameState.MENU);
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                } else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    renderer.helpSelectedButton = (renderer.helpSelectedButton + 1) % 2;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    renderer.helpSelectedButton = (renderer.helpSelectedButton + 1) % 2;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                    if (renderer.helpSelectedButton == 0) {
                        // Showcase
                        showcaseEnteredFrom = GameState.INFO;
                        startDebugShowcase();
                        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                        screenShakeIntensity = 5;
                    } else {
                        // Start Tutorial
                        startTutorial();
                        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                        screenShakeIntensity = 5;
                    }
                }
                break;
                
            case ACHIEVEMENTS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    achievementsScroll = Math.max(0, achievementsScroll - 100);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    // Calculate max scroll based on achievement count
                    int totalAchievements = achievementManager.getAllAchievements().size();
                    int rows = (int)Math.ceil(totalAchievements / 3.0);
                    int maxScroll = Math.max(0, (rows * 115) - 600); // 115 per row, 600 visible area
                    achievementsScroll = Math.min(maxScroll, achievementsScroll + 100);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                else if (key == KeyEvent.VK_ESCAPE) transitionToState(GameState.MENU);
                break;
                
            case LEADERBOARD_VIEW:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    leaderboardViewScroll = Math.max(0, leaderboardViewScroll - 100);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    int maxScroll = Math.max(0, (28 * 50) - 500); // 50px per row, 500 visible area
                    leaderboardViewScroll = Math.min(maxScroll, leaderboardViewScroll + 100);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                    selectedLeaderboardDifficulty = Math.max(0, selectedLeaderboardDifficulty - 1);
                    leaderboardViewScroll = 0;
                    leaderboardViewScrollAnimated = 0;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    selectedLeaderboardDifficulty = Math.min(GameMode.values().length - 1, selectedLeaderboardDifficulty + 1);
                    leaderboardViewScroll = 0;
                    leaderboardViewScrollAnimated = 0;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_ESCAPE) transitionToState(GameState.MENU);
                break;
                
            case ATTACK_INTRO:
                // Press any key to continue from attack intro
                dismissAttackIntro();
                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                screenShakeIntensity = 3;
                break;
            
            case ATTACK_SHOWCASE:
                // Attack/Item showcase selection screen - carousel style
                // showcaseHoveredButton: 0 = Attacks tab, 1 = Items tab, 2 = Left arrow, 3 = Right arrow, -1 = none
                if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) {
                    // Move up between rows: Arrows -> Tabs
                    if (showcaseHoveredButton == 2 || showcaseHoveredButton == 3) {
                        showcaseHoveredButton = showcaseTab; // Arrows -> Current tab
                    } else if (showcaseHoveredButton == -1) {
                        showcaseHoveredButton = showcaseTab; // Enter at tabs
                    }
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) {
                    // Move down between rows: Tabs -> Arrows
                    if (showcaseHoveredButton == 0 || showcaseHoveredButton == 1) {
                        showcaseHoveredButton = 2; // Tabs -> Left arrow
                    } else if (showcaseHoveredButton == -1) {
                        showcaseHoveredButton = 2; // Enter at arrows
                    }
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) {
                    // Move left within row OR scroll carousel immediately
                    if (showcaseHoveredButton == 1) {
                        // On Items tab -> switch to Attacks tab immediately
                        showcaseHoveredButton = 0;
                        showcaseTab = 0;
                        debugShowcaseIndex = 0;
                        showcaseCarouselOffset = 0;
                        updateShowcaseInfo();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                    } else if (showcaseHoveredButton == 0) {
                        // Already on Attacks tab, do nothing
                    } else {
                        // Anywhere else (arrows, no selection, center) - scroll to previous item
                        if (debugShowcaseIndex > 0) {
                            debugShowcaseIndex--;
                            showcaseCarouselOffset = -1.0; // Start animation from left
                            updateShowcaseInfo();
                            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        }
                    }
                    screenShakeIntensity = 1;
                } else if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) {
                    // Move right within row OR scroll carousel immediately
                    int maxIndex = (showcaseTab == 0) ? ATTACK_INTROS.length : ITEM_SHOWCASE.length;
                    if (showcaseHoveredButton == 0) {
                        // On Attacks tab -> switch to Items tab immediately
                        showcaseHoveredButton = 1;
                        showcaseTab = 1;
                        debugShowcaseIndex = 0;
                        showcaseCarouselOffset = 0;
                        updateShowcaseInfo();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        maxIndex = ITEM_SHOWCASE.length; // Update maxIndex for new tab
                    } else if (showcaseHoveredButton == 1) {
                        // Already on Items tab, do nothing
                    } else {
                        // Anywhere else (arrows, no selection, center) - scroll to next item
                        if (debugShowcaseIndex < maxIndex - 1) {
                            debugShowcaseIndex++;
                            showcaseCarouselOffset = 1.0; // Start animation from right
                            updateShowcaseInfo();
                            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        }
                    }
                    screenShakeIntensity = 1;
                } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    // Space/Enter always starts the test with the current card
                    startShowcaseTest();
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                } else if (key == KeyEvent.VK_TAB) {
                    // Quick tab switch
                    showcaseTab = (showcaseTab + 1) % 2;
                    debugShowcaseIndex = 0;
                    showcaseCarouselOffset = 0; // Reset animation
                    updateShowcaseInfo();
                    // Update hover to match new tab if on tabs row
                    if (showcaseHoveredButton == 0 || showcaseHoveredButton == 1) {
                        showcaseHoveredButton = showcaseTab;
                    }
                    soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                    screenShakeIntensity = 2;
                } else if (key == KeyEvent.VK_ESCAPE) {
                    // Exit showcase - restore the real game level and equipped item
                    gameData.setCurrentLevel(savedRealLevel);
                    gameData.restoreEquippedItem(savedEquippedItem); // Restore original item
                    debugShowcaseMode = false;
                    debugShowcaseInGameplay = false;
                    transitionToState(showcaseEnteredFrom != null ? showcaseEnteredFrom : GameState.MENU);
                    showcaseEnteredFrom = GameState.MENU; // Reset for next time
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    System.out.println("DEBUG SHOWCASE: Exited - restored level to " + savedRealLevel);
                }
                break;
                
            case LEVEL_CONFIRM:
                // Confirmation dialog for level start
                if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    selectedConfirmItem = 1 - selectedConfirmItem; // Toggle between 0 and 1
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if ((key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) && !planeTakeoffAnimation) {
                    confirmLevelStart();
                    screenShakeIntensity = 3;
                } else if (key == KeyEvent.VK_ESCAPE) {
                    // Escape goes back to level select
                    transitionToState(GameState.LEVEL_SELECT);
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 2;
                }
                break;
                
            case LEVEL_SELECT:
                // Path-style navigation: Left/Right to move along path, can view all levels
                if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) { 
                    navigateLevelMap(-1); 
                    screenShakeIntensity = 2; 
                }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) { 
                    navigateLevelMap(1); 
                    screenShakeIntensity = 2; 
                }
                else if (key == KeyEvent.VK_SPACE) { 
                    // Check if there's a saved game to resume
                    int selectedLevel = gameData.getSelectedLevelView();
                    System.out.println("DEBUG RESUME CHECK: hasSavedGame=" + hasSavedGame + ", savedLevel=" + savedLevel + ", selectedLevel=" + selectedLevel);
                    System.out.println("DEBUG RESUME CHECK: savedPlayer=" + (savedPlayer != null) + ", savedBoss=" + (savedBoss != null));
                    if (hasSavedGame && selectedLevel == savedLevel) {
                        // Show confirmation dialog for resume
                        selectedLevelToStart = savedLevel;
                        selectedConfirmItem = 0;
                        isConfirmingResume = true;
                        transitionToState(GameState.LEVEL_CONFIRM);
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    } else {
                        tryStartLevel();
                    }
                    screenShakeIntensity = 5; 
                }
                else if (key == KeyEvent.VK_ESCAPE) { 
                    transitionToState(GameState.MENU); 
                    screenShakeIntensity = 3; 
                }
                break;
            
            case RISK_CONTRACT:
                if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                    selectedRiskContract = Math.max(0, selectedRiskContract - 1);
                    screenShakeIntensity = 2;
                } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    selectedRiskContract = Math.min(RISK_CONTRACT_NAMES.length - 1, selectedRiskContract + 1);
                    screenShakeIntensity = 2;
                } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    confirmRiskContract();
                    screenShakeIntensity = 5;
                } else if (key == KeyEvent.VK_ESCAPE) {
                    transitionToState(GameState.LEVEL_SELECT);
                    screenShakeIntensity = 3;
                }
                break;
                
            case PLAYING:
                // Tutorial completion screen input
                if (tutorialMode && tutorialCompleteScreen) {
                    if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                        tutorialCompleteSelection = 1 - tutorialCompleteSelection;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
                        if (tutorialCompleteSelection == 0) {
                            completeTutorial();
                        } else {
                            // Play again — restart tutorial
                            completeTutorial();
                            startTutorial();
                        }
                        screenShakeIntensity = 5;
                    }
                    break;
                }
                // Tutorial popup dismiss — intercept all input during popup
                if (tutorialMode && tutorialPopupActive) {
                    if (tutorialPopupInputDelay > 0) break; // 2-second buffer before allowing dismiss
                    if (!KeyBindManager.isReservedKey(key)) {
                        tutorialPopupActive = false;
                        renderer.tutorialPopupActive = false;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        screenShakeIntensity = 2;
                        
                        // Reset per-step tracking for the next step
                        tutorialPlayerMoveDistance = 0;
                        tutorialGrazeCount = 0;
                        tutorialPlayerDied = false;
                        tutorialItemUsed = false;
                        tutorialShieldBlockCount = 0;
                        tutorialShopPurchased = false;
                        tutorialShopVisited = false;
                        if (player != null) {
                            tutorialPrevPlayerX = player.getX();
                            tutorialPrevPlayerY = player.getY();
                        }
                        
                        // For popup-only steps (Welcome, Complete), advance immediately
                        if (tutorialStep == 0 || tutorialStep == 7) {
                            advanceTutorialStep();
                        }
                        
                        // Resume normal speed
                        tutorialSlowdownPhase = 3; // SLOWING_OUT
                        tutorialSlowdownTimer = 30;
                    }
                    break;
                }
                if (unpauseCountdownActive) {
                    // Any key skips the countdown
                    unpauseCountdownActive = false;
                    lastCountdownSecond = -1;
                    soundManager.playSound(SoundManager.Sound.COUNTDOWN_GO);
                    screenShakeIntensity = 2;
                } else if (isPaused) {
                    // Pause menu navigation
                    if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                        selectedPauseItem = Math.max(0, selectedPauseItem - 1);
                        screenShakeIntensity = 1;
                    } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                        int maxPauseIndex = renderer.getActivePauseButtonCount() - 1;
                        selectedPauseItem = Math.min(maxPauseIndex, selectedPauseItem + 1);
                        screenShakeIntensity = 1;
                    } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                        screenShakeIntensity = 3;
                        activatePauseMenuItem(selectedPauseItem);
                    } else if (key == KeyEvent.VK_ESCAPE) {
                        System.out.println("DEBUG: ESC pressed while paused - checking countdown setting");
                        isPaused = false;
                        // Start countdown based on mode: 2 = Always (both pause and resume)
                        if (gameData.getCountdownMode() == 2) {
                            unpauseCountdownActive = true;
                            unpauseCountdownTimer = UNPAUSE_COUNTDOWN_DURATION;
                        }
                        soundManager.playSound(SoundManager.Sound.UNPAUSE);
                        screenShakeIntensity = 2;
                    }
                } else {
                    // Regular gameplay controls
                    if (key == KeyEvent.VK_ESCAPE || (keyBindManager != null && keyBindManager.isAction(KeyBindManager.Action.PAUSE, key))) {
                        soundManager.playSound(SoundManager.Sound.PAUSE);
                        soundManager.stopProximityHum();
                        isPaused = true;
                        selectedPauseItem = 0;
                        renderer.configurePauseMenu(debugShowcaseInGameplay, tutorialMode);
                        screenShakeIntensity = 3;
                    } else if (keyBindManager != null ? keyBindManager.isAction(KeyBindManager.Action.RESTART, key) : key == KeyEvent.VK_R) {
                        // Reset: in showcase mode just clear bullets and reset boss, otherwise restart level
                        if (debugShowcaseInGameplay) {
                            resetShowcase();
                        } else {
                            startGame();
                        }
                    } else if (key == KeyEvent.VK_F && debugShowcaseInGameplay) {
                        // Force boss to shoot immediately in showcase mode
                        if (currentBoss != null) {
                            currentBoss.forceShoot(bullets, player);
                            soundManager.playSound(SoundManager.Sound.BOSS_SHOOT, 0.25f);
                        }
                    } else if ((keyBindManager != null ? keyBindManager.isAction(KeyBindManager.Action.CONFIRM, key) : key == KeyEvent.VK_SPACE) && introPanActive) {
                        // Skip intro animation
                        introPanActive = false;
                        cameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                        cameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                        screenShakeIntensity = 8;
                        if (currentBoss != null) currentBoss.setPosition(currentBoss.getX(), 100);
                        if (demoIntroActive) {
                            demoIntroActive = false;
                            transitionToState(GameState.MENU);
                        }
                    } else if ((keyBindManager != null ? keyBindManager.isAction(KeyBindManager.Action.USE_ITEM, key) : key == KeyEvent.VK_SPACE) && !eKeyPressed && !introPanActive) {
                        // Activate equipped item (only once per key press, and not during intro)
                        System.out.println("SPACE pressed - Attempting item activation");
                        // Powerless contract (type 3) disables ALL active items
                        if (riskContractType == 3) {
                            if (comboSystem != null) comboSystem.setAnnouncement("DISABLED!", WIDTH / 2.0, HEIGHT / 2.0);
                        } else {
                            eKeyPressed = true;
                            ActiveItem item = gameData.getEquippedItem();
                            System.out.println("SPACE: item=" + (item != null ? item.getType() : "null") + 
                                             ", canActivate=" + (item != null ? item.canActivate() : "N/A"));
                            if (item != null && item.canActivate()) {
                                item.activate();
                                // Apply item cooldown passive reduction
                                double cdMult = passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.ITEM_COOLDOWN);
                                if (cdMult < 1.0) item.setCurrentCooldown(item.getCurrentCooldown() * cdMult);
                                System.out.println("SPACE: Item activated!");
                                if (tutorialMode) tutorialItemUsed = true;
                                screenShakeIntensity = 3;
                                // Handle instant effects immediately (before update() deactivates them)
                                if (item.getActiveDuration() == 0) {
                                    handleActiveItemEffects(item, 1.0);
                                }
                                // Handle one-time activation effects for duration-based items
                                if (item.getType() == ActiveItem.ItemType.LUCKY_CHARM && player != null) {
                                    // Spawn a single money circle at player position
                                    double circleX = player.getX();
                                    double circleY = player.getY();
                                    moneyCircles.add(new double[]{circleX, circleY, 0});
                                    soundManager.playSound(SoundManager.Sound.COIN_PICKUP);
                                    
                                    // Create spawn particles (green to match circle)
                                    if (enableParticles) {
                                        for (int i = 0; i < 20; i++) {
                                            double angle = Math.random() * TWO_PI;
                                            double dist = MONEY_CIRCLE_RADIUS * Math.random();
                                            addParticle(
                                                circleX + Math.cos(angle) * dist,
                                                circleY + Math.sin(angle) * dist,
                                                0, -1,
                                                new Color(50, 200, 80), 30, 5,
                                                Particle.ParticleType.SPARK
                                            );
                                        }
                                    }
                                }
                                // Frost beam activation - start extension animation
                                if (item.getType() == ActiveItem.ItemType.FROST_BEAM) {
                                    frostBeamExtending = true;
                                    frostBeamRetracting = false;
                                    frostBeamProgress = 0;
                                    frostBeamShakeTriggered = false; // Reset shake trigger
                                    screenShakeIntensity = 4; // Small initial shake
                                    soundManager.playSound(SoundManager.Sound.SCREEN_SHAKE, 0.3f);
                                    // Point beam at boss initially
                                    if (currentBoss != null && player != null) {
                                        double dx = currentBoss.getX() - player.getX();
                                        double dy = currentBoss.getY() - player.getY();
                                        frostBeamAngle = Math.atan2(dy, dx);
                                    } else if (player != null) {
                                        frostBeamAngle = player.getAngle();
                                    }
                                }
                            }
                        }
                    } else if (key == KeyEvent.VK_K) {
                        // Debug: Trigger boss wobble
                        if (currentBoss != null) {
                            currentBoss.triggerWobble();
                            System.out.println("DEBUG: K pressed - triggering boss wobble");
                        }
                    } else if (key == KeyEvent.VK_L) {
                        // Debug: Trigger boss twirl (360 rotation)
                        if (currentBoss != null) {
                            currentBoss.triggerTwirl();
                            System.out.println("DEBUG: L pressed - triggering boss twirl");
                        }
                    } else if (key == KeyEvent.VK_T) {
                        // Debug: Skip level instantly
                        debugSkipUsed = true;
                        if (currentBoss != null && !bossDeathAnimation) {
                            // Force boss to die properly
                            soundManager.playSound(SoundManager.Sound.BOSS_DEATH);
                            bossDeathAnimation = true;
                            deathAnimationTimer = DEATH_ANIMATION_DURATION;
                            bossDeathScale = 1.0;
                            bossDeathRotation = 0;
                            bossKillTime = gameTimeSeconds;
                            player = null; // Remove player
                            screenShakeIntensity = 25;
                            System.out.println("DEBUG: Level skipped via T key - boss death triggered");
                        } else if (!bossDeathAnimation) {
                            // Boss not spawned yet, transition directly to WIN
                            soundManager.stopMusic();
                            gameState = GameState.WIN;
                            if (renderer != null) renderer.setScreenEnteredTime(gradientTime);
                            screenShakeIntensity = 15;
                            System.out.println("DEBUG: Level skipped via T key - direct to WIN");
                        }
                    } else if (key == KeyEvent.VK_QUOTE) {
                        // Debug: Spawn boss hit particles at player position
                        if (player != null && enableParticles) {
                            double debugX = player.getX();
                            double debugY = player.getY();
                            System.out.println("DEBUG ' KEY: spawning particles at playerX=" + (int)debugX 
                                + " playerY=" + (int)debugY + " cameraX=" + (int)cameraX + " cameraY=" + (int)cameraY
                                + " screenX=" + (int)(debugX - cameraX) + " screenY=" + (int)(debugY - cameraY));
                            // Bright white/yellow impact flash
                            for (int i = 0; i < 30; i++) {
                                double angle = Math.random() * TWO_PI;
                                double speed = 2 + Math.random() * 6;
                                Color impactColor = Math.random() < 0.5 ? IMPACT_WHITE : IMPACT_YELLOW;
                                addParticle(debugX, debugY, Math.cos(angle) * speed, Math.sin(angle) * speed,
                                    impactColor, 20, 8, Particle.ParticleType.SPARK);
                            }
                            // Smoke
                            for (int i = 0; i < 8; i++) {
                                double angle = Math.random() * TWO_PI;
                                double speed = 0.3 + Math.random() * 1.2;
                                addParticle(debugX + (Math.random() - 0.5) * 30, debugY + (Math.random() - 0.5) * 20,
                                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                                    SMOKE_GRAY, 50 + (int)(Math.random() * 20), 12 + Math.random() * 8, Particle.ParticleType.SMOKE);
                            }
                            // Fire
                            for (int i = 0; i < 15 && particles.size() < MAX_PARTICLES; i++) {
                                double angle = Math.random() * TWO_PI;
                                double speed = 1 + Math.random() * 4;
                                Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                                addParticle(debugX, debugY, Math.cos(angle) * speed, Math.sin(angle) * speed,
                                    fireColor, 30, 5, Particle.ParticleType.SPARK);
                            }
                            // Explosion rings
                            for (int i = 0; i < 3 && particles.size() < MAX_PARTICLES; i++) {
                                addParticle(debugX, debugY, 0, 0, FIRE_ORANGE, 40 + i * 10, 40 + i * 25,
                                    Particle.ParticleType.EXPLOSION);
                            }
                        }
                    } else if (key == KeyEvent.VK_SEMICOLON) {
                        // Debug: Spawn boss hit explosion at BOSS position (not player)
                        if (currentBoss != null && enableParticles) {
                            double debugX = currentBoss.getX();
                            double debugY = currentBoss.getY();
                            System.out.println("DEBUG ; KEY: spawning at BOSS pos bossX=" + (int)debugX 
                                + " bossY=" + (int)debugY + " cameraX=" + (int)cameraX + " cameraY=" + (int)cameraY
                                + " screenX=" + (int)(debugX - cameraX) + " screenY=" + (int)(debugY - cameraY)
                                + " bossSize=" + currentBoss.getSize());
                            // Same particles as the real boss hit explosion
                            for (int i = 0; i < 30; i++) {
                                double angle = Math.random() * TWO_PI;
                                double speed = 2 + Math.random() * 6;
                                Color impactColor = Math.random() < 0.5 ? IMPACT_WHITE : IMPACT_YELLOW;
                                addParticle(debugX, debugY, Math.cos(angle) * speed, Math.sin(angle) * speed,
                                    impactColor, 20, 8, Particle.ParticleType.SPARK);
                            }
                            for (int i = 0; i < 8; i++) {
                                double angle = Math.random() * TWO_PI;
                                double speed = 0.3 + Math.random() * 1.2;
                                addParticle(debugX + (Math.random() - 0.5) * 30, debugY + (Math.random() - 0.5) * 20,
                                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                                    SMOKE_GRAY, 50 + (int)(Math.random() * 20), 12 + Math.random() * 8, Particle.ParticleType.SMOKE);
                            }
                            for (int i = 0; i < 15 && particles.size() < MAX_PARTICLES; i++) {
                                double angle = Math.random() * TWO_PI;
                                double speed = 1 + Math.random() * 4;
                                Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                                addParticle(debugX, debugY, Math.cos(angle) * speed, Math.sin(angle) * speed,
                                    fireColor, 30, 5, Particle.ParticleType.SPARK);
                            }
                            for (int i = 0; i < 3 && particles.size() < MAX_PARTICLES; i++) {
                                addParticle(debugX, debugY, 0, 0, FIRE_ORANGE, 40 + i * 10, 40 + i * 25,
                                    Particle.ParticleType.EXPLOSION);
                            }
                            screenShakeIntensity = 10;
                        }
                    } else if ((key == KeyEvent.VK_N || key == KeyEvent.VK_ESCAPE) && debugShowcaseMode) {
                        // Debug showcase: Return to selection screen - restore the real game level
                        gameData.setCurrentLevel(savedRealLevel);
                        debugShowcaseInGameplay = false;
                        bullets.clear();
                        beamAttacks.clear();
                        flares.clear();
                        flareCooldownTimer = 300;
                        transitionToState(GameState.ATTACK_SHOWCASE);
                        System.out.println("DEBUG SHOWCASE: Returning to selection - restored level to " + savedRealLevel);
                    }
                }
                break;
                
            case SHOP:
                // Tutorial popup dismiss — intercept all input during popup
                if (tutorialMode && tutorialPopupActive) {
                    if (tutorialPopupInputDelay > 0) break; // 2-second buffer before allowing dismiss
                    if (!KeyBindManager.isReservedKey(key)) {
                        tutorialPopupActive = false;
                        renderer.tutorialPopupActive = false;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        screenShakeIntensity = 2;
                    }
                    break;
                }
                // Handle passive unlock animation input first (blocks normal shop input)
                if (passiveUnlockAnimation) {
                    if (key == KeyEvent.VK_ESCAPE) {
                        // ESC cancels all remaining passive popups immediately
                        passiveUnlockAnimation = false;
                        passiveUnlockDismissing = false;
                        pendingPassiveUnlocks.clear();
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        break;
                    }
                    if (key == KeyEvent.VK_SPACE) {
                        if (!passiveUnlockDismissing) {
                            if (passiveUnlockTimer > 0) {
                                // First press: skip to fully revealed
                                passiveUnlockTimer = 0;
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            } else {
                                // Second press: start dismiss animation
                                passiveUnlockDismissing = true;
                                passiveUnlockDismissTimer = PASSIVE_DISMISS_DURATION;
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            }
                        }
                    }
                    break; // Block all other shop input during animation
                }
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { 
                    shopManager.selectPrevious();
                    updateShopScroll();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1; 
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { 
                    shopManager.selectNext();
                    updateShopScroll();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1; 
                }
                else if (key == KeyEvent.VK_SPACE) {
                    int selected = shopManager.getSelectedShopItem();
                    if (selected == 0) {
                        // Continue
                        if (tutorialMode && !tutorialShopPurchased) {
                            // Must buy something first in tutorial
                            soundManager.playSound(SoundManager.Sound.PURCHASE_FAIL);
                            screenShakeIntensity = 2;
                        } else {
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            if (tutorialMode) {
                                // Tutorial: advance to settings step and return to gameplay
                                advanceTutorialStep();
                                transitionToState(GameState.PLAYING);
                            } else {
                                transitionToState(GameState.LEVEL_SELECT);
                            }
                            screenShakeIntensity = 5;
                        }
                    } else {
                        System.out.println("DEBUG SHOP: Attempting purchase of item " + selected + ", money: " + gameData.getTotalMoney() + ", cost: " + shopManager.getItemCost(selected));
                        boolean purchased = shopManager.purchaseItem(selected);
                        System.out.println("DEBUG SHOP: Purchase result: " + purchased + ", money after: " + gameData.getTotalMoney());
                        if (purchased) {
                            soundManager.playSound(SoundManager.Sound.PURCHASE_SUCCESS);
                            // Tutorial: track purchase for step 6
                            if (tutorialMode) tutorialShopPurchased = true;
                            // Auto-save after successful purchase
                            performAutoSave();
                        } else {
                            soundManager.playSound(SoundManager.Sound.PURCHASE_FAIL);
                        }
                        screenShakeIntensity = purchased ? 4 : 2;
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) { 
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                    
                    // Auto-save when exiting shop
                    performAutoSave();
                    if (tutorialMode) {
                        // Quit tutorial from shop
                        quitTutorial();
                        return;
                    }
                    tutorialShopVisited = true;
                    
                    // Go back to where we came from: menu if from menu, level select if from gameplay
                    if (shopEnteredFrom == GameState.MENU) {
                        transitionToState(GameState.MENU);
                    } else {
                        transitionToState(GameState.LEVEL_SELECT);
                    }
                    screenShakeIntensity = 3; 
                }
                break;
                
            case GAME_OVER:
                if (key == KeyEvent.VK_SPACE) {
                    if (gameData.isInEndlessMode()) {
                        // Endless mode death: reset to prestige checkpoint
                        boolean fullReset = gameData.getGameMode().resetsOnDeath();
                        gameData.resetEndlessRun(fullReset);
                        gameData.setInEndlessMode(false); // Return to level select
                    }
                    // Roguelike: Player died - no money earned (only earn on level completion)
                    gameData.startNewRun(); // Resets to level 1, keeps upgrades/items
                    passiveUpgradeManager.resetMissilesPrice(); // Reset extra missiles price for new run
                    performAutoSave(); // Save progress after death
                    // Force players to go through level select again
                    transitionToState(GameState.LEVEL_SELECT);
                } else if (key == KeyEvent.VK_ESCAPE) {
                    if (gameData.isInEndlessMode()) {
                        boolean fullReset = gameData.getGameMode().resetsOnDeath();
                        gameData.resetEndlessRun(fullReset);
                        gameData.setInEndlessMode(false);
                    }
                    // Go to menu - no money earned (only earn on level completion)
                    gameData.startNewRun();
                    passiveUpgradeManager.resetMissilesPrice(); // Reset extra missiles price for new run
                    performAutoSave(); // Save progress after death
                    transitionToState(GameState.MENU);
                }
                break;

            case DEMO_OVER:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    demoOverSelection = 0;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    demoOverSelection = 1;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    if (demoOverSelection == 0) {
                        // Play Again — reset to fresh demo state
                        resetDemoState();
                        transitionToState(GameState.MENU);
                    } else {
                        // Quit
                        System.exit(0);
                    }
                }
                break;
                
            case WIN:
                // Handle equip prompt input FIRST (before SPACE check)
                if (showEquipPrompt && itemUnlockAnimation && itemUnlockTimer == 0) {
                    if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                        selectedEquipButton = 0; // Yes
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        return;
                    } else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                        selectedEquipButton = 1; // No
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        return;
                    } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                        if (selectedEquipButton == 0) {
                            // Yes - Equip the new item
                            gameData.equipItem(newItemIndex);
                            soundManager.playSound(SoundManager.Sound.ITEM_PICKUP);
                        } else {
                            // No - Don't equip
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        }
                        showEquipPrompt = false;
                        itemUnlockDismissing = true;
                        itemUnlockDismissTimer = ITEM_DISMISS_DURATION;
                        return;
                    } else if (key == KeyEvent.VK_Y) {
                        // Quick yes with Y key
                        gameData.equipItem(newItemIndex);
                        soundManager.playSound(SoundManager.Sound.ITEM_PICKUP);
                        showEquipPrompt = false;
                        itemUnlockDismissing = true;
                        itemUnlockDismissTimer = ITEM_DISMISS_DURATION;
                        return;
                    } else if (key == KeyEvent.VK_N) {
                        // Quick no with N key
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        showEquipPrompt = false;
                        itemUnlockDismissing = true;
                        itemUnlockDismissTimer = ITEM_DISMISS_DURATION;
                        return;
                    }
                    return; // Wait for valid input, don't process other keys
                }
                
                if (key == KeyEvent.VK_SPACE) {
                    // If endless unlock animation is playing, skip to reveal or start dismiss
                    if (endlessUnlockAnimation && !endlessUnlockDismissing) {
                        if (endlessUnlockTimer > 0) {
                            endlessUnlockTimer = 0;
                            return;
                        }
                        endlessUnlockDismissing = true;
                        endlessUnlockDismissTimer = ENDLESS_DISMISS_DURATION;
                        return;
                    }
                    if (endlessUnlockDismissing) {
                        return;
                    }
                    
                    // If contract animation is playing, skip to reveal or start dismiss
                    if (contractUnlockAnimation && !contractUnlockDismissing) {
                        // If still in animation phase, skip to reveal
                        if (contractUnlockTimer > 0) {
                            contractUnlockTimer = 0; // Skip to fully revealed state
                            return;
                        }
                        // If already revealed, start dismiss animation
                        contractUnlockDismissing = true;
                        contractUnlockDismissTimer = CONTRACT_DISMISS_DURATION;
                        return;
                    }
                    // If contract dismiss is happening, wait
                    if (contractUnlockDismissing) {
                        return;
                    }
                    
                    // If item animation is playing, skip to reveal or start dismiss
                    if (itemUnlockAnimation && !itemUnlockDismissing) {
                        // If still in animation phase, skip to reveal
                        if (itemUnlockTimer > 0) {
                            itemUnlockTimer = 0; // Skip to fully revealed state
                            return;
                        }
                        // If already revealed but showing equip prompt, don't dismiss yet
                        if (showEquipPrompt) {
                            return; // Wait for Y/N input
                        }
                        // If already revealed, start dismiss animation
                        itemUnlockDismissing = true;
                        itemUnlockDismissTimer = ITEM_DISMISS_DURATION;
                        return;
                    }
                    // If already dismissing, wait for it to complete
                    if (itemUnlockDismissing) {
                        return;
                    }
                    
                    // Progression (money, level unlock, boss defeat) already handled in boss death handler.
                    // Just transition to the next screen.
                    if (DEMO_MODE && gameData.getCurrentLevel() > DEMO_MAX_LEVEL) {
                        demoOverSelection = 0;
                        transitionToState(GameState.DEMO_OVER);
                    } else if (leaderboardManager.getRecentResult() != null && !gameData.isInEndlessMode()) {
                        // Show leaderboard screen for campaign levels
                        transitionToState(GameState.LEADERBOARD);
                    } else {
                        shopEnteredFrom = GameState.PLAYING;
                        transitionToState(GameState.SHOP);
                    }
                }
                break;
            
            case LEADERBOARD:
                // Any key skips animation or exits the leaderboard screen
                if (!leaderboardReadyToExit) {
                    // Skip animation — snap to final state
                    leaderboardAnimSkipped = true;
                    leaderboardScreenTimer = 300; // Jump past all animation phases
                } else {
                    // Animation done — proceed to shop
                    leaderboardManager.clearRecentResult();
                    shopEnteredFrom = GameState.PLAYING;
                    transitionToState(GameState.SHOP);
                }
                break;
                
            case DEBUG:
                // Handle item/contract unlock animation input first (debug preview)
                if (itemUnlockAnimation) {
                    if (key == KeyEvent.VK_ESCAPE) {
                        // ESC cancels all remaining debug popups immediately
                        itemUnlockAnimation = false;
                        itemUnlockDismissing = false;
                        if (debugItemPopupQueue != null) debugItemPopupQueue.clear();
                        debugShowContractAfterItems = false;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        break;
                    }
                    if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                        if (!itemUnlockDismissing) {
                            if (itemUnlockTimer > 0) {
                                itemUnlockTimer = 0; // Skip to fully revealed
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            } else {
                                itemUnlockDismissing = true;
                                itemUnlockDismissTimer = ITEM_DISMISS_DURATION;
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            }
                        }
                    }
                    break; // Block all other input during animation
                }
                if (contractUnlockAnimation) {
                    if (key == KeyEvent.VK_ESCAPE) {
                        // ESC cancels contract popup immediately
                        contractUnlockAnimation = false;
                        contractUnlockDismissing = false;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        break;
                    }
                    if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                        if (!contractUnlockDismissing) {
                            if (contractUnlockTimer > 0) {
                                contractUnlockTimer = 0;
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            } else {
                                contractUnlockDismissing = true;
                                contractUnlockDismissTimer = CONTRACT_DISMISS_DURATION;
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            }
                        }
                    }
                    break; // Block all other input during animation
                }
                if (endlessUnlockAnimation) {
                    if (key == KeyEvent.VK_ESCAPE) {
                        endlessUnlockAnimation = false;
                        endlessUnlockDismissing = false;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        break;
                    }
                    if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                        if (!endlessUnlockDismissing) {
                            if (endlessUnlockTimer > 0) {
                                endlessUnlockTimer = 0;
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            } else {
                                endlessUnlockDismissing = true;
                                endlessUnlockDismissTimer = ENDLESS_DISMISS_DURATION;
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            }
                        }
                    }
                    break;
                }
                
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    selectedDebugOption = (selectedDebugOption - 1 + DEBUG_OPTION_COUNT) % DEBUG_OPTION_COUNT;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    selectedDebugOption = (selectedDebugOption + 1) % DEBUG_OPTION_COUNT;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                    if (selectedDebugOption == 11) { // Set Unlocked Level - decrease
                        debugSetLevelValue = Math.max(1, debugSetLevelValue - 1);
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (selectedDebugOption == 14) { // Test Leaderboard - decrease level
                        debugLeaderboardLevel = Math.max(1, debugLeaderboardLevel - 1);
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    if (selectedDebugOption == 11) { // Set Unlocked Level - increase
                        debugSetLevelValue = Math.min(28, debugSetLevelValue + 1);
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (selectedDebugOption == 14) { // Test Leaderboard - increase level
                        debugLeaderboardLevel = Math.min(28, debugLeaderboardLevel + 1);
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                }
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    activateDebugOption(selectedDebugOption);
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    transitionToState(GameState.MENU);
                    screenShakeIntensity = 3;
                }
                break;
        }
    }
    
    private void selectPreviousLevel() {
        gameData.setCurrentLevel(Math.max(1, gameData.getCurrentLevel() - 1));
        soundManager.playSound(SoundManager.Sound.LEVEL_SWITCH);
    }
    
    private void selectNextLevel() {
        gameData.setCurrentLevel(Math.min(gameData.getMaxUnlockedLevel(), gameData.getCurrentLevel() + 1));
        soundManager.playSound(SoundManager.Sound.LEVEL_SWITCH);
        ensureLevelVisible();
    }
    
    private void scrollLevelSelectUp() {
        levelSelectScroll = Math.max(0, levelSelectScroll - 150);
    }
    
    private void scrollLevelSelectDown() {
        levelSelectScroll += 150;
    }
    
    private void ensureLevelVisible() {
        // Auto-scroll to keep selected level visible
        int level = gameData.getCurrentLevel();
        int row = (level - 1) / 3; // 3 columns per row
        int levelY = 200 + row * 150 - (int)levelSelectScroll;
        
        // If level is above visible area, scroll up
        if (levelY < 180) {
            levelSelectScroll = Math.max(0, 200 + row * 150 - 180);
        }
        // If level is below visible area, scroll down
        else if (levelY > HEIGHT - 200) {
            levelSelectScroll = 200 + row * 150 - (HEIGHT - 350);
        }
    }
    
    private void ensureSettingsItemVisible() {
        // Auto-scroll to keep selected setting visible
        if (selectedSettingsItem < 0) {
            // Tabs selected - scroll to top
            settingsScroll = 0;
            return;
        }
        
        // Calculate item position accounting for section headers in graphics tab
        int headerOffset = 0;
        if (selectedSettingsCategory == 0) {
            // Graphics has section headers at indices 0, 4, 8, 11, 15
            int[] headerIndices = {0, 4, 8, 11, 15};
            for (int h : headerIndices) {
                if (selectedSettingsItem >= h) headerOffset += 24;
            }
        }
        
        // Controls tab: calculate visible position accounting for collapsed sections
        if (selectedSettingsCategory == 4) {
            int visibleCount = 0;
            for (int i = 0; i < selectedSettingsItem; i++) {
                if (isControlsItemVisible(i)) visibleCount++;
            }
            int itemY = 200 + visibleCount * 78 - (int)settingsScroll;
            if (itemY < 180) {
                settingsScroll = Math.max(0, 200 + visibleCount * 78 - 180);
            } else if (itemY > HEIGHT - 250) {
                settingsScroll = 200 + visibleCount * 78 - (HEIGHT - 400);
            }
            return;
        }
        
        int itemY = 200 + selectedSettingsItem * 78 + headerOffset - (int)settingsScroll;
        
        // If item is above visible area, scroll up
        if (itemY < 180) {
            settingsScroll = Math.max(0, 200 + selectedSettingsItem * 78 + headerOffset - 180);
        }
        // If item is below visible area, scroll down
        else if (itemY > HEIGHT - 250) {
            settingsScroll = 200 + selectedSettingsItem * 78 + headerOffset - (HEIGHT - 400);
        }
    }
    
    /** Check if a controls tab item is visible (not in a collapsed section). */
    private boolean isControlsItemVisible(int index) {
        if (!controlsKeyboardExpanded && index >= 3 && index <= 11) return false;
        if (!controlsControllerExpanded && index >= 13 && index <= 21) return false;
        return true;
    }
    
    private void navigateLevelMap(int direction) {
        int maxLevel = DEMO_MODE ? DEMO_MAX_LEVEL : CAMPAIGN_LEVELS;
        // Allow navigating to endless slot (29) if endless mode is unlocked
        if (!DEMO_MODE && gameData.isEndlessUnlocked()) {
            maxLevel = ENDLESS_SLOT;
        }
        int newLevel = gameData.getSelectedLevelView() + direction;
        if (newLevel >= 1 && newLevel <= maxLevel) {
            gameData.setSelectedLevelView(newLevel);
            // Set target scroll position (will animate smoothly)
            levelSelectScroll = newLevel;
            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
        }
    }
    
    private void tryStartLevel() {
        int selectedLevel = gameData.getSelectedLevelView();
        
        // Endless mode slot
        if (selectedLevel == ENDLESS_SLOT) {
            if (gameData.isEndlessUnlocked()) {
                // Enter endless mode - set the internal level to endless current
                gameData.setInEndlessMode(true);
                selectedLevelToStart = selectedLevel;
                selectedConfirmItem = 0;
                isConfirmingResume = false;
                transitionToState(GameState.LEVEL_CONFIRM);
                soundManager.playSound(SoundManager.Sound.UI_SELECT);
            } else {
                soundManager.playSound(SoundManager.Sound.UI_ERROR);
            }
            return;
        }
        
        int currentLevel = gameData.getCurrentLevel();
        int maxUnlocked = gameData.getMaxUnlockedLevel();
        
        // Cannot replay levels that have already been beaten
        if (selectedLevel < currentLevel) {
            soundManager.playSound(SoundManager.Sound.UI_ERROR);
            return;
        }
        
        // Can start any unlocked level (for debug or level select)
        if (selectedLevel <= maxUnlocked) {
            // Show confirmation dialog before starting level
            gameData.setInEndlessMode(false); // Ensure we're in campaign mode
            selectedLevelToStart = selectedLevel;
            selectedConfirmItem = 0;
            isConfirmingResume = false; // This is a new game start
            transitionToState(GameState.LEVEL_CONFIRM);
            soundManager.playSound(SoundManager.Sound.UI_SELECT);
        } else {
            // Locked - play error sound  
            soundManager.playSound(SoundManager.Sound.UI_ERROR);
        }
    }
    
    private void confirmLevelStart() {
        if (selectedConfirmItem == 0) {
            // Yes - start plane takeoff animation
            soundManager.playSound(SoundManager.Sound.LEVEL_START);
            planeTakeoffAnimation = true;
            planeTakeoffTimer = 0;
        } else {
            // No - go back to level select
            soundManager.playSound(SoundManager.Sound.UI_SELECT);
            transitionToState(GameState.LEVEL_SELECT);
        }
    }
    
    private void startSelectedLevel() {
        // Roguelike: Always start at current level (can't replay old levels)
        soundManager.playSound(SoundManager.Sound.LEVEL_START);
        
        // Endless mode: skip risk contracts, skip attack intros (player has seen them all)
        if (gameData.isInEndlessMode()) {
            riskContractType = 0;
            riskContractActive = false;
            riskContractMultiplier = 1.0;
            startGame();
            return;
        }
        
        // Only show risk contract screen if contracts are unlocked
        if (gameData.areContractsUnlocked()) {
            selectedRiskContract = 0;
            transitionToState(GameState.RISK_CONTRACT);
        } else {
            // Skip contract selection, start with no contract
            riskContractType = 0;
            riskContractActive = false;
            riskContractMultiplier = 1.0;
            
            // Check for new attack introductions at this level
            checkForNewAttackIntros();
            
            // If there are pending intros, show them first; otherwise start game
            if (!pendingAttackIntros.isEmpty()) {
                showNextAttackIntro();
            } else {
                startGame();
            }
        }
    }
    
    private void pressOnScreenKey() {
        String row = ON_SCREEN_KB_ROWS[onScreenKbRow];
        if (onScreenKbCol < row.length()) {
            char selectedChar = row.charAt(onScreenKbCol);
            if (selectedChar == '\u23CE') {
                confirmSaveName();
            } else if (selectedChar == '\u2190') {
                // Backspace
                if (saveNameCursorPos > 0) {
                    saveNameInput.deleteCharAt(saveNameCursorPos - 1);
                    saveNameCursorPos--;
                    saveNameCursorBlink = 0;
                }
            } else {
                // Type the character
                if (saveNameInput.length() < MAX_SAVE_NAME_LENGTH) {
                    saveNameInput.insert(saveNameCursorPos, selectedChar);
                    saveNameCursorPos++;
                    saveNameCursorBlink = 0;
                }
            }
            soundManager.playSound(SoundManager.Sound.UI_SELECT);
        }
    }
    
    private void confirmSaveName() {
        String name = saveNameInput.toString().trim();
        if (name.isEmpty()) {
            name = "Save " + pendingSaveSlot;
        }
        
        GameMode[] modes = GameMode.values();
        GameMode chosenMode = modes[selectedGameModeIndex];
        SaveData newSave = new SaveData();
        newSave.saveName = name;
        newSave.gameMode = chosenMode;
        if (saveManager.save(pendingSaveSlot, newSave)) {
            newSave.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
            gameData.setCustomSaveName(name);
            soundManager.setMasterVolume(gameData.getMasterVolume());
            soundManager.setSfxVolume(gameData.getSfxVolume());
            soundManager.setUiVolume(gameData.getUiVolume());
            soundManager.setMusicVolume(gameData.getMusicVolume());
            soundManager.setSoundEnabled(gameData.isSoundEnabled());
            soundManager.setSpatialAudioEnabled(gameData.isSpatialAudioEnabled());
            hasSavedGame = false;
            soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
            screenShakeIntensity = 5;
            transitionToState(GameState.MENU);
            // Show tutorial prompt for new saves
            if (!gameData.isTutorialCompleted()) {
                showTutorialPrompt = true;
                tutorialPromptSelection = 0;
            }
        }
        pendingSaveSlot = -1;
    }
    
    private void confirmRiskContract() {
        riskContractType = selectedRiskContract;
        riskContractActive = selectedRiskContract > 0;
        riskContractMultiplier = RISK_CONTRACT_MULTIPLIERS[selectedRiskContract];
        
        // Check for new attack introductions at this level
        checkForNewAttackIntros();
        
        // If there are pending intros, show them first; otherwise start game
        if (!pendingAttackIntros.isEmpty()) {
            showNextAttackIntro();
        } else {
            startGame();
        }
    }
    
    /**
     * Check which attacks are introduced at the current level and haven't been seen yet
     */
    private void checkForNewAttackIntros() {
        pendingAttackIntros.clear();
        int level = gameData.getCurrentLevel();
        System.out.println("DEBUG ATTACK INTRO: Checking for new attack intros at level " + level);
        
        for (String[] intro : ATTACK_INTROS) {
            String attackId = intro[0];
            int introLevel = Integer.parseInt(intro[1]);
            
            // Check if this attack is introduced at current level and hasn't been seen
            if (introLevel == level && !gameData.hasSeenAttackIntro(attackId)) {
                System.out.println("DEBUG ATTACK INTRO: Found new attack - " + attackId);
                pendingAttackIntros.add(attackId);
            }
        }
        System.out.println("DEBUG ATTACK INTRO: Total pending intros: " + pendingAttackIntros.size());
    }
    
    /**
     * Show the next attack introduction from the pending queue
     */
    private void showNextAttackIntro() {
        if (pendingAttackIntros.isEmpty()) {
            startGame();
            return;
        }
        
        String attackId = pendingAttackIntros.remove(0);
        
        // Find the attack data
        for (String[] intro : ATTACK_INTROS) {
            if (intro[0].equals(attackId)) {
                currentAttackIntroId = attackId;
                currentAttackIntroName = intro[2];
                currentAttackIntroDescription = intro[3];
                
                // Load the attack intro image
                attackIntroImage = loadAttackIntroImage(attackId);
                
                transitionToState(GameState.ATTACK_INTRO);
                break;
            }
        }
    }
    
    /**
     * Load or get cached attack intro image
     * Falls back to a placeholder if the image file doesn't exist
     */
    private BufferedImage loadAttackIntroImage(String attackId) {
        // Check cache first
        if (attackIntroImageCache.containsKey(attackId)) {
            return attackIntroImageCache.get(attackId);
        }
        
        // Try to load the actual image
        String imagePath = "sprites/Tutorial/Attacks and Bullets Intros/" + attackId + ".png";
        try {
            BufferedImage img = AssetLoader.loadImage(imagePath);
            if (img != null) {
                attackIntroImageCache.put(attackId, img);
                System.out.println("Loaded attack intro image: " + imagePath);
                return img;
            }
        } catch (Exception e) {
            System.err.println("Could not load attack intro image: " + imagePath + " - " + e.getMessage());
        }
        
        // Fall back to placeholder
        BufferedImage placeholder = createPlaceholderAttackImage(attackId);
        attackIntroImageCache.put(attackId, placeholder);
        return placeholder;
    }
    
    /**
     * Create a placeholder image for attack introductions (fallback)
     */
    private BufferedImage createPlaceholderAttackImage(String attackId) {
        int size = 200;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        
        // Draw placeholder rectangle with attack name
        g.setColor(new Color(60, 60, 80));
        g.fillRoundRect(0, 0, size, size, 20, 20);
        g.setColor(new Color(100, 150, 200));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(2, 2, size - 4, size - 4, 20, 20);
        
        // Draw question mark or icon based on attack type
        g.setColor(new Color(200, 200, 220));
        g.setFont(FontPalette.TITLE_MEDIUM);
        FontMetrics fm = g.getFontMetrics();
        String symbol = "?";
        
        // Choose symbol based on attack type
        if (attackId.contains("bullet")) symbol = "â€¢";
        else if (attackId.contains("beam")) symbol = "â•";
        else if (attackId.contains("shock")) symbol = "â—¯";
        else if (attackId.contains("grenade") || attackId.contains("nuke") || attackId.contains("bomb")) symbol = "ðŸ’£";
        else if (attackId.contains("twirl")) symbol = "â†»";
        else if (attackId.contains("spiral")) symbol = "ðŸŒ€";
        
        int textX = (size - fm.stringWidth(symbol)) / 2;
        int textY = (size + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(symbol, textX, textY);
        
        g.dispose();
        return img;
    }
    
    /**
     * Dismiss the current attack intro and continue
     */
    private void dismissAttackIntro() {
        // Mark as seen
        if (currentAttackIntroId != null) {
            gameData.markAttackIntroSeen(currentAttackIntroId);
        }
        
        // Show next intro or start game
        if (!pendingAttackIntros.isEmpty()) {
            showNextAttackIntro();
        } else {
            startGame();
        }
    }
    
    private void handleMouseMove() {
        // Only handle mouse in menu states
        if (renderer == null) return; // Guard against null renderer
        
        // Don't allow mouse selection during scroll cooldown
        if (scrollCooldown > 0) return;
        
        if (gameState == GameState.MENU) {
            // Tutorial prompt hover - update button selection
            if (showTutorialPrompt) {
                int panelW = config.UIScale.px(460);
                int panelH = config.UIScale.px(220);
                int panelX = (WIDTH - panelW) / 2;
                int panelY = (HEIGHT - panelH) / 2;
                int btnW = config.UIScale.px(120);
                int btnH = config.UIScale.px(45);
                int btnY = panelY + config.UIScale.px(145);
                int btnGap = config.UIScale.px(30);
                int yesX = panelX + (panelW / 2) - btnW - btnGap / 2;
                int noX = panelX + (panelW / 2) + btnGap / 2;
                if (mouseX >= yesX && mouseX <= yesX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    if (tutorialPromptSelection != 0) {
                        tutorialPromptSelection = 0;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                } else if (mouseX >= noX && mouseX <= noX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    if (tutorialPromptSelection != 1) {
                        tutorialPromptSelection = 1;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                }
                return;
            }
            UIButton[] buttons = renderer.getMenuButtons();
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].contains(mouseX, mouseY)) {
                    if (selectedMenuItem != i) {
                        selectedMenuItem = i;
                        screenShakeIntensity = 1;
                    }
                    break;
                }
            }
        } else if (gameState == GameState.SETTINGS) {
            // When warning dialog is showing, only update button hover selection
            if (showSettingsWarning) {
                java.awt.Rectangle[] warnBtns = renderer.getWarningButtonBounds();
                for (int i = 0; i < 3; i++) {
                    if (warnBtns[i] != null && warnBtns[i].contains(mouseX, mouseY)) {
                        if (settingsWarningSelection != i) {
                            settingsWarningSelection = i;
                            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                            screenShakeIntensity = 1;
                        }
                        break;
                    }
                }
                return;
            }
            // Check if hovering over category tabs (use UIScale to match Renderer positions)
            String[] categories = {"GRAPHICS", "AUDIO", "GAMEPLAY", "DEBUG", "CONTROLS", "HUD"};
            int tabWidth = UIScale.px(130);
            int tabStartX = (WIDTH - categories.length * tabWidth) / 2;
            int tabY = UIScale.px(130);
            int tabHeight = UIScale.px(40);
            
            boolean hoveringTab = false;
            for (int i = 0; i < categories.length; i++) {
                int tabX = tabStartX + i * tabWidth;
                if (mouseX >= tabX && mouseX <= tabX + tabWidth - 10 &&
                    mouseY >= tabY && mouseY <= tabY + tabHeight) {
                    // Hovering over tabs - select tabs (-1)
                    if (selectedSettingsItem != -1) {
                        selectedSettingsItem = -1;
                        screenShakeIntensity = 1;
                    }
                    hoveringTab = true;
                    break;
                }
            }
            
            // Check if hovering over settings items
            if (!hoveringTab) {
                if (selectedSettingsCategory == 5) {
                    // HUD tab: delegate to editor
                    renderer.hudLayoutEditor.handleMouseMoved(mouseX, mouseY, hudLayout);
                } else {
                // Use actual button positions set by the renderer for accurate hover detection
                int maxItems = getMaxSettingsItems();
                UIButton[] buttons = renderer.getSettingsButtons();
                
                boolean foundHover = false;
                for (int i = 0; i <= maxItems; i++) {
                    if (selectedSettingsCategory == 4 && !isControlsItemVisible(i)) continue;
                    if (i < buttons.length && buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                        if (selectedSettingsItem != i) {
                            selectedSettingsItem = i;
                            screenShakeIntensity = 1;
                        }
                        foundHover = true;
                        break;
                    }
                }
                }
            }
        } else if (gameState == GameState.MODE_SELECT) {
            // Check if hovering over mode cards (use stored bounds from Renderer)
            java.awt.Rectangle[] modeBounds = renderer.getModeCardBounds();
            if (modeBounds != null) {
                for (int i = 0; i < modeBounds.length; i++) {
                    if (modeBounds[i] != null && modeBounds[i].contains(mouseX, mouseY)) {
                        if (selectedGameModeIndex != i) {
                            selectedGameModeIndex = i;
                            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                            screenShakeIntensity = 1;
                        }
                        break;
                    }
                }
            }
        } else if (gameState == GameState.INFO) {
            // Help & Tutorial screen - hover over Showcase/Tutorial buttons
            if (renderer.helpShowcaseButton != null && renderer.helpShowcaseButton.contains(mouseX, mouseY)) {
                if (renderer.helpSelectedButton != 0) {
                    renderer.helpSelectedButton = 0;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
            } else if (renderer.helpTutorialButton != null && renderer.helpTutorialButton.contains(mouseX, mouseY)) {
                if (renderer.helpSelectedButton != 1) {
                    renderer.helpSelectedButton = 1;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
            }
        } else if (gameState == GameState.SAVE_SELECT) {
            // Check if hovering over save slots
            int slotWidth = WIDTH * 2 / 3;
            int slotHeight = 200;
            int slotX = (WIDTH - slotWidth) / 2;
            int startY = 200;
            int slotSpacing = 230;
            int totalEntries = saveMetadataCache.size() + 1; // existing saves + "New Save"

            for (int i = 0; i < totalEntries; i++) {
                int slotY = startY + i * slotSpacing - (int)saveSelectScrollAnimated;
                if (slotY + slotHeight < 0 || slotY > HEIGHT) continue;

                if (mouseX >= slotX && mouseX <= slotX + slotWidth &&
                    mouseY >= slotY && mouseY <= slotY + slotHeight) {
                    if (selectedSaveSlot != i) {
                        selectedSaveSlot = i;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                        deletingSlot = false;
                        deleteConfirmTimer = 0;
                    }
                    break;
                }
            }
        } else if (gameState == GameState.NAME_INPUT) {
            // Check if hovering over on-screen keyboard keys
            int kbStartY = UIScale.px(260);
            int keySize = UIScale.px(42);
            int keyGap = UIScale.px(6);

            for (int r = 0; r < ON_SCREEN_KB_ROWS.length; r++) {
                String row = ON_SCREEN_KB_ROWS[r];
                int rowWidth = row.length() * (keySize + keyGap) - keyGap;
                int rowX = (WIDTH - rowWidth) / 2;

                for (int c = 0; c < row.length(); c++) {
                    int kx = rowX + c * (keySize + keyGap);
                    int ky = kbStartY + r * (keySize + keyGap);

                    if (mouseX >= kx && mouseX <= kx + keySize &&
                        mouseY >= ky && mouseY <= ky + keySize) {
                        if (onScreenKbRow != r || onScreenKbCol != c) {
                            onScreenKbRow = r;
                            onScreenKbCol = c;
                            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                            screenShakeIntensity = 1;
                        }
                        return;
                    }
                }
            }
        } else if (gameState == GameState.PLAYING && tutorialMode && tutorialCompleteScreen) {
            // Tutorial complete screen hover - update button selection
            int panelW = config.UIScale.px(520);
            int panelH = config.UIScale.px(320);
            int panelX = (WIDTH - panelW) / 2;
            int panelY = (HEIGHT - panelH) / 2;
            int btnW = config.UIScale.px(140);
            int btnH = config.UIScale.px(45);
            int btnY = panelY + config.UIScale.px(220);
            int btnGap = config.UIScale.px(30);
            int leaveX = panelX + (panelW / 2) - btnW - btnGap / 2;
            int againX = panelX + (panelW / 2) + btnGap / 2;
            if (mouseX >= leaveX && mouseX <= leaveX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                if (tutorialCompleteSelection != 0) {
                    tutorialCompleteSelection = 0;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
            } else if (mouseX >= againX && mouseX <= againX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                if (tutorialCompleteSelection != 1) {
                    tutorialCompleteSelection = 1;
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
            }
        } else if (gameState == GameState.PLAYING && isPaused) {
            UIButton[] buttons = renderer.getPauseButtons();
            int activeCount = renderer.getActivePauseButtonCount();
            for (int i = 0; i < activeCount; i++) {
                if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                    if (selectedPauseItem != i) {
                        selectedPauseItem = i;
                        screenShakeIntensity = 1;
                    }
                    break;
                }
            }
        } else if (gameState == GameState.SHOP) {
            // Check if hovering over shop items
            UIButton[] buttons = renderer.getShopButtons();
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                    int currentSelected = shopManager.getSelectedShopItem();
                    if (currentSelected != i) {
                        shopManager.setSelectedShopItem(i);
                        screenShakeIntensity = 1;
                    }
                    break;
                }
            }
        } else if (gameState == GameState.RISK_CONTRACT) {
            // Check if hovering over risk contract cards
            // Card dimensions must match drawRiskContract in Renderer
            int cardWidth = 280;
            int cardHeight = 380;
            int cardSpacing = 40;
            int totalWidth = RISK_CONTRACT_NAMES.length * cardWidth + (RISK_CONTRACT_NAMES.length - 1) * cardSpacing;
            int startX = (WIDTH - totalWidth) / 2;
            int cardY = (HEIGHT - cardHeight) / 2 - 40;
            
            for (int i = 0; i < RISK_CONTRACT_NAMES.length; i++) {
                int cardX = startX + i * (cardWidth + cardSpacing);
                
                // Check if mouse is over this card
                if (mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + cardHeight) {
                    if (selectedRiskContract != i) {
                        selectedRiskContract = i;
                        screenShakeIntensity = 1;
                    }
                    break;
                }
            }
        } else if (gameState == GameState.LEVEL_CONFIRM) {
            // Check if hovering over Yes/No buttons
            int buttonWidth = 150;
            int buttonHeight = 60;
            int buttonSpacing = 50;
            int totalWidth = 2 * buttonWidth + buttonSpacing;
            int startX = (WIDTH - totalWidth) / 2;
            int buttonY = HEIGHT / 2 + 50;
            
            // Check Yes button
            if (mouseX >= startX && mouseX <= startX + buttonWidth &&
                mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                if (selectedConfirmItem != 0) {
                    selectedConfirmItem = 0;
                    screenShakeIntensity = 1;
                }
            }
            // Check No button
            else if (mouseX >= startX + buttonWidth + buttonSpacing && 
                     mouseX <= startX + buttonWidth + buttonSpacing + buttonWidth &&
                     mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                if (selectedConfirmItem != 1) {
                    selectedConfirmItem = 1;
                    screenShakeIntensity = 1;
                }
            }
        } else if (gameState == GameState.WIN && showEquipPrompt && itemUnlockAnimation && itemUnlockTimer == 0) {
            // Check if hovering over equip prompt buttons
            for (int i = 0; i < equipButtons.length; i++) {
                if (equipButtons[i].contains(mouseX, mouseY)) {
                    if (selectedEquipButton != i) {
                        selectedEquipButton = i;
                        screenShakeIntensity = 1;
                    }
                    break;
                }
            }
        } else if (gameState == GameState.LEVEL_SELECT) {
            // Check if hovering over level nodes
            int centerY = HEIGHT / 2 - 40;
            int centerX = WIDTH / 2;
            int levelSpacing = WIDTH / 2;
            int centerNodeRadius = 80;
            double scrollDelta = levelSelectScrollAnimated - gameData.getSelectedLevelView();
            
            // Check nodes within range
            for (int i = -2; i <= 2; i++) {
                int level = gameData.getSelectedLevelView() + i;
                if (level < 1 || level > 20) continue;
                
                int baseX = centerX + i * levelSpacing;
                int x = (int)(baseX - scrollDelta * levelSpacing);
                
                // Calculate node radius based on distance
                double distFromCenter = Math.abs(x - centerX) / (double)levelSpacing;
                double scale = Math.max(0.4, 1.0 - distFromCenter * 0.5);
                int nodeRadius = (int)(centerNodeRadius * scale);
                
                // Check if mouse is over this node
                double dist = Math.sqrt(Math.pow(mouseX - x, 2) + Math.pow(mouseY - centerY, 2));
                if (dist <= nodeRadius) {
                    if (gameData.getSelectedLevelView() != level) {
                        int direction = level - gameData.getSelectedLevelView();
                        navigateLevelMap(direction);
                        screenShakeIntensity = 1;
                    }
                    break;
                }
            }
        }
    }
    
    private void handleMouseClick(java.awt.event.MouseEvent e) {
        if (e.getButton() != java.awt.event.MouseEvent.BUTTON1) return;
        
        if (gameState == GameState.SAVE_SELECT) {
            // Check if clicking on save slots
            int slotWidth = WIDTH * 2 / 3;
            int slotHeight = 200;
            int slotX = (WIDTH - slotWidth) / 2;
            int startY = 200;
            int slotSpacing = 230;
            int totalEntries = saveMetadataCache.size() + 1; // existing saves + "New Save"
            
            for (int i = 0; i < totalEntries; i++) {
                int slotY = startY + i * slotSpacing - (int)saveSelectScrollAnimated;
                
                // Skip if off-screen
                if (slotY + slotHeight < 0 || slotY > HEIGHT) continue;
                
                boolean isExistingSave = (i < saveMetadataCache.size());
                
                // Check if clicking on delete button for existing saves
                if (isExistingSave) {
                    int btnX = slotX + slotWidth - 120;
                    int btnY = slotY + 10;
                    int btnWidth = 100;
                    int btnHeight = 35;
                    
                    if (mouseX >= btnX && mouseX <= btnX + btnWidth &&
                        mouseY >= btnY && mouseY <= btnY + btnHeight) {
                        // Start delete hold for this slot
                        selectedSaveSlot = i;
                        deletingSlot = true;
                        deleteConfirmTimer = 0;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 2;
                        return;
                    }
                }
                
                // Check if clicking on save slot itself
                if (mouseX >= slotX && mouseX <= slotX + slotWidth &&
                    mouseY >= slotY && mouseY <= slotY + slotHeight) {
                    selectedSaveSlot = i;
                    
                    if (isExistingSave) {
                        // Load existing save
                        SaveManager.SaveMetadata meta = saveMetadataCache.get(i);
                        int slot = meta.slotNumber;
                        loadSaveSlot(slot, meta.saveName);
                    } else {
                        // "New Save" â€” go to mode selection
                        pendingSaveSlot = saveManager.getNextAvailableSlot();
                        selectedGameModeIndex = 1;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                        screenShakeIntensity = 5;
                        transitionToState(GameState.MODE_SELECT);
                    }
                    break;
                }
            }
        } else if (gameState == GameState.NAME_INPUT) {
            // Check if clicking on on-screen keyboard keys
            int kbStartY = UIScale.px(260);
            int keySize = UIScale.px(42);
            int keyGap = UIScale.px(6);
            
            for (int r = 0; r < ON_SCREEN_KB_ROWS.length; r++) {
                String row = ON_SCREEN_KB_ROWS[r];
                int rowWidth = row.length() * (keySize + keyGap) - keyGap;
                int rowX = (WIDTH - rowWidth) / 2;
                
                for (int c = 0; c < row.length(); c++) {
                    int kx = rowX + c * (keySize + keyGap);
                    int ky = kbStartY + r * (keySize + keyGap);
                    
                    if (mouseX >= kx && mouseX <= kx + keySize &&
                        mouseY >= ky && mouseY <= ky + keySize) {
                        onScreenKbRow = r;
                        onScreenKbCol = c;
                        pressOnScreenKey();
                        return;
                    }
                }
            }
        } else if (gameState == GameState.MODE_SELECT) {
            // Check if clicking on mode cards (use stored bounds from Renderer)
            java.awt.Rectangle[] modeBounds = renderer.getModeCardBounds();
            if (modeBounds != null) {
                for (int i = 0; i < modeBounds.length; i++) {
                    if (modeBounds[i] != null && modeBounds[i].contains(mouseX, mouseY)) {
                        selectedGameModeIndex = i;
                        // Simulate Enter to confirm selection
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, ' '));
                        break;
                    }
                }
            }
        } else if (gameState == GameState.INFO) {
            // Help & Tutorial screen button clicks
            if (renderer.helpShowcaseButton != null && renderer.helpShowcaseButton.contains(mouseX, mouseY)) {
                renderer.helpSelectedButton = 0;
                showcaseEnteredFrom = GameState.INFO;
                startDebugShowcase();
                soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                screenShakeIntensity = 5;
            } else if (renderer.helpTutorialButton != null && renderer.helpTutorialButton.contains(mouseX, mouseY)) {
                renderer.helpSelectedButton = 1;
                startTutorial();
                soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                screenShakeIntensity = 5;
            }
        } else if (gameState == GameState.MENU) {
            // Tutorial prompt button clicks
            if (showTutorialPrompt) {
                int panelW = config.UIScale.px(460);
                int panelH = config.UIScale.px(220);
                int panelX = (WIDTH - panelW) / 2;
                int panelY = (HEIGHT - panelH) / 2;
                int btnW = config.UIScale.px(120);
                int btnH = config.UIScale.px(45);
                int btnY = panelY + config.UIScale.px(145);
                int btnGap = config.UIScale.px(30);
                int yesX = panelX + (panelW / 2) - btnW - btnGap / 2;
                int noX = panelX + (panelW / 2) + btnGap / 2;
                if (mouseX >= yesX && mouseX <= yesX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    showTutorialPrompt = false;
                    tutorialPromptSelection = 0;
                    startTutorial();
                    screenShakeIntensity = 3;
                    return;
                } else if (mouseX >= noX && mouseX <= noX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    showTutorialPrompt = false;
                    tutorialPromptSelection = 1;
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                    screenShakeIntensity = 2;
                    return;
                }
                return; // Block clicks behind the prompt
            }
            UIButton[] buttons = renderer.getMenuButtons();
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].contains(mouseX, mouseY)) {
                    selectedMenuItem = i;
                    activateMenuItem(selectedMenuItem);
                    break;
                }
            }
        } else if (gameState == GameState.SETTINGS) {
            // Handle warning dialog clicks first
            if (showSettingsWarning) {
                java.awt.Rectangle[] warnBtns = renderer.getWarningButtonBounds();
                for (int i = 0; i < 3; i++) {
                    if (warnBtns[i] != null && warnBtns[i].contains(mouseX, mouseY)) {
                        settingsWarningSelection = i;
                        confirmWarningSelection();
                        return;
                    }
                }
                // Clicked outside buttons but dialog is open — do nothing (block clicks behind)
                return;
            }
            // Check if clicking on category tabs first (use UIScale to match Renderer positions)
            String[] categories = {"GRAPHICS", "AUDIO", "GAMEPLAY", "DEBUG", "CONTROLS", "HUD"};
            int tabWidth = UIScale.px(130);
            int tabStartX = (WIDTH - categories.length * tabWidth) / 2;
            int tabY = UIScale.px(130);
            int tabHeight = UIScale.px(40);
            
            boolean clickedTab = false;
            for (int i = 0; i < categories.length; i++) {
                int tabX = tabStartX + i * tabWidth;
                if (mouseX >= tabX && mouseX <= tabX + tabWidth - 10 &&
                    mouseY >= tabY && mouseY <= tabY + tabHeight) {
                    if (selectedSettingsCategory != i) {
                        selectedSettingsCategory = i;
                        settingsScroll = 0;
                        clampSettingsItem();
                        selectedSettingsItem = -1; // Select tabs when switching
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                        // Initialize HUD editor when switching to HUD tab
                        if (i == 5 && renderer != null) {
                            renderer.hudLayoutEditor.onOpen(hudLayout);
                        }
                    }
                    clickedTab = true;
                    break;
                }
            }
            
            // If didn't click tab, check settings items
            if (!clickedTab) {
                // HUD tab: delegate entirely to editor
                if (selectedSettingsCategory == 5) {
                    renderer.hudLayoutEditor.handleMousePressed(mouseX, mouseY, hudLayout);
                    return;
                }
                // Check for pill selector clicks (Graphics category)
                if (selectedSettingsCategory == 0) {
                    UIButton[] buttons = renderer.getSettingsButtons();
                    for (int i = 0; i < buttons.length; i++) {
                        if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                            int pillIdx = renderer.getPillClickIndex(i, mouseX, mouseY);
                            if (pillIdx >= 0) {
                                selectedSettingsItem = i;
                                setGraphicsPillValue(i, pillIdx);
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                                screenShakeIntensity = 2;
                                return;
                            }
                            // Check +/- button clicks
                            int btnClick = renderer.getSliderButtonClick(i, mouseX, mouseY);
                            if (btnClick != 0) {
                                selectedSettingsItem = i;
                                adjustSetting(i, btnClick);
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                                screenShakeIntensity = 1;
                                return;
                            }
                            break;
                        }
                    }
                }
                
                // Check for volume slider +/- button clicks (Audio category)
                if (selectedSettingsCategory == 1) {
                    UIButton[] buttons = renderer.getSettingsButtons();
                    for (int i = 1; i <= 4; i++) {
                        if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                            int btnClick = renderer.getSliderButtonClick(i, mouseX, mouseY);
                            if (btnClick != 0) {
                                selectedSettingsItem = i;
                                adjustSetting(i, btnClick);
                                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                                screenShakeIntensity = 1;
                                return;
                            }
                        }
                    }
                }
                
                UIButton[] buttons = renderer.getSettingsButtons();
                for (int i = 0; i < buttons.length; i++) {
                    if (selectedSettingsCategory == 4 && !isControlsItemVisible(i)) continue;
                    if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                        selectedSettingsItem = i;
                        toggleSetting(i);
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        screenShakeIntensity = 2;
                        break;
                    }
                }
            }
        } else if (gameState == GameState.PLAYING && isPaused) {
            UIButton[] buttons = renderer.getPauseButtons();
            int activeCount = renderer.getActivePauseButtonCount();
            for (int i = 0; i < activeCount; i++) {
                if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                    selectedPauseItem = i;
                    activatePauseMenuItem(selectedPauseItem);
                    break;
                }
            }
        } else if (gameState == GameState.SHOP) {
            // Block shop clicks while tutorial popup is showing
            if (tutorialMode && tutorialPopupActive) return;
            // Check if clicking on shop items
            UIButton[] buttons = renderer.getShopButtons();
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                    shopManager.setSelectedShopItem(i);
                    
                    // Perform the purchase/continue action (same as SPACE key)
                    if (i == 0) {
                        // Continue button
                        if (tutorialMode && !tutorialShopPurchased) {
                            // Must buy something first in tutorial
                            soundManager.playSound(SoundManager.Sound.PURCHASE_FAIL);
                            screenShakeIntensity = 2;
                        } else {
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            if (tutorialMode) {
                                advanceTutorialStep();
                                transitionToState(GameState.PLAYING);
                            } else {
                                transitionToState(GameState.LEVEL_SELECT);
                            }
                            screenShakeIntensity = 5;
                        }
                    } else {
                        // Try to purchase item
                        boolean purchased = shopManager.purchaseItem(i);
                        if (purchased) {
                            soundManager.playSound(SoundManager.Sound.PURCHASE_SUCCESS);
                            // Tutorial: track purchase for step 6
                            if (tutorialMode) tutorialShopPurchased = true;
                        } else {
                            soundManager.playSound(SoundManager.Sound.PURCHASE_FAIL);
                        }
                        screenShakeIntensity = purchased ? 10 : 3;
                    }
                    break;
                }
            }
        } else if (gameState == GameState.STATS) {
            // Stats screen mouse controls
            int cardWidth = 900;
            int cardHeight = 65;
            int cardSpacing = 10;
            int baseY = 180;
            int itemX = WIDTH / 2 - cardWidth / 2;
            int y = baseY - (int)statsScrollAnimated;
            int currentIndex = 0;
            
            // Check Active Item card (first card, taller)
            int activeItemHeight = cardHeight + 30; // 95
            y += 30; // section header offset
            if (mouseX >= itemX && mouseX <= itemX + cardWidth &&
                mouseY >= y && mouseY <= y + activeItemHeight) {
                selectedStatItem = 0;
                updateStatsScroll();
                
                // Left half = previous item, right half = next item
                if (hasSavedGame) {
                    soundManager.playSound(SoundManager.Sound.UI_ERROR);
                    screenShakeIntensity = 3;
                } else if (gameData.hasActiveItems()) {
                    if (mouseX < WIDTH / 2) {
                        gameData.equipPreviousItem();
                    } else {
                        gameData.equipNextItem();
                    }
                    // Sync display index to newly equipped item
                    if (renderer != null) {
                        renderer.setStatsActiveItemDisplayIndex(getEquippedItemDisplayIndex());
                    }
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                return;
            }
            y += activeItemHeight + cardSpacing + 50; // +50 for next section header
            currentIndex++;
            
            // Check visible Upgrade cards (only unlocked, excluding Extra Missiles)
            java.util.List<PassiveUpgrade> visibleClickUpgrades = getVisibleShopUpgrades();
            for (int i = 0; i < visibleClickUpgrades.size(); i++) {
                if (mouseX >= itemX && mouseX <= itemX + cardWidth &&
                    mouseY >= y && mouseY <= y + cardHeight) {
                    selectedStatItem = currentIndex;
                    updateStatsScroll();
                    
                    if (hasSavedGame) {
                        soundManager.playSound(SoundManager.Sound.UI_ERROR);
                        screenShakeIntensity = 3;
                    } else {
                        PassiveUpgrade upgrade = visibleClickUpgrades.get(i);
                        if (mouseX < WIDTH / 2) {
                            // Decrease active level
                            if (upgrade.getActiveLevel() > 0) {
                                upgrade.setActiveLevel(upgrade.getActiveLevel() - 1);
                                soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                                screenShakeIntensity = 2;
                            }
                        } else {
                            // Increase active level (up to purchased level)
                            if (upgrade.getActiveLevel() < upgrade.getCurrentLevel()) {
                                upgrade.setActiveLevel(upgrade.getActiveLevel() + 1);
                                soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                                screenShakeIntensity = 2;
                            }
                        }
                    }
                    return;
                }
                y += cardHeight + cardSpacing;
                currentIndex++;
            }
            
            // Extra Missiles card (read-only, just select it)
            y += 50; // section header offset
            if (passiveUpgradeManager != null && mouseX >= itemX && mouseX <= itemX + cardWidth &&
                mouseY >= y && mouseY <= y + cardHeight) {
                selectedStatItem = currentIndex;
                updateStatsScroll();
                soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                screenShakeIntensity = 1;
                return;
            }
        } else if (gameState == GameState.RISK_CONTRACT) {
            // Check if clicking on risk contract cards
            // Card dimensions must match drawRiskContract in Renderer
            int cardWidth = 280;
            int cardHeight = 380;
            int cardSpacing = 40;
            int totalWidth = RISK_CONTRACT_NAMES.length * cardWidth + (RISK_CONTRACT_NAMES.length - 1) * cardSpacing;
            int startX = (WIDTH - totalWidth) / 2;
            int cardY = (HEIGHT - cardHeight) / 2 - 40;
            
            for (int i = 0; i < RISK_CONTRACT_NAMES.length; i++) {
                int cardX = startX + i * (cardWidth + cardSpacing);
                
                // Check if mouse is over this card
                if (mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + cardHeight) {
                    selectedRiskContract = i;
                    
                    // Confirm selection (same as SPACE key)
                    confirmRiskContract();
                    screenShakeIntensity = 5;
                    break;
                }
            }
        } else if (gameState == GameState.LEVEL_CONFIRM) {
            // Check if clicking on Yes/No buttons
            int buttonWidth = 150;
            int buttonHeight = 60;
            int buttonSpacing = 50;
            int totalWidth = 2 * buttonWidth + buttonSpacing;
            int startX = (WIDTH - totalWidth) / 2;
            int buttonY = HEIGHT / 2 + 50;
            
            // Check Yes button
            if (mouseX >= startX && mouseX <= startX + buttonWidth &&
                mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                selectedConfirmItem = 0;
                confirmLevelStart();
                screenShakeIntensity = 3;
            }
            // Check No button
            else if (mouseX >= startX + buttonWidth + buttonSpacing && 
                     mouseX <= startX + buttonWidth + buttonSpacing + buttonWidth &&
                     mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                selectedConfirmItem = 1;
                confirmLevelStart();
                screenShakeIntensity = 3;
            }
        } else if (gameState == GameState.WIN && showEquipPrompt && itemUnlockAnimation && itemUnlockTimer == 0) {
            // Check if clicking on equip prompt buttons
            for (int i = 0; i < equipButtons.length; i++) {
                if (equipButtons[i].contains(mouseX, mouseY)) {
                    selectedEquipButton = i;
                    if (i == 0) {
                        // Yes - Equip the new item
                        gameData.equipItem(newItemIndex);
                        soundManager.playSound(SoundManager.Sound.ITEM_PICKUP);
                    } else {
                        // No - Don't equip
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    }
                    showEquipPrompt = false;
                    itemUnlockDismissing = true;
                    itemUnlockDismissTimer = ITEM_DISMISS_DURATION;
                    screenShakeIntensity = 3;
                    break;
                }
            }
        } else if (gameState == GameState.LEVEL_SELECT) {
            // Check if clicking on level nodes
            int centerY = (int)(HEIGHT * 0.67); // Must match Renderer.drawLevelSelect
            int centerX = WIDTH / 2;
            int levelSpacing = WIDTH / 2;
            int centerNodeRadius = 80;
            double scrollDelta = levelSelectScrollAnimated - gameData.getSelectedLevelView();
            
            // Check nodes within range
            for (int i = -2; i <= 2; i++) {
                int level = gameData.getSelectedLevelView() + i;
                if (level < 1 || level > 28) continue;
                
                int baseX = centerX + i * levelSpacing;
                int x = (int)(baseX - scrollDelta * levelSpacing);
                
                // Calculate node radius based on distance
                double distFromCenter = Math.abs(x - centerX) / (double)levelSpacing;
                double scale = Math.max(0.4, 1.0 - distFromCenter * 0.5);
                int nodeRadius = (int)(centerNodeRadius * scale);
                
                // Check if mouse is over this node
                double dist = Math.sqrt(Math.pow(mouseX - x, 2) + Math.pow(mouseY - centerY, 2));
                if (dist <= nodeRadius) {
                    // Navigate to this level first
                    if (gameData.getSelectedLevelView() != level) {
                        int direction = level - gameData.getSelectedLevelView();
                        navigateLevelMap(direction);
                    }
                    
                    // If clicking the centered node, try to start that level
                    if (distFromCenter < 0.3) {
                        // Check if there's a saved game to resume
                        if (hasSavedGame && level == savedLevel) {
                            // Show confirmation dialog for resume
                            selectedLevelToStart = savedLevel;
                            selectedConfirmItem = 0;
                            isConfirmingResume = true;
                            transitionToState(GameState.LEVEL_CONFIRM);
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        } else {
                            tryStartLevel();
                        }
                    }
                    screenShakeIntensity = 2;
                    break;
                }
            }
        } else if (gameState == GameState.ATTACK_SHOWCASE) {
            // Showcase selection screen button clicks - Carousel style
            int centerX = WIDTH / 2;
            int centerY = HEIGHT / 2 - 50; // Match drawing offset
            int cardSpacing = 600;
            int centerCardWidth = 620;
            int centerCardHeight = 540;
            
            // Tab button areas
            int tabWidth = 150;
            int tabHeight = 40;
            int tabY = 25;
            int attacksTabX = WIDTH / 2 - tabWidth - 10;
            int itemsTabX = WIDTH / 2 + 10;
            
            // Check attacks tab click
            if (mouseX >= attacksTabX && mouseX <= attacksTabX + tabWidth &&
                mouseY >= tabY && mouseY <= tabY + tabHeight && showcaseTab != 0) {
                showcaseTab = 0;
                debugShowcaseIndex = 0;
                updateShowcaseInfo();
                soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                screenShakeIntensity = 2;
            }
            // Check items tab click
            else if (mouseX >= itemsTabX && mouseX <= itemsTabX + tabWidth &&
                     mouseY >= tabY && mouseY <= tabY + tabHeight && showcaseTab != 1) {
                showcaseTab = 1;
                debugShowcaseIndex = 0;
                updateShowcaseInfo();
                soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                screenShakeIntensity = 2;
            }
            // Check carousel card clicks - check if clicking left, center, or right cards
            else {
                int maxIndex = (showcaseTab == 0) ? ATTACK_INTROS.length : ITEM_SHOWCASE.length;
                int cardY = centerY - centerCardHeight / 2;
                
                // Left cards area (previous items)
                int leftCardX = centerX - cardSpacing - (int)(centerCardWidth * 0.65) / 2;
                int leftCardW = (int)(centerCardWidth * 0.65);
                int leftCardH = (int)(centerCardHeight * 0.65);
                int leftCardY = centerY - leftCardH / 2;
                
                // Right cards area (next items)
                int rightCardX = centerX + cardSpacing - (int)(centerCardWidth * 0.65) / 2;
                int rightCardW = leftCardW;
                int rightCardH = leftCardH;
                int rightCardY = leftCardY;
                
                // Center card
                int cCardX = centerX - centerCardWidth / 2;
                int cCardY = centerY - centerCardHeight / 2;
                
                // Check left card click (go to previous - no loop)
                if (mouseX >= leftCardX && mouseX <= leftCardX + leftCardW &&
                    mouseY >= leftCardY && mouseY <= leftCardY + leftCardH) {
                    if (debugShowcaseIndex > 0) {
                        debugShowcaseIndex--;
                        showcaseCarouselOffset = -1.0; // Animate from left
                        updateShowcaseInfo();
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 2;
                    }
                }
                // Check right card click (go to next - no loop)
                else if (mouseX >= rightCardX && mouseX <= rightCardX + rightCardW &&
                         mouseY >= rightCardY && mouseY <= rightCardY + rightCardH) {
                    if (debugShowcaseIndex < maxIndex - 1) {
                        debugShowcaseIndex++;
                        showcaseCarouselOffset = 1.0; // Animate from right
                        updateShowcaseInfo();
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 2;
                    }
                }
                // Check center card click - only the image area starts the test (not the whole card)
                else if (mouseX >= cCardX && mouseX <= cCardX + centerCardWidth &&
                         mouseY >= cCardY && mouseY <= cCardY + centerCardHeight) {
                    // Only start if clicking in the middle portion of the card (image area)
                    int startBtnY = cCardY + centerCardHeight - 80;
                    int startBtnH = 50;
                    int startBtnW = 200;
                    int startBtnX = centerX - startBtnW / 2;
                    if (mouseX >= startBtnX && mouseX <= startBtnX + startBtnW &&
                        mouseY >= startBtnY && mouseY <= startBtnY + startBtnH) {
                        startShowcaseTest();
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        screenShakeIntensity = 5;
                    }
                }
                // Check left arrow box click
                else if (mouseX <= 95 && mouseY >= HEIGHT / 2 - 60 && mouseY <= HEIGHT / 2 + 60) {
                    if (debugShowcaseIndex > 0) {
                        debugShowcaseIndex--;
                        showcaseCarouselOffset = -1.0;
                        updateShowcaseInfo();
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 2;
                    }
                }
                // Check right arrow box click
                else if (mouseX >= WIDTH - 95 && mouseY >= HEIGHT / 2 - 60 && mouseY <= HEIGHT / 2 + 60) {
                    if (debugShowcaseIndex < maxIndex - 1) {
                        debugShowcaseIndex++;
                        showcaseCarouselOffset = 1.0;
                        updateShowcaseInfo();
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 2;
                    }
                }
            }
        }
    }
    
    private void handleMouseWheel(java.awt.event.MouseWheelEvent e) {
        int rotation = e.getWheelRotation(); // Positive = scroll down, Negative = scroll up
        
        switch (gameState) {
            case SAVE_SELECT:
                int totalSaveEntries = saveMetadataCache.size() + 1;
                int maxSaveScroll = Math.max(0, totalSaveEntries * 180 + 200 - HEIGHT + 60);
                if (rotation > 0) {
                    saveSelectScroll = Math.min(maxSaveScroll, saveSelectScroll + 120);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (rotation < 0) {
                    saveSelectScroll = Math.max(0, saveSelectScroll - 120);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                break;
                
            case SHOP:
                if (rotation > 0) {
                    // Scroll down - select next item
                    shopManager.selectNext();
                    updateShopScroll();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (rotation < 0) {
                    // Scroll up - select previous item
                    shopManager.selectPrevious();
                    updateShopScroll();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                break;
                
            case ACHIEVEMENTS:
                if (rotation > 0) {
                    // Scroll down
                    int totalAchievements = achievementManager.getAllAchievements().size();
                    int rows = (int)Math.ceil(totalAchievements / 3.0);
                    int maxScroll = Math.max(0, (rows * 115) - 600);
                    achievementsScroll = Math.min(maxScroll, achievementsScroll + 100);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (rotation < 0) {
                    // Scroll up
                    achievementsScroll = Math.max(0, achievementsScroll - 100);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                break;
                
            case STATS:
                int maxStatItemsScroll = 1 + getVisibleShopUpgrades().size() + 1;
                if (rotation > 0) {
                    // Scroll down - select next item
                    selectedStatItem = Math.min(maxStatItemsScroll - 1, selectedStatItem + 1);
                    updateStatsScroll();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (rotation < 0) {
                    // Scroll up - select previous item
                    selectedStatItem = Math.max(0, selectedStatItem - 1);
                    updateStatsScroll();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                break;
                
            case SETTINGS:
                int maxSettingsItems = getMaxSettingsItems();
                // Calculate max scroll based on content height
                int settingsTotalItems = maxSettingsItems + 1;
                int settingsHeaderOffset = 0;
                if (selectedSettingsCategory == 0) {
                    int[] hdrIdx = {0, 4, 8, 11, 15};
                    for (int h : hdrIdx) {
                        if (maxSettingsItems >= h) settingsHeaderOffset += 24;
                    }
                }
                // Controls tab: count only visible items
                if (selectedSettingsCategory == 4) {
                    int visibleCount = 0;
                    for (int ci = 0; ci <= maxSettingsItems; ci++) {
                        if (isControlsItemVisible(ci)) visibleCount++;
                    }
                    settingsTotalItems = visibleCount;
                }
                int settingsContentHeight = 200 + settingsTotalItems * 78 + settingsHeaderOffset;
                int maxSettingsScroll = Math.max(0, settingsContentHeight - HEIGHT + 250);
                if (rotation > 0) {
                    // Scroll down - scroll by 3 items for faster scrolling, clamped to max
                    settingsScroll = Math.min(maxSettingsScroll, settingsScroll + 360);
                    scrollCooldown = 20; // 1/3 second cooldown
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                } else if (rotation < 0) {
                    // Scroll up - scroll by 3 items for faster scrolling
                    settingsScroll = Math.max(0, settingsScroll - 360);
                    scrollCooldown = 20; // 1/3 second cooldown
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                }
                break;
        }
    }
    
    private void activateMenuItem(int index) {
        soundManager.playSound(SoundManager.Sound.MENU_OPEN);
        screenShakeIntensity = 5;
        // New order: Select Level, Shop, Stats, Achievements, Leaderboard, Game Info, Settings, Save Files
        switch (index) {
            case 0: transitionToState(GameState.LEVEL_SELECT); break;
            case 1: shopEnteredFrom = GameState.MENU; transitionToState(GameState.SHOP); break;
            case 2: transitionToState(GameState.STATS); break;
            case 3: transitionToState(GameState.ACHIEVEMENTS); break;
            case 4: transitionToState(GameState.LEADERBOARD_VIEW); break;
            case 5: transitionToState(GameState.INFO); break;
            case 6: settingsEnteredFrom = GameState.MENU; snapshotSettings(); transitionToState(GameState.SETTINGS); break;
            case 7: if (!DEMO_MODE) transitionToState(GameState.SAVE_SELECT); break; // Save Files
        }
    }
    
    private void activatePauseMenuItem(int index) {
        if (renderer.isShowcasePauseMode()) {
            // Showcase pause menu: Settings, Restart, Main Menu, Back to Showcase
            switch (index) {
                case 0: // Settings
                    soundManager.playSound(SoundManager.Sound.MENU_OPEN);
                    settingsEnteredFrom = GameState.PLAYING;
                    snapshotSettings();
                    gameState = GameState.SETTINGS;
                    break;
                case 1: // Restart
                    isPaused = false;
                    resetShowcase();
                    break;
                case 2: // Main Menu
                    System.out.println("DEBUG SHOWCASE: Going to main menu from showcase pause");
                    isPaused = false;
                    gameData.setCurrentLevel(savedRealLevel);
                    gameData.restoreEquippedItem(savedEquippedItem);
                    debugShowcaseMode = false;
                    debugShowcaseInGameplay = false;
                    transitionToState(GameState.MENU);
                    selectedMenuItem = 0;
                    break;
                case 3: // Back to Showcase
                    isPaused = false;
                    // Restore the real level and equipped item when exiting showcase
                    gameData.setCurrentLevel(savedRealLevel);
                    gameData.restoreEquippedItem(savedEquippedItem);
                    debugShowcaseInGameplay = false;
                    transitionToState(GameState.ATTACK_SHOWCASE);
                    System.out.println("DEBUG SHOWCASE: Back to showcase selection - restored level to " + savedRealLevel);
                    break;
            }
        } else {
            // Normal pause menu: Resume, Settings, Main Menu
            switch (index) {
                case 0: // Resume
                    soundManager.playSound(SoundManager.Sound.UNPAUSE);
                    isPaused = false;
                    if (gameData.getCountdownMode() == 2) {
                        unpauseCountdownActive = true;
                        unpauseCountdownTimer = UNPAUSE_COUNTDOWN_DURATION;
                        System.out.println("DEBUG: Starting unpause countdown - timer: " + unpauseCountdownTimer);
                    }
                    break;
                case 1: // Settings
                    soundManager.playSound(SoundManager.Sound.MENU_OPEN);
                    settingsEnteredFrom = GameState.PLAYING;
                    snapshotSettings();
                    gameState = GameState.SETTINGS;
                    break;
                case 2: // Main Menu (or Quit Tutorial)
                    isPaused = false;
                    if (tutorialMode) {
                        System.out.println("DEBUG: Quitting tutorial from pause menu");
                        quitTutorial();
                    } else {
                        System.out.println("DEBUG: Going to main menu from pause - saving game state");
                        if (!debugShowcaseMode) {
                            saveGameState();
                        } else {
                            gameData.setCurrentLevel(savedRealLevel);
                            gameData.restoreEquippedItem(savedEquippedItem);
                            debugShowcaseMode = false;
                            debugShowcaseInGameplay = false;
                            System.out.println("DEBUG SHOWCASE: Exited via pause menu - restored level to " + savedRealLevel);
                        }
                        transitionToState(GameState.MENU);
                        selectedMenuItem = 0;
                    }
                    break;
            }
        }
    }
    
    // Auto-equip the active item at the given display index if it's unlocked
    private static final ActiveItem.ItemType[] STATS_ITEM_ORDER = {
        ActiveItem.ItemType.LUCKY_CHARM, ActiveItem.ItemType.SHIELD, ActiveItem.ItemType.BOMBS,
        ActiveItem.ItemType.STUN, ActiveItem.ItemType.IMPULSE, ActiveItem.ItemType.TIME_SLOW,
        ActiveItem.ItemType.TYPE_PURGE, ActiveItem.ItemType.DASH, ActiveItem.ItemType.FROST_BEAM
    };
    
    private void autoEquipStatsItem(int displayIndex) {
        if (displayIndex >= 0 && displayIndex < STATS_ITEM_ORDER.length) {
            ActiveItem.ItemType itemType = STATS_ITEM_ORDER[displayIndex];
            if (gameData.getUnlockedItems().contains(itemType)) {
                gameData.equipItemByType(itemType);
            }
        }
    }
    
    // Get the stats display index for the currently equipped item type
    private int getEquippedItemDisplayIndex() {
        ActiveItem equipped = gameData.getEquippedItem();
        if (equipped != null) {
            for (int i = 0; i < STATS_ITEM_ORDER.length; i++) {
                if (STATS_ITEM_ORDER[i] == equipped.getType()) {
                    return i;
                }
            }
        }
        return 0;
    }
    
    /** Get the list of shop upgrades visible in the loadout screen (unlocked, excluding Extra Missiles). */
    private java.util.List<PassiveUpgrade> getVisibleShopUpgrades() {
        if (passiveUpgradeManager == null) return java.util.Collections.emptyList();
        java.util.List<PassiveUpgrade> all = passiveUpgradeManager.getAllUpgrades();
        int bestLevel = gameData.getBestRunLevel();
        java.util.List<PassiveUpgrade> visible = new java.util.ArrayList<>();
        for (int i = 0; i < all.size() - 1; i++) { // Exclude last (Extra Missiles)
            PassiveUpgrade u = all.get(i);
            if (u.getUnlockLevel() == 0 || u.getUnlockLevel() <= bestLevel) {
                visible.add(u);
            }
        }
        return visible;
    }
    
    // Handle player death - check for missiles first
    private void handlePlayerDeath() {
        // Stop proximity hum on death
        soundManager.stopProximityHum();
        
        // In debug showcase mode, player is invincible
        if (debugShowcaseMode) {
            return; // Ignore death in showcase mode
        }
        
        // In tutorial mode, play death effects but auto-respawn without penalty
        if (tutorialMode) {
            tutorialPlayerDied = true;
            
            // Play death effects
            deathExplosionX = player.getX();
            deathExplosionY = player.getY();
            soundManager.playSound(SoundManager.Sound.PLAYER_DEATH, 0.7f);
            soundManager.playSound(SoundManager.Sound.EXPL_MEDIUM_1, 0.9f);
            screenShakeIntensity = 20;
            slowMotionFactor = 0.15;
            slowMotionTimer = 45;
            screenFlashTimer = 10;
            deathFlashTimer = 20;
            
            // Clear bullets to give breathing room
            bullets.clear();
            beamAttacks.clear();
            
            // Start death sequence (will auto-respawn)
            deathSequenceActive = true;
            deathCameraHoldTimer = DEATH_CAMERA_HOLD_FRAMES;
            cameraPanBackTimer = 0;
            playerHidden = true;
            
            // Give boss immunity
            invulnerabilityTimer = 300;
            bossVulnerable = false;
            
            // Show tip
            if (comboSystem != null) {
                comboSystem.setAnnouncement("YOU RESPAWNED!", WIDTH / 2.0, HEIGHT / 2.0);
            }
            
            // Don't deduct missiles or track stats
            return;
        }
        
        // Deactivate any active item effects on death
        ActiveItem equippedOnDeath = gameData.getEquippedItem();
        if (equippedOnDeath != null && equippedOnDeath.isActive()) {
            equippedOnDeath.setActive(false);
            
            // Stop lingering SFX for specific items
            if (equippedOnDeath.getType() == ActiveItem.ItemType.STUN) {
                soundManager.stopSound(SoundManager.Sound.ELECTRIC_ZAP);
            }
        }
        
        // Reset frost beam state immediately (no retraction animation on death)
        frostBeamExtending = false;
        frostBeamRetracting = false;
        frostBeamProgress = 0;
        frostBeamRetractPhase = 0;
        frostBeamShakeTriggered = false;
        frostBeamStopDistance = -1;
        
        // Reset boss stun (item-driven effect)
        bossStunned = false;
        bossStunShakeOffset = 0;
        
        // Reset shield (item-driven effect)
        shieldActive = false;
        shieldHits = 0;
        
        // No missiles left - immediate game over (shouldn't normally reach here)
        if (gameData.getMissiles() <= 0) {
            soundManager.playSound(SoundManager.Sound.PLAYER_DEATH);
            soundManager.playSound(SoundManager.Sound.GAME_OVER, 0.6f);
            soundManager.stopMusic();
            screenShakeIntensity = 10;
            tookDamageThisBoss = true;
            hasSavedGame = false;
            gameState = GameState.GAME_OVER;
            if (renderer != null) renderer.setScreenEnteredTime(gradientTime);
            performAutoSave();
            return;
        }
        
        // Determine if the missile being consumed is an extra (purchased) one
        boolean wasExtraMissile = gameData.getMissiles() > gameData.getBaseMissiles();
        
        // Consume one missile
        gameData.useMissile();
        
        // Track missiles used stat
        gameData.getCurrentLevelStats().incrementMissilesUsed();
        missilesUsedThisRun++;
        
        // Record death position for camera hold
        deathExplosionX = player.getX();
        deathExplosionY = player.getY();
        
        // Create massive death explosion (plays for ALL deaths including final)
        if (enableParticles) {
            // DEBRIS fragments - spinning missile body fragments (55 pieces)
            for (int i = 0; i < 55; i++) {
                double angle = Math.random() * Math.PI * 2;
                double speed = 1.5 + Math.random() * 5.0;
                Color debrisColor = Math.random() < 0.5 
                    ? METAL_DEBRIS   // Dark metal
                    : PLAYER_DEATH_RED; // Red body
                addParticle(
                    deathExplosionX, deathExplosionY,
                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                    debrisColor, 80, 6 + Math.random() * 10,
                    Particle.ParticleType.DEBRIS
                );
            }
            
            // EXHAUST particles - fireball bloom (75 pieces)
            for (int i = 0; i < 75; i++) {
                double angle = Math.random() * Math.PI * 2;
                double speed = 2 + Math.random() * 8;
                Color fireColor;
                double r = Math.random();
                if (r < 0.25) fireColor = new Color(255, 255, 230); // White-hot core
                else if (r < 0.5) fireColor = new Color(255, 220, 80); // Bright yellow
                else if (r < 0.75) fireColor = new Color(255, 150, 40); // Orange
                else fireColor = new Color(255, 80, 20); // Deep orange-red
                addParticle(
                    deathExplosionX, deathExplosionY,
                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                    fireColor, 55, 6 + Math.random() * 4,
                    Particle.ParticleType.EXHAUST
                );
            }
            
            // EXPLOSION rings - 10 expanding rings (white-hot center to deep red)
            for (int i = 0; i < 10; i++) {
                addParticle(
                    deathExplosionX, deathExplosionY, 0, 0,
                    new Color(255, Math.max(0, 250 - i * 28), Math.max(0, 140 - i * 14), Math.max(60, 250 - i * 22)),
                    40 + i * 8, 25 + i * 30,
                    Particle.ParticleType.EXPLOSION
                );
            }
            
            // SPARK streaks - fast radiating sparks (60 pieces)
            for (int i = 0; i < 60; i++) {
                double angle = Math.random() * Math.PI * 2;
                double speed = 6 + Math.random() * 10;
                Color sparkColor;
                double r = Math.random();
                if (r < 0.4) sparkColor = new Color(255, 255, 210); // White-yellow
                else if (r < 0.7) sparkColor = new Color(255, 200, 100); // Gold
                else sparkColor = new Color(255, 130, 50); // Orange
                addParticle(
                    deathExplosionX, deathExplosionY,
                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                    sparkColor, 22, 2 + Math.random() * 3,
                    Particle.ParticleType.SPARK
                );
            }
            
            // SMOKE particles - lingering dark smoke (25 pieces)
            for (int i = 0; i < 25; i++) {
                double angle = Math.random() * Math.PI * 2;
                double speed = 0.3 + Math.random() * 2.0;
                int gray = 80 + (int)(Math.random() * 50);
                addParticle(
                    deathExplosionX, deathExplosionY,
                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                    new Color(gray, gray, gray + 15, 210), 
                    75, 14 + Math.random() * 8,
                    Particle.ParticleType.SMOKE
                );
            }
            
            // EMBERS - slow drifting fire particles for lingering effect (30 pieces)
            for (int i = 0; i < 30; i++) {
                double angle = Math.random() * Math.PI * 2;
                double speed = 0.5 + Math.random() * 1.5;
                Color emberColor = Math.random() < 0.5
                    ? new Color(255, 160, 40, 200)   // Orange ember
                    : new Color(255, 100, 20, 180);   // Red ember
                addParticle(
                    deathExplosionX + (Math.random() - 0.5) * 30,
                    deathExplosionY + (Math.random() - 0.5) * 30,
                    Math.cos(angle) * speed, Math.sin(angle) * speed - 0.5,
                    emberColor, 90, 3 + Math.random() * 3,
                    Particle.ParticleType.EXHAUST
                );
            }
        }
        
        // Play explosion sounds
        soundManager.playSound(SoundManager.Sound.PLAYER_DEATH, 0.7f);
        soundManager.playSound(SoundManager.Sound.EXPL_MEDIUM_1, 0.9f);
        
        // Enhanced screen effects (always play, even on final death)
        screenShakeIntensity = 30;
        slowMotionFactor = 0.10;
        slowMotionTimer = 60;
        screenFlashTimer = 14; // White flash
        deathFlashTimer = 28;  // Red vignette
        
        // Check if player still has missiles remaining
        if (gameData.getMissiles() > 0) {
            // Extra missiles clear all bullets; base missiles do NOT
            if (wasExtraMissile) {
                bullets.clear();
                beamAttacks.clear();
                flares.clear();
                flareCooldownTimer = 300;
            }
            
            // Give boss 5 seconds of immunity
            invulnerabilityTimer = 300;
            bossVulnerable = false;
            
            // Start death sequence
            deathSequenceActive = true;
            deathCameraHoldTimer = DEATH_CAMERA_HOLD_FRAMES;
            cameraPanBackTimer = 0;
            playerHidden = true;
            
            // Show missile used announcement with different text/color for base vs extra
            if (comboSystem != null) {
                if (gameData.getMissiles() == 1) {
                    comboSystem.setAnnouncement("LAST MISSILE!", WIDTH / 2.0, HEIGHT / 2.0);
                } else if (wasExtraMissile) {
                    comboSystem.setAnnouncement("EXTRA MISSILE!", WIDTH / 2.0, HEIGHT / 2.0);
                } else {
                    comboSystem.setAnnouncement("MISSILE USED!", WIDTH / 2.0, HEIGHT / 2.0);
                }
            }
            
            // Floating "-1" damage number so player clearly sees missile loss (fancy announcement style)
            damageNumbers.add(new DamageNumber("-1", deathExplosionX, deathExplosionY - 30,
                new Color(255, 60, 60), 56, true));
            
            // Don't end the game - continue playing
            return;
        }
        
        // That was the last missile - game over
        soundManager.playSound(SoundManager.Sound.GAME_OVER, 0.6f);
        soundManager.stopMusic();
        tookDamageThisBoss = true;
        hasSavedGame = false;
        gameState = GameState.GAME_OVER;
        if (renderer != null) renderer.setScreenEnteredTime(gradientTime);
        
        // Auto-save on game over
        performAutoSave();
    }
    
    private void saveGameState() {
        if (tutorialMode) return; // Don't save during tutorial
        // Save current game state for resume feature
        hasSavedGame = true;
        savedLevel = gameData.getCurrentLevel();
        
        System.out.println("DEBUG SAVE STATE: Saving game state - Level: " + savedLevel + ", hasSavedGame: " + hasSavedGame);
        System.out.println("DEBUG SAVE STATE: player=" + (player != null) + ", boss=" + (currentBoss != null));
        
        // Create serializable resume state for cross-session resume
        savedResumeState = new ResumeState();
        savedResumeState.isValid = true;
        savedResumeState.level = savedLevel;
        savedResumeState.survivalTime = gameData.getSurvivalTime();
        savedResumeState.score = gameData.getScore();
        savedResumeState.runMoney = gameData.getRunMoney();
        savedResumeState.capturePlayer(player);
        savedResumeState.captureBoss(currentBoss, bossHitCount);
        savedResumeState.captureBullets(bullets);
        savedResumeState.bossVulnerable = bossVulnerable;
        savedResumeState.vulnerabilityTimer = vulnerabilityTimer;
        savedResumeState.invulnerabilityTimer = invulnerabilityTimer;
        savedResumeState.tookDamageThisBoss = tookDamageThisBoss;
        savedResumeState.dodgeCombo = dodgeCombo;
        savedResumeState.shieldActive = shieldActive;
        savedResumeState.shieldHits = shieldHits;
        savedResumeState.comboTimer = comboTimer;
        savedResumeState.missiles = gameData.getMissiles();
        savedResumeState.baseMissiles = gameData.getBaseMissiles();
        savedResumeState.riskContractType = riskContractType;
        savedResumeState.riskContractActive = riskContractActive;
        savedResumeState.riskContractMultiplier = riskContractMultiplier;
        if (comboSystem != null) {
            savedResumeState.comboCount = comboSystem.getCombo();
            savedResumeState.comboMultiplier = (int)comboSystem.getMultiplier();
        }
        
        // Save references for in-session resume (shallow copy is fine for our use case)
        savedPlayer = player;
        savedBoss = currentBoss;
        savedBullets = new ArrayList<>(bullets);
        savedParticles = new ArrayList<>(particles);
        savedBeamAttacks = new ArrayList<>(beamAttacks);
        savedDamageNumbers = new ArrayList<>(damageNumbers);
        
        // Save game state variables
        savedLevel = gameData.getCurrentLevel();
        savedRiskContractType = riskContractType;
        savedRiskContractActive = riskContractActive;
        savedRiskContractMultiplier = riskContractMultiplier;
        savedSurvivalTime = gameData.getSurvivalTime();
        savedBossVulnerable = bossVulnerable;
        savedVulnerabilityTimer = vulnerabilityTimer;
        savedInvulnerabilityTimer = invulnerabilityTimer;
        savedBossHitCount = bossHitCount;
        savedTookDamageThisBoss = tookDamageThisBoss;
        savedDodgeCombo = dodgeCombo;
        savedShieldActive = shieldActive;
        savedShieldHits = shieldHits;
        savedComboTimer = comboTimer;
        savedBossIntroActive = bossIntroActive;
        savedBossIntroTimer = bossIntroTimer;
        savedBossIntroText = bossIntroText;
        savedWaitingForRespawn = waitingForRespawn;
        savedRespawnDelayTimer = respawnDelayTimer;
        savedRespawnInvincibilityTimer = respawnInvincibilityTimer;
        savedBossDeathAnimation = bossDeathAnimation;
        savedDeathAnimationTimer = deathAnimationTimer;
        savedBossDeathScale = bossDeathScale;
        savedBossDeathRotation = bossDeathRotation;
        savedStoppedMovingTimer = stoppedMovingTimer;
        savedGameTimeSeconds = gameTimeSeconds;
        savedResumeState.gameTimeSeconds = gameTimeSeconds;
        
        // Persist the resume state to disk
        performAutoSave();
    }
    
    private void restoreGameState() {
        if (!hasSavedGame) {
            return;
        }
        
        // Check if we have valid in-memory saved objects
        // If savedPlayer or savedBoss is null, try to restore from serialized ResumeState
        if (savedPlayer == null || savedBoss == null) {
            if (savedResumeState != null && savedResumeState.isValid) {
                System.out.println("DEBUG: Restoring from serialized ResumeState (cross-session resume)");
                restoreFromResumeState();
                return;
            }
            System.out.println("DEBUG: Saved game state invalid (no objects or ResumeState) - starting fresh");
            hasSavedGame = false;
            gameData.setCurrentLevel(savedLevel);
            startGame();
            return;
        }
        
        // Restore game state from in-memory objects (same session)
        gameState = GameState.PLAYING;
        isPaused = false;
        
        // Start countdown when resuming from menu if mode is 1 (Resume Only) or 2 (Always)
        if (gameData.getCountdownMode() >= 1) {
            unpauseCountdownActive = true;
            unpauseCountdownTimer = UNPAUSE_COUNTDOWN_DURATION;
            System.out.println("DEBUG: Starting resume countdown - timer: " + unpauseCountdownTimer);
        }
        
        // Restore saved objects (with null checks for safety)
        player = savedPlayer;
        currentBoss = savedBoss;
        setupBossFactory(currentBoss);
        bullets.clear();
        if (savedBullets != null) bullets.addAll(savedBullets);
        particles.clear();
        if (savedParticles != null) particles.addAll(savedParticles);
        beamAttacks.clear();
        if (savedBeamAttacks != null) beamAttacks.addAll(savedBeamAttacks);
        damageNumbers.clear();
        if (savedDamageNumbers != null) damageNumbers.addAll(savedDamageNumbers);
        flares.clear();
        flareCooldownTimer = 300;
        
        // Restore game state variables
        gameData.setCurrentLevel(savedLevel);
        riskContractType = savedRiskContractType;
        riskContractActive = savedRiskContractActive;
        riskContractMultiplier = savedRiskContractMultiplier;
        gameData.setSurvivalTime(savedSurvivalTime);
        bossVulnerable = savedBossVulnerable;
        vulnerabilityTimer = savedVulnerabilityTimer;
        invulnerabilityTimer = savedInvulnerabilityTimer;
        bossHitCount = savedBossHitCount;
        tookDamageThisBoss = savedTookDamageThisBoss;
        dodgeCombo = savedDodgeCombo;
        shieldActive = savedShieldActive;
        shieldHits = savedShieldHits;
        comboTimer = savedComboTimer;
        bossIntroActive = savedBossIntroActive;
        bossIntroTimer = savedBossIntroTimer;
        bossIntroText = savedBossIntroText;
        waitingForRespawn = savedWaitingForRespawn;
        respawnDelayTimer = savedRespawnDelayTimer;
        respawnInvincibilityTimer = savedRespawnInvincibilityTimer;
        bossDeathAnimation = savedBossDeathAnimation;
        deathAnimationTimer = savedDeathAnimationTimer;
        bossDeathScale = savedBossDeathScale;
        bossDeathRotation = savedBossDeathRotation;
        stoppedMovingTimer = savedStoppedMovingTimer;
        
        // Restore game timer by adjusting gameStartTime so wall-clock math continues correctly
        gameTimeSeconds = savedGameTimeSeconds;
        gameStartTime = System.currentTimeMillis() - (long)(gameTimeSeconds * 1000);
        
        // Clear saved game after restoring
        hasSavedGame = false;
        
        // Persist that we've resumed (so reload won't try to resume again)
        performAutoSave();
        
        // Start ambient background sound
        soundManager.startAmbientSound();
        
        // Resume music (fast crossfade for snappy level entry)
        soundManager.playMusicFast(getRandomBattleMusicPath());
    }
    
    /**
     * Restore game state from serialized ResumeState (cross-session resume)
     * Creates new game objects from saved primitive data
     */

    private void restoreFromResumeState() {
        ResumeState rs = savedResumeState;
        
        // Set level and game data
        gameData.setCurrentLevel(rs.level);
        gameData.setSurvivalTime(rs.survivalTime);
        gameData.setScore(rs.score);
        gameData.setRunMoney(rs.runMoney);
        gameData.setMissiles(rs.missiles);
        gameData.setBaseMissiles(rs.baseMissiles);
        
        // Create new player at saved position
        player = new Player(rs.playerX, rs.playerY, gameData.getActiveSpeedLevel(), keyBindManager, controllerManager);
        
        // Create new boss at saved position with saved state
        currentBoss = new Boss(rs.bossX, rs.bossY, rs.bossLevel, soundManager, gameData.getGameMode());
        setupBossFactory(currentBoss);
        currentBoss.setAllowedPatterns(getAllowedPatternsForLevel(rs.bossLevel));
        currentBoss.setPosition(rs.bossX, rs.bossY);
        currentBoss.setVelocity(rs.bossVX, rs.bossVY);
        currentBoss.setShootTimer(rs.bossShootTimer);
        currentBoss.setSpiralRotation(rs.bossSpiralRotation);
        
        // Restore bullets
        bullets.clear();
        bullets.addAll(rs.restoreBullets());
        
        // Clear particles and beams (they're transient visual effects)
        particles.clear();
        beamAttacks.clear();
        damageNumbers.clear();
        flares.clear();
        flareCooldownTimer = 300;
        
        // Restore game state
        bossHitCount = rs.bossHitCount;
        
        // Apply saved hits to boss's internal health so it matches the visual health bar
        int restoredHealth = currentBoss.getMaxHealth() - rs.bossHitCount;
        if (restoredHealth < currentBoss.getMaxHealth()) {
            currentBoss.setCurrentHealth(restoredHealth);
        }
        
        bossVulnerable = rs.bossVulnerable;
        vulnerabilityTimer = rs.vulnerabilityTimer;
        invulnerabilityTimer = rs.invulnerabilityTimer;
        tookDamageThisBoss = rs.tookDamageThisBoss;
        dodgeCombo = rs.dodgeCombo;
        shieldActive = rs.shieldActive;
        shieldHits = rs.shieldHits;
        comboTimer = rs.comboTimer;
        riskContractType = rs.riskContractType;
        riskContractActive = rs.riskContractActive;
        riskContractMultiplier = rs.riskContractMultiplier;
        
        // Restore game timer
        gameTimeSeconds = rs.gameTimeSeconds;
        gameStartTime = System.currentTimeMillis() - (long)(gameTimeSeconds * 1000);
        
        // Reset non-restored state
        bossIntroActive = false;
        waitingForRespawn = false;
        bossDeathAnimation = false;
        stoppedMovingTimer = 0;
        hasMovedOnce = false; // Reset Can't Stop contract so player must input movement before timer starts
        
        // Set game state to playing with countdown
        gameState = GameState.PLAYING;
        isPaused = false;
        
        if (gameData.getCountdownMode() >= 1) {
            unpauseCountdownActive = true;
            unpauseCountdownTimer = UNPAUSE_COUNTDOWN_DURATION;
            System.out.println("DEBUG: Starting cross-session resume countdown");
        }
        
        // Clear saved game after restoring
        hasSavedGame = false;
        savedResumeState = null;
        
        // Persist that we've resumed
        performAutoSave();
        
        // Start ambient background sound and music (fast crossfade for snappy level entry)
        soundManager.startAmbientSound();
        soundManager.playMusicFast(getRandomBattleMusicPath());
        
        System.out.println("DEBUG: Cross-session resume complete - Level " + rs.level + 
            ", Boss HP: " + (int)(rs.bossHealth * 100) + "%, Bullets: " + bullets.size());
    }
    
    /**
     * Auto-save the current game state to the active save slot
     */
    private void performAutoSave() {
        if (DEMO_MODE) return; // No saving in demo mode
        if (tutorialMode) return; // Don't auto-save during tutorial
        if (saveManager.getCurrentSaveSlot() == -1) {
            // No active save slot
            return;
        }
        
        try {
            // Preserve custom save name if one exists, otherwise use default
            String saveName = gameData.getCustomSaveName() != null ? 
                gameData.getCustomSaveName() : "Save " + saveManager.getCurrentSaveSlot();
            SaveData saveData = SaveData.fromGameData(gameData, achievementManager, 
                passiveUpgradeManager, saveName);
            
            // Include full resume state in save for cross-session resume
            saveData.setResumeState(hasSavedGame, savedLevel, savedResumeState);
            
            saveManager.autoSave(saveData);
            System.out.println("Auto-saved to slot " + saveManager.getCurrentSaveSlot() + 
                " (hasSavedGame=" + hasSavedGame + ", savedLevel=" + savedLevel + 
                ", hasResumeState=" + (savedResumeState != null && savedResumeState.isValid) + ")");
            
            // Show auto-save indicator
            showAutoSaveIndicator = true;
            autoSaveIndicatorTimer = AUTO_SAVE_INDICATOR_DURATION;
        } catch (Exception e) {
            System.err.println("Auto-save failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Refresh the save metadata cache from disk.
     */
    private void refreshSaveMetadata() {
        saveMetadataCache = saveManager.getAllSaveMetadata();
    }

    /**
     * Reset demo state to a fresh start (used by "Play Again" on the demo over screen).
     */
    private void resetDemoState() {
        setupDemoSave();
        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
        screenShakeIntensity = 5;
    }

    /**
     * Set up a fresh in-memory demo save (no disk writes). Called on launch and "Play Again".
     */
    private void setupDemoSave() {
        SaveData demoSave = new SaveData();
        demoSave.saveName = "Demo";
        demoSave.gameMode = GameMode.HARD;
        demoSave.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
        gameData.setCustomSaveName("Demo");
        hasSavedGame = false;
        savedLevel = 1;
        savedResumeState = null;
    }
    
    /**
     * Ensure the selected save slot is visible by adjusting scroll.
     */
    private void ensureSaveSlotVisible() {
        int startY = 200;
        int slotSpacing = 230;
        int slotHeight = 200;
        int slotTop = startY + selectedSaveSlot * slotSpacing;
        int slotBottom = slotTop + slotHeight;
        
        // Ensure slot is within visible area (with padding)
        int viewTop = saveSelectScroll;
        int viewBottom = saveSelectScroll + HEIGHT - 60; // Leave room for instructions at bottom
        
        if (slotTop < viewTop + 20) {
            saveSelectScroll = Math.max(0, slotTop - 20);
        } else if (slotBottom > viewBottom - 20) {
            saveSelectScroll = Math.max(0, slotBottom - HEIGHT + 80);
        }
    }
    
    /**
     * Called when the game window is closing - save the current state
     */
    public void saveOnExit() {
        if (DEMO_MODE) {
            System.out.println("Demo mode - skipping save on exit.");
            if (updateThreadPool != null) updateThreadPool.shutdownNow();
            return;
        }
        System.out.println("Game closing - performing auto-save...");
        performAutoSave();
        // Shut down thread pool cleanly
        if (updateThreadPool != null) {
            updateThreadPool.shutdownNow();
        }
    }

    /**
     * Start a demo boss intro cinematic from the menu (U key).
     * Sets up minimal player/boss for level 1 and plays the full cinematic.
     */
    private void startDemoIntro() {
        demoIntroActive = true;
        
        // Create temporary player and boss for the demo
        int speedLevel = getActiveSpeedLevel();
        player = new Player(WORLD_WIDTH / 2, WORLD_HEIGHT - 200, speedLevel, keyBindManager, controllerManager);
        currentBoss = new Boss(WORLD_WIDTH / 2, 100, 1, soundManager, gameData.getGameMode());
        setupBossFactory(currentBoss);
        bullets.clear();
        particles.clear();
        damageNumbers.clear();
        beamAttacks.clear();
        introParticles.clear();
        flares.clear();
        flareCooldownTimer = 300;
        
        // Set up intro pan with boss name banner
        bossIntroActive = false;
        bossIntroText = currentBoss.getVehicleName();
        introPanActive = true;
        introPanTimer = 0;
        bossEntranceY = -200;
        cameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
        cameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
        isPaused = false;
        
        // Switch to PLAYING so the update loop and renderer handle it
        gameState = GameState.PLAYING;
        soundManager.playSound(SoundManager.Sound.BOSS_INTRO);
        screenShakeIntensity = 8;
        System.out.println("DEBUG: Demo intro started for level 1");
    }

    /**
     * Start the debug attack showcase mode for taking screenshots.
     * Shows a selection screen where user can browse attacks and start tests.
     */
    private void startDebugShowcase() {
        // Save the real game level and equipped item before entering showcase
        savedRealLevel = gameData.getCurrentLevel();
        savedEquippedItem = gameData.getEquippedItem(); // Save current equipped item
        
        debugShowcaseMode = true;
        debugShowcaseIndex = 0;
        debugShowcaseTimer = 0;
        debugShowcaseInGameplay = false;
        showcaseHoveredButton = -1;
        showcaseTab = 0; // Start on Attacks tab
        
        // Update the current showcase display
        updateShowcaseInfo();
        
        // Go to showcase selection screen
        transitionToState(GameState.ATTACK_SHOWCASE);
        
        System.out.println("DEBUG SHOWCASE: Started - Use TAB to switch tabs, A/D to browse, SPACE to test, ESC to exit");
    }
    
    /**
     * Start tutorial mode — a guided practice level with pop-up instructions.
     * No progress is saved. Player gets temporary money and auto-equipped Shield.
     */
    private void startTutorial() {
        // Save current state to restore after tutorial
        tutorialSavedMoney = gameData.getTotalMoney();
        tutorialSavedMissiles = gameData.getMissiles();
        tutorialSavedLevel = gameData.getCurrentLevel();
        tutorialSavedItem = gameData.getEquippedItem();
        
        // Set up tutorial state
        tutorialMode = true;
        tutorialStep = 0;
        tutorialStepCompleted = false;
        tutorialPlayerMoveDistance = 0;
        tutorialGrazeCount = 0;
        tutorialPlayerDied = false;
        tutorialItemUsed = false;
        tutorialShieldBlockCount = 0;
        tutorialShopPurchased = false;
        tutorialShopVisited = false;
        tutorialSlowdownPhase = 0;
        tutorialSlowdownTimer = 0;
        tutorialTaskText = "";
        tutorialTaskProgress = 0;
        tutorialTaskHasBar = false;
        
        // Give tutorial resources
        gameData.setTotalMoney(500); // Temporary $500
        gameData.setCurrentLevel(1); // Level 1 boss (easiest)
        
        // Save and zero out all passive upgrade levels for clean tutorial
        java.util.List<PassiveUpgrade> allUpgrades = passiveUpgradeManager.getAllUpgrades();
        tutorialSavedUpgradeLevels = new int[allUpgrades.size()];
        tutorialSavedActiveUpgradeLevels = new int[allUpgrades.size()];
        for (int i = 0; i < allUpgrades.size(); i++) {
            tutorialSavedUpgradeLevels[i] = allUpgrades.get(i).getCurrentLevel();
            tutorialSavedActiveUpgradeLevels[i] = allUpgrades.get(i).getActiveLevel();
            allUpgrades.get(i).setCurrentLevel(0);
            allUpgrades.get(i).setActiveLevel(0);
        }
        shopManager.hideUpgrades = false;
        // Save and set bestRunLevel to 0 so only base upgrades (unlockLevel=0) show in tutorial shop
        tutorialSavedBestRunLevel = gameData.getBestRunLevel();
        gameData.setBestRunLevel(0);
        shopManager.rebuildSortedOrder();
        
        // No item equipped at start — Shield introduced at step 4
        
        // Start the level using normal startGame
        startGame();
        
        // Skip the boss intro animation for tutorial
        introPanActive = false;
        bossIntroActive = false;
        if (currentBoss != null) {
            currentBoss.setPosition(currentBoss.getX(), 100); // Place boss at normal position immediately
            bossEntranceY = 100;
        }
        
        // Sync tutorial mode to renderer
        renderer.tutorialMode = true;
        renderer.tutorialStep = 0;
        
        // Override after startGame: give 99 missiles (effectively infinite lives)
        gameData.setMissiles(5);
        gameData.setBaseMissiles(5);
        
        // Track initial player position for movement distance
        if (player != null) {
            tutorialPrevPlayerX = player.getX();
            tutorialPrevPlayerY = player.getY();
        }
        
        // Show first tutorial popup immediately (no slowing-in for step 0)
        showTutorialPopup(0);
        
        System.out.println("TUTORIAL: Started - Step 0 (Welcome)");
    }
    
    /**
     * Show a tutorial popup for the given step.
     */
    private void showTutorialPopup(int step) {
        if (step >= 0 && step < TUTORIAL_STEPS.length) {
            tutorialPopupActive = true;
            renderer.tutorialPopupActive = true;
            tutorialPopupInputDelay = 60; // 1 second at 60fps before dismiss allowed
            tutorialPopupTitle = TUTORIAL_STEPS[step][0];
            // Body is everything after the title
            tutorialPopupBody = new String[TUTORIAL_STEPS[step].length - 1];
            System.arraycopy(TUTORIAL_STEPS[step], 1, tutorialPopupBody, 0, tutorialPopupBody.length);
        }
    }
    
    /**
     * Advance to the next tutorial step.
     */
    private void advanceTutorialStep() {
        tutorialStep++;
        tutorialStepCompleted = false;
        renderer.tutorialStep = tutorialStep;
        
        if (tutorialStep >= TUTORIAL_STEPS.length) {
            // Show completion screen instead of immediately completing
            tutorialCompleteScreen = true;
            tutorialCompleteSelection = 0;
            // Unlock achievement and mark complete now
            achievementManager.incrementProgress(Achievement.AchievementType.TUTORIAL_COMPLETE, 1);
            gameData.setTutorialCompleted(true);
            performAutoSave();
            return;
        }
        
        // Equip Shield when reaching the Active Items step
        if (tutorialStep == 4) {
            gameData.equipItemByType(ActiveItem.ItemType.SHIELD);
            tutorialShieldBlockCount = 0;
        }
        
        // Set boss to 1 HP when reaching the Defeat Boss step
        if (tutorialStep == 5 && currentBoss != null) {
            currentBoss.setMaxHealth(1);
            currentBoss.setCurrentHealth(1);
        }
        
        // Trigger HUD highlight effect when new UI elements are introduced
        if (tutorialStep == 3 || tutorialStep == 4 || tutorialStep == 5) {
            renderer.tutorialHighlightElement = tutorialStep;
            renderer.tutorialHighlightTimer = 180; // 3 seconds at 60fps
        }
        
        // Show popup for next step (except step 6 - Shop popup is shown after SHOP transition)
        if (tutorialStep != 6) {
            showTutorialPopup(tutorialStep);
        }
        System.out.println("TUTORIAL: Advanced to step " + tutorialStep + " (" + TUTORIAL_STEPS[tutorialStep][0] + ")");
    }
    
    /**
     * Complete the tutorial — unlock achievement, restore state, return to menu.
     */
    private void completeTutorial() {
        // Restore original state
        gameData.setTotalMoney(tutorialSavedMoney);
        gameData.setMissiles(tutorialSavedMissiles);
        gameData.setBaseMissiles(Math.min(tutorialSavedMissiles, 5));
        gameData.setCurrentLevel(tutorialSavedLevel);
        gameData.restoreEquippedItem(tutorialSavedItem);
        
        // Restore passive upgrade levels
        if (tutorialSavedUpgradeLevels != null) {
            java.util.List<PassiveUpgrade> allUpgrades = passiveUpgradeManager.getAllUpgrades();
            for (int i = 0; i < Math.min(tutorialSavedUpgradeLevels.length, allUpgrades.size()); i++) {
                allUpgrades.get(i).setCurrentLevel(tutorialSavedUpgradeLevels[i]);
                allUpgrades.get(i).setActiveLevel(tutorialSavedActiveUpgradeLevels[i]);
            }
            tutorialSavedUpgradeLevels = null;
            tutorialSavedActiveUpgradeLevels = null;
        }
        gameData.setBestRunLevel(tutorialSavedBestRunLevel);
        shopManager.rebuildSortedOrder();
        
        // Reset tutorial state
        tutorialMode = false;
        tutorialPopupActive = false;
        tutorialCompleteScreen = false;
        renderer.tutorialPopupActive = false;
        tutorialSlowdownPhase = 0;
        slowMotionFactor = 1.0;
        slowMotionTimer = 0;
        renderer.tutorialMode = false;
        renderer.tutorialStep = 0;
        
        // Auto-save to persist achievement and tutorialCompleted flag
        performAutoSave();
        
        // Return to Help menu
        transitionToState(GameState.INFO);
        System.out.println("TUTORIAL: Completed! Achievement unlocked.");
    }
    
    /**
     * Quit the tutorial early without saving progress.
     */
    private void quitTutorial() {
        // Restore original state
        gameData.setTotalMoney(tutorialSavedMoney);
        gameData.setMissiles(tutorialSavedMissiles);
        gameData.setBaseMissiles(Math.min(tutorialSavedMissiles, 5));
        gameData.setCurrentLevel(tutorialSavedLevel);
        gameData.restoreEquippedItem(tutorialSavedItem);
        
        // Restore passive upgrade levels
        if (tutorialSavedUpgradeLevels != null) {
            java.util.List<PassiveUpgrade> allUpgrades = passiveUpgradeManager.getAllUpgrades();
            for (int i = 0; i < Math.min(tutorialSavedUpgradeLevels.length, allUpgrades.size()); i++) {
                allUpgrades.get(i).setCurrentLevel(tutorialSavedUpgradeLevels[i]);
                allUpgrades.get(i).setActiveLevel(tutorialSavedActiveUpgradeLevels[i]);
            }
            tutorialSavedUpgradeLevels = null;
            tutorialSavedActiveUpgradeLevels = null;
        }
        gameData.setBestRunLevel(tutorialSavedBestRunLevel);
        shopManager.rebuildSortedOrder();
        
        // Reset tutorial state
        tutorialMode = false;
        tutorialPopupActive = false;
        renderer.tutorialPopupActive = false;
        tutorialSlowdownPhase = 0;
        slowMotionFactor = 1.0;
        slowMotionTimer = 0;
        renderer.tutorialMode = false;
        renderer.tutorialStep = 0;
        
        transitionToState(GameState.MENU);
        selectedMenuItem = 0;
        System.out.println("TUTORIAL: Quit early - state restored.");
    }
    
    /**
     * Check if the current tutorial step's completion condition is met.
     */
    private void checkTutorialStepProgress() {
        if (tutorialStepCompleted) return; // Already completed, waiting for slow-down
        
        boolean completed = false;
        
        switch (tutorialStep) {
            case 0: // Welcome — dismissed by pressing ENTER (handled in popup input)
                tutorialTaskText = "";
                tutorialTaskHasBar = false;
                break;
            case 1: // Movement — move 1000+ pixels total
                if (player != null) {
                    double dx = player.getX() - tutorialPrevPlayerX;
                    double dy = player.getY() - tutorialPrevPlayerY;
                    tutorialPlayerMoveDistance += Math.sqrt(dx * dx + dy * dy);
                    tutorialPrevPlayerX = player.getX();
                    tutorialPrevPlayerY = player.getY();
                }
                tutorialTaskText = "Move around! " + (int)Math.min(tutorialPlayerMoveDistance, TUTORIAL_MOVE_GOAL) + "/" + TUTORIAL_MOVE_GOAL + " px";
                tutorialTaskProgress = Math.min(1.0, tutorialPlayerMoveDistance / TUTORIAL_MOVE_GOAL);
                tutorialTaskHasBar = true;
                if (tutorialPlayerMoveDistance >= TUTORIAL_MOVE_GOAL) {
                    completed = true;
                }
                break;
            case 2: // Dodging — 5 grazes
                tutorialTaskText = "Graze bullets! " + Math.min(tutorialGrazeCount, TUTORIAL_GRAZE_GOAL) + "/" + TUTORIAL_GRAZE_GOAL;
                tutorialTaskProgress = Math.min(1.0, (double)tutorialGrazeCount / TUTORIAL_GRAZE_GOAL);
                tutorialTaskHasBar = true;
                if (tutorialGrazeCount >= TUTORIAL_GRAZE_GOAL) {
                    completed = true;
                }
                break;
            case 3: // Death & Respawn — get hit once
                tutorialTaskText = "Get hit by a bullet to see respawning!";
                tutorialTaskHasBar = false;
                if (tutorialPlayerDied) {
                    completed = true;
                }
                break;
            case 4: // Active Items — block 3 bullets with Shield
                tutorialTaskText = "Block bullets with Shield! " + Math.min(tutorialShieldBlockCount, TUTORIAL_SHIELD_GOAL) + "/" + TUTORIAL_SHIELD_GOAL;
                tutorialTaskProgress = Math.min(1.0, (double)tutorialShieldBlockCount / TUTORIAL_SHIELD_GOAL);
                tutorialTaskHasBar = true;
                if (tutorialShieldBlockCount >= TUTORIAL_SHIELD_GOAL) {
                    completed = true;
                }
                break;
            case 5: // Defeat the boss (1 HP)
                tutorialTaskText = "Attack when the shield disappears!";
                tutorialTaskHasBar = false;
                // Handled by boss death sequence
                break;
            case 6: // Shop — buy something
                tutorialTaskText = "Buy an upgrade from the Shop!";
                tutorialTaskHasBar = false;
                if (tutorialShopPurchased) {
                    completed = true;
                }
                break;
            case 7: // Tutorial complete — handled by popup dismiss
                tutorialTaskText = "";
                tutorialTaskHasBar = false;
                break;
        }
        
        if (completed) {
            tutorialStepCompleted = true;
            // Start cinematic slow-down
            tutorialSlowdownPhase = 1; // SLOWING_IN
            tutorialSlowdownTimer = 40;
            slowMotionFactor = 0.15;
            slowMotionTimer = 999; // Keep slow-mo active until we manually reset
            System.out.println("TUTORIAL: Step " + tutorialStep + " completed — slowing down...");
        }
    }
    
    /**
     * Update the attack info display for the current showcase index
     */
    private void updateShowcaseInfo() {
        if (showcaseTab == 0) {
            // ATTACKS TAB
            if (debugShowcaseIndex < 0) debugShowcaseIndex = ATTACK_INTROS.length - 1;
            if (debugShowcaseIndex >= ATTACK_INTROS.length) debugShowcaseIndex = 0;
            
            String[] intro = ATTACK_INTROS[debugShowcaseIndex];
            currentAttackIntroId = intro[0];
            int attackLevel = Integer.parseInt(intro[1]);
            currentAttackIntroName = intro[2];
            currentAttackIntroDescription = intro[3];
            currentAttackIntroCategory = intro[4]; // Category type
            attackIntroImage = loadAttackIntroImage(currentAttackIntroId);
            
            // Set the level to match this attack (changes boss sprite and background)
            gameData.setCurrentLevel(attackLevel);
        } else {
            // ITEMS TAB
            if (debugShowcaseIndex < 0) debugShowcaseIndex = ITEM_SHOWCASE.length - 1;
            if (debugShowcaseIndex >= ITEM_SHOWCASE.length) debugShowcaseIndex = 0;
            
            String[] item = ITEM_SHOWCASE[debugShowcaseIndex];
            currentAttackIntroId = item[0]; // ItemType name
            int itemLevel = Integer.parseInt(item[1]);
            currentAttackIntroName = item[2];
            currentAttackIntroDescription = item[3];
            currentAttackIntroCategory = "ITEM"; // Mark as item for rendering
            attackIntroImage = loadItemShowcaseImage(item[0]); // Load item icon
            
            // Set the level to unlock this item
            gameData.setCurrentLevel(itemLevel);
        }
    }
    
    /**
     * Load showcase image for an active item
     */
    private BufferedImage loadItemShowcaseImage(String itemTypeName) {
        // Check cache first
        String cacheKey = "ITEM_" + itemTypeName;
        if (attackIntroImageCache.containsKey(cacheKey)) {
            return attackIntroImageCache.get(cacheKey);
        }
        
        // Try to load item image from sprites folder
        String[] possiblePaths = {
            "sprites/Tutorial/Attacks and Bullets Intros/" + itemTypeName + ".png",
            "sprites/items/" + itemTypeName + ".png",
            "sprites/items/" + itemTypeName.toLowerCase() + ".png"
        };
        
        for (String imagePath : possiblePaths) {
            try {
                BufferedImage img = AssetLoader.loadImage(imagePath);
                if (img != null) {
                    attackIntroImageCache.put(cacheKey, img);
                    System.out.println("Loaded item showcase image: " + imagePath);
                    return img;
                }
            } catch (Exception e) {
                // Try next path
            }
        }
        
        // Create a colored placeholder for items
        BufferedImage placeholder = createItemPlaceholderImage(itemTypeName);
        attackIntroImageCache.put(cacheKey, placeholder);
        return placeholder;
    }
    
    /**
     * Create a placeholder image for item showcase
     */
    private BufferedImage createItemPlaceholderImage(String itemTypeName) {
        int size = 200;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Get color based on item type
        Color itemColor = new Color(100, 200, 255); // Default cyan for items
        switch (itemTypeName) {
            case "SHIELD": itemColor = new Color(100, 180, 255); break;
            case "MAGNET": itemColor = new Color(255, 200, 100); break;
            case "SLOW_TIME": itemColor = new Color(180, 100, 255); break;
            case "PHASE": itemColor = new Color(100, 255, 200); break;
            case "BOMB": itemColor = new Color(255, 100, 100); break;
            case "STUN": itemColor = new Color(255, 255, 100); break;
            case "DOUBLE_DAMAGE": itemColor = new Color(255, 150, 50); break;
            case "IMPULSE": itemColor = new Color(150, 200, 255); break;
            case "FROST_BEAM": itemColor = new Color(150, 220, 255); break;
        }
        
        // Draw circular background
        g.setColor(new Color(40, 45, 60));
        g.fillOval(10, 10, size - 20, size - 20);
        g.setColor(itemColor);
        g.setStroke(new BasicStroke(4));
        g.drawOval(10, 10, size - 20, size - 20);
        
        // Draw icon symbol
        g.setFont(FontPalette.TITLE_MEDIUM);
        g.setColor(itemColor);
        String symbol = "?";
        switch (itemTypeName) {
            case "SHIELD": symbol = "â—‰"; break;
            case "MAGNET": symbol = "âŠ›"; break;
            case "SLOW_TIME": symbol = "â—·"; break;
            case "PHASE": symbol = "â—ˆ"; break;
            case "BOMB": symbol = "â—†"; break;
            case "STUN": symbol = "âš¡"; break;
            case "DOUBLE_DAMAGE": symbol = "Ã—2"; break;
            case "IMPULSE": symbol = "â—Ž"; break;
            case "FROST_BEAM": symbol = "â„"; break;
        }
        FontMetrics fm = g.getFontMetrics();
        int symbolX = (size - fm.stringWidth(symbol)) / 2;
        int symbolY = size / 2 + fm.getAscent() / 3;
        g.drawString(symbol, symbolX, symbolY);
        
        g.dispose();
        return img;
    }
    
    /**
     * Start the gameplay test for the currently selected attack or item
     */
    private void startShowcaseTest() {
        // Check if this item is locked
        boolean testLocked;
        if (debugShowcaseUnlockAll) {
            testLocked = false;
        } else if (showcaseTab == 1) {
            // For items tab, check against actual unlocked items list
            String itemTypeId = ITEM_SHOWCASE[debugShowcaseIndex][0];
            try {
                ActiveItem.ItemType checkType = ActiveItem.ItemType.valueOf(itemTypeId);
                testLocked = !gameData.getUnlockedItems().contains(checkType);
            } catch (Exception e) {
                testLocked = true;
            }
        } else {
            // For attacks tab, check against max unlocked level
            String levelStr = ATTACK_INTROS[debugShowcaseIndex][1];
            int checkLevel = Integer.parseInt(levelStr);
            testLocked = checkLevel > gameData.getMaxUnlockedLevel();
        }
        if (testLocked) {
            // Item is locked, can't start
            soundManager.playSound(SoundManager.Sound.PURCHASE_FAIL);
            screenShakeIntensity = 3;
            return;
        }
        
        debugShowcaseInGameplay = true;
        debugShowcaseTimer = 0;
        
        if (showcaseTab == 0) {
            // ATTACKS TAB - Test boss attack
            int attackLevel = Integer.parseInt(ATTACK_INTROS[debugShowcaseIndex][1]);
            gameData.setCurrentLevel(attackLevel);
            
            // Start the game
            riskContractType = 0;
            riskContractActive = false;
            riskContractMultiplier = 1.0;
            startGame();
            
            // Skip intro animations
            introPanActive = false;
            bossIntroActive = false;
            invulnerabilityTimer = 0;
            
            // Configure boss
            if (currentBoss != null && currentAttackIntroId != null) {
                configureBossForShowcase(currentAttackIntroId);
                currentBoss.setDebugSlowMode(true);
                currentBoss.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT / 2 - 50);
                currentBoss.setStayStationary(true);
            }
            
            System.out.println("DEBUG SHOWCASE: Testing Attack - " + currentAttackIntroName + 
                              " - Press N or ESC to return to selection, R to reset");
        } else {
            // ITEMS TAB - Test active item
            String itemTypeName = ITEM_SHOWCASE[debugShowcaseIndex][0];
            int itemLevel = Integer.parseInt(ITEM_SHOWCASE[debugShowcaseIndex][1]);
            gameData.setCurrentLevel(itemLevel);
            
            // Start the game
            riskContractType = 0;
            riskContractActive = false;
            riskContractMultiplier = 1.0;
            startGame();
            
            // Skip intro animations
            introPanActive = false;
            bossIntroActive = false;
            invulnerabilityTimer = 0;
            
            // Equip the selected active item (using gameData to set it directly)
            try {
                ActiveItem.ItemType itemType = ActiveItem.ItemType.valueOf(itemTypeName);
                gameData.equipItemByType(itemType); // Special method for showcase
                ActiveItem item = gameData.getEquippedItem();
                if (item != null) {
                    item.setCurrentCooldown(0); // Start ready to use
                }
            } catch (IllegalArgumentException e) {
                System.out.println("DEBUG SHOWCASE: Invalid item type: " + itemTypeName);
            }
            
            // Configure boss to shoot bullets for testing items
            if (currentBoss != null) {
                currentBoss.setDebugSlowMode(true);
                currentBoss.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT / 2 - 50);
                currentBoss.setStayStationary(true);
            }
            
            System.out.println("DEBUG SHOWCASE: Testing Item - " + currentAttackIntroName + 
                              " - Press SPACE to use item, N or ESC to return, R to reset");
        }
    }
    
    /**
     * Reset the showcase: clear bullets, reset boss position, but stay in gameplay
     */
    private void resetShowcase() {
        // Clear all bullets and effects
        bullets.clear();
        beamAttacks.clear();
        particles.clear();
        damageNumbers.clear();
        flares.clear();
        flareCooldownTimer = 300;
        
        // Reset boss position and state
        if (currentBoss != null) {
            currentBoss.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT / 2 - 50);
            currentBoss.clearBeamAttacks();
            // Re-configure for the showcase attack
            if (currentAttackIntroId != null) {
                configureBossForShowcase(currentAttackIntroId);
            }
        }
        
        // Reset player position
        player.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT - 100);
        
        soundManager.playSound(SoundManager.Sound.UI_SELECT);
        screenShakeIntensity = 5;
        
        System.out.println("DEBUG SHOWCASE: Reset - bullets cleared, boss reset");
    }
    
    /**
     * Show a specific attack in the debug showcase (legacy method for compatibility)
     */
    private void showDebugShowcaseAttack(int index) {
        showcaseTab = 0; // Force attacks tab for this legacy method
        debugShowcaseIndex = index;
        if (index < 0 || index >= ATTACK_INTROS.length) {
            debugShowcaseIndex = 0;
        }
        updateShowcaseInfo();
        debugShowcaseInGameplay = false;
        transitionToState(GameState.ATTACK_SHOWCASE);
        
        System.out.println("DEBUG SHOWCASE: Showing " + (debugShowcaseIndex + 1) + "/" + ATTACK_INTROS.length + 
                          " - " + currentAttackIntroName);
    }
    
    /**
     * Configure the boss to use a specific attack for the showcase
     */
    private void configureBossForShowcase(String attackId) {
        if (currentBoss == null) return;
        
        // Reset all forced modes
        currentBoss.setForcedPatternType(-1);
        currentBoss.setForceBeamAttack(false);
        currentBoss.setForceShockwave(false);
        currentBoss.setForceTwirlAttack(false);
        currentBoss.setForceMegaAttack(-1);
        currentBoss.setDisableBulletShooting(false); // Reset bullet shooting
        currentBoss.setDisableBeamAttacks(false); // Reset beam disable
        currentBoss.setDisableShockwave(false); // Reset shockwave disable
        currentBoss.setDisableTwirl(false); // Reset twirl disable
        
        // Map attack IDs to boss behavior
        // Pattern types: 0=Spiral, 1=Circle, 2=Aimed, 3=Wave, 4=Random, 5=Fast, 6=Large,
        //                7=Mixed, 8=SpiralBullets, 10=Accelerating, 11=WaveBullets,
        //                12=Bombs, 13=Grenades, 14=Nukes
        // Mega patterns: 0=MegaBurst, 1=MegaRing, 2=MegaCross, 3=MegaStar, 4=MegaHex
        switch (attackId) {
            case "basic_bullets":
                currentBoss.setForcedPatternType(2); // Aimed at player (simple shots)
                break;
            case "spiral_attack":
                currentBoss.setForcedPatternType(0); // Spiral pattern
                break;
            case "circle_attack":
                currentBoss.setForcedPatternType(1); // Circle pattern
                break;
            case "aimed_shots":
                currentBoss.setForcedPatternType(2); // Aimed at player
                break;
            case "wave_attack":
                currentBoss.setForcedPatternType(3); // Wave pattern
                break;
            case "random_spray":
                currentBoss.setForcedPatternType(4); // Random spray
                break;
            case "fast_bullets":
                currentBoss.setForcedPatternType(5); // Fast bullets
                break;
            case "large_bullets":
                currentBoss.setForcedPatternType(6); // Large bullets
                break;
            case "mixed_attack":
                currentBoss.setForcedPatternType(7); // Mixed attack
                break;
            case "beam_attack":
                currentBoss.setForceBeamAttack(true);
                currentBoss.setDisableBulletShooting(true); // Only show beam, no bullets
                currentBoss.setDisableShockwave(true); // No shockwave during beam showcase
                currentBoss.setDisableTwirl(true); // No twirl during beam showcase
                break;
            case "spiral_bullets":
                currentBoss.setForcedPatternType(8); // Spiral bullets
                break;
            case "shockwave":
                currentBoss.setForceShockwave(true);
                currentBoss.setDisableBulletShooting(true); // Only show shockwave, no bullets
                currentBoss.setDisableBeamAttacks(true); // No beams during shockwave showcase
                currentBoss.setDisableTwirl(true); // No twirl during shockwave showcase
                break;
            case "twirl_attack":
                currentBoss.setForceTwirlAttack(true);
                currentBoss.setDisableBulletShooting(true); // Only show twirl, no bullets
                currentBoss.setDisableBeamAttacks(true); // No beams during twirl showcase
                currentBoss.setDisableShockwave(true); // No shockwave during twirl showcase
                break;
            case "accelerating_bullets":
                currentBoss.setForcedPatternType(10); // Accelerating
                break;
            case "wave_bullets":
                currentBoss.setForcedPatternType(11); // Wave bullets
                break;
            case "bombs":
                currentBoss.setForcedPatternType(12); // Bombs
                break;
            case "grenades":
                currentBoss.setForcedPatternType(13); // Grenades
                break;
            case "nuke_bombs":
                currentBoss.setForcedPatternType(14); // Nukes
                break;
            case "homing_bullets":
                currentBoss.setForcedPatternType(15); // Homing
                break;
            case "bouncing_bullets":
                currentBoss.setForcedPatternType(16); // Bouncing
                break;
            case "mega_burst":
                currentBoss.setForceMegaAttack(0); // Mega burst
                break;
            case "mega_cross":
                currentBoss.setForceMegaAttack(2); // Mega cross
                break;
            case "mega_star":
                currentBoss.setForceMegaAttack(3); // Mega star
                break;
            case "mega_hex":
                currentBoss.setForceMegaAttack(4); // Mega hex
                break;
            case "mega_spiral":
                currentBoss.setForceMegaAttack(1); // Mega spiral
                break;
        }
        
        System.out.println("DEBUG SHOWCASE: Boss configured for attack: " + attackId);
    }
    
    /**
     * Update the debug showcase timer (called from main update loop)
     * Only tracks time during gameplay phase, not intro screens
     */
    private void updateDebugShowcase() {
        if (!debugShowcaseMode) return;
        
        // Only count timer during gameplay phase
        if (debugShowcaseInGameplay && gameState == GameState.PLAYING) {
            debugShowcaseTimer++;
            if (debugShowcaseTimer >= DEBUG_SHOWCASE_INTERVAL) {
                // Time's up, move to next attack
                debugShowcaseIndex++;
                showDebugShowcaseAttack(debugShowcaseIndex);
            }
        }
    }

    private void startGame() {
        gameState = GameState.PLAYING;
        
        // Clear all key states to prevent stuck movement from menu navigation
        java.util.Arrays.fill(keys, false);
        
        // Release cached shadow images from previous level to free memory
        ShadowCache.clear();
        
        int speedLevel = getActiveSpeedLevel();
        player = new Player(WORLD_WIDTH / 2, WORLD_HEIGHT - 200, speedLevel, keyBindManager, controllerManager);
        bullets.clear();
        particles.clear();
        damageNumbers.clear();
        beamAttacks.clear();
        moneyCircles.clear(); // Clear Pool of Loot circles from previous level
        flares.clear();
        flareCooldownTimer = 300;
        
        // Top off object pools between levels to prevent allocation spikes
        prewarmPools();
        
        // Determine the boss level: in endless mode, use the endless cycle level for boss type
        // but pass a higher effective level for difficulty scaling
        int bossLevel;
        if (gameData.isInEndlessMode()) {
            bossLevel = gameData.getEndlessCurrentLevel(); // 1-28 cycle for boss type/sprite
        } else {
            bossLevel = gameData.getCurrentLevel();
        }
        
        currentBoss = new Boss(WORLD_WIDTH / 2, 100, bossLevel, soundManager, gameData.getGameMode()); // Normal position, will move during intro
        
        // In endless mode, apply additional difficulty scaling
        if (gameData.isInEndlessMode()) {
            int effectiveLevel = gameData.getEndlessEffectiveLevel();
            currentBoss.setEffectiveLevel(effectiveLevel);
        }
        
        setupBossFactory(currentBoss);
        currentBoss.setAllowedPatterns(getAllowedPatternsForLevel(bossLevel)); // Sync attacks with ATTACK_INTROS
        gameData.setSurvivalTime(0);
        dodgeCombo = 0;
        stoppedMovingTimer = 0; // Reset Can't Stop timer
        comboTimer = 0;
        bossVulnerable = false;
        vulnerabilityTimer = 0;
        
        // Reset level stats for new attempt
        gameData.resetCurrentLevelStats();
        
        // Track spawn position for spawn protection radius
        spawnProtectionX = WORLD_WIDTH / 2;
        spawnProtectionY = WORLD_HEIGHT - 200;
        
        // Reset cumulative run stats if starting from level 1 (or endless level 1)
        if (gameData.getCurrentLevel() == 1 || (gameData.isInEndlessMode() && gameData.getEndlessCurrentLevel() == 1)) {
            gameData.resetCumulativeRunStats();
        }
        
        // Start ambient background sound
        soundManager.startAmbientSound();
        
        // Start boss fight music (fast crossfade for snappy level entry)
        soundManager.playMusicFast(getRandomBattleMusicPath());
        
        invulnerabilityTimer = 300; // 5 seconds of immunity at boss start
        bossHitCount = 0;
        respawnInvincibilityTimer = 0; // No respawn invincibility at start
        playerInvincible = false; // Reset player invincibility from previous level
        shieldActive = false; // Reset shield from previous level/respawn
        shieldHits = 0; // Reset shield hit counter
        shieldFirstUse = true; // Reset first use flag for shield cooldown
        shieldOrbitAngle = 0; // Reset orbit angle
        hasMovedOnce = false; // Reset Can't Stop contract movement tracker
        stoppedMovingTimer = 0;
        waitingForRespawn = false;
        respawnDelayTimer = 0;
        isPaused = false;
        selectedPauseItem = 0;
        unpauseCountdownActive = false;
        unpauseCountdownTimer = 0;
        tookDamageThisBoss = false;
        totalGrazesThisRun = 0;
        deathSequenceActive = false;
        playerHidden = false;
        respawnBlinkTimer = 0;
        bossHitCameraHoldTimer = 0;
        
        // Ensure missiles are at least the base count (fixes old saves with incorrect values)
        if (gameData.getMissiles() < gameData.getBaseMissiles()) {
            gameData.setMissiles(gameData.getBaseMissiles());
        }
        if (gameData.getBaseMissiles() < 3) {
            gameData.setBaseMissiles(3);
            gameData.setMissiles(Math.max(gameData.getMissiles(), 3));
        }
        
        comboSystem.resetCombo();
        
        // Set boss name for intro banner
        bossIntroActive = false;
        bossIntroText = currentBoss.getVehicleName();
        if (currentBoss.isMegaBoss()) {
            bossIntroText += " [MEGA]";
        }
        soundManager.playSound(SoundManager.Sound.BOSS_INTRO);
        
        // Start intro sequence with boss entrance
        introPanActive = true;
        introPanActive = true;
        introPanTimer = 0;
        bossEntranceY = -200; // Boss will start above screen
        cameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
        cameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
        
        screenShakeIntensity = 0;
        bossDeathAnimation = false;
        deathAnimationTimer = 0;
        bossDeathScale = 1.0;
        bossDeathRotation = 0;
        escapeTimer = 0;
        
        // Reset dynamic zoom effects
        effectZoom = 1.0;
        targetEffectZoom = 1.0;
        dashZoomActive = false;
        dashZoomTimer = 0;
        
        // Initialize timer and FPS tracking
        gameStartTime = System.currentTimeMillis();
        gameTimeSeconds = 0;
        debugSkipUsed = false;
        currentFPS = 0;
        frameCount = 0;
        lastFPSTime = System.currentTimeMillis();
        bossKillTime = 0;
        
        // Start active item cooldown at start of level (with passive cooldown reduction)
        ActiveItem equippedItem = gameData.getEquippedItem();
        if (equippedItem != null) {
            equippedItem.startLevelCooldown();
            double cdMult = passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.ITEM_COOLDOWN);
            if (cdMult < 1.0) equippedItem.setCurrentCooldown(equippedItem.getCurrentCooldown() * cdMult);
        }
    }
    
    public void start() {
        if (gameThread == null) {
            running = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }
    
    @Override
    public void run() {
        long lastTime = System.nanoTime();
        // Fixed-timestep: game logic ALWAYS ticks at 60 Hz regardless of
        // the display frame-rate chosen in settings.  This guarantees every
        // timer, counter, and velocity in the game behaves identically no
        // matter which FPS option the player selects.  The FPS setting only
        // controls how often we *render* (and thus how smooth the image
        // looks), not how fast the game runs.
        final double FIXED_UPDATE_HZ = 60.0;
        final double nsPerTick = 1000000000.0 / FIXED_UPDATE_HZ;
        double delta = 0;
        
        // Initialize off-screen render buffers (TYPE_INT_RGB â€” no alpha needed for display)
        renderBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        displayBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        System.out.println("[GPU] Render buffers: software BufferedImage"
            + " (GPU=" + enableGPUAcceleration + ")");
        
        while (running) {
            // Recreate render buffers if needed (e.g. after fullscreen toggle)
            if (needsBufferRecreate) {
                needsBufferRecreate = false;
                renderBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                displayBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            }
            
            long now = System.nanoTime();
            
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;
            
            // Cap delta to prevent spiral-of-death: if the game can't keep up
            // (e.g. slow I/O or long GC pause), drop frames instead of
            // queuing ever-more updates that make the lag worse.
            if (delta > 5) delta = 5;
            
            while (delta >= 1) {
                // deltaTime is always 1.0 (one tick = 1/60th of a second).
                // All game logic was written for this base rate, so no
                // per-value scaling is needed.
                double deltaTime = 1.0;
                update(deltaTime);
                gradientTime += 0.02; // Animate gradient (1 tick = 1 unit)
                if (renderer != null) renderer.advanceParallaxScroll(); // Advance BG scroll at fixed 60Hz
                
                // Blink cursor for save name input
                if (gameState == GameState.NAME_INPUT) {
                    saveNameCursorBlink++;
                }
                
                // Update escape timer
                if (escapeTimer > 0) {
                    escapeTimer -= 1.0;
                    if (escapeTimer < 0) escapeTimer = 0;
                }
                
                // Update scroll cooldown
                if (scrollCooldown > 0) {
                    scrollCooldown -= 1.0;
                }
                
                // Update game timer (only during active, unpaused gameplay — not during intro animations or unpause countdown)
                if (gameState == GameState.PLAYING && player != null) {
                    if (!isPaused && !unpauseCountdownActive && !introPanActive && !bossIntroActive) {
                        gameTimeSeconds = (System.currentTimeMillis() - gameStartTime) / 1000.0;
                    } else {
                        // Keep gameStartTime in sync so timer doesn't jump when unpaused/intro ends
                        gameStartTime = System.currentTimeMillis() - (long)(gameTimeSeconds * 1000);
                    }
                }
                
                delta--;
            }
            
            // Count *rendered* frames for the FPS display (not update ticks)
            frameCount++;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFPSTime >= 1000) {
                currentFPS = frameCount;
                frameCount = 0;
                lastFPSTime = currentTime;
            }
            
            // Render frame to off-screen buffer on game thread (avoids blocking EDT)
            renderToBuffer();
            
            // Swap buffers and trigger lightweight repaint (EDT just blits the buffer)
            synchronized (bufferSwapLock) {
                BufferedImage temp = displayBuffer;
                displayBuffer = renderBuffer;
                renderBuffer = temp;
            }
            repaint();
            
            // Sync display for smoother frame delivery
            Toolkit.getDefaultToolkit().sync();
            
            // Submit async background render (runs in parallel with EDT blit + sleep)
            if (backgroundMode == 1 && renderer != null) {
                renderer.submitBackgroundRender(updateThreadPool, gameData.getCurrentLevel(), WIDTH, HEIGHT);
            }
            
            // Sleep to hit the *display* frame-rate chosen in settings.
            // The update tick-rate is fixed at 60 Hz above; this only
            // controls how often we present a new image to the screen.
            double displayFPS = getTargetFPS();
            try {
                if (fpsLimit == 4) {
                    // Unlimited FPS - minimal yield
                    Thread.sleep(1);
                } else {
                    long targetNs = (long)(1000000000.0 / displayFPS);
                    long remainingMs = (targetNs - (System.nanoTime() - now)) / 1000000;
                    if (remainingMs > 1) {
                        Thread.sleep(remainingMs);
                    } else {
                        Thread.sleep(1);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private double getTargetFPS() {
        switch (fpsLimit) {
            case 0: return 30;
            case 1: return 60;
            case 2: return 120;
            case 3: return 144;
            case 4: return 1000; // Unlimited (cap at 1000 for safety)
            default: return 60;
        }
    }
    
    private void update(double deltaTime) {
        // Poll controller input
        if (controllerManager != null) {
            controllerManager.poll();
            
            // Controller rebind capture â€” intercept button presses when rebinding
            if (waitingForKeyBind && gameState == GameState.SETTINGS && selectedSettingsCategory == 4
                    && controllerManager.isConnected()) {
                // Check for B button to cancel rebinding
                if (controllerManager.isJustPressed(KeyBindManager.ControllerButton.B)) {
                    waitingForKeyBind = false;
                    rebindingActionIndex = -1;
                    rebindingController = false;
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                } else if (rebindingController) {
                    // Controller section rebinding - accept controller button
                    KeyBindManager.ControllerButton pressed = controllerManager.getFirstJustPressedButton();
                    if (pressed != null) {
                        int actionIndex = rebindingActionIndex - 1;
                        KeyBindManager.Action[] actions = KeyBindManager.Action.values();
                        if (actionIndex >= 0 && actionIndex < actions.length) {
                            keyBindManager.setControllerButton(actions[actionIndex], pressed);
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 3;
                        }
                        waitingForKeyBind = false;
                        rebindingActionIndex = -1;
                        rebindingController = false;
                    }
                }
            } else {
                handleControllerInput();
            }
        }
        
        // Update debug showcase mode timer
        if (debugShowcaseMode && gameState == GameState.ATTACK_INTRO) {
            updateDebugShowcase();
        }
        
        // Update bullet size multiplier from passive upgrades
        if (passiveUpgradeManager != null) {
            double bulletSizeMultiplier = passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.BULLET_SIZE);
            Bullet.setBulletSizeMultiplier(bulletSizeMultiplier);
        }
        
        // Handle unpause countdown timer (decrement even when game is frozen)
        if (unpauseCountdownActive) {
            unpauseCountdownTimer -= deltaTime;
            
            // Calculate current countdown second
            int currentSecond = (unpauseCountdownTimer > 0) ? ((int)((unpauseCountdownTimer - 1) / 60) + 1) : 0;
            
            // Trigger flash and sound on countdown changes
            if (currentSecond != lastCountdownSecond) {
                lastCountdownSecond = currentSecond;
                countdownFlashTimer = 15;
                if (currentSecond > 0) {
                    soundManager.playSound(SoundManager.Sound.COUNTDOWN_TICK);
                } else {
                    soundManager.playSound(SoundManager.Sound.COUNTDOWN_GO);
                }
            }
            
            if (unpauseCountdownTimer <= 0) {
                unpauseCountdownActive = false;
                lastCountdownSecond = -1;
            }
        }
        
        // Update cursor visibility based on game state
        boolean shouldHideCursor = (gameState == GameState.PLAYING && !isPaused);
        Cursor currentCursor = getCursor();
        if (shouldHideCursor && currentCursor != blankCursor) {
            setCursor(blankCursor);
        } else if (!shouldHideCursor && currentCursor == blankCursor) {
            setCursor(defaultCursor);
        }
        
        // Handle hit freeze frames (pause game briefly on boss damage)
        if (hitFreezeFrames > 0) {
            hitFreezeFrames -= deltaTime;
            // Still tick announcement timer during freeze so text animates
            if (comboSystem != null) comboSystem.tickAnnouncement(deltaTime);
            // Tick visual effect timers during freeze so animations play smoothly
            if (bossFlashTimer > 0) bossFlashTimer -= deltaTime;
            if (bossHitFlashTimer > 0) bossHitFlashTimer -= deltaTime;
            if (screenFlashTimer > 0) screenFlashTimer -= deltaTime;
            if (deathFlashTimer > 0) deathFlashTimer -= deltaTime;
            // Decay screen shake during freeze so it doesn't stay stuck at max
            if (screenShakeIntensity > 0) {
                screenShakeX = (Math.random() - 0.5) * screenShakeIntensity;
                screenShakeY = (Math.random() - 0.5) * screenShakeIntensity;
                screenShakeIntensity *= Math.pow(0.9, deltaTime);
                if (screenShakeIntensity < 0.1) screenShakeIntensity = 0;
            }
            return; // Skip gameplay update during freeze
        }
        
        // Apply slow-motion effect to delta time
        double effectiveDelta = deltaTime;
        if (slowMotionTimer > 0) {
            slowMotionTimer -= deltaTime;
            effectiveDelta = deltaTime * slowMotionFactor;
            if (slowMotionTimer <= 0) {
                slowMotionFactor = 1.0; // Reset
            }
        }
        
        // Use effectiveDelta for all gameplay updates during slow-motion
        final double dt = effectiveDelta;
        
        // === TUTORIAL STEP PROGRESS CHECKING ===
        if (tutorialMode && !tutorialPopupActive && !tutorialCompleteScreen && gameState == GameState.PLAYING) {
            // Handle cinematic slow-down phases
            if (tutorialSlowdownPhase == 1) {
                // SLOWING_IN: gameplay runs in ultra slow-mo, timer counts down
                tutorialSlowdownTimer--;
                if (tutorialSlowdownTimer <= 0) {
                    // Transition to POPUP_SHOWN — advance triggers popup via advanceTutorialStep
                    tutorialSlowdownPhase = 2;
                    advanceTutorialStep();
                }
            } else if (tutorialSlowdownPhase == 3) {
                // SLOWING_OUT: gameplay resumes in slow-mo, gradually restore
                tutorialSlowdownTimer--;
                if (tutorialSlowdownTimer <= 0) {
                    tutorialSlowdownPhase = 0;
                    slowMotionFactor = 1.0;
                    slowMotionTimer = 0;
                }
            } else {
                // NONE: check step completion conditions
                checkTutorialStepProgress();
            }
        }
        
        // If tutorial popup is active, skip all gameplay updates
        if (tutorialMode && tutorialPopupActive) {
            // Tick highlight timer even during popup
            if (renderer.tutorialHighlightTimer > 0) renderer.tutorialHighlightTimer--;
            // Tick popup input delay timer
            if (tutorialPopupInputDelay > 0) tutorialPopupInputDelay--;
            // Only allow input (handled in key press handler)
            return;
        }
        
        // Tick tutorial highlight timer
        if (tutorialMode && renderer.tutorialHighlightTimer > 0) {
            renderer.tutorialHighlightTimer--;
        }
        
        // Update perfect dodge i-frames
        if (perfectDodgeIFrames > 0) {
            perfectDodgeIFrames -= deltaTime;
        }
        if (perfectDodgeFlashTimer > 0) {
            perfectDodgeFlashTimer -= deltaTime;
        }
        
        // Update combo pulse animation (decay back to 1.0)
        if (comboPulseScale > 1.0) {
            comboPulseScale = Math.max(1.0, comboPulseScale - 0.05 * deltaTime);
        }
        
        // Update camera breathing effect
        cameraBreathTime += 0.02 * deltaTime;
        cameraBreathOffset = Math.sin(cameraBreathTime) * 2.0;
        
        // Smooth UI number animations (freeze during pause/countdown so score doesn't appear to tick up)
        if (!(gameState == GameState.PLAYING && (isPaused || unpauseCountdownActive))) {
            double scoreTarget = gameData.getScore();
            double moneyTarget = gameData.getTotalMoney() + gameData.getRunMoney();
            displayedScore += (scoreTarget - displayedScore) * 0.15 * deltaTime;
            displayedMoney += (moneyTarget - displayedMoney) * 0.15 * deltaTime;
        }
        
        // Update item unlock animation timer (let it countdown for animation progress)
        if (itemUnlockTimer > 0) {
            itemUnlockTimer -= deltaTime;
            if (itemUnlockTimer < 0) itemUnlockTimer = 0; // Clamp so == 0 checks work reliably
        }
        
        // Update leaderboard screen animation timer
        if (gameState == GameState.LEADERBOARD) {
            int prevTimer = leaderboardScreenTimer;
            leaderboardScreenTimer += (int) deltaTime;
            // Phase A: countdown reveal (0-90 frames / 1.5s)
            // Phase B: rank placement (90-180 frames / 1.5s)  
            // Phase C: hold (180+ frames)
            if (leaderboardAnimSkipped || leaderboardScreenTimer >= 180) {
                leaderboardReadyToExit = true;
            }
            
            // SFX triggers at animation milestones (one-shot)
            if (!leaderboardAnimSkipped) {
                // Countdown ticks during scramble phase (every 15 frames)
                if (leaderboardScreenTimer < 72 && prevTimer / 15 != leaderboardScreenTimer / 15) {
                    soundManager.playSound(SoundManager.Sound.COUNTDOWN_TICK, 0.5f);
                }
                // Time reveal at frame 72 (phase A resolves at 0.8 * 90)
                if (!lbSfxTimeReveal && leaderboardScreenTimer >= 72) {
                    lbSfxTimeReveal = true;
                    soundManager.playSound(SoundManager.Sound.COUNTDOWN_GO);
                }
                // Panel slides in at frame 90
                if (!lbSfxPanelSlide && leaderboardScreenTimer >= 90) {
                    lbSfxPanelSlide = true;
                    soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                }
                // Result fanfare at frame 135 (halfway through panel animation)
                if (!lbSfxResult && leaderboardScreenTimer >= 135) {
                    lbSfxResult = true;
                    LeaderboardManager.LeaderboardResult res = leaderboardManager.getRecentResult();
                    if (res == LeaderboardManager.LeaderboardResult.FIRST_COMPLETION) {
                        soundManager.playSound(SoundManager.Sound.ACHIEVEMENT_UNLOCK);
                    } else if (res == LeaderboardManager.LeaderboardResult.NEW_RECORD) {
                        soundManager.playSound(SoundManager.Sound.RANK_UP);
                    } else {
                        soundManager.playSound(SoundManager.Sound.UI_SELECT, 0.6f);
                    }
                }
            }
        }
        
        // Update dismiss animation
        if (itemUnlockDismissing) {
            itemUnlockDismissTimer -= deltaTime;
            if (itemUnlockDismissTimer <= 0) {
                itemUnlockAnimation = false;
                itemUnlockDismissing = false;
                
                // Check if we have a debug item popup queue with more items to show
                if (debugItemPopupQueue != null && !debugItemPopupQueue.isEmpty()) {
                    startNextDebugItemPopup();
                }
                // After item animation ends, check if we should show contract unlock
                // This happens on level 6 (second mega boss)
                else if (gameData.getCurrentLevel() == 6 && gameData.areContractsUnlocked() && !contractUnlockAnimation) {
                    soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                    contractUnlockAnimation = true;
                    contractUnlockTimer = CONTRACT_UNLOCK_DURATION;
                }
                // After item animation ends, check if endless unlock should show
                else if (gameData.hasCompletedCampaign() && !gameData.hasSeenEndlessUnlock() && !endlessUnlockAnimation) {
                    gameData.setEndlessUnlocked(true);
                    soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                    endlessUnlockAnimation = true;
                    endlessUnlockTimer = ENDLESS_UNLOCK_DURATION;
                    gameData.setSeenEndlessUnlock(true);
                }
                // Or if debug contract should show after all debug items
                else if (debugShowContractAfterItems) {
                    startNextDebugItemPopup(); // Will trigger contract since queue is empty
                }
            }
        }
        
        // Update contract unlock animation timer
        if (contractUnlockTimer > 0) {
            contractUnlockTimer -= deltaTime;
        }
        
        // Update contract dismiss animation
        if (contractUnlockDismissing) {
            contractUnlockDismissTimer -= deltaTime;
            if (contractUnlockDismissTimer <= 0) {
                contractUnlockAnimation = false;
                contractUnlockDismissing = false;
                // After contract dismiss, check if endless unlock should show
                if (gameData.hasCompletedCampaign() && !gameData.hasSeenEndlessUnlock() && !endlessUnlockAnimation) {
                    gameData.setEndlessUnlocked(true);
                    soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                    endlessUnlockAnimation = true;
                    endlessUnlockTimer = ENDLESS_UNLOCK_DURATION;
                    gameData.setSeenEndlessUnlock(true);
                }
            }
        }
        
        // Update endless unlock animation timer
        if (endlessUnlockTimer > 0) {
            endlessUnlockTimer -= deltaTime;
        }
        
        // Update endless unlock dismiss animation
        if (endlessUnlockDismissing) {
            endlessUnlockDismissTimer -= deltaTime;
            if (endlessUnlockDismissTimer <= 0) {
                endlessUnlockAnimation = false;
                endlessUnlockDismissing = false;
            }
        }
        
        // Update passive unlock animation timer
        if (passiveUnlockAnimation && passiveUnlockTimer > 0) {
            passiveUnlockTimer -= deltaTime;
        }
        
        // Update passive unlock dismiss animation
        if (passiveUnlockDismissing) {
            passiveUnlockDismissTimer -= deltaTime;
            if (passiveUnlockDismissTimer <= 0) {
                passiveUnlockDismissing = false;
                passiveUnlockAnimation = false;
                // Show next queued unlock, if any
                if (!pendingPassiveUnlocks.isEmpty()) {
                    startNextPassiveUnlockAnimation();
                }
            }
        }
        
        // Update state transitions
        if (stateTransitionProgress < 1.0f) {
            stateTransitionProgress = Math.min(1.0f, stateTransitionProgress + TRANSITION_SPEED * (float)deltaTime);
        }
        
        // Update save selection state
        if (gameState == GameState.SAVE_SELECT) {
            // Update delete confirmation timer only when delete button is held (deletingSlot flag)
            // Keyboard/mouse release handlers manage their own cancellation;
            // controller release is handled in handleControllerInput()
            if (deletingSlot) {
                deleteConfirmTimer++;
                
                // Auto-delete when timer reaches threshold
                if (deleteConfirmTimer >= 60) {
                    if (selectedSaveSlot < saveMetadataCache.size()) {
                        SaveManager.SaveMetadata meta = saveMetadataCache.get(selectedSaveSlot);
                        int slot = meta.slotNumber;
                        if (saveManager.saveExists(slot)) {
                            saveManager.delete(slot);
                            refreshSaveMetadata();
                            if (selectedSaveSlot > saveMetadataCache.size()) {
                                selectedSaveSlot = saveMetadataCache.size();
                            }
                            deletingSlot = false;
                            deleteConfirmTimer = 0;
                            controllerDeleteActive = false;
                            soundManager.playSound(SoundManager.Sound.BOSS_HIT);
                            screenShakeIntensity = 8;
                        }
                    }
                }
            }
            
            // NOTE: refreshSaveMetadata() is already called on state entry
            // (transitionToState) and after delete operations. No need to
            // poll the filesystem every frame â€” that caused a death-spiral freeze.
            
            // Smooth scroll animation for save select
            double saveScrollDiff = saveSelectScroll - saveSelectScrollAnimated;
            saveSelectScrollAnimated += saveScrollDiff * 0.15 * deltaTime;
            if (Math.abs(saveScrollDiff) < 0.5) {
                saveSelectScrollAnimated = saveSelectScroll;
            }
        }
        
        // Update auto-save indicator
        if (autoSaveIndicatorTimer > 0) {
            autoSaveIndicatorTimer -= deltaTime;
            if (autoSaveIndicatorTimer <= 0) {
                showAutoSaveIndicator = false;
            }
        }
        
        // Smooth scroll animation for level select carousel
        if (gameState == GameState.LEVEL_SELECT || gameState == GameState.LEVEL_CONFIRM) {
            double scrollDiff = levelSelectScroll - levelSelectScrollAnimated;
            levelSelectScrollAnimated += scrollDiff * 0.15 * deltaTime; // Smooth interpolation
            if (Math.abs(scrollDiff) < 0.01) {
                levelSelectScrollAnimated = levelSelectScroll;
            }
            
            // Handle plane takeoff animation
            if (planeTakeoffAnimation) {
                planeTakeoffTimer += deltaTime;
                if (planeTakeoffTimer >= PLANE_TAKEOFF_DURATION) {
                    // Animation complete - now actually start the level
                    planeTakeoffAnimation = false;
                    planeTakeoffTimer = 0;
                    
                    if (isConfirmingResume) {
                        // Resume saved game
                        restoreGameState();
                    } else {
                        // Start new level
                        gameData.setCurrentLevel(selectedLevelToStart);
                        
                        // Clear any saved game state since starting a new level
                        hasSavedGame = false;
                        
                        if (gameData.areContractsUnlocked()) {
                            selectedRiskContract = 0;
                            transitionToState(GameState.RISK_CONTRACT);
                        } else {
                            riskContractType = 0;
                            riskContractActive = false;
                            riskContractMultiplier = 1.0;
                            
                            // Check for new attack introductions at this level
                            checkForNewAttackIntros();
                            
                            // If there are pending intros, show them first; otherwise start game
                            if (!pendingAttackIntros.isEmpty()) {
                                showNextAttackIntro();
                            } else {
                                startGame();
                            }
                        }
                    }
                }
            }
        }
        
        // Smooth scroll animation for shop
        if (gameState == GameState.SHOP) {
            double shopScrollDiff = shopScroll - shopScrollAnimated;
            shopScrollAnimated += shopScrollDiff * 0.15 * deltaTime; // Smooth interpolation
            if (Math.abs(shopScrollDiff) < 0.01) {
                shopScrollAnimated = shopScroll;
            }
        }
        
        // Smooth scroll animation for stats screen
        if (gameState == GameState.STATS) {
            double statsScrollDiff = statsScroll - statsScrollAnimated;
            statsScrollAnimated += statsScrollDiff * 0.15 * deltaTime; // Smooth interpolation
            if (Math.abs(statsScrollDiff) < 0.01) {
                statsScrollAnimated = statsScroll;
            }
        }
        
        // Smooth scroll animation for achievements screen
        if (gameState == GameState.ACHIEVEMENTS) {
            double achievementsScrollDiff = achievementsScroll - achievementsScrollAnimated;
            achievementsScrollAnimated += achievementsScrollDiff * 0.15 * deltaTime; // Smooth interpolation
            if (Math.abs(achievementsScrollDiff) < 0.01) {
                achievementsScrollAnimated = achievementsScroll;
            }
        }
        
        // Smooth scroll animation for leaderboard view screen
        if (gameState == GameState.LEADERBOARD_VIEW) {
            double lbViewScrollDiff = leaderboardViewScroll - leaderboardViewScrollAnimated;
            leaderboardViewScrollAnimated += lbViewScrollDiff * 0.15 * deltaTime;
            if (Math.abs(lbViewScrollDiff) < 0.01) {
                leaderboardViewScrollAnimated = leaderboardViewScroll;
            }
        }
        
        if (gameState != GameState.PLAYING) return;
        
        // Skip all gameplay updates while paused or during unpause countdown
        if (isPaused || unpauseCountdownActive) return;
        
        // Update afterimage trail for player
        if (player != null) {
            afterimageTimer += deltaTime;
            if (afterimageTimer >= 3) { // Every 3 frames
                afterimageTimer = 0;
                // Shift old positions
                for (int i = afterimageX.length - 1; i > 0; i--) {
                    afterimageX[i] = afterimageX[i-1];
                    afterimageY[i] = afterimageY[i-1];
                    afterimageAlpha[i] = afterimageAlpha[i-1] * 0.7; // Fade out
                }
                // Add new position
                double speed = Math.sqrt(player.getVX() * player.getVX() + player.getVY() * player.getVY());
                afterimageX[0] = player.getX();
                afterimageY[0] = player.getY();
                afterimageAlpha[0] = Math.min(1.0, speed / 4.0); // Only visible when moving fast
            }
        }
        
        // Reset active item effect states each frame
        playerInvincible = false;
        dashSpeedMultiplier = 1.0;
        // NOTE: shieldHits is NOT reset here - it persists until shields are destroyed by bullets
        // Shield persists until used
        
        // Update boss stun timer - sync with item active state
        // Check if stun item is currently active
        ActiveItem equippedForStun = gameData.getEquippedItem();
        boolean stunItemActive = equippedForStun != null && 
            equippedForStun.getType() == ActiveItem.ItemType.STUN && 
            equippedForStun.isActive();
        
        if (bossStunned) {
            // Create shaking effect for stunned boss
            bossStunShakeOffset = (Math.random() - 0.5) * 8;
            
            // End stun when item is no longer active
            if (!stunItemActive) {
                bossStunned = false;
                bossStunShakeOffset = 0;
            }
        }
        
        // Update frost beam angle - smoothly follow player facing direction
        if (player != null) {
            double targetAngle = player.getAngle();
            double angleDiff = targetAngle - frostBeamAngle;
            // Normalize angle difference to -PI to PI
            while (angleDiff > Math.PI) angleDiff -= TWO_PI;
            while (angleDiff < -Math.PI) angleDiff += TWO_PI;
            // Smoothly interpolate
            frostBeamAngle += angleDiff * FROST_BEAM_TURN_SPEED * deltaTime;
            // Keep angle normalized
            while (frostBeamAngle > Math.PI) frostBeamAngle -= TWO_PI;
            while (frostBeamAngle < -Math.PI) frostBeamAngle += TWO_PI;
        }
        
        // Update frost beam animation (two-phase: extend thin, then thicken)
        if (frostBeamExtending) {
            frostBeamProgress += FROST_BEAM_EXTEND_SPEED * deltaTime;
            
            // Trigger intense shake when beam hits the "thicken" phase (at 0.3)
            if (frostBeamProgress >= 0.3 && !frostBeamShakeTriggered) {
                frostBeamShakeTriggered = true;
                screenShakeIntensity = 18; // Sharp intense shake
                soundManager.playSound(SoundManager.Sound.SCREEN_SHAKE, 0.7f);
            }
            
            if (frostBeamProgress >= 1.0) {
                frostBeamProgress = 1.0;
                frostBeamExtending = false;
            }
        } else if (frostBeamRetracting) {
            frostBeamRetractPhase += FROST_BEAM_RETRACT_SPEED * deltaTime;
            // Phase 1: Beam thins out rapidly (0-0.4)
            // Phase 2: Beam shortens from tip (0.4-0.8)
            // Phase 3: Circle fades (0.8-1.0)
            if (frostBeamRetractPhase >= 1.0) {
                frostBeamRetractPhase = 0;
                frostBeamProgress = 0;
                frostBeamRetracting = false;
                frostBeamShakeTriggered = false; // Reset for next use
            }
        }
        
        // Update bomb explosion queue (staggered explosions)
        if (!bombExplosionQueue.isEmpty()) {
            bombExplosionTimer++;
            
            // Process bombs whose delay has passed
            java.util.Iterator<double[]> bombIter = bombExplosionQueue.iterator();
            while (bombIter.hasNext()) {
                double[] bomb = bombIter.next();
                double bombX = bomb[0];
                double bombY = bomb[1];
                double delay = bomb[2];
                
                if (bombExplosionTimer >= delay) {
                    // EXPLODE THIS BOMB!
                    bombIter.remove();
                    
                    // Play explosion sound (only every other bomb to reduce audio spam)
                    if (bombExplosionTimer % 2 == 0) {
                        float pitch = 0.8f + (float)(Math.random() * 0.4f);
                        soundManager.playSound(SoundManager.Sound.EXPL_MEDIUM_1, pitch);
                    }
                    
                    // Screen shake (lighter)
                    screenShakeIntensity = Math.max(screenShakeIntensity, 8);
                    
                    // Destroy bullets within explosion radius (no per-bullet particles)
                    // Use squared distance to avoid sqrt per bullet
                    double bombRadiusSq = BOMB_EXPLOSION_RADIUS * BOMB_EXPLOSION_RADIUS;
                    int bulletsDestroyed = 0;
                    for (int i = bullets.size() - 1; i >= 0; i--) {
                        Bullet bullet = bullets.get(i);
                        double dx = bullet.getX() - bombX;
                        double dy = bullet.getY() - bombY;
                        double distSq = dx * dx + dy * dy;
                        
                        if (distSq < bombRadiusSq) {
                            bullets.remove(i);
                            returnBulletToPool(bullet);
                            bulletsDestroyed++;
                        }
                    }
                    
                    // Award score
                    gameData.addScore(bulletsDestroyed * 5);
                    
                    // EXPLOSION EFFECTS (balanced visuals)
                    if (enableParticles) {
                        // Shockwave ring
                        addParticle(
                            bombX, bombY, 0, 0,
                            BOMB_FLASH, 18, (int)(BOMB_EXPLOSION_RADIUS * 1.3),
                            Particle.ParticleType.EXPLOSION
                        );
                        
                        // Fire burst particles
                        for (int i = 0; i < 8; i++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 4 + Math.random() * 5;
                            Color fireColor = (Math.random() < 0.5) ? FIRE_ORANGE : FIRE_YELLOW;
                            addParticle(
                                bombX, bombY,
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                fireColor, 18, 5 + (int)(Math.random() * 3),
                                Particle.ParticleType.SPARK
                            );
                        }
                        
                        // Fast debris sparks
                        for (int i = 0; i < 6; i++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 10 + Math.random() * 6;
                            addParticle(
                                bombX, bombY,
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                BOMB_SPARK, 20, 2,
                                Particle.ParticleType.SPARK
                            );
                        }
                    }
                }
            }
            
            // Final big shake when all bombs done
            if (bombExplosionQueue.isEmpty() && bombExplosionTimer > 0) {
                soundManager.playSound(SoundManager.Sound.EXPL_LONG_1, 0.6f);
                screenShakeIntensity = 20;
                hitFreezeFrames = 6;
                bombExplosionTimer = 0;
            }
        }
        
        // Update dynamic zoom effects for active items
        ActiveItem equippedForZoom = gameData.getEquippedItem();
        if (equippedForZoom != null && equippedForZoom.isActive()) {
            if (equippedForZoom.getType() == ActiveItem.ItemType.TIME_SLOW) {
                // Zoom in during time slow
                targetEffectZoom = TIME_SLOW_ZOOM;
            } else if (equippedForZoom.getType() == ActiveItem.ItemType.DASH) {
                // Quick zoom out during dash (speed effect) - short impulse
                targetEffectZoom = DASH_ZOOM;
                dashZoomActive = true;
                dashZoomTimer = 8; // Very short impulse zoom - quick snap back
            }
        } else {
            // Return to normal zoom (unless dash/impulse zoom is still active)
            if (dashZoomActive) {
                dashZoomTimer -= dt;
                if (dashZoomTimer <= 0) {
                    dashZoomActive = false;
                    if (!impulseZoomActive) targetEffectZoom = 1.0;
                }
            } else if (impulseZoomActive) {
                impulseZoomTimer -= dt;
                if (impulseZoomTimer <= 0) {
                    impulseZoomActive = false;
                    targetEffectZoom = 1.0;
                    soundManager.stopSound(SoundManager.Sound.ELECTRIC_ZAP); // Stop impulse SFX
                }
            } else {
                targetEffectZoom = 1.0;
            }
        }
        
        // Smoothly interpolate effect zoom towards target
        effectZoom += (targetEffectZoom - effectZoom) * ZOOM_LERP_SPEED * deltaTime;
        
        // Update money circles
        // Track if player already got money this frame to prevent stacking
        boolean playerGotMoneyThisFrame = false;
        int moneyCircleAnimTimer = 0; // Use first circle's timer for money timing
        
        // Money circles are permanent - they never expire
        
        for (double[] circle : moneyCircles) {
            circle[2]++; // Increment timer for animation timing
            if (moneyCircleAnimTimer == 0) moneyCircleAnimTimer = (int)circle[2];
            
            // Check if player is in this circle (but only give money once)
            if (player != null && !playerGotMoneyThisFrame) {
                double dx = player.getX() - circle[0];
                double dy = player.getY() - circle[1];
                double distFromCircle = Math.sqrt(dx * dx + dy * dy);
                
                if (distFromCircle <= MONEY_CIRCLE_RADIUS) {
                    // Player is in a circle - give money every 20 frames (3 times per second)
                    // Use global timer so all circles are synchronized
                    if (moneyCircleAnimTimer % 20 == 0) {
                        gameData.addRunMoney(MONEY_CIRCLE_BONUS);
                        // Money only added to wallet on level completion
                        playerGotMoneyThisFrame = true; // Prevent stacking from other circles
                        
                        // Spawn falling money sign particle near player (bigger size)
                        if (enableParticles) {
                            // Money sign spawns near player and falls down
                            double spawnX = player.getX() + (Math.random() - 0.5) * 40;
                            double spawnY = player.getY() - 30;
                            addParticle(
                                spawnX, spawnY,
                                (Math.random() - 0.5) * 1.5, // Slight horizontal drift
                                2.0 + Math.random() * 1.5, // Fall downward
                                SPAWN_GREEN, 60, 18, // Green money color, BIGGER size (was 8)
                                Particle.ParticleType.MONEY_SIGN
                            );
                        }
                    }
                }
            }
            // Circles are permanent - never deactivate based on timer
        }
        
        // Update death sequence state machine
        if (deathSequenceActive) {
            if (deathCameraHoldTimer > 0) {
                // Phase 1: Hold camera at death position
                deathCameraHoldTimer -= deltaTime;
                // Lock camera on death position
                double baseCameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                double baseCameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                double deathOffsetX = deathExplosionX - WORLD_WIDTH / 2.0;
                double deathOffsetY = deathExplosionY - WORLD_HEIGHT / 2.0;
                cameraX = baseCameraX + Math.max(-CAMERA_MAX_OFFSET, Math.min((WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_MAX_OFFSET, deathOffsetX));
                cameraY = baseCameraY + Math.max(-CAMERA_MAX_OFFSET, Math.min((WORLD_HEIGHT - HEIGHT) / 2.0 + CAMERA_MAX_OFFSET, deathOffsetY));
                
                if (deathCameraHoldTimer <= 0) {
                    // Start pan-back phase
                    cameraPanBackTimer = CAMERA_PAN_BACK_FRAMES;
                    cameraPanStartX = cameraX;
                    cameraPanStartY = cameraY;
                }
            } else if (cameraPanBackTimer > 0) {
                // Phase 2: Pan camera back to spawn point
                cameraPanBackTimer -= deltaTime;
                double progress = 1.0 - (cameraPanBackTimer / (double)CAMERA_PAN_BACK_FRAMES);
                progress = Math.max(0, Math.min(1, progress));
                // Smooth easing
                double ease = progress * progress * (3 - 2 * progress);
                double spawnCameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                double spawnCameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                // Offset for spawn point
                double spawnOffsetY = (WORLD_HEIGHT - 200) - WORLD_HEIGHT / 2.0;
                if (Math.abs(spawnOffsetY) > CAMERA_DEADZONE) {
                    spawnCameraY += spawnOffsetY - Math.signum(spawnOffsetY) * CAMERA_DEADZONE;
                }
                cameraX = cameraPanStartX + (spawnCameraX - cameraPanStartX) * ease;
                cameraY = cameraPanStartY + (spawnCameraY - cameraPanStartY) * ease;
                
                if (cameraPanBackTimer <= 0) {
                    // Phase 3: Respawn player
                    playerHidden = false;
                    deathSequenceActive = false;
                    player.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT - 200);
                    player.resetVelocity();
                    
                    // Reset Can't Stop contract so player must input movement before timer starts
                    hasMovedOnce = false;
                    stoppedMovingTimer = 0;
                    
                    // Grant temporary invincibility
                    playerInvincible = true;
                    respawnInvincibilityTimer = RESPAWN_BLINK_FRAMES; // 3 seconds
                    respawnBlinkTimer = RESPAWN_BLINK_FRAMES;
                    
                    // Track spawn position for radius check
                    spawnProtectionX = player.getX();
                    spawnProtectionY = player.getY();
                }
            }
        }
        
        // Update respawn blink timer
        if (respawnBlinkTimer > 0) {
            respawnBlinkTimer -= deltaTime;
            if (respawnBlinkTimer <= 0) {
                respawnBlinkTimer = 0;
            }
            
            // Damage smoke trail during respawn blink â€” shows the missile was just hit
            if (enableParticles && player != null && Math.random() < 0.40) {
                double px = player.getX() + (Math.random() - 0.5) * 16;
                double py = player.getY() + (Math.random() - 0.5) * 16;
                if (Math.random() < 0.6) {
                    int gray = 60 + (int)(Math.random() * 40);
                    addParticle(px, py, (Math.random()-0.5)*0.6, -0.4 - Math.random()*0.8,
                        new Color(gray, gray, gray, 160), 40, 4 + Math.random()*3,
                        Particle.ParticleType.SMOKE);
                } else {
                    addParticle(px, py, (Math.random()-0.5)*0.5, -0.3 - Math.random()*0.6,
                        new Color(255, 140 + (int)(Math.random()*60), 30, 170), 30, 3 + Math.random()*2,
                        Particle.ParticleType.EXHAUST);
                }
            }
        }
        
        // Handle respawn invincibility timer
        if (respawnInvincibilityTimer > 0) {
            respawnInvincibilityTimer -= deltaTime;
            
            // Check if player moved too far from spawn point
            if (player != null) {
                double dx = player.getX() - spawnProtectionX;
                double dy = player.getY() - spawnProtectionY;
                double distanceFromSpawn = Math.sqrt(dx * dx + dy * dy);
                
                if (distanceFromSpawn > SPAWN_PROTECTION_RADIUS) {
                    // Player moved too far - remove protection immediately
                    respawnInvincibilityTimer = 0;
                    playerInvincible = false;
                }
            }
            
            if (respawnInvincibilityTimer <= 0) {
                // Timer expired - remove invincibility
                playerInvincible = false;
            } else {
                // Still invincible from respawn
                playerInvincible = true;
            }
        }
        
        // Track survival time (for stats only - score is awarded at level completion based on speed)
        // Don't count intro animation time as survival time
        if (player != null && !introPanActive && !bossIntroActive) {
            gameData.incrementSurvivalTime();
        }
        
        // Update active item
        ActiveItem equippedItem = gameData.getEquippedItem();
        if (equippedItem != null) {
            equippedItem.update(deltaTime);
            
            // Detect when item becomes ready
            boolean isReadyNow = equippedItem.canActivate();
            if (isReadyNow && !wasItemReady) {
                // Item just became ready - trigger flicker effect
                itemReadyFlickerTimer = 20; // Flicker for 20 frames
                soundManager.playSound(SoundManager.Sound.POWERUP_ACTIVATE);
            }
            wasItemReady = isReadyNow;
            
            // Detect when item effect completes (was active, now not) - exclude instant items
            boolean isActiveNow = equippedItem.isActive();
            if (wasItemActive && !isActiveNow && equippedItem.getActiveDuration() > 0) {
                // Item effect just finished - trigger flash effect
                itemCompleteFlashTimer = 15;
                soundManager.playSound(SoundManager.Sound.ITEM_END, 0.4f);
                
                // Stop lingering SFX for specific items
                if (equippedItem.getType() == ActiveItem.ItemType.STUN) {
                    soundManager.stopSound(SoundManager.Sound.ELECTRIC_ZAP);
                }
                
                // Start frost beam retraction animation
                if (equippedItem.getType() == ActiveItem.ItemType.FROST_BEAM) {
                    frostBeamRetracting = true;
                    frostBeamExtending = false;
                    frostBeamRetractPhase = 0; // Start retraction from beginning
                }
            }
            wasItemActive = isActiveNow;
            
            // Handle active item effects
            if (equippedItem.isActive()) {
                handleActiveItemEffects(equippedItem, deltaTime);
            }
            // Note: Shield persists until all 3 orbs are destroyed by bullets
            // Don't clear shieldActive here - it's managed by collision detection
        }
        
        // Update screen shake
        if (screenShakeIntensity > 0) {
            // Play shake sound for strong impacts (intensity >= 5)
            if (screenShakeIntensity >= 5 && screenShakeIntensity < 5.5) {
                soundManager.playSound(SoundManager.Sound.SCREEN_SHAKE, 0.3f);
            }
            screenShakeX = (Math.random() - 0.5) * screenShakeIntensity;
            screenShakeY = (Math.random() - 0.5) * screenShakeIntensity;
            screenShakeIntensity *= Math.pow(0.9, deltaTime);
            if (screenShakeIntensity < 0.1) screenShakeIntensity = 0;
        } else {
            screenShakeX = 0;
            screenShakeY = 0;
        }
        
        // Update flash timers
        if (bossFlashTimer > 0) {
            bossFlashTimer -= deltaTime;
        }
        if (screenFlashTimer > 0) {
            screenFlashTimer -= deltaTime;
        }
        if (itemReadyFlickerTimer > 0) {
            itemReadyFlickerTimer -= deltaTime;
        }
        if (itemCompleteFlashTimer > 0) {
            itemCompleteFlashTimer -= deltaTime;
        }
        if (achievementFlashTimer > 0) {
            achievementFlashTimer -= deltaTime;
        }
        if (bossIntroFlashTimer > 0) {
            bossIntroFlashTimer -= deltaTime;
        }
        if (countdownFlashTimer > 0) {
            countdownFlashTimer -= deltaTime;
        }
        if (bossHitFlashTimer > 0) {
            bossHitFlashTimer -= deltaTime;
        }
        if (deathFlashTimer > 0) {
            deathFlashTimer -= deltaTime;
        }
        if (typePurgeFlashTimer > 0) {
            typePurgeFlashTimer -= deltaTime;
        }

        // Update combo timer
        if (comboTimer > 0) {
            comboTimer -= deltaTime;
            if (comboTimer <= 0) {
                dodgeCombo = 0;
            }
        }
        
        // If paused or countdown active, freeze all gameplay
        if (isPaused || unpauseCountdownActive) {
            return;
        }
        
        // Update player with delta time (only if alive)
        if (player != null) {
            // Only allow player control when intro pan and boss intro cinematic are complete
            if (!introPanActive && !bossIntroActive) {
                player.update(keys, WORLD_WIDTH, WORLD_HEIGHT, dt); // Use world bounds for larger map
                
                // Targeting passive: auto-aim toward boss when nearby
                int targetingLevel = getActiveTargetingLevel();
                if (targetingLevel > 0 && currentBoss != null && bossVulnerable) {
                    double dx = currentBoss.getX() - player.getX();
                    double dy = currentBoss.getY() - player.getY();
                    double distToBoss = Math.sqrt(dx * dx + dy * dy);
                    // Radius scales with level: 175 / 220 / 265
                    double targetingRadius = 130 + (targetingLevel * 45);
                    
                    if (distToBoss < targetingRadius && distToBoss > 0) {
                        double angleToBoss = Math.atan2(dy, dx);
                        double playerAngle = player.getAngle();
                        double angleDiff = angleToBoss - playerAngle;
                        while (angleDiff > Math.PI) angleDiff -= TWO_PI;
                        while (angleDiff < -Math.PI) angleDiff += TWO_PI;
                        
                        // Angle cone widens with level: ~70Â° / ~90Â° / ~110Â°
                        double maxAngleCone = (Math.PI / 3) + (targetingLevel * Math.PI / 9);
                        if (Math.abs(angleDiff) < maxAngleCone) {
                            // Strength scales with level: 0.045 / 0.09 / 0.135
                            double baseStrength = targetingLevel * 0.045;
                            double distanceFalloff = 1.0 - (distToBoss / targetingRadius);
                            double assistStrength = baseStrength * distanceFalloff * dt;
                            player.nudgeAngle(angleToBoss, assistStrength);
                        }
                    }
                }
                
                // Update orbiting shield rotation
                if (shieldActive && shieldHits > 0) {
                    shieldOrbitAngle += dt * 0.06; // Gentle shield rotation
                    if (shieldOrbitAngle > TWO_PI) shieldOrbitAngle -= TWO_PI;
                }
                
                // Can't Stop contract: Check if player is moving
                if (riskContractType == 4 && riskContractActive) {
                    double playerSpeed = Math.sqrt(player.getVX() * player.getVX() + player.getVY() * player.getVY());
                    
                    // Mark that player has moved at least once (based on actual input, not velocity)
                    // This prevents death at spawn before the player has pressed any movement key
                    if (!hasMovedOnce) {
                        boolean movementInputActive = false;
                        if (keyBindManager != null) {
                            int upKey = keyBindManager.getKey(KeyBindManager.Action.MOVE_UP);
                            int downKey = keyBindManager.getKey(KeyBindManager.Action.MOVE_DOWN);
                            int leftKey = keyBindManager.getKey(KeyBindManager.Action.MOVE_LEFT);
                            int rightKey = keyBindManager.getKey(KeyBindManager.Action.MOVE_RIGHT);
                            if ((upKey >= 0 && upKey < keys.length && keys[upKey]) ||
                                (downKey >= 0 && downKey < keys.length && keys[downKey]) ||
                                (leftKey >= 0 && leftKey < keys.length && keys[leftKey]) ||
                                (rightKey >= 0 && rightKey < keys.length && keys[rightKey])) {
                                movementInputActive = true;
                            }
                        }
                        if (!movementInputActive && controllerManager != null && controllerManager.isConnected()) {
                            if (controllerManager.isActionPressed(KeyBindManager.Action.MOVE_UP) ||
                                controllerManager.isActionPressed(KeyBindManager.Action.MOVE_DOWN) ||
                                controllerManager.isActionPressed(KeyBindManager.Action.MOVE_LEFT) ||
                                controllerManager.isActionPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                                movementInputActive = true;
                            }
                        }
                        if (movementInputActive) {
                            hasMovedOnce = true;
                        }
                    }
                    
                    // Only enforce movement requirement after player has moved once
                    if (hasMovedOnce) {
                        if (playerSpeed < MIN_MOVEMENT_SPEED) {
                            // Player is not moving
                            stoppedMovingTimer += deltaTime;
                            
                            // Show warning when timer is running low
                            if (stoppedMovingTimer >= STOPPED_GRACE_PERIOD) {
                            // Kill the player
                            soundManager.playSound(SoundManager.Sound.PLAYER_DEATH);
                            screenShakeIntensity = 10;
                            tookDamageThisBoss = true;
                            
                            // Show death message
                            if (comboSystem != null) comboSystem.setAnnouncement("KEEP MOVING!", WIDTH / 2.0, HEIGHT / 2.0);
                            
                            // Create missile destruction particles\n                            if (enableParticles) {\n                                for (int i = 0; i < 15; i++) {\n                                    double angle = Math.random() * Math.PI * 2;\n                                    double speed = 1 + Math.random() * 3;\n                                    Color debrisColor = Math.random() < 0.5 ? METAL_DEBRIS : PLAYER_DEATH_RED;\n                                    addParticle(\n                                        player.getX(), player.getY(),\n                                        Math.cos(angle) * speed, Math.sin(angle) * speed,\n                                        debrisColor, 60, 8 + Math.random() * 6,\n                                        Particle.ParticleType.DEBRIS\n                                    );\n                                }\n                                for (int i = 0; i < 20; i++) {\n                                    double angle = Math.random() * Math.PI * 2;\n                                    double speed = 3 + Math.random() * 5;\n                                    Color fireColor;\n                                    double r = Math.random();\n                                    if (r < 0.3) fireColor = new Color(255, 255, 220);\n                                    else if (r < 0.6) fireColor = new Color(255, 200, 50);\n                                    else fireColor = new Color(255, 120, 30);\n                                    addParticle(\n                                        player.getX(), player.getY(),\n                                        Math.cos(angle) * speed, Math.sin(angle) * speed,\n                                        fireColor, 40, 6,\n                                        Particle.ParticleType.EXHAUST\n                                    );\n                                }\n                            }
                            
                            player = null; // Kill player
                            gameState = GameState.GAME_OVER;
                            if (renderer != null) renderer.setScreenEnteredTime(gradientTime);
                            soundManager.stopMusic();
                        }
                    } else {
                        // Player is moving, reset timer
                        stoppedMovingTimer = 0;
                    }
                    }
                }
            }
            
            // Handle intro sequence
            if (introPanActive) {
                introPanTimer += dt;
                
                double halfDuration = INTRO_PAN_DURATION / 2.0;
                if (introPanTimer < halfDuration) {
                    // Boss entrance animation - fly down from above
                    double progress = introPanTimer / halfDuration;
                    double easeProgress = 1 - Math.pow(1 - progress, 3); // Ease out cubic
                    
                    // Boss flies down smoothly from -200 to 100
                    bossEntranceY = -200 + (300 * easeProgress);
                    if (currentBoss != null) {
                        // Directly set boss Y position during entrance
                        currentBoss.setPosition(currentBoss.getX(), bossEntranceY);
                        // Keep animations running (helicopter blades)
                        currentBoss.updateAnimations(deltaTime);
                        
                        // Add screen shake during descent
                        if (progress > 0.2) {
                            screenShakeIntensity = Math.max(screenShakeIntensity, 8 + easeProgress * 4);
                        }
                        
                        // Add jet trail particles during descent - throttled for performance
                        if (progress > 0.1 && particles.size() < MAX_PARTICLES && Math.random() < 0.25) {
                            double angle = -Math.PI / 2 + (Math.random() - 0.5) * 0.5; // Point upward (thrusters push down)
                            double speed = 1 + Math.random() * 2;
                            addParticle(
                                currentBoss.getX() + (Math.random() - 0.5) * 30,
                                currentBoss.getY() + currentBoss.getSize() / 2,
                                Math.cos(angle) * speed,
                                Math.sin(angle) * speed,
                                JET_TRAIL_COLOR,
                                60 + (int)(Math.random() * 30),
                                8.0 + Math.random() * 8.0,
                                Particle.ParticleType.TRAIL
                            );
                        }
                    }
                    
                    // Camera follows boss down slightly
                    double baseOffY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                    double targetY = bossEntranceY * 0.3;
                    cameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                    cameraY = baseOffY + targetY;
                    
                } else if (introPanTimer < INTRO_PAN_DURATION) {
                    // Pan back to player (second half)
                    double progress = (introPanTimer - halfDuration) / halfDuration;
                    double easeProgress = progress * progress * (3 - 2 * progress); // Smooth ease
                    
                    // Boss settles into final position (100)
                    if (currentBoss != null) {
                        if (bossEntranceY < 100) {
                            bossEntranceY += (100 - bossEntranceY) * 0.1;
                        } else {
                            bossEntranceY = 100;
                        }
                        currentBoss.setPosition(currentBoss.getX(), bossEntranceY);
                        // Keep animations running
                        currentBoss.updateAnimations(deltaTime);
                        
                        // Screen shake decreases as boss settles
                        screenShakeIntensity = Math.max(screenShakeIntensity, 6 * (1 - easeProgress));
                    }
                    
                    // Camera pans back to center
                    double startPanY = 30.0; // Camera's extra Y during boss viewing
                    double baseOffsetY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                    cameraY = baseOffsetY + startPanY * (1 - easeProgress);
                    
                    // Add engine glow particles as boss settles - throttled
                    if (currentBoss != null && particles.size() < MAX_PARTICLES && Math.random() < 0.1) {
                        addParticle(
                            currentBoss.getX() + (Math.random() - 0.5) * 40,
                            currentBoss.getY() + currentBoss.getSize() / 2,
                            (Math.random() - 0.5) * 0.5,
                            1 + Math.random() * 1.5,
                            ENGINE_GLOW_BLUE,
                            40 + (int)(Math.random() * 20),
                            6.0 + Math.random() * 6.0,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                } else {
                    // Entrance complete - add final burst of particles
                    if (introPanTimer - deltaTime < INTRO_PAN_DURATION) {
                        // Just finished - add dramatic particle burst (reduced count)
                        screenShakeIntensity = 15; // Massive shake at the end
                        if (currentBoss != null) {
                            for (int i = 0; i < 12 && particles.size() < MAX_PARTICLES; i++) {
                                double angle = Math.random() * Math.PI * 2;
                                double speed = 1 + Math.random() * 3;
                                addParticle(
                                    currentBoss.getX(),
                                    currentBoss.getY(),
                                    Math.cos(angle) * speed,
                                    Math.sin(angle) * speed,
                                    EXPLOSION_WARM,
                                    30 + (int)(Math.random() * 30),
                                    10.0 + Math.random() * 10.0,
                                    Particle.ParticleType.EXPLOSION
                                );
                            }
                        }
                    }
                    
                    introPanActive = false;
                    cameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                    cameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                    if (currentBoss != null) currentBoss.setPosition(currentBoss.getX(), 100);
                    if (demoIntroActive) {
                        demoIntroActive = false;
                        transitionToState(GameState.MENU);
                    }
                }
            } else if (bossHitCameraHoldTimer > 0) {
                // Hold camera at collision area so boss hit explosion is clearly visible
                bossHitCameraHoldTimer -= deltaTime;
            } else if (player != null && !deathSequenceActive) {
                // Normal camera follow with slow smooth interpolation (only when intro is done and player exists)
                // Base offset centers the world in the viewport
                double baseCameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                double baseCameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                double targetCameraX = baseCameraX;
                double targetCameraY = baseCameraY;
                
                // Calculate offset from world center (camera follows player in expanded world)
                double offsetX = player.getX() - WORLD_WIDTH / 2.0;
                double offsetY = player.getY() - WORLD_HEIGHT / 2.0;
                
                // Only move camera if player is outside deadzone
                if (Math.abs(offsetX) > CAMERA_DEADZONE) {
                    targetCameraX += offsetX - Math.signum(offsetX) * CAMERA_DEADZONE;
                }
                if (Math.abs(offsetY) > CAMERA_DEADZONE) {
                    targetCameraY += offsetY - Math.signum(offsetY) * CAMERA_DEADZONE;
                }
                
                // Clamp target to keep world edges visible (left offset by 160 so gradient edge is fully reachable, right offset by 180)
                double maxOffsetX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET + CAMERA_MAX_OFFSET + 180;
                double maxOffsetY = (WORLD_HEIGHT - HEIGHT) / 2.0 + CAMERA_MAX_OFFSET;
                targetCameraX = Math.max(-(CAMERA_MAX_OFFSET + 160) + CAMERA_HORIZONTAL_OFFSET, Math.min(maxOffsetX, targetCameraX));
                targetCameraY = Math.max(-CAMERA_MAX_OFFSET, Math.min(maxOffsetY, targetCameraY));
                
                // Smoothly interpolate camera position
                double zoomAdjustedSmoothing = CAMERA_SMOOTHING / cameraZoom * dt;
                cameraX += (targetCameraX - cameraX) * zoomAdjustedSmoothing;
                cameraY += (targetCameraY - cameraY) * zoomAdjustedSmoothing;
            }
            
            // Update combo system
            comboSystem.update(deltaTime, 1.0); // Combo duration no longer has passive upgrade
            
            // Update damage numbers
            for (int i = damageNumbers.size() - 1; i >= 0; i--) {
                damageNumbers.get(i).update(deltaTime);
                if (damageNumbers.get(i).isDone()) {
                    damageNumbers.remove(i);
                }
            }
            
            // Update achievement notifications
            if (achievementNotificationTimer > 0) {
                achievementNotificationTimer -= deltaTime;
            }
            if (achievementNotificationTimer <= 0 && !pendingAchievements.isEmpty()) {
                // Remove displayed achievement
                pendingAchievements.remove(0);
                if (!pendingAchievements.isEmpty()) {
                    achievementNotificationTimer = ACHIEVEMENT_NOTIFICATION_DURATION;
                }
            }
            
            // Spawn fire trail behind player (skip during death sequence and boss hit camera hold)
            if (player != null && Game.enableParticles && !playerHidden && !deathSequenceActive && bossHitCameraHoldTimer <= 0) {
                trailSpawnTimer += deltaTime;
                if (trailSpawnTimer >= 2) { // Every 2 frames worth of time
                    trailSpawnTimer = 0;
                    // Create rocket/fire trail particles
                    // Calculate angle based on velocity (or default upward if stationary)
                    double vx = player.getVX();
                    double vy = player.getVY();
                    double speed = Math.sqrt(vx * vx + vy * vy);
                    double angle = (vx == 0 && vy == 0) ? -Math.PI / 2 : Math.atan2(vy, vx);
                    
                    // Spawn particles at the back of the rocket (opposite to movement direction)
                    double backDistance = 20; // Distance behind rocket center
                    double trailX = player.getX() - Math.cos(angle) * backDistance;
                    double trailY = player.getY() - Math.sin(angle) * backDistance;
                    
                    // Scale exhaust with speed upgrade level (0-10)
                    int spdLvl = getActiveSpeedLevel();
                    double spdFrac = spdLvl / 10.0; // 0.0 to 1.0
                    
                    // --- BLUE BASE PARTICLES (only when speed upgrades > 0) ---
                    // These spawn RIGHT at the nozzle with short life so they stay at the base
                    if (spdLvl > 0 && particles.size() < MAX_PARTICLES) {
                        int blueCount = 1 + (spdLvl >= 4 ? 1 : 0) + (spdLvl >= 8 ? 1 : 0);
                        for (int bp = 0; bp < blueCount; bp++) {
                            if (particles.size() >= MAX_PARTICLES) break;
                            double perpAngle = angle + Math.PI / 2;
                            double spread = (Math.random() - 0.5) * 4;
                            double bx = trailX + Math.cos(perpAngle) * spread;
                            double by = trailY + Math.sin(perpAngle) * spread;
                            // Very slow velocity so they stay near the base
                            double bvx = -Math.cos(angle) * (0.2 + Math.random() * 0.4);
                            double bvy = -Math.sin(angle) * (0.2 + Math.random() * 0.4);
                            // Deep blue flame colors - more intense at higher levels
                            int r = (int)(0 + Math.random() * 20);
                            int g = (int)(100 + Math.random() * 100);
                            int b = 255;
                            addParticle(bx, by, bvx, bvy,
                                new Color(r, g, b),
                                6 + (int)(Math.random() * 6 + spdFrac * 4), // Short life - stays at base
                                4 + (int)(Math.random() * 3 + spdFrac * 2),
                                Particle.ParticleType.EXHAUST);
                        }
                    }
                    
                    // --- ORANGE/FIRE TRAIL PARTICLES (the main exhaust plume) ---
                    // These travel further and form the visible trail behind the rocket
                    if (particles.size() < MAX_PARTICLES) {
                        double perpAngle = angle + Math.PI / 2;
                        double spread = (Math.random() - 0.5) * 6;
                        double finalX = trailX + Math.cos(perpAngle) * spread;
                        double finalY = trailY + Math.sin(perpAngle) * spread;
                        
                        // Particle velocity scales with player speed for natural trail length
                        // Stationary: moderate idle exhaust, moving: trails behind proportionally
                        double baseVel = 0.6 + Math.random() * 0.5;
                        double speedScale = 1.0 + Math.min(speed * 0.08, 0.5); // Gentle scaling, capped
                        double particleVX = -Math.cos(angle) * baseVel * speedScale;
                        double particleVY = -Math.sin(angle) * baseVel * speedScale;
                        
                        Color trailColor = Math.random() < 0.5 ? FIRE_ORANGE : BOSS_FIRE;
                        
                        // Lifetime: decent idle length, modest increase with speed upgrades
                        int baseLife = 18 + (int)(spdFrac * 5);
                        int lifeVariance = 8;
                        
                        addParticle(
                            finalX, finalY,
                            particleVX, particleVY,
                            trailColor,
                            baseLife + (int)(Math.random() * lifeVariance),
                            6 + (int)(Math.random() * 5),
                            Particle.ParticleType.EXHAUST
                        );
                    }
                }
            }
        }
        
        // Update particles - parallel update then single-threaded removal
        {
            final double particleDt = deltaTime;
            int pSize = particles.size();
            if (pSize > 300 && THREAD_COUNT > 1) {
                // Parallel particle position update â€” only worth it above ~300 particles
                // (thread submission + latch overhead exceeds benefit for small counts)
                int chunkSize = (pSize + THREAD_COUNT - 1) / THREAD_COUNT;
                CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
                for (int t = 0; t < THREAD_COUNT; t++) {
                    final int start = t * chunkSize;
                    final int end = Math.min(start + chunkSize, pSize);
                    updateThreadPool.submit(() -> {
                        try {
                            for (int pi = start; pi < end; pi++) {
                                particles.get(pi).update(particleDt);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                try { latch.await(); } catch (InterruptedException ignored) {}
            } else {
                for (int pi = 0; pi < pSize; pi++) {
                    particles.get(pi).update(particleDt);
                }
            }
            // Efficient compaction: move live particles to front, trim dead ones
            int writeIdx = 0;
            for (int readIdx = 0; readIdx < particles.size(); readIdx++) {
                Particle p = particles.get(readIdx);
                if (p.isAlive()) {
                    if (writeIdx != readIdx) particles.set(writeIdx, p);
                    writeIdx++;
                } else {
                    returnParticleToPool(p);
                }
            }
            if (writeIdx < particles.size()) {
                particles.subList(writeIdx, particles.size()).clear();
            }
        }
        
        // Check for shockwave collision with player (circular arc - only the visible arc segment)
        if (currentBoss != null && player != null && currentBoss.isShockwaveActive() && !currentBoss.hasShockwaveHitPlayer()) {
            double shockwaveRadius = currentBoss.getShockwaveRadius();
            double shockwaveAngle = currentBoss.getShockwaveAngle();
            double dx = player.getX() - currentBoss.getX();
            double dy = player.getY() - currentBoss.getY();
            double distanceToPlayer = Math.sqrt(dx * dx + dy * dy);
            double angleToPlayer = Math.atan2(dy, dx);
            
            // Calculate angle difference (accounting for wrap-around)
            double angleDiff = Math.abs(angleToPlayer - shockwaveAngle);
            if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;
            
            double coneAngle = Math.PI / 2; // 90 degree cone (matches visual)
            double shockwaveThickness = 25; // Narrower detection window for precise edge hits only
            
            // Check if player is within cone angle AND the wave edge is currently passing through them
            // Only hit when wave radius is close to player distance (wave is expanding through player)
            boolean inCone = angleDiff < coneAngle / 2;
            boolean atWaveEdge = distanceToPlayer >= (shockwaveRadius - 5) && distanceToPlayer <= (shockwaveRadius + shockwaveThickness);
            
            if (inCone && atWaveEdge && distanceToPlayer > 20) {
                // Apply knockback to player
                player.applyKnockback(currentBoss.getX(), currentBoss.getY(), currentBoss.getShockwaveKnockback());
                currentBoss.setShockwaveHitPlayer(); // Mark that player was hit - prevents any further hits
                screenShakeIntensity = Math.max(screenShakeIntensity, 5);
            }
        }
        
        // Check if player touches boss invulnerability shield (instant death)
        if (currentBoss != null && player != null && !bossVulnerable && !bossDeathAnimation 
                && invulnerabilityTimer > 0 && !playerInvincible && !deathSequenceActive && !debugShowcaseMode) {
            double sdx = player.getX() - currentBoss.getX();
            double sdy = player.getY() - currentBoss.getY();
            double distToShield = Math.sqrt(sdx * sdx + sdy * sdy);
            double shieldRadius = currentBoss.getSize() * 1.4; // Must match Renderer shield radius
            double shieldThickness = 25; // Detection band around the shield ring
            
            if (distToShield >= (shieldRadius - shieldThickness) && distToShield <= (shieldRadius + shieldThickness)) {
                // Player touched the shield - instant death
                screenShakeIntensity = Math.max(screenShakeIntensity, 12);
                soundManager.playSound(SoundManager.Sound.ELECTRIC_ZAP);
                handlePlayerDeath();
            }
        }
        
        // Check if player hit boss (only vulnerable during special window)
        // In showcase mode, boss cannot be damaged
        if (currentBoss != null && player != null && player.collidesWith(currentBoss) && !bossDeathAnimation && !debugShowcaseMode && !deathSequenceActive) {
            if (bossVulnerable) {
                // Tutorial: block boss hits before the "Defeat the Boss" step
                if (tutorialMode && tutorialStep != 5) {
                    // Teleport player back to respawn point and show message
                    if (comboSystem != null) {
                        comboSystem.setAnnouncement("COMPLETE THE CURRENT STEP FIRST!", 
                            (double)(WIDTH / 2), (double)(HEIGHT / 2));
                    }
                    if (player != null) {
                        player.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT - 200);
                        player.resetVelocity();
                    }
                    bossVulnerable = false; // End vulnerability window
                } else {
                // Trigger wobble effect immediately on hit
                currentBoss.triggerWobble();
                
                soundManager.playSoundSpatial(SoundManager.Sound.BOSS_HIT, 1.0f, currentBoss.getX(), WORLD_WIDTH);
                
                // Check for critical strike (instant kill)
                double critChance = passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.CRITICAL_HIT);
                boolean isCritical = Math.random() < critChance;
                
                if (isCritical) {
                    // Instant kill the boss
                    while (currentBoss.getCurrentHealth() > 0) {
                        currentBoss.takeDamage(true); // Hit by player missile
                    }
                    
                    // Show critical hit message using combo announcement system
                    if (comboSystem != null) {
                        comboSystem.setAnnouncement("CRITICAL HIT!", 
                            WIDTH / 2.0, HEIGHT / 2.0);
                    }
                } else {
                    // Deal normal damage to boss using new health system
                    currentBoss.takeDamage(true); // Hit by player missile
                }
                
                int remainingHealth = currentBoss.getCurrentHealth();
                
                // Show popup text
                if (remainingHealth > 0) {
                    // Animated announcement for boss HP (same style as other popups)
                    if (comboSystem != null) {
                        comboSystem.setAnnouncement("BOSS HP: " + remainingHealth, 
                            currentBoss.getX(), currentBoss.getY());
                    }
                } else {
                    // Big dramatic announcement for boss defeated
                    if (comboSystem != null) {
                        comboSystem.setAnnouncement("BOSS DEFEATED!", 
                            WIDTH / 2.0, HEIGHT / 2.0);
                    }
                }
                
                // Trigger flash effect and sound
                bossHitFlashTimer = remainingHealth > 0 ? 18 : 30;
                soundManager.playSound(remainingHealth > 0 ? 
                    SoundManager.Sound.BOSS_HIT_CONFIRMED : 
                    SoundManager.Sound.BOSS_FINAL_HIT);
                
                // Increment hit counter (for old visual effects)
                bossHitCount++;
                
                // Cancel any active item effects on boss hit
                ActiveItem equippedOnHit = gameData.getEquippedItem();
                if (equippedOnHit != null && equippedOnHit.isActive()) {
                    equippedOnHit.setActive(false);
                    
                    // Stop lingering SFX for specific items
                    if (equippedOnHit.getType() == ActiveItem.ItemType.STUN) {
                        soundManager.stopSound(SoundManager.Sound.ELECTRIC_ZAP);
                    }
                }
                // Reset frost beam state on boss hit
                frostBeamExtending = false;
                frostBeamRetracting = false;
                frostBeamProgress = 0;
                frostBeamRetractPhase = 0;
                frostBeamShakeTriggered = false;
                frostBeamStopDistance = -1;
                // Reset boss stun on boss hit
                bossStunned = false;
                bossStunShakeOffset = 0;
                // Reset shield on boss hit
                shieldActive = false;
                shieldHits = 0;
                
                // Progressive damage effects - more smoke and fire with each hit
                int particleMultiplier = bossHitCount; // 1x, 2x, 3x particles
                
                // Save collision point BEFORE player teleport (used later for bomb detonation)
                double collisionX = (player.getX() + currentBoss.getX()) / 2;
                double collisionY = (player.getY() + currentBoss.getY()) / 2;
                
                // Create impact particles at collision point (between player and boss)
                if (enableParticles) {
                    double impactX = collisionX;
                    double impactY = collisionY;
                    
                    System.out.println("DEBUG BOSS HIT #" + bossHitCount + ": playerY=" + (int)player.getY() 
                        + " bossY=" + (int)currentBoss.getY() + " impactY=" + (int)impactY 
                        + " cameraY=" + (int)cameraY + " screenImpactY=" + (int)(impactY - cameraY));
                    
                    // Bright white/yellow impact flash (scales with hit count)
                    for (int i = 0; i < 30 * particleMultiplier; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 2 + Math.random() * 6;
                        Color impactColor = Math.random() < 0.5 ? IMPACT_WHITE : IMPACT_YELLOW;
                        addParticle(
                            impactX, impactY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            impactColor, 20, 8,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Smoke particles (more with each hit) - use SMOKE type for softer look
                    for (int i = 0; i < 8 * particleMultiplier; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 0.3 + Math.random() * 1.2;
                        int gray = 50 + (int)(Math.random() * 40); // Vary darkness
                        addParticle(
                            currentBoss.getX() + (Math.random() - 0.5) * 30, 
                            currentBoss.getY() + (Math.random() - 0.5) * 20,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            SMOKE_GRAY, 50 + (int)(Math.random() * 20), 12 + Math.random() * 8,
                            Particle.ParticleType.SMOKE
                        );
                    }
                    
                    // Fire particles (reduced count for performance)
                    int fireCount = Math.min(15 * particleMultiplier, 30);
                    for (int i = 0; i < fireCount && particles.size() < MAX_PARTICLES; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 1 + Math.random() * 4;
                        Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            fireColor, 30, 5,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Metal debris particles (reduced count, use cached color)
                    int debrisCount = Math.min(15 * particleMultiplier, 25);
                    for (int i = 0; i < debrisCount && particles.size() < MAX_PARTICLES; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 2 + Math.random() * 5;
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            METAL_DEBRIS, 25, 4,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Sparks from plane damage (reduced count, use cached color)
                    int sparkCount = Math.min(20 * particleMultiplier, 40);
                    for (int i = 0; i < sparkCount && particles.size() < MAX_PARTICLES; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 3 + Math.random() * 6;
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            SPARK_YELLOW, 20, 3,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Large explosion rings at impact (reduced count, use cached color)
                    for (int i = 0; i < 3 && particles.size() < MAX_PARTICLES; i++) {
                        addParticle(
                            impactX, impactY, 0, 0,
                            FIRE_ORANGE, 
                            40 + i * 10, 
                            40 + i * 25 + (particleMultiplier * 10),
                            Particle.ParticleType.EXPLOSION
                        );
                    }
                }
                
                // === BOMB DETONATION EXPLOSION (every hit) ===
                if (enableParticles) {
                    // EXHAUST fireball bloom (60 pieces — white-hot core to deep red)
                    for (int i = 0; i < 60; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 1 + Math.random() * 7;
                        Color fireColor;
                        double r = Math.random();
                        if (r < 0.2) fireColor = new Color(255, 255, 230);
                        else if (r < 0.45) fireColor = new Color(255, 220, 60);
                        else if (r < 0.7) fireColor = FIRE_ORANGE;
                        else fireColor = FIRE_RED;
                        addParticle(collisionX, collisionY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            fireColor, 45 + (int)(Math.random() * 25), 10 + Math.random() * 6,
                            Particle.ParticleType.EXHAUST);
                    }
                    
                    // SMOKE cloud (25 pieces)
                    for (int i = 0; i < 25; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 0.2 + Math.random() * 2.0;
                        int gray = 40 + (int)(Math.random() * 50);
                        addParticle(
                            collisionX + (Math.random() - 0.5) * 25, collisionY + (Math.random() - 0.5) * 25,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            new Color(gray, gray, gray, 190), 70 + (int)(Math.random() * 25), 14 + Math.random() * 8,
                            Particle.ParticleType.SMOKE);
                    }
                    
                    // EXPLOSION shockwave rings (8 expanding rings)
                    for (int i = 0; i < 8; i++) {
                        addParticle(collisionX, collisionY, 0, 0,
                            new Color(255, Math.max(0, 240 - i * 30), Math.max(0, 80 - i * 10), Math.max(50, 240 - i * 25)),
                            30 + i * 7, 50 + i * 30,
                            Particle.ParticleType.EXPLOSION);
                    }
                    
                    // SPARK streaks (35 fast radiating sparks)
                    for (int i = 0; i < 35; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 5 + Math.random() * 9;
                        Color sparkColor;
                        double r = Math.random();
                        if (r < 0.4) sparkColor = new Color(255, 255, 200);
                        else if (r < 0.7) sparkColor = new Color(255, 200, 80);
                        else sparkColor = FIRE_ORANGE;
                        addParticle(collisionX, collisionY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            sparkColor, 20, 2 + Math.random() * 3,
                            Particle.ParticleType.SPARK);
                    }
                    
                    // DEBRIS fragments (20 spinning missile body pieces)
                    for (int i = 0; i < 20; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 1.5 + Math.random() * 5;
                        Color debrisColor = Math.random() < 0.5 ? METAL_DEBRIS : PLAYER_DEATH_RED;
                        addParticle(collisionX, collisionY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            debrisColor, 65, 5 + Math.random() * 7,
                            Particle.ParticleType.DEBRIS);
                    }
                }
                
                // Hit-pause: freeze frames on boss damage (more frames for more hits)
                hitFreezeFrames = 3 + bossHitCount * 2;
                
                // Hold camera at collision point so explosion is clearly visible
                // (camera stays locked during freeze + this hold before following player)
                bossHitCameraHoldTimer = 25;
                
                // Reset vulnerability
                bossVulnerable = false;
                invulnerabilityTimer = 300; // 5 seconds of invulnerability
                
                // Teleport player back to spawn so boss recovers (fires) before player is back
                player.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT - 200);
                player.resetVelocity();
                playerInvincible = true;
                respawnInvincibilityTimer = 120; // 2 seconds (shorter than death's 3s)
                respawnBlinkTimer = 120;
                spawnProtectionX = player.getX();
                spawnProtectionY = player.getY();
                
                screenShakeIntensity = 20 + (bossHitCount * 8); // More shake with each hit
                bossFlashTimer = 12; // Longer boss flash effect
                
                // Check if boss is defeated using new health system
                if (currentBoss.isDead()) {
                    // Roguelike: Track boss defeat for stats
                    gameData.onBossDefeated();
                    
                    // Tutorial: Boss defeated — advance to next step
                    if (tutorialMode && tutorialStep == 5) {
                        tutorialStepCompleted = true;
                        tutorialSlowdownPhase = 1;
                        tutorialSlowdownTimer = 40;
                        slowMotionFactor = 0.15;
                        slowMotionTimer = 999;
                    }
                    
                    // Track perfect boss kill for achievements
                    if (!tookDamageThisBoss) {
                        consecutivePerfectBosses++;
                        achievementManager.incrementProgress(Achievement.AchievementType.PERFECT_BOSS, 1);
                        achievementManager.incrementProgress(Achievement.AchievementType.NO_DAMAGE, 1);
                    } else {
                        consecutivePerfectBosses = 0;
                    }
                    
                    // Update achievements
                    achievementManager.incrementProgress(Achievement.AchievementType.BOSS_KILLS, 1);
                    achievementManager.updateProgress(Achievement.AchievementType.REACH_LEVEL, gameData.getCurrentLevel());
                    achievementManager.updateProgress(Achievement.AchievementType.GRAZE_COUNT, totalGrazesThisRun);
                    achievementManager.updateProgress(Achievement.AchievementType.HIGH_COMBO, comboSystem.getMaxCombo());
                    
                    // Check for Raw Dog achievement (no upgrades purchased)
                    if (hasNoUpgradesPurchased()) {
                        achievementManager.updateProgress(Achievement.AchievementType.NO_UPGRADES, gameData.getCurrentLevel());
                    }
                    
                    // Check for newly unlocked achievements
                    List<Achievement> newlyUnlocked = achievementManager.getRecentlyUnlocked();
                    if (!newlyUnlocked.isEmpty()) {
                        pendingAchievements.addAll(newlyUnlocked);
                        achievementNotificationTimer = ACHIEVEMENT_NOTIFICATION_DURATION;
                        achievementFlashTimer = 20; // Flash effect for achievement
                        soundManager.playSound(SoundManager.Sound.ACHIEVEMENT_UNLOCKED);
                        // Record into global achievements
                        if (achievementManager.recordGlobalUnlocks(newlyUnlocked, globalSaveData)) {
                            saveManager.saveGlobal(globalSaveData);
                        }
                        achievementManager.clearRecentlyUnlocked();
                    }
                    
                    // Award points and money with passive multipliers
                    int winBonus = 1000 + (gameData.getCurrentLevel() * 500);
                    // Apply combo multiplier
                    winBonus = (int)(winBonus * comboSystem.getMultiplier());
                    // Apply score multiplier passive
                    winBonus = (int)(winBonus * passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.MONEY_AND_SCORE));
                    // Apply speed bonus: faster completion = higher score
                    // Par time = 55 + level * 3 seconds; finishing faster gives up to 3x, slow runs floor at 0.5x
                    double parTime = 55.0 + gameData.getCurrentLevel() * 3.0;
                    double speedMultiplier = Math.max(0.5, Math.min(3.0, 2.0 - (gameTimeSeconds / parTime)));
                    winBonus = (int)(winBonus * speedMultiplier);
                    
                    // Missile survival bonus: remaining missiles (not extra lives) grant bonus score
                    // Each surviving missile = 250 + level * 50 points
                    int missilesRemaining = gameData.getMissiles();
                    int missileSurvivalBonus = 0;
                    if (missilesRemaining > 0) {
                        missileSurvivalBonus = missilesRemaining * (250 + gameData.getCurrentLevel() * 50);
                        missileSurvivalBonus = (int)(missileSurvivalBonus * passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.MONEY_AND_SCORE));
                        winBonus += missileSurvivalBonus;
                        gameData.getCurrentLevelStats().setMissileSurvivalBonus(missileSurvivalBonus);
                    }
                    
                    gameData.addScore(winBonus);
                    
                    int moneyReward = currentBoss.getMoneyReward();
                    
                    // Apply money gain passive multiplier
                    moneyReward = (int)(moneyReward * passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.MONEY_AND_SCORE));
                    
                    gameData.addRunMoney(moneyReward);
                    // Money only added to wallet on level completion
                    
                    // Update money achievement
                    achievementManager.updateProgress(Achievement.AchievementType.MONEY_EARNED, gameData.getTotalMoney());
                    
                    // Save level completion time and stats
                    int levelTimeInFrames = (int)(gameTimeSeconds * 60);
                    gameData.setLevelCompletionTime(gameData.getCurrentLevel(), levelTimeInFrames);
                    gameData.getCurrentLevelStats().setTimeInFrames(levelTimeInFrames);
                    gameData.getCurrentLevelStats().setDodges(comboSystem.getTotalDodges());
                    gameData.getCurrentLevelStats().setPerfectDodges(comboSystem.getPerfectDodgeCount());
                    gameData.getCurrentLevelStats().setMaxCombo(comboSystem.getMaxCombo());
                    // Bullets spawned, near misses, damage taken, and closest call are already being tracked throughout the level
                    gameData.saveLevelStats(gameData.getCurrentLevel());
                    
                    // Check speedrun achievements (each achievement checks if time is under its threshold)
                    for (Achievement speedAchievement : achievementManager.getAllAchievements()) {
                        if (speedAchievement.getType() == Achievement.AchievementType.SPEED_RUN && 
                            !speedAchievement.isUnlocked() && 
                            levelTimeInFrames <= speedAchievement.getTarget()) {
                            speedAchievement.unlock();
                            achievementManager.getRecentlyUnlocked().add(speedAchievement);
                        }
                    }
                    
                    // Check clutch survival achievement (used 5+ missiles and survived on last one)
                    if (missilesUsedThisRun >= 5 && gameData.getMissiles() == 1) {
                        achievementManager.updateProgress(Achievement.AchievementType.CLUTCH_SURVIVAL, missilesUsedThisRun);
                    }
                    
                    // Record any speed/clutch achievements into global save
                    List<Achievement> lateUnlocked = achievementManager.getRecentlyUnlocked();
                    if (!lateUnlocked.isEmpty()) {
                        pendingAchievements.addAll(lateUnlocked);
                        if (achievementNotificationTimer <= 0) {
                            achievementNotificationTimer = ACHIEVEMENT_NOTIFICATION_DURATION;
                            achievementFlashTimer = 20;
                            soundManager.playSound(SoundManager.Sound.ACHIEVEMENT_UNLOCKED);
                        }
                        if (achievementManager.recordGlobalUnlocks(lateUnlocked, globalSaveData)) {
                            saveManager.saveGlobal(globalSaveData);
                        }
                        achievementManager.clearRecentlyUnlocked();
                    }
                    
                    // Submit to leaderboard (campaign mode only, not endless, not debug skipped)
                    if (!debugSkipUsed && !gameData.isInEndlessMode() && gameData.getCurrentLevel() >= 1 && gameData.getCurrentLevel() <= Game.CAMPAIGN_LEVELS) {
                        String saveName = gameData.getCustomSaveName() != null ?
                            gameData.getCustomSaveName() : "Save " + saveManager.getCurrentSaveSlot();
                        leaderboardCompletedLevel = gameData.getCurrentLevel();
                        leaderboardCompletedDifficulty = gameData.getGameMode();
                        leaderboardManager.submitTime(gameData.getGameMode(), gameData.getCurrentLevel(), levelTimeInFrames, saveName);
                        // Save updated leaderboard to global
                        leaderboardManager.saveToGlobal(globalSaveData);
                        saveManager.saveGlobal(globalSaveData);
                    }
                    
                    // Start boss death animation
                    soundManager.playSound(SoundManager.Sound.BOSS_DEATH);
                    bossDeathAnimation = true;
                    deathAnimationTimer = DEATH_ANIMATION_DURATION;
                    bossDeathScale = 1.0;
                    bossDeathRotation = 0;
                    bossKillTime = gameTimeSeconds;
                    
                    // Make player disappear (missile hit)
                    player = null;
                    
                    // Massive final explosion — bomb detonation
                    screenShakeIntensity = 35;
                
                if (enableParticles) {
                    double boomX = currentBoss.getX();
                    double boomY = currentBoss.getY();
                    
                    // EXHAUST fireball bloom (80 pieces — white-hot core fading to deep red)
                    for (int i = 0; i < 80; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 1.5 + Math.random() * 9;
                        Color fireColor;
                        double r = Math.random();
                        if (r < 0.2) fireColor = new Color(255, 255, 230);      // White-hot core
                        else if (r < 0.45) fireColor = new Color(255, 220, 60); // Bright yellow
                        else if (r < 0.7) fireColor = FIRE_ORANGE;              // Orange
                        else fireColor = FIRE_RED;                               // Deep red
                        addParticle(boomX, boomY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            fireColor, 55 + (int)(Math.random() * 30), 10 + Math.random() * 8,
                            Particle.ParticleType.EXHAUST);
                    }
                    
                    // SMOKE cloud (30 pieces — thick dark smoke billowing out)
                    for (int i = 0; i < 30; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 0.3 + Math.random() * 2.5;
                        int gray = 40 + (int)(Math.random() * 50);
                        addParticle(
                            boomX + (Math.random() - 0.5) * 30, boomY + (Math.random() - 0.5) * 30,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            new Color(gray, gray, gray, 200), 80 + (int)(Math.random() * 30), 16 + Math.random() * 10,
                            Particle.ParticleType.SMOKE);
                    }
                    
                    // EXPLOSION shockwave rings (10 expanding rings)
                    for (int i = 0; i < 10; i++) {
                        addParticle(boomX, boomY, 0, 0,
                            new Color(255, Math.max(0, 240 - i * 25), Math.max(0, 80 - i * 8), Math.max(40, 240 - i * 22)),
                            35 + i * 8, 60 + i * 35,
                            Particle.ParticleType.EXPLOSION);
                    }
                    
                    // SPARK streaks (50 fast radiating sparks)
                    for (int i = 0; i < 50; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 5 + Math.random() * 12;
                        Color sparkColor;
                        double r = Math.random();
                        if (r < 0.4) sparkColor = new Color(255, 255, 200);
                        else if (r < 0.7) sparkColor = new Color(255, 200, 80);
                        else sparkColor = FIRE_ORANGE;
                        addParticle(boomX, boomY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            sparkColor, 22, 2 + Math.random() * 3,
                            Particle.ParticleType.SPARK);
                    }
                    
                    // DEBRIS fragments (30 spinning missile body pieces)
                    for (int i = 0; i < 30; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 2 + Math.random() * 6;
                        Color debrisColor = Math.random() < 0.5 ? METAL_DEBRIS : PLAYER_DEATH_RED;
                        addParticle(boomX, boomY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            debrisColor, 70, 5 + Math.random() * 8,
                            Particle.ParticleType.DEBRIS);
                    }
                }
                } else {
                    // Non-fatal hit - delay respawn and show bomb detonation
                    double hitX = collisionX;
                    double hitY = collisionY;
                    player = null; // Remove player temporarily
                    waitingForRespawn = true;
                    respawnDelayTimer = RESPAWN_DELAY;
                    
                    // Massive screen shake for bomb detonation
                    screenShakeIntensity = 25 + (bossHitCount * 8);
                    
                    // === BLAST RADIUS — destroy nearby bullets in the bomb explosion ===
                    // Large blast radius so it visibly clears bullets around the detonation
                    double blastRadius = 250;
                    double blastRadiusSq = blastRadius * blastRadius;
                    int bulletsDestroyed = 0;
                    for (int bi = bullets.size() - 1; bi >= 0; bi--) {
                        Bullet b = bullets.get(bi);
                        double dx = b.getX() - hitX;
                        double dy = b.getY() - hitY;
                        if (dx * dx + dy * dy < blastRadiusSq) {
                            // Spark at each destroyed bullet position (cap particles at 40)
                            if (enableParticles && bulletsDestroyed < 40) {
                                addParticle(b.getX(), b.getY(),
                                    (Math.random() - 0.5) * 4, (Math.random() - 0.5) * 4,
                                    FIRE_YELLOW, 18, 5,
                                    Particle.ParticleType.SPARK);
                            }
                            bullets.remove(bi);
                            returnBulletToPool(b);
                            bulletsDestroyed++;
                        }
                    }
                    
                    // Reset vulnerability
                    bossVulnerable = false;
                    invulnerabilityTimer = 300; // 5 seconds of invulnerability
                }
                
                return;
                } // end tutorial else (normal boss hit)
            } else {
                // Hit boss when not vulnerable - player dies (only if not invincible)
                if (!playerInvincible) {
                    handlePlayerDeath();
                    return;
                }
            }
        }
        
        // Update boss death animation
        if (bossDeathAnimation) {
            deathAnimationTimer -= deltaTime;
            // Tick announcement timer during death animation so CRITICAL HIT! text animates
            if (comboSystem != null) comboSystem.tickAnnouncement(deltaTime);
            
            // Calculate animation progress (0 to 1)
            double progress = 1.0 - (deathAnimationTimer / (double)DEATH_ANIMATION_DURATION);
            
            // Boss shrinks and falls (scale decreases)
            bossDeathScale = 1.0 - (progress * 0.7); // Shrink to 30% size
            
            // Boss spins as it falls
            bossDeathRotation += 0.05 * deltaTime;
            
            // Continuous explosions during death
            if (enableParticles && Math.random() < 0.15 * deltaTime) {
                double offsetX = (Math.random() - 0.5) * 80 * bossDeathScale;
                double offsetY = (Math.random() - 0.5) * 80 * bossDeathScale;
                for (int i = 0; i < 15; i++) {
                    double angle = Math.random() * TWO_PI;
                    double speed = 1 + Math.random() * 4;
                    Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                    addParticle(
                        currentBoss.getX() + offsetX, currentBoss.getY() + offsetY,
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        fireColor, 30, 4,
                        Particle.ParticleType.SPARK
                    );
                }
            }
            
            // Continuous screen shake that decreases over time
            screenShakeIntensity = 15 * (1.0 - progress);
            
            // Smoke trails
            if (enableParticles && Math.random() < 0.3 * deltaTime) {
                addParticle(
                    currentBoss.getX() + (Math.random() - 0.5) * 60,
                    currentBoss.getY() + (Math.random() - 0.5) * 60,
                    (Math.random() - 0.5) * 2, 2 + Math.random() * 3,
                    SMOKE_GRAY, 40, 8,
                    Particle.ParticleType.SPARK
                );
            }
            
            // Final explosion and transition to win screen
            if (deathAnimationTimer <= 0) {
                // Final massive explosion - reduce particle count for performance
                if (enableParticles) {
                    for (int i = 0; i < 50 && particles.size() < MAX_PARTICLES; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double speed = 2 + Math.random() * 6;
                        Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            fireColor, 60, 8,
                            Particle.ParticleType.SPARK
                        );
                    }
                }
                
                screenShakeIntensity = 20;
                
                // Check if this is a mega boss (every 3rd level)
                int currentLevel = gameData.getCurrentLevel();
                
                // Check if this is level 7 (currentLevel == 6, 0-indexed)
                // Unlock risk contracts when defeating level 7 boss
                if (currentLevel == 6 && !gameData.areContractsUnlocked()) {
                    // Unlock risk contracts permanently!
                    gameData.unlockContracts();
                    // We'll show the contract animation in the WIN state
                }
                
                // Use max unlocked level to determine item unlocks (not current level)
                int maxLevel = Math.max(gameData.getMaxUnlockedLevel(), currentLevel);
                int expectedItems = maxLevel / 3; // Items unlock every 3 levels
                int currentItemCount = gameData.getUnlockedItems().size();
                if (expectedItems > currentItemCount) {
                    // Unlock all missing items up to the max level
                    int toUnlock = expectedItems - currentItemCount;
                    for (int u = 0; u < toUnlock; u++) {
                        gameData.unlockNextItem();
                    }
                    // Get the newly unlocked item for display
                    java.util.List<ActiveItem.ItemType> unlockedItems = gameData.getUnlockedItems();
                    if (!unlockedItems.isEmpty()) {
                        ActiveItem newItem = new ActiveItem(unlockedItems.get(unlockedItems.size() - 1));
                        unlockedItemName = newItem.getName();
                        unlockedItemDescription = newItem.getDescription(); // Store description
                    }
                    // Equip first item if this is the first unlock
                    if (currentItemCount == 0 && unlockedItems.size() >= 1) {
                        System.out.println("DEBUG: First item unlocked, auto-equipping");
                        gameData.equipItem(0);
                        showEquipPrompt = false;
                    } else {
                        // Show equip prompt for subsequent items
                        System.out.println("DEBUG: Item " + unlockedItems.size() + " unlocked, showing equip prompt");
                        showEquipPrompt = true;
                        newItemIndex = unlockedItems.size() - 1;
                        selectedEquipButton = 0; // Default to "Yes"
                    }
                    // Trigger animation
                    soundManager.playSound(SoundManager.Sound.ITEM_PICKUP);
                    itemUnlockAnimation = true;
                    itemUnlockTimer = ITEM_UNLOCK_DURATION;
                    System.out.println("DEBUG: Item unlock animation started, showEquipPrompt=" + showEquipPrompt);
                }
                
                soundManager.playSound(SoundManager.Sound.LEVEL_COMPLETE);
                soundManager.stopMusic();
                
                // Tutorial mode: skip WIN screen, go directly to SHOP
                if (tutorialMode) {
                    bossDeathAnimation = false;
                    shopEnteredFrom = GameState.PLAYING;
                    shopManager.rebuildSortedOrder();
                    transitionToState(GameState.SHOP);
                    // Show "Welcome to the Shop" popup now that we're actually in the shop
                    showTutorialPopup(6);
                    return;
                }
                
                gameState = GameState.WIN;
                if (renderer != null) renderer.setScreenEnteredTime(gradientTime);
                bossDeathAnimation = false;
                hasSavedGame = false; // Clear saved game on win so purchases persist
                
                // === Process campaign/endless progression NOW (before auto-save) ===
                if (!gameData.isInEndlessMode()) {
                    // Campaign mode: mark boss defeated, award money, unlock next level
                    int bossReward = 50 + (currentLevel * 10);
                    if (!gameData.getDefeatedBosses()[currentLevel - 1]) {
                        gameData.setBossDefeated(currentLevel - 1, true);
                        bossReward += 100;
                    }
                    
                    System.out.println("DEBUG WIN: Level " + currentLevel + " completed, money before: " + gameData.getTotalMoney() + ", reward: " + bossReward);
                    gameData.addRunMoney(bossReward);
                    gameData.addTotalMoney(gameData.getRunMoney());
                    gameData.setRunMoney(0);
                    System.out.println("DEBUG WIN: Money after reward: " + gameData.getTotalMoney());
                    
                    int nextLevel = currentLevel + 1;
                    if (DEMO_MODE) nextLevel = Math.min(nextLevel, DEMO_MAX_LEVEL);
                    gameData.setMaxUnlockedLevel(Math.max(gameData.getMaxUnlockedLevel(), nextLevel));
                    gameData.setCurrentLevel(nextLevel);
                    
                    // Backfill all boss defeats when beating the final campaign level
                    if (currentLevel >= CAMPAIGN_LEVELS) {
                        for (int i = 0; i < CAMPAIGN_LEVELS; i++) {
                            if (!gameData.getDefeatedBosses()[i]) {
                                gameData.setBossDefeated(i, true);
                                System.out.println("DEBUG: Backfilled defeatedBosses[" + i + "]");
                            }
                        }
                    }
                    
                    // Check if campaign is now complete (unlocks endless mode)
                    if (gameData.hasCompletedCampaign() && !gameData.hasSeenEndlessUnlock()) {
                        gameData.setEndlessUnlocked(true);
                        System.out.println("DEBUG: Campaign complete! Endless mode unlocked!");
                    }
                } else {
                    // Endless mode: track progression
                    int endlessLevel = gameData.getEndlessCurrentLevel();
                    int effectiveLevel = gameData.getEndlessEffectiveLevel();
                    int bossReward = 50 + (effectiveLevel * 10);
                    gameData.addRunMoney(bossReward);
                    gameData.addTotalMoney(gameData.getRunMoney());
                    gameData.setRunMoney(0);
                    
                    int totalBeaten = gameData.getTotalEndlessLevelsBeaten() + 1;
                    if (totalBeaten > gameData.getEndlessHighestLevel()) {
                        gameData.setEndlessHighestLevel(totalBeaten);
                    }
                    achievementManager.updateProgress(Achievement.AchievementType.ENDLESS_LEVELS, totalBeaten);
                    
                    if (endlessLevel >= CAMPAIGN_LEVELS) {
                        gameData.incrementEndlessPrestige();
                        gameData.setEndlessCurrentLevel(1);
                        System.out.println("DEBUG ENDLESS: Prestige! Now prestige " + gameData.getEndlessPrestige());
                    } else {
                        gameData.setEndlessCurrentLevel(endlessLevel + 1);
                    }
                    System.out.println("DEBUG ENDLESS WIN: level " + endlessLevel + " beaten, next=" + gameData.getEndlessCurrentLevel());
                }
                
                // If level 7 was defeated and contracts were unlocked, trigger animation (only once)
                if (currentLevel == 6 && gameData.areContractsUnlocked() && !itemUnlockAnimation && !gameData.hasSeenContractUnlock()) {
                    soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                    contractUnlockAnimation = true;
                    contractUnlockTimer = CONTRACT_UNLOCK_DURATION;
                    gameData.setSeenContractUnlock(true);
                }
                
                // If campaign just completed, trigger endless unlock popup
                if (gameData.hasCompletedCampaign() && !gameData.hasSeenEndlessUnlock() && !itemUnlockAnimation && !contractUnlockAnimation) {
                    soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                    endlessUnlockAnimation = true;
                    endlessUnlockTimer = ENDLESS_UNLOCK_DURATION;
                    gameData.setSeenEndlessUnlock(true);
                }
                
                // Auto-save after all flags are set so popup states persist
                performAutoSave();
                
                return;
            }
        }
        
        // Simple invulnerability system - boss is vulnerable after timer expires
        if (invulnerabilityTimer > 0) {
            invulnerabilityTimer -= deltaTime; // Countdown scaled by delta time
            bossVulnerable = false;
        } else {
            bossVulnerable = true; // Boss is always vulnerable when not in immunity period
        }
        
        // Update boss with delta time (but not during death animation, intro, respawn delay, stun, or boss intro cinematic)
        // During death sequence, boss fires at full speed so player respawns into active bullets
        // Apply TIME_SLOW factor to boss delta so boss also moves slower
        double bossDt = deathSequenceActive ? deltaTime : dt;
        ActiveItem equippedForBoss = gameData.getEquippedItem();
        if (equippedForBoss != null && equippedForBoss.isActive() && equippedForBoss.getType() == ActiveItem.ItemType.TIME_SLOW) {
            bossDt *= 0.15; // 15% speed (85% slow) — same factor as bullets/beams
        }
        if (currentBoss != null && !bossDeathAnimation && !introPanActive && !bossIntroActive && player != null && !bossStunned && bossHitCameraHoldTimer <= 0) {
            int bulletCountBefore = bullets.size();
            currentBoss.update(bullets, player, WORLD_WIDTH, WORLD_HEIGHT, bossDt, particles);
            beamAttacks = currentBoss.getBeamAttacks();
            
            // Continuous smoke/fire particles on damaged boss
            if (enableParticles) {
                float bossHpPct = currentBoss.getHealthPercent();
                if (bossHpPct < 0.7f) {
                    float dmgRatio = 1.0f - bossHpPct;
                    // Emit more particles as damage increases (up to ~40% chance/frame)
                    if (Math.random() < dmgRatio * 0.5) {
                        double bx = currentBoss.getX() + (Math.random() - 0.5) * currentBoss.getSize() * 0.8;
                        double by = currentBoss.getY() + (Math.random() - 0.5) * currentBoss.getSize() * 0.5;
                        if (Math.random() < 0.6) {
                            // Dark smoke
                            int gray = 50 + (int)(Math.random() * 40);
                            addParticle(bx, by, (Math.random()-0.5)*0.8, -0.5 - Math.random()*1.0,
                                new Color(gray, gray, gray, 180), 50, 6 + Math.random()*5,
                                Particle.ParticleType.SMOKE);
                        } else {
                            // Fire/exhaust
                            addParticle(bx, by, (Math.random()-0.5)*0.6, -0.3 - Math.random()*0.8,
                                new Color(255, 130 + (int)(Math.random()*80), 20, 200), 35, 4 + Math.random()*3,
                                Particle.ParticleType.EXHAUST);
                        }
                    }
                }
            }
            
            // Track bullets spawned for stats
            int bulletsSpawned = bullets.size() - bulletCountBefore;
            if (bulletsSpawned > 0) {
                for (int i = 0; i < bulletsSpawned; i++) {
                    gameData.getCurrentLevelStats().incrementBulletsSpawned();
                }
            }
            
            // Apply risk contract effects to newly spawned bullets
            if (riskContractType > 0 && bullets.size() > bulletCountBefore) {
                List<Bullet> newBullets = new java.util.ArrayList<>();
                for (int i = bulletCountBefore; i < bullets.size(); i++) {
                    Bullet bullet = bullets.get(i);
                    
                    // Speed Demon: 50% faster bullets
                    if (riskContractType == 2) {
                        double speedMult = 1.5;
                        bullet.multiplySpeed(speedMult);
                    }
                    
                    // Bullet Storm: duplicate bullets with slight offset
                    if (riskContractType == 1) {
                        Bullet duplicate = getBulletFromPool();
                        duplicate.reset(
                            bullet.getX() + (Math.random() - 0.5) * 10,
                            bullet.getY() + (Math.random() - 0.5) * 10,
                            bullet.getVX() * (0.9 + Math.random() * 0.2),
                            bullet.getVY() * (0.9 + Math.random() * 0.2),
                            bullet.getType()
                        );
                        newBullets.add(duplicate);
                    }
                }
                bullets.addAll(newBullets);
            }
            
            // Add continuous flame and smoke particles from damaged boss
            if (bossHitCount > 0 && enableParticles) {
                // More frequent particles with each hit
                double spawnChance = 0.2 * bossHitCount; // 20% per hit level
                
                if (Math.random() < spawnChance) {
                    // Flame particles
                    double angle = Math.PI / 2 + (Math.random() - 0.5) * 0.8; // Downward
                    double speed = 0.5 + Math.random() * 1.5;
                    Color flameColor = Math.random() < 0.6 ? FIRE_ORANGE : FIRE_RED;
                    addParticle(
                        currentBoss.getX() + (Math.random() - 0.5) * 40,
                        currentBoss.getY() + (Math.random() - 0.5) * 30,
                        Math.cos(angle) * speed,
                        Math.sin(angle) * speed,
                        flameColor, 35 + (int)(Math.random() * 20), 6 + Math.random() * 4,
                        Particle.ParticleType.TRAIL
                    );
                }
                
                if (Math.random() < spawnChance * 0.7) {
                    // Smoke particles (darker, slower) - use SMOKE type for softer look
                    double angle = Math.PI / 2 + (Math.random() - 0.5) * 0.6;
                    double speed = 0.2 + Math.random() * 0.8;
                    addParticle(
                        currentBoss.getX() + (Math.random() - 0.5) * 35,
                        currentBoss.getY() + (Math.random() - 0.5) * 25,
                        Math.cos(angle) * speed,
                        Math.sin(angle) * speed,
                        SMOKE_GRAY, 60 + (int)(Math.random() * 40), 10 + Math.random() * 6,
                        Particle.ParticleType.SMOKE
                    );
                }
            }
        } else if (currentBoss != null && bossHitCameraHoldTimer > 0) {
            // Boss frozen during camera hold, but keep visual animations (helicopter blades)
            currentBoss.updateAnimations(deltaTime);
        }
        
        // Handle respawn delay after non-fatal boss hit
        if (waitingForRespawn) {
            respawnDelayTimer -= deltaTime;
            
            if (respawnDelayTimer <= 0) {
                // Respawn player at bottom with invincibility (not shield item)
                soundManager.playSound(SoundManager.Sound.PLAYER_RESPAWN);
                int speedLevel = getActiveSpeedLevel();
                player = new Player(WORLD_WIDTH / 2, WORLD_HEIGHT - 200, speedLevel, keyBindManager, controllerManager);
                // Don't activate shield here - use invincibility instead
                // shieldActive is only for the Shield active item
                playerInvincible = true;
                respawnInvincibilityTimer = 180; // 3 seconds of invincibility after respawn
                
                // Reset Can't Stop contract so player must input movement before timer starts
                hasMovedOnce = false;
                stoppedMovingTimer = 0;
                
                // Track spawn position for radius check
                spawnProtectionX = player.getX();
                spawnProtectionY = player.getY();
                
                waitingForRespawn = false;
                
                // Add respawn flash effect
                if (enableParticles) {
                    // Bright spawn flash at new player position (reduced count)
                    for (int i = 0; i < 40 && particles.size() < MAX_PARTICLES; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 3 + Math.random() * 7;
                        addParticle(
                            WORLD_WIDTH / 2, WORLD_HEIGHT - 200,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            SPAWN_CYAN, 35, 12,
                            Particle.ParticleType.SPARK
                        );
                    }
                    // Shield activation rings (reduced count)
                    for (int i = 0; i < 3 && particles.size() < MAX_PARTICLES; i++) {
                        addParticle(
                            WORLD_WIDTH / 2, WORLD_HEIGHT - 200, 0, 0,
                            SHIELD_BLUE, 40 + i * 12, 35 + i * 20,
                            Particle.ParticleType.EXPLOSION
                        );
                    }
                }
            }
        }
        
        // Check beam attack collisions (only if player exists)
        for (BeamAttack beam : beamAttacks) {
            // Apply time slow effect from active item (TIME_SLOW or STUN)
            if (equippedItem != null && equippedItem.isActive()) {
                if (equippedItem.getType() == ActiveItem.ItemType.TIME_SLOW) {
                    beam.applyTimeSlow(0.15); // 15% speed (85% slow)
                } else if (equippedItem.getType() == ActiveItem.ItemType.STUN) {
                    beam.applyTimeSlow(0.0); // Completely freeze beams during stun
                }
            }
            
            // Update beam lifecycle
            beam.update(dt);
            
            // Play beam lifecycle sounds
            if (beam.shouldPlayWarning()) {
                soundManager.playSound(SoundManager.Sound.BEAM_WARNING, 0.5f);
            }
            if (beam.shouldPlayFire()) {
                soundManager.playSound(SoundManager.Sound.EXPL_MEDIUM_1, 0.6f);
            }
            
            if (player != null && !deathSequenceActive && beam.collidesWith(player)) {
                // Check if player is invincible from respawn
                if (respawnInvincibilityTimer > 0) {
                    continue; // Skip damage during invincibility
                }
                
                // Hit by beam - handlePlayerDeath creates explosion particles
                handlePlayerDeath();
                return;
            }
        }
        
        // ===== OPTIMIZED BULLET UPDATE =====
        // Phase 1: Parallel bullet position updates (bullets are independent during movement)
        {
            final int bulletSlowLevel = getActiveBulletSlowLevel();
            final double bulletSlowMult = bulletSlowLevel > 0 ? passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.BULLET_SLOW) : 1.0;
            final boolean timeSlowActive = equippedItem != null && equippedItem.isActive() && 
                equippedItem.getType() == ActiveItem.ItemType.TIME_SLOW;
            final Player playerRef = player; // Capture for lambda
            final double bulletDt = deltaTime;
            int bSize = bullets.size();
            
            if (bSize > 500 && THREAD_COUNT > 1) {
                // Parallel position update â€” only worth it above ~500 bullets
                // (thread submission + latch overhead exceeds benefit for <500)
                int chunkSize = (bSize + THREAD_COUNT - 1) / THREAD_COUNT;
                CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
                for (int t = 0; t < THREAD_COUNT; t++) {
                    final int start = t * chunkSize;
                    final int end = Math.min(start + chunkSize, bSize);
                    updateThreadPool.submit(() -> {
                        try {
                            for (int bi = start; bi < end; bi++) {
                                Bullet bullet = bullets.get(bi);
                                bullet.resetFrameSpeedMultiplier();
                                if (bulletSlowLevel > 0) bullet.applySlow(bulletSlowMult);
                                if (timeSlowActive) bullet.applySlow(0.15);
                                bullet.update(playerRef, WORLD_WIDTH, WORLD_HEIGHT, bulletDt);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                try { latch.await(); } catch (InterruptedException ignored) {}
            } else {
                for (int i = 0; i < bSize; i++) {
                    Bullet bullet = bullets.get(i);
                    bullet.resetFrameSpeedMultiplier();
                    if (bulletSlowLevel > 0) bullet.applySlow(bulletSlowMult);
                    if (timeSlowActive) bullet.applySlow(0.15);
                    bullet.update(playerRef, WORLD_WIDTH, WORLD_HEIGHT, bulletDt);
                }
            }
        }
        
        // Phase 2: Sequential post-processing (explosions, trails, removal)
        // Collect new fragments separately to avoid modifying list during iteration
        List<Bullet> newFragments = null;
        int writeIdx = 0;
        for (int i = 0; i < bullets.size(); i++) {
            Bullet bullet = bullets.get(i);
            
            // Spawn trail particles for fast-moving bullets
            if (enableParticles && bullet.shouldSpawnTrail() && Math.random() < 0.10 * deltaTime) {
                addParticle(
                    bullet.getX(), bullet.getY(),
                    -bullet.getVX() * 0.2, -bullet.getVY() * 0.2,
                    bullet.getTrailColor(), 15, 3,
                    Particle.ParticleType.TRAIL
                );
            }
            
            // Check if explosive bullets should explode
            if (bullet.shouldExplode()) {
                // Play appropriate explosion sound based on bullet type
                Bullet.BulletType bulletType = bullet.getType();
                if (bulletType == Bullet.BulletType.BOMB || bulletType == Bullet.BulletType.GRENADE || bulletType == Bullet.BulletType.NUKE) {
                    soundManager.playSoundSpatial(SoundManager.Sound.GRENADE_EXPLODE, 0.6f, bullet.getX(), WORLD_WIDTH);
                } else {
                    SoundManager.Sound[] explosionSounds = {
                        SoundManager.Sound.EXPL_SHORT_1, SoundManager.Sound.EXPL_SHORT_2, 
                        SoundManager.Sound.EXPL_SHORT_3, SoundManager.Sound.EXPL_SHORT_4, 
                        SoundManager.Sound.EXPL_SHORT_5
                    };
                    soundManager.playSoundSpatial(explosionSounds[(int)(Math.random() * explosionSounds.length)], 0.4f, bullet.getX(), WORLD_WIDTH);
                }
                
                // Create explosion particles with shockwave (using particle pool)
                if (enableParticles) {
                    List<Particle> explosionParticles = bullet.createExplosionParticles();
                    int particlesToAdd = bullets.size() > 200 ? explosionParticles.size() / 2 : explosionParticles.size();
                    for (int j = 0; j < particlesToAdd && particles.size() < MAX_PARTICLES; j++) {
                        Particle ep = explosionParticles.get(j);
                        addParticle(ep.getX(), ep.getY(), ep.getVX(), ep.getVY(), ep.getColor(), ep.getLifetime(), ep.getSize(), ep.getType());
                    }
                }
                
                // Collect fragments to add after loop (using bullet pool)
                List<Bullet> fragments = bullet.createFragments();
                if (!fragments.isEmpty()) {
                    if (newFragments == null) newFragments = new ArrayList<>();
                    for (Bullet frag : fragments) {
                        Bullet pooled = getBulletFromPool();
                        pooled.reset(frag.getX(), frag.getY(), frag.getVX(), frag.getVY(), frag.getType());
                        pooled.setSpriteVariant(frag.getSpriteVariant());
                        pooled.setWarningTime(0); // Fragments spawn instantly
                        newFragments.add(pooled);
                    }
                }
                returnBulletToPool(bullet);
                continue; // Don't keep this bullet
            }
            
            // Check if bullet is off-screen
            if (bullet.isOffScreen(WORLD_WIDTH, WORLD_HEIGHT)) {
                returnBulletToPool(bullet);
                continue; // Don't keep this bullet
            }
            
            // Keep this bullet - compact in-place (avoids O(n) shift from remove(i))
            if (writeIdx != i) bullets.set(writeIdx, bullet);
            writeIdx++;
        }
        // Trim removed bullets from end
        if (writeIdx < bullets.size()) {
            bullets.subList(writeIdx, bullets.size()).clear();
        }
        // Add any new fragments
        if (newFragments != null) {
            bullets.addAll(newFragments);
        }
        
        // Rebuild spatial grid after all bullet updates for optimized collision
        rebuildBulletGrid();
        
        // ===== FLARE SYSTEM =====
        if (player != null && !bossDeathAnimation && !deathSequenceActive) {
            int flaresLevel = getActiveFlaresLevel();
            
            // Decrement flare cooldown
            flareCooldownTimer -= deltaTime;
            
            // Deploy flares when cooldown ready and upgrade active
            if (flareCooldownTimer <= 0 && flaresLevel > 0) {
                // Detection range scales with level: 180, 210, 240, 270, 300
                double detectionRange = 150 + flaresLevel * 30;
                double detRangeSq = detectionRange * detectionRange;
                
                // Scan for nearby homing bullets not already targeting a flare
                java.util.List<Bullet> nearbyHomingBullets = new java.util.ArrayList<>();
                for (Bullet bullet : bullets) {
                    if (bullet.getType() == Bullet.BulletType.HOMING && bullet.isActive() && !bullet.isTargetingFlare()) {
                        double dx = bullet.getX() - player.getX();
                        double dy = bullet.getY() - player.getY();
                        if (dx * dx + dy * dy < detRangeSq) {
                            nearbyHomingBullets.add(bullet);
                        }
                    }
                }
                
                if (!nearbyHomingBullets.isEmpty()) {
                    // Determine flare count from level: 1/2/3/4/5/6/7
                    int[] flareCounts = {0, 1, 2, 3, 4, 5, 6, 7};
                    int flareCount = flareCounts[Math.min(flaresLevel, 7)];
                    
                    // Calculate backward direction (opposite of player velocity)
                    double pvx = player.getVX();
                    double pvy = player.getVY();
                    double playerSpeed = Math.sqrt(pvx * pvx + pvy * pvy);
                    double backAngle;
                    if (playerSpeed > 0.5) {
                        backAngle = Math.atan2(-pvy, -pvx); // Opposite of movement
                    } else {
                        backAngle = Math.PI / 2; // Default: downward if stationary
                    }
                    
                    // Spawn flares fanned out from backward direction
                    java.util.List<Flare> newFlares = new java.util.ArrayList<>();
                    double spreadStep = flareCount > 1 ? Math.toRadians(60.0) / (flareCount - 1) : 0;
                    double startAngle = backAngle - Math.toRadians(30.0);
                    
                    for (int fi = 0; fi < flareCount; fi++) {
                        double angle = flareCount > 1 ? startAngle + fi * spreadStep : backAngle;
                        double flareSpeed = 3.0 + Math.random() * 1.0;
                        double fvx = Math.cos(angle) * flareSpeed;
                        double fvy = Math.sin(angle) * flareSpeed;
                        Flare flare = new Flare(player.getX(), player.getY(), fvx, fvy);
                        flares.add(flare);
                        newFlares.add(flare);
                        
                        // Deployment VFX: spawn FLARE_SPARK particles
                        if (enableParticles) {
                            for (int pi = 0; pi < 4; pi++) {
                                double sparkAngle = angle + (Math.random() - 0.5) * 0.8;
                                double sparkSpeed = 1.0 + Math.random() * 2.0;
                                addParticle(
                                    player.getX(), player.getY(),
                                    Math.cos(sparkAngle) * sparkSpeed, Math.sin(sparkAngle) * sparkSpeed,
                                    FLARE_RED, 20 + Math.random() * 10, 3 + Math.random() * 2,
                                    Particle.ParticleType.FLARE_SPARK
                                );
                            }
                        }
                    }
                    
                    // Retarget chance scales with level: 40%, 55%, 70%, 85%, 95%
                    double[] retargetChances = {0, 0.40, 0.55, 0.70, 0.85, 0.95};
                    double retargetChance = retargetChances[Math.min(flaresLevel, 5)];
                    
                    // For each nearby homing bullet, roll retarget chance
                    for (Bullet bullet : nearbyHomingBullets) {
                        if (Math.random() < retargetChance && !newFlares.isEmpty()) {
                            // Find nearest flare to this bullet
                            Flare nearest = null;
                            double nearestDistSq = Double.MAX_VALUE;
                            for (Flare f : newFlares) {
                                double dx = f.getX() - bullet.getX();
                                double dy = f.getY() - bullet.getY();
                                double distSq = dx * dx + dy * dy;
                                if (distSq < nearestDistSq) {
                                    nearestDistSq = distSq;
                                    nearest = f;
                                }
                            }
                            if (nearest != null) {
                                bullet.setFlareTarget(nearest.getX(), nearest.getY());
                            }
                        }
                    }
                    
                    // Reset cooldown: 720 - (level-1) * 150
                    flareCooldownTimer = FLARE_BASE_COOLDOWN - (flaresLevel - 1) * 150;
                    
                    // Play flare deploy SFX
                    soundManager.playSoundSpatial(SoundManager.Sound.FLARE_DEPLOY, 0.5f, player.getX(), WORLD_WIDTH);
                }
            }
            
            // Update flares and spawn trail particles
            for (int fi = flares.size() - 1; fi >= 0; fi--) {
                Flare flare = flares.get(fi);
                flare.update(deltaTime);
                if (!flare.isActive()) {
                    flares.remove(fi);
                    continue;
                }
                // Trail particle each frame
                if (enableParticles && Math.random() < 0.7) {
                    addParticle(
                        flare.getX(), flare.getY(),
                        (Math.random() - 0.5) * 0.3, (Math.random() - 0.5) * 0.3,
                        FLARE_RED, 10, 2,
                        Particle.ParticleType.FLARE_SPARK
                    );
                }
            }
            
            // Update flare targets on bullets tracking flares
            for (Bullet bullet : bullets) {
                if (bullet.isTargetingFlare() && bullet.getType() == Bullet.BulletType.HOMING) {
                    // Find nearest alive flare to update tracking
                    Flare nearest = null;
                    double nearestDistSq = Double.MAX_VALUE;
                    for (Flare f : flares) {
                        if (!f.isActive()) continue;
                        double dx = f.getX() - bullet.getX();
                        double dy = f.getY() - bullet.getY();
                        double distSq = dx * dx + dy * dy;
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = f;
                        }
                    }
                    if (nearest != null) {
                        bullet.setFlareTarget(nearest.getX(), nearest.getY());
                    } else {
                        // No active flares left, resume tracking player
                        bullet.clearFlareTarget();
                    }
                }
            }
            
            // Flare-bullet collision detection
            for (int fi = flares.size() - 1; fi >= 0; fi--) {
                Flare flare = flares.get(fi);
                if (!flare.isActive()) continue;
                
                for (int bi = bullets.size() - 1; bi >= 0; bi--) {
                    Bullet bullet = bullets.get(bi);
                    if (bullet.getType() == Bullet.BulletType.HOMING && bullet.isTargetingFlare() && bullet.isActive()) {
                        if (flare.collidesWith(bullet)) {
                            double cx = (flare.getX() + bullet.getX()) / 2;
                            double cy = (flare.getY() + bullet.getY()) / 2;
                            
                            // Destroy both
                            flare.setActive(false);
                            returnBulletToPool(bullet);
                            bullets.remove(bi);
                            
                            // Play flare collision SFX
                            soundManager.playSoundSpatial(SoundManager.Sound.FLARE_EXPLODE, 0.4f, cx, WORLD_WIDTH);
                            
                            // Explosion VFX
                            if (enableParticles) {
                                // Glowing explosion sparks
                                for (int pi = 0; pi < 10; pi++) {
                                    double angle = TWO_PI * pi / 10.0;
                                    double speed = 1.5 + Math.random() * 2.5;
                                    Color sparkColor;
                                    double rand = Math.random();
                                    if (rand < 0.4) sparkColor = FLARE_RED;
                                    else if (rand < 0.7) sparkColor = FLARE_ORANGE;
                                    else sparkColor = FLARE_YELLOW;
                                    addParticle(
                                        cx, cy,
                                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                                        sparkColor, 15 + Math.random() * 10, 2 + Math.random() * 4,
                                        Particle.ParticleType.FLARE_SPARK
                                    );
                                }
                                // Explosion ring
                                addParticle(
                                    cx, cy, 0, 0,
                                    FLARE_ORANGE, 25, 20,
                                    Particle.ParticleType.EXPLOSION
                                );
                            }
                            
                            break; // Flare destroyed, move to next flare
                        }
                    }
                }
            }
            // Remove destroyed flares
            flares.removeIf(f -> !f.isActive());
        }
        
        // Proximity warning hum - find closest bullet to player for subtle audio cue
        if (player != null && !bossDeathAnimation && !deathSequenceActive) {
            List<Bullet> nearbyForHum = getNearbyBullets(player.getX(), player.getY());
            double closestDist = Double.MAX_VALUE;
            double closestX = 0;
            for (Bullet bullet : nearbyForHum) {
                if (!bullet.isActive() || bullet.getWarningTime() > 0) continue;
                double hdx = bullet.getX() - player.getX();
                double hdy = bullet.getY() - player.getY();
                double dist = Math.sqrt(hdx * hdx + hdy * hdy);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestX = bullet.getX();
                }
            }
            soundManager.updateProximityHum(closestDist, PROXIMITY_WARNING_DISTANCE, closestX, WORLD_WIDTH);
        } else {
            soundManager.stopProximityHum();
        }
        
        // Shield collision check - independent of player hitbox, uses shield's visual radius
        if (player != null && !bossDeathAnimation && shieldActive && shieldHits > 0) {
            List<Bullet> nearbyForShield = getNearbyBullets(player.getX(), player.getY());
            java.util.Iterator<Bullet> shieldIter = nearbyForShield.iterator();
            while (shieldIter.hasNext()) {
                Bullet bullet = shieldIter.next();
                if (!bullet.isActive()) continue;
                if (bullet.getWarningTime() > 0) continue;
                
                // Distance from bullet to player center
                double bDx = bullet.getX() - player.getX();
                double bDy = bullet.getY() - player.getY();
                double bulletDist = Math.sqrt(bDx * bDx + bDy * bDy);
                double shieldOrbitR = 38; // Match visual orbit radius
                // Shield blocks if bullet is within the full outer glow radius (orbit + glow + half stroke)
                boolean inShieldBand = bulletDist < shieldOrbitR + 25; // Tighter shield hitbox (was +35)
                
                if (inShieldBand) {
                    // One shield breaks per hit
                    shieldHits--;
                    if (shieldHits <= 0) {
                        shieldActive = false;
                    }
                    // Tutorial: track shield blocks for step 4
                    if (tutorialMode) {
                        tutorialShieldBlockCount++;
                        // Reset shield cooldown instantly in tutorial so player can reactivate
                        if (shieldHits <= 0) {
                            ActiveItem equipped = gameData.getEquippedItem();
                            if (equipped != null) {
                                equipped.setCurrentCooldown(0);
                                equipped.setActive(false);
                            }
                        }
                    }
                    soundManager.playSoundSpatial(SoundManager.Sound.SHIELD_BREAK, 1.0f, bullet.getX(), WORLD_WIDTH);
                    bullets.remove(bullet);
                    returnBulletToPool(bullet);
                    
                    // Create shield break particles at the destroyed shield's position
                    if (enableParticles) {
                        double shieldOrbitRadius = 38;
                        double destroyedShieldAngle = shieldOrbitAngle + (shieldHits * TWO_PI / 3.0);
                        double shieldX = player.getX() + Math.cos(destroyedShieldAngle) * shieldOrbitRadius;
                        double shieldY = player.getY() + Math.sin(destroyedShieldAngle) * shieldOrbitRadius;
                        
                        for (int j = 0; j < 20; j++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 2 + Math.random() * 5;
                            addParticle(
                                shieldX, shieldY,
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                FROST_BEAM_ICE, 30, 8,
                                Particle.ParticleType.SPARK
                            );
                        }
                    }
                    
                    screenShakeIntensity = 6;
                    if (shieldHits <= 0) break; // All shields gone, stop checking
                }
            }
        }
        
        // Check collisions using spatial grid (much faster for many bullets!)
        if (player != null && !bossDeathAnimation && !deathSequenceActive) {
            List<Bullet> nearbyBullets = getNearbyBullets(player.getX(), player.getY());
            for (Bullet bullet : nearbyBullets) {
                if (bullet.isActive() && bullet.collidesWith(player)) {
                    // Check for active item invincibility (DASH or INVINCIBILITY)
                    if (playerInvincible) {
                        // Invincible - bullets pass through
                        continue;
                    }
                    
                    // Check for perfect dodge i-frames
                    if (perfectDodgeIFrames > 0) {
                        // Perfect dodge invincibility - phase through bullet
                        continue;
                    }
                    
                    // Lucky Dodge chance - phase through bullets (scales with level via getMultiplier)
                    int luckyDodgeLevel = getActiveLuckyDodgeLevel();
                    if (luckyDodgeLevel > 0) {
                        double dodgeChance = passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.LUCKY_DODGE); // 10% per level
                        if (Math.random() < dodgeChance) {
                            soundManager.playSound(SoundManager.Sound.DODGE, 1.0f + (dodgeCombo * 0.1f));
                            
                            // Lucky dodge! Trigger flicker animation
                            player.triggerFlicker();
                            bullets.remove(bullet);
                            returnBulletToPool(bullet);
                            
                            // Increment dodge combo
                            dodgeCombo++;
                            comboTimer = COMBO_TIMEOUT;
                            
                            // Add score based on combo
                            gameData.addScore(10 * dodgeCombo);
                            
                            // Create dodge particles
                            if (enableParticles) {
                                for (int j = 0; j < 8; j++) {
                                    double angle = TWO_PI * j / 8;
                                    addParticle(
                                        player.getX(), player.getY(),
                                        Math.cos(angle) * 2, Math.sin(angle) * 2,
                                        DODGE_GREEN, 20, 5,
                                        Particle.ParticleType.DODGE
                                    );
                                }
                            }
                            
                            continue;
                        }
                    }
                    
                    // No dodge - handlePlayerDeath creates explosion particles
                    // Track damage taken
                    gameData.getCurrentLevelStats().incrementDamageTaken();
                    handlePlayerDeath();
                    return;
                }
                
                // Check for graze (near miss) - use squared distance to avoid sqrt
                double grazeRadius = (tutorialMode && tutorialStep == 2) ? GRAZE_DISTANCE * 3.0 : GRAZE_DISTANCE;
                double closeCallRadius = CLOSE_CALL_DISTANCE;
                double perfectDodgeRadius = PERFECT_DODGE_DISTANCE;
                double gdx = bullet.getX() - player.getX();
                double gdy = bullet.getY() - player.getY();
                double distSq = gdx * gdx + gdy * gdy;
                double grazeRadiusSq = grazeRadius * grazeRadius;
                double playerRadiusHalf = player.getSize() / 2.0;
                double playerRadiusHalfSq = playerRadiusHalf * playerRadiusHalf;
                
                if (!bullet.hasGrazed() && distSq < grazeRadiusSq && distSq > playerRadiusHalfSq) {
                    double dist = Math.sqrt(distSq); // Only compute sqrt when actually grazing
                    bullet.setGrazed(true);
                    totalGrazesThisRun++;
                    if (tutorialMode) tutorialGrazeCount++;
                    
                    // Track closest call and graze distance for risk %
                    gameData.getCurrentLevelStats().updateClosestCall(dist);
                    gameData.getCurrentLevelStats().addGrazeDistance(dist);
                    
                    // Determine graze tier
                    boolean isPerfectDodge = dist < perfectDodgeRadius;
                    boolean isCloseCall = dist < closeCallRadius;
                    
                    // Track near misses (close calls that aren't perfect dodges)
                    if (isCloseCall && !isPerfectDodge) {
                        gameData.getCurrentLevelStats().incrementNearMisses();
                    }
                    
                    // Calculate graze value based on tier
                    int grazeValue = 1;
                    int moneyBonus = 0;
                    Color particleColor = GRAZE_BLUE; // Use cached color
                    
                    if (isPerfectDodge) {
                        // PERFECT DODGE - highest reward
                        soundManager.playSoundSpatial(SoundManager.Sound.PERFECT_DODGE, 1.2f, bullet.getX(), WORLD_WIDTH);
                        grazeValue = 5;
                        moneyBonus = (int)(25 * riskContractMultiplier);
                        particleColor = GRAZE_GOLD; // Use cached color
                        
                        // Grant brief invincibility
                        perfectDodgeIFrames = PERFECT_DODGE_IFRAMES;
                        perfectDodgeFlashTimer = 20;
                        
                        // Intense slow-mo and effects
                        slowMotionFactor = 0.15;
                        slowMotionTimer = 10;
                        screenShakeIntensity = Math.max(screenShakeIntensity, 5);
                        comboPulseScale = 1.6;
                        
                        // Show PERFECT! announcement
                        if (comboSystem != null) comboSystem.setAnnouncement("PERFECT!", WIDTH / 2.0, HEIGHT / 2.0);
                        
                    } else if (isCloseCall) {
                        // CLOSE CALL - medium reward
                        soundManager.playSoundSpatial(SoundManager.Sound.CLOSE_CALL, 0.9f, bullet.getX(), WORLD_WIDTH);
                        grazeValue = 2;
                        moneyBonus = (int)(10 * riskContractMultiplier);
                        particleColor = GRAZE_GREEN; // Use cached color
                        
                        // Moderate slow-mo
                        slowMotionFactor = 0.25;
                        slowMotionTimer = 6;
                        screenShakeIntensity = Math.max(screenShakeIntensity, 3);
                        comboPulseScale = 1.4;
                        
                    } else {
                        // Normal graze - no sound to prevent spam
                        grazeValue = 1;
                        moneyBonus = (int)(2 * riskContractMultiplier);
                        comboPulseScale = 1.2;
                    }
                    
                    // Add combo with tier info and player position for announcement spawn
                    comboSystem.addCombo(grazeValue, isCloseCall, isPerfectDodge, soundManager, player.getX(), player.getY());
                    
                    // Add score with combo multiplier
                    int grazeScore = (int)(10 * grazeValue * comboSystem.getMultiplier());
                    gameData.addScore(grazeScore);
                    
                    // Add money bonus
                    if (moneyBonus > 0) {
                        gameData.addRunMoney(moneyBonus);
                        if (isPerfectDodge) {
                            soundManager.playSound(SoundManager.Sound.COIN_PICKUP, 1.2f);
                        }
                    }
                    
                    // Create enhanced graze particle effect
                    if (enableParticles) {
                        // More particles for higher tiers
                        int particleCount = isPerfectDodge ? 15 : (isCloseCall ? 10 : 6);
                        double bulletAngle = Math.atan2(bullet.getVY(), bullet.getVX());
                        
                        for (int j = 0; j < particleCount; j++) {
                            double spreadAngle = bulletAngle + Math.PI + (Math.random() - 0.5) * 1.2;
                            double speed = 2 + Math.random() * (isPerfectDodge ? 5 : 3);
                            addParticle(
                                player.getX() + (Math.random() - 0.5) * 10, 
                                player.getY() + (Math.random() - 0.5) * 10,
                                Math.cos(spreadAngle) * speed, Math.sin(spreadAngle) * speed,
                                particleColor, 20, isPerfectDodge ? 6 : 4,
                                Particle.ParticleType.TRAIL
                            );
                        }
                        
                        // Glow ring at graze point
                        int ringSize = isPerfectDodge ? 30 : (isCloseCall ? 20 : 15);
                        addParticle(
                            (bullet.getX() + player.getX()) / 2, 
                            (bullet.getY() + player.getY()) / 2, 
                            0, 0,
                            particleColor, 15, ringSize,
                            Particle.ParticleType.EXPLOSION
                        );
                        
                        // Extra starburst for perfect dodges
                        if (isPerfectDodge) {
                            for (int j = 0; j < 8; j++) {
                                double angle = (j / 8.0) * TWO_PI;
                                addParticle(
                                    player.getX(), player.getY(),
                                    Math.cos(angle) * 4, Math.sin(angle) * 4,
                                    PERFECT_DODGE_FLASH, 25, 3,
                                    Particle.ParticleType.SPARK
                                );
                            }
                        }
                    }
                }
            }
        }
    }
        
    // Bullet pooling methods
    private Bullet getBulletFromPool() {
        if (bulletPool.isEmpty()) {
            return new Bullet(0, 0, 0, 0);
        }
        return bulletPool.remove(bulletPool.size() - 1);
    }
    
    private void returnBulletToPool(Bullet bullet) {
        if (bulletPool.size() < BULLET_POOL_PREWARM) { // Cap pool size
            bulletPool.add(bullet);
        }
    }
    
    /** Wire the bullet pool factory into a Boss so it reuses pooled bullets instead of allocating new ones. */
    private void setupBossFactory(Boss boss) {
        if (boss != null) {
            boss.setBulletFactory((bx, by, bvx, bvy, type) -> {
                Bullet b = getBulletFromPool();
                b.reset(bx, by, bvx, bvy, type);
                return b;
            });
        }
    }
    
    // Particle pooling methods
    private Particle getParticleFromPool() {
        if (particlePool.isEmpty()) {
            return new Particle(0, 0, 0, 0, Color.WHITE, 1, 1, Particle.ParticleType.SPARK);
        }
        return particlePool.remove(particlePool.size() - 1);
    }
    
    private void returnParticleToPool(Particle particle) {
        if (particlePool.size() < PARTICLE_POOL_PREWARM) { // Cap pool size
            particlePool.add(particle);
        }
    }

    /**
     * Pre-warm object pools by creating objects up front so the first
     * wave of bullets/particles doesn't trigger hundreds of allocations.
     * Called once at startup and again at each level start.
     */
    private void prewarmPools() {
        // Top off bullet pool
        while (bulletPool.size() < BULLET_POOL_PREWARM) {
            bulletPool.add(new Bullet(0, 0, 0, 0));
        }
        // Top off particle pool
        while (particlePool.size() < PARTICLE_POOL_PREWARM) {
            particlePool.add(new Particle(0, 0, 0, 0, Color.WHITE, 1, 1, Particle.ParticleType.SPARK));
        }
    }
    
    // Add particle with pooling and limit check
    private void addParticle(double x, double y, double vx, double vy, Color color, double lifetime, double size, Particle.ParticleType type) {
        if (particles.size() >= MAX_PARTICLES) return; // Limit particles
        Particle p = getParticleFromPool();
        p.reset(x, y, vx, vy, color, lifetime, size, type);
        particles.add(p);
    }
    
    // Check for close calls with bullets (graze detection)
    private void checkBulletGrazes(Player player) {
        List<Bullet> nearbyBullets = getNearbyBullets(player.getX(), player.getY());
        double playerRadius = player.getSize() / 2.0;
        
        for (Bullet bullet : nearbyBullets) {
            if (bullet.hasGrazed()) continue; // Only count each graze once
            
            double dx = bullet.getX() - player.getX();
            double dy = bullet.getY() - player.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);
            double bulletRadius = 4.0; // Default bullet radius
            double hitDistance = playerRadius + bulletRadius;
            
            // Check if bullet is in graze zone (close but not hitting)
            if (distance > hitDistance && distance < hitDistance + GRAZE_DISTANCE) {
                bullet.setGrazed(true);
                
                // Award graze bonus
                int grazeBonus = 10;
                grazeScore += grazeBonus;
                gameData.addScore(grazeBonus);
                
                // Spawn graze particles
                if (enableParticles && Math.random() < 0.3) {
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 0.5 + Math.random() * 1.5;
                        addParticle(
                            player.getX() + Math.cos(angle) * playerRadius,
                            player.getY() + Math.sin(angle) * playerRadius,
                            Math.cos(angle) * speed,
                            Math.sin(angle) * speed,
                            GRAZE_BLUE,
                            20, 3,
                            Particle.ParticleType.SPARK
                        );
                    }
                }
            }
        }
    }
    
    // Spatial grid methods for optimized collision detection
    private int getGridKey(double x, double y) {
        int gridX = (int)(x * INV_GRID_CELL_SIZE);
        int gridY = (int)(y * INV_GRID_CELL_SIZE);
        return gridX * GRID_WIDTH_MULTIPLIER + gridY; // Simple hash
    }
    
    private int lastBulletGridSize = 0; // Track for incremental updates
    
    private void rebuildBulletGrid() {
        // Only rebuild if bullet count changed significantly or every few frames
        // This is a major performance optimization for high bullet counts
        int currentSize = bullets.size();
        
        // Clear and rebuild the grid
        for (List<Bullet> cellList : bulletGrid.values()) {
            cellList.clear();
        }
        
        for (Bullet bullet : bullets) {
            if (bullet.isActive()) {
                int key = getGridKey(bullet.getX(), bullet.getY());
                List<Bullet> cellList = bulletGrid.get(key);
                if (cellList == null) {
                    cellList = new ArrayList<>(16); // Pre-sized for typical cell
                    bulletGrid.put(key, cellList);
                }
                cellList.add(bullet);
            }
        }
        
        lastBulletGridSize = currentSize;
    }
    
    private List<Bullet> getNearbyBullets(double x, double y) {
        nearbyBulletsCache.clear(); // Reuse list to avoid allocation
        // Pre-compute base grid coordinates
        int baseX = (int)(x * INV_GRID_CELL_SIZE);
        int baseY = (int)(y * INV_GRID_CELL_SIZE);
        
        // Check 3x3 grid around player - manually add to avoid addAll overhead
        for (int dx = -1; dx <= 1; dx++) {
            int checkX = baseX + dx;
            for (int dy = -1; dy <= 1; dy++) {
                int checkY = baseY + dy;
                int key = checkX * GRID_WIDTH_MULTIPLIER + checkY;
                List<Bullet> cellBullets = bulletGrid.get(key);
                if (cellBullets != null) {
                    for (Bullet b : cellBullets) {
                        nearbyBulletsCache.add(b);
                    }
                }
            }
        }
        return nearbyBulletsCache;
    }
    
    /**
     * Render the current frame to an off-screen buffer on the game thread.
     * This moves all heavy rendering work OFF the EDT, drastically reducing lag.
     */
    private void renderToBuffer() {
        BufferedImage buf = renderBuffer;
        if (buf == null) return;
        Graphics2D g2d = buf.createGraphics();
        
        // Clear buffer
        g2d.setComposite(AlphaComposite.Src);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Apply rendering hints based on settings
        if (enableAntiAliasing) {
            if (gameState == GameState.PLAYING && bullets.size() > 100) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            } else {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            }
        } else {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        }
        
        // Draw previous state if transitioning
        if (stateTransitionProgress < 1.0f && previousState != null) {
            g2d.setComposite(RenderCache.getAlpha(1.0f - stateTransitionProgress));
            drawState(g2d, previousState);
            g2d.setComposite(RenderCache.getAlpha(stateTransitionProgress));
            drawState(g2d, gameState);
            g2d.setComposite(RenderCache.ALPHA_FULL);
        } else {
            drawState(g2d, gameState);
        }
        
        // Draw auto-save indicator overlay
        if (showAutoSaveIndicator) {
            drawAutoSaveIndicator(g2d, WIDTH, HEIGHT);
        }
        
        // Draw current track name overlay (debug)
        if (showTrackName) {
            String trackName = soundManager.getCurrentMusicName();
            if (trackName != null) {
                if (debugTrackFont == null) debugTrackFont = new Font("Monospaced", Font.PLAIN, 11);
                g2d.setFont(debugTrackFont);
                FontMetrics fm = g2d.getFontMetrics();
                String label = "Now Playing: " + trackName;
                int textW = fm.stringWidth(label);
                int px = WIDTH - textW - 12;
                int py = 18;
                g2d.setColor(RenderCache.BLACK_140);
                g2d.fillRoundRect(px - 6, py - 12, textW + 12, 16, 6, 6);
                g2d.setColor(RenderCache.GREEN_TRACK);
                g2d.drawString(label, px, py);
            }
        }
        
        g2d.dispose();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        // Fill panel with black first so any sub-pixel edge gaps from
        // buffer scaling don't show stale white pixels in recordings.
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Lightweight blit: just draw the pre-rendered buffer to screen
        // All heavy rendering was done on the game thread in renderToBuffer()
        BufferedImage buf;
        synchronized (bufferSwapLock) {
            buf = displayBuffer;
        }
        if (buf != null) {
            // Stretch buffer to fill the entire panel â€” no black bars.
            // The window is always sized to match the screen's native aspect ratio,
            // so distortion is negligible.
            g.drawImage(buf, 0, 0, getWidth(), getHeight(), null);
        }
    }
    
    private void drawState(Graphics2D g2d, GameState state) {
        // If renderer not loaded yet, show loading screen
        if (renderer == null && state != GameState.LOADING) {
            drawSimpleLoading(g2d, WIDTH, HEIGHT, loadingProgress);
            return;
        }
        
        switch (state) {
            case SAVE_SELECT:
                renderer.drawSaveSelection(g2d, WIDTH, HEIGHT, gradientTime, selectedSaveSlot, 
                    saveMetadataCache, deletingSlot, deleteConfirmTimer, escapeTimer, saveSelectScrollAnimated);
                break;
            case MODE_SELECT:
                renderer.drawModeSelect(g2d, WIDTH, HEIGHT, gradientTime, selectedGameModeIndex);
                break;
            case NAME_INPUT:
                renderer.drawNameInput(g2d, WIDTH, HEIGHT, gradientTime, saveNameInput.toString(), 
                    saveNameCursorPos, saveNameCursorBlink, onScreenKbRow, onScreenKbCol, ON_SCREEN_KB_ROWS);
                break;
            case MENU:
                renderer.drawMenu(g2d, WIDTH, HEIGHT, gradientTime, escapeTimer, selectedMenuItem, saveManager.getCurrentSaveSlot(), gameData.getGameMode());
                if (showTutorialPrompt) {
                    renderer.drawTutorialPrompt(g2d, WIDTH, HEIGHT, gradientTime, tutorialPromptSelection);
                }
                break;
            case INFO:
                renderer.drawInfo(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case ACHIEVEMENTS:
                renderer.drawAchievements(g2d, WIDTH, HEIGHT, gradientTime, achievementManager, achievementsScrollAnimated);
                break;
            case STATS:
                renderer.drawStats(g2d, WIDTH, HEIGHT, gradientTime, passiveUpgradeManager, hasSavedGame);
                renderer.drawStatsUpgrades(g2d, WIDTH, selectedStatItem, passiveUpgradeManager, statsScrollAnimated, hasSavedGame, gameData.getBestRunLevel());
                break;
            case SETTINGS:
                renderer.drawSettings(g2d, WIDTH, HEIGHT, selectedSettingsItem, gradientTime, settingsScroll, selectedSettingsCategory, gameData);
                break;
            case LEVEL_SELECT:
                renderer.drawLevelSelect(g2d, WIDTH, HEIGHT, gameData.getCurrentLevel(), gameData.getMaxUnlockedLevel(), gradientTime, levelSelectScrollAnimated, hasSavedGame, savedLevel, planeTakeoffAnimation, planeTakeoffTimer);
                break;
            case LEVEL_CONFIRM:
                renderer.drawLevelConfirm(g2d, WIDTH, HEIGHT, selectedLevelToStart, selectedConfirmItem, isConfirmingResume, gradientTime, planeTakeoffAnimation, planeTakeoffTimer, levelSelectScrollAnimated, hasSavedGame, savedLevel);
                break;
            case RISK_CONTRACT:
                renderer.drawRiskContract(g2d, WIDTH, HEIGHT, selectedRiskContract, RISK_CONTRACT_NAMES, RISK_CONTRACT_DESCRIPTIONS, RISK_CONTRACT_MULTIPLIERS, gradientTime, gameData.getCurrentLevel());
                break;
            case ATTACK_INTRO:
                drawAttackIntro(g2d, WIDTH, HEIGHT);
                break;
            case ATTACK_SHOWCASE:
                drawAttackShowcase(g2d, WIDTH, HEIGHT);
                break;
            case PLAYING:
                // Apply camera zoom combined with dynamic effect zoom
                AffineTransform originalTransform = g2d.getTransform();
                double totalZoom = cameraZoom * effectZoom; // Combine base zoom with effect zoom
                
                // When zooming OUT (totalZoom < 1.0), fill exposed edges with blurred/scaled background
                if (totalZoom < 1.0) {
                    // First, draw an enlarged blurred version of the background to fill edges
                    Graphics2D bgG = (Graphics2D) g2d.create();
                    
                    // Scale up the background to fill the exposed areas (inverse of zoom)
                    double bgScale = 1.0 / totalZoom;
                    double offsetX = (WIDTH * bgScale - WIDTH) / 2;
                    double offsetY = (HEIGHT * bgScale - HEIGHT) / 2;
                    
                    // Draw scaled and dimmed background
                    bgG.translate(-offsetX, -offsetY);
                    bgG.scale(bgScale, bgScale);
                    
                    // Draw the background (gradient or parallax)
                    if (backgroundMode == 0) {
                        renderer.drawAnimatedGradientPublic(bgG, WIDTH, HEIGHT, gradientTime, gameData.getCurrentLevel());
                    } else if (backgroundMode == 1) {
                        renderer.drawParallaxBackgroundPublic(bgG, WIDTH, HEIGHT, gameData.getCurrentLevel());
                    } else {
                        renderer.drawAnimatedGradientPublic(bgG, WIDTH, HEIGHT, gradientTime, gameData.getCurrentLevel());
                    }
                    
                    // Apply blur/darken effect over the background edges
                    bgG.dispose();
                    
                    // Draw a semi-transparent dark overlay to make edges less prominent
                    float edgeDarkness = (float)(1.0 - totalZoom) * 0.6f; // Darker when zoomed out more
                    Composite edgeOld = g2d.getComposite();
                    g2d.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, edgeDarkness))));
                    g2d.setColor(Color.BLACK);
                    g2d.fillRect(0, 0, WIDTH, HEIGHT);
                    g2d.setComposite(edgeOld);
                }
                
                g2d.translate(WIDTH / 2, HEIGHT / 2);
                g2d.scale(totalZoom, totalZoom);
                g2d.translate(-WIDTH / 2, -HEIGHT / 2);
                
                // Apply screen shake
                g2d.translate(screenShakeX, screenShakeY);
                renderer.drawGame(g2d, WIDTH, HEIGHT, player, currentBoss, bullets, particles, beamAttacks, gameData.getCurrentLevel(), gradientTime, bossVulnerable, invulnerabilityTimer, dodgeCombo, comboTimer > 0, bossDeathAnimation, bossDeathScale, bossDeathRotation, gameTimeSeconds, currentFPS, shieldActive, playerInvincible, bossHitCount, cameraX, cameraY, introPanActive, bossFlashTimer, screenFlashTimer, comboSystem, damageNumbers, bossIntroActive, bossIntroText, bossIntroTimer, isPaused, selectedPauseItem, pendingAchievements, achievementNotificationTimer, deathSequenceActive, playerHidden, respawnBlinkTimer, riskContractType, riskContractActive, stoppedMovingTimer, unpauseCountdownActive, unpauseCountdownTimer, itemReadyFlickerTimer, itemCompleteFlashTimer, achievementFlashTimer, bossIntroFlashTimer, countdownFlashTimer, bossHitFlashTimer, typePurgeFlashTimer, typePurgeFlashColor, moneyCircles, MONEY_CIRCLE_RADIUS, frostBeamAngle, frostBeamProgress, frostBeamStopDistance, frostBeamRetracting, frostBeamRetractPhase, shieldHits, shieldOrbitAngle, bossIntroPlayerX, bossIntroBossX, bossIntroVsScale, bossIntroFlash, bossIntroPhase, introParticles, deathFlashTimer, flares);
                
                // Draw boss stun effect
                if (bossStunned && currentBoss != null) {
                    drawBossStunEffect(g2d, cameraX, cameraY, gradientTime);
                }
                
                // Restore original transform (removes both shake and zoom)
                g2d.setTransform(originalTransform);
                
                // Tutorial overlays — popup and HUD bar drawn AFTER drawGame (above highlight tint)
                if (tutorialMode) {
                    if (tutorialCompleteScreen) {
                        renderer.drawTutorialCompleteScreen(g2d, WIDTH, HEIGHT, gradientTime, tutorialCompleteSelection);
                    } else {
                        renderer.drawTutorialHUD(g2d, WIDTH, HEIGHT, tutorialStep, TUTORIAL_STEPS.length, 
                            tutorialStep < TUTORIAL_STEPS.length ? TUTORIAL_STEPS[tutorialStep][0] : "Complete",
                            tutorialTaskText, tutorialTaskProgress, tutorialTaskHasBar);
                        if (tutorialPopupActive) {
                            renderer.drawTutorialPopup(g2d, WIDTH, HEIGHT, tutorialPopupTitle, tutorialPopupBody, gradientTime, tutorialPopupInputDelay);
                        }
                    }
                }
                break;
            case LOADING:
                // Draw loading screen directly (renderer not yet created)
                drawSimpleLoading(g2d, WIDTH, HEIGHT, loadingProgress);
                break;
            case GAME_OVER:
                renderer.drawGameOver(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case DEMO_OVER:
                renderer.drawDemoOver(g2d, WIDTH, HEIGHT, gradientTime, demoOverSelection);
                break;
            case WIN:
                renderer.drawWin(g2d, WIDTH, HEIGHT, gradientTime, bossKillTime);
                // Draw item unlock animation if active
                if (itemUnlockAnimation) {
                    drawItemUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                // Draw contract unlock animation if active (after item animation)
                if (contractUnlockAnimation) {
                    drawContractUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                // Draw endless unlock animation if active (after contract animation)
                if (endlessUnlockAnimation) {
                    drawEndlessUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                break;
            case LEADERBOARD:
                renderer.drawLeaderboard(g2d, WIDTH, HEIGHT, gradientTime,
                    leaderboardManager, leaderboardScreenTimer, leaderboardAnimSkipped,
                    leaderboardReadyToExit, leaderboardCompletedLevel, leaderboardCompletedDifficulty,
                    bossKillTime);
                break;
            case LEADERBOARD_VIEW:
                renderer.drawLeaderboardView(g2d, WIDTH, HEIGHT, gradientTime,
                    leaderboardManager, selectedLeaderboardDifficulty, leaderboardViewScrollAnimated);
                break;
            case SHOP:
                renderer.drawShop(g2d, WIDTH, HEIGHT, gradientTime, shopScrollAnimated);
                // Draw passive upgrade unlock animation if active (overlay on top of shop)
                if (passiveUnlockAnimation) {
                    drawPassiveUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                // Draw tutorial popup overlay on shop screen
                if (tutorialMode && tutorialPopupActive) {
                    renderer.drawTutorialPopup(g2d, WIDTH, HEIGHT, tutorialPopupTitle, tutorialPopupBody, gradientTime, tutorialPopupInputDelay);
                }
                break;
            case DEBUG:
                renderer.drawDebug(g2d, WIDTH, HEIGHT, gradientTime, selectedDebugOption, debugSetLevelValue, debugLeaderboardLevel);
                // Draw item/contract/endless unlock animations if active (debug preview)
                if (itemUnlockAnimation) {
                    drawItemUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                if (contractUnlockAnimation) {
                    drawContractUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                if (endlessUnlockAnimation) {
                    drawEndlessUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                break;
        }
    }
    
    // Chill menu tracks (light - first half of levels unlocked, 1-14)
    private static final String[] CHILL_MENU_TRACKS = {
        "SFX/Music Tracks/Main/Burning Grounds.wav",
        "SFX/Music Tracks/Main/Chilling Outside.wav",
        "SFX/Music Tracks/Main/Made to Build.wav",
        "SFX/Music Tracks/Main/Planet Hell.wav",
        "SFX/Music Tracks/Main/Prepare to Fight.wav"
    };
    
    // Intense menu tracks (heavy - second half of levels unlocked, 15-28)
    private static final String[] HEAVY_MENU_TRACKS = {
        "SFX/Music Tracks/Main/Planet Hell.wav",
        "SFX/Music Tracks/Main/Prepare to Fight.wav",
        "SFX/Music Tracks/Main/Rock Battle Menu.wav",
        "SFX/Music Tracks/Main/Rock Factory.wav",
        "SFX/Music Tracks/Main/Rock Combat Theme.wav"
    };
    
    // Boss fight tracks (gameplay)
    private static final String[] BOSS_TRACKS = {
        "SFX/Music Tracks/Short/Burning Grounds Short Speed Increase.wav",
        "SFX/Music Tracks/Short/Chilling Outside Short Speed Increase.wav",
        "SFX/Music Tracks/Main/Heavy Rock Menu Theme.wav",
        "SFX/Music Tracks/Main/Rock Battle Theme.wav",
        "SFX/Music Tracks/Main/Conflict.wav",
        "SFX/Music Tracks/Main/Fantasy Rock Night.wav",
        "SFX/Music Tracks/Main/Hell and Demons.wav",
        "SFX/Music Tracks/Main/Rock Synth.wav",
        "SFX/Music Tracks/Main/Synthwave Metal.wav",
        "SFX/Music Tracks/Main/Time to Fight.wav",
        "SFX/Music Tracks/Main/Under Attack.wav"
    };
    
    // Helper method to get menu music path based on level progression
    private String getMenuMusicPath() {
        // Chill menu music for first half of levels (1-14), intense for second half (15-28)
        if (gameData.getMaxUnlockedLevel() <= 14) {
            return CHILL_MENU_TRACKS[(int)(Math.random() * CHILL_MENU_TRACKS.length)];
        } else {
            return HEAVY_MENU_TRACKS[(int)(Math.random() * HEAVY_MENU_TRACKS.length)];
        }
    }
    
    // Helper method to get a random boss fight music path
    private String getRandomBattleMusicPath() {
        return BOSS_TRACKS[(int)(Math.random() * BOSS_TRACKS.length)];
    }
    
    // Check if a given music path is a menu track (from either chill or heavy pool)
    private boolean isMenuTrack(String path) {
        if (path == null) return false;
        return isChillTrack(path) || isHeavyTrack(path);
    }
    
    // Check if a given music path is from the chill menu pool
    private boolean isChillTrack(String path) {
        if (path == null) return false;
        String wavPath = path.replace(".mp3", ".wav");
        for (String track : CHILL_MENU_TRACKS) {
            if (track.replace(".mp3", ".wav").equals(wavPath)) return true;
        }
        return false;
    }
    
    // Check if a given music path is from the heavy menu pool
    private boolean isHeavyTrack(String path) {
        if (path == null) return false;
        String wavPath = path.replace(".mp3", ".wav");
        for (String track : HEAVY_MENU_TRACKS) {
            if (track.replace(".mp3", ".wav").equals(wavPath)) return true;
        }
        return false;
    }
    
    // Check if a state is a "menu-like" state where menu music should play
    private boolean isMenuState(GameState state) {
        return state == GameState.MENU || state == GameState.LEVEL_SELECT ||
               state == GameState.SHOP || state == GameState.STATS ||
               state == GameState.ACHIEVEMENTS || state == GameState.INFO ||
               state == GameState.SETTINGS || state == GameState.SAVE_SELECT ||
               state == GameState.MODE_SELECT || state == GameState.LEADERBOARD ||
               state == GameState.LEADERBOARD_VIEW ||
               state == GameState.DEBUG || state == GameState.LEVEL_CONFIRM ||
               state == GameState.ATTACK_SHOWCASE || state == GameState.ATTACK_INTRO;
    }
    
    // Helper method to transition to a new state
    private void transitionToState(GameState newState) {
        if (gameState != newState) {
            // Handle music transitions (leaderboard has its own music, skip generic menu logic)
            if (newState != GameState.LEADERBOARD && isMenuState(newState)) {
                // Start menu music if not playing, or switch pools if progression crossed the threshold
                String currentTrack = soundManager.getCurrentMusic();
                boolean shouldBeHeavy = gameData.getMaxUnlockedLevel() > 14;
                boolean wrongPool = (shouldBeHeavy && isChillTrack(currentTrack)) || (!shouldBeHeavy && isHeavyTrack(currentTrack));
                if (!isMenuTrack(currentTrack) || wrongPool) {
                    soundManager.playMusic(getMenuMusicPath());
                }
            } else if (newState == GameState.PLAYING) {
                soundManager.playMusicFast(getRandomBattleMusicPath());
            }
            
            // Initialize level select scroll position when entering
            if (newState == GameState.LEVEL_SELECT) {
                // Always snap to the player's current level
                int selectedLevel = Math.max(1, gameData.getCurrentLevel());
                System.out.println("DEBUG: Entering LEVEL_SELECT - currentLevel: " + selectedLevel + ", hasSavedGame: " + hasSavedGame);
                gameData.setSelectedLevelView(selectedLevel);
                levelSelectScroll = selectedLevel;
                levelSelectScrollAnimated = selectedLevel;
            }
            
            // Initialize leaderboard screen animation state
            if (newState == GameState.LEADERBOARD) {
                leaderboardScreenTimer = 0;
                leaderboardAnimSkipped = false;
                leaderboardReadyToExit = false;
                lbSfxTimeReveal = false;
                lbSfxPanelSlide = false;
                lbSfxResult = false;
                if (renderer != null) renderer.setScreenEnteredTime(gradientTime);
                // Play a mellow victory track distinct from battle/menu music
                String[] victoryTracks = {
                    "SFX/Music Tracks/No Melody/Chilling Outside No Melody.wav",
                    "SFX/Music Tracks/No Melody/Made to Build No Melody.wav",
                    "SFX/Music Tracks/No Melody/Burning Grounds No Melody.wav"
                };
                soundManager.playMusic(victoryTracks[(int)(Math.random() * victoryTracks.length)]);
            }
            
            // Initialize leaderboard view screen
            if (newState == GameState.LEADERBOARD_VIEW) {
                leaderboardViewScroll = 0;
                leaderboardViewScrollAnimated = 0;
                // Default to current game mode's difficulty tab
                if (gameData != null && gameData.getGameMode() != null) {
                    selectedLeaderboardDifficulty = gameData.getGameMode().ordinal();
                }
            }
            
            // Reset shop scroll when entering shop
            if (newState == GameState.SHOP) {
                shopScroll = 0;
                shopScrollAnimated = 0;
                shopManager.rebuildSortedOrder(); // Re-sort so maxed items appear at bottom
                
                // Check for newly unlocked passive upgrades to show introduction animations
                pendingPassiveUnlocks.clear();
                passiveUnlockAnimation = false;
                passiveUnlockDismissing = false;
                int bestLevel = gameData.getBestRunLevel();
                for (PassiveUpgrade upgrade : passiveUpgradeManager.getAllUpgrades()) {
                    int unlockLvl = upgrade.getUnlockLevel();
                    if (unlockLvl > 0 && unlockLvl <= bestLevel && !gameData.hasSeenPassiveUnlock(upgrade.getId())) {
                        pendingPassiveUnlocks.add(upgrade);
                    }
                }
                // Start first animation if any unlocks are pending
                if (!pendingPassiveUnlocks.isEmpty()) {
                    startNextPassiveUnlockAnimation();
                }
            }
            
            // Reset save select scroll when entering save select
            if (newState == GameState.SAVE_SELECT) {
                saveSelectScroll = 0;
                saveSelectScrollAnimated = 0;
                selectedSaveSlot = 0;
                deletingSlot = false;
                deleteConfirmTimer = 0;
                refreshSaveMetadata();
            }
            
            // Reset stats scroll when entering stats screen
            if (newState == GameState.STATS) {
                statsScroll = 0;
                statsScrollAnimated = 0;
                selectedStatItem = 0; // Start at top (active item)
                // Sync display index to currently equipped item
                if (renderer != null) {
                    renderer.setStatsActiveItemDisplayIndex(getEquippedItemDisplayIndex());
                }
            }
            
            // Reset achievements scroll when entering achievements screen
            if (newState == GameState.ACHIEVEMENTS) {
                achievementsScroll = 0;
                achievementsScrollAnimated = 0;
            }
            
            previousState = gameState;
            gameState = newState;
            stateTransitionProgress = 0.0f;
        }
    }
    
    // Public getters for InputHandler (if needed)
    public GameState getGameState() { return gameState; }
    public double getFrostBeamAngle() { return frostBeamAngle; }
    public double getFrostBeamProgress() { return frostBeamProgress; }
    public void setGameState(GameState state) { this.gameState = state; }
    public void selectPreviousStat() { selectedStatItem = Math.max(0, selectedStatItem - 1); }
    public void selectNextStat() { selectedStatItem = Math.min(3, selectedStatItem + 1); }
    public void decreaseUpgrade() { gameData.adjustUpgrade(selectedStatItem, -1); }
    public void increaseUpgrade() { gameData.adjustUpgrade(selectedStatItem, 1); }
    public void selectPreviousShopItem() { shopManager.selectPrevious(); }
    public void selectNextShopItem() { shopManager.selectNext(); }
    public void purchaseSelectedItem() { 
        int selected = shopManager.getSelectedShopItem();
        if (selected == 0) startGame();
        else shopManager.purchaseItem(selected);
    }
    
    private void updateShopScroll() {
        // Calculate target scroll offset to keep selected item centered
        int selectedItem = shopManager.getSelectedShopItem();
        int itemsVisible = 6; // Number of items visible on screen
        
        // Only scroll if selection is beyond visible area
        if (selectedItem > itemsVisible - 3) {
            shopScroll = (selectedItem - (itemsVisible - 3)) * 100; // 100 pixels per item
        } else {
            shopScroll = 0;
        }
    }
    
    private void updateStatsScroll() {
        // Calculate target scroll offset to keep selected item centered
        int itemsVisible = 6; // Number of items visible on screen
        
        // Only scroll if selection is beyond visible area
        if (selectedStatItem > itemsVisible - 2) {
            statsScroll = (selectedStatItem - (itemsVisible - 2)) * 90; // 90 pixels per item (card height + padding)
        } else {
            statsScroll = 0;
        }
    }
    
    private void toggleSetting(int settingIndex) {
        // Category 0: Graphics (15 settings, indices 0-14 matching renderer)
        // Category 1: Audio (5 settings)
        // Category 2: Gameplay (1 setting)
        // Category 3: Debug (2 settings)
        // Category 4: Controls (10 settings)
        
        if (selectedSettingsCategory == 0) {
            int offset = getGPUSettingsOffset();
            // GPU-specific settings (only when GPU is available)
            if (gpuAvailable && settingIndex == 0) {
                enableGPUAcceleration = !enableGPUAcceleration; saveGPUConfig(); markNeedsRestart(); return;
            }
            if (gpuAvailable && enableGPUAcceleration && settingIndex == 1) {
                gpuPipelineType = (gpuPipelineType + 1) % 3; saveGPUConfig(); markNeedsRestart(); return;
            }
            if (gpuAvailable && enableGPUAcceleration && settingIndex == 2) {
                bufferStrategyMode = (bufferStrategyMode + 1) % 2; saveGPUConfig(); markNeedsRestart(); return;
            }
            // Original graphics settings shifted by offset
            int idx = settingIndex - offset;
            switch (idx) {
                case 0: toggleFullscreen(); break;
                case 1: resolutionPreset = (resolutionPreset + 1) % 6; break;
                case 2: enableVSync = !enableVSync; break;
                case 3: fpsLimit = (fpsLimit + 1) % 5; updateFPSLimit(); break;
                case 4: enableAntiAliasing = !enableAntiAliasing; break;
                case 5: shadowQuality = (shadowQuality + 1) % 4; enableShadows = shadowQuality > 0; break;
                case 6: enableParticles = !enableParticles; break;
                case 7: enableBloom = !enableBloom; break;
                case 8: enableMotionBlur = !enableMotionBlur; break;
                case 9: enableChromaticAberration = !enableChromaticAberration; break;
                case 10: enableVignette = !enableVignette; break;
                case 11: enableGrainEffect = !enableGrainEffect; break;
                case 12: /* Camera Zoom - handled by adjustSetting */ break;
                case 13: enableUIParallax = !enableUIParallax; break;
                case 14: uiScale = (uiScale + 1) % 3; config.UIScale.setScale(uiScale); if (renderer != null) renderer.onUIScaleChanged(); break;
            }
        } else if (selectedSettingsCategory == 1) {
            // Audio settings
            if (settingIndex == 0) {
                gameData.setSoundEnabled(!gameData.isSoundEnabled());
                soundManager.setSoundEnabled(gameData.isSoundEnabled());
            } else if (settingIndex == 5) {
                // Spatial Audio toggle
                gameData.setSpatialAudioEnabled(!gameData.isSpatialAudioEnabled());
                soundManager.setSpatialAudioEnabled(gameData.isSpatialAudioEnabled());
            }
        } else if (selectedSettingsCategory == 2) {
            // Gameplay settings
            if (settingIndex == 0) {
                // Cycle through: 0=None, 1=Resume Only, 2=Always
                gameData.setCountdownMode((gameData.getCountdownMode() + 1) % 3);
            }
        } else if (selectedSettingsCategory == 3) {
            // Debug settings
            if (settingIndex == 0) {
                enableHitboxes = !enableHitboxes;
            } else if (settingIndex == 1) {
                showTrackName = !showTrackName;
            }
        } else if (selectedSettingsCategory == 4) {
            // Controls settings
            if (settingIndex == 0) {
                // Preset - cycle forward
                keyBindManager.nextPreset(controllerManager != null && controllerManager.isConnected());
            } else if (settingIndex == 1) {
                // Input Device - read only, no toggle
            } else if (settingIndex == 2) {
                // Keyboard section header - toggle expand/collapse
                controlsKeyboardExpanded = !controlsKeyboardExpanded;
                if (!controlsKeyboardExpanded && selectedSettingsItem > 2 && selectedSettingsItem < 12) {
                    selectedSettingsItem = 2;
                }
            } else if (settingIndex >= 3 && settingIndex <= 11) {
                // Keyboard action rebinding
                waitingForKeyBind = true;
                rebindingActionIndex = settingIndex - 2; // Maps to Action ordinal + 1 offset
                rebindingController = false;
            } else if (settingIndex == 12) {
                // Controller section header - toggle expand/collapse
                controlsControllerExpanded = !controlsControllerExpanded;
                if (!controlsControllerExpanded && selectedSettingsItem > 12 && selectedSettingsItem < 22) {
                    selectedSettingsItem = 12;
                }
            } else if (settingIndex >= 13 && settingIndex <= 21) {
                // Controller action rebinding
                waitingForKeyBind = true;
                rebindingActionIndex = settingIndex - 12; // Maps to Action ordinal + 1 offset
                rebindingController = true;
            }
        }
        markSettingsDirty();
    }
    
    /** Set a slider setting directly from a 0..1 progress value (for click/drag on slider track). */
    private void setSliderValue(int settingIndex, float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (selectedSettingsCategory == 0) {
            int offset = getGPUSettingsOffset();
            if (settingIndex == 12 + offset) { // Camera Zoom: 0.75 .. 1.5
                double range = 1.5 - 0.75;
                double raw = 0.75 + progress * range;
                cameraZoom = Math.round(raw * 20.0) / 20.0; // snap to 0.05 steps
                cameraZoom = Math.max(0.75, Math.min(1.5, cameraZoom));
            }
        } else if (selectedSettingsCategory == 1) {
            float vol = Math.round(progress * 20f) / 20f; // snap to 0.05 steps
            switch (settingIndex) {
                case 1: gameData.setMasterVolume(vol); soundManager.setMasterVolume(vol); break;
                case 2: gameData.setSfxVolume(vol); soundManager.setSfxVolume(vol); break;
                case 3: gameData.setUiVolume(vol); soundManager.setUiVolume(vol); break;
                case 4: gameData.setMusicVolume(vol); soundManager.setMusicVolume(vol); break;
            }
        }
        markSettingsDirty();
    }

    private boolean adjustSetting(int settingIndex, int direction) {
        // Graphics sliders (matching renderer order with dynamic GPU offset)
        if (selectedSettingsCategory == 0) {
            int offset = getGPUSettingsOffset();
            // GPU-specific settings
            if (gpuAvailable && settingIndex == 0) { // Hardware Acceleration (toggle)
                enableGPUAcceleration = !enableGPUAcceleration;
                saveGPUConfig();
                markNeedsRestart();
                return true;
            }
            if (gpuAvailable && enableGPUAcceleration && settingIndex == 1) { // Pipeline Type (pill)
                gpuPipelineType = Math.max(0, Math.min(2, gpuPipelineType + direction));
                saveGPUConfig();
                markNeedsRestart();
                return true;
            }
            if (gpuAvailable && enableGPUAcceleration && settingIndex == 2) { // Buffer Mode (pill)
                bufferStrategyMode = Math.max(0, Math.min(1, bufferStrategyMode + direction));
                saveGPUConfig();
                markNeedsRestart();
                return true;
            }
            // Original settings shifted by offset
            int idx = settingIndex - offset;
            if (idx == 0) { // Fullscreen (toggle)
                toggleFullscreen();
                return true;
            } else if (idx == 1) { // Resolution Preset
                resolutionPreset = Math.max(0, Math.min(5, resolutionPreset + direction));
                markNeedsRestart();
                return true;
            } else if (idx == 2) { // VSync (toggle)
                enableVSync = !enableVSync;
                return true;
            } else if (idx == 3) { // FPS Limit
                fpsLimit = Math.max(0, Math.min(4, fpsLimit + direction));
                updateFPSLimit();
                return true;
            } else if (idx == 4) { // Anti-Aliasing (toggle)
                enableAntiAliasing = !enableAntiAliasing;
                return true;
            } else if (idx == 5) { // Shadow Quality (slider)
                shadowQuality = Math.max(0, Math.min(3, shadowQuality + direction));
                enableShadows = shadowQuality > 0;
                return true;
            } else if (idx == 6) { // Particle Effects (toggle)
                enableParticles = !enableParticles;
                return true;
            } else if (idx == 7) { // Bloom (toggle)
                enableBloom = !enableBloom;
                return true;
            } else if (idx == 8) { // Motion Blur (toggle)
                enableMotionBlur = !enableMotionBlur;
                return true;
            } else if (idx == 9) { // Chromatic Aberration (toggle)
                enableChromaticAberration = !enableChromaticAberration;
                return true;
            } else if (idx == 10) { // Vignette (toggle)
                enableVignette = !enableVignette;
                return true;
            } else if (idx == 11) { // Grain Effect (toggle)
                enableGrainEffect = !enableGrainEffect;
                return true;
            } else if (idx == 12) { // Camera Zoom
                double step = 0.05 * direction;
                cameraZoom = Math.max(0.75, Math.min(1.5, cameraZoom + step));
                return true;
            } else if (idx == 13) { // UI Parallax (toggle)
                enableUIParallax = !enableUIParallax;
                return true;
            } else if (idx == 14) { // UI Scale (pill)
                uiScale = Math.max(0, Math.min(2, uiScale + direction));
                config.UIScale.setScale(uiScale);
                if (renderer != null) renderer.onUIScaleChanged();
                return true;
            }
        }
        // Audio sliders
        else if (selectedSettingsCategory == 1) {
            if (settingIndex == 0) { // Sound Enabled (toggle)
                gameData.setSoundEnabled(!gameData.isSoundEnabled());
                soundManager.setSoundEnabled(gameData.isSoundEnabled());
                return true;
            }
            float step = 0.05f * direction;
            switch (settingIndex) {
                case 1: // Master Volume
                    gameData.setMasterVolume(gameData.getMasterVolume() + step);
                    soundManager.setMasterVolume(gameData.getMasterVolume());
                    return true;
                case 2: // SFX Volume
                    gameData.setSfxVolume(gameData.getSfxVolume() + step);
                    soundManager.setSfxVolume(gameData.getSfxVolume());
                    return true;
                case 3: // UI Volume
                    gameData.setUiVolume(gameData.getUiVolume() + step);
                    soundManager.setUiVolume(gameData.getUiVolume());
                    return true;
                case 4: // Music Volume
                    gameData.setMusicVolume(gameData.getMusicVolume() + step);
                    soundManager.setMusicVolume(gameData.getMusicVolume());
                    return true;
                case 5: // Spatial Audio (toggle)
                    gameData.setSpatialAudioEnabled(!gameData.isSpatialAudioEnabled());
                    soundManager.setSpatialAudioEnabled(gameData.isSpatialAudioEnabled());
                    return true;
            }
        } else if (selectedSettingsCategory == 2) {
            // Gameplay settings
            if (settingIndex == 0) { // Resume Countdown
                int newMode = gameData.getCountdownMode() + direction;
                if (newMode < 0) newMode = 2;
                if (newMode > 2) newMode = 0;
                gameData.setCountdownMode(newMode);
                return true;
            }
        } else if (selectedSettingsCategory == 3) {
            // Debug settings
            if (settingIndex == 0) { // Show Hitboxes (toggle)
                enableHitboxes = !enableHitboxes;
                return true;
            } else if (settingIndex == 1) { // Show Track Name (toggle)
                showTrackName = !showTrackName;
                return true;
            }
        } else if (selectedSettingsCategory == 4) {
            // Controls settings
            if (settingIndex == 0) { // Preset
                if (direction > 0) keyBindManager.nextPreset(controllerManager != null && controllerManager.isConnected());
                else keyBindManager.prevPreset(controllerManager != null && controllerManager.isConnected());
                return true;
            } else if (settingIndex == 1) { // Input Device (read only)
                return false;
            } else if (settingIndex == 2) { // Keyboard section header
                controlsKeyboardExpanded = !controlsKeyboardExpanded;
                if (!controlsKeyboardExpanded && selectedSettingsItem > 2 && selectedSettingsItem < 12) {
                    selectedSettingsItem = 2;
                }
                return true;
            } else if (settingIndex >= 3 && settingIndex <= 11) {
                // Keyboard action rebinding
                waitingForKeyBind = true;
                rebindingActionIndex = settingIndex - 2;
                rebindingController = false;
                return true;
            } else if (settingIndex == 12) { // Controller section header
                controlsControllerExpanded = !controlsControllerExpanded;
                if (!controlsControllerExpanded && selectedSettingsItem > 12 && selectedSettingsItem < 22) {
                    selectedSettingsItem = 12;
                }
                return true;
            } else if (settingIndex >= 13 && settingIndex <= 21) {
                // Controller action rebinding
                waitingForKeyBind = true;
                rebindingActionIndex = settingIndex - 12;
                rebindingController = true;
                return true;
            }
            return false;
        }
        // Setting not adjustable with left/right
        return false;
    }
    
    /** Set a graphics setting directly by pill option index */
    private void setGraphicsPillValue(int settingIndex, int pillIndex) {
        int offset = getGPUSettingsOffset();
        // GPU-specific pills
        if (gpuAvailable && enableGPUAcceleration && settingIndex == 1) {
            gpuPipelineType = Math.max(0, Math.min(2, pillIndex));
            saveGPUConfig();
            markNeedsRestart();
            return;
        }
        if (gpuAvailable && enableGPUAcceleration && settingIndex == 2) {
            bufferStrategyMode = Math.max(0, Math.min(1, pillIndex));
            saveGPUConfig();
            markNeedsRestart();
            return;
        }
        int idx = settingIndex - offset;
        switch (idx) {
            case 0: // Fullscreen: 0=Windowed, 1=Fullscreen
                boolean wantFull = pillIndex == 1;
                if (wantFull != isFullscreen) toggleFullscreen();
                break;
            case 1: // Resolution
                resolutionPreset = Math.max(0, Math.min(5, pillIndex));
                markNeedsRestart();
                break;
            case 3: // FPS Limit
                fpsLimit = Math.max(0, Math.min(4, pillIndex));
                updateFPSLimit();
                break;
            case 5: // Shadow Quality
                shadowQuality = Math.max(0, Math.min(3, pillIndex));
                enableShadows = shadowQuality > 0;
                break;
            case 14: // UI Scale
                uiScale = Math.max(0, Math.min(2, pillIndex));
                config.UIScale.setScale(uiScale);
                if (renderer != null) renderer.onUIScaleChanged();
                break;
        }
        markSettingsDirty();
    }
    
    private int getMaxSettingsItems() {
        if (selectedSettingsCategory == 0) return 14 + getGPUSettingsOffset(); // Graphics: 15 base items (0-14) + GPU settings
        if (selectedSettingsCategory == 1) return 5; // Audio: 6 items (0-5)
        if (selectedSettingsCategory == 2) return 0; // Gameplay: 1 item (0)
        if (selectedSettingsCategory == 3) return 1; // Debug: 2 items (0-1)
        if (selectedSettingsCategory == 4) {
            // Controls: Preset(0), InputDevice(1), KeyboardHeader(2), 9 keyboard actions(3-11), ControllerHeader(12), 9 controller actions(13-21)
            int max = 12; // Always include up to controller header
            if (controlsControllerExpanded) max = 21;
            return max;
        }
        if (selectedSettingsCategory == 5) return -1; // HUD: no list items, editor handles interaction
        return 0;
    }

    /** Skip over collapsed section items in controls tab after navigation. */
    private void skipCollapsedControlsItems(int direction) {
        if (selectedSettingsCategory != 4) return;
        // Skip collapsed keyboard items (3-11)
        if (!controlsKeyboardExpanded && selectedSettingsItem >= 3 && selectedSettingsItem <= 11) {
            selectedSettingsItem = direction > 0 ? 12 : 2;
        }
        // Skip collapsed controller items (13-21)
        if (!controlsControllerExpanded && selectedSettingsItem >= 13 && selectedSettingsItem <= 21) {
            selectedSettingsItem = direction > 0 ? 21 : 12;
        }
        // Clamp to max
        int max = getMaxSettingsItems();
        if (selectedSettingsItem > max) selectedSettingsItem = max;
    }

    /** Clamp selectedSettingsItem so it never exceeds the new tab's item count. */
    private void clampSettingsItem() {
        if (selectedSettingsItem > 0) {
            int max = getMaxSettingsItems();
            if (max < 0) {
                // HUD tab: no item list
                selectedSettingsItem = -1;
            } else if (selectedSettingsItem > max) {
                selectedSettingsItem = max;
            }
        }
        // Initialize HUD editor when switching to HUD tab
        if (selectedSettingsCategory == 5 && renderer != null) {
            renderer.hudLayoutEditor.onOpen(hudLayout);
        }
    }
    
    /** Take a snapshot of all current settings when the user enters the Settings screen. */
    public void snapshotSettings() {
        // Graphics
        snap_isFullscreen = isFullscreen;
        snap_resolutionPreset = resolutionPreset;
        snap_enableVSync = enableVSync;
        snap_fpsLimit = fpsLimit;
        snap_enableAntiAliasing = enableAntiAliasing;
        snap_backgroundMode = backgroundMode;
        snap_enableGradientAnimation = enableGradientAnimation;
        snap_gradientQuality = gradientQuality;
        snap_enableGrainEffect = enableGrainEffect;
        snap_enableParticles = enableParticles;
        snap_enableShadows = enableShadows;
        snap_shadowQuality = shadowQuality;
        snap_enableBloom = enableBloom;
        snap_enableMotionBlur = enableMotionBlur;
        snap_enableChromaticAberration = enableChromaticAberration;
        snap_enableVignette = enableVignette;
        snap_cameraZoom = cameraZoom;
        snap_enableUIParallax = enableUIParallax;
        snap_uiScale = uiScale;
        // GPU
        snap_enableGPUAcceleration = enableGPUAcceleration;
        snap_gpuPipelineType = gpuPipelineType;
        snap_bufferStrategyMode = bufferStrategyMode;
        // Audio
        snap_soundEnabled = gameData.isSoundEnabled();
        snap_masterVolume = gameData.getMasterVolume();
        snap_sfxVolume = gameData.getSfxVolume();
        snap_uiVolume = gameData.getUiVolume();
        snap_musicVolume = gameData.getMusicVolume();
        snap_spatialAudioEnabled = gameData.isSpatialAudioEnabled();
        // Gameplay
        snap_countdownMode = gameData.getCountdownMode();
        // Debug
        snap_enableHitboxes = enableHitboxes;
        snap_showTrackName = showTrackName;
        // Controls
        if (keyBindManager != null) {
            snap_keyBinds = keyBindManager.exportKeyBinds();
            snap_presetOrdinal = keyBindManager.exportPresetOrdinal();
        }
        // Reset state
        settingsDirty = false;
        settingsNeedsRestart = false;
        showSettingsWarning = false;
        settingsWarningSelection = 0;
        System.out.println("[Settings] Snapshot taken");
    }
    
    /** Restore all settings to the snapshot taken when entering Settings (discard changes). */
    public void restoreSettings() {
        // Graphics
        if (snap_isFullscreen != isFullscreen) toggleFullscreen();
        resolutionPreset = snap_resolutionPreset;
        enableVSync = snap_enableVSync;
        fpsLimit = snap_fpsLimit; updateFPSLimit();
        enableAntiAliasing = snap_enableAntiAliasing;
        backgroundMode = snap_backgroundMode;
        enableGradientAnimation = snap_enableGradientAnimation;
        gradientQuality = snap_gradientQuality;
        enableGrainEffect = snap_enableGrainEffect;
        enableParticles = snap_enableParticles;
        enableShadows = snap_enableShadows;
        shadowQuality = snap_shadowQuality;
        enableBloom = snap_enableBloom;
        enableMotionBlur = snap_enableMotionBlur;
        enableChromaticAberration = snap_enableChromaticAberration;
        enableVignette = snap_enableVignette;
        cameraZoom = snap_cameraZoom;
        enableUIParallax = snap_enableUIParallax;
        uiScale = snap_uiScale;
        config.UIScale.setScale(uiScale);
        if (renderer != null) renderer.onUIScaleChanged();
        // GPU
        enableGPUAcceleration = snap_enableGPUAcceleration;
        gpuPipelineType = snap_gpuPipelineType;
        bufferStrategyMode = snap_bufferStrategyMode;
        saveGPUConfig();
        // Audio
        gameData.setSoundEnabled(snap_soundEnabled);
        soundManager.setSoundEnabled(snap_soundEnabled);
        gameData.setMasterVolume(snap_masterVolume);
        soundManager.setMasterVolume(snap_masterVolume);
        gameData.setSfxVolume(snap_sfxVolume);
        soundManager.setSfxVolume(snap_sfxVolume);
        gameData.setUiVolume(snap_uiVolume);
        soundManager.setUiVolume(snap_uiVolume);
        gameData.setMusicVolume(snap_musicVolume);
        soundManager.setMusicVolume(snap_musicVolume);
        gameData.setSpatialAudioEnabled(snap_spatialAudioEnabled);
        soundManager.setSpatialAudioEnabled(snap_spatialAudioEnabled);
        // Gameplay
        gameData.setCountdownMode(snap_countdownMode);
        // Debug
        enableHitboxes = snap_enableHitboxes;
        showTrackName = snap_showTrackName;
        // Controls
        if (keyBindManager != null) {
            keyBindManager.importPresetOrdinal(snap_presetOrdinal);
            keyBindManager.importKeyBinds(snap_keyBinds);
        }
        settingsDirty = false;
        settingsNeedsRestart = false;
        System.out.println("[Settings] Restored to snapshot (changes discarded)");
    }
    
    /** Commit current settings (apply): save, update snapshot, and optionally restart window. */
    public void applySettings() {
        performAutoSave();
        boolean needsRestart = settingsNeedsRestart;
        // Update snapshot to current values so further ESC won't revert
        snapshotSettings();
        System.out.println("[Settings] Applied" + (needsRestart ? " (restart required)" : ""));
        if (needsRestart) {
            restartWindow();
        }
    }
    
    /** Restart the game window to apply pipeline / resolution changes. */
    private void restartWindow() {
        System.out.println("[Settings] Restarting via new process (pipeline flags require fresh JVM)...");
        // Save GPU config so the new process reads updated pipeline flags
        saveGPUConfig();
        try {
            // Build command to launch a new JVM process
            String javaBin = System.getProperty("java.home") + java.io.File.separator
                + "bin" + java.io.File.separator + "java";
            String classpath = System.getProperty("java.class.path");
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", classpath, "App");
            pb.directory(new java.io.File(System.getProperty("user.dir")));
            pb.inheritIO(); // pipe stdout/stderr to parent console
            pb.start();
            System.out.println("[Settings] New process launched, exiting current JVM");
        } catch (Exception e) {
            System.err.println("[Settings] Failed to restart: " + e.getMessage());
            // Fallback: re-launch in same JVM (pipeline won't change but at least window refreshes)
            javax.swing.SwingUtilities.invokeLater(() -> {
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window w : windows) {
                    if (w instanceof javax.swing.JFrame) {
                        w.dispose();
                    }
                }
                App.main(new String[0]);
            });
            return;
        }
        System.exit(0);
    }
    
    /** Mark settings as dirty (something changed since last apply/snapshot). */
    public void markSettingsDirty() {
        settingsDirty = true;
    }
    
    /** Mark that a restart will be needed on apply (pipeline or resolution change). */
    public void markNeedsRestart() {
        settingsNeedsRestart = true;
        settingsDirty = true;
    }
    
    /** Execute the currently selected warning-dialog option (Apply & Exit / Discard & Exit / Cancel). */
    private void confirmWarningSelection() {
        if (settingsWarningSelection == 0) {
            // Apply & Exit
            soundManager.playSound(SoundManager.Sound.UI_SELECT);
            showSettingsWarning = false;
            applySettings();
            if (settingsEnteredFrom == GameState.PLAYING) {
                isPaused = true;
                gameState = GameState.PLAYING;
            } else {
                transitionToState(GameState.MENU);
            }
        } else if (settingsWarningSelection == 1) {
            // Discard & Exit
            soundManager.playSound(SoundManager.Sound.UI_CANCEL);
            showSettingsWarning = false;
            restoreSettings();
            if (settingsEnteredFrom == GameState.PLAYING) {
                isPaused = true;
                gameState = GameState.PLAYING;
            } else {
                transitionToState(GameState.MENU);
            }
        } else {
            // Cancel - close warning
            soundManager.playSound(SoundManager.Sound.UI_CANCEL);
            showSettingsWarning = false;
        }
        screenShakeIntensity = 3;
    }
    
    /** Reset only the current tab's settings to their defaults. */
    private void resetCurrentTabToDefaults() {
        switch (selectedSettingsCategory) {
            case 0: // Graphics
                resolutionPreset = 3;
                enableVSync = true;
                fpsLimit = 1; updateFPSLimit();
                enableAntiAliasing = true;
                backgroundMode = 0;
                enableGradientAnimation = true;
                gradientQuality = 1;
                enableGrainEffect = false;
                enableParticles = true;
                enableShadows = true;
                shadowQuality = 2;
                enableBloom = true;
                enableMotionBlur = false;
                enableChromaticAberration = true;
                enableVignette = true;
                cameraZoom = 1.0;
                enableUIParallax = true;
                uiScale = 1;
                config.UIScale.setScale(uiScale);
                if (renderer != null) renderer.onUIScaleChanged();
                enableGPUAcceleration = false;
                gpuPipelineType = 0;
                bufferStrategyMode = 1;
                saveGPUConfig();
                break;
            case 1: // Audio
                gameData.setSoundEnabled(true);
                soundManager.setSoundEnabled(true);
                gameData.setMasterVolume(1.0f);
                soundManager.setMasterVolume(1.0f);
                gameData.setSfxVolume(1.0f);
                soundManager.setSfxVolume(1.0f);
                gameData.setUiVolume(1.0f);
                soundManager.setUiVolume(1.0f);
                gameData.setMusicVolume(1.0f);
                soundManager.setMusicVolume(1.0f);
                gameData.setSpatialAudioEnabled(true);
                soundManager.setSpatialAudioEnabled(true);
                break;
            case 2: // Gameplay
                gameData.setCountdownMode(0);
                break;
            case 3: // Debug
                enableHitboxes = false;
                showTrackName = false;
                break;
            case 4: // Controls
                if (keyBindManager != null) keyBindManager.resetDefaults();
                break;
            case 5: // HUD
                // HUD layout reset handled by editor if needed
                break;
        }
        markSettingsDirty();
        System.out.println("[Settings] Reset tab " + selectedSettingsCategory + " to defaults");
    }
    
    private void resetSettingsToDefaults() {
        // Reset all graphics settings to defaults
        resolutionPreset = 3; // 1920x1080
        enableVSync = true;
        fpsLimit = 1; // 60 FPS
        updateFPSLimit();
        enableAntiAliasing = true;
        backgroundMode = 0; // Gradient
        enableGradientAnimation = true;
        gradientQuality = 1; // Medium
        enableGrainEffect = false;
        enableParticles = true;
        enableShadows = true;
        shadowQuality = 2; // Medium
        enableBloom = true;
        enableMotionBlur = false;
        enableChromaticAberration = true;
        enableVignette = true;
        cameraZoom = 1.0;
        enableUIParallax = true;
        uiScale = 1; // Medium (default)
        config.UIScale.setScale(uiScale);
        if (renderer != null) renderer.onUIScaleChanged();
        // Don't reset fullscreen - that's a user preference
        
        // Reset GPU acceleration settings
        enableGPUAcceleration = false;
        gpuPipelineType = 0; // Auto
        bufferStrategyMode = 1; // Triple buffer
        saveGPUConfig();
        
        // Reset all audio settings to defaults
        gameData.setSoundEnabled(true);
        soundManager.setSoundEnabled(true);
        gameData.setMasterVolume(1.0f);
        soundManager.setMasterVolume(1.0f);
        gameData.setSfxVolume(1.0f);
        soundManager.setSfxVolume(1.0f);
        gameData.setUiVolume(1.0f);
        soundManager.setUiVolume(1.0f);
        gameData.setMusicVolume(1.0f);
        soundManager.setMusicVolume(1.0f);
        gameData.setSpatialAudioEnabled(true);
        soundManager.setSpatialAudioEnabled(true);
        
        // Reset gameplay settings to defaults
        gameData.setCountdownMode(0); // None
        
        // Reset debug settings to defaults
        enableHitboxes = false;
        showTrackName = false;
        
        // Reset keybinds to defaults
        if (keyBindManager != null) keyBindManager.resetDefaults();
        
        System.out.println("All settings reset to defaults");
    }
    
    /**
     * Translate controller button presses into game actions each frame.
     * This mirrors handleKeyPress() for controller input.
     */
    /** Get display text for an action key (dynamic based on current keybinds) */
    private String keyText(KeyBindManager.Action action) {
        if (keyBindManager != null) return keyBindManager.getKeyDisplayText(action);
        switch (action) {
            case CONFIRM: return "SPACE"; case BACK: return "ESC";
            case USE_ITEM: return "E"; case PAUSE: return "P";
            case RESTART: return "R"; default: return "?";
        }
    }
    
    /** Get movement keys display text */
    private String moveKeysText() {
        if (keyBindManager != null) return keyBindManager.getMovementKeysText();
        return "WASD/Arrows";
    }
    
    private void handleControllerInput() {
        if (controllerManager == null || !controllerManager.isConnected()) return;
        
        // Tutorial popup dismiss — any controller button dismisses the popup
        if (tutorialMode && tutorialPopupActive) {
            if (tutorialPopupInputDelay > 0) return; // 2-second buffer
            if (controllerManager.getFirstJustPressedButton() != null) {
                tutorialPopupActive = false;
                renderer.tutorialPopupActive = false;
                soundManager.playSound(SoundManager.Sound.UI_SELECT);
                screenShakeIntensity = 2;
                
                if (gameState == GameState.PLAYING) {
                    // Reset per-step tracking for the next step
                    tutorialPlayerMoveDistance = 0;
                    tutorialGrazeCount = 0;
                    tutorialPlayerDied = false;
                    tutorialItemUsed = false;
                    tutorialShieldBlockCount = 0;
                    tutorialShopPurchased = false;
                    tutorialShopVisited = false;
                    if (player != null) {
                        tutorialPrevPlayerX = player.getX();
                        tutorialPrevPlayerY = player.getY();
                    }
                    
                    // For popup-only steps (Welcome, Complete), advance immediately
                    if (tutorialStep == 0 || tutorialStep == 7) {
                        advanceTutorialStep();
                    }
                    
                    // Resume normal speed
                    tutorialSlowdownPhase = 3; // SLOWING_OUT
                    tutorialSlowdownTimer = 30;
                }
            }
            return;
        }
        
        switch (gameState) {
            case MENU:
                // Tutorial prompt intercepts all controller input
                if (showTutorialPrompt) {
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                        tutorialPromptSelection = 1 - tutorialPromptSelection;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                        showTutorialPrompt = false;
                        if (tutorialPromptSelection == 0) {
                            startTutorial();
                        } else {
                            soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                        }
                        screenShakeIntensity = 3;
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                        showTutorialPrompt = false;
                        soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                        screenShakeIntensity = 2;
                    }
                    break;
                }
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    selectedMenuItem = Math.max(0, selectedMenuItem - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    selectedMenuItem = Math.min(6, selectedMenuItem + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    activateMenuItem(selectedMenuItem);
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    if (escapeTimer > 0) {
                        System.exit(0);
                    } else {
                        escapeTimer = ESCAPE_TIMEOUT;
                        screenShakeIntensity = 3;
                    }
                }
                break;
                
            case SAVE_SELECT:
                // Handle controller X button hold for delete
                if (controllerManager.isJustPressed(KeyBindManager.ControllerButton.X)) {
                    if (selectedSaveSlot < saveMetadataCache.size()) {
                        SaveManager.SaveMetadata meta = saveMetadataCache.get(selectedSaveSlot);
                        if (saveManager.saveExists(meta.slotNumber) && !deletingSlot) {
                            deletingSlot = true;
                            deleteConfirmTimer = 0;
                            controllerDeleteActive = true;
                            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        }
                    }
                }
                // Cancel controller delete when X button released
                if (controllerDeleteActive && !controllerManager.isPressed(KeyBindManager.ControllerButton.X)) {
                    deletingSlot = false;
                    deleteConfirmTimer = 0;
                    controllerDeleteActive = false;
                }
                
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    selectedSaveSlot = Math.max(0, selectedSaveSlot - 1);
                    ensureSaveSlotVisible();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                    deletingSlot = false;
                    deleteConfirmTimer = 0;
                    controllerDeleteActive = false;
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    int maxIndex = saveMetadataCache.size();
                    selectedSaveSlot = Math.min(maxIndex, selectedSaveSlot + 1);
                    ensureSaveSlotVisible();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                    deletingSlot = false;
                    deleteConfirmTimer = 0;
                    controllerDeleteActive = false;
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    // Simulate Enter key press for save slot selection
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    if (escapeTimer > 0) {
                        System.exit(0);
                    } else {
                        escapeTimer = ESCAPE_TIMEOUT;
                        screenShakeIntensity = 3;
                    }
                }
                break;
                
            case MODE_SELECT:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;
                
            case NAME_INPUT:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT)) {
                    // Move on-screen keyboard cursor left
                    onScreenKbCol = Math.max(0, onScreenKbCol - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                    // Move on-screen keyboard cursor right
                    String row = ON_SCREEN_KB_ROWS[onScreenKbRow];
                    onScreenKbCol = Math.min(row.length() - 1, onScreenKbCol + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    // Press selected on-screen key directly
                    pressOnScreenKey();
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;
                
            case LEVEL_SELECT:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_LEFT, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;
                
            case SETTINGS:
                if (selectedSettingsCategory == 5 && renderer != null) {
                    // HUD tab: controller sticks move a virtual mouse cursor
                    float stickX = controllerManager.getRawLeftStickX();
                    float stickY = controllerManager.getRawLeftStickY();
                    boolean cursorMoved = false;
                    if (stickX != 0 || stickY != 0) {
                        mouseX = Math.max(0, Math.min(WIDTH, mouseX + (int)(stickX * CONTROLLER_CURSOR_SPEED)));
                        mouseY = Math.max(0, Math.min(HEIGHT, mouseY + (int)(stickY * CONTROLLER_CURSOR_SPEED)));
                        cursorMoved = true;
                    }
                    // D-pad also moves cursor (digital, fixed speed)
                    if (controllerManager.isPressed(KeyBindManager.ControllerButton.DPAD_LEFT))  { mouseX = Math.max(0, mouseX - 5); cursorMoved = true; }
                    if (controllerManager.isPressed(KeyBindManager.ControllerButton.DPAD_RIGHT)) { mouseX = Math.min(WIDTH, mouseX + 5); cursorMoved = true; }
                    if (controllerManager.isPressed(KeyBindManager.ControllerButton.DPAD_UP))    { mouseY = Math.max(0, mouseY - 5); cursorMoved = true; }
                    if (controllerManager.isPressed(KeyBindManager.ControllerButton.DPAD_DOWN))  { mouseY = Math.min(HEIGHT, mouseY + 5); cursorMoved = true; }
                    // A button = mouse press/drag/release
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                        controllerHudMouseDown = true;
                        renderer.hudLayoutEditor.handleMousePressed(mouseX, mouseY, hudLayout);
                    } else if (controllerHudMouseDown && !controllerManager.isActionPressed(KeyBindManager.Action.CONFIRM)) {
                        controllerHudMouseDown = false;
                        renderer.hudLayoutEditor.handleMouseReleased(hudLayout);
                    } else if (controllerHudMouseDown && cursorMoved) {
                        renderer.hudLayoutEditor.handleMouseDragged(mouseX, mouseY, hudLayout);
                    }
                    // Update hover state and warp system cursor when cursor moves
                    if (cursorMoved) {
                        handleMouseMove();
                        warpCursorToGameCoords(mouseX, mouseY);
                    }
                    // RB = tab switch (still works in HUD tab)
                    if (controllerManager.isJustPressed(KeyBindManager.ControllerButton.RB)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, ' '));
                    }
                    // B = back
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                    }
                } else {
                    // Non-HUD settings tabs: normal keyboard-style navigation
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_LEFT, ' '));
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, ' '));
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                    } else if (controllerManager.isJustPressed(KeyBindManager.ControllerButton.RB)) {
                        // Tab switch with RB
                        handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, ' '));
                    } else if (controllerManager.isJustPressed(KeyBindManager.ControllerButton.Y)) {
                        // Reset current tab to defaults with Y button
                        resetCurrentTabToDefaults();
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        screenShakeIntensity = 5;
                    }
                }
                break;
                
            case PLAYING:
                // Tutorial completion screen
                if (tutorialMode && tutorialCompleteScreen) {
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                        tutorialCompleteSelection = 1 - tutorialCompleteSelection;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                        if (tutorialCompleteSelection == 0) {
                            completeTutorial();
                        } else {
                            completeTutorial();
                            startTutorial();
                        }
                        screenShakeIntensity = 5;
                    }
                    break;
                }
                if (unpauseCountdownActive) {
                    // Any controller button skips the countdown
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.BACK) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.PAUSE) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT) ||
                        controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                        unpauseCountdownActive = false;
                        lastCountdownSecond = -1;
                        soundManager.playSound(SoundManager.Sound.COUNTDOWN_GO);
                        screenShakeIntensity = 2;
                    }
                } else if (isPaused) {
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                        selectedPauseItem = Math.max(0, selectedPauseItem - 1);
                        screenShakeIntensity = 1;
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                        int maxPauseIndex = renderer.getActivePauseButtonCount() - 1;
                        selectedPauseItem = Math.min(maxPauseIndex, selectedPauseItem + 1);
                        screenShakeIntensity = 1;
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                        screenShakeIntensity = 3;
                        activatePauseMenuItem(selectedPauseItem);
                    } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                        isPaused = false;
                        if (gameData.getCountdownMode() == 2) {
                            unpauseCountdownActive = true;
                            unpauseCountdownTimer = UNPAUSE_COUNTDOWN_DURATION;
                        }
                        soundManager.playSound(SoundManager.Sound.UNPAUSE);
                        screenShakeIntensity = 2;
                    }
                } else {
                    // Gameplay - pause with Start
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.PAUSE)) {
                        soundManager.playSound(SoundManager.Sound.PAUSE);
                        isPaused = true;
                        selectedPauseItem = 0;
                        renderer.configurePauseMenu(debugShowcaseInGameplay, tutorialMode);
                        screenShakeIntensity = 3;
                    }
                    // Use item with A button (isActionJustPressed already handles single-press detection)
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.USE_ITEM) && !introPanActive && !bossIntroActive) {
                        ActiveItem item = gameData.getEquippedItem();
                        if (item != null && item.canActivate()) {
                            item.activate();
                            // Apply item cooldown passive reduction
                            double cdMult = passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.ITEM_COOLDOWN);
                            if (cdMult < 1.0) item.setCurrentCooldown(item.getCurrentCooldown() * cdMult);
                            screenShakeIntensity = 3;
                            if (item.getActiveDuration() == 0) {
                                handleActiveItemEffects(item, 1.0);
                            }
                        }
                    }
                    // Skip intros
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                        if (introPanActive) {
                            introPanActive = false;
                            cameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                            cameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                            screenShakeIntensity = 8;
                            if (currentBoss != null) currentBoss.setPosition(currentBoss.getX(), 100);
                            if (demoIntroActive) {
                                demoIntroActive = false;
                                transitionToState(GameState.MENU);
                            }
                        }
                    }
                    // Restart with Y
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.RESTART)) {
                        if (debugShowcaseInGameplay) {
                            resetShowcase();
                        } else {
                            startGame();
                        }
                    }
                }
                break;
                
            case GAME_OVER:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;

            case DEMO_OVER:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                }
                break;
                
            case WIN:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                }
                break;
            
            case LEADERBOARD:
                // Any controller button skips animation or exits
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM) ||
                    controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                }
                break;
                
            case SHOP:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;
                
            case ATTACK_INTRO:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM) ||
                    controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    dismissAttackIntro();
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                }
                break;
                
            case ACHIEVEMENTS:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;

            case ATTACK_SHOWCASE:
                // Showcase carousel: controller mirrors keyboard navigation
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_LEFT, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                } else if (controllerManager.isJustPressed(KeyBindManager.ControllerButton.RB)) {
                    // Quick tab switch with RB
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, ' '));
                }
                break;
                
            case DEBUG:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;
                
            case RISK_CONTRACT:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT)) {
                    selectedRiskContract = Math.max(0, selectedRiskContract - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                    selectedRiskContract = Math.min(RISK_CONTRACT_NAMES.length - 1, selectedRiskContract + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    confirmRiskContract();
                    screenShakeIntensity = 5;
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    transitionToState(GameState.LEVEL_SELECT);
                    screenShakeIntensity = 3;
                }
                break;

            default:
                // For other states, map confirm/back to their equivalent keys
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;
        }
    }

    /** Get the current virtual mouse X position (for controller cursor in Renderer). */
    public static int getMouseX() { return instance != null ? instance.mouseX : 0; }

    /** Get the current virtual mouse Y position (for controller cursor in Renderer). */
    public static int getMouseY() { return instance != null ? instance.mouseY : 0; }

    /**
     * Warp the system cursor so it matches the current game-space mouseX/mouseY.
     * Transforms game coordinates back to screen coordinates and moves the OS pointer.
     */
    private void warpCursorToGameCoords(int gx, int gy) {
        if (robot == null) return;
        try {
            Point screenPos = getLocationOnScreen();
            int panelW = getWidth();
            int panelH = getHeight();
            double sx = (double) panelW / WIDTH;
            double sy = (double) panelH / HEIGHT;
            double scale = Math.min(sx, sy);
            int offX = (panelW - (int)(WIDTH * scale)) / 2;
            int offY = (panelH - (int)(HEIGHT * scale)) / 2;
            robot.mouseMove(screenPos.x + offX + (int)(gx * scale),
                            screenPos.y + offY + (int)(gy * scale));
        } catch (Exception ignored) {}
    }
    
    // Flag checked by game thread to recreate render buffers after fullscreen toggle
    private volatile boolean needsBufferRecreate = false;

    /** Load a save slot and sync all settings including fullscreen window state. */
    private void loadSaveSlot(int slot, String saveName) {
        SaveData saveData = saveManager.load(slot);
        if (saveData != null) {
            // Remember current window fullscreen state before save overwrites the flag
            boolean windowIsFullscreen = isFullscreen;
            saveData.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
            boolean wantFullscreen = isFullscreen; // loadIntoGameData set this from save data
            gameData.setCustomSaveName(saveName);
            if (renderer != null) renderer.hudLayout = hudLayout;
            hasSavedGame = saveData.hasSavedGame();
            savedLevel = saveData.getSavedLevel();
            savedResumeState = saveData.getResumeState();
            levelSelectScroll = gameData.getSelectedLevelView();
            levelSelectScrollAnimated = gameData.getSelectedLevelView();
            soundManager.setMasterVolume(gameData.getMasterVolume());
            soundManager.setSfxVolume(gameData.getSfxVolume());
            soundManager.setUiVolume(gameData.getUiVolume());
            soundManager.setMusicVolume(gameData.getMusicVolume());
            soundManager.setSoundEnabled(gameData.isSoundEnabled());
            soundManager.setSpatialAudioEnabled(gameData.isSpatialAudioEnabled());
            // Sync actual window fullscreen state with the loaded setting
            if (wantFullscreen != windowIsFullscreen) {
                isFullscreen = windowIsFullscreen; // reset so toggleFullscreen flips to desired state
                toggleFullscreen();
            }
            soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
            screenShakeIntensity = 5;
            transitionToState(GameState.MENU);
        }
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (window instanceof javax.swing.JFrame) {
            javax.swing.JFrame frame = (javax.swing.JFrame) window;
            
            java.awt.Rectangle screenBounds = frame.getGraphicsConfiguration().getBounds();
            
            // dispose() is required to change the undecorated property.
            // Render buffers are plain software BufferedImages, so they
            // survive the window dispose/recreate cycle.
            frame.dispose();
            
            if (isFullscreen) {
                // Borderless windowed fullscreen — covers the entire screen
                frame.setUndecorated(true);
                frame.setBounds(screenBounds);
            } else {
                // Windowed with title bar — 80% screen height, 16:9 aspect ratio
                frame.setUndecorated(false);
                frame.setExtendedState(javax.swing.JFrame.NORMAL);
                int contentH = (int)(screenBounds.height * 0.8);
                int contentW = (int)(contentH * 16.0 / 9.0);
                setPreferredSize(new java.awt.Dimension(contentW, contentH));
                frame.pack();
                frame.setLocationRelativeTo(null);
            }
            
            frame.setVisible(true);
            
            // Recreate render buffers for the new window
            needsBufferRecreate = true;
            
            // Request focus back to game panel
            this.requestFocusInWindow();
            
            // Force an immediate repaint so the new window isn't blank
            revalidate();
            repaint();
        }
    }
    
    private void updateFPSLimit() {
        // This method is called when FPS limit changes
        // The actual FPS limiting happens in the game loop
        System.out.println("FPS limit updated to: " + 
            (fpsLimit == 0 ? "30 FPS" : 
             fpsLimit == 1 ? "60 FPS" : 
             fpsLimit == 2 ? "120 FPS" : 
             fpsLimit == 3 ? "144 FPS" : "Unlimited"));
    }
    
    private boolean hasNoUpgradesPurchased() {
        // Check if any passive upgrade has been purchased on this save file
        // Active items are allowed - only passive upgrades disqualify
        if (passiveUpgradeManager != null) {
            for (PassiveUpgrade upgrade : passiveUpgradeManager.getAllUpgrades()) {
                if (upgrade.getCurrentLevel() > 0) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private void startAssetLoading() {
        Thread loadingThread = new Thread(() -> {
            try {
                // Start loading screen music immediately
                soundManager.playMusicEarly("SFX/Music Tracks/Main/Chilling Outside.wav");
                targetLoadingProgress = 2;
                repaint();

                // --- Phase 1: Parallel asset loading ---
                // Weights: sounds=28%, boss=15%, bullet+player=15%, fonts+input=5% = 63% total
                java.util.concurrent.atomic.AtomicInteger progress = new java.util.concurrent.atomic.AtomicInteger(2);
                java.util.concurrent.CountDownLatch allAssets = new java.util.concurrent.CountDownLatch(4);
                java.util.concurrent.CountDownLatch fontsReady = new java.util.concurrent.CountDownLatch(1);

                // Thread 1: Preload all sound effects (28% of bar)
                Thread soundThread = new Thread(() -> {
                    try {
                        final int[] last = {0};
                        soundManager.preloadSounds((int pct) -> {
                            int now = (int)(pct * 0.28);
                            int delta = now - last[0];
                            if (delta > 0) {
                                last[0] = now;
                                targetLoadingProgress = progress.addAndGet(delta);
                                repaint();
                            }
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                    finally { allAssets.countDown(); }
                }, "Load-Sounds");

                // Thread 2: Preload boss sprites (15% of bar)
                Thread bossThread = new Thread(() -> {
                    try {
                        final int[] last = {0};
                        Boss.preloadSprites((int pct) -> {
                            int now = (int)(pct * 0.15);
                            int delta = now - last[0];
                            if (delta > 0) {
                                last[0] = now;
                                targetLoadingProgress = progress.addAndGet(delta);
                                repaint();
                            }
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                    finally { allAssets.countDown(); }
                }, "Load-Boss");

                // Thread 3: Preload bullet + player sprites (15% of bar)
                Thread bulletThread = new Thread(() -> {
                    try {
                        final int[] last = {0};
                        Bullet.preloadSprites((int pct) -> {
                            int now = (int)(pct * 0.10);
                            int delta = now - last[0];
                            if (delta > 0) {
                                last[0] = now;
                                targetLoadingProgress = progress.addAndGet(delta);
                                repaint();
                            }
                        });
                        Player.preloadSprites();
                        targetLoadingProgress = progress.addAndGet(5);
                        repaint();
                    } catch (Exception e) { e.printStackTrace(); }
                    finally { allAssets.countDown(); }
                }, "Load-Bullets");

                // Thread 4: Fonts + controller/keyboard sprites (5% of bar)
                Thread fontThread = new Thread(() -> {
                    try {
                        AssetLoader.initAll();
                        fontsReady.countDown();
                        if (keyBindManager != null) {
                            keyBindManager.loadControllerSprites();
                            keyBindManager.loadKeyboardSprites();
                        }
                        targetLoadingProgress = progress.addAndGet(5);
                        repaint();
                    } catch (Exception e) {
                        e.printStackTrace();
                        fontsReady.countDown();
                    } finally { allAssets.countDown(); }
                }, "Load-Fonts");

                soundThread.setDaemon(true);
                bossThread.setDaemon(true);
                bulletThread.setDaemon(true);
                fontThread.setDaemon(true);
                soundThread.start();
                bossThread.start();
                bulletThread.start();
                fontThread.start();

                // Wait for all parallel tasks to finish
                allAssets.await();
                targetLoadingProgress = 65;
                repaint();

                // --- Phase 2: Create renderer (requires fonts) ---
                fontsReady.await();
                renderer = new Renderer(gameData, shopManager, passiveUpgradeManager, (int bgPercent) -> {
                    targetLoadingProgress = 65 + (int)(bgPercent * 0.25);
                    repaint();
                });
                renderer.hudLayout = hudLayout;
                renderer.setLeaderboardManager(leaderboardManager);
                targetLoadingProgress = 90;
                repaint();

                // --- Phase 3: Finalize ---
                Thread.sleep(200);
                targetLoadingProgress = 100;
                repaint();

                Thread.sleep(300);
                loadingComplete = true;
                if (DEMO_MODE) {
                    setupDemoSave();
                    transitionToState(GameState.MENU);
                } else {
                    transitionToState(GameState.SAVE_SELECT);
                }
                repaint();

            } catch (Exception e) {
                e.printStackTrace();
                // On error, still go to save selection
                loadingComplete = true;
                if (DEMO_MODE) {
                    setupDemoSave();
                    transitionToState(GameState.MENU);
                } else {
                    transitionToState(GameState.SAVE_SELECT);
                }
                repaint();
            }
        });
        loadingThread.start();
    }
    
    private void drawSimpleLoading(Graphics2D g, int width, int height, int progress) {
        // Smooth interpolation of progress
        double smoothSpeed = 0.15;
        displayedLoadingProgress += (targetLoadingProgress - displayedLoadingProgress) * smoothSpeed;
        int smoothProgress = (int)displayedLoadingProgress;
        double time = gradientTime;
        
        // Expand window to fullscreen at 55% loaded — detect current monitor dynamically
        if (!loadingExpanded && smoothProgress >= 55) {
            loadingExpanded = true;
            isFullscreen = true;
            java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (window instanceof javax.swing.JFrame) {
                javax.swing.JFrame frame = (javax.swing.JFrame) window;
                // Detect which monitor the window is currently on
                java.awt.GraphicsConfiguration gc = frame.getGraphicsConfiguration();
                java.awt.Rectangle currentScreenBounds = gc.getBounds();
                // Switch from decorated (windowed) to undecorated (fullscreen)
                frame.dispose();
                frame.setUndecorated(true);
                frame.setBounds(currentScreenBounds);
                frame.setVisible(true);
                this.requestFocusInWindow();
            }
        }
        
        // â”€â”€ Military themed background â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        UITheme.drawScreenBackground(g, width, height, time);
        
        // â”€â”€ Title â€” stencil-style with ember particles â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        UITheme.drawTitle(g, "MISSILE MAN", width, height / 2 - 120,
            ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED,
            time, FontPalette.TITLE_LARGE);
        
        // â”€â”€ Loading stage label â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        String stageText;
        if (smoothProgress < 30)       stageText = "LOADING AUDIO SYSTEMS";
        else if (smoothProgress < 45)  stageText = "SCANNING HOSTILE AIRCRAFT";
        else if (smoothProgress < 55)  stageText = "LOADING MUNITIONS";
        else if (smoothProgress < 65)  stageText = "CALIBRATING FLIGHT CONTROLS";
        else if (smoothProgress < 90)  stageText = "PAINTING COCKPIT VIEW";
        else                           stageText = "SYSTEMS ARMED â€” READY";
        
        // Animated dots
        int dotCount = (int)((System.currentTimeMillis() / 350) % 4);
        String dots = ".".repeat(dotCount);
        
        g.setFont(FontPalette.MEDIUM);
        FontMetrics fm = g.getFontMetrics();
        
        // Stage text with orange accent
        g.setColor(ColorPalette.ACCENT_ORANGE);
        String fullStageText = stageText + dots;
        g.drawString(fullStageText, (width - fm.stringWidth(stageText + "...")) / 2, height / 2 + 10);
        
        // â”€â”€ Missile-arming gauge progress bar (1.5x wider) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        int barWidth = Math.min(750, (int)((width - 200) * 1.5));
        int barHeight = 28;
        int barX = (width - barWidth) / 2;
        int barY = height / 2 + 45;
        UITheme.drawProgressBar(g, barX, barY, barWidth, barHeight,
            smoothProgress / 100.0, ColorPalette.ACCENT_ORANGE);
        
        // â”€â”€ Version tag in bottom-right corner â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        g.setFont(FontPalette.XS_13);
        g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_DIM, 100));
        String verText = Game.GAME_VERSION;
        fm = g.getFontMetrics();
        g.drawString(verText, width - fm.stringWidth(verText) - 20, height - 20);
    }
    
    // Handle active item effects during gameplay
    private void handleActiveItemEffects(ActiveItem item, double deltaTime) {
        switch (item.getType()) {
            case LUCKY_CHARM:
                // Circle is spawned once at activation time (in key handler)
                // This per-frame handler is empty for Pool of Loot
                break;
                
            case DASH:
                // Apply speed boost and invincibility during dash
                playerInvincible = true;
                dashSpeedMultiplier = 1.1; // Quick burst without excessive speed
                if (player != null) {
                    // Apply dash impulse in current movement direction
                    player.applyDashImpulse(dashSpeedMultiplier, keys);
                    
                    // Soft aim assist - nudge missile toward boss when nearby and pointing roughly at it
                    if (currentBoss != null && bossVulnerable) {
                        double dx = currentBoss.getX() - player.getX();
                        double dy = currentBoss.getY() - player.getY();
                        double distToBoss = Math.sqrt(dx * dx + dy * dy);
                        
                        if (distToBoss < 300 && distToBoss > 0) {
                            double angleToBoss = Math.atan2(dy, dx);
                            double playerAngle = player.getAngle();
                            double angleDiff = angleToBoss - playerAngle;
                            while (angleDiff > Math.PI) angleDiff -= TWO_PI;
                            while (angleDiff < -Math.PI) angleDiff += TWO_PI;
                            
                            // Only assist when pointing roughly toward boss (within ~60 degrees)
                            if (Math.abs(angleDiff) < Math.PI / 3) {
                                double assistStrength = 0.2 * (1.0 - distToBoss / 300);
                                player.nudgeAngle(angleToBoss, assistStrength);
                            }
                        }
                    }
                }
                break;
                
            case IMPULSE:
                // Push all bullets away from player (instant effect)
                soundManager.playSound(SoundManager.Sound.ELECTRIC_ZAP);
                
                // Trigger impulse zoom effect
                impulseZoomActive = true;
                impulseZoomTimer = 25; // Hold zoom for about 0.4 seconds
                targetEffectZoom = IMPULSE_ZOOM;
                
                if (player != null) {
                    for (Bullet bullet : bullets) {
                        double dx = bullet.getX() - player.getX();
                        double dy = bullet.getY() - player.getY();
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        
                        if (distance < 400 && distance > 0) { // Larger impulse radius
                            // Push bullet away with much stronger force
                            double angle = Math.atan2(dy, dx);
                            double pushForce = 35 * (1.0 - distance / 400);
                            
                            // Heavy bullets are barely moved by impulse
                            if (bullet.getType() == Bullet.BulletType.LARGE || 
                                bullet.getType() == Bullet.BulletType.BOMB ||
                                bullet.getType() == Bullet.BulletType.NUKE) {
                                pushForce *= 0.15;
                            }
                            
                            bullet.applyForce(Math.cos(angle) * pushForce, Math.sin(angle) * pushForce);
                        }
                    }
                    
                    // Create impulse particles
                    if (enableParticles) {
                        for (int i = 0; i < 30; i++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 5 + Math.random() * 5;
                            addParticle(
                                player.getX(), player.getY(),
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                ColorPalette.SUCCESS_GREEN, 30, 8,
                                Particle.ParticleType.SPARK
                            );
                        }
                    }
                    
                    screenShakeIntensity = 8;
                }
                break;
                
            case SHIELD:
                // Orbiting shield - only activate if not already active (no stacking)
                System.out.println("SHIELD: Attempting activation. shieldActive=" + shieldActive);
                if (!shieldActive) {
                    soundManager.playSound(SoundManager.Sound.SHIELD_ACTIVATE);
                    shieldActive = true;
                    shieldHits = 3; // 3 shields orbiting
                    shieldOrbitAngle = 0; // Reset orbit angle
                    System.out.println("SHIELD: Activated! shieldHits=" + shieldHits);
                    
                    // Set cooldown based on first use or not
                    if (shieldFirstUse) {
                        // First use in level - will have 5 second cooldown (set in startLevelCooldown)
                        shieldFirstUse = false;
                    }
                    // After first use, item will use full 20 second cooldown from ActiveItem
                }
                break;
                
            case BOMBS:
                // Spawn scattered bomb explosions across the screen
                soundManager.playSound(SoundManager.Sound.BOMB_ACTIVATE);
                
                // Number of bombs to spawn (reduced for performance)
                int numBombs = 8 + (int)(Math.random() * 5); // 8-12 bombs
                double bombRadius = 100; // Slightly larger radius to compensate
                double minBombDistance = 100; // Minimum distance between bomb centers
                
                // Store bomb positions
                java.util.List<double[]> bombPositions = new java.util.ArrayList<>();
                
                // Generate bomb positions with minimum distance constraint
                int maxAttempts = 50;
                for (int i = 0; i < numBombs && maxAttempts > 0; i++) {
                    // Random position within world bounds (with padding)
                    double bombX = 80 + Math.random() * (WORLD_WIDTH - 160);
                    double bombY = 80 + Math.random() * (WORLD_HEIGHT - 160);
                    
                    // Check minimum distance from other bombs
                    boolean validPosition = true;
                    for (double[] existingBomb : bombPositions) {
                        double dx = bombX - existingBomb[0];
                        double dy = bombY - existingBomb[1];
                        if (Math.sqrt(dx * dx + dy * dy) < minBombDistance) {
                            validPosition = false;
                            break;
                        }
                    }
                    
                    if (validPosition) {
                        bombPositions.add(new double[]{bombX, bombY, i * 5 + Math.random() * 3}); // x, y, delay - more spread out timing
                    } else {
                        i--; // Try again
                        maxAttempts--;
                    }
                }
                
                // Schedule bomb explosions with staggered timing
                final java.util.List<double[]> finalBombPositions = bombPositions;
                bombExplosionQueue.clear();
                bombExplosionQueue.addAll(finalBombPositions);
                bombExplosionTimer = 0;
                
                // Initial screen shake
                screenShakeIntensity = 8;
                break;
                
            case TYPE_PURGE:
                // Delete all bullets of a randomly selected type and flash screen that color
                if (!bullets.isEmpty()) {
                    // Find all bullet types currently on screen
                    java.util.Set<Bullet.BulletType> typesOnScreen = new java.util.HashSet<>();
                    for (Bullet bullet : bullets) {
                        if (bullet.isActive()) {
                            typesOnScreen.add(bullet.getType());
                        }
                    }
                    
                    if (!typesOnScreen.isEmpty()) {
                        // Pick a random type from those on screen
                        Bullet.BulletType[] types = typesOnScreen.toArray(new Bullet.BulletType[0]);
                        Bullet.BulletType targetType = types[(int)(Math.random() * types.length)];
                        
                        // Get the color for this bullet type and trigger screen flash
                        typePurgeFlashColor = getBulletTypeColor(targetType);
                        typePurgeFlashTimer = 30; // 0.5 second flash
                        
                        // Remove all bullets of that type with particles
                        int purgedCount = 0;
                        for (int i = bullets.size() - 1; i >= 0; i--) {
                            Bullet bullet = bullets.get(i);
                            if (bullet.getType() == targetType) {
                                // Create disintegration particles
                                for (int j = 0; j < 4; j++) {
                                    addParticle(
                                        bullet.getX(), bullet.getY(),
                                        (Math.random() - 0.5) * 4,
                                        (Math.random() - 0.5) * 4,
                                        typePurgeFlashColor, 15, 20,
                                        Particle.ParticleType.SPARK
                                    );
                                }
                                bullets.remove(i);
                                purgedCount++;
                            }
                        }
                        
                        System.out.println("TYPE_PURGE: Erased " + purgedCount + " " + targetType + " bullets!");
                        screenShakeIntensity = 8;
                    }
                }
                break;
                
            case TIME_SLOW:
                // Bullets move at 50% speed (applied in bullet update loop)
                // This effect is checked in the bullet collision section
                break;
                
            case FROST_BEAM:
                // Fire an icy beam that freezes bullets - doesn't penetrate, stops at first hit
                // Only freeze bullets when beam is sufficiently extended (progress > 0.3)
                frostBeamStopDistance = -1; // Reset stop distance each frame
                
                if (player != null && frostBeamProgress > 0.3) {
                    double angle = frostBeamAngle; // Use smooth beam angle instead of player angle
                    double maxLaserLength = Math.sqrt(WORLD_WIDTH * WORLD_WIDTH + WORLD_HEIGHT * WORLD_HEIGHT);
                    
                    // Beam width and length scale with animation progress
                    double easedProgress = 1.0 - Math.pow(1.0 - frostBeamProgress, 3);
                    double laserWidth = 50 * easedProgress;
                    double laserLength = maxLaserLength * Math.min(1.0, easedProgress * 1.5);
                    
                    // Calculate tip position (at edge of centered circle)
                    double circleRadius = 30 + (10 * easedProgress);
                    double tipX = player.getX() + Math.cos(angle) * circleRadius;
                    double tipY = player.getY() + Math.sin(angle) * circleRadius;
                    
                    // Calculate laser end point
                    double laserEndX = tipX + Math.cos(angle) * laserLength;
                    double laserEndY = tipY + Math.sin(angle) * laserLength;
                    
                    // Find the closest bullet along the beam (frozen or not) for visual stop
                    Bullet closestBulletToFreeze = null;
                    double closestTToFreeze = Double.MAX_VALUE; // For freezing (unfrozen bullets only)
                    double closestTForVisual = Double.MAX_VALUE; // For visual stop (any bullet)
                    
                    for (int i = bullets.size() - 1; i >= 0; i--) {
                        Bullet bullet = bullets.get(i);
                        
                        // Skip bullets that haven't spawned yet (still in warning phase)
                        if (!bullet.isActive()) {
                            continue;
                        }
                        
                        double bulletX = bullet.getX();
                        double bulletY = bullet.getY();
                        
                        // Check if bullet is within frost beam (line segment collision)
                        double dx = laserEndX - tipX;
                        double dy = laserEndY - tipY;
                        double lineLength = Math.sqrt(dx * dx + dy * dy);
                        double t = Math.max(0, Math.min(1, ((bulletX - tipX) * dx + (bulletY - tipY) * dy) / (lineLength * lineLength)));
                        double closestX = tipX + t * dx;
                        double closestY = tipY + t * dy;
                        double distanceToLine = Math.sqrt(Math.pow(bulletX - closestX, 2) + Math.pow(bulletY - closestY, 2));
                        
                        if (distanceToLine < laserWidth / 2) {
                            // Track closest bullet for visual (any bullet, frozen or not)
                            if (t < closestTForVisual) {
                                closestTForVisual = t;
                                // Calculate actual distance from beam start to bullet
                                frostBeamStopDistance = t * laserLength;
                            }
                            
                            // Track closest unfrozen bullet for freezing
                            if (!bullet.isFrozen() && t < closestTToFreeze) {
                                closestBulletToFreeze = bullet;
                                closestTToFreeze = t;
                            }
                        }
                    }
                    
                    // Freeze only the closest unfrozen bullet (beam doesn't penetrate)
                    if (closestBulletToFreeze != null) {
                        closestBulletToFreeze.setFrameSpeedMultiplier(0.0); // Completely frozen
                        closestBulletToFreeze.setFreezeTimer(360); // 6 seconds at 60fps
                        gameData.addScore(5);
                        
                        // Create ice/frost particles
                        if (enableParticles) {
                            for (int j = 0; j < 3; j++) {
                                double particleAngle = Math.random() * TWO_PI;
                                double speed = 0.5 + Math.random() * 2;
                                addParticle(
                                    closestBulletToFreeze.getX(), closestBulletToFreeze.getY(),
                                    Math.cos(particleAngle) * speed, Math.sin(particleAngle) * speed,
                                    FROST_BEAM_ICE, 20, 3, // Ice blue color
                                    Particle.ParticleType.SPARK
                                );
                            }
                        }
                    }
                }
                break;
                
            case STUN:
                // Stun the boss - can't move, can't shoot, shakes
                // Stun duration is now tied to item's active state
                soundManager.playSound(SoundManager.Sound.ELECTRIC_ZAP);
                if (currentBoss != null) {
                    bossStunned = true;
                    screenShakeIntensity = 10;
                    
                    // Create stun particles around boss
                    if (enableParticles) {
                        for (int i = 0; i < 20; i++) {
                            double angle = Math.random() * TWO_PI;
                            double dist = 30 + Math.random() * 50;
                            addParticle(
                                currentBoss.getX() + Math.cos(angle) * dist, 
                                currentBoss.getY() + Math.sin(angle) * dist,
                                0, -1,
                                ColorPalette.TEXT_GOLD, 40, 6,
                                Particle.ParticleType.SPARK
                            );
                        }
                    }
                }
                break;
                
            default:
                break;
        }
    }
    
    private void drawBossStunEffect(Graphics2D g, double cameraX, double cameraY, double time) {
        if (currentBoss == null) return;
        
        // Draw yellow/electric stun effect around boss
        double bossX = currentBoss.getX() - cameraX + bossStunShakeOffset;
        double bossY = currentBoss.getY() - cameraY;
        double bossSize = currentBoss.getSize();
        
        Graphics2D g2d = (Graphics2D) g.create();
        
        // Draw stun ring effect - semi-transparent
        float alpha = 0.25f + (float)(Math.sin(time * 15) * 0.15f); // More transparent (0.1 to 0.4 range)
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(ColorPalette.TEXT_GOLD); // Yellow stun color
        g2d.setStroke(new BasicStroke(4));
        g2d.drawOval((int)(bossX - bossSize * 0.7), (int)(bossY - bossSize * 0.7),
                     (int)(bossSize * 1.4), (int)(bossSize * 1.4));
        
        // Draw electric sparks - also semi-transparent
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 1.2f)); // Slightly brighter
        g2d.setColor(STARBURST_WARM);
        for (int i = 0; i < 6; i++) {
            double angle = (time * 8 + i * Math.PI / 3) % (Math.PI * 2);
            double dist = bossSize * 0.6;
            double sparkX = bossX + Math.cos(angle) * dist;
            double sparkY = bossY + Math.sin(angle) * dist;
            g2d.fillOval((int)(sparkX - 4), (int)(sparkY - 4), 8, 8);
        }
        
        // Draw "STUNNED" text - also semi-transparent
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2d.setFont(FontPalette.SMALL);
        g2d.setColor(ColorPalette.TEXT_GOLD);
        FontMetrics fm = g2d.getFontMetrics();
        String text = "STUNNED!";
        g2d.drawString(text, (int)(bossX - fm.stringWidth(text) / 2), (int)(bossY - bossSize * 0.8));
        
        g2d.dispose();
    }
    
    private void drawAutoSaveIndicator(Graphics2D g, int width, int height) {
        // Calculate fade-in/fade-out alpha
        float progress = Math.min(1.0f, (float)autoSaveIndicatorTimer / 30.0f); // Fade in over 0.5 seconds
        float alpha = 1.0f;
        if (autoSaveIndicatorTimer < 30) {
            alpha = progress; // Fade out in last 0.5 seconds
        }
        
        // Position in top-right corner
        int x = width - 220;
        int y = 30;
        int boxWidth = 200;
        int boxHeight = 50;
        
        // Draw background box with transparency
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.9f));
        g.setColor(ColorPalette.BG_DARK);
        g.fillRoundRect(x, y, boxWidth, boxHeight, 10, 10);
        
        // Draw border
        g.setColor(FROST_BEAM_ICE);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, boxWidth, boxHeight, 10, 10);
        
        // Draw save icon (floppy disk shape)
        int iconX = x + 15;
        int iconY = y + 10;
        int iconSize = 30;
        g.setColor(FROST_BEAM_ICE);
        g.fillRect(iconX, iconY, iconSize, iconSize);
        g.setColor(ColorPalette.BG_DARK);
        g.fillRect(iconX + 8, iconY + 3, iconSize - 16, 8); // Label area
        g.fillRect(iconX + 5, iconY + iconSize - 12, iconSize - 10, 8); // Bottom slot
        
        // Draw "Saving..." text
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.setFont(FontPalette.TINY);
        String saveText = "Saving...";
        FontMetrics fm = g.getFontMetrics();
        int textX = iconX + iconSize + 10;
        int textY = y + (boxHeight + fm.getAscent()) / 2 - 2;
        g.drawString(saveText, textX, textY);
        
        // Reset composite
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }
    
    /**
     * Draw the attack introduction screen
     */
    private void drawAttackIntro(Graphics2D g, int width, int height) {
        // Military themed background (matching the rest of the game)
        UITheme.drawScreenBackground(g, width, height, gradientTime);

        // Animated pulse effect (smaller range to prevent overflow)
        double pulse = Math.sin(System.currentTimeMillis() / 300.0) * 0.03 + 1.0;

        // Box dimensions - fill most of the screen
        int boxWidth = Math.min(900, width - 40);
        int boxHeight = Math.min(800, height - 30);
        int boxX = (width - boxWidth) / 2;
        int boxY = (height - boxHeight) / 2;

        // Calculate available space for the image (after accounting for text areas)
        int headerHeight = 70;
        int nameHeight = 55;
        int descriptionHeight = 100;
        int promptHeight = 50;
        int boxPaddingV = 25;
        int sectionGap = 20;
        int imageFramePadding = 15;

        int textAreaHeight = headerHeight + nameHeight + descriptionHeight + promptHeight + sectionGap * 3 + boxPaddingV * 2;
        int maxImageHeight = Math.max(300, boxHeight - textAreaHeight - imageFramePadding * 2);
        int maxImageWidth = Math.max(400, boxWidth - 60);

        // Calculate image dimensions
        int imgDisplayWidth = 0;
        int imgDisplayHeight = 0;

        if (attackIntroImage != null) {
            int imgWidth = attackIntroImage.getWidth();
            int imgHeight = attackIntroImage.getHeight();
            double scale = Math.min((double)maxImageWidth / imgWidth, (double)maxImageHeight / imgHeight);
            imgDisplayWidth = Math.max(100, Math.min((int)(imgWidth * scale), maxImageWidth));
            imgDisplayHeight = Math.max(100, Math.min((int)(imgHeight * scale), maxImageHeight));
        } else {
            imgDisplayWidth = 150;
            imgDisplayHeight = 150;
        }

        // Draw chamfered card for the main content area
        UITheme.drawCard(g, boxX, boxY, boxWidth, boxHeight, ColorPalette.ACCENT_ORANGE);

        // Track current Y position for layout
        int currentY = boxY + boxPaddingV;

        // "NEW ATTACK!" header â€” using stencil title style
        UITheme.drawTitle(g, "NEW ATTACK!", boxWidth, currentY + 50,
            ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED, gradientTime,
            FontPalette.getDisplay(Font.BOLD, Math.min(50, boxWidth / 10)));
        currentY += 70;

        // Attack name
        int nameFontSize = Math.min(40, boxWidth / 12);
        g.setFont(FontPalette.get(Font.BOLD, nameFontSize));
        g.setColor(ColorPalette.TEXT_PRIMARY);
        FontMetrics fm = g.getFontMetrics();
        if (currentAttackIntroName != null) {
            // Shadow
            g.setColor(ColorPalette.TEXT_SHADOW);
            int nameX = boxX + (boxWidth - fm.stringWidth(currentAttackIntroName)) / 2;
            currentY += fm.getAscent();
            g.drawString(currentAttackIntroName, nameX + 2, currentY + 2);
            g.setColor(ColorPalette.TEXT_PRIMARY);
            g.drawString(currentAttackIntroName, nameX, currentY);
        }
        currentY += sectionGap;

        // Attack image - centered in box
        int imageX = boxX + (boxWidth - imgDisplayWidth) / 2;
        int imageY = currentY;

        if (attackIntroImage != null) {
            int pulseWidth = (int)(imgDisplayWidth * pulse);
            int pulseHeight = (int)(imgDisplayHeight * pulse);
            int pulseImageX = boxX + (boxWidth - pulseWidth) / 2;
            int pulseOffsetY = (imgDisplayHeight - pulseHeight) / 2;

            // Image frame with military feel
            g.setColor(new Color(10, 15, 25, 200));
            g.fillRoundRect(imageX - imageFramePadding, imageY - imageFramePadding,
                           imgDisplayWidth + imageFramePadding * 2, imgDisplayHeight + imageFramePadding * 2, 8, 8);
            g.setColor(ColorPalette.BORDER_STEEL);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(imageX - imageFramePadding, imageY - imageFramePadding,
                           imgDisplayWidth + imageFramePadding * 2, imgDisplayHeight + imageFramePadding * 2, 8, 8);

            g.drawImage(attackIntroImage, pulseImageX, imageY + pulseOffsetY, pulseWidth, pulseHeight, null);
        } else {
            g.setColor(new Color(20, 25, 35));
            g.fillRoundRect(imageX, imageY, imgDisplayWidth, imgDisplayHeight, 8, 8);
            g.setColor(ColorPalette.BORDER_STEEL);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(imageX, imageY, imgDisplayWidth, imgDisplayHeight, 8, 8);

            g.setFont(FontPalette.get(Font.BOLD, 36));
            g.setColor(ColorPalette.TEXT_DIM);
            fm = g.getFontMetrics();
            g.drawString("?", imageX + imgDisplayWidth/2 - fm.stringWidth("?")/2,
                        imageY + imgDisplayHeight/2 + fm.getAscent()/2 - 4);
        }
        currentY = imageY + imgDisplayHeight + imageFramePadding + sectionGap;

        // Attack description
        if (currentAttackIntroDescription != null) {
            int descFontSize = Math.min(24, boxWidth / 25);
            int descLineHeight = descFontSize + 10;
            g.setFont(FontPalette.get(Font.PLAIN, descFontSize));
            g.setColor(ColorPalette.TEXT_DIM);
            fm = g.getFontMetrics();
            String[] lines = currentAttackIntroDescription.split("\n");
            for (String line : lines) {
                int lineX = boxX + (boxWidth - fm.stringWidth(line)) / 2;
                g.drawString(line, lineX, currentY + fm.getAscent());
                currentY += descLineHeight;
            }
        }

        // "Press SPACE to continue" prompt
        double promptPulse = Math.sin(System.currentTimeMillis() / 400.0) * 0.3 + 0.7;
        int promptFontSize = Math.min(26, boxWidth / 24);
        g.setFont(FontPalette.get(Font.BOLD, promptFontSize));
        g.setColor(new Color(ColorPalette.ACCENT_ORANGE.getRed(), ColorPalette.ACCENT_ORANGE.getGreen(),
                             ColorPalette.ACCENT_ORANGE.getBlue(), (int)(255 * promptPulse)));
        String prompt = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to continue";
        fm = g.getFontMetrics();
        int promptX = boxX + (boxWidth - fm.stringWidth(prompt)) / 2;
        int promptY = boxY + boxHeight - boxPaddingV - 5;
        g.drawString(prompt, promptX, promptY);

        // Level indicator in corner
        g.setFont(FontPalette.get(Font.BOLD, 14));
        g.setColor(ColorPalette.TEXT_DIM);
        String levelText;
        if (gameData != null && gameData.isInEndlessMode()) {
            levelText = "Endless Mode Prestige " + gameData.getEndlessPrestige() + " Level " + gameData.getEndlessCurrentLevel();
        } else {
            levelText = "Level " + (gameData != null ? gameData.getCurrentLevel() : "?");
        }
        g.drawString(levelText, boxX + 15, boxY + boxHeight - 10);
    }
    
    /**
     * Draw the attack showcase selection screen for debugging/screenshots
     */
    private void drawAttackShowcase(Graphics2D g, int width, int height) {
        // Military themed background (matching the rest of the game)
        UITheme.drawScreenBackground(g, width, height, gradientTime);

        // Animated jet silhouettes
        UITheme.drawJetSilhouette(g, width, height, gradientTime);

        double time = gradientTime;
        double pulse = Math.sin(System.currentTimeMillis() / 400.0) * 0.05 + 1.0;

        // === TITLE ===
        UITheme.drawTitle(g, "SHOWCASE", width, 55,
            ColorPalette.ACCENT_CYAN, ColorPalette.ACCENT_ORANGE, time, FontPalette.getDisplay(Font.BOLD, 42));

        // === TAB BUTTONS ===
        int tabWidth = 150;
        int tabHeight = 40;
        int tabY = 80;
        int attacksTabX = width / 2 - tabWidth - 10;
        int itemsTabX = width / 2 + 10;

        boolean attacksTabHover = mouseX >= attacksTabX && mouseX <= attacksTabX + tabWidth &&
                                  mouseY >= tabY && mouseY <= tabY + tabHeight;
        boolean attacksTabActive = (showcaseTab == 0);
        boolean attacksTabKeySelected = (showcaseHoveredButton == 0);
        drawShowcaseTab(g, attacksTabX, tabY, tabWidth, tabHeight, "ATTACKS", attacksTabActive, attacksTabHover, attacksTabKeySelected);

        boolean itemsTabHover = mouseX >= itemsTabX && mouseX <= itemsTabX + tabWidth &&
                                mouseY >= tabY && mouseY <= tabY + tabHeight;
        boolean itemsTabActive = (showcaseTab == 1);
        boolean itemsTabKeySelected = (showcaseHoveredButton == 1);
        drawShowcaseTab(g, itemsTabX, tabY, tabWidth, tabHeight, "ITEMS", itemsTabActive, itemsTabHover, itemsTabKeySelected);

        // Counter
        g.setFont(FontPalette.TINY);
        g.setColor(ColorPalette.TEXT_DIM);
        int maxIndex = (showcaseTab == 0) ? ATTACK_INTROS.length : ITEM_SHOWCASE.length;
        String counter = (debugShowcaseIndex + 1) + " / " + maxIndex;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(counter, (width - fm.stringWidth(counter)) / 2, tabY + tabHeight + 30);

        // === CAROUSEL CARDS ===
        int centerX = width / 2;
        int centerY = height / 2 - 30;
        int cardSpacing = 600;
        int centerCardWidth = 620;
        int centerCardHeight = 520;

        // Animate carousel offset towards 0
        if (Math.abs(showcaseCarouselOffset) > 0.01) {
            showcaseCarouselOffset *= 0.85;
        } else {
            showcaseCarouselOffset = 0;
        }

        for (int offset = -2; offset <= 2; offset++) {
            int itemIndex = debugShowcaseIndex + offset;
            if (itemIndex < 0 || itemIndex >= maxIndex) continue;

            double animatedOffset = offset + showcaseCarouselOffset;
            int cardCenterX = centerX + (int)(animatedOffset * cardSpacing);
            double distFromCenter = Math.abs(animatedOffset);
            double scale = Math.max(0.5, 1.0 - distFromCenter * 0.35);
            float alpha = (float)Math.max(0.2, 1.0 - distFromCenter * 0.5);

            if (cardCenterX < -200 || cardCenterX > width + 200) continue;

            int cardW = (int)(centerCardWidth * scale);
            int cardH = (int)(centerCardHeight * scale);
            int cardX = cardCenterX - cardW / 2;
            int cardY = centerY - cardH / 2;

            // Get item data
            String itemName, itemDesc, itemLevel, itemCategory, itemId;
            if (showcaseTab == 0) {
                itemId = ATTACK_INTROS[itemIndex][0];
                itemLevel = ATTACK_INTROS[itemIndex][1];
                itemName = ATTACK_INTROS[itemIndex][2];
                itemDesc = ATTACK_INTROS[itemIndex][3];
                itemCategory = ATTACK_INTROS[itemIndex].length > 4 ? ATTACK_INTROS[itemIndex][4] : "Attack";
            } else {
                itemName = ITEM_SHOWCASE[itemIndex][2];
                itemLevel = ITEM_SHOWCASE[itemIndex][1];
                itemCategory = "ACTIVE ITEM";
                itemDesc = ITEM_SHOWCASE[itemIndex][3];
                itemId = ITEM_SHOWCASE[itemIndex][0];
            }

            BufferedImage cardImage = null;
            if (showcaseTab == 0) {
                cardImage = loadAttackIntroImage(itemId);
            } else {
                cardImage = loadItemShowcaseImage(itemId);
            }

            // Check locked status
            int itemUnlockLevel = Integer.parseInt(itemLevel);
            boolean isShowcaseLocked;
            if (debugShowcaseUnlockAll) {
                isShowcaseLocked = false;
            } else if (showcaseTab == 1) {
                try {
                    ActiveItem.ItemType checkType = ActiveItem.ItemType.valueOf(itemId);
                    isShowcaseLocked = !gameData.getUnlockedItems().contains(checkType);
                } catch (Exception e) {
                    isShowcaseLocked = true;
                }
            } else {
                isShowcaseLocked = itemUnlockLevel > gameData.getMaxUnlockedLevel();
            }

            Composite originalComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            // Use UITheme chamfered cards
            Color accentColor = offset == 0 ? ColorPalette.ACCENT_ORANGE : ColorPalette.BORDER_STEEL;
            if (offset == 0 && Math.abs(showcaseCarouselOffset) < 0.3) {
                UITheme.drawCardSelected(g, cardX, cardY, cardW, cardH, accentColor, time);
            } else {
                UITheme.drawCard(g, cardX, cardY, cardW, cardH, accentColor);
            }

            if (isShowcaseLocked) {
                // Locked card overlay
                g.setColor(new Color(0, 0, 0, (int)(150 * alpha)));
                g.fillRect(cardX + 5, cardY + 5, cardW - 10, cardH - 10);

                int lockSize = (int)(60 * scale);
                g.setFont(FontPalette.get(Font.BOLD, lockSize));
                g.setColor(new Color(ColorPalette.ACCENT_YELLOW.getRed(), ColorPalette.ACCENT_YELLOW.getGreen(),
                                     ColorPalette.ACCENT_YELLOW.getBlue(), (int)(220 * alpha)));
                fm = g.getFontMetrics();
                String lockStr = "[X]";
                int lockX = cardX + (cardW - fm.stringWidth(lockStr)) / 2;
                int lockY = cardY + cardH / 2 - (int)(20 * scale);
                g.drawString(lockStr, lockX, lockY);

                int unlockFontSize = Math.max(10, (int)(18 * scale));
                g.setFont(FontPalette.get(Font.BOLD, unlockFontSize));
                g.setColor(new Color(ColorPalette.TEXT_GOLD.getRed(), ColorPalette.TEXT_GOLD.getGreen(),
                                     ColorPalette.TEXT_GOLD.getBlue(), (int)(200 * alpha)));
                fm = g.getFontMetrics();
                String unlockStr = "Unlocks at Level " + itemLevel;
                int unlockX = cardX + (cardW - fm.stringWidth(unlockStr)) / 2;
                g.drawString(unlockStr, unlockX, lockY + (int)(40 * scale));

                g.setComposite(originalComposite);
                continue;
            }

            // Item name at top
            int fontSize = Math.max(14, (int)(28 * scale));
            g.setFont(FontPalette.get(Font.BOLD, fontSize));
            g.setColor(ColorPalette.TEXT_PRIMARY);
            fm = g.getFontMetrics();
            int nameX = cardX + (cardW - fm.stringWidth(itemName)) / 2;
            // Shadow
            g.setColor(ColorPalette.TEXT_SHADOW);
            g.drawString(itemName, nameX + 2, cardY + (int)(35 * scale) + 2);
            g.setColor(ColorPalette.TEXT_PRIMARY);
            g.drawString(itemName, nameX, cardY + (int)(35 * scale));

            // Item image
            int maxImageWidth = (int)(cardW * 0.88);
            int maxImageHeight = (int)(cardH * 0.52);
            int imageY = cardY + (int)(48 * scale);

            if (cardImage != null) {
                int imgWidth = cardImage.getWidth();
                int imgHeight = cardImage.getHeight();
                double imgAspect = (double) imgWidth / imgHeight;

                int drawWidth, drawHeight;
                if (imgAspect > 1) {
                    drawWidth = maxImageWidth;
                    drawHeight = (int)(maxImageWidth / imgAspect);
                    if (drawHeight > maxImageHeight) {
                        drawHeight = maxImageHeight;
                        drawWidth = (int)(maxImageHeight * imgAspect);
                    }
                } else {
                    drawHeight = maxImageHeight;
                    drawWidth = (int)(maxImageHeight * imgAspect);
                    if (drawWidth > maxImageWidth) {
                        drawWidth = maxImageWidth;
                        drawHeight = (int)(maxImageWidth / imgAspect);
                    }
                }

                int imageX = cardX + (cardW - drawWidth) / 2;
                int imageCenterY = imageY + (maxImageHeight - drawHeight) / 2;

                if (offset == 0) {
                    double pulseScale = 1.0 + (pulse - 1.0) * 0.5;
                    int pulseW = (int)(drawWidth * pulseScale);
                    int pulseH = (int)(drawHeight * pulseScale);
                    int pulseX = imageX - (pulseW - drawWidth) / 2;
                    int pulseY = imageCenterY - (pulseH - drawHeight) / 2;
                    g.drawImage(cardImage, pulseX, pulseY, pulseW, pulseH, null);
                } else {
                    g.drawImage(cardImage, imageX, imageCenterY, drawWidth, drawHeight, null);
                }
            } else {
                int placeholderSize = (int)(Math.min(maxImageWidth, maxImageHeight) * 0.8);
                int imageX = cardX + (cardW - placeholderSize) / 2;
                g.setColor(new Color(20, 25, 35, (int)(255 * alpha)));
                g.fillRoundRect(imageX, imageY, placeholderSize, placeholderSize, 8, 8);
                g.setColor(ColorPalette.BORDER_STEEL);
                g.setStroke(new BasicStroke(2));
                g.drawRoundRect(imageX, imageY, placeholderSize, placeholderSize, 8, 8);
                g.setFont(FontPalette.get(Font.BOLD, (int)(24 * scale)));
                g.setColor(ColorPalette.TEXT_DIM);
                fm = g.getFontMetrics();
                g.drawString("?", imageX + placeholderSize/2 - fm.stringWidth("?")/2, imageY + placeholderSize/2 + fm.getAscent()/3);
            }

            // Description (center card only)
            int descStartY = imageY + maxImageHeight + (int)(30 * scale);
            if (offset == 0 && itemDesc != null) {
                g.setFont(FontPalette.INFO);
                g.setColor(ColorPalette.TEXT_DIM);
                String[] lines = itemDesc.split("\n");
                fm = g.getFontMetrics();
                int descY = descStartY;
                int descPadding = 25;
                int maxDescWidth = cardW - descPadding * 2;
                for (String line : lines) {
                    String displayLine = line;
                    while (fm.stringWidth(displayLine) > maxDescWidth && displayLine.length() > 3) {
                        displayLine = displayLine.substring(0, displayLine.length() - 4) + "...";
                    }
                    int lineX = cardX + (cardW - fm.stringWidth(displayLine)) / 2;
                    g.drawString(displayLine, lineX, descY);
                    descY += 24;
                }
            }

            // Level text (bottom left)
            g.setFont(FontPalette.get(Font.BOLD, Math.max(12, (int)(14 * scale))));
            g.setColor(new Color(ColorPalette.TEXT_GOLD.getRed(), ColorPalette.TEXT_GOLD.getGreen(),
                                 ColorPalette.TEXT_GOLD.getBlue(), (int)(255 * alpha)));
            String lvlText = showcaseTab == 0 ? "Lv." + itemLevel : "Unlocks Lv." + itemLevel;
            g.drawString(lvlText, cardX + (int)(16 * scale), cardY + cardH - (int)(16 * scale));

            // Category + START button (center card only)
            if (offset == 0) {
                Color categoryColor = showcaseTab == 0 ? getCategoryColor(itemCategory) : ColorPalette.ACCENT_CYAN;
                g.setColor(categoryColor);
                g.setFont(FontPalette.get(Font.BOLD, 14));
                fm = g.getFontMetrics();
                g.drawString(itemCategory, cardX + cardW - fm.stringWidth(itemCategory) - 16, cardY + cardH - 16);

                // START button â€” military style
                int startBtnW = 200;
                int startBtnH = 50;
                int startBtnX = cardCenterX - startBtnW / 2;
                int startBtnY = cardY + cardH - 80;
                boolean startHover = mouseX >= startBtnX && mouseX <= startBtnX + startBtnW &&
                                     mouseY >= startBtnY && mouseY <= startBtnY + startBtnH;
                float btnPulse = (float)(0.6 + 0.4 * Math.sin(time * 3));

                if (startHover) {
                    // Glow
                    g.setColor(new Color(ColorPalette.ACCENT_ORANGE.getRed(), ColorPalette.ACCENT_ORANGE.getGreen(),
                                         ColorPalette.ACCENT_ORANGE.getBlue(), (int)(60 * btnPulse)));
                    g.fillRoundRect(startBtnX - 4, startBtnY - 4, startBtnW + 8, startBtnH + 8, 10, 10);
                    // Fill
                    GradientPaint btnGrad = new GradientPaint(startBtnX, startBtnY, ColorPalette.ACCENT_ORANGE,
                                                              startBtnX, startBtnY + startBtnH, ColorPalette.ACCENT_RED);
                    g.setPaint(btnGrad);
                    g.fillRoundRect(startBtnX, startBtnY, startBtnW, startBtnH, 8, 8);
                    g.setColor(ColorPalette.ACCENT_YELLOW);
                    g.setStroke(new BasicStroke(2.5f));
                    g.drawRoundRect(startBtnX, startBtnY, startBtnW, startBtnH, 8, 8);
                } else {
                    GradientPaint btnGrad = new GradientPaint(startBtnX, startBtnY, new Color(60, 70, 90),
                                                              startBtnX, startBtnY + startBtnH, new Color(40, 45, 60));
                    g.setPaint(btnGrad);
                    g.fillRoundRect(startBtnX, startBtnY, startBtnW, startBtnH, 8, 8);
                    g.setColor(new Color(ColorPalette.ACCENT_ORANGE.getRed(), ColorPalette.ACCENT_ORANGE.getGreen(),
                                         ColorPalette.ACCENT_ORANGE.getBlue(), (int)(150 + 80 * btnPulse)));
                    g.setStroke(new BasicStroke(2f));
                    g.drawRoundRect(startBtnX, startBtnY, startBtnW, startBtnH, 8, 8);
                }
                g.setFont(FontPalette.getDisplay(Font.BOLD, 22));
                g.setColor(ColorPalette.TEXT_WHITE);
                fm = g.getFontMetrics();
                String startLabel = "START";
                g.drawString(startLabel, startBtnX + (startBtnW - fm.stringWidth(startLabel)) / 2,
                             startBtnY + startBtnH / 2 + fm.getAscent() / 2 - 2);
            }

            g.setComposite(originalComposite);
        }

        // === ARROW BOXES ===
        int arrowBoxWidth = 80;
        int arrowBoxHeight = 120;
        int arrowY = height / 2 - 30 - arrowBoxHeight / 2;
        int leftArrowX = 15;
        int rightArrowX = width - arrowBoxWidth - 15;

        float arrowPulse = (float)(0.5 + 0.5 * Math.sin(time * 4));

        boolean canGoLeft = debugShowcaseIndex > 0;
        boolean canGoRight = debugShowcaseIndex < maxIndex - 1;

        // Left arrow
        drawShowcaseArrowBox(g, leftArrowX, arrowY, arrowBoxWidth, arrowBoxHeight, true,
                             showcaseHoveredButton == 2, canGoLeft, time, arrowPulse);

        // Right arrow
        drawShowcaseArrowBox(g, rightArrowX, arrowY, arrowBoxWidth, arrowBoxHeight, false,
                             showcaseHoveredButton == 3, canGoRight, time, arrowPulse);

        // Instructions at bottom
        g.setFont(FontPalette.XS_16);
        g.setColor(ColorPalette.TEXT_DIM);
        String instructions = moveKeysText() + " to navigate  |  " + keyText(KeyBindManager.Action.CONFIRM) + "/CLICK to start  |  " + keyText(KeyBindManager.Action.BACK) + " to exit";
        fm = g.getFontMetrics();
        g.drawString(instructions, (width - fm.stringWidth(instructions)) / 2, height - 30);
    }

    /** Draw an arrow box for the showcase carousel â€” military style */
    private void drawShowcaseArrowBox(Graphics2D g, int x, int y, int w, int h, boolean isLeft,
                                       boolean hovered, boolean enabled, double time, float arrowPulse) {
        String arrow = isLeft ? "<" : ">";
        FontMetrics fm;

        if (!enabled) {
            g.setColor(new Color(15, 18, 28, 100));
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(new Color(50, 55, 70, 80));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(x, y, w, h, 8, 8);
            g.setFont(FontPalette.get(Font.BOLD, 48));
            g.setColor(new Color(60, 65, 80, 100));
            fm = g.getFontMetrics();
            g.drawString(arrow, x + (w - fm.stringWidth(arrow)) / 2, y + h / 2 + 15);
        } else if (hovered) {
            float glowPulse = (float)(0.3 + 0.2 * Math.sin(time * 5));
            g.setColor(new Color(ColorPalette.ACCENT_ORANGE.getRed(), ColorPalette.ACCENT_ORANGE.getGreen(),
                                 ColorPalette.ACCENT_ORANGE.getBlue(), (int)(200 * glowPulse)));
            g.fillRoundRect(x - 5, y - 5, w + 10, h + 10, 12, 12);
            GradientPaint arrowGrad = new GradientPaint(x, y, new Color(60, 50, 40), x, y + h, new Color(40, 35, 30));
            g.setPaint(arrowGrad);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(ColorPalette.ACCENT_ORANGE);
            g.setStroke(new BasicStroke(3));
            g.drawRoundRect(x, y, w, h, 8, 8);
            g.setFont(FontPalette.get(Font.BOLD, 48));
            g.setColor(ColorPalette.TEXT_WHITE);
            fm = g.getFontMetrics();
            g.drawString(arrow, x + (w - fm.stringWidth(arrow)) / 2, y + h / 2 + 15);
        } else {
            GradientPaint arrowGrad = new GradientPaint(x, y, new Color(30, 35, 50), x, y + h, new Color(20, 25, 38));
            g.setPaint(arrowGrad);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(ColorPalette.BORDER_STEEL);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x, y, w, h, 8, 8);
            g.setFont(FontPalette.get(Font.BOLD, 48));
            g.setColor(new Color(ColorPalette.TEXT_DIM.getRed(), ColorPalette.TEXT_DIM.getGreen(),
                                 ColorPalette.TEXT_DIM.getBlue(), (int)(150 + 100 * arrowPulse)));
            fm = g.getFontMetrics();
            g.drawString(arrow, x + (w - fm.stringWidth(arrow)) / 2, y + h / 2 + 15);
        }
    }
    
    /**
     * Draw a tab button for the showcase screen
     */
    private void drawShowcaseTab(Graphics2D g, int x, int y, int width, int height, String label, boolean active, boolean hovered, boolean keyboardSelected) {
        double time = gradientTime;

        // Keyboard selection glow
        if (keyboardSelected) {
            float glowPulse = (float)(0.3 + 0.2 * Math.sin(time * 5));
            g.setColor(new Color(ColorPalette.ACCENT_ORANGE.getRed(), ColorPalette.ACCENT_ORANGE.getGreen(),
                                 ColorPalette.ACCENT_ORANGE.getBlue(), (int)(200 * glowPulse)));
            g.fillRoundRect(x - 5, y - 5, width + 10, height + 10, 10, 10);
        }

        // Tab background â€” metallic gradient
        if (active) {
            GradientPaint tabGrad = new GradientPaint(x, y, new Color(50, 60, 85), x, y + height, new Color(35, 40, 60));
            g.setPaint(tabGrad);
        } else if (hovered || keyboardSelected) {
            GradientPaint tabGrad = new GradientPaint(x, y, new Color(40, 50, 70), x, y + height, new Color(28, 33, 50));
            g.setPaint(tabGrad);
        } else {
            GradientPaint tabGrad = new GradientPaint(x, y, new Color(25, 30, 45), x, y + height, new Color(18, 22, 35));
            g.setPaint(tabGrad);
        }
        g.fillRoundRect(x, y, width, height, 8, 8);

        // Tab border
        if (keyboardSelected) {
            g.setColor(ColorPalette.ACCENT_ORANGE);
            g.setStroke(new BasicStroke(3));
        } else if (active) {
            g.setColor(ColorPalette.ACCENT_CYAN);
            g.setStroke(new BasicStroke(2));
        } else {
            g.setColor(ColorPalette.BORDER_STEEL);
            g.setStroke(new BasicStroke(1));
        }
        g.drawRoundRect(x, y, width, height, 8, 8);

        // Accent line on bottom for active tab
        if (active) {
            g.setColor(ColorPalette.ACCENT_ORANGE);
            g.setStroke(new BasicStroke(3));
            g.drawLine(x + 8, y + height, x + width - 8, y + height);
        }

        // Tab label
        g.setFont(FontPalette.TINY);
        g.setColor((active || keyboardSelected) ? ColorPalette.TEXT_WHITE : ColorPalette.TEXT_DIM);
        FontMetrics fm = g.getFontMetrics();
        int labelX = x + (width - fm.stringWidth(label)) / 2;
        int labelY = y + height / 2 + 6;
        g.drawString(label, labelX, labelY);
    }
    
    /**
     * Get color for attack category
     */
    private Color getCategoryColor(String category) {
        switch (category) {
            case "Pattern": return new Color(100, 180, 255);   // Blue
            case "Targeted": return new Color(255, 150, 100);  // Orange
            case "Special": return new Color(180, 100, 255);   // Purple
            case "Beam": return new Color(255, 255, 100);      // Yellow
            case "AOE": return new Color(255, 100, 100);       // Red
            case "Mega": return new Color(255, 80, 200);       // Pink/Magenta
            case "Mixed": return new Color(100, 255, 180);     // Teal
            case "Movement": return new Color(150, 255, 150);  // Green
            case "Explosive": return new Color(255, 120, 50);  // Dark Orange
            default: return new Color(180, 180, 180);          // Gray
        }
    }
    
    /**
     * Get a distinctive color for each bullet type (for Type Purge item effect)
     */
    private Color getBulletTypeColor(Bullet.BulletType type) {
        switch (type) {
            case NORMAL:       return new Color(255, 100, 50);   // Orange-red (fire)
            case FAST:         return new Color(255, 255, 100);  // Yellow (fast/bright)
            case LARGE:        return new Color(255, 50, 50);    // Deep red (danger)
            case HOMING:       return new Color(180, 50, 255);   // Purple (tracking/magic)
            case BOUNCING:     return new Color(50, 255, 150);   // Cyan-green (bouncy)
            case SPIRAL:       return new Color(255, 100, 200);  // Pink (spiraling)
            case ACCELERATING: return new Color(255, 200, 50);   // Gold (accelerating)
            case WAVE:         return new Color(100, 150, 255);  // Blue (wave motion)
            case BOMB:         return new Color(255, 80, 0);     // Dark orange (explosive)
            case GRENADE:      return new Color(200, 150, 50);   // Bronze/brown (grenade)
            case NUKE:         return new Color(255, 255, 200);  // Pale yellow (nuclear glow)
            case FRAGMENT:     return new Color(255, 150, 100);  // Light orange (debris)
            default:           return new Color(255, 255, 255);  // White fallback
        }
    }
    
    private void drawItemUnlockAnimation(Graphics2D g, int width, int height) {
        // Calculate animation progress (0.0 to 1.0)
        float progress = 1.0f - ((float) itemUnlockTimer / ITEM_UNLOCK_DURATION);
        
        // Calculate dismiss progress (1.0 = visible, 0.0 = gone)
        float dismissMultiplier = 1.0f;
        if (itemUnlockDismissing) {
            dismissMultiplier = (float) itemUnlockDismissTimer / ITEM_DISMISS_DURATION;
        }
        
        // Full dark overlay with fade
        int overlayAlpha = (int)(200 * Math.min(progress * 2, 1.0f) * dismissMultiplier);
        g.setColor(new Color(0, 0, 0, Math.min(overlayAlpha, 200)));
        
        // Reset translation to avoid screen shake affecting overlay
        Graphics2D g2d = (Graphics2D) g;
        g2d.setTransform(IDENTITY_TX);
        g.fillRect(0, 0, width, height);
        
        // Calculate position (slide up from bottom, slide down when dismissing)
        int centerX = width / 2;
        int startY = height + 300;
        int endY = height / 2;
        int dismissOffset = (int)((1.0f - dismissMultiplier) * 400); // Slide down when dismissing
        int currentY = (int)(startY + (endY - startY) * (1.0 - Math.pow(1.0 - progress, 2.5))) + dismissOffset;
        
        // Scale effect (start small, grow to full size, shrink when dismissing)
        float scale;
        if (progress < 0.4f) {
            scale = (float)Math.pow(progress / 0.4f, 0.8); // Smooth growth
        } else {
            scale = 1.0f;
        }
        scale *= dismissMultiplier; // Shrink during dismiss
        
        // Single glow layer for performance (scaled by shadow quality)
        long now = System.currentTimeMillis();
        if (shadowQuality > 0) {
            int glowSize = Math.max(1, (int)(400 * scale));
            float pulse = (float)Math.abs(Math.sin(now / 400.0)) * 0.3f + 0.7f;
            
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, currentY,
                glowSize,
                new float[]{0.0f, 0.6f, 1.0f},
                new Color[]{
                    new Color(235, 203, 139, (int)(70 * scale * pulse * dismissMultiplier)),
                    new Color(163, 190, 140, (int)(35 * scale * pulse * dismissMultiplier)),
                    ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 0)
                }
            );
            g.setPaint(glowPaint);
            g.fillOval(centerX - glowSize, currentY - glowSize, glowSize * 2, glowSize * 2);
        }
        
        // Animated particles around the box
        if (progress > 0.3f && enableParticles) {
            int particleCount = 16;
            double baseAngle = now / 125.0;
            double timeSin = now / 250.0;
            for (int i = 0; i < particleCount; i++) {
                double angle = (baseAngle + i * (360.0 / particleCount)) * Math.PI / 180.0;
                int radius = (int)(200 * scale);
                int px = (int)(centerX + Math.cos(angle) * radius);
                int py = (int)(currentY + Math.sin(angle) * radius * 0.7);
                int size = (int)(5 * scale);
                
                int pAlpha = (int)(200 * (float)Math.abs(Math.sin(angle * 3 + timeSin)) * scale * dismissMultiplier);
                g.setColor(new Color(235, 203, 139, Math.min(255, Math.max(0, pAlpha))));
                g.fillOval(px - size/2, py - size/2, size, size);
            }
        }
        
        // Draw box with better styling
        int boxWidth = (int)(UIScale.px(875) * scale);
        int boxHeight = (int)(UIScale.px(350) * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = currentY - boxHeight / 2;
        
        // Box shadow
        float progAlpha = Math.min(progress * 2, 1.0f);
        g.setColor(new Color(0, 0, 0, (int)(100 * progAlpha)));
        g.fillRoundRect(boxX + 5, boxY + 5, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Box background with gradient
        GradientPaint boxGradient = new GradientPaint(
            boxX, boxY, new Color(40, 40, 50, (int)(240 * progAlpha)),
            boxX, boxY + boxHeight, new Color(25, 25, 35, (int)(240 * progAlpha))
        );
        g.setPaint(boxGradient);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Animated border
        float borderPulse = (float)Math.abs(Math.sin(now / 400.0));
        int borderR = (int)(163 + (235 - 163) * borderPulse);
        int borderG = (int)(190 + (203 - 190) * borderPulse);
        int borderB = (int)(140 + (139 - 140) * borderPulse);
        g.setColor(new Color(borderR, borderG, borderB, (int)(255 * progAlpha)));
        g.setStroke(RenderCache.getStroke(3f));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Text content
        if (progress > 0.25f) {
            float textAlpha = Math.min((progress - 0.25f) / 0.3f, 1.0f) * dismissMultiplier;
            
            // "NEW ITEM UNLOCKED!" with shadow
            g.setFont(FontPalette.getDisplay(Font.BOLD, (int)(70 * scale)));
            String titleText = "NEW ITEM UNLOCKED!";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = centerX - titleFm.stringWidth(titleText) / 2;
            int titleY = currentY - (int)(100 * scale);
            
            // Title shadow
            g.setColor(new Color(0, 0, 0, (int)(150 * textAlpha)));
            g.drawString(titleText, titleX + 2, titleY + 2);
            
            // Title text with pulse
            float titlePulse = (float)Math.abs(Math.sin(now / 500.0)) * 0.3f + 0.7f;
            g.setColor(new Color(235, 203, 139, (int)(255 * textAlpha * titlePulse)));
            g.drawString(titleText, titleX, titleY);
            
            // Item name with shadow
            g.setFont(FontPalette.get(Font.BOLD, (int)(55 * scale)));
            FontMetrics itemFm = g.getFontMetrics();
            int itemX = centerX - itemFm.stringWidth(unlockedItemName) / 2;
            int itemY = currentY - (int)(12 * scale);
            
            g.setColor(new Color(0, 0, 0, (int)(150 * textAlpha)));
            g.drawString(unlockedItemName, itemX + 2, itemY + 2);
            
            g.setColor(new Color(163, 190, 140, (int)(255 * textAlpha)));
            g.drawString(unlockedItemName, itemX, itemY);
            
            // Item description (with word wrap to prevent overflow)
            if (unlockedItemDescription != null && !unlockedItemDescription.isEmpty() && progress > 0.4f) {
                g.setFont(FontPalette.get(Font.PLAIN, (int)(30 * scale)));
                FontMetrics descFm = g.getFontMetrics();
                int maxDescWidth = boxWidth - (int)(UIScale.px(50) * scale);
                g.setColor(new Color(200, 200, 200, (int)(220 * textAlpha)));
                
                // Word wrap description
                String[] words = unlockedItemDescription.split(" ");
                StringBuilder line = new StringBuilder();
                int descY = currentY + (int)(42 * scale);
                for (String word : words) {
                    String test = line.length() == 0 ? word : line + " " + word;
                    if (descFm.stringWidth(test) > maxDescWidth && line.length() > 0) {
                        String l = line.toString();
                        g.drawString(l, centerX - descFm.stringWidth(l) / 2, descY);
                        descY += descFm.getHeight();
                        line = new StringBuilder(word);
                    } else {
                        line = new StringBuilder(test);
                    }
                }
                if (line.length() > 0) {
                    String l = line.toString();
                    g.drawString(l, centerX - descFm.stringWidth(l) / 2, descY);
                }
            }
            
            // "Press SPACE to continue" hint (or buttons for equip prompt)
            if (progress > 0.8f) {
                if (showEquipPrompt && itemUnlockTimer == 0) {
                    // Draw equip buttons
                    g.setFont(FontPalette.get(Font.PLAIN, (int)(25 * scale)));
                    String promptText = "Equip this item?";
                    FontMetrics promptFm = g.getFontMetrics();
                    int promptX = centerX - promptFm.stringWidth(promptText) / 2;
                    int promptY = currentY + (int)(88 * scale);
                    
                    g.setColor(new Color(200, 200, 200, (int)(220 * textAlpha)));
                    g.drawString(promptText, promptX, promptY);
                    
                    // Position and draw buttons relative to animation box
                    int buttonWidth = UIScale.px(200);
                    int buttonHeight = UIScale.px(60);
                    int buttonY = currentY + (int)(115 * scale);
                    int spacing = UIScale.px(30);
                    int totalWidth = (buttonWidth * 2) + spacing;
                    int startX = centerX - totalWidth / 2;
                    
                    // Update button positions
                    equipButtons[0].setPosition(startX, buttonY);
                    equipButtons[1].setPosition(startX + buttonWidth + spacing, buttonY);
                    
                    // Update and draw buttons
                    long currentTime = System.currentTimeMillis();
                    for (int i = 0; i < equipButtons.length; i++) {
                        equipButtons[i].update(i == selectedEquipButton, currentTime);
                        equipButtons[i].draw(g, currentTime);
                    }
                } else {
                    g.setFont(FontPalette.get(Font.PLAIN, (int)(25 * scale)));
                    String hintText = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to continue";
                    FontMetrics hintFm = g.getFontMetrics();
                    int hintX = centerX - hintFm.stringWidth(hintText) / 2;
                    int hintY = currentY + (int)(125 * scale);
                    
                    float hintPulse = (float)Math.abs(Math.sin(now / 500.0));
                    g.setColor(new Color(150, 150, 150, (int)(200 * hintPulse)));
                    g.drawString(hintText, hintX, hintY);
                }
            }
        }
    }
    
    private void drawContractUnlockAnimation(Graphics2D g, int width, int height) {
        // Calculate animation progress (0.0 to 1.0)
        float progress = 1.0f - ((float) contractUnlockTimer / CONTRACT_UNLOCK_DURATION);
        
        // Calculate dismiss progress (1.0 = visible, 0.0 = gone)
        float dismissMultiplier = 1.0f;
        if (contractUnlockDismissing) {
            dismissMultiplier = (float) contractUnlockDismissTimer / CONTRACT_DISMISS_DURATION;
        }
        
        // Full dark overlay with fade
        int overlayAlpha = (int)(220 * Math.min(progress * 2, 1.0f) * dismissMultiplier);
        g.setColor(new Color(0, 0, 0, Math.min(overlayAlpha, 220)));
        
        // Reset translation to avoid screen shake affecting overlay
        Graphics2D g2d = (Graphics2D) g;
        g2d.setTransform(IDENTITY_TX);
        g.fillRect(0, 0, width, height);
        
        // Calculate position (slide up from bottom, slide down when dismissing)
        int centerX = width / 2;
        int startY = height + 350;
        int endY = height / 2;
        int dismissOffset = (int)((1.0f - dismissMultiplier) * 400);
        int currentY = (int)(startY + (endY - startY) * (1.0 - Math.pow(1.0 - progress, 2.5))) + dismissOffset;
        
        // Scale effect
        float scale;
        if (progress < 0.4f) {
            scale = (float)Math.pow(progress / 0.4f, 0.8);
        } else {
            scale = 1.0f;
        }
        scale *= dismissMultiplier;
        
        // Single glow layer for contracts (danger theme, scaled by shadow quality)
        long now = System.currentTimeMillis();
        if (shadowQuality > 0) {
            int glowSize = Math.max(1, (int)(420 * scale));
            float pulse = (float)Math.abs(Math.sin(now / 500.0)) * 0.3f + 0.7f;
            
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, currentY,
                glowSize,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{
                    new Color(255, 100, 50, (int)(50 * scale * pulse * dismissMultiplier)),
                    new Color(200, 50, 50, (int)(25 * scale * pulse * dismissMultiplier)),
                    new Color(150, 50, 50, 0)
                }
            );
            g.setPaint(glowPaint);
            g.fillOval(centerX - glowSize, currentY - glowSize, glowSize * 2, glowSize * 2);
        }
        
        // Animated danger particles
        if (progress > 0.3f && enableParticles) {
            int particleCount = 16;
            double baseAngle = now / 100.0;
            double timeSin = now / 200.0;
            for (int i = 0; i < particleCount; i++) {
                double angle = (baseAngle + i * (360.0 / particleCount)) * Math.PI / 180.0;
                int radius = (int)(210 * scale);
                int px = (int)(centerX + Math.cos(angle) * radius);
                int py = (int)(currentY + Math.sin(angle) * radius * 0.7);
                int size = (int)(5 * scale);
                
                int pAlpha = (int)(180 * (float)Math.abs(Math.sin(angle * 4 + timeSin)) * scale * dismissMultiplier);
                g.setColor(new Color(255, 150, 100, Math.min(255, Math.max(0, pAlpha))));
                g.fillOval(px - size/2, py - size/2, size, size);
            }
        }
        
        // Draw box with danger styling
        int boxWidth = (int)(UIScale.px(940) * scale);
        int boxHeight = (int)(UIScale.px(475) * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = currentY - boxHeight / 2;
        
        // Box shadow
        float progAlpha = Math.min(progress * 2, 1.0f);
        g.setColor(new Color(0, 0, 0, (int)(120 * progAlpha)));
        g.fillRoundRect(boxX + 6, boxY + 6, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Box background with dark red gradient
        GradientPaint boxGradient = new GradientPaint(
            boxX, boxY, new Color(50, 25, 30, (int)(245 * progAlpha)),
            boxX, boxY + boxHeight, new Color(30, 15, 20, (int)(245 * progAlpha))
        );
        g.setPaint(boxGradient);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Border with pulsing red/orange
        float borderPulse = (float)Math.abs(Math.sin(now / 300.0));
        int borderR = (int)(200 + 55 * borderPulse);
        int borderG = (int)(80 + 70 * borderPulse);
        int borderB = (int)(50 + 50 * borderPulse);
        g.setColor(new Color(borderR, borderG, borderB, (int)(255 * progAlpha)));
        g.setStroke(RenderCache.getStroke(3f));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Warning stripes (diagonal lines at top)
        if (progress > 0.2f) {
            float stripeAlpha = Math.min((progress - 0.2f) / 0.2f, 1.0f) * dismissMultiplier;
            g.setClip(boxX + 10, boxY + 10, boxWidth - 20, 25);
            g.setColor(new Color(255, 200, 0, (int)(100 * stripeAlpha)));
            for (int i = -10; i < boxWidth + 30; i += 20) {
                g.fillPolygon(
                    new int[]{boxX + i, boxX + i + 15, boxX + i + 25, boxX + i + 10},
                    new int[]{boxY + 10, boxY + 10, boxY + 35, boxY + 35},
                    4
                );
            }
            g.setClip(null);
        }
        
        // Text content
        if (progress > 0.25f) {
            float textAlpha = Math.min((progress - 0.25f) / 0.3f, 1.0f) * dismissMultiplier;
            
            // "RISK CONTRACTS UNLOCKED!" with shadow
            g.setFont(FontPalette.get(Font.BOLD, (int)(60 * scale)));
            String titleText = "RISK CONTRACTS UNLOCKED!";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = centerX - titleFm.stringWidth(titleText) / 2;
            int titleY = currentY - (int)(150 * scale);
            
            // Title shadow
            g.setColor(new Color(0, 0, 0, (int)(180 * textAlpha)));
            g.drawString(titleText, titleX + 3, titleY + 3);
            
            // Title text with danger pulse
            float titlePulse = (float)Math.abs(Math.sin(now / 400.0)) * 0.3f + 0.7f;
            g.setColor(new Color(255, 150, 100, (int)(255 * textAlpha * titlePulse)));
            g.drawString(titleText, titleX, titleY);
        }
        
        // Description section
        if (progress > 0.4f) {
            float descAlpha = Math.min((progress - 0.4f) / 0.3f, 1.0f) * dismissMultiplier;
            
            // Contract symbol - use ASCII exclamation
            g.setFont(FontPalette.get(Font.BOLD, (int)(75 * scale)));
            String symbol = "!";
            FontMetrics symbolFm = g.getFontMetrics();
            g.setColor(new Color(255, 200, 50, (int)(255 * descAlpha)));
            g.drawString(symbol, centerX - symbolFm.stringWidth(symbol) / 2, currentY - (int)(62 * scale));
            
            // Description lines
            String[] descLines = {
                "Choose a RISK CONTRACT before each level",
                "to multiply your rewards!",
                "",
                "- Bullet Storm - 2x bullets, 2x money",
                "- Speed Demon - Faster bullets, 1.75x money", 
                "- Shieldless - No active items, 1.5x money"
            };
            
            g.setFont(FontPalette.get(Font.PLAIN, (int)(25 * scale)));
            int lineY = currentY + (int)(12 * scale);
            for (String line : descLines) {
                if (line.isEmpty()) {
                    lineY += (int)(12 * scale);
                    continue;
                }
                FontMetrics lineFm = g.getFontMetrics();
                int lineX = centerX - lineFm.stringWidth(line) / 2;
                
                // Different colors for bullet points
                if (line.startsWith("- ")) {
                    g.setColor(new Color(255, 200, 150, (int)(220 * descAlpha)));
                } else {
                    g.setColor(new Color(200, 200, 200, (int)(220 * descAlpha)));
                }
                g.drawString(line, lineX, lineY);
                lineY += (int)(32 * scale);
            }
        }
        
        // "Press SPACE to continue" hint
        if (progress > 0.7f) {
            float hintAlpha = Math.min((progress - 0.7f) / 0.2f, 1.0f) * dismissMultiplier;
            g.setFont(FontPalette.get(Font.PLAIN, (int)(22 * scale)));
            String hintText = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to continue";
            FontMetrics hintFm = g.getFontMetrics();
            int hintX = centerX - hintFm.stringWidth(hintText) / 2;
            int hintY = currentY + (int)(200 * scale);
            
            float hintPulse = (float)Math.abs(Math.sin(now / 500.0));
            g.setColor(new Color(180, 180, 180, (int)(200 * hintPulse * hintAlpha)));
            g.drawString(hintText, hintX, hintY);
        }
    }
    
    private void drawEndlessUnlockAnimation(Graphics2D g, int width, int height) {
        float progress = 1.0f - ((float) endlessUnlockTimer / ENDLESS_UNLOCK_DURATION);
        
        float dismissMultiplier = 1.0f;
        if (endlessUnlockDismissing) {
            dismissMultiplier = (float) endlessUnlockDismissTimer / ENDLESS_DISMISS_DURATION;
        }
        
        // Dark overlay
        int overlayAlpha = (int)(220 * Math.min(progress * 2, 1.0f) * dismissMultiplier);
        g.setColor(new Color(0, 0, 0, Math.min(overlayAlpha, 220)));
        Graphics2D g2d = (Graphics2D) g;
        g2d.setTransform(IDENTITY_TX);
        g.fillRect(0, 0, width, height);
        
        int centerX = width / 2;
        int startY = height + 350;
        int endY = height / 2;
        int dismissOffset = (int)((1.0f - dismissMultiplier) * 400);
        int currentY = (int)(startY + (endY - startY) * (1.0 - Math.pow(1.0 - progress, 2.5))) + dismissOffset;
        
        float scale;
        if (progress < 0.4f) {
            scale = (float)Math.pow(progress / 0.4f, 0.8);
        } else {
            scale = 1.0f;
        }
        scale *= dismissMultiplier;
        
        // Purple glow
        long now = System.currentTimeMillis();
        if (shadowQuality > 0) {
            int glowSize = Math.max(1, (int)(420 * scale));
            float pulse = (float)Math.abs(Math.sin(now / 500.0)) * 0.3f + 0.7f;
            
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, currentY,
                glowSize,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{
                    new Color(140, 80, 255, (int)(50 * scale * pulse * dismissMultiplier)),
                    new Color(100, 50, 200, (int)(25 * scale * pulse * dismissMultiplier)),
                    new Color(80, 40, 180, 0)
                }
            );
            g.setPaint(glowPaint);
            g.fillOval(centerX - glowSize, currentY - glowSize, glowSize * 2, glowSize * 2);
        }
        
        // Orbiting particles
        if (progress > 0.3f && enableParticles) {
            int particleCount = 16;
            double baseAngle = now / 100.0;
            double timeSin = now / 200.0;
            for (int i = 0; i < particleCount; i++) {
                double angle = (baseAngle + i * (360.0 / particleCount)) * Math.PI / 180.0;
                int radius = (int)(210 * scale);
                int px = (int)(centerX + Math.cos(angle) * radius);
                int py = (int)(currentY + Math.sin(angle) * radius * 0.7);
                int size = (int)(5 * scale);
                
                int pAlpha = (int)(180 * (float)Math.abs(Math.sin(angle * 4 + timeSin)) * scale * dismissMultiplier);
                g.setColor(new Color(180, 130, 255, Math.min(255, Math.max(0, pAlpha))));
                g.fillOval(px - size/2, py - size/2, size, size);
            }
        }
        
        // Box
        int boxWidth = (int)(UIScale.px(940) * scale);
        int boxHeight = (int)(UIScale.px(475) * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = currentY - boxHeight / 2;
        
        float progAlpha = Math.min(progress * 2, 1.0f);
        
        // Box shadow
        g.setColor(new Color(0, 0, 0, (int)(120 * progAlpha)));
        g.fillRoundRect(boxX + 6, boxY + 6, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Box background with dark purple gradient
        GradientPaint boxGradient = new GradientPaint(
            boxX, boxY, new Color(40, 20, 60, (int)(245 * progAlpha)),
            boxX, boxY + boxHeight, new Color(20, 10, 40, (int)(245 * progAlpha))
        );
        g.setPaint(boxGradient);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Pulsing purple border
        float borderPulse = (float)Math.abs(Math.sin(now / 300.0));
        int borderR = (int)(140 + 60 * borderPulse);
        int borderG = (int)(80 + 50 * borderPulse);
        int borderB = (int)(220 + 35 * borderPulse);
        g.setColor(new Color(borderR, borderG, borderB, (int)(255 * progAlpha)));
        g.setStroke(RenderCache.getStroke(3f));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Decorative top stripes (purple theme)
        if (progress > 0.2f) {
            float stripeAlpha = Math.min((progress - 0.2f) / 0.2f, 1.0f) * dismissMultiplier;
            g.setClip(boxX + 10, boxY + 10, boxWidth - 20, 25);
            g.setColor(new Color(160, 100, 255, (int)(100 * stripeAlpha)));
            for (int i = -10; i < boxWidth + 30; i += 20) {
                g.fillPolygon(
                    new int[]{boxX + i, boxX + i + 15, boxX + i + 25, boxX + i + 10},
                    new int[]{boxY + 10, boxY + 10, boxY + 35, boxY + 35},
                    4
                );
            }
            g.setClip(null);
        }
        
        // Title
        if (progress > 0.25f) {
            float textAlpha = Math.min((progress - 0.25f) / 0.3f, 1.0f) * dismissMultiplier;
            
            g.setFont(FontPalette.get(Font.BOLD, (int)(42 * scale)));
            String titleText = "ENDLESS MODE UNLOCKED!";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = centerX - titleFm.stringWidth(titleText) / 2;
            int titleY = currentY - (int)(150 * scale);
            
            g.setColor(new Color(0, 0, 0, (int)(180 * textAlpha)));
            g.drawString(titleText, titleX + 3, titleY + 3);
            
            float titlePulse = (float)Math.abs(Math.sin(now / 400.0)) * 0.3f + 0.7f;
            g.setColor(new Color(180, 130, 255, (int)(255 * textAlpha * titlePulse)));
            g.drawString(titleText, titleX, titleY);
        }
        
        // Infinity symbol and description
        if (progress > 0.4f) {
            float descAlpha = Math.min((progress - 0.4f) / 0.3f, 1.0f) * dismissMultiplier;
            
            g.setFont(FontPalette.get(Font.BOLD, (int)(75 * scale)));
            String symbol = "\u221E"; // Infinity
            FontMetrics symbolFm = g.getFontMetrics();
            g.setColor(new Color(200, 160, 255, (int)(255 * descAlpha)));
            g.drawString(symbol, centerX - symbolFm.stringWidth(symbol) / 2, currentY - (int)(62 * scale));
            
            String[] descLines = {
                "You've conquered all 28 bosses!",
                "A new challenge awaits...",
                "",
                "Face the bosses again with increasing",
                "difficulty in an endless loop.",
                "How far can you go?"
            };
            
            g.setFont(FontPalette.get(Font.PLAIN, (int)(25 * scale)));
            int lineY = currentY + (int)(12 * scale);
            for (String line : descLines) {
                if (line.isEmpty()) {
                    lineY += (int)(12 * scale);
                    continue;
                }
                FontMetrics lineFm = g.getFontMetrics();
                int lineX = centerX - lineFm.stringWidth(line) / 2;
                g.setColor(new Color(200, 200, 220, (int)(220 * descAlpha)));
                g.drawString(line, lineX, lineY);
                lineY += (int)(32 * scale);
            }
        }
        
        // Press SPACE hint
        if (progress > 0.7f) {
            float hintAlpha = Math.min((progress - 0.7f) / 0.2f, 1.0f) * dismissMultiplier;
            g.setFont(FontPalette.get(Font.PLAIN, (int)(22 * scale)));
            String hintText = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to continue";
            FontMetrics hintFm = g.getFontMetrics();
            int hintX = centerX - hintFm.stringWidth(hintText) / 2;
            int hintY = currentY + (int)(200 * scale);
            
            float hintPulse = (float)Math.abs(Math.sin(now / 500.0));
            g.setColor(new Color(180, 180, 200, (int)(200 * hintPulse * hintAlpha)));
            g.drawString(hintText, hintX, hintY);
        }
    }
    
    /**
     * Start the next passive unlock animation from the pending queue.
     */
    private void startNextPassiveUnlockAnimation() {
        if (pendingPassiveUnlocks.isEmpty()) {
            passiveUnlockAnimation = false;
            return;
        }
        PassiveUpgrade upgrade = pendingPassiveUnlocks.poll();
        passiveUnlockAnimation = true;
        passiveUnlockDismissing = false;
        passiveUnlockTimer = PASSIVE_UNLOCK_DURATION;
        passiveUnlockDismissTimer = 0;
        unlockedPassiveName = upgrade.getName();
        unlockedPassiveDescription = upgrade.getDescription();
        gameData.markPassiveUnlockSeen(upgrade.getId());
    }
    
    private void drawPassiveUnlockAnimation(Graphics2D g, int width, int height) {
        // Calculate animation progress (0.0 to 1.0)
        float progress = 1.0f - ((float) passiveUnlockTimer / PASSIVE_UNLOCK_DURATION);
        
        // Calculate dismiss progress (1.0 = visible, 0.0 = gone)
        float dismissMultiplier = 1.0f;
        if (passiveUnlockDismissing) {
            dismissMultiplier = (float) passiveUnlockDismissTimer / PASSIVE_DISMISS_DURATION;
        }
        
        // Full dark overlay with fade
        int overlayAlpha = (int)(200 * Math.min(progress * 2, 1.0f) * dismissMultiplier);
        g.setColor(new Color(0, 0, 0, Math.min(overlayAlpha, 200)));
        
        // Reset translation to avoid screen shake affecting overlay
        Graphics2D g2d = (Graphics2D) g;
        g2d.setTransform(IDENTITY_TX);
        g.fillRect(0, 0, width, height);
        
        // Calculate position (slide up from bottom, slide down when dismissing)
        int centerX = width / 2;
        int startY = height + 350;
        int endY = height / 2;
        int dismissOffset = (int)((1.0f - dismissMultiplier) * 400);
        int currentY = (int)(startY + (endY - startY) * (1.0 - Math.pow(1.0 - progress, 2.5))) + dismissOffset;
        
        // Scale effect
        float scale;
        if (progress < 0.4f) {
            scale = (float)Math.pow(progress / 0.4f, 0.8);
        } else {
            scale = 1.0f;
        }
        scale *= dismissMultiplier;
        
        // Single glow layer (mystic theme, scaled by shadow quality)
        long now = System.currentTimeMillis();
        if (shadowQuality > 0) {
            int glowSize = Math.max(1, (int)(400 * scale));
            float pulse = (float)Math.abs(Math.sin(now / 600.0)) * 0.3f + 0.7f;
            
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, currentY,
                glowSize,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{
                    new Color(120, 80, 220, (int)(50 * scale * pulse * dismissMultiplier)),
                    new Color(80, 60, 180, (int)(25 * scale * pulse * dismissMultiplier)),
                    new Color(60, 40, 140, 0)
                }
            );
            g.setPaint(glowPaint);
            g.fillOval(centerX - glowSize, currentY - glowSize, glowSize * 2, glowSize * 2);
        }
        
        // Animated orbiting particles â€” optimized for performance
        if (progress > 0.3f && enableParticles) {
            int particleCount = 12;
            double baseAngle = now / 120.0;
            double timeSin = now / 250.0;
            for (int i = 0; i < particleCount; i++) {
                double angle = (baseAngle + i * (360.0 / particleCount)) * Math.PI / 180.0;
                int radius = (int)(200 * scale);
                int px = (int)(centerX + Math.cos(angle) * radius);
                int py = (int)(currentY + Math.sin(angle) * radius * 0.7);
                int size = (int)(4 * scale);
                
                int pAlpha = (int)(200 * (float)Math.abs(Math.sin(angle * 3 + timeSin)) * scale * dismissMultiplier);
                g.setColor(new Color(160, 120, 255, Math.min(255, Math.max(0, pAlpha))));
                g.fillOval(px - size/2, py - size/2, size, size);
            }
        }
        
        // Draw box with purple/blue styling
        int boxWidth = (int)(UIScale.px(1060) * scale);
        int boxHeight = (int)(UIScale.px(475) * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = currentY - boxHeight / 2;
        
        // Box shadow
        float progAlpha = Math.min(progress * 2, 1.0f);
        g.setColor(new Color(0, 0, 0, (int)(120 * progAlpha)));
        g.fillRoundRect(boxX + 6, boxY + 6, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Box background with dark purple gradient
        GradientPaint boxGradient = new GradientPaint(
            boxX, boxY, new Color(35, 25, 60, (int)(245 * progAlpha)),
            boxX, boxY + boxHeight, new Color(20, 15, 45, (int)(245 * progAlpha))
        );
        g.setPaint(boxGradient);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Border with pulsing purple/blue
        float borderPulse = (float)Math.abs(Math.sin(now / 400.0));
        int borderR = (int)(120 + 60 * borderPulse);
        int borderG = (int)(80 + 40 * borderPulse);
        int borderB = (int)(200 + 55 * borderPulse);
        g.setColor(new Color(borderR, borderG, borderB, (int)(255 * progAlpha)));
        g.setStroke(RenderCache.getStroke(3f));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, UIScale.px(25), UIScale.px(25));
        
        // Title: "NEW UPGRADE UNLOCKED!"
        if (progress > 0.25f) {
            float textAlpha = Math.min((progress - 0.25f) / 0.3f, 1.0f) * dismissMultiplier;
            
            g.setFont(FontPalette.get(Font.BOLD, (int)(60 * scale)));
            String titleText = "NEW UPGRADE UNLOCKED!";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = centerX - titleFm.stringWidth(titleText) / 2;
            int titleY = currentY - (int)(150 * scale);
            
            // Title shadow
            g.setColor(new Color(0, 0, 0, (int)(180 * textAlpha)));
            g.drawString(titleText, titleX + 3, titleY + 3);
            
            // Title text with purple pulse
            float titlePulse = (float)Math.abs(Math.sin(now / 500.0)) * 0.3f + 0.7f;
            g.setColor(new Color(180, 140, 255, (int)(255 * textAlpha * titlePulse)));
            g.drawString(titleText, titleX, titleY);
        }
        
        // Upgrade name
        if (progress > 0.4f) {
            float nameAlpha = Math.min((progress - 0.4f) / 0.25f, 1.0f) * dismissMultiplier;
            
            // Use ASCII symbol matching the shop icon for this upgrade type
            g.setFont(FontPalette.get(Font.BOLD, (int)(73 * scale)));
            String symbol = "*"; // Default fallback
            // Look up the correct icon from PassiveUpgradeManager
            if (passiveUpgradeManager != null) {
                for (PassiveUpgrade pu : passiveUpgradeManager.getAllUpgrades()) {
                    if (pu.getName().equals(unlockedPassiveName)) {
                        switch (pu.getType()) {
                            case MAX_HEALTH: symbol = "H"; break;
                            case ITEM_COOLDOWN: symbol = "C"; break;
                            case BULLET_SIZE: symbol = "B"; break;
                            case MONEY_AND_SCORE: symbol = "$"; break;
                            case CRITICAL_HIT: symbol = "*"; break;
                            case SPEED_BOOST: symbol = "S"; break;
                            case BULLET_SLOW: symbol = "T"; break;
                            case LUCKY_DODGE: symbol = "L"; break;
                            case TARGETING: symbol = "@"; break;
                            case FLARES: symbol = "F"; break;
                            default: symbol = "?"; break;
                        }
                        break;
                    }
                }
            }
            FontMetrics symbolFm = g.getFontMetrics();
            g.setColor(new Color(200, 170, 255, (int)(255 * nameAlpha)));
            g.drawString(symbol, centerX - symbolFm.stringWidth(symbol) / 2, currentY - (int)(56 * scale));
            
            // Upgrade name
            g.setFont(FontPalette.get(Font.BOLD, (int)(45 * scale)));
            FontMetrics nameFm = g.getFontMetrics();
            int nameX = centerX - nameFm.stringWidth(unlockedPassiveName) / 2;
            int nameY = currentY + (int)(25 * scale);
            
            // Name shadow
            g.setColor(new Color(0, 0, 0, (int)(150 * nameAlpha)));
            g.drawString(unlockedPassiveName, nameX + 2, nameY + 2);
            
            // Name text - bright white/lavender
            g.setColor(new Color(230, 220, 255, (int)(255 * nameAlpha)));
            g.drawString(unlockedPassiveName, nameX, nameY);
        }
        
        // Description (with word wrap to prevent overflow)
        if (progress > 0.5f) {
            float descAlpha = Math.min((progress - 0.5f) / 0.25f, 1.0f) * dismissMultiplier;
            
            g.setFont(FontPalette.get(Font.PLAIN, (int)(27 * scale)));
            FontMetrics descFm = g.getFontMetrics();
            int maxDescWidth = boxWidth - (int)(75 * scale);
            g.setColor(new Color(200, 190, 220, (int)(220 * descAlpha)));
            
            // Word wrap description
            String[] words = unlockedPassiveDescription.split(" ");
            StringBuilder line = new StringBuilder();
            int descY = currentY + (int)(58 * scale);
            for (String word : words) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (descFm.stringWidth(test) > maxDescWidth && line.length() > 0) {
                    String l = line.toString();
                    g.drawString(l, centerX - descFm.stringWidth(l) / 2, descY);
                    descY += descFm.getHeight();
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            if (line.length() > 0) {
                String l = line.toString();
                g.drawString(l, centerX - descFm.stringWidth(l) / 2, descY);
            }
        }
        
        // "Press SPACE to continue" hint
        if (progress > 0.7f) {
            float hintAlpha = Math.min((progress - 0.7f) / 0.2f, 1.0f) * dismissMultiplier;
            
            // Show remaining count if more upgrades are queued
            String remainingText = "";
            if (!pendingPassiveUnlocks.isEmpty()) {
                remainingText = " (" + pendingPassiveUnlocks.size() + " more)";
            }
            
            g.setFont(FontPalette.get(Font.PLAIN, (int)(22 * scale)));
            String hintText = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to continue" + remainingText;
            FontMetrics hintFm = g.getFontMetrics();
            int hintX = centerX - hintFm.stringWidth(hintText) / 2;
            int hintY = currentY + (int)(182 * scale);
            
            float hintPulse = (float)Math.abs(Math.sin(now / 500.0));
            g.setColor(new Color(180, 170, 200, (int)(200 * hintPulse * hintAlpha)));
            g.drawString(hintText, hintX, hintY);
        }
    }
    
    /**
     * Activate the selected debug menu option.
     */
    private void activateDebugOption(int option) {
        soundManager.playSound(SoundManager.Sound.UI_SELECT);
        switch (option) {
            case 0: // Unlock all levels
                gameData.unlockAllLevels();
                screenShakeIntensity = 5;
                break;
            case 1: // Give $10,000
                gameData.giveCheatMoney(10000);
                screenShakeIntensity = 5;
                break;
            case 2: // Max all upgrades
                gameData.maxAllUpgrades();
                screenShakeIntensity = 5;
                break;
            case 3: // Give $1,000
                gameData.giveCheatMoney(1000);
                screenShakeIntensity = 3;
                break;
            case 4: // Give $100
                gameData.giveCheatMoney(100);
                screenShakeIntensity = 2;
                break;
            case 5: // Unlock all active items
                gameData.unlockAllItems();
                screenShakeIntensity = 5;
                break;
            case 6: // Unlock risk contracts
                gameData.unlockContracts();
                screenShakeIntensity = 5;
                break;
            case 7: // Toggle showcase unlock all
                debugShowcaseUnlockAll = !debugShowcaseUnlockAll;
                screenShakeIntensity = 5;
                System.out.println("DEBUG: Showcase unlock all = " + debugShowcaseUnlockAll);
                break;
            case 8: // Unlock all passive upgrades (set bestRunLevel to max)
                gameData.setBestRunLevel(28);
                gameData.clearSeenPassiveUnlocks();
                screenShakeIntensity = 5;
                System.out.println("DEBUG: All passive upgrades unlocked (bestRunLevel=28, seenPassiveUnlocks cleared)");
                break;
            case 9: // Preview all item & contract popups
                debugPreviewItemAndContractPopups();
                break;
            case 10: // Preview all passive upgrade popups
                debugPreviewPassivePopups();
                break;
            case 11: // Set unlocked level to specific value
                gameData.setMaxUnlockedLevel(debugSetLevelValue);
                if (gameData.getCurrentLevel() > debugSetLevelValue) {
                    gameData.setCurrentLevel(debugSetLevelValue);
                }
                screenShakeIntensity = 5;
                System.out.println("DEBUG: Set max unlocked level to " + debugSetLevelValue);
                break;
            case 12: // Unlock endless mode
                gameData.unlockAllLevels();
                gameData.setEndlessUnlocked(true);
                gameData.setSeenEndlessUnlock(false); // Reset so popup shows
                // Trigger the endless unlock popup
                soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                endlessUnlockAnimation = true;
                endlessUnlockTimer = ENDLESS_UNLOCK_DURATION;
                gameData.setSeenEndlessUnlock(true);
                screenShakeIntensity = 5;
                System.out.println("DEBUG: Endless mode unlocked!");
                break;
            case 13: // Reset all leaderboard times
                leaderboardManager.clearAll();
                leaderboardManager.saveToGlobal(globalSaveData);
                saveManager.saveGlobal(globalSaveData);
                screenShakeIntensity = 5;
                System.out.println("DEBUG: All leaderboard times reset!");
                break;
            case 14: // Test leaderboard animation for a specific level
                int fakeTimeFrames = 600 + (int)(Math.random() * 3000); // Random 10s-60s
                String fakeSaveName = "Debug Test";
                GameMode debugMode = gameData.getGameMode();
                leaderboardManager.submitTime(debugMode, debugLeaderboardLevel, fakeTimeFrames, fakeSaveName);
                leaderboardManager.saveToGlobal(globalSaveData);
                saveManager.saveGlobal(globalSaveData);
                leaderboardCompletedLevel = debugLeaderboardLevel;
                leaderboardCompletedDifficulty = debugMode;
                // Use fake bossKillTime matching the submitted frames
                bossKillTime = fakeTimeFrames / 60.0;
                transitionToState(GameState.LEADERBOARD);
                screenShakeIntensity = 5;
                System.out.println("DEBUG: Submitted time " + fakeTimeFrames + " frames for level " + debugLeaderboardLevel + " (" + debugMode + ")");
                break;
        }
    }
    
    /**
     * Debug: preview all active item unlock animations and then the contract unlock animation.
     * Shows animations overlaid on the DEBUG state.
     */
    private void debugPreviewItemAndContractPopups() {
        // Store the items as a queue for cycling through
        debugItemPopupQueue = new java.util.LinkedList<>();
        for (ActiveItem.ItemType type : ActiveItem.ItemType.values()) {
            debugItemPopupQueue.add(type);
        }
        debugShowContractAfterItems = true;
        
        // Start the first item popup
        startNextDebugItemPopup();
        screenShakeIntensity = 5;
        System.out.println("DEBUG: Starting item popup preview (" + ActiveItem.ItemType.values().length + " items + contract)");
    }
    
    /**
     * Start the next debug item popup from the queue.
     */
    private void startNextDebugItemPopup() {
        if (debugItemPopupQueue == null || debugItemPopupQueue.isEmpty()) {
            itemUnlockAnimation = false;
            // Show contract popup after all items
            if (debugShowContractAfterItems) {
                debugShowContractAfterItems = false;
                soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                contractUnlockAnimation = true;
                contractUnlockDismissing = false;
                contractUnlockTimer = CONTRACT_UNLOCK_DURATION;
                contractUnlockDismissTimer = 0;
            }
            return;
        }
        ActiveItem.ItemType type = debugItemPopupQueue.poll();
        ActiveItem item = new ActiveItem(type);
        unlockedItemName = item.getName();
        unlockedItemDescription = item.getDescription();
        showEquipPrompt = false;
        itemUnlockAnimation = true;
        itemUnlockDismissing = false;
        itemUnlockTimer = ITEM_UNLOCK_DURATION;
        itemUnlockDismissTimer = 0;
        soundManager.playSound(SoundManager.Sound.ITEM_PICKUP);
    }
    
    /**
     * Debug: preview all passive upgrade unlock animations.
     * Shows animations overlaid on the DEBUG state (transitions to SHOP temporarily).
     */
    private void debugPreviewPassivePopups() {
        // Clear seen unlocks so they all show
        gameData.clearSeenPassiveUnlocks();
        // Set bestRunLevel high enough for all to qualify
        gameData.setBestRunLevel(28);
        
        // Queue all passive upgrades that have unlock levels > 0
        pendingPassiveUnlocks.clear();
        passiveUnlockAnimation = false;
        passiveUnlockDismissing = false;
        for (PassiveUpgrade upgrade : passiveUpgradeManager.getAllUpgrades()) {
            if (upgrade.getUnlockLevel() > 0) {
                pendingPassiveUnlocks.add(upgrade);
            }
        }
        
        if (!pendingPassiveUnlocks.isEmpty()) {
            startNextPassiveUnlockAnimation();
        }
        
        // Transition to SHOP to display the passive animations (they render on SHOP state)
        shopScroll = 0;
        shopScrollAnimated = 0;
        shopManager.rebuildSortedOrder();
        transitionToState(GameState.SHOP);
        screenShakeIntensity = 5;
        System.out.println("DEBUG: Starting passive popup preview (" + pendingPassiveUnlocks.size() + " upgrades queued + 1 showing)");
    }
    
    // Helper methods to get active upgrade levels from PassiveUpgradeManager
    private int getActiveSpeedLevel() {
        if (passiveUpgradeManager != null) {
            PassiveUpgrade upgrade = passiveUpgradeManager.getUpgrade("speed");
            if (upgrade != null) {
                return upgrade.getActiveLevel();
            }
        }
        return 0;
    }
    
    private int getActiveBulletSlowLevel() {
        if (passiveUpgradeManager != null) {
            PassiveUpgrade upgrade = passiveUpgradeManager.getUpgrade("bullet_slow");
            if (upgrade != null) {
                return upgrade.getActiveLevel();
            }
        }
        return 0;
    }
    
    private int getActiveLuckyDodgeLevel() {
        if (passiveUpgradeManager != null) {
            PassiveUpgrade upgrade = passiveUpgradeManager.getUpgrade("lucky_dodge");
            if (upgrade != null) {
                return upgrade.getActiveLevel();
            }
        }
        return 0;
    }
    
    private int getActiveTargetingLevel() {
        if (passiveUpgradeManager != null) {
            PassiveUpgrade upgrade = passiveUpgradeManager.getUpgrade("targeting");
            if (upgrade != null) {
                return upgrade.getActiveLevel();
            }
        }
        return 0;
    }
    
    private int getActiveFlaresLevel() {
        if (passiveUpgradeManager != null) {
            PassiveUpgrade upgrade = passiveUpgradeManager.getUpgrade("flares");
            if (upgrade != null) {
                return upgrade.getActiveLevel();
            }
        }
        return 0;
    }
}
