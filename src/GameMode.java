import java.awt.Color;

/**
 * Represents the difficulty mode for a save file.
 * Mode is selected when creating a new save and is locked for the lifetime of that save.
 * 
 * EASY   - Relaxed bosses (slower attacks, longer recovery), same player progression as Hard.
 *          Progress is saved on death (no roguelike reset).
 * HARD   - Full boss challenge, but progress is saved on death (no roguelike reset).
 * MASTER - Full boss challenge with roguelike progression reset on death (the original experience).
 */
public enum GameMode implements java.io.Serializable {
    EASY(
        "Easy Mode",
        "Relaxed bosses, progress saved",
        new Color(163, 190, 140),   // Green
        1.4,   // shootIntervalScale   — slower shooting
        0.75,  // assaultDurationScale — shorter aggression windows
        1.3,   // recoveryDurationScale — longer rest periods
        1.5    // beamTimerScale        — delayed beam attacks
    ),
    HARD(
        "Hard Mode",
        "Full challenge, progress saved",
        new Color(235, 203, 139),   // Gold
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
        1.0
    );

    private final String displayName;
    private final String description;
    private final Color color;
    private final double shootIntervalScale;
    private final double assaultDurationScale;
    private final double recoveryDurationScale;
    private final double beamTimerScale;

    GameMode(String displayName, String description, Color color,
             double shootIntervalScale, double assaultDurationScale,
             double recoveryDurationScale, double beamTimerScale) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.shootIntervalScale = shootIntervalScale;
        this.assaultDurationScale = assaultDurationScale;
        this.recoveryDurationScale = recoveryDurationScale;
        this.beamTimerScale = beamTimerScale;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Color getColor() { return color; }
    public double getShootIntervalScale() { return shootIntervalScale; }
    public double getAssaultDurationScale() { return assaultDurationScale; }
    public double getRecoveryDurationScale() { return recoveryDurationScale; }
    public double getBeamTimerScale() { return beamTimerScale; }

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
