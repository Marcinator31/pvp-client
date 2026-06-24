package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lenkt die Kamera auf die Freecam-Position um, wenn die Freecam aktiv ist.
 *
 * Wir haengen uns ans Ende von Camera.update(...) (method_19321), das jeden
 * Frame die Kamera positioniert, und ueberschreiben danach das pos-Feld mit der
 * Freecam-Position. Die Rotation bleibt -- sie kommt aus der Spieler-
 * Blickrichtung, also dreht die Maus weiterhin die Kamera.
 *
 * Wir setzen das pos-Feld (field_18712) direkt per @Shadow. Das ist robuster
 * als die protected setPos-Methode -- Feld-Shadows funktionieren bei Mixins
 * zuverlaessig.
 */
@Mixin(net.minecraft.client.render.Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Vec3d field_18712; // pos

    @Inject(method = "method_19321", at = @At("TAIL"))
    private void pvpclient$freecamUpdate(World area, Entity focusedEntity,
                                         boolean thirdPerson, boolean inverseView,
                                         float tickProgress, CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        // Bewegung pro Frame (fluessig, framerate-unabhaengig), dann Position
        // auf die Kamera anwenden.
        Freecam.updateFrame();
        this.field_18712 = Freecam.getPos();
    }
}
