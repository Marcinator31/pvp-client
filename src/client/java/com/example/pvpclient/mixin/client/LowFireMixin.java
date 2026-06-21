package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.LowFireModule;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Fire -- senkt die Feuer-Flammen im First-Person-Overlay nach unten,
 * sodass man beim Brennen mehr sieht. Vorbild: BactroMod.
 *
 * Ziel verifiziert aus BactroMod-Bytecode:
 *   class_4603 (InGameOverlayRenderer) -> method_23070 (renderFireOverlay).
 *   Nach matrixStack.push() (method_22903) injizieren und per translate
 *   (method_46416, FFF) die Flamme nach unten verschieben.
 *
 * BactroMod nutzt einen konfigurierbaren fireOffset. Wir nehmen einen
 * festen, deutlichen Wert nach unten (Y negativ schiebt die Flamme runter).
 */
@Mixin(targets = "net.minecraft.client.render.InGameOverlayRenderer")
public class LowFireMixin {

    @Inject(
        method = "method_23070",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_4587;method_22903()V",
            shift = At.Shift.AFTER
        )
    )
    private static void pvpclient$lowFire(MatrixStack matrices, VertexConsumerProvider consumers,
                                          Sprite sprite, CallbackInfo ci) {
        LowFireModule mod = find();
        if (mod != null && mod.isEnabled()) {
            // Flamme nach unten schieben (aus dem Sichtfeld).
            matrices.translate(0.0f, -0.3f, 0.0f);
        }
    }

    private static LowFireModule find() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof LowFireModule l) return l;
        }
        return null;
    }
}
