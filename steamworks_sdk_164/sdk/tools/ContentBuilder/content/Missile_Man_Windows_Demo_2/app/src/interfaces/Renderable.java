package interfaces;

import java.awt.Graphics2D;

/**
 * Interface for game entities that can be rendered to the screen.
 * 
 * AI CONTEXT:
 * - Implement this interface for any visible game object
 * - The render() method is called once per frame after update()
 * - Graphics2D is the Java2D rendering context
 * 
 * IMPLEMENTERS:
 * - Player (sprite + shadow)
 * - Boss (sprite + effects)
 * - Bullet (circle or sprite)
 * - Particle (shape with transparency)
 * - DamageNumber (floating text)
 * - UIButton (interactive UI elements)
 * 
 * RENDERING ORDER (painter's algorithm):
 * 1. Background layers (parallax)
 * 2. Particles (background layer)
 * 3. Shadows (player, boss)
 * 4. Boss
 * 5. Player
 * 6. Bullets
 * 7. Particles (foreground layer)
 * 8. UI elements (damage numbers, HUD)
 * 9. Screen effects (vignette, flash)
 * 
 * USAGE PATTERN:
 * In render loop:
 * for (Renderable entity : entities) {
 *     entity.render(g);
 * }
 */
public interface Renderable {
    /**
     * Renders the entity to the screen.
     * 
     * @param g Graphics2D context for drawing
     *          Use g.drawImage(), g.fillOval(), g.drawString(), etc.
     *          Coordinate system: (0,0) is top-left, Y increases downward
     */
    void render(Graphics2D g);
}
