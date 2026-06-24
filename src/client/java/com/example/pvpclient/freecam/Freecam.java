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
 * WICHTIG fuer fluessige Bewegung: Die Position wird pro RENDER-FRAME aktualisiert
 * (nicht pro Tick), mit Delta-Zeit-Skalierung. So ist die Bewegung bei jeder
 * Framerate gleich schnell und ruckelt nicht. Der CameraMixin ruft updateFrame()
 * jeden Frame auf.
 *
 * Die Rotation kommt aus der normalen Spieler-Blickrichtung (die Maus dreht also
 * die Kamera), die Position ist unabhaengig -- der Spieler bleibt stehen.
 */
public final class Freecam {

    private static boolean active = false;
    private static double x, y, z;          // aktuelle Freecam-Position
    private static double velX, velY, velZ; // Geschwindigkeit (Bloecke pro Sekunde)

    // Geschwindigkeit in Bloecken pro Sekunde (nicht pro Tick!).
    private static final double SPEED = 10.0;
    private static final double SPRINT_MULT = 3.0;
    // Reibung pro Sekunde: wie stark die Geschwindigkeit abklingt, wenn keine
    // Taste gedrueckt ist. Hoeher = laenger gleiten. Wird mit Delta skaliert.
    private static final double DAMPING_PER_SEC = 0.0025; // (Faktor^Sekunde)

    private static long lastFrameNano = 0L;

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
            lastFrameNano = System.nanoTime();
        }
    }

    public static void disable() {
        active = false;
    }

    /**
     * Pro RENDER-FRAME aufrufen (aus dem CameraMixin): liest Eingaben, bewegt die
     * Freecam mit Delta-Zeit. So ist die Bewegung fluessig und framerate-unabhaengig.
     */
    public static void updateFrame() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { active = false; return; }

        // Delta-Zeit seit dem letzten Frame (in Sekunden), begrenzt gegen Spruenge.
        long now = System.nanoTime();
        double dt = (now - lastFrameNano) / 1_000_000_000.0;
        lastFrameNano = now;
        if (dt <= 0) return;
        if (dt > 0.1) dt = 0.1; // bei Hängern nicht springen

        // Bei offenem Bildschirm nur ausgleiten, keine neuen Eingaben.
        boolean inputAllowed = (mc.currentScreen == null);

        double accel = 0;
        double fx = 0, fy = 0, fz = 0, rx = 0, rz = 0;
        boolean up = false, down = false;
        boolean anyMove = false;

        if (inputAllowed) {
            boolean fwd   = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_W);
            boolean back  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_S);
            boolean left  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            boolean right = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_D);
            up    = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
            down  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);

            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            // Vorwaerts-Vektor (inkl. Pitch fuers Hoch-/Runterfliegen beim Blicken).
            fx = -Math.sin(yawRad) * Math.cos(pitchRad);
            fy = -Math.sin(pitchRad);
            fz =  Math.cos(yawRad) * Math.cos(pitchRad);
            // Rechts-Vektor (horizontal), korrekt = (-cos(yaw), -sin(yaw)).
            // Herleitung: rechts = forward um 90 Grad gedreht in der XZ-Ebene
            // -> (-fz, fx) bei pitch 0. So zeigt D wirklich nach rechts.
            rx = -Math.cos(yawRad);
            rz = -Math.sin(yawRad);

            double speed = SPEED;
            if (mc.options.sprintKey.isPressed()) speed *= SPRINT_MULT;
            accel = speed;

            // Zielgeschwindigkeit aus den gedrueckten Tasten bauen.
            double tvx = 0, tvy = 0, tvz = 0;
            if (fwd)   { tvx += fx; tvy += fy; tvz += fz; anyMove = true; }
            if (back)  { tvx -= fx; tvy -= fy; tvz -= fz; anyMove = true; }
            if (right) { tvx += rx; tvz += rz; anyMove = true; }
            if (left)  { tvx -= rx; tvz -= rz; anyMove = true; }
            if (up)    { tvy += 1; anyMove = true; }
            if (down)  { tvy -= 1; anyMove = true; }

            // Richtungsvektor normalisieren, damit Diagonale nicht schneller ist.
            double len = Math.sqrt(tvx*tvx + tvy*tvy + tvz*tvz);
            if (len > 0.0001) {
                tvx = tvx/len * speed;
                tvy = tvy/len * speed;
                tvz = tvz/len * speed;
            }
            // Direkte Zielgeschwindigkeit (snappy, gut kontrollierbar).
            velX = tvx; velY = tvy; velZ = tvz;
        }

        // Wenn keine Taste: ausgleiten ueber Daempfung (delta-skaliert).
        if (!anyMove) {
            double factor = Math.pow(DAMPING_PER_SEC, dt);
            velX *= factor; velY *= factor; velZ *= factor;
        }

        // Position bewegen: Geschwindigkeit (Bloecke/Sek) * Delta-Zeit.
        x += velX * dt;
        y += velY * dt;
        z += velZ * dt;
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
