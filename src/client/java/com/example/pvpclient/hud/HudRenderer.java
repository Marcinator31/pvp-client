package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.ArmorHudModule;
import com.example.pvpclient.module.modules.CpsModule;
import com.example.pvpclient.module.modules.CoordinatesModule;
import com.example.pvpclient.module.modules.PotionEffectsModule;
import com.example.pvpclient.module.modules.SaturationModule;
import com.example.pvpclient.module.modules.FpsModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Zeichnet die HUD-Overlays ueber die HudElementRegistry-API.
 */
public final class HudRenderer {

    private static final String MOD_ID = "pvpclient";

    public static void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.of(MOD_ID, "hud"),
            (context, tickCounter) -> onHudRender(context)
        );
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }

    private static void onHudRender(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        // KEINE frühen return-Checks mehr. Vorher brach hier
        // 'if (client.player == null) return;' oder der Debug-Check die
        // Methode ab, BEVOR die HUDs gezeichnet wurden -- deshalb erschien
        // nur das (frühere) Diagnose-Rechteck, aber nie CPS/FPS.
        if (client.textRenderer == null) return;

        // --- CPS ---
        CpsModule cps = (CpsModule) find(CpsModule.class);
        if (cps != null && cps.isEnabled()) {
            String text = "CPS: " + CpsCounter.LEFT.getCps();
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    cps.x.getInt(), cps.y.getInt(), cps.color.get());
        }

        // --- FPS ---
        FpsModule fps = (FpsModule) find(FpsModule.class);
        if (fps != null && fps.isEnabled()) {
            String text = client.getCurrentFps() + " FPS";
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    fps.x.getInt(), fps.y.getInt(), fps.color.get());
        }

        // --- Koordinaten (nur wenn ein Spieler da ist) ---
        CoordinatesModule coords = (CoordinatesModule) find(CoordinatesModule.class);
        if (coords != null && coords.isEnabled() && client.player != null) {
            int px = (int) Math.floor(client.player.getX());
            int py = (int) Math.floor(client.player.getY());
            int pz = (int) Math.floor(client.player.getZ());

            // Blickrichtung als Himmelsrichtung.
            String dir;
            switch (client.player.getHorizontalFacing()) {
                case NORTH -> dir = "N";
                case SOUTH -> dir = "S";
                case EAST  -> dir = "O";
                case WEST  -> dir = "W";
                default    -> dir = "";
            }

            String text = "XYZ: " + px + " " + py + " " + pz + "  [" + dir + "]";
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    coords.x.getInt(), coords.y.getInt(), coords.color.get());
        }

        // --- Potion-Effekte (nur wenn ein Spieler da ist) ---
        PotionEffectsModule potions = (PotionEffectsModule) find(PotionEffectsModule.class);
        if (potions != null && potions.isEnabled() && client.player != null) {
            int lineY = potions.y.getInt();
            int lineX = potions.x.getInt();
            for (var effect : client.player.getStatusEffects()) {
                // Effektname (uebersetzt) + Verstaerkungsstufe + Restzeit.
                String name = Text.translatable(effect.getTranslationKey()).getString();
                int amp = effect.getAmplifier();
                if (amp > 0) {
                    name = name + " " + (amp + 1);
                }
                String time = net.minecraft.entity.effect.StatusEffectUtil
                        .getDurationText(effect, 1.0f, 20.0f)
                        .getString();
                String line = name + " " + time;

                context.drawTextWithShadow(client.textRenderer, Text.literal(line),
                        lineX, lineY, potions.color.get());
                lineY += 10; // naechste Zeile
            }
        }

        // --- Sättigung (AppleSkin-Stil, nur wenn ein Spieler da ist) ---
        SaturationModule sat = (SaturationModule) find(SaturationModule.class);
        if (sat != null && sat.isEnabled() && client.player != null) {
            var hunger = client.player.getHungerManager();
            float saturation = hunger.getSaturationLevel();
            int food = hunger.getFoodLevel();

            // Sättigung auf eine Nachkommastelle.
            String satText = "Saturation: " + (Math.round(saturation * 10.0f) / 10.0f);
            context.drawTextWithShadow(client.textRenderer, Text.literal(satText),
                    sat.x.getInt(), sat.y.getInt(), sat.color.get());

            if (sat.showHunger.get()) {
                String foodText = "Hunger: " + food + "/20";
                context.drawTextWithShadow(client.textRenderer, Text.literal(foodText),
                        sat.x.getInt(), sat.y.getInt() + 10, sat.color.get());
            }
        }

        // --- ArmorHUD (nur wenn ein Spieler da ist) ---
        ArmorHudModule armor = (ArmorHudModule) find(ArmorHudModule.class);
        if (armor != null && armor.isEnabled() && client.player != null) {
            ArmorHud.render(context, client);
        }
    }
}
