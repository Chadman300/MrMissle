import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerTest {
    private Player player;
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;
    
    @BeforeEach
    public void setUp() {
        player = new Player(960, 540, 0); // Center of screen, no speed upgrade
    }
    
    @Test
    public void testInitialPosition() {
        assertEquals(960, player.getX(), 0.1);
        assertEquals(540, player.getY(), 0.1);
    }
    
    @Test
    public void testMovement() {
        boolean[] keys = new boolean[256];
        
        double initialX = player.getX();
        
        // Simulate right movement
        keys[68] = true; // 'D' key
        player.update(keys, SCREEN_WIDTH, SCREEN_HEIGHT, 1.0);
        
        assertTrue(player.getX() > initialX);
    }
    
    @Test
    public void testBoundaryConstraints() {
        boolean[] keys = new boolean[256];
        
        // Create player at left edge
        Player edgePlayer = new Player(0, 540, 0);
        
        // Try to move left
        keys[65] = true; // 'A' key
        for (int i = 0; i < 100; i++) {
            edgePlayer.update(keys, SCREEN_WIDTH, SCREEN_HEIGHT, 1.0);
        }
        
        // Should not go below 0
        assertTrue(edgePlayer.getX() >= 0);
    }
    
    @Test
    public void testVelocity() {
        assertEquals(0, player.getVX(), 0.001);
        assertEquals(0, player.getVY(), 0.001);
        
        boolean[] keys = new boolean[256];
        keys[68] = true; // Move right
        player.update(keys, SCREEN_WIDTH, SCREEN_HEIGHT, 1.0);
        
        assertTrue(player.getVX() > 0);
    }
    
    @Test
    public void testSpeedUpgrade() {
        Player normalPlayer = new Player(960, 540, 0);
        Player fastPlayer = new Player(960, 540, 3);
        
        boolean[] keys = new boolean[256];
        keys[68] = true; // Move right
        
        normalPlayer.update(keys, SCREEN_WIDTH, SCREEN_HEIGHT, 1.0);
        double normalSpeed = normalPlayer.getX();
        
        fastPlayer.setPosition(960, 540); // Reset position
        fastPlayer.update(keys, SCREEN_WIDTH, SCREEN_HEIGHT, 1.0);
        double fastSpeed = fastPlayer.getX();
        
        // Fast player should move more
        assertTrue(fastSpeed - 960 > normalSpeed - 960);
    }
    
    @Test
    public void testSetPosition() {
        player.setPosition(100, 200);
        assertEquals(100, player.getX(), 0.1);
        assertEquals(200, player.getY(), 0.1);
    }
    
    @Test
    public void testGetSize() {
        int size = player.getSize();
        assertTrue(size > 0);
        assertEquals(20, size); // Expected player size
    }
}
