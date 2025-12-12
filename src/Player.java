import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Player {
    private double x, y;
    private double vx, vy; // Velocity
    private static final int SIZE = 20;
    private static final double MAX_SPEED = 6.0;
    private static final double ACCELERATION = 0.5;
    private static final double FRICTION = 0.85;
    private double speedMultiplier;
    private int flickerTimer; // For Lucky Dodge animation
    private static final int FLICKER_DURATION = 15; // Frames to flicker
    
    // Sun angle for directional shadows (top-left, about 135 degrees)
    private static final double SUN_ANGLE = Math.PI * 0.75; // 135 degrees
    private static final double SHADOW_DISTANCE = 0; // Shadow directly under sprite
    private static final double SHADOW_SCALE = 1.0; // Shadow is 1:1 scale with sprite
    
    private static BufferedImage missileSprite;
    private static BufferedImage missileShadow;
    
    public Player(double x, double y) {
        this(x, y, 0);
    }
    
    public Player(double x, double y, int speedUpgradeLevel) {
        this.x = x;
        this.y = y;
        this.vx = 0;
        this.vy = 0;
        this.speedMultiplier = 1.0 + (speedUpgradeLevel * 0.15);
        this.flickerTimer = 0;
        loadSprite();
    }
    
    private void loadSprite() {
        if (missileSprite == null) {
            try {
                BufferedImage original = ImageIO.read(new File("sprites\\Missle Man Assets\\Missles\\Missle Black.png"));
                //missileSprite = rotateImage180(original);
                missileSprite = original;
            } catch (IOException e) {
                System.err.println("Could not load missile sprite: " + e.getMessage());
            }
        }
        if (missileShadow == null) {
            try {
                BufferedImage original = ImageIO.read(new File("sprites\\Missle Man Assets\\Missles\\Missle Black Shadow.png"));
                //missileShadow = rotateImage180(original);
                missileShadow = original;
            } catch (IOException e) {
                System.err.println("Could not load missile shadow: " + e.getMessage());
            }
        }
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
    
    public void update(boolean[] keys, int screenWidth, int screenHeight) {
        update(keys, screenWidth, screenHeight, 1.0);
    }
    
    public void update(boolean[] keys, int screenWidth, int screenHeight, double deltaTime) {
        // Decrement flicker timer (scaled by delta time)
        if (flickerTimer > 0) flickerTimer -= deltaTime;
        
        // Acceleration-based movement
        double ax = 0, ay = 0;
        
        if (keys[KeyEvent.VK_W] || keys[KeyEvent.VK_UP]) ay -= ACCELERATION;
        if (keys[KeyEvent.VK_S] || keys[KeyEvent.VK_DOWN]) ay += ACCELERATION;
        if (keys[KeyEvent.VK_A] || keys[KeyEvent.VK_LEFT]) ax -= ACCELERATION;
        if (keys[KeyEvent.VK_D] || keys[KeyEvent.VK_RIGHT]) ax += ACCELERATION;
        
        // Normalize diagonal acceleration
        if (ax != 0 && ay != 0) {
            ax *= 0.707; // 1/sqrt(2)
            ay *= 0.707;
        }
        
        // Apply acceleration to velocity (scaled by delta time)
        vx += ax * deltaTime;
        vy += ay * deltaTime;
        
        // Apply friction when no input
        double frictionFactor = Math.pow(FRICTION, deltaTime);
        if (ax == 0) vx *= frictionFactor;
        if (ay == 0) vy *= frictionFactor;
        
        // Clamp velocity to max speed
        double maxSpeed = MAX_SPEED * speedMultiplier;
        double speed = Math.sqrt(vx * vx + vy * vy);
        if (speed > maxSpeed) {
            vx = (vx / speed) * maxSpeed;
            vy = (vy / speed) * maxSpeed;
        }
        
        // Apply velocity to position (scaled by delta time)
        x += vx * deltaTime;
        y += vy * deltaTime;
        
        // Keep player on screen (with bounce)
        if (x < SIZE) {
            x = SIZE;
            vx *= -0.3;
        }
        if (x > screenWidth - SIZE) {
            x = screenWidth - SIZE;
            vx *= -0.3;
        }
        if (y < SIZE) {
            y = SIZE;
            vy *= -0.3;
        }
        if (y > screenHeight - SIZE) {
            y = screenHeight - SIZE;
            vy *= -0.3;
        }
    }
    
    public void draw(Graphics2D g) {
        // Apply flicker effect if Lucky Dodge was triggered
        float alpha = 1.0f;
        if (flickerTimer > 0) {
            // Rapid flicker between visible and semi-transparent
            alpha = (flickerTimer % 3 == 0) ? 0.3f : 1.0f;
        }
        
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // Calculate rotation angle based on velocity (pointing in movement direction)
        double angle = Math.atan2(vy, vx);
        // If stationary, point upward
        if (vx == 0 && vy == 0) {
            angle = -Math.PI / 2;
        }
        
        // Save original transform
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.translate(x, y);
        
        int spriteSize = SIZE;
        
        // Draw shadow sprite first with directional offset that moves with rotation
        if (Game.enableShadows && missileShadow != null) {
            // Calculate shadow offset relative to object rotation
            double objectRotation = angle + Math.PI / 2;
            double relativeAngle = SUN_ANGLE - objectRotation;
            double shadowOffsetX = Math.cos(relativeAngle) * SHADOW_DISTANCE;
            double shadowOffsetY = Math.sin(relativeAngle) * SHADOW_DISTANCE;
            
            // Shadow is slightly larger
            int shadowWidth = (int)(spriteSize * SHADOW_SCALE);
            int shadowHeight = (int)(spriteSize * 2 * SHADOW_SCALE);
            
            // Rotate to match object
            g2d.rotate(objectRotation);
            
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.5f));
            g2d.drawImage(missileShadow, 
                (int)(-shadowWidth/2 + shadowOffsetX), 
                (int)(-shadowHeight/2 + shadowOffsetY), 
                shadowWidth, shadowHeight, null);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            
            // Reset rotation for sprite
            g2d.rotate(-objectRotation);
        }
        
        // Rotate for sprite drawing
        g2d.rotate(angle + Math.PI / 2); // Back to original rotation since sprite is now pre-rotated
        
        if (missileSprite != null) {
            // Draw sprite
            g2d.drawImage(missileSprite, -spriteSize/2, -spriteSize/2, spriteSize, spriteSize * 2, null);
        } else {
            // Fallback: draw simple circle with shadow if sprite not loaded
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.fillOval(-SIZE/2 + 2, -SIZE/2 + 2, SIZE, SIZE);
            g2d.setColor(new Color(255, 50, 50));
            g2d.fillOval(-SIZE/2, -SIZE/2, SIZE, SIZE);
        }
        
        g2d.dispose();
        
        // Draw hitbox (small red dot at center)
        g.setColor(Color.RED);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.5f));
        g.fillOval((int)x - 2, (int)y - 2, 4, 4);
        
        // Reset alpha
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }
    
    public boolean collidesWith(Boss boss) {
        // Check if player touches boss (instant win)
        if (boss == null) return false;
        double dx = x - boss.getX();
        double dy = y - boss.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        // Larger hitbox for boss collision (60% of sprite size)
        return distance < (SIZE * 0.6) + (boss.getSize() * 0.6);
    }
    
    public void triggerFlicker() {
        flickerTimer = FLICKER_DURATION;
    }
    
    public boolean isFlickering() {
        return flickerTimer > 0;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    public int getSize() { return SIZE; }
    public double getVX() { return vx; }
    public double getVY() { return vy; }
}
