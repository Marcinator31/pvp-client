package com.example.pvpclient.gui;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.core.setting.Setting;
import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

/**
 * Das ClickGUI -- dein "Mods-Menue" wie bei Lunar.
 *
 * Wichtig zum Verstehen: Diese Klasse kennt KEIN einzelnes Feature.
 * Sie laeuft nur ueber ModuleManager.getModules() und zeichnet, was
 * sie findet. Ein Modul mehr -> erscheint hier automatisch. Genau das
 * macht das System wartbar.
 *
 * Das hier ist eine funktionierende, bewusst schlichte Version:
 * - Kategorien als Spalten
 * - pro Modul eine Zeile (Klick = an/aus)
 * - Rechtsklick auf ein Modul klappt seine Settings auf
 *
 * Es ist absichtlich "from scratch" gezeichnet, damit du siehst, wie
 * GUI in Minecraft funktioniert. Spaeter kannst du es beliebig
 * aufhuebschen (Animationen, Drag&Drop der Panels, Suchfeld ...).
 */
public class ClickGui extends Screen {

    private static final int ROW_H = 14;
    private static final int PANEL_W = 110;

    // Welche Module sind gerade "aufgeklappt" (Settings sichtbar)?
    private final Set<Module> expanded = new HashSet<>();

    public ClickGui() {
        super(Text.literal("PvP Client"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Mausposition merken -- der Klick-Handler nutzt sie (siehe oben).
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // Kein this.renderBackground(...) hier -- das loest in 1.21.11 einen
        // Blur aus, der pro Frame nur einmal erlaubt ist (sonst Crash).
        // Stattdessen eine simple halbtransparente Abdunklung des Schirms.
        ctx.fill(0, 0, this.width, this.height, 0x88000000);
        Theme t = Theme.INSTANCE;

        int x = 10;
        // Eine Spalte pro Kategorie.
        for (Module.Category cat : Module.Category.values()) {
            int y = 10;

            // Kategorie-Kopf
            ctx.fill(x, y, x + PANEL_W, y + ROW_H, t.accent.get());
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(cat.name()),
                    x + 4, y + 3, t.text.get());
            y += ROW_H + 1;

            // Module dieser Kategorie
            for (Module m : ModuleManager.INSTANCE.getByCategory(cat)) {
                int bg = m.isEnabled() ? t.enabledColor.get() : t.panel.get();
                ctx.fill(x, y, x + PANEL_W, y + ROW_H, bg);
                ctx.drawTextWithShadow(this.textRenderer, Text.literal(m.getName()),
                        x + 4, y + 3, t.text.get());
                y += ROW_H;

                // Aufgeklappte Settings darunter
                if (expanded.contains(m)) {
                    for (Setting s : m.getSettings()) {
                        ctx.fill(x, y, x + PANEL_W, y + ROW_H, t.background.get());
                        String label = settingLabel(s);
                        ctx.drawTextWithShadow(this.textRenderer, Text.literal(label),
                                x + 8, y + 3, t.textDim.get());
                        y += ROW_H;
                    }
                }
                y += 1;
            }

            x += PANEL_W + 8;
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Baut den Anzeigetext einer Setting-Zeile je nach Typ. */
    private String settingLabel(Setting s) {
        if (s instanceof BooleanSetting b) {
            return s.getName() + ": " + (b.get() ? "an" : "aus");
        } else if (s instanceof NumberSetting n) {
            return s.getName() + ": " + n.get();
        } else if (s instanceof com.example.pvpclient.core.setting.ModeSetting mode) {
            return s.getName() + ": " + mode.get();
        } else if (s instanceof ColorSetting) {
            return s.getName() + " (Farbe)";
        }
        return s.getName();
    }

    // Letzte bekannte Mausposition -- aus render() gespeichert, wo sie
    // garantiert korrekt skaliert ankommt. So muessen wir sie im Klick
    // nicht selbst (fehleranfaellig) aus Fenster/Scale berechnen.
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = lastMouseX;
        double mouseY = lastMouseY;
        int button = click.button();

        int x = 10;
        for (Module.Category cat : Module.Category.values()) {
            int y = 10 + ROW_H + 1; // unter dem Kategorie-Kopf

            for (Module m : ModuleManager.INSTANCE.getByCategory(cat)) {
                if (inside(mouseX, mouseY, x, y, PANEL_W, ROW_H)) {
                    if (button == 0) {
                        m.toggle();                 // Linksklick = an/aus
                    } else if (button == 1) {
                        // Rechtsklick = Settings auf/zuklappen
                        if (!expanded.add(m)) expanded.remove(m);
                    }
                    return true;
                }
                y += ROW_H;

                if (expanded.contains(m)) {
                    for (Setting s : m.getSettings()) {
                        if (inside(mouseX, mouseY, x, y, PANEL_W, ROW_H)) {
                            handleSettingClick(m, s, button);
                            return true;
                        }
                        y += ROW_H;
                    }
                }
                y += 1;
            }
            x += PANEL_W + 8;
        }
        return false;
    }

    /** Klick auf eine Setting-Zeile -- simple Bedienung ohne Slider-Widget. */
    private void handleSettingClick(Module m, Setting s, int button) {
        if (s instanceof BooleanSetting b) {
            // Sonderfaelle der globalen HUD-Farbe (Knoepfe).
            if (m instanceof com.example.pvpclient.module.modules.GlobalHudColorModule ghc) {
                if (s == ghc.apply) {
                    ghc.applyToAll();   // Farbe auf alle HUD-Elemente uebertragen.
                    return;
                }
                if (s == ghc.reset) {
                    ghc.resetToWhite(); // Alle HUD-Texte wieder auf Weiss.
                    return;
                }
            }
            // WICHTIG: Ist das die "Aktiviert"-Zeile (der Haupt-An/Aus-Schalter
            // des Moduls)? Dann ueber setEnabled() schalten, damit onEnable()/
            // onDisable() ausgeloest werden -- sonst aendert sich nur der Wert,
            // aber das Feature wuerde im Spiel nichts tun.
            if (s == m.getEnabledSetting()) {
                m.toggle();
            } else {
                b.toggle();
            }
        } else if (s instanceof NumberSetting n) {
            // Linksklick erhoeht, Rechtsklick verringert -- simpel, aber es geht.
            double dir = (button == 1) ? -1 : 1;
            n.set(n.get() + dir * n.getStep());
        } else if (s instanceof com.example.pvpclient.core.setting.ModeSetting mode) {
            // Klick schaltet zur naechsten Option durch.
            mode.cycle();
        } else if (s instanceof ColorSetting c) {
            // Sonderfall HUD-Farbe: beim Aendern der Farbe SOFORT auf alle
            // HUD-Elemente anwenden (Live-Vorschau, kein extra Knopf noetig).
            if (m instanceof com.example.pvpclient.module.modules.GlobalHudColorModule ghc
                    && c == ghc.color) {
                net.minecraft.client.MinecraftClient.getInstance()
                    .setScreen(new ColorPickerScreen(this, c, ghc::applyToAll));
                return;
            }
            // Normaler Farbwaehler; "this" als Eltern-Screen, damit man
            // nach dem Schliessen wieder im ClickGui landet.
            net.minecraft.client.MinecraftClient.getInstance()
                .setScreen(new ColorPickerScreen(this, c));
        }
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean shouldPause() {
        return false; // Spiel laeuft im Hintergrund weiter (wie bei Lunar).
    }

    @Override
    public void removed() {
        // Beim Schliessen des ClickGUI sofort speichern -- so ueberlebt jede
        // Aenderung (Position, Farbe, an/aus) auch einen spaeteren Absturz,
        // nicht erst beim sauberen Spiel-Beenden.
        com.example.pvpclient.core.ConfigManager.save();
        super.removed();
    }
}
