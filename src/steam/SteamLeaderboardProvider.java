package steam;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUserStats;
import com.codedisaster.steamworks.SteamUserStatsCallback;
import com.codedisaster.steamworks.SteamLeaderboardHandle;
import com.codedisaster.steamworks.SteamLeaderboardEntriesHandle;
import com.codedisaster.steamworks.SteamLeaderboardEntry;
import com.codedisaster.steamworks.SteamFriends;

import interfaces.LeaderboardCallback;
import interfaces.LeaderboardEntry;
import interfaces.LeaderboardProvider;

/**
 * Steam-backed LeaderboardProvider using steamworks4j.
 *
 * AI CONTEXT:
 * - Leaderboard names follow LeaderboardProvider.buildLeaderboardName for campaign
 *   (e.g. "campaign_master_5"). The single endless board is "endless_total_levels".
 * - Steam requires a SteamLeaderboardHandle (obtained async) before upload/download.
 *   We cache handles and queue pending operations until the find callback fires.
 * - Campaign boards are uploaded as milliseconds (frames * 1000 / 60) so Steam's
 *   built-in Time-Milliseconds display formats correctly in the overlay.
 * - Endless board is uploaded as raw total-levels-beaten (descending sort).
 * - This class only compiles when lib/steamworks4j-*.jar is on the classpath.
 *   Game.java loads it via reflection so missing-jar builds still run.
 */
public class SteamLeaderboardProvider implements LeaderboardProvider {

    /** Endless mode leaderboard name. Must match Steamworks Partner site. */
    public static final String ENDLESS_LEADERBOARD = "endless_total_levels";

    private final SteamUserStats userStats;
    private final SteamFriends friends;
    private final Map<String, SteamLeaderboardHandle> handles = new HashMap<>();
    private final Map<String, List<PendingUpload>> pendingUploads = new HashMap<>();
    private final Map<String, List<PendingDownload>> pendingDownloads = new HashMap<>();
    private final Map<Long, String> handleNames = new HashMap<>(); // handle -> name lookup
    private final Map<Long, LeaderboardCallback> pendingCallbacks = new HashMap<>(); // entries handle -> cb
    private final Map<Long, Integer> pendingCallbackCounts = new HashMap<>();

    private static final class PendingUpload {
        final int score;
        PendingUpload(int score) { this.score = score; }
    }

    private static final class PendingDownload {
        final int count;
        final LeaderboardCallback callback;
        PendingDownload(int count, LeaderboardCallback cb) { this.count = count; this.callback = cb; }
    }

    public SteamLeaderboardProvider() {
        this.friends = new SteamFriends(null);
        SteamUserStatsCallback callback = new SteamUserStatsCallback() {
            @Override public void onUserStatsReceived(long gameId, SteamID steamID, SteamResult result) {}
            @Override public void onUserStatsStored(long gameId, SteamResult result) {}
            @Override public void onUserAchievementStored(long gameId, boolean group, String name, int cur, int max) {}

            @Override
            public void onLeaderboardFindResult(SteamLeaderboardHandle leaderboard, boolean found) {
                if (!found || leaderboard == null) {
                    System.err.println("[Steam] Leaderboard find failed (not found / null handle)");
                    return;
                }
                String name = userStats.getLeaderboardName(leaderboard);
                handles.put(name, leaderboard);
                handleNames.put(leaderboard.handle, name);

                // Flush queued uploads.
                List<PendingUpload> ups = pendingUploads.remove(name);
                if (ups != null) {
                    for (PendingUpload u : ups) {
                        uploadNow(leaderboard, u.score);
                    }
                }
                // Flush queued downloads.
                List<PendingDownload> downs = pendingDownloads.remove(name);
                if (downs != null) {
                    for (PendingDownload d : downs) {
                        downloadNow(leaderboard, d.count, d.callback);
                    }
                }
            }

            @Override
            public void onLeaderboardScoresDownloaded(SteamLeaderboardHandle leaderboard,
                                                     SteamLeaderboardEntriesHandle entries, int numEntries) {
                LeaderboardCallback cb = pendingCallbacks.remove(entries.handle);
                Integer requested = pendingCallbackCounts.remove(entries.handle);
                if (cb == null) return;
                int max = (requested != null) ? Math.min(requested, numEntries) : numEntries;
                List<LeaderboardEntry> out = new ArrayList<>(max);
                SteamLeaderboardEntry tmp = new SteamLeaderboardEntry();
                for (int i = 0; i < max; i++) {
                    if (userStats.getDownloadedLeaderboardEntry(entries, i, tmp, null, 0)) {
                        String persona = friends.getFriendPersonaName(tmp.getSteamIDUser());
                        out.add(new LeaderboardEntry(persona != null ? persona : "Unknown",
                                tmp.getScore(), tmp.getGlobalRank()));
                    }
                }
                cb.onScoresReceived(out.toArray(new LeaderboardEntry[0]));
            }

            @Override
            public void onLeaderboardScoreUploaded(boolean success, SteamLeaderboardHandle leaderboard,
                                                  int score, boolean scoreChanged, int globalRankNew,
                                                  int globalRankPrevious) {
                String name = handleNames.get(leaderboard.handle);
                if (success) {
                    System.out.println("[Steam] Uploaded score to " + name + ": " + score
                            + (scoreChanged ? " (rank " + globalRankNew + ")" : " (no change)"));
                } else {
                    System.err.println("[Steam] Upload failed for " + name);
                }
            }

            @Override public void onGlobalStatsReceived(long gameId, SteamResult result) {}
        };
        this.userStats = new SteamUserStats(callback);
    }

    @Override
    public void submitScore(String leaderboardName, int score) {
        if (leaderboardName == null || !SteamAPI.isSteamRunning()) return;
        // Convert campaign scores from frames -> milliseconds for nicer Steam display.
        int wireScore = leaderboardName.startsWith("campaign_")
                ? (int)((long)score * 1000L / 60L)
                : score;
        SteamLeaderboardHandle handle = handles.get(leaderboardName);
        if (handle != null) {
            uploadNow(handle, wireScore);
        } else {
            pendingUploads.computeIfAbsent(leaderboardName, k -> new ArrayList<>()).add(new PendingUpload(wireScore));
            userStats.findOrCreateLeaderboard(leaderboardName,
                    leaderboardName.startsWith("campaign_")
                            ? SteamUserStats.LeaderboardSortMethod.Ascending
                            : SteamUserStats.LeaderboardSortMethod.Descending,
                    leaderboardName.startsWith("campaign_")
                            ? SteamUserStats.LeaderboardDisplayType.TimeMilliSeconds
                            : SteamUserStats.LeaderboardDisplayType.Numeric);
        }
    }

    @Override
    public void requestTopScores(String leaderboardName, int count, LeaderboardCallback callback) {
        if (callback == null) return;
        if (leaderboardName == null || !SteamAPI.isSteamRunning()) {
            callback.onScoresReceived(new LeaderboardEntry[0]);
            return;
        }
        SteamLeaderboardHandle handle = handles.get(leaderboardName);
        if (handle != null) {
            downloadNow(handle, count, callback);
        } else {
            pendingDownloads.computeIfAbsent(leaderboardName, k -> new ArrayList<>())
                    .add(new PendingDownload(count, callback));
            userStats.findLeaderboard(leaderboardName);
        }
    }

    @Override
    public boolean isAvailable() {
        return SteamAPI.isSteamRunning();
    }

    private void uploadNow(SteamLeaderboardHandle handle, int score) {
        try {
            userStats.uploadLeaderboardScore(handle,
                    SteamUserStats.LeaderboardUploadScoreMethod.KeepBest,
                    score, new int[0]);
        } catch (Exception e) {
            System.err.println("[Steam] uploadLeaderboardScore threw: " + e.getMessage());
        }
    }

    private void downloadNow(SteamLeaderboardHandle handle, int count, LeaderboardCallback cb) {
        try {
            SteamLeaderboardEntriesHandle entries = userStats.downloadLeaderboardEntries(handle,
                    SteamUserStats.LeaderboardDataRequest.Global, 1, Math.max(1, count));
            if (entries != null) {
                pendingCallbacks.put(entries.handle, cb);
                pendingCallbackCounts.put(entries.handle, count);
            } else {
                cb.onScoresReceived(new LeaderboardEntry[0]);
            }
        } catch (Exception e) {
            System.err.println("[Steam] downloadLeaderboardEntries threw: " + e.getMessage());
            cb.onScoresReceived(new LeaderboardEntry[0]);
        }
    }

    /** Static entry point Game.java calls via reflection. Assumes SteamAPI already init. */
    public static LeaderboardProvider tryCreate() {
        return new SteamLeaderboardProvider();
    }
}
