import java.awt.*;
import java.awt.geom.AffineTransform;

public class DamageNumber {
    private String text;
    private double x, y;
    private double vy; // Velocity upward
    private double lifetime;
    private double maxLifetime;
    private Color color;
    private int fontSize;
    private Font cachedFont; // Cached to avoid per-frame Font allocation
    private boolean fancy; // Announcement-style rendering (pop-in, sway, outline)
    
    public DamageNumber(String text, double x, double y, Color color, int fontSize) {
        this(text, x, y, color, fontSize, false);
    }
    
    public DamageNumber(String text, double x, double y, Color color, int fontSize, boolean fancy) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.vy = fancy ? -0.8 : -2.0; // Fancy floats slower
        this.color = color;
        this.fontSize = fontSize;
        this.maxLifetime = fancy ? 100 : 60; // Fancy lasts longer
        this.lifetime = 0;
        this.fancy = fancy;
        this.cachedFont = new Font("Arial", Font.BOLD, config.UIScale.fontSize(fontSize));
    }
    
    public void update(double deltaTime) {
        y += vy * deltaTime;
        vy *= (deltaTime == 1.0) ? 0.95 : Math.pow(0.95, deltaTime); // Slow down (fast path for fixed timestep)
        lifetime += deltaTime;
    }
    
    public boolean isDone() {
        return lifetime >= maxLifetime;
    }
    
    public void draw(Graphics2D g) {
        if (fancy) {
            drawFancy(g);
        } else {
            drawSimple(g);
        }
    }
    
    private void drawSimple(Graphics2D g) {
        float alpha = 1.0f - (float)(lifetime / maxLifetime);
        // Use RenderCache for alpha composite instead of allocating new Color per frame
        Composite savedComp = g.getComposite();
        g.setComposite(RenderCache.getAlpha(alpha));
        g.setColor(color);
        g.setFont(cachedFont);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (int)(x - fm.stringWidth(text) / 2), (int)y);
        g.setComposite(savedComp);
    }
    
    /** Announcement-style: elastic pop-in, gentle sway, outline + glow, smooth fade */
    private void drawFancy(Graphics2D g) {
        float lifeProgress = (float)(lifetime / maxLifetime); // 0.0 = just spawned, 1.0 = done
        
        // Elastic pop-in scale
        float scale;
        if (lifeProgress < 0.12f) {
            float t = lifeProgress / 0.12f;
            scale = 1.4f * easeOutBack(t);
        } else if (lifeProgress < 0.22f) {
            float t = (lifeProgress - 0.12f) / 0.1f;
            scale = 1.4f - 0.4f * t;
        } else {
            scale = 1.0f + 0.03f * (float)Math.sin(lifeProgress * Math.PI * 5);
        }
        
        // Alpha: quick fade in, hold, smooth fade out
        float alpha;
        if (lifeProgress < 0.08f) {
            alpha = lifeProgress / 0.08f;
        } else if (lifeProgress < 0.65f) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (lifeProgress - 0.65f) / 0.35f;
        }
        alpha = Math.max(0, Math.min(1, alpha));
        
        // Gentle sway
        float swayAngle = (float)(Math.sin(lifeProgress * Math.PI * 6) * Math.PI / 28);
        swayAngle *= Math.min(1.0f, (1.0f - lifeProgress) * 2);
        
        AffineTransform saved = g.getTransform();
        Composite savedComp = g.getComposite();
        
        g.translate(x, y);
        g.rotate(swayAngle);
        g.scale(scale, scale);
        
        g.setFont(cachedFont);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        int tx = -tw / 2;
        int ty = fm.getAscent() / 3;
        
        // Outline / glow
        g.setComposite(RenderCache.getAlpha(alpha * 0.4f));
        g.setColor(Color.BLACK);
        for (int ox = -3; ox <= 3; ox++) {
            for (int oy = -3; oy <= 3; oy++) {
                if (ox != 0 || oy != 0) {
                    g.drawString(text, tx + ox, ty + oy);
                }
            }
        }
        
        // Shadow
        g.setComposite(RenderCache.getAlpha(alpha * 0.6f));
        g.setColor(Color.BLACK);
        g.drawString(text, tx + 3, ty + 3);
        
        // Main text
        g.setComposite(RenderCache.getAlpha(alpha));
        g.setColor(color);
        g.drawString(text, tx, ty);
        
        // Shine highlight
        g.setComposite(RenderCache.getAlpha(alpha * 0.3f));
        g.setColor(Color.WHITE);
        g.drawString(text, tx, ty - 1);
        
        g.setComposite(savedComp);
        g.setTransform(saved);
    }
    
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float)Math.pow(t - 1, 3) + c1 * (float)Math.pow(t - 1, 2);
    }
}
