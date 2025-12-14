import java.awt.*;

public class Particle {
    private double x, y;
    private double vx, vy;
    private Color color;
    private int lifetime;
    private int maxLifetime;
    private double size;
    private ParticleType type;
    
    // Cached values to avoid recomputation
    private double progress; // Cached progress (0 to 1)
    private double expansionSize; // Cached expansion size for SMOKE/DODGE
    
    // Cached AlphaComposite instances for performance
    private static final AlphaComposite[] ALPHA_CACHE = new AlphaComposite[101];
    private static final BasicStroke STROKE_3 = new BasicStroke(3f);
    private static final BasicStroke[] STROKE_CACHE = new BasicStroke[20];
    static {
        for (int i = 0; i <= 100; i++) {
            ALPHA_CACHE[i] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, i / 100f);
        }
        for (int i = 0; i < 20; i++) {
            STROKE_CACHE[i] = new BasicStroke(i * 0.5f);
        }
    }
    
    public enum ParticleType {
        SPARK,      // Quick burst
        TRAIL,      // Smooth trail
        EXPLOSION,  // Expanding circle
        DODGE,      // Lucky dodge effect
        SMOKE       // Soft, expanding smoke puffs
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
    
    // Reset particle for pooling
    public void reset(double x, double y, double vx, double vy, Color color, int lifetime, double size, ParticleType type) {
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
        
        // Pre-compute progress for rendering
        progress = 1.0 - (double)lifetime / maxLifetime;
        
        // Pre-compute expansion size for SMOKE and DODGE types
        if (type == ParticleType.SMOKE) {
            expansionSize = size * (1.5 + progress * 2.5);
        } else if (type == ParticleType.DODGE) {
            expansionSize = size * (1 + (maxLifetime - lifetime) / (double)maxLifetime);
        } else if (type == ParticleType.EXPLOSION) {
            expansionSize = size * (1 + progress * 2);
        }
    }
    
    public void draw(Graphics2D g) {
        float alpha = Math.max(0, Math.min(1, (float)lifetime / maxLifetime));
        int alphaIndex = (int)(alpha * 100);
        
        g.setComposite(ALPHA_CACHE[alphaIndex]);
        
        switch (type) {
            case SPARK:
                g.setColor(color);
                g.fillOval((int)(x - size/2), (int)(y - size/2), (int)size, (int)size);
                break;
                
            case TRAIL:
                g.setColor(color);
                int trailLength = (int)(size * 2);
                int strokeIndex = Math.min(19, Math.max(0, (int)(size * 2)));
                g.setStroke(STROKE_CACHE[strokeIndex]);
                g.drawLine((int)x, (int)y, (int)(x - vx * trailLength), (int)(y - vy * trailLength));
                break;
                
            case EXPLOSION:
                g.setColor(color);
                g.setStroke(STROKE_3);
                g.drawOval((int)(x - expansionSize/2), (int)(y - expansionSize/2), (int)expansionSize, (int)expansionSize);
                break;
                
            case DODGE:
                g.setColor(color);
                g.fillOval((int)(x - expansionSize/2), (int)(y - expansionSize/2), (int)expansionSize, (int)expansionSize);
                break;
                
            case SMOKE:
                // Smoke expands and fades - softer, larger look
                int baseAlpha = color.getAlpha();
                int fadedAlpha = (int)(baseAlpha * alpha * 0.6);
                
                // Outer soft layer (use cached color components)
                int r = color.getRed();
                int gColor = color.getGreen();
                int b = color.getBlue();
                g.setColor(new Color(r, gColor, b, Math.max(0, fadedAlpha / 2)));
                g.fillOval((int)(x - expansionSize * 0.7), (int)(y - expansionSize * 0.7), 
                          (int)(expansionSize * 1.4), (int)(expansionSize * 1.4));
                
                // Core layer
                g.setColor(new Color(r, gColor, b, Math.max(0, fadedAlpha)));
                g.fillOval((int)(x - expansionSize/2), (int)(y - expansionSize/2), (int)expansionSize, (int)expansionSize);
                break;
        }
        
        g.setComposite(ALPHA_CACHE[100]);
    }
    
    public boolean isAlive() {
        return lifetime > 0;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
}
