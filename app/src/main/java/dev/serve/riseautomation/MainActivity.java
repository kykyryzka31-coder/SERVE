package dev.serve.riseautomation;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView status = new TextView(this);
        status.setText(R.string.setup_status);
        status.setPadding(32, 32, 32, 32);
        setContentView(status);
    }
}
