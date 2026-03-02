# Plan: Flares Passive Upgrade

## Summary

Add a new passive upgrade called **Flares** (5 max levels). When homing bullets are detected near the player, flares auto-deploy from the back of the missile on a cooldown timer. Homing bullets have a chance to retarget onto flares instead of the player. On contact, both the flare and bullet are destroyed with an explosion VFX. Flares shoot out, then gradually decelerate while still drifting. Visuals are bright red with spark trails and a **glow effect**.

### Scaling per Level

| Level | Cooldown (frames) | Flare Count | Retarget Chance | Detection Range |
|-------|-------------------|-------------|-----------------|-----------------|
| 1     | ~900 (15s)        | 1           | 30%             | 150px           |
| 2     | ~780 (13s)        | 2           | 45%             | 175px           |
| 3     | ~660 (11s)        | 3           | 60%             | 200px           |
| 4     | ~540 (9s)         | 3           | 75%             | 225px           |
| 5     | ~420 (7s)         | 4           | 90%             | 250px           |

---

## Steps

### Step 1 — Create `Flare.java` (new file in `src/`)

Create a new class `src/Flare.java` representing a single flare entity. Fields:

- `x`, `y` — position (double)
- `vx`, `vy` — velocity (double), initialized to shoot backward from player, decelerates over time
- `lifetime` — remaining frames alive (~180 frames / 3 seconds)
- `age` — frames since spawned
- `active` — boolean, starts true
- `deceleration` — constant ~0.98 per frame (multiply velocity each update)
- `glowRadius` — float, pulses slightly for glow rendering

Methods:
- `update(double deltaTime)` — move by velocity * dt, multiply velocity by `deceleration`, decrement lifetime, set `active = false` when expired
- `draw(Graphics2D g)` — render as bright red core circle (~4px) with:
  - **Glow effect**: draw 2–3 concentric semi-transparent circles behind the core (e.g., `new Color(255, 50, 30, 60)` at 12px, `new Color(255, 80, 40, 30)` at 20px) — glow radius pulses slightly using `Math.sin(age * 0.15)`
  - Spark trail: spawn a small tail line in the direction opposite to velocity using `new Color(255, 200, 50, alphaFade)`
- `getX()`, `getY()` — position getters
- `isActive()` — returns `active`
- `collidesWith(Bullet b)` — distance check between flare and bullet (radius ~15px)

### Step 2 — Add `FLARES` to `UpgradeType` enum in `PassiveUpgrade.java`

In `src/PassiveUpgrade.java`, add `FLARES` to the `UpgradeType` enum after `TARGETING`.

In the `getMultiplier()` switch statement, add:
```java
case FLARES: return activeLevel;  // raw level, scaling handled in Game.java
```

### Step 3 — Register the upgrade in `PassiveUpgradeManager.java`

In `src/PassiveUpgradeManager.java` → `initializeUpgrades()`, add:

```java
addUpgrade("flares", "Flares",
    "Deploys decoy flares that divert homing missiles. Upgrades increase frequency, count, and diversion chance.",
    PassiveUpgrade.UpgradeType.FLARES, 800, 5);
```

Base cost 800 with exponential scaling (1.8x per level), same pattern as existing passives. Save/load is automatic — no changes needed in `SaveData.java` or `SaveManager.java`.

### Step 4 — Add `targetX` / `targetY` fields to `Bullet.java`

In `src/Bullet.java`, add fields to support alternative targeting:

- `double flareTargetX, flareTargetY` — alternative homing target coordinates
- `boolean targetingFlare` — when true, home toward flare coords instead of player

Modify the `HOMING` case in `update()` (around line 236):
- If `targetingFlare` is true, compute `angleToPlayer` using `flareTargetX/Y` instead of `player.getX()/getY()`
- Increase turn rate slightly when targeting flare (e.g., `0.035` instead of `0.02`) so bullets commit to the flare faster

Add methods:
- `setFlareTarget(double x, double y)` — sets `flareTargetX/Y` and `targetingFlare = true`
- `clearFlareTarget()` — sets `targetingFlare = false`
- `isTargetingFlare()` — getter

### Step 5 — Add flare system fields to `Game.java`

In `src/Game.java`, add instance fields:

- `private List<Flare> flares;` — active flare entities
- `private double flareCooldownTimer;` — frames until next deployment allowed (starts at 0)
- `private static final double FLARE_BASE_COOLDOWN = 900;` — 15 seconds at 60fps

Initialize `flares = new ArrayList<>()` in the constructor alongside other entity lists. Reset `flareCooldownTimer = 300` (5s grace period) in the level start / reset method.

### Step 6 — Add `getActiveFlaresLevel()` helper in `Game.java`

Follow the exact pattern of `getActiveTargetingLevel()` (around line 9750):

```java
private int getActiveFlaresLevel() {
    if (passiveUpgradeManager != null) {
        PassiveUpgrade upgrade = passiveUpgradeManager.getUpgrade("flares");
        if (upgrade != null) return upgrade.getActiveLevel();
    }
    return 0;
}
```

### Step 7 — Add flare deployment logic in `Game.java` update loop

In the `updateGameState()` method (or wherever the main per-frame logic runs), add a flare deployment block **before** the bullet collision loop:

1. Decrement `flareCooldownTimer` by `deltaTime`
2. If `flareCooldownTimer <= 0` and `getActiveFlaresLevel() > 0`:
   - Scan nearby bullets using `getNearbyBullets(player.getX(), player.getY())` with an **extended range** check (150–250px based on level) — filter for `bullet.getType() == BulletType.HOMING` and `!bullet.isTargetingFlare()`
   - If any qualifying homing bullets found:
     - Determine flare count from level (1/2/3/3/4)
     - Spawn that many `Flare` objects:
       - Position: player's current `x, y`
       - Velocity: shoot backward (opposite of player heading) with spread — base speed ~3-4px/frame, fanned out at angles (e.g., -30°, 0°, +30° from backward direction)
     - For each nearby homing bullet, roll the retarget chance (30%–90% based on level):
       - On success: call `bullet.setFlareTarget(flare.getX(), flare.getY())` — assign to the nearest spawned flare
     - Reset `flareCooldownTimer` to `FLARE_BASE_COOLDOWN - (level - 1) * 120`
     - Spawn deployment VFX particles (see Step 9)

### Step 8 — Add flare update and collision logic in `Game.java`

In the same update loop, after flare deployment:

1. **Update flares**: iterate `flares` list, call `flare.update(deltaTime)`, remove if `!flare.isActive()`
2. **Update flare targets on bullets**: for each bullet that `isTargetingFlare()`:
   - Find the flare it's tracking (nearest alive flare — or store a reference)
   - Update `bullet.setFlareTarget(flare.getX(), flare.getY())` each frame so the bullet follows the moving flare
   - If the tracked flare is no longer active, call `bullet.clearFlareTarget()` (bullet resumes tracking player)
3. **Flare-bullet collision**: for each active flare, check all nearby homing bullets:
   - If `flare.collidesWith(bullet)` and `bullet.isTargetingFlare()`:
     - Remove both: `flare.active = false`, mark bullet for removal / return to pool
     - Spawn explosion VFX: 6–10 particles at the collision point using `addParticle()` with `ParticleType.EXPLOSION` in red/orange colors
     - Spawn **glowing** spark particles (see Step 9)

### Step 9 — Add glowing particle effects

#### 9a — Add `FLARE_SPARK` to `ParticleType` enum in `Particle.java`

In `src/Particle.java`, add `FLARE_SPARK` to the `ParticleType` enum.

#### 9b — Add glow rendering in `Particle.draw()`

In the `draw()` method's switch statement, add a `FLARE_SPARK` case:

- Draw a **glow halo** first: 2 concentric circles with decreasing alpha
  - Outer glow: `new Color(255, 60, 30, 40)`, radius = `size * 3`
  - Inner glow: `new Color(255, 100, 50, 80)`, radius = `size * 1.8`
- Draw the core: filled circle in `color` (bright red/orange), radius = `size`
- Optionally enable `RenderingHints.VALUE_ANTIALIAS_ON` for smooth glow edges

#### 9c — Spawn glowing particles in `Game.java`

- **On flare deployment**: spawn 3–5 `FLARE_SPARK` particles per flare at the player's position, bright red `new Color(255, 70, 30)`, size 3–5, lifetime 20–30 frames, velocity fanning backward
- **On flare-bullet collision**: spawn 8–12 `FLARE_SPARK` particles at collision point, mixed red/orange/yellow colors, size 2–6, lifetime 15–25, velocity radiating outward in all directions
- **Flare trail particles**: each frame while a flare is alive, spawn 1 `FLARE_SPARK` particle at flare position with low velocity, small size (2), short lifetime (10), creates a glowing trail behind each flare

### Step 10 — Render flares in `Renderer.java`

In `src/Renderer.java` → `drawGame()`, add flare rendering **after particles but before the player** (around line 5921):

```java
// Draw flares
for (Flare flare : flares) {
    if (flare.isActive()) flare.draw(g);
}
```

The `Flare.draw()` method (from Step 1) handles its own glow rendering.

### Step 11 — Clear flares on level transitions

In `Game.java`, wherever bullets/particles are cleared between levels or on game over:
- Call `flares.clear()`
- Reset `flareCooldownTimer`
- Clear `targetingFlare` on all remaining bullets

---

## Verification

1. **Compile**: build with `javac` — confirm no errors from new enum values, new class, modified bullet fields
2. **Shop test**: open the passive upgrade shop, verify "Flares" appears with correct name, description, cost (800), and max level (5)
3. **Purchase & equip**: buy 1 level, equip it in loadout, start a boss fight that uses homing bullets
4. **Deployment test**: wait for homing bullets to approach — verify flares eject backward from the missile with red glowing spark trails
5. **Retargeting test**: observe that some homing bullets curve toward flares instead of player
6. **Collision test**: verify that when a bullet hits a flare, both are destroyed with a glowing explosion effect
7. **Upgrade scaling**: buy up to level 5, verify more flares spawn, cooldown is shorter, and more bullets get diverted
8. **Save/load test**: save game, reload, verify flares level persists
9. **No-homing test**: fight a boss that uses only normal bullets — verify flares never deploy (no wasted cooldowns)
10. **Performance**: check frame rate with max flares + many homing bullets — particle count should stay within `MAX_PARTICLES` cap

---

## Decisions

- **Flare as a new class** rather than reusing Particle: flares need collision detection, individual tracking, and are targeted by bullets — too complex for the particle system
- **Alternative targeting via `flareTargetX/Y` fields on Bullet**: simplest approach since Bullet has no generic target system — avoids refactoring all bullet code
- **Glow via concentric semi-transparent circles**: cheaper than shader-based glow, consistent with the existing `Graphics2D` rendering pipeline
- **`FLARE_SPARK` particle type**: separate from regular `SPARK` so glow rendering only applies to flare effects without changing existing particle visuals
- **Exponential cost scaling** (800 base, 1.8x): consistent with all other passives in the shop
