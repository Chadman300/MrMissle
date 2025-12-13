public class Achievement {
    private String id;
    private String name;
    private String description;
    private boolean unlocked;
    private int progress;
    private int target;
    private AchievementType type;
    
    public enum AchievementType {
        BOSS_KILLS,      // Defeat X bosses
        REACH_LEVEL,     // Reach level X
        NO_DAMAGE,       // Complete a level without taking damage
        GRAZE_COUNT,     // Graze X bullets in one game
        HIGH_COMBO,      // Reach combo of X
        MONEY_EARNED,    // Earn X total money
        PERFECT_BOSS     // Defeat boss taking no damage
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
