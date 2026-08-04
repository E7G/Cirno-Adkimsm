package nep.timeline.cirno;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nep.timeline.cirno.configs.ConfigManager;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.CommonConstants;
import nep.timeline.cirno.entity.AppItem;
import nep.timeline.cirno.core.AndroidPolicy;
import nep.timeline.cirno.provide.ApplicationBinder;
import nep.timeline.cirno.provide.ApplicationBinderFacade;
import nep.timeline.cirno.utils.AndroidRuntime;
import nep.timeline.cirno.utils.PackageUtils;

/** Native Android UI: app configuration plus a live freezer-effect monitor. */
public final class MainActivity extends Activity {
    private static final long MONITOR_REFRESH_VISIBLE_MS = 10_000L;
    private int BG;
    private int SURFACE;
    private int TEXT;
    private int MUTED;
    private int BORDER;
    private int ACCENT;
    private int GREEN;
    private int ORANGE;
    private int RED;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Cirno-UI");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AppItem> apps = new ArrayList<>();
    private final List<AppItem> monitorApps = new ArrayList<>();
    private final List<AppItem> filteredApps = new ArrayList<>();
    private final List<AppItem> filteredMonitorApps = new ArrayList<>();
    private final Runnable monitorTick = this::scheduleMonitorRefresh;

    private AppAdapter appAdapter;
    private MonitorAdapter monitorAdapter;
    private EditText search;
    private EditText monitorSearch;
    private TextView appTab;
    private TextView monitorTab;
    private TextView logTab;
    private TextView appStatus;
    private TextView monitorStatus;
    private TextView logStatus;
    private TextView logText;
    private TextView runningValue;
    private TextView frozenValue;
    private TextView processValue;
    private ProgressBar appProgress;
    private ProgressBar monitorProgress;
    private FrameLayout content;
    private View appPage;
    private View monitorPage;
    private View logPage;
    private boolean monitoring;
    private boolean monitorRefreshPending;
    private boolean activityVisible;
    private int appFilterType;
    private TextView appFilterAction;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AndroidRuntime.init(this);
        applyThemeColors();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(isDarkMode() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildUi();
        worker.execute(() -> {
            ConfigManager.readConfig();
            loadApps();
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(8), dp(20), 0);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));
        root.addView(buildTabs(), new LinearLayout.LayoutParams(-1, dp(52)));

        content = new FrameLayout(this);
        appPage = buildAppPage();
        monitorPage = buildMonitorPage();
        logPage = buildLogPage();
        content.addView(appPage, new FrameLayout.LayoutParams(-1, -1));
        content.addView(monitorPage, new FrameLayout.LayoutParams(-1, -1));
        content.addView(logPage, new FrameLayout.LayoutParams(-1, -1));
        monitorPage.setVisibility(View.GONE);
        logPage.setVisibility(View.GONE);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void applyThemeColors() {
        boolean dark = isDarkMode();
        if (dark) {
            BG = Color.rgb(17, 19, 24);
            SURFACE = Color.rgb(28, 31, 39);
            TEXT = Color.rgb(239, 242, 248);
            MUTED = Color.rgb(166, 175, 190);
            BORDER = Color.rgb(55, 61, 73);
            ACCENT = Color.rgb(145, 145, 255);
            GREEN = Color.rgb(91, 207, 145);
            ORANGE = Color.rgb(246, 170, 86);
            RED = Color.rgb(255, 125, 125);
        } else {
            BG = Color.rgb(246, 247, 251);
            SURFACE = Color.WHITE;
            TEXT = Color.rgb(28, 35, 48);
            MUTED = Color.rgb(105, 115, 132);
            BORDER = Color.rgb(226, 231, 239);
            ACCENT = Color.rgb(79, 70, 229);
            GREEN = Color.rgb(23, 132, 83);
            ORANGE = Color.rgb(205, 112, 25);
            RED = Color.rgb(191, 63, 63);
        }
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(2), 0, dp(8));

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Cirno", 25, true);
        titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(AndroidPolicy.isClover() ? "MI PAD 4" : "ANDROID", 10, true);
        badge.setTextColor(ACCENT);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(0x164F46E5, 0, 12));
        badge.setPadding(dp(10), 0, dp(10), 0);
        titleLine.addView(badge, new LinearLayout.LayoutParams(-2, dp(28)));
        header.addView(titleLine);

        TextView subtitle = text("轻量冻结管理 · 状态可验证", 12, false);
        subtitle.setTextColor(MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        header.addView(subtitle);
        return header;
    }

    private View buildTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(round(SURFACE, BORDER, 14));
        appTab = tab("应用配置");
        monitorTab = tab("实时监控");
        logTab = tab("环形日志");
        tabs.addView(appTab, new LinearLayout.LayoutParams(0, -1, 1));
        tabs.addView(monitorTab, new LinearLayout.LayoutParams(0, -1, 1));
        tabs.addView(logTab, new LinearLayout.LayoutParams(0, -1, 1));
        appTab.setOnClickListener(v -> showPage(false));
        monitorTab.setOnClickListener(v -> showPage(true));
        logTab.setOnClickListener(v -> showLogPage());
        updateTabStyle(0);
        return tabs;
    }

    private View buildAppPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(12), 0, 0);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("搜索应用名称或包名");
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(round(SURFACE, BORDER, 16));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        page.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, dp(6));
        appStatus = text("读取应用列表…", 12, false);
        appStatus.setTextColor(MUTED);
        actions.addView(appStatus, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = action("刷新");
        refresh.setOnClickListener(v -> loadApps());
        actions.addView(refresh, new LinearLayout.LayoutParams(dp(76), dp(36)));
        appFilterAction = action("用户应用");
        appFilterAction.setOnClickListener(v -> showAppFilterPicker());
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(92), dp(36));
        filterParams.leftMargin = dp(8);
        actions.addView(appFilterAction, filterParams);
        TextView settings = action("策略");
        settings.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams settingParams = new LinearLayout.LayoutParams(dp(76), dp(36));
        settingParams.leftMargin = dp(8);
        actions.addView(settings, settingParams);
        page.addView(actions);

        appProgress = new ProgressBar(this);
        appProgress.setVisibility(View.GONE);
        page.addView(appProgress, new LinearLayout.LayoutParams(-1, dp(3)));
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, dp(2), 0, dp(14));
        appAdapter = new AppAdapter();
        list.setAdapter(appAdapter);
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private View buildMonitorPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(10), 0, 0);

        monitorSearch = new EditText(this);
        monitorSearch.setSingleLine(true);
        monitorSearch.setTextSize(14);
        monitorSearch.setTextColor(TEXT);
        monitorSearch.setHintTextColor(MUTED);
        monitorSearch.setHint("搜索冻结应用或包名");
        monitorSearch.setPadding(dp(16), 0, dp(16), 0);
        monitorSearch.setBackground(round(SURFACE, BORDER, 16));
        monitorSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterMonitorApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        page.addView(monitorSearch, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout summary = new LinearLayout(this);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(metricCard("运行中", runningValue = text("—", 22, true), ACCENT), metricParams());
        summary.addView(metricCard("已冻结进程", frozenValue = text("—", 22, true), GREEN), metricParams());
        summary.addView(metricCard("进程冻结率", processValue = text("—", 22, true), ORANGE), metricParams());
        page.addView(summary);

        LinearLayout monitorLine = new LinearLayout(this);
        monitorLine.setGravity(Gravity.CENTER_VERTICAL);
        monitorLine.setPadding(0, dp(10), 0, dp(8));
        monitorStatus = text("等待监控数据", 12, false);
        monitorStatus.setTextColor(MUTED);
        monitorLine.addView(monitorStatus, new LinearLayout.LayoutParams(0, -2, 1));
        monitorProgress = new ProgressBar(this);
        monitorProgress.setVisibility(View.GONE);
        monitorLine.addView(monitorProgress, new LinearLayout.LayoutParams(dp(22), dp(22)));
        page.addView(monitorLine);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(14));
        monitorAdapter = new MonitorAdapter();
        list.setAdapter(monitorAdapter);
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private View buildLogPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(10), 0, 0);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, 0, 0, dp(8));
        logStatus = text("最近 4 MB · 最多 10000 行", 12, false);
        logStatus.setTextColor(MUTED);
        actions.addView(logStatus, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = action("刷新");
        refresh.setOnClickListener(v -> loadLogs());
        actions.addView(refresh, new LinearLayout.LayoutParams(dp(76), dp(36)));
        page.addView(actions);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(round(SURFACE, BORDER, 16));
        logText = text("读取环形日志…", 12, false);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextColor(TEXT);
        logText.setGravity(Gravity.TOP | Gravity.START);
        logText.setPadding(dp(14), dp(14), dp(14), dp(14));
        scroll.addView(logText, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private LinearLayout.LayoutParams metricParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(82), 1);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        return params;
    }

    private View metricCard(String label, TextView value, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), 0, dp(8), 0);
        card.setBackground(round(SURFACE, 0, 16));
        value.setTextColor(accent);
        card.addView(value, new LinearLayout.LayoutParams(-1, dp(35)));
        TextView caption = text(label, 11, false);
        caption.setTextColor(MUTED);
        card.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private TextView tab(String label) {
        TextView view = text(label, 14, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), 0, dp(4), 0);
        return view;
    }

    private TextView action(String label) {
        TextView view = text(label, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(ACCENT);
        view.setBackground(round(0x124F46E5, 0, 12));
        return view;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(TEXT);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void showPage(boolean monitor) {
        appPage.setVisibility(monitor ? View.GONE : View.VISIBLE);
        monitorPage.setVisibility(monitor ? View.VISIBLE : View.GONE);
        logPage.setVisibility(View.GONE);
        updateTabStyle(monitor ? 1 : 0);
        monitoring = monitor;
        if (monitor) {
            loadMonitor(true);
            mainHandler.removeCallbacks(monitorTick);
            if (activityVisible) mainHandler.postDelayed(monitorTick, MONITOR_REFRESH_VISIBLE_MS);
        } else {
            mainHandler.removeCallbacks(monitorTick);
        }
    }

    private void showLogPage() {
        appPage.setVisibility(View.GONE);
        monitorPage.setVisibility(View.GONE);
        logPage.setVisibility(View.VISIBLE);
        monitoring = false;
        mainHandler.removeCallbacks(monitorTick);
        updateTabStyle(2);
        loadLogs();
    }

    private void updateTabStyle(int selected) {
        appTab.setTextColor(selected == 0 ? ACCENT : MUTED);
        monitorTab.setTextColor(selected == 1 ? ACCENT : MUTED);
        logTab.setTextColor(selected == 2 ? ACCENT : MUTED);
        appTab.setTextColor(selected == 0 ? Color.WHITE : MUTED);
        monitorTab.setTextColor(selected == 1 ? Color.WHITE : MUTED);
        logTab.setTextColor(selected == 2 ? Color.WHITE : MUTED);
        appTab.setBackground(round(selected == 0 ? ACCENT : Color.TRANSPARENT, 0, 10));
        monitorTab.setBackground(round(selected == 1 ? ACCENT : Color.TRANSPARENT, 0, 10));
        logTab.setBackground(round(selected == 2 ? ACCENT : Color.TRANSPARENT, 0, 10));
    }

    private void loadLogs() {
        logStatus.setText("读取中…");
        worker.execute(() -> {
            String content = readRingLog();
            runOnUiThread(() -> {
                logText.setText(content);
                logStatus.setText(content.startsWith("无法读取")
                        ? "读取失败"
                        : "环形日志 · 最近 4 MB · 自动保留最新内容");
            });
        });
    }

    private String readRingLog() {
        try {
            SuFile file = new SuFile(GlobalVars.LOG_DIR, "current.log");
            if (!file.exists() || file.length() == 0L) {
                return "暂无日志\n\n请确认 Hook 已启用，并在日志级别中开启信息或调试。";
            }
            long length = file.length();
            long offset = Math.max(0L, length - 4L * 1024L * 1024L);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = SuFileInputStream.open(file)) {
                long remaining = offset;
                while (remaining > 0L) {
                    long skipped = input.skip(remaining);
                    if (skipped <= 0L) break;
                    remaining -= skipped;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
            String value = new String(output.toByteArray(), StandardCharsets.UTF_8);
            if (offset > 0L) value = value.substring(value.indexOf('\n') + 1);
            String[] lines = value.split("\\R");
            int start = Math.max(0, lines.length - 10000);
            StringBuilder result = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) result.append(lines[i]).append('\n');
            }
            return result.length() == 0 ? "暂无日志" : result.toString();
        } catch (Throwable error) {
            return "无法读取环形日志：" + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private void loadApps() {
        runOnUiThread(() -> {
            appProgress.setVisibility(View.VISIBLE);
            appStatus.setText("正在读取应用…");
        });
        worker.execute(() -> {
            ConfigManager.readConfig();
            List<AppItem> result;
            try { result = PackageUtils.filter(3); }
            catch (Throwable error) { result = new ArrayList<>(); }
            List<AppItem> loaded = result;
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(loaded);
                filterApps(search == null ? "" : search.getText().toString());
                appProgress.setVisibility(View.GONE);
                appStatus.setText(filteredApps.size() + " 个匹配应用 · 点击卡片查看配置");
            });
        });
    }

    private void filterApps(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredApps.clear();
        for (AppItem item : apps) {
            String name = item.appName == null ? "" : item.appName.toLowerCase(Locale.ROOT);
            String pkg = item.packageName == null ? "" : item.packageName.toLowerCase(Locale.ROOT);
            boolean system = PackageUtils.isSystemUIChecker(this, item.packageInfo);
            boolean typeMatch = appFilterType == 0 ? !system : system;
            if (typeMatch && (needle.isEmpty() || name.contains(needle) || pkg.contains(needle))) filteredApps.add(item);
        }
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
    }

    private void showAppFilterPicker() {
        String[] options = {"用户应用", "系统应用"};
        new AlertDialog.Builder(this)
                .setTitle("应用类型")
                .setSingleChoiceItems(options, appFilterType, (dialog, which) -> {
                    appFilterType = which;
                    appFilterAction.setText(options[which]);
                    filterApps(search == null ? "" : search.getText().toString());
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void filterMonitorApps(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredMonitorApps.clear();
        for (AppItem item : monitorApps) {
            String name = item.appName == null ? "" : item.appName.toLowerCase(Locale.ROOT);
            String pkg = item.packageName == null ? "" : item.packageName.toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || name.contains(needle) || pkg.contains(needle)) filteredMonitorApps.add(item);
        }
        if (monitorAdapter != null) monitorAdapter.notifyDataSetChanged();
    }

    private void loadMonitor(boolean showLoading) {
        if (monitorRefreshPending) return;
        monitorRefreshPending = true;
        if (showLoading) monitorProgress.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            List<AppItem> result;
            try { result = PackageUtils.getFrozenApplication(AndroidRuntime.context()); }
            catch (Throwable error) { result = new ArrayList<>(); }
            List<AppItem> loaded = result;
            runOnUiThread(() -> {
                monitorRefreshPending = false;
                monitorApps.clear();
                monitorApps.addAll(loaded);
                filterMonitorApps(monitorSearch == null ? "" : monitorSearch.getText().toString());
                updateMonitorSummary();
                monitorAdapter.notifyDataSetChanged();
                monitorProgress.setVisibility(View.GONE);
                monitorStatus.setText("实时刷新 · " + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date())
                        + " · " + freezeTypeSummary());
            });
        });
    }

    private void scheduleMonitorRefresh() {
        if (!monitoring || !activityVisible) return;
        loadMonitor(false);
        mainHandler.postDelayed(monitorTick, MONITOR_REFRESH_VISIBLE_MS);
    }

    private void updateMonitorSummary() {
        int frozenApps = 0;
        int frozenProcesses = 0;
        int totalProcesses = 0;
        int v1 = 0;
        int v2 = 0;
        for (AppItem item : monitorApps) {
            if (item.isFrozen) frozenApps++;
            if (item.isFrozen && "V1".equalsIgnoreCase(item.frozenType)) v1++;
            if (item.isFrozen && "V2".equalsIgnoreCase(item.frozenType)) v2++;
            frozenProcesses += item.frozenProcessCount;
            totalProcesses += item.applicationProcessCount;
        }
        runningValue.setText(String.valueOf(monitorApps.size()));
        frozenValue.setText(frozenProcesses + "/" + totalProcesses);
        monitorStatus.setText("V1 " + v1 + " · V2 " + v2 + " · 冻结应用 " + frozenApps);
        processValue.setText(totalProcesses == 0 ? "—" : Math.round(frozenProcesses * 100f / totalProcesses) + "%");
    }

    private String freezeTypeSummary() {
        int v1 = 0;
        int v2 = 0;
        for (AppItem item : monitorApps) {
            if (!item.isFrozen) continue;
            if ("V1".equalsIgnoreCase(item.frozenType)) v1++;
            if ("V2".equalsIgnoreCase(item.frozenType)) v2++;
        }
        return "V1 " + v1 + " · V2 " + v2;
    }

    private void showSettings() {
        int delay = GlobalVars.globalSettings == null ? 5 : GlobalVars.globalSettings.freezeDelay;
        new AlertDialog.Builder(this)
                .setTitle("低功耗策略")
                .setMessage("clover 设备已启用 Android 原生策略核心。\n\n冻结防抖：" + delay
                        + " 秒\n前台、音频、定位、录音、VPN 和活跃网络进程会自动豁免。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private String appConfigSummary(AppItem item) {
        StringBuilder summary = new StringBuilder(item.packageName);
        if (item.userId != 0) summary.append("#").append(item.userId);
        List<String> tags = new ArrayList<>();
        if (item.black) tags.add("冻结名单");
        if (item.white) tags.add("白名单");
        if (item.backgroundPlay) tags.add("后台播放");
        if (item.locationCheck != 0) tags.add("定位");
        if (item.networkCheck) tags.add("网络消息");
        if (item.networkSpeedEnabled) tags.add("网速");
        if (item.blockAutostart) tags.add("自启动");
        if (item.processConfig) tags.add("进程");
        if (item.memoryTrimConfig || item.memoryTrimGcConfig) tags.add("内存");
        if (AppConfigs.isValidBackgroundOomAdj(item.backgroundOomAdj)) tags.add("OOM " + item.backgroundOomAdj);
        if (!tags.isEmpty()) summary.append(" · ").append(String.join(" / ", tags));
        return summary.toString();
    }

    private void showMonitorDetailsLegacy(AppItem item) {
        String type = item.frozenType == null || item.frozenType.isEmpty() ? "未知类型" : item.frozenType;
        String status = item.isFrozen ? "已冻结 · " + type : "未冻结 · " + shortReason(item.notFrozenReason);
        String message = status
                + "\n\n进程：" + item.frozenProcessCount + "/" + item.applicationProcessCount + " 已冻结"
                + "\nCPU：" + String.format(Locale.ROOT, "%.2f%%", item.cpuUsage)
                + "\nRSS：" + formatMemory(item.rss)
                + "\n压缩：" + item.compactedProcessCount + " 个进程";
        new AlertDialog.Builder(this)
                .setTitle(item.appName == null ? item.packageName : item.appName)
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showMonitorDetails(AppItem item) {
        String type = item.isFrozen && item.frozenType != null && !item.frozenType.isEmpty()
                ? item.frozenType : "未知";
        String state = item.isFrozen ? "已冻结 · " + type : "未冻结 · " + shortReason(item.notFrozenReason);
        String message = state
                + "\n\n进程：" + item.frozenProcessCount + "/" + item.applicationProcessCount + " 已冻结"
                + "\n冻结类型：" + type
                + "\nCPU：" + String.format(Locale.ROOT, "%.2f%%", item.cpuUsage)
                + "\nRSS：" + formatMemory(item.rss)
                + "\n压缩：" + item.compactedProcessCount + " 个进程";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.appName == null ? item.packageName : item.appName)
                .setMessage(message)
                .setPositiveButton("关闭", null);
        if (item.networkSpeedEnabled) {
            builder.setNeutralButton("查看网速", (dialog, which) -> loadNetworkSpeed(item));
        }
        builder.show();
    }

    private void loadNetworkSpeed(AppItem item) {
        worker.execute(() -> {
            String result = "网速获取失败";
            try {
                ApplicationBinderFacade binder = ApplicationBinder.getInstance();
                if (binder != null) {
                    String json = binder.getNetworkSpeed(item.packageName, item.userId);
                    long rx = parseLongField(json, "rx");
                    long tx = parseLongField(json, "tx");
                    result = "↑" + formatSpeed(tx) + "  ↓" + formatSpeed(rx);
                }
            } catch (Throwable ignored) {
            }
            String message = result;
            runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        });
    }

    private long parseLongField(String json, String field) {
        if (json == null) return 0L;
        int start = json.indexOf("\"" + field + "\"");
        if (start < 0) return 0L;
        String value = json.substring(start + field.length() + 3);
        int end = value.indexOf(',');
        if (end < 0) end = value.indexOf('}');
        try { return Long.parseLong(value.substring(0, end).trim()); } catch (Throwable ignored) { return 0L; }
    }

    private String formatSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        if (bytesPerSec < 1024 * 1024) {
            return BigDecimal.valueOf(bytesPerSec).divide(BigDecimal.valueOf(1024), 1, RoundingMode.HALF_UP) + " KB/s";
        }
        return BigDecimal.valueOf(bytesPerSec).divide(BigDecimal.valueOf(1048576), 2, RoundingMode.HALF_UP) + " MB/s";
    }

    private void showAppConfigLegacy(AppItem item) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(4), dp(20), dp(4));
        TextView packageText = text(item.packageName + "  ·  用户 " + item.userId, 12, false);
        packageText.setTextColor(MUTED);
        body.addView(packageText, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text("开关会立即写入 root 配置；冻结名单可在列表开关快速切换。", 12, false);
        hint.setTextColor(MUTED);
        hint.setPadding(0, dp(6), 0, dp(8));
        body.addView(hint);

        addConfigToggle(body, "冻结名单", item.black, enabled -> AppConfigs.setBlackApp(item.packageName, item.userId, enabled));
        addConfigToggle(body, "白名单（完全豁免）", item.white, enabled -> AppConfigs.setWhiteApp(item.packageName, item.userId, enabled));
        addConfigToggle(body, "允许后台播放", item.backgroundPlay, enabled -> AppConfigs.setBackgroundPlayAllowed(item.packageName, item.userId, enabled));
        addConfigToggle(body, "允许定位", item.locationCheck != 0, enabled -> AppConfigs.setLocationUseAllowed(item.packageName, item.userId, enabled));
        addConfigToggle(body, "允许网络消息", item.networkCheck, enabled -> AppConfigs.setNetworkMessageAllowed(item.packageName, item.userId, enabled));
        addConfigToggle(body, "显示网络速度", item.networkSpeedEnabled, enabled -> AppConfigs.setNetworkSpeedAllowed(item.packageName, item.userId, enabled));
        addConfigToggle(body, "拦截后台自启动", item.blockAutostart, enabled -> AppConfigs.setAutostartBlocked(item.packageName, item.userId, enabled));
        addConfigToggle(body, "禁用内存 Trim", item.memoryTrimConfig, enabled -> AppConfigs.setMemoryTrimEnabled(item.packageName, item.userId, !enabled));
        addConfigToggle(body, "禁用 Trim GC", item.memoryTrimGcConfig, enabled -> AppConfigs.setMemoryTrimGcEnabled(item.packageName, item.userId, !enabled));

        EditText oom = new EditText(this);
        oom.setSingleLine(true);
        oom.setTextSize(14);
        oom.setTextColor(TEXT);
        oom.setHintTextColor(MUTED);
        oom.setHint("默认");
        oom.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (AppConfigs.isValidBackgroundOomAdj(item.backgroundOomAdj)) oom.setText(String.valueOf(item.backgroundOomAdj));
        oom.setSelectAllOnFocus(true);
        body.addView(label("后台 OOM Adj（0-999，留空恢复默认）"));
        body.addView(oom, new LinearLayout.LayoutParams(-1, dp(48)));

        String excluded = AppConfigs.getExcludedProcesses(item.packageName, item.userId).toString();
        TextView process = text("冻结进程排除：" + (excluded.equals("[]") ? "无" : excluded), 12, false);
        process.setTextColor(MUTED);
        process.setPadding(0, dp(10), 0, dp(4));
        body.addView(process);
        scroll.addView(body);

        new AlertDialog.Builder(this)
                .setTitle(item.appName == null ? item.packageName : item.appName)
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .setPositiveButton("保存 OOM", (dialog, which) -> saveOomAdj(item, oom.getText().toString()))
                .show();
    }

    private void showAppConfig(AppItem item) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(4), dp(20), dp(4));

        TextView packageText = text(item.packageName + " · 用户 " + item.userId, 12, false);
        packageText.setTextColor(MUTED);
        body.addView(packageText);
        TextView hint = text("所有开关均写入原版 root 配置；白名单会关闭其他豁免项。", 12, false);
        hint.setTextColor(MUTED);
        hint.setPadding(0, dp(6), 0, dp(8));
        body.addView(hint);

        boolean systemApp = PackageUtils.isSystemUIChecker(this, item.packageInfo);
        boolean builtinWhitelist = CommonConstants.isWhitelistApps(item.packageName);
        if (!systemApp) {
            addConfigToggle(body, "白名单（完全豁免）", builtinWhitelist || item.white,
                    !builtinWhitelist, enabled -> {
                        if (builtinWhitelist) return;
                        AppConfigs.setWhiteApp(item.packageName, item.userId, enabled);
                        if (enabled) {
                            AppConfigs.setBackgroundPlayAllowed(item.packageName, item.userId, false);
                            AppConfigs.setLocationUseAllowed(item.packageName, item.userId, false);
                            AppConfigs.setNetworkMessageAllowed(item.packageName, item.userId, false);
                            AppConfigs.setNetworkSpeedAllowed(item.packageName, item.userId, false);
                        }
                    });
        }

        boolean exemptionsEnabled = !builtinWhitelist && !item.white;
        if (!builtinWhitelist && (!systemApp || item.black)) {
            addConfigToggle(body, "允许后台播放", item.backgroundPlay, exemptionsEnabled,
                    enabled -> AppConfigs.setBackgroundPlayAllowed(item.packageName, item.userId, enabled));
            addConfigToggle(body, "允许定位", item.locationCheck != 0, exemptionsEnabled,
                    enabled -> AppConfigs.setLocationUseAllowed(item.packageName, item.userId, enabled));
            addConfigToggle(body, "允许网络消息", item.networkCheck, exemptionsEnabled,
                    enabled -> AppConfigs.setNetworkMessageAllowed(item.packageName, item.userId, enabled));
            addConfigToggle(body, "显示网络速度", item.networkSpeedEnabled, exemptionsEnabled,
                    enabled -> AppConfigs.setNetworkSpeedAllowed(item.packageName, item.userId, enabled));
        }

        addConfigToggle(body, "拦截后台自启动", item.blockAutostart, exemptionsEnabled,
                enabled -> AppConfigs.setAutostartBlocked(item.packageName, item.userId, enabled));

        boolean globalTrim = GlobalVars.globalSettings != null && GlobalVars.globalSettings.memoryTrimEnabled;
        addConfigToggle(body, "冻结后回收内存", AppConfigs.isMemoryTrimEnabled(item.packageName, item.userId), globalTrim,
                enabled -> AppConfigs.setMemoryTrimEnabled(item.packageName, item.userId, enabled));
        boolean globalGc = globalTrim && GlobalVars.globalSettings.memoryTrimGcEnabled;
        addConfigToggle(body, "回收后触发 GC", AppConfigs.isMemoryTrimGcEnabled(item.packageName, item.userId), globalGc,
                enabled -> AppConfigs.setMemoryTrimGcEnabled(item.packageName, item.userId, enabled));

        TextView oom = text("后台 OOM Adj · " + oomLabel(AppConfigs.getBackgroundOomAdj(item.packageName, item.userId)), 14, false);
        oom.setTextColor(TEXT);
        oom.setGravity(Gravity.CENTER_VERTICAL);
        oom.setPadding(0, dp(4), 0, dp(4));
        oom.setOnClickListener(v -> showOomPicker(item, oom));
        body.addView(oom, new LinearLayout.LayoutParams(-1, dp(52)));

        addConfigToggle(body, "冻结名单", item.black, true,
                enabled -> AppConfigs.setBlackApp(item.packageName, item.userId, enabled));

        TextView processTitle = text("进程冻结控制", 14, true);
        processTitle.setTextColor(TEXT);
        processTitle.setPadding(0, dp(12), 0, dp(2));
        body.addView(processTitle);
        TextView processHint = text("开启后该进程会被冻结，关闭后该进程不会被冻结。读取中…", 12, false);
        processHint.setTextColor(MUTED);
        body.addView(processHint);
        LinearLayout processContainer = new LinearLayout(this);
        processContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(processContainer);
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.appName == null ? item.packageName : item.appName)
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .create();
        dialog.show();
        worker.execute(() -> {
            List<String> processNames = loadProcessNames(item);
            runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                processHint.setText(processNames.isEmpty() ? "暂无可配置进程" : "每个进程可单独排除冻结");
                for (String processName : processNames) {
                    boolean excluded = AppConfigs.isProcessExcludedFromFreeze(item.packageName, item.userId, processName);
                    addConfigToggle(processContainer, processName, !excluded, true, enabled ->
                            AppConfigs.setProcessExcludedFromFreeze(item.packageName, item.userId, processName, !enabled));
                }
            });
        });
    }

    private List<String> loadProcessNames(AppItem item) {
        List<String> names = new ArrayList<>();
        try {
            ApplicationBinderFacade binder = ApplicationBinder.getInstance();
            if (binder == null) return names;
            String json = binder.getProcessesForApp(item.packageName, item.userId);
            if (json == null) return names;
            String value = json.trim();
            if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
            for (String token : value.split(",")) {
                String name = token.trim().replace("\\\"", "");
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    private String oomLabel(int adj) {
        if (!AppConfigs.isValidBackgroundOomAdj(adj)) return "默认";
        return String.valueOf(adj);
    }

    private void showOomPicker(AppItem item, TextView target) {
        String[] labels = {"默认", "保持活动（0）", "可见（100）", "可感知（200）", "服务（500）",
                "服务 B（800）", "缓存（900）", "低优先级（999）", "自定义"};
        int[] values = {AppConfigs.BACKGROUND_OOM_ADJ_DEFAULT, 0, 100, 200, 500, 800, 900, 999};
        int current = AppConfigs.getBackgroundOomAdj(item.packageName, item.userId);
        int checked = 0;
        for (int i = 1; i < values.length; i++) if (values[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("后台 OOM Adj")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which == labels.length - 1) {
                        dialog.dismiss();
                        showCustomOom(item, target);
                        return;
                    }
                    saveOomValue(item, values[which], target);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomOom(AppItem item, TextView target) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("0–999");
        int current = AppConfigs.getBackgroundOomAdj(item.packageName, item.userId);
        if (AppConfigs.isValidBackgroundOomAdj(current)) input.setText(String.valueOf(current));
        new AlertDialog.Builder(this)
                .setTitle("自定义后台 OOM Adj")
                .setMessage("取值范围：0–999；输入空值恢复默认")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    int adj = AppConfigs.BACKGROUND_OOM_ADJ_DEFAULT;
                    if (!value.isEmpty()) {
                        try { adj = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
                    }
                    if (!AppConfigs.isValidBackgroundOomAdj(adj) && adj != AppConfigs.BACKGROUND_OOM_ADJ_DEFAULT) {
                        Toast.makeText(this, "OOM Adj 必须是 0–999", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveOomValue(item, adj, target);
                })
                .show();
    }

    private void saveOomValue(AppItem item, int adj, TextView target) {
        worker.execute(() -> {
            AppConfigs.setBackgroundOomAdj(item.packageName, item.userId, adj);
            ConfigManager.manager.saveConfigSU();
            runOnUiThread(() -> {
                target.setText("后台 OOM Adj · " + oomLabel(adj));
                Toast.makeText(this, "OOM Adj 已保存", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private TextView label(String value) {
        TextView view = text(value, 12, false);
        view.setTextColor(MUTED);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    private void addConfigToggle(LinearLayout parent, String title, boolean checked, ConfigMutation mutation) {
        addConfigToggle(parent, title, checked, true, mutation);
    }

    private void addConfigToggle(LinearLayout parent, String title, boolean checked, boolean toggleEnabled,
                                 ConfigMutation mutation) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextSize(14);
        toggle.setTextColor(TEXT);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setChecked(checked);
        toggle.setEnabled(toggleEnabled);
        toggle.setPadding(0, dp(2), 0, dp(2));
        toggle.setOnCheckedChangeListener((button, enabled) -> worker.execute(() -> {
            mutation.apply(enabled);
            ConfigManager.manager.saveConfigSU();
        }));
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void saveOomAdj(AppItem item, String value) {
        worker.execute(() -> {
            int adj = AppConfigs.BACKGROUND_OOM_ADJ_DEFAULT;
            if (value != null && !value.trim().isEmpty()) {
                try { adj = Integer.parseInt(value.trim()); } catch (NumberFormatException ignored) { }
            }
            AppConfigs.setBackgroundOomAdj(item.packageName, item.userId, adj);
            ConfigManager.manager.saveConfigSU();
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "应用配置已保存", Toast.LENGTH_SHORT).show());
        });
    }

    private interface ConfigMutation {
        void apply(boolean enabled);
    }

    private String shortReasonLegacy(String reason) {
        if (reason == null || reason.isEmpty()) return "状态未知";
        return reason.replace('_', ' ');
    }

    private String shortReason(String reason) {
        if (reason == null || reason.isEmpty()) return "状态未知";
        return reason.replace('_', ' ');
    }

    private String formatMemory(long rssKb) {
        if (rssKb < 1024) return rssKb + " KB";
        return BigDecimal.valueOf(rssKb).divide(BigDecimal.valueOf(1024), 1, RoundingMode.HALF_UP) + " MB";
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityVisible = true;
        if (monitoring) {
            loadMonitor(false);
            mainHandler.removeCallbacks(monitorTick);
            mainHandler.postDelayed(monitorTick, MONITOR_REFRESH_VISIBLE_MS);
        }
    }

    @Override
    protected void onStop() {
        activityVisible = false;
        mainHandler.removeCallbacks(monitorTick);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        monitoring = false;
        mainHandler.removeCallbacks(monitorTick);
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public AppItem getItem(int position) { return filteredApps.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AppItem item = getItem(position);
            LinearLayout row = makeRow(recycled);
            addAppIdentity(row, item, false);
            Switch freeze = new Switch(MainActivity.this);
            freeze.setText("冻结");
            freeze.setTextSize(12);
            freeze.setTextColor(MUTED);
            freeze.setChecked(item.black);
            freeze.setOnClickListener(v -> worker.execute(() -> {
                item.black = freeze.isChecked();
                AppConfigs.setBlackApp(item.packageName, item.userId, item.black);
                ConfigManager.manager.saveConfigSU();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "已保存 " + item.packageName, Toast.LENGTH_SHORT).show());
            }));
            row.addView(freeze, new LinearLayout.LayoutParams(-2, -2));
            row.setOnClickListener(v -> showAppConfig(item));
            return row;
        }
    }

    private final class MonitorAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredMonitorApps.size(); }
        @Override public AppItem getItem(int position) { return filteredMonitorApps.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AppItem item = getItem(position);
            LinearLayout row = makeRow(recycled);
            addAppIdentity(row, item, true);
            String type = item.frozenType == null || item.frozenType.isEmpty() ? "未知" : item.frozenType;
            TextView state = text(item.isFrozen ? "已冻结 · " + type : "未冻结", 12, true);
            state.setTextColor(item.isFrozen ? GREEN : ORANGE);
            state.setGravity(Gravity.CENTER);
            state.setBackground(round(item.isFrozen ? 0x1636A269 : 0x16CD7019, 0, 10));
            state.setPadding(dp(8), 0, dp(8), 0);
            row.addView(state, new LinearLayout.LayoutParams(-2, dp(30)));
            row.setOnClickListener(v -> showMonitorDetails(item));
            row.setOnLongClickListener(v -> {
                showAppConfig(item);
                return true;
            });
            return row;
        }
    }

    private LinearLayout makeRow(View recycled) {
        LinearLayout row = recycled instanceof LinearLayout ? (LinearLayout) recycled : new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setMinimumHeight(dp(72));
        row.setBackground(round(SURFACE, BORDER, 16));
        row.removeAllViews();
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private void addAppIdentity(LinearLayout row, AppItem item, boolean monitor) {
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(item.appIcon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(item.appName == null ? item.packageName : item.appName, 15, true);
        TextView detail;
        if (monitor) {
            String type = item.isFrozen && item.frozenType != null ? " · " + item.frozenType : "";
            detail = text(item.frozenProcessCount + "/" + item.applicationProcessCount + " 进程" + type + " · "
                    + String.format(Locale.ROOT, "%.2f%% CPU · %s", item.cpuUsage, formatMemory(item.rss)), 11, false);
        } else {
            detail = text(appConfigSummary(item), 11, false);
        }
        detail.setTextColor(MUTED);
        labels.addView(name);
        labels.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    }
}
