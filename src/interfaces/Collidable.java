package interfaces;

/**
 * Interface for entities that have a 2D position and bounding box.
 * Used for collision detection and spatial queries.
 * 
 * AI CONTEXT:
 * - Implement this for any object that occupies space in the game world
 * - Used by collision detection systems
 * - Enables spatial partitioning (grid-based optimization)
 * 
 * IMPLEMENTERS:
 * - Player (20x20 hitbox)
 * - Boss (size varies by level)
 * - Bullet (8-12 pixel radius)
 * - BeamAttack (rectangular area)
 * 
 * COLLISION DETECTION TYPES:
 * 1. Circle-Circle: sqrt((x1-x2)^2 + (y1-y2)^2) < r1+r2
 * 2. Circle-Rectangle: Point-in-rectangle check
 * 3. Rectangle-Rectangle: AABB (Axis-Aligned Bounding Box)
 * 
 * OPTIMIZATION:
 * - Spatial grid partitions world into cells (50x50 pixels)
 * - Only check collisions within same/adjacent cells
 * - See Game.java → bulletGrid for implementation
 */
public interface Collidable extends Positionable {
    /**
     * Gets the collision radius for circle-based collision detection.
     * 
     * @return Radius in pixels from center point
     */
    double getRadius();
    
    /**
     * Checks if this entity is currently collidable.
     * Used to disable collision during invincibility, death, etc.
     * 
     * @return true if entity should participate in collision detection
     */
    boolean isActive();
}
