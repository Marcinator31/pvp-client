package com.example.pvpclient.core;

import com.example.pvpclient.core.setting.Setting;
import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Speichert und laedt alle Modul-Einstellungen (an/aus, Position, Farben,
 * Skalierung ...) in einer Textdatei, damit sie einen Neustart ueberleben.
 *
 * Format pro Zeile:  ModulName\tSettingName\tWert
 * (Tab-getrennt, damit Namen mit Leerzeichen/Sonderzeichen kein Problem sind.)
 *
 * Jedes Setting kann sich schon selbst serialisieren (serialize/deserialize),
 * wir muessen die Werte also nur einsammeln und wieder zuordnen.
 */
public final class ConfigManager {

    private ConfigManager() {}

    /** Datei: <config>/pvpclient/pvpclient.txt */
    private static Path configFile() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("pvpclient");
        return dir.resolve("pvpclient.txt");
    }

    /** Alle aktuellen Einstellungen in die Datei schreiben. */
    public static void save() {
        try {
            Path file = configFile();
            Files.createDirectories(file.getParent());

            List<String> lines = new ArrayList<>();
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                for (Setting s : m.getSettings()) {
                    // ModulName \t SettingName \t serialisierterWert
                    String line = m.getName() + "\t" + s.getName() + "\t" + s.serialize();
                    lines.add(line);
                }
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // Speichern soll das Spiel nie crashen lassen.
            System.out.println("[pvpclient] Konnte Config nicht speichern: " + e.getMessage());
        }
    }

    /** Einstellungen aus der Datei laden und auf die Module anwenden. */
    public static void load() {
        try {
            Path file = configFile();
            if (!Files.exists(file)) {
                return; // Noch keine Config -> Defaults behalten.
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) continue;
                // In drei Teile zerlegen: ModulName, SettingName, Wert.
                // limit=3, damit ein Wert selbst Tabs enthalten duerfte.
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) continue;

                String modName = parts[0];
                String settingName = parts[1];
                String value = parts[2];

                Setting target = findSetting(modName, settingName);
                if (target != null) {
                    target.deserialize(value);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[pvpclient] Konnte Config nicht laden: " + e.getMessage());
        }
    }

    /** Findet ein Setting anhand von Modul- und Settingname. */
    private static Setting findSetting(String modName, String settingName) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (!m.getName().equals(modName)) continue;
            for (Setting s : m.getSettings()) {
                if (s.getName().equals(settingName)) {
                    return s;
                }
            }
        }
        return null;
    }
}
