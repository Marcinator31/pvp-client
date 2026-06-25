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
 * PERFORMANCE -- der Knackpunkt: Bei grosser Reichweite ist ein vollstaendiger
 * Scan riesig (range=128 -> ~17 Mio Bloecke). Wuerde man das in einem Frame
 * machen, bricht die FPS ein. Deshalb scannen wir INKREMENTELL:
 *
 *   - Pro Frame wird nur eine begrenzte Anzahl Bloecke geprueft (BUDGET).
 *   - Der Scan laeuft als Spalten-Sweep ueber viele Frames; Treffer sammeln wir
 *     in einem "in Arbeit"-Puffer.
 *   - Ist der Sweep komplett, wird der Puffer zum sichtbaren Cache, und ein
 *     neuer Sweep beginnt (an der aktuellen Spielerposition).
 *
 * Gezeichnet wird jeden Frame nur der fertige Cache -- das ist billig und die
 * Frame-Zeit bleibt konstant, egal wie gross die Reichweite ist.
 */
public final class BlockEspRenderer {

    // Fertige, sichtbare Treffer (werden jeden Frame gezeichnet).
    private static final List<BlockPos> visible = new ArrayList<>();
    // Treffer des laufenden Sweeps (noch nicht fertig).
    private static List<BlockPos> working = new ArrayList<>();

    // Wie viele Bloecke pro Frame maximal geprueft werden (Zeit-Budget).
    private static final int BUDGET_PER_FRAME = 40_000;

    // Sweep-Zustand: aktuelle Spalte (dx, dz) relativ zum Sweep-Zentrum.
    private static boolean sweeping = false;
    private static int originX, originY, originZ; // Zentrum des aktuellen Sweeps
    private static int sweepRange;
    private static int curDx, curDz;              // Fortschritt im Spalten-Sweep
    private static String sweepSelection = "";

    // Wann der letzte Sweep fertig wurde + Bewegungsschwelle fuer Neustart.
    private static int lastDoneX, lastDoneY, lastDoneZ;
    private static boolean haveResult = false;
    private static final int RESTART_MOVE = 4; // Bloecke

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
                // Scan-Fortschritt (inkrementell, begrenztes Budget pro Frame).
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

                for (BlockPos pos : visible) {
                    Box box = new Box(
                            pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
                    VoxelShape shape = VoxelShapes.cuboid(box);
                    VertexRendering.drawOutline(matrices, lines, shape,
                            -cam.x, -cam.y, -cam.z,
                            color, lineWidth);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Treibt den inkrementellen Scan voran: startet bei Bedarf einen neuen Sweep
     * und prueft pro Frame hoechstens BUDGET_PER_FRAME Bloecke.
     */
    private static void stepScan(MinecraftClient client, BlockEspModule mod) {
        int px = (int) Math.floor(client.player.getX());
        int py = (int) Math.floor(client.player.getY());
        int pz = (int) Math.floor(client.player.getZ());
        int range = mod.range.getInt();
        String selection = mod.serializeBlocks();

        // Neuen Sweep starten? (kein Sweep laeuft UND (noch kein Ergebnis,
        // Auswahl/Reichweite geaendert, oder Spieler hat sich genug bewegt)).
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
                return; // nichts zu tun, Cache bleibt
            }
        }

        int minY = client.world.getBottomY();
        int maxY = client.world.getTopYInclusive();
        int yStart = Math.max(originY - sweepRange, minY);
        int yEnd = Math.min(originY + sweepRange, maxY);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        int budget = BUDGET_PER_FRAME;

        // Spalten-Sweep: pro Spalte (dx,dz) alle y pruefen, bis Budget leer ist.
        while (budget > 0) {
            int x = originX + curDx;
            int z = originZ + curDz;
            for (int y = yStart; y <= yEnd; y++) {
                pos.set(x, y, z);
                BlockState state = client.world.getBlockState(pos);
                if (!state.isAir()) {
                    Identifier id = Registries.BLOCK.getId(state.getBlock());
                    if (mod.isBlockEnabled(id)) {
                        working.add(pos.toImmutable());
                    }
                }
            }
            budget -= (yEnd - yStart + 1);

            // Naechste Spalte.
            curDz++;
            if (curDz > sweepRange) {
                curDz = -sweepRange;
                curDx++;
                if (curDx > sweepRange) {
                    // Sweep fertig -> Ergebnis uebernehmen.
                    visible.clear();
                    visible.addAll(working);
                    working = new ArrayList<>();
                    sweeping = false;
                    haveResult = true;
                    lastDoneX = originX; lastDoneY = originY; lastDoneZ = originZ;
                    return;
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
