/**
 * Represents an active item that the player can unlock and equip.
 * Active items provide special abilities with cooldowns.
 * 
 * AI CONTEXT:
 * - Items are unlocked by defeating bosses (level-based progression)
 * - Only ONE item can be equipped at a time
 * - Items have cooldowns measured in frames (60 FPS)
 * - Some items are instant, others have duration
 * 
 * UNLOCK PROGRESSION:
 * - Level 3: Pool of Loot (spawn money circle)
 * - Level 6: Shield (orbiting shields)
 * - Level 7: Bombs (explosive barrage)
 * - Level 9: Stun (freeze boss)
 * - Level 12: Chromatic Purge (delete random bullet type)
 * - Level 15: Time Slow (slow bullets/beams)
 * - Level 18: Dash (invincibility frames + aim assist)
 * - Level 21: Impulse (push bullets)
 * - Level 24: Frost Beam (freeze bullets)
 * 
 * POWER RANKING (weakest to strongest):
 * Pool of Loot < Shield < Bombs < Stun < Impulse < Time Slow < Chromatic Purge < Dash < Frost Beam
 * - Level 27: Bombs (explosive barrage)
 * 
 * BALANCING:
 * - To change cooldowns: modify cooldownFrames in constructor
 * - To change durations: modify activeDuration in constructor
 * - To change unlock levels: see Game.java → checkItemUnlocks()
 * 
 * USAGE IN GAME:
 * 1. Player equips item from unlocked list
 * 2. Player presses E to activate (if ready)
 * 3. Item goes on cooldown
 * 4. Cooldown ticks down each frame
 * 5. When cooldown reaches 0, item is ready again
 */
public class ActiveItem {
    /**
     * All available active item types.
     * Ordered by power level (weakest to strongest).
     */
    public enum ItemType {
        // Ordered by power level (weakest to strongest)
        LUCKY_CHARM,    // Spawn money circle for bonus money - Level 3
        SHIELD,         // Orbiting shields that block bullets - Level 6
        BOMBS,          // Explosive barrage on screen - Level 7
        STUN,           // Stun the boss temporarily - Level 9
        IMPULSE,        // Push bullets away in radius - Level 21
        TIME_SLOW,      // Slow bullets + beams temporarily - Level 15
        TYPE_PURGE,     // Delete all bullets of a random type - Level 12
        DASH,           // Dash with I-frames + aim assist - Level 18
        FROST_BEAM      // Freeze bullets in a beam - Level 24
    }
    
    private ItemType type;
    private String name;
    private String description;
    private int cooldownFrames;
    private double currentCooldown;
    private boolean active;
    private int activeDuration; // How long the effect lasts (0 for instant)
    private double activeTimer;
    
    public ActiveItem(ItemType type) {
        this.type = type;
        this.currentCooldown = 0;
        this.active = false;
        this.activeTimer = 0;
        
        // Set properties based on type
        switch (type) {
            case LUCKY_CHARM:
                name = "Pool of Loot";
                description = "Spawn money circle lasting 20s (35s cooldown)";
                cooldownFrames = 2100; // 35 seconds
                activeDuration = 6; // Near-instant (circle is spawned, just need brief activation)
                currentCooldown = 0; // Starts ready to use
                break;
            case SHIELD:
                name = "Shield";
                description = "3 orbiting shields (5s first, 20s after)";
                cooldownFrames = 1200; // 20 seconds for subsequent uses
                activeDuration = 0; // Instant activation - shields persist until destroyed
                break;
            case TYPE_PURGE:
                name = "Chromatic Purge";
                description = "Erase random bullet type (15s cooldown)";
                cooldownFrames = 900; // 15 seconds (was 5)
                activeDuration = 0; // Instant
                break;
            case IMPULSE:
                name = "Impulse";
                description = "Push bullets away (5s cooldown)";
                cooldownFrames = 300; // 5 seconds
                activeDuration = 0; // Instant
                break;
            case DASH:
                name = "Dash";
                description = "Dash with I-frames (2s cooldown)";
                cooldownFrames = 120; // 2 seconds (was 3.5)
                activeDuration = 15; // 0.25 seconds of dash (reduced)
                break;
            case BOMBS:
                name = "Bombs";
                description = "Bomb barrage (6s cooldown)";
                cooldownFrames = 360; // 6 seconds (was 12)
                activeDuration = 0; // Instant
                break;
            case TIME_SLOW:
                name = "Time Slow";
                description = "Slow bullets 85% (7.5s cooldown)";
                cooldownFrames = 450; // 7.5 seconds (was 15)
                activeDuration = 240; // 4 seconds (was 2s)
                break;
            case FROST_BEAM:
                name = "Frost Beam";
                description = "Freeze bullets (5s cooldown)";
                cooldownFrames = 300; // 5 seconds
                activeDuration = 120; // 2 seconds
                break;
            case STUN:
                name = "Stun";
                description = "Stun the boss for 1s (10s cooldown)";
                cooldownFrames = 600; // 10 seconds
                activeDuration = 60; // 1 second stun duration (was 1.5s)
                break;
        }
    }
    
    public void update(double deltaTime) {
        // Update cooldown using deltaTime for frame-rate independence
        if (currentCooldown > 0) {
            currentCooldown -= deltaTime;
            if (currentCooldown < 0) currentCooldown = 0;
        }
        
        // Update active duration using deltaTime
        if (active && activeDuration > 0) {
            activeTimer -= deltaTime;
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
        return currentCooldown <= 0 && !active && cooldownFrames > 0;
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
        // Pool of Loot and Shield get a shorter 5 second delay, others get full cooldown
        if (type == ItemType.LUCKY_CHARM || type == ItemType.SHIELD) {
            currentCooldown = 300; // 5 seconds at 60fps
        } else {
            currentCooldown = cooldownFrames; // Full cooldown for other items
        }
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
    public double getCurrentCooldown() { return currentCooldown; }
    public boolean isActive() { return active; }
    public double getActiveTimer() { return activeTimer; }
    public int getActiveDuration() { return activeDuration; }
    
    public float getCooldownPercent() {
        if (cooldownFrames == 0) return 1.0f;
        return 1.0f - ((float)currentCooldown / (float)cooldownFrames);
    }
    
    public void setActive(boolean active) { this.active = active; }
    public void setCurrentCooldown(double cooldown) { this.currentCooldown = cooldown; }
}
