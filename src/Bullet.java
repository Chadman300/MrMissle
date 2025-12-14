import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Bullet {
    private double x, y;
    private double vx, vy;
    private static final int SIZE = 6;
    private BulletType type;
    
    // Sun angle for directional shadows
    private static final double SUN_ANGLE = Math.PI * 0.75; // 135 degrees
    private static final double SHADOW_DISTANCE = 5; // Shadow distance from sprite
    private static final double SHADOW_SCALE = 0.7; // Shadow is 70% scale of sprite
    
    // Bullet sprites
    private static BufferedImage[] bulletSprites = new BufferedImage[17];
    private static BufferedImage[] bulletShadows = new BufferedImage[17];
    private static boolean spritesLoaded = false;
    
    // Cached colors for performance
    private static final Color FIRE_ORANGE = new Color(255, 100, 0);
    private static final Color FIRE_YELLOW = new Color(255, 200, 0);
    private static final Color FIRE_RED = new Color(255, 50, 0);
    private static final Color TRAIL_YELLOW = new Color(255, 220, 0, 180);
    private static final Color TRAIL_PURPLE = new Color(200, 50, 255, 180);
    
    // Cached math constants
    private static final double TWO_PI = Math.PI * 2;
    private static final double HALF_PI = Math.PI / 2;
    private static final double SQRT_2 = Math.sqrt(2);
    private static final double INV_SQRT_2 = 1.0 / Math.sqrt(2);
    
    // Cached speed for collision detection (avoid repeated sqrt)
    private double cachedSpeed = 0;
    private int speedCacheAge = 0;
    private int warningTime;
    private static final int WARNING_DURATION = 120; // Frames before bullet activates
    private double age; // Frames since activation
    private double spiralAngle; // For spiral bullets
    private boolean hasSplit; // For splitting bullets
    private double explosionTimer; // Time until explosion for bombs
    private static final double EXPLOSION_TIME = 120; // Frames until explosion
    private static final double FLICKER_START = 30; // Start flickering 30 frames before explosion
    private int spriteVariant; // Which variant (0-2) for bombs/grenades
    private int bounceCount; // Number of times bounced
    private boolean hasGrazed; // Track if bullet has been grazed by player
    private static final int MAX_BOUNCES = 1; // Max bounces for bouncing bullets
    private static final double HOMING_LIFETIME = 480; // 8 seconds lifetime for homing bullets
    
    public enum BulletType {
        NORMAL,      // Standard bullet
        FAST,        // Faster, smaller bullet
        LARGE,       // Slower, larger bullet
        HOMING,      // Slightly tracks player
        BOUNCING,    // Bounces off walls
        SPIRAL,      // Spirals as it moves
        SPLITTING,   // Splits into smaller bullets
        ACCELERATING,// Speeds up over time
        WAVE,        // Moves in a wave pattern
        BOMB,        // Slows down then explodes into fragments
        GRENADE,     // Arcs then explodes into fragments
        NUKE,        // Large explosive that slows then detonates
        FRAGMENT     // Small fragment from explosion
    }
    
    public Bullet(double x, double y, double vx, double vy) {
        this(x, y, vx, vy, BulletType.NORMAL);
    }
    
    public Bullet(double x, double y, double vx, double vy, BulletType type) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.type = type;
        this.warningTime = WARNING_DURATION;
        this.age = 0;
        this.spiralAngle = 0;
        this.hasSplit = false;
        this.explosionTimer = EXPLOSION_TIME;
        this.spriteVariant = (int)(Math.random() * 3); // Random variant 0-2
        this.bounceCount = 0;
        this.hasGrazed = false;
        loadSprites();
    }
    
    private static void loadSprites() {
        if (spritesLoaded) return;
        try {
            // Load all bullet sprites
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj 1 Purple.png", 1);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj 2 Purple.png", 4);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj Blue 1.png", 2);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj Blue 2.png", 3);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj Blue 3.png", 5);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj Orange 1.png", 0);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj Orange 2.png", 6);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Proj Red 1.png", 7);
            
            // Load explosive projectiles (bombs, grenades, nuke)
            loadSpriteWithPathAndShadow("sprites/Missle Man Assets/Projectiles/Bomb 1.png", 
                                        "sprites/Missle Man Assets/Projectiles/Bomb 1 Shadow.png", 8);
            loadSpriteWithPathAndShadow("sprites/Missle Man Assets/Projectiles/Bomb 2.png",
                                        "sprites/Missle Man Assets/Projectiles/Bomb 2 Shadow.png", 9);
            
            loadSpriteWithPathAndShadow("sprites/Missle Man Assets/Projectiles/Grenade 1.png",
                                        "sprites/Missle Man Assets/Projectiles/Grenade 1 Shadow.png", 10);
            loadSpriteWithPathAndShadow("sprites/Missle Man Assets/Projectiles/Grenade 2.png",
                                        "sprites/Missle Man Assets/Projectiles/Grenade 2 Shadow.png", 11);
            loadSpriteWithPathAndShadow("sprites/Missle Man Assets/Projectiles/Grenade 3.png",
                                        "sprites/Missle Man Assets/Projectiles/Grenade 3 Shadow.png", 12);
            
            loadSpriteWithPathAndShadow("sprites/Missle Man Assets/Projectiles/Mini Nuke.png",
                                        "sprites/Missle Man Assets/Projectiles/Mini Nuke Shadow.png", 13);
            
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Fragment Proj 1.png", 14);
            loadSpriteWithPath("sprites/Missle Man Assets/Projectiles/Fragment Proj 2.png", 15);
            
            spritesLoaded = true;
        } catch (IOException e) {
            System.err.println("Failed to load bullet sprites: " + e.getMessage());
            spritesLoaded = false;
        }
    }
    
    private static void loadSpriteWithPath(String path, int index) throws IOException {
        try {
            bulletSprites[index] = ImageIO.read(new File(path));
        } catch (IOException e) {
            System.err.println("Could not load bullet sprite: " + path);
            throw e;
        }
    }
    
    private static void loadSpriteWithPathAndShadow(String spritePath, String shadowPath, int index) throws IOException {
        try {
            bulletSprites[index] = ImageIO.read(new File(spritePath));
        } catch (IOException e) {
            System.err.println("Could not load bullet sprite: " + spritePath);
            throw e;
        }
        try {
            bulletShadows[index] = ImageIO.read(new File(shadowPath));
        } catch (IOException e) {
            System.err.println("Could not load bullet shadow: " + shadowPath);
            throw e;
        }
    }
    
    // Reset bullet for pooling
    public void reset(double x, double y, double vx, double vy, BulletType type) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.type = type;
        this.warningTime = WARNING_DURATION;
        this.age = 0;
        this.spiralAngle = 0;
        this.hasSplit = false;
        this.explosionTimer = EXPLOSION_TIME;
        this.spriteVariant = (int)(Math.random() * 3);
        this.bounceCount = 0;
    }
    
    public void update() {
        update(null, 0, 0, 1.0);
    }
    
    public void update(Player player, int screenWidth, int screenHeight) {
        update(player, screenWidth, screenHeight, 1.0);
    }
    
    public void update(Player player, int screenWidth, int screenHeight, double deltaTime) {
        if (warningTime > 0) {
            warningTime -= deltaTime;
            return;
        }
        
        age += deltaTime;
        
        // Type-specific behavior
        switch (type) {
            case FAST:
                // Already faster from initial velocity
                break;
            case HOMING:
                if (player != null) {
                    // Slightly adjust direction towards player
                    double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
                    double currentAngle = Math.atan2(vy, vx);
                    double angleDiff = angleToPlayer - currentAngle;
                    // Normalize angle (optimized)
                    if (angleDiff > Math.PI) angleDiff -= TWO_PI;
                    else if (angleDiff < -Math.PI) angleDiff += TWO_PI;
                    // Turn slightly towards player (scaled by delta time)
                    currentAngle += angleDiff * 0.02 * deltaTime;
                    // Cache and reuse speed calculation
                    if (speedCacheAge > 10 || cachedSpeed == 0) {
                        cachedSpeed = Math.sqrt(vx * vx + vy * vy);
                        speedCacheAge = 0;
                    } else {
                        speedCacheAge++;
                    }
                    vx = Math.cos(currentAngle) * cachedSpeed;
                    vy = Math.sin(currentAngle) * cachedSpeed;
                }
                break;
            case BOUNCING:
                // Bounce off walls (only once)
                if (bounceCount < MAX_BOUNCES) {
                    if (x < 10 || x > screenWidth - 10) {
                        vx *= -1;
                        bounceCount++;
                    }
                    if (y < 10 || y > screenHeight - 10) {
                        vy *= -1;
                        bounceCount++;
                    }
                }
                break;
            case SPIRAL:
                // Rotate velocity vector to create spiral motion
                spiralAngle += 0.08;
                // Cache speed and angle calculations
                if (speedCacheAge > 10 || cachedSpeed == 0) {
                    cachedSpeed = Math.sqrt(vx * vx + vy * vy);
                    speedCacheAge = 0;
                } else {
                    speedCacheAge++;
                }
                double baseAngle = Math.atan2(vy, vx);
                double spiralOffset = Math.sin(spiralAngle) * 0.5;
                double newAngle = baseAngle + spiralOffset;
                vx = Math.cos(newAngle) * cachedSpeed;
                vy = Math.sin(newAngle) * cachedSpeed;
                break;
            case ACCELERATING:
                // Speed up over time
                double accelFactor = 1 + (age * 0.01);
                vx *= Math.min(accelFactor, 1.05);
                vy *= Math.min(accelFactor, 1.05);
                break;
            case WAVE:
                // Move in sine wave pattern
                double perpAngle = Math.atan2(vy, vx) + HALF_PI;
                double waveOffset = Math.sin(age * 0.2) * 2 * deltaTime;
                x += Math.cos(perpAngle) * waveOffset;
                y += Math.sin(perpAngle) * waveOffset;
                break;
            case BOMB:
            case GRENADE:
            case NUKE:
                // Slow down over time
                double slowFactor = 0.99; // 1% slowdown per frame (less friction)
                vx *= Math.pow(slowFactor, deltaTime);
                vy *= Math.pow(slowFactor, deltaTime);
                
                // Count down to explosion
                explosionTimer -= deltaTime;
                break;
            case FRAGMENT:
                // Fragments just fly straight
                break;
            default:
                break;
        }
        
        // Move bullet (scaled by delta time)
        x += vx * deltaTime;
        y += vy * deltaTime;
    }
    
    public void applySlow(double factor) {
        vx *= factor;
        vy *= factor;
    }
    
    public boolean shouldSpawnTrail() {
        // Fast and accelerating bullets spawn trails when active
        return warningTime <= 0 && (type == BulletType.FAST || type == BulletType.ACCELERATING);
    }
    
    public Color getTrailColor() {
        // Return appropriate trail color based on bullet type
        if (type == BulletType.FAST) {
            return TRAIL_YELLOW;
        } else if (type == BulletType.ACCELERATING) {
            return TRAIL_PURPLE;
        }
        return Color.WHITE;
    }
    
    public boolean shouldExplode() {
        return (type == BulletType.BOMB || type == BulletType.GRENADE || type == BulletType.NUKE) 
               && explosionTimer <= 0;
    }
    
    public java.util.List<Particle> createExplosionParticles() {
        java.util.List<Particle> explosionParticles = new java.util.ArrayList<>();
        
        // Number of particles and rings based on type
        int particleCount = 15;
        int rings = 2;
        if (type == BulletType.NUKE) {
            particleCount = 40;
            rings = 4;
        } else if (type == BulletType.GRENADE) {
            particleCount = 25;
            rings = 3;
        }
        
        // Fire particles
        for (int i = 0; i < particleCount; i++) {
            double angle = Math.random() * TWO_PI;
            double speed = 1 + Math.random() * 4;
            Color fireColor;
            double rand = Math.random();
            if (rand < 0.4) {
                fireColor = FIRE_ORANGE;
            } else if (rand < 0.7) {
                fireColor = FIRE_YELLOW;
            } else {
                fireColor = FIRE_RED;
            }
            explosionParticles.add(new Particle(
                x, y,
                Math.cos(angle) * speed, Math.sin(angle) * speed,
                fireColor, 30, 5,
                Particle.ParticleType.SPARK
            ));
        }
        
        // Shockwave rings
        for (int i = 0; i < rings; i++) {
            int baseSize = (type == BulletType.NUKE) ? 60 : (type == BulletType.GRENADE) ? 40 : 30;
            explosionParticles.add(new Particle(
                x, y, 0, 0,
                new Color(255, 150 - i * 30, 0, 200 - i * 50), 
                35 + i * 10, 
                baseSize + i * 20,
                Particle.ParticleType.EXPLOSION
            ));
        }
        
        return explosionParticles;
    }
    
    public java.util.List<Bullet> createFragments() {
        java.util.List<Bullet> fragments = new java.util.ArrayList<>();
        
        // Number of fragments based on type
        int fragmentCount = 8;
        if (type == BulletType.NUKE) fragmentCount = 16;
        if (type == BulletType.GRENADE) fragmentCount = 8;
        
        // Create fragments in all directions
        for (int i = 0; i < fragmentCount; i++) {
            double angle = (TWO_PI * i) / fragmentCount;
            double speed = 2.0 + Math.random() * 1.5;
            int fragmentSprite = (i % 2 == 0) ? 14 : 15; // Alternate between Fragment Proj 1 & 2
            Bullet fragment = new Bullet(x, y, 
                Math.cos(angle) * speed, 
                Math.sin(angle) * speed, 
                BulletType.FRAGMENT);
            fragment.spriteVariant = fragmentSprite - 14; // 0 or 1
            fragments.add(fragment);
        }
        
        return fragments;
    }
    
    public void draw(Graphics2D g) {
        // Draw warning indicator during warning phase
        if (warningTime > 0) {
            float alpha = Math.min(0.5f, (float)(warningTime % 20) / 20.0f + 0.2f);
            g.setColor(new Color(180, 40, 40, (int)(alpha * 180))); // Dim red with transparency
            int warningSize = 8 + (WARNING_DURATION - warningTime) / 6;
            
            // Draw crosshair warning
            g.setStroke(new BasicStroke(2));
            g.drawLine((int)x - warningSize, (int)y, (int)x + warningSize, (int)y);
            g.drawLine((int)x, (int)y - warningSize, (int)x, (int)y + warningSize);
            
            // Draw warning circle
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval((int)x - warningSize/2, (int)y - warningSize/2, warningSize, warningSize);
            return;
        }
        
        // Get sprite index and size based on type
        int spriteIndex = 0;
        int spriteSize = SIZE * 4;
        
        switch (type) {
            case NORMAL:
                spriteIndex = 0;
                spriteSize = SIZE * 4;
                break;
            case FAST:
                spriteIndex = 1;
                spriteSize = SIZE * 3;
                break;
            case LARGE:
                spriteIndex = 2;
                spriteSize = SIZE * 5;
                break;
            case HOMING:
                spriteIndex = 3;
                spriteSize = SIZE * 4;
                break;
            case BOUNCING:
                spriteIndex = 4;
                spriteSize = SIZE * 4;
                break;
            case SPIRAL:
                spriteIndex = 5;
                spriteSize = SIZE * 4;
                break;
            case SPLITTING:
                spriteIndex = 6;
                spriteSize = SIZE * 5;
                break;
            case ACCELERATING:
            case WAVE:
                spriteIndex = 7;
                spriteSize = SIZE * 4;
                break;
            case BOMB:
                spriteIndex = 8 + (spriteVariant % 2); // Bomb 1 or Bomb 2
                spriteSize = SIZE * 6;
                break;
            case GRENADE:
                spriteIndex = 10 + (spriteVariant % 3); // Grenade 1, 2, or 3
                spriteSize = SIZE * 5;
                break;
            case NUKE:
                spriteIndex = 13; // Mini Nuke
                spriteSize = SIZE * 7;
                break;
            case FRAGMENT:
                spriteIndex = 14 + spriteVariant; // Fragment Proj 1 or 2
                spriteSize = SIZE * 3;
                break;
        }
        
        // Flickering effect for explosives about to detonate
        boolean shouldFlicker = (type == BulletType.BOMB || type == BulletType.GRENADE || type == BulletType.NUKE)
                                && explosionTimer > 0 && explosionTimer < FLICKER_START;
        float flickerAlpha = 1.0f;
        if (shouldFlicker) {
            // Fast flicker effect
            int flickerFrame = (int)(explosionTimer / 2);
            flickerAlpha = (flickerFrame % 2 == 0) ? 1.0f : 0.3f;
        }
        
        // Draw sprite if loaded, otherwise fallback to orb
        if (spritesLoaded && bulletSprites[spriteIndex] != null) {
            Graphics2D g2d = (Graphics2D) g.create();
            // Calculate rotation angle based on velocity
            double angle = Math.atan2(vy, vx);
            
            g2d.translate(x, y);
            
            // Draw shadow with rotation-based offset
            if (Game.enableShadows) {
                double objectRotation = angle + HALF_PI;
                double relativeAngle = SUN_ANGLE - objectRotation;
                double shadowOffsetX = Math.cos(relativeAngle) * SHADOW_DISTANCE;
                double shadowOffsetY = Math.sin(relativeAngle) * SHADOW_DISTANCE;
                
                int shadowSize = (int)(spriteSize * SHADOW_SCALE);
                
                // Rotate for shadow
                g2d.rotate(objectRotation);
                
                // Check if we have a dedicated shadow sprite for this bullet
                BufferedImage shadowSprite = bulletShadows[spriteIndex];
                if (shadowSprite != null) {
                    // Draw sprite shadow with proper dimensions
                    int nativeShadowWidth = shadowSprite.getWidth();
                    int nativeShadowHeight = shadowSprite.getHeight();
                    double shadowScale = (double)spriteSize / Math.max(nativeShadowWidth, nativeShadowHeight);
                    int drawShadowWidth = (int)(nativeShadowWidth * shadowScale);
                    int drawShadowHeight = (int)(nativeShadowHeight * shadowScale);
                    
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f * flickerAlpha));
                    g2d.drawImage(shadowSprite,
                        (int)(-drawShadowWidth/2 + shadowOffsetX),
                        (int)(-drawShadowHeight/2 + shadowOffsetY),
                        drawShadowWidth, drawShadowHeight, null);
                } else {
                    // Fallback: draw oval shadow (taller than wide, rotated 90 degrees)
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f * flickerAlpha));
                    g2d.setColor(new Color(0, 0, 0));
                    int shadowWidth = (int)(shadowSize * 0.7); // 30% narrower
                    int shadowHeight = (int)(shadowSize * 1.3); // 30% taller
                    g2d.fillOval(
                        (int)(-shadowWidth/2 + shadowOffsetX),
                        (int)(-shadowHeight/2 + shadowOffsetY),
                        shadowWidth, shadowHeight);
                }
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flickerAlpha));
                
                // Reset rotation for sprite drawing
                g2d.rotate(-objectRotation);
            } else {
                // Apply flickering alpha even if shadows are disabled
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flickerAlpha));
            }
            g2d.rotate(angle + HALF_PI); // Rotate sprite to face direction of travel
            
            // Get native sprite dimensions and scale proportionally
            int nativeWidth = bulletSprites[spriteIndex].getWidth();
            int nativeHeight = bulletSprites[spriteIndex].getHeight();
            double scale = (double)spriteSize / Math.max(nativeWidth, nativeHeight);
            int drawWidth = (int)(nativeWidth * scale);
            int drawHeight = (int)(nativeHeight * scale);
            
            // Draw sprite centered with proportional dimensions
            g2d.drawImage(bulletSprites[spriteIndex], 
                -drawWidth/2, -drawHeight/2, 
                drawWidth, drawHeight, null);
            
            g2d.dispose();
        } else {
            // Fallback: draw colored orb
            int size = SIZE;
            Color color;
            
            switch (type) {
                case FAST:
                    size = SIZE - 2;
                    color = new Color(255, 220, 0); // Bright yellow
                    break;
                case LARGE:
                    size = SIZE + 4;
                    color = new Color(0, 100, 255); // Bright blue
                    break;
                case HOMING:
                    color = new Color(255, 50, 200); // Hot pink
                    break;
                case BOUNCING:
                    color = new Color(50, 255, 100); // Bright green
                    break;
                case SPIRAL:
                    color = new Color(0, 255, 255); // Bright cyan
                    break;
                case SPLITTING:
                    size = SIZE + 4;
                    color = new Color(255, 100, 0); // Bright orange
                    break;
                case ACCELERATING:
                    color = new Color(200, 50, 255); // Bright purple
                    break;
                case WAVE:
                    color = new Color(0, 255, 200); // Bright teal
                    break;
                default:
                    color = new Color(255, 50, 50); // Bright red
                    break;
            }
            
            // Draw vibrant orb with glow effect
            // Outer glow
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g.setColor(color);
            g.fillOval((int)(x - size), (int)(y - size), size * 2, size * 2);
            
            // Main orb
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g.setColor(color);
            g.fillOval((int)(x - size/2), (int)(y - size/2), size, size);
            
            // Bright highlight for depth
            g.setColor(new Color(255, 255, 255, 200));
            g.fillOval((int)(x - size/4), (int)(y - size/3), size/2, size/2);
            
            // Inner core (brighter)
            Color brightCore = new Color(
                Math.min(255, color.getRed() + 100),
                Math.min(255, color.getGreen() + 100),
                Math.min(255, color.getBlue() + 100)
            );
            g.setColor(brightCore);
            g.fillOval((int)(x - size/6), (int)(y - size/6), size/3, size/3);
        }
    }
    
    public boolean isOffScreen(int width, int height) {
        // Check if bullet is completely off screen with generous margin
        int margin = 100;
        // Homing bullets expire after lifetime
        if (type == BulletType.HOMING && age > HOMING_LIFETIME) {
            return true;
        }
        return x < -margin || x > width + margin || y < -margin || y > height + margin;
    }
    
    public boolean collidesWith(Player player) {
        if (warningTime > 0) return false; // Can't hit during warning
        double dx = x - player.getX();
        double dy = y - player.getY();
        double distanceSquared = dx * dx + dy * dy;
        // Smaller hitbox (30% of sprite size) - use squared distance to avoid sqrt
        int actualSize = (type == BulletType.LARGE) ? SIZE + 4 : (type == BulletType.FAST) ? SIZE - 2 : SIZE;
        double threshold = (actualSize * 0.5) + (player.getSize() * 0.3);
        return distanceSquared < threshold * threshold;
    }
    
    public boolean shouldSplit() {
        return type == BulletType.SPLITTING && !hasSplit && age > 60;
    }
    
    public void markAsSplit() {
        hasSplit = true;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVX() { return vx; }
    public double getVY() { return vy; }
    public BulletType getType() { return type; }
    
    public void multiplySpeed(double factor) {
        vx *= factor;
        vy *= factor;
    }
    
    public boolean hasGrazed() { return hasGrazed; }
    public void setGrazed(boolean grazed) { this.hasGrazed = grazed; }
    
    public boolean isActive() {
        return warningTime <= 0;
    }
    
    // Apply force to bullet (used by active items like SHOCKWAVE, MAGNET)
    public void applyForce(double fx, double fy) {
        vx += fx;
        vy += fy;
    }
}
