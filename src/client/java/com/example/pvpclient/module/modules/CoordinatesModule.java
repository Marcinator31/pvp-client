package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/** Koordinaten-Anzeige (XYZ + Blickrichtung). */
public class CoordinatesModule extends Module {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 28, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    public CoordinatesModule() {
        super("Coordinates", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
    }
}
