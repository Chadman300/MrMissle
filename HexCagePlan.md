# Hex Cage Attack — Implementation Plan

## Overview
A new specialty boss attack: **6 beams form a hexagon** centered on the boss. The hexagon starts at a large radius, **shrinks inward** (making the safe zone smaller), **holds** at minimum size, then **expands back out**. Boss fire rate is reduced during the attack, and the boss cannot move past the beam walls. Introduced at **level 22+** with its own showcase entry.

---

## Files to Modify

### 1. Boss.java — Fields
Add after `spinningBeamDirection` (~line 86):

| Field | Type | Purpose |
|---|---|---|
| `hexCageActive` | `boolean` | Whether hex cage is currently active |
| `hexCageRadius` | `double` | Current hexagon radius from boss center |
| `hexCageTargetRadius` | `double` | Radius the hex is moving toward |
| `hexCageMaxRadius` | `double` | Starting/max radius (~300–400 px) |
| `hexCageMinRadius` | `double` | Smallest radius (~120–150 px) |
| `hexCageMoveSpeed` | `double` | How fast the radius changes per frame |
| `hexCageWarningTimer` | `int` | Frames of blink warning before activation |
| `hexCageHoldTimer` | `int` | Frames to hold at min/max radius |
| `hexCageTimer` | `int` | Overall attack duration timer |
| `hexCageWidth` | `int` | Beam width (visual thickness of each side) |
| `hexCageAngle` | `double` | Base rotation angle of the hexagon |
| `hexCagePhase` | `int` | 0=shrinking, 1=hold-min, 2=expanding, 3=hold-max |
| `hexCageAttackCooldown` | `int` | Cooldown frames between hex cage attacks |
| `forceHexCage` | `boolean` | Showcase force flag |
| `disableHexCage` | `boolean` | Showcase disable flag |

### 2. Boss.java — Force/Disable Flags
Add near line ~121, after `disableSpinningBeam`:
```java
private boolean forceHexCage = false;
private boolean disableHexCage = false;
```

### 3. Boss.java — Update Loop
Add **after** spinning beam update (~line 993). Phases:

1. **Warning** — `hexCageWarningTimer > 0`: decrement timer, hex cage blinks but no collision
2. **Shrinking** (phase 0) — decrease `hexCageRadius` by `hexCageMoveSpeed` per frame until `hexCageMinRadius`
3. **Hold at min** (phase 1) — hold for `hexCageHoldTimer` frames (~120 frames / 2 sec)
4. **Expanding** (phase 2) — increase `hexCageRadius` by `hexCageMoveSpeed` until `hexCageMaxRadius`
5. **Hold at max** (phase 3) — brief hold, then set `hexCageActive = false`

During active: `boss.vx = 0; boss.vy = 0` (boss frozen in place).

### 4. Boss.java — Trigger / Cooldown
Add after spinning beam trigger logic (~line 947):
- Introduced at **level 22+**
- Minimum **20-second cooldown** (1200 frames at 60fps)
- ~25% chance per eligible frame
- Blocked if `spinningBeamActive` or `disableHexCage`
- Forced if `forceHexCage`

### 5. Boss.java — `startHexCageAttack()` Method
Add after `startSpinningBeamAttack()`:
```java
public void startHexCageAttack() {
    hexCageActive = true;
    hexCagePhase = 0; // shrinking
    hexCageMaxRadius = 350;   // starting radius
    hexCageMinRadius = 130;   // smallest radius
    hexCageRadius = hexCageMaxRadius;
    hexCageMoveSpeed = 1.5;   // px per frame
    hexCageWarningTimer = 180; // 3 sec warning
    hexCageHoldTimer = 120;    // 2 sec hold at min
    hexCageWidth = 30;         // beam thickness
    hexCageAngle = 0;          // upright hex
    hexCageTimer = 720;        // ~12 sec total
    hexCageAttackCooldown = 1200; // 20 sec cooldown
}
```

### 6. Boss.java — Getters
Add after spinning beam getters (~line 2083):
```java
public boolean isHexCageActive() { return hexCageActive; }
public double getHexCageRadius() { return hexCageRadius; }
public int getHexCageWarningTimer() { return hexCageWarningTimer; }
public int getHexCageWidth() { return hexCageWidth; }
public double getHexCageAngle() { return hexCageAngle; }
public int getHexCagePhase() { return hexCagePhase; }
```

### 7. Boss.java — Setters
Add after spinning beam setters (~line 2650):
```java
public void setForceHexCage(boolean b) { forceHexCage = b; }
public void setDisableHexCage(boolean b) { disableHexCage = b; }
```

### 8. Boss.java — Shooting Reduction
At line ~907, multiply by `hexCageActive ? 0.3 : 1.0` (70% reduction during hex cage).

### 9. Boss.java — Normal Beam Blocking
At line ~918, add `&& !hexCageActive` to `shouldFireBeams` condition.

### 10. Boss.java — Movement Constraint
When `hexCageActive`, boss position stays frozen (vx=vy=0 in update loop).

---

### 11. Game.java — ATTACK_INTROS Entry
Add to `ATTACK_INTROS` array after the spinning_beam entry (~line 483):
```java
{"hex_cage", "22", "Hex Cage", "Beam walls form a shrinking hexagon!\nStay inside and dodge the closing walls!", "Beam"}
```

### 12. Game.java — Showcase Switch
Add case after spinning_beam case (~line 5768):
```java
case "hex_cage":
    boss.setForceHexCage(true);
    boss.setDisableSpinningBeam(true);
    boss.setDisableBeams(true);
    break;
```

### 13. Game.java — Collision Detection
Add after spinning beam collision (~line 8648). For each of the 6 hex sides:
- Compute the side's center point using `hexCageRadius` and angle offset (`i * 60°`)
- Check if the player's bounding box crosses any side (treat each side as a rotated rectangle of `hexCageWidth` thickness)
- If player is **outside** the hexagon boundary, deal damage

### 14. Renderer.java — Rendering
Add after spinning beam rendering (~line 6712):
- Draw 6 beam segments as **rotated rectangles** forming a hexagon at `hexCageRadius` from boss center
- Each segment spans between two adjacent hex vertices
- **Warning phase**: blinking outlines (alternating alpha based on frame count)
- **Active phase**: full beam with scanlines + edge borders (reuse `SPIN_BEAM_SCANLINE_TILE` and same style as spinning beams)
- Color: could use a distinct tint (e.g., cyan/teal) to differentiate from spinning beam

---

## Hexagon Geometry Reference

Vertices of a regular hexagon centered at `(cx, cy)` with radius `r` and base angle `a`:
```
vertex[i] = (cx + r * cos(a + i * π/3), cy + r * sin(a + i * π/3))   for i = 0..5
```

Each side connects `vertex[i]` to `vertex[(i+1) % 6]`.

Side midpoint for beam rectangle placement:
```
midX = (vertex[i].x + vertex[i+1].x) / 2
midY = (vertex[i].y + vertex[i+1].y) / 2
sideAngle = atan2(vertex[i+1].y - vertex[i].y, vertex[i+1].x - vertex[i].x)
sideLength = r  (for a regular hexagon, side length == radius)
```

---

## Phase Timeline (approximate at 60fps)

| Phase | Duration | Description |
|---|---|---|
| Warning | 180 frames (3s) | Blinking hex outline, no collision |
| Shrinking | ~147 frames (~2.4s) | Radius goes from 350 → 130 at 1.5 px/frame |
| Hold at min | 120 frames (2s) | Hex stays at 130 radius |
| Expanding | ~147 frames (~2.4s) | Radius goes from 130 → 350 at 1.5 px/frame |
| Done | — | `hexCageActive = false` |
| **Total** | **~594 frames (~10s)** | |

---

## Level Scaling Ideas (future)
- **Level 24+**: Hex cage rotates slowly while shrinking
- **Level 26+**: Smaller minimum radius (100 px)
- **Level 28+**: Faster shrink speed (2.0 px/frame)
- **Level 30+**: Two consecutive hex cages (brief pause between)
