package com.hidden.launcher;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
            case "8bit": return Color.rgb(10, 14, 28);
            case "soft_launch": return Color.rgb(247, 239, 229);
            case "moth": return Color.rgb(18, 20, 29);
            default: return Color.rgb(241, 239, 232);
        }
    }

    public static int foreground(String rawTheme, Resources res) {
        String theme = resolve(rawTheme, res);
        switch (theme) {
            case "light":
            case "doom_light":
                return Color.rgb(26, 25, 22);
            case "soft_launch":
                return Color.rgb(70, 47, 48);
            case "8bit":
                return Color.rgb(244, 232, 180);
            case "doom_dark":
                return Color.rgb(235, 218, 195);
            case "moth":
                return Color.rgb(236, 226, 209);
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
            case "soft_launch":
                return Color.rgb(133, 103, 101);
            case "8bit":
                return Color.rgb(154, 166, 124);
            case "doom_dark":
                return Color.rgb(155, 145, 128);
            case "moth":
                return Color.rgb(161, 151, 139);
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
            case "8bit": return Color.rgb(20, 27, 45);
            case "soft_launch": return Color.rgb(236, 216, 210);
            case "moth": return Color.rgb(32, 34, 45);
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
            case "8bit": return Color.rgb(83, 96, 67);
            case "soft_launch": return Color.rgb(210, 184, 177);
            case "moth": return Color.rgb(67, 66, 76);
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
        } else if ("8bit".equals(theme)) {
            drawPixels(canvas, width, height, seed);
        } else if ("soft_launch".equals(theme)) {
            drawSoftWallpaper(canvas, width, height, seed);
        } else if ("moth".equals(theme)) {
            drawMothWallpaper(canvas, width, height, seed);
        }
    }

    private static void drawSoftWallpaper(Canvas canvas, int width, int height, long seed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(seed);
        int min = Math.max(1, Math.min(width, height));

        // Quiet paper grain: visible enough to stop the surface feeling flat, never decorative text.
        for (int i = 0; i < 95; i++) {
            p.setColor(i % 2 == 0 ? Color.rgb(205, 165, 157) : Color.rgb(113, 137, 108));
            p.setAlpha(12 + r.nextInt(15));
            float x = r.nextFloat() * width;
            float y = r.nextFloat() * height;
            float rr = Math.max(0.6f, min * (0.0005f + r.nextFloat() * 0.0007f));
            canvas.drawCircle(x, y, rr, p);
        }

        // A handful of tiny, low-opacity botanical marks near edges only.
        int count = Math.max(5, height / Math.max(1, width * 2));
        for (int i = 0; i < count; i++) {
            float x = r.nextBoolean()
                ? width * (0.025f + r.nextFloat() * 0.08f)
                : width * (0.895f + r.nextFloat() * 0.08f);
            float y = height * (0.08f + r.nextFloat() * 0.84f);
            float size = Math.max(4f, min * (0.010f + r.nextFloat() * 0.006f));
            drawNativeFlower(canvas, x, y, size, i % 5, -18f + r.nextFloat() * 36f, 0.18f);
        }
    }

    private static void drawMothWallpaper(Canvas canvas, int width, int height, long seed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(seed);
        int min = Math.max(1, Math.min(width, height));

        for (int i = 0; i < 85; i++) {
            p.setColor(i % 3 == 0 ? Color.rgb(155, 125, 139) : Color.rgb(132, 137, 106));
            p.setAlpha(10 + r.nextInt(14));
            float x = r.nextFloat() * width;
            float y = r.nextFloat() * height;
            float rr = Math.max(0.6f, min * (0.0005f + r.nextFloat() * 0.0008f));
            canvas.drawCircle(x, y, rr, p);
        }

        int count = Math.max(4, height / Math.max(1, width * 3));
        for (int i = 0; i < count; i++) {
            float x = r.nextBoolean()
                ? width * (0.03f + r.nextFloat() * 0.07f)
                : width * (0.90f + r.nextFloat() * 0.06f);
            float y = height * (0.10f + r.nextFloat() * 0.80f);
            float size = Math.max(5f, min * (0.012f + r.nextFloat() * 0.006f));
            drawCeramicMoth(canvas, x, y, size, i % 4, -25f + r.nextFloat() * 50f, 0.16f);
        }
    }

    public static void drawNativeFlower(
        Canvas canvas,
        float cx,
        float cy,
        float size,
        int type,
        float rotation,
        float alpha
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);

        int a = Math.max(0, Math.min(255, Math.round(255f * alpha)));
        int stem = Color.argb(a, 88, 111, 83);
        int blush = Color.argb(a, 198, 111, 112);
        int rose = Color.argb(a, 173, 78, 82);
        int yellow = Color.argb(a, 218, 171, 72);
        int cream = Color.argb(a, 240, 232, 214);
        int mauve = Color.argb(a, 147, 111, 139);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, size * 0.10f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(stem);
        canvas.drawLine(0, size * 0.7f, 0, -size * 0.12f, p);

        p.setStyle(Paint.Style.FILL);

        switch (Math.floorMod(type, 5)) {
            case 0: // gum blossom
                p.setColor(blush);
                for (int i = 0; i < 10; i++) {
                    float angle = (float)(Math.PI * 2 * i / 10.0);
                    float x = (float)Math.cos(angle) * size * 0.42f;
                    float y = (float)Math.sin(angle) * size * 0.42f - size * 0.15f;
                    canvas.drawCircle(x, y, size * 0.10f, p);
                }
                p.setColor(cream);
                canvas.drawCircle(0, -size * 0.15f, size * 0.13f, p);
                break;

            case 1: // wattle
                p.setColor(yellow);
                for (int i = 0; i < 7; i++) {
                    float x = ((i % 3) - 1) * size * 0.23f;
                    float y = -size * 0.18f - (i / 3) * size * 0.24f;
                    canvas.drawCircle(x, y, size * 0.12f, p);
                }
                break;

            case 2: // flannel flower
                p.setColor(cream);
                for (int i = 0; i < 6; i++) {
                    canvas.save();
                    canvas.rotate(i * 60f);
                    RectF petal = new RectF(-size * 0.11f, -size * 0.58f, size * 0.11f, -size * 0.12f);
                    canvas.drawOval(petal, p);
                    canvas.restore();
                }
                p.setColor(Color.argb(a, 135, 129, 95));
                canvas.drawCircle(0, 0, size * 0.11f, p);
                break;

            case 3: // billy button
                p.setColor(yellow);
                canvas.drawCircle(0, -size * 0.28f, size * 0.31f, p);
                p.setColor(Color.argb(Math.max(1, a / 2), 251, 226, 151));
                for (int i = 0; i < 7; i++) {
                    float angle = (float)(Math.PI * 2 * i / 7.0);
                    canvas.drawCircle(
                        (float)Math.cos(angle) * size * 0.17f,
                        -size * 0.28f + (float)Math.sin(angle) * size * 0.17f,
                        size * 0.045f,
                        p
                    );
                }
                break;

            default: // tiny banksia
                p.setColor(mauve);
                RectF head = new RectF(-size * 0.23f, -size * 0.65f, size * 0.23f, size * 0.12f);
                canvas.drawOval(head, p);
                p.setColor(rose);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(0.8f, size * 0.055f));
                for (int i = -2; i <= 2; i++) {
                    float xx = i * size * 0.075f;
                    canvas.drawLine(xx, -size * 0.55f, xx, size * 0.02f, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
        }

        // A pair of eucalyptus-like leaves.
        p.setColor(stem);
        canvas.save();
        canvas.rotate(-28f);
        canvas.drawOval(new RectF(-size * 0.13f, size * 0.18f, size * 0.05f, size * 0.55f), p);
        canvas.restore();
        canvas.save();
        canvas.rotate(32f);
        canvas.drawOval(new RectF(-size * 0.04f, size * 0.25f, size * 0.14f, size * 0.61f), p);
        canvas.restore();

        canvas.restore();
    }

    public static void drawCeramicMoth(
        Canvas canvas,
        float cx,
        float cy,
        float size,
        int type,
        float rotation,
        float alpha
    ) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);

        int a = Math.max(0, Math.min(255, Math.round(255f * alpha)));
        int[] bases = {
            Color.argb(a, 191, 147, 157),
            Color.argb(a, 205, 185, 146),
            Color.argb(a, 139, 151, 111),
            Color.argb(a, 150, 142, 170)
        };
        int base = bases[Math.floorMod(type, bases.length)];

        // Small offset shadow makes them read like raised ceramic magnets.
        p.setColor(Color.argb(Math.max(1, a / 3), 0, 0, 0));
        canvas.drawOval(new RectF(-size * 0.92f, -size * 0.38f + size * 0.13f, -size * 0.05f, size * 0.56f + size * 0.13f), p);
        canvas.drawOval(new RectF(size * 0.05f, -size * 0.38f + size * 0.13f, size * 0.92f, size * 0.56f + size * 0.13f), p);

        p.setColor(base);
        canvas.drawOval(new RectF(-size * 0.92f, -size * 0.42f, -size * 0.05f, size * 0.50f), p);
        canvas.drawOval(new RectF(size * 0.05f, -size * 0.42f, size * 0.92f, size * 0.50f), p);

        int inner = Color.argb(a, 105, 87, 83);
        p.setColor(inner);
        canvas.drawOval(new RectF(-size * 0.13f, -size * 0.55f, size * 0.13f, size * 0.67f), p);
        canvas.drawCircle(0, -size * 0.56f, size * 0.13f, p);

        // Wing markings.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(0.8f, size * 0.085f));
        p.setColor(Color.argb(Math.max(1, (int)(a * 0.58f)), 241, 227, 199));
        canvas.drawArc(new RectF(-size * 0.76f, -size * 0.28f, -size * 0.17f, size * 0.32f), 200f, 150f, false, p);
        canvas.drawArc(new RectF(size * 0.17f, -size * 0.28f, size * 0.76f, size * 0.32f), 190f, 150f, false, p);

        // Glaze highlight.
        p.setStrokeWidth(Math.max(0.7f, size * 0.06f));
        p.setColor(Color.argb(Math.max(1, (int)(a * 0.70f)), 255, 250, 238));
        canvas.drawArc(new RectF(-size * 0.74f, -size * 0.31f, -size * 0.19f, size * 0.28f), 205f, 65f, false, p);
        canvas.drawArc(new RectF(size * 0.19f, -size * 0.31f, size * 0.74f, size * 0.28f), 270f, 65f, false, p);

        // Antennae.
        p.setColor(Color.argb(a, 115, 101, 92));
        p.setStrokeWidth(Math.max(0.7f, size * 0.05f));
        canvas.drawLine(-size * 0.04f, -size * 0.63f, -size * 0.35f, -size * 0.94f, p);
        canvas.drawLine(size * 0.04f, -size * 0.63f, size * 0.35f, -size * 0.94f, p);

        canvas.restore();
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
