package com.example.pvpclient.mixin.client;

import com.example.pvpclient.hud.CpsCounter;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SO funktionieren Mixins -- das musst du verstehen, der Rest ist Wiederholung.
 *
 * Bei Plugins hast du Events abonniert (@EventHandler onClick...).
 * Hier gibt es das nicht. Stattdessen "injizieren" wir Code in eine
 * bestehende Minecraft-Methode.
 *
 * @Mixin(Mouse.class)  -> "wir haengen uns an die Klasse Mouse"
 * @Inject(method=...)  -> "und zwar in deren Methode onMouseButton"
 * at @At("HEAD")       -> "ganz am Anfang dieser Methode"
 *
 * Wenn Minecraft also einen Mausklick verarbeitet, laeuft danach
 * unsere onMouseButton-Methode mit und wir zaehlen den Klick.
 *
 * Hinweis zu den Namen: Wir nutzen Mojang Mappings (ab 1.21.11 Standard).
 * Falls 'onMouseButton' beim Build nicht gefunden wird, ist der Mojang-Name
 * leicht anders -- dann im Fehlerlog / in der Mouse-Klasse den echten
 * Methodennamen nachschauen und hier eintragen. Das ist normaler
 * Mod-Entwicklungs-Alltag.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;
    private static final int ACTION_PRESS = 1; // GLFW: 1 = gedrueckt

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void pvpclient$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != ACTION_PRESS) {
            return; // nur das Druecken zaehlen, nicht das Loslassen
        }
        if (button == LEFT_BUTTON) {
            CpsCounter.LEFT.onClick();
        } else if (button == RIGHT_BUTTON) {
            CpsCounter.RIGHT.onClick();
        }
    }
}
