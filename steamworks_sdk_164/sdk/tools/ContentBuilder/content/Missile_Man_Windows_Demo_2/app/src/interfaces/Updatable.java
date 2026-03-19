package interfaces;

/**
 * Interface for game entities that can be updated each frame.
 * 
 * AI CONTEXT:
 * - Implement this interface for any object that changes over time
 * - The update() method is called once per frame (60 FPS target)
 * - deltaTime allows frame-rate independent animation
 * 
 * IMPLEMENTERS:
 * - Player (movement, animation)
 * - Boss (AI, attack patterns)
 * - Bullet (movement, lifetime)
 * - Particle (visual effects, fade out)
 * - DamageNumber (floating text animation)
 * 
 * USAGE PATTERN:
 * In game loop:
 * for (Updatable entity : entities) {
 *     entity.update(deltaTime);
 * }
 */
public interface Updatable {
    /**
     * Updates the entity's state for this frame.
     * 
     * @param deltaTime Time elapsed since last frame in seconds
     *                  Typically 1/60 (0.0166...) for 60 FPS
     *                  Use this to scale movement: position += velocity * deltaTime
     */
    void update(double deltaTime);
}
