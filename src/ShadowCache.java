import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;

/**
 * Generates and caches soft drop-shadow images from source sprites.
 * 
 * Shadows are black silhouettes with a soft fade-out blur on all edges.
 * Uses an IdentityHashMap keyed on the source BufferedImage reference,
 * so each unique sprite gets exactly one cached shadow.
 */
public class ShadowCache {
    
    /** Cached shadow images keyed by source sprite reference. */
    private static final IdentityHashMap<BufferedImage, BufferedImage> cache = new IdentityHashMap<>();
    
    /** Extra pixels added around the shadow for the blur fade-out. */
    private static final int PADDING = 18;
    
    /** Box blur radius — applied 3 times for a smooth Gaussian-like blur. */
    private static final int BLUR_RADIUS = 6;
    
    /**
     * Get the cached shadow for a source sprite, generating it if needed.
     * The returned image is larger than the source by 2*PADDING in each dimension.
     * Draw it offset by -PADDING relative to where you'd draw the source sprite.
     *
     * @param source the sprite to generate a shadow for
     * @return the cached shadow image (ARGB, black silhouette with soft edges)
     */
    public static BufferedImage getShadow(BufferedImage source) {
        BufferedImage shadow = cache.get(source);
        if (shadow == null) {
            shadow = generateShadow(source);
            cache.put(source, shadow);
        }
        return shadow;
    }
    
    /**
     * Returns the padding added around the shadow image.
     * When drawing the shadow, offset by -getPadding() on both axes
     * so the shadow silhouette aligns with the source sprite.
     */
    public static int getPadding() {
        return PADDING;
    }
    
    /**
     * Clear the entire shadow cache.
     * Call when sprites are reloaded or on major state transitions.
     */
    public static void clear() {
        cache.clear();
    }
    
    /**
     * Generate a shadow image from a source sprite.
     * 1. Extract alpha channel as a black silhouette
     * 2. Apply 3-pass box blur for soft fade-out edges
     */
    private static BufferedImage generateShadow(BufferedImage source) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        int shadowW = srcW + PADDING * 2;
        int shadowH = srcH + PADDING * 2;
        
        // Step 1: Create black silhouette from source alpha channel
        // We work with a raw alpha array for speed
        int[] pixels = new int[shadowW * shadowH];
        // pixels default to 0 (fully transparent black)
        
        // Copy source alpha into the center of our padded canvas
        int[] srcPixels = new int[srcW * srcH];
        source.getRGB(0, 0, srcW, srcH, srcPixels, 0, srcW);
        
        for (int sy = 0; sy < srcH; sy++) {
            for (int sx = 0; sx < srcW; sx++) {
                int alpha = (srcPixels[sy * srcW + sx] >>> 24) & 0xFF;
                if (alpha > 0) {
                    // Black pixel with source alpha: 0xAARRGGBB where RGB=0
                    pixels[(sy + PADDING) * shadowW + (sx + PADDING)] = (alpha << 24);
                }
            }
        }
        
        // Step 2: Extract alpha channel for blur processing
        int[] alphaChannel = new int[shadowW * shadowH];
        for (int i = 0; i < pixels.length; i++) {
            alphaChannel[i] = (pixels[i] >>> 24) & 0xFF;
        }
        
        // Step 3: Apply 3-pass box blur (approximates Gaussian blur)
        int[] temp = new int[shadowW * shadowH];
        for (int pass = 0; pass < 3; pass++) {
            boxBlurH(alphaChannel, temp, shadowW, shadowH, BLUR_RADIUS);
            boxBlurV(temp, alphaChannel, shadowW, shadowH, BLUR_RADIUS);
        }
        
        // Step 4: Reconstruct ARGB pixels (black with blurred alpha)
        for (int i = 0; i < pixels.length; i++) {
            int a = Math.min(255, alphaChannel[i]);
            pixels[i] = (a > 0) ? (a << 24) : 0;  // Black with blurred alpha
        }
        
        // Step 5: Create the final shadow BufferedImage
        BufferedImage shadow = new BufferedImage(shadowW, shadowH, BufferedImage.TYPE_INT_ARGB);
        shadow.setRGB(0, 0, shadowW, shadowH, pixels, 0, shadowW);
        
        return shadow;
    }
    
    /** Horizontal box blur pass. */
    private static void boxBlurH(int[] src, int[] dst, int w, int h, int r) {
        double invSize = 1.0 / (r + r + 1);
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            int sum = 0;
            
            // Seed the running sum with the left edge pixel repeated
            for (int ix = -r; ix <= r; ix++) {
                sum += src[rowOffset + Math.max(0, Math.min(w - 1, ix))];
            }
            
            for (int x = 0; x < w; x++) {
                dst[rowOffset + x] = (int)(sum * invSize);
                // Advance the window
                int left = Math.max(0, x - r);
                int right = Math.min(w - 1, x + r + 1);
                sum += src[rowOffset + right] - src[rowOffset + left];
            }
        }
    }
    
    /** Vertical box blur pass. */
    private static void boxBlurV(int[] src, int[] dst, int w, int h, int r) {
        double invSize = 1.0 / (r + r + 1);
        for (int x = 0; x < w; x++) {
            int sum = 0;
            
            // Seed the running sum with the top edge pixel repeated
            for (int iy = -r; iy <= r; iy++) {
                sum += src[Math.max(0, Math.min(h - 1, iy)) * w + x];
            }
            
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = (int)(sum * invSize);
                // Advance the window
                int top = Math.max(0, y - r);
                int bottom = Math.min(h - 1, y + r + 1);
                sum += src[bottom * w + x] - src[top * w + x];
            }
        }
    }
}
