public class ActiveItem {
    public enum ItemType {
        // Ordered by power level (weakest to strongest)
        LUCKY_CHARM,    // +50% money and score earned (passive) - Level 3
        SHIELD,         // Tank one hit - Level 6
        MAGNET,         // Pull dodged bullets for bonus score - Level 9
        SHOCKWAVE,      // Push bullets away in radius - Level 12
        DASH,           // Dash with I-frames - Level 15
        BOMB,           // Clear all bullets on screen - Level 18
        TIME_SLOW,      // Slow bullets temporarily - Level 21
        LASER_BEAM,     // Fire powerful beam - Level 24
        INVINCIBILITY   // Brief invulnerability - Level 27
    }
    
    private ItemType type;
    private String name;
    private String description;
    private int cooldownFrames;
    private int currentCooldown;
    private boolean active;
    private int activeDuration; // How long the effect lasts (0 for instant)
    private int activeTimer;
    
    public ActiveItem(ItemType type) {
        this.type = type;
        this.currentCooldown = 0;
        this.active = false;
        this.activeTimer = 0;
        
        // Set properties based on type
        switch (type) {
            case LUCKY_CHARM:
                name = "Lucky Charm";
                description = "+50% Money & Score (Passive)";
                cooldownFrames = 0; // Passive, no cooldown
                activeDuration = 0;
                break;
            case SHIELD:
                name = "Shield";
                description = "Tank one hit (7.5s cooldown)";
                cooldownFrames = 450; // 7.5 seconds (was 15)
                activeDuration = 600; // Active for 10 seconds
                break;
            case MAGNET:
                name = "Magnet";
                description = "Pull dodged bullets (4s cooldown)";
                cooldownFrames = 240; // 4 seconds (was 8)
                activeDuration = 180; // 3 seconds
                break;
            case SHOCKWAVE:
                name = "Shockwave";
                description = "Push bullets away (5s cooldown)";
                cooldownFrames = 300; // 5 seconds (was 10)
                activeDuration = 0; // Instant
                break;
            case DASH:
                name = "Dash";
                description = "Dash with I-frames (2s cooldown)";
                cooldownFrames = 120; // 2 seconds (was 3.5)
                activeDuration = 20; // 0.33 seconds of dash
                break;
            case BOMB:
                name = "Bomb";
                description = "Clear all bullets (6s cooldown)";
                cooldownFrames = 360; // 6 seconds (was 12)
                activeDuration = 0; // Instant
                break;
            case TIME_SLOW:
                name = "Time Slow";
                description = "Slow bullets 50% (7.5s cooldown)";
                cooldownFrames = 450; // 7.5 seconds (was 15)
                activeDuration = 240; // 4 seconds
                break;
            case LASER_BEAM:
                name = "Laser Beam";
                description = "Fire powerful beam (5s cooldown)";
                cooldownFrames = 300; // 5 seconds (was 10)
                activeDuration = 120; // 2 seconds
                break;
            case INVINCIBILITY:
                name = "Invincibility";
                description = "Brief invulnerability (10s cooldown)";
                cooldownFrames = 600; // 10 seconds (was 20)
                activeDuration = 180; // 3 seconds
                break;
        }
    }
    
    public void update() {
        // Update cooldown
        if (currentCooldown > 0) {
            currentCooldown--;
        }
        
        // Update active duration
        if (active && activeDuration > 0) {
            activeTimer--;
            if (activeTimer <= 0) {
                active = false;
            }
        }
        
        // Instant items (activeDuration == 0) deactivate immediately after being handled
        if (active && activeDuration == 0) {
            active = false;
        }
    }
    
    public boolean canActivate() {
        return currentCooldown == 0 && !active && cooldownFrames > 0;
    }
    
    public void activate() {
        if (canActivate()) {
            active = true;
            activeTimer = activeDuration;
            currentCooldown = cooldownFrames;
        }
    }
    
    public void startLevelCooldown() {
        // Start each level with item on cooldown
        currentCooldown = cooldownFrames;
        active = false;
        activeTimer = 0;
    }
    
    public boolean isPassive() {
        return cooldownFrames == 0;
    }
    
    // Getters
    public ItemType getType() { return type; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCooldownFrames() { return cooldownFrames; }
    public int getCurrentCooldown() { return currentCooldown; }
    public boolean isActive() { return active; }
    public int getActiveTimer() { return activeTimer; }
    public int getActiveDuration() { return activeDuration; }
    
    public float getCooldownPercent() {
        if (cooldownFrames == 0) return 1.0f;
        return 1.0f - ((float)currentCooldown / (float)cooldownFrames);
    }
    
    public void setActive(boolean active) { this.active = active; }
    public void setCurrentCooldown(int cooldown) { this.currentCooldown = cooldown; }
}
