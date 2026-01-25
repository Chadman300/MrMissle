import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * SaveData contains all game state that needs to be persisted.
 * This class is serializable so it can be saved to disk.
 */
public class SaveData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Metadata
    public String saveName;
    public long saveTimestamp;
    public int saveVersion = 1;
    
    // Score and money
    public int score;
    public int totalMoney;
    public int runMoney;
    public int survivalTime;
    
    // Level progression
    public int currentLevel;
    public int maxUnlockedLevel;
    public boolean[] defeatedBosses;
    
    // Upgrades (persistent)
    public int speedUpgradeLevel;
    public int bulletSlowUpgradeLevel;
    public int luckyDodgeUpgradeLevel;
    public int attackWindowUpgradeLevel;
    
    // Active upgrades (allocated from purchased upgrades)
    public int activeSpeedLevel;
    public int activeBulletSlowLevel;
    public int activeLuckyDodgeLevel;
    public int activeAttackWindowLevel;
    
    // Active Items system
    public List<String> unlockedItems; // Store as strings for serialization
    public int equippedItemIndex;
    
    // Risk Contracts system
    public boolean contractsUnlocked;
    public boolean seenContractUnlock;
    
    // Roguelike run tracking
    public int runHighestLevel;
    public int totalRunsCompleted;
    public int bestRunLevel;
    public int totalBossesDefeated;
    
    // Extra lives
    public int extraLives;
    
    // Level select navigation
    public int selectedLevelView;
    public int[] levelCompletionTimes;
    
    // Level stats (saved as arrays of primitives for serialization)
    public LevelStatsData[] levelStats;
    public LevelStatsData currentLevelStats;
    public LevelStatsData cumulativeRunStats;
    
    // Audio settings
    public float masterVolume;
    public float sfxVolume;
    public float uiVolume;
    public float musicVolume;
    public boolean soundEnabled;
    
    // Gameplay settings
    public int countdownMode;
    
    // Achievements data (we'll add this if needed)
    public List<String> unlockedAchievements;
    
    // Passive upgrades data
    public List<String> unlockedPassiveUpgrades;
    
    /**
     * Inner class to hold level stats data in a serializable format
     */
    public static class LevelStatsData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public int timeInFrames;
        public int dodges;
        public int bulletsSpawned;
        public int livesUsed;
        public int damageTaken;
        public int perfectDodges;
        public int nearMisses;
        public int maxCombo;
        
        public LevelStatsData() {
            this.timeInFrames = 0;
            this.dodges = 0;
            this.bulletsSpawned = 0;
            this.livesUsed = 0;
            this.damageTaken = 0;
            this.perfectDodges = 0;
            this.nearMisses = 0;
            this.maxCombo = 0;
        }
        
        public LevelStatsData(LevelStats stats) {
            this.timeInFrames = stats.getTimeInFrames();
            this.dodges = stats.getDodges();
            this.bulletsSpawned = stats.getBulletsSpawned();
            this.livesUsed = stats.getLivesUsed();
            this.damageTaken = stats.getDamageTaken();
            this.perfectDodges = stats.getPerfectDodges();
            this.nearMisses = stats.getNearMisses();
            this.maxCombo = stats.getMaxCombo();
        }
        
        public LevelStats toLevelStats() {
            LevelStats stats = new LevelStats();
            stats.setTimeInFrames(timeInFrames);
            stats.setDodges(dodges);
            stats.setBulletsSpawned(bulletsSpawned);
            stats.setLivesUsed(livesUsed);
            stats.setDamageTaken(damageTaken);
            stats.setPerfectDodges(perfectDodges);
            stats.setNearMisses(nearMisses);
            stats.setMaxCombo(maxCombo);
            return stats;
        }
    }
    
    public SaveData() {
        this.saveName = "New Game";
        this.saveTimestamp = System.currentTimeMillis();
        this.currentLevel = 1;
        this.maxUnlockedLevel = 1;
        this.selectedLevelView = 1;
        this.defeatedBosses = new boolean[100];
        this.levelCompletionTimes = new int[100];
        this.levelStats = new LevelStatsData[100];
        for (int i = 0; i < levelStats.length; i++) {
            levelStats[i] = new LevelStatsData();
        }
        this.currentLevelStats = new LevelStatsData();
        this.cumulativeRunStats = new LevelStatsData();
        this.unlockedItems = new ArrayList<>();
        this.unlockedAchievements = new ArrayList<>();
        this.unlockedPassiveUpgrades = new ArrayList<>();
        
        // Roguelike stats - start at level 1
        this.runHighestLevel = 1;
        this.bestRunLevel = 1;
        this.totalRunsCompleted = 0;
        this.totalBossesDefeated = 0;
        
        // Default audio settings
        this.masterVolume = 0.7f;
        this.sfxVolume = 0.8f;
        this.uiVolume = 0.8f;
        this.musicVolume = 0.5f;
        this.soundEnabled = true;
        this.countdownMode = 1;
    }
    
    /**
     * Create SaveData from current GameData
     */
    public static SaveData fromGameData(GameData gameData, AchievementManager achievementManager, 
                                       PassiveUpgradeManager passiveUpgradeManager, String saveName) {
        SaveData data = new SaveData();
        data.saveName = saveName;
        data.saveTimestamp = System.currentTimeMillis();
        
        // Copy all data from GameData
        data.score = gameData.getScore();
        data.totalMoney = gameData.getTotalMoney();
        data.runMoney = gameData.getRunMoney();
        data.survivalTime = gameData.getSurvivalTime();
        
        data.currentLevel = gameData.getCurrentLevel();
        data.maxUnlockedLevel = gameData.getMaxUnlockedLevel();
        data.defeatedBosses = gameData.getDefeatedBosses().clone();
        
        data.speedUpgradeLevel = gameData.getSpeedUpgradeLevel();
        data.bulletSlowUpgradeLevel = gameData.getBulletSlowUpgradeLevel();
        data.luckyDodgeUpgradeLevel = gameData.getLuckyDodgeUpgradeLevel();
        data.attackWindowUpgradeLevel = gameData.getAttackWindowUpgradeLevel();
        
        data.activeSpeedLevel = gameData.getActiveSpeedLevel();
        data.activeBulletSlowLevel = gameData.getActiveBulletSlowLevel();
        data.activeLuckyDodgeLevel = gameData.getActiveLuckyDodgeLevel();
        data.activeAttackWindowLevel = gameData.getActiveAttackWindowLevel();
        
        // Active items
        data.unlockedItems = new ArrayList<>();
        for (ActiveItem.ItemType type : gameData.getUnlockedItems()) {
            data.unlockedItems.add(type.name());
        }
        data.equippedItemIndex = gameData.getEquippedItemIndex();
        
        data.contractsUnlocked = gameData.areContractsUnlocked();
        data.seenContractUnlock = gameData.hasSeenContractUnlock();
        
        data.runHighestLevel = gameData.getRunHighestLevel();
        data.totalRunsCompleted = gameData.getTotalRunsCompleted();
        data.bestRunLevel = gameData.getBestRunLevel();
        data.totalBossesDefeated = gameData.getTotalBossesDefeated();
        
        data.extraLives = gameData.getExtraLives();
        data.selectedLevelView = gameData.getSelectedLevelView();
        
        // Level stats
        for (int i = 0; i < 100; i++) {
            data.levelCompletionTimes[i] = gameData.getLevelCompletionTime(i + 1);
            data.levelStats[i] = new LevelStatsData(gameData.getLevelStats(i + 1));
        }
        data.currentLevelStats = new LevelStatsData(gameData.getCurrentLevelStats());
        data.cumulativeRunStats = new LevelStatsData(gameData.getCumulativeRunStats());
        
        // Audio settings
        data.masterVolume = gameData.getMasterVolume();
        data.sfxVolume = gameData.getSfxVolume();
        data.uiVolume = gameData.getUiVolume();
        data.musicVolume = gameData.getMusicVolume();
        data.soundEnabled = gameData.isSoundEnabled();
        data.countdownMode = gameData.getCountdownMode();
        
        // Achievements
        if (achievementManager != null) {
            data.unlockedAchievements = new ArrayList<>();
            for (Achievement achievement : achievementManager.getAllAchievements()) {
                if (achievement.isUnlocked()) {
                    data.unlockedAchievements.add(achievement.getId());
                }
            }
        }
        
        // Passive upgrades (save current and active levels)
        if (passiveUpgradeManager != null) {
            data.unlockedPassiveUpgrades = new ArrayList<>();
            for (PassiveUpgrade upgrade : passiveUpgradeManager.getAllUpgrades()) {
                // Save as "id:currentLevel:activeLevel"
                String upgradeData = upgrade.getId() + ":" + upgrade.getCurrentLevel() + ":" + upgrade.getActiveLevel();
                data.unlockedPassiveUpgrades.add(upgradeData);
            }
        }
        
        return data;
    }
    
    /**
     * Load this SaveData into a GameData object
     */
    public void loadIntoGameData(GameData gameData, AchievementManager achievementManager,
                                PassiveUpgradeManager passiveUpgradeManager) {
        gameData.setScore(score);
        gameData.setTotalMoney(totalMoney);
        gameData.setRunMoney(runMoney);
        gameData.setSurvivalTime(survivalTime);
        
        gameData.setCurrentLevel(currentLevel);
        // Ensure at least level 1 is always unlocked
        gameData.setMaxUnlockedLevel(Math.max(1, maxUnlockedLevel));
        for (int i = 0; i < defeatedBosses.length && i < 100; i++) {
            gameData.setBossDefeated(i, defeatedBosses[i]);
        }
        
        gameData.setSpeedUpgradeLevel(speedUpgradeLevel);
        gameData.setBulletSlowUpgradeLevel(bulletSlowUpgradeLevel);
        gameData.setLuckyDodgeUpgradeLevel(luckyDodgeUpgradeLevel);
        gameData.setAttackWindowUpgradeLevel(attackWindowUpgradeLevel);
        
        gameData.setActiveSpeedLevel(activeSpeedLevel);
        gameData.setActiveBulletSlowLevel(activeBulletSlowLevel);
        gameData.setActiveLuckyDodgeLevel(activeLuckyDodgeLevel);
        gameData.setActiveAttackWindowLevel(activeAttackWindowLevel);
        
        // Active items - need to reconstruct from strings
        gameData.getUnlockedItems().clear();
        for (String itemName : unlockedItems) {
            try {
                ActiveItem.ItemType type = ActiveItem.ItemType.valueOf(itemName);
                gameData.getUnlockedItems().add(type);
            } catch (IllegalArgumentException e) {
                // Skip invalid items
            }
        }
        if (equippedItemIndex >= 0 && equippedItemIndex < gameData.getUnlockedItems().size()) {
            gameData.equipItem(equippedItemIndex);
        }
        
        gameData.setContractsUnlocked(contractsUnlocked);
        gameData.setSeenContractUnlock(seenContractUnlock);
        
        // Roguelike stats
        gameData.setRunHighestLevel(runHighestLevel);
        gameData.setTotalRunsCompleted(totalRunsCompleted);
        gameData.setBestRunLevel(bestRunLevel);
        gameData.setTotalBossesDefeated(totalBossesDefeated);
        
        gameData.setExtraLives(extraLives);
        gameData.setSelectedLevelView(selectedLevelView);
        
        // Level stats
        for (int i = 0; i < 100 && i < levelCompletionTimes.length; i++) {
            gameData.setLevelCompletionTime(i + 1, levelCompletionTimes[i]);
        }
        
        // Audio settings
        gameData.setMasterVolume(masterVolume);
        gameData.setSfxVolume(sfxVolume);
        gameData.setUiVolume(uiVolume);
        gameData.setMusicVolume(musicVolume);
        gameData.setSoundEnabled(soundEnabled);
        gameData.setCountdownMode(countdownMode);
        
        // Achievements
        if (achievementManager != null && unlockedAchievements != null) {
            for (String achievementId : unlockedAchievements) {
                Achievement achievement = achievementManager.getAchievementById(achievementId);
                if (achievement != null && !achievement.isUnlocked()) {
                    achievement.unlock();
                }
            }
        }
        
        // Passive upgrades
        if (passiveUpgradeManager != null && unlockedPassiveUpgrades != null) {
            for (String upgradeData : unlockedPassiveUpgrades) {
                // Parse "id:currentLevel:activeLevel"
                String[] parts = upgradeData.split(":");
                if (parts.length == 3) {
                    String id = parts[0];
                    int currentLevel = Integer.parseInt(parts[1]);
                    int activeLevel = Integer.parseInt(parts[2]);
                    PassiveUpgrade upgrade = passiveUpgradeManager.getUpgradeById(id);
                    if (upgrade != null) {
                        upgrade.setCurrentLevel(currentLevel);
                        upgrade.setActiveLevel(activeLevel);
                        
                        // Restore extra lives from health upgrade
                        if (id.equals("health")) {
                            gameData.setExtraLives(currentLevel);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get a summary string for display in save slot UI
     */
    public String getSummary() {
        return String.format("Level %d | $%d | %d Runs", 
            maxUnlockedLevel, totalMoney, totalRunsCompleted);
    }
}
