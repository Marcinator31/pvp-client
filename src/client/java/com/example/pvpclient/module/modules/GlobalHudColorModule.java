package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;

/**
 * "HUD-Farbe" -- wendet EINE Farbe auf alle HUD-Elemente gleichzeitig an
 * (FPS, CPS, Koordinaten, Trank-Effekte, Ruestungs-HUD).
 *
 * Bedienung im ClickGUI:
 *   - "Farbe" einstellen (die gewuenschte gemeinsame Farbe).
 *   - "Jetzt anwenden" antippen -> die Farbe wird sofort auf alle HUD-Texte
 *     uebertragen. Der Knopf springt danach automatisch wieder auf aus.
 *
 * So muss man nicht jedes HUD-Element einzeln einfaerben. Dieses Modul ist
 * ein Werkzeug, kein an/aus-Feature -- der "Aktiviert"-Schalter spielt hier
 * keine Rolle, es zaehlt nur der "Jetzt anwenden"-Knopf.
 */
public class GlobalHudColorModule extends Module {

    public final ColorSetting color = new ColorSetting("Farbe", 0xFFFFFFFF);
    public final BooleanSetting apply = new BooleanSetting("Jetzt anwenden", false);

    public GlobalHudColorModule() {
        super("HUD-Farbe", Category.HUD);
        addSetting(color);
        addSetting(apply);
    }

    /**
     * Uebertraegt die eingestellte Farbe auf alle HUD-Elemente. Wird vom
     * ClickGUI aufgerufen, wenn der "Jetzt anwenden"-Knopf angetippt wird.
     */
    public void applyToAll() {
        int c = color.get();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            // Alle HUD-Elemente mit Farb-Setting einfaerben.
            if (m instanceof HudElement he) {
                he.hudColor().set(c);
            }
            // ArmorHud implementiert HudElement nicht (kein x/y), hat aber
            // eine textColor -- separat mit abdecken.
            if (m instanceof ArmorHudModule armor) {
                armor.textColor.set(c);
            }
        }
        // Knopf zuruecksetzen (er ist nur ein Ausloeser, kein Dauerzustand).
        apply.set(false);
    }
}
