package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.FullbrightModule;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Umgeht die Gamma-Begrenzung, damit Fullbright wirklich hell wird.
 *
 * Problem: client.options.getGamma().setValue(10.0) wird normalerweise
 * auf das Maximum 1.0 begrenzt -- deshalb war Fullbright kaum sichtbar.
 *
 * Loesung: Wir haengen uns in getValue() der Gamma-Option ein und geben
 * einen hohen Wert zurueck, wenn Fullbright an ist. So sieht der
 * Lightmap-Renderer einen Gamma-Wert von z.B. 15, ohne dass wir den
 * gespeicherten Wert veraendern.
 *
 * HINWEIS: Dieser Mixin zielt auf SimpleOption.getValue(). Da viele
 * Optionen diese Methode nutzen, pruefen wir, ob es WIRKLICH die
 * Gamma-Option ist -- ueber den Vergleich mit client.options.getGamma().
 */
@Mixin(SimpleOption.class)
public class GammaMixin {

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void pvpclient$boostGamma(CallbackInfoReturnable<Object> cir) {
        FullbrightModule mod = find();
        if (mod == null || !mod.isEnabled()) return;

        net.minecraft.client.MinecraftClient client =
            net.minecraft.client.MinecraftClient.getInstance();
        if (client.options == null) return;

        // Nur eingreifen, wenn DIESE Option die Gamma-Option ist.
        if ((Object) this == client.options.getGamma()) {
            cir.setReturnValue(15.0);
        }
    }

    private static FullbrightModule find() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof FullbrightModule f) return f;
        }
        return null;
    }
}
