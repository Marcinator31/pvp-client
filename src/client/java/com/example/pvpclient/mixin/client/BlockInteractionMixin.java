package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blockiert Block-Interaktionen (Abbau und Benutzen/Platzieren), solange die
 * Freecam aktiv ist.
 *
 * Warum: In der Freecam liegt die Kamera woanders als der Spieler. Wuerde man
 * abbauen/platzieren, ginge die Aktion von der Freecam-Position aus -- der
 * Server sieht den Spieler dann etwas weit weg "ohne hinzusehen" abbauen, was
 * Anticheats (z.B. auf SMP-Servern) als Nuker/illegale Aktion werten und
 * reparieren/flaggen. Deshalb -- wie die etablierte Freecam-Mod -- blockieren
 * wir Block-Interaktionen in der Freecam komplett. Angriffe auf Entities bleiben
 * erlaubt (separat).
 *
 *   handleBlockBreaking = method_1590 (kontinuierlicher Abbau, Maustaste halten)
 *   doItemUse           = method_1583 (Rechtsklick: platzieren / benutzen)
 */
@Mixin(MinecraftClient.class)
public abstract class BlockInteractionMixin {

    @Inject(method = "method_1590", at = @At("HEAD"), cancellable = true)
    private void pvpclient$blockBreakingInFreecam(boolean breaking, CallbackInfo ci) {
        if (Freecam.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "method_1583", at = @At("HEAD"), cancellable = true)
    private void pvpclient$blockUseInFreecam(CallbackInfo ci) {
        if (Freecam.isActive()) {
            ci.cancel();
        }
    }
}
