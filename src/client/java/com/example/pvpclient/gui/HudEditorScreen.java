package com.example.pvpclient.gui;

import com.example.pvpclient.core.ConfigManager;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.ArmorHudModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor: verschiebe die HUD-Elemente (FPS, CPS, Koordinaten,
 * Trank-Effekte) UND die einzelnen ArmorHUD-Teile (Helm, Brust, Hose,
 * Schuhe, Waffe) einfach mit der Maus, statt X/Y-Zahlen einzutippen.
 *
 * Bedienung:
 *   - Linke Maustaste auf ein Element/Teil gedrueckt halten und ziehen.
 *   - Loslassen legt die neue Position fest.
 *   - ESC schliesst den Editor und speichert automatisch.
 *
 * Es werden nur AKTIVE Elemente angezeigt (die, die auch im Spiel sichtbar
 * sind). Die Position wird live in die jeweiligen Settings geschrieben.
 */
public class HudEditorScreen extends Screen {

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    // --- Was wird gerade gezogen? Immer nur EINES davon ist != null. ---

    // Ein normales HUD-Element (FPS/CPS/...), das ueber x/y verschoben wird.
    private HudElement draggingElement = null;

    // Ein ArmorHUD-Teil, das ueber seine Offset-Settings verschoben wird.
    // Wir merken uns die Offset-Settings und den Startwert + Startmaus.
    private NumberSetting dragPartOffX = null;
    private NumberSetting dragPartOffY = null;
    private int partStartOffX = 0;
    private int partStartOffY = 0;
    private int dragStartMouseX = 0;
    private int dragStartMouseY = 0;

    // Greif-Offset fuer normale Elemente (Maus zur Element-Ecke).
    private int grabOffsetX = 0;
    private int grabOffsetY = 0;

    public HudEditorScreen() {
        super(Text.literal("HUD-Editor"));
    }

    /** Alle aktiven normalen HUD-Elemente einsammeln. */
    private List<HudElement> elements() {
        List<HudElement> list = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof HudElement he && m.isEnabled()) {
                list.add(he);
            }
        }
        return list;
    }

    /** Das aktive ArmorHUD-Modul (oder null, wenn aus/nicht vorhanden). */
    private ArmorHudModule armor() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof ArmorHudModule a && a.isEnabled()) return a;
        }
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // KEIN renderBackground(...) -- das loest in 1.21.11 einen Blur aus,
        // der pro Frame nur einmal erlaubt ist (sonst Crash). Stattdessen
        // direkt eine halbtransparente Abdunklung zeichnen.
        ctx.fill(0, 0, this.width, this.height, 0x80000000);

        ctx.drawTextWithShadow(this.textRenderer,
            "HUD-Editor: Elemente & ArmorHUD-Teile ziehen. ESC schliesst & speichert.",
            8, 8, 0xFFFFFFFF);

        // 1) Normale HUD-Elemente als Kaesten.
        for (HudElement he : elements()) {
            int x = he.hudX().getInt();
            int y = he.hudY().getInt();
            int w = he.hudWidth();
            int h = he.hudHeight();

            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            boolean active = (he == draggingElement);
            int border = active ? 0xFFFFFF55 : (hovered ? 0xFF55FF7A : 0xFFFFFFFF);
            int fill = active ? 0x6055FF7A : 0x40FFFFFF;

            ctx.fill(x, y, x + w, y + h, fill);
            drawBorder(ctx, x, y, w, h, border);
            ctx.drawTextWithShadow(this.textRenderer, he.hudName(), x + 2, y + 2, 0xFFFFFFFF);
        }

        // 2) ArmorHUD-Teile als eigene Kaesten (Helm/Brust/Hose/Schuhe/Waffe).
        ArmorHudModule a = armor();
        if (a != null) {
            for (ArmorHudModule.ArmorPart part : a.computeParts(this.width, this.height)) {
                int x = part.x;
                int y = part.y;
                int s = part.size;

                boolean hovered = inside(mouseX, mouseY, x, y, s, s);
                boolean active = (dragPartOffX == part.offsetX && dragPartOffY == part.offsetY
                                  && (dragPartOffX != null));
                int border = active ? 0xFFFFFF55 : (hovered ? 0xFF7AB8FF : 0xFFCCCCCC);
                int fill = active ? 0x6055AAFF : 0x4055AAFF;

                ctx.fill(x, y, x + s, y + s, fill);
                drawBorder(ctx, x, y, s, s, border);
                // Kuerzel des Teils (erster Buchstabe) in den kleinen Kasten.
                ctx.drawTextWithShadow(this.textRenderer,
                    part.name.substring(0, 1), x + 5, y + 4, 0xFFFFFFFF);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) { // linke Maustaste
            // Zuerst ArmorHUD-Teile pruefen (liegen meist oben rechts, kleiner).
            ArmorHudModule a = armor();
            if (a != null) {
                List<ArmorHudModule.ArmorPart> parts = a.computeParts(this.width, this.height);
                for (int i = parts.size() - 1; i >= 0; i--) {
                    ArmorHudModule.ArmorPart part = parts.get(i);
                    if (inside(lastMouseX, lastMouseY, part.x, part.y, part.size, part.size)) {
                        dragPartOffX = part.offsetX;
                        dragPartOffY = part.offsetY;
                        partStartOffX = part.offsetX.getInt();
                        partStartOffY = part.offsetY.getInt();
                        dragStartMouseX = lastMouseX;
                        dragStartMouseY = lastMouseY;
                        return true;
                    }
                }
            }

            // Dann normale HUD-Elemente (oberstes zuerst).
            List<HudElement> els = elements();
            for (int i = els.size() - 1; i >= 0; i--) {
                HudElement he = els.get(i);
                int x = he.hudX().getInt();
                int y = he.hudY().getInt();
                if (inside(lastMouseX, lastMouseY, x, y, he.hudWidth(), he.hudHeight())) {
                    draggingElement = he;
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
        // ArmorHUD-Teil ziehen: Offset um die Mausbewegung veraendern.
        if (dragPartOffX != null && dragPartOffY != null) {
            int dx = lastMouseX - dragStartMouseX;
            int dy = lastMouseY - dragStartMouseY;
            dragPartOffX.set(partStartOffX + dx);
            dragPartOffY.set(partStartOffY + dy);
            return true;
        }

        // Normales HUD-Element ziehen: neue Position = Maus minus Greif-Offset.
        if (draggingElement != null) {
            int newX = lastMouseX - grabOffsetX;
            int newY = lastMouseY - grabOffsetY;
            newX = clamp(newX, 0, this.width - 1);
            newY = clamp(newY, 0, this.height - 1);
            draggingElement.hudX().set(newX);
            draggingElement.hudY().set(newY);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        boolean was = (draggingElement != null) || (dragPartOffX != null);
        draggingElement = null;
        dragPartOffX = null;
        dragPartOffY = null;
        if (was) return true;
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
