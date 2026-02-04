import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ResumeState stores all game state needed for cross-session resume.
 * All fields are primitives or serializable collections to ensure disk persistence.
 * This captures the essential state without storing non-serializable objects like BufferedImage.
 */
public class ResumeState implements Serializable {
    private static final long serialVersionUID = 2L;
    
    // Whether there's a valid resume state
    public boolean isValid = false;
    
    // Player state
    public double playerX, playerY;
    public double playerVX, playerVY;
    
    // Boss state
    public double bossX, bossY;
    public double bossVX, bossVY;
    public int bossLevel;
    public boolean bossMega;
    public int bossShootTimer;
    public int bossPatternType;
    public double bossSpiralRotation;
    public double bossHealth; // Represented as percentage (0.0 to 1.0)
    public int bossHitCount; // How many times boss has been hit
    
    // Bullets (stored as arrays of primitives for performance)
    public double[] bulletX;
    public double[] bulletY;
    public double[] bulletVX;
    public double[] bulletVY;
    public int[] bulletType; // Ordinal of BulletType enum
    public double[] bulletAge;
    public int[] bulletWarningTime;
    public int bulletCount;
    
    // Game state
    public int level;
    public int survivalTime;
    public int score;
    public int runMoney;
    public boolean bossVulnerable;
    public int vulnerabilityTimer;
    public int invulnerabilityTimer;
    public boolean tookDamageThisBoss;
    public int dodgeCombo;
    public boolean shieldActive;
    public int shieldHits;
    public int comboTimer;
    public int extraLives;
    
    // Risk contract state
    public int riskContractType;
    public boolean riskContractActive;
    public double riskContractMultiplier;
    
    // Combo system state
    public int comboCount;
    public int comboMultiplier;
    
    public ResumeState() {
        // Initialize with empty/default values
        bulletX = new double[0];
        bulletY = new double[0];
        bulletVX = new double[0];
        bulletVY = new double[0];
        bulletType = new int[0];
        bulletAge = new double[0];
        bulletWarningTime = new int[0];
        bulletCount = 0;
    }
    
    /**
     * Capture bullet state from a list of bullets
     */
    public void captureBullets(List<Bullet> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            bulletCount = 0;
            bulletX = new double[0];
            bulletY = new double[0];
            bulletVX = new double[0];
            bulletVY = new double[0];
            bulletType = new int[0];
            bulletAge = new double[0];
            bulletWarningTime = new int[0];
            return;
        }
        
        bulletCount = bullets.size();
        bulletX = new double[bulletCount];
        bulletY = new double[bulletCount];
        bulletVX = new double[bulletCount];
        bulletVY = new double[bulletCount];
        bulletType = new int[bulletCount];
        bulletAge = new double[bulletCount];
        bulletWarningTime = new int[bulletCount];
        
        for (int i = 0; i < bulletCount; i++) {
            Bullet b = bullets.get(i);
            bulletX[i] = b.getX();
            bulletY[i] = b.getY();
            bulletVX[i] = b.getVX();
            bulletVY[i] = b.getVY();
            bulletType[i] = b.getType().ordinal();
            bulletAge[i] = b.getAge();
            bulletWarningTime[i] = b.getWarningTime();
        }
    }
    
    /**
     * Restore bullets from saved state
     */
    public List<Bullet> restoreBullets() {
        List<Bullet> bullets = new ArrayList<>();
        if (bulletCount == 0) return bullets;
        
        Bullet.BulletType[] types = Bullet.BulletType.values();
        for (int i = 0; i < bulletCount; i++) {
            Bullet.BulletType type = types[bulletType[i]];
            Bullet b = new Bullet(bulletX[i], bulletY[i], bulletVX[i], bulletVY[i], type);
            b.setAge(bulletAge[i]);
            b.setWarningTime(bulletWarningTime[i]);
            bullets.add(b);
        }
        return bullets;
    }
    
    /**
     * Capture player state
     */
    public void capturePlayer(Player player) {
        if (player == null) return;
        playerX = player.getX();
        playerY = player.getY();
        playerVX = player.getVX();
        playerVY = player.getVY();
    }
    
    /**
     * Capture boss state
     */
    public void captureBoss(Boss boss, int hitCount) {
        if (boss == null) return;
        bossX = boss.getX();
        bossY = boss.getY();
        bossVX = boss.getVX();
        bossVY = boss.getVY();
        bossLevel = boss.getLevel();
        bossMega = boss.isMegaBoss();
        bossShootTimer = boss.getShootTimer();
        bossPatternType = boss.getPatternType();
        bossSpiralRotation = boss.getSpiralRotation();
        bossHitCount = hitCount;
        // Calculate health percentage (boss takes ~10-15 hits based on level)
        int maxHits = boss.isMegaBoss() ? 15 + boss.getLevel() / 2 : 10 + boss.getLevel() / 3;
        bossHealth = 1.0 - ((double)hitCount / maxHits);
    }
}
