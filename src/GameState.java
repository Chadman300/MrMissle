/**
 * Represents the current state of the game.
 * 
 * AI CONTEXT:
 * - The game is organized as a state machine
 * - Each state has different update() and render() logic in Game.java
 * - Transitions between states are handled by user input or game events
 * 
 * STATE TRANSITIONS:
 * MENU → LEVEL_SELECT (user clicks play)
 * LEVEL_SELECT → LEVEL_CONFIRM (user selects level)
 * LEVEL_CONFIRM → RISK_CONTRACT (user confirms level)
 * RISK_CONTRACT → PLAYING (user selects risk contract)
 * PLAYING → GAME_OVER (player dies with no extra lives)
 * PLAYING → WIN (boss defeated)
 * GAME_OVER/WIN → MENU (automatic after animation)
 * Any state → SHOP (user navigates to shop)
 * SHOP → Previous state (user exits shop)
 * 
 * To add a new state:
 * 1. Add enum value here
 * 2. Add case in Game.update()
 * 3. Add case in Renderer.render()
 * 4. Add transition logic from existing states
 */
public enum GameState {
    /** Loading screen - shown during asset initialization */
    LOADING,
    
    /** Main menu - first screen shown on launch */
    MENU,
    
    /** Info/help screen - tutorial information */
    INFO,
    
    /** Statistics screen - achievements, high scores, total stats */
    STATS,
    
    /** Level selection screen - scrollable list of unlocked levels */
    LEVEL_SELECT,
    
    /** Level confirmation dialog - "Start Level X?" with Yes/No */
    LEVEL_CONFIRM,
    
    /** Risk contract selection - choose difficulty modifiers for bonus rewards */
    RISK_CONTRACT,
    
    /** Active gameplay state - player dodges bullets and fights boss */
    PLAYING,
    
    /** Game over screen - player died with no extra lives, shows run stats */
    GAME_OVER,
    
    /** Victory screen - boss defeated, shows rewards */
    WIN,
    
    /** Shop interface - purchase upgrades and items with money */
    SHOP,
    
    /** Settings menu - audio volume, controls, etc. */
    SETTINGS,
    
    /** Debug mode - development tools and testing features */
    DEBUG,
    
    /** Achievements screen - detailed achievement progress */
    ACHIEVEMENTS
}
