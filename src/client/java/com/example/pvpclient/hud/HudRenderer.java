package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.ArmorHudModule;
import com.example.pvpclient.module.modules.CpsModule;
import com.example.pvpclient.module.modules.CoordinatesModule;
import com.example.pvpclient.module.modules.PotionEffectsModule;
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

        // Vanilla-Statuseffekt-Overlay (oben rechts) entfernen, damit unsere
        // eigene Effekt-Anzeige links nicht doppelt ist. removeElement tut
        // nichts, falls der Identifier nicht existiert -> kein Crash-Risiko.
        try {
            HudElementRegistry.removeElement(Identifier.ofVanilla("status_effects"));
        } catch (Throwable ignored) {
            // Falls der Name in dieser Version abweicht: ignorieren.
        }
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
            pushScale(context, cps.x.getInt(), cps.y.getInt(), cps.scale.getFloat());
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    cps.x.getInt(), cps.y.getInt(), cps.color.get());
            popScale(context);
        }

        // --- FPS ---
        FpsModule fps = (FpsModule) find(FpsModule.class);
        if (fps != null && fps.isEnabled()) {
            String text = client.getCurrentFps() + " FPS";
            pushScale(context, fps.x.getInt(), fps.y.getInt(), fps.scale.getFloat());
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    fps.x.getInt(), fps.y.getInt(), fps.color.get());
            popScale(context);
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
            pushScale(context, coords.x.getInt(), coords.y.getInt(), coords.scale.getFloat());
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    coords.x.getInt(), coords.y.getInt(), coords.color.get());
            popScale(context);
        }

        // --- Potion-Effekte (Icon + Name + Restzeit darunter, wie AppleSkin) ---
        PotionEffectsModule potions = (PotionEffectsModule) find(PotionEffectsModule.class);
        if (potions != null && potions.isEnabled() && client.player != null) {
            int lineY = potions.y.getInt();
            int lineX = potions.x.getInt();
            var spriteManager = client.getStatusEffectSpriteManager();

            pushScale(context, lineX, lineY, potions.scale.getFloat());
            for (var effect : client.player.getStatusEffects()) {
                // 1) Vanilla-Icon links (18x18 wie im Inventar).
                var sprite = spriteManager.getSprite(effect.getEffectType());
                if (sprite != null) {
                    context.drawSpriteStretched(
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        sprite, lineX, lineY, 18, 18);
                }

                // 2) Englischer Name (aus Translation-Key-Pfad), rechts oben neben Icon.
                String key = effect.getTranslationKey();
                String raw = key.substring(key.lastIndexOf('.') + 1);
                String name = capitalize(raw.replace('_', ' '));
                int amp = effect.getAmplifier();
                if (amp > 0) {
                    name = name + " " + toRoman(amp + 1);
                }
                context.drawTextWithShadow(client.textRenderer, Text.literal(name),
                        lineX + 22, lineY + 1, potions.color.get());

                // 3) Restzeit darunter (mm:ss), etwas gedimmt.
                String time = net.minecraft.entity.effect.StatusEffectUtil
                        .getDurationText(effect, 1.0f, 20.0f).getString();
                context.drawTextWithShadow(client.textRenderer, Text.literal(time),
                        lineX + 22, lineY + 11, 0xFFAAAAAA);

                lineY += 22; // naechste Zeile (Platz fuers 18px-Icon)
            }
            popScale(context);
        }

        // --- ArmorHUD (nur wenn ein Spieler da ist) ---
        ArmorHudModule armor = (ArmorHudModule) find(ArmorHudModule.class);
        if (armor != null && armor.isEnabled() && client.player != null) {
            ArmorHud.render(context, client);
        }
    }

    /**
     * Beginnt eine Skalierung um den Punkt (anchorX, anchorY). Alles bis zum
     * passenden popScale wird mit dem Faktor scale gezeichnet, wobei der
     * Ankerpunkt fix bleibt (das Element waechst/schrumpft also an seiner
     * Position statt zum Bildschirmrand zu wandern).
     *
     * Nutzt den 2D-Matrixstack (Matrix3x2fStack) von DrawContext.getMatrices(),
     * der in 1.21.11 fuer GUI-Transforms zustaendig ist.
     */
    private static void pushScale(DrawContext context, float anchorX, float anchorY, float scale) {
        var m = context.getMatrices();
        m.pushMatrix();
        m.translate(anchorX, anchorY);
        m.scale(scale, scale);
        m.translate(-anchorX, -anchorY);
    }

    private static void popScale(DrawContext context) {
        context.getMatrices().popMatrix();
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
