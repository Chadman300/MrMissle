public class PassiveUpgrade {
    private String id;
    private String name;
    private String description;
    private int cost;
    private int currentLevel;
    private int maxLevel;
    private UpgradeType type;
    
    public enum UpgradeType {
        MOVEMENT_SPEED,     // Increase player movement speed
        MAX_HEALTH,         // Increase max health
        GRAZE_RADIUS,       // Increase graze detection radius
        ITEM_COOLDOWN,      // Reduce item cooldown
        BULLET_SIZE,        // Reduce bullet size (easier to dodge)
        COMBO_DURATION,     // Increase combo timeout
        MONEY_GAIN,         // Increase money earned
        SCORE_MULTIPLIER    // Increase score gain
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
            case MOVEMENT_SPEED:
                return 1.0 + (currentLevel * 0.1); // +10% per level
            case MAX_HEALTH:
                return currentLevel; // +1 health per level
            case GRAZE_RADIUS:
                return 1.0 + (currentLevel * 0.15); // +15% per level
            case ITEM_COOLDOWN:
                return 1.0 - (currentLevel * 0.1); // -10% per level (min 0.5)
            case BULLET_SIZE:
                return 1.0 - (currentLevel * 0.05); // -5% per level (min 0.75)
            case COMBO_DURATION:
                return 1.0 + (currentLevel * 0.2); // +20% per level
            case MONEY_GAIN:
                return 1.0 + (currentLevel * 0.15); // +15% per level
            case SCORE_MULTIPLIER:
                return 1.0 + (currentLevel * 0.2); // +20% per level
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
