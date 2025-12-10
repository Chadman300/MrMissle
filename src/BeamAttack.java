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
    
    private static final int WARNING_DURATION = 90; // 1.5 seconds warning
    private static final int BEAM_DURATION = 30;    // 0.5 seconds active beam
    private static final Color WARNING_COLOR = new Color(235, 203, 139, 150); // Yellow/gold warning
    private static final Color BEAM_COLOR = new Color(191, 97, 106, 200);     // Red damage beam
    
    public BeamAttack(double position, double width, BeamType type) {
        this.position = position;
        this.width = width;
        this.type = type;
        this.warningTimer = WARNING_DURATION;
        this.beamTimer = BEAM_DURATION;
        this.isActive = false;
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
    
    public void draw(Graphics2D g, int screenWidth, int screenHeight) {
        if (warningTimer > 0) {
            // Draw blinking warning line
            // Blink faster as warning time runs out
            double blinkSpeed = 0.1 + (WARNING_DURATION - warningTimer) / WARNING_DURATION * 0.4;
            int alpha = (int)(Math.abs(Math.sin(warningTimer * blinkSpeed)) * 150 + 50);
            
            g.setColor(new Color(235, 203, 139, alpha));
            
            if (type == BeamType.VERTICAL) {
                // Draw vertical warning line
                int x = (int)(position - width / 2);
                g.fillRect(x, 0, (int)width, screenHeight);
                
                // Draw warning borders
                g.setColor(new Color(235, 203, 139, Math.min(255, alpha + 100)));
                g.setStroke(new BasicStroke(3));
                g.drawLine(x, 0, x, screenHeight);
                g.drawLine(x + (int)width, 0, x + (int)width, screenHeight);
                
                // Draw warning text
                if (warningTimer > 30) {
                    g.setFont(new Font("Arial", Font.BOLD, 24));
                    String warning = "!";
                    FontMetrics fm = g.getFontMetrics();
                    int textX = (int)(position - fm.stringWidth(warning) / 2);
                    // Draw multiple warning symbols along the beam
                    for (int y = 50; y < screenHeight; y += 100) {
                        g.drawString(warning, textX, y);
                    }
                }
            } else {
                // Draw horizontal warning line
                int y = (int)(position - width / 2);
                g.fillRect(0, y, screenWidth, (int)width);
                
                // Draw warning borders
                g.setColor(new Color(235, 203, 139, Math.min(255, alpha + 100)));
                g.setStroke(new BasicStroke(3));
                g.drawLine(0, y, screenWidth, y);
                g.drawLine(0, y + (int)width, screenWidth, y + (int)width);
                
                // Draw warning text
                if (warningTimer > 30) {
                    g.setFont(new Font("Arial", Font.BOLD, 24));
                    String warning = "!";
                    FontMetrics fm = g.getFontMetrics();
                    int textY = (int)(position + fm.getHeight() / 3);
                    // Draw multiple warning symbols along the beam
                    for (int x = 50; x < screenWidth; x += 100) {
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
                g.fillRect(x - 10, 0, (int)width + 20, screenHeight);
                
                // Main beam
                g.setColor(BEAM_COLOR);
                g.fillRect(x, 0, (int)width, screenHeight);
                
                // Inner bright core
                g.setColor(new Color(255, 150, 150, 220));
                g.fillRect(x + (int)width / 4, 0, (int)width / 2, screenHeight);
                
                // Animated scanlines for effect
                g.setColor(new Color(255, 200, 200, 100));
                for (int y = 0; y < screenHeight; y += 8) {
                    int offset = (int)((beamTimer * 10) % 8);
                    g.fillRect(x, y + offset, (int)width, 2);
                }
            } else {
                int y = (int)(position - width / 2);
                
                // Outer glow
                g.setColor(new Color(191, 97, 106, 80));
                g.fillRect(0, y - 10, screenWidth, (int)width + 20);
                
                // Main beam
                g.setColor(BEAM_COLOR);
                g.fillRect(0, y, screenWidth, (int)width);
                
                // Inner bright core
                g.setColor(new Color(255, 150, 150, 220));
                g.fillRect(0, y + (int)width / 4, screenWidth, (int)width / 2);
                
                // Animated scanlines for effect
                g.setColor(new Color(255, 200, 200, 100));
                for (int x = 0; x < screenWidth; x += 8) {
                    int offset = (int)((beamTimer * 10) % 8);
                    g.fillRect(x + offset, y, 2, (int)width);
                }
            }
        }
    }
    
    public BeamType getType() { return type; }
    public double getPosition() { return position; }
    public double getWidth() { return width; }
}
