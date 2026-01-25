# Class Reference Guide

Quick lookup for all classes, their responsibilities, and relationships.

---

## 🎮 Core Game Classes

### Game (src/Game.java)
**Extends:** JPanel  
**Implements:** Runnable

**Responsibilities:**
- Main game loop (60 FPS)
- State machine orchestration
- Input handling (keyboard, mouse)
- Entity management (Player, Boss, Bullets, Particles)
- Collision detection (spatial grid)
- Save game coordination
- Sound coordination

**Key Fields:**
```java
WIDTH, HEIGHT              // Screen dimensions
gameState                  // Current state (enum)
player                     // Player instance
currentBoss                // Active boss
bullets, particles         // Entity lists
gameData                   // Persistence
renderer                   // Rendering system
```

**Key Methods:**
```java
run()                      // Game loop
update(deltaTime)          // Update all entities
render()                   // Delegate to renderer
```

**Dependencies:** Everything (⚠️ God object - needs refactoring)

---

## 🎨 Rendering

### Renderer (src/Renderer.java)
**Responsibilities:**
- All drawing operations
- Parallax backgrounds (14 sets x 6 layers)
- UI rendering for all states
- Visual effects (vignette, screen shake)
- Font management (cached fonts)

**Key Fields:**
```java
backgroundLayers           // [14][6] images
menuButtons                // UI button arrays
cachedVignette             // Performance cache
```

**Key Methods:**
```java
render(GameState, ...)     // Main dispatcher
renderGame()               // Gameplay rendering
renderMenu()               // Menu rendering
renderShop()               // Shop rendering
// ... one render method per state
```

**Dependencies:** Game, GameData, ShopManager, PassiveUpgradeManager, UIButton

---

### UIButton (src/UIButton.java)
**Responsibilities:**
- Interactive button component
- Hover detection
- Click detection
- Visual states (normal, hover, pressed)

**Key Fields:**
```java
x, y, width, height        // Bounds
text                       // Button label
hovered, pressed           // State flags
```

**Key Methods:**
```java
update(mouseX, mouseY)     // Update hover state
isClicked(mouseX, mouseY)  // Detect clicks
render(g)                  // Draw button
```

**Dependencies:** None (standalone component)

---

## 🎯 Entities

### Player (src/Player.java)
**Responsibilities:**
- Player movement (WASD)
- Physics (acceleration, friction)
- Dash ability
- Sprite rendering with shadow
- Squash/stretch animation

**Key Fields:**
```java
x, y                       // Position
vx, vy                     // Velocity
speedMultiplier            // From upgrades
isDashing                  // Dash state
```

**Key Methods:**
```java
move(keys, deltaTime)      // Process input and move
update(deltaTime)          // Update animation
render(g)                  // Draw sprite + shadow
```

**Dependencies:** GameConfig (constants), AssetLoader (sprite)

---

### Boss (src/Boss.java)
**Responsibilities:**
- Boss AI and positioning
- Attack pattern generation
- Bullet spawning (level-specific)
- Health management (3 hits)
- Beam attack coordination

**Key Fields:**
```java
x, y                       // Position
currentLevel               // Which level boss this is
health                     // Hits remaining (max 3)
```

**Key Methods:**
```java
update(deltaTime)          // Update AI and position
spawnBullets()             // Generate attack pattern
takeDamage()               // Handle hit
render(g)                  // Draw boss sprite
```

**Dependencies:** Bullet (spawning), BeamAttack (special attacks), GameConfig

---

### Bullet (src/Bullet.java)
**Responsibilities:**
- Projectile movement
- Lifetime tracking
- Type-specific behavior (normal, tracking, fast)
- Collision bounds

**Enum:** BulletType (NORMAL, TRACKING, FAST, SLOW, etc.)

**Key Fields:**
```java
x, y                       // Position
vx, vy                     // Velocity
type                       // BulletType enum
lifetime                   // Age in frames
```

**Key Methods:**
```java
update(deltaTime)          // Move bullet
isOffscreen()              // Check bounds
render(g)                  // Draw bullet
```

**Dependencies:** GameConfig (bullet speed modifiers), Player (for tracking bullets)

---

### Particle (src/Particle.java)
**Responsibilities:**
- Visual effect animation
- Fade out over time
- Physics (velocity, gravity)
- Type-specific rendering

**Key Fields:**
```java
x, y                       // Position
vx, vy                     // Velocity
lifetime, maxLifetime      // Age tracking
color                      // Particle color
type                       // Particle type string
```

**Key Methods:**
```java
update(deltaTime)          // Move and age
isDone()                   // Check if expired
render(g)                  // Draw particle
```

**Dependencies:** ColorPalette (colors)

---

### BeamAttack (src/BeamAttack.java)
**Responsibilities:**
- Linear beam weapon
- Warning indicator
- Activation delay
- Hit detection

**Enum:** BeamType (HORIZONTAL, VERTICAL, DIAGONAL)

**Key Fields:**
```java
x, y                       // Origin
type                       // BeamType enum
warningTimer               // Pre-fire warning
activeTimer                // Active duration
```

**Key Methods:**
```java
update(deltaTime)          // Progress through states
isActive()                 // Check if can damage
render(g)                  // Draw beam + warning
```

**Dependencies:** ColorPalette (beam colors)

---

### DamageNumber (src/DamageNumber.java)
**Responsibilities:**
- Floating text animation
- Fade out and rise
- Critical hit styling

**Key Fields:**
```java
text                       // Display string
x, y                       // Position
lifetime                   // Age in frames
isCritical                 // Styling flag
```

**Key Methods:**
```java
update(deltaTime)          // Animate upward + fade
isDone()                   // Check if expired
render(g)                  // Draw text
```

**Dependencies:** None

---

## 💾 Data & Persistence

### GameData (src/GameData.java)
**Responsibilities:**
- Player progression (levels, unlocks)
- Currency (total money, run money)
- Upgrades (purchased levels)
- Active items (unlocked, equipped)
- Achievements
- Save/load to JSON

**Key Fields:**
```java
totalMoney                 // Persistent currency
currentLevel               // Active level
maxUnlockedLevel           // Progression cap
speedUpgradeLevel          // Upgrade progress
// ... many more upgrade/unlock fields
```

**Key Methods:**
```java
save()                     // Write to ~/game_save.json
load()                     // Read from file
// ... getters and setters
```

**Dependencies:** Achievement, ActiveItem, LevelStats (data structures), Gson (JSON)

---

### LevelStats (src/LevelStats.java)
**Responsibilities:**
- Per-level statistics tracking
- Best time, best combo
- Perfect clears

**Key Fields:**
```java
levelNumber                // Which level
bestTime                   // Fastest completion
bestCombo                  // Highest combo
perfectClear               // No damage taken
```

**Dependencies:** None (simple data class)

---

## 🛒 Management Systems

### ShopManager (src/ShopManager.java)
**Responsibilities:**
- Shop UI state
- Upgrade prices
- Purchase validation
- Economy balance

**Key Fields:**
```java
gameData                   // Reference to player data
// Upgrade prices defined in constructor
```

**Key Methods:**
```java
canAfford(item, level)     // Check if player has money
purchase(item)             // Deduct money, apply upgrade
```

**Dependencies:** GameData (reads/writes money and upgrades)

---

### SoundManager (src/SoundManager.java)
**Responsibilities:**
- Audio playback (music, SFX)
- Volume control
- Sound effect catalog

**Enum:** Sound (all sound effect types)

**Key Methods:**
```java
playSound(Sound)           // Play SFX
playMusic(filename)        // Play background music
setVolume(volume)          // Master volume
```

**Dependencies:** AssetLoader (load audio files)

---

### AchievementManager (src/AchievementManager.java)
**Responsibilities:**
- Achievement definitions
- Progress tracking
- Unlock detection
- Notification queueing

**Key Fields:**
```java
achievements               // List<Achievement>
```

**Key Methods:**
```java
initializeAchievements()   // Define all achievements
checkAchievement(type, value)  // Update progress
unlockAchievement(id)      // Unlock specific achievement
```

**Dependencies:** Achievement (data structure), GameData (persistence)

---

### PassiveUpgradeManager (src/PassiveUpgradeManager.java)
**Responsibilities:**
- Upgrade effect calculations
- Upgrade descriptions
- Level-based scaling

**Key Fields:**
```java
gameData                   // Reference to player data
```

**Key Methods:**
```java
getSpeedMultiplier()       // Calculate speed boost
getBulletSlowFactor()      // Calculate bullet slow
getLuckyDodgeChance()      // Calculate dodge chance
getAttackWindowBonus()     // Calculate window extension
```

**Dependencies:** GameData (reads upgrade levels), PassiveUpgrade (definitions)

---

### PassiveUpgrade (src/PassiveUpgrade.java)
**Responsibilities:**
- Upgrade type definitions
- Upgrade metadata

**Enum:** UpgradeType (SPEED, BULLET_SLOW, LUCKY_DODGE, ATTACK_WINDOW)

**Key Fields:**
```java
type                       // UpgradeType enum
name                       // Display name
description                // Effect description
currentLevel               // Current level
maxLevel                   // Level cap
```

**Dependencies:** None (data structure)

---

## 🎯 Game Systems

### ComboSystem (src/ComboSystem.java)
**Responsibilities:**
- Combo tracking (consecutive dodges)
- Combo timeout
- Combo multiplier calculation

**Key Fields:**
```java
combo                      // Current combo count
comboTimer                 // Timeout countdown
```

**Key Methods:**
```java
addCombo()                 // Increment combo
update(deltaTime)          // Tick timeout
reset()                    // Clear combo
getMultiplier()            // Calculate score multiplier
```

**Dependencies:** GameConfig (COMBO_TIMEOUT)

---

### ActiveItem (src/ActiveItem.java)
**Responsibilities:**
- Active item definitions
- Cooldown tracking
- Activation state
- Effect duration

**Enum:** ItemType (LUCKY_CHARM, SHIELD, MAGNET, SHOCKWAVE, DASH, BOMB, TIME_SLOW, LASER_BEAM, INVINCIBILITY)

**Key Fields:**
```java
type                       // ItemType enum
currentCooldown            // Cooldown timer
active                     // Effect active flag
activeDuration             // Effect length
```

**Key Methods:**
```java
activate()                 // Use item
update(deltaTime)          // Tick cooldowns
isReady()                  // Check if can use
```

**Dependencies:** GameConfig (would benefit from cooldown constants)

---

### Achievement (src/Achievement.java)
**Responsibilities:**
- Achievement data structure
- Progress tracking
- Unlock status

**Enum:** AchievementType (BOSS_KILLS, REACH_LEVEL, NO_DAMAGE, GRAZE_COUNT, HIGH_COMBO, MONEY_EARNED, PERFECT_BOSS, NO_UPGRADES, SPEED_RUN)

**Key Fields:**
```java
id, name, description      // Identity
progress, target           // Progress tracking
unlocked                   // Unlock status
type                       // AchievementType enum
```

**Dependencies:** None (data structure)

---

## 🔧 Utilities

### AssetLoader (src/AssetLoader.java)
**Responsibilities:**
- Load images (JAR-compatible)
- Load audio (JAR-compatible)
- Resource path resolution

**Key Methods:**
```java
loadImage(path)            // Load BufferedImage
loadAudioClip(path)        // Load Clip
getAudioInputStream(path)  // Load AudioInputStream
```

**Dependencies:** None (uses Java ImageIO, AudioSystem)

---

## 📊 Enums & Constants

### GameState (src/GameState.java)
**Values:**
- LOADING - Asset loading
- MENU - Main menu
- INFO - Help screen
- STATS - Statistics
- LEVEL_SELECT - Level selection
- LEVEL_CONFIRM - Level confirmation
- RISK_CONTRACT - Difficulty modifiers
- PLAYING - Active gameplay
- GAME_OVER - Death screen
- WIN - Victory screen
- SHOP - Shop interface
- SETTINGS - Settings menu
- DEBUG - Debug mode
- ACHIEVEMENTS - Achievement screen

**Dependencies:** None (enum)

---

## 🎨 Configuration (New)

### GameConfig (config/GameConfig.java)
**Responsibilities:**
- All game constants
- Physics parameters
- Timing values
- Balance tuning

**Categories:**
- Display settings (FPS)
- Player settings (speed, dash, etc.)
- Boss settings (health, vulnerability)
- Combat settings (graze distances)
- Upgrade limits
- Performance settings
- UI animation timings
- Risk contracts
- Math constants

**Dependencies:** None (all static final constants)

---

### ColorPalette (config/ColorPalette.java)
**Responsibilities:**
- Color definitions
- Alpha composites
- Visual constants

**Categories:**
- Particle colors
- UI feedback colors
- Shield colors
- Player effects
- Alpha composites

**Dependencies:** None (all static final constants)

---

## 🔗 Dependency Graph

```
App
 └─ Game (god object - orchestrates everything)
     ├─ Player
     ├─ Boss
     │   └─ Bullet
     │   └─ BeamAttack
     ├─ Particle
     ├─ DamageNumber
     ├─ GameData
     │   ├─ Achievement
     │   ├─ ActiveItem
     │   └─ LevelStats
     ├─ Renderer
     │   └─ UIButton
     ├─ ShopManager
     │   └─ GameData
     ├─ SoundManager
     │   └─ AssetLoader
     ├─ AchievementManager
     │   ├─ Achievement
     │   └─ GameData
     ├─ PassiveUpgradeManager
     │   ├─ PassiveUpgrade
     │   └─ GameData
     └─ ComboSystem
```

---

## 🎯 Class Count Summary

| Category | Count | Files |
|----------|-------|-------|
| Core | 2 | App, Game |
| Entities | 6 | Player, Boss, Bullet, Particle, BeamAttack, DamageNumber |
| Managers | 5 | GameData, ShopManager, SoundManager, AchievementManager, PassiveUpgradeManager |
| Systems | 4 | ComboSystem, ActiveItem, PassiveUpgrade, Achievement |
| Rendering | 2 | Renderer, UIButton |
| Utilities | 1 | AssetLoader |
| Enums | 1 | GameState |
| Config | 2 | GameConfig, ColorPalette |
| **Total** | **23** | |

---

## 🎯 Interfaces (New)

| Interface | Implementers | Purpose |
|-----------|--------------|---------|
| Positionable | Player, Boss, Bullet, Particle, BeamAttack, DamageNumber | 2D position |
| Collidable | Player, Boss, Bullet, BeamAttack | Collision detection |
| Updatable | Player, Boss, Bullet, Particle, BeamAttack, DamageNumber, ComboSystem, ActiveItem | Per-frame updates |
| Renderable | Player, Boss, Bullet, Particle, BeamAttack, DamageNumber, UIButton | Screen drawing |

**Note:** These interfaces are DEFINED but not yet IMPLEMENTED on existing classes. This is a future refactoring task.

---

## 📏 Size Reference

| File | Approx Lines | Refactor Priority |
|------|--------------|-------------------|
| Game.java | 4800 | 🔴 URGENT |
| Renderer.java | 4000 | 🔴 URGENT |
| GameData.java | 430 | 🟡 Medium |
| Player.java | 390 | 🟢 Low |
| Boss.java | 600-800 (est) | 🟡 Medium |
| Others | <300 each | 🟢 Low |

---

**Last Updated:** January 2026  
**Use Case:** Quick class lookup, dependency checking, impact analysis
