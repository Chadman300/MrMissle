import java.awt.Color;

/**
 * Represents a single skill in the skill tree.
 * Skills are purchased with money and have adjustable active levels.
 */
public class Skill {
    
    private String id;
    private String name;
    private String description;
    private SkillTree.SkillCategory category;
    private int purchasedLevel;  // How many levels bought
    private int activeLevel;     // How many levels currently active (0 to purchasedLevel)
    private int maxLevel;
    private int baseCost;
    private int costPerLevel;
    private int displayOrder;    // Order in the skill tree UI
    
    public Skill(String id, String name, String description, SkillTree.SkillCategory category,
                 int maxLevel, int baseCost, int costPerLevel, int displayOrder) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.costPerLevel = costPerLevel;
        this.displayOrder = displayOrder;
        this.purchasedLevel = 0;
        this.activeLevel = 0;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public SkillTree.SkillCategory getCategory() { return category; }
    public int getPurchasedLevel() { return purchasedLevel; }
    public int getActiveLevel() { return activeLevel; }
    public int getMaxLevel() { return maxLevel; }
    public int getDisplayOrder() { return displayOrder; }
    
    // Get cost for next level
    public int getCost() {
        return baseCost + (purchasedLevel * costPerLevel);
    }
    
    // Get cost for a specific level
    public int getCostForLevel(int level) {
        return baseCost + (level * costPerLevel);
    }
    
    // Get total money invested
    public int getTotalInvestedCost() {
        int total = 0;
        for (int i = 0; i < purchasedLevel; i++) {
            total += getCostForLevel(i);
        }
        return total;
    }
    
    public boolean isUnlocked() {
        return purchasedLevel > 0;
    }
    
    public boolean isMaxed() {
        return purchasedLevel >= maxLevel;
    }
    
    public boolean canUpgrade() {
        return !isMaxed();
    }
    
    public boolean canAfford(int money) {
        return money >= getCost() && canUpgrade();
    }
    
    // Purchase one level
    public void purchase() {
        if (!isMaxed()) {
            purchasedLevel++;
            activeLevel = purchasedLevel; // Auto-activate when purchased
        }
    }
    
    // Set purchased level directly (for loading)
    public void setPurchasedLevel(int level) {
        this.purchasedLevel = Math.max(0, Math.min(maxLevel, level));
        // Ensure active level doesn't exceed purchased
        this.activeLevel = Math.min(this.activeLevel, this.purchasedLevel);
    }
    
    // Set active level (for adjusting how much is used)
    public void setActiveLevel(int level) {
        this.activeLevel = Math.max(0, Math.min(purchasedLevel, level));
    }
    
    // Adjust active level by delta
    public void adjustActiveLevel(int delta) {
        setActiveLevel(activeLevel + delta);
    }
    
    // Reset skill (for refunds)
    public void reset() {
        this.purchasedLevel = 0;
        this.activeLevel = 0;
    }
    
    // Get effect value at current active level
    public double getEffectValue() {
        return getEffectValueAtLevel(activeLevel);
    }
    
    // Get effect value at a specific level (for preview)
    public double getEffectValueAtLevel(int level) {
        switch (id) {
            case "speed": return level * 15.0;           // 15% speed per level
            case "bullet_slow": return level * 0.1;      // 0.1% bullet slow per level
            case "lucky_dodge": return level * 3.0;      // 3% dodge chance per level
            case "extra_life": return level;             // 1 life per level
            case "cooldown": return level * 10.0;        // 10% cooldown reduction per level
            case "bullet_size": return level * 5.0;      // 5% smaller bullets per level
            case "money_score": return level * 15.0;     // 15% more money/score per level
            case "critical": return level * 0.5;         // 0.5% crit chance per level
            default: return 0;
        }
    }
    
    // Get formatted effect description
    public String getEffectDescription() {
        if (activeLevel == 0) return "Not active";
        
        switch (id) {
            case "speed": return "+" + (int)getEffectValue() + "% Speed";
            case "bullet_slow": return String.format("-%.1f%% Bullet Speed", getEffectValue());
            case "lucky_dodge": return "+" + (int)getEffectValue() + "% Dodge Chance";
            case "extra_life": return (int)getEffectValue() + " Extra " + (activeLevel == 1 ? "Life" : "Lives");
            case "cooldown": return "-" + (int)getEffectValue() + "% Cooldown";
            case "bullet_size": return "-" + (int)getEffectValue() + "% Bullet Size";
            case "money_score": return "+" + (int)getEffectValue() + "% Money & Score";
            case "critical": return String.format("+%.1f%% Crit Chance", getEffectValue());
            default: return "";
        }
    }
    
    // Get category color
    public Color getCategoryColor() {
        switch (category) {
            case MOVEMENT: return new Color(136, 192, 208);  // Blue
            case DEFENSE: return new Color(163, 190, 140);   // Green
            case ECONOMY: return new Color(235, 203, 139);   // Gold
            case ACTIVE_ITEM: return new Color(180, 142, 173); // Purple
            default: return new Color(216, 222, 233);
        }
    }
    
    // Get category icon/letter
    public String getCategoryIcon() {
        switch (id) {
            case "speed": return "S";
            case "bullet_slow": return "T";
            case "lucky_dodge": return "L";
            case "extra_life": return "♥";
            case "cooldown": return "C";
            case "bullet_size": return "B";
            case "money_score": return "$";
            case "critical": return "!";
            default: return "?";
        }
    }
    
    // Get name for a specific level (with Roman numerals)
    public String getLevelName(int level) {
        if (level <= 0) return name;
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                          "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
                          "XXI", "XXII", "XXIII", "XXIV", "XXV", "XXVI", "XXVII", "XXVIII", "XXIX", "XXX",
                          "XXXI", "XXXII", "XXXIII", "XXXIV", "XXXV", "XXXVI", "XXXVII", "XXXVIII", "XXXIX", "XL",
                          "XLI", "XLII", "XLIII", "XLIV", "XLV", "XLVI", "XLVII", "XLVIII", "XLIX", "L"};
        if (level < romans.length) {
            return name + " " + romans[level];
        }
        return name + " " + level;
    }
    
    // Get short name for node display
    public String getShortName() {
        switch (id) {
            case "speed": return "Speed";
            case "bullet_slow": return "Bullet Time";
            case "lucky_dodge": return "Lucky Dodge";
            case "extra_life": return "Extra Life";
            case "cooldown": return "Quick Charge";
            case "bullet_size": return "Small Bullets";
            case "money_score": return "Fortune";
            case "critical": return "Critical";
            default: return name;
        }
    }
    
    // Get current bonus text for skill tree display
    public String getCurrentBonusText() {
        if (purchasedLevel == 0) return "Not purchased";
        
        switch (id) {
            case "speed": return "+" + (int)(purchasedLevel * 15) + "% Speed";
            case "bullet_slow": return String.format("-%.1f%% Enemy Bullets", purchasedLevel * 0.1);
            case "lucky_dodge": return "+" + (int)(purchasedLevel * 3) + "% Dodge";
            case "extra_life": return "+" + purchasedLevel + " " + (purchasedLevel == 1 ? "Life" : "Lives");
            case "cooldown": return "-" + (int)(purchasedLevel * 10) + "% Cooldown";
            case "bullet_size": return "-" + (int)(purchasedLevel * 5) + "% Bullet Size";
            case "money_score": return "+" + (int)(purchasedLevel * 15) + "% Rewards";
            case "critical": return String.format("+%.1f%% Crit", purchasedLevel * 0.5);
            default: return "";
        }
    }
}
