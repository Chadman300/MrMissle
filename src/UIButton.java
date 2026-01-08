import java.awt.*;

public class UIButton {
    private String text;
    private String icon; // Unicode icon or symbol
    private int x, y, width, height;
    private boolean isSelected;
    private Color baseColor;
    private Color selectedColor;
    private double swayOffset;
    private double scaleAmount;
    
    public UIButton(String text, int x, int y, int width, int height) {
        this.text = text;
        this.icon = null;
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
        this.icon = null;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseColor = baseColor;
        this.selectedColor = selectedColor;
        this.swayOffset = 0;
        this.scaleAmount = 1.0;
    }
    
    public UIButton(String text, String icon, int x, int y, int width, int height, Color baseColor, Color selectedColor) {
        this.text = text;
        this.icon = icon;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseColor = baseColor;
        this.selectedColor = selectedColor;
        this.swayOffset = 0;
        this.scaleAmount = 1.0;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    public void update(boolean selected, double time) {
        this.isSelected = selected;
        
        if (selected) {
            // Convert time to seconds for smooth animation
            double timeInSeconds = time / 1000.0;
            // Subtle sway animation (reduced from 5 to 2 pixels)
            swayOffset = Math.sin(timeInSeconds * 3) * 2;
            // Subtle scale animation (reduced from 0.05 to 0.015 = 1.5%)
            scaleAmount = 1.0 + Math.sin(timeInSeconds * 4) * 0.015;
        } else {
            swayOffset = 0;
            scaleAmount = 1.0;
        }
    }
    
    public void draw(Graphics2D g, double time) {
        // Create graphics context for button
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
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
                x, y + height, selectedColor.darker()
            );
            g2.setPaint(grad);
        } else {
            g2.setColor(baseColor);
        }
        g2.fillRoundRect(x, y, width, height, 20, 20);
        
        // Draw decorative inner line pattern for selected buttons
        if (isSelected) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            for (int i = 0; i < width; i += 8) {
                g2.drawLine(x + i, y, x + i - 20, y + height);
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
        
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
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(swayOffset, 0); // Only apply sway to text, not scale
        
        // Calculate text position (adjusted for icon if present)
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        
        int iconSpace = (icon != null) ? 35 : 0;
        int totalTextWidth = fm.stringWidth(text) + iconSpace;
        int textX = x + (width - totalTextWidth) / 2 + iconSpace;
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
        
        // Draw icon if present
        if (icon != null) {
            int iconX = textX - iconSpace + 5;
            int iconY = y + height / 2;
            int iconSize = 18;
            
            Color iconColor = isSelected ? Color.WHITE : new Color(200, 200, 220);
            Color iconShadow = new Color(0, 0, 0, 100);
            
            drawIcon(g2, icon, iconX, iconY, iconSize, iconColor, iconShadow, time);
        }
        
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
    
    private void drawIcon(Graphics2D g, String iconType, int x, int y, int size, Color color, Color shadow, double time) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Slight rotation animation for selected icons
        if (isSelected) {
            double rotation = Math.sin(time * 3) * 0.1;
            g2.rotate(rotation, x, y);
        }
        
        int halfSize = size / 2;
        
        switch (iconType) {
            case "level": // Play/Triangle icon for Select Level
                // Shadow
                g2.setColor(shadow);
                int[] triXs = {x - halfSize + 3, x - halfSize + 3, x + halfSize + 3};
                int[] triYs = {y - halfSize + 2, y + halfSize + 2, y + 2};
                g2.fillPolygon(triXs, triYs, 3);
                // Icon
                g2.setColor(color);
                int[] triX = {x - halfSize, x - halfSize, x + halfSize};
                int[] triY = {y - halfSize, y + halfSize, y};
                g2.fillPolygon(triX, triY, 3);
                break;
                
            case "shop": // Shopping bag/diamond icon
                // Shadow
                g2.setColor(shadow);
                g2.translate(2, 2);
                drawDiamond(g2, x, y, size);
                g2.translate(-2, -2);
                // Icon
                g2.setColor(color);
                drawDiamond(g2, x, y, size);
                break;
                
            case "stats": // Bar chart icon
                g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Shadow
                g2.setColor(shadow);
                g2.drawLine(x - halfSize + 2, y + halfSize + 2, x - halfSize + 2, y + 2);
                g2.drawLine(x + 2, y + halfSize + 2, x + 2, y - halfSize / 2 + 2);
                g2.drawLine(x + halfSize + 2, y + halfSize + 2, x + halfSize + 2, y - halfSize + 2);
                // Icon
                g2.setColor(color);
                g2.drawLine(x - halfSize, y + halfSize, x - halfSize, y);
                g2.drawLine(x, y + halfSize, x, y - halfSize / 2);
                g2.drawLine(x + halfSize, y + halfSize, x + halfSize, y - halfSize);
                break;
                
            case "achievements": // Trophy/Star icon
                // Shadow
                g2.setColor(shadow);
                drawStar(g2, x + 2, y + 2, size, 5);
                // Icon
                g2.setColor(color);
                drawStar(g2, x, y, size, 5);
                break;
                
            case "info": // Info circle with 'i'
                // Shadow
                g2.setColor(shadow);
                g2.fillOval(x - halfSize + 2, y - halfSize + 2, size, size);
                // Icon background
                g2.setColor(color);
                g2.fillOval(x - halfSize, y - halfSize, size, size);
                // 'i' letter
                g2.setColor(isSelected ? selectedColor : baseColor);
                g2.setFont(new Font("Arial", Font.BOLD, size - 4));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("i", x - fm.stringWidth("i") / 2, y + fm.getAscent() / 2 - 2);
                break;
                
            case "settings": // Gear icon (hexagon with circle)
                // Shadow
                g2.setColor(shadow);
                drawGear(g2, x + 2, y + 2, size);
                // Icon
                g2.setColor(color);
                drawGear(g2, x, y, size);
                break;
        }
        
        g2.dispose();
    }
    
    private void drawDiamond(Graphics2D g, int x, int y, int size) {
        int halfSize = size / 2;
        int[] xPoints = {x, x + halfSize, x, x - halfSize};
        int[] yPoints = {y - halfSize, y, y + halfSize, y};
        g.fillPolygon(xPoints, yPoints, 4);
    }
    
    private void drawStar(Graphics2D g, int x, int y, int size, int points) {
        double outerRadius = size / 2.0;
        double innerRadius = size / 4.0;
        
        int[] xPoints = new int[points * 2];
        int[] yPoints = new int[points * 2];
        
        for (int i = 0; i < points * 2; i++) {
            double angle = Math.PI / 2 + (i * Math.PI / points);
            double radius = (i % 2 == 0) ? outerRadius : innerRadius;
            xPoints[i] = (int)(x + radius * Math.cos(angle));
            yPoints[i] = (int)(y - radius * Math.sin(angle));
        }
        
        g.fillPolygon(xPoints, yPoints, points * 2);
    }
    
    private void drawGear(Graphics2D g, int x, int y, int size) {
        int teeth = 6;
        double outerRadius = size / 2.0;
        double innerRadius = size / 3.0;
        
        int[] xPoints = new int[teeth * 4];
        int[] yPoints = new int[teeth * 4];
        
        for (int i = 0; i < teeth; i++) {
            double baseAngle = (i * Math.PI * 2 / teeth);
            double toothWidth = Math.PI / (teeth * 2);
            
            // Outer point 1
            xPoints[i * 4] = (int)(x + outerRadius * Math.cos(baseAngle - toothWidth / 2));
            yPoints[i * 4] = (int)(y + outerRadius * Math.sin(baseAngle - toothWidth / 2));
            // Outer point 2
            xPoints[i * 4 + 1] = (int)(x + outerRadius * Math.cos(baseAngle + toothWidth / 2));
            yPoints[i * 4 + 1] = (int)(y + outerRadius * Math.sin(baseAngle + toothWidth / 2));
            // Inner point 1
            double midAngle = baseAngle + Math.PI / teeth;
            xPoints[i * 4 + 2] = (int)(x + innerRadius * Math.cos(midAngle - toothWidth));
            yPoints[i * 4 + 2] = (int)(y + innerRadius * Math.sin(midAngle - toothWidth));
            // Inner point 2
            xPoints[i * 4 + 3] = (int)(x + innerRadius * Math.cos(midAngle + toothWidth));
            yPoints[i * 4 + 3] = (int)(y + innerRadius * Math.sin(midAngle + toothWidth));
        }
        
        g.fillPolygon(xPoints, yPoints, teeth * 4);
        
        // Center hole
        Color orig = g.getColor();
        g.setColor(isSelected ? selectedColor.darker() : baseColor);
        int holeSize = size / 4;
        g.fillOval(x - holeSize / 2, y - holeSize / 2, holeSize, holeSize);
        g.setColor(orig);
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
