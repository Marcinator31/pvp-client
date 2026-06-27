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
import net.minecraft.client.world.ClientWorld;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Block-ESP mit Outlines um ausgewaehlte Bloecke.
 *
 * PERFORMANCE -- der entscheidende Punkt fuer "keine FPS-Drops": Die Welt-Suche
 * laeuft in einem EIGENEN HINTERGRUND-THREAD, nicht im Render-Thread. Der
 * Render-Thread zeichnet nur die fertige Box-Liste, die der Worker bereitstellt.
 * Dadurch hat das (teure) Scannen NULL Einfluss auf die Framerate -- egal wie
 * gross die Reichweite ist.
 *
 * Datenuebergabe: Der Worker baut eine neue Liste und legt sie atomar in eine
 * AtomicReference. Der Render-Thread liest sie nur. Kein gemeinsames
 * Veraendern, daher keine Sperren noetig.
 *
 * Welt-Lesen aus einem Fremd-Thread ist nicht offiziell unterstuetzt, in der
 * Praxis fuer reines Lesen aber tragbar; alle Zugriffe sind in try/catch
 * gekapselt, damit ein seltener Nebenlaeufigkeitsfehler nichts kaputt macht.
 */
public final class BlockEspRenderer {

    // Vom Worker befuelltes, vom Render-Thread gelesenes Ergebnis.
    private static final AtomicReference<List<Box>> RESULT =
            new AtomicReference<>(new ArrayList<>());

    // Maximale Anzahl Outlines (schuetzt sowohl Scan als auch Zeichnen).
    private static final int MAX_RESULTS = 4000;

    private static volatile boolean running = false;
    private static Thread worker;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            BlockEspModule mod = (BlockEspModule) find(BlockEspModule.class);
            if (mod == null || !mod.isEnabled() || !mod.hasAnyBlock()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            // Worker sicherstellen (laeuft dauerhaft, scannt im Hintergrund).
            ensureWorker();

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = client.player.getCameraPosVec(tickDelta);
                if (com.example.pvpclient.freecam.Freecam.isActive()) {
                    cam = com.example.pvpclient.freecam.Freecam.getPos();
                }

                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());

                int color = mod.getEspColor();
                if ((color >>> 24) == 0) color = 0xFF000000 | color;
                float lineWidth = mod.lineWidth.getFloat();

                // Nur die fertige Liste zeichnen -- KEINE Suche hier.
                List<Box> boxes = RESULT.get();
                for (int i = 0; i < boxes.size(); i++) {
                    Box box = boxes.get(i);
                    VoxelShape shape = VoxelShapes.cuboid(box);
                    VertexRendering.drawOutline(matrices, lines, shape,
                            -cam.x, -cam.y, -cam.z,
                            color, lineWidth);
                }

                // Optional: Tracer-Linien von der Sicht zu den Bloecken.
                if (mod.tracersEnabled() && !boxes.isEmpty()) {
                    int tColor = mod.getTracerColor();
                    if ((tColor >>> 24) == 0) tColor = 0xFF000000 | tColor;

                    // Startpunkt: knapp vor der Kamera in Blickrichtung, damit die
                    // Linie wie aus dem Fadenkreuz wirkt. Wir nehmen die Kamera-
                    // Blickrichtung aus yaw/pitch der echten Kamera.
                    Vec3d start = pvpclient$tracerStart(client, cam, tickDelta);

                    org.joml.Matrix4f mat = matrices.peek().getPositionMatrix();
                    float sr = ((tColor >> 16) & 0xFF) / 255.0f;
                    float sg = ((tColor >> 8) & 0xFF) / 255.0f;
                    float sb = (tColor & 0xFF) / 255.0f;
                    float sa = ((tColor >>> 24) & 0xFF) / 255.0f;
                    float tw = mod.lineWidth.getFloat();

                    for (int i = 0; i < boxes.size(); i++) {
                        Box box = boxes.get(i);
                        // Block-Mittelpunkt, relativ zur Kamera.
                        double ex = box.minX + 0.5 - cam.x;
                        double ey = box.minY + 0.5 - cam.y;
                        double ez = box.minZ + 0.5 - cam.z;
                        double sx = start.x - cam.x;
                        double sy = start.y - cam.y;
                        double sz = start.z - cam.z;

                        // Normal = normierte Richtung der Linie (vom Format verlangt).
                        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
                        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (len < 1.0e-4) continue;
                        float nx = (float) (dx / len);
                        float ny = (float) (dy / len);
                        float nz = (float) (dz / len);

                        // WICHTIG: Das Lines-Format verlangt pro Vertex auch
                        // lineWidth -- fehlt es, crasht das Rendering
                        // ("Missing elements in vertex: LineWidth").
                        lines.vertex(mat, (float) sx, (float) sy, (float) sz)
                                .color(sr, sg, sb, sa).normal(nx, ny, nz).lineWidth(tw);
                        lines.vertex(mat, (float) ex, (float) ey, (float) ez)
                                .color(sr, sg, sb, sa).normal(nx, ny, nz).lineWidth(tw);
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /** Startet den Hintergrund-Worker einmalig. */
    private static void ensureWorker() {
        if (running) return;
        running = true;
        worker = new Thread(BlockEspRenderer::workerLoop, "pvpclient-blockesp");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Endlosschleife im Hintergrund: scannt die Welt um den Spieler und legt das
     * Ergebnis ab. Laeuft mit kurzer Pause zwischen den Durchlaeufen, damit der
     * Thread nicht durchdreht.
     */
    private static void workerLoop() {
        while (true) {
            try {
                Thread.sleep(60); // ~16 Scans/Sekunde maximal

                MinecraftClient client = MinecraftClient.getInstance();
                BlockEspModule mod = (BlockEspModule) find(BlockEspModule.class);
                if (client == null || mod == null || !mod.isEnabled()
                        || !mod.hasAnyBlock()) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }
                ClientWorld world = client.world;
                if (world == null || client.player == null) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }

                int cx = (int) Math.floor(client.player.getX());
                int cy = (int) Math.floor(client.player.getY());
                int cz = (int) Math.floor(client.player.getZ());
                int range = mod.range.getInt();

                List<Box> found = scan(world, mod, cx, cy, cz, range);
                RESULT.set(found); // atomar uebergeben
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable t) {
                // Nebenlaeufigkeitsfehler o.ae. -> Durchlauf ueberspringen.
            }
        }
    }

    /**
     * Durchsucht den Bereich RINGFOERMIG von innen nach aussen und veroeffentlicht
     * das Zwischenergebnis nach jedem Ring. Dadurch erscheinen nahe Bloecke quasi
     * sofort, ferne kommen Ring fuer Ring nach -- statt erst nach einem
     * kompletten (bei grosser Reichweite sekundenlangen) Scan alles auf einmal.
     */
    private static List<Box> scan(ClientWorld world, BlockEspModule mod,
                                  int cx, int cy, int cz, int range) {
        List<Box> out = new ArrayList<>();

        int minY = world.getBottomY();
        int maxY = world.getTopYInclusive();
        int vRange = Math.min(range, 64); // vertikal begrenzen (Volumen!)
        int yStart = Math.max(cy - vRange, minY);
        int yEnd = Math.min(cy + vRange, maxY);

        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Ring 0 = nur die Mittelspalte, dann immer groessere Quadrat-Ringe.
        for (int r = 0; r <= range; r++) {
            // Alle (dx,dz) auf dem Rand des Quadrats mit "Radius" r abklappern.
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Nur den RAND des aktuellen Rings (sonst doppelt).
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;

                    int x = cx + dx;
                    int z = cz + dz;
                    for (int y = yStart; y <= yEnd; y++) {
                        pos.set(x, y, z);
                        BlockState state;
                        try {
                            state = world.getBlockState(pos);
                        } catch (Throwable t) {
                            continue; // Chunk evtl. gerade entladen
                        }
                        if (state.isAir()) continue;
                        Identifier id = Registries.BLOCK.getId(state.getBlock());
                        if (!mod.isBlockEnabled(id)) continue;
                        out.add(new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0));
                        if (out.size() >= MAX_RESULTS) return out;
                    }
                }
            }
            // Nach jedem Ring das bisherige Ergebnis sichtbar machen -> nahe
            // Bloecke erscheinen sofort, der Rest fuellt sich auf. Nicht nach
            // JEDEM Ring (das waere bei grosser Reichweite viel Kopierarbeit),
            // sondern alle paar Ringe -- das reicht fuers Gefuehl von "instant".
            if ((r & 7) == 0) {
                RESULT.set(new ArrayList<>(out));
            }
        }
        return out;
    }

    /**
     * Startpunkt der Tracer: ein kleines Stueck vor der Kamera in Blickrichtung.
     * So scheinen die Linien aus dem Fadenkreuz zu kommen statt aus dem Auge.
     * Bei aktiver Freecam nutzen wir deren Blickrichtung.
     */
    private static Vec3d pvpclient$tracerStart(MinecraftClient client, Vec3d cam,
                                               float tickDelta) {
        float yaw, pitch;
        if (com.example.pvpclient.freecam.Freecam.isActive()) {
            yaw = com.example.pvpclient.freecam.Freecam.getYaw();
            pitch = com.example.pvpclient.freecam.Freecam.getPitch();
        } else {
            yaw = client.player.getYaw(tickDelta);
            pitch = client.player.getPitch(tickDelta);
        }
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        // Blickrichtungs-Vektor (Minecraft-Konvention).
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        // 0.5 Bloecke vor die Kamera setzen.
        return new Vec3d(cam.x + fx * 0.5, cam.y + fy * 0.5, cam.z + fz * 0.5);
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
