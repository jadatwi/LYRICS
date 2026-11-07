package com.skylyrics;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SkyLyricsClient implements ClientModInitializer {
    static final Map<String, int[][]> THEMES = new LinkedHashMap<>();
    static {
        THEMES.put("lavender", new int[][]{{170, 150, 255}, {120, 190, 255}});
        THEMES.put("candy",    new int[][]{{255, 120, 200}, {255, 190, 120}});
        THEMES.put("sunset",   new int[][]{{255, 170, 80},  {255, 90, 150}});
        THEMES.put("ice",      new int[][]{{200, 245, 255}, {120, 190, 255}});
        THEMES.put("royal",    new int[][]{{255, 215, 90},  {180, 110, 255}});
        THEMES.put("mint",     new int[][]{{140, 255, 200}, {100, 220, 255}});
    }

    private final Config cfg = Config.load();
    private final SpotifyLyrics lyr = new SpotifyLyrics(cfg);
    private int curIdx = -1;
    private float lockedYaw = 0;

    @Override
    public void onInitializeClient() {
        lyr.start();
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of("skylyrics", "lyrics"), this::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(buildCommand()));
    }

    // ---------------- rendering ----------------

    private void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.enabled || !lyr.playing || mc.player == null || mc.options.hudHidden) return;
        List<SpotifyLyrics.Line> lines = lyr.lines;
        if (lines.isEmpty()) return;

        long prog = lyr.now() + cfg.offsetMs;
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) { if (lines.get(i).t() <= prog) idx = i; else break; }
        if (idx < 0) return;

        Camera cam = mc.gameRenderer.getCamera();
        float camYaw = cam.getYaw(), camPitch = cam.getPitch();
        if (idx != curIdx) { curIdx = idx; lockedYaw = camYaw; }

        SpotifyLyrics.Line line = lines.get(idx);
        if (line.text().isEmpty()) return;
        long start = line.t();
        long end = idx + 1 < lines.size() ? lines.get(idx + 1).t() : start + 4000;
        long elapsed = prog - start;
        double alpha = Math.max(0.05, Math.min(1.0, Math.min(elapsed / 350.0, (end - prog) / 450.0 + 0.2)));

        String[] words = line.text().trim().split("\\s+");
        int n = words.length;
        int shown = n;
        if (cfg.reveal) shown = Math.min(n, Math.max(1, (int) Math.ceil(n * Math.min(1.0, elapsed / ((end - start) * 0.75)))));

        TextRenderer tr = mc.textRenderer;
        int W = mc.getWindow().getScaledWidth(), H = mc.getWindow().getScaledHeight();
        double tanV = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double ppd = (H / 2.0) / tanV * Math.PI / 180.0; // pixels per degree near screen centre
        double s = cfg.scale;

        double[] wDeg = new double[n];
        double gapDeg = tr.getWidth(" ") * s / ppd, total = 0;
        for (int i = 0; i < n; i++) { wDeg[i] = tr.getWidth(words[i]) * s / ppd; total += wDeg[i] + (i < n - 1 ? gapDeg : 0); }

        float baseYaw = cfg.follow ? camYaw : lockedYaw;
        int[][] th = THEMES.getOrDefault(cfg.theme, THEMES.get("lavender"));
        double t = System.currentTimeMillis() / 1000.0;

        double pos = -total / 2;
        for (int i = 0; i < shown; i++) {
            double a = pos + wDeg[i] / 2;
            pos += wDeg[i] + gapDeg;
            double bob = Math.sin(t * 1.5 + i * 0.6) * 0.5;

            double[] p = project(baseYaw, a, bob, camYaw, camPitch, W, H, tanV);
            double[] p1 = project(baseYaw, a - 1, bob, camYaw, camPitch, W, H, tanV);
            double[] p2 = project(baseYaw, a + 1, bob, camYaw, camPitch, W, H, tanV);
            if (p == null || p1 == null || p2 == null) continue;
            float ang = (float) Math.atan2(p2[1] - p1[1], p2[0] - p1[0]);

            double f = n > 1 ? (double) i / (n - 1) : 0;
            int r = (int) (th[0][0] + (th[1][0] - th[0][0]) * f);
            int g = (int) (th[0][1] + (th[1][1] - th[0][1]) * f);
            int b = (int) (th[0][2] + (th[1][2] - th[0][2]) * f);
            int color = ((int) (255 * alpha) << 24) | (r << 16) | (g << 8) | b;

            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate((float) p[0], (float) p[1]);
            m.rotate(ang);
            m.scale((float) s, (float) s);
            ctx.drawText(tr, words[i], -tr.getWidth(words[i]) / 2, -4, color, true);
            m.popMatrix();
        }
    }

    /** Projects a point on the "sky sphere" (yaw offset a degrees from the line centre) to screen pixels. */
    private double[] project(float baseYaw, double a, double bob, float camYaw, float camPitch, int W, int H, double tanV) {
        double yaw = Math.toRadians(baseYaw + a);
        // positive pitch = down in Minecraft, so edges (|a| large) droop slightly: that is the curve
        double pitch = Math.toRadians(cfg.pitch + cfg.curve * a * a * 0.012 + bob);
        double vx = -Math.sin(yaw) * Math.cos(pitch), vy = -Math.sin(pitch), vz = Math.cos(yaw) * Math.cos(pitch);

        double cy = Math.toRadians(camYaw), cp = Math.toRadians(camPitch);
        double fx = -Math.sin(cy) * Math.cos(cp), fy = -Math.sin(cp), fz = Math.cos(cy) * Math.cos(cp);
        double rx = -Math.cos(cy), ry = 0, rz = -Math.sin(cy);
        double ux = -Math.sin(cy) * Math.sin(cp), uy = Math.cos(cp), uz = Math.cos(cy) * Math.sin(cp);

        double depth = vx * fx + vy * fy + vz * fz;
        if (depth < 0.1) return null;
        double x = (vx * rx + vy * ry + vz * rz) / depth;
        double y = (vx * ux + vy * uy + vz * uz) / depth;
        return new double[]{W / 2.0 + x / tanV * (H / 2.0), H / 2.0 - y / tanV * (H / 2.0)};
    }

    // ---------------- commands ----------------

    private static int msg(CommandContext<FabricClientCommandSource> c, String s) {
        c.getSource().sendFeedback(Text.literal("[SkyLyrics] " + s));
        return 1;
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildCommand() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("lyrics");

        root.then(literal("on").executes(c -> { cfg.enabled = true; cfg.save(); return msg(c, "on"); }));
        root.then(literal("off").executes(c -> { cfg.enabled = false; cfg.save(); return msg(c, "off"); }));
        root.then(literal("reveal").executes(c -> { cfg.reveal = !cfg.reveal; cfg.save(); return msg(c, "reveal = " + cfg.reveal); }));
        root.then(literal("follow").executes(c -> { cfg.follow = !cfg.follow; cfg.save(); return msg(c, "follow = " + cfg.follow); }));

        root.then(literal("status").executes(c -> msg(c, "playing=" + lyr.playing + " | " + lyr.artist + " - " + lyr.title
                + " | lyrics: " + lyr.lyricsState + " (" + lyr.lines.size() + " lines)"
                + (lyr.lastError.isEmpty() ? "" : " | " + lyr.lastError))));

        root.then(literal("setup").executes(c -> {
            msg(c, "1) Create an app at developer.spotify.com/dashboard, redirect URI: http://127.0.0.1:8888/callback");
            msg(c, "2) Run: node get_token.js <CLIENT_ID> <CLIENT_SECRET>  (gives you a refresh token)");
            msg(c, "3) /lyrics set clientId <id>, /lyrics set clientSecret <secret>, /lyrics set refreshToken <token>");
            return 1;
        }));

        for (String key : new String[]{"clientId", "clientSecret", "refreshToken"}) {
            root.then(literal("set").then(literal(key).then(argument("value", StringArgumentType.greedyString()).executes(c -> {
                String v = StringArgumentType.getString(c, "value").trim();
                switch (key) {
                    case "clientId" -> cfg.clientId = v;
                    case "clientSecret" -> cfg.clientSecret = v;
                    default -> cfg.refreshToken = v;
                }
                lyr.resetToken();
                cfg.save();
                return msg(c, "saved " + key);
            }))));
        }

        root.then(literal("theme").then(argument("name", StringArgumentType.word())
                .suggests((c, b) -> { THEMES.keySet().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> {
                    String name = StringArgumentType.getString(c, "name").toLowerCase();
                    if (!THEMES.containsKey(name)) return msg(c, "themes: " + String.join(", ", THEMES.keySet()));
                    cfg.theme = name; cfg.save();
                    return msg(c, "theme = " + name);
                })));

        root.then(literal("scale").then(argument("v", FloatArgumentType.floatArg(0.5f, 10f)).executes(c -> {
            cfg.scale = FloatArgumentType.getFloat(c, "v"); cfg.save(); return msg(c, "scale = " + cfg.scale); })));
        root.then(literal("curve").then(argument("v", FloatArgumentType.floatArg(0f, 5f)).executes(c -> {
            cfg.curve = FloatArgumentType.getFloat(c, "v"); cfg.save(); return msg(c, "curve = " + cfg.curve); })));
        root.then(literal("pitch").then(argument("v", FloatArgumentType.floatArg(-80f, 60f)).executes(c -> {
            cfg.pitch = FloatArgumentType.getFloat(c, "v"); cfg.save(); return msg(c, "pitch = " + cfg.pitch + " (negative = higher in the sky)"); })));
        root.then(literal("offset").then(argument("ms", IntegerArgumentType.integer(-5000, 5000)).executes(c -> {
            cfg.offsetMs = IntegerArgumentType.getInteger(c, "ms"); cfg.save(); return msg(c, "offset = " + cfg.offsetMs + " ms"); })));

        root.then(literal("gui").executes(c -> { openGui(); return 1; }));

        root.then(literal("help").executes(c -> {
            msg(c, "/lyrics  (opens the settings screen)");
            msg(c, "/lyrics on|off|status|setup|reveal|follow");
            msg(c, "/lyrics theme <" + String.join("|", THEMES.keySet()) + ">");
            msg(c, "/lyrics scale|curve|pitch <number>, /lyrics offset <ms>");
            return 1;
        }));

        return root.executes(c -> { openGui(); return 1; });
    }

    private void openGui() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // delay one tick: the chat screen closes right after a command runs and would cancel setScreen
        mc.send(() -> mc.setScreen(new LyricsScreen(cfg, lyr)));
    }
}
