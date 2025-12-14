import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

public class Renderer {
    private GameData gameData;
    private ShopManager shopManager;
    
    // Menu buttons
    private UIButton[] menuButtons;
    private UIButton[] shopButtons;
    private UIButton[] statsButtons;
    private UIButton[] settingsButtons;
    private UIButton[] pauseButtons;
    
    // Parallax background layers (14 sets x 6 layers each)
    private static BufferedImage[][] backgroundLayers = new BufferedImage[14][6];
    private static boolean backgroundsLoaded = false;
    private double[] layerScrollOffsets = new double[6]; // Scroll offset for each layer
    
    // Background overlay
    private static BufferedImage overlayImage = null;
    private static boolean overlayLoaded = false;
    
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
    
    public Renderer(GameData gameData, ShopManager shopManager) {
        this.gameData = gameData;
        this.shopManager = shopManager;
        
        // Load background layers
        loadBackgroundLayers();
        
        // Load overlay image
        loadOverlay();
        
        // Initialize menu buttons (positions will be updated in drawMenu)
        menuButtons = new UIButton[6];
        menuButtons[0] = new UIButton("Select Level", 0, 0, 300, 50, new Color(191, 97, 106), new Color(220, 120, 130)); // Red
        menuButtons[1] = new UIButton("Game Info", 0, 0, 300, 50, new Color(191, 97, 106), new Color(220, 120, 130)); // Red
        menuButtons[2] = new UIButton("Stats & Loadout", 0, 0, 300, 50, new Color(191, 97, 106), new Color(220, 120, 130)); // Red
        menuButtons[3] = new UIButton("Shop", 0, 0, 300, 50, new Color(191, 97, 106), new Color(220, 120, 130)); // Red
        menuButtons[4] = new UIButton("Achievements", 0, 0, 300, 50, new Color(191, 97, 106), new Color(220, 120, 130)); // Red
        menuButtons[5] = new UIButton("Settings", 0, 0, 300, 50, new Color(191, 97, 106), new Color(220, 120, 130)); // Red
        
        // Initialize shop buttons (7 items)
        shopButtons = new UIButton[7];
        for (int i = 0; i < 7; i++) {
            shopButtons[i] = new UIButton("", 0, 0, 800, 50, new Color(76, 86, 106), new Color(180, 142, 173));
        }
        
        // Initialize stats buttons (5 items: 4 upgrades + active item)
        statsButtons = new UIButton[5];
        String[] statNames = {"Speed Boost", "Bullet Slow", "Lucky Dodge", "Attack Window+", "Active Item"};
        Color[] statColors = {new Color(143, 188, 187), new Color(136, 192, 208), new Color(180, 142, 173), new Color(235, 203, 139), new Color(163, 190, 140)};
        for (int i = 0; i < 5; i++) {
            statsButtons[i] = new UIButton(statNames[i], 0, 0, 840, 70, new Color(59, 66, 82), statColors[i]);
        }
        
        // Initialize settings buttons (11 options)
        settingsButtons = new UIButton[11];
        for (int i = 0; i < 11; i++) {
            settingsButtons[i] = new UIButton("", 0, 0, 700, 80, new Color(76, 86, 106), new Color(235, 203, 139));
        }
        
        // Initialize pause buttons
        pauseButtons = new UIButton[3];
        String[] pauseLabels = {"Resume", "Restart", "Main Menu"};
        for (int i = 0; i < 3; i++) {
            pauseButtons[i] = new UIButton(pauseLabels[i], 0, 0, 300, 60, new Color(76, 86, 106), new Color(235, 203, 139));
        }
    }
    
    private void loadBackgroundLayers() {
        if (backgroundsLoaded) return;
        try {
            int totalLoaded = 0;
            for (int set = 0; set < 14; set++) {
                for (int layer = 0; layer < 6; layer++) {
                    // Try multiple possible paths to handle different working directories
                    String[] possiblePaths = {
                        String.format("sprites/Backgrounds/background (%d)/%d.png", set + 1, layer + 1),
                        String.format("../sprites/Backgrounds/background (%d)/%d.png", set + 1, layer + 1),
                        String.format("sprites\\Backgrounds\\background (%d)\\%d.png", set + 1, layer + 1),
                        String.format("..\\sprites\\Backgrounds\\background (%d)\\%d.png", set + 1, layer + 1)
                    };
                    
                    BufferedImage image = null;
                    String successfulPath = null;
                    for (String path : possiblePaths) {
                        File file = new File(path);
                        if (file.exists()) {
                            try {
                                image = ImageIO.read(file);
                                if (image != null) {
                                    successfulPath = path;
                                    totalLoaded++;
                                    break;
                                }
                            } catch (IOException e) {
                                System.err.println("Could not load background sprite: " + path);
                            }
                        }
                    }
                    
                    if (image == null) {
                        System.err.println("Failed to load background layer " + (layer + 1) + " for set " + (set + 1) + ". Tried paths: " + String.join(", ", possiblePaths));
                    }
                    
                    // Store the image (can be null if layer doesn't exist for this set)
                    backgroundLayers[set][layer] = image;
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
    
    private void loadOverlay() {
        if (overlayLoaded) return;
        try {
            String[] possiblePaths = {
                "sprites/Backgrounds/Overlay.png",
                "../sprites/Backgrounds/Overlay.png",
                "sprites\\Backgrounds\\Overlay.png",
                "..\\sprites\\Backgrounds\\Overlay.png"
            };
            
            for (String path : possiblePaths) {
                File file = new File(path);
                if (file.exists()) {
                    overlayImage = ImageIO.read(file);
                    overlayLoaded = true;
                    System.out.println("Overlay image loaded from: " + path);
                    return;
                }
            }
            System.out.println("Overlay image not found - will run without overlay");
        } catch (Exception e) {
            System.err.println("Error loading overlay: " + e.getMessage());
            overlayLoaded = false;
        }
    }
    
    private void drawParallaxBackground(Graphics2D g, int width, int height, int level, double time) {
        if (!backgroundsLoaded) return;
        
        // Select background set based on level (cycle through 14 sets)
        int bgSet = (level - 1) % 14;
        
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
    
    public void drawMenu(Graphics2D g, int width, int height, double time, int escapeTimer, int selectedMenuItem) {
        // Draw animated gradient background with palette colors
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});
        
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
        
        // Draw buttons
        int buttonY = 280;
        int buttonSpacing = 70;
        for (int i = 0; i < menuButtons.length; i++) {
            menuButtons[i].setPosition((width - 300) / 2, buttonY + i * buttonSpacing);
            menuButtons[i].update(i == selectedMenuItem, time);
            menuButtons[i].draw(g, time);
        }
        
        // Show score and money
        g.setFont(FONT_LARGE);
        fm = g.getFontMetrics();
        
        // Score display
        g.setColor(new Color(163, 190, 140)); // Green
        String scoreText = "Score: " + gameData.getScore();
        int scoreX = width / 2 - fm.stringWidth(scoreText) - 30;
        g.drawString(scoreText, scoreX, height - 150);
        
        // Money display
        g.setColor(new Color(235, 203, 139)); // Gold
        String moneyText = "$" + gameData.getTotalMoney();
        int moneyX = width / 2 + 30;
        g.drawString(moneyText, moneyX, height - 150);
        
        // Quit hint
        if (escapeTimer > 0) {
            g.setColor(new Color(191, 97, 106)); // Palette red
            g.setFont(FONT_MEDIUM_BOLD);
            String quitText = "Press ESC again to Quit";
            fm = g.getFontMetrics();
            g.drawString(quitText, (width - fm.stringWidth(quitText)) / 2, height - 80);
        }
    }
    
    public void drawInfo(Graphics2D g, int width, int height, double time) {
        // Draw animated gradient
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});
        
        // Holographic title
        g.setFont(FONT_TITLE_MEDIUM);
        String title = "GAME INFO";
        FontMetrics fm = g.getFontMetrics();
        int titleX = (width - fm.stringWidth(title)) / 2;
        int titleY = 80;
        
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
        
        // Game Rules section
        g.setColor(new Color(143, 188, 187)); // Palette teal
        g.setFont(FONT_LARGE);
        g.drawString("CORE RULES:", 70, 120);
        
        g.setColor(Color.WHITE);
        g.setFont(FONT_INFO);
        String[] rules = {
            "• You have 1 HP - One hit = Game Over",
            "• Boss has 1 HP - One hit during attack window = Victory",
            "• Move with WASD or Arrow Keys",
            "• Attack window opens periodically - look for the yellow ring!",
            "• Beam attacks spawn at higher levels with WARNING indicators"
        };
        
        int y = 155;
        for (String line : rules) {
            g.drawString(line, 90, y);
            y += 30;
        }
        
        // Boss types section
        y += 20;
        g.setColor(new Color(235, 203, 139)); // Palette yellow
        g.setFont(new Font("Arial", Font.BOLD, 28));
        g.drawString("BOSS TYPES:", 70, y);
        y += 35;
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String[] bossInfo = {
            "Level 1-3: Triangle, Square, Pentagon - Basic patterns",
            "Level 4-6: Hexagon, Heptagon, Octagon - Mixed attacks",
            "Level 7-9: Nonagon, Decagon, 11-gon - Advanced patterns",
            "Level 10+: 12+ sided polygons - All attack types + Beams",
            "",
            "Each boss gains 1 side per level with increasingly complex patterns!"
        };
        
        for (String line : bossInfo) {
            g.drawString(line, 90, y);
            y += 28;
        }
        
        // Projectile types section
        y += 20;
        g.setColor(new Color(136, 192, 208)); // Palette cyan
        g.setFont(new Font("Arial", Font.BOLD, 28));
        g.drawString("PROJECTILE TYPES:", 70, y);
        y += 35;
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String[] projectileInfo = {
            "1. NORMAL - Standard red bullets",
            "2. FAST - Orange bullets with higher speed",
            "3. LARGE - Big blue bullets, easier to see",
            "4. HOMING - Purple bullets that track you",
            "5. BOUNCING - Green bullets that bounce off walls",
            "6. SPIRAL - Pink bullets that rotate as they move",
            "7. SPLITTING - Yellow bullets that split into 3",
            "8. ACCELERATING - Cyan bullets that speed up",
            "9. WAVE - Magenta bullets moving in wave patterns",
            "",
            "All special projectiles show 45-frame warning indicators!"
        };
        
        for (String line : projectileInfo) {
            g.drawString(line, 90, y);
            y += 28;
        }
        
        // Controls hint
        g.setColor(new Color(216, 222, 233));
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString("Press ESC to return to menu | Press R to restart during gameplay | Press P to visit shop", 70, height - 50);
    }
    
    public void drawAchievements(Graphics2D g, int width, int height, double time, AchievementManager achievementManager) {
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
        
        for (int i = 0; i < achievements.size(); i++) {
            Achievement ach = achievements.get(i);
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * (cardWidth + gapX);
            int y = startY + row * (cardHeight + gapY);
            
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
        
        // Controls hint
        g.setColor(new Color(216, 222, 233));
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String hint = "Press ESC to return to menu";
        fm = g.getFontMetrics();
        g.drawString(hint, (width - fm.stringWidth(hint)) / 2, height - 40);
    }
    
    public void drawStats(Graphics2D g, int width, int height, double time) {
        // Draw animated gradient
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});
        
        // Holographic title
        g.setFont(new Font("Arial", Font.BOLD, 60));
        String title = "STATS & LOADOUT";
        FontMetrics fm = g.getFontMetrics();
        int titleX = (width - fm.stringWidth(title)) / 2;
        int titleY = 100;
        
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
        g.setFont(new Font("Arial", Font.BOLD, 36));
        String money = "Total Money: $" + gameData.getTotalMoney();
        fm = g.getFontMetrics();
        int moneyX = (width - fm.stringWidth(money)) / 2;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
        g.fillRect(moneyX - 20, 135, fm.stringWidth(money) + 40, 45);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.drawString(money, moneyX, 165);
        
        // Show max level reached
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String maxLevel = "Highest Level Unlocked: " + gameData.getMaxUnlockedLevel();
        fm = g.getFontMetrics();
        g.drawString(maxLevel, (width - fm.stringWidth(maxLevel)) / 2, 180);
        
        // Upgrade allocation section
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 32));
        String allocTitle = "UPGRADE ALLOCATION";
        fm = g.getFontMetrics();
        g.drawString(allocTitle, (width - fm.stringWidth(allocTitle)) / 2, 240);
        
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String allocDesc = "Allocate your purchased upgrades to your loadout";
        fm = g.getFontMetrics();
        g.drawString(allocDesc, (width - fm.stringWidth(allocDesc)) / 2, 270);
        
        // Instructions
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String inst1 = "Use UP/DOWN to select | LEFT/RIGHT or A/D to adjust";
        String inst2 = "Press ESC to return to menu";
        fm = g.getFontMetrics();
        g.drawString(inst1, (width - fm.stringWidth(inst1)) / 2, height - 80);
        g.drawString(inst2, (width - fm.stringWidth(inst2)) / 2, height - 50);
        
        // Show active loadout summary
        g.setColor(Color.YELLOW);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        String summary = "Current Loadout: Speed +" + (gameData.getActiveSpeedLevel() * 15) + "% | Bullet Slow " + 
                        (gameData.getActiveBulletSlowLevel() * 5) + "% | Luck +" + gameData.getActiveLuckyDodgeLevel();
        fm = g.getFontMetrics();
        g.drawString(summary, (width - fm.stringWidth(summary)) / 2, height - 120);
    }
    
    public void drawStatsUpgrades(Graphics2D g, int width, int selectedStatItem) {
        String[] upgradeNames = {"Speed Boost", "Bullet Slow", "Lucky Dodge", "Attack Window+", "Active Item"};
        
        int y = 340;
        for (int i = 0; i < upgradeNames.length; i++) {
            boolean isSelected = i == selectedStatItem;
            
            // Special handling for Active Item (index 4)
            if (i == 4) {
                // Draw active item selection box
                g.setColor(new Color(40, 40, 40));
                g.fillRoundRect(width / 2 - 410, y - 30, 820, 70, 15, 15);
                
                // Selection indicator
                if (isSelected) {
                    g.setColor(new Color(235, 203, 139));
                    g.setStroke(new BasicStroke(3));
                    g.drawRoundRect(width / 2 - 410, y - 30, 820, 70, 15, 15);
                } else {
                    g.setColor(new Color(100, 100, 100));
                    g.setStroke(new BasicStroke(1));
                    g.drawRoundRect(width / 2 - 410, y - 30, 820, 70, 15, 15);
                }
                
                // Draw label
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 26));
                g.drawString("Active Item:", width / 2 - 390, y);
                
                // Draw current item or status
                if (gameData.hasActiveItems()) {
                    ActiveItem equippedItem = gameData.getEquippedItem();
                    if (equippedItem != null) {
                        g.setFont(new Font("Arial", Font.BOLD, 24));
                        g.setColor(new Color(163, 190, 140));
                        g.drawString(equippedItem.getName(), width / 2 - 100, y);
                        
                        g.setFont(new Font("Arial", Font.PLAIN, 16));
                        g.setColor(new Color(180, 180, 180));
                        g.drawString("← → to switch", width / 2 + 180, y + 5);
                        
                        // Draw item description
                        g.setFont(new Font("Arial", Font.ITALIC, 16));
                        g.setColor(new Color(200, 200, 150));
                        String description = equippedItem.getDescription();
                        g.drawString(description, width / 2 - 390, y + 28);
                    }
                    
                    g.setFont(new Font("Arial", Font.PLAIN, 18));
                    g.setColor(new Color(136, 192, 208));
                    g.drawString(String.format("Unlocked: %d/%d", gameData.getUnlockedItems().size(), 10), width / 2 - 390, y + 50);
                } else {
                    g.setFont(new Font("Arial", Font.ITALIC, 20));
                    g.setColor(new Color(150, 150, 150));
                    g.drawString("None Unlocked - Defeat mega bosses (levels 3, 6, 9...)", width / 2 - 100, y);
                }
                
                y += 100;
                continue;
            }
            
            int owned = 0;
            int active = 0;
            
            switch (i) {
                case 0: owned = gameData.getSpeedUpgradeLevel(); active = gameData.getActiveSpeedLevel(); break;
                case 1: owned = gameData.getBulletSlowUpgradeLevel(); active = gameData.getActiveBulletSlowLevel(); break;
                case 2: owned = gameData.getLuckyDodgeUpgradeLevel(); active = gameData.getActiveLuckyDodgeLevel(); break;
                case 3: owned = gameData.getAttackWindowUpgradeLevel(); active = gameData.getActiveAttackWindowLevel(); break;
            }
            
            // Position and draw the main upgrade button
            statsButtons[i].setPosition((width - 840) / 2, y - 30);
            
            // Draw minus button
            int minusX = width / 2 + 50;
            g.setColor(active > 0 ? new Color(191, 97, 106) : new Color(80, 80, 80)); // Red when active, gray when disabled
            g.fillRoundRect(minusX, y - 20, 40, 40, 10, 10);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(minusX, y - 20, 40, 40, 10, 10);
            g.setFont(new Font("Arial", Font.BOLD, 32));
            g.drawString("-", minusX + 13, y + 10);
            
            // Draw progress bar background
            int barX = width / 2 + 110;
            int barWidth = 200;
            
            // Background
            g.setColor(new Color(60, 60, 60));
            g.fillRoundRect(barX, y - 15, barWidth, 30, 8, 8);
            
            // Filled portion with gradient
            if (owned > 0) {
                float fillRatio = (float) active / owned;
                int fillWidth = (int) (barWidth * fillRatio);
                
                GradientPaint barGradient = new GradientPaint(
                    barX, y - 15, new Color(143, 188, 187),
                    barX + fillWidth, y + 15, new Color(163, 190, 140)
                );
                g.setPaint(barGradient);
                g.fillRoundRect(barX, y - 15, fillWidth, 30, 8, 8);
            }
            
            // Border
            g.setColor(isSelected ? new Color(235, 203, 139) : Color.WHITE);
            g.setStroke(new BasicStroke(isSelected ? 3 : 2));
            g.drawRoundRect(barX, y - 15, barWidth, 30, 8, 8);
            
            // Draw text on bar
            g.setFont(new Font("Arial", Font.BOLD, 18));
            String barText = active + " / " + owned;
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(barText, barX + (barWidth - fm.stringWidth(barText)) / 2, y + 5);
            
            // Draw plus button
            int plusX = width / 2 + 330;
            g.setColor(active < owned ? new Color(163, 190, 140) : new Color(80, 80, 80)); // Green when available, gray when maxed
            g.fillRoundRect(plusX, y - 20, 40, 40, 10, 10);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(plusX, y - 20, 40, 40, 10, 10);
            g.setFont(new Font("Arial", Font.BOLD, 32));
            g.drawString("+", plusX + 11, y + 10);
            
            // Draw the upgrade name and owned count in a styled box
            g.setColor(new Color(40, 40, 40));
            g.fillRoundRect(width / 2 - 410, y - 30, 450, 70, 15, 15);
            
            // Selection indicator
            if (isSelected) {
                g.setColor(new Color(235, 203, 139));
                g.setStroke(new BasicStroke(3));
                g.drawRoundRect(width / 2 - 410, y - 30, 450, 70, 15, 15);
            } else {
                g.setColor(new Color(100, 100, 100));
                g.setStroke(new BasicStroke(1));
                g.drawRoundRect(width / 2 - 410, y - 30, 450, 70, 15, 15);
            }
            
            // Draw upgrade name
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 26));
            g.drawString(upgradeNames[i], width / 2 - 390, y);
            
            // Draw owned count
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.setColor(new Color(136, 192, 208)); // Palette cyan
            g.drawString("Owned: " + owned, width / 2 - 390, y + 28);
            
            y += 100;
        }
    }
    
    public void drawLevelSelect(Graphics2D g, int width, int height, int currentLevel, int maxUnlockedLevel, double time, double scrollOffset) {
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
        
        // Progress indicator (dots at top)
        int dotY = 80;
        int dotSpacing = 20;
        int totalDots = 20;
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
        
        // Center Y for the level carousel
        int centerY = height / 2 - 40;
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
            g.drawString("◄", 15, centerY + 18);
        }
        if (selectedLevel < 20) {
            // Right arrow
            g.setFont(new Font("Arial", Font.BOLD, 50));
            float arrowPulse = (float)(0.5 + 0.5 * Math.sin(time * 4));
            g.setColor(new Color(150, 150, 160, (int)(100 + 100 * arrowPulse)));
            g.drawString("►", width - 55, centerY + 18);
        }
        
        // Smooth carousel: use scrollOffset to position all levels
        // Each level is spaced apart, and we scroll based on the animated offset
        int levelSpacing = width / 2; // Half screen width between levels
        int centerNodeRadius = 80; // Larger center node
        int sideNodeRadius = 50;   // Smaller side nodes
        
        // Draw levels based on scroll position (show 5 levels for smooth transitions)
        for (int i = -2; i <= 2; i++) {
            int level = selectedLevel + i;
            if (level < 1 || level > 20) continue;
            
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
                String crown = "★";
                fm = g.getFontMetrics();
                g.drawString(crown, x - fm.stringWidth(crown) / 2, centerY - nodeRadius - 10);
            }
            
            // Checkmark for completed
            if (isCompleted) {
                int checkSize = (int)(22 * scale);
                g.setFont(new Font("Arial", Font.BOLD, checkSize));
                g.setColor(new Color(100, 255, 100));
                String check = "✓";
                fm = g.getFontMetrics();
                g.drawString(check, x + nodeRadius - checkSize / 2, centerY - nodeRadius + checkSize);
            }
            
            // Lock icon for locked
            if (isLocked) {
                int lockSize = (int)(18 * scale);
                g.setFont(new Font("Arial", Font.PLAIN, lockSize));
                g.setColor(new Color(100, 100, 110));
                String lock = "🔒";
                fm = g.getFontMetrics();
                g.drawString(lock, x - fm.stringWidth(lock) / 2, centerY + nodeRadius + lockSize + 5);
            }
            
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
        
        // Draw info panel for selected level at bottom
        drawLevelInfoPanel(g, width, height, selectedLevel, currentLevel, time);
    }
    
    private void drawLevelInfoPanel(Graphics2D g, int width, int height, int selectedLevel, int currentLevel, double time) {
        int panelHeight = 140;
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
        String typeLabel = isMegaBoss ? "★ MEGA BOSS - Level " + selectedLevel : "Level " + selectedLevel;
        fm = g.getFontMetrics();
        g.drawString(typeLabel, panelX + (panelWidth - fm.stringWidth(typeLabel)) / 2, panelY + 70);
        
        // Status and time info - centered
        g.setFont(new Font("Arial", Font.BOLD, 18));
        int infoY = panelY + 100;
        
        if (isCompleted) {
            g.setColor(new Color(100, 200, 100));
            String status = "✓ DEFEATED";
            fm = g.getFontMetrics();
            
            // Show best time if available
            int bestTime = gameData.getLevelCompletionTime(selectedLevel);
            if (bestTime > 0) {
                int seconds = bestTime / 60;
                int frames = bestTime % 60;
                String timeStr = String.format("  •  Best: %d.%02ds", seconds, frames * 100 / 60);
                status += timeStr;
            }
            g.drawString(status, panelX + (panelWidth - fm.stringWidth(status)) / 2, infoY);
        } else if (isCurrent) {
            // Animated "READY" text
            float pulse = (float)(0.7 + 0.3 * Math.sin(time * 5));
            g.setColor(new Color((int)(100 * pulse + 100), (int)(200 * pulse + 55), (int)(100 * pulse + 100)));
            String startText = "► PRESS SPACE TO START ◄";
            fm = g.getFontMetrics();
            g.drawString(startText, panelX + (panelWidth - fm.stringWidth(startText)) / 2, infoY);
        } else {
            g.setColor(new Color(120, 120, 130));
            String lockText = "🔒 LOCKED";
            fm = g.getFontMetrics();
            g.drawString(lockText, panelX + (panelWidth - fm.stringWidth(lockText)) / 2, infoY);
        }
        
        // Navigation hints at very bottom
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.setColor(new Color(100, 110, 130));
        String navHint = "← →  Navigate    SPACE  Start    ESC  Back";
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
        
        // Draw contract cards
        int cardWidth = 200;
        int cardHeight = 280;
        int cardSpacing = 30;
        int totalWidth = contractNames.length * cardWidth + (contractNames.length - 1) * cardSpacing;
        int startX = (width - totalWidth) / 2;
        int cardY = 160;
        
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
            
            // Contract icon/symbol
            int iconY = cardY + offsetY + 50;
            g.setFont(new Font("Arial", Font.BOLD, 48));
            String icon = i == 0 ? "○" : i == 1 ? "◆◆" : i == 2 ? "»»" : "⊘";
            FontMetrics iconFm = g.getFontMetrics();
            Color iconColor = i == 0 ? new Color(100, 180, 100) :
                             i == 1 ? new Color(255, 100, 100) :
                             i == 2 ? new Color(100, 150, 255) : new Color(255, 180, 100);
            g.setColor(isSelected ? iconColor : new Color(100, 100, 100));
            g.drawString(icon, cardX + offsetX + (scaledWidth - iconFm.stringWidth(icon)) / 2, iconY);
            
            // Contract name
            g.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics nameFm = g.getFontMetrics();
            g.setColor(isSelected ? Color.WHITE : new Color(150, 150, 150));
            g.drawString(contractNames[i], cardX + offsetX + (scaledWidth - nameFm.stringWidth(contractNames[i])) / 2, 
                        cardY + offsetY + 90);
            
            // Multiplier
            g.setFont(new Font("Arial", Font.BOLD, 28));
            String multiplier = i == 0 ? "—" : String.format("%.2fx", contractMultipliers[i]);
            FontMetrics multFm = g.getFontMetrics();
            g.setColor(i == 0 ? new Color(150, 150, 150) : new Color(255, 215, 0));
            g.drawString(multiplier, cardX + offsetX + (scaledWidth - multFm.stringWidth(multiplier)) / 2, 
                        cardY + offsetY + 130);
            
            // Description (word wrapped)
            g.setFont(new Font("Arial", Font.PLAIN, 13));
            g.setColor(isSelected ? new Color(200, 200, 200) : new Color(120, 120, 120));
            String desc = contractDescriptions[i];
            int descY = cardY + offsetY + 160;
            int maxLineWidth = scaledWidth - 20;
            
            // Simple word wrapping
            String[] words = desc.split(" ");
            StringBuilder line = new StringBuilder();
            int lineY = descY;
            for (String word : words) {
                String testLine = line.isEmpty() ? word : line + " " + word;
                FontMetrics descFm = g.getFontMetrics();
                if (descFm.stringWidth(testLine) > maxLineWidth) {
                    g.drawString(line.toString(), cardX + offsetX + 10, lineY);
                    line = new StringBuilder(word);
                    lineY += 18;
                } else {
                    line = new StringBuilder(testLine);
                }
            }
            if (!line.isEmpty()) {
                g.drawString(line.toString(), cardX + offsetX + 10, lineY);
            }
        }
        
        // Controls hint
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        g.setColor(new Color(150, 150, 150));
        String hint = "← →  Select   |   SPACE  Confirm   |   ESC  Back";
        FontMetrics hintFm = g.getFontMetrics();
        g.drawString(hint, (width - hintFm.stringWidth(hint)) / 2, height - 40);
        
        // Warning for risky contracts
        if (selectedContract > 0) {
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.setColor(new Color(255, 100, 100, (int)(200 + 55 * Math.sin(time * 3))));
            String warning = "⚠ Higher risk = Higher reward!";
            FontMetrics warnFm = g.getFontMetrics();
            g.drawString(warning, (width - warnFm.stringWidth(warning)) / 2, height - 70);
        }
    }
    
    public void drawGame(Graphics2D g, int width, int height, Player player, Boss boss, List<Bullet> bullets, List<Particle> particles, List<BeamAttack> beamAttacks, int level, double time, boolean bossVulnerable, int vulnerabilityTimer, int dodgeCombo, boolean showCombo, boolean bossDeathAnimation, double bossDeathScale, double bossDeathRotation, double gameTime, int fps, boolean shieldActive, boolean playerInvincible, int bossHitCount, double cameraX, double cameraY, boolean introPanActive, int bossFlashTimer, int screenFlashTimer, ComboSystem comboSystem, List<DamageNumber> damageNumbers, boolean bossIntroActive, String bossIntroText, int bossIntroTimer, boolean isPaused, int selectedPauseItem, List<Achievement> pendingAchievements, int achievementNotificationTimer, boolean resurrectionAnimation, int resurrectionTimer, double resurrectionScale, double resurrectionGlow) {
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
        
        // Draw beam attacks (behind everything else)
        for (int i = 0; i < beamAttacks.size(); i++) {
            BeamAttack beam = beamAttacks.get(i);
            if (beam != null) {
                beam.draw(g, width, height, cameraX, cameraY);
            }
        }
        
        // Draw laser beam from active item
        ActiveItem equippedItem = gameData.getEquippedItem();
        if (player != null && equippedItem != null && equippedItem.isActive() && 
            equippedItem.getType() == ActiveItem.ItemType.LASER_BEAM) {
            double laserX = player.getX();
            double laserWidth = 40;
            double laserY = 0; // Beam goes to top of screen
            double laserHeight = player.getY();
            
            // Outer glow
            g.setColor(new Color(235, 203, 139, 50));
            g.fillRect((int)(laserX - laserWidth), (int)laserY, (int)(laserWidth * 2), (int)laserHeight);
            
            // Inner beam
            g.setColor(new Color(235, 203, 139, 150));
            g.fillRect((int)(laserX - laserWidth / 2), (int)laserY, (int)laserWidth, (int)laserHeight);
            
            // Core
            g.setColor(new Color(255, 255, 200, 200));
            g.fillRect((int)(laserX - laserWidth / 4), (int)laserY, (int)(laserWidth / 2), (int)laserHeight);
        }
        
        // Draw particles (behind sprites) - use snapshot to avoid ConcurrentModificationException
        java.util.List<Particle> particleSnapshot = new java.util.ArrayList<>(particles);
        for (Particle particle : particleSnapshot) {
            if (particle != null && particle.isAlive()) {
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
        
        // Draw player (only if not in death animation)
        if (player != null) {
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
            
            // Draw shield if active
            if (shieldActive) {
                int shieldRadius = 35;
                int pulseOffset = (int)(Math.sin(time * 0.1) * 3);
                
                // Outer shield glow
                g.setColor(SHIELD_GLOW);
                g.fillOval((int)player.getX() - shieldRadius - pulseOffset, 
                          (int)player.getY() - shieldRadius - pulseOffset, 
                          (shieldRadius + pulseOffset) * 2, (shieldRadius + pulseOffset) * 2);
                
                // Inner shield
                g.setColor(SHIELD_RING);
                g.setStroke(STROKE_3);
                g.drawOval((int)player.getX() - shieldRadius, 
                          (int)player.getY() - shieldRadius, 
                          shieldRadius * 2, shieldRadius * 2);
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
            // Normal boss drawing
            boss.draw(g);
            
            // Boss attack phase glow effect
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
            
            if (bossVulnerable) {
                // Pulsing ring around boss
                // Calculate color based on time remaining (green -> yellow -> red)
                double timeRatio = vulnerabilityTimer / 1200.0; // Normalize to 0-1
                Color circleColor;
                if (timeRatio > 0.5) {
                    // Green to Yellow (first half)
                    int green = 255;
                    int red = Math.max(0, Math.min(255, (int)(255 * (1 - (timeRatio - 0.5) * 2))));
                    circleColor = new Color(red, green, 0, 150);
                } else {
                    // Yellow to Red (second half)
                    int red = 255;
                    int green = Math.max(0, Math.min(255, (int)(255 * (timeRatio * 2))));
                    circleColor = new Color(red, green, 0, 150);
                }
                
                double pulseSize = 120 + Math.sin(time * 10) * 15;
                g.setColor(circleColor);
                g.setStroke(new BasicStroke(4f));
                g.drawOval((int)(boss.getX() - pulseSize/2), (int)(boss.getY() - pulseSize/2), (int)pulseSize, (int)pulseSize);
            }
        }
        
        // Draw bullets (including warnings for inactive bullets)
        for (int i = 0; i < bullets.size(); i++) {
            Bullet bullet = bullets.get(i);
            if (bullet != null) {
                bullet.draw(g);
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
            
            // Vulnerability indicator
            if (bossVulnerable) {
                // Calculate color based on time remaining (green -> yellow -> red)
                double timeRatio = vulnerabilityTimer / 1200.0;
                Color textColor;
                if (timeRatio > 0.5) {
                    int green = 255;
                    int red = Math.max(0, Math.min(255, (int)(255 * (1 - (timeRatio - 0.5) * 2))));
                    textColor = new Color(red, green, 0);
                } else {
                    int red = 255;
                    int green = Math.max(0, Math.min(255, (int)(255 * (timeRatio * 2))));
                    textColor = new Color(red, green, 0);
                }
                
                g.setColor(textColor);
                g.setFont(new Font("Arial", Font.BOLD, 14));
                String vulnText = "ATTACK NOW!";
                fm = g.getFontMetrics();
                int vulnX = barX + barWidth - fm.stringWidth(vulnText) - 15;
                g.drawString(vulnText, vulnX, barY + 18);
            }
            
            // Attack Phase indicator (Assault vs Recovery) - positioned above HP bar panel
            int phaseBarWidth = 150;
            int phaseBarHeight = 8;
            int phaseBarX = barX + barWidth - phaseBarWidth - 10;
            int phaseBarY = barY - 25;
            
            // Phase label and icon
            g.setFont(new Font("Arial", Font.BOLD, 11));
            String phaseText = boss.isAssaultPhase() ? "⚔ ASSAULT" : "◐ RECOVERY";
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
        
        // Restore original transform for UI elements (they should not be affected by camera)
        g.setTransform(originalTransform);
        
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
        
        // Draw combo milestone announcements (center screen, big dramatic text)
        if (comboSystem != null && comboSystem.getCurrentAnnouncement() != null) {
            String announcement = comboSystem.getCurrentAnnouncement();
            float announcementProgress = comboSystem.getAnnouncementTimer() / 90.0f;
            
            // Scale in then fade out effect
            float scale = announcementProgress > 0.8f ? 
                (1.0f - announcementProgress) / 0.2f * 0.5f + 1.0f : // Scale up from 1.0 to 1.5
                Math.min(1.5f, 1.5f - (0.8f - announcementProgress) * 0.25f); // Settle to 1.25
            float alpha = Math.min(1.0f, announcementProgress * 2.0f); // Fade out in last half
            
            AffineTransform announcementTransform = g.getTransform();
            int centerX = width / 2;
            int centerY = height / 3;
            
            g.translate(centerX, centerY);
            g.scale(scale, scale);
            g.translate(-centerX, -centerY);
            
            // Draw shadow
            g.setFont(new Font("Arial", Font.BOLD, 72));
            FontMetrics announceFm = g.getFontMetrics();
            int announceWidth = announceFm.stringWidth(announcement);
            
            g.setColor(new Color(0, 0, 0, (int)(180 * alpha)));
            g.drawString(announcement, centerX - announceWidth / 2 + 4, centerY + 4);
            
            // Draw main text with gradient-like color based on announcement
            Color announceColor = switch(announcement) {
                case "NICE!" -> new Color(163, 190, 140, (int)(255 * alpha)); // Green
                case "GREAT!" -> new Color(136, 192, 208, (int)(255 * alpha)); // Blue
                case "AMAZING!" -> new Color(235, 203, 139, (int)(255 * alpha)); // Yellow
                case "INCREDIBLE!" -> new Color(208, 135, 112, (int)(255 * alpha)); // Orange
                case "LEGENDARY!" -> new Color(180, 142, 173, (int)(255 * alpha)); // Purple
                case "GODLIKE!" -> new Color(191, 97, 106, (int)(255 * alpha)); // Red
                case "IMPOSSIBLE!" -> new Color(255, 215, 0, (int)(255 * alpha)); // Gold
                default -> new Color(255, 255, 255, (int)(255 * alpha));
            };
            g.setColor(announceColor);
            g.drawString(announcement, centerX - announceWidth / 2, centerY);
            
            g.setTransform(announcementTransform);
        }
        
        // Draw close call / perfect dodge indicators below combo
        if (comboSystem != null && (comboSystem.getCloseCallCount() > 0 || comboSystem.getPerfectDodgeCount() > 0)) {
            int indicatorY = showCombo && dodgeCombo > 1 ? 70 : 10;
            g.setFont(new Font("Arial", Font.BOLD, 14));
            
            if (comboSystem.getPerfectDodgeCount() > 0) {
                g.setColor(new Color(255, 215, 0)); // Gold for perfect
                g.drawString("⚡ PERFECT x" + comboSystem.getPerfectDodgeCount(), width - 200, indicatorY);
                indicatorY += 18;
            }
            if (comboSystem.getCloseCallCount() > 0) {
                g.setColor(new Color(163, 190, 140)); // Green for close call
                g.drawString("★ CLOSE x" + comboSystem.getCloseCallCount(), width - 200, indicatorY);
            }
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
        
        // Draw boss intro cinematic
        if (bossIntroActive) {
            // Dark overlay - extended beyond screen to prevent shake edge visibility
            int shakeMargin = 250; // Extra margin to cover screen shake offset (extends all directions)
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(-shakeMargin, -shakeMargin, width + shakeMargin * 2, height + shakeMargin * 2);
            
            // Boss name/level with fade in
            float introAlpha = Math.max(0.0f, Math.min(1.0f, bossIntroTimer / 30f));
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, introAlpha));
            g2d.setFont(new Font("Arial", Font.BOLD, 72));
            g2d.setColor(Color.WHITE);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(bossIntroText, (width - fm.stringWidth(bossIntroText)) / 2, height / 2);
            g2d.dispose();
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
            String pauseText = "PAUSED";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(pauseText, (width - fm.stringWidth(pauseText)) / 2, height / 3);
            
            // Menu options using UIButtons
            int buttonY = height / 2 - 30;
            for (int i = 0; i < pauseButtons.length; i++) {
                pauseButtons[i].setPosition((width - 300) / 2, buttonY + i * 80);
                pauseButtons[i].update(i == selectedPauseItem, time);
                pauseButtons[i].draw(g, time);
            }
        }
        
        // Draw achievement notification
        if (pendingAchievements != null && !pendingAchievements.isEmpty() && achievementNotificationTimer > 0 && !isPaused) {
            Achievement ach = pendingAchievements.get(0);
            float alpha = Math.max(0.0f, Math.min(1.0f, achievementNotificationTimer < 30 ? achievementNotificationTimer / 30f : 1.0f));
            
            int notifX = width - 420;
            int notifY = 200;
            
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
        
        // Apply vignette effect at the end (darkens edges)
        if (Game.enableVignette) {
            applyVignette(g, width, height);
        }
    }
    
    public void drawShop(Graphics2D g, int width, int height, double time) {
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
        
        for (int i = 0; i < items.length; i++) {
            int cost = shopManager.getItemCost(i);
            boolean canAfford = gameData.getTotalMoney() >= cost || i == 5;
            
            // Build button text with cost
            String buttonText = items[i];
            if (i != 5) {
                buttonText += "  -  $" + cost;
            }
            
            // Update button appearance based on affordability
            if (!canAfford) {
                shopButtons[i] = new UIButton(buttonText, 0, 0, 800, 50, new Color(60, 60, 60), new Color(100, 100, 100));
            } else {
                shopButtons[i] = new UIButton(buttonText, 0, 0, 800, 50, new Color(76, 86, 106), new Color(180, 142, 173));
            }
            
            shopButtons[i].setPosition((width - 800) / 2, y - 30);
            shopButtons[i].update(i == selectedItem, time);
            shopButtons[i].draw(g, time);
            
            y += 80;
        }
        
        // Instructions
        g.setColor(new Color(216, 222, 233));
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String inst1 = "Use UP/DOWN to select | SPACE to purchase | ESC to continue";
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
        
        // Show persistent stats
        g.setFont(new Font("Arial", Font.PLAIN, 22));
        g.setColor(new Color(180, 180, 190));
        String totalMoney = "Total Money: $" + gameData.getTotalMoney();
        fm = g.getFontMetrics();
        g.drawString(totalMoney, (width - fm.stringWidth(totalMoney)) / 2, height / 2 + 90);
        
        String bestRun = "Best Run: Level " + Math.max(gameData.getBestRunLevel(), gameData.getCurrentLevel());
        fm = g.getFontMetrics();
        g.drawString(bestRun, (width - fm.stringWidth(bestRun)) / 2, height / 2 + 115);
        
        // Show extra lives remaining
        if (gameData.getExtraLives() > 0) {
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.setColor(new Color(255, 215, 0)); // Gold color
            String livesText = "★ Extra Lives: " + gameData.getExtraLives() + " ★";
            fm = g.getFontMetrics();
            g.drawString(livesText, (width - fm.stringWidth(livesText)) / 2, height / 2 + 145);
        }
        
        // Controls
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        g.setColor(new Color(216, 222, 233));
        String retry = "SPACE - New Run  |  ESC - Main Menu";
        fm = g.getFontMetrics();
        g.drawString(retry, (width - fm.stringWidth(retry)) / 2, height / 2 + 170);
        
        // Roguelike reminder
        g.setFont(new Font("Arial", Font.ITALIC, 18));
        g.setColor(new Color(163, 190, 140));
        String keep = "Your upgrades and items are saved!";
        fm = g.getFontMetrics();
        g.drawString(keep, (width - fm.stringWidth(keep)) / 2, height / 2 + 200);
    }
    
    public void drawWin(Graphics2D g, int width, int height, double time, double bossKillTime) {
        // Draw animated gradient
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});
        
        // Holographic title
        g.setFont(new Font("Arial", Font.BOLD, 84));
        String win = "VICTORY!";
        FontMetrics fm = g.getFontMetrics();
        int titleX = (width - fm.stringWidth(win)) / 2;
        int titleY = height / 2 - 120;
        
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
        g.setFont(new Font("Arial", Font.BOLD, 36));
        String score = "Score: " + gameData.getScore();
        fm = g.getFontMetrics();
        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2 - 10);
        
        String money = "Money Earned: $" + gameData.getRunMoney();
        fm = g.getFontMetrics();
        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 + 30);
        
        // Display boss kill time
        int minutes = (int)(bossKillTime / 60);
        int seconds = (int)(bossKillTime % 60);
        int milliseconds = (int)((bossKillTime % 1) * 100);
        String timeStr = String.format("Time: %d:%02d.%02d", minutes, seconds, milliseconds);
        fm = g.getFontMetrics();
        g.setColor(new Color(255, 215, 0)); // Gold color for time
        g.drawString(timeStr, (width - fm.stringWidth(timeStr)) / 2, height / 2 + 70);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String inst = "Press SPACE to Visit Shop";
        fm = g.getFontMetrics();
        g.drawString(inst, (width - fm.stringWidth(inst)) / 2, height / 2 + 130);
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
        String[] categories = {"GRAPHICS", "AUDIO", "DEBUG"};
        int tabWidth = 200;
        int tabStartX = (width - categories.length * tabWidth) / 2;
        int tabY = 130;
        
        g.setFont(new Font("Arial", Font.BOLD, 20));
        for (int i = 0; i < categories.length; i++) {
            int tabX = tabStartX + i * tabWidth;
            boolean isSelected = i == selectedCategory;
            
            // Tab background
            if (isSelected) {
                g.setColor(new Color(88, 91, 112, 200));
            } else {
                g.setColor(new Color(67, 76, 94, 150));
            }
            g.fillRoundRect(tabX, tabY, tabWidth - 10, 40, 10, 10);
            
            // Tab border
            if (isSelected) {
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
        String subtitle = "TAB to switch category | Arrow keys to adjust | Mouse wheel to scroll";
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
        String[] settingNames = {"Background Mode", "Gradient Animation", "Gradient Quality", "Grain Effect", "Particle Effects", "Shadows", "Bloom/Glow", "Motion Blur", "Chromatic Aberration", "Vignette"};
        String[] settingValues = {
            Game.backgroundMode == 0 ? "Gradient" : Game.backgroundMode == 1 ? "Parallax" : "Static",
            Game.enableGradientAnimation ? "ON" : "OFF",
            Game.gradientQuality == 0 ? "Low" : Game.gradientQuality == 1 ? "Medium" : "High",
            Game.enableGrainEffect ? "ON" : "OFF",
            Game.enableParticles ? "ON" : "OFF",
            Game.enableShadows ? "ON" : "OFF",
            Game.enableBloom ? "ON" : "OFF",
            Game.enableMotionBlur ? "ON" : "OFF",
            Game.enableChromaticAberration ? "ON" : "OFF",
            Game.enableVignette ? "ON" : "OFF"
        };
        
        String[] descriptions = {
            "Choose between gradient, parallax images, or static image background",
            "Animate gradient backgrounds (may affect performance)",
            "Number of gradient layers (higher = better but slower)",
            "Add grain texture overlay (performance impact)",
            "Enable particle effects (trails, explosions, etc.)",
            "Enable shadows for all objects (planes, bullets)",
            "Glow effect on bright objects (performance impact)",
            "Blur effect on fast moving objects (performance impact)",
            "Color fringing on screen edges (cinematic effect)",
            "Darken screen edges (focuses attention on center)"
        };
        
        drawSettingsList(g, width, height, selectedItem, time, scrollOffset, settingNames, settingValues, descriptions, false);
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
            // Skip rendering if outside visible area
            if (y < 180 || y > height - 90) {
                y += 120;
                continue;
            }
            
            boolean isSelected = i == selectedItem;
            
            // Background box
            int boxX = (width - 700) / 2;
            int boxY = y - 20;
            int boxWidth = 700;
            int boxHeight = 70;
            
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
            "[6] Unlock All Active Items"
        };
        
        Color[] colors = {
            new Color(255, 215, 0),  // Gold
            new Color(0, 255, 127),  // Spring green
            new Color(138, 43, 226), // Blue violet
            new Color(255, 165, 0),  // Orange
            new Color(135, 206, 250), // Light sky blue
            new Color(163, 190, 140) // Green for active items
        };
        
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
        
        // Glow around vulnerable boss
        if (bossVulnerable && boss != null) {
            for (int i = 3; i > 0; i--) {
                float alpha = 0.15f / i;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setColor(new Color(255, 255, 0));
                double glowSize = boss.getSize() + (i * 25);
                g.fillOval((int)(boss.getX() - glowSize/2), (int)(boss.getY() - glowSize/2), (int)glowSize, (int)glowSize);
            }
        }
        
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
    
    private void applyVignette(Graphics2D g, int width, int height) {
        // Vignette effect: darken edges to focus attention on center
        Composite originalComposite = g.getComposite();
        
        // Create radial gradient from center
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = (int)Math.sqrt(centerX * centerX + centerY * centerY) * 3;
        
        // Draw multiple layers for smooth gradient
        for (int i = 0; i < 4; i++) {
            float alpha = Math.min(1.0f, 0.36f * (i + 1)); // Cap at 1.0f to avoid exceeding range
            int innerRadius = radius - (radius / 4) * (4 - i);
            int outerRadius = radius;
            
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            
            // Draw darkened edges - starts darkening much earlier (0.2 instead of 0.4)
            RadialGradientPaint gradient = new RadialGradientPaint(
                centerX, centerY, outerRadius,
                new float[]{0.0f, 0.2f, 1.0f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 255)}
            );
            g.setPaint(gradient);
            g.fillRect(0, 0, width, height);
        }
        
        g.setComposite(originalComposite);
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
    
    // Getter methods for button arrays (for mouse navigation)
    public UIButton[] getMenuButtons() { return menuButtons; }
    public UIButton[] getSettingsButtons() { return settingsButtons; }
    public UIButton[] getPauseButtons() { return pauseButtons; }
    public UIButton[] getShopButtons() { return shopButtons; }
    public UIButton[] getStatsButtons() { return statsButtons; }
}
