# 🚀 MISSILE MAN

**A bullet-hell boss rush game with roguelike progression, active items, and risk contracts**

Originally created for a game jam, now evolved into a full-featured bullet hell experience with persistent upgrades, achievements, and multiple game modes.

---

## 🎮 Game Overview

**Genre:** Bullet Hell Boss Rush  
**Playstyle:** Precision dodging, vulnerability windows, risk-reward combat

### Core Mechanics
- **Vulnerability Windows** - Bosses are invincible until their 20-second attack window
- **3-Hit Boss System** - Damage the boss 3 times during vulnerability to win
- **Graze System** - Get close to bullets for bonus score and combos
- **Perfect Dodge** - Frame-perfect dodges grant brief invincibility
- **Active Items** - Unlock and equip powerful abilities with cooldowns
- **Persistent Progression** - Earn money to purchase permanent upgrades

---

## 🎯 Game Features

### 🌟 Core Systems
- **30+ Unique Boss Levels** - Each with distinct bullet patterns
- **Progressive Difficulty** - Levels unlock as you defeat bosses
- **Roguelike Elements** - Extra lives, run-based challenges
- **14 Parallax Backgrounds** - Beautiful scrolling environments
- **Achievement System** - Unlock achievements for special challenges
- **Save System** - Auto-save progress, upgrades, and unlocks

### ⚡ Active Items (Unlock by beating levels)
- **Lucky Charm** (Lv 3) - +50% money and score (passive)
- **Shield** (Lv 6) - Tank 3 hits with cooldown
- **Magnet** (Lv 9) - Pull dodged bullets for bonus score
- **Shockwave** (Lv 12) - Push bullets away
- **Dash** (Lv 15) - Dash with invincibility frames
- **Bomb** (Lv 18) - Clear all bullets on screen
- **Time Slow** (Lv 21) - Slow down time temporarily
- **Laser Beam** (Lv 24) - Fire powerful beam attack
- **Invincibility** (Lv 27) - Brief god mode

### 📈 Passive Upgrades (Purchase in shop)
- **Speed Boost** - Increase movement speed (Max: Level 10)
- **Bullet Slow** - Slow down enemy bullets (Max: Level 50)
- **Lucky Dodge** - Chance to survive fatal hits (Max: Level 12)
- **Attack Window** - Extend boss vulnerability duration (Max: Level 10)

### 🎲 Risk Contracts (Unlock at Level 6)
Increase difficulty for bonus rewards:
- **Bullet Storm** - 2x bullets (2x money)
- **Speed Demon** - 50% faster bullets (1.75x money)
- **Shieldless** - No shield item (1.5x money)
- **Can't Stop Moving** - Must keep moving (2.5x money)

### 💎 Polish Features
- **Combo System** - Chain dodges for score multipliers
- **Graze Detection** - Close calls, perfect dodges, frame-perfect timing
- **Screen Shake** - Impact feedback on hits
- **Particle Effects** - Explosions, impacts, trails (300 particles max)
- **Boss Intro Cinematics** - Dramatic boss appearances
- **Damage Numbers** - Floating combat text
- **Sound System** - 85+ sound effects and music tracks
- **Visual Effects** - Vignette, transitions, screen flash

---

## 🕹️ Controls

### Movement
- **WASD** or **Arrow Keys** - Move player
- **Mouse** - Navigate menus

### Actions
- **E** - Activate equipped item
- **ESC** - Pause game (during gameplay)
- **Click** - Select menu options

### Debug (if enabled)
- **D** - Toggle debug mode

---

## 🎯 How to Play

1. **Start Game** - Launch from main menu
2. **Select Level** - Choose from unlocked levels
3. **Choose Risk Contract** (optional) - Select difficulty modifier
4. **Dodge Bullets** - Avoid bullet patterns for 20 seconds
5. **Attack Window Opens** - Boss becomes vulnerable (golden glow)
6. **Hit Boss 3 Times** - Damage during vulnerability window
7. **Defeat Boss** - Earn money and unlock next level
8. **Visit Shop** - Spend money on permanent upgrades
9. **Equip Items** - Choose active item for next run
10. **Progress Further** - Unlock all 30+ levels!

---

## 💡 Pro Tips

### Survival
- **Master the Graze** - Get close to bullets (but not too close) for score
- **Perfect Dodge** - Within 8 pixels grants brief invincibility
- **Use Items Wisely** - Save powerful items for vulnerability windows
- **Watch the Timer** - Know when vulnerability window is coming

### Scoring
- **Graze Bullets** - 25px = graze, 15px = close call, 8px = perfect
- **Build Combos** - Chain dodges within 3-second window
- **Risk Contracts** - Higher difficulty = more money earned

### Progression
- **Upgrade Speed First** - Makes dodging easier
- **Bullet Slow Second** - More time to react
- **Lucky Dodge** - Extra lives for hard levels
- **Attack Window** - More time to damage boss

---

## 🛠️ Technical Details

### Technologies
- **Language:** Java (Java 8+)
- **GUI Framework:** Swing (javax.swing)
- **Graphics:** Java2D (java.awt)
- **Serialization:** Gson (JSON)
- **Performance:** 60 FPS game loop with spatial grid optimization

### Performance Features
- **Spatial Grid** - 50x50 pixel cells for collision optimization
- **Object Pooling** - Bullets and particles recycled
- **Cached Rendering** - Pre-created colors, fonts, composites
- **Particle Limit** - 300 max particles for consistent FPS

---

## 🚀 How to Run

### From Source
```bash
# Compile all Java files
javac -d bin src/*.java src/**/*.java

# Run the game
java -cp bin App
```

### From JAR
```bash
# Run the packaged JAR
java -jar MissileMan.jar
```

### Build JAR
```bash
# Create executable JAR
jar cvfm MissileMan.jar MANIFEST.MF -C bin . sprites/ SFX/
```

---

## 📁 Project Structure

```
CameComp1/
├── src/                    # Source code
│   ├── config/            # Configuration (GameConfig, ColorPalette)
│   ├── interfaces/        # Interface definitions
│   ├── Game.java          # Main game loop (~4800 lines)
│   ├── Renderer.java      # Rendering system (~4000 lines)
│   ├── Player.java        # Player entity
│   ├── Boss.java          # Boss entity
│   ├── GameData.java      # Save/load system
│   └── ...                # Other game classes
├── sprites/               # Visual assets
│   ├── Backgrounds/       # 14 parallax background sets
│   └── Missle Man Assets/ # Player and boss sprites
├── SFX/                   # Sound effects and music
│   ├── Explosions SFX/    # Explosion sounds
│   ├── Retro Game SFX/    # 85+ game sound effects
│   ├── Music Tracks/      # Background music
│   └── UI SFX/            # Menu sounds
├── bin/                   # Compiled classes
├── test/                  # Unit tests
└── docs/                  # Documentation (see DOCS_INDEX.md)
```

---

## 📚 Documentation

This project has extensive documentation for developers and AI assistants:

- **[QUICK_START.md](QUICK_START.md)** - Fast orientation guide
- **[AI_DEVELOPMENT_GUIDE.md](AI_DEVELOPMENT_GUIDE.md)** - Comprehensive development guide
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Technical architecture
- **[CLASS_REFERENCE.md](CLASS_REFERENCE.md)** - Class directory
- **[REFACTORING_ROADMAP.md](REFACTORING_ROADMAP.md)** - Future improvements
- **[DOCS_INDEX.md](DOCS_INDEX.md)** - Documentation hub

---

## 💾 Save System

Game automatically saves:
- **Money earned** - Persistent across sessions
- **Levels unlocked** - Progression tracking
- **Upgrades purchased** - Speed, bullet slow, lucky dodge, attack window
- **Items unlocked** - Active item collection
- **Achievements** - Achievement progress
- **Statistics** - High scores, best times

**Save Location:** `~/game_save.json`

---

## 🎨 Credits

### Assets
- **Background Music:** Various tracks
- **Sound Effects:** 85+ retro game sound effects
- **Sprites:** Custom missile and plane sprites

### Development
- **Original Concept:** Game Jam 2024
- **Current Version:** Evolved with roguelike progression and polish
- **AI Optimization:** January 2026 - Restructured for AI-assisted development

---

## 📊 Game Statistics

- **Lines of Code:** ~10,000
- **Levels:** 30+
- **Active Items:** 9
- **Passive Upgrades:** 4
- **Risk Contracts:** 5
- **Achievements:** Multiple challenges
- **Sound Effects:** 85+
- **Background Sets:** 14

---

## 🔧 Development

### Requirements
- Java 8 or higher
- Gson library (for JSON serialization)

### Contributing
See [REFACTORING_ROADMAP.md](REFACTORING_ROADMAP.md) for planned improvements and how to contribute.

---

## 📜 License

[Add license information]

---

**Enjoy dodging bullets and defeating bosses!** 🎮🚀

