import interfaces.LeaderboardCallback;
import interfaces.LeaderboardEntry;
import interfaces.LeaderboardProvider;

/**
 * Manages local leaderboard records for all campaign levels across all difficulties.
 * 
 * AI CONTEXT:
 * - Follows the AchievementManager registry pattern (HashMap + recentlyUpdated tracking)
 * - Records stored in GlobalSaveData (persists across all save slots, survives deletion)
 * - 4 difficulties × 28 levels = 112 leaderboard slots
 * - submitTime() returns a LeaderboardResult indicating what happened
 * - recentResult holds the last submission result for the leaderboard animation screen
 * - Implements LeaderboardProvider for local use; can be replaced with Steam provider
 * 
 * SECURITY:
 * - Checksum validation on load (invalid records zeroed out)
 * - Range validation (reject times < 1s or > 1hr at 60fps) 
 * - Future: Steam leaderboards provide server-side anti-cheat
 * 
 * TO ADD STEAM LEADERBOARDS:
 * 1. Create SteamLeaderboardProvider implementing LeaderboardProvider
 * 2. Call setProvider(steamProvider) after Steam API initialization
 * 3. Local records continue to serve as cache/fallback
 */
public class LeaderboardManager implements LeaderboardProvider {

    /** Number of difficulty categories (Easy, Hard, Master, Ultra) */
    public static final int DIFFICULTY_COUNT = 4;

    /** Number of campaign levels */
    public static final int LEVEL_COUNT = 28;

    /** Result of a leaderboard submission */
    public enum LeaderboardResult {
        /** First time completing this level on this difficulty */
        FIRST_COMPLETION,
        /** Beat the previous best time */
        NEW_RECORD,
        /** Did not beat the previous best time */
        NO_IMPROVEMENT
    }

    /** All-time records: [difficulty ordinal][level index 0-27] */
    private LeaderboardRecord[][] records;

    /** Last submission result — read by the leaderboard animation screen */
    private LeaderboardResult recentResult;

    /** Level that was just completed (1-indexed, for display) */
    private int recentLevel;

    /** Difficulty of the recent completion */
    private int recentDifficulty;

    /** The record from the most recent submission (may be existing or new) */
    private LeaderboardRecord recentRecord;

    /** The previous best record before this submission (null if first clear or no change) */
    private LeaderboardRecord recentPreviousRecord;

    /** The player's completion time from the most recent submission (frames) */
    private int recentTimeInFrames;

    /** Optional external provider (e.g. Steam). Defaults to this (local). */
    private LeaderboardProvider externalProvider;

    public LeaderboardManager() {
        records = new LeaderboardRecord[DIFFICULTY_COUNT][LEVEL_COUNT];
    }

    /**
     * Submit a completion time for a campaign level.
     * Only records if it's a new best or first completion.
     * 
     * @param mode The game difficulty (determines which leaderboard category)
     * @param level Level number (1-indexed, 1–28)
     * @param timeInFrames Completion time in frames at 60fps
     * @param saveName Name of the save slot that achieved this time
     * @return Result indicating FIRST_COMPLETION, NEW_RECORD, or NO_IMPROVEMENT
     */
    public LeaderboardResult submitTime(GameMode mode, int level, int timeInFrames, String saveName) {
        // Validate inputs
        if (mode == null || level < 1 || level > LEVEL_COUNT) {
            recentResult = LeaderboardResult.NO_IMPROVEMENT;
            return recentResult;
        }

        int diffIdx = getDifficultyIndex(mode);
        if (diffIdx < 0 || diffIdx >= DIFFICULTY_COUNT) {
            recentResult = LeaderboardResult.NO_IMPROVEMENT;
            return recentResult;
        }

        // Range validation
        if (!LeaderboardRecord.isTimeInRange(timeInFrames)) {
            recentResult = LeaderboardResult.NO_IMPROVEMENT;
            return recentResult;
        }

        int levelIdx = level - 1;
        recentLevel = level;
        recentDifficulty = diffIdx;
        recentTimeInFrames = timeInFrames;

        LeaderboardRecord existing = records[diffIdx][levelIdx];

        if (existing == null) {
            // First completion
            LeaderboardRecord newRecord = new LeaderboardRecord(timeInFrames, saveName, levelIdx, diffIdx);
            records[diffIdx][levelIdx] = newRecord;
            recentRecord = newRecord;
            recentPreviousRecord = null;
            recentResult = LeaderboardResult.FIRST_COMPLETION;
        } else if (timeInFrames < existing.getTimeInFrames()) {
            // New record — beat previous best
            recentPreviousRecord = existing;
            LeaderboardRecord newRecord = new LeaderboardRecord(timeInFrames, saveName, levelIdx, diffIdx);
            records[diffIdx][levelIdx] = newRecord;
            recentRecord = newRecord;
            recentResult = LeaderboardResult.NEW_RECORD;
        } else {
            // No improvement
            recentRecord = existing;
            recentPreviousRecord = null;
            recentResult = LeaderboardResult.NO_IMPROVEMENT;
        }

        // Submit to external provider if available (e.g. Steam)
        if (externalProvider != null && externalProvider.isAvailable()) {
            String name = LeaderboardProvider.buildLeaderboardName(mode.name(), level);
            externalProvider.submitScore(name, timeInFrames);
        }

        return recentResult;
    }

    /**
     * Get the best record for a specific level and difficulty.
     * @param mode Game difficulty
     * @param level Level number (1-indexed)
     * @return The record, or null if no record exists
     */
    public LeaderboardRecord getRecord(GameMode mode, int level) {
        int diffIdx = getDifficultyIndex(mode);
        if (diffIdx < 0 || diffIdx >= DIFFICULTY_COUNT || level < 1 || level > LEVEL_COUNT) {
            return null;
        }
        return records[diffIdx][level - 1];
    }

    /**
     * Get the best time in frames for a specific level and difficulty.
     * @return Best time in frames, or -1 if no record exists
     */
    public int getBestTime(GameMode mode, int level) {
        LeaderboardRecord record = getRecord(mode, level);
        return record != null ? record.getTimeInFrames() : -1;
    }

    /** Check if a record exists for the given level and difficulty. */
    public boolean hasRecord(GameMode mode, int level) {
        return getRecord(mode, level) != null;
    }

    /**
     * Load records from GlobalSaveData, validating checksums.
     * Invalid records are silently discarded (zeroed out).
     */
    public void loadFromGlobal(GlobalSaveData globalData) {
        LeaderboardRecord[][] saved = globalData.leaderboardRecords;
        if (saved == null) {
            records = new LeaderboardRecord[DIFFICULTY_COUNT][LEVEL_COUNT];
            return;
        }

        records = new LeaderboardRecord[DIFFICULTY_COUNT][LEVEL_COUNT];
        for (int d = 0; d < Math.min(saved.length, DIFFICULTY_COUNT); d++) {
            if (saved[d] == null) continue;
            for (int l = 0; l < Math.min(saved[d].length, LEVEL_COUNT); l++) {
                LeaderboardRecord record = saved[d][l];
                if (record != null && record.isValid()) {
                    records[d][l] = record;
                } else if (record != null) {
                    System.err.println("Leaderboard: Invalid record detected for difficulty " + d + " level " + (l + 1) + " — discarding");
                }
            }
        }
    }

    /**
     * Save current records to GlobalSaveData for persistence.
     */
    public void saveToGlobal(GlobalSaveData globalData) {
        globalData.leaderboardRecords = records;
    }

    /** Clear all records (for debug/testing). */
    public void clearAll() {
        records = new LeaderboardRecord[DIFFICULTY_COUNT][LEVEL_COUNT];
        recentResult = null;
        recentRecord = null;
    }

    /**
     * Set an external leaderboard provider (e.g. Steam).
     * When set, submissions are forwarded to the external provider in addition to local storage.
     */
    public void setProvider(LeaderboardProvider provider) {
        this.externalProvider = provider;
    }

    // --- LeaderboardProvider implementation (local) ---

    @Override
    public void submitScore(String leaderboardName, int score) {
        // Parse leaderboard name to extract difficulty and level
        // Format: "campaign_{difficulty}_{level}"
        // This is a no-op for the local provider since submitTime() is the primary API
    }

    @Override
    public void requestTopScores(String leaderboardName, int count, LeaderboardCallback callback) {
        // For local provider, return the single best record
        // Parse name to find the record
        if (callback == null) return;

        // Parse "campaign_{difficulty}_{level}"
        String[] parts = leaderboardName.split("_");
        if (parts.length != 3) {
            callback.onScoresReceived(new LeaderboardEntry[0]);
            return;
        }

        GameMode mode = null;
        try {
            mode = GameMode.valueOf(parts[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            callback.onScoresReceived(new LeaderboardEntry[0]);
            return;
        }

        int level;
        try {
            level = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            callback.onScoresReceived(new LeaderboardEntry[0]);
            return;
        }

        LeaderboardRecord record = getRecord(mode, level);
        if (record != null) {
            LeaderboardEntry entry = new LeaderboardEntry(
                record.getSaveName(), record.getTimeInFrames(), 1);
            callback.onScoresReceived(new LeaderboardEntry[] { entry });
        } else {
            callback.onScoresReceived(new LeaderboardEntry[0]);
        }
    }

    @Override
    public boolean isAvailable() {
        return true; // Local provider is always available
    }

    // --- Getters for UI/animation ---

    /** Get the result of the most recent submission. */
    public LeaderboardResult getRecentResult() { return recentResult; }

    /** Get the level number (1-indexed) of the most recent submission. */
    public int getRecentLevel() { return recentLevel; }

    /** Get the difficulty index of the most recent submission. */
    public int getRecentDifficulty() { return recentDifficulty; }

    /** Get the record associated with the most recent submission. */
    public LeaderboardRecord getRecentRecord() { return recentRecord; }

    /** Get the previous best record before the most recent submission (null if first clear). */
    public LeaderboardRecord getRecentPreviousRecord() { return recentPreviousRecord; }

    /** Get the player's actual completion time from the most recent submission. */
    public int getRecentTimeInFrames() { return recentTimeInFrames; }

    /** Get the full records array (for testing/debug). */
    LeaderboardRecord[][] getAllRecords() { return records; }

    /** Clear the recent result after the leaderboard screen has displayed it. */
    public void clearRecentResult() {
        recentResult = null;
    }

    // --- Helpers ---

    /**
     * Map a GameMode to a difficulty index (0-3).
     * Supports future Ultra mode by mapping ordinal values.
     */
    private static int getDifficultyIndex(GameMode mode) {
        // Map by ordinal: EASY=0, HARD=1, MASTER=2, (future ULTRA=3)
        return mode.ordinal();
    }

    /**
     * Get a display name for a difficulty index.
     */
    public static String getDifficultyName(int difficultyIndex) {
        GameMode[] modes = GameMode.values();
        if (difficultyIndex >= 0 && difficultyIndex < modes.length) {
            return modes[difficultyIndex].getDisplayName();
        }
        return "Unknown";
    }
}
