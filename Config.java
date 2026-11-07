package com.skylyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public boolean enabled = true;
    public String clientId = "";
    public String clientSecret = "";
    public String refreshToken = "";
    public String theme = "lavender";
    public float scale = 2.0f;     // text size
    public float curve = 1.0f;     // 0 = flat line, higher = more bend
    public float pitch = -22f;     // degrees; negative = above the horizon
    public int offsetMs = 0;       // + = lyrics later, - = earlier
    public boolean reveal = true;  // reveal words as they are sung
    public boolean follow = false; // true = lyrics follow your view

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("skylyrics.json"); }

    public static Config load() {
        try {
            if (Files.exists(file())) {
                Config c = GSON.fromJson(Files.readString(file()), Config.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) { }
        return new Config();
    }

    public void save() {
        try { Files.writeString(file(), GSON.toJson(this)); } catch (Exception ignored) { }
    }
}
