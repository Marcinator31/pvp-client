package com.example.pvpclient.account;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Der Account-Switcher-Bildschirm mit echtem Microsoft-Login.
 *
 * "Microsoft-Login" startet den Device Code Flow: der Client holt einen Code,
 * zeigt ihn an und oeffnet die Login-Seite im Browser. Der Nutzer gibt den Code
 * ein und loggt sich bei Microsoft ein; sobald das fertig ist, erscheint der
 * Account in der Liste. Der Login laeuft in einem eigenen Thread, damit das
 * Spiel nicht einfriert.
 *
 * Darunter pro gespeichertem Account "Wechseln" und "Entfernen".
 */
public class AccountScreen extends Screen {

    // Status-Text fuer den laufenden Login (Code, Fehler, Erfolg).
    private volatile String statusLine = "";
    private volatile String codeLine = "";
    private volatile boolean loggingIn = false;

    public AccountScreen() {
        super(Text.literal("Accounts"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - 100;
        int y = 44;

        // "Microsoft-Login" -- startet den Device Code Flow.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(loggingIn ? "Login laeuft..." : "+ Microsoft-Login"),
            btn -> startMicrosoftLogin()
        ).dimensions(x, y, 200, 20).build());

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

    /** Startet den Microsoft Device Code Flow in einem Hintergrund-Thread. */
    private void startMicrosoftLogin() {
        if (loggingIn) return;
        loggingIn = true;
        statusLine = "Verbinde mit Microsoft...";
        codeLine = "";
        this.clearAndInit();

        Thread t = new Thread(() -> {
            try {
                Account acc = MicrosoftAuth.login(code -> {
                    // Code anzeigen + Browser oeffnen.
                    codeLine = "Code: " + code.userCode;
                    statusLine = "Oeffne Browser... Code eingeben: " + code.userCode;
                    try {
                        Util.getOperatingSystem().open(new java.net.URI(code.verificationUri));
                    } catch (Throwable ignored) {
                        statusLine = "Gehe zu " + code.verificationUri
                                + " und gib den Code ein.";
                    }
                });

                // Erfolg -> Account speichern.
                AccountManager.INSTANCE.add(acc);
                statusLine = "Eingeloggt als " + acc.username + "!";
                codeLine = "";
            } catch (Throwable e) {
                statusLine = "Fehler: " + e.getMessage();
                codeLine = "";
            } finally {
                loggingIn = false;
                // GUI im Main-Thread neu aufbauen.
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                    if (this.client != null && this.client.currentScreen == this) {
                        this.clearAndInit();
                    }
                });
            }
        }, "pvpclient-ms-login");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Aktueller Account-Name oben.
        String current = "Aktuell: " + this.client.getSession().getUsername();
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(current),
                this.width / 2, 18, 0xFFFFFF00);

        // Status + Code (waehrend/nach dem Login).
        if (!statusLine.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusLine),
                    this.width / 2, this.height - 52, 0xFF55FF55);
        }
        if (!codeLine.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(codeLine),
                    this.width / 2, this.height - 64, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
