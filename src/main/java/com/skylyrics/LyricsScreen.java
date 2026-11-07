package com.skylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Settings screen: "Look" tab (theme, sliders, toggles) and "Spotify" tab (credentials + status). */
public class LyricsScreen extends Screen {
    private static final int PW = 300, PH = 218;
    private final Config cfg;
    private final SpotifyLyrics lyr;
    private int tab = 0; // 0 = look, 1 = spotify
    private int px, py;
    private TextFieldWidget idField, secretField, tokenField;
    private ButtonWidget themeBtn;

    public LyricsScreen(Config cfg, SpotifyLyrics lyr) {
        super(Text.literal("SkyLyrics"));
        this.cfg = cfg;
        this.lyr = lyr;
    }

    /** Slider that maps 0..1 to [min, max]. */
    private static class Slide extends SliderWidget {
        private final String label, fmt;
        private final double min, max;
        private final Consumer<Double> onChange;

        Slide(int x, int y, int w, String label, double min, double max, double val, String fmt, Consumer<Double> onChange) {
            super(x, y, w, 20, Text.empty(), (val - min) / (max - min));
            this.label = label; this.min = min; this.max = max; this.fmt = fmt; this.onChange = onChange;
            updateMessage();
        }
        private double get() { return min + this.value * (max - min); }
        @Override protected void updateMessage() { setMessage(Text.literal(label + ": " + String.format(fmt, get()))); }
        @Override protected void applyValue() { onChange.accept(get()); }
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        int x = px + 14, w = PW - 28;

        addDrawableChild(ButtonWidget.builder(Text.literal("Look"), b -> { tab = 0; clearAndInit(); })
                .dimensions(px + 14, py + 28, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Spotify"), b -> { tab = 1; clearAndInit(); })
                .dimensions(px + 88, py + 28, 70, 18).build());

        if (tab == 0) {
            int y = py + 54;
            addDrawableChild(ButtonWidget.builder(onOff("Lyrics", cfg.enabled), b -> {
                cfg.enabled = !cfg.enabled; b.setMessage(onOff("Lyrics", cfg.enabled));
            }).dimensions(x, y, w / 2 - 2, 20).build());
            addDrawableChild(ButtonWidget.builder(onOff("Word reveal", cfg.reveal), b -> {
                cfg.reveal = !cfg.reveal; b.setMessage(onOff("Word reveal", cfg.reveal));
            }).dimensions(x + w / 2 + 2, y, w / 2 - 2, 20).build());

            y += 24;
            addDrawableChild(ButtonWidget.builder(onOff("Follow view", cfg.follow), b -> {
                cfg.follow = !cfg.follow; b.setMessage(onOff("Follow view", cfg.follow));
            }).dimensions(x, y, w / 2 - 2, 20).build());
            themeBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Theme: " + cfg.theme), b -> {
                List<String> names = new ArrayList<>(SkyLyricsClient.THEMES.keySet());
                int i = names.indexOf(cfg.theme);
                cfg.theme = names.get((i + 1) % names.size());
                b.setMessage(Text.literal("Theme: " + cfg.theme));
            }).dimensions(x + w / 2 + 2, y, w / 2 - 2, 20).build());

            y += 28;
            addDrawableChild(new Slide(x, y, w, "Size", 0.5, 6, cfg.scale, "%.1f", v -> cfg.scale = v.floatValue()));
            y += 24;
            addDrawableChild(new Slide(x, y, w, "Curve", 0, 3, cfg.curve, "%.1f", v -> cfg.curve = v.floatValue()));
            y += 24;
            addDrawableChild(new Slide(x, y, w, "Height (deg above horizon)", -10, 70, -cfg.pitch, "%.0f", v -> cfg.pitch = -v.floatValue()));
            y += 24;
            addDrawableChild(new Slide(x, y, w, "Sync offset (ms)", -2000, 2000, cfg.offsetMs, "%.0f", v -> cfg.offsetMs = (int) Math.round(v)));
        } else {
            int y = py + 66;
            idField = field(x, y, w, "Client ID", cfg.clientId);
            y += 36;
            secretField = field(x, y, w, "Client Secret", cfg.clientSecret);
            y += 36;
            tokenField = field(x, y, w, "Refresh Token", cfg.refreshToken);
            y += 28;
            addDrawableChild(ButtonWidget.builder(Text.literal("Save & reconnect"), b -> {
                cfg.clientId = idField.getText().trim();
                cfg.clientSecret = secretField.getText().trim();
                cfg.refreshToken = tokenField.getText().trim();
                lyr.resetToken();
                cfg.save();
                b.setMessage(Text.literal("Saved!"));
            }).dimensions(x, y, w, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(px + PW - 14 - 70, py + PH - 26, 70, 18).build());
    }

    private TextFieldWidget field(int x, int y, int w, String label, String value) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(label));
        f.setMaxLength(256);
        f.setText(value);
        return addDrawableChild(f);
    }

    private static Text onOff(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        // panel
        ctx.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, 0xFF6B5BD6);
        ctx.fill(px, py, px + PW, py + PH, 0xF0141020);
        ctx.fill(px, py, px + PW, py + 22, 0xF01E1830);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(textRenderer, "SkyLyrics", px + 14, py + 7, 0xFFC9B8FF, true);

        // theme preview bar
        int[][] th = SkyLyricsClient.THEMES.getOrDefault(cfg.theme, SkyLyricsClient.THEMES.get("lavender"));
        int bx = px + PW - 14 - 90, by = py + 9, bw = 90;
        if (tab == 0) {
            for (int i = 0; i < bw; i++) {
                double f = i / (double) bw;
                int r = (int) (th[0][0] + (th[1][0] - th[0][0]) * f);
                int g = (int) (th[0][1] + (th[1][1] - th[0][1]) * f);
                int b = (int) (th[0][2] + (th[1][2] - th[0][2]) * f);
                ctx.fill(bx + i, by, bx + i + 1, by + 5, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }

        if (tab == 1) {
            int x = px + 14;
            ctx.drawText(textRenderer, "Client ID", x, py + 55, 0xFFAAAAAA, false);
            ctx.drawText(textRenderer, "Client Secret", x, py + 91, 0xFFAAAAAA, false);
            ctx.drawText(textRenderer, "Refresh Token", x, py + 127, 0xFFAAAAAA, false);
        }

        // status line
        String status;
        if (!lyr.lastError.isEmpty()) status = "\u00a7c" + lyr.lastError;
        else if (lyr.playing) status = "\u00a7aNow playing: \u00a7f" + lyr.artist + " - " + lyr.title + " \u00a77(lyrics: " + lyr.lyricsState + ")";
        else status = "\u00a77Nothing playing on Spotify";
        ctx.drawText(textRenderer, status, px + 14, py + PH - 21, 0xFFFFFFFF, false);
    }

    @Override
    public void close() {
        cfg.save();
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldPause() { return false; }
}
