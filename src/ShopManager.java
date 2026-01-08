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
        if (itemIndex == 0) {
            return 0; // Free (just continue)
        }
        
        // All upgrades are now in PassiveUpgradeManager (index 1 = upgrade 0, etc.)
        if (itemIndex >= 1 && passiveUpgradeManager != null) {
            int upgradeIndex = itemIndex - 1;
            if (upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                return passiveUpgradeManager.getAllUpgrades().get(upgradeIndex).getCost();
            }
        }
        return 0;
    }
    
    public boolean purchaseItem(int itemIndex) {
        if (itemIndex == 0) {
            return true; // Continue button - free
        }
        
        // Check if upgrade is maxed before allowing purchase
        if (isUpgradeMaxed(itemIndex)) {
            return false; // Can't purchase if already maxed
        }
        
        // All upgrades are now handled by PassiveUpgradeManager
        if (itemIndex >= 1 && passiveUpgradeManager != null) {
            int upgradeIndex = itemIndex - 1;
            if (upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);
                return passiveUpgradeManager.purchaseUpgrade(upgrade.getId(), gameData);
            }
        }
        return false;
    }
    
    public int getTotalShopItems() {
        // 1 Continue + all upgrades from PassiveUpgradeManager
        int upgradeCount = (passiveUpgradeManager != null) ? passiveUpgradeManager.getAllUpgrades().size() : 0;
        return 1 + upgradeCount;
    }
    
    public String[] getShopItems() {
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("Continue - Return to level select");
        
        // Add all upgrades from PassiveUpgradeManager
        if (passiveUpgradeManager != null) {
            java.util.List<PassiveUpgrade> upgrades = passiveUpgradeManager.getAllUpgrades();
            for (int i = 0; i < upgrades.size(); i++) {
                PassiveUpgrade upgrade = upgrades.get(i);
                String maxInfo;
                
                // Special handling for Extra Lives (last upgrade)
                if (upgrade.getId().equals("health")) {
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
        if (itemIndex == 0) {
            return false; // Continue button is never "maxed"
        }
        
        // All upgrades are now in PassiveUpgradeManager
        if (itemIndex >= 1 && passiveUpgradeManager != null) {
            int upgradeIndex = itemIndex - 1;
            if (upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);
                // Special handling for Extra Lives - maxed at 3 lives
                if (upgrade.getId().equals("health")) {
                    return gameData.getExtraLives() >= 3;
                }
                return upgrade.isMaxed();
            }
        }
        return false;
    }
}
