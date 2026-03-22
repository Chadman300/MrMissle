import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * GlobalSaveData stores settings and data shared across all save slots.
 * - GPU acceleration settings (synced to all save files when changed)
 * - Global achievements (first-time unlocks, independent of per-save achievements)
 * Saved to saves/global.dat
 */
public class GlobalSaveData implements Serializable {
    private static final long serialVersionUID = 1L;

    // GPU Acceleration settings (global — synced across all saves)
    public boolean enableGPUAcceleration = false;
    public int gpuPipelineType = 0;   // 0=Auto, 1=OpenGL, 2=Direct3D
    public int bufferStrategyMode = 1; // 0=Double buffer, 1=Triple buffer

    // Global achievements (independent of per-save achievements)
    public List<String> globalUnlockedAchievements;

    // All-time leaderboard records: [difficulty ordinal][level index 0-27]
    // Persists across ALL save slots and survives save deletion — truly all-time records
    public LeaderboardRecord[][] leaderboardRecords;

    public long saveTimestamp;

    public GlobalSaveData() {
        globalUnlockedAchievements = new ArrayList<>();
        saveTimestamp = System.currentTimeMillis();
    }

    /**
     * Record a newly unlocked achievement globally (if not already present).
     * Returns true if this was the first global unlock for this achievement.
     */
    public boolean recordAchievement(String achievementId) {
        if (achievementId != null && !globalUnlockedAchievements.contains(achievementId)) {
            globalUnlockedAchievements.add(achievementId);
            return true;
        }
        return false;
    }

    /**
     * Check if an achievement has been globally unlocked.
     */
    public boolean isAchievementUnlocked(String achievementId) {
        return globalUnlockedAchievements.contains(achievementId);
    }

    /**
     * Get the number of globally unlocked achievements.
     */
    public int getUnlockedCount() {
        return globalUnlockedAchievements.size();
    }

    /**
     * Custom deserialization for backwards compatibility with future fields.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (globalUnlockedAchievements == null) {
            globalUnlockedAchievements = new ArrayList<>();
        }
        // Backwards compatibility: old saves without leaderboard data
        if (leaderboardRecords == null) {
            leaderboardRecords = new LeaderboardRecord[LeaderboardManager.DIFFICULTY_COUNT][LeaderboardManager.LEVEL_COUNT];
        }
    }
}
