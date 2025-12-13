import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private int selectedMenuItem; // For main menu navigation
    private double levelSelectScroll; // Scroll offset for level select
    
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
    private List<Particle> particlePool; // Pool for recycling particles
    private List<BeamAttack> beamAttacks;
    
    // Particle limits for performance
    private static final int MAX_PARTICLES = 500;
    
    // Cached colors for performance
    private static final Color IMPACT_WHITE = new Color(255, 255, 255);
    private static final Color IMPACT_YELLOW = new Color(255, 255, 150);
    private static final Color IMPACT_RING = new Color(255, 255, 200);
    private static final Color FIRE_ORANGE = new Color(255, 100, 0);
    private static final Color FIRE_YELLOW = new Color(255, 200, 0);
    private static final Color FIRE_RED = new Color(255, 50, 0);
    private static final Color SMOKE_GRAY = new Color(80, 80, 80, 150);
    private static final Color BOSS_FIRE = new Color(255, 150, 0);
    private static final Color BOSS_FIRE_BRIGHT = new Color(255, 200, 50);
    private static final Color VULNERABILITY_GOLD = new Color(235, 203, 139);
    private static final Color WARNING_RED = new Color(191, 97, 106);
    private static final Color PLAYER_DEATH_RED = new Color(191, 97, 106);
    private static final Color DODGE_GREEN = new Color(163, 190, 140);
    
    // Cached math constants
    private static final double TWO_PI = Math.PI * 2;
    
    // Spatial grid for bullet collision optimization
    private static final int GRID_CELL_SIZE = 50;
    private Map<Integer, List<Bullet>> bulletGrid;
    
    // Player trail effect
    private int trailSpawnTimer;
    
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
    private int invulnerabilityTimer; // Prevents boss from going vulnerable at level start
    private static final int VULNERABILITY_DURATION = 1200; // 20 second window
    private static final int INVULNERABILITY_DURATION = 150; // 2.5 seconds at start (changed from 300/5 seconds)
    private boolean bossDeathAnimation;
    private int deathAnimationTimer;
    private static final int DEATH_ANIMATION_DURATION = 180; // 3 seconds
    private double bossDeathScale;
    private double bossDeathRotation;
    
    // Settings
    private int selectedSettingsItem;
    public static boolean enableGradientAnimation = true;
    public static boolean enableGrainEffect = false;
    public static boolean enableParticles = true;
    public static boolean enableShadows = true;
    public static boolean enableBloom = true;
    public static boolean enableMotionBlur = false;
    public static boolean enableChromaticAberration = false;
    public static boolean enableVignette = true;
    public static int gradientQuality = 1; // 0=Low (1 layer), 1=Medium (2 layers), 2=High (3 layers)
    public static int backgroundMode = 1; // 0=Gradient, 1=Parallax Images, 2=Static Image
    
    // Quit confirmation
    private int escapeTimer; // Timer for double-tap escape confirmation
    private static final int ESCAPE_TIMEOUT = 120; // 2 seconds to press escape again
    
    // Timer and FPS tracking
    private long gameStartTime; // Time when current game started (in milliseconds)
    private double gameTimeSeconds; // Current game time in seconds
    private int currentFPS;
    private long lastFPSTime;
    private int frameCount;
    private double bossKillTime; // Time when boss was killed
    
    // Loading progress
    private volatile int loadingProgress = 0;
    private volatile boolean loadingComplete = false;
    
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
        particlePool = new ArrayList<>();
        beamAttacks = new ArrayList<>();
        bulletGrid = new HashMap<>();
        gameData = new GameData();
        shopManager = new ShopManager(gameData);
        
        // Initial state - start with loading screen
        gameState = GameState.LOADING;
        selectedStatItem = 0;
        selectedMenuItem = 0;
        selectedSettingsItem = 0;
        gradientTime = 0;
        screenShakeX = 0;
        screenShakeY = 0;
        trailSpawnTimer = 0;
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
        
        // Start loading assets in background thread
        startAssetLoading();
    }
    
    private void handleKeyPress(KeyEvent e) {
        int key = e.getKeyCode();
        
        switch (gameState) {
            case MENU:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    selectedMenuItem = Math.max(0, selectedMenuItem - 1);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    selectedMenuItem = Math.min(4, selectedMenuItem + 1);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    screenShakeIntensity = 5;
                    switch (selectedMenuItem) {
                        case 0: gameState = GameState.LEVEL_SELECT; break;
                        case 1: gameState = GameState.INFO; break;
                        case 2: gameState = GameState.STATS; break;
                        case 3: gameState = GameState.SHOP; break;
                        case 4: gameState = GameState.SETTINGS; break;
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    // Double-tap escape to quit
                    if (escapeTimer > 0) {
                        // Second press - quit
                        System.exit(0);
                    } else {
                        // First press - start timer
                        escapeTimer = ESCAPE_TIMEOUT;
                        screenShakeIntensity = 3;
                    }
                }
                // Legacy hotkeys still work
                else if (key == KeyEvent.VK_I) { gameState = GameState.INFO; screenShakeIntensity = 5; }
                else if (key == KeyEvent.VK_P) { gameState = GameState.SHOP; screenShakeIntensity = 5; }
                else if (key == KeyEvent.VK_O) { gameState = GameState.SETTINGS; screenShakeIntensity = 5; }
                // Debug menu shortcut
                else if (key == KeyEvent.VK_F3) { gameState = GameState.DEBUG; screenShakeIntensity = 5; }
                break;
                
            case STATS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { selectedStatItem = Math.max(0, selectedStatItem - 1); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { selectedStatItem = Math.min(3, selectedStatItem + 1); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) { gameData.adjustUpgrade(selectedStatItem, -1); screenShakeIntensity = 2; }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) { gameData.adjustUpgrade(selectedStatItem, 1); screenShakeIntensity = 2; }
                else if (key == KeyEvent.VK_ESCAPE) { gameState = GameState.MENU; screenShakeIntensity = 3; }
                break;
                
            case SETTINGS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { selectedSettingsItem = Math.max(0, selectedSettingsItem - 1); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { selectedSettingsItem = Math.min(9, selectedSettingsItem + 1); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A || key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    toggleSetting(selectedSettingsItem);
                    screenShakeIntensity = 3;
                }
                else if (key == KeyEvent.VK_SPACE) { toggleSetting(selectedSettingsItem); screenShakeIntensity = 3; }
                else if (key == KeyEvent.VK_ESCAPE) { gameState = GameState.MENU; screenShakeIntensity = 3; }
                break;
                
            case INFO:
                if (key == KeyEvent.VK_ESCAPE) gameState = GameState.MENU;
                break;
                
            case LEVEL_SELECT:
                if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) { selectPreviousLevel(); screenShakeIntensity = 2; }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) { selectNextLevel(); screenShakeIntensity = 2; }
                else if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { scrollLevelSelectUp(); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { scrollLevelSelectDown(); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_SPACE) { startSelectedLevel(); screenShakeIntensity = 5; }
                else if (key == KeyEvent.VK_ESCAPE) { gameState = GameState.MENU; screenShakeIntensity = 3; }
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
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { shopManager.selectPrevious(); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { shopManager.selectNext(); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_SPACE) {
                    int selected = shopManager.getSelectedShopItem();
                    if (selected == 0) {
                        // Continue to next level
                        startGame();
                        screenShakeIntensity = 5;
                    } else {
                        boolean purchased = shopManager.purchaseItem(selected);
                        screenShakeIntensity = purchased ? 4 : 2;
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) { startGame(); screenShakeIntensity = 3; }
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
                
            case DEBUG:
                if (key == KeyEvent.VK_1) {
                    // Unlock all levels
                    gameData.unlockAllLevels();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_2) {
                    // Give 10000 money
                    gameData.giveCheatMoney(10000);
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_3) {
                    // Max all upgrades
                    gameData.maxAllUpgrades();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_4) {
                    // Give 1000 money
                    gameData.giveCheatMoney(1000);
                    screenShakeIntensity = 3;
                }
                else if (key == KeyEvent.VK_5) {
                    // Give 100 money
                    gameData.giveCheatMoney(100);
                    screenShakeIntensity = 2;
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    gameState = GameState.MENU;
                    screenShakeIntensity = 3;
                }
                break;
        }
    }
    
    private void selectPreviousLevel() {
        gameData.setCurrentLevel(Math.max(1, gameData.getCurrentLevel() - 1));
    }
    
    private void selectNextLevel() {
        gameData.setCurrentLevel(Math.min(gameData.getMaxUnlockedLevel(), gameData.getCurrentLevel() + 1));
        ensureLevelVisible();
    }
    
    private void scrollLevelSelectUp() {
        levelSelectScroll = Math.max(0, levelSelectScroll - 150);
    }
    
    private void scrollLevelSelectDown() {
        levelSelectScroll += 150;
    }
    
    private void ensureLevelVisible() {
        // Auto-scroll to keep selected level visible
        int level = gameData.getCurrentLevel();
        int row = (level - 1) / 3; // 3 columns per row
        int levelY = 200 + row * 150 - (int)levelSelectScroll;
        
        // If level is above visible area, scroll up
        if (levelY < 180) {
            levelSelectScroll = Math.max(0, 200 + row * 150 - 180);
        }
        // If level is below visible area, scroll down
        else if (levelY > HEIGHT - 200) {
            levelSelectScroll = 200 + row * 150 - (HEIGHT - 350);
        }
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
        invulnerabilityTimer = INVULNERABILITY_DURATION; // 5 seconds of immunity
        screenShakeIntensity = 0;
        bossDeathAnimation = false;
        deathAnimationTimer = 0;
        bossDeathScale = 1.0;
        bossDeathRotation = 0;
        escapeTimer = 0;
        
        // Initialize timer and FPS tracking
        gameStartTime = System.currentTimeMillis();
        gameTimeSeconds = 0;
        currentFPS = 0;
        frameCount = 0;
        lastFPSTime = System.currentTimeMillis();
        bossKillTime = 0;
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
                
                // Update game timer (only during gameplay)
                if (gameState == GameState.PLAYING && player != null) {
                    gameTimeSeconds = (System.currentTimeMillis() - gameStartTime) / 1000.0;
                }
                
                // Calculate FPS
                frameCount++;
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFPSTime >= 1000) {
                    currentFPS = frameCount;
                    frameCount = 0;
                    lastFPSTime = currentTime;
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
            
            // Spawn fire trail behind player
            if (Game.enableParticles) {
                trailSpawnTimer++;
                if (trailSpawnTimer >= 2) { // Every 2 frames
                    trailSpawnTimer = 0;
                    // Create rocket/fire trail particles
                    // Calculate angle based on velocity (or default upward if stationary)
                    double vx = player.getVX();
                    double vy = player.getVY();
                    double angle = (vx == 0 && vy == 0) ? -Math.PI / 2 : Math.atan2(vy, vx);
                    
                    // Spawn particles at the back of the rocket (opposite to movement direction)
                    double backDistance = 20; // Distance behind rocket center
                    double trailX = player.getX() - Math.cos(angle) * backDistance;
                    double trailY = player.getY() - Math.sin(angle) * backDistance;
                    
                    for (int i = 0; i < 2; i++) {
                        // Add spread perpendicular to movement direction
                        double perpAngle = angle + Math.PI / 2;
                        double spread = (Math.random() - 0.5) * 6;
                        double finalX = trailX + Math.cos(perpAngle) * spread;
                        double finalY = trailY + Math.sin(perpAngle) * spread;
                        
                        // Particle velocity opposite to rocket direction
                        double particleVX = -Math.cos(angle) * (0.5 + Math.random() * 1.0);
                        double particleVY = -Math.sin(angle) * (0.5 + Math.random() * 1.0);
                        
                        addParticle(
                            finalX, finalY,
                            particleVX, particleVY,
                            new Color(255, 150 + (int)(Math.random() * 50), 0),
                            15 + (int)(Math.random() * 10),
                            6 + (int)(Math.random() * 6),
                            Particle.ParticleType.SPARK
                        );
                    }
                }
            }
        }
        
        // Update particles using iterator for efficient removal
        for (java.util.Iterator<Particle> it = particles.iterator(); it.hasNext();) {
            Particle p = it.next();
            p.update(deltaTime);
            if (!p.isAlive()) {
                it.remove();
                returnParticleToPool(p);
            }
        }
        
        // Check if player hit boss (only vulnerable during special window)
        if (currentBoss != null && player != null && player.collidesWith(currentBoss) && !bossDeathAnimation) {
            if (bossVulnerable) {
                // TODO: Play sound effect - boss_hit.wav
                
                // Successful hit! Start death animation
                int winBonus = 1000 + (gameData.getCurrentLevel() * 500) + (dodgeCombo * 100);
                gameData.addScore(winBonus);
                
                // Add money reward based on boss type
                int moneyReward = currentBoss.getMoneyReward();
                gameData.addRunMoney(moneyReward);
                gameData.addTotalMoney(moneyReward);
                
                // Start boss death animation
                bossDeathAnimation = true;
                deathAnimationTimer = DEATH_ANIMATION_DURATION;
                bossDeathScale = 1.0;
                bossDeathRotation = 0;
                bossKillTime = gameTimeSeconds; // Record time when boss was killed
                
                // Create impact particles at collision point (between player and boss)
                if (enableParticles) {
                    double impactX = (player.getX() + currentBoss.getX()) / 2;
                    double impactY = (player.getY() + currentBoss.getY()) / 2;
                    
                    // Bright white/yellow impact flash
                    for (int i = 0; i < 30; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 2 + Math.random() * 6;
                        Color impactColor = Math.random() < 0.5 ? IMPACT_WHITE : IMPACT_YELLOW;
                        addParticle(
                            impactX, impactY,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            impactColor, 20, 8,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Impact shockwave rings
                    for (int i = 0; i < 3; i++) {
                        addParticle(
                            impactX, impactY, 0, 0,
                            new Color(255, 255, 200, 200 - i * 60), 25 + i * 8, 30 + i * 20,
                            Particle.ParticleType.EXPLOSION
                        );
                    }
                }
                
                // Make player disappear (missile hit)
                player = null;
                
                // Initial massive explosion
                screenShakeIntensity = 25;
                
                // Create massive fiery explosion particles
                int explosionParticleCount = bullets.size() > 200 ? 50 : 100; // Reduce at high bullet density
                for (int i = 0; i < explosionParticleCount; i++) {
                    double angle = Math.random() * TWO_PI;
                    double speed = 3 + Math.random() * 8;
                    Color fireColor;
                    double rand = Math.random();
                    if (rand < 0.4) {
                        fireColor = FIRE_ORANGE;
                    } else if (rand < 0.7) {
                        fireColor = FIRE_YELLOW;
                    } else {
                        fireColor = FIRE_RED;
                    }
                    addParticle(
                        currentBoss.getX(), currentBoss.getY(),
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        fireColor, 50 + (int)(Math.random() * 30), 6,
                        Particle.ParticleType.SPARK
                    );
                }
                
                // Multiple explosion rings
                for (int i = 0; i < 5; i++) {
                    addParticle(
                        currentBoss.getX(), currentBoss.getY(), 0, 0,
                        new Color(255, 150 - i * 20, 0), 40 + i * 15, 40 + i * 25,
                        Particle.ParticleType.EXPLOSION
                    );
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
            if (enableParticles && Math.random() < 0.15 * deltaTime) {
                double offsetX = (Math.random() - 0.5) * 80 * bossDeathScale;
                double offsetY = (Math.random() - 0.5) * 80 * bossDeathScale;
                for (int i = 0; i < 15; i++) {
                    double angle = Math.random() * TWO_PI;
                    double speed = 1 + Math.random() * 4;
                    Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                    addParticle(
                        currentBoss.getX() + offsetX, currentBoss.getY() + offsetY,
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        fireColor, 30, 4,
                        Particle.ParticleType.SPARK
                    );
                }
            }
            
            // Continuous screen shake that decreases over time
            screenShakeIntensity = 15 * (1.0 - progress);
            
            // Smoke trails
            if (enableParticles && Math.random() < 0.3 * deltaTime) {
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
                if (enableParticles) {
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
                }
                
                screenShakeIntensity = 20;
                gameState = GameState.WIN;
                bossDeathAnimation = false;
                return;
            }
        }
        
        // Boss becomes vulnerable periodically (less frequent at early levels)
        if (invulnerabilityTimer > 0) {
            invulnerabilityTimer -= deltaTime; // Countdown immunity timer
        }
        
        double vulnerabilityChance = 0.01 * deltaTime;
        if (gameData.getCurrentLevel() <= 3) {
            vulnerabilityChance *= 0.5; // Half as likely at levels 1-3
        }
        if (!bossVulnerable && currentBoss != null && invulnerabilityTimer <= 0 && Math.random() < vulnerabilityChance) {
            // TODO: Play sound effect - vulnerability_window_open.wav
            
            bossVulnerable = true;
            // Base duration + 60 frames (1 second) per upgrade level
            vulnerabilityTimer = VULNERABILITY_DURATION + (gameData.getActiveAttackWindowLevel() * 60);
            // Visual indicator - sparkles around boss
            if (enableParticles) {
                // Larger burst of sparkles when vulnerability opens
                for (int i = 0; i < 25; i++) {
                    double angle = Math.random() * TWO_PI;
                    double radius = 40 + Math.random() * 30;
                    double speed = 0.5 + Math.random() * 1.5;
                    addParticle(
                        currentBoss.getX() + Math.cos(angle) * radius,
                        currentBoss.getY() + Math.sin(angle) * radius,
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        VULNERABILITY_GOLD, 40, 4,
                        Particle.ParticleType.SPARK
                    );
                }
            }
        }
        
        // Warning sparkles 1 second before vulnerability window closes
        if (bossVulnerable && vulnerabilityTimer > 0 && vulnerabilityTimer < 60 && currentBoss != null) {
            // Intermittent warning sparkles
            if (enableParticles && Math.random() < 0.3 * deltaTime) {
                double angle = Math.random() * TWO_PI;
                double radius = 50 + Math.random() * 20;
                addParticle(
                    currentBoss.getX() + Math.cos(angle) * radius,
                    currentBoss.getY() + Math.sin(angle) * radius,
                    0, -2,
                    WARNING_RED, 20, 3,
                    Particle.ParticleType.SPARK
                );
            }
        }
        
        // Update boss with delta time (but not during death animation)
        if (currentBoss != null && !bossDeathAnimation) {
            currentBoss.update(bullets, player, WIDTH, HEIGHT, deltaTime, particles);
            beamAttacks = currentBoss.getBeamAttacks();
        }
        
        // Check beam attack collisions (only if player exists)
        for (BeamAttack beam : beamAttacks) {
            if (player != null && beam.collidesWith(player)) {
                // Hit by beam - game over
                // TODO: Play sound effect - player_death.wav
                
                // Create death particles
                for (int j = 0; j < 20; j++) {
                    double angle = Math.random() * TWO_PI;
                    double speed = 1 + Math.random() * 3;
                    addParticle(
                        player.getX(), player.getY(),
                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                        PLAYER_DEATH_RED, 30, 6,
                        Particle.ParticleType.SPARK
                    );
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
            
            // Spawn trail particles for fast-moving bullets
            if (enableParticles && bullet.shouldSpawnTrail() && Math.random() < 0.10 * deltaTime) {
                addParticle(
                    bullet.getX(), bullet.getY(),
                    -bullet.getVX() * 0.2, -bullet.getVY() * 0.2,
                    bullet.getTrailColor(), 15, 3,
                    Particle.ParticleType.TRAIL
                );
            }
            
            // Check if explosive bullets should explode
            if (bullet.shouldExplode()) {
                // TODO: Play sound effect - explosion.wav (volume/pitch based on bullet type)
                
                // Create explosion particles with shockwave
                if (enableParticles) {
                    // Scale down particle count if too many bullets
                    List<Particle> explosionParticles = bullet.createExplosionParticles();
                    int particlesToAdd = bullets.size() > 200 ? explosionParticles.size() / 2 : explosionParticles.size();
                    for (int j = 0; j < particlesToAdd && particles.size() < MAX_PARTICLES; j++) {
                        particles.add(explosionParticles.get(j));
                    }
                }
                
                // Create fragments from explosion
                List<Bullet> fragments = bullet.createFragments();
                bullets.addAll(fragments);
                bullets.remove(i);
                returnBulletToPool(bullet);
                continue;
            }
            
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
            }
        }
        
        // Rebuild spatial grid after all bullet updates for optimized collision
        rebuildBulletGrid();
        
        // Check collisions using spatial grid (much faster for many bullets!)
        if (player != null) {
            List<Bullet> nearbyBullets = getNearbyBullets(player.getX(), player.getY());
            for (Bullet bullet : nearbyBullets) {
                if (bullet.isActive() && bullet.collidesWith(player)) {
                    // Lucky Dodge chance - phase through bullets
                    int luckyDodgeLevel = gameData.getActiveLuckyDodgeLevel();
                    if (luckyDodgeLevel > 0) {
                        double dodgeChance = luckyDodgeLevel * 0.05; // 5% per level
                        if (Math.random() < dodgeChance) {
                            // TODO: Play sound effect - lucky_dodge.wav (pitch up with combo)
                            
                            // Lucky dodge! Trigger flicker animation
                            player.triggerFlicker();
                            bullets.remove(bullet);
                            returnBulletToPool(bullet);
                            
                            // Increment dodge combo
                            dodgeCombo++;
                            comboTimer = COMBO_TIMEOUT;
                            
                            // Add score based on combo
                            gameData.addScore(10 * dodgeCombo);
                            
                            // Create dodge particles
                            if (enableParticles) {
                                for (int j = 0; j < 8; j++) {
                                    double angle = TWO_PI * j / 8;
                                    addParticle(
                                        player.getX(), player.getY(),
                                        Math.cos(angle) * 2, Math.sin(angle) * 2,
                                        DODGE_GREEN, 20, 5,
                                        Particle.ParticleType.DODGE
                                    );
                                }
                            }
                            
                            continue;
                        }
                    }
                    
                    // No dodge - game over
                    // TODO: Play sound effect - player_death.wav
                    
                    // Create death particles
                    if (enableParticles) {
                        for (int j = 0; j < 20; j++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 1 + Math.random() * 3;
                            addParticle(
                                player.getX(), player.getY(),
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                PLAYER_DEATH_RED, 30, 6,
                                Particle.ParticleType.SPARK
                            );
                        }
                    }
                    screenShakeIntensity = 10;
                    gameState = GameState.GAME_OVER;
                    return;
                }
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
    
    // Particle pooling methods
    private Particle getParticleFromPool() {
        if (particlePool.isEmpty()) {
            return new Particle(0, 0, 0, 0, Color.WHITE, 1, 1, Particle.ParticleType.SPARK);
        }
        return particlePool.remove(particlePool.size() - 1);
    }
    
    private void returnParticleToPool(Particle particle) {
        if (particlePool.size() < 300) { // Cap pool size
            particlePool.add(particle);
        }
    }
    
    // Add particle with pooling and limit check
    private void addParticle(double x, double y, double vx, double vy, Color color, int lifetime, double size, Particle.ParticleType type) {
        if (particles.size() >= MAX_PARTICLES) return; // Limit particles
        Particle p = getParticleFromPool();
        p.reset(x, y, vx, vy, color, lifetime, size, type);
        particles.add(p);
    }
    
    // Spatial grid methods for optimized collision detection
    private int getGridKey(double x, double y) {
        int gridX = (int)(x / GRID_CELL_SIZE);
        int gridY = (int)(y / GRID_CELL_SIZE);
        return gridX * 10000 + gridY; // Simple hash
    }
    
    private void rebuildBulletGrid() {
        bulletGrid.clear();
        for (Bullet bullet : bullets) {
            if (bullet.isActive()) {
                int key = getGridKey(bullet.getX(), bullet.getY());
                bulletGrid.computeIfAbsent(key, k -> new ArrayList<>()).add(bullet);
            }
        }
    }
    
    private List<Bullet> getNearbyBullets(double x, double y) {
        List<Bullet> nearby = new ArrayList<>();
        // Check 3x3 grid around player
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int checkX = (int)(x / GRID_CELL_SIZE) + dx;
                int checkY = (int)(y / GRID_CELL_SIZE) + dy;
                int key = checkX * 10000 + checkY;
                List<Bullet> cellBullets = bulletGrid.get(key);
                if (cellBullets != null) {
                    nearby.addAll(cellBullets);
                }
            }
        }
        return nearby;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        switch (gameState) {
            case MENU:
                renderer.drawMenu(g2d, WIDTH, HEIGHT, gradientTime, escapeTimer, selectedMenuItem);
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
                renderer.drawLevelSelect(g2d, WIDTH, HEIGHT, gameData.getCurrentLevel(), gameData.getMaxUnlockedLevel(), gradientTime, levelSelectScroll);
                break;
            case PLAYING:
                // Apply screen shake
                g2d.translate(screenShakeX, screenShakeY);
                renderer.drawGame(g2d, WIDTH, HEIGHT, player, currentBoss, bullets, particles, beamAttacks, gameData.getCurrentLevel(), gradientTime, bossVulnerable, vulnerabilityTimer, dodgeCombo, comboTimer > 0, bossDeathAnimation, bossDeathScale, bossDeathRotation, gameTimeSeconds, currentFPS);
                g2d.translate(-screenShakeX, -screenShakeY);
                break;
            case LOADING:
                // Draw loading screen directly (renderer not yet created)
                drawSimpleLoading(g2d, WIDTH, HEIGHT, loadingProgress);
                break;
            case GAME_OVER:
                renderer.drawGameOver(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case WIN:
                renderer.drawWin(g2d, WIDTH, HEIGHT, gradientTime, bossKillTime);
                break;
            case SHOP:
                renderer.drawShop(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case DEBUG:
                renderer.drawDebug(g2d, WIDTH, HEIGHT, gradientTime);
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
            case 0: // Background Mode
                backgroundMode = (backgroundMode + 1) % 3;
                break;
            case 1: // Gradient Animation
                enableGradientAnimation = !enableGradientAnimation;
                break;
            case 2: // Gradient Quality
                gradientQuality = (gradientQuality + 1) % 3; // Cycle through 0, 1, 2
                break;
            case 3: // Grain Effect
                enableGrainEffect = !enableGrainEffect;
                break;
            case 4: // Particle Effects
                enableParticles = !enableParticles;
                break;
            case 5: // Shadows
                enableShadows = !enableShadows;
                break;
            case 6: // Bloom
                enableBloom = !enableBloom;
                break;
            case 7: // Motion Blur
                enableMotionBlur = !enableMotionBlur;
                break;
            case 8: // Chromatic Aberration
                enableChromaticAberration = !enableChromaticAberration;
                break;
            case 9: // Vignette
                enableVignette = !enableVignette;
                break;
        }
    }
    
    private void startAssetLoading() {
        Thread loadingThread = new Thread(() -> {
            try {
                loadingProgress = 10;
                repaint();
                
                // Create renderer (this loads backgrounds and overlay)
                renderer = new Renderer(gameData, shopManager);
                loadingProgress = 80;
                repaint();
                
                // Small delay to ensure everything is ready
                Thread.sleep(200);
                loadingProgress = 100;
                repaint();
                
                // Wait a moment then switch to menu
                Thread.sleep(300);
                loadingComplete = true;
                gameState = GameState.MENU;
                repaint();
                
            } catch (Exception e) {
                e.printStackTrace();
                // On error, still go to menu
                loadingComplete = true;
                gameState = GameState.MENU;
                repaint();
            }
        });
        loadingThread.start();
    }
    
    private void drawSimpleLoading(Graphics2D g, int width, int height, int progress) {
        // Simple loading screen without renderer
        g.setColor(new Color(30, 30, 40));
        g.fillRect(0, 0, width, height);
        
        // Title
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 72));
        String title = "ONE HIT MAN";
        FontMetrics fm = g.getFontMetrics();
        int titleX = (width - fm.stringWidth(title)) / 2;
        int titleY = height / 2 - 100;
        g.drawString(title, titleX, titleY);
        
        // Loading text
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String loadingText = "Loading...";
        fm = g.getFontMetrics();
        g.drawString(loadingText, (width - fm.stringWidth(loadingText)) / 2, height / 2 + 20);
        
        // Progress bar
        int barWidth = 400;
        int barHeight = 30;
        int barX = (width - barWidth) / 2;
        int barY = height / 2 + 60;
        
        // Background
        g.setColor(new Color(60, 60, 70));
        g.fillRoundRect(barX, barY, barWidth, barHeight, 15, 15);
        
        // Progress fill
        int fillWidth = (int)(barWidth * (progress / 100.0));
        if (fillWidth > 0) {
            g.setColor(new Color(136, 192, 208));
            g.fillRoundRect(barX, barY, fillWidth, barHeight, 15, 15);
        }
        
        // Border
        g.setColor(new Color(200, 200, 200));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(barX, barY, barWidth, barHeight, 15, 15);
        
        // Percentage
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        String percentText = progress + "%";
        fm = g.getFontMetrics();
        g.drawString(percentText, (width - fm.stringWidth(percentText)) / 2, barY + barHeight + 30);
    }
}
