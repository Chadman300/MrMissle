public class ShopManager {
    private GameData gameData;
    private int selectedShopItem;
    
    public ShopManager(GameData gameData) {
        this.gameData = gameData;
        this.selectedShopItem = 0;
    }
    
    public int getSelectedShopItem() {
        return selectedShopItem;
    }
    
    public void setSelectedShopItem(int item) {
        this.selectedShopItem = Math.max(0, Math.min(6, item));
    }
    
    public void selectPrevious() {
        selectedShopItem = Math.max(0, selectedShopItem - 1);
    }
    
    public void selectNext() {
        selectedShopItem = Math.min(6, selectedShopItem + 1);
    }
    
    public int getItemCost(int itemIndex) {
        switch (itemIndex) {
            case 0: return 0; // Free (just continue)
            case 1: return 75 + (gameData.getSpeedUpgradeLevel() * 35);
            case 2: return 100 + (gameData.getBulletSlowUpgradeLevel() * 50);
            case 3: return 125 + (gameData.getLuckyDodgeUpgradeLevel() * 65);
            case 4: return 150 + (gameData.getAttackWindowUpgradeLevel() * 75);
            case 5: return 200; // Score multiplier
            case 6: return 5000 + (gameData.getExtraLives() * 3000); // Extra Life - very expensive
            default: return 0;
        }
    }
    
    public boolean purchaseItem(int itemIndex) {
        int cost = getItemCost(itemIndex);
        
        if (itemIndex == 0) {
            return true; // Continue button - free
        }
        
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
                case 5:
                    // Score multiplier (not implemented yet)
                    break;
                case 6:
                    gameData.addExtraLife();
                    break;
            }
            return true;
        }
        return false;
    }
    
    public String[] getShopItems() {
        return new String[] {
            "Continue - Return to level select",
            "Speed Boost - Increases movement speed by 15%",
            "Bullet Slow - Slows enemy bullets by 0.1%",
            "Lucky Dodge - Small chance to phase through bullets",
            "Attack Window+ - Adds 1 second to boss vulnerability window",
            "Score Multiplier - Increases score gain (Coming Soon)",
            "Extra Life - Resurrect once when you die"
        };
    }
}
