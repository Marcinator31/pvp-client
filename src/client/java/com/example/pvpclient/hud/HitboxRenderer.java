package com.example.pvpclient.hud;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet Entity-Hitboxen im 3D-Raum.
 *
 * Nutzt VertexRendering.drawBox(MatrixStack, VertexConsumer, Box, r,g,b,a)
 * -- die Signatur ist gegen die Yarn-Javadocs verifiziert. Farben als
 * 0..1-Floats. Der Linien-Layer kommt von RenderLayer.getLines().
 */
public final class HitboxRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(HitboxRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        HitboxModule module = find();
        if (module == null || !module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        // Kamera-Position -- Boxen werden relativ dazu gezeichnet.
        Vec3d cam = context.camera().getPos();

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;

            Integer color = colorFor(entity, module);
            if (color == null) continue;

            // Echte Vanilla-Hitbox, relativ zur Kamera verschoben.
            Box box = entity.getBoundingBox().offset(-cam.x, -cam.y, -cam.z);

            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8)  & 0xFF) / 255f;
            float b = ( color        & 0xFF) / 255f;

            VertexRendering.drawBox(matrices, lines, box, r, g, b, a);
        }
    }

    /** Farbe je nach Kategorie; null wenn diese Kategorie aus ist. */
    private static Integer colorFor(Entity entity, HitboxModule m) {
        if (entity instanceof PlayerEntity) {
            return m.showPlayers.get() ? m.playerColor.get() : null;
        }
        if (entity instanceof HostileEntity) {
            return m.showHostiles.get() ? m.hostileColor.get() : null;
        }
        if (entity instanceof PassiveEntity) {
            return m.showAnimals.get() ? m.animalColor.get() : null;
        }
        if (entity instanceof LivingEntity) {
            return m.showHostiles.get() ? m.hostileColor.get() : null;
        }
        return m.showMisc.get() ? m.miscColor.get() : null;
    }

    private static HitboxModule find() {
        for (var mod : ModuleManager.INSTANCE.getModules()) {
            if (mod instanceof HitboxModule h) return h;
        }
        return null;
    }
}
