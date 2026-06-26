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
 * Zwei Performance-Stellschrauben, beide wichtig:
 *
 * 1) SUCHE inkrementell: Bei grosser Reichweite ist ein voller Scan riesig
 *    (range=512 -> >100 Mio Bloecke). Wir pruefen pro Frame nur ein Budget und
 *    verteilen den Sweep ueber viele Frames.
 *
 * 2) ZEICHNEN begrenzt + vorberechnet: Das eigentliche Ruckeln bei Max-Distanz
 *    kommt vom ZEICHNEN zehntausender Boxen pro Frame. Deshalb:
 *      - Wir cachen fertige Box-Objekte (kein new Box / VoxelShapes.cuboid je
 *        Frame -- das wird einmal beim Sweep gebaut).
 *      - Wir begrenzen die Anzahl gezeichneter Boxen (MAX_RENDER). Bei sehr
 *        haeufigen Bloecken (Stein) ueber 512 Bloecke waeren es sonst
 *        Hunderttausende -- voellig unbrauchbar. Mit Limit bleibt es fluessig.
 */
public final class BlockEspRenderer {

    // Fertige, sichtbare Box-Outlines (jeden Frame gezeichnet).
    private static final List<Box> visible = new ArrayList<>();
    // Boxen des laufenden Sweeps.
    private static List<Box> working = new ArrayList<>();

    // Block-Lookups pro Frame (Suche). Reine Lookups sind billig.
    private static final int BUDGET_PER_FRAME = 300_000;

    // Maximale Anzahl gleichzeitig gezeichneter Outlines. Schuetzt die FPS:
    // mehr als das bringt visuell nichts (alles zugepflastert) und kostet massiv.
    private static final int MAX_RENDER = 3000;

    // Sweep-Zustand.
    private static boolean sweeping = false;
    private static int originX, originY, originZ;
    private static int sweepRange;
    private static int curDx, curDz;
    private static String sweepSelection = "";

    private static int lastDoneX, lastDoneY, lastDoneZ;
    private static boolean haveResult = false;
    private static final int RESTART_MOVE = 4;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            BlockEspModule mod = (BlockEspModule) find(BlockEspModule.class);
            if (mod == null || !mod.isEnabled() || !mod.hasAnyBlock()) {
                visible.clear(); working.clear(); sweeping = false; haveResult = false;
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                stepScan(client, mod);

                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = client.player.getCameraPosVec(tickDelta);
                if (com.example.pvpclient.freecam.Freecam.isActive()) {
                    cam = com.example.pvpclient.freecam.Freecam.getPos();
                }

                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());

                int color = mod.getEspColor();
                if ((color >>> 24) == 0) color = 0xFF000000 | color;
                float lineWidth = mod.lineWidth.getFloat();

                // Nur fertige, vorberechnete Boxen zeichnen (billig).
                for (int i = 0; i < visible.size(); i++) {
                    Box box = visible.get(i);
                    VoxelShape shape = VoxelShapes.cuboid(box);
                    VertexRendering.drawOutline(matrices, lines, shape,
                            -cam.x, -cam.y, -cam.z,
                            color, lineWidth);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static void stepScan(MinecraftClient client, BlockEspModule mod) {
        int px = (int) Math.floor(client.player.getX());
        int py = (int) Math.floor(client.player.getY());
        int pz = (int) Math.floor(client.player.getZ());
        int range = mod.range.getInt();
        String selection = mod.serializeBlocks();

        if (!sweeping) {
            boolean moved = Math.abs(px - lastDoneX) >= RESTART_MOVE
                    || Math.abs(py - lastDoneY) >= RESTART_MOVE
                    || Math.abs(pz - lastDoneZ) >= RESTART_MOVE;
            boolean changed = !selection.equals(sweepSelection) || range != sweepRange;
            if (!haveResult || moved || changed) {
                sweeping = true;
                originX = px; originY = py; originZ = pz;
                sweepRange = range;
                sweepSelection = selection;
                curDx = -range; curDz = -range;
                working = new ArrayList<>();
            } else {
                return;
            }
        }

        int minY = client.world.getBottomY();
        int maxY = client.world.getTopYInclusive();
        int vRange = Math.min(sweepRange, 64);
        int yStart = Math.max(originY - vRange, minY);
        int yEnd = Math.min(originY + vRange, maxY);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        int budget = BUDGET_PER_FRAME;

        while (budget > 0) {
            int x = originX + curDx;
            int z = originZ + curDz;
            for (int y = yStart; y <= yEnd; y++) {
                pos.set(x, y, z);
                BlockState state = client.world.getBlockState(pos);
                if (!state.isAir()) {
                    Identifier id = Registries.BLOCK.getId(state.getBlock());
                    if (mod.isBlockEnabled(id)) {
                        // Box gleich beim Finden bauen (nicht jeden Frame neu).
                        // Sobald das Render-Limit erreicht ist, hoeren wir auf zu
                        // sammeln -- mehr koennen wir eh nicht sinnvoll zeichnen.
                        if (working.size() < MAX_RENDER) {
                            working.add(new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0));
                        }
                    }
                }
            }
            budget -= (yEnd - yStart + 1);

            // Wenn das Limit voll ist, Sweep sofort abschliessen -- weiteres
            // Suchen wuerde nur Zeit kosten, ohne mehr zeichnen zu koennen.
            if (working.size() >= MAX_RENDER) {
                finishSweep();
                return;
            }

            curDz++;
            if (curDz > sweepRange) {
                curDz = -sweepRange;
                curDx++;
                if (curDx > sweepRange) {
                    finishSweep();
                    return;
                }
            }
        }
    }

    private static void finishSweep() {
        visible.clear();
        visible.addAll(working);
        working = new ArrayList<>();
        sweeping = false;
        haveResult = true;
        lastDoneX = originX; lastDoneY = originY; lastDoneZ = originZ;
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
