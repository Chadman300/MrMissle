import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages gamepad/controller input via JInput (if available).
 * Gracefully degrades if JInput is not on the classpath — all methods
 * return sensible defaults so the rest of the game still works.
 * 
 * Polls the first detected GAMEPAD or STICK controller each frame.
 * Maps analog stick axes and digital buttons to ControllerButton states.
 */
public class ControllerManager {

    // ── State ────────────────────────────────────────────────────────
    private boolean jinputAvailable = false;
    private Object controller = null; // net.java.games.input.Controller
    private boolean connected = false;

    // Current and previous frame button states for edge detection
    private final Map<KeyBindManager.ControllerButton, Boolean> currentState = new HashMap<>();
    private final Map<KeyBindManager.ControllerButton, Boolean> previousState = new HashMap<>();

    // Stick deadzone
    private static final float DEADZONE = 0.35f;

    // JInput reflection handles (to avoid compile-time dependency)
    private Method controllerPoll;
    private Method controllerGetComponents;
    private Method componentGetIdentifier;
    private Method componentGetPollData;
    private Object axisIdentifier; // Component.Identifier.Axis class
    private Object xAxis, yAxis, rxAxis, ryAxis, zAxis, rzAxis, povIdentifier;
    private Map<String, Object> buttonIdentifiers = new HashMap<>();

    // Reconnect polling
    private int reconnectTimer = 0;
    private static final int RECONNECT_INTERVAL = 300; // Check every 5 seconds at 60fps

    private KeyBindManager keyBindManager;

    // ── Constructor ──────────────────────────────────────────────────
    public ControllerManager(KeyBindManager keyBindManager) {
        this.keyBindManager = keyBindManager;

        // Initialize all button states to false
        for (KeyBindManager.ControllerButton btn : KeyBindManager.ControllerButton.values()) {
            currentState.put(btn, false);
            previousState.put(btn, false);
        }

        // Try to load JInput via reflection
        try {
            initJInput();
        } catch (Exception e) {
            System.out.println("JInput not available — controller support disabled. (" + e.getMessage() + ")");
            jinputAvailable = false;
        }
    }

    // ── JInput Initialization (reflection-based) ─────────────────────
    private void initJInput() throws Exception {
        // Set the native library path so JInput can find DLLs in lib/
        String libPath = System.getProperty("user.dir") + java.io.File.separator + "lib";
        System.setProperty("net.java.games.input.librarypath", libPath);

        // Pre-load native DLLs with absolute paths so JInput's System.loadLibrary
        // calls find them already loaded (java.library.path is cached at JVM start)
        java.io.File libDir = new java.io.File(libPath);
        if (libDir.isDirectory()) {
            for (java.io.File f : libDir.listFiles()) {
                if (f.getName().endsWith(".dll") && f.getName().startsWith("jinput")) {
                    try {
                        System.load(f.getAbsolutePath());
                    } catch (UnsatisfiedLinkError ignored) {
                        // May fail for some DLLs on this platform – that's fine
                    }
                }
            }
        }

        // Load the ControllerEnvironment
        Class<?> envClass = Class.forName("net.java.games.input.ControllerEnvironment");
        Method getDefault = envClass.getMethod("getDefaultEnvironment");
        Object env = getDefault.invoke(null);

        Method getControllers = envClass.getMethod("getControllers");
        Object[] controllers = (Object[]) getControllers.invoke(env);

        // Load Controller class and Type enum
        Class<?> controllerClass = Class.forName("net.java.games.input.Controller");
        Method getType = controllerClass.getMethod("getType");
        controllerPoll = controllerClass.getMethod("poll");
        controllerGetComponents = controllerClass.getMethod("getComponents");

        // Load Component class
        Class<?> componentClass = Class.forName("net.java.games.input.Component");
        componentGetIdentifier = componentClass.getMethod("getIdentifier");
        componentGetPollData = componentClass.getMethod("getPollData");

        // Load Type constants (Controller$Type is a class with static fields, NOT an enum)
        Class<?> typeClass = Class.forName("net.java.games.input.Controller$Type");
        Object typeGamepad = typeClass.getField("GAMEPAD").get(null);
        Object typeStick = typeClass.getField("STICK").get(null);

        // Load Axis identifiers
        Class<?> axisIdClass = Class.forName("net.java.games.input.Component$Identifier$Axis");
        xAxis = axisIdClass.getField("X").get(null);
        yAxis = axisIdClass.getField("Y").get(null);
        try { rxAxis = axisIdClass.getField("RX").get(null); } catch (Exception ignored) {}
        try { ryAxis = axisIdClass.getField("RY").get(null); } catch (Exception ignored) {}
        try { zAxis = axisIdClass.getField("Z").get(null); } catch (Exception ignored) {}
        try { rzAxis = axisIdClass.getField("RZ").get(null); } catch (Exception ignored) {}
        try { povIdentifier = axisIdClass.getField("POV").get(null); } catch (Exception ignored) {}

        // Load Button identifiers
        Class<?> buttonIdClass = Class.forName("net.java.games.input.Component$Identifier$Button");
        String[] buttonNames = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "X", "Y"};
        for (String name : buttonNames) {
            try {
                buttonIdentifiers.put(name, buttonIdClass.getField(name).get(null));
            } catch (Exception ignored) {}
        }

        // Find first gamepad/stick
        for (Object ctrl : controllers) {
            Object type = getType.invoke(ctrl);
            if (type == typeGamepad || type == typeStick) {
                controller = ctrl;
                connected = true;
                jinputAvailable = true;
                System.out.println("Controller found: " + controllerClass.getMethod("getName").invoke(ctrl));
                return;
            }
        }

        // No controller found but JInput loaded successfully
        jinputAvailable = true;
        connected = false;
        System.out.println("JInput loaded — no controllers detected.");
    }

    // ── Poll (called every frame) ────────────────────────────────────
    public void poll() {
        if (!jinputAvailable) return;

        // Periodically check for new controllers
        if (!connected) {
            reconnectTimer++;
            if (reconnectTimer >= RECONNECT_INTERVAL) {
                reconnectTimer = 0;
                tryReconnect();
            }
            return;
        }

        // Copy current → previous
        for (KeyBindManager.ControllerButton btn : KeyBindManager.ControllerButton.values()) {
            previousState.put(btn, currentState.get(btn));
            currentState.put(btn, false);
        }

        try {
            // Poll the controller
            Boolean pollResult = (Boolean) controllerPoll.invoke(controller);
            if (!pollResult) {
                // Controller disconnected
                connected = false;
                controller = null;
                System.out.println("Controller disconnected.");
                if (keyBindManager.isControllerMode()) {
                    keyBindManager.onKeyboardInput(); // Fall back to keyboard
                }
                return;
            }

            Object[] components = (Object[]) controllerGetComponents.invoke(controller);
            
            float leftX = 0, leftY = 0;
            float zVal = 0, rzVal = 0;
            float pov = 0;
            Map<String, Boolean> rawButtons = new HashMap<>();

            for (Object comp : components) {
                Object id = componentGetIdentifier.invoke(comp);
                float value = (Float) componentGetPollData.invoke(comp);

                // Axes
                if (id.equals(xAxis)) leftX = value;
                else if (id.equals(yAxis)) leftY = value;
                else if (zAxis != null && id.equals(zAxis)) zVal = value;
                else if (rzAxis != null && id.equals(rzAxis)) rzVal = value;
                else if (povIdentifier != null && id.equals(povIdentifier)) pov = value;
                // Buttons (check by identifier toString)
                else {
                    String idStr = id.toString();
                    if (value > 0.5f) {
                        rawButtons.put(idStr, true);
                    }
                }
            }

            // Map left stick to directional buttons
            if (leftY < -DEADZONE) currentState.put(KeyBindManager.ControllerButton.LEFT_STICK_UP, true);
            if (leftY > DEADZONE) currentState.put(KeyBindManager.ControllerButton.LEFT_STICK_DOWN, true);
            if (leftX < -DEADZONE) currentState.put(KeyBindManager.ControllerButton.LEFT_STICK_LEFT, true);
            if (leftX > DEADZONE) currentState.put(KeyBindManager.ControllerButton.LEFT_STICK_RIGHT, true);

            // Map D-Pad (POV hat)
            // JInput POV values: 0.25=UP, 0.375=UP-RIGHT, 0.5=RIGHT, 0.625=DOWN-RIGHT,
            //   0.75=DOWN, 0.875=DOWN-LEFT, 1.0=LEFT, 0.125=UP-LEFT, 0.0=CENTER
            if (pov != 0.0f) {
                if (pov == 0.25f || pov == 0.125f || pov == 0.375f)
                    currentState.put(KeyBindManager.ControllerButton.DPAD_UP, true);
                if (pov == 0.75f || pov == 0.625f || pov == 0.875f)
                    currentState.put(KeyBindManager.ControllerButton.DPAD_DOWN, true);
                if (pov == 1.0f || pov == 0.875f || pov == 0.125f)
                    currentState.put(KeyBindManager.ControllerButton.DPAD_LEFT, true);
                if (pov == 0.5f || pov == 0.375f || pov == 0.625f)
                    currentState.put(KeyBindManager.ControllerButton.DPAD_RIGHT, true);
            }

            // Map face buttons (Xbox standard: 0=A, 1=B, 2=X, 3=Y, 4=LB, 5=RB, 6=Back, 7=Start)
            if (rawButtons.containsKey("0") || rawButtons.containsKey("A"))
                currentState.put(KeyBindManager.ControllerButton.A, true);
            if (rawButtons.containsKey("1") || rawButtons.containsKey("B"))
                currentState.put(KeyBindManager.ControllerButton.B, true);
            if (rawButtons.containsKey("2") || rawButtons.containsKey("X"))
                currentState.put(KeyBindManager.ControllerButton.X, true);
            if (rawButtons.containsKey("3") || rawButtons.containsKey("Y"))
                currentState.put(KeyBindManager.ControllerButton.Y, true);
            if (rawButtons.containsKey("4"))
                currentState.put(KeyBindManager.ControllerButton.LB, true);
            if (rawButtons.containsKey("5"))
                currentState.put(KeyBindManager.ControllerButton.RB, true);
            if (rawButtons.containsKey("6"))
                currentState.put(KeyBindManager.ControllerButton.BACK_BTN, true);
            if (rawButtons.containsKey("7"))
                currentState.put(KeyBindManager.ControllerButton.START, true);
            // Map L3/R3 (stick click buttons: 8=L3, 9=R3)
            if (rawButtons.containsKey("8"))
                currentState.put(KeyBindManager.ControllerButton.LEFT_STICK_PRESS, true);
            if (rawButtons.containsKey("9"))
                currentState.put(KeyBindManager.ControllerButton.RIGHT_STICK_PRESS, true);

            // Map analog triggers (Z axis: positive=LT, negative=RT on most Xbox controllers)
            // Some controllers use separate RZ axis for one trigger
            if (zVal > DEADZONE) currentState.put(KeyBindManager.ControllerButton.LT, true);
            if (zVal < -DEADZONE) currentState.put(KeyBindManager.ControllerButton.RT, true);
            if (rzVal > DEADZONE) currentState.put(KeyBindManager.ControllerButton.RT, true);
            if (rzVal < -DEADZONE) currentState.put(KeyBindManager.ControllerButton.LT, true);

            // Auto-detect input mode: if any button/stick is active, switch to controller
            boolean anyActive = false;
            for (Boolean v : currentState.values()) {
                if (v) { anyActive = true; break; }
            }
            if (anyActive && keyBindManager != null) {
                keyBindManager.onControllerInput();
            }

        } catch (Exception e) {
            // If polling fails, mark as disconnected
            connected = false;
            controller = null;
            System.err.println("Controller poll error: " + e.getMessage());
        }
    }

    // ── Button Queries ───────────────────────────────────────────────

    /**
     * Is the button currently held down?
     */
    public boolean isPressed(KeyBindManager.ControllerButton button) {
        Boolean val = currentState.get(button);
        return val != null && val;
    }

    /**
     * Was the button just pressed this frame (not held from previous frame)?
     */
    public boolean isJustPressed(KeyBindManager.ControllerButton button) {
        Boolean curr = currentState.get(button);
        Boolean prev = previousState.get(button);
        return (curr != null && curr) && (prev == null || !prev);
    }

    /**
     * Check if the controller button mapped to an action is currently pressed.
     */
    public boolean isActionPressed(KeyBindManager.Action action) {
        KeyBindManager.ControllerButton btn = keyBindManager.getControllerButton(action);
        return isPressed(btn);
    }

    /**
     * Check if the controller button mapped to an action was just pressed this frame.
     */
    public boolean isActionJustPressed(KeyBindManager.Action action) {
        KeyBindManager.ControllerButton btn = keyBindManager.getControllerButton(action);
        return isJustPressed(btn);
    }

    // ── Connection Status ────────────────────────────────────────────

    public boolean isConnected() { return connected; }
    public boolean isJInputAvailable() { return jinputAvailable; }

    private void tryReconnect() {
        try {
            // DefaultControllerEnvironment.getDefaultEnvironment() is a singleton
            // that caches the controller list on first call. To detect hot-plugged
            // controllers we must reset the cached singleton via reflection.
            Class<?> envClass = Class.forName("net.java.games.input.ControllerEnvironment");

            // Reset the singleton so getDefaultEnvironment() rescans hardware
            try {
                java.lang.reflect.Field defaultField = envClass.getDeclaredField("defaultEnvironment");
                defaultField.setAccessible(true);
                defaultField.set(null, null);
            } catch (Exception e) {
                // If the field name differs, try alternate names
                try {
                    java.lang.reflect.Field[] fields = envClass.getDeclaredFields();
                    for (java.lang.reflect.Field f : fields) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) &&
                            envClass.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            f.set(null, null);
                            break;
                        }
                    }
                } catch (Exception ignored2) {}
            }

            // Now re-init — getDefaultEnvironment() will create a new instance
            // and scan for controllers fresh
            Method getDefault = envClass.getMethod("getDefaultEnvironment");
            Object env = getDefault.invoke(null);
            Method getControllers = envClass.getMethod("getControllers");
            Object[] controllers = (Object[]) getControllers.invoke(env);

            Class<?> controllerClass = Class.forName("net.java.games.input.Controller");
            Method getType = controllerClass.getMethod("getType");
            Class<?> typeClass = Class.forName("net.java.games.input.Controller$Type");
            Object typeGamepad = typeClass.getField("GAMEPAD").get(null);
            Object typeStick = typeClass.getField("STICK").get(null);

            for (Object ctrl : controllers) {
                Object type = getType.invoke(ctrl);
                if (type == typeGamepad || type == typeStick) {
                    controller = ctrl;
                    connected = true;
                    // Re-cache reflection methods for the new controller
                    controllerPoll = controllerClass.getMethod("poll");
                    controllerGetComponents = controllerClass.getMethod("getComponents");
                    System.out.println("Controller reconnected: " + controllerClass.getMethod("getName").invoke(ctrl));
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Check if any button was just pressed (for generic "any key to continue" prompts).
     */
    public boolean isAnyButtonJustPressed() {
        for (KeyBindManager.ControllerButton btn : KeyBindManager.ControllerButton.values()) {
            if (isJustPressed(btn)) return true;
        }
        return false;
    }

    /**
     * Get the first controller button that was just pressed this frame.
     * Returns null if no button was just pressed. Used for rebinding capture.
     */
    public KeyBindManager.ControllerButton getFirstJustPressedButton() {
        for (KeyBindManager.ControllerButton btn : KeyBindManager.ControllerButton.values()) {
            if (isJustPressed(btn)) return btn;
        }
        return null;
    }

    /**
     * Get left stick X axis value (-1 to 1). Returns 0 if not connected.
     */
    public float getLeftStickX() {
        if (!connected) return 0;
        if (isPressed(KeyBindManager.ControllerButton.LEFT_STICK_LEFT)) return -1;
        if (isPressed(KeyBindManager.ControllerButton.LEFT_STICK_RIGHT)) return 1;
        return 0;
    }

    /**
     * Get left stick Y axis value (-1 to 1). Returns 0 if not connected.
     */
    public float getLeftStickY() {
        if (!connected) return 0;
        if (isPressed(KeyBindManager.ControllerButton.LEFT_STICK_UP)) return -1;
        if (isPressed(KeyBindManager.ControllerButton.LEFT_STICK_DOWN)) return 1;
        return 0;
    }
}
