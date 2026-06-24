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
import net.minecraft.text.Style;
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

            // Farbe in den Text einbetten (submitLabel hat keinen Farb-Parameter,
            // nutzt sonst die feste weisse Nametag-Farbe). withColor nimmt RGB.
            int rgb = mod.color.get() & 0xFFFFFF;
            Text label = Text.literal(textStr)
                    .setStyle(Style.EMPTY.withColor(rgb));

            int light = 0xF000F0;          // volles Licht
            double distSq = 0.0;           // 0 = immer sichtbar

            // Skalierung: submitLabel hat keinen Groessen-Parameter, also
            // skalieren wir den MatrixStack davor. push/pop kapselt das.
            float sc = (float) mod.scale.get();
            if (sc <= 0f) sc = 1f;

            // Position ueber dem Kopf. Da der MatrixStack gleich skaliert wird,
            // teilen wir die Hoehe durch den Faktor, damit der Text bei jeder
            // Groesse auf gleicher Welt-Hoehe ueber dem Kopf bleibt.
            double labelY = (entity.getHeight() + 0.5) / sc;
            Vec3d labelPos = new Vec3d(0.0, labelY, 0.0);

            matrices.push();
            matrices.scale(sc, sc, sc);

            // Ueber die Render-Command-Queue zeichnen (nativer 1.21.11-Weg).
            RenderCommandQueue rcq = queue.getBatchingQueue(light);
            rcq.submitLabel(matrices, labelPos, 0, label, true, light, distSq, camState);

            matrices.pop();

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
                // Volle Herzen (je 2 HP) + ggf. ein halbes Herz bei ungerader HP.
                int full = hp / 2;
                boolean half = (hp % 2) == 1;
                // Bei sehr vielen HP nicht endlos Herzen -> ab 10 Herzen Zahl.
                if (full > 10) return hp + " \u2764";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < full; i++) sb.append('\u2764'); // volles Herz
                if (half) sb.append('\u2765'); // halbes/anderes Herz-Symbol
                if (sb.length() == 0) sb.append('\u2765');
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
