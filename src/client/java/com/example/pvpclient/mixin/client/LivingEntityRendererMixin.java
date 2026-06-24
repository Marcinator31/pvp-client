package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HealthIndicatorModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

/**
 * Zeichnet die Lebenspunkte ueber Entities -- Ansatz uebernommen von der
 * funktionierenden HealthIndicators-Mod (adytech99) fuer 1.21.11.
 *
 * Warum dieser Weg (und nicht WorldRenderEvents): In 1.21.11 laeuft das
 * Entity-Rendering ueber RenderStates. Die render-Methode des
 * LivingEntityRenderer bekommt einen MatrixStack, der BEREITS an der
 * Entity-Position sitzt -- ideal, um direkt darueber zu zeichnen. Der
 * WorldRenderEvents-MatrixStack passt dafuer nicht, deshalb war vorher nichts
 * sichtbar.
 *
 * Mechanik:
 *   1. updateRenderState @TAIL: merkt sich Entity je RenderState (WeakHashMap),
 *      weil render() nur den RenderState, nicht die Entity bekommt.
 *   2. render @TAIL: holt die Entity zum RenderState und zeichnet den
 *      Health-Text als zur Kamera ausgerichtetes Billboard ueber dem Kopf.
 *
 * Signaturen exakt aus dem Mod-Bytecode uebernommen:
 *   updateRenderState(LivingEntity, LivingEntityRenderState, float)  [method_62355]
 *   render(LivingEntityRenderState, MatrixStack, OrderedRenderCommandQueue,
 *          CameraRenderState)                                        [method_4054]
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Unique
    private static final WeakHashMap<LivingEntityRenderState, LivingEntity>
        pvpclient$entityMap = new WeakHashMap<>();

    @Unique
    private static final float PVPCLIENT_BASE_SIZE = 0.025f;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void pvpclient$captureEntity(LivingEntity entity, LivingEntityRenderState state,
                                         float tickDelta, CallbackInfo ci) {
        pvpclient$entityMap.put(state, entity);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL"))
    private void pvpclient$renderHealth(LivingEntityRenderState state, MatrixStack matrices,
                                        OrderedRenderCommandQueue queue, CameraRenderState camState,
                                        CallbackInfo ci) {
        try {
            HealthIndicatorModule mod = (HealthIndicatorModule) pvpclient$find(HealthIndicatorModule.class);
            if (mod == null || !mod.isEnabled()) return;

            LivingEntity entity = pvpclient$entityMap.get(state);
            if (entity == null || !entity.isAlive()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == entity) return; // nicht ueber sich selbst
            if (!pvpclient$shouldShow(mod, entity)) return;

            String text = pvpclient$buildText(mod.mode.get(), entity);
            if (text.isEmpty()) return;

            TextRenderer tr = client.textRenderer;
            Camera camera = client.gameRenderer.getCamera();
            if (camera == null) return;
            Quaternionf camRot = camera.getRotation();

            float userScale = (float) mod.scale.get();
            int color = mod.color.get();

            matrices.push();
            // Der MatrixStack sitzt an den Fuessen der Entity (Entity-relativ).
            // Etwas ueber den Kopf gehen.
            matrices.translate(0.0, entity.getHeight() + 0.5, 0.0);
            // Billboard: zur Kamera ausrichten.
            matrices.multiply(camRot);
            // Text-Y waechst nach unten -> negativ skalieren.
            float s = PVPCLIENT_BASE_SIZE * userScale;
            matrices.scale(-s, -s, s);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            int width = tr.getWidth(text);
            float drawX = -width / 2.0f;

            VertexConsumerProvider.Immediate immediate =
                client.getBufferBuilders().getEntityVertexConsumers();
            tr.draw(text, drawX, 0.0f, color, false, matrix, immediate,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);
            immediate.draw();

            matrices.pop();
        } catch (Throwable t) {
            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[pvpclient] HealthIndicator Render-Fehler: " + t);
                t.printStackTrace();
            }
        }
    }

    @Unique
    private static boolean pvpclient$logged = false;

    @Unique
    private static String pvpclient$buildText(String modeName, LivingEntity living) {
        int hp = Math.round(living.getHealth());
        if (hp < 0) hp = 0;
        switch (modeName) {
            case "Zahl":
                return Integer.toString(hp);
            case "Zahl+Herz":
                return hp + " \u2764";
            case "Herzen":
            default:
                int hearts = (int) Math.ceil(hp / 2.0);
                if (hearts <= 0) return "\u2764";
                if (hearts > 10) return hp + " \u2764";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < hearts; i++) sb.append('\u2764');
                return sb.toString();
        }
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
