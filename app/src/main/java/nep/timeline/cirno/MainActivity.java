package nep.timeline.cirno;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nep.timeline.cirno.configs.ConfigManager;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppItem;
import nep.timeline.cirno.nativecore.AppRuntime;
import nep.timeline.cirno.nativecore.NativePolicy;
import nep.timeline.cirno.utils.PackageUtils;

/** Native Android UI: app configuration plus a live freezer-effect monitor. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT = Color.rgb(28, 35, 48);
    private static final int MUTED = Color.rgb(105, 115, 132);
    private static final int BORDER = Color.rgb(226, 231, 239);
    private static final int ACCENT = Color.rgb(79, 70, 229);
    private static final int GREEN = Color.rgb(23, 132, 83);
    private static final int ORANGE = Color.rgb(205, 112, 25);
    private static final int RED = Color.rgb(191, 63, 63);

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Cirno-UI");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AppItem> apps = new ArrayList<>();
    private final List<AppItem> monitorApps = new ArrayList<>();
    private final List<AppItem> filteredApps = new ArrayList<>();
    private final Runnable monitorTick = this::scheduleMonitorRefresh;

    private AppAdapter appAdapter;
    private MonitorAdapter monitorAdapter;
    private EditText search;
    private TextView appTab;
    private TextView monitorTab;
    private TextView appStatus;
    private TextView monitorStatus;
    private TextView runningValue;
    private TextView frozenValue;
    private TextView processValue;
    private ProgressBar appProgress;
    private ProgressBar monitorProgress;
    private FrameLayout content;
    private View appPage;
    private View monitorPage;
    private boolean monitoring;
    private boolean monitorRefreshPending;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppRuntime.init(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
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
        root.setPadding(dp(18), dp(12), dp(18), 0);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));
        root.addView(buildTabs(), new LinearLayout.LayoutParams(-1, dp(52)));

        content = new FrameLayout(this);
        appPage = buildAppPage();
        monitorPage = buildMonitorPage();
        content.addView(appPage, new FrameLayout.LayoutParams(-1, -1));
        content.addView(monitorPage, new FrameLayout.LayoutParams(-1, -1));
        monitorPage.setVisibility(View.GONE);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, dp(12));

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Cirno", 28, true);
        titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(NativePolicy.isClover() ? "CLOVER" : "ANDROID", 11, true);
        badge.setTextColor(ACCENT);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(0x1A4F46E5, 0, 12));
        badge.setPadding(dp(10), 0, dp(10), 0);
        titleLine.addView(badge, new LinearLayout.LayoutParams(-2, dp(28)));
        header.addView(titleLine);

        TextView subtitle = text("低占用冻结 · 实时确认进程状态", 13, false);
        subtitle.setTextColor(MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        header.addView(subtitle);
        return header;
    }

    private View buildTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, 0, 0, dp(10));
        tabs.setBackground(round(SURFACE, 0, 16));
        appTab = tab("应用配置");
        monitorTab = tab("实时监控");
        tabs.addView(appTab, new LinearLayout.LayoutParams(0, -1, 1));
        tabs.addView(monitorTab, new LinearLayout.LayoutParams(0, -1, 1));
        appTab.setOnClickListener(v -> showPage(false));
        monitorTab.setOnClickListener(v -> showPage(true));
        updateTabStyle(false);
        return tabs;
    }

    private View buildAppPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(10), 0, 0);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("搜索应用名称或包名");
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(round(SURFACE, BORDER, 14));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        page.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, dp(8));
        appStatus = text("读取应用列表…", 12, false);
        appStatus.setTextColor(MUTED);
        actions.addView(appStatus, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = action("刷新");
        refresh.setOnClickListener(v -> loadApps());
        actions.addView(refresh, new LinearLayout.LayoutParams(dp(76), dp(36)));
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
        updateTabStyle(monitor);
        monitoring = monitor;
        if (monitor) {
            loadMonitor(true);
            mainHandler.removeCallbacks(monitorTick);
            mainHandler.postDelayed(monitorTick, 1500L);
        } else {
            mainHandler.removeCallbacks(monitorTick);
        }
    }

    private void updateTabStyle(boolean monitor) {
        appTab.setTextColor(monitor ? MUTED : ACCENT);
        monitorTab.setTextColor(monitor ? ACCENT : MUTED);
        appTab.setBackground(round(monitor ? SURFACE : 0x164F46E5, 0, 12));
        monitorTab.setBackground(round(monitor ? 0x164F46E5 : SURFACE, 0, 12));
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
                appStatus.setText(loaded.size() + " 个应用 · 点击开关加入冻结名单");
            });
        });
    }

    private void filterApps(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredApps.clear();
        for (AppItem item : apps) {
            String name = item.appName == null ? "" : item.appName.toLowerCase(Locale.ROOT);
            String pkg = item.packageName == null ? "" : item.packageName.toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || name.contains(needle) || pkg.contains(needle)) filteredApps.add(item);
        }
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
    }

    private void loadMonitor(boolean showLoading) {
        if (monitorRefreshPending) return;
        monitorRefreshPending = true;
        if (showLoading) monitorProgress.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            List<AppItem> result;
            try { result = PackageUtils.getFrozenApplication(AppRuntime.context()); }
            catch (Throwable error) { result = new ArrayList<>(); }
            List<AppItem> loaded = result;
            runOnUiThread(() -> {
                monitorRefreshPending = false;
                monitorApps.clear();
                monitorApps.addAll(loaded);
                updateMonitorSummary();
                monitorAdapter.notifyDataSetChanged();
                monitorProgress.setVisibility(View.GONE);
                monitorStatus.setText("实时刷新 · " + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date())
                        + " · 点击应用查看冻结详情");
            });
        });
    }

    private void scheduleMonitorRefresh() {
        if (!monitoring) return;
        loadMonitor(false);
        mainHandler.postDelayed(monitorTick, 1500L);
    }

    private void updateMonitorSummary() {
        int frozenApps = 0;
        int frozenProcesses = 0;
        int totalProcesses = 0;
        for (AppItem item : monitorApps) {
            if (item.isFrozen) frozenApps++;
            frozenProcesses += item.frozenProcessCount;
            totalProcesses += item.applicationProcessCount;
        }
        runningValue.setText(String.valueOf(monitorApps.size()));
        frozenValue.setText(frozenProcesses + "/" + totalProcesses);
        processValue.setText(totalProcesses == 0 ? "—" : Math.round(frozenProcesses * 100f / totalProcesses) + "%");
    }

    private void showSettings() {
        int delay = GlobalVars.globalSettings == null ? 5 : GlobalVars.globalSettings.freezeDelay;
        new AlertDialog.Builder(this)
                .setTitle("低功耗策略")
                .setMessage("clover 设备已启用 Rust 策略核心。\n\n冻结防抖：" + delay
                        + " 秒\n前台、音频、定位、录音、VPN 和活跃网络进程会自动豁免。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showMonitorDetails(AppItem item) {
        String status = item.isFrozen ? "已冻结 · V2" : "未冻结 · " + shortReason(item.notFrozenReason);
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
                AppConfigs.setBlackApp(item.packageName, item.userId, freeze.isChecked());
                ConfigManager.manager.saveConfigSU();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "已保存 " + item.packageName, Toast.LENGTH_SHORT).show());
            }));
            row.addView(freeze, new LinearLayout.LayoutParams(-2, -2));
            return row;
        }
    }

    private final class MonitorAdapter extends BaseAdapter {
        @Override public int getCount() { return monitorApps.size(); }
        @Override public AppItem getItem(int position) { return monitorApps.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AppItem item = getItem(position);
            LinearLayout row = makeRow(recycled);
            addAppIdentity(row, item, true);
            TextView state = text(item.isFrozen ? "已冻结" : "未冻结", 12, true);
            state.setTextColor(item.isFrozen ? GREEN : ORANGE);
            state.setGravity(Gravity.CENTER);
            state.setBackground(round(item.isFrozen ? 0x1636A269 : 0x16CD7019, 0, 10));
            state.setPadding(dp(8), 0, dp(8), 0);
            row.addView(state, new LinearLayout.LayoutParams(-2, dp(30)));
            row.setOnClickListener(v -> showMonitorDetails(item));
            return row;
        }
    }

    private LinearLayout makeRow(View recycled) {
        LinearLayout row = recycled instanceof LinearLayout ? (LinearLayout) recycled : new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(SURFACE, 0, 16));
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
            detail = text(item.frozenProcessCount + "/" + item.applicationProcessCount + " 进程 · "
                    + String.format(Locale.ROOT, "%.2f%% CPU · %s", item.cpuUsage, formatMemory(item.rss)), 11, false);
        } else {
            detail = text(item.packageName + "#" + item.userId, 11, false);
        }
        detail.setTextColor(MUTED);
        labels.addView(name);
        labels.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    }
}
