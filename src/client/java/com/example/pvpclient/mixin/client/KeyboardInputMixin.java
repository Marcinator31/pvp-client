package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blockiert die Spieler-Bewegungseingabe, solange die Freecam aktiv ist.
 *
 * Ohne das wuerde WASD gleichzeitig den Spieler UND die Freecam bewegen -- der
 * Charakter laeuft also mit, was alles verbuggt (Spieler an anderer Stelle als
 * Kamera, Hitboxen springen). Wir haengen uns ans Ende von tick() und setzen
 * die berechnete Eingabe auf neutral zurueck, wenn die Freecam laeuft.
 *
 * Die Felder liegen in der Oberklasse Input -- wir erreichen sie per @Shadow.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Shadow
    public Vec2f field_55868;     // movementVector (in Input)
    @Shadow
    public PlayerInput field_54155; // playerInput (in Input)

    @Inject(method = "method_3129", at = @At("TAIL"))
    private void pvpclient$blockInputInFreecam(CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        // Keine Bewegung an den Spieler weitergeben.
        this.field_55868 = Vec2f.ZERO;
        this.field_54155 = PlayerInput.DEFAULT;
    }
}
