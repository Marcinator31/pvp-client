package com.example.pvpclient.gui;

import com.example.pvpclient.core.ConfigManager;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor: verschiebe die HUD-Elemente (FPS, CPS, Koordinaten,
 * Trank-Effekte) einfach mit der Maus, statt X/Y-Zahlen einzutippen.
 *
 * Bedienung:
 *   - Linke Maustaste auf ein Element gedrueckt halten und ziehen.
 *   - Loslassen legt die neue Position fest.
 *   - ESC schliesst den Editor und speichert automatisch.
 *
 * Es werden nur AKTIVE HUD-Elemente angezeigt (die, die auch im Spiel
 * sichtbar sind). Die Position wird live in die x/y-Settings geschrieben.
 */
public class HudEditorScreen extends Screen {

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    // Welches Element wird gerade gezogen (null = keins).
    private HudElement dragging = null;
    // Offset zwischen Mausklick und Element-Ecke, damit es nicht springt.
    private int grabOffsetX = 0;
    private int grabOffsetY = 0;

    public HudEditorScreen() {
        super(Text.literal("HUD-Editor"));
    }

    /** Alle aktiven HUD-Elemente einsammeln. */
    private List<HudElement> elements() {
        List<HudElement> list = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof HudElement he && m.isEnabled()) {
                list.add(he);
            }
        }
        return list;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // KEIN renderBackground(...) -- das loest in 1.21.11 einen Blur aus,
        // der pro Frame nur einmal erlaubt ist (sonst Crash). Stattdessen
        // direkt eine halbtransparente Abdunklung zeichnen.
        ctx.fill(0, 0, this.width, this.height, 0x80000000);

        // Hinweistext oben.
        ctx.drawTextWithShadow(this.textRenderer,
            "HUD-Editor: Elemente mit der Maus ziehen. ESC schliesst & speichert.",
            8, 8, 0xFFFFFFFF);

        // Jedes aktive HUD-Element als Kasten zeichnen.
        for (HudElement he : elements()) {
            int x = he.hudX().getInt();
            int y = he.hudY().getInt();
            int w = he.hudWidth();
            int h = he.hudHeight();

            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            boolean active = (he == dragging);

            // Rahmenfarbe: gelb wenn gezogen, hellgruen wenn drueber, sonst weiss.
            int border = active ? 0xFFFFFF55 : (hovered ? 0xFF55FF7A : 0xFFFFFFFF);
            // Fuellung leicht durchscheinend.
            int fill = active ? 0x6055FF7A : 0x40FFFFFF;

            ctx.fill(x, y, x + w, y + h, fill);
            drawBorder(ctx, x, y, w, h, border);

            // Label in den Kasten.
            ctx.drawTextWithShadow(this.textRenderer, he.hudName(), x + 2, y + 2, 0xFFFFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) { // linke Maustaste
            // Von oben nach unten pruefen: das oberste getroffene Element ziehen.
            List<HudElement> els = elements();
            for (int i = els.size() - 1; i >= 0; i--) {
                HudElement he = els.get(i);
                int x = he.hudX().getInt();
                int y = he.hudY().getInt();
                if (inside(lastMouseX, lastMouseY, x, y, he.hudWidth(), he.hudHeight())) {
                    dragging = he;
                    grabOffsetX = lastMouseX - x;
                    grabOffsetY = lastMouseY - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != null) {
            // Neue Position = aktuelle Maus minus Greif-Offset.
            int newX = lastMouseX - grabOffsetX;
            int newY = lastMouseY - grabOffsetY;

            // In den Bildschirm einpassen (nicht aus dem sichtbaren Bereich ziehen).
            newX = clamp(newX, 0, this.width - 1);
            newY = clamp(newY, 0, this.height - 1);

            dragging.hudX().set(newX);
            dragging.hudY().set(newY);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        // Editor geschlossen -> Positionen speichern.
        ConfigManager.save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // --- kleine Helfer ---

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** Zeichnet einen 1px-Rahmen um ein Rechteck. */
    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);             // oben
        ctx.fill(x, y + h - 1, x + w, y + h, color);     // unten
        ctx.fill(x, y, x + 1, y + h, color);             // links
        ctx.fill(x + w - 1, y, x + w, y + h, color);     // rechts
    }
}
