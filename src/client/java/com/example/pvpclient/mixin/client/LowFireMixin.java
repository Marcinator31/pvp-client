package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.LowFireModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Fire -- senkt die Feuer-Flammen im First-Person-Overlay nach unten.
 * Vorbild: BactroMod.
 *
 * Signatur jetzt korrekt (aus Yarn-Mapping verifiziert):
 *   class_4603 = InGameOverlayRenderer (Paket: client.gui.hud, NICHT render!)
 *   method_23070 = renderFireOverlay(class_310, class_4587)
 *                = renderFireOverlay(MinecraftClient, MatrixStack)
 *
 * Vorher griff der Mixin nicht, weil Paket UND Parameter falsch waren
 * (ich hatte MatrixStack/VertexConsumerProvider/Sprite statt
 * MinecraftClient/MatrixStack).
 *
 * Wir injizieren nach matrixStack.push() (method_22903) und schieben per
 * translate die Flamme nach unten.
 */
@Mixin(InGameOverlayRenderer.class)
public class LowFireMixin {

    @Inject(
        method = "method_23070",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_4587;method_22903()V",
            shift = At.Shift.AFTER
        )
    )
    private static void pvpclient$lowFire(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        LowFireModule mod = find();
        if (mod != null && mod.isEnabled()) {
            // Flamme nach unten schieben, sodass man mehr sieht.
            matrices.translate(0.0f, -0.4f, 0.0f);
        }
    }

    private static LowFireModule find() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof LowFireModule l) return l;
        }
        return null;
    }
}
