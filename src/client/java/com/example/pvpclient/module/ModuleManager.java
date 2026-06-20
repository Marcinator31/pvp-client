package com.example.pvpclient.module;

import com.example.pvpclient.module.modules.ArmorHudModule;
import com.example.pvpclient.module.modules.CpsModule;
import com.example.pvpclient.module.modules.FpsModule;
import com.example.pvpclient.module.modules.HitboxModule;
import com.example.pvpclient.module.modules.ToggleSprintModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Liste aller Module. Hier wird jedes Feature EINMAL
 * registriert -- danach taucht es ueberall automatisch auf
 * (im GUI, beim Speichern, beim Rendern).
 */
public final class ModuleManager {

    public static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        // --- Hier registrierst du neue Features ---
        register(new CpsModule());
        register(new ArmorHudModule());
        register(new FpsModule());
        register(new ToggleSprintModule());
        register(new HitboxModule());
        // Weitere kommen einfach hier dazu.
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    /** Alle Module einer Kategorie -- praktisch fuers GUI. */
    public List<Module> getByCategory(Module.Category category) {
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) result.add(m);
        }
        return result;
    }
}
