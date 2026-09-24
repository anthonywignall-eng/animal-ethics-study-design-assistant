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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int PAPER = Color.rgb(241, 239, 232);
    private static final int INK = Color.rgb(21, 21, 19);
    private static final int MUTED = Color.rgb(113, 110, 103);
    private static final int DARK = Color.rgb(8, 9, 12);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private List<AppItem> apps = new ArrayList<>();
    private float downY;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (currentScreen == Screen.HOME) showHome();
            handler.postDelayed(this, 60000);
        }
    };

    private enum Screen { HOME, DRAWER, SETTINGS }
    private Screen currentScreen = Screen.HOME;

    static class AppItem {
        String label;
        String pkg;
        String activity;
        AppItem(String label, String pkg, String activity) {
            this.label = label;
            this.pkg = pkg;
            this.activity = activity;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("hidden_launcher", MODE_PRIVATE);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        apps = loadApps();
        initialiseEssentials();
        showHome();
        handler.postDelayed(clockTick, 60000);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) showHome();
    }

    @Override
    public void onBackPressed() {
        if (currentScreen != Screen.HOME) showHome();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
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
        return t;
    }

    private void pad(View v, int l, int t, int r, int b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    private void showHome() {
        currentScreen = Screen.HOME;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        pad(root, 28, 36, 28, 18);

        TextView time = text(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()), 62, INK);
        TextView date = text(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()), 15, MUTED);
        root.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(date);

        TextView weather = text("", 14, INK);
        weather.setPadding(0, dp(20), 0, 0);
        root.addView(weather);
        String city = prefs.getString("weather_city", "");
        if (city == null || city.trim().isEmpty()) {
            weather.setText("weather · choose a city in settings");
        } else {
            weather.setText(city + " · checking weather");
            fetchWeather(city.trim(), weather);
        }

        TextView battery = text("battery · " + batteryPercent() + "%", 14, MUTED);
        root.addView(battery);

        if (!isDefaultHome()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundColor(Color.rgb(227, 224, 215));
            pad(card, 14, 12, 12, 12);

            TextView label = text("Make Hidden your home screen", 14, INK);
            card.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button set = new Button(this);
            set.setText("Set");
            set.setAllCaps(false);
            set.setOnClickListener(v -> requestHomeRole());
            card.addView(set);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = dp(22);
            root.addView(card, cp);
        }

        LinearLayout essentials = new LinearLayout(this);
        essentials.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        ep.topMargin = dp(24);
        root.addView(essentials, ep);

        Set<String> hidden = hiddenSet();
        Set<String> essential = essentialSet();
        int count = 0;
        for (AppItem app : apps) {
            if (essential.contains(app.pkg) && !hidden.contains(app.pkg)) {
                TextView row = text(app.label, 20, INK);
                pad(row, 0, 8, 0, 8);
                row.setOnClickListener(v -> launch(app));
                essentials.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                count++;
            }
        }

        if (count == 0) {
            TextView none = text("choose your essentials in settings", 16, MUTED);
            pad(none, 0, 8, 0, 8);
            none.setOnClickListener(v -> showSettings());
            essentials.addView(none);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView drawer = text("↑ apps", 13, MUTED);
        TextView settings = text("settings", 13, MUTED);
        drawer.setOnClickListener(v -> showDrawer());
        settings.setOnClickListener(v -> showSettings());
        footer.addView(drawer, new LinearLayout.LayoutParams(0, dp(52), 1f));
        footer.addView(settings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
        root.addView(footer);

        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) downY = event.getY();
            if (event.getAction() == MotionEvent.ACTION_UP && event.getY() - downY < -dp(90)) {
                showDrawer();
                return true;
            }
            return false;
        });

        setContentView(root);
    }

    private void showDrawer() {
        currentScreen = Screen.DRAWER;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        pad(header, 28, 28, 28, 10);

        TextView title = text("apps", 38, INK);
        header.addView(title);

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(INK);
        search.setHintTextColor(Color.rgb(139, 135, 126));
        search.setBackgroundColor(Color.rgb(227, 224, 215));
        pad(search, 14, 10, 14, 10);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(14);
        header.addView(search, sp);

        TextView helper = text("hold an app to hide it", 12, MUTED);
        pad(helper, 0, 10, 0, 2);
        header.addView(helper);
        content.addView(header);

        LinearLayout visible = new LinearLayout(this);
        visible.setOrientation(LinearLayout.VERTICAL);
        content.addView(visible);
        populateVisible(visible, "");

        Set<String> hidden = hiddenSet();
        JourneyView journey = new JourneyView(this, !hidden.isEmpty());
        content.addView(journey, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(hidden.isEmpty() ? 1100 : 5000)));

        LinearLayout hiddenBlock = new LinearLayout(this);
        hiddenBlock.setOrientation(LinearLayout.VERTICAL);
        hiddenBlock.setBackgroundColor(DARK);

        if (!hidden.isEmpty()) {
            TextView hTitle = text("hidden", 40, PAPER);
            pad(hTitle, 28, 26, 28, 0);
            hiddenBlock.addView(hTitle);
            TextView hSub = text("you came looking for these", 13, Color.rgb(136, 138, 145));
            pad(hSub, 28, 0, 28, 18);
            hiddenBlock.addView(hSub);

            for (AppItem app : apps) {
                if (hidden.contains(app.pkg)) hiddenBlock.addView(appRow(app, true));
            }

            View tail = new View(this);
            tail.setBackgroundColor(DARK);
            hiddenBlock.addView(tail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
        }

        content.addView(hiddenBlock);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                populateVisible(visible, q);
                journey.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
                hiddenBlock.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        setContentView(scroll);
        scroll.scrollTo(0, 0);
    }

    private void populateVisible(LinearLayout visible, String query) {
        visible.removeAllViews();
        Set<String> hidden = hiddenSet();
        String q = query.toLowerCase(Locale.ROOT);

        for (AppItem app : apps) {
            if (hidden.contains(app.pkg)) continue;
            if (!q.isEmpty() && !app.label.toLowerCase(Locale.ROOT).contains(q)) continue;
            visible.addView(appRow(app, false));
        }
    }

    private View appRow(AppItem app, boolean dark) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(dark ? DARK : PAPER);
        pad(wrap, 28, 10, 28, 0);

        TextView row = text(app.label, 18, dark ? PAPER : INK);
        pad(row, 0, 2, 0, 10);
        row.setOnClickListener(v -> launch(app));
        row.setOnLongClickListener(v -> {
            if (dark) confirmRestore(app);
            else confirmHide(app);
            return true;
        });
        wrap.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        View line = new View(this);
        line.setBackgroundColor(dark ? Color.rgb(39, 41, 51) : Color.rgb(213, 209, 199));
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return wrap;
    }

    private void confirmHide(AppItem app) {
        new AlertDialog.Builder(this)
            .setTitle("Hide " + app.label + "?")
            .setMessage("It will disappear from the normal list and search. To reach it again, you'll have to make the long trip down.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Hide it", (d, w) -> {
                Set<String> hidden = hiddenSet();
                hidden.add(app.pkg);
                saveSet("hidden_packages", hidden);

                Set<String> essential = essentialSet();
                if (essential.remove(app.pkg)) saveSet("essential_packages", essential);
                showDrawer();
            })
            .show();
    }

    private void confirmRestore(AppItem app) {
        new AlertDialog.Builder(this)
            .setTitle("Bring " + app.label + " back?")
            .setMessage("It will return to the normal app list.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore", (d, w) -> {
                Set<String> hidden = hiddenSet();
                hidden.remove(app.pkg);
                saveSet("hidden_packages", hidden);
                showDrawer();
            })
            .show();
    }

    private void showSettings() {
        currentScreen = Screen.SETTINGS;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAPER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pad(content, 28, 26, 28, 50);
        scroll.addView(content);

        TextView title = text("settings", 38, INK);
        content.addView(title);

        TextView back = text("‹ home", 14, MUTED);
        pad(back, 0, 8, 0, 18);
        back.setOnClickListener(v -> showHome());
        content.addView(back);

        TextView weatherTitle = text("weather", 19, INK);
        content.addView(weatherTitle);
        TextView weatherExplainer = text("Choose a place yourself. Hidden never asks Android for your location.", 13, MUTED);
        pad(weatherExplainer, 0, 6, 0, 8);
        content.addView(weatherExplainer);

        EditText city = new EditText(this);
        city.setSingleLine(true);
        city.setHint("Adelaide, South Australia");
        city.setText(prefs.getString("weather_city", ""));
        city.setTextSize(15);
        city.setBackgroundColor(Color.rgb(227, 224, 215));
        pad(city, 14, 10, 14, 10);
        content.addView(city, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView saveCity = text("save city", 15, INK);
        pad(saveCity, 0, 8, 0, 24);
        saveCity.setOnClickListener(v -> {
            prefs.edit().putString("weather_city", city.getText().toString().trim()).apply();
            saveCity.setText("saved");
        });
        content.addView(saveCity);

        TextView eTitle = text("home essentials", 19, INK);
        content.addView(eTitle);
        TextView eSub = text("Tap the few apps that deserve to be on your home screen.", 13, MUTED);
        pad(eSub, 0, 6, 0, 10);
        content.addView(eSub);

        Set<String> hidden = hiddenSet();
        for (AppItem app : apps) {
            if (hidden.contains(app.pkg)) continue;
            content.addView(essentialRow(app));
        }

        TextView pTitle = text("privacy", 19, INK);
        pad(pTitle, 0, 34, 0, 6);
        content.addView(pTitle);

        TextView p1 = text("No account. No analytics. No ads. No tracking SDKs. Your hidden list and settings stay on this phone.", 14, INK);
        p1.setLineSpacing(0, 1.25f);
        content.addView(p1);

        TextView p2 = text("Permission footprint: internet only, for weather. No location, contacts, notification access, accessibility service, microphone, camera or storage permission.", 13, MUTED);
        p2.setLineSpacing(0, 1.25f);
        pad(p2, 0, 10, 0, 0);
        content.addView(p2);

        TextView brand = text("Hidden", 18, INK);
        pad(brand, 0, 34, 0, 0);
        content.addView(brand);
        content.addView(text("distraction-killing launcher · v0.1", 12, MUTED));

        setContentView(scroll);
    }

    private View essentialRow(AppItem app) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        pad(row, 0, 8, 0, 8);

        TextView name = text(app.label, 16, INK);
        TextView tick = text(essentialSet().contains(app.pkg) ? "✓" : "", 17, INK);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(36), 1f));
        row.addView(tick, new LinearLayout.LayoutParams(dp(36), dp(36)));

        row.setOnClickListener(v -> {
            Set<String> set = essentialSet();
            if (set.contains(app.pkg)) set.remove(app.pkg);
            else set.add(app.pkg);
            saveSet("essential_packages", set);
            tick.setText(set.contains(app.pkg) ? "✓" : "");
        });
        return row;
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

    private void initialiseEssentials() {
        if (prefs.getBoolean("essentials_initialized", false)) return;

        String[] wanted = {"phone", "messages", "camera", "maps", "calendar", "music"};
        Set<String> chosen = new HashSet<>();

        for (String target : wanted) {
            for (AppItem app : apps) {
                if (app.label.toLowerCase(Locale.ROOT).contains(target)) {
                    chosen.add(app.pkg);
                    break;
                }
            }
        }

        prefs.edit()
            .putStringSet("essential_packages", chosen)
            .putBoolean("essentials_initialized", true)
            .apply();
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

    private void fetchWeather(String city, TextView target) {
        new Thread(() -> {
            try {
                String q = URLEncoder.encode(city, StandardCharsets.UTF_8.toString());
                JSONObject geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=1&language=en&format=json");
                JSONArray results = geo.optJSONArray("results");
                if (results == null || results.length() == 0) throw new Exception("not found");

                JSONObject place = results.getJSONObject(0);
                double lat = place.getDouble("latitude");
                double lon = place.getDouble("longitude");
                String name = place.optString("name", city);
                String admin = place.optString("admin1", "");
                String label = admin.isEmpty() || admin.equalsIgnoreCase(name) ? name : name + ", " + admin;

                JSONObject forecast = getJson("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,weather_code&timezone=auto&forecast_days=1");
                JSONObject current = forecast.getJSONObject("current");
                int temp = (int)Math.round(current.getDouble("temperature_2m"));
                int code = current.optInt("weather_code", -1);
                String line = label + " · " + temp + "° · " + weatherLabel(code);

                runOnUiThread(() -> {
                    if (currentScreen == Screen.HOME && target.getWindowToken() != null) target.setText(line);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (currentScreen == Screen.HOME && target.getWindowToken() != null) target.setText(city + " · weather unavailable");
                });
            }
        }).start();
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "HiddenLauncher/0.1");
        try {
            if (c.getResponseCode() < 200 || c.getResponseCode() > 299) throw new Exception("http");
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

    class JourneyView extends View {
        private final boolean hasHidden;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        JourneyView(Context context, boolean hasHidden) {
            super(context);
            this.hasHidden = hasHidden;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int[] colors = {
                PAPER,
                Color.rgb(240, 217, 132),
                Color.rgb(239, 156, 85),
                Color.rgb(223, 93, 84),
                Color.rgb(182, 69, 112),
                Color.rgb(116, 59, 120),
                Color.rgb(52, 51, 84),
                Color.rgb(23, 26, 40),
                DARK
            };

            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), colors, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            paint.setTextAlign(Paint.Align.CENTER);

            if (hasHidden) {
                paint.setTextSize(dp(12));
                paint.setColor(Color.rgb(117, 95, 53));
                canvas.drawText("there's nothing else up here", getWidth()/2f, dp(650), paint);

                paint.setTextSize(dp(13));
                paint.setColor(Color.rgb(221, 186, 181));
                canvas.drawText("still going?", getWidth()/2f, getHeight()/2f, paint);

                paint.setColor(Color.rgb(176, 178, 194));
                canvas.drawText("you hid these for a reason", getWidth()/2f, getHeight() - dp(600), paint);
            } else {
                paint.setTextSize(dp(13));
                paint.setColor(Color.rgb(107, 93, 88));
                canvas.drawText("nothing hidden yet", getWidth()/2f, getHeight()/2f, paint);
            }
        }
    }
}
