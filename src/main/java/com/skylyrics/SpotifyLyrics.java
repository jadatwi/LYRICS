package com.skylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Talks to Spotify (currently playing) and LRCLIB (synced lyrics) on a background thread. */
public class SpotifyLyrics {
    public record Line(long t, String text) { }

    public volatile boolean playing = false;
    public volatile String title = "", artist = "", lyricsState = "none";
    public volatile List<Line> lines = List.of();
    private volatile long progressMs = 0, syncedAt = 0;
    public volatile String lastError = "";

    private final Config cfg;
    private final HttpClient http = HttpClient.newHttpClient();
    private String token = null;
    private long tokenExpiry = 0;
    private String trackId = null;

    public SpotifyLyrics(Config cfg) { this.cfg = cfg; }

    public long now() { return progressMs + (System.currentTimeMillis() - syncedAt); }

    public void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SkyLyrics-poll");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            try { poll(); } catch (Exception e) { lastError = String.valueOf(e); playing = false; }
        }, 1, 2, TimeUnit.SECONDS);
    }

    public void resetToken() { token = null; tokenExpiry = 0; }

    private boolean ensureToken() throws Exception {
        if (token != null && System.currentTimeMillis() < tokenExpiry - 30000) return true;
        if (cfg.clientId.isEmpty() || cfg.clientSecret.isEmpty() || cfg.refreshToken.isEmpty()) {
            lastError = "Not connected. Run /lyrics setup";
            return false;
        }
        String basic = Base64.getEncoder().encodeToString(
                (cfg.clientId + ":" + cfg.clientSecret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(cfg.refreshToken, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) { lastError = "Spotify login failed (" + res.statusCode() + ")"; return false; }
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        token = o.get("access_token").getAsString();
        tokenExpiry = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000;
        if (o.has("refresh_token")) { cfg.refreshToken = o.get("refresh_token").getAsString(); cfg.save(); }
        lastError = "";
        return true;
    }

    private void poll() throws Exception {
        if (!ensureToken()) { playing = false; return; }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        if (code == 401) { token = null; lastError = "Spotify 401: token rejected, retrying"; playing = false; return; }
        if (code == 204 || (code == 200 && res.body().isEmpty())) {
            lastError = "Spotify sees no active player (204). Press play in the Spotify app, same account.";
            playing = false; return;
        }
        if (code != 200) {
            String b = res.body() == null ? "" : res.body();
            lastError = "Spotify error " + code + ": " + (b.length() > 120 ? b.substring(0, 120) : b);
            playing = false; return;
        }
        lastError = "";

        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        JsonElement item = o.get("item");
        if (item == null || item.isJsonNull()) { lastError = "Spotify: no track info (ad, podcast or local file?)"; playing = false; return; }
        if (!o.get("is_playing").getAsBoolean()) { lastError = "Spotify says the track is paused"; playing = false; return; }
        JsonObject it = item.getAsJsonObject();

        JsonElement prog = o.get("progress_ms");
        progressMs = prog == null || prog.isJsonNull() ? 0 : prog.getAsLong();
        syncedAt = System.currentTimeMillis();
        playing = true;

        String id = it.has("id") && !it.get("id").isJsonNull() ? it.get("id").getAsString() : it.get("name").getAsString();
        if (!id.equals(trackId)) {
            trackId = id;
            title = it.get("name").getAsString();
            JsonArray arts = it.has("artists") ? it.getAsJsonArray("artists") : new JsonArray();
            artist = arts.size() > 0 ? arts.get(0).getAsJsonObject().get("name").getAsString() : "";
            lines = List.of();
            lyricsState = "loading";
            fetchLyrics(title, artist, it.get("duration_ms").getAsLong() / 1000, id);
        }
    }

    private void fetchLyrics(String track, String art, long durSec, String forId) {
        try {
            String url = "https://lrclib.net/api/get?track_name=" + URLEncoder.encode(track, StandardCharsets.UTF_8)
                    + "&artist_name=" + URLEncoder.encode(art, StandardCharsets.UTF_8) + "&duration=" + durSec;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyLyrics/1.0").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (!forId.equals(trackId)) return;
            if (res.statusCode() == 200) {
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                JsonElement s = o.get("syncedLyrics");
                if (s != null && !s.isJsonNull()) {
                    List<Line> parsed = parseLrc(s.getAsString());
                    lines = parsed;
                    lyricsState = parsed.isEmpty() ? "none" : "ok";
                    return;
                }
            }
            lyricsState = "none";
        } catch (Exception e) {
            lyricsState = "none";
        }
    }

    private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");

    static List<Line> parseLrc(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String row : lrc.split("\n")) {
            Matcher m = TS.matcher(row);
            List<Long> times = new ArrayList<>();
            while (m.find()) times.add((long) ((Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2))) * 1000));
            String text = row.replaceAll("\\[[^\\]]*]", "").trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::t));
        return out;
    }
}
