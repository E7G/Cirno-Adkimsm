package nep.timeline.cirno;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nep.timeline.cirno.configs.ConfigManager;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppItem;
import nep.timeline.cirno.nativecore.AppRuntime;
import nep.timeline.cirno.nativecore.NativePolicy;
import nep.timeline.cirno.utils.PackageUtils;

/** Low-overhead Android View UI. No Compose tree, blur shader or coroutine loop. */
public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Cirno-UI");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final List<AppItem> apps = new ArrayList<>();
    private AppAdapter adapter;
    private TextView status;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppRuntime.init(this);
        setTitle("Cirno");
        buildUi();
        worker.execute(() -> {
            ConfigManager.readConfig();
            loadApps();
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));

        TextView title = text("Cirno · 低占用墓碑", 22, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        status = text("初始化…", 13, false);
        status.setTextColor(Color.DKGRAY);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> loadApps());
        actions.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = new Button(this);
        settings.setText("低功耗设置");
        settings.setOnClickListener(v -> showSettings());
        actions.addView(settings, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void loadApps() {
        runOnUiThread(() -> {
            progress.setVisibility(View.VISIBLE);
            status.setText("读取应用列表…");
        });
        worker.execute(() -> {
            List<AppItem> result;
            try { result = PackageUtils.filter(3); }
            catch (Throwable error) { result = new ArrayList<>(); }
            List<AppItem> loaded = result;
            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(loaded);
                adapter.notifyDataSetChanged();
                progress.setVisibility(View.GONE);
                status.setText((NativePolicy.isClover() ? "clover/SDM660 · " : "通用设备 · ")
                        + "Rust核心: " + NativePolicy.backend() + " · " + apps.size() + " 个应用");
            });
        });
    }

    private void showSettings() {
        int delay = GlobalVars.globalSettings == null ? 5 : GlobalVars.globalSettings.freezeDelay;
        Toast.makeText(this, "冻结防抖 " + delay + " 秒；clover 默认关闭模糊与高频刷新", Toast.LENGTH_LONG).show();
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(25, 25, 25));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public AppItem getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AppItem item = getItem(position);
            LinearLayout row = recycled instanceof LinearLayout ? (LinearLayout) recycled : new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(4), 0, dp(4));
            row.removeAllViews();
            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(item.appIcon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(item.appName == null ? item.packageName : item.appName, 16, false);
            TextView pkg = text(item.packageName + "#" + item.userId, 11, false);
            pkg.setTextColor(Color.GRAY);
            labels.addView(name);
            labels.addView(pkg);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            Switch freeze = new Switch(MainActivity.this);
            freeze.setText("冻结");
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
}
