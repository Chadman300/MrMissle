import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class GenerateSprites {
    public static void main(String[] args) {
        try {
            // Create sprites directory if it doesn't exist
            File spritesDir = new File("sprites");
            if (!spritesDir.exists()) {
                spritesDir.mkdir();
            }
            
            // Generate missile sprite (64x64)
            BufferedImage missile = createMissileSprite(64, 64);
            ImageIO.write(missile, "PNG", new File("sprites/missile.png"));
            System.out.println("Created missile.png");
            
            // Generate fighter plane sprite (128x128)
            BufferedImage plane = createPlaneSprite(128, 128);
            ImageIO.write(plane, "PNG", new File("sprites/plane.png"));
            System.out.println("Created plane.png");
            
            // Generate helicopter sprite (128x128)
            BufferedImage helicopter = createHelicopterSprite(128, 128);
            ImageIO.write(helicopter, "PNG", new File("sprites/helicopter.png"));
            System.out.println("Created helicopter.png");
            
            // Generate shadow sprites
            BufferedImage missileShadow = createMissileShadow(64, 64);
            ImageIO.write(missileShadow, "PNG", new File("sprites/missile_shadow.png"));
            System.out.println("Created missile_shadow.png");
            
            BufferedImage planeShadow = createPlaneShadow(128, 128);
            ImageIO.write(planeShadow, "PNG", new File("sprites/plane_shadow.png"));
            System.out.println("Created plane_shadow.png");
            
            BufferedImage helicopterShadow = createHelicopterShadow(128, 128);
            ImageIO.write(helicopterShadow, "PNG", new File("sprites/helicopter_shadow.png"));
            System.out.println("Created helicopter_shadow.png");
            
            System.out.println("\nAll sprites generated successfully!");
            
        } catch (IOException e) {
            System.err.println("Error generating sprites: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static BufferedImage createMissileSprite(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Missile body (pointing up)
        g.setColor(new Color(191, 97, 106)); // Red body
        int[] bodyX = {centerX, centerX - 6, centerX - 6, centerX + 6, centerX + 6};
        int[] bodyY = {8, 18, height - 12, height - 12, 18};
        g.fillPolygon(bodyX, bodyY, 5);
        
        // Nose cone
        g.setColor(new Color(150, 70, 80));
        int[] noseX = {centerX, centerX - 8, centerX + 8};
        int[] noseY = {8, 18, 18};
        g.fillPolygon(noseX, noseY, 3);
        
        // Fins
        g.setColor(new Color(143, 188, 187)); // Teal fins
        // Left fin
        int[] leftFinX = {centerX - 6, centerX - 16, centerX - 6};
        int[] leftFinY = {height - 16, height - 8, height - 8};
        g.fillPolygon(leftFinX, leftFinY, 3);
        // Right fin
        int[] rightFinX = {centerX + 6, centerX + 16, centerX + 6};
        int[] rightFinY = {height - 16, height - 8, height - 8};
        g.fillPolygon(rightFinX, rightFinY, 3);
        
        // Exhaust
        g.setColor(new Color(208, 135, 112)); // Orange
        g.fillOval(centerX - 4, height - 10, 8, 8);
        
        // Details/stripes
        g.setColor(new Color(236, 239, 244)); // White
        g.fillRect(centerX - 5, 25, 10, 2);
        g.fillRect(centerX - 5, 35, 10, 2);
        
        // Highlight
        g.setColor(new Color(255, 255, 255, 100));
        g.fillOval(centerX - 2, 15, 4, 8);
        
        g.dispose();
        return img;
    }
    
    private static BufferedImage createPlaneSprite(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Main fuselage (sleek fighter jet body)
        g.setColor(new Color(76, 86, 106)); // Dark base
        int[] bodyX = {15, 25, 55, 85, 100, 105, 100, 85, 55, 25, 15};
        int[] bodyY = {centerY, centerY - 10, centerY - 12, centerY - 10, centerY - 6, centerY, centerY + 6, centerY + 10, centerY + 12, centerY + 10, centerY};
        g.fillPolygon(bodyX, bodyY, 11);
        
        // Upper fuselage highlight
        g.setColor(new Color(94, 129, 172)); // Blue-gray
        int[] topBodyX = {20, 30, 60, 90, 102, 90, 60, 30};
        int[] topBodyY = {centerY - 2, centerY - 8, centerY - 10, centerY - 8, centerY - 3, centerY - 6, centerY - 8, centerY - 6};
        g.fillPolygon(topBodyX, topBodyY, 8);
        
        // Main delta wings
        g.setColor(new Color(129, 161, 193)); // Light blue
        // Top wing
        int[] topWingX = {35, 50, 75, 80, 70, 45};
        int[] topWingY = {centerY - 10, centerY - 10, centerY - 8, centerY - 38, centerY - 36, centerY - 10};
        g.fillPolygon(topWingX, topWingY, 6);
        // Bottom wing
        int[] botWingX = {35, 50, 75, 80, 70, 45};
        int[] botWingY = {centerY + 10, centerY + 10, centerY + 8, centerY + 38, centerY + 36, centerY + 10};
        g.fillPolygon(botWingX, botWingY, 6);
        
        // Wing tips (darker accent)
        g.setColor(new Color(76, 86, 106));
        int[] tipTopX = {70, 80, 78, 68};
        int[] tipTopY = {centerY - 36, centerY - 38, centerY - 34, centerY - 33};
        g.fillPolygon(tipTopX, tipTopY, 4);
        int[] tipBotX = {70, 80, 78, 68};
        int[] tipBotY = {centerY + 36, centerY + 38, centerY + 34, centerY + 33};
        g.fillPolygon(tipBotX, tipBotY, 4);
        
        // Air intakes
        g.setColor(new Color(59, 66, 82));
        g.fillOval(48, centerY - 14, 12, 8);
        g.fillOval(48, centerY + 6, 12, 8);
        
        // Vertical stabilizers (twin tail)
        g.setColor(new Color(94, 129, 172));
        int[] tailTop1X = {18, 28, 32, 22};
        int[] tailTop1Y = {centerY - 10, centerY - 10, centerY - 22, centerY - 20};
        g.fillPolygon(tailTop1X, tailTop1Y, 4);
        int[] tailTop2X = {18, 28, 32, 22};
        int[] tailTop2Y = {centerY + 10, centerY + 10, centerY + 22, centerY + 20};
        g.fillPolygon(tailTop2X, tailTop2Y, 4);
        
        // Cockpit canopy (glossy)
        g.setColor(new Color(136, 192, 208, 200)); // Cyan glass
        int[] canopyX = {75, 92, 98, 92, 75};
        int[] canopyY = {centerY - 8, centerY - 8, centerY, centerY + 8, centerY + 8};
        g.fillPolygon(canopyX, canopyY, 5);
        
        // Canopy frame
        g.setColor(new Color(76, 86, 106));
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(85, centerY - 7, 85, centerY + 7);
        
        // Nose cone (pointed)
        g.setColor(new Color(59, 66, 82));
        int[] noseX = {100, 115, 100};
        int[] noseY = {centerY - 6, centerY, centerY + 6};
        g.fillPolygon(noseX, noseY, 3);
        
        // Engine exhausts (twin engines)
        g.setColor(new Color(191, 97, 106)); // Red hot
        g.fillOval(10, centerY - 8, 10, 7);
        g.fillOval(10, centerY + 1, 10, 7);
        // Inner glow
        g.setColor(new Color(235, 203, 139, 180)); // Yellow glow
        g.fillOval(12, centerY - 7, 6, 5);
        g.fillOval(12, centerY + 2, 6, 5);
        
        // Body panel lines
        g.setColor(new Color(59, 66, 82));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(40, centerY - 10, 85, centerY - 9);
        g.drawLine(40, centerY + 10, 85, centerY + 9);
        g.drawLine(65, centerY - 3, 95, centerY - 2);
        
        // Wing stripes/markings
        g.setColor(new Color(236, 239, 244)); // White
        g.fillRect(50, centerY - 22, 15, 3);
        g.fillRect(50, centerY + 19, 15, 3);
        g.setColor(new Color(191, 97, 106)); // Red stripe
        g.fillRect(68, centerY - 22, 8, 3);
        g.fillRect(68, centerY + 19, 8, 3);
        
        // Weapons hardpoints (missiles under wings)
        g.setColor(new Color(76, 86, 106));
        g.fillRect(55, centerY - 28, 3, 8);
        g.fillRect(55, centerY + 20, 3, 8);
        g.setColor(new Color(191, 97, 106));
        g.fillRect(55, centerY - 28, 3, 6);
        g.fillRect(55, centerY + 20, 3, 6);
        
        g.dispose();
        return img;
    }
    
    private static BufferedImage createHelicopterSprite(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Tail boom (extended)
        g.setColor(new Color(129, 161, 193)); // Light blue-gray
        g.fillRoundRect(12, centerY - 4, 28, 8, 4, 4);
        
        // Tail boom stripes
        g.setColor(new Color(236, 239, 244)); // White
        g.fillRect(15, centerY - 3, 3, 6);
        g.fillRect(22, centerY - 3, 3, 6);
        g.fillRect(29, centerY - 3, 3, 6);
        
        // Tail rotor mount
        g.setColor(new Color(76, 86, 106));
        g.fillRect(8, centerY - 6, 6, 12);
        
        // Tail rotor blades
        g.setColor(new Color(216, 222, 233, 220));
        g.setStroke(new BasicStroke(3));
        g.drawLine(11, centerY - 15, 11, centerY + 15);
        g.setStroke(new BasicStroke(2));
        g.drawLine(6, centerY, 16, centerY);
        
        // Tail rotor hub
        g.setColor(new Color(59, 66, 82));
        g.fillOval(9, centerY - 3, 4, 6);
        
        // Main body (larger, more detailed)
        g.setColor(new Color(143, 188, 187)); // Teal body
        int[] bodyX = {30, 35, 70, 78, 78, 70, 35, 30};
        int[] bodyY = {centerY - 14, centerY - 16, centerY - 16, centerY - 10, centerY + 10, centerY + 16, centerY + 16, centerY + 14};
        g.fillPolygon(bodyX, bodyY, 8);
        
        // Body side panels (darker)
        g.setColor(new Color(129, 161, 193));
        int[] sideX = {32, 38, 68, 75, 75, 68, 38, 32};
        int[] sideY = {centerY + 2, centerY + 4, centerY + 4, centerY + 6, centerY + 8, centerY + 10, centerY + 10, centerY + 8};
        g.fillPolygon(sideX, sideY, 8);
        
        // Cockpit (large bubble)
        g.setColor(new Color(136, 192, 208, 220)); // Cyan glass
        int[] cockpitX = {70, 78, 95, 98, 95, 78};
        int[] cockpitY = {centerY - 14, centerY - 16, centerY - 14, centerY, centerY + 14, centerY + 16};
        g.fillPolygon(cockpitX, cockpitY, 6);
        
        // Cockpit frame details
        g.setColor(new Color(76, 86, 106));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(85, centerY - 14, 85, centerY + 14);
        g.drawLine(92, centerY - 10, 92, centerY + 10);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(75, centerY - 8, 95, centerY - 8);
        g.drawLine(75, centerY, 95, centerY);
        g.drawLine(75, centerY + 8, 95, centerY + 8);
        
        // Nose sensor dome
        g.setColor(new Color(59, 66, 82));
        g.fillOval(95, centerY - 5, 10, 10);
        
        // Engine housing (top of body)
        g.setColor(new Color(94, 129, 172));
        int[] engineX = {40, 50, 68, 72, 68, 50};
        int[] engineY = {centerY - 16, centerY - 22, centerY - 22, centerY - 18, centerY - 16, centerY - 16};
        g.fillPolygon(engineX, engineY, 6);
        
        // Engine vents
        g.setColor(new Color(59, 66, 82));
        g.fillRect(45, centerY - 21, 2, 5);
        g.fillRect(50, centerY - 21, 2, 5);
        g.fillRect(55, centerY - 21, 2, 5);
        g.fillRect(60, centerY - 21, 2, 5);
        
        // Exhaust port
        g.setColor(new Color(191, 97, 106)); // Red
        g.fillOval(38, centerY - 20, 8, 6);
        g.setColor(new Color(235, 203, 139, 150)); // Yellow glow
        g.fillOval(40, centerY - 19, 5, 4);
        
        // Landing skids (more detailed)
        g.setColor(new Color(76, 86, 106));
        g.setStroke(new BasicStroke(4));
        // Main skid rails
        g.drawLine(35, centerY + 22, 85, centerY + 22);
        g.drawLine(35, centerY + 28, 85, centerY + 28);
        // Vertical supports
        g.setStroke(new BasicStroke(3));
        g.drawLine(40, centerY + 16, 40, centerY + 22);
        g.drawLine(55, centerY + 16, 55, centerY + 22);
        g.drawLine(70, centerY + 16, 70, centerY + 22);
        g.drawLine(45, centerY + 16, 45, centerY + 28);
        g.drawLine(75, centerY + 16, 75, centerY + 28);
        
        // Skid cross braces
        g.setStroke(new BasicStroke(2));
        g.drawLine(35, centerY + 22, 40, centerY + 28);
        g.drawLine(85, centerY + 22, 80, centerY + 28);
        
        // Weapons pylons
        g.setColor(new Color(76, 86, 106));
        g.fillRect(42, centerY + 10, 4, 8);
        g.fillRect(72, centerY + 10, 4, 8);
        // Rocket pods
        g.setColor(new Color(94, 129, 172));
        g.fillRoundRect(38, centerY + 16, 12, 6, 3, 3);
        g.fillRoundRect(68, centerY + 16, 12, 6, 3, 3);
        g.setColor(new Color(59, 66, 82));
        for (int i = 0; i < 4; i++) {
            g.fillOval(40 + i * 2, centerY + 17, 2, 4);
            g.fillOval(70 + i * 2, centerY + 17, 2, 4);
        }
        
        // Main rotor mast
        g.setColor(new Color(59, 66, 82));
        g.fillRect(centerX - 3, centerY - 24, 6, 8);
        
        // Main rotor hub (detailed)
        g.setColor(new Color(76, 86, 106));
        g.fillOval(centerX - 8, centerY - 28, 16, 16);
        g.setColor(new Color(59, 66, 82));
        g.fillOval(centerX - 6, centerY - 26, 12, 12);
        
        // Main rotor blades (multi-layer for depth)
        g.setStroke(new BasicStroke(5));
        g.setColor(new Color(216, 222, 233, 180));
        g.drawLine(centerX - 50, centerY - 20, centerX + 50, centerY - 20);
        g.setStroke(new BasicStroke(4));
        g.setColor(new Color(216, 222, 233, 220));
        g.drawLine(centerX, centerY - 70, centerX, centerY + 30);
        g.setStroke(new BasicStroke(5));
        g.setColor(new Color(216, 222, 233, 160));
        g.drawLine(centerX - 35, centerY - 55, centerX + 35, centerY + 15);
        g.drawLine(centerX - 35, centerY + 15, centerX + 35, centerY - 55);
        
        // Rotor blade tips (red safety stripes)
        g.setColor(new Color(191, 97, 106));
        g.setStroke(new BasicStroke(6));
        g.drawLine(centerX - 50, centerY - 20, centerX - 45, centerY - 20);
        g.drawLine(centerX + 45, centerY - 20, centerX + 50, centerY - 20);
        
        // Body markings
        g.setColor(new Color(236, 239, 244)); // White
        g.fillRect(50, centerY + 6, 20, 4);
        g.setColor(new Color(191, 97, 106)); // Red
        g.fillRect(52, centerY - 10, 16, 3);
        
        // Door outlines
        g.setColor(new Color(76, 86, 106));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(48, centerY - 8, 14, 16, 2, 2);
        
        g.dispose();
        return img;
    }
    
    private static BufferedImage createMissileShadow(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = width / 2;
        
        // Simplified missile shadow shape
        g.setColor(new Color(0, 0, 0, 80));
        int[] bodyX = {centerX, centerX - 5, centerX - 5, centerX + 5, centerX + 5};
        int[] bodyY = {10, 18, height - 14, height - 14, 18};
        g.fillPolygon(bodyX, bodyY, 5);
        
        // Fin shadows
        int[] leftFinX = {centerX - 5, centerX - 14, centerX - 5};
        int[] leftFinY = {height - 18, height - 10, height - 10};
        g.fillPolygon(leftFinX, leftFinY, 3);
        int[] rightFinX = {centerX + 5, centerX + 14, centerX + 5};
        int[] rightFinY = {height - 18, height - 10, height - 10};
        g.fillPolygon(rightFinX, rightFinY, 3);
        
        g.dispose();
        return img;
    }
    
    private static BufferedImage createPlaneShadow(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerY = height / 2;
        
        // Simplified plane shadow
        g.setColor(new Color(0, 0, 0, 80));
        
        // Fuselage shadow
        int[] bodyX = {18, 28, 58, 88, 102, 106, 102, 88, 58, 28, 18};
        int[] bodyY = {centerY, centerY - 9, centerY - 11, centerY - 9, centerY - 5, centerY, centerY + 5, centerY + 9, centerY + 11, centerY + 9, centerY};
        g.fillPolygon(bodyX, bodyY, 11);
        
        // Wing shadows
        int[] topWingX = {38, 53, 78, 82, 73, 48};
        int[] topWingY = {centerY - 9, centerY - 9, centerY - 7, centerY - 36, centerY - 34, centerY - 9};
        g.fillPolygon(topWingX, topWingY, 6);
        
        int[] botWingX = {38, 53, 78, 82, 73, 48};
        int[] botWingY = {centerY + 9, centerY + 9, centerY + 7, centerY + 36, centerY + 34, centerY + 9};
        g.fillPolygon(botWingX, botWingY, 6);
        
        g.dispose();
        return img;
    }
    
    private static BufferedImage createHelicopterShadow(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        g.setColor(new Color(0, 0, 0, 80));
        
        // Tail boom shadow
        g.fillRoundRect(14, centerY - 3, 26, 6, 3, 3);
        
        // Body shadow
        int[] bodyX = {32, 37, 72, 80, 80, 72, 37, 32};
        int[] bodyY = {centerY - 12, centerY - 14, centerY - 14, centerY - 8, centerY + 8, centerY + 14, centerY + 14, centerY + 12};
        g.fillPolygon(bodyX, bodyY, 8);
        
        // Cockpit shadow
        int[] cockpitX = {72, 80, 97, 100, 97, 80};
        int[] cockpitY = {centerY - 12, centerY - 14, centerY - 12, centerY, centerY + 12, centerY + 14};
        g.fillPolygon(cockpitX, cockpitY, 6);
        
        // Rotor shadow (simplified disc)
        g.setColor(new Color(0, 0, 0, 40));
        g.fillOval(centerX - 50, centerY - 50, 100, 100);
        
        g.dispose();
        return img;
    }
}
