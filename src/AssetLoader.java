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
                    return img;
                }
            } finally {
                stream.close();
            }
        }
        
        // Fallback to file system (works in IDE)
        File file = new File(normalizedPath);
        if (file.exists()) {
            return ImageIO.read(file);
        }
        
        // Try with backslashes for Windows
        file = new File(path);
        if (file.exists()) {
            return ImageIO.read(file);
        }
        
        throw new IOException("Could not find image: " + path);
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
}
