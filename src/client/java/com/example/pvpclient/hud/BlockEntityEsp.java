package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.ContainerEspModule;
import com.example.pvpclient.module.modules.SpawnerEspModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
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
 * ESP fuer Block-Entities: Container (alles mit Inventar) und Mob-Spawner.
 *
 * Wie beim Block-ESP laeuft die Suche in einem HINTERGRUND-THREAD (stabile FPS),
 * der die geladenen Chunks durchgeht und die passenden Block-Entities sammelt.
 * Der Render-Thread zeichnet nur die fertigen Boxen (+ optional Tracer).
 *
 * Container und Spawner teilen sich Scan und Renderer; pro Treffer merken wir
 * uns Position und Typ, damit wir mit der jeweils eingestellten Farbe zeichnen.
 */
public final class BlockEntityEsp {

    private static final class Hit {
        final Box box;
        final Vec3d center;
        final boolean spawner; // true = Spawner, false = Container
        Hit(Box box, Vec3d center, boolean spawner) {
            this.box = box; this.center = center; this.spawner = spawner;
        }
    }

    private static final AtomicReference<List<Hit>> RESULT =
            new AtomicReference<>(new ArrayList<>());
    private static final int CHUNK_RADIUS = 8;
    private static final int MAX_RESULTS = 3000;

    private static volatile boolean running = false;
    private static Thread worker;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            ContainerEspModule cont = (ContainerEspModule) find(ContainerEspModule.class);
            SpawnerEspModule spawn = (SpawnerEspModule) find(SpawnerEspModule.class);
            boolean contOn = cont != null && cont.isEnabled();
            boolean spawnOn = spawn != null && spawn.isEnabled();
            if (!contOn && !spawnOn) return;

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

                int contColor = contOn ? cont.getColor() : 0;
                if ((contColor >>> 24) == 0) contColor |= 0xFF000000;
                int spawnColor = spawnOn ? spawn.getColor() : 0;
                if ((spawnColor >>> 24) == 0) spawnColor |= 0xFF000000;

                Vec3d start = EspRender.tracerStart(client, cam, tickDelta);
                org.joml.Matrix4f mat = matrices.peek().getPositionMatrix();

                List<Hit> hits = RESULT.get();
                for (int i = 0; i < hits.size(); i++) {
                    Hit h = hits.get(i);
                    // Je nach Typ pruefen, ob das Modul an ist + Farbe waehlen.
                    if (h.spawner && !spawnOn) continue;
                    if (!h.spawner && !contOn) continue;
                    int color = h.spawner ? spawnColor : contColor;
                    EspRender.drawBox(matrices, lines, h.box, cam, color, 2.0f);

                    boolean tracer = h.spawner ? spawn.tracerEnabled() : cont.tracerEnabled();
                    if (tracer) {
                        EspRender.drawTracer(mat, lines, start, h.center, cam, color, 2.0f);
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void ensureWorker() {
        if (running) return;
        running = true;
        worker = new Thread(BlockEntityEsp::workerLoop, "pvpclient-blockentity-esp");
        worker.setDaemon(true);
        worker.start();
    }

    private static void workerLoop() {
        while (true) {
            try {
                Thread.sleep(120);

                MinecraftClient client = MinecraftClient.getInstance();
                ContainerEspModule cont = (ContainerEspModule) find(ContainerEspModule.class);
                SpawnerEspModule spawn = (SpawnerEspModule) find(SpawnerEspModule.class);
                boolean contOn = cont != null && cont.isEnabled();
                boolean spawnOn = spawn != null && spawn.isEnabled();
                if (!contOn && !spawnOn) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }
                ClientWorld world = client.world;
                if (world == null || client.player == null) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }

                int pcx = client.player.getBlockX() >> 4;
                int pcz = client.player.getBlockZ() >> 4;
                List<Hit> found = new ArrayList<>();

                outer:
                for (int dcx = -CHUNK_RADIUS; dcx <= CHUNK_RADIUS; dcx++) {
                    for (int dcz = -CHUNK_RADIUS; dcz <= CHUNK_RADIUS; dcz++) {
                        BlockPos cpos = new BlockPos(((pcx + dcx) << 4) + 8, 0,
                                ((pcz + dcz) << 4) + 8);
                        WorldChunk chunk;
                        try { chunk = world.getWorldChunk(cpos); }
                        catch (Throwable t) { continue; }
                        if (chunk == null) continue;

                        Map<BlockPos, BlockEntity> bes;
                        try { bes = chunk.getBlockEntities(); }
                        catch (Throwable t) { continue; }
                        if (bes == null || bes.isEmpty()) continue;

                        for (Map.Entry<BlockPos, BlockEntity> e : bes.entrySet()) {
                            BlockEntity be = e.getValue();
                            BlockPos p = e.getKey();
                            boolean isSpawner = be instanceof MobSpawnerBlockEntity;
                            boolean isContainer = be instanceof Inventory;

                            if (isSpawner && spawnOn) {
                                found.add(makeHit(p, true));
                            } else if (isContainer && contOn) {
                                found.add(makeHit(p, false));
                            }
                            if (found.size() >= MAX_RESULTS) break outer;
                        }
                    }
                }

                RESULT.set(found);
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable t) {
                // Durchlauf ueberspringen.
            }
        }
    }

    private static Hit makeHit(BlockPos p, boolean spawner) {
        Box box = new Box(p.getX(), p.getY(), p.getZ(),
                p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0);
        Vec3d center = new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        return new Hit(box, center, spawner);
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
