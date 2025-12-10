import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class Boss {
    private double x, y;
    private double rotation; // Current rotation angle
    private double targetRotation; // Target rotation angle
    private int level;
    private static final int SIZE = 100;
    private int shootTimer;
    private int shootInterval;
    private int patternType;
    private double targetX, targetY; // Target position for smooth movement
    private int moveTimer; // Timer to pick new target
    private static final double MOVE_SPEED = 1.5; // Smooth movement speed
    private int beamAttackTimer; // Timer for beam attacks
    private int beamAttackInterval; // How often to spawn beam attacks
    private List<BeamAttack> beamAttacks; // Active beam attacks
    
    private static BufferedImage planeSprite;
    private static BufferedImage helicopterSprite;
    private static BufferedImage planeShadow;
    private static BufferedImage helicopterShadow;
    
    public Boss(double x, double y, int level) {
        this.x = x;
        this.y = y;
        this.rotation = Math.PI / 2; // Start facing down
        this.targetRotation = Math.PI / 2;
        this.level = level;
        this.shootTimer = 0;
        this.shootInterval = Math.max(20, 60 - level * 5); // Faster shooting at higher levels
        // Randomize starting pattern so each boss is different
        this.patternType = (int)(Math.random() * 12);
        // Start with current position as target
        this.targetX = x;
        this.targetY = y;
        this.moveTimer = 0;
        this.beamAttacks = new ArrayList<>();
        this.beamAttackTimer = 120 + (int)(Math.random() * 60); // First beam after 2-3 seconds
        this.beamAttackInterval = Math.max(180, 300 - level * 10); // More frequent at higher levels
        loadSprites();
    }
    
    private void loadSprites() {
        if (planeSprite == null) {
            try {
                planeSprite = ImageIO.read(new File("sprites/plane.png"));
            } catch (IOException e) {
                System.err.println("Could not load plane sprite: " + e.getMessage());
            }
        }
        if (helicopterSprite == null) {
            try {
                helicopterSprite = ImageIO.read(new File("sprites/helicopter.png"));
            } catch (IOException e) {
                System.err.println("Could not load helicopter sprite: " + e.getMessage());
            }
        }
        if (planeShadow == null) {
            try {
                planeShadow = ImageIO.read(new File("sprites/plane_shadow.png"));
            } catch (IOException e) {
                System.err.println("Could not load plane shadow: " + e.getMessage());
            }
        }
        if (helicopterShadow == null) {
            try {
                helicopterShadow = ImageIO.read(new File("sprites/helicopter_shadow.png"));
            } catch (IOException e) {
                System.err.println("Could not load helicopter shadow: " + e.getMessage());
            }
        }
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight) {
        update(bullets, player, screenWidth, screenHeight, 1.0);
    }
    
    public void update(List<Bullet> bullets, Player player, int screenWidth, int screenHeight, double deltaTime) {
        // Smooth movement to target position
        moveTimer += deltaTime;
        
        // Pick a new random target every 60-120 frames (1-2 seconds)
        if (moveTimer >= 60 + Math.random() * 60) {
            moveTimer = 0;
            
            // Pick random position in the top half of screen with some margin
            targetX = SIZE + Math.random() * (screenWidth - SIZE * 2);
            targetY = SIZE + Math.random() * (screenHeight / 2.5 - SIZE * 2);
        }
        
        // Smoothly move towards target
        double dx = targetX - x;
        double dy = targetY - y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        if (distance > MOVE_SPEED) {
            // Calculate target rotation based on movement direction
            targetRotation = Math.atan2(dy, dx);
            
            // Move towards target at constant speed (scaled by delta time)
            double moveSpeedAdjusted = MOVE_SPEED * (1.0 + level * 0.1) * deltaTime;
            x += (dx / distance) * moveSpeedAdjusted;
            y += (dy / distance) * moveSpeedAdjusted;
        }
        
        // Smoothly interpolate rotation angle
        double rotationDiff = targetRotation - rotation;
        // Normalize angle difference to [-PI, PI]
        while (rotationDiff > Math.PI) rotationDiff -= 2 * Math.PI;
        while (rotationDiff < -Math.PI) rotationDiff += 2 * Math.PI;
        // Smooth rotation (15% per frame, scaled by delta time)
        rotation += rotationDiff * 0.15 * deltaTime;
        
        // Keep boss within bounds
        x = Math.max(SIZE, Math.min(screenWidth - SIZE, x));
        y = Math.max(SIZE, Math.min(screenHeight / 3, y));
        
        // Shooting pattern (scaled by delta time)
        shootTimer += deltaTime;
        if (shootTimer >= shootInterval) {
            shootTimer = 0;
            shoot(bullets, player);
        }
        
        // Beam attacks (at higher levels)
        if (level >= 2) {
            beamAttackTimer += deltaTime;
            if (beamAttackTimer >= beamAttackInterval) {
                beamAttackTimer = 0;
                spawnBeamAttack(screenWidth, screenHeight);
            }
        }
        
        // Update beam attacks
        for (int i = beamAttacks.size() - 1; i >= 0; i--) {
            BeamAttack beam = beamAttacks.get(i);
            beam.update(deltaTime);
            if (beam.isDone()) {
                beamAttacks.remove(i);
            }
        }
    }
    
    private void shoot(List<Bullet> bullets, Player player) {
        patternType = (patternType + 1) % (3 + level); // More patterns at higher levels
        
        switch (patternType % 12) {
            case 0: // Spiral pattern
                shootSpiral(bullets);
                break;
            case 1: // Circle pattern
                shootCircle(bullets, 8 + level * 2);
                break;
            case 2: // Aimed at player
                shootAtPlayer(bullets, player, 3);
                break;
            case 3: // Wave pattern
                shootWave(bullets);
                break;
            case 4: // Random spray
                shootRandom(bullets, 5 + level);
                break;
            case 5: // Fast bullets
                shootFast(bullets, player);
                break;
            case 6: // Large bullets
                shootLarge(bullets);
                break;
            case 7: // Mixed attack
                shootMixed(bullets, player);
                break;
            case 8: // Spiral bullets
                shootSpiralBullets(bullets);
                break;
            case 9: // Splitting bullets
                shootSplittingBullets(bullets);
                break;
            case 10: // Accelerating bullets
                shootAcceleratingBullets(bullets, player);
                break;
            case 11: // Wave bullets
                shootWaveBullets(bullets);
                break;
        }
    }
    
    private void shootSpiral(List<Bullet> bullets) {
        int numBullets = 6 + level;
        double angleOffset = shootTimer * 0.1;
        for (int i = 0; i < numBullets; i++) {
            double angle = (Math.PI * 2 * i / numBullets) + angleOffset;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 3, Math.sin(angle) * 3));
        }
    }
    
    private void shootCircle(List<Bullet> bullets, int numBullets) {
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5));
        }
    }
    
    private void shootAtPlayer(List<Bullet> bullets, Player player, int spread) {
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -spread; i <= spread; i++) {
            double angle = angleToPlayer + (i * 0.2);
            bullets.add(new Bullet(x, y, Math.cos(angle) * 4, Math.sin(angle) * 4));
        }
    }
    
    private void shootWave(List<Bullet> bullets) {
        int numBullets = 10 + level * 2;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI / 4 + (Math.PI / 2 * i / numBullets);
            double speed = 2 + Math.sin(i * 0.5) * 1.5;
            bullets.add(new Bullet(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
    }
    
    private void shootRandom(List<Bullet> bullets, int numBullets) {
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 2;
            bullets.add(new Bullet(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
    }
    
    private void shootFast(List<Bullet> bullets, Player player) {
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = 0; i < 5 + level; i++) {
            double angle = angleToPlayer + (Math.random() - 0.5) * 0.5;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 6, Math.sin(angle) * 6, Bullet.BulletType.FAST));
        }
    }
    
    private void shootLarge(List<Bullet> bullets) {
        int numBullets = 4 + level;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 1.5, Math.sin(angle) * 1.5, Bullet.BulletType.LARGE));
        }
    }
    
    private void shootMixed(List<Bullet> bullets, Player player) {
        // Combination attack with different bullet types
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        
        // Homing bullets
        for (int i = 0; i < 2; i++) {
            double angle = angleToPlayer + (i - 0.5) * 0.3;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5, Bullet.BulletType.HOMING));
        }
        
        // Circle of bouncing bullets
        if (level >= 3) {
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2 * i / 6;
                bullets.add(new Bullet(x, y, Math.cos(angle) * 3, Math.sin(angle) * 3, Bullet.BulletType.BOUNCING));
            }
        }
    }
    
    private void shootSpiralBullets(List<Bullet> bullets) {
        int numBullets = 5 + level;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2, Math.sin(angle) * 2, Bullet.BulletType.SPIRAL));
        }
    }
    
    private void shootSplittingBullets(List<Bullet> bullets) {
        int numBullets = 3 + level / 2;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI * 2 * i / numBullets;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5, Bullet.BulletType.SPLITTING));
        }
    }
    
    private void shootAcceleratingBullets(List<Bullet> bullets, Player player) {
        double angleToPlayer = Math.atan2(player.getY() - y, player.getX() - x);
        for (int i = -2; i <= 2; i++) {
            double angle = angleToPlayer + i * 0.3;
            bullets.add(new Bullet(x, y, Math.cos(angle) * 1.5, Math.sin(angle) * 1.5, Bullet.BulletType.ACCELERATING));
        }
    }
    
    private void shootWaveBullets(List<Bullet> bullets) {
        int numBullets = 8 + level;
        for (int i = 0; i < numBullets; i++) {
            double angle = Math.PI / 4 + (Math.PI / 2 * i / numBullets);
            bullets.add(new Bullet(x, y, Math.cos(angle) * 2.5, Math.sin(angle) * 2.5, Bullet.BulletType.WAVE));
        }
    }
    
    private void spawnBeamAttack(int screenWidth, int screenHeight) {
        // Randomly choose between vertical and horizontal beams
        boolean isVertical = Math.random() < 0.5;
        
        if (isVertical) {
            // Spawn 1-3 vertical beams depending on level
            int numBeams = 1 + (level >= 5 ? 1 : 0) + (level >= 8 ? 1 : 0);
            for (int i = 0; i < numBeams; i++) {
                double position = screenWidth * (0.2 + Math.random() * 0.6);
                double width = 40 + level * 5; // Wider beams at higher levels
                beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.VERTICAL));
            }
        } else {
            // Spawn 1-3 horizontal beams depending on level
            int numBeams = 1 + (level >= 5 ? 1 : 0) + (level >= 8 ? 1 : 0);
            for (int i = 0; i < numBeams; i++) {
                double position = screenHeight * (0.3 + Math.random() * 0.5);
                double width = 40 + level * 5; // Wider beams at higher levels
                beamAttacks.add(new BeamAttack(position, width, BeamAttack.BeamType.HORIZONTAL));
            }
        }
    }
    
    public List<BeamAttack> getBeamAttacks() {
        return beamAttacks;
    }
    
    public void draw(Graphics2D g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Odd levels = fighter planes, Even levels = helicopters
        BufferedImage sprite = (level % 2 == 1) ? planeSprite : helicopterSprite;
        BufferedImage shadow = (level % 2 == 1) ? planeShadow : helicopterShadow;
        
        if (sprite != null) {
            // Use smooth rotation angle
            // Rotate and draw sprite with shadow
            g2d.translate(x, y);
            g2d.rotate(rotation - Math.PI / 2); // Subtract 90 degrees to align sprite
            int spriteSize = SIZE * 3;
            
            // Draw shadow sprite
            if (shadow != null) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                g2d.drawImage(shadow, -spriteSize/2 + 4, -spriteSize/2 + 4, spriteSize, spriteSize, null);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
            
            // Draw sprite
            g2d.drawImage(sprite, -spriteSize/2, -spriteSize/2, spriteSize, spriteSize, null);
        } else {
            // Fallback: draw simple polygon with shadow if sprite not loaded
            int sides = Math.min(level + 2, 20);
            Polygon shape = new Polygon();
            for (int i = 0; i < sides; i++) {
                double angle = 2 * Math.PI * i / sides;
                int px = (int)(x + SIZE * Math.cos(angle));
                int py = (int)(y + SIZE * Math.sin(angle));
                shape.addPoint(px, py);
            }
            // Draw shadow
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.translate(2, 2);
            g2d.fillPolygon(shape);
            g2d.translate(-2, -2);
            // Draw shape
            g2d.setColor(new Color(0, 100, 255));
            g2d.fillPolygon(shape);
        }
        
        g2d.dispose();
        
        // Draw level indicator below boss
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        String vehicleName = getVehicleName(level);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(vehicleName, (int)x - fm.stringWidth(vehicleName)/2, (int)y + SIZE/2 + 15);
        
        g.setFont(new Font("Arial", Font.BOLD, 16));
        String levelText = "LV " + level;
        fm = g.getFontMetrics();
        g.drawString(levelText, (int)x - fm.stringWidth(levelText)/2, (int)y + SIZE/2 + 32);
    }
    
    private String getVehicleName(int lvl) {
        if (lvl % 2 == 1) {
            // Odd levels: Fighter planes
            switch ((lvl - 1) / 2 % 10) {
                case 0: return "✈ MIG-15";
                case 1: return "✈ MIG-21";
                case 2: return "✈ MIG-29";
                case 3: return "✈ SU-27";
                case 4: return "✈ SU-57";
                case 5: return "✈ F-86 SABRE";
                case 6: return "✈ F-4 PHANTOM";
                case 7: return "✈ F-15 EAGLE";
                case 8: return "✈ F-22 RAPTOR";
                default: return "✈ F-35 LIGHTNING";
            }
        } else {
            // Even levels: Helicopters
            switch ((lvl / 2 - 1) % 10) {
                case 0: return "🚁 UH-1 HUEY";
                case 1: return "🚁 AH-64 APACHE";
                case 2: return "🚁 MI-24 HIND";
                case 3: return "🚁 CH-47 CHINOOK";
                case 4: return "🚁 MI-28 HAVOC";
                case 5: return "🚁 AH-1 COBRA";
                case 6: return "🚁 KA-52 ALLIGATOR";
                case 7: return "🚁 UH-60 BLACK HAWK";
                case 8: return "🚁 MI-26 HALO";
                default: return "🚁 AH-64E GUARDIAN";
            }
        }
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    public int getSize() { return SIZE; }
    public double getHitboxRadius() { return SIZE * 0.6; } // 60% of sprite size for fitting hitbox
}
