package steam;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUserStats;
import com.codedisaster.steamworks.SteamUserStatsCallback;
import com.codedisaster.steamworks.SteamLeaderboardHandle;
import com.codedisaster.steamworks.SteamLeaderboardEntriesHandle;
import com.codedisaster.steamworks.SteamID;

import interfaces.AchievementProvider;

/**
 * Steam-backed AchievementProvider using steamworks4j.
 *
 * AI CONTEXT:
 * - Achievement IDs must match Steam "API Name" exactly (configured in Steamworks
 *   Partner site). They already do — see AchievementManager.initializeAchievements().
 * - SteamAPI.init() must succeed before constructing this. Wraps SteamUserStats.
 * - storeStats() is what actually triggers the Steam toast / Cloud sync.
 * - This class only compiles when lib/steamworks4j-*.jar is on the classpath.
 *   Game.java loads it via reflection so missing-jar builds still run.
 */
public class SteamAchievementProvider implements AchievementProvider {

    private final SteamUserStats userStats;
    private boolean ready;

    public SteamAchievementProvider() {
        // Empty callback — we only need pass/fail logging.
        SteamUserStatsCallback callback = new SteamUserStatsCallback() {
            @Override public void onUserStatsReceived(long gameId, SteamID steamID, SteamResult result) {
                if (result == SteamResult.OK) {
                    ready = true;
                    System.out.println("[Steam] User stats received");
                } else {
                    System.err.println("[Steam] User stats request failed: " + result);
                }
            }
            @Override public void onUserStatsStored(long gameId, SteamResult result) {
                if (result != SteamResult.OK) {
                    System.err.println("[Steam] storeStats failed: " + result);
                }
            }
            @Override public void onUserAchievementStored(long gameId, boolean isGroupAchievement,
                                                         String achievementName, int curProgress, int maxProgress) {
                System.out.println("[Steam] Achievement stored: " + achievementName);
            }
            @Override public void onLeaderboardFindResult(SteamLeaderboardHandle leaderboard, boolean found) {}
            @Override public void onLeaderboardScoresDownloaded(SteamLeaderboardHandle leaderboard,
                                                                SteamLeaderboardEntriesHandle entries, int numEntries) {}
            @Override public void onLeaderboardScoreUploaded(boolean success, SteamLeaderboardHandle leaderboard,
                                                             int score, boolean scoreChanged, int globalRankNew,
                                                             int globalRankPrevious) {}
            @Override public void onGlobalStatsReceived(long gameId, SteamResult result) {}
        };
        this.userStats = new SteamUserStats(callback);
        // Kick off async fetch of current stats so isUnlocked can answer truthfully.
        userStats.requestCurrentStats();
        // Optimistic — most calls work even before requestCurrentStats completes.
        this.ready = true;
    }

    @Override
    public void unlock(String achievementId) {
        if (achievementId == null || !SteamAPI.isSteamRunning()) return;
        try {
            if (userStats.setAchievement(achievementId)) {
                userStats.storeStats();
            } else {
                System.err.println("[Steam] setAchievement returned false for: " + achievementId
                        + " (unknown API Name in Steamworks?)");
            }
        } catch (Exception e) {
            System.err.println("[Steam] unlock(" + achievementId + ") threw: " + e.getMessage());
        }
    }

    @Override
    public boolean isUnlocked(String achievementId) {
        if (achievementId == null) return false;
        try {
            return userStats.isAchieved(achievementId, false);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isReady() {
        return ready && SteamAPI.isSteamRunning();
    }

    /**
     * Static entry point Game.java calls via reflection.
     * Returns a ready provider or throws on failure (caller catches).
     */
    public static AchievementProvider tryCreate() throws SteamException {
        if (!SteamAPI.isSteamRunning(true)) {
            if (!SteamAPI.init()) {
                throw new SteamException("SteamAPI.init() returned false (no steam_appid.txt? Steam not running?)");
            }
        }
        return new SteamAchievementProvider();
    }

    /** Pump Steam callbacks. Call once per frame from the game loop. */
    public static void runCallbacks() {
        if (SteamAPI.isSteamRunning()) {
            SteamAPI.runCallbacks();
        }
    }

    /** Shutdown Steam API. Call once on exit. */
    public static void shutdown() {
        try {
            SteamAPI.shutdown();
        } catch (Exception ignored) {}
    }
}
