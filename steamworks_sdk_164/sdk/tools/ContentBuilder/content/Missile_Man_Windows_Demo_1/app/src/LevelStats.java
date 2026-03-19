public class LevelStats {
    private int timeInFrames;
    private int dodges;
    private int bulletsSpawned;
    private int missilesUsed;
    private int damageTaken;
    private int perfectDodges;
    private int nearMisses;
    private int maxCombo;
    private double closestCall; // Closest bullet distance achieved
    private double totalGrazeDistance; // Sum of all graze distances
    private int grazeCount; // Number of grazes for averaging
    private int missileSurvivalBonus; // Score bonus for surviving with missiles
    
    public LevelStats() {
        this.timeInFrames = 0;
        this.dodges = 0;
        this.bulletsSpawned = 0;
        this.missilesUsed = 0;
        this.damageTaken = 0;
        this.perfectDodges = 0;
        this.nearMisses = 0;
        this.maxCombo = 0;
        this.closestCall = 999.0; // Start with high value
        this.totalGrazeDistance = 0;
        this.grazeCount = 0;
        this.missileSurvivalBonus = 0;
    }
    
    // Getters
    public int getTimeInFrames() { return timeInFrames; }
    public int getDodges() { return dodges; }
    public int getBulletsSpawned() { return bulletsSpawned; }
    public int getMissilesUsed() { return missilesUsed; }
    public int getDamageTaken() { return damageTaken; }
    public int getPerfectDodges() { return perfectDodges; }
    public int getNearMisses() { return nearMisses; }
    public int getMaxCombo() { return maxCombo; }
    public double getClosestCall() { return closestCall; }
    public int getMissileSurvivalBonus() { return missileSurvivalBonus; }
    
    // Setters
    public void setTimeInFrames(int time) { this.timeInFrames = time; }
    public void setDodges(int dodges) { this.dodges = dodges; }
    public void setBulletsSpawned(int bullets) { this.bulletsSpawned = bullets; }
    public void setMissilesUsed(int missiles) { this.missilesUsed = missiles; }
    public void setDamageTaken(int damage) { this.damageTaken = damage; }
    public void setPerfectDodges(int perfect) { this.perfectDodges = perfect; }
    public void setNearMisses(int nearMisses) { this.nearMisses = nearMisses; }
    public void setMaxCombo(int maxCombo) { this.maxCombo = maxCombo; }
    public void setClosestCall(double closest) { this.closestCall = closest; }
    public void setMissileSurvivalBonus(int bonus) { this.missileSurvivalBonus = bonus; }
    
    // Incrementers
    public void incrementDodges() { this.dodges++; }
    public void incrementBulletsSpawned() { this.bulletsSpawned++; }
    public void incrementMissilesUsed() { this.missilesUsed++; }
    public void incrementDamageTaken() { this.damageTaken++; }
    public void incrementPerfectDodges() { this.perfectDodges++; }
    public void incrementNearMisses() { this.nearMisses++; }
    
    // Update closest call if this distance is closer
    public void updateClosestCall(double distance) {
        if (distance < this.closestCall) {
            this.closestCall = distance;
        }
    }
    
    // Track graze distance for risk calculation
    public void addGrazeDistance(double distance) {
        this.totalGrazeDistance += distance;
        this.grazeCount++;
    }
    
    // Calculate risk percentage (0-100, higher = riskier play)
    public int getRiskPercentage() {
        if (grazeCount == 0) return 0;
        double avgDistance = totalGrazeDistance / grazeCount;
        // Grazes only register between player collision radius (10px) and
        // GRAZE_DISTANCE (25px), so normalize within that actual band.
        double minGrazeDist = 10.0; // player radius (SIZE/2)
        double maxGrazeDist = 25.0; // GRAZE_DISTANCE
        double normalized = (avgDistance - minGrazeDist) / (maxGrazeDist - minGrazeDist);
        double risk = (1.0 - normalized) * 100.0;
        return Math.max(0, Math.min(100, (int)risk));
    }
    
    // Reset for new level
    public void reset() {
        this.timeInFrames = 0;
        this.dodges = 0;
        this.bulletsSpawned = 0;
        this.missilesUsed = 0;
        this.damageTaken = 0;
        this.perfectDodges = 0;
        this.nearMisses = 0;
        this.maxCombo = 0;
        this.closestCall = 999.0;
        this.totalGrazeDistance = 0;
        this.grazeCount = 0;
        this.missileSurvivalBonus = 0;
    }
    
    // Copy constructor for saving stats
    public LevelStats copy() {
        LevelStats copy = new LevelStats();
        copy.timeInFrames = this.timeInFrames;
        copy.dodges = this.dodges;
        copy.bulletsSpawned = this.bulletsSpawned;
        copy.missilesUsed = this.missilesUsed;
        copy.damageTaken = this.damageTaken;
        copy.perfectDodges = this.perfectDodges;
        copy.nearMisses = this.nearMisses;
        copy.maxCombo = this.maxCombo;
        copy.closestCall = this.closestCall;
        copy.totalGrazeDistance = this.totalGrazeDistance;
        copy.grazeCount = this.grazeCount;
        copy.missileSurvivalBonus = this.missileSurvivalBonus;
        return copy;
    }
    
    // Add stats from another LevelStats (for cumulative tracking)
    public void addStats(LevelStats other) {
        this.timeInFrames += other.timeInFrames;
        this.dodges += other.dodges;
        this.bulletsSpawned += other.bulletsSpawned;
        this.missilesUsed += other.missilesUsed;
        this.damageTaken += other.damageTaken;
        this.perfectDodges += other.perfectDodges;
        this.nearMisses += other.nearMisses;
        if (other.maxCombo > this.maxCombo) this.maxCombo = other.maxCombo;
        if (other.closestCall < this.closestCall) this.closestCall = other.closestCall;
        this.totalGrazeDistance += other.totalGrazeDistance;
        this.grazeCount += other.grazeCount;
    }
}
