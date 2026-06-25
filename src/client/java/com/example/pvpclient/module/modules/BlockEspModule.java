package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Block-ESP: hebt ausgewaehlte Bloecke in der Welt mit einer farbigen Box-
 * Outline hervor. Welche Bloecke, waehlt man im Block-ESP-Menue (Item-Grid);
 * Farbe und Such-Reichweite sind einstellbar.
 *
 * Funktioniert wie das Mob-ESP, nur fuer Bloecke -- statt eines Glow-Effekts
 * (den Bloecke nicht haben) zeichnet der BlockEspRenderer die Outlines selbst.
 */
public class BlockEspModule extends Module {

    public final ColorSetting color = new ColorSetting("Farbe", 0xFF00FFFF);
    public final NumberSetting range = new NumberSetting("Reichweite", 32, 8, 64, 4);
    public final NumberSetting lineWidth = new NumberSetting("Linienbreite", 2.0, 0.5, 5.0, 0.5);

    // Aktive Block-Typen (z.B. "minecraft:diamond_ore").
    private final Set<String> enabledBlocks = new HashSet<>();

    public BlockEspModule() {
        super("Block-ESP", Category.PVP);
        addSetting(color);
        addSetting(range);
        addSetting(lineWidth);
    }

    public boolean isBlockEnabled(Identifier id) {
        return id != null && enabledBlocks.contains(id.toString());
    }

    public boolean isBlockEnabled(String id) {
        return enabledBlocks.contains(id);
    }

    public void toggleBlock(String id) {
        if (!enabledBlocks.add(id)) enabledBlocks.remove(id);
    }

    public Set<String> getEnabledBlocks() {
        return enabledBlocks;
    }

    public boolean hasAnyBlock() {
        return !enabledBlocks.isEmpty();
    }

    public int getEspColor() {
        return color.get();
    }

    public String serializeBlocks() {
        return String.join(",", enabledBlocks);
    }

    public void deserializeBlocks(String data) {
        enabledBlocks.clear();
        if (data == null || data.isEmpty()) return;
        for (String s : data.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) enabledBlocks.add(t);
        }
    }
}
