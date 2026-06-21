package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/** Zeigt aktive Trank-Effekte als Liste (Name + Restzeit). */
public class PotionEffectsModule extends Module {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 44, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);

    public PotionEffectsModule() {
        super("Potion Effects", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
    }
}
