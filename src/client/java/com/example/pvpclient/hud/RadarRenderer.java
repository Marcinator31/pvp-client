package com.example.pvpclient.hud;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.RadarModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Zeichnet den runden Entity-Radar.
 *
 *  - Kreis-Rahmen + halbtransparenter Hintergrund.
 *  - Spieler-Pfeil in der Mitte (zeigt nach oben = Blickrichtung).
 *  - Entities als farbige Punkte, nach ihrer relativen Lage projiziert.
 *  - Bei Spielern: Kopf, Name und Entfernung in Metern.
 *
 * Die Projektion dreht die Welt so, dass die Blickrichtung des Spielers
 * immer nach oben zeigt (verifizierte Mathematik, siehe unten).
 */
public final class RadarRenderer {

    // Punktfarben je Typ.
    private static final int COL_PLAYER  = 0xFFFF5555; // rot
    private static final int COL_HOSTILE = 0xFFFFAA00; // orange
    private static final int COL_ANIMAL  = 0xFF55FF55; // gruen
    private static final int COL_ITEM    = 0xFF55FFFF; // cyan

    public static void render(DrawContext context, MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        RadarModule mod = (RadarModule) module();
        if (mod == null || !mod.isEnabled()) return;

        double scale = mod.scale.get();
        int diameter = (int) Math.round(mod.baseDiameter() * scale);
        int radius = diameter / 2;

        int left = mod.x.getInt();
        int top = mod.y.getInt();
        int cx = left + radius; // Mittelpunkt X
        int cy = top + radius;  // Mittelpunkt Y

        int frame = mod.color.get();

        // 1) Hintergrund (halbtransparentes Quadrat hinter dem Kreis).
        context.fill(left, top, left + diameter, top + diameter, 0x80000000);

        // 2) Kreis-Rahmen aus vielen kurzen Segmenten zeichnen.
        drawCircle(context, cx, cy, radius, frame);
        // Innenkreis (halber Radius) als Hilfslinie.
        drawCircle(context, cx, cy, radius / 2, 0x60FFFFFF);

        // 3) Reichweite + Blickrichtung des Spielers.
        double maxRange = mod.range.get();        // Bloecke, die der Rand bedeutet
        float yaw = client.player.getYaw();
        double yawRad = Math.toRadians(yaw);

        // Basisvektoren fuer "Blickrichtung = oben" (verifizierte Formel V2):
        //   rechts im Radar = -cos/-sin, oben = Blickrichtung.
        double rightX = -Math.cos(yawRad), rightZ = -Math.sin(yawRad);
        double fwdX   = -Math.sin(yawRad), fwdZ   =  Math.cos(yawRad);

        double px = client.player.getX();
        double pz = client.player.getZ();

        // 4) Entities durchgehen und als Punkte zeichnen.
        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;

            int dotColor;
            boolean isPlayer = e instanceof PlayerEntity;
            if (isPlayer) {
                if (!mod.showPlayers.get()) continue;
                dotColor = COL_PLAYER;
            } else if (isHostile(e)) {
                if (!mod.showHostiles.get()) continue;
                dotColor = COL_HOSTILE;
            } else if (isAnimal(e)) {
                if (!mod.showAnimals.get()) continue;
                dotColor = COL_ANIMAL;
            } else if (e instanceof ItemEntity) {
                if (!mod.showItems.get()) continue;
                dotColor = COL_ITEM;
            } else {
                continue; // andere Typen ignorieren
            }

            double dx = e.getX() - px;
            double dz = e.getZ() - pz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > maxRange) continue; // ausserhalb der Reichweite

            // Projektion auf den Radar.
            double sRight = dx * rightX + dz * rightZ;
            double sUp    = dx * fwdX   + dz * fwdZ;

            // Auf Radarradius skalieren (Distanz/maxRange * radius).
            double f = (radius) / maxRange;
            int dotX = (int) Math.round(cx + sRight * f);
            int dotY = (int) Math.round(cy - sUp * f); // minus: +screenY = unten

            // Sicherheitshalber in den Kreis clampen (sollte schon passen).
            // 5) Punkt zeichnen (kleines Quadrat).
            int s = Math.max(2, (int) Math.round(2 * scale));
            context.fill(dotX - s, dotY - s, dotX + s, dotY + s, dotColor);

            // 6) Bei Spielern: Kopf, Name, Entfernung.
            if (isPlayer && mod.playerDetails.get()
                    && e instanceof AbstractClientPlayerEntity acp) {
                drawPlayerInfo(context, client, acp, dotX, dotY, dist);
            }
        }

        // 7) Spieler-Pfeil in der Mitte (zeigt nach oben).
        drawArrow(context, cx, cy, Math.max(3, (int) Math.round(4 * scale)), 0xFFFFFFFF);
    }

    /** Zeichnet Kopf + Name + Entfernung neben einem Spieler-Punkt. */
    private static void drawPlayerInfo(DrawContext context, MinecraftClient client,
                                       AbstractClientPlayerEntity player,
                                       int dotX, int dotY, double dist) {
        try {
            int headSize = 8;
            int hx = dotX + 4;
            int hy = dotY - headSize / 2;

            // In 1.21.11 liegt getSkinTextures() nicht mehr direkt auf dem
            // Spieler, sondern auf dem PlayerListEntry. Den holen wir oeffentlich
            // ueber den NetworkHandler anhand der Spieler-UUID. Die UUID nehmen
            // wir aus dem GameProfile (getGameProfile().getId()) -- das ist
            // robuster als entity.getUuid().
            var handler = client.getNetworkHandler();
            if (handler != null) {
                var entry = handler.getPlayerListEntry(player.getGameProfile().getId());
                if (entry != null) {
                    var skin = entry.getSkinTextures();
                    // Kopf zeichnen (inkl. Hut-Overlay).
                    PlayerSkinDrawer.draw(context, skin, hx, hy, headSize, 0xFFFFFFFF);
                }
            }

            // Name + Entfernung rechts neben dem Kopf (auch ohne Skin sinnvoll).
            String name = player.getName().getString();
            String info = name + " " + (int) Math.round(dist) + "m";
            context.drawTextWithShadow(client.textRenderer, Text.literal(info),
                    hx + headSize + 2, dotY - 4, 0xFFFFFFFF);
        } catch (Throwable ignored) {
            // Skin/Name nicht verfuegbar -> nur der Punkt bleibt.
        }
    }

    /** Zeichnet einen Kreis-Umriss aus kurzen Liniensegmenten. */
    private static void drawCircle(DrawContext context, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int segments = Math.max(24, r * 2);
        double step = (Math.PI * 2) / segments;
        int prevX = cx + r, prevY = cy;
        for (int i = 1; i <= segments; i++) {
            double a = step * i;
            int x = (int) Math.round(cx + Math.cos(a) * r);
            int y = (int) Math.round(cy + Math.sin(a) * r);
            drawLine(context, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    /** Zeichnet eine duenne Linie zwischen zwei Punkten (Bresenham-artig). */
    private static void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int guard = 0;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            if (++guard > 5000) break; // Sicherheitsbremse
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    /** Zeichnet einen kleinen nach oben zeigenden Pfeil (Spieler-Marker). */
    private static void drawArrow(DrawContext context, int cx, int cy, int size, int color) {
        // Einfacher Pfeil: vertikale Linie + zwei kurze Schenkel oben.
        drawLine(context, cx, cy - size, cx, cy + size, color);
        drawLine(context, cx, cy - size, cx - size / 2, cy - size / 2, color);
        drawLine(context, cx, cy - size, cx + size / 2, cy - size / 2, color);
    }

    private static boolean isHostile(Entity e) {
        // Monster-Interface faengt auch Slimes/Magma-Wuerfel.
        return e instanceof net.minecraft.entity.mob.Monster;
    }

    private static boolean isAnimal(Entity e) {
        return e instanceof AnimalEntity || e instanceof PassiveEntity;
    }

    private static com.example.pvpclient.module.Module module() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof RadarModule) return m;
        }
        return null;
    }
}
