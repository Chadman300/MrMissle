import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages customizable keybindings and controller button mappings.
 * Supports keyboard presets (WASD, Arrow Keys), a controller preset,
 * and fully custom bindings. Automatically detects input mode
 * (keyboard vs controller) based on last input received.
 */
public class KeyBindManager {

    // ── Actions ──────────────────────────────────────────────────────
    public enum Action {
        MOVE_UP("Move Up"),
        MOVE_DOWN("Move Down"),
        MOVE_LEFT("Move Left"),
        MOVE_RIGHT("Move Right"),
        USE_ITEM("Use Item"),
        PAUSE("Pause"),
        RESTART("Restart"),
        CONFIRM("Confirm"),
        BACK("Back");

        private final String displayName;
        Action(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    // ── Controller Buttons ───────────────────────────────────────────
    public enum ControllerButton {
        A("A", "Digital Buttons/ABXY/button_xbox_digital_a_1.png"),
        B("B", "Digital Buttons/ABXY/button_xbox_digital_b_1.png"),
        X("X", "Digital Buttons/ABXY/button_xbox_digital_x_1.png"),
        Y("Y", "Digital Buttons/ABXY/button_xbox_digital_y_1.png"),
        LB("LB", "Digital Buttons/Shoulder/button_xbox_digital_bumper_dark_2.png"),
        RB("RB", "Digital Buttons/Shoulder/button_xbox_digital_bumper_dark_1.png"),
        START("Start", "Digital Buttons/System/button_xbox_digital_start_1.png"),
        BACK_BTN("Back", "Digital Buttons/System/button_xbox_digital_back_1.png"),
        DPAD_UP("D-Pad Up", "D-Pad/button_xbox_dpad_dark_1.png"),
        DPAD_DOWN("D-Pad Down", "D-Pad/button_xbox_dpad_dark_3.png"),
        DPAD_LEFT("D-Pad Left", "D-Pad/button_xbox_dpad_dark_4.png"),
        DPAD_RIGHT("D-Pad Right", "D-Pad/button_xbox_dpad_dark_2.png"),
        LEFT_STICK_UP("Left Stick Up", "Analog Sticks/Left/button_xbox_analog_l_direction_3.png"),
        LEFT_STICK_DOWN("Left Stick Down", "Analog Sticks/Left/button_xbox_analog_l_direction_1.png"),
        LEFT_STICK_LEFT("Left Stick Left", "Analog Sticks/Left/button_xbox_analog_l_direction_2.png"),
        LEFT_STICK_RIGHT("Left Stick Right", "Analog Sticks/Left/button_xbox_analog_l_direction_4.png"),
        LT("LT", "Analog Triggers/button_xbox_analog_trigger_dark_1.png"),
        RT("RT", "Analog Triggers/button_xbox_analog_trigger_dark_2.png"),
        LEFT_STICK_PRESS("L3", "Digital Buttons/Analog/button_xbox_digital_analog_click_dark_1.png"),
        RIGHT_STICK_PRESS("R3", "Digital Buttons/Analog/button_xbox_digital_analog_click_dark_4.png");

        private static final String XBOX_BASE = "sprites/UI/XBOX BUTTONS - Premium Assets/";
        private final String displayName;
        private final String spritePath;

        ControllerButton(String displayName, String spritePath) {
            this.displayName = displayName;
            this.spritePath = spritePath;
        }
        public String getDisplayName() { return displayName; }
        public String getSpritePath() { return XBOX_BASE + spritePath; }
    }

    // ── Presets ───────────────────────────────────────────────────────
    public enum Preset {
        WASD("WASD"),
        ARROW_KEYS("Arrow Keys"),
        CONTROLLER("Controller"),
        CUSTOM("Custom");

        private final String displayName;
        Preset(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    // ── Input Mode ───────────────────────────────────────────────────
    public enum InputMode { KEYBOARD, CONTROLLER }

    // ── Default Keyboard Preset Maps ─────────────────────────────────
    private static final int[] WASD_DEFAULTS = {
        KeyEvent.VK_W,      // MOVE_UP
        KeyEvent.VK_S,      // MOVE_DOWN
        KeyEvent.VK_A,      // MOVE_LEFT
        KeyEvent.VK_D,      // MOVE_RIGHT
        KeyEvent.VK_SPACE,  // USE_ITEM
        KeyEvent.VK_P,      // PAUSE
        KeyEvent.VK_R,      // RESTART
        KeyEvent.VK_SPACE,  // CONFIRM (same as USE_ITEM by default)
        KeyEvent.VK_ESCAPE  // BACK
    };

    private static final int[] ARROW_DEFAULTS = {
        KeyEvent.VK_UP,     // MOVE_UP
        KeyEvent.VK_DOWN,   // MOVE_DOWN
        KeyEvent.VK_LEFT,   // MOVE_LEFT
        KeyEvent.VK_RIGHT,  // MOVE_RIGHT
        KeyEvent.VK_SPACE,  // USE_ITEM
        KeyEvent.VK_P,      // PAUSE
        KeyEvent.VK_R,      // RESTART
        KeyEvent.VK_SPACE,  // CONFIRM
        KeyEvent.VK_ESCAPE  // BACK
    };

    // ── Default Controller Mapping ───────────────────────────────────
    private static final ControllerButton[] CONTROLLER_DEFAULTS = {
        ControllerButton.LEFT_STICK_UP,    // MOVE_UP
        ControllerButton.LEFT_STICK_DOWN,  // MOVE_DOWN
        ControllerButton.LEFT_STICK_LEFT,  // MOVE_LEFT
        ControllerButton.LEFT_STICK_RIGHT, // MOVE_RIGHT
        ControllerButton.A,                // USE_ITEM
        ControllerButton.START,            // PAUSE
        ControllerButton.Y,                // RESTART
        ControllerButton.A,                // CONFIRM
        ControllerButton.B                 // BACK
    };

    // ── Reserved keys (cannot be bound) ──────────────────────────────
    private static final int[] RESERVED_KEYS = {
        KeyEvent.VK_F11,   // Fullscreen toggle
        KeyEvent.VK_TAB,   // Tab switching in settings
        KeyEvent.VK_F3,    // Debug
        KeyEvent.VK_F10    // Showcase
    };

    // ── State ────────────────────────────────────────────────────────
    private final Map<Action, Integer> keyboardBindings = new HashMap<>();
    private final Map<Action, ControllerButton> controllerBindings = new HashMap<>();
    private Preset currentPreset = Preset.WASD;
    private InputMode inputMode = InputMode.KEYBOARD;
    private Preset lastKeyboardPreset = Preset.WASD; // Remember keyboard preset when switching to controller

    // Controller button sprites (loaded from individual PNGs)
    private final Map<ControllerButton, BufferedImage> buttonSprites = new HashMap<>();
    private boolean spritesLoaded = false;

    // Keyboard key sprites (loaded from individual PNGs)
    private final Map<Integer, BufferedImage> keyboardSprites = new HashMap<>(); // VK_code -> sprite
    private boolean keyboardSpritesLoaded = false;

    // Keyboard sprite base path and VK_code -> filename mappings
    private static final String KB_BASE = "sprites/UI/SimpleKeys/Classic/Dark/Single PNGs/";
    private static final Object[][] KEYBOARD_FILE_MAP = {
        // Letters
        {KeyEvent.VK_A, "A"}, {KeyEvent.VK_B, "B"}, {KeyEvent.VK_C, "C"}, {KeyEvent.VK_D, "D"},
        {KeyEvent.VK_E, "E"}, {KeyEvent.VK_F, "F"}, {KeyEvent.VK_G, "G"}, {KeyEvent.VK_H, "H"},
        {KeyEvent.VK_I, "I"}, {KeyEvent.VK_J, "J"}, {KeyEvent.VK_K, "K"}, {KeyEvent.VK_L, "L"},
        {KeyEvent.VK_M, "M"}, {KeyEvent.VK_N, "N"}, {KeyEvent.VK_O, "O"}, {KeyEvent.VK_P, "P"},
        {KeyEvent.VK_Q, "Q"}, {KeyEvent.VK_R, "R"}, {KeyEvent.VK_S, "S"}, {KeyEvent.VK_T, "T"},
        {KeyEvent.VK_U, "U"}, {KeyEvent.VK_V, "V"}, {KeyEvent.VK_W, "W"}, {KeyEvent.VK_X, "X"},
        {KeyEvent.VK_Y, "Y"}, {KeyEvent.VK_Z, "Z"},
        // Numbers
        {KeyEvent.VK_0, "0"}, {KeyEvent.VK_1, "1"}, {KeyEvent.VK_2, "2"}, {KeyEvent.VK_3, "3"},
        {KeyEvent.VK_4, "4"}, {KeyEvent.VK_5, "5"}, {KeyEvent.VK_6, "6"}, {KeyEvent.VK_7, "7"},
        {KeyEvent.VK_8, "8"}, {KeyEvent.VK_9, "9"},
        // Arrows
        {KeyEvent.VK_UP, "ARROWUP"}, {KeyEvent.VK_DOWN, "ARROWDOWN"},
        {KeyEvent.VK_LEFT, "ARROWLEFT"}, {KeyEvent.VK_RIGHT, "ARROWRIGHT"},
        // Modifiers & special
        {KeyEvent.VK_SPACE, "SPACE"}, {KeyEvent.VK_ENTER, "ENTER"},
        {KeyEvent.VK_SHIFT, "SHIFT"}, {KeyEvent.VK_CONTROL, "CTRL"},
        {KeyEvent.VK_ALT, "ALT"}, {KeyEvent.VK_TAB, "TAB"},
        {KeyEvent.VK_BACK_SPACE, "BACKSPACE"}, {KeyEvent.VK_CAPS_LOCK, "CAPS"},
        // Punctuation
        {KeyEvent.VK_PLUS, "PLUS"}, {KeyEvent.VK_QUOTE, "QUOTE"},
        {KeyEvent.VK_BACK_SLASH, "PIPE"}, {KeyEvent.VK_PERIOD, "GREATERTHAN"},
        {KeyEvent.VK_COMMA, "LESSTHAN"}, {KeyEvent.VK_SEMICOLON, "COLON"},
        {KeyEvent.VK_SLASH, "QUESTIONMARK"}, {KeyEvent.VK_BACK_QUOTE, "TILDE"}
    };

    // ── Constructor ──────────────────────────────────────────────────
    public KeyBindManager() {
        applyPreset(Preset.WASD);
        applyControllerDefaults();
    }

    // ── Preset Management ────────────────────────────────────────────

    public void applyPreset(Preset preset) {
        if (preset == Preset.CONTROLLER) {
            currentPreset = Preset.CONTROLLER;
            inputMode = InputMode.CONTROLLER;
            return;
        }

        int[] defaults;
        switch (preset) {
            case ARROW_KEYS: defaults = ARROW_DEFAULTS; break;
            case WASD:
            default: defaults = WASD_DEFAULTS; break;
        }

        Action[] actions = Action.values();
        for (int i = 0; i < actions.length && i < defaults.length; i++) {
            keyboardBindings.put(actions[i], defaults[i]);
        }

        currentPreset = preset;
        lastKeyboardPreset = preset;
        inputMode = InputMode.KEYBOARD;
    }

    private void applyControllerDefaults() {
        Action[] actions = Action.values();
        for (int i = 0; i < actions.length && i < CONTROLLER_DEFAULTS.length; i++) {
            controllerBindings.put(actions[i], CONTROLLER_DEFAULTS[i]);
        }
    }

    public Preset getCurrentPreset() { return currentPreset; }

    public void nextPreset(boolean controllerConnected) {
        Preset[] values = getAvailablePresets(controllerConnected);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentPreset) {
                Preset next = values[(i + 1) % values.length];
                applyPreset(next);
                return;
            }
        }
        applyPreset(values[0]);
    }

    public void prevPreset(boolean controllerConnected) {
        Preset[] values = getAvailablePresets(controllerConnected);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentPreset) {
                Preset prev = values[(i - 1 + values.length) % values.length];
                applyPreset(prev);
                return;
            }
        }
        applyPreset(values[0]);
    }

    private Preset[] getAvailablePresets(boolean controllerConnected) {
        if (controllerConnected) {
            return new Preset[]{ Preset.WASD, Preset.ARROW_KEYS, Preset.CONTROLLER, Preset.CUSTOM };
        }
        return new Preset[]{ Preset.WASD, Preset.ARROW_KEYS, Preset.CUSTOM };
    }

    // ── Key Binding ──────────────────────────────────────────────────

    public int getKey(Action action) {
        Integer key = keyboardBindings.get(action);
        return key != null ? key : WASD_DEFAULTS[action.ordinal()];
    }

    public void setKey(Action action, int keyCode) {
        // Check for reserved keys
        for (int reserved : RESERVED_KEYS) {
            if (keyCode == reserved) return;
        }

        // Auto-swap: if another action has this key, give it our old key
        int oldKey = getKey(action);
        for (Map.Entry<Action, Integer> entry : keyboardBindings.entrySet()) {
            if (entry.getKey() != action && entry.getValue() == keyCode) {
                entry.setValue(oldKey);
                break;
            }
        }

        keyboardBindings.put(action, keyCode);
        detectPreset();
    }

    public boolean isAction(Action action, int keyCode) {
        return getKey(action) == keyCode;
    }

    /**
     * Check if a keyCode corresponds to any movement action.
     */
    public boolean isMovementKey(int keyCode) {
        return isAction(Action.MOVE_UP, keyCode) 
            || isAction(Action.MOVE_DOWN, keyCode) 
            || isAction(Action.MOVE_LEFT, keyCode) 
            || isAction(Action.MOVE_RIGHT, keyCode);
    }

    /**
     * After manual key changes, check if the current bindings match a known preset.
     */
    private void detectPreset() {
        if (matchesDefaults(WASD_DEFAULTS)) {
            currentPreset = Preset.WASD;
            lastKeyboardPreset = Preset.WASD;
        } else if (matchesDefaults(ARROW_DEFAULTS)) {
            currentPreset = Preset.ARROW_KEYS;
            lastKeyboardPreset = Preset.ARROW_KEYS;
        } else {
            currentPreset = Preset.CUSTOM;
            lastKeyboardPreset = Preset.CUSTOM;
        }
    }

    private boolean matchesDefaults(int[] defaults) {
        Action[] actions = Action.values();
        for (int i = 0; i < actions.length && i < defaults.length; i++) {
            if (getKey(actions[i]) != defaults[i]) return false;
        }
        return true;
    }

    public static boolean isReservedKey(int keyCode) {
        for (int reserved : RESERVED_KEYS) {
            if (keyCode == reserved) return true;
        }
        return false;
    }

    // ── Controller Binding ───────────────────────────────────────────

    public ControllerButton getControllerButton(Action action) {
        ControllerButton btn = controllerBindings.get(action);
        return btn != null ? btn : CONTROLLER_DEFAULTS[action.ordinal()];
    }

    public void setControllerButton(Action action, ControllerButton button) {
        controllerBindings.put(action, button);
    }

    // ── Input Mode ───────────────────────────────────────────────────

    public InputMode getInputMode() { return inputMode; }

    public void setInputMode(InputMode mode) {
        if (this.inputMode != mode) {
            this.inputMode = mode;
            if (mode == InputMode.CONTROLLER) {
                currentPreset = Preset.CONTROLLER;
            } else {
                currentPreset = lastKeyboardPreset;
            }
        }
    }

    public boolean isControllerMode() {
        return inputMode == InputMode.CONTROLLER;
    }

    /**
     * Called when keyboard input is detected — switch to keyboard mode.
     */
    public void onKeyboardInput() {
        if (inputMode == InputMode.CONTROLLER) {
            setInputMode(InputMode.KEYBOARD);
        }
    }

    /**
     * Called when controller input is detected — switch to controller mode.
     */
    public void onControllerInput() {
        if (inputMode == InputMode.KEYBOARD) {
            setInputMode(InputMode.CONTROLLER);
        }
    }

    // ── Display Text ─────────────────────────────────────────────────

    /**
     * Get display text for an action's current binding.
     * Returns keyboard key name or controller button name based on input mode.
     */
    public String getKeyDisplayText(Action action) {
        if (inputMode == InputMode.CONTROLLER) {
            return getControllerButton(action).getDisplayName();
        }
        return getKeyName(getKey(action));
    }

    /**
     * Get a short display name for a keyboard key code.
     */
    public static String getKeyName(int keyCode) {
        // Provide nicer names for common keys
        switch (keyCode) {
            case KeyEvent.VK_SPACE: return "SPACE";
            case KeyEvent.VK_ESCAPE: return "ESC";
            case KeyEvent.VK_ENTER: return "ENTER";
            case KeyEvent.VK_UP: return "UP";
            case KeyEvent.VK_DOWN: return "DOWN";
            case KeyEvent.VK_LEFT: return "LEFT";
            case KeyEvent.VK_RIGHT: return "RIGHT";
            case KeyEvent.VK_SHIFT: return "SHIFT";
            case KeyEvent.VK_CONTROL: return "CTRL";
            case KeyEvent.VK_ALT: return "ALT";
            case KeyEvent.VK_TAB: return "TAB";
            case KeyEvent.VK_BACK_SPACE: return "BACKSPACE";
            case KeyEvent.VK_DELETE: return "DELETE";
            default: return KeyEvent.getKeyText(keyCode).toUpperCase();
        }
    }

    /**
     * Get the text describing the movement keys (e.g., "WASD" or "Arrow Keys" or "Left Stick").
     */
    public String getMovementKeysText() {
        if (inputMode == InputMode.CONTROLLER) {
            return "Left Stick";
        }
        int up = getKey(Action.MOVE_UP);
        int down = getKey(Action.MOVE_DOWN);
        int left = getKey(Action.MOVE_LEFT);
        int right = getKey(Action.MOVE_RIGHT);

        // Check for known patterns
        if (up == KeyEvent.VK_W && left == KeyEvent.VK_A && down == KeyEvent.VK_S && right == KeyEvent.VK_D) {
            return "WASD";
        }
        if (up == KeyEvent.VK_UP && down == KeyEvent.VK_DOWN && left == KeyEvent.VK_LEFT && right == KeyEvent.VK_RIGHT) {
            return "Arrow Keys";
        }
        return getKeyName(up) + "/" + getKeyName(left) + "/" + getKeyName(down) + "/" + getKeyName(right);
    }

    // ── Controller Button Sprites ────────────────────────────────────

    /**
     * Load all controller button sprites from individual Xbox premium PNGs.
     * Called during asset loading.
     */
    public void loadControllerSprites() {
        if (spritesLoaded) return;
        for (ControllerButton btn : ControllerButton.values()) {
            try {
                BufferedImage img = AssetLoader.loadImage(btn.getSpritePath());
                if (img != null) {
                    buttonSprites.put(btn, img);
                }
            } catch (IOException e) {
                System.err.println("Could not load controller sprite for " + btn.name() + ": " + e.getMessage());
            }
        }
        spritesLoaded = true;
    }

    /**
     * Load all keyboard key sprites from individual SimpleKeys PNGs.
     * Called during asset loading.
     */
    public void loadKeyboardSprites() {
        if (keyboardSpritesLoaded) return;
        for (Object[] mapping : KEYBOARD_FILE_MAP) {
            int vkCode = (Integer) mapping[0];
            String fileName = (String) mapping[1];
            try {
                BufferedImage img = AssetLoader.loadImage(KB_BASE + fileName + ".png");
                if (img != null) {
                    keyboardSprites.put(vkCode, img);
                }
            } catch (IOException e) {
                System.err.println("Could not load keyboard sprite for " + fileName + ": " + e.getMessage());
            }
        }
        keyboardSpritesLoaded = true;
    }

    /**
     * Get the sprite image for the button/key bound to an action.
     * Returns controller button sprite in controller mode, keyboard icon in keyboard mode.
     */
    public BufferedImage getActionIcon(Action action) {
        if (inputMode == InputMode.CONTROLLER) {
            ControllerButton btn = getControllerButton(action);
            return buttonSprites.get(btn);
        } else {
            int keyCode = getKey(action);
            return keyboardSprites.get(keyCode);
        }
    }

    /**
     * Get the sprite image for a specific controller button.
     */
    public BufferedImage getButtonSprite(ControllerButton button) {
        return buttonSprites.get(button);
    }

    /**
     * Get the sprite image for a specific keyboard key code.
     */
    public BufferedImage getKeySprite(int vkCode) {
        return keyboardSprites.get(vkCode);
    }

    public boolean areSpritesLoaded() { return spritesLoaded; }
    public boolean areKeyboardSpritesLoaded() { return keyboardSpritesLoaded; }

    // ── Reset ────────────────────────────────────────────────────────

    public void resetDefaults() {
        applyPreset(Preset.WASD);
        applyControllerDefaults();
    }

    // ── Serialization helpers ────────────────────────────────────────

    /**
     * Export keyboard bindings as an int array (indexed by Action ordinal).
     */
    public int[] exportKeyBinds() {
        Action[] actions = Action.values();
        int[] result = new int[actions.length];
        for (int i = 0; i < actions.length; i++) {
            result[i] = getKey(actions[i]);
        }
        return result;
    }

    /**
     * Import keyboard bindings from an int array.
     */
    public void importKeyBinds(int[] binds) {
        if (binds == null) return;
        Action[] actions = Action.values();
        for (int i = 0; i < actions.length && i < binds.length; i++) {
            keyboardBindings.put(actions[i], binds[i]);
        }
        detectPreset();
    }

    /**
     * Export preset ordinal for saving.
     */
    public int exportPresetOrdinal() {
        return currentPreset.ordinal();
    }

    /**
     * Import preset from ordinal. If it's a known keyboard preset, apply it.
     * If CUSTOM, rely on importKeyBinds() to set the actual keys.
     */
    public void importPresetOrdinal(int ordinal) {
        Preset[] presets = Preset.values();
        if (ordinal >= 0 && ordinal < presets.length) {
            Preset p = presets[ordinal];
            if (p == Preset.CUSTOM || p == Preset.CONTROLLER) {
                // For custom/controller, just set the label — actual keys come from importKeyBinds
                currentPreset = p;
                if (p != Preset.CONTROLLER) lastKeyboardPreset = p;
            } else {
                applyPreset(p);
            }
        }
    }
}
