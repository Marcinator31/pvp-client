package com.example.pvpclient.hud;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Zeichnet Entity-Hitboxen im 3D-Raum.
 *
 * Verifiziert in 1.21.11 ueber VS-Code-Autovervollstaendigung:
 *  - WorldRenderEvents/WorldRenderContext liegen im Unterpaket ...rendering.v1.world
 *  - Die Box-Methode ist:
 *      VertexRendering.drawOutline(MatrixStack, VertexConsumer, VoxelShape,
 *                                  double x, double y, double z, int color, float ?)
 *    -> sie nimmt eine VoxelShape (nicht Box!) plus Offset und ARGB-Farbe.
 *
 * Wir bauen aus der Entity-Bounding-Box eine VoxelShape, verschieben sie
 * relativ zur Kamera ueber die drei double-Offsets und faerben sie je
 * nach Entity-Kategorie.
 */
public final class HitboxRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(HitboxRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        HitboxModule module = find();
        if (module == null || !module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        // Linien-Layer fuer Umrisse.
        VertexConsumer lines = consumers.getBuffer(net.minecraft.client.render.RenderLayer.getLines());

        // Kamera-Position -- die Box wird relativ dazu gezeichnet.
        Vec3d cam = context.camera().getPos();

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;

            Integer color = colorFor(entity, module);
            if (color == null) continue;

            // Vanilla-Hitbox -> in eine VoxelShape umwandeln.
            Box box = entity.getBoundingBox();
            // Die Box muss relativ zum Welt-Ursprung als VoxelShape vorliegen;
            // wir bauen eine Shape aus den lokalen Ausmassen (0..breite etc.)
            // und verschieben sie ueber die Offset-Parameter zur Kamera.
            double x = box.minX - cam.x;
            double y = box.minY - cam.y;
            double z = box.minZ - cam.z;

            var shape = VoxelShapes.cuboid(
                0.0, 0.0, 0.0,
                box.maxX - box.minX,
                box.maxY - box.minY,
                box.maxZ - box.minZ
            );

            // Letzter float-Parameter: Linienbreite/Alpha-aehnlich -> 1.0f.
            VertexRendering.drawOutline(matrices, lines, shape, x, y, z, color, 1.0f);
        }
    }

    /** Farbe (ARGB) je nach Kategorie; null wenn diese Kategorie aus ist. */
    private static Integer colorFor(Entity entity, HitboxModule m) {
        if (entity instanceof PlayerEntity) {
            return m.showPlayers.get() ? m.playerColor.get() : null;
        }
        if (entity instanceof HostileEntity) {
            return m.showHostiles.get() ? m.hostileColor.get() : null;
        }
        if (entity instanceof PassiveEntity) {
            return m.showAnimals.get() ? m.animalColor.get() : null;
        }
        if (entity instanceof LivingEntity) {
            return m.showHostiles.get() ? m.hostileColor.get() : null;
        }
        return m.showMisc.get() ? m.miscColor.get() : null;
    }

    private static HitboxModule find() {
        for (var mod : ModuleManager.INSTANCE.getModules()) {
            if (mod instanceof HitboxModule h) return h;
        }
        return null;
    }
}
