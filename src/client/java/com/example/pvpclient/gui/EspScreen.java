package com.example.pvpclient.gui;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.EspModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP-Menue: zeigt alle Mobs als Spawn-Egg-Grid. Klick auf ein Ei schaltet ESP
 * fuer diesen Mob an/aus (aktive sind hervorgehoben). Mausrad scrollt.
 *
 * Die Mob-Liste wird dynamisch aus der Registry gebaut: jeder EntityType, der
 * ein Spawn-Egg hat, ist ein Mob. So sind automatisch alle Mobs dabei.
 *
 * Bewusst nur mit fill() + drawItem gebaut -- robust ueber Versionen.
 */
public class EspScreen extends Screen {

    private final Screen parent;
    private final EspModule esp;

    // Ein Eintrag: Icon-Item (Spawn-Ei oder Ersatz) + ID + Anzeigename.
    private static final class MobEntry {
        final net.minecraft.item.Item icon;
        final String id;
        final String name;
        MobEntry(net.minecraft.item.Item icon, String id, String name) {
            this.icon = icon; this.id = id; this.name = name;
        }
    }

    private final List<MobEntry> mobs = new ArrayList<>();

    // Layout.
    private static final int CELL = 22;     // Zellgroesse (Ei = 16 + Rand)
    private static final int PADDING = 10;
    private int columns = 10;
    private int gridTop = 40;
    private int scroll = 0;

    private int lastMouseX, lastMouseY;

    public EspScreen(Screen parent) {
        super(Text.literal("ESP - Mobs auswaehlen"));
        this.parent = parent;
        this.esp = (EspModule) findEsp();
        buildMobList();
    }

    private void buildMobList() {
        mobs.clear();

        // Spieler hat KEIN Spawn-Egg -> manuell zuerst hinzufuegen, mit einem
        // Spieler-Kopf als Icon. Die ID 'minecraft:player' passt zur Laufzeit-ID,
        // die der ESP-Mixin pruefen.
        mobs.add(new MobEntry(net.minecraft.item.Items.PLAYER_HEAD,
                "minecraft:player", "Spieler"));

        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            SpawnEggItem egg = SpawnEggItem.forEntity(type);
            if (egg == null) continue; // kein Spawn-Egg -> kein "Mob"
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            if (id == null) continue;
            String name = type.getName().getString();
            mobs.add(new MobEntry(egg, id.toString(), name));
        }
        // Alphabetisch nach Namen sortieren, fuer eine stabile Anzeige.
        // Der Spieler bleibt dabei einfach in der Sortierung (kein Sonderrang noetig).
        mobs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    @Override
    protected void init() {
        // Spaltenzahl an die Fensterbreite anpassen.
        int usable = this.width - 2 * PADDING;
        columns = Math.max(1, usable / CELL);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        ctx.fill(0, 0, this.width, this.height, 0xC0000000);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("ESP - Mob anklicken zum An/Aus, Mausrad scrollt, ESC schliesst"),
                PADDING, 16, 0xFFFFFFFF);

        int startX = PADDING;
        int i = 0;
        for (MobEntry mob : mobs) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * CELL;
            int y = gridTop + row * CELL + scroll;
            i++;

            // Nur sichtbare Zellen zeichnen.
            if (y + CELL < gridTop || y > this.height) continue;

            boolean on = esp != null && esp.isMobEnabled(mob.id);
            boolean hover = mouseX >= x && mouseX < x + CELL
                    && mouseY >= y && mouseY < y + CELL;

            // Hintergrund: aktiv = gruen, hover = hell, sonst dunkel.
            int bg = on ? 0xA000C040 : (hover ? 0x60FFFFFF : 0x40000000);
            ctx.fill(x, y, x + CELL, y + CELL, bg);
            if (on) {
                // Rahmen fuer aktive.
                ctx.fill(x, y, x + CELL, y + 1, 0xFF00FF66);
                ctx.fill(x, y + CELL - 1, x + CELL, y + CELL, 0xFF00FF66);
                ctx.fill(x, y, x + 1, y + CELL, 0xFF00FF66);
                ctx.fill(x + CELL - 1, y, x + CELL, y + CELL, 0xFF00FF66);
            }

            // Spawn-Ei zentriert (16px) in der Zelle.
            int ix = x + (CELL - 16) / 2;
            int iy = y + (CELL - 16) / 2;
            try {
                ctx.drawItem(new ItemStack(mob.icon), ix, iy);
            } catch (Throwable ignored) {
            }
        }

        // Tooltip fuer das Mob unter der Maus.
        MobEntry hovered = mobAt(mouseX, mouseY);
        if (hovered != null) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(hovered.name),
                    PADDING, this.height - 16, 0xFFFFFF00);
        }
    }

    private MobEntry mobAt(int mx, int my) {
        int startX = PADDING;
        int i = 0;
        for (MobEntry mob : mobs) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * CELL;
            int y = gridTop + row * CELL + scroll;
            i++;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL
                    && y + CELL >= gridTop && y <= this.height) {
                return mob;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        MobEntry mob = mobAt((int) click.x(), (int) click.y());
        if (mob != null && esp != null) {
            esp.toggleMob(mob.id);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        scroll += (int) Math.round(verticalAmount * CELL);
        if (scroll > 0) scroll = 0;
        // Untere Grenze berechnen.
        int rows = (mobs.size() + columns - 1) / columns;
        int totalHeight = rows * CELL;
        int minScroll = Math.min(0, (this.height - 40) - (gridTop + totalHeight));
        if (scroll < minScroll) scroll = minScroll;
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private static Object findEsp() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof EspModule) return m;
        }
        return null;
    }
}
