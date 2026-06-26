package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blockiert die Spieler-Bewegungseingabe, solange die Freecam aktiv ist.
 *
 * Ohne das wuerde WASD gleichzeitig den Spieler UND die Freecam bewegen -- der
 * Charakter laeuft also mit, was alles verbuggt (Spieler an anderer Stelle als
 * Kamera, Hitboxen springen).
 *
 * Wir haengen uns ans Ende von tick() und setzen die berechnete Eingabe auf
 * neutral zurueck. Die Felder liegen in der Oberklasse Input -- wir erreichen
 * sie ueber das InputAccessor-Interface (Cast), nicht per @Shadow auf der
 * Subklasse (das schlug fehl, weil Mixin die vererbten Felder dort nicht fand).
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "method_3129", at = @At("TAIL"))
    private void pvpclient$blockInputInFreecam(CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        InputAccessor acc = (InputAccessor) this;
        acc.pvpclient$setMovementVector(Vec2f.ZERO);
        acc.pvpclient$setPlayerInput(PlayerInput.DEFAULT);
    }
}
