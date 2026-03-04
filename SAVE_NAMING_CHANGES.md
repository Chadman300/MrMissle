# Save File Naming Feature — Implementation Guide

This feature lets players name their save file when creating a new save. Includes an on-screen keyboard so it works with controllers too.

---

## Overview of Changes

| File | What to do |
|------|-----------|
| `GameState.java` | Add `NAME_INPUT` enum value |
| `Game.java` | Add state variables, key handling, controller input, render dispatch, auto-save fix |
| `Renderer.java` | Add `drawNameInput()` method |

---

## 1. GameState.java — Add NAME_INPUT

Add a new enum value **after `MODE_SELECT`** (around line 37):

```java
    /** Game mode selection - choose Easy/Hard/Master when creating a new save */
    MODE_SELECT,
    
    /** Save name input - on-screen keyboard for naming a new save file */
    NAME_INPUT,
    
    /** Main menu - first screen shown on launch */
    MENU,
```

---

## 2. Game.java — All Changes

### 2a. State Variables (around line 682, after `selectedGameModeIndex`)

Add these new variables right before `public Game() {`:

```java
    // Game mode selection (shown when creating a new save)
    private int pendingSaveSlot = -1; // Slot waiting for mode selection (-1 = none)
    private int selectedGameModeIndex = 1; // 0=EASY, 1=HARD, 2=MASTER (default to HARD)
    
    // Save name input
    private StringBuilder saveNameInput = new StringBuilder("New Game");
    private int saveNameCursorPos = 0; // Cursor position in the name string
    private int saveNameCursorBlink = 0; // Blink timer for cursor
    private int onScreenKbRow = 0; // On-screen keyboard row (0-3)
    private int onScreenKbCol = 0; // On-screen keyboard column (0-9)
    private static final String[] ON_SCREEN_KB_ROWS = {
        "ABCDEFGHIJ",
        "KLMNOPQRST",
        "UVWXYZ0123",
        "456789 ←⏎"  // space, backspace (←), confirm (⏎)
    };
    private static final int MAX_SAVE_NAME_LENGTH = 20;
```

### 2b. keyTyped Handler (around line 837, in the KeyAdapter)

Add a `keyTyped` override **after the `keyReleased` method** inside the existing `addKeyListener(new KeyAdapter() { ... })`:

```java
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() < keys.length) {
                    keys[e.getKeyCode()] = false;
                }
                // ... existing keyReleased code ...
            }
            
            @Override
            public void keyTyped(KeyEvent e) {
                if (gameState == GameState.NAME_INPUT) {
                    char c = e.getKeyChar();
                    // Allow printable characters (letters, digits, spaces, basic punctuation)
                    if (c >= 32 && c < 127 && c != '\n' && c != '\r') {
                        if (saveNameInput.length() < MAX_SAVE_NAME_LENGTH) {
                            saveNameInput.insert(saveNameCursorPos, c);
                            saveNameCursorPos++;
                            saveNameCursorBlink = 0;
                        }
                    }
                }
            }
```

### 2c. NAME_INPUT Case in handleKeyPress (around line 1110, after MODE_SELECT's `break;`)

Add a new case block between `MODE_SELECT` and `MENU`:

```java
                break;
                
            case NAME_INPUT:
                if (key == KeyEvent.VK_ENTER) {
                    // Confirm the name and create the save
                    confirmSaveName();
                } else if (key == KeyEvent.VK_ESCAPE) {
                    // Go back to mode select
                    soundManager.playSound(SoundManager.Sound.UI_CANCEL);
                    screenShakeIntensity = 3;
                    transitionToState(GameState.MODE_SELECT);
                } else if (key == KeyEvent.VK_BACK_SPACE) {
                    // Delete character before cursor
                    if (saveNameCursorPos > 0) {
                        saveNameInput.deleteCharAt(saveNameCursorPos - 1);
                        saveNameCursorPos--;
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_DELETE) {
                    // Delete character at cursor
                    if (saveNameCursorPos < saveNameInput.length()) {
                        saveNameInput.deleteCharAt(saveNameCursorPos);
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_LEFT) {
                    if (saveNameCursorPos > 0) {
                        saveNameCursorPos--;
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_RIGHT) {
                    if (saveNameCursorPos < saveNameInput.length()) {
                        saveNameCursorPos++;
                        saveNameCursorBlink = 0;
                    }
                } else if (key == KeyEvent.VK_UP) {
                    // Navigate on-screen keyboard
                    onScreenKbRow = Math.max(0, onScreenKbRow - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                } else if (key == KeyEvent.VK_DOWN) {
                    onScreenKbRow = Math.min(ON_SCREEN_KB_ROWS.length - 1, onScreenKbRow + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                } else if (key == KeyEvent.VK_SPACE) {
                    // On-screen keyboard: press the selected key
                    String row = ON_SCREEN_KB_ROWS[onScreenKbRow];
                    if (onScreenKbCol < row.length()) {
                        char selectedChar = row.charAt(onScreenKbCol);
                        if (selectedChar == '⏎') {
                            confirmSaveName();
                        } else if (selectedChar == '←') {
                            // Backspace
                            if (saveNameCursorPos > 0) {
                                saveNameInput.deleteCharAt(saveNameCursorPos - 1);
                                saveNameCursorPos--;
                                saveNameCursorBlink = 0;
                            }
                        } else {
                            // Type the character
                            if (saveNameInput.length() < MAX_SAVE_NAME_LENGTH) {
                                saveNameInput.insert(saveNameCursorPos, selectedChar);
                                saveNameCursorPos++;
                                saveNameCursorBlink = 0;
                            }
                        }
                        soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    }
                }
                break;
                
            case MENU:
```

**Note:** You need to change the existing `case MENU:` — don't duplicate it. Just insert the `case NAME_INPUT:` block before it.

### 2d. confirmSaveName() Method (around line 2140, after the existing `confirmRiskContract()` method)

Add this new method:

```java
    private void confirmSaveName() {
        String name = saveNameInput.toString().trim();
        if (name.isEmpty()) {
            name = "Save " + pendingSaveSlot;
        }
        
        GameMode[] modes = GameMode.values();
        GameMode chosenMode = modes[selectedGameModeIndex];
        SaveData newSave = new SaveData();
        newSave.saveName = name;
        newSave.gameMode = chosenMode;
        if (saveManager.save(pendingSaveSlot, newSave)) {
            newSave.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
            soundManager.setMasterVolume(gameData.getMasterVolume());
            soundManager.setSfxVolume(gameData.getSfxVolume());
            soundManager.setUiVolume(gameData.getUiVolume());
            soundManager.setMusicVolume(gameData.getMusicVolume());
            soundManager.setSoundEnabled(gameData.isSoundEnabled());
            soundManager.setSpatialAudioEnabled(gameData.isSpatialAudioEnabled());
            hasSavedGame = false;
            soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
            screenShakeIntensity = 5;
            transitionToState(GameState.MENU);
        }
        pendingSaveSlot = -1;
    }
```

### 2e. MODE_SELECT → NAME_INPUT Redirect (around line 1081)

Change the MODE_SELECT ENTER/SPACE handler to go to NAME_INPUT instead of creating the save directly.

**Find this block:**
```java
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    // Create the save with the selected game mode
                    GameMode[] modes = GameMode.values();
                    GameMode chosenMode = modes[selectedGameModeIndex];
                    SaveData newSave = new SaveData();
                    newSave.saveName = "Save " + pendingSaveSlot;
                    newSave.gameMode = chosenMode;
                    if (saveManager.save(pendingSaveSlot, newSave)) {
                        newSave.loadIntoGameData(gameData, achievementManager, passiveUpgradeManager);
                        soundManager.setMasterVolume(gameData.getMasterVolume());
                        soundManager.setSfxVolume(gameData.getSfxVolume());
                        soundManager.setUiVolume(gameData.getUiVolume());
                        soundManager.setMusicVolume(gameData.getMusicVolume());
                        soundManager.setSoundEnabled(gameData.isSoundEnabled());
                        soundManager.setSpatialAudioEnabled(gameData.isSpatialAudioEnabled());
                        hasSavedGame = false;
                        soundManager.playSound(SoundManager.Sound.UI_SELECT_ALT);
                        screenShakeIntensity = 5;
                        transitionToState(GameState.MENU);
                    }
                    pendingSaveSlot = -1;
                }
```

**Replace with:**
```java
                else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                    // Go to name input screen
                    saveNameInput = new StringBuilder("Save " + pendingSaveSlot);
                    saveNameCursorPos = saveNameInput.length();
                    saveNameCursorBlink = 0;
                    onScreenKbRow = 0;
                    onScreenKbCol = 0;
                    soundManager.playSound(SoundManager.Sound.UI_SELECT);
                    screenShakeIntensity = 3;
                    transitionToState(GameState.NAME_INPUT);
                }
```

### 2f. Render Dispatch (around line 7438)

Add a `NAME_INPUT` case in the render switch, after the `MODE_SELECT` case:

```java
            case MODE_SELECT:
                renderer.drawModeSelect(g2d, WIDTH, HEIGHT, gradientTime, selectedGameModeIndex);
                break;
            case NAME_INPUT:
                renderer.drawNameInput(g2d, WIDTH, HEIGHT, gradientTime, saveNameInput.toString(), 
                    saveNameCursorPos, saveNameCursorBlink, onScreenKbRow, onScreenKbCol, ON_SCREEN_KB_ROWS);
                break;
            case MENU:
```

### 2g. Cursor Blink Timer (in the update loop)

Find where `gradientTime` is updated (should be in the main update method). Add cursor blink increment nearby. You can search for `gradientTime +=` and add this after it:

```java
        // Blink cursor for save name input
        if (gameState == GameState.NAME_INPUT) {
            saveNameCursorBlink++;
        }
```

### 2h. Controller Input for NAME_INPUT (around line 8170, after MODE_SELECT controller case)

Add a new case after the `MODE_SELECT` controller case and before `LEVEL_SELECT`:

```java
            case NAME_INPUT:
                if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_UP)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_DOWN)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_DOWN, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_LEFT)) {
                    // Move on-screen keyboard cursor left
                    onScreenKbCol = Math.max(0, onScreenKbCol - 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.MOVE_RIGHT)) {
                    // Move on-screen keyboard cursor right
                    String row = ON_SCREEN_KB_ROWS[onScreenKbRow];
                    onScreenKbCol = Math.min(row.length() - 1, onScreenKbCol + 1);
                    soundManager.playSound(SoundManager.Sound.UI_CURSOR);
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.CONFIRM)) {
                    // Press selected on-screen key
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                } else if (controllerManager.isActionJustPressed(KeyBindManager.Action.BACK)) {
                    handleKeyPress(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, ' '));
                }
                break;
```

### 2i. Auto-Save — Preserve Custom Name (line ~3702)

In `performAutoSave()`, change the hard-coded name to preserve the custom name. 

**Find:**
```java
            SaveData saveData = SaveData.fromGameData(gameData, achievementManager, 
                passiveUpgradeManager, "Save " + saveManager.getCurrentSaveSlot());
```

**Replace with:**
```java
            // Preserve custom save name if one exists, otherwise use default
            String currentSaveName = null;
            SaveManager.SaveMetadata meta = saveManager.getSaveMetadata(saveManager.getCurrentSaveSlot());
            if (meta != null && meta.saveName != null && !meta.saveName.isEmpty()) {
                currentSaveName = meta.saveName;
            } else {
                currentSaveName = "Save " + saveManager.getCurrentSaveSlot();
            }
            SaveData saveData = SaveData.fromGameData(gameData, achievementManager, 
                passiveUpgradeManager, currentSaveName);
```

**Note:** This requires `SaveManager` to have a `getSaveMetadata(int slot)` method. If it doesn't exist, you'll need to add one, or alternatively store the save name in `GameData` so it's always available:

```java
// In GameData.java - add field:
private String customSaveName = null;
public String getCustomSaveName() { return customSaveName; }
public void setCustomSaveName(String name) { this.customSaveName = name; }
```

Then in `performAutoSave()`:
```java
            String saveName = gameData.getCustomSaveName() != null ? 
                gameData.getCustomSaveName() : "Save " + saveManager.getCurrentSaveSlot();
            SaveData saveData = SaveData.fromGameData(gameData, achievementManager, 
                passiveUpgradeManager, saveName);
```

And set it when loading a save (in `loadIntoGameData` or right after calling it):
```java
gameData.setCustomSaveName(saveData.saveName);
```

---

## 3. Renderer.java — Add drawNameInput()

Add this method near `drawModeSelect()` (after it, around line 1610):

```java
    public void drawNameInput(Graphics2D g, int width, int height, double time,
                              String currentName, int cursorPos, int cursorBlink,
                              int kbRow, int kbCol, String[] kbRows) {
        // Background
        UITheme.drawScreenBackground(g, width, height, time);
        
        // Title
        UITheme.drawTitle(g, "NAME YOUR SAVE", width, UIScale.px(80), 
            ColorPalette.ACCENT_YELLOW, ColorPalette.ACCENT_ORANGE, time);
        
        // Subtitle
        g.setFont(FONT_MEDIUM);
        g.setColor(new Color(ColorPalette.TEXT_PRIMARY.getRed(), 
            ColorPalette.TEXT_PRIMARY.getGreen(), ColorPalette.TEXT_PRIMARY.getBlue(), 180));
        String subtitle = "Type a name or use the on-screen keyboard";
        FontMetrics fmSub = g.getFontMetrics();
        g.drawString(subtitle, (width - fmSub.stringWidth(subtitle)) / 2, UIScale.px(120));
        
        // --- Name input field ---
        int fieldW = UIScale.px(500);
        int fieldH = UIScale.px(50);
        int fieldX = (width - fieldW) / 2;
        int fieldY = UIScale.px(155);
        
        // Field background
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(fieldX, fieldY, fieldW, fieldH, UIScale.px(8), UIScale.px(8));
        g.setColor(ColorPalette.ACCENT_YELLOW);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(fieldX, fieldY, fieldW, fieldH, UIScale.px(8), UIScale.px(8));
        g.setStroke(new BasicStroke(1));
        
        // Name text
        g.setFont(FONT_LARGE);
        g.setColor(ColorPalette.TEXT_PRIMARY);
        FontMetrics fmName = g.getFontMetrics();
        int textX = fieldX + UIScale.px(15);
        int textY = fieldY + (fieldH + fmName.getAscent() - fmName.getDescent()) / 2;
        g.drawString(currentName, textX, textY);
        
        // Blinking cursor
        if ((cursorBlink / 30) % 2 == 0) {
            String beforeCursor = currentName.substring(0, cursorPos);
            int cursorX = textX + fmName.stringWidth(beforeCursor);
            g.setColor(ColorPalette.ACCENT_YELLOW);
            g.fillRect(cursorX, fieldY + UIScale.px(8), 2, fieldH - UIScale.px(16));
        }
        
        // Character count
        g.setFont(FONT_SMALL);
        g.setColor(new Color(180, 180, 180));
        String charCount = currentName.length() + "/20";
        FontMetrics fmSmall = g.getFontMetrics();
        g.drawString(charCount, fieldX + fieldW - fmSmall.stringWidth(charCount) - UIScale.px(10), 
            fieldY + fieldH + UIScale.px(18));
        
        // --- On-screen keyboard ---
        int kbStartY = UIScale.px(260);
        int keySize = UIScale.px(42);
        int keyGap = UIScale.px(6);
        
        for (int r = 0; r < kbRows.length; r++) {
            String row = kbRows[r];
            int rowWidth = row.length() * (keySize + keyGap) - keyGap;
            int rowX = (width - rowWidth) / 2;
            
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                int kx = rowX + c * (keySize + keyGap);
                int ky = kbStartY + r * (keySize + keyGap);
                
                boolean isHighlighted = (r == kbRow && c == kbCol);
                
                // Key background
                if (isHighlighted) {
                    g.setColor(ColorPalette.ACCENT_YELLOW);
                } else {
                    g.setColor(new Color(60, 60, 80, 200));
                }
                g.fillRoundRect(kx, ky, keySize, keySize, UIScale.px(6), UIScale.px(6));
                
                // Key border
                g.setColor(isHighlighted ? ColorPalette.ACCENT_ORANGE : new Color(100, 100, 120));
                g.drawRoundRect(kx, ky, keySize, keySize, UIScale.px(6), UIScale.px(6));
                
                // Key label
                g.setFont(FONT_MEDIUM_BOLD);
                g.setColor(isHighlighted ? Color.BLACK : ColorPalette.TEXT_PRIMARY);
                String label = String.valueOf(ch);
                if (ch == '←') label = "DEL";
                if (ch == '⏎') label = "OK";
                if (ch == ' ') label = "___";
                FontMetrics fmKey = g.getFontMetrics();
                int lx = kx + (keySize - fmKey.stringWidth(label)) / 2;
                int ly = ky + (keySize + fmKey.getAscent() - fmKey.getDescent()) / 2;
                g.drawString(label, lx, ly);
            }
        }
        
        // Controls hint
        g.setFont(FONT_SMALL);
        g.setColor(new Color(ColorPalette.TEXT_SECONDARY.getRed(), 
            ColorPalette.TEXT_SECONDARY.getGreen(), ColorPalette.TEXT_SECONDARY.getBlue(), 160));
        String hint = "[ENTER] Confirm  |  [ESC] Back  |  [BACKSPACE] Delete";
        FontMetrics fmHint = g.getFontMetrics();
        g.drawString(hint, (width - fmHint.stringWidth(hint)) / 2, height - UIScale.px(30));
    }
```

---

## Build & Test

After making all changes, build with:

```powershell
Remove-Item -Recurse -Force bin\* -ErrorAction SilentlyContinue
& "C:\Program Files\Java\jdk-24\bin\javac.exe" -d bin -cp "lib/*" (Get-ChildItem src -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
```

Then run:

```powershell
& "C:\Program Files\Java\jdk-24\bin\java.exe" -cp "bin;lib/*" App
```

### Test checklist
- [ ] Create a new save → goes to Mode Select → then Name Input screen
- [ ] Type a name with keyboard → characters appear in text field
- [ ] Backspace/Delete work correctly
- [ ] Enter confirms and creates the save with the custom name
- [ ] ESC goes back to mode select
- [ ] On-screen keyboard navigates with arrow keys (for controller)
- [ ] Space presses the highlighted on-screen key
- [ ] Controller D-pad/stick navigates the on-screen keyboard
- [ ] Controller A button types the highlighted character
- [ ] Controller B button goes back
- [ ] Auto-save preserves the custom name
- [ ] Save name appears correctly in the save selection screen
