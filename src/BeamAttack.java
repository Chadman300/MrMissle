import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class BeamAttack {
    public enum BeamType {
        VERTICAL,   // Top to bottom beam
        HORIZONTAL, // Left to right beam
        DIAGONAL    // Angled beam through a center point
    }
    
    private double position; // X position for vertical, Y position for horizontal
    private double width;    // Width of the beam
    private BeamType type;
    private double angle;    // Angle in radians for DIAGONAL beams (0 = horizontal, PI/4 = 45°)
    private double centerX;  // Center X for DIAGONAL beams
    private double centerY;  // Center Y for DIAGONAL beams
    private double warningTimer; // Countdown until beam appears
    private double beamTimer;    // How long beam stays active
    private boolean isActive; // Whether beam is dealing damage
    private boolean warningPlayed; // Whether warning sound was played
    private boolean firePlayed; // Whether fire sound was played
    private double timeSlowMultiplier = 1.0; // Multiplier for time slow effect (1.0 = normal, 0.3 = 70% slow)
    private boolean justFinished; // True for one frame when beam transitions from active to done
    
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
    
    /** Constructor for DIAGONAL beams that pass through a center point at an angle. */
    public BeamAttack(double centerX, double centerY, double width, double angle) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.width = width;
        this.angle = angle;
        this.type = BeamType.DIAGONAL;
        this.position = 0; // Not used for diagonal
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
        
        // Clear one-frame flag from previous update
        justFinished = false;
        
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
                justFinished = true;
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
        } else if (type == BeamType.HORIZONTAL) {
            // Check if player is within vertical range of beam
            return Math.abs(py - position) < (width / 2 + playerRadius);
        } else {
            // DIAGONAL: rotate player position into beam's local space
            // Translate so beam center is origin, then rotate by -angle
            double dx = px - centerX;
            double dy = py - centerY;
            double cosA = Math.cos(-angle);
            double sinA = Math.sin(-angle);
            // In rotated space, the beam runs along the X axis; check perpendicular (Y) distance
            double localY = dx * sinA + dy * cosA;
            return Math.abs(localY) < (width / 2 + playerRadius);
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
            
            if (type == BeamType.DIAGONAL) {
                // DIAGONAL warning: rotate graphics around center and draw as horizontal warning
                AffineTransform savedTransform = g.getTransform();
                g.rotate(angle, centerX, centerY);
                int beamLength = (int)(Math.sqrt(screenWidth * screenWidth + screenHeight * screenHeight) * 1.5);
                int halfLen = beamLength / 2;
                int bx = (int)(centerX - halfLen);
                int by = (int)(centerY - width / 2);
                
                g.fillRect(bx, by, beamLength, (int)width);
                
                // Warning borders
                g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF + 100f / 255f)));
                g.setColor(warningColor);
                g.setStroke(WARNING_STROKE);
                g.drawLine(bx, by, bx + beamLength, by);
                g.drawLine(bx, by + (int)width, bx + beamLength, by + (int)width);
                
                // Warning text along the beam
                if (warningTimer > 30) {
                    g.setFont(WARNING_FONT);
                    String warning = "!";
                    FontMetrics fm = g.getFontMetrics();
                    int textY = (int)(centerY + fm.getHeight() / 3);
                    for (int wx = bx + 50; wx < bx + beamLength; wx += 100) {
                        g.drawString(warning, wx, textY);
                    }
                }
                g.setTransform(savedTransform);
            } else if (type == BeamType.VERTICAL) {
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
            } else if (type == BeamType.HORIZONTAL) {
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
            if (type == BeamType.DIAGONAL) {
                // DIAGONAL active beam: rotate graphics around center point and draw as horizontal
                AffineTransform savedTransform = g.getTransform();
                g.rotate(angle, centerX, centerY);
                int beamLength = (int)(Math.sqrt(screenWidth * screenWidth + screenHeight * screenHeight) * 1.5);
                int halfLen = beamLength / 2;
                int bx = (int)(centerX - halfLen);
                int by = (int)(centerY - width / 2);
                
                // Outer glow
                g.setColor(BEAM_GLOW);
                g.fillRect(bx, by - 10, beamLength, (int)width + 20);
                // Main beam
                g.setColor(BEAM_MAIN);
                g.fillRect(bx, by, beamLength, (int)width);
                // Inner core
                g.setColor(BEAM_CORE);
                g.fillRect(bx, by + (int)width / 4, beamLength, (int)width / 2);
                // Animated scanlines
                int offset = (int)((beamTimer * 10) % 8);
                TexturePaint scanPaint = new TexturePaint(SCANLINE_TILE_H,
                    new java.awt.geom.Rectangle2D.Float(offset, 0, 8, 1));
                g.setPaint(scanPaint);
                g.fillRect(bx, by, beamLength, (int)width);
                
                g.setTransform(savedTransform);
            } else if (type == BeamType.VERTICAL) {
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
    public double getWarningTimer() { return warningTimer; }
    public double getAngle() { return angle; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    
    /** Set position (used when merging overlapping beams). */
    public void setPosition(double position) { this.position = position; }
    
    /** Set width (used when merging overlapping beams). */
    public void setWidth(double width) { this.width = width; }
    
    /** Whether this beam is still in the warning phase (hasn't fired yet). */
    public boolean isInWarningPhase() { return warningTimer > 0 && !isActive; }
    
    /** True for exactly one frame when the beam finishes its active phase. */
    public boolean justFinished() { return justFinished; }
    
    /**
     * Check if a bullet is within this beam AND within a small region around the beam's midpoint.
     * Used to destroy bullets when the beam disappears.
     * @param middleRadius how far along the beam axis from the center counts as "middle"
     */
    public boolean collidesWithBulletMiddle(double bx, double by, int worldWidth, int worldHeight, double middleRadius) {
        if (type == BeamType.VERTICAL) {
            // Beam runs along Y axis at x=position; check cross-axis (X) distance
            if (Math.abs(bx - position) > width / 2) return false;
            // Middle = center of the world height
            double midY = worldHeight / 2.0;
            return Math.abs(by - midY) <= middleRadius;
        } else if (type == BeamType.HORIZONTAL) {
            // Beam runs along X axis at y=position; check cross-axis (Y) distance
            if (Math.abs(by - position) > width / 2) return false;
            // Middle = center of the world width
            double midX = worldWidth / 2.0;
            return Math.abs(bx - midX) <= middleRadius;
        } else {
            // DIAGONAL: rotate bullet into beam-local space
            double dx = bx - centerX;
            double dy = by - centerY;
            double cosA = Math.cos(-angle);
            double sinA = Math.sin(-angle);
            double localX = dx * cosA - dy * sinA;
            double localY = dx * sinA + dy * cosA;
            // Cross-axis check (perpendicular to beam)
            if (Math.abs(localY) > width / 2) return false;
            // Along-axis check (distance from center along beam)
            return Math.abs(localX) <= middleRadius;
        }
    }
}
