package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HealthIndicatorModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Zeichnet die Lebenspunkte ueber Entities als zur Kamera ausgerichteten Text
 * (Billboard), wie ein Nametag. Drei Modi (Herzen / Zahl+Herz / Zahl),
 * Skalierung und Farbe kommen aus dem HealthIndicatorModule.
 *
 * Technik (verifiziert gegen 1.21.11-Yarn, eng an HitboxRenderer angelehnt):
 *   - WorldRenderEvents.AFTER_ENTITIES liefert MatrixStack + VertexConsumer.
 *   - Kamera ueber client.gameRenderer.getCamera() (WorldRenderContext hat in
 *     dieser Fabric-Version kein camera()).
 *   - Position: entity.getLerpedPos(delta) + Hoehe, relativ zur Kamera-Pos
 *     (getCameraPosVec) -- denn Vertex-Koordinaten sind kamera-relativ.
 *   - Billboard: MatrixStack mit camera.getRotation() multiplizieren, dann
 *     auf den Kopf stellen (Text-Y waechst nach unten) + skalieren.
 *   - TextRenderer.draw(String, x, y, color, shadow, Matrix4f, VCP, layer,
 *       backgroundColor, light).
 */
public final class HealthIndicatorRenderer {

    // Standard-Textgroesse im 3D: 0.025 ist die uebliche Nametag-Skalierung.
    private static final float BASE_SIZE = 0.025f;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            HealthIndicatorModule mod = (HealthIndicatorModule) find(HealthIndicatorModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Camera camera = client.gameRenderer.getCamera();
                if (camera == null) return;
                Quaternionf camRot = camera.getRotation();

                Vec3d camPos = client.player.getCameraPosVec(tickDelta);
                TextRenderer tr = client.textRenderer;

                float userScale = (float) mod.scale.get();
                int color = mod.color.get();
                String modeName = mod.mode.get();

                int drawn = 0;
                for (Entity e : client.world.getEntities()) {
                    if (e == client.player) continue;
                    if (!(e instanceof LivingEntity living)) continue;
                    if (!shouldShow(mod, living)) continue;
                    if (!living.isAlive()) continue;

                    String text = buildText(modeName, living);
                    if (text.isEmpty()) continue;

                    // Interpolierte Entity-Position, etwas ueber dem Kopf.
                    Vec3d pos = living.getLerpedPos(tickDelta);
                    double x = pos.x - camPos.x;
                    double y = pos.y + living.getHeight() + 0.5 - camPos.y;
                    double z = pos.z - camPos.z;

                    matrices.push();
                    matrices.translate(x, y, z);
                    // Billboard: zur Kamera ausrichten.
                    matrices.multiply(camRot);
                    // Text-Y waechst nach unten -> negativ skalieren, plus
                    // Nutzer-Skalierung.
                    float s = BASE_SIZE * userScale;
                    matrices.scale(-s, -s, s);

                    Matrix4f matrix = matrices.peek().getPositionMatrix();
                    int width = tr.getWidth(text);
                    float drawX = -width / 2.0f; // zentrieren

                    // SEE_THROUGH: Text ist immer sichtbar (auch durch Waende),
                    // damit die Anzeige nicht versehentlich verdeckt wird.
                    tr.draw(text, drawX, 0.0f, color, false, matrix, consumers,
                            TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);

                    matrices.pop();
                    drawn++;
                }

                // WICHTIG: Der Text wird nur in einen Sammel-Buffer geschrieben.
                // Ohne Flush erscheint NICHTS. Den Buffer jetzt zeichnen.
                if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
                    immediate.draw();
                }

                // Einmalige Diagnose: wie viele Entities wurden gezeichnet?
                if (!loggedOnce) {
                    loggedOnce = true;
                    System.out.println("[pvpclient] HealthIndicator aktiv, gezeichnet="
                            + drawn + " (consumers=" + consumers.getClass().getSimpleName() + ")");
                }
            } catch (Throwable t) {
                // Render-Fehler duerfen den Welt-Render nicht abbrechen.
                // Den Fehler aber EINMALIG ins Log schreiben, damit man bei
                // Problemen die Ursache sieht (statt stillem Verschlucken).
                if (!loggedError) {
                    loggedError = true;
                    System.out.println("[pvpclient] HealthIndicator Render-Fehler: " + t);
                    t.printStackTrace();
                }
            }
        });
    }

    // Verhindert Log-Spam: der Render-Fehler wird nur einmal ausgegeben.
    private static boolean loggedError = false;
    // Einmalige Erfolgs-/Status-Diagnose.
    private static boolean loggedOnce = false;

    /** Baut den Anzeige-String je nach Modus. */
    private static String buildText(String modeName, LivingEntity living) {
        int hp = Math.round(living.getHealth());
        if (hp < 0) hp = 0;

        switch (modeName) {
            case "Zahl":
                return Integer.toString(hp);
            case "Zahl+Herz":
                return hp + " \u2764";
            case "Herzen":
            default:
                // Ein Herz je 2 HP (wie die Lebensleiste), aufgerundet.
                int hearts = (int) Math.ceil(hp / 2.0);
                if (hearts <= 0) return "\u2764"; // mindestens eins zeigen
                // Bei sehr vielen HP nicht endlos Herzen -> ab 10 Herzen Zahl.
                if (hearts > 10) {
                    return hp + " \u2764";
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < hearts; i++) sb.append('\u2764');
                return sb.toString();
        }
    }

    /** Prueft, ob fuer diese Entity-Art angezeigt werden soll. */
    private static boolean shouldShow(HealthIndicatorModule mod, LivingEntity living) {
        if (living instanceof PlayerEntity) return mod.showPlayers.get();
        if (living instanceof Monster) return mod.showMonsters.get();
        if (living instanceof AnimalEntity || living instanceof PassiveEntity) {
            return mod.showAnimals.get();
        }
        // Sonstige Lebewesen (z.B. Wasser-Mobs) zu den Tieren zaehlen.
        return mod.showAnimals.get();
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
