package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.SusChunksModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sus Chunks: faerbt Chunks als Heatmap nach Spieler-Aktivitaet.
 *
 * Hintergrund-Thread berechnet pro geladenem Chunk einen Score:
 *   Score = Container * 3 + sonstige Block-Entities * 1
 * (Container zaehlen staerker, weil sie der deutlichste Base-Indikator sind.)
 * Chunks ueber dem Mindest-Score bekommen eine vertikale Saeulen-Outline, deren
 * Farbe von gruen (wenig) ueber gelb nach rot (viel) geht.
 */
public final class SusChunks {

    private static final class ChunkMark {
        final Box box;
        final int color;
        ChunkMark(Box box, int color) { this.box = box; this.color = color; }
    }

    private static final AtomicReference<List<ChunkMark>> RESULT =
            new AtomicReference<>(new ArrayList<>());
    private static final int CHUNK_RADIUS = 10;

    private static volatile boolean running = false;
    private static Thread worker;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            SusChunksModule mod = (SusChunksModule) find(SusChunksModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            ensureWorker();

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = EspRender.cameraOffset(client, tickDelta);
                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());

                List<ChunkMark> marks = RESULT.get();
                for (int i = 0; i < marks.size(); i++) {
                    ChunkMark m = marks.get(i);
                    EspRender.drawBox(matrices, lines, m.box, cam, m.color, 2.0f);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void ensureWorker() {
        if (running) return;
        running = true;
        worker = new Thread(SusChunks::workerLoop, "pvpclient-suschunks");
        worker.setDaemon(true);
        worker.start();
    }

    private static void workerLoop() {
        while (true) {
            try {
                Thread.sleep(400);

                MinecraftClient client = MinecraftClient.getInstance();
                SusChunksModule mod = (SusChunksModule) find(SusChunksModule.class);
                if (client == null || mod == null || !mod.isEnabled()) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }
                ClientWorld world = client.world;
                if (world == null || client.player == null) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }

                int minScore = mod.getMinScore();
                int maxScore = Math.max(mod.getMaxScore(), minScore + 1);
                int minY = world.getBottomY();
                int maxY = world.getTopYInclusive();

                int pcx = client.player.getBlockX() >> 4;
                int pcz = client.player.getBlockZ() >> 4;
                List<ChunkMark> marks = new ArrayList<>();

                for (int dcx = -CHUNK_RADIUS; dcx <= CHUNK_RADIUS; dcx++) {
                    for (int dcz = -CHUNK_RADIUS; dcz <= CHUNK_RADIUS; dcz++) {
                        int ccx = pcx + dcx;
                        int ccz = pcz + dcz;
                        int score = scoreChunk(world, ccx, ccz);
                        if (score < minScore) continue;

                        // Score auf 0..1 normieren -> Heatmap-Farbe.
                        float t = (float) (score - minScore) / (float) (maxScore - minScore);
                        if (t > 1f) t = 1f;
                        int color = heatColor(t);

                        // Vertikale Chunk-Saeule (etwas schmaler als 16, damit
                        // benachbarte Chunks sich optisch trennen).
                        double x0 = ccx << 4, z0 = ccz << 4;
                        Box box = new Box(x0 + 0.5, minY, z0 + 0.5,
                                x0 + 15.5, maxY, z0 + 15.5);
                        marks.add(new ChunkMark(box, color));
                    }
                }

                RESULT.set(marks);
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable t) {
                // Durchlauf ueberspringen.
            }
        }
    }

    /** Aktivitaets-Score eines Chunks aus seinen Block-Entities. */
    private static int scoreChunk(ClientWorld world, int chunkX, int chunkZ) {
        BlockPos cpos = new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
        WorldChunk chunk;
        try { chunk = world.getWorldChunk(cpos); }
        catch (Throwable t) { return 0; }
        if (chunk == null) return 0;

        Map<BlockPos, BlockEntity> bes;
        try { bes = chunk.getBlockEntities(); }
        catch (Throwable t) { return 0; }
        if (bes == null || bes.isEmpty()) return 0;

        int score = 0;
        for (BlockEntity be : bes.values()) {
            // Container zaehlen stark, alles andere (Oefen, Schilder, ...) leicht.
            if (be instanceof Inventory) score += 3;
            else score += 1;
        }
        return score;
    }

    /** Heatmap: t=0 -> gruen, t=0.5 -> gelb, t=1 -> rot. ARGB mit fixem Alpha. */
    private static int heatColor(float t) {
        int r, g;
        if (t < 0.5f) {
            // gruen -> gelb
            r = (int) (255 * (t / 0.5f));
            g = 255;
        } else {
            // gelb -> rot
            r = 255;
            g = (int) (255 * (1f - (t - 0.5f) / 0.5f));
        }
        int a = 0xC0; // leicht transparent
        return (a << 24) | (r << 16) | (g << 8);
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
