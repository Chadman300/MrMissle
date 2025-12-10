import java.awt.*;
import java.util.List;

public class Renderer {
    private GameData gameData;
    private ShopManager shopManager;
    
    public Renderer(GameData gameData, ShopManager shopManager) {
        this.gameData = gameData;
        this.shopManager = shopManager;
    }
    
    public void drawMenu(Graphics2D g, int width, int height, double time, int escapeTimer) {
        // Draw animated gradient background with palette colors
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 72));
        String title = "ONE HIT MAN";
        FontMetrics fm = g.getFontMetrics();
        
        // Balatro-style title with holographic shine effect
        int titleX = (width - fm.stringWidth(title)) / 2;
        int titleY = 150;
        
        // Shadow layers
        g.setColor(new Color(0, 0, 0, 100));
        g.drawString(title, titleX + 4, titleY + 4);
        
        // Gradient text effect
        GradientPaint titleGrad = new GradientPaint(
            titleX, titleY - 50, new Color(143, 188, 187), // Palette teal
            titleX, titleY + 20, new Color(136, 192, 208) // Palette cyan
        );
        g.setPaint(titleGrad);
        g.drawString(title, titleX, titleY);
        
        // Holographic shine
        int shineOffset = (int)(Math.sin(time * 2) * 30);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g.setColor(Color.WHITE);
        g.drawString(title, titleX + 2 + shineOffset / 10, titleY - 2);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String[] instructions = {
            "Rules:",
            "- You have 1 HP (One hit = death)",
            "- Boss has 1 HP (One hit = win)",
            "- Move: WASD or Arrow Keys",
            "",
            "Press SPACE to Select Level",
            "Press I for Info",
            "Press S for Stats & Loadout",
            "Press P for Shop",
            "Press O for Settings",
            escapeTimer > 0 ? "Press ESC again to Quit" : "Press ESC to Quit"
        };
        
        int y = 250;
        for (String line : instructions) {
            fm = g.getFontMetrics();
            g.drawString(line, (width - fm.stringWidth(line)) / 2, y);
            y += 35;
        }
        
        // Show money
        g.setColor(Color.GREEN);
        g.setFont(new Font("Arial", Font.BOLD, 32));
        String money = "Money: $" + gameData.getTotalMoney();
        fm = g.getFontMetrics();
        g.drawString(money, (width - fm.stringWidth(money)) / 2, height - 100);
    }
    
    public void drawInfo(Graphics2D g, int width, int height, double time) {
        // Draw animated gradient with palette colors
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "GAME INFO";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (width - fm.stringWidth(title)) / 2, 60);
        
        // Boss types section
        g.setColor(new Color(235, 203, 139)); // Palette yellow
        g.setFont(new Font("Arial", Font.BOLD, 28));
        g.drawString("BOSS TYPES (Geometric Shapes):", 50, 120);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        String[] bossInfo = {
            "Level 1-3: Triangle, Square, Pentagon - Basic patterns",
            "Level 4-6: Hexagon, Heptagon, Octagon - Mixed attacks",
            "Level 7-9: Nonagon, Decagon, 11-gon - Advanced patterns",
            "Level 10+: 12+ sided polygons - All attack types",
            "",
            "Each boss gains 1 side per level and uses more complex patterns!"
        };
        
        int y = 155;
        for (String line : bossInfo) {
            g.drawString(line, 70, y);
            y += 25;
        }
        
        // Projectile types section
        g.setColor(new Color(136, 192, 208)); // Palette cyan
        g.setFont(new Font("Arial", Font.BOLD, 28));
        g.drawString("PROJECTILE TYPES:", 50, y + 20);
        y += 55;
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        String[] projectileInfo = {
            "1. NORMAL - Standard red bullets",
            "2. FAST - Orange bullets with higher speed",
            "3. LARGE - Big blue bullets, easier to see",
            "4. HOMING - Purple bullets that track you",
            "5. BOUNCING - Green bullets that bounce off walls",
            "6. SPIRAL - Pink bullets that rotate as they move",
            "7. SPLITTING - Yellow bullets that split into 3",
            "8. ACCELERATING - Cyan bullets that speed up over time",
            "9. WAVE - Magenta bullets that move in wave patterns",
            "",
            "All projectiles (except NORMAL) have 45-frame warning indicators!"
        };
        
        for (String line : projectileInfo) {
            g.drawString(line, 70, y);
            y += 25;
        }
        
        g.setColor(Color.GRAY);
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        g.drawString("Press ESC to return to menu", 50, height - 50);
    }
    
    public void drawStats(Graphics2D g, int width, int height, double time) {
        // Draw animated Balatro-style gradient
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(30, 15, 45), new Color(45, 30, 60), new Color(60, 45, 75)});
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "STATS & LOADOUT";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (width - fm.stringWidth(title)) / 2, 80);
        
        // Show total money
        g.setColor(new Color(163, 190, 140)); // Palette green
        g.setFont(new Font("Arial", Font.BOLD, 32));
        String money = "Total Money: $" + gameData.getTotalMoney();
        fm = g.getFontMetrics();
        g.drawString(money, (width - fm.stringWidth(money)) / 2, 140);
        
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
        String[][] upgrades = {
            {"Speed Boost", "Owned: " + gameData.getSpeedUpgradeLevel(), "Active: " + gameData.getActiveSpeedLevel()},
            {"Bullet Slow", "Owned: " + gameData.getBulletSlowUpgradeLevel(), "Active: " + gameData.getActiveBulletSlowLevel()},
            {"Lucky Dodge", "Owned: " + gameData.getLuckyDodgeUpgradeLevel(), "Active: " + gameData.getActiveLuckyDodgeLevel()},
            {"Attack Window+", "Owned: " + gameData.getAttackWindowUpgradeLevel(), "Active: " + gameData.getActiveAttackWindowLevel()}
        };
        
        int y = 340;
        for (int i = 0; i < upgrades.length; i++) {
            boolean isSelected = i == selectedStatItem;
            int owned = 0;
            int active = 0;
            
            switch (i) {
                case 0: owned = gameData.getSpeedUpgradeLevel(); active = gameData.getActiveSpeedLevel(); break;
                case 1: owned = gameData.getBulletSlowUpgradeLevel(); active = gameData.getActiveBulletSlowLevel(); break;
                case 2: owned = gameData.getLuckyDodgeUpgradeLevel(); active = gameData.getActiveLuckyDodgeLevel(); break;
                case 3: owned = gameData.getAttackWindowUpgradeLevel(); active = gameData.getActiveAttackWindowLevel(); break;
            }
            
            // Draw selection box
            if (isSelected) {
                g.setColor(Color.YELLOW);
                g.setStroke(new BasicStroke(3));
                g.drawRect(width / 2 - 420, y - 35, 840, 80);
            }
            
            // Draw upgrade box
            g.setColor(new Color(40, 40, 40));
            g.fillRect(width / 2 - 410, y - 30, 820, 70);
            
            // Draw upgrade name
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 26));
            g.drawString(upgrades[i][0], width / 2 - 390, y);
            
            // Draw owned count
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.setColor(Color.CYAN);
            g.drawString(upgrades[i][1], width / 2 - 390, y + 28);
            
            // Draw minus button
            g.setColor(active > 0 ? Color.ORANGE : Color.GRAY);
            g.fillRect(width / 2 + 50, y - 20, 40, 40);
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 32));
            g.drawString("-", width / 2 + 63, y + 10);
            
            // Draw progress bar
            int barWidth = 200;
            int barX = width / 2 + 110;
            
            // Background
            g.setColor(new Color(60, 60, 60));
            g.fillRect(barX, y - 15, barWidth, 30);
            
            // Filled portion
            if (owned > 0) {
                float fillRatio = (float) active / owned;
                int fillWidth = (int) (barWidth * fillRatio);
                g.setColor(new Color(163, 190, 140)); // Palette green
                g.fillRect(barX, y - 15, fillWidth, 30);
            }
            
            // Border
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(barX, y - 15, barWidth, 30);
            
            // Draw text on bar
            g.setFont(new Font("Arial", Font.BOLD, 18));
            String barText = active + " / " + owned;
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(barText, barX + (barWidth - fm.stringWidth(barText)) / 2, y + 5);
            
            // Draw plus button
            g.setColor(active < owned ? Color.ORANGE : Color.GRAY);
            g.fillRect(width / 2 + 330, y - 20, 40, 40);
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 32));
            g.drawString("+", width / 2 + 341, y + 10);
            
            y += 100;
        }
    }
    
    public void drawLevelSelect(Graphics2D g, int width, int height, int currentLevel, int maxUnlockedLevel, double time) {
        // Draw animated Balatro-style gradient
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(20, 25, 50), new Color(35, 40, 65), new Color(50, 35, 70)});
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "SELECT LEVEL";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (width - fm.stringWidth(title)) / 2, 80);
        
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String instruction = "Use LEFT/RIGHT to select | SPACE to start | ESC to go back";
        fm = g.getFontMetrics();
        g.drawString(instruction, (width - fm.stringWidth(instruction)) / 2, 130);
        
        // Draw level grid
        int startY = 200;
        int levelsPerRow = 5;
        int boxSize = 80;
        int spacing = 40;
        
        for (int i = 1; i <= 20; i++) {
            int row = (i - 1) / levelsPerRow;
            int col = (i - 1) % levelsPerRow;
            int x = width / 2 - (levelsPerRow * (boxSize + spacing)) / 2 + col * (boxSize + spacing);
            int y = startY + row * (boxSize + spacing);
            
            boolean isUnlocked = i <= maxUnlockedLevel;
            boolean isSelected = i == currentLevel;
            
            // Draw box with card-style appearance
            if (isSelected) {
                // Glow effect
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                g.setColor(new Color(255, 255, 150));
                g.setStroke(new BasicStroke(8));
                g.drawRect(x - 6, y - 6, boxSize + 12, boxSize + 12);
                
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                g.setColor(new Color(255, 255, 200));
                g.setStroke(new BasicStroke(4));
                g.drawRect(x - 2, y - 2, boxSize + 4, boxSize + 4);
            }
            
            if (isUnlocked) {
                g.setColor(new Color(50, 150, 50));
            } else {
                g.setColor(new Color(100, 100, 100));
            }
            g.fillRect(x, y, boxSize, boxSize);
            
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(x, y, boxSize, boxSize);
            
            // Draw level number
            g.setFont(new Font("Arial", Font.BOLD, 32));
            String levelNum = String.valueOf(i);
            fm = g.getFontMetrics();
            g.drawString(levelNum, x + (boxSize - fm.stringWidth(levelNum)) / 2, y + boxSize / 2 + 10);
            
            // Draw lock icon for locked levels
            if (!isUnlocked) {
                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 40));
                //g.drawString("🔒", x + 20, y + 55);
            }
        }
    }
    
    public void drawGame(Graphics2D g, int width, int height, Player player, Boss boss, List<Bullet> bullets, List<Particle> particles, List<BeamAttack> beamAttacks, int level, double time, boolean bossVulnerable, int dodgeCombo, boolean showCombo, boolean bossDeathAnimation, double bossDeathScale, double bossDeathRotation) {
        // Draw vibrant animated sky gradient
        Color[] colors = getLevelGradientColors(level);
        drawAnimatedGradient(g, width, height, time, colors);
        
        // Draw beam attacks (behind everything else)
        for (BeamAttack beam : beamAttacks) {
            beam.draw(g, width, height);
        }
        
        // Draw particles (behind sprites)
        for (Particle particle : particles) {
            particle.draw(g);
        }
        
        // Draw player (only if not in death animation)
        if (player != null) {
            player.draw(g);
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
            if (bossVulnerable) {
                // Pulsing ring around boss
                double pulseSize = 70 + Math.sin(time * 10) * 10;
                g.setColor(new Color(235, 203, 139, 150));
                g.setStroke(new BasicStroke(4f));
                g.drawOval((int)(boss.getX() - pulseSize/2), (int)(boss.getY() - pulseSize/2), (int)pulseSize, (int)pulseSize);
                
                // "ATTACK NOW!" text
                g.setFont(new Font("Arial", Font.BOLD, 20));
                String attackText = "ATTACK NOW!";
                FontMetrics fm = g.getFontMetrics();
                int textX = (int)boss.getX() - fm.stringWidth(attackText) / 2;
                int textY = (int)boss.getY() - 50;
                g.setColor(new Color(0, 0, 0, 180));
                g.fillRoundRect(textX - 5, textY - 20, fm.stringWidth(attackText) + 10, 28, 5, 5);
                g.setColor(new Color(235, 203, 139));
                g.drawString(attackText, textX, textY);
            }
        }
        
        // Draw bullets
        for (Bullet bullet : bullets) {
            bullet.draw(g);
        }
        
        // Draw UI with better contrast
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(10, 10, 280, 90, 10, 10);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Level: " + level, 20, 35);
        g.drawString("Score: " + gameData.getScore(), 20, 65);
        g.drawString("Money: $" + (gameData.getTotalMoney() + gameData.getRunMoney()), 20, 95);
        
        // Draw combo counter
        if (showCombo && dodgeCombo > 1) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(width - 210, 10, 200, 60, 10, 10);
            
            g.setColor(new Color(163, 190, 140));
            g.setFont(new Font("Arial", Font.BOLD, 32));
            String comboText = "COMBO x" + dodgeCombo;
            g.drawString(comboText, width - 200, 50);
            
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            g.setColor(Color.WHITE);
            g.drawString("Lucky Dodges!", width - 200, 68);
        }
    }
    
    public void drawShop(Graphics2D g, int width, int height, double time) {
        // Draw animated Balatro-style gradient
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(45, 15, 55), new Color(60, 30, 70), new Color(75, 45, 85)});
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "UPGRADE SHOP";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (width - fm.stringWidth(title)) / 2, 80);
        
        // Show money
        g.setColor(new Color(163, 190, 140)); // Palette green
        g.setFont(new Font("Arial", Font.BOLD, 32));
        String money = "Money: $" + gameData.getTotalMoney();
        fm = g.getFontMetrics();
        g.drawString(money, (width - fm.stringWidth(money)) / 2, 140);
        
        // Show earnings
        g.setColor(new Color(235, 203, 139)); // Palette yellow
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String earnings = "Earned this run: $" + gameData.getRunMoney();
        fm = g.getFontMetrics();
        g.drawString(earnings, (width - fm.stringWidth(earnings)) / 2, 180);
        
        // Shop items
        String[] items = shopManager.getShopItems();
        int y = 250;
        int selectedItem = shopManager.getSelectedShopItem();
        
        for (int i = 0; i < items.length; i++) {
            boolean isSelected = i == selectedItem;
            int cost = shopManager.getItemCost(i);
            boolean canAfford = gameData.getTotalMoney() >= cost || i == 3 || i == 5;
            
            // Draw selection highlight with glow
            if (isSelected) {
                // Outer glow
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g.setColor(new Color(255, 255, 100));
                g.setStroke(new BasicStroke(8));
                g.drawRect(width / 2 - 415, y - 40, 830, 70);
                
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                g.setColor(new Color(255, 255, 150));
                g.setStroke(new BasicStroke(3));
                g.drawRect(width / 2 - 410, y - 35, 820, 60);
            }
            
            // Draw item box
            g.setColor(new Color(40, 40, 40));
            g.fillRect(width / 2 - 400, y - 30, 800, 50);
            
            // Draw item text
            if (canAfford) {
                g.setColor(Color.WHITE);
            } else {
                g.setColor(Color.GRAY);
            }
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.drawString(items[i], width / 2 - 380, y);
            
            // Draw cost
            if (i != 3 && i != 5) {
                g.setColor(canAfford ? new Color(163, 190, 140) : new Color(191, 97, 106)); // Palette green or red
                g.drawString("$" + cost, width / 2 + 320, y);
            }
            
            y += 80;
        }
        
        // Instructions
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String inst1 = "Use UP/DOWN to select | SPACE to purchase | ESC to continue";
        fm = g.getFontMetrics();
        g.drawString(inst1, (width - fm.stringWidth(inst1)) / 2, height - 50);
    }
    
    public void drawGameOver(Graphics2D g, int width, int height, double time) {
        // Draw animated Balatro-style gradient
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(50, 15, 15), new Color(65, 25, 25), new Color(45, 20, 30)});
        
        g.setColor(Color.RED);
        g.setFont(new Font("Arial", Font.BOLD, 72));
        String gameOver = "GAME OVER";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(gameOver, (width - fm.stringWidth(gameOver)) / 2, height / 2 - 50);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 32));
        String score = "Score: " + gameData.getScore();
        fm = g.getFontMetrics();
        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2 + 20);
        
        String money = "Money Earned: $" + gameData.getRunMoney();
        fm = g.getFontMetrics();
        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 + 60);
        
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String retry = "Press SPACE to return to menu";
        fm = g.getFontMetrics();
        g.drawString(retry, (width - fm.stringWidth(retry)) / 2, height / 2 + 120);
    }
    
    public void drawWin(Graphics2D g, int width, int height, double time) {
        // Darker, more subdued gradient for victory screen
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(30, 40, 30), new Color(40, 50, 45), new Color(50, 60, 55)});
        
        g.setColor(new Color(163, 190, 140)); // Palette green
        g.setFont(new Font("Arial", Font.BOLD, 72));
        String win = "VICTORY!";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(win, (width - fm.stringWidth(win)) / 2, height / 2 - 50);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 32));
        String score = "Score: " + gameData.getScore();
        fm = g.getFontMetrics();
        g.drawString(score, (width - fm.stringWidth(score)) / 2, height / 2 + 20);
        
        String money = "Money Earned: $" + gameData.getRunMoney();
        fm = g.getFontMetrics();
        g.drawString(money, (width - fm.stringWidth(money)) / 2, height / 2 + 60);
        
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        String inst = "Press SPACE to Visit Shop";
        fm = g.getFontMetrics();
        g.drawString(inst, (width - fm.stringWidth(inst)) / 2, height / 2 + 120);
    }
    
    public void drawSettings(Graphics2D g, int width, int height, int selectedItem, double time) {
        // Draw animated gradient with palette colors
        drawAnimatedGradient(g, width, height, time, new Color[]{new Color(46, 52, 64), new Color(59, 66, 82), new Color(76, 86, 106)});
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "SETTINGS";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (width - fm.stringWidth(title)) / 2, 80);
        
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        String subtitle = "Use UP/DOWN (or W/S) to navigate | SPACE or LEFT/RIGHT (or A/D) to toggle";
        fm = g.getFontMetrics();
        g.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, 120);
        
        // Settings items
        String[][] settings = {
            {"Gradient Animation", Game.enableGradientAnimation ? "ON" : "OFF"},
            {"Gradient Quality", Game.gradientQuality == 0 ? "Low (1 Layer)" : 
                                 Game.gradientQuality == 1 ? "Medium (2 Layers)" : "High (3 Layers)"},
            {"Grain Effect", Game.enableGrainEffect ? "ON" : "OFF"}
        };
        
        String[] descriptions = {
            "Animate gradient backgrounds (may affect performance)",
            "Number of gradient layers (higher = better but slower)",
            "Add grain texture overlay (performance impact)"
        };
        
        int y = 200;
        for (int i = 0; i < settings.length; i++) {
            boolean isSelected = i == selectedItem;
            
            // Draw selection highlight with glow
            if (isSelected) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g.setColor(new Color(235, 203, 139)); // Palette yellow // Palette yellow
                g.setStroke(new BasicStroke(6));
                g.drawRect(width / 2 - 360, y - 30, 720, 100);
                
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                g.setColor(new Color(235, 203, 139)); // Palette yellow
                g.setStroke(new BasicStroke(3));
                g.drawRect(width / 2 - 355, y - 25, 710, 90);
            }
            
            // Draw setting box
            g.setColor(new Color(76, 86, 106)); // Palette dark blue-gray
            g.fillRect(width / 2 - 350, y - 20, 700, 80);
            
            // Draw setting name
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString(settings[i][0], width / 2 - 330, y + 10);
            
            // Draw setting value
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.setColor(new Color(163, 190, 140)); // Palette green
            g.drawString(settings[i][1], width / 2 - 330, y + 40);
            
            // Draw description
            if (isSelected) {
                g.setFont(new Font("Arial", Font.ITALIC, 16));
                g.setColor(new Color(216, 222, 233)); // Palette light gray
                g.drawString(descriptions[i], width / 2 - 330, y + 65);
            }
            
            y += 120;
        }
        
        // Instructions
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        String esc = "Press ESC to return to menu";
        fm = g.getFontMetrics();
        g.drawString(esc, (width - fm.stringWidth(esc)) / 2, height - 50);
    }
    
    // Optimized Balatro-style animated gradient system
    private void drawAnimatedGradient(Graphics2D g, int width, int height, double time, Color[] colors) {
        // Determine offsets based on animation setting - made much more dramatic
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
            // Second layer (Medium and High quality) - increased opacity
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
            // Third layer (High quality only) - increased opacity
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
        // Vibrant sky gradients - much brighter and more colorful
        switch ((level - 1) % 7) {
            case 0: return new Color[]{new Color(135, 206, 250), new Color(100, 180, 255), new Color(70, 130, 220)}; // Bright sky blue
            case 1: return new Color[]{new Color(255, 200, 100), new Color(255, 150, 80), new Color(135, 206, 250)}; // Sunset orange to blue
            case 2: return new Color[]{new Color(255, 120, 150), new Color(180, 100, 200), new Color(100, 150, 255)}; // Pink to purple to blue
            case 3: return new Color[]{new Color(100, 220, 255), new Color(120, 200, 255), new Color(140, 180, 255)}; // Cyan sky
            case 4: return new Color[]{new Color(255, 180, 100), new Color(255, 140, 120), new Color(180, 140, 220)}; // Warm sunset
            case 5: return new Color[]{new Color(200, 230, 255), new Color(150, 200, 255), new Color(120, 170, 240)}; // Clear day sky
            default: return new Color[]{new Color(120, 200, 255), new Color(100, 180, 240), new Color(80, 150, 220)}; // Deep sky blue
        }
    }
    
    private void drawClouds(Graphics2D g, int width, int height, double time) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        
        // Draw multiple layers of clouds
        for (int layer = 0; layer < 3; layer++) {
            double speed = 0.3 + (layer * 0.15);
            int yBase = 50 + (layer * 80);
            int cloudSize = 40 + (layer * 15);
            float alpha = 0.7f - (layer * 0.15f);
            
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            
            // Draw 4-6 clouds per layer
            for (int i = 0; i < 5; i++) {
                double xOffset = ((time * speed * 10) + (i * 250)) % (width + 200);
                int x = (int)xOffset - 100;
                int y = yBase + (int)(Math.sin(time * 0.5 + i) * 20);
                
                drawCloud(g, x, y, cloudSize);
            }
        }
        
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }
    
    private void drawCloud(Graphics2D g, int x, int y, int size) {
        g.setColor(Color.WHITE);
        
        // Draw fluffy cloud shape with multiple circles
        g.fillOval(x, y, size, size);
        g.fillOval(x + size / 3, y - size / 4, (int)(size * 1.2), (int)(size * 1.2));
        g.fillOval(x + (int)(size * 0.6), y, size, size);
        g.fillOval(x + size, y + size / 6, (int)(size * 0.8), (int)(size * 0.8));
        g.fillOval(x + size / 2, y + size / 4, (int)(size * 0.9), (int)(size * 0.9));
    }
    
    private void drawScrollingTerrain(Graphics2D g, int width, int height, int level, double time) {
        // Different terrain for each level - top-down view scrolling downward
        int terrainType = (level - 1) % 7;
        double scrollSpeed = 2.0;
        double scrollOffset = (time * scrollSpeed) % 100;
        
        // Apply blur effect to terrain for motion blur
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        switch (terrainType) {
            case 0: // Forest
                drawForest(g, width, height, scrollOffset);
                break;
            case 1: // Ocean
                drawOcean(g, width, height, scrollOffset);
                break;
            case 2: // Desert
                drawDesert(g, width, height, scrollOffset);
                break;
            case 3: // Mountains
                drawMountains(g, width, height, scrollOffset);
                break;
            case 4: // Lakes/Rivers
                drawLakes(g, width, height, scrollOffset);
                break;
            case 5: // City
                drawCity(g, width, height, scrollOffset);
                break;
            case 6: // Tundra
                drawTundra(g, width, height, scrollOffset);
                break;
        }
        
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }
    
    private void drawForest(Graphics2D g, int width, int height, double scroll) {
        // Draw trees from top-down view
        for (int row = -2; row < 12; row++) {
            for (int col = 0; col < 15; col++) {
                int x = col * 90 + ((row % 2) * 45);
                int y = (int)(row * 80 - scroll * 8);
                if (y > -50 && y < height + 50) {
                    // Motion blur streak
                    g.setColor(new Color(34, 139, 34, 60));
                    g.fillOval(x, y - 8, 40, 56);
                    
                    // Tree (top-down circular canopy)
                    g.setColor(new Color(34, 139, 34, 180));
                    g.fillOval(x, y, 40, 40);
                    g.setColor(new Color(20, 100, 20, 180));
                    g.fillOval(x + 5, y + 5, 30, 30);
                }
            }
        }
    }
    
    private void drawOcean(Graphics2D g, int width, int height, double scroll) {
        // Draw waves and islands
        for (int row = -1; row < 10; row++) {
            int y = (int)(row * 100 - scroll * 8);
            if (y > -60 && y < height + 60) {
                // Motion blur for waves
                g.setColor(new Color(30, 144, 255, 60));
                g.setStroke(new BasicStroke(3));
                for (int x = 0; x < width; x += 40) {
                    g.drawArc(x, y - 5, 40, 25, 0, 180);
                }
                
                // Waves
                g.setColor(new Color(30, 144, 255, 120));
                g.setStroke(new BasicStroke(3));
                for (int x = 0; x < width; x += 40) {
                    g.drawArc(x, y, 40, 20, 0, 180);
                }
                
                // Occasional islands
                if (row % 3 == 0) {
                    int islandX = (row * 137) % (width - 100);
                    g.setColor(new Color(139, 69, 19, 150));
                    g.fillOval(islandX, y + 30, 80, 50);
                    g.setColor(new Color(34, 139, 34, 150));
                    g.fillOval(islandX + 10, y + 25, 30, 30);
                    g.fillOval(islandX + 40, y + 20, 35, 35);
                }
            }
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
}
