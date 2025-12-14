import java.awt.*;

public class BeamAttack {
    public enum BeamType {
        VERTICAL,   // Top to bottom beam
        HORIZONTAL  // Left to right beam
    }
    
    private double position; // X position for vertical, Y position for horizontal
    private double width;    // Width of the beam
    private BeamType type;
    private int warningTimer; // Countdown until beam appears
    private int beamTimer;    // How long beam stays active
    private boolean isActive; // Whether beam is dealing damage
    private boolean warningPlayed; // Whether warning sound was played
    private boolean firePlayed; // Whether fire sound was played
    
    private static final int WARNING_DURATION = 210; // 3.5 seconds warning (increased from 150)
    private static final int BEAM_DURATION = 45;     // 0.75 seconds active beam (increased from 30)
    
    public BeamAttack(double position, double width, BeamType type) {
        this.position = position;
        this.width = width;
        this.type = type;
        this.warningTimer = WARNING_DURATION;
        this.beamTimer = BEAM_DURATION;
        this.isActive = false;
        this.warningPlayed = false;
        this.firePlayed = false;
    }
    
    public void update(double deltaTime) {
        if (warningTimer > 0) {
            warningTimer -= deltaTime;
            if (warningTimer <= 0) {
                // Warning complete, activate beam
                isActive = true;
            }
        } else if (isActive && beamTimer > 0) {
            beamTimer -= deltaTime;
            if (beamTimer <= 0) {
                isActive = false;
            }
        }
    }
    
    public boolean isDone() {
        return warningTimer <= 0 && beamTimer <= 0;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public boolean shouldPlayWarning() {
        if (!warningPlayed && warningTimer > 0) {
            warningPlayed = true;
            return true;
        }
        return false;
    }
    
    public boolean shouldPlayFire() {
        if (!firePlayed && isActive && warningTimer <= 0) {
            firePlayed = true;
            return true;
        }
        return false;
    }
    
    public boolean collidesWith(Player player) {
        if (!isActive) return false;
        
        double px = player.getX();
        double py = player.getY();
        double playerRadius = player.getSize() / 2.0;
        
        if (type == BeamType.VERTICAL) {
            // Check if player is within horizontal range of beam
            return Math.abs(px - position) < (width / 2 + playerRadius);
        } else {
            // Check if player is within vertical range of beam
            return Math.abs(py - position) < (width / 2 + playerRadius);
        }
    }
    
    public void draw(Graphics2D g, int screenWidth, int screenHeight, double cameraX, double cameraY) {
        // Extend drawing area to account for camera offset
        int margin = 100; // Extra margin to ensure beams reach screen edges
        int minX = (int)cameraX - margin;
        int minY = (int)cameraY - margin;
        int maxX = (int)cameraX + screenWidth + margin;
        int maxY = (int)cameraY + screenHeight + margin;
        
        if (warningTimer > 0) {
            // Calculate color transition: green → yellow → red
            // Progress from 0 (start) to 1 (end of warning)
            double progress = 1.0 - (warningTimer / (double)WARNING_DURATION);
            
            Color warningColor;
            if (progress < 0.5) {
                // First half: green to yellow
                // Green: 163, 190, 140 -> Yellow: 235, 203, 139
                double t = progress * 2; // 0 to 1
                int r = (int)(163 + (235 - 163) * t);
                int g1 = (int)(190 + (203 - 190) * t);
                int b = (int)(140 + (139 - 140) * t);
                warningColor = new Color(r, g1, b);
            } else {
                // Second half: yellow to red
                // Yellow: 235, 203, 139 -> Red: 191, 97, 106
                double t = (progress - 0.5) * 2; // 0 to 1
                int r = (int)(235 + (191 - 235) * t);
                int g1 = (int)(203 + (97 - 203) * t);
                int b = (int)(139 + (106 - 139) * t);
                warningColor = new Color(r, g1, b);
            }
            
            // Draw blinking warning line
            // Blink faster as warning time runs out
            double blinkSpeed = 0.1 + (WARNING_DURATION - warningTimer) / WARNING_DURATION * 0.4;
            int alpha = (int)(Math.abs(Math.sin(warningTimer * blinkSpeed)) * 150 + 50);
            
            g.setColor(new Color(warningColor.getRed(), warningColor.getGreen(), warningColor.getBlue(), alpha));
            
            if (type == BeamType.VERTICAL) {
                // Draw vertical warning line
                int x = (int)(position - width / 2);
                g.fillRect(x, minY, (int)width, maxY - minY);
                
                // Draw warning borders
                g.setColor(new Color(warningColor.getRed(), warningColor.getGreen(), warningColor.getBlue(), Math.min(255, alpha + 100)));
                g.setStroke(new BasicStroke(3));
                g.drawLine(x, minY, x, maxY);
                g.drawLine(x + (int)width, minY, x + (int)width, maxY);
                
                // Draw warning text
                if (warningTimer > 30) {
                    g.setFont(new Font("Arial", Font.BOLD, 24));
                    String warning = "!";
                    FontMetrics fm = g.getFontMetrics();
                    int textX = (int)(position - fm.stringWidth(warning) / 2);
                    // Draw multiple warning symbols along the beam
                    for (int y = minY + 50; y < maxY; y += 100) {
                        g.drawString(warning, textX, y);
                    }
                }
            } else {
                // Draw horizontal warning line
                int y = (int)(position - width / 2);
                g.fillRect(minX, y, maxX - minX, (int)width);
                
                // Draw warning borders
                g.setColor(new Color(warningColor.getRed(), warningColor.getGreen(), warningColor.getBlue(), Math.min(255, alpha + 100)));
                g.setStroke(new BasicStroke(3));
                g.drawLine(minX, y, maxX, y);
                g.drawLine(minX, y + (int)width, maxX, y + (int)width);
                
                // Draw warning text
                if (warningTimer > 30) {
                    g.setFont(new Font("Arial", Font.BOLD, 24));
                    String warning = "!";
                    FontMetrics fm = g.getFontMetrics();
                    int textY = (int)(position + fm.getHeight() / 3);
                    // Draw multiple warning symbols along the beam
                    for (int x = minX + 50; x < maxX; x += 100) {
                        g.drawString(warning, x, textY);
                    }
                }
            }
        } else if (isActive) {
            // Draw active damage beam with glow effect
            if (type == BeamType.VERTICAL) {
                int x = (int)(position - width / 2);
                
                // Outer glow
                g.setColor(new Color(191, 97, 106, 80));
                g.fillRect(x - 10, minY, (int)width + 20, maxY - minY);
                
                // Main beam (red)
                g.setColor(new Color(191, 97, 106, 200));
                g.fillRect(x, minY, (int)width, maxY - minY);
                
                // Inner bright core
                g.setColor(new Color(255, 150, 150, 220));
                g.fillRect(x + (int)width / 4, minY, (int)width / 2, maxY - minY);
                
                // Animated scanlines for effect
                g.setColor(new Color(255, 200, 200, 100));
                for (int y = minY; y < maxY; y += 8) {
                    int offset = (int)((beamTimer * 10) % 8);
                    g.fillRect(x, y + offset, (int)width, 2);
                }
            } else {
                int y = (int)(position - width / 2);
                
                // Outer glow
                g.setColor(new Color(191, 97, 106, 80));
                g.fillRect(minX, y - 10, maxX - minX, (int)width + 20);
                
                // Main beam (red)
                g.setColor(new Color(191, 97, 106, 200));
                g.fillRect(minX, y, maxX - minX, (int)width);
                
                // Inner bright core
                g.setColor(new Color(255, 150, 150, 220));
                g.fillRect(minX, y + (int)width / 4, maxX - minX, (int)width / 2);
                
                // Animated scanlines for effect
                g.setColor(new Color(255, 200, 200, 100));
                for (int x = minX; x < maxX; x += 8) {
                    int offset = (int)((beamTimer * 10) % 8);
                    g.fillRect(x + offset, y, 2, (int)width);
                }
            }
        }
    }
    
    // Overload for backward compatibility
    public void draw(Graphics2D g, int screenWidth, int screenHeight) {
        draw(g, screenWidth, screenHeight, 0, 0);
    }
    
    public BeamType getType() { return type; }
    public double getPosition() { return position; }
    public double getWidth() { return width; }
}
