package com.example.pvpclient.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Ein "Accessor-Mixin" -- ein zweiter Mixin-Typ, den du kennen solltest.
 *
 * Problem: MinecraftClient.session ist privat und final. Der Account-
 * Switcher muss es aber neu setzen koennen.
 *
 * Loesung: @Accessor erzeugt automatisch einen Setter dafuer. Statt
 * haesslicher Reflection castest du den Client einfach auf dieses
 * Interface und rufst setSession() auf:
 *
 *   ((MinecraftClientAccessor) MinecraftClient.getInstance())
 *       .setSession(neueSession);
 *
 * Hinweis: Der exakte Feldname ("session") kann je nach Mappings
 * leicht abweichen. Falls der Build meckert, in der MinecraftClient-
 * Klasse nach dem Session-Feld suchen und den Namen anpassen.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("field_1726")
    void setSession(Session session);

    /**
     * Getter fuer das package-private worldRenderer-Feld. Wird vom Potato
     * Mode genutzt, um nach einer Render-Distanz-Aenderung reload() aufzurufen,
     * damit die Aenderung sofort sichtbar wird.
     */
    @Accessor("worldRenderer")
    WorldRenderer getWorldRenderer();

    /**
     * Setter fuer das crosshairTarget-Feld. Wird genutzt, damit in der Freecam
     * der Abbau/Angriff vom echten Spieler ausgeht statt von der Kamera: wir
     * berechnen das Ziel selbst vom Spieler und setzen es hier.
     */
    @Accessor("field_1765")
    void setCrosshairTarget(net.minecraft.util.hit.HitResult target);
}
