# Code Architecture Reference

## 🎯 Quick Navigation for AI Assistants

This document provides a structural overview of the codebase for rapid navigation and understanding.

---

## 📦 File Organization

### Configuration (`config/`)
| File | Lines | Purpose | When to Modify |
|------|-------|---------|----------------|
| `GameConfig.java` | ~200 | Game constants, timing, physics | Balancing, tweaking gameplay feel |
| `ColorPalette.java` | ~100 | Visual constants, colors | Changing visual theme |

### Interfaces (`interfaces/`)
| File | Purpose | Implementers |
|------|---------|--------------|
| `Positionable.java` | 2D position | All world entities |
| `Collidable.java` | Collision detection | Player, Boss, Bullet, BeamAttack |
| `Updatable.java` | Per-frame updates | All active entities |
| `Renderable.java` | Drawing to screen | All visible entities |

### Core Game Files (`src/`)
| File | Lines | Responsibility | Complexity |
|------|-------|----------------|------------|
| `Game.java` | ~4800 | Game loop, orchestration | ⚠️ VERY HIGH |
| `Renderer.java` | ~4000 | All rendering logic | ⚠️ VERY HIGH |
| `GameData.java` | ~430 | Save/load, progression | ✅ Moderate |
| `Player.java` | ~390 | Player movement, input | ✅ Low |
| `Boss.java` | ? | Boss AI, attack patterns | ⚠️ High |
| `Bullet.java` | ? | Projectile behavior | ✅ Low |
| `GameState.java` | ~70 | State machine enum | ✅ Very Low |
| `App.java` | <50 | Entry point | ✅ Very Low |

### Managers (`src/`)
| File | Purpose | Dependencies |
|------|---------|--------------|
| `ShopManager.java` | Economy, purchases | GameData |
| `SoundManager.java` | Audio playback | AssetLoader |
| `AchievementManager.java` | Achievement tracking | GameData |
| `PassiveUpgradeManager.java` | Upgrade calculations | GameData |

### Entities (`src/`)
| File | Purpose | Interfaces |
|------|---------|------------|
| `Player.java` | Player character | Positionable, Collidable, Updatable, Renderable |
| `Boss.java` | Boss enemies | Positionable, Collidable, Updatable, Renderable |
| `Bullet.java` | Projectiles | Positionable, Collidable, Updatable, Renderable |
| `Particle.java` | Visual effects | Positionable, Updatable, Renderable |
| `BeamAttack.java` | Beam weapons | Positionable, Collidable, Updatable, Renderable |
| `DamageNumber.java` | Floating text | Positionable, Updatable, Renderable |

### Systems (`src/`)
| File | Purpose | Used By |
|------|---------|---------|
| `ComboSystem.java` | Combo tracking | Game |
| `ActiveItem.java` | Active items | Game, GameData |
| `PassiveUpgrade.java` | Upgrade definitions | PassiveUpgradeManager |
| `Achievement.java` | Achievement data | AchievementManager |
| `LevelStats.java` | Per-level statistics | GameData |

### UI (`src/`)
| File | Purpose |
|------|---------|
| `UIButton.java` | Interactive button component |

### Utilities (`src/`)
| File | Purpose |
|------|---------|
| `AssetLoader.java` | Load images/audio (JAR-compatible) |

---

## 🔄 Data Flow

### Startup Flow
```
App.main()
  └─> new JFrame + Game panel
      └─> Game constructor
          ├─> Load save file (GameData.load())
          ├─> Initialize systems (ShopManager, Renderer, etc.)
          └─> Start game thread (run() loop)
```

### Game Loop Flow (60 FPS)
```
Game.run() [loop]
  ├─> Calculate deltaTime
  ├─> update(deltaTime)
  │    ├─> Update based on gameState
  │    ├─> Update player (if PLAYING)
  │    ├─> Update boss (if PLAYING)
  │    ├─> Update bullets (if PLAYING)
  │    ├─> Check collisions
  │    └─> Update UI state
  └─> render()
       └─> Renderer.render(gameState, ...)
            ├─> Draw backgrounds
            ├─> Draw entities
            ├─> Draw UI
            └─> Draw effects
```

### Save/Load Flow
```
GameData.save()
  └─> Convert to JSON (Gson)
      └─> Write to ~/game_save.json

GameData.load()
  └─> Read ~/game_save.json
      └─> Parse JSON (Gson)
          └─> Restore state
```

---

## 🎮 Gameplay Systems

### Player Movement System
**Location:** `Player.java` → `move()`

**Algorithm:**
1. Read input (WASD keys)
2. Apply acceleration
3. Normalize diagonal movement (× INV_SQRT_2)
4. Apply speed multiplier (from upgrades)
5. Apply friction
6. Clamp to max speed
7. Update position

**Key Variables:**
- `vx, vy` - Velocity
- `speedMultiplier` - From upgrades (1.0 + level * 0.1)
- `ACCELERATION` - 0.5
- `FRICTION` - 0.85
- `MAX_SPEED` - 6.0

### Collision Detection System
**Location:** `Game.java` → `update()` in PLAYING state

**Optimization: Spatial Grid**
- World divided into 50x50 pixel cells
- Bullets placed in grid cells
- Only check collisions in nearby cells
- Hash function: `cellY * GRID_WIDTH_MULTIPLIER + cellX`

**Collision Types:**
1. **Player vs Bullet** - Circle-circle (graze/hit)
2. **Boss vs Bullet** (player bullets) - Circle-circle (damage)
3. **Player vs BeamAttack** - Circle-rectangle

**Graze Detection:**
- Distance < 25px → Graze
- Distance < 15px → Close Call
- Distance < 8px → Perfect Dodge (grants i-frames)

### Boss Attack System
**Location:** `Boss.java` → `spawnBullets()`

**Pattern Selection:**
- Each level has unique attack pattern
- Patterns increase in complexity
- Bullet count, speed, spread varies
- Some patterns use waves, others use spirals

**Vulnerability System:**
- Boss spawns → 3 second invulnerability
- Then vulnerable for 20 seconds
- Player must hit boss 3 times during window
- After window closes → boss respawns bullets

### Upgrade System
**Location:** `PassiveUpgradeManager.java`, `GameData.java`

**Upgrade Types:**
1. **Speed** - Movement speed (max 10)
2. **Bullet Slow** - Slows enemy bullets (max 50)
3. **Lucky Dodge** - Chance to revive (max 12)
4. **Attack Window** - Extends vulnerability (max 10)

**Application:**
- Player purchases upgrades in shop
- Upgrades stored in GameData
- Applied during gameplay:
  - Speed → Player.speedMultiplier
  - Bullet Slow → Bullet speed reduction
  - Lucky Dodge → Death check rolls
  - Attack Window → Vulnerability timer extension

### Active Item System
**Location:** `ActiveItem.java`, `Game.java`

**Flow:**
1. Items unlocked by defeating bosses
2. Player equips one item
3. Player presses E to activate
4. Cooldown starts
5. Effect applies (instant or duration-based)
6. Cooldown ticks down
7. Item ready when cooldown = 0

**Item Types:**
- **Instant:** Shockwave, Bomb
- **Duration:** Shield, Magnet, Dash, Time Slow, Laser, Invincibility
- **Passive:** Pool of Loot (always on)

---

## 🎨 Rendering Pipeline

### Rendering Order (Painter's Algorithm)
**Location:** `Renderer.java` → `render()`

```
1. Clear screen (black background)
2. Parallax background layers (6 layers)
3. Vignette overlay
4. Shadows (player, boss)
5. Boss sprite
6. Player sprite
7. Bullets
8. Particles
9. Beam attacks
10. Damage numbers
11. HUD elements (score, health, etc.)
12. Screen effects (flash, shake)
13. UI overlays (menus, buttons)
```

### Parallax Background System
**Location:** `Renderer.java` → `renderGame()`

**Structure:**
- 14 background sets (player selectable)
- 6 layers per set
- Each layer scrolls at different speed
- Layers loaded from `sprites/Backgrounds/background (X)/`

**Scroll Speed Formula:**
```java
layerSpeed = baseSpeed * (layerIndex + 1) * 0.15
```

### Particle System
**Location:** `Particle.java`, `Game.java`

**Features:**
- Object pooling (300 max particles)
- Types: impact, fire, smoke, explosion
- Fades out over lifetime
- Affected by velocity and gravity

---

## 💾 Save System

### Save File Structure
**Location:** `~/game_save.json`

**Format:** JSON via Gson library

**Saved Data:**
```json
{
  "totalMoney": 5000,
  "currentLevel": 10,
  "maxUnlockedLevel": 15,
  "speedUpgradeLevel": 5,
  "bulletSlowUpgradeLevel": 20,
  "luckyDodgeUpgradeLevel": 3,
  "attackWindowUpgradeLevel": 8,
  "unlockedItems": ["SHIELD", "DASH", "BOMB"],
  "equippedItemIndex": 1,
  "defeatedBosses": [true, true, true, ...],
  "achievements": [...],
  "contractsUnlocked": true,
  "extraLives": 2
}
```

### Save Triggers
- After each boss defeat
- After shop purchase
- After achievement unlock
- On game exit (graceful shutdown)

---

## 🔧 Common Modification Patterns

### Adding a New Boss Level
**Files to modify:**
1. `Boss.java` → `spawnBullets()` - Add case for new level
2. `GameData.java` → Extend `defeatedBosses` array if needed
3. `Renderer.java` → `renderLevelSelect()` - Add to level list
4. Test thoroughly with risk contracts

### Adding a New Upgrade
**Files to modify:**
1. `GameConfig.java` → Add `MAX_X_LEVEL` constant
2. `GameData.java` → Add level properties (persistent + active)
3. `PassiveUpgrade.java` → Add upgrade type enum
4. `PassiveUpgradeManager.java` → Add upgrade logic
5. `ShopManager.java` → Add shop UI button
6. `Renderer.java` → Add shop rendering
7. Apply effect in relevant game code

### Changing Game Balance
**Quick changes:**
- Player speed: `GameConfig.PLAYER_MAX_SPEED`
- Boss health: `GameConfig.BOSS_MAX_HITS`
- Vulnerability duration: `GameConfig.BOSS_VULNERABILITY_DURATION`
- Bullet spawn rates: `Boss.java` → `spawnBullets()` → bullet count
- Upgrade costs: `ShopManager.java` → upgrade prices
- Item cooldowns: `ActiveItem.java` → cooldownFrames

### Adding Visual Effects
**Files to modify:**
1. `ColorPalette.java` → Define colors
2. `Particle.java` → Create particle type if needed
3. `Renderer.java` → Draw effect in appropriate render method
4. `Game.java` → Spawn particles/effects in update loop

### Debugging Performance Issues
**Hotspots to check:**
1. `Game.update()` → PLAYING case - Bullet updates
2. `Renderer.renderGame()` → Drawing operations
3. Particle count - Check `MAX_PARTICLES` limit
4. Spatial grid - Verify `GRID_CELL_SIZE` is optimal
5. Asset loading - Check `AssetLoader` for errors

---

## 📊 Complexity Heat Map

### 🔴 High Complexity (Refactor Priority)
- `Game.java` - Too many responsibilities
- `Renderer.java` - Too many responsibilities
- `Boss.java` - Complex AI patterns (expected)

### 🟡 Medium Complexity
- `GameData.java` - Many properties but well-organized
- `ShopManager.java` - UI state management
- `PassiveUpgradeManager.java` - Calculation logic

### 🟢 Low Complexity
- `Player.java` - Clear, focused responsibility
- `Bullet.java` - Simple entity
- `Particle.java` - Simple entity
- `UIButton.java` - Simple component
- `GameState.java` - Simple enum

---

## 🎯 Navigation Quick Tips

### "I need to change..."
- **Player controls** → `Player.java` → `move()`
- **Boss behavior** → `Boss.java` → `update()` or `spawnBullets()`
- **UI appearance** → `Renderer.java` → search for relevant render method
- **Game constants** → `GameConfig.java`
- **Colors** → `ColorPalette.java`
- **Save data** → `GameData.java`
- **Shop prices** → `ShopManager.java`
- **Sound effects** → `SoundManager.java`
- **Achievements** → `AchievementManager.java`

### "I need to understand..."
- **Game loop** → `Game.java` → `run()` method
- **State machine** → `GameState.java` + `Game.java` → `update()` switch
- **Collision detection** → `Game.java` → search for "collision" or "graze"
- **Rendering order** → `Renderer.java` → `render()` method
- **Save system** → `GameData.java` → `save()` and `load()` methods

---

**Last Updated:** January 2026  
**Maintained by:** AI-assisted development
