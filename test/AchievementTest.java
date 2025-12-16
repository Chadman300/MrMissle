import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AchievementTest {
    
    @Test
    public void testAchievementCreation() {
        Achievement ach = new Achievement(
            Achievement.AchievementType.BOSS_KILLS,
            "First Blood",
            "Defeat your first boss",
            1
        );
        
        assertFalse(ach.isUnlocked());
        assertEquals("First Blood", ach.getName());
        assertEquals("Defeat your first boss", ach.getDescription());
        assertEquals(0, ach.getProgress());
        assertEquals(1, ach.getRequirement());
    }
    
    @Test
    public void testProgressTracking() {
        Achievement ach = new Achievement(
            Achievement.AchievementType.BOSS_KILLS,
            "Boss Slayer",
            "Defeat 10 bosses",
            10
        );
        
        ach.addProgress(5);
        assertEquals(5, ach.getProgress());
        assertEquals(50.0f, ach.getProgressPercent(), 0.01f);
        assertFalse(ach.isUnlocked());
        
        ach.addProgress(5);
        assertEquals(10, ach.getProgress());
        assertEquals(100.0f, ach.getProgressPercent(), 0.01f);
        assertTrue(ach.isUnlocked());
    }
    
    @Test
    public void testSetProgress() {
        Achievement ach = new Achievement(
            Achievement.AchievementType.REACH_LEVEL,
            "Level Master",
            "Reach level 20",
            20
        );
        
        ach.setProgress(15);
        assertEquals(15, ach.getProgress());
        assertFalse(ach.isUnlocked());
        
        ach.setProgress(20);
        assertTrue(ach.isUnlocked());
    }
    
    @Test
    public void testUnlockAchievement() {
        Achievement ach = new Achievement(
            Achievement.AchievementType.NO_DAMAGE,
            "Perfect",
            "Complete a level without taking damage",
            1
        );
        
        assertFalse(ach.isUnlocked());
        ach.unlock();
        assertTrue(ach.isUnlocked());
    }
    
    @Test
    public void testProgressClamping() {
        Achievement ach = new Achievement(
            Achievement.AchievementType.GRAZE_COUNT,
            "Close Call",
            "Graze 100 bullets",
            100
        );
        
        ach.addProgress(150); // Over requirement
        assertEquals(100, ach.getProgress()); // Should be clamped to requirement
        assertEquals(100.0f, ach.getProgressPercent(), 0.01f);
    }
}
