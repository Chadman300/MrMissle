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





    

    // Cached rendering objects for performance

    private static final AlphaComposite ALPHA_FULL = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f);

    private static final AlphaComposite ALPHA_HALF = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);

    private static final AlphaComposite ALPHA_THIRD = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);

    private static final Color AFTERIMAGE_COLOR = new Color(200, 220, 255);

    private static final Color SHIELD_GLOW = new Color(136, 192, 208, 50);

    private static final Color SHIELD_RING = new Color(136, 192, 208, 100);

    private static final Color SHIELD_CORE = new Color(136, 192, 208, 150);

    private static final BasicStroke STROKE_1 = new BasicStroke(1f);

    private static final BasicStroke STROKE_2 = new BasicStroke(2f);

    private static final BasicStroke STROKE_3 = new BasicStroke(3f);

    

    // Cached vignette for performance

    private BufferedImage cachedVignette = null;

    private int cachedVignetteWidth = 0;

    private int cachedVignetteHeight = 0;

    

    // Cached Font objects to avoid repeated creation

    private static final Font FONT_TITLE_LARGE = new Font("Arial", Font.BOLD, 84);

    private static final Font FONT_TITLE = new Font("Arial", Font.BOLD, 72);

    private static final Font FONT_TITLE_MEDIUM = new Font("Arial", Font.BOLD, 60);

    private static final Font FONT_SUBTITLE = new Font("Arial", Font.BOLD, 36);

    private static final Font FONT_LARGE_32 = new Font("Arial", Font.BOLD, 32);

    private static final Font FONT_LARGE = new Font("Arial", Font.BOLD, 28);

    private static final Font FONT_MEDIUM = new Font("Arial", Font.PLAIN, 24);

    private static final Font FONT_MEDIUM_BOLD = new Font("Arial", Font.BOLD, 24);

    private static final Font FONT_SMALL = new Font("Arial", Font.PLAIN, 20);

    private static final Font FONT_INFO = new Font("Arial", Font.PLAIN, 18);

    private static final Font FONT_TINY = new Font("Arial", Font.BOLD, 18);

    private static final Font FONT_EXTRA_SMALL_16 = new Font("Arial", Font.BOLD, 16);

    private static final Font FONT_EXTRA_SMALL_13 = new Font("Arial", Font.PLAIN, 13);

    private static final Font FONT_EXTRA_SMALL_12 = new Font("Arial", Font.BOLD, 12);

    private static final Font FONT_EXTRA_SMALL_11 = new Font("Arial", Font.PLAIN, 11);

    

    // Smooth UI animations

    private double displayedScore = 0;

    private double displayedMoney = 0;

    private double comboPulseScale = 1.0;

    private int lastComboCount = 0;

    

    public Renderer(GameData gameData, ShopManager shopManager, PassiveUpgradeManager passiveUpgradeManager, java.util.function.IntConsumer bgProgressCallback) {

        this.gameData = gameData;

        this.shopManager = shopManager;

        this.passiveUpgradeManager = passiveUpgradeManager;

        

        // Load background layers

        loadBackgroundLayers(bgProgressCallback);

        

        // Load overlay image

        loadOverlay();

        

        // Initialize menu buttons (positions will be updated in drawMenu)

        // Order: Select Level, Shop, Stats, Achievements, Game Info, Settings, Save Files

        menuButtons = new UIButton[7];

        menuButtons[0] = new UIButton("Select Level", "level", 0, 0, 300, 55, new Color(163, 190, 140), new Color(180, 210, 160)); // Green - Play

        menuButtons[1] = new UIButton("Shop", "shop", 0, 0, 300, 55, new Color(235, 203, 139), new Color(250, 220, 160)); // Gold

        menuButtons[2] = new UIButton("Stats", "stats", 0, 0, 300, 55, new Color(136, 192, 208), new Color(160, 210, 225)); // Cyan

        menuButtons[3] = new UIButton("Achievements", "achievements", 0, 0, 300, 55, new Color(180, 142, 173), new Color(200, 165, 195)); // Purple

        menuButtons[4] = new UIButton("Game Info", "info", 0, 0, 300, 55, new Color(143, 188, 187), new Color(165, 205, 205)); // Teal

        menuButtons[5] = new UIButton("Settings", "settings", 0, 0, 300, 55, new Color(191, 97, 106), new Color(215, 120, 130)); // Red

        menuButtons[6] = new UIButton("[SAVE] Save Files", "save", 0, 0, 300, 55, new Color(208, 135, 112), new Color(225, 155, 135)); // Orange

        

        // Initialize shop buttons (15 items: continue + 4 shop upgrades + 10 passive upgrades)

        shopButtons = new UIButton[15];

        for (int i = 0; i < 15; i++) {

            shopButtons[i] = new UIButton("", 0, 0, 800, 50, new Color(76, 86, 106), new Color(180, 142, 173));

        }

        

        // Initialize stats buttons (5 items: 4 upgrades + active item)

        statsButtons = new UIButton[4];

        String[] statNames = {"Speed Boost", "Bullet Slow", "Lucky Dodge", "Active Item"};

        Color[] statColors = {new Color(143, 188, 187), new Color(136, 192, 208), new Color(180, 142, 173), new Color(235, 203, 139)};

        for (int i = 0; i < 4; i++) {

            statsButtons[i] = new UIButton(statNames[i], 0, 0, 840, 70, new Color(59, 66, 82), statColors[i]);

        }

        

        // Initialize settings buttons (16 options - max for Graphics category)

        settingsButtons = new UIButton[16];

        for (int i = 0; i < 16; i++) {

            settingsButtons[i] = new UIButton("", 0, 0, 700, 70, new Color(76, 86, 106), new Color(235, 203, 139));

        }

        

        // Initialize pause buttons (4 buttons - labels set dynamically by configurePauseMenu)

        pauseButtons = new UIButton[4];

        String[] pauseLabels = {"Resume", "Settings", "Main Menu", ""};

        for (int i = 0; i < 4; i++) {

            pauseButtons[i] = new UIButton(pauseLabels[i], 0, 0, 300, 60, new Color(76, 86, 106), new Color(235, 203, 139));

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

        // Draw dark animated gradient background

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        String title = "MR. MISSLE";

        g.setFont(FONT_TITLE_LARGE);

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = height / 2 - 100;

        

        // Shadow layer

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text (teal to purple)

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 50, new Color(143, 188, 187),

            titleX, titleY + 20, new Color(180, 142, 173)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(ALPHA_THIRD);

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(ALPHA_FULL);

        

        // Loading text

        g.setColor(new Color(216, 222, 233));

        g.setFont(FONT_MEDIUM);

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

            GradientPaint barGradient = new GradientPaint(

                barX, barY, new Color(143, 188, 187),

                barX + fillWidth, barY + barHeight, new Color(136, 192, 208)

            );

            g.setPaint(barGradient);

            g.fillRoundRect(barX, barY, fillWidth, barHeight, 15, 15);

        }

        

        // Border

        g.setColor(new Color(200, 200, 200));

        g.setStroke(new BasicStroke(2));

        g.drawRoundRect(barX, barY, barWidth, barHeight, 15, 15);

        

        // Percentage text

        g.setColor(Color.WHITE);

        g.setFont(FONT_TINY);

        String percentText = progress + "%";

        fm = g.getFontMetrics();

        g.drawString(percentText, (width - fm.stringWidth(percentText)) / 2, barY + barHeight + 30);

    }

    

    public void drawMenu(Graphics2D g, int width, int height, double time, double escapeTimer, int selectedMenuItem, int currentSaveSlot) {

        // Draw animated gradient background with palette colors

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Draw cool geometric background effects

        drawGeometricBackground(g, width, height, time);

        

        g.setColor(Color.WHITE);

        g.setFont(FONT_TITLE);

        String title = "MR. MISSLE";

        FontMetrics fm = g.getFontMetrics();

        

        // Balatro-style title with holographic shine effect

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 150;

        

        // Shadow layers

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text effect

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 50, new Color(191, 97, 106), // Red

            titleX, titleY + 20, new Color(220, 120, 130) // Lighter red

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(ALPHA_THIRD);

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(ALPHA_FULL);

        

        // Draw buttons with slight vertical offset

        int buttonY = 240;

        int buttonSpacing = 75;

        for (int i = 0; i < menuButtons.length; i++) {

            menuButtons[i].setPosition((width - 300) / 2, buttonY + i * buttonSpacing);

            menuButtons[i].update(i == selectedMenuItem, time);

            menuButtons[i].draw(g, time);

        }

        

        // Show score and money in a nice card

        drawStatsCard(g, width, height, time);

        

        // Version and save slot info (bottom left, offset to avoid corner decorations)

        g.setFont(FONT_TINY);

        g.setColor(new Color(120, 130, 150, 180));

        String versionText = Game.GAME_VERSION;

        g.drawString(versionText, 110, height - 70);

        

        if (currentSaveSlot > 0) {

            String saveText = "Save Slot " + currentSaveSlot;

            g.drawString(saveText, 110, height - 50);

        }

        

        // Quit hint

        if (escapeTimer > 0) {

            g.setColor(new Color(191, 97, 106)); // Palette red

            g.setFont(FONT_MEDIUM_BOLD);

            String quitText = "Press ESC again to Quit";

            fm = g.getFontMetrics();

            g.drawString(quitText, (width - fm.stringWidth(quitText)) / 2, height - 210);

        }

    }

    

    public void drawSaveSelection(Graphics2D g, int width, int height, double time, int selectedSlot,

                                  SaveManager.SaveMetadata[] saveMetadata, boolean deletingSlot, 

                                  int deleteConfirmTimer, double escapeTimer) {

        // Draw animated gradient background

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Draw geometric background effects

        drawGeometricBackground(g, width, height, time);

        

        // Title

        g.setFont(FONT_TITLE);

        String title = "SELECT SAVE";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 120;

        

        // Shadow

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 50, new Color(136, 192, 208),

            titleX, titleY + 20, new Color(143, 188, 187)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(ALPHA_THIRD);

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(ALPHA_FULL);

        

        // Draw save slots

        int slotWidth = 800;

        int slotHeight = 160;

        int slotX = (width - slotWidth) / 2;

        int startY = 200;

        int slotSpacing = 180;

        

        for (int i = 0; i < 3; i++) {

            int slotY = startY + i * slotSpacing;

            boolean isSelected = (i == selectedSlot);

            boolean isEmpty = (saveMetadata[i] == null);

            

            // Draw slot background

            Graphics2D g2 = (Graphics2D) g.create();

            if (Game.enableAntiAliasing) {

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            }

            

            // Selection glow

            if (isSelected) {

                g2.setColor(new Color(235, 203, 139, 100));

                g2.fillRoundRect(slotX - 8, slotY - 8, slotWidth + 16, slotHeight + 16, 20, 20);

            }

            

            // Main slot background

            Color slotColor = isEmpty ? new Color(59, 66, 82) : new Color(76, 86, 106);

            if (isSelected) {

                slotColor = new Color(88, 91, 112); // Lighter when selected

            }

            g2.setColor(slotColor);

            g2.fillRoundRect(slotX, slotY, slotWidth, slotHeight, 15, 15);

            

            // Border

            Color borderColor = isSelected ? new Color(235, 203, 139) : new Color(94, 129, 172);

            g2.setStroke(new BasicStroke(3));

            g2.setColor(borderColor);

            g2.drawRoundRect(slotX, slotY, slotWidth, slotHeight, 15, 15);

            

            // Slot number

            g2.setFont(FONT_LARGE);

            g2.setColor(new Color(235, 203, 139));

            String slotNum = "SLOT " + (i + 1);

            g2.drawString(slotNum, slotX + 20, slotY + 35);

            

            if (isEmpty) {

                // Empty slot - show "New Game"

                g2.setFont(FONT_MEDIUM);

                g2.setColor(new Color(216, 222, 233, 150));

                String emptyText = "[ Empty Slot - Press Enter to Create ]";

                fm = g2.getFontMetrics();

                int emptyX = slotX + (slotWidth - fm.stringWidth(emptyText)) / 2;

                g2.drawString(emptyText, emptyX, slotY + 85);

            } else {

                // Show save data

                SaveManager.SaveMetadata meta = saveMetadata[i];

                

                // Save name

                g2.setFont(FONT_MEDIUM_BOLD);

                g2.setColor(Color.WHITE);

                g2.drawString(meta.saveName, slotX + 20, slotY + 65);

                

                // Stats line 1

                g2.setFont(FONT_SMALL);

                g2.setColor(new Color(216, 222, 233));

                String stats1 = String.format("Max Level: %d  |  Money: $%d", meta.maxLevel, meta.totalMoney);

                g2.drawString(stats1, slotX + 20, slotY + 90);

                

                // Stats line 2

                String stats2 = String.format("Runs: %d  |  Best Run: Level %d  |  Bosses: %d", 

                    meta.totalRuns, meta.bestRunLevel, meta.totalBosses);

                g2.drawString(stats2, slotX + 20, slotY + 110);

                

                // Last saved date

                g2.setColor(new Color(216, 222, 233, 180));

                String dateText = "Last Saved: " + meta.getFormattedDate();

                g2.drawString(dateText, slotX + 20, slotY + 128);

                

                // Delete button

                int btnX = slotX + slotWidth - 120;

                int btnY = slotY + 10;

                int btnWidth = 100;

                int btnHeight = 35;

                

                // Button background

                Color btnColor = isSelected && deletingSlot ? new Color(191, 97, 106) : new Color(191, 97, 106, 150);

                g2.setColor(btnColor);

                g2.fillRoundRect(btnX, btnY, btnWidth, btnHeight, 8, 8);

                

                // Button border

                g2.setStroke(new BasicStroke(2));

                g2.setColor(new Color(215, 120, 130));

                g2.drawRoundRect(btnX, btnY, btnWidth, btnHeight, 8, 8);

                

                // Button text

                g2.setFont(FONT_TINY);

                g2.setColor(Color.WHITE);

                String btnText = "DELETE";

                fm = g2.getFontMetrics();

                int textX = btnX + (btnWidth - fm.stringWidth(btnText)) / 2;

                int textY = btnY + (btnHeight + fm.getAscent()) / 2 - 2;

                g2.drawString(btnText, textX, textY);

                

                // Delete confirmation if deleting this slot

                if (isSelected && deletingSlot) {

                    float progress = Math.min(1.0f, deleteConfirmTimer / 60.0f);

                    

                    // Delete progress bar - positioned below the delete button

                    int barX = slotX + 20;

                    int barY = slotY + slotHeight - 25;

                    int barWidth = slotWidth - 40;

                    int barHeight = 12;

                    

                    g2.setColor(new Color(191, 97, 106, 100));

                    g2.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);

                    

                    g2.setColor(new Color(191, 97, 106));

                    g2.fillRoundRect(barX, barY, (int)(barWidth * progress), barHeight, 6, 6);

                    

                    // Delete text

                    g2.setFont(FONT_TINY);

                    String deleteText = "HOLD DELETE...";

                    fm = g2.getFontMetrics();

                    g2.drawString(deleteText, barX + (barWidth - fm.stringWidth(deleteText)) / 2, barY - 5);

                }

            }

            

            g2.dispose();

        }

        

        // Instructions

        g.setFont(FONT_MEDIUM);

        g.setColor(new Color(216, 222, 233, 200));

        String instructions = "↑/↓: Navigate  |  ENTER: Select/Create  |  DELETE: Hold to Delete Save";

        fm = g.getFontMetrics();

        g.drawString(instructions, (width - fm.stringWidth(instructions)) / 2, height - 100);

        

        // Quit hint

        if (escapeTimer > 0) {

            g.setColor(new Color(191, 97, 106));

            g.setFont(FONT_MEDIUM_BOLD);

            String quitText = "Press ESC again to Quit";

            fm = g.getFontMetrics();

            g.drawString(quitText, (width - fm.stringWidth(quitText)) / 2, height - 60);

        }

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

        g.setStroke(new BasicStroke(1));

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

            new Color(163, 190, 140, 40), // Green

            new Color(191, 97, 106, 35),  // Red

            new Color(136, 192, 208, 30)  // Cyan

        };

        

        g.setStroke(new BasicStroke(2));

        

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

        g.setStroke(new BasicStroke(2));

        

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

        g.setColor(new Color(46, 52, 64, 200));

        g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 15, 15);

        

        // Card border

        int borderAlpha = (int)(150 + 50 * Math.sin(time * 2));

        g.setColor(new Color(76, 86, 106, borderAlpha));

        g.setStroke(new BasicStroke(2));

        g.drawRoundRect(cardX, cardY, cardWidth, cardHeight, 15, 15);

        

        // Calculate divider position based on text widths

        int dividerX = cardX + padding / 2 + scoreWidth + dividerSpace / 2;

        

        // Divider line

        g.setColor(new Color(76, 86, 106, 100));

        g.drawLine(dividerX, cardY + 10, dividerX, cardY + cardHeight - 10);

        

        int textY = cardY + cardHeight / 2 + fm.getAscent() / 2 - 5;

        

        // Score display (left side)

        g.setColor(new Color(163, 190, 140)); // Green

        int scoreX = cardX + padding / 2;

        g.drawString(scoreText, scoreX, textY);

        

        // Money display (right side of divider)

        g.setColor(new Color(235, 203, 139)); // Gold

        int moneyX = dividerX + dividerSpace / 2;

        g.drawString(moneyText, moneyX, textY);

    }

    

    public void drawInfo(Graphics2D g, int width, int height, double time) {

        // Draw animated gradient

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        g.setFont(FONT_TITLE_MEDIUM);

        String title = "GAME GUIDE";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 60;

        

        // Shadow

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 30, new Color(136, 192, 208),

            titleX, titleY + 20, new Color(143, 188, 187)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        int leftX = 60;

        int rightX = width / 2 + 40;

        int columnWidth = width / 2 - 80;

        

        // LEFT COLUMN

        int y = 105;

        

        // Core Mechanics section

        g.setColor(new Color(143, 188, 187));

        g.setFont(new Font("Arial", Font.BOLD, 24));

        g.drawString("CORE MECHANICS", leftX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.PLAIN, 16));

        String[] mechanics = {

            "VULNERABILITY SYSTEM:",

            "  â€¢ Boss invulnerable for 20 seconds",

            "  â€¢ Watch for GOLDEN GLOW = Attack Window!",

            "  â€¢ Window lasts 20 seconds (longer with upgrades)",

            "  â€¢ Hit boss 3 times to win",

            "",

            "GRAZE SYSTEM:",

            "  â€¢ 25px from bullet = Graze (+score, +combo)",

            "  â€¢ 15px = Close Call (bonus points)",

            "  â€¢ 8px = Perfect Dodge (grants i-frames!)",

            "  â€¢ Build combos: Chain dodges within 3s",

            "",

            "DEATH & RESPAWN:",

            "  â€¢ One hit = death (unless Lucky Dodge procs)",

            "  â€¢ Boss hit (non-fatal) = 1.5s respawn delay",

            "  â€¢ Use extra lives for second chances",

            "  â€¢ Lucky Dodge upgrade = revival chance"

        };

        

        for (String line : mechanics) {

            g.drawString(line, leftX + 10, y);

            y += 20;

        }

        

        // Passive Upgrades section

        y += 10;

        g.setColor(new Color(235, 203, 139));

        g.setFont(new Font("Arial", Font.BOLD, 24));

        g.drawString("PASSIVE UPGRADES", leftX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.PLAIN, 16));

        String[] upgrades = {

            "SPEED BOOST (Max Lv 10):",

            "  â€¢ +10% movement speed per level",

            "  â€¢ Essential for dodging dense patterns",

            "",

            "BULLET SLOW (Max Lv 50):",

            "  â€¢ Slows enemy bullets by 2% per level",

            "  â€¢ More time to react and plan dodges",

            "",

            "LUCKY DODGE (Max Lv 12):",

            "  â€¢ 8% chance per level to survive hits",

            "  â€¢ Flicker effect on successful dodge",

            "  â€¢ Stacks with extra lives",

            "",

            "ATTACK WINDOW (Max Lv 10):",

            "  â€¢ +1 second vulnerability per level",

            "  â€¢ Max: 30 seconds to hit boss",

            "  â€¢ More forgiving timing"

        };

        

        for (String line : upgrades) {

            g.drawString(line, leftX + 10, y);

            y += 20;

        }

        

        // RIGHT COLUMN

        y = 105;

        

        // Active Items section

        g.setColor(new Color(136, 192, 208));

        g.setFont(new Font("Arial", Font.BOLD, 24));

        g.drawString("ACTIVE ITEMS", rightX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.PLAIN, 16));

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

        g.setColor(new Color(191, 97, 106));

        g.setFont(new Font("Arial", Font.BOLD, 24));

        g.drawString("RISK CONTRACTS", rightX, y);

        y += 25;

        

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.PLAIN, 16));

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

        g.setColor(new Color(216, 222, 233));

        g.setFont(new Font("Arial", Font.BOLD, 18));

        g.drawString("CONTROLS: WASD/Arrows = Move  |  E = Use Item  |  ESC = Pause  |  Mouse = Navigate Menus", width / 2 - 450, height - 30);

        g.drawString("TIP: Visit SHOP for upgrades | Complete ACHIEVEMENTS | Use STATS to track progress", width / 2 - 420, height - 10);

    }

    

    public void drawAchievements(Graphics2D g, int width, int height, double time, AchievementManager achievementManager, double scrollOffset) {

        // Draw animated gradient background

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        g.setFont(new Font("Arial", Font.BOLD, 60));

        String title = "ACHIEVEMENTS";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 80;

        

        // Shadow

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 30, new Color(235, 203, 139), // Gold

            titleX, titleY + 20, new Color(255, 230, 150)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        // Achievement count

        int unlocked = achievementManager.getUnlockedCount();

        int total = achievementManager.getAllAchievements().size();

        g.setFont(new Font("Arial", Font.BOLD, 24));

        g.setColor(new Color(163, 190, 140)); // Green

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

                g.setColor(new Color(235, 203, 139, 40));

                g.fillRoundRect(x - 3, y - 3, cardWidth + 6, cardHeight + 6, 15, 15);

                g.setColor(new Color(46, 52, 64, 240));

            } else {

                // Locked - darker

                g.setColor(new Color(30, 35, 45, 240));

            }

            g.fillRoundRect(x, y, cardWidth, cardHeight, 12, 12);

            

            // Border

            g.setStroke(new BasicStroke(2));

            if (ach.isUnlocked()) {

                g.setColor(new Color(235, 203, 139)); // Gold border

            } else {

                g.setColor(new Color(76, 86, 106)); // Grey border

            }

            g.drawRoundRect(x, y, cardWidth, cardHeight, 12, 12);

            

            // Achievement icon/status

            int iconSize = 40;

            int iconX = x + 15;

            int iconY = y + (cardHeight - iconSize) / 2;

            

            if (ach.isUnlocked()) {

                // Checkmark circle

                g.setColor(new Color(163, 190, 140)); // Green

                g.fillOval(iconX, iconY, iconSize, iconSize);

                g.setColor(Color.WHITE);

                g.setStroke(new BasicStroke(3));

                g.drawLine(iconX + 10, iconY + 20, iconX + 18, iconY + 28);

                g.drawLine(iconX + 18, iconY + 28, iconX + 30, iconY + 12);

            } else {

                // Lock icon

                g.setColor(new Color(100, 100, 110));

                g.fillOval(iconX, iconY, iconSize, iconSize);

                g.setColor(new Color(60, 60, 70));

                g.fillRect(iconX + 12, iconY + 22, 16, 14);

                g.setColor(new Color(80, 80, 90));

                g.setStroke(new BasicStroke(2));

                g.drawArc(iconX + 13, iconY + 10, 14, 16, 0, 180);

            }

            

            // Achievement name

            g.setFont(new Font("Arial", Font.BOLD, 16));

            if (ach.isUnlocked()) {

                g.setColor(new Color(235, 203, 139)); // Gold

            } else {

                g.setColor(new Color(150, 150, 160));

            }

            g.drawString(ach.getName(), x + 65, y + 28);

            

            // Description

            g.setFont(new Font("Arial", Font.PLAIN, 13));

            g.setColor(ach.isUnlocked() ? new Color(200, 200, 210) : new Color(100, 100, 110));

            g.drawString(ach.getDescription(), x + 65, y + 48);

            

            // Progress bar (only if not unlocked)

            if (!ach.isUnlocked()) {

                int barWidth = cardWidth - 80;

                int barHeight = 8;

                int barX = x + 65;

                int barY = y + 60;

                

                // Background

                g.setColor(new Color(40, 45, 55));

                g.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);

                

                // Fill

                float progress = ach.getProgressPercent();

                g.setColor(new Color(136, 192, 208)); // Teal

                g.fillRoundRect(barX, barY, (int)(barWidth * progress), barHeight, 4, 4);

                

                // Progress text

                g.setFont(new Font("Arial", Font.PLAIN, 11));

                g.setColor(new Color(120, 130, 140));

                String progressText = ach.getProgress() + " / " + ach.getTarget();

                g.drawString(progressText, barX + barWidth - fm.stringWidth(progressText) + 20, y + 85);

            } else {

                // "COMPLETE" badge

                g.setFont(new Font("Arial", Font.BOLD, 12));

                g.setColor(new Color(163, 190, 140));

                g.drawString("COMPLETE", x + 65, y + 75);

            }

        }

        

        // Reset clip

        g.setClip(null);

        

        // Controls hint

        g.setColor(new Color(216, 222, 233));

        g.setFont(new Font("Arial", Font.PLAIN, 20));

        String hint = "Press ESC to return to menu | UP/DOWN to scroll";

        fm = g.getFontMetrics();

        g.drawString(hint, (width - fm.stringWidth(hint)) / 2, height - 40);

    }

    

    public void drawStats(Graphics2D g, int width, int height, double time, PassiveUpgradeManager passiveManager) {

        // Draw animated gradient

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        g.setFont(new Font("Arial", Font.BOLD, 60));

        String title = "STATS";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 80;

        

        // Shadow

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 30, new Color(143, 188, 187),

            titleX, titleY + 20, new Color(136, 192, 208)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        // Show total money with glow

        g.setColor(new Color(163, 190, 140));

        g.setFont(new Font("Arial", Font.BOLD, 28));

        String money = "Money: $" + gameData.getTotalMoney();

        fm = g.getFontMetrics();

        int moneyX = (width - fm.stringWidth(money)) / 2;

        g.drawString(money, moneyX, 120);

        

        // Instructions at top

        g.setColor(new Color(216, 222, 233));

        g.setFont(new Font("Arial", Font.PLAIN, 16));

        String inst = "UP/DOWN to select | LEFT/RIGHT to adjust | ESC to return";

        fm = g.getFontMetrics();

        g.drawString(inst, (width - fm.stringWidth(inst)) / 2, 145);

    }

    

    public void drawStatsUpgrades(Graphics2D g, int width, int selectedStatItem, PassiveUpgradeManager passiveManager, double scrollOffset) {

        int baseY = 180;

        int y = baseY - (int)scrollOffset;

        int cardWidth = 900;

        int cardHeight = 65;

        int cardSpacing = 10;

        int currentIndex = 0;

        

        // Section 1: Active Item (index 0)

        g.setColor(new Color(163, 190, 140));

        g.setFont(new Font("Arial", Font.BOLD, 20));

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

            {ActiveItem.ItemType.TYPE_PURGE, 12, "Chromatic Purge", "Erase all bullets of a random type"},

            {ActiveItem.ItemType.TIME_SLOW, 15, "Time Slow", "Slow bullets & beams by 70%"},

            {ActiveItem.ItemType.DASH, 18, "Dash", "Quick dash with invincibility"},

            {ActiveItem.ItemType.IMPULSE, 21, "Impulse", "Push all bullets away from you"},

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


        g.setColor(new Color(0, 0, 0, 100));


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


            cardBg = new Color(76, 86, 106, 200);


        } else {


            cardBg = new Color(35, 35, 45, 220);


        }


        g.setColor(cardBg);


        g.fillRoundRect(itemX, y, cardWidth, singleCardH, 12, 12);


        


        // Selection highlight border


        if (isSelected) {


            g.setColor(new Color(235, 203, 139, 200));


            g.setStroke(new BasicStroke(2.5f));


            g.drawRoundRect(itemX, y, cardWidth, singleCardH, 12, 12);


            g.setStroke(new BasicStroke(1f));


        } else if (isEquipped) {


            g.setColor(new Color(163, 210, 140, 180));


            g.setStroke(new BasicStroke(2f));


            g.drawRoundRect(itemX, y, cardWidth, singleCardH, 12, 12);


            g.setStroke(new BasicStroke(1f));


        }


        


        // Left arrow


        if (displayIndex > 0) {


            g.setFont(new Font("Arial", Font.BOLD, 28));


            g.setColor(isSelected ? new Color(235, 203, 139) : new Color(150, 150, 150));


            g.drawString("<", itemX + 14, y + singleCardH / 2 + 10);


        }


        


        // Right arrow


        if (displayIndex < allItems.length - 1) {


            g.setFont(new Font("Arial", Font.BOLD, 28));


            g.setColor(isSelected ? new Color(235, 203, 139) : new Color(150, 150, 150));


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

            Color itemIconColor = new Color(100, 200, 255);

            String itemSymbol = "?";

            switch (itemType) {

                case LUCKY_CHARM: itemIconColor = new Color(255, 215, 80); itemSymbol = "$"; break;

                case SHIELD: itemIconColor = new Color(100, 180, 255); itemSymbol = "O"; break;

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

            g.setColor(new Color(40, 45, 55));

            g.fillOval(iconX, iconY, iconSize, iconSize);

            g.setColor(new Color(itemIconColor.getRed(), itemIconColor.getGreen(), itemIconColor.getBlue(), isEquipped ? 255 : 200));

            g.setStroke(new BasicStroke(2f));

            g.drawOval(iconX, iconY, iconSize, iconSize);

            g.setStroke(new BasicStroke(1f));



            // Draw symbol centered in circle

            g.setFont(new Font("Arial", Font.BOLD, 24));

            g.setColor(itemIconColor);

            FontMetrics symFm = g.getFontMetrics();

            g.drawString(itemSymbol, iconX + iconSize / 2 - symFm.stringWidth(itemSymbol) / 2, iconY + iconSize / 2 + symFm.getAscent() / 3);



            // --- Text info (right of icon) ---

            int textX = iconX + iconSize + 24;

            int textRightEdge = itemX + cardWidth - 60;



            // Item name (large, bold)

            g.setFont(new Font("Arial", Font.BOLD, 18));

            g.setColor(isEquipped ? new Color(163, 210, 140) : Color.WHITE);

            String displayName = itemName;

            g.drawString(displayName, textX, y + 35);



            // Equipped badge (inline, smaller)

            if (isEquipped) {

                FontMetrics nameFm = g.getFontMetrics();

                int badgeX = textX + nameFm.stringWidth(displayName) + 12;

                g.setFont(new Font("Arial", Font.BOLD, 11));

                FontMetrics badgeFm = g.getFontMetrics();

                String badge = "EQUIPPED";

                int badgeW = badgeFm.stringWidth(badge) + 12;

                int badgeH = 18;

                int badgeY = y + 22;

                g.setColor(new Color(163, 210, 140, 40));

                g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 8, 8);

                g.setColor(new Color(163, 210, 140, 160));

                g.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 8, 8);

                g.setColor(new Color(163, 210, 140));

                g.drawString(badge, badgeX + 6, badgeY + 13);

            }



            // Description (medium, light)

            g.setFont(new Font("Arial", Font.PLAIN, 13));

            g.setColor(new Color(175, 185, 200));

            g.drawString(itemDesc, textX, y + 56);



            // Unlock level (small, teal, with dot separator)

            g.setFont(new Font("Arial", Font.PLAIN, 13));

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

            g.setStroke(new BasicStroke(2f));

            g.drawOval(iconX, iconY, iconSize, iconSize);

            g.setStroke(new BasicStroke(1f));



            // Lock symbol in circle

            g.setFont(new Font("Arial", Font.BOLD, 28));

            g.setColor(new Color(200, 180, 140));

            FontMetrics lockFm = g.getFontMetrics();

            String lockSym = "[X]";

            g.drawString(lockSym, iconX + iconSize / 2 - lockFm.stringWidth(lockSym) / 2, iconY + iconSize / 2 + lockFm.getAscent() / 3);



            // --- Locked text info ---

            int textX = iconX + iconSize + 24;



            // Encrypted name

            g.setFont(new Font("Arial", Font.BOLD, 16));

            g.setColor(new Color(190, 180, 160));

            String lockedName = encryptItemName(itemName);

            g.drawString(lockedName, textX, y + 40);



            // Unlock requirement (prominent, golden)

            g.setFont(new Font("Arial", Font.BOLD, 15));

            g.setColor(new Color(235, 210, 140));

            String reqStr = "Defeat Level " + unlockLevel + " Boss to Unlock";

            g.drawString(reqStr, textX, y + 62);



            // Subtle locked separator







        }




        


        // Counter: X / 9


        g.setFont(new Font("Arial", Font.BOLD, 14));


        g.setColor(new Color(136, 192, 208));


        String counter = (displayIndex + 1) + " / " + allItems.length;


        FontMetrics ctrFm = g.getFontMetrics();


        g.drawString(counter, itemX + (cardWidth - ctrFm.stringWidth(counter)) / 2, y + singleCardH + 20);


        


        y += singleCardH + 30 + cardSpacing;

        currentIndex++;

        

        // Section 2: All Upgrades (indices 1+) - from PassiveUpgradeManager

        y += 20;

        g.setColor(new Color(180, 142, 173));

        g.setFont(new Font("Arial", Font.BOLD, 20));

        g.drawString("SHOP UPGRADES - Allocate purchased levels", width / 2 - 400, y);

        y += 30;

        

        // All upgrades now come from PassiveUpgradeManager

        if (passiveManager != null) {

            java.util.List<PassiveUpgrade> upgrades = passiveManager.getAllUpgrades();

            

            // Draw all adjustable upgrades (all except Extra Lives which is last)

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

            

            // Read-only section for Extra Lives

            if (upgrades.size() > 0) {

                y += 20;

                g.setColor(new Color(163, 190, 140));

                g.setFont(new Font("Arial", Font.BOLD, 20));

                g.drawString("CONSUMABLE LIVES - Buy from shop, used on death", width / 2 - 400, y);

                y += 30;

                

                // Draw Extra Lives (last item, read-only)

                PassiveUpgrade upgrade = upgrades.get(upgrades.size() - 1);

                isSelected = currentIndex == selectedStatItem;

                

                String icon = getPassiveIcon(upgrade.getType());

                int livesOwned = gameData.getExtraLives();  // Current lives count

                int livesPurchased = upgrade.getCurrentLevel();  // Total purchased

                

                drawUpgradeCard(g, width / 2 - cardWidth / 2, y, cardWidth, cardHeight,

                               icon, upgrade.getName(), livesOwned, livesPurchased, isSelected, true, true);

                

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

        g.setColor(new Color(0, 0, 0, 120));

        g.fillRoundRect(x + 3, y + 3, width, height, 15, 15);

        

        // Card background

        Color cardColor;

        if (current >= max && max > 0) {

            cardColor = new Color(85, 75, 45, 200); // Dark gold for maxed - better text contrast

        } else if (isSelected && !isReadOnly) {

            cardColor = new Color(120, 110, 140, 200); // Softer purple for selected

        } else {

            cardColor = new Color(76, 86, 106, 200);

        }

        

        g.setColor(cardColor);

        g.fillRoundRect(x, y, width, height, 15, 15);

        

        // Border glow for selected

        if (isSelected && !isReadOnly) {

            g.setColor(new Color(180, 170, 130, 140)); // Softer border glow

            g.setStroke(new BasicStroke(2f));

            g.drawRoundRect(x, y, width, height, 15, 15);

            g.setStroke(new BasicStroke(1f));

        }

        

        // Draw icon

        g.setFont(new Font("Arial", Font.BOLD, 32));

        g.setColor(new Color(235, 203, 139));

        g.drawString(icon, x + 20, y + 40);

        

        // Draw name

        g.setFont(new Font("Arial", Font.BOLD, 20));

        g.setColor(Color.WHITE);

        g.drawString(name, x + 75, y + 30);

        

        // Draw level info

        g.setFont(new Font("Arial", Font.PLAIN, 14));

        g.setColor(new Color(200, 200, 200));

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

            g.setColor(new Color(40, 40, 50, 180));

            g.fillRoundRect(barX, barY, barWidth, barHeight, 5, 5);

            

            // Fill

            if (max > 0 && current > 0) {

                double progress = (double)current / max;

                int fillWidth = (int)(barWidth * progress);

                

                GradientPaint grad = new GradientPaint(

                    barX, 0, new Color(163, 190, 140),

                    barX + fillWidth, 0, new Color(235, 203, 139)

                );

                g.setPaint(grad);

                g.fillRoundRect(barX, barY, fillWidth, barHeight, 5, 5);

            }

            

            // Level text

            g.setFont(new Font("Arial", Font.BOLD, 14));

            g.setColor(current >= max && max > 0 ? new Color(235, 203, 139) : Color.WHITE);

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

            

            g.setColor(current > 0 ? new Color(191, 97, 106) : new Color(80, 80, 80));

            g.fillRoundRect(minusX, btnY, btnSize, btnSize, 8, 8);

            g.setColor(Color.WHITE);

            g.setStroke(new BasicStroke(2));

            g.drawRoundRect(minusX, btnY, btnSize, btnSize, 8, 8);

            g.setFont(new Font("Arial", Font.BOLD, 28));

            g.drawString("-", minusX + 12, btnY + 26);

            

            // Plus button

            int plusX = x + 845;

            g.setColor(current < max ? new Color(163, 190, 140) : new Color(80, 80, 80));

            g.fillRoundRect(plusX, btnY, btnSize, btnSize, 8, 8);

            g.setColor(Color.WHITE);

            g.setStroke(new BasicStroke(2));

            g.drawRoundRect(plusX, btnY, btnSize, btnSize, 8, 8);

            g.setFont(new Font("Arial", Font.BOLD, 28));

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

        

        // Draw animated gradient background

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(20, 25, 40), new Color(30, 35, 50), new Color(40, 45, 60)});

        

        // Title

        g.setFont(new Font("Arial", Font.BOLD, 42));

        String title = "JOURNEY MAP";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 50;

        

        // Title shadow and gradient

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 3, titleY + 3);

        GradientPaint titleGrad = new GradientPaint(titleX, titleY - 30, new Color(180, 142, 173), titleX, titleY + 20, new Color(235, 203, 139));

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Show "RESUME AVAILABLE" indicator if there's a saved game

        if (hasSavedGame && selectedLevel == savedLevel) {

            g.setFont(new Font("Arial", Font.BOLD, 24));

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

                g.setColor(new Color(100, 180, 100)); // Completed

            } else if (i == currentLevel) {

                g.setColor(new Color(100, 200, 255)); // Current

            } else {

                g.setColor(new Color(60, 60, 70)); // Locked

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

        g.setColor(new Color(50, 55, 65));

        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g.drawLine(0, centerY, width, centerY);

        

        // Draw arrow indicators on the sides

        if (selectedLevel > 1) {

            // Left arrow

            g.setFont(new Font("Arial", Font.BOLD, 50));

            float arrowPulse = (float)(0.5 + 0.5 * Math.sin(time * 4));

            g.setColor(new Color(150, 150, 160, (int)(100 + 100 * arrowPulse)));

            g.drawString("<", 15, centerY + 18);

        }

        if (selectedLevel < 28) {

            // Right arrow

            g.setFont(new Font("Arial", Font.BOLD, 50));

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

            

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            

            // Selection glow for center

            if (isSelected && distFromCenter < 0.3) {

                float glowPulse = (float)(0.3 + 0.2 * Math.sin(time * 4));

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glowPulse * alpha));

                Color glowColor = isCurrent ? new Color(100, 255, 100) : 

                                  isCompleted ? new Color(100, 180, 255) : new Color(255, 150, 100);

                g.setColor(glowColor);

                g.fillOval(x - nodeRadius - 25, centerY - nodeRadius - 25, (nodeRadius + 25) * 2, (nodeRadius + 25) * 2);

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            }

            

            // Node shadow

            g.setColor(new Color(0, 0, 0, (int)(80 * alpha)));

            g.fillOval(x - nodeRadius + 5, centerY - nodeRadius + 5, nodeRadius * 2, nodeRadius * 2);

            

            // Node fill color

            if (isMegaBoss) {

                if (isCompleted) {

                    GradientPaint grad = new GradientPaint(x - nodeRadius, centerY - nodeRadius, new Color(100, 50, 120), 

                                                           x + nodeRadius, centerY + nodeRadius, new Color(140, 80, 160));

                    g.setPaint(grad);

                } else if (isCurrent) {

                    GradientPaint grad = new GradientPaint(x - nodeRadius, centerY - nodeRadius, new Color(150, 80, 180), 

                                                           x + nodeRadius, centerY + nodeRadius, new Color(200, 120, 220));

                    g.setPaint(grad);

                } else {

                    g.setColor(new Color(50, 40, 60));

                }

            } else {

                if (isCompleted) {

                    GradientPaint grad = new GradientPaint(x - nodeRadius, centerY - nodeRadius, new Color(50, 100, 60), 

                                                           x + nodeRadius, centerY + nodeRadius, new Color(70, 130, 80));

                    g.setPaint(grad);

                } else if (isCurrent) {

                    GradientPaint grad = new GradientPaint(x - nodeRadius, centerY - nodeRadius, new Color(60, 150, 80), 

                                                           x + nodeRadius, centerY + nodeRadius, new Color(80, 200, 100));

                    g.setPaint(grad);

                } else {

                    g.setColor(new Color(45, 45, 50));

                }

            }

            g.fillOval(x - nodeRadius, centerY - nodeRadius, nodeRadius * 2, nodeRadius * 2);

            

            // Node border

            if (isSelected && distFromCenter < 0.3) {

                g.setColor(Color.WHITE);

                g.setStroke(new BasicStroke(5));

            } else if (isCurrent) {

                g.setColor(new Color(150, 255, 150));

                g.setStroke(new BasicStroke(3));

            } else if (isCompleted) {

                g.setColor(new Color(100, 160, 100));

                g.setStroke(new BasicStroke(2));

            } else {

                g.setColor(new Color(70, 70, 80));

                g.setStroke(new BasicStroke(2));

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

                    

                    // Glow effect for unlocked planes using radial gradient

                    if (!isLocked) {

                        float glowIntensity = (float)(0.5 + 0.3 * Math.sin(time * 0.8));

                        

                        Color glowColor;

                        if (isMegaBoss) {

                            glowColor = new Color(200, 150, 255); // Purple glow for mega bosses

                        } else if (isCompleted) {

                            glowColor = new Color(100, 255, 150); // Green glow for completed

                        } else if (isCurrent) {

                            glowColor = new Color(100, 200, 255); // Blue glow for current

                        } else {

                            glowColor = new Color(255, 255, 200); // Yellow glow for available

                        }

                        

                        // Draw smooth radial gradient glow (fixed position, doesn't bounce)

                        AffineTransform glowTransform = g.getTransform();

                        int fixedGlowY = centerY - nodeRadius - spriteHeight - 110; // Fixed position without bounce

                        g.translate(spriteX, fixedGlowY + spriteHeight / 2);

                        

                        float glowRadius = Math.max(spriteWidth, spriteHeight) * 0.7f;

                        Color transparent = new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), 0);

                        Color center = new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), (int)(180 * glowIntensity * alpha));

                        

                        RadialGradientPaint gradient = new RadialGradientPaint(

                            0, 0, glowRadius,

                            new float[]{0.0f, 0.6f, 1.0f},

                            new Color[]{center, new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(), (int)(100 * glowIntensity * alpha)), transparent}

                        );

                        g.setPaint(gradient);

                        g.fillOval((int)(-glowRadius), (int)(-glowRadius), (int)(glowRadius * 2), (int)(glowRadius * 2));

                        

                        g.setTransform(glowTransform);

                    }

                    

                    // Set alpha - dim for locked levels

                    float spriteAlpha = isLocked ? 0.3f : 1.0f;

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, spriteAlpha * alpha));

                    

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

                            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

                            

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

                        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

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

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                }

            } catch (Exception e) {

                // If sprite fails to load, skip it

            }

            

            // Level number - scale font with node size

            int fontSize = (int)(48 * scale);

            g.setFont(new Font("Arial", Font.BOLD, fontSize));

            String levelNum = String.valueOf(level);

            fm = g.getFontMetrics();

            int textX = x - fm.stringWidth(levelNum) / 2;

            int textY = centerY + fm.getAscent() / 2 - 2;

            

            g.setColor(new Color(0, 0, 0, 100));

            g.drawString(levelNum, textX + 1, textY + 1);

            

            if (isLocked) {

                g.setColor(new Color(80, 80, 85));

            } else {

                g.setColor(Color.WHITE);

            }

            g.drawString(levelNum, textX, textY);

            

            // Mega boss star above node

            if (isMegaBoss && !isLocked) {

                int starSize = (int)(24 * scale);

                g.setFont(new Font("Arial", Font.BOLD, starSize));

                g.setColor(new Color(255, 215, 0));

                String crown = "*";

                fm = g.getFontMetrics();

                g.drawString(crown, x - fm.stringWidth(crown) / 2, centerY - nodeRadius - 10);

            }

            

            // Checkmark for completed

            if (isCompleted) {

                int checkSize = (int)(22 * scale);

                g.setFont(new Font("Arial", Font.BOLD, checkSize));

                g.setColor(new Color(100, 255, 100));

                String check = "V";

                fm = g.getFontMetrics();

                g.drawString(check, x + nodeRadius - checkSize / 2, centerY - nodeRadius + checkSize);

            }

            

            // Lock icon for locked

            if (isLocked) {

                int lockSize = (int)(18 * scale);

                g.setFont(new Font("Arial", Font.BOLD, lockSize));

                g.setColor(new Color(100, 100, 110));

                String lock = "[L]";

                fm = g.getFontMetrics();

                g.drawString(lock, x - fm.stringWidth(lock) / 2, centerY + nodeRadius + lockSize + 5);

            }

            

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        }

        

        // Draw info panel for selected level at bottom

        drawLevelInfoPanel(g, width, height, selectedLevel, currentLevel, time);

    }

    

    private void drawLevelInfoPanel(Graphics2D g, int width, int height, int selectedLevel, int currentLevel, double time) {

        int panelHeight = 200;

        int panelY = height - panelHeight - 30;

        int panelWidth = 500;

        int panelX = (width - panelWidth) / 2;

        

        // Panel background with rounded corners

        g.setColor(new Color(25, 30, 40, 240));

        g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 25, 25);

        

        // Border glow based on status

        boolean isCompleted = selectedLevel < currentLevel;

        boolean isCurrent = selectedLevel == currentLevel;

        boolean isMegaBoss = selectedLevel % 3 == 0;

        

        Color borderColor = isCompleted ? new Color(80, 160, 80) :

                           isCurrent ? new Color(100, 200, 255) :

                           new Color(70, 70, 80);

        g.setColor(borderColor);

        g.setStroke(new BasicStroke(3));

        g.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 25, 25);

        

        // Boss name - centered

        String bossName = GameData.getBossName(selectedLevel);

        g.setFont(new Font("Arial", Font.BOLD, 32));

        FontMetrics fm = g.getFontMetrics();

        int nameX = panelX + (panelWidth - fm.stringWidth(bossName)) / 2;

        

        if (isMegaBoss) {

            GradientPaint nameGrad = new GradientPaint(nameX, panelY + 40, new Color(200, 150, 255), 

                                                        nameX + fm.stringWidth(bossName), panelY + 40, new Color(255, 200, 100));

            g.setPaint(nameGrad);

        } else {

            g.setColor(new Color(230, 235, 245));

        }

        g.drawString(bossName, nameX, panelY + 45);

        

        // Level type label - centered

        g.setFont(new Font("Arial", Font.PLAIN, 16));

        g.setColor(isMegaBoss ? new Color(255, 200, 100) : new Color(140, 150, 170));

        String typeLabel = isMegaBoss ? "* MEGA BOSS - Level " + selectedLevel : "Level " + selectedLevel;

        fm = g.getFontMetrics();

        g.drawString(typeLabel, panelX + (panelWidth - fm.stringWidth(typeLabel)) / 2, panelY + 70);

        

        // Status and stats info

        g.setFont(new Font("Arial", Font.BOLD, 16));

        int infoY = panelY + 100;

        

        if (isCompleted) {

            g.setColor(new Color(100, 200, 100));

            String status = "V DEFEATED";

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

            g.setFont(new Font("Arial", Font.PLAIN, 13));

            g.setColor(new Color(160, 170, 180));

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

            if (stats.getLivesUsed() > 0) {

                if (line4.length() > 0) line4.append("  -  ");

                line4.append("Lives: ").append(stats.getLivesUsed());

            }

            if (line4.length() > 0) {

                fm = g.getFontMetrics();

                g.drawString(line4.toString(), panelX + (panelWidth - fm.stringWidth(line4.toString())) / 2, infoY);

            }

        } else if (isCurrent) {

            // Animated "READY" text

            float pulse = (float)(0.7 + 0.3 * Math.sin(time * 5));

            g.setColor(new Color((int)(100 * pulse + 100), (int)(200 * pulse + 55), (int)(100 * pulse + 100)));

            String startText = "> PRESS SPACE TO START <";

            fm = g.getFontMetrics();

            g.drawString(startText, panelX + (panelWidth - fm.stringWidth(startText)) / 2, infoY);

        } else {

            g.setColor(new Color(120, 120, 130));

            String lockText = "[L] LOCKED";

            fm = g.getFontMetrics();

            g.drawString(lockText, panelX + (panelWidth - fm.stringWidth(lockText)) / 2, infoY);

        }

        

        // Navigation hints at very bottom

        g.setFont(new Font("Arial", Font.PLAIN, 14));

        g.setColor(new Color(100, 110, 130));

        String navHint = "<- -> or CLICK  Navigate    SPACE or CLICK  Start    ESC  Back";

        fm = g.getFontMetrics();

        g.drawString(navHint, panelX + (panelWidth - fm.stringWidth(navHint)) / 2, panelY + panelHeight - 15);

    }

    

    public void drawRiskContract(Graphics2D g, int width, int height, int selectedContract, 

                                  String[] contractNames, String[] contractDescriptions, 

                                  double[] contractMultipliers, double time, int level) {

        // Draw animated background

        Color[] colors = getLevelGradientColors(level);

        drawAnimatedGradient(g, width, height, time, colors);

        

        // Dark overlay for contrast

        g.setColor(new Color(0, 0, 0, 180));

        g.fillRect(0, 0, width, height);

        

        // Title

        g.setFont(new Font("Arial", Font.BOLD, 48));

        String title = "RISK CONTRACT";

        FontMetrics titleFm = g.getFontMetrics();

        int titleX = (width - titleFm.stringWidth(title)) / 2;

        

        // Title glow

        float glowPulse = (float)(0.4 + 0.2 * Math.sin(time * 2));

        g.setColor(new Color(255, 100, 100, (int)(100 * glowPulse)));

        g.drawString(title, titleX - 3, 83);

        g.drawString(title, titleX + 3, 77);

        

        g.setColor(new Color(255, 150, 150));

        g.drawString(title, titleX, 80);

        

        // Subtitle

        g.setFont(new Font("Arial", Font.PLAIN, 20));

        String subtitle = "Choose your challenge modifier for Level " + level;

        FontMetrics subFm = g.getFontMetrics();

        g.setColor(new Color(200, 200, 200));

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

            g.setColor(new Color(0, 0, 0, 100));

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

                g.setColor(new Color(40, 45, 55));

            }

            g.fillRoundRect(cardX + offsetX, cardY + offsetY, scaledWidth, scaledHeight, 15, 15);

            

            // Card border

            g.setColor(isSelected ? new Color(255, 255, 200) : new Color(80, 85, 95));

            g.setStroke(new BasicStroke(isSelected ? 3 : 2));

            g.drawRoundRect(cardX + offsetX, cardY + offsetY, scaledWidth, scaledHeight, 15, 15);

            

            // Contract icon/symbol - draw custom graphics (larger)

            int iconY = cardY + offsetY + 65;

            int iconCenterX = cardX + offsetX + scaledWidth / 2;

            Color iconColor = i == 0 ? new Color(100, 180, 100) :

                             i == 1 ? new Color(255, 100, 100) :

                             i == 2 ? new Color(100, 150, 255) : new Color(255, 180, 100);

            g.setColor(isSelected ? iconColor : new Color(100, 100, 100));

            g.setStroke(new BasicStroke(4));

            

            if (i == 0) {

                // No Contract - Circle with checkmark (larger)

                g.drawOval(iconCenterX - 35, iconY - 50, 70, 70);

                g.setStroke(new BasicStroke(5));

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

                g.setStroke(new BasicStroke(4));

                g.drawLine(iconCenterX - 21, iconY - 28, iconCenterX + 21, iconY + 14);

                g.drawLine(iconCenterX + 21, iconY - 28, iconCenterX - 21, iconY + 14);

            }

            g.setStroke(new BasicStroke(1));

            

            // Contract name

            g.setFont(new Font("Arial", Font.BOLD, 24));

            FontMetrics nameFm = g.getFontMetrics();

            g.setColor(isSelected ? Color.WHITE : new Color(150, 150, 150));

            g.drawString(contractNames[i], cardX + offsetX + (scaledWidth - nameFm.stringWidth(contractNames[i])) / 2, 

                        cardY + offsetY + 120);

            

            // Multiplier

            g.setFont(new Font("Arial", Font.BOLD, 36));

            String multiplier = i == 0 ? "--" : String.format("%.2fx", contractMultipliers[i]);

            FontMetrics multFm = g.getFontMetrics();

            g.setColor(i == 0 ? new Color(150, 150, 150) : new Color(255, 215, 0));

            g.drawString(multiplier, cardX + offsetX + (scaledWidth - multFm.stringWidth(multiplier)) / 2, 

                        cardY + offsetY + 170);

            

            // Description (word wrapped)

            g.setFont(new Font("Arial", Font.PLAIN, 16));

            g.setColor(isSelected ? new Color(200, 200, 200) : new Color(120, 120, 120));

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

        g.setFont(new Font("Arial", Font.PLAIN, 18));

        g.setColor(new Color(150, 150, 150));

        String hint = "<  > or CLICK  Select   |   SPACE or CLICK  Confirm   |   ESC  Back";

        FontMetrics hintFm = g.getFontMetrics();

        g.drawString(hint, (width - hintFm.stringWidth(hint)) / 2, height - 40);

        

        // Warning for risky contracts

        if (selectedContract > 0) {

            g.setFont(new Font("Arial", Font.BOLD, 16));

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

        

        // Draw animated background

        Color[] colors = getLevelGradientColors(level);

        drawAnimatedGradient(g, width, height, time, colors);

        

        // Dark overlay for contrast

        g.setColor(new Color(0, 0, 0, 180));

        g.fillRect(0, 0, width, height);

        

        // Title

        g.setFont(new Font("Arial", Font.BOLD, 56));

        String title = isResume ? "RESUME LEVEL " + level + "?" : "START LEVEL " + level + "?";

        FontMetrics titleFm = g.getFontMetrics();

        int titleX = (width - titleFm.stringWidth(title)) / 2;

        

        // Title glow

        float glowPulse = (float)(0.4 + 0.2 * Math.sin(time * 2));

        g.setColor(new Color(235, 203, 139, (int)(100 * glowPulse)));

        g.drawString(title, titleX - 2, height / 2 - 48);

        g.drawString(title, titleX + 2, height / 2 - 52);

        

        g.setColor(new Color(235, 203, 139));

        g.drawString(title, titleX, height / 2 - 50);

        

        // Yes and No buttons

        int buttonWidth = 150;

        int buttonHeight = 60;

        int buttonSpacing = 50;

        int totalWidth = 2 * buttonWidth + buttonSpacing;

        int startX = (width - totalWidth) / 2;

        int buttonY = height / 2 + 50;

        

        // Draw Yes button

        boolean yesSelected = (selectedConfirmItem == 0);

        Color yesColor = yesSelected ? new Color(163, 190, 140) : new Color(80, 90, 70);

        Color yesHover = new Color(180, 210, 160);

        

        // Button shadow

        g.setColor(new Color(0, 0, 0, 100));

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

        g.setStroke(new BasicStroke(2));

        g.drawRoundRect(startX, buttonY, buttonWidth, buttonHeight, 10, 10);

        

        // Button text

        g.setFont(new Font("Arial", Font.BOLD, 32));

        FontMetrics yesFm = g.getFontMetrics();

        g.setColor(Color.WHITE);

        String yesText = "YES";

        g.drawString(yesText, startX + (buttonWidth - yesFm.stringWidth(yesText)) / 2, 

                     buttonY + (buttonHeight + yesFm.getAscent()) / 2 - 2);

        

        // Draw No button

        int noButtonX = startX + buttonWidth + buttonSpacing;

        boolean noSelected = (selectedConfirmItem == 1);

        Color noColor = noSelected ? new Color(191, 97, 106) : new Color(90, 50, 60);

        Color noHover = new Color(220, 120, 130);

        

        // Button shadow

        g.setColor(new Color(0, 0, 0, 100));

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

        g.setStroke(new BasicStroke(2));

        g.drawRoundRect(noButtonX, buttonY, buttonWidth, buttonHeight, 10, 10);

        

        // Button text

        g.setFont(new Font("Arial", Font.BOLD, 32));

        FontMetrics noFm = g.getFontMetrics();

        g.setColor(Color.WHITE);

        String noText = "NO";

        g.drawString(noText, noButtonX + (buttonWidth - noFm.stringWidth(noText)) / 2, 

                     buttonY + (buttonHeight + noFm.getAscent()) / 2 - 2);

        

        // Controls hint

        g.setFont(new Font("Arial", Font.PLAIN, 18));

        g.setColor(new Color(150, 150, 150));

        String hint = "<  > or CLICK  Select   |   SPACE or CLICK  Confirm   |   ESC  Back";

        FontMetrics hintFm = g.getFontMetrics();

        g.drawString(hint, (width - hintFm.stringWidth(hint)) / 2, height - 40);

    }

    

    public void drawGame(Graphics2D g, int width, int height, Player player, Boss boss, List<Bullet> bullets, List<Particle> particles, List<BeamAttack> beamAttacks, int level, double time, boolean bossVulnerable, double invulnerabilityTimer, int dodgeCombo, boolean showCombo, boolean bossDeathAnimation, double bossDeathScale, double bossDeathRotation, double gameTime, int fps, boolean shieldActive, boolean playerInvincible, int bossHitCount, double cameraX, double cameraY, boolean introPanActive, double bossFlashTimer, double screenFlashTimer, ComboSystem comboSystem, List<DamageNumber> damageNumbers, boolean bossIntroActive, String bossIntroText, double bossIntroTimer, boolean isPaused, int selectedPauseItem, List<Achievement> pendingAchievements, double achievementNotificationTimer, boolean resurrectionAnimation, double resurrectionTimer, double resurrectionScale, double resurrectionGlow, int riskContractType, boolean riskContractActive, double stoppedMovingTimer, boolean unpauseCountdownActive, double unpauseCountdownTimer, double itemReadyFlickerTimer, double itemCompleteFlashTimer, double achievementFlashTimer, double bossIntroFlashTimer, double countdownFlashTimer, double bossHitFlashTimer, double typePurgeFlashTimer, Color typePurgeFlashColor, java.util.List<double[]> moneyCircles, double moneyCircleRadius, double frostBeamAngle, double frostBeamProgress, double frostBeamStopDistance, boolean frostBeamRetracting, double frostBeamRetractPhase, int shieldHits, double shieldOrbitAngle, double bossIntroPlayerX, double bossIntroBossX, double bossIntroVsScale, double bossIntroFlash, int bossIntroPhase, List<Particle> introParticles) {

        // Draw background based on mode setting

        if (Game.backgroundMode == 0) {

            // Gradient mode

            Color[] colors = getLevelGradientColors(level);

            drawAnimatedGradient(g, width, height, time, colors);

        } else if (Game.backgroundMode == 1 && backgroundsLoaded) {

            // Parallax mode

            drawParallaxBackground(g, width, height, level, time);

        } else if (Game.backgroundMode == 2 && backgroundsLoaded) {

            // Static image mode (first layer only)

            drawStaticBackground(g, width, height, level);

        } else {

            // Fallback to gradient if images not loaded

            Color[] colors = getLevelGradientColors(level);

            drawAnimatedGradient(g, width, height, time, colors);

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

            

            Graphics2D g2d = (Graphics2D) g.create();

            

            // Draw combined transparent green fill

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

            g2d.setColor(new Color(50, 200, 80)); // Green

            g2d.fill(combinedArea);

            

            // Draw combined outer ring

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));

            g2d.setColor(new Color(50, 200, 80)); // Green

            g2d.setStroke(new BasicStroke(2));

            g2d.draw(combinedArea);

            

            // Find connected groups of circles (overlapping = centers within 2*radius)

            // Each group gets its own $ sign at its center of mass

            boolean[] visited = new boolean[moneyCircles.size()];

            g2d.setFont(new Font("Arial", Font.BOLD, 36));

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

            g2d.setColor(new Color(50, 200, 80)); // Green

            FontMetrics fm = g2d.getFontMetrics();

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

                g2d.drawString(symbol, (int)(gx - fm.stringWidth(symbol) / 2), (int)(gy + fm.getAscent() / 3));

            }

            

            g2d.dispose();

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

        

        // Draw particles (behind sprites) - use snapshot to avoid ConcurrentModificationException

        // Skip MONEY_SIGN particles here - they'll be drawn on top later

        java.util.List<Particle> particleSnapshot = new java.util.ArrayList<>(particles);

        for (Particle particle : particleSnapshot) {

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

                    

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                    g.setColor(AFTERIMAGE_COLOR);

                    int size = 12 - i * 2;

                    g.fillOval((int)trailX - size/2, (int)trailY - size/2, size, size);

                }

                g.setComposite(ALPHA_FULL);

            }

        }

        

        // Draw player (only if not in death animation, and not during boss intro cinematic)

        if (player != null && !bossIntroActive) {

            // Draw resurrection glow if animation is active

            if (resurrectionAnimation) {

                double glowRadius = 80 * resurrectionScale;

                int glowAlpha = (int)(255 * resurrectionGlow);

                

                // Outer golden glow

                g.setColor(new Color(255, 215, 0, Math.max(0, Math.min(255, glowAlpha / 2))));

                g.fillOval((int)(player.getX() - glowRadius), 

                          (int)(player.getY() - glowRadius), 

                          (int)(glowRadius * 2), (int)(glowRadius * 2));

                

                // Inner bright glow

                double innerRadius = glowRadius * 0.6;

                g.setColor(new Color(255, 255, 200, Math.max(0, Math.min(255, glowAlpha))));

                g.fillOval((int)(player.getX() - innerRadius), 

                          (int)(player.getY() - innerRadius), 

                          (int)(innerRadius * 2), (int)(innerRadius * 2));

                

                // Draw resurrection text

                if (resurrectionTimer > 60) { // Show text in first half of animation

                    float textAlpha = Math.min(1.0f, (float)(resurrectionTimer - 60) / 60);

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, textAlpha));

                    g.setFont(new Font("Arial", Font.BOLD, 36));

                    g.setColor(new Color(255, 215, 0));

                    String resText = "EXTRA LIFE!";

                    FontMetrics fm = g.getFontMetrics();

                    g.drawString(resText, (int)(player.getX() - fm.stringWidth(resText) / 2), (int)(player.getY() - 80));

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

                }

            }

            

            player.draw(g);

            

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

                    g.setColor(new Color(60, 180, 255, 50));

                    g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(px - glowR, py - glowR, glowR * 2, glowR * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Mid glow arc ===

                    g.setColor(new Color(80, 200, 255, 90));

                    g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Main shield body - thick curved line ===

                    g.setColor(new Color(100, 210, 255, 200));

                    g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Bright inner edge highlight ===

                    int innerR = orbitRadius - 3;

                    g.setColor(new Color(200, 240, 255, 220));

                    g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(px - innerR, py - innerR, innerR * 2, innerR * 2, (int)startAngle, (int)arcSpan);

                    

                    // === Bright tips at the ends of each arc ===

                    double tipAngle1 = Math.toRadians(startAngle);

                    double tipAngle2 = Math.toRadians(startAngle + arcSpan);

                    int tipSize = 5;

                    g.setColor(new Color(220, 250, 255, 240));

                    g.fillOval(px + (int)(Math.cos(tipAngle1) * orbitRadius) - tipSize/2,

                              py - (int)(Math.sin(tipAngle1) * orbitRadius) - tipSize/2, tipSize, tipSize);

                    g.fillOval(px + (int)(Math.cos(tipAngle2) * orbitRadius) - tipSize/2,

                              py - (int)(Math.sin(tipAngle2) * orbitRadius) - tipSize/2, tipSize, tipSize);

                }

                

                // Subtle inner ring connecting all shields

                g.setColor(new Color(100, 200, 255, 25));

                g.setStroke(new BasicStroke(1.5f));

                g.drawOval(px - orbitRadius, py - orbitRadius, orbitRadius * 2, orbitRadius * 2);

            }

            

            // Draw invincibility glow

            if (playerInvincible) {

                int glowRadius = 40;

                int pulseSize = (int)(Math.sin(time * 0.15) * 5);

                

                // Pulsing gold glow

                g.setColor(new Color(235, 203, 139, 80));

                g.fillOval((int)player.getX() - glowRadius - pulseSize, 

                          (int)player.getY() - glowRadius - pulseSize, 

                          (glowRadius + pulseSize) * 2, (glowRadius + pulseSize) * 2);

                

                g.setColor(new Color(255, 255, 200, 120));

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

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

            g2d.setColor(new Color(255, 100, 0));

            double size = boss.getSize() * bossDeathScale;

            g2d.fillOval((int)(boss.getX() - size/2), (int)(boss.getY() - size/2), (int)size, (int)size);

            

            g2d.dispose();

        } else {

            // Draw ALL indicators UNDER boss sprite

            

            // Draw soft bloom/glow effect UNDER boss (layered for smooth falloff)

            if (Game.enableBloom) {

                Graphics2D bloomG = (Graphics2D) g.create();

                

                // Choose bloom color based on state

                Color bloomColor;

                if (bossVulnerable) {

                    // Golden/white bloom when vulnerable - indicates "attack now!"

                    float pulse = 0.7f + 0.3f * (float)Math.sin(time * 6);

                    bloomColor = new Color(255, 240, 200); // Warm golden white

                    

                    // Draw layered bloom (larger = more transparent for soft falloff)

                    for (int i = 4; i > 0; i--) {

                        float alpha = (0.08f * pulse) / i;

                        bloomG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(alpha, 1.0f)));

                        bloomG.setColor(bloomColor);

                        double glowSize = boss.getSize() * (1.0 + i * 0.4);

                        bloomG.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                    }

                } else {

                    // Subtle cool bloom when invulnerable

                    bloomColor = new Color(100, 150, 200);

                    for (int i = 3; i > 0; i--) {

                        float alpha = 0.04f / i;

                        bloomG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                        bloomG.setColor(bloomColor);

                        double glowSize = boss.getSize() * (1.0 + i * 0.3);

                        bloomG.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                    }

                }

                bloomG.dispose();

            }

            

            // Draw attack phase glow effect UNDER boss

            if (boss.isAssaultPhase()) {

                // Red pulsing glow during assault

                Graphics2D g2d = (Graphics2D) g.create();

                float pulseAlpha = 0.15f + (float)(Math.sin(time * 8) * 0.08f);

                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulseAlpha));

                g2d.setColor(new Color(255, 50, 50));

                double glowSize = boss.getSize() * 1.6;

                g2d.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                g2d.dispose();

            } else {

                // Blue calm glow during recovery

                Graphics2D g2d = (Graphics2D) g.create();

                float pulseAlpha = 0.1f + (float)(Math.sin(time * 3) * 0.05f);

                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulseAlpha));

                g2d.setColor(new Color(80, 150, 255));

                double glowSize = boss.getSize() * 1.4;

                g2d.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);

                g2d.dispose();

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

                

                int bossShieldRadius = (int)(boss.getSize() * 1.4); // Bigger - fully outside the boss

                double TWO_PI_B = Math.PI * 2;

                double bossArcSpan = 35; // Degrees each arc covers (smaller per segment)

                int numArcs = 8; // 8 shield arcs around boss

                double bossShieldAngle = time * 0.12; // Much faster rotation

                int bx = (int)boss.getX();

                int by = (int)boss.getY();

                

                // Color transitions from blue (full) to red/yellow (low time)

                int sr = (int)(100 + 155 * (1 - timeRatio)); // 100 -> 255

                int sg = (int)(210 * timeRatio); // 210 -> 0

                int sb = (int)(255 * timeRatio); // 255 -> 0

                

                for (int i = 0; i < numArcs; i++) {

                    double angleRad = bossShieldAngle + (i * TWO_PI_B / numArcs);

                    double angleDeg = Math.toDegrees(angleRad);

                    double startAngle = angleDeg - bossArcSpan / 2;

                    

                    // Outer glow arc

                    int glowPulse = (int)(Math.sin(time * 0.08 + i * 1.3) * 5);

                    int glowR = bossShieldRadius + 12 + glowPulse;

                    g.setColor(new Color(sr, sg, sb, (int)(50 * shieldAlpha)));

                    g.setStroke(new BasicStroke(18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(bx - glowR, by - glowR, glowR * 2, glowR * 2, (int)startAngle, (int)bossArcSpan);

                    

                    // Mid glow arc

                    g.setColor(new Color(sr, sg, sb, (int)(90 * shieldAlpha)));

                    g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(bx - bossShieldRadius, by - bossShieldRadius, bossShieldRadius * 2, bossShieldRadius * 2, (int)startAngle, (int)bossArcSpan);

                    

                    // Main shield body

                    g.setColor(new Color(sr, sg, sb, (int)(200 * shieldAlpha)));

                    g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(bx - bossShieldRadius, by - bossShieldRadius, bossShieldRadius * 2, bossShieldRadius * 2, (int)startAngle, (int)bossArcSpan);

                    

                    // Inner edge highlight

                    int innerR = bossShieldRadius - 5;

                    g.setColor(new Color(Math.min(255, sr + 100), Math.min(255, sg + 40), Math.min(255, sb + 40), (int)(220 * shieldAlpha)));

                    g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.drawArc(bx - innerR, by - innerR, innerR * 2, innerR * 2, (int)startAngle, (int)bossArcSpan);

                    

                    // Bright tips at arc ends

                    double tipAngle1 = Math.toRadians(startAngle);

                    double tipAngle2 = Math.toRadians(startAngle + bossArcSpan);

                    int tipSize = 8;

                    g.setColor(new Color(255, Math.min(255, sg + 40), Math.min(255, sb + 40), (int)(240 * shieldAlpha)));

                    g.fillOval(bx + (int)(Math.cos(tipAngle1) * bossShieldRadius) - tipSize/2,

                              by - (int)(Math.sin(tipAngle1) * bossShieldRadius) - tipSize/2, tipSize, tipSize);

                    g.fillOval(bx + (int)(Math.cos(tipAngle2) * bossShieldRadius) - tipSize/2,

                              by - (int)(Math.sin(tipAngle2) * bossShieldRadius) - tipSize/2, tipSize, tipSize);

                }

                

                // Subtle inner ring connecting all arcs

                g.setColor(new Color(sr, sg, sb, (int)(25 * shieldAlpha)));

                g.setStroke(new BasicStroke(1.5f));

                g.drawOval(bx - bossShieldRadius, by - bossShieldRadius, bossShieldRadius * 2, bossShieldRadius * 2);

            }

            

            // Normal boss drawing (AFTER all indicators so boss appears on top)

            boss.draw(g);

            

            // Draw shockwave during recovery phase (circular arc directed at player)

            if (boss.isShockwaveActive()) {

                Graphics2D g2d = (Graphics2D) g.create();

                double radius = boss.getShockwaveRadius();

                double angle = boss.getShockwaveAngle();

                double coneAngle = Math.PI / 2; // 90 degree cone

                

                int bossX = (int)boss.getX();

                int bossY = (int)boss.getY();

                

                // Convert angle to degrees for arc drawing

                // atan2 uses screen coordinates (Y down), but drawArc uses math coordinates (Y up)

                // So we negate the angle to flip it correctly

                double adjustedAngle = -Math.toDegrees(angle);

                int startAngleDeg = (int)(adjustedAngle - Math.toDegrees(coneAngle/2));

                int arcAngleDeg = (int)Math.toDegrees(coneAngle);

                

                // Draw multiple expanding circular arcs

                for (int i = 0; i < 5; i++) {

                    double ringRadius = radius - (i * 20);

                    if (ringRadius > 0) {

                        // Draw filled arc background with higher opacity for visibility

                        float bgAlpha = (float)((1.0 - (ringRadius / 250.0)) * 0.4f) / (i * 0.3f + 1);

                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, bgAlpha));

                        g2d.setColor(new Color(150, 200, 255));

                        g2d.fillArc((int)(bossX - ringRadius), (int)(bossY - ringRadius), 

                                   (int)(ringRadius * 2), (int)(ringRadius * 2), 

                                   startAngleDeg, arcAngleDeg);

                        

                        // Draw arc outline with much higher opacity

                        float alpha = (float)((1.0 - (ringRadius / 250.0)) * 1.2f) / (i * 0.2f + 1);

                        alpha = Math.min(alpha, 1.0f); // Clamp to max 1.0

                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                        g2d.setColor(new Color(100, 180, 255));

                        g2d.setStroke(new BasicStroke(12 - i * 1.5f)); // Slightly thicker

                        g2d.drawArc((int)(bossX - ringRadius), (int)(bossY - ringRadius), 

                                   (int)(ringRadius * 2), (int)(ringRadius * 2), 

                                   startAngleDeg, arcAngleDeg);

                    }

                }

                g2d.dispose();

            }

            

            // Boss damage flash effect

            if (bossFlashTimer > 0) {

                Graphics2D g2d = (Graphics2D) g.create();

                float flashAlpha = (float)bossFlashTimer / 12.0f * 0.5f; // Fade out over 12 frames

                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

                g2d.setColor(Color.WHITE);

                double size = boss.getSize() * 1.2;

                g2d.fillOval((int)(boss.getX() - size/2), (int)(boss.getY() - size/2), (int)size, (int)size);

                g2d.dispose();

            }

        }

        } // End boss != null check

        

        // Draw bullets (including warnings for inactive bullets)

        for (int i = 0; i < bullets.size(); i++) {

            Bullet bullet = bullets.get(i);

            if (bullet != null) {

                bullet.draw(g);

            }

        }

        

        // Draw MONEY_SIGN particles ON TOP of player and bullets

        for (Particle particle : particleSnapshot) {

            if (particle != null && particle.isAlive() && particle.getType() == Particle.ParticleType.MONEY_SIGN) {

                particle.draw(g);

            }

        }

        

        // Draw hitboxes for debugging if enabled

        if (Game.enableHitboxes) {

            Graphics2D g2d = (Graphics2D) g.create();

            g2d.setStroke(new BasicStroke(2));

            

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

                g2d.setStroke(new BasicStroke(2));

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

            g2d.setStroke(new BasicStroke(1));

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

        

        // Restore original transform and apply inverse zoom for UI elements

        // This makes UI stay the same size regardless of game zoom

        g.setTransform(originalTransform);

        double inverseZoom = 1.0 / Game.cameraZoom;

        g.translate(width / 2, height / 2);

        g.scale(inverseZoom, inverseZoom);

        g.translate(-width / 2, -height / 2);

        

        // Draw boss health bar at bottom

        if (boss != null) {

            int barWidth = 600;

            int barHeight = 40;

            

            // Apply parallax effect - boss bar moves less with camera (30% of camera movement)

            int parallaxOffsetX = (int)(cameraX * 0.3);

            int parallaxOffsetY = (int)(cameraY * 0.3);

            

            int barX = (width - barWidth) / 2 + parallaxOffsetX;

            int barY = height - 110 + parallaxOffsetY;

            

            // Boss name and type

            String bossName = boss.getVehicleName();

            String bossType = boss.isMegaBoss() ? "[MEGA BOSS]" : "[MINI BOSS]";

            

            // Background panel with shadow

            g.setColor(new Color(0, 0, 0, 100));

            g.fillRoundRect(barX + 3, barY + 3, barWidth, barHeight + 45, 15, 15);

            g.setColor(new Color(20, 20, 30, 200));

            g.fillRoundRect(barX, barY, barWidth, barHeight + 45, 15, 15);

            

            // Boss type label

            g.setFont(new Font("Arial", Font.BOLD, 14));

            FontMetrics fm = g.getFontMetrics();

            Color typeColor = boss.isMegaBoss() ? new Color(255, 50, 50) : new Color(100, 200, 100);

            g.setColor(typeColor);

            g.drawString(bossType, barX + 10, barY + 18);

            

            // Boss name

            g.setFont(new Font("Arial", Font.BOLD, 18));

            fm = g.getFontMetrics();

            g.setColor(boss.isMegaBoss() ? new Color(255, 215, 0) : Color.WHITE);

            g.drawString(bossName, barX + 10, barY + 38);

            

            // Health bar background

            g.setColor(new Color(60, 60, 60));

            g.fillRoundRect(barX + 10, barY + 45, barWidth - 20, 15, 8, 8);

            

            // Health bar fill (always full - boss has no health system, just vulnerability window)

            GradientPaint healthGradient;

            if (boss.isMegaBoss()) {

                healthGradient = new GradientPaint(

                    barX + 10, 0, new Color(200, 50, 50),

                    barX + barWidth - 10, 0, new Color(255, 100, 100)

                );

            } else {

                healthGradient = new GradientPaint(

                    barX + 10, 0, new Color(50, 150, 50),

                    barX + barWidth - 10, 0, new Color(100, 200, 100)

                );

            }

            g.setPaint(healthGradient);

            g.fillRoundRect(barX + 10, barY + 45, barWidth - 20, 15, 8, 8);

            

            // Add hit indicators based on boss type (2 segments for mini, 3 for mega)

            int maxHits = boss.isMegaBoss() ? 3 : 2;

            g.setColor(new Color(0, 0, 0, 150));

            int segmentWidth = (barWidth - 20) / maxHits;

            for (int i = 1; i < maxHits; i++) {

                int dividerX = barX + 10 + (segmentWidth * i);

                g.fillRect(dividerX - 1, barY + 45, 2, 15);

            }

            

            // Darken segments that have been hit

            g.setColor(new Color(0, 0, 0, 120));

            for (int i = 0; i < bossHitCount && i < maxHits; i++) {

                g.fillRoundRect(barX + 10 + (segmentWidth * i), barY + 45, segmentWidth, 15, 8, 8);

            }

            

            // Draw hit count text

            g.setFont(new Font("Arial", Font.BOLD, 12));

            g.setColor(Color.WHITE);

            String hitText = "Hits: " + bossHitCount + "/" + maxHits;

            g.drawString(hitText, barX + barWidth - 70, barY + 57);

            

            // Attack Phase indicator (Assault vs Recovery) - positioned on right side of boss bar

            int phaseBarWidth = 150;

            int phaseBarHeight = 8;

            int phaseBarX = barX + barWidth - phaseBarWidth - 15;

            int phaseBarY = barY + 22;

            

            // Phase label and icon

            g.setFont(new Font("Arial", Font.BOLD, 11));

            String phaseText = boss.isAssaultPhase() ? "[!] ASSAULT" : "[-] RECOVERY";

            Color phaseColor = boss.isAssaultPhase() ? new Color(255, 80, 80) : new Color(80, 180, 255);

            

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

            g.setColor(new Color(40, 40, 50));

            g.fillRoundRect(phaseBarX, phaseBarY + 3, phaseBarWidth, phaseBarHeight, 4, 4);

            

            // Phase progress bar fill

            float phaseProgress = boss.getAttackPhaseProgress();

            int fillWidth = (int)(phaseBarWidth * phaseProgress);

            GradientPaint phaseGradient;

            if (boss.isAssaultPhase()) {

                phaseGradient = new GradientPaint(

                    phaseBarX, 0, new Color(255, 50, 50),

                    phaseBarX + phaseBarWidth, 0, new Color(255, 150, 50)

                );

            } else {

                phaseGradient = new GradientPaint(

                    phaseBarX, 0, new Color(50, 150, 255),

                    phaseBarX + phaseBarWidth, 0, new Color(100, 200, 150)

                );

            }

            g.setPaint(phaseGradient);

            g.fillRoundRect(phaseBarX, phaseBarY + 3, fillWidth, phaseBarHeight, 4, 4);

            

            // Health bar border

            g.setColor(new Color(200, 200, 200));

            g.setStroke(new BasicStroke(2));

            g.drawRoundRect(barX + 10, barY + 45, barWidth - 20, 15, 8, 8);

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

        

        // Draw UI with better contrast

        g.setColor(new Color(0, 0, 0, 150));

        g.fillRoundRect(10, 10, 280, 140, 10, 10);

        

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.BOLD, 24));

        g.drawString("Level: " + level, 20, 35);

        g.drawString("Score: " + (int)displayedScore, 20, 65);

        g.drawString("Money: $" + (int)displayedMoney, 20, 95);

        

        // Display timer and FPS

        g.setFont(new Font("Arial", Font.PLAIN, 18));

        int minutes = (int)(gameTime / 60);

        int seconds = (int)(gameTime % 60);

        int milliseconds = (int)((gameTime % 1) * 100);

        String timeStr = String.format("Time: %d:%02d.%02d", minutes, seconds, milliseconds);

        g.drawString(timeStr, 20, 120);

        g.drawString("FPS: " + fps, 20, 145);

        

        // Draw combo counter with pulse effect

        if (showCombo && dodgeCombo > 1) {

            g.setColor(new Color(0, 0, 0, 150));

            g.fillRoundRect(width - 210, 10, 200, 60, 10, 10);

            

            // Apply pulse scale to combo text

            AffineTransform comboTransform = g.getTransform();

            int comboX = width - 110;

            int comboY = 50;

            g.translate(comboX, comboY);

            g.scale(comboPulseScale, comboPulseScale);

            g.translate(-comboX, -comboY);

            

            g.setColor(new Color(163, 190, 140));

            g.setFont(new Font("Arial", Font.BOLD, 32));

            String comboText = "COMBO x" + dodgeCombo;

            FontMetrics comboFm = g.getFontMetrics();

            g.drawString(comboText, width - 205 + (190 - comboFm.stringWidth(comboText)) / 2, 50);

            

            // Reset transform after pulse

            g.setTransform(comboTransform);

        }

        

        // Draw combo milestone announcements at fixed spawn position (where player was)

        if (comboSystem != null && comboSystem.getCurrentAnnouncement() != null) {

            String announcement = comboSystem.getCurrentAnnouncement();

            float announcementProgress = (float)(comboSystem.getAnnouncementTimer() / 90.0); // 1.0 = just started, 0.0 = ending

            float lifeProgress = 1.0f - announcementProgress; // 0.0 = just started, 1.0 = ending

            

            // Smooth elastic pop-in effect (overshoots then settles)

            float scale;

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

            

            // Smooth alpha: fade in quick, hold, then fade out

            float alpha;

            if (lifeProgress < 0.1f) {

                alpha = lifeProgress / 0.1f; // Quick fade in

            } else if (lifeProgress < 0.7f) {

                alpha = 1.0f; // Hold full opacity

            } else {

                alpha = 1.0f - (lifeProgress - 0.7f) / 0.3f; // Smooth fade out

            }

            

            // Gentle sway rotation (like hanging text)

            float swayAngle = (float)(Math.sin(lifeProgress * Math.PI * 6) * Math.PI / 24); // +/- 7.5 degrees

            // Dampen sway as it fades out

            swayAngle *= Math.min(1.0f, (1.0f - lifeProgress) * 2);

            

            // Smooth float upward with easing

            float floatUp = easeOutQuad(lifeProgress) * 80;

            

            AffineTransform announcementTransform = g.getTransform();

            // Use fixed spawn position (where player was when announcement triggered)

            int textX = (int)comboSystem.getAnnouncementSpawnX();

            int textY = (int)(comboSystem.getAnnouncementSpawnY() - 60 - floatUp);

            

            // Apply transforms: translate, rotate around text center, then scale

            g.translate(textX, textY);

            g.rotate(swayAngle);

            g.scale(scale, scale);

            

            // Draw with larger font

            g.setFont(new Font("Arial", Font.BOLD, 72));

            FontMetrics announceFm = g.getFontMetrics();

            int announceWidth = announceFm.stringWidth(announcement);

            int textOffsetX = -announceWidth / 2;

            int textOffsetY = announceFm.getAscent() / 3;

            

            // Draw glow/outline for better visibility

            int glowAlpha = (int)(100 * alpha);

            g.setColor(new Color(0, 0, 0, glowAlpha));

            for (int ox = -3; ox <= 3; ox++) {

                for (int oy = -3; oy <= 3; oy++) {

                    if (ox != 0 || oy != 0) {

                        g.drawString(announcement, textOffsetX + ox, textOffsetY + oy);

                    }

                }

            }

            

            // Draw shadow

            g.setColor(new Color(0, 0, 0, (int)(200 * alpha)));

            g.drawString(announcement, textOffsetX + 4, textOffsetY + 4);

            

            // Draw main text with color based on announcement

            Color announceColor = switch(announcement) {

                case "NICE!" -> new Color(163, 190, 140, (int)(255 * alpha)); // Green

                case "GREAT!" -> new Color(100, 200, 255, (int)(255 * alpha)); // Bright Blue

                case "AMAZING!" -> new Color(255, 220, 100, (int)(255 * alpha)); // Bright Yellow

                case "INCREDIBLE!" -> new Color(255, 150, 80, (int)(255 * alpha)); // Bright Orange

                case "LEGENDARY!" -> new Color(220, 130, 220, (int)(255 * alpha)); // Bright Purple

                case "GODLIKE!" -> new Color(255, 100, 100, (int)(255 * alpha)); // Bright Red

                case "IMPOSSIBLE!" -> new Color(255, 215, 0, (int)(255 * alpha)); // Gold

                default -> new Color(255, 255, 255, (int)(255 * alpha));

            };

            g.setColor(announceColor);

            g.drawString(announcement, textOffsetX, textOffsetY);

            

            // Add shine highlight on top half

            Color shineColor = new Color(255, 255, 255, (int)(80 * alpha));

            g.setColor(shineColor);

            g.drawString(announcement, textOffsetX, textOffsetY - 1);

            

            g.setTransform(announcementTransform);

        }

        

        // Draw close call / perfect dodge indicators below combo

        if (comboSystem != null && (comboSystem.getCloseCallCount() > 0 || comboSystem.getPerfectDodgeCount() > 0)) {

            int indicatorY = showCombo && dodgeCombo > 1 ? 70 : 10;

            g.setFont(new Font("Arial", Font.BOLD, 14));

            

            if (comboSystem.getPerfectDodgeCount() > 0) {

                g.setColor(new Color(255, 215, 0)); // Gold for perfect

                g.drawString("ÃƒÂ¢Ã…Â¡Ã‚Â¡ PERFECT x" + comboSystem.getPerfectDodgeCount(), width - 200, indicatorY);

                indicatorY += 18;

            }

            if (comboSystem.getCloseCallCount() > 0) {

                g.setColor(new Color(163, 190, 140)); // Green for close call

                g.drawString("ÃƒÂ¢Ã‹Å“Ã¢â‚¬Â¦ CLOSE x" + comboSystem.getCloseCallCount(), width - 200, indicatorY);

            }

        }

        

        // Draw extra lives indicator (top right, above active item)

        if (gameData.getExtraLives() > 0) {

            int livesUIX = width - 210;

            int livesUIY = showCombo && dodgeCombo > 1 ? 170 : 100;

            

            g.setColor(new Color(0, 0, 0, 150));

            g.fillRoundRect(livesUIX, livesUIY, 200, 40, 10, 10);

            

            g.setFont(new Font("Arial", Font.BOLD, 20));

            g.setColor(new Color(255, 215, 0)); // Gold

            String livesText = "Lives: " + gameData.getExtraLives();

            g.drawString(livesText, livesUIX + 10, livesUIY + 27);

        }

        

        // Draw active item UI

        equippedItem = gameData.getEquippedItem();

        if (equippedItem != null) {

            int itemUIX = width - 210;

            int itemUIY = showCombo && dodgeCombo > 1 ? 80 : 10;

            

            // Background

            g.setColor(new Color(0, 0, 0, 150));

            g.fillRoundRect(itemUIX, itemUIY, 200, 80, 10, 10);

            

            // Item name

            g.setFont(new Font("Arial", Font.BOLD, 20));

            if (equippedItem.canActivate()) {

                g.setColor(new Color(163, 190, 140)); // Green when ready

            } else if (equippedItem.isActive()) {

                g.setColor(new Color(235, 203, 139)); // Yellow when active

            } else {

                g.setColor(new Color(150, 150, 150)); // Gray when on cooldown

            }

            g.drawString(equippedItem.getName(), itemUIX + 10, itemUIY + 25);

            

            // Cooldown bar

            g.setColor(new Color(60, 60, 60));

            g.fillRect(itemUIX + 10, itemUIY + 35, 180, 15);

            

            if (equippedItem.isActive()) {

                // Active duration bar (yellow)

                float activePercent = (float)equippedItem.getActiveTimer() / (float)equippedItem.getActiveDuration();

                g.setColor(new Color(235, 203, 139));

                g.fillRect(itemUIX + 10, itemUIY + 35, (int)(180 * activePercent), 15);

            } else {

                // Cooldown progress bar (green)

                float cooldownPercent = equippedItem.getCooldownPercent();

                g.setColor(new Color(163, 190, 140));

                g.fillRect(itemUIX + 10, itemUIY + 35, (int)(180 * cooldownPercent), 15);

            }

            

            // Key hint

            g.setFont(new Font("Arial", Font.PLAIN, 14));

            g.setColor(Color.WHITE);

            String keyHint = equippedItem.canActivate() ? "Press [SPACE]" : 

                           equippedItem.isActive() ? "ACTIVE" :

                           String.format("%.1fs", equippedItem.getCurrentCooldown() / 60.0);

            g.drawString(keyHint, itemUIX + 10, itemUIY + 68);

        }

        

        // Draw "Press SPACE to skip" text during intro animation

        if (introPanActive) {

            g.setFont(new Font("Arial", Font.BOLD, 18));

            g.setColor(new Color(255, 255, 255, 180));

            String skipText = "Press SPACE to skip";

            FontMetrics fm = g.getFontMetrics();

            int textX = (width - fm.stringWidth(skipText)) / 2;

            int textY = height - 30;

            

            // Draw shadow for better visibility

            g.setColor(new Color(0, 0, 0, 150));

            g.drawString(skipText, textX + 2, textY + 2);

            g.setColor(new Color(255, 255, 255, 180));

            g.drawString(skipText, textX, textY);

        }

        

        // Draw combo display

        if (comboSystem != null && comboSystem.getCombo() > 1 && !introPanActive) {

            int comboX = width - 250;

            int comboY = 100;

            

            // Combo background

            g.setColor(new Color(0, 0, 0, 180));

            g.fillRoundRect(comboX, comboY, 200, 80, 15, 15);

            

            // Combo number

            g.setFont(new Font("Arial", Font.BOLD, 48));

            g.setColor(new Color(235, 203, 139));

            String comboText = comboSystem.getCombo() + "x";

            FontMetrics fm = g.getFontMetrics();

            g.drawString(comboText, comboX + (200 - fm.stringWidth(comboText)) / 2, comboY + 45);

            

            // Multiplier

            g.setFont(new Font("Arial", Font.PLAIN, 14));

            g.setColor(new Color(216, 222, 233));

            String multText = String.format("%.1fx Score", comboSystem.getMultiplier());

            fm = g.getFontMetrics();

            g.drawString(multText, comboX + (200 - fm.stringWidth(multText)) / 2, comboY + 65);

            

            // Timeout bar

            float timeoutProgress = comboSystem.getTimeoutProgress();

            g.setColor(new Color(60, 60, 60));

            g.fillRect(comboX + 10, comboY + 72, 180, 3);

            g.setColor(new Color(163, 190, 140));

            g.fillRect(comboX + 10, comboY + 72, (int)(180 * timeoutProgress), 3);

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

                // Scale factor: starts at 0.15 (heavy blur) → 1.0 (sharp) as phase progresses

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

            g.setColor(new Color(40, 40, 40, 200));

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

            g.setColor(new Color(200, 200, 200));

            g.setStroke(new BasicStroke(2));

            g.drawRoundRect(barX, barY, barWidth, barHeight, 10, 10);

            

            // Warning text with pulsing effect

            g.setFont(new Font("Arial", Font.BOLD, 20));

            float textPulse = (float) (0.7 + 0.3 * Math.sin(time * 8 * (1 + dangerLevel * 2)));

            g.setColor(new Color(255, 255, 255, (int) (255 * textPulse)));

            String warningText = dangerLevel < 0.5 ? "KEEP MOVING!" : 

                                dangerLevel < 0.8 ? "ÃƒÂ¢Ã…Â¡Ã‚Â  MOVE NOW!" : "ÃƒÂ¢Ã…Â¡Ã‚Â ÃƒÂ¢Ã…Â¡Ã‚Â  MOVE! ÃƒÂ¢Ã…Â¡Ã‚Â ÃƒÂ¢Ã…Â¡Ã‚Â ";

            FontMetrics fm = g.getFontMetrics();

            g.drawString(warningText, barX + (barWidth - fm.stringWidth(warningText)) / 2, barY + 26);

            

            // Time remaining text

            if (timeRemaining > 0) {

                g.setFont(new Font("Arial", Font.PLAIN, 14));

                g.setColor(new Color(220, 220, 220));

                String timeText = String.format("%.1fs", timeRemaining);

                g.drawString(timeText, barX + barWidth + 10, barY + 26);

            }

        }

        

        // Draw pause menu

        if (isPaused) {

            // Dark overlay - extended beyond screen to prevent shake edge visibility

            int shakeMargin = 250; // Extends all directions equally

            g.setColor(new Color(0, 0, 0, 200));

            g.fillRect(-shakeMargin, -shakeMargin, width + shakeMargin * 2, height + shakeMargin * 2);

            

            // Pause title

            g.setFont(new Font("Arial", Font.BOLD, 84));

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

            g.setColor(new Color(0, 0, 0, 150));

            g.fillRect(0, 0, width, height);

            

            // Calculate countdown number (3, 2, 1, GO!)

            int secondsRemaining = (unpauseCountdownTimer > 0) ? (int)(((unpauseCountdownTimer - 1) / 60) + 1) : 0;

            String countdownText;

            Color countdownColor;

            

            if (secondsRemaining > 0) {

                countdownText = String.valueOf(secondsRemaining);

                countdownColor = new Color(235, 203, 139); // Gold

            } else {

                countdownText = "GO!";

                countdownColor = new Color(163, 190, 140); // Green

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

            g.setFont(new Font("Arial", Font.BOLD, 120));

            FontMetrics countdownFm = g.getFontMetrics();

            int textWidth = countdownFm.stringWidth(countdownText);

            

            g.setColor(new Color(0, 0, 0, (int)(180 * alpha)));

            g.drawString(countdownText, centerX - textWidth / 2 + 4, centerY + 4);

            

            // Main text

            g.setColor(new Color(countdownColor.getRed(), countdownColor.getGreen(), countdownColor.getBlue(), (int)(255 * alpha)));

            g.drawString(countdownText, centerX - textWidth / 2, centerY);

            

            g.setTransform(countdownTransform);

            

            // Subtitle (not scaled)

            g.setFont(new Font("Arial", Font.PLAIN, 32));

            String subtitleText = "Get Ready!";

            FontMetrics subtitleFm = g.getFontMetrics();

            g.setColor(new Color(216, 222, 233));

            g.drawString(subtitleText, (width - subtitleFm.stringWidth(subtitleText)) / 2, centerY + 80);

        }

        

        // Draw achievement notification

        if (pendingAchievements != null && !pendingAchievements.isEmpty() && achievementNotificationTimer > 0 && !isPaused) {

            Achievement ach = pendingAchievements.get(0);

            float alpha = (float)Math.max(0.0, Math.min(1.0, achievementNotificationTimer < 30 ? achievementNotificationTimer / 30.0 : 1.0));

            

            int notifX = width - 420;

            

            // Calculate Y position based on what UI is showing

            int notifY = 10; // Start at top

            

            // Check if combo is showing (takes priority)

            if (showCombo && dodgeCombo > 1) {

                notifY = 80; // Below combo

            }

            

            // Check if lives are showing

            if (gameData.getExtraLives() > 0) {

                int livesY = showCombo && dodgeCombo > 1 ? 10 : 10;

                notifY = Math.max(notifY, livesY + 50); // Below lives (40px height + 10px padding)

            }

            

            // Check if active item is showing

            if (equippedItem != null) {

                int itemUIY = showCombo && dodgeCombo > 1 ? 80 : 10;

                notifY = Math.max(notifY, itemUIY + 90); // Below item (80px height + 10px padding)

            }

            

            Graphics2D g2d = (Graphics2D) g.create();

            

            // Background

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            g2d.setColor(new Color(46, 52, 64, 230));

            g2d.fillRoundRect(notifX, notifY, 400, 100, 15, 15);

            

            // Title

            g2d.setFont(new Font("Arial", Font.BOLD, 20));

            g2d.setColor(new Color(235, 203, 139));

            g2d.drawString("Achievement Unlocked!", notifX + 20, notifY + 30);

            

            // Achievement name

            g2d.setFont(new Font("Arial", Font.BOLD, 24));

            g2d.setColor(new Color(216, 222, 233));

            g2d.drawString(ach.getName(), notifX + 20, notifY + 60);

            

            // Description

            g2d.setFont(new Font("Arial", Font.PLAIN, 14));

            g2d.drawString(ach.getDescription(), notifX + 20, notifY + 85);

            

            g2d.dispose();

        }

        

        // Draw overlay on top of everything (not affected by camera shake)

        if (overlayLoaded && overlayImage != null) {

            g.drawImage(overlayImage, 0, 0, width, height, null);

        }

        

        // Screen flash effect on player death

        if (screenFlashTimer > 0) {

            Graphics2D g2d = (Graphics2D) g.create();

            float flashAlpha = (float)screenFlashTimer / 15.0f * 0.7f; // Fade out over 15 frames

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

            g2d.setColor(Color.WHITE);

            g2d.fillRect(0, 0, width, height);

            g2d.dispose();

        }

        

        // Type Purge chromatic flash effect (flashes the color of the purged bullet type)

        if (typePurgeFlashTimer > 0 && typePurgeFlashColor != null) {

            Graphics2D g2d = (Graphics2D) g.create();

            // Rapid blink effect with the bullet type's color

            boolean blinkOn = (typePurgeFlashTimer / 3) % 2 == 0;

            if (blinkOn) {

                float flashAlpha = Math.min(0.6f, (float)typePurgeFlashTimer / 30.0f * 0.6f);

                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

                g2d.setColor(typePurgeFlashColor);

                g2d.fillRect(0, 0, width, height);

            }

            g2d.dispose();

        }

        

        // Item ready flicker effect (green flicker)

        if (itemReadyFlickerTimer > 0) {

            Graphics2D g2d = (Graphics2D) g.create();

            // Flicker on/off every 4 frames

            if ((itemReadyFlickerTimer / 4) % 2 == 0) {

                float flickerAlpha = Math.min(0.3f, (float)itemReadyFlickerTimer / 20.0f * 0.3f);

                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flickerAlpha));

                g2d.setColor(new Color(163, 190, 140)); // Green tint

                g2d.fillRect(0, 0, width, height);

            }

            g2d.dispose();

        }

        

        // Item complete flash effect (blue flash)

        if (itemCompleteFlashTimer > 0) {

            Graphics2D g2d = (Graphics2D) g.create();

            float flashAlpha = (float)itemCompleteFlashTimer / 15.0f * 0.5f; // Fade out over 15 frames

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

            g2d.setColor(new Color(80, 180, 255)); // Blue tint

            g2d.fillRect(0, 0, width, height);

            g2d.dispose();

        }

        

        

        // Achievement unlocked flash effect (gold flash)

        if (achievementFlashTimer > 0) {

            Graphics2D g2d = (Graphics2D) g.create();

            float flashAlpha = (float)achievementFlashTimer / 20.0f * 0.4f; // Fade out over 20 frames

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

            g2d.setColor(new Color(235, 203, 139)); // Gold tint

            g2d.fillRect(0, 0, width, height);

            g2d.dispose();

        }

        

        // Boss intro flash effect (red/orange flash)

        if (bossIntroFlashTimer > 0) {

            Graphics2D g2d = (Graphics2D) g.create();

            float flashAlpha = (float)bossIntroFlashTimer / 25.0f * 0.5f; // Fade out over 25 frames

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

            g2d.setColor(new Color(208, 135, 112)); // Red/orange tint

            g2d.fillRect(0, 0, width, height);

            g2d.dispose();

        }

        

        // Countdown flash effect (white pulse)

        if (countdownFlashTimer > 0) {

            Graphics2D g2d = (Graphics2D) g.create();

            float flashAlpha = (float)countdownFlashTimer / 15.0f * 0.3f; // Quick fade over 15 frames

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

            g2d.setColor(new Color(255, 255, 255)); // White flash

            g2d.fillRect(0, 0, width, height);

            g2d.dispose();

        }

        

        // Boss hit flash effect (intense red flash)

        if (bossHitFlashTimer > 0) {

            Graphics2D g2d = (Graphics2D) g.create();

            float flashAlpha = (float)bossHitFlashTimer / 18.0f * 0.6f; // Strong fade over 18 frames

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flashAlpha));

            g2d.setColor(new Color(255, 50, 50)); // Intense red flash

            g2d.fillRect(0, 0, width, height);

            g2d.dispose();

        }

        // Apply vignette effect at the end (darkens edges)

        if (Game.enableVignette) {

            applyVignette(g, width, height);

        }

    }

    

    public void drawShop(Graphics2D g, int width, int height, double time, double scrollOffset) {

        // Draw animated Balatro-style gradient

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        g.setFont(new Font("Arial", Font.BOLD, 64));

        String title = "UPGRADE SHOP";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 100;

        

        // Shadow

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 30, new Color(180, 142, 173),

            titleX, titleY + 20, new Color(235, 203, 139)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        // Show money with glowing effect

        g.setColor(new Color(163, 190, 140)); // Green

        g.setFont(new Font("Arial", Font.BOLD, 36));

        String money = "Money: $" + gameData.getTotalMoney();

        fm = g.getFontMetrics();

        int moneyX = (width - fm.stringWidth(money)) / 2;

        // Glow effect

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.fillRect(moneyX - 20, 140, fm.stringWidth(money) + 40, 50);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        g.drawString(money, moneyX, 170);

        

        // Show earnings

        g.setColor(new Color(235, 203, 139)); // Yellow

        g.setFont(new Font("Arial", Font.PLAIN, 24));

        String earnings = "Earned this run: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(earnings, (width - fm.stringWidth(earnings)) / 2, 210);

        

        // Shop items using buttons

        String[] items = shopManager.getShopItems();

        int y = 250;

        int selectedItem = shopManager.getSelectedShopItem();

        

        // Create a clipping region for scrollable area

        g.setClip(0, 220, width, height - 270);

        

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

                g.setColor(new Color(0, 0, 0, 120));

                g.fillRoundRect(itemX + 3, scrolledY - 27, 900, 70, 15, 15);

                

                // Card background color based on state

                Color cardColor;

                if (i == 0) {

                    cardColor = new Color(163, 190, 140, 200); // Green for continue

                } else if (isMaxed) {

                    cardColor = new Color(85, 75, 45, 200); // Dark gold for maxed - better text contrast

                } else if (!canAfford) {

                    cardColor = new Color(40, 40, 50, 200);

                } else if (i == selectedItem) {

                    cardColor = new Color(140, 120, 150, 200); // Softer purple for selected

                } else {

                    cardColor = new Color(76, 86, 106, 200);

                }

                

                g.setColor(cardColor);

                g.fillRoundRect(itemX, scrolledY - 30, 900, 70, 15, 15);

                

                // Border glow for selected item

                if (i == selectedItem) {

                    g.setColor(new Color(180, 170, 130, 140)); // Softer border glow

                    g.setStroke(new BasicStroke(2f));

                    g.drawRoundRect(itemX, scrolledY - 30, 900, 70, 15, 15);

                    g.setStroke(new BasicStroke(1f));

                }

                

                // Draw icon/symbol on the left

                String icon = getItemIcon(i);

                g.setFont(new Font("Arial", Font.BOLD, 36));

                g.setColor(canAfford ? new Color(235, 203, 139) : new Color(100, 100, 100));

                g.drawString(icon, itemX + 20, scrolledY + 10);

                

                // Draw item name and description

                String[] itemParts = items[i].split(" - ", 2);

                String itemName = itemParts[0];

                String itemDesc = itemParts.length > 1 ? itemParts[1] : "";

                

                g.setFont(new Font("Arial", Font.BOLD, 20));

                g.setColor(canAfford ? Color.WHITE : new Color(120, 120, 120));

                g.drawString(itemName, itemX + 75, scrolledY - 5);

                

                g.setFont(new Font("Arial", Font.PLAIN, 14));

                g.setColor(canAfford ? new Color(200, 200, 200) : new Color(100, 100, 100));

                g.drawString(itemDesc, itemX + 75, scrolledY + 15);

                

                // Draw progress bar for all upgrades (not for Continue)

                // All upgrades now come from PassiveUpgradeManager (shop index 1 = upgrade index 0)

                if (i > 0 && passiveUpgradeManager != null) {

                    int upgradeIndex = i - 1;

                    if (upgradeIndex < passiveUpgradeManager.getAllUpgrades().size()) {

                        PassiveUpgrade upgrade = passiveUpgradeManager.getAllUpgrades().get(upgradeIndex);

                        int currentLevel = upgrade.getCurrentLevel();

                        int maxLevel = upgrade.getMaxLevel();

                        

                        // Special handling for Extra Lives (last upgrade)

                        boolean isExtraLives = upgrade.getId().equals("health");

                        if (isExtraLives) {

                            currentLevel = gameData.getExtraLives(); // Show current lives owned

                        }

                        

                        int barX = itemX + 75;

                        int barY = scrolledY + 30;

                        int barWidth = 550;

                        int barHeight = 8;

                        

                        // Level text above progress bar

                        g.setFont(new Font("Arial", Font.BOLD, 11));

                        g.setColor(upgrade.isMaxed() || isMaxed ? new Color(235, 203, 139) : new Color(200, 200, 200));

                        String levelText = isExtraLives ? currentLevel + "/3 lives" : currentLevel + "/" + maxLevel;

                        g.drawString(levelText, barX, barY - 3);

                        

                        // Progress bar background

                        g.setColor(new Color(40, 40, 50, 180));

                        g.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);

                        

                        // Progress bar fill

                        if (currentLevel > 0) {

                            double progress = (double)currentLevel / maxLevel;

                            int fillWidth = (int)(barWidth * progress);

                            

                            GradientPaint progressGrad = new GradientPaint(

                                barX, 0, new Color(163, 190, 140),

                                barX + fillWidth, 0, new Color(235, 203, 139)

                            );

                            g.setPaint(progressGrad);

                            g.fillRoundRect(barX, barY, fillWidth, barHeight, 4, 4);

                        }

                    }

                }

                

                // Draw cost on the right

                if (i != 0) {

                    g.setFont(new Font("Arial", Font.BOLD, 24));

                    if (isMaxed) {

                        g.setColor(new Color(235, 203, 139));

                        FontMetrics costFm = g.getFontMetrics();

                        String maxedStr = "MAXED";

                        int maxedW = costFm.stringWidth(maxedStr);

                        g.drawString(maxedStr, itemX + 900 - maxedW - 20, scrolledY + 10);

                    } else {

                        g.setColor(canAfford ? new Color(163, 190, 140) : new Color(191, 97, 106));

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

        

        // Instructions

        g.setColor(new Color(216, 222, 233));

        g.setFont(new Font("Arial", Font.PLAIN, 20));

        String inst1 = "Use UP/DOWN or MOUSE to select | SPACE or CLICK to purchase | ESC to continue";

        fm = g.getFontMetrics();

        g.drawString(inst1, (width - fm.stringWidth(inst1)) / 2, height - 50);

    }

    

    public void drawGameOver(Graphics2D g, int width, int height, double time) {

        // Draw animated gradient

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(67, 76, 94)});

        

        // Holographic title

        g.setFont(new Font("Arial", Font.BOLD, 84));

        String gameOver = "RUN ENDED";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(gameOver)) / 2;

        int titleY = height / 2 - 140;

        

        // Shadow

        g.setColor(new Color(0, 0, 0, 150));

        g.drawString(gameOver, titleX + 5, titleY + 5);

        

        // Gradient text (red theme)

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 40, new Color(191, 97, 106),

            titleX, titleY + 30, new Color(220, 120, 130)

        );

        g.setPaint(titleGrad);

        g.drawString(gameOver, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(gameOver, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        // Run stats with consistent styling

        g.setColor(new Color(216, 222, 233));

        g.setFont(new Font("Arial", Font.BOLD, 32));

        

        // Level reached this run

        String level = "Level Reached: " + gameData.getCurrentLevel();

        fm = g.getFontMetrics();

        g.drawString(level, (width - fm.stringWidth(level)) / 2, height / 2 - 40);

        

        String score = "Score: " + gameData.getScore();

        fm = g.getFontMetrics();

        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2);

        

        String money = "Money Earned: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 + 40);

        

        // Display cumulative run stats

        LevelStats runStats = gameData.getCumulativeRunStats();

        g.setFont(new Font("Arial", Font.PLAIN, 20));

        g.setColor(new Color(200, 200, 210));

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

                g.setColor(new Color(255, 120, 120));

            } else if (riskPercent >= 40) {

                g.setColor(new Color(255, 180, 100));

            } else {

                g.setColor(new Color(150, 200, 255));

            }

            g.drawString(risk, (width - fm.stringWidth(risk)) / 2, statsY);

            g.setColor(new Color(200, 200, 210));

            statsY += 24;

        }

        

        if (runStats.getDamageTaken() > 0 || runStats.getLivesUsed() > 0) {

            String survival = "Damage Taken: " + runStats.getDamageTaken() + "  Lives Used: " + runStats.getLivesUsed();

            fm = g.getFontMetrics();

            g.drawString(survival, (width - fm.stringWidth(survival)) / 2, statsY);

            statsY += 30;

        }

        

        // Add spacing before persistent stats

        statsY += 15;

        

        // Show persistent stats

        g.setFont(new Font("Arial", Font.PLAIN, 22));

        g.setColor(new Color(180, 180, 190));

        String totalMoney = "Total Money: $" + gameData.getTotalMoney();

        fm = g.getFontMetrics();

        g.drawString(totalMoney, (width - fm.stringWidth(totalMoney)) / 2, statsY);

        statsY += 25;

        

        String bestRun = "Best Run: Level " + Math.max(gameData.getBestRunLevel(), gameData.getCurrentLevel());

        fm = g.getFontMetrics();

        g.drawString(bestRun, (width - fm.stringWidth(bestRun)) / 2, statsY);

        statsY += 30;

        

        // Show extra lives remaining

        if (gameData.getExtraLives() > 0) {

            g.setFont(new Font("Arial", Font.BOLD, 24));

            g.setColor(new Color(255, 215, 0)); // Gold color

            String livesText = "ÃƒÂ¢Ã‹Å“Ã¢â‚¬Â¦ Extra Lives: " + gameData.getExtraLives() + " ÃƒÂ¢Ã‹Å“Ã¢â‚¬Â¦";

            fm = g.getFontMetrics();

            g.drawString(livesText, (width - fm.stringWidth(livesText)) / 2, statsY);

            statsY += 35;

        } else {

            statsY += 10;

        }

        

        // Controls

        g.setFont(new Font("Arial", Font.PLAIN, 24));

        g.setColor(new Color(216, 222, 233));

        String retry = "SPACE - New Run  |  ESC - Main Menu";

        fm = g.getFontMetrics();

        g.drawString(retry, (width - fm.stringWidth(retry)) / 2, statsY);

        statsY += 30;

        

        // Roguelike reminder

        g.setFont(new Font("Arial", Font.ITALIC, 18));

        g.setColor(new Color(163, 190, 140));

        String keep = "Your upgrades and items are saved!";

        fm = g.getFontMetrics();

        g.drawString(keep, (width - fm.stringWidth(keep)) / 2, statsY);

    }

    

    public void drawWin(Graphics2D g, int width, int height, double time, double bossKillTime) {

        // Draw animated gradient

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        g.setFont(new Font("Arial", Font.BOLD, 84));

        String win = "VICTORY!";

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(win)) / 2;

        int titleY = height / 2 - 180;

        

        // Shadow

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(win, titleX + 5, titleY + 5);

        

        // Gradient text (green theme)

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 40, new Color(163, 190, 140),

            titleX, titleY + 30, new Color(180, 200, 160)

        );

        g.setPaint(titleGrad);

        g.drawString(win, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(win, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        // Stats with consistent styling

        g.setColor(new Color(216, 222, 233));

        g.setFont(new Font("Arial", Font.BOLD, 32));

        String score = "Score: " + gameData.getScore();

        fm = g.getFontMetrics();

        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2 - 90);

        

        String money = "Money Earned: $" + gameData.getRunMoney();

        fm = g.getFontMetrics();

        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 - 50);

        

        // Display boss kill time

        int minutes = (int)(bossKillTime / 60);

        int seconds = (int)(bossKillTime % 60);

        int milliseconds = (int)((bossKillTime % 1) * 100);

        String timeStr = String.format("Time: %d:%02d.%02d", minutes, seconds, milliseconds);

        fm = g.getFontMetrics();

        g.setColor(new Color(255, 215, 0)); // Gold color for time

        g.drawString(timeStr, (width - fm.stringWidth(timeStr)) / 2, height / 2 - 10);

        

        // Display level stats (only non-zero stats)

        LevelStats stats = gameData.getCurrentLevelStats();

        g.setFont(new Font("Arial", Font.PLAIN, 20));

        g.setColor(new Color(180, 190, 200));

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

            g.setColor(new Color(255, 215, 0));

            g.drawString(perfect, (width - fm.stringWidth(perfect)) / 2, statsY);

            g.setColor(new Color(180, 190, 200));

            statsY += 26;

        }

        

        if (stats.getNearMisses() > 0) {

            String nearMiss = "Near Misses: " + stats.getNearMisses();

            fm = g.getFontMetrics();

            g.setColor(new Color(255, 165, 0));

            g.drawString(nearMiss, (width - fm.stringWidth(nearMiss)) / 2, statsY);

            g.setColor(new Color(180, 190, 200));

            statsY += 26;

        }

        

        if (stats.getMaxCombo() > 0) {

            String maxCombo = "Max Combo: " + stats.getMaxCombo() + "x";

            fm = g.getFontMetrics();

            g.setColor(new Color(100, 200, 255));

            g.drawString(maxCombo, (width - fm.stringWidth(maxCombo)) / 2, statsY);

            g.setColor(new Color(180, 190, 200));

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

                g.setColor(new Color(255, 100, 100)); // High risk - red

            } else if (riskPercent >= 40) {

                g.setColor(new Color(255, 165, 0)); // Medium risk - orange

            } else {

                g.setColor(new Color(100, 200, 255)); // Low risk - blue

            }

            g.drawString(risk, (width - fm.stringWidth(risk)) / 2, statsY);

            g.setColor(new Color(180, 190, 200));

            statsY += 26;

        }

        

        if (stats.getDamageTaken() > 0) {

            String damage = "Damage Taken: " + stats.getDamageTaken();

            fm = g.getFontMetrics();

            g.setColor(new Color(200, 100, 100));

            g.drawString(damage, (width - fm.stringWidth(damage)) / 2, statsY);

            g.setColor(new Color(180, 190, 200));

            statsY += 26;

        }

        

        if (stats.getLivesUsed() > 0) {

            String lives = "Lives Used: " + stats.getLivesUsed();

            fm = g.getFontMetrics();

            g.drawString(lives, (width - fm.stringWidth(lives)) / 2, statsY);

            statsY += 26;

        }

        

        g.setColor(Color.WHITE);

        g.setFont(new Font("Arial", Font.PLAIN, 24));

        String inst = "Press SPACE to Visit Shop";

        fm = g.getFontMetrics();

        // Position instruction text below stats, with minimum at height/2 + 160

        int instructionY = Math.max(height / 2 + 160, statsY + 30);

        g.drawString(inst, (width - fm.stringWidth(inst)) / 2, instructionY);

    }

    

    public void drawSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, int selectedCategory, GameData gameData) {

        // Draw animated gradient with palette colors

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        String title = "SETTINGS";

        g.setFont(new Font("Arial", Font.BOLD, 60));

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 80;

        

        // Shadow layer

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text (purple to blue)

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 40, new Color(180, 142, 173),

            titleX, titleY + 30, new Color(136, 192, 208)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        // Category tabs

        String[] categories = {"GRAPHICS", "AUDIO", "GAMEPLAY", "DEBUG"};

        int tabWidth = 200;

        int tabStartX = (width - categories.length * tabWidth) / 2;

        int tabY = 130;

        

        g.setFont(new Font("Arial", Font.BOLD, 20));

        for (int i = 0; i < categories.length; i++) {

            int tabX = tabStartX + i * tabWidth;

            boolean isSelected = i == selectedCategory;

            boolean tabsFocused = selectedItem == -1; // Tabs are focused

            

            // Tab background

            if (isSelected) {

                g.setColor(new Color(88, 91, 112, 200));

            } else {

                g.setColor(new Color(67, 76, 94, 150));

            }

            g.fillRoundRect(tabX, tabY, tabWidth - 10, 40, 10, 10);

            

            // Tab border

            if (isSelected) {

                // Double border if tabs are focused

                if (tabsFocused) {

                    g.setColor(new Color(163, 190, 140, 200)); // Green glow when focused

                    g.setStroke(new BasicStroke(4));

                    g.drawRoundRect(tabX - 1, tabY - 1, tabWidth - 8, 42, 10, 10);

                }

                g.setColor(new Color(235, 203, 139));

                g.setStroke(new BasicStroke(2));

                g.drawRoundRect(tabX, tabY, tabWidth - 10, 40, 10, 10);

            }

            

            // Tab text

            g.setColor(isSelected ? new Color(235, 203, 139) : new Color(216, 222, 233));

            fm = g.getFontMetrics();

            g.drawString(categories[i], tabX + (tabWidth - 10 - fm.stringWidth(categories[i])) / 2, tabY + 26);

        }

        

        g.setFont(new Font("Arial", Font.PLAIN, 16));

        g.setColor(new Color(216, 222, 233));

        String subtitle = "W/S to navigate | TAB / A-D / Arrows to switch tabs when selected | R to reset | ESC to exit";

        fm = g.getFontMetrics();

        g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, 185);

        

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

        }

        

        // Restore clipping

        g.setClip(oldClip);

        

        // Instructions

        g.setColor(new Color(216, 222, 233));

        g.setFont(new Font("Arial", Font.PLAIN, 18));

        String inst = "Press ESC to return to menu";

        fm = g.getFontMetrics();

        g.drawString(inst, (width - fm.stringWidth(inst)) / 2, height - 30);

    }

    

    private void drawGraphicsSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset) {

        String[] settingNames = {"Resolution", "VSync", "FPS Limit", "Anti-Aliasing", "Background Mode", "Gradient Animation", "Gradient Quality", "Grain Effect", "Particle Effects", "Shadows", "Bloom/Glow", "Motion Blur", "Chromatic Aberration", "Vignette", "Camera Zoom", "Fullscreen Mode"};

        String[] settingValues = {

            Game.resolutionPreset == 0 ? "1280x720" : Game.resolutionPreset == 1 ? "1366x768" : Game.resolutionPreset == 2 ? "1600x900" : Game.resolutionPreset == 3 ? "1920x1080" : Game.resolutionPreset == 4 ? "2560x1440" : "3840x2160",

            Game.enableVSync ? "ON" : "OFF",

            Game.fpsLimit == 0 ? "30 FPS" : Game.fpsLimit == 1 ? "60 FPS" : Game.fpsLimit == 2 ? "120 FPS" : Game.fpsLimit == 3 ? "144 FPS" : "Unlimited",

            Game.enableAntiAliasing ? "ON" : "OFF",

            Game.backgroundMode == 0 ? "Gradient" : Game.backgroundMode == 1 ? "Parallax" : "Static",

            Game.enableGradientAnimation ? "ON" : "OFF",

            Game.gradientQuality == 0 ? "Low" : Game.gradientQuality == 1 ? "Medium" : "High",

            Game.enableGrainEffect ? "ON" : "OFF",

            Game.enableParticles ? "ON" : "OFF",

            Game.enableShadows ? "ON" : "OFF",

            Game.enableBloom ? "ON" : "OFF",

            Game.enableMotionBlur ? "ON" : "OFF",

            Game.enableChromaticAberration ? "ON" : "OFF",

            Game.enableVignette ? "ON" : "OFF",

            String.format("%.0f%%", Game.cameraZoom * 100),

            Game.isFullscreen ? "Fullscreen" : "Windowed"

        };

        

        String[] descriptions = {

            "Display resolution (restart required for changes to take effect)",

            "Synchronize frame rate with monitor refresh (smoother, less tearing)",

            "Maximum frames per second (lower = better performance)",

            "Smooth edges of graphics (better quality, slight performance impact)",

            "Choose between gradient, parallax images, or static image background",

            "Animate gradient backgrounds (may affect performance)",

            "Number of gradient layers (higher = better but slower)",

            "Add grain texture overlay (performance impact)",

            "Enable particle effects (trails, explosions, etc.)",

            "Enable shadows for all objects (planes, bullets)",

            "Glow effect on bright objects (performance impact)",

            "Blur effect on fast moving objects (performance impact)",

            "Color fringing on screen edges (cinematic effect)",

            "Darken screen edges (focuses attention on center)",

            "How zoomed in the camera is during gameplay (75% - 150%)",

            "Toggle between fullscreen and windowed mode (F11)"

        };

        

        // Create slider info: [hasSlider, min, max, current]

        float[][] sliders = new float[settingNames.length][4];

        sliders[0] = new float[]{1, 0, 5, Game.resolutionPreset}; // Resolution

        sliders[1] = new float[]{0, 0, 0, 0}; // VSync (toggle)

        sliders[2] = new float[]{1, 0, 4, Game.fpsLimit}; // FPS Limit

        sliders[3] = new float[]{0, 0, 0, 0}; // Anti-aliasing (toggle)

        sliders[4] = new float[]{1, 0, 2, Game.backgroundMode}; // Background Mode

        sliders[5] = new float[]{0, 0, 0, 0}; // Gradient Animation (toggle)

        sliders[6] = new float[]{1, 0, 2, Game.gradientQuality}; // Gradient Quality

        sliders[7] = new float[]{0, 0, 0, 0}; // Grain (toggle)

        sliders[8] = new float[]{0, 0, 0, 0}; // Particles (toggle)

        sliders[9] = new float[]{0, 0, 0, 0}; // Shadows (toggle)

        sliders[10] = new float[]{0, 0, 0, 0}; // Bloom (toggle)

        sliders[11] = new float[]{0, 0, 0, 0}; // Motion Blur (toggle)

        sliders[12] = new float[]{0, 0, 0, 0}; // Chromatic Aberration (toggle)

        sliders[13] = new float[]{0, 0, 0, 0}; // Vignette (toggle)

        sliders[14] = new float[]{1, 0.75f, 1.5f, (float)Game.cameraZoom}; // Camera Zoom

        sliders[15] = new float[]{0, 0, 0, 0}; // Fullscreen (toggle)

        

        boolean[] toggles = {

            false, Game.enableVSync, false, Game.enableAntiAliasing, false,

            Game.enableGradientAnimation, false, Game.enableGrainEffect,

            Game.enableParticles, Game.enableShadows, Game.enableBloom,

            Game.enableMotionBlur, Game.enableChromaticAberration,

            Game.enableVignette, false, Game.isFullscreen

        };

        

        drawSettingsListWithSliders(g, width, height, selectedItem, time, scrollOffset, settingNames, settingValues, descriptions, sliders, toggles);

    }

    

    private void drawAudioSettings(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, GameData gameData) {

        String[] settingNames = {"Sound Enabled", "Master Volume", "SFX Volume", "UI Volume", "Music Volume"};

        String[] settingValues = {

            gameData.isSoundEnabled() ? "ON" : "OFF",

            String.format("%.0f%%", gameData.getMasterVolume() * 100),

            String.format("%.0f%%", gameData.getSfxVolume() * 100),

            String.format("%.0f%%", gameData.getUiVolume() * 100),

            String.format("%.0f%%", gameData.getMusicVolume() * 100)

        };

        

        String[] descriptions = {

            "Enable or disable all sound effects",

            "Overall volume level (affects all sounds)",

            "Volume for game sound effects (explosions, hits, etc.)",

            "Volume for menu sounds (clicks, navigation, etc.)",

            "Volume for background music (not yet implemented)"

        };

        

        float[] volumes = {0, gameData.getMasterVolume(), gameData.getSfxVolume(), gameData.getUiVolume(), gameData.getMusicVolume()};

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

        String[] settingNames = {"Show Hitboxes"};

        String[] settingValues = {Game.enableHitboxes ? "ON" : "OFF"};

        String[] descriptions = {"Debug: Show collision hitboxes for player, boss, and bullets"};

        

        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, settingNames, settingValues, descriptions, false);

    }

    

    private void drawSettingsList(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, boolean showSliders) {

        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, names, values, descriptions, showSliders, null);

    }

    

    private void drawSettingsList(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, boolean showSliders, float[] sliderValues) {

        int y = 240 - (int)scrollOffset;

        FontMetrics fm;

        

        for (int i = 0; i < names.length; i++) {

            boolean isSelected = i == selectedItem;

            

            // Background box

            int boxX = (width - 700) / 2;

            int boxY = y - 20;

            int boxWidth = 700;

            int boxHeight = 70;

            

            // Update settings button position for click detection

            if (i < settingsButtons.length && settingsButtons[i] != null) {

                settingsButtons[i].setPosition(boxX, boxY);

                settingsButtons[i].setSize(boxWidth, boxHeight);

            }

            

            // Skip rendering if outside visible area

            if (y < 180 || y > height - 90) {

                y += 120;

                continue;

            }

            

            if (isSelected) {

                g.setColor(new Color(88, 91, 112, 200));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

                

                g.setColor(new Color(235, 203, 139));

                g.setStroke(new BasicStroke(2));

                g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

            } else {

                g.setColor(new Color(67, 76, 94, 150));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

            }

            

            // Setting name

            g.setFont(new Font("Arial", Font.BOLD, 20));

            g.setColor(isSelected ? new Color(235, 203, 139) : new Color(216, 222, 233));

            g.drawString(names[i], boxX + 20, boxY + 28);

            

            // Value or slider

            if (showSliders && sliderValues != null && i > 0) {

                // Draw volume slider

                int sliderX = boxX + 20;

                int sliderY = boxY + 40;

                int sliderWidth = boxWidth - 40;

                int sliderHeight = 10;

                

                // Slider background

                g.setColor(new Color(46, 52, 64));

                g.fillRoundRect(sliderX, sliderY, sliderWidth, sliderHeight, 5, 5);

                

                // Slider fill

                int fillWidth = (int)(sliderWidth * sliderValues[i]);

                g.setColor(new Color(163, 190, 140));

                g.fillRoundRect(sliderX, sliderY, fillWidth, sliderHeight, 5, 5);

                

                // Value text

                g.setFont(new Font("Arial", Font.PLAIN, 16));

                g.setColor(new Color(216, 222, 233));

                fm = g.getFontMetrics();

                g.drawString(values[i], boxX + boxWidth - fm.stringWidth(values[i]) - 20, boxY + 28);

            } else if (values[i].equals("ON") || values[i].equals("OFF")) {

                // Draw toggle switch for ON/OFF settings

                int toggleX = boxX + boxWidth - 80;

                int toggleY = boxY + 10;

                int toggleWidth = 60;

                int toggleHeight = 30;

                

                boolean isOn = values[i].equals("ON");

                

                // Toggle background

                if (isOn) {

                    g.setColor(new Color(163, 190, 140, 200));

                } else {

                    g.setColor(new Color(76, 86, 106, 200));

                }

                g.fillRoundRect(toggleX, toggleY, toggleWidth, toggleHeight, 15, 15);

                

                // Toggle circle

                int circleX = isOn ? toggleX + toggleWidth - 28 : toggleX + 2;

                int circleY = toggleY + 2;

                g.setColor(new Color(236, 239, 244));

                g.fillOval(circleX, circleY, 26, 26);

                g.setColor(new Color(216, 222, 233));

                g.setStroke(new BasicStroke(2));

                g.drawOval(circleX, circleY, 26, 26);

                

                // ON/OFF text

                g.setFont(new Font("Arial", Font.BOLD, 14));

                g.setColor(new Color(216, 222, 233));

                String toggleText = isOn ? "ON" : "OFF";

                fm = g.getFontMetrics();

                g.drawString(toggleText, toggleX - fm.stringWidth(toggleText) - 10, toggleY + 21);

            } else {

                // Regular value text

                g.setFont(new Font("Arial", Font.BOLD, 20));

                fm = g.getFontMetrics();

                g.drawString(values[i], boxX + boxWidth - fm.stringWidth(values[i]) - 20, boxY + 28);

            }

            

            // Draw description below if selected

            if (isSelected) {

                g.setFont(new Font("Arial", Font.ITALIC, 14));

                g.setColor(new Color(216, 222, 233));

                fm = g.getFontMetrics();

                g.drawString(descriptions[i], (width - fm.stringWidth(descriptions[i])) / 2, y + 75);

            }

            

            y += 120;

        }

    }

    

    private void drawSettingsListWithSliders(Graphics2D g, int width, int height, int selectedItem, double time, double scrollOffset, String[] names, String[] values, String[] descriptions, float[][] sliders, boolean[] toggles) {

        int y = 240 - (int)scrollOffset;

        FontMetrics fm;

        

        for (int i = 0; i < names.length; i++) {

            boolean isSelected = i == selectedItem;

            

            // Background box

            int boxX = (width - 700) / 2;

            int boxY = y - 20;

            int boxWidth = 700;

            int boxHeight = 70;

            

            // Update settings button position for click detection

            if (i < settingsButtons.length && settingsButtons[i] != null) {

                settingsButtons[i].setPosition(boxX, boxY);

                settingsButtons[i].setSize(boxWidth, boxHeight);

            }

            

            // Skip rendering if outside visible area

            if (y < 180 || y > height - 90) {

                y += 120;

                continue;

            }

            

            if (isSelected) {

                g.setColor(new Color(88, 91, 112, 200));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

                

                g.setColor(new Color(235, 203, 139));

                g.setStroke(new BasicStroke(2));

                g.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

            } else {

                g.setColor(new Color(67, 76, 94, 150));

                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

            }

            

            // Setting name

            g.setFont(new Font("Arial", Font.BOLD, 20));

            g.setColor(isSelected ? new Color(235, 203, 139) : new Color(216, 222, 233));

            g.drawString(names[i], boxX + 20, boxY + 28);

            

            // Check if this setting has a slider

            if (sliders[i][0] == 1) {

                // Draw slider

                int sliderX = boxX + 20;

                int sliderY = boxY + 40;

                int sliderWidth = boxWidth - 40;

                int sliderHeight = 10;

                

                float min = sliders[i][1];

                float max = sliders[i][2];

                float current = sliders[i][3];

                float progress = (current - min) / (max - min);

                

                // Slider background

                g.setColor(new Color(46, 52, 64));

                g.fillRoundRect(sliderX, sliderY, sliderWidth, sliderHeight, 5, 5);

                

                // Slider fill

                int fillWidth = (int)(sliderWidth * progress);

                g.setColor(new Color(163, 190, 140));

                g.fillRoundRect(sliderX, sliderY, fillWidth, sliderHeight, 5, 5);

                

                // Slider handle

                int handleX = sliderX + fillWidth - 8;

                int handleY = sliderY - 6;

                g.setColor(new Color(235, 203, 139));

                g.fillOval(handleX, handleY, 16, 22);

                g.setColor(new Color(216, 222, 233));

                g.setStroke(new BasicStroke(2));

                g.drawOval(handleX, handleY, 16, 22);

                

                // Value text

                g.setFont(new Font("Arial", Font.PLAIN, 16));

                g.setColor(new Color(216, 222, 233));

                fm = g.getFontMetrics();

                g.drawString(values[i], boxX + boxWidth - fm.stringWidth(values[i]) - 20, boxY + 28);

            } else if (toggles[i] || (values[i].equals("ON") || values[i].equals("OFF"))) {

                // Draw toggle switch

                int toggleX = boxX + boxWidth - 80;

                int toggleY = boxY + 10;

                int toggleWidth = 60;

                int toggleHeight = 30;

                

                boolean isOn = toggles[i];

                

                // Toggle background

                if (isOn) {

                    g.setColor(new Color(163, 190, 140, 200));

                } else {

                    g.setColor(new Color(76, 86, 106, 200));

                }

                g.fillRoundRect(toggleX, toggleY, toggleWidth, toggleHeight, 15, 15);

                

                // Toggle circle

                int circleX = isOn ? toggleX + toggleWidth - 28 : toggleX + 2;

                int circleY = toggleY + 2;

                g.setColor(new Color(236, 239, 244));

                g.fillOval(circleX, circleY, 26, 26);

                g.setColor(new Color(216, 222, 233));

                g.setStroke(new BasicStroke(2));

                g.drawOval(circleX, circleY, 26, 26);

                

                // ON/OFF text

                g.setFont(new Font("Arial", Font.BOLD, 14));

                g.setColor(new Color(216, 222, 233));

                String toggleText = isOn ? "ON" : "OFF";

                fm = g.getFontMetrics();

                g.drawString(toggleText, toggleX - fm.stringWidth(toggleText) - 10, toggleY + 21);

            } else {

                // Regular value text

                g.setFont(new Font("Arial", Font.BOLD, 20));

                fm = g.getFontMetrics();

                g.drawString(values[i], boxX + boxWidth - fm.stringWidth(values[i]) - 20, boxY + 28);

            }

            

            // Draw description below if selected

            if (isSelected) {

                g.setFont(new Font("Arial", Font.ITALIC, 14));

                g.setColor(new Color(216, 222, 233));

                fm = g.getFontMetrics();

                g.drawString(descriptions[i], (width - fm.stringWidth(descriptions[i])) / 2, y + 75);

            }

            

            y += 120;

        }

    }

    

    public void drawDebug(Graphics2D g, int width, int height, double time) {

        // Draw animated gradient with dark palette colors

        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});

        

        // Holographic title

        String title = "DEBUG MENU";

        g.setFont(new Font("Arial", Font.BOLD, 72));

        FontMetrics fm = g.getFontMetrics();

        int titleX = (width - fm.stringWidth(title)) / 2;

        int titleY = 80;

        

        // Shadow layer

        g.setColor(new Color(0, 0, 0, 100));

        g.drawString(title, titleX + 4, titleY + 4);

        

        // Gradient text (red theme for debug/cheat)

        GradientPaint titleGrad = new GradientPaint(

            titleX, titleY - 40, new Color(191, 97, 106),

            titleX, titleY + 30, new Color(208, 135, 112)

        );

        g.setPaint(titleGrad);

        g.drawString(title, titleX, titleY);

        

        // Holographic shine

        int shineOffset = (int)(Math.sin(time * 2) * 30);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        g.setColor(Color.WHITE);

        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        

        g.setColor(new Color(255, 200, 200));

        g.setFont(new Font("Arial", Font.PLAIN, 18));

        String subtitle = "Developer/Cheat Menu - Press Number Keys";

        fm = g.getFontMetrics();

        g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, 120);

        

        // Cheat options

        int startY = 200;

        int spacing = 80;

        g.setFont(new Font("Arial", Font.BOLD, 32));

        

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

            new Color(255, 215, 0),  // Gold

            new Color(0, 255, 127),  // Spring green

            new Color(138, 43, 226), // Blue violet

            new Color(255, 165, 0),  // Orange

            new Color(135, 206, 250), // Light sky blue

            new Color(163, 190, 140), // Green for active items

            new Color(255, 99, 71),  // Tomato red for risk contracts

            new Color(255, 215, 100) // Yellow for showcase unlock

        };

        

        // Show last played SFX name

        SoundManager sm = SoundManager.getInstance();

        String lastSfx = sm.getLastPlayedSound();

        long elapsed = System.currentTimeMillis() - sm.getLastPlayedTime();

        if (lastSfx != null && !lastSfx.isEmpty() && elapsed < 3000) {

            float alpha = Math.min(1f, Math.max(0f, 1f - (elapsed / 3000f)));

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            g.setFont(new Font("Arial", Font.BOLD, 22));

            FontMetrics sfxFm = g.getFontMetrics();

            String sfxLabel = "SFX: " + lastSfx;

            int sfxW = sfxFm.stringWidth(sfxLabel) + 24;

            int sfxH = 36;

            int sfxX = (width - sfxW) / 2;

            int sfxY = 140;

            g.setColor(new Color(0, 0, 0, 160));

            g.fillRoundRect(sfxX, sfxY, sfxW, sfxH, 12, 12);

            g.setColor(new Color(255, 120, 120));

            g.drawString(sfxLabel, sfxX + 12, sfxY + 25);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        }



        for (int i = 0; i < options.length; i++) {

            g.setColor(colors[i]);

            fm = g.getFontMetrics();

            int x = (width - fm.stringWidth(options[i])) / 2;

            int y = startY + i * spacing;

            

            // Draw shadow

            g.setColor(new Color(0, 0, 0, 100));

            g.drawString(options[i], x + 3, y + 3);

            

            // Draw text

            g.setColor(colors[i]);

            g.drawString(options[i], x, y);

            /*

            // Disabled water effects code (had variable conflicts)

            if (y > -60 && y < height + 60) {

                // Motion blur for waves

                g.setColor(new Color(30, 144, 255, 60));

                g.setStroke(new BasicStroke(3));

                for (int wx = 0; wx < width; wx += 40) {

                    g.drawArc(wx, y - 5, 40, 25, 0, 180);

                }

                

                // Waves

                g.setColor(new Color(30, 144, 255, 120));

                g.setStroke(new BasicStroke(3));

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

                g.setColor(new Color(255, 255, 255, 180));

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

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                g.setColor(new Color(150, 200, 255));

                double glowSize = 50 + (i * 15);

                g.fillOval((int)(player.getX() - glowSize/2), (int)(player.getY() - glowSize/2), (int)glowSize, (int)glowSize);

            }

        }

        

        // Glow around bright particles (using only X/Y position)

        // Create snapshot to avoid ConcurrentModificationException

        java.util.List<Particle> particleSnapshot = new java.util.ArrayList<>(particles);

        for (Particle p : particleSnapshot) {

            if (p != null && p.isAlive()) {

                // Apply glow to all particles with simple distance-based intensity

                for (int i = 2; i > 0; i--) {

                    float alpha = 0.05f / i;

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                    g.setColor(new Color(255, 200, 100)); // Orange glow for particles

                    double glowSize = 15 + (i * 8);

                    g.fillOval((int)(p.getX() - glowSize/2), (int)(p.getY() - glowSize/2), (int)glowSize, (int)glowSize);

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

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                

                double trailX = player.getX() - (vx * i * 0.8);

                double trailY = player.getY() - (vy * i * 0.8);

                

                g.setColor(new Color(150, 200, 255));

                g.fillOval((int)(trailX - 15), (int)(trailY - 15), 30, 30);

            }

            

            g.setComposite(originalComposite);

        }

    }

    

    private void applyChromaticAberration(Graphics2D g, int width, int height) {

        // Chromatic aberration: subtle color fringing at screen edges

        Composite originalComposite = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.03f));

        

        // Red fringe on left edge

        g.setColor(new Color(255, 0, 0));

        g.fillRect(0, 0, 15, height);

        

        // Cyan fringe on right edge

        g.setColor(new Color(0, 255, 255));

        g.fillRect(width - 15, 0, 15, height);

        

        // Blue fringe on top

        g.setColor(new Color(0, 0, 255));

        g.fillRect(0, 0, width, 15);

        

        // Yellow fringe on bottom

        g.setColor(new Color(255, 255, 0));

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

                

                vg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

                

                // Draw darkened edges

                RadialGradientPaint gradient = new RadialGradientPaint(

                    centerX, centerY, radius,

                    new float[]{0.0f, 0.2f, 1.0f},

                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 255)}

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

    

    // Optimized Balatro-style animated gradient system

    private void drawAnimatedGradient(Graphics2D g, int width, int height, double time, Color[] colors) {

        // Determine offsets based on animation setting

        int offset1 = Game.enableGradientAnimation ? (int)(Math.sin(time * 0.5) * 150) : 0;

        int offset2 = Game.enableGradientAnimation ? (int)(Math.cos(time * 0.4) * 120) : 0;

        int offset3 = Game.enableGradientAnimation ? (int)(Math.sin(time * 0.6) * 130) : 0;

        

        // Base layer (always drawn)

        GradientPaint base = new GradientPaint(

            0, offset1, colors[0],

            0, height + offset1, colors[1]

        );

        g.setPaint(base);

        g.fillRect(0, 0, width, height);

        

        // Draw additional layers based on quality setting

        if (Game.gradientQuality >= 1) {

            // Second layer (Medium and High quality)

            Color accentColor = new Color(

                colors[2].getRed(), colors[2].getGreen(), colors[2].getBlue(), 160

            );

            GradientPaint accent = new GradientPaint(

                width / 2, offset2, accentColor,

                width / 2, height + offset2, new Color(colors[2].getRed(), colors[2].getGreen(), colors[2].getBlue(), 0)

            );

            g.setPaint(accent);

            g.fillRect(0, 0, width, height);

        }

        

        if (Game.gradientQuality >= 2) {

            // Third layer (High quality only)

            Color midColor = new Color(

                colors[1].getRed(), colors[1].getGreen(), colors[1].getBlue(), 120

            );

            GradientPaint mid = new GradientPaint(

                offset3, 0, new Color(colors[1].getRed(), colors[1].getGreen(), colors[1].getBlue(), 0),

                width + offset3, height, midColor

            );

            g.setPaint(mid);

            g.fillRect(0, 0, width, height);

        }

        

        // Optional grain effect

        if (Game.enableGrainEffect) {

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.03f));

            for (int i = 0; i < 150; i++) {

                int x = (int)(Math.random() * width);

                int y = (int)(Math.random() * height);

                int size = (int)(Math.random() * 2) + 1;

                g.setColor(Color.WHITE);

                g.fillRect(x, y, size, size);

            }

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        }

    }

    

    private Color[] getLevelGradientColors(int level) {

        // Different color palettes for different level ranges

        int palette = ((level - 1) / 5) % 6;

        

        switch (palette) {

            case 0: // Levels 1-5: Dark blue theme

                return new Color[]{

                    new Color(46, 52, 64),

                    new Color(59, 66, 82),

                    new Color(76, 86, 106)

                };

            case 1: // Levels 6-10: Purple theme

                return new Color[]{

                    new Color(59, 66, 82),

                    new Color(76, 86, 106),

                    new Color(88, 91, 112)

                };

            case 2: // Levels 11-15: Red theme

                return new Color[]{

                    new Color(46, 52, 64),

                    new Color(67, 76, 94),

                    new Color(76, 86, 106)

                };

            case 3: // Levels 16-20: Green theme

                return new Color[]{

                    new Color(46, 52, 64),

                    new Color(59, 66, 82),

                    new Color(67, 76, 94)

                };

            case 4: // Levels 21-25: Orange theme

                return new Color[]{

                    new Color(59, 66, 82),

                    new Color(67, 76, 94),

                    new Color(76, 86, 106)

                };

            case 5: // Levels 26+: Teal theme

                return new Color[]{

                    new Color(46, 52, 64),

                    new Color(59, 66, 82),

                    new Color(76, 86, 106)

                };

            default:

                return new Color[]{

                    new Color(46, 52, 64),

                    new Color(59, 66, 82),

                    new Color(76, 86, 106)

                };

        }

    }

    

    // Public methods for drawing backgrounds (used by Game for zoom-out edge fill)

    public void drawAnimatedGradientPublic(Graphics2D g, int width, int height, double time, int level) {

        Color[] colors = getLevelGradientColors(level);

        drawAnimatedGradient(g, width, height, time, colors);

    }

    

    public void drawParallaxBackgroundPublic(Graphics2D g, int width, int height, int level) {

        drawParallaxBackground(g, width, height, level, 0);

    }

    

    // Getter methods for button arrays (for mouse navigation)

    public UIButton[] getMenuButtons() { return menuButtons; }

    public UIButton[] getSettingsButtons() { return settingsButtons; }

    public UIButton[] getPauseButtons() { return pauseButtons; }

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

        fg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));

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

                            new Color(0, 0, 0, 0)});

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

                slg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sa));

                slg.setColor(new Color(200, 200, 255));

                slg.setStroke(new BasicStroke(1.5f));

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
                flg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flAlpha));
                flg.setStroke(new BasicStroke(3f));
                flg.setColor(new Color(180, 210, 255));
                flg.drawLine(cx - width, cy - (int)(width * 0.12), cx + width, cy + (int)(width * 0.12));
                flg.drawLine(cx - width, cy + (int)(width * 0.12), cx + width, cy - (int)(width * 0.12));
                flg.setColor(new Color(255, 245, 210));
                flg.setStroke(new BasicStroke(1.5f));
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

                                new Color(0, 0, 0, 0)});

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

                    rg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, ra)));

                    rg.setColor(new Color(100, 180, 255));

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
                    wsg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, wAlpha));
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

                    rayG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rAlpha));

                    rayG.setColor(new Color(120, 200, 255));

                    rayG.setStroke(new BasicStroke(6f));

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

                                new Color(0, 0, 0, 0)});

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
                        ag.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, aiAlpha));
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
                pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0f, Math.min(1f, masterAlpha * auraI))));
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
                    rimg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, rimPulse * auraI * masterAlpha))));
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
                    npg.setFont(new Font("Impact", Font.BOLD, 48));
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
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na))));
                    GradientPaint panelGrad = new GradientPaint(nx, ny - fm.getAscent() - 10,
                        new Color(5, 20, 50, 230), nx, ny + 10, new Color(10, 30, 70, 200));
                    npg.setPaint(panelGrad);
                    npg.fill(panelBg);
                    // Bright cyan border
                    npg.setStroke(new BasicStroke(2.5f));
                    npg.setColor(new Color(80, 210, 255, clampA((int)(240 * na))));
                    npg.draw(panelBg);
                    // Inner highlight line
                    npg.setStroke(new BasicStroke(1f));
                    npg.setColor(new Color(120, 220, 255, clampA((int)(100 * na))));
                    npg.drawLine(nx - pad + skew + 4, ny - fm.getAscent() - 8,
                                 nx + nw + pad + skew - 4, ny - fm.getAscent() - 8);
                    // Extending horizontal accent lines from corners
                    float lineExt = (float)(ns * 140);
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na * 0.7f))));
                    npg.setStroke(new BasicStroke(2f));
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
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na * 0.5f))));
                    npg.setColor(new Color(0, 40, 90));
                    npg.drawString(pName, nx + 2, ny + 2);
                    // Text outline
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na))));
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
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na * 0.45f))));
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

                lg.setStroke(new BasicStroke(2f));

                for (int c = 0; c < 6; c++) {

                    double ba = (c * Math.PI * 2.0 / 6) + time * 2;

                    double lx = px + Math.cos(ba) * 25;

                    double ly = py + Math.sin(ba) * 25;

                    for (int s = 0; s < 4; s++) {

                        double nx = lx + Math.cos(ba + Math.sin(time * 10 + c + s) * 0.8) * (15 + s * 12);

                        double ny = ly + Math.sin(ba + Math.cos(time * 8 + c + s) * 0.8) * (15 + s * 12);

                        float ca = Math.max(0f, Math.min(1f, (0.5f - s * 0.1f) * masterAlpha));

                        lg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ca));

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

                    sg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, la));

                    sg.setColor(layer < 2 ? new Color(255, 255, 255) :

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

                    sg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,

                        Math.max(0f, Math.min(1f, 0.8f * sa))));

                    sg.setColor(new Color(255, 255, 220));

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

                        new Color(0, 0, 0, 0)});

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

                    spG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sAlpha));

                    spG.setColor(new Color(255, 120, 40));

                    spG.setStroke(new BasicStroke(10f));

                    spG.drawLine(bx + (int)(Math.cos(angle) * 50), by + (int)(Math.sin(angle) * 50),

                                 bx + (int)(Math.cos(angle) * 280), by + (int)(Math.sin(angle) * 280));

                    spG.setColor(new Color(255, 200, 100));

                    spG.setStroke(new BasicStroke(3f));

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

                    rg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, ra)));

                    rg.setColor(new Color(255, 150, 80));

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
                    wsg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, wAlpha));
                    wsg.setColor(new Color(255, 150, 60));
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

                                new Color(0, 0, 0, 0)});

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
                        ag.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, aiAlpha));
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
                bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0f, Math.min(1f, masterAlpha * ba))));
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
                    rimg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, rimPulse * ba * masterAlpha))));
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
                    npg.setFont(new Font("Impact", Font.BOLD, fs));
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
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na))));
                    GradientPaint panelGrad = new GradientPaint(nx, ny - fm.getAscent() - 10,
                        new Color(50, 10, 5, 230), nx, ny + 10, new Color(70, 15, 10, 200));
                    npg.setPaint(panelGrad);
                    npg.fill(panelBg);
                    // Red-orange border
                    npg.setStroke(new BasicStroke(2.5f));
                    npg.setColor(new Color(255, 130, 40, clampA((int)(240 * na))));
                    npg.draw(panelBg);
                    // Inner highlight line
                    npg.setStroke(new BasicStroke(1f));
                    npg.setColor(new Color(255, 180, 80, clampA((int)(100 * na))));
                    npg.drawLine(nx - pad - skew + 4, ny - fm.getAscent() - 8,
                                 nx + nw + pad - skew - 4, ny - fm.getAscent() - 8);
                    // Extending horizontal accent lines
                    float lineExt = (float)(ns * 140);
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na * 0.7f))));
                    npg.setStroke(new BasicStroke(2f));
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
                    npg.setFont(new Font("Impact", Font.PLAIN, 16));
                    FontMetrics sfm = npg.getFontMetrics();
                    String subtitle = "WARNING";
                    int subW = sfm.stringWidth(subtitle);
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na * 0.6f))));
                    npg.setColor(new Color(255, 200, 100));
                    npg.drawString(subtitle, nx + nw / 2 - subW / 2, ny - fm.getAscent() - 18);
                    // Text drop shadow
                    npg.setFont(new Font("Impact", Font.BOLD, fs));
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na * 0.5f))));
                    npg.setColor(new Color(100, 20, 0));
                    npg.drawString(bossIntroText, nx + 2, ny + 2);
                    // Text outline
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na))));
                    npg.setColor(new Color(60, 10, 0, clampA((int)(255 * na))));
                    for (int ox = -2; ox <= 2; ox++)
                        for (int oy = -2; oy <= 2; oy++)
                            if (ox * ox + oy * oy <= 5)
                                npg.drawString(bossIntroText, nx + ox, ny + oy);
                    // Gradient text fill
                    GradientPaint textGrad = new GradientPaint(nx, ny - fm.getAscent(),
                        new Color(255, 235, 180), nx, ny, new Color(255, 150, 60));
                    npg.setPaint(textGrad);
                    npg.drawString(bossIntroText, nx, ny);
                    // White highlight pass
                    npg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        Math.max(0f, Math.min(1f, na * 0.4f))));
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

                lg.setStroke(new BasicStroke(2.5f));

                for (int c = 0; c < 8; c++) {

                    double baseA = (c * Math.PI * 2.0 / 8) - time * 2.5;

                    double lx = bx + Math.cos(baseA) * 30;

                    double ly = by + Math.sin(baseA) * 30;

                    for (int s = 0; s < 5; s++) {

                        double nx = lx + Math.cos(baseA + Math.sin(time * 12 + c + s) * 0.9) * (18 + s * 15);

                        double ny = ly + Math.sin(baseA + Math.cos(time * 9 + c + s) * 0.9) * (18 + s * 15);

                        float ca = Math.max(0f, Math.min(1f, (0.4f - s * 0.07f) * masterAlpha));

                        lg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ca));

                        lg.setColor(new Color(255, 160, 80));

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
                    ebg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sAlpha));
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
                        ebg.setColor(new Color(255, 160, 80));
                    else
                        ebg.setColor(new Color(255, 255, 220));
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

                    rg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, ra)));

                    rg.setColor(r % 2 == 0 ? new Color(120, 200, 255) : new Color(255, 150, 80));

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

                    fg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, aa));

                    fg.setColor(i % 2 == 0 ? new Color(80, 160, 255) : new Color(255, 100, 40));

                    fg.setStroke(new BasicStroke(18f));

                    fg.drawLine(cx + (int)(Math.cos(angle) * 20), cy + (int)(Math.sin(angle) * 20),

                                cx + (int)(Math.cos(angle) * 200), cy + (int)(Math.sin(angle) * 200));

                    fg.setColor(new Color(255, 255, 255));

                    fg.setStroke(new BasicStroke(5f));

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

                mg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, impactA));

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

                    mg.setColor(i % 2 == 0 ? new Color(0, 0, 0) : new Color(255, 255, 255));

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

                        new Color(0, 0, 0, 0)});

                g.setPaint(expl);

                g.fillOval(cx - 200, cy - 200, 400, 400);

            }



            // VS TEXT with chromatic aberration + glow

            {

                Graphics2D vg = (Graphics2D) g.create();

                if (Game.enableAntiAliasing)

                    vg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                vg.setFont(new Font("Impact", Font.BOLD, 200));

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

                    gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,

                        Math.max(0f, Math.min(1f, glAlpha))));

                    float glScale = 1.0f + gl * 0.08f;

                    gg.translate(cx, vsY - fm.getAscent() / 2);

                    gg.scale(glScale, glScale);

                    gg.translate(-cx, -(vsY - fm.getAscent() / 2));

                    gg.setColor(new Color(255, 200, 100));

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

                    cr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,

                        Math.max(0f, Math.min(1f, va * 0.4f))));

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

                    pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,

                        Math.max(0f, Math.min(1f, masterAlpha))));

                    p.draw(pg);

                    pg.dispose();

                }

            }

        } catch (Exception e) { /* thread safety */ }



        // ===== LETTERBOX BARS =====

        if (barH > 2) {

            GradientPaint topBar = new GradientPaint(0, -M, new Color(0, 0, 0), 0, barH, new Color(15, 18, 30));

            g.setPaint(topBar);

            g.fillRect(-M, -M, width + M * 2, barH + M);

            GradientPaint botBar = new GradientPaint(0, height - barH, new Color(15, 18, 30), 0, height + M, new Color(0, 0, 0));

            g.setPaint(botBar);

            g.fillRect(-M, height - barH, width + M * 2, barH + M);



            // Gold trim

            int trimA = clampA((int)(180 + 40 * Math.sin(time * 3)));

            g.setColor(new Color(235, 203, 139, trimA));

            g.setStroke(new BasicStroke(2.5f));

            g.drawLine(-M, barH, width + M, barH);

            g.drawLine(-M, height - barH, width + M, height - barH);



            // Stage banner

            if (bossIntroPhase >= 3 && barH > 30) {

                String stageText = "STAGE " + level;

                g.setFont(new Font("Impact", Font.BOLD, 28));

                FontMetrics fm = g.getFontMetrics();

                int stW = fm.stringWidth(stageText);

                int stX = cx - stW / 2;

                int stY = height - barH / 2 + fm.getAscent() / 2 - 3;

                int stPad = 20;

                g.setColor(new Color(20, 22, 35, 200));

                g.fillRect(stX - stPad, stY - fm.getAscent() - 3, stW + stPad * 2, fm.getHeight() + 6);

                g.setColor(new Color(235, 203, 139, 160));

                g.setStroke(new BasicStroke(1.5f));

                g.drawRect(stX - stPad, stY - fm.getAscent() - 3, stW + stPad * 2, fm.getHeight() + 6);

                int dSize = 6;

                int dY = stY - fm.getAscent() / 2;

                g.setColor(new Color(235, 203, 139, 200));

                g.fillPolygon(new int[]{stX - stPad - 12, stX - stPad - 12 + dSize, stX - stPad - 12, stX - stPad - 12 - dSize},

                              new int[]{dY - dSize, dY, dY + dSize, dY}, 4);

                g.fillPolygon(new int[]{stX + stW + stPad + 12, stX + stW + stPad + 12 + dSize, stX + stW + stPad + 12, stX + stW + stPad + 12 - dSize},

                              new int[]{dY - dSize, dY, dY + dSize, dY}, 4);

                GradientPaint stp = new GradientPaint(stX, stY - fm.getAscent(),

                    new Color(255, 240, 200), stX, stY, new Color(235, 203, 139));

                g.setPaint(stp);

                g.drawString(stageText, stX, stY);

            }



            // Skip prompt

            if (bossIntroPhase >= 1 && barH > 20) {

                float skipPulse = 0.5f + 0.5f * (float)Math.sin(time * 3);

                g.setFont(new Font("Arial", Font.BOLD, 14));

                g.setColor(new Color(216, 222, 233, clampA((int)(180 * skipPulse * masterAlpha))));

                String skipText = "PRESS SPACE TO SKIP";

                FontMetrics fm = g.getFontMetrics();

                g.drawString(skipText, width - fm.stringWidth(skipText) - 20, barH / 2 + fm.getAscent() / 2 - 2);

            }

        }



        // ===== ANIME SCANLINE OVERLAY =====
        if (masterAlpha > 0.2f && bossIntroPhase >= 1 && !isPhase5) {
            Graphics2D slg2 = (Graphics2D) g.create();
            float scanAlpha = Math.max(0f, Math.min(1f, 0.04f * masterAlpha));
            slg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, scanAlpha));
            slg2.setColor(new Color(0, 0, 0));
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

                new Color[]{new Color(0, 0, 0, 0),

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
                    width / 3, cy, new Color(0, 0, 0, 0));
                g.setPaint(leftEdge);
                g.fillRect(-M, -M, width / 3 + M, height + M * 2);
                GradientPaint rightEdge = new GradientPaint(
                    width * 2 / 3, cy, new Color(0, 0, 0, 0),
                    width + M, cy, new Color(255, 90, 20, clampA((int)(45 * edgeAlpha * 255))));
                g.setPaint(rightEdge);
                g.fillRect(width * 2 / 3, -M, width / 3 + M, height + M * 2);
            }

            // Film grain overlay (cinematic noise)
            if (!isPhase5) {
                Graphics2D grg = (Graphics2D) g.create();
                grg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.max(0f, Math.min(1f, 0.025f * masterAlpha))));
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



