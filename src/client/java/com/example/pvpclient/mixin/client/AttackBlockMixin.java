package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blockiert das Angreifen (linke Maustaste / doAttack), solange die Freecam
 * aktiv ist.
 *
 * Warum: In der Freecam soll man nur schauen, nicht kaempfen. Wuerde der Angriff
 * durchgehen, koennte er die lokale Kamera-Entity treffen (Kick "invalid
 * entity") oder aus der Freecam-Position ausgehen (Anticheat-Flag). Wir brechen
 * doAttack bei aktiver Freecam komplett ab.
 *
 * doAttack = method_1536 (gibt boolean zurueck). HEAD + setReturnValue(false).
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
