package config;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Stores the player's custom HUD layout configuration.
 * Positions are stored as screen percentages (0.0-1.0) for resolution independence.
 * Each element has an anchor point, opacity, visibility, style variant, and stack mode.
 */
public class HUDLayout implements Serializable {
    private static final long serialVersionUID = 1L;

    // ==========================================
    // HUD Element enum — each draggable piece
    // ==========================================
    public enum HUDElement {
        INFO_PANEL("Info Panel", "Level / Score / Money / Timer", 280, 140),
        BOSS_HEALTH("Boss Health", "Boss name and health bar", 600, 85),
        MISSILE_BAR("Missile Bar", "Lives / missile count", 50, 545),
        ACTIVE_ITEM("Active Item", "Equipped item + cooldown", 200, 80),
        COMBO_DISPLAY("Combo Display", "Score multiplier combo", 200, 80),
        DODGE_COUNTER("Dodge Counter", "Dodge combo counter", 200, 60),
        ACHIEVEMENT_POPUP("Achievements", "Achievement notifications", 400, 100),
        CLOSE_CALL_INDICATOR("Close Call", "Perfect dodge / close call", 200, 36);

        public final String displayName;
        public final String description;
        public final int defaultWidth;
        public final int defaultHeight;

        HUDElement(String displayName, String description, int defaultWidth, int defaultHeight) {
            this.displayName = displayName;
            this.description = description;
            this.defaultWidth = defaultWidth;
            this.defaultHeight = defaultHeight;
        }

        /** Whether this element is part of the top-right stack group */
        public boolean isStackGroupElement() {
            return this == ACTIVE_ITEM || this == COMBO_DISPLAY || this == DODGE_COUNTER
                || this == ACHIEVEMENT_POPUP || this == CLOSE_CALL_INDICATOR;
        }
    }

    // ==========================================
    // Anchor Point — where the element snaps to
    // ==========================================
    public enum AnchorPoint {
        TOP_LEFT("Top-Left"),
        TOP_RIGHT("Top-Right"),
        TOP_CENTER("Top-Center"),
        BOTTOM_CENTER("Bottom-Center"),
        CENTER_LEFT("Center-Left"),
        CENTER_RIGHT("Center-Right"),
        FREE("Free");

        public final String displayName;

        AnchorPoint(String displayName) {
            this.displayName = displayName;
        }
    }

    // ==========================================
    // Per-element configuration
    // ==========================================
    public static class HUDElementConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /** X position as percentage of screen width (0.0 = left edge, 1.0 = right edge) */
        public double xPercent;
        /** Y position as percentage of screen height (0.0 = top edge, 1.0 = bottom edge) */
        public double yPercent;
        /** Which edge/corner this element is anchored to */
        public AnchorPoint anchor;
        /** Opacity 0.0 (invisible) to 1.0 (fully opaque) */
        public float opacity;
        /** Whether this element is visible at all */
        public boolean visible;
        /** Style variant index (0 = default, 1 = classic/alternate) */
        public int styleVariant;
        /** For stack group elements: true = auto-stack in group, false = independent position */
        public boolean useStack;

        public HUDElementConfig() {
            this.xPercent = 0;
            this.yPercent = 0;
            this.anchor = AnchorPoint.FREE;
            this.opacity = 1.0f;
            this.visible = true;
            this.styleVariant = 0;
            this.useStack = true;
        }

        public HUDElementConfig(double xPercent, double yPercent, AnchorPoint anchor,
                                float opacity, boolean visible, int styleVariant, boolean useStack) {
            this.xPercent = xPercent;
            this.yPercent = yPercent;
            this.anchor = anchor;
            this.opacity = opacity;
            this.visible = visible;
            this.styleVariant = styleVariant;
            this.useStack = useStack;
        }

        public HUDElementConfig deepCopy() {
            return new HUDElementConfig(xPercent, yPercent, anchor, opacity, visible, styleVariant, useStack);
        }
    }

    // ==========================================
    // Layout data
    // ==========================================
    private Map<HUDElement, HUDElementConfig> elements;
    private boolean gridSnap = false; // Grid snap mode for the editor

    public HUDLayout() {
        elements = new EnumMap<>(HUDElement.class);
        // Initialize all elements with defaults
        for (HUDElement el : HUDElement.values()) {
            elements.put(el, new HUDElementConfig());
        }
    }

    public HUDElementConfig getConfig(HUDElement element) {
        HUDElementConfig cfg = elements.get(element);
        if (cfg == null) {
            cfg = new HUDElementConfig();
            elements.put(element, cfg);
        }
        return cfg;
    }

    public void setConfig(HUDElement element, HUDElementConfig config) {
        elements.put(element, config);
    }

    public boolean isGridSnap() { return gridSnap; }
    public void setGridSnap(boolean snap) { this.gridSnap = snap; }

    // ==========================================
    // Anchor presets — 2 per element
    // ==========================================

    /** Get the two anchor presets for an element (used in the editor's pill selector) */
    public static AnchorPoint[] getAnchorPresets(HUDElement element) {
        switch (element) {
            case INFO_PANEL:
                return new AnchorPoint[]{AnchorPoint.TOP_LEFT, AnchorPoint.TOP_RIGHT};
            case BOSS_HEALTH:
                return new AnchorPoint[]{AnchorPoint.BOTTOM_CENTER, AnchorPoint.TOP_CENTER};
            case MISSILE_BAR:
                return new AnchorPoint[]{AnchorPoint.CENTER_LEFT, AnchorPoint.CENTER_RIGHT};
            case ACTIVE_ITEM:
            case COMBO_DISPLAY:
            case DODGE_COUNTER:
            case ACHIEVEMENT_POPUP:
            case CLOSE_CALL_INDICATOR:
                return new AnchorPoint[]{AnchorPoint.TOP_RIGHT, AnchorPoint.TOP_LEFT};
            default:
                return new AnchorPoint[]{AnchorPoint.TOP_LEFT, AnchorPoint.TOP_RIGHT};
        }
    }

    /** Convert an anchor point to default x/y percentages for a given element */
    public static double[] anchorToPercent(AnchorPoint anchor, HUDElement element, int screenW, int screenH) {
        int w = element.defaultWidth;
        int h = element.defaultHeight;
        double margin = 10.0; // 10px margin from edges
        switch (anchor) {
            case TOP_LEFT:
                return new double[]{margin / screenW, margin / screenH};
            case TOP_RIGHT:
                return new double[]{(screenW - w - margin) / screenW, margin / screenH};
            case TOP_CENTER:
                return new double[]{(screenW - w) / 2.0 / screenW, margin / screenH};
            case BOTTOM_CENTER:
                return new double[]{(screenW - w) / 2.0 / screenW, (screenH - h - margin * 11) / screenH};
            case CENTER_LEFT:
                return new double[]{margin / screenW, (screenH - h) / 2.0 / screenH};
            case CENTER_RIGHT:
                return new double[]{(screenW - w - margin) / screenW, (screenH - h) / 2.0 / screenH};
            case FREE:
            default:
                return new double[]{margin / screenW, margin / screenH};
        }
    }

    // ==========================================
    // Default layout — mirrors current hardcoded positions
    // ==========================================

    /**
     * Creates a layout matching the current hardcoded HUD positions.
     * Uses 1920x1080 as reference since positions are stored as percentages.
     */
    public static HUDLayout defaultLayout() {
        // Use a reference resolution for computing percentages
        int refW = 1920;
        int refH = 1080;

        HUDLayout layout = new HUDLayout();

        // Info panel: top-left at (10, 10)
        HUDElementConfig info = layout.getConfig(HUDElement.INFO_PANEL);
        info.xPercent = 10.0 / refW;
        info.yPercent = 10.0 / refH;
        info.anchor = AnchorPoint.TOP_LEFT;
        info.opacity = 1.0f;
        info.visible = true;
        info.useStack = false;

        // Boss health: bottom-center at (center, height-110)
        HUDElementConfig boss = layout.getConfig(HUDElement.BOSS_HEALTH);
        boss.xPercent = (refW - 600) / 2.0 / refW;
        boss.yPercent = (refH - 110.0) / refH;
        boss.anchor = AnchorPoint.BOTTOM_CENTER;
        boss.opacity = 1.0f;
        boss.visible = true;
        boss.useStack = false;

        // Missile bar: left edge, vertically centered
        HUDElementConfig missiles = layout.getConfig(HUDElement.MISSILE_BAR);
        missiles.xPercent = 10.0 / refW;
        missiles.yPercent = (refH - 545) / 2.0 / refH;
        missiles.anchor = AnchorPoint.CENTER_LEFT;
        missiles.opacity = 1.0f;
        missiles.visible = true;
        missiles.styleVariant = 0; // 0 = vertical (default), 1 = horizontal (classic)
        missiles.useStack = false;

        // Top-right stack group elements — all useStack=true
        // Dodge counter: top-right stack start
        HUDElementConfig dodge = layout.getConfig(HUDElement.DODGE_COUNTER);
        dodge.xPercent = (refW - 210.0) / refW;
        dodge.yPercent = 10.0 / refH;
        dodge.anchor = AnchorPoint.TOP_RIGHT;
        dodge.opacity = 1.0f;
        dodge.visible = true;
        dodge.useStack = true;

        // Close call: below dodge counter in stack
        HUDElementConfig closeCall = layout.getConfig(HUDElement.CLOSE_CALL_INDICATOR);
        closeCall.xPercent = (refW - 200.0) / refW;
        closeCall.yPercent = 75.0 / refH;
        closeCall.anchor = AnchorPoint.TOP_RIGHT;
        closeCall.opacity = 1.0f;
        closeCall.visible = true;
        closeCall.useStack = true;

        // Active item: below close call in stack
        HUDElementConfig item = layout.getConfig(HUDElement.ACTIVE_ITEM);
        item.xPercent = (refW - 210.0) / refW;
        item.yPercent = 111.0 / refH;
        item.anchor = AnchorPoint.TOP_RIGHT;
        item.opacity = 1.0f;
        item.visible = true;
        item.useStack = true;

        // Combo display: below active item in stack
        HUDElementConfig combo = layout.getConfig(HUDElement.COMBO_DISPLAY);
        combo.xPercent = (refW - 250.0) / refW;
        combo.yPercent = 196.0 / refH;
        combo.anchor = AnchorPoint.TOP_RIGHT;
        combo.opacity = 1.0f;
        combo.visible = true;
        combo.useStack = true;

        // Achievement popup: below combo in stack
        HUDElementConfig ach = layout.getConfig(HUDElement.ACHIEVEMENT_POPUP);
        ach.xPercent = (refW - 420.0) / refW;
        ach.yPercent = 281.0 / refH;
        ach.anchor = AnchorPoint.TOP_RIGHT;
        ach.opacity = 1.0f;
        ach.visible = true;
        ach.useStack = true;

        return layout;
    }

    // ==========================================
    // Utility
    // ==========================================

    /** Deep copy the entire layout (for revert snapshots) */
    public HUDLayout deepCopy() {
        HUDLayout copy = new HUDLayout();
        copy.gridSnap = this.gridSnap;
        for (Map.Entry<HUDElement, HUDElementConfig> entry : elements.entrySet()) {
            copy.elements.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return copy;
    }

    /** Clamp all element positions to stay within 0.0-1.0 range (call on resolution change) */
    public void clampToScreen() {
        for (HUDElementConfig cfg : elements.values()) {
            cfg.xPercent = Math.max(0.0, Math.min(1.0, cfg.xPercent));
            cfg.yPercent = Math.max(0.0, Math.min(1.0, cfg.yPercent));
            cfg.opacity = Math.max(0.0f, Math.min(1.0f, cfg.opacity));
        }
    }

    /** Get pixel X for an element given current screen width */
    public int getPixelX(HUDElement element, int screenWidth) {
        return (int)(getConfig(element).xPercent * screenWidth);
    }

    /** Get pixel Y for an element given current screen height */
    public int getPixelY(HUDElement element, int screenHeight) {
        return (int)(getConfig(element).yPercent * screenHeight);
    }

    /** Check if the top-right stack group is in stacked mode (any stack element's useStack) */
    public boolean isStackMode() {
        // All stack elements share the same useStack value; check the first one
        for (HUDElement el : HUDElement.values()) {
            if (el.isStackGroupElement()) {
                return getConfig(el).useStack;
            }
        }
        return true;
    }

    /** Set stack mode for ALL stack group elements at once */
    public void setStackMode(boolean stacked) {
        for (HUDElement el : HUDElement.values()) {
            if (el.isStackGroupElement()) {
                getConfig(el).useStack = stacked;
            }
        }
    }

    /**
     * When switching from stacked to individual, assign each element
     * its current stacked position as its free position.
     * Call this BEFORE setting useStack=false.
     * @param screenWidth current screen width for computing positions
     * @param screenHeight current screen height for computing positions
     * @param stackPositions map of element -> current pixel Y in the stack (computed by renderer)
     */
    public void unstackToIndividual(int screenWidth, int screenHeight, Map<HUDElement, int[]> stackPositions) {
        for (Map.Entry<HUDElement, int[]> entry : stackPositions.entrySet()) {
            HUDElementConfig cfg = getConfig(entry.getKey());
            int[] pos = entry.getValue(); // [pixelX, pixelY]
            cfg.xPercent = (double) pos[0] / screenWidth;
            cfg.yPercent = (double) pos[1] / screenHeight;
            cfg.anchor = AnchorPoint.FREE;
        }
    }
}
