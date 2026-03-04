import java.awt.*;
import java.awt.image.BufferedImage;

public class BeamAttack {
    public enum BeamType {
        VERTICAL,   // Top to bottom beam
        HORIZONTAL  // Left to right beam
    }
    
    private double position; // X position for vertical, Y position for horizontal
    private double width;    // Width of the beam
    private BeamType type;
    private double warningTimer; // Countdown until beam appears
    private double beamTimer;    // How long beam stays active
    private boolean isActive; // Whether beam is dealing damage
    private boolean warningPlayed; // Whether warning sound was played
    private boolean firePlayed; // Whether fire sound was played
    private double timeSlowMultiplier = 1.0; // Multiplier for time slow effect (1.0 = normal, 0.3 = 70% slow)
    
    private static final int WARNING_DURATION = 210; // 3.5 seconds warning (increased from 150)
    private static final int BEAM_DURATION = 45;     // 0.75 seconds active beam (increased from 30)
    
    // Cached drawing objects to avoid per-frame allocations
    private static final Color BEAM_GLOW = new Color(191, 97, 106, 80);
    private static final Color BEAM_MAIN = new Color(191, 97, 106, 200);
    private static final Color BEAM_CORE = new Color(255, 150, 150, 220);
    private static final Color BEAM_SCANLINE = new Color(255, 200, 200, 100);
    private static final Font WARNING_FONT = new Font("Arial", Font.BOLD, 24);
    private static final BasicStroke WARNING_STROKE = new BasicStroke(3);
    
    // Pre-rendered scanline tile (8x8: 2px opaque + 6px transparent, tiled via TexturePaint)
    // Replaces ~160-265 fillRect calls per beam with a single TexturePaint fill
    private static final BufferedImage SCANLINE_TILE_V;
    private static final BufferedImage SCANLINE_TILE_H;
    static {
        // Vertical beam scanline tile (horizontal stripe pattern)
        SCANLINE_TILE_V = new BufferedImage(1, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gv = SCANLINE_TILE_V.createGraphics();
        gv.setColor(BEAM_SCANLINE);
        gv.fillRect(0, 0, 1, 2);
        gv.dispose();
        // Horizontal beam scanline tile (vertical stripe pattern)
        SCANLINE_TILE_H = new BufferedImage(8, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gh = SCANLINE_TILE_H.createGraphics();
        gh.setColor(BEAM_SCANLINE);
        gh.fillRect(0, 0, 2, 1);
        gh.dispose();
    }
    
    // Pre-computed warning colors at 20 discrete steps (green → yellow → red)
    // Eliminates per-frame Color allocation during the 210-frame warning phase
    private static final int WARNING_COLOR_STEPS = 20;
    private static final Color[] WARNING_COLORS = new Color[WARNING_COLOR_STEPS];
    static {
        for (int i = 0; i < WARNING_COLOR_STEPS; i++) {
            double progress = (double) i / (WARNING_COLOR_STEPS - 1);
            int r, g1, b;
            if (progress < 0.5) {
                double t = progress * 2;
                r = (int)(163 + (235 - 163) * t);
                g1 = (int)(190 + (203 - 190) * t);
                b = (int)(140 + (139 - 140) * t);
            } else {
                double t = (progress - 0.5) * 2;
                r = (int)(235 + (191 - 235) * t);
                g1 = (int)(203 + (97 - 203) * t);
                b = (int)(139 + (106 - 139) * t);
            }
            WARNING_COLORS[i] = new Color(r, g1, b);
        }
    }
    
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
        // Apply time slow multiplier to deltaTime
        double effectiveDeltaTime = deltaTime * timeSlowMultiplier;
        
        // Reset time slow multiplier each frame (must be reapplied if active)
        timeSlowMultiplier = 1.0;
        
        if (warningTimer > 0) {
            warningTimer -= effectiveDeltaTime;
            if (warningTimer <= 0) {
                // Warning complete, activate beam
                isActive = true;
            }
        } else if (isActive && beamTimer > 0) {
            beamTimer -= effectiveDeltaTime;
            if (beamTimer <= 0) {
                isActive = false;
            }
        }
    }
    
    /**
     * Apply time slow effect to this beam.
     * Must be called each frame while time slow is active.
     */
    public void applyTimeSlow(double multiplier) {
        this.timeSlowMultiplier = multiplier;
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
            // Look up pre-computed warning color from cached array
            double progress = 1.0 - (warningTimer / (double)WARNING_DURATION);
            int colorIdx = Math.min(WARNING_COLOR_STEPS - 1, Math.max(0, (int)(progress * (WARNING_COLOR_STEPS - 1))));
            Color warningColor = WARNING_COLORS[colorIdx];
            
            // Draw blinking warning line
            // Blink faster as warning time runs out
            double blinkSpeed = 0.1 + (WARNING_DURATION - warningTimer) / WARNING_DURATION * 0.4;
            float alphaF = (float)(Math.abs(Math.sin(warningTimer * blinkSpeed)) * 150 + 50) / 255f;
            
            // Use base color + AlphaComposite instead of new Color(r,g,b,alpha)
            Composite savedComp = g.getComposite();
            g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF)));
            g.setColor(warningColor);
            
            if (type == BeamType.VERTICAL) {
                // Draw vertical warning line
                int x = (int)(position - width / 2);
                g.fillRect(x, minY, (int)width, maxY - minY);
                
                // Draw warning borders — increase alpha for borders
                g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF + 100f / 255f)));
                g.setColor(warningColor);
                g.setStroke(WARNING_STROKE);
                g.drawLine(x, minY, x, maxY);
                g.drawLine(x + (int)width, minY, x + (int)width, maxY);
                
                // Draw warning text
                if (warningTimer > 30) {
                    g.setFont(WARNING_FONT);
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
                
                // Draw warning borders — increase alpha for borders
                g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF + 100f / 255f)));
                g.setColor(warningColor);
                g.setStroke(WARNING_STROKE);
                g.drawLine(minX, y, maxX, y);
                g.drawLine(minX, y + (int)width, maxX, y + (int)width);
                
                // Draw warning text
                if (warningTimer > 30) {
                    g.setFont(WARNING_FONT);
                    String warning = "!";
                    FontMetrics fm = g.getFontMetrics();
                    int textY = (int)(position + fm.getHeight() / 3);
                    // Draw multiple warning symbols along the beam
                    for (int x = minX + 50; x < maxX; x += 100) {
                        g.drawString(warning, x, textY);
                    }
                }
            }
            g.setComposite(savedComp); // Restore composite after warning drawing
        } else if (isActive) {
            // Draw active damage beam with glow effect
            if (type == BeamType.VERTICAL) {
                int x = (int)(position - width / 2);
                
                // Outer glow
                g.setColor(BEAM_GLOW);
                g.fillRect(x - 10, minY, (int)width + 20, maxY - minY);
                
                // Main beam (red)
                g.setColor(BEAM_MAIN);
                g.fillRect(x, minY, (int)width, maxY - minY);
                
                // Inner bright core
                g.setColor(BEAM_CORE);
                g.fillRect(x + (int)width / 4, minY, (int)width / 2, maxY - minY);
                
                // Animated scanlines via TexturePaint (replaces ~160 fillRect calls)
                int offset = (int)((beamTimer * 10) % 8);
                TexturePaint scanPaint = new TexturePaint(SCANLINE_TILE_V,
                    new java.awt.geom.Rectangle2D.Float(0, offset, 1, 8));
                g.setPaint(scanPaint);
                g.fillRect(x, minY, (int)width, maxY - minY);
            } else {
                int y = (int)(position - width / 2);
                
                // Outer glow
                g.setColor(BEAM_GLOW);
                g.fillRect(minX, y - 10, maxX - minX, (int)width + 20);
                
                // Main beam (red)
                g.setColor(BEAM_MAIN);
                g.fillRect(minX, y, maxX - minX, (int)width);
                
                // Inner bright core
                g.setColor(BEAM_CORE);
                g.fillRect(minX, y + (int)width / 4, maxX - minX, (int)width / 2);
                
                // Animated scanlines via TexturePaint (replaces ~265 fillRect calls)
                int offset = (int)((beamTimer * 10) % 8);
                TexturePaint scanPaint = new TexturePaint(SCANLINE_TILE_H,
                    new java.awt.geom.Rectangle2D.Float(offset, 0, 8, 1));
                g.setPaint(scanPaint);
                g.fillRect(minX, y, maxX - minX, (int)width);
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
