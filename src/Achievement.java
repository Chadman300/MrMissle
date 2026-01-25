/**
 * Represents a trackable achievement with progress.
 * 
 * AI CONTEXT:
 * - Achievements reward player milestones and encourage different playstyles
 * - Each achievement has:
 *   - Unique ID (for save/load persistence)
 *   - Name and description (displayed in UI)
 *   - Type (category of achievement)
 *   - Progress counter (current / target)
 *   - Unlocked status (boolean)
 * 
 * ACHIEVEMENT TYPES:
 * - BOSS_KILLS: Total bosses defeated
 * - REACH_LEVEL: Highest level reached
 * - NO_DAMAGE: Complete level taking no damage
 * - GRAZE_COUNT: Graze X bullets in single run
 * - HIGH_COMBO: Reach combo of X
 * - MONEY_EARNED: Accumulate X total money
 * - PERFECT_BOSS: Defeat boss without taking damage
 * - NO_UPGRADES: Complete game without buying upgrades
 * - SPEED_RUN: Complete level within time limit
 * 
 * IMPLEMENTATION:
 * - Created in AchievementManager.initializeAchievements()
 * - Progress tracked in Game.java and GameData.java
 * - Saved to game_save.json for persistence
 * - Notifications shown when unlocked
 * 
 * TO ADD NEW ACHIEVEMENT:
 * 1. Add type to AchievementType enum (if needed)
 * 2. Create achievement in AchievementManager
 * 3. Add progress tracking in appropriate game code
 * 4. Test unlock triggers
 */
public class Achievement {
    /** Unique identifier for save/load */
    private String id;
    
    /** Display name shown in UI */
    private String name;
    
    /** Description of how to unlock */
    private String description;
    
    /** Whether achievement is unlocked */
    private boolean unlocked;
    
    /** Current progress toward target */
    private int progress;
    
    /** Target value to unlock achievement */
    private int target;
    
    /** Category/type of achievement */
    private AchievementType type;
    
    /**
     * Achievement categories.
     * Used to organize achievements and track progress appropriately.
     */
    public enum AchievementType {
        BOSS_KILLS,      // Defeat X bosses
        REACH_LEVEL,     // Reach level X
        NO_DAMAGE,       // Complete a level without taking damage
        GRAZE_COUNT,     // Graze X bullets in one game
        HIGH_COMBO,      // Reach combo of X
        MONEY_EARNED,    // Earn X total money
        PERFECT_BOSS,    // Defeat boss taking no damage
        NO_UPGRADES,     // Complete levels without purchasing any upgrades
        SPEED_RUN        // Complete a level within X seconds
    }
    
    public Achievement(String id, String name, String description, AchievementType type, int target) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.target = target;
        this.progress = 0;
        this.unlocked = false;
    }
    
    public void addProgress(int amount) {
        if (!unlocked) {
            progress += amount;
            if (progress >= target) {
                progress = target;
                unlocked = true;
            }
        }
    }
    
    public void setProgress(int progress) {
        if (!unlocked) {
            this.progress = progress;
            if (this.progress >= target) {
                this.progress = target;
                unlocked = true;
            }
        }
    }
    
    public void unlock() {
        unlocked = true;
        progress = target;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isUnlocked() { return unlocked; }
    public int getProgress() { return progress; }
    public int getTarget() { return target; }
    public AchievementType getType() { return type; }
    public float getProgressPercent() { return (float)progress / target; }
}
