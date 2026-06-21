package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/**
 * ArmorHUD als Modul.
 *
 * Kann jetzt:
 *  - die ganze HUD verschieben (baseX / baseY)
 *  - skalieren (scale)
 *  - die Haltbarkeits-Prozent ein/ausblenden (showDurability)
 *  - die Textfarbe der Prozentanzeige waehlen (textColor)
 *  - JEDES Teil einzeln verschieben (Offsets je Helm/Brust/Hose/Schuhe/Waffe)
 *
 * Die Einzel-Offsets werden ZUSAETZLICH zur Basisposition angewendet.
 */
public class ArmorHudModule extends Module {

    public final BooleanSetting showDurability = new BooleanSetting("Haltbarkeit %", true);
    public final ColorSetting textColor = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    public final NumberSetting baseX = new NumberSetting("X", 0, -1920, 1920, 1);
    public final NumberSetting baseY = new NumberSetting("Y", 0, -1080, 1080, 1);

    public final NumberSetting helmetOffsetX = new NumberSetting("Helm X", 0, -400, 400, 1);
    public final NumberSetting helmetOffsetY = new NumberSetting("Helm Y", 0, -400, 400, 1);
    public final NumberSetting chestOffsetX  = new NumberSetting("Brust X", 0, -400, 400, 1);
    public final NumberSetting chestOffsetY  = new NumberSetting("Brust Y", 0, -400, 400, 1);
    public final NumberSetting legsOffsetX   = new NumberSetting("Hose X", 0, -400, 400, 1);
    public final NumberSetting legsOffsetY   = new NumberSetting("Hose Y", 0, -400, 400, 1);
    public final NumberSetting bootsOffsetX  = new NumberSetting("Schuhe X", 0, -400, 400, 1);
    public final NumberSetting bootsOffsetY  = new NumberSetting("Schuhe Y", 0, -400, 400, 1);
    public final NumberSetting handOffsetX   = new NumberSetting("Waffe X", 0, -400, 400, 1);
    public final NumberSetting handOffsetY   = new NumberSetting("Waffe Y", 0, -400, 400, 1);

    public ArmorHudModule() {
        super("ArmorHUD", Category.HUD);
        enabledByDefault();
        addSetting(showDurability);
        addSetting(textColor);
        addSetting(scale);
        addSetting(baseX);
        addSetting(baseY);
        addSetting(helmetOffsetX);
        addSetting(helmetOffsetY);
        addSetting(chestOffsetX);
        addSetting(chestOffsetY);
        addSetting(legsOffsetX);
        addSetting(legsOffsetY);
        addSetting(bootsOffsetX);
        addSetting(bootsOffsetY);
        addSetting(handOffsetX);
        addSetting(handOffsetY);
    }
}
