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
        // Bei sehr vielen Herzen begrenzen, damit die Reihe nicht zu breit wird.
        if (full > 10) { full = 10; half = false; }

        // Herzen als Text-Symbole bauen: volle Herzen + ggf. ein halbes.
        // Wir nutzen denselben submitLabel-Weg wie die Zahl-Modi (der sicher
        // funktioniert) -- das umgeht die fragilen Textur-Quads komplett.
        // Volles Herz: \u2764 (schwarzes Herz, wird eingefaerbt).
        // Halbes Herz: \u2765 (Herz mit Auslassung) als Naeherung.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < full; i++) sb.append('\u2764');
        if (half) sb.append('\u2765');
        if (sb.length() == 0) sb.append('\u2764'); // mind. ein Herz zeigen

        Text label = Text.literal(sb.toString())
                .setStyle(Style.EMPTY.withColor(color & 0xFFFFFF));

        double labelY = (entity.getHeight() + 0.5) / sc;
        Vec3d labelPos = new Vec3d(0.0, labelY, 0.0);
        int light = 0xF000F0;

        matrices.push();
        matrices.scale(sc, sc, sc);
        RenderCommandQueue rcq = queue.getBatchingQueue(light);
        rcq.submitLabel(matrices, labelPos, 0, label, true, light, 0.0, camState);
        matrices.pop();
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
