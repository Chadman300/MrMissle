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
        // All passive upgrades are single-purchase (max level 1)
        addUpgrade("speed", "Speed Boost", "Increase movement speed by 15%", 
                   PassiveUpgrade.UpgradeType.SPEED_BOOST, 500, 1);
        
        addUpgrade("bullet_slow", "Bullet Slow", "Slow enemy bullets", 
                   PassiveUpgrade.UpgradeType.BULLET_SLOW, 750, 1);
        
        addUpgrade("lucky_dodge", "Lucky Dodge", "Chance to phase through bullets", 
                   PassiveUpgrade.UpgradeType.LUCKY_DODGE, 1000, 1);
        
        addUpgrade("cooldown", "Quick Charge", "Reduce item cooldown", 
                   PassiveUpgrade.UpgradeType.ITEM_COOLDOWN, 600, 1);
        
        addUpgrade("bullet_size", "Small Bullets", "Reduce enemy bullet size", 
                   PassiveUpgrade.UpgradeType.BULLET_SIZE, 800, 1);
        
        addUpgrade("money_score", "Fortune & Glory", "Increase money and score earned", 
                   PassiveUpgrade.UpgradeType.MONEY_AND_SCORE, 700, 1);
        
        addUpgrade("critical", "Critical Strike", "Chance to instantly kill boss on hit", 
                   PassiveUpgrade.UpgradeType.CRITICAL_HIT, 1500, 1);
        
        addUpgrade("targeting", "Targeting", "Soft auto-aim toward boss when nearby", 
                   PassiveUpgrade.UpgradeType.TARGETING, 900, 1);
        
        addUpgrade("health", "Extra Lives", "Purchase an extra life (Max 3)", 
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
        
        // Special handling for Extra Lives - uses gameData.extraLives instead of upgrade.currentLevel
        if (id.equals("health")) {
            // Can only buy if not at max lives AND have enough money
            int currentLives = gameData.getExtraLives();
            if (currentLives >= 3) {
                return false; // Already at max lives
            }
            
            // Calculate cost based on current lives (not upgrade.currentLevel)
            // Cost stays fixed at baseCost for extra lives
            int cost = upgrade.getBaseCost();
            if (gameData.getTotalMoney() < cost) {
                return false; // Not enough money
            }
            
            gameData.setTotalMoney(gameData.getTotalMoney() - cost);
            gameData.addExtraLife();
            // Don't call upgrade.upgrade() for health - we track via gameData.extraLives only
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
     * Reset extra lives price for new run.
     * The extra lives upgrade price increases with each purchase,
     * so we reset the current level back to 0 when a run ends/death occurs.
     */
    public void resetExtraLivesPrice() {
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
