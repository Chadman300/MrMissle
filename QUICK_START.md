# Quick Start Guide for AI Assistants

## 🚀 First Time Working with This Codebase?

Read this FIRST, then refer to other docs as needed.

---

## 📚 Documentation Structure

1. **THIS FILE** - Quick start, immediate orientation
2. **AI_DEVELOPMENT_GUIDE.md** - Comprehensive guide for AI assistants
3. **ARCHITECTURE.md** - Technical architecture reference
4. **README.md** - User-facing project readme (if exists)

---

## ⚡ 30-Second Overview

**What is this?**  
A bullet-hell boss rush game where players dodge patterns and attack during vulnerability windows.

**Tech Stack:**  
Java + Swing (Java2D for rendering)

**Key Stats:**
- ~10,000 lines of code
- 22 Java source files
- 60 FPS game loop
- Save system via JSON

---

## 🎯 Most Common Requests & Where to Look

| User Request | File(s) | Specific Location |
|--------------|---------|-------------------|
| "Change player speed" | `config/GameConfig.java` | `PLAYER_MAX_SPEED` |
| "Modify boss attacks" | `Boss.java` | `spawnBullets()` method |
| "Adjust colors/theme" | `config/ColorPalette.java` | Color constants |
| "Fix save system" | `GameData.java` | `save()` / `load()` methods |
| "Add new upgrade" | `PassiveUpgrade.java`<br>`PassiveUpgradeManager.java`<br>`GameData.java` | Multiple files |
| "Change UI layout" | `Renderer.java` | Relevant `render*()` method |
| "Modify shop prices" | `ShopManager.java` | Constructor price setup |
| "Add achievement" | `AchievementManager.java` | `initializeAchievements()` |
| "Balance difficulty" | `GameConfig.java`<br>`Boss.java` | Timing constants<br>Bullet spawn logic |

---

## 🏗️ Architecture in 3 Sentences

1. **Game.java** is the orchestrator - runs the game loop, manages state, coordinates everything
2. **Renderer.java** handles ALL drawing - completely separated from game logic
3. **GameData.java** is the single source of truth for persistence - all progress, unlocks, and upgrades

---

## 📁 Critical Files (Must Understand)

### Game.java (~4800 lines) ⚠️
**Role:** Main game loop, state machine, input handling, collision detection

**Warning:** This file is VERY LARGE - needs refactoring

**Quick Navigation:**
- Line ~10: Class definition, field declarations
- Line ~250: Constructor
- Line ~500: Game loop (`run()` method)
- Line ~1000+: State-specific update logic (search for `switch(gameState)`)
- Line ~3000+: Collision detection

### Renderer.java (~4000 lines) ⚠️
**Role:** All rendering operations

**Warning:** This file is VERY LARGE - needs refactoring

**Quick Navigation:**
- Find `render()` method - main entry point
- Search for `render` + state name (e.g., "renderMenu", "renderShop")
- Bottom of file: Helper methods for UI elements

### GameData.java (~430 lines) ✅
**Role:** Save/load system, player progression

**Structure:**
- Fields: All persistent data
- Methods: `save()`, `load()`, getters/setters

**Key Concept:** This is the ONLY class that touches the save file

---

## 🎮 Game Loop Flow

```
60 times per second:
├─ 1. Calculate deltaTime
├─ 2. update()
│   ├─ Handle input
│   ├─ Update entities based on gameState
│   ├─ Check collisions
│   └─ Update UI state
└─ 3. render()
    └─ Renderer.render() draws everything
```

---

## 🔍 How to Find Code

### Method 1: Semantic Search
Use your semantic search tool with queries like:
- "player movement code"
- "boss bullet spawning"
- "collision detection"
- "save game logic"

### Method 2: File-Specific Search
If you know the file, search within it:
- Player code → Search in `Player.java`
- Boss code → Search in `Boss.java`
- UI code → Search in `Renderer.java`
- Data → Search in `GameData.java`

### Method 3: State-Based Search
For UI/menu code, search for the state name:
- Main menu → Search "MENU" in `Renderer.java`
- Shop → Search "SHOP" in `Renderer.java` and `ShopManager.java`
- Playing → Search "PLAYING" in `Game.java`

---

## 🎨 Visual Hierarchy

```
Background (parallax layers)
  └─ Shadows
      └─ Boss
          └─ Player
              └─ Bullets
                  └─ Particles
                      └─ Effects (damage numbers, beams)
                          └─ HUD
                              └─ UI Overlays (menus)
                                  └─ Screen Effects (flash, shake)
```

---

## 💾 Save System Quick Facts

- **Location:** `~/game_save.json`
- **Format:** JSON (Gson library)
- **Saves:** Money, upgrades, unlocks, achievements, level progress
- **Auto-save:** After boss defeat, shop purchase, achievement unlock
- **Manual save:** On game exit

---

## 🎯 Configuration System

**New in restructuring:** All magic numbers extracted to config files

### GameConfig.java
All gameplay constants:
- Player physics (speed, acceleration, friction)
- Boss mechanics (health, vulnerability duration)
- Combat (graze distances, combo timeout)
- Performance (particle limits, grid size)
- UI timing (animation durations)

### ColorPalette.java
All visual constants:
- Color definitions (particles, UI, effects)
- Alpha composites (transparency levels)

**Before changing values in code:** Check if constant exists in config first!

---

## 🧩 New Interface System

**New in restructuring:** Interfaces for better abstraction

- `Positionable` - Has X/Y position
- `Collidable` - Can collide (extends Positionable)
- `Updatable` - Updates each frame
- `Renderable` - Draws to screen

**Use these when:** Creating new entity types or systems

---

## ⚠️ Common Pitfalls

### 1. Modifying Game.java
- **Problem:** File is huge, easy to get lost
- **Solution:** Use search, work in small sections, test frequently

### 2. Frame Rate Dependent Code
- **Problem:** Code that assumes 60 FPS
- **Solution:** Use `deltaTime` for all time-based calculations

### 3. Coordinate System
- **Problem:** Forgetting Y increases downward
- **Solution:** (0,0) = top-left, Y down, X right

### 4. Save File Corruption
- **Problem:** Invalid JSON breaks save system
- **Solution:** Always test save/load after changing GameData fields

### 5. Collision Performance
- **Problem:** Checking every bullet against every entity
- **Solution:** Spatial grid already implemented, use it

---

## 🚀 Making Your First Change

### Example: "Change player speed to 8.0"

**Steps:**
1. Open `config/GameConfig.java`
2. Find `PLAYER_MAX_SPEED`
3. Change from `6.0` to `8.0`
4. Save file
5. Recompile and test

**That's it!** No need to search through Game.java.

### Example: "Add new boss attack pattern for level 30"

**Steps:**
1. Open `Boss.java`
2. Find `spawnBullets()` method
3. Add case for `currentLevel == 30`
4. Define bullet spawn pattern (copy similar level as template)
5. Test with and without risk contracts

---

## 🐛 Debugging Tips

### Game crashes on startup
→ Check `AssetLoader.java` console output for missing assets

### Save file not loading
→ Delete `~/game_save.json` and restart (creates fresh save)

### Player not moving
→ Check `Player.java` → `move()` method and GameConfig constants

### Boss not spawning bullets
→ Check `Boss.java` → `spawnBullets()` for your level number

### UI not responding
→ Check `Game.java` → input handling for current gameState

---

## 📞 Need More Detail?

- **Comprehensive guide** → Read `AI_DEVELOPMENT_GUIDE.md`
- **Architecture details** → Read `ARCHITECTURE.md`
- **Specific system** → Search in relevant file
- **Still stuck?** → Ask for clarification with specific file/line context

---

## ✅ Pre-Flight Checklist

Before making changes:
- [ ] Do I understand which state this affects?
- [ ] Have I found the relevant file(s)?
- [ ] Do I know if config constants exist for this?
- [ ] Do I understand the data flow?
- [ ] Can I test this change easily?

---

**Remember:** 
- Start small
- Test frequently  
- Use the config files
- Search before asking
- Check documentation first

**You've got this! 🚀**
