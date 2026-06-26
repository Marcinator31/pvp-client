package com.example.pvpclient.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Echter Microsoft-Login ueber den OAuth2 Device Code Flow.
 *
 * Ablauf (alles ohne eigenes Azure-Setup, mit der oeffentlichen MC-Client-ID):
 *   1) Device-Code anfordern -> der Nutzer bekommt eine URL + einen Code.
 *   2) Nutzer loggt sich im Browser ein (microsoft.com/link, Code eingeben).
 *   3) Wir pollen, bis Microsoft uns einen Token gibt.
 *   4) MS-Token -> Xbox Live -> XSTS -> Minecraft-Token -> Profil.
 *
 * Der Device Code Flow ist ideal, weil er KEINE Redirect-URI und KEIN Secret
 * braucht -- nur die Client-ID. Wir nutzen die oeffentlich bekannte Client-ID
 * des offiziellen Minecraft-Launchers.
 *
 * Aufrufer: login(onCode) startet den Flow. onCode wird mit der Anzeige-Info
 * (URL + Code) aufgerufen, sobald sie da ist -- die GUI zeigt sie dann an.
 * Die Methode blockiert bis zum Ende (Token da oder Fehler), gehoert also in
 * einen eigenen Thread.
 */
public final class MicrosoftAuth {

    // Oeffentliche Client-ID des offiziellen Minecraft-Launchers.
    private static final String CLIENT_ID = "00000000402b5328";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private MicrosoftAuth() {}

    /** Info, die dem Nutzer angezeigt wird, damit er sich einloggen kann. */
    public static final class DeviceCode {
        public final String userCode;
        public final String verificationUri;
        DeviceCode(String userCode, String verificationUri) {
            this.userCode = userCode;
            this.verificationUri = verificationUri;
        }
    }

    /**
     * Fuehrt den kompletten Login durch. onCode wird aufgerufen, sobald der
     * Geraetecode da ist (zum Anzeigen). Gibt am Ende einen fertigen Account
     * zurueck. Blockiert -> in eigenem Thread aufrufen.
     */
    public static Account login(Consumer<DeviceCode> onCode) throws Exception {
        // --- Schritt 1: Device-Code anfordern ---
        String body = "client_id=" + enc(CLIENT_ID)
                + "&scope=" + enc("XboxLive.signin offline_access");
        JsonObject dc = postForm(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
                body);

        String deviceCode = dc.get("device_code").getAsString();
        String userCode = dc.get("user_code").getAsString();
        String verUri = dc.has("verification_uri")
                ? dc.get("verification_uri").getAsString()
                : dc.get("verification_uri_complete").getAsString();
        int interval = dc.has("interval") ? dc.get("interval").getAsInt() : 5;

        // GUI ueber den Code informieren.
        onCode.accept(new DeviceCode(userCode, verUri));

        // --- Schritt 2: Auf den Token pollen ---
        String msAccessToken = null;
        long deadline = System.currentTimeMillis() + 15 * 60 * 1000L; // 15 min
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(interval, 1) * 1000L);

            String pollBody = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                    + "&client_id=" + enc(CLIENT_ID)
                    + "&device_code=" + enc(deviceCode);
            JsonObject poll = postFormAllowError(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    pollBody);

            if (poll.has("access_token")) {
                msAccessToken = poll.get("access_token").getAsString();
                break;
            }
            if (poll.has("error")) {
                String err = poll.get("error").getAsString();
                // authorization_pending / slow_down -> weiter warten.
                if (err.equals("authorization_pending") || err.equals("slow_down")) {
                    if (err.equals("slow_down")) interval += 5;
                    continue;
                }
                // Alles andere ist ein echter Fehler.
                throw new RuntimeException("Microsoft-Login abgebrochen: " + err);
            }
        }
        if (msAccessToken == null) {
            throw new RuntimeException("Zeitueberschreitung beim Microsoft-Login.");
        }

        // --- Schritt 3: Xbox Live (XBL) ---
        JsonObject xblReq = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
        xblReq.add("Properties", xblProps);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate",
                xblReq.toString());
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // --- Schritt 4: XSTS ---
        JsonObject xstsReq = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        xstsProps.add("UserTokens", tokens);
        xstsReq.add("Properties", xstsProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        JsonObject xsts = postJsonAllowError(
                "https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq.toString());
        if (xsts.has("XErr")) {
            long xerr = xsts.get("XErr").getAsLong();
            throw new RuntimeException(xstsErrorMessage(xerr));
        }
        String xstsToken = xsts.get("Token").getAsString();

        // --- Schritt 5: Minecraft-Login ---
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc = postJson(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                mcReq.toString());
        String mcToken = mc.get("access_token").getAsString();

        // --- Schritt 6: Profil holen ---
        HttpRequest profReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcToken)
                .GET().build();
        HttpResponse<String> profResp = HTTP.send(profReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject profile = JsonParser.parseString(profResp.body()).getAsJsonObject();
        if (!profile.has("id")) {
            throw new RuntimeException(
                "Kein Minecraft-Profil gefunden (besitzt dieser Account das Spiel?).");
        }
        String uuid = profile.get("id").getAsString();
        String name = profile.get("name").getAsString();

        // Fertiger Account mit echtem Token.
        Account acc = new Account(name, formatUuid(uuid));
        acc.accessToken = mcToken;
        return acc;
    }

    // ---- HTTP-Helfer ----

    private static JsonObject postForm(String url, String body) throws Exception {
        JsonObject o = postFormAllowError(url, body);
        return o;
    }

    private static JsonObject postFormAllowError(String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static JsonObject postJson(String url, String json) throws Exception {
        JsonObject o = postJsonAllowError(url, json);
        return o;
    }

    private static JsonObject postJsonAllowError(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Wandelt eine UUID ohne Bindestriche ins Standardformat. */
    private static String formatUuid(String raw) {
        if (raw.length() != 32) return raw;
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-"
                + raw.substring(20);
    }

    private static String xstsErrorMessage(long xerr) {
        if (xerr == 2148916233L) return "Dieser Microsoft-Account hat kein Xbox-Profil.";
        if (xerr == 2148916235L) return "Xbox Live ist in deiner Region nicht verfuegbar.";
        if (xerr == 2148916238L) return "Kinderkonto -- muss einer Familie hinzugefuegt werden.";
        return "Xbox-Login fehlgeschlagen (XErr " + xerr + ").";
    }
}
