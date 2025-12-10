import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class Game extends JPanel implements Runnable {
    // Game constants
    public static final int WIDTH;
    public static final int HEIGHT;
    private static final int FPS = 60;
    
    static {
        // Get screen dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        WIDTH = screenSize.width;
        HEIGHT = screenSize.height;
    }
    
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
    private List<Particle> particles;
    private List<BeamAttack> beamAttacks;
    
    // Input
    private boolean[] keys;
    
    // Animation
    private double gradientTime;
    
    // Visual effects
    private double screenShakeX;
    private double screenShakeY;
    private double screenShakeIntensity;
    
    // Combo system
    private int dodgeCombo;
    private int comboTimer;
    private static final int COMBO_TIMEOUT = 180; // 3 seconds
    
    // Boss mechanics
    private boolean bossVulnerable;
    private int vulnerabilityTimer;
    private static final int VULNERABILITY_DURATION = 1200; // 20 second window
    private boolean bossDeathAnimation;
    private int deathAnimationTimer;
    private static final int DEATH_ANIMATION_DURATION = 180; // 3 seconds
    private double bossDeathScale;
    private double bossDeathRotation;
    
    // Settings
    private int selectedSettingsItem;
    public static boolean enableGradientAnimation = true;
    public static boolean enableGrainEffect = false;
    public static int gradientQuality = 1; // 0=Low (1 layer), 1=Medium (2 layers), 2=High (3 layers)
    
    // Quit confirmation
    private int escapeTimer; // Timer for double-tap escape confirmation
    private static final int ESCAPE_TIMEOUT = 120; // 2 seconds to press escape again
    
    public Game() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        setDoubleBuffered(true); // Enable double buffering for smoother rendering
        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR)); // Crosshair cursor for fullscreen
        
        // Initialize systems
        keys = new boolean[256];
        bullets = new ArrayList<>();
        bulletPool = new ArrayList<>();
        particles = new ArrayList<>();
        beamAttacks = new ArrayList<>();
        gameData = new GameData();
        shopManager = new ShopManager(gameData);
        renderer = new Renderer(gameData, shopManager);
        
        // Initial state
        gameState = GameState.MENU;
        selectedStatItem = 0;
        selectedSettingsItem = 0;
        gradientTime = 0;
        screenShakeX = 0;
        screenShakeY = 0;
        screenShakeIntensity = 0;
        dodgeCombo = 0;
        comboTimer = 0;
        bossVulnerable = false;
        vulnerabilityTimer = 0;
        
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
                else if (key == KeyEvent.VK_P) gameState = GameState.SHOP; // P for Shop
                else if (key == KeyEvent.VK_ESCAPE) {
                    // Double-tap escape to quit
                    if (escapeTimer > 0) {
                        // Second press - quit
                        System.exit(0);
                    } else {
                        // First press - start timer
                        escapeTimer = ESCAPE_TIMEOUT;
                    }
                }
                break;
                
            case STATS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) selectedStatItem = Math.max(0, selectedStatItem - 1);
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) selectedStatItem = Math.min(3, selectedStatItem + 1);
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
                if (key == KeyEvent.VK_R) {
                    // Restart current level
                    startGame();
                } else if (key == KeyEvent.VK_ESCAPE) {
                    // Return to main menu
                    gameState = GameState.MENU;
                }
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
        particles.clear();
        currentBoss = new Boss(WIDTH / 2, 100, gameData.getCurrentLevel());
        gameData.setSurvivalTime(0);
        dodgeCombo = 0;
        comboTimer = 0;
        bossVulnerable = false;
        vulnerabilityTimer = 0;
        screenShakeIntensity = 0;
        bossDeathAnimation = false;
        deathAnimationTimer = 0;
        bossDeathScale = 1.0;
        bossDeathRotation = 0;
        escapeTimer = 0;
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
                
                // Update escape timer
                if (escapeTimer > 0) {
                    escapeTimer -= deltaTime;
                    if (escapeTimer < 0) escapeTimer = 0;
                }
                
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
        if (gameState != GameState.PLAYING) return;
        
        // Track survival and score (scaled by delta time) - only when player is alive
        if (player != null) {
            gameData.incrementSurvivalTime();
            gameData.addScore((int)deltaTime);
        }
        
        // Update screen shake
        if (screenShakeIntensity > 0) {
            screenShakeX = (Math.random() - 0.5) * screenShakeIntensity;
            screenShakeY = (Math.random() - 0.5) * screenShakeIntensity;
            screenShakeIntensity *= 0.9;
            if (screenShakeIntensity < 0.1) screenShakeIntensity = 0;
        } else {
            screenShakeX = 0;
            screenShakeY = 0;
        }
        
        // Update combo timer
        if (comboTimer > 0) {
            comboTimer -= deltaTime;
            if (comboTimer <= 0) {
                dodgeCombo = 0;
            }
        }
        
        // Update boss vulnerability
        if (bossVulnerable) {
            vulnerabilityTimer -= deltaTime;
            if (vulnerabilityTimer <= 0) {
                bossVulnerable = false;
            }
        }
        
        // Update player with delta time (only if alive)
        if (player != null) {
            player.update(keys, WIDTH, HEIGHT, deltaTime);
        }
        
        // Update particles
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.update(deltaTime);
            if (!p.isAlive()) {
                particles.remove(i);
            }
        }
        
        // Check if player hit boss (only vulnerable during special window)
        if (currentBoss != null && player != null && player.collidesWith(currentBoss) && !bossDeathAnimation) {
            if (bossVulnerable) {
                // Successful hit! Start death animation
                int winBonus = 1000 + (gameData.getCurrentLevel() * 500) + (dodgeCombo * 100);
                gameData.addScore(winBonus);
                
                // Start boss death animation
                bossDeathAnimation = true;
                deathAnimationTimer = DEATH_ANIMATION_DURATION;
                bossDeathScale = 1.0;
                bossDeathRotation = 0;
                
                // Make player disappear (missile hit)
                player = null;
                
                // Initial massive explosion
                screenShakeIntensity = 25;
                
                // Create massive fiery explosion particles
                for (int i = 0; i < 100; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double speed = 3 + Math.random() * 8;
                    Color fireColor;
                    double rand = Math.random();
                    if (rand < 0.4) {
                        fireColor = new Color(255, 100, 0); // Orange
                    } else if (rand < 0.7) {
                        fireColor = new Color(255, 200, 0); // Yellow
                    } else {
                        fireColor = new Color(255, 50, 0); // Red
                    }
                    particles.add(new Particle(
                        currentBoss.getX(), currentBoss.getY(),
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        fireColor, 50 + (int)(Math.random() * 30), 6,
                        Particle.ParticleType.SPARK
                    ));
                }
                
                // Multiple explosion rings
                for (int i = 0; i < 5; i++) {
                    particles.add(new Particle(
                        currentBoss.getX(), currentBoss.getY(), 0, 0,
                        new Color(255, 150 - i * 20, 0), 40 + i * 15, 40 + i * 25,
                        Particle.ParticleType.EXPLOSION
                    ));
                }
                
                return;
            } else {
                // Hit boss when not vulnerable - player dies
                screenShakeIntensity = 10;
                gameState = GameState.GAME_OVER;
                return;
            }
        }
        
        // Update boss death animation
        if (bossDeathAnimation) {
            deathAnimationTimer -= deltaTime;
            
            // Calculate animation progress (0 to 1)
            double progress = 1.0 - (deathAnimationTimer / (double)DEATH_ANIMATION_DURATION);
            
            // Boss shrinks and falls (scale decreases)
            bossDeathScale = 1.0 - (progress * 0.7); // Shrink to 30% size
            
            // Boss spins as it falls
            bossDeathRotation += 0.05 * deltaTime;
            
            // Continuous explosions during death
            if (Math.random() < 0.15 * deltaTime) {
                double offsetX = (Math.random() - 0.5) * 80 * bossDeathScale;
                double offsetY = (Math.random() - 0.5) * 80 * bossDeathScale;
                for (int i = 0; i < 15; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double speed = 1 + Math.random() * 4;
                    Color fireColor = Math.random() < 0.5 ? 
                        new Color(255, 150, 0) : new Color(255, 200, 50);
                    particles.add(new Particle(
                        currentBoss.getX() + offsetX, currentBoss.getY() + offsetY,
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        fireColor, 30, 4,
                        Particle.ParticleType.SPARK
                    ));
                }
            }
            
            // Continuous screen shake that decreases over time
            screenShakeIntensity = 15 * (1.0 - progress);
            
            // Smoke trails
            if (Math.random() < 0.3 * deltaTime) {
                particles.add(new Particle(
                    currentBoss.getX() + (Math.random() - 0.5) * 60,
                    currentBoss.getY() + (Math.random() - 0.5) * 60,
                    (Math.random() - 0.5) * 2, 2 + Math.random() * 3,
                    new Color(80, 80, 80, 150), 40, 8,
                    Particle.ParticleType.SPARK
                ));
            }
            
            // Final explosion and transition to win screen
            if (deathAnimationTimer <= 0) {
                // Final massive explosion
                for (int i = 0; i < 80; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double speed = 2 + Math.random() * 6;
                    Color fireColor = new Color(255, (int)(100 + Math.random() * 155), 0);
                    particles.add(new Particle(
                        currentBoss.getX(), currentBoss.getY(),
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        fireColor, 60, 8,
                        Particle.ParticleType.SPARK
                    ));
                }
                
                screenShakeIntensity = 20;
                gameState = GameState.WIN;
                bossDeathAnimation = false;
                return;
            }
        }
        
        // Boss becomes vulnerable periodically
        if (!bossVulnerable && currentBoss != null && Math.random() < 0.005 * deltaTime) {
            bossVulnerable = true;
            // Base duration + 60 frames (1 second) per upgrade level
            vulnerabilityTimer = VULNERABILITY_DURATION + (gameData.getActiveAttackWindowLevel() * 60);
            // Visual indicator - sparkles around boss
            for (int i = 0; i < 10; i++) {
                double angle = Math.random() * Math.PI * 2;
                double radius = 40 + Math.random() * 20;
                particles.add(new Particle(
                    currentBoss.getX() + Math.cos(angle) * radius,
                    currentBoss.getY() + Math.sin(angle) * radius,
                    0, -1,
                    new Color(235, 203, 139), 30, 3,
                    Particle.ParticleType.SPARK
                ));
            }
        }
        
        // Update boss with delta time (but not during death animation)
        if (currentBoss != null && !bossDeathAnimation) {
            currentBoss.update(bullets, player, WIDTH, HEIGHT, deltaTime);
            beamAttacks = currentBoss.getBeamAttacks();
        }
        
        // Check beam attack collisions (only if player exists)
        for (BeamAttack beam : beamAttacks) {
            if (player != null && beam.collidesWith(player)) {
                // Hit by beam - game over
                // Create death particles
                for (int j = 0; j < 20; j++) {
                    double angle = Math.random() * Math.PI * 2;
                    double speed = 1 + Math.random() * 3;
                    particles.add(new Particle(
                        player.getX(), player.getY(),
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        new Color(191, 97, 106), 30, 6,
                        Particle.ParticleType.SPARK
                    ));
                }
                screenShakeIntensity = 10;
                gameState = GameState.GAME_OVER;
                return;
            }
        }
        
        // Update bullets
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            
            // Apply bullet slow upgrade (reduced to 0.1% per level)
            if (gameData.getActiveBulletSlowLevel() > 0) {
                bullet.applySlow(0.999 - (gameData.getActiveBulletSlowLevel() * 0.0001));
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
            
            // Check collision with player (only if player exists)
            if (player != null && bullet.isActive() && bullet.collidesWith(player)) {
                // Lucky Dodge chance - phase through bullets
                int luckyDodgeLevel = gameData.getActiveLuckyDodgeLevel();
                if (luckyDodgeLevel > 0) {
                    double dodgeChance = luckyDodgeLevel * 0.05; // 5% per level
                    if (Math.random() < dodgeChance) {
                        // Lucky dodge! Trigger flicker animation
                        player.triggerFlicker();
                        bullets.remove(i);
                        returnBulletToPool(bullet);
                        
                        // Increment dodge combo
                        dodgeCombo++;
                        comboTimer = COMBO_TIMEOUT;
                        
                        // Add score based on combo
                        gameData.addScore(10 * dodgeCombo);
                        
                        // Create dodge particles
                        for (int j = 0; j < 8; j++) {
                            double angle = Math.PI * 2 * j / 8;
                            particles.add(new Particle(
                                player.getX(), player.getY(),
                                Math.cos(angle) * 2, Math.sin(angle) * 2,
                                new Color(163, 190, 140), 20, 5,
                                Particle.ParticleType.DODGE
                            ));
                        }
                        
                        continue;
                    }
                }
                
                // No dodge - game over
                // Create death particles
                for (int j = 0; j < 20; j++) {
                    double angle = Math.random() * Math.PI * 2;
                    double speed = 1 + Math.random() * 3;
                    particles.add(new Particle(
                        player.getX(), player.getY(),
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        new Color(191, 97, 106), 30, 6,
                        Particle.ParticleType.SPARK
                    ));
                }
                screenShakeIntensity = 10;
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
                renderer.drawMenu(g2d, WIDTH, HEIGHT, gradientTime, escapeTimer);
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
                // Apply screen shake
                g2d.translate(screenShakeX, screenShakeY);
                renderer.drawGame(g2d, WIDTH, HEIGHT, player, currentBoss, bullets, particles, beamAttacks, gameData.getCurrentLevel(), gradientTime, bossVulnerable, dodgeCombo, comboTimer > 0, bossDeathAnimation, bossDeathScale, bossDeathRotation);
                g2d.translate(-screenShakeX, -screenShakeY);
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
    public void selectNextStat() { selectedStatItem = Math.min(3, selectedStatItem + 1); }
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
