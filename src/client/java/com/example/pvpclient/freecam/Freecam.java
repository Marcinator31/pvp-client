package com.example.pvpclient.freecam;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.FreecamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

/**
 * Verwaltet den Freecam-Zustand: ob aktiv, die freie Kamera-Position und die
 * Bewegung per WASD/Leertaste/Shift.
 *
 * Bewusst minimal: Die Kamera loest sich vom Spieler und fliegt frei. Die
 * Rotation kommt aus der normalen Spieler-Blickrichtung (die Maus dreht also
 * die Kamera), die Position ist unabhaengig -- der Spieler bleibt stehen.
 *
 * Der CameraMixin liest hier Position + Aktiv-Status aus und ueberschreibt die
 * echte Kamera entsprechend.
 */
public final class Freecam {

    private static boolean active = false;
    private static double x, y, z;        // aktuelle Freecam-Position
    private static double velX, velY, velZ; // fuer sanftes Gleiten

    private static final double SPEED = 0.6;   // Beschleunigung pro Tick
    private static final double FRICTION = 0.7; // Daempfung (0..1)

    private Freecam() {}

    public static boolean isActive() {
        return active;
    }

    public static Vec3d getPos() {
        return new Vec3d(x, y, z);
    }

    /** Schaltet die Freecam an/aus. Beim Anschalten startet sie an der Spielerposition. */
    public static void toggle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        active = !active;
        if (active) {
            Vec3d eye = mc.player.getEyePos();
            x = eye.x; y = eye.y; z = eye.z;
            velX = velY = velZ = 0;
        }
    }

    public static void disable() {
        active = false;
    }

    /** Pro Client-Tick: Eingaben verarbeiten und die Freecam bewegen. */
    public static void tick() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { active = false; return; }

        // Wenn ein Bildschirm offen ist (z.B. Pause/ClickGui), nicht bewegen.
        if (mc.currentScreen != null) {
            applyFriction();
            move();
            return;
        }

        boolean fwd   = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_W);
        boolean back  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_S);
        boolean left  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_A);
        boolean right = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_D);
        boolean up    = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
        boolean down  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);

        // Blickrichtung des Spielers (die Maus dreht ihn weiterhin).
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // Vorwaerts-Vektor (inkl. Pitch fuer Hoch-/Runterfliegen beim Blicken).
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz =  Math.cos(yawRad) * Math.cos(pitchRad);
        // Rechts-Vektor (nur horizontal).
        double rx =  Math.cos(yawRad);
        double rz =  Math.sin(yawRad);

        double speed = SPEED;
        if (mc.options.sprintKey.isPressed()) speed *= 2.5; // schneller mit Sprint

        if (fwd)   { velX += fx * speed; velY += fy * speed; velZ += fz * speed; }
        if (back)  { velX -= fx * speed; velY -= fy * speed; velZ -= fz * speed; }
        if (right) { velX += rx * speed; velZ += rz * speed; }
        if (left)  { velX -= rx * speed; velZ -= rz * speed; }
        if (up)    { velY += speed; }
        if (down)  { velY -= speed; }

        applyFriction();
        move();
    }

    private static void applyFriction() {
        velX *= FRICTION; velY *= FRICTION; velZ *= FRICTION;
    }

    private static void move() {
        x += velX; y += velY; z += velZ;
    }

    private static boolean isDown(MinecraftClient mc, int key) {
        return InputUtil.isKeyPressed(mc.getWindow(), key);
    }

    /** Liefert das Freecam-Modul (oder null). */
    public static FreecamModule module() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof FreecamModule f) return f;
        }
        return null;
    }
}
