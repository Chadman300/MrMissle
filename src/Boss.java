import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class Boss {
    private double x, y;
    private double vx, vy; // Velocity
    private double ax, ay; // Acceleration
    private double rotation; // Current rotation angle
    private double targetRotation; // Target rotation angle
    private double angularVelocity; // Current rotation speed
    private int level;
    private boolean isMegaBoss; // Every 3rd boss is a mega boss
    private int size; // Dynamic size based on boss type
    private static final int BASE_SIZE = 100;
    private static final double MAX_SPEED = 2.5; // Maximum movement speed
    private static final double ACCELERATION = 0.15; // How fast to speed up
    private static final double FRICTION = 0.92; // How fast to slow down (0.92 = 8% friction)
    private static final double ANGULAR_ACCELERATION = 0.015; // How fast to turn (reduced from 0.03 for smoother rotation)
    private static final double ANGULAR_FRICTION = 0.85; // Rotation damping (increased from 0.7 for smoother rotation)
    
    // Sun angle for directional shadows (top-left, about 135 degrees)
    private static final double SUN_ANGLE = Math.PI * 0.75; // 135 degrees
    private static final double SHADOW_DISTANCE = 12; // Shadow distance from sprite
    private static final double SHADOW_SCALE = 1.0; // Shadow is 1:1 scale with sprite
    
    private int shootTimer;
    private int shootInterval;
    private int patternType;
    private int maxPatterns; // Maximum attack patterns unlocked
    private double targetX, targetY; // Target position for smooth movement
    private int moveTimer; // Timer to pick new target
    private int beamAttackTimer; // Timer for beam attacks
    private int beamAttackInterval; // How often to spawn beam attacks
    private List<BeamAttack> beamAttacks; // Active beam attacks
    
    // Multiple sprite variants for planes and helicopters
    private static BufferedImage[] miniBossPlaneSprites = new BufferedImage[8];
    private static BufferedImage[] megaBossPlaneSprites = new BufferedImage[8];
    private static BufferedImage[] helicopterSprites = new BufferedImage[8];
    private static BufferedImage[] miniBossPlaneShadows = new BufferedImage[8];
    private static BufferedImage[] megaBossPlaneShadows = new BufferedImage[8];
    private static BufferedImage[] helicopterShadows = new BufferedImage[8];
    private static BufferedImage[] helicopterBlades = new BufferedImage[3]; // Rotor blade sprites
    private static boolean spritesLoaded = false;
    
    // Animation for helicopter blades
    private double bladeRotation = 0;
    private static final double BLADE_ROTATION_SPEED = 0.2; // Radians per frame (reduced from 0.5)
    
    // Sound manager for effects
    private SoundManager soundManager;
    
    // Boss phases
    private int maxHealth;
    private int currentHealth;
    private int currentPhase; // 0-3 for phases (based on health: 100%, 75%, 50%, 25%)
    private boolean phaseTransitioning;
    private int phaseTransitionTimer;
    private static final int PHASE_TRANSITION_DURATION = 90; // 1.5 seconds
    
    // Attack rhythm phases (Assault vs Recovery)
    private boolean isAssaultPhase = true; // true = aggressive, false = recovery
    private int attackPhaseTimer = 0;
    private int assaultPhaseDuration = 300; // 5 seconds of assault (at 60fps)
    private int recoveryPhaseDuration = 180; // 3 seconds of recovery
    private double assaultSpeedMultiplier = 1.8; // Attack 80% faster during assault
    private double recoverySpeedMultiplier = 0.4; // Attack 60% slower during recovery
    private int phaseFlashTimer = 0; // Visual flash when phase changes
    private boolean justChangedPhase = false; // For visual effects
    
    public Boss(double x, double y, int level) {
        this(x, y, level, null);
    }
    
    public Boss(double x, double y, int level, SoundManager soundManager) {
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
        this.maxHealth = isMegaBoss ? 3 : 2; // Mega bosses have 3 hits, mini bosses have 2 hits
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
            this.assaultPhaseDuration += 30; // +0.5 second assault (reduced from +60)
            this.recoveryPhaseDuration -= 15; // -0.25 second recovery (reduced from -30)
            this.assaultSpeedMultiplier = 1.95; // Moderately faster attacks (reduced from 2.2)
        }
        
        loadSprites();
    }
    
    private BufferedImage rotateImage180(BufferedImage img) {
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
        if (spritesLoaded) return;
        try {
            // Load mini boss plane variants (Regular Planes)
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 7.png", miniBossPlaneSprites, 0);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 8.png", miniBossPlaneSprites, 1);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 9.png", miniBossPlaneSprites, 2);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 10.png", miniBossPlaneSprites, 3);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 11.png", miniBossPlaneSprites, 4);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 12.png", miniBossPlaneSprites, 5);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 13.png", miniBossPlaneSprites, 6);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 14.png", miniBossPlaneSprites, 7);
            
            // Load mini boss plane shadows
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 7 Shadow.png", miniBossPlaneShadows, 0);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 8 Shadow.png", miniBossPlaneShadows, 1);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 9 Shadow.png", miniBossPlaneShadows, 2);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 10 Shadow.png", miniBossPlaneShadows, 3);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 11 Shadow.png", miniBossPlaneShadows, 4);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 12 Shadow.png", miniBossPlaneShadows, 5);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 13 Shadow.png", miniBossPlaneShadows, 6);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 14 Shadow.png", miniBossPlaneShadows, 7);
            
            // Load mega boss plane variants (Boss Planes)
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 1.png", megaBossPlaneSprites, 0);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 2.png", megaBossPlaneSprites, 1);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 3.png", megaBossPlaneSprites, 2);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 4.png", megaBossPlaneSprites, 3);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 5.png", megaBossPlaneSprites, 4);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 6.png", megaBossPlaneSprites, 5);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 7.png", megaBossPlaneSprites, 6);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 8.png", megaBossPlaneSprites, 7);
            
            // Load mega boss plane shadows
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 1 Shadow.png", megaBossPlaneShadows, 0);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 2 Shadow.png", megaBossPlaneShadows, 1);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 3 Shadow.png", megaBossPlaneShadows, 2);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 4 Shadow.png", megaBossPlaneShadows, 3);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 5 Shadow.png", megaBossPlaneShadows, 4);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 6 Shadow.png", megaBossPlaneShadows, 5);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 7 Shadow.png", megaBossPlaneShadows, 6);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 8 Shadow.png", megaBossPlaneShadows, 7);
            
            // Load helicopter variants
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 1.png", helicopterSprites, 0);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 2.png", helicopterSprites, 1);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3.png", helicopterSprites, 2);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4.png", helicopterSprites, 3);
            helicopterSprites[4] = helicopterSprites[0]; // Reuse
            helicopterSprites[5] = helicopterSprites[1]; // Reuse
            helicopterSprites[6] = helicopterSprites[2]; // Reuse
            helicopterSprites[7] = helicopterSprites[3]; // Reuse
            
            // Load helicopter shadows
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 1 Shadow.png", helicopterShadows, 0);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 2 Shadow.png", helicopterShadows, 1);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3 Shadow.png", helicopterShadows, 2);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4 Shadow.png", helicopterShadows, 3);
            helicopterShadows[4] = helicopterShadows[0]; // Reuse
            helicopterShadows[5] = helicopterShadows[1]; // Reuse
            helicopterShadows[6] = helicopterShadows[0]; // Reuse
            helicopterShadows[7] = helicopterShadows[1]; // Reuse
            
            // Load helicopter blade sprites
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter Wings.png", helicopterBlades, 0);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3 Wings.png", helicopterBlades, 1);
            loadBossSpriteWithPath("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4 Wings.png", helicopterBlades, 2);
            
            spritesLoaded = true;
        } catch (IOException e) {
            System.err.println("Failed to load boss sprites: " + e.getMessage());
            // Don't set spritesLoaded to false - allow fallback rendering
        }
    }
    
    private void loadBossSpriteWithPath(String path, BufferedImage[] array, int index) throws IOException {
        try {
            array[index] = rotateImage180(ImageIO.read(new File(path)));
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
        if (level % 2 == 0) {
            bladeRotation += BLADE_ROTATION_SPEED * deltaTime;
            if (bladeRotation > Math.PI * 2) {
                bladeRotation -= Math.PI * 2;
            }
        }
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight, double deltaTime, List<Particle> particles) {
        // Smooth movement to target position
        moveTimer += deltaTime;
        
        // Pick a new target every 120-180 frames (2-3 seconds) for longer paths
        if (moveTimer >= 120 + Math.random() * 60) {
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
            double angle = Math.random() * Math.PI * 2;
            
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
        
        // Acceleration-based movement
        if (distance > 10) { // Dead zone to prevent jittering
            // Calculate desired acceleration direction (reduced scaling)
            double accelStrength = ACCELERATION * (1.0 + level * 0.025);
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
        
        // Apply friction
        vx *= FRICTION;
        vy *= FRICTION;
        
        // Limit max speed (reduced scaling)
        double speed = Math.sqrt(vx * vx + vy * vy);
        double maxSpeed = MAX_SPEED * (1.0 + level * 0.05);
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
        
        // Apply angular friction
        angularVelocity *= ANGULAR_FRICTION;
        
        // Apply angular velocity to rotation
        rotation += angularVelocity * deltaTime;
        
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
            
            // Calculate wing tip positions (perpendicular to rotation)
            double perpAngle = rotation + Math.PI / 2; // Perpendicular to facing direction
            
            // Left wing tip
            double leftWingX = x + Math.cos(perpAngle) * wingSpan;
            double leftWingY = y + Math.sin(perpAngle) * wingSpan;
            
            // Right wing tip
            double rightWingX = x - Math.cos(perpAngle) * wingSpan;
            double rightWingY = y - Math.sin(perpAngle) * wingSpan;
            
            // Larger trails for mega bosses
            int trailSize = isMegaBoss ? 8 : 4;
            int trailSizeVariation = isMegaBoss ? 6 : 3;
            
            // Spawn trail particles at wing tips (every few frames)
            if (Math.random() < 0.3 * deltaTime) {
                // Left wing trail
                particles.add(new Particle(
                    leftWingX,
                    leftWingY,
                    -vx * 0.3 + (Math.random() - 0.5) * 0.5,
                    -vy * 0.3 + (Math.random() - 0.5) * 0.5,
                    new Color(200, 220, 255, 180), // Light blue/white
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
                    new Color(200, 220, 255, 180), // Light blue/white
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
            
            // When entering assault phase, immediately switch to a new pattern
            if (isAssaultPhase) {
                patternType = (int)(Math.random() * maxPatterns);
            }
        }
        
        // Calculate attack speed based on current phase
        double attackPhaseMultiplier = isAssaultPhase ? assaultSpeedMultiplier : recoverySpeedMultiplier;
        
        // Shooting pattern (scaled by delta time) - faster in later phases
        double phaseSpeedMultiplier = 1.0 + (currentPhase * 0.15); // 15% faster per phase
        shootTimer += deltaTime * phaseSpeedMultiplier * attackPhaseMultiplier;
        if (shootTimer >= shootInterval) {
            shootTimer = 0;
            shoot(bullets, player);
        }
        
        // Beam attacks (at higher levels - starting at level 4)
        if (level >= 4) {
            beamAttackTimer += deltaTime;
            if (beamAttackTimer >= beamAttackInterval) {
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
        int bulletCountBefore = bullets.size();
        
        // Mega bosses have special attack patterns
        if (isMegaBoss && Math.random() < 0.15) {
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
        
        // Cycle through unlocked patterns only
        patternType = (patternType + 1) % maxPatterns;
        
        switch (patternType % 15) {
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
            case 9: // Splitting bullets
                shootSplittingBullets(bullets);
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
        }
        
        // Play boss shoot sound if bullets were actually spawned
        if (soundManager != null && bullets.size() > bulletCountBefore) {
            soundManager.playSound(SoundManager.Sound.BOSS_SHOOT, 0.25f);
        }
    }
    
    private void shootSpiral(List<Bullet> bullets) {
        int numBullets = 12 + level * 2; // Increased from 8 + level
        double angleOffset = shootTimer * 0.1;
        double speedMultiplier = Math.min(1.0, 0.4 + (level * 0.12)); // Starts at 40%, reaches 100% at level 5
        for (int i = 0; i < numBullets; i++) {
            double angle = (Math.PI * 2 * i / numBullets) + angleOffset;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 3 * speedMultiplier, Math.sin(angle) * 3 * speedMultiplier));
        }
    }
    
    private void shootCircle(List<Bullet> bullets, int numBullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier));
        }
    }
    
    private void shootAtPlayer(List<Bullet> bullets, Player player, int spread) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -spread; i <= spread; i++) {
            double angle = angleToPlayer + (i * 0.2);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 4 * speedMultiplier, Math.sin(angle) * 4 * speedMultiplier));
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
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
    }
    
    private void shootRandom(List<Bullet> bullets, int numBullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = (2 + Math.random() * 2) * speedMultiplier;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
    }
    
    private void shootFast(List<Bullet> bullets, Player player) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = 0; i < 5 + level / 2; i++) { // Slower increase (was level)
            double angle = angleToPlayer + (Math.random() - 0.5) * 0.5;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 6 * speedMultiplier, Math.sin(angle) * 6 * speedMultiplier, Bullet.BulletType.FAST));
        }
    }
    
    private void shootLarge(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 5 + level / 2; // Slower increase
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, Bullet.BulletType.LARGE));
        }
    }
    
    private void shootMixed(List<Bullet> bullets, Player player) {
        // Combination attack with different bullet types
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        
        // Homing bullets
        for (int i = 0; i < 3; i++) { // Increased from 1
            double angle = angleToPlayer + (i - 1) * 0.3;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.HOMING));
        }
        
        // Circle of bouncing bullets
        if (level >= 3) {
            for (int i = 0; i < 8; i++) { // Increased from 4
                double angle = Math.PI * 2 * i / 8; // Updated divisor
                double spawnX = x + Math.cos(angle) * size * 1.5;
                double spawnY = y + Math.sin(angle) * size * 1.5;
                bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 3 * speedMultiplier, Math.sin(angle) * 3 * speedMultiplier, Bullet.BulletType.BOUNCING));
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
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2 * speedMultiplier, Math.sin(angle) * 2 * speedMultiplier, Bullet.BulletType.SPIRAL));
        }
    }
    
    private void shootSplittingBullets(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 4 + level / 2; // Slower increase
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.SPLITTING));
        }
    }
    
    private void shootAcceleratingBullets(List<Bullet> bullets, Player player) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -2; i <= 2; i++) { // Increased from -1 to 1 (now 5 bullets instead of 3)
            double angle = angleToPlayer + i * 0.3;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, Bullet.BulletType.ACCELERATING));
        }
    }
    
    private void shootWaveBullets(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 8 + level / 2; // Slower increase
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI / 4 + (Math.PI / 2 * i / numBullets);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.WAVE));
        }
    }
    
    private void shootBombs(List<Bullet> bullets) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        int numBullets = 3 + level / 2; // Increased from 2 + level / 3
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2.0 * speedMultiplier, Math.sin(angle) * 2.0 * speedMultiplier, Bullet.BulletType.BOMB));
        }
    }
    
    private void shootGrenades(List<Bullet> bullets, Player player) {
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        int numBullets = 2 + (level >= 5 ? 1 : 0); // Increased from 1 + (level >= 5 ? 1 : 0)
        for (int i = 0; i < numBullets; i++) {
            double angle = angleToPlayer + (i - numBullets/2.0) * 0.3;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.GRENADE));
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
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, Bullet.BulletType.NUKE));
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
            
            // Mix of bullet types for chaos
            Bullet.BulletType type;
            double rand = Math.random();
            if (rand < 0.3) {
                type = Bullet.BulletType.FAST;
            } else if (rand < 0.5) {
                type = Bullet.BulletType.HOMING;
            } else if (rand < 0.7) {
                type = Bullet.BulletType.ACCELERATING;
            } else {
                type = Bullet.BulletType.NORMAL;
            }
            
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, type));
        }
    }
    
    private void shootMegaSpiral(List<Bullet> bullets) {
        // Layered spiral with multiple speeds and types
        double speedMultiplier = Math.min(1.3, 0.4 + (level * 0.15)); // Increased speed scaling
        double angleOffset = shootTimer * 0.15;
        
        // Three layers of spirals at different speeds
        int[] layers = {8, 12, 16};
        double[] speeds = {2.0, 3.0, 4.0};
        Bullet.BulletType[] types = {Bullet.BulletType.NORMAL, Bullet.BulletType.SPIRAL, Bullet.BulletType.WAVE};
        
        for (int layer = 0; layer < 3; layer++) {
            int numBullets = layers[layer] + level;
            double layerOffset = angleOffset * (1 + layer * 0.3);
            
            for (int i = 0; i < numBullets; i++) {
                double angle = (Math.PI * 2 * i / numBullets) + layerOffset;
                double speed = speeds[layer] * speedMultiplier;
                double spawnX = x + Math.cos(angle) * size * 1.5;
                double spawnY = y + Math.sin(angle) * size * 1.5;
                bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, types[layer]));
            }
        }
    }
    
    private void shootMegaCross(List<Bullet> bullets, Player player) {
        // Cross pattern with rotating arms + homing center
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
                
                Bullet.BulletType type = (i % 3 == 0) ? Bullet.BulletType.LARGE : Bullet.BulletType.NORMAL;
                bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, type));
            }
        }
        
        // Center cluster of homing bullets
        for (int i = 0; i < 3 + level / 3; i++) {
            double angle = angleToPlayer + (Math.random() - 0.5) * 0.8;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 2.5 * speedMultiplier, Math.sin(angle) * 2.5 * speedMultiplier, Bullet.BulletType.HOMING));
        }
    }
    
    private void shootMegaStar(List<Bullet> bullets) {
        // Star burst with splitting bullets
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
                
                // Outer bullets split, inner bullets are large
                Bullet.BulletType type = (i <= 1 || i >= 3) ? Bullet.BulletType.SPLITTING : Bullet.BulletType.LARGE;
                bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, type));
            }
        }
        
        // Center ring of bombs
        for (int i = 0; i < 3 + level / 4; i++) {
            double angle = Math.PI * 2 * i / (4 + level / 3);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 1.5 * speedMultiplier, Math.sin(angle) * 1.5 * speedMultiplier, Bullet.BulletType.BOMB));
        }
    }
    
    private void shootMegaHex(List<Bullet> bullets, Player player) {
        // Hexagonal formation with wave bullets + grenades
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
                bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed, Bullet.BulletType.WAVE));
            }
        }
        
        // Grenades aimed at player
        for (int i = 0; i < 2 + level / 3; i++) {
            double angle = angleToPlayer + (i - 1) * 0.4;
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 3 * speedMultiplier, Math.sin(angle) * 3 * speedMultiplier, Bullet.BulletType.GRENADE));
        }
        
        // Ring of accelerating bullets
        for (int i = 0; i < 6 + level / 2; i++) {
            double angle = Math.PI * 2 * i / (10 + level);
            double spawnX = x + Math.cos(angle) * size * 1.5;
            double spawnY = y + Math.sin(angle) * size * 1.5;
            bullets.add(new Bullet(spawnX, spawnY, Math.cos(angle) * 1.8 * speedMultiplier, Math.sin(angle) * 1.8 * speedMultiplier, Bullet.BulletType.ACCELERATING));
        }
    }
    
    // ========== END MEGA BOSS SPECIAL ATTACKS ==========
    
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
            int numBeams = 1 + (level >= 5 ? 1 : 0) + (level >= 8 ? 1 : 0);
            for (int i = 0; i < numBeams; i++) {
                double position = screenWidth * (0.2 + Math.random() * 0.6);
                double width = 40 + level * 5; // Wider beams at higher levels
                beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.VERTICAL));
            }
        } else {
            // Spawn 1-3 horizontal beams depending on level
            int numBeams = 1 + (level >= 5 ? 1 : 0) + (level >= 8 ? 1 : 0);
            for (int i = 0; i < numBeams; i++) {
                double position = screenHeight * (0.3 + Math.random() * 0.5);
                double width = 40 + level * 5; // Wider beams at higher levels
                beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.HORIZONTAL));
            }
        }
    }
    
    private void spawnCrossBeams(int screenWidth, int screenHeight) {
        // One vertical and one horizontal beam forming a cross
        double width = 50 + level * 6;
        double verticalX = screenWidth * (0.3 + Math.random() * 0.4);
        double horizontalY = screenHeight * (0.35 + Math.random() * 0.3);
        
        beamAttacks.add(new BeamAttack(verticalX, width, BeamAttack.BeamType.VERTICAL));
        beamAttacks.add(new BeamAttack(horizontalY, width, BeamAttack.BeamType.HORIZONTAL));
    }
    
    private void spawnGridBeams(int screenWidth, int screenHeight) {
        // Multiple vertical and horizontal beams forming a grid
        double width = 35 + level * 4;
        int numVertical = 2 + level / 5;
        int numHorizontal = 2 + level / 5;
        
        // Vertical beams
        for (int i = 0; i < numVertical; i++) {
            double position = screenWidth * ((i + 1.0) / (numVertical + 1.0));
            beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.VERTICAL));
        }
        
        // Horizontal beams
        for (int i = 0; i < numHorizontal; i++) {
            double position = screenHeight * ((i + 2.0) / (numHorizontal + 3.0)); // Start lower on screen
            beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.HORIZONTAL));
        }
    }
    
    private void spawnRotatingBeams(int screenWidth, int screenHeight) {
        // Diagonal beams that create rotating pattern
        double width = 55 + level * 7;
        
        // Create 2-3 diagonal-style beams by combining offset vertical/horizontal
        int numPairs = 2 + (level >= 10 ? 1 : 0);
        for (int i = 0; i < numPairs; i++) {
            double offsetFactor = (i + 1.0) / (numPairs + 1.0);
            beamAttacks.add(new BeamAttack(screenWidth * offsetFactor, width, BeamAttack.BeamType.VERTICAL));
            beamAttacks.add(new BeamAttack(screenHeight * (0.3 + offsetFactor * 0.4), width, BeamAttack.BeamType.HORIZONTAL));
        }
    }
    
    public List<BeamAttack> getBeamAttacks() {
        return beamAttacks;
    }
    
    private BufferedImage getCurrentSprite() {
        // Get the currently displayed sprite based on level
        int spriteIndex = ((level - 1) / 2) % 8;
        
        if (level % 2 == 0) {
            // Helicopter
            return helicopterSprites[spriteIndex];
        } else if (isMegaBoss) {
            // Mega boss plane
            return megaBossPlaneSprites[spriteIndex];
        } else {
            // Mini boss plane
            return miniBossPlaneSprites[spriteIndex];
        }
    }
    
    public void draw(Graphics2D g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Odd levels = fighter planes, Even levels = helicopters
        // Select sprite variant based on level (cycles through 8 variants)
        int spriteIndex = ((level - 1) / 2) % 8;
        BufferedImage sprite;
        BufferedImage shadow;
        
        if (level % 2 == 0) {
            // Even levels: Helicopters (always mega bosses)
            sprite = helicopterSprites[spriteIndex];
            shadow = helicopterShadows[spriteIndex];
        } else {
            // Odd levels: Planes (can be mini or mega)
            if (isMegaBoss) {
                sprite = megaBossPlaneSprites[spriteIndex];
                shadow = megaBossPlaneShadows[spriteIndex];
            } else {
                sprite = miniBossPlaneSprites[spriteIndex];
                shadow = miniBossPlaneShadows[spriteIndex];
            }
        }
        
        if (sprite != null) {
            // Use smooth rotation angle
            // Rotate and draw sprite with shadow
            g2d.translate(x, y);
            
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
            
            // Draw shadow sprite with directional offset in world space (before rotation)
            if (Game.enableShadows && shadow != null) {
                // Calculate shadow offset in world space based on sun angle and object rotation
                // Shadow appears to move as object rotates relative to fixed sun direction
                double relativeAngle = SUN_ANGLE - (rotation - Math.PI / 2);
                double shadowOffsetX = Math.cos(relativeAngle) * SHADOW_DISTANCE;
                double shadowOffsetY = Math.sin(relativeAngle) * SHADOW_DISTANCE;
                
                // Shadow is larger and more transparent
                int shadowWidth = (int)(spriteWidth * SHADOW_SCALE);
                int shadowHeight = (int)(spriteHeight * SHADOW_SCALE);
                
                // Rotate to match object orientation
                g2d.rotate(rotation - Math.PI / 2);
                
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g2d.drawImage(shadow, 
                    (int)(-shadowWidth/2 + shadowOffsetX), 
                    (int)(-shadowHeight/2 + shadowOffsetY), 
                    shadowWidth, shadowHeight, null);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                
                // Reset rotation for sprite
                g2d.rotate(-(rotation - Math.PI / 2));
            }
            
            // Now rotate for the sprite itself
            g2d.rotate(rotation - Math.PI / 2); // Subtract 90 degrees to align sprite
            
            boolean isHelicopter = (level % 2 == 0);
            // Draw sprite
            g2d.drawImage(sprite, -spriteWidth/2, -spriteHeight/2, spriteWidth, spriteHeight, null);
            
            // Draw spinning helicopter blades if this is a helicopter
            if (isHelicopter && helicopterBlades[0] != null) {
                // Choose blade sprite based on helicopter variant
                int bladeIndex = Math.min(spriteIndex / 3, 2); // 0-2, 3-5, 6-7 map to blade 0, 1, 2
                BufferedImage bladeSprite = helicopterBlades[bladeIndex];
                
                if (bladeSprite != null) {
                    Graphics2D bladeG2d = (Graphics2D) g2d.create();
                    bladeG2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)); // Add transparency
                    bladeG2d.rotate(bladeRotation); // Apply blade rotation
                    int bladeSize = (int)(spriteWidth * 1.2); // Blades slightly larger than body
                    bladeG2d.drawImage(bladeSprite, -bladeSize/2, -bladeSize/2, bladeSize, bladeSize, null);
                    bladeG2d.dispose();
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
            // Draw shadow
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.translate(2, 2);
            g2d.fillPolygon(shape);
            g2d.translate(-2, -2);
            // Draw shape - mega bosses have red tint
            if (isMegaBoss) {
                g2d.setColor(new Color(255, 50, 50)); // Red for mega boss
            } else {
                g2d.setColor(new Color(0, 100, 255)); // Blue for mini boss
            }
            g2d.fillPolygon(shape);
        }
        
        g2d.dispose();
    }
    
    private String getVehicleName(int lvl) {
        if (lvl % 2 == 1) {
            // Odd levels: Fighter planes
            switch ((lvl - 1) / 2 % 10) {
                case 0: return "[PLANE] MIG-15";
                case 1: return "[PLANE] MIG-21";
                case 2: return "[PLANE] MIG-29";
                case 3: return "[PLANE] SU-27";
                case 4: return "[PLANE] SU-57";
                case 5: return "[PLANE] F-86 SABRE";
                case 6: return "[PLANE] F-4 PHANTOM";
                case 7: return "[PLANE] F-15 EAGLE";
                case 8: return "[PLANE] F-22 RAPTOR";
                default: return "[PLANE] F-35 LIGHTNING";
            }
        } else {
            // Even levels: Helicopters
            switch ((lvl / 2 - 1) % 10) {
                case 0: return "[HELI] UH-1 HUEY";
                case 1: return "[HELI] AH-64 APACHE";
                case 2: return "[HELI] MI-24 HIND";
                case 3: return "[HELI] CH-47 CHINOOK";
                case 4: return "[HELI] MI-28 HAVOC";
                case 5: return "[HELI] AH-1 COBRA";
                case 6: return "[HELI] KA-52 ALLIGATOR";
                case 7: return "[HELI] UH-60 BLACK HAWK";
                case 8: return "[HELI] MI-26 HALO";
                default: return "[HELI] AH-64E GUARDIAN";
            }
        }
    }
    
    // Health and phase management
    public void takeDamage() {
        if (currentHealth > 0) {
            currentHealth--;
            
            // Calculate new phase based on health percentage
            int newPhase = maxHealth - currentHealth;
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
    
    // Get money reward based on boss type
    public int getMoneyReward() {
        if (isMegaBoss) {
            return 700 + (level * 250); // Mega bosses give much more money
        } else {
            return 150 + (level * 70); // Mini bosses give better rewards
        }
    }
}
