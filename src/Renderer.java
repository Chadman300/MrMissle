import config.ColorPalette;
import config.FontPalette;
import config.HUDLayout;
import config.UITheme;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;



public class Renderer {

    private GameData gameData;

    private ShopManager shopManager;

    private PassiveUpgradeManager passiveUpgradeManager;

    

    // Menu buttons

    private UIButton[] menuButtons;

    private UIButton[] shopButtons;

    private UIButton[] statsButtons;

    private UIButton[] settingsButtons;

    private UIButton[] pauseButtons;

    

    // Settings UI click target tracking (populated during rendering)

    private int[][] pillClickTargets;  // [settingIndex] = {x0, w0, x1, w1, ...} or null

    private int[] pillClickTargetY;    // [settingIndex] = screen Y of pill row

    private int pillClickH = 26;       // pill option height

    private int[] sliderMinusBtnX;     // [settingIndex] = screen X of minus button

    private int[] sliderPlusBtnX;      // [settingIndex] = screen X of plus button

    private int[] sliderBtnYPos;       // [settingIndex] = screen Y of buttons

    private int sliderBtnSize = 26;    // +/- button size

    // HUD Layout Editor
    public HUDLayoutEditor hudLayoutEditor = new HUDLayoutEditor();
    public HUDLayout hudLayout; // active layout reference, set from Game

    private boolean showcasePauseMode = false;

    private int activePauseButtonCount = 3;

    private int statsActiveItemDisplayIndex = 0;

    

    // Number of background sets available

    private static final int BACKGROUND_SET_COUNT = 8;

    

    // Parallax background layers (BACKGROUND_SET_COUNT sets x 6 layers each)

    private static BufferedImage[][] backgroundLayers = new BufferedImage[BACKGROUND_SET_COUNT][6];

    private static boolean backgroundsLoaded = false;

    private double[] layerScrollOffsets = new double[6]; // Scroll offset for each layer

    

    // Background overlay

    private static BufferedImage overlayImage = null;

    private static boolean overlayLoaded = false;



    // VFX sprite sheets for cinematic intro

    private BufferedImage[][] vfxFrames = null;

    private boolean vfxLoaded = false;

    private static final int VFX_FRAME_H = 64;

    private static final String[] VFX_FILES = {

        "652", "653", "654", "655", "662", "663", "664", "665", "672", "673", "674", "675"

    };

    private double[][] introStars = null;
    private BufferedImage introBuf = null;
    private int introBufW, introBufH;





    

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
    // Shockwave arc strokes (cached — was 5 new BasicStroke per frame in loop)
    private static final BasicStroke SHOCKWAVE_STROKE_0 = new BasicStroke(12f);
    private static final BasicStroke SHOCKWAVE_STROKE_1 = new BasicStroke(10.5f);
    private static final BasicStroke SHOCKWAVE_STROKE_2 = new BasicStroke(9f);
    private static final BasicStroke SHOCKWAVE_STROKE_3 = new BasicStroke(7.5f);
    private static final BasicStroke SHOCKWAVE_STROKE_4 = new BasicStroke(6f);
    private static final BasicStroke[] SHOCKWAVE_STROKES = { SHOCKWAVE_STROKE_0, SHOCKWAVE_STROKE_1, SHOCKWAVE_STROKE_2, SHOCKWAVE_STROKE_3, SHOCKWAVE_STROKE_4 };

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

    // â"€â"€ Cached Colors for in-level drawGame (avoid per-frame new Color()) â"€â"€
    // Player shield arcs (6 per shield segment, per-frame)
    private static final Color SHIELD_ARC_OUTER = new Color(60, 180, 255, 50);
    private static final Color SHIELD_ARC_MID = new Color(80, 200, 255, 90);
    private static final Color SHIELD_ARC_MAIN = new Color(100, 210, 255, 200);
    private static final Color SHIELD_ARC_EDGE = new Color(200, 240, 255, 220);
    private static final Color SHIELD_ARC_TIP = new Color(220, 250, 255, 240);
    private static final Color SHIELD_ARC_INNER = new Color(100, 200, 255, 25);
    private static final Color INVINCIBILITY_GLOW = new Color(255, 255, 200, 120);
    private static final Color BOSS_DEATH_FIRE = new Color(255, 100, 0);
    private static final Color BOSS_COOL_BLOOM = new Color(100, 150, 200);
    private static final Color BOSS_CALM_GLOW = new Color(80, 150, 255);
    private static final Color WORLD_EDGE_80 = new Color(0, 0, 0, 80);
    // Baked level bounds images (rendered once — eliminates 8 gradient fills per frame)
    private static BufferedImage bakedEdgeTop, bakedEdgeBottom, bakedEdgeLeft, bakedEdgeRight;
    private static BufferedImage bakedCornerTL, bakedCornerTR, bakedCornerBL, bakedCornerBR;
    private static boolean levelBoundsBaked = false;
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

    // Cached vignette for performance

    private BufferedImage cachedVignette = null;

    private int cachedVignetteWidth = 0;

    private int cachedVignetteHeight = 0;

    

    // Cached Font objects Ã¢â‚¬â€ all derived from FontPalette (custom font with fallback)
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

        

        // Initialize menu buttons Ã¢â‚¬â€ military/rock themed colors
        menuButtons = new UIButton[7];
        menuButtons[0] = new UIButton("Select Level", "level", 0, 0, 300, 55, ColorPalette.BTN_LEVEL, ColorPalette.BTN_LEVEL_SEL);
        menuButtons[1] = new UIButton("Armory", "shop", 0, 0, 300, 55, ColorPalette.BTN_SHOP, ColorPalette.BTN_SHOP_SEL);
        menuButtons[2] = new UIButton("Stats", "stats", 0, 0, 300, 55, ColorPalette.BTN_STATS, ColorPalette.BTN_STATS_SEL);
        menuButtons[3] = new UIButton("Achievements", "achievements", 0, 0, 300, 55, ColorPalette.BTN_ACHIEVE, ColorPalette.BTN_ACHIEVE_SEL);
        menuButtons[4] = new UIButton("Intel", "info", 0, 0, 300, 55, ColorPalette.BTN_INFO, ColorPalette.BTN_INFO_SEL);
        menuButtons[5] = new UIButton("Settings", "settings", 0, 0, 300, 55, ColorPalette.BTN_SETTINGS, ColorPalette.BTN_SETTINGS_SEL);
        menuButtons[6] = new UIButton("[SAVE] Save Files", "save", 0, 0, 300, 55, ColorPalette.BTN_SAVE, ColorPalette.BTN_SAVE_SEL);

        

        // Initialize shop buttons (15 items)
        shopButtons = new UIButton[15];
        for (int i = 0; i < 15; i++) {
            shopButtons[i] = new UIButton("", 0, 0, 800, 50, ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_PURPLE);
        }

        // Initialize stats buttons (4 items)
        statsButtons = new UIButton[4];
        String[] statNames = {"Speed Boost", "Bullet Slow", "Lucky Dodge", "Active Item"};
        Color[] statColors = {ColorPalette.BTN_INFO, ColorPalette.BTN_STATS, ColorPalette.ACCENT_PURPLE, ColorPalette.ACCENT_ORANGE};

        for (int i = 0; i < 4; i++) {

            statsButtons[i] = new UIButton(statNames[i], 0, 0, 840, 70, ColorPalette.BUTTON_BASE, statColors[i]);

        }

        

        // Initialize settings buttons (16 options)
        settingsButtons = new UIButton[16];
        for (int i = 0; i < 16; i++) {
            settingsButtons[i] = new UIButton("", 0, 0, 900, 50, ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_ORANGE);
        }

        // Initialize pause buttons (4 buttons)
        pauseButtons = new UIButton[4];
        String[] pauseLabels = {"Resume", "Settings", "Main Menu", ""};
        for (int i = 0; i < 4; i++) {
            pauseButtons[i] = new UIButton(pauseLabels[i], 0, 0, 300, 60, ColorPalette.BUTTON_BASE, ColorPalette.ACCENT_ORANGE);
        }

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

                    

                    // Store the image (can be null if layer doesn't exist for this set)

                    backgroundLayers[set][layer] = image;

                    if (progressCallback != null) progressCallback.accept((int)((set * 6 + layer + 1) * 100.0 / 48));

                }

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

        

        // Parallax speeds for each layer (furthest to closest)

        double[] speeds = {0.1, 0.2, 0.35, 0.5, 0.7, 1.0};

        

        // Update scroll offsets for each layer

        for (int i = 0; i < 6; i++) {

            // Get layer image

            BufferedImage layer = backgroundLayers[bgSet][i];

            if (layer == null) continue; // Skip if this layer doesn't exist for this background set

            

            layerScrollOffsets[i] += speeds[i] * 0.5;

            

            // Calculate how many times to tile the image

            int imgWidth = layer.getWidth();

            int imgHeight = layer.getHeight();

            

            // Scale to fit screen height

            double scale = (double)height / imgHeight;

            int scaledWidth = (int)(imgWidth * scale);

            int scaledHeight = height;

            

            // Wrap scroll offset

            double offset = layerScrollOffsets[i] % scaledWidth;

            

            // Draw tiled layers with wrapping

            int x = (int)(-offset);

            while (x < width) {

                g.drawImage(layer, x, 0, scaledWidth, scaledHeight, null);

                x += scaledWidth;

            }

        }

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

        // Title Ã¢â‚¬â€ stencil-style with ember particles
        UITheme.drawTitle(g, "MR. MISSILE", width, height / 2 - 100,
            ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED,
            time, FontPalette.TITLE_LARGE);

        // "ARMING..." text
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.setFont(FontPalette.MEDIUM);
        String loadingText = "ARMING SYSTEMS...";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(loadingText, (width - fm.stringWidth(loadingText)) / 2, height / 2 + 20);

        // Missile-arming gauge progress bar
        int barWidth = 400;
        int barHeight = 24;
        int barX = (width - barWidth) / 2;
        int barY = height / 2 + 50;
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

        // Title Ã¢â‚¬â€ stencil-style with embers
        UITheme.drawTitle(g, "MR. MISSILE", width, 150,
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
            int titleX = (width - titleFm.stringWidth("MR. MISSILE")) / 2;

            String splashText = gameMode.getSplashText();
            g2.setFont(FontPalette.MEDIUM_BOLD);
            FontMetrics splashFm = g2.getFontMetrics();

            int anchorX = titleX + titleFm.stringWidth("MR. MISSILE") + 10;
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

        // Draw buttons Ã¢â‚¬â€ mission briefing clipboard stack
        int buttonY = 240;
        int buttonSpacing = 75;
        for (int i = 0; i < menuButtons.length; i++) {
            menuButtons[i].setPosition((width - 300) / 2, buttonY + i * buttonSpacing);
            menuButtons[i].update(i == selectedMenuItem, time);
            menuButtons[i].draw(g, time);
        }

        // Score and money card
        drawStatsCard(g, width, height, time);

        // Version and save slot info (bottom right)
        g.setFont(FontPalette.TINY);
        g.setColor(ColorPalette.TEXT_DIM);
        String versionText = Game.GAME_VERSION;
        FontMetrics fmVer = g.getFontMetrics();
        g.drawString(versionText, width - fmVer.stringWidth(versionText) - 20, height - 70);

        if (currentSaveSlot > 0) {
            String saveText = "Save Slot " + currentSaveSlot;
            g.drawString(saveText, width - fmVer.stringWidth(saveText) - 20, height - 50);
        }

        // Quit hint
        if (escapeTimer > 0) {
            g.setColor(ColorPalette.ACCENT_RED);
            g.setFont(FontPalette.MEDIUM_BOLD);
            drawPromptWithIcons(g, width / 2, height - 210, "Press ", KeyBindManager.Action.BACK, " again to Quit");
        }
    }

    

    public void drawSaveSelection(Graphics2D g, int width, int height, double time, int selectedSlot,

                                  java.util.List<SaveManager.SaveMetadata> saveMetadata, boolean deletingSlot, 

                                  int deleteConfirmTimer, double escapeTimer, double scrollOffset) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "SAVE FILES", width, 120, ColorPalette.ACCENT_ORANGE, ColorPalette.ACCENT_RED, time);

        

        // Draw save slots with scroll
        FontMetrics fm;

        int slotWidth = 800;

        int slotHeight = 160;

        int slotX = (width - slotWidth) / 2;

        int startY = 200;

        int slotSpacing = 180;

        int totalEntries = saveMetadata.size() + 1; // existing saves + "New Save" button

        

        // Clip to content area (below title, above instructions)

        Shape oldClip = g.getClip();

        g.clipRect(0, 160, width, height - 300);

        

        for (int i = 0; i < totalEntries; i++) {

            int slotY = startY + i * slotSpacing - (int)scrollOffset;

            boolean isSelected = (i == selectedSlot);

            boolean isExistingSave = (i < saveMetadata.size());

            

            // Skip if completely off-screen

            if (slotY + slotHeight < 160 || slotY > height - 60) continue;

            

            // Draw slot background (anti-aliasing already set by renderToBuffer)

            

            // Selection glow

            if (isSelected) {

                g.setColor(new Color(ColorPalette.ACCENT_ORANGE.getRed(), ColorPalette.ACCENT_ORANGE.getGreen(), ColorPalette.ACCENT_ORANGE.getBlue(), 80));

                Shape glowShape = UITheme.createChamferedRect(slotX - 8, slotY - 8, slotWidth + 16, slotHeight + 16, 12);

                g.fill(glowShape);

            }

            

            if (isExistingSave) {

                // Existing save slot

                SaveManager.SaveMetadata meta = saveMetadata.get(i);

                

                // Main slot background Ã¢â‚¬â€ chamfered military card

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

                g.fillRect(slotX, slotY + 8, 4, slotHeight - 16);

                

                // Slot number

                g.setFont(FONT_LARGE);

                g.setColor(ColorPalette.TEXT_GOLD);

                String slotNum = "SAVE " + meta.slotNumber;

                g.drawString(slotNum, slotX + 20, slotY + 35);

                

                // Show game mode badge next to slot number

                if (meta.gameMode != null) {

                    GameMode mode = meta.gameMode;

                    String modeLabel = mode.getDisplayName();

                    g.setFont(FONT_EXTRA_SMALL_16);

                    FontMetrics modeFm = g.getFontMetrics();

                    int modeX = slotX + 20 + g.getFontMetrics(FONT_LARGE).stringWidth(slotNum) + 15;

                    int modeY = slotY + 35;

                    // Mode badge background pill

                    int badgeW = modeFm.stringWidth(modeLabel) + 16;

                    int badgeH = 22;

                    g.setColor(new Color(

                        mode.getColor().getRed(),

                        mode.getColor().getGreen(),

                        mode.getColor().getBlue(),

                        60

                    ));

                    g.fillRoundRect(modeX - 8, modeY - 16, badgeW, badgeH, 8, 8);

                    g.setStroke(RenderCache.getStroke(1.5f));

                    g.setColor(mode.getColor());

                    g.drawRoundRect(modeX - 8, modeY - 16, badgeW, badgeH, 8, 8);

                    g.drawString(modeLabel, modeX, modeY);

                }

                

                // Save name

                g.setFont(FONT_MEDIUM_BOLD);

                g.setColor(Color.WHITE);

                g.drawString(meta.saveName, slotX + 20, slotY + 65);

                

                // Stats line 1

                g.setFont(FONT_SMALL);

                g.setColor(ColorPalette.TEXT_PRIMARY);

                String stats1 = String.format("Max Level: %d  |  Money: $%d", meta.maxLevel, meta.totalMoney);

                g.drawString(stats1, slotX + 20, slotY + 90);

                

                // Stats line 2

                String stats2 = String.format("Runs: %d  |  Best Run: Level %d  |  Bosses: %d", 

                    meta.totalRuns, meta.bestRunLevel, meta.totalBosses);

                g.drawString(stats2, slotX + 20, slotY + 110);

                

                // Created date (left) and Last saved date (right)

                g.setFont(FONT_TINY);

                g.setColor(ColorPalette.TEXT_DIM);

                String createdText = "Created: " + meta.getFormattedCreationDate();

                g.drawString(createdText, slotX + 20, slotY + 130);

                

                g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 180));

                String dateText = "Last Saved: " + meta.getFormattedDate();

                FontMetrics dateFm = g.getFontMetrics();

                g.drawString(dateText, slotX + slotWidth - 20 - dateFm.stringWidth(dateText), slotY + 130);

                

                // Delete button

                int btnX = slotX + slotWidth - 120;

                int btnY = slotY + 10;

                int btnWidth = 100;

                int btnHeight = 35;

                

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

                

                // Delete confirmation if deleting this slot

                if (isSelected && deletingSlot) {

                    float progress = Math.min(1.0f, deleteConfirmTimer / 60.0f);

                    

                    // Delete progress bar - positioned below the delete button

                    int barX = slotX + 20;

                    int barY = slotY + slotHeight - 25;

                    int barWidth = slotWidth - 40;

                    int barHeight = 12;

                    

                    g.setColor(new Color(ColorPalette.ACCENT_RED.getRed(), ColorPalette.ACCENT_RED.getGreen(), ColorPalette.ACCENT_RED.getBlue(), 100));

                    g.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);

                    

                    g.setColor(ColorPalette.ACCENT_RED);

                    g.fillRoundRect(barX, barY, (int)(barWidth * progress), barHeight, 6, 6);

                    

                    // Delete text

                    g.setFont(FONT_TINY);

                    boolean ctrlMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();

                    String deleteText = ctrlMode ? "HOLD X..." : "HOLD DELETE...";

                    fm = g.getFontMetrics();

                    g.drawString(deleteText, barX + (barWidth - fm.stringWidth(deleteText)) / 2, barY - 5);

                }

            } else {

                // "New Save" button Ã¢â‚¬â€ dashed military card

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

                int plusCenterY = slotY + slotHeight / 2 - 12;

                g.drawLine(plusCenterX - 15, plusCenterY, plusCenterX + 15, plusCenterY);

                g.drawLine(plusCenterX, plusCenterY - 15, plusCenterX, plusCenterY + 15);

                

                // Text

                g.setFont(FONT_MEDIUM);

                g.setColor(isSelected ? ColorPalette.SUCCESS_GREEN : ColorPalette.TEXT_DIM);

                String newText = "New Save";

                FontMetrics newFm = g.getFontMetrics();

                g.drawString(newText, plusCenterX - newFm.stringWidth(newText) / 2, plusCenterY + 40);

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

            g.drawString(upArrow, (width - fm.stringWidth(upArrow)) / 2, 180);

        }

        int maxScroll = Math.max(0, totalEntries * slotSpacing + startY - height + 60);

        if (scrollOffset < maxScroll - 5) {

            // Down arrow indicator

            g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), (int)(150 + 50 * Math.sin(time * 4))));

            g.setFont(FONT_MEDIUM);

            fm = g.getFontMetrics();

            String downArrow = "\u25BC  Scroll Down";

            g.drawString(downArrow, (width - fm.stringWidth(downArrow)) / 2, height - 80);

        }

        

        // Instructions

        g.setFont(FONT_MEDIUM);

        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 200));

        boolean isCtrlMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();

        if (isCtrlMode) {

            drawPromptWithIcons(g, width / 2, height - 50, "", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, ": Navigate  |  ", KeyBindManager.Action.CONFIRM, ": Select/Create  |  ", KeyBindManager.ControllerButton.X, ": Hold to Delete Save");

        } else {

            drawPromptWithIcons(g, width / 2, height - 50, "", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, ": Navigate  |  ", KeyBindManager.Action.CONFIRM, ": Select/Create  |  DELETE: Hold to Delete Save");

        }

        

        // Quit hint

        if (escapeTimer > 0) {

            g.setColor(ColorPalette.ACCENT_RED);

            g.setFont(FONT_MEDIUM_BOLD);

            drawPromptWithIcons(g, width / 2, height - 20, "Press ", KeyBindManager.Action.BACK, " again to Quit");

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

        UITheme.drawTitle(g, "SELECT MODE", width, 100, ColorPalette.ACCENT_YELLOW, ColorPalette.ACCENT_ORANGE, time);

        

        // Subtitle

        g.setFont(FONT_MEDIUM);

        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 200));

        String subtitle = "Choose a difficulty for this save (locked once created)";

        FontMetrics fm = g.getFontMetrics();

        g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, 135);

        

        // Draw mode cards

        GameMode[] modes = GameMode.values();

        int cardWidth = 700;

        int cardX = (width - cardWidth) / 2;

        int startY = 180;

        int cardGap = 20;

        int textMaxWidth = cardWidth - 50;

        

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

            cardHeights[i] = 50 + descHeight + 6 + detailHeight + 10;

        }

        

        int currentY = startY;

        for (int i = 0; i < modes.length; i++) {

            GameMode mode = modes[i];

            int cardHeight = cardHeights[i];

            int cardY = currentY;

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

                Shape glowShape = UITheme.createChamferedRect(cardX - 8, cardY - 8, cardWidth + 16, cardHeight + 16, 12);

                g2.fill(glowShape);

            }

            

            // Card background Ã¢â‚¬â€ chamfered military card

            Color bgColor = isSelected ? ColorPalette.BG_CARD_SELECTED : ColorPalette.BG_CARD;

            g2.setColor(bgColor);

            Shape cardShape = UITheme.createChamferedRect(cardX, cardY, cardWidth, cardHeight, 10);

            g2.fill(cardShape);

            

            // Border in mode color

            g2.setStroke(new BasicStroke(isSelected ? 4 : 2));

            g2.setColor(isSelected ? mode.getColor() : new Color(

                mode.getColor().getRed(), 

                mode.getColor().getGreen(), 

                mode.getColor().getBlue(), 

                120

            ));

            g2.draw(cardShape);

            

            // Accent line on left

            g2.setColor(mode.getColor());

            g2.fillRect(cardX, cardY + 8, 4, cardHeight - 16);

            

            // Mode name

            g2.setFont(FONT_LARGE);

            g2.setColor(isSelected ? mode.getColor() : ColorPalette.TEXT_PRIMARY);

            g2.drawString(mode.getDisplayName(), cardX + 25, cardY + 40);

            

            // Description (wrapped)

            g2.setFont(FONT_MEDIUM);

            g2.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 220));

            int textY = cardY + 70;

            for (String line : descLines[i]) {

                g2.drawString(line, cardX + 25, textY);

                textY += g2.getFontMetrics().getHeight();

            }

            

            // Detail text (wrapped)

            g2.setFont(FONT_SMALL);

            g2.setColor(ColorPalette.TEXT_DIM);

            textY += 6;

            for (String line : detailLines[i]) {

                g2.drawString(line, cardX + 25, textY);

                textY += g2.getFontMetrics().getHeight();

            }

            

            // Selection arrow (missile shape)

            if (isSelected) {

                g2.setColor(mode.getColor());

                double bounce = Math.sin(time * 6) * 5;

                int arrowX = (int)(cardX - 28 + bounce);

                int arrowY = cardY + cardHeight / 2;

                int[] xPoints = {arrowX, arrowX, arrowX + 14};

                int[] yPoints = {arrowY - 10, arrowY + 10, arrowY};

                g2.fillPolygon(xPoints, yPoints, 3);

            }

            

            g2.dispose();

            currentY += cardHeight + cardGap;

        }

        

        // Instructions

        g.setFont(FONT_MEDIUM);

        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 200));

        drawPromptWithIcons(g, width / 2, height - 80, "", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, ": Navigate  |  ", KeyBindManager.Action.CONFIRM, ": Select  |  ", KeyBindManager.Action.BACK, ": Back");

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

        Graphics2D g2 = (Graphics2D) g.create();

        if (Game.enableAntiAliasing) {

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        }

        

        // Floating hexagons

        drawFloatingShapes(g2, width, height, time);

        

        // Grid lines with perspective

        drawPerspectiveGrid(g2, width, height, time);

        

        // Orbiting circles

        drawOrbitingCircles(g2, width, height, time);

        

        // Corner decorations

        drawCornerDecorations(g2, width, height, time);

        

        g2.dispose();

    }

    

    private void drawFloatingShapes(Graphics2D g, int width, int height, double time) {

        // Draw floating hexagons and triangles

        int numShapes = 12;

        for (int i = 0; i < numShapes; i++) {

            double phase = (i * Math.PI * 2.0) / numShapes;

            double x = width * (0.1 + 0.8 * ((Math.sin(time * 0.3 + phase) + 1) / 2));

            double y = height * (0.1 + 0.8 * ((Math.cos(time * 0.2 + phase * 1.5) + 1) / 2));

            double size = 20 + 30 * Math.sin(time * 0.5 + phase);

            double rotation = time * 0.5 + phase;

            int alpha = (int)(30 + 20 * Math.sin(time + phase));

            

            Graphics2D g2 = (Graphics2D) g.create();

            g2.translate(x, y);

            g2.rotate(rotation);

            

            if (i % 3 == 0) {

                // Hexagon

                g2.setColor(new Color(143, 188, 187, alpha)); // Teal

                drawHexagon(g2, 0, 0, (int)size);

            } else if (i % 3 == 1) {

                // Triangle

                g2.setColor(new Color(180, 142, 173, alpha)); // Purple

                drawTriangle(g2, 0, 0, (int)size);

            } else {

                // Diamond

                g2.setColor(new Color(235, 203, 139, alpha)); // Gold

                drawDiamond(g2, 0, 0, (int)size);

            }

            

            g2.dispose();

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

        int dividerSpace = 40; // Space for divider and padding

        int padding = 50; // Left and right padding

        

        // Dynamically size the card based on content

        int minCardWidth = 350;

        int requiredWidth = scoreWidth + moneyWidth + dividerSpace + padding;

        int cardWidth = Math.max(minCardWidth, requiredWidth);

        int cardHeight = 70;

        int cardX = (width - cardWidth) / 2;

        int cardY = height - 130;

        

        // Card background

        g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 200));

        g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 15, 15);

        

        // Card border

        int borderAlpha = (int)(150 + 50 * Math.sin(time * 2));

        g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, borderAlpha));

        g.setStroke(RenderCache.getStroke(2));

        g.drawRoundRect(cardX, cardY, cardWidth, cardHeight, 15, 15);

        

        // Calculate divider position based on text widths

        int dividerX = cardX + padding / 2 + scoreWidth + dividerSpace / 2;

        

        // Divider line

        g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 100));

        g.drawLine(dividerX, cardY + 10, dividerX, cardY + cardHeight - 10);

        

        int textY = cardY + cardHeight / 2 + fm.getAscent() / 2 - 5;

        

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

        UITheme.drawTitle(g, "MISSION INTEL", width, 60, ColorPalette.ACCENT_CYAN, ColorPalette.ACCENT_ORANGE, time, FONT_TITLE_MEDIUM);

        

        int leftX = 60;

        int rightX = width / 2 + 40;

        int columnWidth = width / 2 - 80;

        

        // LEFT COLUMN

        int y = 105;

        

        // Core Mechanics section

        g.setColor(ColorPalette.ACCENT_CYAN);

        g.setFont(FONT_MEDIUM_BOLD);

        g.drawString("CORE MECHANICS", leftX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(FONT_EXTRA_SMALL_16);

        String[] mechanics = {

            "VULNERABILITY SYSTEM:",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Boss invulnerable for 20 seconds",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Watch for GOLDEN GLOW = Attack Window!",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Window lasts 20 seconds (longer with upgrades)",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Hit boss 3 times to win",

            "",

            "GRAZE SYSTEM:",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ 25px from bullet = Graze (+score, +combo)",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ 15px = Close Call (bonus points)",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ 8px = Perfect Dodge (grants i-frames!)",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Build combos: Chain dodges within 3s",

            "",

            "DEATH & RESPAWN:",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ One hit = death (unless Lucky Dodge procs)",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Boss hit (non-fatal) = 1.5s respawn delay",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Use extra missiles for second chances",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Lucky Dodge upgrade = revival chance"

        };

        

        for (String line : mechanics) {

            g.drawString(line, leftX + 10, y);

            y += 20;

        }

        

        // Passive Upgrades section

        y += 10;

        g.setColor(ColorPalette.TEXT_GOLD);

        g.setFont(FONT_MEDIUM_BOLD);

        g.drawString("PASSIVE UPGRADES", leftX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(FONT_EXTRA_SMALL_16);

        String[] upgrades = {

            "SPEED BOOST (Max Lv 10):",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ +10% movement speed per level",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Essential for dodging dense patterns",

            "",

            "BULLET SLOW (Max Lv 50):",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Slows enemy bullets by 2% per level",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ More time to react and plan dodges",

            "",

            "LUCKY DODGE (Max Lv 12):",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ 8% chance per level to survive hits",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Flicker effect on successful dodge",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Stacks with extra missiles",

            "",

            "ATTACK WINDOW (Max Lv 10):",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ +1 second vulnerability per level",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ Max: 30 seconds to hit boss",

            "  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ More forgiving timing"

        };

        

        for (String line : upgrades) {

            g.drawString(line, leftX + 10, y);

            y += 20;

        }

        

        // RIGHT COLUMN

        y = 105;

        

        // Active Items section

        g.setColor(ColorPalette.ACCENT_CYAN);

        g.setFont(FONT_MEDIUM_BOLD);

        g.drawString("ACTIVE ITEMS", rightX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(FONT_EXTRA_SMALL_16);

        String[] items = {

            "POOL OF LOOT (Lv 3) - 35s cooldown",

            "  Spawn money circle for bonus cash",

            "",

            "SHIELD (Lv 6) - 7.5s cooldown",

            "  Tank 3 hits, active for 1 second",

            "",

            "BOMBS (Lv 7) - 6s cooldown",

            "  Explosive barrage across screen",

            "",

            "STUN (Lv 9) - 10s cooldown",

            "  Freeze the boss for 3 seconds",

            "",

            "CHROMATIC PURGE (Lv 12) - 5s cooldown",

            "  Erase all bullets of a random type",

            "",

            "TIME SLOW (Lv 15) - 7.5s cooldown",

            "  Slow bullets & beams for 2 seconds",

            "",

            "DASH (Lv 18) - 2s cooldown",

            "  Quick dash with invincibility frames",

            "",

            "IMPULSE (Lv 21) - 5s cooldown",

            "  Push all bullets away instantly",

            "",

            "FROST BEAM (Lv 24) - 5s cooldown",

            "  Freeze bullets in an icy beam"

        };

        

        for (String line : items) {

            g.drawString(line, rightX + 10, y);

            y += 19;

        }

        

        // Risk Contracts section

        y += 10;

        g.setColor(ColorPalette.ACCENT_RED);

        g.setFont(FONT_MEDIUM_BOLD);

        g.drawString("RISK CONTRACTS", rightX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(FONT_EXTRA_SMALL_16);

        String[] contracts = {

            "Unlock at Level 6 for bonus rewards:",

            "",

            "BULLET STORM - 2x bullets (2x money)",

            "SPEED DEMON - 50% faster bullets (1.75x)",

            "POWERLESS - All active items disabled (1.5x)",

            "CAN'T STOP - Must keep moving (2.5x)",

            "",

            "Higher risk = Higher reward!"

        };

        

        for (String line : contracts) {

            g.drawString(line, rightX + 10, y);

            y += 20;

        }

        

        // Controls hint at bottom

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_TINY);

        drawPromptWithIcons(g, width / 2, height - 30, "CONTROLS: ", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_LEFT, "/", KeyBindManager.Action.MOVE_DOWN, "/", KeyBindManager.Action.MOVE_RIGHT, " = Move  |  ", KeyBindManager.Action.USE_ITEM, " = Use Item  |  ", KeyBindManager.Action.PAUSE, " = Pause  |  Mouse = Navigate Menus");

        g.drawString("TIP: Visit SHOP for upgrades | Complete ACHIEVEMENTS | Use STATS to track progress", width / 2 - 420, height - 10);

    }

    

    public void drawAchievements(Graphics2D g, int width, int height, double time, AchievementManager achievementManager, double scrollOffset) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "ACHIEVEMENTS", width, 80, ColorPalette.ACCENT_YELLOW, ColorPalette.TEXT_GOLD, time, FONT_TITLE_MEDIUM);

        

        // Achievement count
        FontMetrics fm;

        int unlocked = achievementManager.getUnlockedCount();

        int total = achievementManager.getAllAchievements().size();

        g.setFont(FONT_MEDIUM_BOLD);

        g.setColor(ColorPalette.SUCCESS_GREEN); // Green

        String countText = unlocked + " / " + total + " Unlocked";

        fm = g.getFontMetrics();

        g.drawString(countText, (width - fm.stringWidth(countText)) / 2, 120);

        

        // Draw achievements in a grid

        java.util.List<Achievement> achievements = achievementManager.getAllAchievements();

        int columns = 3;

        int cardWidth = 380;

        int cardHeight = 100;

        int startX = (width - (columns * cardWidth + (columns - 1) * 20)) / 2;

        int startY = 150;

        int gapX = 20;

        int gapY = 15;

        

        // Create clipping region for scrollable area

        g.setClip(0, 140, width, height - 180);

        

        for (int i = 0; i < achievements.size(); i++) {

            Achievement ach = achievements.get(i);

            int col = i % columns;

            int row = i / columns;

            int x = startX + col * (cardWidth + gapX);

            int y = (int)(startY + row * (cardHeight + gapY) - scrollOffset);

            

            // Only draw if visible in clipping region

            if (y + cardHeight < 140 || y > height - 40) {

                continue;

            }

            

            // Card background

            if (ach.isUnlocked()) {

                // Unlocked - golden glow

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_GOLD, 40));

                g.fillRoundRect(x - 3, y - 3, cardWidth + 6, cardHeight + 6, 15, 15);

                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 240));

            } else {

                // Locked - darker

                g.setColor(new Color(30, 35, 45, 240));

            }

            g.fillRoundRect(x, y, cardWidth, cardHeight, 12, 12);

            

            // Border

            g.setStroke(RenderCache.getStroke(2));

            if (ach.isUnlocked()) {

                g.setColor(ColorPalette.TEXT_GOLD); // Gold border

            } else {

                g.setColor(ColorPalette.BORDER_STEEL); // Grey border

            }

            g.drawRoundRect(x, y, cardWidth, cardHeight, 12, 12);

            

            // Achievement icon/status

            int iconSize = 40;

            int iconX = x + 15;

            int iconY = y + (cardHeight - iconSize) / 2;

            

            if (ach.isUnlocked()) {

                // Checkmark circle

                g.setColor(ColorPalette.SUCCESS_GREEN); // Green

                g.fillOval(iconX, iconY, iconSize, iconSize);

                g.setColor(Color.WHITE);

                g.setStroke(RenderCache.getStroke(3));

                g.drawLine(iconX + 10, iconY + 20, iconX + 18, iconY + 28);

                g.drawLine(iconX + 18, iconY + 28, iconX + 30, iconY + 12);

            } else {

                // Lock icon

                g.setColor(NODE_LOCKED_ICON);

                g.fillOval(iconX, iconY, iconSize, iconSize);

                g.setColor(new Color(60, 60, 70));

                g.fillRect(iconX + 12, iconY + 22, 16, 14);

                g.setColor(new Color(80, 80, 90));

                g.setStroke(RenderCache.getStroke(2));

                g.drawArc(iconX + 13, iconY + 10, 14, 16, 0, 180);

            }

            

            // Achievement name

            g.setFont(FONT_EXTRA_SMALL_16);

            if (ach.isUnlocked()) {

                g.setColor(ColorPalette.TEXT_GOLD); // Gold

            } else {

                g.setColor(new Color(150, 150, 160));

            }

            g.drawString(ach.getName(), x + 65, y + 28);

            

            // Description (word-wrapped)

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(ach.isUnlocked() ? RenderCache.SLATE_200_200_210 : new Color(100, 100, 110));

            {
                String desc = ach.getDescription();
                int descMaxW = cardWidth - 80;
                FontMetrics descFm = g.getFontMetrics();
                if (descFm.stringWidth(desc) <= descMaxW) {
                    g.drawString(desc, x + 65, y + 48);
                } else {
                    // Word wrap into lines
                    String[] words = desc.split(" ");
                    StringBuilder line = new StringBuilder();
                    int descY = y + 48;
                    for (String word : words) {
                        String test = line.length() == 0 ? word : line + " " + word;
                        if (descFm.stringWidth(test) > descMaxW && line.length() > 0) {
                            g.drawString(line.toString(), x + 65, descY);
                            descY += descFm.getHeight();
                            line = new StringBuilder(word);
                        } else {
                            if (line.length() > 0) line.append(" ");
                            line.append(word);
                        }
                    }
                    if (line.length() > 0) {
                        g.drawString(line.toString(), x + 65, descY);
                    }
                }
            }

            

            // Progress bar (only if not unlocked)

            if (!ach.isUnlocked()) {

                int barWidth = cardWidth - 80;

                int barHeight = 8;

                int barX = x + 65;

                int barY = y + 60;

                

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

                g.drawString(progressText, barX + barWidth - fm.stringWidth(progressText) + 20, y + 85);

            } else {

                // "COMPLETE" badge

                g.setFont(FONT_EXTRA_SMALL_12);

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.drawString("COMPLETE", x + 65, y + 75);

            }

        }

        

        // Reset clip

        g.setClip(null);

        

        // Controls hint

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_SMALL);

        drawPromptWithIcons(g, width / 2, height - 40,

            "Press ", KeyBindManager.Action.BACK, " to return to menu | ", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " to scroll");

    }

    

    public void drawStats(Graphics2D g, int width, int height, double time, PassiveUpgradeManager passiveManager) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "STATS", width, 80, ColorPalette.ACCENT_CYAN, ColorPalette.ACCENT_ORANGE, time, FONT_TITLE_MEDIUM);

        

        // Show total money with glow

        g.setColor(ColorPalette.SUCCESS_GREEN);

        g.setFont(FONT_LARGE);

        String money = "Money: $" + gameData.getTotalMoney();

        FontMetrics fm = g.getFontMetrics();

        int moneyX = (width - fm.stringWidth(money)) / 2;

        g.drawString(money, moneyX, 120);

        

        // Instructions at top

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_EXTRA_SMALL_16);

        drawPromptWithIcons(g, width / 2, 145, KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " to select | ", KeyBindManager.Action.MOVE_LEFT, "/", KeyBindManager.Action.MOVE_RIGHT, " to adjust | ", KeyBindManager.Action.BACK, " to return");

    }

    

    public void drawStatsUpgrades(Graphics2D g, int width, int selectedStatItem, PassiveUpgradeManager passiveManager, double scrollOffset) {

        int baseY = 180;

        int y = baseY - (int)scrollOffset;

        int cardWidth = 900;

        int cardHeight = 65;

        int cardSpacing = 10;

        int currentIndex = 0;

        

        // Section 1: Active Item (index 0)

        g.setColor(ColorPalette.SUCCESS_GREEN);

        g.setFont(FONT_SMALL);

        g.drawString("ACTIVE ITEM - Unlock from mega bosses", width / 2 - 400, y);

        y += 30;

        

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


        


        int singleCardH = 120;


        


        // Card shadow


        g.setColor(RenderCache.BLACK_100);


        g.fillRoundRect(itemX + 3, y + 3, cardWidth, singleCardH, 12, 12);


        


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


        g.fillRoundRect(itemX, y, cardWidth, singleCardH, 12, 12);


        


        // Selection highlight border


        if (isSelected) {


            g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_GOLD, 200));


            g.setStroke(RenderCache.getStroke(2.5f));


            g.drawRoundRect(itemX, y, cardWidth, singleCardH, 12, 12);


            g.setStroke(RenderCache.getStroke(1f));


        } else if (isEquipped) {


            g.setColor(new Color(163, 210, 140, 180));


            g.setStroke(RenderCache.getStroke(2f));


            g.drawRoundRect(itemX, y, cardWidth, singleCardH, 12, 12);


            g.setStroke(RenderCache.getStroke(1f));


        }


        


        // Left arrow


        if (displayIndex > 0) {


            g.setFont(FONT_LARGE);


            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_150);


            g.drawString("<", itemX + 14, y + singleCardH / 2 + 10);


        }


        


        // Right arrow


        if (displayIndex < allItems.length - 1) {


            g.setFont(FONT_LARGE);


            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_150);


            String rightArrow = ">";


            FontMetrics arrowFm = g.getFontMetrics();


            g.drawString(rightArrow, itemX + cardWidth - arrowFm.stringWidth(rightArrow) - 12, y + singleCardH / 2 + 10);


        }


        


        // Content area (between arrows)


        int contentX = itemX + 50;


        int contentW = cardWidth - 100;


        


        if (isUnlocked) {



            // --- Item icon (color-coded circular icon with symbol) ---

            int iconSize = 56;

            int iconX = contentX + 15;

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

            g.fillOval(iconX - 3, iconY - 3, iconSize + 6, iconSize + 6);

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

            int textX = iconX + iconSize + 24;

            int textRightEdge = itemX + cardWidth - 60;



            // Item name (large, bold)

            g.setFont(FONT_TINY);

            g.setColor(isEquipped ? RenderCache.GREEN_163_210_140 : Color.WHITE);

            String displayName = itemName;

            g.drawString(displayName, textX, y + 35);



            // Equipped badge (inline, smaller)

            if (isEquipped) {

                FontMetrics nameFm = g.getFontMetrics();

                int badgeX = textX + nameFm.stringWidth(displayName) + 12;

                g.setFont(FONT_EXTRA_SMALL_11);

                FontMetrics badgeFm = g.getFontMetrics();

                String badge = "EQUIPPED";

                int badgeW = badgeFm.stringWidth(badge) + 12;

                int badgeH = 18;

                int badgeY = y + 22;

                g.setColor(new Color(163, 210, 140, 40));

                g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 8, 8);

                g.setColor(new Color(163, 210, 140, 160));

                g.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 8, 8);

                g.setColor(RenderCache.GREEN_163_210_140);

                g.drawString(badge, badgeX + 6, badgeY + 13);

            }



            // Description (medium, light)

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(new Color(175, 185, 200));

            g.drawString(itemDesc, textX, y + 56);



            // Unlock level (small, teal, with dot separator)

            g.setFont(FONT_EXTRA_SMALL_13);

            g.setColor(new Color(120, 175, 200));

            String lvlStr = "Unlocked at Level " + unlockLevel;

            g.drawString(lvlStr, textX, y + 74);



            // Subtle separator line under item info







        } else {



            // --- Locked item ---

            int iconSize = 56;

            int iconX = contentX + 15;

            int iconY = y + (singleCardH - iconSize) / 2;



            // Dark locked circle

            g.setColor(new Color(20, 20, 28, 160));

            g.fillOval(iconX - 3, iconY - 3, iconSize + 6, iconSize + 6);

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

            int textX = iconX + iconSize + 24;



            // Encrypted name

            g.setFont(FONT_EXTRA_SMALL_16);

            g.setColor(new Color(190, 180, 160));

            String lockedName = encryptItemName(itemName);

            g.drawString(lockedName, textX, y + 40);



            // Unlock requirement (prominent, golden)

            g.setFont(FontPalette.get(Font.BOLD, 15));

            g.setColor(new Color(235, 210, 140));

            String reqStr = "Defeat Level " + unlockLevel + " Boss to Unlock";

            g.drawString(reqStr, textX, y + 62);



            // Subtle locked separator







        }




        


        // Counter: X / 9


        g.setFont(FontPalette.get(Font.BOLD, 14));


        g.setColor(ColorPalette.ACCENT_CYAN);


        String counter = (displayIndex + 1) + " / " + allItems.length;


        FontMetrics ctrFm = g.getFontMetrics();


        g.drawString(counter, itemX + (cardWidth - ctrFm.stringWidth(counter)) / 2, y + singleCardH + 20);


        


        y += singleCardH + 30 + cardSpacing;

        currentIndex++;

        

        // Section 2: All Upgrades (indices 1+) - from PassiveUpgradeManager

        y += 20;

        g.setColor(ColorPalette.ACCENT_PURPLE);

        g.setFont(FONT_SMALL);

        g.drawString("SHOP UPGRADES - Allocate purchased levels", width / 2 - 400, y);

        y += 30;

        

        // All upgrades now come from PassiveUpgradeManager

        if (passiveManager != null) {

            java.util.List<PassiveUpgrade> upgrades = passiveManager.getAllUpgrades();

            

            // Draw all adjustable upgrades (all except Extra Missiles which is last)

            for (int i = 0; i < upgrades.size() - 1; i++) {

                PassiveUpgrade upgrade = upgrades.get(i);

                isSelected = currentIndex == selectedStatItem;

                

                String icon = getPassiveIcon(upgrade.getType());

                int owned = upgrade.getCurrentLevel();  // Purchased from shop

                int active = upgrade.getActiveLevel();  // Allocated in stats & loadout

                

                drawUpgradeCard(g, width / 2 - cardWidth / 2, y, cardWidth, cardHeight,

                               icon, upgrade.getName(), active, owned, isSelected, true, false);

                

                y += cardHeight + cardSpacing;

                currentIndex++;

            }

            

            // Read-only section for Extra Missiles

            if (upgrades.size() > 0) {

                y += 20;

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.setFont(FONT_SMALL);

                g.drawString("CONSUMABLE MISSILES - Buy from shop, used on death", width / 2 - 400, y);

                y += 30;

                

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

        g.fillRoundRect(x + 3, y + 3, width, height, 15, 15);

        

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

        g.fillRoundRect(x, y, width, height, 15, 15);

        

        // Border glow for selected

        if (isSelected && !isReadOnly) {

            g.setColor(RenderCache.TAN_180_170_130_140); // Softer border glow

            g.setStroke(RenderCache.getStroke(2f));

            g.drawRoundRect(x, y, width, height, 15, 15);

            g.setStroke(RenderCache.getStroke(1f));

        }

        

        // Draw icon

        g.setFont(FONT_LARGE_32);

        g.setColor(ColorPalette.TEXT_GOLD);

        g.drawString(icon, x + 20, y + 40);

        

        // Draw name

        g.setFont(FONT_SMALL);

        g.setColor(Color.WHITE);

        g.drawString(name, x + 75, y + 30);

        

        // Draw level info

        g.setFont(FontPalette.get(Font.PLAIN, 14));

        g.setColor(RenderCache.GRAY_200);

        String levelInfo;

        if (isReadOnly) {

            levelInfo = "Count: " + current;

        } else {

            levelInfo = isShopUpgrade ? "Allocated: " + current + "/" + max + " owned" : "Level: " + current + "/" + max;

        }

        g.drawString(levelInfo, x + 75, y + 50);

        

        // Don't show progress bar or level text for read-only items

        if (!isReadOnly) {

            // Progress bar

            int barX = x + 400;

            int barY = y + 20;

            int barWidth = 350;

            int barHeight = 10;

            

            // Background

            g.setColor(RenderCache.DARK_40_40_50_180);

            g.fillRoundRect(barX, barY, barWidth, barHeight, 5, 5);

            

            // Fill

            if (max > 0 && current > 0) {

                double progress = (double)current / max;

                int fillWidth = (int)(barWidth * progress);

                

                GradientPaint grad = new GradientPaint(

                    barX, 0, ColorPalette.SUCCESS_GREEN,

                    barX + fillWidth, 0, ColorPalette.TEXT_GOLD

                );

                g.setPaint(grad);

                g.fillRoundRect(barX, barY, fillWidth, barHeight, 5, 5);

            }

            

            // Level text

            g.setFont(FontPalette.get(Font.BOLD, 14));

            g.setColor(current >= max && max > 0 ? ColorPalette.TEXT_GOLD : Color.WHITE);

            String levelText = current + "/" + max;

            FontMetrics fm = g.getFontMetrics();

            g.drawString(levelText, barX + barWidth + 10, barY + 10);

        }

        

        // Show buttons only if not read-only

        if (!isReadOnly) {

            // Minus button

            int btnSize = 35;

            int minusX = x + 800;

            int btnY = y + (height - btnSize) / 2;

            

            g.setColor(current > 0 ? ColorPalette.ACCENT_RED : RenderCache.GRAY_80);

            g.fillRoundRect(minusX, btnY, btnSize, btnSize, 8, 8);

            g.setColor(Color.WHITE);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(minusX, btnY, btnSize, btnSize, 8, 8);

            g.setFont(FONT_LARGE);

            g.drawString("-", minusX + 12, btnY + 26);

            

            // Plus button

            int plusX = x + 845;

            g.setColor(current < max ? ColorPalette.SUCCESS_GREEN : RenderCache.GRAY_80);

            g.fillRoundRect(plusX, btnY, btnSize, btnSize, 8, 8);

            g.setColor(Color.WHITE);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(plusX, btnY, btnSize, btnSize, 8, 8);

            g.setFont(FONT_LARGE);

            g.drawString("+", plusX + 11, btnY + 26);

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

            default: return "?";

        }

    }

    

    public void drawLevelSelect(Graphics2D g, int width, int height, int currentLevel, int maxUnlockedLevel, double time, double scrollOffset, boolean hasSavedGame, int savedLevel, boolean planeTakeoffAnimation, double planeTakeoffTimer) {

        int selectedLevel = gameData.getSelectedLevelView();

        

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "JOURNEY MAP", width, 50, ColorPalette.ACCENT_PURPLE, ColorPalette.ACCENT_YELLOW, time, FontPalette.getDisplay(Font.BOLD, 42));

        

        // Show "RESUME AVAILABLE" indicator if there's a saved game
        FontMetrics fm;

        if (hasSavedGame && selectedLevel == savedLevel) {

            g.setFont(FONT_MEDIUM_BOLD);

            String resumeText = "* RESUME AVAILABLE";

            float resumePulse = (float)(0.7 + 0.3 * Math.sin(time * 3));

            g.setColor(new Color(100, 255, 100, (int)(200 * resumePulse)));

            FontMetrics resumeFm = g.getFontMetrics();

            g.drawString(resumeText, (width - resumeFm.stringWidth(resumeText)) / 2, 110);

        }

        

        // Progress indicator (dots at top)

        int dotY = 80;

        int dotSpacing = 20;

        int totalDots = 28;

        int dotsStartX = (width - (totalDots - 1) * dotSpacing) / 2;

        

        for (int i = 1; i <= totalDots; i++) {

            int dotX = dotsStartX + (i - 1) * dotSpacing;

            int dotSize = (i == selectedLevel) ? 10 : 6;

            

            if (i < currentLevel) {

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

        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g.drawLine(0, centerY, width, centerY);

        

        // Draw arrow indicators on the sides

        if (selectedLevel > 1) {

            // Left arrow

            g.setFont(FontPalette.get(Font.BOLD, 50));

            float arrowPulse = (float)(0.5 + 0.5 * Math.sin(time * 4));

            g.setColor(new Color(150, 150, 160, (int)(100 + 100 * arrowPulse)));

            g.drawString("<", 15, centerY + 18);

        }

        if (selectedLevel < 28) {

            // Right arrow

            g.setFont(FontPalette.get(Font.BOLD, 50));

            float arrowPulse = (float)(0.5 + 0.5 * Math.sin(time * 4));

            g.setColor(new Color(150, 150, 160, (int)(100 + 100 * arrowPulse)));

            g.drawString(">", width - 55, centerY + 18);

        }

        

        // Smooth carousel: use scrollOffset to position all levels

        // Each level is spaced apart, and we scroll based on the animated offset

        int levelSpacing = width / 2; // Half screen width between levels

        int centerNodeRadius = 80; // Larger center node

        int sideNodeRadius = 50;   // Smaller side nodes

        

        // Draw levels based on scroll position (show 5 levels for smooth transitions)

        for (int i = -2; i <= 2; i++) {

            int level = selectedLevel + i;

            if (level < 1 || level > 28) continue;

            

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

            

            g.setComposite(RenderCache.getAlpha(alpha));

            

            // Selection glow for center

            if (isSelected && distFromCenter < 0.3) {

                float glowPulse = (float)(0.3 + 0.2 * Math.sin(time * 4));

                g.setComposite(RenderCache.getAlpha(glowPulse * alpha));

                Color glowColor = isCurrent ? SEL_GLOW_CURRENT : 

                                  isCompleted ? SEL_GLOW_COMPLETED : SEL_GLOW_OTHER;

                g.setColor(glowColor);

                g.fillOval(x - nodeRadius - 25, centerY - nodeRadius - 25, (nodeRadius + 25) * 2, (nodeRadius + 25) * 2);

                g.setComposite(RenderCache.getAlpha(alpha));

            }

            

            // Node shadow

            g.setComposite(RenderCache.getAlpha(alpha));
            g.setColor(NODE_SHADOW);

            g.fillOval(x - nodeRadius + 5, centerY - nodeRadius + 5, nodeRadius * 2, nodeRadius * 2);

            

            // Completed level: golden completion ring behind the node

            if (isCompleted) {

                float ringPulse = (float)(0.6 + 0.4 * Math.sin(time * 2 + level * 0.3));

                g.setComposite(RenderCache.getAlpha(ringPulse * alpha));

                g.setColor(NODE_COMPLETED_RING);

                g.setStroke(RenderCache.getStroke(4));

                g.drawOval(x - nodeRadius - 8, centerY - nodeRadius - 8, (nodeRadius + 8) * 2, (nodeRadius + 8) * 2);

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

                    

                    int spriteY = centerY - nodeRadius - spriteHeight - 110 - (int)bounceOffset - takeoffOffset;

                    

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
                        int fixedGlowY = centerY - nodeRadius - spriteHeight - 110;
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

                    

                    // Create transform for rotation (rotate 180 degrees to point tip up, plus Z-axis spin)

                    AffineTransform oldTransform = g.getTransform();

                    g.translate(spriteX, spriteY + spriteHeight / 2);

                    g.rotate(Math.PI); // 180 degrees to point tip up

                    

                    // Simulate Z-axis rotation by scaling width with sine wave (full 360)

                    double zRotation = Math.sin(time * 2 + level * 0.5);

                    double scaleX = zRotation; // Scale between -1.0 and 1.0 for full rotation

                    g.scale(scaleX, 1.0);

                    

                    g.drawImage(planeSprite, -spriteWidth / 2, -spriteHeight / 2, spriteWidth, spriteHeight, null);

                    

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

                            

                            // Position rotor near top of helicopter (offset from center)

                            int rotorOffsetY = spriteHeight / 6; // Adjusted rotor position (lower)

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

                        g.setColor(new Color(255, 255, 255, (int)(200 * sparkle)));

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

                g.drawString(crown, x - fm.stringWidth(crown) / 2, centerY - nodeRadius - 10);

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

                g.drawString(lock, x - fm.stringWidth(lock) / 2, centerY + nodeRadius + lockSize + 5);

            }

            

            // "CLEARED" label below completed nodes

            if (isCompleted) {

                int labelSize = (int)(14 * scale);

                g.setFont(FontPalette.get(Font.BOLD, labelSize));

                g.setColor(new Color(220, 190, 60, (int)(220 * alpha)));

                String cleared = "CLEARED";

                fm = g.getFontMetrics();

                g.drawString(cleared, x - fm.stringWidth(cleared) / 2, centerY + nodeRadius + labelSize + 5);

            }

            

            g.setComposite(RenderCache.getAlpha(1.0f));

        }

        

        // Draw info panel for selected level at bottom

        drawLevelInfoPanel(g, width, height, selectedLevel, currentLevel, time);

    }

    

    private void drawLevelInfoPanel(Graphics2D g, int width, int height, int selectedLevel, int currentLevel, double time) {

        int panelHeight = 200;

        int panelY = height - panelHeight - 30;

        int panelWidth = 700;

        int panelX = (width - panelWidth) / 2;

        

        // Panel background with rounded corners

        g.setColor(PANEL_BG);

        g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 25, 25);

        

        // Border glow based on status

        boolean isCompleted = selectedLevel < currentLevel;

        boolean isCurrent = selectedLevel == currentLevel;

        boolean isMegaBoss = selectedLevel % 3 == 0;

        

        Color borderColor = isCompleted ? new Color(80, 160, 80) :

                           isCurrent ? RenderCache.BLUE_100_200_255 :

                           new Color(70, 70, 80);

        g.setColor(borderColor);

        g.setStroke(RenderCache.getStroke(3));

        g.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 25, 25);

        

        // Boss name - centered

        String bossName = GameData.getBossName(selectedLevel);

        g.setFont(FONT_LARGE_32);

        FontMetrics fm = g.getFontMetrics();

        int nameX = panelX + (panelWidth - fm.stringWidth(bossName)) / 2;

        

        if (isMegaBoss) {

            GradientPaint nameGrad = new GradientPaint(nameX, panelY + 40, GLOW_MEGA_BOSS, 

                                                        nameX + fm.stringWidth(bossName), panelY + 40, PANEL_MEGA_LABEL);

            g.setPaint(nameGrad);

        } else {

            g.setColor(PANEL_TEXT_NAME);

        }

        g.drawString(bossName, nameX, panelY + 45);

        

        // Level type label - centered

        g.setFont(FONT_EXTRA_SMALL_16);

        g.setColor(isMegaBoss ? PANEL_MEGA_LABEL : PANEL_DIM_LABEL);

        String typeLabel = isMegaBoss ? "* MEGA BOSS - Level " + selectedLevel : "Level " + selectedLevel;

        fm = g.getFontMetrics();

        g.drawString(typeLabel, panelX + (panelWidth - fm.stringWidth(typeLabel)) / 2, panelY + 70);

        

        // Status and stats info

        g.setFont(FONT_EXTRA_SMALL_16);

        int infoY = panelY + 100;

        

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

            infoY += 20;

            

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

                infoY += 18;

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

                infoY += 18;

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

                infoY += 18;

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

        drawPromptWithIcons(g, panelX + panelWidth / 2, panelY + panelHeight - 15,

            "", KeyBindManager.Action.MOVE_LEFT, " ", KeyBindManager.Action.MOVE_RIGHT, " or CLICK  Navigate    ", KeyBindManager.Action.CONFIRM, " or CLICK  Start    ", KeyBindManager.Action.BACK, "  Back");

    }

    

    public void drawRiskContract(Graphics2D g, int width, int height, int selectedContract, 

                                  String[] contractNames, String[] contractDescriptions, 

                                  double[] contractMultipliers, double time, int level) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "RISK CONTRACT", width, 80, ColorPalette.ACCENT_RED_BRIGHT, ColorPalette.ACCENT_ORANGE, time, FontPalette.getDisplay(Font.BOLD, 48));

        

        // Subtitle

        g.setFont(FONT_SMALL);

        String subtitle = "Choose your challenge modifier for Level " + level;

        FontMetrics subFm = g.getFontMetrics();

        g.setColor(ColorPalette.TEXT_DIM);

        g.drawString(subtitle, (width - subFm.stringWidth(subtitle)) / 2, 120);

        

        // Draw contract cards (larger and centered)

        int cardWidth = 280;

        int cardHeight = 380;

        int cardSpacing = 40;

        int totalWidth = contractNames.length * cardWidth + (contractNames.length - 1) * cardSpacing;

        int startX = (width - totalWidth) / 2;

        int cardY = (height - cardHeight) / 2 - 40;

        

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

            g.fillRoundRect(cardX + offsetX + 5, cardY + offsetY + 5, scaledWidth, scaledHeight, 15, 15);

            

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

            g.setStroke(new BasicStroke(isSelected ? 3 : 2));

            g.drawRoundRect(cardX + offsetX, cardY + offsetY, scaledWidth, scaledHeight, 15, 15);

            

            // Contract icon/symbol - draw custom graphics (larger)

            int iconY = cardY + offsetY + 65;

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

            g.setFont(FONT_MEDIUM_BOLD);

            FontMetrics nameFm = g.getFontMetrics();

            g.setColor(isSelected ? Color.WHITE : RenderCache.GRAY_150);

            g.drawString(contractNames[i], cardX + offsetX + (scaledWidth - nameFm.stringWidth(contractNames[i])) / 2, 

                        cardY + offsetY + 120);

            

            // Multiplier

            g.setFont(FontPalette.get(Font.BOLD, 36));

            String multiplier = i == 0 ? "--" : String.format("%.2fx", contractMultipliers[i]);

            FontMetrics multFm = g.getFontMetrics();

            g.setColor(i == 0 ? RenderCache.GRAY_150 : ColorPalette.ACCENT_YELLOW);

            g.drawString(multiplier, cardX + offsetX + (scaledWidth - multFm.stringWidth(multiplier)) / 2, 

                        cardY + offsetY + 170);

            

            // Description (word wrapped)

            g.setFont(FONT_EXTRA_SMALL_16);

            g.setColor(isSelected ? RenderCache.GRAY_200 : RenderCache.GRAY_120);

            String desc = contractDescriptions[i];

            int descY = cardY + offsetY + 210;

            int maxLineWidth = scaledWidth - 30;

            

            // Simple word wrapping

            String[] words = desc.split(" ");

            StringBuilder line = new StringBuilder();

            int lineY = descY;

            for (String word : words) {

                String testLine = line.isEmpty() ? word : line + " " + word;

                FontMetrics descFm = g.getFontMetrics();

                if (descFm.stringWidth(testLine) > maxLineWidth) {

                    g.drawString(line.toString(), cardX + offsetX + 15, lineY);

                    line = new StringBuilder(word);

                    lineY += 22;

                } else {

                    line = new StringBuilder(testLine);

                }

            }

            if (!line.isEmpty()) {

                g.drawString(line.toString(), cardX + offsetX + 15, lineY);

            }

        }

        

        // Controls hint

        g.setFont(FONT_INFO);

        g.setColor(RenderCache.GRAY_150);

        drawPromptWithIcons(g, width / 2, height - 40,

            "", KeyBindManager.Action.MOVE_LEFT, " ", KeyBindManager.Action.MOVE_RIGHT, " or CLICK  Select   |   ", KeyBindManager.Action.CONFIRM, " or CLICK  Confirm   |   ", KeyBindManager.Action.BACK, "  Back");

        

        // Warning for risky contracts

        if (selectedContract > 0) {

            g.setFont(FONT_EXTRA_SMALL_16);

            g.setColor(new Color(255, 100, 100, (int)(200 + 55 * Math.sin(time * 3))));

            String warning = "!! Higher risk = Higher reward !!";

            FontMetrics warnFm = g.getFontMetrics();

            g.drawString(warning, (width - warnFm.stringWidth(warning)) / 2, height - 70);

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

        String title = isResume ? "RESUME LEVEL " + level + "?" : "START LEVEL " + level + "?";

        UITheme.drawTitle(g, title, width, height / 2 - 50, ColorPalette.TEXT_GOLD, ColorPalette.ACCENT_ORANGE, time, FontPalette.get(Font.BOLD, 56));

        

        // Yes and No buttons

        int buttonWidth = 150;

        int buttonHeight = 60;

        int buttonSpacing = 50;

        int totalWidth = 2 * buttonWidth + buttonSpacing;

        int startX = (width - totalWidth) / 2;

        int buttonY = height / 2 + 50;

        

        // Draw Yes button

        boolean yesSelected = (selectedConfirmItem == 0);

        Color yesColor = yesSelected ? ColorPalette.SUCCESS_GREEN : new Color(80, 90, 70);

        Color yesHover = new Color(180, 210, 160);

        

        // Button shadow

        g.setColor(RenderCache.BLACK_100);

        g.fillRoundRect(startX + 3, buttonY + 3, buttonWidth, buttonHeight, 10, 10);

        

        // Button background

        if (yesSelected) {

            double pulse = 1.02 + 0.02 * Math.sin(time * 4);

            int pulsedWidth = (int)(buttonWidth * pulse);

            int pulsedHeight = (int)(buttonHeight * pulse);

            int offsetX = (buttonWidth - pulsedWidth) / 2;

            int offsetY = (buttonHeight - pulsedHeight) / 2;

            g.setColor(yesHover);

            g.fillRoundRect(startX + offsetX, buttonY + offsetY, pulsedWidth, pulsedHeight, 10, 10);

        } else {

            g.setColor(yesColor);

            g.fillRoundRect(startX, buttonY, buttonWidth, buttonHeight, 10, 10);

        }

        

        // Button border

        g.setColor(yesSelected ? new Color(200, 230, 180) : new Color(120, 130, 110));

        g.setStroke(RenderCache.getStroke(2));

        g.drawRoundRect(startX, buttonY, buttonWidth, buttonHeight, 10, 10);

        

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

        g.fillRoundRect(noButtonX + 3, buttonY + 3, buttonWidth, buttonHeight, 10, 10);

        

        // Button background

        if (noSelected) {

            double pulse = 1.02 + 0.02 * Math.sin(time * 4);

            int pulsedWidth = (int)(buttonWidth * pulse);

            int pulsedHeight = (int)(buttonHeight * pulse);

            int offsetX = (buttonWidth - pulsedWidth) / 2;

            int offsetY = (buttonHeight - pulsedHeight) / 2;

            g.setColor(noHover);

            g.fillRoundRect(noButtonX + offsetX, buttonY + offsetY, pulsedWidth, pulsedHeight, 10, 10);

        } else {

            g.setColor(noColor);

            g.fillRoundRect(noButtonX, buttonY, buttonWidth, buttonHeight, 10, 10);

        }

        

        // Button border

        g.setColor(noSelected ? new Color(230, 130, 140) : new Color(120, 70, 80));

        g.setStroke(RenderCache.getStroke(2));

        g.drawRoundRect(noButtonX, buttonY, buttonWidth, buttonHeight, 10, 10);

        

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

        drawPromptWithIcons(g, width / 2, height - 40,

            "", KeyBindManager.Action.MOVE_LEFT, " ", KeyBindManager.Action.MOVE_RIGHT, " or CLICK  Select   |   ", KeyBindManager.Action.CONFIRM, " or CLICK  Confirm   |   ", KeyBindManager.Action.BACK, "  Back");

    }

    

    public void drawGame(Graphics2D g, int width, int height, Player player, Boss boss, List<Bullet> bullets, List<Particle> particles, List<BeamAttack> beamAttacks, int level, double time, boolean bossVulnerable, double invulnerabilityTimer, int dodgeCombo, boolean showCombo, boolean bossDeathAnimation, double bossDeathScale, double bossDeathRotation, double gameTime, int fps, boolean shieldActive, boolean playerInvincible, int bossHitCount, double cameraX, double cameraY, boolean introPanActive, double bossFlashTimer, double screenFlashTimer, ComboSystem comboSystem, List<DamageNumber> damageNumbers, boolean bossIntroActive, String bossIntroText, double bossIntroTimer, boolean isPaused, int selectedPauseItem, List<Achievement> pendingAchievements, double achievementNotificationTimer, boolean deathSequenceActive, boolean playerHidden, int respawnBlinkTimer, int riskContractType, boolean riskContractActive, double stoppedMovingTimer, boolean unpauseCountdownActive, double unpauseCountdownTimer, double itemReadyFlickerTimer, double itemCompleteFlashTimer, double achievementFlashTimer, double bossIntroFlashTimer, double countdownFlashTimer, double bossHitFlashTimer, double typePurgeFlashTimer, Color typePurgeFlashColor, java.util.List<double[]> moneyCircles, double moneyCircleRadius, double frostBeamAngle, double frostBeamProgress, double frostBeamStopDistance, boolean frostBeamRetracting, double frostBeamRetractPhase, int shieldHits, double shieldOrbitAngle, double bossIntroPlayerX, double bossIntroBossX, double bossIntroVsScale, double bossIntroFlash, int bossIntroPhase, List<Particle> introParticles, double deathFlashTimer) {

        // Draw background based on mode setting

        if (Game.backgroundMode == 0) {

            // Gradient mode

            int palIdx = getLevelGradientPaletteIndex(level);

            drawAnimatedGradient(g, width, height, time, palIdx);

        } else if (Game.backgroundMode == 1 && backgroundsLoaded) {

            // Parallax mode

            drawParallaxBackground(g, width, height, level, time);

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

            

            Graphics2D g2d = (Graphics2D) g.create();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            

            // Draw hollow circle CENTERED on player - ICE BLUE

            int alpha = (int)(200 * circleProgress);

            

            // Outer glow circle

            g2d.setColor(new Color(100, 180, 230, (int)(80 * circleProgress)));

            g2d.setStroke(new BasicStroke((float)(10 * circleProgress)));

            g2d.drawOval((int)(circleX - circleRadius), (int)(circleY - circleRadius), 

                        (int)(circleRadius * 2), (int)(circleRadius * 2));

            

            // Main circle ring

            g2d.setColor(new Color(136, 192, 208, alpha));

            g2d.setStroke(new BasicStroke((float)(5 * circleProgress)));

            g2d.drawOval((int)(circleX - circleRadius), (int)(circleY - circleRadius), 

                        (int)(circleRadius * 2), (int)(circleRadius * 2));

            

            // Inner bright ring

            g2d.setColor(new Color(200, 235, 255, alpha));

            g2d.setStroke(new BasicStroke((float)(2 * circleProgress)));

            int innerOffset = (int)(3 * circleProgress);

            g2d.drawOval((int)(circleX - circleRadius + innerOffset), (int)(circleY - circleRadius + innerOffset), 

                        (int)(circleRadius * 2 - innerOffset * 2), (int)(circleRadius * 2 - innerOffset * 2));

            

            // Draw the beam extending from circle edge - with smooth base and end cap

            // Skip beam body if length is 0 (final retraction phase)

            if (laserLength > 5) {

                Graphics2D beamG = (Graphics2D) g.create();

                beamG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                beamG.translate(beamStartX, beamStartY);

                beamG.rotate(angle);

                

                // Alpha fades in smoothly, and applies retraction alpha

                double beamAlpha = Math.min(1.0, frostBeamProgress / 0.15) * alphaMultiplier;

            

            // === BASE CONNECTION - Smooth gradient from circle ===

            // Draw a filled semicircle at the base to connect smoothly to the ring

            int baseRadius = (int)(currentWidth * 0.9);

            beamG.setColor(new Color(100, 180, 230, (int)(60 * beamAlpha)));

            beamG.fillArc(-baseRadius/2, -baseRadius, baseRadius, baseRadius * 2, -90, 180);

            

            beamG.setColor(new Color(136, 192, 208, (int)(150 * beamAlpha)));

            int innerBaseRadius = (int)(currentWidth * 0.5);

            beamG.fillArc(-innerBaseRadius/2, -innerBaseRadius, innerBaseRadius, innerBaseRadius * 2, -90, 180);

            

            beamG.setColor(new Color(200, 235, 255, (int)(200 * beamAlpha)));

            int coreBaseRadius = (int)(currentWidth * 0.2);

            beamG.fillArc(-coreBaseRadius/2, -coreBaseRadius, coreBaseRadius, coreBaseRadius * 2, -90, 180);

            

            // === MAIN BEAM BODY - Consistent width rectangles ===

            // Outer glow beam - ICE BLUE

            beamG.setColor(new Color(100, 180, 230, (int)(50 * beamAlpha)));

            beamG.fillRect(0, (int)(-currentWidth * 0.9), (int)laserLength, (int)(currentWidth * 1.8));

            

            // Inner beam - ICE BLUE

            beamG.setColor(new Color(136, 192, 208, (int)(180 * beamAlpha)));

            beamG.fillRect(0, (int)(-currentWidth * 0.5), (int)laserLength, (int)(currentWidth * 1.0));

            

            // Core beam - ICY WHITE (brightest)

            beamG.setColor(new Color(200, 235, 255, (int)(220 * beamAlpha)));

            beamG.fillRect(0, (int)(-currentWidth * 0.2), (int)laserLength, (int)(currentWidth * 0.4));

            

            // === END CAP - Ice crystal topper ===

            int endX = (int)laserLength;

            

            // Outer glow end cap (rounded)

            beamG.setColor(new Color(100, 180, 230, (int)(70 * beamAlpha)));

            int endCapRadius = (int)(currentWidth * 1.2);

            beamG.fillOval(endX - endCapRadius/2, -endCapRadius, endCapRadius, endCapRadius * 2);

            

            // Inner end cap

            beamG.setColor(new Color(136, 192, 208, (int)(180 * beamAlpha)));

            int innerEndRadius = (int)(currentWidth * 0.7);

            beamG.fillOval(endX - innerEndRadius/3, -innerEndRadius, innerEndRadius, innerEndRadius * 2);

            

            // Core bright end

            beamG.setColor(new Color(200, 235, 255, (int)(230 * beamAlpha)));

            int coreEndRadius = (int)(currentWidth * 0.35);

            beamG.fillOval(endX - coreEndRadius/4, -coreEndRadius, coreEndRadius, coreEndRadius * 2);

            

            // Ice crystal spikes at the tip

            beamG.setColor(new Color(220, 245, 255, (int)(200 * beamAlpha)));

            int spikeLength = (int)(currentWidth * 0.6);

            // Center spike

            int[] spikeX = {endX, endX + spikeLength, endX};

            int[] spikeY = {(int)(-currentWidth * 0.15), 0, (int)(currentWidth * 0.15)};

            beamG.fillPolygon(spikeX, spikeY, 3);

            // Top spike

            int[] spikeX2 = {endX, endX + (int)(spikeLength * 0.6), endX};

            int[] spikeY2 = {(int)(-currentWidth * 0.4), (int)(-currentWidth * 0.2), (int)(-currentWidth * 0.15)};

            beamG.fillPolygon(spikeX2, spikeY2, 3);

            // Bottom spike

            int[] spikeX3 = {endX, endX + (int)(spikeLength * 0.6), endX};

            int[] spikeY3 = {(int)(currentWidth * 0.4), (int)(currentWidth * 0.2), (int)(currentWidth * 0.15)};

            beamG.fillPolygon(spikeX3, spikeY3, 3);

            

            beamG.dispose();

            } // End of laserLength > 5 check

            g2d.dispose();

        }

        

        // Draw particles (behind sprites) - indexed loop avoids ArrayList copy
        // Safe: update() and render run sequentially on game thread
        int particleCount = particles.size();
        for (int pi = 0; pi < particleCount; pi++) {
            Particle particle = particles.get(pi);
            if (particle != null && particle.isAlive() && particle.getType() != Particle.ParticleType.MONEY_SIGN) {
                particle.draw(g);
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

        

        // Draw player (only if not in death animation, not during boss intro cinematic, and not hidden during death sequence)

        if (player != null && !bossIntroActive && !playerHidden) {

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

                    g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(px - glowR, py - glowR, glowR * 2, glowR * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Mid glow arc ===

                    g.setColor(SHIELD_ARC_MID);

                    g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Main shield body - thick curved line ===

                    g.setColor(SHIELD_ARC_MAIN);

                    g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Bright inner edge highlight ===

                    int innerR = orbitRadius - 3;

                    g.setColor(SHIELD_ARC_EDGE);

                    g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

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

        if (boss != null && !bossIntroActive) {

        if (bossDeathAnimation) {

            // Save original transform

            Graphics2D g2d = (Graphics2D) g.create();

            

            // Apply death animation transformations

            g2d.translate(boss.getX(), boss.getY());

            g2d.rotate(bossDeathRotation);

            g2d.scale(bossDeathScale, bossDeathScale);

            g2d.translate(-boss.getX(), -boss.getY());

            

            // Draw boss with transformations

            boss.draw(g2d);

            

            // Add red/orange tint for fire effect

            g2d.setComposite(RenderCache.getAlpha(0.3f));

            g2d.setColor(BOSS_DEATH_FIRE);

            double size = boss.getSize() * bossDeathScale;

            g2d.fillOval((int)(boss.getX() - size/2), (int)(boss.getY() - size/2), (int)size, (int)size);

            

            g2d.dispose();

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

                    // Subtle cool bloom when invulnerable — 1 layer instead of 3

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

                int numArcs = 5; // Reduced from 8 — saves ~30 draw calls/frame

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

            

            // Draw shockwave during recovery phase (circular arc directed at player)

            if (boss.isShockwaveActive()) {
                // Save/restore instead of g.create() — avoids Graphics2D clone
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

        

        // Draw bullets (including warnings for inactive bullets)
        Bullet.activeBulletCount = bullets.size(); // Set for dynamic shadow reduction
        for (int i = 0; i < bullets.size(); i++) {

            Bullet bullet = bullets.get(i);

            if (bullet != null) {

                bullet.draw(g);

            }

        }

        

        // Draw MONEY_SIGN particles ON TOP of player and bullets
        for (int pi = 0; pi < particleCount; pi++) {
            Particle particle = particles.get(pi);
            if (particle != null && particle.isAlive() && particle.getType() == Particle.ParticleType.MONEY_SIGN) {
                particle.draw(g);
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

            

            // Boss hitbox (red circle) - uses size * 0.6 radius for collision

            if (boss != null) {

                int bossHitRadius = (int)(boss.getSize() * 0.6);

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

            int worldW = Game.WORLD_WIDTH;

            int worldH = Game.WORLD_HEIGHT;

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

            

            // Bake level bounds gradient images once (world size never changes)
            if (!levelBoundsBaked) {
                bakeLevelBounds(worldW, worldH, gradSize, gradOffset);
                levelBoundsBaked = true;
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
        boolean bossPresent = boss != null && bossCfg.visible;
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

            int barWidth = 600;

            int barHeight = 40;

            

            int barX = (int)(bossCfg.xPercent * width);

            int barY = (int)(bossCfg.yPercent * height + animSlideY);
            
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

            g.fillRoundRect(barX + 3, barY + 3, barWidth, barHeight + 45, 15, 15);

            g.setColor(RenderCache.DARK_20_20_30_200);

            g.fillRoundRect(barX, barY, barWidth, barHeight + 45, 15, 15);

            

            // Boss type label

            g.setFont(FontPalette.get(Font.BOLD, 14));

            FontMetrics fm = g.getFontMetrics();

            Color typeColor = boss.isMegaBoss() ? RenderCache.RED_255_50_50 : RenderCache.GREEN_100_200_100;

            g.setColor(typeColor);

            g.drawString(bossType, barX + 10, barY + 18);

            

            // Boss name

            g.setFont(FONT_TINY);

            fm = g.getFontMetrics();

            g.setColor(boss.isMegaBoss() ? ColorPalette.ACCENT_YELLOW : Color.WHITE);

            g.drawString(bossName, barX + 10, barY + 38);

            

            // Health bar background

            g.setColor(RenderCache.GRAY_60);

            g.fillRoundRect(barX + 10, barY + 45, barWidth - 20, 15, 8, 8);

            

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

            g.fillRoundRect(barX + 10, barY + 45, barWidth - 20, 15, 8, 8);

            

            // Add hit indicators based on boss health (6 for mini, 9 for mega)

            int maxHits = boss.getMaxHealth();

            g.setColor(RenderCache.BLACK_150);

            int segmentWidth = (barWidth - 20) / maxHits;

            for (int i = 1; i < maxHits; i++) {

                int dividerX = barX + 10 + (segmentWidth * i);

                g.fillRect(dividerX - 1, barY + 45, 2, 15);

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

            

            // Health bar border

            g.setColor(RenderCache.GRAY_200);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(barX + 10, barY + 45, barWidth - 20, 15, 8, 8);

            g.setTransform(bossBarTransform);
            g.setComposite(bossOrigComposite);
        }

        

        // Restore to identity transform for remaining UI elements (no zoom, no camera offset)

        g.setTransform(new AffineTransform());

        

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

        

        // Boss intro cinematic (anime sequential reveal)

        if (bossIntroActive) {

            if (bossIntroPhase == 5) {

                // Phase 5: cheap blur via downscale/upscale (renders at reduced res, bilinear scaling = natural blur)

                double blurProgress = Math.min(1.0, (bossIntroTimer - 320) / 60.0);

                // Scale factor: starts at 0.15 (heavy blur) Ã¢â€ â€™ 1.0 (sharp) as phase progresses

                double scaleFactor = 0.25 + 0.75 * blurProgress;

                if (scaleFactor < 0.85) {

                    int sw = Math.max(1, (int)(width * scaleFactor));

                    int sh = Math.max(1, (int)(height * scaleFactor));

                    // Reuse cached buffer if same size, otherwise allocate

                    if (introBuf == null || introBufW != sw || introBufH != sh) {

                        introBuf = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);

                        introBufW = sw;

                        introBufH = sh;

                    }

                    Graphics2D sg = introBuf.createGraphics();

                    sg.setComposite(AlphaComposite.Clear);

                    sg.fillRect(0, 0, sw, sh);

                    sg.setComposite(AlphaComposite.SrcOver);

                    sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    sg.scale(scaleFactor, scaleFactor);

                    drawBossIntroCinematic(sg, width, height, player, boss, bossIntroText,

                        bossIntroTimer, bossIntroPlayerX, bossIntroBossX, bossIntroVsScale,

                        bossIntroFlash, bossIntroPhase, introParticles, time, level, bossIntroFlashTimer);

                    sg.dispose();

                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    g.drawImage(introBuf, 0, 0, width, height, null);

                } else {

                    drawBossIntroCinematic(g, width, height, player, boss, bossIntroText,

                        bossIntroTimer, bossIntroPlayerX, bossIntroBossX, bossIntroVsScale,

                        bossIntroFlash, bossIntroPhase, introParticles, time, level, bossIntroFlashTimer);

                }

            } else {

                drawBossIntroCinematic(g, width, height, player, boss, bossIntroText,

                    bossIntroTimer, bossIntroPlayerX, bossIntroBossX, bossIntroVsScale,

                    bossIntroFlash, bossIntroPhase, introParticles, time, level, bossIntroFlashTimer);

            }

        } else {

            introBuf = null; // Release blur buffer when intro is done

        }

        

        // Draw Can't Stop contract warning

        if (riskContractType == 4 && riskContractActive && stoppedMovingTimer > 0 && !isPaused) {

            int gracePerio = 90; // Match STOPPED_GRACE_PERIOD from Game

            double timeRemaining = (gracePerio - stoppedMovingTimer) / 60.0; // Convert to seconds

            float dangerLevel = (float) stoppedMovingTimer / gracePerio;

            

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

            Color warningColor = new Color(

                (int) (191 + dangerLevel * 64),

                (int) (97 * (1.0 - dangerLevel)),

                (int) (106 * (1.0 - dangerLevel)),

                (int) (150 + dangerLevel * 105)

            );

            g.setColor(warningColor);

            g.fillRoundRect(barX, barY, progressWidth, barHeight, 10, 10);

            

            // Border

            g.setColor(RenderCache.GRAY_200);

            g.setStroke(RenderCache.getStroke(2));

            g.drawRoundRect(barX, barY, barWidth, barHeight, 10, 10);

            

            // Warning text with pulsing effect

            g.setFont(FONT_SMALL);

            float textPulse = (float) (0.7 + 0.3 * Math.sin(time * 8 * (1 + dangerLevel * 2)));

            g.setColor(new Color(255, 255, 255, (int) (255 * textPulse)));

            String warningText = dangerLevel < 0.5 ? "KEEP MOVING!" : 

                                dangerLevel < 0.8 ? "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â  MOVE NOW!" : "ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â  MOVE! ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ";

            FontMetrics fm = g.getFontMetrics();

            g.drawString(warningText, barX + (barWidth - fm.stringWidth(warningText)) / 2, barY + 26);

            

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

            int buttonY = height / 2 - 30;

            for (int i = 0; i < activePauseButtonCount; i++) {

                pauseButtons[i].setPosition((width - 300) / 2, buttonY + i * 80);

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

            

            g.setColor(new Color(0, 0, 0, (int)(180 * alpha)));

            g.drawString(countdownText, centerX - textWidth / 2 + 4, centerY + 4);

            

            // Main text

            g.setColor(new Color(countdownColor.getRed(), countdownColor.getGreen(), countdownColor.getBlue(), (int)(255 * alpha)));

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
            g.setColor(RenderCache.BLACK_150);
            g.fillRoundRect(infoX, infoY, 280, 140, 10, 10);

            g.setColor(Color.WHITE);
            g.setFont(FONT_MEDIUM_BOLD);
            g.drawString("Level: " + level, infoX + 10, infoY + 25);
            g.drawString("Score: " + (int)displayedScore, infoX + 10, infoY + 55);
            g.drawString("Money: $" + (int)displayedMoney, infoX + 10, infoY + 85);

            // Display timer and FPS
            g.setFont(FONT_INFO);
            int minutes = (int)(gameTime / 60);
            int seconds = (int)(gameTime % 60);
            int milliseconds = (int)((gameTime % 1) * 100);
            String timeStr = String.format("Time: %d:%02d.%02d", minutes, seconds, milliseconds);
            g.drawString(timeStr, infoX + 10, infoY + 110);
            g.drawString("FPS: " + fps, infoX + 10, infoY + 135);
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
        if (equippedItem != null && itemCfg.visible) {
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

                Graphics2D g2d = (Graphics2D) g.create();

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

                g2d.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, glowAlpha))));

                g2d.setColor(glowColor);

                g2d.setStroke(RenderCache.getStroke(2.5f));

                g2d.drawRoundRect(itemUIX, itemUIY, itemUIW, itemUIH, 10, 10);

                g2d.dispose();

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
        if (missileCfg.visible && !introPanActive) {
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

                // Bar fill - bottom-to-top
                for (int s = 0; s < currentMissiles; s++) {
                    int segY = barStartY + totalBarHeight - (s + 1) * segmentHeight;
                    GradientPaint segGrad;
                    if (s < baseMissiles) {
                        segGrad = new GradientPaint(
                            barStartX, 0, RenderCache.GREEN_50_150_50,
                            barStartX + mBarWidth, 0, MISSILE_SEG_GREEN
                        );
                    } else {
                        segGrad = new GradientPaint(
                            barStartX, 0, MISSILE_SEG_GOLD_START,
                            barStartX + mBarWidth, 0, MISSILE_SEG_GOLD_END
                        );
                    }
                    g.setPaint(segGrad);
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
                notifX = stackOriginX - 210;
                notifY = topRightY;
            } else {
                notifX = (int)(achCfg.xPercent * width);
                notifY = (int)(achCfg.yPercent * height);
            }

            Composite _ac = g.getComposite();

            g.setComposite(RenderCache.getAlpha(combinedAlpha));
            g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 230));
            g.fillRoundRect(notifX, notifY, 400, 100, 15, 15);

            g.setFont(FONT_SMALL);
            g.setColor(ColorPalette.TEXT_GOLD);
            g.drawString("Achievement Unlocked!", notifX + 20, notifY + 30);

            g.setFont(FONT_MEDIUM_BOLD);
            g.setColor(ColorPalette.TEXT_PRIMARY);
            g.drawString(ach.getName(), notifX + 20, notifY + 60);

            g.setFont(FontPalette.get(Font.PLAIN, 14));
            g.drawString(ach.getDescription(), notifX + 20, notifY + 85);

            g.setComposite(_ac);
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

        

        // Boss hit flash effect (intense red flash)

        if (bossHitFlashTimer > 0) {

            Composite _fc = g.getComposite();

            float flashAlpha = (float)bossHitFlashTimer / 18.0f * 0.6f; // Strong fade over 18 frames

            g.setComposite(RenderCache.getAlpha(flashAlpha));

            g.setColor(RenderCache.RED_255_50_50); // Intense red flash

            g.fillRect(0, 0, width, height);

            g.setComposite(_fc);

        }

        // Death red vignette effect (baked image for performance)
        if (deathFlashTimer > 0) {
            if (bakedDeathVignette == null || bakedDeathVigW != width || bakedDeathVigH != height) {
                bakedDeathVignette = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
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

        UITheme.drawTitle(g, "ARMORY", width, 100, ColorPalette.ACCENT_PURPLE, ColorPalette.TEXT_GOLD, time);

        

        // Show money with glowing effect

        g.setColor(ColorPalette.SUCCESS_GREEN);

        g.setFont(FontPalette.get(Font.BOLD, 36));

        String money = "Money: $" + gameData.getTotalMoney();

        FontMetrics fm = g.getFontMetrics();

        int moneyX = (width - fm.stringWidth(money)) / 2;

        // Glow effect

        g.setComposite(RenderCache.getAlpha(0.3f));

        g.fillRect(moneyX - 20, 140, fm.stringWidth(money) + 40, 50);

        g.setComposite(RenderCache.getAlpha(1.0f));

        g.drawString(money, moneyX, 170);

        

        // Show earnings

        g.setColor(ColorPalette.TEXT_GOLD);

        g.setFont(FONT_MEDIUM);

        String earnings = "Earned this run: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(earnings, (width - fm.stringWidth(earnings)) / 2, 210);

        

        // Shop items using buttons

        String[] items = shopManager.getShopItems();

        int y = 250;

        int selectedItem = shopManager.getSelectedShopItem();

        

        // Create a clipping region for scrollable area (stop above instructions bar)

        g.setClip(0, 220, width, height - 310);

        

        for (int i = 0; i < items.length; i++) {

            int cost = shopManager.getItemCost(i);

            boolean canAfford = gameData.getTotalMoney() >= cost || i == 0;

            boolean isMaxed = shopManager.isUpgradeMaxed(i);

            

            // Apply scroll offset to Y position

            int scrolledY = (int)(y - scrollOffset);

            int itemX = (width - 900) / 2;

            

            // Update button bounds for mouse interaction

            shopButtons[i].setPosition(itemX, scrolledY - 30);

            shopButtons[i].setSize(900, 70);

            

            // Only draw if visible in the clipping region

            if (scrolledY > 180 && scrolledY < height - 60) {

                // Draw card background with shadow

                g.setColor(RenderCache.BLACK_120);

                g.fillRoundRect(itemX + 3, scrolledY - 27, 900, 70, 15, 15);

                

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

                g.fillRoundRect(itemX, scrolledY - 30, 900, 70, 15, 15);

                

                // Border glow for selected item

                if (i == selectedItem) {

                    g.setColor(RenderCache.TAN_180_170_130_140); // Softer border glow

                    g.setStroke(RenderCache.getStroke(2f));

                    g.drawRoundRect(itemX, scrolledY - 30, 900, 70, 15, 15);

                    g.setStroke(RenderCache.getStroke(1f));

                }

                

                // Draw icon/symbol on the left

                String icon = getItemIcon(i);

                g.setFont(FontPalette.get(Font.BOLD, 36));

                g.setColor(canAfford ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_100);

                g.drawString(icon, itemX + 20, scrolledY + 10);

                

                // Draw item name and description

                String[] itemParts = items[i].split(" - ", 2);

                String itemName = itemParts[0];

                String itemDesc = itemParts.length > 1 ? itemParts[1] : "";

                

                g.setFont(FONT_SMALL);

                g.setColor(canAfford ? Color.WHITE : RenderCache.GRAY_120);

                g.drawString(itemName, itemX + 75, scrolledY - 5);

                

                g.setFont(FontPalette.get(Font.PLAIN, 14));

                g.setColor(canAfford ? RenderCache.GRAY_200 : RenderCache.GRAY_100);

                g.drawString(itemDesc, itemX + 75, scrolledY + 15);

                

                // Draw progress bar for all upgrades (not for Continue)

                // All upgrades now come from PassiveUpgradeManager (shop index 1 = upgrade index 0)

                if (i > 0 && passiveUpgradeManager != null) {

                    int upgradeIndex = i - 1;

                    if (upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {

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

                        

                        int barX = itemX + 75;

                        int barY = scrolledY + 30;

                        int barWidth = 550;

                        int barHeight = 8;

                        

                        // Level text above progress bar

                        g.setFont(FONT_EXTRA_SMALL_11);

                        g.setColor(upgrade.isMaxed() || isMaxed ? ColorPalette.TEXT_GOLD : RenderCache.GRAY_200);

                        String levelText = isExtraMissiles ? currentLevel + "/" + maxLevel + " extra missiles" : currentLevel + "/" + maxLevel;

                        g.drawString(levelText, barX, barY - 3);

                        

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

                        g.drawString(maxedStr, itemX + 900 - maxedW - 20, scrolledY + 10);

                    } else {

                        g.setColor(canAfford ? ColorPalette.SUCCESS_GREEN : ColorPalette.ACCENT_RED);

                        FontMetrics costFm = g.getFontMetrics();

                        String costStr = "$" + cost;

                        int costW = costFm.stringWidth(costStr);

                        g.drawString(costStr, itemX + 900 - costW - 20, scrolledY + 10);

                    }

                }

            }

            

            y += 80;

        }

        

        // Reset clip

        g.setClip(null);

        

        // Instructions background bar to prevent overlap with shop items

        g.setColor(ColorPalette.withAlpha(ColorPalette.BG_DARK, 240));

        g.fillRect(0, height - 80, width, 80);

        

        // Instructions

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_SMALL);

        drawPromptWithIcons(g, width / 2, height - 35,

            "Use ", KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " or MOUSE to select | ", KeyBindManager.Action.CONFIRM, " or CLICK to purchase | ", KeyBindManager.Action.BACK, " to continue");

    }

    

    public void drawGameOver(Graphics2D g, int width, int height, double time) {

        // Military themed dark background

        UITheme.drawScreenBackground(g, width, height, time);

        

        

        // Red overlay pulse

        float redPulse = (float)(0.05 + 0.03 * Math.sin(time * 2));

        g.setColor(new Color(200, 40, 40, (int)(255 * redPulse)));

        g.fillRect(0, 0, width, height);

        

        // Title Ã¢â‚¬â€ MISSION FAILED stamp

        UITheme.drawTitle(g, "MISSION FAILED", width, height / 2 - 140, ColorPalette.ACCENT_RED, ColorPalette.ACCENT_RED_BRIGHT, time);

        

        // Stencil stamp overlay â€” slam animation (randomized position/rotation)

        double elapsed = (screenEnteredTime >= 0) ? time - screenEnteredTime : 10;

        double slamDuration = 0.5;

        int stampCX = width / 2 + stampOffsetX;

        int stampCY = height / 2 - 140 + stampOffsetY;

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

        String level = "Level Reached: " + gameData.getCurrentLevel();

        fm = g.getFontMetrics();

        g.drawString(level, (width - fm.stringWidth(level)) / 2, height / 2 - 40);

        

        String score = "Score: " + gameData.getScore();

        fm = g.getFontMetrics();

        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2);

        

        g.setColor(ColorPalette.TEXT_GOLD);

        String money = "Money Earned: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 + 40);

        

        // Display cumulative run stats

        LevelStats runStats = gameData.getCumulativeRunStats();

        g.setFont(FONT_SMALL);

        g.setColor(ColorPalette.TEXT_DIM);

        int statsY = height / 2 + 85;

        

        if (runStats.getDodges() > 0 || runStats.getPerfectDodges() > 0) {

            String dodges = "Run Stats - Dodges: " + runStats.getDodges() + "  Perfect: " + runStats.getPerfectDodges();

            fm = g.getFontMetrics();

            g.drawString(dodges, (width - fm.stringWidth(dodges)) / 2, statsY);

            statsY += 24;

        }

        

        if (runStats.getBulletsSpawned() > 0 || runStats.getMaxCombo() > 0) {

            String combat = "Bullets Faced: " + runStats.getBulletsSpawned() + "  Max Combo: " + runStats.getMaxCombo() + "x";

            fm = g.getFontMetrics();

            g.drawString(combat, (width - fm.stringWidth(combat)) / 2, statsY);

            statsY += 24;

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

            statsY += 24;

        }

        

        if (runStats.getDamageTaken() > 0 || runStats.getMissilesUsed() > 0) {

            String survival = "Damage Taken: " + runStats.getDamageTaken() + "  Missiles Used: " + runStats.getMissilesUsed();

            fm = g.getFontMetrics();

            g.drawString(survival, (width - fm.stringWidth(survival)) / 2, statsY);

            statsY += 30;

        }

        

        // Add spacing before persistent stats

        statsY += 15;

        

        // Show persistent stats

        g.setFont(FONT_SMALL);

        g.setColor(ColorPalette.TEXT_DIM);

        String totalMoney = "Total Money: $" + gameData.getTotalMoney();

        fm = g.getFontMetrics();

        g.drawString(totalMoney, (width - fm.stringWidth(totalMoney)) / 2, statsY);

        statsY += 25;

        

        String bestRun = "Best Run: Level " + Math.max(gameData.getBestRunLevel(), gameData.getCurrentLevel());

        fm = g.getFontMetrics();

        g.drawString(bestRun, (width - fm.stringWidth(bestRun)) / 2, statsY);

        statsY += 30;

        

        // Show missiles remaining

        if (gameData.getMissiles() > 0) {

            g.setFont(FONT_MEDIUM_BOLD);

            g.setColor(ColorPalette.SUCCESS_GREEN);

            String missileText = "\u2726 Missiles Remaining: " + gameData.getMissiles() + " \u2726";

            fm = g.getFontMetrics();

            g.drawString(missileText, (width - fm.stringWidth(missileText)) / 2, statsY);

            statsY += 35;

        } else {

            statsY += 10;

        }

        

        // Controls

        g.setFont(FONT_MEDIUM);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        drawPromptWithIcons(g, width / 2, statsY,

            KeyBindManager.Action.CONFIRM, " - New Run  |  ", KeyBindManager.Action.BACK, " - Main Menu");

        statsY += 30;

        

        // Roguelike reminder

        g.setFont(FontPalette.get(Font.ITALIC, 18));

        g.setColor(ColorPalette.SUCCESS_GREEN);

        String keep = "Your upgrades and items are saved!";

        fm = g.getFontMetrics();

        g.drawString(keep, (width - fm.stringWidth(keep)) / 2, statsY);

    }

    

    public void drawWin(Graphics2D g, int width, int height, double time, double bossKillTime) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Victory confetti

        UITheme.drawConfetti(g, width, height, time);

        

        // Title Ã¢â‚¬â€ MISSION COMPLETE

        UITheme.drawTitle(g, "MISSION COMPLETE", width, height / 2 - 180, ColorPalette.VICTORY_GOLD, ColorPalette.SUCCESS_GREEN, time);

        

        // Rank badge â€” slam animation (randomized corner)

        int scoreForRank = gameData.getScore();

        String rank = UITheme.calculateRank(scoreForRank);

        double elapsed = (screenEnteredTime >= 0) ? time - screenEnteredTime : 10;

        double rankDelay = 0.4;

        double rankSlamDuration = 0.45;

        double rankElapsed = elapsed - rankDelay;

        int badgeRadius = 60;

        int badgeCX, badgeCY;

        // Badge placed near the text area with slight random offset per quadrant

        int textCenterX = width / 2;

        int textTopY = height / 2 - 180;

        switch (badgeCorner) {

            case 0:  badgeCX = textCenterX + 220; badgeCY = textTopY - 10;  break; // right of title

            case 1:  badgeCX = textCenterX - 220; badgeCY = textTopY - 10;  break; // left of title

            case 2:  badgeCX = textCenterX + 200; badgeCY = textTopY + 60;  break; // right-below

            default: badgeCX = textCenterX - 200; badgeCY = textTopY + 60;  break; // left-below

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

            UITheme.drawRankBadge(g2, badgeCX, badgeCY, badgeRadius, rank, time);

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

            UITheme.drawRankBadge(g2, badgeCX, (int)(badgeCY + bobY), badgeRadius, rank, time);

            g2.dispose();

        }

        // Play slam sound when rank lands

        if (!slamSoundPlayed && rankElapsed >= rankSlamDuration) {

            SoundManager.getInstance().playSound(SoundManager.Sound.HIT_METAL, 0.9f);

            SoundManager.getInstance().playSound(SoundManager.Sound.RANK_UP, 0.7f);

            slamSoundPlayed = true;

        }

        

        // Stats with military styling

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_LARGE_32);

        FontMetrics fm;

        String score = "Score: " + gameData.getScore();

        fm = g.getFontMetrics();

        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2 - 90);

        

        g.setColor(ColorPalette.TEXT_GOLD);

        String money = "Money Earned: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 - 50);

        

        // Display boss kill time

        int minutes = (int)(bossKillTime / 60);

        int seconds = (int)(bossKillTime % 60);

        int milliseconds = (int)((bossKillTime % 1) * 100);

        String timeStr = String.format("Time: %d:%02d.%02d", minutes, seconds, milliseconds);

        fm = g.getFontMetrics();

        g.setColor(ColorPalette.VICTORY_GOLD);

        g.drawString(timeStr, (width - fm.stringWidth(timeStr)) / 2, height / 2 - 10);

        

        // Display level stats (only non-zero stats)

        LevelStats stats = gameData.getCurrentLevelStats();

        g.setFont(FONT_SMALL);

        g.setColor(ColorPalette.TEXT_DIM);

        int statsY = height / 2 + 20;

        

        if (stats.getDodges() > 0) {

            String dodges = "Dodges: " + stats.getDodges();

            fm = g.getFontMetrics();

            g.drawString(dodges, (width - fm.stringWidth(dodges)) / 2, statsY);

            statsY += 26;

        }

        

        if (stats.getPerfectDodges() > 0) {

            String perfect = "Perfect Dodges: " + stats.getPerfectDodges();

            fm = g.getFontMetrics();

            g.setColor(ColorPalette.ACCENT_YELLOW);

            g.drawString(perfect, (width - fm.stringWidth(perfect)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += 26;

        }

        

        if (stats.getNearMisses() > 0) {

            String nearMiss = "Near Misses: " + stats.getNearMisses();

            fm = g.getFontMetrics();

            g.setColor(RenderCache.ORANGE_255_165_0);

            g.drawString(nearMiss, (width - fm.stringWidth(nearMiss)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += 26;

        }

        

        if (stats.getMaxCombo() > 0) {

            String maxCombo = "Max Combo: " + stats.getMaxCombo() + "x";

            fm = g.getFontMetrics();

            g.setColor(RenderCache.BLUE_100_200_255);

            g.drawString(maxCombo, (width - fm.stringWidth(maxCombo)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += 26;

        }

        

        if (stats.getBulletsSpawned() > 0) {

            String bullets = "Bullets: " + stats.getBulletsSpawned();

            fm = g.getFontMetrics();

            g.drawString(bullets, (width - fm.stringWidth(bullets)) / 2, statsY);

            statsY += 26;

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

            statsY += 26;

        }

        

        if (stats.getDamageTaken() > 0) {

            String damage = "Damage Taken: " + stats.getDamageTaken();

            fm = g.getFontMetrics();

            g.setColor(new Color(200, 100, 100));

            g.drawString(damage, (width - fm.stringWidth(damage)) / 2, statsY);

            g.setColor(RenderCache.SLATE_180_190_200);

            statsY += 26;

        }

        

        if (stats.getMissilesUsed() > 0) {

            String missiles = "Missiles Used: " + stats.getMissilesUsed();

            fm = g.getFontMetrics();

            g.drawString(missiles, (width - fm.stringWidth(missiles)) / 2, statsY);

            statsY += 26;

        }

        

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_MEDIUM);

        // Position instruction text below stats, with minimum at height/2 + 160

        int instructionY = Math.max(height / 2 + 160, statsY + 30);

        drawPromptWithIcons(g, width / 2, instructionY,

            "Press ", KeyBindManager.Action.CONFIRM, " to Visit Armory");

    }

    

    public void drawSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, int selectedCategory, GameData gameData) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "SETTINGS", width, 80, ColorPalette.ACCENT_PURPLE, ColorPalette.ACCENT_CYAN, time, FONT_TITLE_MEDIUM);

        

        // Category tabs
        FontMetrics fm;

        String[] categories = {"GRAPHICS", "AUDIO", "GAMEPLAY", "DEBUG", "CONTROLS", "HUD"};

        int tabWidth = 130;

        int tabStartX = (width - categories.length * tabWidth) / 2;

        int tabY = 130;

        

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

            g.fillRoundRect(tabX, tabY, tabWidth - 10, 40, 10, 10);

            

            // Tab border

            if (isSelected) {

                // Double border if tabs are focused

                if (tabsFocused) {

                    g.setColor(ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 200)); // Green glow when focused

                    g.setStroke(RenderCache.getStroke(4));

                    g.drawRoundRect(tabX - 1, tabY - 1, tabWidth - 8, 42, 10, 10);

                }

                g.setColor(ColorPalette.TEXT_GOLD);

                g.setStroke(RenderCache.getStroke(2));

                g.drawRoundRect(tabX, tabY, tabWidth - 10, 40, 10, 10);

            }

            

            // Tab text

            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : ColorPalette.TEXT_PRIMARY);

            fm = g.getFontMetrics();

            g.drawString(categories[i], tabX + (tabWidth - 10 - fm.stringWidth(categories[i])) / 2, tabY + 26);

        }

        

        g.setFont(FONT_EXTRA_SMALL_16);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        if (Game.keyBindManager != null && Game.keyBindManager.isControllerMode()) {
            drawPromptWithIcons(g, width / 2, 195, "D-Pad to navigate | ", KeyBindManager.ControllerButton.RB, " to switch tabs | ", KeyBindManager.Action.BACK, " to exit");
        } else {
            drawPromptWithIcons(g, width / 2, 195, KeyBindManager.Action.MOVE_UP, "/", KeyBindManager.Action.MOVE_DOWN, " to navigate | TAB to switch tabs | ", KeyBindManager.Action.BACK, " to exit");
        }

        

        // Create clipping region for scrollable area

        Shape oldClip = g.getClip();

        g.setClip(0, 200, width, height - 260);

        

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

        

        // Instructions

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_INFO);

        boolean settingsCtrlMode = Game.keyBindManager != null && Game.keyBindManager.isControllerMode();

        if (settingsCtrlMode) {

            drawPromptWithIcons(g, width / 2, height - 30,

                KeyBindManager.ControllerButton.Y, ": Reset Defaults  |  ", KeyBindManager.Action.BACK, " : Return to Menu");

        } else {

            drawPromptWithIcons(g, width / 2, height - 30,

                java.awt.event.KeyEvent.VK_R, " Reset Defaults  |  ", java.awt.event.KeyEvent.VK_ESCAPE, " Return to Menu");

        }

    }

    

    private void drawGraphicsSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset) {

        // Reorganized into logical groups: Display, Quality, Background, Effects, Camera

        String[] settingNames = {"Fullscreen Mode", "Resolution", "VSync", "FPS Limit", "Anti-Aliasing", "Shadows", "Particle Effects", "Bloom/Glow", "Background Mode", "Gradient Animation", "Gradient Quality", "Motion Blur", "Chromatic Aberration", "Vignette", "Grain Effect", "Camera Zoom", "UI Parallax"};

        String[] settingValues = {

            Game.isFullscreen ? "Fullscreen" : "Windowed",

            Game.resolutionPreset == 0 ? "1280x720" : Game.resolutionPreset == 1 ? "1366x768" : Game.resolutionPreset == 2 ? "1600x900" : Game.resolutionPreset == 3 ? "1920x1080" : Game.resolutionPreset == 4 ? "2560x1440" : "3840x2160",

            Game.enableVSync ? "ON" : "OFF",

            Game.fpsLimit == 0 ? "30 FPS" : Game.fpsLimit == 1 ? "60 FPS" : Game.fpsLimit == 2 ? "120 FPS" : Game.fpsLimit == 3 ? "144 FPS" : "Unlimited",

            Game.enableAntiAliasing ? "ON" : "OFF",

            Game.shadowQuality == 0 ? "Off" : Game.shadowQuality == 1 ? "Low" : Game.shadowQuality == 2 ? "Medium" : "High",

            Game.enableParticles ? "ON" : "OFF",

            Game.enableBloom ? "ON" : "OFF",

            Game.backgroundMode == 0 ? "Gradient" : Game.backgroundMode == 1 ? "Parallax" : "Static",

            Game.enableGradientAnimation ? "ON" : "OFF",

            Game.gradientQuality == 0 ? "Low" : Game.gradientQuality == 1 ? "Medium" : "High",

            Game.enableMotionBlur ? "ON" : "OFF",

            Game.enableChromaticAberration ? "ON" : "OFF",

            Game.enableVignette ? "ON" : "OFF",

            Game.enableGrainEffect ? "ON" : "OFF",

            String.format("%.0f%%", Game.cameraZoom * 100),

            Game.enableUIParallax ? "ON" : "OFF"

        };

        

        String[] descriptions = {

            "Toggle between fullscreen and windowed mode (F11)",

            "Display resolution (restart required for changes to take effect)",

            "Synchronize frame rate with monitor refresh (smoother, less tearing)",

            "Maximum frames per second (lower = better performance)",

            "Smooth edges of graphics (better quality, slight performance impact)",

            "Shadow quality - more layers = smoother glow (Off/Low/Medium/High)",

            "Enable particle effects (trails, explosions, etc.)",

            "Glow effect on bright objects (performance impact)",

            "Choose between gradient, parallax images, or static image background",

            "Animate gradient backgrounds (may affect performance)",

            "Number of gradient layers (higher = better but slower)",

            "Blur effect on fast moving objects (performance impact)",

            "Color fringing on screen edges (cinematic effect)",

            "Darken screen edges (focuses attention on center)",

            "Add grain texture overlay (performance impact)",

            "How zoomed in the camera is during gameplay (75% - 150%)",

            "UI elements shift slightly with camera movement for depth effect"

        };

        

        // Only Camera Zoom (15) uses a continuous slider now

        float[][] sliders = new float[settingNames.length][4];

        sliders[15] = new float[]{1, 0.75f, 1.5f, (float)Game.cameraZoom};

        

        boolean[] toggles = {

            Game.isFullscreen, false, Game.enableVSync, false, Game.enableAntiAliasing,

            Game.shadowQuality > 0, Game.enableParticles, Game.enableBloom, false,

            Game.enableGradientAnimation, false, Game.enableMotionBlur, Game.enableChromaticAberration,

            Game.enableVignette, Game.enableGrainEffect, false,

            Game.enableUIParallax

        };

        

        // Section headers: which setting index starts a new section

        String[] sectionHeaders = new String[settingNames.length];

        sectionHeaders[0] = "DISPLAY";

        sectionHeaders[4] = "QUALITY";

        sectionHeaders[8] = "BACKGROUND";

        sectionHeaders[11] = "EFFECTS";

        sectionHeaders[15] = "CAMERA";

        

        // Pill selector options for discrete multi-option settings

        String[][] pillOptions = new String[settingNames.length][];

        int[] pillSelected = new int[settingNames.length];

        

        pillOptions[0] = new String[]{"Windowed", "Fullscreen"};

        pillSelected[0] = Game.isFullscreen ? 1 : 0;

        

        pillOptions[1] = new String[]{"1280x720", "1366x768", "1600x900", "1920x1080", "2560x1440", "3840x2160"};

        pillSelected[1] = Game.resolutionPreset;

        

        pillOptions[3] = new String[]{"30", "60", "120", "144", "Unlimited"};

        pillSelected[3] = Game.fpsLimit;

        

        pillOptions[5] = new String[]{"Off", "Low", "Medium", "High"};

        pillSelected[5] = Game.shadowQuality;

        

        pillOptions[8] = new String[]{"Gradient", "Parallax", "Static"};

        pillSelected[8] = Game.backgroundMode;

        

        pillOptions[10] = new String[]{"Low", "Medium", "High"};

        pillSelected[10] = Game.gradientQuality;

        

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

        // 11 items: Preset, Input Device, then 9 actions

        String[] settingNames = new String[11];

        String[] settingValues = new String[11];

        String[] descriptions = new String[11];

        

        // Item 0: Preset

        settingNames[0] = "Preset";

        settingValues[0] = "< " + kbm.getCurrentPreset().name().replace("_", " ") + " >";

        descriptions[0] = "Choose a keybinding preset (use arrows to change)";

        

        // Item 1: Input Device (read-only)

        settingNames[1] = "Input Device";

        settingValues[1] = kbm.getInputMode() == KeyBindManager.InputMode.CONTROLLER ? "Controller" : "Keyboard";

        descriptions[1] = "Current input device (auto-detected when controller is connected)";

        

        // Items 2-10: Actions

        for (int i = 0; i < actions.length; i++) {

            KeyBindManager.Action action = actions[i];

            settingNames[i + 2] = action.name().replace("_", " ");

            

            // Check if this action is currently being rebound

            if (Game.waitingForKeyBind && Game.rebindingActionIndex == i + 1) {

                if (Game.keyBindManager != null && Game.keyBindManager.isControllerMode()) {

                    settingValues[i + 2] = ">> Press a button <<";

                } else {

                    settingValues[i + 2] = ">> Press a key <<";

                }

            } else {

                settingValues[i + 2] = kbm.getKeyDisplayText(action);

            }

            if (Game.keyBindManager != null && Game.keyBindManager.isControllerMode()) {
                descriptions[i + 2] = "Press " + keyText(KeyBindManager.Action.CONFIRM) + " to rebind | " + keyText(KeyBindManager.Action.BACK) + " to cancel";
            } else {
                descriptions[i + 2] = "Press SPACE or ENTER to rebind | ESC to cancel";
            }

        }

        

        // Custom rendering for controls (key display boxes instead of toggles/sliders)

        int y = 230 - (int)scrollOffset;

        FontMetrics fm;

        

        for (int i = 0; i < settingNames.length; i++) {

            boolean isSelected = i == selectedItem;

            

            int boxWidth = width - 200;

            int boxX = (width - boxWidth) / 2;

            int boxY = y - 10;

            int boxHeight = 50;

            

            // Update settings button position for click detection

            if (i < settingsButtons.length && settingsButtons[i] != null) {

                settingsButtons[i].setPosition(boxX, boxY);

                settingsButtons[i].setSize(boxWidth, boxHeight);

            }

            

            // Skip rendering if outside visible area

            if (y < 170 || y > height - 80) {

                y += 78;

                continue;

            }

            

            if (isSelected) {

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_DIM, 200));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

                

                // Special border for rebinding

                if (Game.waitingForKeyBind && Game.rebindingActionIndex == i - 1) {

                    g.setColor(ColorPalette.ACCENT_RED); // Red border when rebinding

                } else {

                    g.setColor(ColorPalette.TEXT_GOLD);

                }

                g.setStroke(RenderCache.getStroke(2));

                g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

            } else {

                g.setColor(ColorPalette.withAlpha(ColorPalette.BG_LIGHT, 150));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

            }

            

            // Setting name

            g.setFont(FontPalette.get(Font.BOLD, 17));

            g.setColor(isSelected ? ColorPalette.TEXT_GOLD : ColorPalette.TEXT_PRIMARY);

            g.drawString(settingNames[i], boxX + 16, boxY + boxHeight / 2 + 6);

            

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

            } else {

                // Action keybind - draw key in a styled box

                boolean isRebinding = Game.waitingForKeyBind && Game.rebindingActionIndex == i - 1;

                

                // Check if we should show a button/key sprite

                KeyBindManager.Action action = KeyBindManager.Action.values()[i - 2];

                java.awt.image.BufferedImage btnSprite = null;

                if (Game.keyBindManager != null && !isRebinding) {

                    btnSprite = Game.keyBindManager.getActionIcon(action);

                }

                

                String keyText = settingValues[i];

                g.setFont(FontPalette.get(Font.BOLD, 15));

                fm = g.getFontMetrics();

                int keyBoxWidth = Math.max(70, fm.stringWidth(keyText) + 24);

                if (btnSprite != null) keyBoxWidth = Math.max(keyBoxWidth, 76);

                int keyBoxX = boxX + boxWidth - keyBoxWidth - 16;

                int keyBoxY = boxY + 4;

                int keyBoxHeight = boxHeight - 8;

                

                if (isRebinding) {

                    // Flashing background for rebinding

                    float alpha = (float)(0.5 + 0.5 * Math.sin(time * 6));

                    g.setColor(new Color(191, 97, 106, (int)(150 * alpha)));

                    g.fillRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, 8, 8);

                    g.setColor(ColorPalette.ACCENT_RED);

                    g.setStroke(RenderCache.getStroke(2));

                    g.drawRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, 8, 8);

                    g.setColor(ColorPalette.TEXT_GOLD);

                } else {

                    // Normal key box

                    g.setColor(ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

                    g.fillRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, 8, 8);

                    g.setColor(ColorPalette.withAlpha(ColorPalette.ACCENT_CYAN, 150));

                    g.setStroke(RenderCache.getStroke(1));

                    g.drawRoundRect(keyBoxX, keyBoxY, keyBoxWidth, keyBoxHeight, 8, 8);

                    g.setColor(ColorPalette.TEXT_PRIMARY);

                }

                

                // Draw button/key sprite or key text

                if (btnSprite != null) {

                    int spriteH = 30; int spriteW = spriteH * btnSprite.getWidth() / btnSprite.getHeight();

                    g.drawImage(btnSprite, keyBoxX + (keyBoxWidth - spriteW) / 2, keyBoxY + (keyBoxHeight - spriteH) / 2, spriteW, spriteH, null);

                } else {

                    g.drawString(keyText, keyBoxX + (keyBoxWidth - fm.stringWidth(keyText)) / 2, keyBoxY + keyBoxHeight / 2 + 6);

                }

            }

            

            // Draw description below if selected

            if (isSelected) {

                g.setFont(FontPalette.get(Font.ITALIC, 13));

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_PRIMARY, 180));

                fm = g.getFontMetrics();

                int descX = Math.max(10, (width - fm.stringWidth(descriptions[i])) / 2);

                g.drawString(descriptions[i], descX, boxY + boxHeight + 16);

            }

            

            y += 78;

        }

    }

    

    private void drawSettingsList(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, boolean showSliders) {

        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, names, values, descriptions, showSliders, null);

    }

    

    private void drawSettingsList(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, boolean showSliders, float[] sliderValues) {

        int boxWidth = width - 200;

        int boxHeight = 50;

        int itemSpacing = 78;

        int boxX = (width - boxWidth) / 2;

        int y = 230 - (int)scrollOffset;

        FontMetrics fm;

        

        // Reset slider click targets for this tab

        if (sliderMinusBtnX == null || sliderMinusBtnX.length < names.length) {

            sliderMinusBtnX = new int[names.length];

            sliderPlusBtnX = new int[names.length];

            sliderBtnYPos = new int[names.length];

        }

        for (int i = 0; i < names.length; i++) {

            sliderMinusBtnX[i] = -1;

            sliderPlusBtnX[i] = -1;

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

            if (y < 170 || y > height - 80) {

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

            int textYBase = boxY + boxHeight / 2 + 6;

            g.drawString(names[i], boxX + 16, textYBase);

            

            // Value or slider

            if (showSliders && sliderValues != null && i > 0 && !values[i].equals("ON") && !values[i].equals("OFF")) {

                // Volume slider with +/- buttons

                float progress = sliderValues[i];

                int btnSize = sliderBtnSize;

                int centerY = boxY + boxHeight / 2;

                int rightMargin = 16;

                

                g.setFont(FontPalette.get(Font.BOLD, 15));

                fm = g.getFontMetrics();

                int valueW = fm.stringWidth(values[i]) + 12;

                

                int plusBtnX = boxX + boxWidth - rightMargin - valueW - btnSize;

                int sliderEndX = plusBtnX - 8;

                int sliderStartX = boxX + boxWidth / 2 - 20;

                int minusBtnX = sliderStartX - btnSize - 8;

                int sliderW = sliderEndX - sliderStartX;

                

                // Store for click detection

                sliderMinusBtnX[i] = minusBtnX;

                sliderPlusBtnX[i] = plusBtnX;

                sliderBtnYPos[i] = centerY - btnSize / 2;

                

                // [-] button

                g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

                g.fillRoundRect(minusBtnX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

                g.setColor(ColorPalette.TEXT_PRIMARY);

                g.setFont(FONT_EXTRA_SMALL_16);

                fm = g.getFontMetrics();

                g.drawString("\u2212", minusBtnX + (btnSize - fm.stringWidth("\u2212")) / 2, centerY + 6);

                

                // Slider bar

                int sliderH = 6;

                int sliderY = centerY - sliderH / 2;

                g.setColor(ColorPalette.BG_DARK);

                g.fillRoundRect(sliderStartX, sliderY, sliderW, sliderH, 3, 3);

                int fillW = (int)(sliderW * progress);

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.fillRoundRect(sliderStartX, sliderY, Math.max(fillW, 3), sliderH, 3, 3);

                

                // Handle

                int handleX = sliderStartX + fillW - 5;

                g.setColor(ColorPalette.TEXT_GOLD);

                g.fillOval(handleX, centerY - 7, 10, 14);

                

                // [+] button

                g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

                g.fillRoundRect(plusBtnX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

                g.setColor(ColorPalette.TEXT_PRIMARY);

                g.setFont(FONT_EXTRA_SMALL_16);

                fm = g.getFontMetrics();

                g.drawString("+", plusBtnX + (btnSize - fm.stringWidth("+")) / 2, centerY + 6);

                

                // Value text

                g.setFont(FontPalette.get(Font.BOLD, 15));

                g.setColor(ColorPalette.TEXT_PRIMARY);

                fm = g.getFontMetrics();

                g.drawString(values[i], boxX + boxWidth - rightMargin - fm.stringWidth(values[i]), centerY + 6);

            } else if (values[i].equals("ON") || values[i].equals("OFF")) {

                // Draw toggle switch

                drawToggleSwitch(g, boxX, boxY, boxWidth, boxHeight, values[i].equals("ON"), isSelected);

            } else {

                // Regular value text

                g.setFont(FontPalette.get(Font.BOLD, 17));

                fm = g.getFontMetrics();

                g.setColor(ColorPalette.SUCCESS_GREEN);

                g.drawString(values[i], boxX + boxWidth - fm.stringWidth(values[i]) - 16, textYBase);

            }

            

            // Draw description below if selected

            if (isSelected) {

                g.setFont(FontPalette.get(Font.ITALIC, 13));

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_PRIMARY, 180));

                fm = g.getFontMetrics();

                int descX = Math.max(10, (width - fm.stringWidth(descriptions[i])) / 2);

                g.drawString(descriptions[i], descX, boxY + boxHeight + 16);

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

        int boxWidth = width - 200;

        int boxHeight = 50;

        int itemSpacing = 78;

        int boxX = (width - boxWidth) / 2;

        int y = 230 - (int)scrollOffset;

        FontMetrics fm;

        

        // Initialize click target arrays if needed

        if (pillClickTargets == null || pillClickTargets.length < names.length) {

            pillClickTargets = new int[names.length][];

            pillClickTargetY = new int[names.length];

            sliderMinusBtnX = new int[names.length];

            sliderPlusBtnX = new int[names.length];

            sliderBtnYPos = new int[names.length];

        }

        for (int i = 0; i < names.length; i++) {

            pillClickTargets[i] = null;

            sliderMinusBtnX[i] = -1;

            sliderPlusBtnX[i] = -1;

        }

        

        for (int i = 0; i < names.length; i++) {

            // Draw section header if this item starts a new section

            if (sectionHeaders != null && i < sectionHeaders.length && sectionHeaders[i] != null) {

                if (y >= 170 && y <= height - 80) {

                    g.setFont(FONT_EXTRA_SMALL_13);

                    if (i > 0) {

                        g.setColor(ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 120));

                        g.setStroke(RenderCache.getStroke(1));

                        g.drawLine(boxX, y - 14, boxX + boxWidth, y - 14);

                    }

                    g.setColor(ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 220));

                    g.drawString(sectionHeaders[i], boxX + 4, y + 2);

                }

                y += 24;

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

            int textY = boxY + boxHeight / 2 + 6;

            g.drawString(names[i], boxX + 16, textY);

            

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

                int descX = Math.max(10, (width - fm.stringWidth(descriptions[i])) / 2);

                g.drawString(descriptions[i], descX, boxY + boxHeight + 16);

            }

            

            y += itemSpacing;

        }

    }

    

    private void drawPillSelector(Graphics2D g, int settingIndex, int boxX, int boxY, int boxWidth, int boxHeight, String[] options, int selected, boolean isRowSelected) {

        g.setFont(FONT_EXTRA_SMALL_12);

        FontMetrics fm = g.getFontMetrics();

        

        int pillH = pillClickH;

        int pillGap = 3;

        int pillPadding = 12;

        

        // Calculate pill widths

        int[] pillW = new int[options.length];

        int totalW = 0;

        for (int j = 0; j < options.length; j++) {

            pillW[j] = fm.stringWidth(options[j]) + pillPadding * 2;

            totalW += pillW[j];

        }

        totalW += (options.length - 1) * pillGap;

        

        int startX = boxX + boxWidth - totalW - 16;

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

        int rightMargin = 16;

        

        // Layout from right: value text, [+], slider bar, [-]

        g.setFont(FontPalette.get(Font.BOLD, 15));

        FontMetrics fm = g.getFontMetrics();

        int valueW = fm.stringWidth(value) + 12;

        

        int plusX = boxX + boxWidth - rightMargin - valueW - btnSize;

        int sliderEndX = plusX - 8;

        int sliderStartX = boxX + boxWidth / 2 - 20;

        int minusX = sliderStartX - btnSize - 8;

        int sliderWidth = sliderEndX - sliderStartX;

        

        // Store for click detection

        sliderMinusBtnX[settingIndex] = minusX;

        sliderPlusBtnX[settingIndex] = plusX;

        sliderBtnYPos[settingIndex] = centerY - btnSize / 2;

        

        // [-] button

        g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

        g.fillRoundRect(minusX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_EXTRA_SMALL_16);

        fm = g.getFontMetrics();

        g.drawString("\u2212", minusX + (btnSize - fm.stringWidth("\u2212")) / 2, centerY + 6);

        

        // Slider bar

        int sliderH = 6;

        int sliderY = centerY - sliderH / 2;

        g.setColor(ColorPalette.BG_DARK);

        g.fillRoundRect(sliderStartX, sliderY, sliderWidth, sliderH, 3, 3);

        int fillW = (int)(sliderWidth * progress);

        g.setColor(ColorPalette.SUCCESS_GREEN);

        g.fillRoundRect(sliderStartX, sliderY, Math.max(fillW, 3), sliderH, 3, 3);

        

        // Handle

        int handleX = sliderStartX + fillW - 5;

        g.setColor(ColorPalette.TEXT_GOLD);

        g.fillOval(handleX, centerY - 7, 10, 14);

        

        // [+] button

        g.setColor(isSelected ? ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 220) : ColorPalette.withAlpha(ColorPalette.BG_MID, 200));

        g.fillRoundRect(plusX, centerY - btnSize / 2, btnSize, btnSize, 6, 6);

        g.setColor(ColorPalette.TEXT_PRIMARY);

        g.setFont(FONT_EXTRA_SMALL_16);

        fm = g.getFontMetrics();

        g.drawString("+", plusX + (btnSize - fm.stringWidth("+")) / 2, centerY + 6);

        

        // Value text

        g.setFont(FontPalette.get(Font.BOLD, 15));

        g.setColor(ColorPalette.TEXT_PRIMARY);

        fm = g.getFontMetrics();

        g.drawString(value, boxX + boxWidth - rightMargin - fm.stringWidth(value), centerY + 6);

    }

    

    private void drawToggleSwitch(Graphics2D g, int boxX, int boxY, int boxWidth, int boxHeight, boolean isOn, boolean isSelected) {

        int toggleW = 44;

        int toggleH = 22;

        int toggleX = boxX + boxWidth - toggleW - 16;

        int toggleY = boxY + (boxHeight - toggleH) / 2;

        

        // Background

        g.setColor(isOn ? ColorPalette.withAlpha(ColorPalette.SUCCESS_GREEN, 200) : ColorPalette.withAlpha(ColorPalette.BORDER_STEEL, 200));

        g.fillRoundRect(toggleX, toggleY, toggleW, toggleH, 11, 11);

        

        // Circle

        int circleSize = 18;

        int circleX = isOn ? toggleX + toggleW - circleSize - 2 : toggleX + 2;

        int circleY = toggleY + 2;

        g.setColor(ColorPalette.TEXT_WHITE);

        g.fillOval(circleX, circleY, circleSize, circleSize);

        

        // ON/OFF label

        g.setFont(FONT_EXTRA_SMALL_12);

        FontMetrics fm = g.getFontMetrics();

        String label = isOn ? "ON" : "OFF";

        g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_PRIMARY, 180));

        g.drawString(label, toggleX - fm.stringWidth(label) - 8, toggleY + 15);

    }

    

    public void drawDebug(Graphics2D g, int width, int height, double time) {

        // Military themed background

        UITheme.drawScreenBackground(g, width, height, time);

        

        // Title

        UITheme.drawTitle(g, "DEBUG MENU", width, 80, ColorPalette.ACCENT_RED, ColorPalette.ACCENT_ORANGE, time);

        

        g.setColor(new Color(255, 200, 200));

        g.setFont(FONT_INFO);

        String subtitle = "Developer/Cheat Menu - Press Number Keys";

        FontMetrics fm = g.getFontMetrics();

        g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, 120);

        

        // Cheat options

        int startY = 200;

        int spacing = 80;

        g.setFont(FONT_LARGE_32);

        

        String[] options = {

            "[1] Unlock All Levels (1-20)",

            "[2] Give $10,000",

            "[3] Max All Upgrades",

            "[4] Give $1,000",

            "[5] Give $100",

            "[6] Unlock All Active Items",

            "[7] Unlock Risk Contracts",

            "[8] Toggle Showcase Unlock All"

        };

        

        Color[] colors = {

            ColorPalette.ACCENT_YELLOW,  // Gold

            new Color(0, 255, 127),  // Spring green

            new Color(138, 43, 226), // Blue violet

            RenderCache.ORANGE_255_165_0,  // Orange

            new Color(135, 206, 250), // Light sky blue

            ColorPalette.SUCCESS_GREEN, // Green for active items

            new Color(255, 99, 71),  // Tomato red for risk contracts

            new Color(255, 215, 100) // Yellow for showcase unlock

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

            int sfxW = sfxFm.stringWidth(sfxLabel) + 24;

            int sfxH = 36;

            int sfxX = (width - sfxW) / 2;

            int sfxY = 140;

            g.setColor(new Color(0, 0, 0, 160));

            g.fillRoundRect(sfxX, sfxY, sfxW, sfxH, 12, 12);

            g.setColor(RenderCache.RED_255_120_120);

            g.drawString(sfxLabel, sfxX + 12, sfxY + 25);

            g.setComposite(RenderCache.getAlpha(1.0f));

        }



        for (int i = 0; i < options.length; i++) {

            g.setColor(colors[i]);

            fm = g.getFontMetrics();

            int x = (width - fm.stringWidth(options[i])) / 2;

            int y = startY + i * spacing;

            

            // Draw shadow

            g.setColor(RenderCache.BLACK_100);

            g.drawString(options[i], x + 3, y + 3);

            

            // Draw text

            g.setColor(colors[i]);

            g.drawString(options[i], x, y);

            /*

            // Disabled water effects code (had variable conflicts)

            if (y > -60 && y < height + 60) {

                // Motion blur for waves

                g.setColor(new Color(30, 144, 255, 60));

                g.setStroke(RenderCache.getStroke(3));

                for (int wx = 0; wx < width; wx += 40) {

                    g.drawArc(wx, y - 5, 40, 25, 0, 180);

                }

                

                // Waves

                g.setColor(new Color(30, 144, 255, 120));

                g.setStroke(RenderCache.getStroke(3));

                for (int wx2 = 0; wx2 < width; wx2 += 40) {

                    g.drawArc(wx2, y, 40, 20, 0, 180);

                }

                

                // Occasional islands

                if (i % 3 == 0) {

                    int islandX = (i * 137) % (width - 100);

                    g.setColor(new Color(139, 69, 19, 150));

                    g.fillOval(islandX, y + 30, 80, 50);

                    g.setColor(new Color(34, 139, 34, 150));

                    g.fillOval(islandX + 10, y + 25, 30, 30);

                    g.fillOval(islandX + 40, y + 20, 35, 35);

                }

            }

            */

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

        

        // Glow around bright particles — CAPPED for performance
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

        

        // All upgrades now use PassiveUpgradeManager

        // Index 1 = Speed, 2 = Bullet Slow, 3 = Lucky Dodge, 4 = Quick Charge, etc.

        if (passiveUpgradeManager != null && itemIndex >= 1) {

            int upgradeIndex = itemIndex - 1;

            if (upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {

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

            // Create a new vignette image

            cachedVignette = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

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

                    new Color[]{RenderCache.BLACK_0, RenderCache.BLACK_0, new Color(0, 0, 0, 255)}

                );

                vg.setPaint(gradient);

                vg.fillRect(0, 0, width, height);

            }

            

            vg.dispose();

            cachedVignetteWidth = width;

            cachedVignetteHeight = height;

        }

        

        // Simply draw the cached vignette

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

        // Top strip: vertical gradient sb(top) → tr(bottom)
        bakedEdgeTop = new BufferedImage(stripW, gs, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = bakedEdgeTop.createGraphics();
        gt.setPaint(new GradientPaint(0, 0, sb, 0, gs, tr));
        gt.fillRect(0, 0, stripW, gs);
        gt.dispose();

        // Bottom strip: vertical gradient tr(top) → sb(bottom)
        bakedEdgeBottom = new BufferedImage(stripW, gs, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gb = bakedEdgeBottom.createGraphics();
        gb.setPaint(new GradientPaint(0, 0, tr, 0, gs, sb));
        gb.fillRect(0, 0, stripW, gs);
        gb.dispose();

        // Left strip: horizontal gradient sb(left) → tr(right)
        bakedEdgeLeft = new BufferedImage(gs, stripH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gl = bakedEdgeLeft.createGraphics();
        gl.setPaint(new GradientPaint(0, 0, sb, gs, 0, tr));
        gl.fillRect(0, 0, gs, stripH);
        gl.dispose();

        // Right strip: horizontal gradient tr(left) → sb(right)
        bakedEdgeRight = new BufferedImage(gs, stripH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr2 = bakedEdgeRight.createGraphics();
        gr2.setPaint(new GradientPaint(0, 0, tr, gs, 0, sb));
        gr2.fillRect(0, 0, gs, stripH);
        gr2.dispose();

        // Top-left corner: radial gradient, center at inner corner (gs, gs)
        bakedCornerTL = new BufferedImage(gs, gs, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gc1 = bakedCornerTL.createGraphics();
        gc1.setPaint(new java.awt.RadialGradientPaint((float)gs, (float)gs, (float)gs, cd, cc));
        gc1.fillRect(0, 0, gs, gs);
        gc1.dispose();

        // Top-right corner: center at (0, gs)
        bakedCornerTR = new BufferedImage(gs, gs, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gc2 = bakedCornerTR.createGraphics();
        gc2.setPaint(new java.awt.RadialGradientPaint(0f, (float)gs, (float)gs, cd, cc));
        gc2.fillRect(0, 0, gs, gs);
        gc2.dispose();

        // Bottom-left corner: center at (gs, 0)
        bakedCornerBL = new BufferedImage(gs, gs, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gc3 = bakedCornerBL.createGraphics();
        gc3.setPaint(new java.awt.RadialGradientPaint((float)gs, 0f, (float)gs, cd, cc));
        gc3.fillRect(0, 0, gs, gs);
        gc3.dispose();

        // Bottom-right corner: center at (0, 0)
        bakedCornerBR = new BufferedImage(gs, gs, BufferedImage.TYPE_INT_ARGB);
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
                cachedBgGradient = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
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

        // Optional grain effect (drawn live — only 40 tiny rects)
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

    public void configurePauseMenu(boolean isShowcase) {

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

            pauseButtons[2].setText("Main Menu");

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



    /** Lazy-load VFX sprite sheets and slice into frames */

    private void ensureVFXLoaded() {

        if (vfxLoaded) return;

        vfxLoaded = true;

        vfxFrames = new BufferedImage[VFX_FILES.length][];

        for (int i = 0; i < VFX_FILES.length; i++) {

            try {

                BufferedImage sheet = AssetLoader.loadImage(

                    "sprites/Missle Man Assets/VFX/Free/" + VFX_FILES[i] + ".png");

                int rows = sheet.getHeight() / VFX_FRAME_H;

                if (rows < 1) rows = 1;

                vfxFrames[i] = new BufferedImage[rows];

                for (int r = 0; r < rows; r++) {

                    int fy = r * VFX_FRAME_H;

                    int fh = Math.min(VFX_FRAME_H, sheet.getHeight() - fy);

                    vfxFrames[i][r] = sheet.getSubimage(0, fy, sheet.getWidth(), fh);

                }

            } catch (Exception e) {

                vfxFrames[i] = null;

            }

        }

    }



    /** Ensure cinematic starfield is generated */

    private void ensureIntroStars(int width, int height) {

        if (introStars != null) return;

        java.util.Random rng = new java.util.Random(42);

        introStars = new double[300][5];

        for (int i = 0; i < 300; i++) {

            introStars[i][0] = rng.nextDouble() * (width + 200) - 100;

            introStars[i][1] = rng.nextDouble() * (height + 200) - 100;

            introStars[i][2] = 0.5 + rng.nextDouble() * 2.5;

            introStars[i][3] = 0.3 + rng.nextDouble() * 1.7;

            introStars[i][4] = 0.3 + rng.nextDouble() * 0.7;

        }

    }



    /** Draw a VFX frame at the specified position with scaling and rotation */

    private void drawVFXFrame(Graphics2D g, int effectIndex, int frameIndex,

            int x, int y, double scale, double rotation, float alpha) {

        if (vfxFrames == null || effectIndex >= vfxFrames.length || vfxFrames[effectIndex] == null) return;

        BufferedImage[] frames = vfxFrames[effectIndex];

        if (frames.length == 0) return;

        int fi = Math.max(0, Math.min(frameIndex, frames.length - 1));

        BufferedImage frame = frames[fi];

        if (frame == null) return;

        Graphics2D fg = (Graphics2D) g.create();

        fg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        fg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, alpha))));

        fg.translate(x, y);

        fg.rotate(rotation);

        fg.scale(scale, scale);

        fg.drawImage(frame, -frame.getWidth() / 2, -frame.getHeight() / 2, null);

        fg.dispose();

    }



    /** Get animated VFX frame index based on timer */

    private int getVFXFrame(int effectIndex, double timer, double startTime, double duration) {

        if (vfxFrames == null || effectIndex >= vfxFrames.length || vfxFrames[effectIndex] == null) return 0;

        int numFrames = vfxFrames[effectIndex].length;

        double progress = Math.max(0, Math.min(1, (timer - startTime) / duration));

        return Math.min(numFrames - 1, (int)(progress * numFrames));

    }



    /**

     * Draw the anime-style boss intro cinematic.

     * 6 phases: flash -> player spotlight -> slash -> boss reveal -> VS clash -> fade out.

     */

    private void drawBossIntroCinematic(Graphics2D g, int width, int height,

            Player player, Boss boss, String bossIntroText, double bossIntroTimer,

            double bossIntroPlayerX, double bossIntroBossX, double bossIntroVsScale,

            double bossIntroFlash, int bossIntroPhase, java.util.List<Particle> introParticles,

            double time, int level, double bossIntroFlashTimer) {



        // Lazy-load VFX sprites and starfield

        ensureVFXLoaded();

        ensureIntroStars(width, height);



        int M = 250; // shake margin

        int cx = width / 2;

        int cy = height / 2;

        double t = bossIntroTimer;



        // Master alpha: fade in phase 0, fade out phase 5

        float masterAlpha;

        if (bossIntroPhase == 0) masterAlpha = Math.min(1f, (float)(t / 25.0));

        else if (bossIntroPhase == 5) masterAlpha = Math.max(0f, 1f - (float)((t - 330) / 50.0));

        else masterAlpha = 1f;

        masterAlpha = Math.max(0f, Math.min(1f, masterAlpha));



        // Player Y - slides up from bottom during phase 1, flies down off screen in phase 5

        double playerY;

        if (bossIntroPhase < 1) playerY = height + 200;

        else if (bossIntroPhase == 1) {

            double p = Math.min(1.0, (t - 30) / 60.0);

            p = 1.0 - Math.pow(1.0 - p, 3);

            playerY = height + 200 + (cy - (height + 200)) * p;

        } else if (bossIntroPhase == 5) {

            double flyP = Math.min(1.0, (t - 320) / 60.0);

            double flyEase = flyP * flyP * flyP;

            playerY = cy + flyEase * (height + 200 - cy);

        } else playerY = cy;



        // Boss Y - drops from top during phase 3, flies up off screen in phase 5

        double bossDispY;

        if (bossIntroPhase < 3) bossDispY = -250;

        else if (bossIntroPhase == 3) {

            double p = Math.min(1.0, (t - 155) / 60.0);

            p = 1.0 - Math.pow(1.0 - p, 3);

            bossDispY = -250 + (cy - (-250)) * p;

        } else if (bossIntroPhase == 5) {

            double flyP = Math.min(1.0, (t - 320) / 60.0);

            double flyEase = flyP * flyP * flyP;

            bossDispY = cy - flyEase * (cy + 300);

        } else bossDispY = cy;



        // Letterbox bars animation

        double barSlide;

        if (bossIntroPhase < 1) barSlide = Math.min(1.0, t / 20.0);

        else if (bossIntroPhase == 5) barSlide = Math.max(0.0, 1.0 - (t - 330) / 40.0);

        else barSlide = 1.0;

        barSlide = barSlide * barSlide * (3 - 2 * barSlide);

        int barH = (int)(70 * barSlide);

        boolean isPhase5 = (bossIntroPhase == 5);

        // ===== BACKGROUND: deep space =====

        g.setColor(new Color(5, 5, 15, clampA((int)(255 * masterAlpha))));

        g.fillRect(-M, -M, width + M * 2, height + M * 2);



        // Nebula wash

        if (masterAlpha > 0.05f && !isPhase5) {

            RadialGradientPaint neb = new RadialGradientPaint(

                new Point2D.Float(cx, cy), width * 0.8f,

                new float[]{0f, 0.3f, 0.6f, 1f},

                new Color[]{new Color(20, 10, 40, clampA((int)(40 * masterAlpha))),

                            new Color(10, 5, 30, clampA((int)(30 * masterAlpha))),

                            new Color(5, 2, 20, clampA((int)(15 * masterAlpha))),

                            RenderCache.BLACK_0});

            g.setPaint(neb);

            g.fillRect(-M, -M, width + M * 2, height + M * 2);

        }



        // ===== ANIMATED STARFIELD =====

        if (introStars != null && masterAlpha > 0.05f && !isPhase5) {

            int starStep = 1;
            for (int i = 0; i < introStars.length; i += starStep) {

                double sx = introStars[i][0];

                double sy = introStars[i][1] - time * introStars[i][3] * 15;

                sy = ((sy % (height + 200)) + (height + 200)) % (height + 200) - 100;

                double sz = introStars[i][2];

                double sbr = introStars[i][4];

                float twinkle = (float)(sbr * (0.6 + 0.4 * Math.sin(time * (2 + i * 0.1) + i)));

                twinkle = Math.max(0f, Math.min(1f, twinkle * masterAlpha));

                if (twinkle < 0.05f) continue;

                g.setColor(new Color(220, 230, 255, clampA((int)(255 * twinkle))));

                int ssz = (int)Math.ceil(sz);

                g.fillOval((int)sx - ssz/2, (int)sy - ssz/2, ssz, ssz);

                // Larger stars get a cross-shaped glint (skip in phase 5 - invisible through blur)

                if (!isPhase5 && sz > 1.8 && twinkle > 0.3f) {

                    g.setColor(new Color(200, 220, 255, clampA((int)(80 * twinkle))));

                    int gLen = (int)(sz * 3);

                    g.drawLine((int)sx - gLen, (int)sy, (int)sx + gLen, (int)sy);

                    g.drawLine((int)sx, (int)sy - gLen, (int)sx, (int)sy + gLen);

                }

            }

        }



        // ===== RADIAL SPEED LINES (anime power-up) =====

        if (masterAlpha > 0.15f && !isPhase5) {

            Graphics2D slg = (Graphics2D) g.create();

            for (int i = 0; i < 40; i++) {

                double angle = (i * Math.PI * 2.0 / 40) + time * 0.5;

                float sa = (float)(0.04 + 0.03 * Math.sin(time * 4 + i * 0.8));

                sa = Math.max(0f, Math.min(1f, sa * masterAlpha));

                slg.setComposite(RenderCache.getAlpha(sa));

                slg.setColor(new Color(200, 200, 255));

                slg.setStroke(RenderCache.getStroke(1.5f));

                slg.drawLine(cx + (int)(Math.cos(angle) * 120), cy + (int)(Math.sin(angle) * 120),

                             cx + (int)(Math.cos(angle) * 700), cy + (int)(Math.sin(angle) * 700));

            }

            slg.dispose();

        }



        // ===== PHASE 0: IMPACT FLASH =====

        if (bossIntroPhase == 0 && bossIntroFlash > 0) {

            float fAlpha = Math.max(0f, Math.min(1f, (float)bossIntroFlash * masterAlpha));

            g.setColor(new Color(255, 255, 255, clampA((int)(255 * fAlpha))));

            g.fillRect(-M, -M, width + M * 2, height + M * 2);

            // Anamorphic lens flares

            g.setColor(new Color(200, 220, 255, clampA((int)(200 * bossIntroFlash))));

            g.fillRect(-M, cy - 4, width + M * 2, 8);

            // Diagonal cross flares + circular lens ghosts
            {
                Graphics2D flg = (Graphics2D) g.create();
                if (Game.enableAntiAliasing)
                    flg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float flAlpha = Math.max(0f, Math.min(1f, (float)(bossIntroFlash * 0.5)));
                flg.setComposite(RenderCache.getAlpha(flAlpha));
                flg.setStroke(RenderCache.getStroke(3f));
                flg.setColor(new Color(180, 210, 255));
                flg.drawLine(cx - width, cy - (int)(width * 0.12), cx + width, cy + (int)(width * 0.12));
                flg.drawLine(cx - width, cy + (int)(width * 0.12), cx + width, cy - (int)(width * 0.12));
                flg.setColor(new Color(255, 245, 210));
                flg.setStroke(RenderCache.getStroke(1.5f));
                flg.drawLine(cx - width, cy - (int)(width * 0.06), cx + width, cy + (int)(width * 0.06));
                flg.drawLine(cx - width, cy + (int)(width * 0.06), cx + width, cy - (int)(width * 0.06));
                for (int lr = 0; lr < 5; lr++) {
                    int lrX = cx + (int)((lr - 2) * width * 0.16);
                    int lrR = 12 + lr * 7;
                    flg.setColor(new Color(200, 225, 255, clampA((int)(25 * bossIntroFlash))));
                    flg.drawOval(lrX - lrR, cy - lrR, lrR * 2, lrR * 2);
                }
                flg.dispose();
            }

            // VFX explosion overlay (effect 0 = 652.png)

            int flashFrame = getVFXFrame(0, t, 0, 30);

            drawVFXFrame(g, 0, flashFrame, cx, cy, 8.0, time * 0.5, (float)(bossIntroFlash * 0.9));

            // Second VFX layer rotated (effect 3 = 655.png)

            drawVFXFrame(g, 3, flashFrame, cx, cy, 6.0, -time * 0.3 + Math.PI / 4, (float)(bossIntroFlash * 0.6));

        }



        // ===== PLAYER SPRITE + AURA (phases 1+) =====

        if (bossIntroPhase >= 1 && bossIntroPhase <= 5 && player != null) {

            int px = (int)bossIntroPlayerX;

            int py = (int)playerY;

            float auraI = (bossIntroPhase == 1) ? Math.min(1f, (float)((t - 30) / 50.0)) : 1f;

            auraI = Math.max(0f, auraI * masterAlpha);



            // Blue aura (skip in phase 5 - blurred away)

            if (auraI > 0.01f && !isPhase5) {

                float breathe = 0.85f + 0.15f * (float)Math.sin(time * 3);

                float ar = 240 * breathe;

                RadialGradientPaint aura = new RadialGradientPaint(

                    new Point2D.Float(px, py), ar,

                    new float[]{0f, 0.15f, 0.35f, 0.6f, 0.85f, 1f},

                    new Color[]{new Color(130, 220, 255, clampA((int)(90 * auraI))),

                                new Color(80, 180, 255, clampA((int)(65 * auraI))),

                                new Color(50, 140, 255, clampA((int)(45 * auraI))),

                                new Color(30, 100, 220, clampA((int)(25 * auraI))),

                                new Color(15, 50, 150, clampA((int)(10 * auraI))),

                                RenderCache.BLACK_0});

                g.setPaint(aura);

                g.fillOval((int)(px - ar), (int)(py - ar), (int)(ar * 2), (int)(ar * 2));

            }



            // 3 shockwave rings

            if (bossIntroPhase == 1 && t > 40) {

                Graphics2D rg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    rg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                for (int r = 0; r < 3; r++) {

                    double rp = ((t - 40) / 50.0 + r * 0.25) % 1.0;

                    float ra = Math.max(0f, (1f - (float)rp) * 0.5f * masterAlpha);

                    int rr = (int)(20 + rp * 300);

                    rg.setComposite(RenderCache.getAlpha(Math.min(1f, ra)));

                    rg.setColor(RenderCache.BLUE_100_180_255);

                    rg.setStroke(new BasicStroke(Math.max(0.5f, 3f - (float)rp * 2.5f)));

                    rg.drawOval(px - rr, py - rr, rr * 2, rr * 2);

                }

                rg.dispose();

            }

            // Horizontal wind streaks (anime power rush)
            if (bossIntroPhase == 1 && t > 35 && auraI > 0.1f) {
                Graphics2D wsg = (Graphics2D) g.create();
                for (int i = 0; i < 22; i++) {
                    double lineY = py - 220 + i * 20 + Math.sin(time * 3.5 + i) * 12;
                    double lineLen = 50 + Math.abs(Math.sin(time * 5 + i * 1.3)) * 50;
                    float wAlpha = (float)(0.04 + 0.03 * Math.sin(time * 6 + i * 0.8));
                    wAlpha = Math.max(0f, Math.min(1f, wAlpha * auraI));
                    wsg.setComposite(RenderCache.getAlpha(wAlpha));
                    wsg.setColor(new Color(140, 210, 255));
                    wsg.setStroke(new BasicStroke(1.2f + (float)(Math.sin(time * 4 + i) * 0.5)));
                    double drift = (time * (70 + i * 10)) % 500;
                    int sx = (int)(px - 250 + drift);
                    wsg.drawLine(sx, (int)lineY, (int)(sx + lineLen), (int)lineY);
                }
                wsg.dispose();
            }

            // 16 rotating energy rays

            if (auraI > 0.1f && !isPhase5) {

                Graphics2D rayG = (Graphics2D) g.create();

                for (int i = 0; i < 16; i++) {

                    double angle = (i * Math.PI * 2.0 / 16) + time * 1.5;

                    float rAlpha = (float)(0.06 + 0.04 * Math.sin(time * 5 + i * 1.2));

                    rAlpha = Math.max(0f, Math.min(1f, rAlpha * auraI));

                    rayG.setComposite(RenderCache.getAlpha(rAlpha));

                    rayG.setColor(RenderCache.BLUE_120_200_255);

                    rayG.setStroke(RenderCache.getStroke(6f));

                    rayG.drawLine(px + (int)(Math.cos(angle) * 40), py + (int)(Math.sin(angle) * 40),

                                  px + (int)(Math.cos(angle) * 210), py + (int)(Math.sin(angle) * 210));

                }

                rayG.dispose();

            }



            // Ground energy reflection pool under player

            if (auraI > 0.1f && !isPhase5) {

                float grAlpha = auraI * 0.25f * masterAlpha;

                float grBreathe = 0.8f + 0.2f * (float)Math.sin(time * 3.5);

                int grW = (int)(180 * grBreathe);

                int grH = (int)(40 * grBreathe);

                RadialGradientPaint grPool = new RadialGradientPaint(

                    new Point2D.Float(px, py + 130), grW,

                    new float[]{0f, 0.4f, 0.8f, 1f},

                    new Color[]{new Color(100, 200, 255, clampA((int)(80 * grAlpha))),

                                new Color(60, 160, 255, clampA((int)(50 * grAlpha))),

                                new Color(30, 100, 200, clampA((int)(20 * grAlpha))),

                                RenderCache.BLACK_0});

                Graphics2D grg = (Graphics2D) g.create();

                grg.setPaint(grPool);

                grg.scale(1.0, (double)grH / grW);

                int grCy = (int)((py + 130) * ((double)grW / grH));

                grg.fillOval(px - grW, grCy - grW, grW * 2, grW * 2);

                grg.dispose();

            }



            // VFX energy behind player (effect 4 = 662.png, effect 7 = 665.png)

            if (bossIntroPhase >= 1 && bossIntroPhase <= 3 && auraI > 0.15f && !isPhase5) {

                int ef = getVFXFrame(4, t, 30, 80);

                drawVFXFrame(g, 4, ef, px, py, 5.0, time * 0.4, auraI * 0.7f);

                drawVFXFrame(g, 7, ef, px, py, 4.0, -time * 0.6 + Math.PI / 3, auraI * 0.5f);

            }



            // Player sprite - tilted, scaled 6x, with hover bob + afterimage trails
            {
                // Hovering bob animation
                double bob = Math.sin(time * 2.5) * 8 + Math.sin(time * 4.1) * 3;
                double sway = Math.sin(time * 1.8) * 4;
                int drawPx = (int)(px + sway);
                int drawPy = (int)(py + bob);
                // Subtle tilt oscillation
                double tiltOsc = Math.sin(time * 2.0) * 2.0;
                double baseAngle = -15 + tiltOsc;

                // Afterimage trails (3 ghosts trailing behind during movement phases)
                if (bossIntroPhase >= 1 && bossIntroPhase <= 4 && auraI > 0.3f) {
                    for (int ai = 3; ai >= 1; ai--) {
                        float aiAlpha = Math.max(0f, Math.min(1f, (0.12f - ai * 0.03f) * auraI * masterAlpha));
                        double aiDelay = ai * 0.06;
                        double aiBob = Math.sin((time - aiDelay) * 2.5) * 8 + Math.sin((time - aiDelay) * 4.1) * 3;
                        double aiSway = Math.sin((time - aiDelay) * 1.8) * 4;
                        int aiPx = (int)(px + aiSway - ai * 3);
                        int aiPy = (int)(py + aiBob + ai * 2);
                        Graphics2D ag = (Graphics2D) g.create();
                        ag.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        ag.setComposite(RenderCache.getAlpha(aiAlpha));
                        ag.translate(aiPx, aiPy);
                        ag.rotate(Math.toRadians(baseAngle - ai * 0.5));
                        ag.scale(6.0, 6.0);
                        ag.translate(-player.getX(), -player.getY());
                        player.draw(ag);
                        ag.dispose();
                    }
                }

                // Main sprite
                Graphics2D pg = (Graphics2D) g.create();
                pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (Game.enableAntiAliasing)
                    pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                pg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, masterAlpha * auraI))));
                pg.translate(drawPx, drawPy);
                pg.rotate(Math.toRadians(baseAngle));
                pg.scale(6.0, 6.0);
                pg.translate(-player.getX(), -player.getY());
                player.draw(pg);
                pg.dispose();

                // White highlight rim pulse
                if (auraI > 0.3f && !isPhase5) {
                    float rimPulse = 0.15f + 0.1f * (float)Math.sin(time * 5);
                    Graphics2D rimg = (Graphics2D) g.create();
                    rimg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, rimPulse * auraI * masterAlpha))));
                    rimg.translate(drawPx, drawPy);
                    rimg.rotate(Math.toRadians(baseAngle));
                    rimg.scale(6.15, 6.15);
                    rimg.translate(-player.getX(), -player.getY());
                    player.draw(rimg);
                    rimg.dispose();
                }
            }



            // Player name plate "CHALLENGER" - anime style
            if (bossIntroPhase >= 1 && bossIntroPhase <= 4) {
                double ns = (bossIntroPhase == 1) ?
                    Math.min(1.0, Math.max(0, (t - 50) / 40.0)) : 1.0;
                ns = 1.0 - Math.pow(1.0 - ns, 3);
                float na = Math.max(0f, Math.min(1f, (float)ns * masterAlpha));
                if (na > 0.05f) {
                    Graphics2D npg = (Graphics2D) g.create();
                    if (Game.enableAntiAliasing)
                        npg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    String pName = "CHALLENGER";
                    npg.setFont(FontPalette.CINEMATIC_48);
                    FontMetrics fm = npg.getFontMetrics();
                    int nw = fm.stringWidth(pName);
                    int nx = (int)(px - nw / 2 + (1.0 - ns) * -200);
                    int ny = py + 105;
                    int pad = 22;
                    // Skewed parallelogram background panel
                    int skew = 10;
                    Path2D panelBg = new Path2D.Double();
                    panelBg.moveTo(nx - pad + skew, ny - fm.getAscent() - 10);
                    panelBg.lineTo(nx + nw + pad + skew, ny - fm.getAscent() - 10);
                    panelBg.lineTo(nx + nw + pad - skew, ny + 10);
                    panelBg.lineTo(nx - pad - skew, ny + 10);
                    panelBg.closePath();
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na))));
                    GradientPaint panelGrad = new GradientPaint(nx, ny - fm.getAscent() - 10,
                        new Color(5, 20, 50, 230), nx, ny + 10, new Color(10, 30, 70, 200));
                    npg.setPaint(panelGrad);
                    npg.fill(panelBg);
                    // Bright cyan border
                    npg.setStroke(RenderCache.getStroke(2.5f));
                    npg.setColor(new Color(80, 210, 255, clampA((int)(240 * na))));
                    npg.draw(panelBg);
                    // Inner highlight line
                    npg.setStroke(RenderCache.getStroke(1f));
                    npg.setColor(new Color(120, 220, 255, clampA((int)(100 * na))));
                    npg.drawLine(nx - pad + skew + 4, ny - fm.getAscent() - 8,
                                 nx + nw + pad + skew - 4, ny - fm.getAscent() - 8);
                    // Extending horizontal accent lines from corners
                    float lineExt = (float)(ns * 140);
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na * 0.7f))));
                    npg.setStroke(RenderCache.getStroke(2f));
                    npg.setColor(new Color(80, 210, 255));
                    npg.drawLine((int)(nx - pad - skew), ny + 10,
                                 (int)(nx - pad - skew - lineExt), ny + 10);
                    npg.drawLine((int)(nx + nw + pad + skew), ny - fm.getAscent() - 10,
                                 (int)(nx + nw + pad + skew + lineExt), ny - fm.getAscent() - 10);
                    // Diamond end-caps
                    int dSz = 5;
                    int leftEndX = (int)(nx - pad - skew - lineExt);
                    int rightEndX = (int)(nx + nw + pad + skew + lineExt);
                    npg.fillPolygon(new int[]{leftEndX, leftEndX - dSz, leftEndX, leftEndX + dSz},
                        new int[]{ny + 10 - dSz, ny + 10, ny + 10 + dSz, ny + 10}, 4);
                    npg.fillPolygon(new int[]{rightEndX, rightEndX - dSz, rightEndX, rightEndX + dSz},
                        new int[]{ny - fm.getAscent() - 10 - dSz, ny - fm.getAscent() - 10,
                                  ny - fm.getAscent() - 10 + dSz, ny - fm.getAscent() - 10}, 4);
                    // Text drop shadow
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na * 0.5f))));
                    npg.setColor(new Color(0, 40, 90));
                    npg.drawString(pName, nx + 2, ny + 2);
                    // Text outline
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na))));
                    npg.setColor(new Color(0, 20, 60, clampA((int)(255 * na))));
                    for (int ox = -2; ox <= 2; ox++)
                        for (int oy = -2; oy <= 2; oy++)
                            if (ox * ox + oy * oy <= 5)
                                npg.drawString(pName, nx + ox, ny + oy);
                    // Gradient text fill
                    GradientPaint textGrad = new GradientPaint(nx, ny - fm.getAscent(),
                        new Color(230, 248, 255), nx, ny, new Color(100, 215, 255));
                    npg.setPaint(textGrad);
                    npg.drawString(pName, nx, ny);
                    // White highlight pass
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na * 0.45f))));
                    npg.setColor(Color.WHITE);
                    npg.drawString(pName, nx, ny - 1);
                    npg.dispose();
                }
            }



            // Blue energy crackle (lightning arcs)

            if (bossIntroPhase == 1 && t > 45 && auraI > 0.3f) {

                Graphics2D lg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                lg.setStroke(RenderCache.getStroke(2f));

                for (int c = 0; c < 6; c++) {

                    double ba = (c * Math.PI * 2.0 / 6) + time * 2;

                    double lx = px + Math.cos(ba) * 25;

                    double ly = py + Math.sin(ba) * 25;

                    for (int s = 0; s < 4; s++) {

                        double nx = lx + Math.cos(ba + Math.sin(time * 10 + c + s) * 0.8) * (15 + s * 12);

                        double ny = ly + Math.sin(ba + Math.cos(time * 8 + c + s) * 0.8) * (15 + s * 12);

                        float ca = Math.max(0f, Math.min(1f, (0.5f - s * 0.1f) * masterAlpha));

                        lg.setComposite(RenderCache.getAlpha(ca));

                        lg.setColor(new Color(180, 230, 255));

                        lg.drawLine((int)lx, (int)ly, (int)nx, (int)ny);

                        lx = nx; ly = ny;

                    }

                }

                lg.dispose();

            }

        }



        // ===== PHASE 2: SLASH TRANSITION =====

        if (bossIntroPhase == 2 || (bossIntroPhase == 3 && t < 175)) {

            double sp;

            float sa;

            if (bossIntroPhase == 2) { sp = bossIntroFlash; sa = masterAlpha; }

            else { sp = 1.0; sa = Math.max(0f, 1.0f - (float)((t - 155) / 20.0)); }

            if (sa > 0.01f) {

                Graphics2D sg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int sx1 = (int)(width * (1.0 - sp * 0.3));

                int sy1 = (int)(-50 + sp * 50);

                int sx2 = (int)(width * (0.7 - sp * 0.7));

                int sy2 = (int)(height * (0.3 + sp * 0.7));

                for (int layer = 5; layer >= 0; layer--) {

                    float la = Math.max(0f, Math.min(1f, (0.05f + layer * 0.03f) * sa));

                    sg.setComposite(RenderCache.getAlpha(la));

                    sg.setColor(layer < 2 ? Color.WHITE :

                                layer < 4 ? new Color(255, 220, 120) : new Color(255, 180, 60));

                    sg.setStroke(new BasicStroke(2 + layer * 8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    sg.drawLine(sx1, sy1, sx2, sy2);

                }

                int sparkCount = 12;

                for (int i = 0; i < sparkCount; i++) {

                    double spp = (i + 0.5) / sparkCount;

                    if (spp > sp) break;

                    int skx = (int)(sx1 + (sx2 - sx1) * spp + Math.sin(time * 15 + i) * 8);

                    int sky = (int)(sy1 + (sy2 - sy1) * spp + Math.cos(time * 12 + i) * 8);

                    int ss = Math.max(1, 3 + (int)(Math.sin(time * 8 + i * 2) * 2));

                    sg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, 0.8f * sa))));

                    sg.setColor(RenderCache.CREAM_255_255_220);

                    sg.fillOval(skx - ss, sky - ss, ss * 2, ss * 2);

                }

                sg.dispose();



                // VFX effect on slash line (effect 6 = 664.png)

                int slFrame = getVFXFrame(6, t, 120, 35);

                int slMidX = (sx1 + sx2) / 2;

                int slMidY = (sy1 + sy2) / 2;

                double slAngle = Math.atan2(sy2 - sy1, sx2 - sx1);

                drawVFXFrame(g, 6, slFrame, slMidX, slMidY, 4.0, slAngle, sa * 0.6f);

            }

        }



        // ===== BOSS SPRITE + AURA (phases 3+) =====

        if (bossIntroPhase >= 3 && (bossIntroPhase == 5 || bossDispY > -200)) {

            int bx = (int)bossIntroBossX;

            int by = (int)bossDispY;

            float ba = (bossIntroPhase == 3) ? Math.min(1f, (float)((t - 155) / 50.0)) : 1f;

            ba = Math.max(0f, ba * masterAlpha);



            // Menacing red aura (skip in phase 5 - blurred away)

            if (ba > 0.01f && !isPhase5) {

                float breath = 0.8f + 0.2f * (float)Math.sin(time * 3);

                float ar = 260 * breath;

                RadialGradientPaint bap = new RadialGradientPaint(

                    new Point2D.Float(bx, by), ar,

                    new float[]{0f, 0.15f, 0.35f, 0.55f, 0.75f, 1f},

                    new Color[]{

                        new Color(255, 200, 150, clampA((int)(60 * ba))),

                        new Color(255, 100, 50, clampA((int)(50 * ba))),

                        new Color(200, 50, 20, clampA((int)(40 * ba))),

                        new Color(150, 20, 10, clampA((int)(25 * ba))),

                        new Color(80, 10, 5, clampA((int)(12 * ba))),

                        RenderCache.BLACK_0});

                g.setPaint(bap);

                g.fillOval((int)(bx - ar), (int)(by - ar), (int)(ar * 2), (int)(ar * 2));

            }



            // 22 energy spikes (rotating opposite)

            if (ba > 0.2f && !isPhase5) {

                Graphics2D spG = (Graphics2D) g.create();

                for (int i = 0; i < 22; i++) {

                    double angle = (i * Math.PI * 2.0 / 22) - time * 1.2;

                    float sAlpha = (float)(0.08 + 0.05 * Math.sin(time * 4 + i * 0.9));

                    sAlpha = Math.max(0f, Math.min(1f, sAlpha * ba));

                    spG.setComposite(RenderCache.getAlpha(sAlpha));

                    spG.setColor(new Color(255, 120, 40));

                    spG.setStroke(RenderCache.getStroke(10f));

                    spG.drawLine(bx + (int)(Math.cos(angle) * 50), by + (int)(Math.sin(angle) * 50),

                                 bx + (int)(Math.cos(angle) * 280), by + (int)(Math.sin(angle) * 280));

                    spG.setColor(PANEL_MEGA_LABEL);

                    spG.setStroke(RenderCache.getStroke(3f));

                    spG.drawLine(bx + (int)(Math.cos(angle) * 50), by + (int)(Math.sin(angle) * 50),

                                 bx + (int)(Math.cos(angle) * 230), by + (int)(Math.sin(angle) * 230));

                }

                spG.dispose();

            }



            // Impact shockwave on boss arrival

            if (bossIntroPhase == 3 && t > 180 && t < 220) {

                Graphics2D rg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    rg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                for (int r = 0; r < 3; r++) {

                    double rp = Math.min(1.0, ((t - 180) / 40.0 + r * 0.15) % 1.0);

                    float ra = Math.max(0f, (1f - (float)rp) * 0.6f * masterAlpha);

                    int rr = (int)(30 + rp * 350);

                    rg.setComposite(RenderCache.getAlpha(Math.min(1f, ra)));

                    rg.setColor(RenderCache.WARM_255_150_80);

                    rg.setStroke(new BasicStroke(Math.max(0.5f, 4f - (float)rp * 3f)));

                    rg.drawOval(bx - rr, by - rr, rr * 2, rr * 2);

                }

                rg.dispose();

            }

            // Wind streaks (boss power entrance)
            if (bossIntroPhase == 3 && t > 160 && ba > 0.1f) {
                Graphics2D wsg = (Graphics2D) g.create();
                for (int i = 0; i < 24; i++) {
                    double lineY = by - 250 + i * 21 + Math.sin(time * 2.8 + i * 0.7) * 14;
                    double lineLen = 55 + Math.abs(Math.sin(time * 4.5 + i * 1.5)) * 55;
                    float wAlpha = (float)(0.05 + 0.04 * Math.sin(time * 5 + i * 1.1));
                    wAlpha = Math.max(0f, Math.min(1f, wAlpha * ba));
                    wsg.setComposite(RenderCache.getAlpha(wAlpha));
                    wsg.setColor(RenderCache.WARM_255_150_60);
                    wsg.setStroke(new BasicStroke(1.3f + (float)(Math.sin(time * 3.5 + i) * 0.6)));
                    double drift = (time * (75 + i * 11)) % 520;
                    int sx = (int)(bx - 260 + drift);
                    wsg.drawLine(sx, (int)lineY, (int)(sx + lineLen), (int)lineY);
                }
                wsg.dispose();
            }



            // Ground energy reflection pool under boss

            if (ba > 0.1f && !isPhase5) {

                float grAlpha = ba * 0.2f * masterAlpha;

                float grBreathe = 0.8f + 0.2f * (float)Math.sin(time * 2.8);

                int grW = (int)(200 * grBreathe);

                int grH = (int)(45 * grBreathe);

                RadialGradientPaint grPool = new RadialGradientPaint(

                    new Point2D.Float(bx, by + 140), grW,

                    new float[]{0f, 0.4f, 0.8f, 1f},

                    new Color[]{new Color(255, 140, 40, clampA((int)(70 * grAlpha))),

                                new Color(255, 80, 20, clampA((int)(45 * grAlpha))),

                                new Color(200, 40, 10, clampA((int)(18 * grAlpha))),

                                RenderCache.BLACK_0});

                Graphics2D grg = (Graphics2D) g.create();

                grg.setPaint(grPool);

                grg.scale(1.0, (double)grH / grW);

                int grCy = (int)((by + 140) * ((double)grW / grH));

                grg.fillOval(bx - grW, grCy - grW, grW * 2, grW * 2);

                grg.dispose();

            }



            // VFX fire behind boss (effect 8 = 672.png, effect 9 = 673.png)

            if (bossIntroPhase >= 3 && ba > 0.15f && !isPhase5) {

                int ef = getVFXFrame(8, t, 155, 95);

                drawVFXFrame(g, 8, ef, bx, by, 6.0, -time * 0.3, ba * 0.7f);

                drawVFXFrame(g, 9, ef, bx, by, 4.5, time * 0.5 + Math.PI / 6, ba * 0.5f);

            }



            // Boss sprite - tilted, scaled 5x, with hover bob + afterimage trails
            {
                // Menacing hover animation (slower, heavier feel than player)
                double bBob = Math.sin(time * 1.8 + Math.PI) * 10 + Math.sin(time * 3.3) * 4;
                double bSway = Math.sin(time * 1.3 + 0.5) * 5;
                int drawBx = (int)(bx + bSway);
                int drawBy = (int)(by + bBob);
                double bTiltOsc = Math.sin(time * 1.5 + Math.PI) * 2.5;
                double bBaseAngle = 15 + bTiltOsc;

                // Afterimage trails (red-tinted ghosts)
                if (bossIntroPhase >= 3 && bossIntroPhase <= 4 && ba > 0.3f) {
                    for (int ai = 3; ai >= 1; ai--) {
                        float aiAlpha = Math.max(0f, Math.min(1f, (0.14f - ai * 0.035f) * ba * masterAlpha));
                        double aiDelay = ai * 0.07;
                        double aiBob = Math.sin((time - aiDelay) * 1.8 + Math.PI) * 10 + Math.sin((time - aiDelay) * 3.3) * 4;
                        double aiSway = Math.sin((time - aiDelay) * 1.3 + 0.5) * 5;
                        int aiPx = (int)(bx + aiSway + ai * 3);
                        int aiPy = (int)(by + aiBob + ai * 2);
                        Graphics2D ag = (Graphics2D) g.create();
                        ag.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        ag.setComposite(RenderCache.getAlpha(aiAlpha));
                        ag.translate(aiPx, aiPy);
                        ag.rotate(Math.toRadians(bBaseAngle + ai * 0.5));
                        ag.scale(5.0, 5.0);
                        ag.translate(-boss.getX(), -boss.getY());
                        boss.draw(ag);
                        ag.dispose();
                    }
                }

                // Main sprite
                Graphics2D bg = (Graphics2D) g.create();
                bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (Game.enableAntiAliasing)
                    bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                bg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, masterAlpha * ba))));
                bg.translate(drawBx, drawBy);
                bg.rotate(Math.toRadians(bBaseAngle));
                bg.scale(5.0, 5.0);
                bg.translate(-boss.getX(), -boss.getY());
                boss.draw(bg);
                bg.dispose();

                // Red rim highlight pulse
                if (ba > 0.3f && !isPhase5) {
                    float rimPulse = 0.12f + 0.08f * (float)Math.sin(time * 4);
                    Graphics2D rimg = (Graphics2D) g.create();
                    rimg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, rimPulse * ba * masterAlpha))));
                    rimg.translate(drawBx, drawBy);
                    rimg.rotate(Math.toRadians(bBaseAngle));
                    rimg.scale(5.15, 5.15);
                    rimg.translate(-boss.getX(), -boss.getY());
                    boss.draw(rimg);
                    rimg.dispose();
                }
            }



            // Boss name plate - anime style
            if (bossIntroPhase >= 3 && bossIntroPhase <= 4) {
                double ns = (bossIntroPhase == 3) ?
                    Math.min(1.0, Math.max(0, (t - 175) / 40.0)) : 1.0;
                ns = 1.0 - Math.pow(1.0 - ns, 3);
                float na = Math.max(0f, Math.min(1f, (float)ns * masterAlpha));
                if (na > 0.05f) {
                    Graphics2D npg = (Graphics2D) g.create();
                    if (Game.enableAntiAliasing)
                        npg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int fs = bossIntroText.length() > 14 ? 38 : bossIntroText.length() > 10 ? 44 : 52;
                    npg.setFont(FontPalette.get(Font.BOLD, fs));
                    FontMetrics fm = npg.getFontMetrics();
                    int nw = fm.stringWidth(bossIntroText);
                    int nx = (int)(bx - nw / 2 + (1.0 - ns) * 200);
                    int ny = by + 120;
                    int pad = 22;
                    // Skewed parallelogram panel (opposite skew from player)
                    int skew = 10;
                    Path2D panelBg = new Path2D.Double();
                    panelBg.moveTo(nx - pad - skew, ny - fm.getAscent() - 10);
                    panelBg.lineTo(nx + nw + pad - skew, ny - fm.getAscent() - 10);
                    panelBg.lineTo(nx + nw + pad + skew, ny + 10);
                    panelBg.lineTo(nx - pad + skew, ny + 10);
                    panelBg.closePath();
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na))));
                    GradientPaint panelGrad = new GradientPaint(nx, ny - fm.getAscent() - 10,
                        new Color(50, 10, 5, 230), nx, ny + 10, new Color(70, 15, 10, 200));
                    npg.setPaint(panelGrad);
                    npg.fill(panelBg);
                    // Red-orange border
                    npg.setStroke(RenderCache.getStroke(2.5f));
                    npg.setColor(new Color(255, 130, 40, clampA((int)(240 * na))));
                    npg.draw(panelBg);
                    // Inner highlight line
                    npg.setStroke(RenderCache.getStroke(1f));
                    npg.setColor(new Color(255, 180, 80, clampA((int)(100 * na))));
                    npg.drawLine(nx - pad - skew + 4, ny - fm.getAscent() - 8,
                                 nx + nw + pad - skew - 4, ny - fm.getAscent() - 8);
                    // Extending horizontal accent lines
                    float lineExt = (float)(ns * 140);
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na * 0.7f))));
                    npg.setStroke(RenderCache.getStroke(2f));
                    npg.setColor(new Color(255, 130, 40));
                    npg.drawLine((int)(nx - pad - skew), ny - fm.getAscent() - 10,
                                 (int)(nx - pad - skew - lineExt), ny - fm.getAscent() - 10);
                    npg.drawLine((int)(nx + nw + pad + skew), ny + 10,
                                 (int)(nx + nw + pad + skew + lineExt), ny + 10);
                    // Diamond end-caps
                    int dSz = 5;
                    int leftEndX = (int)(nx - pad - skew - lineExt);
                    int rightEndX = (int)(nx + nw + pad + skew + lineExt);
                    npg.fillPolygon(new int[]{leftEndX, leftEndX - dSz, leftEndX, leftEndX + dSz},
                        new int[]{ny - fm.getAscent() - 10 - dSz, ny - fm.getAscent() - 10,
                                  ny - fm.getAscent() - 10 + dSz, ny - fm.getAscent() - 10}, 4);
                    npg.fillPolygon(new int[]{rightEndX, rightEndX - dSz, rightEndX, rightEndX + dSz},
                        new int[]{ny + 10 - dSz, ny + 10, ny + 10 + dSz, ny + 10}, 4);
                    // "WARNING" subtitle above boss name
                    npg.setFont(FontPalette.get(Font.PLAIN, 16));
                    FontMetrics sfm = npg.getFontMetrics();
                    String subtitle = "WARNING";
                    int subW = sfm.stringWidth(subtitle);
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na * 0.6f))));
                    npg.setColor(PANEL_MEGA_LABEL);
                    npg.drawString(subtitle, nx + nw / 2 - subW / 2, ny - fm.getAscent() - 18);
                    // Text drop shadow
                    npg.setFont(FontPalette.get(Font.BOLD, fs));
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na * 0.5f))));
                    npg.setColor(new Color(100, 20, 0));
                    npg.drawString(bossIntroText, nx + 2, ny + 2);
                    // Text outline
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na))));
                    npg.setColor(new Color(60, 10, 0, clampA((int)(255 * na))));
                    for (int ox = -2; ox <= 2; ox++)
                        for (int oy = -2; oy <= 2; oy++)
                            if (ox * ox + oy * oy <= 5)
                                npg.drawString(bossIntroText, nx + ox, ny + oy);
                    // Gradient text fill
                    GradientPaint textGrad = new GradientPaint(nx, ny - fm.getAscent(),
                        new Color(255, 235, 180), nx, ny, RenderCache.WARM_255_150_60);
                    npg.setPaint(textGrad);
                    npg.drawString(bossIntroText, nx, ny);
                    // White highlight pass
                    npg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, na * 0.4f))));
                    npg.setColor(Color.WHITE);
                    npg.drawString(bossIntroText, nx, ny - 1);
                    npg.dispose();
                }
            }



            // Red lightning crackle

            if (bossIntroPhase == 3 && t > 185 && ba > 0.3f) {

                Graphics2D lg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                lg.setStroke(RenderCache.getStroke(2.5f));

                for (int c = 0; c < 8; c++) {

                    double baseA = (c * Math.PI * 2.0 / 8) - time * 2.5;

                    double lx = bx + Math.cos(baseA) * 30;

                    double ly = by + Math.sin(baseA) * 30;

                    for (int s = 0; s < 5; s++) {

                        double nx = lx + Math.cos(baseA + Math.sin(time * 12 + c + s) * 0.9) * (18 + s * 15);

                        double ny = ly + Math.sin(baseA + Math.cos(time * 9 + c + s) * 0.9) * (18 + s * 15);

                        float ca = Math.max(0f, Math.min(1f, (0.4f - s * 0.07f) * masterAlpha));

                        lg.setComposite(RenderCache.getAlpha(ca));

                        lg.setColor(RenderCache.WARM_255_160_80);

                        lg.drawLine((int)lx, (int)ly, (int)nx, (int)ny);

                        lx = nx; ly = ny;

                    }

                }

                lg.dispose();

            }

        }



        // ===== PHASE 4: VS CLASH =====

        if (bossIntroPhase >= 4 && bossIntroVsScale > 0.05 && !isPhase5) {

            // Energy beam connection between player and boss
            int pDrawX = (int)bossIntroPlayerX;
            int bDrawX = (int)bossIntroBossX;
            if (pDrawX > 0 && bDrawX < width) {
                Graphics2D ebg = (Graphics2D) g.create();
                if (Game.enableAntiAliasing)
                    ebg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float beamAlpha = Math.max(0f, Math.min(1f, (float)(bossIntroVsScale * 0.35 * masterAlpha)));
                // Pulsing energy strands between the two fighters
                for (int strand = 0; strand < 5; strand++) {
                    float sAlpha = Math.max(0f, Math.min(1f, beamAlpha * (0.6f - strand * 0.1f)));
                    ebg.setComposite(RenderCache.getAlpha(sAlpha));
                    Path2D beam = new Path2D.Double();
                    beam.moveTo(pDrawX + 80, cy);
                    // Wavy control points
                    double wave1 = Math.sin(time * 6 + strand * 1.5) * (30 + strand * 15);
                    double wave2 = Math.cos(time * 5 + strand * 2.0) * (25 + strand * 12);
                    double midX = (pDrawX + bDrawX) / 2.0;
                    beam.curveTo(midX - 80, cy + wave1, midX + 80, cy + wave2, bDrawX - 80, cy);
                    ebg.setStroke(new BasicStroke(4f - strand * 0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    // Blue near player, red near boss, white in middle
                    if (strand < 2)
                        ebg.setColor(new Color(150, 220, 255));
                    else if (strand > 3)
                        ebg.setColor(RenderCache.WARM_255_160_80);
                    else
                        ebg.setColor(RenderCache.CREAM_255_255_220);
                    ebg.draw(beam);
                }
                ebg.dispose();
            }

            // Expanding shockwave rings (alternating blue/red)

            if (bossIntroPhase == 4 && t < 275) {

                Graphics2D rg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    rg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                double shockP = (t - 250) / 25.0;

                for (int r = 0; r < 5; r++) {

                    double rp = Math.min(1.0, shockP + r * 0.12);

                    if (rp < 0) continue;

                    float ra = Math.max(0f, (1f - (float)rp) * 0.7f * masterAlpha);

                    int rr = (int)(20 + rp * 500);

                    rg.setComposite(RenderCache.getAlpha(Math.min(1f, ra)));

                    rg.setColor(r % 2 == 0 ? RenderCache.BLUE_120_200_255 : RenderCache.WARM_255_150_80);

                    rg.setStroke(new BasicStroke(Math.max(0.5f, 5f - (float)rp * 4f)));

                    rg.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);

                }

                rg.dispose();

            }



            // 12 rotating cross-flare arms (alternating blue/red)

            {

                Graphics2D fg = (Graphics2D) g.create();

                for (int i = 0; i < 12; i++) {

                    double angle = (i * Math.PI * 2.0 / 12) + time * 0.5;

                    float aa = (float)(0.1 + 0.06 * Math.sin(time * 3 + i));

                    aa = Math.max(0f, Math.min(1f, aa * (float)bossIntroVsScale * masterAlpha));

                    fg.setComposite(RenderCache.getAlpha(aa));

                    fg.setColor(i % 2 == 0 ? new Color(80, 160, 255) : new Color(255, 100, 40));

                    fg.setStroke(RenderCache.getStroke(18f));

                    fg.drawLine(cx + (int)(Math.cos(angle) * 20), cy + (int)(Math.sin(angle) * 20),

                                cx + (int)(Math.cos(angle) * 200), cy + (int)(Math.sin(angle) * 200));

                    fg.setColor(Color.WHITE);

                    fg.setStroke(RenderCache.getStroke(5f));

                    fg.drawLine(cx + (int)(Math.cos(angle) * 20), cy + (int)(Math.sin(angle) * 20),

                                cx + (int)(Math.cos(angle) * 160), cy + (int)(Math.sin(angle) * 160));

                }

                fg.dispose();

            }



            // Manga impact lines (black wedge shapes radiating from center)

            {

                Graphics2D mg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                float impactA = Math.max(0f, Math.min(1f, (float)bossIntroVsScale * 0.4f * masterAlpha));

                mg.setComposite(RenderCache.getAlpha(impactA));

                int numWedges = 24;

                double maxR = Math.max(width, height) * 0.9;

                double innerR = 180;

                for (int i = 0; i < numWedges; i++) {

                    double angle = (i * Math.PI * 2.0 / numWedges);

                    double halfWidth = Math.PI / numWedges * 0.35;

                    Path2D wedge = new Path2D.Double();

                    wedge.moveTo(cx + Math.cos(angle - halfWidth) * innerR, cy + Math.sin(angle - halfWidth) * innerR);

                    wedge.lineTo(cx + Math.cos(angle - halfWidth * 0.3) * maxR, cy + Math.sin(angle - halfWidth * 0.3) * maxR);

                    wedge.lineTo(cx + Math.cos(angle + halfWidth * 0.3) * maxR, cy + Math.sin(angle + halfWidth * 0.3) * maxR);

                    wedge.lineTo(cx + Math.cos(angle + halfWidth) * innerR, cy + Math.sin(angle + halfWidth) * innerR);

                    wedge.closePath();

                    mg.setColor(i % 2 == 0 ? Color.BLACK : Color.WHITE);

                    mg.fill(wedge);

                }

                mg.dispose();

            }



            // VFX explosion at VS center (effect 2 = 654.png, effect 11 = 675.png)

            {

                float vfxA = Math.max(0f, Math.min(1f, (float)bossIntroVsScale * masterAlpha * 0.8f));

                int ef = getVFXFrame(2, t, 250, 80);

                drawVFXFrame(g, 2, ef, cx, cy, 10.0, time * 0.2, vfxA);

                drawVFXFrame(g, 11, ef, cx, cy, 8.0, -time * 0.4 + Math.PI / 3, vfxA * 0.7f);

            }



            // Radial energy explosion from center

            float expA = Math.max(0f, Math.min(1f, (float)bossIntroVsScale * 0.3f * masterAlpha));

            if (expA > 0.01f) {

                RadialGradientPaint expl = new RadialGradientPaint(

                    new Point2D.Float(cx, cy), 200,

                    new float[]{0f, 0.3f, 0.7f, 1f},

                    new Color[]{

                        new Color(255, 255, 255, clampA((int)(120 * expA * bossIntroVsScale))),

                        new Color(255, 220, 100, clampA((int)(80 * expA * bossIntroVsScale))),

                        new Color(255, 150, 50, clampA((int)(30 * expA * bossIntroVsScale))),

                        RenderCache.BLACK_0});

                g.setPaint(expl);

                g.fillOval(cx - 200, cy - 200, 400, 400);

            }



            // VS TEXT with chromatic aberration + glow

            {

                Graphics2D vg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    vg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                vg.setFont(FontPalette.get(Font.BOLD, 200));

                FontMetrics fm = vg.getFontMetrics();

                String vsText = "VS";

                int vsW = fm.stringWidth(vsText);

                int vsX = cx - vsW / 2;

                int vsY = cy + fm.getAscent() / 2 - fm.getDescent();

                float va = Math.max(0f, Math.min(1f, (float)bossIntroVsScale * masterAlpha));



                // Layered shadow

                for (int s = 10; s >= 1; s--) {

                    vg.setColor(new Color(0, 0, 0, clampA((int)(15 * va))));

                    vg.drawString(vsText, vsX + s * 2, vsY + s * 2);

                }



                // Multi-pass glow (draw at increasing scales with decreasing alpha)

                for (int gl = 4; gl >= 1; gl--) {

                    float glAlpha = va * 0.12f / gl;

                    if (glAlpha < 0.01f) continue;

                    Graphics2D gg = (Graphics2D) vg.create();

                    gg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, glAlpha))));

                    float glScale = 1.0f + gl * 0.08f;

                    gg.translate(cx, vsY - fm.getAscent() / 2);

                    gg.scale(glScale, glScale);

                    gg.translate(-cx, -(vsY - fm.getAscent() / 2));

                    gg.setColor(PANEL_MEGA_LABEL);

                    gg.drawString(vsText, vsX, vsY);

                    gg.dispose();

                }



                // Dark outline

                vg.setColor(new Color(40, 20, 10, clampA((int)(255 * va))));

                for (int ox = -3; ox <= 3; ox++) {

                    for (int oy = -3; oy <= 3; oy++) {

                        if (ox * ox + oy * oy <= 10)

                            vg.drawString(vsText, vsX + ox, vsY + oy);

                    }

                }



                // Chromatic aberration - red channel offset left, blue offset right

                if (va > 0.1f) {

                    int chromaOff = (int)(3 * bossIntroVsScale);

                    Graphics2D cr = (Graphics2D) vg.create();

                    cr.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, va * 0.4f))));

                    cr.setColor(new Color(255, 60, 60));

                    cr.drawString(vsText, vsX - chromaOff, vsY);

                    cr.setColor(new Color(60, 60, 255));

                    cr.drawString(vsText, vsX + chromaOff, vsY);

                    cr.dispose();

                }



                // Fire gradient fill (main text)

                if (va > 0.1f) {

                    GradientPaint vsp = new GradientPaint(vsX, vsY - fm.getAscent(),

                        new Color(255, 255, 240), vsX, vsY, new Color(255, 150, 30));

                    vg.setPaint(vsp);

                    vg.drawString(vsText, vsX, vsY);

                    vg.setColor(new Color(255, 255, 255, clampA((int)(200 * va))));

                    vg.drawString(vsText, vsX, vsY - 2);

                }

                vg.dispose();

            }

        }



        // ===== VS FLASH OVERLAY =====

        if (bossIntroFlash > 0 && bossIntroPhase == 4) {

            g.setColor(new Color(255, 255, 255, clampA((int)(200 * bossIntroFlash * masterAlpha))));

            g.fillRect(-M, -M, width + M * 2, height + M * 2);

        }



        // ===== INTRO PARTICLES (thread-safe snapshot) =====

        if (!isPhase5) try {

            java.util.List<Particle> pCopy = new java.util.ArrayList<>(introParticles);

            for (Particle p : pCopy) {

                if (p != null && p.isAlive()) {

                    Graphics2D pg = (Graphics2D) g.create();

                    pg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, masterAlpha))));

                    p.draw(pg);

                    pg.dispose();

                }

            }

        } catch (Exception e) { /* thread safety */ }



        // ===== LETTERBOX BARS =====

        if (barH > 2) {

            GradientPaint topBar = new GradientPaint(0, -M, Color.BLACK, 0, barH, new Color(15, 18, 30));

            g.setPaint(topBar);

            g.fillRect(-M, -M, width + M * 2, barH + M);

            GradientPaint botBar = new GradientPaint(0, height - barH, new Color(15, 18, 30), 0, height + M, Color.BLACK);

            g.setPaint(botBar);

            g.fillRect(-M, height - barH, width + M * 2, barH + M);



            // Gold trim

            int trimA = clampA((int)(180 + 40 * Math.sin(time * 3)));

            g.setColor(new Color(235, 203, 139, trimA));

            g.setStroke(RenderCache.getStroke(2.5f));

            g.drawLine(-M, barH, width + M, barH);

            g.drawLine(-M, height - barH, width + M, height - barH);



            // Stage banner

            if (bossIntroPhase >= 3 && barH > 30) {

                String stageText = "STAGE " + level;

                g.setFont(FontPalette.getBody(Font.BOLD, 28));

                FontMetrics fm = g.getFontMetrics();

                int stW = fm.stringWidth(stageText);

                int stX = cx - stW / 2;

                int stY = height - barH / 2 + fm.getAscent() / 2 - 3;

                int stPad = 20;

                g.setColor(new Color(20, 22, 35, 200));

                g.fillRect(stX - stPad, stY - fm.getAscent() - 3, stW + stPad * 2, fm.getHeight() + 6);

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_GOLD, 160));

                g.setStroke(RenderCache.getStroke(1.5f));

                g.drawRect(stX - stPad, stY - fm.getAscent() - 3, stW + stPad * 2, fm.getHeight() + 6);

                int dSize = 6;

                int dY = stY - fm.getAscent() / 2;

                g.setColor(ColorPalette.withAlpha(ColorPalette.TEXT_GOLD, 200));

                g.fillPolygon(new int[]{stX - stPad - 12, stX - stPad - 12 + dSize, stX - stPad - 12, stX - stPad - 12 - dSize},

                              new int[]{dY - dSize, dY, dY + dSize, dY}, 4);

                g.fillPolygon(new int[]{stX + stW + stPad + 12, stX + stW + stPad + 12 + dSize, stX + stW + stPad + 12, stX + stW + stPad + 12 - dSize},

                              new int[]{dY - dSize, dY, dY + dSize, dY}, 4);

                GradientPaint stp = new GradientPaint(stX, stY - fm.getAscent(),

                    RenderCache.WARM_255_240_200, stX, stY, ColorPalette.TEXT_GOLD);

                g.setPaint(stp);

                g.drawString(stageText, stX, stY);

            }



            // Skip prompt

            if (bossIntroPhase >= 1 && barH > 20) {

                float skipPulse = 0.5f + 0.5f * (float)Math.sin(time * 3);

                g.setFont(FontPalette.get(Font.BOLD, 14));

                g.setColor(new Color(216, 222, 233, clampA((int)(180 * skipPulse * masterAlpha))));

                Object[] skipSegs = {"PRESS ", KeyBindManager.Action.CONFIRM, " TO SKIP"};

                int skipW = measurePromptWidth(g, skipSegs);

                drawPromptWithIcons(g, width - 20 - skipW / 2, barH / 2 + g.getFontMetrics().getAscent() / 2 - 2, skipSegs);

            }

        }



        // ===== ANIME SCANLINE OVERLAY =====
        if (masterAlpha > 0.2f && bossIntroPhase >= 1 && !isPhase5) {
            Graphics2D slg2 = (Graphics2D) g.create();
            float scanAlpha = Math.max(0f, Math.min(1f, 0.04f * masterAlpha));
            slg2.setComposite(RenderCache.getAlpha(scanAlpha));
            slg2.setColor(Color.BLACK);
            // Draw horizontal scanlines every 4 pixels
            for (int sy = -M; sy < height + M; sy += 4) {
                slg2.drawLine(-M, sy, width + M, sy);
            }
            slg2.dispose();
        }

        // ===== CORNER BRACKETS (phases 3+) =====

        if (bossIntroPhase >= 3 && bossIntroPhase <= 4 && barH > 30) {

            Graphics2D cg = (Graphics2D) g.create();

            if (Game.enableAntiAliasing)

                cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int bSize = 40, inset = 18;

            int bAlpha = clampA((int)(160 * masterAlpha + 40 * Math.sin(time * 2)));

            // Glow layer

            cg.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            cg.setColor(new Color(235, 203, 139, clampA(bAlpha / 4)));

            cg.drawLine(inset, barH + inset, inset + bSize, barH + inset);

            cg.drawLine(inset, barH + inset, inset, barH + inset + bSize);

            cg.drawLine(width - inset, barH + inset, width - inset - bSize, barH + inset);

            cg.drawLine(width - inset, barH + inset, width - inset, barH + inset + bSize);

            cg.drawLine(inset, height - barH - inset, inset + bSize, height - barH - inset);

            cg.drawLine(inset, height - barH - inset, inset, height - barH - inset - bSize);

            cg.drawLine(width - inset, height - barH - inset, width - inset - bSize, height - barH - inset);

            cg.drawLine(width - inset, height - barH - inset, width - inset, height - barH - inset - bSize);

            // Sharp layer

            cg.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            cg.setColor(new Color(235, 203, 139, clampA(bAlpha)));

            cg.drawLine(inset, barH + inset, inset + bSize, barH + inset);

            cg.drawLine(inset, barH + inset, inset, barH + inset + bSize);

            cg.drawLine(width - inset, barH + inset, width - inset - bSize, barH + inset);

            cg.drawLine(width - inset, barH + inset, width - inset, barH + inset + bSize);

            cg.drawLine(inset, height - barH - inset, inset + bSize, height - barH - inset);

            cg.drawLine(inset, height - barH - inset, inset, height - barH - inset - bSize);

            cg.drawLine(width - inset, height - barH - inset, width - inset - bSize, height - barH - inset);

            cg.drawLine(width - inset, height - barH - inset, width - inset, height - barH - inset - bSize);

            cg.dispose();

        }



        // ===== DRAMATIC VIGNETTE =====

        if (masterAlpha > 0.1f && !isPhase5) {

            RadialGradientPaint vig = new RadialGradientPaint(

                new Point2D.Float(cx, cy), width * 0.65f,

                new float[]{0f, 0.5f, 0.8f, 1f},

                new Color[]{RenderCache.BLACK_0,

                           new Color(0, 0, 0, clampA((int)(25 * masterAlpha))),

                           new Color(0, 0, 0, clampA((int)(90 * masterAlpha))),

                           new Color(0, 0, 0, clampA((int)(180 * masterAlpha)))});

            g.setPaint(vig);

            g.fillRect(-M, -M, width + M * 2, height + M * 2);



            // Phase-based color temperature overlay
            if (bossIntroPhase >= 1) {
                Color tempColor;
                float tempAlpha;
                if (bossIntroPhase <= 2) {
                    tempColor = new Color(30, 80, 180);
                    tempAlpha = 0.035f;
                } else if (bossIntroPhase == 3) {
                    tempColor = new Color(180, 60, 20);
                    tempAlpha = 0.035f;
                } else if (bossIntroPhase == 4) {
                    tempColor = new Color(200, 170, 80);
                    tempAlpha = 0.025f;
                } else {
                    tempColor = new Color(80, 80, 100);
                    tempAlpha = 0.02f;
                }
                tempAlpha = Math.max(0f, Math.min(1f, tempAlpha * masterAlpha));
                g.setColor(new Color(tempColor.getRed(), tempColor.getGreen(), tempColor.getBlue(),
                    clampA((int)(255 * tempAlpha))));
                g.fillRect(-M, -M, width + M * 2, height + M * 2);
            }

            // Edge color tint - blue left, red right (wider reach)
            if (bossIntroPhase >= 1 && bossIntroPhase <= 4 && !isPhase5) {
                float edgeAlpha = Math.max(0f, Math.min(1f, 0.1f * masterAlpha));
                GradientPaint leftEdge = new GradientPaint(
                    -M, cy, new Color(50, 130, 255, clampA((int)(50 * edgeAlpha * 255))),
                    width / 3, cy, RenderCache.BLACK_0);
                g.setPaint(leftEdge);
                g.fillRect(-M, -M, width / 3 + M, height + M * 2);
                GradientPaint rightEdge = new GradientPaint(
                    width * 2 / 3, cy, RenderCache.BLACK_0,
                    width + M, cy, new Color(255, 90, 20, clampA((int)(45 * edgeAlpha * 255))));
                g.setPaint(rightEdge);
                g.fillRect(width * 2 / 3, -M, width / 3 + M, height + M * 2);
            }

            // Film grain overlay (cinematic noise)
            if (!isPhase5) {
                Graphics2D grg = (Graphics2D) g.create();
                grg.setComposite(RenderCache.getAlpha(Math.max(0f, Math.min(1f, 0.025f * masterAlpha))));
                int seed = (int)(time * 60);
                for (int gy = 0; gy < height; gy += 12) {
                    for (int gx = 0; gx < width; gx += 12) {
                        int hash = ((gx * 73 + gy * 137 + seed * 41) ^ 0x5F3759DF) & 0xFF;
                        if (hash > 220) {
                            grg.setColor(hash > 240 ? Color.WHITE : Color.BLACK);
                            grg.fillRect(gx, gy, 2, 2);
                        }
                    }
                }
                grg.dispose();
            }

        }

    }



}



