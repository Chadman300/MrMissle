package interfaces;

/**
 * Abstraction layer for achievement unlock notification.
 * Allows the local achievement system to optionally notify Steam (or another
 * backend) without the rest of the codebase needing to know about it.
 *
 * AI CONTEXT:
 * - The local AchievementManager is always the source of truth for progress.
 * - This provider is a notifier only — called once per achievement on the
 *   first global unlock (see AchievementManager.recordGlobalUnlocks).
 * - Achievement IDs MUST match the Steam "API Name" exactly so no translation
 *   table is needed (see AchievementManager.initializeAchievements()).
 */
public interface AchievementProvider {

    /**
     * Notify the backend that an achievement has been unlocked.
     * Must be idempotent — calling twice with the same id is safe.
     */
    void unlock(String achievementId);

    /**
     * Query whether the backend reports the given achievement as unlocked.
     * Used during one-time bulk resync. Implementations that cannot answer
     * synchronously should return false (the worst case is a redundant unlock).
     */
    boolean isUnlocked(String achievementId);

    /**
     * True if the backend is initialized and accepting calls.
     */
    boolean isReady();
}
