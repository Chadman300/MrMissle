import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Boss {
    
    /** Factory interface for creating bullets through the pool system instead of 'new Bullet()'. */
    @FunctionalInterface
    public interface BulletFactory {
        Bullet create(double x, double y, double vx, double vy, Bullet.BulletType type);
    }
    
    private BulletFactory bulletFactory;
    
    /** Set the bullet factory (should use Game's pool). Falls back to 'new Bullet()' if null. */
    public void setBulletFactory(BulletFactory factory) {
        this.bulletFactory = factory;
    }
    
    /** Create a bullet through the factory (pooled) or fallback to new. */
    private Bullet createBullet(double x, double y, double vx, double vy, Bullet.BulletType type) {
        if (bulletFactory != null) {
            return bulletFactory.create(x, y, vx, vy, type);
        }
        return new Bullet(x, y, vx, vy, type);
    }
    
    private double x, y;
    private double vx, vy; // Velocity
    private double ax, ay; // Acceleration
    private double rotation; // Current rotation angle
    private double targetRotation; // Target rotation angle
    private double angularVelocity; // Current rotation speed
    
    // Wobble effect when hit by player missile
    private double wobbleRotation; // Z-axis rotation wobble angle
    private double wobbleVelocity; // Wobble rotation velocity
    private static final double WOBBLE_STRENGTH = 0.3; // Initial wobble angle - smaller
    private static final double WOBBLE_DAMPING = 0.95; // Damping factor - very high for long wobble duration
    private static final double WOBBLE_SPRING = 0.015; // Spring force - very low for slower oscillation
    
    // Twirl effect (360-degree rotation)
    private double twirlRotation; // Current twirl rotation (0 to 2*PI)
    private boolean twirlActive; // Whether twirl is currently happening
    private static final double TWIRL_SPEED = 0.05; // Rotation speed per frame - slower
    
    // Twirl attack pattern (assault phase)
    private boolean twirlAttackActive = false; // Whether doing a twirl attack sequence
    private int twirlAttackCount = 0; // Number of twirls completed in current attack
    private int twirlAttackMaxCount = 2; // Number of twirls per attack
    private double twirlAttackSpeedBoost = 2.5; // Speed multiplier during twirl attack
    private double twirlAttackTimer = 0; // Timer between individual twirls
    private double twirlAttackAngle = 0; // Current angle for circular movement around screen
    
    private int level;
    private boolean isMegaBoss; // Every 3rd boss is a mega boss
    private int size; // Dynamic size based on boss type
    private static final int BASE_SIZE = 100;
    private static final double MAX_SPEED = 2.5; // Maximum movement speed
    private static final double ACCELERATION = 0.15; // How fast to speed up
    private static final double FRICTION = 0.92; // How fast to slow down (0.92 = 8% friction)
    private static final double ANGULAR_ACCELERATION = 0.015; // How fast to turn (reduced from 0.03 for smoother rotation)
    private static final double ANGULAR_FRICTION = 0.85; // Rotation damping (increased from 0.7 for smoother rotation)
    
    // Dark glow shadow settings (centered underneath)
    private static final double SHADOW_GLOW_OFFSET_Y = 5; // Slight downward offset for "underneath" feel
    private static final double SHADOW_MIN_SCALE = 0.75; // Innermost layer scale
    private static final double SHADOW_MAX_SCALE = 1.15; // Outermost layer scale
    private static final double SHADOW_WIDTH_STRETCH = 1.3; // Make shadow wider than tall
    private static final float SHADOW_MAX_ALPHA = 0.14f; // Alpha of innermost (most opaque) layer
    private static final float SHADOW_MIN_ALPHA = 0.02f; // Alpha of outermost (most transparent) layer
    
    private double shootTimer;
    private int shootInterval;
    private int patternType;
    private int maxPatterns; // Maximum attack patterns unlocked
    private java.util.Set<Integer> allowedPatterns = null; // Which pattern types are allowed (null = use maxPatterns)
    private double spiralRotation = 0; // Continuous rotation for spiral patterns
    
    // Spiral attack sequence state (spawns bullets one at a time)
    private boolean spiralAttackActive = false;
    private int spiralBulletsToSpawn = 0; // Total bullets in this spiral
    private int spiralBulletsSpawned = 0; // How many spawned so far
    private double spiralSpawnTimer = 0; // Timer for delay between bullets
    private static final double SPIRAL_SPAWN_DELAY = 5; // Frames between each bullet spawn
    private double spiralSpeed = 2.5; // Speed for all bullets in spiral
    
    private int forcedPatternType = -1; // For debug showcase mode: -1 = normal, 0-14 = forced pattern
    private int forcedMegaAttack = -1; // For debug showcase mode: -1 = normal, 0-4 = forced mega attack
    private boolean forceBeamAttack = false; // Force beam attacks for showcase
    private boolean forceShockwave = false; // Force shockwave for showcase
    private boolean forceTwirlAttack = false; // Force twirl attack for showcase
    private boolean disableBulletShooting = false; // Disable all bullet shooting for showcase
    private boolean disableBeamAttacks = false; // Disable beam attacks during showcase
    private boolean disableShockwave = false; // Disable shockwave during showcase
    private boolean disableTwirl = false; // Disable twirl during showcase
    private boolean debugSlowMode = false; // Slow shooting for debug showcase screenshots
    private boolean stayStationary = false; // Stay in place for debug showcase
    private static final int DEBUG_SLOW_SHOOT_INTERVAL = 150; // 2.5 seconds between shots in debug mode
    private double targetX, targetY; // Target position for smooth movement
    private double moveTimer; // Timer to pick new target
    private double beamAttackTimer; // Timer for beam attacks
    private int beamAttackInterval; // How often to spawn beam attacks
    private List<BeamAttack> beamAttacks; // Active beam attacks
    
    // Multiple sprite variants for planes and helicopters
    private static BufferedImage[] miniBossPlaneSprites = new BufferedImage[17]; // Planes 1-9, 11-15, Helicopters 2-4
    private static BufferedImage[] megaBossPlaneSprites = new BufferedImage[9]; // Boss Planes 1-8, Helicopter 1
    private static BufferedImage[] miniBossPlaneShadows = new BufferedImage[17];
    private static BufferedImage[] megaBossPlaneShadows = new BufferedImage[9];
    private static BufferedImage[] helicopterBlades = new BufferedImage[3]; // Rotor blade sprites
    private static BufferedImage finalBossSprite;
    private static BufferedImage finalBossShadow;
    private static boolean spritesLoaded = false;
    
    /**
     * Preload all boss sprites (called from background loading thread).
     * Creates a temporary Boss instance to trigger the static sprite loading.
     */
    public static void preloadSprites() {
        preloadSprites(null);
    }
    
    public static void preloadSprites(java.util.function.IntConsumer progressCallback) {
        if (!spritesLoaded) {
            loadSpritesWithProgress(progressCallback);
        }
    }

    /**
     * Get the sprite for a specific level (for use in level select preview).
     * This method ensures sprites are loaded and returns the appropriate sprite.
     */
    public static BufferedImage getSpriteForLevel(int level) {
        // Ensure sprites are loaded
        if (!spritesLoaded) {
            loadSpritesWithProgress(null);
        }
        
        if (level == 28) {
            return finalBossSprite;
        }
        
        boolean isMegaBoss = (level % 3 == 0);
        if (isMegaBoss) {
            int megaIndex = ((level / 3) - 1) % 9;
            return megaBossPlaneSprites[megaIndex];
        } else {
            int miniIndex = (level - 1) % 17;
            return miniBossPlaneSprites[miniIndex];
        }
    }
    
    /**
     * Check if a level features a helicopter boss (for level select rendering)
     */
    public static boolean isHelicopterLevel(int level) {
        if (level == 28) return false; // Final boss is not a helicopter
        
        boolean isMegaBoss = (level % 3 == 0);
        if (isMegaBoss) {
            int megaIndex = ((level / 3) - 1) % 9;
            return megaIndex == 7; // Helicopter 1 is at index 7 in megaBossPlaneSprites
        } else {
            int miniIndex = (level - 1) % 17;
            return miniIndex >= 14; // Helicopters 2-4 are at indices 14-16
        }
    }
    
    /**
     * Get the rotor blade sprite for a helicopter level (for level select rendering)
     */
    public static BufferedImage getRotorSpriteForLevel(int level) {
        if (!spritesLoaded) {
            loadSpritesWithProgress(null);
        }
        
        if (!isHelicopterLevel(level)) return null;
        
        boolean isMegaBoss = (level % 3 == 0);
        if (isMegaBoss) {
            return helicopterBlades[0]; // Helicopter 1 uses blade 0
        } else {
            int miniIndex = (level - 1) % 17;
            if (miniIndex == 14) return helicopterBlades[0]; // Helicopter 2 uses blade 0
            if (miniIndex == 15) return helicopterBlades[1]; // Helicopter 3 uses blade 1
            if (miniIndex == 16) return helicopterBlades[2]; // Helicopter 4 uses blade 2
        }
        return helicopterBlades[0];
    }
    
    // Animation for helicopter blades
    private double bladeRotation = 0;
    private static final double BLADE_ROTATION_SPEED = 0.2; // Radians per frame (reduced from 0.5)
    
    // Cached colors for performance (avoid allocations in hot paths)
    private static final Color WING_TRAIL_COLOR = new Color(200, 220, 255, 180);
    private static final Color ENGINE_GLOW_COLOR = new Color(100, 150, 255, 180);
    private static final Color JET_TRAIL_COLOR = new Color(255, 150, 0, 200);
    private static final Color EXPLOSION_COLOR = new Color(255, 200, 100, 200);
    
    // Sound manager for effects
    private SoundManager soundManager;
    private int lastScreenWidth; // Cached for spatial audio in shoot methods
    
    // Boss phases
    private int maxHealth;
    private int currentHealth;
    private int currentPhase; // 0-3 for phases (triggers every 2 HP lost, capped at 3)
    private boolean phaseTransitioning;
    private double phaseTransitionTimer;
    private static final int PHASE_TRANSITION_DURATION = 90; // 1.5 seconds
    
    // Attack rhythm phases (Assault vs Recovery)
    private boolean isAssaultPhase = true; // true = aggressive, false = recovery
    private double attackPhaseTimer = 0;
    private int assaultPhaseDuration = 300; // 5 seconds of assault (at 60fps)
    private int recoveryPhaseDuration = 180; // 3 seconds of recovery
    private double assaultSpeedMultiplier = 1.8; // Attack 80% faster during assault
    private double recoverySpeedMultiplier = 0.4; // Attack 60% slower during recovery
    private int phaseFlashTimer = 0; // Visual flash when phase changes
    private boolean justChangedPhase = false; // For visual effects
    
    // Shockwave attack (during recovery phase)
    private boolean shockwaveActive = false;
    private double shockwaveRadius = 0;
    private double shockwaveAngle = 0; // Direction towards player when spawned
    private boolean shockwaveHasHitPlayer = false; // Track if shockwave already hit player
    private double shockwaveMaxRadius = 250; // Maximum shockwave reach (smaller)
    private double shockwaveSpeed = 2.5; // Pixels per frame (slower for better visibility)
    private static final double SHOCKWAVE_KNOCKBACK = 12; // Knockback strength
    
    public Boss(double x, double y, int level) {
        this(x, y, level, null, GameMode.MASTER);
    }
    
    public Boss(double x, double y, int level, SoundManager soundManager) {
        this(x, y, level, soundManager, GameMode.MASTER);
    }
    
    public Boss(double x, double y, int level, SoundManager soundManager, GameMode gameMode) {
        this.x = x;
        this.y = y;
        this.soundManager = soundManager;
        this.vx = 0;
        this.vy = 0;
        this.ax = 0;
        this.ay = 0;
        this.rotation = Math.PI / 2; // Start facing down
        this.targetRotation = Math.PI / 2;
        this.angularVelocity = 0;
        this.wobbleRotation = 0;
        this.wobbleVelocity = 0;
        this.twirlRotation = 0;
        this.twirlActive = false;
        this.twirlAttackActive = false;
        this.twirlAttackCount = 0;
        this.twirlAttackTimer = 0;
        this.twirlAttackAngle = Math.random() * Math.PI * 2; // Random starting angle
        this.level = level;
        
        // Pattern: mini, mini, mega, mini, mini, mega...
        // Every 3rd level is a mega boss (3, 6, 9, 12...)
        this.isMegaBoss = (level % 3 == 0);
        
        // Size: mega bosses are 150% size, mini bosses are 95% size
        this.size = isMegaBoss ? (int)(BASE_SIZE * 1.5) : (int)(BASE_SIZE * 0.95);
        
        // Attack patterns unlock gradually - approximately 1 per level
        // Level 1: 3 patterns, Level 2: 4 patterns, ..., Level 13+: all 15 patterns
        this.maxPatterns = Math.min(2 + level, 15); // All patterns unlocked by level 13
        
        this.shootTimer = 0;
        this.shootInterval = Math.max(45, 75 + level * 2); // Slightly faster but more consistent
        // Start with random pattern from available pool
        this.patternType = (int)(Math.random() * maxPatterns);
        // Start with current position as target
        this.targetX = x;
        this.targetY = y;
        this.moveTimer = 0;
        this.beamAttacks = new ArrayList<>();
        this.beamAttackTimer = 180 + (int)(Math.random() * 60); // First beam after 3-4 seconds
        this.beamAttackInterval = Math.max(300, 480 - level * 10); // Less frequent, more manageable
        
        // Initialize health and phases
        this.maxHealth = isMegaBoss ? 5 : 3; // Mega bosses have 5 hits, mini bosses have 3 hits
        this.currentHealth = maxHealth;
        this.currentPhase = 0;
        this.phaseTransitioning = false;
        this.phaseTransitionTimer = 0;
        
        // Initialize attack rhythm phases - scale with level
        this.isAssaultPhase = true;
        this.attackPhaseTimer = 0;
        // Assault gets longer and recovery gets shorter at higher levels (slower scaling)
        this.assaultPhaseDuration = 300 + level * 8; // 5-6.3 seconds (reduced from *15)
        this.recoveryPhaseDuration = Math.max(150, 210 - level * 4); // 3.5-2.5 seconds (reduced from *8)
        // Mega bosses are more aggressive (but not overwhelming)
        if (isMegaBoss) {
            this.assaultPhaseDuration += 15; // +0.25 second assault (easier than normal bosses)
            this.recoveryPhaseDuration -= 15; // -0.25 second recovery (reduced from -30)
            this.assaultSpeedMultiplier = 1.6; // Moderately faster attacks (easier than before)
        }
        
        // Apply game mode scaling (Easy mode makes bosses more forgiving)
        if (gameMode != null && gameMode != GameMode.MASTER) {
            this.shootInterval = (int)(this.shootInterval * gameMode.getShootIntervalScale());
            this.assaultPhaseDuration = (int)(this.assaultPhaseDuration * gameMode.getAssaultDurationScale());
            this.recoveryPhaseDuration = (int)(this.recoveryPhaseDuration * gameMode.getRecoveryDurationScale());
            this.beamAttackTimer = (int)(this.beamAttackTimer * gameMode.getBeamTimerScale());
            this.beamAttackInterval = (int)(this.beamAttackInterval * gameMode.getBeamTimerScale());
        }
        
        loadSprites();
    }
    
    private static BufferedImage rotateImage180(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage rotated = new BufferedImage(w, h, img.getType());
        Graphics2D g2d = rotated.createGraphics();
        g2d.rotate(Math.PI, w / 2.0, h / 2.0);
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return rotated;
    }
    
    private void loadSprites() {
        loadSpritesWithProgress(null);
    }
    
    private static void loadSpritesWithProgress(java.util.function.IntConsumer progressCallback) {
        if (spritesLoaded) return;
        try {
            int totalAssets = 58;
            int[] loaded = {0};
            // Load mini boss planes: Planes 1-9, 11-15 (14 planes)
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 1.png", miniBossPlaneSprites, 0);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 2.png", miniBossPlaneSprites, 1);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 3.png", miniBossPlaneSprites, 2);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 4.png", miniBossPlaneSprites, 3);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 5.png", miniBossPlaneSprites, 4);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 6.png", miniBossPlaneSprites, 5);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 7.png", miniBossPlaneSprites, 6);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 8.png", miniBossPlaneSprites, 7);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 9.png", miniBossPlaneSprites, 8);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 11.png", miniBossPlaneSprites, 9);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 12.png", miniBossPlaneSprites, 10);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 13.png", miniBossPlaneSprites, 11);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 14.png", miniBossPlaneSprites, 12);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 15.png", miniBossPlaneSprites, 13);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            // Helicopters 2, 3, 4 for mini bosses
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 2.png", miniBossPlaneSprites, 14);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3.png", miniBossPlaneSprites, 15);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4.png", miniBossPlaneSprites, 16);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            
            // Load mini boss shadows
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 1 Shadow.png", miniBossPlaneShadows, 0);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 2 Shadow.png", miniBossPlaneShadows, 1);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 3 Shadow.png", miniBossPlaneShadows, 2);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 4 Shadow.png", miniBossPlaneShadows, 3);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 5 Shadow.png", miniBossPlaneShadows, 4);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 6 Shadow.png", miniBossPlaneShadows, 5);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 7 Shadow.png", miniBossPlaneShadows, 6);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 8 Shadow.png", miniBossPlaneShadows, 7);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 9 Shadow.png", miniBossPlaneShadows, 8);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 11 Shadow.png", miniBossPlaneShadows, 9);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 12 Shadow.png", miniBossPlaneShadows, 10);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 13 Shadow.png", miniBossPlaneShadows, 11);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 14 Shadow.png", miniBossPlaneShadows, 12);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\Plane 15 Shadow.png", miniBossPlaneShadows, 13);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 2 Shadow.png", miniBossPlaneShadows, 14);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3 Shadow.png", miniBossPlaneShadows, 15);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4 Shadow.png", miniBossPlaneShadows, 16);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            
            // Load mega boss planes: Boss Planes 1-8 (8 boss planes)
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 1.png", megaBossPlaneSprites, 0);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 2.png", megaBossPlaneSprites, 1);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 3.png", megaBossPlaneSprites, 2);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 4.png", megaBossPlaneSprites, 3);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 5.png", megaBossPlaneSprites, 4);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 6.png", megaBossPlaneSprites, 5);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 7.png", megaBossPlaneSprites, 6);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            // Helicopter 1 for mega bosses (swapped with Boss Plane 8 for level 24/27)
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 1.png", megaBossPlaneSprites, 7);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 8.png", megaBossPlaneSprites, 8);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            
            // Load mega boss shadows
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 1 Shadow.png", megaBossPlaneShadows, 0);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 2 Shadow.png", megaBossPlaneShadows, 1);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 3 Shadow.png", megaBossPlaneShadows, 2);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 4 Shadow.png", megaBossPlaneShadows, 3);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 5 Shadow.png", megaBossPlaneShadows, 4);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 6 Shadow.png", megaBossPlaneShadows, 5);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 7 Shadow.png", megaBossPlaneShadows, 6);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 1 Shadow.png", megaBossPlaneShadows, 7);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 8 Shadow.png", megaBossPlaneShadows, 8);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            
            // Load helicopter blade sprites
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter Wings.png", helicopterBlades, 0);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3 Wings.png", helicopterBlades, 1);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4 Wings.png", helicopterBlades, 2);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            
            // Load final boss
            finalBossSprite = AssetLoader.prescaleImage(
                rotateImage180(AssetLoader.loadImage("sprites\\Missle Man Assets\\Boss Planes\\Final Boss.png")), SPRITE_PRESCALE_SIZE);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            finalBossShadow = AssetLoader.prescaleImage(
                rotateImage180(AssetLoader.loadImage("sprites\\Missle Man Assets\\Boss Planes\\Final Boss Shadow.png")), SPRITE_PRESCALE_SIZE);
            loaded[0]++; if (progressCallback != null) progressCallback.accept((int)(loaded[0] * 100.0 / totalAssets));
            
            spritesLoaded = true;
        } catch (IOException e) {
            System.err.println("Failed to load boss sprites: " + e.getMessage());
            // Don't set spritesLoaded to false - allow fallback rendering
        }
    }
    
    // Maximum rendered size for any boss sprite (mega boss: BASE_SIZE*1.5*2 = 300)
    private static final int SPRITE_PRESCALE_SIZE = (int)(BASE_SIZE * 1.5 * 2);

    private static void loadBossSpriteWithPath(String path, BufferedImage[] array, int index) throws IOException {
        try {
            array[index] = AssetLoader.prescaleImage(
                rotateImage180(AssetLoader.loadImage(path)), SPRITE_PRESCALE_SIZE);
        } catch (IOException e) {
            System.err.println("Could not load boss sprite: " + path);
            throw e;
        }
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight) {
        update(bullets, player, screenWidth, screenHeight, 1.0, null);
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight, double deltaTime) {
        update(bullets, player, screenWidth, screenHeight, deltaTime, null);
    }
    
    // Update only visual animations (helicopter blades, etc.) - safe to call during intro
    public void updateAnimations(double deltaTime) {
        // Animate helicopter blades if this is a helicopter
        if (isHelicopter()) {
            bladeRotation += BLADE_ROTATION_SPEED * deltaTime;
            if (bladeRotation > Math.PI * 2) {
                bladeRotation -= Math.PI * 2;
            }
        }
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight, double deltaTime, List<Particle> particles) {
        // Cache screen width for spatial audio in shoot sub-methods
        this.lastScreenWidth = screenWidth;
        
        // Smooth movement to target position
        moveTimer += deltaTime;
        
        // Pick a new target every 120-180 frames (2-3 seconds) for longer paths
        // Skip normal movement during twirl attack
        if (!twirlAttackActive && moveTimer >= 120 + Math.random() * 60) {
            moveTimer = 0;
            
            // Calculate vector away from player
            double playerX = player.getX();
            double playerY = player.getY();
            double awayFromPlayerX = x - playerX;
            double awayFromPlayerY = y - playerY;
            double distFromPlayer = Math.sqrt(awayFromPlayerX * awayFromPlayerX + awayFromPlayerY * awayFromPlayerY);
            
            // If too close to player, move away; otherwise pick points that maintain distance
            if (distFromPlayer > 1) {
                awayFromPlayerX /= distFromPlayer;
                awayFromPlayerY /= distFromPlayer;
            }
            
            // Pick a target that's away from the player
            double centerX = screenWidth / 2.0;
            double centerY = screenHeight / 3.0; // Lowered from /4.0 to /3.0
            double radius = Math.min(screenWidth, screenHeight) / 2.0; // Increased from /3.0 to /2.0 for larger circles
            
            // Bias the angle to point away from player
            double angleToPlayer = Math.atan2(playerY - y, playerX - x);
            double avoidAngle = angleToPlayer + Math.PI + (Math.random() - 0.5) * Math.PI / 2; // Opposite direction ± 45°
            
            targetX = centerX + Math.cos(avoidAngle) * radius;
            targetY = centerY + Math.sin(avoidAngle) * radius;
            
            // Clamp to screen bounds
            targetX = Math.max(size, Math.min(screenWidth - size, targetX));
            targetY = Math.max(size, Math.min(screenHeight / 1.8 - size, targetY)); // Lowered from /2.5 to /1.8
        }
        
        // Calculate direction to target
        double dx = targetX - x;
        double dy = targetY - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // Skip movement if stationary mode is enabled (for debug showcase)
        if (stayStationary) {
            vx = 0;
            vy = 0;
            ax = 0;
            ay = 0;
        } else if (distance > 10) { // Dead zone to prevent jittering
            // Calculate desired acceleration direction (reduced scaling)
            double accelStrength = ACCELERATION * (1.0 + level * 0.025);
            
            // Apply speed boost during twirl attack
            if (twirlAttackActive) {
                accelStrength *= twirlAttackSpeedBoost;
   
            }
            
            ax = (dx / distance) * accelStrength * deltaTime;
            ay = (dy / distance) * accelStrength * deltaTime;
            
            // Apply acceleration to velocity
            vx += ax;
            vy += ay;
            
            // Calculate target rotation based on movement direction
            targetRotation = Math.atan2(dy, dx);
        } else {
            // Arrived at target, no acceleration
            ax = 0;
            ay = 0;
        }
        
        // Apply friction (deltaTime is always 1.0 fixed timestep, avoid expensive Math.pow)
        double frictionFactor = (deltaTime == 1.0) ? FRICTION : Math.pow(FRICTION, deltaTime);
        vx *= frictionFactor;
        vy *= frictionFactor;
        
        // Limit max speed (reduced scaling)
        double speed = Math.sqrt(vx * vx + vy * vy);
        double maxSpeed = MAX_SPEED * (1.0 + level * 0.05);
        
        // Boost max speed during twirl attack
        if (twirlAttackActive) {
            maxSpeed *= twirlAttackSpeedBoost;
        }
        
        if (speed > maxSpeed) {
            vx = (vx / speed) * maxSpeed;
            vy = (vy / speed) * maxSpeed;
        }
        
        // Apply velocity to position
        x += vx * deltaTime;
        y += vy * deltaTime;
        
        // Smooth angular acceleration for rotation
        double rotationDiff = targetRotation - rotation;
        // Normalize angle difference to [-PI, PI]
        while (rotationDiff > Math.PI) rotationDiff -= 2 * Math.PI;
        while (rotationDiff < -Math.PI) rotationDiff += 2 * Math.PI;
        
        // Apply angular acceleration
        double angularAccel = rotationDiff * ANGULAR_ACCELERATION * deltaTime;
        angularVelocity += angularAccel;
        
        // Apply angular friction (deltaTime is always 1.0 fixed timestep)
        angularVelocity *= (deltaTime == 1.0) ? ANGULAR_FRICTION : Math.pow(ANGULAR_FRICTION, deltaTime);
        
        // Apply angular velocity to rotation
        rotation += angularVelocity * deltaTime;
        
        // Update wobble effect (z-axis rotation)
        // Apply spring force to return to zero
        wobbleVelocity += -wobbleRotation * WOBBLE_SPRING * deltaTime;
        // Apply damping (deltaTime is always 1.0 fixed timestep)
        wobbleVelocity *= (deltaTime == 1.0) ? WOBBLE_DAMPING : Math.pow(WOBBLE_DAMPING, deltaTime);
        // Apply velocity
        wobbleRotation += wobbleVelocity * deltaTime;
        
        // Clamp wobble to prevent extreme values
        if (Math.abs(wobbleRotation) < 0.001 && Math.abs(wobbleVelocity) < 0.001) {
            wobbleRotation = 0;
            wobbleVelocity = 0;
        }
        
        // Update twirl effect
        if (twirlActive) {
            twirlRotation += TWIRL_SPEED * deltaTime;
            if (twirlRotation >= Math.PI * 2) {
                twirlRotation = 0;
                twirlActive = false;
            }
        }
        
        // Generate wing tip trails for all boss types (planes and helicopters)
        if (particles != null) {
            // Get current sprite dimensions for accurate wing positioning
            BufferedImage currentSprite = getCurrentSprite();
            double wingSpan = size * 0.8; // Default fallback
            
            if (currentSprite != null) {
                // Calculate actual sprite width after scaling
                int nativeWidth = currentSprite.getWidth();
                int nativeHeight = currentSprite.getHeight();
                double targetSize = size * 2;
                double scaleX = targetSize / nativeWidth;
                double scaleY = targetSize / nativeHeight;
                double scale = Math.min(scaleX, scaleY);
                int actualSpriteWidth = (int)(nativeWidth * scale);
                
                // Wing span is half the actual sprite width
                wingSpan = actualSpriteWidth * 0.5;
            }
            
            // Apply wobble/twirl scale to wing span
            double currentScale = 1.0;
            if (twirlActive && Math.abs(wobbleRotation) > 0.001) {
                currentScale = Math.sin(twirlRotation + Math.PI / 2 + wobbleRotation);
            } else if (twirlActive) {
                currentScale = Math.sin(twirlRotation + Math.PI / 2);
            } else if (Math.abs(wobbleRotation) > 0.001) {
                currentScale = 0.65 + 0.35 * Math.cos(wobbleRotation);
            }
            double scaledWingSpan = wingSpan * Math.abs(currentScale);
            
            // Calculate wing tip positions (perpendicular to rotation)
            double perpAngle = rotation + Math.PI / 2; // Perpendicular to facing direction
            
            // Left wing tip
            double leftWingX = x + Math.cos(perpAngle) * scaledWingSpan;
            double leftWingY = y + Math.sin(perpAngle) * scaledWingSpan;
            
            // Right wing tip
            double rightWingX = x - Math.cos(perpAngle) * scaledWingSpan;
            double rightWingY = y - Math.sin(perpAngle) * scaledWingSpan;
            
            // Larger trails for mega bosses
            int trailSize = isMegaBoss ? 8 : 4;
            int trailSizeVariation = isMegaBoss ? 6 : 3;
            
            // Spawn trail particles at wing tips (every few frames) - throttled for performance
            if (particles.size() < 200 && Math.random() < 0.2 * deltaTime) {
                // Left wing trail
                particles.add(new Particle(
                    leftWingX,
                    leftWingY,
                    -vx * 0.3 + (Math.random() - 0.5) * 0.5,
                    -vy * 0.3 + (Math.random() - 0.5) * 0.5,
                    WING_TRAIL_COLOR,
                    20 + (int)(Math.random() * 15),
                    trailSize + (int)(Math.random() * trailSizeVariation),
                    Particle.ParticleType.TRAIL
                ));
                
                // Right wing trail
                particles.add(new Particle(
                    rightWingX,
                    rightWingY,
                    -vx * 0.3 + (Math.random() - 0.5) * 0.5,
                    -vy * 0.3 + (Math.random() - 0.5) * 0.5,
                    WING_TRAIL_COLOR,
                    20 + (int)(Math.random() * 15),
                    trailSize + (int)(Math.random() * trailSizeVariation),
                    Particle.ParticleType.TRAIL
                ));
            }
        }
        
        // Update animations (blade rotation, etc.)
        updateAnimations(deltaTime);
        
        // Update phase transition
        if (phaseTransitioning) {
            phaseTransitionTimer += deltaTime;
            if (phaseTransitionTimer >= PHASE_TRANSITION_DURATION) {
                phaseTransitioning = false;
                phaseTransitionTimer = 0;
            }
            return; // Don't shoot or move during phase transition
        }
        
        // Keep boss within bounds (and bounce off walls)
        if (x < size || x > screenWidth - size) {
            x = Math.max(size, Math.min(screenWidth - size, x));
            vx *= -0.5; // Bounce with energy loss
        }
        if (y < size || y > screenHeight / 3) {
            y = Math.max(size, Math.min(screenHeight / 3, y));
            vy *= -0.5; // Bounce with energy loss
        }
        
        // Update attack rhythm phase (Assault vs Recovery)
        attackPhaseTimer += deltaTime;
        phaseFlashTimer = Math.max(0, phaseFlashTimer - 1);
        justChangedPhase = false;
        
        int currentPhaseDuration = isAssaultPhase ? assaultPhaseDuration : recoveryPhaseDuration;
        if (attackPhaseTimer >= currentPhaseDuration) {
            attackPhaseTimer = 0;
            isAssaultPhase = !isAssaultPhase;
            phaseFlashTimer = 30; // Visual flash for 0.5 seconds
            justChangedPhase = true;
            
            // When entering recovery phase, randomly spawn shockwave (only if boss has been hit at least once)
            // Shockwave attack only available from level 12 onwards (or forced for debug showcase)
            boolean shouldShockwave = !disableShockwave && (level >= 12 || forceShockwave) && 
                                      (!isAssaultPhase) && 
                                      (forceShockwave || (currentHealth < maxHealth && Math.random() < 0.4));
            if (shouldShockwave && !shockwaveActive) {
                shockwaveActive = true;
                shockwaveRadius = 0;
                shockwaveHasHitPlayer = false; // Reset hit tracking
                // Calculate angle towards player
                if (player != null) {
                    shockwaveAngle = Math.atan2(player.getY() - y, player.getX() - x);
                }
                if (soundManager != null) {
                    soundManager.playSoundSpatial(SoundManager.Sound.MAGIC_CHARGE, 1.0f, this.x, lastScreenWidth);
                }
            }
            
            // When entering assault phase, immediately switch to a new pattern
            if (isAssaultPhase) {
                patternType = getRandomAllowedPattern();
                
                // 30% chance to start a twirl attack sequence during assault
                // Twirl/spin attack only available from level 7 onwards (or forced for debug showcase)
                boolean shouldTwirl = !disableTwirl && (level >= 7 || forceTwirlAttack) && 
                                      (forceTwirlAttack || Math.random() < 0.3);
                if (shouldTwirl && !twirlAttackActive) {
                    twirlAttackActive = true;
                    twirlAttackCount = 0;
                    twirlAttackTimer = 0;
                    triggerTwirl(); // Start first twirl
                }
            }
        }
        
        // Calculate attack speed based on current phase
        double attackPhaseMultiplier = isAssaultPhase ? assaultSpeedMultiplier : recoverySpeedMultiplier;
        
        // Update shockwave
        if (shockwaveActive) {
            shockwaveRadius += shockwaveSpeed * deltaTime;
            if (shockwaveRadius >= shockwaveMaxRadius) {
                shockwaveActive = false;
            }
        }
        
        // Update twirl attack sequence
        if (twirlAttackActive && isAssaultPhase) {
            twirlAttackTimer += deltaTime;
            
            // Continuously move along circular arc while attack is active
            twirlAttackAngle += 0.03 * deltaTime; // Constant angular velocity
            
            // Calculate position on circular arc around screen edge
            double centerX = screenWidth / 2.0;
            double centerY = screenHeight / 3.0;
            double arcRadius = Math.min(screenWidth, screenHeight) * 0.4; // Large arc
            
            targetX = centerX + Math.cos(twirlAttackAngle) * arcRadius;
            targetY = centerY + Math.sin(twirlAttackAngle) * arcRadius;
            
            // Clamp to screen bounds
            targetX = Math.max(size, Math.min(screenWidth - size, targetX));
            targetY = Math.max(size, Math.min(screenHeight / 1.8 - size, targetY));
            
            // Check if current twirl finished and we need another
            if (!twirlActive && twirlAttackTimer > 30) { // Delay between twirls
                twirlAttackCount++;
                
                if (twirlAttackCount < twirlAttackMaxCount) {
                    // Start next twirl
                    triggerTwirl();
                    twirlAttackTimer = 0;
                } else {
                    // Finished all twirls
                    twirlAttackActive = false;
                    twirlAttackCount = 0;
                }
            }
        }
        
        // Shooting pattern (scaled by delta time) - faster in later phases
        double phaseSpeedMultiplier = 1.0 + (currentPhase * 0.15); // 15% faster per phase
        shootTimer += deltaTime * phaseSpeedMultiplier * attackPhaseMultiplier;
        if (shootTimer >= shootInterval) {
            shootTimer = 0;
            shoot(bullets, player);
        }
        
        // Update spiral attack sequence (spawns bullets one at a time)
        updateSpiralAttack(bullets, deltaTime);
        
        // Beam attacks (at higher levels - starting at level 10, or forced for debug showcase)
        // Don't fire beams if a specific non-beam pattern is forced or if beams are disabled
        boolean shouldFireBeams = !disableBeamAttacks && (forceBeamAttack || (level >= 10 && forcedPatternType < 0 && forcedMegaAttack < 0));
        if (shouldFireBeams) {
            beamAttackTimer += deltaTime;
            // Use faster interval when forced for debug showcase
            int effectiveInterval = forceBeamAttack ? Math.min(120, beamAttackInterval) : beamAttackInterval;
            if (beamAttackTimer >= effectiveInterval) {
                beamAttackTimer = 0;
                spawnBeamAttack(screenWidth, screenHeight);
            }
        }
        
        // Update beam attacks
        for (int i = beamAttacks.size() - 1; i >= 0; i--) {
            BeamAttack beam = beamAttacks.get(i);
            beam.update(deltaTime);
            if (beam.isDone()) {
                beamAttacks.remove(i);
            }
        }
    }
    
    private void shoot(List<Bullet> bullets, Player player) {
        // Skip all bullet shooting if disabled (for beam/shockwave/twirl showcase)
        if (disableBulletShooting) return;
        
        int bulletCountBefore = bullets.size();
        
        // If a mega attack is forced (for debug showcase), use only that
        if (forcedMegaAttack >= 0 && forcedMegaAttack <= 4) {
            switch (forcedMegaAttack) {
                case 0:
                    shootMegaBarrage(bullets, player);
                    break;
                case 1:
                    shootMegaSpiral(bullets);
                    break;
                case 2:
                    shootMegaCross(bullets, player);
                    break;
                case 3:
                    shootMegaStar(bullets);
                    break;
                case 4:
                    shootMegaHex(bullets, player);
                    break;
            }
            // Play boss shoot sound
            if (soundManager != null && bullets.size() > bulletCountBefore) {
                soundManager.playSoundSpatial(SoundManager.Sound.BOSS_SHOOT, 0.25f, this.x, lastScreenWidth);
            }
            return;
        }
        
        // Mega bosses have special attack patterns (only if not in forced pattern mode)
        if (isMegaBoss && forcedPatternType < 0 && Math.random() < 0.15) {
            // 15% chance to use mega boss special attacks (reduced from 25%)
            int specialPattern = (int)(Math.random() * 5);
            switch (specialPattern) {
                case 0:
                    shootMegaBarrage(bullets, player);
                    return;
                case 1:
                    shootMegaSpiral(bullets);
                    return;
                case 2:
                    shootMegaCross(bullets, player);
                    return;
                case 3:
                    shootMegaStar(bullets);
                    return;
                case 4:
                    shootMegaHex(bullets, player);
                    return;
            }
        }
        
        // Cycle through unlocked patterns only (or use forced pattern for debug showcase)
        int currentPattern;
        if (forcedPatternType >= 0 && forcedPatternType < 17) {
            currentPattern = forcedPatternType; // Use forced pattern
        } else {
            // Use allowed patterns if set, otherwise use maxPatterns
            patternType = getNextAllowedPattern();
            currentPattern = patternType;
        }
        
        switch (currentPattern) {
            case 0: // Spiral pattern
                shootSpiral(bullets);
                break;
            case 1: // Circle pattern
                shootCircle(bullets, 15 + level); // Slower increase (was level * 2)
                break;
            case 2: // Aimed at player
                shootAtPlayer(bullets, player, 6); // Increased from 4
                break;
            case 3: // Wave pattern
                shootWave(bullets);
                break;
            case 4: // Random spray
                shootRandom(bullets, 10 + level); // Slower increase (was level * 2)
                break;
            case 5: // Fast bullets
                shootFast(bullets, player);
                break;
            case 6: // Large bullets
                shootLarge(bullets);
                break;
            case 7: // Mixed attack
                shootMixed(bullets, player);
                break;
            case 8: // Spiral bullets
                shootSpiralBullets(bullets);
                break;
            case 10: // Accelerating bullets
                shootAcceleratingBullets(bullets, player);
                break;
            case 11: // Wave bullets
                shootWaveBullets(bullets);
                break;
            case 12: // Bombs
                shootBombs(bullets);
                break;
            case 13: // Grenades at player
                shootGrenades(bullets, player);
                break;
            case 14: // Mini nukes
                shootNukes(bullets);
                break;
            case 15: // Homing bullets
                shootHoming(bullets, player);
                break;
            case 16: // Bouncing bullets
                shootBouncing(bullets);
                break;
        }
        
        // Play type-specific boss shoot sound if bullets were actually spawned
        if (soundManager != null && bullets.size() > bulletCountBefore) {
            SoundManager.Sound shootSound;
            switch (currentPattern) {
                case 5:  // Fast bullets
                    shootSound = SoundManager.Sound.BOSS_SHOOT_FAST;
                    break;
                case 6:  // Large bullets
                    shootSound = SoundManager.Sound.BOSS_SHOOT_LARGE;
                    break;
                case 15: // Homing bullets
                    shootSound = SoundManager.Sound.BOSS_SHOOT_HOMING;
                    break;
                case 16: // Bouncing bullets
                    shootSound = SoundManager.Sound.BOSS_SHOOT_BOUNCING;
                    break;
                case 0:  // Spiral pattern
                case 8:  // Spiral bullets
                    shootSound = SoundManager.Sound.BOSS_SHOOT_SPIRAL;
                    break;
                case 10: // Accelerating bullets
                    shootSound = SoundManager.Sound.BOSS_SHOOT_ACCELERATING;
                    break;
                case 3:  // Wave pattern
                case 11: // Wave bullets
                    shootSound = SoundManager.Sound.BOSS_SHOOT_WAVE;
                    break;
                case 12: // Bombs
                    shootSound = SoundManager.Sound.BOSS_SHOOT_BOMB;
                    break;
                case 13: // Grenades
                    shootSound = SoundManager.Sound.BOSS_SHOOT_GRENADE;
                    break;
                case 14: // Nukes
                    shootSound = SoundManager.Sound.BOSS_SHOOT_NUKE;
                    break;
                default: // Normal, circle, aimed, random, mixed
                    shootSound = SoundManager.Sound.BOSS_SHOOT;
                    break;
            }
            soundManager.playSoundSpatial(shootSound, 0.25f, this.x, lastScreenWidth);
        }
    }
    
    private void shootSpiral(List<Bullet> bullets) {
        // Spiral: initiate a sequence that spawns bullets one at a time
        // This creates a true spiral pattern as each bullet is released at a different angle
        if (!spiralAttackActive) {
            spiralAttackActive = true;
            spiralBulletsToSpawn = 20 + level * 3; // More bullets for better spiral
            spiralBulletsSpawned = 0;
            spiralSpawnTimer = 0;
            spiralSpeed = Math.min(3.0, 1.5 + (level * 0.1)); // All bullets same speed
        }
        // Actual spawning happens in updateSpiralAttack()
    }
    
    // Update spiral attack - spawns one bullet at a time with delay
    private void updateSpiralAttack(List<Bullet> bullets, double deltaTime) {
        if (!spiralAttackActive) return;
        
        spiralSpawnTimer += deltaTime;
        
        // Spawn a bullet when timer is ready
        while (spiralSpawnTimer >= SPIRAL_SPAWN_DELAY && spiralBulletsSpawned < spiralBulletsToSpawn) {
            spiralSpawnTimer -= SPIRAL_SPAWN_DELAY;
            
            // Each bullet gets spawned at progressively rotating angle
            double angle = spiralRotation;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * spiralSpeed, Math.sin(angle) * spiralSpeed, Bullet.BulletType.NORMAL));
            
            spiralBulletsSpawned++;
            spiralRotation += 0.3; // Rotate for next bullet (creates the spiral)
            
            // Play sound for each bullet spawn (quieter)
            if (soundManager != null && spiralBulletsSpawned % 3 == 1) {
                soundManager.playSoundSpatial(SoundManager.Sound.BOSS_SHOOT, 0.1f, this.x, lastScreenWidth);
            }
        }
        
        // Check if spiral is complete
        if (spiralBulletsSpawned >= spiralBulletsToSpawn) {
            spiralAttackActive = false;
        }
    }
    
    private void shootCircle(List<Bullet> bullets, int numBullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.NORMAL));
        }
    }
    
    private void shootAtPlayer(List<Bullet> bullets, Player player, int spread) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -spread; i <= spread; i++) {
            double angle = angleToPlayer + (i * 0.2);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 4 * speedMultiplier, Math.sin(angle) * 4 * speedMultiplier, Bullet.BulletType.NORMAL));
        }
    }
    
    private void shootWave(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 16 + level; // Slower increase (was level * 2)
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI / 4 + (Math.PI / 2 * i / numBullets);
            double speed = (2 + Math.sin(i * 0.5) * 1.5) * speedMultiplier;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, Bullet.BulletType.NORMAL));
        }
    }
    
    private void shootRandom(List<Bullet> bullets, int numBullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = (2 + Math.random() * 2) * speedMultiplier;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, Bullet.BulletType.NORMAL));
        }
    }
    
    private void shootFast(List<Bullet> bullets, Player player) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = 0; i < 5 + level / 2; i++) { // Slower increase (was level)
            double angle = angleToPlayer + (Math.random() - 0.5) * 0.5;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 6 * speedMultiplier, Math.sin(angle) * 6 * speedMultiplier, Bullet.BulletType.FAST));
        }
    }
    
    private void shootLarge(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 5 + level / 2; // Slower increase
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, Bullet.BulletType.LARGE));
        }
    }
    
    private void shootHoming(List<Bullet> bullets, Player player) {
        // Dedicated homing bullet attack
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15));
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        int numBullets = 5 + level / 2;
        for (int i = 0; i < numBullets; i++) {
            double angle = angleToPlayer + (i - numBullets/2) * 0.25;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.HOMING));
        }
    }
    
    private void shootBouncing(List<Bullet> bullets) {
        // Dedicated bouncing bullet attack
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15));
        int numBullets = 8 + level / 2;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 3 * speedMultiplier, Math.sin(angle) * 3 * speedMultiplier, Bullet.BulletType.BOUNCING));
        }
    }
    
    private void shootMixed(List<Bullet> bullets, Player player) {
        // Combination attack with different bullet types (spiral + bouncing)
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        
        // Spiral bullets
        int numSpiral = 4 + level / 3;
        for (int i = 0; i < numSpiral; i++) {
            double angle = Math.PI * 2 * i / numSpiral;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.SPIRAL));
        }
        
        // Circle of bouncing bullets
        if (level >= 3) {
            for (int i = 0; i < 8; i++) { // Increased from 4
                double angle = Math.PI * 2 * i / 8; // Updated divisor
                double spawnX = x + Math.cos(angle) * size * 1.5;
                double spawnY = y + Math.sin(angle) * size * 1.5;
                bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 3 * speedMultiplier, Math.sin(angle) * 3 * speedMultiplier, Bullet.BulletType.BOUNCING));
            }
        }
    }
    
    private void shootSpiralBullets(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 5 + level / 2; // Slower increase
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2 * speedMultiplier, Math.sin(angle) * 2 * speedMultiplier, Bullet.BulletType.SPIRAL));
        }
    }
    
    private void shootAcceleratingBullets(List<Bullet> bullets, Player player) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -2; i <= 2; i++) { // Increased from -1 to 1 (now 5 bullets instead of 3)
            double angle = angleToPlayer + i * 0.3;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, Bullet.BulletType.ACCELERATING));
        }
    }
    
    private void shootWaveBullets(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 8 + level / 2; // Slower increase
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI / 4 + (Math.PI / 2 * i / numBullets);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.WAVE));
        }
    }
    
    private void shootBombs(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 3 + level / 2; // Increased from 2 + level / 3
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2.0 * speedMultiplier, Math.sin(angle) * 2.0 * speedMultiplier, Bullet.BulletType.BOMB));
        }
    }
    
    private void shootGrenades(List<Bullet> bullets, Player player) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        int numBullets = 1 + (level >= 8 ? 1 : 0); // Fires fewer grenades
        for (int i = 0; i < numBullets; i++) {
            double angle = angleToPlayer + (i - numBullets/2.0) * 0.3;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.GRENADE));
        }
    }
    
    private void shootNukes(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        // 1-3 nukes since they're very powerful
        int numBullets = 1 + (level >= 4 ? 1 : 0) + (level >= 7 ? 1 : 0); // Increased from 1 + (level >= 5 ? 1 : 0)
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, Bullet.BulletType.NUKE));
        }
    }
    
    // ========== MEGA BOSS SPECIAL ATTACKS ==========
    
    private void shootMegaBarrage(List<Bullet> bullets, Player player) {
        // Massive dense bullet storm aimed at player
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        
        // Dense cone of bullets
        int numBullets = 15 + level * 2;
        for (int i = 0; i < numBullets; i++) {
            double spread = Math.PI / 3; // 60 degree cone
            double angle = angleToPlayer + (i / (double)numBullets - 0.5) * spread;
            double speed = (2.5 + Math.random() * 2) * speedMultiplier;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            
            // Only use bullet types that are unlocked at current level
            // mega_burst unlocks at level 3. Bullet unlock levels:
            // LARGE=3, FAST=4, SPIRAL=6, BOUNCING=7, ACCELERATING=9, GRENADE=10, WAVE=12, HOMING=13, BOMB=15
            Bullet.BulletType type = Bullet.BulletType.NORMAL;
            double rand = Math.random();
            if (level >= 9 && rand < 0.2) {
                type = Bullet.BulletType.ACCELERATING;
            } else if (level >= 4 && rand < 0.35) {
                type = Bullet.BulletType.FAST;
            } else if (level >= 3 && rand < 0.5) {
                type = Bullet.BulletType.LARGE;
            }
            // At level 3, just large and normal bullets
            
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, type));
        }
    }
    
    private void shootMegaSpiral(List<Bullet> bullets) {
        // Layered spiral with multiple speeds and level-appropriate types
        // Unlocks at level 15. Can use: all except NUKE(18)
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleOffset = shootTimer * 0.15;
        
        // Three layers of spirals at different speeds
        int[] layers = {8, 12, 16};
        double[] speeds = {2.0, 3.0, 4.0};
        // Spiral bullets unlock at 6, Bomb bullets at 15
        Bullet.BulletType[] types = {
            Bullet.BulletType.NORMAL, 
            level >= 6 ? Bullet.BulletType.SPIRAL : Bullet.BulletType.NORMAL, 
            level >= 15 ? Bullet.BulletType.BOMB : Bullet.BulletType.NORMAL
        };
        
        for (int layer = 0; layer < 3; layer++) {
            int numBullets = layers[layer] + level;
            double layerOffset = angleOffset * (1 + layer * 0.3);
            
            for (int i = 0; i < numBullets; i++) {
                double angle = (Math.PI * 2 * i / numBullets) + layerOffset;
                double speed = speeds[layer] * speedMultiplier;
                double spawnX = x + Math.cos(angle) * size * 1.5;
                double spawnY = y + Math.sin(angle) * size * 1.5;
                bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, types[layer]));
            }
        }
    }
    
    private void shootMegaCross(List<Bullet> bullets, Player player) {
        // Cross pattern with rotating arms + center bullets
        // Unlocks at level 6
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        
        // Four arms of the cross
        for (int arm = 0; arm < 4; arm++) {
            double armAngle = (Math.PI / 2 * arm) + shootTimer * 0.1;
            int bulletsPerArm = 5 + level / 2;
            
            for (int i = 0; i < bulletsPerArm; i++) {
                double angle = armAngle;
                double distance = (i + 1) * 0.3;
                double speed = (2.5 + distance) * speedMultiplier;
                double spawnX = x + Math.cos(angle) * size * 1.5;
                double spawnY = y + Math.sin(angle) * size * 1.5;
                
                // Large bullets only if level >= 3
                Bullet.BulletType type = (level >= 3 && i % 3 == 0) ? Bullet.BulletType.LARGE : Bullet.BulletType.NORMAL;
                bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, type));
            }
        }
        
        // Center cluster - spiral only if level >= 6, otherwise fast if >= 4, large if >= 3
        for (int i = 0; i < 3 + level / 3; i++) {
            double angle = angleToPlayer + (Math.random() - 0.5) * 0.8;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            Bullet.BulletType type = level >= 6 ? Bullet.BulletType.SPIRAL : 
                                     (level >= 4 ? Bullet.BulletType.FAST : 
                                     (level >= 3 ? Bullet.BulletType.LARGE : Bullet.BulletType.NORMAL));
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, type));
        }
    }
    
    private void shootMegaStar(List<Bullet> bullets) {
        // Star burst with level-appropriate bullets
        // Unlocks at level 9. Can use: NORMAL, LARGE(3), FAST(4), SPIRAL(6), BOUNCING(7), ACCELERATING(9)
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numPoints = 6 + level / 3; // 6-9 points
        
        for (int point = 0; point < numPoints; point++) {
            double pointAngle = (Math.PI * 2 * point / numPoints);
            
            // Each point shoots multiple bullets outward
            for (int i = 0; i < 3; i++) {
                double spread = 0.4;
                double angle = pointAngle + (i - 2) * (spread / 5);
                double speed = (2.0 + i * 0.5) * speedMultiplier;
                double spawnX = x + Math.cos(angle) * size * 1.5;
                double spawnY = y + Math.sin(angle) * size * 1.5;
                
                // Bouncing bullets only if level >= 7, Large only if level >= 3
                Bullet.BulletType type = Bullet.BulletType.NORMAL;
                if (i <= 1 || i >= 3) {
                    type = level >= 7 ? Bullet.BulletType.BOUNCING : Bullet.BulletType.NORMAL;
                } else {
                    type = level >= 3 ? Bullet.BulletType.LARGE : Bullet.BulletType.NORMAL;
                }
                bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, type));
            }
        }
        
        // Center ring - accelerating at level 9 (when this mega unlocks)
        for (int i = 0; i < 3 + level / 4; i++) {
            double angle = Math.PI * 2 * i / (4 + level / 3);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            Bullet.BulletType type = level >= 9 ? Bullet.BulletType.ACCELERATING : Bullet.BulletType.NORMAL;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, type));
        }
    }
    
    private void shootMegaHex(List<Bullet> bullets, Player player) {
        // Hexagonal formation with level-appropriate bullets
        // Unlocks at level 12. Can use: all except HOMING(13), BOMB(15), NUKE(18)
        double speedMultiplier = Math.min(1.0, 0.4 + (level * 0.12));
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        
        // Six sides of hexagon
        for (int side = 0; side < 6; side++) {
            double sideAngle = (Math.PI / 3 * side) + shootTimer * 0.08;
            int bulletsPerSide = 4 + level / 2;
            
            for (int i = 0; i < bulletsPerSide; i++) {
                double angle = sideAngle + (i - bulletsPerSide / 2.0) * 0.1;
                double speed = (2.5 + Math.sin(i * 0.5)) * speedMultiplier;
                double spawnX = x + Math.cos(angle) * size * 1.5;
                double spawnY = y + Math.sin(angle) * size * 1.5;
                // Wave bullets only if level >= 12
                Bullet.BulletType type = level >= 12 ? Bullet.BulletType.WAVE : Bullet.BulletType.NORMAL;
                bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, type));
            }
        }
        
        // Center bullets aimed at player - grenades only if level >= 10
        for (int i = 0; i < 1 + level / 5; i++) {
            double angle = angleToPlayer + (i - 1) * 0.4;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            Bullet.BulletType type = level >= 10 ? Bullet.BulletType.GRENADE : Bullet.BulletType.NORMAL;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 3 * speedMultiplier, Math.sin(angle) * 3 * speedMultiplier, type));
        }
        
        // Ring of bullets - accelerating only if level >= 9
        for (int i = 0; i < 6 + level / 2; i++) {
            double angle = Math.PI * 2 * i / (10 + level);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            Bullet.BulletType type = level >= 9 ? Bullet.BulletType.ACCELERATING : Bullet.BulletType.NORMAL;
            bullets.add(createBullet(spawnX, spawnY, Math.cos(angle) * 1.8 * speedMultiplier, Math.sin(angle) * 1.8 * speedMultiplier, type));
        }
    }
    
    // ========== END MEGA BOSS SPECIAL ATTACKS ==========
    
    /**
     * Check if a new beam would overlap with any existing beam of the same type.
     * @param position The position of the new beam
     * @param width The width of the new beam
     * @param type The type of beam (VERTICAL or HORIZONTAL)
     * @return true if the beam would overlap with an existing beam
     */
    private boolean wouldBeamOverlap(double position, double width, BeamAttack.BeamType type) {
        for (BeamAttack existingBeam : beamAttacks) {
            if (existingBeam.getType() == type) {
                double existingPos = existingBeam.getPosition();
                double existingWidth = existingBeam.getWidth();
                
                // Check if the beams overlap (with a small buffer for safety)
                double minDistance = (width / 2) + (existingWidth / 2) + 10; // 10px buffer
                if (Math.abs(position - existingPos) < minDistance) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Try to find a non-overlapping position for a beam within the given range.
     * @param minPos Minimum position
     * @param maxPos Maximum position
     * @param width Width of the beam
     * @param type Type of beam
     * @param maxAttempts Maximum number of random attempts
     * @return A valid position, or -1 if no valid position found
     */
    private double findNonOverlappingPosition(double minPos, double maxPos, double width, BeamAttack.BeamType type, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double position = minPos + Math.random() * (maxPos - minPos);
            if (!wouldBeamOverlap(position, width, type)) {
                return position;
            }
        }
        return -1; // No valid position found
    }
    
    private void spawnBeamAttack(int screenWidth, int screenHeight) {
        // Mega bosses have more intense beam patterns
        if (isMegaBoss && Math.random() < 0.35) {
            // 35% chance for mega boss special beam patterns (reduced from 50%)
            int specialBeam = (int)(Math.random() * 3);
            switch (specialBeam) {
                case 0: // Cross pattern beams
                    spawnCrossBeams(screenWidth, screenHeight);
                    return;
                case 1: // Grid pattern beams
                    spawnGridBeams(screenWidth, screenHeight);
                    return;
                case 2: // Rotating beam
                    spawnRotatingBeams(screenWidth, screenHeight);
                    return;
            }
        }
        
        // Randomly choose between vertical and horizontal beams
        boolean isVertical = Math.random() < 0.5;
        
        if (isVertical) {
            // Spawn 1-3 vertical beams depending on level
            int numBeams = 1 + (level >= 14 ? 1 : 0) + (level >= 18 ? 1 : 0);
            for (int i = 0; i < numBeams; i++) {
                double width = 40 + level * 5; // Wider beams at higher levels
                double position = findNonOverlappingPosition(screenWidth * 0.2, screenWidth * 0.8, width, BeamAttack.BeamType.VERTICAL, 10);
                if (position >= 0) {
                    beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.VERTICAL));
                }
            }
        } else {
            // Spawn 1-3 horizontal beams depending on level
            int numBeams = 1 + (level >= 14 ? 1 : 0) + (level >= 18 ? 1 : 0);
            for (int i = 0; i < numBeams; i++) {
                double width = 40 + level * 5; // Wider beams at higher levels
                double position = findNonOverlappingPosition(screenHeight * 0.3, screenHeight * 0.8, width, BeamAttack.BeamType.HORIZONTAL, 10);
                if (position >= 0) {
                    beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.HORIZONTAL));
                }
            }
        }
    }
    
    private void spawnCrossBeams(int screenWidth, int screenHeight) {
        // One vertical and one horizontal beam forming a cross
        double width = 50 + level * 6;
        double verticalX = findNonOverlappingPosition(screenWidth * 0.3, screenWidth * 0.7, width, BeamAttack.BeamType.VERTICAL, 10);
        double horizontalY = findNonOverlappingPosition(screenHeight * 0.35, screenHeight * 0.65, width, BeamAttack.BeamType.HORIZONTAL, 10);
        
        if (verticalX >= 0) {
            beamAttacks.add(new BeamAttack(verticalX, width, BeamAttack.BeamType.VERTICAL));
        }
        if (horizontalY >= 0) {
            beamAttacks.add(new BeamAttack(horizontalY, width, BeamAttack.BeamType.HORIZONTAL));
        }
    }
    
    private void spawnGridBeams(int screenWidth, int screenHeight) {
        // Multiple vertical and horizontal beams forming a grid
        double width = 35 + level * 4;
        int numVertical = 2 + level / 5;
        int numHorizontal = 2 + level / 5;
        
        // Vertical beams - use evenly spaced positions but check for overlaps
        for (int i = 0; i < numVertical; i++) {
            double position = screenWidth * ((i + 1.0) / (numVertical + 1.0));
            if (!wouldBeamOverlap(position, width, BeamAttack.BeamType.VERTICAL)) {
                beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.VERTICAL));
            }
        }
        
        // Horizontal beams - use evenly spaced positions but check for overlaps
        for (int i = 0; i < numHorizontal; i++) {
            double position = screenHeight * ((i + 2.0) / (numHorizontal + 3.0)); // Start lower on screen
            if (!wouldBeamOverlap(position, width, BeamAttack.BeamType.HORIZONTAL)) {
                beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.HORIZONTAL));
            }
        }
    }
    
    private void spawnRotatingBeams(int screenWidth, int screenHeight) {
        // Diagonal beams that create rotating pattern
        double width = 55 + level * 7;
        
        // Create 2-3 diagonal-style beams by combining offset vertical/horizontal
        int numPairs = 2 + (level >= 10 ? 1 : 0);
        for (int i = 0; i < numPairs; i++) {
            double offsetFactor = (i + 1.0) / (numPairs + 1.0);
            double verticalPos = screenWidth * offsetFactor;
            double horizontalPos = screenHeight * (0.3 + offsetFactor * 0.4);
            
            if (!wouldBeamOverlap(verticalPos, width, BeamAttack.BeamType.VERTICAL)) {
                beamAttacks.add(new BeamAttack(verticalPos, width, BeamAttack.BeamType.VERTICAL));
            }
            if (!wouldBeamOverlap(horizontalPos, width, BeamAttack.BeamType.HORIZONTAL)) {
                beamAttacks.add(new BeamAttack(horizontalPos, width, BeamAttack.BeamType.HORIZONTAL));
            }
        }
    }
    
    public List<BeamAttack> getBeamAttacks() {
        return beamAttacks;
    }
    
    public void clearBeamAttacks() {
        beamAttacks.clear();
        // Also reset spiral attack state
        spiralAttackActive = false;
        spiralBulletsSpawned = 0;
        spiralBulletsToSpawn = 0;
        shockwaveActive = false;
    }
    
    /**
     * Force the boss to shoot immediately (for debug showcase).
     */
    public void forceShoot(List<Bullet> bullets, Player player) {
        shoot(bullets, player);
        shootTimer = 0; // Reset timer so next shot follows normal interval
    }
    
    /**
     * Set which pattern types are allowed for this boss.
     * If null, uses the default maxPatterns logic.
     */
    public void setAllowedPatterns(java.util.Set<Integer> patterns) {
        this.allowedPatterns = patterns;
        if (patterns != null && !patterns.isEmpty()) {
            // Pick a random starting pattern from allowed ones
            Integer[] arr = patterns.toArray(new Integer[0]);
            this.patternType = arr[(int)(Math.random() * arr.length)];
        }
    }
    
    /**
     * Check if a pattern type is allowed.
     */
    private boolean isPatternAllowed(int pattern) {
        if (allowedPatterns == null) {
            return pattern < maxPatterns;
        }
        return allowedPatterns.contains(pattern);
    }
    
    /**
     * Get a random allowed pattern type.
     */
    private int getRandomAllowedPattern() {
        if (allowedPatterns == null || allowedPatterns.isEmpty()) {
            return (int)(Math.random() * maxPatterns);
        }
        Integer[] arr = allowedPatterns.toArray(new Integer[0]);
        return arr[(int)(Math.random() * arr.length)];
    }
    
    /**
     * Get the next allowed pattern in sequence.
     */
    private int getNextAllowedPattern() {
        if (allowedPatterns == null || allowedPatterns.isEmpty()) {
            return (patternType + 1) % maxPatterns;
        }
        // Find patterns greater than current, or wrap to smallest
        int next = -1;
        int smallest = Integer.MAX_VALUE;
        for (int p : allowedPatterns) {
            if (p > patternType && (next == -1 || p < next)) {
                next = p;
            }
            if (p < smallest) smallest = p;
        }
        return next != -1 ? next : smallest;
    }
    
    private BufferedImage getCurrentSprite() {
        // Level 28 is the final boss
        if (level == 28) {
            return finalBossSprite;
        }
        
        // Get sprite index based on level
        if (isMegaBoss) {
            // Mega bosses: cycle through Boss Planes 1-8 and Helicopter 1 (9 sprites)
            int megaIndex = ((level / 3) - 1) % 9; // levels 3,6,9,12,15,18 -> indices 0-5
            return megaBossPlaneSprites[megaIndex];
        } else {
            // Mini bosses: cycle through Planes 1-9, 11-15, Helicopters 2-4 (17 sprites)
            int miniIndex = (level - 1) % 17; // distribute across all mini boss levels
            return miniBossPlaneSprites[miniIndex];
        }
    }
    
    private boolean isHelicopter() {
        // Determine if current boss is a helicopter based on sprite
        if (level == 28) return false; // Final boss is not a helicopter
        
        if (isMegaBoss) {
            int megaIndex = ((level / 3) - 1) % 9;
            return megaIndex == 7; // Helicopter 1 is at index 7
        } else {
            int miniIndex = (level - 1) % 17;
            return miniIndex >= 14; // Helicopters 2-4 are at indices 14-16
        }
    }
    
    private int getHelicopterBladeIndex() {
        // Get the appropriate blade sprite for the current helicopter
        if (isMegaBoss) {
            return 0; // Helicopter 1 uses blade 0 (Helecopter Wings.png)
        } else {
            int miniIndex = (level - 1) % 17;
            if (miniIndex == 14) return 0; // Helicopter 2 uses blade 0
            if (miniIndex == 15) return 1; // Helicopter 3 uses blade 1
            if (miniIndex == 16) return 2; // Helicopter 4 uses blade 2
        }
        return 0;
    }
    
    public void draw(Graphics2D g) {
        // Save state instead of g.create() — avoids Graphics2D allocation
        AffineTransform savedTx = g.getTransform();
        Composite savedComp = g.getComposite();
        RenderingHints savedHints = null;
        
        if (Game.enableAntiAliasing) {
            savedHints = g.getRenderingHints();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        
        // Get appropriate sprite and shadow
        BufferedImage sprite;
        BufferedImage shadow;
        boolean isHelicopter = isHelicopter();
        
        if (level == 28) {
            // Final boss
            sprite = finalBossSprite;
            shadow = finalBossShadow;
        } else if (isMegaBoss) {
            // Mega bosses: Boss Planes 1-8 and Helicopter 1
            int megaIndex = ((level / 3) - 1) % 9;
            sprite = megaBossPlaneSprites[megaIndex];
            shadow = megaBossPlaneShadows[megaIndex];
        } else {
            // Mini bosses: Planes 1-9, 11-15, Helicopters 2-4
            int miniIndex = (level - 1) % 17;
            sprite = miniBossPlaneSprites[miniIndex];
            shadow = miniBossPlaneShadows[miniIndex];
        }
        
        if (sprite != null) {
            // Use smooth rotation angle
            // Rotate and draw sprite with shadow
            g.translate(x, y);
            
            // Get native sprite dimensions
            int nativeWidth = sprite.getWidth();
            int nativeHeight = sprite.getHeight();
            
            // Calculate scale factor to fit within size * 2
            double targetSize = size * 2;
            double scaleX = targetSize / nativeWidth;
            double scaleY = targetSize / nativeHeight;
            double scale = Math.min(scaleX, scaleY); // Use smaller scale to prevent stretching
            
            // Apply scale proportionally
            int spriteWidth = (int)(nativeWidth * scale);
            int spriteHeight = (int)(nativeHeight * scale);
            
            // Draw dark glow shadow centered underneath
            if (Game.enableShadows && shadow != null) {
                // Apply z-axis rotation to shadow
                // Helicopters don't roll — they stay level during twirls/dives
                double shadowScaleX;
                if (isHelicopter) {
                    shadowScaleX = 1.0;
                } else if (twirlActive && Math.abs(wobbleRotation) > 0.001) {
                    shadowScaleX = Math.sin(twirlRotation + Math.PI / 2 + wobbleRotation);
                } else if (twirlActive) {
                    shadowScaleX = Math.sin(twirlRotation + Math.PI / 2);
                } else if (Math.abs(wobbleRotation) > 0.001) {
                    shadowScaleX = 0.65 + 0.35 * Math.cos(wobbleRotation);
                } else {
                    shadowScaleX = 1.0;
                }
                
                // Rotate to match object orientation
                g.rotate(rotation - Math.PI / 2);
                
                // Reduced layer count for performance: Low=2, Medium=3, High=5
                int layerCount = Game.shadowQuality == 1 ? 2 : Game.shadowQuality == 2 ? 3 : 5;
                
                // Draw layers from outermost (largest, most transparent) to innermost
                for (int i = 0; i < layerCount; i++) {
                    double t = (layerCount == 1) ? 1.0 : (double)i / (layerCount - 1);
                    double layerScale = SHADOW_MAX_SCALE + (SHADOW_MIN_SCALE - SHADOW_MAX_SCALE) * t;
                    float layerAlpha = SHADOW_MIN_ALPHA + (SHADOW_MAX_ALPHA - SHADOW_MIN_ALPHA) * (float)t;
                    
                    int layerW = (int)(spriteWidth * layerScale);
                    int layerH = (int)(spriteHeight * layerScale);
                    // Stretch the shorter side slightly so the shadow is more rounded
                    if (layerW < layerH) {
                        layerW = (int)(layerW * SHADOW_WIDTH_STRETCH);
                    } else {
                        layerH = (int)(layerH * SHADOW_WIDTH_STRETCH);
                    }
                    
                    if (Math.abs(shadowScaleX - 1.0) > 0.001) {
                        layerW = (int)(layerW * Math.abs(shadowScaleX));
                    }
                    
                    g.setComposite(RenderCache.getAlpha(layerAlpha));
                    g.drawImage(shadow,
                        (int)(-layerW / 2), (int)(-layerH / 2 + SHADOW_GLOW_OFFSET_Y),
                        layerW, layerH, null);
                }
                
                g.setComposite(RenderCache.ALPHA_FULL);
                
                // Reset rotation for sprite
                g.rotate(-(rotation - Math.PI / 2));
            }
            
            // Now rotate for the sprite itself
            g.rotate(rotation - Math.PI / 2); // Subtract 90 degrees to align sprite
            
            // Apply z-axis rotation (wobble and/or twirl)
            // Helicopters don't roll — they stay level during twirls/dives
            double spriteScaleX;
            if (isHelicopter) {
                spriteScaleX = 1.0; // No roll effect for helicopters
            } else if (twirlActive && Math.abs(wobbleRotation) > 0.001) {
                // Both twirl and wobble: combine them
                spriteScaleX = Math.sin(twirlRotation + Math.PI / 2 + wobbleRotation);
            } else if (twirlActive) {
                // Twirl only: offset by PI/2 so it starts and ends at scale 1.0
                spriteScaleX = Math.sin(twirlRotation + Math.PI / 2);
            } else if (Math.abs(wobbleRotation) > 0.001) {
                // Wobble only: keep positive range
                spriteScaleX = 0.65 + 0.35 * Math.cos(wobbleRotation);
            } else {
                // No effect
                spriteScaleX = 1.0;
            }
            
            if (Math.abs(spriteScaleX - 1.0) > 0.001) {
                g.scale(spriteScaleX, 1.0);
            }
            
            g.drawImage(sprite, -spriteWidth/2, -spriteHeight/2, spriteWidth, spriteHeight, null);
            
            // Draw spinning helicopter blades if this is a helicopter
            if (isHelicopter && helicopterBlades[0] != null) {
                int bladeIndex = getHelicopterBladeIndex();
                BufferedImage bladeSprite = helicopterBlades[bladeIndex];
                
                if (bladeSprite != null) {
                    // Save/restore instead of g.create() for blade
                    AffineTransform bladeTx = g.getTransform();
                    g.setComposite(RenderCache.ALPHA_HALF);
                    g.rotate(bladeRotation);
                    int bladeSize = (int)(spriteWidth * 3.6); // 3x bigger blades
                    g.drawImage(bladeSprite, -bladeSize/2, -bladeSize/2, bladeSize, bladeSize, null);
                    g.setTransform(bladeTx);
                }
            }
        } else {
            // Fallback: draw simple polygon with shadow if sprite not loaded
            int sides = Math.min(level + 2, 20);
            Polygon shape = new Polygon();
            for (int i = 0; i < sides; i++) {
                double angle = 2 * Math.PI * i / sides;
                int px = (int)(x + size * Math.cos(angle));
                int py = (int)(y + size * Math.sin(angle));
                shape.addPoint(px, py);
            }
            g.setColor(RenderCache.BLACK_100);
            g.translate(2, 2);
            g.fillPolygon(shape);
            g.translate(-2, -2);
            if (isMegaBoss) {
                g.setColor(RenderCache.BULLET_RED);
            } else {
                g.setColor(RenderCache.BULLET_BLUE);
            }
            g.fillPolygon(shape);
        }
        
        // Restore all state
        g.setTransform(savedTx);
        g.setComposite(savedComp);
        if (savedHints != null) g.setRenderingHints(savedHints);
    }
    
    private String getVehicleName(int lvl) {
        if (lvl % 2 == 1) {
            // Odd levels: Fighter planes
            switch ((lvl - 1) / 2 % 10) {
                case 0: return "SKY VIPER";
                case 1: return "CRIMSON PHANTOM";
                case 2: return "STORM HAWK";
                case 3: return "THUNDER FALCON";
                case 4: return "NIGHT RAPTOR";
                case 5: return "STEEL EAGLE";
                case 6: return "DELTA STRIKER";
                case 7: return "IRON PHOENIX";
                case 8: return "LIGHTNING FURY";
                default: return "SHADOW TALON";
            }
        } else {
            // Even levels: Helicopters
            switch ((lvl / 2 - 1) % 10) {
                case 0: return "ROTOR BEAST";
                case 1: return "BLADE HUNTER";
                case 2: return "IRON HORNET";
                case 3: return "SKY TITAN";
                case 4: return "VENOM BLADE";
                case 5: return "COBRA WING";
                case 6: return "GATOR CHOPPER";
                case 7: return "DARK EAGLE";
                case 8: return "HALO DESTROYER";
                default: return "GUARDIAN PRIME";
            }
        }
    }
    
    // Health and phase management
    public void takeDamage() {
        takeDamage(false);
    }
    
    // Debug method to trigger wobble without damaging
    public void triggerWobble() {
        wobbleVelocity = WOBBLE_STRENGTH * (Math.random() > 0.5 ? 1 : -1);
        System.out.println("DEBUG WOBBLE: wobbleVelocity=" + wobbleVelocity);
    }
    
    // Trigger a 360-degree twirl on z-axis
    public void triggerTwirl() {
        if (!twirlActive) {
            twirlActive = true;
            twirlRotation = 0;
            System.out.println("DEBUG TWIRL: Starting 360-degree rotation");
        }
    }
    
    public void takeDamage(boolean hitByPlayer) {
        if (currentHealth > 0) {
            currentHealth--;
            
            // Wobble is now triggered externally during hit animation, not here
            
            // Calculate new phase based on health lost (every 2 HP lost = 1 phase, capped at 3)
            int newPhase = Math.min((maxHealth - currentHealth) / 2, 3);
            if (newPhase > currentPhase && currentHealth > 0) {
                // Enter phase transition
                currentPhase = newPhase;
                phaseTransitioning = true;
                phaseTransitionTimer = 0;
            }
        }
    }
    
    public int getCurrentHealth() {
        return currentHealth;
    }
    
    public int getMaxHealth() {
        return maxHealth;
    }
    
    public float getHealthPercent() {
        return (float)currentHealth / maxHealth;
    }
    
    public int getCurrentPhase() {
        return currentPhase;
    }
    
    public boolean isPhaseTransitioning() {
        return phaseTransitioning;
    }
    
    public float getPhaseTransitionProgress() {
        if (!phaseTransitioning) return 0f;
        return (float)phaseTransitionTimer / PHASE_TRANSITION_DURATION;
    }
    
    public boolean isDead() {
        return currentHealth <= 0;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    public void setPosition(double x, double y) { 
        this.x = x; 
        this.y = y; 
    }
    public int getSize() { return size; }
    public double getHitboxRadius() { return size * 0.6; } // 60% of sprite size for fitting hitbox
    public boolean isMegaBoss() { return isMegaBoss; }
    public String getVehicleName() { return getVehicleName(level); }
    
    // Attack phase getters
    public boolean isAssaultPhase() { return isAssaultPhase; }
    public boolean isRecoveryPhase() { return !isAssaultPhase; }
    public float getAttackPhaseProgress() { 
        int duration = isAssaultPhase ? assaultPhaseDuration : recoveryPhaseDuration;
        return (float)attackPhaseTimer / duration;
    }
    public int getPhaseFlashTimer() { return phaseFlashTimer; }
    public boolean justChangedPhase() { return justChangedPhase; }
    
    // Shockwave getters
    public boolean isShockwaveActive() { return shockwaveActive; }
    public double getShockwaveRadius() { return shockwaveRadius; }
    public double getShockwaveAngle() { return shockwaveAngle; }
    public boolean hasShockwaveHitPlayer() { return shockwaveHasHitPlayer; }
    public void setShockwaveHitPlayer() { shockwaveHasHitPlayer = true; }
    public double getShockwaveKnockback() { return SHOCKWAVE_KNOCKBACK; }
    
    // Debug showcase mode methods
    /**
     * Force the boss to use only a specific attack pattern.
     * @param patternId The pattern ID (0-14), or -1 for normal behavior
     */
    public void setForcedPatternType(int patternId) {
        this.forcedPatternType = patternId;
    }
    
    /**
     * Force beam attack mode for debug showcase
     */
    public void setForceBeamAttack(boolean force) {
        this.forceBeamAttack = force;
        if (force) {
            this.beamAttackTimer = 0; // Attack immediately
        }
    }
    
    /**
     * Force shockwave attack mode for debug showcase
     */
    public void setForceShockwave(boolean force) {
        this.forceShockwave = force;
    }
    
    /**
     * Force twirl attack mode for debug showcase
     */
    public void setForceTwirlAttack(boolean force) {
        this.forceTwirlAttack = force;
    }
    
    /**
     * Disable all bullet shooting (for beam/shockwave/twirl showcase)
     */
    public void setDisableBulletShooting(boolean disable) {
        this.disableBulletShooting = disable;
    }
    
    /**
     * Disable beam attacks during showcase
     */
    public void setDisableBeamAttacks(boolean disable) {
        this.disableBeamAttacks = disable;
    }
    
    /**
     * Disable shockwave during showcase
     */
    public void setDisableShockwave(boolean disable) {
        this.disableShockwave = disable;
    }
    
    /**
     * Disable twirl during showcase
     */
    public void setDisableTwirl(boolean disable) {
        this.disableTwirl = disable;
    }
    
    /**
     * Enable debug slow mode for screenshot taking
     */
    public void setDebugSlowMode(boolean slow) {
        this.debugSlowMode = slow;
        if (slow) {
            this.shootInterval = DEBUG_SLOW_SHOOT_INTERVAL;
        }
    }
    
    /**
     * Make the boss stay in place (no movement)
     */
    public void setStayStationary(boolean stationary) {
        this.stayStationary = stationary;
    }
    
    /**
     * Force a specific mega attack for debug showcase
     * @param megaAttackId 0=MegaBurst, 1=MegaSpiral, 2=MegaCross, 3=MegaStar, 4=MegaHex, -1=normal
     */
    public void setForceMegaAttack(int megaAttackId) {
        this.forcedMegaAttack = megaAttackId;
    }
    
    // Get money reward based on boss type
    public int getMoneyReward() {
        if (isMegaBoss) {
            return 700 + (level * 250); // Mega bosses give much more money
        } else {
            return 150 + (level * 70); // Mini bosses give better rewards
        }
    }
    
    // Getters for resume state
    public int getLevel() { return level; }
    public double getVX() { return vx; }
    public double getVY() { return vy; }
    public double getShootTimer() { return shootTimer; }
    public int getPatternType() { return patternType; }
    public double getSpiralRotation() { return spiralRotation; }
    
    // Setters for resume state restoration
    public void setVelocity(double vx, double vy) { this.vx = vx; this.vy = vy; }
    public void setShootTimer(double timer) { this.shootTimer = timer; }
    public void setSpiralRotation(double rotation) { this.spiralRotation = rotation; }
}
