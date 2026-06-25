package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.BlockEspModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Zeichnet farbige Box-Outlines um ausgewaehlte Bloecke in der Welt (Block-ESP).
 *
 * WICHTIG fuer Performance: Das Absuchen der Welt ist teuer (bei grosser
 * Reichweite Millionen Bloecke). Deshalb trennen wir Suche und Zeichnen:
 *   - Gesucht wird nur alle paar Ticks (SCAN_INTERVAL) ODER wenn sich die
 *     Spielerposition deutlich geaendert hat. Die Treffer landen in einem Cache.
 *   - Gezeichnet wird jeden Frame -- aber nur die gecachten Positionen, das ist
 *     billig.
 *
 * So bleibt die Suche selten und das Rendering schnell, auch bei voller Distanz.
 * Die Reichweite kommt aus dem Modul (in Bloecken, kann bis zur Render-Distanz
 * hochgestellt werden).
 */
public final class BlockEspRenderer {

    // Cache der gefundenen Block-Positionen (zwischen den Scans wiederverwendet).
    private static final List<BlockPos> cache = new ArrayList<>();

    // Wann zuletzt gescannt wurde (in ms) + wo der Spieler dabei stand.
    private static long lastScanMs = 0L;
    private static int lastScanX, lastScanY, lastScanZ;
    private static int lastRange = -1;
    private static String lastSelection = "";

    // Mindestabstand zwischen zwei Scans und Bewegungsschwelle.
    private static final long SCAN_INTERVAL_MS = 250; // ~4x pro Sekunde
    private static final int MOVE_THRESHOLD = 2;      // Bloecke

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            BlockEspModule mod = (BlockEspModule) find(BlockEspModule.class);
            if (mod == null || !mod.isEnabled()) { cache.clear(); return; }
            if (!mod.hasAnyBlock()) { cache.clear(); return; }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                // Ggf. neu scannen (selten), sonst Cache nutzen.
                maybeScan(client, mod);

                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = client.player.getCameraPosVec(tickDelta);
                // Bei aktiver Freecam die echte Kamera-Position als Offset nutzen.
                if (com.example.pvpclient.freecam.Freecam.isActive()) {
                    cam = com.example.pvpclient.freecam.Freecam.getPos();
                }

                // No-Depth-Linien -> durch Waende sichtbar.
                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());

                int color = mod.getEspColor();
                if ((color >>> 24) == 0) color = 0xFF000000 | color;
                float lineWidth = mod.lineWidth.getFloat();

                // Nur die gecachten Treffer zeichnen (billig).
                for (BlockPos pos : cache) {
                    Box box = new Box(
                            pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
                    VoxelShape shape = VoxelShapes.cuboid(box);
                    VertexRendering.drawOutline(matrices, lines, shape,
                            -cam.x, -cam.y, -cam.z,
                            color, lineWidth);
                }
            } catch (Throwable ignored) {
                // Render darf nie crashen.
            }
        });
    }

    /**
     * Scannt die Welt neu, wenn genug Zeit vergangen ist, der Spieler sich
     * bewegt hat, oder sich Reichweite/Auswahl geaendert haben. Sonst bleibt der
     * Cache unveraendert.
     */
    private static void maybeScan(MinecraftClient client, BlockEspModule mod) {
        int px = (int) Math.floor(client.player.getX());
        int py = (int) Math.floor(client.player.getY());
        int pz = (int) Math.floor(client.player.getZ());
        int range = mod.range.getInt();
        String selection = mod.serializeBlocks();

        long now = System.currentTimeMillis();
        boolean moved = Math.abs(px - lastScanX) >= MOVE_THRESHOLD
                || Math.abs(py - lastScanY) >= MOVE_THRESHOLD
                || Math.abs(pz - lastScanZ) >= MOVE_THRESHOLD;
        boolean changed = range != lastRange || !selection.equals(lastSelection);
        boolean timeUp = (now - lastScanMs) >= SCAN_INTERVAL_MS;

        if (!changed && !moved && !timeUp && !cache.isEmpty()) return;

        lastScanMs = now;
        lastScanX = px; lastScanY = py; lastScanZ = pz;
        lastRange = range; lastSelection = selection;

        scan(client, mod, px, py, pz, range);
    }

    /** Durchsucht den Wuerfel um den Spieler und fuellt den Cache. */
    private static void scan(MinecraftClient client, BlockEspModule mod,
                             int cx, int cy, int cz, int range) {
        cache.clear();

        // Vertikal an der Welthoehe begrenzen, damit wir nicht ins Leere scannen.
        int minY = client.world.getBottomY();
        int maxY = client.world.getTopYInclusive();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -range; dx <= range; dx++) {
            int x = cx + dx;
            for (int dz = -range; dz <= range; dz++) {
                int z = cz + dz;
                int yStart = Math.max(cy - range, minY);
                int yEnd = Math.min(cy + range, maxY);
                for (int y = yStart; y <= yEnd; y++) {
                    pos.set(x, y, z);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    Identifier id = Registries.BLOCK.getId(state.getBlock());
                    if (!mod.isBlockEnabled(id)) continue;
                    cache.add(pos.toImmutable());
                }
            }
        }
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
