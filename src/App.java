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
import javax.imageio.ImageIO;
import javax.swing.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Mr. Missile");
            
            // Set application icon
            try {
                BufferedImage icon = ImageIO.read(new File("sprites/Missle Man Assets/MissleManLogo.png"));
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
            
            // Loading expand will detect current monitor dynamically
            game.setLoadingExpandBounds(screenBounds);
            
            frame.setVisible(true);
            game.start();
        });
    }
}
