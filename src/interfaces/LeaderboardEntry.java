package interfaces;

/**
 * A single entry in a leaderboard result set.
 * Used by LeaderboardCallback to deliver query results.
 */
public class LeaderboardEntry {
    private final String playerName;
    private final int score;
    private final int rank;

    public LeaderboardEntry(String playerName, int score, int rank) {
        this.playerName = playerName;
        this.score = score;
        this.rank = rank;
    }

    public String getPlayerName() { return playerName; }
    public int getScore() { return score; }
    public int getRank() { return rank; }
}
