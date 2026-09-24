package com.hidden.launcher;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class HiddenWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new HiddenEngine();
    }

    class HiddenEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final SharedPreferences prefs =
            getSharedPreferences("hidden_launcher", MODE_PRIVATE);

        HiddenEngine() {
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) draw();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            draw();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("theme_mode".equals(key)) draw();
        }

        @Override
        public void onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        private void draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(backgroundForTheme());
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        private int backgroundForTheme() {
            String mode = prefs.getString("theme_mode", "system");

            if ("oled".equals(mode)) return Color.BLACK;
            if ("dark".equals(mode)) return Color.rgb(18, 18, 18);
            if ("light".equals(mode)) return Color.rgb(241, 239, 232);

            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (night == Configuration.UI_MODE_NIGHT_YES) {
                return Color.rgb(18, 18, 18);
            }
            return Color.rgb(241, 239, 232);
        }
    }
}
