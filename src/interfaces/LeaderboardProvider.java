package interfaces;

/**
 * Abstraction layer for leaderboard submission and retrieval.
 * Allows the game to work with local leaderboards by default, and seamlessly
 * switch to Steam leaderboards when integrated.
 * 
 * AI CONTEXT:
 * - Default implementation: LocalLeaderboardProvider (wraps LeaderboardManager)
 * - Future implementation: SteamLeaderboardProvider (uses Steamworks SDK)
 * - Leaderboard names follow format: "campaign_{difficulty}_{level}" 
 *   e.g. "campaign_hard_14", "campaign_easy_1"
 * - Scores are completion times in frames (lower = better)
 * 
 * TO INTEGRATE STEAM:
 * 1. Create SteamLeaderboardProvider implementing this interface
 * 2. Use ISteamUserStats FindOrCreateLeaderboard / UploadLeaderboardScore
 * 3. Set LeaderboardManager's provider to the Steam implementation
 * 4. Local records still serve as cache/fallback when offline
 */
public interface LeaderboardProvider {

    /**
     * Submit a score (completion time in frames) to the given leaderboard.
     * Lower scores are better (fastest time wins).
     * 
     * @param leaderboardName Unique leaderboard ID (e.g. "campaign_hard_14")
     * @param score Completion time in frames (at 60fps)
     */
    void submitScore(String leaderboardName, int score);

    /**
     * Request the top scores for a leaderboard. Results delivered via callback.
     * 
     * @param leaderboardName Unique leaderboard ID
     * @param count Maximum number of entries to retrieve
     * @param callback Receives the results asynchronously (or synchronously for local)
     */
    void requestTopScores(String leaderboardName, int count, LeaderboardCallback callback);

    /**
     * Check if this provider is available and ready to accept submissions.
     * Local provider always returns true. Steam provider returns false when offline.
     */
    boolean isAvailable();

    /**
     * Build the standard leaderboard name for a campaign level + difficulty.
     * Format: "campaign_{difficulty}_{level}" where difficulty is lowercase name
     * and level is 1-indexed.
     *
     * @param modeName Lowercase difficulty name (e.g. "easy", "hard", "master")
     * @param level Level number (1-indexed)
     */
    static String buildLeaderboardName(String modeName, int level) {
        return "campaign_" + modeName.toLowerCase() + "_" + level;
    }
}
