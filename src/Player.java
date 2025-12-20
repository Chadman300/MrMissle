import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Player {
    private double x, y;
    private double vx, vy; // Velocity
    private double prevVX, prevVY; // Previous velocity for squash/stretch
    private static final int SIZE = 20;
    private static final double MAX_SPEED = 6.0;
    private static final double DASH_MAX_SPEED = 15.0; // Higher speed limit during dash
    private static final double ACCELERATION = 0.5;
    private static final double FRICTION = 0.85;
    private double speedMultiplier;
    private int flickerTimer; // For Lucky Dodge animation
    private static final int FLICKER_DURATION = 15; // Frames to flicker
    
    // Cached values for performance
    private double cachedSpeed = 0; // Cached velocity magnitude
    private int speedCacheAge = 0; // Age of cached speed value
    private static final double INV_SQRT_2 = 1.0 / Math.sqrt(2); // Pre-computed constant
    
    // Squash and stretch animation
    private double squashX = 1.0; // Horizontal scale
    private double squashY = 1.0; // Vertical scale
    
    // Dash state
    private boolean isDashing = false;
    private int dashFrames = 0;
    private static final int DASH_DURATION = 15; // Frames to ignore speed limit
    
    // Sun angle for directional shadows (top-left, about 135 degrees)
    private static final double SUN_ANGLE = Math.PI * 0.75; // 135 degrees
    private static final double SHADOW_DISTANCE = 12; // Shadow distance from sprite
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
        this.prevVX = 0;
        this.prevVY = 0;
        this.speedMultiplier = 1.0 + (speedUpgradeLevel * 0.15);
        this.flickerTimer = 0;
        this.squashX = 1.0;
        this.squashY = 1.0;
        loadSprite();
    }
    
    private void loadSprite() {
        if (missileSprite == null) {
            String path = "sprites/Missle Man Assets/Missles/Missle Black.png";
            try {
                missileSprite = AssetLoader.loadImage(path);
            } catch (IOException e) {
                System.err.println("Could not load missile sprite: " + path);
            }
        }
        if (missileShadow == null) {
            String path = "sprites/Missle Man Assets/Missles/Missle Black Shadow.png";
            try {
                missileShadow = AssetLoader.loadImage(path);
            } catch (IOException e) {
                System.err.println("Could not load missile shadow: " + path);
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
        
        // Store previous velocity for squash/stretch calculation
        prevVX = vx;
        prevVY = vy;
        
        // Acceleration-based movement
        double ax = 0, ay = 0;
        
        if (keys[KeyEvent.VK_W] || keys[KeyEvent.VK_UP]) ay -= ACCELERATION;
        if (keys[KeyEvent.VK_S] || keys[KeyEvent.VK_DOWN]) ay += ACCELERATION;
        if (keys[KeyEvent.VK_A] || keys[KeyEvent.VK_LEFT]) ax -= ACCELERATION;
        if (keys[KeyEvent.VK_D] || keys[KeyEvent.VK_RIGHT]) ax += ACCELERATION;
        
        // Normalize diagonal acceleration
        if (ax != 0 && ay != 0) {
            ax *= INV_SQRT_2;
            ay *= INV_SQRT_2;
        }
        
        // Apply acceleration to velocity (scaled by delta time)
        vx += ax * deltaTime;
        vy += ay * deltaTime;
        
        // Apply friction when no input
        double frictionFactor = Math.pow(FRICTION, deltaTime);
        if (ax == 0) vx *= frictionFactor;
        if (ay == 0) vy *= frictionFactor;
        
        // Update dash state
        if (isDashing) {
            dashFrames--;
            if (dashFrames <= 0) {
                isDashing = false;
            }
        }
        
        // Clamp velocity to max speed using cached speed calculation (higher limit during dash)
        double maxSpeed = isDashing ? (DASH_MAX_SPEED * speedMultiplier) : (MAX_SPEED * speedMultiplier);
        
        // Only recalculate speed every few frames or when needed
        if (speedCacheAge > 3 || cachedSpeed == 0 || (ax != 0 || ay != 0)) {
            cachedSpeed = Math.sqrt(vx * vx + vy * vy);
            speedCacheAge = 0;
        } else {
            speedCacheAge++;
        }
        
        if (cachedSpeed > maxSpeed) {
            double ratio = maxSpeed / cachedSpeed;
            vx *= ratio;
            vy *= ratio;
            cachedSpeed = maxSpeed;
        }
        
        // Calculate squash/stretch based on speed and direction changes
        double speedRatio = cachedSpeed / maxSpeed;
        double targetSquashX = 1.0 - speedRatio * 0.15; // Compress horizontally when moving
        double targetSquashY = 1.0 + speedRatio * 0.2;  // Stretch vertically when moving
        
        // Detect direction change for additional squash effect
        if ((vx > 0 && prevVX < 0) || (vx < 0 && prevVX > 0)) {
            targetSquashX = 1.2; // Brief horizontal squash on direction change
            targetSquashY = 0.8;
        }
        if ((vy > 0 && prevVY < 0) || (vy < 0 && prevVY > 0)) {
            targetSquashY = 1.2;
            targetSquashX = 0.8;
        }
        
        // Smoothly interpolate squash values
        squashX += (targetSquashX - squashX) * 0.2;
        squashY += (targetSquashY - squashY) * 0.2;
        
        // Apply velocity to position (scaled by delta time)
        x += vx * deltaTime;
        y += vy * deltaTime;
        
        // Keep player on screen (with extended boundaries for camera following)
        int margin = 50; // Extra space beyond screen edges
        if (x < SIZE - margin) {
            x = SIZE - margin;
            vx *= -0.3;
        }
        if (x > screenWidth - SIZE + margin) {
            x = screenWidth - SIZE + margin;
            vx *= -0.3;
        }
        if (y < SIZE - margin) {
            y = SIZE - margin;
            vy *= -0.3;
        }
        if (y > screenHeight - SIZE + margin) {
            y = screenHeight - SIZE + margin;
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
        
        // Apply squash/stretch transformation
        g2d.scale(squashX, squashY);
        
        // Calculate sprite dimensions proportionally based on native size
        int spriteWidth, spriteHeight;
        if (missileSprite != null) {
            // Get native dimensions
            int nativeWidth = missileSprite.getWidth();
            int nativeHeight = missileSprite.getHeight();
            
            // Scale proportionally to fit within SIZE * 2 height
            double targetHeight = SIZE * 2;
            double scale = targetHeight / nativeHeight;
            spriteWidth = (int)(nativeWidth * scale);
            spriteHeight = (int)(nativeHeight * scale);
        } else {
            // Fallback dimensions
            spriteWidth = SIZE;
            spriteHeight = SIZE * 2;
        }
        
        // Draw shadow sprite first with directional offset that moves with rotation
        if (Game.enableShadows && missileShadow != null) {
            // Calculate shadow offset relative to object rotation
            double objectRotation = angle + Math.PI / 2;
            double relativeAngle = SUN_ANGLE - objectRotation;
            double shadowOffsetX = Math.cos(relativeAngle) * SHADOW_DISTANCE;
            double shadowOffsetY = Math.sin(relativeAngle) * SHADOW_DISTANCE;
            
            // Shadow same size as sprite
            int shadowWidth = (int)(spriteWidth * SHADOW_SCALE);
            int shadowHeight = (int)(spriteHeight * SHADOW_SCALE);
            
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
            // Draw sprite with proportional dimensions
            g2d.drawImage(missileSprite, -spriteWidth/2, -spriteHeight/2, spriteWidth, spriteHeight, null);
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
        // Check if player touches boss (instant win) - use squared distance
        if (boss == null) return false;
        double dx = x - boss.getX();
        double dy = y - boss.getY();
        double distanceSquared = dx * dx + dy * dy;
        // Smaller hitbox for boss collision (40% of sprite size)
        double threshold = (SIZE * 0.4) + (boss.getSize() * 0.6);
        return distanceSquared < threshold * threshold;
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
    
    // Get the angle the missile is facing based on velocity
    public double getAngle() {
        if (vx == 0 && vy == 0) {
            return -Math.PI / 2; // Point upward when stationary
        }
        return Math.atan2(vy, vx);
    }
    
    // Set position (for debug teleport)
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    // Apply dash boost (used by DASH active item)
    public void applyDashBoost(double multiplier) {
        // Increase current velocity by the multiplier
        vx *= multiplier;
        vy *= multiplier;
    }
    
    // Apply knockback from shockwave or other effects
    public void applyKnockback(double sourceX, double sourceY, double strength) {
        // Calculate direction away from source
        double dx = x - sourceX;
        double dy = y - sourceY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        if (distance > 0) {
            // Normalize direction and apply knockback
            dx /= distance;
            dy /= distance;
            
            vx += dx * strength;
            vy += dy * strength;
        }
    }
    
    public void applyDashImpulse(double multiplier, boolean[] keys) {
        // Get current input direction
        double dashX = 0;
        double dashY = 0;
        
        if (keys[KeyEvent.VK_W] || keys[KeyEvent.VK_UP]) dashY -= 1;
        if (keys[KeyEvent.VK_S] || keys[KeyEvent.VK_DOWN]) dashY += 1;
        if (keys[KeyEvent.VK_A] || keys[KeyEvent.VK_LEFT]) dashX -= 1;
        if (keys[KeyEvent.VK_D] || keys[KeyEvent.VK_RIGHT]) dashX += 1;
        
        // If no input, use current velocity direction
        if (dashX == 0 && dashY == 0) {
            if (vx != 0 || vy != 0) {
                double speed = Math.sqrt(vx * vx + vy * vy);
                dashX = vx / speed;
                dashY = vy / speed;
            } else {
                // No movement at all, dash upward by default
                dashY = -1;
            }
        }
        
        // Normalize diagonal dashes
        if (dashX != 0 && dashY != 0) {
            double length = Math.sqrt(dashX * dashX + dashY * dashY);
            dashX /= length;
            dashY /= length;
        }
        
        // Add strong impulse in dash direction (adds to current velocity)
        double dashSpeed = MAX_SPEED * multiplier;
        vx += dashX * dashSpeed;
        vy += dashY * dashSpeed;
        
        // Enable dash mode to temporarily ignore speed limits
        isDashing = true;
        dashFrames = DASH_DURATION;
    }
}
