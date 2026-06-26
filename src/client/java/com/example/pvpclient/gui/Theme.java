package com.example.pvpclient.gui;

import com.example.pvpclient.core.setting.ColorSetting;

/**
 * Zentrale Farbgebung des Client-GUI.
 *
 * Statt ueberall Farben hartzukodieren, kommen alle GUI-Farben von hier.
 * Das ist die Grundlage fuers "alles farblich anpassen": aendere den
 * Wert hier (oder spaeter ueber einen Farbwaehler im Menue), und das
 * ganze GUI zieht mit.
 *
 * Es sind ColorSettings -- also direkt speicherbar und im GUI editierbar.
 */
public final class Theme {

    public static final Theme INSTANCE = new Theme();

    public final ColorSetting accent      = new ColorSetting("Akzent",        0xFF4C8BF5); // blau
    public final ColorSetting background   = new ColorSetting("Hintergrund",   0xCC1A1A1E); // dunkel, halbtransparent
    public final ColorSetting panel        = new ColorSetting("Panel",         0xFF232329);
    public final ColorSetting text         = new ColorSetting("Text",          0xFFFFFFFF);
    public final ColorSetting textDim       = new ColorSetting("Text gedimmt",  0xFFB0B0B8);
    public final ColorSetting enabledColor = new ColorSetting("An-Farbe",      0xFF55FF7A); // gruen
    public final ColorSetting disabledColor = new ColorSetting("Aus-Farbe",     0xFF55585F); // grau

    private Theme() {}
}
