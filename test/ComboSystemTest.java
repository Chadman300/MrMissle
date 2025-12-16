import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ComboSystemTest {
    private ComboSystem comboSystem;
    
    @BeforeEach
    public void setUp() {
        comboSystem = new ComboSystem();
    }
    
    @Test
    public void testInitialState() {
        assertEquals(0, comboSystem.getCombo());
        assertEquals(1.0, comboSystem.getMultiplier(), 0.001);
        assertEquals(0, comboSystem.getMaxCombo());
    }
    
    @Test
    public void testAddCombo() {
        comboSystem.addCombo();
        assertEquals(1, comboSystem.getCombo());
        
        comboSystem.addCombo();
        assertEquals(2, comboSystem.getCombo());
        
        // Check multiplier increases
        assertTrue(comboSystem.getMultiplier() > 1.0);
    }
    
    @Test
    public void testComboMultiplier() {
        // Each combo level adds 5% multiplier
        comboSystem.addCombo();
        assertEquals(1.05, comboSystem.getMultiplier(), 0.001);
        
        for (int i = 1; i < 10; i++) {
            comboSystem.addCombo();
        }
        assertEquals(1.5, comboSystem.getMultiplier(), 0.001); // 10 combos = 50% bonus
    }
    
    @Test
    public void testMaxComboTracking() {
        comboSystem.addCombo();
        comboSystem.addCombo();
        comboSystem.addCombo();
        
        assertEquals(3, comboSystem.getMaxCombo());
        
        comboSystem.resetCombo();
        assertEquals(0, comboSystem.getCombo());
        assertEquals(3, comboSystem.getMaxCombo()); // Max should persist
    }
    
    @Test
    public void testComboTimeout() {
        comboSystem.addCombo();
        comboSystem.addCombo();
        assertEquals(2, comboSystem.getCombo());
        
        // Simulate time passing (3 seconds = 180 frames at 60 FPS)
        for (int i = 0; i < 181; i++) {
            comboSystem.update(1.0, 1.0); // 1 frame delta, no upgrade multiplier
        }
        
        assertEquals(0, comboSystem.getCombo());
    }
    
    @Test
    public void testComboTimeoutReset() {
        comboSystem.addCombo();
        
        // Update halfway to timeout
        for (int i = 0; i < 90; i++) {
            comboSystem.update(1.0, 1.0);
        }
        
        // Add another combo (should reset timer)
        comboSystem.addCombo();
        assertEquals(2, comboSystem.getCombo());
        
        // Update halfway again
        for (int i = 0; i < 90; i++) {
            comboSystem.update(1.0, 1.0);
        }
        
        // Should still have combo
        assertEquals(2, comboSystem.getCombo());
    }
    
    @Test
    public void testResetCombo() {
        comboSystem.addCombo();
        comboSystem.addCombo();
        comboSystem.addCombo();
        
        comboSystem.resetCombo();
        assertEquals(0, comboSystem.getCombo());
        assertEquals(1.0, comboSystem.getMultiplier(), 0.001);
    }
    
    @Test
    public void testTimeoutMultiplier() {
        comboSystem.addCombo();
        
        // Test with upgrade multiplier (e.g., 1.2x duration)
        for (int i = 0; i < 180; i++) {
            comboSystem.update(1.0, 1.2);
        }
        
        // With 1.2x multiplier, should still have combo after base 180 frames
        assertTrue(comboSystem.getCombo() > 0);
    }
}
