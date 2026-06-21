package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;

/**
 * No Fog -- schaltet den Welt-Nebel ab.
 *
 * Nutzt FogRenderer.toggleFog() (parameterlos). FogRenderer liegt in
 * 1.21.11 im Paket net.minecraft.client.render.fog (verifiziert ueber
 * Yarn-Javadocs -- mit .fog im Pfad, das war vorher der Build-Fehler).
 *
 * toggleFog() kippt den Fog-Zustand. Wir kippen beim Ein- und Ausschalten.
 *
 * Reines Visual-Feature, server-erlaubt.
 */
public class NoFogModule extends Module {

    public NoFogModule() {
        super("No Fog", Category.MISC);
    }

    @Override
    public void onEnable() {
        net.minecraft.client.render.fog.FogRenderer.toggleFog();
    }

    @Override
    public void onDisable() {
        net.minecraft.client.render.fog.FogRenderer.toggleFog();
    }
}
