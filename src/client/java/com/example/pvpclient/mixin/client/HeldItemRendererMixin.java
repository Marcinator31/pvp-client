package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HandItemScaleModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Macht das in der ersten Person gehaltene Item kleiner (Haupt- und Nebenhand),
 * je nach HandItemScaleModule.
 *
 * Ansatz: Am Anfang von renderFirstPersonItem den MatrixStack um den
 * Groessen-Faktor skalieren. Da push/pop rund um den Aufruf vom Vanilla-Code
 * gemacht werden, wirkt die Skalierung nur fuer dieses Item und nicht dauerhaft.
 *
 * Die volle Methoden-Signatur ist angegeben, damit es eindeutig ist.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(
        method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
        at = @At("HEAD")
    )
    private void pvpclient$scaleHandItem(AbstractClientPlayerEntity player, float tickDelta,
                                         float pitch, Hand hand, float swingProgress,
                                         ItemStack item, float equipProgress,
                                         MatrixStack matrices,
                                         OrderedRenderCommandQueue queue, int light,
                                         CallbackInfo ci) {
        try {
            HandItemScaleModule mod = (HandItemScaleModule) find(HandItemScaleModule.class);

            // Einmalige Diagnose: feuert der Inject ueberhaupt, und ist das Modul an?
            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[pvpclient] HandItemScale Inject feuert. Modul="
                        + (mod == null ? "null" : (mod.isEnabled() ? "AN" : "aus")));
            }

            if (mod == null || !mod.isEnabled()) return;

            float scale = (float) mod.size.get();
            if (scale == 1.0f) return; // nichts zu tun

            // Um die Item-Position herum skalieren, damit es nicht wegdriftet:
            // erst etwas in Richtung Hand verschieben, skalieren, zurueck.
            // Einfachste robuste Variante: gleichmaessig skalieren.
            matrices.scale(scale, scale, scale);
        } catch (Throwable ignored) {
            // Render darf nie crashen.
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean pvpclient$logged = false;

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
