package config;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Shared military / rock themed UI drawing helpers.
 * All methods are static — call UITheme.drawXxx(g, ...).
 *
 * Provides:
 * - Screen backgrounds (metallic gradient + warning stripes + radar sweep)
 * - Stencil-style screen titles with ember particles
 * - Angular beveled cards with chamfered corners
 * - Missile-arming progress bar
 * - Rubber-stamp text effect (MISSION FAILED / MISSION COMPLETE)
 * - Warning light animations
 * - Confetti particles
 * - Medal / rank rendering
 */
public class UITheme {

    // Cache for the metallic background — regenerated if size changes
    private static BufferedImage cachedMetalBg = null;
    private static int cachedBgW = 0, cachedBgH = 0;

    // ── Full composite background cache (updated at ~10fps) ──────────
    private static BufferedImage cachedFullBg = null;
    private static int cachedFullBgW = 0, cachedFullBgH = 0;
    private static long lastBgUpdateNanos = 0;
    private static final long BG_UPDATE_INTERVAL_NANOS = 100_000_000L; // 100ms

    // ── Vignette cache (fully static — only depends on size) ─────────
    private static BufferedImage cachedVignette = null;
    private static int cachedVigW = 0, cachedVigH = 0;

    // ── Radial glow cache (for journey map nodes etc.) ───────────────
    private static final java.util.Map<Integer, BufferedImage> glowCache = new java.util.HashMap<>();
    private static final int GLOW_IMG_SIZE = 256;

    // Ember particle state (simple procedural, no external state needed)
    private static double[][] embers = null;

    // Confetti particles for victory screen
    private static double[][] confetti = null;

    // ─── SCREEN BACKGROUND ───────────────────────────────────────────────

    /**
     * Draw the standard military-themed screen background.
     * Dark metallic gradient + subtle noise + warning stripes on edges +
     * animated radar sweep glow.
     */
    public static void drawScreenBackground(Graphics2D g, int width, int height, double time) {
        long now = System.nanoTime();
        boolean sizeChanged = cachedFullBgW != width || cachedFullBgH != height;
        boolean stale = (now - lastBgUpdateNanos) > BG_UPDATE_INTERVAL_NANOS;

        if (cachedFullBg == null || sizeChanged || stale) {
            if (cachedFullBg == null || sizeChanged) {
                cachedFullBg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                cachedFullBgW = width;
                cachedFullBgH = height;
            }
            Graphics2D bg = cachedFullBg.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 1. Base gradient — deep dark military
            drawMilitaryGradient(bg, width, height, time);

            // 2. Subtle metallic noise texture (procedural)
            drawMetalTexture(bg, width, height);

            // 3. Warning stripes along top and bottom edges
            drawWarningStripes(bg, width, height, time);

            // 4. Radar sweep glow in bottom-right corner
            drawRadarSweep(bg, width, height, time);

            // 5. Corner rivets / brackets
            drawMilitaryCorners(bg, width, height, time);

            // 6. Subtle vignette overlay
            drawDarkVignette(bg, width, height);

            bg.dispose();
            lastBgUpdateNanos = now;
        }

        g.drawImage(cachedFullBg, 0, 0, null);
    }

    private static void drawMilitaryGradient(Graphics2D g, int width, int height, double time) {
        // Animated gradient with military colors
        double phase = time * 0.3;
        float shift = (float)(Math.sin(phase) * 0.05 + 0.5);

        GradientPaint gp1 = new GradientPaint(
            0, 0, ColorPalette.BG_DARK,
            width * shift, height, ColorPalette.BG_MID
        );
        g.setPaint(gp1);
        g.fillRect(0, 0, width, height);

        // Second layer for depth
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        GradientPaint gp2 = new GradientPaint(
            width, 0, ColorPalette.BG_LIGHT,
            0, height, new Color(5, 5, 15, 0)
        );
        g.setPaint(gp2);
        g.fillRect(0, 0, width, height);
        g.setComposite(ColorPalette.ALPHA_FULL);
    }

    private static void drawMetalTexture(Graphics2D g, int width, int height) {
        // Generate/cache a subtle noise pattern
        if (cachedMetalBg == null || cachedBgW != width || cachedBgH != height) {
            cachedMetalBg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D mg = cachedMetalBg.createGraphics();
            // Horizontal brushed lines
            for (int y = 0; y < height; y += 2) {
                int a = 5 + (int)((Math.sin(y * 0.7) + 1) * 4);
                mg.setColor(new Color(255, 255, 255, Math.min(a, 15)));
                mg.drawLine(0, y, width, y);
            }
            // Subtle vertical variation
            for (int x = 0; x < width; x += 4) {
                int a = (int)((Math.sin(x * 0.3) + 1) * 3);
                mg.setColor(new Color(0, 0, 0, Math.min(a, 10)));
                mg.drawLine(x, 0, x, height);
            }
            mg.dispose();
            cachedBgW = width;
            cachedBgH = height;
        }
        g.drawImage(cachedMetalBg, 0, 0, null);
    }

    private static void drawWarningStripes(Graphics2D g, int width, int height, double time) {
        int stripeH = 6;
        int stripeW = 20;
        double scroll = time * 30;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));

        // Top edge
        for (int x = -stripeW; x < width + stripeW; x += stripeW * 2) {
            int sx = x + (int)(scroll % (stripeW * 2));
            g.setColor(ColorPalette.ACCENT_YELLOW);
            g.fillRect(sx, 0, stripeW, stripeH);
            g.setColor(ColorPalette.STRIPE_DARK);
            g.fillRect(sx + stripeW, 0, stripeW, stripeH);
        }

        // Bottom edge
        for (int x = -stripeW; x < width + stripeW; x += stripeW * 2) {
            int sx = x - (int)(scroll % (stripeW * 2));
            g.setColor(ColorPalette.ACCENT_YELLOW);
            g.fillRect(sx, height - stripeH, stripeW, stripeH);
            g.setColor(ColorPalette.STRIPE_DARK);
            g.fillRect(sx + stripeW, height - stripeH, stripeW, stripeH);
        }

        g.setComposite(ColorPalette.ALPHA_FULL);
    }

    private static void drawRadarSweep(Graphics2D g, int width, int height, double time) {
        int cx = width - 80;
        int cy = height - 80;
        int radius = 120;
        double angle = time * 1.5;

        // Sweep arc
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
        g.setColor(ColorPalette.RADAR_GREEN);
        g.fillArc(cx - radius, cy - radius, radius * 2, radius * 2,
                  (int)Math.toDegrees(-angle), 60);

        // Ring
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
        g.setColor(new Color(50, 200, 100, 40));
        g.setStroke(new BasicStroke(1));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.drawOval(cx - radius / 2, cy - radius / 2, radius, radius);

        // Center dot
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g.setColor(new Color(50, 255, 100));
        g.fillOval(cx - 2, cy - 2, 4, 4);

        g.setComposite(ColorPalette.ALPHA_FULL);
    }

    private static void drawMilitaryCorners(Graphics2D g, int width, int height, double time) {
        int sz = 50;
        int inset = 15;
        int alpha = (int)(140 + 60 * Math.sin(time * 2));
        Color c = new Color(
            ColorPalette.ACCENT_ORANGE.getRed(),
            ColorPalette.ACCENT_ORANGE.getGreen(),
            ColorPalette.ACCENT_ORANGE.getBlue(),
            alpha
        );
        g.setColor(c);
        g.setStroke(new BasicStroke(2));

        // Top-left
        g.drawLine(inset, inset, inset + sz, inset);
        g.drawLine(inset, inset, inset, inset + sz);
        // Top-right
        g.drawLine(width - inset, inset, width - inset - sz, inset);
        g.drawLine(width - inset, inset, width - inset, inset + sz);
        // Bottom-left
        g.drawLine(inset, height - inset, inset + sz, height - inset);
        g.drawLine(inset, height - inset, inset, height - inset - sz);
        // Bottom-right
        g.drawLine(width - inset, height - inset, width - inset - sz, height - inset);
        g.drawLine(width - inset, height - inset, width - inset, height - inset - sz);

        // Small rivet dots at corners
        int dotR = 3;
        g.setColor(new Color(150, 150, 160, alpha));
        g.fillOval(inset - dotR, inset - dotR, dotR * 2, dotR * 2);
        g.fillOval(width - inset - dotR, inset - dotR, dotR * 2, dotR * 2);
        g.fillOval(inset - dotR, height - inset - dotR, dotR * 2, dotR * 2);
        g.fillOval(width - inset - dotR, height - inset - dotR, dotR * 2, dotR * 2);
    }

    private static void drawDarkVignette(Graphics2D g, int width, int height) {
        // Cache vignette as BufferedImage — it's fully static (no time dependency)
        if (cachedVignette == null || cachedVigW != width || cachedVigH != height) {
            cachedVignette = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D vg = cachedVignette.createGraphics();

            int edgeW = width / 4;
            vg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            // Left
            GradientPaint left = new GradientPaint(0, 0, new Color(0, 0, 0, 120), edgeW, 0, new Color(0, 0, 0, 0));
            vg.setPaint(left);
            vg.fillRect(0, 0, edgeW, height);
            // Right
            GradientPaint right = new GradientPaint(width - edgeW, 0, new Color(0, 0, 0, 0), width, 0, new Color(0, 0, 0, 120));
            vg.setPaint(right);
            vg.fillRect(width - edgeW, 0, edgeW, height);
            // Top
            GradientPaint top = new GradientPaint(0, 0, new Color(0, 0, 0, 80), 0, height / 5, new Color(0, 0, 0, 0));
            vg.setPaint(top);
            vg.fillRect(0, 0, width, height / 5);
            // Bottom
            GradientPaint bot = new GradientPaint(0, height - height / 5, new Color(0, 0, 0, 0), 0, height, new Color(0, 0, 0, 80));
            vg.setPaint(bot);
            vg.fillRect(0, height - height / 5, width, height / 5);

            vg.dispose();
            cachedVigW = width;
            cachedVigH = height;
        }
        g.drawImage(cachedVignette, 0, 0, null);
    }

    // ─── SCREEN TITLE ────────────────────────────────────────────────────

    /**
     * Draw a stencil-style screen title with ember particles.
     * Replaces the repeated holographic title pattern.
     *
     * @param g       graphics context
     * @param text    title text
     * @param y       vertical center of the title
     * @param c1      gradient start color
     * @param c2      gradient end color
     * @param width   screen width (for centering)
     * @param time    animation time
     */
    public static void drawTitle(Graphics2D g, String text, int width, int y, Color c1, Color c2, double time) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setFont(FontPalette.TITLE_MEDIUM);
        FontMetrics fm = g2.getFontMetrics();
        int titleX = (width - fm.stringWidth(text)) / 2;

        // Heavy drop shadow
        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawString(text, titleX + 5, y + 5);
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawString(text, titleX + 3, y + 3);

        // Gradient fill
        GradientPaint gp = new GradientPaint(titleX, y - 40, c1, titleX + fm.stringWidth(text), y + 10, c2);
        g2.setPaint(gp);
        g2.drawString(text, titleX, y);

        // Animated ember glow along bottom edge of text
        int shineOffset = (int)(Math.sin(time * 3) * 20);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)(0.2 + 0.1 * Math.sin(time * 4))));
        g2.setColor(ColorPalette.ACCENT_ORANGE);
        g2.drawString(text, titleX + 1 + shineOffset / 15, y + 1);
        g2.setComposite(ColorPalette.ALPHA_FULL);

        // White hot-spot highlight
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
        g2.setColor(Color.WHITE);
        g2.drawString(text, titleX + shineOffset / 10, y - 1);
        g2.setComposite(ColorPalette.ALPHA_FULL);

        // Spark/ember particles along the title
        drawTitleEmbers(g2, titleX, y, fm.stringWidth(text), time);

        g2.dispose();
    }

    /**
     * Overload for larger or smaller title font.
     */
    public static void drawTitle(Graphics2D g, String text, int width, int y, Color c1, Color c2, double time, Font font) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int titleX = (width - fm.stringWidth(text)) / 2;

        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawString(text, titleX + 5, y + 5);
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawString(text, titleX + 3, y + 3);

        GradientPaint gp = new GradientPaint(titleX, y - 40, c1, titleX + fm.stringWidth(text), y + 10, c2);
        g2.setPaint(gp);
        g2.drawString(text, titleX, y);

        int shineOffset = (int)(Math.sin(time * 3) * 20);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)(0.2 + 0.1 * Math.sin(time * 4))));
        g2.setColor(ColorPalette.ACCENT_ORANGE);
        g2.drawString(text, titleX + 1 + shineOffset / 15, y + 1);
        g2.setComposite(ColorPalette.ALPHA_FULL);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
        g2.setColor(Color.WHITE);
        g2.drawString(text, titleX + shineOffset / 10, y - 1);
        g2.setComposite(ColorPalette.ALPHA_FULL);

        drawTitleEmbers(g2, titleX, y, fm.stringWidth(text), time);
        g2.dispose();
    }

    private static void drawTitleEmbers(Graphics2D g, int x, int y, int textWidth, double time) {
        // Small glowing particles drifting upward from the title
        int numEmbers = 8;
        for (int i = 0; i < numEmbers; i++) {
            double phase = i * Math.PI * 2.0 / numEmbers;
            double ex = x + textWidth * ((Math.sin(time * 0.7 + phase) + 1) / 2);
            double ey = y - 10 - 30 * ((time * 0.5 + phase * 0.3) % 1.0);
            int alpha = (int)(80 * (1.0 - ((time * 0.5 + phase * 0.3) % 1.0)));
            if (alpha < 0) alpha = 0;
            int size = 2 + (int)(Math.sin(time * 3 + phase) * 1.5);

            g.setColor(new Color(255, 150 + (int)(Math.sin(phase) * 50), 30, alpha));
            g.fillOval((int)ex, (int)ey, size, size);
        }
    }

    // ─── ANGULAR CARD ────────────────────────────────────────────────────

    /**
     * Draw an angular beveled card with chamfered top-left and bottom-right
     * corners (military / dog-tag aesthetic).
     */
    public static void drawCard(Graphics2D g, int x, int y, int w, int h, Color accent) {
        int chamfer = 14;

        // Build chamfered shape
        Path2D.Double shape = createChamferedRect(x, y, w, h, chamfer);

        // Shadow
        g.setColor(new Color(0, 0, 0, 100));
        AffineTransform at = AffineTransform.getTranslateInstance(4, 4);
        g.fill(at.createTransformedShape(shape));

        // Fill — metallic gradient
        GradientPaint cardGrad = new GradientPaint(
            x, y, ColorPalette.BG_CARD.brighter(),
            x, y + h, ColorPalette.BG_CARD
        );
        g.setPaint(cardGrad);
        g.fill(shape);

        // Inner glow line at top
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
        g.setColor(accent);
        g.setStroke(new BasicStroke(1));
        g.drawLine(x + chamfer + 5, y + 1, x + w - 5, y + 1);
        g.setComposite(ColorPalette.ALPHA_FULL);

        // Border
        g.setColor(ColorPalette.BORDER_STEEL);
        g.setStroke(new BasicStroke(2));
        g.draw(shape);

        // Accent line on left edge
        g.setColor(accent);
        g.setStroke(new BasicStroke(3));
        g.drawLine(x, y + chamfer + 5, x, y + h - chamfer - 5);
    }

    /**
     * Draw a selected/highlighted card variant with glow and caution-tape stripes.
     */
    public static void drawCardSelected(Graphics2D g, int x, int y, int w, int h, Color accent, double time) {
        int chamfer = 14;
        Path2D.Double shape = createChamferedRect(x, y, w, h, chamfer);

        // Glow behind card
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(accent);
        g.setStroke(new BasicStroke(8));
        g.draw(shape);
        g.setComposite(ColorPalette.ALPHA_FULL);

        // Shadow
        g.setColor(new Color(0, 0, 0, 120));
        AffineTransform at = AffineTransform.getTranslateInstance(5, 5);
        g.fill(at.createTransformedShape(shape));

        // Fill — brighter gradient
        GradientPaint cardGrad = new GradientPaint(
            x, y, ColorPalette.BG_CARD_SELECTED.brighter(),
            x, y + h, ColorPalette.BG_CARD_SELECTED
        );
        g.setPaint(cardGrad);
        g.fill(shape);

        // Caution-tape diagonal stripes
        Shape oldClip = g.getClip();
        g.setClip(shape);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
        g.setColor(ColorPalette.ACCENT_YELLOW);
        for (int i = -h; i < w + h; i += 16) {
            g.drawLine(x + i, y, x + i - h, y + h);
        }
        g.setClip(oldClip);
        g.setComposite(ColorPalette.ALPHA_FULL);

        // Border with animated glow
        int glowAlpha = (int)(180 + 75 * Math.sin(time * 5));
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.min(255, glowAlpha)));
        g.setStroke(new BasicStroke(3));
        g.draw(shape);

        // Accent line
        g.setColor(accent);
        g.setStroke(new BasicStroke(4));
        g.drawLine(x, y + chamfer + 5, x, y + h - chamfer - 5);
    }

    /**
     * Create a rectangle with chamfered (cut) top-left and bottom-right corners.
     */
    public static Path2D.Double createChamferedRect(int x, int y, int w, int h, int chamfer) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x + chamfer, y);                 // top edge start (after chamfer)
        p.lineTo(x + w, y);                       // top-right (square)
        p.lineTo(x + w, y + h - chamfer);          // right edge down to chamfer
        p.lineTo(x + w - chamfer, y + h);          // bottom-right chamfer
        p.lineTo(x, y + h);                       // bottom-left (square)
        p.lineTo(x, y + chamfer);                  // left edge up to chamfer
        p.closePath();                             // top-left chamfer
        return p;
    }

    // ─── PROGRESS BAR (MISSILE-ARMING GAUGE) ─────────────────────────────

    /**
     * Draw a missile-arming-gauge style progress bar with segmented fill
     * and tick marks.
     */
    public static void drawProgressBar(Graphics2D g, int x, int y, int w, int h, double progress, Color fillColor) {
        // Background track
        g.setColor(new Color(20, 25, 35));
        g.fillRect(x, y, w, h);

        // Segmented fill
        int fillW = (int)(w * Math.min(1.0, Math.max(0.0, progress)));
        if (fillW > 0) {
            GradientPaint gp = new GradientPaint(x, y, fillColor, x + fillW, y + h, fillColor.darker());
            g.setPaint(gp);
            g.fillRect(x, y, fillW, h);

            // Segments (gaps every 20px)
            g.setColor(new Color(0, 0, 0, 60));
            for (int sx = x + 20; sx < x + fillW; sx += 20) {
                g.drawLine(sx, y, sx, y + h);
            }

            // Hot tip glow
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g.setColor(Color.WHITE);
            g.fillRect(x + fillW - 3, y, 3, h);
            g.setComposite(ColorPalette.ALPHA_FULL);
        }

        // Tick marks along the bar
        g.setColor(new Color(100, 110, 130, 80));
        for (int tx = x; tx <= x + w; tx += w / 10) {
            g.drawLine(tx, y, tx, y + h / 3);
            g.drawLine(tx, y + h - h / 3, tx, y + h);
        }

        // Border
        g.setColor(ColorPalette.BORDER_STEEL);
        g.setStroke(new BasicStroke(2));
        g.drawRect(x, y, w, h);

        // Percentage text
        int pct = (int)(progress * 100);
        String pctText = pct + "%";
        g.setFont(FontPalette.TINY);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString(pctText, x + (w - fm.stringWidth(pctText)) / 2, y + h + 22);
    }

    // ─── RUBBER STAMP TEXT ───────────────────────────────────────────────

    /**
     * Draw a large angled rubber-stamp effect (MISSION FAILED / MISSION COMPLETE).
     */
    public static void drawStencilStamp(Graphics2D g, String text, int cx, int cy, Color color, double angle, double time) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.translate(cx, cy);
        g2.rotate(angle);

        g2.setFont(FontPalette.TITLE_LARGE);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getHeight();
        int tx = -tw / 2;
        int ty = fm.getAscent() / 2;

        // Stamp border rectangle
        int pad = 20;
        g2.setStroke(new BasicStroke(6));
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
        g2.drawRect(tx - pad, ty - th - pad / 2, tw + pad * 2, th + pad);

        // Inner double-line
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(tx - pad + 5, ty - th - pad / 2 + 5, tw + pad * 2 - 10, th + pad - 10);

        // Stamp text
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
        g2.drawString(text, tx, ty);

        // Rough edge effect — slight jitter
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
        g2.setColor(color);
        g2.drawString(text, tx + 2, ty + 1);
        g2.drawString(text, tx - 1, ty - 1);

        g2.dispose();
    }

    /**
     * Overload that accepts a custom font and uses default angle/time.
     */
    public static void drawStencilStamp(Graphics2D g, String text, int cx, int cy, Color color, Font font) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.translate(cx, cy);
        g2.rotate(-0.15); // slight angle

        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getHeight();
        int tx = -tw / 2;
        int ty = fm.getAscent() / 2;

        int pad = 20;
        g2.setStroke(new BasicStroke(6));
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
        g2.drawRect(tx - pad, ty - th - pad / 2, tw + pad * 2, th + pad);

        g2.setStroke(new BasicStroke(2));
        g2.drawRect(tx - pad + 5, ty - th - pad / 2 + 5, tw + pad * 2 - 10, th + pad - 10);

        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
        g2.drawString(text, tx, ty);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
        g2.setColor(color);
        g2.drawString(text, tx + 2, ty + 1);
        g2.drawString(text, tx - 1, ty - 1);

        g2.dispose();
    }

    // ─── WARNING LIGHTS ──────────────────────────────────────────────────

    /**
     * Draw flickering red warning lights in screen corners (game over effect).
     */
    public static void drawWarningLights(Graphics2D g, int width, int height, double time) {
        double flicker = Math.sin(time * 8);
        if (flicker < 0) return; // off half the time

        int alpha = (int)(flicker * 60);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, alpha / 255f)));

        // Red glow in corners
        int glowR = 150;
        g.setColor(new Color(255, 30, 30));

        // Top-left
        g.fillOval(-glowR / 2, -glowR / 2, glowR, glowR);
        // Top-right
        g.fillOval(width - glowR / 2, -glowR / 2, glowR, glowR);
        // Bottom-left
        g.fillOval(-glowR / 2, height - glowR / 2, glowR, glowR);
        // Bottom-right
        g.fillOval(width - glowR / 2, height - glowR / 2, glowR, glowR);

        g.setComposite(ColorPalette.ALPHA_FULL);
    }

    // ─── CONFETTI (VICTORY) ──────────────────────────────────────────────

    /**
     * Draw falling/rotating confetti particles for the victory screen.
     */
    public static void drawConfetti(Graphics2D g, int width, int height, double time) {
        int count = 40;
        if (confetti == null || confetti.length != count) {
            confetti = new double[count][5]; // x, y, rotation, speed, colorIndex
            for (int i = 0; i < count; i++) {
                confetti[i][0] = Math.random() * width;
                confetti[i][1] = Math.random() * height;
                confetti[i][2] = Math.random() * Math.PI * 2;
                confetti[i][3] = 30 + Math.random() * 50;
                confetti[i][4] = i % 4;
            }
        }

        Color[] colors = {
            ColorPalette.VICTORY_GOLD,
            ColorPalette.ACCENT_ORANGE,
            ColorPalette.SUCCESS_GREEN,
            ColorPalette.ACCENT_YELLOW
        };

        for (int i = 0; i < count; i++) {
            double cx = confetti[i][0] + Math.sin(time * 0.5 + i) * 30;
            double cy = (confetti[i][1] + time * confetti[i][3]) % (height + 20) - 10;
            double rot = confetti[i][2] + time * 2;
            int colorIdx = (int) confetti[i][4];

            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(cx, cy);
            g2.rotate(rot);
            g2.setColor(colors[colorIdx]);
            g2.fillRect(-4, -2, 8, 4);
            g2.dispose();
        }
    }

    // ─── MEDAL / RANK BADGE ──────────────────────────────────────────────

    /**
     * Draw a rank medal badge at the given position.
     * rank: "S", "A", "B", "C", "D"
     * Enhanced military medal design with metallic finish, star shape,
     * rotating rays, shine sweep, and ribbon banner.
     */
    public static void drawRankBadge(Graphics2D g, int cx, int cy, int radius, String rank, double time) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Color based on rank
        Color rankColor, rankColorDark, rankColorBright;
        switch (rank) {
            case "S":
                rankColor = ColorPalette.MEDAL_GOLD;
                rankColorDark = new Color(180, 140, 0);
                rankColorBright = new Color(255, 245, 150);
                break;
            case "A":
                rankColor = ColorPalette.SUCCESS_GREEN;
                rankColorDark = new Color(40, 130, 40);
                rankColorBright = new Color(160, 255, 160);
                break;
            case "B":
                rankColor = ColorPalette.ACCENT_CYAN;
                rankColorDark = new Color(30, 100, 140);
                rankColorBright = new Color(160, 230, 255);
                break;
            case "C":
                rankColor = ColorPalette.ACCENT_YELLOW;
                rankColorDark = new Color(140, 130, 30);
                rankColorBright = new Color(255, 255, 160);
                break;
            default:
                rankColor = ColorPalette.TEXT_DIM;
                rankColorDark = new Color(80, 80, 80);
                rankColorBright = new Color(180, 180, 180);
                break;
        }

        // ── Rotating light rays behind the badge (S and A ranks) ──
        if ("S".equals(rank) || "A".equals(rank)) {
            int rayCount = "S".equals(rank) ? 12 : 8;
            float rayAlpha = "S".equals(rank) ? 0.18f : 0.10f;
            double rotSpeed = "S".equals(rank) ? 0.4 : 0.25;
            Graphics2D gr = (Graphics2D) g2.create();
            gr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rayAlpha));
            for (int i = 0; i < rayCount; i++) {
                double a = time * rotSpeed + i * Math.PI * 2.0 / rayCount;
                double halfW = Math.PI / (rayCount * 2.5);
                Path2D ray = new Path2D.Double();
                ray.moveTo(cx, cy);
                ray.lineTo(cx + Math.cos(a - halfW) * radius * 2.8, cy + Math.sin(a - halfW) * radius * 2.8);
                ray.lineTo(cx + Math.cos(a + halfW) * radius * 2.8, cy + Math.sin(a + halfW) * radius * 2.8);
                ray.closePath();
                gr.setColor(rankColorBright);
                gr.fill(ray);
            }
            gr.dispose();
        }

        // ── Multi-layered outer glow ──
        for (int layer = 3; layer >= 0; layer--) {
            float glowAlpha = (float)(0.08 + 0.05 * Math.sin(time * 3.0 + layer * 0.5)) * (4 - layer) / 4.0f;
            int expand = 6 + layer * 5;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.01f, Math.min(glowAlpha, 1.0f))));
            g2.setColor(rankColor);
            g2.fillOval(cx - radius - expand, cy - radius - expand, (radius + expand) * 2, (radius + expand) * 2);
        }
        g2.setComposite(ColorPalette.ALPHA_FULL);

        // ── 8-pointed star shape behind the circle ──
        int starOuter = (int)(radius * 1.35);
        int starInner = (int)(radius * 1.05);
        Path2D star = new Path2D.Double();
        int starPoints = 8;
        for (int i = 0; i < starPoints * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / starPoints;
            int r = (i % 2 == 0) ? starOuter : starInner;
            double px = cx + r * Math.cos(a);
            double py = cy + r * Math.sin(a);
            if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.closePath();

        // Star metallic gradient fill
        g2.setPaint(new RadialGradientPaint(cx, cy, starOuter,
            new float[]{0.0f, 0.5f, 1.0f},
            new Color[]{rankColorBright, rankColor, rankColorDark}));
        g2.fill(star);

        // Star border
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(rankColorDark.getRed(), rankColorDark.getGreen(), rankColorDark.getBlue(), 200));
        g2.draw(star);

        // ── Main circle — metallic gradient fill ──
        g2.setPaint(new RadialGradientPaint(
            cx - radius / 4, cy - radius / 4, radius * 1.4f,
            new float[]{0.0f, 0.4f, 0.85f, 1.0f},
            new Color[]{
                new Color(60, 60, 80),
                new Color(35, 35, 50),
                new Color(20, 20, 30),
                new Color(10, 10, 18)
            }));
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // ── Thick ornamental border ring ──
        g2.setStroke(new BasicStroke(4f));
        g2.setPaint(new RadialGradientPaint(cx, cy, radius,
            new float[]{0.85f, 0.92f, 1.0f},
            new Color[]{rankColorDark, rankColor, rankColorBright}));
        g2.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // ── Compass tick marks around the border ──
        g2.setStroke(new BasicStroke(1.5f));
        int ticks = 24;
        for (int i = 0; i < ticks; i++) {
            double a = i * Math.PI * 2.0 / ticks;
            boolean major = (i % 6 == 0);
            int innerR = major ? radius - 8 : radius - 5;
            int outerR = radius - 2;
            int x1 = cx + (int)(innerR * Math.cos(a));
            int y1 = cy + (int)(innerR * Math.sin(a));
            int x2 = cx + (int)(outerR * Math.cos(a));
            int y2 = cy + (int)(outerR * Math.sin(a));
            g2.setColor(new Color(rankColor.getRed(), rankColor.getGreen(), rankColor.getBlue(), major ? 180 : 80));
            g2.drawLine(x1, y1, x2, y2);
        }

        // ── Inner decorative ring ──
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(rankColor.getRed(), rankColor.getGreen(), rankColor.getBlue(), 120));
        int innerRing = radius - 10;
        g2.drawOval(cx - innerRing, cy - innerRing, innerRing * 2, innerRing * 2);

        // ── Dotted accent ring ──
        int dotRing = radius - 14;
        int dotCount = 32;
        for (int i = 0; i < dotCount; i++) {
            double a = i * Math.PI * 2.0 / dotCount;
            int dx = cx + (int)(dotRing * Math.cos(a));
            int dy = cy + (int)(dotRing * Math.sin(a));
            g2.setColor(new Color(rankColor.getRed(), rankColor.getGreen(), rankColor.getBlue(), 60));
            g2.fillOval(dx - 1, dy - 1, 2, 2);
        }

        // ── Orbiting sparkles for S rank ──
        if ("S".equals(rank)) {
            int sparkles = 6;
            for (int i = 0; i < sparkles; i++) {
                double a = time * 1.2 + i * Math.PI * 2.0 / sparkles;
                double dist = radius + 12 + 4 * Math.sin(time * 2.5 + i);
                int sx = cx + (int)(dist * Math.cos(a));
                int sy = cy + (int)(dist * Math.sin(a));
                int sSize = 3 + (int)(2 * Math.abs(Math.sin(time * 3.0 + i * 1.5)));
                float sAlpha = 0.5f + 0.4f * (float)Math.abs(Math.sin(time * 2.0 + i));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sAlpha));
                g2.setColor(rankColorBright);
                // Draw a tiny 4-pointed star sparkle
                Path2D sparkle = new Path2D.Double();
                sparkle.moveTo(sx, sy - sSize);
                sparkle.lineTo(sx + sSize / 3.0, sy);
                sparkle.lineTo(sx, sy + sSize);
                sparkle.lineTo(sx - sSize / 3.0, sy);
                sparkle.closePath();
                g2.fill(sparkle);
                sparkle = new Path2D.Double();
                sparkle.moveTo(sx - sSize, sy);
                sparkle.lineTo(sx, sy + sSize / 3.0);
                sparkle.lineTo(sx + sSize, sy);
                sparkle.lineTo(sx, sy - sSize / 3.0);
                sparkle.closePath();
                g2.fill(sparkle);
            }
            g2.setComposite(ColorPalette.ALPHA_FULL);
        }

        // ── Rank letter — embossed 3D effect ──
        Font rankFont = FontPalette.get(Font.BOLD, radius * 1.1f);
        g2.setFont(rankFont);
        FontMetrics fm = g2.getFontMetrics();
        int tx = cx - fm.stringWidth(rank) / 2;
        int ty = cy + fm.getAscent() / 2 - 2;

        // Shadow (offset down-right)
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(rank, tx + 2, ty + 2);

        // Highlight (offset up-left)
        g2.setColor(new Color(rankColorBright.getRed(), rankColorBright.getGreen(), rankColorBright.getBlue(), 80));
        g2.drawString(rank, tx - 1, ty - 1);

        // Main letter with gradient
        g2.setPaint(new GradientPaint(tx, ty - fm.getAscent(), rankColorBright, tx, ty, rankColor));
        g2.drawString(rank, tx, ty);

        // ── Diagonal shine sweep (periodic) ──
        double shinePhase = (time * 0.5) % 3.0;
        if (shinePhase < 1.0) {
            Graphics2D gs = (Graphics2D) g2.create();
            gs.setClip(new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
            float sweepX = (float)(shinePhase * 3.0 - 1.0);
            float x1 = cx - radius + (radius * 2) * sweepX;
            int shineW = (int)(radius * 0.4);
            gs.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            gs.setPaint(new GradientPaint(x1, cy - radius, new Color(255, 255, 255, 0),
                x1 + shineW / 2f, cy, new Color(255, 255, 255, 120),
                true));
            gs.fillRect((int) x1, cy - radius, shineW, radius * 2);
            gs.dispose();
        }

        // ── Ribbon banner below the badge ──
        int ribbonW = (int)(radius * 1.6);
        int ribbonH = (int)(radius * 0.45);
        int ribbonY = cy + radius + 4;
        int ribbonX = cx - ribbonW / 2;
        // Ribbon notch shape
        Path2D ribbon = new Path2D.Double();
        ribbon.moveTo(ribbonX - 6, ribbonY);
        ribbon.lineTo(ribbonX + ribbonW + 6, ribbonY);
        ribbon.lineTo(ribbonX + ribbonW + 6, ribbonY + ribbonH);
        ribbon.lineTo(ribbonX + ribbonW / 2.0, ribbonY + ribbonH - 5);
        ribbon.lineTo(ribbonX - 6, ribbonY + ribbonH);
        ribbon.closePath();
        // Ribbon fill
        g2.setPaint(new GradientPaint(ribbonX, ribbonY, rankColor, ribbonX, ribbonY + ribbonH, rankColorDark));
        g2.fill(ribbon);
        // Ribbon border
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(0, 0, 0, 100));
        g2.draw(ribbon);
        // Ribbon text
        String rankName;
        switch (rank) {
            case "S": rankName = "ELITE"; break;
            case "A": rankName = "EXCELLENT"; break;
            case "B": rankName = "GOOD"; break;
            case "C": rankName = "AVERAGE"; break;
            default: rankName = "ROOKIE"; break;
        }
        Font ribbonFont = FontPalette.get(Font.BOLD, radius * 0.28f);
        g2.setFont(ribbonFont);
        FontMetrics rfm = g2.getFontMetrics();
        g2.setColor(new Color(0, 0, 0, 150));
        g2.drawString(rankName, cx - rfm.stringWidth(rankName) / 2 + 1, ribbonY + ribbonH / 2 + rfm.getAscent() / 2 - 3 + 1);
        g2.setColor(new Color(255, 255, 255, 230));
        g2.drawString(rankName, cx - rfm.stringWidth(rankName) / 2, ribbonY + ribbonH / 2 + rfm.getAscent() / 2 - 3);

        g2.dispose();
    }

    // ─── JET SILHOUETTE ──────────────────────────────────────────────────

    // Pre-computed jet polygon templates (avoid per-frame array allocation)
    private static final int[][] JET_XP_TEMPLATES = new int[2][];
    private static final int[][] JET_YP_TEMPLATES = new int[2][];
    private static final Color CONTRAIL_COLOR = new Color(200, 200, 220, 30);
    private static final float[] JET_ALPHAS = {0.08f, 0.11f};
    private static final BasicStroke CONTRAIL_STROKE = new BasicStroke(2);
    static {
        int[] baseXp = {0, -30, -40, -50, -40, -30, -50, -55, -50, -30, 0, 20};
        int[] baseYp = {0, -5, -5, -15, -5, -8, -8, -3, 0, 3, 3, 0};
        for (int j = 0; j < 2; j++) {
            int scale = 2 + j;
            JET_XP_TEMPLATES[j] = new int[baseXp.length];
            JET_YP_TEMPLATES[j] = new int[baseYp.length];
            for (int i = 0; i < baseXp.length; i++) {
                JET_XP_TEMPLATES[j][i] = baseXp[i] * scale;
                JET_YP_TEMPLATES[j][i] = baseYp[i] * scale;
            }
        }
    }

    /**
     * Draw an animated jet silhouette streaking across the background.
     * Uses pre-computed polygon templates and cached Color/Stroke objects.
     */
    public static void drawJetSilhouette(Graphics2D g, int width, int height, double time) {
        Composite origComposite = g.getComposite();
        AffineTransform origTransform = g.getTransform();

        for (int j = 0; j < 2; j++) {
            double speed = 80 + j * 40;
            double yPos = height * (0.25 + j * 0.3);
            double xPos = ((time * speed) % (width + 200)) - 100;

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, JET_ALPHAS[j]));
            g.setColor(ColorPalette.TEXT_DIM);
            g.translate(xPos, yPos);

            g.fillPolygon(JET_XP_TEMPLATES[j], JET_YP_TEMPLATES[j], JET_XP_TEMPLATES[j].length);

            // Contrail
            int scale = 2 + j;
            g.setStroke(CONTRAIL_STROKE);
            g.setColor(CONTRAIL_COLOR);
            g.drawLine(-55 * scale, 0, -55 * scale - 200, (int)(Math.sin(time + j) * 3));

            g.setTransform(origTransform);
        }
        g.setComposite(origComposite);
    }

    // ─── UTILITY ─────────────────────────────────────────────────────────

    /**
     * Get a cached radial glow image for the given base color.
     * Returns a pre-rendered GLOW_IMG_SIZE x GLOW_IMG_SIZE BufferedImage
     * with a soft radial gradient from the color center to transparent edge.
     * Use with AlphaComposite to control per-instance intensity.
     */
    public static BufferedImage getCachedGlow(Color baseColor) {
        int key = baseColor.getRGB();
        BufferedImage glow = glowCache.get(key);
        if (glow == null) {
            glow = new BufferedImage(GLOW_IMG_SIZE, GLOW_IMG_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = glow.createGraphics();
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float radius = GLOW_IMG_SIZE / 2f;
            Color transparent = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 0);
            Color center = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 180);
            Color mid = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 100);
            java.awt.RadialGradientPaint rgp = new java.awt.RadialGradientPaint(
                radius, radius, radius,
                new float[]{0.0f, 0.6f, 1.0f},
                new Color[]{center, mid, transparent}
            );
            gg.setPaint(rgp);
            gg.fillOval(0, 0, GLOW_IMG_SIZE, GLOW_IMG_SIZE);
            gg.dispose();
            glowCache.put(key, glow);
        }
        return glow;
    }

    /**
     * Calculate a rank string based on a score and base threshold.
     */
    public static String calculateRank(int score, int baseThreshold) {
        if (score >= baseThreshold * 4) return "S";
        if (score >= baseThreshold * 3) return "A";
        if (score >= baseThreshold * 2) return "B";
        if (score >= baseThreshold) return "C";
        return "D";
    }

    /**
     * Calculate rank with default threshold of 1000.
     */
    public static String calculateRank(int score) {
        return calculateRank(score, 1000);
    }

    private UITheme() { throw new AssertionError("Utility class"); }
}
