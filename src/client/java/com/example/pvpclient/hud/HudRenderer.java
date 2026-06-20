package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.ArmorHudModule;
import com.example.pvpclient.module.modules.CpsModule;
import com.example.pvpclient.module.modules.FpsModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Zeichnet die HUD-Overlays ueber die HudElementRegistry-API.
 *
 * DIAGNOSE-VERSION: zeichnet zusaetzlich IMMER ein rotes Rechteck oben
 * links und einen festen Text -- unabhaengig von jedem Modul. Damit
 * sehen wir eindeutig, ob die HUD-API ueberhaupt feuert.
 */
public final class HudRenderer {

    private static final String MOD_ID = "pvpclient";

    public static void register() {
        System.out.println("[pvpclient] HudRenderer.register() WIRD AUFGERUFEN");
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.of(MOD_ID, "hud"),
            (context, tickCounter) -> onHudRender(context)
        );
        System.out.println("[pvpclient] HUD-Element wurde registriert");
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }

    private static void onHudRender(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        // === DIAGNOSE: immer sichtbar, egal welches Modul ===
        // Rotes Rechteck oben links + Text. Wenn DAS erscheint, feuert
        // die HUD-API und wir wissen, dass das Problem woanders liegt.
        context.fill(50, 50, 150, 100, 0xFFFF0000);
        if (client.textRenderer != null) {
            context.drawTextWithShadow(client.textRenderer,
                Text.literal("HUD AKTIV"), 55, 70, 0xFFFFFFFF);

            // Zeige, welche Module gerade als aktiviert gelten.
            int dy = 110;
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                String line = m.getName() + ": " + (m.isEnabled() ? "AN" : "aus");
                int col = m.isEnabled() ? 0xFF00FF00 : 0xFFFF5555;
                context.drawTextWithShadow(client.textRenderer,
                    Text.literal(line), 55, dy, col);
                dy += 12;
            }
        }
        // === Ende Diagnose ===

        if (client.player == null) return;
        if (client.getDebugHud().shouldShowDebugHud()) return;

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

        // --- ArmorHUD ---
        ArmorHudModule armor = (ArmorHudModule) find(ArmorHudModule.class);
        if (armor != null && armor.isEnabled()) {
            ArmorHud.render(context, client);
        }
    }
}
