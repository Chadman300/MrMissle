import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AchievementManager {
    private List<Achievement> achievements;
    private Map<String, Achievement> achievementMap;
    private List<Achievement> recentlyUnlocked;
    
    public AchievementManager() {
        achievements = new ArrayList<>();
        achievementMap = new HashMap<>();
        recentlyUnlocked = new ArrayList<>();
        initializeAchievements();
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
}
