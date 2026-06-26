package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.KeySetting;
import com.example.pvpclient.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam: loest die Kamera vom Spieler. Mit der eingestellten Taste schaltet
 * man die freie Kamera an/aus und fliegt dann mit WASD + Leertaste/Shift herum.
 * Der Spieler bleibt dabei stehen.
 *
 * Die eigentliche Logik steckt in der Freecam-Klasse + CameraMixin. Dieses
 * Modul haelt nur die Tasten-Einstellung (in der GUI aenderbar).
 */
public class FreecamModule extends Module {

    public final KeySetting key = new KeySetting("Taste", GLFW.GLFW_KEY_F4);

    public FreecamModule() {
        super("Freecam", Category.MISC);
        addSetting(key);
    }
}
