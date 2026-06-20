package com.example.pvpclient.hud;

/**
 * Hitbox-Renderer -- VORERST DEAKTIVIERT.
 *
 * Das 3D-Linienzeichnen (WorldRenderContext / WorldRenderEvents /
 * VertexConsumer) ist die fummeligste und versionsabhaengigste Render-API
 * in Fabric. Die Klassennamen dafuer liessen sich fuer 1.21.11 nicht sicher
 * verifizieren, und falscher 3D-Render-Code fuehrt zu schwer auffindbaren
 * Abstuerzen.
 *
 * Damit du JETZT einen lauffaehigen Client hast, ist das Zeichnen hier
 * vorerst leer. Das HitboxModule bleibt im Menue sichtbar und an/aus-
 * schaltbar -- nur das tatsaechliche Zeichnen kommt nach, sobald die
 * restlichen Features laufen und wir die 3D-API in IntelliJ gegen die
 * echten Mappings pruefen koennen.
 *
 * register() wird weiterhin aufgerufen, tut aber aktuell nichts.
 */
public final class HitboxRenderer {

    public static void register() {
        // TODO: WorldRenderEvents.AFTER_ENTITIES registrieren, sobald die
        // exakte 3D-Render-API fuer 1.21.11 verifiziert ist.
        // Bis dahin bewusst leer, damit der Build laeuft.
    }
}
