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
    
    // ====== Final Boss (Level 28) Tests ======
    
    @Test
    public void testFinalBossStats() {
        Boss finalBoss = new Boss(400, 100, 28);
        assertTrue(finalBoss.isFinalBoss());
        assertEquals(10, finalBoss.getMaxHealth());
        assertEquals(10, finalBoss.getCurrentHealth());
        assertEquals(250, finalBoss.getSize());
        assertFalse(finalBoss.isMegaBoss()); // Level 28 is not a mega boss pattern
    }
    
    @Test
    public void testFinalBossPhaseTransition() {
        Boss finalBoss = new Boss(400, 100, 28);
        assertEquals(0, finalBoss.getCurrentPhase());
        
        // Damage to 5 HP — still phase 0
        for (int i = 0; i < 5; i++) {
            finalBoss.takeDamage();
        }
        assertEquals(5, finalBoss.getCurrentHealth());
        assertEquals(0, finalBoss.getCurrentPhase());
        
        // Damage to 4 HP — enters phase 1
        finalBoss.takeDamage();
        assertEquals(4, finalBoss.getCurrentHealth());
        assertEquals(1, finalBoss.getCurrentPhase());
    }
    
    @Test
    public void testFinalBossDamageState() {
        Boss finalBoss = new Boss(400, 100, 28);
        
        // Full health: damage state 0
        assertEquals(0, finalBoss.getDamageState());
        
        // 9 HP: state 1
        finalBoss.takeDamage();
        assertEquals(1, finalBoss.getDamageState());
        
        // 7 HP: state 2
        finalBoss.takeDamage(); // 8 HP
        finalBoss.takeDamage(); // 7 HP
        assertEquals(2, finalBoss.getDamageState());
        
        // 5 HP: state 3
        finalBoss.takeDamage(); // 6 HP
        finalBoss.takeDamage(); // 5 HP
        assertEquals(3, finalBoss.getDamageState());
        
        // 3 HP: state 4
        finalBoss.takeDamage(); // 4 HP
        finalBoss.takeDamage(); // 3 HP
        assertEquals(4, finalBoss.getDamageState());
        
        // 2 HP: still state 4
        finalBoss.takeDamage(); // 2 HP
        assertEquals(4, finalBoss.getDamageState());
        
        // 1 HP: state 5
        finalBoss.takeDamage(); // 1 HP
        assertEquals(5, finalBoss.getDamageState());
    }
    
    @Test
    public void testFinalBossDeath() {
        Boss finalBoss = new Boss(400, 100, 28);
        
        for (int i = 0; i < 10; i++) {
            finalBoss.takeDamage();
        }
        
        assertEquals(0, finalBoss.getCurrentHealth());
        assertTrue(finalBoss.isDead());
    }
    
    @Test
    public void testNonFinalBossIsNotFinal() {
        assertFalse(boss.isFinalBoss()); // Level 1
        Boss midBoss = new Boss(400, 100, 14);
        assertFalse(midBoss.isFinalBoss());
    }
}
