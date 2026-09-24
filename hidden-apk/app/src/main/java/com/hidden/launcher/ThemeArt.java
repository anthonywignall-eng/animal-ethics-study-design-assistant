package com.hidden.launcher;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
            case "soft_launch": return Color.rgb(247, 238, 235);
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
                return Color.rgb(59, 43, 52);
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
                return Color.rgb(126, 101, 113);
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
            case "soft_launch": return Color.rgb(235, 217, 218);
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
            case "soft_launch": return Color.rgb(216, 194, 199);
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
        int[] flecks = {
            Color.rgb(196, 151, 160),
            Color.rgb(181, 166, 142),
            Color.rgb(151, 164, 139),
            Color.rgb(177, 150, 179)
        };

        int count = Math.max(55, (width * height) / Math.max(1, width * 720));
        for (int i = 0; i < count; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height;
            float r = Math.max(0.7f, Math.min(width, height) * (0.0006f + random.nextFloat() * 0.0012f));
            p.setColor(flecks[random.nextInt(flecks.length)]);
            p.setAlpha(24 + random.nextInt(28));
            canvas.drawOval(new RectF(x - r * 1.6f, y - r, x + r * 1.6f, y + r), p);
        }
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
