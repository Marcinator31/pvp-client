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
            // Mit einer Nachkommastelle, wie im F3-Bildschirm.
            double px = Math.round(client.player.getX() * 10.0) / 10.0;
            double py = Math.round(client.player.getY() * 10.0) / 10.0;
            double pz = Math.round(client.player.getZ() * 10.0) / 10.0;

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

        // --- Potion-Effekte (englischer Name + roemische Stufe) ---
        PotionEffectsModule potions = (PotionEffectsModule) find(PotionEffectsModule.class);
        if (potions != null && potions.isEnabled() && client.player != null) {
            int lineY = potions.y.getInt();
            int lineX = potions.x.getInt();

            for (var effect : client.player.getStatusEffects()) {
                // Englischer Effektname ueber den Translation-Key-Pfad.
                // getTranslationKey() liefert z.B. "effect.minecraft.strength";
                // wir nehmen den letzten Teil und machen ihn lesbar.
                String key = effect.getTranslationKey();
                String raw = key.substring(key.lastIndexOf('.') + 1);
                String name = capitalize(raw.replace('_', ' '));

                // Roemische Stufe (Amplifier 0 = I, 1 = II, ...).
                String roman = toRoman(effect.getAmplifier() + 1);
                String label = name + " " + roman;

                context.drawTextWithShadow(client.textRenderer, Text.literal(label),
                        lineX, lineY, potions.color.get());

                lineY += 12; // naechste Zeile
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

    /** Macht den ersten Buchstaben jedes Wortes gross ("strength" -> "Strength"). */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
                if (c == ' ') cap = true;
            }
        }
        return sb.toString();
    }

    /** Wandelt 1..n in roemische Zahlen (I, II, III, IV, ...). */
    private static String toRoman(int n) {
        if (n <= 0) return "";
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                n -= values[i];
                sb.append(symbols[i]);
            }
        }
        return sb.toString();
    }
}
