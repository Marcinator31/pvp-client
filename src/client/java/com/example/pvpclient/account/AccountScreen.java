package com.example.pvpclient.account;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Der Account-Switcher-Bildschirm (Offline-Modus).
 *
 * Oben ein Eingabefeld + "Hinzufuegen": man tippt einen Spielernamen ein und
 * legt damit einen Offline-Account an (kein Microsoft-Login noetig -- ideal
 * fuer Offline-/Cracked-Server). Darunter pro gespeichertem Account eine Zeile
 * mit "Wechseln" und "Entfernen".
 *
 * Echte Microsoft-Logins braeuchten eine Azure-App-Registrierung; das ist hier
 * bewusst weggelassen, damit der Switcher ohne weiteres Setup funktioniert.
 */
public class AccountScreen extends Screen {

    private TextFieldWidget nameField;

    public AccountScreen() {
        super(Text.literal("Accounts"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - 100;
        int y = 40;

        // Eingabefeld fuer den Namen.
        nameField = new TextFieldWidget(this.textRenderer, x, y, 150, 20,
                Text.literal("Name"));
        nameField.setMaxLength(16);
        nameField.setPlaceholder(Text.literal("Spielername..."));
        this.addDrawableChild(nameField);

        // "Hinzufuegen" -- legt einen Offline-Account mit dem Namen an.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Hinzufuegen"),
            btn -> {
                String name = nameField.getText().trim();
                if (!name.isEmpty()) {
                    Account a = new Account(name, "");
                    AccountManager.INSTANCE.add(a);
                    nameField.setText("");
                    this.clearAndInit(); // GUI neu aufbauen -> neuer Account erscheint
                }
            }
        ).dimensions(x + 155, y, 45, 20).build());

        y += 30;

        // Pro gespeichertem Account: "Wechseln" + "Entfernen".
        for (Account acc : new java.util.ArrayList<>(AccountManager.INSTANCE.getAccounts())) {
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Wechseln: " + acc.username),
                btn -> {
                    AccountManager.INSTANCE.switchTo(acc);
                    this.clearAndInit();
                }
            ).dimensions(x, y, 150, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("X"),
                btn -> {
                    AccountManager.INSTANCE.remove(acc);
                    this.clearAndInit();
                }
            ).dimensions(x + 155, y, 45, 20).build());

            y += 24;
        }

        // Schliessen-Button.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Schliessen"),
            btn -> this.close()
        ).dimensions(x, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        // Aktueller Account-Name oben anzeigen.
        String current = "Aktuell: " + this.client.getSession().getUsername();
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(current),
                this.width / 2, 20, 0xFFFFFF00);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
