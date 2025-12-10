import javax.swing.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("One Hit Man");
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
