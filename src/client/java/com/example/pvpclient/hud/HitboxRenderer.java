package com.example.pvpclient.hud;

/**
 * Hitbox-Renderer -- vorerst wieder deaktiviert, damit der Build laeuft.
 *
 * Grund: Die 3D-Welt-Render-API (WorldRenderEvents, WorldRenderContext,
 * VertexRendering.drawBox, RenderLayer.getLines) wurde in 1.21.11 stark
 * umgebaut (Fabric/Mojang Richtung neuer Render-Pipeline). Die exakten
 * neuen Klassen-/Methodennamen liessen sich aus der Ferne nicht sicher
 * verifizieren.
 *
 * Diese Funktion am besten in IntelliJ fertigstellen: dort zeigt die
 * Auto-Vervollstaendigung beim Tippen sofort die korrekten Namen fuer
 * genau deine Fabric-API-Version an.
 */
public final class HitboxRenderer {

    public static void register() {
        // Bewusst leer -- siehe Klassen-Kommentar.
    }
}
