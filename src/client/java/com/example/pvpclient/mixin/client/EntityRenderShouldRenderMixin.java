package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.FreeCamera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Verhindert, dass die Freecam-Kamera-Entity gerendert wird (kein Koerper, keine
 * Hitbox ueber dem Freecam-Kopf).
 *
 * WICHTIG -- richtige Klasse: In 1.21.11 entscheidet EntityRenderManager
 * (intermediary class_898, frueher "EntityRenderDispatcher") per shouldRender,
 * ob eine Entity gezeichnet wird. (Ein frueherer Versuch zielte auf
 * EntityRenderer.shouldRender und griff nicht.) Fuer die FreeCamera geben wir
 * hier false zurueck -> sie wird komplett uebersprungen.
 *
 * shouldRender = method_3950 (Entity, Frustum, double, double, double) -> boolean.
 */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderShouldRenderMixin {

    @Inject(method = "method_3950", at = @At("HEAD"), cancellable = true)
    private void pvpclient$hideFreeCamera(Entity entity, Frustum frustum,
                                          double x, double y, double z,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof FreeCamera) {
            cir.setReturnValue(false);
        }
    }
}
