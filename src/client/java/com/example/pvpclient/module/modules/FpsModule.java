package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/** Einfache FPS-Anzeige. */
public class FpsModule extends Module {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 16, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);

    public FpsModule() {
        super("FPS", Category.HUD);
        enabledByDefault();
        addSetting(x);
        addSetting(y);
        addSetting(color);
    }
}
