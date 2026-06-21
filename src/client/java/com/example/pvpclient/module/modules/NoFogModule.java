package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;

/**
 * No Fog -- schaltet den Welt-Nebel ab.
 *
 * Nutzt FogRenderer.toggleFog() (in 1.21.11 via VS-Code verifiziert,
 * parameterlos). Die Methode kippt den Fog-Zustand. Das eigentliche
 * Umschalten passiert beim Ein-/Ausschalten des Moduls.
 *
 * Reines Visual-Feature, server-erlaubt.
 */
public class NoFogModule extends Module {

    public NoFogModule() {
        super("No Fog", Category.MISC);
    }

    @Override
    public void onEnable() {
        net.minecraft.client.render.FogRenderer.toggleFog();
    }

    @Override
    public void onDisable() {
        net.minecraft.client.render.FogRenderer.toggleFog();
    }
}
