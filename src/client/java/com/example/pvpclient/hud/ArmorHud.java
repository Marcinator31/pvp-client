package com.example.pvpclient.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * ArmorHUD -- zeigt die 4 Ruestungsteile + die Hauptwaffe an,
 * jeweils mit dem Item-Icon und der verbleibenden Haltbarkeit.
 *
 * Das ist exakt dasselbe Grundgeruest wie beim CPS-Renderer:
 * Wir bekommen den DrawContext und zeichnen drauf. Neu ist nur,
 * dass wir context.drawItem(...) benutzen statt Text.
 *
 * Aufgerufen wird das aus HudRenderer.onHudRender().
 */
public final class ArmorHud {

    // Wie gross ein Item-Icon gezeichnet wird (Vanilla-Standard: 16px).
    private static final int SLOT = 16;
    private static final int PADDING = 2;

    /**
     * Zeichnet die Ruestungsleiste.
     *
     * @param context  der Render-Kontext (kommt vom HudRenderCallback)
     * @param client   die MinecraftClient-Instanz
     */
    public static void render(DrawContext context, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Reihenfolge: Helm, Brustplatte, Hose, Schuhe, dann Hauptwaffe.
        // getArmor(slot): 0 = Schuhe, 1 = Hose, 2 = Brust, 3 = Helm.
        // Wir drehen es um, damit Helm oben steht.
        ItemStack[] stacks = new ItemStack[] {
            player.getInventory().getArmorStack(3), // Helm
            player.getInventory().getArmorStack(2), // Brustplatte
            player.getInventory().getArmorStack(1), // Hose
            player.getInventory().getArmorStack(0), // Schuhe
            player.getMainHandStack()               // Waffe in der Hand
        };

        // Position: rechts mittig am Bildschirmrand, vertikal gestapelt.
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        int totalHeight = stacks.length * (SLOT + PADDING);
        int x = screenW - SLOT - 6;          // 6px Abstand vom rechten Rand
        int startY = (screenH - totalHeight) / 2;

        int y = startY;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                y += SLOT + PADDING;
                continue; // leeren Slot ueberspringen
            }

            // 1) Item-Icon zeichnen
            context.drawItem(stack, x, y);

            // 2) Stack-Overlay (zeigt u.a. den Haltbarkeitsbalken automatisch)
            context.drawStackOverlay(client.textRenderer, stack, x, y);

            // 3) Optional: Haltbarkeit als Prozent-Text links neben das Icon.
            if (stack.isDamageable()) {
                int max = stack.getMaxDamage();
                int left = max - stack.getDamage();
                int percent = (int) Math.round((left / (double) max) * 100.0);

                String txt = percent + "%";
                int color = colorForPercent(percent);

                int textW = client.textRenderer.getWidth(txt);
                context.drawTextWithShadow(
                    client.textRenderer,
                    txt,
                    x - textW - 4,           // links neben das Icon
                    y + (SLOT - 8) / 2,      // vertikal zentriert zum Icon
                    color
                );
            }

            y += SLOT + PADDING;
        }
    }

    /** Gruen bei voller Haltbarkeit, gelb in der Mitte, rot wenn fast kaputt. */
    private static int colorForPercent(int percent) {
        if (percent > 50) return 0xFF55FF55; // gruen
        if (percent > 25) return 0xFFFFFF55; // gelb
        return 0xFFFF5555;                   // rot
    }
}
