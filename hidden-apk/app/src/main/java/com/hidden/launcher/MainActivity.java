package com.hidden.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Build;
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
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


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
    private static final int ONBOARDING_VERSION = 7;

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
    private LinearLayout drawerShelf;
    private boolean drawerShelfTargetVisible = false;
    private EditText stickyDrawerSearch;
    private EditText flowDrawerSearch;
    private boolean syncingDrawerSearch = false;
    private boolean drawerSearchActive = false;

    private TextView homeClockView;
    private TextView homeDateView;
    private TextView homeBatteryView;
    private final List<TextView> drawerClockViews = new ArrayList<>();
    private final List<TextView> drawerBatteryViews = new ArrayList<>();
    private TextView defaultHomeStatusView;
    private TextView favouriteCountView;
    private TextView hiddenCountView;
    private boolean batteryReceiverRegistered = false;
    private boolean packageReceiverRegistered = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateLiveUtilities();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            reloadAppsAndSelections();
        }
    };

    private final Runnable uiTicker = new Runnable() {
        @Override public void run() {
            updateLiveUtilities();
            long now = System.currentTimeMillis();
            long delay = 60000L - (now % 60000L) + 80L;
            handler.postDelayed(this, delay);
        }
    };

    private enum Screen { HOME, SETTINGS, THEME_PICKER, ONBOARDING, PICKER, NOTIFICATION_REVIEW, INTRO }

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
        ensureDefaults();
        apps = loadApps();
        pruneSelectionSets();
        applyPalette();

        if (prefs.getInt("onboarding_version", 0) < ONBOARDING_VERSION) {
            showIntroWelcome();
        } else {
            showLauncherSurface(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        reloadAppsAndSelections();
        refreshExternalStateViews();

        if (!batteryReceiverRegistered) {
            IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(batteryReceiver, batteryFilter);
            }
            batteryReceiverRegistered = true;
        }

        if (!packageReceiverRegistered) {
            IntentFilter packageFilter = new IntentFilter();
            packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            packageFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            packageFilter.addDataScheme("package");

            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(packageReceiver, packageFilter);
            }
            packageReceiverRegistered = true;
        }

        handler.removeCallbacks(uiTicker);
        uiTicker.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(uiTicker);

        if (batteryReceiverRegistered) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
            batteryReceiverRegistered = false;
        }

        if (packageReceiverRegistered) {
            try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
            packageReceiverRegistered = false;
        }

        super.onPause();
    }

    private void reloadAppsAndSelections() {
        List<AppItem> refreshed = loadApps();
        boolean changed = !sameAppList(apps, refreshed);
        apps = refreshed;
        boolean selectionsChanged = pruneSelectionSets();

        if (changed && launcherAdapter != null) launcherAdapter.refreshApps();
        if (changed || selectionsChanged) refreshExternalStateViews();
    }

    private boolean sameAppList(List<AppItem> a, List<AppItem> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            AppItem x = a.get(i);
            AppItem y = b.get(i);
            if (!x.pkg.equals(y.pkg) || !x.activity.equals(y.activity) || !x.label.equals(y.label)) return false;
        }
        return true;
    }

    private boolean pruneSelectionSets() {
        if (prefs == null) return false;

        Set<String> hidden = hiddenSet();
        Set<String> essential = essentialSet();

        boolean hiddenChanged = hidden.removeIf(pkg -> !isPackageInstalled(pkg));
        boolean essentialChanged = essential.removeIf(pkg -> !isPackageInstalled(pkg));
        boolean changed = hiddenChanged || essentialChanged;

        if (changed) {
            SharedPreferences.Editor e = prefs.edit();
            e.putStringSet("hidden_packages", new HashSet<>(hidden));
            e.putStringSet("essential_packages", new HashSet<>(essential));
            e.apply();
        }
        return changed;
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void refreshExternalStateViews() {
        if (defaultHomeStatusView != null) {
            defaultHomeStatusView.setText(isDefaultHome() ? "HIDDEN" : "CHANGE");
        }
        if (favouriteCountView != null) {
            favouriteCountView.setText(selectedCount(essentialSet()) + " SELECTED");
        }
        if (hiddenCountView != null) {
            hiddenCountView.setText(selectedCount(hiddenSet()) + " HIDDEN");
        }
    }

    private String currentTimeText() {
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
    }

    private void updateLiveUtilities() {
        String time = currentTimeText();
        String date = new SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(new Date());
        String battery = "BATTERY  " + batteryPercent() + "%";

        if (homeClockView != null) homeClockView.setText(time);
        if (homeDateView != null) homeDateView.setText(date);
        if (homeBatteryView != null) homeBatteryView.setText(battery);

        for (TextView clock : drawerClockViews) {
            if (clock != null) clock.setText(time);
        }
        for (TextView batteryView : drawerBatteryViews) {
            if (batteryView != null) batteryView.setText(battery);
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
            if (isDrawerSearchFocused()) {
                hideKeyboardKeepSearch();
                return;
            }

            if (launcherAdapter != null && launcherAdapter.isSearchMode()) {
                resetDrawerSearch();
                rewindHome(true);
                return;
            }

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

        if (currentScreen == Screen.THEME_PICKER) {
            showSettings();
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
        String legacy = prefs.getString(
            "theme_mode",
            prefs.getString("drawer_theme", prefs.getString("home_theme", "dark"))
        );
        String canonical = normaliseTheme(legacy);

        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains("clock_mode")) e.putString("clock_mode", MODE_HOME);
        if (!prefs.contains("battery_mode")) e.putString("battery_mode", MODE_HOME);
        if (!prefs.contains("show_swipe_hint")) e.putBoolean("show_swipe_hint", true);
        if (!prefs.contains("doom_screens")) e.putInt("doom_screens", 18);

        e.putString("theme_mode", canonical)
            .remove("home_theme")
            .remove("drawer_theme")
            .remove("hidden_theme")
            .apply();
    }

    private String normaliseTheme(String raw) {
        if (raw == null) return "dark";
        if ("doom".equals(raw)) return "doom_dark";
        if ("grayscale".equals(raw)) return "dark";
        if ("system".equals(raw)) return ThemeArt.resolve("system", getResources());
        if ("light".equals(raw) || "dark".equals(raw) || "oled".equals(raw) ||
            "doom_light".equals(raw) || "doom_dark".equals(raw) ||
            "soft_launch".equals(raw) || "moth".equals(raw) || "8bit".equals(raw)) {
            return raw;
        }
        return "dark";
    }

    private String currentTheme() {
        return normaliseTheme(prefs.getString("theme_mode", "dark"));
    }

    private boolean isTheme(String name) {
        return name.equals(currentTheme());
    }

    private boolean isDoomTheme() {
        return "doom_light".equals(currentTheme()) || "doom_dark".equals(currentTheme());
    }

    private boolean useDarkPalette() {
        String mode = currentTheme();
        return "dark".equals(mode) || "oled".equals(mode) ||
            "doom_dark".equals(mode) || "moth".equals(mode) || "8bit".equals(mode);
    }

    private void setPaletteValues(String rawTheme) {
        String mode = normaliseTheme(rawTheme);
        bg = ThemeArt.background(mode, getResources());
        fg = ThemeArt.foreground(mode, getResources());
        muted = ThemeArt.muted(mode, getResources());
        panel = ThemeArt.panel(mode, getResources());
        line = ThemeArt.line(mode, getResources());
    }

    private void applyPalette() {
        String mode = currentTheme();
        setPaletteValues(mode);

        boolean lightBars = "light".equals(mode) || "doom_light".equals(mode) || "soft_launch".equals(mode);
        int systemUi = lightBars
            ? (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
            : 0;
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        getWindow().getDecorView().setBackgroundColor(bg);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int drawerShelfHeight() {
        float extraScale = Math.max(0f, getResources().getConfiguration().fontScale - 1f);
        return dp(Math.round(148f + extraScale * 58f));
    }

    private int drawerInfoHeight() {
        float extraScale = Math.max(0f, getResources().getConfiguration().fontScale - 1f);
        return dp(Math.round(34f + extraScale * 15f));
    }

    private int drawerSearchHeight() {
        float extraScale = Math.max(0f, getResources().getConfiguration().fontScale - 1f);
        return dp(Math.round(46f + extraScale * 18f));
    }

    private int drawerSettingsHeight() {
        float extraScale = Math.max(0f, getResources().getConfiguration().fontScale - 1f);
        return dp(Math.round(42f + extraScale * 18f));
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

    private int[] doomAccentColors() {
        return isTheme("doom_light")
            ? new int[]{
                Color.rgb(92, 43, 37),
                Color.rgb(54, 78, 49),
                Color.rgb(105, 72, 27),
                Color.rgb(55, 61, 91),
                Color.rgb(92, 49, 67),
                Color.rgb(43, 77, 67)
            }
            : new int[]{
                Color.rgb(239, 169, 146),
                Color.rgb(194, 211, 163),
                Color.rgb(239, 203, 123),
                Color.rgb(181, 176, 211),
                Color.rgb(229, 180, 188),
                Color.rgb(174, 209, 195)
            };
    }

    private CharSequence globalStyledText(String value) {
        if (!isDoomTheme()) return value;
        int[] colors = doomAccentColors();

        SpannableString styled = new SpannableString(value);
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) continue;
            styled.setSpan(
                new ForegroundColorSpan(colors[i % colors.length]),
                i,
                i + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return styled;
    }

    private void applyRegularTypeface(TextView t) {
        if (isTheme("8bit")) {
            t.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
            t.setLetterSpacing(0.045f);
            t.getPaint().setAntiAlias(false);
        } else if (isDoomTheme()) {
            t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            t.setLetterSpacing(0.035f);
        } else if (isTheme("soft_launch")) {
            t.setTypeface(Typeface.create("sans-serif-rounded", Typeface.NORMAL));
            t.setLetterSpacing(0.012f);
        } else {
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
    }

    private void applyStrongTypeface(TextView t) {
        if (isTheme("8bit")) {
            t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            t.setLetterSpacing(0.055f);
            t.getPaint().setAntiAlias(false);
        } else if (isDoomTheme()) {
            t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            t.setLetterSpacing(0.055f);
        } else if (isTheme("soft_launch")) {
            t.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
            t.setLetterSpacing(0.018f);
        } else if (isTheme("moth")) {
            t.setTypeface(Typeface.create("serif", Typeface.BOLD));
            t.setLetterSpacing(0.018f);
        } else {
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.setFontFeatureSettings("kern");
        applyRegularTypeface(t);
        return t;
    }

    private TextView heading(String value, float sp) {
        TextView t = text(value, sp, fg);
        if (isDoomTheme()) {
            t.setText(globalStyledText(value));
            int shadow = isTheme("doom_light")
                ? Color.argb(125, 255, 250, 238)
                : Color.argb(220, 0, 0, 0);
            t.setShadowLayer(1.8f, 0f, 1f, shadow);
        }
        applyStrongTypeface(t);
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
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        frame.requestApplyInsets();

        launcherRecycler = new RecyclerView(this);
        launcherRecycler.setLayoutManager(new LinearLayoutManager(this));
        launcherRecycler.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        launcherRecycler.setItemAnimator(null);
        launcherAdapter = new LauncherAdapter();
        launcherRecycler.setAdapter(launcherAdapter);
        launcherRecycler.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN &&
                launcherAdapter != null &&
                launcherAdapter.isSearchMode() &&
                isDrawerSearchFocused()) {
                hideKeyboardKeepSearch();
            }
            return false;
        });
        frame.addView(launcherRecycler, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawerSearchActive = false;
        homeClockView = null;
        homeDateView = null;
        homeBatteryView = null;
        drawerClockViews.clear();
        drawerBatteryViews.clear();
        stickyDrawerSearch = null;
        flowDrawerSearch = null;
        drawerShelf = buildDrawerShelf(false);
        drawerShelfTargetVisible = false;
        drawerShelf.setAlpha(0f);
        drawerShelf.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams shelfParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            drawerShelfHeight(),
            Gravity.TOP
        );
        frame.addView(drawerShelf, shelfParams);

        alphabetRail = new AlphabetRailView(this);
        alphabetRailTargetVisible = false;
        alphabetRail.setAlpha(0f);
        alphabetRail.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        rp.topMargin = drawerShelfHeight() + dp(10);
        rp.bottomMargin = dp(18);
        frame.addView(alphabetRail, rp);

        alphabetRail.setListener(letter -> {
            int pos = launcherAdapter.positionForLetter(letter);
            if (pos >= 0) launcherRecycler.smoothScrollToPosition(pos);
        });

        launcherRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            boolean insideHidden = false;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING &&
                    launcherAdapter != null &&
                    launcherAdapter.isSearchMode() &&
                    isDrawerSearchFocused()) {
                    hideKeyboardKeepSearch();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int first = lm.findFirstVisibleItemPosition();
                int last = lm.findLastVisibleItemPosition();
                int journey = launcherAdapter.journeyPosition();
                int hiddenStart = launcherAdapter.hiddenHeaderPosition();

                boolean beforeDoom = journey < 0 || last < journey;
                boolean showRail = !launcherAdapter.isSearchMode()
                    && launcherAdapter.query.isEmpty()
                    && first >= launcherAdapter.firstAppPosition()
                    && beforeDoom;

                int headerPos = launcherAdapter.headerPosition();
                View headerView = lm.findViewByPosition(headerPos);
                boolean headerReachedTop =
                    launcherAdapter.isSearchMode() ||
                    first > headerPos ||
                    (first == headerPos && headerView != null && headerView.getTop() <= 0);

                setAlphabetRailVisible(showRail);
                setDrawerShelfVisible((launcherAdapter.isSearchMode() || headerReachedTop) && beforeDoom);

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

    private AlertDialog.Builder dialogBuilder() {
        int dialogTheme = useDarkPalette()
            ? android.R.style.Theme_Material_Dialog_Alert
            : android.R.style.Theme_Material_Light_Dialog_Alert;
        return new AlertDialog.Builder(this, dialogTheme);
    }

    private void rewindHome(boolean smooth) {
        if (launcherRecycler == null) {
            showLauncherSurface(true);
            return;
        }

        if (launcherAdapter != null && launcherAdapter.isSearchMode()) {
            resetDrawerSearch();
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

    private void setDrawerShelfVisible(boolean visible) {
        if (drawerShelf == null || drawerShelfTargetVisible == visible) return;
        drawerShelfTargetVisible = visible;
        drawerShelf.animate().cancel();

        if (visible) {
            drawerShelf.setVisibility(View.VISIBLE);
            drawerShelf.setAlpha(0f);
            drawerShelf.animate().alpha(1f).setDuration(170).start();
        } else {
            drawerShelf.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> {
                    if (!drawerShelfTargetVisible && drawerShelf != null) {
                        drawerShelf.setVisibility(View.INVISIBLE);
                    }
                })
                .start();
        }
    }

    private LinearLayout buildHomePanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new ThemePatternDrawable(currentTheme(), 4815162342L));
        root.setMinimumHeight(availableHeight());
        pad(root, 28, 42, 28, 20);

        LinearLayout utilityBlock = new LinearLayout(this);
        utilityBlock.setOrientation(LinearLayout.VERTICAL);
        utilityBlock.setGravity(Gravity.CENTER_HORIZONTAL);

        boolean hasClock = modeOnHome("clock_mode");
        boolean hasBattery = modeOnHome("battery_mode");

        if (hasClock) {
            TextView time = heading(currentTimeText(), 68);
            homeClockView = time;
            time.setGravity(Gravity.CENTER);
            time.setIncludeFontPadding(false);
            utilityBlock.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

            TextView date = text(new SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(new Date()), 14, muted);
            homeDateView = date;
            date.setGravity(Gravity.CENTER);
            date.setLetterSpacing(0.035f);
            utilityBlock.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        }

        if (hasBattery) {
            TextView battery = text("BATTERY  " + batteryPercent() + "%", 12, muted);
            homeBatteryView = battery;
            battery.setGravity(Gravity.CENTER);
            applyStrongTypeface(battery);
            battery.setLetterSpacing(0.12f);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            bp.topMargin = dp(hasClock ? 5 : 0);
            utilityBlock.addView(battery, bp);
        }

        if (hasClock || hasBattery) {
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            up.topMargin = dp(46);
            root.addView(utilityBlock, up);
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
                applyStrongTypeface(row);
                pad(row, 0, 5, 0, 5);
                row.setOnClickListener(v -> launch(app));
                row.setMinimumHeight(dp(48));
                favourites.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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

    private LinearLayout buildDrawerShelf() {
        return buildDrawerShelf(false);
    }

    private LinearLayout buildDrawerShelf(boolean inFlow) {
        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.VERTICAL);
        shelf.setGravity(Gravity.CENTER_HORIZONTAL);
        shelf.setBackgroundColor(bg);
        pad(shelf, 24, 7, 24, 0);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER);

        if (modeInDrawer("clock_mode")) {
            TextView clock = text(currentTimeText(), 14, fg);
            drawerClockViews.add(clock);
            applyStrongTypeface(clock);
            clock.setGravity(Gravity.CENTER);
            clock.setIncludeFontPadding(false);
            info.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        }

        if (modeInDrawer("clock_mode") && modeInDrawer("battery_mode")) {
            TextView dot = text("  ·  ", 14, muted);
            dot.setGravity(Gravity.CENTER);
            info.addView(dot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        }

        if (modeInDrawer("battery_mode")) {
            TextView battery = text("BATTERY  " + batteryPercent() + "%", 14, muted);
            drawerBatteryViews.add(battery);
            applyStrongTypeface(battery);
            battery.setLetterSpacing(0.06f);
            battery.setGravity(Gravity.CENTER);
            info.addView(battery, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        }

        shelf.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, drawerInfoHeight()));

        EditText search = new EditText(this);
        search.setHint(isTheme("8bit") ? "SEARCH APPS_" : "Search apps");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(fg);
        search.setHintTextColor(muted);
        search.setHighlightColor(Color.argb(70, Color.red(fg), Color.green(fg), Color.blue(fg)));
        Drawable cursor = search.getTextCursorDrawable();
        if (cursor != null) {
            cursor = cursor.mutate();
            cursor.setTint(fg);
            search.setTextCursorDrawable(cursor);
        }
        search.setBackgroundColor(panel);
        if (isTheme("8bit")) {
            search.setTypeface(Typeface.MONOSPACE);
            search.setAllCaps(false);
            search.getPaint().setAntiAlias(false);
        } else if (isDoomTheme()) {
            search.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            search.setLetterSpacing(0.04f);
        }
        pad(search, 14, 8, 14, 8);
        search.setText(launcherAdapter == null ? "" : launcherAdapter.query);
        search.setSelection(search.length());

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            drawerSearchHeight()
        );
        searchParams.topMargin = dp(4);
        shelf.addView(search, searchParams);

        if (inFlow) flowDrawerSearch = search;
        else stickyDrawerSearch = search;

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                syncDrawerSearch(search, value.toString());
            }

            @Override public void afterTextChanged(Editable value) {}
        });

        search.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                enterDrawerSearch();

                if (inFlow && stickyDrawerSearch != null && stickyDrawerSearch != search) {
                    handler.post(() -> {
                        if (stickyDrawerSearch != null) {
                            stickyDrawerSearch.requestFocus();
                            stickyDrawerSearch.setSelection(stickyDrawerSearch.length());
                        }
                    });
                }
            } else {
                handler.postDelayed(this::maybeExitDrawerSearch, 140);
            }
        });

        TextView settings = text("HIDDEN SETTINGS", 12, muted);
        applyStrongTypeface(settings);
        settings.setGravity(Gravity.CENTER);
        settings.setLetterSpacing(0.12f);
        settings.setOnClickListener(v -> showSettings());
        shelf.addView(settings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, drawerSettingsHeight()));

        View divider = new View(this);
        divider.setBackgroundColor(line);
        shelf.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        return shelf;
    }

    private boolean isDrawerSearchFocused() {
        return (stickyDrawerSearch != null && stickyDrawerSearch.hasFocus()) ||
            (flowDrawerSearch != null && flowDrawerSearch.hasFocus());
    }

    private void hideKeyboardKeepSearch() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }

        if (stickyDrawerSearch != null) stickyDrawerSearch.clearFocus();
        if (flowDrawerSearch != null) flowDrawerSearch.clearFocus();

        if (launcherRecycler != null) launcherRecycler.requestFocus();
    }

    private void resetDrawerSearch() {
        hideKeyboardKeepSearch();
        drawerSearchActive = false;

        syncingDrawerSearch = true;
        if (stickyDrawerSearch != null) stickyDrawerSearch.setText("");
        if (flowDrawerSearch != null) flowDrawerSearch.setText("");
        syncingDrawerSearch = false;

        if (launcherAdapter != null) {
            launcherAdapter.clearSearchAndRestoreDrawer();
        }

        setAlphabetRailVisible(false);
        setDrawerShelfVisible(false);
    }

    private void enterDrawerSearch() {
        if (!drawerSearchActive) {
            drawerSearchActive = true;
            if (launcherAdapter != null) launcherAdapter.refreshForSearchMode();
        }
        setAlphabetRailVisible(false);
        setDrawerShelfVisible(true);
        pinSearchResultsToTop();
    }

    private void maybeExitDrawerSearch() {
        boolean stickyFocused = stickyDrawerSearch != null && stickyDrawerSearch.hasFocus();
        boolean flowFocused = flowDrawerSearch != null && flowDrawerSearch.hasFocus();
        String q = launcherAdapter == null ? "" : launcherAdapter.query;

        if (!stickyFocused && !flowFocused && q.trim().isEmpty() && drawerSearchActive) {
            drawerSearchActive = false;
            if (launcherAdapter != null) launcherAdapter.refreshForSearchMode();
        }
    }

    private void pinSearchResultsToTop() {
        if (launcherRecycler == null) return;
        launcherRecycler.post(() -> {
            RecyclerView.LayoutManager manager = launcherRecycler.getLayoutManager();
            if (manager instanceof LinearLayoutManager) {
                ((LinearLayoutManager)manager).scrollToPositionWithOffset(0, 0);
            } else {
                launcherRecycler.scrollToPosition(0);
            }
            setDrawerShelfVisible(true);
        });
    }

    private void syncDrawerSearch(EditText source, String value) {
        if (syncingDrawerSearch) return;
        syncingDrawerSearch = true;

        if (stickyDrawerSearch != null && stickyDrawerSearch != source &&
            !stickyDrawerSearch.getText().toString().equals(value)) {
            stickyDrawerSearch.setText(value);
            stickyDrawerSearch.setSelection(stickyDrawerSearch.length());
        }

        if (flowDrawerSearch != null && flowDrawerSearch != source &&
            !flowDrawerSearch.getText().toString().equals(value)) {
            flowDrawerSearch.setText(value);
            flowDrawerSearch.setSelection(flowDrawerSearch.length());
        }

        syncingDrawerSearch = false;

        if (!value.trim().isEmpty() && !drawerSearchActive) drawerSearchActive = true;
        if (launcherAdapter != null) launcherAdapter.setQuery(value);
        if (drawerSearchActive || !value.trim().isEmpty()) {
            setAlphabetRailVisible(false);
            setDrawerShelfVisible(true);
            pinSearchResultsToTop();
        }
    }

    private LinearLayout buildDrawerHeader() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackgroundColor(bg);
        top.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout inFlowShelf = buildDrawerShelf(true);
        top.addView(inFlowShelf, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            drawerShelfHeight()
        ));

        return top;
    }

    private void showSettings() {
        showSettings(0);
    }

    private void showSettings(int restoreY) {
        currentScreen = Screen.SETTINGS;
        defaultHomeStatusView = null;
        favouriteCountView = null;
        hiddenCountView = null;
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
            doomStatus.setText(doomStatusText());
            pause.setText(doomPaused() ? "Resume Doom Scroll now" : "Pause Doom Scroll for 30 minutes");
        });
        content.addView(pause);

        addSectionTitle(content, "UTILITIES");
        content.addView(modeRow("Clock", "clock_mode"));
        content.addView(modeRow("Battery", "battery_mode"));

        addSectionTitle(content, "HOME");
        content.addView(toggleRow("Swipe-up hint", "show_swipe_hint"));

        LinearLayout favouriteRow = (LinearLayout)actionRow("Favourite apps", selectedCount(essentialSet()) + " SELECTED", v -> showAppPicker(true));
        favouriteCountView = (TextView)favouriteRow.getChildAt(1);
        content.addView(favouriteRow);

        addSectionTitle(content, "HIDDEN");
        LinearLayout hiddenRow = (LinearLayout)actionRow("HIDDEN apps", selectedCount(hiddenSet()) + " HIDDEN", v -> showAppPicker(false));
        hiddenCountView = (TextView)hiddenRow.getChildAt(1);
        content.addView(hiddenRow);
        content.addView(actionRow("Review notifications", "Android settings", v -> showNotificationReview()));

        addSectionTitle(content, "APPEARANCE");
        content.addView(actionRow("Theme", themeLabel(currentTheme()), v -> showThemePickerScreen()));
        content.addView(actionRow("Seamless Home", "matching wallpaper", v -> openHiddenWallpaperPicker()));

        addSectionTitle(content, "ABOUT HIDDEN");
        TextView privacy = text("No account. No analytics. No ads. No tracking. No location access. No notification access. Your settings stay on this phone.", 14, fg);
        privacy.setLineSpacing(0, 1.25f);
        content.addView(privacy);

        TextView offline = text("HIDDEN has no internet permission. It cannot connect to the network.", 13, muted);
        pad(offline, 0, 8, 0, 14);
        content.addView(offline);

        content.addView(actionRow("Replay welcome", "intro + setup", v -> showIntroWelcome()));
        LinearLayout homeRoleRow = (LinearLayout)actionRow("Default Home app", isDefaultHome() ? "HIDDEN" : "CHANGE", v -> requestHomeRole());
        defaultHomeStatusView = (TextView)homeRoleRow.getChildAt(1);
        content.addView(homeRoleRow);

        TextView version = text("HIDDEN · v0.7.1", 12, muted);
        pad(version, 0, 26, 0, 0);
        content.addView(version);

        setContentView(scroll);
        if (restoreY > 0) scroll.post(() -> scroll.scrollTo(0, restoreY));
    }

    private TextView boldAction(String label) {
        TextView t = text(label, 16, fg);
        applyStrongTypeface(t);
        pad(t, 0, 12, 0, 12);
        return t;
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        String display = isTheme("8bit") ? "[ " + title + " ]" : title;
        TextView t = text(display, 12, muted);
        applyStrongTypeface(t);
        pad(t, 0, 30, 0, 8);
        parent.addView(t);
    }

    private View modeRow(String label, String key) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(modeLabel(prefs.getString(key, MODE_OFF)), 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        left.setMaxLines(2);
        right.setMaxLines(2);
        right.setMaxWidth(dp(180));
        right.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.leftMargin = dp(12);
        row.addView(right, rp);
        row.setOnClickListener(v -> {
            String next = nextMode(prefs.getString(key, MODE_OFF));
            prefs.edit().putString(key, next).apply();
            right.setText(modeLabel(next));
        });
        return row;
    }

    private View toggleRow(String label, String key) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(prefs.getBoolean(key, true) ? "ON" : "OFF", 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        left.setMaxLines(2);
        right.setMaxLines(2);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.leftMargin = dp(12);
        row.addView(right, rp);
        row.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean(key, true);
            prefs.edit().putBoolean(key, next).apply();
            right.setText(next ? "ON" : "OFF");
        });
        return row;
    }

    private View actionRow(String label, String value, View.OnClickListener listener) {
        LinearLayout row = settingRowBase();
        TextView left = text(label, 17, fg);
        TextView right = text(value, 13, muted);
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        left.setMaxLines(2);
        right.setMaxLines(2);
        right.setMaxWidth(dp(180));
        right.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.leftMargin = dp(12);
        row.addView(right, rp);
        if (listener != null) row.setOnClickListener(listener);
        return row;
    }

    private LinearLayout settingRowBase() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(bg);
        row.setMinimumHeight(dp(54));
        pad(row, 0, 8, 0, 8);
        return row;
    }

    private SeekBar doomSeekBar(TextView label) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(22);
        bar.setProgressTintList(ColorStateList.valueOf(fg));
        bar.setThumbTintList(ColorStateList.valueOf(fg));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(line));
        bar.setSplitTrack(false);
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
        applyStrongTypeface(brand);
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
        applyStrongTypeface(brand);
        outer.addView(brand);

        if (page == 0) onboardingOne(outer);
        else if (page == 1) onboardingTwo(outer);
        else if (page == 2) onboardingTheme(outer);
        else if (page == 3) onboardingDoom(outer);
        else onboardingFinish(outer);

        View stretch = new View(this);
        outer.addView(stretch, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = text("PAGE " + (page + 1) + " / 5", 12, muted);
        TextView next = heading(page == 4 ? "Use HIDDEN" : "Next  →", 18);
        next.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.addView(count, new LinearLayout.LayoutParams(0, dp(56), 1f));
        footer.addView(next, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));
        outer.addView(footer);

        next.setOnClickListener(v -> {
            if (page < 4) showOnboarding(page + 1);
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

        TextView copy = text("Put each utility on Home, in the app drawer, in both places, or nowhere. Or choose neither and leave your phone almost empty.", 16, muted);
        copy.setLineSpacing(0, 1.25f);
        content.addView(copy);

        addSectionTitle(content, "UTILITIES");
        content.addView(onboardingModeRow("Clock", "clock_mode"));
        content.addView(onboardingModeRow("Battery", "battery_mode"));
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
            if (value.equals(current)) applyStrongTypeface(option);
            else applyRegularTypeface(option);
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

    private void onboardingTheme(LinearLayout content) {
        TextView title = heading("Choose your HIDDEN.", 34);
        pad(title, 0, 14, 0, 8);
        content.addView(title);

        TextView copy = text("One theme now runs the whole launcher: Home, drawer, Doom Scroll, HIDDEN, Settings and the matching wallpaper.", 15, muted);
        copy.setLineSpacing(0, 1.22f);
        content.addView(copy);

        String[] themes = {"light", "dark", "oled", "doom_light", "doom_dark", "soft_launch", "moth", "8bit"};
        for (String theme : themes) {
            boolean selected = theme.equals(currentTheme());
            ThemePreviewView preview = new ThemePreviewView(this, theme, selected);
            preview.setOnClickListener(v -> {
                prefs.edit().putString("theme_mode", theme).apply();
                applyPalette();
                showOnboarding(2);
            });
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78));
            pp.topMargin = dp(9);
            content.addView(preview, pp);
        }

        addSectionTitle(content, "SEAMLESS HOME");
        TextView seamless = text("Use HIDDEN's matching wallpaper so Android has the same surface to show during the Home transition.", 13, muted);
        seamless.setLineSpacing(0, 1.2f);
        content.addView(seamless);

        TextView wallpaper = boldAction("Set matching HIDDEN wallpaper");
        wallpaper.setOnClickListener(v -> openHiddenWallpaperPicker());
        content.addView(wallpaper);
    }

    private void onboardingDoom(LinearLayout content) {
        TextView title = heading("How far away should distractions be?", 34);
        pad(title, 0, 14, 0, 8);
        content.addView(title);

        TextView copy = text("Doom Scroll is the distance between the useful part of your phone and the apps you chose to hide.", 15, muted);
        copy.setLineSpacing(0, 1.22f);
        content.addView(copy);

        TextView doomLabel = heading(doomDistanceLabel(prefs.getInt("doom_screens", 18)), 17);
        pad(doomLabel, 0, 24, 0, 4);
        content.addView(doomLabel);
        content.addView(doomSeekBar(doomLabel));

        addSectionTitle(content, "NAVIGATION");
        content.addView(toggleRow("Swipe-up hint", "show_swipe_hint"));

        TextView pauseNote = text("If you repeatedly make the trip to HIDDEN, HIDDEN can offer a 30-minute Doom Scroll pause when you clearly need access.", 13, muted);
        pauseNote.setLineSpacing(0, 1.2f);
        pad(pauseNote, 0, 8, 0, 0);
        content.addView(pauseNote);
    }

    private void onboardingFinish(LinearLayout content) {
        TextView title = heading("Keep the time thieves out of reach.", 34);
        pad(title, 0, 14, 0, 8);
        content.addView(title);

        TextView copy = text("Make HIDDEN your Home app. Everything else stays private, local and deliberately boring.", 16, muted);
        copy.setLineSpacing(0, 1.25f);
        content.addView(copy);

        addSectionTitle(content, "PRIVACY");
        TextView privacy = text("No account. No analytics. No ads. No tracking. No location access. No notification access.", 14, fg);
        privacy.setLineSpacing(0, 1.25f);
        content.addView(privacy);

        TextView offline = text("HIDDEN has no internet permission. Nothing leaves your phone.", 13, muted);
        offline.setLineSpacing(0, 1.25f);
        pad(offline, 0, 10, 0, 18);
        content.addView(offline);

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

    private String themeLabel(String theme) {
        String t = normaliseTheme(theme);
        if ("light".equals(t)) return "LIGHT";
        if ("dark".equals(t)) return "DARK";
        if ("oled".equals(t)) return "OLED";
        if ("doom_light".equals(t)) return "DOOM SCROLL LIGHT";
        if ("doom_dark".equals(t)) return "DOOM SCROLL DARK";
        if ("soft_launch".equals(t)) return "SOFT LAUNCH";
        if ("moth".equals(t)) return "MOTH TO A FLAME";
        if ("8bit".equals(t)) return "8-BIT";
        return "DARK";
    }

    private void showThemePickerScreen() {
        currentScreen = Screen.THEME_PICKER;
        applyPalette();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pad(content, 26, 42, 26, 38);
        scroll.addView(content);

        TextView title = heading("Theme", 34);
        content.addView(title);

        TextView copy = text("One theme applies everywhere in HIDDEN.", 14, muted);
        pad(copy, 0, 6, 0, 12);
        content.addView(copy);

        String[] themes = {"light", "dark", "oled", "doom_light", "doom_dark", "soft_launch", "moth", "8bit"};
        for (String theme : themes) {
            boolean selected = theme.equals(currentTheme());
            ThemePreviewView preview = new ThemePreviewView(this, theme, selected);
            preview.setOnClickListener(v -> {
                prefs.edit().putString("theme_mode", theme).apply();
                applyPalette();
                showThemePickerScreen();
            });
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
            pp.topMargin = dp(9);
            content.addView(preview, pp);
        }

        TextView wallpaper = boldAction("Set matching HIDDEN wallpaper");
        pad(wallpaper, 0, 18, 0, 12);
        wallpaper.setOnClickListener(v -> openHiddenWallpaperPicker());
        content.addView(wallpaper);

        TextView back = boldAction("‹ Settings");
        back.setOnClickListener(v -> showSettings());
        content.addView(back);

        setContentView(scroll);
    }

    private void openHiddenWallpaperPicker() {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, HiddenWallpaperService.class)
            );
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception ignored) {}
        }
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
        if (launcherAdapter != null && launcherAdapter.isSearchMode()) {
            resetDrawerSearch();
        }

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

    class ThemePatternDrawable extends Drawable {
        private final String theme;
        private final long seed;

        ThemePatternDrawable(String theme, long seed) {
            this.theme = theme;
            this.seed = seed;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            ThemeArt.draw(canvas, bounds.width(), bounds.height(), theme, getResources(), seed);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
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
            a.setDuration(5200);
            a.addUpdateListener(v -> {
                progress = (float)v.getAnimatedValue();
                invalidate();
            });
            a.start();
            handler.postDelayed(done, 4650);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(bg);

            float phoneW = getWidth() * 0.58f;
            float phoneH = phoneW * 1.85f;
            float left = (getWidth() - phoneW) / 2f;
            float top = getHeight() * 0.12f;
            RectF phone = new RectF(left, top, left + phoneW, top + phoneH);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(3));
            p.setColor(fg);
            c.drawRoundRect(phone, dp(30), dp(30), p);

            RectF screen = new RectF(
                phone.left + dp(10),
                phone.top + dp(18),
                phone.right - dp(10),
                phone.bottom - dp(18)
            );

            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            c.drawRoundRect(screen, dp(22), dp(22), p);

            float explode = clamp((progress - 0.07f) / 0.34f);
            int cols = 4;
            int rows = 5;
            float icon = phoneW * 0.13f;
            float xGap = (screen.width() - cols * icon) / (cols + 1);
            float yGap = dp(22);

            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < cols; col++) {
                    int idx = r * cols + col;
                    float delay = idx / 29f;
                    float local = clamp((explode - delay) * 2.45f);
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
                            float bit = dp(3 + random.nextInt(4));
                            c.drawRect(
                                x + icon/2 + ox,
                                y + icon/2 + oy,
                                x + icon/2 + ox + bit,
                                y + icon/2 + oy + bit,
                                p
                            );
                        }
                    }
                }
            }

            p.setAlpha(255);

            float reveal = clamp((progress - 0.34f) / 0.16f);
            float doom = clamp((progress - 0.50f) / 0.39f);

            if (reveal > 0f) {
                int save = c.save();
                Path clip = new Path();
                clip.addRoundRect(screen, dp(22), dp(22), Path.Direction.CW);
                c.clipPath(clip);

                float sh = screen.height();
                float worldH = sh * 6.5f;
                float scrollY = easeInOut(doom) * (worldH - sh);

                drawIntroWorld(c, screen, sh, scrollY, reveal);

                c.restoreToCount(save);
            }

            if (progress > 0.88f) {
                int alpha = (int)(255 * clamp((progress - 0.88f) / 0.12f));
                p.setColor(Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg)));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
            }
        }

        private void drawIntroWorld(Canvas c, RectF screen, float sh, float scrollY, float reveal) {
            float x = screen.left;
            float w = screen.width();

            // HIDDEN home.
            drawWorldRect(c, screen, 0f, sh, scrollY, bg);

            float rise = dp(40) * (1f - reveal);
            drawWorldCenteredText(c, screen, "4:38", dp(66) + rise, scrollY, dp(25), fg, true);
            drawWorldCenteredText(c, screen, "Thursday · 24 September", dp(88) + rise, scrollY, dp(9), muted, false);
            drawWorldCenteredText(c, screen, "BATTERY  72%", dp(112) + rise, scrollY, dp(9), muted, true);
            drawWorldText(c, screen, "Phone", x + dp(20), sh - dp(84) + rise, scrollY, dp(12), fg, false);
            drawWorldText(c, screen, "Messages", x + dp(20), sh - dp(60) + rise, scrollY, dp(12), fg, false);
            drawWorldText(c, screen, "Camera", x + dp(20), sh - dp(36) + rise, scrollY, dp(12), fg, false);

            // App drawer.
            float drawerTop = sh;
            drawWorldRect(c, screen, drawerTop, drawerTop + sh * 1.25f, scrollY, bg);
            drawWorldCenteredText(c, screen, "4:38  ·  BATTERY 72%", drawerTop + dp(34), scrollY, dp(10), muted, true);
            drawWorldCenteredText(c, screen, "HIDDEN Settings", drawerTop + dp(58), scrollY, dp(11), fg, true);
            String[] appNames = {"Calculator", "Camera", "Maps", "Messages", "Music", "Photos"};
            for (int i = 0; i < appNames.length; i++) {
                drawWorldText(c, screen, appNames[i], x + dp(20), drawerTop + dp(96 + i * 34), scrollY, dp(12), fg, false);
            }

            // A little dead air before the colour journey.
            float blankTop = sh * 2.25f;
            float blankBottom = sh * 2.85f;
            drawWorldRect(c, screen, blankTop, blankBottom, scrollY, bg);

            // The HIDDEN colour journey.
            float gradTop = blankBottom;
            float gradBottom = sh * 5.25f;
            float gy1 = screen.top + gradTop - scrollY;
            float gy2 = screen.top + gradBottom - scrollY;

            int[] rainbow = {
                Color.rgb(224, 205, 159),
                Color.rgb(214, 172, 116),
                Color.rgb(196, 131, 115),
                Color.rgb(172, 111, 137),
                Color.rgb(133, 105, 145),
                Color.rgb(91, 101, 135),
                Color.rgb(55, 76, 106),
                Color.rgb(20, 27, 42),
                Color.rgb(6, 7, 10)
            };

            LinearGradient gradient = new LinearGradient(
                0, gy1,
                0, gy2,
                rainbow,
                null,
                Shader.TileMode.CLAMP
            );
            p.setShader(gradient);
            c.drawRect(screen.left, gy1, screen.right, gy2, p);
            p.setShader(null);

            // Space / stars.
            float spaceTop = sh * 5.25f;
            float spaceBottom = sh * 6.5f;
            drawWorldRect(c, screen, spaceTop, spaceBottom, scrollY, Color.rgb(6, 7, 10));

            random.setSeed(424242L);
            for (int i = 0; i < 80; i++) {
                float sx = screen.left + random.nextFloat() * w;
                float wy = spaceTop + random.nextFloat() * (spaceBottom - spaceTop);
                float sy = screen.top + wy - scrollY;
                if (sy < screen.top || sy > screen.bottom) continue;

                float density = getResources().getDisplayMetrics().density;
                float radius = density * (0.7f + random.nextFloat());
                int alpha = 120 + random.nextInt(136);
                p.setColor(Color.argb(alpha, 246, 246, 242));
                c.drawCircle(sx, sy, radius, p);
            }

            drawWorldText(c, screen, "HIDDEN", x + dp(20), sh * 6.08f, scrollY, dp(20), Color.rgb(245,245,242), true);
            drawWorldText(c, screen, "Instagram", x + dp(20), sh * 6.18f, scrollY, dp(12), Color.rgb(225,225,220), false);
            drawWorldText(c, screen, "Reddit", x + dp(20), sh * 6.25f, scrollY, dp(12), Color.rgb(225,225,220), false);
            drawWorldText(c, screen, "YouTube", x + dp(20), sh * 6.32f, scrollY, dp(12), Color.rgb(225,225,220), false);
        }

        private void drawWorldCenteredText(
            Canvas c,
            RectF screen,
            String value,
            float worldY,
            float scrollY,
            float textSize,
            int color,
            boolean bold
        ) {
            float y = screen.top + worldY - scrollY;
            if (y < screen.top - dp(30) || y > screen.bottom + dp(30)) return;

            p.setShader(null);
            p.setColor(color);
            p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            p.setTextSize(textSize);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(value, screen.centerX(), y, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawWorldRect(Canvas c, RectF screen, float worldTop, float worldBottom, float scrollY, int color) {
            float top = screen.top + worldTop - scrollY;
            float bottom = screen.top + worldBottom - scrollY;
            if (bottom < screen.top || top > screen.bottom) return;

            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRect(screen.left, top, screen.right, bottom, p);
        }

        private void drawWorldText(
            Canvas c,
            RectF screen,
            String value,
            float x,
            float worldY,
            float scrollY,
            float textSize,
            int color,
            boolean bold
        ) {
            float y = screen.top + worldY - scrollY;
            if (y < screen.top - dp(30) || y > screen.bottom + dp(30)) return;

            p.setShader(null);
            p.setColor(color);
            p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            p.setTextSize(textSize);
            c.drawText(value, x, y, p);
        }

        private float easeInOut(float t) {
            t = clamp(t);
            return t < 0.5f
                ? 2f * t * t
                : 1f - (float)Math.pow(-2f * t + 2f, 2f) / 2f;
        }

        private float clamp(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }

    class ThemePreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String theme;
        private final boolean selected;

        ThemePreviewView(Context context, String theme, boolean selected) {
            super(context);
            this.theme = theme;
            this.selected = selected;
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ThemeArt.draw(canvas, getWidth(), getHeight(), theme, getResources(), 8675309L);

            String label = themeLabel(theme);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(15));
            if ("8bit".equals(theme)) {
                paint.setTypeface(Typeface.MONOSPACE);
            } else if ("soft_launch".equals(theme)) {
                paint.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
            } else if ("moth".equals(theme)) {
                paint.setTypeface(Typeface.create("serif", Typeface.BOLD));
            } else {
                paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            }
            paint.setAntiAlias(!"8bit".equals(theme));

            int textColor = ThemeArt.foreground(theme, getResources());
            paint.setColor(textColor);
            canvas.drawText(label, getWidth() / 2f, getHeight() / 2f + dp(5), paint);

            if (selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(textColor);
                canvas.drawRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1), paint);
                paint.setStyle(Paint.Style.FILL);
            }
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

            paint.setTypeface(isTheme("8bit")
                ? Typeface.MONOSPACE
                : Typeface.create("sans-serif", Typeface.BOLD));
            paint.setAntiAlias(!isTheme("8bit"));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));

            int[] doomColors = doomAccentColors();

            float step = getHeight() / (float)letters.length();
            for (int i = 0; i < letters.length(); i++) {
                paint.setColor(isDoomTheme() ? doomColors[i % doomColors.length] : muted);
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
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), blank);
            ThemeArt.draw(canvas, getWidth(), blank, currentTheme(), getResources(), 4815162342L);
            canvas.restore();

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
        private static final int TYPE_EMPTY = 8;

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
            if ("EMPTY".equals(item)) return -1006;
            if (item instanceof AppItem) return ((AppItem)item).pkg.hashCode();
            if (item instanceof HiddenApp) return -2000000000L + ((HiddenApp)item).app.pkg.hashCode();
            return position;
        }

        void setQuery(String value) {
            String next = value == null ? "" : value.trim();
            if (next.equals(query) && isSearchMode() == drawerSearchActive) return;
            query = next;
            rebuild();
            notifyDataSetChanged();
            if (isSearchMode()) pinSearchResultsToTop();
        }

        void refreshForSearchMode() {
            rebuild();
            notifyDataSetChanged();
            if (isSearchMode()) pinSearchResultsToTop();
        }

        void refreshApps() {
            rebuild();
            notifyDataSetChanged();
        }

        void clearSearchAndRestoreDrawer() {
            query = "";
            rebuild();
            notifyDataSetChanged();
        }

        boolean isSearchMode() {
            return drawerSearchActive || !query.isEmpty();
        }

        int headerPosition() { return isSearchMode() ? 0 : 1; }
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

            String q = query.toLowerCase(Locale.ROOT);

            if (isSearchMode()) {
                items.add("HEADER");
                firstAppPos = 1;
            } else {
                items.add("HOME");
                items.add("HEADER");
                firstAppPos = 2;
            }

            Set<String> hidden = hiddenSet();
            int matchCount = 0;

            for (AppItem app : apps) {
                if (hidden.contains(app.pkg)) continue;
                if (!q.isEmpty() && !app.label.toLowerCase(Locale.ROOT).contains(q)) continue;

                int position = items.size();
                items.add(app);
                matchCount++;

                if (!app.label.isEmpty()) {
                    char c = Character.toUpperCase(app.label.charAt(0));
                    if (c >= 'A' && c <= 'Z' && !letterPositions.containsKey(c)) letterPositions.put(c, position);
                }
            }

            if (isSearchMode()) {
                if (!q.isEmpty() && matchCount == 0) items.add("EMPTY");
                return;
            }
            if (hidden.isEmpty()) return;

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
            if ("EMPTY".equals(item)) return TYPE_EMPTY;
            if (item instanceof HiddenApp) return TYPE_HIDDEN_APP;
            return TYPE_APP;
        }

        @NonNull
        @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            if (type == TYPE_HOME) {
                LinearLayout home = buildHomePanel();
                home.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new SimpleHolder(home);
            }
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
                block.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                pad(block, 26, 24, 26, 16);
                return new SimpleHolder(block);
            }

            if (type == TYPE_EMPTY) {
                TextView empty = text("No apps found", 16, muted);
                empty.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
                empty.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(72)
                ));
                return new SimpleHolder(empty);
            }

            if (type == TYPE_REWIND) {
                TextView rewind = heading("REWIND  ↑", 18);
                rewind.setGravity(Gravity.CENTER);
                rewind.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                pad(rewind, 20, 26, 20, 34);
                return new SimpleHolder(rewind);
            }

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setMinimumHeight(dp(54));
            pad(row, 26, 5, 58, 5);

            TextView label = text("", 18, fg);
            label.setSingleLine(true);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setIncludeFontPadding(false);
            row.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new AppHolder(row, label);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);

            if (type == TYPE_APP) {
                AppItem app = (AppItem)items.get(position);
                AppHolder h = (AppHolder)holder;
                h.root.setBackgroundColor(bg);
                h.label.setTextColor(fg);

                if (isTheme("8bit")) {
                    h.label.setText("> " + app.label.toUpperCase(Locale.ROOT));
                    applyRegularTypeface(h.label);
                } else if (isDoomTheme()) {
                    h.label.setText(globalStyledText(app.label.toUpperCase(Locale.ROOT)));
                    h.label.setShadowLayer(1.6f, 0f, 1f, isTheme("doom_light") ? Color.argb(120,255,250,238) : Color.argb(220,0,0,0));
                    applyStrongTypeface(h.label);
                } else {
                    h.label.setText(app.label);
                    applyRegularTypeface(h.label);
                }
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

                String hiddenTheme = currentTheme();
                boolean hiddenDoom = "doom_light".equals(hiddenTheme) || "doom_dark".equals(hiddenTheme);
                TextView title = hiddenHeading(hiddenDoom ? "DOOM SCROLL" : "HIDDEN", 36, hp.foreground);
                block.addView(title);

                if (hiddenDoom) {
                    TextView fine = hiddenLabel("fine.", 13, hp.muted, false);
                    pad(fine, 0, 2, 0, 8);
                    block.addView(fine);
                } else if ("8bit".equals(currentTheme())) {
                    TextView ready = hiddenLabel("SECTOR 00 // READY_", 12, hp.muted, true);
                    pad(ready, 0, 2, 0, 8);
                    block.addView(ready);
                }

                if (showRepeatPause && !doomPaused()) {
                    TextView pause = hiddenLabel("You've made this trip a few times. Pause Doom Scroll for 30 minutes?", 14, hp.foreground, false);
                    pause.setLineSpacing(0, 1.2f);
                    pad(pause, 0, 12, 0, 6);
                    block.addView(pause);

                    TextView action = hiddenLabel("Pause for 30 minutes", 15, hp.foreground, true);
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
                styleHiddenText(rewind, true);
                rewind.setOnClickListener(v -> rewindHome(true));
            }
        }

        @Override public int getItemCount() { return items.size(); }

        private TextView hiddenHeading(String value, float sp, int color) {
            return hiddenLabel(value, sp, color, true);
        }

        private TextView hiddenLabel(String value, float sp, int color, boolean strong) {
            TextView t = new TextView(MainActivity.this);
            t.setTextSize(sp);
            t.setTextColor(color);
            t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            t.setFontFeatureSettings("kern");

            String theme = currentTheme();
            if ("8bit".equals(theme)) {
                t.setText(value.toUpperCase(Locale.ROOT));
                t.setTypeface(Typeface.MONOSPACE, strong ? Typeface.BOLD : Typeface.NORMAL);
                t.getPaint().setAntiAlias(false);
                t.setLetterSpacing(0.055f);
            } else if ("doom_light".equals(theme) || "doom_dark".equals(theme)) {
                t.setText(strong ? earthyDoomText(value) : value);
                t.setTypeface(Typeface.create("sans-serif-condensed", strong ? Typeface.BOLD : Typeface.NORMAL));
                t.setLetterSpacing(strong ? 0.055f : 0.035f);
                if (strong) {
                    t.setShadowLayer(1.6f, 0f, 1f, "doom_light".equals(theme) ? Color.argb(120,255,250,238) : Color.argb(220,0,0,0));
                }
            } else {
                t.setText(value);
                t.setTypeface(Typeface.create("sans-serif", strong ? Typeface.BOLD : Typeface.NORMAL));
            }
            return t;
        }

        private void styleHiddenText(TextView t, boolean strong) {
            String value = t.getText().toString();
            String theme = currentTheme();
            if ("8bit".equals(theme)) {
                t.setText(value.toUpperCase(Locale.ROOT));
                t.setTypeface(Typeface.MONOSPACE, strong ? Typeface.BOLD : Typeface.NORMAL);
                t.getPaint().setAntiAlias(false);
            } else if ("doom_light".equals(theme) || "doom_dark".equals(theme)) {
                t.setText(earthyDoomText(value));
                t.setTypeface(Typeface.create("sans-serif-condensed", strong ? Typeface.BOLD : Typeface.NORMAL));
            } else {
                t.setTypeface(Typeface.create("sans-serif", strong ? Typeface.BOLD : Typeface.NORMAL));
            }
        }

        private void confirmHide(AppItem app) {
            dialogBuilder()
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
            dialogBuilder()
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

            String theme = currentTheme();
            if ("8bit".equals(theme)) {
                h.label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                h.label.getPaint().setAntiAlias(false);
                h.label.setLetterSpacing(0.055f);
                h.label.setText("> " + app.label.toUpperCase(Locale.ROOT) + " _");
            } else if ("doom_light".equals(theme) || "doom_dark".equals(theme)) {
                h.label.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                h.label.setText(earthyDoomText(app.label.toUpperCase(Locale.ROOT)));
                h.label.setShadowLayer(1.6f, 0f, 1f, "doom_light".equals(theme) ? Color.argb(120,255,250,238) : Color.argb(220,0,0,0));
            } else {
                h.label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                h.label.setText(app.label);
            }
        }

        private CharSequence earthyDoomText(String value) {
            return globalStyledText(value);
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
        String theme = currentTheme();
        return new HiddenPalette(
            ThemeArt.background(theme, getResources()),
            ThemeArt.foreground(theme, getResources()),
            ThemeArt.muted(theme, getResources())
        );
    }
}
