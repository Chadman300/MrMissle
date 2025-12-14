import java.awt.*;

public class UIButton {
    private String text;
    private int x, y, width, height;
    private boolean isSelected;
    private Color baseColor;
    private Color selectedColor;
    private double swayOffset;
    private double scaleAmount;
    
    public UIButton(String text, int x, int y, int width, int height) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseColor = new Color(59, 66, 82);
        this.selectedColor = new Color(143, 188, 187);
        this.swayOffset = 0;
        this.scaleAmount = 1.0;
    }
    
    public UIButton(String text, int x, int y, int width, int height, Color baseColor, Color selectedColor) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseColor = baseColor;
        this.selectedColor = selectedColor;
        this.swayOffset = 0;
        this.scaleAmount = 1.0;
    }
    
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public void update(boolean selected, double time) {
        this.isSelected = selected;
        
        if (selected) {
            // Subtle sway animation (reduced from 5 to 2 pixels)
            swayOffset = Math.sin(time * 3) * 2;
            // Subtle scale animation (reduced from 0.05 to 0.015 = 1.5%)
            scaleAmount = 1.0 + Math.sin(time * 4) * 0.015;
        } else {
            swayOffset = 0;
            scaleAmount = 1.0;
        }
    }
    
    public void draw(Graphics2D g, double time) {
        // Create graphics context for button
        Graphics2D g2 = (Graphics2D) g.create();
        
        // Calculate center
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        
        // Apply sway
        g2.translate(swayOffset, 0);
        
        // Apply scale from center (only to button shape, not text)
        g2.translate(centerX, centerY);
        g2.scale(scaleAmount, scaleAmount);
        g2.translate(-centerX, -centerY);
        
        // Draw shadow
        if (isSelected) {
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRoundRect(x + 6, y + 6, width, height, 20, 20);
        }
        
        // Draw button background with gradient
        if (isSelected) {
            GradientPaint grad = new GradientPaint(
                x, y, selectedColor,
                x, y + height, new Color(136, 192, 208)
            );
            g2.setPaint(grad);
        } else {
            g2.setColor(baseColor);
        }
        g2.fillRoundRect(x, y, width, height, 20, 20);
        
        // Draw border
        g2.setStroke(new BasicStroke(3));
        if (isSelected) {
            g2.setColor(new Color(235, 203, 139));
            // Animated glowing border
            int glowAlpha = (int)(Math.abs(Math.sin(time * 5)) * 155 + 100);
            g2.setColor(new Color(235, 203, 139, glowAlpha));
            g2.setStroke(new BasicStroke(4));
        } else {
            g2.setColor(new Color(76, 86, 106));
        }
        g2.drawRoundRect(x, y, width, height, 20, 20);
        
        // Reset transform for text so it doesn't scale
        g2.dispose();
        g2 = (Graphics2D) g.create();
        g2.translate(swayOffset, 0); // Only apply sway to text, not scale
        
        // Draw text at constant size
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        int textX = x + (width - fm.stringWidth(text)) / 2;
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
        
        // Text shadow
        g2.setColor(new Color(0, 0, 0, 150));
        g2.drawString(text, textX + 2, textY + 2);
        
        // Main text
        g2.setColor(isSelected ? Color.WHITE : new Color(216, 222, 233));
        g2.drawString(text, textX, textY);
        
        // Shine effect for selected
        if (isSelected) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            g2.setColor(Color.WHITE);
            int shineY = y + (int)(Math.sin(time * 4) * height / 4 + height / 2);
            g2.fillRoundRect(x, shineY - 10, width, 20, 20, 20);
        }
        
        g2.dispose();
    }
    
    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
