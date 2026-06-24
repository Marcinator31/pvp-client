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
 * Frame die Kamera positioniert, und ueberschreiben danach die Position mit der
 * Freecam-Position. Die Rotation bleibt -- sie kommt aus der Spieler-
 * Blickrichtung, also dreht die Maus weiterhin die Kamera.
 *
 * setPos(double,double,double) ist in Camera selbst (protected). Da wir AUF
 * Camera mixen, koennen wir es als @Shadow direkt aufrufen.
 */
@Mixin(net.minecraft.client.render.Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void method_19327(double x, double y, double z); // setPos

    @Inject(method = "method_19321", at = @At("TAIL"))
    private void pvpclient$freecamUpdate(World area, Entity focusedEntity,
                                         boolean thirdPerson, boolean inverseView,
                                         float tickProgress, CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        Vec3d pos = Freecam.getPos();
        method_19327(pos.x, pos.y, pos.z);
    }
}
