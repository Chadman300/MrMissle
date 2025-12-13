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
    private double settingsScroll; // Scroll offset for settings menu
    
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
    private boolean eKeyPressed; // Track E key state to prevent continuous activation
    
    // Animation
    private double gradientTime;
    
    // Item unlock animation
    private boolean itemUnlockAnimation;
    private boolean itemUnlockDismissing; // True when animation is fading out
    private int itemUnlockTimer;
    private int itemUnlockDismissTimer; // Timer for fade-out animation
    private String unlockedItemName;
    private static final int ITEM_UNLOCK_DURATION = 180; // 3 seconds
    private static final int ITEM_DISMISS_DURATION = 30; // 0.5 seconds fade out
    
    // UI Transitions
    private GameState previousState;
    private float stateTransitionProgress; // 0.0 = old state, 1.0 = new state
    private static final float TRANSITION_SPEED = 0.08f; // Speed of state transitions
    
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
    private int bossHitCount; // Number of times boss has been hit (max 3)
    private int bossFlashTimer; // Flash effect when boss takes damage
    private static final int BOSS_MAX_HITS = 3;
    private static final int VULNERABILITY_DURATION = 1200; // 20 second window
    private static final int INVULNERABILITY_DURATION = 150; // 2.5 seconds at start (changed from 300/5 seconds)
    private boolean bossDeathAnimation;
    private int deathAnimationTimer;
    private static final int DEATH_ANIMATION_DURATION = 180; // 3 seconds
    private double bossDeathScale;
    private boolean waitingForRespawn; // Waiting after non-fatal boss hit
    private int respawnDelayTimer; // Timer before respawning player
    private static final int RESPAWN_DELAY = 90; // 1.5 seconds delay
    private double bossDeathRotation;
    
    // Polish effects
    private static final double GRAZE_DISTANCE = 25; // Distance for graze detection
    private int grazeScore = 0; // Accumulate graze score
    private int hitPauseTimer = 0; // Brief pause on impact
    private int screenFlashTimer = 0; // Screen flash on player hit
    
    // Active item effects
    private boolean playerInvincible; // For INVINCIBILITY item and DASH i-frames
    private boolean shieldActive; // For SHIELD item
    private int respawnInvincibilityTimer; // Shorter invincibility after respawn
    private double dashSpeedMultiplier; // For DASH item
    
    // Camera tracking with smooth interpolation
    private double cameraX = 0;
    private double cameraY = 0;
    private static final double CAMERA_SMOOTHING = 0.02; // Slower and smoother (was 0.05)
    private static final double CAMERA_DEADZONE = 80; // Distance from center before camera moves
    private static final double CAMERA_MAX_OFFSET = 100; // Max pixels camera can move from center
    private boolean introPanActive = false;
    private int introPanTimer = 0;
    private static final int INTRO_PAN_DURATION = 240; // 4 seconds total (2s boss entrance, 2s pan back)
    private double bossEntranceY = -200; // Boss starts above screen
    
    // Settings
    private int selectedSettingsItem;
    public static boolean enableGradientAnimation = true;
    public static boolean enableGrainEffect = false;
    public static boolean enableParticles = true;
    public static boolean enableShadows = true;
    public static boolean enableBloom = true;
    public static boolean enableMotionBlur = false;
    public static boolean enableChromaticAberration = true;
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
    private volatile int targetLoadingProgress = 0;
    private double displayedLoadingProgress = 0.0;
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
        settingsScroll = 0;
        selectedSettingsItem = 0;
        gradientTime = 0;
        itemUnlockAnimation = false;
        itemUnlockDismissing = false;
        itemUnlockTimer = 0;
        itemUnlockDismissTimer = 0;
        previousState = GameState.MENU;
        stateTransitionProgress = 1.0f;
        unlockedItemName = "";
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
                // Reset SPACE key tracking on release
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    eKeyPressed = false;
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
                        case 0: transitionToState(GameState.LEVEL_SELECT); break;
                        case 1: transitionToState(GameState.INFO); break;
                        case 2: transitionToState(GameState.STATS); break;
                        case 3: transitionToState(GameState.SHOP); break;
                        case 4: transitionToState(GameState.SETTINGS); break;
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
                else if (key == KeyEvent.VK_I) { transitionToState(GameState.INFO); screenShakeIntensity = 5; }
                else if (key == KeyEvent.VK_P) { transitionToState(GameState.SHOP); screenShakeIntensity = 5; }
                else if (key == KeyEvent.VK_O) { transitionToState(GameState.SETTINGS); screenShakeIntensity = 5; }
                // Debug menu shortcut
                else if (key == KeyEvent.VK_F3) { transitionToState(GameState.DEBUG); screenShakeIntensity = 5; }
                break;
                
            case STATS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { selectedStatItem = Math.max(0, selectedStatItem - 1); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { selectedStatItem = Math.min(4, selectedStatItem + 1); screenShakeIntensity = 1; }
                else if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A) {
                    if (selectedStatItem == 4 && gameData.hasActiveItems()) {
                        gameData.equipPreviousItem();
                        screenShakeIntensity = 2;
                    } else {
                        gameData.adjustUpgrade(selectedStatItem, -1);
                        screenShakeIntensity = 2;
                    }
                }
                else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D) {
                    if (selectedStatItem == 4 && gameData.hasActiveItems()) {
                        gameData.equipNextItem();
                        screenShakeIntensity = 2;
                    } else {
                        gameData.adjustUpgrade(selectedStatItem, 1);
                        screenShakeIntensity = 2;
                    }
                }
                else if (key == KeyEvent.VK_ESCAPE) { gameState = GameState.MENU; screenShakeIntensity = 3; }
                break;
                
            case SETTINGS:
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) { 
                    selectedSettingsItem = Math.max(0, selectedSettingsItem - 1); 
                    ensureSettingsItemVisible();
                    screenShakeIntensity = 1; 
                }
                else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) { 
                    selectedSettingsItem = Math.min(9, selectedSettingsItem + 1);
                    ensureSettingsItemVisible();
                    screenShakeIntensity = 1; 
                }
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
                    transitionToState(GameState.MENU);
                } else if (key == KeyEvent.VK_SPACE && introPanActive) {
                    // Skip intro animation
                    introPanActive = false;
                    cameraX = 0;
                    cameraY = 0;
                    screenShakeIntensity = 8;
                } else if (key == KeyEvent.VK_SPACE && !eKeyPressed && !introPanActive) {
                    // Activate equipped item (only once per key press, and not during intro)
                    eKeyPressed = true;
                    ActiveItem item = gameData.getEquippedItem();
                    if (item != null && item.canActivate()) {
                        item.activate();
                        screenShakeIntensity = 3;
                    }
                } else if (key == KeyEvent.VK_T && currentBoss != null && player != null) {
                    // Debug: Teleport player to boss (instant death)
                    player.setPosition(currentBoss.getX(), currentBoss.getY());
                    screenShakeIntensity = 10;
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
                    transitionToState(GameState.MENU);
                }
                break;
                
            case WIN:
                if (key == KeyEvent.VK_SPACE) {
                    // If animation is playing, start dismiss animation
                    if (itemUnlockAnimation && !itemUnlockDismissing) {
                        itemUnlockDismissing = true;
                        itemUnlockDismissTimer = ITEM_DISMISS_DURATION;
                        return;
                    }
                    // If already dismissing, wait for it to complete
                    if (itemUnlockDismissing) {
                        return;
                    }
                    
                    // Unlock next level
                    int currentLevel = gameData.getCurrentLevel();
                    gameData.setMaxUnlockedLevel(Math.max(gameData.getMaxUnlockedLevel(), currentLevel + 1));
                    
                    // Award money
                    int bossReward = 50 + (currentLevel * 10);
                    if (!gameData.getDefeatedBosses()[currentLevel - 1]) {
                        gameData.setBossDefeated(currentLevel - 1, true);
                        bossReward += 100;
                    }
                    
                    // Apply LUCKY_CHARM multiplier if equipped
                    ActiveItem equippedItem = gameData.getEquippedItem();
                    if (equippedItem != null && equippedItem.getType() == ActiveItem.ItemType.LUCKY_CHARM) {
                        bossReward = (int)(bossReward * 1.5); // 50% bonus
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
                else if (key == KeyEvent.VK_6) {
                    // Unlock all active items
                    gameData.unlockAllItems();
                    screenShakeIntensity = 5;
                }
                else if (key == KeyEvent.VK_ESCAPE) {
                    transitionToState(GameState.MENU);
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
    
    private void ensureSettingsItemVisible() {
        // Auto-scroll to keep selected setting visible
        int itemY = 200 + selectedSettingsItem * 150 - (int)settingsScroll;
        
        // If item is above visible area, scroll up
        if (itemY < 180) {
            settingsScroll = Math.max(0, 200 + selectedSettingsItem * 150 - 180);
        }
        // If item is below visible area, scroll down
        else if (itemY > HEIGHT - 250) {
            settingsScroll = 200 + selectedSettingsItem * 150 - (HEIGHT - 400);
        }
    }
    
    private void startSelectedLevel() {
        if (gameData.getCurrentLevel() <= gameData.getMaxUnlockedLevel()) {
            startGame();
        }
    }
    
    private void startGame() {
        gameState = GameState.PLAYING;
        player = new Player(WIDTH / 2, HEIGHT - 200, gameData.getActiveSpeedLevel());
        bullets.clear();
        particles.clear();
        currentBoss = new Boss(WIDTH / 2, 100, gameData.getCurrentLevel()); // Normal position, will move during intro
        gameData.setSurvivalTime(0);
        dodgeCombo = 0;
        comboTimer = 0;
        bossVulnerable = false;
        vulnerabilityTimer = 0;
        invulnerabilityTimer = INVULNERABILITY_DURATION; // 5 seconds of immunity
        bossHitCount = 0;
        respawnInvincibilityTimer = 0; // No respawn invincibility at start
        waitingForRespawn = false;
        respawnDelayTimer = 0;
        
        // Start intro sequence with boss entrance
        introPanActive = true;
        introPanTimer = 0;
        bossEntranceY = -200; // Boss will start above screen
        cameraX = 0;
        cameraY = 0;
        
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
        
        // Start active item cooldown at start of level
        ActiveItem equippedItem = gameData.getEquippedItem();
        if (equippedItem != null) {
            equippedItem.startLevelCooldown();
        }
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
        // Update item unlock animation timer (let it countdown for animation progress)
        if (itemUnlockTimer > 0) {
            itemUnlockTimer--;
        }
        
        // Update dismiss animation
        if (itemUnlockDismissing) {
            itemUnlockDismissTimer--;
            if (itemUnlockDismissTimer <= 0) {
                itemUnlockAnimation = false;
                itemUnlockDismissing = false;
            }
        }
        
        // Update state transitions
        if (stateTransitionProgress < 1.0f) {
            stateTransitionProgress = Math.min(1.0f, stateTransitionProgress + TRANSITION_SPEED);
        }
        
        if (gameState != GameState.PLAYING) return;
        
        // Reset active item effect states each frame
        playerInvincible = false;
        dashSpeedMultiplier = 1.0;
        // Shield persists until used
        
        // Handle respawn invincibility timer
        if (respawnInvincibilityTimer > 0) {
            respawnInvincibilityTimer -= deltaTime;
            if (respawnInvincibilityTimer <= 0) {
                // Timer expired - remove shield and invincibility
                shieldActive = false;
                playerInvincible = false;
            } else {
                // Still invincible from respawn
                playerInvincible = true;
            }
        }
        
        // Track survival and score (scaled by delta time) - only when player is alive
        if (player != null) {
            gameData.incrementSurvivalTime();
            
            // Apply score multiplier from active item
            ActiveItem item = gameData.getEquippedItem();
            int scoreGain = (int)deltaTime;
            if (item != null && item.getType() == ActiveItem.ItemType.LUCKY_CHARM) {
                scoreGain = (int)(scoreGain * 1.5); // 50% bonus
            }
            gameData.addScore(scoreGain);
        }
        
        // Update active item
        ActiveItem equippedItem = gameData.getEquippedItem();
        if (equippedItem != null) {
            equippedItem.update();
            
            // Handle active item effects
            if (equippedItem.isActive()) {
                handleActiveItemEffects(equippedItem, deltaTime);
            } else {
                // Item just ended - clear shield if it was active
                if (equippedItem.getType() == ActiveItem.ItemType.SHIELD) {
                    shieldActive = false;
                }
            }
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
            // Only allow player control when intro pan is complete
            if (!introPanActive) {
                player.update(keys, WIDTH, HEIGHT, deltaTime);
            }
            
            // Handle intro sequence
            if (introPanActive) {
                introPanTimer += deltaTime;
                
                double halfDuration = INTRO_PAN_DURATION / 2.0;
                if (introPanTimer < halfDuration) {
                    // Boss entrance animation - fly down from above
                    double progress = introPanTimer / halfDuration;
                    double easeProgress = 1 - Math.pow(1 - progress, 3); // Ease out cubic
                    
                    // Boss flies down smoothly from -200 to 100
                    bossEntranceY = -200 + (300 * easeProgress);
                    if (currentBoss != null) {
                        // Directly set boss Y position during entrance
                        currentBoss.setPosition(currentBoss.getX(), bossEntranceY);
                        // Keep animations running (helicopter blades)
                        currentBoss.updateAnimations(deltaTime);
                        
                        // Add screen shake during descent
                        if (progress > 0.2) {
                            screenShakeIntensity = Math.max(screenShakeIntensity, 8 + easeProgress * 4);
                        }
                        
                        // Add jet trail particles during descent
                        if (progress > 0.1 && Math.random() < 0.4) {
                            double angle = Math.PI / 2 + (Math.random() - 0.5) * 0.5;
                            double speed = 1 + Math.random() * 2;
                            particles.add(new Particle(
                                currentBoss.getX() + (Math.random() - 0.5) * 30,
                                currentBoss.getY() + currentBoss.getSize() / 2,
                                Math.cos(angle) * speed,
                                Math.sin(angle) * speed,
                                new Color(255, 150, 0, 200),
                                60 + (int)(Math.random() * 30),
                                8.0 + Math.random() * 8.0,
                                Particle.ParticleType.TRAIL
                            ));
                        }
                    }
                    
                    // Camera follows boss down slightly
                    double targetY = bossEntranceY * 0.3;
                    cameraY = targetY;
                    
                } else if (introPanTimer < INTRO_PAN_DURATION) {
                    // Pan back to player (second half)
                    double progress = (introPanTimer - halfDuration) / halfDuration;
                    double easeProgress = progress * progress * (3 - 2 * progress); // Smooth ease
                    
                    // Boss settles into final position (100)
                    if (currentBoss != null) {
                        if (bossEntranceY < 100) {
                            bossEntranceY += (100 - bossEntranceY) * 0.1;
                        } else {
                            bossEntranceY = 100;
                        }
                        currentBoss.setPosition(currentBoss.getX(), bossEntranceY);
                        // Keep animations running
                        currentBoss.updateAnimations(deltaTime);
                        
                        // Screen shake decreases as boss settles
                        screenShakeIntensity = Math.max(screenShakeIntensity, 6 * (1 - easeProgress));
                    }
                    
                    // Camera pans back to center
                    double startY = 30.0; // Camera's max Y during boss viewing
                    cameraY = startY * (1 - easeProgress);
                    
                    // Add engine glow particles as boss settles
                    if (currentBoss != null && Math.random() < 0.15) {
                        particles.add(new Particle(
                            currentBoss.getX() + (Math.random() - 0.5) * 40,
                            currentBoss.getY() + currentBoss.getSize() / 2,
                            (Math.random() - 0.5) * 0.5,
                            1 + Math.random() * 1.5,
                            new Color(100, 150, 255, 180),
                            40 + (int)(Math.random() * 20),
                            6.0 + Math.random() * 6.0,
                            Particle.ParticleType.SPARK
                        ));
                    }
                    
                } else {
                    // Entrance complete - add final burst of particles
                    if (introPanTimer - deltaTime < INTRO_PAN_DURATION) {
                        // Just finished - add dramatic particle burst
                        screenShakeIntensity = 15; // Massive shake at the end
                        if (currentBoss != null) {
                            for (int i = 0; i < 20; i++) {
                                double angle = Math.random() * Math.PI * 2;
                                double speed = 1 + Math.random() * 3;
                                particles.add(new Particle(
                                    currentBoss.getX(),
                                    currentBoss.getY(),
                                    Math.cos(angle) * speed,
                                    Math.sin(angle) * speed,
                                    new Color(255, 200, 100, 200),
                                    30 + (int)(Math.random() * 30),
                                    10.0 + Math.random() * 10.0,
                                    Particle.ParticleType.EXPLOSION
                                ));
                            }
                        }
                    }
                    
                    introPanActive = false;
                    cameraX = 0;
                    cameraY = 0;
                }
            } else {
                // Normal camera follow with slow smooth interpolation (only when intro is done)
                double targetCameraX = 0;
                double targetCameraY = 0;
                
                // Calculate offset from screen center
                double offsetX = player.getX() - WIDTH / 2;
                double offsetY = player.getY() - HEIGHT / 2;
                
                // Only move camera if player is outside deadzone
                if (Math.abs(offsetX) > CAMERA_DEADZONE) {
                    targetCameraX = offsetX - Math.signum(offsetX) * CAMERA_DEADZONE;
                }
                if (Math.abs(offsetY) > CAMERA_DEADZONE) {
                    targetCameraY = offsetY - Math.signum(offsetY) * CAMERA_DEADZONE;
                }
                
                // Smoothly interpolate camera position (slower than before)
                cameraX += (targetCameraX - cameraX) * CAMERA_SMOOTHING;
                cameraY += (targetCameraY - cameraY) * CAMERA_SMOOTHING;
                
                // Clamp camera to max offset from center
                cameraX = Math.max(-CAMERA_MAX_OFFSET, Math.min(CAMERA_MAX_OFFSET, cameraX));
                cameraY = Math.max(-CAMERA_MAX_OFFSET, Math.min(CAMERA_MAX_OFFSET, cameraY));
            }
            
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
                
                // Increment hit counter
                bossHitCount++;
                
                // Progressive damage effects - more smoke and fire with each hit
                int particleMultiplier = bossHitCount; // 1x, 2x, 3x particles
                
                // Create impact particles at collision point (between player and boss)
                if (enableParticles) {
                    double impactX = (player.getX() + currentBoss.getX()) / 2;
                    double impactY = (player.getY() + currentBoss.getY()) / 2;
                    
                    // Bright white/yellow impact flash (scales with hit count)
                    for (int i = 0; i < 30 * particleMultiplier; i++) {
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
                    
                    // Smoke particles (more with each hit)
                    for (int i = 0; i < 15 * particleMultiplier; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 0.5 + Math.random() * 2;
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            new Color(80, 80, 80, 150), 40, 8,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Fire particles (more with each hit)
                    for (int i = 0; i < 20 * particleMultiplier; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 1 + Math.random() * 4;
                        Color fireColor = Math.random() < 0.5 ? BOSS_FIRE : BOSS_FIRE_BRIGHT;
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            fireColor, 30, 5,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Metal debris particles (visual damage on plane)
                    for (int i = 0; i < 25 * particleMultiplier; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 2 + Math.random() * 5;
                        Color debrisColor = new Color(160, 160, 170, 200);
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            debrisColor, 25, 4,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Sparks from plane damage
                    for (int i = 0; i < 30 * particleMultiplier; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 3 + Math.random() * 6;
                        Color sparkColor = new Color(255, 220, 100, 220);
                        addParticle(
                            currentBoss.getX(), currentBoss.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            sparkColor, 20, 3,
                            Particle.ParticleType.SPARK
                        );
                    }
                    
                    // Large explosion rings at impact (scales with hits)
                    for (int i = 0; i < 5; i++) {
                        addParticle(
                            impactX, impactY, 0, 0,
                            new Color(255, 150 - i * 20, 50, 220 - i * 40), 
                            40 + i * 10, 
                            40 + i * 25 + (particleMultiplier * 10),
                            Particle.ParticleType.EXPLOSION
                        );
                    }
                }
                
                // Reset vulnerability
                bossVulnerable = false;
                invulnerabilityTimer = 90; // 1.5 seconds before next vulnerability window
                
                screenShakeIntensity = 20 + (bossHitCount * 8); // More shake with each hit
                
                // Check if boss is defeated (2 hits for mini, 3 for mega)
                int maxHits = currentBoss.isMegaBoss() ? 3 : 2;
                if (bossHitCount >= maxHits) {
                    // Award points and money
                    int winBonus = 1000 + (gameData.getCurrentLevel() * 500) + (dodgeCombo * 100);
                    gameData.addScore(winBonus);
                    
                    int moneyReward = currentBoss.getMoneyReward();
                    
                    // Apply LUCKY_CHARM multiplier if equipped
                    if (equippedItem != null && equippedItem.getType() == ActiveItem.ItemType.LUCKY_CHARM) {
                        moneyReward = (int)(moneyReward * 1.5); // 50% bonus
                    }
                    
                    gameData.addRunMoney(moneyReward);
                    gameData.addTotalMoney(moneyReward);
                    
                    // Start boss death animation
                    bossDeathAnimation = true;
                    deathAnimationTimer = DEATH_ANIMATION_DURATION;
                    bossDeathScale = 1.0;
                    bossDeathRotation = 0;
                    bossKillTime = gameTimeSeconds;
                    
                    // Make player disappear (missile hit)
                    player = null;
                    
                    // Massive final explosion
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
                } else {
                    // Non-fatal hit - delay respawn and show explosion
                    double hitX = (player.getX() + currentBoss.getX()) / 2;
                    double hitY = (player.getY() + currentBoss.getY()) / 2;
                    player = null; // Remove player temporarily
                    waitingForRespawn = true;
                    respawnDelayTimer = RESPAWN_DELAY;
                    
                    // Huge screen shake for explosion
                    screenShakeIntensity = 20;
                    
                    // Create explosion at hit location
                    if (enableParticles) {
                        // Large explosion particles
                        for (int i = 0; i < 50; i++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 2 + Math.random() * 6;
                            Color expColor = Math.random() < 0.5 ? FIRE_ORANGE : FIRE_YELLOW;
                            addParticle(
                                hitX, hitY,
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                expColor, 40, 10,
                                Particle.ParticleType.SPARK
                            );
                        }
                        
                        // Explosion rings
                        for (int i = 0; i < 4; i++) {
                            addParticle(
                                hitX, hitY, 0, 0,
                                new Color(255, 150 - i * 30, 50, 220 - i * 50), 
                                30 + i * 10, 
                                30 + i * 15,
                                Particle.ParticleType.EXPLOSION
                            );
                        }
                    }
                    
                    // Reset vulnerability
                    bossVulnerable = false;
                    invulnerabilityTimer = 90; // 1.5 seconds before next vulnerability window
                    
                    screenShakeIntensity = 20 + (bossHitCount * 8); // More shake with each hit
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
                
                // Check if this is a mega boss (every 3rd level)
                int currentLevel = gameData.getCurrentLevel();
                if (currentLevel % 3 == 0 && !gameData.getDefeatedBosses()[currentLevel - 1]) {
                    // Unlock item before transitioning
                    gameData.unlockNextItem();
                    // Get the newly unlocked item for display
                    java.util.List<ActiveItem.ItemType> unlockedItems = gameData.getUnlockedItems();
                    if (!unlockedItems.isEmpty()) {
                        ActiveItem newItem = new ActiveItem(unlockedItems.get(unlockedItems.size() - 1));
                        unlockedItemName = newItem.getName();
                    }
                    // Equip first item if this is the first unlock
                    if (unlockedItems.size() == 1) {
                        gameData.equipItem(0);
                    }
                    // Trigger animation
                    itemUnlockAnimation = true;
                    itemUnlockTimer = ITEM_UNLOCK_DURATION;
                }
                
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
        
        // Update boss with delta time (but not during death animation, intro, or respawn delay)
        if (currentBoss != null && !bossDeathAnimation && !introPanActive && player != null) {
            currentBoss.update(bullets, player, WIDTH, HEIGHT, deltaTime, particles);
            beamAttacks = currentBoss.getBeamAttacks();
            
            // Add continuous flame and smoke particles from damaged boss
            if (bossHitCount > 0 && enableParticles) {
                // More frequent particles with each hit
                double spawnChance = 0.2 * bossHitCount; // 20% per hit level
                
                if (Math.random() < spawnChance) {
                    // Flame particles
                    double angle = Math.PI / 2 + (Math.random() - 0.5) * 0.8; // Downward
                    double speed = 0.5 + Math.random() * 1.5;
                    Color flameColor = Math.random() < 0.6 ? FIRE_ORANGE : FIRE_RED;
                    addParticle(
                        currentBoss.getX() + (Math.random() - 0.5) * 40,
                        currentBoss.getY() + (Math.random() - 0.5) * 30,
                        Math.cos(angle) * speed,
                        Math.sin(angle) * speed,
                        flameColor, 35 + (int)(Math.random() * 20), 6 + Math.random() * 4,
                        Particle.ParticleType.TRAIL
                    );
                }
                
                if (Math.random() < spawnChance * 0.7) {
                    // Smoke particles (darker, slower)
                    double angle = Math.PI / 2 + (Math.random() - 0.5) * 0.6;
                    double speed = 0.3 + Math.random() * 1.0;
                    addParticle(
                        currentBoss.getX() + (Math.random() - 0.5) * 35,
                        currentBoss.getY() + (Math.random() - 0.5) * 25,
                        Math.cos(angle) * speed,
                        Math.sin(angle) * speed,
                        new Color(60, 60, 60, 180), 50 + (int)(Math.random() * 30), 8 + Math.random() * 5,
                        Particle.ParticleType.TRAIL
                    );
                }
            }
        }
        
        // Handle respawn delay after non-fatal boss hit
        if (waitingForRespawn) {
            respawnDelayTimer -= deltaTime;
            
            if (respawnDelayTimer <= 0) {
                // Respawn player at bottom with shield
                player = new Player(WIDTH / 2, HEIGHT - 200, gameData.getActiveSpeedLevel());
                shieldActive = true;
                playerInvincible = true;
                respawnInvincibilityTimer = 180; // 3 seconds of invincibility after respawn
                waitingForRespawn = false;
                
                // Add respawn flash effect
                if (enableParticles) {
                    // Bright spawn flash at new player position
                    for (int i = 0; i < 60; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 3 + Math.random() * 7;
                        Color spawnColor = new Color(100, 200, 255, 220);
                        addParticle(
                            WIDTH / 2, HEIGHT - 200,
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            spawnColor, 35, 12,
                            Particle.ParticleType.SPARK
                        );
                    }
                    // Shield activation rings
                    for (int i = 0; i < 4; i++) {
                        addParticle(
                            WIDTH / 2, HEIGHT - 200, 0, 0,
                            new Color(136, 192, 208, 220 - i * 45), 40 + i * 12, 35 + i * 20,
                            Particle.ParticleType.EXPLOSION
                        );
                    }
                }
            }
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
            
            // Apply time slow from active item
            if (equippedItem != null && equippedItem.isActive() && 
                equippedItem.getType() == ActiveItem.ItemType.TIME_SLOW) {
                bullet.applySlow(0.5); // 50% speed
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
                    // Check for active item invincibility (DASH or INVINCIBILITY)
                    if (playerInvincible) {
                        // Invincible - bullets pass through
                        continue;
                    }
                    
                    // Check for shield
                    if (shieldActive) {
                        // Shield blocks the hit
                        shieldActive = false;
                        bullets.remove(bullet);
                        returnBulletToPool(bullet);
                        
                        // Create shield break particles
                        if (enableParticles) {
                            for (int j = 0; j < 15; j++) {
                                double angle = Math.random() * TWO_PI;
                                double speed = 2 + Math.random() * 4;
                                addParticle(
                                    player.getX(), player.getY(),
                                    Math.cos(angle) * speed, Math.sin(angle) * speed,
                                    new Color(136, 192, 208), 25, 6,
                                    Particle.ParticleType.SPARK
                                );
                            }
                        }
                        
                        screenShakeIntensity = 5;
                        continue;
                    }
                    
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
    
    // Check for close calls with bullets (graze detection)
    private void checkBulletGrazes(Player player) {
        List<Bullet> nearbyBullets = getNearbyBullets(player.getX(), player.getY());
        double playerRadius = player.getSize() / 2.0;
        
        for (Bullet bullet : nearbyBullets) {
            if (bullet.hasGrazed()) continue; // Only count each graze once
            
            double dx = bullet.getX() - player.getX();
            double dy = bullet.getY() - player.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);
            double bulletRadius = bullet.getSize() / 2.0;
            double hitDistance = playerRadius + bulletRadius;
            
            // Check if bullet is in graze zone (close but not hitting)
            if (distance > hitDistance && distance < hitDistance + GRAZE_DISTANCE) {
                bullet.setGrazed(true);
                
                // Award graze bonus
                int grazeBonus = 10;
                grazeScore += grazeBonus;
                gameData.addScore(grazeBonus);
                
                // Spawn graze particles
                if (enableParticles && Math.random() < 0.3) {
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 0.5 + Math.random() * 1.5;
                        addParticle(
                            player.getX() + Math.cos(angle) * playerRadius,
                            player.getY() + Math.sin(angle) * playerRadius,
                            Math.cos(angle) * speed,
                            Math.sin(angle) * speed,
                            new Color(100, 200, 255, 200),
                            20, 3,
                            Particle.ParticleType.SPARK
                        );
                    }
                }
            }
        }
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
        
        // Draw previous state if transitioning
        if (stateTransitionProgress < 1.0f && previousState != null) {
            // Draw old state with fade out
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f - stateTransitionProgress));
            drawState(g2d, previousState);
            
            // Draw new state with fade in
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, stateTransitionProgress));
            drawState(g2d, gameState);
            
            // Reset composite
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } else {
            drawState(g2d, gameState);
        }
    }
    
    private void drawState(Graphics2D g2d, GameState state) {
        switch (state) {
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
                renderer.drawSettings(g2d, WIDTH, HEIGHT, selectedSettingsItem, gradientTime, settingsScroll);
                break;
            case LEVEL_SELECT:
                renderer.drawLevelSelect(g2d, WIDTH, HEIGHT, gameData.getCurrentLevel(), gameData.getMaxUnlockedLevel(), gradientTime, levelSelectScroll);
                break;
            case PLAYING:
                // Apply screen shake
                g2d.translate(screenShakeX, screenShakeY);
                renderer.drawGame(g2d, WIDTH, HEIGHT, player, currentBoss, bullets, particles, beamAttacks, gameData.getCurrentLevel(), gradientTime, bossVulnerable, vulnerabilityTimer, dodgeCombo, comboTimer > 0, bossDeathAnimation, bossDeathScale, bossDeathRotation, gameTimeSeconds, currentFPS, shieldActive, playerInvincible, bossHitCount, cameraX, cameraY, introPanActive, bossFlashTimer, screenFlashTimer);
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
                // Draw item unlock animation if active
                if (itemUnlockAnimation) {
                    drawItemUnlockAnimation(g2d, WIDTH, HEIGHT);
                }
                break;
            case SHOP:
                renderer.drawShop(g2d, WIDTH, HEIGHT, gradientTime);
                break;
            case DEBUG:
                renderer.drawDebug(g2d, WIDTH, HEIGHT, gradientTime);
                break;
        }
    }
    
    // Helper method to transition to a new state
    private void transitionToState(GameState newState) {
        if (gameState != newState) {
            previousState = gameState;
            gameState = newState;
            stateTransitionProgress = 0.0f;
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
                targetLoadingProgress = 10;
                repaint();
                
                // Create renderer (this loads backgrounds and overlay)
                renderer = new Renderer(gameData, shopManager);
                targetLoadingProgress = 80;
                repaint();
                
                // Small delay to ensure everything is ready
                Thread.sleep(200);
                targetLoadingProgress = 100;
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
        // Smooth interpolation of progress
        double smoothSpeed = 0.15; // Higher = faster interpolation
        displayedLoadingProgress += (targetLoadingProgress - displayedLoadingProgress) * smoothSpeed;
        int smoothProgress = (int)displayedLoadingProgress;
        
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
        
        // Loading text with animated dots
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        int dotCount = (int)((System.currentTimeMillis() / 300) % 4);
        String loadingText = "Loading" + ".".repeat(dotCount);
        fm = g.getFontMetrics();
        g.drawString(loadingText, (width - fm.stringWidth("Loading...")) / 2, height / 2 + 20);
        
        // Progress bar
        int barWidth = 400;
        int barHeight = 30;
        int barX = (width - barWidth) / 2;
        int barY = height / 2 + 60;
        
        // Background
        g.setColor(new Color(60, 60, 70));
        g.fillRoundRect(barX, barY, barWidth, barHeight, 15, 15);
        
        // Progress fill with smooth animation
        int fillWidth = (int)(barWidth * (smoothProgress / 100.0));
        if (fillWidth > 0) {
            // Animated glow effect
            double glowPulse = Math.sin(System.currentTimeMillis() / 200.0) * 0.2 + 0.8;
            int r = (int)(136 * glowPulse);
            int gb = (int)(192 * glowPulse);
            int b = (int)(208 * glowPulse);
            g.setColor(new Color(r, gb, b));
            g.fillRoundRect(barX, barY, fillWidth, barHeight, 15, 15);
            
            // Brighter leading edge
            if (smoothProgress < 100) {
                int edgeWidth = 20;
                int edgeX = Math.max(barX, barX + fillWidth - edgeWidth);
                GradientPaint edgeGlow = new GradientPaint(
                    edgeX, barY, new Color(255, 255, 255, 100),
                    edgeX + edgeWidth, barY, new Color(136, 192, 208, 0)
                );
                g.setPaint(edgeGlow);
                g.fillRoundRect(edgeX, barY, Math.min(edgeWidth, fillWidth), barHeight, 15, 15);
            }
        }
        
        // Border
        g.setColor(new Color(200, 200, 200));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(barX, barY, barWidth, barHeight, 15, 15);
        
        // Percentage
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        String percentText = smoothProgress + "%";
        fm = g.getFontMetrics();
        g.drawString(percentText, (width - fm.stringWidth(percentText)) / 2, barY + barHeight + 30);
    }
    
    // Handle active item effects during gameplay
    private void handleActiveItemEffects(ActiveItem item, double deltaTime) {
        switch (item.getType()) {
            case DASH:
                // Apply speed boost and invincibility during dash
                playerInvincible = true;
                dashSpeedMultiplier = 5.0;
                if (player != null) {
                    player.applyDashBoost(dashSpeedMultiplier);
                }
                break;
                
            case SHOCKWAVE:
                // Push all bullets away from player (instant effect)
                if (player != null) {
                    for (Bullet bullet : bullets) {
                        double dx = bullet.getX() - player.getX();
                        double dy = bullet.getY() - player.getY();
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        
                        if (distance < 300) { // Shockwave radius
                            // Push bullet away
                            double angle = Math.atan2(dy, dx);
                            double pushForce = 10 * (1.0 - distance / 300);
                            bullet.applyForce(Math.cos(angle) * pushForce, Math.sin(angle) * pushForce);
                        }
                    }
                    
                    // Create shockwave particles
                    if (enableParticles) {
                        for (int i = 0; i < 30; i++) {
                            double angle = Math.random() * TWO_PI;
                            double speed = 5 + Math.random() * 5;
                            addParticle(
                                player.getX(), player.getY(),
                                Math.cos(angle) * speed, Math.sin(angle) * speed,
                                new Color(163, 190, 140), 30, 8,
                                Particle.ParticleType.SPARK
                            );
                        }
                    }
                    
                    screenShakeIntensity = 8;
                }
                break;
                
            case SHIELD:
                // Shield is active - will tank next hit
                shieldActive = true;
                break;
                
            case BOMB:
                // Clear all bullets (instant effect)
                int clearedBullets = bullets.size();
                for (Bullet bullet : bullets) {
                    returnBulletToPool(bullet);
                }
                bullets.clear();
                
                // Award score for cleared bullets
                gameData.addScore(clearedBullets * 5);
                
                // Create massive explosion effect
                if (enableParticles && player != null) {
                    for (int i = 0; i < 50; i++) {
                        double angle = Math.random() * TWO_PI;
                        double speed = 3 + Math.random() * 8;
                        Color fireColor = Math.random() < 0.5 ? FIRE_ORANGE : FIRE_YELLOW;
                        addParticle(
                            player.getX(), player.getY(),
                            Math.cos(angle) * speed, Math.sin(angle) * speed,
                            fireColor, 40, 8,
                            Particle.ParticleType.SPARK
                        );
                    }
                }
                
                screenShakeIntensity = 15;
                break;
                
            case MAGNET:
                // Pull nearby bullets toward player for scoring
                if (player != null) {
                    for (Bullet bullet : bullets) {
                        double dx = player.getX() - bullet.getX();
                        double dy = player.getY() - bullet.getY();
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        
                        if (distance < 400) { // Magnet radius
                            double angle = Math.atan2(dy, dx);
                            double pullForce = 0.5 * (1.0 - distance / 400);
                            bullet.applyForce(Math.cos(angle) * pullForce, Math.sin(angle) * pullForce);
                        }
                    }
                }
                break;
                
            case TIME_SLOW:
                // Bullets move at 50% speed (applied in bullet update loop)
                // This effect is checked in the bullet collision section
                break;
                
            case LASER_BEAM:
                // Fire a damaging laser beam upward
                // Damage bullets in path
                if (player != null) {
                    double laserX = player.getX();
                    double laserWidth = 40;
                    
                    for (int i = bullets.size() - 1; i >= 0; i--) {
                        Bullet bullet = bullets.get(i);
                        double bulletX = bullet.getX();
                        double bulletY = bullet.getY();
                        
                        // Check if bullet is in laser path
                        if (Math.abs(bulletX - laserX) < laserWidth / 2 && bulletY < player.getY()) {
                            bullets.remove(i);
                            returnBulletToPool(bullet);
                            gameData.addScore(10);
                            
                            // Create destruction particles
                            if (enableParticles) {
                                for (int j = 0; j < 5; j++) {
                                    double angle = Math.random() * TWO_PI;
                                    double speed = 1 + Math.random() * 3;
                                    addParticle(
                                        bulletX, bulletY,
                                        Math.cos(angle) * speed, Math.sin(angle) * speed,
                                        new Color(235, 203, 139), 15, 4,
                                        Particle.ParticleType.SPARK
                                    );
                                }
                            }
                        }
                    }
                }
                break;
                
            case INVINCIBILITY:
                // Player is invincible
                playerInvincible = true;
                break;
                
            default:
                break;
        }
    }
    
    private void drawItemUnlockAnimation(Graphics2D g, int width, int height) {
        // Calculate animation progress (0.0 to 1.0)
        float progress = 1.0f - ((float) itemUnlockTimer / ITEM_UNLOCK_DURATION);
        
        // Calculate dismiss progress (1.0 = visible, 0.0 = gone)
        float dismissMultiplier = 1.0f;
        if (itemUnlockDismissing) {
            dismissMultiplier = (float) itemUnlockDismissTimer / ITEM_DISMISS_DURATION;
        }
        
        // Full dark overlay with fade
        int overlayAlpha = (int)(200 * Math.min(progress * 2, 1.0f) * dismissMultiplier);
        g.setColor(new Color(0, 0, 0, Math.min(overlayAlpha, 200)));
        g.fillRect(0, 0, width, height);
        
        // Calculate position (slide up from bottom, slide down when dismissing)
        int centerX = width / 2;
        int startY = height + 300;
        int endY = height / 2;
        int dismissOffset = (int)((1.0f - dismissMultiplier) * 400); // Slide down when dismissing
        int currentY = (int)(startY + (endY - startY) * Math.pow(progress, 0.7)) + dismissOffset;
        
        // Scale effect (start small, grow to full size, shrink when dismissing)
        float scale;
        if (progress < 0.4f) {
            scale = (float)Math.pow(progress / 0.4f, 0.5); // Smooth growth
        } else {
            scale = 1.0f;
        }
        scale *= dismissMultiplier; // Shrink during dismiss
        
        // Multiple glow layers for more impact
        for (int i = 0; i < 3; i++) {
            int glowSize = Math.max(1, (int)((500 + i * 100) * scale)); // Ensure radius is at least 1
            float pulseSpeed = 2.0f + i * 0.5f;
            float pulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 200.0 * pulseSpeed)) * 0.3f + 0.7f;
            
            RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, currentY,
                glowSize,
                new float[]{0.0f, 0.6f, 1.0f},
                new Color[]{
                    new Color(235, 203, 139, (int)(80 * scale * pulse * dismissMultiplier)),
                    new Color(163, 190, 140, (int)(40 * scale * pulse * dismissMultiplier)),
                    new Color(163, 190, 140, 0)
                }
            );
            g.setPaint(glowPaint);
            g.fillOval(centerX - glowSize, currentY - glowSize, glowSize * 2, glowSize * 2);
        }
        
        // Animated particles around the box
        if (progress > 0.3f && enableParticles) {
            int particleCount = 30;
            for (int i = 0; i < particleCount; i++) {
                double angle = (System.currentTimeMillis() / 50.0 + i * (360.0 / particleCount)) * Math.PI / 180.0;
                int radius = (int)(200 * scale);
                int px = (int)(centerX + Math.cos(angle) * radius);
                int py = (int)(currentY + Math.sin(angle) * radius * 0.7);
                int size = (int)(6 * scale);
                
                float particleAlpha = (float)Math.abs(Math.sin(angle * 3 + System.currentTimeMillis() / 100.0));
                g.setColor(new Color(235, 203, 139, (int)(200 * particleAlpha * scale * dismissMultiplier)));
                g.fillOval(px - size/2, py - size/2, size, size);
            }
        }
        
        // Draw box with better styling
        int boxWidth = (int)(700 * scale);
        int boxHeight = (int)(280 * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = currentY - boxHeight / 2;
        
        // Box shadow
        g.setColor(new Color(0, 0, 0, (int)(100 * Math.min(progress * 2, 1.0f))));
        g.fillRoundRect(boxX + 5, boxY + 5, boxWidth, boxHeight, 25, 25);
        
        // Box background with gradient
        GradientPaint boxGradient = new GradientPaint(
            boxX, boxY, new Color(40, 40, 50, (int)(240 * Math.min(progress * 2, 1.0f))),
            boxX, boxY + boxHeight, new Color(25, 25, 35, (int)(240 * Math.min(progress * 2, 1.0f)))
        );
        g.setPaint(boxGradient);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 25, 25);
        
        // Animated border with rainbow glow
        float borderPulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 150.0));
        int borderR = (int)(163 + (235 - 163) * borderPulse);
        int borderG = (int)(190 + (203 - 190) * borderPulse);
        int borderB = (int)(140 + (139 - 140) * borderPulse);
        g.setColor(new Color(borderR, borderG, borderB, (int)(255 * Math.min(progress * 2, 1.0f))));
        g.setStroke(new BasicStroke(5));
        g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 25, 25);
        
        // Inner glow border
        g.setColor(new Color(255, 255, 255, (int)(100 * borderPulse * Math.min(progress * 2, 1.0f))));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(boxX + 8, boxY + 8, boxWidth - 16, boxHeight - 16, 20, 20);
        
        // Text content
        if (progress > 0.25f) {
            float textAlpha = Math.min((progress - 0.25f) / 0.3f, 1.0f) * dismissMultiplier;
            
            // "NEW ITEM UNLOCKED!" with shadow
            g.setFont(new Font("Arial", Font.BOLD, (int)(56 * scale)));
            String titleText = "NEW ITEM UNLOCKED!";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = centerX - titleFm.stringWidth(titleText) / 2;
            int titleY = currentY - (int)(80 * scale);
            
            // Title shadow
            g.setColor(new Color(0, 0, 0, (int)(150 * textAlpha)));
            g.drawString(titleText, titleX + 2, titleY + 2);
            
            // Title text with pulse
            float titlePulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 200.0)) * 0.3f + 0.7f;
            g.setColor(new Color(235, 203, 139, (int)(255 * textAlpha * titlePulse)));
            g.drawString(titleText, titleX, titleY);
            
            // Item name with shadow
            g.setFont(new Font("Arial", Font.BOLD, (int)(44 * scale)));
            FontMetrics itemFm = g.getFontMetrics();
            int itemX = centerX - itemFm.stringWidth(unlockedItemName) / 2;
            int itemY = currentY - (int)(10 * scale);
            
            g.setColor(new Color(0, 0, 0, (int)(150 * textAlpha)));
            g.drawString(unlockedItemName, itemX + 2, itemY + 2);
            
            g.setColor(new Color(163, 190, 140, (int)(255 * textAlpha)));
            g.drawString(unlockedItemName, itemX, itemY);
            
            // Item description
            ActiveItem currentItem = gameData.getEquippedItem();
            if (currentItem != null && progress > 0.4f) {
                g.setFont(new Font("Arial", Font.PLAIN, (int)(24 * scale)));
                String description = currentItem.getDescription();
                FontMetrics descFm = g.getFontMetrics();
                int descX = centerX - descFm.stringWidth(description) / 2;
                int descY = currentY + (int)(50 * scale);
                
                g.setColor(new Color(200, 200, 200, (int)(220 * textAlpha)));
                g.drawString(description, descX, descY);
            }
            
            // "Press SPACE to continue" hint
            if (progress > 0.8f) {
                g.setFont(new Font("Arial", Font.PLAIN, (int)(20 * scale)));
                String hintText = "Press SPACE to continue";
                FontMetrics hintFm = g.getFontMetrics();
                int hintX = centerX - hintFm.stringWidth(hintText) / 2;
                int hintY = currentY + (int)(100 * scale);
                
                float hintPulse = (float)Math.abs(Math.sin(System.currentTimeMillis() / 300.0));
                g.setColor(new Color(150, 150, 150, (int)(200 * hintPulse)));
                g.drawString(hintText, hintX, hintY);
            }
        }
    }
}
