import java.io.Serializable;

/**
 * Stores a single leaderboard record for a specific level + difficulty combination.
 * 
 * AI CONTEXT:
 * - One record per level per difficulty (28 levels × 4 difficulties = 112 total)
 * - Stored in GlobalSaveData (persists across all save slots, survives save deletion)
 * - Checksum provides tamper detection for casual hex-editing
 * - Designed for future Steam leaderboard integration via LeaderboardProvider interface
 * 
 * FIELDS:
 * - timeInFrames: Best completion time at 60fps (primary ranking metric)
 * - timestamp: When the record was set (System.currentTimeMillis)
 * - saveName: Which save slot set this record (cosmetic label only)
 * - checksum: Integrity check computed from time + level + difficulty + salt
 */
public class LeaderboardRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Salt for checksum computation — prevents trivial checksum forgery */
    private static final int CHECKSUM_SALT = 0x4D53_4C4E; // "MSLN"

    /** Minimum valid time: 1 second at 60fps */
    public static final int MIN_VALID_FRAMES = 60;

    /** Maximum valid time: 1 hour at 60fps */
    public static final int MAX_VALID_FRAMES = 216000;

    private int timeInFrames;
    private long timestamp;
    private String saveName;
    private int checksum;

    /** Level index (0-27) this record is for — used in checksum verification */
    private int levelIndex;

    /** Difficulty ordinal this record is for — used in checksum verification */
    private int difficultyOrdinal;

    public LeaderboardRecord(int timeInFrames, String saveName, int levelIndex, int difficultyOrdinal) {
        this.timeInFrames = timeInFrames;
        this.timestamp = System.currentTimeMillis();
        this.saveName = saveName;
        this.levelIndex = levelIndex;
        this.difficultyOrdinal = difficultyOrdinal;
        this.checksum = computeChecksum(timeInFrames, levelIndex, difficultyOrdinal);
    }

    /**
     * Compute integrity checksum from record data.
     * Prevents casual hex-editing of save files. Not meant to stop determined attackers
     * — real anti-cheat is handled by Steam leaderboards.
     */
    private static int computeChecksum(int timeInFrames, int levelIndex, int difficultyOrdinal) {
        int hash = timeInFrames * 31;
        hash += levelIndex * 17;
        hash += difficultyOrdinal * 13;
        hash ^= CHECKSUM_SALT;
        // Mix bits to make pattern less obvious
        hash = ((hash >>> 16) ^ hash) * 0x45d9f3b;
        hash = ((hash >>> 16) ^ hash) * 0x45d9f3b;
        hash = (hash >>> 16) ^ hash;
        return hash;
    }

    /**
     * Validate this record's integrity.
     * @return true if checksum matches and time is within valid range
     */
    public boolean isValid() {
        if (timeInFrames < MIN_VALID_FRAMES || timeInFrames > MAX_VALID_FRAMES) {
            return false;
        }
        return checksum == computeChecksum(timeInFrames, levelIndex, difficultyOrdinal);
    }

    /** Get time in seconds (double precision for display). */
    public double getTimeSeconds() {
        return timeInFrames / 60.0;
    }

    /** Format time as M:SS.cc (minutes:seconds.centiseconds). */
    public String formatTime() {
        double seconds = getTimeSeconds();
        int mins = (int)(seconds / 60);
        int secs = (int)(seconds % 60);
        int centis = (int)((seconds % 1) * 100);
        return String.format("%d:%02d.%02d", mins, secs, centis);
    }

    /** Format a time value (in milliseconds) as M:SS.cc — used for Steam-sourced scores. */
    public static String formatTimeMs(int ms) {
        if (ms < 0) ms = 0;
        double seconds = ms / 1000.0;
        int mins = (int)(seconds / 60);
        int secs = (int)(seconds % 60);
        int centis = (int)((seconds % 1) * 100);
        return String.format("%d:%02d.%02d", mins, secs, centis);
    }

    /** Check if a given time (in frames) is within the valid range. */
    public static boolean isTimeInRange(int timeInFrames) {
        return timeInFrames >= MIN_VALID_FRAMES && timeInFrames <= MAX_VALID_FRAMES;
    }

    // Getters
    public int getTimeInFrames() { return timeInFrames; }
    public long getTimestamp() { return timestamp; }
    public String getSaveName() { return saveName; }
    public int getLevelIndex() { return levelIndex; }
    public int getDifficultyOrdinal() { return difficultyOrdinal; }
}
