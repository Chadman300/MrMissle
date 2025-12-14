public class GameData {
    // Game constants
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 800;
    
    // Score and money
    private int score;
    private int totalMoney;
    private int runMoney;
    private int survivalTime;
    
    // Level progression
    private int currentLevel;
    private int maxUnlockedLevel;
    private boolean[] defeatedBosses;
    
    // Upgrades (persistent)
    private int speedUpgradeLevel;
    private int bulletSlowUpgradeLevel;
    private int luckyDodgeUpgradeLevel;
    private int attackWindowUpgradeLevel;
    
    // Active upgrades (allocated from purchased upgrades)
    private int activeSpeedLevel;
    private int activeBulletSlowLevel;
    private int activeLuckyDodgeLevel;
    private int activeAttackWindowLevel;
    
    // Active Items system
    private java.util.List<ActiveItem.ItemType> unlockedItems;
    private ActiveItem equippedItem;
    private int equippedItemIndex; // Index in unlocked items list
    
    // Risk Contracts system (permanent unlock)
    private boolean contractsUnlocked;
    
    // Roguelike run tracking
    private int runHighestLevel;      // Highest level reached this run
    private int totalRunsCompleted;   // Total number of runs (deaths)
    private int bestRunLevel;         // Best level ever reached in a single run
    private int totalBossesDefeated;  // Lifetime boss kills
    
    // Extra lives (resurrection mechanic)
    private int extraLives;           // Number of extra lives available
    
    // Level select navigation (separate from currentLevel for viewing)
    private int selectedLevelView;    // Which level is currently selected for viewing in map
    private int[] levelCompletionTimes; // Best completion time for each level (in frames)
    
    // Audio settings
    private float masterVolume = 0.7f;
    private float sfxVolume = 0.8f;
    private float uiVolume = 0.8f;
    private float musicVolume = 0.5f;
    private boolean soundEnabled = true;

    public GameData() {
        score = 0;
        totalMoney = 0;
        runMoney = 0;
        survivalTime = 0;
        defeatedBosses = new boolean[100];
        currentLevel = 1;
        maxUnlockedLevel = 1;
        speedUpgradeLevel = 0;
        bulletSlowUpgradeLevel = 0;
        luckyDodgeUpgradeLevel = 0;
        attackWindowUpgradeLevel = 0;
        activeSpeedLevel = 0;
        activeBulletSlowLevel = 0;
        activeLuckyDodgeLevel = 0;
        activeAttackWindowLevel = 0;
        
        // Initialize active items
        unlockedItems = new java.util.ArrayList<>();
        equippedItem = null;
        equippedItemIndex = -1;
        
        // Risk contracts start locked
        contractsUnlocked = false;
        
        // Roguelike run tracking
        runHighestLevel = 1;
        totalRunsCompleted = 0;
        bestRunLevel = 1;
        totalBossesDefeated = 0;
        
        // Extra lives
        extraLives = 0;
        
        // Level select navigation
        selectedLevelView = 1;
        levelCompletionTimes = new int[100];
    }
    
    // Getters and setters
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public void addScore(int points) { this.score += points; }
    
    public int getTotalMoney() { return totalMoney; }
    public void setTotalMoney(int totalMoney) { this.totalMoney = totalMoney; }
    public void addTotalMoney(int amount) { this.totalMoney += amount; }
    
    public int getRunMoney() { return runMoney; }
    public void setRunMoney(int runMoney) { this.runMoney = runMoney; }
    public void addRunMoney(int amount) { this.runMoney += amount; }
    
    public int getSurvivalTime() { return survivalTime; }
    public void setSurvivalTime(int survivalTime) { this.survivalTime = survivalTime; }
    public void incrementSurvivalTime() { this.survivalTime++; }
    
    public int getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int currentLevel) { this.currentLevel = currentLevel; }
    
    public int getMaxUnlockedLevel() { return maxUnlockedLevel; }
    public void setMaxUnlockedLevel(int maxUnlockedLevel) { this.maxUnlockedLevel = maxUnlockedLevel; }
    
    public boolean[] getDefeatedBosses() { return defeatedBosses; }
    public void setBossDefeated(int level, boolean defeated) { 
        if (level >= 0 && level < defeatedBosses.length) {
            defeatedBosses[level] = defeated;
        }
    }
    
    public int getSpeedUpgradeLevel() { return speedUpgradeLevel; }
    public void setSpeedUpgradeLevel(int level) { this.speedUpgradeLevel = level; }
    public void incrementSpeedUpgrade() { this.speedUpgradeLevel++; }
    
    public int getBulletSlowUpgradeLevel() { return bulletSlowUpgradeLevel; }
    public void setBulletSlowUpgradeLevel(int level) { this.bulletSlowUpgradeLevel = level; }
    public void incrementBulletSlowUpgrade() { this.bulletSlowUpgradeLevel++; }
    
    public int getLuckyDodgeUpgradeLevel() { return luckyDodgeUpgradeLevel; }
    public void setLuckyDodgeUpgradeLevel(int level) { this.luckyDodgeUpgradeLevel = level; }
    public void incrementLuckyDodgeUpgrade() { this.luckyDodgeUpgradeLevel++; }
    
    public int getAttackWindowUpgradeLevel() { return attackWindowUpgradeLevel; }
    public void setAttackWindowUpgradeLevel(int level) { this.attackWindowUpgradeLevel = level; }
    public void incrementAttackWindowUpgrade() { this.attackWindowUpgradeLevel++; }
    
    public int getActiveSpeedLevel() { return activeSpeedLevel; }
    public void setActiveSpeedLevel(int level) { this.activeSpeedLevel = Math.max(0, Math.min(speedUpgradeLevel, level)); }
    
    public int getActiveBulletSlowLevel() { return activeBulletSlowLevel; }
    public void setActiveBulletSlowLevel(int level) { this.activeBulletSlowLevel = Math.max(0, Math.min(bulletSlowUpgradeLevel, level)); }
    
    public int getActiveLuckyDodgeLevel() { return activeLuckyDodgeLevel; }
    public void setActiveLuckyDodgeLevel(int level) { this.activeLuckyDodgeLevel = Math.max(0, Math.min(luckyDodgeUpgradeLevel, level)); }
    
    public int getActiveAttackWindowLevel() { return activeAttackWindowLevel; }
    public void setActiveAttackWindowLevel(int level) { this.activeAttackWindowLevel = Math.max(0, Math.min(attackWindowUpgradeLevel, level)); }
    
    public void adjustUpgrade(int upgradeIndex, int delta) {
        switch (upgradeIndex) {
            case 0: // Speed
                activeSpeedLevel = Math.max(0, Math.min(speedUpgradeLevel, activeSpeedLevel + delta));
                break;
            case 1: // Bullet Slow
                activeBulletSlowLevel = Math.max(0, Math.min(bulletSlowUpgradeLevel, activeBulletSlowLevel + delta));
                break;
            case 2: // Lucky Dodge
                activeLuckyDodgeLevel = Math.max(0, Math.min(luckyDodgeUpgradeLevel, activeLuckyDodgeLevel + delta));
                break;
            case 3: // Attack Window
                activeAttackWindowLevel = Math.max(0, Math.min(attackWindowUpgradeLevel, activeAttackWindowLevel + delta));
                break;
        }
    }
    
    // Cheat/Debug methods
    public void unlockAllLevels() {
        maxUnlockedLevel = 20;
        for (int i = 0; i < defeatedBosses.length; i++) {
            defeatedBosses[i] = true;
        }
    }
    
    public void giveCheatMoney(int amount) {
        totalMoney += amount;
    }
    
    public void maxAllUpgrades() {
        speedUpgradeLevel = 10;
        bulletSlowUpgradeLevel = 10;
        luckyDodgeUpgradeLevel = 10;
        attackWindowUpgradeLevel = 10;
    }
    
    // Active Items methods
    public void unlockNextItem() {
        ActiveItem.ItemType[] allItems = ActiveItem.ItemType.values();
        if (unlockedItems.size() < allItems.length) {
            unlockedItems.add(allItems[unlockedItems.size()]);
        }
    }
    
    public void unlockAllItems() {
        unlockedItems.clear();
        for (ActiveItem.ItemType type : ActiveItem.ItemType.values()) {
            unlockedItems.add(type);
        }
        if (!unlockedItems.isEmpty() && equippedItem == null) {
            equipItem(0);
        }
    }
    
    public void equipItem(int index) {
        if (index >= 0 && index < unlockedItems.size()) {
            equippedItemIndex = index;
            equippedItem = new ActiveItem(unlockedItems.get(index));
        }
    }
    
    public void equipNextItem() {
        if (!unlockedItems.isEmpty()) {
            equippedItemIndex = (equippedItemIndex + 1) % unlockedItems.size();
            equippedItem = new ActiveItem(unlockedItems.get(equippedItemIndex));
        }
    }
    
    public void equipPreviousItem() {
        if (!unlockedItems.isEmpty()) {
            equippedItemIndex--;
            if (equippedItemIndex < 0) equippedItemIndex = unlockedItems.size() - 1;
            equippedItem = new ActiveItem(unlockedItems.get(equippedItemIndex));
        }
    }
    
    public ActiveItem getEquippedItem() { return equippedItem; }
    public java.util.List<ActiveItem.ItemType> getUnlockedItems() { return unlockedItems; }
    public int getEquippedItemIndex() { return equippedItemIndex; }
    public boolean hasActiveItems() { return !unlockedItems.isEmpty(); }
    
    // Risk Contracts methods
    public boolean areContractsUnlocked() { return contractsUnlocked; }
    public void unlockContracts() { contractsUnlocked = true; }
    public void setContractsUnlocked(boolean unlocked) { contractsUnlocked = unlocked; }
    
    // Roguelike run methods
    public void startNewRun() {
        // Update stats before resetting
        if (runHighestLevel > bestRunLevel) {
            bestRunLevel = runHighestLevel;
        }
        totalRunsCompleted++;
        
        // Reset run-specific data
        score = 0;
        runMoney = 0;
        survivalTime = 0;
        currentLevel = 1;
        runHighestLevel = 1;
        
        // Keep: totalMoney, upgrades, active items, unlocked items, contracts, defeated bosses tracking
    }
    
    public void onBossDefeated() {
        totalBossesDefeated++;
        if (currentLevel > runHighestLevel) {
            runHighestLevel = currentLevel;
        }
    }
    
    public int getRunHighestLevel() { return runHighestLevel; }
    public int getTotalRunsCompleted() { return totalRunsCompleted; }
    public int getBestRunLevel() { return bestRunLevel; }
    public int getTotalBossesDefeated() { return totalBossesDefeated; }
    
    // Extra lives methods
    public int getExtraLives() { return extraLives; }
    public void setExtraLives(int lives) { this.extraLives = Math.max(0, lives); }
    public void addExtraLife() { this.extraLives++; }
    public boolean useExtraLife() {
        if (extraLives > 0) {
            extraLives--;
            return true;
        }
        return false;
    }
    
    // Level select navigation methods
    public int getSelectedLevelView() { return selectedLevelView; }
    public void setSelectedLevelView(int level) { this.selectedLevelView = Math.max(1, Math.min(20, level)); }
    
    public int getLevelCompletionTime(int level) {
        if (level >= 1 && level <= levelCompletionTimes.length) {
            return levelCompletionTimes[level - 1];
        }
        return 0;
    }
    
    public void setLevelCompletionTime(int level, int timeInFrames) {
        if (level >= 1 && level <= levelCompletionTimes.length) {
            // Only save if it's a new record (or first completion)
            if (levelCompletionTimes[level - 1] == 0 || timeInFrames < levelCompletionTimes[level - 1]) {
                levelCompletionTimes[level - 1] = timeInFrames;
            }
        }
    }
    
    // Boss name generator - matches actual aircraft names from Boss.java
    public static String getBossName(int level) {
        if (level <= 0) return "Unknown";
        
        if (level % 2 == 1) {
            // Odd levels: Fighter planes
            switch ((level - 1) / 2 % 10) {
                case 0: return "MIG-15";
                case 1: return "MIG-21";
                case 2: return "MIG-29";
                case 3: return "SU-27";
                case 4: return "SU-57";
                case 5: return "F-86 SABRE";
                case 6: return "F-4 PHANTOM";
                case 7: return "F-15 EAGLE";
                case 8: return "F-22 RAPTOR";
                default: return "F-35 LIGHTNING";
            }
        } else {
            // Even levels: Helicopters
            switch ((level / 2 - 1) % 10) {
                case 0: return "UH-1 HUEY";
                case 1: return "AH-64 APACHE";
                case 2: return "MI-24 HIND";
                case 3: return "CH-47 CHINOOK";
                case 4: return "MI-28 HAVOC";
                case 5: return "AH-1 COBRA";
                case 6: return "KA-52 ALLIGATOR";
                case 7: return "UH-60 BLACK HAWK";
                case 8: return "MI-26 HALO";
                default: return "AH-64E GUARDIAN";
            }
        }
    }
    
    // Audio settings methods
    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float volume) { this.masterVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getSfxVolume() { return sfxVolume; }
    public void setSfxVolume(float volume) { this.sfxVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getUiVolume() { return uiVolume; }
    public void setUiVolume(float volume) { this.uiVolume = Math.max(0, Math.min(1, volume)); }
    
    public float getMusicVolume() { return musicVolume; }
    public void setMusicVolume(float volume) { this.musicVolume = Math.max(0, Math.min(1, volume)); }
    
    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean enabled) { this.soundEnabled = enabled; }
}
