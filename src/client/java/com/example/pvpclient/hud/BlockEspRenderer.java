package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.BlockEspModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Zeichnet farbige Box-Outlines um ausgewaehlte Bloecke in der Welt (Block-ESP).
 *
 * Mechanik wie der HitboxRenderer (gleicher 1.21.11-Render-Weg):
 *   - WorldRenderEvents.AFTER_ENTITIES liefert MatrixStack + Consumers.
 *   - RenderLayers.lines() als Linien-Buffer.
 *   - VertexRendering.drawOutline(matrices, lines, shape, offX, offY, offZ,
 *       color, lineWidth) zeichnet die Outline; Offset = -Kamera-Position.
 *
 * Statt Entities durchlaufen wir die Bloecke in einem Wuerfel um den Spieler
 * (Reichweite aus dem Modul) und zeichnen fuer jeden aktivierten Block-Typ.
 * Die Reichweite ist bewusst begrenzt, damit die Suche pro Frame guenstig bleibt.
 */
public final class BlockEspRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            BlockEspModule mod = (BlockEspModule) find(BlockEspModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (!mod.hasAnyBlock()) return; // nichts ausgewaehlt -> nichts tun

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = client.player.getCameraPosVec(tickDelta);

                VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());

                int color = mod.getEspColor();
                if ((color >>> 24) == 0) color = 0xFF000000 | color; // Alpha sichern
                float lineWidth = mod.lineWidth.getFloat();

                int range = mod.range.getInt();
                // Spieler-Blockposition robust aus den Koordinaten bauen.
                BlockPos center = BlockPos.ofFloored(
                        client.player.getX(), client.player.getY(), client.player.getZ());

                // Wuerfel um den Spieler absuchen.
                BlockPos.Mutable pos = new BlockPos.Mutable();
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        for (int dz = -range; dz <= range; dz++) {
                            pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                            BlockState state = client.world.getBlockState(pos);
                            if (state.isAir()) continue;

                            Identifier id = Registries.BLOCK.getId(state.getBlock());
                            if (!mod.isBlockEnabled(id)) continue;

                            // Outline an der Blockposition (1x1x1).
                            Box box = new Box(
                                    pos.getX(), pos.getY(), pos.getZ(),
                                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
                            VoxelShape shape = VoxelShapes.cuboid(box);

                            VertexRendering.drawOutline(matrices, lines, shape,
                                    -cam.x, -cam.y, -cam.z,
                                    color, lineWidth);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Render darf nie crashen.
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
