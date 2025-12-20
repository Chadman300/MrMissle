public class ShopManager {
    private GameData gameData;
    private PassiveUpgradeManager passiveUpgradeManager;
    private int selectedShopItem;
    
    public ShopManager(GameData gameData) {
        this.gameData = gameData;
        this.selectedShopItem = 0;
    }
    
    public void setPassiveUpgradeManager(PassiveUpgradeManager manager) {
        this.passiveUpgradeManager = manager;
    }
    
    public int getSelectedShopItem() {
        return selectedShopItem;
    }
    
    public void setSelectedShopItem(int item) {
        int maxIndex = getTotalShopItems() - 1;
        this.selectedShopItem = Math.max(0, Math.min(maxIndex, item));
    }
    
    public void selectPrevious() {
        selectedShopItem = Math.max(0, selectedShopItem - 1);
    }
    
    public void selectNext() {
        int maxIndex = getTotalShopItems() - 1;
        selectedShopItem = Math.min(maxIndex, selectedShopItem + 1);
    }
    
    public int getItemCost(int itemIndex) {
        switch (itemIndex) {
            case 0: return 0; // Free (just continue)
            case 1: return 75 + (gameData.getSpeedUpgradeLevel() * 35);
            case 2: return 100 + (gameData.getBulletSlowUpgradeLevel() * 50);
            case 3: return 125 + (gameData.getLuckyDodgeUpgradeLevel() * 100);
            case 4: return 150 + (gameData.getAttackWindowUpgradeLevel() * 75);
            default: 
                // Passive upgrades (5-14)
                if (itemIndex >= 5 && itemIndex <= 14 && passiveUpgradeManager != null) {
                    int passiveIndex = itemIndex - 5;
                    if (passiveIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                        return passiveUpgradeManager.getAllUpgrades().get(passiveIndex).getCost();
                    }
                }
                return 0;
        }
    }
    
    public boolean purchaseItem(int itemIndex) {
        int cost = getItemCost(itemIndex);
        
        if (itemIndex == 0) {
            return true; // Continue button - free
        }
        
        // Check if upgrade is maxed before allowing purchase
        if (isUpgradeMaxed(itemIndex)) {
            return false; // Can't purchase if already maxed
        }
        
        // Passive upgrades (5-14) are handled by PassiveUpgradeManager
        if (itemIndex >= 5 && itemIndex <= 14 && passiveUpgradeManager != null) {
            int passiveIndex = itemIndex - 5;
            if (passiveIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(passiveIndex);
                return passiveUpgradeManager.purchaseUpgrade(upgrade.getId(), gameData);
            }
            return false;
        }
        
        // Regular upgrades (1-4) are handled here
        if (gameData.getTotalMoney() >= cost) {
            gameData.addTotalMoney(-cost);
            
            switch (itemIndex) {
                case 1:
                    gameData.incrementSpeedUpgrade();
                    gameData.setActiveSpeedLevel(gameData.getSpeedUpgradeLevel()); // Auto-select
                    break;
                case 2:
                    gameData.incrementBulletSlowUpgrade();
                    gameData.setActiveBulletSlowLevel(gameData.getBulletSlowUpgradeLevel()); // Auto-select
                    break;
                case 3:
                    gameData.incrementLuckyDodgeUpgrade();
                    gameData.setActiveLuckyDodgeLevel(gameData.getLuckyDodgeUpgradeLevel()); // Auto-select
                    break;
                case 4:
                    gameData.incrementAttackWindowUpgrade();
                    gameData.setActiveAttackWindowLevel(gameData.getAttackWindowUpgradeLevel()); // Auto-select
                    break;
            }
            return true;
        }
        return false;
    }
    
    public int getTotalShopItems() {
        // 1 Continue + 4 shop upgrades + passive upgrades
        int passiveCount = (passiveUpgradeManager != null) ? passiveUpgradeManager.getAllUpgrades().size() : 0;
        return 5 + passiveCount;
    }
    
    public String[] getShopItems() {
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("Continue - Return to level select");
        items.add("Speed Boost - Increases movement speed by 15% (Max 10)");
        items.add("Bullet Slow - Slows enemy bullets by 0.1% (Max 50)");
        items.add("Lucky Dodge - 3% chance per level to phase through bullets (Max 35%)");
        items.add("Attack Window+ - Adds 0.25 seconds to boss vulnerability window (Max 10)");
        
        // Add passive upgrades
        if (passiveUpgradeManager != null) {
            java.util.List<PassiveUpgrade> upgrades = passiveUpgradeManager.getAllUpgrades();
            for (int i = 0; i < upgrades.size(); i++) {
                PassiveUpgrade upgrade = upgrades.get(i);
                String maxInfo;
                
                // Special handling for Extra Lives (last upgrade)
                if (i == upgrades.size() - 1) {
                    int currentLives = gameData.getExtraLives();
                    maxInfo = (currentLives >= 3) ? " (MAXED)" : " (" + currentLives + "/3 lives)";
                } else {
                    maxInfo = upgrade.isMaxed() ? " (MAXED)" : " (" + upgrade.getCurrentLevel() + "/" + upgrade.getMaxLevel() + ")";
                }
                
                items.add(upgrade.getName() + " - " + upgrade.getDescription() + maxInfo);
            }
        }
        
        return items.toArray(new String[0]);
    }
    
    public boolean isUpgradeMaxed(int itemIndex) {
        switch (itemIndex) {
            case 1: return gameData.getSpeedUpgradeLevel() >= GameData.MAX_SPEED_LEVEL;
            case 2: return gameData.getBulletSlowUpgradeLevel() >= GameData.MAX_BULLET_SLOW_LEVEL;
            case 3: return gameData.getLuckyDodgeUpgradeLevel() >= GameData.MAX_LUCKY_DODGE_LEVEL;
            case 4: return gameData.getAttackWindowUpgradeLevel() >= GameData.MAX_ATTACK_WINDOW_LEVEL;
            default:
                // Check passive upgrades (5-14)
                if (itemIndex >= 5 && itemIndex <= 14 && passiveUpgradeManager != null) {
                    int passiveIndex = itemIndex - 5;
                    if (passiveIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                        PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(passiveIndex);
                        // Special handling for Extra Lives (last upgrade) - maxed at 3 lives
                        if (passiveIndex == passiveUpgradeManager.getAllUpgrades().size() - 1) {
                            return gameData.getExtraLives() >= 3;
                        }
                        return upgrade.isMaxed();
                    }
                }
                return false;
        }
    }
}
