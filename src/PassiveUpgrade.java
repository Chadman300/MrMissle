public class PassiveUpgrade {
    private String id;
    private String name;
    private String description;
    private int baseCost;
    private int costIncrement;
    private int currentLevel;  // How many purchased from shop
    private int activeLevel;   // How many allocated/active in stats & loadout
    private int maxLevel;
    private UpgradeType type;
    private boolean useLinearCost; // true = base + level*increment, false = 1.5x multiplier
    
    public enum UpgradeType {
        MAX_HEALTH,         // Increase max health
        ITEM_COOLDOWN,      // Reduce item cooldown
        BULLET_SIZE,        // Reduce bullet size (easier to dodge)
        MONEY_AND_SCORE,    // Increase money and score earned
        CRITICAL_HIT,       // Chance to instantly kill boss
        SPEED_BOOST,        // Increase movement speed
        BULLET_SLOW,        // Slow enemy bullets
        LUCKY_DODGE,        // Chance to phase through bullets
        TARGETING           // Auto-aim toward boss when nearby
    }
    
    // Constructor for exponential cost upgrades (1.5x multiplier)
    public PassiveUpgrade(String id, String name, String description, UpgradeType type, int baseCost, int maxLevel) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.baseCost = baseCost;
        this.costIncrement = 0;
        this.maxLevel = maxLevel;
        this.currentLevel = 0;
        this.activeLevel = 0;
        this.useLinearCost = false;
    }
    
    // Constructor for linear cost upgrades (base + level * increment)
    public PassiveUpgrade(String id, String name, String description, UpgradeType type, int baseCost, int costIncrement, int maxLevel) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.baseCost = baseCost;
        this.costIncrement = costIncrement;
        this.maxLevel = maxLevel;
        this.currentLevel = 0;
        this.activeLevel = 0;
        this.useLinearCost = true;
    }
    
    public boolean canUpgrade(int money) {
        return currentLevel < maxLevel && money >= getCost();
    }
    
    public void upgrade() {
        if (currentLevel < maxLevel) {
            currentLevel++;
            activeLevel = currentLevel; // Auto-activate when purchased
        }
    }
    
    public int getCost() {
        if (useLinearCost) {
            return baseCost + (currentLevel * costIncrement);
        } else {
            // Exponential cost: base * 1.8^level
            return (int)(baseCost * Math.pow(1.8, currentLevel));
        }
    }
    
    public double getMultiplier() {
        switch (type) {
            case MAX_HEALTH:
                return activeLevel; // +1 health per purchase
            case ITEM_COOLDOWN:
                return 1.0 - (activeLevel * 0.125); // -12.5% cooldown (reduced from 25%)
            case BULLET_SIZE:
                return 1.0 - (activeLevel * 0.12); // -12% bullet size (reduced from 20%)
            case MONEY_AND_SCORE:
                return 1.0 + (activeLevel * 0.3); // +30% money and score
            case CRITICAL_HIT:
                return activeLevel * 0.02; // 2% chance to instantly kill boss
            case SPEED_BOOST:
                return 1.0 + (activeLevel * 0.15); // +15% speed (max 10 = 2.5x)
            case BULLET_SLOW:
                return 1.0 - (activeLevel * 0.075); // -7.5% bullet speed (reduced from 15%)
            case LUCKY_DODGE:
                return activeLevel * 0.1; // 10% chance to phase through bullets
            case TARGETING:
                return activeLevel * 0.045; // Aim-assist strength (tripled per level)
            default:
                return 1.0;
        }
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCurrentLevel() { return currentLevel; } // Purchased level
    public int getActiveLevel() { return activeLevel; }   // Allocated level
    public int getBaseCost() { return baseCost; }         // Base cost (before multipliers)
    public int getMaxLevel() { return maxLevel; }
    public UpgradeType getType() { return type; }
    public boolean isMaxed() { return currentLevel >= maxLevel; }
    
    // Setter for manual level adjustment in stats & loadout (active level)
    public void setActiveLevel(int level) {
        this.activeLevel = Math.max(0, Math.min(level, currentLevel)); // Can't exceed purchased amount
    }
    
    // Setter for purchased level (used by shop)
    public void setCurrentLevel(int level) {
        this.currentLevel = Math.max(0, Math.min(level, maxLevel));
        // Ensure active level doesn't exceed purchased level
        this.activeLevel = Math.min(this.activeLevel, this.currentLevel);
    }
}
