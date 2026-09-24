package com.hidden.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS = "hidden_launcher";
    private static final String MODE_HOME = "home";
    private static final String MODE_DRAWER = "drawer";
    private static final String MODE_OFF = "off";
    private static final long HALF_HOUR = 30L * 60L * 1000L;
    private static final long REPEAT_WINDOW = 2L * 60L * 1000L;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<AppItem> apps = new ArrayList<>();
    private Screen currentScreen = Screen.HOME;
    private int onboardingPage = 0;
    private float homeDownY = 0f;

    private int bg;
    private int fg;
    private int muted;
    private int panel;
    private int line;

    private enum Screen { HOME, DRAWER, SETTINGS, ONBOARDING, PICKER, NOTIFICATION_REVIEW }

    static class AppItem {
        final String label;
        final String pkg;
        final String activity;

        AppItem(String label, String pkg, String activity) {
            this.label = label;
            this.pkg = pkg;
            this.activity = activity;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        apps = loadApps();
        ensureDefaults();
        applyPalette();

        if (!prefs.getBoolean("onboarding_complete", false)) {
            showOnboarding(0);
        } else {
            showHome();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) showHome();
    }

    @Override
    public void onBackPressed() {
        if (currentScreen == Screen.HOME) {
            super.onBackPressed();
            return;
        }
        if (currentScreen == Screen.ONBOARDING) {
            if (onboardingPage > 0) showOnboarding(onboardingPage - 1);
            else super.onBackPressed();
            return;
        }
        if (currentScreen == Screen.PICKER || currentScreen == Screen.NOTIFICATION_REVIEW) {
            if (!prefs.getBoolean("onboarding_complete", false)) showOnboarding(1);
            else showSettings();
            return;
        }
        showHome();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains("clock_mode")) e.putString("clock_mode", MODE_HOME);
        if (!prefs.contains("weather_mode")) e.putString("weather_mode", MODE_OFF);
        if (!prefs.contains("battery_mode")) e.putString("battery_mode", MODE_HOME);
        if (!prefs.contains("show_swipe_hint")) e.putBoolean("show_swipe_hint", true);
        if (!prefs.contains("theme_mode")) e.putString("theme_mode", "system");
        if (!prefs.contains("hidden_theme")) e.putString("hidden_theme", "dark");
        if (!prefs.contains("doom_screens")) e.putInt("doom_screens", 18);
        e.apply();
    }

    private boolean useDarkPalette() {
        String mode = prefs.getString("theme_mode", "system");
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyPalette() {
        boolean dark = useDarkPalette();
        if (dark) {
            bg = Color.rgb(18, 18, 18);
            fg = Color.rgb(239, 239, 236);
            muted = Color.rgb(157, 157, 151);
            panel = Color.rgb(31, 31, 31);
            line = Color.rgb(55, 55, 55);
            getWindow().getDecorView().setSystemUiVisibility(0);
        } else {
            bg = Color.rgb(241, 239, 232);
            fg = Color.rgb(21, 21, 19);
            muted = Color.rgb(113, 110, 103);
            panel = Color.rgb(227, 224, 215);
            line = Color.rgb(213, 209, 199);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return t;
    }

    private TextView heading(String value, float sp) {
        TextView t = text(value, sp, fg);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    private void pad(View v, int l, int t, int r, int b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(line);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private String utilMode(String key) {
        return prefs.getString(key, MODE_OFF);
    }

    private String modeLabel(String value) {
        if (MODE_HOME.equals(value)) return "HOME";
        if (MODE_DRAWER.equals(value)) return "DRAWER";
        return "OFF";
    }

    private String nextMode(String value) {
        if (MODE_HOME.equals(value)) return MODE_DRAWER;
        if (MODE_DRAWER.equals(value)) return MODE_OFF;
        return MODE_HOME;
    }

    private void showHome() {
        currentScreen = Screen.HOME;
        applyPalette();

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        pad(root, 28, 34, 28, 22);
        frame.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean hasAny = false;

        if (MODE_HOME.equals(utilMode("clock_mode"))) {
            TextView time = heading(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()), 62);
            TextView date = text(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()), 15, muted);
            root.addView(time);
            root.addView(date);
            hasAny = true;
        }

        if (MODE_HOME.equals(utilMode("weather_mode"))) {
            TextView weather = text("", 14, fg);
            pad(weather, 0, hasAny ? 18 : 0, 0, 0);
            root.addView(weather);
            renderWeather(weather);
            hasAny = true;
        }

        if (MODE_HOME.equals(utilMode("battery_mode"))) {
            TextView battery = text("battery · " + batteryPercent() + "%", 14, muted);
            pad(battery, 0, hasAny ? 4 : 0, 0, 0);
            root.addView(battery);
            hasAny = true;
        }

        LinearLayout essentials = new LinearLayout(this);
        essentials.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        ep.topMargin = dp(hasAny ? 24 : 0);
        root.addView(essentials, ep);

        Set<String> hidden = hiddenSet();
        Set<String> essential = essentialSet();
        for (AppItem app : apps) {
            if (essential.contains(app.pkg) && !hidden.contains(app.pkg)) {
                TextView row = text(app.label, 20, fg);
                pad(row, 0, 8, 0, 8);
                row.setOnClickListener(v -> launch(app));
                essentials.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
            }
        }

        if (prefs.getBoolean("show_swipe_hint", true)) {
            ChevronView arrow = new ChevronView(this);
            FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(dp(48), dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            ap.bottomMargin = dp(12);
            frame.addView(arrow, ap);
        }

        frame.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) homeDownY = event.getY();
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - homeDownY;
                if (dy < -dp(80)) {
                    showDrawer();
                    return true;
                }
            }
            return true;
        });

        setContentView(frame);
    }

    private void renderWeather(TextView target) {
        String city = prefs.getString("weather_city", "").trim();
        if (city.isEmpty()) {
            target.setText("weather · choose a city in Hidden Settings");
            return;
        }
        target.setText(city + " · checking weather");
        fetchWeather(city, target);
    }

    private void showDrawer() {
        currentScreen = Screen.DRAWER;
        applyPalette();

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        pad(top, 24, 18, 24, 10);
        outer.addView(top);

        TextView settings = heading("Hidden Settings", 17);
        pad(settings, 0, 7, 0, 7);
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings);

        String drawerInfo = drawerUtilityText();
        if (!drawerInfo.isEmpty()) {
            TextView utils = text(drawerInfo, 13, muted);
            pad(utils, 0, 6, 0, 5);
            top.addView(utils);
            if (MODE_DRAWER.equals(utilMode("weather_mode"))) renderDrawerWeather(utils);
        }

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(fg);
        search.setHintTextColor(muted);
        search.setBackgroundColor(panel);
        pad(search, 14, 9, 14, 9);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        sp.topMargin = dp(8);
        top.addView(search, sp);

        FrameLayout listFrame = new FrameLayout(this);
        outer.addView(listFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        DoomRecyclerView recycler = new DoomRecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        listFrame.addView(recycler, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        AlphabetRailView rail = new AlphabetRailView(this);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        rp.topMargin = dp(6);
        rp.bottomMargin = dp(8);
        listFrame.addView(rail, rp);

        DrawerAdapter adapter = new DrawerAdapter();
        recycler.setAdapter(adapter);
        recycler.setAdapterRef(adapter);

        rail.setListener(letter -> {
            int pos = adapter.positionForLetter(letter);
            if (pos >= 0) recycler.smoothScrollToPosition(pos);
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setQuery(s.toString());
                rail.setEnabled(s.toString().trim().isEmpty());
                rail.setAlpha(s.toString().trim().isEmpty() ? 1f : 0.18f);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            boolean insideHidden = false;

            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int first = lm.findFirstVisibleItemPosition();
                int hiddenStart = adapter.hiddenHeaderPosition();
                if (hiddenStart < 0) return;

                if (!insideHidden && first >= hiddenStart) {
                    insideHidden = true;
                    if (recordHiddenEntry()) adapter.setShowRepeatPause(true);
                } else if (insideHidden && first < Math.max(0, hiddenStart - 1)) {
                    insideHidden = false;
                }
            }
        });

        setContentView(outer);
    }

    private String drawerUtilityText() {
        List<String> bits = new ArrayList<>();
        if (MODE_DRAWER.equals(utilMode("clock_mode"))) {
            bits.add(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()));
        }
        if (MODE_DRAWER.equals(utilMode("battery_mode"))) {
            bits.add("battery " + batteryPercent() + "%");
        }
        if (MODE_DRAWER.equals(utilMode("weather_mode"))) {
            String city = prefs.getString("weather_city", "").trim();
            bits.add(city.isEmpty() ? "weather off" : city + " · checking");
        }
        return join(bits, "   ·   ");
    }

    private void renderDrawerWeather(TextView target) {
        String city = prefs.getString("weather_city", "").trim();
        if (city.isEmpty()) return;
        final String timePart = MODE_DRAWER.equals(utilMode("clock_mode")) ? new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()) : "";
        final String batteryPart = MODE_DRAWER.equals(utilMode("battery_mode")) ? "battery " + batteryPercent() + "%" : "";
        fetchWeatherValue(city, value -> {
            List<String> bits = new ArrayList<>();
            if (!timePart.isEmpty()) bits.add(timePart);
            if (!batteryPart.isEmpty()) bits.add(batteryPart);
            bits.add(value);
            if (currentScreen == Screen.DRAWER && target.getWindowToken() != null) target.setText(join(bits, "   ·   "));
        });
    }

    private String join(List<String> parts, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) b.append(sep);
            b.append(parts.get(i));
        }
        return b.toString();
    }

    private void showSettings() {
        currentScreen = Screen.SETTINGS;
        applyPalette();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pad(content, 26, 24, 26, 56);
        scroll.addView(content);

        TextView title = heading("Hidden Settings", 36);
        content.addView(title);
        TextView home = text("‹ home", 14, muted);
        pad(home, 0, 6, 0, 22);
        home.setOnClickListener(v -> showHome());
        content.addView(home);

        addSectionTitle(content, "DOOM SCROLL");
        TextView doomStatus = text(doomStatusText(), 13, muted);
        content.addView(doomStatus);

        TextView distanceLabel = heading(doomDistanceLabel(prefs.getInt("doom_screens", 18)), 16);
        pad(distanceLabel, 0, 12, 0, 2);
        content.addView(distanceLabel);

        SeekBar doom = doomSeekBar(distanceLabel);
        content.addView(doom);

        TextView pause = text(doomPaused() ? "resume Doom Scroll now" : "pause Doom Scroll for 30 minutes", 15, fg);
        pad(pause, 0, 12, 0, 10);
        pause.setOnClickListener(v -> {
            if (doomPaused()) prefs.edit().putLong("doom_pause_until", 0L).apply();
            else prefs.edit().putLong("doom_pause_until", System.currentTimeMillis() + HALF_HOUR).apply();
            showSettings();
        });
        content.addView(pause);

        addSectionTitle(content, "HOME");
        content.addView(modeRow("Clock", "clock_mode"));
        content.addView(modeRow("Weather", "weather_mode"));
        content.addView(modeRow("Battery", "battery_mode"));
        content.addView(toggleRow("Swipe-up hint", "show_swipe_hint"));
        content.addView(actionRow("Favourite apps", selectedCount(essentialSet()) + " selected", v -> showAppPicker(true)));

        addSectionTitle(content, "DRAWER");
        TextView drawerNote = text("Utilities set to DRAWER appear above the app list.", 13, muted);
        pad(drawerNote, 0, 0, 0, 10);
        content.addView(drawerNote);
        content.addView(actionRow("Alphabet rail", "A–Z", null));
        content.addView(actionRow("Search", "hidden apps stay excluded", null));

        addSectionTitle(content, "HIDDEN");
        content.addView(actionRow("Hidden apps", selectedCount(hiddenSet()) + " hidden", v -> showAppPicker(false)));
        content.addView(cycleRow("Hidden-area theme", hiddenThemeLabel(), v -> {
            prefs.edit().putString("hidden_theme", nextHiddenTheme()).apply();
            showSettings();
        }));
        content.addView(actionRow("Review notifications", "Android settings", v -> showNotificationReview()));

        addSectionTitle(content, "WEATHER");
        TextView note = text("Weather uses only the city you type here. Hidden never asks Android for your location.", 13, muted);
        note.setLineSpacing(0, 1.2f);
        pad(note, 0, 0, 0, 8);
        content.addView(note);

        EditText city = new EditText(this);
        city.setSingleLine(true);
        city.setHint("Adelaide, South Australia");
        city.setText(prefs.getString("weather_city", ""));
        city.setTextColor(fg);
        city.setHintTextColor(muted);
        city.setTextSize(15);
        city.setBackgroundColor(panel);
        pad(city, 12, 8, 12, 8);
        content.addView(city, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView save = text("save weather location", 15, fg);
        pad(save, 0, 9, 0, 0);
        save.setOnClickListener(v -> {
            prefs.edit().putString("weather_city", city.getText().toString().trim()).apply();
            save.setText("saved");
        });
        content.addView(save);

        addSectionTitle(content, "APPEARANCE");
        content.addView(cycleRow("Theme", themeLabel(), v -> {
            prefs.edit().putString("theme_mode", nextTheme()).apply();
            applyPalette();
            showSettings();
        }));

        addSectionTitle(content, "ABOUT HIDDEN");
        TextView privacy = text("No account. No analytics. No ads. No tracking. No location access. No notification access. Your settings stay on this phone.", 14, fg);
        privacy.setLineSpacing(0, 1.25f);
        content.addView(privacy);
        TextView internet = text("Internet is used only when weather is enabled.", 13, muted);
        pad(internet, 0, 8, 0, 14);
        content.addView(internet);

        content.addView(actionRow("Default Home app", isDefaultHome() ? "Hidden" : "change", v -> requestHomeRole()));
        TextView version = text("Hidden · v0.2", 12, muted);
        pad(version, 0, 24, 0, 0);
        content.addView(version);

        setContentView(scroll);
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView t = text(title, 12, muted);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        pad(t, 0, 28, 0, 8);
        parent.addView(t);
    }

    private View modeRow(String label, String key) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 16, fg);
        TextView right = text(modeLabel(utilMode(key)), 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(46), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(90), dp(46)));
        row.setOnClickListener(v -> {
            prefs.edit().putString(key, nextMode(utilMode(key))).apply();
            showSettings();
        });
        return row;
    }

    private View toggleRow(String label, String key) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 16, fg);
        TextView right = text(prefs.getBoolean(key, true) ? "ON" : "OFF", 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(46), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(90), dp(46)));
        row.setOnClickListener(v -> {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, true)).apply();
            showSettings();
        });
        return row;
    }

    private View cycleRow(String label, String value, View.OnClickListener listener) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 16, fg);
        TextView right = text(value, 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(46), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(150), dp(46)));
        if (listener != null) row.setOnClickListener(listener);
        return row;
    }

    private View actionRow(String label, String value, View.OnClickListener listener) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 16, fg);
        TextView right = text(value, 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(46), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(160), dp(46)));
        if (listener != null) row.setOnClickListener(listener);
        return row;
    }

    private LinearLayout settingRowBase() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(bg);
        return row;
    }

    private SeekBar doomSeekBar(TextView label) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(22);
        bar.setProgress(Math.max(0, Math.min(22, prefs.getInt("doom_screens", 18) - 8)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int screens = 8 + progress;
                label.setText(doomDistanceLabel(screens));
                if (fromUser) prefs.edit().putInt("doom_screens", screens).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return bar;
    }

    private String doomDistanceLabel(int screens) {
        String mood;
        if (screens <= 11) mood = "Short";
        else if (screens <= 17) mood = "Far";
        else if (screens <= 23) mood = "Very far";
        else mood = "Ridiculous";
        return mood + " · about " + screens + " swipes";
    }

    private String doomStatusText() {
        if (!doomPaused()) return "On · hidden apps stay deliberately far away.";
        long mins = Math.max(1, (prefs.getLong("doom_pause_until", 0L) - System.currentTimeMillis() + 59999) / 60000);
        return "Paused · about " + mins + " min remaining.";
    }

    private boolean doomPaused() {
        return prefs.getLong("doom_pause_until", 0L) > System.currentTimeMillis();
    }

    private void showOnboarding(int page) {
        onboardingPage = page;
        currentScreen = Screen.ONBOARDING;
        applyPalette();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pad(content, 28, 34, 28, 44);
        scroll.addView(content);

        TextView mark = text("HIDDEN", 12, muted);
        mark.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        content.addView(mark);

        if (page == 0) onboardingOne(content);
        else if (page == 1) onboardingTwo(content);
        else onboardingThree(content);

        setContentView(scroll);
    }

    private void onboardingOne(LinearLayout content) {
        TextView title = heading("What belongs on Home?", 34);
        pad(title, 0, 12, 0, 8);
        content.addView(title);

        TextView copy = text("Your home screen can contain as much — or as little — as you want. Utilities can live on Home, in the app drawer, or nowhere.", 15, muted);
        copy.setLineSpacing(0, 1.2f);
        content.addView(copy);

        addSectionTitle(content, "UTILITIES");
        content.addView(onboardingModeRow("Clock", "clock_mode"));
        content.addView(onboardingModeRow("Weather", "weather_mode"));
        content.addView(onboardingModeRow("Battery", "battery_mode"));

        TextView none = text("NONE · clear the home screen", 15, fg);
        pad(none, 0, 14, 0, 12);
        none.setOnClickListener(v -> {
            prefs.edit()
                .putString("clock_mode", MODE_OFF)
                .putString("weather_mode", MODE_OFF)
                .putString("battery_mode", MODE_OFF)
                .apply();
            showOnboarding(0);
        });
        content.addView(none);

        if (!MODE_OFF.equals(utilMode("weather_mode"))) {
            EditText city = new EditText(this);
            city.setSingleLine(true);
            city.setHint("Weather city · Adelaide, South Australia");
            city.setText(prefs.getString("weather_city", ""));
            city.setTextColor(fg);
            city.setHintTextColor(muted);
            city.setTextSize(15);
            city.setBackgroundColor(panel);
            pad(city, 12, 8, 12, 8);
            content.addView(city, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

            TextView save = text("save city", 14, fg);
            pad(save, 0, 8, 0, 8);
            save.setOnClickListener(v -> {
                prefs.edit().putString("weather_city", city.getText().toString().trim()).apply();
                save.setText("saved");
            });
            content.addView(save);
        }

        addSectionTitle(content, "LOOK");
        content.addView(onboardingCycleRow("Theme", themeLabel(), () -> {
            prefs.edit().putString("theme_mode", nextTheme()).apply();
            applyPalette();
            showOnboarding(0);
        }));

        addNextButton(content, "Next", 1);
    }

    private View onboardingModeRow(String label, String key) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(modeLabel(utilMode(key)), 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(52), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(100), dp(52)));
        row.setOnClickListener(v -> {
            prefs.edit().putString(key, nextMode(utilMode(key))).apply();
            showOnboarding(0);
        });
        return row;
    }

    private View onboardingCycleRow(String label, String value, Runnable action) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(value, 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(52), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(140), dp(52)));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void onboardingTwo(LinearLayout content) {
        TextView title = heading("What deserves to stay close?", 34);
        pad(title, 0, 12, 0, 8);
        content.addView(title);

        TextView copy = text("Pick the few apps you actually want within reach. Less is more. Especially time.", 15, muted);
        copy.setLineSpacing(0, 1.2f);
        content.addView(copy);

        TextView favourites = heading("Choose favourite apps", 17);
        pad(favourites, 0, 22, 0, 4);
        favourites.setOnClickListener(v -> showAppPicker(true));
        content.addView(favourites);
        content.addView(text(selectedCount(essentialSet()) + " selected", 13, muted));

        TextView hidden = heading("Choose apps to hide", 17);
        pad(hidden, 0, 24, 0, 4);
        hidden.setOnClickListener(v -> showAppPicker(false));
        content.addView(hidden);
        content.addView(text(selectedCount(hiddenSet()) + " hidden", 13, muted));

        if (!hiddenSet().isEmpty()) {
            TextView noteTitle = heading("One more thing.", 16);
            pad(noteTitle, 0, 22, 0, 4);
            content.addView(noteTitle);

            TextView note = text("Hidden can keep these apps out of sight, but their notifications can still come looking for you. Consider turning those notifications off yourself. Hidden will never request notification access.", 14, muted);
            note.setLineSpacing(0, 1.2f);
            content.addView(note);

            TextView review = text("review hidden-app notifications", 14, fg);
            pad(review, 0, 10, 0, 0);
            review.setOnClickListener(v -> showNotificationReview());
            content.addView(review);
        }

        addSectionTitle(content, "DOOM SCROLL");
        TextView doomCopy = text("How far away should distractions be?", 15, fg);
        content.addView(doomCopy);
        TextView doomLabel = text(doomDistanceLabel(prefs.getInt("doom_screens", 18)), 13, muted);
        pad(doomLabel, 0, 4, 0, 2);
        content.addView(doomLabel);
        content.addView(doomSeekBar(doomLabel));

        addNextButton(content, "Next", 2);
    }

    private void onboardingThree(LinearLayout content) {
        TextView title = heading("Keep the time thieves out of reach.", 34);
        pad(title, 0, 12, 0, 8);
        content.addView(title);

        TextView copy = text("The things you need stay close. The things that steal your time can stay far away.", 15, muted);
        copy.setLineSpacing(0, 1.2f);
        content.addView(copy);

        addSectionTitle(content, "HIDDEN AREA");
        content.addView(onboardingCycleRow("Theme", hiddenThemeLabel(), () -> {
            prefs.edit().putString("hidden_theme", nextHiddenTheme()).apply();
            showOnboarding(2);
        }));
        content.addView(onboardingCycleRow("Swipe-up hint", prefs.getBoolean("show_swipe_hint", true) ? "ON" : "OFF", () -> {
            prefs.edit().putBoolean("show_swipe_hint", !prefs.getBoolean("show_swipe_hint", true)).apply();
            showOnboarding(2);
        }));

        addSectionTitle(content, "PRIVACY");
        TextView privacy = text("No account. No analytics. No ads. No tracking. No location access. No notification access. Your settings stay on this phone.", 14, fg);
        privacy.setLineSpacing(0, 1.25f);
        content.addView(privacy);

        TextView weather = text("Weather is optional. If you enable it, Hidden uses only the city you typed. If weather is off, Hidden makes no weather requests.", 13, muted);
        weather.setLineSpacing(0, 1.25f);
        pad(weather, 0, 10, 0, 18);
        content.addView(weather);

        TextView homeStatus = text(isDefaultHome() ? "Hidden is already your Home app." : "Choose Hidden as your Home app.", 15, fg);
        pad(homeStatus, 0, 8, 0, 8);
        homeStatus.setOnClickListener(v -> requestHomeRole());
        content.addView(homeStatus);

        TextView finish = heading("Use Hidden", 18);
        finish.setGravity(Gravity.CENTER);
        finish.setBackgroundColor(panel);
        pad(finish, 12, 14, 12, 14);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        fp.topMargin = dp(18);
        content.addView(finish, fp);
        finish.setOnClickListener(v -> {
            prefs.edit().putBoolean("onboarding_complete", true).apply();
            if (!isDefaultHome()) requestHomeRole();
            showHome();
        });

        TextView back = text("‹ back", 14, muted);
        pad(back, 0, 18, 0, 0);
        back.setOnClickListener(v -> showOnboarding(1));
        content.addView(back);
    }

    private void addNextButton(LinearLayout content, String label, int page) {
        TextView next = heading(label + "  →", 17);
        next.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        pad(next, 0, 28, 0, 12);
        next.setOnClickListener(v -> showOnboarding(page));
        content.addView(next);
    }

    private void showAppPicker(boolean favourites) {
        currentScreen = Screen.PICKER;
        applyPalette();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        pad(root, 24, 22, 24, 14);

        TextView title = heading(favourites ? "Favourite apps" : "Apps to hide", 30);
        root.addView(title);

        TextView done = text("done", 14, muted);
        done.setGravity(Gravity.END);
        pad(done, 0, 4, 0, 10);
        done.setOnClickListener(v -> {
            if (!prefs.getBoolean("onboarding_complete", false)) showOnboarding(1);
            else showSettings();
        });
        root.addView(done);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Set<String> selected = favourites ? essentialSet() : hiddenSet();

        for (AppItem app : apps) {
            if (favourites && hiddenSet().contains(app.pkg)) continue;

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(app.label, 16, fg);
            TextView mark = text(selected.contains(app.pkg) ? "●" : "○", 17, muted);
            mark.setGravity(Gravity.CENTER);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(50), 1f));
            row.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(50)));

            row.setOnClickListener(v -> {
                Set<String> set = favourites ? essentialSet() : hiddenSet();
                if (set.contains(app.pkg)) set.remove(app.pkg);
                else set.add(app.pkg);

                if (favourites) {
                    saveSet("essential_packages", set);
                } else {
                    saveSet("hidden_packages", set);
                    if (set.contains(app.pkg)) {
                        Set<String> essentials = essentialSet();
                        if (essentials.remove(app.pkg)) saveSet("essential_packages", essentials);
                    }
                }
                mark.setText(set.contains(app.pkg) ? "●" : "○");
            });
            list.addView(row);
            list.addView(divider());
        }

        setContentView(root);
    }

    private void showNotificationReview() {
        currentScreen = Screen.NOTIFICATION_REVIEW;
        applyPalette();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pad(content, 26, 24, 26, 40);
        scroll.addView(content);

        TextView title = heading("Hidden app notifications", 30);
        content.addView(title);

        TextView copy = text("Hidden won't read or control your notifications. Tap an app below to open Android's own notification settings.", 14, muted);
        copy.setLineSpacing(0, 1.2f);
        pad(copy, 0, 8, 0, 16);
        content.addView(copy);

        Set<String> hidden = hiddenSet();
        for (AppItem app : apps) {
            if (!hidden.contains(app.pkg)) continue;
            TextView row = text(app.label, 16, fg);
            pad(row, 0, 9, 0, 9);
            row.setOnClickListener(v -> openNotificationSettings(app.pkg));
            content.addView(row);
            content.addView(divider());
        }

        TextView back = text("‹ back", 14, muted);
        pad(back, 0, 18, 0, 0);
        back.setOnClickListener(v -> {
            if (!prefs.getBoolean("onboarding_complete", false)) showOnboarding(1);
            else showSettings();
        });
        content.addView(back);

        setContentView(scroll);
    }

    private void openNotificationSettings(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private String themeLabel() {
        String t = prefs.getString("theme_mode", "system");
        if ("light".equals(t)) return "LIGHT";
        if ("dark".equals(t)) return "DARK";
        return "SYSTEM";
    }

    private String nextTheme() {
        String t = prefs.getString("theme_mode", "system");
        if ("system".equals(t)) return "light";
        if ("light".equals(t)) return "dark";
        return "system";
    }

    private String hiddenThemeLabel() {
        String t = prefs.getString("hidden_theme", "dark");
        if ("light".equals(t)) return "LIGHT";
        if ("grayscale".equals(t)) return "GREYSCALE";
        if ("8bit".equals(t)) return "8-BIT MESS";
        if ("doom".equals(t)) return "DOOM SCROLL";
        return "DARK";
    }

    private String nextHiddenTheme() {
        String t = prefs.getString("hidden_theme", "dark");
        if ("dark".equals(t)) return "light";
        if ("light".equals(t)) return "grayscale";
        if ("grayscale".equals(t)) return "8bit";
        if ("8bit".equals(t)) return "doom";
        return "dark";
    }

    private int selectedCount(Set<String> set) {
        return set == null ? 0 : set.size();
    }

    private List<AppItem> loadApps() {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL);
        List<AppItem> out = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            if (getPackageName().equals(info.activityInfo.packageName)) continue;
            String label = info.loadLabel(getPackageManager()).toString();
            out.add(new AppItem(label, info.activityInfo.packageName, info.activityInfo.name));
        }
        Collections.sort(out, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private Set<String> hiddenSet() {
        Set<String> raw = prefs.getStringSet("hidden_packages", Collections.emptySet());
        return new HashSet<>(raw == null ? Collections.emptySet() : raw);
    }

    private Set<String> essentialSet() {
        Set<String> raw = prefs.getStringSet("essential_packages", Collections.emptySet());
        return new HashSet<>(raw == null ? Collections.emptySet() : raw);
    }

    private void saveSet(String key, Set<String> set) {
        prefs.edit().putStringSet(key, new HashSet<>(set)).apply();
    }

    private void launch(AppItem app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(app.pkg, app.activity));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private int batteryPercent() {
        Intent status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) return 0;
        int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return scale <= 0 ? 0 : Math.round(level * 100f / scale);
    }

    private boolean isDefaultHome() {
        RoleManager rm = getSystemService(RoleManager.class);
        return rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME);
    }

    private void requestHomeRole() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 42);
        }
    }

    private boolean recordHiddenEntry() {
        long now = System.currentTimeMillis();
        String raw = prefs.getString("hidden_entry_times", "");
        ArrayDeque<Long> q = new ArrayDeque<>();
        if (!raw.isEmpty()) {
            String[] parts = raw.split(",");
            for (String p : parts) {
                try {
                    long t = Long.parseLong(p);
                    if (now - t <= REPEAT_WINDOW) q.addLast(t);
                } catch (Exception ignored) {}
            }
        }
        q.addLast(now);
        while (q.size() > 4) q.removeFirst();

        StringBuilder b = new StringBuilder();
        for (Long t : q) {
            if (b.length() > 0) b.append(",");
            b.append(t);
        }
        prefs.edit().putString("hidden_entry_times", b.toString()).apply();
        return q.size() >= 3 && !doomPaused();
    }

    interface WeatherCallback { void onValue(String value); }

    private void fetchWeather(String city, TextView target) {
        fetchWeatherValue(city, value -> {
            if ((currentScreen == Screen.HOME || currentScreen == Screen.DRAWER) && target.getWindowToken() != null) {
                target.setText(value);
            }
        });
    }

    private void fetchWeatherValue(String city, WeatherCallback callback) {
        new Thread(() -> {
            String result;
            try {
                String first = city.split(",")[0].trim();
                String q = URLEncoder.encode(first, StandardCharsets.UTF_8.toString());
                JSONObject geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=5&language=en&format=json");
                JSONArray results = geo.optJSONArray("results");
                if (results == null || results.length() == 0) throw new Exception("not found");

                JSONObject place = results.getJSONObject(0);
                double lat = place.getDouble("latitude");
                double lon = place.getDouble("longitude");
                String name = place.optString("name", first);
                String admin = place.optString("admin1", "");
                String label = admin.isEmpty() || admin.equalsIgnoreCase(name) ? name : name + ", " + admin;

                JSONObject forecast = getJson("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,weather_code&timezone=auto&forecast_days=1");
                JSONObject current = forecast.getJSONObject("current");
                int temp = (int)Math.round(current.getDouble("temperature_2m"));
                int code = current.optInt("weather_code", -1);
                result = label + " · " + temp + "° · " + weatherLabel(code);
            } catch (Exception e) {
                result = city + " · weather unavailable";
            }
            final String finalResult = result;
            runOnUiThread(() -> callback.onValue(finalResult));
        }).start();
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "HiddenLauncher/0.2");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code > 299) throw new Exception("http");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            r.close();
            return new JSONObject(b.toString());
        } finally {
            c.disconnect();
        }
    }

    private String weatherLabel(int code) {
        if (code == 0) return "clear";
        if (code == 1) return "mostly clear";
        if (code == 2) return "partly cloudy";
        if (code == 3) return "overcast";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 57) return "drizzle";
        if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return "showers";
        if (code == 85 || code == 86) return "snow showers";
        if (code >= 95) return "storm";
        return "weather";
    }

    class ChevronView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ChevronView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            p.setColor(muted);
            p.setStrokeWidth(dp(2));
            p.setStyle(Paint.Style.STROKE);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f + dp(3);
            canvas.drawLine(cx - dp(8), cy + dp(4), cx, cy - dp(4), p);
            canvas.drawLine(cx, cy - dp(4), cx + dp(8), cy + dp(4), p);
        }
    }

    class DoomRecyclerView extends RecyclerView {
        DrawerAdapter adapterRef;
        DoomRecyclerView(Context context) { super(context); }
        void setAdapterRef(DrawerAdapter ref) { adapterRef = ref; }

        @Override public boolean fling(int velocityX, int velocityY) {
            if (adapterRef != null && adapterRef.isJourneyVisible()) {
                LinearLayoutManager lm = (LinearLayoutManager)getLayoutManager();
                if (lm != null) {
                    int first = lm.findFirstVisibleItemPosition();
                    int journey = adapterRef.journeyPosition();
                    if (journey >= 0 && Math.abs(first - journey) <= 1) {
                        velocityY = (int)(velocityY * 0.22f);
                    }
                }
            }
            return super.fling(velocityX, velocityY);
        }
    }

    class AlphabetRailView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private char active = 0;
        private boolean enabledRail = true;
        interface Listener { void onLetter(char letter); }
        private Listener listener;

        AlphabetRailView(Context context) {
            super(context);
            setClickable(true);
        }

        void setListener(Listener l) { listener = l; }

        @Override public void setEnabled(boolean enabled) {
            enabledRail = enabled;
            super.setEnabled(enabled);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!enabledRail) return;
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            paint.setColor(muted);
            float step = getHeight() / (float)letters.length();
            for (int i = 0; i < letters.length(); i++) {
                float y = step * i + step * 0.7f;
                canvas.drawText(String.valueOf(letters.charAt(i)), getWidth() - dp(12), y, paint);
            }

            if (active != 0) {
                paint.setTextSize(dp(34));
                paint.setColor(fg);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(String.valueOf(active), getWidth() - dp(50), getHeight() / 2f, paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!enabledRail || listener == null) return false;
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                active = 0;
                invalidate();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                int idx = Math.max(0, Math.min(letters.length() - 1, (int)(e.getY() / Math.max(1f, getHeight()) * letters.length())));
                char next = letters.charAt(idx);
                if (next != active) {
                    active = next;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    listener.onLetter(next);
                    invalidate();
                }
                return true;
            }
            return true;
        }
    }

    class JourneyView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random(4815162342L);
        private final List<float[]> stars = new ArrayList<>();

        JourneyView(Context context, int height) {
            super(context);
            setMinimumHeight(height);
            buildStars(height);
        }

        private void buildStars(int height) {
            int screen = getResources().getDisplayMetrics().heightPixels;
            int starStart = (int)(height * 0.78f);
            int count = Math.max(80, (height - starStart) / Math.max(1, screen) * 42);
            int width = getResources().getDisplayMetrics().widthPixels;
            for (int i = 0; i < count; i++) {
                float x = random.nextFloat() * width;
                float y = starStart + random.nextFloat() * Math.max(1, height - starStart);
                float r = dp(1) + random.nextFloat() * dp(1.4f);
                float a = 0.35f + random.nextFloat() * 0.65f;
                stars.add(new float[]{x, y, r, a});
            }
        }

        private int dp(float v) {
            return Math.round(v * getResources().getDisplayMetrics().density);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int h = getHeight();
            int screen = getResources().getDisplayMetrics().heightPixels;
            int screens = prefs.getInt("doom_screens", 18);
            int blankScreens = Math.min(4, Math.max(2, screens / 4));
            int blank = Math.min(h - 1, screen * blankScreens);
            int colorEnd = Math.max(blank + 1, (int)(h * 0.78f));

            p.setShader(null);
            p.setColor(bg);
            canvas.drawRect(0, 0, getWidth(), blank, p);

            int[] colors = useDarkPalette()
                ? new int[]{bg, Color.rgb(72, 58, 83), Color.rgb(126, 66, 94), Color.rgb(170, 81, 91), Color.rgb(113, 55, 103), Color.rgb(38, 39, 64), Color.rgb(7, 8, 11)}
                : new int[]{bg, Color.rgb(239, 218, 145), Color.rgb(239, 163, 92), Color.rgb(222, 95, 86), Color.rgb(181, 70, 112), Color.rgb(111, 58, 119), Color.rgb(44, 45, 71), Color.rgb(7, 8, 11)};

            LinearGradient g = new LinearGradient(0, blank, 0, colorEnd, colors, null, Shader.TileMode.CLAMP);
            p.setShader(g);
            canvas.drawRect(0, blank, getWidth(), colorEnd, p);
            p.setShader(null);
            p.setColor(Color.rgb(7, 8, 11));
            canvas.drawRect(0, colorEnd, getWidth(), h, p);

            Rect clip = canvas.getClipBounds();
            for (float[] s : stars) {
                if (s[1] < clip.top - dp(4) || s[1] > clip.bottom + dp(4)) continue;
                p.setColor(Color.argb((int)(255 * s[3]), 245, 245, 243));
                canvas.drawCircle(s[0], s[1], s[2], p);
            }
        }
    }

    class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_APP = 1;
        private static final int TYPE_JOURNEY = 2;
        private static final int TYPE_HIDDEN_HEADER = 3;
        private static final int TYPE_HIDDEN_APP = 4;

        private String query = "";
        private final List<Object> items = new ArrayList<>();
        private final Map<Character,Integer> letterPositions = new HashMap<>();
        private int journeyPos = -1;
        private int hiddenHeaderPos = -1;
        private boolean showRepeatPause = false;

        DrawerAdapter() { rebuild(); }

        void setQuery(String value) {
            query = value == null ? "" : value.trim();
            rebuild();
            notifyDataSetChanged();
        }

        void setShowRepeatPause(boolean value) {
            showRepeatPause = value;
            if (hiddenHeaderPos >= 0) notifyItemChanged(hiddenHeaderPos);
        }

        int positionForLetter(char letter) {
            Integer p = letterPositions.get(letter);
            if (p != null) return p;
            for (char c = letter; c <= 'Z'; c++) {
                p = letterPositions.get(c);
                if (p != null) return p;
            }
            for (char c = letter; c >= 'A'; c--) {
                p = letterPositions.get(c);
                if (p != null) return p;
            }
            return -1;
        }

        int journeyPosition() { return journeyPos; }
        int hiddenHeaderPosition() { return hiddenHeaderPos; }
        boolean isJourneyVisible() { return journeyPos >= 0; }

        private void rebuild() {
            items.clear();
            letterPositions.clear();
            journeyPos = -1;
            hiddenHeaderPos = -1;

            Set<String> hidden = hiddenSet();
            String q = query.toLowerCase(Locale.ROOT);

            for (AppItem app : apps) {
                if (hidden.contains(app.pkg)) continue;
                if (!q.isEmpty() && !app.label.toLowerCase(Locale.ROOT).contains(q)) continue;
                int position = items.size();
                items.add(app);
                if (!app.label.isEmpty()) {
                    char c = Character.toUpperCase(app.label.charAt(0));
                    if (c >= 'A' && c <= 'Z' && !letterPositions.containsKey(c)) letterPositions.put(c, position);
                }
            }

            if (!q.isEmpty() || hidden.isEmpty()) return;

            if (!doomPaused()) {
                journeyPos = items.size();
                items.add("JOURNEY");
            }

            hiddenHeaderPos = items.size();
            items.add("HIDDEN_HEADER");

            for (AppItem app : apps) {
                if (hidden.contains(app.pkg)) items.add(new HiddenApp(app));
            }
        }

        @Override public int getItemViewType(int position) {
            Object item = items.get(position);
            if (item instanceof AppItem) return TYPE_APP;
            if (item instanceof HiddenApp) return TYPE_HIDDEN_APP;
            if ("JOURNEY".equals(item)) return TYPE_JOURNEY;
            return TYPE_HIDDEN_HEADER;
        }

        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == TYPE_JOURNEY) {
                int height = getResources().getDisplayMetrics().heightPixels * prefs.getInt("doom_screens", 18);
                JourneyView v = new JourneyView(MainActivity.this, height);
                v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
                return new SimpleHolder(v);
            }

            if (type == TYPE_HIDDEN_HEADER) {
                LinearLayout block = new LinearLayout(MainActivity.this);
                block.setOrientation(LinearLayout.VERTICAL);
                pad(block, 26, 24, 26, 16);
                return new SimpleHolder(block);
            }

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            pad(row, 24, 0, 54, 0);
            TextView label = text("", 17, fg);
            row.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            return new AppHolder(row, label);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);

            if (type == TYPE_APP) {
                AppItem app = (AppItem)items.get(position);
                AppHolder h = (AppHolder)holder;
                h.root.setBackgroundColor(bg);
                h.label.setText(app.label);
                h.label.setTextColor(fg);
                h.label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                h.root.setOnClickListener(v -> launch(app));
                h.root.setOnLongClickListener(v -> {
                    confirmHide(app);
                    return true;
                });
                return;
            }

            if (type == TYPE_HIDDEN_APP) {
                HiddenApp wrap = (HiddenApp)items.get(position);
                AppHolder h = (AppHolder)holder;
                applyHiddenRowTheme(h, wrap.app);
                h.root.setOnClickListener(v -> launch(wrap.app));
                h.root.setOnLongClickListener(v -> {
                    confirmRestore(wrap.app);
                    return true;
                });
                return;
            }

            if (type == TYPE_HIDDEN_HEADER) {
                LinearLayout block = (LinearLayout)holder.itemView;
                block.removeAllViews();
                HiddenPalette hp = hiddenPalette();
                block.setBackgroundColor(hp.background);

                TextView title = headingForHidden(hiddenThemeLabel().equals("DOOM SCROLL") ? "DOOM SCROLL" : "hidden", 36, hp.foreground);
                block.addView(title);

                if ("doom".equals(prefs.getString("hidden_theme", "dark"))) {
                    TextView fine = text("fine.", 13, hp.muted);
                    pad(fine, 0, 2, 0, 8);
                    block.addView(fine);
                }

                if (showRepeatPause && !doomPaused()) {
                    TextView pause = text("You've made this trip a few times. Pause Doom Scroll for 30 minutes?", 14, hp.foreground);
                    pause.setLineSpacing(0, 1.2f);
                    pad(pause, 0, 12, 0, 6);
                    block.addView(pause);

                    TextView action = headingForHidden("pause for 30 minutes", 14, hp.foreground);
                    pad(action, 0, 4, 0, 2);
                    action.setOnClickListener(v -> {
                        prefs.edit().putLong("doom_pause_until", System.currentTimeMillis() + HALF_HOUR).apply();
                        showDrawer();
                    });
                    block.addView(action);
                }
            }
        }

        @Override public int getItemCount() { return items.size(); }

        void confirmHide(AppItem app) {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Hide " + app.label + "?")
                .setMessage("It will disappear from the normal list and search.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Hide it", (d, w) -> {
                    Set<String> hidden = hiddenSet();
                    hidden.add(app.pkg);
                    saveSet("hidden_packages", hidden);
                    Set<String> essential = essentialSet();
                    if (essential.remove(app.pkg)) saveSet("essential_packages", essential);
                    rebuild();
                    notifyDataSetChanged();
                })
                .show();
        }

        void confirmRestore(AppItem app) {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Bring " + app.label + " back?")
                .setMessage("It will return to the normal app list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (d, w) -> {
                    Set<String> hidden = hiddenSet();
                    hidden.remove(app.pkg);
                    saveSet("hidden_packages", hidden);
                    rebuild();
                    notifyDataSetChanged();
                })
                .show();
        }

        TextView headingForHidden(String value, float sp, int color) {
            TextView t = text(value, sp, color);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            return t;
        }

        void applyHiddenRowTheme(AppHolder h, AppItem app) {
            HiddenPalette hp = hiddenPalette();
            h.root.setBackgroundColor(hp.background);
            h.label.setTextColor(hp.foreground);
            h.label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

            String theme = prefs.getString("hidden_theme", "dark");
            if ("8bit".equals(theme)) {
                h.label.setTypeface(Typeface.MONOSPACE);
                h.label.setText(glitchLabel(app.label));
            } else if ("doom".equals(theme)) {
                h.label.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                h.label.setText(app.label.toUpperCase(Locale.ROOT));
            } else {
                h.label.setText(app.label);
            }
        }

        String glitchLabel(String label) {
            int mod = Math.abs(label.hashCode()) % 3;
            if (mod == 0) return "▌ " + label + "  ░";
            if (mod == 1) return "░ " + label + "  ▌";
            return "▓ " + label;
        }
    }

    static class HiddenApp {
        final AppItem app;
        HiddenApp(AppItem app) { this.app = app; }
    }

    static class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(View v) { super(v); }
    }

    static class AppHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final TextView label;
        AppHolder(LinearLayout root, TextView label) {
            super(root);
            this.root = root;
            this.label = label;
        }
    }

    static class HiddenPalette {
        final int background;
        final int foreground;
        final int muted;
        HiddenPalette(int background, int foreground, int muted) {
            this.background = background;
            this.foreground = foreground;
            this.muted = muted;
        }
    }

    private HiddenPalette hiddenPalette() {
        String theme = prefs.getString("hidden_theme", "dark");
        if ("light".equals(theme)) return new HiddenPalette(Color.rgb(242, 240, 234), Color.rgb(22, 22, 20), Color.rgb(112, 112, 106));
        if ("grayscale".equals(theme)) return new HiddenPalette(Color.rgb(184, 184, 184), Color.rgb(35, 35, 35), Color.rgb(86, 86, 86));
        if ("8bit".equals(theme)) return new HiddenPalette(Color.rgb(12, 12, 15), Color.rgb(235, 235, 231), Color.rgb(128, 128, 132));
        if ("doom".equals(theme)) return new HiddenPalette(Color.rgb(10, 6, 6), Color.rgb(222, 78, 67), Color.rgb(143, 88, 80));
        return new HiddenPalette(Color.rgb(8, 9, 12), Color.rgb(241, 241, 238), Color.rgb(136, 138, 145));
    }
}
