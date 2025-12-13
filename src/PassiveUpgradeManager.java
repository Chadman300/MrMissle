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
        addUpgrade("speed", "Swift Movement", "Increase movement speed by 10% per level", 
                   PassiveUpgrade.UpgradeType.MOVEMENT_SPEED, 100, 5);
        
        addUpgrade("health", "Extra Hearts", "Increase maximum health by 1 per level", 
                   PassiveUpgrade.UpgradeType.MAX_HEALTH, 150, 3);
        
        addUpgrade("graze", "Graze Master", "Increase graze detection radius by 15% per level", 
                   PassiveUpgrade.UpgradeType.GRAZE_RADIUS, 80, 5);
        
        addUpgrade("cooldown", "Quick Charge", "Reduce item cooldown by 10% per level", 
                   PassiveUpgrade.UpgradeType.ITEM_COOLDOWN, 120, 5);
        
        addUpgrade("bullet_size", "Small Bullets", "Reduce enemy bullet size by 5% per level", 
                   PassiveUpgrade.UpgradeType.BULLET_SIZE, 200, 5);
        
        addUpgrade("combo_time", "Combo Extender", "Increase combo duration by 20% per level", 
                   PassiveUpgrade.UpgradeType.COMBO_DURATION, 100, 5);
        
        addUpgrade("money", "Fortune Seeker", "Increase money earned by 15% per level", 
                   PassiveUpgrade.UpgradeType.MONEY_GAIN, 150, 5);
        
        addUpgrade("score", "Score Booster", "Increase score gained by 20% per level", 
                   PassiveUpgrade.UpgradeType.SCORE_MULTIPLIER, 100, 5);
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
    
    public List<PassiveUpgrade> getAllUpgrades() {
        return upgrades;
    }
    
    public PassiveUpgrade getUpgrade(String id) {
        return upgradeMap.get(id);
    }
}
