package com.example.pvpclient.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Basisklasse fuer Module, die ein gebuendeltes Resource Pack an- und
 * ausschalten. Beim Aktivieren wird das Pack zur Liste der aktiven Packs
 * hinzugefuegt, beim Deaktivieren entfernt -- danach jeweils ein
 * Ressourcen-Reload.
 *
 * Der interne Pack-Name eines built-in Packs enthaelt die ID, die wir bei
 * der Registrierung vergeben haben (z.B. "pvpclient:smalltotem"). Da das
 * genaue Praefix je nach Version variiert ("builtin/", Doppelpunkt etc.),
 * suchen wir das Profil ueber "enthaelt den ID-Teil" statt ueber exakte
 * Gleichheit -- das ist robust gegen Namensschema-Aenderungen.
 *
 * APIs verifiziert: MinecraftClient.getResourcePackManager(),
 * ResourcePackManager.getNames()/setEnabledProfiles()/getEnabledNames(),
 * MinecraftClient.reloadResources().
 */
public abstract class ResourcePackModule extends Module {

    private final String packIdPart;

    protected ResourcePackModule(String name, Category category, String packIdPart) {
        super(name, category);
        this.packIdPart = packIdPart;
    }

    @Override
    public void onEnable() {
        setPackEnabled(true);
    }

    @Override
    public void onDisable() {
        setPackEnabled(false);
    }

    private void setPackEnabled(boolean enable) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        ResourcePackManager manager = client.getResourcePackManager();
        if (manager == null) return;

        // Das vollstaendige Profil finden, dessen Name unseren ID-Teil enthaelt.
        String fullName = null;
        for (String name : manager.getNames()) {
            if (name.contains(packIdPart)) {
                fullName = name;
                break;
            }
        }
        if (fullName == null) return; // Pack nicht gefunden -> nichts tun

        // Aktuelle aktive Liste kopieren und anpassen.
        List<String> enabled = new ArrayList<>(manager.getEnabledNames());

        if (enable) {
            if (!enabled.contains(fullName)) {
                enabled.add(fullName);
            }
        } else {
            enabled.remove(fullName);
        }

        manager.setEnabledProfiles(enabled);

        // Ressourcen neu laden, damit die Aenderung sofort wirkt.
        client.reloadResources();
    }
}
