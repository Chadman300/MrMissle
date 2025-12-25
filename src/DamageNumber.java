import java.awt.*;
import java.awt.geom.AffineTransform;

public class DamageNumber {
    private String text;
    private double x, y;
    private double vy; // Velocity upward
    private int lifetime;
    private int maxLifetime;
    private Color color;
    private int fontSize;
    
    public DamageNumber(String text, double x, double y, Color color, int fontSize) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.vy = -2.0; // Float upward
        this.color = color;
        this.fontSize = fontSize;
        this.maxLifetime = 60; // 1 second
        this.lifetime = 0;
    }
    
    public void update(double deltaTime) {
        y += vy * deltaTime;
        vy *= 0.95; // Slow down
        lifetime += deltaTime;
    }
    
    public boolean isDone() {
        return lifetime >= maxLifetime;
    }
    
    public void draw(Graphics2D g) {
        // Check if this is a boss damage number
        boolean isBossDamage = text.startsWith("BOSS HP:") || text.equals("BOSS DEFEATED!");
        
        if (isBossDamage) {
            // Use dramatic centered style for boss damage
            drawBossDamage(g);
        } else {
            // Regular damage number
            float alpha = 1.0f - ((float)lifetime / maxLifetime);
            Color drawColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
            
            g.setColor(drawColor);
            g.setFont(new Font("Arial", Font.BOLD, fontSize));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (int)(x - fm.stringWidth(text) / 2), (int)y);
        }
    }
    
    private void drawBossDamage(Graphics2D g) {
        float progress = (float)lifetime / maxLifetime;
        
        // Scale in then fade out effect (like combo announcements)
        float scale = progress > 0.8f ? 
            (1.0f - progress) / 0.2f * 0.5f + 1.0f : // Scale up from 1.0 to 1.5
            Math.min(1.5f, 1.5f - (0.8f - progress) * 0.25f); // Settle to 1.25
        float alpha = Math.min(1.0f, progress * 2.0f); // Fade out in last half
        
        AffineTransform bossTransform = g.getTransform();
        int centerX = (int)x;
        int centerY = (int)y;
        
        g.translate(centerX, centerY);
        g.scale(scale, scale);
        g.translate(-centerX, -centerY);
        
        // Draw shadow
        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        
        g.setColor(new Color(0, 0, 0, (int)(180 * alpha)));
        g.drawString(text, centerX - textWidth / 2 + 4, centerY + 4);
        
        // Main text
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * alpha)));
        g.drawString(text, centerX - textWidth / 2, centerY);
        
        g.setTransform(bossTransform);
    }
}
