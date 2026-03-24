import sys

with open('src/Renderer.java', 'rb') as f:
    content = f.read()

# Find the marker line and replace the entire damage overlay block
marker = b'overlay_MARKER'
marker_idx = content.find(marker)
if marker_idx == -1:
    print("ERROR: Marker not found")
    sys.exit(1)

# Find start of the comment line (go back to find "// Boss accumulated")
line_start = content.rfind(b'\n', 0, marker_idx) + 1

# Find the end of the old block: the closing "}" + \r\n before "// Draw shockwave"
end_marker = b'// Draw shockwave during recovery phase'
end_idx = content.find(end_marker, marker_idx)
if end_idx == -1:
    print("ERROR: end marker not found")
    sys.exit(1)

# Go back to find the blank line before the shockwave comment
# The old block ends with "}\r\n\r\n" so back up past whitespace
block_end = end_idx
# Back up to include the blank lines between blocks
while block_end > 0 and content[block_end-1:block_end] in [b'\r', b'\n', b' ']:
    block_end -= 1
block_end += 1  # include the newline

old_block = content[line_start:block_end]
print(f"Found old block at byte {line_start}, length {len(old_block)}")
print(f"First 200 bytes: {old_block[:200]}")

# Build replacement
# Detect indent
indent = b'            '
nl = b'\r\n'

new_block = indent + b'// Boss accumulated damage overlay - smoke wisps & fire glow' + nl
new_block += indent + b'float bossHpPct = boss.getHealthPercent(); // 1.0 = full, 0.0 = dead' + nl
new_block += indent + b'if (boss.isFinalBoss()) {' + nl
new_block += indent + b'    // Final boss: 6-state progressive damage system based on getDamageState()' + nl
new_block += indent + b'    int damageState = boss.getDamageState();' + nl
new_block += indent + b'    if (damageState > 0) {' + nl
new_block += indent + b'        Composite _damSave = g.getComposite();' + nl
new_block += indent + b'        int bSize = boss.getSize();' + nl
new_block += indent + b'        int bCx = (int) boss.getX();' + nl
new_block += indent + b'        int bCy = (int) boss.getY();' + nl
new_block += nl
new_block += indent + b'        if (damageState >= 1) {' + nl
new_block += indent + b'            // State 1: Light scratches - thin dark lines across body' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.15f));' + nl
new_block += indent + b'            g.setColor(new Color(30, 30, 30));' + nl
new_block += indent + b'            g.setStroke(RenderCache.getStroke(1.5f));' + nl
new_block += indent + b'            int scratchSpread = bSize / 3;' + nl
new_block += indent + b'            for (int i = 0; i < 4; i++) {' + nl
new_block += indent + b'                int sx = bCx - scratchSpread + (int)(Math.sin(i * 2.7) * scratchSpread);' + nl
new_block += indent + b'                int sy = bCy - scratchSpread / 2 + i * (scratchSpread / 2);' + nl
new_block += indent + b'                g.drawLine(sx, sy, sx + 15 + i * 5, sy + 8 - i * 3);' + nl
new_block += indent + b'            }' + nl
new_block += indent + b'        }' + nl
new_block += indent + b'        if (damageState >= 2) {' + nl
new_block += indent + b'            // State 2: Smoke wisps - faint gray haze near wing tips' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.2f));' + nl
new_block += indent + b'            g.setColor(new Color(60, 60, 60));' + nl
new_block += indent + b'            int wispW = (int)(bSize * 0.3);' + nl
new_block += indent + b'            int wispH = (int)(bSize * 0.2);' + nl
new_block += indent + b'            g.fillOval(bCx - bSize / 2 - wispW / 4, bCy - wispH / 2, wispW, wispH);' + nl
new_block += indent + b'            g.fillOval(bCx + bSize / 2 - wispW * 3 / 4, bCy - wispH / 2, wispW, wispH);' + nl
new_block += indent + b'        }' + nl
new_block += indent + b'        if (damageState >= 3) {' + nl
new_block += indent + b'            // State 3: Thick smoke + small fires' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.35f));' + nl
new_block += indent + b'            g.setColor(new Color(40, 40, 40));' + nl
new_block += indent + b'            int smokeW = (int)(bSize * 1.0);' + nl
new_block += indent + b'            int smokeH = (int)(bSize * 0.5);' + nl
new_block += indent + b'            g.fillOval(bCx - smokeW / 2, bCy - smokeH / 2, smokeW, smokeH);' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.3f));' + nl
new_block += indent + b'            g.setColor(new Color(255, 140, 30));' + nl
new_block += indent + b'            int fireS = bSize / 5;' + nl
new_block += indent + b'            g.fillOval(bCx - bSize / 4 - fireS / 2, bCy + bSize / 6, fireS, fireS);' + nl
new_block += indent + b'            g.fillOval(bCx + bSize / 6 - fireS / 2, bCy - bSize / 6, fireS, fireS);' + nl
new_block += indent + b'        }' + nl
new_block += indent + b'        if (damageState >= 4) {' + nl
new_block += indent + b'            // State 4: Heavy fire + thick black smoke trail' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.45f));' + nl
new_block += indent + b'            g.setColor(new Color(20, 20, 20));' + nl
new_block += indent + b'            int trailW = (int)(bSize * 0.6);' + nl
new_block += indent + b'            int trailH = (int)(bSize * 1.2);' + nl
new_block += indent + b'            g.fillOval(bCx - trailW / 2, bCy, trailW, trailH);' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.4f));' + nl
new_block += indent + b'            g.setColor(new Color(255, 100, 10));' + nl
new_block += indent + b'            int fireW = (int)(bSize * 0.7);' + nl
new_block += indent + b'            int fireH = (int)(bSize * 0.4);' + nl
new_block += indent + b'            g.fillOval(bCx - fireW / 2, bCy - fireH / 3, fireW, fireH);' + nl
new_block += indent + b'        }' + nl
new_block += indent + b'        if (damageState >= 5) {' + nl
new_block += indent + b'            // State 5: Engulfed in flames + bright fire' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.5f));' + nl
new_block += indent + b'            g.setColor(new Color(255, 80, 0));' + nl
new_block += indent + b'            int engulfW = (int)(bSize * 1.3);' + nl
new_block += indent + b'            int engulfH = (int)(bSize * 0.9);' + nl
new_block += indent + b'            g.fillOval(bCx - engulfW / 2, bCy - engulfH / 2, engulfW, engulfH);' + nl
new_block += indent + b'            g.setComposite(RenderCache.getAlpha(0.25f));' + nl
new_block += indent + b'            g.setColor(new Color(255, 255, 200));' + nl
new_block += indent + b'            int coreW = (int)(bSize * 0.5);' + nl
new_block += indent + b'            int coreH = (int)(bSize * 0.3);' + nl
new_block += indent + b'            g.fillOval(bCx - coreW / 2, bCy - coreH / 2, coreW, coreH);' + nl
new_block += indent + b'        }' + nl
new_block += indent + b'        g.setComposite(_damSave);' + nl
new_block += indent + b'    }' + nl
new_block += indent + b'} else if (bossHpPct < 0.7f) {' + nl
new_block += indent + b'    Composite _damSave = g.getComposite();' + nl
new_block += indent + b'    int bSize = boss.getSize();' + nl
new_block += indent + b'    int bCx = (int) boss.getX();' + nl
new_block += indent + b'    int bCy = (int) boss.getY();' + nl
new_block += indent + b'    float dmgRatio = 1.0f - bossHpPct;' + nl
new_block += indent + b'    float overlayAlpha = Math.min(dmgRatio * 0.7f, 0.55f);' + nl
new_block += nl
new_block += indent + b'    // Dark smoke haze across body' + nl
new_block += indent + b'    g.setComposite(RenderCache.getAlpha(overlayAlpha * 0.6f));' + nl
new_block += indent + b'    g.setColor(new Color(40, 40, 40));' + nl
new_block += indent + b'    int smokeW = (int)(bSize * 1.4 * dmgRatio);' + nl
new_block += indent + b'    int smokeH = (int)(bSize * 0.8 * dmgRatio);' + nl
new_block += indent + b'    g.fillOval(bCx - smokeW / 2, bCy - smokeH / 2, smokeW, smokeH);' + nl
new_block += nl
new_block += indent + b'    // Fire glow when heavily damaged (>50% lost)' + nl
new_block += indent + b'    if (bossHpPct < 0.5f) {' + nl
new_block += indent + b'        float fireAlpha = Math.min((0.5f - bossHpPct) * 1.2f, 0.45f);' + nl
new_block += indent + b'        g.setComposite(RenderCache.getAlpha(fireAlpha));' + nl
new_block += indent + b'        g.setColor(new Color(255, 120, 20));' + nl
new_block += indent + b'        int fireW = (int)(bSize * 0.9 * dmgRatio);' + nl
new_block += indent + b'        int fireH = (int)(bSize * 0.5 * dmgRatio);' + nl
new_block += indent + b'        g.fillOval(bCx - fireW / 2, bCy - fireH / 3, fireW, fireH);' + nl
new_block += indent + b'    }' + nl
new_block += indent + b'    g.setComposite(_damSave);' + nl
new_block += indent + b'}'

result = content[:line_start] + new_block + content[block_end-1:]

with open('src/Renderer.java', 'wb') as f:
    f.write(result)

print(f"Replacement done. Old block {len(old_block)} bytes -> new block {len(new_block)} bytes")
