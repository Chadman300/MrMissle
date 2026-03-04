import java.awt.*;

public class DamageNumber {
    private String text;
    private double x, y;
    private double vy; // Velocity upward
    private double lifetime;
    private double maxLifetime;
    private Color color;
    private int fontSize;
    private Font cachedFont; // Cached to avoid per-frame Font allocation
    
    public DamageNumber(String text, double x, double y, Color color, int fontSize) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.vy = -2.0; // Float upward
        this.color = color;
        this.fontSize = fontSize;
        this.maxLifetime = 60;
        this.lifetime = 0;
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
}
