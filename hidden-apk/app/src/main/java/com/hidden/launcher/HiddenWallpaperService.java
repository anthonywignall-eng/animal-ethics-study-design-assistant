package com.hidden.launcher;

import android.content.SharedPreferences;
import android.graphics.Canvas;
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
            if ("home_theme".equals(key) || "theme_mode".equals(key)) draw();
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

                String fallback = prefs.getString("theme_mode", "dark");
                String theme = prefs.getString("home_theme", fallback);
                ThemeArt.draw(
                    canvas,
                    canvas.getWidth(),
                    canvas.getHeight(),
                    theme,
                    getResources(),
                    4815162342L
                );
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
