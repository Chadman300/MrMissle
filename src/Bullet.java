import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Bullet {
    private double x, y;
    private double vx, vy;
    private static final int SIZE = 4;
    private BulletType type;
    
    // Sun angle for directional shadows
    private static final double SUN_ANGLE = Math.PI * 0.75; // 135 degrees
    private static final double SHADOW_DISTANCE = 0; // Shadow directly under sprite
    private static final double SHADOW_SCALE = 1.0; // Shadow is 1:1 scale with sprite
    
    // Bullet sprites
    private static BufferedImage[] bulletSprites = new BufferedImage[8];
    private static boolean spritesLoaded = false;
    private int warningTime;
    private static final int WARNING_DURATION = 45; // Frames before bullet activates
    private double age; // Frames since activation
    private double spiralAngle; // For spiral bullets
    private boolean hasSplit; // For splitting bullets
    
    public enum BulletType {
        NORMAL,      // Standard bullet
        FAST,        // Faster, smaller bullet
        LARGE,       // Slower, larger bullet
        HOMING,      // Slightly tracks player
        BOUNCING,    // Bounces off walls
        SPIRAL,      // Spirals as it moves
        SPLITTING,   // Splits into smaller bullets
        ACCELERATING,// Speeds up over time
        WAVE         // Moves in a wave pattern
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
        loadSprites();
    }
    
    private static void loadSprites() {
        if (spritesLoaded) return;
        try {
            // Load all bullet sprites
            bulletSprites[0] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj 1 Purple.png")); // NORMAL
            bulletSprites[1] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj 2 Purple.png")); // FAST
            bulletSprites[2] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj Blue 1.png")); // LARGE
            bulletSprites[3] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj Blue 2.png")); // HOMING
            bulletSprites[4] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj Blue 3.png")); // BOUNCING
            bulletSprites[5] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj Orange 1.png")); // SPIRAL
            bulletSprites[6] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj Orange 2.png")); // SPLITTING
            bulletSprites[7] = ImageIO.read(new File("sprites/Missle Man Assets/Projectiles/Proj Red 1.png")); // ACCELERATING/WAVE
            spritesLoaded = true;
        } catch (IOException e) {
            System.err.println("Could not load bullet sprites: " + e.getMessage());
            spritesLoaded = false;
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
                    // Normalize angle
                    while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                    while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
                    // Turn slightly towards player (scaled by delta time)
                    currentAngle += angleDiff * 0.02 * deltaTime;
                    double speed = Math.sqrt(vx * vx + vy * vy);
                    vx = Math.cos(currentAngle) * speed;
                    vy = Math.sin(currentAngle) * speed;
                }
                break;
            case BOUNCING:
                // Bounce off walls
                if (x < 10 || x > screenWidth - 10) vx *= -1;
                if (y < 10 || y > screenHeight - 10) vy *= -1;
                break;
            case SPIRAL:
                // Rotate velocity vector to create spiral motion
                spiralAngle += 0.08;
                double currentSpeed = Math.sqrt(vx * vx + vy * vy);
                double baseAngle = Math.atan2(vy, vx);
                vx = Math.cos(baseAngle + Math.sin(spiralAngle) * 0.5) * currentSpeed;
                vy = Math.sin(baseAngle + Math.sin(spiralAngle) * 0.5) * currentSpeed;
                break;
            case ACCELERATING:
                // Speed up over time
                double accelFactor = 1 + (age * 0.01);
                vx *= Math.min(accelFactor, 1.05);
                vy *= Math.min(accelFactor, 1.05);
                break;
            case WAVE:
                // Move in sine wave pattern
                double perpAngle = Math.atan2(vy, vx) + Math.PI / 2;
                double waveOffset = Math.sin(age * 0.2) * 2 * deltaTime;
                x += Math.cos(perpAngle) * waveOffset;
                y += Math.sin(perpAngle) * waveOffset;
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
    
    public void draw(Graphics2D g) {
        // Draw warning indicator during warning phase
        if (warningTime > 0) {
            float alpha = (float)(warningTime % 20) / 20.0f;
            g.setColor(new Color(191, 97, 106, (int)(alpha * 180))); // Palette red
            int warningSize = 20 + (WARNING_DURATION - warningTime) / 3;
            
            // Draw crosshair warning
            g.setStroke(new BasicStroke(3));
            g.drawLine((int)x - warningSize, (int)y, (int)x + warningSize, (int)y);
            g.drawLine((int)x, (int)y - warningSize, (int)x, (int)y + warningSize);
            
            // Draw warning circle
            g.setStroke(new BasicStroke(2));
            g.drawOval((int)x - warningSize/2, (int)y - warningSize/2, warningSize, warningSize);
            return;
        }
        
        // Get sprite index and size based on type
        int spriteIndex = 0;
        int spriteSize = SIZE * 3;
        
        switch (type) {
            case NORMAL:
                spriteIndex = 0;
                spriteSize = SIZE * 3;
                break;
            case FAST:
                spriteIndex = 1;
                spriteSize = SIZE * 2;
                break;
            case LARGE:
                spriteIndex = 2;
                spriteSize = SIZE * 4;
                break;
            case HOMING:
                spriteIndex = 3;
                spriteSize = SIZE * 3;
                break;
            case BOUNCING:
                spriteIndex = 4;
                spriteSize = SIZE * 3;
                break;
            case SPIRAL:
                spriteIndex = 5;
                spriteSize = SIZE * 3;
                break;
            case SPLITTING:
                spriteIndex = 6;
                spriteSize = SIZE * 4;
                break;
            case ACCELERATING:
            case WAVE:
                spriteIndex = 7;
                spriteSize = SIZE * 3;
                break;
        }
        
        // Draw sprite if loaded, otherwise fallback to orb
        if (spritesLoaded && bulletSprites[spriteIndex] != null) {
            Graphics2D g2d = (Graphics2D) g.create();
            // Calculate rotation angle based on velocity
            double angle = Math.atan2(vy, vx);
            
            g2d.translate(x, y);
            
            // Draw shadow with rotation-based offset
            if (Game.enableShadows) {
                double objectRotation = angle + Math.PI / 2;
                double relativeAngle = SUN_ANGLE - objectRotation;
                double shadowOffsetX = Math.cos(relativeAngle) * SHADOW_DISTANCE;
                double shadowOffsetY = Math.sin(relativeAngle) * SHADOW_DISTANCE;
                
                int shadowSize = (int)(spriteSize * SHADOW_SCALE);
                
                // Rotate for shadow
                g2d.rotate(objectRotation);
                
                // Draw shadow (darker, semi-transparent version)
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                g2d.setColor(new Color(0, 0, 0));
                g2d.fillOval(
                    (int)(-shadowSize/2 + shadowOffsetX),
                    (int)(-shadowSize/2 + shadowOffsetY),
                    shadowSize, shadowSize);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                
                // Reset rotation for sprite drawing
                g2d.rotate(-objectRotation);
            }
            g2d.rotate(angle + Math.PI / 2); // Rotate sprite to face direction of travel
            
            // Draw sprite centered
            g2d.drawImage(bulletSprites[spriteIndex], 
                -spriteSize/2, -spriteSize/2, 
                spriteSize, spriteSize, null);
            
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
        return x < -margin || x > width + margin || y < -margin || y > height + margin;
    }
    
    public boolean collidesWith(Player player) {
        if (warningTime > 0) return false; // Can't hit during warning
        double dx = x - player.getX();
        double dy = y - player.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        // Larger, more fitting hitbox (70% of sprite size)
        int actualSize = (type == BulletType.LARGE) ? SIZE + 4 : (type == BulletType.FAST) ? SIZE - 2 : SIZE;
        return distance < (actualSize * 0.7) + (player.getSize() * 0.7);
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
    
    public boolean isActive() {
        return warningTime <= 0;
    }
}
