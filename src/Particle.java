import java.awt.*;

public class Particle {
    private double x, y;
    private double vx, vy;
    private Color color;
    private int lifetime;
    private int maxLifetime;
    private double size;
    private ParticleType type;
    
    public enum ParticleType {
        SPARK,      // Quick burst
        TRAIL,      // Smooth trail
        EXPLOSION,  // Expanding circle
        DODGE       // Lucky dodge effect
    }
    
    public Particle(double x, double y, double vx, double vy, Color color, int lifetime, double size, ParticleType type) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.color = color;
        this.lifetime = lifetime;
        this.maxLifetime = lifetime;
        this.size = size;
        this.type = type;
    }
    
    public void update(double deltaTime) {
        // Update position
        x += vx * deltaTime;
        y += vy * deltaTime;
        
        // Apply gravity for certain types
        if (type == ParticleType.SPARK || type == ParticleType.EXPLOSION) {
            vy += 0.2 * deltaTime;
        }
        
        // Fade out and slow down
        lifetime -= deltaTime;
        vx *= 0.98;
        vy *= 0.98;
    }
    
    public void draw(Graphics2D g) {
        float alpha = Math.max(0, Math.min(1, (float)lifetime / maxLifetime));
        
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        switch (type) {
            case SPARK:
                g.setColor(color);
                g.fillOval((int)(x - size/2), (int)(y - size/2), (int)size, (int)size);
                break;
                
            case TRAIL:
                g.setColor(color);
                int trailLength = (int)(size * 2);
                g.setStroke(new BasicStroke((float)size));
                g.drawLine((int)x, (int)y, (int)(x - vx * trailLength), (int)(y - vy * trailLength));
                break;
                
            case EXPLOSION:
                double expansionSize = size * (1 + (maxLifetime - lifetime) / (double)maxLifetime * 2);
                g.setColor(color);
                g.setStroke(new BasicStroke(3f));
                g.drawOval((int)(x - expansionSize/2), (int)(y - expansionSize/2), (int)expansionSize, (int)expansionSize);
                break;
                
            case DODGE:
                double dodgeSize = size * (1 + (maxLifetime - lifetime) / (double)maxLifetime);
                g.setColor(color);
                g.fillOval((int)(x - dodgeSize/2), (int)(y - dodgeSize/2), (int)dodgeSize, (int)dodgeSize);
                break;
        }
        
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }
    
    public boolean isAlive() {
        return lifetime > 0;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
}
