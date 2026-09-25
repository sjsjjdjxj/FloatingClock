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
        tvCountdown = root.findViewById(R.id.tvCountdown);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(Prefs.KEY_X, 30);
        params.y = prefs.getInt(Prefs.KEY_Y, 200);

        if (prefs.getBoolean(Prefs.KEY_LOCKED, false)) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }

        wm.addView(root, params);
        setupTouch();
        updateViews();
    }

    private void setupTouch() {
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downRawX;
                        float dy = e.getRawY() - downRawY;
                        if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            dragging = true;
                        }
                        if (dragging) {
                            params.x = (int) (startX + dx);
                            params.y = (int) (startY + dy);
                            try { wm.updateViewLayout(root, params); } catch (Exception ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) {
                            prefs.edit().putInt(Prefs.KEY_X, params.x).putInt(Prefs.KEY_Y, params.y).apply();
                        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                            handleTap((int)e.getRawX(), (int)e.getRawY());
                        }
                        dragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    private void handleTap(int rawX, int rawY) {
        int[] loc = new int[2];
        tvCountdown.getLocationOnScreen(loc);
        if (rawX >= loc[0] && rawX <= loc[0] + tvCountdown.getWidth() &&
            rawY >= loc[1] && rawY <= loc[1] + tvCountdown.getHeight()) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } else {
            cycleUnit();
        }
    }

    private void cycleUnit() {
        int cur = prefs.getInt(Prefs.KEY_UNIT, Prefs.UNIT_SECOND);
        int next = (cur + 1) % 3;
        prefs.edit().putInt(Prefs.KEY_UNIT, next).apply();
        String name = next == Prefs.UNIT_DAY ? "天" : next == Prefs.UNIT_HOUR ? "小时" : "秒";
        Toast.makeText(this, "倒计时单位：" + name, Toast.LENGTH_SHORT).show();
        updateViews();
    }

    private void applyLock(boolean locked) {
        if (params == null) return;
        if (locked) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (root != null && root.isAttachedToWindow()) {
            try { wm.updateViewLayout(root, params); } catch (Exception ignored) {}
        }
    }

    private void updateViews() {
        if (tvTime == null) return;
        tvTime.setText(timeFmt.format(new Date()));
        boolean running = prefs.getBoolean(Prefs.KEY_CD_RUNNING, false);
        long remain;
        if (running) {
            long end = prefs.getLong(Prefs.KEY_CD_END, 0L);
            remain = end - System.currentTimeMillis();
            if (remain <= 0) {
                remain = 0;
                prefs.edit().putBoolean(Prefs.KEY_CD_RUNNING, false).putLong(Prefs.KEY_CD_REMAIN, 0L).apply();
            }
        } else {
            remain = prefs.getLong(Prefs.KEY_CD_REMAIN, 0L);
        }
        tvCountdown.setText(formatCountdown(remain));
    }

    private String formatCountdown(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        int unit = prefs.getInt(Prefs.KEY_UNIT, Prefs.UNIT_SECOND);
        long d = total / 86400;
        long h = (total % 86400) / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (unit == Prefs.UNIT_DAY) {
            return d > 0 ? String.format(Locale.US, "%dd %02d:%02d:%02d", d, h, m, s) :
                    String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
        } else if (unit == Prefs.UNIT_HOUR) {
            return String.format(Locale.US, "%dh %02d:%02d", total / 3600, m, s);
        } else {
            return String.format(Locale.US, "%ds", total);
        }
    }

    private void togglePause() {
        boolean running = prefs.getBoolean(Prefs.KEY_CD_RUNNING, false);
        if (running) {
            long end = prefs.getLong(Prefs.KEY_CD_END, 0L);
            long remain = Math.max(0L, end - System.currentTimeMillis());
            prefs.edit().putLong(Prefs.KEY_CD_REMAIN, remain).putBoolean(Prefs.KEY_CD_RUNNING, false).apply();
        } else {
            long remain = prefs.getLong(Prefs.KEY_CD_REMAIN, 0L);
            if (remain > 0) {
                prefs.edit().putLong(Prefs.KEY_CD_END, System.currentTimeMillis() + remain).putBoolean(Prefs.KEY_CD_RUNNING, true).apply();
            }
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "悬浮时钟", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification() {
        PendingIntent openPi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent lockPi = PendingIntent.getService(this, 1, new Intent(this, FloatingClockService.class).setAction(ACTION_TOGGLE_LOCKED), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPi = PendingIntent.getService(this, 2, new Intent(this, FloatingClockService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("悬浮时钟")
                .setContentText("正在显示悬浮时钟")
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(0, "锁定/解锁", lockPi)
                .addAction(0, "关闭", stopPi)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }
}
