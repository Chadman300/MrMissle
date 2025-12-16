import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GameDataTest {
    private GameData gameData;
    
    @BeforeEach
    public void setUp() {
        gameData = new GameData();
    }
    
    @Test
    public void testInitialState() {
        assertEquals(0, gameData.getScore());
        assertEquals(0, gameData.getTotalMoney());
        assertEquals(0, gameData.getRunMoney());
        assertEquals(1, gameData.getCurrentLevel());
        assertEquals(1, gameData.getMaxUnlockedLevel());
    }
    
    @Test
    public void testScoreManagement() {
        gameData.addScore(100);
        assertEquals(100, gameData.getScore());
        
        gameData.addScore(50);
        assertEquals(150, gameData.getScore());
        
        gameData.setScore(0);
        assertEquals(0, gameData.getScore());
    }
    
    @Test
    public void testMoneyManagement() {
        gameData.addTotalMoney(500);
        assertEquals(500, gameData.getTotalMoney());
        
        gameData.addRunMoney(100);
        assertEquals(100, gameData.getRunMoney());
        
        gameData.setTotalMoney(gameData.getTotalMoney() - 200);
        assertEquals(300, gameData.getTotalMoney());
    }
    
    @Test
    public void testLevelProgression() {
        gameData.setCurrentLevel(3);
        assertEquals(3, gameData.getCurrentLevel());
        
        gameData.setMaxUnlockedLevel(5);
        assertEquals(5, gameData.getMaxUnlockedLevel());
    }
    
    @Test
    public void testUpgrades() {
        assertEquals(0, gameData.getActiveSpeedLevel());
        
        gameData.adjustUpgrade(0, 1); // Speed upgrade
        assertEquals(1, gameData.getActiveSpeedLevel());
        
        gameData.adjustUpgrade(0, 1);
        assertEquals(2, gameData.getActiveSpeedLevel());
        
        gameData.adjustUpgrade(0, -1);
        assertEquals(1, gameData.getActiveSpeedLevel());
        
        // Test lower bound
        gameData.adjustUpgrade(0, -5);
        assertEquals(0, gameData.getActiveSpeedLevel());
    }
    
    @Test
    public void testSurvivalTime() {
        assertEquals(0, gameData.getSurvivalTime());
        
        gameData.setSurvivalTime(120);
        assertEquals(120, gameData.getSurvivalTime());
    }
}
