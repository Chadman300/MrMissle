import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Mr. Missle");
            
            // Set application icon
            try {
                BufferedImage icon = ImageIO.read(new File("sprites/Missle Man Assets/MissleManLogo.png"));
                frame.setIconImage(icon);
            } catch (Exception e) {
                System.err.println("Could not load application icon: " + e.getMessage());
            }
            
            Game game = new Game();
            frame.add(game);
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
            
            // Start in fullscreen mode with window decorations hidden
            frame.setUndecorated(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setResizable(true); // Allow resizing if user exits fullscreen
            
            frame.setVisible(true);
            game.start();
        });
    }
}
