package com.example.floatingclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingClockService extends Service {
    public static final String ACTION_START = "com.example.floatingclock.START";
    public static final String ACTION_STOP = "com.example.floatingclock.STOP";
    public static final String ACTION_SET_COUNTDOWN = "com.example.floatingclock.SET_COUNTDOWN";
    public static final String ACTION_TOGGLE_PAUSE = "com.example.floatingclock.TOGGLE_PAUSE";
    public static final String ACTION_RESET = "com.example.floatingclock.RESET";
    public static final String ACTION_SET_LOCKED = "com.example.floatingclock.SET_LOCKED";
    public static final String ACTION_TOGGLE_LOCKED = "com.example.floatingclock.TOGGLE_LOCKED";
    public static final String EXTRA_MILLIS = "millis";

    public static final String CHANNEL_ID = "floating_clock_ch";
    public static final int NOTIF_ID = 1001;
    public static boolean isRunning = false;

    private WindowManager wm;
    private View root;
    private TextView tvTime;
    private TextView tvCountdown;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private float downRawX, downRawY;
    private int startX, startY;
    private boolean dragging = false;
    private int touchSlop;

    private Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateViews();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 1000L - (now % 1000L) + 20L);
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        prefs = Prefs.p(this);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        touchSlop = (int) (getResources().getDisplayMetrics().density * 6f);
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        addOverlay();
        handler.post(ticker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_SET_COUNTDOWN.equals(action)) {
                long millis = intent.getLongExtra(EXTRA_MILLIS, 0L);
                if (millis > 0) {
                    prefs.edit().putLong(Prefs.KEY_CD_TOTAL, millis)
                            .putLong(Prefs.KEY_CD_REMAIN, millis)
                            .putLong(Prefs.KEY_CD_END, System.currentTimeMillis() + millis)
                            .putBoolean(Prefs.KEY_CD_RUNNING, true).apply();
                }
            } else if (ACTION_TOGGLE_PAUSE.equals(action)) {
                togglePause();
            } else if (ACTION_RESET.equals(action)) {
                long total = prefs.getLong(Prefs.KEY_CD_TOTAL, 0L);
                prefs.edit().putLong(Prefs.KEY_CD_REMAIN, total).putBoolean(Prefs.KEY_CD_RUNNING, false).apply();
            } else if (ACTION_SET_LOCKED.equals(action)) {
                applyLock(prefs.getBoolean(Prefs.KEY_LOCKED, false));
            } else if (ACTION_TOGGLE_LOCKED.equals(action)) {
                boolean locked = !prefs.getBoolean(Prefs.KEY_LOCKED, false);
                prefs.edit().putBoolean(Prefs.KEY_LOCKED, locked).apply();
                applyLock(locked);
                Toast.makeText(this, locked ? "悬浮窗已锁定" : "悬浮窗已解锁", Toast.LENGTH_SHORT).show();
            }
        }
        updateViews();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        isRunning = false;
        super.onDestroy();
    }

    private void addOverlay() {
        root = LayoutInflater.from(this).inflate(R.layout.floating_clock, null);
        tvTime = root.findViewById(R.id.tvTime);
        tvCount
