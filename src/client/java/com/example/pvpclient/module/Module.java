package com.example.pvpclient.module;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Basis fuer JEDES Feature im Client.
 *
 * Das ist der Trick, mit dem das ganze "Lunar-Menue" funktioniert:
 * Jedes Feature ist ein Module mit
 *   - Name + Kategorie (fuer die Sortierung im Menue)
 *   - einem enabled-Zustand (der An/Aus-Schalter)
 *   - einer Liste von Settings (die anpassbaren Optionen)
 *
 * Das GUI muss KEIN einziges Feature einzeln kennen. Es laeuft nur
 * ueber die Modul-Liste und die Settings jedes Moduls. Neues Feature
 * = neue Module-Unterklasse registrieren, GUI bleibt unangetastet.
 */
public abstract class Module {

    public enum Category {
        HUD, PVP, PERFORMANCE, MISC
    }

    private final String name;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();

    private final BooleanSetting enabled = new BooleanSetting("Aktiviert", false);

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
        // enabled ist selbst ein Setting -> erscheint automatisch im GUI.
        this.settings.add(enabled);
    }

    /** Settings in der Unterklasse hinzufuegen. */
    protected void addSetting(Setting setting) {
        this.settings.add(setting);
    }

    public String getName() { return name; }
    public Category getCategory() { return category; }
    public List<Setting> getSettings() { return settings; }

    public boolean isEnabled() { return enabled.get(); }

    public void setEnabled(boolean value) {
        boolean was = enabled.get();
        enabled.set(value);
        if (value && !was) onEnable();
        if (!value && was) onDisable();
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    // Hooks fuer Unterklassen -- optional zu ueberschreiben.
    protected void onEnable() {}
    protected void onDisable() {}
}
