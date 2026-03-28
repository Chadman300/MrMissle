import config.ColorPalette;
import config.FontPalette;
import config.HUDLayout;
import config.UIScale;
import config.UITheme;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;



public class Renderer {

    private GameData gameData;

    private ShopManager shopManager;

    private PassiveUpgradeManager passiveUpgradeManager;

    private LeaderboardManager leaderboardManager;

    

    // Menu buttons

    private UIButton[] menuButtons;

    private UIButton[] shopButtons;

    private UIButton[] statsButtons;

    private UIButton[] settingsButtons;

    private UIButton[] pauseButtons;

    // Mode select card bounds (populated during drawModeSelect)
    private java.awt.Rectangle[] modeCardBounds = new java.awt.Rectangle[0];

    // Warning dialog button bounds (populated during drawSettings when warning shown)
    private java.awt.Rectangle[] warningButtonBounds = new java.awt.Rectangle[3];

    

    // Settings UI click target tracking (populated during rendering)

    private int[][] pillClickTargets;  // [settingIndex] = {x0, w0, x1, w1, ...} or null

    private int[] pillClickTargetY;    // [settingIndex] = screen Y of pill row

    private int pillClickH = UIScale.px(26);       // pill option height

    private int[] sliderMinusBtnX;     // [settingIndex] = screen X of minus button

    private int[] sliderPlusBtnX;      // [settingIndex] = screen X of plus button

    private int[] sliderBtnYPos;       // [settingIndex] = screen Y of buttons

    private int sliderBtnSize = UIScale.px(26);    // +/- button size

    private int[] sliderTrackStartX;   // [settingIndex] = screen X of slider track left edge
    private int[] sliderTrackEndX;     // [settingIndex] = screen X of slider track right edge

    // HUD Layout Editor
    public HUDLayoutEditor hudLayoutEditor = new HUDLayoutEditor();
    public HUDLayout hudLayout; // active layout reference, set from Game

    private boolean showcasePauseMode = false;

    private int activePauseButtonCount = 3;

    private int statsActiveItemDisplayIndex = 0;

    
    // Help & Tutorial screen buttons
    public UIButton helpShowcaseButton;
    public UIButton helpTutorialButton;
    public int helpSelectedButton = 0; // 0 = Showcase, 1 = Tutorial
    public boolean tutorialMode = false;
    public int tutorialStep = 0;
    public int tutorialHighlightElement = -1; // 3=missiles, 4=active item, 5=boss health
    public int tutorialHighlightTimer = 0; // Frames remaining for highlight effect
    public boolean tutorialPopupActive = false;
    // Saved screen-space bounds of highlighted HUD elements (set during drawGame)
    public int tutorialHLX, tutorialHLY, tutorialHLW, tutorialHLH;

    

    // Number of background sets available

    private static final int BACKGROUND_SET_COUNT = 8;

    

    // Parallax background layers (BACKGROUND_SET_COUNT sets x 6 layers each)

    private static BufferedImage[][] backgroundLayers = new BufferedImage[BACKGROUND_SET_COUNT][6];

    private static boolean backgroundsLoaded = false;

    private double[] layerScrollOffsets = new double[6]; // Scroll offset for each layer

    // Parallax speeds for each layer (furthest to closest) â€” static to avoid per-frame allocation
    private static final double[] PARALLAX_SPEEDS = {0.1, 0.2, 0.35, 0.5, 0.7, 1.0};

    

    // Async background rendering â€” renders parallax on worker thread while game thread sleeps
    private BufferedImage bgBuffer;               // Off-screen buffer for background
    private volatile boolean bgBufferReady;        // True when bgBuffer has valid content
    private volatile int bgBufferLevel = -1;       // Level the buffer was rendered for
    private java.util.concurrent.Future<?> bgRenderFuture;  // Handle to async render task
    private double[] bgScrollSnapshot = new double[6];      // Snapshot of offsets for async render

    // Background overlay

    private static BufferedImage overlayImage = null;

    private static boolean overlayLoaded = false;



    // Pre-cached rotated+scaled plane sprites for level select (avoids per-frame AffineTransform rotation)
    // Key: (level << 16) | (spriteWidth & 0xFFFF), value: 180Â°-rotated & scaled BufferedImage
    private static final java.util.HashMap<Long, BufferedImage> planeSpriteCache = new java.util.HashMap<>();

    // Pre-rendered scanline tile for spinning beam (matches BeamAttack's SCANLINE_TILE_H)
    private static final BufferedImage SPIN_BEAM_SCANLINE_TILE;
    static {
        SPIN_BEAM_SCANLINE_TILE = new BufferedImage(8, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = SPIN_BEAM_SCANLINE_TILE.createGraphics();
        sg.setColor(new Color(255, 200, 200, 100));
        sg.fillRect(0, 0, 2, 1);
        sg.dispose();
    }

    /** Get a pre-rotated (180Â°) and scaled plane sprite for the level select screen. */
    private static BufferedImage getCachedPlaneSprite(BufferedImage source, int level, int targetW, int targetH) {
        long key = ((long)level << 32) | ((long)targetW << 16) | (targetH & 0xFFFFL);
        BufferedImage cached = planeSpriteCache.get(key);
        if (cached == null) {
            cached = Game.createOptimalImage(targetW, targetH, true);
            Graphics2D cg = cached.createGraphics();
            cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // Rotate 180Â° around center, then draw scaled
            cg.translate(targetW / 2.0, targetH / 2.0);
            cg.rotate(Math.PI);
            cg.drawImage(source, -targetW / 2, -targetH / 2, targetW, targetH, null);
            cg.dispose();
            planeSpriteCache.put(key, cached);
        }
        return cached;
    }





    

    // Cached rendering objects for performance (using ColorPalette for shared colors)
    private static final AlphaComposite ALPHA_FULL = ColorPalette.ALPHA_FULL;
    private static final AlphaComposite ALPHA_HALF = ColorPalette.ALPHA_HALF;
    private static final AlphaComposite ALPHA_THIRD = ColorPalette.ALPHA_THIRD;
    private static final Color AFTERIMAGE_COLOR = ColorPalette.AFTERIMAGE_COLOR;
    private static final Color SHIELD_GLOW = ColorPalette.SHIELD_GLOW;
    private static final Color SHIELD_RING = ColorPalette.SHIELD_RING;
    private static final Color SHIELD_CORE = ColorPalette.SHIELD_CORE;

    private static final BasicStroke STROKE_1 = RenderCache.getStroke(1f);

    private static final BasicStroke STROKE_2 = RenderCache.getStroke(2f);

    private static final BasicStroke STROKE_3 = RenderCache.getStroke(3f);
    // Boss shield arc strokes (cached to avoid 32 new BasicStroke per frame)
    private static final BasicStroke SHIELD_STROKE_OUTER = new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke SHIELD_STROKE_MAIN = new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    // Cached rounded strokes used per-frame in shield arcs, HUD brackets, etc.
    private static final BasicStroke ROUND_STROKE_14 = new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ROUND_STROKE_10 = new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ROUND_STROKE_8 = new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ROUND_STROKE_6 = new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ROUND_STROKE_3_5 = new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ROUND_STROKE_2_5 = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    // Shockwave arc strokes (cached â€” was 5 new BasicStroke per frame in loop)
    private static final BasicStroke SHOCKWAVE_STROKE_0 = new BasicStroke(12f);
    private static final BasicStroke SHOCKWAVE_STROKE_1 = new BasicStroke(10.5f);
    private static final BasicStroke SHOCKWAVE_STROKE_2 = new BasicStroke(9f);
    private static final BasicStroke SHOCKWAVE_STROKE_3 = new BasicStroke(7.5f);
    private static final BasicStroke SHOCKWAVE_STROKE_4 = new BasicStroke(6f);
    private static final BasicStroke[] SHOCKWAVE_STROKES = { SHOCKWAVE_STROKE_0, SHOCKWAVE_STROKE_1, SHOCKWAVE_STROKE_2, SHOCKWAVE_STROKE_3, SHOCKWAVE_STROKE_4 };
    // Cached identity transform â€” avoids new AffineTransform() every frame
    private static final AffineTransform IDENTITY_TRANSFORM = new AffineTransform();

    // â”€â”€ Cached Colors for journey map & UI menus (avoid per-frame new Color()) â”€â”€
    private static final Color NODE_COMPLETED_GOLD = new Color(220, 180, 50);
    private static final Color NODE_CURRENT_BLUE = RenderCache.BLUE_100_200_255;
    private static final Color NODE_LOCKED_GRAY = new Color(60, 60, 70);
    private static final Color NODE_SHADOW = new Color(0, 0, 0, 80);
    private static final Color NODE_COMPLETED_RING = new Color(255, 200, 50);
    private static final Color NODE_BORDER_CURRENT = new Color(150, 255, 150);
    private static final Color NODE_BORDER_COMPLETED = new Color(220, 180, 60);
    private static final Color NODE_BORDER_LOCKED = new Color(70, 70, 80);
    private static final Color NODE_LOCKED_TEXT = new Color(80, 80, 85);
    private static final Color NODE_LOCKED_ICON = new Color(100, 100, 110);
    private static final Color NODE_CLEARED_LABEL = new Color(220, 190, 60);
    private static final Color PATH_LINE_COLOR = new Color(50, 55, 65);
    private static final Color ARROW_DIM = new Color(150, 150, 160);
    private static final Color BADGE_GREEN = new Color(40, 160, 60);
    // Node fill colors (pre-blended midpoints of former gradients for perf)
    private static final Color MEGA_COMPLETED = new Color(145, 105, 50);
    private static final Color MEGA_CURRENT = new Color(175, 100, 200);
    private static final Color MEGA_LOCKED = new Color(50, 40, 60);
    private static final Color MINI_COMPLETED = new Color(110, 93, 40);
    private static final Color MINI_CURRENT = new Color(70, 175, 90);
    private static final Color MINI_LOCKED = new Color(45, 45, 50);
    // Glow colors for sprite preview
    private static final Color GLOW_MEGA_BOSS = new Color(200, 150, 255);
    private static final Color GLOW_COMPLETED = new Color(100, 255, 150);
    private static final Color GLOW_CURRENT = RenderCache.BLUE_100_200_255;
    private static final Color GLOW_AVAILABLE = new Color(255, 255, 200);
    // Selection glow colors
    private static final Color SEL_GLOW_CURRENT = new Color(100, 255, 100);
    private static final Color SEL_GLOW_COMPLETED = RenderCache.BLUE_100_180_255;
    private static final Color SEL_GLOW_OTHER = new Color(255, 150, 100);
    // drawTitle ember color base
    private static final Color PANEL_BG = new Color(25, 30, 40, 240);
    private static final Color PANEL_BORDER_COMPLETED = new Color(80, 160, 80);
    private static final Color PANEL_BORDER_CURRENT = RenderCache.BLUE_100_200_255;
    private static final Color PANEL_BORDER_LOCKED = new Color(70, 70, 80);
    private static final Color PANEL_TEXT_NAME = new Color(230, 235, 245);
    private static final Color PANEL_MEGA_LABEL = new Color(255, 200, 100);
    private static final Color PANEL_DIM_LABEL = new Color(140, 150, 170);
    private static final Color PANEL_STATS_TEXT = new Color(160, 170, 180);
    private static final Color PANEL_LOCK_TEXT = new Color(120, 120, 130);
    private static final Color PANEL_NAV_HINT = new Color(100, 110, 130);
    // Endless mode colors
    private static final Color ENDLESS_NODE_BG = new Color(40, 35, 55);
    private static final Color ENDLESS_NODE_BORDER = new Color(120, 80, 180);
    private static final Color ENDLESS_GLOW = new Color(160, 100, 255);
    private static final Color ENDLESS_GRAY_TINT = new Color(100, 100, 120);
    private static final Color ENDLESS_QUESTION_MARK = new Color(200, 180, 255);
    private static final Color ENDLESS_ORBIT_PARTICLE = new Color(180, 150, 255);

    // Ã¢"â‚¬Ã¢"â‚¬ Cached Colors for in-level drawGame (avoid per-frame new Color()) Ã¢"â‚¬Ã¢"â‚¬
    // Player shield arcs (6 per shield segment, per-frame)
    private static final Color SHIELD_ARC_OUTER = new Color(60, 180, 255, 50);
    private static final Color SHIELD_ARC_MID = new Color(80, 200, 255, 90);
    private static final Color SHIELD_ARC_MAIN = new Color(100, 210, 255, 200);
    private static final Color SHIELD_ARC_EDGE = new Color(200, 240, 255, 220);
    private static final Color SHIELD_ARC_TIP = new Color(220, 250, 255, 240);
    private static final Color SHIELD_ARC_INNER = new Color(100, 200, 255, 25);
    private static final Color INVINCIBILITY_GLOW = new Color(255, 255, 200, 120);
    private static final Color BOSS_DEATH_FIRE = new Color(255, 100, 0);
    // Frost beam cached colors (avoid per-frame new Color() in frost beam rendering)
    private static final Color FROST_OUTER_GLOW = new Color(100, 180, 230);
    private static final Color FROST_SPIKE_WHITE = new Color(220, 245, 255);
    private static final Color BOSS_COOL_BLOOM = new Color(100, 150, 200);
    private static final Color BOSS_CALM_GLOW = new Color(80, 150, 255);
    private static final Color WORLD_EDGE_80 = new Color(0, 0, 0, 80);
    // Baked level bounds images (rendered once â€” eliminates 8 gradient fills per frame)
    private static BufferedImage bakedEdgeTop, bakedEdgeBottom, bakedEdgeLeft, bakedEdgeRight;
    private static BufferedImage bakedCornerTL, bakedCornerTR, bakedCornerBL, bakedCornerBR;
    private static boolean levelBoundsBaked = false;
    private static int bakedWorldW = -1, bakedWorldH = -1;
    // Baked background gradient image (refreshes every few frames instead of 3 GradientPaint fills/frame)
    private BufferedImage cachedBgGradient;
    private int cachedBgPaletteIdx = -1;
    private int bgGradientFrameCounter = 0;
    private double lastBgTime = Double.NaN;
    private static final int BG_GRADIENT_REFRESH_RATE = 4; // Rebuild every N frames when animated
    // Baked death vignette image (rendered once per screen size)
    private static BufferedImage bakedDeathVignette;
    private static int bakedDeathVigW = -1, bakedDeathVigH = -1;
    private static final Color PHASE_BAR_BG = new Color(40, 40, 50);
    private static final Color PHASE_ASSAULT_END = new Color(255, 150, 50);
    private static final Color PHASE_RECOVERY_START = new Color(50, 150, 255);
    private static final Color PHASE_RECOVERY_END = new Color(100, 200, 150);
    private static final Color MISSILE_SEG_GREEN = new Color(120, 210, 120);
    private static final Color MISSILE_SEG_GOLD_START = new Color(200, 170, 0);
    private static final Color MISSILE_SEG_GOLD_END = new Color(255, 225, 60);
    private static final Color DEATH_VIGNETTE_MID = new Color(180, 30, 30, 120);
    private static final Color DEATH_VIGNETTE_EDGE = new Color(200, 20, 20, 220);
    private static final Color RISK_BAR_BG = new Color(40, 40, 40, 200);
    private static final Color RISK_TIME_TEXT = new Color(220, 220, 220);
    // Pre-computed risk contract warning colors (20 steps from safe to danger)
    // Eliminates per-frame new Color() in the risk contract HUD
    private static final int RISK_WARNING_STEPS = 20;
    private static final Color[] RISK_WARNING_COLORS = new Color[RISK_WARNING_STEPS];
    static {
        for (int i = 0; i < RISK_WARNING_STEPS; i++) {
            float d = (float) i / (RISK_WARNING_STEPS - 1);
            RISK_WARNING_COLORS[i] = new Color(
                (int)(191 + d * 64),
                (int)(97 * (1.0 - d)),
                (int)(106 * (1.0 - d)),
                (int)(150 + d * 105)
            );
        }
    }
    // Cached missile segment gradient paints (rebuilt when barStartX changes)
    private int cachedMissileBarStartX = -1;
    private GradientPaint cachedMissileGradGreen, cachedMissileGradGold;
    private static final Color HP_GRADIENT_MEGA = new Color(200, 50, 50);
    // Cached boss bar gradient paints (rebuilt only when HUD layout changes barX)
    private static int cachedBossBarX = -1;
    private static GradientPaint cachedHPGradMega, cachedHPGradNormal;
    private static GradientPaint cachedPhaseGradAssault, cachedPhaseGradRecovery;
    // Cached announcement base colors (used with AlphaComposite instead of per-frame Color+alpha)
    private static final Color ANNOUNCE_BOSS_HP = new Color(255, 80, 80);
    private static final Color ANNOUNCE_NICE = new Color(163, 190, 140);
    private static final Color ANNOUNCE_GREAT = new Color(100, 200, 255);
    private static final Color ANNOUNCE_AMAZING = new Color(255, 220, 100);
    private static final Color ANNOUNCE_INCREDIBLE = new Color(255, 150, 80);
    private static final Color ANNOUNCE_LEGENDARY = new Color(220, 130, 220);
    private static final Color ANNOUNCE_GODLIKE = new Color(255, 100, 100);
    private static final Color ANNOUNCE_GOLD = new Color(255, 215, 0);
    private static final Color ANNOUNCE_GREEN = new Color(100, 255, 100);
    private static final Color ANNOUNCE_GRAY = new Color(150, 150, 150);
    private static final Color ANNOUNCE_RED_PINK = new Color(191, 97, 106);
    // â”€â”€ Cached Colors for boss intro banner (avoid ~15 new Color() per frame) â”€â”€
    private static final Color INTRO_SCAN_RED = new Color(255, 30, 30);
    private static final Color INTRO_STRIPE_RED = new Color(200, 30, 30);
    private static final Color INTRO_GLOW_RED = new Color(180, 20, 20);
    private static final Color INTRO_PANEL_BG = new Color(8, 8, 12);
    private static final Color INTRO_BORDER_BRIGHT = new Color(200, 35, 35);
    private static final Color INTRO_BORDER_DARK = new Color(140, 20, 20);
    private static final Color INTRO_HIGHLIGHT = new Color(255, 100, 100);
    private static final Color INTRO_SHADOW_200 = new Color(0, 0, 0, 200);
    private static final Color INTRO_GLOW_MEGA = new Color(255, 180, 30);
    private static final Color INTRO_GLOW_MINI = new Color(200, 200, 255);
    private static final Color INTRO_NAME_SHADOW = new Color(0, 0, 0, 220);
    private static final Color INTRO_NAME_MEGA = new Color(255, 220, 80);
    private static final Color INTRO_DASH_RED = new Color(200, 40, 40);
    private static final Color INTRO_WARN_BASE = new Color(255, 40, 40);

    // Cached vignette for performance

    private BufferedImage cachedVignette = null;

    private int cachedVignetteWidth = 0;

    private int cachedVignetteHeight = 0;

    

    // Cached Font objects â€” all derived from FontPalette (custom font with fallback)
    // FontPalette.init() must be called before first use (done in AssetLoader.initAll())
    private static Font FONT_TITLE_LARGE;
    private static Font FONT_TITLE;
    private static Font FONT_TITLE_MEDIUM;
    private static Font FONT_SUBTITLE;
    private static Font FONT_LARGE_32;
    private static Font FONT_LARGE;
    private static Font FONT_MEDIUM;
    private static Font FONT_MEDIUM_BOLD;
    private static Font FONT_SMALL;
    private static Font FONT_INFO;
    private static Font FONT_TINY;
    private static Font FONT_EXTRA_SMALL_16;
    private static Font FONT_EXTRA_SMALL_13;
    private static Font FONT_EXTRA_SMALL_12;
    private static Font FONT_EXTRA_SMALL_11;
    
    /** Call once after FontPalette.init() to bind the cached font references. */
    public static void initFonts() {
        FontPalette.init();
        FONT_TITLE_LARGE = FontPalette.TITLE_LARGE;
        FONT_TITLE = FontPalette.TITLE;
        FONT_TITLE_MEDIUM = FontPalette.TITLE_MEDIUM;
        FONT_SUBTITLE = FontPalette.SUBTITLE;
        FONT_LARGE_32 = FontPalette.LARGE_32;
        FONT_LARGE = FontPalette.LARGE;
        FONT_MEDIUM = FontPalette.MEDIUM;
        FONT_MEDIUM_BOLD = FontPalette.MEDIUM_BOLD;
        FONT_SMALL = FontPalette.SMALL;
        FONT_INFO = FontPalette.INFO;
        FONT_TINY = FontPalette.TINY;
        FONT_EXTRA_SMALL_16 = FontPalette.XS_16;
        FONT_EXTRA_SMALL_13 = FontPalette.XS_13;
        FONT_EXTRA_SMALL_12 = FontPalette.XS_12;
        FONT_EXTRA_SMALL_11 = FontPalette.XS_11;
    }

    

    // Smooth UI animations

    private double displayedScore = 0;

    private double displayedMoney = 0;

    private double comboPulseScale = 1.0;

    private int lastComboCount = 0;

    // Boss health bar pop-in animation
    private boolean lastBossPresent = false;
    private double bossHealthBarAnim = 0.0; // 0 = hidden, 1 = fully shown

    // Slam animation for game over / win screens
    private double screenEnteredTime = -1;
    private boolean slamSoundPlayed = false;
    // Random placement for stamp / badge each time screens appear
    private int stampOffsetX = 0;
    private int stampOffsetY = 0;
    private double stampAngleOffset = 0;
    private int badgeCorner = 0; // 0-3 quadrant around the text
    private double badgeRotation = 0; // random slight tilt for badge
    private static final java.util.Random screenRng = new java.util.Random();

    public void setScreenEnteredTime(double time) {
        this.screenEnteredTime = time;
        this.slamSoundPlayed = false;
        // Randomize stamp placement
        this.stampOffsetX = screenRng.nextInt(61) - 30; // -30 to +30
        this.stampOffsetY = screenRng.nextInt(41) - 20; // -20 to +20
        this.stampAngleOffset = (screenRng.nextDouble() - 0.5) * 0.3; // -0.15 to +0.15 radians extra
        // Randomize badge quadrant and slight rotation
        this.badgeCorner = screenRng.nextInt(4);
        this.badgeRotation = (screenRng.nextDouble() - 0.5) * 0.25; // -0.125 to +0.125 radians
    }

    /** Set the leaderboard manager reference for best-time display on victory screen. */
    public void setLeaderboardManager(LeaderboardManager lbManager) {
        this.leaderboardManager = lbManager;
    }

    

    public Renderer(GameData gameData, ShopManager shopManager, PassiveUpgradeManager passiveUpgradeManager, java.util.function.IntConsumer bgProgressCallback) {

        this.gameData = gameData;

        this.shopManager = shopManager;

        this.passiveUpgradeManager = passiveUpgradeManager;

        
        // Initialize fonts from FontPalette
        initFonts();

        // Load background layers

        loadBackgroundLayers(bgProgressCallback);

        

        // Load overlay image

        loadOverlay();

        

        // Initialize menu buttons â€” military/rock themed colors
        menuButtons = new UIButton[8];
        menuButtons[0] = new UIButton("Select Level", "level", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_LEVEL, ColorPalette.BTN_LEVEL_SEL);
        menuButtons[1] = new UIButton("Shop", "shop", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_SHOP, ColorPalette.BTN_SHOP_SEL);
        menuButtons[2] = new UIButton("Loadout", "stats", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_STATS, ColorPalette.BTN_STATS_SEL);
        menuButtons[3] = new UIButton("Achievements", "achievements", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_ACHIEVE, ColorPalette.BTN_ACHIEVE_SEL);
        menuButtons[4] = new UIButton("Leaderboard", "leaderboard", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_LEADERBOARD, ColorPalette.BTN_LEADERBOARD_SEL);
        menuButtons[5] = new UIButton("Help & Tutorial", "info", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_INFO, ColorPalette.BTN_INFO_SEL);
        menuButtons[6] = new UIButton("Settings", "settings", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_SETTINGS, ColorPalette.BTN_SETTINGS_SEL);
        menuButtons[7] = new UIButton("[SAVE] Save Files", "save", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_SAVE, ColorPalette.BTN_SAVE_SEL);

        

        // Initialize shop buttons (15 items)
        shopButtons = new UIButton[15];
        for (int i = 0; i < 15; i++) {
            shopButtons[i] = new UIButton("", 0, 0, UIScale.px(800), UIScale.px(50), ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_PURPLE);
        }

        // Initialize stats buttons (4 items)
        statsButtons = new UIButton[4];
        String[] statNames = {"Speed Boost", "Bullet Slow", "Lucky Dodge", "Active Item"};
        Color[] statColors = {ColorPalette.BTN_INFO, ColorPalette.BTN_STATS, ColorPalette.ACCENT_PURPLE, ColorPalette.ACCENT_ORANGE};

        for (int i = 0; i < 4; i++) {

            statsButtons[i] = new UIButton(statNames[i], 0, 0, UIScale.px(840), UIScale.px(70), ColorPalette.BUTTON_BASE, statColors[i]);

        }

        

        // Initialize settings buttons (22 options)
        settingsButtons = new UIButton[22];
        for (int i = 0; i < 22; i++) {
            settingsButtons[i] = new UIButton("", 0, 0, UIScale.px(900), UIScale.px(50), ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_ORANGE);
        }

        // Initialize pause buttons (4 buttons)
        pauseButtons = new UIButton[4];
        String[] pauseLabels = {"Resume", "Settings", "Main Menu", ""};
        for (int i = 0; i < 4; i++) {
            pauseButtons[i] = new UIButton(pauseLabels[i], 0, 0, UIScale.px(300), UIScale.px(60), ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_ORANGE);
        }

        // Initialize help screen buttons
        helpShowcaseButton = new UIButton("Showcase", "info", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_INFO, ColorPalette.BTN_INFO_SEL);
        helpTutorialButton = new UIButton("Start Tutorial", "level", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_LEVEL, ColorPalette.BTN_LEVEL_SEL);
    }

    /**
     * Called when the UI Scale setting changes at runtime.
     * Re-creates button objects at the new scaled sizes and re-binds fonts.
     */
    public void onUIScaleChanged() {
        // Re-bind static font references from FontPalette (which was already reinitialised by UIScale.setScale)
        initFonts();
        
        // Update settings UI click target sizes
        pillClickH = UIScale.px(26);
        sliderBtnSize = UIScale.px(26);
        
        // Recreate menu buttons at new scale
        menuButtons[0] = new UIButton("Select Level", "level", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_LEVEL, ColorPalette.BTN_LEVEL_SEL);
        menuButtons[1] = new UIButton("Shop", "shop", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_SHOP, ColorPalette.BTN_SHOP_SEL);
        menuButtons[2] = new UIButton("Loadout", "stats", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_STATS, ColorPalette.BTN_STATS_SEL);
        menuButtons[3] = new UIButton("Achievements", "achievements", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_ACHIEVE, ColorPalette.BTN_ACHIEVE_SEL);
        menuButtons[4] = new UIButton("Help & Tutorial", "info", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_INFO, ColorPalette.BTN_INFO_SEL);
        menuButtons[5] = new UIButton("Settings", "settings", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_SETTINGS, ColorPalette.BTN_SETTINGS_SEL);
        menuButtons[6] = new UIButton("[SAVE] Save Files", "save", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_SAVE, ColorPalette.BTN_SAVE_SEL);
        
        // Recreate shop buttons
        for (int i = 0; i < shopButtons.length; i++) {
            shopButtons[i] = new UIButton("", 0, 0, UIScale.px(800), UIScale.px(50), ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_PURPLE);
        }
        
        // Recreate stats buttons
        String[] statNames = {"Speed Boost", "Bullet Slow", "Lucky Dodge", "Active Item"};
        Color[] statColors = {ColorPalette.BTN_INFO, ColorPalette.BTN_STATS, ColorPalette.ACCENT_PURPLE, ColorPalette.ACCENT_ORANGE};
        for (int i = 0; i < statsButtons.length; i++) {
            statsButtons[i] = new UIButton(statNames[i], 0, 0, UIScale.px(840), UIScale.px(70), ColorPalette.BUTTON_BASE, statColors[i]);
        }
        
        // Recreate settings buttons
        for (int i = 0; i < settingsButtons.length; i++) {
            settingsButtons[i] = new UIButton("", 0, 0, UIScale.px(900), UIScale.px(50), ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_ORANGE);
        }
        
        // Recreate pause buttons
        String[] pauseLabels = {"Resume", "Settings", "Main Menu", ""};
        for (int i = 0; i < pauseButtons.length; i++) {
            pauseButtons[i] = new UIButton(pauseLabels[i], 0, 0, UIScale.px(300), UIScale.px(60), ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_ORANGE);
        }
        
        // Recreate help screen buttons
        helpShowcaseButton = new UIButton("Showcase", "info", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_INFO, ColorPalette.BTN_INFO_SEL);
        helpTutorialButton = new UIButton("Start Tutorial", "level", 0, 0, UIScale.px(300), UIScale.px(55), ColorPalette.BTN_LEVEL, ColorPalette.BTN_LEVEL_SEL);
    }

    

    private void loadBackgroundLayers(java.util.function.IntConsumer progressCallback) {

        if (backgroundsLoaded) return;

        try {

            int totalLoaded = 0;

            for (int set = 0; set < BACKGROUND_SET_COUNT; set++) {

                for (int layer = 0; layer < 6; layer++) {

                    String path = String.format("sprites/Backgrounds/background (%d)/%d.png", set + 1, layer + 1);

                    

                    BufferedImage image = null;

                    try {

                        image = AssetLoader.loadImage(path);

                        if (image != null) {

                            totalLoaded++;

                        }

                    } catch (IOException e) {

                        // Layer doesn't exist for this set - this is normal

                    }

                    

                    // Pre-scale to screen height once at load time so drawParallaxBackground()
                    // can do a simple 1:1 blit instead of per-frame bilinear scaling (saves ~50% render time)
                    if (image != null) {
                        int targetH = Game.HEIGHT;
                        double scale = (double) targetH / image.getHeight();
                        int targetW = (int)(image.getWidth() * scale);
                        if (targetW > 0 && targetH > 0) {
                            BufferedImage prescaled = Game.createOptimalImage(targetW, targetH, true);
                            Graphics2D pg = prescaled.createGraphics();
                            pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            pg.drawImage(image, 0, 0, targetW, targetH, null);
                            pg.dispose();
                            image.flush(); // Release original full-size image native memory
                            image = prescaled; // replace with pre-scaled version
                        }
                    }

                    // Store the image (can be null if layer doesn't exist for this set)
                    backgroundLayers[set][layer] = image;

                    if (progressCallback != null) progressCallback.accept((int)((set * 6 + layer + 1) * 100.0 / 48));

                }

                // Pre-composite the 3 slowest layers (0,1,2) into a single layer
                // to reduce per-frame drawImage calls from ~8-12 to ~5-8
                compositeSlowLayers(set);
            }

            

            if (totalLoaded > 0) {

                backgroundsLoaded = true;

                System.out.println("Parallax backgrounds loaded successfully! (" + totalLoaded + " layers)");

            } else {

                System.err.println("No background layers could be loaded!");

                backgroundsLoaded = false;

            }

        } catch (Exception e) {

            System.err.println("Error loading background layers: " + e.getMessage());

            e.printStackTrace();

            backgroundsLoaded = false;

        }

    }

    


    /**
     * Pre-composite the 3 slowest parallax layers (indices 0,1,2 at speeds 0.1, 0.2, 0.35)
     * into a single layer stored at index 0. Indices 1 and 2 are set to null.
     * This reduces per-frame drawImage calls by ~33% with no visible quality loss,
     * since these layers move so slowly they appear nearly static relative to each other.
     */
    private void compositeSlowLayers(int set) {
        BufferedImage layer0 = backgroundLayers[set][0];
        BufferedImage layer1 = backgroundLayers[set][1];
        BufferedImage layer2 = backgroundLayers[set][2];
        // Need at least layer0 as the base
        if (layer0 == null) return;
        // If no other layers exist, nothing to merge
        if (layer1 == null && layer2 == null) return;
        // Use the widest layer's width to avoid tiling artifacts
        int compositeW = layer0.getWidth();
        if (layer1 != null) compositeW = Math.max(compositeW, layer1.getWidth());
        if (layer2 != null) compositeW = Math.max(compositeW, layer2.getWidth());
        int compositeH = Game.HEIGHT;
        BufferedImage merged = Game.createOptimalImage(compositeW, compositeH, true);
        Graphics2D mg = merged.createGraphics();
        // Tile each layer across the composite width
        for (int li = 0; li < 3; li++) {
            BufferedImage src = backgroundLayers[set][li];
            if (src == null) continue;
            int sw = src.getWidth();
            for (int x = 0; x < compositeW; x += sw) {
                mg.drawImage(src, x, 0, null);
            }
        }
        mg.dispose();
        // Release originals
        layer0.flush();
        if (layer1 != null) layer1.flush();
        if (layer2 != null) layer2.flush();
        // Store merged as layer 0, null out 1 and 2
        backgroundLayers[set][0] = merged;
        backgroundLayers[set][1] = null;
        backgroundLayers[set][2] = null;
    }

    /**
     * Load backgrounds with a progress callback (called from loading thread).
     */
    public void loadBackgroundsWithProgress(java.util.function.IntConsumer progressCallback) {
        loadBackgroundLayers(progressCallback);
    }


    private void loadOverlay() {

        if (overlayLoaded) return;

        try {

            String path = "sprites/Backgrounds/Overlay.png";

            overlayImage = AssetLoader.loadImage(path);

            overlayLoaded = true;

            System.out.println("Overlay image loaded successfully");

        } catch (Exception e) {

            System.out.println("Overlay image not found - will run without overlay");

            overlayLoaded = false;

        }

    }

    

    private void drawParallaxBackground(Graphics2D g, int width, int height, int level, double time) {

        if (!backgroundsLoaded) return;

        

        // Select background set based on level (cycle through available sets)

        int bgSet = (level - 1) % BACKGROUND_SET_COUNT;

        

        // Draw each layer (offsets are advanced by advanceParallaxScroll)

        for (int i = 0; i < 6; i++) {

            // Get layer image

            BufferedImage layer = backgroundLayers[bgSet][i];

            if (layer == null) continue; // Skip if this layer doesn't exist for this background set;

            

            // Images are pre-scaled to screen height at load time, so use 1:1 blit

            int scaledWidth = layer.getWidth();

            

            // Wrap scroll offset

            double offset = layerScrollOffsets[i] % scaledWidth;

            

            // Draw tiled layers with wrapping (no scaling â€” simple blit)

            int x = (int)(-offset);

            while (x < width) {

                g.drawImage(layer, x, 0, null);

                x += scaledWidth;

            }

        }

    }

    

    /**
     * Advance parallax scroll offsets (called every frame, before async render).
     * This must be called on the game thread so offsets stay in sync with updates.
     */
    void advanceParallaxScroll() {
        if (!backgroundsLoaded) return;
        for (int i = 0; i < 6; i++) {
            layerScrollOffsets[i] += PARALLAX_SPEEDS[i] * 0.5;
        }
    }

    /**
     * Submit background rendering to a thread pool for next frame.
     * Call this AFTER the buffer swap + repaint in the game loop so the
     * worker renders in parallel with the EDT blit and the frame sleep.
     */
    void submitBackgroundRender(java.util.concurrent.ExecutorService pool, int level, int width, int height) {
        if (!backgroundsLoaded || Game.backgroundMode != 1) {
            bgBufferReady = false;
            return;
        }
        // Ensure buffer exists and matches screen size
        if (bgBuffer == null || bgBuffer.getWidth() != width || bgBuffer.getHeight() != height) {
            if (bgBuffer != null) bgBuffer.flush();
            bgBuffer = Game.createOptimalImage(width, height, true);
        }
        // Snapshot scroll offsets so the worker reads stable values
        System.arraycopy(layerScrollOffsets, 0, bgScrollSnapshot, 0, 6);
        final int bgSet = (level - 1) % BACKGROUND_SET_COUNT;
        final int w = width;
        final int h = height;
        final double[] scrollSnap = bgScrollSnapshot;
        bgBufferReady = false;
        bgRenderFuture = pool.submit(() -> {
            try {
                Graphics2D bg = bgBuffer.createGraphics();
                // Clear to transparent
                bg.setComposite(AlphaComposite.Clear);
                bg.fillRect(0, 0, w, h);
                bg.setComposite(AlphaComposite.SrcOver);
                for (int i = 0; i < 6; i++) {
                    BufferedImage layer = backgroundLayers[bgSet][i];
                    if (layer == null) continue;
                    int scaledWidth = layer.getWidth();
                    double offset = scrollSnap[i] % scaledWidth;
                    int x = (int)(-offset);
                    while (x < w) {
                        bg.drawImage(layer, x, 0, null);
                        x += scaledWidth;
                    }
                }
                bg.dispose();
                bgBufferLevel = bgSet;
                bgBufferReady = true;
            } catch (Exception e) {
                bgBufferReady = false; // Fallback to sync render
            }
        });
    }

    /**
     * Wait for the async background render to complete (with timeout fallback).
     * Call at the start of drawGame() before drawing anything.
     * Returns true if the pre-rendered buffer is ready to blit.
     */
    boolean waitForBackground() {
        if (bgRenderFuture == null) return bgBufferReady;
        try {
            bgRenderFuture.get(8, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Timed out or failed â€” fall back to synchronous render
        }
        bgRenderFuture = null;
        return bgBufferReady;
    }

    /**
     * Blit the pre-rendered background buffer onto the main graphics context.
     * Returns true if successful, false if caller should fall back to sync draw.
     */
    boolean blitBackground(Graphics2D g, int level) {
        int bgSet = (level - 1) % BACKGROUND_SET_COUNT;
        if (bgBufferReady && bgBuffer != null && bgBufferLevel == bgSet) {
            g.drawImage(bgBuffer, 0, 0, null);
            return true;
        }
        return false;
    }

    private void drawStaticBackground(Graphics2D g, int width, int height, int level) {

        // Select background set based on level (cycle through 14 sets)

        int bgSet = (level - 1) % 14;

        

        // Draw only the first layer (closest/most detailed layer)

        BufferedImage layer = backgroundLayers[bgSet][5]; // Layer 5 is the closest layer

        if (layer == null) {

            // Try other layers if layer 5 doesn't exist

            for (int i = 5; i >= 0; i--) {

                if (backgroundLayers[bgSet][i] != null) {

                    layer = backgroundLayers[bgSet][i];

                    break;

                }

            }

        }

        

        if (layer != null) {

            // Scale to fit screen

            int imgWidth = layer.getWidth();

            int imgHeight = layer.getHeight();

            double scale = Math.max((double)width / imgWidth, (double)height / imgHeight);

            int scaledWidth = (int)(imgWidth * scale);

            int scaledHeight = (int)(imgHeight * scale);

            

            // Center the image

            int x = (width - scaledWidth) / 2;

            int y = (height - scaledHeight) / 2;

            

            g.drawImage(layer, x, y, scaledWidth, scaledHeight, null);

        }

    }

    

    public void drawLoading(Graphics2D g, int width, int height, double time, int progress) {

        // Military themed background
        UITheme.drawScreenBackground(g, width, height, time);

        // Title â€” stencil-style with ember particles
        UITheme.drawTitle(g, "MISSILE MAN", width, height / 2 - UIScale.px(100),
            ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED,
            time, FontPalette.TITLE_LARGE);

        // "ARMING..." text
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.setFont(FontPalette.MEDIUM);
        String loadingText = "ARMING SYSTEMS...";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(loadingText, (width - fm.stringWidth(loadingText)) / 2, height / 2 + UIScale.px(20));

        // Missile-arming gauge progress bar
        int barWidth = UIScale.px(400);
        int barHeight = UIScale.px(24);
        int barX = (width - barWidth) / 2;
        int barY = height / 2 + UIScale.px(50);
        UITheme.drawProgressBar(g, barX, barY, barWidth, barHeight,
            progress / 100.0, ColorPalette.ACCENT_ORANGE);
    }

    

    public void drawMenu(Graphics2D g, int width, int height, double time, double escapeTimer, int selectedMenuItem, int currentSaveSlot, GameMode gameMode) {

        // Military themed background
        UITheme.drawScreenBackground(g, width, height, time);

        // Animated jet silhouettes streaking across
        UITheme.drawJetSilhouette(g, width, height, time);

        // Subtle game mode tint overlay
        if (gameMode != null) {
            g.setColor(new Color(
                gameMode.getColor().getRed(),
                gameMode.getColor().getGreen(),
                gameMode.getColor().getBlue(),
                20
            ));
            g.fillRect(0, 0, width, height);
        }

        // Title â€” stencil-style with embers
        UITheme.drawTitle(g, "MISSILE MAN", width, 150,
            ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED,
            time, FontPalette.TITLE);

        // Minecraft-style spinning mode splash text
        if (gameMode != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (Game.enableAntiAliasing) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }

            g.setFont(FontPalette.TITLE);
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = (width - titleFm.stringWidth("MISSILE MAN")) / 2;

            String splashText = gameMode.getSplashText();
            g2.setFont(FontPalette.MEDIUM_BOLD);
            FontMetrics splashFm = g2.getFontMetrics();

            int anchorX = titleX + titleFm.stringWidth("MISSILE MAN") + 10;
            int anchorY = 150 - 5;

            int textW = splashFm.stringWidth(splashText);
            int textH = splashFm.getAscent();
            int offsetX = -textW / 2;
            int offsetY = textH / 2;

            double wobbleAngle = Math.sin(time * 3) * Math.toRadians(12);
            double scalePulse = 1.0 + 0.08 * Math.sin(time * 5);

            java.awt.geom.AffineTransform oldTransform = g2.getTransform();
            g2.translate(anchorX, anchorY);
            g2.rotate(wobbleAngle);
            g2.scale(scalePulse, scalePulse);

            g2.setColor(RenderCache.BLACK_120);
            g2.drawString(splashText, offsetX + 2, offsetY + 2);

            g2.setColor(gameMode.getColor());
            g2.drawString(splashText, offsetX, offsetY);

            g2.setComposite(RenderCache.getAlpha((float)(0.2 + 0.15 * Math.sin(time * 4))));
            g2.setColor(Color.WHITE);
            g2.drawString(splashText, offsetX, offsetY);

            g2.setTransform(oldTransform);
            g2.dispose();
        }

        // Draw buttons â€” mission briefing clipboard stack
        int buttonY = 240;
        int buttonSpacing = 75;
        int buttonCount = Game.DEMO_MODE ? menuButtons.length - 1 : menuButtons.length; // Hide Save Files in demo
        for (int i = 0; i < buttonCount; i++) {
            menuButtons[i].setPosition((width - 300) / 2, buttonY + i * buttonSpacing);
            menuButtons[i].update(i == selectedMenuItem, time);
            menuButtons[i].draw(g, time);
        }

        // Score and money card
        drawStatsCard(g, width, height, time);

        // Version and save slot info (bottom right)
        g.setFont(FontPalette.TINY);
        g.setColor(ColorPalette.TEXT_DIM);
        String versionText = Game.DEMO_MODE ? Game.GAME_VERSION + " DEMO" : Game.GAME_VERSION;
        FontMetrics fmVer = g.getFontMetrics();
        g.drawString(versionText, width - fmVer.stringWidth(versionText) - UIScale.px(20), height - UIScale.px(70));

        if (!Game.DEMO_MODE && currentSaveSlot > 0) {
            String saveText = "Save Slot " + currentSaveSlot;
            g.drawString(saveText, width - fmVer.stringWidth(saveText) - UIScale.px(20), height - UIScale.px(50));
        }

        // Quit hint
        if (escapeTimer > 0) {
            g.setColor(ColorPalette.ACCENT_RED);
            g.setFont(FontPalette.MEDIUM_BOLD);
            drawPromptWithIcons(g, width / 2, height - UIScale.px(210), "Press ", KeyBindManager.Action.BACK, " again to Quit");
        }
    }

    

    public void drawSaveSelection(Graphics2D g, int width, int height, double time, int selectedSlot,

                                  java.util.List<SaveManager.SaveMetadata> saveMetadata, boolean deletingSlot, 

                                  int deleteConfirmTimer, double escapeTimer, double scrollOffset) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "SAVE FILES", width, UIScale.px(120), ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED, time);

        

        // Draw save slots with scroll
        FontMetrics fm;

        int slotWidth = width * 2 / 3;

        int slotHeight = UIScale.px(200);

        int slotX = (width - slotWidth) / 2;

        int startY = UIScale.px(200);

        int slotSpacing = UIScale.px(230);

        int totalEntries = saveMetadata.size() + 1; // existing saves + "New Save" button

        

        // Clip to content area (below title, above instructions)

        Shape oldClip = g.getClip();

        g.clipRect(0, UIScale.px(160), width, height - UIScale.px(300));

        

        for (int i = 0; i < totalEntries; i++) {

            int slotY = startY + i * slotSpacing - (int)scrollOffset;

            boolean isSelected = (i == selectedSlot);

            boolean isExistingSave = (i < saveMetadata.size());

            

            // Skip if completely off-screen

            if (slotY + slotHeight < UIScale.px(160) || slotY > height - UIScale.px(60)) continue;

            

            // Draw slot background (anti-aliasing already set by renderToBuffer)

            

            // Selection glow

            if (isSelected) {

                g.setColor(new Color(ColorPalette.ACCENT_ORANGE.getRed(), ColorPalette.ACCENT_ORANGE.getGreen(), ColorPalette.ACCENT_ORANGE.getBlue(), 80));

                Shape glowShape = UITheme.createChamferedRect(slotX - UIScale.px(8), slotY - UIScale.px(8), slotWidth + UIScale.px(16), slotHeight + UIScale.px(16), UIScale.px(12));

                g.fill(glowShape);

            }

            

            if (isExistingSave) {

                // Existing save slot

                SaveManager.SaveMetadata meta = saveMetadata.get(i);

                

                // Main slot background â€” chamfered military card

                Color slotColor = isSelected ? ColorPalette.BG_CARD_SELECTED : ColorPalette.BG_CARD;

                g.setColor(slotColor);

                Shape cardShape = UITheme.createChamferedRect(slotX, slotY, slotWidth, slotHeight, 10);

                g.fill(cardShape);

                

                // Border

                Color borderColor = isSelected ? ColorPalette.ACCENT_ORANGE : ColorPalette.BORDER_STEEL;

                g.setStroke(RenderCache.getStroke(isSelected ? 3f : 2f));

                g.setColor(borderColor);

                g.draw(cardShape);

                

                // Accent line on left

                g.setColor(ColorPalette.ACCENT_ORANGE);

                g.fillRect(slotX, slotY + UIScale.px(8), UIScale.px(4), slotHeight - UIScale.px(16));

                

                // Save name as title

                g.setFont(FONT_LARGE);

                g.setColor(ColorPalette.TEXT_GOLD);

                String slotNum = meta.saveName;

                g.drawString(slotNum, slotX + UIScale.px(20), slotY + UIScale.px(35));

                

                // Show game mode badge next to slot number

                if (meta.gameMode != null) {

                    GameMode mode = meta.gameMode;

                    String modeLabel = mode.getDisplayName();

                    g.setFont(FONT_EXTRA_SMALL_16);

                    FontMetrics modeFm = g.getFontMetrics();

                    int modeX = slotX + UIScale.px(20) + g.getFontMetrics(FONT_LARGE).stringWidth(slotNum) + UIScale.px(15);

                    int modeY = slotY + UIScale.px(30);

                    // Mode badge background pill

                    int badgeW = modeFm.stringWidth(modeLabel) + UIScale.px(16);

                    int badgeH = UIScale.px(22);

                    g.setColor(new Color(

                        mode.getColor().getRed(),

                        mode.getColor().getGreen(),

                        mode.getColor().getBlue(),

                        60

                    ));

                    g.fillRoundRect(modeX - UIScale.px(8), modeY - UIScale.px(16), badgeW, badgeH, UIScale.px(8), UIScale.px(8));

                    g.setStroke(RenderCache.getStroke(1.5f));

                    g.setColor(mode.getColor());

                    g.drawRoundRect(modeX - UIScale.px(8), modeY - UIScale.px(16), badgeW, badgeH, UIScale.px(8), UIScale.px(8));

                    g.drawString(modeLabel, modeX, modeY);

                }

                

                // Stats line - money (gold) and level (green)
                g.setFont(FONT_SMALL);
                FontMetrics statsFm = g.getFontMetrics();
                int statsX = slotX + UIScale.px(20);
                int statsY = slotY + UIScale.px(60);

                // Money in gold
                String moneyStr = "$" + meta.totalMoney;
                g.setColor(ColorPalette.TEXT_GOLD);
                g.drawString(moneyStr, statsX, statsY);
                statsX += statsFm.stringWidth(moneyStr);

                // Separator
                g.setColor(ColorPalette.TEXT_PRIMARY);
                String sep1 = "  |  ";
                g.drawString(sep1, statsX, statsY);
                statsX += statsFm.stringWidth(sep1);

                // Level in green
                String levelStr = "Level " + meta.maxLevel;
                g.setColor(ColorPalette.SUCCESS_GREEN);
                g.drawString(levelStr, statsX, statsY);
                statsX += statsFm.stringWidth(levelStr);

                // Separator
                g.setColor(ColorPalette.TEXT_PRIMARY);
                String sep2 = "  |  ";
                g.drawString(sep2, statsX, statsY);
                statsX += statsFm.stringWidth(sep2);

                // Runs in default
                String runsStr = meta.totalRuns + " Runs";
                g.setColor(ColorPalette.TEXT_PRIMARY);
                g.drawString(runsStr, statsX, statsY);

                

                // Completion progress bar - gradient from red to green
                int totalGameLevels = 28;
                float completionPct = Math.min(1.0f, (float) meta.maxLevel / totalGameLevels);
                int pBarX = slotX + UIScale.px(20);
                int pBarY = slotY + UIScale.px(165);
                int pBarW = slotWidth - UIScale.px(150);
                int pBarH = UIScale.px(16);
                
                // Bar background
                g.setColor(new Color(30, 30, 40, 180));
                g.fillRoundRect(pBarX, pBarY, pBarW, pBarH, UIScale.px(5), UIScale.px(5));
                
                // Bar fill with smooth color gradient based on completion
                if (completionPct > 0) {
                    int fillW = Math.max(UIScale.px(5), (int)(pBarW * completionPct));
                    // Smooth gradient: red(0%) -> orange(25%) -> yellow(50%) -> green(100%)
                    Color startColor, endColor;
                    float localT;
                    if (completionPct < 0.25f) {
                        startColor = new Color(220, 50, 50);
                        endColor = new Color(255, 140, 0);
                        localT = completionPct / 0.25f;
                    } else if (completionPct < 0.5f) {
                        startColor = new Color(255, 140, 0);
                        endColor = new Color(255, 220, 0);
                        localT = (completionPct - 0.25f) / 0.25f;
                    } else if (completionPct < 0.75f) {
                        startColor = new Color(255, 220, 0);
                        endColor = new Color(100, 220, 50);
                        localT = (completionPct - 0.5f) / 0.25f;
                    } else {
                        startColor = new Color(100, 220, 50);
                        endColor = new Color(50, 200, 80);
                        localT = (completionPct - 0.75f) / 0.25f;
                    }
                    int r = (int)(startColor.getRed() + (endColor.getRed() - startColor.getRed()) * localT);
                    int gr = (int)(startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * localT);
                    int b = (int)(startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * localT);
                    g.setColor(new Color(Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, gr)), Math.min(255, Math.max(0, b))));
                    g.fillRoundRect(pBarX, pBarY, fillW, pBarH, UIScale.px(5), UIScale.px(5));
                }
                
                // Bar border
                g.setColor(isSelected ? ColorPalette.ACCENT_ORANGE : ColorPalette.BORDER_STEEL);
                g.setStroke(RenderCache.getStroke(1f));
                g.drawRoundRect(pBarX, pBarY, pBarW, pBarH, UIScale.px(5), UIScale.px(5));
                
                // Percentage label
                g.setFont(FONT_TINY);
                String pctText = String.format("%d%%", (int)(completionPct * 100));
                g.setColor(ColorPalette.TEXT_PRIMARY);
                g.drawString(pctText, pBarX + pBarW + UIScale.px(8), pBarY + pBarH - UIScale.px(2));

                // Stats line 2 - Best run / Bosses
                g.setFont(FONT_SMALL);
                g.setColor(ColorPalette.TEXT_PRIMARY);
                String stats2 = String.format("Best Run: Level %d  |  Bosses Defeated: %d", meta.bestRunLevel, meta.totalBosses);
                g.drawString(stats2, slotX + UIScale.px(20), slotY + UIScale.px(90));

                // Created date (left) and Last saved date (right)

                g.setFont(FONT_TINY);

                g.setColor(ColorPalette.TEXT_DIM);

                String createdText = "Created: " + meta.getFormattedCreationDate();

                g.drawString(createdText, slotX + UIScale.px(20), slotY + UIScale.px(120));

                

                g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 180));

                String dateText = "Last Saved: " + meta.getFormattedDate();

                FontMetrics dateFm = g.getFontMetrics();

                g.drawString(dateText, slotX + slotWidth - UIScale.px(20) - dateFm.stringWidth(dateText), slotY + UIScale.px(120));

                

                // Delete button

                int btnX = slotX + slotWidth - UIScale.px(120);

                int btnY = slotY + UIScale.px(10);

                int btnWidth = UIScale.px(100);

                int btnHeight = UIScale.px(35);

                

                // Button background

                Color btnColor = isSelected && deletingSlot ? ColorPalette.ACCENT_RED_BRIGHT : new Color(ColorPalette.ACCENT_RED.getRed(), ColorPalette.ACCENT_RED.getGreen(), ColorPalette.ACCENT_RED.getBlue(), 150);

                g.setColor(btnColor);

                Shape delShape = UITheme.createChamferedRect(btnX, btnY, btnWidth, btnHeight, 6);

                g.fill(delShape);

                

                // Button border

                g.setStroke(RenderCache.getStroke(2));

                g.setColor(ColorPalette.ACCENT_RED_BRIGHT);

                g.draw(delShape);

                

                // Button text

                g.setFont(FONT_TINY);

                g.setColor(Color.WHITE);

                String btnText = "DELETE";

                fm = g.getFontMetrics();

                int textX = btnX + (btnWidth - fm.stringWidth(btnText)) / 2;

                int textY = btnY + (btnHeight + fm.getAscent()) / 2 - 2;

                g.drawString(btnText, textX, textY);

                

                // Delete confirmation - transparent red overlay sweeping across the card

                if (isSelected && deletingSlot) {

                    float progress = Math.min(1.0f, deleteConfirmTimer / 60.0f);

                    

                    // Red overlay that spans across the card as you hold delete
                    int overlayW = (int)(slotWidth * progress);
                    if (overlayW > 0) {
                        Shape overlayClip = UITheme.createChamferedRect(slotX, slotY, slotWidth, slotHeight, 10);
                        Shape prevClip = g.getClip();
                        g.setClip(overlayClip);
                        g.setColor(new Color(255, 30, 30, 80));
                        g.fillRect(slotX, slotY, overlayW, slotHeight);
                        g.setClip(prevClip);
                    }

                    // "DELETE" text centered on card, semi-transparent
                    g.setFont(FONT_LARGE);
                    String deleteLabel = "DELETE";
                    fm = g.getFontMetrics();
                    int dlX = slotX + (slotWidth - fm.stringWidth(deleteLabel)) / 2;
                    int dlY = slotY + (slotHeight + fm.getAscent() - fm.getDescent()) / 2;
                    g.setColor(new Color(255, 50, 50, 140));
                    g.drawString(deleteLabel, dlX, dlY);

                }

            } else {

                // "New Save" button â€” dashed military card

                Color newSlotColor = isSelected ? ColorPalette.BG_CARD_SELECTED : ColorPalette.BG_CARD;

                g.setColor(newSlotColor);

                Shape newShape = UITheme.createChamferedRect(slotX, slotY, slotWidth, slotHeight, 10);

                g.fill(newShape);

                

                // Dashed border

                float[] dash = {10, 6};

                Color borderColor = isSelected ? ColorPalette.SUCCESS_GREEN : new Color(ColorPalette.BORDER_STEEL.getRed(), ColorPalette.BORDER_STEEL.getGreen(), ColorPalette.BORDER_STEEL.getBlue(), 150);

                g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, dash, (float)(time * 20)));

                g.setColor(borderColor);

                g.draw(newShape);

                

                // Plus icon

                g.setStroke(RenderCache.getStroke(4));

                g.setColor(isSelected ? ColorPalette.SUCCESS_GREEN : ColorPalette.TEXT_DIM);

                int plusCenterX = slotX + slotWidth / 2;

                int plusCenterY = slotY + slotHeight / 2 - UIScale.px(12);

                g.drawLine(plusCenterX - UIScale.px(15), plusCenterY, plusCenterX + UIScale.px(15), plusCenterY);

                g.drawLine(plusCenterX, plusCenterY - UIScale.px(15), plusCenterX, plusCenterY + UIScale.px(15));

                

                // Text

                g.setFont(FONT_MEDIUM);

                g.setColor(isSelected ? ColorPalette.SUCCESS_GREEN : ColorPalette.TEXT_DIM);

                String newText = "New Save";

                FontMetrics newFm = g.getFontMetrics();

                g.drawString(newText, plusCenterX - newFm.stringWidth(newText) / 2, plusCenterY + UIScale.px(40));

            }

        }

        

        // Restore clip

        g.setClip(oldClip);

        

        // Scroll indicators

        if (scrollOffset > 5) {

            // Up arrow indicator

            g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), (int)(150 + 50 * Math.sin(time * 4))));

            g.setFont(FONT_MEDIUM);

            fm = g.getFontMetrics();

            String upArrow = "\u25B2  Scroll Up";

            g.drawString(upArrow, (width - fm.stringWidth(upArrow)) / 2, UIScale.px(180));

        }

        int maxScroll = Math.max(0, totalEntries * slotSpacing + startY - height + 60);

        if (scrollOffset < maxScroll - 5) {

            // Down arrow indicator

            g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), (int)(150 + 50 * Math.sin(time * 4))));

            g.setFont(FONT_MEDIUM);

            fm = g.getFontMetrics();

            String downArrow = "\u25BC  Scroll Down";

            g.drawString(downArrow, (width - fm.stringWidth(downArrow)) / 2, height - UIScale.px(80));

        }

        

        // Instructions

        g.setFont(FONT_MEDIUM);

        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 200));

        boolean isCtrlMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();

        if (isCtrlMode) {

            drawPromptWithIcons(g, width / 2, height - UIScale.px(50), "", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, ": Navigate  |  ", KeyBindManager.Action.CONFIRM, ": Select/Create  |  ", KeyBindManager.ControllerButton.X, ": Hold to Delete Save");

        } else {

            drawPromptWithIcons(g, width / 2, height - UIScale.px(50), "", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, ": Navigate  |  ", KeyBindManager.Action.CONFIRM, ": Select/Create  |  DELETE: Hold to Delete Save");

        }

        

        // Quit hint

        if (escapeTimer > 0) {

            g.setColor(ColorPalette.ACCENT_RED);

            g.setFont(FONT_MEDIUM_BOLD);

            drawPromptWithIcons(g, width / 2, height - UIScale.px(20), "Press ", KeyBindManager.Action.BACK, " again to Quit");

        }

    }

    

    /**

     * Draw the game mode selection screen (shown when creating a new save).

     * Three cards for Easy/Hard/Master with descriptions and color coding.

     */

    public void drawModeSelect(Graphics2D g, int width, int height, double time, int selectedIndex) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "SELECT MODE", width, UIScale.px(100), ColorPalette.ACCENT_YELLOW, ColorPalette.ACCENT_ORANGE, time);

        

        // Subtitle

        g.setFont(FONT_MEDIUM);

        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 200));

        String subtitle = "Choose a difficulty for this save (locked once created)";

        FontMetrics fm = g.getFontMetrics();

        g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, UIScale.px(135));

        

        // Draw mode cards

        GameMode[] modes = GameMode.values();

        int cardWidth = UIScale.px(700);

        int cardX = (width - cardWidth) / 2;

        int startY = UIScale.px(205);

        int cardGap = UIScale.px(20);

        int textMaxWidth = cardWidth - UIScale.px(50);

        

        // Pre-calculate wrapped text and card heights

        int[] cardHeights = new int[modes.length];

        java.util.List<String>[] descLines = new java.util.List[modes.length];

        java.util.List<String>[] detailLines = new java.util.List[modes.length];

        String[] details = new String[modes.length];

        for (int i = 0; i < modes.length; i++) {

            switch (modes[i]) {

                case EASY:

                    details[i] = "Bosses attack slower with longer rest periods. Progress is saved on death.";

                    break;

                case HARD:

                    details[i] = "Full boss difficulty. Progress is saved on death \u2014 no level resets.";

                    break;

                case MASTER:

                    details[i] = "Full boss difficulty. Roguelike resets \u2014 levels and lives reset on death.";

                    break;

                default:

                    details[i] = "";

            }

            g.setFont(FONT_MEDIUM);

            descLines[i] = wrapText(modes[i].getDescription(), g.getFontMetrics(), textMaxWidth);

            g.setFont(FONT_SMALL);

            detailLines[i] = wrapText(details[i], g.getFontMetrics(), textMaxWidth);

            int descHeight = descLines[i].size() * g.getFontMetrics(FONT_MEDIUM).getHeight();

            int detailHeight = detailLines[i].size() * g.getFontMetrics(FONT_SMALL).getHeight();

            cardHeights[i] = UIScale.px(50) + descHeight + UIScale.px(6) + detailHeight + UIScale.px(10);

        }

        

        // Store card bounds for mouse hit-testing in Game
        modeCardBounds = new java.awt.Rectangle[modes.length];

        int currentY = startY;

        for (int i = 0; i < modes.length; i++) {

            GameMode mode = modes[i];

            int cardHeight = cardHeights[i];

            int cardY = currentY;

            modeCardBounds[i] = new java.awt.Rectangle(cardX, cardY, cardWidth, cardHeight);

            boolean isSelected = (i == selectedIndex);

            

            Graphics2D g2 = (Graphics2D) g.create();

            if (Game.enableAntiAliasing) {

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            }

            

            // Selection glow

            if (isSelected) {

                float glowPulse = (float)(0.6 + 0.4 * Math.sin(time * 4));

                g2.setColor(new Color(

                    mode.getColor().getRed(), 

                    mode.getColor().getGreen(), 

                    mode.getColor().getBlue(), 

                    (int)(80 * glowPulse)

                ));

                Shape glowShape = UITheme.createChamferedRect(cardX - UIScale.px(8), cardY - UIScale.px(8), cardWidth + UIScale.px(16), cardHeight + UIScale.px(16), UIScale.px(12));

                g2.fill(glowShape);

            }

            

            // Card background â€” chamfered military card

            Color bgColor = isSelected ? ColorPalette.BG_CARD_SELECTED : ColorPalette.BG_CARD;

            g2.setColor(bgColor);

            Shape cardShape = UITheme.createChamferedRect(cardX, cardY, cardWidth, cardHeight, UIScale.px(10));

            g2.fill(cardShape);

            

            // Border in mode color

            g2.setStroke(RenderCache.getStroke(isSelected ? 4f : 2f));

            g2.setColor(isSelected ? mode.getColor() : new Color(

                mode.getColor().getRed(), 

                mode.getColor().getGreen(), 

                mode.getColor().getBlue(), 

                120

            ));

            g2.draw(cardShape);

            

            // Accent line on left

            g2.setColor(mode.getColor());

            g2.fillRect(cardX, cardY + UIScale.px(8), UIScale.px(4), cardHeight - UIScale.px(16));

            

            // Mode name

            g2.setFont(FONT_LARGE);

            g2.setColor(isSelected ? mode.getColor() : ColorPalette.TEXT_PRIMARY);

            g2.drawString(mode.getDisplayName(), cardX + UIScale.px(25), cardY + UIScale.px(40));

            

            // Description (wrapped)

            g2.setFont(FONT_MEDIUM);

            g2.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 220));

            int textY = cardY + UIScale.px(70);

            for (String line : descLines[i]) {

                g2.drawString(line, cardX + UIScale.px(25), textY);

                textY += g2.getFontMetrics().getHeight();

            }

            

            // Detail text (wrapped)

            g2.setFont(FONT_SMALL);

            g2.setColor(ColorPalette.TEXT_DIM);

            textY += UIScale.px(6);

            for (String line : detailLines[i]) {

                g2.drawString(line, cardX + UIScale.px(25), textY);

                textY += g2.getFontMetrics().getHeight();

            }

            

            // Selection arrow (missile shape)

            if (isSelected) {

                g2.setColor(mode.getColor());

                double bounce = Math.sin(time * 6) * 5;

                int arrowX = (int)(cardX - UIScale.px(28) + bounce);

                int arrowY = cardY + cardHeight / 2;

                int[] xPoints = {arrowX, arrowX, arrowX + UIScale.px(14)};

                int[] yPoints = {arrowY - UIScale.px(10), arrowY + UIScale.px(10), arrowY};

                g2.fillPolygon(xPoints, yPoints, 3);

            }

            

            g2.dispose();

            currentY += cardHeight + cardGap;

        }

        

        // Instructions

        g.setFont(FONT_MEDIUM);

        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 200));

        drawPromptWithIcons(g, width / 2, height - UIScale.px(80), "", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, ": Navigate  |  ", KeyBindManager.Action.CONFIRM, ": Select  |  ", KeyBindManager.Action.BACK, ": Back");

    }

    
    public void drawNameInput(Graphics2D g, int width, int height, double time,
                              String currentName, int cursorPos, int cursorBlink,
                              int kbRow, int kbCol, String[] kbRows) {
        // Background
        UITheme.drawScreenBackground(g, width, height, time);
        
        // Title
        UITheme.drawTitle(g, "NAME YOUR SAVE", width, UIScale.px(80), 
            ColorPalette.ACCENT_YELLOW, ColorPalette.ACCENT_ORANGE, time);
        
        // Subtitle
        g.setFont(FONT_MEDIUM);
        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), 
            ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 180));
        String subtitle = "Type a name or use the on-screen keyboard";
        FontMetrics fmSub = g.getFontMetrics();
        g.drawString(subtitle, (width - fmSub.stringWidth(subtitle)) / 2, UIScale.px(120));
        
        // --- Name input field ---
        int fieldW = UIScale.px(500);
        int fieldH = UIScale.px(50);
        int fieldX = (width - fieldW) / 2;
        int fieldY = UIScale.px(155);
        
        // Field background
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(fieldX, fieldY, fieldW, fieldH, UIScale.px(8), UIScale.px(8));
        g.setColor(ColorPalette.ACCENT_YELLOW);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(fieldX, fieldY, fieldW, fieldH, UIScale.px(8), UIScale.px(8));
        g.setStroke(new BasicStroke(1));
        
        // Name text
        g.setFont(FONT_LARGE);
        g.setColor(ColorPalette.TEXT_PRIMARY);
        FontMetrics fmName = g.getFontMetrics();
        int textX = fieldX + UIScale.px(15);
        int textY = fieldY + (fieldH + fmName.getAscent() - fmName.getDescent()) / 2;
        g.drawString(currentName, textX, textY);
        
        // Blinking cursor
        if ((cursorBlink / 30) % 2 == 0) {
            String beforeCursor = currentName.substring(0, cursorPos);
            int cursorX = textX + fmName.stringWidth(beforeCursor);
            g.setColor(ColorPalette.ACCENT_YELLOW);
            g.fillRect(cursorX, fieldY + UIScale.px(8), 2, fieldH - UIScale.px(16));
        }
        
        // Character count
        g.setFont(FONT_SMALL);
        g.setColor(new Color(180, 180, 180));
        String charCount = currentName.length() + "/20";
        FontMetrics fmSmall = g.getFontMetrics();
        g.drawString(charCount, fieldX + fieldW - fmSmall.stringWidth(charCount) - UIScale.px(10), 
            fieldY + fieldH + UIScale.px(18));
        
        // --- On-screen keyboard ---
        int kbStartY = UIScale.px(260);
        int keySize = UIScale.px(42);
        int keyGap = UIScale.px(6);
        
        for (int r = 0; r < kbRows.length; r++) {
            String row = kbRows[r];
            int rowWidth = row.length() * (keySize + keyGap) - keyGap;
            int rowX = (width - rowWidth) / 2;
            
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                int kx = rowX + c * (keySize + keyGap);
                int ky = kbStartY + r * (keySize + keyGap);
                
                boolean isHighlighted = (r == kbRow && c == kbCol);
                
                // Key background
                if (isHighlighted) {
                    g.setColor(ColorPalette.ACCENT_YELLOW);
                } else {
                    g.setColor(new Color(60, 60, 80, 200));
                }
                g.fillRoundRect(kx, ky, keySize, keySize, UIScale.px(6), UIScale.px(6));
                
                // Key border
                g.setColor(isHighlighted ? ColorPalette.ACCENT_ORANGE : new Color(100, 100, 120));
                g.drawRoundRect(kx, ky, keySize, keySize, UIScale.px(6), UIScale.px(6));
                
                // Key label
                g.setColor(isHighlighted ? Color.BLACK : ColorPalette.TEXT_PRIMARY);
                String label = String.valueOf(ch);
                if (ch == '\u2190') label = "DEL";
                if (ch == '\u23CE') label = "OK";
                if (ch == ' ') label = "SPC";
                // Use smaller font for multi-character labels to prevent overflow
                g.setFont(label.length() > 1 ? FONT_SMALL : FONT_MEDIUM_BOLD);
                FontMetrics fmKey = g.getFontMetrics();
                int lx = kx + (keySize - fmKey.stringWidth(label)) / 2;
                int ly = ky + (keySize + fmKey.getAscent() - fmKey.getDescent()) / 2;
                g.drawString(label, lx, ly);
            }
        }
        
        // Controls hint
        g.setFont(FONT_SMALL);
        g.setColor(new Color(ColorPalette.TEXT_DIM.getRed(), 
            ColorPalette.TEXT_DIM.getGreen(), ColorPalette.TEXT_DIM.getBlue(), 160));
        String hint = "[ENTER] Confirm  |  [ESC] Back  |  [BACKSPACE] Delete";
        FontMetrics fmHint = g.getFontMetrics();
        g.drawString(hint, (width - fmHint.stringWidth(hint)) / 2, height - UIScale.px(30));
    }

    /**
     * Wraps text into multiple lines that fit within the given pixel width.
     */
    private java.util.List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
            } else {
                String test = currentLine + " " + word;
                if (fm.stringWidth(test) > maxWidth) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    currentLine.append(" ").append(word);
                }
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    private void drawGeometricBackground(Graphics2D g, int width, int height, double time) {

        // Save/restore instead of g.create() 

        Object savedAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

        if (Game.enableAntiAliasing) {

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        }

        

        // Floating hexagons

        drawFloatingShapes(g, width, height, time);

        

        // Grid lines with perspective

        drawPerspectiveGrid(g, width, height, time);

        

        // Orbiting circles

        drawOrbitingCircles(g, width, height, time);

        

        // Corner decorations

        drawCornerDecorations(g, width, height, time);

        

        if (savedAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, savedAA);

    }

    

    private void drawFloatingShapes(Graphics2D g, int width, int height, double time) {

        // Draw floating hexagons and triangles â€” save/restore transform instead of 12 g.create()/dispose() per frame

        int numShapes = 12;

        AffineTransform savedTransform = g.getTransform();

        for (int i = 0; i < numShapes; i++) {

            double phase = (i * Math.PI * 2.0) / numShapes;

            double x = width * (0.1 + 0.8 * ((Math.sin(time * 0.3 + phase) + 1) / 2));

            double y = height * (0.1 + 0.8 * ((Math.cos(time * 0.2 + phase * 1.5) + 1) / 2));

            double size = 20 + 30 * Math.sin(time * 0.5 + phase);

            double rotation = time * 0.5 + phase;

            int alpha = (int)(30 + 20 * Math.sin(time + phase));

            

            g.translate(x, y);

            g.rotate(rotation);

            

            if (i % 3 == 0) {

                // Hexagon

                g.setColor(new Color(143, 188, 187, alpha)); // Teal

                drawHexagon(g, 0, 0, (int)size);

            } else if (i % 3 == 1) {

                // Triangle

                g.setColor(new Color(180, 142, 173, alpha)); // Purple

                drawTriangle(g, 0, 0, (int)size);

            } else {

                // Diamond

                g.setColor(new Color(235, 203, 139, alpha)); // Gold

                drawDiamond(g, 0, 0, (int)size);

            }

            

            g.setTransform(savedTransform);

        }

    }

    

    private void drawPerspectiveGrid(Graphics2D g, int width, int height, double time) {

        g.setStroke(RenderCache.getStroke(1));

        int gridSpacing = 60;

        double waveOffset = time * 20;

        

        // Horizontal lines with wave effect

        for (int y = 0; y < height; y += gridSpacing) {

            int alpha = (int)(15 + 10 * Math.sin(y * 0.02 + time));

            g.setColor(new Color(136, 192, 208, alpha));

            

            int prevX = 0;

            int prevY = y + (int)(Math.sin(waveOffset * 0.01) * 5);

            for (int x = 20; x <= width; x += 20) {

                int newY = y + (int)(Math.sin((x + waveOffset) * 0.01) * 5);

                g.drawLine(prevX, prevY, x, newY);

                prevX = x;

                prevY = newY;

            }

        }

        

        // Vertical lines with subtle movement

        for (int x = 0; x < width; x += gridSpacing) {

            int alpha = (int)(10 + 8 * Math.sin(x * 0.02 + time * 1.5));

            g.setColor(new Color(180, 142, 173, alpha));

            g.drawLine(x + (int)(Math.sin(time + x * 0.01) * 3), 0, 

                       x + (int)(Math.sin(time + x * 0.01 + Math.PI) * 3), height);

        }

    }

    

    private void drawOrbitingCircles(Graphics2D g, int width, int height, double time) {

        int centerX = width / 2;

        int centerY = height / 2;

        

        // Multiple orbiting rings

        int[] radii = {200, 320, 450};

        Color[] colors = {

            ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 40), // Green

            ColorPalette.withAlpha(ColorPalette.ACCENT_RED, 35),  // Red

            ColorPalette.withAlpha(ColorPalette.ACCENT_CYAN, 30)  // Cyan

        };

        

        g.setStroke(RenderCache.getStroke(2));

        

        for (int r = 0; r < radii.length; r++) {

            int radius = radii[r];

            g.setColor(colors[r]);

            

            // Draw orbit path (dashed)

            float[] dash = {10, 15};

            g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, (float)(time * 20)));

            g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            

            // Draw orbiting dots

            int numDots = 3 + r;

            for (int i = 0; i < numDots; i++) {

                double angle = time * (0.5 + r * 0.2) + (i * Math.PI * 2 / numDots);

                int dotX = centerX + (int)(radius * Math.cos(angle));

                int dotY = centerY + (int)(radius * Math.sin(angle));

                int dotSize = 6 + r * 2;

                

                // Glow

                g.setColor(new Color(colors[r].getRed(), colors[r].getGreen(), colors[r].getBlue(), 60));

                g.fillOval(dotX - dotSize, dotY - dotSize, dotSize * 2, dotSize * 2);

                

                // Core

                g.setColor(new Color(colors[r].getRed(), colors[r].getGreen(), colors[r].getBlue(), 150));

                g.fillOval(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize);

            }

        }

    }

    

    private void drawCornerDecorations(Graphics2D g, int width, int height, double time) {

        int cornerSize = 80;

        g.setStroke(RenderCache.getStroke(2));

        

        // Top-left corner

        drawCornerBracket(g, 20, 20, cornerSize, time, false, false);

        

        // Top-right corner

        drawCornerBracket(g, width - 20, 20, cornerSize, time, true, false);

        

        // Bottom-left corner

        drawCornerBracket(g, 20, height - 20, cornerSize, time, false, true);

        

        // Bottom-right corner

        drawCornerBracket(g, width - 20, height - 20, cornerSize, time, true, true);

    }

    

    private void drawCornerBracket(Graphics2D g, int x, int y, int size, double time, boolean flipX, boolean flipY) {

        int alpha = (int)(100 + 50 * Math.sin(time * 2));

        g.setColor(new Color(235, 203, 139, alpha));

        

        int dx = flipX ? -1 : 1;

        int dy = flipY ? -1 : 1;

        

        // L-shaped bracket

        g.drawLine(x, y, x + size * dx, y);

        g.drawLine(x, y, x, y + size * dy);

        

        // Inner decoration

        g.setColor(new Color(143, 188, 187, alpha / 2));

        g.drawLine(x + 10 * dx, y + 10 * dy, x + (size - 20) * dx, y + 10 * dy);

        g.drawLine(x + 10 * dx, y + 10 * dy, x + 10 * dx, y + (size - 20) * dy);

        

        // Diamond accent

        int diamondX = x + 25 * dx;

        int diamondY = y + 25 * dy;

        int diamondSize = 8;

        g.setColor(new Color(180, 142, 173, alpha));

        int[] xPoints = {diamondX, diamondX + diamondSize, diamondX, diamondX - diamondSize};

        int[] yPoints = {diamondY - diamondSize, diamondY, diamondY + diamondSize, diamondY};

        g.fillPolygon(xPoints, yPoints, 4);

    }

    

    private void drawHexagon(Graphics2D g, int x, int y, int size) {

        int[] xPoints = new int[6];

        int[] yPoints = new int[6];

        for (int i = 0; i < 6; i++) {

            double angle = Math.PI / 6 + i * Math.PI / 3;

            xPoints[i] = x + (int)(size * Math.cos(angle));

            yPoints[i] = y + (int)(size * Math.sin(angle));

        }

        g.drawPolygon(xPoints, yPoints, 6);

    }

    

    private void drawTriangle(Graphics2D g, int x, int y, int size) {

        int[] xPoints = {x, x + size / 2, x - size / 2};

        int[] yPoints = {y - size / 2, y + size / 2, y + size / 2};

        g.drawPolygon(xPoints, yPoints, 3);

    }

    

    private void drawDiamond(Graphics2D g, int x, int y, int size) {

        int halfSize = size / 2;

        int[] xPoints = {x, x + halfSize, x, x - halfSize};

        int[] yPoints = {y - halfSize, y, y + halfSize, y};

        g.drawPolygon(xPoints, yPoints, 4);

    }

    

    private void drawStatsCard(Graphics2D g, int width, int height, double time) {

        g.setFont(FONT_LARGE);

        FontMetrics fm = g.getFontMetrics();

        

        // Prepare text strings

        String scoreText = "Score: " + gameData.getScore();

        String moneyText = "$" + gameData.getTotalMoney();

        

        // Calculate required widths

        int scoreWidth = fm.stringWidth(scoreText);

        int moneyWidth = fm.stringWidth(moneyText);

        int dividerSpace = UIScale.px(40); // Space for divider and padding

        int padding = UIScale.px(50); // Left and right padding

        

        // Dynamically size the card based on content

        int minCardWidth = UIScale.px(350);

        int requiredWidth = scoreWidth + moneyWidth + dividerSpace + padding;

        int cardWidth = Math.max(minCardWidth, requiredWidth);

        int cardHeight = UIScale.px(70);

        int cardX = (width - cardWidth) / 2;

        int cardY = height - UIScale.px(130);

        

        // Card background

        g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 200));

        g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, UIScale.px(15), UIScale.px(15));

        

        // Card border

        int borderAlpha = (int)(150 + 50 * Math.sin(time * 2));

        g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, borderAlpha));

        g.setStroke(RenderCache.getStroke(2));

        g.drawRoundRect(cardX, cardY, cardWidth, cardHeight, UIScale.px(15), UIScale.px(15));

        

        // Calculate divider position based on text widths

        int dividerX = cardX + padding / 2 + scoreWidth + dividerSpace / 2;

        

        // Divider line

        g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 100));

        g.drawLine(dividerX, cardY + UIScale.px(10), dividerX, cardY + cardHeight - UIScale.px(10));

        

        int textY = cardY + cardHeight / 2 + fm.getAscent() / 2 - UIScale.px(5);

        

        // Score display (left side)

        g.setColor(ColorPalette.SUCCESS_GREEN); // Green

        int scoreX = cardX + padding / 2;

        g.drawString(scoreText, scoreX, textY);

        

        // Money display (right side of divider)

        g.setColor(ColorPalette.TEXT_GOLD); // Gold

        int moneyX = dividerX + dividerSpace / 2;

        g.drawString(moneyText, moneyX, textY);

    }

    

    public void drawInfo(Graphics2D g, int width, int height, double time) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "HELP & TUTORIAL", width, UIScale.px(60), ColorPalette.ACCENT_CYAN, ColorPalette.ACCENT_ORANGE, time, FONT_TITLE_MEDIUM);

        

        int leftX = UIScale.px(60);

        int rightX = width / 2 + UIScale.px(40);

        

        // LEFT COLUMN â€” Core Mechanics

        int y = UIScale.px(105);

        

        g.setColor(ColorPalette.ACCENT_CYAN);

        g.setFont(FONT_MEDIUM_BOLD);

        g.drawString("CORE MECHANICS", leftX, y);

        y += UIScale.px(25);

        

        g.setColor(Color.WHITE);

        g.setFont(FONT_EXTRA_SMALL_16);

        String[] mechanics = {

            "â€¢ Boss is invulnerable for 20s â€” watch for GOLDEN GLOW!",

            "â€¢ Attack Window: Hit the boss 3 times to win",

            "â€¢ Graze bullets for score & combo bonuses",

            "â€¢ Perfect Dodge (8px) grants invincibility frames",

            "â€¢ Chain grazes within 3s to build combos",

            "",

            "â€¢ One hit = death (Lucky Dodge can save you)",

            "â€¢ Extra missiles grant second chances",

            "â€¢ Upgrades: Speed, Bullet Slow, Lucky Dodge, Attack Window",

            "â€¢ Risk Contracts (Lv 6+): Higher risk = higher reward"

        };

        

        for (String line : mechanics) {

            g.drawString(line, leftX + UIScale.px(10), y);

            y += UIScale.px(20);

        }

        

        // RIGHT COLUMN â€” Active Items summary

        y = UIScale.px(105);

        

        g.setColor(ColorPalette.ACCENT_CYAN);

        g.setFont(FONT_MEDIUM_BOLD);

        g.drawString("ACTIVE ITEMS", rightX, y);

        y += UIScale.px(25);

        

        g.setColor(Color.WHITE);

        g.setFont(FONT_EXTRA_SMALL_16);

        String[] items = {

            "Pool of Loot (Lv 3) â€” Spawn money circle",

            "Shield (Lv 6) â€” Tank 3 hits",

            "Bombs (Lv 7) â€” Explosive barrage",

            "Stun (Lv 9) â€” Freeze boss 3 seconds",

            "Chromatic Purge (Lv 12) â€” Erase bullet type",

            "Time Slow (Lv 15) â€” Slow everything 85%",

            "Dash (Lv 18) â€” Quick dash + i-frames",

            "Impulse (Lv 21) â€” Push all bullets away",

            "Frost Beam (Lv 24) â€” Freeze bullets in beam"

        };

        

        for (String line : items) {

            g.drawString(line, rightX + UIScale.px(10), y);

            y += UIScale.px(20);

        }

        

        // === ACTION BUTTONS (centered below text) ===

        int btnW = UIScale.px(300);

        int btnH = UIScale.px(55);

        int btnX = (width - btnW) / 2;

        int btnY1 = height - UIScale.px(220);

        int btnY2 = height - UIScale.px(130);

        

        helpShowcaseButton.setPosition(btnX, btnY1);

        helpTutorialButton.setPosition(btnX, btnY2);

        

        helpShowcaseButton.update(helpSelectedButton == 0, time);

        helpTutorialButton.update(helpSelectedButton == 1, time);

        

        helpShowcaseButton.draw(g, time);

        helpTutorialButton.draw(g, time);

        

        // Subtitles under each button

        g.setFont(FONT_EXTRA_SMALL_13);

        g.setColor(new Color(160, 170, 190));

        String sub1 = "Browse all attacks and items";

        FontMetrics fm1 = g.getFontMetrics();

        g.drawString(sub1, btnX + (btnW - fm1.stringWidth(sub1)) / 2, btnY1 + btnH + UIScale.px(18));

        

        String sub2 = "Play a guided practice level";

        g.drawString(sub2, btnX + (btnW - fm1.stringWidth(sub2)) / 2, btnY2 + btnH + UIScale.px(18));

        

        // Controls hint at bottom

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_TINY);

        drawPromptWithIcons(g, width / 2, height - UIScale.px(20), "CONTROLS: ", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " = Navigate  |  ENTER = Select  |  ESC = Back");
    }

    /**
     * Draw the tutorial popup overlay (semi-transparent backdrop + centered panel).
     * Called from Game's render when tutorialPopupActive is true during PLAYING state.
     */
    public void drawTutorialPopup(Graphics2D g, int width, int height, String title, String[] body, double time, int inputDelay) {
        // Dark overlay
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, width, height);
        
        // Panel dimensions â€” larger for readability
        int panelW = UIScale.px(700);
        int lineCount = body.length + 2; // title + body lines + continue prompt
        int panelH = UIScale.px(110 + lineCount * 38);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        
        // Panel background
        g.setColor(new Color(15, 18, 30, 245));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 20, 20);
        
        // Outer glow border
        g.setColor(new Color(80, 200, 240, 60));
        g.setStroke(RenderCache.getStroke(4f));
        g.drawRoundRect(panelX - 2, panelY - 2, panelW + 4, panelH + 4, 22, 22);
        
        // Inner border
        g.setColor(ColorPalette.ACCENT_CYAN);
        g.setStroke(RenderCache.getStroke(2f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 20, 20);
        
        // Title â€” large and bold
        g.setFont(FONT_LARGE_32);
        g.setColor(ColorPalette.ACCENT_CYAN);
        FontMetrics fmTitle = g.getFontMetrics();
        g.drawString(title, panelX + (panelW - fmTitle.stringWidth(title)) / 2, panelY + UIScale.px(50));
        
        // Divider line under title
        int divY = panelY + UIScale.px(60);
        g.setColor(new Color(80, 200, 240, 80));
        g.setStroke(RenderCache.getStroke(1f));
        g.drawLine(panelX + UIScale.px(30), divY, panelX + panelW - UIScale.px(30), divY);
        
        // Body text â€” parse {MOVE}, {USE_ITEM}, {PAUSE} and {C:COLOR}...{/C} tokens
        g.setFont(FONT_SMALL);
        int bodyY = panelY + UIScale.px(90);
        int panelCenterX = panelX + panelW / 2;
        for (String line : body) {
            Object[] segments = parseTutorialTokens(line);
            drawTutorialLine(g, panelCenterX, bodyY, segments);
            bodyY += UIScale.px(34);
        }
        
        // Continue prompt â€” progress bar while locked, pulsing text when ready
        if (inputDelay > 0) {
            // Small progress bar filling from left to right
            int barW = UIScale.px(200);
            int barH = UIScale.px(6);
            int barX = panelX + (panelW - barW) / 2;
            int barY = panelY + panelH - UIScale.px(25);
            // Background track
            g.setColor(new Color(40, 50, 70, 180));
            g.fillRoundRect(barX, barY, barW, barH, barH, barH);
            // Fill â€” progress from 0 to 1 as delay counts down from 60 to 0
            double progress = 1.0 - (inputDelay / 60.0);
            int fillW = (int)(barW * progress);
            if (fillW > 0) {
                g.setColor(new Color(80, 200, 240, 200));
                g.fillRoundRect(barX, barY, fillW, barH, barH, barH);
            }
        } else {
            double pulse = 0.5 + 0.5 * Math.sin(time * 4);
            g.setColor(new Color(180, 190, 210, (int)(120 + 135 * pulse)));
            g.setFont(FONT_EXTRA_SMALL_16);
            FontMetrics fmHint = g.getFontMetrics();
            String hint = "[ Press any key or click to continue ]";
            g.drawString(hint, panelX + (panelW - fmHint.stringWidth(hint)) / 2, panelY + panelH - UIScale.px(22));
        }
    }
    
    /** Simple wrapper to carry a colored text segment in tutorial lines. */
    private static class ColoredText {
        final String text;
        final Color color;
        ColoredText(String text, Color color) { this.text = text; this.color = color; }
    }
    
    /** Map a color name token to a Color. */
    private static Color tutorialColor(String name) {
        switch (name) {
            case "GOLD":   return ColorPalette.TEXT_GOLD;
            case "CYAN":   return ColorPalette.ACCENT_CYAN;
            case "RED":    return ColorPalette.ACCENT_RED_BRIGHT;
            case "GREEN":  return new Color(80, 230, 120);
            case "ORANGE": return ColorPalette.ACCENT_ORANGE;
            case "YELLOW": return ColorPalette.ACCENT_YELLOW;
            default:       return Color.WHITE;
        }
    }
    
    /**
     * Parse tutorial text tokens into mixed segments for rendering.
     * Supports: {MOVE}, {USE_ITEM}, {PAUSE} â†’ KeyBindManager.Action
     *           {C:COLOR}text{/C} â†’ ColoredText
     *           Plain text â†’ String
     */
    private Object[] parseTutorialTokens(String text) {
        java.util.List<Object> segments = new java.util.ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int braceStart = text.indexOf('{', i);
            if (braceStart < 0) {
                segments.add(text.substring(i));
                break;
            }
            int braceEnd = text.indexOf('}', braceStart);
            if (braceEnd < 0) {
                segments.add(text.substring(i));
                break;
            }
            // Add text before the token
            if (braceStart > i) {
                segments.add(text.substring(i, braceStart));
            }
            String token = text.substring(braceStart + 1, braceEnd);
            
            // Check for color start token: C:COLOR
            if (token.startsWith("C:")) {
                String colorName = token.substring(2);
                Color color = tutorialColor(colorName);
                // Find the closing {/C}
                int closeIdx = text.indexOf("{/C}", braceEnd + 1);
                if (closeIdx >= 0) {
                    String coloredContent = text.substring(braceEnd + 1, closeIdx);
                    segments.add(new ColoredText(coloredContent, color));
                    i = closeIdx + 4; // skip past {/C}
                } else {
                    // No closing tag â€” treat as literal
                    segments.add(text.substring(braceStart, braceEnd + 1));
                    i = braceEnd + 1;
                }
                continue;
            }
            
            // Check for closing color tag (shouldn't appear standalone)
            if (token.equals("/C")) {
                i = braceEnd + 1;
                continue;
            }
            
            switch (token) {
                case "MOVE":
                    segments.add(KeyBindManager.Action.MOVE_UP);
                    segments.add(KeyBindManager.Action.MOVE_LEFT);
                    segments.add(KeyBindManager.Action.MOVE_DOWN);
                    segments.add(KeyBindManager.Action.MOVE_RIGHT);
                    break;
                case "USE_ITEM":
                    segments.add(KeyBindManager.Action.USE_ITEM);
                    break;
                case "PAUSE":
                    segments.add(KeyBindManager.Action.PAUSE);
                    break;
                default:
                    segments.add("{" + token + "}");
                    break;
            }
            i = braceEnd + 1;
        }
        return segments.toArray();
    }
    
    /**
     * Draw a single tutorial popup line with key sprites and colored text, centered.
     */
    private void drawTutorialLine(Graphics2D g, int centerX, int y, Object[] segments) {
        FontMetrics fm = g.getFontMetrics();
        int iconH = fm.getHeight() - 2;
        
        // Measure pass
        int totalWidth = 0;
        for (Object seg : segments) {
            if (seg instanceof String) {
                totalWidth += fm.stringWidth((String) seg);
            } else if (seg instanceof ColoredText) {
                totalWidth += fm.stringWidth(((ColoredText) seg).text);
            } else if (seg instanceof KeyBindManager.Action) {
                if (Game.keyBindManager != null) {
                    java.awt.image.BufferedImage icon = Game.keyBindManager.getActionIcon((KeyBindManager.Action) seg);
                    if (icon != null) {
                        totalWidth += iconH * icon.getWidth() / icon.getHeight() + 2;
                    } else {
                        totalWidth += measureKeyCap(fm, keyText((KeyBindManager.Action) seg));
                    }
                } else {
                    totalWidth += measureKeyCap(fm, keyText((KeyBindManager.Action) seg));
                }
            }
        }
        
        // Draw pass
        int drawX = centerX - totalWidth / 2;
        for (Object seg : segments) {
            if (seg instanceof String) {
                g.setColor(Color.WHITE);
                g.drawString((String) seg, drawX, y);
                drawX += fm.stringWidth((String) seg);
            } else if (seg instanceof ColoredText) {
                ColoredText ct = (ColoredText) seg;
                g.setColor(ct.color);
                g.drawString(ct.text, drawX, y);
                drawX += fm.stringWidth(ct.text);
            } else if (seg instanceof KeyBindManager.Action) {
                KeyBindManager.Action action = (KeyBindManager.Action) seg;
                if (Game.keyBindManager != null) {
                    java.awt.image.BufferedImage icon = Game.keyBindManager.getActionIcon(action);
                    if (icon != null) {
                        int iW = iconH * icon.getWidth() / icon.getHeight();
                        g.drawImage(icon, drawX, y - iconH + 2, iW, iconH, null);
                        drawX += iW + 2;
                    } else {
                        Color sc = g.getColor();
                        drawX += drawKeyCap(g, fm, keyText(action), drawX, y);
                        g.setColor(sc);
                    }
                } else {
                    Color sc = g.getColor();
                    drawX += drawKeyCap(g, fm, keyText(action), drawX, y);
                    g.setColor(sc);
                }
            }
        }
    }
    
    /**
     * Draw tutorial highlight effect â€” dims everything except the newly introduced HUD element
     * and draws a pulsing glow around it. Called from within drawGame so transform matches HUD.
     */
    public void drawTutorialHighlight(Graphics2D g, int width, int height, double time) {
        if (tutorialHighlightTimer <= 0 || tutorialHighlightElement < 0) return;
        if (tutorialHLW <= 0 || tutorialHLH <= 0) return;
        
        int ex = tutorialHLX, ey = tutorialHLY, ew = tutorialHLW, eh = tutorialHLH;
        int pad = UIScale.px(8);
        
        // Fade alpha based on timer (fade out in last 30 frames)
        float alpha = tutorialHighlightTimer < 30 ? tutorialHighlightTimer / 30f : 1f;
        
        // Dark overlay with cutout â€” oversized rect covers screen at any zoom level
        java.awt.geom.Area overlay = new java.awt.geom.Area(new java.awt.Rectangle(-width, -height, width * 3, height * 3));
        overlay.subtract(new java.awt.geom.Area(new java.awt.Rectangle(ex - pad, ey - pad, ew + pad * 2, eh + pad * 2)));
        Composite origComp = g.getComposite();
        g.setComposite(RenderCache.getAlpha((float)(0.55 * alpha)));
        g.setColor(Color.BLACK);
        g.fill(overlay);
        
        // Pulsing glow border around the element
        float pulse = (float)(0.5 + 0.5 * Math.sin(time * 5));
        g.setComposite(RenderCache.getAlpha(alpha));
        g.setColor(new Color(0, 255, 255, (int)(120 + 135 * pulse)));
        g.setStroke(RenderCache.getStroke(3f));
        g.drawRoundRect(ex - pad, ey - pad, ew + pad * 2, eh + pad * 2, 12, 12);
        
        // Outer glow
        g.setComposite(RenderCache.getAlpha(alpha * 0.3f));
        g.setColor(new Color(0, 255, 255));
        g.setStroke(RenderCache.getStroke(6f));
        g.drawRoundRect(ex - pad - 3, ey - pad - 3, ew + (pad + 3) * 2, eh + (pad + 3) * 2, 14, 14);
        
        g.setComposite(origComp);
    }

    /**
     * Draw the tutorial HUD indicator (step progress + "TUTORIAL" label).
     * Called during PLAYING state rendering when tutorialMode is true.
     */
    public void drawTutorialHUD(Graphics2D g, int width, int height, int currentStep, int totalSteps, String stepName,
                                String taskText, double taskProgress, boolean taskHasBar) {
        // Centered at top of screen, large and prominent
        String label = "TUTORIAL";
        String progress = "Step " + (currentStep + 1) + "/" + totalSteps + " â€” " + stepName;
        
        g.setFont(FONT_MEDIUM);
        FontMetrics fmLabel = g.getFontMetrics();
        int labelW = fmLabel.stringWidth(label);
        
        g.setFont(FONT_SMALL);
        FontMetrics fmProg = g.getFontMetrics();
        int progW = fmProg.stringWidth(progress);
        
        // Measure task text width
        int taskW = 0;
        if (taskText != null && !taskText.isEmpty()) {
            taskW = fmProg.stringWidth(taskText);
        }
        
        int barWidth = UIScale.px(200);
        int contentW = Math.max(labelW, Math.max(progW, taskW + (taskHasBar ? barWidth + UIScale.px(16) : 0)));
        int pillW = contentW + UIScale.px(40);
        int hasTask = (taskText != null && !taskText.isEmpty()) ? 1 : 0;
        int pillH = UIScale.px(60) + (hasTask > 0 ? UIScale.px(taskHasBar ? 40 : 24) : 0);
        int pillX = (width - pillW) / 2;
        int pillY;
        if (tutorialMode && tutorialStep < 5) {
            // Before boss bar appears: position at very bottom of screen
            pillY = height - pillH - UIScale.px(12);
        } else {
            // After boss bar appears: position above the boss health bar
            HUDLayout bossHudLayout = Game.hudLayout != null ? Game.hudLayout : HUDLayout.defaultLayout();
            HUDLayout.HUDElementConfig bossTutCfg = bossHudLayout.getConfig(HUDLayout.HUDElement.BOSS_HEALTH);
            int bossBarTop = (int)(bossTutCfg.yPercent * height);
            pillY = bossBarTop - pillH - UIScale.px(12);
        }
        
        // Background pill
        g.setColor(new Color(20, 25, 40, 210));
        g.fillRoundRect(pillX, pillY, pillW, pillH, 16, 16);
        g.setColor(ColorPalette.ACCENT_CYAN);
        g.setStroke(RenderCache.getStroke(2f));
        g.drawRoundRect(pillX, pillY, pillW, pillH, 16, 16);
        
        // "TUTORIAL" label â€” large, centered
        g.setFont(FONT_MEDIUM);
        g.setColor(ColorPalette.ACCENT_CYAN);
        g.drawString(label, (width - labelW) / 2, pillY + UIScale.px(24));
        
        // Step progress â€” centered below
        g.setFont(FONT_SMALL);
        g.setColor(new Color(200, 210, 230));
        g.drawString(progress, (width - progW) / 2, pillY + UIScale.px(48));
        
        // Task bar â€” below step progress
        if (taskText != null && !taskText.isEmpty()) {
            int taskY = pillY + UIScale.px(60);
            
            // Task text
            g.setFont(FONT_SMALL);
            g.setColor(new Color(255, 230, 140));
            if (taskHasBar) {
                // Text on the left, bar on the right
                g.drawString(taskText, pillX + UIScale.px(16), taskY + UIScale.px(12));
                
                // Progress bar
                int barH = UIScale.px(12);
                int barX = pillX + pillW - barWidth - UIScale.px(16);
                int barY = taskY + UIScale.px(3);
                
                // Bar background
                g.setColor(new Color(40, 45, 60, 200));
                g.fillRoundRect(barX, barY, barWidth, barH, 6, 6);
                
                // Bar fill
                int fillWidth = (int)(barWidth * Math.min(1.0, taskProgress));
                if (fillWidth > 0) {
                    Color barColor = taskProgress >= 1.0 ? new Color(80, 255, 120) : ColorPalette.ACCENT_CYAN;
                    g.setColor(barColor);
                    g.fillRoundRect(barX, barY, fillWidth, barH, 6, 6);
                }
                
                // Bar border
                g.setColor(new Color(100, 120, 160, 150));
                g.setStroke(RenderCache.getStroke(1f));
                g.drawRoundRect(barX, barY, barWidth, barH, 6, 6);
            } else {
                // Just centered task text
                int tw = fmProg.stringWidth(taskText);
                g.drawString(taskText, (width - tw) / 2, taskY + UIScale.px(12));
            }
        }
    }

    /**
     * Draw the first-save tutorial prompt overlay on the menu screen.
     */
    public void drawTutorialPrompt(Graphics2D g, int width, int height, double time, int selection) {
        // Dark overlay
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, width, height);
        
        // Panel
        int panelW = UIScale.px(460);
        int panelH = UIScale.px(220);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        
        g.setColor(new Color(20, 25, 35, 240));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        g.setColor(ColorPalette.ACCENT_CYAN);
        g.setStroke(RenderCache.getStroke(2f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        
        // Title
        g.setFont(FONT_LARGE);
        g.setColor(ColorPalette.ACCENT_CYAN);
        FontMetrics fmTitle = g.getFontMetrics();
        String title = "PLAY THE TUTORIAL?";
        g.drawString(title, panelX + (panelW - fmTitle.stringWidth(title)) / 2, panelY + UIScale.px(45));
        
        // Body
        g.setFont(FONT_EXTRA_SMALL_16);
        g.setColor(Color.WHITE);
        FontMetrics fmBody = g.getFontMetrics();
        String line1 = "Learn the basics in a guided practice level.";
        String line2 = "No progress will be saved.";
        g.drawString(line1, panelX + (panelW - fmBody.stringWidth(line1)) / 2, panelY + UIScale.px(85));
        g.drawString(line2, panelX + (panelW - fmBody.stringWidth(line2)) / 2, panelY + UIScale.px(110));
        
        // Buttons: Yes / No
        int btnW = UIScale.px(120);
        int btnH = UIScale.px(45);
        int btnY = panelY + UIScale.px(145);
        int btnGap = UIScale.px(30);
        int yesX = panelX + (panelW / 2) - btnW - btnGap / 2;
        int noX = panelX + (panelW / 2) + btnGap / 2;
        
        // Yes button
        Color yesBg = selection == 0 ? new Color(40, 160, 60) : new Color(40, 60, 40);
        Color yesBorder = selection == 0 ? new Color(80, 220, 100) : new Color(60, 80, 60);
        g.setColor(yesBg);
        g.fillRoundRect(yesX, btnY, btnW, btnH, 10, 10);
        g.setColor(yesBorder);
        g.setStroke(RenderCache.getStroke(2f));
        g.drawRoundRect(yesX, btnY, btnW, btnH, 10, 10);
        g.setFont(FONT_MEDIUM_BOLD);
        g.setColor(Color.WHITE);
        FontMetrics fmBtn = g.getFontMetrics();
        g.drawString("YES", yesX + (btnW - fmBtn.stringWidth("YES")) / 2, btnY + (btnH + fmBtn.getAscent()) / 2 - 2);
        
        // No button
        Color noBg = selection == 1 ? new Color(160, 50, 50) : new Color(60, 40, 40);
        Color noBorder = selection == 1 ? new Color(220, 80, 80) : new Color(80, 60, 60);
        g.setColor(noBg);
        g.fillRoundRect(noX, btnY, btnW, btnH, 10, 10);
        g.setColor(noBorder);
        g.drawRoundRect(noX, btnY, btnW, btnH, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString("NO", noX + (btnW - fmBtn.stringWidth("NO")) / 2, btnY + (btnH + fmBtn.getAscent()) / 2 - 2);
    }

    public void drawTutorialCompleteScreen(Graphics2D g, int width, int height, double time, int selection) {
        // Full opaque background
        UITheme.drawScreenBackground(g, width, height, time);

        // Panel
        int panelW = UIScale.px(520);
        int panelH = UIScale.px(320);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        g.setColor(new Color(20, 25, 35, 240));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        g.setColor(ColorPalette.ACCENT_CYAN);
        g.setStroke(RenderCache.getStroke(2f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 16, 16);

        // Title
        g.setFont(FONT_LARGE);
        g.setColor(ColorPalette.ACCENT_CYAN);
        FontMetrics fmTitle = g.getFontMetrics();
        String title = "TUTORIAL COMPLETE!";
        g.drawString(title, panelX + (panelW - fmTitle.stringWidth(title)) / 2, panelY + UIScale.px(50));

        // Achievement line
        g.setFont(FONT_MEDIUM_BOLD);
        g.setColor(ColorPalette.ACCENT_YELLOW);
        FontMetrics fmMed = g.getFontMetrics();
        String achLine = "Achievement Unlocked!";
        g.drawString(achLine, panelX + (panelW - fmMed.stringWidth(achLine)) / 2, panelY + UIScale.px(95));

        // Body text
        g.setFont(FONT_EXTRA_SMALL_16);
        g.setColor(Color.WHITE);
        FontMetrics fmBody = g.getFontMetrics();
        String line1 = "Great job! You've learned the basics.";
        String line2 = "You're ready for the real thing!";
        g.drawString(line1, panelX + (panelW - fmBody.stringWidth(line1)) / 2, panelY + UIScale.px(140));
        g.drawString(line2, panelX + (panelW - fmBody.stringWidth(line2)) / 2, panelY + UIScale.px(165));

        // Buttons: Leave / Play Again
        int btnW = UIScale.px(140);
        int btnH = UIScale.px(45);
        int btnY = panelY + UIScale.px(220);
        int btnGap = UIScale.px(30);
        int leaveX = panelX + (panelW / 2) - btnW - btnGap / 2;
        int againX = panelX + (panelW / 2) + btnGap / 2;

        // Leave button
        Color leaveBg = selection == 0 ? new Color(40, 160, 60) : new Color(40, 60, 40);
        Color leaveBorder = selection == 0 ? new Color(80, 220, 100) : new Color(60, 80, 60);
        g.setColor(leaveBg);
        g.fillRoundRect(leaveX, btnY, btnW, btnH, 10, 10);
        g.setColor(leaveBorder);
        g.setStroke(RenderCache.getStroke(2f));
        g.drawRoundRect(leaveX, btnY, btnW, btnH, 10, 10);
        g.setFont(FONT_MEDIUM_BOLD);
        g.setColor(Color.WHITE);
        FontMetrics fmBtn = g.getFontMetrics();
        g.drawString("LEAVE", leaveX + (btnW - fmBtn.stringWidth("LEAVE")) / 2, btnY + (btnH + fmBtn.getAscent()) / 2 - 2);

        // Play Again button
        Color againBg = selection == 1 ? new Color(40, 100, 180) : new Color(40, 50, 70);
        Color againBorder = selection == 1 ? new Color(80, 160, 240) : new Color(60, 70, 80);
        g.setColor(againBg);
        g.fillRoundRect(againX, btnY, btnW, btnH, 10, 10);
        g.setColor(againBorder);
        g.drawRoundRect(againX, btnY, btnW, btnH, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString("REPLAY", againX + (btnW - fmBtn.stringWidth("REPLAY")) / 2, btnY + (btnH + fmBtn.getAscent()) / 2 - 2);
    }

    

    public void drawAchievements(Graphics2D g, int width, int height, double time, AchievementManager achievementManager, double scrollOffset) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "ACHIEVEMENTS", width, UIScale.px(80), ColorPalette.ACCENT_YELLOW, ColorPalette.TEXT_GOLD, time, FONT_TITLE_MEDIUM);

        

        // Achievement count
        FontMetrics fm;

        int unlocked = achievementManager.getUnlockedCount();

        int total = achievementManager.getAllAchievements().size();

        g.setFont(FONT_MEDIUM_BOLD);

        g.setColor(ColorPalette.SUCCESS_GREEN); // Green

        String countText = unlocked + " / " + total + " Unlocked";

        fm = g.getFontMetrics();

        g.drawString(countText, (width - fm.stringWidth(countText)) / 2, UIScale.px(120));

        

        // Draw achievements in a grid

        java.util.List<Achievement> achievements = achievementManager.getAllAchievements();

        int columns = 3;

        int cardWidth = UIScale.px(380);

        int cardHeight = UIScale.px(100);

        int startX = (width - (columns * cardWidth + (columns - 1) * UIScale.px(20))) / 2;

        int startY = UIScale.px(150);

        int gapX = UIScale.px(20);

        int gapY = UIScale.px(15);

        

        // Create clipping region for scrollable area

        g.setClip(0, UIScale.px(140), width, height - UIScale.px(180));

        

        for (int i = 0; i < achievements.size(); i++) {

            Achievement ach = achievements.get(i);

            int col = i % columns;

            int row = i / columns;

            int x = startX + col * (cardWidth + gapX);

            int y = (int)(startY + row * (cardHeight + gapY) - scrollOffset);

            

            // Only draw if visible in clipping region

            if (y + cardHeight < UIScale.px(140) || y > height - UIScale.px(40)) {

                continue;

            }

            

            // Card background

            if (ach.isUnlocked()) {

                // Unlocked - golden glow

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_GOLD, 40));

                g.fillRoundRect(x - UIScale.px(3), y - UIScale.px(3), cardWidth + UIScale.px(6), cardHeight + UIScale.px(6), UIScale.px(15), UIScale.px(15));

                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 240));

            } else {

                // Locked - darker

                g.setColor(new Color(30, 35, 45, 240));

            }

            g.fillRoundRect(x, y, cardWidth, cardHeight, UIScale.px(12), UIScale.px(12));

            

            // Border

            g.setStroke(RenderCache.getStroke(2));

            if (ach.isUnlocked()) {

                g.setColor(ColorPalette.TEXT_GOLD); // Gold border

            } else {

                g.setColor(ColorPalette.BORDER_STEEL); // Grey border

            }

            g.drawRoundRect(x, y, cardWidth, cardHeight, UIScale.px(12), UIScale.px(12));

            

            // Achievement icon/status

            int iconSize = UIScale.px(40);

            int iconX = x + UIScale.px(15);

            int iconY = y + (cardHeight - iconSize) / 2;

            

            if (ach.isUnlocked()) {

                // Checkmark circle

                g.setColor(ColorPalette.SUCCESS_GREEN); // Green

                g.fillOval(iconX, iconY, iconSize, iconSize);

                g.setColor(Color.WHITE);

                g.setStroke(RenderCache.getStroke(3));

                g.drawLine(iconX + UIScale.px(10), iconY + UIScale.px(20), iconX + UIScale.px(18), iconY + UIScale.px(28));

                g.drawLine(iconX + UIScale.px(18), iconY + UIScale.px(28), iconX + UIScale.px(30), iconY + UIScale.px(12));

            } else {

                // Lock icon

                g.setColor(NODE_LOCKED_ICON);

                g.fillOval(iconX, iconY, iconSize, iconSize);

                g.setColor(new Color(60, 60, 70));

                g.fillRect(iconX + UIScale.px(12), iconY + UIScale.px(22), UIScale.px(16), UIScale.px(14));

                g.setColor(new Color(80, 80, 90));

                g.setStroke(RenderCache.getStroke(2));

                g.drawArc(iconX + UIScale.px(13), iconY + UIScale.px(10), UIScale.px(14), UIScale.px(16), 0, 180);

            }

            

            // Achievement name

            g.setFont(FONT_EXTRA_SMALL_16);

            if (ach.isUnlocked()) {

                g.setColor(ColorPalette.TEXT_GOLD); // Gold

            } else {

                g.setColor(new Color(150, 150, 160));

            }

            g.drawString(ach.getName(), x + UIScale.px(65), y + UIScale.px(28));

            

            // Description (word-wrapped)

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(ach.isUnlocked() ? RenderCache.SLATE_200_200_210 : new Color(100, 100, 110));

            int descEndY;
            {
                String desc = ach.getDescription();
                int descMaxW = cardWidth - UIScale.px(80);
                FontMetrics descFm = g.getFontMetrics();
                if (descFm.stringWidth(desc) <= descMaxW) {
                    g.drawString(desc, x + UIScale.px(65), y + UIScale.px(48));
                    descEndY = y + UIScale.px(48);
                } else {
                    // Word wrap into lines
                    String[] words = desc.split(" ");
                    StringBuilder line = new StringBuilder();
                    int descY = y + UIScale.px(48);
                    for (String word : words) {
                        String test = line.length() == 0 ? word : line + " " + word;
                        if (descFm.stringWidth(test) > descMaxW && line.length() > 0) {
                            g.drawString(line.toString(), x + UIScale.px(65), descY);
                            descY += descFm.getHeight();
                            line = new StringBuilder(word);
                        } else {
                            if (line.length() > 0) line.append(" ");
                            line.append(word);
                        }
                    }
                    if (line.length() > 0) {
                        g.drawString(line.toString(), x + UIScale.px(65), descY);
                    }
                    descEndY = descY;
                }
            }

            

            // Progress bar (only if not unlocked)

            if (!ach.isUnlocked()) {

                int barWidth = cardWidth - UIScale.px(80);

                int barHeight = UIScale.px(8);

                int barX = x + UIScale.px(65);

                int barY = descEndY + UIScale.px(10);

                

                // Background

                g.setColor(RenderCache.DARK_40_45_55);

                g.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);

                

                // Fill

                float progress = ach.getProgressPercent();

                g.setColor(ColorPalette.ACCENT_CYAN); // Teal

                g.fillRoundRect(barX, barY, (int)(barWidth * progress), barHeight, 4, 4);

                

                // Progress text

                g.setFont(FONT_EXTRA_SMALL_11);

                g.setColor(new Color(120, 130, 140));

                String progressText = ach.getProgress() + " / " + ach.getTarget();

                g.drawString(progressText, barX + barWidth - fm.stringWidth(progressText) + UIScale.px(20), barY + UIScale.px(20));

            } else {

                // "COMPLETE" badge

                g.setFont(FONT_EXTRA_SMALL_12);

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.drawString("COMPLETE", x + UIScale.px(65), descEndY + UIScale.px(20));

            }

        }

        

        // Reset clip

        g.setClip(null);

        

        // Controls hint

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_SMALL);

        drawPromptWithIcons(g, width / 2, height - UIScale.px(40),

            "Press ", KeyBindManager.Action.BACK, " to return to menu | ", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " to scroll");

    }

    

    public void drawStats(Graphics2D g, int width, int height, double time, PassiveUpgradeManager passiveManager, boolean loadoutLocked) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "LOADOUT", width, 80, ColorPalette.ACCENT_CYAN, ColorPalette.ACCENT_ORANGE, time, FONT_TITLE_MEDIUM);

        

        // Instructions at top (drawn first so banner/money appear between title and this)

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_EXTRA_SMALL_16);

        drawPromptWithIcons(g, width / 2, 145, KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " to select | ", KeyBindManager.Action.MOVE_LEFT, "/", KeyBindManager.Action.MOVE_RIGHT, " to adjust | ", KeyBindManager.Action.BACK, " to return");

        

        if (loadoutLocked) {

            // Lock banner below the controls line

            int bannerW = UIScale.px(700);

            int bannerH = UIScale.px(30);

            int bannerX = (width - bannerW) / 2;

            int bannerY = 160;

            // Banner background

            g.setColor(new Color(180, 60, 40, 180));

            g.fillRoundRect(bannerX, bannerY, bannerW, bannerH, UIScale.px(8), UIScale.px(8));

            g.setColor(new Color(255, 100, 80, 200));

            g.setStroke(RenderCache.getStroke(1.5f));

            g.drawRoundRect(bannerX, bannerY, bannerW, bannerH, UIScale.px(8), UIScale.px(8));

            g.setStroke(RenderCache.getStroke(1f));

            // Banner text - vertically centered in banner

            g.setFont(FONT_EXTRA_SMALL_16);

            g.setColor(new Color(255, 220, 200));

            String lockMsg = "LOCKED - Complete or abandon your current level to make changes";

            FontMetrics lockFm = g.getFontMetrics();

            g.drawString(lockMsg, (width - lockFm.stringWidth(lockMsg)) / 2, bannerY + bannerH / 2 + lockFm.getAscent() / 2 - 1);

        } else {

            // Show total money with glow

            g.setColor(ColorPalette.SUCCESS_GREEN);

            g.setFont(FONT_LARGE);

            String money = "Money: $" + gameData.getTotalMoney();

            FontMetrics fm = g.getFontMetrics();

            int moneyX = (width - fm.stringWidth(money)) / 2;

            g.drawString(money, moneyX, 120);

        }

    }

    

    public void drawStatsUpgrades(Graphics2D g, int width, int selectedStatItem, PassiveUpgradeManager passiveManager, double scrollOffset, boolean loadoutLocked, int bestLevel) {

        int baseY = loadoutLocked ? 220 : 180;

        int y = baseY - (int)scrollOffset;

        int cardWidth = 900;

        int cardHeight = 65;

        int cardSpacing = 10;

        int currentIndex = 0;

        

        // Section 1: Active Item (index 0)

        g.setColor(loadoutLocked ? new Color(160, 140, 140) : ColorPalette.SUCCESS_GREEN);

        g.setFont(FONT_SMALL);

        g.drawString("ACTIVE ITEM - Unlock from mega bosses", width / 2 - UIScale.px(400), y);

        y += UIScale.px(30);

        

        boolean isSelected = currentIndex == selectedStatItem;

        int itemX = width / 2 - cardWidth / 2;

        

        // All active items data: {ItemType, unlockLevel, name, description}

        Object[][] allItems = {

            {ActiveItem.ItemType.LUCKY_CHARM, 3, "Pool of Loot", "Spawn money circle for bonus cash"},

            {ActiveItem.ItemType.SHIELD, 6, "Shield", "3 orbiting shields block bullets"},

            {ActiveItem.ItemType.BOMBS, 7, "Bombs", "Rain explosive bombs on screen"},

            {ActiveItem.ItemType.STUN, 9, "Stun", "Freeze the boss temporarily"},

            {ActiveItem.ItemType.IMPULSE, 12, "Impulse", "Push all bullets away from you"},

            {ActiveItem.ItemType.TIME_SLOW, 15, "Time Slow", "Slow bullets & beams by 85%"},

            {ActiveItem.ItemType.TYPE_PURGE, 18, "Chromatic Purge", "Erase all bullets of a random type"},

            {ActiveItem.ItemType.DASH, 21, "Dash", "Quick dash with invincibility"},

            {ActiveItem.ItemType.FROST_BEAM, 24, "Frost Beam", "Freeze bullets in an icy beam"},

        };

        

        java.util.List<ActiveItem.ItemType> unlockedItems = gameData.getUnlockedItems();

        ActiveItem equippedItem = gameData.getEquippedItem();

        

        // Single card carousel view - navigate with left/right arrows


        int displayIndex = statsActiveItemDisplayIndex;


        if (displayIndex < 0) displayIndex = 0;


        if (displayIndex >= allItems.length) displayIndex = allItems.length - 1;


        


        ActiveItem.ItemType itemType = (ActiveItem.ItemType) allItems[displayIndex][0];


        int unlockLevel = (int) allItems[displayIndex][1];


        String itemName = (String) allItems[displayIndex][2];


        String itemDesc = (String) allItems[displayIndex][3];


        boolean isUnlocked = unlockedItems.contains(itemType);


        boolean isEquipped = equippedItem != null && equippedItem.getType() == itemType;


        


        int singleCardH = UIScale.px(120);


        


        // Card shadow


        g.setColor(RenderCache.BLACK_100);


        g.fillRoundRect(itemX + UIScale.px(3), y + UIScale.px(3), cardWidth, singleCardH, UIScale.px(12), UIScale.px(12));


        


        // Card background


        Color cardBg;


        if (isSelected && isEquipped) {


            cardBg = new Color(50, 80, 50, 230);


        } else if (isSelected) {


            cardBg = new Color(86, 96, 120, 230);


        } else if (isEquipped) {


            cardBg = new Color(60, 90, 55, 230);


        } else if (isUnlocked) {


            cardBg = ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 200);


        } else {


            cardBg = new Color(35, 35, 45, 220);


        }


        g.setColor(cardBg);


        g.fillRoundRect(itemX, y, cardWidth, singleCardH, UIScale.px(12), UIScale.px(12));


        


        // Selection highlight border


        if (isSelected) {


            g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_GOLD, 200));


            g.setStroke(RenderCache.getStroke(2.5f));


            g.drawRoundRect(itemX, y, cardWidth, singleCardH, UIScale.px(12), UIScale.px(12));


            g.setStroke(RenderCache.getStroke(1f));


        } else if (isEquipped) {


            g.setColor(new Color(163, 210, 140, 180));


            g.setStroke(RenderCache.getStroke(2f));


            g.drawRoundRect(itemX, y, cardWidth, singleCardH, UIScale.px(12), UIScale.px(12));


            g.setStroke(RenderCache.getStroke(1f));


        }


        


        // Left arrow (hidden when locked)


        if (displayIndex > 0 && !loadoutLocked) {


            g.setFont(FONT_LARGE);


            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_150);


            g.drawString("<", itemX + UIScale.px(14), y + singleCardH / 2 + UIScale.px(10));


        }


        


        // Right arrow (hidden when locked)


        if (displayIndex < allItems.length - 1 && !loadoutLocked) {


            g.setFont(FONT_LARGE);


            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_150);


            String rightArrow = ">";


            FontMetrics arrowFm = g.getFontMetrics();


            g.drawString(rightArrow, itemX + cardWidth - arrowFm.stringWidth(rightArrow) - UIScale.px(12), y + singleCardH / 2 + UIScale.px(10));


        }


        


        // Content area (between arrows)


        int contentX = itemX + UIScale.px(50);


        int contentW = cardWidth - UIScale.px(100);


        


        if (isUnlocked) {



            // --- Item icon (color-coded circular icon with symbol) ---

            int iconSize = UIScale.px(56);

            int iconX = contentX + UIScale.px(15);

            int iconY = y + (singleCardH - iconSize) / 2;



            // Get item-specific color and symbol

            Color itemIconColor = RenderCache.BLUE_100_200_255;

            String itemSymbol = "?";

            switch (itemType) {

                case LUCKY_CHARM: itemIconColor = new Color(255, 215, 80); itemSymbol = "$"; break;

                case SHIELD: itemIconColor = RenderCache.BLUE_100_180_255; itemSymbol = "O"; break;

                case BOMBS: itemIconColor = new Color(255, 110, 80); itemSymbol = "*"; break;

                case STUN: itemIconColor = new Color(255, 240, 100); itemSymbol = "!"; break;

                case TYPE_PURGE: itemIconColor = new Color(200, 130, 255); itemSymbol = "X"; break;

                case TIME_SLOW: itemIconColor = new Color(160, 200, 255); itemSymbol = "~"; break;

                case DASH: itemIconColor = new Color(100, 255, 180); itemSymbol = ">"; break;

                case IMPULSE: itemIconColor = new Color(140, 200, 255); itemSymbol = "@"; break;

                case FROST_BEAM: itemIconColor = new Color(150, 230, 255); itemSymbol = "#"; break;

            }



            // Circular background with glow

            g.setColor(new Color(itemIconColor.getRed() / 6, itemIconColor.getGreen() / 6, itemIconColor.getBlue() / 6, 160));

            g.fillOval(iconX - UIScale.px(3), iconY - UIScale.px(3), iconSize + UIScale.px(6), iconSize + UIScale.px(6));

            g.setColor(RenderCache.DARK_40_45_55);

            g.fillOval(iconX, iconY, iconSize, iconSize);

            g.setColor(new Color(itemIconColor.getRed(), itemIconColor.getGreen(), itemIconColor.getBlue(), isEquipped ? 255 : 200));

            g.setStroke(RenderCache.getStroke(2f));

            g.drawOval(iconX, iconY, iconSize, iconSize);

            g.setStroke(RenderCache.getStroke(1f));



            // Draw symbol centered in circle

            g.setFont(FONT_MEDIUM_BOLD);

            g.setColor(itemIconColor);

            FontMetrics symFm = g.getFontMetrics();

            g.drawString(itemSymbol, iconX + iconSize / 2 - symFm.stringWidth(itemSymbol) / 2, iconY + iconSize / 2 + symFm.getAscent() / 3);



            // --- Text info (right of icon) ---

            int textX = iconX + iconSize + UIScale.px(24);

            int textRightEdge = itemX + cardWidth - UIScale.px(60);



            // Item name (large, bold)

            g.setFont(FONT_TINY);

            g.setColor(isEquipped ? RenderCache.GREEN_163_210_140 : Color.WHITE);

            String displayName = itemName;

            g.drawString(displayName, textX, y + UIScale.px(35));



            // Equipped badge (inline, smaller)

            if (isEquipped) {

                FontMetrics nameFm = g.getFontMetrics();

                int badgeX = textX + nameFm.stringWidth(displayName) + UIScale.px(12);

                g.setFont(FONT_EXTRA_SMALL_11);

                FontMetrics badgeFm = g.getFontMetrics();

                String badge = "EQUIPPED";

                int badgeW = badgeFm.stringWidth(badge) + UIScale.px(12);

                int badgeH = UIScale.px(18);

                int badgeY = y + UIScale.px(22);

                g.setColor(new Color(163, 210, 140, 40));

                g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, UIScale.px(8), UIScale.px(8));

                g.setColor(new Color(163, 210, 140, 160));

                g.drawRoundRect(badgeX, badgeY, badgeW, badgeH, UIScale.px(8), UIScale.px(8));

                g.setColor(RenderCache.GREEN_163_210_140);

                g.drawString(badge, badgeX + UIScale.px(6), badgeY + UIScale.px(13));

            }



            // Description (medium, light)

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(new Color(175, 185, 200));

            g.drawString(itemDesc, textX, y + UIScale.px(56));



            // Unlock level (small, teal, with dot separator)

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(new Color(120, 175, 200));

            String lvlStr = "Unlocked at Level " + unlockLevel;

            g.drawString(lvlStr, textX, y + UIScale.px(74));



            // Subtle separator line under item info







        } else {



            // --- Locked item ---

            int iconSize = UIScale.px(56);

            int iconX = contentX + UIScale.px(15);

            int iconY = y + (singleCardH - iconSize) / 2;



            // Dark locked circle

            g.setColor(new Color(20, 20, 28, 160));

            g.fillOval(iconX - UIScale.px(3), iconY - UIScale.px(3), iconSize + UIScale.px(6), iconSize + UIScale.px(6));

            g.setColor(new Color(30, 30, 38));

            g.fillOval(iconX, iconY, iconSize, iconSize);

            g.setColor(new Color(100, 90, 70, 120));

            g.setStroke(RenderCache.getStroke(2f));

            g.drawOval(iconX, iconY, iconSize, iconSize);

            g.setStroke(RenderCache.getStroke(1f));



            // Lock symbol in circle

            g.setFont(FONT_LARGE);

            g.setColor(new Color(200, 180, 140));

            FontMetrics lockFm = g.getFontMetrics();

            String lockSym = "[X]";

            g.drawString(lockSym, iconX + iconSize / 2 - lockFm.stringWidth(lockSym) / 2, iconY + iconSize / 2 + lockFm.getAscent() / 3);



            // --- Locked text info ---

            int textX = iconX + iconSize + UIScale.px(24);



            // Encrypted name

            g.setFont(FONT_EXTRA_SMALL_16);

            g.setColor(new Color(190, 180, 160));

            String lockedName = encryptItemName(itemName);

            g.drawString(lockedName, textX, y + UIScale.px(40));



            // Unlock requirement (prominent, golden)

            g.setFont(FontPalette.get(Font.BOLD, 15));

            g.setColor(new Color(235, 210, 140));

            String reqStr = "Defeat Level " + unlockLevel + " Boss to Unlock";

            g.drawString(reqStr, textX, y + UIScale.px(62));



            // Subtle locked separator







        }




        


        // Counter: X / 9


        g.setFont(FontPalette.get(Font.BOLD, 14));


        g.setColor(ColorPalette.ACCENT_CYAN);


        String counter = (displayIndex + 1) + " / " + allItems.length;


        FontMetrics ctrFm = g.getFontMetrics();


        g.drawString(counter, itemX + (cardWidth - ctrFm.stringWidth(counter)) / 2, y + singleCardH + UIScale.px(20));


        


        y += singleCardH + UIScale.px(30) + cardSpacing;

        currentIndex++;

        

        // Section 2: All Upgrades (indices 1+) - from PassiveUpgradeManager

        y += UIScale.px(20);

        g.setColor(loadoutLocked ? new Color(160, 140, 140) : ColorPalette.ACCENT_PURPLE);

        g.setFont(FONT_SMALL);

        g.drawString("SHOP UPGRADES - Allocate purchased levels", width / 2 - UIScale.px(400), y);

        y += UIScale.px(30);

        

        // All upgrades now come from PassiveUpgradeManager

        if (passiveManager != null) {

            java.util.List<PassiveUpgrade> upgrades = passiveManager.getAllUpgrades();

            

            // Draw only unlocked adjustable upgrades (all except Extra Missiles which is last)

            for (int i = 0; i < upgrades.size() - 1; i++) {

                PassiveUpgrade upgrade = upgrades.get(i);

                // Skip locked upgrades - don't draw, don't advance y or index

                if (upgrade.getUnlockLevel() > 0 && upgrade.getUnlockLevel() > bestLevel) continue;

                isSelected = currentIndex == selectedStatItem;

                

                String icon = getPassiveIcon(upgrade.getType());

                int owned = upgrade.getCurrentLevel();  // Purchased from shop

                int active = upgrade.getActiveLevel();  // Allocated in stats & loadout

                

                drawUpgradeCard(g, width / 2 - cardWidth / 2, y, cardWidth, cardHeight,

                               icon, upgrade.getName(), active, owned, isSelected, true, loadoutLocked);

                

                y += cardHeight + cardSpacing;

                currentIndex++;

            }

            

            // Read-only section for Extra Missiles

            if (upgrades.size() > 0) {

                y += UIScale.px(20);

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.setFont(FONT_SMALL);

                g.drawString("CONSUMABLE MISSILES - Buy from shop, used on death", width / 2 - UIScale.px(400), y);

                y += UIScale.px(30);

                

                // Draw Extra Missiles (last item, read-only)

                PassiveUpgrade upgrade = upgrades.get(upgrades.size() - 1);

                isSelected = currentIndex == selectedStatItem;

                

                String icon = getPassiveIcon(upgrade.getType());

                // Show only extra missiles purchased (yellow ones that clear bullets)

                int extraMissiles = Math.max(0, gameData.getMissiles() - gameData.getBaseMissiles());

                int maxExtraMissiles = upgrade.getMaxLevel();  // Max purchasable (3)

                

                drawUpgradeCard(g, width / 2 - cardWidth / 2, y, cardWidth, cardHeight,

                               icon, upgrade.getName(), extraMissiles, maxExtraMissiles, isSelected, true, true);

                

                y += cardHeight + cardSpacing;

                currentIndex++;

            }

        }

    }

    

    private void drawUpgradeCard(Graphics2D g, int x, int y, int width, int height, String icon, String name, int current, int max, boolean isSelected, boolean isShopUpgrade) {

        drawUpgradeCard(g, x, y, width, height, icon, name, current, max, isSelected, isShopUpgrade, false);

    }

    

    private void drawUpgradeCard(Graphics2D g, int x, int y, int width, int height, String icon, String name, int current, int max, boolean isSelected, boolean isShopUpgrade, boolean isReadOnly) {

        // Shadow

        g.setColor(RenderCache.BLACK_120);

        g.fillRoundRect(x + UIScale.px(3), y + UIScale.px(3), width, height, UIScale.px(15), UIScale.px(15));

        

        // Card background

        Color cardColor;

        if (current >= max && max > 0) {

            cardColor = RenderCache.TAN_85_75_45_200; // Dark gold for maxed - better text contrast

        } else if (isSelected && !isReadOnly) {

            cardColor = new Color(120, 110, 140, 200); // Softer purple for selected

        } else {

            cardColor = ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 200);

        }

        

        g.setColor(cardColor);

        g.fillRoundRect(x, y, width, height, UIScale.px(15), UIScale.px(15));

        

        // Border glow for selected

        if (isSelected && !isReadOnly) {

            g.setColor(RenderCache.TAN_180_170_130_140); // Softer border glow

            g.setStroke(RenderCache.getStroke(2f));

            g.drawRoundRect(x, y, width, height, UIScale.px(15), UIScale.px(15));

            g.setStroke(RenderCache.getStroke(1f));

        }

        

        // Draw icon

        g.setFont(FONT_LARGE_32);

        g.setColor(ColorPalette.TEXT_GOLD);

        g.drawString(icon, x + UIScale.px(20), y + UIScale.px(40));

        

        // Draw name

        g.setFont(FONT_SMALL);

        g.setColor(Color.WHITE);

        g.drawString(name, x + UIScale.px(75), y + UIScale.px(30));

        

        // Draw level info

        g.setFont(FontPalette.get(Font.PLAIN, 14));

        g.setColor(RenderCache.GRAY_200);

        String levelInfo;

        if (isReadOnly) {

            levelInfo = "Count: " + current;

        } else {

            levelInfo = isShopUpgrade ? "Allocated: " + current + "/" + max + " owned" : "Level: " + current + "/" + max;

        }

        g.drawString(levelInfo, x + UIScale.px(75), y + UIScale.px(50));

        

        // Don't show progress bar or level text for read-only items

        if (!isReadOnly) {

            // Progress bar

            int barX = x + UIScale.px(400);

            int barY = y + UIScale.px(20);

            int barWidth = UIScale.px(350);

            int barHeight = UIScale.px(10);

            

            // Background

            g.setColor(RenderCache.DARK_40_40_50_180);

            g.fillRoundRect(barX, barY, barWidth, barHeight, UIScale.px(5), UIScale.px(5));

            

            // Fill

            if (max > 0 && current > 0) {

                double progress = (double)current / max;

                int fillWidth = (int)(barWidth * progress);

                

                GradientPaint grad = new GradientPaint(

                    barX, 0, ColorPalette.SUCCESS_GREEN,

                    barX + fillWidth, 0, ColorPalette.TEXT_GOLD

                );

                g.setPaint(grad);

                g.fillRoundRect(barX, barY, fillWidth, barHeight, UIScale.px(5), UIScale.px(5));

            }

            

            // Level text

            g.setFont(FontPalette.get(Font.BOLD, 14));

            g.setColor(current >= max && max > 0 ? ColorPalette.TEXT_GOLD : Color.WHITE);

            String levelText = current + "/" + max;

            FontMetrics fm = g.getFontMetrics();

            g.drawString(levelText, barX + barWidth + UIScale.px(10), barY + UIScale.px(10));

        }

        

        // Show buttons only if not read-only

        if (!isReadOnly) {

            // Minus button

            int btnSize = UIScale.px(35);

            int minusX = x + UIScale.px(800);

            int btnY = y + (height - btnSize) / 2;

            

            g.setColor(current > 0 ? ColorPalette.ACCENT_RED : RenderCache.GRAY_80);

            g.fillRoundRect(minusX, btnY, btnSize, btnSize, UIScale.px(8), UIScale.px(8));

            g.setColor(Color.WHITE);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(minusX, btnY, btnSize, btnSize, UIScale.px(8), UIScale.px(8));

            g.setFont(FONT_LARGE);

            g.drawString("-", minusX + UIScale.px(12), btnY + UIScale.px(26));

            

            // Plus button

            int plusX = x + UIScale.px(845);

            g.setColor(current < max ? ColorPalette.SUCCESS_GREEN : RenderCache.GRAY_80);

            g.fillRoundRect(plusX, btnY, btnSize, btnSize, UIScale.px(8), UIScale.px(8));

            g.setColor(Color.WHITE);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(plusX, btnY, btnSize, btnSize, UIScale.px(8), UIScale.px(8));

            g.setFont(FONT_LARGE);

            g.drawString("+", plusX + UIScale.px(11), btnY + UIScale.px(26));

        }

    }

    

    private String getPassiveIcon(PassiveUpgrade.UpgradeType type) {

        switch (type) {

            case MAX_HEALTH: return "H";

            case ITEM_COOLDOWN: return "C";

            case BULLET_SIZE: return "B";

            case MONEY_AND_SCORE: return "$";

            case CRITICAL_HIT: return "*";

            case SPEED_BOOST: return "S";

            case BULLET_SLOW: return "T";

            case LUCKY_DODGE: return "L";

            case TARGETING: return "@";

            case FLARES: return "F";

            default: return "?";

        }

    }

    

    public void drawLevelSelect(Graphics2D g, int width, int height, int currentLevel, int maxUnlockedLevel, double time, double scrollOffset, boolean hasSavedGame, int savedLevel, boolean planeTakeoffAnimation, double planeTakeoffTimer) {

        int selectedLevel = gameData.getSelectedLevelView();

        

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "JOURNEY MAP", width, UIScale.px(50), ColorPalette.ACCENT_PURPLE, ColorPalette.ACCENT_YELLOW, time, FontPalette.getDisplay(Font.BOLD, 42));

        

        // Show "RESUME AVAILABLE" indicator if there's a saved game
        FontMetrics fm;

        if (hasSavedGame && selectedLevel == savedLevel) {

            g.setFont(FONT_MEDIUM_BOLD);

            String resumeText = "* RESUME AVAILABLE";

            float resumePulse = (float)(0.7 + 0.3 * Math.sin(time * 3));

            g.setColor(new Color(100, 255, 100, (int)(200 * resumePulse)));

            FontMetrics resumeFm = g.getFontMetrics();

            g.drawString(resumeText, (width - resumeFm.stringWidth(resumeText)) / 2, UIScale.px(110));

        }

        

        // Progress indicator (dots at top)

        int dotY = UIScale.px(80);

        int dotSpacing = UIScale.px(20);

        int totalDots = Game.DEMO_MODE ? Game.DEMO_MAX_LEVEL : 28;
        
        // Add endless dot if unlocked
        boolean endlessUnlocked = gameData.isEndlessUnlocked();
        if (!Game.DEMO_MODE && endlessUnlocked) {
            totalDots = 29;
        }

        int dotsStartX = (width - (totalDots - 1) * dotSpacing) / 2;

        

        for (int i = 1; i <= totalDots; i++) {

            int dotX = dotsStartX + (i - 1) * dotSpacing;

            int dotSize = (i == selectedLevel) ? UIScale.px(10) : UIScale.px(6);

            
            if (i == 29) {
                // Endless mode dot - purple pulsing
                float pulse = (float)(0.5 + 0.5 * Math.sin(time * 3));
                g.setColor(new Color(160, 100, 255, (int)(150 + 105 * pulse)));
            } else if (i < currentLevel) {

                g.setColor(NODE_COMPLETED_GOLD); // Completed - gold

            } else if (i == currentLevel) {

                g.setColor(NODE_CURRENT_BLUE); // Current

            } else {

                g.setColor(NODE_LOCKED_GRAY); // Locked

            }

            

            if (i == selectedLevel) {

                // Highlight selected dot

                g.setColor(Color.WHITE);

            }

            

            g.fillOval(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize);

        }

        

        // Center Y for the level carousel - moved to lower third for plane sprite visibility

        int centerY = (int)(height * 0.67); // Lower third of screen

        int centerX = width / 2;

        

        // Draw the horizontal path line behind the nodes

        g.setColor(PATH_LINE_COLOR);

        g.setStroke(ROUND_STROKE_6);

        g.drawLine(0, centerY, width, centerY);

        

        // Draw arrow indicators on the sides

        if (selectedLevel > 1) {

            // Left arrow

            g.setFont(FontPalette.get(Font.BOLD, 50));

            float arrowPulse = (float)(0.5 + 0.5 * Math.sin(time * 4));

            g.setColor(new Color(150, 150, 160, (int)(100 + 100 * arrowPulse)));

            g.drawString("<", UIScale.px(15), centerY + UIScale.px(18));

        }

        int maxSelectLevel = Game.DEMO_MODE ? Game.DEMO_MAX_LEVEL : 28;
        if (!Game.DEMO_MODE && endlessUnlocked) maxSelectLevel = 29;
        if (selectedLevel < maxSelectLevel) {

            // Right arrow

            g.setFont(FontPalette.get(Font.BOLD, 50));

            float arrowPulse = (float)(0.5 + 0.5 * Math.sin(time * 4));

            g.setColor(new Color(150, 150, 160, (int)(100 + 100 * arrowPulse)));

            g.drawString(">", width - UIScale.px(55), centerY + UIScale.px(18));

        }

        

        // Smooth carousel: use scrollOffset to position all levels

        // Each level is spaced apart, and we scroll based on the animated offset

        int levelSpacing = width / 2; // Half screen width between levels

        int centerNodeRadius = UIScale.px(80); // Larger center node

        int sideNodeRadius = UIScale.px(50);   // Smaller side nodes

        

        // Draw levels based on scroll position (show 5 levels for smooth transitions)

        for (int i = -2; i <= 2; i++) {

            int level = selectedLevel + i;
            int maxCarouselLevel = Game.DEMO_MODE ? Game.DEMO_MAX_LEVEL : 28;
            if (!Game.DEMO_MODE && endlessUnlocked) maxCarouselLevel = 29;

            if (level < 1 || level > maxCarouselLevel) continue;

            

            // Calculate x position based on scroll offset for smooth animation

            double scrollDelta = scrollOffset - selectedLevel;

            int baseX = centerX + i * levelSpacing;

            int x = (int)(baseX - scrollDelta * levelSpacing);

            

            // Skip if off screen

            if (x < -100 || x > width + 100) continue;

            

            // Calculate size and alpha based on distance from center

            double distFromCenter = Math.abs(x - centerX) / (double)levelSpacing;

            double scale = Math.max(0.4, 1.0 - distFromCenter * 0.5);

            float alpha = (float)Math.max(0.3, 1.0 - distFromCenter * 0.6);

            

            int nodeRadius = (int)(centerNodeRadius * scale);

            

            boolean isCompleted = level < currentLevel;

            boolean isCurrent = level == currentLevel;

            boolean isLocked = level > maxUnlockedLevel;

            boolean isMegaBoss = (level % 3 == 0);

            boolean isSelected = level == selectedLevel;
            
            // === ENDLESS MODE SLOT (level 29) - Special rendering ===
            if (level == 29) {
                drawEndlessSlot(g, x, centerY, nodeRadius, alpha, scale, isSelected, distFromCenter, time, width, height, planeTakeoffAnimation, planeTakeoffTimer);
                g.setComposite(RenderCache.getAlpha(1.0f));
                continue;
            }

            

            g.setComposite(RenderCache.getAlpha(alpha));

            

            // Selection glow for center

            if (isSelected && distFromCenter < 0.3) {

                float glowPulse = (float)(0.3 + 0.2 * Math.sin(time * 4));

                g.setComposite(RenderCache.getAlpha(glowPulse * alpha));

                Color glowColor = isCurrent ? SEL_GLOW_CURRENT : 

                                  isCompleted ? SEL_GLOW_COMPLETED : SEL_GLOW_OTHER;

                g.setColor(glowColor);

                g.fillOval(x - nodeRadius - UIScale.px(25), centerY - nodeRadius - UIScale.px(25), (nodeRadius + UIScale.px(25)) * 2, (nodeRadius + UIScale.px(25)) * 2);

                g.setComposite(RenderCache.getAlpha(alpha));

            }

            

            // Node shadow

            g.setComposite(RenderCache.getAlpha(alpha));
            g.setColor(NODE_SHADOW);

            g.fillOval(x - nodeRadius + UIScale.px(5), centerY - nodeRadius + UIScale.px(5), nodeRadius * 2, nodeRadius * 2);

            

            // Completed level: golden completion ring behind the node

            if (isCompleted) {

                float ringPulse = (float)(0.6 + 0.4 * Math.sin(time * 2 + level * 0.3));

                g.setComposite(RenderCache.getAlpha(ringPulse * alpha));

                g.setColor(NODE_COMPLETED_RING);

                g.setStroke(RenderCache.getStroke(4));

                g.drawOval(x - nodeRadius - UIScale.px(8), centerY - nodeRadius - UIScale.px(8), (nodeRadius + UIScale.px(8)) * 2, (nodeRadius + UIScale.px(8)) * 2);

                g.setComposite(RenderCache.getAlpha(alpha));

            }

            

            // Node fill color (solid fills - avoids per-frame GradientPaint allocation)
            if (isMegaBoss) {
                g.setColor(isCompleted ? MEGA_COMPLETED : isCurrent ? MEGA_CURRENT : MEGA_LOCKED);
            } else {
                g.setColor(isCompleted ? MINI_COMPLETED : isCurrent ? MINI_CURRENT : MINI_LOCKED);
            }
            g.fillOval(x - nodeRadius, centerY - nodeRadius, nodeRadius * 2, nodeRadius * 2);

            

            // Node border

            if (isSelected && distFromCenter < 0.3) {

                g.setColor(Color.WHITE);

                g.setStroke(RenderCache.getStroke(5));

            } else if (isCurrent) {

                g.setColor(NODE_BORDER_CURRENT);

                g.setStroke(RenderCache.getStroke(3));

            } else if (isCompleted) {

                g.setColor(NODE_BORDER_COMPLETED);

                g.setStroke(RenderCache.getStroke(3));

            } else {

                g.setColor(NODE_BORDER_LOCKED);

                g.setStroke(RenderCache.getStroke(2));

            }

            g.drawOval(x - nodeRadius, centerY - nodeRadius, nodeRadius * 2, nodeRadius * 2);

            

            // Draw plane sprite above the node (rotated tip up with cool effects)

            try {

                BufferedImage planeSprite = Boss.getSpriteForLevel(level);

                if (planeSprite != null) {

                    int spriteWidth = (int)(planeSprite.getWidth() * scale);

                    int spriteHeight = (int)(planeSprite.getHeight() * scale);

                    int spriteX = x;

                    

                    // Move sprite higher up and add bounce animation for selected

                    float bounceOffset = 0;

                    if (isSelected && distFromCenter < 0.3 && !planeTakeoffAnimation) {

                        bounceOffset = (float)(Math.sin(time * 5) * 8); // Gentle hovering

                    }

                    

                    // Apply takeoff animation - plane flies straight up

                    int takeoffOffset = 0;

                    if (planeTakeoffAnimation && isSelected && distFromCenter < 0.3) {

                        // Easing function: starts slow, speeds up

                        float progress = (float)(planeTakeoffTimer / 60.0);

                        float easedProgress = progress * progress; // Quadratic ease-in

                        takeoffOffset = (int)(easedProgress * height * 1.5); // Fly off screen

                    }

                    

                    int spriteY = centerY - nodeRadius - spriteHeight - UIScale.px(110) - (int)bounceOffset - takeoffOffset;

                    

                    // Glow effect for unlocked planes using cached glow image
                    if (!isLocked) {
                        float glowIntensity = (float)(0.5 + 0.3 * Math.sin(time * 0.8));
                        
                        Color glowColor;
                        if (isMegaBoss) {
                            glowColor = GLOW_MEGA_BOSS; // Purple glow for mega bosses
                        } else if (isCompleted) {
                            glowColor = GLOW_COMPLETED; // Green glow for completed
                        } else if (isCurrent) {
                            glowColor = GLOW_CURRENT; // Blue glow for current
                        } else {
                            glowColor = GLOW_AVAILABLE; // Yellow glow for available
                        }
                        
                        // Use cached glow image instead of per-frame RadialGradientPaint
                        BufferedImage glowImg = UITheme.getCachedGlow(glowColor);
                        int fixedGlowY = centerY - nodeRadius - spriteHeight - UIScale.px(110);
                        float glowRadius = Math.max(spriteWidth, spriteHeight) * 0.7f;
                        int glowDiameter = (int)(glowRadius * 2);
                        int glowDrawX = spriteX - (int)glowRadius;
                        int glowDrawY = fixedGlowY + spriteHeight / 2 - (int)glowRadius;
                        
                        Composite prevGlowComposite = g.getComposite();
                        g.setComposite(RenderCache.getAlpha(Math.min(1.0f, glowIntensity * alpha)));
                        g.drawImage(glowImg, glowDrawX, glowDrawY, glowDiameter, glowDiameter, null);
                        g.setComposite(prevGlowComposite);
                    }

                    // Set alpha - dim for locked levels

                    float spriteAlpha = isLocked ? 0.3f : 1.0f;

                    g.setComposite(RenderCache.getAlpha(spriteAlpha * alpha));

                    

                    // Enable smooth rendering for sprite

                    Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);

                    Object oldAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    if (Game.enableAntiAliasing) {

                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    }

                    

                    // Use pre-cached 180Â°-rotated+scaled sprite (eliminates per-frame bilinear rotation)

                    AffineTransform oldTransform = g.getTransform();

                    BufferedImage rotatedSprite = getCachedPlaneSprite(planeSprite, level, spriteWidth, spriteHeight);

                    g.translate(spriteX, spriteY + spriteHeight / 2);

                    

                    // Simulate Z-axis rotation by scaling width with sine wave (full 360)

                    double zRotation = Math.sin(time * 2 + level * 0.5);

                    double scaleX = zRotation; // Scale between -1.0 and 1.0 for full rotation

                    g.scale(scaleX, 1.0);

                    

                    g.drawImage(rotatedSprite, -spriteWidth / 2, -spriteHeight / 2, null);

                    

                    // Draw spinning rotors for helicopters

                    if (Boss.isHelicopterLevel(level)) {

                        BufferedImage rotorSprite = Boss.getRotorSpriteForLevel(level);

                        if (rotorSprite != null) {

                            int rotorWidth = (int)(rotorSprite.getWidth() * scale);

                            int rotorHeight = (int)(rotorSprite.getHeight() * scale);

                            

                            // Save current transform and composite

                            AffineTransform rotorTransform = g.getTransform();

                            Composite oldComposite = g.getComposite();

                            

                            // Make rotors slightly transparent

                            g.setComposite(RenderCache.getAlpha(0.2f));

                            

                            // Position rotor near top of helicopter (negative Y = upward from center)

                            int rotorOffsetY = -spriteHeight / 4; // Default offset upward toward rotor hub

                            // Per-level adjustments for helicopters with different body shapes

                            if (level == 16 || level == 24) {

                                rotorOffsetY = -spriteHeight / 6; // Blades sit a bit lower on these models

                            }

                            g.translate(0, rotorOffsetY); // Move to rotor center position

                            

                            // Rotate the rotor blades around its center (fast spin animation)

                            double rotorAngle = time * 15; // Fast spinning

                            g.rotate(rotorAngle);

                            

                            // Draw rotor centered at origin

                            g.drawImage(rotorSprite, -rotorWidth / 2, -rotorHeight / 2, rotorWidth, rotorHeight, null);

                            

                            g.setComposite(oldComposite);

                            g.setTransform(rotorTransform);

                        }

                    }

                    

                    g.setTransform(oldTransform);

                    

                    // Sparkle effects for completed levels

                    if (isCompleted && !isLocked && distFromCenter < 0.5) {

                        g.setComposite(RenderCache.getAlpha(alpha));

                        float sparkle = (float)(Math.sin(time * 7 + level) * 0.5 + 0.5);

                        g.setColor(Color.WHITE);

                        g.setComposite(RenderCache.getAlpha(sparkle * 0.784f * alpha));

                        int sparkleSize = 4;

                        // Star sparkles around the plane

                        for (int s = 0; s < 4; s++) {

                            double angle = time * 2 + s * Math.PI / 2;

                            int sx = spriteX + (int)(Math.cos(angle) * spriteWidth * 0.6);

                            int sy = spriteY + (int)(Math.sin(angle) * spriteHeight * 0.6);

                            g.fillOval(sx - sparkleSize / 2, sy - sparkleSize / 2, sparkleSize, sparkleSize);

                        }

                    }

                    

                    // Restore rendering hints and alpha

                    if (oldInterpolation != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);

                    if (oldAntialiasing != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);

                    g.setComposite(RenderCache.getAlpha(alpha));

                }

            } catch (Exception e) {

                // If sprite fails to load, skip it

            }

            

            // Level number - scale font with node size

            int fontSize = (int)(48 * scale);

            g.setFont(FontPalette.get(Font.BOLD, fontSize));

            String levelNum = String.valueOf(level);

            fm = g.getFontMetrics();

            int textX = x - fm.stringWidth(levelNum) / 2;

            int textY = centerY + fm.getAscent() / 2 - 2;

            

            g.setColor(RenderCache.BLACK_100);

            g.drawString(levelNum, textX + 1, textY + 1);

            

            if (isLocked) {

                g.setColor(NODE_LOCKED_TEXT);

            } else {

                g.setColor(Color.WHITE);

            }

            g.drawString(levelNum, textX, textY);

            

            // Mega boss star above node

            if (isMegaBoss && !isLocked) {

                int starSize = (int)(24 * scale);

                g.setFont(FontPalette.get(Font.BOLD, starSize));

                g.setColor(ColorPalette.ACCENT_YELLOW);

                String crown = "*";

                fm = g.getFontMetrics();

                g.drawString(crown, x - fm.stringWidth(crown) / 2, centerY - nodeRadius - UIScale.px(10));

            }

            

            // Checkmark badge for completed

            if (isCompleted) {

                int badgeSize = (int)(28 * scale);

                int badgeX = x + nodeRadius - badgeSize / 2;

                int badgeY = centerY - nodeRadius - badgeSize / 4;

                // Green circle background

                g.setColor(BADGE_GREEN);

                g.fillOval(badgeX - 2, badgeY - 2, badgeSize + 4, badgeSize + 4);

                // White border

                g.setColor(Color.WHITE);

                g.setStroke(RenderCache.getStroke(2));

                g.drawOval(badgeX - 2, badgeY - 2, badgeSize + 4, badgeSize + 4);

                // Checkmark symbol

                g.setFont(FontPalette.get(Font.BOLD, (int)(badgeSize * 0.8)));

                g.setColor(Color.WHITE);

                fm = g.getFontMetrics();

                String check = "\u2713";

                g.drawString(check, badgeX + (badgeSize - fm.stringWidth(check)) / 2, badgeY + badgeSize - fm.getDescent());

            }

            

            // Lock icon for locked

            if (isLocked) {

                int lockSize = (int)(18 * scale);

                g.setFont(FontPalette.get(Font.BOLD, lockSize));

                g.setColor(NODE_LOCKED_ICON);

                String lock = "[L]";

                fm = g.getFontMetrics();

                g.drawString(lock, x - fm.stringWidth(lock) / 2, centerY + nodeRadius + lockSize + UIScale.px(5));

            }

            

            // "CLEARED" label below completed nodes

            if (isCompleted) {

                int labelSize = (int)(14 * scale);

                g.setFont(FontPalette.get(Font.BOLD, labelSize));

                g.setColor(new Color(220, 190, 60, (int)(220 * alpha)));

                String cleared = "CLEARED";

                fm = g.getFontMetrics();

                g.drawString(cleared, x - fm.stringWidth(cleared) / 2, centerY + nodeRadius + labelSize + UIScale.px(5));

            }

            

            g.setComposite(RenderCache.getAlpha(1.0f));

        }

        

        // Draw info panel for selected level at bottom

        drawLevelInfoPanel(g, width, height, selectedLevel, currentLevel, time);

    }
    
    /**
     * Draw the endless mode slot in the journey map carousel.
     * Features: grayed-out rotating plane sprites with "?" overlay, orbiting "?" particles.
     */
    private void drawEndlessSlot(Graphics2D g, int x, int centerY, int nodeRadius, float alpha, double scale,
                                  boolean isSelected, double distFromCenter, double time,
                                  int width, int height, boolean planeTakeoffAnimation, double planeTakeoffTimer) {
        g.setComposite(RenderCache.getAlpha(alpha));
        
        // Selection glow - purple for endless
        if (isSelected && distFromCenter < 0.3) {
            float glowPulse = (float)(0.3 + 0.2 * Math.sin(time * 4));
            g.setComposite(RenderCache.getAlpha(glowPulse * alpha));
            g.setColor(ENDLESS_GLOW);
            g.fillOval(x - nodeRadius - UIScale.px(25), centerY - nodeRadius - UIScale.px(25),
                       (nodeRadius + UIScale.px(25)) * 2, (nodeRadius + UIScale.px(25)) * 2);
            g.setComposite(RenderCache.getAlpha(alpha));
        }
        
        // Node shadow
        g.setColor(NODE_SHADOW);
        g.fillOval(x - nodeRadius + UIScale.px(5), centerY - nodeRadius + UIScale.px(5),
                   nodeRadius * 2, nodeRadius * 2);
        
        // Node fill - dark purple
        g.setColor(ENDLESS_NODE_BG);
        g.fillOval(x - nodeRadius, centerY - nodeRadius, nodeRadius * 2, nodeRadius * 2);
        
        // Pulsing border
        float borderPulse = (float)(0.5 + 0.5 * Math.sin(time * 2));
        g.setColor(new Color(ENDLESS_NODE_BORDER.getRed(), ENDLESS_NODE_BORDER.getGreen(),
                             ENDLESS_NODE_BORDER.getBlue(), (int)(150 + 105 * borderPulse)));
        g.setStroke(isSelected && distFromCenter < 0.3 ? RenderCache.getStroke(5) : RenderCache.getStroke(3));
        g.drawOval(x - nodeRadius, centerY - nodeRadius, nodeRadius * 2, nodeRadius * 2);
        
        // Draw grayed-out plane sprite with "?" - cycles randomly on Z-rotation thinnest point
        try {
            // Pick a plane sprite based on time - switches at the thinnest point of rotation
            double zRotation = Math.sin(time * 2 + 29 * 0.5);
            // Switch sprite at zero-crossing of Z-rotation (when plane is edge-on)
            int halfCycle = (int)((time * 2 + 29 * 0.5) / Math.PI);
            int spriteIndex = (Math.abs(halfCycle) % 28) + 1; // Cycle through 1-28
            BufferedImage planeSprite = Boss.getSpriteForLevel(spriteIndex);
            
            if (planeSprite != null) {
                int spriteWidth = (int)(planeSprite.getWidth() * scale);
                int spriteHeight = (int)(planeSprite.getHeight() * scale);
                
                // Bounce animation
                float bounceOffset = 0;
                if (isSelected && distFromCenter < 0.3 && !planeTakeoffAnimation) {
                    bounceOffset = (float)(Math.sin(time * 5) * 8);
                }
                
                // Takeoff animation
                int takeoffOffset = 0;
                if (planeTakeoffAnimation && isSelected && distFromCenter < 0.3) {
                    float progress = (float)(planeTakeoffTimer / 60.0);
                    float easedProgress = progress * progress;
                    takeoffOffset = (int)(easedProgress * height * 1.5);
                }
                
                int spriteY = centerY - nodeRadius - spriteHeight - UIScale.px(110) - (int)bounceOffset - takeoffOffset;
                
                // Glow effect - purple for endless
                float glowIntensity = (float)(0.5 + 0.3 * Math.sin(time * 0.8));
                BufferedImage glowImg = UITheme.getCachedGlow(ENDLESS_GLOW);
                int fixedGlowY = centerY - nodeRadius - spriteHeight - UIScale.px(110);
                float glowRadius = Math.max(spriteWidth, spriteHeight) * 0.7f;
                int glowDiameter = (int)(glowRadius * 2);
                Composite prevGlowComp = g.getComposite();
                g.setComposite(RenderCache.getAlpha(Math.min(1.0f, glowIntensity * alpha)));
                g.drawImage(glowImg, x - (int)glowRadius, fixedGlowY + spriteHeight / 2 - (int)glowRadius,
                           glowDiameter, glowDiameter, null);
                g.setComposite(prevGlowComp);
                
                // Draw solid gray silhouette of the plane sprite
                Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (Game.enableAntiAliasing) {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                }
                
                // Create a gray-filled silhouette: draw sprite into temp image, then SRC_IN fill with gray
                BufferedImage rotatedSprite = getCachedPlaneSprite(planeSprite, spriteIndex, spriteWidth, spriteHeight);
                
                // Build outline silhouette (draw shifted copies, then SRC_IN fill)
                int pad = 4;
                BufferedImage outline = Game.createOptimalImage(spriteWidth + pad * 2, spriteHeight + pad * 2, true);
                Graphics2D og = outline.createGraphics();
                for (int dy = -pad; dy <= pad; dy++) {
                    for (int dx = -pad; dx <= pad; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        og.drawImage(rotatedSprite, pad + dx, pad + dy, null);
                    }
                }
                og.setComposite(AlphaComposite.SrcIn);
                og.setColor(new Color(80, 80, 100));
                og.fillRect(0, 0, spriteWidth + pad * 2, spriteHeight + pad * 2);
                og.dispose();
                
                BufferedImage silhouette = Game.createOptimalImage(spriteWidth, spriteHeight, true);
                Graphics2D sg = silhouette.createGraphics();
                sg.drawImage(rotatedSprite, 0, 0, null);
                sg.setComposite(AlphaComposite.SrcIn);
                sg.setColor(ENDLESS_GRAY_TINT);
                sg.fillRect(0, 0, spriteWidth, spriteHeight);
                sg.dispose();
                
                AffineTransform oldTransform = g.getTransform();
                g.translate(x, spriteY + spriteHeight / 2);
                
                // Z-axis rotation (same as campaign planes)
                double scaleX = zRotation;
                g.scale(scaleX, 1.0);
                
                // Draw outline behind, then silhouette on top
                g.setComposite(RenderCache.getAlpha(0.7f * alpha));
                g.drawImage(outline, -spriteWidth / 2 - pad, -spriteHeight / 2 - pad, null);
                g.setComposite(RenderCache.getAlpha(0.6f * alpha));
                g.drawImage(silhouette, -spriteWidth / 2, -spriteHeight / 2, null);
                
                g.setTransform(oldTransform);
                
                // Draw "?" over the plane sprite
                g.setComposite(RenderCache.getAlpha(0.9f * alpha));
                int qFontSize = (int)(60 * scale);
                g.setFont(FontPalette.get(Font.BOLD, qFontSize));
                g.setColor(ENDLESS_QUESTION_MARK);
                FontMetrics qfm = g.getFontMetrics();
                String qMark = "?";
                g.drawString(qMark, x - qfm.stringWidth(qMark) / 2,
                           spriteY + spriteHeight / 2 + qfm.getAscent() / 2);
                
                // Restore rendering hints
                if (oldInterp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
                if (oldAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
            }
        } catch (Exception e) {
            // Skip sprite if loading fails
        }
        
        // Orbiting "?" particles around the node
        g.setComposite(RenderCache.getAlpha(alpha));
        int numOrbitParticles = 5;
        for (int p = 0; p < numOrbitParticles; p++) {
            double orbitAngle = time * 1.5 + p * (2 * Math.PI / numOrbitParticles);
            double orbitRadius = nodeRadius + UIScale.px(20);
            int px = x + (int)(Math.cos(orbitAngle) * orbitRadius);
            int py = centerY + (int)(Math.sin(orbitAngle) * orbitRadius);
            
            // Smooth transparency cycling
            float particleAlpha = (float)(0.3 + 0.7 * Math.abs(Math.sin(time * 2 + p * 1.2)));
            g.setComposite(RenderCache.getAlpha(particleAlpha * alpha));
            g.setColor(ENDLESS_ORBIT_PARTICLE);
            int pSize = (int)(14 * scale);
            g.setFont(FontPalette.get(Font.BOLD, pSize));
            g.drawString("?", px - pSize / 4, py + pSize / 4);
        }
        
        g.setComposite(RenderCache.getAlpha(alpha));
        
        // "ENDLESS" text on the node instead of a number
        int fontSize = (int)(28 * scale);
        g.setFont(FontPalette.get(Font.BOLD, fontSize));
        g.setColor(ENDLESS_QUESTION_MARK);
        String endlessText = "\u221E"; // Infinity symbol
        FontMetrics fm = g.getFontMetrics();
        int textX = x - fm.stringWidth(endlessText) / 2;
        int textY = centerY + fm.getAscent() / 2 - 2;
        g.drawString(endlessText, textX, textY);
        
        // Prestige indicator below node
        int prestige = gameData.getEndlessPrestige();
        if (prestige > 0) {
            int labelSize = (int)(14 * scale);
            g.setFont(FontPalette.get(Font.BOLD, labelSize));
            g.setColor(new Color(200, 160, 255, (int)(220 * alpha)));
            String prestigeText = "PRESTIGE " + prestige;
            fm = g.getFontMetrics();
            g.drawString(prestigeText, x - fm.stringWidth(prestigeText) / 2,
                        centerY + nodeRadius + labelSize + UIScale.px(5));
        }
    }

    

    private void drawLevelInfoPanel(Graphics2D g, int width, int height, int selectedLevel, int currentLevel, double time) {

        int panelHeight = UIScale.px(200);

        int panelY = height - panelHeight - UIScale.px(30);

        int panelWidth = UIScale.px(700);

        int panelX = (width - panelWidth) / 2;

        

        // Panel background with rounded corners

        g.setColor(PANEL_BG);

        g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, UIScale.px(25), UIScale.px(25));
        
        // === ENDLESS MODE INFO PANEL ===
        if (selectedLevel == 29) {
            // Purple border
            g.setColor(ENDLESS_NODE_BORDER);
            g.setStroke(RenderCache.getStroke(3));
            g.drawRoundRect(panelX, panelY, panelWidth, panelHeight, UIScale.px(25), UIScale.px(25));
            
            // Title
            g.setFont(FONT_LARGE_32);
            g.setColor(ENDLESS_QUESTION_MARK);
            String title = "ENDLESS MODE";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, panelX + (panelWidth - fm.stringWidth(title)) / 2, panelY + UIScale.px(45));
            
            // Description
            g.setFont(FONT_EXTRA_SMALL_16);
            g.setColor(PANEL_DIM_LABEL);
            String desc = "Fight through all 28 bosses in an endless loop with increasing difficulty";
            fm = g.getFontMetrics();
            g.drawString(desc, panelX + (panelWidth - fm.stringWidth(desc)) / 2, panelY + UIScale.px(70));
            
            // Stats
            int infoY = panelY + UIScale.px(100);
            g.setFont(FONT_EXTRA_SMALL_16);
            
            int prestige = gameData.getEndlessPrestige();
            int endlessLevel = gameData.getEndlessCurrentLevel();
            int highestLevel = gameData.getEndlessHighestLevel();
            
            if (prestige > 0 || endlessLevel > 1) {
                g.setColor(new Color(200, 160, 255));
                String progressText = "Prestige: " + prestige + "  |  Endless Level: " + endlessLevel + "/28  |  Highest: " + highestLevel;
                fm = g.getFontMetrics();
                g.drawString(progressText, panelX + (panelWidth - fm.stringWidth(progressText)) / 2, infoY);
                infoY += UIScale.px(25);
            }
            
            // Start prompt
            float pulse = (float)(0.7 + 0.3 * Math.sin(time * 5));
            g.setColor(new Color((int)(100 * pulse + 60), (int)(80 * pulse + 60), (int)(200 * pulse + 55)));
            drawPromptWithIcons(g, panelX + panelWidth / 2, infoY,
                "> PRESS ", KeyBindManager.Action.CONFIRM, " TO ENTER ENDLESS <");
            
            // Navigation hints
            g.setFont(FontPalette.get(Font.PLAIN, 14));
            g.setColor(PANEL_NAV_HINT);
            drawPromptWithIcons(g, panelX + panelWidth / 2, panelY + panelHeight - UIScale.px(15),
                "", KeyBindManager.Action.MOVE_LEFT, " ", KeyBindManager.Action.MOVE_RIGHT, " or CLICK  Navigate    ", KeyBindManager.Action.CONFIRM, " or CLICK  Start    ", KeyBindManager.Action.BACK, "  Back");
            return;
        }

        

        // Border glow based on status

        boolean isCompleted = selectedLevel < currentLevel;

        boolean isCurrent = selectedLevel == currentLevel;

        boolean isMegaBoss = selectedLevel % 3 == 0;

        

        Color borderColor = isCompleted ? PANEL_BORDER_COMPLETED :

                           isCurrent ? PANEL_BORDER_CURRENT :

                           PANEL_BORDER_LOCKED;

        g.setColor(borderColor);

        g.setStroke(RenderCache.getStroke(3));

        g.drawRoundRect(panelX, panelY, panelWidth, panelHeight, UIScale.px(25), UIScale.px(25));

        

        // Boss name - centered

        String bossName = GameData.getBossName(selectedLevel);

        g.setFont(FONT_LARGE_32);

        FontMetrics fm = g.getFontMetrics();

        int nameX = panelX + (panelWidth - fm.stringWidth(bossName)) / 2;

        

        if (isMegaBoss) {

            GradientPaint nameGrad = new GradientPaint(nameX, panelY + UIScale.px(40), GLOW_MEGA_BOSS, 

                                                        nameX + fm.stringWidth(bossName), panelY + UIScale.px(40), PANEL_MEGA_LABEL);

            g.setPaint(nameGrad);

        } else {

            g.setColor(PANEL_TEXT_NAME);

        }

        g.drawString(bossName, nameX, panelY + UIScale.px(45));

        

        // Level type label - centered

        g.setFont(FONT_EXTRA_SMALL_16);

        g.setColor(isMegaBoss ? PANEL_MEGA_LABEL : PANEL_DIM_LABEL);

        String typeLabel = isMegaBoss ? "* MEGA BOSS - Level " + selectedLevel : "Level " + selectedLevel;

        fm = g.getFontMetrics();

        g.drawString(typeLabel, panelX + (panelWidth - fm.stringWidth(typeLabel)) / 2, panelY + UIScale.px(70));

        

        // Status and stats info

        g.setFont(FONT_EXTRA_SMALL_16);

        int infoY = panelY + UIScale.px(100);

        

        if (isCompleted) {

            g.setColor(NODE_CLEARED_LABEL);

            String status = "\u2713 DEFEATED";

            fm = g.getFontMetrics();

            

            // Show best time if available

            int bestTime = gameData.getLevelCompletionTime(selectedLevel);

            if (bestTime > 0) {

                int seconds = bestTime / 60;

                int frames = bestTime % 60;

                String timeStr = String.format("  -  Best: %d.%02ds", seconds, frames * 100 / 60);

                status += timeStr;

            }

            g.drawString(status, panelX + (panelWidth - fm.stringWidth(status)) / 2, infoY);

            

            // Show level stats if available

            LevelStats stats = gameData.getLevelStats(selectedLevel);

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(PANEL_STATS_TEXT);

            infoY += UIScale.px(20);

            

            // First line: Dodges and Perfect

            StringBuilder line1 = new StringBuilder();

            if (stats.getDodges() > 0) {

                line1.append("Dodges: ").append(stats.getDodges());

            }

            if (stats.getPerfectDodges() > 0) {

                if (line1.length() > 0) line1.append("  -  ");

                line1.append("Perfect: ").append(stats.getPerfectDodges());

            }

            if (line1.length() > 0) {

                fm = g.getFontMetrics();

                g.drawString(line1.toString(), panelX + (panelWidth - fm.stringWidth(line1.toString())) / 2, infoY);

                infoY += UIScale.px(18);

            }

            

            // Second line: Near Misses and Max Combo

            StringBuilder line2 = new StringBuilder();

            if (stats.getNearMisses() > 0) {

                line2.append("Near Misses: ").append(stats.getNearMisses());

            }

            if (stats.getMaxCombo() > 0) {

                if (line2.length() > 0) line2.append("  -  ");

                line2.append("Max Combo: ").append(stats.getMaxCombo()).append("x");

            }

            if (line2.length() > 0) {

                fm = g.getFontMetrics();

                g.drawString(line2.toString(), panelX + (panelWidth - fm.stringWidth(line2.toString())) / 2, infoY);

                infoY += UIScale.px(18);

            }

            

            // Third line: Bullets and Risk %

            StringBuilder line3 = new StringBuilder();

            if (stats.getBulletsSpawned() > 0) {

                line3.append("Bullets: ").append(stats.getBulletsSpawned());

            }

            int riskPercent = stats.getRiskPercentage();

            if (riskPercent > 0) {

                if (line3.length() > 0) line3.append("  -  ");

                line3.append("Risk: ").append(riskPercent).append("%");

            }

            if (line3.length() > 0) {

                fm = g.getFontMetrics();

                g.drawString(line3.toString(), panelX + (panelWidth - fm.stringWidth(line3.toString())) / 2, infoY);

                infoY += UIScale.px(18);

            }

            

            // Fourth line: Damage and Lives

            StringBuilder line4 = new StringBuilder();

            if (stats.getDamageTaken() > 0) {

                line4.append("Damage: ").append(stats.getDamageTaken());

            }

            if (stats.getMissilesUsed() > 0) {

                if (line4.length() > 0) line4.append("  -  ");

                line4.append("Missiles: ").append(stats.getMissilesUsed());

            }

            if (line4.length() > 0) {

                fm = g.getFontMetrics();

                g.drawString(line4.toString(), panelX + (panelWidth - fm.stringWidth(line4.toString())) / 2, infoY);

            }

        } else if (isCurrent) {

            // Animated "READY" text

            float pulse = (float)(0.7 + 0.3 * Math.sin(time * 5));

            g.setColor(new Color((int)(100 * pulse + 100), (int)(200 * pulse + 55), (int)(100 * pulse + 100)));

            drawPromptWithIcons(g, panelX + panelWidth / 2, infoY,

                "> PRESS ", KeyBindManager.Action.CONFIRM, " TO START <");

        } else {

            g.setColor(PANEL_LOCK_TEXT);

            String lockText = "[L] LOCKED";

            fm = g.getFontMetrics();

            g.drawString(lockText, panelX + (panelWidth - fm.stringWidth(lockText)) / 2, infoY);

        }

        

        // Navigation hints at very bottom

        g.setFont(FontPalette.get(Font.PLAIN, 14));

        g.setColor(PANEL_NAV_HINT);

        drawPromptWithIcons(g, panelX + panelWidth / 2, panelY + panelHeight - UIScale.px(15),

            "", KeyBindManager.Action.MOVE_LEFT, " ", KeyBindManager.Action.MOVE_RIGHT, " or CLICK  Navigate    ", KeyBindManager.Action.CONFIRM, " or CLICK  Start    ", KeyBindManager.Action.BACK, "  Back");

    }

    

    public void drawRiskContract(Graphics2D g, int width, int height, int selectedContract, 

                                  String[] contractNames, String[] contractDescriptions, 

                                  double[] contractMultipliers, double time, int level) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "RISK CONTRACT", width, UIScale.px(80), ColorPalette.ACCENT_RED_BRIGHT, ColorPalette.ACCENT_ORANGE, time, FontPalette.getDisplay(Font.BOLD, 26));

        

        // Subtitle

        g.setFont(FONT_SMALL);

        String subtitle = "Choose your challenge modifier for Level " + level;

        FontMetrics subFm = g.getFontMetrics();

        g.setColor(ColorPalette.TEXT_DIM);

        g.drawString(subtitle, (width - subFm.stringWidth(subtitle)) / 2, UIScale.px(120));

        

        // Draw contract cards (larger and centered)

        int cardWidth = UIScale.px(280);

        int cardHeight = UIScale.px(380);

        int cardSpacing = UIScale.px(40);

        int totalWidth = contractNames.length * cardWidth + (contractNames.length - 1) * cardSpacing;

        int startX = (width - totalWidth) / 2;

        int cardY = (height - cardHeight) / 2 - UIScale.px(40);

        

        for (int i = 0; i < contractNames.length; i++) {

            int cardX = startX + i * (cardWidth + cardSpacing);

            boolean isSelected = (i == selectedContract);

            

            // Card selection animation

            double cardScale = isSelected ? 1.05 + 0.02 * Math.sin(time * 4) : 1.0;

            int scaledWidth = (int)(cardWidth * cardScale);

            int scaledHeight = (int)(cardHeight * cardScale);

            int offsetX = (cardWidth - scaledWidth) / 2;

            int offsetY = (cardHeight - scaledHeight) / 2;

            

            // Card shadow

            g.setColor(RenderCache.BLACK_100);

            g.fillRoundRect(cardX + offsetX + UIScale.px(5), cardY + offsetY + UIScale.px(5), scaledWidth, scaledHeight, UIScale.px(15), UIScale.px(15));

            

            // Card background

            if (isSelected) {

                // Selected card has colored gradient

                Color topColor = i == 0 ? new Color(60, 100, 60) : 

                                i == 1 ? new Color(120, 50, 50) :

                                i == 2 ? new Color(50, 80, 120) : new Color(100, 80, 50);

                Color bottomColor = i == 0 ? new Color(40, 70, 40) :

                                   i == 1 ? new Color(80, 30, 30) :

                                   i == 2 ? new Color(30, 50, 80) : new Color(70, 50, 30);

                GradientPaint gradient = new GradientPaint(

                    cardX + offsetX, cardY + offsetY, topColor,

                    cardX + offsetX, cardY + offsetY + scaledHeight, bottomColor);

                g.setPaint(gradient);

            } else {

                g.setColor(RenderCache.DARK_40_45_55);

            }

            g.fillRoundRect(cardX + offsetX, cardY + offsetY, scaledWidth, scaledHeight, 15, 15);

            

            // Card border

            g.setColor(isSelected ? GLOW_AVAILABLE : new Color(80, 85, 95));

            g.setStroke(RenderCache.getStroke(isSelected ? 3f : 2f));

            g.drawRoundRect(cardX + offsetX, cardY + offsetY, scaledWidth, scaledHeight, 15, 15);

            

            // Contract icon/symbol - draw custom graphics (larger)

            int iconY = cardY + offsetY + UIScale.px(65);

            int iconCenterX = cardX + offsetX + scaledWidth / 2;

            Color iconColor = i == 0 ? new Color(100, 180, 100) :

                             i == 1 ? RenderCache.RED_255_100_100 :

                             i == 2 ? new Color(100, 150, 255) : RenderCache.WARM_255_180_100;

            g.setColor(isSelected ? iconColor : RenderCache.GRAY_100);

            g.setStroke(RenderCache.getStroke(4));

            

            if (i == 0) {

                // No Contract - Circle with checkmark (larger)

                g.drawOval(iconCenterX - 35, iconY - 50, 70, 70);

                g.setStroke(RenderCache.getStroke(5));

                g.drawLine(iconCenterX - 15, iconY - 15, iconCenterX, iconY);

                g.drawLine(iconCenterX, iconY, iconCenterX + 20, iconY - 28);

            } else if (i == 1) {

                // Bullet Storm - Multiple circles (larger)

                g.fillOval(iconCenterX - 28, iconY - 35, 20, 20);

                g.fillOval(iconCenterX + 8, iconY - 35, 20, 20);

                g.fillOval(iconCenterX - 10, iconY - 8, 20, 20);

            } else if (i == 2) {

                // Speed Demon - Forward arrows (larger)

                int[] xPoints1 = {iconCenterX - 28, iconCenterX - 14, iconCenterX - 28};

                int[] yPoints1 = {iconY - 35, iconY - 15, iconY + 5};

                g.fillPolygon(xPoints1, yPoints1, 3);

                int[] xPoints2 = {iconCenterX + 8, iconCenterX + 22, iconCenterX + 8};

                int[] yPoints2 = {iconY - 35, iconY - 15, iconY + 5};

                g.fillPolygon(xPoints2, yPoints2, 3);

            } else {

                // Powerless - Shield with X (larger)

                g.drawArc(iconCenterX - 28, iconY - 42, 56, 63, 0, 180);

                g.drawLine(iconCenterX - 28, iconY - 42, iconCenterX - 28, iconY + 7);

                g.drawLine(iconCenterX + 28, iconY - 42, iconCenterX + 28, iconY + 7);

                g.drawLine(iconCenterX - 28, iconY + 7, iconCenterX, iconY + 21);

                g.drawLine(iconCenterX + 28, iconY + 7, iconCenterX, iconY + 21);

                g.setStroke(RenderCache.getStroke(4));

                g.drawLine(iconCenterX - 21, iconY - 28, iconCenterX + 21, iconY + 14);

                g.drawLine(iconCenterX + 21, iconY - 28, iconCenterX - 21, iconY + 14);

            }

            g.setStroke(RenderCache.getStroke(1));

            

            // Contract name

            g.setFont(FontPalette.get(Font.BOLD, 17));

            FontMetrics nameFm = g.getFontMetrics();

            g.setColor(isSelected ? Color.WHITE : RenderCache.GRAY_150);

            g.drawString(contractNames[i], cardX + offsetX + (scaledWidth - nameFm.stringWidth(contractNames[i])) / 2, 

                        cardY + offsetY + UIScale.px(120));

            

            // Multiplier

            g.setFont(FontPalette.get(Font.BOLD, 24));

            String multiplier = i == 0 ? "--" : String.format("%.2fx", contractMultipliers[i]);

            FontMetrics multFm = g.getFontMetrics();

            g.setColor(i == 0 ? RenderCache.GRAY_150 : ColorPalette.ACCENT_YELLOW);

            g.drawString(multiplier, cardX + offsetX + (scaledWidth - multFm.stringWidth(multiplier)) / 2, 

                        cardY + offsetY + UIScale.px(155));

            

            // Description (word wrapped)

            g.setFont(FONT_EXTRA_SMALL_16);

            g.setColor(isSelected ? RenderCache.GRAY_200 : RenderCache.GRAY_120);

            String desc = contractDescriptions[i];

            int descY = cardY + offsetY + UIScale.px(210);

            int maxLineWidth = scaledWidth - UIScale.px(30);

            

            // Simple word wrapping

            String[] words = desc.split(" ");

            StringBuilder line = new StringBuilder();

            int lineY = descY;

            for (String word : words) {

                String testLine = line.isEmpty() ? word : line + " " + word;

                FontMetrics descFm = g.getFontMetrics();

                if (descFm.stringWidth(testLine) > maxLineWidth) {

                    g.drawString(line.toString(), cardX + offsetX + UIScale.px(15), lineY);

                    line = new StringBuilder(word);

                    lineY += UIScale.px(22);

                } else {

                    line = new StringBuilder(testLine);

                }

            }

            if (!line.isEmpty()) {

                g.drawString(line.toString(), cardX + offsetX + UIScale.px(15), lineY);

            }

        }

        

        // Controls hint

        g.setFont(FONT_INFO);

        g.setColor(RenderCache.GRAY_150);

        drawPromptWithIcons(g, width / 2, height - UIScale.px(40),

            "", KeyBindManager.Action.MOVE_LEFT, " ", KeyBindManager.Action.MOVE_RIGHT, " or CLICK  Select   |   ", KeyBindManager.Action.CONFIRM, " or CLICK  Confirm   |   ", KeyBindManager.Action.BACK, "  Back");

        

        // Warning for risky contracts

        if (selectedContract > 0) {

            g.setFont(FONT_EXTRA_SMALL_16);

            g.setColor(new Color(255, 100, 100, (int)(200 + 55 * Math.sin(time * 3))));

            String warning = "!! Higher risk = Higher reward !!";

            FontMetrics warnFm = g.getFontMetrics();

            g.drawString(warning, (width - warnFm.stringWidth(warning)) / 2, height - UIScale.px(70));

        }

    }

    

    public void drawLevelConfirm(Graphics2D g, int width, int height, int level, int selectedConfirmItem, boolean isResume, double time, boolean planeTakeoffAnimation, double planeTakeoffTimer, double scrollOffset, boolean hasSavedGame, int savedLevel) {

        // If plane takeoff animation is active, show the level select screen with flying plane

        if (planeTakeoffAnimation) {

            // Draw level select screen in background

            drawLevelSelect(g, width, height, level, level, time, scrollOffset, hasSavedGame, savedLevel, true, planeTakeoffTimer);

            

            // Fade to white as plane flies up

            float fadeProgress = (float)(planeTakeoffTimer / 60.0);

            g.setColor(new Color(255, 255, 255, (int)(255 * fadeProgress * fadeProgress)));

            g.fillRect(0, 0, width, height);

            return;

        }

        

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        String title;

        if (gameData != null && gameData.isInEndlessMode()) {

            title = (isResume ? "RESUME" : "START") + " ENDLESS MODE?";

        } else {

            title = isResume ? "RESUME LEVEL " + level + "?" : "START LEVEL " + level + "?";

        }

        UITheme.drawTitle(g, title, width, height / 2 - UIScale.px(50), ColorPalette.TEXT_GOLD, ColorPalette.ACCENT_ORANGE, time, FontPalette.get(Font.BOLD, 56));

        

        // Endless mode subtitle (Prestige # - Level #)

        if (gameData != null && gameData.isInEndlessMode()) {

            String subText = "Prestige " + gameData.getEndlessPrestige() + " \u2022 Level " + gameData.getEndlessCurrentLevel();

            g.setFont(FontPalette.get(Font.BOLD, 28));

            FontMetrics sfm = g.getFontMetrics();

            int subX = (width - sfm.stringWidth(subText)) / 2;

            int subY = height / 2 - UIScale.px(50) + 38;

            g.setColor(new Color(0, 0, 0, 150));

            g.drawString(subText, subX + 2, subY + 2);

            g.setColor(new Color(180, 130, 255));

            g.drawString(subText, subX, subY);

        }

        

        // Yes and No buttons

        int buttonWidth = UIScale.px(150);

        int buttonHeight = UIScale.px(60);

        int buttonSpacing = UIScale.px(50);

        int totalWidth = 2 * buttonWidth + buttonSpacing;

        int startX = (width - totalWidth) / 2;

        int buttonY = height / 2 + UIScale.px(50);

        

        // Draw Yes button

        boolean yesSelected = (selectedConfirmItem == 0);

        Color yesColor = yesSelected ? ColorPalette.SUCCESS_GREEN : new Color(80, 90, 70);

        Color yesHover = new Color(180, 210, 160);

        

        // Button shadow

        g.setColor(RenderCache.BLACK_100);

        g.fillRoundRect(startX + 3, buttonY + 3, buttonWidth, buttonHeight, UIScale.px(10), UIScale.px(10));

        

        // Button background

        if (yesSelected) {

            double pulse = 1.02 + 0.02 * Math.sin(time * 4);

            int pulsedWidth = (int)(buttonWidth * pulse);

            int pulsedHeight = (int)(buttonHeight * pulse);

            int offsetX = (buttonWidth - pulsedWidth) / 2;

            int offsetY = (buttonHeight - pulsedHeight) / 2;

            g.setColor(yesHover);

            g.fillRoundRect(startX + offsetX, buttonY + offsetY, pulsedWidth, pulsedHeight, UIScale.px(10), UIScale.px(10));

        } else {

            g.setColor(yesColor);

            g.fillRoundRect(startX, buttonY, buttonWidth, buttonHeight, UIScale.px(10), UIScale.px(10));

        }

        

        // Button border

        g.setColor(yesSelected ? new Color(200, 230, 180) : new Color(120, 130, 110));

        g.setStroke(RenderCache.getStroke(2));

        g.drawRoundRect(startX, buttonY, buttonWidth, buttonHeight, UIScale.px(10), UIScale.px(10));

        

        // Button text

        g.setFont(FONT_LARGE_32);

        FontMetrics yesFm = g.getFontMetrics();

        g.setColor(Color.WHITE);

        String yesText = "YES";

        g.drawString(yesText, startX + (buttonWidth - yesFm.stringWidth(yesText)) / 2, 

                     buttonY + (buttonHeight + yesFm.getAscent()) / 2 - 2);

        

        // Draw No button

        int noButtonX = startX + buttonWidth + buttonSpacing;

        boolean noSelected = (selectedConfirmItem == 1);

        Color noColor = noSelected ? ColorPalette.ACCENT_RED : new Color(90, 50, 60);

        Color noHover = new Color(220, 120, 130);

        

        // Button shadow

        g.setColor(RenderCache.BLACK_100);

        g.fillRoundRect(noButtonX + 3, buttonY + 3, buttonWidth, buttonHeight, UIScale.px(10), UIScale.px(10));

        

        // Button background

        if (noSelected) {

            double pulse = 1.02 + 0.02 * Math.sin(time * 4);

            int pulsedWidth = (int)(buttonWidth * pulse);

            int pulsedHeight = (int)(buttonHeight * pulse);

            int offsetX = (buttonWidth - pulsedWidth) / 2;

            int offsetY = (buttonHeight - pulsedHeight) / 2;

            g.setColor(noHover);

            g.fillRoundRect(noButtonX + offsetX, buttonY + offsetY, pulsedWidth, pulsedHeight, UIScale.px(10), UIScale.px(10));

        } else {

            g.setColor(noColor);

            g.fillRoundRect(noButtonX, buttonY, buttonWidth, buttonHeight, UIScale.px(10), UIScale.px(10));

        }

        

        // Button border

        g.setColor(noSelected ? new Color(230, 130, 140) : new Color(120, 70, 80));

        g.setStroke(RenderCache.getStroke(2));

        g.drawRoundRect(noButtonX, buttonY, buttonWidth, buttonHeight, UIScale.px(10), UIScale.px(10));

        

        // Button text

        g.setFont(FONT_LARGE_32);

        FontMetrics noFm = g.getFontMetrics();

        g.setColor(Color.WHITE);

        String noText = "NO";

        g.drawString(noText, noButtonX + (buttonWidth - noFm.stringWidth(noText)) / 2, 

                     buttonY + (buttonHeight + noFm.getAscent()) / 2 - 2);

        

        // Controls hint

        g.setFont(FONT_INFO);

        g.setColor(RenderCache.GRAY_150);

        drawPromptWithIcons(g, width / 2, height - UIScale.px(40),

            "", KeyBindManager.Action.MOVE_LEFT, " ", KeyBindManager.Action.MOVE_RIGHT, " or CLICK  Select   |   ", KeyBindManager.Action.CONFIRM, " or CLICK  Confirm   |   ", KeyBindManager.Action.BACK, "  Back");

    }

    

    public void drawGame(Graphics2D g, int width, int height, Player player, Boss boss, List<Bullet> bullets, List<Particle> particles, List<BeamAttack> beamAttacks, int level, double time, boolean bossVulnerable, double invulnerabilityTimer, int dodgeCombo, boolean showCombo, boolean bossDeathAnimation, double bossDeathScale, double bossDeathRotation, double gameTime, int fps, boolean shieldActive, boolean playerInvincible, int bossHitCount, double cameraX, double cameraY, boolean introPanActive, double bossFlashTimer, double screenFlashTimer, ComboSystem comboSystem, List<DamageNumber> damageNumbers, boolean bossIntroActive, String bossIntroText, double bossIntroTimer, boolean isPaused, int selectedPauseItem, List<Achievement> pendingAchievements, double achievementNotificationTimer, boolean deathSequenceActive, boolean playerHidden, int respawnBlinkTimer, int riskContractType, boolean riskContractActive, double stoppedMovingTimer, boolean unpauseCountdownActive, double unpauseCountdownTimer, double itemReadyFlickerTimer, double itemCompleteFlashTimer, double achievementFlashTimer, double bossIntroFlashTimer, double countdownFlashTimer, double bossHitFlashTimer, double typePurgeFlashTimer, Color typePurgeFlashColor, java.util.List<double[]> moneyCircles, double moneyCircleRadius, double frostBeamAngle, double frostBeamProgress, double frostBeamStopDistance, boolean frostBeamRetracting, double frostBeamRetractPhase, int shieldHits, double shieldOrbitAngle, double bossIntroPlayerX, double bossIntroBossX, double bossIntroVsScale, double bossIntroFlash, int bossIntroPhase, List<Particle> introParticles, double deathFlashTimer, List<Flare> flares, int levelWorldW, int levelWorldH) {

        // Draw background based on mode setting

        if (Game.backgroundMode == 0) {

            // Gradient mode

            int palIdx = getLevelGradientPaletteIndex(level);

            drawAnimatedGradient(g, width, height, time, palIdx);

        } else if (Game.backgroundMode == 1 && backgroundsLoaded) {

            // Parallax mode â€” use async pre-rendered buffer if available

            waitForBackground();

            if (!blitBackground(g, level)) {

                drawParallaxBackground(g, width, height, level, time);

            }

        } else if (Game.backgroundMode == 2 && backgroundsLoaded) {

            // Static image mode (first layer only)

            drawStaticBackground(g, width, height, level);

        } else {

            // Fallback to gradient if images not loaded

            int palIdx = getLevelGradientPaletteIndex(level);

            drawAnimatedGradient(g, width, height, time, palIdx);

        }

        

        // Apply chromatic aberration effect before drawing game objects

        if (Game.enableChromaticAberration) {

            applyChromaticAberration(g, width, height);

        }

        

        // Save the original transform and apply camera offset to all game objects

        AffineTransform originalTransform = g.getTransform();

        

        // Add subtle camera breathing effect (gentle sine wave movement)

        double breathX = Math.sin(time * 0.5) * 1.5;

        double breathY = Math.cos(time * 0.3) * 1.0;

        g.translate(-cameraX + breathX, -cameraY + breathY);

        

        // Draw money circles (Pool of Loot) - UNDER all sprites but after background

        // Use Area to combine overlapping circles into one unified shape

        if (!moneyCircles.isEmpty()) {

            java.awt.geom.Area combinedArea = new java.awt.geom.Area();

            

            for (double[] circle : moneyCircles) {

                double drawX = circle[0];

                double drawY = circle[1];

                double radius = moneyCircleRadius;

                

                // Add this circle to the combined area

                java.awt.geom.Ellipse2D.Double ellipse = new java.awt.geom.Ellipse2D.Double(

                    drawX - radius, drawY - radius, radius * 2, radius * 2);

                combinedArea.add(new java.awt.geom.Area(ellipse));

            }

            

            Composite _mc = g.getComposite();

            

            // Draw combined transparent green fill

            g.setComposite(RenderCache.getAlpha(0.2f));

            g.setColor(RenderCache.GREEN_50_200_80); // Green

            g.fill(combinedArea);

            

            // Draw combined outer ring

            g.setComposite(RenderCache.getAlpha(0.6f));

            g.setColor(RenderCache.GREEN_50_200_80); // Green

            g.setStroke(RenderCache.getStroke(2));

            g.draw(combinedArea);

            

            // Find connected groups of circles (overlapping = centers within 2*radius)

            // Each group gets its own $ sign at its center of mass

            boolean[] visited = new boolean[moneyCircles.size()];

            g.setFont(FontPalette.get(Font.BOLD, 36));

            g.setComposite(RenderCache.getAlpha(0.5f));

            g.setColor(RenderCache.GREEN_50_200_80); // Green

            FontMetrics fm = g.getFontMetrics();

            String symbol = "$";

            

            for (int i = 0; i < moneyCircles.size(); i++) {

                if (visited[i]) continue;

                // BFS/flood-fill to find all circles connected to circle i

                java.util.List<Integer> group = new java.util.ArrayList<>();

                java.util.Queue<Integer> queue = new java.util.LinkedList<>();

                queue.add(i);

                visited[i] = true;

                while (!queue.isEmpty()) {

                    int cur = queue.poll();

                    group.add(cur);

                    double[] c1 = moneyCircles.get(cur);

                    for (int j = 0; j < moneyCircles.size(); j++) {

                        if (visited[j]) continue;

                        double[] c2 = moneyCircles.get(j);

                        double dx = c1[0] - c2[0];

                        double dy = c1[1] - c2[1];

                        if (Math.sqrt(dx * dx + dy * dy) < moneyCircleRadius * 2) {

                            visited[j] = true;

                            queue.add(j);

                        }

                    }

                }

                // Draw $ at center of mass of this group

                double gx = 0, gy = 0;

                for (int idx : group) {

                    gx += moneyCircles.get(idx)[0];

                    gy += moneyCircles.get(idx)[1];

                }

                gx /= group.size();

                gy /= group.size();

                g.drawString(symbol, (int)(gx - fm.stringWidth(symbol) / 2), (int)(gy + fm.getAscent() / 3));

            }

            

            g.setComposite(_mc);

        }

        

        // Draw beam attacks (behind everything else)

        for (int i = 0; i < beamAttacks.size(); i++) {

            BeamAttack beam = beamAttacks.get(i);

            if (beam != null) {

                beam.draw(g, width, height, cameraX, cameraY);

            }

        }

        
        // Draw spinning beam attack (rotating arms from boss center)
        if (boss != null && boss.isSpinningBeamActive()) {
            double bossX = boss.getX();
            double bossY = boss.getY();
            int sections = boss.getSpinningBeamSections();
            double beamAngle = boss.getSpinningBeamAngle();
            double beamWidth = boss.getSpinningBeamWidth();
            double warningTime = boss.getSpinningBeamWarningTimer();
            
            // Beam length: diagonal of the screen so arms always reach edges
            int beamLength = (int)(Math.sqrt(width * width + height * height) * 1.2);
            
            Composite savedComp = g.getComposite();
            AffineTransform savedTransform = g.getTransform();
            
            for (int s = 0; s < sections; s++) {
                double armAngle = beamAngle + (s * 2.0 * Math.PI / sections);
                
                g.setTransform(savedTransform);
                g.rotate(armAngle, bossX, bossY);
                
                int bx = (int)bossX;
                int by = (int)(bossY - beamWidth / 2);
                
                if (warningTime > 0) {
                    // Warning phase: blinking translucent beams
                    double progress = 1.0 - (warningTime / 180.0);
                    int r = (int)(100 + progress * 155); // green-ish to red
                    int gn = (int)(200 - progress * 150);
                    double blinkSpeed = 0.1 + progress * 0.4;
                    float alphaF = (float)(Math.abs(Math.sin(warningTime * blinkSpeed)) * 150 + 50) / 255f;
                    
                    g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF)));
                    g.setColor(new Color(r, gn, 50));
                    g.fillRect(bx, by, beamLength, (int)beamWidth);
                    
                    // Warning borders
                    g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF + 0.3f)));
                    g.setStroke(new BasicStroke(2f));
                    g.drawLine(bx, by, bx + beamLength, by);
                    g.drawLine(bx, by + (int)beamWidth, bx + beamLength, by + (int)beamWidth);
                    
                    // Warning "!" symbols
                    if (warningTime > 30) {
                        g.setFont(new Font("SansSerif", Font.BOLD, 20));
                        int textY = (int)(bossY + g.getFontMetrics().getHeight() / 3);
                        for (int wx = bx + 60; wx < bx + beamLength; wx += 120) {
                            g.drawString("!", wx, textY);
                        }
                    }
                } else {
                    // Active phase: full damage beam with glow
                    // Outer glow
                    g.setComposite(RenderCache.getAlpha(0.3f));
                    g.setColor(new Color(191, 97, 106));
                    g.fillRect(bx, by - 10, beamLength, (int)beamWidth + 20);
                    
                    // Main beam
                    g.setComposite(RenderCache.getAlpha(0.78f));
                    g.setColor(new Color(191, 97, 106));
                    g.fillRect(bx, by, beamLength, (int)beamWidth);
                    
                    // Inner core
                    g.setComposite(RenderCache.getAlpha(0.86f));
                    g.setColor(new Color(255, 150, 150));
                    g.fillRect(bx, by + (int)beamWidth / 4, beamLength, (int)beamWidth / 2);
                    
                    // Animated scanlines (horizontal stripe pattern scrolling along the beam)
                    g.setComposite(savedComp);
                    double spinTimer = boss.getSpinningBeamTimer();
                    int scanOffset = (int)((spinTimer * 10) % 8);
                    TexturePaint scanPaint = new TexturePaint(SPIN_BEAM_SCANLINE_TILE,
                        new Rectangle2D.Float(scanOffset, 0, 8, 1));
                    g.setPaint(scanPaint);
                    g.fillRect(bx, by, beamLength, (int)beamWidth);
                    
                    // Edge borders
                    g.setComposite(RenderCache.getAlpha(0.6f));
                    g.setColor(new Color(255, 150, 150));
                    g.setStroke(new BasicStroke(2f));
                    g.drawLine(bx, by, bx + beamLength, by);
                    g.drawLine(bx, by + (int)beamWidth, bx + beamLength, by + (int)beamWidth);
                    
                    g.setComposite(savedComp);
                }
            }
            
            g.setTransform(savedTransform);
            g.setComposite(savedComp);
            
            // Draw center orb at boss position (sized to fully cover beam seams)
            int orbRadius;
            int fullBeam = (int)(beamWidth);
            if (warningTime > 0) {
                // Warning phase: pulsing orb that grows to cover beam junction
                double progress = 1.0 - (warningTime / 180.0);
                orbRadius = (int)(fullBeam * 0.3 + progress * fullBeam * 0.7);
                int r = (int)(100 + progress * 155);
                int gn = (int)(200 - progress * 150);
                double blinkSpeed = 0.1 + progress * 0.4;
                float alphaF = (float)(Math.abs(Math.sin(warningTime * blinkSpeed)) * 150 + 50) / 255f;
                
                // Outer glow
                int glowPad = orbRadius / 3;
                g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF * 0.4f)));
                g.setColor(new Color(r, gn, 50));
                g.fillOval((int)bossX - orbRadius - glowPad, (int)bossY - orbRadius - glowPad, (orbRadius + glowPad) * 2, (orbRadius + glowPad) * 2);
                // Main orb
                g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF)));
                g.fillOval((int)bossX - orbRadius, (int)bossY - orbRadius, orbRadius * 2, orbRadius * 2);
                // Bright core
                g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF + 0.2f)));
                g.setColor(new Color(255, 255, 200));
                int coreR = orbRadius / 2;
                g.fillOval((int)bossX - coreR, (int)bossY - coreR, coreR * 2, coreR * 2);
            } else {
                // Active phase: steady glowing orb covering beam junction
                orbRadius = fullBeam;
                int glowPad = orbRadius / 2;
                // Outer glow
                g.setComposite(RenderCache.getAlpha(0.3f));
                g.setColor(new Color(191, 97, 106));
                g.fillOval((int)bossX - orbRadius - glowPad, (int)bossY - orbRadius - glowPad, (orbRadius + glowPad) * 2, (orbRadius + glowPad) * 2);
                // Main orb
                g.setComposite(RenderCache.getAlpha(0.78f));
                g.fillOval((int)bossX - orbRadius, (int)bossY - orbRadius, orbRadius * 2, orbRadius * 2);
                // Inner core
                g.setComposite(RenderCache.getAlpha(0.86f));
                g.setColor(new Color(255, 150, 150));
                int coreR = orbRadius / 2;
                g.fillOval((int)bossX - coreR, (int)bossY - coreR, coreR * 2, coreR * 2);
                // Bright center dot
                g.setComposite(RenderCache.getAlpha(0.95f));
                g.setColor(new Color(255, 220, 220));
                int dotR = orbRadius / 4;
                g.fillOval((int)bossX - dotR, (int)bossY - dotR, dotR * 2, dotR * 2);
            }
            g.setComposite(savedComp);
        }

        // Draw hex cage attack (6 beams forming a hexagon around boss)
        if (boss != null && boss.isHexCageActive()) {
            double bossX = boss.getX();
            double bossY = boss.getY();
            double hexRadius = boss.getHexCageRadius();
            double hexAngle = boss.getHexCageAngle();
            int hexWidth = boss.getHexCageWidth();
            int warningTime = boss.getHexCageWarningTimer();
            int hexPhase = boss.getHexCagePhase();
            
            Composite savedComp2 = g.getComposite();
            AffineTransform savedTransform2 = g.getTransform();
            Stroke savedStroke = g.getStroke();
            
            // Compute the 6 hex vertices
            double[] vx = new double[6];
            double[] vy = new double[6];
            for (int i = 0; i < 6; i++) {
                double a = hexAngle + i * Math.PI / 3.0;
                vx[i] = bossX + hexRadius * Math.cos(a);
                vy[i] = bossY + hexRadius * Math.sin(a);
            }
            
            if (warningTime > 0) {
                // Warning phase: blinking hex outline
                double progress = 1.0 - (warningTime / 180.0);
                double blinkSpeed = 0.1 + progress * 0.4;
                float alphaF = (float)(Math.abs(Math.sin(warningTime * blinkSpeed)) * 150 + 50) / 255f;
                
                // Cyan/teal tint for hex cage
                int r = (int)(50 + progress * 50);
                int gn = (int)(180 + progress * 75);
                int b = (int)(200 + progress * 55);
                
                g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF)));
                g.setColor(new Color(r, gn, b));
                g.setStroke(new BasicStroke(3f + (float)(progress * 3)));
                
                // Draw hex outline
                int[] xPoints = new int[6];
                int[] yPoints = new int[6];
                for (int i = 0; i < 6; i++) {
                    xPoints[i] = (int)vx[i];
                    yPoints[i] = (int)vy[i];
                }
                g.drawPolygon(xPoints, yPoints, 6);
                
                // Warning "!" symbols at alternate vertices
                if (warningTime > 30) {
                    g.setFont(new Font("SansSerif", Font.BOLD, 20));
                    g.setComposite(RenderCache.getAlpha(Math.min(1f, alphaF + 0.2f)));
                    for (int i = 0; i < 6; i += 2) {
                        g.drawString("!", (int)vx[i] - 5, (int)vy[i] + 7);
                    }
                }
            } else {
                // Active phase: draw 6 beam segments as rotated rectangles
                for (int i = 0; i < 6; i++) {
                    int next = (i + 1) % 6;
                    double midX = (vx[i] + vx[next]) / 2.0;
                    double midY = (vy[i] + vy[next]) / 2.0;
                    double sideAngle = Math.atan2(vy[next] - vy[i], vx[next] - vx[i]);
                    double sideLength = hexRadius; // side length == radius for regular hex
                    
                    g.setTransform(savedTransform2);
                    g.rotate(sideAngle, midX, midY);
                    
                    int bx = (int)(midX - sideLength / 2.0);
                    int by = (int)(midY - hexWidth / 2.0);
                    int bw = (int)sideLength;
                    
                    // Outer glow (cyan/teal tint)
                    g.setComposite(RenderCache.getAlpha(0.25f));
                    g.setColor(new Color(80, 200, 220));
                    g.fillRect(bx - 5, by - 8, bw + 10, hexWidth + 16);
                    
                    // Main beam body
                    g.setComposite(RenderCache.getAlpha(0.75f));
                    g.setColor(new Color(70, 190, 210));
                    g.fillRect(bx, by, bw, hexWidth);
                    
                    // Inner core (brighter)
                    g.setComposite(RenderCache.getAlpha(0.85f));
                    g.setColor(new Color(140, 230, 245));
                    g.fillRect(bx, by + hexWidth / 4, bw, hexWidth / 2);
                    
                    // Animated scanlines (reuse spinning beam scanline tile)
                    g.setComposite(savedComp2);
                    int scanOffset = (int)((hexRadius * 0.5) % 8);
                    TexturePaint scanPaint = new TexturePaint(SPIN_BEAM_SCANLINE_TILE,
                        new Rectangle2D.Float(scanOffset, 0, 8, 1));
                    g.setPaint(scanPaint);
                    g.fillRect(bx, by, bw, hexWidth);
                    
                    // Edge borders
                    g.setComposite(RenderCache.getAlpha(0.5f));
                    g.setColor(new Color(140, 230, 245));
                    g.setStroke(new BasicStroke(2f));
                    g.drawLine(bx, by, bx + bw, by);
                    g.drawLine(bx, by + hexWidth, bx + bw, by + hexWidth);
                    
                    g.setComposite(savedComp2);
                }
                
                g.setTransform(savedTransform2);
                
                // Draw vertex glow points where beams meet
                for (int i = 0; i < 6; i++) {
                    int glowR = hexWidth / 2 + 4;
                    g.setComposite(RenderCache.getAlpha(0.4f));
                    g.setColor(new Color(80, 200, 220));
                    g.fillOval((int)vx[i] - glowR, (int)vy[i] - glowR, glowR * 2, glowR * 2);
                    int coreR = hexWidth / 4;
                    g.setComposite(RenderCache.getAlpha(0.7f));
                    g.setColor(new Color(180, 240, 255));
                    g.fillOval((int)vx[i] - coreR, (int)vy[i] - coreR, coreR * 2, coreR * 2);
                }
            }
            
            g.setTransform(savedTransform2);
            g.setComposite(savedComp2);
            g.setStroke(savedStroke);
        }

        // Draw frost beam from active item (two-phase animation: extend thin, then thicken)

        ActiveItem equippedItem = gameData.getEquippedItem();

        boolean shouldDrawFrostBeam = player != null && (frostBeamProgress > 0 || frostBeamRetracting);

        if (shouldDrawFrostBeam) {

            double angle = frostBeamAngle;

            double maxLaserLength = Math.sqrt(width * width + height * height); // Full screen diagonal

            

            // RETRACTION ANIMATION - override normal phases when retracting

            double lengthMultiplier = 1.0;

            double widthMultiplier = 1.0;

            double alphaMultiplier = 1.0;

            

            if (frostBeamRetracting) {

                // Phase 1 (0.0-0.3): Beam rapidly thins out

                // Phase 2 (0.3-0.7): Beam shortens from the tip

                // Phase 3 (0.7-1.0): Circle fades and shrinks

                

                if (frostBeamRetractPhase < 0.3) {

                    // Thinning phase - width shrinks quickly

                    double thinProgress = frostBeamRetractPhase / 0.3;

                    widthMultiplier = 1.0 - (thinProgress * 0.7); // Goes to 30% width

                    lengthMultiplier = 1.0;

                    alphaMultiplier = 1.0;

                } else if (frostBeamRetractPhase < 0.7) {

                    // Shortening phase - beam retracts from tip

                    double shortenProgress = (frostBeamRetractPhase - 0.3) / 0.4;

                    // Ease in-out for smooth retraction

                    double easedShorten = shortenProgress < 0.5 

                        ? 2 * shortenProgress * shortenProgress 

                        : 1 - Math.pow(-2 * shortenProgress + 2, 2) / 2;

                    widthMultiplier = 0.3;

                    lengthMultiplier = 1.0 - easedShorten; // Shrinks to 0

                    alphaMultiplier = 1.0 - (shortenProgress * 0.3); // Slight fade

                } else {

                    // Circle fade phase

                    double fadeProgress = (frostBeamRetractPhase - 0.7) / 0.3;

                    widthMultiplier = 0.3 * (1.0 - fadeProgress);

                    lengthMultiplier = 0;

                    alphaMultiplier = 1.0 - fadeProgress;

                }

            }

            

            // TWO-PHASE ANIMATION (for extending):

            // Phase 1 (0.0 - 0.3): Beam extends to full length but stays thin

            // Phase 2 (0.3 - 0.6): Beam rapidly thickens with sharp transition

            // Phase 3 (0.6 - 1.0): Full beam, fully thick

            

            // Length: Reaches full length quickly in phase 1

            double lengthProgress = Math.min(1.0, frostBeamProgress / 0.25); // Full length by 0.25

            double easedLengthProgress = 1.0 - Math.pow(1.0 - lengthProgress, 2); // Ease out

            

            // Width: Stays thin until 0.3, then rapidly expands

            double widthProgress;

            if (frostBeamProgress < 0.3) {

                // Phase 1: Very thin (10% width)

                widthProgress = 0.1;

            } else if (frostBeamProgress < 0.6) {

                // Phase 2: Rapid expansion from 10% to 100%

                double expandProgress = (frostBeamProgress - 0.3) / 0.3; // 0 to 1 over this phase

                // Use elastic ease for satisfying snap

                double elasticEase = Math.pow(2, -10 * (1 - expandProgress)) * Math.sin((expandProgress - 0.1) * 5 * Math.PI) + 1;

                elasticEase = Math.max(0, Math.min(1, elasticEase)); // Clamp

                widthProgress = 0.1 + 0.9 * elasticEase;

            } else {

                // Phase 3: Full width

                widthProgress = 1.0;

            }

            

            // Apply retraction multipliers

            if (frostBeamRetracting) {

                widthProgress *= widthMultiplier;

                easedLengthProgress *= lengthMultiplier;

            }

            

            // Circle also expands with width

            double circleProgress = Math.max(widthProgress, 0.3) * alphaMultiplier; // At least 30% visible

            

            double maxWidth = 55;

            double currentWidth = maxWidth * widthProgress;

            

            // Length is constant (full) once extended

            double laserLength = maxLaserLength * easedLengthProgress;

            

            // If beam hits a bullet, stop at that distance (only when not retracting)

            if (!frostBeamRetracting && frostBeamStopDistance > 0 && frostBeamStopDistance < laserLength) {

                laserLength = frostBeamStopDistance;

            }

            

            // Circle parameters - centered on player

            double circleRadius = 25 + (15 * circleProgress); // Grows with width phase

            double circleX = player.getX();

            double circleY = player.getY();

            

            // Calculate beam start position (at edge of centered circle)

            double beamStartX = player.getX() + Math.cos(angle) * circleRadius;

            double beamStartY = player.getY() + Math.sin(angle) * circleRadius;

            

            // Save/restore instead of g.create() to avoid Graphics2D clone allocation

            AffineTransform savedFrostTransform = g.getTransform();

            Composite savedFrostComposite = g.getComposite();

            Stroke savedFrostStroke = g.getStroke();

            Object savedFrostAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            

            // Draw hollow circle CENTERED on player - ICE BLUE

            int alpha = (int)(200 * circleProgress);

            

            // Outer glow circle â€” use base color + AlphaComposite instead of new Color(r,g,b,a)

            g.setComposite(RenderCache.getAlpha((float)(80 * circleProgress) / 255f));

            g.setColor(FROST_OUTER_GLOW);

            g.setStroke(RenderCache.getStroke((float)(10 * circleProgress)));

            g.drawOval((int)(circleX - circleRadius), (int)(circleY - circleRadius), 

                        (int)(circleRadius * 2), (int)(circleRadius * 2));

            

            // Main circle ring

            g.setComposite(RenderCache.getAlpha((float)alpha / 255f));

            g.setColor(RenderCache.ICE_BLUE);

            g.setStroke(RenderCache.getStroke((float)(5 * circleProgress)));

            g.drawOval((int)(circleX - circleRadius), (int)(circleY - circleRadius), 

                        (int)(circleRadius * 2), (int)(circleRadius * 2));

            

            // Inner bright ring

            g.setColor(RenderCache.ICY_WHITE);

            g.setStroke(RenderCache.getStroke((float)(2 * circleProgress)));

            int innerOffset = (int)(3 * circleProgress);

            g.drawOval((int)(circleX - circleRadius + innerOffset), (int)(circleY - circleRadius + innerOffset), 

                        (int)(circleRadius * 2 - innerOffset * 2), (int)(circleRadius * 2 - innerOffset * 2));

            

            // Draw the beam extending from circle edge - with smooth base and end cap

            // Skip beam body if length is 0 (final retraction phase)

            if (laserLength > 5) {

                // Save/restore transform for beam rotation instead of g.create()

                g.translate(beamStartX, beamStartY);

                g.rotate(angle);

                

                // Alpha fades in smoothly, and applies retraction alpha

                double beamAlpha = Math.min(1.0, frostBeamProgress / 0.15) * alphaMultiplier;

            

            // === BASE CONNECTION - Smooth gradient from circle ===

            // Draw a filled semicircle at the base to connect smoothly to the ring

            int baseRadius = (int)(currentWidth * 0.9);

            g.setComposite(RenderCache.getAlpha((float)(60 * beamAlpha) / 255f));

            g.setColor(FROST_OUTER_GLOW);

            g.fillArc(-baseRadius/2, -baseRadius, baseRadius, baseRadius * 2, -90, 180);

            

            g.setComposite(RenderCache.getAlpha((float)(150 * beamAlpha) / 255f));

            g.setColor(RenderCache.ICE_BLUE);

            int innerBaseRadius = (int)(currentWidth * 0.5);

            g.fillArc(-innerBaseRadius/2, -innerBaseRadius, innerBaseRadius, innerBaseRadius * 2, -90, 180);

            

            g.setComposite(RenderCache.getAlpha((float)(200 * beamAlpha) / 255f));

            g.setColor(RenderCache.ICY_WHITE);

            int coreBaseRadius = (int)(currentWidth * 0.2);

            g.fillArc(-coreBaseRadius/2, -coreBaseRadius, coreBaseRadius, coreBaseRadius * 2, -90, 180);

            

            // === MAIN BEAM BODY - Consistent width rectangles ===

            // Outer glow beam - ICE BLUE

            g.setComposite(RenderCache.getAlpha((float)(50 * beamAlpha) / 255f));

            g.setColor(FROST_OUTER_GLOW);

            g.fillRect(0, (int)(-currentWidth * 0.9), (int)laserLength, (int)(currentWidth * 1.8));

            

            // Inner beam - ICE BLUE

            g.setComposite(RenderCache.getAlpha((float)(180 * beamAlpha) / 255f));

            g.setColor(RenderCache.ICE_BLUE);

            g.fillRect(0, (int)(-currentWidth * 0.5), (int)laserLength, (int)(currentWidth * 1.0));

            

            // Core beam - ICY WHITE (brightest)

            g.setComposite(RenderCache.getAlpha((float)(220 * beamAlpha) / 255f));

            g.setColor(RenderCache.ICY_WHITE);

            g.fillRect(0, (int)(-currentWidth * 0.2), (int)laserLength, (int)(currentWidth * 0.4));

            

            // === END CAP - Ice crystal topper ===

            int endX = (int)laserLength;

            

            // Outer glow end cap (rounded)

            g.setComposite(RenderCache.getAlpha((float)(70 * beamAlpha) / 255f));

            g.setColor(FROST_OUTER_GLOW);

            int endCapRadius = (int)(currentWidth * 1.2);

            g.fillOval(endX - endCapRadius/2, -endCapRadius, endCapRadius, endCapRadius * 2);

            

            // Inner end cap

            g.setComposite(RenderCache.getAlpha((float)(180 * beamAlpha) / 255f));

            g.setColor(RenderCache.ICE_BLUE);

            int innerEndRadius = (int)(currentWidth * 0.7);

            g.fillOval(endX - innerEndRadius/3, -innerEndRadius, innerEndRadius, innerEndRadius * 2);

            

            // Core bright end

            g.setComposite(RenderCache.getAlpha((float)(230 * beamAlpha) / 255f));

            g.setColor(RenderCache.ICY_WHITE);

            int coreEndRadius = (int)(currentWidth * 0.35);

            g.fillOval(endX - coreEndRadius/4, -coreEndRadius, coreEndRadius, coreEndRadius * 2);

            

            // Ice crystal spikes at the tip

            g.setComposite(RenderCache.getAlpha((float)(200 * beamAlpha) / 255f));

            g.setColor(FROST_SPIKE_WHITE);

            int spikeLength = (int)(currentWidth * 0.6);

            // Center spike

            int[] spikeX = {endX, endX + spikeLength, endX};

            int[] spikeY = {(int)(-currentWidth * 0.15), 0, (int)(currentWidth * 0.15)};

            g.fillPolygon(spikeX, spikeY, 3);

            // Top spike

            int[] spikeX2 = {endX, endX + (int)(spikeLength * 0.6), endX};

            int[] spikeY2 = {(int)(-currentWidth * 0.4), (int)(-currentWidth * 0.2), (int)(-currentWidth * 0.15)};

            g.fillPolygon(spikeX2, spikeY2, 3);

            // Bottom spike

            int[] spikeX3 = {endX, endX + (int)(spikeLength * 0.6), endX};

            int[] spikeY3 = {(int)(currentWidth * 0.4), (int)(currentWidth * 0.2), (int)(currentWidth * 0.15)};

            g.fillPolygon(spikeX3, spikeY3, 3);

            

            } // End of laserLength > 5 check

            // Restore all saved state

            g.setTransform(savedFrostTransform);

            g.setComposite(savedFrostComposite);

            g.setStroke(savedFrostStroke);

            if (savedFrostAA != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, savedFrostAA);

        }

        

        // Draw particles (behind sprites) - indexed loop avoids ArrayList copy
        // Safe: update() and render run sequentially on game thread
        // === Viewport culling: skip objects outside camera view ===
        double viewLeft = cameraX - breathX - 80;   // 80px margin for large particles/glow
        double viewTop = cameraY - breathY - 80;
        double viewRight = cameraX - breathX + width + 80;
        double viewBottom = cameraY - breathY + height + 80;
        
        int particleCount = particles.size();
        for (int pi = 0; pi < particleCount && pi < particles.size(); pi++) {
            Particle particle = particles.get(pi);
            if (particle != null && particle.isAlive() && particle.getType() != Particle.ParticleType.MONEY_SIGN) {
                double px = particle.getX();
                double py = particle.getY();
                if (px >= viewLeft && px <= viewRight && py >= viewTop && py <= viewBottom) {
                    particle.draw(g);
                }
            }
        }
        
        // Draw flares (after particles, before player)
        if (flares != null) {
            for (int fi = 0; fi < flares.size(); fi++) {
                Flare flare = flares.get(fi);
                if (flare != null && flare.isActive()) {
                    double fx = flare.getX();
                    double fy = flare.getY();
                    if (fx >= viewLeft && fx <= viewRight && fy >= viewTop && fy <= viewBottom) {
                        flare.draw(g);
                    }
                }
            }
        }

        

        // Draw player afterimages (ghost trail when moving fast)

        if (player != null && Game.enableParticles) {

            double speed = Math.sqrt(player.getVX() * player.getVX() + player.getVY() * player.getVY());

            if (speed > 2.0) {

                // Draw fading afterimages behind the player

                double angle = Math.atan2(player.getVY(), player.getVX());

                for (int i = 1; i <= 4; i++) {

                    double trailX = player.getX() - Math.cos(angle) * (i * 8);

                    double trailY = player.getY() - Math.sin(angle) * (i * 8);

                    float alpha = (float)(0.3 - i * 0.07) * (float)(speed / 6.0);

                    alpha = Math.max(0, Math.min(1, alpha));

                    

                    g.setComposite(RenderCache.getAlpha(alpha));

                    g.setColor(AFTERIMAGE_COLOR);

                    int size = 12 - i * 2;

                    g.fillOval((int)trailX - size/2, (int)trailY - size/2, size, size);

                }

                g.setComposite(ALPHA_FULL);

            }

        }

        

        // Draw player (only if not hidden during death sequence)

        if (player != null && !playerHidden) {

            // Apply blink effect during respawn
            if (respawnBlinkTimer > 0) {
                // Toggle between semi-transparent and fully opaque
                float blinkAlpha = (respawnBlinkTimer % 12 < 6) ? 0.3f : 1.0f;
                g.setComposite(RenderCache.getAlpha(blinkAlpha));
            }

            player.draw(g);

            // Reset composite after blink
            if (respawnBlinkTimer > 0) {
                g.setComposite(ALPHA_FULL);
            }

            

            // Draw orbiting shields if active - curved arc shields (top-down view)

            if (shieldActive && shieldHits > 0) {

                int orbitRadius = 38; // Closer to player

                double TWO_PI = Math.PI * 2;

                double arcSpan = 80; // Degrees each shield arc covers

                int px = (int)player.getX();

                int py = (int)player.getY();

                

                // Draw each remaining shield as a curved arc

                for (int i = 0; i < shieldHits; i++) {

                    // Calculate angle in degrees - evenly spaced around player

                    double angleRad = shieldOrbitAngle + (i * TWO_PI / 3.0);

                    double angleDeg = Math.toDegrees(angleRad);

                    double startAngle = angleDeg - arcSpan / 2;

                    

                    // === Outer glow arc ===

                    int glowPulse = (int)(Math.sin(time * 0.08 + i * 2.1) * 3);

                    int glowR = orbitRadius + 8 + glowPulse;

                    g.setColor(SHIELD_ARC_OUTER);

                    g.setStroke(ROUND_STROKE_14);

                    g.drawArc(px - glowR, py - glowR, glowR * 2, glowR * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Mid glow arc ===

                    g.setColor(SHIELD_ARC_MID);

                    g.setStroke(ROUND_STROKE_10);

                    g.drawArc(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Main shield body - thick curved line ===

                    g.setColor(SHIELD_ARC_MAIN);

                    g.setStroke(ROUND_STROKE_6);

                    g.drawArc(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Bright inner edge highlight ===

                    int innerR = orbitRadius - 3;

                    g.setColor(SHIELD_ARC_EDGE);

                    g.setStroke(ROUND_STROKE_2_5);

                    g.drawArc(px - innerR, py - innerR, innerR * 2, innerR * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Bright tips at the ends of each arc ===

                    double tipAngle1 = Math.toRadians(startAngle);

                    double tipAngle2 = Math.toRadians(startAngle + arcSpan);

                    int tipSize = 5;

                    g.setColor(SHIELD_ARC_TIP);

                    g.fillOval(px + (int)(Math.cos(tipAngle1) * orbitRadius) - tipSize/2,

                              py - (int)(Math.sin(tipAngle1) * orbitRadius) - tipSize/2, tipSize, tipSize);

                    g.fillOval(px + (int)(Math.cos(tipAngle2) * orbitRadius) - tipSize/2,

                              py - (int)(Math.sin(tipAngle2) * orbitRadius) - tipSize/2, tipSize, tipSize);

                }

                

                // Subtle inner ring connecting all shields

                g.setColor(SHIELD_ARC_INNER);

                g.setStroke(RenderCache.getStroke(1.5f));

                g.drawOval(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2);

            }

            

            // Draw invincibility glow

            if (playerInvincible) {

                int glowRadius = 40;

                int pulseSize = (int)(Math.sin(time * 0.15) * 5);

                

                // Pulsing gold glow

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_GOLD, 80));

                g.fillOval((int)player.getX() - glowRadius - pulseSize, 

                          (int)player.getY() - glowRadius - pulseSize, 

                          (glowRadius + pulseSize) * 2, (glowRadius + pulseSize) * 2);

                

                g.setColor(INVINCIBILITY_GLOW);

                g.fillOval((int)player.getX() - glowRadius / 2, 

                          (int)player.getY() - glowRadius / 2, 

                          glowRadius, glowRadius);

            }

        }

        

        // Draw boss with special handling during death animation

        if (boss != null) {

        if (bossDeathAnimation) {

            // Save/restore transform instead of g.create() to avoid Graphics2D clone

            AffineTransform savedDeathTransform = g.getTransform();

            Composite savedDeathComposite = g.getComposite();

            

            // Apply death animation transformations

            g.translate(boss.getX(), boss.getY());

            g.rotate(bossDeathRotation);

            g.scale(bossDeathScale, bossDeathScale);

            g.translate(-boss.getX(), -boss.getY());

            

            // Draw boss with transformations

            boss.draw(g);

            

            // Add red/orange tint for fire effect

            g.setComposite(RenderCache.getAlpha(0.3f));

            g.setColor(BOSS_DEATH_FIRE);

            double size = boss.getSize() * bossDeathScale;

            g.fillOval((int)(boss.getX() - size/2), (int)(boss.getY() - size/2), (int)size, (int)size);

            

            g.setTransform(savedDeathTransform);

            g.setComposite(savedDeathComposite);

        } else {

            // Draw ALL indicators UNDER boss sprite

            

            // Draw soft bloom/glow effect UNDER boss (layered for smooth falloff)

            if (Game.enableBloom) {

                // Use save/restore instead of g.create() to avoid Graphics2D clone
                Composite savedBossBloomComp = g.getComposite();

                

                // Choose bloom color based on state

                Color bloomColor;

                if (bossVulnerable) {

                    // Golden/white bloom when vulnerable - indicates "attack now!"

                    float pulse = 0.7f + 0.3f * (float)Math.sin(time * 6);

                    bloomColor = RenderCache.WARM_255_240_200;

                    

                    // 2 layers instead of 4 (saves 2 fillOval per frame)

                    for (int i = 2; i > 0; i--) {

                        float alpha = (0.12f * pulse) / i;

                        g.setComposite(RenderCache.getAlpha(Math.min(alpha, 1.0f)));

                        g.setColor(bloomColor);

                        double glowSize = boss.getSize() * (1.0 + i * 0.6);

                        g.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                    }

                } else {

                    // Subtle cool bloom when invulnerable â€” 1 layer instead of 3

                    bloomColor = BOSS_COOL_BLOOM;

                    g.setComposite(RenderCache.getAlpha(0.04f));

                    g.setColor(bloomColor);

                    double glowSize = boss.getSize() * 1.5;

                    g.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                }

                g.setComposite(savedBossBloomComp);

            }

            

            // Draw attack phase glow effect UNDER boss

            if (boss.isAssaultPhase()) {

                // Red pulsing glow during assault

                Composite _gc = g.getComposite();

                float pulseAlpha = 0.15f + (float)(Math.sin(time * 8) * 0.08f);

                g.setComposite(RenderCache.getAlpha(pulseAlpha));

                g.setColor(RenderCache.RED_255_50_50);

                double glowSize = boss.getSize() * 1.6;

                g.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                g.setComposite(_gc);

            } else {

                // Blue calm glow during recovery

                Composite _gc = g.getComposite();

                float pulseAlpha = 0.1f + (float)(Math.sin(time * 3) * 0.05f);

                g.setComposite(RenderCache.getAlpha(pulseAlpha));

                g.setColor(BOSS_CALM_GLOW);

                double glowSize = boss.getSize() * 1.4;

                g.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                g.setComposite(_gc);

            }

            

            // Draw invulnerability indicator UNDER boss when boss cannot be attacked

            if (!bossVulnerable && !bossDeathAnimation && invulnerabilityTimer > 0) {

                // Orbiting arc shields similar to player shield, scaled up for boss

                double timeRatio = invulnerabilityTimer / 300.0; // Normalize to 0-1

                

                // Blink and fade when disappearing (last 25% of duration)

                float shieldAlpha;

                if (timeRatio < 0.25) {

                    // Blink rapidly (faster as it gets closer to 0)

                    double blinkSpeed = 15.0 + (1.0 - timeRatio / 0.25) * 35.0; // 15 -> 50

                    double blink = Math.sin(time * blinkSpeed);

                    float fadeBase = (float)(timeRatio / 0.25); // 0->1 over last 25%

                    shieldAlpha = blink > 0 ? fadeBase : fadeBase * 0.15f;

                } else {

                    shieldAlpha = 1.0f;

                }

                

                int bossShieldRadius = (int)(boss.getSize() * 1.4);

                double TWO_PI_B = Math.PI * 2;

                double bossArcSpan = 45; // Wider arcs with fewer segments

                int numArcs = 5; // Reduced from 8 â€” saves ~30 draw calls/frame

                double bossShieldAngle = time * 0.12;

                int bx = (int)boss.getX();

                int by = (int)boss.getY();

                

                // Color transitions from blue (full) to red/yellow (low time)

                int sr = (int)(100 + 155 * (1 - timeRatio));

                int sg = (int)(210 * timeRatio);

                int sb = (int)(255 * timeRatio);

                
                // Pre-compute Colors ONCE outside loop (was 40 new Color per frame)
                Color shieldOuter = new Color(sr, sg, sb, (int)(70 * shieldAlpha));
                Color shieldMain = new Color(sr, sg, sb, (int)(200 * shieldAlpha));

                for (int i = 0; i < numArcs; i++) {

                    double angleRad = bossShieldAngle + (i * TWO_PI_B / numArcs);

                    double angleDeg = Math.toDegrees(angleRad);

                    double startAngle = angleDeg - bossArcSpan / 2;

                    
                    // Outer glow arc (merged outer+mid into one)
                    g.setColor(shieldOuter);
                    g.setStroke(SHIELD_STROKE_OUTER);
                    g.drawArc(bx - bossShieldRadius, by - bossShieldRadius, bossShieldRadius * 2, bossShieldRadius * 2, (int)startAngle, (int)bossArcSpan);

                    
                    // Main shield body
                    g.setColor(shieldMain);
                    g.setStroke(SHIELD_STROKE_MAIN);
                    g.drawArc(bx - bossShieldRadius, by - bossShieldRadius, bossShieldRadius * 2, bossShieldRadius * 2, (int)startAngle, (int)bossArcSpan);

                }

                

                // Subtle inner ring connecting all arcs (reuse shieldOuter color)

                g.setColor(shieldOuter);

                g.setStroke(RenderCache.getStroke(1.5f));

                g.drawOval(bx - bossShieldRadius, by - bossShieldRadius, bossShieldRadius * 2, bossShieldRadius * 2);

            }

            

            // Normal boss drawing (AFTER all indicators so boss appears on top)

            boss.draw(g);

            
            // Boss accumulated damage overlay - smoke wisps & fire glow
            float bossHpPct = boss.getHealthPercent(); // 1.0 = full, 0.0 = dead
            if (boss.isFinalBoss()) {
                // Final boss: 6-state progressive damage system based on getDamageState()
                int damageState = boss.getDamageState();
                if (damageState > 0) {
                    Composite _damSave = g.getComposite();
                    int bSize = boss.getSize();
                    int bCx = (int) boss.getX();
                    int bCy = (int) boss.getY();

                    if (damageState >= 1) {
                        // State 1: Light scratches - thin dark lines across body
                        g.setComposite(RenderCache.getAlpha(0.15f));
                        g.setColor(new Color(30, 30, 30));
                        g.setStroke(RenderCache.getStroke(1.5f));
                        int scratchSpread = bSize / 3;
                        for (int i = 0; i < 4; i++) {
                            int sx = bCx - scratchSpread + (int)(Math.sin(i * 2.7) * scratchSpread);
                            int sy = bCy - scratchSpread / 2 + i * (scratchSpread / 2);
                            g.drawLine(sx, sy, sx + 15 + i * 5, sy + 8 - i * 3);
                        }
                    }
                    if (damageState >= 2) {
                        // State 2: Smoke wisps - faint gray haze near wing tips
                        g.setComposite(RenderCache.getAlpha(0.2f));
                        g.setColor(new Color(60, 60, 60));
                        int wispW = (int)(bSize * 0.3);
                        int wispH = (int)(bSize * 0.2);
                        g.fillOval(bCx - bSize / 2 - wispW / 4, bCy - wispH / 2, wispW, wispH);
                        g.fillOval(bCx + bSize / 2 - wispW * 3 / 4, bCy - wispH / 2, wispW, wispH);
                    }
                    if (damageState >= 3) {
                        // State 3: Thick smoke + small fires
                        g.setComposite(RenderCache.getAlpha(0.35f));
                        g.setColor(new Color(40, 40, 40));
                        int smokeW = (int)(bSize * 1.0);
                        int smokeH = (int)(bSize * 0.5);
                        g.fillOval(bCx - smokeW / 2, bCy - smokeH / 2, smokeW, smokeH);
                        g.setComposite(RenderCache.getAlpha(0.3f));
                        g.setColor(new Color(255, 140, 30));
                        int fireS = bSize / 5;
                        g.fillOval(bCx - bSize / 4 - fireS / 2, bCy + bSize / 6, fireS, fireS);
                        g.fillOval(bCx + bSize / 6 - fireS / 2, bCy - bSize / 6, fireS, fireS);
                    }
                    if (damageState >= 4) {
                        // State 4: Heavy fire + thick black smoke trail
                        g.setComposite(RenderCache.getAlpha(0.45f));
                        g.setColor(new Color(20, 20, 20));
                        int trailW = (int)(bSize * 0.6);
                        int trailH = (int)(bSize * 1.2);
                        g.fillOval(bCx - trailW / 2, bCy, trailW, trailH);
                        g.setComposite(RenderCache.getAlpha(0.4f));
                        g.setColor(new Color(255, 100, 10));
                        int fireW = (int)(bSize * 0.7);
                        int fireH = (int)(bSize * 0.4);
                        g.fillOval(bCx - fireW / 2, bCy - fireH / 3, fireW, fireH);
                    }
                    if (damageState >= 5) {
                        // State 5: Engulfed in flames + bright fire
                        g.setComposite(RenderCache.getAlpha(0.5f));
                        g.setColor(new Color(255, 80, 0));
                        int engulfW = (int)(bSize * 1.3);
                        int engulfH = (int)(bSize * 0.9);
                        g.fillOval(bCx - engulfW / 2, bCy - engulfH / 2, engulfW, engulfH);
                        g.setComposite(RenderCache.getAlpha(0.25f));
                        g.setColor(new Color(255, 255, 200));
                        int coreW = (int)(bSize * 0.5);
                        int coreH = (int)(bSize * 0.3);
                        g.fillOval(bCx - coreW / 2, bCy - coreH / 2, coreW, coreH);
                    }
                    g.setComposite(_damSave);
                }
            } else if (bossHpPct < 0.7f) {
                Composite _damSave = g.getComposite();
                int bSize = boss.getSize();
                int bCx = (int) boss.getX();
                int bCy = (int) boss.getY();
                float dmgRatio = 1.0f - bossHpPct;
                float overlayAlpha = Math.min(dmgRatio * 0.7f, 0.55f);

                // Dark smoke haze across body
                g.setComposite(RenderCache.getAlpha(overlayAlpha * 0.6f));
                g.setColor(new Color(40, 40, 40));
                int smokeW = (int)(bSize * 1.4 * dmgRatio);
                int smokeH = (int)(bSize * 0.8 * dmgRatio);
                g.fillOval(bCx - smokeW / 2, bCy - smokeH / 2, smokeW, smokeH);

                // Fire glow when heavily damaged (>50% lost)
                if (bossHpPct < 0.5f) {
                    float fireAlpha = Math.min((0.5f - bossHpPct) * 1.2f, 0.45f);
                    g.setComposite(RenderCache.getAlpha(fireAlpha));
                    g.setColor(new Color(255, 120, 20));
                    int fireW = (int)(bSize * 0.9 * dmgRatio);
                    int fireH = (int)(bSize * 0.5 * dmgRatio);
                    g.fillOval(bCx - fireW / 2, bCy - fireH / 3, fireW, fireH);
                }
                g.setComposite(_damSave);
            }

            // Draw shockwave during recovery phase (circular arc directed at player)

            if (boss.isShockwaveActive()) {
                // Save/restore instead of g.create() â€” avoids Graphics2D clone
                Composite shockSavedComp = g.getComposite();
                Stroke shockSavedStroke = g.getStroke();

                double radius = boss.getShockwaveRadius();

                double angle = boss.getShockwaveAngle();

                double coneAngle = Math.PI / 2; // 90 degree cone

                

                int bossX = (int)boss.getX();

                int bossY = (int)boss.getY();

                

                // Convert angle to degrees for arc drawing

                double adjustedAngle = -Math.toDegrees(angle);

                int startAngleDeg = (int)(adjustedAngle - Math.toDegrees(coneAngle/2));

                int arcAngleDeg = (int)Math.toDegrees(coneAngle);

                

                // Draw multiple expanding circular arcs (reduced to 3 for perf)

                for (int i = 0; i < 3; i++) {

                    double ringRadius = radius - (i * 30);

                    if (ringRadius > 0) {

                        float bgAlpha = (float)((1.0 - (ringRadius / 250.0)) * 0.4f) / (i * 0.3f + 1);

                        g.setComposite(RenderCache.getAlpha(bgAlpha));

                        g.setColor(RenderCache.BLUE_150_200_255);

                        g.fillArc((int)(bossX - ringRadius), (int)(bossY - ringRadius), 

                                   (int)(ringRadius * 2), (int)(ringRadius * 2), 

                                   startAngleDeg, arcAngleDeg);

                        

                        float alpha = (float)((1.0 - (ringRadius / 250.0)) * 1.2f) / (i * 0.2f + 1);

                        alpha = Math.min(alpha, 1.0f);

                        g.setComposite(RenderCache.getAlpha(alpha));

                        g.setColor(RenderCache.BLUE_100_180_255);

                        g.setStroke(SHOCKWAVE_STROKES[i]);

                        g.drawArc((int)(bossX - ringRadius), (int)(bossY - ringRadius), 

                                   (int)(ringRadius * 2), (int)(ringRadius * 2), 

                                   startAngleDeg, arcAngleDeg);

                    }

                }

                g.setComposite(shockSavedComp);
                g.setStroke(shockSavedStroke);

            }

            

            // Boss damage flash effect

            if (bossFlashTimer > 0) {

                Composite _fc = g.getComposite();

                float flashAlpha = (float)bossFlashTimer / 12.0f * 0.5f; // Fade out over 12 frames

                g.setComposite(RenderCache.getAlpha(flashAlpha));

                g.setColor(Color.WHITE);

                double size = boss.getSize() * 1.2;

                g.fillOval((int)(boss.getX() - size/2), (int)(boss.getY() - size/2), (int)size, (int)size);

                g.setComposite(_fc);

            }

        }

        } // End boss != null check

        

        // Draw bullets (including warnings for inactive bullets) â€” with viewport culling
        Bullet.activeBulletCount = bullets.size(); // Set for dynamic shadow reduction
        for (int i = 0; i < bullets.size(); i++) {

            Bullet bullet = bullets.get(i);

            if (bullet != null) {
                double bx = bullet.getX();
                double by = bullet.getY();
                // Use wider margin (120px) for bullets since they have warning indicators and glow effects
                if (bx >= viewLeft - 40 && bx <= viewRight + 40 && by >= viewTop - 40 && by <= viewBottom + 40) {
                    bullet.draw(g);
                }
            }

        }

        

        // Draw MONEY_SIGN particles ON TOP of player and bullets â€” with viewport culling
        for (int pi = 0; pi < particles.size(); pi++) {
            Particle particle = particles.get(pi);
            if (particle != null && particle.isAlive() && particle.getType() == Particle.ParticleType.MONEY_SIGN) {
                double px = particle.getX();
                double py = particle.getY();
                if (px >= viewLeft && px <= viewRight && py >= viewTop && py <= viewBottom) {
                    particle.draw(g);
                }
            }
        }

        

        // Draw hitboxes for debugging if enabled

        if (Game.enableHitboxes) {

            Graphics2D g2d = (Graphics2D) g.create();

            g2d.setStroke(RenderCache.getStroke(2));

            

            // Player hitbox (green circle) - uses SIZE * 0.3 radius for collision

            if (player != null) {

                int playerHitRadius = (int)(player.getSize() * 0.3);

                int grazeRadius = (int)(player.getSize() * 0.3 + 4 + 25); // hitDistance + GRAZE_DISTANCE

                

                // Graze zone (outer cyan dashed circle)

                g2d.setColor(new Color(0, 200, 255, 100));

                float[] dashPattern = {8, 4};

                g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dashPattern, 0));

                g2d.drawOval((int)player.getX() - grazeRadius, (int)player.getY() - grazeRadius, 

                            grazeRadius * 2, grazeRadius * 2);

                

                // Actual hitbox (solid green)

                g2d.setStroke(RenderCache.getStroke(2));

                g2d.setColor(new Color(0, 255, 0, 200));

                g2d.drawOval((int)player.getX() - playerHitRadius, (int)player.getY() - playerHitRadius, 

                            playerHitRadius * 2, playerHitRadius * 2);

                

                // Center dot

                g2d.setColor(new Color(0, 255, 0, 255));

                g2d.fillOval((int)player.getX() - 2, (int)player.getY() - 2, 4, 4);

            }

            

            // Boss hitbox (red circle) - uses size * 0.85 radius for collision

            if (boss != null) {

                int bossHitRadius = (int)(boss.getSize() * 0.85);

                g2d.setColor(new Color(255, 0, 0, 150));

                g2d.drawOval((int)boss.getX() - bossHitRadius, (int)boss.getY() - bossHitRadius, 

                            bossHitRadius * 2, bossHitRadius * 2);

                

                // Center dot

                g2d.setColor(new Color(255, 0, 0, 255));

                g2d.fillOval((int)boss.getX() - 3, (int)boss.getY() - 3, 6, 6);

            }

            

            // Bullet hitboxes (yellow circles) - uses SIZE * 0.5 radius (SIZE = 6)

            g2d.setColor(new Color(255, 255, 0, 150));

            g2d.setStroke(RenderCache.getStroke(1));

            for (int i = 0; i < bullets.size(); i++) {

                Bullet bullet = bullets.get(i);

                if (bullet != null && bullet.isActive()) {

                    int bulletHitRadius = 3; // SIZE * 0.5 = 6 * 0.5 = 3

                    g2d.drawOval((int)bullet.getX() - bulletHitRadius, (int)bullet.getY() - bulletHitRadius, 

                                bulletHitRadius * 2, bulletHitRadius * 2);

                }

            }

            

            g2d.dispose();

        }

        

        // Apply bloom/glow effect on bright objects

        if (Game.enableBloom) {

            applyBloom(g, player, boss, bullets, particles, bossVulnerable);

        }

        

        // Draw level bounds: solid black outside world, gradient fade at edges

        {

            // Snap to integer pixel coordinates to prevent subpixel gaps between
            // the solid fills and gradient strips caused by fractional camera offsets
            AffineTransform boundsTransform = g.getTransform();
            double tx = boundsTransform.getTranslateX();
            double ty = boundsTransform.getTranslateY();
            AffineTransform snapped = new AffineTransform(boundsTransform);
            snapped.translate(Math.round(tx) - tx, Math.round(ty) - ty);
            g.setTransform(snapped);

            int worldW = levelWorldW;

            int worldH = levelWorldH;

            int gradSize = 80; // Width of the gradient fade

            int gradOffset = 80; // Start gradient this far outside world bounds

            int pad = 2000; // Extra padding for solid black fill beyond world

            Color solidBlack = WORLD_EDGE_80;

            

            // Solid black regions outside the world bounds

            g.setColor(solidBlack);

            g.fillRect(-pad, -pad, pad - gradOffset, worldH + pad * 2); // Left outside

            g.fillRect(worldW + gradOffset, -pad, pad - gradOffset, worldH + pad * 2); // Right outside

            g.fillRect(-gradOffset, -pad, worldW + gradOffset * 2, pad - gradOffset); // Top outside

            g.fillRect(-gradOffset, worldH + gradOffset, worldW + gradOffset * 2, pad - gradOffset); // Bottom outside

            

            // Bake level bounds gradient images (re-bake when arena size changes)
            if (!levelBoundsBaked || bakedWorldW != worldW || bakedWorldH != worldH) {
                bakeLevelBounds(worldW, worldH, gradSize, gradOffset);
                levelBoundsBaked = true;
                bakedWorldW = worldW;
                bakedWorldH = worldH;
            }

            // Draw baked gradient edge strips
            g.drawImage(bakedEdgeTop, -gradOffset + gradSize, -gradOffset, null);
            g.drawImage(bakedEdgeBottom, -gradOffset + gradSize, worldH + gradOffset - gradSize, null);
            g.drawImage(bakedEdgeLeft, -gradOffset, -gradOffset + gradSize, null);
            g.drawImage(bakedEdgeRight, worldW + gradOffset - gradSize, -gradOffset + gradSize, null);

            // Draw baked gradient corner pieces
            g.drawImage(bakedCornerTL, -gradOffset, -gradOffset, null);
            g.drawImage(bakedCornerTR, worldW + gradOffset - gradSize, -gradOffset, null);
            g.drawImage(bakedCornerBL, -gradOffset, worldH + gradOffset - gradSize, null);
            g.drawImage(bakedCornerBR, worldW + gradOffset - gradSize, worldH + gradOffset - gradSize, null);

            // Restore original (fractional) transform for game objects
            g.setTransform(boundsTransform);

        }

        

        // Restore original transform and apply inverse zoom for UI elements

        // This makes UI stay the same size regardless of game zoom

        g.setTransform(originalTransform);

        double inverseZoom = 1.0 / Game.cameraZoom;

        g.translate(width / 2, height / 2);

        g.scale(inverseZoom, inverseZoom);

        g.translate(-width / 2, -height / 2);

        

        // Apply UI parallax - all UI shifts slightly with camera for depth feel

        if (Game.enableUIParallax) {

            int uiParallaxX = (int)(cameraX * 0.0375);

            int uiParallaxY = (int)(cameraY * 0.0375);

            g.translate(uiParallaxX, uiParallaxY);

        }

        

        // Draw boss health bar at bottom - layout-aware (with pop-in animation)
        HUDLayout bossLayout = Game.hudLayout != null ? Game.hudLayout : HUDLayout.defaultLayout();
        HUDLayout.HUDElementConfig bossCfg = bossLayout.getConfig(HUDLayout.HUDElement.BOSS_HEALTH);
        // Animate boss health bar in/out
        boolean bossPresent = boss != null && bossCfg.visible && (!tutorialMode || tutorialStep >= 5);
        if (bossPresent && !lastBossPresent) {
            bossHealthBarAnim = 0.0; // Reset animation on new boss
        }
        lastBossPresent = bossPresent;
        if (bossPresent) {
            bossHealthBarAnim = Math.min(1.0, bossHealthBarAnim + 0.025); // ~40 frames to fully appear
        } else {
            bossHealthBarAnim = Math.max(0.0, bossHealthBarAnim - 0.05); // Faster fade out
        }
        if (bossHealthBarAnim > 0.0 && boss != null) {
            Composite bossOrigComposite = g.getComposite();
            
            // Pop-in animation: elastic scale + slide up + alpha fade
            float animT = (float) bossHealthBarAnim;
            float animAlpha;
            float animScale;
            float animSlideY;
            if (animT < 0.4f) {
                // Phase 1: Slide up + fade in + overshoot scale
                float t = animT / 0.4f;
                float ease = 1.0f - (1.0f - t) * (1.0f - t); // ease-out quad
                animAlpha = ease;
                animScale = 0.5f + 0.8f * ease; // 0.5 -> 1.3 (overshoot)
                animSlideY = (1.0f - ease) * 60; // slide up 60px
            } else if (animT < 0.65f) {
                // Phase 2: Settle from overshoot
                float t = (animT - 0.4f) / 0.25f;
                animAlpha = 1.0f;
                animScale = 1.3f - 0.3f * t; // 1.3 -> 1.0
                animSlideY = 0;
            } else {
                // Phase 3: Fully visible with subtle pulse
                animAlpha = 1.0f;
                animScale = 1.0f + 0.015f * (float) Math.sin(animT * Math.PI * 6);
                animSlideY = 0;
            }
            
            // Combine layout opacity with animation alpha
            float combinedAlpha = Math.max(0.01f, animAlpha * (bossCfg.opacity < 1.0f ? bossCfg.opacity : 1.0f));
            g.setComposite(RenderCache.getAlpha(combinedAlpha));

            int barWidth = UIScale.px(600);

            int barHeight = UIScale.px(40);

            

            int barX = (int)(bossCfg.xPercent * width);

            int barY = (int)(bossCfg.yPercent * height + animSlideY);
            
            // Save bounds for tutorial highlight
            if (tutorialHighlightElement == 5) {
                tutorialHLX = barX; tutorialHLY = (int)(bossCfg.yPercent * height);
                tutorialHLW = barWidth; tutorialHLH = barHeight + UIScale.px(45);
            }
            
            // Apply scale transform centered on the bar
            AffineTransform bossBarTransform = g.getTransform();
            int barCenterX = barX + barWidth / 2;
            int barCenterY = barY + (barHeight + 45) / 2;
            g.translate(barCenterX, barCenterY);
            g.scale(animScale, animScale);
            g.translate(-barCenterX, -barCenterY);

            

            // Boss name and type

            String bossName = boss.getVehicleName();

            String bossType = boss.isMegaBoss() ? "[MEGA BOSS]" : "[MINI BOSS]";

            

            // Background panel with shadow

            g.setColor(RenderCache.BLACK_100);

            g.fillRoundRect(barX + 3, barY + 3, barWidth, barHeight + UIScale.px(45), UIScale.px(15), UIScale.px(15));

            g.setColor(RenderCache.DARK_20_20_30_200);

            g.fillRoundRect(barX, barY, barWidth, barHeight + UIScale.px(45), UIScale.px(15), UIScale.px(15));

            

            // Boss type label

            g.setFont(FontPalette.get(Font.BOLD, 14));

            FontMetrics fm = g.getFontMetrics();

            Color typeColor = boss.isMegaBoss() ? RenderCache.RED_255_50_50 : RenderCache.GREEN_100_200_100;

            g.setColor(typeColor);

            g.drawString(bossType, barX + UIScale.px(10), barY + UIScale.px(18));

            

            // Boss name

            g.setFont(FONT_TINY);

            fm = g.getFontMetrics();

            g.setColor(boss.isMegaBoss() ? ColorPalette.ACCENT_YELLOW : Color.WHITE);

            g.drawString(bossName, barX + UIScale.px(10), barY + UIScale.px(38));

            

            // Health bar background

            g.setColor(RenderCache.GRAY_60);

            g.fillRoundRect(barX + UIScale.px(10), barY + UIScale.px(45), barWidth - UIScale.px(20), UIScale.px(15), UIScale.px(8), UIScale.px(8));

            

            // Health bar fill (cached gradients, rebuilt only when barX changes)
            if (cachedBossBarX != barX) {
                cachedBossBarX = barX;
                cachedHPGradMega = new GradientPaint(barX + 10, 0, HP_GRADIENT_MEGA, barX + barWidth - 10, 0, RenderCache.RED_255_100_100);
                cachedHPGradNormal = new GradientPaint(barX + 10, 0, RenderCache.GREEN_50_150_50, barX + barWidth - 10, 0, RenderCache.GREEN_100_200_100);
                int pbw = 150, pbx = barX + barWidth - pbw - 15;
                cachedPhaseGradAssault = new GradientPaint(pbx, 0, RenderCache.RED_255_50_50, pbx + pbw, 0, PHASE_ASSAULT_END);
                cachedPhaseGradRecovery = new GradientPaint(pbx, 0, PHASE_RECOVERY_START, pbx + pbw, 0, PHASE_RECOVERY_END);
            }
            g.setPaint(boss.isMegaBoss() ? cachedHPGradMega : cachedHPGradNormal);

            g.fillRoundRect(barX + UIScale.px(10), barY + UIScale.px(45), barWidth - UIScale.px(20), UIScale.px(15), UIScale.px(8), UIScale.px(8));

            

            // Add hit indicators based on boss health (6 for mini, 9 for mega)

            int maxHits = boss.getMaxHealth();

            g.setColor(RenderCache.BLACK_150);

            int segmentWidth = (barWidth - UIScale.px(20)) / maxHits;

            for (int i = 1; i < maxHits; i++) {

                int dividerX = barX + UIScale.px(10) + (segmentWidth * i);

                g.fillRect(dividerX - 1, barY + UIScale.px(45), 2, UIScale.px(15));

            }

            

            // Darken segments that have been hit

            g.setColor(RenderCache.BLACK_120);

            for (int i = 0; i < bossHitCount && i < maxHits; i++) {

                g.fillRoundRect(barX + 10 + (segmentWidth * i), barY + 45, segmentWidth, 15, 8, 8);

            }

            

            // Draw hit count text

            g.setFont(FONT_EXTRA_SMALL_12);

            g.setColor(Color.WHITE);

            String hitText = "Hits: " + bossHitCount + "/" + maxHits;

            g.drawString(hitText, barX + barWidth - 70, barY + 57);

            

            // Attack Phase indicator (Assault vs Recovery) - positioned on right side of boss bar

            int phaseBarWidth = 150;

            int phaseBarHeight = 8;

            int phaseBarX = barX + barWidth - phaseBarWidth - 15;

            int phaseBarY = barY + 22;

            

            // Phase label and icon

            g.setFont(FONT_EXTRA_SMALL_11);

            String phaseText = boss.isAssaultPhase() ? "[!] ASSAULT" : "[-] RECOVERY";

            Color phaseColor = boss.isAssaultPhase() ? RenderCache.RED_255_80_80 : RenderCache.BLUE_80_180_255;

            

            // Flash effect when phase changes

            if (boss.getPhaseFlashTimer() > 0) {

                float flashAlpha = boss.getPhaseFlashTimer() / 30f;

                phaseColor = new Color(

                    (int)(phaseColor.getRed() + (255 - phaseColor.getRed()) * flashAlpha),

                    (int)(phaseColor.getGreen() + (255 - phaseColor.getGreen()) * flashAlpha),

                    (int)(phaseColor.getBlue() + (255 - phaseColor.getBlue()) * flashAlpha)

                );

            }

            

            g.setColor(phaseColor);

            g.drawString(phaseText, phaseBarX, phaseBarY - 2);

            

            // Phase progress bar background

            g.setColor(PHASE_BAR_BG);

            g.fillRoundRect(phaseBarX, phaseBarY + 3, phaseBarWidth, phaseBarHeight, 4, 4);

            

            // Phase progress bar fill

            float phaseProgress = boss.getAttackPhaseProgress();

            int fillWidth = (int)(phaseBarWidth * phaseProgress);

            g.setPaint(boss.isAssaultPhase() ? cachedPhaseGradAssault : cachedPhaseGradRecovery);

            g.fillRoundRect(phaseBarX, phaseBarY + 3, fillWidth, phaseBarHeight, 4, 4);

            

            // Attack Section indicator - shows current attack pattern section
            String sectionName = boss.getSectionName();
            if (sectionName != null && !sectionName.isEmpty()) {
                int secBarWidth = 150;
                int secBarHeight = 8;
                int secBarX = barX + 15;
                int secBarY = barY + 22;
                
                // Section label color based on section type
                int secType = boss.getSectionType();
                Color secColor;
                Color secEndColor;
                switch (secType) {
                    case 0: secColor = new Color(180, 100, 255); secEndColor = new Color(220, 150, 255); break; // Spiral - purple
                    case 1: secColor = new Color(255, 80, 80);   secEndColor = new Color(255, 150, 100); break; // Aimed - red
                    case 2: secColor = new Color(255, 200, 50);  secEndColor = new Color(255, 255, 100); break; // Chaos - yellow
                    case 3: secColor = new Color(255, 50, 150);  secEndColor = new Color(255, 120, 200); break; // Mega - pink
                    case 4: secColor = new Color(80, 200, 120);  secEndColor = new Color(150, 255, 180); break; // Breather - green
                    case 5: secColor = new Color(255, 100, 30);  secEndColor = new Color(255, 180, 50);  break; // Finale - orange
                    default: secColor = Color.WHITE;             secEndColor = Color.GRAY; break;
                }
                
                g.setFont(FONT_EXTRA_SMALL_11);
                g.setColor(secColor);
                g.drawString(sectionName, secBarX, secBarY - 2);
                
                // Section progress bar background
                g.setColor(PHASE_BAR_BG);
                g.fillRoundRect(secBarX, secBarY + 3, secBarWidth, secBarHeight, 4, 4);
                
                // Section progress bar fill
                float secProgress = boss.getSectionProgress();
                int secFillWidth = (int)(secBarWidth * secProgress);
                g.setPaint(new GradientPaint(secBarX, 0, secColor, secBarX + secBarWidth, 0, secEndColor));
                g.fillRoundRect(secBarX, secBarY + 3, secFillWidth, secBarHeight, 4, 4);
            }

            // Health bar border

            g.setColor(RenderCache.GRAY_200);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(barX + 10, barY + 45, barWidth - 20, 15, 8, 8);

            g.setTransform(bossBarTransform);
            g.setComposite(bossOrigComposite);
        }

        

        // Restore to identity transform for remaining UI elements (no zoom, no camera offset)

        g.setTransform(IDENTITY_TRANSFORM);

        

        // Update smooth UI animations

        double targetScore = gameData.getScore();

        double targetMoney = gameData.getTotalMoney() + gameData.getRunMoney();

        displayedScore += (targetScore - displayedScore) * 0.12;

        displayedMoney += (targetMoney - displayedMoney) * 0.12;

        

        // Detect combo increase for pulse effect

        if (comboSystem != null && comboSystem.getCombo() > lastComboCount) {

            comboPulseScale = 1.4;

            lastComboCount = comboSystem.getCombo();

        } else if (comboSystem != null && comboSystem.getCombo() < lastComboCount) {

            lastComboCount = comboSystem.getCombo();

        }

        // Decay pulse

        if (comboPulseScale > 1.0) {

            comboPulseScale = Math.max(1.0, comboPulseScale - 0.03);

        }

        

        // [UI HUD and top-right stack moved below overlay for proper layering]

        

        // Draw combo milestone announcements at fixed spawn position (where player was)

        if (comboSystem != null && comboSystem.getCurrentAnnouncement() != null) {

            String announcement = comboSystem.getCurrentAnnouncement();

            boolean isBossHP = announcement.startsWith("BOSS HP:");

            float announcementProgress = (float)(comboSystem.getAnnouncementTimer() / 90.0); // 1.0 = just started, 0.0 = ending

            float lifeProgress = 1.0f - announcementProgress; // 0.0 = just started, 1.0 = ending

            

            // Smooth elastic pop-in effect (overshoots then settles)

            float scale;

            if (isBossHP) {
                // Smaller, snappier pop for boss HP
                if (lifeProgress < 0.1f) {
                    float t = lifeProgress / 0.1f;
                    scale = 1.15f * easeOutBack(t);
                } else if (lifeProgress < 0.18f) {
                    float t = (lifeProgress - 0.1f) / 0.08f;
                    scale = 1.15f - 0.15f * t;
                } else {
                    scale = 1.0f + 0.02f * (float)Math.sin(lifeProgress * Math.PI * 4);
                }
            } else {

            if (lifeProgress < 0.15f) {

                // Pop in with overshoot (0 -> 1.3 in first 15%)

                float t = lifeProgress / 0.15f;

                scale = 1.3f * easeOutBack(t);

            } else if (lifeProgress < 0.25f) {

                // Settle down from overshoot (1.3 -> 1.0)

                float t = (lifeProgress - 0.15f) / 0.1f;

                scale = 1.3f - 0.3f * t;

            } else {

                // Normal size with gentle pulse

                scale = 1.0f + 0.05f * (float)Math.sin(lifeProgress * Math.PI * 4);

            }
            } // end else (non-boss-HP scale)

            

            // Smooth alpha: fade in quick, hold, then fade out

            float alpha;

            if (lifeProgress < 0.1f) {

                alpha = lifeProgress / 0.1f; // Quick fade in

            } else if (lifeProgress < 0.7f) {

                alpha = 1.0f; // Hold full opacity

            } else {

                alpha = 1.0f - (lifeProgress - 0.7f) / 0.3f; // Smooth fade out

            }

            

            // Gentle sway rotation (like hanging text) â€” reduced for boss HP

            float swayAngle = (float)(Math.sin(lifeProgress * Math.PI * 6) * Math.PI / (isBossHP ? 48 : 24));

            // Dampen sway as it fades out

            swayAngle *= Math.min(1.0f, (1.0f - lifeProgress) * 2);

            

            // Smooth float upward with easing â€” less float for boss HP

            float floatUp = easeOutQuad(lifeProgress) * (isBossHP ? 40 : 80);

            

            AffineTransform announcementTransform = g.getTransform();

            // Use fixed spawn position (where player was when announcement triggered)

            int textX = (int)comboSystem.getAnnouncementSpawnX();

            int textY = (int)(comboSystem.getAnnouncementSpawnY() - 60 - floatUp);

            // Clamp to screen bounds so text never goes off-screen
            int margin = isBossHP ? 80 : 150;
            textX = Math.max(margin, Math.min(width - margin, textX));
            textY = Math.max(margin, Math.min(height - 40, textY));

            

            // Apply transforms: translate, rotate around text center, then scale

            g.translate(textX, textY);

            g.rotate(swayAngle);

            g.scale(scale, scale);

            

            // Use Arial Black for boss HP, Inlanders title font for other announcements

            g.setFont(isBossHP ? FontPalette.get(Font.BOLD, 28) : FONT_TITLE);

            FontMetrics announceFm = g.getFontMetrics();

            int announceWidth = announceFm.stringWidth(announcement);

            int textOffsetX = -announceWidth / 2;

            int textOffsetY = announceFm.getAscent() / 3;

            

            // Draw glow/outline (AlphaComposite instead of per-frame Color allocs)
            Composite annSavedComp = g.getComposite();
            g.setComposite(RenderCache.getAlpha((float)(alpha * 100.0 / 255.0)));
            g.setColor(Color.BLACK);
            for (int ox = -3; ox <= 3; ox++) {
                for (int oy = -3; oy <= 3; oy++) {
                    if (ox != 0 || oy != 0) {
                        g.drawString(announcement, textOffsetX + ox, textOffsetY + oy);
                    }
                }
            }
            
            // Draw shadow
            g.setComposite(RenderCache.getAlpha((float)(alpha * 200.0 / 255.0)));
            g.drawString(announcement, textOffsetX + 4, textOffsetY + 4);
            
            // Draw main text (cached opaque Colors + AlphaComposite for fade)
            g.setComposite(RenderCache.getAlpha((float)alpha));
            Color announceColor;
            if (announcement.startsWith("BOSS HP:")) {
                announceColor = ANNOUNCE_BOSS_HP;
            } else {
                announceColor = switch(announcement) {
                case "NICE!" -> ANNOUNCE_NICE;
                case "GREAT!" -> ANNOUNCE_GREAT;
                case "AMAZING!" -> ANNOUNCE_AMAZING;
                case "INCREDIBLE!" -> ANNOUNCE_INCREDIBLE;
                case "LEGENDARY!" -> ANNOUNCE_LEGENDARY;
                case "GODLIKE!" -> ANNOUNCE_GODLIKE;
                case "IMPOSSIBLE!", "CRITICAL HIT!", "BOSS DEFEATED!", "EXTRA MISSILE!", "PERFECT!" -> ANNOUNCE_GOLD;
                case "MISSILE USED!" -> ANNOUNCE_GREEN;
                case "LAST MISSILE!" -> ANNOUNCE_GODLIKE;
                case "DISABLED!" -> ANNOUNCE_GRAY;
                case "KEEP MOVING!" -> ANNOUNCE_RED_PINK;
                default -> Color.WHITE;
                };
            }
            g.setColor(announceColor);
            g.drawString(announcement, textOffsetX, textOffsetY);
            
            // Add shine highlight on top half
            g.setComposite(RenderCache.getAlpha((float)(alpha * 80.0 / 255.0)));
            g.setColor(Color.WHITE);
            g.drawString(announcement, textOffsetX, textOffsetY - 1);
            g.setComposite(annSavedComp);
            
            g.setTransform(announcementTransform);

        }

        

        

        

        

        // Draw "Press SPACE to skip" text during intro animation

        if (introPanActive) {

            g.setFont(FONT_TINY);

            g.setColor(RenderCache.WHITE_180);

            int textY = height - 30;

            

            // Draw shadow for better visibility

            g.setColor(RenderCache.BLACK_150);

            drawPromptWithIcons(g, width / 2 + 2, textY + 2,

                "Press ", KeyBindManager.Action.CONFIRM, " to skip");

            g.setColor(RenderCache.WHITE_180);

            drawPromptWithIcons(g, width / 2, textY,

                "Press ", KeyBindManager.Action.CONFIRM, " to skip");

        }

        

        

        // Draw damage numbers

        if (damageNumbers != null) {

            for (DamageNumber dmg : damageNumbers) {

                dmg.draw(g);

            }

        }


        // Draw boss name banner during intro pan
        if (introPanActive && bossIntroText != null && boss != null) {
            // === Full-screen darkened overlay with vignette ===
            g.setComposite(RenderCache.getAlpha(0.45f));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            // Vignette â€” darker edges
            for (int v = 0; v < 40; v++) {
                float vAlpha = 0.015f * (40 - v);
                g.setComposite(RenderCache.getAlpha(vAlpha));
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, v); // top
                g.fillRect(0, height - v, width, v); // bottom
                g.fillRect(0, 0, v, height); // left
                g.fillRect(width - v, 0, v, height); // right
            }

            // === Shake offset for the banner ===
            double shakeT = time * 60.0; // frame-ish counter from time
            boolean isFinalBossIntro = boss != null && boss.isFinalBoss();
            int shakeAmt = isFinalBossIntro ? 10 : 6;
            int shakeX = (int)((Math.random() - 0.5) * shakeAmt);
            int shakeY = (int)((Math.random() - 0.5) * (shakeAmt - 1));

            // === Horizontal red scan lines across background ===
            g.setComposite(RenderCache.getAlpha(0.08f));
            g.setColor(INTRO_SCAN_RED);
            for (int sy = 0; sy < height; sy += 4) {
                g.fillRect(0, sy, width, 1);
            }

            // === Red warning stripe bars (top and bottom) ===
            int stripeH = 3;
            g.setComposite(RenderCache.getAlpha(0.7f));
            g.setColor(INTRO_STRIPE_RED);
            g.fillRect(0, height / 2 - 55 + shakeY, width, stripeH);
            g.fillRect(0, height / 2 + 50 + shakeY, width, stripeH);

            // === Banner panel with shake ===
            // Measure boss name first to scale panel width
            int nameFs = bossIntroText.length() > 14 ? 28 : bossIntroText.length() > 10 ? 34 : 40;
            g.setFont(FontPalette.get(java.awt.Font.BOLD, nameFs));
            int measuredNameW = g.getFontMetrics().stringWidth(bossIntroText);
            
            // Build subtext for endless mode
            String introSubText = null;
            if (gameData != null && gameData.isInEndlessMode()) {
                introSubText = "Prestige " + gameData.getEndlessPrestige() + " \u2022 Level " + gameData.getEndlessCurrentLevel();
            }
            
            int minPanelW = measuredNameW + 80; // padding around boss name
            int panelW = Math.max(Math.min(width - 60, 500), minPanelW);
            int panelH = introSubText != null ? 110 : 90;
            int panelX = (width - panelW) / 2 + shakeX;
            int panelY = height / 2 - panelH / 2 - 3 + shakeY;

            // Outer glow (bigger for final boss)
            int glowLayers = isFinalBossIntro ? 5 : 3;
            for (int gl = glowLayers; gl >= 1; gl--) {
                g.setComposite(RenderCache.getAlpha((isFinalBossIntro ? 0.15f : 0.12f) * gl));
                g.setColor(INTRO_GLOW_RED);
                g.fillRoundRect(panelX - gl * 3, panelY - gl * 3,
                    panelW + gl * 6, panelH + gl * 6, 14, 14);
            }

            // Dark panel fill
            g.setComposite(RenderCache.getAlpha(0.85f));
            g.setColor(INTRO_PANEL_BG);
            g.fillRoundRect(panelX, panelY, panelW, panelH, 10, 10);

            // Red border with double stroke
            g.setComposite(RenderCache.getAlpha(0.95f));
            g.setColor(INTRO_BORDER_BRIGHT);
            g.drawRoundRect(panelX, panelY, panelW, panelH, 10, 10);
            g.setColor(INTRO_BORDER_DARK);
            g.drawRoundRect(panelX + 2, panelY + 2, panelW - 4, panelH - 4, 8, 8);

            // Inner highlight line at top of panel
            g.setComposite(RenderCache.getAlpha(0.15f));
            g.setColor(INTRO_HIGHLIGHT);
            g.fillRect(panelX + 10, panelY + 1, panelW - 20, 1);

            // === "WARNING" subtitle ===
            g.setComposite(AlphaComposite.SrcOver);
            g.setFont(FontPalette.get(java.awt.Font.BOLD, 14));
            FontMetrics wfm = g.getFontMetrics();
            String warningText = isFinalBossIntro ?
                "\u26A0  F I N A L   B O S S  \u26A0" : "\u26A0  W A R N I N G  \u26A0";
            int warnW = wfm.stringWidth(warningText);
            int warnX = panelX + panelW / 2 - warnW / 2;
            int warnY = panelY + 24;
            // Shadow
            g.setColor(INTRO_SHADOW_200);
            g.drawString(warningText, warnX + 1, warnY + 1);
            // Pulsing red glow text â€” use AlphaComposite with base color
            float pulse = (float)(0.7 + 0.3 * Math.sin(time * 8.0));
            g.setComposite(RenderCache.getAlpha(pulse));
            g.setColor(INTRO_WARN_BASE);
            g.drawString(warningText, warnX, warnY);

            // === Boss name in large bold font ===
            int fontSize = nameFs;
            g.setFont(FontPalette.get(java.awt.Font.BOLD, fontSize));
            FontMetrics nfm = g.getFontMetrics();
            int nameW = nfm.stringWidth(bossIntroText);
            int nameX = panelX + panelW / 2 - nameW / 2;
            int nameY = panelY + 65;

            // Text glow behind name
            g.setComposite(RenderCache.getAlpha(0.25f));
            g.setColor(boss.isMegaBoss() ? INTRO_GLOW_MEGA : INTRO_GLOW_MINI);
            g.drawString(bossIntroText, nameX - 1, nameY);
            g.drawString(bossIntroText, nameX + 1, nameY);
            g.drawString(bossIntroText, nameX, nameY - 1);
            g.drawString(bossIntroText, nameX, nameY + 1);

            // Shadow
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(INTRO_NAME_SHADOW);
            g.drawString(bossIntroText, nameX + 2, nameY + 2);

            // Main name text
            g.setColor(boss.isMegaBoss() ? INTRO_NAME_MEGA : Color.WHITE);
            g.drawString(bossIntroText, nameX, nameY);
            
            // === Endless mode subtext (Prestige # - Level #) ===
            if (introSubText != null) {
                g.setFont(FontPalette.get(java.awt.Font.BOLD, 16));
                FontMetrics sfm = g.getFontMetrics();
                int subW = sfm.stringWidth(introSubText);
                int subX = panelX + panelW / 2 - subW / 2;
                int subY = nameY + 22;
                g.setComposite(RenderCache.getAlpha(0.5f));
                g.setColor(Color.BLACK);
                g.drawString(introSubText, subX + 1, subY + 1);
                float subPulse = (float)(0.6 + 0.4 * Math.sin(time * 5.0));
                g.setComposite(RenderCache.getAlpha(subPulse));
                g.setColor(new Color(180, 130, 255));
                g.drawString(introSubText, subX, subY);
                g.setComposite(AlphaComposite.SrcOver);
            }

            // === Small decorative dashes by the name ===
            g.setComposite(RenderCache.getAlpha(0.5f));
            g.setColor(INTRO_DASH_RED);
            int dashY = nameY - nfm.getAscent() / 2 + 4;
            g.fillRect(panelX + 12, dashY, 20, 2);
            g.fillRect(panelX + panelW - 32, dashY, 20, 2);

            g.setComposite(AlphaComposite.SrcOver);
        }


        // Draw Can't Stop contract warning

        if (riskContractType == 4 && riskContractActive && stoppedMovingTimer > 0 && !isPaused) {

            int gracePeriod = 60; // Match STOPPED_GRACE_PERIOD from Game

            double timeRemaining = (gracePeriod - stoppedMovingTimer) / 60.0; // Convert to seconds

            float dangerLevel = (float) stoppedMovingTimer / gracePeriod;

            

            // Pulsing warning bar at bottom of screen

            int barWidth = 400;

            int barHeight = 40;

            int barX = (width - barWidth) / 2;

            int barY = height - 100;

            

            // Background

            g.setColor(RISK_BAR_BG);

            g.fillRoundRect(barX, barY, barWidth, barHeight, 10, 10);

            

            // Progress bar (red, gets more intense as time runs out)

            int progressWidth = (int) (barWidth * (1.0 - dangerLevel));
            // Use pre-computed warning color array to avoid per-frame new Color()
            int wIdx = Math.min(RISK_WARNING_STEPS - 1, Math.max(0, (int)(dangerLevel * (RISK_WARNING_STEPS - 1))));
            g.setColor(RISK_WARNING_COLORS[wIdx]);

            g.fillRoundRect(barX, barY, progressWidth, barHeight, 10, 10);

            

            // Border

            g.setColor(RenderCache.GRAY_200);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(barX, barY, barWidth, barHeight, 10, 10);

            

            // Warning text with pulsing effect

            g.setFont(FONT_SMALL);

            float textPulse = (float) (0.7 + 0.3 * Math.sin(time * 8 * (1 + dangerLevel * 2)));
            Composite savedTextComp = g.getComposite();
            g.setComposite(RenderCache.getAlpha(textPulse));
            g.setColor(Color.WHITE);

            String warningText = dangerLevel < 0.5 ? "KEEP MOVING!" : 

                                dangerLevel < 0.8 ? "!! MOVE NOW!" : "!! MOVE !! !!";

            FontMetrics fm = g.getFontMetrics();

            g.drawString(warningText, barX + (barWidth - fm.stringWidth(warningText)) / 2, barY + 26);
            g.setComposite(savedTextComp); // Restore after pulsing text

            

            // Time remaining text

            if (timeRemaining > 0) {

                g.setFont(FontPalette.get(Font.PLAIN, 14));

                g.setColor(RISK_TIME_TEXT);

                String timeText = String.format("%.1fs", timeRemaining);

                g.drawString(timeText, barX + barWidth + 10, barY + 26);

            }

        }

        

        // Draw pause menu

        if (isPaused) {

            // Dark overlay - extended beyond screen to prevent shake edge visibility

            int shakeMargin = 250; // Extends all directions equally

            g.setColor(RenderCache.BLACK_200);

            g.fillRect(-shakeMargin, -shakeMargin, width + shakeMargin * 2, height + shakeMargin * 2);

            

            // Pause title

            g.setFont(FONT_TITLE_LARGE);

            g.setColor(Color.WHITE);

            String pauseText = showcasePauseMode ? "SHOWCASE PAUSED" : "PAUSED";

            FontMetrics fm = g.getFontMetrics();

            g.drawString(pauseText, (width - fm.stringWidth(pauseText)) / 2, height / 3);

            

            // Menu options using UIButtons

            int buttonY = height / 2 - UIScale.px(30);

            for (int i = 0; i < activePauseButtonCount; i++) {

                pauseButtons[i].setPosition((width - UIScale.px(300)) / 2, buttonY + i * UIScale.px(80));

                pauseButtons[i].update(i == selectedPauseItem, time);

                pauseButtons[i].draw(g, time);

            }

        }

        

        // Draw unpause countdown (centered dramatic style)

        if (unpauseCountdownActive) {

            // Dark overlay

            g.setColor(RenderCache.BLACK_150);

            g.fillRect(0, 0, width, height);

            

            // Calculate countdown number (3, 2, 1, GO!)

            int secondsRemaining = (unpauseCountdownTimer > 0) ? (int)(((unpauseCountdownTimer - 1) / 60) + 1) : 0;

            String countdownText;

            Color countdownColor;

            

            if (secondsRemaining > 0) {

                countdownText = String.valueOf(secondsRemaining);

                countdownColor = ColorPalette.TEXT_GOLD; // Gold

            } else {

                countdownText = "GO!";

                countdownColor = ColorPalette.SUCCESS_GREEN; // Green

            }

            

            // Scale animation (quick pop-in on each tick)

            int frameInTick = (int)(unpauseCountdownTimer % 60);

            float tickProgress = frameInTick / 60.0f;

            float scale = tickProgress > 0.8f ? 

                (1.0f - tickProgress) / 0.2f * 0.5f + 1.0f : 

                Math.min(1.5f, 1.5f - (0.8f - tickProgress) * 0.25f);

            float alpha = 1.0f;

            

            AffineTransform countdownTransform = g.getTransform();

            int centerX = width / 2;

            int centerY = height / 3;

            

            g.translate(centerX, centerY);

            g.scale(scale, scale);

            g.translate(-centerX, -centerY);

            

            // Draw shadow

            g.setFont(FontPalette.get(Font.BOLD, 120));

            FontMetrics countdownFm = g.getFontMetrics();

            int textWidth = countdownFm.stringWidth(countdownText);

            

            g.setColor(RenderCache.BLACK_180);

            g.drawString(countdownText, centerX - textWidth / 2 + 4, centerY + 4);

            

            // Main text

            g.setColor(countdownColor);

            g.drawString(countdownText, centerX - textWidth / 2, centerY);

            

            g.setTransform(countdownTransform);

            

            // Subtitle (not scaled)

            g.setFont(FontPalette.getDisplay(Font.PLAIN, 32));

            String subtitleText = "Get Ready!";

            FontMetrics subtitleFm = g.getFontMetrics();

            g.setColor(ColorPalette.TEXT_PRIMARY);

            g.drawString(subtitleText, (width - subtitleFm.stringWidth(subtitleText)) / 2, centerY + 80);

        }

        

        

        // Draw overlay on top of everything (not affected by camera shake)

        if (overlayLoaded && overlayImage != null) {

            g.drawImage(overlayImage, 0, 0, width, height, null);

        }

        // ==========================================
        // TOP-RIGHT UI STACK (above overlay for visibility)
        // Uses cumulative topRightY for proper stacking
        // ==========================================

        // === HUD LAYOUT: Read layout config (always use Game's live reference) ===
        HUDLayout activeLayout = Game.hudLayout != null ? Game.hudLayout : HUDLayout.defaultLayout();
        Composite originalComposite = g.getComposite();

        // Draw UI with better contrast (top-left HUD - INFO PANEL)
        HUDLayout.HUDElementConfig infoCfg = activeLayout.getConfig(HUDLayout.HUDElement.INFO_PANEL);
        if (infoCfg.visible) {
            if (infoCfg.opacity < 1.0f) {
                g.setComposite(RenderCache.getAlpha(Math.max(0.01f, infoCfg.opacity)));
            }
            int infoX = (int)(infoCfg.xPercent * width);
            int infoY = (int)(infoCfg.yPercent * height);
            
            // Determine if endless mode adds an extra line
            boolean endlessHud = gameData != null && gameData.isInEndlessMode() && !tutorialMode;
            int extraShift = endlessHud ? UIScale.px(17) : 0;
            
            g.setColor(RenderCache.BLACK_150);
            g.fillRoundRect(infoX, infoY, UIScale.px(280), UIScale.px(140) + extraShift, UIScale.px(10), UIScale.px(10));

            g.setColor(Color.WHITE);
            g.setFont(FONT_MEDIUM_BOLD);
            String levelLabel;
            if (tutorialMode) {
                levelLabel = "Tutorial";
            } else if (endlessHud) {
                levelLabel = "Endless Mode";
            } else {
                levelLabel = "Level: " + level;
            }
            g.drawString(levelLabel, infoX + UIScale.px(10), infoY + UIScale.px(25));
            
            // Endless subtext (prestige & level) on second line
            if (endlessHud) {
                g.setFont(FONT_INFO);
                g.setColor(new Color(180, 130, 255));
                String endlessSub = "P" + gameData.getEndlessPrestige() + " \u2022 Lv " + gameData.getEndlessCurrentLevel();
                g.drawString(endlessSub, infoX + UIScale.px(10), infoY + UIScale.px(42));
                g.setColor(Color.WHITE);
                g.setFont(FONT_MEDIUM_BOLD);
            }
            
            g.drawString("Score: " + (int)displayedScore, infoX + UIScale.px(10), infoY + UIScale.px(55) + extraShift);
            g.drawString("Money: $" + (int)displayedMoney, infoX + UIScale.px(10), infoY + UIScale.px(85) + extraShift);

            // Display timer and FPS
            g.setFont(FONT_INFO);
            int minutes = (int)(gameTime / 60);
            int seconds = (int)(gameTime % 60);
            int milliseconds = (int)((gameTime % 1) * 100);
            String timeStr = String.format("Time: %d:%02d.%02d", minutes, seconds, milliseconds);
            g.drawString(timeStr, infoX + UIScale.px(10), infoY + UIScale.px(110) + extraShift);
            g.drawString("FPS: " + fps, infoX + UIScale.px(10), infoY + UIScale.px(135) + extraShift);
            g.setComposite(originalComposite);
        }

        // Top-right UI stack with cumulative Y positioning
        // === HUD LAYOUT: stack vs individual mode ===
        boolean hudStackMode = activeLayout.isStackMode();
        HUDLayout.HUDElementConfig dodgeCfg = activeLayout.getConfig(HUDLayout.HUDElement.DODGE_COUNTER);
        // Stack origin: use dodge counter's position as the stack anchor
        int stackOriginX = hudStackMode ? (int)(dodgeCfg.xPercent * width) : 0;
        int stackOriginY = hudStackMode ? (int)(dodgeCfg.yPercent * height) : 0;
        int topRightY = stackOriginY;

        // 1. Dodge combo counter
        if (showCombo && dodgeCombo > 1) {
            if (dodgeCfg.visible) {
                int dcX, dcY;
                if (hudStackMode) {
                    dcX = stackOriginX;
                    dcY = topRightY;
                } else {
                    dcX = (int)(dodgeCfg.xPercent * width);
                    dcY = (int)(dodgeCfg.yPercent * height);
                }
                if (dodgeCfg.opacity < 1.0f) {
                    g.setComposite(RenderCache.getAlpha(Math.max(0.01f, dodgeCfg.opacity)));
                }
                g.setColor(RenderCache.BLACK_150);
                g.fillRoundRect(dcX, dcY, 200, 60, 10, 10);

                AffineTransform comboTransform = g.getTransform();
                int comboX = dcX + 100;
                int comboCenterY = dcY + 40;
                g.translate(comboX, comboCenterY);
                g.scale(comboPulseScale, comboPulseScale);
                g.translate(-comboX, -comboCenterY);

                g.setColor(ColorPalette.SUCCESS_GREEN);
                g.setFont(FONT_LARGE_32);
                String dodgeComboText = "COMBO x" + dodgeCombo;
                FontMetrics comboFm = g.getFontMetrics();
                g.drawString(dodgeComboText, dcX + 5 + (190 - comboFm.stringWidth(dodgeComboText)) / 2, comboCenterY);

                g.setTransform(comboTransform);
                g.setComposite(originalComposite);
            }
            if (hudStackMode) topRightY += 65;
        }

        // 2. Close call / perfect dodge indicators
        HUDLayout.HUDElementConfig ccCfg = activeLayout.getConfig(HUDLayout.HUDElement.CLOSE_CALL_INDICATOR);
        if (comboSystem != null && (comboSystem.getCloseCallCount() > 0 || comboSystem.getPerfectDodgeCount() > 0)) {
            if (ccCfg.visible) {
                int ccX, ccY;
                if (hudStackMode) {
                    ccX = stackOriginX + 10;
                    ccY = topRightY;
                } else {
                    ccX = (int)(ccCfg.xPercent * width);
                    ccY = (int)(ccCfg.yPercent * height);
                }
                if (ccCfg.opacity < 1.0f) {
                    g.setComposite(RenderCache.getAlpha(Math.max(0.01f, ccCfg.opacity)));
                }
                g.setFont(FontPalette.get(Font.BOLD, 14));
                int ccOffY = 0;
                if (comboSystem.getPerfectDodgeCount() > 0) {
                    g.setColor(ColorPalette.ACCENT_YELLOW);
                    g.drawString("\u2721 PERFECT x" + comboSystem.getPerfectDodgeCount(), ccX, ccY + 12 + ccOffY);
                    ccOffY += 18;
                }
                if (comboSystem.getCloseCallCount() > 0) {
                    g.setColor(ColorPalette.SUCCESS_GREEN);
                    g.drawString("\u22C6 CLOSE x" + comboSystem.getCloseCallCount(), ccX, ccY + 12 + ccOffY);
                    ccOffY += 18;
                }
                g.setComposite(originalComposite);
                if (hudStackMode) topRightY += ccOffY;
            }
        }

        // 3. Active item UI
        HUDLayout.HUDElementConfig itemCfg = activeLayout.getConfig(HUDLayout.HUDElement.ACTIVE_ITEM);
        equippedItem = gameData.getEquippedItem();
        if (equippedItem != null && itemCfg.visible && (!tutorialMode || tutorialStep >= 4)) {
            int itemUIX, itemUIY;
            if (hudStackMode) {
                itemUIX = stackOriginX;
                itemUIY = topRightY;
            } else {
                itemUIX = (int)(itemCfg.xPercent * width);
                itemUIY = (int)(itemCfg.yPercent * height);
            }
            int itemUIW = 200;
            int itemUIH = 80;
            // Save bounds for tutorial highlight
            if (tutorialHighlightElement == 4) {
                tutorialHLX = itemUIX; tutorialHLY = itemUIY;
                tutorialHLW = itemUIW; tutorialHLH = itemUIH;
            }
            if (itemCfg.opacity < 1.0f) {
                g.setComposite(RenderCache.getAlpha(Math.max(0.01f, itemCfg.opacity)));
            }
            // Determine if any popup/flash is active for glow effect

            boolean popupGlow = itemReadyFlickerTimer > 0 || itemCompleteFlashTimer > 0

                || achievementFlashTimer > 0 || bossHitFlashTimer > 0 || countdownFlashTimer > 0;

            

            // Background

            g.setColor(RenderCache.BLACK_150);

            g.fillRoundRect(itemUIX, itemUIY, itemUIW, itemUIH, 10, 10);

            

            // Glow border when popup events are active or item is ready

            if (popupGlow || equippedItem.canActivate()) {

                // Save/restore composite instead of g.create() to avoid Graphics2D clone

                Composite savedGlowComposite = g.getComposite();

                Stroke savedGlowStroke = g.getStroke();

                Color glowColor;

                float glowAlpha;

                if (itemReadyFlickerTimer > 0) {

                    glowColor = ColorPalette.SUCCESS_GREEN;

                    glowAlpha = Math.min(0.8f, (float)itemReadyFlickerTimer / 20.0f);

                } else if (itemCompleteFlashTimer > 0) {

                    glowColor = RenderCache.BLUE_80_180_255;

                    glowAlpha = Math.min(0.8f, (float)itemCompleteFlashTimer / 15.0f);

                } else if (achievementFlashTimer > 0) {

                    glowColor = ColorPalette.TEXT_GOLD;

                    glowAlpha = Math.min(0.7f, (float)achievementFlashTimer / 20.0f);

                } else if (bossHitFlashTimer > 0) {

                    glowColor = RenderCache.RED_255_80_80;

                    glowAlpha = Math.min(0.6f, (float)bossHitFlashTimer / 15.0f);

                } else if (equippedItem.canActivate()) {

                    glowColor = ColorPalette.SUCCESS_GREEN;

                    glowAlpha = (float)(0.3 + 0.2 * Math.sin(time * 4));

                } else {

                    glowColor = Color.WHITE;

                    glowAlpha = 0.3f;

                }

                g.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, glowAlpha))));

                g.setColor(glowColor);

                g.setStroke(RenderCache.getStroke(2.5f));

                g.drawRoundRect(itemUIX, itemUIY, itemUIW, itemUIH, 10, 10);

                g.setComposite(savedGlowComposite);

                g.setStroke(savedGlowStroke);

            }

            

            // Item name

            g.setFont(FONT_SMALL);

            if (equippedItem.canActivate()) {

                g.setColor(ColorPalette.SUCCESS_GREEN); // Green when ready

            } else if (equippedItem.isActive()) {

                g.setColor(ColorPalette.TEXT_GOLD); // Yellow when active

            } else {

                g.setColor(RenderCache.GRAY_150); // Gray when on cooldown

            }

            g.drawString(equippedItem.getName(), itemUIX + 10, itemUIY + 25);

            

            // Cooldown bar

            g.setColor(RenderCache.GRAY_60);

            g.fillRect(itemUIX + 10, itemUIY + 35, 180, 15);

            

            if (equippedItem.isActive()) {

                // Active duration bar (yellow)

                float activePercent = (float)equippedItem.getActiveTimer() / (float)equippedItem.getActiveDuration();

                g.setColor(ColorPalette.TEXT_GOLD);

                g.fillRect(itemUIX + 10, itemUIY + 35, (int)(180 * activePercent), 15);

            } else {

                // Cooldown progress bar (green)

                float cooldownPercent = equippedItem.getCooldownPercent();

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.fillRect(itemUIX + 10, itemUIY + 35, (int)(180 * cooldownPercent), 15);

            }

            

            // Key hint - left-aligned inside the box to prevent overflow

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(Color.WHITE);

            String keyHint = equippedItem.canActivate() ? null : 

                           equippedItem.isActive() ? "ACTIVE" :

                           String.format("%.1fs", equippedItem.getCurrentCooldown() / 60.0);

            if (keyHint != null) {
                g.drawString(keyHint, itemUIX + 10, itemUIY + 68);
            } else {
                // Left-align instead of center to keep text inside the box
                drawLeftAlignedPromptWithIcons(g, itemUIX + 10, itemUIY + 68, "Press ", KeyBindManager.Action.USE_ITEM, "");
            }

            g.setComposite(originalComposite);
            if (hudStackMode) topRightY += 85;
        }

        // 4. Missile bar indicator (layout-aware with style variant)
        HUDLayout.HUDElementConfig missileCfg = activeLayout.getConfig(HUDLayout.HUDElement.MISSILE_BAR);
        if (missileCfg.visible && !introPanActive && (!tutorialMode || tutorialStep >= 3)) {
            if (missileCfg.opacity < 1.0f) {
                g.setComposite(RenderCache.getAlpha(Math.max(0.01f, missileCfg.opacity)));
            }

            int currentMissiles = gameData.getMissiles();
            int baseMissiles = gameData.getBaseMissiles();
            int totalSlots = Math.max(baseMissiles, currentMissiles);

            if (missileCfg.styleVariant == 1) {
                // === HORIZONTAL MISSILE BAR (classic style) ===
                int missileUIX = (int)(missileCfg.xPercent * width);
                int missileUIY = (int)(missileCfg.yPercent * height);
                // Save bounds for tutorial highlight
                if (tutorialHighlightElement == 3) {
                    tutorialHLX = missileUIX; tutorialHLY = missileUIY;
                    tutorialHLW = 200; tutorialHLH = 40;
                }
                g.setColor(RenderCache.BLACK_150);
                g.fillRoundRect(missileUIX, missileUIY, 200, 40, 10, 10);
                g.setFont(FontPalette.get(Font.BOLD, 14));
                g.setColor(ColorPalette.TEXT_PRIMARY);
                g.drawString("MISSILES", missileUIX + 10, missileUIY + 15);
                int slotStartX = missileUIX + 10;
                int slotY = missileUIY + 22;
                int slotWidth = 26;
                int slotHeight = 10;
                int slotGap = 4;
                for (int s = 0; s < totalSlots; s++) {
                    if (s < currentMissiles) {
                        g.setColor(ColorPalette.SUCCESS_GREEN);
                        g.fillRoundRect(slotStartX + s * (slotWidth + slotGap), slotY, slotWidth, slotHeight, 3, 3);
                    } else {
                        g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 120));
                        g.fillRoundRect(slotStartX + s * (slotWidth + slotGap), slotY, slotWidth, slotHeight, 3, 3);
                    }
                }
            } else {
                // === VERTICAL LEFT-EDGE MISSILE BAR (default style, styled like boss health bar) ===
                int panelWidth = 50;
                int mBarWidth = 20;
                int totalBarHeight = 480;
                int segmentHeight = totalBarHeight / Math.max(1, totalSlots);
                int panelHeight = totalBarHeight + 65;

                int barX = (int)(missileCfg.xPercent * width);
                int barY = (int)(missileCfg.yPercent * height);
                // Save bounds for tutorial highlight
                if (tutorialHighlightElement == 3) {
                    tutorialHLX = barX; tutorialHLY = barY;
                    tutorialHLW = panelWidth; tutorialHLH = panelHeight;
                }

                // Shadow
                g.setColor(RenderCache.BLACK_100);
                g.fillRoundRect(barX + 3, barY + 3, panelWidth, panelHeight, 12, 12);

                // Background panel
                g.setColor(RenderCache.DARK_20_20_30_200);
                g.fillRoundRect(barX, barY, panelWidth, panelHeight, 12, 12);

                // Label
                g.setFont(FontPalette.get(Font.BOLD, 10));
                g.setColor(ColorPalette.TEXT_PRIMARY);
                FontMetrics labelFm = g.getFontMetrics();
                String label = "MISSILES";
                int labelX = barX + (panelWidth - labelFm.stringWidth(label)) / 2;
                g.drawString(label, labelX, barY + 16);

                // Bar background
                int barStartX = barX + (panelWidth - mBarWidth) / 2;
                int barStartY = barY + 24;
                g.setColor(RenderCache.GRAY_60);
                g.fillRoundRect(barStartX, barStartY, mBarWidth, totalBarHeight, 6, 6);

                // Bar fill - bottom-to-top (cache gradient paints, rebuild only when barStartX changes)
                if (cachedMissileBarStartX != barStartX) {
                    cachedMissileBarStartX = barStartX;
                    cachedMissileGradGreen = new GradientPaint(
                        barStartX, 0, RenderCache.GREEN_50_150_50,
                        barStartX + mBarWidth, 0, MISSILE_SEG_GREEN
                    );
                    cachedMissileGradGold = new GradientPaint(
                        barStartX, 0, MISSILE_SEG_GOLD_START,
                        barStartX + mBarWidth, 0, MISSILE_SEG_GOLD_END
                    );
                }
                for (int s = 0; s < currentMissiles; s++) {
                    int segY = barStartY + totalBarHeight - (s + 1) * segmentHeight;
                    g.setPaint(s < baseMissiles ? cachedMissileGradGreen : cachedMissileGradGold);
                    g.fillRoundRect(barStartX, segY, mBarWidth, segmentHeight, 6, 6);
                }

                // Darken empty segments
                for (int s = currentMissiles; s < totalSlots; s++) {
                    int segY = barStartY + totalBarHeight - (s + 1) * segmentHeight;
                    g.setColor(RenderCache.BLACK_120);
                    g.fillRoundRect(barStartX, segY, mBarWidth, segmentHeight, 6, 6);
                }

                // Segment dividers
                g.setColor(RenderCache.BLACK_150);
                for (int s = 1; s < totalSlots; s++) {
                    int divY = barStartY + s * segmentHeight;
                    g.fillRect(barStartX, divY - 1, mBarWidth, 2);
                }

                // Bar border
                g.setColor(RenderCache.GRAY_200);
                g.setStroke(RenderCache.getStroke(2));
                g.drawRoundRect(barStartX, barStartY, mBarWidth, totalBarHeight, 6, 6);
                g.setStroke(RenderCache.getStroke(1));

                // Count text below bar
                g.setFont(FONT_EXTRA_SMALL_12);
                g.setColor(Color.WHITE);
                String countText = currentMissiles + "/" + totalSlots;
                FontMetrics countFm = g.getFontMetrics();
                int countX = barX + (panelWidth - countFm.stringWidth(countText)) / 2;
                g.drawString(countText, countX, barStartY + totalBarHeight + 18);
            }
            g.setComposite(originalComposite);
        }

        // 5. Combo display (score multiplier) - layout-aware with stack/individual mode
        HUDLayout.HUDElementConfig comboCfg = activeLayout.getConfig(HUDLayout.HUDElement.COMBO_DISPLAY);
        if (comboCfg.visible && comboSystem != null && comboSystem.getCombo() > 1 && !introPanActive) {
            int comboDispX, comboDispY;
            if (hudStackMode) {
                comboDispX = stackOriginX - 40;
                comboDispY = topRightY;
            } else {
                comboDispX = (int)(comboCfg.xPercent * width);
                comboDispY = (int)(comboCfg.yPercent * height);
            }
            if (comboCfg.opacity < 1.0f) {
                g.setComposite(RenderCache.getAlpha(Math.max(0.01f, comboCfg.opacity)));
            }

            g.setColor(RenderCache.BLACK_180);
            g.fillRoundRect(comboDispX, comboDispY, 200, 80, 15, 15);

            g.setFont(FontPalette.get(Font.BOLD, 48));
            g.setColor(ColorPalette.TEXT_GOLD);
            String comboDispText = comboSystem.getCombo() + "x";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(comboDispText, comboDispX + (200 - fm.stringWidth(comboDispText)) / 2, comboDispY + 45);

            g.setFont(FontPalette.get(Font.PLAIN, 14));
            g.setColor(ColorPalette.TEXT_PRIMARY);
            String multText = String.format("%.1fx Score", comboSystem.getMultiplier());
            fm = g.getFontMetrics();
            g.drawString(multText, comboDispX + (200 - fm.stringWidth(multText)) / 2, comboDispY + 65);

            float timeoutProgress = comboSystem.getTimeoutProgress();
            g.setColor(RenderCache.GRAY_60);
            g.fillRect(comboDispX + 10, comboDispY + 72, 180, 3);
            g.setColor(ColorPalette.SUCCESS_GREEN);
            g.fillRect(comboDispX + 10, comboDispY + 72, (int)(180 * timeoutProgress), 3);
            g.setComposite(originalComposite);
            if (hudStackMode) topRightY += 85;
        }

        // 6. Achievement notification - layout-aware with stack/individual mode
        HUDLayout.HUDElementConfig achCfg = activeLayout.getConfig(HUDLayout.HUDElement.ACHIEVEMENT_POPUP);
        if (achCfg.visible && pendingAchievements != null && !pendingAchievements.isEmpty() && achievementNotificationTimer > 0 && !isPaused) {
            Achievement ach = pendingAchievements.get(0);
            float achAlpha = (float)Math.max(0.0, Math.min(1.0, achievementNotificationTimer < 30 ? achievementNotificationTimer / 30.0 : 1.0));
            // Combine element opacity with fade alpha
            float combinedAlpha = achAlpha * Math.max(0.01f, achCfg.opacity);

            int notifX, notifY;
            if (hudStackMode) {
                notifX = stackOriginX - UIScale.px(210);
                notifY = topRightY;
            } else {
                notifX = (int)(achCfg.xPercent * width);
                notifY = (int)(achCfg.yPercent * height);
            }

            Composite _ac = g.getComposite();

            g.setComposite(RenderCache.getAlpha(combinedAlpha));
            g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 230));
            g.fillRoundRect(notifX, notifY, UIScale.px(400), UIScale.px(100), UIScale.px(15), UIScale.px(15));

            g.setFont(FONT_SMALL);
            g.setColor(ColorPalette.TEXT_GOLD);
            g.drawString("Achievement Unlocked!", notifX + UIScale.px(20), notifY + UIScale.px(30));

            g.setFont(FONT_MEDIUM_BOLD);
            g.setColor(ColorPalette.TEXT_PRIMARY);
            g.drawString(ach.getName(), notifX + UIScale.px(20), notifY + UIScale.px(60));

            g.setFont(FontPalette.get(Font.PLAIN, 14));
            g.drawString(ach.getDescription(), notifX + UIScale.px(20), notifY + UIScale.px(85));

            g.setComposite(_ac);
        }

        // Tutorial highlight overlay (drawn after HUD elements so cutout reveals them).
        // Popup and tutorial bar are drawn AFTER drawGame in Game.java, so they appear above this.
        if (tutorialMode && tutorialHighlightTimer > 0) {
            drawTutorialHighlight(g, width, height, time);
        }

        

        // Screen flash effect on player death

        if (screenFlashTimer > 0) {

            Composite _fc = g.getComposite();

            float flashAlpha = (float)screenFlashTimer / 15.0f * 0.7f; // Fade out over 15 frames

            g.setComposite(RenderCache.getAlpha(flashAlpha));

            g.setColor(Color.WHITE);

            g.fillRect(0, 0, width, height);

            g.setComposite(_fc);

        }

        

        // Final boss phase transition overlay
        if (boss != null && boss.isFinalBoss() && boss.isPhaseTransitioning()) {
            Composite _ptc = g.getComposite();
            float transProgress = boss.getPhaseTransitionProgress();
            
            // Red/orange tint that pulses
            float tintAlpha = (float)(0.15 + 0.15 * Math.sin(transProgress * Math.PI * 6));
            g.setComposite(RenderCache.getAlpha(tintAlpha));
            g.setColor(new Color(255, 60, 20));
            g.fillRect(0, 0, width, height);
            
            // Boss energy glow - expanding/contracting ring around boss
            double bScreenX = boss.getX() - cameraX;
            double bScreenY = boss.getY() - cameraY;
            int bSize = boss.getSize();
            float ringPulse = (float)(0.8 + 0.4 * Math.sin(transProgress * Math.PI * 8));
            int ringRadius = (int)(bSize * ringPulse);
            g.setStroke(RenderCache.getStroke(3.0f));
            for (int r = 0; r < 4; r++) {
                float rAlpha = (0.4f - r * 0.08f) * (float)Math.sin(transProgress * Math.PI);
                g.setComposite(RenderCache.getAlpha(Math.max(0.01f, rAlpha)));
                g.setColor(r < 2 ? new Color(255, 200, 100) : new Color(255, 100, 30));
                int rr = ringRadius + r * 15;
                g.drawOval((int)(bScreenX - rr), (int)(bScreenY - rr), rr * 2, rr * 2);
            }
            g.setStroke(RenderCache.getStroke(1.0f));
            
            // "PHASE 2" banner across screen at peak of transition (35%-75% progress)
            if (transProgress > 0.35f && transProgress < 0.75f) {
                float bannerAlpha;
                if (transProgress < 0.45f) bannerAlpha = (transProgress - 0.35f) / 0.10f;
                else if (transProgress > 0.65f) bannerAlpha = (0.75f - transProgress) / 0.10f;
                else bannerAlpha = 1.0f;
                
                // Dark backdrop strip
                g.setComposite(RenderCache.getAlpha(bannerAlpha * 0.6f));
                g.setColor(new Color(20, 0, 0));
                g.fillRect(0, height / 2 - 40, width, 80);
                
                g.setFont(FontPalette.get(java.awt.Font.BOLD, 52));
                FontMetrics ptFm = g.getFontMetrics();
                String phaseText = "P H A S E   2";
                int ptW = ptFm.stringWidth(phaseText);
                int ptX = width / 2 - ptW / 2;
                int ptY = height / 2 + ptFm.getAscent() / 2 - 5;
                
                // Shake the text
                int pShakeX = (int)((Math.random() - 0.5) * 4 * Math.sin(transProgress * Math.PI));
                int pShakeY = (int)((Math.random() - 0.5) * 3 * Math.sin(transProgress * Math.PI));
                
                // Glow behind text
                g.setComposite(RenderCache.getAlpha(bannerAlpha * 0.3f));
                g.setColor(new Color(255, 100, 30));
                for (int ox = -2; ox <= 2; ox++) {
                    for (int oy = -2; oy <= 2; oy++) {
                        if (ox != 0 || oy != 0) g.drawString(phaseText, ptX + ox + pShakeX, ptY + oy + pShakeY);
                    }
                }
                
                // Shadow
                g.setComposite(RenderCache.getAlpha(bannerAlpha * 0.8f));
                g.setColor(Color.BLACK);
                g.drawString(phaseText, ptX + 3 + pShakeX, ptY + 3 + pShakeY);
                
                // Main text - hot white-orange
                g.setComposite(RenderCache.getAlpha(bannerAlpha));
                g.setColor(new Color(255, 220, 180));
                g.drawString(phaseText, ptX + pShakeX, ptY + pShakeY);
                
                // Red stripes above/below banner
                g.setComposite(RenderCache.getAlpha(bannerAlpha * 0.8f));
                g.setColor(new Color(200, 40, 20));
                g.fillRect(0, height / 2 - 42, width, 3);
                g.fillRect(0, height / 2 + 39, width, 3);
            }
            
            g.setComposite(_ptc);
        }

        // Type Purge chromatic flash effect (flashes the color of the purged bullet type)

        if (typePurgeFlashTimer > 0 && typePurgeFlashColor != null) {

            // Rapid blink effect with the bullet type's color

            boolean blinkOn = (typePurgeFlashTimer / 3) % 2 == 0;

            if (blinkOn) {

                Composite _fc = g.getComposite();

                float flashAlpha = Math.min(0.6f, (float)typePurgeFlashTimer / 30.0f * 0.6f);

                g.setComposite(RenderCache.getAlpha(flashAlpha));

                g.setColor(typePurgeFlashColor);

                g.fillRect(0, 0, width, height);

                g.setComposite(_fc);

            }

        }

        

        // Item ready flicker effect (green flicker)

        if (itemReadyFlickerTimer > 0) {

            // Flicker on/off every 4 frames

            if ((itemReadyFlickerTimer / 4) % 2 == 0) {

                Composite _fc = g.getComposite();

                float flickerAlpha = Math.min(0.3f, (float)itemReadyFlickerTimer / 20.0f * 0.3f);

                g.setComposite(RenderCache.getAlpha(flickerAlpha));

                g.setColor(ColorPalette.SUCCESS_GREEN); // Green tint

                g.fillRect(0, 0, width, height);

                g.setComposite(_fc);

            }

        }

        

        // Item complete flash effect (blue flash)

        if (itemCompleteFlashTimer > 0) {

            Composite _fc = g.getComposite();

            float flashAlpha = (float)itemCompleteFlashTimer / 15.0f * 0.5f; // Fade out over 15 frames

            g.setComposite(RenderCache.getAlpha(flashAlpha));

            g.setColor(RenderCache.BLUE_80_180_255); // Blue tint

            g.fillRect(0, 0, width, height);

            g.setComposite(_fc);

        }

        

        

        // Achievement unlocked flash effect (gold flash)

        if (achievementFlashTimer > 0) {

            Composite _fc = g.getComposite();

            float flashAlpha = (float)achievementFlashTimer / 20.0f * 0.4f; // Fade out over 20 frames

            g.setComposite(RenderCache.getAlpha(flashAlpha));

            g.setColor(ColorPalette.TEXT_GOLD); // Gold tint

            g.fillRect(0, 0, width, height);

            g.setComposite(_fc);

        }

        

        // Boss intro flash effect (red/orange flash)

        if (bossIntroFlashTimer > 0) {

            Composite _fc = g.getComposite();

            float flashAlpha = (float)bossIntroFlashTimer / 25.0f * 0.5f; // Fade out over 25 frames

            g.setComposite(RenderCache.getAlpha(flashAlpha));

            g.setColor(ColorPalette.ACCENT_ORANGE); // Red/orange tint

            g.fillRect(0, 0, width, height);

            g.setComposite(_fc);

        }

        

        // Countdown flash effect (white pulse)

        if (countdownFlashTimer > 0) {

            Composite _fc = g.getComposite();

            float flashAlpha = (float)countdownFlashTimer / 15.0f * 0.3f; // Quick fade over 15 frames

            g.setComposite(RenderCache.getAlpha(flashAlpha));

            g.setColor(Color.WHITE); // White flash

            g.fillRect(0, 0, width, height);

            g.setComposite(_fc);

        }

        

        // Boss hit flash effect (orange/amber flash â€” distinct from red death flash)

        if (bossHitFlashTimer > 0) {

            Composite _fc = g.getComposite();

            float flashAlpha = (float)bossHitFlashTimer / 18.0f * 0.6f; // Strong fade over 18 frames

            g.setComposite(RenderCache.getAlpha(flashAlpha));

            g.setColor(RenderCache.ORANGE_255_165_0); // Orange flash for boss hit (red = death)

            g.fillRect(0, 0, width, height);

            g.setComposite(_fc);

        }

        // Death red vignette effect (baked image for performance)
        if (deathFlashTimer > 0) {
            if (bakedDeathVignette == null || bakedDeathVigW != width || bakedDeathVigH != height) {
                if (bakedDeathVignette != null) bakedDeathVignette.flush();
                bakedDeathVignette = Game.createOptimalImage(width, height, true);
                Graphics2D dv = bakedDeathVignette.createGraphics();
                dv.setPaint(new java.awt.RadialGradientPaint(
                    width / 2.0f, height / 2.0f,
                    Math.max(width, height) * 0.6f,
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{RenderCache.BLACK_0, DEATH_VIGNETTE_MID, DEATH_VIGNETTE_EDGE}
                ));
                dv.fillRect(0, 0, width, height);
                dv.dispose();
                bakedDeathVigW = width; bakedDeathVigH = height;
            }
            Composite _fc = g.getComposite();
            float vignetteAlpha = Math.min(0.7f, (float)deathFlashTimer / 25.0f * 0.7f);
            g.setComposite(RenderCache.getAlpha(vignetteAlpha));
            g.drawImage(bakedDeathVignette, 0, 0, null);
            g.setComposite(_fc);
        }

        // Apply vignette effect at the end (darkens edges)

        if (Game.enableVignette) {

            applyVignette(g, width, height);

        }

    }

    

    public void drawShop(Graphics2D g, int width, int height, double time, double scrollOffset) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "SHOP", width, UIScale.px(100), ColorPalette.ACCENT_PURPLE, ColorPalette.TEXT_GOLD, time);

        

        // Show money with glowing effect

        g.setColor(ColorPalette.SUCCESS_GREEN);

        g.setFont(FontPalette.get(Font.BOLD, 36));

        String money = "Money: $" + gameData.getTotalMoney();

        FontMetrics fm = g.getFontMetrics();

        int moneyX = (width - fm.stringWidth(money)) / 2;

        // Glow effect

        g.setComposite(RenderCache.getAlpha(0.3f));

        g.fillRect(moneyX - UIScale.px(20), UIScale.px(140), fm.stringWidth(money) + UIScale.px(40), UIScale.px(50));

        g.setComposite(RenderCache.getAlpha(1.0f));

        g.drawString(money, moneyX, UIScale.px(170));

        

        // Show earnings

        g.setColor(ColorPalette.TEXT_GOLD);

        g.setFont(FONT_MEDIUM);

        String earnings = "Earned this run: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(earnings, (width - fm.stringWidth(earnings)) / 2, UIScale.px(210));

        

        // Shop items using buttons

        String[] items = shopManager.getShopItems();

        int y = UIScale.px(250);

        int selectedItem = shopManager.getSelectedShopItem();

        

        // Create a clipping region for scrollable area (stop above instructions bar)

        g.setClip(0, UIScale.px(220), width, height - UIScale.px(310));

        

        for (int i = 0; i < items.length; i++) {

            int cost = shopManager.getItemCost(i);

            boolean canAfford = gameData.getTotalMoney() >= cost || i == 0;

            boolean isMaxed = shopManager.isUpgradeMaxed(i);

            

            // Apply scroll offset to Y position

            int scrolledY = (int)(y - scrollOffset);

            int itemX = (width - UIScale.px(1050)) / 2;

            

            // Update button bounds for mouse interaction

            shopButtons[i].setPosition(itemX, scrolledY - UIScale.px(30));

            shopButtons[i].setSize(UIScale.px(900), UIScale.px(70));

            

            // Only draw if visible in the clipping region

            if (scrolledY > 180 && scrolledY < height - 60) {

                // Draw card background with shadow

                g.setColor(RenderCache.BLACK_120);

                g.fillRoundRect(itemX + UIScale.px(3), scrolledY - UIScale.px(27), UIScale.px(1050), UIScale.px(90), UIScale.px(15), UIScale.px(15));

                

                // Card background color based on state

                Color cardColor;

                if (i == 0) {

                    cardColor = ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 200); // Green for continue

                } else if (isMaxed) {

                    cardColor = RenderCache.TAN_85_75_45_200; // Dark gold for maxed - better text contrast

                } else if (!canAfford) {

                    cardColor = new Color(40, 40, 50, 200);

                } else if (i == selectedItem) {

                    cardColor = new Color(140, 120, 150, 200); // Softer purple for selected

                } else {

                    cardColor = ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 200);

                }

                

                g.setColor(cardColor);

                g.fillRoundRect(itemX, scrolledY - UIScale.px(30), UIScale.px(1050), UIScale.px(90), UIScale.px(15), UIScale.px(15));

                

                // Border glow for selected item

                if (i == selectedItem) {

                    g.setColor(RenderCache.TAN_180_170_130_140); // Softer border glow

                    g.setStroke(RenderCache.getStroke(2f));

                    g.drawRoundRect(itemX, scrolledY - UIScale.px(30), UIScale.px(1050), UIScale.px(90), UIScale.px(15), UIScale.px(15));

                    g.setStroke(RenderCache.getStroke(1f));

                }

                

                // Draw icon/symbol on the left

                String icon = getItemIcon(i);

                g.setFont(FontPalette.get(Font.BOLD, 36));

                g.setColor(canAfford ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_100);

                g.drawString(icon, itemX + UIScale.px(20), scrolledY + UIScale.px(10));

                

                // Draw item name and description

                String[] itemParts = items[i].split(" - ", 2);

                String itemName = itemParts[0];

                String itemDesc = itemParts.length > 1 ? itemParts[1] : "";

                

                g.setFont(FONT_SMALL);

                g.setColor(canAfford ? Color.WHITE : RenderCache.GRAY_120);

                g.drawString(itemName, itemX + UIScale.px(75), scrolledY - UIScale.px(5));

                

                g.setColor(canAfford ? RenderCache.GRAY_200 : RenderCache.GRAY_100);

                // Shrink font until description fits on one line
                int maxDescWidth = UIScale.px(800); // Leave room for cost on right side
                int descFontSize = 14;
                g.setFont(FontPalette.get(Font.PLAIN, descFontSize));
                if (!itemDesc.isEmpty()) {
                    while (descFontSize > 9 && g.getFontMetrics().stringWidth(itemDesc) > maxDescWidth) {
                        descFontSize--;
                        g.setFont(FontPalette.get(Font.PLAIN, descFontSize));
                    }
                }
                g.drawString(itemDesc, itemX + UIScale.px(75), scrolledY + UIScale.px(15));

                

                // Draw progress bar for all upgrades (not for Continue)

                // Use sorted order from ShopManager for correct upgrade lookup

                if (i > 0 && passiveUpgradeManager != null) {

                    int upgradeIndex = shopManager.getOriginalUpgradeIndex(i);

                    if (upgradeIndex >= 0 && upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {

                        PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);

                        int currentLevel = upgrade.getCurrentLevel();

                        int maxLevel = upgrade.getMaxLevel();

                        

                        // Special handling for Extra Missiles (last upgrade)

                        boolean isExtraMissiles = upgrade.getId().equals("health");

                        if (isExtraMissiles) {

                            // Show only extra missiles purchased (yellow ones), not base missiles

                            int extraMissiles = gameData.getMissiles() - gameData.getBaseMissiles();

                            currentLevel = Math.max(0, extraMissiles);

                        }

                        

                        int barX = itemX + UIScale.px(75);

                        int barY = scrolledY + UIScale.px(40);

                        int barWidth = UIScale.px(700);

                        int barHeight = UIScale.px(8);

                        

                        // Level text above progress bar

                        g.setFont(FONT_EXTRA_SMALL_11);

                        g.setColor(upgrade.isMaxed() || isMaxed ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_200);

                        String levelText = isExtraMissiles ? currentLevel + "/" + maxLevel + " extra missiles" : currentLevel + "/" + maxLevel;

                        g.drawString(levelText, barX, barY - UIScale.px(3));

                        

                        // Progress bar background

                        g.setColor(RenderCache.DARK_40_40_50_180);

                        g.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);

                        

                        // Progress bar fill

                        if (currentLevel > 0) {

                            double progress = (double)currentLevel / maxLevel;

                            int fillWidth = (int)(barWidth * progress);

                            

                            GradientPaint progressGrad = new GradientPaint(

                                barX, 0, ColorPalette.SUCCESS_GREEN,

                                barX + fillWidth, 0, ColorPalette.TEXT_GOLD

                            );

                            g.setPaint(progressGrad);

                            g.fillRoundRect(barX, barY, fillWidth, barHeight, 4, 4);

                        }

                    }

                }

                

                // Draw cost on the right

                if (i != 0) {

                    g.setFont(FONT_MEDIUM_BOLD);

                    if (isMaxed) {

                        g.setColor(ColorPalette.TEXT_GOLD);

                        FontMetrics costFm = g.getFontMetrics();

                        String maxedStr = "MAXED";

                        int maxedW = costFm.stringWidth(maxedStr);

                        g.drawString(maxedStr, itemX + UIScale.px(1050) - maxedW - UIScale.px(20), scrolledY + UIScale.px(10));

                    } else {

                        g.setColor(canAfford ? ColorPalette.SUCCESS_GREEN : ColorPalette.ACCENT_RED);

                        FontMetrics costFm = g.getFontMetrics();

                        String costStr = "$" + cost;

                        int costW = costFm.stringWidth(costStr);

                        g.drawString(costStr, itemX + UIScale.px(1050) - costW - UIScale.px(20), scrolledY + UIScale.px(10));

                    }

                }

            }

            

            y += UIScale.px(100);

        }

        

        // Reset clip

        g.setClip(null);

        

        // Instructions background bar to prevent overlap with shop items

        g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 240));

        g.fillRect(0, height - UIScale.px(80), width, UIScale.px(80));

        

        // Instructions

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_SMALL);

        if (tutorialMode) {
            drawPromptWithIcons(g, width / 2, height - UIScale.px(35),
                "Use ", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " to select | ", KeyBindManager.Action.CONFIRM, " to purchase | ", KeyBindManager.Action.BACK, " to quit tutorial");
        } else {
            drawPromptWithIcons(g, width / 2, height - UIScale.px(35),
                "Use ", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " or MOUSE to select | ", KeyBindManager.Action.CONFIRM, " or CLICK to purchase | ", KeyBindManager.Action.BACK, " to continue");
        }

    }

    

    public void drawGameOver(Graphics2D g, int width, int height, double time) {

        // Military themed dark background

        UITheme.drawScreenBackground(g, width, height, time);

        

        

        // Red overlay pulse

        float redPulse = (float)(0.05 + 0.03 * Math.sin(time * 2));

        g.setColor(new Color(200, 40, 40, (int)(255 * redPulse)));

        g.fillRect(0, 0, width, height);

        

        // Title â€” MISSION FAILED stamp

        UITheme.drawTitle(g, "MISSION FAILED", width, height / 2 - UIScale.px(140), ColorPalette.ACCENT_RED, ColorPalette.ACCENT_RED_BRIGHT, time);

        

        // Stencil stamp overlay â€” slam animation (randomized position/rotation)

        double elapsed = (screenEnteredTime >= 0) ? time - screenEnteredTime : 10;

        double slamDuration = 0.5;

        int stampCX = width / 2 + stampOffsetX;

        int stampCY = height / 2 - UIScale.px(140) + stampOffsetY;

        if (elapsed < slamDuration) {

            float t = (float)(elapsed / slamDuration);

            float scale = 1.0f + 3.0f * (1.0f - easeOutBack(t));

            float alpha = Math.min(1.0f, t * 3.0f);

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setComposite(RenderCache.getAlpha(alpha));

            g2.translate(stampCX, stampCY);

            g2.rotate(stampAngleOffset);

            g2.scale(scale, scale);

            g2.translate(-stampCX, -stampCY);

            UITheme.drawStencilStamp(g2, "FAILED", stampCX, stampCY, ColorPalette.ACCENT_RED, FontPalette.getDisplay(Font.BOLD, 48));

            g2.dispose();

        } else {

            Graphics2D g2 = (Graphics2D) g.create();

            g2.translate(stampCX, stampCY);

            g2.rotate(stampAngleOffset);

            g2.translate(-stampCX, -stampCY);

            UITheme.drawStencilStamp(g2, "FAILED", stampCX, stampCY, ColorPalette.ACCENT_RED, FontPalette.getDisplay(Font.BOLD, 48));

            g2.dispose();

        }

        // Play slam sound when stamp lands

        if (!slamSoundPlayed && elapsed >= slamDuration) {

            SoundManager.getInstance().playSound(SoundManager.Sound.HIT_STRONG, 0.8f);

            slamSoundPlayed = true;

        }

        

        // Run stats with military styling

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_LARGE_32);

        FontMetrics fm;

        

        // Level reached this run

        String level;

        if (gameData.isInEndlessMode()) {

            level = "Endless Mode Prestige " + gameData.getEndlessPrestige() + " Level " + gameData.getEndlessCurrentLevel();

        } else {

            level = "Level Reached: " + gameData.getCurrentLevel();

        }

        fm = g.getFontMetrics();

        g.drawString(level, (width - fm.stringWidth(level)) / 2, height / 2 - UIScale.px(40));

        

        String score = "Score: " + gameData.getScore();

        fm = g.getFontMetrics();

        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2);

        

        g.setColor(ColorPalette.TEXT_GOLD);

        String money = "Money Earned: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 + UIScale.px(40));

        

        // Display cumulative run stats

        LevelStats runStats = gameData.getCumulativeRunStats();

        g.setFont(FONT_SMALL);

        g.setColor(ColorPalette.TEXT_DIM);

        int statsY = height / 2 + UIScale.px(85);

        

        if (runStats.getDodges() > 0 || runStats.getPerfectDodges() > 0) {

            String dodges = "Run Stats - Dodges: " + runStats.getDodges() + "  Perfect: " + runStats.getPerfectDodges();

            fm = g.getFontMetrics();

            g.drawString(dodges, (width - fm.stringWidth(dodges)) / 2, statsY);

            statsY += UIScale.px(24);

        }

        

        if (runStats.getBulletsSpawned() > 0 || runStats.getMaxCombo() > 0) {

            String combat = "Bullets Faced: " + runStats.getBulletsSpawned() + "  Max Combo: " + runStats.getMaxCombo() + "x";

            fm = g.getFontMetrics();

            g.drawString(combat, (width - fm.stringWidth(combat)) / 2, statsY);

            statsY += UIScale.px(24);

        }

        

        if (runStats.getRiskPercentage() > 0 || runStats.getNearMisses() > 0) {

            String risk = "Near Misses: " + runStats.getNearMisses() + "  Risk %: " + runStats.getRiskPercentage() + "%";

            fm = g.getFontMetrics();

            // Color risk based on level

            int riskPercent = runStats.getRiskPercentage();

            if (riskPercent >= 70) {

                g.setColor(RenderCache.RED_255_120_120);

            } else if (riskPercent >= 40) {

                g.setColor(RenderCache.WARM_255_180_100);

            } else {

                g.setColor(RenderCache.BLUE_150_200_255);

            }

            g.drawString(risk, (width - fm.stringWidth(risk)) / 2, statsY);

            g.setColor(RenderCache.SLATE_200_200_210);

            statsY += UIScale.px(24);

        }

        

        if (runStats.getDamageTaken() > 0 || runStats.getMissilesUsed() > 0) {

            String survival = "Damage Taken: " + runStats.getDamageTaken() + "  Missiles Used: " + runStats.getMissilesUsed();

            fm = g.getFontMetrics();

            g.drawString(survival, (width - fm.stringWidth(survival)) / 2, statsY);

            statsY += UIScale.px(30);

        }

        

        // Add spacing before persistent stats

        statsY += UIScale.px(15);

        

        // Show persistent stats

        g.setFont(FONT_SMALL);

        g.setColor(ColorPalette.TEXT_DIM);

        String totalMoney = "Total Money: $" + gameData.getTotalMoney();

        fm = g.getFontMetrics();

        g.drawString(totalMoney, (width - fm.stringWidth(totalMoney)) / 2, statsY);

        statsY += UIScale.px(25);

        

        String bestRun = "Best Run: Level " + Math.max(gameData.getBestRunLevel(), gameData.getCurrentLevel());

        fm = g.getFontMetrics();

        g.drawString(bestRun, (width - fm.stringWidth(bestRun)) / 2, statsY);

        statsY += UIScale.px(30);

        

        // Show missiles remaining

        if (gameData.getMissiles() > 0) {

            g.setFont(FONT_MEDIUM_BOLD);

            g.setColor(ColorPalette.SUCCESS_GREEN);

            String missileText = "\u2726 Missiles Remaining: " + gameData.getMissiles() + " \u2726";

            fm = g.getFontMetrics();

            g.drawString(missileText, (width - fm.stringWidth(missileText)) / 2, statsY);

            statsY += UIScale.px(35);

        } else {

            statsY += UIScale.px(10);

        }

        

        // Controls

        g.setFont(FONT_MEDIUM);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        drawPromptWithIcons(g, width / 2, statsY,

            KeyBindManager.Action.CONFIRM, " - New Run  |  ", KeyBindManager.Action.BACK, " - Main Menu");

        statsY += UIScale.px(30);

        

        // Roguelike reminder

        g.setFont(FontPalette.get(Font.ITALIC, 18));

        g.setColor(ColorPalette.SUCCESS_GREEN);

        String keep = "Your upgrades and items are saved!";

        fm = g.getFontMetrics();

        g.drawString(keep, (width - fm.stringWidth(keep)) / 2, statsY);

    }

    

    public void drawDemoOver(Graphics2D g, int width, int height, double time, int selectedOption) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Orange overlay pulse

        float pulse = (float)(0.05 + 0.03 * Math.sin(time * 2));

        g.setColor(new Color(235, 160, 50, (int)(255 * pulse)));

        g.fillRect(0, 0, width, height);

        

        // Title â€” DEMO OVER

        UITheme.drawTitle(g, "DEMO OVER", width, height / 2 - UIScale.px(160),

            ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED, time);

        

        // Thank you message

        g.setFont(FONT_LARGE_32);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        FontMetrics fm;

        String thanks = "Thanks for playing the demo!";

        fm = g.getFontMetrics();

        g.drawString(thanks, (width - fm.stringWidth(thanks)) / 2, height / 2 - UIScale.px(60));

        

        // Stats summary

        g.setFont(FONT_MEDIUM);

        g.setColor(ColorPalette.TEXT_DIM);

        String levels = "Levels Completed: " + Game.DEMO_MAX_LEVEL;

        fm = g.getFontMetrics();

        g.drawString(levels, (width - fm.stringWidth(levels)) / 2, height / 2 - UIScale.px(20));

        

        String score = "Final Score: " + gameData.getScore();

        fm = g.getFontMetrics();

        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2 + UIScale.px(10));

        

        // Full game tease

        g.setFont(FontPalette.get(Font.ITALIC, 20));

        g.setColor(ColorPalette.TEXT_GOLD);

        String tease = "The full game has " + 28 + " levels, upgrades, items & more!";

        fm = g.getFontMetrics();

        g.drawString(tease, (width - fm.stringWidth(tease)) / 2, height / 2 + UIScale.px(60));

        

        // Buttons

        int btnY = height / 2 + UIScale.px(110);

        int btnSpacing = UIScale.px(50);

        String[] options = {"Play Again", "Quit"};

        for (int i = 0; i < options.length; i++) {

            g.setFont(FONT_MEDIUM_BOLD);

            fm = g.getFontMetrics();

            int textW = fm.stringWidth(options[i]);

            int bx = (width - textW) / 2;

            int by = btnY + i * btnSpacing;

            

            if (i == selectedOption) {

                // Selected highlight

                g.setColor(ColorPalette.ACCENT_ORANGE);

                g.fillRoundRect(bx - UIScale.px(20), by - fm.getAscent() - UIScale.px(5),

                    textW + UIScale.px(40), fm.getHeight() + UIScale.px(10),

                    UIScale.px(8), UIScale.px(8));

                g.setColor(Color.BLACK);

            } else {

                g.setColor(ColorPalette.TEXT_DIM);

            }

            g.drawString(options[i], bx, by);

        }

        

        // Controls hint

        g.setFont(FONT_SMALL);

        g.setColor(ColorPalette.TEXT_DIM);

        drawPromptWithIcons(g, width / 2, height - UIScale.px(40),

            KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN,

            " to select  |  ", KeyBindManager.Action.CONFIRM, " to confirm");

    }

    

    public void drawWin(Graphics2D g, int width, int height, double time, double bossKillTime) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Victory confetti

        UITheme.drawConfetti(g, width, height, time);

        

        // Title â€” MISSION COMPLETE

        UITheme.drawTitle(g, "MISSION COMPLETE", width, height / 2 - UIScale.px(180), ColorPalette.VICTORY_GOLD, ColorPalette.SUCCESS_GREEN, time);

        

        // Stats with military styling

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_LARGE_32);

        FontMetrics fm;

        String score = "Score: " + gameData.getScore();

        fm = g.getFontMetrics();

        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2 - UIScale.px(90));

        

        g.setColor(ColorPalette.TEXT_GOLD);

        String money = "Money Earned: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 - UIScale.px(50));

        

        // Display boss kill time

        int minutes = (int)(bossKillTime / 60);

        int seconds = (int)(bossKillTime % 60);

        int milliseconds = (int)((bossKillTime % 1) * 100);

        String timeStr = String.format("Time: %d:%02d.%02d", minutes, seconds, milliseconds);

        fm = g.getFontMetrics();

        g.setColor(ColorPalette.VICTORY_GOLD);

        g.drawString(timeStr, (width - fm.stringWidth(timeStr)) / 2, height / 2 - UIScale.px(10));

        // Display all-time best time from leaderboard
        if (leaderboardManager != null && !gameData.isInEndlessMode()) {
            LeaderboardRecord bestRecord = leaderboardManager.getRecord(
                gameData.getGameMode(), gameData.getCurrentLevel());
            LeaderboardManager.LeaderboardResult recentResult = leaderboardManager.getRecentResult();
            boolean isNewRecord = recentResult == LeaderboardManager.LeaderboardResult.NEW_RECORD
                || recentResult == LeaderboardManager.LeaderboardResult.FIRST_COMPLETION;

            if (isNewRecord) {
                g.setFont(FONT_SMALL);
                String newRecStr = "NEW RECORD!";
                FontMetrics nrFm = g.getFontMetrics();
                g.setColor(new Color(255, 215, 0));
                g.drawString(newRecStr, (width - nrFm.stringWidth(newRecStr)) / 2, height / 2 + UIScale.px(10));
            } else if (bestRecord != null) {
                g.setFont(FONT_SMALL);
                String bestStr = "Best: " + bestRecord.formatTime();
                FontMetrics bFm = g.getFontMetrics();
                g.setColor(ColorPalette.TEXT_DIM);
                g.drawString(bestStr, (width - bFm.stringWidth(bestStr)) / 2, height / 2 + UIScale.px(10));
            }
        }

        // Display level stats (only non-zero stats)

        LevelStats stats = gameData.getCurrentLevelStats();

        g.setFont(FONT_SMALL);

        g.setColor(ColorPalette.TEXT_DIM);

        int statsY = height / 2 + UIScale.px(40);

        

        if (stats.getDodges() > 0) {

            String dodges = "Dodges: " + stats.getDodges();

            fm = g.getFontMetrics();

            g.drawString(dodges, (width - fm.stringWidth(dodges)) / 2, statsY);

            statsY += UIScale.px(26);

        }

        

        if (stats.getPerfectDodges() > 0) {

            String perfect = "Perfect Dodges: " + stats.getPerfectDodges();

            fm = g.getFontMetrics();

            g.setColor(ColorPalette.ACCENT_YELLOW);

            g.drawString(perfect, (width - fm.stringWidth(perfect)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += UIScale.px(26);

        }

        

        if (stats.getNearMisses() > 0) {

            String nearMiss = "Near Misses: " + stats.getNearMisses();

            fm = g.getFontMetrics();

            g.setColor(RenderCache.ORANGE_255_165_0);

            g.drawString(nearMiss, (width - fm.stringWidth(nearMiss)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += UIScale.px(26);

        }

        

        if (stats.getMaxCombo() > 0) {

            String maxCombo = "Max Combo: " + stats.getMaxCombo() + "x";

            fm = g.getFontMetrics();

            g.setColor(RenderCache.BLUE_100_200_255);

            g.drawString(maxCombo, (width - fm.stringWidth(maxCombo)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += UIScale.px(26);

        }

        

        if (stats.getBulletsSpawned() > 0) {

            String bullets = "Bullets: " + stats.getBulletsSpawned();

            fm = g.getFontMetrics();

            g.drawString(bullets, (width - fm.stringWidth(bullets)) / 2, statsY);

            statsY += UIScale.px(26);

        }

        

        int riskPercent = stats.getRiskPercentage();

        if (riskPercent > 0) {

            String risk = "Risk %: " + riskPercent + "%";

            fm = g.getFontMetrics();

            // Color based on risk level

            if (riskPercent >= 70) {

                g.setColor(RenderCache.RED_255_100_100); // High risk - red

            } else if (riskPercent >= 40) {

                g.setColor(RenderCache.ORANGE_255_165_0); // Medium risk - orange

            } else {

                g.setColor(RenderCache.BLUE_100_200_255); // Low risk - blue

            }

            g.drawString(risk, (width - fm.stringWidth(risk)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += UIScale.px(26);

        }

        

        if (stats.getDamageTaken() > 0) {

            String damage = "Damage Taken: " + stats.getDamageTaken();

            fm = g.getFontMetrics();

            g.setColor(new Color(200, 100, 100));

            g.drawString(damage, (width - fm.stringWidth(damage)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += UIScale.px(26);

        }

        

        if (stats.getMissilesUsed() > 0) {

            String missiles = "Missiles Used: " + stats.getMissilesUsed();

            fm = g.getFontMetrics();

            g.drawString(missiles, (width - fm.stringWidth(missiles)) / 2, statsY);

            statsY += UIScale.px(26);

        }

        

        if (stats.getMissileSurvivalBonus() > 0) {

            String missileBonus = "Missile Bonus: +" + stats.getMissileSurvivalBonus();

            fm = g.getFontMetrics();

            g.setColor(ColorPalette.SUCCESS_GREEN);

            g.drawString(missileBonus, (width - fm.stringWidth(missileBonus)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += UIScale.px(26);

        }

        

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_MEDIUM);

        // Position instruction text below stats, with minimum at height/2 + 160

        int instructionY = Math.max(height / 2 + UIScale.px(160), statsY + UIScale.px(30));

        drawPromptWithIcons(g, width / 2, instructionY,

            "Press ", KeyBindManager.Action.CONFIRM, " to Visit Shop");

        

        // Rank badge â€” slam animation (drawn last so it renders on top of all text)

        int scoreForRank = gameData.getScore();

        String rank = UITheme.calculateRank(scoreForRank);

        double elapsed = (screenEnteredTime >= 0) ? time - screenEnteredTime : 10;

        double rankDelay = 0.4;

        double rankSlamDuration = 0.45;

        double rankElapsed = elapsed - rankDelay;

        int badgeRadius = UIScale.px(60);

        int badgeCX, badgeCY;

        // Badge placed near the text area with slight random offset per quadrant

        int textCenterX = width / 2;

        int textTopY = height / 2 - UIScale.px(180);

        switch (badgeCorner) {

            case 0:  badgeCX = textCenterX + UIScale.px(220); badgeCY = textTopY - UIScale.px(10);  break;

            case 1:  badgeCX = textCenterX - UIScale.px(220); badgeCY = textTopY - UIScale.px(10);  break;

            case 2:  badgeCX = textCenterX + UIScale.px(200); badgeCY = textTopY + UIScale.px(60);  break;

            default: badgeCX = textCenterX - UIScale.px(200); badgeCY = textTopY + UIScale.px(60);  break;

        }

        if (rankElapsed < 0) {

            // Not yet visible

        } else if (rankElapsed < rankSlamDuration) {

            float t = (float)(rankElapsed / rankSlamDuration);

            float scale = 1.0f + 3.5f * (1.0f - easeOutBack(t));

            float alpha = Math.min(1.0f, t * 3.0f);

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setComposite(RenderCache.getAlpha(alpha));

            g2.translate(badgeCX, badgeCY);

            g2.rotate(badgeRotation);

            g2.scale(scale, scale);

            g2.translate(-badgeCX, -badgeCY);

            UITheme.drawRankBadge(g2, badgeCX, badgeCY, badgeRadius, rank, time, width, height);

            g2.dispose();

        } else {

            // Gentle float/bob after slam

            double postSlam = rankElapsed - rankSlamDuration;

            float bobY = (float)(Math.sin(postSlam * 1.8) * 3.0);

            float pulse = 1.0f + 0.015f * (float)Math.sin(postSlam * 2.5);

            Graphics2D g2 = (Graphics2D) g.create();

            g2.translate(badgeCX, badgeCY + bobY);

            g2.rotate(badgeRotation);

            g2.scale(pulse, pulse);

            g2.translate(-badgeCX, -(badgeCY + bobY));

            UITheme.drawRankBadge(g2, badgeCX, (int)(badgeCY + bobY), badgeRadius, rank, time, width, height);

            g2.dispose();

        }

        // Play slam sound when rank lands

        if (!slamSoundPlayed && rankElapsed >= rankSlamDuration) {

            SoundManager.getInstance().playSound(SoundManager.Sound.HIT_METAL, 0.9f);

            SoundManager.getInstance().playSound(SoundManager.Sound.RANK_UP, 0.7f);

            slamSoundPlayed = true;

        }

    }

    
    /**
     * Draw the leaderboard screen shown after victory.
     * Combined animation: countdown reveal → rank placement → hold.
     * Skippable with any key press.
     */
    public void drawLeaderboard(Graphics2D g, int width, int height, double time,
            LeaderboardManager lbManager, int animTimer, boolean animSkipped,
            boolean readyToExit, int completedLevel, GameMode completedDifficulty,
            double bossKillTime) {
        
        // Military themed background
        UITheme.drawScreenBackground(g, width, height, time);
        
        // Get leaderboard data
        LeaderboardManager.LeaderboardResult result = lbManager.getRecentResult();
        LeaderboardRecord bestRecord = lbManager.getRecentRecord();
        LeaderboardRecord previousRecord = lbManager.getRecentPreviousRecord();
        int playerTime = lbManager.getRecentTimeInFrames();
        boolean isFirstClear = result == LeaderboardManager.LeaderboardResult.FIRST_COMPLETION;
        boolean isNewRecord = result == LeaderboardManager.LeaderboardResult.NEW_RECORD || isFirstClear;
        // For comparison: use previous best if we just set a new record, otherwise use current best
        LeaderboardRecord compareRecord = (result == LeaderboardManager.LeaderboardResult.NEW_RECORD && previousRecord != null) 
            ? previousRecord : bestRecord;
        
        // Animation phase calculations
        int effectiveTimer = animSkipped ? 300 : animTimer;
        float phaseA = Math.min(1.0f, effectiveTimer / 90.0f);
        float phaseB = Math.min(1.0f, Math.max(0, effectiveTimer - 90) / 90.0f);
        float phaseC = Math.max(0, effectiveTimer - 180) / 60.0f;
        
        int centerX = width / 2;
        
        // --- Layout anchors (spaced to avoid overlap) ---
        int titleY = height / 2 - UIScale.px(290);
        int subtitleY = titleY + UIScale.px(50);
        int yourTimeLabelY = subtitleY + UIScale.px(70);
        int yourTimeValueY = yourTimeLabelY + UIScale.px(65);
        int panelTopY = yourTimeValueY + UIScale.px(55);
        int panelW = UIScale.px(500);
        int panelH = isFirstClear ? UIScale.px(160) : UIScale.px(260);
        int panelX = centerX - panelW / 2;
        
        // Title (Inlanders for text)
        UITheme.drawTitle(g, "LEADERBOARD", width, titleY, 
            ColorPalette.VICTORY_GOLD, ColorPalette.SUCCESS_GREEN, time);
        // Subtitle with level number in Arial Black (Inlanders doesn't support digits)
        String subtitleText = "LEVEL " + completedLevel + " \u2014 " + 
            (completedDifficulty != null ? completedDifficulty.getDisplayName().replace(" Mode", "").toUpperCase() : "UNKNOWN");
        g.setFont(FontPalette.get(java.awt.Font.BOLD, 28));
        FontMetrics stFm = g.getFontMetrics();
        int stW = stFm.stringWidth(subtitleText);
        g.setColor(ColorPalette.TEXT_DIM);
        g.drawString(subtitleText, centerX - stW / 2, subtitleY);
        
        // === Phase A: Countdown Reveal — Your time scrambles then resolves ===
        {
            g.setFont(FONT_TITLE_MEDIUM);
            FontMetrics fm = g.getFontMetrics();
            String label = "YOUR TIME";
            int labelW = fm.stringWidth(label);
            
            float labelAlpha = Math.min(1.0f, phaseA * 3.0f);
            Composite saved = g.getComposite();
            g.setComposite(RenderCache.getAlpha(labelAlpha));
            g.setColor(ColorPalette.TEXT_DIM);
            g.drawString(label, centerX - labelW / 2, yourTimeLabelY);
            g.setComposite(saved);
            
            // Scrambled -> real time display (Arial Black for digits)
            Font timeFont = FontPalette.get(java.awt.Font.BOLD, 72);
            g.setFont(timeFont);
            fm = g.getFontMetrics();
            
            String displayTime;
            if (phaseA < 0.8f) {
                double scrambleFactor = 1.0 - (phaseA / 0.8);
                double noise = (Math.sin(effectiveTimer * 7.3) * 0.5 + Math.cos(effectiveTimer * 11.7) * 0.3) * scrambleFactor;
                double fakeTime = bossKillTime * (1.0 + noise * 0.8);
                fakeTime = Math.max(0.01, fakeTime);
                int mins = (int)(fakeTime / 60);
                int secs = (int)(fakeTime % 60);
                int centis = (int)((fakeTime % 1) * 100);
                displayTime = String.format("%d:%02d.%02d", mins, secs, centis);
            } else {
                int mins = (int)(bossKillTime / 60);
                int secs = (int)(bossKillTime % 60);
                int centis = (int)((bossKillTime % 1) * 100);
                displayTime = String.format("%d:%02d.%02d", mins, secs, centis);
            }
            
            float scale = 1.0f;
            if (phaseA >= 0.8f && phaseA < 1.0f) {
                float t = (phaseA - 0.8f) / 0.2f;
                scale = 1.0f + 0.4f * (1.0f - easeOutBack(t));
            }
            
            int timeW = fm.stringWidth(displayTime);
            saved = g.getComposite();
            float timeAlpha = Math.min(1.0f, phaseA * 2.0f);
            g.setComposite(RenderCache.getAlpha(timeAlpha));
            
            if (scale != 1.0f) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(RenderCache.getAlpha(timeAlpha));
                g2.translate(centerX, yourTimeValueY);
                g2.scale(scale, scale);
                g2.setFont(timeFont);
                g2.setColor(ColorPalette.VICTORY_GOLD);
                g2.drawString(displayTime, -timeW / 2, 0);
                g2.dispose();
            } else {
                g.setColor(ColorPalette.VICTORY_GOLD);
                g.drawString(displayTime, centerX - timeW / 2, yourTimeValueY);
            }
            g.setComposite(saved);
        }
        
        // === Phase B: Leaderboard Panel Slides In ===
        {
            float slideProgress = easeOutBack(phaseB);
            int slideOffset = (int)((1.0f - slideProgress) * UIScale.px(200));
            float panelAlpha = Math.min(1.0f, phaseB * 2.0f);
            
            if (phaseB > 0) {
                Composite saved = g.getComposite();
                g.setComposite(RenderCache.getAlpha(panelAlpha));
                
                int drawY = panelTopY + slideOffset;
                
                // Panel background
                g.setColor(new Color(20, 25, 35, 220));
                g.fillRoundRect(panelX, drawY, panelW, panelH, UIScale.px(12), UIScale.px(12));
                
                // Panel border
                g.setColor(new Color(80, 90, 110));
                g.setStroke(STROKE_2);
                g.drawRoundRect(panelX, drawY, panelW, panelH, UIScale.px(12), UIScale.px(12));
                
                int infoY = drawY + UIScale.px(40);
                
                if (isFirstClear) {
                    // First clear — simple centered message, no comparison
                    g.setFont(FONT_SUBTITLE);
                    FontMetrics fm = g.getFontMetrics();
                    String firstMsg = "FIRST CLEAR!";
                    g.setColor(ColorPalette.VICTORY_GOLD);
                    g.drawString(firstMsg, centerX - fm.stringWidth(firstMsg) / 2, infoY);
                    infoY += UIScale.px(40);
                    
                    g.setFont(FONT_MEDIUM);
                    fm = g.getFontMetrics();
                    String recorded = "Time recorded as your best!";
                    g.setColor(ColorPalette.TEXT_DIM);
                    g.drawString(recorded, centerX - fm.stringWidth(recorded) / 2, infoY);
                } else if (compareRecord != null) {
                    // Header
                    g.setFont(FONT_MEDIUM_BOLD);
                    FontMetrics fm = g.getFontMetrics();
                    String header = "PREVIOUS BEST";
                    g.setColor(ColorPalette.TEXT_PRIMARY);
                    g.drawString(header, centerX - fm.stringWidth(header) / 2, infoY);
                    
                    // Divider line
                    g.setColor(new Color(60, 65, 80));
                    g.drawLine(panelX + UIScale.px(20), infoY + UIScale.px(12), 
                               panelX + panelW - UIScale.px(20), infoY + UIScale.px(12));
                    infoY += UIScale.px(40);
                    
                    // Previous best time
                    g.setFont(FONT_LARGE);
                    fm = g.getFontMetrics();
                    String bestTimeStr = compareRecord.formatTime();
                    g.setColor(ColorPalette.MEDAL_GOLD);
                    g.drawString(bestTimeStr, centerX - fm.stringWidth(bestTimeStr) / 2, infoY);
                    infoY += UIScale.px(30);
                    
                    // Set by
                    g.setFont(FONT_MEDIUM);
                    fm = g.getFontMetrics();
                    String setBy = "Set by: " + compareRecord.getSaveName();
                    g.setColor(ColorPalette.TEXT_DIM);
                    g.drawString(setBy, centerX - fm.stringWidth(setBy) / 2, infoY);
                    infoY += UIScale.px(25);
                    
                    // Date
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy");
                    String dateStr = "Date: " + sdf.format(new java.util.Date(compareRecord.getTimestamp()));
                    g.drawString(dateStr, centerX - fm.stringWidth(dateStr) / 2, infoY);
                    infoY += UIScale.px(35);
                    
                    // Difference vs previous best
                    g.setFont(FONT_MEDIUM_BOLD);
                    fm = g.getFontMetrics();
                    int diff = playerTime - compareRecord.getTimeInFrames();
                    double diffSec = Math.abs(diff) / 60.0;
                    int dMins = (int)(diffSec / 60);
                    int dSecs = (int)(diffSec % 60);
                    int dCentis = (int)((diffSec % 1) * 100);
                    String diffStr;
                    if (diff <= 0) {
                        diffStr = dMins > 0 ? String.format("-%d:%02d.%02d faster!", dMins, dSecs, dCentis) :
                                              String.format("-0:%02d.%02d faster!", dSecs, dCentis);
                        g.setColor(ColorPalette.SUCCESS_GREEN);
                    } else {
                        diffStr = dMins > 0 ? String.format("+%d:%02d.%02d slower", dMins, dSecs, dCentis) :
                                              String.format("+0:%02d.%02d slower", dSecs, dCentis);
                        g.setColor(new Color(200, 100, 100));
                    }
                    g.drawString(diffStr, centerX - fm.stringWidth(diffStr) / 2, infoY);
                } else {
                    // No previous record (shouldn't normally happen since FIRST_COMPLETION is handled above)
                    g.setFont(FONT_LARGE);
                    FontMetrics fm = g.getFontMetrics();
                    String noRecord = "No Record";
                    g.setColor(ColorPalette.TEXT_DIM);
                    g.drawString(noRecord, centerX - fm.stringWidth(noRecord) / 2, infoY);
                }
                
                g.setComposite(saved);
            }
        }
        
        // === NEW RECORD banner (only for beating a previous record, not first clear) ===
        if (result == LeaderboardManager.LeaderboardResult.NEW_RECORD && phaseB > 0.5f) {
            float bannerAlpha = Math.min(1.0f, (phaseB - 0.5f) * 4.0f);
            Composite saved = g.getComposite();
            g.setComposite(RenderCache.getAlpha(bannerAlpha));
            
            g.setFont(FONT_TITLE);
            FontMetrics fm = g.getFontMetrics();
            String newRecordText = "NEW RECORD!";
            int nrW = fm.stringWidth(newRecordText);
            
            float pulse = 1.0f + 0.05f * (float)Math.sin(time * 4.0);
            int bannerY = panelTopY + panelH + UIScale.px(40);
            
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(RenderCache.getAlpha(bannerAlpha));
            g2.translate(centerX, bannerY);
            g2.scale(pulse, pulse);
            
            g2.setColor(new Color(255, 210, 80, 60));
            g2.setFont(FONT_TITLE);
            g2.drawString(newRecordText, -nrW / 2 - 2, 2);
            g2.drawString(newRecordText, -nrW / 2 + 2, -2);
            
            g2.setColor(ColorPalette.VICTORY_GOLD);
            g2.drawString(newRecordText, -nrW / 2, 0);
            g2.dispose();
            
            g.setComposite(saved);
        }
        
        // Victory confetti for any new record or first clear
        if (isNewRecord && phaseB > 0.5f) {
            UITheme.drawConfetti(g, width, height, time);
        }
        
        // === Phase C: "Press any key" prompt ===
        if (readyToExit) {
            float promptAlpha = 0.5f + 0.3f * (float)Math.sin(time * 3.0);
            Composite saved = g.getComposite();
            g.setComposite(RenderCache.getAlpha(promptAlpha));
            g.setFont(FONT_MEDIUM);
            FontMetrics fm = g.getFontMetrics();
            String prompt = "Press any key to continue";
            int promptW = fm.stringWidth(prompt);
            g.setColor(ColorPalette.TEXT_DIM);
            g.drawString(prompt, centerX - promptW / 2, height - UIScale.px(50));
            g.setComposite(saved);
        }
    }

    /**
     * Draws the leaderboard view screen accessible from the main menu.
     * Shows best times for all 28 levels across difficulty tabs.
     */
    public void drawLeaderboardView(Graphics2D g, int width, int height, double time,
            LeaderboardManager lbManager, int selectedDifficulty, double scrollOffset) {
        
        // Military themed background
        UITheme.drawScreenBackground(g, width, height, time);
        
        // Title
        UITheme.drawTitle(g, "LEADERBOARD", width, UIScale.px(55),
            ColorPalette.ACCENT_CYAN, new Color(180, 200, 220), time, FONT_TITLE_MEDIUM);
        
        // Difficulty tabs
        GameMode[] modes = GameMode.values();
        int tabWidth = UIScale.px(180);
        int tabHeight = UIScale.px(38);
        int tabGap = UIScale.px(12);
        int totalTabsWidth = modes.length * tabWidth + (modes.length - 1) * tabGap;
        int tabStartX = (width - totalTabsWidth) / 2;
        int tabY = UIScale.px(100);
        
        Font tabFont = FontPalette.get(java.awt.Font.BOLD, 16);
        g.setFont(tabFont);
        
        for (int i = 0; i < modes.length; i++) {
            int tx = tabStartX + i * (tabWidth + tabGap);
            boolean selected = (i == selectedDifficulty);
            
            // Tab background
            if (selected) {
                // Active tab — filled with mode color
                g.setColor(ColorPalette.withAlpha(modes[i].getColor(), 180));
                g.fillRoundRect(tx, tabY, tabWidth, tabHeight, UIScale.px(8), UIScale.px(8));
                // Glow border
                g.setStroke(RenderCache.getStroke(2));
                g.setColor(modes[i].getColor());
                g.drawRoundRect(tx, tabY, tabWidth, tabHeight, UIScale.px(8), UIScale.px(8));
            } else {
                // Inactive tab — dark, dimmed
                g.setColor(new Color(30, 35, 50, 200));
                g.fillRoundRect(tx, tabY, tabWidth, tabHeight, UIScale.px(8), UIScale.px(8));
                g.setStroke(RenderCache.getStroke(1));
                g.setColor(ColorPalette.withAlpha(modes[i].getColor(), 80));
                g.drawRoundRect(tx, tabY, tabWidth, tabHeight, UIScale.px(8), UIScale.px(8));
            }
            
            // Tab text
            FontMetrics tfm = g.getFontMetrics();
            String tabLabel = modes[i].getDisplayName().replace(" Mode", "").toUpperCase();
            int textW = tfm.stringWidth(tabLabel);
            g.setColor(selected ? Color.WHITE : ColorPalette.TEXT_DIM);
            g.drawString(tabLabel, tx + (tabWidth - textW) / 2, tabY + tabHeight / 2 + tfm.getAscent() / 2 - 1);
        }
        
        // Arrow hints on tabs — show key/controller icons
        int arrowIconY = tabY + tabHeight / 2;
        if (selectedDifficulty > 0) {
            drawSingleActionIcon(g, tabStartX - UIScale.px(18), arrowIconY, KeyBindManager.Action.MOVE_LEFT);
        }
        if (selectedDifficulty < modes.length - 1) {
            drawSingleActionIcon(g, tabStartX + totalTabsWidth + UIScale.px(22), arrowIconY, KeyBindManager.Action.MOVE_RIGHT);
        }
        
        // Completion count — styled as a pill badge below the tabs
        GameMode selectedMode = modes[selectedDifficulty];
        int completedCount = 0;
        for (int lvl = 1; lvl <= LeaderboardManager.LEVEL_COUNT; lvl++) {
            if (lbManager.hasRecord(selectedMode, lvl)) completedCount++;
        }
        
        boolean allCompleted = (completedCount == LeaderboardManager.LEVEL_COUNT);
        Font countFont = FontPalette.get(java.awt.Font.BOLD, 13);
        g.setFont(countFont);
        FontMetrics countFm = g.getFontMetrics();
        String countText = completedCount + " / " + LeaderboardManager.LEVEL_COUNT + " Completed";
        int pillW = countFm.stringWidth(countText) + UIScale.px(24);
        int pillH = UIScale.px(24);
        int pillX = (width - pillW) / 2;
        int pillY = tabY + tabHeight + UIScale.px(10);
        
        // Pill background
        Color pillColor = allCompleted ? ColorPalette.TEXT_GOLD : selectedMode.getColor();
        g.setColor(ColorPalette.withAlpha(pillColor, 40));
        g.fillRoundRect(pillX, pillY, pillW, pillH, pillH, pillH);
        g.setStroke(RenderCache.getStroke(1));
        g.setColor(ColorPalette.withAlpha(pillColor, 100));
        g.drawRoundRect(pillX, pillY, pillW, pillH, pillH, pillH);
        
        // Pill text
        g.setColor(allCompleted ? ColorPalette.TEXT_GOLD : ColorPalette.TEXT_PRIMARY);
        g.drawString(countText, pillX + UIScale.px(12), pillY + pillH / 2 + countFm.getAscent() / 2 - 1);
        
        // Level records list — scrollable area
        int listTop = pillY + pillH + UIScale.px(12);
        int listBottom = height - UIScale.px(50);
        int rowHeight = UIScale.px(42);
        int rowGap = UIScale.px(6);
        int listWidth = UIScale.px(700);
        int listX = (width - listWidth) / 2;
        
        // Clipping region for scroll
        java.awt.Shape oldClip = g.getClip();
        g.setClip(0, listTop, width, listBottom - listTop);
        
        // Column widths
        int colLevel = UIScale.px(70);     // "Level XX"
        int colTime = UIScale.px(140);     // "M:SS.cc"
        int colSave = UIScale.px(180);     // Save name
        int colDate = UIScale.px(180);     // Date
        
        // Header row (fixed, drawn above clip for visual, but inside clip for simplicity)
        int headerY = listTop - (int)scrollOffset;
        
        // Draw header background
        g.setColor(new Color(25, 30, 45, 220));
        g.fillRoundRect(listX, headerY, listWidth, rowHeight, UIScale.px(6), UIScale.px(6));
        g.setStroke(RenderCache.getStroke(1));
        g.setColor(ColorPalette.BORDER_STEEL);
        g.drawRoundRect(listX, headerY, listWidth, rowHeight, UIScale.px(6), UIScale.px(6));
        
        Font headerFont = FontPalette.get(java.awt.Font.BOLD, 14);
        g.setFont(headerFont);
        g.setColor(ColorPalette.TEXT_DIM);
        FontMetrics hfm = g.getFontMetrics();
        int hTextY = headerY + rowHeight / 2 + hfm.getAscent() / 2 - 1;
        
        int cx = listX + UIScale.px(15);
        g.drawString("LEVEL", cx, hTextY);
        cx += colLevel;
        g.drawString("BEST TIME", cx, hTextY);
        cx += colTime;
        g.drawString("SAVE", cx, hTextY);
        cx += colSave;
        g.drawString("DATE", cx, hTextY);
        
        // Data rows
        Font rowFont = FontPalette.get(java.awt.Font.PLAIN, 15);
        Font timeFont = FontPalette.get(java.awt.Font.BOLD, 16);
        java.text.SimpleDateFormat dateFmt = new java.text.SimpleDateFormat("MMM dd, yyyy");
        
        for (int lvl = 1; lvl <= LeaderboardManager.LEVEL_COUNT; lvl++) {
            int rowY = headerY + (rowHeight + rowGap) * lvl;
            
            // Skip if not visible
            if (rowY + rowHeight < listTop || rowY > listBottom) continue;
            
            LeaderboardRecord record = lbManager.getRecord(selectedMode, lvl);
            boolean hasRecord = (record != null);
            
            // Row background — alternating with subtle mode color tint
            if (hasRecord) {
                g.setColor(ColorPalette.withAlpha(selectedMode.getColor(), lvl % 2 == 0 ? 20 : 30));
            } else {
                g.setColor(new Color(20, 22, 32, lvl % 2 == 0 ? 160 : 180));
            }
            g.fillRoundRect(listX, rowY, listWidth, rowHeight, UIScale.px(6), UIScale.px(6));
            
            // Subtle border for records
            if (hasRecord) {
                g.setStroke(RenderCache.getStroke(1));
                g.setColor(ColorPalette.withAlpha(selectedMode.getColor(), 60));
                g.drawRoundRect(listX, rowY, listWidth, rowHeight, UIScale.px(6), UIScale.px(6));
            }
            
            int textY = rowY + rowHeight / 2;
            cx = listX + UIScale.px(15);
            
            // Level number
            g.setFont(rowFont);
            FontMetrics rfm = g.getFontMetrics();
            int rTextY = textY + rfm.getAscent() / 2 - 1;
            g.setColor(hasRecord ? ColorPalette.TEXT_PRIMARY : ColorPalette.TEXT_DIM);
            g.drawString("Level " + lvl, cx, rTextY);
            cx += colLevel;
            
            if (hasRecord) {
                // Best time
                g.setFont(timeFont);
                FontMetrics timeFm = g.getFontMetrics();
                int tTextY = textY + timeFm.getAscent() / 2 - 1;
                g.setColor(ColorPalette.TEXT_GOLD);
                g.drawString(record.formatTime(), cx, tTextY);
                cx += colTime;
                
                // Save name
                g.setFont(rowFont);
                rfm = g.getFontMetrics();
                rTextY = textY + rfm.getAscent() / 2 - 1;
                g.setColor(ColorPalette.TEXT_PRIMARY);
                String saveName = record.getSaveName();
                if (saveName != null && saveName.length() > 16) saveName = saveName.substring(0, 15) + "\u2026";
                g.drawString(saveName != null ? saveName : "???", cx, rTextY);
                cx += colSave;
                
                // Date
                g.setColor(ColorPalette.TEXT_DIM);
                String dateStr = dateFmt.format(new java.util.Date(record.getTimestamp()));
                g.drawString(dateStr, cx, rTextY);
            } else {
                // No record placeholders
                g.setFont(rowFont);
                FontMetrics rfm2 = g.getFontMetrics();
                int rTextY2 = textY + rfm2.getAscent() / 2 - 1;
                g.setColor(new Color(60, 65, 80));
                g.drawString("--:--.--", cx, rTextY2);
                cx += colTime;
                g.drawString("-", cx, rTextY2);
                cx += colSave;
                g.drawString("-", cx, rTextY2);
            }
        }
        
        g.setClip(oldClip);
        
        // Scroll indicators (fade edges)
        if (scrollOffset > 0) {
            java.awt.GradientPaint fadeTop = new java.awt.GradientPaint(
                0, listTop, new Color(10, 10, 20, 200),
                0, listTop + UIScale.px(30), new Color(10, 10, 20, 0));
            g.setPaint(fadeTop);
            g.fillRect(0, listTop, width, UIScale.px(30));
        }
        int maxScroll = Math.max(0, (LeaderboardManager.LEVEL_COUNT + 1) * (rowHeight + rowGap) - (listBottom - listTop));
        if (scrollOffset < maxScroll - 1) {
            java.awt.GradientPaint fadeBottom = new java.awt.GradientPaint(
                0, listBottom - UIScale.px(30), new Color(10, 10, 20, 0),
                0, listBottom, new Color(10, 10, 20, 200));
            g.setPaint(fadeBottom);
            g.fillRect(0, listBottom - UIScale.px(30), width, UIScale.px(30));
        }
        
        // Footer hint with key/controller icons
        g.setFont(FONT_SMALL);
        g.setColor(ColorPalette.TEXT_DIM);
        drawPromptWithIcons(g, width / 2, height - UIScale.px(20),
            "", KeyBindManager.Action.MOVE_LEFT, "/", KeyBindManager.Action.MOVE_RIGHT,
            " Switch Difficulty   ",
            KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN,
            " Scroll   ",
            KeyBindManager.Action.BACK, " Back");
    }

    public void drawSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, int selectedCategory, GameData gameData) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "SETTINGS", width, UIScale.px(80), ColorPalette.ACCENT_PURPLE, ColorPalette.ACCENT_CYAN, time, FONT_TITLE_MEDIUM);

        

        // Category tabs
        FontMetrics fm;

        String[] categories = {"GRAPHICS", "AUDIO", "GAMEPLAY", "DEBUG", "CONTROLS", "HUD"};

        int tabWidth = UIScale.px(130);

        int tabStartX = (width - categories.length * tabWidth) / 2;

        int tabY = UIScale.px(130);

        

        g.setFont(FONT_SMALL);

        for (int i = 0; i < categories.length; i++) {

            int tabX = tabStartX + i * tabWidth;

            boolean isSelected = i == selectedCategory;

            boolean tabsFocused = selectedItem == -1; // Tabs are focused

            

            // Tab background

            if (isSelected) {

                g.setColor(new Color(ColorPalette.BG_CARD_SELECTED.getRed(), ColorPalette.BG_CARD_SELECTED.getGreen(), ColorPalette.BG_CARD_SELECTED.getBlue(), 200));

            } else {

                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_LIGHT, 150));

            }

            g.fillRoundRect(tabX, tabY, tabWidth - UIScale.px(10), UIScale.px(40), UIScale.px(10), UIScale.px(10));

            

            // Tab border

            if (isSelected) {

                // Double border if tabs are focused

                if (tabsFocused) {

                    g.setColor(ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 200)); // Green glow when focused

                    g.setStroke(RenderCache.getStroke(4));

                    g.drawRoundRect(tabX - UIScale.px(1), tabY - UIScale.px(1), tabWidth - UIScale.px(8), UIScale.px(42), UIScale.px(10), UIScale.px(10));

                }

                g.setColor(ColorPalette.TEXT_GOLD);

                g.setStroke(RenderCache.getStroke(2));

                g.drawRoundRect(tabX, tabY, tabWidth - UIScale.px(10), UIScale.px(40), UIScale.px(10), UIScale.px(10));

            }

            

            // Tab text

            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : ColorPalette.TEXT_PRIMARY);

            fm = g.getFontMetrics();

            g.drawString(categories[i], tabX + (tabWidth - UIScale.px(10) - fm.stringWidth(categories[i])) / 2, tabY + UIScale.px(26));

        }

        

        g.setFont(FONT_EXTRA_SMALL_16);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        if (Game.keyBindManager != null && Game.keyBindManager.isControllerMode()) {
            drawPromptWithIcons(g, width / 2, UIScale.px(195), "D-Pad to navigate | ", KeyBindManager.ControllerButton.RB, " to switch tabs | ", KeyBindManager.Action.BACK, " to exit");
        } else {
            drawPromptWithIcons(g, width / 2, UIScale.px(195), KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " to navigate | TAB to switch tabs | ", KeyBindManager.Action.BACK, " to exit");
        }

        

        // Create clipping region for scrollable area

        Shape oldClip = g.getClip();

        g.setClip(0, UIScale.px(200), width, height - UIScale.px(260));

        

        // Draw settings based on category

        if (selectedCategory == 0) {

            drawGraphicsSettings(g, width, height, selectedItem, time, scrollOffset);

        } else if (selectedCategory == 1) {

            drawAudioSettings(g, width, height, selectedItem, time, scrollOffset, gameData);

        } else if (selectedCategory == 2) {

            drawGameplaySettings(g, width, height, selectedItem, time, scrollOffset, gameData);

        } else if (selectedCategory == 3) {

            drawDebugSettings(g, width, height, selectedItem, time, scrollOffset);

        } else if (selectedCategory == 4) {

            drawControlsSettings(g, width, height, selectedItem, time, scrollOffset);

        } else if (selectedCategory == 5) {
            // HUD Layout Editor â€” renders its own mock screen + side panel
            if (Game.hudLayout != null) {
                hudLayoutEditor.render(g, 50, 200, width - 100, height - 270, Game.hudLayout, time);
            }
        }

        

        // Restore clipping

        g.setClip(oldClip);

        

        // Instructions / Bottom bar

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_INFO);

        boolean settingsCtrlMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();

        if (settingsCtrlMode) {

            String dirtyMarker = Game.settingsDirty ? "  *" : "";

            drawPromptWithIcons(g, width / 2, height - UIScale.px(30),

                KeyBindManager.ControllerButton.Y, ": Reset Tab  |  ",

                KeyBindManager.ControllerButton.START, ": Apply" + dirtyMarker + "  |  ",

                KeyBindManager.Action.BACK, " : Return to Menu");

        } else {

            String dirtyMarker = Game.settingsDirty ? "  *" : "";

            drawPromptWithIcons(g, width / 2, height - UIScale.px(30),

                java.awt.event.KeyEvent.VK_R, " Reset Tab  |  ",

                java.awt.event.KeyEvent.VK_ENTER, " Apply" + dirtyMarker + "  |  ",

                java.awt.event.KeyEvent.VK_ESCAPE, " Return to Menu");

        }

        

        // Unsaved changes warning overlay

        if (Game.showSettingsWarning) {

            // Darken background

            g.setColor(new Color(0, 0, 0, 180));

            g.fillRect(0, 0, width, height);

            

            // Warning box

            int boxW = UIScale.px(600);

            int boxH = UIScale.px(200);

            int boxX = (width - boxW) / 2;

            int boxY = (height - boxH) / 2;

            

            // Box background

            g.setColor(new Color(30, 30, 45, 240));

            g.fillRoundRect(boxX, boxY, boxW, boxH, UIScale.px(12), UIScale.px(12));

            g.setColor(ColorPalette.ACCENT_ORANGE);

            g.setStroke(new java.awt.BasicStroke(UIScale.px(2)));

            g.drawRoundRect(boxX, boxY, boxW, boxH, UIScale.px(12), UIScale.px(12));

            

            // Warning title

            g.setColor(ColorPalette.ACCENT_YELLOW);

            g.setFont(FONT_SUBTITLE);

            String warnTitle = "Unsaved Changes";

            java.awt.FontMetrics wfm = g.getFontMetrics();

            g.drawString(warnTitle, width / 2 - wfm.stringWidth(warnTitle) / 2, boxY + UIScale.px(45));

            

            // Warning message

            g.setColor(ColorPalette.TEXT_PRIMARY);

            g.setFont(FONT_INFO);

            wfm = g.getFontMetrics();

            String warnMsg = "You have unsaved settings changes.";

            g.drawString(warnMsg, width / 2 - wfm.stringWidth(warnMsg) / 2, boxY + UIScale.px(80));

            

            if (Game.settingsNeedsRestart) {

                g.setColor(ColorPalette.ACCENT_YELLOW);

                String restartMsg = "Applying will restart the window.";

                g.drawString(restartMsg, width / 2 - wfm.stringWidth(restartMsg) / 2, boxY + UIScale.px(100));

            }

            

            // Three options

            String[] options = {"Apply & Exit", "Discard & Exit", "Cancel"};

            int btnW = UIScale.px(155);

            int btnH = UIScale.px(36);

            int totalBtnW = btnW * 3 + UIScale.px(20) * 2;

            int btnStartX = (width - totalBtnW) / 2;

            int btnY = boxY + boxH - UIScale.px(60);

            // Store bounds for mouse click detection

            for (int bi = 0; bi < 3; bi++) {

                warningButtonBounds[bi] = new java.awt.Rectangle(

                    btnStartX + bi * (btnW + UIScale.px(20)), btnY, btnW, btnH);

            }

            

            g.setFont(FONT_INFO);

            wfm = g.getFontMetrics();

            for (int i = 0; i < 3; i++) {

                int bx = btnStartX + i * (btnW + UIScale.px(20));

                boolean sel = (i == Game.settingsWarningSelection);

                

                // Button background

                if (sel) {

                    g.setColor(ColorPalette.ACCENT_ORANGE);

                } else {

                    g.setColor(new Color(50, 50, 70, 200));

                }

                g.fillRoundRect(bx, btnY, btnW, btnH, UIScale.px(6), UIScale.px(6));

                

                // Button border

                g.setColor(sel ? ColorPalette.TEXT_PRIMARY : ColorPalette.TEXT_DIM);

                g.setStroke(new java.awt.BasicStroke(sel ? UIScale.px(2) : 1));

                g.drawRoundRect(bx, btnY, btnW, btnH, UIScale.px(6), UIScale.px(6));

                

                // Button text

                g.setColor(sel ? Color.WHITE : ColorPalette.TEXT_DIM);

                g.drawString(options[i], bx + (btnW - wfm.stringWidth(options[i])) / 2, btnY + btnH / 2 + wfm.getAscent() / 2 - 2);

            }

        }

    }

    

    private void drawGraphicsSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset) {

        // Dynamic GPU settings offset â€” GPU section only appears when GPU is detected

        int offset = Game.getGPUSettingsOffset();

        int totalItems = 15 + offset;

        

        // Build arrays dynamically to accommodate GPU settings

        java.util.ArrayList<String> namesList = new java.util.ArrayList<>();

        java.util.ArrayList<String> valuesList = new java.util.ArrayList<>();

        java.util.ArrayList<String> descList = new java.util.ArrayList<>();

        java.util.ArrayList<boolean[]> togglesList = new java.util.ArrayList<>();

        

        // --- GPU ACCELERATION section (only if GPU detected) ---

        if (Game.gpuAvailable) {

            namesList.add("Hardware Acceleration");

            valuesList.add(Game.enableGPUAcceleration ? "ON" : "OFF");

            descList.add("Enable GPU-accelerated rendering pipeline (restart required)");

            

            if (Game.enableGPUAcceleration) {

                namesList.add("Rendering Pipeline");

                valuesList.add(Game.gpuPipelineType == 0 ? "Auto" : Game.gpuPipelineType == 1 ? "OpenGL" : "Direct3D");

                descList.add("GPU rendering backend â€” Auto lets Java choose the best (restart required)");

                

                namesList.add("Buffer Mode");

                valuesList.add(Game.bufferStrategyMode == 0 ? "Double" : "Triple");

                descList.add("Triple buffering = smoother but +1 frame latency; Double = lower latency");

            }

        }

        

        // --- Original 15 settings ---

        String[] baseNames = {"Fullscreen Mode", "Resolution", "VSync", "FPS Limit", "Anti-Aliasing", "Shadows", "Particle Effects", "Bloom/Glow", "Motion Blur", "Chromatic Aberration", "Vignette", "Grain Effect", "Camera Zoom", "UI Parallax", "UI Scale"};

        String[] baseValues = {

            Game.isFullscreen ? "Fullscreen" : "Windowed",

            Game.resolutionPreset == 0 ? "1280x720" : Game.resolutionPreset == 1 ? "1366x768" : Game.resolutionPreset == 2 ? "1600x900" : Game.resolutionPreset == 3 ? "1920x1080" : Game.resolutionPreset == 4 ? "2560x1440" : "3840x2160",

            Game.enableVSync ? "ON" : "OFF",

            Game.fpsLimit == 0 ? "30 FPS" : Game.fpsLimit == 1 ? "60 FPS" : Game.fpsLimit == 2 ? "120 FPS" : Game.fpsLimit == 3 ? "144 FPS" : "Unlimited",

            Game.enableAntiAliasing ? "ON" : "OFF",

            Game.shadowQuality == 0 ? "Off" : Game.shadowQuality == 1 ? "Low" : Game.shadowQuality == 2 ? "Medium" : "High",

            Game.enableParticles ? "ON" : "OFF",

            Game.enableBloom ? "ON" : "OFF",

            Game.enableMotionBlur ? "ON" : "OFF",

            Game.enableChromaticAberration ? "ON" : "OFF",

            Game.enableVignette ? "ON" : "OFF",

            Game.enableGrainEffect ? "ON" : "OFF",

            String.format("%.0f%%", Game.cameraZoom * 100),

            Game.enableUIParallax ? "ON" : "OFF",

            config.UIScale.LABELS[Game.uiScale]

        };

        String[] baseDescriptions = {

            "Toggle between fullscreen and windowed mode (F11)",

            "Display resolution (restart required for changes to take effect)",

            "Synchronize frame rate with monitor refresh (smoother, less tearing)",

            "Maximum frames per second (lower = better performance)",

            "Smooth edges of graphics (better quality, slight performance impact)",

            "Shadow quality - more layers = smoother glow (Off/Low/Medium/High)",

            "Enable particle effects (trails, explosions, etc.)",

            "Glow effect on bright objects (performance impact)",

            "Blur effect on fast moving objects (performance impact)",

            "Color fringing on screen edges (cinematic effect)",

            "Darken screen edges (focuses attention on center)",

            "Add grain texture overlay (performance impact)",

            "How zoomed in the camera is during gameplay (75% - 150%)",

            "UI elements shift slightly with camera movement for depth effect",

            "Scale all UI elements â€” menus, buttons, HUD, shop, popups (Small / Medium / Large)"

        };

        for (int i = 0; i < baseNames.length; i++) {

            namesList.add(baseNames[i]);

            valuesList.add(baseValues[i]);

            descList.add(baseDescriptions[i]);

        }

        

        String[] settingNames = namesList.toArray(new String[0]);

        String[] settingValues = valuesList.toArray(new String[0]);

        String[] descriptions = descList.toArray(new String[0]);

        

        // Camera Zoom slider â€” shifted by offset

        float[][] sliders = new float[settingNames.length][4];

        sliders[12 + offset] = new float[]{1, 0.75f, 1.5f, (float)Game.cameraZoom};

        

        // Toggles array

        boolean[] toggles = new boolean[settingNames.length];

        // GPU toggles

        if (Game.gpuAvailable) {

            toggles[0] = Game.enableGPUAcceleration;

            // indices 1,2 when expanded are pills, not toggles â€” leave false

        }

        // Original toggles shifted by offset

        boolean[] baseToggles = {

            Game.isFullscreen, false, Game.enableVSync, false, Game.enableAntiAliasing,

            Game.shadowQuality > 0, Game.enableParticles, Game.enableBloom,

            Game.enableMotionBlur, Game.enableChromaticAberration,

            Game.enableVignette, Game.enableGrainEffect, false,

            Game.enableUIParallax, false

        };

        for (int i = 0; i < baseToggles.length; i++) {

            toggles[i + offset] = baseToggles[i];

        }

        

        // Section headers

        String[] sectionHeaders = new String[settingNames.length];

        if (Game.gpuAvailable) {

            sectionHeaders[0] = "GPU ACCELERATION";

        }

        sectionHeaders[0 + offset] = "DISPLAY";

        sectionHeaders[4 + offset] = "QUALITY";

        sectionHeaders[8 + offset] = "EFFECTS";

        sectionHeaders[12 + offset] = "CAMERA";

        

        // Pill selector options

        String[][] pillOptions = new String[settingNames.length][];

        int[] pillSelected = new int[settingNames.length];

        

        // GPU pills (only when acceleration is enabled)

        if (Game.gpuAvailable && Game.enableGPUAcceleration) {

            pillOptions[1] = new String[]{"Auto", "OpenGL", "Direct3D"};

            pillSelected[1] = Game.gpuPipelineType;

            

            pillOptions[2] = new String[]{"Double", "Triple"};

            pillSelected[2] = Game.bufferStrategyMode;

        }

        

        // Original pills shifted by offset

        pillOptions[0 + offset] = new String[]{"Windowed", "Fullscreen"};

        pillSelected[0 + offset] = Game.isFullscreen ? 1 : 0;

        

        pillOptions[1 + offset] = new String[]{"1280x720", "1366x768", "1600x900", "1920x1080", "2560x1440", "3840x2160"};

        pillSelected[1 + offset] = Game.resolutionPreset;

        

        pillOptions[3 + offset] = new String[]{"30", "60", "120", "144", "Unlimited"};

        pillSelected[3 + offset] = Game.fpsLimit;

        

        pillOptions[5 + offset] = new String[]{"Off", "Low", "Medium", "High"};

        pillSelected[5 + offset] = Game.shadowQuality;

        

        pillOptions[14 + offset] = new String[]{"Small", "Medium", "Large"};

        pillSelected[14 + offset] = Game.uiScale;

        

        drawSettingsListWithSliders(g, width, height, selectedItem, time, scrollOffset, settingNames, settingValues, descriptions, sliders, toggles, sectionHeaders, pillOptions, pillSelected);

    }

    

    private void drawAudioSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, GameData gameData) {

        String[] settingNames = {"Sound Enabled", "Master Volume", "SFX Volume", "UI Volume", "Music Volume", "Spatial Audio"};

        String[] settingValues = {

            gameData.isSoundEnabled() ? "ON" : "OFF",

            String.format("%.0f%%", gameData.getMasterVolume() * 100),

            String.format("%.0f%%", gameData.getSfxVolume() * 100),

            String.format("%.0f%%", gameData.getUiVolume() * 100),

            String.format("%.0f%%", gameData.getMusicVolume() * 100),

            gameData.isSpatialAudioEnabled() ? "ON" : "OFF"

        };

        

        String[] descriptions = {

            "Enable or disable all sound effects",

            "Overall volume level (affects all sounds)",

            "Volume for game sound effects (explosions, hits, etc.)",

            "Volume for menu sounds (clicks, navigation, etc.)",

            "Volume for background music (not yet implemented)",

            "Stereo panning based on sound position (left/right channels)"

        };

        

        float[] volumes = {0, gameData.getMasterVolume(), gameData.getSfxVolume(), gameData.getUiVolume(), gameData.getMusicVolume(), 0};

        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, settingNames, settingValues, descriptions, true, volumes);

    }

    

    private void drawGameplaySettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, GameData gameData) {

        String[] settingNames = {"Resume Countdown"};

        String countdownValue = gameData.getCountdownMode() == 0 ? "None" : 

                                gameData.getCountdownMode() == 1 ? "Resume Only" : "Always";

        String[] settingValues = {countdownValue};

        String[] descriptions = {"When to show countdown: 'None', 'Resume Only' (from menu), or 'Always' (pause and resume)"};

        

        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, settingNames, settingValues, descriptions, false);

    }

    

    private void drawDebugSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset) {

        String[] settingNames = {"Show Hitboxes", "Show Track Name"};

        String[] settingValues = {Game.enableHitboxes ? "ON" : "OFF", Game.showTrackName ? "ON" : "OFF"};

        String[] descriptions = {"Debug: Show collision hitboxes for player, boss, and bullets", "Debug: Show the currently playing music track name on screen"};

        

        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, settingNames, settingValues, descriptions, false);

    }

    

    private void drawControlsSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset) {
        KeyBindManager kbm = Game.keyBindManager;
        if (kbm == null) return;
        
        KeyBindManager.Action[] actions = KeyBindManager.Action.values();
        // 22 items: Preset(0), InputDevice(1), KeyboardHeader(2), 9 keyboard actions(3-11), ControllerHeader(12), 9 controller actions(13-21)
        int totalItems = 22;
        String[] settingNames = new String[totalItems];
        String[] settingValues = new String[totalItems];
        String[] descriptions = new String[totalItems];
        
        // Item 0: Preset
        settingNames[0] = "Preset";
        settingValues[0] = "< " + kbm.getCurrentPreset().name().replace("_", " ") + " >";
        descriptions[0] = "Choose a keybinding preset (use arrows to change)";
        
        // Item 1: Input Device (read-only)
        settingNames[1] = "Input Device";
        settingValues[1] = kbm.getInputMode() == KeyBindManager.InputMode.CONTROLLER ? "Controller" : "Keyboard";
        descriptions[1] = "Current input device (auto-detected when controller is connected)";
        
        // Item 2: Keyboard section header
        settingNames[2] = "KEYBOARD";
        settingValues[2] = Game.controlsKeyboardExpanded ? "\u25BC" : "\u25CF";
        descriptions[2] = "Click or press Enter to expand/collapse keyboard keybinds";
        
        // Items 3-11: Keyboard keybinds
        for (int i = 0; i < actions.length; i++) {
            KeyBindManager.Action action = actions[i];
            settingNames[i + 3] = action.name().replace("_", " ");
            
            if (Game.waitingForKeyBind && !Game.rebindingController && Game.rebindingActionIndex == i + 1) {
                settingValues[i + 3] = ">> Press a key <<";
            } else {
                settingValues[i + 3] = KeyBindManager.getKeyName(kbm.getKey(action));
            }
            descriptions[i + 3] = "Press SPACE or ENTER to rebind | ESC to cancel";
        }
        
        // Item 12: Controller section header
        settingNames[12] = "CONTROLLER";
        settingValues[12] = Game.controlsControllerExpanded ? "\u25BC" : "\u25CF";
        descriptions[12] = "Click or press Enter to expand/collapse controller bindings";
        
        // Items 13-21: Controller bindings
        for (int i = 0; i < actions.length; i++) {
            KeyBindManager.Action action = actions[i];
            settingNames[i + 13] = action.name().replace("_", " ");
            
            if (Game.waitingForKeyBind && Game.rebindingController && Game.rebindingActionIndex == i + 1) {
                settingValues[i + 13] = ">> Press a button <<";
            } else {
                settingValues[i + 13] = kbm.getControllerButton(action).getDisplayName();
            }
            descriptions[i + 13] = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to rebind | " + keyText(KeyBindManager.Action.BACK) + " to cancel";
        }
        
        // Custom rendering for controls with collapsible sections
        int y = UIScale.px(230) - (int)scrollOffset;
        FontMetrics fm;
        
        for (int i = 0; i < totalItems; i++) {
            // Skip collapsed items
            if (!Game.controlsKeyboardExpanded && i >= 3 && i <= 11) continue;
            if (!Game.controlsControllerExpanded && i >= 13 && i <= 21) continue;
            
            boolean isSelected = i == selectedItem;
            boolean isHeader = (i == 2 || i == 12);
            
            int boxWidth = width - UIScale.px(200);
            int boxX = (width - boxWidth) / 2;
            int boxY = y - UIScale.px(10);
            int boxHeight = UIScale.px(50);
            
            // Update settings button position for click detection
            if (i < settingsButtons.length && settingsButtons[i] != null) {
                settingsButtons[i].setPosition(boxX, boxY);
                settingsButtons[i].setSize(boxWidth, boxHeight);
            }
            
            // Skip rendering if outside visible area
            if (y < UIScale.px(170) || y > height - UIScale.px(80)) {
                y += UIScale.px(78);
                continue;
            }
            
            if (isHeader) {
                // Section header styling
                if (isSelected) {
                    g.setColor(ColorPalette.withAlpha(ColorPalette.ACCENT_CYAN, 40));
                    g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
                    g.setColor(ColorPalette.ACCENT_CYAN);
                    g.setStroke(RenderCache.getStroke(2));
                    g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
                } else {
                    g.setColor(ColorPalette.withAlpha(ColorPalette.BG_MID, 120));
                    g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
                    g.setColor(ColorPalette.withAlpha(ColorPalette.ACCENT_CYAN, 80));
                    g.setStroke(RenderCache.getStroke(1));
                    g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
                }
                
                // Header icon + name
                g.setFont(FontPalette.get(Font.BOLD, 17));
                g.setColor(isSelected ? ColorPalette.ACCENT_CYAN : ColorPalette.TEXT_PRIMARY);
                String headerLabel = settingValues[i] + "  " + settingNames[i];
                g.drawString(headerLabel, boxX + UIScale.px(16), boxY + boxHeight / 2 + UIScale.px(6));
                
            } else if (isSelected) {
                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_DIM, 200));
                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
                
                // Special border for rebinding (keyboard items 3-11 or controller items 13-21)
                boolean isKeyboardRebinding = i >= 3 && i <= 11 && Game.waitingForKeyBind && !Game.rebindingController && Game.rebindingActionIndex == i - 2;
                boolean isControllerRebinding = i >= 13 && i <= 21 && Game.waitingForKeyBind && Game.rebindingController && Game.rebindingActionIndex == i - 12;
                if (isKeyboardRebinding || isControllerRebinding) {
                    g.setColor(ColorPalette.ACCENT_RED);
                } else {
                    g.setColor(ColorPalette.TEXT_GOLD);
                }
                g.setStroke(RenderCache.getStroke(2));
                g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
            } else {
                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_LIGHT, 150));
                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
            }
            
            if (!isHeader) {
                // Setting name
                g.setFont(FontPalette.get(Font.BOLD, 17));
                g.setColor(isSelected ? ColorPalette.TEXT_GOLD : ColorPalette.TEXT_PRIMARY);
                g.drawString(settingNames[i], boxX + UIScale.px(16), boxY + boxHeight / 2 + UIScale.px(6));
            }
            
            // Value rendering
            if (i == 0) {
                // Preset - draw with arrows
                g.setFont(FontPalette.get(Font.BOLD, 17));
                fm = g.getFontMetrics();
                g.setColor(ColorPalette.SUCCESS_GREEN);
                g.drawString(settingValues[i], boxX + boxWidth - fm.stringWidth(settingValues[i]) - 16, boxY + boxHeight / 2 + 6);
            } else if (i == 1) {
                // Input device - draw with icon
                g.setFont(FontPalette.get(Font.BOLD, 17));
                fm = g.getFontMetrics();
                g.setColor(ColorPalette.ACCENT_CYAN);
                g.drawString(settingValues[i], boxX + boxWidth - fm.stringWidth(settingValues[i]) - 16, boxY + boxHeight / 2 + 6);
            } else if (i >= 3 && i <= 11) {
                // Keyboard keybind - draw key in a styled box
                boolean isRebinding = Game.waitingForKeyBind && !Game.rebindingController && Game.rebindingActionIndex == i - 2;
                
                KeyBindManager.Action action = KeyBindManager.Action.values()[i - 3];
                java.awt.image.BufferedImage btnSprite = null;
                if (Game.keyBindManager != null && !isRebinding) {
                    btnSprite = Game.keyBindManager.getKeySprite(kbm.getKey(action));
                }
                
                String keyText = settingValues[i];
                g.setFont(FontPalette.get(Font.BOLD, 15));
                fm = g.getFontMetrics();
                int keyBoxWidth = Math.max(UIScale.px(70), fm.stringWidth(keyText) + UIScale.px(24));
                if (btnSprite != null) keyBoxWidth = Math.max(keyBoxWidth, UIScale.px(76));
                int keyBoxX = boxX + boxWidth - keyBoxWidth - UIScale.px(16);
                int keyBoxY = boxY + UIScale.px(4);
                int keyBoxHeight = boxHeight - UIScale.px(8);
                
                if (isRebinding) {
                    float alpha = (float)(0.5 + 0.5 * Math.sin(time * 6));
                    g.setColor(new Color(191, 97, 106, (int)(150 * alpha)));
                    g.fillRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.ACCENT_RED);
                    g.setStroke(RenderCache.getStroke(2));
                    g.drawRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.TEXT_GOLD);
                } else {
                    g.setColor(ColorPalette.withAlpha(ColorPalette.BG_MID, 200));
                    g.fillRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.withAlpha(ColorPalette.ACCENT_CYAN, 150));
                    g.setStroke(RenderCache.getStroke(1));
                    g.drawRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.TEXT_PRIMARY);
                }
                
                if (btnSprite != null) {
                    int spriteH = UIScale.px(30); int spriteW = spriteH * btnSprite.getWidth() / btnSprite.getHeight();
                    g.drawImage(btnSprite, keyBoxX + (keyBoxWidth - spriteW) / 2, keyBoxY + (keyBoxHeight - spriteH) / 2, spriteW, spriteH, null);
                } else {
                    g.drawString(keyText, keyBoxX + (keyBoxWidth - fm.stringWidth(keyText)) / 2, keyBoxY + keyBoxHeight / 2 + UIScale.px(6));
                }
            } else if (i >= 13 && i <= 21) {
                // Controller binding - draw button in a styled box
                boolean isRebinding = Game.waitingForKeyBind && Game.rebindingController && Game.rebindingActionIndex == i - 12;
                
                KeyBindManager.Action action = KeyBindManager.Action.values()[i - 13];
                java.awt.image.BufferedImage btnSprite = null;
                if (Game.keyBindManager != null && !isRebinding) {
                    btnSprite = Game.keyBindManager.getButtonSprite(kbm.getControllerButton(action));
                }
                
                String keyText = settingValues[i];
                g.setFont(FontPalette.get(Font.BOLD, 15));
                fm = g.getFontMetrics();
                int keyBoxWidth = Math.max(UIScale.px(70), fm.stringWidth(keyText) + UIScale.px(24));
                if (btnSprite != null) keyBoxWidth = Math.max(keyBoxWidth, UIScale.px(76));
                int keyBoxX = boxX + boxWidth - keyBoxWidth - UIScale.px(16);
                int keyBoxY = boxY + UIScale.px(4);
                int keyBoxHeight = boxHeight - UIScale.px(8);
                
                if (isRebinding) {
                    float alpha = (float)(0.5 + 0.5 * Math.sin(time * 6));
                    g.setColor(new Color(191, 97, 106, (int)(150 * alpha)));
                    g.fillRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.ACCENT_RED);
                    g.setStroke(RenderCache.getStroke(2));
                    g.drawRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.TEXT_GOLD);
                } else {
                    g.setColor(ColorPalette.withAlpha(ColorPalette.BG_MID, 200));
                    g.fillRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.withAlpha(ColorPalette.ACCENT_CYAN, 150));
                    g.setStroke(RenderCache.getStroke(1));
                    g.drawRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, UIScale.px(8), UIScale.px(8));
                    g.setColor(ColorPalette.TEXT_PRIMARY);
                }
                
                if (btnSprite != null) {
                    int spriteH = UIScale.px(30); int spriteW = spriteH * btnSprite.getWidth() / btnSprite.getHeight();
                    g.drawImage(btnSprite, keyBoxX + (keyBoxWidth - spriteW) / 2, keyBoxY + (keyBoxHeight - spriteH) / 2, spriteW, spriteH, null);
                } else {
                    g.drawString(keyText, keyBoxX + (keyBoxWidth - fm.stringWidth(keyText)) / 2, keyBoxY + keyBoxHeight / 2 + UIScale.px(6));
                }
            }
            
            // Draw description below if selected
            if (isSelected && !isHeader) {
                g.setFont(FontPalette.get(Font.ITALIC, 13));
                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_PRIMARY, 180));
                fm = g.getFontMetrics();
                int descX = Math.max(UIScale.px(10), (width - fm.stringWidth(descriptions[i])) / 2);
                g.drawString(descriptions[i], descX, boxY + boxHeight + UIScale.px(16));
            } else if (isSelected && isHeader) {
                g.setFont(FontPalette.get(Font.ITALIC, 13));
                g.setColor(ColorPalette.withAlpha(ColorPalette.ACCENT_CYAN, 150));
                fm = g.getFontMetrics();
                int descX = Math.max(UIScale.px(10), (width - fm.stringWidth(descriptions[i])) / 2);
                g.drawString(descriptions[i], descX, boxY + boxHeight + UIScale.px(16));
            }
            
            y += UIScale.px(78);
        }
    }

    

    private void drawSettingsList(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, boolean showSliders) {

        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, names, values, descriptions, showSliders, null);

    }

    

    private void drawSettingsList(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, boolean showSliders, float[] sliderValues) {

        int boxWidth = width - UIScale.px(200);

        int boxHeight = UIScale.px(50);

        int itemSpacing = UIScale.px(78);

        int boxX = (width - boxWidth) / 2;

        int y = UIScale.px(230) - (int)scrollOffset;

        FontMetrics fm;

        

        // Reset slider click targets for this tab

        if (sliderMinusBtnX == null || sliderMinusBtnX.length < names.length) {

            sliderMinusBtnX = new int[names.length];

            sliderPlusBtnX = new int[names.length];

            sliderBtnYPos = new int[names.length];
            sliderTrackStartX = new int[names.length];
            sliderTrackEndX = new int[names.length];

        }

        for (int i = 0; i < names.length; i++) {

            sliderMinusBtnX[i] = -1;

            sliderPlusBtnX[i] = -1;
            sliderTrackStartX[i] = -1;
            sliderTrackEndX[i] = -1;

        }

        

        for (int i = 0; i < names.length; i++) {

            boolean isSelected = i == selectedItem;

            int boxY = y - 10;

            

            // Update button position for click detection

            if (i < settingsButtons.length && settingsButtons[i] != null) {

                settingsButtons[i].setPosition(boxX, boxY);

                settingsButtons[i].setSize(boxWidth, boxHeight);

            }

            

            // Skip if outside visible area

            if (y < UIScale.px(170) || y > height - UIScale.px(80)) {

                y += itemSpacing;

                continue;

            }

            

            if (isSelected) {

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_DIM, 200));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

                g.setColor(ColorPalette.TEXT_GOLD);

                g.setStroke(RenderCache.getStroke(2));

                g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

            } else {

                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_LIGHT, 150));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

            }

            

            // Setting name

            g.setFont(FontPalette.get(Font.BOLD, 17));

            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : ColorPalette.TEXT_PRIMARY);

            int textYBase = boxY + boxHeight / 2 + UIScale.px(6);

            g.drawString(names[i], boxX + UIScale.px(16), textYBase);

            

            // Value or slider

            if (showSliders && sliderValues != null && i > 0 && !values[i].equals("ON") && !values[i].equals("OFF")) {

                // Volume slider with +/- buttons

                float progress = sliderValues[i];

                int btnSize = sliderBtnSize;

                int centerY = boxY + boxHeight / 2;

                int rightMargin = UIScale.px(16);

                

                g.setFont(FontPalette.get(Font.BOLD, 15));

                fm = g.getFontMetrics();

                int valueW = fm.stringWidth(values[i]) + UIScale.px(12);

                

                int plusBtnX = boxX + boxWidth - rightMargin - valueW - btnSize;

                int sliderEndX = plusBtnX - UIScale.px(8);

                int sliderStartX = boxX + boxWidth / 2 - UIScale.px(20);

                int minusBtnX = sliderStartX - btnSize - UIScale.px(8);

                int sliderW = sliderEndX - sliderStartX;

                

                // Store for click detection

                sliderMinusBtnX[i] = minusBtnX;

                sliderPlusBtnX[i] = plusBtnX;

                sliderBtnYPos[i] = centerY - btnSize / 2;
                sliderTrackStartX[i] = sliderStartX;
                sliderTrackEndX[i] = sliderEndX;

                

                // [-] button

                g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

                g.fillRoundRect(minusBtnX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

                g.setColor(ColorPalette.TEXT_PRIMARY);

                g.setFont(FONT_EXTRA_SMALL_16);

                fm = g.getFontMetrics();

                g.drawString("\u2212", minusBtnX + (btnSize - fm.stringWidth("\u2212")) / 2, centerY + UIScale.px(6));

                

                // Slider bar

                int sliderH = UIScale.px(6);

                int sliderY = centerY - sliderH / 2;

                g.setColor(ColorPalette.BG_DARK);

                g.fillRoundRect(sliderStartX, sliderY, sliderW, sliderH, 3, 3);

                int fillW = (int)(sliderW * progress);

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.fillRoundRect(sliderStartX, sliderY, Math.max(fillW, 3), sliderH, 3, 3);

                

                // Handle

                int handleX = sliderStartX + fillW - UIScale.px(5);

                g.setColor(ColorPalette.TEXT_GOLD);

                g.fillOval(handleX, centerY - UIScale.px(7), UIScale.px(10), UIScale.px(14));

                

                // [+] button

                g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

                g.fillRoundRect(plusBtnX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

                g.setColor(ColorPalette.TEXT_PRIMARY);

                g.setFont(FONT_EXTRA_SMALL_16);

                fm = g.getFontMetrics();

                g.drawString("+", plusBtnX + (btnSize - fm.stringWidth("+")) / 2, centerY + UIScale.px(6));

                

                // Value text

                g.setFont(FontPalette.get(Font.BOLD, 15));

                g.setColor(ColorPalette.TEXT_PRIMARY);

                fm = g.getFontMetrics();

                g.drawString(values[i], boxX + boxWidth - rightMargin - fm.stringWidth(values[i]), centerY + UIScale.px(6));

            } else if (values[i].equals("ON") || values[i].equals("OFF")) {

                // Draw toggle switch

                drawToggleSwitch(g, boxX, boxY, boxWidth, boxHeight, values[i].equals("ON"), isSelected);

            } else {

                // Regular value text

                g.setFont(FontPalette.get(Font.BOLD, 17));

                fm = g.getFontMetrics();

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.drawString(values[i], boxX + boxWidth - fm.stringWidth(values[i]) - UIScale.px(16), textYBase);

            }

            

            // Draw description below if selected

            if (isSelected) {

                g.setFont(FontPalette.get(Font.ITALIC, 13));

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_PRIMARY, 180));

                fm = g.getFontMetrics();

                int descX = Math.max(UIScale.px(10), (width - fm.stringWidth(descriptions[i])) / 2);

                g.drawString(descriptions[i], descX, boxY + boxHeight + UIScale.px(16));

            }

            

            y += itemSpacing;

        }

    }

    

    private void drawSettingsListWithSliders(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, float[][] sliders, boolean[] toggles) {

        drawSettingsListWithSliders(g, width, height, selectedItem, time, scrollOffset, names, values, descriptions, sliders, toggles, null, null, null);

    }

    

    private void drawSettingsListWithSliders(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, float[][] sliders, boolean[] toggles, String[] sectionHeaders) {

        drawSettingsListWithSliders(g, width, height, selectedItem, time, scrollOffset, names, values, descriptions, sliders, toggles, sectionHeaders, null, null);

    }

    

    private void drawSettingsListWithSliders(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, float[][] sliders, boolean[] toggles, String[] sectionHeaders, String[][] pillOptions, int[] pillSelected) {

        int boxWidth = width - UIScale.px(200);

        int boxHeight = UIScale.px(50);

        int itemSpacing = UIScale.px(78);

        int boxX = (width - boxWidth) / 2;

        int y = UIScale.px(230) - (int)scrollOffset;

        FontMetrics fm;

        

        // Initialize click target arrays if needed

        if (pillClickTargets == null || pillClickTargets.length < names.length) {

            pillClickTargets = new int[names.length][];

            pillClickTargetY = new int[names.length];

            sliderMinusBtnX = new int[names.length];

            sliderPlusBtnX = new int[names.length];

            sliderBtnYPos = new int[names.length];
            sliderTrackStartX = new int[names.length];
            sliderTrackEndX = new int[names.length];

        }

        for (int i = 0; i < names.length; i++) {

            pillClickTargets[i] = null;

            sliderMinusBtnX[i] = -1;

            sliderPlusBtnX[i] = -1;
            sliderTrackStartX[i] = -1;
            sliderTrackEndX[i] = -1;

        }

        

        for (int i = 0; i < names.length; i++) {

            // Draw section header if this item starts a new section

            if (sectionHeaders != null && i < sectionHeaders.length && sectionHeaders[i] != null) {

                if (y >= UIScale.px(170) && y <= height - UIScale.px(80)) {

                    g.setFont(FONT_EXTRA_SMALL_13);

                    if (i > 0) {

                        g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 120));

                        g.setStroke(RenderCache.getStroke(1));

                        g.drawLine(boxX, y - UIScale.px(14), boxX + boxWidth, y - UIScale.px(14));

                    }

                    g.setColor(ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 220));

                    g.drawString(sectionHeaders[i], boxX + UIScale.px(4), y + UIScale.px(2));

                }

                y += UIScale.px(24);

            }

            

            boolean isSelected = i == selectedItem;

            int boxY = y - 10;

            

            // Update button position for click detection

            if (i < settingsButtons.length && settingsButtons[i] != null) {

                settingsButtons[i].setPosition(boxX, boxY);

                settingsButtons[i].setSize(boxWidth, boxHeight);

            }

            

            // Skip if outside visible area

            if (y < 170 || y > height - 80) {

                y += itemSpacing;

                continue;

            }

            

            // Background

            if (isSelected) {

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_DIM, 200));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

                g.setColor(ColorPalette.TEXT_GOLD);

                g.setStroke(RenderCache.getStroke(2));

                g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

            } else {

                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_LIGHT, 150));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

            }

            

            // Setting name

            g.setFont(FontPalette.get(Font.BOLD, 17));

            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : ColorPalette.TEXT_PRIMARY);

            int textY = boxY + boxHeight / 2 + UIScale.px(6);

            g.drawString(names[i], boxX + UIScale.px(16), textY);

            

            // Determine widget type and render

            boolean hasPill = pillOptions != null && i < pillOptions.length && pillOptions[i] != null;

            boolean hasSlider = !hasPill && sliders[i][0] == 1;

            

            if (hasPill) {

                drawPillSelector(g, i, boxX, boxY, boxWidth, boxHeight, pillOptions[i], pillSelected[i], isSelected);

            } else if (hasSlider) {

                drawSliderWithButtons(g, i, boxX, boxY, boxWidth, boxHeight, sliders[i], values[i], isSelected);

            } else {

                drawToggleSwitch(g, boxX, boxY, boxWidth, boxHeight, toggles[i], isSelected);

            }

            

            // Description below when selected

            if (isSelected && descriptions != null && i < descriptions.length) {

                g.setFont(FontPalette.get(Font.ITALIC, 13));

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_PRIMARY, 180));

                fm = g.getFontMetrics();

                int descX = Math.max(UIScale.px(10), (width - fm.stringWidth(descriptions[i])) / 2);

                g.drawString(descriptions[i], descX, boxY + boxHeight + UIScale.px(16));

            }

            

            y += itemSpacing;

        }

    }

    

    private void drawPillSelector(Graphics2D g, int settingIndex, int boxX, int boxY, int boxWidth, int boxHeight, String[] options, int selected, boolean isRowSelected) {

        g.setFont(FONT_EXTRA_SMALL_12);

        FontMetrics fm = g.getFontMetrics();

        

        int pillH = pillClickH;

        int pillGap = UIScale.px(3);

        int pillPadding = UIScale.px(12);

        

        // Calculate pill widths

        int[] pillW = new int[options.length];

        int totalW = 0;

        for (int j = 0; j < options.length; j++) {

            pillW[j] = fm.stringWidth(options[j]) + pillPadding * 2;

            totalW += pillW[j];

        }

        totalW += (options.length - 1) * pillGap;

        

        int startX = boxX + boxWidth - totalW - UIScale.px(16);

        int pillY = boxY + (boxHeight - pillH) / 2;

        int px = startX;

        

        // Store click targets

        int[] targets = new int[options.length * 2];

        pillClickTargetY[settingIndex] = pillY;

        

        for (int j = 0; j < options.length; j++) {

            boolean isSel = j == selected;

            targets[j * 2] = px;

            targets[j * 2 + 1] = pillW[j];

            

            // Pill background

            if (isSel) {

                g.setColor(ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 220));

            } else if (isRowSelected) {

                g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 200));

            } else {

                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_MID, 180));

            }

            g.fillRoundRect(px, pillY, pillW[j], pillH, 6, 6);

            

            // Border for selected pill

            if (isSel) {

                g.setColor(ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 255));

                g.setStroke(RenderCache.getStroke(1));

                g.drawRoundRect(px, pillY, pillW[j], pillH, 6, 6);

            }

            

            // Text

            g.setFont(FontPalette.get(isSel ? Font.BOLD : Font.PLAIN, 12));

            fm = g.getFontMetrics();

            if (isSel) {

                g.setColor(ColorPalette.BG_DARK);

            } else {

                g.setColor(new Color(216, 222, 233, isRowSelected ? 200 : 140));

            }

            int textX = px + (pillW[j] - fm.stringWidth(options[j])) / 2;

            int textYPos = pillY + (pillH + fm.getAscent() - fm.getDescent()) / 2;

            g.drawString(options[j], textX, textYPos);

            

            px += pillW[j] + pillGap;

        }

        

        pillClickTargets[settingIndex] = targets;

    }

    

    private void drawSliderWithButtons(Graphics2D g, int settingIndex, int boxX, int boxY, int boxWidth, int boxHeight, float[] sliderInfo, String value, boolean isSelected) {

        float min = sliderInfo[1];

        float max = sliderInfo[2];

        float current = sliderInfo[3];

        float progress = (current - min) / (max - min);

        

        int btnSize = sliderBtnSize;

        int centerY = boxY + boxHeight / 2;

        int rightMargin = UIScale.px(16);

        

        // Layout from right: value text, [+], slider bar, [-]

        g.setFont(FontPalette.get(Font.BOLD, 15));

        FontMetrics fm = g.getFontMetrics();

        int valueW = fm.stringWidth(value) + UIScale.px(12);

        

        int plusX = boxX + boxWidth - rightMargin - valueW - btnSize;

        int sliderEndX = plusX - UIScale.px(8);

        int sliderStartX = boxX + boxWidth / 2 - UIScale.px(20);

        int minusX = sliderStartX - btnSize - UIScale.px(8);

        int sliderWidth = sliderEndX - sliderStartX;

        

        // Store for click detection

        sliderMinusBtnX[settingIndex] = minusX;

        sliderPlusBtnX[settingIndex] = plusX;

        sliderBtnYPos[settingIndex] = centerY - btnSize / 2;
        if (sliderTrackStartX != null && settingIndex < sliderTrackStartX.length) {
            sliderTrackStartX[settingIndex] = sliderStartX;
            sliderTrackEndX[settingIndex] = sliderEndX;
        }

        

        // [-] button

        g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

        g.fillRoundRect(minusX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_EXTRA_SMALL_16);

        fm = g.getFontMetrics();

        g.drawString("\u2212", minusX + (btnSize - fm.stringWidth("\u2212")) / 2, centerY + UIScale.px(6));

        

        // Slider bar

        int sliderH = UIScale.px(6);

        int sliderY = centerY - sliderH / 2;

        g.setColor(ColorPalette.BG_DARK);

        g.fillRoundRect(sliderStartX, sliderY, sliderWidth, sliderH, 3, 3);

        int fillW = (int)(sliderWidth * progress);

        g.setColor(ColorPalette.SUCCESS_GREEN);

        g.fillRoundRect(sliderStartX, sliderY, Math.max(fillW, 3), sliderH, 3, 3);

        

        // Handle

        int handleX = sliderStartX + fillW - UIScale.px(5);

        g.setColor(ColorPalette.TEXT_GOLD);

        g.fillOval(handleX, centerY - UIScale.px(7), UIScale.px(10), UIScale.px(14));

        

        // [+] button

        g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

        g.fillRoundRect(plusX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_EXTRA_SMALL_16);

        fm = g.getFontMetrics();

        g.drawString("+", plusX + (btnSize - fm.stringWidth("+")) / 2, centerY + UIScale.px(6));

        

        // Value text

        g.setFont(FontPalette.get(Font.BOLD, 15));

        g.setColor(ColorPalette.TEXT_PRIMARY);

        fm = g.getFontMetrics();

        g.drawString(value, boxX + boxWidth - rightMargin - fm.stringWidth(value), centerY + UIScale.px(6));

    }

    

    private void drawToggleSwitch(Graphics2D g, int boxX, int boxY, int boxWidth, int boxHeight, boolean isOn, boolean isSelected) {

        int toggleW = UIScale.px(44);

        int toggleH = UIScale.px(22);

        int toggleX = boxX + boxWidth - toggleW - UIScale.px(16);

        int toggleY = boxY + (boxHeight - toggleH) / 2;

        

        // Background

        g.setColor(isOn ? ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 200) : ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 200));

        g.fillRoundRect(toggleX, toggleY, toggleW, toggleH, UIScale.px(11), UIScale.px(11));

        

        // Circle

        int circleSize = UIScale.px(18);

        int circleX = isOn ? toggleX + toggleW - circleSize - UIScale.px(2) : toggleX + UIScale.px(2);

        int circleY = toggleY + UIScale.px(2);

        g.setColor(ColorPalette.TEXT_WHITE);

        g.fillOval(circleX, circleY, circleSize, circleSize);

        

        // ON/OFF label

        g.setFont(FONT_EXTRA_SMALL_12);

        FontMetrics fm = g.getFontMetrics();

        String label = isOn ? "ON" : "OFF";

        g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_PRIMARY, 180));

        g.drawString(label, toggleX - fm.stringWidth(label) - UIScale.px(8), toggleY + UIScale.px(15));

    }

    

    public void drawDebug(Graphics2D g, int width, int height, double time, int selectedOption, int debugSetLevelValue, int debugLeaderboardLevel) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "DEBUG MENU", width, 80, ColorPalette.ACCENT_RED, ColorPalette.ACCENT_ORANGE, time);

        

        g.setColor(new Color(255, 200, 200));

        g.setFont(FONT_INFO);

        String subtitle = "Developer/Cheat Menu - Navigate with UP/DOWN, Activate with SPACE";

        FontMetrics fm = g.getFontMetrics();

        g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, 120);

        

        // Debug options

        String[] options = {

            "Unlock All Levels (1-28)",

            "Give $10,000",

            "Max All Upgrades",

            "Give $1,000",

            "Give $100",

            "Unlock All Active Items",

            "Unlock Risk Contracts",

            "Toggle Showcase Unlock All",

            "Unlock All Passive Upgrades",

            "Preview Item & Contract Popups",

            "Preview Passive Upgrade Popups",

            "Set Unlocked Level: \u25C0 " + debugSetLevelValue + " \u25B6",
            "Unlock Endless Mode",
            "Reset Leaderboard Times",
            "Test Leaderboard: Level \u25C0 " + debugLeaderboardLevel + " \u25B6"

        };

        

        Color[] colors = {

            ColorPalette.ACCENT_YELLOW,  // Gold

            new Color(0, 255, 127),      // Spring green

            new Color(138, 43, 226),     // Blue violet

            RenderCache.ORANGE_255_165_0, // Orange

            new Color(135, 206, 250),    // Light sky blue

            ColorPalette.SUCCESS_GREEN,  // Green for active items

            new Color(255, 99, 71),      // Tomato red for risk contracts

            new Color(255, 215, 100),    // Yellow for showcase unlock

            new Color(180, 120, 255),    // Purple for passive upgrades

            new Color(100, 200, 255),    // Cyan for item/contract preview

            new Color(200, 150, 255),    // Lavender for passive preview

            new Color(255, 140, 0),      // Dark orange for set level
            new Color(160, 100, 255),    // Purple for endless mode
            new Color(255, 80, 80),       // Red for leaderboard reset
            new Color(100, 255, 200)     // Teal for test leaderboard

        };

        

        // Show last played SFX name

        SoundManager sm = SoundManager.getInstance();

        String lastSfx = sm.getLastPlayedSound();

        long elapsed = System.currentTimeMillis() - sm.getLastPlayedTime();

        if (lastSfx != null && !lastSfx.isEmpty() && elapsed < 3000) {

            float alpha = Math.min(1f, Math.max(0f, 1f - (elapsed / 3000f)));

            g.setComposite(RenderCache.getAlpha(alpha));

            g.setFont(FontPalette.get(Font.BOLD, 22));

            FontMetrics sfxFm = g.getFontMetrics();

            String sfxLabel = "SFX: " + lastSfx;

            int sfxW = sfxFm.stringWidth(sfxLabel) + UIScale.px(24);

            int sfxH = UIScale.px(36);

            int sfxX = (width - sfxW) / 2;

            int sfxY = UIScale.px(140);

            g.setColor(new Color(0, 0, 0, 160));

            g.fillRoundRect(sfxX, sfxY, sfxW, sfxH, UIScale.px(12), UIScale.px(12));

            g.setColor(RenderCache.RED_255_120_120);

            g.drawString(sfxLabel, sfxX + UIScale.px(12), sfxY + UIScale.px(25));

            g.setComposite(RenderCache.getAlpha(1.0f));

        }



        // Draw selectable button list

        int startY = UIScale.px(170);

        int spacing = UIScale.px(52);

        int buttonWidth = UIScale.px(500);

        int buttonHeight = UIScale.px(44);

        g.setFont(FontPalette.get(Font.BOLD, 22));



        for (int i = 0; i < options.length; i++) {

            fm = g.getFontMetrics();

            int x = (width - buttonWidth) / 2;

            int y = startY + i * spacing;

            boolean isSelected = (i == selectedOption);

            

            // Draw button background

            if (isSelected) {

                // Selected button: bright glow background

                g.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 40));

                g.fillRoundRect(x - UIScale.px(10), y - UIScale.px(6), buttonWidth + UIScale.px(20), buttonHeight, UIScale.px(16), UIScale.px(16));

                // Border

                g.setColor(colors[i]);

                g.setStroke(RenderCache.getStroke(3));

                g.drawRoundRect(x - UIScale.px(10), y - UIScale.px(6), buttonWidth + UIScale.px(20), buttonHeight, UIScale.px(16), UIScale.px(16));

                g.setStroke(RenderCache.getStroke(1));

                

                // Selection indicator arrow

                g.setFont(FontPalette.get(Font.BOLD, 26));

                g.setColor(colors[i]);

                g.drawString("\u25B6", x - UIScale.px(30), y + UIScale.px(28));

                g.setFont(FontPalette.get(Font.BOLD, 22));

            } else {

                // Unselected button: subtle dark background

                g.setColor(new Color(0, 0, 0, 80));

                g.fillRoundRect(x - UIScale.px(10), y - UIScale.px(6), buttonWidth + UIScale.px(20), buttonHeight, UIScale.px(16), UIScale.px(16));

                g.setColor(new Color(100, 100, 100, 100));

                g.setStroke(RenderCache.getStroke(1));

                g.drawRoundRect(x - UIScale.px(10), y - UIScale.px(6), buttonWidth + UIScale.px(20), buttonHeight, UIScale.px(16), UIScale.px(16));

            }

            

            // Draw text centered in button

            int textX = (width - fm.stringWidth(options[i])) / 2;

            int textY = y + UIScale.px(28);

            

            // Draw shadow

            g.setColor(RenderCache.BLACK_100);

            g.drawString(options[i], textX + 2, textY + 2);

            

            // Draw text

            if (isSelected) {

                g.setColor(Color.WHITE);

            } else {

                g.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 180));

            }

            g.drawString(options[i], textX, textY);

        }

    }

    

    private void drawDesert(Graphics2D g, int width, int height, double scroll) {

        // Draw sand dunes and cacti

        for (int row = -1; row < 8; row++) {

            int y = (int)(row * 120 - scroll * 8);

            if (y > -80 && y < height + 80) {

                // Sand dunes

                g.setColor(new Color(237, 201, 175, 150));

                int duneX = (row * 200) % width;

                g.fillOval(duneX - 50, y, 150, 60);

                g.fillOval(duneX + 100, y + 20, 200, 80);

                

                // Cacti

                if (row % 2 == 1) {

                    int cactusX = (row * 173) % (width - 40);

                    g.setColor(new Color(107, 142, 35, 180));

                    g.fillRect(cactusX + 15, y + 30, 10, 40);

                    g.fillRect(cactusX + 5, y + 40, 10, 15);

                    g.fillRect(cactusX + 25, y + 45, 10, 15);

                }

            }

        }

    }

    

    private void drawMountains(Graphics2D g, int width, int height, double scroll) {

        // Draw mountain peaks from above

        for (int row = -1; row < 6; row++) {

            int y = (int)(row * 150 - scroll * 8);

            if (y > -100 && y < height + 100) {

                int baseX = (row * 117) % (width - 200);

                // Mountain mass

                g.setColor(new Color(105, 105, 105, 150));

                int[] xPoints = {baseX, baseX + 100, baseX + 200, baseX + 150, baseX + 50};

                int[] yPoints = {y + 100, y, y + 100, y + 80, y + 80};

                g.fillPolygon(xPoints, yPoints, 5);

                

                // Snow cap

                g.setColor(RenderCache.WHITE_180);

                int[] snowX = {baseX + 70, baseX + 100, baseX + 130};

                int[] snowY = {y + 30, y, y + 30};

                g.fillPolygon(snowX, snowY, 3);

            }

        }

    }

    

    private void drawLakes(Graphics2D g, int width, int height, double scroll) {

        // Draw lakes and rivers

        for (int row = -1; row < 10; row++) {

            int y = (int)(row * 90 - scroll * 8);

            if (y > -60 && y < height + 60) {

                // Rivers (winding)

                g.setColor(new Color(30, 144, 255, 130));

                int riverX = width / 3 + (int)(Math.sin(row * 0.5) * 100);

                g.fillRoundRect(riverX, y, 80, 100, 30, 30);

                

                // Lakes

                if (row % 3 == 0) {

                    int lakeX = (row * 211) % (width - 150);

                    g.setColor(new Color(64, 164, 223, 140));

                    g.fillOval(lakeX, y + 20, 120, 80);

                    

                    // Grass around lake

                    g.setColor(new Color(34, 139, 34, 120));

                    g.fillOval(lakeX - 10, y + 10, 140, 100);

                }

            }

        }

    }

    

    private void drawCity(Graphics2D g, int width, int height, double scroll) {

        // Draw buildings from above (top-down)

        for (int row = -1; row < 15; row++) {

            for (int col = 0; col < 10; col++) {

                int x = col * 130 + ((row % 2) * 65);

                int y = (int)(row * 60 - scroll * 8);

                if (y > -50 && y < height + 50) {

                    // Buildings

                    int buildingSize = 40 + ((row + col) % 3) * 15;

                    g.setColor(new Color(128, 128, 128, 180));

                    g.fillRect(x, y, buildingSize, buildingSize);

                    

                    // Windows/details

                    g.setColor(new Color(255, 255, 200, 150));

                    for (int i = 0; i < 3; i++) {

                        for (int j = 0; j < 3; j++) {

                            g.fillRect(x + 5 + i * 12, y + 5 + j * 12, 8, 8);

                        }

                    }

                }

            }

        }

    }

    

    private void drawTundra(Graphics2D g, int width, int height, double scroll) {

        // Draw snowy tundra with rocks and ice

        for (int row = -1; row < 12; row++) {

            int y = (int)(row * 70 - scroll * 8);

            if (y > -50 && y < height + 50) {

                // Snow patches

                g.setColor(new Color(255, 255, 255, 140));

                for (int i = 0; i < 5; i++) {

                    int x = (row * 83 + i * 230) % width;

                    g.fillOval(x, y, 60 + i * 10, 40 + i * 5);

                }

                

                // Rocks

                if (row % 2 == 0) {

                    int rockX = (row * 149) % (width - 50);

                    g.setColor(new Color(105, 105, 105, 160));

                    g.fillOval(rockX, y + 15, 35, 25);

                    g.fillOval(rockX + 20, y + 20, 30, 20);

                }

            }

        }

    }

    

    // Visual effects methods

    

    private void applyBloom(Graphics2D g, Player player, Boss boss, List<Bullet> bullets, List<Particle> particles, boolean bossVulnerable) {

        // Bloom effect: draw glowing halos around bright objects

        Composite originalComposite = g.getComposite();

        

        // Note: Boss glow removed as it was drawing on top of the boss sprite

        // The vulnerability indicator is now drawn UNDER the boss in the main render loop

        

        // Glow around player

        if (player != null) {

            for (int i = 2; i > 0; i--) {

                float alpha = 0.1f / i;

                g.setComposite(RenderCache.getAlpha(alpha));

                g.setColor(RenderCache.BLUE_150_200_255);

                double glowSize = 50 + (i * 15);

                g.fillOval((int)(player.getX() - glowSize/2), (int)(player.getY() - glowSize/2), (int)glowSize, (int)glowSize);

            }

        }

        

        // Glow around bright particles â€” CAPPED for performance
        // Skip particle bloom entirely when too many (saves 200-400 fillOval/frame)
        int particleCount = particles.size();
        if (particleCount <= 80) {
            int bloomLimit = Math.min(particleCount, 20);
            int step = Math.max(1, particleCount / Math.max(1, bloomLimit));
            int drawn = 0;
            for (int idx = 0; idx < particleCount && drawn < bloomLimit; idx += step) {
                Particle p = particles.get(idx);
                if (p != null && p.isAlive()) {
                    g.setComposite(RenderCache.getAlpha(0.05f));
                    g.setColor(PANEL_MEGA_LABEL);
                    double glowSize = 23;
                    g.fillOval((int)(p.getX() - glowSize/2), (int)(p.getY() - glowSize/2), (int)glowSize, (int)glowSize);
                    drawn++;
                }
            }
        }

        

        g.setComposite(originalComposite);

    }

    

    private void applyMotionBlur(Graphics2D g, Player player) {

        // Motion blur: draw faded trail behind fast-moving player

        double vx = player.getVX();

        double vy = player.getVY();

        double speed = Math.sqrt(vx * vx + vy * vy);

        

        if (speed > 3) { // Only apply if moving fast

            Composite originalComposite = g.getComposite();

            int trailLength = (int)Math.min(speed * 2, 15);

            

            for (int i = 1; i <= trailLength; i++) {

                float alpha = 0.3f * (1 - i / (float)trailLength);

                g.setComposite(RenderCache.getAlpha(alpha));

                

                double trailX = player.getX() - (vx * i * 0.8);

                double trailY = player.getY() - (vy * i * 0.8);

                

                g.setColor(RenderCache.BLUE_150_200_255);

                g.fillOval((int)(trailX - 15), (int)(trailY - 15), 30, 30);

            }

            

            g.setComposite(originalComposite);

        }

    }

    

    private void applyChromaticAberration(Graphics2D g, int width, int height) {
        // Chromatic aberration: subtle color fringing at screen edges
        Composite originalComposite = g.getComposite();
        g.setComposite(RenderCache.getAlpha(0.03f));
        
        // Red fringe on left edge
        g.setColor(Color.RED);
        g.fillRect(0, 0, 15, height);
        
        // Cyan fringe on right edge
        g.setColor(Color.CYAN);
        g.fillRect(width - 15, 0, 15, height);
        
        // Blue fringe on top
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, 15);
        
        // Yellow fringe on bottom
        g.setColor(Color.YELLOW);
        g.fillRect(0, height - 15, width, 15);
        
        g.setComposite(originalComposite);
    }

    

    private String getItemIcon(int itemIndex) {

        if (itemIndex == 0) return ">"; // Continue

        

        // Use sorted order from ShopManager for correct upgrade lookup

        if (passiveUpgradeManager != null && itemIndex >= 1) {

            int upgradeIndex = shopManager.getOriginalUpgradeIndex(itemIndex);

            if (upgradeIndex >= 0 && upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {

                PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);

                return getPassiveIcon(upgrade.getType());

            }

        }

        return "?";

    }

    

    private String encryptItemName(String name) {

        // Generate a "corrupted data" string based on the item name length

        StringBuilder sb = new StringBuilder();

        String glitchChars = "#$%&@!?*^~";

        for (int i = 0; i < name.length(); i++) {

            if (name.charAt(i) == ' ') {

                sb.append(' ');

            } else {

                sb.append(glitchChars.charAt((i * 7 + name.length()) % glitchChars.length()));

            }

        }

        return sb.toString();

    }

    

    private int getUpgradeLevel(int itemIndex) {

        if (itemIndex == 0) return 0;

        

        // All upgrades now use PassiveUpgradeManager

        if (passiveUpgradeManager != null && itemIndex >= 1) {

            int upgradeIndex = itemIndex - 1;

            if (upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {

                return passiveUpgradeManager.getAllUpgrades().get(upgradeIndex).getCurrentLevel();

            }

        }

        return 0;

    }

    

    private int getUpgradeMaxLevel(int itemIndex) {

        switch (itemIndex) {

            case 1: return GameData.MAX_SPEED_LEVEL;

            case 2: return GameData.MAX_BULLET_SLOW_LEVEL;

            case 3: return GameData.MAX_LUCKY_DODGE_LEVEL;

            case 4: return GameData.MAX_ATTACK_WINDOW_LEVEL;

            default: return 1;

        }

    }

    

    private void applyVignette(Graphics2D g, int width, int height) {

        // Check if we need to regenerate the cached vignette

        if (cachedVignette == null || cachedVignetteWidth != width || cachedVignetteHeight != height) {

            // Cache at full resolution â€” eliminates per-frame scaling blit overhead
            // (profiling showed 14.5s / 3% CPU on the upscale drawImage path)
            if (cachedVignette != null) cachedVignette.flush();
            cachedVignette = Game.createOptimalImage(width, height, true);

            Graphics2D vg = cachedVignette.createGraphics();

            vg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            

            // Create radial gradient from center

            int centerX = width / 2;

            int centerY = height / 2;

            int radius = (int)Math.sqrt(centerX * centerX + centerY * centerY) * 3;

            

            // Draw multiple layers for smooth gradient

            for (int i = 0; i < 4; i++) {

                float alpha = Math.min(1.0f, 0.36f * (i + 1));

                

                vg.setComposite(RenderCache.getAlpha(alpha));

                

                // Draw darkened edges

                RadialGradientPaint gradient = new RadialGradientPaint(

                    centerX, centerY, radius,

                    new float[]{0.0f, 0.2f, 1.0f},

                    new Color[]{RenderCache.BLACK_0, RenderCache.BLACK_0, Color.BLACK}

                );

                vg.setPaint(gradient);

                vg.fillRect(0, 0, width, height);

            }

            

            vg.dispose();

            cachedVignetteWidth = width;

            cachedVignetteHeight = height;

        }

        

        // 1:1 blit â€” no scaling overhead (was drawImage with upscale, 14.5s CPU)

        g.drawImage(cachedVignette, 0, 0, null);

    }

    /** Bake the 4 edge gradient strips + 4 corner radials to BufferedImages (called once). */
    private static void bakeLevelBounds(int worldW, int worldH, int gs, int go) {
        Color sb = WORLD_EDGE_80;
        Color tr = RenderCache.BLACK_0;
        float[] cd = {0.0f, 1.0f};
        Color[] cc = {tr, sb};

        int stripW = worldW + go * 2 - gs * 2; // width of top/bottom strips
        int stripH = worldH + go * 2 - gs * 2; // height of left/right strips

        // Top strip: vertical gradient sb(top) â†’ tr(bottom)
        bakedEdgeTop = Game.createOptimalImage(stripW, gs, true);
        Graphics2D gt = bakedEdgeTop.createGraphics();
        gt.setPaint(new GradientPaint(0, 0, sb, 0, gs, tr));
        gt.fillRect(0, 0, stripW, gs);
        gt.dispose();

        // Bottom strip: vertical gradient tr(top) â†’ sb(bottom)
        bakedEdgeBottom = Game.createOptimalImage(stripW, gs, true);
        Graphics2D gb = bakedEdgeBottom.createGraphics();
        gb.setPaint(new GradientPaint(0, 0, tr, 0, gs, sb));
        gb.fillRect(0, 0, stripW, gs);
        gb.dispose();

        // Left strip: horizontal gradient sb(left) â†’ tr(right)
        bakedEdgeLeft = Game.createOptimalImage(gs, stripH, true);
        Graphics2D gl = bakedEdgeLeft.createGraphics();
        gl.setPaint(new GradientPaint(0, 0, sb, gs, 0, tr));
        gl.fillRect(0, 0, gs, stripH);
        gl.dispose();

        // Right strip: horizontal gradient tr(left) â†’ sb(right)
        bakedEdgeRight = Game.createOptimalImage(gs, stripH, true);
        Graphics2D gr2 = bakedEdgeRight.createGraphics();
        gr2.setPaint(new GradientPaint(0, 0, tr, gs, 0, sb));
        gr2.fillRect(0, 0, gs, stripH);
        gr2.dispose();

        // Top-left corner: radial gradient, center at inner corner (gs, gs)
        bakedCornerTL = Game.createOptimalImage(gs, gs, true);
        Graphics2D gc1 = bakedCornerTL.createGraphics();
        gc1.setPaint(new java.awt.RadialGradientPaint((float)gs, (float)gs, (float)gs, cd, cc));
        gc1.fillRect(0, 0, gs, gs);
        gc1.dispose();

        // Top-right corner: center at (0, gs)
        bakedCornerTR = Game.createOptimalImage(gs, gs, true);
        Graphics2D gc2 = bakedCornerTR.createGraphics();
        gc2.setPaint(new java.awt.RadialGradientPaint(0f, (float)gs, (float)gs, cd, cc));
        gc2.fillRect(0, 0, gs, gs);
        gc2.dispose();

        // Bottom-left corner: center at (gs, 0)
        bakedCornerBL = Game.createOptimalImage(gs, gs, true);
        Graphics2D gc3 = bakedCornerBL.createGraphics();
        gc3.setPaint(new java.awt.RadialGradientPaint((float)gs, 0f, (float)gs, cd, cc));
        gc3.fillRect(0, 0, gs, gs);
        gc3.dispose();

        // Bottom-right corner: center at (0, 0)
        bakedCornerBR = Game.createOptimalImage(gs, gs, true);
        Graphics2D gc4 = bakedCornerBR.createGraphics();
        gc4.setPaint(new java.awt.RadialGradientPaint(0f, 0f, (float)gs, cd, cc));
        gc4.fillRect(0, 0, gs, gs);
        gc4.dispose();
    }

    // Optimized Balatro-style animated gradient system

    private void drawAnimatedGradient(Graphics2D g, int width, int height, double time, int paletteIndex) {
        Color[] colors = LEVEL_GRADIENT_PALETTES[paletteIndex];
        Color[] derived = LEVEL_GRADIENT_DERIVED[paletteIndex];

        // Bake gradient layers to an off-screen image; refresh every N frames (or once when not animated)
        boolean sizeChanged = cachedBgGradient == null || cachedBgGradient.getWidth() != width || cachedBgGradient.getHeight() != height;
        boolean paletteChanged = cachedBgPaletteIdx != paletteIndex;
        boolean animTick = Game.enableGradientAnimation && (++bgGradientFrameCounter >= BG_GRADIENT_REFRESH_RATE);
        boolean staticFirstBake = !Game.enableGradientAnimation && (sizeChanged || paletteChanged || Double.isNaN(lastBgTime));

        if (sizeChanged || paletteChanged || animTick || staticFirstBake) {
            bgGradientFrameCounter = 0;
            lastBgTime = time;
            cachedBgPaletteIdx = paletteIndex;
            if (sizeChanged) {
                if (cachedBgGradient != null) cachedBgGradient.flush();
                cachedBgGradient = Game.createOptimalImage(width, height, false);
            }
            Graphics2D bg = cachedBgGradient.createGraphics();

            int offset1 = Game.enableGradientAnimation ? (int)(Math.sin(time * 0.5) * 150) : 0;
            int offset2 = Game.enableGradientAnimation ? (int)(Math.cos(time * 0.4) * 120) : 0;
            int offset3 = Game.enableGradientAnimation ? (int)(Math.sin(time * 0.6) * 130) : 0;

            bg.setPaint(new GradientPaint(0, offset1, colors[0], 0, height + offset1, colors[1]));
            bg.fillRect(0, 0, width, height);

            if (Game.gradientQuality >= 1) {
                bg.setPaint(new GradientPaint(width / 2, offset2, derived[0], width / 2, height + offset2, derived[1]));
                bg.fillRect(0, 0, width, height);
            }
            if (Game.gradientQuality >= 2) {
                bg.setPaint(new GradientPaint(offset3, 0, derived[3], width + offset3, height, derived[2]));
                bg.fillRect(0, 0, width, height);
            }
            bg.dispose();
        }

        // Draw the cached gradient image (one drawImage instead of up to 3 GradientPaint fills)
        g.drawImage(cachedBgGradient, 0, 0, null);

        // Optional grain effect (drawn live â€” only 40 tiny rects)
        if (Game.enableGrainEffect) {
            g.setComposite(RenderCache.getAlpha(0.03f));
            for (int i = 0; i < 40; i++) {
                int x = (int)(Math.random() * width);
                int y = (int)(Math.random() * height);
                int size = (int)(Math.random() * 2) + 1;
                g.setColor(Color.WHITE);
                g.fillRect(x, y, size, size);
            }
            g.setComposite(RenderCache.getAlpha(1.0f));
        }
    }

    

    // Pre-cached level gradient color palettes (avoids 21 new Color() per frame)
    private static final Color[][] LEVEL_GRADIENT_PALETTES = {
        { new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106) },  // 0: Dark blue
        { new Color(59, 66, 82), new Color(76, 86, 106), new Color(88, 91, 112) }, // 1: Purple
        { new Color(46, 52, 64), new Color(67, 76, 94), new Color(76, 86, 106) },  // 2: Red
        { new Color(46, 52, 64), new Color(59, 66, 82), new Color(67, 76, 94) },   // 3: Green
        { new Color(59, 66, 82), new Color(67, 76, 94), new Color(76, 86, 106) },  // 4: Orange
        { new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106) },  // 5: Teal
    };
    // Pre-cached derivative colors for drawAnimatedGradient (avoids 4 new Color per frame)
    private static final Color[][] LEVEL_GRADIENT_DERIVED = new Color[6][4];
    static {
        for (int i = 0; i < 6; i++) {
            Color c2 = LEVEL_GRADIENT_PALETTES[i][2];
            Color c1 = LEVEL_GRADIENT_PALETTES[i][1];
            LEVEL_GRADIENT_DERIVED[i][0] = new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 160); // accent 160
            LEVEL_GRADIENT_DERIVED[i][1] = new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 0);   // accent 0
            LEVEL_GRADIENT_DERIVED[i][2] = new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 120); // mid 120
            LEVEL_GRADIENT_DERIVED[i][3] = new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 0);   // mid 0
        }
    }

    private static int getLevelGradientPaletteIndex(int level) {
        int palette = ((level - 1) / 5) % 6;
        if (palette < 0 || palette >= LEVEL_GRADIENT_PALETTES.length) palette = 0;
        return palette;
    }

    private Color[] getLevelGradientColors(int level) {
        return LEVEL_GRADIENT_PALETTES[getLevelGradientPaletteIndex(level)];
    }

    

    // Public methods for drawing backgrounds (used by Game for zoom-out edge fill)

    public void drawAnimatedGradientPublic(Graphics2D g, int width, int height, double time, int level) {
        drawAnimatedGradient(g, width, height, time, getLevelGradientPaletteIndex(level));
    }

    

    public void drawParallaxBackgroundPublic(Graphics2D g, int width, int height, int level) {

        drawParallaxBackground(g, width, height, level, 0);

    }

    

    // Dynamic keybind text helpers

    /** Get display text for an action key (e.g., "SPACE", "W", "A Button") */

    private String keyText(KeyBindManager.Action action) {

        if (Game.keyBindManager != null) {

            return Game.keyBindManager.getKeyDisplayText(action);

        }

        // Fallback if keyBindManager not initialized

        switch (action) {

            case MOVE_UP: return "W";

            case MOVE_DOWN: return "S";

            case MOVE_LEFT: return "A";

            case MOVE_RIGHT: return "D";

            case USE_ITEM: return "E";

            case PAUSE: return "P";

            case RESTART: return "R";

            case CONFIRM: return "SPACE";

            case BACK: return "ESC";

            default: return "?";

        }

    }

    

    /** Get movement keys text like "WASD" or custom description */

    private String moveKeysText() {

        if (Game.keyBindManager != null) {

            return Game.keyBindManager.getMovementKeysText();

        }

        return "WASD/Arrows";

    }

    

    /**

     * Draw prompt text centered at (centerX, y) with inline controller button icons.

     * Segments alternate: text, action, text, action, ... ending with text.

     * Supports String, KeyBindManager.Action, and KeyBindManager.ControllerButton segments.

     * Example: drawPromptWithIcons(g, centerX, y, "Press ", Action.CONFIRM, " to start | ", Action.BACK, " to quit")

     * In keyboard mode, actions render as text, ControllerButton segments are hidden.

     * In controller mode, actions and ControllerButtons render as sprites.

     */

    /** Get a short display label for a VK key code (shorter than KeyEvent.getKeyText) */
    private String vkKeyLabel(int vkCode) {
        switch (vkCode) {
            case java.awt.event.KeyEvent.VK_ESCAPE: return "Esc";
            case java.awt.event.KeyEvent.VK_SPACE: return "Space";
            case java.awt.event.KeyEvent.VK_ENTER: return "Enter";
            case java.awt.event.KeyEvent.VK_BACK_SPACE: return "Bksp";
            case java.awt.event.KeyEvent.VK_DELETE: return "Del";
            case java.awt.event.KeyEvent.VK_CONTROL: return "Ctrl";
            case java.awt.event.KeyEvent.VK_SHIFT: return "Shift";
            case java.awt.event.KeyEvent.VK_ALT: return "Alt";
            case java.awt.event.KeyEvent.VK_TAB: return "Tab";
            default: return java.awt.event.KeyEvent.getKeyText(vkCode);
        }
    }

    /** Measure the width of a styled keycap box for a key label. */
    private int measureKeyCap(FontMetrics fm, String keyLabel) {
        int textW = fm.stringWidth(keyLabel);
        int pad = 8;
        return textW + pad * 2 + 4; // text + padding + border spacing
    }

    /** Draw a single action icon (key sprite or controller button) centered at (x, centerY). */
    private void drawSingleActionIcon(Graphics2D g, int x, int centerY, KeyBindManager.Action action) {
        if (Game.keyBindManager == null) return;
        java.awt.image.BufferedImage icon = Game.keyBindManager.getActionIcon(action);
        if (icon != null) {
            int iconH = UIScale.px(22);
            int iconW = iconH * icon.getWidth() / icon.getHeight();
            g.drawImage(icon, x - iconW / 2, centerY - iconH / 2, iconW, iconH, null);
        } else {
            // Fallback: draw keycap text
            Font savedFont = g.getFont();
            g.setFont(FONT_SMALL);
            FontMetrics fm = g.getFontMetrics();
            String label = keyText(action);
            drawKeyCap(g, fm, label, x - measureKeyCap(fm, label) / 2, centerY + fm.getAscent() / 2);
            g.setFont(savedFont);
        }
    }

    /** Draw a styled keycap box at (x, y) and return its width. y is the text baseline. */
    private int drawKeyCap(Graphics2D g, FontMetrics fm, String keyLabel, int x, int y) {
        int textW = fm.stringWidth(keyLabel);
        int pad = 8;
        int boxW = textW + pad * 2;
        int boxH = fm.getHeight();
        int boxX = x;
        int boxY = y - fm.getAscent() - 2;
        // Background
        g.setColor(new Color(50, 55, 70, 200));
        g.fillRoundRect(boxX, boxY, boxW, boxH + 2, 6, 6);
        // Border
        g.setColor(new Color(120, 130, 150, 180));
        g.setStroke(RenderCache.getStroke(1.5f));
        g.drawRoundRect(boxX, boxY, boxW, boxH + 2, 6, 6);
        // Bottom shadow edge for 3D effect
        g.setColor(new Color(30, 35, 45, 150));
        g.drawLine(boxX + 3, boxY + boxH + 2, boxX + boxW - 3, boxY + boxH + 2);
        // Text
        g.setColor(new Color(220, 225, 235));
        g.drawString(keyLabel, boxX + pad, y);
        return boxW + 4; // total advance including spacing
    }

    /** Left-aligned version of drawPromptWithIcons - draws from startX instead of centering */
    private void drawLeftAlignedPromptWithIcons(Graphics2D g, int startX, int y, Object... segments) {
        boolean controllerMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();
        FontMetrics fm = g.getFontMetrics();
        int iconH = fm.getHeight() - 2;
        int drawX = startX;
        Color savedColor = g.getColor();
        for (Object seg : segments) {
            if (seg instanceof String) {
                g.setColor(savedColor);
                g.drawString((String) seg, drawX, y);
                drawX += fm.stringWidth((String) seg);
            } else if (seg instanceof KeyBindManager.Action) {
                KeyBindManager.Action action = (KeyBindManager.Action) seg;
                if (Game.keyBindManager != null) {
                    java.awt.image.BufferedImage icon = Game.keyBindManager.getActionIcon(action);
                    if (icon != null) {
                        int iW = iconH * icon.getWidth() / icon.getHeight();
                        g.drawImage(icon, drawX, y - iconH + 2, iW, iconH, null);
                        drawX += iW + 2;
                    } else {
                        Color sc = g.getColor();
                        String text = keyText(action);
                        drawX += drawKeyCap(g, fm, text, drawX, y);
                        g.setColor(sc);
                    }
                } else {
                    Color sc = g.getColor();
                    String text = keyText(action);
                    drawX += drawKeyCap(g, fm, text, drawX, y);
                    g.setColor(sc);
                }
            } else if (seg instanceof KeyBindManager.ControllerButton) {
                KeyBindManager.ControllerButton btn = (KeyBindManager.ControllerButton) seg;
                if (controllerMode && Game.keyBindManager != null) {
                    java.awt.image.BufferedImage icon = Game.keyBindManager.getButtonSprite(btn);
                    if (icon != null) {
                        int iW = iconH * icon.getWidth() / icon.getHeight();
                        g.drawImage(icon, drawX, y - iconH + 2, iW, iconH, null);
                        drawX += iW + 2;
                    } else {
                        Color sc = g.getColor();
                        String text = btn.getDisplayName();
                        drawX += drawKeyCap(g, fm, text, drawX, y);
                        g.setColor(sc);
                    }
                }
            } else if (seg instanceof Integer) {
                // Raw VK key code â€” render as keyboard sprite or keycap
                int vkCode = (Integer) seg;
                java.awt.image.BufferedImage icon = (Game.keyBindManager != null) ? Game.keyBindManager.getKeySprite(vkCode) : null;
                if (icon != null) {
                    int iW = iconH * icon.getWidth() / icon.getHeight();
                    g.drawImage(icon, drawX, y - iconH + 2, iW, iconH, null);
                    drawX += iW + 2;
                } else {
                    Color sc = g.getColor();
                    drawX += drawKeyCap(g, fm, vkKeyLabel(vkCode), drawX, y);
                    g.setColor(sc);
                }
            }
        }
    }

    private void drawPromptWithIcons(Graphics2D g, int centerX, int y, Object... segments) {

        boolean controllerMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();

        FontMetrics fm = g.getFontMetrics();

        int iconH = fm.getHeight() - 2;

        

        // First pass: measure total width

        int totalWidth = 0;

        for (Object seg : segments) {

            if (seg instanceof String) {

                totalWidth += fm.stringWidth((String) seg);

            } else if (seg instanceof KeyBindManager.Action) {

                if (Game.keyBindManager != null) {

                    java.awt.image.BufferedImage icon = Game.keyBindManager.getActionIcon((KeyBindManager.Action) seg);

                    int iW = (icon != null) ? iconH * icon.getWidth() / icon.getHeight() : 0;

                    totalWidth += (icon != null) ? iW + 2 : measureKeyCap(fm, keyText((KeyBindManager.Action) seg));

                } else {

                    totalWidth += measureKeyCap(fm, keyText((KeyBindManager.Action) seg));

                }

            } else if (seg instanceof KeyBindManager.ControllerButton) {

                if (controllerMode && Game.keyBindManager != null) {

                    java.awt.image.BufferedImage icon = Game.keyBindManager.getButtonSprite((KeyBindManager.ControllerButton) seg);

                    int iW = (icon != null) ? iconH * icon.getWidth() / icon.getHeight() : 0;

                    totalWidth += (icon != null) ? iW + 2 : measureKeyCap(fm, ((KeyBindManager.ControllerButton) seg).getDisplayName());

                }

                // In keyboard mode, ControllerButton segments are hidden (zero width)

            } else if (seg instanceof Integer) {

                int vkCode = (Integer) seg;

                java.awt.image.BufferedImage icon = (Game.keyBindManager != null) ? Game.keyBindManager.getKeySprite(vkCode) : null;

                int iW = (icon != null) ? iconH * icon.getWidth() / icon.getHeight() : 0;

                totalWidth += (icon != null) ? iW + 2 : measureKeyCap(fm, vkKeyLabel(vkCode));

            }

        }

        

        // Second pass: draw from left

        int drawX = centerX - totalWidth / 2;

        Color savedColor = g.getColor();

        for (Object seg : segments) {

            if (seg instanceof String) {

                g.setColor(savedColor);

                g.drawString((String) seg, drawX, y);

                drawX += fm.stringWidth((String) seg);

            } else if (seg instanceof KeyBindManager.Action) {

                KeyBindManager.Action action = (KeyBindManager.Action) seg;

                if (Game.keyBindManager != null) {

                    java.awt.image.BufferedImage icon = Game.keyBindManager.getActionIcon(action);

                    if (icon != null) {

                        int iW = iconH * icon.getWidth() / icon.getHeight();

                        g.drawImage(icon, drawX, y - iconH + 2, iW, iconH, null);

                        drawX += iW + 2;

                    } else {

                        Color sc = g.getColor();

                        String text = keyText(action);

                        drawX += drawKeyCap(g, fm, text, drawX, y);

                        g.setColor(sc);

                    }

                } else {

                    Color sc = g.getColor();

                    String text = keyText(action);

                    drawX += drawKeyCap(g, fm, text, drawX, y);

                    g.setColor(sc);

                }

            } else if (seg instanceof KeyBindManager.ControllerButton) {

                KeyBindManager.ControllerButton btn = (KeyBindManager.ControllerButton) seg;

                if (controllerMode && Game.keyBindManager != null) {

                    java.awt.image.BufferedImage icon = Game.keyBindManager.getButtonSprite(btn);

                    if (icon != null) {

                        int iW = iconH * icon.getWidth() / icon.getHeight();

                        g.drawImage(icon, drawX, y - iconH + 2, iW, iconH, null);

                        drawX += iW + 2;

                    } else {

                        Color sc = g.getColor();

                        String text = btn.getDisplayName();

                        drawX += drawKeyCap(g, fm, text, drawX, y);

                        g.setColor(sc);

                    }

                }

                // In keyboard mode, ControllerButton segments are hidden

            } else if (seg instanceof Integer) {

                // Raw VK key code â€” render as keyboard sprite or keycap

                int vkCode = (Integer) seg;

                java.awt.image.BufferedImage icon = (Game.keyBindManager != null) ? Game.keyBindManager.getKeySprite(vkCode) : null;

                if (icon != null) {

                    int iW = iconH * icon.getWidth() / icon.getHeight();

                    g.drawImage(icon, drawX, y - iconH + 2, iW, iconH, null);

                    drawX += iW + 2;

                } else {

                    Color sc = g.getColor();

                    drawX += drawKeyCap(g, fm, vkKeyLabel(vkCode), drawX, y);

                    g.setColor(sc);

                }

            }

        }

    }

    

    /**

     * Get the total width of prompt segments for layout calculations.

     */

    private int measurePromptWidth(Graphics2D g, Object... segments) {

        boolean controllerMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();

        FontMetrics fm = g.getFontMetrics();

        int iconH = fm.getHeight() - 2;

        int totalWidth = 0;

        for (Object seg : segments) {

            if (seg instanceof String) {

                totalWidth += fm.stringWidth((String) seg);

            } else if (seg instanceof KeyBindManager.Action) {

                if (Game.keyBindManager != null) {

                    java.awt.image.BufferedImage icon = Game.keyBindManager.getActionIcon((KeyBindManager.Action) seg);

                    int iW = (icon != null) ? iconH * icon.getWidth() / icon.getHeight() : 0;

                    totalWidth += (icon != null) ? iW + 2 : measureKeyCap(fm, keyText((KeyBindManager.Action) seg));

                } else {

                    totalWidth += measureKeyCap(fm, keyText((KeyBindManager.Action) seg));

                }

            } else if (seg instanceof KeyBindManager.ControllerButton) {

                if (controllerMode && Game.keyBindManager != null) {

                    java.awt.image.BufferedImage icon = Game.keyBindManager.getButtonSprite((KeyBindManager.ControllerButton) seg);

                    int iW = (icon != null) ? iconH * icon.getWidth() / icon.getHeight() : 0;

                    totalWidth += (icon != null) ? iW + 2 : measureKeyCap(fm, ((KeyBindManager.ControllerButton) seg).getDisplayName());

                }

            } else if (seg instanceof Integer) {

                int vkCode = (Integer) seg;

                java.awt.image.BufferedImage icon = (Game.keyBindManager != null) ? Game.keyBindManager.getKeySprite(vkCode) : null;

                int iW = (icon != null) ? iconH * icon.getWidth() / icon.getHeight() : 0;

                totalWidth += (icon != null) ? iW + 2 : measureKeyCap(fm, vkKeyLabel(vkCode));

            }

        }

        return totalWidth;

    }

    

    // Getter methods for button arrays (for mouse navigation)

    public UIButton[] getMenuButtons() { return menuButtons; }

    public UIButton[] getSettingsButtons() { return settingsButtons; }

    public UIButton[] getPauseButtons() { return pauseButtons; }

    /** Returns the mode card hit-test rectangles (populated by drawModeSelect). */
    public java.awt.Rectangle[] getModeCardBounds() { return modeCardBounds; }

    /** Returns the warning dialog button bounds (populated by drawSettings when warning is shown). */
    public java.awt.Rectangle[] getWarningButtonBounds() { return warningButtonBounds; }

    

    /** Returns which pill option was clicked for a given setting, or -1 if none */

    public int getPillClickIndex(int settingIndex, int mouseX, int mouseY) {

        if (pillClickTargets == null || settingIndex >= pillClickTargets.length || pillClickTargets[settingIndex] == null) return -1;

        int[] targets = pillClickTargets[settingIndex];

        int py = pillClickTargetY[settingIndex];

        if (mouseY < py || mouseY > py + pillClickH) return -1;

        for (int j = 0; j < targets.length / 2; j++) {

            int px = targets[j * 2];

            int pw = targets[j * 2 + 1];

            if (mouseX >= px && mouseX <= px + pw) return j;

        }

        return -1;

    }

    

    /** Returns -1 for minus click, +1 for plus click, 0 for neither */

    public int getSliderButtonClick(int settingIndex, int mouseX, int mouseY) {

        if (sliderMinusBtnX == null || settingIndex >= sliderMinusBtnX.length) return 0;

        int by = sliderBtnYPos[settingIndex];

        int bs = sliderBtnSize;

        if (mouseY >= by && mouseY <= by + bs) {

            if (mouseX >= sliderMinusBtnX[settingIndex] && mouseX <= sliderMinusBtnX[settingIndex] + bs) return -1;

            if (mouseX >= sliderPlusBtnX[settingIndex] && mouseX <= sliderPlusBtnX[settingIndex] + bs) return 1;

        }

        return 0;

    }

    
    /** Returns 0..1 progress if click is on the slider track, or -1 if not on track */
    public float getSliderTrackClick(int settingIndex, int mouseX, int mouseY) {
        if (sliderTrackStartX == null || settingIndex >= sliderTrackStartX.length) return -1;
        if (sliderTrackStartX[settingIndex] < 0) return -1;
        int by = sliderBtnYPos[settingIndex];
        int bs = sliderBtnSize;
        // Use button Y range for vertical hit (generous â€” covers the slider area)
        if (mouseY >= by && mouseY <= by + bs) {
            int sx = sliderTrackStartX[settingIndex];
            int ex = sliderTrackEndX[settingIndex];
            if (mouseX >= sx && mouseX <= ex) {
                return Math.max(0f, Math.min(1f, (float)(mouseX - sx) / (float)(ex - sx)));
            }
        }
        return -1;
    }

    /** Returns 0..1 progress based on horizontal mouse position only (ignores Y). Used during drag. */
    public float getSliderTrackProgress(int settingIndex, int mouseX) {
        if (sliderTrackStartX == null || settingIndex >= sliderTrackStartX.length) return -1;
        if (sliderTrackStartX[settingIndex] < 0) return -1;
        int sx = sliderTrackStartX[settingIndex];
        int ex = sliderTrackEndX[settingIndex];
        return Math.max(0f, Math.min(1f, (float)(mouseX - sx) / (float)(ex - sx)));
    }

    public void configurePauseMenu(boolean isShowcase, boolean isTutorial) {

        showcasePauseMode = isShowcase;

        if (isShowcase) {

            activePauseButtonCount = 4;

            pauseButtons[0].setText("Settings");

            pauseButtons[1].setText("Restart");

            pauseButtons[2].setText("Main Menu");

            pauseButtons[3].setText("Back to Showcase");

        } else {

            activePauseButtonCount = 3;

            pauseButtons[0].setText("Resume");

            pauseButtons[1].setText("Settings");

            pauseButtons[2].setText(isTutorial ? "Quit Tutorial" : "Main Menu");

        }

    }

    public boolean isShowcasePauseMode() { return showcasePauseMode; }

    public int getActivePauseButtonCount() { return activePauseButtonCount; }

    public UIButton[] getShopButtons() { return shopButtons; }

    public int getStatsActiveItemDisplayIndex() { return statsActiveItemDisplayIndex; }

    public void setStatsActiveItemDisplayIndex(int index) { statsActiveItemDisplayIndex = index; }

    public UIButton[] getStatsButtons() { return statsButtons; }

    

    // Easing functions for smooth animations

    private float easeOutQuad(float t) {

        return 1 - (1 - t) * (1 - t);

    }

    

    private float easeOutBack(float t) {

        float c1 = 1.70158f;

        float c3 = c1 + 1;

        return 1 + c3 * (float)Math.pow(t - 1, 3) + c1 * (float)Math.pow(t - 1, 2);

    }

    

    private float easeOutElastic(float t) {

        if (t == 0 || t == 1) return t;

        float c4 = (2 * (float)Math.PI) / 3;

        return (float)Math.pow(2, -10 * t) * (float)Math.sin((t * 10 - 0.75f) * c4) + 1;

    }



    // ============================================================

    // BOSS INTRO CINEMATIC - Utility methods and main draw method

    // ============================================================



    /** Helper: clamp alpha to 0-255 range */

    private int clampA(int alpha) {

        return Math.max(0, Math.min(255, alpha));

    }

}



