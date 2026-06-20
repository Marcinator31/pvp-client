package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.CpsModule;
import com.example.pvpclient.module.modules.FpsModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Zeichnet die HUD-Overlays -- jetzt modul-gesteuert.
 *
 * Jedes HUD-Element prueft zuerst, ob sein Modul aktiviert ist, und
 * liest Position/Farbe aus den Settings des Moduls. So wirkt sich das
 * ClickGUI direkt aufs Bild aus: Modul aus -> nichts gezeichnet;
 * Farbe geaendert -> sofort sichtbar.
 */
public final class HudRenderer {

    public static void register() {
        HudRenderCallback.EVENT.register(HudRenderer::onHudRender);
    }

    private static Module find(Class<? extends Module> type) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (type.isInstance(m)) return m;
        }
        return null;
    }

    private static void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
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
        com.example.pvpclient.module.modules.ArmorHudModule armor =
            (com.example.pvpclient.module.modules.ArmorHudModule)
                find(com.example.pvpclient.module.modules.ArmorHudModule.class);
        if (armor != null && armor.isEnabled()) {
            ArmorHud.render(context, client);
        }
    }
}
