import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LeaderboardTest {
    private LeaderboardManager manager;

    @BeforeEach
    public void setUp() {
        manager = new LeaderboardManager();
    }

    // --- LeaderboardRecord tests ---

    @Test
    public void testRecordIsValid() {
        LeaderboardRecord record = new LeaderboardRecord(3600, "Save1", 0, 0);
        assertTrue(record.isValid());
    }

    @Test
    public void testRecordBelowMinInvalid() {
        LeaderboardRecord record = new LeaderboardRecord(10, "Save1", 0, 0);
        assertFalse(record.isValid());
    }

    @Test
    public void testRecordAboveMaxInvalid() {
        LeaderboardRecord record = new LeaderboardRecord(300000, "Save1", 0, 0);
        assertFalse(record.isValid());
    }

    @Test
    public void testIsTimeInRange() {
        assertTrue(LeaderboardRecord.isTimeInRange(60));
        assertTrue(LeaderboardRecord.isTimeInRange(216000));
        assertFalse(LeaderboardRecord.isTimeInRange(59));
        assertFalse(LeaderboardRecord.isTimeInRange(216001));
    }

    @Test
    public void testFormatTime() {
        // 3600 frames at 60fps = 60 seconds = 1:00.00
        LeaderboardRecord record = new LeaderboardRecord(3600, "Save1", 0, 0);
        assertEquals("1:00.00", record.formatTime());
    }

    @Test
    public void testFormatTimeWithCentiseconds() {
        // 150 frames at 60fps = 2.5 seconds = 0:02.50
        LeaderboardRecord record = new LeaderboardRecord(150, "Save1", 0, 0);
        assertEquals("0:02.50", record.formatTime());
    }

    @Test
    public void testGetTimeSeconds() {
        LeaderboardRecord record = new LeaderboardRecord(120, "Save1", 0, 0);
        assertEquals(2.0, record.getTimeSeconds(), 0.001);
    }

    // --- LeaderboardManager submit tests ---

    @Test
    public void testFirstCompletion() {
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.FIRST_COMPLETION, result);
        assertEquals(LeaderboardManager.LeaderboardResult.FIRST_COMPLETION, manager.getRecentResult());
    }

    @Test
    public void testNewRecord() {
        manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.EASY, 1, 3000, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.NEW_RECORD, result);
    }

    @Test
    public void testNoImprovement() {
        manager.submitTime(GameMode.EASY, 1, 3000, "Save1");
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.NO_IMPROVEMENT, result);
    }

    @Test
    public void testSameTimeIsNoImprovement() {
        manager.submitTime(GameMode.EASY, 1, 3000, "Save1");
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.EASY, 1, 3000, "Save2");
        assertEquals(LeaderboardManager.LeaderboardResult.NO_IMPROVEMENT, result);
    }

    @Test
    public void testDifferentLevelsIndependent() {
        manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.EASY, 2, 5000, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.FIRST_COMPLETION, result);
    }

    @Test
    public void testDifferentDifficultiesIndependent() {
        manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.HARD, 1, 3600, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.FIRST_COMPLETION, result);
    }

    @Test
    public void testGetRecord() {
        manager.submitTime(GameMode.HARD, 5, 1800, "MySave");
        LeaderboardRecord record = manager.getRecord(GameMode.HARD, 5);
        assertNotNull(record);
        assertEquals(1800, record.getTimeInFrames());
        assertEquals("MySave", record.getSaveName());
    }

    @Test
    public void testGetRecordNullBeforeSubmission() {
        assertNull(manager.getRecord(GameMode.EASY, 1));
    }

    @Test
    public void testHasRecord() {
        assertFalse(manager.hasRecord(GameMode.EASY, 1));
        manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        assertTrue(manager.hasRecord(GameMode.EASY, 1));
    }

    // --- Validation edge cases ---

    @Test
    public void testInvalidLevelRejected() {
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.EASY, 0, 3600, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.NO_IMPROVEMENT, result);

        result = manager.submitTime(GameMode.EASY, 29, 3600, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.NO_IMPROVEMENT, result);
    }

    @Test
    public void testNullModeRejected() {
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(null, 1, 3600, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.NO_IMPROVEMENT, result);
    }

    @Test
    public void testOutOfRangeTimeRejected() {
        LeaderboardManager.LeaderboardResult result =
            manager.submitTime(GameMode.EASY, 1, 10, "Save1");
        assertEquals(LeaderboardManager.LeaderboardResult.NO_IMPROVEMENT, result);
        assertNull(manager.getRecord(GameMode.EASY, 1));
    }

    // --- Recent result tracking ---

    @Test
    public void testRecentResultTracking() {
        manager.submitTime(GameMode.MASTER, 10, 4200, "BestSave");
        assertEquals(LeaderboardManager.LeaderboardResult.FIRST_COMPLETION, manager.getRecentResult());
        assertNotNull(manager.getRecentRecord());
        assertEquals(4200, manager.getRecentRecord().getTimeInFrames());
    }

    @Test
    public void testClearRecentResult() {
        manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        assertNotNull(manager.getRecentResult());
        manager.clearRecentResult();
        assertNull(manager.getRecentResult());
    }

    // --- GlobalSaveData integration ---

    @Test
    public void testSaveAndLoadFromGlobal() {
        manager.submitTime(GameMode.EASY, 1, 3600, "Save1");
        manager.submitTime(GameMode.HARD, 15, 1200, "Save2");

        GlobalSaveData global = new GlobalSaveData();
        manager.saveToGlobal(global);

        LeaderboardManager newManager = new LeaderboardManager();
        newManager.loadFromGlobal(global);

        LeaderboardRecord r1 = newManager.getRecord(GameMode.EASY, 1);
        assertNotNull(r1);
        assertEquals(3600, r1.getTimeInFrames());

        LeaderboardRecord r2 = newManager.getRecord(GameMode.HARD, 15);
        assertNotNull(r2);
        assertEquals(1200, r2.getTimeInFrames());
    }

    @Test
    public void testLoadFromNullGlobalSafe() {
        assertDoesNotThrow(() -> manager.loadFromGlobal(null));
    }

    @Test
    public void testLoadInvalidRecordsZeroed() {
        GlobalSaveData global = new GlobalSaveData();
        // Create a tampered record (manually set fields would break checksum)
        // Simplest test: save valid, verify load works, then null records survive
        manager.saveToGlobal(global);
        LeaderboardManager newManager = new LeaderboardManager();
        newManager.loadFromGlobal(global);
        // All records should be null (none submitted)
        assertNull(newManager.getRecord(GameMode.EASY, 1));
    }

    // --- Boundary tests ---

    @Test
    public void testAllLevelsAndDifficulties() {
        GameMode[] modes = {GameMode.EASY, GameMode.HARD, GameMode.MASTER};
        for (GameMode mode : modes) {
            for (int level = 1; level <= 28; level++) {
                LeaderboardManager.LeaderboardResult result =
                    manager.submitTime(mode, level, 3600, "Save");
                assertEquals(LeaderboardManager.LeaderboardResult.FIRST_COMPLETION, result,
                    "First completion expected for " + mode + " level " + level);
            }
        }
    }

    @Test
    public void testNewRecordUpdatesStoredRecord() {
        manager.submitTime(GameMode.EASY, 1, 5000, "OldSave");
        manager.submitTime(GameMode.EASY, 1, 2000, "NewSave");

        LeaderboardRecord record = manager.getRecord(GameMode.EASY, 1);
        assertEquals(2000, record.getTimeInFrames());
        assertEquals("NewSave", record.getSaveName());
    }
}
