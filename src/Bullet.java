import java.awt.*;

public class Bullet {
    private double x, y;
    private double vx, vy;
    private static final int SIZE = 8;
    private BulletType type;
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
        
        // Calculate rotation angle based on velocity
        // Type-specific appearance - vibrant colored orbs with better contrast
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
