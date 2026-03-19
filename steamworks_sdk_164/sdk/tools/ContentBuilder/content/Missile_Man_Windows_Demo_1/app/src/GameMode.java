import java.awt.Color;

/**
 * Represents the difficulty mode for a save file.
 * Mode is selected when creating a new save and is locked for the lifetime of that save.
 * 
 * EASY   - Relaxed bosses (slower attacks, longer recovery, fewer/slower bullets,
 *          narrower beams, weaker shockwaves/twirls, gentler level scaling).
 *          Progress is saved on death (no roguelike reset).
 * HARD   - Full boss challenge, but progress is saved on death (no roguelike reset).
 * MASTER - Full boss challenge with roguelike progression reset on death (the original experience).
 */
public enum GameMode implements java.io.Serializable {
    EASY(
        "Easy Mode",
        "Relaxed bosses, progress saved",
        new Color(163, 190, 140),   // Green
        1.4,   // shootIntervalScale    — slower shooting
        0.75,  // assaultDurationScale  — shorter aggression windows
        1.3,   // recoveryDurationScale — longer rest periods
        1.5,   // beamTimerScale        — delayed beam attacks
        0.7,   // bulletCountScale      — 30% fewer bullets per pattern
        0.8,   // bulletSpeedScale      — 20% slower bullets
        0.7,   // beamWidthScale        — 30% narrower beams
        0.7,   // shockwaveScale        — 30% weaker shockwave & twirl
        0.6    // levelScaleMultiplier  — level-dependent formulas ramp slower
    ),
    HARD(
        "Hard Mode",
        "Full challenge, progress saved",
        new Color(235, 203, 139),   // Gold
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0
    ),
    MASTER(
        "Master Mode",
        "Full challenge, roguelike resets",
        new Color(191, 97, 106),    // Red
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0,
        1.0
    );

    private final String displayName;
    private final String description;
    private final Color color;
    private final double shootIntervalScale;
    private final double assaultDurationScale;
    private final double recoveryDurationScale;
    private final double beamTimerScale;
    private final double bulletCountScale;
    private final double bulletSpeedScale;
    private final double beamWidthScale;
    private final double shockwaveScale;
    private final double levelScaleMultiplier;

    GameMode(String displayName, String description, Color color,
             double shootIntervalScale, double assaultDurationScale,
             double recoveryDurationScale, double beamTimerScale,
             double bulletCountScale, double bulletSpeedScale,
             double beamWidthScale, double shockwaveScale,
             double levelScaleMultiplier) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.shootIntervalScale = shootIntervalScale;
        this.assaultDurationScale = assaultDurationScale;
        this.recoveryDurationScale = recoveryDurationScale;
        this.beamTimerScale = beamTimerScale;
        this.bulletCountScale = bulletCountScale;
        this.bulletSpeedScale = bulletSpeedScale;
        this.beamWidthScale = beamWidthScale;
        this.shockwaveScale = shockwaveScale;
        this.levelScaleMultiplier = levelScaleMultiplier;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Color getColor() { return color; }
    public double getShootIntervalScale() { return shootIntervalScale; }
    public double getAssaultDurationScale() { return assaultDurationScale; }
    public double getRecoveryDurationScale() { return recoveryDurationScale; }
    public double getBeamTimerScale() { return beamTimerScale; }
    public double getBulletCountScale() { return bulletCountScale; }
    public double getBulletSpeedScale() { return bulletSpeedScale; }
    public double getBeamWidthScale() { return beamWidthScale; }
    public double getShockwaveScale() { return shockwaveScale; }
    public double getLevelScaleMultiplier() { return levelScaleMultiplier; }

    /** Whether this mode resets progression (level, missiles) on death. */
    public boolean resetsOnDeath() {
        return this == MASTER;
    }

    /** Get the splash text shown on the main menu (Minecraft-style). */
    public String getSplashText() {
        switch (this) {
            case EASY:   return "Easy Mode!";
            case HARD:   return "Hard Mode!";
            case MASTER: return "Master Mode!";
            default:     return "Master Mode!";
        }
    }
}
