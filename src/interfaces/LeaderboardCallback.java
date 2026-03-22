package interfaces;

/**
 * Callback for asynchronous leaderboard score retrieval.
 * Used by LeaderboardProvider.requestTopScores().
 * 
 * For local leaderboards, this is called synchronously.
 * For Steam leaderboards, this would be called when the Steam API responds.
 */
public interface LeaderboardCallback {
    /**
     * Called when leaderboard entries are available.
     * 
     * @param entries Array of leaderboard entries (name, score, rank), or null on error
     */
    void onScoresReceived(LeaderboardEntry[] entries);
}
