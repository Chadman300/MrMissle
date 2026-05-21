import interfaces.AchievementProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AchievementManager {
    private List<Achievement> achievements;
    private Map<String, Achievement> achievementMap;
    private List<Achievement> recentlyUnlocked;

    /** Optional external backend (e.g. Steam). Null = local only. */
    private AchievementProvider provider;

    public AchievementManager() {
        achievements = new ArrayList<>();
        achievementMap = new HashMap<>();
        recentlyUnlocked = new ArrayList<>();
        initializeAchievements();
    }

    /** Set an external achievement provider (e.g. Steam). */
    public void setProvider(AchievementProvider provider) {
        this.provider = provider;
    }

    /** True if an external provider is wired up and ready. */
    public boolean hasExternalProvider() {
        return provider != null && provider.isReady();
    }
    
    private void initializeAchievements() {
        // Boss kills
        addAchievement("first_blood", "First Blood", "Defeat your first boss", Achievement.AchievementType.BOSS_KILLS, 1);
        addAchievement("boss_slayer", "Boss Slayer", "Defeat 5 bosses", Achievement.AchievementType.BOSS_KILLS, 5);
        addAchievement("boss_hunter", "Boss Hunter", "Defeat 10 bosses", Achievement.AchievementType.BOSS_KILLS, 10);
        addAchievement("boss_destroyer", "Boss Destroyer", "Defeat 25 bosses", Achievement.AchievementType.BOSS_KILLS, 25);
        
        // Reach level
        addAchievement("novice", "Novice Pilot", "Reach level 5", Achievement.AchievementType.REACH_LEVEL, 5);
        addAchievement("veteran", "Veteran Pilot", "Reach level 10", Achievement.AchievementType.REACH_LEVEL, 10);
        addAchievement("ace", "Ace Pilot", "Reach level 15", Achievement.AchievementType.REACH_LEVEL, 15);
        addAchievement("legendary", "Legendary Pilot", "Reach level 20", Achievement.AchievementType.REACH_LEVEL, 20);
        
        // Perfect boss
        addAchievement("untouchable", "Untouchable", "Defeat a boss without taking damage", Achievement.AchievementType.PERFECT_BOSS, 1);
        addAchievement("flawless_run", "Flawless Run", "Defeat 3 bosses in a row without taking damage", Achievement.AchievementType.NO_DAMAGE, 3);
        
        // Grazes
        addAchievement("close_call", "Close Call", "Graze 50 bullets in one game", Achievement.AchievementType.GRAZE_COUNT, 50);
        addAchievement("thrill_seeker", "Thrill Seeker", "Graze 200 bullets in one game", Achievement.AchievementType.GRAZE_COUNT, 200);
        addAchievement("death_dancer", "Death Dancer", "Graze 500 bullets in one game", Achievement.AchievementType.GRAZE_COUNT, 500);
        
        // Combo
        addAchievement("combo_starter", "Combo Starter", "Reach a 10x combo", Achievement.AchievementType.HIGH_COMBO, 10);
        addAchievement("combo_master", "Combo Master", "Reach a 25x combo", Achievement.AchievementType.HIGH_COMBO, 25);
        addAchievement("combo_god", "Combo God", "Reach a 50x combo", Achievement.AchievementType.HIGH_COMBO, 50);
        
        // Money
        addAchievement("penny_pincher", "Penny Pincher", "Earn $1000 total", Achievement.AchievementType.MONEY_EARNED, 1000);
        addAchievement("money_maker", "Money Maker", "Earn $5000 total", Achievement.AchievementType.MONEY_EARNED, 5000);
        addAchievement("tycoon", "Tycoon", "Earn $10000 total", Achievement.AchievementType.MONEY_EARNED, 10000);
        
        // Raw Dog - No upgrades challenges
        addAchievement("raw_dog_5", "Raw Dog I", "Reach level 5 without purchasing any upgrades", Achievement.AchievementType.NO_UPGRADES, 5);
        addAchievement("raw_dog_10", "Raw Dog II", "Reach level 10 without purchasing any upgrades", Achievement.AchievementType.NO_UPGRADES, 10);
        addAchievement("raw_dog_15", "Raw Dog III", "Reach level 15 without purchasing any upgrades", Achievement.AchievementType.NO_UPGRADES, 15);
        addAchievement("raw_dog_20", "Raw Dog IV", "Reach level 20 without purchasing any upgrades", Achievement.AchievementType.NO_UPGRADES, 20);
        addAchievement("raw_dog_master", "Raw Dog Master", "Reach level 28 without purchasing any upgrades", Achievement.AchievementType.NO_UPGRADES, 28);
        
        // Speedrunning - Complete levels under time limit (time in seconds)
        addAchievement("speedrun_30", "Speed Demon I", "Complete a level in under 30 seconds", Achievement.AchievementType.SPEED_RUN, 1800); // 30 seconds * 60 fps
        addAchievement("speedrun_20", "Speed Demon II", "Complete a level in under 20 seconds", Achievement.AchievementType.SPEED_RUN, 1200); // 20 seconds * 60 fps
        addAchievement("speedrun_15", "Lightning Fast", "Complete a level in under 15 seconds", Achievement.AchievementType.SPEED_RUN, 900); // 15 seconds * 60 fps
        addAchievement("speedrun_10", "Sonic Boom", "Complete a level in under 10 seconds", Achievement.AchievementType.SPEED_RUN, 600); // 10 seconds * 60 fps
        addAchievement("speedrun_master", "Time Lord", "Complete a level in under 7 seconds", Achievement.AchievementType.SPEED_RUN, 420); // 7 seconds * 60 fps
        
        // Clutch survival
        addAchievement("clutch", "Clutch!", "Use 5 missiles in a single run and survive on your last one.", Achievement.AchievementType.CLUTCH_SURVIVAL, 5);
        
        // Tutorial
        addAchievement("training_thrusters", "Training Thrusters", "Complete the tutorial", Achievement.AchievementType.TUTORIAL_COMPLETE, 1);
        
        // Endless mode
        addAchievement("endless_rookie", "Into the Unknown", "Play endless mode for the first time", Achievement.AchievementType.ENDLESS_LEVELS, 1);
        addAchievement("endless_10", "Endless Warrior", "Beat 10 levels in endless mode", Achievement.AchievementType.ENDLESS_LEVELS, 10);
        addAchievement("endless_28", "Full Circle", "Complete a full prestige cycle in endless mode", Achievement.AchievementType.ENDLESS_LEVELS, 28);
        addAchievement("endless_56", "Double Down", "Beat 56 levels in endless mode (2 prestiges)", Achievement.AchievementType.ENDLESS_LEVELS, 56);
        addAchievement("endless_100", "Centurion", "Beat 100 levels in endless mode", Achievement.AchievementType.ENDLESS_LEVELS, 100);
    }
    
    private void addAchievement(String id, String name, String description, Achievement.AchievementType type, int target) {
        Achievement achievement = new Achievement(id, name, description, type, target);
        achievements.add(achievement);
        achievementMap.put(id, achievement);
    }
    
    public void updateProgress(Achievement.AchievementType type, int value) {
        for (Achievement achievement : achievements) {
            if (achievement.getType() == type && !achievement.isUnlocked()) {
                int oldProgress = achievement.getProgress();
                achievement.setProgress(value);
                if (achievement.isUnlocked() && oldProgress < achievement.getTarget()) {
                    recentlyUnlocked.add(achievement);
                }
            }
        }
    }
    
    public void incrementProgress(Achievement.AchievementType type, int amount) {
        for (Achievement achievement : achievements) {
            if (achievement.getType() == type && !achievement.isUnlocked()) {
                int oldProgress = achievement.getProgress();
                achievement.addProgress(amount);
                if (achievement.isUnlocked() && oldProgress < achievement.getTarget()) {
                    recentlyUnlocked.add(achievement);
                }
            }
        }
    }
    
    public List<Achievement> getRecentlyUnlocked() {
        return new ArrayList<>(recentlyUnlocked);
    }
    
    public void clearRecentlyUnlocked() {
        recentlyUnlocked.clear();
    }
    
    public List<Achievement> getAllAchievements() {
        return achievements;
    }
    
    public int getUnlockedCount() {
        int count = 0;
        for (Achievement achievement : achievements) {
            if (achievement.isUnlocked()) count++;
        }
        return count;
    }
    
    public Achievement getAchievement(String id) {
        return achievementMap.get(id);
    }
    
    public Achievement getAchievementById(String id) {
        return achievementMap.get(id);
    }
    
    /**
     * Reset all achievements to locked state with zero progress.
     * Used when loading a different save file to prevent leaking.
     */
    public void resetAllAchievements() {
        for (Achievement achievement : achievements) {
            achievement.reset();
        }
        recentlyUnlocked.clear();
    }
    
    /**
     * Record any recently unlocked achievements into the global save data.
     * Also notifies the external provider (e.g. Steam) on the FIRST global unlock,
     * so replays on other saves never double-fire.
     * Returns true if the global data was modified (needs saving).
     */
    public boolean recordGlobalUnlocks(List<Achievement> newlyUnlocked, GlobalSaveData globalData) {
        if (globalData == null || newlyUnlocked == null) return false;
        boolean changed = false;
        for (Achievement achievement : newlyUnlocked) {
            if (globalData.recordAchievement(achievement.getId())) {
                changed = true;
                if (provider != null && provider.isReady()) {
                    provider.unlock(achievement.getId());
                }
            }
        }
        return changed;
    }

    /**
     * One-time bulk push of every previously-unlocked global achievement to the
     * external provider. Call exactly once per machine after Steam first connects
     * (gated by GlobalSaveData.steamSynced).
     * Returns the number of unlocks pushed.
     */
    public int bulkSyncToProvider(GlobalSaveData globalData) {
        if (provider == null || !provider.isReady() || globalData == null
                || globalData.globalUnlockedAchievements == null) return 0;
        int pushed = 0;
        for (String id : globalData.globalUnlockedAchievements) {
            if (!provider.isUnlocked(id)) {
                provider.unlock(id);
                pushed++;
            }
        }
        return pushed;
    }
}
