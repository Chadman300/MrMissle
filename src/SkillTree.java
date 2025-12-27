import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the skill tree system.
 * Skills are purchased with money and can have their active level adjusted.
 */
public class SkillTree {
    
    // Skill categories for organization
    public enum SkillCategory {
        MOVEMENT,       // Speed-related skills
        DEFENSE,        // Survival skills
        ECONOMY,        // Money/score bonuses
        ACTIVE_ITEM     // Active item selection (special category)
    }
    
    private Map<String, Skill> skills;
    private List<Skill> orderedSkills;  // For iteration in order
    private int selectedActiveItemIndex; // Which active item is selected
    
    public SkillTree() {
        skills = new HashMap<>();
        orderedSkills = new ArrayList<>();
        selectedActiveItemIndex = 0;
        initializeSkills();
    }
    
    private void initializeSkills() {
        // === MOVEMENT SKILLS ===
        addSkill(new Skill("speed", "Speed Boost", 
            "Increase movement speed by 15% per level",
            SkillCategory.MOVEMENT, 10, 75, 35, 0));
        
        // === DEFENSE SKILLS ===
        addSkill(new Skill("bullet_slow", "Bullet Time", 
            "Slow enemy bullets by 0.1% per level",
            SkillCategory.DEFENSE, 50, 100, 50, 1));
        
        addSkill(new Skill("lucky_dodge", "Lucky Dodge", 
            "3% chance per level to phase through bullets",
            SkillCategory.DEFENSE, 12, 125, 100, 2));
        
        addSkill(new Skill("extra_life", "Extra Life", 
            "Resurrect once when killed (consumable)",
            SkillCategory.DEFENSE, 3, 5000, 0, 3));
        
        // === ECONOMY SKILLS ===
        addSkill(new Skill("cooldown", "Quick Charge", 
            "Reduce item cooldown by 10% per level",
            SkillCategory.ECONOMY, 5, 120, 60, 4));
        
        addSkill(new Skill("bullet_size", "Small Bullets", 
            "Reduce enemy bullet size by 5% per level",
            SkillCategory.ECONOMY, 5, 200, 100, 5));
        
        addSkill(new Skill("money_score", "Fortune & Glory", 
            "Increase money and score earned by 15% per level",
            SkillCategory.ECONOMY, 5, 150, 75, 6));
        
        addSkill(new Skill("critical", "Critical Strike", 
            "0.5% chance per level to instantly kill boss",
            SkillCategory.ECONOMY, 5, 250, 125, 7));
    }
    
    private void addSkill(Skill skill) {
        skills.put(skill.getId(), skill);
        orderedSkills.add(skill);
    }
    
    // Skill access
    public Skill getSkill(String id) {
        return skills.get(id);
    }
    
    public List<Skill> getAllSkills() {
        return orderedSkills;
    }
    
    public List<Skill> getSkillsByCategory(SkillCategory category) {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : orderedSkills) {
            if (skill.getCategory() == category) {
                result.add(skill);
            }
        }
        return result;
    }
    
    // Get the index of a skill in the ordered list
    public int getSkillIndex(String id) {
        for (int i = 0; i < orderedSkills.size(); i++) {
            if (orderedSkills.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
    
    // Purchase a skill level with money
    public boolean purchaseSkill(String id, GameData gameData) {
        Skill skill = skills.get(id);
        if (skill == null) return false;
        
        int cost = skill.getCost();
        if (gameData.getTotalMoney() >= cost && skill.canUpgrade()) {
            gameData.addTotalMoney(-cost);
            skill.purchase();
            return true;
        }
        return false;
    }
    
    // Adjust the active level of a skill (how much is being used)
    public void adjustActiveLevel(String id, int delta) {
        Skill skill = skills.get(id);
        if (skill != null) {
            skill.adjustActiveLevel(delta);
        }
    }
    
    // Reset a skill and refund money (partial refund)
    public int refundSkill(String id, GameData gameData) {
        Skill skill = skills.get(id);
        if (skill != null && skill.getPurchasedLevel() > 0) {
            int refund = (int)(skill.getTotalInvestedCost() * 0.5); // 50% refund
            skill.reset();
            gameData.addTotalMoney(refund);
            return refund;
        }
        return 0;
    }
    
    // Active item selection
    public int getSelectedActiveItemIndex() {
        return selectedActiveItemIndex;
    }
    
    public void setSelectedActiveItemIndex(int index) {
        this.selectedActiveItemIndex = Math.max(0, index);
    }
    
    public void selectNextActiveItem(GameData gameData) {
        if (gameData.hasActiveItems()) {
            selectedActiveItemIndex = (selectedActiveItemIndex + 1) % gameData.getUnlockedItems().size();
            gameData.equipItem(selectedActiveItemIndex);
        }
    }
    
    public void selectPreviousActiveItem(GameData gameData) {
        if (gameData.hasActiveItems()) {
            selectedActiveItemIndex--;
            if (selectedActiveItemIndex < 0) {
                selectedActiveItemIndex = gameData.getUnlockedItems().size() - 1;
            }
            gameData.equipItem(selectedActiveItemIndex);
        }
    }
    
    // === GAMEPLAY EFFECT GETTERS ===
    
    // Speed bonus (percentage)
    public double getSpeedBonus() {
        Skill skill = getSkill("speed");
        return skill != null ? skill.getActiveLevel() * 15.0 : 0;
    }
    
    // Bullet slow percentage
    public double getBulletSlowPercent() {
        Skill skill = getSkill("bullet_slow");
        return skill != null ? skill.getActiveLevel() * 0.1 : 0;
    }
    
    // Lucky dodge chance (percentage)
    public double getLuckyDodgeChance() {
        Skill skill = getSkill("lucky_dodge");
        return skill != null ? skill.getActiveLevel() * 3.0 : 0;
    }
    
    // Extra lives available
    public int getExtraLives() {
        Skill skill = getSkill("extra_life");
        return skill != null ? skill.getActiveLevel() : 0;
    }
    
    // Cooldown reduction (percentage)
    public double getCooldownReduction() {
        Skill skill = getSkill("cooldown");
        return skill != null ? skill.getActiveLevel() * 10.0 : 0;
    }
    
    // Bullet size reduction (percentage)
    public double getBulletSizeReduction() {
        Skill skill = getSkill("bullet_size");
        return skill != null ? skill.getActiveLevel() * 5.0 : 0;
    }
    
    // Money and score bonus (percentage)
    public double getMoneyScoreBonus() {
        Skill skill = getSkill("money_score");
        return skill != null ? skill.getActiveLevel() * 15.0 : 0;
    }
    
    // Critical hit chance (percentage)
    public double getCriticalChance() {
        Skill skill = getSkill("critical");
        return skill != null ? skill.getActiveLevel() * 0.5 : 0;
    }
    
    // Use an extra life (returns true if one was available)
    public boolean useExtraLife() {
        Skill skill = getSkill("extra_life");
        if (skill != null && skill.getActiveLevel() > 0) {
            skill.setActiveLevel(skill.getActiveLevel() - 1);
            return true;
        }
        return false;
    }
    
    // Restore extra life when purchased
    public void restoreExtraLife() {
        Skill skill = getSkill("extra_life");
        if (skill != null) {
            skill.setActiveLevel(skill.getPurchasedLevel());
        }
    }
    
    // === SAVE/LOAD HELPERS ===
    
    public String getSkillLevelsString() {
        StringBuilder sb = new StringBuilder();
        for (Skill skill : orderedSkills) {
            if (sb.length() > 0) sb.append(",");
            sb.append(skill.getId()).append(":").append(skill.getPurchasedLevel()).append(":").append(skill.getActiveLevel());
        }
        return sb.toString();
    }
    
    public void loadSkillLevels(String data) {
        if (data == null || data.isEmpty()) return;
        String[] entries = data.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length >= 2) {
                Skill skill = skills.get(parts[0]);
                if (skill != null) {
                    skill.setPurchasedLevel(Integer.parseInt(parts[1]));
                    if (parts.length >= 3) {
                        skill.setActiveLevel(Integer.parseInt(parts[2]));
                    } else {
                        skill.setActiveLevel(skill.getPurchasedLevel());
                    }
                }
            }
        }
    }
    
    // Total number of items in skill tree (skills + 1 for active item slot)
    public int getTotalItems() {
        return orderedSkills.size() + 1; // +1 for active item selection
    }
    
    // Check if an index refers to the active item selection
    public boolean isActiveItemIndex(int index) {
        return index == orderedSkills.size();
    }
}
