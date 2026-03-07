public class ShopManager {
    private GameData gameData;
    private PassiveUpgradeManager passiveUpgradeManager;
    private int selectedShopItem;
    
    // Maps display index (after "Continue") to original upgrade index in PassiveUpgradeManager
    // Maxed items are pushed to the bottom of this list
    private int[] sortedUpgradeOrder;
    
    public ShopManager(GameData gameData) {
        this.gameData = gameData;
        this.selectedShopItem = 0;
    }
    
    public void setPassiveUpgradeManager(PassiveUpgradeManager manager) {
        this.passiveUpgradeManager = manager;
        rebuildSortedOrder();
    }
    
    /**
     * Rebuild the sorted upgrade order so maxed items appear at the bottom.
     * Locked items (unlockLevel > bestRunLevel) are completely excluded.
     * Call this after any purchase or state change.
     */
    public void rebuildSortedOrder() {
        if (passiveUpgradeManager == null) {
            sortedUpgradeOrder = new int[0];
            return;
        }
        java.util.List<PassiveUpgrade> upgrades = passiveUpgradeManager.getAllUpgrades();
        int count = upgrades.size();
        int bestLevel = gameData.getBestRunLevel();
        
        // Build sorted order: non-maxed first, then maxed, but Flares and
        // Extra Missiles always appear in that relative order at the end of
        // whichever group (non-maxed or maxed) they belong to.
        java.util.List<Integer> nonMaxedRegular = new java.util.ArrayList<>();
        java.util.List<Integer> maxedRegular = new java.util.ArrayList<>();
        int flaresIdx = -1, healthIdx = -1;
        boolean flaresMaxed = false, healthMaxed = false;
        
        for (int i = 0; i < count; i++) {
            PassiveUpgrade upgrade = upgrades.get(i);
            if (upgrade.getUnlockLevel() > bestLevel) continue;
            
            boolean isMax;
            if (upgrade.getId().equals("health")) {
                int extraMissiles = Math.max(0, gameData.getMissiles() - gameData.getBaseMissiles());
                isMax = extraMissiles >= upgrade.getMaxLevel();
                healthIdx = i;
                healthMaxed = isMax;
                continue;
            } else if (upgrade.getId().equals("flares")) {
                isMax = upgrade.isMaxed();
                flaresIdx = i;
                flaresMaxed = isMax;
                continue;
            } else {
                isMax = upgrade.isMaxed();
            }
            
            if (isMax) {
                maxedRegular.add(i);
            } else {
                nonMaxedRegular.add(i);
            }
        }
        
        java.util.List<Integer> finalList = new java.util.ArrayList<>();
        // 1. Non-maxed regular upgrades
        for (int i : nonMaxedRegular) finalList.add(i);
        // 2. Non-maxed flares, then non-maxed health
        if (flaresIdx >= 0 && !flaresMaxed) finalList.add(flaresIdx);
        if (healthIdx >= 0 && !healthMaxed) finalList.add(healthIdx);
        // 3. Maxed regular upgrades
        for (int i : maxedRegular) finalList.add(i);
        // 4. Maxed flares, then maxed health
        if (flaresIdx >= 0 && flaresMaxed) finalList.add(flaresIdx);
        if (healthIdx >= 0 && healthMaxed) finalList.add(healthIdx);
        
        sortedUpgradeOrder = new int[finalList.size()];
        for (int i = 0; i < finalList.size(); i++) sortedUpgradeOrder[i] = finalList.get(i);
    }
    
    /**
     * Get the original upgrade index for a given display item index.
     * Display index 0 = Continue, display index 1+ maps through sortedUpgradeOrder.
     */
    private int getUpgradeIndex(int displayIndex) {
        int sortedIdx = displayIndex - 1;
        if (sortedIdx >= 0 && sortedIdx < sortedUpgradeOrder.length) {
            return sortedUpgradeOrder[sortedIdx];
        }
        return -1;
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
        
        if (passiveUpgradeManager != null) {
            int upgradeIndex = getUpgradeIndex(itemIndex);
            if (upgradeIndex >= 0 && upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);
                // Extra Missiles has fixed cost (baseCost) - doesn't scale with level
                if (upgrade.getId().equals("health")) {
                    return upgrade.getBaseCost();
                }
                return upgrade.getCost();
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
        
        if (passiveUpgradeManager != null) {
            int upgradeIndex = getUpgradeIndex(itemIndex);
            if (upgradeIndex >= 0 && upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);
                boolean result = passiveUpgradeManager.purchaseUpgrade(upgrade.getId(), gameData);
                if (result) {
                    rebuildSortedOrder(); // Re-sort after purchase (item may now be maxed)
                }
                return result;
            }
        }
        return false;
    }
    
    public int getTotalShopItems() {
        // 1 Continue + unlocked upgrades only (locked items are excluded from sortedUpgradeOrder)
        return 1 + (sortedUpgradeOrder != null ? sortedUpgradeOrder.length : 0);
    }
    
    public String[] getShopItems() {
        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("Continue - Return to level select");
        
        // Add upgrades in sorted order (maxed items at bottom)
        if (passiveUpgradeManager != null) {
            java.util.List<PassiveUpgrade> upgrades = passiveUpgradeManager.getAllUpgrades();
            for (int si = 0; si < sortedUpgradeOrder.length; si++) {
                int upgradeIdx = sortedUpgradeOrder[si];
                PassiveUpgrade upgrade = upgrades.get(upgradeIdx);
                String maxInfo;
                
                // Special handling for Extra Missiles (last upgrade)
                if (upgrade.getId().equals("health")) {
                    int extraMissiles = Math.max(0, gameData.getMissiles() - gameData.getBaseMissiles());
                    int maxExtra = upgrade.getMaxLevel();
                    maxInfo = (extraMissiles >= maxExtra) ? " (MAXED)" : " (" + extraMissiles + "/" + maxExtra + " extra missiles)";
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
        
        if (passiveUpgradeManager != null) {
            int upgradeIndex = getUpgradeIndex(itemIndex);
            if (upgradeIndex >= 0 && upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {
                PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);
                // Special handling for Extra Missiles - maxed at 3 extra missiles
                if (upgrade.getId().equals("health")) {
                    int extraMissiles = Math.max(0, gameData.getMissiles() - gameData.getBaseMissiles());
                    return extraMissiles >= upgrade.getMaxLevel();
                }
                return upgrade.isMaxed();
            }
        }
        return false;
    }
    
    /**
     * Get the original upgrade index for a display item index.
     * Used by Renderer to look up the correct PassiveUpgrade for icons/progress bars.
     */
    public int getOriginalUpgradeIndex(int displayItemIndex) {
        return getUpgradeIndex(displayItemIndex);
    }
}
