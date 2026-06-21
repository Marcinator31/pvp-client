package com.example.pvpclient.hud;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.ArmorHudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * ArmorHUD -- Ruestungsteile + Waffe mit Haltbarkeit.
 *
 * Liest jetzt alle Settings aus dem ArmorHudModule:
 *  - Basis-Position (baseX/baseY) verschiebt die ganze HUD
 *  - Einzel-Offsets verschieben jedes Teil zusaetzlich
 *  - textColor faerbt die Prozentanzeige
 *  - showDurability blendet die Prozente ein/aus
 *
 * Hinweis zum Scale: Echtes Icon-Skalieren braucht die MatrixStack-API,
 * die in 1.21.11 nicht sicher verfuegbar ist. Daher wirkt 'scale' hier
 * auf den ABSTAND der Teile (groesserer Abstand = HUD wirkt groesser),
 * nicht auf die Icon-Pixelgroesse. Das ist der stabile Weg.
 */
public final class ArmorHud {

    private static final int SLOT = 16;
    private static final int PADDING = 2;

    private static ArmorHudModule module() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof ArmorHudModule a) return a;
        }
        return null;
    }

    public static void render(DrawContext context, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        ArmorHudModule mod = module();
        if (mod == null) return;

        ItemStack[] stacks = new ItemStack[] {
            player.getEquippedStack(EquipmentSlot.HEAD),
            player.getEquippedStack(EquipmentSlot.CHEST),
            player.getEquippedStack(EquipmentSlot.LEGS),
            player.getEquippedStack(EquipmentSlot.FEET),
            player.getMainHandStack()
        };

        // Einzel-Offsets pro Teil (gleiche Reihenfolge wie stacks).
        int[][] offsets = new int[][] {
            { mod.helmetOffsetX.getInt(), mod.helmetOffsetY.getInt() },
            { mod.chestOffsetX.getInt(),  mod.chestOffsetY.getInt()  },
            { mod.legsOffsetX.getInt(),   mod.legsOffsetY.getInt()   },
            { mod.bootsOffsetX.getInt(),  mod.bootsOffsetY.getInt()  },
            { mod.handOffsetX.getInt(),   mod.handOffsetY.getInt()   }
        };

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        // Abstand zwischen den Teilen, beeinflusst von 'scale'.
        double scale = mod.scale.get();
        int step = (int) Math.round((SLOT + PADDING) * scale);

        // Standard-Ankerpunkt: rechts mittig. Plus Basis-Offset aus den Settings.
        int totalHeight = stacks.length * step;
        int baseX = screenW - SLOT - 6 + mod.baseX.getInt();
        int startY = (screenH - totalHeight) / 2 + mod.baseY.getInt();

        boolean showPercent = mod.showDurability.get();
        int textColor = mod.textColor.get();

        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (stack == null || stack.isEmpty()) continue;

            int x = baseX + offsets[i][0];
            int y = startY + i * step + offsets[i][1];

            context.drawItem(stack, x, y);
            context.drawStackOverlay(client.textRenderer, stack, x, y);

            if (showPercent && stack.isDamageable()) {
                int max = stack.getMaxDamage();
                int leftDur = max - stack.getDamage();
                int percent = (int) Math.round((leftDur / (double) max) * 100.0);

                String txt = percent + "%";
                int textW = client.textRenderer.getWidth(txt);
                context.drawTextWithShadow(
                    client.textRenderer, txt,
                    x - textW - 4, y + (SLOT - 8) / 2, textColor
                );
            }
        }
    }
}
