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
        int[] leftFinX = {centerX - 6, centerX - 16, centerX - 6};
        int[] leftFinY = {height - 16, height - 8, height - 8};
        g.fillPolygon(leftFinX, leftFinY, 3);
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
        
        // Colors from reference images
        Color darkBody = new Color(67, 76, 94);
        Color mediumBody = new Color(88, 101, 125);
        Color lightBody = new Color(108, 122, 147);
        Color cockpitGlass = new Color(180, 142, 173);
        Color accent = new Color(139, 147, 175);
        Color darkAccent = new Color(46, 52, 64);
        
        // Main fuselage (elongated teardrop)
        g.setColor(mediumBody);
        int[] fuselageX = {centerX, centerX - 10, centerX - 12, centerX - 12, centerX - 10, centerX - 6, centerX + 6, centerX + 10, centerX + 12, centerX + 12, centerX + 10};
        int[] fuselageY = {20, 30, 45, 75, 90, 105, 105, 90, 75, 45, 30};
        g.fillPolygon(fuselageX, fuselageY, 11);
        
        // Cockpit area
        g.setColor(cockpitGlass);
        int[] cockpitX = {centerX, centerX - 6, centerX - 7, centerX - 6, centerX + 6, centerX + 7, centerX + 6};
        int[] cockpitY = {28, 38, 52, 62, 62, 52, 38};
        g.fillPolygon(cockpitX, cockpitY, 7);
        
        // Cockpit highlight
        g.setColor(new Color(200, 180, 210, 150));
        g.fillOval(centerX - 4, 40, 8, 12);
        
        // Nose cone
        g.setColor(darkBody);
        int[] noseX = {centerX, centerX - 5, centerX + 5};
        int[] noseY = {20, 28, 28};
        g.fillPolygon(noseX, noseY, 3);
        
        // Main delta wings (swept back)
        g.setColor(lightBody);
        int[] leftWingX = {centerX - 12, centerX - 42, centerX - 38, centerX - 12};
        int[] leftWingY = {50, 70, 78, 70};
        g.fillPolygon(leftWingX, leftWingY, 4);
        int[] rightWingX = {centerX + 12, centerX + 42, centerX + 38, centerX + 12};
        int[] rightWingY = {50, 70, 78, 70};
        g.fillPolygon(rightWingX, rightWingY, 4);
        
        // Wing tips
        g.setColor(darkBody);
        int[] leftTipX = {centerX - 38, centerX - 42, centerX - 40};
        int[] leftTipY = {78, 70, 80};
        g.fillPolygon(leftTipX, leftTipY, 3);
        int[] rightTipX = {centerX + 38, centerX + 42, centerX + 40};
        int[] rightTipY = {78, 70, 80};
        g.fillPolygon(rightTipX, rightTipY, 3);
        
        // Tail stabilizers
        g.setColor(accent);
        int[] leftTailX = {centerX - 8, centerX - 18, centerX - 16, centerX - 8};
        int[] leftTailY = {92, 96, 102, 98};
        g.fillPolygon(leftTailX, leftTailY, 4);
        int[] rightTailX = {centerX + 8, centerX + 18, centerX + 16, centerX + 8};
        int[] rightTailY = {92, 96, 102, 98};
        g.fillPolygon(rightTailX, rightTailY, 4);
        
        // Engine exhausts
        g.setColor(darkAccent);
        g.fillOval(centerX - 8, 100, 6, 8);
        g.fillOval(centerX + 2, 100, 6, 8);
        g.setColor(new Color(191, 97, 106, 200));
        g.fillOval(centerX - 7, 102, 4, 5);
        g.fillOval(centerX + 3, 102, 4, 5);
        
        // Air intakes
        g.setColor(darkAccent);
        g.fillOval(centerX - 10, 55, 5, 8);
        g.fillOval(centerX + 5, 55, 5, 8);
        
        // Panel lines
        g.setColor(darkBody);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(centerX - 10, 35, centerX - 10, 85);
        g.drawLine(centerX + 10, 35, centerX + 10, 85);
        g.drawLine(centerX, 65, centerX, 90);
        
        // Wing markings
        g.setColor(new Color(236, 239, 244));
        g.fillRect(centerX - 25, 68, 8, 2);
        g.fillRect(centerX + 17, 68, 8, 2);
        
        g.dispose();
        return img;
    }
    
    private static BufferedImage createHelicopterSprite(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Colors from reference images
        Color darkBody = new Color(78, 74, 107);
        Color mediumBody = new Color(98, 94, 134);
        Color lightBody = new Color(118, 114, 154);
        Color rotorColor = new Color(150, 146, 186, 180);
        Color cockpitGlass = new Color(180, 142, 173, 200);
        Color darkAccent = new Color(58, 54, 87);
        
        // Main rotor disc (large, spinning blades)
        g.setColor(new Color(200, 196, 236, 100));
        g.fillOval(centerX - 48, centerY - 48, 96, 96);
        
        // Main rotor blades (cross pattern)
        g.setColor(rotorColor);
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(centerX - 45, centerY, centerX + 45, centerY);
        g.drawLine(centerX, centerY - 45, centerX, centerY + 45);
        
        // Rotor blade tips
        g.setColor(darkBody);
        g.fillOval(centerX - 48, centerY - 3, 8, 6);
        g.fillOval(centerX + 40, centerY - 3, 8, 6);
        g.fillOval(centerX - 3, centerY - 48, 6, 8);
        g.fillOval(centerX - 3, centerY + 40, 6, 8);
        
        // Tail boom
        g.setColor(mediumBody);
        g.fillRoundRect(15, centerY - 5, 35, 10, 5, 5);
        g.setColor(lightBody);
        g.fillRect(20, centerY - 4, 3, 8);
        g.fillRect(28, centerY - 4, 3, 8);
        g.fillRect(36, centerY - 4, 3, 8);
        
        // Tail rotor mount
        g.setColor(darkBody);
        g.fillRect(10, centerY - 8, 8, 16);
        
        // Tail rotor
        g.setColor(new Color(180, 176, 216, 180));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(14, centerY - 12, 14, centerY + 12);
        g.drawLine(9, centerY, 19, centerY);
        g.setColor(darkAccent);
        g.fillOval(12, centerY - 3, 4, 6);
        
        // Main body/fuselage
        g.setColor(mediumBody);
        int[] bodyX = {48, 50, 70, 85, 90, 85, 70, 50};
        int[] bodyY = {centerY - 16, centerY - 18, centerY - 18, centerY - 12, centerY, centerY + 12, centerY + 18, centerY + 18};
        g.fillPolygon(bodyX, bodyY, 8);
        
        // Body lower section
        g.setColor(darkBody);
        int[] lowerBodyX = {50, 68, 82, 87, 82, 68, 50};
        int[] lowerBodyY = {centerY + 4, centerY + 4, centerY + 8, centerY + 10, centerY + 14, centerY + 16, centerY + 16};
        g.fillPolygon(lowerBodyX, lowerBodyY, 7);
        
        // Cockpit bubble
        g.setColor(cockpitGlass);
        int[] cockpitX = {68, 72, 88, 95, 88, 72};
        int[] cockpitY = {centerY - 16, centerY - 18, centerY - 16, centerY, centerY + 16, centerY + 18};
        g.fillPolygon(cockpitX, cockpitY, 6);
        
        // Cockpit frame details
        g.setColor(darkBody);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(80, centerY - 15, 80, centerY + 15);
        g.drawLine(87, centerY - 12, 87, centerY + 12);
        g.drawLine(72, centerY - 10, 90, centerY - 10);
        g.drawLine(72, centerY, 92, centerY);
        g.drawLine(72, centerY + 10, 90, centerY + 10);
        
        // Cockpit highlight
        g.setColor(new Color(220, 200, 230, 120));
        g.fillOval(75, centerY - 8, 10, 16);
        
        // Nose sensor
        g.setColor(darkAccent);
        g.fillOval(93, centerY - 4, 8, 8);
        
        // Engine housing
        g.setColor(lightBody);
        int[] engineX = {50, 58, 75, 78, 75, 58};
        int[] engineY = {centerY - 18, centerY - 24, centerY - 24, centerY - 20, centerY - 18, centerY - 18};
        g.fillPolygon(engineX, engineY, 6);
        
        // Engine vents
        g.setColor(darkAccent);
        g.fillRect(55, centerY - 23, 2, 5);
        g.fillRect(60, centerY - 23, 2, 5);
        g.fillRect(65, centerY - 23, 2, 5);
        g.fillRect(70, centerY - 23, 2, 5);
        
        // Exhaust
        g.setColor(new Color(191, 97, 106));
        g.fillOval(48, centerY - 22, 8, 6);
        g.setColor(new Color(235, 203, 139, 180));
        g.fillOval(50, centerY - 21, 5, 4);
        
        // Landing skids
        g.setColor(darkBody);
        g.setStroke(new BasicStroke(4f));
        g.drawLine(52, centerY + 20, 85, centerY + 20);
        g.drawLine(52, centerY + 26, 85, centerY + 26);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(58, centerY + 16, 58, centerY + 20);
        g.drawLine(70, centerY + 16, 70, centerY + 20);
        g.drawLine(82, centerY + 14, 82, centerY + 20);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(52, centerY + 23, 58, centerY + 20);
        g.drawLine(85, centerY + 23, 82, centerY + 20);
        
        // Rotor hub (on top)
        g.setColor(darkAccent);
        g.fillOval(centerX - 5, centerY - 5, 10, 10);
        g.setColor(new Color(100, 96, 136));
        g.fillOval(centerX - 3, centerY - 3, 6, 6);
        
        g.dispose();
        return img;
    }
    
    private static BufferedImage createMissileShadow(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int centerX = width / 2;
        g.setColor(new Color(0, 0, 0, 80));
        int[] bodyX = {centerX, centerX - 5, centerX - 5, centerX + 5, centerX + 5};
        int[] bodyY = {10, 18, height - 14, height - 14, 18};
        g.fillPolygon(bodyX, bodyY, 5);
        
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
        
        int centerX = width / 2;
        int centerY = height / 2;
        g.setColor(new Color(0, 0, 0, 80));
        
        int[] bodyX = {centerX, centerX - 9, centerX - 11, centerX - 11, centerX - 9, centerX - 5, centerX + 5, centerX + 9, centerX + 11, centerX + 11, centerX + 9};
        int[] bodyY = {22, 32, 47, 77, 92, 107, 107, 92, 77, 47, 32};
        g.fillPolygon(bodyX, bodyY, 11);
        
        int[] leftWingX = {centerX - 11, centerX - 40, centerX - 36, centerX - 11};
        int[] leftWingY = {52, 72, 80, 72};
        g.fillPolygon(leftWingX, leftWingY, 4);
        int[] rightWingX = {centerX + 11, centerX + 40, centerX + 36, centerX + 11};
        int[] rightWingY = {52, 72, 80, 72};
        g.fillPolygon(rightWingX, rightWingY, 4);
        
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
        g.fillRoundRect(17, centerY - 4, 33, 8, 4, 4);
        
        int[] bodyX = {50, 52, 72, 87, 92, 87, 72, 52};
        int[] bodyY = {centerY - 14, centerY - 16, centerY - 16, centerY - 10, centerY + 2, centerY + 14, centerY + 20, centerY + 20};
        g.fillPolygon(bodyX, bodyY, 8);
        
        int[] cockpitX = {70, 74, 90, 97, 90, 74};
        int[] cockpitY = {centerY - 14, centerY - 16, centerY - 14, centerY + 2, centerY + 18, centerY + 20};
        g.fillPolygon(cockpitX, cockpitY, 6);
        
        g.setColor(new Color(0, 0, 0, 40));
        g.fillOval(centerX - 50, centerY - 50, 100, 100);
        
        g.dispose();
        return img;
    }
}
