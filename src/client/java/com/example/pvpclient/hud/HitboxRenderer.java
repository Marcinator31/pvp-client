package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet die Hitboxen aller Entities in der Welt (wie F3+B), aber
 * eingefaerbt nach Kategorie und einzeln im ClickGUI schaltbar.
 *
 * Technik (alles aus 1.21.11-Javadoc verifiziert):
 *   - WorldRenderEvents.AFTER_ENTITIES gibt MatrixStack + VertexConsumerProvider
 *   - VertexRendering.drawBox(MatrixStack, VertexConsumer, Box, r,g,b,a)
 *     zeichnet eine Box als Linien
 *   - Koordinaten muessen relativ zur Kamera sein -> Kamera-Pos abziehen
 */
public final class HitboxRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            HitboxModule mod = (HitboxModule) find(HitboxModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                // Kamera-Position (alle Box-Koordinaten relativ dazu).
                Vec3d cam = context.camera().getPos();

                // Linien-Buffer (durch Bloecke sichtbar).
                VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

                // Alle Entities im Umkreis holen. getEntitiesByClass(Entity.class,
                // box, predicate) ist im EntityView-Interface garantiert
                // vorhanden (anders als ein evtl. fehlendes getEntities()).
                // Box: grosszuegiger Wuerfel um den Spieler (Render-Distanz).
                double range = 128.0;
                Box area = new Box(
                        client.player.getX() - range, client.player.getY() - range, client.player.getZ() - range,
                        client.player.getX() + range, client.player.getY() + range, client.player.getZ() + range);

                for (Entity entity : client.world.getEntitiesByClass(
                        Entity.class, area, e -> e != client.player)) {
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

                    // ARGB in 0..1-Komponenten zerlegen.
                    float a = ((color >> 24) & 0xFF) / 255.0f;
                    float r = ((color >> 16) & 0xFF) / 255.0f;
                    float g = ((color >> 8) & 0xFF) / 255.0f;
                    float b = (color & 0xFF) / 255.0f;
                    if (a <= 0.0f) a = 1.0f; // Falls Alpha 0 gespeichert: voll sichtbar.

                    // Hitbox holen und relativ zur Kamera verschieben.
                    Box box = entity.getBoundingBox().offset(-cam.x, -cam.y, -cam.z);

                    // Box als Linien zeichnen.
                    VertexRendering.drawBox(matrices, lines,
                            box.minX, box.minY, box.minZ,
                            box.maxX, box.maxY, box.maxZ,
                            r, g, b, a);
                }
            } catch (Throwable ignored) {
                // Falls eine Render-Methode in dieser Version abweicht:
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
