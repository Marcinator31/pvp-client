package com.example.pvpclient;

import net.fabricmc.api.ModInitializer;

/**
 * Common-Einstiegspunkt (laeuft auf Client UND theoretisch Server).
 * Ein PvP-Client ist fast komplett clientseitig, daher bleibt hier
 * wenig zu tun. Die Musik spielt in PvpClientClient.
 */
public class PvpClient implements ModInitializer {

    public static final String MOD_ID = "pvpclient";

    @Override
    public void onInitialize() {
        System.out.println("[" + MOD_ID + "] geladen.");
    }
}
