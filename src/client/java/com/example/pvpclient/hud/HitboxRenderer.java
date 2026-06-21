package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Zeichnet die Hitboxen aller Entities in der Welt (wie F3+B), aber
 * eingefaerbt nach Kategorie und einzeln im ClickGUI schaltbar.
 *
 * Verifiziert gegen die ECHTEN 1.21.11-Yarn-Mappings (build.4):
 *   - WorldRenderEvents liegt jetzt im Paket ...rendering.v1.world
 *   - Lines-RenderLayer: RenderLayers.lines() (frueher RenderLayer.getLines())
 *   - VertexRendering.drawOutline(MatrixStack, VertexConsumer, VoxelShape,
 *       double offsetX, offsetY, offsetZ, int color, float lineWidth)
 *     (frueher drawBox mit r,g,b,a -- in 1.21.11 ein gepackter int + lineWidth)
 *   - VoxelShapes.cuboid(Box) wandelt die Entity-Box in eine VoxelShape
 *   - Koordinaten relativ zur Kamera -> Kamera-Pos als Offset uebergeben
 */
public final class HitboxRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            HitboxModule mod = (HitboxModule) find(HitboxModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                // Kamera-Position: Spieler-Kamera-Pos als Bezugspunkt (alle
                // Box-Koordinaten relativ dazu). getCameraPosVec(1.0f) ist in
                // 1.21.11 verifiziert vorhanden -- anders als ein evtl. nicht
                // gemapptes Camera.getPos().
                Vec3d cam = client.player.getCameraPosVec(1.0f);

                // Linien-Buffer (1.21.11: RenderLayers.lines()).
                VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());

                float lineWidth = mod.lineWidth.getFloat();

                // Alle Entities im Umkreis (getEntitiesByClass ist garantiert da).
                double range = 128.0;
                Box query = new Box(
                        client.player.getX() - range, client.player.getY() - range, client.player.getZ() - range,
                        client.player.getX() + range, client.player.getY() + range, client.player.getZ() + range);

                for (Entity entity : client.world.getEntitiesByClass(
                        Entity.class, query, e -> e != client.player)) {
                    // Kategorie + Farbe bestimmen.
                    int color;
                    if (entity instanceof PlayerEntity) {
                        if (!mod.showPlayers.get()) continue;
                        color = mod.playerColor.get();
                    } else if (entity instanceof HostileEntity) {
                        if (!mod.showHostiles.get()) continue;
                        color = mod.hostileColor.get();
                    } else if (entity instanceof PassiveEntity) {
                        if (!mod.showAnimals.get()) continue;
                        color = mod.animalColor.get();
                    } else {
                        if (!mod.showMisc.get()) continue;
                        color = mod.miscColor.get();
                    }

                    // Sicherstellen, dass Alpha gesetzt ist (sonst unsichtbar).
                    if ((color >>> 24) == 0) {
                        color = 0xFF000000 | color;
                    }

                    // Entity-Hitbox -> VoxelShape. Die Box bleibt in Welt-Koords;
                    // die Kamera-Position wird als Offset an drawOutline uebergeben.
                    Box box = entity.getBoundingBox();
                    VoxelShape shape = VoxelShapes.cuboid(box);

                    VertexRendering.drawOutline(matrices, lines, shape,
                            -cam.x, -cam.y, -cam.z,
                            color, lineWidth);
                }
            } catch (Throwable ignored) {
                // Falls eine Render-Methode in dieser Version doch abweicht:
                // Hitboxen still ausfallen lassen, NICHT das Spiel crashen.
            }
        });
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
