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
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random(seed ^ 0xB07A11L);

        int sprays = Math.max(7, height / Math.max(1, width / 2));
        for (int i = 0; i < sprays; i++) {
            boolean left = i % 2 == 0;
            float y = height * (0.10f + (i + 0.5f) / sprays * 0.78f) + (random.nextFloat() - 0.5f) * height * 0.06f;
            float reach = width * (0.13f + random.nextFloat() * 0.10f);
            float baseX = left ? -width * 0.015f : width * 1.015f;
            float tipX = left ? reach : width - reach;
            int green = random.nextBoolean() ? Color.rgb(101, 128, 91) : Color.rgb(125, 145, 105);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(2f, width * 0.0045f));
            p.setColor(Color.argb(150, Color.red(green), Color.green(green), Color.blue(green)));

            Path stem = new Path();
            stem.moveTo(baseX, y + height * 0.035f);
            stem.cubicTo(
                left ? width * 0.03f : width * 0.97f, y + height * 0.015f,
                left ? width * 0.08f : width * 0.92f, y - height * 0.025f,
                tipX, y
            );
            canvas.drawPath(stem, p);

            p.setStyle(Paint.Style.FILL);
            for (int leaf = 0; leaf < 5; leaf++) {
                float t = 0.18f + leaf * 0.16f;
                float cx = baseX + (tipX - baseX) * t;
                float cy = y + height * (0.025f - t * 0.035f) + (leaf % 2 == 0 ? -1 : 1) * height * 0.008f;
                float lw = width * (0.032f + random.nextFloat() * 0.012f);
                float lh = width * 0.010f;
                p.setColor(Color.argb(120 + random.nextInt(40), Color.red(green), Color.green(green), Color.blue(green)));
                canvas.save();
                canvas.rotate((left ? -18f : 18f) + (leaf % 2 == 0 ? -24f : 24f), cx, cy);
                canvas.drawOval(new RectF(cx - lw, cy - lh, cx + lw, cy + lh), p);
                canvas.restore();
            }

            int flowerType = i % 4;
            float fx = left ? width * (0.07f + random.nextFloat() * 0.08f) : width * (0.93f - random.nextFloat() * 0.08f);
            float fs = width * (0.022f + random.nextFloat() * 0.010f);

            if (flowerType == 0) {
                p.setColor(Color.argb(190, 229, 183, 66));
                for (int n = 0; n < 6; n++) {
                    float ox = (n % 2 == 0 ? -1f : 1f) * fs * 0.42f;
                    float oy = (n - 2.5f) * fs * 0.43f;
                    canvas.drawCircle(fx + ox, y + oy, fs * 0.23f, p);
                }
            } else if (flowerType == 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, fs * 0.10f));
                p.setColor(Color.argb(175, 213, 111, 143));
                for (int n = 0; n < 14; n++) {
                    double a = Math.PI * 2d * n / 14d;
                    canvas.drawLine(fx, y, fx + (float)Math.cos(a) * fs, y + (float)Math.sin(a) * fs, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(190, 239, 181, 186));
                canvas.drawCircle(fx, y, fs * 0.26f, p);
            } else if (flowerType == 2) {
                p.setColor(Color.argb(185, 184, 67, 83));
                for (int n = 0; n < 8; n++) {
                    double a = Math.PI * 2d * n / 8d;
                    float cx = fx + (float)Math.cos(a) * fs * 0.42f;
                    float cy = y + (float)Math.sin(a) * fs * 0.34f;
                    canvas.drawOval(new RectF(cx - fs * 0.18f, cy - fs * 0.31f, cx + fs * 0.18f, cy + fs * 0.31f), p);
                }
                p.setColor(Color.argb(195, 225, 129, 127));
                canvas.drawCircle(fx, y, fs * 0.27f, p);
            } else {
                p.setColor(Color.argb(175, 191, 132, 78));
                RectF cone = new RectF(fx - fs * 0.32f, y - fs * 0.95f, fx + fs * 0.32f, y + fs * 0.65f);
                canvas.drawRoundRect(cone, fs * 0.24f, fs * 0.24f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, fs * 0.07f));
                p.setColor(Color.argb(160, 235, 177, 112));
                for (int n = 0; n < 5; n++) {
                    float yy = y - fs * 0.67f + n * fs * 0.28f;
                    canvas.drawLine(fx - fs * 0.20f, yy, fx + fs * 0.20f, yy, p);
                }
                p.setStyle(Paint.Style.FILL);
            }
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
