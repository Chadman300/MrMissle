import config.FontPalette;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/**
 * Utility class for loading assets that works both in IDE and JAR environments
 */
public class AssetLoader {
    
    /**
     * Load an image from the classpath (works inside JAR)
     */
    public static BufferedImage loadImage(String path) throws IOException {
        // Normalize path - use forward slashes and ensure it starts correctly
        String normalizedPath = normalizePath(path);
        
        // Try loading as resource first (works in JAR)
        InputStream stream = AssetLoader.class.getResourceAsStream("/" + normalizedPath);
        if (stream != null) {
            try {
                BufferedImage img = ImageIO.read(stream);
                if (img != null) {
                    return toCompatibleImage(img);
                }
            } finally {
                stream.close();
            }
        }
        
        // Fallback to file system (works in IDE)
        File file = new File(normalizedPath);
        if (file.exists()) {
            return toCompatibleImage(ImageIO.read(file));
        }
        
        // Try with backslashes for Windows
        file = new File(path);
        if (file.exists()) {
            return toCompatibleImage(ImageIO.read(file));
        }
        
        throw new IOException("Could not find image: " + path);
    }

    /**
     * Convert a BufferedImage to a GPU-compatible format when hardware
     * acceleration is enabled. This ensures the image is stored in a format
     * the GPU can blit without per-pixel conversion, which is the single
     * biggest performance win for hardware-accelerated rendering.
     */
    private static BufferedImage toCompatibleImage(BufferedImage img) {
        if (img == null || !Game.enableGPUAcceleration) return img;
        try {
            boolean hasAlpha = img.getColorModel().hasAlpha();
            BufferedImage compat = Game.createOptimalImage(img.getWidth(), img.getHeight(), hasAlpha);
            Graphics2D g = compat.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            img.flush(); // release original non-compatible image
            return compat;
        } catch (Exception e) {
            return img; // fallback to original on any error
        }
    }
    
    /**
     * Load an audio clip from the classpath (works inside JAR)
     */
    public static Clip loadAudioClip(String path) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        String normalizedPath = normalizePath(path);
        
        // Try loading as resource first (works in JAR)
        InputStream stream = AssetLoader.class.getResourceAsStream("/" + normalizedPath);
        if (stream != null) {
            try {
                BufferedInputStream bufferedStream = new BufferedInputStream(stream);
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedStream);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                return clip;
            } catch (Exception e) {
                // Fall through to file system approach
            }
        }
        
        // Fallback to file system (works in IDE)
        File file = new File(normalizedPath);
        if (!file.exists()) {
            file = new File(path);
        }
        
        if (file.exists()) {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            return clip;
        }
        
        throw new IOException("Could not find audio: " + path);
    }
    
    /**
     * Get an AudioInputStream from the classpath (works inside JAR)
     */
    public static AudioInputStream getAudioInputStream(String path) throws IOException, UnsupportedAudioFileException {
        String normalizedPath = normalizePath(path);
        
        // Try loading as resource first (works in JAR)
        InputStream stream = AssetLoader.class.getResourceAsStream("/" + normalizedPath);
        if (stream != null) {
            BufferedInputStream bufferedStream = new BufferedInputStream(stream);
            return AudioSystem.getAudioInputStream(bufferedStream);
        }
        
        // Fallback to file system (works in IDE)
        File file = new File(normalizedPath);
        if (!file.exists()) {
            file = new File(path);
        }
        
        if (file.exists()) {
            return AudioSystem.getAudioInputStream(file);
        }
        
        throw new IOException("Could not find audio: " + path);
    }
    
    /**
     * Check if a resource exists
     */
    public static boolean resourceExists(String path) {
        String normalizedPath = normalizePath(path);
        
        // Check as resource
        if (AssetLoader.class.getResource("/" + normalizedPath) != null) {
            return true;
        }
        
        // Check as file
        return new File(normalizedPath).exists() || new File(path).exists();
    }
    
    /**
     * Pre-scale a BufferedImage so that its largest dimension equals maxSize.
     * Uses high-quality bicubic interpolation.  If the image is already smaller
     * than maxSize it is returned unchanged.
     */
    public static BufferedImage prescaleImage(BufferedImage img, int maxSize) {
        if (img == null) return null;
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= maxSize && h <= maxSize) return img; // already small enough

        double scale = (double) maxSize / Math.max(w, h);
        int newW = Math.max(1, (int)(w * scale));
        int newH = Math.max(1, (int)(h * scale));

        BufferedImage scaled = Game.createOptimalImage(newW, newH, true);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(img, 0, 0, newW, newH, null);
        g2d.dispose();
        return scaled;
    }

    /**
     * Normalize path for resource loading
     */
    private static String normalizePath(String path) {
        // Replace backslashes with forward slashes
        String normalized = path.replace("\\", "/");
        // Remove leading slashes
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // Remove ../ prefix if present
        while (normalized.startsWith("../")) {
            normalized = normalized.substring(3);
        }
        return normalized;
    }

    /**
     * Initialize all asset subsystems (fonts, etc.).
     * Call once at startup before any rendering.
     */
    public static void initAll() {
        FontPalette.init();
    }
}
