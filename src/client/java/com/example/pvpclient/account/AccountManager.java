package com.example.pvpclient.account;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet die gespeicherten Accounts und fuehrt den eigentlichen
 * "Switch" aus -- also das Umsetzen der aktiven Minecraft-Session.
 *
 * Das WIE des Login (Microsoft-OAuth) steckt in MicrosoftAuth.
 * Diese Klasse kuemmert sich nur darum, was man mit einem bereits
 * eingeloggten Account macht.
 */
public final class AccountManager {

    public static final AccountManager INSTANCE = new AccountManager();

    private final List<Account> accounts = new ArrayList<>();

    private AccountManager() {}

    public List<Account> getAccounts() {
        return accounts;
    }

    public void add(Account account) {
        accounts.add(account);
        // TODO: hier persistent speichern (siehe AccountStorage-Hinweis unten).
    }

    public void remove(Account account) {
        accounts.remove(account);
        // TODO: ebenfalls speichern.
    }

    /**
     * DER KERN DES SWITCHERS.
     *
     * Minecraft haelt die aktive Anmeldung in einem Session-Objekt.
     * "Account wechseln" heisst: ein neues Session-Objekt mit den Daten
     * des Ziel-Accounts bauen und in den Client setzen.
     *
     * ACHTUNG -- zwei reale Stolpersteine:
     *
     * 1) MinecraftClient.session ist final. Man kommt da nur per
     *    Reflection oder (sauberer) per Mixin/Accessor ran, der das
     *    Feld beschreibbar macht. Du brauchst also einen kleinen
     *    @Accessor-Mixin auf MinecraftClient, der setSession() freilegt.
     *    (Suchbegriff fuer dich: "fabric accessor mixin private field".)
     *
     * 2) Der accessToken im Account muss GUELTIG sein. Tokens laufen ab.
     *    Vor dem Switch ggf. per refreshToken erneuern (MicrosoftAuth).
     *    Mit einem abgelaufenen Token kannst du keine Online-Server
     *    joinen (Authentifizierung schlaegt fehl).
     *
     * Der genaue Konstruktor von Session aendert sich zwischen MC-Versionen.
     * Beim Build zeigt dir die IDE die erwarteten Parameter -- nimm die.
     */
    public void switchTo(Account account) {
        if (account.accessToken == null) {
            System.out.println("[pvpclient] Kein gueltiges Token -- erst einloggen/refreshen.");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // Pseudocode -- die echten Parameter zeigt dir die IDE an:
        //
        // Session session = new Session(
        //     account.username,
        //     UuidUtil.fromString(account.uuid),
        //     account.accessToken,
        //     Optional.empty(),     // xuid
        //     Optional.empty(),     // clientId
        //     Session.AccountType.MSA
        // );
        //
        // Und dann ueber deinen Accessor-Mixin:
        // ((MinecraftClientAccessor) client).setSession(session);

        System.out.println("[pvpclient] Wechsle zu Account: " + account.username);
    }
}
