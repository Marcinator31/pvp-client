package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HealthIndicatorModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

/**
 * Zeigt die Lebenspunkte ueber Entities an.
 *
 * Modi "Zahl" und "Zahl+Herz": Text-Label ueber die Render-Command-Queue
 *   (submitLabel) -- so wie Vanilla-Nametags, inkl. Billboard.
 * Modus "Herzen": ECHTE Herz-Texturen (volle + halbe Herzen) als 3D-Quads.
 *   Die Texturen (assets/pvpclient/textures/heart/full.png + half.png) sind
 *   weiss (Inneres) mit schwarzer 1px-Outline. Per Vertex-color() faerben wir
 *   sie ein: weiss*Farbe = Farbe (Inneres gefaerbt), schwarz*Farbe = schwarz
 *   (Outline bleibt schwarz). So ist nur das Innere farbig.
 *
 * Ansatz (Mixin auf LivingEntityRenderer) von der HealthIndicators-Mod.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Unique
    private static final WeakHashMap<LivingEntityRenderState, LivingEntity>
        pvpclient$entityMap = new WeakHashMap<>();

    @Unique
    private static final Identifier PVPCLIENT_HEART_FULL =
        Identifier.of("pvpclient", "textures/heart/full.png");
    @Unique
    private static final Identifier PVPCLIENT_HEART_HALF =
        Identifier.of("pvpclient", "textures/heart/half.png");

    @Unique
    private static boolean pvpclient$logged = false;
    @Unique
    private static boolean pvpclient$heartsLogged = false;
    @Unique
    private static boolean pvpclient$lambdaLogged = false;

    @Inject(method = "method_62355", at = @At("TAIL"))
    private void pvpclient$captureEntity(LivingEntity entity, LivingEntityRenderState state,
                                         float tickDelta, CallbackInfo ci) {
        pvpclient$entityMap.put(state, entity);
    }

    @Inject(method = "method_4054", at = @At("TAIL"))
    private void pvpclient$renderHealth(LivingEntityRenderState state, MatrixStack matrices,
                                        OrderedRenderCommandQueue queue, CameraRenderState camState,
                                        CallbackInfo ci) {
        try {
            HealthIndicatorModule mod =
                (HealthIndicatorModule) pvpclient$find(HealthIndicatorModule.class);
            if (mod == null || !mod.isEnabled()) return;

            LivingEntity entity = pvpclient$entityMap.get(state);
            if (entity == null || !entity.isAlive()) return;
            if (!pvpclient$shouldShow(mod, entity)) return;

            String mode = mod.mode.get();
            float sc = (float) mod.scale.get();
            if (sc <= 0f) sc = 1f;
            int color = mod.color.get();

            if ("Herzen".equals(mode)) {
                pvpclient$renderHearts(entity, matrices, queue, camState, color, sc);
            } else {
                pvpclient$renderTextLabel(entity, matrices, queue, camState, mode, color, sc);
            }

            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[pvpclient] HealthIndicator aktiv, modus=" + mode);
            }
        } catch (Throwable t) {
            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[pvpclient] HealthIndicator Render-Fehler: " + t);
                t.printStackTrace();
            }
        }
    }

    /** Text-Label (Zahl / Zahl+Herz) ueber submitLabel. */
    @Unique
    private void pvpclient$renderTextLabel(LivingEntity entity, MatrixStack matrices,
                                           OrderedRenderCommandQueue queue,
                                           CameraRenderState camState,
                                           String mode, int color, float sc) {
        int hp = Math.round(entity.getHealth());
        if (hp < 0) hp = 0;
        String textStr = "Zahl+Herz".equals(mode) ? (hp + " \u2764") : Integer.toString(hp);

        Text label = Text.literal(textStr).setStyle(Style.EMPTY.withColor(color & 0xFFFFFF));
        double labelY = (entity.getHeight() + 0.5) / sc;
        Vec3d labelPos = new Vec3d(0.0, labelY, 0.0);
        int light = 0xF000F0;

        matrices.push();
        matrices.scale(sc, sc, sc);
        RenderCommandQueue rcq = queue.getBatchingQueue(light);
        rcq.submitLabel(matrices, labelPos, 0, label, true, light, 0.0, camState);
        matrices.pop();
    }

    /** Echte Herz-Texturen (volle + halbe) als 3D-Quads, eingefaerbt. */
    @Unique
    private void pvpclient$renderHearts(LivingEntity entity, MatrixStack matrices,
                                        OrderedRenderCommandQueue queue,
                                        CameraRenderState camState, int color, float sc) {
        int hp = Math.round(entity.getHealth());
        if (hp < 0) hp = 0;
        int full = hp / 2;
        boolean half = (hp % 2) == 1;
        int totalHearts = full + (half ? 1 : 0);
        if (totalHearts <= 0) { totalHearts = 1; half = true; full = 0; }
        // Bei sehr vielen Herzen begrenzen, damit es nicht zu breit wird.
        if (full > 10) { full = 10; half = false; totalHearts = 10; }

        // Kamera-Rotation fuers Billboard.
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (camera == null) return;
        Quaternionf camRot = camera.getRotation();

        int light = 0xF000F0;

        // Jedes Herz ist im 3D ca. 0.25 Bloecke gross.
        float heartWorld = 0.25f * sc;
        float gap = heartWorld * 0.05f;            // kleiner Abstand
        float step = heartWorld + gap;
        float totalWidth = totalHearts * step - gap;

        matrices.push();
        // Ueber den Kopf, Hoehe unabhaengig von der Skalierung halten ist hier
        // nicht noetig, da wir in Weltgroessen (heartWorld) arbeiten.
        matrices.translate(0.0, entity.getHeight() + 0.6, 0.0);
        // Billboard: zur Kamera drehen.
        matrices.multiply(camRot);
        // In Text-Konvention waechst y nach unten -> spiegeln, damit die Herzen
        // aufrecht stehen.
        matrices.scale(-1.0f, -1.0f, 1.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Startpunkt links (zentriert um 0).
        float startX = -totalWidth / 2.0f;

        // textSeeThrough -> Herzen sind immer sichtbar (auch durch Waende),
        // genau wie die HealthIndicators-Mod es im Durch-Wand-Modus macht.
        RenderLayer fullLayer = RenderLayers.textSeeThrough(PVPCLIENT_HEART_FULL);
        RenderLayer halfLayer = RenderLayers.textSeeThrough(PVPCLIENT_HEART_HALF);
        RenderCommandQueue rcq = queue.getBatchingQueue(light);

        float x = startX;
        for (int i = 0; i < full; i++) {
            final float qx = x;
            rcq.submitCustom(matrices, fullLayer, (entry, vc) -> {
                if (!pvpclient$lambdaLogged) {
                    pvpclient$lambdaLogged = true;
                    System.out.println("[pvpclient] Heart-Lambda LÄUFT (Quad wird gezeichnet)");
                }
                pvpclient$quad(vc, entry.getPositionMatrix(), qx, 0f, heartWorld, heartWorld, color, light);
            });
            x += step;
        }
        if (half) {
            final float qx = x;
            rcq.submitCustom(matrices, halfLayer, (entry, vc) ->
                pvpclient$quad(vc, entry.getPositionMatrix(), qx, 0f, heartWorld, heartWorld, color, light));
        }

        if (!pvpclient$heartsLogged) {
            pvpclient$heartsLogged = true;
            System.out.println("[pvpclient] renderHearts aufgerufen: full=" + full
                    + " half=" + half + " heartWorld=" + heartWorld);
        }

        matrices.pop();
    }

    /**
     * Zeichnet ein texturiertes Quad in den VertexConsumer.
     *
     * Reihenfolge + Methoden EXAKT wie die HealthIndicators-Mod (per Bytecode
     * der Methode RenderUtils.drawHeart verifiziert):
     *   pro Vertex: vertex(Matrix4fc,fff) -> texture(ff) -> light(I) -> color(FFFF)
     * Diese Reihenfolge ist entscheidend -- weicht sie vom VertexFormat ab,
     * wird gar nichts gezeichnet (genau das war vorher der Fehler).
     *
     * tint faerbt die Textur: weiss*tint = tint (Inneres), schwarz*tint = schwarz
     * (Outline bleibt schwarz).
     */
    @Unique
    private static void pvpclient$quad(VertexConsumer vc, Matrix4f m,
                                       float x, float y, float w, float h,
                                       int tint, int light) {
        int ai = (tint >> 24) & 0xFF; if (ai == 0) ai = 255;
        float r = ((tint >> 16) & 0xFF) / 255.0f;
        float g = ((tint >> 8) & 0xFF) / 255.0f;
        float b = (tint & 0xFF) / 255.0f;
        float a = ai / 255.0f;

        // Vier Ecken. Reihenfolge je Vertex: position, texture, light, color.
        vc.vertex(m, x,     y + h, 0f).texture(0f, 1f).light(light).color(r, g, b, a);
        vc.vertex(m, x + w, y + h, 0f).texture(1f, 1f).light(light).color(r, g, b, a);
        vc.vertex(m, x + w, y,     0f).texture(1f, 0f).light(light).color(r, g, b, a);
        vc.vertex(m, x,     y,     0f).texture(0f, 0f).light(light).color(r, g, b, a);
    }

    @Unique
    private static boolean pvpclient$shouldShow(HealthIndicatorModule mod, LivingEntity living) {
        if (living instanceof PlayerEntity) return mod.showPlayers.get();
        if (living instanceof Monster) return mod.showMonsters.get();
        if (living instanceof AnimalEntity || living instanceof PassiveEntity) {
            return mod.showAnimals.get();
        }
        return mod.showAnimals.get();
    }

    @Unique
    private static Module pvpclient$find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }
}
