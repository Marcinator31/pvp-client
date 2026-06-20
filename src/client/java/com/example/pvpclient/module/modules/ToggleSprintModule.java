package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;

/**
 * Toggle-Sprint: dauerhaftes Sprinten ohne die Taste gedrueckt zu halten.
 *
 * Das ist ein KOMFORT-Feature (auch Lunar/Vanilla-Servern erlaubt) --
 * es gibt keinen Kampfvorteil, du koenntest die Taste auch selbst
 * gedrueckt halten. Die Sprint-Logik selbst haengst du spaeter per
 * Mixin in die Tastenabfrage ein; hier ist erstmal der Modul-Eintrag,
 * damit es im Menue auftaucht und an/aus schaltbar ist.
 */
public class ToggleSprintModule extends Module {

    public ToggleSprintModule() {
        super("Toggle Sprint", Category.PVP);
    }
}
