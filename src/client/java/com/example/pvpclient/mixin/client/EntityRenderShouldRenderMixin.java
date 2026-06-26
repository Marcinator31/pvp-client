package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.FreeCamera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Verhindert, dass die Freecam-Kamera-Entity gerendert wird.
 *
 * Problem: Die FreeCamera ist ein ClientPlayerEntity und wuerde als Koerper /
 * Hitbox ueber dem Freecam-Kopf gezeichnet. setInvisible() allein reicht nicht
 * (die Hitbox/der Umriss bleibt). Deshalb greifen wir frueher an: shouldRender()
 * entscheidet, OB eine Entity ueberhaupt gerendert wird. Fuer die FreeCamera
 * geben wir false zurueck -> sie wird komplett uebersprungen.
 *
 * shouldRender = method_3933 auf EntityRenderer (gibt boolean zurueck).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRenderShouldRenderMixin {

    @Inject(method = "method_3933", at = @At("HEAD"), cancellable = true)
    private void pvpclient$hideFreeCamera(Entity entity, Frustum frustum,
                                          double x, double y, double z,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof FreeCamera) {
            cir.setReturnValue(false);
        }
    }
}
