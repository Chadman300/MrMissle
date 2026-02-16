# Fix UI Stack Script - 3 fixes:
# 1. Create unified top-right UI stacking
# 2. Move UI stack above overlay image
# 3. Slow down post-level animations

$rendererFile = "c:\Users\vital\source\repos\CameComp1\src\Renderer.java"
$gameFile = "c:\Users\vital\source\repos\CameComp1\src\Game.java"

# Read files
$rendererLines = [System.IO.File]::ReadAllLines($rendererFile)
$gameLines = [System.IO.File]::ReadAllLines($gameFile)

Write-Host "Renderer lines: $($rendererLines.Count)"
Write-Host "Game lines: $($gameLines.Count)"

# ============================================================
# PART 1: Fix Renderer.java - UI Stack restructuring
# ============================================================

# First, find the key markers in the current file
# The first edit already replaced the top-left HUD + dodge combo with a marker comment
# Find that marker
$markerLine = -1
for ($i = 0; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match '\[UI HUD and top-right stack moved below overlay for proper layering\]') {
        $markerLine = $i
        break
    }
}
Write-Host "Marker line: $($markerLine + 1)"

# Find the sections we need to move:

# Section: Close call / perfect dodge indicators
$closeCallStart = -1
$closeCallEnd = -1
for ($i = 0; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match 'Draw close call / perfect dodge indicators below combo') {
        $closeCallStart = $i - 1  # Include blank line before comment
        break
    }
}
# Find the closing brace
$braceDepth = 0
$inBlock = $false
for ($i = $closeCallStart + 1; $i -lt $rendererLines.Count; $i++) {
    $line = $rendererLines[$i].Trim()
    if ($line -match '^\s*if\s*\(comboSystem' -and !$inBlock) {
        $inBlock = $true
    }
    if ($inBlock) {
        $braceDepth += ($line.ToCharArray() | Where-Object { $_ -eq '{' }).Count
        $braceDepth -= ($line.ToCharArray() | Where-Object { $_ -eq '}' }).Count
        if ($braceDepth -eq 0 -and $line -ne '') {
            $closeCallEnd = $i
            break
        }
    }
}
Write-Host "Close call section: lines $($closeCallStart + 1) to $($closeCallEnd + 1)"

# Section: Extra lives indicator
$livesStart = -1
$livesEnd = -1
for ($i = 0; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match 'Draw extra lives indicator') {
        $livesStart = $i - 1
        break
    }
}
$braceDepth = 0
$inBlock = $false
for ($i = $livesStart + 1; $i -lt $rendererLines.Count; $i++) {
    $line = $rendererLines[$i].Trim()
    if ($line -match '^\s*if\s*\(gameData\.getExtraLives' -and !$inBlock) {
        $inBlock = $true
    }
    if ($inBlock) {
        $braceDepth += ($line.ToCharArray() | Where-Object { $_ -eq '{' }).Count
        $braceDepth -= ($line.ToCharArray() | Where-Object { $_ -eq '}' }).Count
        if ($braceDepth -eq 0 -and $line -ne '') {
            $livesEnd = $i
            break
        }
    }
}
Write-Host "Extra lives section: lines $($livesStart + 1) to $($livesEnd + 1)"

# Section: Active item UI
$itemStart = -1
$itemEnd = -1
for ($i = 0; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match '// Draw active item UI') {
        $itemStart = $i - 1
        break
    }
}
# Need to find the end - look for "Press SPACE to skip" or "Draw.*intro" comment
for ($i = $itemStart + 1; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match 'Draw "Press .* to skip"') {
        $itemEnd = $i - 2  # The line before the blank line before the next section
        break
    }
}
# Actually, let's find the closing brace of the if (equippedItem != null) block
$braceDepth = 0
$inBlock = $false
for ($i = $itemStart + 1; $i -lt $rendererLines.Count; $i++) {
    $line = $rendererLines[$i].Trim()
    if ($line -match '^\s*if\s*\(equippedItem\s*!=\s*null\)' -and !$inBlock) {
        $inBlock = $true
    }
    if ($inBlock) {
        $braceDepth += ($line.ToCharArray() | Where-Object { $_ -eq '{' }).Count
        $braceDepth -= ($line.ToCharArray() | Where-Object { $_ -eq '}' }).Count
        if ($braceDepth -eq 0 -and $line -eq '}') {
            $itemEnd = $i
            break
        }
    }
}
Write-Host "Active item section: lines $($itemStart + 1) to $($itemEnd + 1)"

# Section: Combo display (score multiplier)
$comboDisplayStart = -1
$comboDisplayEnd = -1
for ($i = 0; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match '// Draw combo display') {
        $comboDisplayStart = $i - 1
        break
    }
}
$braceDepth = 0
$inBlock = $false
for ($i = $comboDisplayStart + 1; $i -lt $rendererLines.Count; $i++) {
    $line = $rendererLines[$i].Trim()
    if ($line -match '^\s*if\s*\(comboSystem\s*!=\s*null\s*&&\s*comboSystem\.getCombo' -and !$inBlock) {
        $inBlock = $true
    }
    if ($inBlock) {
        $braceDepth += ($line.ToCharArray() | Where-Object { $_ -eq '{' }).Count
        $braceDepth -= ($line.ToCharArray() | Where-Object { $_ -eq '}' }).Count
        if ($braceDepth -eq 0 -and $line -eq '}') {
            $comboDisplayEnd = $i
            break
        }
    }
}
Write-Host "Combo display section: lines $($comboDisplayStart + 1) to $($comboDisplayEnd + 1)"

# Section: Achievement notification
$achieveStart = -1
$achieveEnd = -1
for ($i = 0; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match '// Draw achievement notification') {
        $achieveStart = $i - 1
        break
    }
}
$braceDepth = 0
$inBlock = $false
for ($i = $achieveStart + 1; $i -lt $rendererLines.Count; $i++) {
    $line = $rendererLines[$i].Trim()
    if ($line -match '^\s*if\s*\(pendingAchievements' -and !$inBlock) {
        $inBlock = $true
    }
    if ($inBlock) {
        $braceDepth += ($line.ToCharArray() | Where-Object { $_ -eq '{' }).Count
        $braceDepth -= ($line.ToCharArray() | Where-Object { $_ -eq '}' }).Count
        if ($braceDepth -eq 0 -and $line -eq '}') {
            $achieveEnd = $i
            break
        }
    }
}
Write-Host "Achievement section: lines $($achieveStart + 1) to $($achieveEnd + 1)"

# Section: Overlay image draw
$overlayLine = -1
for ($i = 0; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i] -match '// Draw overlay on top of everything') {
        $overlayLine = $i
        break
    }
}
# Find end of overlay block
$overlayEnd = -1
for ($i = $overlayLine + 1; $i -lt $rendererLines.Count; $i++) {
    if ($rendererLines[$i].Trim() -eq '}') {
        $overlayEnd = $i
        break
    }
}
Write-Host "Overlay section: lines $($overlayLine + 1) to $($overlayEnd + 1)"

# Now extract the sections (save them)
$closeCallCode = $rendererLines[$closeCallStart..$closeCallEnd]
$livesCode = $rendererLines[$livesStart..$livesEnd]
$itemCode = $rendererLines[$itemStart..$itemEnd]
$comboDisplayCode = $rendererLines[$comboDisplayStart..$comboDisplayEnd]
$achieveCode = $rendererLines[$achieveStart..$achieveEnd]

# Build the new unified UI stack code
$newUIStack = @()
$newUIStack += ""
$newUIStack += "        // =========================================="
$newUIStack += "        // TOP-RIGHT UI STACK (above overlay for visibility)"
$newUIStack += "        // Uses cumulative topRightY for proper stacking"
$newUIStack += "        // =========================================="
$newUIStack += ""
$newUIStack += "        // Draw UI with better contrast (top-left HUD)"
$newUIStack += "        g.setColor(new Color(0, 0, 0, 150));"
$newUIStack += "        g.fillRoundRect(10, 10, 280, 140, 10, 10);"
$newUIStack += ""
$newUIStack += "        g.setColor(Color.WHITE);"
$newUIStack += "        g.setFont(new Font(""Arial"", Font.BOLD, 24));"
$newUIStack += "        g.drawString(""Level: "" + level, 20, 35);"
$newUIStack += "        g.drawString(""Score: "" + (int)displayedScore, 20, 65);"
$newUIStack += "        g.drawString(""Money: `$"" + (int)displayedMoney, 20, 95);"
$newUIStack += ""
$newUIStack += "        // Display timer and FPS"
$newUIStack += "        g.setFont(new Font(""Arial"", Font.PLAIN, 18));"
$newUIStack += "        int minutes = (int)(gameTime / 60);"
$newUIStack += "        int seconds = (int)(gameTime % 60);"
$newUIStack += "        int milliseconds = (int)((gameTime % 1) * 100);"
$newUIStack += "        String timeStr = String.format(""Time: %d:%02d.%02d"", minutes, seconds, milliseconds);"
$newUIStack += "        g.drawString(timeStr, 20, 120);"
$newUIStack += "        g.drawString(""FPS: "" + fps, 20, 145);"
$newUIStack += ""
$newUIStack += "        // Top-right UI stack with cumulative Y positioning"
$newUIStack += "        int topRightY = 10;"
$newUIStack += ""
$newUIStack += "        // 1. Dodge combo counter"
$newUIStack += "        if (showCombo && dodgeCombo > 1) {"
$newUIStack += "            g.setColor(new Color(0, 0, 0, 150));"
$newUIStack += "            g.fillRoundRect(width - 210, topRightY, 200, 60, 10, 10);"
$newUIStack += ""
$newUIStack += "            AffineTransform comboTransform = g.getTransform();"
$newUIStack += "            int comboX = width - 110;"
$newUIStack += "            int comboCenterY = topRightY + 40;"
$newUIStack += "            g.translate(comboX, comboCenterY);"
$newUIStack += "            g.scale(comboPulseScale, comboPulseScale);"
$newUIStack += "            g.translate(-comboX, -comboCenterY);"
$newUIStack += ""
$newUIStack += "            g.setColor(new Color(163, 190, 140));"
$newUIStack += "            g.setFont(new Font(""Arial"", Font.BOLD, 32));"
$newUIStack += "            String dodgeComboText = ""COMBO x"" + dodgeCombo;"
$newUIStack += "            FontMetrics comboFm = g.getFontMetrics();"
$newUIStack += "            g.drawString(dodgeComboText, width - 205 + (190 - comboFm.stringWidth(dodgeComboText)) / 2, comboCenterY);"
$newUIStack += ""
$newUIStack += "            g.setTransform(comboTransform);"
$newUIStack += "            topRightY += 65;"
$newUIStack += "        }"
$newUIStack += ""
$newUIStack += "        // 2. Close call / perfect dodge indicators"
$newUIStack += "        if (comboSystem != null && (comboSystem.getCloseCallCount() > 0 || comboSystem.getPerfectDodgeCount() > 0)) {"
$newUIStack += "            g.setFont(new Font(""Arial"", Font.BOLD, 14));"
$newUIStack += "            if (comboSystem.getPerfectDodgeCount() > 0) {"
$newUIStack += "                g.setColor(new Color(255, 215, 0));"
$newUIStack += "                g.drawString(""\u2721 PERFECT x"" + comboSystem.getPerfectDodgeCount(), width - 200, topRightY + 12);"
$newUIStack += "                topRightY += 18;"
$newUIStack += "            }"
$newUIStack += "            if (comboSystem.getCloseCallCount() > 0) {"
$newUIStack += "                g.setColor(new Color(163, 190, 140));"
$newUIStack += "                g.drawString(""\u22C6 CLOSE x"" + comboSystem.getCloseCallCount(), width - 200, topRightY + 12);"
$newUIStack += "                topRightY += 18;"
$newUIStack += "            }"
$newUIStack += "        }"
$newUIStack += ""
$newUIStack += "        // 3. Active item UI"
$newUIStack += "        equippedItem = gameData.getEquippedItem();"
$newUIStack += "        if (equippedItem != null) {"
$newUIStack += "            int itemUIX = width - 210;"
$newUIStack += "            int itemUIY = topRightY;"
$newUIStack += "            int itemUIW = 200;"
$newUIStack += "            int itemUIH = 80;"

# Now we need the active item interior code... let me extract it from the original
# I'll read the interior of the item block directly
Write-Host ""
Write-Host "Extracting active item interior..."

# Find the interior lines (skip the first 5 lines which are the if/position setup)
$itemInteriorStart = -1
for ($i = $itemStart; $i -le $itemEnd; $i++) {
    if ($rendererLines[$i] -match 'Determine if any popup/flash is active') {
        $itemInteriorStart = $i
        break
    }
}
$itemInteriorEnd = $itemEnd - 1  # Before the closing brace

if ($itemInteriorStart -gt 0) {
    Write-Host "Item interior: lines $($itemInteriorStart + 1) to $($itemInteriorEnd + 1)"
    # Add the interior lines as-is
    for ($i = $itemInteriorStart; $i -le $itemInteriorEnd; $i++) {
        $newUIStack += $rendererLines[$i]
    }
}

$newUIStack += "            topRightY += 85;"
$newUIStack += "        }"
$newUIStack += ""
$newUIStack += "        // 4. Extra lives indicator"
$newUIStack += "        if (gameData.getExtraLives() > 0) {"
$newUIStack += "            int livesUIX = width - 210;"
$newUIStack += "            int livesUIY = topRightY;"
$newUIStack += ""
$newUIStack += "            g.setColor(new Color(0, 0, 0, 150));"
$newUIStack += "            g.fillRoundRect(livesUIX, livesUIY, 200, 40, 10, 10);"
$newUIStack += ""
$newUIStack += "            g.setFont(new Font(""Arial"", Font.BOLD, 20));"
$newUIStack += "            g.setColor(new Color(255, 215, 0));"
$newUIStack += "            String livesText = ""Lives: "" + gameData.getExtraLives();"
$newUIStack += "            g.drawString(livesText, livesUIX + 10, livesUIY + 27);"
$newUIStack += "            topRightY += 45;"
$newUIStack += "        }"
$newUIStack += ""
$newUIStack += "        // 5. Combo display (score multiplier)"
$newUIStack += "        if (comboSystem != null && comboSystem.getCombo() > 1 && !introPanActive) {"
$newUIStack += "            int comboDispX = width - 250;"
$newUIStack += "            int comboDispY = topRightY;"
$newUIStack += ""
$newUIStack += "            g.setColor(new Color(0, 0, 0, 180));"
$newUIStack += "            g.fillRoundRect(comboDispX, comboDispY, 200, 80, 15, 15);"
$newUIStack += ""
$newUIStack += "            g.setFont(new Font(""Arial"", Font.BOLD, 48));"
$newUIStack += "            g.setColor(new Color(235, 203, 139));"
$newUIStack += "            String comboDispText = comboSystem.getCombo() + ""x"";"
$newUIStack += "            FontMetrics fm = g.getFontMetrics();"
$newUIStack += "            g.drawString(comboDispText, comboDispX + (200 - fm.stringWidth(comboDispText)) / 2, comboDispY + 45);"
$newUIStack += ""
$newUIStack += "            g.setFont(new Font(""Arial"", Font.PLAIN, 14));"
$newUIStack += "            g.setColor(new Color(216, 222, 233));"
$newUIStack += "            String multText = String.format(""%.1fx Score"", comboSystem.getMultiplier());"
$newUIStack += "            fm = g.getFontMetrics();"
$newUIStack += "            g.drawString(multText, comboDispX + (200 - fm.stringWidth(multText)) / 2, comboDispY + 65);"
$newUIStack += ""
$newUIStack += "            float timeoutProgress = comboSystem.getTimeoutProgress();"
$newUIStack += "            g.setColor(new Color(60, 60, 60));"
$newUIStack += "            g.fillRect(comboDispX + 10, comboDispY + 72, 180, 3);"
$newUIStack += "            g.setColor(new Color(163, 190, 140));"
$newUIStack += "            g.fillRect(comboDispX + 10, comboDispY + 72, (int)(180 * timeoutProgress), 3);"
$newUIStack += "            topRightY += 85;"
$newUIStack += "        }"
$newUIStack += ""
$newUIStack += "        // 6. Achievement notification"
$newUIStack += "        if (pendingAchievements != null && !pendingAchievements.isEmpty() && achievementNotificationTimer > 0 && !isPaused) {"
$newUIStack += "            Achievement ach = pendingAchievements.get(0);"
$newUIStack += "            float achAlpha = (float)Math.max(0.0, Math.min(1.0, achievementNotificationTimer < 30 ? achievementNotificationTimer / 30.0 : 1.0));"
$newUIStack += ""
$newUIStack += "            int notifX = width - 420;"
$newUIStack += "            int notifY = topRightY;"
$newUIStack += ""
$newUIStack += "            Graphics2D g2d = (Graphics2D) g.create();"
$newUIStack += ""
$newUIStack += "            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, achAlpha));"
$newUIStack += "            g2d.setColor(new Color(46, 52, 64, 230));"
$newUIStack += "            g2d.fillRoundRect(notifX, notifY, 400, 100, 15, 15);"
$newUIStack += ""
$newUIStack += "            g2d.setFont(new Font(""Arial"", Font.BOLD, 20));"
$newUIStack += "            g2d.setColor(new Color(235, 203, 139));"
$newUIStack += "            g2d.drawString(""Achievement Unlocked!"", notifX + 20, notifY + 30);"
$newUIStack += ""
$newUIStack += "            g2d.setFont(new Font(""Arial"", Font.BOLD, 24));"
$newUIStack += "            g2d.setColor(new Color(216, 222, 233));"
$newUIStack += "            g2d.drawString(ach.getName(), notifX + 20, notifY + 60);"
$newUIStack += ""
$newUIStack += "            g2d.setFont(new Font(""Arial"", Font.PLAIN, 14));"
$newUIStack += "            g2d.drawString(ach.getDescription(), notifX + 20, notifY + 85);"
$newUIStack += ""
$newUIStack += "            g2d.dispose();"
$newUIStack += "        }"

Write-Host ""
Write-Host "New UI stack has $($newUIStack.Count) lines"

# Now we need to:
# 1. Remove the old sections (close call, lives, active item, combo display, achievement)
# 2. Insert the new unified stack after the overlay draw

# Collect ranges to remove (sorted by line number, descending so removal doesn't shift indices)
$removeRanges = @(
    @{ Start = $closeCallStart; End = $closeCallEnd; Name = "Close call" },
    @{ Start = $livesStart; End = $livesEnd; Name = "Extra lives" },
    @{ Start = $itemStart; End = $itemEnd; Name = "Active item" },
    @{ Start = $comboDisplayStart; End = $comboDisplayEnd; Name = "Combo display" },
    @{ Start = $achieveStart; End = $achieveEnd; Name = "Achievement" }
) | Sort-Object { $_.Start } -Descending

Write-Host ""
Write-Host "Sections to remove (in removal order - bottom first):"
foreach ($range in $removeRanges) {
    Write-Host "  $($range.Name): lines $($range.Start + 1) to $($range.End + 1)"
}

# Build new file
$newLines = [System.Collections.ArrayList]::new($rendererLines)

# Remove sections from bottom to top so indices stay valid
foreach ($range in $removeRanges) {
    $count = $range.End - $range.Start + 1
    $newLines.RemoveRange($range.Start, $count)
    Write-Host "Removed $($range.Name): $count lines at index $($range.Start)"
}

# Now find the overlay draw line in the modified file
$overlayInsertAfter = -1
for ($i = 0; $i -lt $newLines.Count; $i++) {
    if ($newLines[$i] -match '// Draw overlay on top of everything') {
        # Find the closing brace of this if block
        for ($j = $i + 1; $j -lt $newLines.Count; $j++) {
            if ($newLines[$j].Trim() -eq '}') {
                $overlayInsertAfter = $j
                break
            }
        }
        break
    }
}
Write-Host ""
Write-Host "Insert after overlay at new index: $($overlayInsertAfter + 1)"

# Insert the new UI stack after the overlay
$newLines.InsertRange($overlayInsertAfter + 1, $newUIStack)

Write-Host "Inserted $($newUIStack.Count) new UI stack lines"
Write-Host "New total lines: $($newLines.Count)"

# Write the file
[System.IO.File]::WriteAllLines($rendererFile, $newLines.ToArray())
Write-Host ""
Write-Host "Renderer.java updated successfully!"

Write-Host ""
Write-Host "=== PART 1 COMPLETE ==="
