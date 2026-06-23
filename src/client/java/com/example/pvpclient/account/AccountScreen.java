package com.example.pvpclient.account;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Der Account-Switcher-Bildschirm.
 *
 * Bewusst minimal: pro gespeichertem Account ein Button, der per Klick
 * switchTo() aufruft. Plus ein "Account hinzufuegen"-Button, der spaeter
 * MicrosoftAuth.login() startet.
 *
 * GUI-Code ist normaler Minecraft-Screen-Kram -- kein Mixin noetig.
 * Du kannst das beliebig huebsch machen (Avatare, Suchfeld ...), aber
 * erst soll es funktionieren.
 */
public class AccountScreen extends Screen {

    public AccountScreen() {
        super(Text.literal("Accounts"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = 40;

        // Ein Button pro gespeichertem Account.
        for (Account acc : AccountManager.INSTANCE.getAccounts()) {
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Wechseln zu: " + acc.username),
                btn -> AccountManager.INSTANCE.switchTo(acc)
            ).dimensions(x, y, 200, 20).build());
            y += 24;
        }

        // "Account hinzufuegen" -- startet spaeter den Login.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("+ Account hinzufuegen"),
            btn -> {
                // Login laeuft ueber Netzwerk -> spaeter in eigenen Thread,
                // damit das Spiel nicht einfriert. Fuer jetzt nur der Aufruf:
                try {
                    Account a = MicrosoftAuth.login();
                    AccountManager.INSTANCE.add(a);
                    this.clearAndInit(); // GUI neu aufbauen
                } catch (Exception e) {
                    System.out.println("[pvpclient] Login fehlgeschlagen: " + e.getMessage());
                }
            }
        ).dimensions(x, y + 10, 200, 20).build());

        // Schliessen-Button.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Schliessen"),
            btn -> this.close()
        ).dimensions(x, this.height - 30, 200, 20).build());
    }
}
