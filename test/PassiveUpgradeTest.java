import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PassiveUpgradeTest {
    
    @Test
    public void testUpgradeCreation() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.MOVEMENT_SPEED,
            "Swift Movement",
            "Increase movement speed",
            100
        );
        
        assertEquals("Swift Movement", upgrade.getName());
        assertEquals(0, upgrade.getLevel());
        assertEquals(100, upgrade.getCost());
    }
    
    @Test
    public void testUpgradeLeveling() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.MOVEMENT_SPEED,
            "Swift Movement",
            "Increase movement speed",
            100
        );
        
        upgrade.upgrade();
        assertEquals(1, upgrade.getLevel());
        assertEquals(110, upgrade.getCost()); // Cost increases by 10%
        
        upgrade.upgrade();
        assertEquals(2, upgrade.getLevel());
    }
    
    @Test
    public void testMovementSpeedMultiplier() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.MOVEMENT_SPEED,
            "Swift Movement",
            "Increase movement speed",
            100
        );
        
        assertEquals(1.0, upgrade.getMultiplier(), 0.001);
        
        upgrade.upgrade();
        assertEquals(1.1, upgrade.getMultiplier(), 0.001); // +10% per level
        
        upgrade.upgrade();
        assertEquals(1.2, upgrade.getMultiplier(), 0.001);
    }
    
    @Test
    public void testMaxHealthMultiplier() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.MAX_HEALTH,
            "Fortified",
            "Increase max health",
            150
        );
        
        assertEquals(0.0, upgrade.getMultiplier(), 0.001); // Base = 0 (additive)
        
        upgrade.upgrade();
        assertEquals(1.0, upgrade.getMultiplier(), 0.001); // +1 per level
        
        upgrade.upgrade();
        assertEquals(2.0, upgrade.getMultiplier(), 0.001);
    }
    
    @Test
    public void testGrazeRadiusMultiplier() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.GRAZE_RADIUS,
            "Extended Sense",
            "Increase graze detection radius",
            120
        );
        
        assertEquals(1.0, upgrade.getMultiplier(), 0.001);
        
        upgrade.upgrade();
        assertEquals(1.15, upgrade.getMultiplier(), 0.001); // +15% per level
    }
    
    @Test
    public void testBulletSizeMultiplier() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.BULLET_SIZE,
            "Smaller Target",
            "Reduce player hitbox size",
            200
        );
        
        assertEquals(1.0, upgrade.getMultiplier(), 0.001);
        
        upgrade.upgrade();
        assertEquals(0.95, upgrade.getMultiplier(), 0.001); // -5% per level (smaller is better)
        
        upgrade.upgrade();
        assertEquals(0.9, upgrade.getMultiplier(), 0.001);
    }
    
    @Test
    public void testCanUpgrade() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.MOVEMENT_SPEED,
            "Swift Movement",
            "Increase movement speed",
            100
        );
        
        assertTrue(upgrade.canUpgrade(100));
        assertTrue(upgrade.canUpgrade(150));
        assertFalse(upgrade.canUpgrade(99));
        assertFalse(upgrade.canUpgrade(0));
    }
    
    @Test
    public void testCostScaling() {
        PassiveUpgrade upgrade = new PassiveUpgrade(
            PassiveUpgrade.UpgradeType.SCORE_MULTIPLIER,
            "Score Boost",
            "Increase score gains",
            100
        );
        
        assertEquals(100, upgrade.getCost());
        
        upgrade.upgrade();
        assertEquals(110, upgrade.getCost()); // +10%
        
        upgrade.upgrade();
        assertEquals(121, upgrade.getCost()); // +10% again (110 * 1.1 = 121)
    }
}
