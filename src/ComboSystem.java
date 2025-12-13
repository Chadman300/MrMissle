public class ComboSystem {
    private int combo;
    private int comboTimer;
    private int maxCombo;
    private double comboMultiplier;
    private int comboTimeout;
    
    private static final int BASE_COMBO_TIMEOUT = 180; // 3 seconds at 60 FPS
    private static final double COMBO_MULTIPLIER_PER_LEVEL = 0.05; // 5% per combo level
    
    public ComboSystem() {
        this.combo = 0;
        this.maxCombo = 0;
        this.comboTimer = 0;
        this.comboMultiplier = 1.0;
        this.comboTimeout = BASE_COMBO_TIMEOUT;
    }
    
    public void update(double deltaTime, double comboTimeoutMultiplier) {
        comboTimeout = (int)(BASE_COMBO_TIMEOUT * comboTimeoutMultiplier);
        
        if (combo > 0) {
            comboTimer -= deltaTime;
            if (comboTimer <= 0) {
                resetCombo();
            }
        }
        
        // Update multiplier based on combo
        comboMultiplier = 1.0 + (Math.min(combo, 50) * COMBO_MULTIPLIER_PER_LEVEL);
    }
    
    public void addCombo() {
        combo++;
        if (combo > maxCombo) {
            maxCombo = combo;
        }
        comboTimer = comboTimeout;
    }
    
    public void resetCombo() {
        combo = 0;
        comboTimer = 0;
        comboMultiplier = 1.0;
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
}
