package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Fullbright -- alles hell, keine Fackeln noetig.
 *
 * Umsetzung OHNE Mixin (sicher): Wir setzen ueber client.options.getGamma()
 * den Helligkeitswert per Tick auf Maximum, solange das Modul an ist.
 * Beim Ausschalten stellen wir den vorherigen Wert wieder her.
 *
 * getGamma() -> SimpleOption<Double> mit getValue()/setValue() ist gegen
 * die Fabric-Doku verifiziert.
 *
 * Reines Visual-Feature, server-erlaubt.
 */
public class FullbrightModule extends Module {

    // Merkt sich die normale Helligkeit, um sie beim Ausschalten
    // wiederherzustellen.
    private Double savedGamma = null;

    public FullbrightModule() {
        super("Fullbright", Category.MISC);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.options == null) return;

        var gammaOption = client.options.getGamma();

        if (isEnabled()) {
            // Beim ersten aktiven Tick den Originalwert merken.
            if (savedGamma == null) {
                savedGamma = gammaOption.getValue();
            }
            // Auf hell setzen (10.0 = deutlich heller als Vanilla-Max 1.0;
            // viele Fullbright-Mods uebersteuern bewusst).
            gammaOption.setValue(10.0);
        } else {
            // Modul aus -> Originalwert zurueck, falls wir ihn geaendert haben.
            if (savedGamma != null) {
                gammaOption.setValue(savedGamma);
                savedGamma = null;
            }
        }
    }
}
