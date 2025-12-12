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
    private static final double ANGULAR_ACCELERATION = 0.08; // How fast to turn
    private static final double ANGULAR_FRICTION = 0.85; // Rotation damping
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
    private static final double BLADE_ROTATION_SPEED = 0.5; // Radians per frame
    
    public Boss(double x, double y, int level) {
        this.x = x;
        this.y = y;
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
        
        // Size: mega bosses are 150% size, mini bosses are 70% size
        this.size = isMegaBoss ? (int)(BASE_SIZE * 1.5) : (int)(BASE_SIZE * 0.7);
        
        // Attack patterns unlock with each mega boss
        // Mega boss 3 unlocks 3 patterns, mega boss 6 unlocks 6, etc.
        int megaBossCount = (level + 2) / 3; // How many mega bosses have appeared (including this one)
        this.maxPatterns = Math.min(megaBossCount * 3, 12); // Cap at 12 patterns
        
        this.shootTimer = 0;
        this.shootInterval = Math.max(20, 60 - level * 5); // Faster shooting at higher levels
        // Start with random pattern from available pool
        this.patternType = (int)(Math.random() * maxPatterns);
        // Start with current position as target
        this.targetX = x;
        this.targetY = y;
        this.moveTimer = 0;
        this.beamAttacks = new ArrayList<>();
        this.beamAttackTimer = 120 + (int)(Math.random() * 60); // First beam after 2-3 seconds
        this.beamAttackInterval = Math.max(180, 300 - level * 10); // More frequent at higher levels
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
            miniBossPlaneSprites[0] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 7.png")));
            miniBossPlaneSprites[1] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 8.png")));
            miniBossPlaneSprites[2] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 9.png")));
            miniBossPlaneSprites[3] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 10.png")));
            miniBossPlaneSprites[4] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 11.png")));
            miniBossPlaneSprites[5] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 12.png")));
            miniBossPlaneSprites[6] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 13.png")));
            miniBossPlaneSprites[7] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 14.png")));
            
            // Load mini boss plane shadows
            miniBossPlaneShadows[0] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 7 Shadow.png")));
            miniBossPlaneShadows[1] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 8 Shadow.png")));
            miniBossPlaneShadows[2] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 9 Shadow.png")));
            miniBossPlaneShadows[3] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 10 Shadow.png")));
            miniBossPlaneShadows[4] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 11 Shadow.png")));
            miniBossPlaneShadows[5] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 12 Shadow.png")));
            miniBossPlaneShadows[6] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 13 Shadow.png")));
            miniBossPlaneShadows[7] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Regular Planes\\High Res\\Plane 14 Shadow.png")));
            
            // Load mega boss plane variants (Boss Planes)
            megaBossPlaneSprites[0] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 1.png")));
            megaBossPlaneSprites[1] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 2.png")));
            megaBossPlaneSprites[2] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 3.png")));
            megaBossPlaneSprites[3] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 4.png")));
            megaBossPlaneSprites[4] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 5.png")));
            megaBossPlaneSprites[5] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 6.png")));
            megaBossPlaneSprites[6] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 7.png")));
            megaBossPlaneSprites[7] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 8.png")));
            
            // Load mega boss plane shadows
            megaBossPlaneShadows[0] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 1 Shadow.png")));
            megaBossPlaneShadows[1] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 2 Shadow.png")));
            megaBossPlaneShadows[2] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 3 Shadow.png")));
            megaBossPlaneShadows[3] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 4 Shadow.png")));
            megaBossPlaneShadows[4] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 5 Shadow.png")));
            megaBossPlaneShadows[5] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 6 Shadow.png")));
            megaBossPlaneShadows[6] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 7 Shadow.png")));
            megaBossPlaneShadows[7] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Boss Planes\\Boss Plane 8 Shadow.png")));
            
            // Load helicopter variants
            helicopterSprites[0] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 1.png")));
            helicopterSprites[1] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 2.png")));
            helicopterSprites[2] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3.png")));
            helicopterSprites[3] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4.png")));
            helicopterSprites[4] = helicopterSprites[0]; // Reuse
            helicopterSprites[5] = helicopterSprites[1]; // Reuse
            helicopterSprites[6] = helicopterSprites[2]; // Reuse
            helicopterSprites[7] = helicopterSprites[3]; // Reuse
            
            // Load helicopter shadows
            helicopterShadows[0] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 1 Shadow.png")));
            helicopterShadows[1] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 2 Shadow.png")));
            helicopterShadows[2] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3 Shadow.png")));
            helicopterShadows[3] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4 Shadow.png")));
            helicopterShadows[4] = helicopterShadows[0]; // Reuse
            helicopterShadows[5] = helicopterShadows[1]; // Reuse
            helicopterShadows[6] = helicopterShadows[0]; // Reuse
            helicopterShadows[7] = helicopterShadows[1]; // Reuse
            
            // Load helicopter blade sprites
            helicopterBlades[0] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter Wings.png")));
            helicopterBlades[1] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 3 Wings.png")));
            helicopterBlades[2] = rotateImage180(ImageIO.read(new File("sprites\\Missle Man Assets\\Helecopters\\Helecopter 4 Wings.png")));
            
            spritesLoaded = true;
        } catch (IOException e) {
            System.err.println("Could not load boss sprites: " + e.getMessage());
            // Don't set spritesLoaded to false - allow fallback rendering
        }
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight) {
        update(bullets, player, screenWidth, screenHeight, 1.0);
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight, double deltaTime) {
        // Smooth movement to target position
        moveTimer += deltaTime;
        
        // Pick a new random target every 60-120 frames (1-2 seconds)
        if (moveTimer >= 60 + Math.random() * 60) {
            moveTimer = 0;
            
            // Pick random position in the top half of screen with some margin
            targetX = size + Math.random() * (screenWidth - size * 2);
            targetY = size + Math.random() * (screenHeight / 2.5 - size * 2);
        }
        
        // Calculate direction to target
        double dx = targetX - x;
        double dy = targetY - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // Acceleration-based movement
        if (distance > 10) { // Dead zone to prevent jittering
            // Calculate desired acceleration direction
            double accelStrength = ACCELERATION * (1.0 + level * 0.05);
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
        
        // Limit max speed
        double speed = Math.sqrt(vx * vx + vy * vy);
        double maxSpeed = MAX_SPEED * (1.0 + level * 0.1);
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
        
        // Animate helicopter blades if this is a helicopter
        if (level % 2 == 0) {
            bladeRotation += BLADE_ROTATION_SPEED * deltaTime;
            if (bladeRotation > Math.PI * 2) {
                bladeRotation -= Math.PI * 2;
            }
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
        
        // Shooting pattern (scaled by delta time)
        shootTimer += deltaTime;
        if (shootTimer >= shootInterval) {
            shootTimer = 0;
            shoot(bullets, player);
        }
        
        // Beam attacks (at higher levels)
        if (level >= 2) {
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
        // Cycle through unlocked patterns only
        patternType = (patternType + 1) % maxPatterns;
        
        switch (patternType % 12) {
            case 0: // Spiral pattern
                shootSpiral(bullets);
                break;
            case 1: // Circle pattern
                shootCircle(bullets, 8 + level * 2);
                break;
            case 2: // Aimed at player
                shootAtPlayer(bullets, player, 3);
                break;
            case 3: // Wave pattern
                shootWave(bullets);
                break;
            case 4: // Random spray
                shootRandom(bullets, 5 + level);
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
        }
    }
    
    private void shootSpiral(List<Bullet> bullets) {
        int numBullets = 6 + level;
        double angleOffset = shootTimer * 0.1;
        for (int i = 0; i < numBullets; i++) {
            double angle = (Math.PI * 2 * i / numBullets) + angleOffset;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 3, Math.sin(angle) * 3));
        }
    }
    
    private void shootCircle(List<Bullet> bullets, int numBullets) {
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5));
        }
    }
    
    private void shootAtPlayer(List<Bullet> bullets, Player player, int spread) {
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -spread; i <= spread; i++) {
            double angle = angleToPlayer + (i * 0.2);
            bullets.add(new Bullet(x, y, Math.cos(angle) * 4, Math.sin(angle) * 4));
        }
    }
    
    private void shootWave(List<Bullet> bullets) {
        int numBullets = 10 + level * 2;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI / 4 + (Math.PI / 2 * i / numBullets);
            double speed = 2 + Math.sin(i * 0.5) * 1.5;
            bullets.add(new Bullet(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
    }
    
    private void shootRandom(List<Bullet> bullets, int numBullets) {
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 2;
            bullets.add(new Bullet(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
    }
    
    private void shootFast(List<Bullet> bullets, Player player) {
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = 0; i < 5 + level; i++) {
            double angle = angleToPlayer + (Math.random() - 0.5) * 0.5;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 6, Math.sin(angle) * 6, Bullet.BulletType.FAST));
        }
    }
    
    private void shootLarge(List<Bullet> bullets) {
        int numBullets = 4 + level;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 1.5, Math.sin(angle) * 1.5, Bullet.BulletType.LARGE));
        }
    }
    
    private void shootMixed(List<Bullet> bullets, Player player) {
        // Combination attack with different bullet types
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        
        // Homing bullets
        for (int i = 0; i < 2; i++) {
            double angle = angleToPlayer + (i - 0.5) * 0.3;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5, Bullet.BulletType.HOMING));
        }
        
        // Circle of bouncing bullets
        if (level >= 3) {
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2 * i / 6;
                bullets.add(new Bullet(x, y, Math.cos(angle) * 3, Math.sin(angle) * 3, Bullet.BulletType.BOUNCING));
            }
        }
    }
    
    private void shootSpiralBullets(List<Bullet> bullets) {
        int numBullets = 5 + level;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2, Math.sin(angle) * 2, Bullet.BulletType.SPIRAL));
        }
    }
    
    private void shootSplittingBullets(List<Bullet> bullets) {
        int numBullets = 3 + level / 2;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5, Bullet.BulletType.SPLITTING));
        }
    }
    
    private void shootAcceleratingBullets(List<Bullet> bullets, Player player) {
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -2; i <= 2; i++) {
            double angle = angleToPlayer + i * 0.3;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 1.5, Math.sin(angle) * 1.5, Bullet.BulletType.ACCELERATING));
        }
    }
    
    private void shootWaveBullets(List<Bullet> bullets) {
        int numBullets = 8 + level;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI / 4 + (Math.PI / 2 * i / numBullets);
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5, Bullet.BulletType.WAVE));
        }
    }
    
    private void spawnBeamAttack(int screenWidth, int screenHeight) {
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
    
    public List<BeamAttack> getBeamAttacks() {
        return beamAttacks;
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
            g2d.rotate(rotation - Math.PI / 2); // Subtract 90 degrees to align sprite
            int spriteSize = size * 2;
            
            // For helicopters, increase vertical height to reduce compression
            int spriteWidth = spriteSize;
            int spriteHeight = spriteSize;
            boolean isHelicopter = (level % 2 == 0);
            if (isHelicopter) {
                spriteHeight = (int)(spriteSize * 1.4); // 40% taller to reduce compression
            }
            
            // Draw shadow sprite
            if (shadow != null) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                g2d.drawImage(shadow, -spriteWidth/2 + 4, -spriteHeight/2 + 4, spriteWidth, spriteHeight, null);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
            
            // Draw sprite
            g2d.drawImage(sprite, -spriteWidth/2, -spriteHeight/2, spriteWidth, spriteHeight, null);
            
            // Draw spinning helicopter blades if this is a helicopter
            if (isHelicopter && helicopterBlades[0] != null) {
                // Choose blade sprite based on helicopter variant
                int bladeIndex = Math.min(spriteIndex / 3, 2); // 0-2, 3-5, 6-7 map to blade 0, 1, 2
                BufferedImage bladeSprite = helicopterBlades[bladeIndex];
                
                if (bladeSprite != null) {
                    Graphics2D bladeG2d = (Graphics2D) g2d.create();
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
        
        // Draw level indicator below boss
        g.setColor(isMegaBoss ? new Color(255, 215, 0) : Color.WHITE); // Gold for mega, white for mini
        g.setFont(new Font("Arial", Font.BOLD, isMegaBoss ? 18 : 14));
        String vehicleName = getVehicleName(level);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(vehicleName, (int)x - fm.stringWidth(vehicleName)/2, (int)y + size/2 + 15);
        
        // Show MEGA BOSS indicator
        if (isMegaBoss) {
            g.setColor(new Color(255, 50, 50));
            g.setFont(new Font("Arial", Font.BOLD, 14));
            String megaText = "!! MEGA BOSS !!";
            fm = g.getFontMetrics();
            g.drawString(megaText, (int)x - fm.stringWidth(megaText)/2, (int)y + size/2 + 32);
        }
        
        g.setFont(new Font("Arial", Font.BOLD, 16));
        String levelText = "LV " + level;
        fm = g.getFontMetrics();
        g.drawString(levelText, (int)x - fm.stringWidth(levelText)/2, (int)y + size/2 + (isMegaBoss ? 50 : 32));
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
    
    public double getX() { return x; }
    public double getY() { return y; }
    public int getSize() { return size; }
    public double getHitboxRadius() { return size * 0.6; } // 60% of sprite size for fitting hitbox
    public boolean isMegaBoss() { return isMegaBoss; }
    public String getVehicleName() { return getVehicleName(level); }
    
    // Get money reward based on boss type
    public int getMoneyReward() {
        if (isMegaBoss) {
            return 500 + (level * 200); // Mega bosses give much more money
        } else {
            return 100 + (level * 50); // Mini bosses give less money
        }
    }
}
