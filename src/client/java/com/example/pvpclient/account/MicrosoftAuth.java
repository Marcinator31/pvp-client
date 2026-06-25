package com.example.pvpclient.account;

/**
 * Microsoft-Login (MSA) -- das GERUEST mit Erklaerung.
 *
 * ============================================================
 *  LIES DAS, BEVOR DU HIER CODE EINFUEGST.
 * ============================================================
 *
 * Der Login besteht aus einer FESTEN KETTE von Schritten. Du musst
 * jeden verstehen, sonst debuggst du blind. Reihenfolge:
 *
 *  1) Microsoft OAuth2  -> liefert einen Microsoft-"access token"
 *     (NICHT der Minecraft-Token! Nur der Einstieg.)
 *
 *  2) Xbox Live (XBL)   -> tausche den MS-Token gegen ein XBL-Token
 *     Endpoint: user.auth.xboxlive.com/user/authenticate
 *
 *  3) XSTS              -> tausche das XBL-Token gegen ein XSTS-Token
 *     Endpoint: xsts.auth.xboxlive.com/xsts/authorize
 *     (Hier kommen typische Fehlercodes wie 2148916233 = kein
 *      Xbox-Konto. Diese Faelle musst du abfangen.)
 *
 *  4) Minecraft Services -> XSTS-Token gegen den ECHTEN Minecraft-
 *     accessToken tauschen
 *     Endpoint: api.minecraftservices.com/authentication/login_with_xbox
 *
 *  5) Profil holen      -> username + uuid
 *     Endpoint: api.minecraftservices.com/minecraft/profile
 *
 * Erst nach Schritt 5 hast du alles fuer ein Account-Objekt.
 *
 * ------------------------------------------------------------
 *  WAS DU SELBST BESORGEN MUSST (kann dir niemand geben):
 * ------------------------------------------------------------
 *
 *  - Eine AZURE APP-REGISTRIERUNG. Geh ins Azure-Portal,
 *    registriere eine App, aktiviere "Live SDK support" /
 *    den passenden Auth-Flow, und du bekommst eine CLIENT_ID.
 *    Diese Client-ID gehoert hier rein. Ohne sie laeuft NICHTS.
 *
 *  - Den passenden Redirect/Flow. Fuer einen Desktop-Mod nimmt man
 *    ueblicherweise den "Device Code Flow" oder einen lokalen
 *    Redirect (http://localhost mit kurzem eingebettetem Webserver).
 *    Device Code ist am einfachsten und am sichersten fuer den
 *    Anfang: der Nutzer bekommt einen Code + Link, loggt sich im
 *    Browser ein, dein Mod pollt bis es fertig ist.
 *
 * ------------------------------------------------------------
 *  SICHERHEIT -- nicht optional:
 * ------------------------------------------------------------
 *
 *  - CLIENT_ID ist nicht geheim, aber accessToken/refreshToken SIND es.
 *  - Logge niemals Tokens (kein System.out.println(token)).
 *  - Committe niemals die gespeicherte Account-Datei.
 *  - Benutze NUR die echten Microsoft/Mojang-Endpoints oben.
 *    Wenn dich irgendeine Anleitung auf einen anderen Host
 *    umleitet, der "den Login einfacher macht" -> Finger weg,
 *    das greift Accounts ab.
 */
public final class MicrosoftAuth {

    // Deine eigene Azure-App-Client-ID hier eintragen:
    private static final String CLIENT_ID = "DEINE_AZURE_CLIENT_ID_HIER";

    private MicrosoftAuth() {}

    /**
     * Startet den Login und gibt am Ende einen fertigen, eingeloggten
     * Account zurueck (oder wirft bei Fehler).
     *
     * Implementiere hier die 5 Schritte aus dem Klassen-Kommentar.
     * Empfehlung: jeden Schritt als eigene private Methode, die das
     * Ergebnis des vorherigen entgegennimmt. Dann ist es testbar und
     * du siehst genau, an welchem Schritt es klemmt.
     */
    public static Account login() {
        if (CLIENT_ID.startsWith("DEINE_")) {
            throw new IllegalStateException(
                "Keine Azure CLIENT_ID gesetzt. Erst eine App im Azure-Portal "
                + "registrieren und die Client-ID in MicrosoftAuth eintragen."
            );
        }

        // TODO Schritt 1: Microsoft OAuth (Device Code Flow empfohlen)
        // TODO Schritt 2: XBL
        // TODO Schritt 3: XSTS
        // TODO Schritt 4: Minecraft login_with_xbox
        // TODO Schritt 5: Profil (username, uuid)

        throw new UnsupportedOperationException("OAuth-Flow noch nicht implementiert.");
    }
}
