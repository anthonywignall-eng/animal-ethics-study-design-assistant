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
            case "soft_launch": return Color.rgb(247, 239, 235);
            case "moth": return Color.rgb(25, 23, 31);
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
                return Color.rgb(58, 43, 49);
            case "moth":
                return Color.rgb(241, 232, 216);
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
                return Color.rgb(133, 106, 116);
            case "moth":
                return Color.rgb(176, 164, 150);
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
            case "soft_launch": return Color.rgb(237, 219, 221);
            case "moth": return Color.rgb(39, 35, 47);
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
            case "soft_launch": return Color.rgb(218, 194, 200);
            case "moth": return Color.rgb(75, 65, 78);
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
            drawSoftBotanical(canvas, width, height, seed);
        } else if ("moth".equals(theme)) {
            drawMothPaper(canvas, width, height, seed);
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

    private static void drawSoftBotanical(Canvas canvas, int width, int height, long seed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random(seed ^ 0x51A7L);
        int count = Math.max(12, (width * height) / Math.max(1, width * width / 2));

        int[] petalColors = {
            Color.rgb(196, 119, 125),
            Color.rgb(217, 159, 165),
            Color.rgb(225, 185, 121),
            Color.rgb(168, 136, 164),
            Color.rgb(126, 146, 116)
        };

        for (int i = 0; i < count; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float r = Math.max(2f, Math.min(width, height) * (0.003f + random.nextFloat() * 0.0025f));

            p.setColor(petalColors[random.nextInt(petalColors.length)]);
            p.setAlpha(38 + random.nextInt(28));
            int petals = 4 + random.nextInt(3);
            for (int j = 0; j < petals; j++) {
                double a = Math.PI * 2.0 * j / petals;
                float px = x + (float)Math.cos(a) * r * 1.45f;
                float py = y + (float)Math.sin(a) * r * 1.45f;
                canvas.save();
                canvas.rotate((float)(a * 180.0 / Math.PI), px, py);
                canvas.drawOval(new RectF(px - r * 0.55f, py - r, px + r * 0.55f, py + r), p);
                canvas.restore();
            }
            p.setAlpha(45);
            p.setColor(Color.rgb(132, 117, 86));
            canvas.drawCircle(x, y, r * 0.42f, p);
        }
    }

    private static void drawMothPaper(Canvas canvas, int width, int height, long seed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random(seed ^ 0x4D4F5448L);

        int specks = Math.max(26, (width * height) / 26000);
        int[] tones = {
            Color.rgb(86, 75, 91),
            Color.rgb(108, 91, 92),
            Color.rgb(83, 91, 75),
            Color.rgb(131, 116, 101)
        };
        for (int i = 0; i < specks; i++) {
            p.setColor(tones[random.nextInt(tones.length)]);
            p.setAlpha(18 + random.nextInt(20));
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float r = 0.7f + random.nextFloat() * 1.8f;
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
