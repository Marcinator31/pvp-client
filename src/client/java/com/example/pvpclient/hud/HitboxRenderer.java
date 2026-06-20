package com.example.pvpclient.hud;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet Entity-Hitboxen im 3D-Raum.
 *
 * NEU gegenueber den HUD-Modulen: Das hier ist KEIN 2D-Overlay. Wir
 * zeichnen Boxen im Weltraum, also brauchen wir WorldRenderEvents statt
 * HudRenderCallback. Diese Events wurden in Fabric fuer 1.21.10/1.21.11
 * gerade wieder eingefuehrt -- daher passend zu deiner Version.
 *
 * Ablauf pro Frame:
 *   1) Welt-Render-Event feuert (nach den Entities).
 *   2) Wir laufen ueber alle geladenen Entities.
 *   3) Pro Entity Kategorie bestimmen (Spieler/Tier/Gegner/Sonstige).
 *   4) Ist die Kategorie aktiviert? -> Box in ihrer Farbe zeichnen.
 *
 * WICHTIG -- ehrliche Einordnung zum Rendering:
 * Das 3D-Linienzeichnen ist der fummeligste Teil und die API dafuer
 * (VertexConsumerProvider / RenderLayer / MatrixStack) aendert sich
 * zwischen MC-Versionen am haeufigsten. Die Kategorie-Logik unten ist
 * stabil und korrekt; den eigentlichen drawBox-Aufruf musst du ggf. an
 * die exakte 1.21.11-Render-API anpassen. Die Stelle ist klar markiert.
 */
public final class HitboxRenderer {

    public static void register() {
        // AFTER_ENTITIES: zeichnen, nachdem die Entities da sind, damit
        // die Boxen darueber liegen.
        WorldRenderEvents.AFTER_ENTITIES.register(HitboxRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        HitboxModule module = (HitboxModule) find();
        if (module == null || !module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        // Kamera-Position -- Hitboxen werden relativ dazu gezeichnet.
        Vec3d cam = context.camera().getPos();

        for (Entity entity : client.world.getEntities()) {
            // Eigenen Spieler ueberspringen (sonst Box mitten im Bild).
            if (entity == client.player) continue;

            Integer color = colorFor(entity, module);
            if (color == null) continue; // Kategorie aus -> nicht zeichnen

            // Echte Vanilla-Hitbox dieses Entities holen.
            Box box = entity.getBoundingBox();

            // Box relativ zur Kamera verschieben.
            Box rel = box.offset(-cam.x, -cam.y, -cam.z);

            drawBox(context, rel, color, module.lineWidth.getFloat());
        }
    }

    /**
     * Bestimmt die Farbe fuer ein Entity anhand seiner Kategorie.
     * Gibt null zurueck, wenn die Kategorie deaktiviert ist.
     */
    private static Integer colorFor(Entity entity, HitboxModule m) {
        if (entity instanceof PlayerEntity) {
            return m.showPlayers.get() ? m.playerColor.get() : null;
        }
        if (entity instanceof HostileEntity) {
            return m.showHostiles.get() ? m.hostileColor.get() : null;
        }
        if (entity instanceof PassiveEntity) {
            return m.showAnimals.get() ? m.animalColor.get() : null;
        }
        // Alles andere (Items, Pfeile, Boote, ...) zaehlt als "Sonstige".
        // LivingEntity-Check faengt seltene Faelle ab, die weder hostile
        // noch passive sind.
        if (entity instanceof LivingEntity) {
            // Lebende, aber nicht eindeutig zuordenbare -> als Gegner werten.
            return m.showHostiles.get() ? m.hostileColor.get() : null;
        }
        return m.showMisc.get() ? m.miscColor.get() : null;
    }

    /**
     * Zeichnet eine Box als Drahtgitter.
     *
     * ====== HIER IST DIE VERSIONSABHAENGIGE STELLE ======
     * Der Weg, Linien in 3D zu zeichnen, ist in 1.21.11:
     *   - context.matrixStack() fuer die Transformation
     *   - context.consumers() (VertexConsumerProvider) fuer die Vertices
     *   - RenderLayer.getLines() als Linien-Layer
     *   - VertexRendering.drawBox(...) ODER manuell die 12 Kanten
     *
     * Da sich genau diese API zwischen Versionen verschiebt, hier das
     * Prinzip als Pseudocode. Beim Build zeigt dir IntelliJ die exakten
     * Methodennamen. Such-Stichworte: "fabric 1.21 render box worldrenderevents".
     */
    private static void drawBox(WorldRenderContext ctx, Box box, int argb, float width) {
        // float a = ((argb >> 24) & 0xFF) / 255f;
        // float r = ((argb >> 16) & 0xFF) / 255f;
        // float g = ((argb >> 8)  & 0xFF) / 255f;
        // float b = ( argb        & 0xFF) / 255f;
        //
        // MatrixStack matrices = ctx.matrixStack();
        // VertexConsumerProvider consumers = ctx.consumers();
        // VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());
        //
        // VertexRendering.drawBox(
        //     matrices, vc,
        //     box.minX, box.minY, box.minZ,
        //     box.maxX, box.maxY, box.maxZ,
        //     r, g, b, a
        // );
        //
        // (Linienbreite ueber RenderSystem.lineWidth(width) vor dem Zeichnen.)
    }

    private static Object find() {
        for (var mod : ModuleManager.INSTANCE.getModules()) {
            if (mod instanceof HitboxModule) return mod;
        }
        return null;
    }
}
