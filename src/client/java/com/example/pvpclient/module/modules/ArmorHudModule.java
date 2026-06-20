package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/**
 * ArmorHUD als Modul. Demonstriert verschiedene Setting-Typen
 * gemischt: Schalter (Prozent zeigen?) + Zahl (Skalierung).
 */
public class ArmorHudModule extends Module {

    public final BooleanSetting showDurability = new BooleanSetting("Haltbarkeit %", true);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 2.0, 0.1);

    public ArmorHudModule() {
        super("ArmorHUD", Category.HUD);
        addSetting(showDurability);
        addSetting(scale);
    }
}
