import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PassiveUpgradeManager {
    private List<PassiveUpgrade> upgrades;
    private Map<String, PassiveUpgrade> upgradeMap;
    
    public PassiveUpgradeManager() {
        upgrades = new ArrayList<>();
        upgradeMap = new HashMap<>();
        initializeUpgrades();
    }
    
    private void initializeUpgrades() {
        // Passive upgrades - multi-level with exponential cost scaling
        addUpgrade("speed", "Speed Boost", "+15% movement speed per level", 
                   PassiveUpgrade.UpgradeType.SPEED_BOOST, 500, 10);
        
        addUpgrade("bullet_slow", "Bullet Slow", "-7.5% enemy bullet speed per level (faster bullets slowed more)", 
                   PassiveUpgrade.UpgradeType.BULLET_SLOW, 750, 8);
        
        addUpgrade("lucky_dodge", "Lucky Dodge", "+10% phase-through chance per level", 
                   PassiveUpgrade.UpgradeType.LUCKY_DODGE, 1000, 3);
        
        addUpgrade("cooldown", "Quick Charge", "-12.5% item cooldown per level", 
                   PassiveUpgrade.UpgradeType.ITEM_COOLDOWN, 600, 5);
        
        addUpgrade("bullet_size", "Small Bullets", "-12% enemy bullet size per level", 
                   PassiveUpgrade.UpgradeType.BULLET_SIZE, 800, 8);
        
        addUpgrade("money_score", "Fortune & Glory", "+30% money and score per level", 
                   PassiveUpgrade.UpgradeType.MONEY_AND_SCORE, 700, 5);
        
        addUpgrade("critical", "Critical Strike", "+2% instant-kill chance per level", 
                   PassiveUpgrade.UpgradeType.CRITICAL_HIT, 1500, 3);
        
        addUpgrade("targeting", "Targeting", "Much stronger auto-aim + range per level", 
                   PassiveUpgrade.UpgradeType.TARGETING, 900, 5);
        
        addUpgrade("health", "Extra Missiles", "Purchase an extra missile (Max 3)", 
                   PassiveUpgrade.UpgradeType.MAX_HEALTH, 5000, 3);
    }
    
    // For exponential cost upgrades
    private void addUpgrade(String id, String name, String description, 
                           PassiveUpgrade.UpgradeType type, int baseCost, int maxLevel) {
        PassiveUpgrade upgrade = new PassiveUpgrade(id, name, description, type, baseCost, maxLevel);
        upgrades.add(upgrade);
        upgradeMap.put(id, upgrade);
    }
    
    // For linear cost upgrades
    private void addUpgradeLinear(String id, String name, String description, 
                           PassiveUpgrade.UpgradeType type, int baseCost, int costIncrement, int maxLevel) {
        PassiveUpgrade upgrade = new PassiveUpgrade(id, name, description, type, baseCost, costIncrement, maxLevel);
        upgrades.add(upgrade);
        upgradeMap.put(id, upgrade);
    }
    
    public boolean purchaseUpgrade(String id, GameData gameData) {
        PassiveUpgrade upgrade = upgradeMap.get(id);
        if (upgrade == null) {
            return false;
        }
        
        // Special handling for Extra Missiles - uses gameData.missiles instead of upgrade.currentLevel
        if (id.equals("health")) {
            // Can only buy if not at max missiles AND have enough money
            int currentMissiles = gameData.getMissiles();
            int extraMissiles = currentMissiles - gameData.getBaseMissiles();
            if (extraMissiles >= 3) {
                return false; // Already at max extra missiles (3)
            }
            
            // Calculate cost based on current lives (not upgrade.currentLevel)
            // Cost stays fixed at baseCost for extra missiles
            int cost = upgrade.getBaseCost();
            if (gameData.getTotalMoney() < cost) {
                return false; // Not enough money
            }
            
            gameData.setTotalMoney(gameData.getTotalMoney() - cost);
            gameData.addMissile();
            // Don't call upgrade.upgrade() for health - we track via gameData.missiles only
            return true;
        }
        
        // Normal upgrade handling for non-health upgrades
        if (upgrade.canUpgrade(gameData.getTotalMoney())) {
            int cost = upgrade.getCost();
            gameData.setTotalMoney(gameData.getTotalMoney() - cost);
            upgrade.upgrade();
            return true;
        }
        return false;
    }
    
    public double getMultiplier(PassiveUpgrade.UpgradeType type) {
        for (PassiveUpgrade upgrade : upgrades) {
            if (upgrade.getType() == type) {
                return upgrade.getMultiplier();
            }
        }
        return 1.0;
    }
    
    /**
     * Reset missiles price for new run.
     * The missiles upgrade price increases with each purchase,
     * so we reset the current level back to 0 when a run ends/death occurs.
     */
    public void resetMissilesPrice() {
        PassiveUpgrade healthUpgrade = upgradeMap.get("health");
        if (healthUpgrade != null) {
            healthUpgrade.setCurrentLevel(0);
        }
    }
    
    /**
     * Reset all upgrades to level 0. Used when loading a new save file
     * to prevent upgrades from previous save leaking into the new one.
     */
    public void resetAllUpgrades() {
        for (PassiveUpgrade upgrade : upgrades) {
            upgrade.setCurrentLevel(0);
            upgrade.setActiveLevel(0);
        }
    }
    
    public List<PassiveUpgrade> getAllUpgrades() {
        return upgrades;
    }
    
    public PassiveUpgrade getUpgrade(String id) {
        return upgradeMap.get(id);
    }
    
    public PassiveUpgrade getUpgradeById(String id) {
        return upgradeMap.get(id);
    }
}
