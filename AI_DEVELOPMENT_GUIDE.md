# AI Development Guide - Missile Man Game

## 🎯 Purpose
This guide helps AI assistants understand the game architecture and make effective code changes.

---

## 📁 Project Structure

```
src/
├── config/              # Configuration and constants
│   ├── GameConfig.java       # Game balance, timing, physics constants
│   └── ColorPalette.java     # Visual constants (colors, composites)
│
├── entities/            # Game objects (FUTURE - to be created)
│   ├── Player.java           # Player character
│   ├── Boss.java             # Boss enemies
│   ├── Bullet.java           # Projectiles
│   └── Particle.java         # Visual effects
│
├── systems/             # Game logic systems (FUTURE - to be created)
│   ├── ComboSystem.java      # Combo mechanics
│   └── PhysicsSystem.java    # Collision detection
│
├── managers/            # State and resource managers
│   ├── GameData.java         # Persistent data & save system
│   ├── ShopManager.java      # Shop and economy
│   ├── SoundManager.java     # Audio playback
│   ├── AchievementManager.java  # Achievement tracking
│   └── PassiveUpgradeManager.java  # Upgrade system
│
├── ui/                  # User interface (FUTURE - to be created)
│   ├── Renderer.java         # Rendering engine
│   └── UIButton.java         # Button component
│
├── utils/               # Utilities (FUTURE - to be created)
│   └── AssetLoader.java      # Resource loading
│
├── Game.java           # Main game loop and orchestration
├── App.java            # Application entry point
└── GameState.java      # Enum for game states
```

---

## 🏗️ Architecture Overview

### Core Game Loop (Game.java)
**Lines: ~4800+ (VERY LARGE - needs refactoring)**

**Responsibilities:**
- Game loop (60 FPS target)
- Input handling
- State management (menu, playing, pause, etc.)
- Orchestrates all systems

**Key Methods:**
- `run()` - Main game loop
- `update()` - Updates all game entities (varies by state)
- `render()` - Delegates to Renderer
- `handleInput()` - Process keyboard/mouse input

**AI Guidance:**
- This file is TOO LARGE and should be refactored
- Look for state-specific logic (search for `switch(gameState)`)
- Many responsibilities should be moved to dedicated classes

---

### Rendering System (Renderer.java)
**Lines: ~4000+ (VERY LARGE - needs refactoring)**

**Responsibilities:**
- All drawing operations
- Parallax backgrounds (14 sets x 6 layers)
- UI rendering for all game states
- Visual effects (vignette, screen shake, etc.)

**Key Methods:**
- `render()` - Main render dispatcher
- `renderGame()` - Draw gameplay elements
- `renderMenu()` - Draw main menu
- `renderShop()` - Draw shop interface
- Various `render*()` methods for each UI state

**AI Guidance:**
- Renderer is PRESENTATION ONLY - no game logic here
- Organized by game state
- Uses ColorPalette and pre-cached fonts for performance

---

### Player (Player.java)
**Lines: ~390**

**Responsibilities:**
- Player movement and physics
- Input processing (WASD controls)
- Visual effects (squash/stretch, dash)
- Shadow rendering

**Key Properties:**
- `x, y` - Position
- `vx, vy` - Velocity
- `speedMultiplier` - From upgrades
- `isDashing` - Dash ability state

**AI Guidance:**
- Physics uses acceleration + friction model
- Diagonal movement is normalized (INV_SQRT_2)
- Sprite system with shadow offset
- Look at GameConfig for movement constants

---

### Boss (Boss.java)
**Responsibilities:**
- Boss AI and attack patterns
- Bullet spawning logic
- Phase transitions
- Beam attacks

**AI Guidance:**
- Each level has unique attack patterns
- Bullet patterns vary by level complexity
- Uses spatial variation to avoid predictability

---

### Game Data (GameData.java)
**Lines: ~430**

**Responsibilities:**
- Persistent player data (money, upgrades, unlocks)
- Save/load system (JSON serialization)
- Level progression tracking
- Achievement state

**Key Properties:**
- `totalMoney` - Persistent currency
- `runMoney` - Currency for current run
- `currentLevel` - Active level
- `maxUnlockedLevel` - Progression cap
- Upgrade levels (speed, bulletSlow, luckyDodge, attackWindow)

**AI Guidance:**
- This is the SINGLE SOURCE OF TRUTH for player progression
- All upgrades and unlocks are tracked here
- Save system uses Gson library
- Save file: `game_save.json` in user directory

---

## 🎮 Game Systems

### Combat Flow
1. **Boss Spawns** → Invulnerable for 3 seconds (BOSS_INVULNERABILITY_DURATION)
2. **Boss Attacks** → Spawns bullets based on level patterns
3. **Player Dodges** → Graze detection, combo system
4. **Vulnerability Window** → 20 second window to damage boss (BOSS_VULNERABILITY_DURATION)
5. **Boss Hit** → Takes 1 of 3 hits, player respawns
6. **Boss Defeated** → Progress to next level or return to menu

### Graze System
- **Graze** (25px): Close bullet pass, builds combo
- **Close Call** (15px): Very close dodge
- **Perfect Dodge** (8px): Frame-perfect dodge, grants invincibility frames

### Combo System (ComboSystem.java)
- Tracks consecutive dodges
- Expires after 3 seconds (COMBO_TIMEOUT)
- Displayed in UI during gameplay

### Risk Contracts
Player can select difficulty modifiers for increased rewards:
1. **Bullet Storm** - 2x bullets (2x money)
2. **Speed Demon** - 50% faster bullets (1.75x money)
3. **Shieldless** - No shield item (1.5x money)
4. **Can't Stop** - Must keep moving (2.5x money)

---

## 🎨 Visual Systems

### Parallax Backgrounds
- 14 background sets (selectable)
- 6 layers per set (different scroll speeds)
- Stored in: `sprites/Backgrounds/background (X)/`
- Loaded via AssetLoader

### Particle System
- Max 300 particles (MAX_PARTICLES)
- Object pooling for performance
- Types: impact, fire, smoke, explosion

### Screen Effects
- Screen shake on impacts
- Flash effects on achievements, hits
- Vignette overlay
- State transitions with easing

---

## 💾 Data Persistence

### Save File Structure
```json
{
  "totalMoney": 1000,
  "maxUnlockedLevel": 5,
  "speedUpgradeLevel": 3,
  "bulletSlowUpgradeLevel": 10,
  "unlockedItems": ["SHIELD", "DASH"],
  "achievements": [...],
  ...
}
```

**Location:** User home directory / `game_save.json`

---

## 🔧 Common AI Tasks

### Adding a New Upgrade
1. Add max level constant to `GameConfig.java`
2. Add level property to `GameData.java` (persistent + active)
3. Add shop button in `ShopManager.java`
4. Add upgrade logic where needed (e.g., Player.java)
5. Add visual indicator in `Renderer.java`

### Adding a New Boss Pattern
1. Go to `Boss.java` → `spawnBullets()` method
2. Add case for new level number
3. Define bullet spawn pattern
4. Test with risk contracts enabled

### Balancing Game Difficulty
1. Check `GameConfig.java` for timing constants
2. Check `Boss.java` for bullet spawn rates
3. Check `GameData.java` for upgrade effectiveness
4. Modify and test incrementally

### Adding a New Achievement
1. Add to `Achievement.AchievementType` enum
2. Add unlock logic in `AchievementManager.java`
3. Add check condition in `Game.java` where appropriate
4. Achievement will auto-save via GameData

### Changing Visual Style
1. Modify colors in `ColorPalette.java`
2. Adjust fonts in `Renderer.java` (cached fonts section)
3. Update background assets in `sprites/Backgrounds/`

---

## ⚡ Performance Optimizations

### Current Optimizations
1. **Object Pooling** - Bullets and particles are recycled
2. **Spatial Grid** - Bullets partitioned for fast collision checks
3. **Cached Colors/Fonts** - Pre-created to avoid GC pressure
4. **Render Culling** - Off-screen objects not drawn
5. **Cached Math** - Pre-computed constants (TWO_PI, INV_SQRT_2)

### Performance Hotspots
- `Game.update()` - Updates all entities, bullet collisions
- `Renderer.renderGame()` - Draws all visible elements
- Particle system - Limited to 300 particles

### If Game is Slow
1. Check MAX_PARTICLES setting
2. Reduce background layer count
3. Optimize bullet grid size (GRID_CELL_SIZE)
4. Profile with JProfiler or VisualVM

---

## 🐛 Debugging Tips

### Game Won't Start
- Check `AssetLoader.java` - asset loading failures
- Verify `game_save.json` is valid JSON
- Check console for exceptions

### Player Movement Issues
- Check `Player.java` → `move()` method
- Verify GameConfig.PLAYER_* constants
- Check input handling in `Game.java`

### Boss Not Spawning Bullets
- Check `Boss.java` → level-specific spawn logic
- Verify boss is in vulnerable state
- Check bullet pool isn't exhausted

### UI Not Responding
- Check `Game.java` → input handlers for current state
- Verify UIButton hitboxes
- Check mouse/keyboard mode conflicts

---

## 📊 Code Statistics

| File | Lines | Status | Priority to Refactor |
|------|-------|--------|----------------------|
| Game.java | ~4800 | ⚠️ TOO LARGE | 🔴 HIGH |
| Renderer.java | ~4000 | ⚠️ TOO LARGE | 🔴 HIGH |
| GameData.java | ~430 | ✅ OK | 🟡 MEDIUM |
| Player.java | ~390 | ✅ OK | 🟢 LOW |
| Boss.java | ? | ❓ Unknown | 🟡 MEDIUM |

---

## 🎯 Refactoring Roadmap

### Phase 1: Configuration (✅ DONE)
- [x] Extract constants to GameConfig
- [x] Extract colors to ColorPalette
- [x] Create this documentation

### Phase 2: Package Structure (🔄 IN PROGRESS)
- [ ] Create package directories
- [ ] Move entities to entities/
- [ ] Move managers to managers/
- [ ] Move UI to ui/
- [ ] Move utils to utils/

### Phase 3: Game.java Decomposition (📋 PLANNED)
- [ ] Extract input handling to InputHandler
- [ ] Extract state machine to GameStateManager
- [ ] Extract update logic per state to StateHandlers
- [ ] Reduce Game.java to orchestration only

### Phase 4: Renderer.java Decomposition (📋 PLANNED)
- [ ] Extract UI rendering to dedicated UI classes
- [ ] Extract background system to BackgroundRenderer
- [ ] Extract particle rendering to ParticleRenderer
- [ ] Create RenderContext for shared state

---

## 💡 AI Assistant Quick Reference

### When User Says... → Look Here
- "change player speed" → `GameConfig.PLAYER_MAX_SPEED`
- "add new boss attack" → `Boss.java` → `spawnBullets()`
- "modify shop prices" → `ShopManager.java`
- "change colors" → `ColorPalette.java`
- "adjust game timing" → `GameConfig.java` (DURATION constants)
- "add achievement" → `Achievement.java` + `AchievementManager.java`
- "fix save system" → `GameData.java` → `save()` / `load()`
- "background not loading" → `AssetLoader.java` + `Renderer.java`

### State Flow
```
MAIN_MENU → LEVEL_SELECT → LEVEL_CONFIRM → PLAYING
                ↓                              ↓
              SHOP                          PAUSED
                ↓                              ↓
            MAIN_MENU                   PLAYING/MAIN_MENU
```

### File Dependencies
```
App.java
  └─ Game.java (orchestrator)
      ├─ Player.java
      ├─ Boss.java
      ├─ Bullet.java
      ├─ GameData.java (persistence)
      ├─ Renderer.java (all drawing)
      │   └─ UIButton.java
      ├─ ShopManager.java
      ├─ SoundManager.java
      ├─ AchievementManager.java
      ├─ PassiveUpgradeManager.java
      └─ ComboSystem.java
```

---

## 🚀 Getting Started (for AI)

1. **Read this guide first**
2. **Check GameConfig.java** - understand constants
3. **Scan Game.java** - understand flow (warning: large file)
4. **Identify target area** based on user request
5. **Make focused changes** - avoid touching unrelated code
6. **Test incrementally** - suggest user test after each change

---

## 📝 Code Style Guidelines

### Naming Conventions
- **Classes**: PascalCase (e.g., `GameData`, `BulletType`)
- **Methods**: camelCase (e.g., `updatePlayer`, `spawnBullets`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_SPEED`, `BOSS_INTRO_DURATION`)
- **Variables**: camelCase (e.g., `playerX`, `currentLevel`)

### Documentation
- **All public methods** should have JavaDoc
- **Complex algorithms** should have inline comments
- **Magic numbers** should be constants with descriptive names
- **TODO comments** should reference GitHub issues

### Performance
- Prefer primitive types over objects where possible
- Cache frequently used calculations
- Use object pools for frequently created/destroyed objects
- Profile before optimizing

---

**Last Updated:** January 2026  
**Maintained by:** AI-assisted development  
**Questions?** Check Game.java comments or ask for clarification
