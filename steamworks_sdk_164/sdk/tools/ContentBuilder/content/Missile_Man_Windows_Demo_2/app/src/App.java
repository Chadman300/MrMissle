import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.*;

public class App {
    public static void main(String[] args) {
        // Apply GPU pipeline flags BEFORE any AWT/Swing class creates a window.
        // Read saved GPU settings from a lightweight config file (independent of save slots)
        // so we can set JVM properties before the rendering pipeline initializes.
        System.out.println("[GPU] === GPU Pipeline Initialization ===");
        try {
            File gpuConfig = new File("config/gpu.properties");
            if (gpuConfig.exists()) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(gpuConfig)) {
                    props.load(fis);
                }
                boolean gpuEnabled = Boolean.parseBoolean(props.getProperty("enableGPUAcceleration", "false"));
                int pipelineType = Integer.parseInt(props.getProperty("gpuPipelineType", "0"));
                
                System.out.println("[GPU] Config loaded: enabled=" + gpuEnabled + ", pipeline=" + pipelineType
                    + ", bufferMode=" + props.getProperty("bufferStrategyMode", "1"));
                
                if (gpuEnabled) {
                    switch (pipelineType) {
                        case 0: // Auto — enable both, Java picks the best
                            System.setProperty("sun.java2d.opengl", "True");
                            break;
                        case 1: // OpenGL
                            System.setProperty("sun.java2d.opengl", "True");
                            System.setProperty("sun.java2d.d3d", "false");
                            break;
                        case 2: // Direct3D
                            System.setProperty("sun.java2d.d3d", "True");
                            System.setProperty("sun.java2d.opengl", "false");
                            break;
                    }
                    System.out.println("[GPU] Pipeline flags set: opengl=" + System.getProperty("sun.java2d.opengl")
                        + ", d3d=" + System.getProperty("sun.java2d.d3d"));
                } else {
                    // Explicitly disable hardware pipelines so the JVM uses software rendering
                    System.setProperty("sun.java2d.opengl", "false");
                    System.setProperty("sun.java2d.d3d", "false");
                    System.out.println("[GPU] Acceleration disabled in config \u2014 forcing software pipeline");
                    System.out.println("[GPU] Pipeline flags set: opengl=" + System.getProperty("sun.java2d.opengl")
                        + ", d3d=" + System.getProperty("sun.java2d.d3d"));
                }
                
                // Pre-load saved values so Game can use them immediately
                Game.enableGPUAcceleration = gpuEnabled;
                Game.gpuPipelineType = pipelineType;
                Game.bufferStrategyMode = Integer.parseInt(props.getProperty("bufferStrategyMode", "1"));
            } else {
                System.out.println("[GPU] No config/gpu.properties found — first run, using defaults");
            }
        } catch (Exception e) {
            System.err.println("[GPU] Could not load GPU config: " + e.getMessage());
        }
        
        SwingUtilities.invokeLater(() -> {
            // Detect GPU availability (safe to call here — no window created yet)
            Game.detectGPU();
            System.out.println("[GPU] Active Java2D pipeline: opengl=" + System.getProperty("sun.java2d.opengl")
                + ", d3d=" + System.getProperty("sun.java2d.d3d"));
            System.out.println("[GPU] === End GPU Init ===");
            
            JFrame frame = new JFrame("Missile Man");
            
            // Set application icon
            try {
                BufferedImage icon = ImageIO.read(new File("sprites/Steam Page Art/Shortcut Icon.png"));
                frame.setIconImage(icon);
            } catch (Exception e) {
                System.err.println("Could not load application icon: " + e.getMessage());
            }
            
            Game game = new Game();
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            
            // Add window close handler to auto-save before exiting
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // Auto-save on window close
                    game.saveOnExit();
                    System.exit(0);
                }
            });
            
            // Use the default screen (primary monitor)
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice targetScreen = ge.getDefaultScreenDevice();
            Rectangle screenBounds = targetScreen.getDefaultConfiguration().getBounds();
            
            // Start windowed for loading screen — locked to 16:9 (1920x1080) aspect ratio
            frame.setResizable(true);
            int contentH = screenBounds.height / 2;
            int contentW = (int)(contentH * 16.0 / 9.0);
            
            // Set the game panel's preferred size to the exact content dimensions
            game.setPreferredSize(new Dimension(contentW, contentH));
            frame.add(game);
            frame.pack(); // sizes frame to fit content + decorations (title bar)
            frame.setLocationRelativeTo(null); // center on screen
            Game.isFullscreen = false; // Start windowed
            
            // Enforce 16:9 aspect ratio during resize (corner-drag style scaling)
            frame.addComponentListener(new ComponentAdapter() {
                private boolean adjusting = false;
                private int lastW = contentW, lastH = contentH;
                private static final double ASPECT = 16.0 / 9.0;
                
                @Override
                public void componentResized(ComponentEvent e) {
                    if (adjusting || Game.isFullscreen) return;
                    adjusting = true;
                    
                    Insets insets = frame.getInsets();
                    int cw = frame.getWidth() - insets.left - insets.right;
                    int ch = frame.getHeight() - insets.top - insets.bottom;
                    
                    boolean wChanged = (cw != lastW);
                    boolean hChanged = (ch != lastH);
                    
                    int newW, newH;
                    if (wChanged && !hChanged) {
                        // Horizontal edge drag → derive height from width
                        newW = cw;
                        newH = (int)(cw / ASPECT);
                    } else {
                        // Vertical edge or corner drag → derive width from height
                        newH = ch;
                        newW = (int)(ch * ASPECT);
                    }
                    
                    lastW = newW;
                    lastH = newH;
                    
                    frame.setSize(newW + insets.left + insets.right,
                                  newH + insets.top + insets.bottom);
                    game.setPreferredSize(new Dimension(newW, newH));
                    
                    adjusting = false;
                }
            });
            
            frame.setVisible(true);
            game.start();
        });
    }
}
