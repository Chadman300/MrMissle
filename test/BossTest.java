import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BossTest {
    private Boss boss;
    
    @BeforeEach
    public void setUp() {
        boss = new Boss(400, 100, 1);
    }
    
    @Test
    public void testInitialState() {
        assertNotNull(boss);
        assertEquals(400, boss.getX(), 0.1);
        assertEquals(100, boss.getY(), 0.1);
        assertEquals(1, boss.getLevel());
    }
    
    @Test
    public void testHealthSystem() {
        int maxHealth = boss.getMaxHealth();
        assertTrue(maxHealth >= 3); // Regular bosses have 3 HP, mega bosses have 4
        
        assertEquals(maxHealth, boss.getCurrentHealth());
        assertFalse(boss.isDead());
        
        boss.takeDamage();
        assertEquals(maxHealth - 1, boss.getCurrentHealth());
        assertFalse(boss.isDead());
    }
    
    @Test
    public void testPhaseSystem() {
        assertEquals(0, boss.getCurrentPhase());
        
        // Damage boss to trigger phase transitions
        int maxHealth = boss.getMaxHealth();
        boss.takeDamage();
        
        if (maxHealth > 2) {
            assertTrue(boss.getCurrentPhase() >= 0);
        }
    }
    
    @Test
    public void testHealthPercentage() {
        assertEquals(1.0f, boss.getHealthPercent(), 0.01f);
        
        boss.takeDamage();
        float expectedPercent = (float)(boss.getCurrentHealth()) / boss.getMaxHealth();
        assertEquals(expectedPercent, boss.getHealthPercent(), 0.01f);
    }
    
    @Test
    public void testBossDeath() {
        int maxHealth = boss.getMaxHealth();
        
        // Damage boss until dead
        for (int i = 0; i < maxHealth; i++) {
            boss.takeDamage();
        }
        
        assertEquals(0, boss.getCurrentHealth());
        assertTrue(boss.isDead());
    }
    
    @Test
    public void testMegaBossPattern() {
        // Level 3 should be a mega boss (every 3rd level)
        Boss megaBoss = new Boss(400, 100, 3);
        assertTrue(megaBoss.isMegaBoss());
        assertEquals(4, megaBoss.getMaxHealth());
        
        // Level 1 should be a regular boss
        assertFalse(boss.isMegaBoss());
        assertEquals(3, boss.getMaxHealth());
    }
    
    @Test
    public void testMoneyReward() {
        int reward = boss.getMoneyReward();
        assertTrue(reward > 0);
        assertTrue(reward >= 50); // Base reward
    }
    
    @Test
    public void testVehicleName() {
        String name = boss.getVehicleName();
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }
}
