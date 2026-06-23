package com.example.pvpclient.gui;

import com.example.pvpclient.core.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Ein einfacher Farbwaehler mit drei Schiebereglern (R, G, B) plus
 * Vorschau. Schreibt den Wert direkt ins uebergebene ColorSetting.
 *
 * Bewusst nur mit fill() und der gleichen Maus-Logik wie im ClickGui
 * gebaut -- keine fragilen Render-APIs, also stabil ueber Versionen.
 *
 * Bedienung:
 *  - Auf einen Regler klicken oder ziehen -> Wert aendern
 *  - "Fertig" / Escape -> zurueck zum ClickGui
 */
public class ColorPickerScreen extends Screen {

    private final Screen parent;
    private final ColorSetting setting;

    // Optionaler Callback, der nach JEDER Farbaenderung feuert. Damit kann
    // z.B. die HUD-Farbe sofort live auf alle Elemente angewendet werden,
    // statt erst beim Schliessen. null = nichts tun.
    private final Runnable onChange;

    // Aktuelle Komponenten (0..255).
    private int r, g, b;

    // Layout.
    private static final int SLIDER_W = 200;
    private static final int SLIDER_H = 16;
    private int baseX, baseY;

    // Welcher Regler wird gerade gezogen? -1 = keiner.
    private int dragging = -1;

    // Letzte bekannte Mausposition (aus render gespeichert).
    private int lastMouseX, lastMouseY;

    public ColorPickerScreen(Screen parent, ColorSetting setting) {
        this(parent, setting, null);
    }

    public ColorPickerScreen(Screen parent, ColorSetting setting, Runnable onChange) {
        super(Text.literal("Farbe waehlen"));
        this.parent = parent;
        this.setting = setting;
        this.onChange = onChange;
        this.r = setting.red();
        this.g = setting.green();
        this.b = setting.blue();
    }

    /** Schreibt die aktuelle Farbe ins Setting und feuert den Callback. */
    private void pushColor() {
        setting.setComponents(255, r, g, b);
        if (onChange != null) {
            try {
                onChange.run();
            } catch (Throwable ignored) {
                // Callback darf den Picker nie crashen.
            }
        }
    }

    @Override
    protected void init() {
        this.baseX = this.width / 2 - SLIDER_W / 2;
        this.baseY = this.height / 2 - 40;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // Hintergrund abdunkeln (kein renderBackground -> kein Blur-Crash).
        ctx.fill(0, 0, this.width, this.height, 0xCC000000);

        // Falls gerade gezogen wird, Wert live aktualisieren.
        if (dragging >= 0) {
            updateFromMouse(dragging, mouseX);
        }

        // Drei Regler zeichnen.
        drawSlider(ctx, 0, "R", r, 0xFFFF5555);
        drawSlider(ctx, 1, "G", g, 0xFF55FF55);
        drawSlider(ctx, 2, "B", b, 0xFF5555FF);

        // Vorschau-Rechteck.
        int previewY = baseY + 3 * (SLIDER_H + 10) + 6;
        int argb = currentArgb();
        ctx.fill(baseX, previewY, baseX + SLIDER_W, previewY + 24, argb);
        ctx.drawTextWithShadow(this.textRenderer,
            Text.literal("Vorschau"), baseX, previewY - 10, 0xFFFFFFFF);

        // Hinweistext.
        ctx.drawTextWithShadow(this.textRenderer,
            Text.literal("Klicken/ziehen zum Aendern -- Escape = fertig"),
            baseX, previewY + 32, 0xFFB0B0B0);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawSlider(DrawContext ctx, int index, String label, int value, int knobColor) {
        int y = baseY + index * (SLIDER_H + 10);

        // Schiene.
        ctx.fill(baseX, y, baseX + SLIDER_W, y + SLIDER_H, 0xFF333333);

        // Gefuellter Teil entsprechend Wert.
        int fillW = (int) (SLIDER_W * (value / 255.0));
        ctx.fill(baseX, y, baseX + fillW, y + SLIDER_H, knobColor);

        // Label + Zahlenwert.
        ctx.drawTextWithShadow(this.textRenderer,
            Text.literal(label + ": " + value), baseX + 4, y + 4, 0xFFFFFFFF);
    }

    private void updateFromMouse(int index, int mouseX) {
        double frac = (mouseX - baseX) / (double) SLIDER_W;
        int val = (int) Math.round(Math.max(0, Math.min(1, frac)) * 255);
        switch (index) {
            case 0 -> r = val;
            case 1 -> g = val;
            case 2 -> b = val;
        }
        // Live ins Setting schreiben (mit Callback), damit man die Wirkung
        // sofort sieht -- bei HUD-Farbe wird sie direkt auf alle angewendet.
        pushColor();
    }

    private int currentArgb() {
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        int mouseX = lastMouseX;
        int mouseY = lastMouseY;

        for (int i = 0; i < 3; i++) {
            int y = baseY + i * (SLIDER_H + 10);
            if (mouseX >= baseX && mouseX <= baseX + SLIDER_W
                && mouseY >= y && mouseY <= y + SLIDER_H) {
                dragging = i;
                updateFromMouse(i, mouseX);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        dragging = -1;
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        // Endgueltigen Wert speichern (mit Callback) und zurueck zum ClickGui.
        pushColor();
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
