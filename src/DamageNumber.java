import java.awt.*;

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
        float alpha = 1.0f - ((float)lifetime / maxLifetime);
        Color drawColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(alpha * 255));
        
        g.setColor(drawColor);
        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (int)(x - fm.stringWidth(text) / 2), (int)y);
    }
}
