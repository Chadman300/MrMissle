import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class Game extends JPanel implements Runnable {
    // Game constants
    public static final int WIDTH = 1200;
    public static final int HEIGHT = 800;
    private static final int FPS = 60;
    
    // Game state
    private Thread gameThread;
    private boolean running;
    private GameState gameState;
    private int selectedStatItem;
    
    // Core systems
    private GameData gameData;
    private ShopManager shopManager;
    private Renderer renderer;
    
    // Game objects
    private Player player;
    private Boss currentBoss;
    private List<Bullet> bullets;
    private List<Bullet> bulletPool; // Pool for recycling bullets
    
    // Input
    private boolean[] keys;
    
    // Animation
    private double gradientTime;
    
    // Settings
    private int selectedSettingsItem;
    public static boolean enableGradientAnimation = true;
    public static boolean enableGrainEffect = false;
    public static int gradientQuality = 1; // 0=Low (1 layer), 1=Medium (2 layers), 2=High (3 layers)
    
    public Game() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        setDoubleBuffered(true); // Enable double buffering for smoother rendering
        
        // Initialize systems
        keys = new boolean[256];
        bullets = new ArrayList<>();
        bulletPool = new ArrayList<>();
        gameData = new GameData();
        shopManager = new ShopManager(gameData);
        renderer = new Renderer(gameData, shopManager);
        
        // Initial state
        gameState = GameState.MENU;
        selectedStatItem = 0;
        selectedSettingsItem = 0;
        gradientTime = 0;
        
        // Setup input
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() < keys.length) {
                    keys[e.getKeyCode()] = true;
                }
                handleKeyPress(e);
            }
            
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() < keys.length) {
                    keys[e.getKeyCode()] = false;
                }
            }
        });
    }
    
    private void handleKeyPress(KeyEvent e) {
        int key = e.getKeyCode();
        
        switch (gameState) {
            case MENU:
                if (key == KeyEvent.VK_SPACE) gameState = GameState.LEVEL_SELECT;
                else if (key == KeyEvent.VK_I) gameState = GameState.INFO;
                else if (key == KeyEvent.VK_S) gameState = GameState.STATS;
                else if (key == KeyEvent.VK_O) gameState = GameState.SETTINGS; // O for Options/Settings
                break;
                
            case STATS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) selectedStatItem = Math.max(0, selectedStatItem - 1);
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) selectedStatItem = Math.min(2, selectedStatItem + 1);
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) gameData.adjustUpgrade(selectedStatItem, -1);
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) gameData.adjustUpgrade(selectedStatItem, 1);
                else if (key == KeyEvent.VK_ESCAPE) gameState = GameState.MENU;
                break;
                
            case SETTINGS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) selectedSettingsItem = Math.max(0, selectedSettingsItem - 1);
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) selectedSettingsItem = Math.min(2, selectedSettingsItem + 1);
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    toggleSetting(selectedSettingsItem);
                }
                else if (key == KeyEvent.VK_SPACE) toggleSetting(selectedSettingsItem);
                else if (key == KeyEvent.VK_ESCAPE) gameState = GameState.MENU;
                break;
                
            case INFO:
                if (key == KeyEvent.VK_ESCAPE) gameState = GameState.MENU;
                break;
                
            case LEVEL_SELECT:
                if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) selectPreviousLevel();
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) selectNextLevel();
                else if (key == KeyEvent.VK_SPACE) startSelectedLevel();
                else if (key == KeyEvent.VK_ESCAPE) gameState = GameState.MENU;
                break;
                
            case PLAYING:
                // Player movement handled in Player.update()
                break;
                
            case SHOP:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) shopManager.selectPrevious();
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) shopManager.selectNext();
                else if (key == KeyEvent.VK_SPACE) {
                    int selected = shopManager.getSelectedShopItem();
                    if (selected == 0) {
                        // Continue to next level
                        startGame();
                    } else {
                        shopManager.purchaseItem(selected);
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) startGame();
                break;
                
            case GAME_OVER:
                if (key == KeyEvent.VK_R) {
                    int survivalReward = gameData.getSurvivalTime() / 60;
                    gameData.addRunMoney(survivalReward);
                    gameData.addTotalMoney(survivalReward);
                    gameData.setScore(0);
                    gameData.setRunMoney(0);
                    gameData.setSurvivalTime(0);
                    startGame();
                } else if (key == KeyEvent.VK_SPACE) {
                    gameState = GameState.MENU;
                }
                break;
                
            case WIN:
                if (key == KeyEvent.VK_SPACE) {
                    // Unlock next level
                    int currentLevel = gameData.getCurrentLevel();
                    gameData.setMaxUnlockedLevel(Math.max(gameData.getMaxUnlockedLevel(), currentLevel + 1));
                    
                    // Award money
                    int bossReward = 50 + (currentLevel * 10);
                    if (!gameData.getDefeatedBosses()[currentLevel - 1]) {
                        gameData.setBossDefeated(currentLevel - 1, true);
                        bossReward += 100;
                    }
                    gameData.addRunMoney(bossReward);
                    gameData.addTotalMoney(bossReward);
                    
                    gameData.setCurrentLevel(currentLevel + 1);
                    gameState = GameState.SHOP;
                }
                break;
        }
    }
    
    private void selectPreviousLevel() {
        gameData.setCurrentLevel(Math.max(1, gameData.getCurrentLevel() - 1));
    }
    
    private void selectNextLevel() {
        gameData.setCurrentLevel(Math.min(gameData.getMaxUnlockedLevel(), gameData.getCurrentLevel() + 1));
    }
    
    private void startSelectedLevel() {
        if (gameData.getCurrentLevel() <= gameData.getMaxUnlockedLevel()) {
            startGame();
        }
    }
    
    private void startGame() {
        gameState = GameState.PLAYING;
        player = new Player(WIDTH / 2, HEIGHT - 100, gameData.getActiveSpeedLevel());
        bullets.clear();
        currentBoss = new Boss(WIDTH / 2, 100, gameData.getCurrentLevel());
        gameData.setSurvivalTime(0);
    }
    
    public void start() {
        if (gameThread == null) {
            running = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }
    
    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double nsPerTick = 1000000000.0 / FPS;
        double delta = 0;
        
        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;
            
            if (delta >= 1) {
                double deltaTime = delta; // Actual delta time for frame-independent updates
                update(deltaTime);
                gradientTime += 0.02 * deltaTime; // Animate gradient with delta time
                repaint();
                delta--;
            }
            
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void update(double deltaTime) {
        if (gameState != GameState.PLAYING || player == null) return;
        
        // Track survival and score (scaled by delta time)
        gameData.incrementSurvivalTime();
        gameData.addScore((int)deltaTime);
        
        // Update player with delta time
        player.update(keys, WIDTH, HEIGHT, deltaTime);
        
        // Check if player hit boss
        if (currentBoss != null && player.collidesWith(currentBoss)) {
            gameData.addScore(1000 + (gameData.getCurrentLevel() * 500));
            gameState = GameState.WIN;
            return;
        }
        
        // Update boss with delta time
        if (currentBoss != null) {
            currentBoss.update(bullets, player, WIDTH, HEIGHT, deltaTime);
        }
        
        // Update bullets
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            
            // Apply bullet slow upgrade (much less aggressive: 0.99 - 0.97)
            if (gameData.getActiveBulletSlowLevel() > 0) {
                bullet.applySlow(0.99 - (gameData.getActiveBulletSlowLevel() * 0.01));
            }
            
            bullet.update(player, WIDTH, HEIGHT, deltaTime);
            
            // Check if splitting bullet should split
            if (bullet.shouldSplit()) {
                bullet.markAsSplit();
                double baseAngle = Math.atan2(bullet.getVY(), bullet.getVX());
                for (int j = 0; j < 4; j++) {
                    double angle = baseAngle + (Math.PI / 2 * j);
                    // Use pooled bullet if available
                    Bullet newBullet = getBulletFromPool();
                    newBullet.reset(bullet.getX(), bullet.getY(), 
                                   Math.cos(angle) * 3, Math.sin(angle) * 3, 
                                   Bullet.BulletType.FAST);
                    bullets.add(newBullet);
                }
            }
            
            // Remove off-screen bullets and return to pool
            if (bullet.isOffScreen(WIDTH, HEIGHT)) {
                bullets.remove(i);
                returnBulletToPool(bullet);
                continue;
            }
            
            // Check collision with player
            if (bullet.isActive() && bullet.collidesWith(player)) {
                // Lucky Dodge chance - phase through bullets
                int luckyDodgeLevel = gameData.getActiveLuckyDodgeLevel();
                if (luckyDodgeLevel > 0) {
                    double dodgeChance = luckyDodgeLevel * 0.05; // 5% per level
                    if (Math.random() < dodgeChance) {
                        // Lucky dodge! Trigger flicker animation
                        player.triggerFlicker();
                        bullets.remove(i);
                        returnBulletToPool(bullet);
                        continue;
                    }
                }
                
                // No dodge - game over
                gameState = GameState.GAME_OVER;
                return;
            }
        }
    }
    
    // Bullet pooling methods
    private Bullet getBulletFromPool() {
        if (bulletPool.isEmpty()) {
            return new Bullet(0, 0, 0, 0);
        }
        return bulletPool.remove(bulletPool.size() - 1);
    }
    
    private void returnBulletToPool(Bullet bullet) {
        if (bulletPool.size() < 500) { // Cap pool size
            bulletPool.add(bullet);
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        switch (gameState) {
            case MENU:
                renderer.drawMenu(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case INFO:
                renderer.drawInfo(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case STATS:
                renderer.drawStats(g2d, WIDTH, HEIGHT, gradientTime);
                renderer.drawStatsUpgrades(g2d, WIDTH, selectedStatItem);
                break;
            case SETTINGS:
                renderer.drawSettings(g2d, WIDTH, HEIGHT, selectedSettingsItem, gradientTime);
                break;
            case LEVEL_SELECT:
                renderer.drawLevelSelect(g2d, WIDTH, HEIGHT, gameData.getCurrentLevel(), gameData.getMaxUnlockedLevel(), gradientTime);
                break;
            case PLAYING:
                renderer.drawGame(g2d, WIDTH, HEIGHT, player, currentBoss, bullets, gameData.getCurrentLevel(), gradientTime);
                break;
            case GAME_OVER:
                renderer.drawGameOver(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case WIN:
                renderer.drawWin(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case SHOP:
                renderer.drawShop(g2d, WIDTH, HEIGHT, gradientTime);
                break;
        }
    }
    
    // Public getters for InputHandler (if needed)
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState state) { this.gameState = state; }
    public void selectPreviousStat() { selectedStatItem = Math.max(0, selectedStatItem - 1); }
    public void selectNextStat() { selectedStatItem = Math.min(2, selectedStatItem + 1); }
    public void decreaseUpgrade() { gameData.adjustUpgrade(selectedStatItem, -1); }
    public void increaseUpgrade() { gameData.adjustUpgrade(selectedStatItem, 1); }
    public void selectPreviousShopItem() { shopManager.selectPrevious(); }
    public void selectNextShopItem() { shopManager.selectNext(); }
    public void purchaseSelectedItem() { 
        int selected = shopManager.getSelectedShopItem();
        if (selected == 0) startGame();
        else shopManager.purchaseItem(selected);
    }
    
    private void toggleSetting(int settingIndex) {
        switch (settingIndex) {
            case 0: // Gradient Animation
                enableGradientAnimation = !enableGradientAnimation;
                break;
            case 1: // Gradient Quality
                gradientQuality = (gradientQuality + 1) % 3; // Cycle through 0, 1, 2
                break;
            case 2: // Grain Effect
                enableGrainEffect = !enableGrainEffect;
                break;
        }
    }
}
