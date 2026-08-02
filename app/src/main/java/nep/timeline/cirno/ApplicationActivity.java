package nep.timeline.cirno;

import android.app.Activity;
import android.os.Bundle;

/** Kept as a lightweight compatibility entry point for existing deep links. */
public final class ApplicationActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new MainActivityCompatView(this));
    }

    private static final class MainActivityCompatView extends android.widget.TextView {
        MainActivityCompatView(android.content.Context context) {
            super(context);
            setText("请从 Cirno 首页选择应用");
            setTextSize(18);
            setPadding(32, 32, 32, 32);
        }
    }
}
