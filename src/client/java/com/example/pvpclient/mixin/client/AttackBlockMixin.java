package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blockiert das Angreifen, solange die Freecam aktiv ist.
 *
 * Warum: In der Freecam gibt es eine lokale Kamera-Entity. Schlaegt man auf die
 * Stelle, wo der (unsichtbare) Spieler steht, koennte der Angriff diese nur
 * lokal existierende Entity treffen. Der Client wuerde dann ein Attack-Paket mit
 * ihrer ID an den Server schicken -- der Server kennt sie nicht und kickt
 * ("attack invalid entity"). Indem wir doAttack() bei aktiver Freecam komplett
 * abbrechen, wird gar kein Angriff verarbeitet und kein Paket gesendet. In der
 * Freecam will man ohnehin nur schauen, nicht kaempfen.
 *
 * doAttack = method_1536 (gibt boolean zurueck). Wir injizieren am HEAD und
 * canceln mit Rueckgabe false, wenn Freecam aktiv ist.
 */
@Mixin(MinecraftClient.class)
public abstract class AttackBlockMixin {

    @Inject(method = "method_1536", at = @At("HEAD"), cancellable = true)
    private void pvpclient$blockAttackInFreecam(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
