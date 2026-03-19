import config.ColorPalette;
import config.FontPalette;
import config.HUDLayout;
import config.HUDLayout.AnchorPoint;
import config.HUDLayout.HUDElement;
import config.HUDLayout.HUDElementConfig;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * HUD Layout Editor -- embedded in the Settings "HUD" tab.
 * Renders a scaled-down mock game screen with draggable HUD element placeholders.
 * Players can reposition elements, adjust transparency, toggle visibility,
 * pick anchor presets, switch style variants, and toggle stack/individual mode.
 *
 * Side panel is split into:
 *   - Global controls (always visible): Stack toggle, Grid Snap toggle, Master Opacity
 *   - Per-element controls (when element selected): Visible, Opacity, Anchor, Style
 */
public class HUDLayoutEditor {

    // ==========================================
    // State
    // ==========================================
    private HUDElement selectedElement = null;
    private HUDElement draggingElement = null;
    private boolean draggingStack = false; // true when dragging the whole stack group
    private int dragOffsetX, dragOffsetY; // offset from element top-left to mouse on drag start

    // Snap state
    private static final int SNAP_THRESHOLD = 8; // pixels in mock-screen space
    private static final int GRID_SIZE = 40;      // grid snap spacing in reference pixels
    private List<Integer> snapLinesX = new ArrayList<>(); // active vertical snap lines (ref coords)
    private List<Integer> snapLinesY = new ArrayList<>(); // active horizontal snap lines (ref coords)

    // Mock screen bounds (computed each frame)
    private int mockX, mockY, mockW, mockH;
    private double mockScale; // scale factor: mock pixels -> real screen pixels

    // Side panel bounds
    private int panelX, panelY, panelW, panelH;

    // Side panel control click targets (refreshed each render)
    private Rectangle2D[] anchorPillTargets = new Rectangle2D[3]; // 2 presets + FREE
    private Rectangle2D visibleToggleTarget = new Rectangle2D.Double();
    private Rectangle2D opacityMinusTarget  = new Rectangle2D.Double();
    private Rectangle2D opacityPlusTarget   = new Rectangle2D.Double();
    private Rectangle2D styleVariantTarget  = new Rectangle2D.Double(); // only for MISSILE_BAR
    // Global controls
    private Rectangle2D globalStackToggleTarget   = new Rectangle2D.Double();
    private Rectangle2D globalGridSnapTarget       = new Rectangle2D.Double();
    private Rectangle2D globalOpacityMinusTarget   = new Rectangle2D.Double();
    private Rectangle2D globalOpacityPlusTarget    = new Rectangle2D.Double();
    private Rectangle2D revertDefaultBtn  = new Rectangle2D.Double();
    private Rectangle2D revertChangesBtn  = new Rectangle2D.Double();

    // Reference resolution for the mock screen (16:9)
    private static final int REF_W = 1920;
    private static final int REF_H = 1080;

    // Snapshot for "Revert Changes"
    private HUDLayout snapshotLayout = null;

    // ==========================================
    // Public API
    // ==========================================

    /** Call when the HUD tab is first opened to snapshot the layout for revert */
    public void onOpen(HUDLayout layout) {
        snapshotLayout = layout.deepCopy();
        selectedElement = null;
        draggingElement = null;
        draggingStack = false;
    }

    /**
     * Render the entire HUD editor within the given content area bounds.
     */
    public void render(Graphics2D g, int areaX, int areaY, int areaW, int areaH,
                       HUDLayout layout, double time) {
        // Reset snap indicators
        snapLinesX.clear();
        snapLinesY.clear();

        // Compute layout: mock screen on left (~70%), side panel on right (~30%)
        int sidePanelWidth = Math.min(260, (int)(areaW * 0.28));
        int gap = 15;
        int mockAreaW = areaW - sidePanelWidth - gap;
        int mockAreaH = areaH - 20; // small margin

        // Fit 16:9 mock screen into the available area (letterboxed)
        double scaleX = (double) mockAreaW / REF_W;
        double scaleY = (double) mockAreaH / REF_H;
        mockScale = Math.min(scaleX, scaleY);
        mockW = (int)(REF_W * mockScale);
        mockH = (int)(REF_H * mockScale);
        mockX = areaX + (mockAreaW - mockW) / 2;
        mockY = areaY + (mockAreaH - mockH) / 2 + 10;

        // Side panel
        panelX = areaX + mockAreaW + gap;
        panelY = areaY + 10;
        panelW = sidePanelWidth;
        panelH = areaH - 20;

        // --- Draw mock screen background ---
        drawMockScreen(g, layout, time);

        // --- Draw side panel ---
        drawSidePanel(g, layout, time);
    }

    // ==========================================
    // Mock Screen Rendering
    // ==========================================

    private void drawMockScreen(Graphics2D g, HUDLayout layout, double time) {
        // Blue gradient background
        GradientPaint bg = new GradientPaint(
            mockX, mockY, new Color(15, 25, 60),
            mockX, mockY + mockH, new Color(30, 50, 100)
        );
        g.setPaint(bg);
        g.fillRoundRect(mockX, mockY, mockW, mockH, 8, 8);

        // Border
        g.setColor(new Color(80, 100, 140));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(mockX, mockY, mockW, mockH, 8, 8);
        g.setStroke(new BasicStroke(1));

        // Clip to mock screen
        Shape oldClip = g.getClip();
        g.setClip(mockX, mockY, mockW, mockH);

        // Draw grid: lines when grid snap is on, dots when off
        if (layout.isGridSnap()) {
            g.setColor(new Color(255, 255, 255, 20));
            int gridSpacing = (int)(GRID_SIZE * mockScale);
            if (gridSpacing > 3) {
                for (int gx = mockX + gridSpacing; gx < mockX + mockW; gx += gridSpacing) {
                    g.drawLine(gx, mockY, gx, mockY + mockH);
                }
                for (int gy = mockY + gridSpacing; gy < mockY + mockH; gy += gridSpacing) {
                    g.drawLine(mockX, gy, mockX + mockW, gy);
                }
            }
        } else {
            g.setColor(new Color(255, 255, 255, 25));
            int gridSpacing = (int)(40 * mockScale);
            if (gridSpacing > 3) {
                for (int gx = mockX + gridSpacing; gx < mockX + mockW; gx += gridSpacing) {
                    for (int gy = mockY + gridSpacing; gy < mockY + mockH; gy += gridSpacing) {
                        g.fillRect(gx, gy, 1, 1);
                    }
                }
            }
        }

        // Draw center crosshair lines (faint)
        g.setColor(new Color(255, 255, 255, 15));
        int centerSX = mockX + mockW / 2;
        int centerSY = mockY + mockH / 2;
        g.drawLine(centerSX, mockY, centerSX, mockY + mockH);
        g.drawLine(mockX, centerSY, mockX + mockW, centerSY);

        // Draw each HUD element as a placeholder rectangle
        boolean stackMode = layout.isStackMode();

        // Draw stack connector lines when in stack mode
        if (stackMode) {
            drawStackConnectors(g, layout);
        }

        // First draw non-stack and non-selected elements, then selected on top
        for (HUDElement el : getDrawOrder()) {
            if (el == selectedElement) continue; // draw selected last (on top)
            HUDElementConfig cfg = layout.getConfig(el);
            if (!cfg.visible && el != draggingElement) {
                drawElementPlaceholder(g, el, cfg, layout, false, true, time);
            } else {
                drawElementPlaceholder(g, el, cfg, layout, false, false, time);
            }
        }
        // Draw selected element on top
        if (selectedElement != null) {
            HUDElementConfig cfg = layout.getConfig(selectedElement);
            drawElementPlaceholder(g, selectedElement, cfg, layout, true, !cfg.visible, time);
        }

        // Draw all snap indicator lines
        if (draggingElement != null) {
            g.setColor(new Color(255, 220, 50, 180));
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{4, 4}, (float)(time * 10 % 8)));
            for (int sx : snapLinesX) {
                int screenX = mockX + (int)(sx * mockScale);
                g.drawLine(screenX, mockY, screenX, mockY + mockH);
            }
            for (int sy : snapLinesY) {
                int screenY = mockY + (int)(sy * mockScale);
                g.drawLine(mockX, screenY, mockX + mockW, screenY);
            }
            g.setStroke(new BasicStroke(1));
        }

        // Draw anchor guide-lines for selected element
        if (selectedElement != null && draggingElement == null) {
            HUDElementConfig cfg = layout.getConfig(selectedElement);
            if (cfg.anchor != AnchorPoint.FREE) {
                drawAnchorGuideLine(g, selectedElement, cfg);
            }
        }

        g.setClip(oldClip);

        // Label below mock screen
        g.setFont(FontPalette.get(Font.PLAIN, 12));
        g.setColor(ColorPalette.TEXT_DIM);
        String hint = draggingElement != null ? "Drag to reposition \u2022 Snaps to edges & other elements"
            : "Click an element to select \u2022 Drag to move";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(hint, mockX + (mockW - fm.stringWidth(hint)) / 2, mockY + mockH + 16);
    }

    private void drawElementPlaceholder(Graphics2D g, HUDElement el, HUDElementConfig cfg,
                                         HUDLayout layout, boolean isSelected, boolean isHidden, double time) {
        int[] pos = getElementMockPos(el, cfg, layout);
        int ex = pos[0];
        int ey = pos[1];
        int[] size = getElementDisplaySize(el, layout);
        int ew = (int)(size[0] * mockScale);
        int eh = (int)(size[1] * mockScale);

        // Background
        float alpha = isHidden ? 0.15f : Math.max(0.2f, cfg.opacity * 0.7f);
        Color bgColor = getElementColor(el);

        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(bgColor);
        g.fillRoundRect(ex, ey, ew, eh, 6, 6);
        g.setComposite(oldComp);

        // Border
        if (isSelected) {
            float pulse = (float)(0.6 + 0.4 * Math.sin(time * 4));
            g.setColor(new Color(80, 220, 80, (int)(255 * pulse)));
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(ex - 1, ey - 1, ew + 2, eh + 2, 6, 6);
            g.setStroke(new BasicStroke(1));
        } else if (isHidden) {
            g.setColor(new Color(255, 255, 255, 40));
            float[] dash = {3, 3};
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dash, 0));
            g.drawRoundRect(ex, ey, ew, eh, 6, 6);
            g.setStroke(new BasicStroke(1));
        } else {
            g.setColor(new Color(255, 255, 255, 60));
            g.drawRoundRect(ex, ey, ew, eh, 6, 6);
        }

        // Label text
        g.setFont(FontPalette.get(Font.BOLD, Math.max(9, (int)(12 * mockScale))));
        g.setColor(isHidden ? new Color(255, 255, 255, 60) : new Color(255, 255, 255, 200));
        FontMetrics fm = g.getFontMetrics();
        String label = el.displayName;
        if (cfg.styleVariant == 1 && el == HUDElement.MISSILE_BAR) {
            label += " (Classic)";
        }
        if (!cfg.visible) {
            label += " [Hidden]";
        }
        int textX = ex + (ew - fm.stringWidth(label)) / 2;
        int textY = ey + (eh + fm.getAscent()) / 2 - 2;
        if (fm.stringWidth(label) > ew - 4) {
            label = el.displayName;
            textX = ex + (ew - fm.stringWidth(label)) / 2;
        }
        g.drawString(label, Math.max(ex + 2, textX), textY);

        // Draw mock content hint (small)
        if (eh > 30 && ew > 60) {
            g.setFont(FontPalette.get(Font.PLAIN, Math.max(7, (int)(9 * mockScale))));
            g.setColor(new Color(255, 255, 255, isHidden ? 30 : 80));
            fm = g.getFontMetrics();
            String desc = el.description;
            textX = ex + (ew - fm.stringWidth(desc)) / 2;
            if (fm.stringWidth(desc) <= ew - 4) {
                g.drawString(desc, textX, textY + fm.getHeight());
            }
        }

        // Opacity indicator bar at bottom
        if (!isHidden && cfg.opacity < 1.0f) {
            int barW = Math.min(ew - 8, 40);
            int barH = 3;
            int barXPos = ex + (ew - barW) / 2;
            int barYPos = ey + eh - barH - 3;
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(barXPos, barYPos, barW, barH);
            g.setColor(new Color(255, 255, 255, 150));
            g.fillRect(barXPos, barYPos, (int)(barW * cfg.opacity), barH);
        }
    }

    /** Draw visual connectors between stacked elements */
    private void drawStackConnectors(Graphics2D g, HUDLayout layout) {
        HUDElement[] stackOrder = getStackOrder();

        g.setColor(new Color(100, 220, 100, 80));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10, new float[]{4, 4}, 0));

        for (int i = 0; i < stackOrder.length - 1; i++) {
            HUDElement elA = stackOrder[i];
            HUDElement elB = stackOrder[i + 1];
            HUDElementConfig cfgA = layout.getConfig(elA);
            HUDElementConfig cfgB = layout.getConfig(elB);
            int[] posA = getElementMockPos(elA, cfgA, layout);
            int[] posB = getElementMockPos(elB, cfgB, layout);
            int[] sizeA = getElementDisplaySize(elA, layout);
            int[] sizeB = getElementDisplaySize(elB, layout);
            int ewA = (int)(sizeA[0] * mockScale);
            int ehA = (int)(sizeA[1] * mockScale);
            int ewB = (int)(sizeB[0] * mockScale);

            int x1 = posA[0] + ewA / 2;
            int y1 = posA[1] + ehA;
            int x2 = posB[0] + ewB / 2;
            int y2 = posB[1];
            g.drawLine(x1, y1, x2, y2);
        }

        // "STACKED" label
        HUDElementConfig dodgeCfg = layout.getConfig(HUDElement.DODGE_COUNTER);
        int[] dodgePos = getElementMockPos(HUDElement.DODGE_COUNTER, dodgeCfg, layout);
        g.setFont(FontPalette.get(Font.BOLD, Math.max(8, (int)(10 * mockScale))));
        g.setColor(new Color(100, 220, 100, 120));
        g.setStroke(new BasicStroke(1));
        g.drawString("\u25C6 STACKED", dodgePos[0], dodgePos[1] - 4);
    }

    private void drawAnchorGuideLine(Graphics2D g, HUDElement el, HUDElementConfig cfg) {
        int[] pos = getElementMockPos(el, cfg, null);
        int ex = pos[0], ey = pos[1];
        int[] size = getElementDisplaySize(el, null);
        int ew = (int)(size[0] * mockScale);
        int eh = (int)(size[1] * mockScale);
        int cx = ex + ew / 2, cy = ey + eh / 2;

        int targetX = cx, targetY = cy;
        switch (cfg.anchor) {
            case TOP_LEFT: targetX = mockX; targetY = mockY; break;
            case TOP_RIGHT: targetX = mockX + mockW; targetY = mockY; break;
            case TOP_CENTER: targetX = mockX + mockW / 2; targetY = mockY; break;
            case BOTTOM_CENTER: targetX = mockX + mockW / 2; targetY = mockY + mockH; break;
            case CENTER_LEFT: targetX = mockX; targetY = mockY + mockH / 2; break;
            case CENTER_RIGHT: targetX = mockX + mockW; targetY = mockY + mockH / 2; break;
            default: return;
        }

        g.setColor(new Color(100, 180, 255, 100));
        float[] dash = {6, 4};
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dash, 0));
        g.drawLine(cx, cy, targetX, targetY);
        g.setColor(new Color(100, 180, 255, 180));
        g.fillOval(targetX - 4, targetY - 4, 8, 8);
        g.setStroke(new BasicStroke(1));
    }

    // ==========================================
    // Element Sizing & Positioning
    // ==========================================

    // Uniform size for stack-mode elements in the editor preview
    private static final int STACK_UNIFORM_W = 200;
    private static final int STACK_UNIFORM_H = 50;
    private static final int STACK_UNIFORM_GAP = 5;

    private int[] getElementDisplaySize(HUDElement el, HUDLayout layout) {
        if (layout != null && layout.isStackMode() && el.isStackGroupElement()) {
            return new int[]{STACK_UNIFORM_W, STACK_UNIFORM_H};
        }
        return new int[]{el.defaultWidth, el.defaultHeight};
    }

    private int[] getElementMockPos(HUDElement el, HUDElementConfig cfg, HUDLayout layout) {
        if (layout != null && layout.isStackMode() && el.isStackGroupElement()) {
            HUDElementConfig dodgeCfg = layout.getConfig(HUDElement.DODGE_COUNTER);
            int stackOriginX = (int)(dodgeCfg.xPercent * REF_W);
            int stackOriginY = (int)(dodgeCfg.yPercent * REF_H);
            int step = STACK_UNIFORM_H + STACK_UNIFORM_GAP;

            HUDElement[] stackOrder = getStackOrder();
            int topRightY = stackOriginY;
            for (HUDElement stackEl : stackOrder) {
                if (stackEl == el) {
                    int px = mockX + (int)(stackOriginX * mockScale);
                    int py = mockY + (int)(topRightY * mockScale);
                    return new int[]{px, py};
                }
                topRightY += step;
            }
        }
        int px = mockX + (int)(cfg.xPercent * REF_W * mockScale);
        int py = mockY + (int)(cfg.yPercent * REF_H * mockScale);
        return new int[]{px, py};
    }

    private Color getElementColor(HUDElement el) {
        switch (el) {
            case INFO_PANEL: return new Color(40, 80, 160);
            case BOSS_HEALTH: return new Color(160, 50, 50);
            case MISSILE_BAR: return new Color(50, 140, 50);
            case ACTIVE_ITEM: return new Color(130, 90, 30);
            case COMBO_DISPLAY: return new Color(160, 130, 30);
            case DODGE_COUNTER: return new Color(30, 130, 70);
            case ACHIEVEMENT_POPUP: return new Color(130, 100, 30);
            case CLOSE_CALL_INDICATOR: return new Color(80, 140, 80);
            default: return new Color(80, 80, 120);
        }
    }

    private HUDElement[] getDrawOrder() {
        return new HUDElement[]{
            HUDElement.INFO_PANEL,
            HUDElement.BOSS_HEALTH,
            HUDElement.MISSILE_BAR,
            HUDElement.DODGE_COUNTER,
            HUDElement.CLOSE_CALL_INDICATOR,
            HUDElement.ACTIVE_ITEM,
            HUDElement.COMBO_DISPLAY,
            HUDElement.ACHIEVEMENT_POPUP,
        };
    }

    private HUDElement[] getStackOrder() {
        return new HUDElement[]{
            HUDElement.DODGE_COUNTER,
            HUDElement.CLOSE_CALL_INDICATOR,
            HUDElement.ACTIVE_ITEM,
            HUDElement.COMBO_DISPLAY,
            HUDElement.ACHIEVEMENT_POPUP
        };
    }

    // ==========================================
    // Side Panel Rendering
    // ==========================================

    private void drawSidePanel(Graphics2D g, HUDLayout layout, double time) {
        // Panel background
        g.setColor(new Color(20, 22, 30, 200));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 10, 10);
        g.setColor(new Color(60, 65, 80));
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 10, 10);

        int px = panelX + 12;
        int py = panelY + 10;
        int contentW = panelW - 24;

        // ---- GLOBAL CONTROLS (always visible) ----
        g.setFont(FontPalette.get(Font.BOLD, 14));
        g.setColor(ColorPalette.TEXT_GOLD);
        g.drawString("Global Settings", px, py + 14);
        py += 24;

        // Stack toggle
        py = drawToggleControl(g, px, py, contentW, "Stack Group", layout.isStackMode(), globalStackToggleTarget);
        py += 6;

        // Grid snap toggle
        py = drawToggleControl(g, px, py, contentW, "Grid Snap", layout.isGridSnap(), globalGridSnapTarget);
        py += 6;

        // Master opacity
        py = drawMasterOpacity(g, px, py, contentW, layout);
        py += 8;

        // Separator
        g.setColor(new Color(80, 90, 110));
        g.drawLine(px, py, px + contentW, py);
        py += 10;

        // ---- ELEMENT CONTROLS ----
        if (selectedElement == null) {
            g.setFont(FontPalette.get(Font.BOLD, 13));
            g.setColor(new Color(160, 170, 190));
            g.drawString("Element Settings", px, py + 14);
            py += 24;

            g.setFont(FontPalette.get(Font.PLAIN, 11));
            g.setColor(ColorPalette.TEXT_DIM);
            String[] instructions = {
                "Click an element in the",
                "preview to select it.",
                "",
                "Drag to reposition.",
                "Elements snap to edges,",
                "centers, and each other."
            };
            for (String line : instructions) {
                g.drawString(line, px, py);
                py += 15;
            }
        } else {
            HUDElementConfig cfg = layout.getConfig(selectedElement);

            g.setFont(FontPalette.get(Font.BOLD, 14));
            g.setColor(ColorPalette.TEXT_GOLD);
            g.drawString(selectedElement.displayName, px, py + 14);
            py += 22;

            g.setFont(FontPalette.get(Font.PLAIN, 10));
            g.setColor(ColorPalette.TEXT_DIM);
            g.drawString(selectedElement.description, px, py);
            py += 16;

            // Separator
            g.setColor(new Color(60, 65, 80));
            g.drawLine(px, py, px + contentW, py);
            py += 10;

            // Visible
            py = drawToggleControl(g, px, py, contentW, "Visible", cfg.visible, visibleToggleTarget);
            py += 8;

            // Opacity
            py = drawOpacitySlider(g, px, py, contentW, cfg);
            py += 8;

            // Anchor
            py = drawAnchorPresets(g, px, py, contentW, cfg, selectedElement);
            py += 8;

            // Style variant (MISSILE_BAR only)
            if (selectedElement == HUDElement.MISSILE_BAR) {
                py = drawStyleVariant(g, px, py, contentW, cfg);
                py += 8;
            }

            // Position info
            g.setColor(new Color(60, 65, 80));
            g.drawLine(px, py, px + contentW, py);
            py += 10;
            g.setFont(FontPalette.get(Font.PLAIN, 10));
            g.setColor(ColorPalette.TEXT_DIM);
            g.drawString(String.format("Pos: %.1f%%, %.1f%%", cfg.xPercent * 100, cfg.yPercent * 100), px, py);
            py += 13;
            g.drawString("Anchor: " + cfg.anchor.displayName, px, py);
        }

        // ---- REVERT BUTTONS (always at bottom) ----
        int btnW = contentW;
        int btnH = 28;
        int btnY = panelY + panelH - btnH * 2 - 20;

        drawButton(g, px, btnY, btnW, btnH, "Revert Changes", new Color(100, 80, 40), revertChangesBtn);
        btnY += btnH + 8;
        drawButton(g, px, btnY, btnW, btnH, "Reset to Default", new Color(140, 50, 50), revertDefaultBtn);
    }

    private int drawMasterOpacity(Graphics2D g, int x, int y, int w, HUDLayout layout) {
        // Compute average opacity
        float total = 0;
        int count = 0;
        for (HUDElement el : HUDElement.values()) {
            total += layout.getConfig(el).opacity;
            count++;
        }
        int avgPercent = Math.round(total / count * 100);

        g.setFont(FontPalette.get(Font.BOLD, 12));
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString("All Opacity", x, y + 12);

        g.setFont(FontPalette.get(Font.PLAIN, 10));
        g.setColor(ColorPalette.TEXT_GOLD);
        String valStr = avgPercent + "%";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(valStr, x + w - fm.stringWidth(valStr), y + 12);
        y += 18;

        int btnSize = 20;

        // Minus button
        globalOpacityMinusTarget.setRect(x, y, btnSize, btnSize);
        g.setColor(new Color(60, 60, 70));
        g.fillRoundRect(x, y, btnSize, btnSize, 4, 4);
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.setFont(FontPalette.get(Font.BOLD, 14));
        g.drawString("-", x + 6, y + 15);

        // Plus button
        int plusX = x + w - btnSize;
        globalOpacityPlusTarget.setRect(plusX, y, btnSize, btnSize);
        g.setColor(new Color(60, 60, 70));
        g.fillRoundRect(plusX, y, btnSize, btnSize, 4, 4);
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString("+", plusX + 4, y + 15);

        // Bar
        int barX = x + btnSize + 4;
        int barW = w - btnSize * 2 - 8;
        int barY = y + 6;
        int barH = 8;
        g.setColor(new Color(40, 40, 50));
        g.fillRoundRect(barX, barY, barW, barH, 4, 4);
        g.setColor(new Color(80, 180, 80));
        g.fillRoundRect(barX, barY, (int)(barW * (total / count)), barH, 4, 4);

        return y + btnSize + 4;
    }

    private int drawToggleControl(Graphics2D g, int x, int y, int w, String label, boolean value, Rectangle2D target) {
        g.setFont(FontPalette.get(Font.BOLD, 12));
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString(label, x, y + 12);

        int toggleW = 40;
        int toggleH = 20;
        int toggleX = x + w - toggleW;
        int toggleY = y;
        target.setRect(toggleX, toggleY, toggleW, toggleH);

        g.setColor(value ? new Color(60, 160, 60) : new Color(80, 80, 80));
        g.fillRoundRect(toggleX, toggleY, toggleW, toggleH, toggleH, toggleH);
        int knobX = value ? toggleX + toggleW - toggleH : toggleX;
        g.setColor(Color.WHITE);
        g.fillOval(knobX + 2, toggleY + 2, toggleH - 4, toggleH - 4);

        return y + toggleH + 4;
    }

    private int drawOpacitySlider(Graphics2D g, int x, int y, int w, HUDElementConfig cfg) {
        g.setFont(FontPalette.get(Font.BOLD, 12));
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString("Opacity", x, y + 12);

        int percent = Math.round(cfg.opacity * 100);
        g.setFont(FontPalette.get(Font.PLAIN, 10));
        g.setColor(ColorPalette.TEXT_GOLD);
        String valStr = percent + "%";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(valStr, x + w - fm.stringWidth(valStr), y + 12);
        y += 18;

        int btnSize = 20;

        opacityMinusTarget.setRect(x, y, btnSize, btnSize);
        g.setColor(new Color(60, 60, 70));
        g.fillRoundRect(x, y, btnSize, btnSize, 4, 4);
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.setFont(FontPalette.get(Font.BOLD, 14));
        g.drawString("-", x + 6, y + 15);

        int plusX = x + w - btnSize;
        opacityPlusTarget.setRect(plusX, y, btnSize, btnSize);
        g.setColor(new Color(60, 60, 70));
        g.fillRoundRect(plusX, y, btnSize, btnSize, 4, 4);
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString("+", plusX + 4, y + 15);

        int barX = x + btnSize + 4;
        int barW = w - btnSize * 2 - 8;
        int barY = y + 6;
        int barH = 8;
        g.setColor(new Color(40, 40, 50));
        g.fillRoundRect(barX, barY, barW, barH, 4, 4);
        g.setColor(new Color(80, 180, 80));
        g.fillRoundRect(barX, barY, (int)(barW * cfg.opacity), barH, 4, 4);

        return y + btnSize + 4;
    }

    private int drawAnchorPresets(Graphics2D g, int x, int y, int w, HUDElementConfig cfg, HUDElement el) {
        g.setFont(FontPalette.get(Font.BOLD, 12));
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString("Anchor", x, y + 12);
        y += 18;

        AnchorPoint[] presets = HUDLayout.getAnchorPresets(el);
        String[] labels = {presets[0].displayName, presets[1].displayName, "Free"};
        AnchorPoint[] values = {presets[0], presets[1], AnchorPoint.FREE};
        int pillH = 22;
        int pillGap = 4;
        int pillW = (w - pillGap * (labels.length - 1)) / labels.length;

        for (int i = 0; i < labels.length; i++) {
            int pillX = x + i * (pillW + pillGap);
            boolean selected = cfg.anchor == values[i];
            anchorPillTargets[i] = new Rectangle2D.Double(pillX, y, pillW, pillH);

            g.setColor(selected ? new Color(60, 160, 60) : new Color(50, 50, 60));
            g.fillRoundRect(pillX, y, pillW, pillH, 6, 6);
            if (selected) {
                g.setColor(new Color(100, 220, 100));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(pillX, y, pillW, pillH, 6, 6);
                g.setStroke(new BasicStroke(1));
            }

            g.setFont(FontPalette.get(Font.PLAIN, Math.min(11, (pillW - 4) * 11 / 60)));
            g.setColor(selected ? Color.WHITE : ColorPalette.TEXT_DIM);
            FontMetrics fm = g.getFontMetrics();
            String label = labels[i];
            while (fm.stringWidth(label) > pillW - 6 && label.length() > 3) {
                label = label.substring(0, label.length() - 1);
            }
            g.drawString(label, pillX + (pillW - fm.stringWidth(label)) / 2, y + pillH - 6);
        }

        return y + pillH + 4;
    }

    private int drawStyleVariant(Graphics2D g, int x, int y, int w, HUDElementConfig cfg) {
        g.setFont(FontPalette.get(Font.BOLD, 12));
        g.setColor(ColorPalette.TEXT_PRIMARY);
        g.drawString("Style", x, y + 12);
        y += 18;

        String[] labels = {"Vertical", "Horizontal"};
        int pillH = 22;
        int pillGap = 4;
        int pillW = (w - pillGap) / 2;

        for (int i = 0; i < labels.length; i++) {
            int pillX = x + i * (pillW + pillGap);
            boolean selected = cfg.styleVariant == i;

            if (i == 0) {
                styleVariantTarget.setRect(pillX, y, pillW * 2 + pillGap, pillH);
            }

            g.setColor(selected ? new Color(60, 160, 60) : new Color(50, 50, 60));
            g.fillRoundRect(pillX, y, pillW, pillH, 6, 6);
            if (selected) {
                g.setColor(new Color(100, 220, 100));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(pillX, y, pillW, pillH, 6, 6);
                g.setStroke(new BasicStroke(1));
            }

            g.setFont(FontPalette.get(Font.PLAIN, 11));
            g.setColor(selected ? Color.WHITE : ColorPalette.TEXT_DIM);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(labels[i], pillX + (pillW - fm.stringWidth(labels[i])) / 2, y + pillH - 6);
        }

        return y + pillH + 4;
    }

    private void drawButton(Graphics2D g, int x, int y, int w, int h, String text, Color color, Rectangle2D target) {
        target.setRect(x, y, w, h);
        g.setColor(color);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(new Color(255, 255, 255, 40));
        g.drawRoundRect(x, y, w, h, 8, 8);

        g.setFont(FontPalette.get(Font.BOLD, 12));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + h / 2 + fm.getAscent() / 2 - 1);
    }

    // ==========================================
    // Mouse Handling
    // ==========================================

    public void handleMousePressed(int mx, int my, HUDLayout layout) {
        // Check side panel controls first
        if (handleSidePanelClick(mx, my, layout)) {
            return;
        }

        // Check revert buttons
        if (revertDefaultBtn.contains(mx, my)) {
            HUDLayout def = HUDLayout.defaultLayout();
            copyLayoutInto(def, layout);
            return;
        }
        if (revertChangesBtn.contains(mx, my) && snapshotLayout != null) {
            copyLayoutInto(snapshotLayout, layout);
            snapshotLayout = layout.deepCopy();
            return;
        }

        // Check if clicking on an element in the mock screen
        if (mx >= mockX && mx <= mockX + mockW && my >= mockY && my <= mockY + mockH) {
            HUDElement[] order = getDrawOrder();
            // Check selected element first (it's drawn on top)
            if (selectedElement != null) {
                if (hitTestElement(mx, my, selectedElement, layout.getConfig(selectedElement), layout)) {
                    startDrag(mx, my, selectedElement, layout);
                    return;
                }
            }
            // Then check others in reverse order
            for (int i = order.length - 1; i >= 0; i--) {
                HUDElement el = order[i];
                if (el == selectedElement) continue;
                if (hitTestElement(mx, my, el, layout.getConfig(el), layout)) {
                    selectedElement = el;
                    startDrag(mx, my, el, layout);
                    return;
                }
            }
            selectedElement = null;
        }
    }

    public void handleMouseDragged(int mx, int my, HUDLayout layout) {
        if (draggingElement == null) return;

        if (draggingStack) {
            // Move the whole stack as a group -- only update dodge counter position (the anchor)
            HUDElementConfig dodgeCfg = layout.getConfig(HUDElement.DODGE_COUNTER);

            int newMockPx = (int)((mx - mockX - dragOffsetX) / mockScale);
            int newMockPy = (int)((my - mockY - dragOffsetY) / mockScale);

            // Clamp so the entire stack stays within screen
            int stackH = (STACK_UNIFORM_H + STACK_UNIFORM_GAP) * getStackOrder().length - STACK_UNIFORM_GAP;
            newMockPx = Math.max(0, Math.min(REF_W - STACK_UNIFORM_W, newMockPx));
            newMockPy = Math.max(0, Math.min(REF_H - stackH, newMockPy));

            // Snap the stack anchor
            snapLinesX.clear();
            snapLinesY.clear();
            int[] snapped = snapToEdges(newMockPx, newMockPy, STACK_UNIFORM_W, stackH, HUDElement.DODGE_COUNTER, layout);
            newMockPx = snapped[0];
            newMockPy = snapped[1];

            dodgeCfg.xPercent = (double) newMockPx / REF_W;
            dodgeCfg.yPercent = (double) newMockPy / REF_H;
            dodgeCfg.anchor = AnchorPoint.FREE;
        } else {
            // Individual element drag
            HUDElementConfig cfg = layout.getConfig(draggingElement);

            int newMockPx = (int)((mx - mockX - dragOffsetX) / mockScale);
            int newMockPy = (int)((my - mockY - dragOffsetY) / mockScale);

            int elW = draggingElement.defaultWidth;
            int elH = draggingElement.defaultHeight;
            newMockPx = Math.max(0, Math.min(REF_W - elW, newMockPx));
            newMockPy = Math.max(0, Math.min(REF_H - elH, newMockPy));

            snapLinesX.clear();
            snapLinesY.clear();
            int[] snapped = snapToEdges(newMockPx, newMockPy, elW, elH, draggingElement, layout);
            newMockPx = snapped[0];
            newMockPy = snapped[1];

            cfg.xPercent = (double) newMockPx / REF_W;
            cfg.yPercent = (double) newMockPy / REF_H;
            cfg.anchor = AnchorPoint.FREE;
        }
    }

    public void handleMouseReleased(HUDLayout layout) {
        draggingElement = null;
        draggingStack = false;
    }

    public void handleMouseMoved(int mx, int my, HUDLayout layout) {
        // Hover effects could go here (future)
    }

    // ==========================================
    // Snap Logic (Enhanced)
    // ==========================================

    /**
     * Enhanced snap: screen edges, center, quarter marks, other element edges
     * and centers, plus optional grid snap. Populates snapLinesX/Y for visual feedback.
     */
    private int[] snapToEdges(int px, int py, int w, int h, HUDElement dragging, HUDLayout layout) {
        int bestX = px, bestY = py;
        int bestDx = SNAP_THRESHOLD + 1, bestDy = SNAP_THRESHOLD + 1;

        boolean gridSnap = layout.isGridSnap();
        int cx = px + w / 2;
        int cy = py + h / 2;
        int d;

        // --- Screen edge snapping ---
        d = Math.abs(px);
        if (d < SNAP_THRESHOLD && d < bestDx) { bestX = 0; bestDx = d; }
        d = Math.abs(px + w - REF_W);
        if (d < SNAP_THRESHOLD && d < bestDx) { bestX = REF_W - w; bestDx = d; }
        d = Math.abs(py);
        if (d < SNAP_THRESHOLD && d < bestDy) { bestY = 0; bestDy = d; }
        d = Math.abs(py + h - REF_H);
        if (d < SNAP_THRESHOLD && d < bestDy) { bestY = REF_H - h; bestDy = d; }

        // --- Screen center ---
        d = Math.abs(cx - REF_W / 2);
        if (d < SNAP_THRESHOLD && d < bestDx) { bestX = REF_W / 2 - w / 2; bestDx = d; }
        d = Math.abs(cy - REF_H / 2);
        if (d < SNAP_THRESHOLD && d < bestDy) { bestY = REF_H / 2 - h / 2; bestDy = d; }

        // --- Screen quarter marks (25%, 75%) ---
        int[] quartersX = {REF_W / 4, REF_W * 3 / 4};
        int[] quartersY = {REF_H / 4, REF_H * 3 / 4};
        for (int qx : quartersX) {
            d = Math.abs(px - qx);
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = qx; bestDx = d; }
            d = Math.abs(cx - qx);
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = qx - w / 2; bestDx = d; }
            d = Math.abs((px + w) - qx);
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = qx - w; bestDx = d; }
        }
        for (int qy : quartersY) {
            d = Math.abs(py - qy);
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = qy; bestDy = d; }
            d = Math.abs(cy - qy);
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = qy - h / 2; bestDy = d; }
            d = Math.abs((py + h) - qy);
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = qy - h; bestDy = d; }
        }

        // --- Snap to other element edges and centers ---
        for (HUDElement other : HUDElement.values()) {
            if (other == dragging) continue;
            if (draggingStack && other.isStackGroupElement()) continue;
            HUDElementConfig otherCfg = layout.getConfig(other);
            if (!otherCfg.visible) continue;
            int ox = (int)(otherCfg.xPercent * REF_W);
            int oy = (int)(otherCfg.yPercent * REF_H);
            int ow = other.defaultWidth;
            int oh = other.defaultHeight;
            int oCenterX = ox + ow / 2;
            int oCenterY = oy + oh / 2;

            // Edge-to-edge X
            d = Math.abs(px - ox);
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = ox; bestDx = d; }
            d = Math.abs(px - (ox + ow));
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = ox + ow; bestDx = d; }
            d = Math.abs((px + w) - ox);
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = ox - w; bestDx = d; }
            d = Math.abs((px + w) - (ox + ow));
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = ox + ow - w; bestDx = d; }

            // Center-to-center X
            d = Math.abs(cx - oCenterX);
            if (d < SNAP_THRESHOLD && d < bestDx) { bestX = oCenterX - w / 2; bestDx = d; }

            // Edge-to-edge Y
            d = Math.abs(py - oy);
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = oy; bestDy = d; }
            d = Math.abs(py - (oy + oh));
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = oy + oh; bestDy = d; }
            d = Math.abs((py + h) - oy);
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = oy - h; bestDy = d; }
            d = Math.abs((py + h) - (oy + oh));
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = oy + oh - h; bestDy = d; }

            // Center-to-center Y
            d = Math.abs(cy - oCenterY);
            if (d < SNAP_THRESHOLD && d < bestDy) { bestY = oCenterY - h / 2; bestDy = d; }
        }

        // --- Grid snap (overrides if enabled and closer) ---
        if (gridSnap) {
            int gridX = Math.round((float) px / GRID_SIZE) * GRID_SIZE;
            int gridY = Math.round((float) py / GRID_SIZE) * GRID_SIZE;
            d = Math.abs(px - gridX);
            if (d < SNAP_THRESHOLD + 4) { bestX = gridX; bestDx = 0; }
            d = Math.abs(py - gridY);
            if (d < SNAP_THRESHOLD + 4) { bestY = gridY; bestDy = 0; }
        }

        if (bestDx > SNAP_THRESHOLD && !gridSnap) bestX = px;
        if (bestDy > SNAP_THRESHOLD && !gridSnap) bestY = py;

        // --- Build snap lines for visual feedback ---
        int finalCx = bestX + w / 2;
        int finalCy = bestY + h / 2;

        // Screen edges
        if (bestX == 0) snapLinesX.add(0);
        if (bestX + w == REF_W) snapLinesX.add(REF_W);
        if (bestY == 0) snapLinesY.add(0);
        if (bestY + h == REF_H) snapLinesY.add(REF_H);

        // Screen center
        if (finalCx == REF_W / 2) snapLinesX.add(REF_W / 2);
        if (finalCy == REF_H / 2) snapLinesY.add(REF_H / 2);

        // Quarter lines
        for (int qx : quartersX) {
            if (bestX == qx || finalCx == qx || bestX + w == qx) snapLinesX.add(qx);
        }
        for (int qy : quartersY) {
            if (bestY == qy || finalCy == qy || bestY + h == qy) snapLinesY.add(qy);
        }

        // Element alignment lines
        for (HUDElement other : HUDElement.values()) {
            if (other == dragging) continue;
            if (draggingStack && other.isStackGroupElement()) continue;
            HUDElementConfig otherCfg = layout.getConfig(other);
            if (!otherCfg.visible) continue;
            int ox = (int)(otherCfg.xPercent * REF_W);
            int oy = (int)(otherCfg.yPercent * REF_H);
            int ow = other.defaultWidth;
            int oh = other.defaultHeight;
            int oCenterX = ox + ow / 2;
            int oCenterY = oy + oh / 2;

            if (bestX == ox || bestX + w == ox) snapLinesX.add(ox);
            if (bestX == ox + ow || bestX + w == ox + ow) snapLinesX.add(ox + ow);
            if (finalCx == oCenterX) snapLinesX.add(oCenterX);

            if (bestY == oy || bestY + h == oy) snapLinesY.add(oy);
            if (bestY == oy + oh || bestY + h == oy + oh) snapLinesY.add(oy + oh);
            if (finalCy == oCenterY) snapLinesY.add(oCenterY);
        }

        return new int[]{bestX, bestY};
    }

    // ==========================================
    // Side Panel Click Handling
    // ==========================================

    private boolean handleSidePanelClick(int mx, int my, HUDLayout layout) {
        // --- Global controls (always active) ---
        if (globalStackToggleTarget.contains(mx, my)) {
            layout.setStackMode(!layout.isStackMode());
            return true;
        }
        if (globalGridSnapTarget.contains(mx, my)) {
            layout.setGridSnap(!layout.isGridSnap());
            return true;
        }
        if (globalOpacityMinusTarget.contains(mx, my)) {
            for (HUDElement el : HUDElement.values()) {
                HUDElementConfig cfg = layout.getConfig(el);
                cfg.opacity = Math.max(0.0f, cfg.opacity - 0.1f);
            }
            return true;
        }
        if (globalOpacityPlusTarget.contains(mx, my)) {
            for (HUDElement el : HUDElement.values()) {
                HUDElementConfig cfg = layout.getConfig(el);
                cfg.opacity = Math.min(1.0f, cfg.opacity + 0.1f);
            }
            return true;
        }

        // --- Per-element controls (only when element selected) ---
        if (selectedElement == null) return false;
        HUDElementConfig cfg = layout.getConfig(selectedElement);

        if (visibleToggleTarget.contains(mx, my)) {
            cfg.visible = !cfg.visible;
            return true;
        }
        if (opacityMinusTarget.contains(mx, my)) {
            cfg.opacity = Math.max(0.0f, cfg.opacity - 0.1f);
            return true;
        }
        if (opacityPlusTarget.contains(mx, my)) {
            cfg.opacity = Math.min(1.0f, cfg.opacity + 0.1f);
            return true;
        }

        // Anchor pills
        AnchorPoint[] presets = HUDLayout.getAnchorPresets(selectedElement);
        AnchorPoint[] anchorValues = {presets[0], presets[1], AnchorPoint.FREE};
        for (int i = 0; i < anchorPillTargets.length; i++) {
            if (anchorPillTargets[i] != null && anchorPillTargets[i].contains(mx, my)) {
                cfg.anchor = anchorValues[i];
                if (anchorValues[i] != AnchorPoint.FREE) {
                    double[] pos = HUDLayout.anchorToPercent(anchorValues[i], selectedElement, REF_W, REF_H);
                    cfg.xPercent = pos[0];
                    cfg.yPercent = pos[1];
                }
                return true;
            }
        }

        // Style variant (MISSILE_BAR only)
        if (selectedElement == HUDElement.MISSILE_BAR && styleVariantTarget.contains(mx, my)) {
            double midX = styleVariantTarget.getX() + styleVariantTarget.getWidth() / 2;
            cfg.styleVariant = mx < midX ? 0 : 1;
            return true;
        }

        return false;
    }

    // ==========================================
    // Helpers
    // ==========================================

    private boolean hitTestElement(int mx, int my, HUDElement el, HUDElementConfig cfg, HUDLayout layout) {
        int[] pos = getElementMockPos(el, cfg, layout);
        int ex = pos[0], ey = pos[1];
        int[] size = getElementDisplaySize(el, layout);
        int ew = (int)(size[0] * mockScale);
        int eh = (int)(size[1] * mockScale);
        return mx >= ex && mx <= ex + ew && my >= ey && my <= ey + eh;
    }

    private void startDrag(int mx, int my, HUDElement el, HUDLayout layout) {
        draggingElement = el;
        draggingStack = false;

        if (layout.isStackMode() && el.isStackGroupElement()) {
            // Drag the whole stack as a group -- offset relative to dodge counter (anchor)
            draggingStack = true;
            HUDElementConfig dodgeCfg = layout.getConfig(HUDElement.DODGE_COUNTER);
            int anchorScreenX = mockX + (int)(dodgeCfg.xPercent * REF_W * mockScale);
            int anchorScreenY = mockY + (int)(dodgeCfg.yPercent * REF_H * mockScale);
            dragOffsetX = mx - anchorScreenX;
            dragOffsetY = my - anchorScreenY;
        } else {
            int[] pos = getElementMockPos(el, layout.getConfig(el), layout);
            dragOffsetX = mx - pos[0];
            dragOffsetY = my - pos[1];
        }
    }

    /** Copy all elements from src into dst (keeping dst reference) */
    private void copyLayoutInto(HUDLayout src, HUDLayout dst) {
        for (HUDElement el : HUDElement.values()) {
            HUDElementConfig srcCfg = src.getConfig(el);
            dst.setConfig(el, srcCfg.deepCopy());
        }
        dst.setStackMode(src.isStackMode());
        dst.setGridSnap(src.isGridSnap());
    }

    public HUDElement getSelectedElement() {
        return selectedElement;
    }

    public boolean isDragging() {
        return draggingElement != null;
    }
}
