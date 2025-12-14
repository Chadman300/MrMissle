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
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setUndecorated(true); // Remove window borders
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH); // Maximize window
            frame.setVisible(true);
            game.start();
        });
    }
}
