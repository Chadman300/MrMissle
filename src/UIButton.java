import config.ColorPalette;
import config.FontPalette;
import config.UITheme;
import java.awt.*;
import java.awt.geom.Path2D;

public class UIButton {
    private String text;
    private String icon; // Icon type identifier
    private int x, y, width, height;
    private boolean isSelected;
    private Color baseColor;
    private Color selectedColor;
    private double swayOffset;
    private double scaleAmount;
    
    // Chamfer size for the angular dog-tag look
    private static final int CHAMFER = 10;
    
    public UIButton(String text, int x, int y, int width, int height) {
        this.text = text;
        this.icon = null;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseColor = ColorPalette.BUTTON_BASE;
        this.selectedColor = ColorPalette.BUTTON_SELECTED;
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
        if (Game.enableAntiAliasing) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        
        // Calculate center
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        
        // Apply sway
        g2.translate(swayOffset, 0);
        
        // Apply scale from center
        g2.translate(centerX, centerY);
        g2.scale(scaleAmount, scaleAmount);
        g2.translate(-centerX, -centerY);
        
        // Build chamfered button shape (angular dog-tag look)
        Path2D.Double shape = UITheme.createChamferedRect(x, y, width, height, CHAMFER);
        
        // Draw shadow
        if (isSelected) {
            g2.setColor(new Color(0, 0, 0, 120));
            Path2D.Double shadowShape = UITheme.createChamferedRect(x + 5, y + 5, width, height, CHAMFER);
            g2.fill(shadowShape);
        }
        
        // Draw button background
        if (isSelected) {
            // Metallic gradient for selected
            GradientPaint grad = new GradientPaint(
                x, y, selectedColor.brighter(),
                x, y + height, selectedColor.darker()
            );
            g2.setPaint(grad);
        } else {
            // Flat dark steel for unselected
            GradientPaint grad = new GradientPaint(
                x, y, baseColor.brighter(),
                x, y + height, baseColor
            );
            g2.setPaint(grad);
        }
        g2.fill(shape);
        
        // Caution-tape diagonal stripes for selected buttons
        if (isSelected) {
            Shape oldClip = g2.getClip();
            g2.setClip(shape);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.07f));
            g2.setColor(ColorPalette.ACCENT_YELLOW);
            for (int i = -height; i < width + height; i += 12) {
                g2.drawLine(x + i, y, x + i - height, y + height);
            }
            g2.setClip(oldClip);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
        
        // Inner glow line at top for selected
        if (isSelected) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            g2.setColor(ColorPalette.ACCENT_ORANGE);
            g2.setStroke(new BasicStroke(1));
            g2.drawLine(x + CHAMFER + 3, y + 1, x + width - 3, y + 1);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
        
        // Draw border
        g2.setStroke(new BasicStroke(2));
        if (isSelected) {
            // Animated glowing border
            int glowAlpha = (int)(Math.abs(Math.sin(time * 5)) * 155 + 100);
            g2.setColor(new Color(
                ColorPalette.ACCENT_ORANGE.getRed(),
                ColorPalette.ACCENT_ORANGE.getGreen(),
                ColorPalette.ACCENT_ORANGE.getBlue(),
                glowAlpha
            ));
            g2.setStroke(new BasicStroke(3));
        } else {
            g2.setColor(ColorPalette.BORDER_STEEL);
        }
        g2.draw(shape);
        
        // Accent line on left edge
        if (isSelected) {
            g2.setColor(selectedColor);
            g2.setStroke(new BasicStroke(3));
            g2.drawLine(x, y + CHAMFER + 3, x, y + height - 3);
        }
        
        // Reset transform for text so it doesn't scale
        g2.dispose();
        g2 = (Graphics2D) g.create();
        if (Game.enableAntiAliasing) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g2.translate(swayOffset, 0); // Only apply sway to text, not scale
        
        // Calculate text position (adjusted for icon if present)
        g2.setFont(FontPalette.getDisplay(Font.BOLD, 20));
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
            
            Color iconColor = isSelected ? Color.WHITE : new Color(180, 185, 200);
            Color iconShadow = new Color(0, 0, 0, 120);
            
            drawIcon(g2, icon, iconX, iconY, iconSize, iconColor, iconShadow, time);
        }
        
        // Text shadow
        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawString(text, textX + 2, textY + 2);
        
        // Main text
        g2.setColor(isSelected ? Color.WHITE : ColorPalette.TEXT_PRIMARY);
        g2.drawString(text, textX, textY);
        
        // Shine sweep for selected
        if (isSelected) {
            Path2D.Double btnShape = UITheme.createChamferedRect(
                x + (int)swayOffset, y, width, height, CHAMFER);
            Shape oldClip = g2.getClip();
            g2.setClip(btnShape);
            int shineX = x + (int)((Math.sin(time * 2) + 1) / 2 * (width + 60)) - 30;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
            GradientPaint shine = new GradientPaint(shineX, y, new Color(255, 255, 255, 0),
                shineX + 30, y, Color.WHITE);
            g2.setPaint(shine);
            g2.fillRect(shineX, y, 60, height);
            g2.setClip(oldClip);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
        
        g2.dispose();
    }
    
    private void drawIcon(Graphics2D g, String iconType, int x, int y, int size, Color color, Color shadow, double time) {
        Graphics2D g2 = (Graphics2D) g.create();
        if (Game.enableAntiAliasing) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        
        // Slight rotation animation for selected icons
        if (isSelected) {
            double rotation = Math.sin(time * 3) * 0.1;
            g2.rotate(rotation, x, y);
        }
        
        int halfSize = size / 2;
        
        switch (iconType) {
            case "level": // Missile silhouette for Select Level
                // Shadow
                g2.setColor(shadow);
                drawMissileIcon(g2, x + 2, y + 2, size);
                // Icon
                g2.setColor(color);
                drawMissileIcon(g2, x, y, size);
                break;
                
            case "shop": // Ammo crate icon
                // Shadow
                g2.setColor(shadow);
                g2.fillRect(x - halfSize + 2, y - halfSize + 2, size, size - 4);
                // Icon
                g2.setColor(color);
                g2.fillRect(x - halfSize, y - halfSize, size, size - 4);
                // Cross on crate
                g2.setColor(isSelected ? selectedColor : baseColor);
                g2.setStroke(new BasicStroke(2));
                g2.drawLine(x - halfSize + 3, y, x + halfSize - 3, y);
                g2.drawLine(x, y - halfSize + 2, x, y + halfSize - 4);
                break;
                
            case "stats": // Radar sweep icon
                g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Shadow
                g2.setColor(shadow);
                g2.drawOval(x - halfSize + 2, y - halfSize + 2, size, size);
                // Icon
                g2.setColor(color);
                g2.drawOval(x - halfSize, y - halfSize, size, size);
                // Sweep line
                double sweepAngle = time * 3;
                int sx = x + (int)(halfSize * Math.cos(sweepAngle));
                int sy = y + (int)(halfSize * Math.sin(sweepAngle));
                g2.drawLine(x, y, sx, sy);
                g2.fillOval(x - 2, y - 2, 4, 4);
                break;
                
            case "achievements": // Medal icon
                // Shadow
                g2.setColor(shadow);
                g2.fillOval(x - halfSize + 2, y - halfSize / 2 + 2, size - 2, size - 2);
                // Medal circle
                g2.setColor(color);
                g2.fillOval(x - halfSize, y - halfSize / 2, size - 2, size - 2);
                // Ribbon below
                g2.setColor(isSelected ? ColorPalette.ACCENT_RED : new Color(150, 80, 80));
                int ry = y + halfSize / 2 + 2;
                g2.fillRect(x - 4, ry, 3, 6);
                g2.fillRect(x + 2, ry, 3, 6);
                // Star on medal
                g2.setColor(isSelected ? selectedColor.darker() : baseColor);
                drawStar(g2, x, y + 1, size / 2, 5);
                break;
                
            case "info": // Clipboard icon
                // Shadow
                g2.setColor(shadow);
                g2.fillRect(x - halfSize + 4, y - halfSize + 2, size - 6, size + 2);
                // Clipboard body
                g2.setColor(color);
                g2.fillRect(x - halfSize + 2, y - halfSize, size - 6, size + 2);
                // Clip at top
                g2.setColor(isSelected ? selectedColor : baseColor);
                g2.fillRect(x - 3, y - halfSize - 2, 6, 4);
                // Lines on clipboard
                g2.setStroke(new BasicStroke(1));
                g2.drawLine(x - halfSize + 5, y - 2, x + halfSize - 5, y - 2);
                g2.drawLine(x - halfSize + 5, y + 3, x + halfSize - 5, y + 3);
                g2.drawLine(x - halfSize + 5, y + 8, x + halfSize - 8, y + 8);
                break;
                
            case "settings": // Wrench icon
                g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Shadow
                g2.setColor(shadow);
                g2.drawLine(x - halfSize + 4, y + halfSize, x + halfSize, y - halfSize + 2);
                // Wrench handle
                g2.setColor(color);
                g2.drawLine(x - halfSize + 2, y + halfSize - 2, x + halfSize - 2, y - halfSize);
                // Wrench head
                g2.setStroke(new BasicStroke(2));
                g2.drawArc(x + halfSize - 6, y - halfSize - 2, 8, 8, -30, 240);
                break;
                
            case "save": // Dog tag icon
                g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // Shadow
                g2.setColor(shadow);
                g2.drawRoundRect(x - halfSize + 4, y - halfSize + 4, size - 4, size + 2, 6, 6);
                // Tag body
                g2.setColor(color);
                g2.drawRoundRect(x - halfSize + 2, y - halfSize + 2, size - 4, size + 2, 6, 6);
                // Hole at top
                g2.drawOval(x - 2, y - halfSize + 4, 4, 4);
                break;
        }
        
        g2.dispose();
    }
    
    private void drawMissileIcon(Graphics2D g, int x, int y, int size) {
        int halfSize = size / 2;
        // Missile body (pointed right)
        int[] mx = {x + halfSize, x + halfSize - 3, x - halfSize + 3, x - halfSize, x - halfSize + 3, x + halfSize - 3};
        int[] my = {y, y - halfSize / 2, y - halfSize / 2, y, y + halfSize / 2, y + halfSize / 2};
        g.fillPolygon(mx, my, 6);
        // Fins
        g.fillRect(x - halfSize, y - halfSize / 2 - 2, 4, halfSize + 4);
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
        // Kept for backward compatibility but unused in new icon set
        int halfSize = size / 2;
        g.fillOval(x - halfSize, y - halfSize, size, size);
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
