$text = [System.IO.File]::ReadAllText("$PSScriptRoot\src\Renderer.java")

# Replace the remaining inst usage including double-line spacing
$old = "// Instructions drawn with icons below`r`n`r`n        fm = g.getFontMetrics();`r`n`r`n        g.drawString(inst, (width - fm.stringWidth(inst)) / 2, 145);"

$new = "drawPromptWithIcons(g, width / 2, 145, KeyBindManager.Action.MOVE_UP, `"/`", KeyBindManager.Action.MOVE_DOWN, `" to select | `", KeyBindManager.Action.MOVE_LEFT, `"/`", KeyBindManager.Action.MOVE_RIGHT, `" to adjust | `", KeyBindManager.Action.BACK, `" to return`");"

if ($text.Contains($old)) {
    $text = $text.Replace($old, $new)
    Write-Host "Replaced inst usage successfully"
} else {
    Write-Host "ERROR: Could not find old text"
}

[System.IO.File]::WriteAllText("$PSScriptRoot\src\Renderer.java", $text)
