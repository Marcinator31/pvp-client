package com.example.pvpclient.mixin.client;

import net.minecraft.client.MinecraftClient;
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

    @Accessor("session")
    void setSession(Session session);
}
