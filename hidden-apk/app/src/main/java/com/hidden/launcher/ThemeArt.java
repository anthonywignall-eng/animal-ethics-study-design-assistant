package com.hidden.launcher;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Random;

public final class ThemeArt {
    private ThemeArt() {}

    public static String resolve(String theme, Resources res) {
        if (!"system".equals(theme)) return theme;
        int night = res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
    }

    public static int background(String rawTheme, Resources res) {
        String theme = resolve(rawTheme, res);
        switch (theme) {
            case "dark": return Color.rgb(18, 18, 18);
            case "oled": return Color.BLACK;
            case "doom_light": return Color.rgb(245, 239, 226);
            case "doom_dark": return Color.rgb(5, 6, 6);
            case "soft_launch": return Color.rgb(250, 226, 217);
            case "moth": return Color.rgb(24, 21, 29);
            case "8bit": return Color.rgb(10, 14, 28);
            default: return Color.rgb(241, 239, 232);
        }
    }

    public static int foreground(String rawTheme, Resources res) {
        String theme = resolve(rawTheme, res);
        switch (theme) {
            case "light":
            case "doom_light":
                return Color.rgb(26, 25, 22);
            case "8bit":
                return Color.rgb(244, 232, 180);
            case "doom_dark":
                return Color.rgb(235, 218, 195);
            case "soft_launch":
                return Color.rgb(72, 43, 54);
            case "moth":
                return Color.rgb(239, 229, 214);
            default:
                return Color.rgb(242, 242, 238);
        }
    }

    public static int muted(String rawTheme, Resources res) {
        String theme = resolve(rawTheme, res);
        switch (theme) {
            case "light":
            case "doom_light":
                return Color.rgb(112, 108, 99);
            case "8bit":
                return Color.rgb(154, 166, 124);
            case "doom_dark":
                return Color.rgb(155, 145, 128);
            case "soft_launch":
                return Color.rgb(139, 96, 108);
            case "moth":
                return Color.rgb(166, 151, 139);
            default:
                return Color.rgb(155, 155, 149);
        }
    }

    public static int panel(String rawTheme, Resources res) {
        String theme = resolve(rawTheme, res);
        switch (theme) {
            case "light": return Color.rgb(227, 224, 215);
            case "doom_light": return Color.rgb(235, 226, 208);
            case "dark": return Color.rgb(31, 31, 31);
            case "oled": return Color.rgb(13, 13, 13);
            case "doom_dark": return Color.rgb(19, 17, 15);
            case "soft_launch": return Color.rgb(244, 204, 201);
            case "moth": return Color.rgb(40, 34, 46);
            case "8bit": return Color.rgb(20, 27, 45);
            default: return Color.rgb(31, 31, 31);
        }
    }

    public static int line(String rawTheme, Resources res) {
        String theme = resolve(rawTheme, res);
        switch (theme) {
            case "light": return Color.rgb(213, 209, 199);
            case "doom_light": return Color.rgb(205, 193, 172);
            case "dark": return Color.rgb(55, 55, 55);
            case "oled": return Color.rgb(38, 38, 38);
            case "doom_dark": return Color.rgb(62, 53, 44);
            case "soft_launch": return Color.rgb(226, 179, 181);
            case "moth": return Color.rgb(76, 65, 78);
            case "8bit": return Color.rgb(83, 96, 67);
            default: return Color.rgb(55, 55, 55);
        }
    }

    public static void draw(Canvas canvas, int width, int height, String rawTheme, Resources res, long seed) {
        String theme = resolve(rawTheme, res);
        canvas.drawColor(background(theme, res));

        if ("doom_light".equals(theme)) {
            drawPaint(canvas, width, height, seed, false);
        } else if ("doom_dark".equals(theme)) {
            drawPaint(canvas, width, height, seed, true);
        } else if ("soft_launch".equals(theme)) {
            drawSoftPaper(canvas, width, height, seed);
            drawSoftBotanicalEdges(canvas, width, height, seed);
        } else if ("moth".equals(theme)) {
            drawMothPaper(canvas, width, height, seed);
        } else if ("8bit".equals(theme)) {
            drawPixels(canvas, width, height, seed);
        }
    }

    private static void drawPaint(Canvas canvas, int width, int height, long seed, boolean dark) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random(seed);

        int[] colors = dark
            ? new int[]{
                Color.rgb(221, 174, 73),
                Color.rgb(185, 85, 63),
                Color.rgb(59, 108, 78),
                Color.rgb(104, 139, 164),
                Color.rgb(229, 221, 193)
            }
            : new int[]{
                Color.rgb(207, 159, 43),
                Color.rgb(158, 66, 52),
                Color.rgb(38, 99, 67),
                Color.rgb(111, 145, 169),
                Color.rgb(63, 58, 48)
            };

        int bigCount = dark ? 34 : 74;
        int tinyCount = dark ? 68 : 170;

        for (int i = 0; i < bigCount; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float r = Math.max(2.2f, Math.min(width, height) * (0.003f + random.nextFloat() * 0.0045f));
            p.setColor(colors[random.nextInt(colors.length)]);
            p.setAlpha(dark ? 235 : 220);
            canvas.drawCircle(x, y, r, p);

            if (random.nextBoolean()) {
                p.setAlpha(dark ? 160 : 130);
                canvas.drawCircle(x + r * 0.75f, y - r * 0.4f, r * 0.25f, p);
                canvas.drawCircle(x - r * 0.6f, y + r * 0.65f, r * 0.18f, p);
            }
        }

        for (int i = 0; i < tinyCount; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float r = Math.max(0.8f, Math.min(width, height) * (0.0007f + random.nextFloat() * 0.0012f));
            p.setColor(colors[random.nextInt(colors.length)]);
            p.setAlpha(dark ? 145 : 120);
            canvas.drawCircle(x, y, r, p);
        }
    }

    private static void drawSoftPaper(Canvas canvas, int width, int height, long seed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random(seed ^ 0x51A7L);

        // Keep Home quiet: warm peach paper with faint blush/eucalyptus fibres,
        // not a floral wallpaper. The actual flowers only appear in Doom Scroll.
        int[] flecks = {
            Color.rgb(213, 137, 151),
            Color.rgb(225, 158, 130),
            Color.rgb(157, 171, 143),
            Color.rgb(185, 146, 176),
            Color.rgb(232, 181, 151)
        };

        int count = Math.max(70, (width * height) / Math.max(1, width * 620));
        for (int i = 0; i < count; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float r = Math.max(0.7f, Math.min(width, height) * (0.00055f + random.nextFloat() * 0.0010f));
            p.setColor(flecks[random.nextInt(flecks.length)]);
            p.setAlpha(18 + random.nextInt(24));
            canvas.drawOval(new RectF(x - r * 1.9f, y - r * 0.72f, x + r * 1.9f, y + r * 0.72f), p);
        }

        // Sparse translucent peach washes give the paper more warmth without
        // adding visible decorative objects.
        for (int i = 0; i < 9; i++) {
            float cx = random.nextFloat() * width;
            float cy = random.nextFloat() * height;
            float rw = width * (0.08f + random.nextFloat() * 0.13f);
            float rh = rw * (0.45f + random.nextFloat() * 0.45f);
            p.setColor(random.nextBoolean() ? Color.rgb(239, 159, 150) : Color.rgb(244, 177, 145));
            p.setAlpha(7 + random.nextInt(7));
            canvas.drawOval(new RectF(cx - rw, cy - rh, cx + rw, cy + rh), p);
        }
    }

    private static void drawSoftBotanicalEdges(Canvas canvas, int width, int height, long seed) {
        // Deliberately composed rather than patterned. The centre remains almost
        // empty; recognisable Australian natives grow in from the screen edges.
        float unit = Math.max(42f, width * 0.105f);

        drawNativeCluster(canvas, width * 0.035f, height * 0.145f, unit * 1.08f, 0.30f, 3, -12f, 0.92f);
        drawNativeCluster(canvas, width * 0.965f, height * 0.205f, unit * 1.02f, 0.28f, 1, 16f, 0.90f);
        drawNativeCluster(canvas, width * 0.025f, height * 0.395f, unit * 0.88f, 0.22f, 0, -8f, 0.82f);
        drawNativeCluster(canvas, width * 0.975f, height * 0.525f, unit * 1.12f, 0.30f, 4, 12f, 0.90f);
        drawNativeCluster(canvas, width * 0.035f, height * 0.705f, unit * 0.92f, 0.24f, 1, -18f, 0.84f);
        drawNativeCluster(canvas, width * 0.965f, height * 0.825f, unit * 0.92f, 0.25f, 2, 14f, 0.82f);
    }

    public static void drawNativeCluster(
        Canvas canvas,
        float x,
        float y,
        float size,
        float progress,
        int type,
        float rotation,
        float opacity
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = Math.max(18f, size);
        float depth = Math.max(0f, Math.min(1f, progress));
        int alpha = Math.max(40, Math.min(255, Math.round(255f * opacity)));

        canvas.save();
        canvas.rotate(rotation, x, y);

        int stem = mix(Color.rgb(126, 93, 72), Color.rgb(66, 59, 52), depth * 0.65f);
        int leafDark = mix(Color.rgb(93, 119, 86), Color.rgb(39, 61, 49), depth * 0.78f);
        int leafLight = mix(Color.rgb(139, 151, 113), Color.rgb(72, 91, 70), depth * 0.68f);

        // Soft painted shadow under the cluster gives the flat Canvas shapes depth.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(Math.round(alpha * 0.13f), 47, 38, 39));
        canvas.drawOval(new RectF(x - s * 0.88f, y - s * 0.58f, x + s * 0.88f, y + s * 0.80f), p);

        // Main woody stem.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(Math.max(1.5f, s * 0.045f));
        p.setColor(Color.argb(alpha, Color.red(stem), Color.green(stem), Color.blue(stem)));
        Path branch = new Path();
        branch.moveTo(x - s * 0.76f, y + s * 0.62f);
        branch.cubicTo(
            x - s * 0.42f, y + s * 0.38f,
            x - s * 0.18f, y + s * 0.08f,
            x + s * 0.34f, y - s * 0.36f
        );
        canvas.drawPath(branch, p);

        // Secondary twigs make the foliage read like a botanical drawing.
        p.setStrokeWidth(Math.max(1f, s * 0.024f));
        for (int i = 0; i < 4; i++) {
            float t = 0.20f + i * 0.19f;
            float bx = x - s * 0.67f + s * 0.94f * t;
            float by = y + s * 0.54f - s * 0.82f * t;
            float side = i % 2 == 0 ? -1f : 1f;
            canvas.drawLine(bx, by, bx + side * s * 0.34f, by - s * (0.16f + i * 0.025f), p);
        }

        // Long eucalyptus leaves with two-tone fill and a fine central vein.
        for (int i = 0; i < 9; i++) {
            float t = 0.10f + i * 0.085f;
            float cx = x - s * 0.68f + s * 1.03f * t;
            float cy = y + s * 0.58f - s * 0.94f * t;
            float side = i % 2 == 0 ? -1f : 1f;
            cx += side * s * (0.20f + (i % 3) * 0.035f);
            cy -= s * 0.10f;
            float angle = side * (38f + (i % 3) * 8f) - 18f;

            canvas.save();
            canvas.rotate(angle, cx, cy);
            Path leaf = lanceolate(cx, cy, s * (0.30f + (i % 2) * 0.035f), s * 0.095f);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(
                cx - s * 0.28f, cy, cx + s * 0.28f, cy,
                Color.argb(alpha, Color.red(leafDark), Color.green(leafDark), Color.blue(leafDark)),
                Color.argb(alpha, Color.red(leafLight), Color.green(leafLight), Color.blue(leafLight)),
                Shader.TileMode.CLAMP
            ));
            canvas.drawPath(leaf, p);
            p.setShader(null);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.7f, s * 0.010f));
            p.setColor(Color.argb(Math.round(alpha * 0.45f), 224, 215, 183));
            canvas.drawLine(cx - s * 0.22f, cy, cx + s * 0.22f, cy, p);
            canvas.restore();
        }

        float fx = x + s * 0.30f;
        float fy = y - s * 0.39f;

        switch (Math.floorMod(type, 6)) {
            case 0:
                drawWattle(canvas, p, fx, fy, s, alpha, depth);
                break;
            case 1:
                drawGumBlossom(canvas, p, fx, fy, s, alpha, depth);
                break;
            case 2:
                drawFlannelFlower(canvas, p, fx, fy, s, alpha, depth);
                break;
            case 3:
                drawWaratah(canvas, p, fx, fy, s, alpha, depth);
                break;
            case 4:
                drawBanksia(canvas, p, fx, fy, s, alpha, depth);
                break;
            default:
                drawGumBuds(canvas, p, fx, fy, s, alpha, depth);
                break;
        }

        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private static Path lanceolate(float x, float y, float halfLength, float halfWidth) {
        Path path = new Path();
        path.moveTo(x - halfLength, y);
        path.cubicTo(
            x - halfLength * 0.42f, y - halfWidth,
            x + halfLength * 0.42f, y - halfWidth,
            x + halfLength, y
        );
        path.cubicTo(
            x + halfLength * 0.42f, y + halfWidth,
            x - halfLength * 0.42f, y + halfWidth,
            x - halfLength, y
        );
        path.close();
        return path;
    }

    private static void drawWattle(Canvas c, Paint p, float x, float y, float s, int alpha, float depth) {
        int gold = mix(Color.rgb(238, 194, 71), Color.rgb(187, 137, 49), depth * 0.52f);
        int light = mix(Color.rgb(255, 226, 126), Color.rgb(220, 179, 91), depth * 0.42f);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.022f));
        p.setColor(Color.argb(alpha, 93, 113, 74));
        Path spray = new Path();
        spray.moveTo(x - s * 0.48f, y + s * 0.38f);
        spray.cubicTo(x - s * 0.20f, y + s * 0.10f, x + s * 0.12f, y - s * 0.10f, x + s * 0.44f, y - s * 0.50f);
        c.drawPath(spray, p);

        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 13; i++) {
            float t = i / 12f;
            float bx = x - s * 0.34f + t * s * 0.68f;
            float by = y + s * 0.25f - t * s * 0.68f;
            float side = (i % 2 == 0 ? -1f : 1f) * s * (0.11f + (i % 3) * 0.018f);
            float cx = bx + side;
            float cy = by;
            float r = s * (0.085f + (i % 3) * 0.007f);

            p.setShader(new RadialGradient(
                cx - r * 0.28f, cy - r * 0.30f, r * 1.15f,
                new int[]{
                    Color.argb(alpha, Color.red(light), Color.green(light), Color.blue(light)),
                    Color.argb(alpha, Color.red(gold), Color.green(gold), Color.blue(gold)),
                    Color.argb(Math.round(alpha * 0.72f), 141, 99, 36)
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
            ));
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);

            p.setColor(Color.argb(Math.round(alpha * 0.65f), 255, 235, 152));
            for (int d = 0; d < 5; d++) {
                double a = d * Math.PI * 0.4;
                c.drawCircle(cx + (float)Math.cos(a) * r * 0.52f, cy + (float)Math.sin(a) * r * 0.52f, r * 0.075f, p);
            }
        }
    }

    private static void drawGumBlossom(Canvas c, Paint p, float x, float y, float s, int alpha, float depth) {
        int pinkA = mix(Color.rgb(236, 143, 164), Color.rgb(164, 86, 112), depth * 0.55f);
        int pinkB = mix(Color.rgb(250, 184, 192), Color.rgb(202, 118, 139), depth * 0.45f);

        // Woody cup and unopened buds.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(alpha, 157, 110, 76));
        c.drawOval(new RectF(x - s * 0.18f, y + s * 0.06f, x + s * 0.18f, y + s * 0.30f), p);
        p.setColor(Color.argb(alpha, 112, 128, 82));
        for (int i = 0; i < 3; i++) {
            float bx = x + (i - 1) * s * 0.27f;
            c.drawOval(new RectF(bx - s * 0.09f, y - s * 0.34f, bx + s * 0.09f, y - s * 0.12f), p);
        }

        // Dozens of irregular stamens create the soft brush-like bloom.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 38; i++) {
            double a = Math.PI * 2d * i / 38d + (i % 4) * 0.055d;
            float len = s * (0.43f + (i % 5) * 0.035f);
            float ex = x + (float)Math.cos(a) * len;
            float ey = y - s * 0.08f + (float)Math.sin(a) * len * 0.82f;
            int col = i % 2 == 0 ? pinkA : pinkB;
            p.setColor(Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col)));
            p.setStrokeWidth(Math.max(0.9f, s * (0.012f + (i % 3) * 0.002f)));
            c.drawLine(x, y - s * 0.05f, ex, ey, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(alpha, 245, 202, 118));
            c.drawCircle(ex, ey, Math.max(1f, s * 0.022f), p);
            p.setStyle(Paint.Style.STROKE);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(alpha, Color.red(pinkB), Color.green(pinkB), Color.blue(pinkB)));
        c.drawCircle(x, y - s * 0.05f, s * 0.13f, p);
    }

    private static void drawFlannelFlower(Canvas c, Paint p, float x, float y, float s, int alpha, float depth) {
        int petal = mix(Color.rgb(247, 241, 226), Color.rgb(209, 204, 188), depth * 0.42f);
        int edge = mix(Color.rgb(187, 194, 166), Color.rgb(130, 144, 117), depth * 0.55f);

        for (int i = 0; i < 9; i++) {
            c.save();
            c.rotate(i * 40f + (i % 2) * 3f, x, y);
            Path petalPath = new Path();
            petalPath.moveTo(x, y - s * 0.07f);
            petalPath.cubicTo(x - s * 0.16f, y - s * 0.25f, x - s * 0.12f, y - s * 0.66f, x, y - s * 0.78f);
            petalPath.cubicTo(x + s * 0.12f, y - s * 0.66f, x + s * 0.16f, y - s * 0.25f, x, y - s * 0.07f);
            petalPath.close();

            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(
                x, y - s * 0.76f, x, y,
                Color.argb(alpha, 255, 250, 239),
                Color.argb(alpha, Color.red(petal), Color.green(petal), Color.blue(petal)),
                Shader.TileMode.CLAMP
            ));
            c.drawPath(petalPath, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(0.7f, s * 0.012f));
            p.setColor(Color.argb(Math.round(alpha * 0.55f), Color.red(edge), Color.green(edge), Color.blue(edge)));
            c.drawLine(x, y - s * 0.12f, x, y - s * 0.65f, p);
            c.restore();
        }

        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(
            x, y, s * 0.24f,
            Color.argb(alpha, 226, 199, 103),
            Color.argb(alpha, 99, 122, 81),
            Shader.TileMode.CLAMP
        ));
        c.drawCircle(x, y, s * 0.23f, p);
        p.setShader(null);
    }

    private static void drawWaratah(Canvas c, Paint p, float x, float y, float s, int alpha, float depth) {
        int deep = mix(Color.rgb(155, 48, 65), Color.rgb(92, 38, 49), depth * 0.62f);
        int mid = mix(Color.rgb(202, 74, 88), Color.rgb(133, 55, 70), depth * 0.55f);
        int light = mix(Color.rgb(235, 116, 118), Color.rgb(179, 85, 96), depth * 0.48f);

        // Dark green bracts around the flower.
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 10; i++) {
            c.save();
            c.rotate(i * 36f, x, y + s * 0.12f);
            p.setColor(Color.argb(alpha, 70, 99, 69));
            c.drawOval(new RectF(x - s * 0.10f, y + s * 0.08f, x + s * 0.10f, y + s * 0.72f), p);
            c.restore();
        }

        int[] cols = {deep, mid, light};
        for (int ring = 0; ring < 4; ring++) {
            int petals = 16 - ring * 3;
            float radius = s * (0.43f - ring * 0.085f);
            int col = cols[Math.min(2, ring)];
            for (int i = 0; i < petals; i++) {
                double a = Math.PI * 2d * i / petals + ring * 0.17d;
                float cx = x + (float)Math.cos(a) * radius;
                float cy = y - s * 0.06f + (float)Math.sin(a) * radius * 0.76f;
                c.save();
                c.rotate((float)Math.toDegrees(a) + 90f, cx, cy);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(
                    cx, cy - s * 0.23f, cx, cy + s * 0.23f,
                    Color.argb(alpha, Color.red(light), Color.green(light), Color.blue(light)),
                    Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col)),
                    Shader.TileMode.CLAMP
                ));
                c.drawOval(new RectF(cx - s * 0.095f, cy - s * 0.25f, cx + s * 0.095f, cy + s * 0.25f), p);
                p.setShader(null);
                c.restore();
            }
        }
        p.setColor(Color.argb(alpha, Color.red(light), Color.green(light), Color.blue(light)));
        c.drawCircle(x, y - s * 0.06f, s * 0.15f, p);
    }

    private static void drawBanksia(Canvas c, Paint p, float x, float y, float s, int alpha, float depth) {
        int ochre = mix(Color.rgb(197, 127, 76), Color.rgb(116, 79, 54), depth * 0.65f);
        int cream = mix(Color.rgb(240, 179, 111), Color.rgb(179, 126, 83), depth * 0.55f);

        // Two serrated leaves behind the cone.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(alpha, 69, 99, 67));
        for (int side : new int[]{-1, 1}) {
            c.save();
            c.rotate(side * 28f, x, y + s * 0.15f);
            Path leaf = lanceolate(x + side * s * 0.16f, y + s * 0.24f, s * 0.62f, s * 0.14f);
            c.drawPath(leaf, p);
            c.restore();
        }

        RectF cone = new RectF(x - s * 0.31f, y - s * 0.72f, x + s * 0.31f, y + s * 0.58f);
        p.setShader(new LinearGradient(
            cone.left, cone.top, cone.right, cone.bottom,
            Color.argb(alpha, Color.red(cream), Color.green(cream), Color.blue(cream)),
            Color.argb(alpha, Color.red(ochre), Color.green(ochre), Color.blue(ochre)),
            Shader.TileMode.CLAMP
        ));
        c.drawRoundRect(cone, s * 0.28f, s * 0.28f, p);
        p.setShader(null);

        // Repeating follicles give the cone a recognisable banksia texture.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.8f, s * 0.012f));
        p.setColor(Color.argb(Math.round(alpha * 0.72f), 104, 68, 48));
        for (int row = 0; row < 10; row++) {
            float yy = y - s * 0.57f + row * s * 0.115f;
            for (int col = -1; col <= 1; col++) {
                float xx = x + col * s * 0.16f + (row % 2 == 0 ? s * 0.075f : 0f);
                c.drawOval(new RectF(xx - s * 0.055f, yy - s * 0.035f, xx + s * 0.055f, yy + s * 0.035f), p);
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void drawGumBuds(Canvas c, Paint p, float x, float y, float s, int alpha, float depth) {
        int bud = mix(Color.rgb(176, 112, 111), Color.rgb(107, 72, 76), depth * 0.58f);
        int cap = mix(Color.rgb(224, 155, 151), Color.rgb(149, 91, 100), depth * 0.52f);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.022f));
        p.setColor(Color.argb(alpha, 88, 108, 73));
        for (int i = 0; i < 5; i++) {
            float angle = -0.65f + i * 0.32f;
            float ex = x + (float)Math.sin(angle) * s * 0.58f;
            float ey = y - (float)Math.cos(angle) * s * 0.58f;
            c.drawLine(x, y + s * 0.25f, ex, ey, p);

            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(
                ex, ey - s * 0.16f, ex, ey + s * 0.16f,
                Color.argb(alpha, Color.red(cap), Color.green(cap), Color.blue(cap)),
                Color.argb(alpha, Color.red(bud), Color.green(bud), Color.blue(bud)),
                Shader.TileMode.CLAMP
            ));
            c.drawOval(new RectF(ex - s * 0.10f, ey - s * 0.14f, ex + s * 0.10f, ey + s * 0.15f), p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static int mix(int a, int b, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
            Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
            Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t)
        );
    }

    private static void drawMothPaper(Canvas canvas, int width, int height, long seed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random(seed ^ 0xA117L);
        int[] flecks = {
            Color.rgb(220, 199, 166),
            Color.rgb(117, 102, 119),
            Color.rgb(119, 128, 104),
            Color.rgb(153, 112, 92)
        };

        int count = Math.max(50, (width * height) / Math.max(1, width * 760));
        for (int i = 0; i < count; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float r = Math.max(0.7f, Math.min(width, height) * (0.0006f + random.nextFloat() * 0.0010f));
            p.setColor(flecks[random.nextInt(flecks.length)]);
            p.setAlpha(18 + random.nextInt(24));
            canvas.drawCircle(x, y, r, p);
        }
    }

    private static void drawPixels(Canvas canvas, int width, int height, long seed) {
        Paint p = new Paint();
        Random random = new Random(seed);
        int[] colors = {
            Color.rgb(244, 207, 72),
            Color.rgb(218, 76, 67),
            Color.rgb(88, 139, 99),
            Color.rgb(101, 150, 208),
            Color.rgb(229, 218, 181)
        };

        int size = Math.max(4, width / 95);
        int count = Math.max(70, (width * height) / Math.max(1, size * size * 190));

        for (int i = 0; i < count; i++) {
            float x = random.nextInt(Math.max(1, width / size)) * size;
            float y = random.nextInt(Math.max(1, height / size)) * size;
            p.setColor(colors[random.nextInt(colors.length)]);
            p.setAlpha(220);
            if (random.nextFloat() < 0.28f) {
                canvas.drawRect(new RectF(x, y + size, x + size * 3, y + size * 2), p);
                canvas.drawRect(new RectF(x + size, y, x + size * 2, y + size * 3), p);
            } else {
                canvas.drawRect(new RectF(x, y, x + size, y + size), p);
            }
        }
    }
}
