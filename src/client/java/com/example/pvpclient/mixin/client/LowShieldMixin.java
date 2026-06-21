package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.LowShieldModule;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Shield -- verschiebt das gehaltene Schild nach unten, damit es
 * weniger Sicht blockiert. Vorbild: BactroMod.
 *
 * Ziel verifiziert aus BactroMod-Bytecode:
 *   class_759 (HeldItemRenderer) -> method_3233 (renderItem),
 *   HEAD, cancellable. Bei Schild (field_8255) die matrixStack per
 *   translate (method_22904, DDD) nach unten schieben.
 *
 * Hinweis: BactroMods Original rendert das Schild danach teilweise neu
 * (wegen der neuen 1.21.11-Render-Queue). Wir nehmen die einfachere
 * Variante: nur verschieben, nicht canceln -- das verschiebt das Schild,
 * ohne in die Render-Queue einzugreifen (robuster).
 *
 * Die genaue Parameterzahl von method_3233 lassen wir Mixin selbst
 * aufloesen, indem wir nur die Methode benennen (kein voller Deskriptor).
 */
@Mixin(net.minecraft.client.render.item.HeldItemRenderer.class)
public class LowShieldMixin {

    @Inject(method = "method_3233", at = @At("HEAD"))
    private void pvpclient$lowShield(LivingEntity entity, ItemStack stack, Object displayContext,
                                     MatrixStack matrices, Object queue, int light, CallbackInfo ci) {
        LowShieldModule mod = find();
        if (mod != null && mod.isEnabled() && stack.isOf(Items.SHIELD)) {
            matrices.translate(0.0, -0.6, 0.0);
        }
    }

    private static LowShieldModule find() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof LowShieldModule l) return l;
        }
        return null;
    }
}
