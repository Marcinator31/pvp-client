package com.example.pvpclient.mixin.client;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HealthIndicatorModule;
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
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

/**
 * Zeigt die Lebenspunkte ueber Entities als Text-Label -- so wie ein Nametag.
 *
 * Ansatz von der HealthIndicators-Mod (adytech99) + Vanilla-Nametag-Mechanik:
 *   1. updateRenderState (method_62355) @TAIL: merkt sich die Entity je
 *      RenderState (WeakHashMap), weil render() nur den RenderState bekommt.
 *   2. render (method_4054) @TAIL: zeichnet den Health-Text ueber dem Kopf.
 *
 * WICHTIG fuer 1.21.11: Text ueber Entities MUSS ueber die
 * OrderedRenderCommandQueue gezeichnet werden (queue.getBatchingQueue(...)
 * .submitLabel(...)), nicht ueber TextRenderer.draw mit VertexConsumern -- der
 * Buffer wuerde sonst nicht geflusht und nichts erscheint. submitLabel ist
 * genau die Methode, die Vanilla fuer Spieler-Namen nutzt, daher uebernimmt sie
 * Billboard, Hintergrund und Positionierung automatisch.
 *
 * Die Label-Position (nameLabelPos) berechnet Vanilla bereits im RenderState --
 * wir verwenden sie, damit der Text genau dort sitzt, wo sonst der Name steht.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Unique
    private static final WeakHashMap<LivingEntityRenderState, LivingEntity>
        pvpclient$entityMap = new WeakHashMap<>();

    @Unique
    private static boolean pvpclient$logged = false;

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

            String textStr = pvpclient$buildText(mod.mode.get(), entity);
            if (textStr.isEmpty()) return;
            Text label = Text.literal(textStr);

            // Position ueber dem Kopf. nameLabelPos setzt Vanilla nur fuer
            // benannte Entities -- deshalb bauen wir die Position selbst aus der
            // Entity-Hoehe (etwas Abstand ueber den Kopf). submitLabel uebernimmt
            // das Billboard, wir liefern nur den Offset relativ zur Entity.
            Vec3d labelPos = new Vec3d(0.0, entity.getHeight() + 0.5, 0.0);

            int light = 0xF000F0;          // volles Licht
            double distSq = state.field_53332; // squaredDistanceToCamera

            // Ueber die Render-Command-Queue zeichnen (nativer 1.21.11-Weg).
            RenderCommandQueue rcq = queue.getBatchingQueue(light);
            rcq.submitLabel(matrices, labelPos, 0, label, true, light, distSq, camState);

            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[pvpclient] HealthIndicator: submitLabel genutzt, text=" + textStr);
            }
        } catch (Throwable t) {
            if (!pvpclient$logged) {
                pvpclient$logged = true;
                System.out.println("[pvpclient] HealthIndicator Render-Fehler: " + t);
                t.printStackTrace();
            }
        }
    }

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
