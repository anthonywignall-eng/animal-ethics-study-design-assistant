package com.hidden.launcher;

import android.animation.ValueAnimator;
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
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private static final String MODE_BOTH = "both";
    private static final String MODE_OFF = "off";
    private static final long HALF_HOUR = 30L * 60L * 1000L;
    private static final long REPEAT_WINDOW = 2L * 60L * 1000L;
    private static final int ONBOARDING_VERSION = 3;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<AppItem> apps = new ArrayList<>();

    private int bg;
    private int fg;
    private int muted;
    private int panel;
    private int line;

    private Screen currentScreen = Screen.HOME;
    private int onboardingPage = 0;

    private RecyclerView launcherRecycler;
    private LauncherAdapter launcherAdapter;
    private AlphabetRailView alphabetRail;
    private boolean alphabetRailTargetVisible = false;

    private enum Screen { HOME, SETTINGS, ONBOARDING, PICKER, NOTIFICATION_REVIEW, INTRO }

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

        if (prefs.getInt("onboarding_version", 0) < ONBOARDING_VERSION) {
            showIntroWelcome();
        } else {
            showLauncherSurface(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (launcherRecycler == null || currentScreen != Screen.HOME) {
                showLauncherSurface(true);
            } else {
                rewindHome(true);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (currentScreen == Screen.HOME && launcherRecycler != null) {
            LinearLayoutManager lm = (LinearLayoutManager) launcherRecycler.getLayoutManager();
            int first = lm == null ? 0 : lm.findFirstVisibleItemPosition();
            if (first > 0) {
                rewindHome(true);
                return;
            }
            super.onBackPressed();
            return;
        }

        if (currentScreen == Screen.ONBOARDING) {
            if (onboardingPage > 0) showOnboarding(onboardingPage - 1);
            else showIntroWelcome();
            return;
        }

        if (currentScreen == Screen.PICKER || currentScreen == Screen.NOTIFICATION_REVIEW) {
            if (prefs.getInt("onboarding_version", 0) < ONBOARDING_VERSION) showOnboarding(1);
            else showSettings();
            return;
        }

        if (currentScreen == Screen.INTRO) {
            super.onBackPressed();
            return;
        }

        showLauncherSurface(true);
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
            muted = Color.rgb(158, 158, 151);
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

    private int availableHeight() {
        int h = getResources().getDisplayMetrics().heightPixels;
        int status = systemBarHeight("status_bar_height");
        int nav = systemBarHeight("navigation_bar_height");
        return Math.max(dp(620), h - status - nav);
    }

    private int systemBarHeight(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
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

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(line);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private boolean modeOnHome(String key) {
        String v = prefs.getString(key, MODE_OFF);
        return MODE_HOME.equals(v) || MODE_BOTH.equals(v);
    }

    private boolean modeInDrawer(String key) {
        String v = prefs.getString(key, MODE_OFF);
        return MODE_DRAWER.equals(v) || MODE_BOTH.equals(v);
    }

    private String modeLabel(String value) {
        if (MODE_HOME.equals(value)) return "HOME";
        if (MODE_DRAWER.equals(value)) return "DRAWER";
        if (MODE_BOTH.equals(value)) return "BOTH";
        return "NONE";
    }

    private String nextMode(String value) {
        if (MODE_HOME.equals(value)) return MODE_DRAWER;
        if (MODE_DRAWER.equals(value)) return MODE_BOTH;
        if (MODE_BOTH.equals(value)) return MODE_OFF;
        return MODE_HOME;
    }

    private void showLauncherSurface(boolean rewind) {
        currentScreen = Screen.HOME;
        applyPalette();

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(bg);

        launcherRecycler = new RecyclerView(this);
        launcherRecycler.setLayoutManager(new LinearLayoutManager(this));
        launcherRecycler.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        launcherRecycler.setItemAnimator(null);
        launcherAdapter = new LauncherAdapter();
        launcherRecycler.setAdapter(launcherAdapter);
        frame.addView(launcherRecycler, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        alphabetRail = new AlphabetRailView(this);
        alphabetRailTargetVisible = false;
        alphabetRail.setAlpha(0f);
        alphabetRail.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        rp.topMargin = dp(18);
        rp.bottomMargin = dp(18);
        frame.addView(alphabetRail, rp);

        alphabetRail.setListener(letter -> {
            int pos = launcherAdapter.positionForLetter(letter);
            if (pos >= 0) launcherRecycler.smoothScrollToPosition(pos);
        });

        launcherRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            boolean insideHidden = false;

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int first = lm.findFirstVisibleItemPosition();
                int last = lm.findLastVisibleItemPosition();
                int journey = launcherAdapter.journeyPosition();
                int hiddenStart = launcherAdapter.hiddenHeaderPosition();

                boolean showRail = launcherAdapter.query.isEmpty()
                    && first >= launcherAdapter.firstAppPosition()
                    && (journey < 0 || last < journey);

                setAlphabetRailVisible(showRail);

                if (hiddenStart >= 0) {
                    if (!insideHidden && first >= hiddenStart) {
                        insideHidden = true;
                        if (recordHiddenEntry()) launcherAdapter.setShowRepeatPause(true);
                    } else if (insideHidden && first < Math.max(0, hiddenStart - 1)) {
                        insideHidden = false;
                    }
                }
            }
        });

        setContentView(frame);
        if (rewind) launcherRecycler.scrollToPosition(0);
    }

    private void rewindHome(boolean smooth) {
        if (launcherRecycler == null) {
            showLauncherSurface(true);
            return;
        }
        if (smooth) {
            launcherRecycler.stopScroll();
            launcherRecycler.smoothScrollToPosition(0);
        } else {
            launcherRecycler.scrollToPosition(0);
        }
    }

    private void setAlphabetRailVisible(boolean visible) {
        if (alphabetRail == null || alphabetRailTargetVisible == visible) return;
        alphabetRailTargetVisible = visible;
        alphabetRail.animate().cancel();

        if (visible) {
            alphabetRail.setVisibility(View.VISIBLE);
            alphabetRail.setAlpha(0f);
            alphabetRail.animate().alpha(1f).setDuration(170).start();
        } else {
            alphabetRail.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> {
                    if (!alphabetRailTargetVisible && alphabetRail != null) {
                        alphabetRail.setVisibility(View.INVISIBLE);
                    }
                })
                .start();
        }
    }

    private LinearLayout buildHomePanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setMinimumHeight(availableHeight());
        pad(root, 28, 66, 28, 20);

        boolean anyUtility = false;

        if (modeOnHome("clock_mode")) {
            TextView time = heading(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()), 62);
            TextView date = text(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()), 15, muted);
            root.addView(time);
            root.addView(date);
            anyUtility = true;
        }

        if (modeOnHome("weather_mode")) {
            TextView weather = text("", 14, fg);
            pad(weather, 0, anyUtility ? 18 : 0, 0, 0);
            root.addView(weather);
            renderWeather(weather);
            anyUtility = true;
        }

        if (modeOnHome("battery_mode")) {
            TextView battery = text("Battery · " + batteryPercent() + "%", 14, muted);
            pad(battery, 0, anyUtility ? 4 : 0, 0, 0);
            root.addView(battery);
            anyUtility = true;
        }

        View stretch = new View(this);
        root.addView(stretch, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout favourites = new LinearLayout(this);
        favourites.setOrientation(LinearLayout.VERTICAL);
        Set<String> hidden = hiddenSet();
        Set<String> essential = essentialSet();

        for (AppItem app : apps) {
            if (essential.contains(app.pkg) && !hidden.contains(app.pkg)) {
                TextView row = text(app.label, 21, fg);
                row.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                pad(row, 0, 5, 0, 5);
                row.setOnClickListener(v -> launch(app));
                favourites.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            }
        }

        root.addView(favourites);

        if (prefs.getBoolean("show_swipe_hint", true)) {
            ChevronView arrow = new ChevronView(this);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            ap.topMargin = dp(12);
            root.addView(arrow, ap);
            arrow.setOnClickListener(v -> launcherRecycler.smoothScrollToPosition(1));
        }

        return root;
    }

    private LinearLayout buildDrawerHeader() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(bg);
        pad(top, 26, 42, 58, 14);

        TextView settings = heading("HIDDEN Settings", 19);
        pad(settings, 0, 5, 0, 10);
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings);

        String drawerInfo = drawerUtilityText();
        if (!drawerInfo.isEmpty()) {
            TextView utils = text(drawerInfo, 14, muted);
            pad(utils, 0, 2, 0, 10);
            top.addView(utils);
            if (modeInDrawer("weather_mode")) renderDrawerWeather(utils);
        }

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(fg);
        search.setHintTextColor(muted);
        search.setBackgroundColor(panel);
        pad(search, 14, 9, 14, 9);
        search.setText(launcherAdapter == null ? "" : launcherAdapter.query);
        search.setSelection(search.length());
        top.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (launcherAdapter != null) launcherAdapter.setQuery(s.toString());
                if (!s.toString().trim().isEmpty()) setAlphabetRailVisible(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        return top;
    }

    private String drawerUtilityText() {
        List<String> bits = new ArrayList<>();
        if (modeInDrawer("clock_mode")) bits.add(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()));
        if (modeInDrawer("battery_mode")) bits.add("Battery " + batteryPercent() + "%");
        if (modeInDrawer("weather_mode")) {
            String cached = cachedWeather();
            if (!cached.isEmpty()) bits.add(cached);
            else {
                String city = prefs.getString("weather_city", "").trim();
                bits.add(city.isEmpty() ? "Weather off" : city + " · checking");
            }
        }
        return join(bits, "   ·   ");
    }

    private void renderDrawerWeather(TextView target) {
        String city = prefs.getString("weather_city", "").trim();
        if (city.isEmpty()) return;

        final String timePart = modeInDrawer("clock_mode") ? new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()) : "";
        final String batteryPart = modeInDrawer("battery_mode") ? "Battery " + batteryPercent() + "%" : "";

        fetchWeatherValue(city, (ok, value) -> {
            List<String> bits = new ArrayList<>();
            if (!timePart.isEmpty()) bits.add(timePart);
            if (!batteryPart.isEmpty()) bits.add(batteryPart);
            bits.add(value);
            if (target.getWindowToken() != null) target.setText(join(bits, "   ·   "));
        });
    }

    private void renderWeather(TextView target) {
        String city = prefs.getString("weather_city", "").trim();
        if (city.isEmpty()) {
            target.setText("Weather · choose a city in HIDDEN Settings");
            return;
        }

        String cached = cachedWeather();
        target.setText(cached.isEmpty() ? city + " · checking weather" : cached);

        fetchWeatherValue(city, (ok, value) -> {
            if (target.getWindowToken() != null) target.setText(value);
        });
    }

    private String cachedWeather() {
        long at = prefs.getLong("last_weather_at", 0L);
        if (System.currentTimeMillis() - at > 2L * 60L * 60L * 1000L) return "";
        return prefs.getString("last_weather", "");
    }

    private void showSettings() {
        currentScreen = Screen.SETTINGS;
        applyPalette();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pad(content, 26, 42, 26, 60);
        scroll.addView(content);

        TextView title = heading("HIDDEN Settings", 36);
        content.addView(title);

        TextView back = text("‹ home", 15, muted);
        pad(back, 0, 8, 0, 18);
        back.setOnClickListener(v -> showLauncherSurface(true));
        content.addView(back);

        addSectionTitle(content, "DOOM SCROLL");
        TextView doomStatus = text(doomStatusText(), 14, muted);
        content.addView(doomStatus);

        TextView distanceLabel = heading(doomDistanceLabel(prefs.getInt("doom_screens", 18)), 17);
        pad(distanceLabel, 0, 12, 0, 4);
        content.addView(distanceLabel);

        SeekBar doom = doomSeekBar(distanceLabel);
        content.addView(doom);

        TextView pause = boldAction(doomPaused() ? "Resume Doom Scroll now" : "Pause Doom Scroll for 30 minutes");
        pause.setOnClickListener(v -> {
            if (doomPaused()) prefs.edit().putLong("doom_pause_until", 0L).apply();
            else prefs.edit().putLong("doom_pause_until", System.currentTimeMillis() + HALF_HOUR).apply();
            showSettings();
        });
        content.addView(pause);

        addSectionTitle(content, "UTILITIES");
        content.addView(modeRow("Clock", "clock_mode"));
        content.addView(modeRow("Weather", "weather_mode"));
        content.addView(modeRow("Battery", "battery_mode"));

        addSectionTitle(content, "HOME");
        content.addView(toggleRow("Swipe-up hint", "show_swipe_hint"));
        content.addView(actionRow("Favourite apps", selectedCount(essentialSet()) + " selected", v -> showAppPicker(true)));

        addSectionTitle(content, "HIDDEN");
        content.addView(actionRow("HIDDEN apps", selectedCount(hiddenSet()) + " hidden", v -> showAppPicker(false)));
        content.addView(cycleRow("HIDDEN-area theme", hiddenThemeLabel(), v -> {
            prefs.edit().putString("hidden_theme", nextHiddenTheme()).apply();
            showSettings();
        }));
        content.addView(actionRow("Review notifications", "Android settings", v -> showNotificationReview()));

        addSectionTitle(content, "WEATHER");
        TextView note = text("HIDDEN uses only the place you type here. It never asks Android for your location.", 14, muted);
        note.setLineSpacing(0, 1.2f);
        pad(note, 0, 0, 0, 10);
        content.addView(note);

        EditText city = new EditText(this);
        city.setSingleLine(true);
        city.setHint("Adelaide, South Australia");
        city.setText(prefs.getString("weather_city", ""));
        city.setTextColor(fg);
        city.setHintTextColor(muted);
        city.setTextSize(16);
        city.setBackgroundColor(panel);
        pad(city, 12, 8, 12, 8);
        content.addView(city, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView weatherResult = text("", 13, muted);
        pad(weatherResult, 0, 8, 0, 2);
        content.addView(weatherResult);

        TextView saveTest = boldAction("Save + test weather");
        saveTest.setOnClickListener(v -> {
            String place = city.getText().toString().trim();
            prefs.edit().putString("weather_city", place).apply();
            weatherResult.setText("Testing…");
            if (place.isEmpty()) {
                weatherResult.setText("Enter a city first.");
                return;
            }
            fetchWeatherValue(place, (ok, value) -> weatherResult.setText(ok ? "Working · " + value : value));
        });
        content.addView(saveTest);

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

        TextView internet = text("Internet is used only when Weather is enabled.", 13, muted);
        pad(internet, 0, 8, 0, 14);
        content.addView(internet);

        content.addView(actionRow("Replay welcome", "intro + setup", v -> showIntroWelcome()));
        content.addView(actionRow("Default Home app", isDefaultHome() ? "HIDDEN" : "change", v -> requestHomeRole()));

        TextView version = text("HIDDEN · v0.3.1", 12, muted);
        pad(version, 0, 26, 0, 0);
        content.addView(version);

        setContentView(scroll);
    }

    private TextView boldAction(String label) {
        TextView t = text(label, 16, fg);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        pad(t, 0, 12, 0, 12);
        return t;
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView t = text(title, 12, muted);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        pad(t, 0, 30, 0, 8);
        parent.addView(t);
    }

    private View modeRow(String label, String key) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(modeLabel(prefs.getString(key, MODE_OFF)), 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(50), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(110), dp(50)));
        row.setOnClickListener(v -> {
            prefs.edit().putString(key, nextMode(prefs.getString(key, MODE_OFF))).apply();
            showSettings();
        });
        return row;
    }

    private View toggleRow(String label, String key) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(prefs.getBoolean(key, true) ? "ON" : "OFF", 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(50), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(90), dp(50)));
        row.setOnClickListener(v -> {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, true)).apply();
            showSettings();
        });
        return row;
    }

    private View cycleRow(String label, String value, View.OnClickListener listener) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(value, 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(50), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(160), dp(50)));
        row.setOnClickListener(listener);
        return row;
    }

    private View actionRow(String label, String value, View.OnClickListener listener) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(value, 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(50), 1f));
        row.addView(right, new LinearLayout.LayoutParams(dp(160), dp(50)));
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

    private void showIntroWelcome() {
        currentScreen = Screen.INTRO;
        applyPalette();

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(bg);

        IntroAnimationView animation = new IntroAnimationView(this);
        frame.addView(animation, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.BOTTOM);
        pad(welcome, 28, 34, 28, 48);
        welcome.setAlpha(0f);

        TextView brand = text("HIDDEN", 12, muted);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        welcome.addView(brand);

        TextView title = heading("Welcome to HIDDEN Launcher", 36);
        pad(title, 0, 10, 0, 10);
        welcome.addView(title);

        TextView copy = text("Choose what stays close. Hide the apps that steal your time. Everything else gets out of the way.", 16, muted);
        copy.setLineSpacing(0, 1.25f);
        welcome.addView(copy);

        TextView start = heading("Get started  →", 18);
        start.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        pad(start, 0, 28, 0, 8);
        start.setOnClickListener(v -> showOnboarding(0));
        welcome.addView(start);

        frame.addView(welcome, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);

        animation.start(() -> welcome.animate().alpha(1f).setDuration(500).start());
    }

    private void showOnboarding(int page) {
        onboardingPage = page;
        currentScreen = Screen.ONBOARDING;
        applyPalette();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setMinimumHeight(availableHeight());
        pad(outer, 28, 42, 28, 26);
        scroll.addView(outer);

        TextView brand = text("HIDDEN", 12, muted);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        outer.addView(brand);

        if (page == 0) onboardingOne(outer);
        else if (page == 1) onboardingTwo(outer);
        else onboardingThree(outer);

        View stretch = new View(this);
        outer.addView(stretch, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = text("PAGE " + (page + 1) + " / 3", 12, muted);
        TextView next = heading(page == 2 ? "Use HIDDEN" : "Next  →", 18);
        next.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.addView(count, new LinearLayout.LayoutParams(0, dp(56), 1f));
        footer.addView(next, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));
        outer.addView(footer);

        next.setOnClickListener(v -> {
            if (page < 2) showOnboarding(page + 1);
            else {
                prefs.edit().putInt("onboarding_version", ONBOARDING_VERSION).apply();
                if (!isDefaultHome()) requestHomeRole();
                showLauncherSurface(true);
            }
        });

        setContentView(scroll);
    }

    private void onboardingOne(LinearLayout content) {
        TextView title = heading("What should your phone show you?", 34);
        pad(title, 0, 14, 0, 8);
        content.addView(title);

        TextView copy = text("Put each utility on Home, in the app drawer, in both places, or nowhere. Your phone can be almost empty if you want.", 16, muted);
        copy.setLineSpacing(0, 1.25f);
        content.addView(copy);

        UtilityPreviewView preview = new UtilityPreviewView(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
        pp.topMargin = dp(18);
        content.addView(preview, pp);

        addSectionTitle(content, "UTILITIES");
        content.addView(onboardingModeRow("Clock", "clock_mode"));
        content.addView(onboardingModeRow("Weather", "weather_mode"));
        content.addView(onboardingModeRow("Battery", "battery_mode"));

        if (!MODE_OFF.equals(prefs.getString("weather_mode", MODE_OFF))) {
            EditText city = new EditText(this);
            city.setSingleLine(true);
            city.setHint("Weather city · Adelaide, South Australia");
            city.setText(prefs.getString("weather_city", ""));
            city.setTextColor(fg);
            city.setHintTextColor(muted);
            city.setTextSize(16);
            city.setBackgroundColor(panel);
            pad(city, 12, 8, 12, 8);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            cp.topMargin = dp(14);
            content.addView(city, cp);

            TextView save = boldAction("Save weather city");
            save.setOnClickListener(v -> {
                prefs.edit().putString("weather_city", city.getText().toString().trim()).apply();
                save.setText("Saved");
            });
            content.addView(save);
        }
    }

    private View onboardingModeRow(String label, String key) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        pad(block, 0, 4, 0, 10);

        TextView name = heading(label, 18);
        block.addView(name);

        LinearLayout options = new LinearLayout(this);
        options.setGravity(Gravity.CENTER_VERTICAL);

        String current = prefs.getString(key, MODE_OFF);
        String[] values = {MODE_HOME, MODE_DRAWER, MODE_BOTH, MODE_OFF};
        String[] labels = {"HOME", "DRAWER", "BOTH", "NONE"};

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            TextView option = text(labels[i], 12, value.equals(current) ? fg : muted);
            option.setTypeface(Typeface.create("sans-serif", value.equals(current) ? Typeface.BOLD : Typeface.NORMAL));
            option.setGravity(Gravity.CENTER);
            option.setBackgroundColor(value.equals(current) ? panel : Color.TRANSPARENT);
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) op.leftMargin = dp(4);
            options.addView(option, op);
            option.setOnClickListener(v -> {
                prefs.edit().putString(key, value).apply();
                showOnboarding(0);
            });
        }

        block.addView(options);
        return block;
    }

    private void onboardingTwo(LinearLayout content) {
        TextView title = heading("Choose what stays close.", 34);
        pad(title, 0, 14, 0, 8);
        content.addView(title);

        TextView copy = text("Favourite apps stay near your thumb. HIDDEN apps stay out of sight. Less is more. Especially time.", 16, muted);
        copy.setLineSpacing(0, 1.25f);
        content.addView(copy);

        TextView fav = pickerCard("Favourite apps", selectedCount(essentialSet()) + " selected");
        fav.setOnClickListener(v -> showAppPicker(true));
        content.addView(fav);

        TextView hidden = pickerCard("HIDDEN apps", selectedCount(hiddenSet()) + " hidden");
        hidden.setOnClickListener(v -> showAppPicker(false));
        content.addView(hidden);

        if (!hiddenSet().isEmpty()) {
            TextView noteTitle = heading("One more thing.", 17);
            pad(noteTitle, 0, 20, 0, 4);
            content.addView(noteTitle);

            TextView note = text("Their notifications can still come looking for you. Consider turning those notifications off yourself. HIDDEN will never request notification access.", 14, muted);
            note.setLineSpacing(0, 1.2f);
            content.addView(note);

            TextView review = boldAction("Review hidden-app notifications");
            review.setOnClickListener(v -> showNotificationReview());
            content.addView(review);
        }

        addSectionTitle(content, "DOOM SCROLL");
        TextView q = heading("How far away should distractions be?", 17);
        content.addView(q);

        TextView doomLabel = text(doomDistanceLabel(prefs.getInt("doom_screens", 18)), 14, muted);
        pad(doomLabel, 0, 5, 0, 2);
        content.addView(doomLabel);
        content.addView(doomSeekBar(doomLabel));
    }

    private TextView pickerCard(String title, String subtitle) {
        TextView t = heading(title + "\n" + subtitle, 20);
        t.setLineSpacing(dp(5), 1f);
        t.setBackgroundColor(panel);
        pad(t, 16, 16, 16, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84));
        lp.topMargin = dp(16);
        t.setLayoutParams(lp);
        return t;
    }

    private void onboardingThree(LinearLayout content) {
        TextView title = heading("Keep the time thieves out of reach.", 34);
        pad(title, 0, 14, 0, 8);
        content.addView(title);

        TextView copy = text("Pick the look, keep the swipe hint if you want it, then make HIDDEN your Home app.", 16, muted);
        copy.setLineSpacing(0, 1.25f);
        content.addView(copy);

        addSectionTitle(content, "APPEARANCE");
        content.addView(cycleRow("Launcher theme", themeLabel(), v -> {
            prefs.edit().putString("theme_mode", nextTheme()).apply();
            applyPalette();
            showOnboarding(2);
        }));

        content.addView(cycleRow("HIDDEN-area theme", hiddenThemeLabel(), v -> {
            prefs.edit().putString("hidden_theme", nextHiddenTheme()).apply();
            showOnboarding(2);
        }));

        content.addView(toggleRow("Swipe-up hint", "show_swipe_hint"));

        addSectionTitle(content, "PRIVACY");
        TextView privacy = text("No account. No analytics. No ads. No tracking. No location access. No notification access. Your settings stay on this phone.", 14, fg);
        privacy.setLineSpacing(0, 1.25f);
        content.addView(privacy);

        TextView weather = text("Weather is optional. If enabled, HIDDEN sends only the place name you typed to the weather provider.", 13, muted);
        weather.setLineSpacing(0, 1.25f);
        pad(weather, 0, 10, 0, 18);
        content.addView(weather);

        TextView homeStatus = boldAction(isDefaultHome() ? "HIDDEN is already your Home app" : "Choose HIDDEN as your Home app");
        homeStatus.setOnClickListener(v -> requestHomeRole());
        content.addView(homeStatus);
    }

    private void showAppPicker(boolean favourites) {
        currentScreen = Screen.PICKER;
        applyPalette();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        pad(root, 26, 42, 26, 16);

        TextView title = heading(favourites ? "Favourite apps" : "Apps to hide", 32);
        root.addView(title);

        TextView done = heading("Done", 17);
        done.setGravity(Gravity.END);
        pad(done, 0, 6, 0, 12);
        done.setOnClickListener(v -> {
            if (prefs.getInt("onboarding_version", 0) < ONBOARDING_VERSION) showOnboarding(1);
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
            TextView name = text(app.label, 19, fg);
            TextView mark = text(selected.contains(app.pkg) ? "●" : "○", 20, muted);
            mark.setGravity(Gravity.CENTER);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(58), 1f));
            row.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(58)));

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
        pad(content, 26, 42, 26, 40);
        scroll.addView(content);

        TextView title = heading("HIDDEN app notifications", 30);
        content.addView(title);

        TextView copy = text("HIDDEN won't read or control your notifications. Tap an app below to open Android's own notification settings.", 14, muted);
        copy.setLineSpacing(0, 1.2f);
        pad(copy, 0, 8, 0, 16);
        content.addView(copy);

        Set<String> hidden = hiddenSet();
        for (AppItem app : apps) {
            if (!hidden.contains(app.pkg)) continue;
            TextView row = text(app.label, 18, fg);
            pad(row, 0, 10, 0, 10);
            row.setOnClickListener(v -> openNotificationSettings(app.pkg));
            content.addView(row);
            content.addView(divider());
        }

        TextView back = boldAction("‹ Back");
        back.setOnClickListener(v -> {
            if (prefs.getInt("onboarding_version", 0) < ONBOARDING_VERSION) showOnboarding(1);
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

    private interface WeatherCallback {
        void onResult(boolean ok, String value);
    }

    private void fetchWeatherValue(String city, WeatherCallback callback) {
        new Thread(() -> {
            boolean ok = false;
            String result;
            try {
                JSONObject place = geocode(city);
                double lat = place.getDouble("latitude");
                double lon = place.getDouble("longitude");
                String name = place.optString("name", city);
                String admin = place.optString("admin1", "");
                String label = admin.isEmpty() || admin.equalsIgnoreCase(name) ? name : name + ", " + admin;

                JSONObject forecast = getJson("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,weather_code&timezone=auto&forecast_days=1");
                JSONObject current = forecast.getJSONObject("current");
                int temp = (int)Math.round(current.getDouble("temperature_2m"));
                int code = current.optInt("weather_code", -1);

                result = label + " · " + temp + "° · " + weatherLabel(code);
                ok = true;
                prefs.edit().putString("last_weather", result).putLong("last_weather_at", System.currentTimeMillis()).apply();
            } catch (Exception e) {
                result = city + " · weather unavailable";
            }

            boolean finalOk = ok;
            String finalResult = result;
            runOnUiThread(() -> callback.onResult(finalOk, finalResult));
        }).start();
    }

    private JSONObject geocode(String city) throws Exception {
        String q = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8.toString());
        JSONObject geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=5&language=en&format=json");
        JSONArray results = geo.optJSONArray("results");

        if (results == null || results.length() == 0) {
            String fallback = city.split(",")[0].trim();
            q = URLEncoder.encode(fallback, StandardCharsets.UTF_8.toString());
            geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=5&language=en&format=json");
            results = geo.optJSONArray("results");
        }

        if (results == null || results.length() == 0) throw new Exception("place not found");
        return results.getJSONObject(0);
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "HiddenLauncher/0.3.1");

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

    private String join(List<String> parts, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) b.append(sep);
            b.append(parts.get(i));
        }
        return b.toString();
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
            float cy = getHeight() / 2f + dp(4);
            canvas.drawLine(cx - dp(9), cy + dp(4), cx, cy - dp(5), p);
            canvas.drawLine(cx, cy - dp(5), cx + dp(9), cy + dp(4), p);
        }
    }

    class UtilityPreviewView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        UtilityPreviewView(Context c) { super(c); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            int gap = dp(14);
            int cardW = (w - gap) / 2;

            drawMiniPhone(canvas, 0, 0, cardW, h, true);
            drawMiniPhone(canvas, cardW + gap, 0, cardW, h, false);
        }

        private void drawMiniPhone(Canvas c, int x, int y, int w, int h, boolean home) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(panel);
            c.drawRoundRect(new RectF(x, y, x + w, y + h), dp(18), dp(18), p);

            p.setColor(fg);
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            p.setTextSize(dp(11));
            c.drawText(home ? "HOME" : "DRAWER", x + dp(14), y + dp(20), p);

            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(dp(9));
            p.setColor(muted);

            if (home) {
                c.drawText("4:38", x + dp(14), y + dp(48), p);
                c.drawText("Adelaide · 18°", x + dp(14), y + dp(66), p);
                c.drawText("Battery · 72%", x + dp(14), y + dp(84), p);
                c.drawText("Phone", x + dp(14), y + h - dp(44), p);
                c.drawText("Camera", x + dp(14), y + h - dp(26), p);
            } else {
                c.drawText("HIDDEN Settings", x + dp(14), y + dp(48), p);
                c.drawText("4:38 · Battery 72%", x + dp(14), y + dp(66), p);
                c.drawText("Calculator", x + dp(14), y + dp(94), p);
                c.drawText("Camera", x + dp(14), y + dp(112), p);
                c.drawText("Maps", x + dp(14), y + dp(130), p);
            }
        }
    }

    class IntroAnimationView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float progress = 0f;
        final Random random = new Random(1337L);

        IntroAnimationView(Context c) { super(c); }

        void start(Runnable done) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(3300);
            a.addUpdateListener(v -> {
                progress = (float)v.getAnimatedValue();
                invalidate();
            });
            a.start();
            handler.postDelayed(done, 2800);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(bg);

            float phoneW = getWidth() * 0.58f;
            float phoneH = phoneW * 1.85f;
            float left = (getWidth() - phoneW) / 2f;
            float top = getHeight() * 0.15f;
            RectF phone = new RectF(left, top, left + phoneW, top + phoneH);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(3));
            p.setColor(fg);
            c.drawRoundRect(phone, dp(30), dp(30), p);

            RectF screen = new RectF(phone.left + dp(10), phone.top + dp(18), phone.right - dp(10), phone.bottom - dp(18));
            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            c.drawRoundRect(screen, dp(22), dp(22), p);

            float explode = clamp((progress - 0.18f) / 0.45f);
            int cols = 4;
            int rows = 5;
            float icon = phoneW * 0.13f;
            float xGap = (screen.width() - cols * icon) / (cols + 1);
            float yGap = dp(22);

            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < cols; col++) {
                    int idx = r * cols + col;
                    float delay = idx / 26f;
                    float local = clamp((explode - delay) * 2.2f);
                    if (local >= 1f) continue;

                    float x = screen.left + xGap + col * (icon + xGap);
                    float y = screen.top + dp(42) + r * (icon + yGap);

                    p.setColor(fg);
                    p.setAlpha((int)(255 * (1f - local)));
                    c.drawRect(x, y, x + icon, y + icon, p);

                    if (local > 0f) {
                        random.setSeed(1000L + idx);
                        for (int b = 0; b < 7; b++) {
                            float ox = (random.nextFloat() - 0.5f) * dp(90) * local;
                            float oy = (random.nextFloat() - 0.5f) * dp(90) * local;
                            float s = dp(3 + random.nextInt(4));
                            c.drawRect(x + icon/2 + ox, y + icon/2 + oy, x + icon/2 + ox + s, y + icon/2 + oy + s, p);
                        }
                    }
                }
            }

            p.setAlpha(255);

            float reveal = clamp((progress - 0.64f) / 0.26f);
            if (reveal > 0f) {
                float rise = dp(70) * (1f - reveal);
                p.setColor(fg);
                p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                p.setTextSize(dp(24));
                c.drawText("4:38", screen.left + dp(20), screen.top + dp(56) + rise, p);

                p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                p.setTextSize(dp(10));
                p.setColor(muted);
                c.drawText("Thursday, 24 September", screen.left + dp(20), screen.top + dp(76) + rise, p);
                c.drawText("Battery · 72%", screen.left + dp(20), screen.top + dp(100) + rise, p);

                p.setColor(fg);
                p.setTextSize(dp(12));
                c.drawText("Phone", screen.left + dp(20), screen.bottom - dp(82) + rise, p);
                c.drawText("Messages", screen.left + dp(20), screen.bottom - dp(58) + rise, p);
                c.drawText("Camera", screen.left + dp(20), screen.bottom - dp(34) + rise, p);
            }

            if (progress > 0.82f) {
                int alpha = (int)(255 * clamp((progress - 0.82f) / 0.18f));
                p.setColor(Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg)));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
            }
        }

        private float clamp(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }

    class AlphabetRailView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private char active = 0;
        interface Listener { void onLetter(char letter); }
        private Listener listener;

        AlphabetRailView(Context context) { super(context); setClickable(true); }
        void setListener(Listener l) { listener = l; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            paint.setColor(muted);

            float step = getHeight() / (float)letters.length();
            for (int i = 0; i < letters.length(); i++) {
                float y = step * i + step * 0.7f;
                canvas.drawText(String.valueOf(letters.charAt(i)), getWidth() - dp(13), y, paint);
            }

            if (active != 0) {
                paint.setTextSize(dp(36));
                paint.setColor(fg);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(String.valueOf(active), getWidth() - dp(50), getHeight() / 2f, paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (listener == null) return false;

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
            int starStart = (int)(height * 0.78f);
            int count = Math.max(80, (height - starStart) / Math.max(1, getResources().getDisplayMetrics().heightPixels) * 42);
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
                ? new int[]{bg, Color.rgb(84, 69, 92), Color.rgb(132, 82, 92), Color.rgb(171, 104, 85), Color.rgb(113, 76, 99), Color.rgb(51, 50, 69), Color.rgb(7, 8, 11)}
                : new int[]{bg, Color.rgb(224, 205, 159), Color.rgb(213, 164, 123), Color.rgb(190, 126, 112), Color.rgb(157, 111, 131), Color.rgb(101, 88, 113), Color.rgb(48, 49, 67), Color.rgb(7, 8, 11)};

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

    class LauncherAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HOME = 1;
        private static final int TYPE_HEADER = 2;
        private static final int TYPE_APP = 3;
        private static final int TYPE_JOURNEY = 4;
        private static final int TYPE_HIDDEN_HEADER = 5;
        private static final int TYPE_HIDDEN_APP = 6;
        private static final int TYPE_REWIND = 7;

        private final List<Object> items = new ArrayList<>();
        private final Map<Character,Integer> letterPositions = new HashMap<>();
        private String query = "";
        private int journeyPos = -1;
        private int hiddenHeaderPos = -1;
        private int firstAppPos = 2;
        private boolean showRepeatPause = false;

        LauncherAdapter() {
            setHasStableIds(true);
            rebuild();
        }

        @Override public long getItemId(int position) {
            Object item = items.get(position);
            if ("HOME".equals(item)) return -1001;
            if ("HEADER".equals(item)) return -1002;
            if ("JOURNEY".equals(item)) return -1003;
            if ("HIDDEN_HEADER".equals(item)) return -1004;
            if ("REWIND".equals(item)) return -1005;
            if (item instanceof AppItem) return ((AppItem)item).pkg.hashCode();
            if (item instanceof HiddenApp) return -2000000000L + ((HiddenApp)item).app.pkg.hashCode();
            return position;
        }

        void setQuery(String value) {
            String next = value == null ? "" : value.trim();
            if (next.equals(query)) return;
            query = next;
            rebuild();
            notifyDataSetChanged();
        }

        int firstAppPosition() { return firstAppPos; }
        int journeyPosition() { return journeyPos; }
        int hiddenHeaderPosition() { return hiddenHeaderPos; }

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

        private void rebuild() {
            items.clear();
            letterPositions.clear();
            journeyPos = -1;
            hiddenHeaderPos = -1;

            items.add("HOME");
            items.add("HEADER");
            firstAppPos = 2;

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

            items.add("REWIND");
        }

        @Override public int getItemViewType(int position) {
            Object item = items.get(position);
            if ("HOME".equals(item)) return TYPE_HOME;
            if ("HEADER".equals(item)) return TYPE_HEADER;
            if ("JOURNEY".equals(item)) return TYPE_JOURNEY;
            if ("HIDDEN_HEADER".equals(item)) return TYPE_HIDDEN_HEADER;
            if ("REWIND".equals(item)) return TYPE_REWIND;
            if (item instanceof HiddenApp) return TYPE_HIDDEN_APP;
            return TYPE_APP;
        }

        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == TYPE_HOME) return new SimpleHolder(buildHomePanel());
            if (type == TYPE_HEADER) return new SimpleHolder(buildDrawerHeader());

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

            if (type == TYPE_REWIND) {
                TextView rewind = heading("REWIND  ↑", 18);
                rewind.setGravity(Gravity.CENTER);
                pad(rewind, 20, 26, 20, 34);
                return new SimpleHolder(rewind);
            }

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            pad(row, 26, 0, 58, 0);

            TextView label = text("", 18, fg);
            label.setSingleLine(true);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setIncludeFontPadding(false);
            row.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
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
                h.root.setOnLongClickListener(v -> { confirmHide(app); return true; });
                return;
            }

            if (type == TYPE_HIDDEN_APP) {
                HiddenApp wrap = (HiddenApp)items.get(position);
                AppHolder h = (AppHolder)holder;
                applyHiddenRowTheme(h, wrap.app);
                h.root.setOnClickListener(v -> launch(wrap.app));
                h.root.setOnLongClickListener(v -> { confirmRestore(wrap.app); return true; });
                return;
            }

            if (type == TYPE_HIDDEN_HEADER) {
                LinearLayout block = (LinearLayout)holder.itemView;
                block.removeAllViews();
                HiddenPalette hp = hiddenPalette();
                block.setBackgroundColor(hp.background);

                TextView title = hiddenHeading("doom".equals(prefs.getString("hidden_theme", "dark")) ? "DOOM SCROLL" : "HIDDEN", 36, hp.foreground);
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

                    TextView action = hiddenHeading("Pause for 30 minutes", 15, hp.foreground);
                    pad(action, 0, 4, 0, 2);
                    action.setOnClickListener(v -> {
                        prefs.edit().putLong("doom_pause_until", System.currentTimeMillis() + HALF_HOUR).apply();
                        rebuild();
                        notifyDataSetChanged();
                    });
                    block.addView(action);
                }
                return;
            }

            if (type == TYPE_REWIND) {
                TextView rewind = (TextView)holder.itemView;
                HiddenPalette hp = hiddenPalette();
                rewind.setTextColor(hp.foreground);
                rewind.setBackgroundColor(hp.background);
                rewind.setOnClickListener(v -> rewindHome(true));
            }
        }

        @Override public int getItemCount() { return items.size(); }

        private TextView hiddenHeading(String value, float sp, int color) {
            TextView t = text(value, sp, color);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            return t;
        }

        private void confirmHide(AppItem app) {
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

        private void confirmRestore(AppItem app) {
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

        private void applyHiddenRowTheme(AppHolder h, AppItem app) {
            HiddenPalette hp = hiddenPalette();
            h.root.setBackgroundColor(hp.background);
            h.label.setTextColor(hp.foreground);

            String theme = prefs.getString("hidden_theme", "dark");
            if ("8bit".equals(theme)) {
                h.label.setTypeface(Typeface.MONOSPACE);
                h.label.setText(glitchLabel(app.label));
            } else if ("doom".equals(theme)) {
                h.label.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                h.label.setText(earthyDoomText(app.label.toUpperCase(Locale.ROOT)));
            } else {
                h.label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                h.label.setText(app.label);
            }
        }

        private CharSequence earthyDoomText(String value) {
            int[] colors = {
                Color.rgb(205, 146, 129),
                Color.rgb(168, 174, 137),
                Color.rgb(202, 171, 112),
                Color.rgb(143, 139, 154),
                Color.rgb(190, 154, 146),
                Color.rgb(151, 168, 157)
            };

            SpannableString s = new SpannableString(value);
            for (int i = 0; i < value.length(); i++) {
                if (Character.isWhitespace(value.charAt(i))) continue;
                s.setSpan(new ForegroundColorSpan(colors[i % colors.length]), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return s;
        }

        private String glitchLabel(String label) {
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
        if ("doom".equals(theme)) return new HiddenPalette(Color.rgb(10, 8, 8), Color.rgb(218, 176, 159), Color.rgb(143, 115, 105));
        return new HiddenPalette(Color.rgb(8, 9, 12), Color.rgb(241, 241, 238), Color.rgb(136, 138, 145));
    }
}
