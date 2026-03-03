import java.awt.*;

public class Flare {
    private double x, y;
    private double vx, vy;
    private int lifetime;
    private int age;
    private boolean active;
    private static final double DECELERATION = 0.98;
    private float glowRadius;

    // Cached colors for glow rendering (avoid per-frame allocations)
    private static final Color GLOW_OUTER = new Color(255, 50, 30, 60);
    private static final Color GLOW_INNER = new Color(255, 80, 40, 30);
    private static final Color CORE_COLOR = new Color(255, 60, 20);
    private static final Color SPARK_TRAIL = new Color(255, 200, 50);

    public Flare(double x, double y, double vx, double vy) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.lifetime = 180; // ~3 seconds at 60fps
        this.age = 0;
        this.active = true;
        this.glowRadius = 12f;
    }

    public void update(double deltaTime) {
        if (!active) return;

        // Move by velocity
        x += vx * deltaTime;
        y += vy * deltaTime;

        // Decelerate
        double damping = (deltaTime == 1.0) ? DECELERATION : Math.pow(DECELERATION, deltaTime);
        vx *= damping;
        vy *= damping;

        age++;
        lifetime--;

        if (lifetime <= 0) {
            active = false;
        }
    }

    public void draw(Graphics2D g) {
        if (!active) return;

        // Pulsing glow radius
        float pulse = (float)(Math.sin(age * 0.15) * 2.0);
        float currentGlow = glowRadius + pulse;

        Composite savedComposite = g.getComposite();

        // Outer glow halo
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(GLOW_OUTER);
        int outerSize = (int)(currentGlow * 1.8);
        g.fillOval((int)(x - outerSize), (int)(y - outerSize), outerSize * 2, outerSize * 2);

        // Inner glow halo
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(GLOW_INNER);
        int innerSize = (int)(currentGlow);
        g.fillOval((int)(x - innerSize), (int)(y - innerSize), innerSize * 2, innerSize * 2);

        // Core bright red circle (~4px radius)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(CORE_COLOR);
        g.fillOval((int)(x - 4), (int)(y - 4), 8, 8);

        // Bright white center for intensity
        g.setColor(Color.WHITE);
        g.fillOval((int)(x - 2), (int)(y - 2), 4, 4);

        // Spark trail line opposite to velocity direction
        double speed = Math.sqrt(vx * vx + vy * vy);
        if (speed > 0.1) {
            float trailAlpha = Math.max(0.1f, Math.min(1.0f, (float)(lifetime) / 180.0f));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, trailAlpha * 0.7f));
            g.setColor(SPARK_TRAIL);
            int trailLen = (int)(speed * 5);
            double invSpeed = 1.0 / speed;
            int tx = (int)(x - vx * invSpeed * trailLen);
            int ty = (int)(y - vy * invSpeed * trailLen);
            g.setStroke(new BasicStroke(2f));
            g.drawLine((int)x, (int)y, tx, ty);
        }

        g.setComposite(savedComposite);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /**
     * Check if this flare collides with a bullet (distance check, ~15px radius).
     */
    public boolean collidesWith(Bullet bullet) {
        double dx = x - bullet.getX();
        double dy = y - bullet.getY();
        double distSq = dx * dx + dy * dy;
        return distSq < 15 * 15; // 15px collision radius
    }
}
