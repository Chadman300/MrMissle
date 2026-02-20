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
import config.UITheme;

public class Game extends JPanel implements Runnable {
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
    private int mouseX, mouseY; // Mouse position for UI navigation
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
    
    // Keybind & controller systems
    public static KeyBindManager keyBindManager;
    private ControllerManager controllerManager;
    public static boolean waitingForKeyBind = false;
    public static int rebindingActionIndex = -1; // Index into controls settings list
    
    // Game objects
    private Player player;
    private Boss currentBoss;
    private List<Bullet> bullets;
    private List<Bullet> bulletPool; // Pool for recycling bullets
    private List<Particle> particles;
    private List<Particle> particlePool; // Pool for recycling particles
    private List<Particle> introParticles; // Separate particles for boss intro cinematic (screen-space)
    private List<BeamAttack> beamAttacks;
    
    // Particle limits for performance
    private static final int MAX_PARTICLES = 200; // Reduced for better performance
    private static final int MAX_BULLETS = 500; // Cap bullets for performance
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
    private double bossIntroPlayerX; // Player X position (center → left)
    private double bossIntroPlayerY; // Player Y position (computed from phase)
    private double bossIntroBossX;   // Boss X position (off-screen → right)
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
        {"TIME_SLOW", "15", "Time Slow", "Slow bullets & beams by 85%\n7.5 second cooldown, lasts 4 seconds"},
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
    
    // Loading progress
    private volatile int loadingProgress = 0;
    private volatile int targetLoadingProgress = 0;
    private double displayedLoadingProgress = 0.0;
    private volatile boolean loadingComplete = false;
    
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
        bullets = new ArrayList<>();
        bulletPool = new ArrayList<>();
        particles = new ArrayList<>();
        particlePool = new ArrayList<>();
        introParticles = new ArrayList<>();
        beamAttacks = new ArrayList<>();
        bulletGrid = new HashMap<>();
        gameData = new GameData();
        shopManager = new ShopManager(gameData);
        achievementManager = new AchievementManager();
        passiveUpgradeManager = new PassiveUpgradeManager();
        shopManager.setPassiveUpgradeManager(passiveUpgradeManager); // Connect passive upgrades to shop
        comboSystem = new ComboSystem();
        saveManager = new SaveManager(); // Initialize save manager
        pendingAchievements = new ArrayList<>();
        damageNumbers = new ArrayList<>();
        soundManager = SoundManager.getInstance();
        
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
        
        // Initialize game feel effects
        hitFreezeFrames = 0;
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
        });
        
        // Add mouse listeners for UI navigation
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // Transform mouse coordinates from screen space to game space
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                double scaleX = (double) panelWidth / WIDTH;
                double scaleY = (double) panelHeight / HEIGHT;
                double scale = Math.min(scaleX, scaleY);
                int scaledWidth = (int) (WIDTH * scale);
                int scaledHeight = (int) (HEIGHT * scale);
                int offsetX = (panelWidth - scaledWidth) / 2;
                int offsetY = (panelHeight - scaledHeight) / 2;
                
                mouseX = (int) ((e.getX() - offsetX) / scale);
                mouseY = (int) ((e.getY() - offsetY) / scale);
                handleMouseMove();
            }
            
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                // Transform mouse coordinates from screen space to game space
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                double scaleX = (double) panelWidth / WIDTH;
                double scaleY = (double) panelHeight / HEIGHT;
                double scale = Math.min(scaleX, scaleY);
                int scaledWidth = (int) (WIDTH * scale);
                int scaledHeight = (int) (HEIGHT * scale);
                int offsetX = (panelWidth - scaledWidth) / 2;
                int offsetY = (panelHeight - scaledHeight) / 2;
                
                mouseX = (int) ((e.getX() - offsetX) / scale);
                mouseY = (int) ((e.getY() - offsetY) / scale);
                
                // Delegate to HUD layout editor when on HUD tab
                if (gameState == GameState.SETTINGS && selectedSettingsCategory == 5 && renderer != null) {
                    renderer.hudLayoutEditor.handleMouseDragged(mouseX, mouseY, hudLayout);
                }
            }
        });
        
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                // Transform mouse coordinates from screen space to game space
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                double scaleX = (double) panelWidth / WIDTH;
                double scaleY = (double) panelHeight / HEIGHT;
                double scale = Math.min(scaleX, scaleY);
                int scaledWidth = (int) (WIDTH * scale);
                int scaledHeight = (int) (HEIGHT * scale);
                int offsetX = (panelWidth - scaledWidth) / 2;
                int offsetY = (panelHeight - scaledHeight) / 2;
                mouseX = (int) ((e.getX() - offsetX) / scale);
                mouseY = (int) ((e.getY() - offsetY) / scale);

                // Handle press for responsive feel
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    handleMouseClick(e);
                }
            }
            
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
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
        
        // Keybind rebinding intercept — capture the pressed key when waiting
        if (waitingForKeyBind && gameState == GameState.SETTINGS && selectedSettingsCategory == 4) {
            if (key == KeyEvent.VK_ESCAPE) {
                // Cancel rebinding
                waitingForKeyBind = false;
                rebindingActionIndex = -1;
                soundManager.playSound(SoundManager.Sound.UI_CANCEL);
            } else if (!KeyBindManager.isReservedKey(key)) {
                // Bind the key to the action
                // rebindingActionIndex 1-7 maps to Action values 0-6 (index 0 is preset selector, index 8 is input device)
                int actionIndex = rebindingActionIndex - 1; // Subtract 1 for preset row
                KeyBindManager.Action[] actions = KeyBindManager.Action.values();
                if (actionIndex >= 0 && actionIndex < actions.length) {
                    keyBindManager.setKey(actions[actionIndex], key);
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                }
                waitingForKeyBind = false;
                rebindingActionIndex = -1;
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
                        // Clicking on an existing save — load it
                        SaveManager.SaveMetadata meta = saveMetadataCache.get(selectedSaveSlot);
                        int slot = meta.slotNumber;
                        SaveData saveData = saveManager.load(slot);
                        if (saveData != null) {
                            saveData.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
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
                            soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                            screenShakeIntensity = 5;
                            transitionToState(GameState.MENU);
                        }
                    } else {
                        // "New Save" button — go to mode selection
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
                    // Create the save with the selected game mode
                    GameMode[] modes = GameMode.values();
                    GameMode chosenMode = modes[selectedGameModeIndex];
                    SaveData newSave = new SaveData();
                    newSave.saveName = "Save " + pendingSaveSlot;
                    newSave.gameMode = chosenMode;
                    if (saveManager.save(pendingSaveSlot, newSave)) {
                        newSave.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
                        soundManager.setMasterVolume(gameData.getMasterVolume());
                        soundManager.setSfxVolume(gameData.getSfxVolume());
                        soundManager.setUiVolume(gameData.getUiVolume());
                        soundManager.setMusicVolume(gameData.getMusicVolume());
                        soundManager.setSoundEnabled(gameData.isSoundEnabled());
                        hasSavedGame = false;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                        screenShakeIntensity = 5;
                        transitionToState(GameState.MENU);
                    }
                    pendingSaveSlot = -1;
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    // Go back to save select
                    pendingSaveSlot = -1;
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                    screenShakeIntensity = 3;
                    transitionToState(GameState.SAVE_SELECT);
                }
                break;
                
            case MENU:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    selectedMenuItem = Math.max(0, selectedMenuItem - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    selectedMenuItem = Math.min(6, selectedMenuItem + 1); // Updated to 6 for new menu item
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                    screenShakeIntensity = 5;
                    // New order: Select Level, Shop, Stats, Achievements, Game Info, Settings, Save Files
                    switch (selectedMenuItem) {
                        case 0: transitionToState(GameState.LEVEL_SELECT); break;
                        case 1: shopEnteredFrom = GameState.MENU; transitionToState(GameState.SHOP); break;
                        case 2: transitionToState(GameState.STATS); break;
                        case 3: transitionToState(GameState.ACHIEVEMENTS); break;
                        case 4: transitionToState(GameState.INFO); break;
                        case 5: settingsEnteredFrom = GameState.MENU; transitionToState(GameState.SETTINGS); break;
                        case 6: transitionToState(GameState.SAVE_SELECT); break; // New: Save Files
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
                // All upgrades now come from PassiveUpgradeManager (1 active item + upgrades)
                int maxStatItems = 1 + (passiveUpgradeManager != null ? passiveUpgradeManager.getAllUpgrades().size() : 0);
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
                        // Browse active items (all 9 including locked)
                        int idx = renderer.getStatsActiveItemDisplayIndex();
                        if (idx > 0) {
                            idx--;
                            renderer.setStatsActiveItemDisplayIndex(idx);
                            // Auto-equip if unlocked
                            autoEquipStatsItem(idx);
                        }
                        screenShakeIntensity = 2;
                    } else if (selectedStatItem >= 1 && passiveUpgradeManager != null) {
                        // All upgrades are now in PassiveUpgradeManager (index 1+)
                        int upgradeIndex = selectedStatItem - 1;
                        int numUpgrades = passiveUpgradeManager.getAllUpgrades().size();
                        // Skip Extra Missiles (last item) - it's read-only
                        if (upgradeIndex < numUpgrades - 1) {
                            PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);
                            if (upgrade.getActiveLevel() > 0) {
                                upgrade.setActiveLevel(upgrade.getActiveLevel() - 1);
                                screenShakeIntensity = 2;
                            }
                        }
                    }
                }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    if (selectedStatItem == 0) {
                        // Browse active items (all 9 including locked)
                        int idx = renderer.getStatsActiveItemDisplayIndex();
                        if (idx < 8) {
                            idx++;
                            renderer.setStatsActiveItemDisplayIndex(idx);
                            // Auto-equip if unlocked
                            autoEquipStatsItem(idx);
                        }
                        screenShakeIntensity = 2;
                    } else if (selectedStatItem >= 1 && passiveUpgradeManager != null) {
                        // All upgrades are now in PassiveUpgradeManager (index 1+)
                        int upgradeIndex = selectedStatItem - 1;
                        int numUpgrades = passiveUpgradeManager.getAllUpgrades().size();
                        // Skip Extra Missiles (last item) - it's read-only
                        if (upgradeIndex < numUpgrades - 1) {
                            PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);
                            // Only allow increasing up to purchased level (not maxLevel)
                            if (upgrade.getActiveLevel() < upgrade.getCurrentLevel()) {
                                upgrade.setActiveLevel(upgrade.getActiveLevel() + 1);
                                screenShakeIntensity = 2;
                            }
                        }
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) { transitionToState(GameState.MENU); screenShakeIntensity = 3; }
                break;
                
            case SETTINGS:
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
                        ensureSettingsItemVisible();
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                }
                else if (key == KeyEvent.VK_LEFT) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to previous tab
                        selectedSettingsCategory = (selectedSettingsCategory + 5) % 6;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, -1)) {
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 2;
                        }
                    }
                }
                else if (key == KeyEvent.VK_RIGHT) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to next tab
                        selectedSettingsCategory = (selectedSettingsCategory + 1) % 6;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, 1)) {
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 2;
                        }
                    }
                }
                else if (key == KeyEvent.VK_A) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to previous tab
                        selectedSettingsCategory = (selectedSettingsCategory + 5) % 6;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, -1)) {
                            soundManager.playSound(SoundManager.Sound.UI_SELECT);
                            screenShakeIntensity = 2;
                        }
                    }
                }
                else if (key == KeyEvent.VK_D) {
                    if (selectedSettingsItem == -1) {
                        // Tabs selected - switch to next tab
                        selectedSettingsCategory = (selectedSettingsCategory + 1) % 6;
                        clampSettingsItem();
                        soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                        screenShakeIntensity = 2;
                    } else {
                        // Try to adjust setting
                        if (adjustSetting(selectedSettingsItem, 1)) {
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
                    clampSettingsItem();
                    selectedSettingsItem = -1;
                    soundManager.playSound(SoundManager.Sound.UI_SWIPE);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_R) {
                    // Reset settings to defaults
                    resetSettingsToDefaults();
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_ESCAPE) { 
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                    // Save settings changes
                    performAutoSave();
                    // Return to where we came from (pause menu or main menu)
                    if (settingsEnteredFrom == GameState.PLAYING) {
                        // Came from pause menu - return to paused game
                        isPaused = true;
                        gameState = GameState.PLAYING;
                    } else {
                        transitionToState(GameState.MENU);
                    }
                    screenShakeIntensity = 3; 
                }
                break;
                
            case INFO:
                if (key == KeyEvent.VK_ESCAPE) transitionToState(GameState.MENU);
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
                
            case ATTACK_INTRO:
                // Press any key to continue from attack intro
                if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    dismissAttackIntro();
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                } else if (key == KeyEvent.VK_ESCAPE) {
                    dismissAttackIntro();
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                }
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
                    transitionToState(GameState.MENU);
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
                if (isPaused) {
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
                        isPaused = true;
                        selectedPauseItem = 0;
                        renderer.configurePauseMenu(debugShowcaseInGameplay);
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
                    } else if ((keyBindManager != null ? keyBindManager.isAction(KeyBindManager.Action.CONFIRM, key) : key == KeyEvent.VK_SPACE) && bossIntroActive) {
                        // Skip boss intro cinematic
                        bossIntroActive = false;
                        screenShakeIntensity = 5;
                        // Reset player and boss to proper gameplay positions
                        if (player != null) player.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT - 200);
                        if (currentBoss != null) currentBoss.setPosition(WORLD_WIDTH / 2, 100);
                        introParticles.clear();
                        if (demoIntroActive) {
                            demoIntroActive = false;
                            transitionToState(GameState.MENU);
                        }
                    } else if ((keyBindManager != null ? keyBindManager.isAction(KeyBindManager.Action.USE_ITEM, key) : key == KeyEvent.VK_SPACE) && !eKeyPressed && !introPanActive && !bossIntroActive) {
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
                    } else if ((key == KeyEvent.VK_N || key == KeyEvent.VK_ESCAPE) && debugShowcaseMode) {
                        // Debug showcase: Return to selection screen - restore the real game level
                        gameData.setCurrentLevel(savedRealLevel);
                        debugShowcaseInGameplay = false;
                        bullets.clear();
                        beamAttacks.clear();
                        transitionToState(GameState.ATTACK_SHOWCASE);
                        System.out.println("DEBUG SHOWCASE: Returning to selection - restored level to " + savedRealLevel);
                    }
                }
                break;
                
            case SHOP:
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
                        // Continue to level select
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        transitionToState(GameState.LEVEL_SELECT);
                        screenShakeIntensity = 5;
                    } else {
                        System.out.println("DEBUG SHOP: Attempting purchase of item " + selected + ", money: " + gameData.getTotalMoney() + ", cost: " + shopManager.getItemCost(selected));
                        boolean purchased = shopManager.purchaseItem(selected);
                        System.out.println("DEBUG SHOP: Purchase result: " + purchased + ", money after: " + gameData.getTotalMoney());
                        if (purchased) {
                            soundManager.playSound(SoundManager.Sound.PURCHASE_SUCCESS);
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
                    // Roguelike: Give survival reward and start new run from level 1
                    int survivalReward = gameData.getSurvivalTime() / 60;
                    gameData.addTotalMoney(survivalReward);
                    gameData.startNewRun(); // Resets to level 1, keeps upgrades/items
                    passiveUpgradeManager.resetMissilesPrice(); // Reset extra missiles price for new run
                    performAutoSave(); // Save progress after death
                    // Force players to go through level select again
                    transitionToState(GameState.LEVEL_SELECT);
                } else if (key == KeyEvent.VK_ESCAPE) {
                    // Go to menu but don't start new run yet (let them check stats, shop, etc)
                    int survivalReward = gameData.getSurvivalTime() / 60;
                    gameData.addTotalMoney(survivalReward);
                    gameData.startNewRun();
                    passiveUpgradeManager.resetMissilesPrice(); // Reset extra missiles price for new run
                    performAutoSave(); // Save progress after death
                    transitionToState(GameState.MENU);
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
                    
                    // Unlock next level
                    int currentLevel = gameData.getCurrentLevel();
                    gameData.setMaxUnlockedLevel(Math.max(gameData.getMaxUnlockedLevel(), currentLevel + 1));
                    
                    // Award money
                    int bossReward = 50 + (currentLevel * 10);
                    if (!gameData.getDefeatedBosses()[currentLevel - 1]) {
                        gameData.setBossDefeated(currentLevel - 1, true);
                        bossReward += 100;
                    }
                    
                    System.out.println("DEBUG WIN: Level " + currentLevel + " completed, money before: " + gameData.getTotalMoney() + ", reward: " + bossReward);
                    gameData.addRunMoney(bossReward);
                    gameData.addTotalMoney(bossReward);
                    System.out.println("DEBUG WIN: Money after reward: " + gameData.getTotalMoney());
                    
                    gameData.setCurrentLevel(currentLevel + 1);
                    shopEnteredFrom = GameState.PLAYING; // Came from beating a boss
                    transitionToState(GameState.SHOP);
                }
                break;
                
            case DEBUG:
                if (key == KeyEvent.VK_1) {
                    // Unlock all levels
                    gameData.unlockAllLevels();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_2) {
                    // Give 10000 money
                    gameData.giveCheatMoney(10000);
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_3) {
                    // Max all upgrades
                    gameData.maxAllUpgrades();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_4) {
                    // Give 1000 money
                    gameData.giveCheatMoney(1000);
                    screenShakeIntensity = 3;
                }
                else if (key == KeyEvent.VK_5) {
                    // Give 100 money
                    gameData.giveCheatMoney(100);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_6) {
                    // Unlock all active items
                    gameData.unlockAllItems();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_7) {
                    // Unlock risk contracts
                    gameData.unlockContracts();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_8) {
                    // Toggle unlock all showcase content
                    debugShowcaseUnlockAll = !debugShowcaseUnlockAll;
                    screenShakeIntensity = 5;
                    System.out.println("DEBUG: Showcase unlock all = " + debugShowcaseUnlockAll);
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
    
    private void navigateLevelMap(int direction) {
        int newLevel = gameData.getSelectedLevelView() + direction;
        if (newLevel >= 1 && newLevel <= 28) {
            gameData.setSelectedLevelView(newLevel);
            // Set target scroll position (will animate smoothly)
            levelSelectScroll = newLevel;
            soundManager.playSound(SoundManager.Sound.UI_CURSOR);
        }
    }
    
    private void tryStartLevel() {
        int selectedLevel = gameData.getSelectedLevelView();
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
        if (attackId.contains("bullet")) symbol = "•";
        else if (attackId.contains("beam")) symbol = "═";
        else if (attackId.contains("shock")) symbol = "◯";
        else if (attackId.contains("grenade") || attackId.contains("nuke") || attackId.contains("bomb")) symbol = "💣";
        else if (attackId.contains("twirl")) symbol = "↻";
        else if (attackId.contains("spiral")) symbol = "🌀";
        
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
            // Check if hovering over category tabs
            String[] categories = {"GRAPHICS", "AUDIO", "GAMEPLAY", "DEBUG", "CONTROLS", "HUD"};
            int tabWidth = 130;
            int tabStartX = (WIDTH - categories.length * tabWidth) / 2;
            int tabY = 130;
            
            boolean hoveringTab = false;
            for (int i = 0; i < categories.length; i++) {
                int tabX = tabStartX + i * tabWidth;
                if (mouseX >= tabX && mouseX <= tabX + tabWidth - 10 &&
                    mouseY >= tabY && mouseY <= tabY + 40) {
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
                // Calculate item positions based on current category
                int maxItems = getMaxSettingsItems();
                int boxX = (WIDTH - 700) / 2;
                int boxWidth = 700;
                int itemHeight = 120;
                int startY = 240 - (int)settingsScroll;
                
                boolean foundHover = false;
                for (int i = 0; i <= maxItems; i++) {
                    int boxY = startY + i * itemHeight - 20;
                    int boxHeight = 70;
                    
                    // Skip if outside visible area (200 to HEIGHT - 60)
                    if (boxY + boxHeight < 200 || boxY > HEIGHT - 90) {
                        continue;
                    }
                    
                    // Check if mouse is over this item
                    if (mouseX >= boxX && mouseX <= boxX + boxWidth &&
                        mouseY >= boxY && mouseY <= boxY + boxHeight) {
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
            // Check if hovering over mode cards
            int cardWidth = 700;
            int cardHeight = 130;
            int cardX = (WIDTH - cardWidth) / 2;
            int modeStartY = 180;
            int cardSpacing = 150;
            
            for (int i = 0; i < GameMode.values().length; i++) {
                int cardY = modeStartY + i * cardSpacing;
                if (mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + cardHeight) {
                    if (selectedGameModeIndex != i) {
                        selectedGameModeIndex = i;
                        soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                        screenShakeIntensity = 1;
                    }
                    break;
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
            int slotWidth = 800;
            int slotHeight = 160;
            int slotX = (WIDTH - slotWidth) / 2;
            int startY = 200;
            int slotSpacing = 180;
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
                        SaveData saveData = saveManager.load(slot);
                        if (saveData != null) {
                            saveData.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
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
                            soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                            screenShakeIntensity = 5;
                            transitionToState(GameState.MENU);
                        }
                    } else {
                        // "New Save" — go to mode selection
                        pendingSaveSlot = saveManager.getNextAvailableSlot();
                        selectedGameModeIndex = 1;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                        screenShakeIntensity = 5;
                        transitionToState(GameState.MODE_SELECT);
                    }
                    break;
                }
            }
        } else if (gameState == GameState.MODE_SELECT) {
            // Check if clicking on mode cards
            int cardWidth = 700;
            int cardHeight = 130;
            int cardX = (WIDTH - cardWidth) / 2;
            int startY = 180;
            int cardSpacing = 150;
            
            for (int i = 0; i < GameMode.values().length; i++) {
                int cardY = startY + i * cardSpacing;
                if (mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + cardHeight) {
                    selectedGameModeIndex = i;
                    // Simulate Enter to confirm selection
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, ' '));
                    break;
                }
            }
        } else if (gameState == GameState.MENU) {
            UIButton[] buttons = renderer.getMenuButtons();
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].contains(mouseX, mouseY)) {
                    selectedMenuItem = i;
                    activateMenuItem(selectedMenuItem);
                    break;
                }
            }
        } else if (gameState == GameState.SETTINGS) {
            // Check if clicking on category tabs first
            String[] categories = {"GRAPHICS", "AUDIO", "GAMEPLAY", "DEBUG", "CONTROLS", "HUD"};
            int tabWidth = 130;
            int tabStartX = (WIDTH - categories.length * tabWidth) / 2;
            int tabY = 130;
            
            boolean clickedTab = false;
            for (int i = 0; i < categories.length; i++) {
                int tabX = tabStartX + i * tabWidth;
                if (mouseX >= tabX && mouseX <= tabX + tabWidth - 10 &&
                    mouseY >= tabY && mouseY <= tabY + 40) {
                    if (selectedSettingsCategory != i) {
                        selectedSettingsCategory = i;
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
            // Check if clicking on shop items
            UIButton[] buttons = renderer.getShopButtons();
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] != null && buttons[i].contains(mouseX, mouseY)) {
                    shopManager.setSelectedShopItem(i);
                    
                    // Perform the purchase/continue action (same as SPACE key)
                    if (i == 0) {
                        // Continue button - go to level select
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        transitionToState(GameState.LEVEL_SELECT);
                        screenShakeIntensity = 5;
                    } else {
                        // Try to purchase item
                        boolean purchased = shopManager.purchaseItem(i);
                        if (purchased) {
                            soundManager.playSound(SoundManager.Sound.PURCHASE_SUCCESS);
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
                if (gameData.hasActiveItems()) {
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
            
            // Check Upgrade cards
            if (passiveUpgradeManager != null) {
                java.util.List<PassiveUpgrade> upgrades = passiveUpgradeManager.getAllUpgrades();
                
                // All upgrades except Extra Missiles (last one is read-only)
                for (int i = 0; i < upgrades.size() - 1; i++) {
                    if (mouseX >= itemX && mouseX <= itemX + cardWidth &&
                        mouseY >= y && mouseY <= y + cardHeight) {
                        selectedStatItem = currentIndex;
                        updateStatsScroll();
                        
                        // Left side = decrease, right side = increase
                        PassiveUpgrade upgrade = upgrades.get(i);
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
                        return;
                    }
                    y += cardHeight + cardSpacing;
                    currentIndex++;
                }
                
                // Extra Missiles card (read-only, just select it)
                y += 50; // section header offset
                if (upgrades.size() > 0 && mouseX >= itemX && mouseX <= itemX + cardWidth &&
                    mouseY >= y && mouseY <= y + cardHeight) {
                    selectedStatItem = currentIndex;
                    updateStatsScroll();
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                    screenShakeIntensity = 1;
                    return;
                }
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
                int maxStatItems = 1 + 4 + (passiveUpgradeManager != null ? passiveUpgradeManager.getAllUpgrades().size() : 0);
                if (rotation > 0) {
                    // Scroll down - select next item
                    selectedStatItem = Math.min(maxStatItems - 1, selectedStatItem + 1);
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
                if (rotation > 0) {
                    // Scroll down - scroll by 3 items for faster scrolling
                    settingsScroll += 360; // 120px per item * 3
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
        // New order: Select Level, Shop, Stats, Achievements, Game Info, Settings, Save Files
        switch (index) {
            case 0: transitionToState(GameState.LEVEL_SELECT); break;
            case 1: shopEnteredFrom = GameState.MENU; transitionToState(GameState.SHOP); break;
            case 2: transitionToState(GameState.STATS); break;
            case 3: transitionToState(GameState.ACHIEVEMENTS); break;
            case 4: transitionToState(GameState.INFO); break;
            case 5: settingsEnteredFrom = GameState.MENU; transitionToState(GameState.SETTINGS); break;
            case 6: transitionToState(GameState.SAVE_SELECT); break; // Save Files
        }
    }
    
    private void activatePauseMenuItem(int index) {
        if (renderer.isShowcasePauseMode()) {
            // Showcase pause menu: Settings, Restart, Main Menu, Back to Showcase
            switch (index) {
                case 0: // Settings
                    soundManager.playSound(SoundManager.Sound.MENU_OPEN);
                    settingsEnteredFrom = GameState.PLAYING;
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
                    gameState = GameState.SETTINGS;
                    break;
                case 2: // Main Menu
                    System.out.println("DEBUG: Going to main menu from pause - saving game state");
                    isPaused = false;
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
    
    // Handle player death - check for missiles first
    private void handlePlayerDeath() {
        // In debug showcase mode, player is invincible
        if (debugShowcaseMode) {
            return; // Ignore death in showcase mode
        }
        
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
            // DEBRIS fragments - spinning missile body fragments (40 pieces)
            for (int i = 0; i < 40; i++) {
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
            
            // EXHAUST particles - fireball bloom (55 pieces)
            for (int i = 0; i < 55; i++) {
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
            
            // EXPLOSION rings - 7 expanding rings (white-hot center to deep red)
            for (int i = 0; i < 7; i++) {
                addParticle(
                    deathExplosionX, deathExplosionY, 0, 0,
                    new Color(255, Math.max(0, 250 - i * 40), Math.max(0, 140 - i * 20), Math.max(60, 250 - i * 30)),
                    40 + i * 8, 25 + i * 30,
                    Particle.ParticleType.EXPLOSION
                );
            }
            
            // SPARK streaks - fast radiating sparks (40 pieces)
            for (int i = 0; i < 40; i++) {
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
            
            // SMOKE particles - lingering dark smoke (15 pieces)
            for (int i = 0; i < 15; i++) {
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
            
            // EMBERS - slow drifting fire particles for lingering effect (20 pieces)
            for (int i = 0; i < 20; i++) {
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
        bullets.clear();
        if (savedBullets != null) bullets.addAll(savedBullets);
        particles.clear();
        if (savedParticles != null) particles.addAll(savedParticles);
        beamAttacks.clear();
        if (savedBeamAttacks != null) beamAttacks.addAll(savedBeamAttacks);
        damageNumbers.clear();
        if (savedDamageNumbers != null) damageNumbers.addAll(savedDamageNumbers);
        
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
        
        // Restore game state
        bossHitCount = rs.bossHitCount;
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
        if (saveManager.getCurrentSaveSlot() == -1) {
            // No active save slot
            return;
        }
        
        try {
            SaveData saveData = SaveData.fromGameData(gameData, achievementManager, 
                passiveUpgradeManager, "Save " + saveManager.getCurrentSaveSlot());
            
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
     * Ensure the selected save slot is visible by adjusting scroll.
     */
    private void ensureSaveSlotVisible() {
        int startY = 200;
        int slotSpacing = 180;
        int slotHeight = 160;
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
        
        // Create temporary player and boss for the cinematic
        int speedLevel = getActiveSpeedLevel();
        player = new Player(WORLD_WIDTH / 2, WORLD_HEIGHT - 200, speedLevel, keyBindManager, controllerManager);
        currentBoss = new Boss(WORLD_WIDTH / 2, 100, 1, soundManager, gameData.getGameMode());
        bullets.clear();
        particles.clear();
        damageNumbers.clear();
        beamAttacks.clear();
        introParticles.clear();
        
        // Set up boss intro state
        bossIntroActive = true;
        bossIntroTimer = 0;
        bossIntroText = currentBoss.getVehicleName();
        bossIntroPlayerX = WIDTH / 2.0;
        bossIntroBossX = WIDTH + 300;
        bossIntroVsScale = 0;
        bossIntroFlash = 1.0;
        bossIntroPhase = 0;
        bossIntroFlashTimer = 25;
        introPanActive = false;
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
            case "SHIELD": symbol = "◉"; break;
            case "MAGNET": symbol = "⊛"; break;
            case "SLOW_TIME": symbol = "◷"; break;
            case "PHASE": symbol = "◈"; break;
            case "BOMB": symbol = "◆"; break;
            case "STUN": symbol = "⚡"; break;
            case "DOUBLE_DAMAGE": symbol = "×2"; break;
            case "IMPULSE": symbol = "◎"; break;
            case "FROST_BEAM": symbol = "❄"; break;
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
        
        int speedLevel = getActiveSpeedLevel();
        player = new Player(WORLD_WIDTH / 2, WORLD_HEIGHT - 200, speedLevel, keyBindManager, controllerManager);
        bullets.clear();
        particles.clear();
        damageNumbers.clear();
        beamAttacks.clear();
        moneyCircles.clear(); // Clear Pool of Loot circles from previous level
        currentBoss = new Boss(WORLD_WIDTH / 2, 100, gameData.getCurrentLevel(), soundManager, gameData.getGameMode()); // Normal position, will move during intro
        currentBoss.setAllowedPatterns(getAllowedPatternsForLevel(gameData.getCurrentLevel())); // Sync attacks with ATTACK_INTROS
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
        
        // Reset cumulative run stats if starting from level 1
        if (gameData.getCurrentLevel() == 1) {
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
        
        // Ensure missiles are at least the base count (fixes old saves with incorrect values)
        if (gameData.getMissiles() < gameData.getBaseMissiles()) {
            gameData.setMissiles(gameData.getBaseMissiles());
        }
        if (gameData.getBaseMissiles() < 3) {
            gameData.setBaseMissiles(3);
            gameData.setMissiles(Math.max(gameData.getMissiles(), 3));
        }
        
        comboSystem.resetCombo();
        
        // Start boss intro cinematic — only on first encounter with each boss
        int lvl = gameData.getCurrentLevel();
        boolean seenBefore = (lvl >= 1 && lvl <= gameData.getDefeatedBosses().length && gameData.getDefeatedBosses()[lvl - 1]);
        if (!seenBefore) {
            bossIntroActive = true;
            bossIntroTimer = 0;
            introParticles.clear();
            bossIntroText = currentBoss.getVehicleName();
            if (currentBoss.isMegaBoss()) {
                bossIntroText += " [MEGA]";
            }
            bossIntroPlayerX = WIDTH / 2.0; // Start at center (flies up from bottom)
            bossIntroBossX = WIDTH + 300; // Start off-screen right
            bossIntroVsScale = 0;
            bossIntroFlash = 1.0; // Immediate impact flash
            bossIntroPhase = 0;
            bossIntroFlashTimer = 25;
            soundManager.playSound(SoundManager.Sound.BOSS_INTRO);
        } else {
            bossIntroActive = false;
        }
        
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
        
        // Initialize off-screen render buffers (TYPE_INT_RGB — no alpha needed for display)
        renderBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        displayBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        
        while (running) {
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
                
                // Update escape timer
                if (escapeTimer > 0) {
                    escapeTimer -= 1.0;
                    if (escapeTimer < 0) escapeTimer = 0;
                }
                
                // Update scroll cooldown
                if (scrollCooldown > 0) {
                    scrollCooldown -= 1.0;
                }
                
                // Update game timer (only during gameplay)
                if (gameState == GameState.PLAYING && player != null) {
                    gameTimeSeconds = (System.currentTimeMillis() - gameStartTime) / 1000.0;
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
            
            // Controller rebind capture — intercept button presses when rebinding
            if (waitingForKeyBind && gameState == GameState.SETTINGS && selectedSettingsCategory == 4
                    && controllerManager.isConnected()) {
                // Check for B button to cancel rebinding
                if (controllerManager.isJustPressed(KeyBindManager.ControllerButton.B)) {
                    waitingForKeyBind = false;
                    rebindingActionIndex = -1;
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                } else {
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
            return; // Skip update during freeze
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
        
        // Smooth UI number animations
        double scoreTarget = gameData.getScore();
        double moneyTarget = gameData.getTotalMoney() + gameData.getRunMoney();
        displayedScore += (scoreTarget - displayedScore) * 0.15 * deltaTime;
        displayedMoney += (moneyTarget - displayedMoney) * 0.15 * deltaTime;
        
        // Update item unlock animation timer (let it countdown for animation progress)
        if (itemUnlockTimer > 0) {
            itemUnlockTimer -= deltaTime;
        }
        
        // Update dismiss animation
        if (itemUnlockDismissing) {
            itemUnlockDismissTimer -= deltaTime;
            if (itemUnlockDismissTimer <= 0) {
                itemUnlockAnimation = false;
                itemUnlockDismissing = false;
                
                // After item animation ends, check if we should show contract unlock
                // This happens on level 6 (second mega boss)
                if (gameData.getCurrentLevel() == 6 && gameData.areContractsUnlocked() && !contractUnlockAnimation) {
                    soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                    contractUnlockAnimation = true;
                    contractUnlockTimer = CONTRACT_UNLOCK_DURATION;
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
            // poll the filesystem every frame — that caused a death-spiral freeze.
            
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
        
        if (gameState != GameState.PLAYING) return;
        
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
                            new Color(255, 180, 80, 250), 18, (int)(BOMB_EXPLOSION_RADIUS * 1.3),
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
                                new Color(255, 220, 150), 20, 2,
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
        
        // Remove expired circles
        if (MONEY_CIRCLE_DURATION > 0) {
            moneyCircles.removeIf(circle -> circle[2] > MONEY_CIRCLE_DURATION);
        }
        
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
                        gameData.addTotalMoney(MONEY_CIRCLE_BONUS);
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
                                new Color(50, 200, 80), 60, 18, // Green money color, BIGGER size (was 8)
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
        
        // Track survival and score (scaled by delta time) - only when player is alive
        if (player != null) {
            gameData.incrementSurvivalTime();
            
            int scoreGain = (int)deltaTime;
            gameData.addScore(scoreGain);
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
                        
                        // Angle cone widens with level: ~70° / ~90° / ~110°
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
                    
                    // Mark that player has moved at least once
                    if (playerSpeed >= MIN_MOVEMENT_SPEED && !hasMovedOnce) {
                        hasMovedOnce = true;
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
                            particles.add(new Particle(
                                currentBoss.getX() + (Math.random() - 0.5) * 30,
                                currentBoss.getY() + currentBoss.getSize() / 2,
                                Math.cos(angle) * speed,
                                Math.sin(angle) * speed,
                                JET_TRAIL_COLOR,
                                60 + (int)(Math.random() * 30),
                                8.0 + Math.random() * 8.0,
                                Particle.ParticleType.TRAIL
                            ));
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
                        particles.add(new Particle(
                            currentBoss.getX() + (Math.random() - 0.5) * 40,
                            currentBoss.getY() + currentBoss.getSize() / 2,
                            (Math.random() - 0.5) * 0.5,
                            1 + Math.random() * 1.5,
                            ENGINE_GLOW_BLUE,
                            40 + (int)(Math.random() * 20),
                            6.0 + Math.random() * 6.0,
                            Particle.ParticleType.SPARK
                        ));
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
                                particles.add(new Particle(
                                    currentBoss.getX(),
                                    currentBoss.getY(),
                                    Math.cos(angle) * speed,
                                    Math.sin(angle) * speed,
                                    EXPLOSION_WARM,
                                    30 + (int)(Math.random() * 30),
                                    10.0 + Math.random() * 10.0,
                                    Particle.ParticleType.EXPLOSION
                                ));
                            }
                        }
                    }
                    
                    introPanActive = false;
                    cameraX = (WORLD_WIDTH - WIDTH) / 2.0 + CAMERA_HORIZONTAL_OFFSET;
                    cameraY = (WORLD_HEIGHT - HEIGHT) / 2.0;
                }
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
            
            // Update boss intro cinematic — anime shonen sequential reveal
            if (bossIntroActive) {
                bossIntroTimer += deltaTime;
                double t = bossIntroTimer;
                double w = WIDTH;
                int cY = HEIGHT / 2;
                
                // Phase 0: Impact flash (0-30)
                if (t < 30) {
                    bossIntroPlayerX = w / 2.0;
                    bossIntroBossX = w + 300;
                    bossIntroVsScale = 0;
                    bossIntroFlash = Math.max(0, 1.0 - t / 30.0);
                    bossIntroPhase = 0;
                }
                // Phase 1: Player spotlight — flies up from bottom center (30-110)
                else if (t < 110) {
                    double progress = (t - 30) / 80.0;
                    bossIntroPlayerX = w / 2.0;
                    bossIntroBossX = w + 300;
                    bossIntroVsScale = 0;
                    bossIntroFlash = 0;
                    bossIntroPhase = 1;
                    if (t >= 50 && t < 53) {
                        screenShakeIntensity = Math.max(screenShakeIntensity, 10);
                    }
                }
                // Phase 2: Slash transition — player slides to left (110-155)
                else if (t < 155) {
                    double progress = (t - 110) / 45.0;
                    double ease = 1.0 - Math.pow(1.0 - progress, 3);
                    bossIntroPlayerX = w * 0.5 - (w * 0.5 - w * 0.27) * ease;
                    bossIntroBossX = w + 300;
                    bossIntroVsScale = 0;
                    bossIntroFlash = ease; // slash progress 0→1
                    bossIntroPhase = 2;
                    if (t >= 110 && t < 113) {
                        screenShakeIntensity = Math.max(screenShakeIntensity, 12);
                    }
                }
                // Phase 3: Boss reveal — crashes in from top-right (155-250)
                else if (t < 250) {
                    double progress = (t - 155) / 95.0;
                    double ease = 1.0 - Math.pow(1.0 - Math.min(progress * 1.3, 1.0), 3);
                    bossIntroPlayerX = w * 0.27;
                    bossIntroBossX = w + 300 - (w + 300 - w * 0.73) * ease;
                    bossIntroVsScale = 0;
                    bossIntroFlash = 0;
                    bossIntroPhase = 3;
                    if (t >= 155 && t < 158) {
                        screenShakeIntensity = Math.max(screenShakeIntensity, 12);
                    }
                    if (t >= 180 && t < 185) {
                        screenShakeIntensity = Math.max(screenShakeIntensity, 15);
                    }
                }
                // Phase 4: VS clash — both slide toward center (250-320)
                else if (t < 320) {
                    double progress = (t - 250) / 70.0;
                    double slideEase = 1.0 - Math.pow(1.0 - Math.min(progress * 2, 1.0), 3);
                    bossIntroPlayerX = w * 0.27 + (w * 0.35 - w * 0.27) * slideEase;
                    bossIntroBossX = w * 0.73 - (w * 0.73 - w * 0.65) * slideEase;
                    double vsProgress = Math.min(1.0, progress * 2.5);
                    double elasticEase = 1.0 + Math.sin(vsProgress * Math.PI * 2) * 0.15 * (1.0 - vsProgress);
                    bossIntroVsScale = Math.min(1.0, vsProgress * 1.5) * elasticEase;
                    if (t < 255) {
                        bossIntroFlash = Math.max(0, 1.0 - (t - 250) / 5.0);
                    } else {
                        bossIntroFlash = 0;
                    }
                    bossIntroPhase = 4;
                    if (t >= 250 && t < 253) {
                        screenShakeIntensity = Math.max(screenShakeIntensity, 20);
                    }
                }
                // Phase 5: Fade out — sprites fly off screen (320-380)
                else {
                    double progress = Math.min(1.0, (t - 320) / 60.0);
                    // Accelerating fly-off with easeIn (slow start, fast end)
                    double flyEase = progress * progress * progress;
                    bossIntroPlayerX = w * 0.35 - flyEase * (w * 0.35 + 300);
                    bossIntroBossX = w * 0.65 + flyEase * (w * 0.35 + 300);
                    bossIntroVsScale = Math.max(0, 1.0 - progress * 1.5);
                    bossIntroFlash = 0;
                    bossIntroPhase = 5;
                    if (t >= 320 && t < 323) {
                        screenShakeIntensity = Math.max(screenShakeIntensity, 10);
                    }
                }
                
                // Compute Y positions (mirrors Renderer logic so entities stay synced)
                if (bossIntroPhase < 1) bossIntroPlayerY = HEIGHT + 200;
                else if (bossIntroPhase == 1) {
                    double yp = Math.min(1.0, (t - 30) / 60.0);
                    yp = 1.0 - Math.pow(1.0 - yp, 3);
                    bossIntroPlayerY = HEIGHT + 200 + (cY - (HEIGHT + 200)) * yp;
                } else if (bossIntroPhase == 5) {
                    double flyP = Math.min(1.0, (t - 320) / 60.0);
                    double flyEase = flyP * flyP * flyP;
                    bossIntroPlayerY = cY + flyEase * (HEIGHT + 200 - cY);
                } else bossIntroPlayerY = cY;
                
                if (bossIntroPhase < 3) bossIntroBossY = -250;
                else if (bossIntroPhase == 3) {
                    double yp = Math.min(1.0, (t - 155) / 60.0);
                    yp = 1.0 - Math.pow(1.0 - yp, 3);
                    bossIntroBossY = -250 + (cY - (-250)) * yp;
                } else if (bossIntroPhase == 5) {
                    double flyP = Math.min(1.0, (t - 320) / 60.0);
                    double flyEase = flyP * flyP * flyP;
                    bossIntroBossY = cY - flyEase * (cY + 300);
                } else bossIntroBossY = cY;
                
                // Sync entity positions to cinematic positions so sprites track correctly
                if (player != null) player.setPosition(bossIntroPlayerX, bossIntroPlayerY);
                if (currentBoss != null) currentBoss.setPosition(bossIntroBossX, bossIntroBossY);
                
                // Update existing intro particles
                for (int i = introParticles.size() - 1; i >= 0; i--) {
                    Particle p = introParticles.get(i);
                    if (p == null) { introParticles.remove(i); continue; }
                    p.update(deltaTime);
                    if (!p.isAlive()) {
                        introParticles.remove(i);
                    }
                }
                
                // Spawn phase-aware anime particles
                if (enableParticles) {
                    Color[] blueColors = {new Color(100, 200, 255), new Color(60, 160, 255),
                                          new Color(150, 220, 255), new Color(200, 240, 255)};
                    Color[] redColors = {new Color(255, 180, 40), new Color(255, 120, 20),
                                         new Color(255, 200, 60), new Color(255, 80, 10)};
                    Color[] goldColors = {new Color(255, 240, 180), new Color(255, 220, 120),
                                          new Color(255, 255, 200), new Color(255, 200, 80)};
                    
                    // Phase 1: Blue energy burst radiating from player
                    if (bossIntroPhase == 1) {
                        for (int i = 0; i < 4; i++) {
                            double angle = Math.random() * Math.PI * 2;
                            double speed = 2.0 + Math.random() * 3.0;
                            double py = cY + (t < 60 ? (HEIGHT + 200 - (HEIGHT + 200 - cY) * ((t - 30) / 30.0)) - cY : 0);
                            introParticles.add(new Particle(
                                bossIntroPlayerX + (Math.random() - 0.5) * 30, py + (Math.random() - 0.5) * 30,
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                blueColors[(int)(Math.random() * blueColors.length)],
                                15 + (int)(Math.random() * 20), 3 + Math.random() * 5,
                                Particle.ParticleType.EXHAUST
                            ));
                        }
                    }
                    // Phase 2: Slash sparks along diagonal cut line
                    if (bossIntroPhase == 2) {
                        double slashX = w * bossIntroFlash;
                        double slashY = HEIGHT * (1.0 - bossIntroFlash);
                        for (int i = 0; i < 3; i++) {
                            introParticles.add(new Particle(
                                slashX + (Math.random() - 0.5) * 40, slashY + (Math.random() - 0.5) * 40,
                                (Math.random() - 0.5) * 4, (Math.random() - 0.5) * 4,
                                goldColors[(int)(Math.random() * goldColors.length)],
                                10 + (int)(Math.random() * 15), 2 + Math.random() * 4,
                                Particle.ParticleType.EXHAUST
                            ));
                        }
                    }
                    // Phase 3: Red fire particles swirling around boss
                    if (bossIntroPhase == 3 && bossIntroBossX < w) {
                        for (int i = 0; i < 4; i++) {
                            double angle = Math.random() * Math.PI * 2;
                            double speed = 1.5 + Math.random() * 2.5;
                            introParticles.add(new Particle(
                                bossIntroBossX + (Math.random() - 0.5) * 40, cY + (Math.random() - 0.5) * 40,
                                Math.cos(angle) * speed, Math.sin(angle) * speed - 1.0,
                                redColors[(int)(Math.random() * redColors.length)],
                                15 + (int)(Math.random() * 25), 3 + Math.random() * 6,
                                Particle.ParticleType.EXHAUST
                            ));
                        }
                    }
                    // Phase 4: White/gold explosion from VS impact
                    if (bossIntroPhase == 4 && t < 265) {
                        for (int i = 0; i < 6; i++) {
                            double angle = Math.random() * Math.PI * 2;
                            double speed = 3.0 + Math.random() * 5.0;
                            introParticles.add(new Particle(
                                w / 2.0 + (Math.random() - 0.5) * 20, cY + (Math.random() - 0.5) * 20,
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                goldColors[(int)(Math.random() * goldColors.length)],
                                12 + (int)(Math.random() * 18), 2 + Math.random() * 5,
                                Particle.ParticleType.EXHAUST
                            ));
                        }
                    }
                    // Phase 1-4: Exhaust particles behind sprites when visible
                    if (bossIntroPhase >= 1 && bossIntroPhase <= 4) {
                        // Player exhaust
                        double pExAngle = Math.toRadians(90);
                        for (int i = 0; i < 2; i++) {
                            double spread = (Math.random() - 0.5) * 0.8;
                            introParticles.add(new Particle(
                                bossIntroPlayerX + (Math.random() - 0.5) * 12, cY + 50,
                                Math.cos(pExAngle + spread) * 1.5, Math.sin(pExAngle + spread) * 2.0,
                                blueColors[(int)(Math.random() * blueColors.length)],
                                12 + (int)(Math.random() * 15), 3 + Math.random() * 4,
                                Particle.ParticleType.EXHAUST
                            ));
                        }
                        // Boss exhaust (only when on-screen)
                        if (bossIntroPhase >= 3 && bossIntroBossX < w) {
                            for (int i = 0; i < 3; i++) {
                                double spread = (Math.random() - 0.5) * 0.8;
                                introParticles.add(new Particle(
                                    bossIntroBossX + (Math.random() - 0.5) * 14, cY + 55,
                                    Math.cos(pExAngle + spread) * 1.8, Math.sin(pExAngle + spread) * 2.5,
                                    redColors[(int)(Math.random() * redColors.length)],
                                    12 + (int)(Math.random() * 18), 3 + Math.random() * 5,
                                    Particle.ParticleType.EXHAUST
                                ));
                            }
                        }
                    }
                    // Cap intro particles
                    while (introParticles.size() > 250) {
                        introParticles.remove(0);
                    }
                }
                
                if (bossIntroTimer >= BOSS_INTRO_DURATION) {
                    bossIntroActive = false;
                    introParticles.clear();
                    // Reset player and boss to proper gameplay positions after cinematic
                    if (player != null) player.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT - 200);
                    if (currentBoss != null) currentBoss.setPosition(WORLD_WIDTH / 2, 100);
                    if (demoIntroActive) {
                        demoIntroActive = false;
                        transitionToState(GameState.MENU);
                    }
                }
                if (bossIntroActive) return; // Freeze all gameplay while boss intro is playing
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
            
            // Spawn fire trail behind player
            if (player != null && Game.enableParticles) {
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
            if (pSize > 50 && THREAD_COUNT > 1) {
                // Parallel particle position update (particles are independent)
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
                // Trigger wobble effect immediately on hit
                currentBoss.triggerWobble();
                
                soundManager.playSound(SoundManager.Sound.BOSS_HIT);
                
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
                
                // Progressive damage effects - more smoke and fire with each hit
                int particleMultiplier = bossHitCount; // 1x, 2x, 3x particles
                
                // Create impact particles at collision point (between player and boss)
                if (enableParticles) {
                    double impactX = (player.getX() + currentBoss.getX()) / 2;
                    double impactY = (player.getY() + currentBoss.getY()) / 2;
                    
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
                
                // Hit-pause: freeze frames on boss damage (more frames for more hits)
                hitFreezeFrames = 3 + bossHitCount * 2;
                
                // Reset vulnerability
                bossVulnerable = false;
                invulnerabilityTimer = 300; // 5 seconds of invulnerability
                
                screenShakeIntensity = 20 + (bossHitCount * 8); // More shake with each hit
                bossFlashTimer = 12; // Longer boss flash effect
                
                // Check if boss is defeated using new health system
                if (currentBoss.isDead()) {
                    // Roguelike: Track boss defeat for stats
                    gameData.onBossDefeated();
                    
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
                        achievementManager.clearRecentlyUnlocked();
                    }
                    
                    // Award points and money with passive multipliers
                    int winBonus = 1000 + (gameData.getCurrentLevel() * 500);
                    // Apply combo multiplier
                    winBonus = (int)(winBonus * comboSystem.getMultiplier());
                    // Apply score multiplier passive
                    winBonus = (int)(winBonus * passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.MONEY_AND_SCORE));
                    gameData.addScore(winBonus);
                    
                    int moneyReward = currentBoss.getMoneyReward();
                    
                    // Apply money gain passive multiplier
                    moneyReward = (int)(moneyReward * passiveUpgradeManager.getMultiplier(PassiveUpgrade.UpgradeType.MONEY_AND_SCORE));
                    
                    gameData.addRunMoney(moneyReward);
                    gameData.addTotalMoney(moneyReward);
                    
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
                    
                    // Start boss death animation
                    soundManager.playSound(SoundManager.Sound.BOSS_DEATH);
                    bossDeathAnimation = true;
                    deathAnimationTimer = DEATH_ANIMATION_DURATION;
                    bossDeathScale = 1.0;
                    bossDeathRotation = 0;
                    bossKillTime = gameTimeSeconds;
                    
                    // Make player disappear (missile hit)
                    player = null;
                    
                    // Massive final explosion
                    screenShakeIntensity = 25;
                
                // Create massive fiery explosion particles
                int explosionParticleCount = bullets.size() > 200 ? 50 : 100; // Reduce at high bullet density
                for (int i = 0; i < explosionParticleCount; i++) {
                    double angle = Math.random() * TWO_PI;
                    double speed = 3 + Math.random() * 8;
                    Color fireColor;
                    double rand = Math.random();
                    if (rand < 0.4) {
                        fireColor = FIRE_ORANGE;
                    } else if (rand < 0.7) {
                        fireColor = FIRE_YELLOW;
                    } else {
                        fireColor = FIRE_RED;
                    }
                    addParticle(
                        currentBoss.getX(), currentBoss.getY(),
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        fireColor, 50 + (int)(Math.random() * 30), 6,
                        Particle.ParticleType.SPARK
                    );
                }
                
                // Multiple explosion rings
                for (int i = 0; i < 5; i++) {
                    addParticle(
                        currentBoss.getX(), currentBoss.getY(), 0, 0,
                        new Color(255, 150 - i * 20, 0), 40 + i * 15, 40 + i * 25,
                        Particle.ParticleType.EXPLOSION
                    );
                }
                } else {
                    // Non-fatal hit - delay respawn and show explosion
                    double hitX = (player.getX() + currentBoss.getX()) / 2;
                    double hitY = (player.getY() + currentBoss.getY()) / 2;
                    player = null; // Remove player temporarily
                    waitingForRespawn = true;
                    respawnDelayTimer = RESPAWN_DELAY;
                    
                    // Huge screen shake for explosion
                    screenShakeIntensity = 20;
                    
                    // Create explosion at hit location
                    if (enableParticles) {
                        // Large explosion particles
                        for (int i = 0; i < 50; i++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 2 + Math.random() * 6;
                            Color expColor = Math.random() < 0.5 ? FIRE_ORANGE : FIRE_YELLOW;
                            addParticle(
                                hitX, hitY,
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                expColor, 40, 10,
                                Particle.ParticleType.SPARK
                            );
                        }
                        
                        // Explosion rings
                        for (int i = 0; i < 4; i++) {
                            addParticle(
                                hitX, hitY, 0, 0,
                                new Color(255, 150 - i * 30, 50, 220 - i * 50), 
                                30 + i * 10, 
                                30 + i * 15,
                                Particle.ParticleType.EXPLOSION
                            );
                        }
                    }
                    
                    // Reset vulnerability
                    bossVulnerable = false;
                    invulnerabilityTimer = 300; // 5 seconds of invulnerability
                    
                    screenShakeIntensity = 20 + (bossHitCount * 8); // More shake with each hit
                }
                
                return;
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
                particles.add(new Particle(
                    currentBoss.getX() + (Math.random() - 0.5) * 60,
                    currentBoss.getY() + (Math.random() - 0.5) * 60,
                    (Math.random() - 0.5) * 2, 2 + Math.random() * 3,
                    SMOKE_GRAY, 40, 8,
                    Particle.ParticleType.SPARK
                ));
            }
            
            // Final explosion and transition to win screen
            if (deathAnimationTimer <= 0) {
                // Final massive explosion - reduce particle count for performance
                if (enableParticles) {
                    for (int i = 0; i < 50 && particles.size() < MAX_PARTICLES; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double speed = 2 + Math.random() * 6;
                        Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                        particles.add(new Particle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            fireColor, 60, 8,
                            Particle.ParticleType.SPARK
                        ));
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
                gameState = GameState.WIN;
                if (renderer != null) renderer.setScreenEnteredTime(gradientTime);
                bossDeathAnimation = false;
                hasSavedGame = false; // Clear saved game on win so purchases persist
                
                // Auto-save on level completion
                performAutoSave();
                
                // If level 7 was defeated and contracts were unlocked, trigger animation (only once)
                if (currentLevel == 6 && gameData.areContractsUnlocked() && !itemUnlockAnimation && !gameData.hasSeenContractUnlock()) {
                    soundManager.playSound(SoundManager.Sound.CONTRACT_UNLOCK);
                    contractUnlockAnimation = true;
                    contractUnlockTimer = CONTRACT_UNLOCK_DURATION;
                    gameData.setSeenContractUnlock(true);
                }
                
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
        if (currentBoss != null && !bossDeathAnimation && !introPanActive && !bossIntroActive && player != null && !bossStunned) {
            int bulletCountBefore = bullets.size();
            currentBoss.update(bullets, player, WORLD_WIDTH, WORLD_HEIGHT, deltaTime, particles);
            beamAttacks = currentBoss.getBeamAttacks();
            
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
            
            if (bSize > 80 && THREAD_COUNT > 1) {
                // Parallel position update across thread pool
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
                    soundManager.playSound(SoundManager.Sound.GRENADE_EXPLODE, 0.6f);
                } else {
                    SoundManager.Sound[] explosionSounds = {
                        SoundManager.Sound.EXPL_SHORT_1, SoundManager.Sound.EXPL_SHORT_2, 
                        SoundManager.Sound.EXPL_SHORT_3, SoundManager.Sound.EXPL_SHORT_4, 
                        SoundManager.Sound.EXPL_SHORT_5
                    };
                    soundManager.playSound(explosionSounds[(int)(Math.random() * explosionSounds.length)], 0.4f);
                }
                
                // Create explosion particles with shockwave
                if (enableParticles) {
                    List<Particle> explosionParticles = bullet.createExplosionParticles();
                    int particlesToAdd = bullets.size() > 200 ? explosionParticles.size() / 2 : explosionParticles.size();
                    for (int j = 0; j < particlesToAdd && particles.size() < MAX_PARTICLES; j++) {
                        particles.add(explosionParticles.get(j));
                    }
                }
                
                // Collect fragments to add after loop
                List<Bullet> fragments = bullet.createFragments();
                if (!fragments.isEmpty()) {
                    if (newFragments == null) newFragments = new ArrayList<>();
                    newFragments.addAll(fragments);
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
                    soundManager.playSound(SoundManager.Sound.SHIELD_BREAK);
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
                                new Color(136, 192, 208), 30, 8,
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
                double grazeRadius = GRAZE_DISTANCE;
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
                        soundManager.playSound(SoundManager.Sound.PERFECT_DODGE, 1.2f);
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
                        soundManager.playSound(SoundManager.Sound.CLOSE_CALL, 0.9f);
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
                                    new Color(255, 255, 200, 200), 25, 3,
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
        if (bulletPool.size() < 500) { // Cap pool size
            bulletPool.add(bullet);
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
        if (particlePool.size() < 300) { // Cap pool size
            particlePool.add(particle);
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
        // Lightweight blit: just draw the pre-rendered buffer to screen
        // All heavy rendering was done on the game thread in renderToBuffer()
        BufferedImage buf;
        synchronized (bufferSwapLock) {
            buf = displayBuffer;
        }
        if (buf != null) {
            Graphics2D g2d = (Graphics2D) g;
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            
            // Calculate scaling to fit window
            double scaleX = (double) panelWidth / WIDTH;
            double scaleY = (double) panelHeight / HEIGHT;
            double scale = Math.min(scaleX, scaleY);
            int scaledWidth = (int) (WIDTH * scale);
            int scaledHeight = (int) (HEIGHT * scale);
            int offsetX = (panelWidth - scaledWidth) / 2;
            int offsetY = (panelHeight - scaledHeight) / 2;
            
            // Fill letterbox areas
            if (offsetX > 0 || offsetY > 0) {
                g2d.setColor(Color.BLACK);
                g2d.fillRect(0, 0, panelWidth, panelHeight);
            }
            
            // Draw the pre-rendered buffer scaled to fit
            g2d.drawImage(buf, offsetX, offsetY, scaledWidth, scaledHeight, null);
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
            case MENU:
                renderer.drawMenu(g2d, WIDTH, HEIGHT, gradientTime, escapeTimer, selectedMenuItem, saveManager.getCurrentSaveSlot(), gameData.getGameMode());
                break;
            case INFO:
                renderer.drawInfo(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case ACHIEVEMENTS:
                renderer.drawAchievements(g2d, WIDTH, HEIGHT, gradientTime, achievementManager, achievementsScrollAnimated);
                break;
            case STATS:
                renderer.drawStats(g2d, WIDTH, HEIGHT, gradientTime, passiveUpgradeManager);
                renderer.drawStatsUpgrades(g2d, WIDTH, selectedStatItem, passiveUpgradeManager, statsScrollAnimated);
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
                    g2d.setColor(new Color(0, 0, 0, (int)(edgeDarkness * 255)));
                    g2d.fillRect(0, 0, WIDTH, HEIGHT);
                }
                
                g2d.translate(WIDTH / 2, HEIGHT / 2);
                g2d.scale(totalZoom, totalZoom);
                g2d.translate(-WIDTH / 2, -HEIGHT / 2);
                
                // Apply screen shake
                g2d.translate(screenShakeX, screenShakeY);
                renderer.drawGame(g2d, WIDTH, HEIGHT, player, currentBoss, bullets, particles, beamAttacks, gameData.getCurrentLevel(), gradientTime, bossVulnerable, invulnerabilityTimer, dodgeCombo, comboTimer > 0, bossDeathAnimation, bossDeathScale, bossDeathRotation, gameTimeSeconds, currentFPS, shieldActive, playerInvincible, bossHitCount, cameraX, cameraY, introPanActive, bossFlashTimer, screenFlashTimer, comboSystem, damageNumbers, bossIntroActive, bossIntroText, bossIntroTimer, isPaused, selectedPauseItem, pendingAchievements, achievementNotificationTimer, deathSequenceActive, playerHidden, respawnBlinkTimer, riskContractType, riskContractActive, stoppedMovingTimer, unpauseCountdownActive, unpauseCountdownTimer, itemReadyFlickerTimer, itemCompleteFlashTimer, achievementFlashTimer, bossIntroFlashTimer, countdownFlashTimer, bossHitFlashTimer, typePurgeFlashTimer, typePurgeFlashColor, moneyCircles, MONEY_CIRCLE_RADIUS, frostBeamAngle, frostBeamProgress, frostBeamStopDistance, frostBeamRetracting, frostBeamRetractPhase, shieldHits, shieldOrbitAngle, bossIntroPlayerX, bossIntroBossX, bossIntroVsScale, bossIntroFlash, bossIntroPhase, introParticles, deathFlashTimer);
                
                // Draw boss stun effect
                if (bossStunned && currentBoss != null) {
                    drawBossStunEffect(g2d, cameraX, cameraY, gradientTime);
                }
                
                // Restore original transform (removes both shake and zoom)
                g2d.setTransform(originalTransform);
                break;
            case LOADING:
                // Draw loading screen directly (renderer not yet created)
                drawSimpleLoading(g2d, WIDTH, HEIGHT, loadingProgress);
                break;
            case GAME_OVER:
                renderer.drawGameOver(g2d, WIDTH, HEIGHT, gradientTime);
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
                break;
            case SHOP:
                renderer.drawShop(g2d, WIDTH, HEIGHT, gradientTime, shopScrollAnimated);
                break;
            case DEBUG:
                renderer.drawDebug(g2d, WIDTH, HEIGHT, gradientTime);
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
        String wavPath = path.replace(".mp3", ".wav");
        for (String track : CHILL_MENU_TRACKS) {
            if (track.replace(".mp3", ".wav").equals(wavPath)) return true;
        }
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
               state == GameState.MODE_SELECT ||
               state == GameState.DEBUG || state == GameState.LEVEL_CONFIRM ||
               state == GameState.ATTACK_SHOWCASE || state == GameState.ATTACK_INTRO;
    }
    
    // Helper method to transition to a new state
    private void transitionToState(GameState newState) {
        if (gameState != newState) {
            // Handle music transitions
            if (isMenuState(newState)) {
                // Only start menu music if not already playing a menu track
                String currentTrack = soundManager.getCurrentMusic();
                if (!isMenuTrack(currentTrack)) {
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
            
            // Reset shop scroll when entering shop
            if (newState == GameState.SHOP) {
                shopScroll = 0;
                shopScrollAnimated = 0;
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
        int itemsVisible = 7; // Number of items visible on screen
        
        // Only scroll if selection is beyond visible area
        if (selectedItem > itemsVisible - 3) {
            shopScroll = (selectedItem - (itemsVisible - 3)) * 80; // 80 pixels per item
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
        // Category 0: Graphics (17 settings)
        // Category 1: Audio (5 settings)
        // Category 2: Gameplay (1 setting)
        // Category 3: Debug (2 settings)
        // Category 4: Controls (10 settings)
        
        if (selectedSettingsCategory == 0) {
            // Graphics settings (reorganized: Display, Quality, Background, Effects, Camera)
            switch (settingIndex) {
                case 0: toggleFullscreen(); break;
                case 1: resolutionPreset = (resolutionPreset + 1) % 6; break;
                case 2: enableVSync = !enableVSync; break;
                case 3: fpsLimit = (fpsLimit + 1) % 5; updateFPSLimit(); break;
                case 4: enableAntiAliasing = !enableAntiAliasing; break;
                case 5: shadowQuality = (shadowQuality + 1) % 4; enableShadows = shadowQuality > 0; break;
                case 6: enableParticles = !enableParticles; break;
                case 7: enableBloom = !enableBloom; break;
                case 8: backgroundMode = (backgroundMode + 1) % 3; break;
                case 9: enableGradientAnimation = !enableGradientAnimation; break;
                case 10: gradientQuality = (gradientQuality + 1) % 3; break;
                case 11: enableMotionBlur = !enableMotionBlur; break;
                case 12: enableChromaticAberration = !enableChromaticAberration; break;
                case 13: enableVignette = !enableVignette; break;
                case 14: enableGrainEffect = !enableGrainEffect; break;
                case 15: /* Camera Zoom - handled by adjustSetting */ break;
                case 16: enableUIParallax = !enableUIParallax; break;
            }
        } else if (selectedSettingsCategory == 1) {
            // Audio settings
            if (settingIndex == 0) {
                gameData.setSoundEnabled(!gameData.isSoundEnabled());
                soundManager.setSoundEnabled(gameData.isSoundEnabled());
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
            } else if (settingIndex >= 2 && settingIndex <= 10) {
                // Action rebinding - enter rebind mode
                waitingForKeyBind = true;
                rebindingActionIndex = settingIndex - 1; // Maps to Action ordinal + 1 offset
            }
        }
    }
    
    private boolean adjustSetting(int settingIndex, int direction) {
        // Graphics sliders (reorganized: Display, Quality, Background, Effects, Camera)
        if (selectedSettingsCategory == 0) {
            if (settingIndex == 0) { // Fullscreen (toggle)
                toggleFullscreen();
                return true;
            } else if (settingIndex == 1) { // Resolution Preset
                resolutionPreset = Math.max(0, Math.min(5, resolutionPreset + direction));
                return true;
            } else if (settingIndex == 2) { // VSync (toggle)
                enableVSync = !enableVSync;
                return true;
            } else if (settingIndex == 3) { // FPS Limit
                fpsLimit = Math.max(0, Math.min(4, fpsLimit + direction));
                updateFPSLimit();
                return true;
            } else if (settingIndex == 4) { // Anti-Aliasing (toggle)
                enableAntiAliasing = !enableAntiAliasing;
                return true;
            } else if (settingIndex == 5) { // Shadow Quality (slider)
                shadowQuality = Math.max(0, Math.min(3, shadowQuality + direction));
                enableShadows = shadowQuality > 0;
                return true;
            } else if (settingIndex == 6) { // Particle Effects (toggle)
                enableParticles = !enableParticles;
                return true;
            } else if (settingIndex == 7) { // Bloom (toggle)
                enableBloom = !enableBloom;
                return true;
            } else if (settingIndex == 8) { // Background Mode
                backgroundMode = (backgroundMode + direction + 3) % 3;
                return true;
            } else if (settingIndex == 9) { // Gradient Animation (toggle)
                enableGradientAnimation = !enableGradientAnimation;
                return true;
            } else if (settingIndex == 10) { // Gradient Quality
                gradientQuality = Math.max(0, Math.min(2, gradientQuality + direction));
                return true;
            } else if (settingIndex == 11) { // Motion Blur (toggle)
                enableMotionBlur = !enableMotionBlur;
                return true;
            } else if (settingIndex == 12) { // Chromatic Aberration (toggle)
                enableChromaticAberration = !enableChromaticAberration;
                return true;
            } else if (settingIndex == 13) { // Vignette (toggle)
                enableVignette = !enableVignette;
                return true;
            } else if (settingIndex == 14) { // Grain Effect (toggle)
                enableGrainEffect = !enableGrainEffect;
                return true;
            } else if (settingIndex == 15) { // Camera Zoom
                double step = 0.05 * direction;
                cameraZoom = Math.max(0.75, Math.min(1.5, cameraZoom + step));
                return true;
            } else if (settingIndex == 16) { // UI Parallax (toggle)
                enableUIParallax = !enableUIParallax;
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
            } else if (settingIndex >= 2 && settingIndex <= 10) {
                // Action rebinding - enter rebind mode on left/right press too
                waitingForKeyBind = true;
                rebindingActionIndex = settingIndex - 1;
                return true;
            }
        }
        // Setting not adjustable with left/right
        return false;
    }
    
    /** Set a graphics setting directly by pill option index */
    private void setGraphicsPillValue(int settingIndex, int pillIndex) {
        switch (settingIndex) {
            case 0: // Fullscreen: 0=Windowed, 1=Fullscreen
                boolean wantFull = pillIndex == 1;
                if (wantFull != isFullscreen) toggleFullscreen();
                break;
            case 1: // Resolution
                resolutionPreset = Math.max(0, Math.min(5, pillIndex));
                break;
            case 3: // FPS Limit
                fpsLimit = Math.max(0, Math.min(4, pillIndex));
                updateFPSLimit();
                break;
            case 5: // Shadow Quality
                shadowQuality = Math.max(0, Math.min(3, pillIndex));
                enableShadows = shadowQuality > 0;
                break;
            case 8: // Background Mode
                backgroundMode = Math.max(0, Math.min(2, pillIndex));
                break;
            case 10: // Gradient Quality
                gradientQuality = Math.max(0, Math.min(2, pillIndex));
                break;
        }
    }
    
    private int getMaxSettingsItems() {
        if (selectedSettingsCategory == 0) return 16; // Graphics: 17 items (0-16)
        if (selectedSettingsCategory == 1) return 4; // Audio: 5 items (0-4)
        if (selectedSettingsCategory == 2) return 0; // Gameplay: 1 item (0)
        if (selectedSettingsCategory == 3) return 1; // Debug: 2 items (0-1)
        if (selectedSettingsCategory == 4) return 10; // Controls: 11 items (0-10) - Preset, Input Device, 9 actions
        if (selectedSettingsCategory == 5) return -1; // HUD: no list items, editor handles interaction
        return 0;
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
        // Don't reset fullscreen - that's a user preference
        
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
        
        switch (gameState) {
            case MENU:
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
                        // Reset settings to defaults with Y button
                        resetSettingsToDefaults();
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                        screenShakeIntensity = 5;
                    }
                }
                break;
                
            case PLAYING:
                if (isPaused) {
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
                        renderer.configurePauseMenu(debugShowcaseInGameplay);
                        screenShakeIntensity = 3;
                    }
                    // Use item with A button
                    if (controllerManager.isActionJustPressed(KeyBindManager.Action.USE_ITEM) && !eKeyPressed && !introPanActive && !bossIntroActive) {
                        eKeyPressed = true;
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
                        } else if (bossIntroActive) {
                            bossIntroActive = false;
                            screenShakeIntensity = 5;
                            if (player != null) player.setPosition(WORLD_WIDTH / 2, WORLD_HEIGHT - 200);
                            if (currentBoss != null) currentBoss.setPosition(WORLD_WIDTH / 2, 100);
                            introParticles.clear();
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
                
            case WIN:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
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
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
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
    
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (window instanceof javax.swing.JFrame) {
            javax.swing.JFrame frame = (javax.swing.JFrame) window;
            
            // Get the graphics device for the screen where the window is currently located
            java.awt.GraphicsConfiguration gc = frame.getGraphicsConfiguration();
            java.awt.GraphicsDevice device = gc.getDevice();
            java.awt.Rectangle screenBounds = gc.getBounds();
            
            if (isFullscreen) {
                // Switch to fullscreen on the current monitor
                frame.dispose();
                frame.setUndecorated(true);
                frame.setVisible(true);
                
                // Set bounds to match the current screen exactly
                frame.setBounds(screenBounds);
                device.setFullScreenWindow(frame);
            } else {
                // Switch to windowed - use current screen size and make 80% of it
                device.setFullScreenWindow(null);
                frame.dispose();
                frame.setExtendedState(javax.swing.JFrame.NORMAL);
                frame.setUndecorated(false);
                
                int windowWidth = (int)(screenBounds.width * 0.8);
                int windowHeight = (int)(screenBounds.height * 0.8);
                
                // Center on the current monitor
                int windowX = screenBounds.x + (screenBounds.width - windowWidth) / 2;
                int windowY = screenBounds.y + (screenBounds.height - windowHeight) / 2;
                
                frame.setBounds(windowX, windowY, windowWidth, windowHeight);
                frame.setVisible(true);
            }
            
            // Request focus back to game
            this.requestFocusInWindow();
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
        // Check shop upgrades
        if (gameData.getSpeedUpgradeLevel() > 0) return false;
        if (gameData.getBulletSlowUpgradeLevel() > 0) return false;
        if (gameData.getLuckyDodgeUpgradeLevel() > 0) return false;
        if (gameData.getAttackWindowUpgradeLevel() > 0) return false;
        
        // Check passive upgrades
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
                targetLoadingProgress = 90;
                repaint();

                // --- Phase 3: Finalize ---
                Thread.sleep(200);
                targetLoadingProgress = 100;
                repaint();

                Thread.sleep(300);
                loadingComplete = true;
                transitionToState(GameState.SAVE_SELECT);
                repaint();

            } catch (Exception e) {
                e.printStackTrace();
                // On error, still go to save selection
                loadingComplete = true;
                transitionToState(GameState.SAVE_SELECT);
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
        
        // ── Military themed background ──────────────────────────────────
        UITheme.drawScreenBackground(g, width, height, time);
        
        // ── Title — stencil-style with ember particles ───────────────────
        UITheme.drawTitle(g, "MR. MISSLE", width, height / 2 - 120,
            ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED,
            time, FontPalette.TITLE_LARGE);
        
        // ── Loading stage label ──────────────────────────────────────────
        String stageText;
        if (smoothProgress < 30)       stageText = "LOADING AUDIO SYSTEMS";
        else if (smoothProgress < 45)  stageText = "SCANNING HOSTILE AIRCRAFT";
        else if (smoothProgress < 55)  stageText = "LOADING MUNITIONS";
        else if (smoothProgress < 65)  stageText = "CALIBRATING FLIGHT CONTROLS";
        else if (smoothProgress < 90)  stageText = "PAINTING COCKPIT VIEW";
        else                           stageText = "SYSTEMS ARMED — READY";
        
        // Animated dots
        int dotCount = (int)((System.currentTimeMillis() / 350) % 4);
        String dots = ".".repeat(dotCount);
        
        g.setFont(FontPalette.MEDIUM);
        FontMetrics fm = g.getFontMetrics();
        
        // Stage text with orange accent
        g.setColor(ColorPalette.ACCENT_ORANGE);
        String fullStageText = stageText + dots;
        g.drawString(fullStageText, (width - fm.stringWidth(stageText + "...")) / 2, height / 2 + 10);
        
        // ── Missile-arming gauge progress bar (1.5x wider) ───────────────
        int barWidth = Math.min(750, (int)((width - 200) * 1.5));
        int barHeight = 28;
        int barX = (width - barWidth) / 2;
        int barY = height / 2 + 45;
        UITheme.drawProgressBar(g, barX, barY, barWidth, barHeight,
            smoothProgress / 100.0, ColorPalette.ACCENT_ORANGE);
        
        // ── Version tag in bottom-right corner ───────────────────────────
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
                                    particles.add(new Particle(
                                        bullet.getX(), bullet.getY(),
                                        (Math.random() - 0.5) * 4,
                                        (Math.random() - 0.5) * 4,
                                        typePurgeFlashColor, 15, 20,
                                        Particle.ParticleType.SPARK
                                    ));
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
                                    new Color(136, 192, 208), 20, 3, // Ice blue color
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
        g2d.setColor(new Color(255, 255, 200));
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
        g.setColor(new Color(136, 192, 208));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, boxWidth, boxHeight, 10, 10);
        
        // Draw save icon (floppy disk shape)
        int iconX = x + 15;
        int iconY = y + 10;
        int iconSize = 30;
        g.setColor(new Color(136, 192, 208));
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

        // "NEW ATTACK!" header — using stencil title style
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
        String levelText = "Level " + (gameData != null ? gameData.getCurrentLevel() : "?");
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

                // START button — military style
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

    /** Draw an arrow box for the showcase carousel — military style */
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

        // Tab background — metallic gradient
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
        g2d.setTransform(new java.awt.geom.AffineTransform());
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
        
        // Multiple glow layers for more impact
        for (int i = 0; i < 3; i++) {
            int glowSize = Math.max(1, (int)((500 + i * 100) * scale)); // Ensure radius is at least 1
            float pulseSpeed = 2.0f + i * 0.5f;
            float pulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 200.0 * pulseSpeed)) * 0.3f + 0.7f;
            
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, currentY,
                glowSize,
                new float[]{0.0f, 0.6f, 1.0f},
                new Color[]{
                    new Color(235, 203, 139, (int)(80 * scale * pulse * dismissMultiplier)),
                    new Color(163, 190, 140, (int)(40 * scale * pulse * dismissMultiplier)),
                    ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 0)
                }
            );
            g.setPaint(glowPaint);
            g.fillOval(centerX - glowSize, currentY - glowSize, glowSize * 2, glowSize * 2);
        }
        
        // Animated particles around the box
        if (progress > 0.3f && enableParticles) {
            int particleCount = 30;
            for (int i = 0; i < particleCount; i++) {
                double angle = (System.currentTimeMillis() / 125.0 + i * (360.0 / particleCount)) * Math.PI / 180.0;
                int radius = (int)(200 * scale);
                int px = (int)(centerX + Math.cos(angle) * radius);
                int py = (int)(currentY + Math.sin(angle) * radius * 0.7);
                int size = (int)(6 * scale);
                
                float particleAlpha = (float)Math.abs(Math.sin(angle * 3 + System.currentTimeMillis() / 250.0));
                g.setColor(new Color(235, 203, 139, (int)(200 * particleAlpha * scale * dismissMultiplier)));
                g.fillOval(px - size/2, py - size/2, size, size);
            }
        }
        
        // Draw box with better styling
        int boxWidth = (int)(700 * scale);
        int boxHeight = (int)(280 * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = currentY - boxHeight / 2;
        
        // Box shadow
        g.setColor(new Color(0, 0, 0, (int)(100 * Math.min(progress * 2, 1.0f))));
        g.fillRoundRect(boxX + 5, boxY + 5, boxWidth, boxHeight, 25, 25);
        
        // Box background with gradient
        GradientPaint boxGradient = new GradientPaint(
            boxX, boxY, new Color(40, 40, 50, (int)(240 * Math.min(progress * 2, 1.0f))),
            boxX, boxY + boxHeight, new Color(25, 25, 35, (int)(240 * Math.min(progress * 2, 1.0f)))
        );
        g.setPaint(boxGradient);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 25, 25);
        
        // Animated border with rainbow glow
        float borderPulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 400.0));
        int borderR = (int)(163 + (235 - 163) * borderPulse);
        int borderG = (int)(190 + (203 - 190) * borderPulse);
        int borderB = (int)(140 + (139 - 140) * borderPulse);
        g.setColor(new Color(borderR, borderG, borderB, (int)(255 * Math.min(progress * 2, 1.0f))));
        g.setStroke(new BasicStroke(5));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 25, 25);
        
        // Inner glow border
        g.setColor(new Color(255, 255, 255, (int)(100 * borderPulse * Math.min(progress * 2, 1.0f))));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(boxX + 8, boxY + 8, boxWidth - 16, boxHeight - 16, 20, 20);
        
        // Text content
        if (progress > 0.25f) {
            float textAlpha = Math.min((progress - 0.25f) / 0.3f, 1.0f) * dismissMultiplier;
            
            // "NEW ITEM UNLOCKED!" with shadow
            g.setFont(FontPalette.getDisplay(Font.BOLD, (int)(56 * scale)));
            String titleText = "NEW ITEM UNLOCKED!";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = centerX - titleFm.stringWidth(titleText) / 2;
            int titleY = currentY - (int)(80 * scale);
            
            // Title shadow
            g.setColor(new Color(0, 0, 0, (int)(150 * textAlpha)));
            g.drawString(titleText, titleX + 2, titleY + 2);
            
            // Title text with pulse
            float titlePulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 500.0)) * 0.3f + 0.7f;
            g.setColor(new Color(235, 203, 139, (int)(255 * textAlpha * titlePulse)));
            g.drawString(titleText, titleX, titleY);
            
            // Item name with shadow
            g.setFont(FontPalette.get(Font.BOLD, (int)(44 * scale)));
            FontMetrics itemFm = g.getFontMetrics();
            int itemX = centerX - itemFm.stringWidth(unlockedItemName) / 2;
            int itemY = currentY - (int)(10 * scale);
            
            g.setColor(new Color(0, 0, 0, (int)(150 * textAlpha)));
            g.drawString(unlockedItemName, itemX + 2, itemY + 2);
            
            g.setColor(new Color(163, 190, 140, (int)(255 * textAlpha)));
            g.drawString(unlockedItemName, itemX, itemY);
            
            // Item description
            if (unlockedItemDescription != null && !unlockedItemDescription.isEmpty() && progress > 0.4f) {
                g.setFont(FontPalette.get(Font.PLAIN, (int)(24 * scale)));
                String description = unlockedItemDescription;
                FontMetrics descFm = g.getFontMetrics();
                int descX = centerX - descFm.stringWidth(description) / 2;
                int descY = currentY + (int)(50 * scale);
                
                g.setColor(new Color(200, 200, 200, (int)(220 * textAlpha)));
                g.drawString(description, descX, descY);
            }
            
            // "Press SPACE to continue" hint (or buttons for equip prompt)
            if (progress > 0.8f) {
                if (showEquipPrompt && itemUnlockTimer == 0) {
                    System.out.println("DEBUG: Drawing equip prompt buttons");
                    // Draw equip buttons
                    g.setFont(FontPalette.get(Font.PLAIN, (int)(20 * scale)));
                    String promptText = "Equip this item?";
                    FontMetrics promptFm = g.getFontMetrics();
                    int promptX = centerX - promptFm.stringWidth(promptText) / 2;
                    int promptY = currentY + (int)(70 * scale);
                    
                    g.setColor(new Color(200, 200, 200, (int)(220 * textAlpha)));
                    g.drawString(promptText, promptX, promptY);
                    
                    // Position and draw buttons relative to animation box
                    int buttonWidth = 200;
                    int buttonHeight = 60;
                    int buttonY = currentY + (int)(110 * scale);
                    int spacing = 30;
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
                    System.out.println("DEBUG: Drawing continue hint - showEquipPrompt=" + showEquipPrompt + ", timer=" + itemUnlockTimer);
                    g.setFont(FontPalette.get(Font.PLAIN, (int)(20 * scale)));
                    String hintText = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to continue";
                    FontMetrics hintFm = g.getFontMetrics();
                    int hintX = centerX - hintFm.stringWidth(hintText) / 2;
                    int hintY = currentY + (int)(100 * scale);
                    
                    float hintPulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 500.0));
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
        g2d.setTransform(new java.awt.geom.AffineTransform());
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
        
        // Red/orange glow layers for contracts (danger theme)
        for (int i = 0; i < 3; i++) {
            int glowSize = Math.max(1, (int)((550 + i * 120) * scale));
            float pulseSpeed = 2.0f + i * 0.5f;
            float pulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 500.0 * pulseSpeed)) * 0.3f + 0.7f;
            
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, currentY,
                glowSize,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{
                    new Color(255, 100, 50, (int)(60 * scale * pulse * dismissMultiplier)),
                    new Color(200, 50, 50, (int)(30 * scale * pulse * dismissMultiplier)),
                    new Color(150, 50, 50, 0)
                }
            );
            g.setPaint(glowPaint);
            g.fillOval(centerX - glowSize, currentY - glowSize, glowSize * 2, glowSize * 2);
        }
        
        // Animated danger particles
        if (progress > 0.3f && enableParticles) {
            int particleCount = 40;
            for (int i = 0; i < particleCount; i++) {
                double angle = (System.currentTimeMillis() / 100.0 + i * (360.0 / particleCount)) * Math.PI / 180.0;
                int radius = (int)(220 * scale);
                int px = (int)(centerX + Math.cos(angle) * radius);
                int py = (int)(currentY + Math.sin(angle) * radius * 0.7);
                int size = (int)(5 * scale);
                
                float particleAlpha = (float)Math.abs(Math.sin(angle * 4 + System.currentTimeMillis() / 200.0));
                g.setColor(new Color(255, 150, 100, (int)(180 * particleAlpha * scale * dismissMultiplier)));
                g.fillOval(px - size/2, py - size/2, size, size);
            }
        }
        
        // Draw box with danger styling
        int boxWidth = (int)(750 * scale);
        int boxHeight = (int)(380 * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = currentY - boxHeight / 2;
        
        // Box shadow
        g.setColor(new Color(0, 0, 0, (int)(120 * Math.min(progress * 2, 1.0f))));
        g.fillRoundRect(boxX + 6, boxY + 6, boxWidth, boxHeight, 25, 25);
        
        // Box background with dark red gradient
        GradientPaint boxGradient = new GradientPaint(
            boxX, boxY, new Color(50, 25, 30, (int)(245 * Math.min(progress * 2, 1.0f))),
            boxX, boxY + boxHeight, new Color(30, 15, 20, (int)(245 * Math.min(progress * 2, 1.0f)))
        );
        g.setPaint(boxGradient);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 25, 25);
        
        // Animated border with pulsing red/orange
        float borderPulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 300.0));
        int borderR = (int)(200 + 55 * borderPulse);
        int borderG = (int)(80 + 70 * borderPulse);
        int borderB = (int)(50 + 50 * borderPulse);
        g.setColor(new Color(borderR, borderG, borderB, (int)(255 * Math.min(progress * 2, 1.0f))));
        g.setStroke(new BasicStroke(5));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 25, 25);
        
        // Inner warning stripes (diagonal lines at top)
        if (progress > 0.2f) {
            float stripeAlpha = Math.min((progress - 0.2f) / 0.2f, 1.0f) * dismissMultiplier;
            g.setClip(boxX + 10, boxY + 10, boxWidth - 20, 30);
            g.setColor(new Color(255, 200, 0, (int)(100 * stripeAlpha)));
            for (int i = -10; i < boxWidth + 30; i += 20) {
                g.fillPolygon(
                    new int[]{boxX + i, boxX + i + 15, boxX + i + 25, boxX + i + 10},
                    new int[]{boxY + 10, boxY + 10, boxY + 40, boxY + 40},
                    4
                );
            }
            g.setClip(null);
        }
        
        // Text content
        if (progress > 0.25f) {
            float textAlpha = Math.min((progress - 0.25f) / 0.3f, 1.0f) * dismissMultiplier;
            
            // "RISK CONTRACTS UNLOCKED!" with shadow
            g.setFont(FontPalette.get(Font.BOLD, (int)(48 * scale)));
            String titleText = "RISK CONTRACTS UNLOCKED!";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = centerX - titleFm.stringWidth(titleText) / 2;
            int titleY = currentY - (int)(120 * scale);
            
            // Title shadow
            g.setColor(new Color(0, 0, 0, (int)(180 * textAlpha)));
            g.drawString(titleText, titleX + 3, titleY + 3);
            
            // Title text with danger pulse
            float titlePulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 400.0)) * 0.3f + 0.7f;
            g.setColor(new Color(255, 150, 100, (int)(255 * textAlpha * titlePulse)));
            g.drawString(titleText, titleX, titleY);
        }
        
        // Description section
        if (progress > 0.4f) {
            float descAlpha = Math.min((progress - 0.4f) / 0.3f, 1.0f) * dismissMultiplier;
            
            // Contract symbol
            g.setFont(FontPalette.get(Font.BOLD, (int)(60 * scale)));
            String symbol = "âš ";
            FontMetrics symbolFm = g.getFontMetrics();
            g.setColor(new Color(255, 200, 50, (int)(255 * descAlpha)));
            g.drawString(symbol, centerX - symbolFm.stringWidth(symbol) / 2, currentY - (int)(50 * scale));
            
            // Description lines
            String[] descLines = {
                "Choose a RISK CONTRACT before each level",
                "to multiply your rewards!",
                "",
                "â€¢ Bullet Storm - 2x bullets, 2x money",
                "â€¢ Speed Demon - Faster bullets, 1.75x money", 
                "• Shieldless - No active items, 1.5x money"
            };
            
            g.setFont(FontPalette.get(Font.PLAIN, (int)(20 * scale)));
            int lineY = currentY + (int)(10 * scale);
            for (String line : descLines) {
                if (line.isEmpty()) {
                    lineY += (int)(10 * scale);
                    continue;
                }
                FontMetrics lineFm = g.getFontMetrics();
                int lineX = centerX - lineFm.stringWidth(line) / 2;
                
                // Different colors for bullet points
                if (line.startsWith("â€¢")) {
                    g.setColor(new Color(255, 200, 150, (int)(220 * descAlpha)));
                } else {
                    g.setColor(new Color(200, 200, 200, (int)(220 * descAlpha)));
                }
                g.drawString(line, lineX, lineY);
                lineY += (int)(26 * scale);
            }
        }
        
        // "Press SPACE to continue" hint
        if (progress > 0.7f) {
            float hintAlpha = Math.min((progress - 0.7f) / 0.2f, 1.0f) * dismissMultiplier;
            g.setFont(FontPalette.get(Font.PLAIN, (int)(18 * scale)));
            String hintText = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to continue";
            FontMetrics hintFm = g.getFontMetrics();
            int hintX = centerX - hintFm.stringWidth(hintText) / 2;
            int hintY = currentY + (int)(160 * scale);
            
            float hintPulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 500.0));
            g.setColor(new Color(180, 180, 180, (int)(200 * hintPulse * hintAlpha)));
            g.drawString(hintText, hintX, hintY);
        }
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
}
