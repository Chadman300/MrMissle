import java.awt.*;
import java.awt.geom.AffineTransform;

public class Particle {
    private double x, y;
    private double vx, vy;
    private Color color;
    private double lifetime;
    private double maxLifetime;
    private double size;
    private ParticleType type;
    
    // Cached values to avoid recomputation
    private double progress; // Cached progress (0 to 1)
    private double expansionSize; // Cached expansion size for SMOKE/DODGE
    
    // Rotation for DEBRIS particles
    private double rotation;
    private double rotationSpeed;
    
    // Use RenderCache for AlphaComposite and BasicStroke (avoid duplicate caches)
    private static final BasicStroke STROKE_3 = new BasicStroke(3f);
    private static Font cachedMoneyFont = null; // Cached font for MONEY_SIGN particles
    
    // Cached colors for FLARE_SPARK (avoid per-frame allocation)
    private static final Color FLARE_SPARK_OUTER = new Color(255, 60, 30);
    private static final Color FLARE_SPARK_INNER = new Color(255, 100, 50);
    
    public enum ParticleType {
        SPARK,      // Quick burst
        TRAIL,      // Smooth trail
        EXPLOSION,  // Expanding circle
        DODGE,      // Lucky dodge effect
        SMOKE,      // Soft, expanding smoke puffs
        MONEY_SIGN, // Falling money sign from Pool of Loot
        EXHAUST,    // Rocket exhaust - like SPARK but no gravity
        DEBRIS,     // Spinning missile fragments with gravity
        FLARE_SPARK // Glowing spark for flare effects
    }
    
    public Particle(double x, double y, double vx, double vy, Color color, double lifetime, double size, ParticleType type) {
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
    public void reset(double x, double y, double vx, double vy, Color color, double lifetime, double size, ParticleType type) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.color = color;
        this.lifetime = lifetime;
        this.maxLifetime = lifetime;
        this.size = size;
        this.type = type;
        if (type == ParticleType.DEBRIS) {
            this.rotation = Math.random() * Math.PI * 2;
            this.rotationSpeed = -0.3 + Math.random() * 0.6;
        } else {
            this.rotation = 0;
            this.rotationSpeed = 0;
        }
    }
    
    public void update(double deltaTime) {
        // Update position
        x += vx * deltaTime;
        y += vy * deltaTime;
        
        // Apply gravity for certain types (not EXPLOSION — shockwave rings should expand in place)
        if (type == ParticleType.SPARK || type == ParticleType.DEBRIS) {
            vy += 0.2 * deltaTime;
        }
        
        // Update rotation for DEBRIS
        if (type == ParticleType.DEBRIS) {
            rotation += rotationSpeed * deltaTime;
        }
        
        // Fade out and slow down
        lifetime -= deltaTime;
        // deltaTime is always 1.0 (fixed timestep), so Math.pow(0.98, 1.0) == 0.98
        // Avoiding Math.pow() saves ~12,000 calls/sec at max particles
        double damping = (deltaTime == 1.0) ? 0.98 : Math.pow(0.98, deltaTime);
        vx *= damping;
        vy *= damping;
        
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
        float alpha = (float)Math.max(0, Math.min(1, lifetime / maxLifetime));
        int alphaIndex = (int)(alpha * 100);
        
        g.setComposite(RenderCache.getAlpha(alpha));
        
        switch (type) {
            case SPARK:
            case EXHAUST:
                g.setColor(color);
                g.fillOval((int)(x - size/2), (int)(y - size/2), (int)size, (int)size);
                break;
                
            case TRAIL:
                g.setColor(color);
                int trailLength = (int)(size * 2);
                g.setStroke(RenderCache.getStroke((float)(size * 2)));
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
                
                int outerAlpha = Math.max(0, fadedAlpha / 2);
                int coreAlpha = Math.max(0, fadedAlpha);
                
                // Outer soft layer - use composite for alpha instead of color alpha
                g.setComposite(RenderCache.getAlpha(Math.min(1.0f, outerAlpha / 255f)));
                g.setColor(color);
                g.fillOval((int)(x - expansionSize * 0.7), (int)(y - expansionSize * 0.7), 
                          (int)(expansionSize * 1.4), (int)(expansionSize * 1.4));
                
                // Core layer
                g.setComposite(RenderCache.getAlpha(Math.min(1.0f, coreAlpha / 255f)));
                g.fillOval((int)(x - expansionSize/2), (int)(y - expansionSize/2), (int)expansionSize, (int)expansionSize);
                break;
                
            case MONEY_SIGN:
                // Draw a falling "$" sign - use cached font
                g.setColor(color);
                if (cachedMoneyFont == null) cachedMoneyFont = new Font("Arial", Font.BOLD, (int)size);
                g.setFont(cachedMoneyFont);
                g.drawString("$", (int)x, (int)y);
                break;
                
            case DEBRIS:
                // Draw a spinning missile fragment (small rotated rectangle)
                // Save state instead of g.create() — avoids Graphics2D copy per particle
                AffineTransform debrisTx = g.getTransform();
                g.setComposite(RenderCache.getAlpha(alpha));
                g.translate(x, y);
                g.rotate(rotation);
                g.setColor(color);
                int fw = (int)Math.max(2, size * 0.4);
                int fh = (int)Math.max(4, size);
                g.fillRect(-fw / 2, -fh / 2, fw, fh);
                // Bright edge highlight
                g.setComposite(RenderCache.getAlpha(alpha));
                g.setColor(Color.WHITE);
                g.fillRect(-fw / 2, -fh / 2, Math.max(1, fw / 2), fh);
                g.setTransform(debrisTx);
                break;
                
            case FLARE_SPARK:
                // Glowing spark for flare effects
                // Outer glow halo
                int outerGlowSize = (int)(size * 3);
                g.setComposite(RenderCache.getAlpha(alpha * 0.4f));
                g.setColor(FLARE_SPARK_OUTER);
                g.fillOval((int)(x - outerGlowSize/2), (int)(y - outerGlowSize/2), outerGlowSize, outerGlowSize);
                // Inner glow
                int innerGlowSize = (int)(size * 1.8);
                g.setComposite(RenderCache.getAlpha(alpha * 0.8f));
                g.setColor(FLARE_SPARK_INNER);
                g.fillOval((int)(x - innerGlowSize/2), (int)(y - innerGlowSize/2), innerGlowSize, innerGlowSize);
                // Core
                g.setComposite(RenderCache.getAlpha(alpha));
                g.setColor(color);
                g.fillOval((int)(x - size/2), (int)(y - size/2), (int)size, (int)size);
                break;
        }
        
        g.setComposite(RenderCache.ALPHA_FULL);
    }
    
    public boolean isAlive() {
        return lifetime > 0;
    }
    
    public ParticleType getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVX() { return vx; }
    public double getVY() { return vy; }
    public Color getColor() { return color; }
    public double getLifetime() { return lifetime; }
    public double getSize() { return size; }
}
