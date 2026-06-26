package com.example.pvpclient.gui;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.BlockEspModule;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Block-ESP-Menue: zeigt alle Bloecke als Item-Grid. Klick auf einen Block
 * schaltet ESP dafuer an/aus (aktive sind hervorgehoben). Mausrad scrollt.
 *
 * Die Liste wird dynamisch aus der Block-Registry gebaut: jeder Block, der ein
 * Item hat (also im Inventar darstellbar ist), kommt rein.
 *
 * Aufbau wie der EspScreen (Mob-ESP), nur mit Bloecken.
 */
public class BlockEspScreen extends Screen {

    private final Screen parent;
    private final BlockEspModule esp;

    private static final class BlockEntry {
        final Item icon;
        final String id;
        final String name;
        BlockEntry(Item icon, String id, String name) {
            this.icon = icon; this.id = id; this.name = name;
        }
    }

    private final List<BlockEntry> blocks = new ArrayList<>();

    private static final int CELL = 22;
    private static final int PADDING = 10;
    private int columns = 10;
    private int gridTop = 40;
    private int scroll = 0;

    private int lastMouseX, lastMouseY;

    public BlockEspScreen(Screen parent) {
        super(Text.literal("Block-ESP - Bloecke auswaehlen"));
        this.parent = parent;
        this.esp = (BlockEspModule) findEsp();
        buildBlockList();
    }

    private void buildBlockList() {
        blocks.clear();
        for (Block block : Registries.BLOCK) {
            Item item = block.asItem();
            // Bloecke ohne Item (z.B. nur technische Bloecke) ueberspringen --
            // sie haben AIR als Item und waeren im Grid leer.
            if (item == Items.AIR) continue;
            Identifier id = Registries.BLOCK.getId(block);
            if (id == null) continue;
            String name = block.getName().getString();
            blocks.add(new BlockEntry(item, id.toString(), name));
        }
        blocks.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    @Override
    protected void init() {
        int usable = this.width - 2 * PADDING;
        columns = Math.max(1, usable / CELL);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        ctx.fill(0, 0, this.width, this.height, 0xC0000000);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("Block-ESP - Block anklicken zum An/Aus, Mausrad scrollt, ESC schliesst"),
                PADDING, 16, 0xFFFFFFFF);

        int startX = PADDING;
        int i = 0;
        for (BlockEntry b : blocks) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * CELL;
            int y = gridTop + row * CELL + scroll;
            i++;

            if (y + CELL < gridTop || y > this.height) continue;

            boolean on = esp != null && esp.isBlockEnabled(b.id);
            boolean hover = mouseX >= x && mouseX < x + CELL
                    && mouseY >= y && mouseY < y + CELL;

            int bg = on ? 0xA000C040 : (hover ? 0x60FFFFFF : 0x40000000);
            ctx.fill(x, y, x + CELL, y + CELL, bg);
            if (on) {
                ctx.fill(x, y, x + CELL, y + 1, 0xFF00FF66);
                ctx.fill(x, y + CELL - 1, x + CELL, y + CELL, 0xFF00FF66);
                ctx.fill(x, y, x + 1, y + CELL, 0xFF00FF66);
                ctx.fill(x + CELL - 1, y, x + CELL, y + CELL, 0xFF00FF66);
            }

            int ix = x + (CELL - 16) / 2;
            int iy = y + (CELL - 16) / 2;
            try {
                ctx.drawItem(new ItemStack(b.icon), ix, iy);
            } catch (Throwable ignored) {
            }
        }

        BlockEntry hovered = blockAt(mouseX, mouseY);
        if (hovered != null) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(hovered.name),
                    PADDING, this.height - 16, 0xFFFFFF00);
        }
    }

    private BlockEntry blockAt(int mx, int my) {
        int startX = PADDING;
        int i = 0;
        for (BlockEntry b : blocks) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * CELL;
            int y = gridTop + row * CELL + scroll;
            i++;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL
                    && y + CELL >= gridTop && y <= this.height) {
                return b;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        BlockEntry b = blockAt((int) click.x(), (int) click.y());
        if (b != null && esp != null) {
            esp.toggleBlock(b.id);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        scroll += (int) Math.round(verticalAmount * CELL);
        if (scroll > 0) scroll = 0;
        int rows = (blocks.size() + columns - 1) / columns;
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
            if (m instanceof BlockEspModule) return m;
        }
        return null;
    }
}
