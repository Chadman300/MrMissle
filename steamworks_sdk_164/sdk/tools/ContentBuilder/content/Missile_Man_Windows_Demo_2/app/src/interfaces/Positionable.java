package interfaces;

/**
 * Interface for entities that have a 2D position in the game world.
 * 
 * AI CONTEXT:
 * - Base interface for spatial positioning
 * - Extended by Collidable for collision detection
 * - All game entities that exist in world space implement this
 * 
 * COORDINATE SYSTEM:
 * - Origin (0, 0) is top-left corner of screen
 * - X increases rightward
 * - Y increases downward
 * - Screen dimensions available in Game.WIDTH and Game.HEIGHT
 */
public interface Positionable {
    /**
     * Gets the X coordinate of the entity's center.
     * 
     * @return X position in pixels
     */
    double getX();
    
    /**
     * Gets the Y coordinate of the entity's center.
     * 
     * @return Y position in pixels
     */
    double getY();
    
    /**
     * Sets the entity's position.
     * 
     * @param x New X coordinate in pixels
     * @param y New Y coordinate in pixels
     */
    void setPosition(double x, double y);
}
