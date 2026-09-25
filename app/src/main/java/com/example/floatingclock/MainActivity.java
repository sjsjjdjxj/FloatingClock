package com.example.floatingclock;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText etValue;
    private Spinner spUnit;
    private Switch switchLock;
    private TextView tvStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = Prefs.p(this);
        etValue = findViewById(R.id.etValue);
        spUnit = findViewById(R.id.spUnit);
        switchLock = findViewById(R.id.switchLock);
        tvStatus = findViewById(R.id.tvStatus);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"天", "小时", "秒"});
        spUnit.setAdapter(adapter);
        spUnit.setSelection(prefs.getInt(Prefs.KEY_UNIT, Prefs.UNIT_SECOND));
        spUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(Prefs.KEY_UNIT, position).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        switchLock.setChecked(prefs.getBoolean(Prefs.KEY_LOCKED, false));
        switchLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(Prefs.KEY_LOCKED, isChecked).apply();
            if (FloatingClockService.isRunning) {
                sendAction(FloatingClockService.ACTION_SET_LOCKED);
            }
        });

        findViewById(R.id.btnStart).setOnClickListener(v -> startCountdown());
        findViewById(R.id.btnPause).setOnClickListener(v -> {
            if (ensureOverlay()) sendAction(FloatingClockService.ACTION_TOGGLE_PAUSE);
        });
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            if (ensureOverlay()) sendAction(FloatingClockService.ACTION_RESET);
        });
        findViewById(R.id.btnShow).setOnClickListener(v -> {
            if (ensureOverlay()) sendAction(FloatingClockService.ACTION_START);
        });
        findViewById(R.id.btnHide).setOnClickListener(v -> {
            if (FloatingClockService.isRunning) sendAction(FloatingClockService.ACTION_STOP);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        switchLock.setChecked(prefs.getBoolean(Prefs.KEY_LOCKED, false));
        updateStatus();
    }

    private void updateStatus() {
        String overlay = Settings.canDrawOverlays(this) ? "已授权" : "未授权";
        String running = FloatingClockService.isRunning ? "运行中" : "已停止";
        boolean cdRun = prefs.getBoolean(Prefs.KEY_CD_RUNNING, false);
        long total = prefs.getLong(Prefs.KEY_CD_TOTAL, 0L) / 1000;
        tvStatus.setText("悬浮窗权限：" + overlay + "\n服务状态：" + running +
                "\n倒计时：" + (cdRun ? "进行中" : "已暂停/未开始") + "（设定 " + total + " 秒）");
    }

    private void startCountdown() {
        String valStr = etValue.getText().toString();
        double v;
        try { v = Double.parseDouble(valStr); } catch (Exception e) { v = -1; }
        if (v <= 0) {
            Toast.makeText(this, "请输入大于 0 的数值", Toast.LENGTH_SHORT).show();
            return;
        }
        long millis;
        int pos = spUnit.getSelectedItemPosition();
        if (pos == Prefs.UNIT_DAY) millis = (long) (v * 86400000L);
        else if (pos == Prefs.UNIT_HOUR) millis = (long) (v * 3600000L);
        else millis = (long) (v * 1000L);

        if (!ensureOverlay()) return;
        Intent i = new Intent(this, FloatingClockService.class);
        i.setAction(FloatingClockService.ACTION_SET_COUNTDOWN);
        i.putExtra(FloatingClockService.EXTRA_MILLIS, millis);
        startFg(i);
        Toast.makeText(this, "倒计时已开始", Toast.LENGTH_SHORT).show();
    }

    private boolean ensureOverlay() {
        if (Settings.canDrawOverlays(this)) return true;
        Toast.makeText(this, "请先授予「显示在其他应用上层」权限", Toast.LENGTH_SHORT).show();
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
        return false;
    }

    private void sendAction(String action) {
        startFg(new Intent(this, FloatingClockService.class).setAction(action));
    }

    private void startFg(Intent i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }
}
