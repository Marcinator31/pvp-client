package com.example.pvpclient;

import com.example.pvpclient.account.AccountScreen;
import com.example.pvpclient.gui.ClickGui;
import com.example.pvpclient.hud.HudRenderer;
import com.example.pvpclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-Einstiegspunkt ("onEnable").
 */
public class PvpClientClient implements ClientModInitializer {

    public static final String MOD_ID = "pvpclient";

    // Ab 1.21.9 ist die Keybind-Kategorie ein Category-Objekt, kein String.
    // Wir erstellen eine eigene Kategorie fuer alle unsere Keybinds.
    private static final KeyBinding.Category CATEGORY =
        KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    private static KeyBinding openClickGuiKey;
    private static KeyBinding openAccountsKey;

    @Override
    public void onInitializeClient() {
        // Module initialisieren (laedt die Registry).
        ModuleManager.INSTANCE.getModules();

        // HUD-Rendering anmelden.
        HudRenderer.register();

        // 3D-Welt-Rendering (Hitboxen) anmelden.
        com.example.pvpclient.hud.HitboxRenderer.register();

        // Keybind: Rechte Umschalttaste oeffnet das ClickGUI (wie viele Clients).
        openClickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pvpclient.clickgui", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        // Keybind: O oeffnet den Account-Switcher.
        openAccountsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pvpclient.accounts", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O, CATEGORY));

        // Einmalige Zustands-Synchronisation: Module, die standardmaessig an
        // sind und ihre Wirkung ueber onEnable() entfalten (AppleSkin,
        // ShieldStatus per Reflection), muessen ihren Effekt beim Start einmal
        // aktiv setzen. Wir machen das beim ersten Tick (dann sind alle
        // eingebetteten Mods garantiert geladen). Ein Flag sorgt fuer "nur einmal".
        final boolean[] synced = { false };

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!synced[0]) {
                synced[0] = true;
                for (var module : ModuleManager.INSTANCE.getModules()) {
                    try {
                        module.syncState();
                    } catch (Throwable ignored) {
                        // Ein einzelnes Modul darf den Start nicht stoeren.
                    }
                }
            }
            while (openClickGuiKey.wasPressed()) {
                client.setScreen(new ClickGui());
            }
            while (openAccountsKey.wasPressed()) {
                client.setScreen(new AccountScreen());
            }
        });

        System.out.println("[" + MOD_ID + "] Client gestartet.");
    }
}
