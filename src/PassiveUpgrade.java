public class PassiveUpgrade {
    private String id;
    private String name;
    private String description;
    private int cost;
    private int currentLevel;
    private int maxLevel;
    private UpgradeType type;
    
    public enum UpgradeType {
        MAX_HEALTH,         // Increase max health
        ITEM_COOLDOWN,      // Reduce item cooldown
        BULLET_SIZE,        // Reduce bullet size (easier to dodge)
        MONEY_AND_SCORE,    // Increase money and score earned
        CRITICAL_HIT        // Chance to instantly kill boss
    }
    
    public PassiveUpgrade(String id, String name, String description, UpgradeType type, int baseCost, int maxLevel) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.cost = baseCost;
        this.maxLevel = maxLevel;
        this.currentLevel = 0;
    }
    
    public boolean canUpgrade(int money) {
        return currentLevel < maxLevel && money >= cost;
    }
    
    public void upgrade() {
        if (currentLevel < maxLevel) {
            currentLevel++;
            // Increase cost for next level (1.5x multiplier)
            cost = (int)(cost * 1.5);
        }
    }
    
    public double getMultiplier() {
        switch (type) {
            case MAX_HEALTH:
                return currentLevel; // +1 health per level
            case ITEM_COOLDOWN:
                return 1.0 - (currentLevel * 0.1); // -10% per level (min 0.5)
            case BULLET_SIZE:
                return 1.0 - (currentLevel * 0.05); // -5% per level (min 0.75)
            case MONEY_AND_SCORE:
                return 1.0 + (currentLevel * 0.15); // +15% per level for both money and score
            case CRITICAL_HIT:
                return currentLevel * 0.01; // 1% chance per level to instantly kill boss
            default:
                return 1.0;
        }
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCost() { return cost; }
    public int getCurrentLevel() { return currentLevel; }
    public int getMaxLevel() { return maxLevel; }
    public UpgradeType getType() { return type; }
    public boolean isMaxed() { return currentLevel >= maxLevel; }
}
