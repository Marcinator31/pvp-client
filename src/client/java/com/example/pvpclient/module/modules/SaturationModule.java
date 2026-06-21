package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/**
 * Sättigungs-Anzeige (im Stil von AppleSkin).
 *
 * Zeigt die versteckte Sättigung als Zahl plus Hunger-Level. Robuste
 * HUD-Text-Variante statt eines fragilen Hungerleisten-Mixins -- nutzt
 * dasselbe HUD-System wie CPS/FPS/Koordinaten.
 *
 * Werte verifiziert aus AppleSkin + 1.21.11-Javadoc:
 *   PlayerEntity.getHungerManager() -> HungerManager
 *   HungerManager.getSaturationLevel() / getFoodLevel()
 */
public class SaturationModule extends Module {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 60, 0, 1080, 1);
    public final BooleanSetting showHunger = new BooleanSetting("Hunger anzeigen", true);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);

    public SaturationModule() {
        super("Saturation", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(showHunger);
        addSetting(color);
    }
}
