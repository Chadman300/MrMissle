import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Player {
    // Keybind and controller references
    private KeyBindManager keyBindManager;
    private ControllerManager controllerManager;
    private double x, y;
    private double vx, vy; // Velocity
    private double prevVX, prevVY; // Previous velocity for squash/stretch
    private static final int SIZE = 20;
    private static final double MAX_SPEED = 6.0;
    private static final double DASH_MAX_SPEED = 15.0; // Higher speed limit during dash
    private static final double ACCELERATION = 0.5;
    private static final double FRICTION = 0.85;
    private double speedMultiplier;
    private double flickerTimer; // For Lucky Dodge animation
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
    
    // Dark glow shadow settings (centered underneath)
    private static final double SHADOW_GLOW_OFFSET_Y = 4; // Slight downward offset for "underneath" feel
    private static final double SHADOW_MIN_SCALE = 1.05; // Innermost layer scale
    private static final double SHADOW_MAX_SCALE = 1.7; // Outermost layer scale
    private static final float SHADOW_MAX_ALPHA = 0.18f; // Alpha of innermost (most opaque) layer
    private static final float SHADOW_MIN_ALPHA = 0.03f; // Alpha of outermost (most transparent) layer
    
    private static BufferedImage missileSprite;
    private static BufferedImage missileShadow;
    
    public Player(double x, double y) {
        this(x, y, 0);
    }
    
    public Player(double x, double y, int speedUpgradeLevel) {
        this(x, y, speedUpgradeLevel, null, null);
    }
    
    public Player(double x, double y, int speedUpgradeLevel, KeyBindManager keyBindManager, ControllerManager controllerManager) {
        this.x = x;
        this.y = y;
        this.vx = 0;
        this.vy = 0;
        this.keyBindManager = keyBindManager;
        this.controllerManager = controllerManager;
        this.prevVX = 0;
        this.prevVY = 0;
        this.speedMultiplier = 1.0 + (speedUpgradeLevel * 0.15);
        this.flickerTimer = 0;
        this.squashX = 1.0;
        this.squashY = 1.0;
        loadSprite();
    }
    
    // Maximum rendered size for player sprite (SIZE * 2 = 40)
    private static final int SPRITE_PRESCALE_SIZE = SIZE * 2;

    /**
     * Preload player sprites (called from background loading thread).
     */
    public static void preloadSprites() {
        if (missileSprite == null) {
            String path = "sprites/Missle Man Assets/Missles/Missle Black.png";
            try {
                missileSprite = AssetLoader.prescaleImage(
                    AssetLoader.loadImage(path), SPRITE_PRESCALE_SIZE);
            } catch (IOException e) {
                System.err.println("Could not load missile sprite: " + path);
            }
        }
        if (missileShadow == null) {
            String path = "sprites/Missle Man Assets/Missles/Missle Black Shadow.png";
            try {
                missileShadow = AssetLoader.prescaleImage(
                    AssetLoader.loadImage(path), SPRITE_PRESCALE_SIZE);
            } catch (IOException e) {
                System.err.println("Could not load missile shadow: " + path);
            }
        }
    }

    private void loadSprite() {
        if (missileSprite == null) {
            String path = "sprites/Missle Man Assets/Missles/Missle Black.png";
            try {
                missileSprite = AssetLoader.prescaleImage(
                    AssetLoader.loadImage(path), SPRITE_PRESCALE_SIZE);
            } catch (IOException e) {
                System.err.println("Could not load missile sprite: " + path);
            }
        }
        if (missileShadow == null) {
            String path = "sprites/Missle Man Assets/Missles/Missle Black Shadow.png";
            try {
                missileShadow = AssetLoader.prescaleImage(
                    AssetLoader.loadImage(path), SPRITE_PRESCALE_SIZE);
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
        
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_UP)) ay -= ACCELERATION;
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_DOWN)) ay += ACCELERATION;
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_LEFT)) ax -= ACCELERATION;
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_RIGHT)) ax += ACCELERATION;
        
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
        
        // Simple squash/stretch based on SPEED and movement angle
        // Moving = stretch along movement direction
        // Stopping = return to normal
        double speedRatio = cachedSpeed / maxSpeed; // 0 to 1
        
        // Extra stretch when dashing
        double dashStretchBonus = isDashing ? 0.4 : 0.0; // 40% extra stretch during dash
        
        // Stretch amount based on speed (faster = more stretch)
        double stretchAmount = speedRatio * 0.25 + dashStretchBonus; // 25% max + dash bonus
        
        // Calculate movement angle for directional stretch
        double moveAngle = Math.atan2(vy, vx);
        
        // Use cosine/sine of angle to determine X/Y stretch
        // cos(0) = 1 for horizontal, cos(90) = 0 for vertical
        // This makes diagonal movement stretch both axes proportionally
        double cosAngle = Math.abs(Math.cos(moveAngle));
        double sinAngle = Math.abs(Math.sin(moveAngle));
        
        // Stretch along movement, compress perpendicular
        // Diagonal movement (45°) has cos=sin=0.707, so both axes stretch equally
        double targetSquashX = 1.0 + stretchAmount * cosAngle - stretchAmount * sinAngle * 0.3;
        double targetSquashY = 1.0 + stretchAmount * sinAngle - stretchAmount * cosAngle * 0.3;
        
        // Clamp to reasonable values (wider range for dash)
        double minSquash = isDashing ? 0.5 : 0.7;
        double maxSquash = isDashing ? 1.5 : 1.3;
        targetSquashX = Math.max(minSquash, Math.min(maxSquash, targetSquashX));
        targetSquashY = Math.max(minSquash, Math.min(maxSquash, targetSquashY));
        
        // Smoothly interpolate squash values (faster during dash for snappy feel)
        double lerpSpeed = isDashing ? 0.3 : 0.15;
        squashX += (targetSquashX - squashX) * lerpSpeed;
        squashY += (targetSquashY - squashY) * lerpSpeed;
        
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
        
        // Draw dark glow shadow centered underneath
        if (Game.enableShadows && missileShadow != null) {
            double objectRotation = angle + Math.PI / 2;
            g2d.rotate(objectRotation);
            
            // Number of layers based on shadow quality: Low=3, Medium=6, High=10
            int layerCount = Game.shadowQuality == 1 ? 3 : Game.shadowQuality == 2 ? 6 : 10;
            
            // Draw layers from outermost (largest, most transparent) to innermost
            for (int i = 0; i < layerCount; i++) {
                // t goes from 0.0 (outermost) to 1.0 (innermost)
                double t = (layerCount == 1) ? 1.0 : (double)i / (layerCount - 1);
                double layerScale = SHADOW_MAX_SCALE + (SHADOW_MIN_SCALE - SHADOW_MAX_SCALE) * t;
                float layerAlpha = SHADOW_MIN_ALPHA + (SHADOW_MAX_ALPHA - SHADOW_MIN_ALPHA) * (float)t;
                
                int lw = (int)(spriteWidth * layerScale);
                int lh = (int)(spriteHeight * layerScale);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * layerAlpha));
                g2d.drawImage(missileShadow,
                    (int)(-lw / 2), (int)(-lh / 2 + SHADOW_GLOW_OFFSET_Y),
                    lw, lh, null);
            }
            
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
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
        
        // Draw hitbox (small red dot at center) - only when hitboxes enabled
        if (Game.enableHitboxes) {
            g.setColor(Color.RED);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.5f));
            g.fillOval((int)x - 2, (int)y - 2, 4, 4);
        }
        
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
    
    // Reset velocity to zero (for respawn)
    public void resetVelocity() {
        this.vx = 0;
        this.vy = 0;
        this.cachedSpeed = 0;
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
    
    public void setKeyBindManager(KeyBindManager keyBindManager) { this.keyBindManager = keyBindManager; }
    public void setControllerManager(ControllerManager controllerManager) { this.controllerManager = controllerManager; }

    /**
     * Check if a movement direction is pressed (keyboard or controller).
     */
    private boolean isMovementPressed(boolean[] keys, KeyBindManager.Action action) {
        if (keyBindManager != null) {
            int keyCode = keyBindManager.getKey(action);
            if (keyCode >= 0 && keyCode < keys.length && keys[keyCode]) return true;
        } else {
            // Fallback to hardcoded WASD/Arrows if no keybind manager
            switch (action) {
                case MOVE_UP: return keys[KeyEvent.VK_W] || keys[KeyEvent.VK_UP];
                case MOVE_DOWN: return keys[KeyEvent.VK_S] || keys[KeyEvent.VK_DOWN];
                case MOVE_LEFT: return keys[KeyEvent.VK_A] || keys[KeyEvent.VK_LEFT];
                case MOVE_RIGHT: return keys[KeyEvent.VK_D] || keys[KeyEvent.VK_RIGHT];
                default: return false;
            }
        }
        // Also check controller
        if (controllerManager != null && controllerManager.isConnected()) {
            if (controllerManager.isActionPressed(action)) return true;
        }
        return false;
    }

    public void applyDashImpulse(double multiplier, boolean[] keys) {
        // Get current input direction
        double dashX = 0;
        double dashY = 0;
        
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_UP)) dashY -= 1;
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_DOWN)) dashY += 1;
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_LEFT)) dashX -= 1;
        if (isMovementPressed(keys, KeyBindManager.Action.MOVE_RIGHT)) dashX += 1;
        
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
