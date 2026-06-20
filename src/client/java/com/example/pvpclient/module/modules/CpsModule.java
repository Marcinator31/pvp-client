package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/**
 * CPS-Anzeige als Modul.
 *
 * Sieh dir an, wie wenig hier steht: nur die Settings. Die eigentliche
 * Klick-Zaehlung lebt weiter in CpsCounter, das Zeichnen im HudRenderer.
 * Das Modul ist nur der "Vertrag": Bin ich an? Wo? Welche Farbe?
 */
public class CpsModule extends Module {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 4, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);

    public CpsModule() {
        super("CPS", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
    }
}
