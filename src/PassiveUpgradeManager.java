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
        addUpgrade("cooldown", "Quick Charge", "Reduce item cooldown by 10% per level", 
                   PassiveUpgrade.UpgradeType.ITEM_COOLDOWN, 120, 5);
        
        addUpgrade("bullet_size", "Small Bullets", "Reduce enemy bullet size by 5% per level", 
                   PassiveUpgrade.UpgradeType.BULLET_SIZE, 200, 5);
        
        addUpgrade("money_score", "Fortune & Glory", "Increase money and score earned by 15% per level", 
                   PassiveUpgrade.UpgradeType.MONEY_AND_SCORE, 150, 5);
        
        addUpgrade("critical", "Critical Strike", "0.5% chance per level to instantly kill boss", 
                   PassiveUpgrade.UpgradeType.CRITICAL_HIT, 250, 5);
        
        addUpgrade("health", "Extra Lives", "Purchase an extra life (Max 3)", 
                   PassiveUpgrade.UpgradeType.MAX_HEALTH, 5000, 3);
    }
    
    private void addUpgrade(String id, String name, String description, 
                           PassiveUpgrade.UpgradeType type, int baseCost, int maxLevel) {
        PassiveUpgrade upgrade = new PassiveUpgrade(id, name, description, type, baseCost, maxLevel);
        upgrades.add(upgrade);
        upgradeMap.put(id, upgrade);
    }
    
    public boolean purchaseUpgrade(String id, GameData gameData) {
        PassiveUpgrade upgrade = upgradeMap.get(id);
        if (upgrade != null && upgrade.canUpgrade(gameData.getTotalMoney())) {
            // Special handling for Extra Lives
            if (id.equals("health")) {
                // Can only buy if not at max lives
                if (gameData.getExtraLives() >= 3) {
                    return false; // Already at max lives
                }
            }
            
            int cost = upgrade.getCost();
            gameData.setTotalMoney(gameData.getTotalMoney() - cost);
            upgrade.upgrade();
            
            // Apply extra life when purchasing
            if (id.equals("health")) {
                gameData.addExtraLife();
            }
            
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
    
    public List<PassiveUpgrade> getAllUpgrades() {
        return upgrades;
    }
    
    public PassiveUpgrade getUpgrade(String id) {
        return upgradeMap.get(id);
    }
}
