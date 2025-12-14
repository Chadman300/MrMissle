public class ComboSystem {
    private int combo;
    private int comboTimer;
    private int maxCombo;
    private double comboMultiplier;
    private int comboTimeout;
    
    // Graze tiers for close calls
    private int closeCallCount; // Very close grazes
    private int perfectDodgeCount; // Frame-perfect dodges
    private int totalGrazeValue; // Accumulated graze value this combo
    
    // Combo milestone announcements
    private String currentAnnouncement;
    private int announcementTimer;
    private static final int ANNOUNCEMENT_DURATION = 90; // 1.5 seconds
    
    // Combo milestones for announcements
    private static final int[] COMBO_MILESTONES = {10, 25, 50, 100, 200, 500, 1000};
    private static final String[] COMBO_MESSAGES = {
        "NICE!", "GREAT!", "AMAZING!", "INCREDIBLE!", "LEGENDARY!", "GODLIKE!", "IMPOSSIBLE!"
    };
    
    private static final int BASE_COMBO_TIMEOUT = 180; // 3 seconds at 60 FPS
    private static final double COMBO_MULTIPLIER_PER_LEVEL = 0.05; // 5% per combo level
    
    public ComboSystem() {
        this.combo = 0;
        this.maxCombo = 0;
        this.comboTimer = 0;
        this.comboMultiplier = 1.0;
        this.comboTimeout = BASE_COMBO_TIMEOUT;
        this.closeCallCount = 0;
        this.perfectDodgeCount = 0;
        this.totalGrazeValue = 0;
        this.currentAnnouncement = null;
        this.announcementTimer = 0;
    }
    
    public void update(double deltaTime, double comboTimeoutMultiplier) {
        comboTimeout = (int)(BASE_COMBO_TIMEOUT * comboTimeoutMultiplier);
        
        if (combo > 0) {
            comboTimer -= deltaTime;
            if (comboTimer <= 0) {
                resetCombo();
            }
        }
        
        // Update multiplier based on combo with bonus for close calls
        double baseMultiplier = 1.0 + (Math.min(combo, 50) * COMBO_MULTIPLIER_PER_LEVEL);
        double closeCallBonus = closeCallCount * 0.02; // +2% per close call
        double perfectDodgeBonus = perfectDodgeCount * 0.05; // +5% per perfect dodge
        comboMultiplier = baseMultiplier + closeCallBonus + perfectDodgeBonus;
        
        // Update announcement timer
        if (announcementTimer > 0) {
            announcementTimer -= deltaTime;
            if (announcementTimer <= 0) {
                currentAnnouncement = null;
            }
        }
    }
    
    public void addCombo() {
        addCombo(1, false, false);
    }
    
    public void addCombo(int value, boolean isCloseCall, boolean isPerfectDodge) {
        addCombo(value, isCloseCall, isPerfectDodge, null);
    }
    
    public void addCombo(int value, boolean isCloseCall, boolean isPerfectDodge, SoundManager soundManager) {
        int previousCombo = combo;
        combo += value;
        totalGrazeValue += value;
        
        if (isCloseCall) {
            closeCallCount++;
        }
        if (isPerfectDodge) {
            perfectDodgeCount++;
        }
        
        if (combo > maxCombo) {
            maxCombo = combo;
        }
        comboTimer = comboTimeout;
        
        // Check for milestone announcements
        for (int i = COMBO_MILESTONES.length - 1; i >= 0; i--) {
            if (combo >= COMBO_MILESTONES[i] && previousCombo < COMBO_MILESTONES[i]) {
                currentAnnouncement = COMBO_MESSAGES[i];
                announcementTimer = ANNOUNCEMENT_DURATION;
                // Play sound with increasing pitch for higher milestones
                if (soundManager != null) {
                    float pitch = 1.0f + (i * 0.15f); // Increase pitch for higher combos
                    soundManager.playSound(SoundManager.Sound.COMBO_MILESTONE, pitch);
                }
                break;
            }
        }
    }
    
    public void resetCombo() {
        combo = 0;
        comboTimer = 0;
        comboMultiplier = 1.0;
        closeCallCount = 0;
        perfectDodgeCount = 0;
        totalGrazeValue = 0;
    }
    
    public int getCombo() {
        return combo;
    }
    
    public int getMaxCombo() {
        return maxCombo;
    }
    
    public double getMultiplier() {
        return comboMultiplier;
    }
    
    public float getTimeoutProgress() {
        if (combo == 0) return 0;
        return (float)comboTimer / comboTimeout;
    }
    
    public int getComboTimer() {
        return comboTimer;
    }
    
    public int getComboTimeout() {
        return comboTimeout;
    }
    
    public String getCurrentAnnouncement() {
        return currentAnnouncement;
    }
    
    public int getAnnouncementTimer() {
        return announcementTimer;
    }
    
    public int getCloseCallCount() {
        return closeCallCount;
    }
    
    public int getPerfectDodgeCount() {
        return perfectDodgeCount;
    }
    
    public int getTotalGrazeValue() {
        return totalGrazeValue;
    }
}
