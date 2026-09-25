package com.example.floatingclock;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    public static final String NAME = "floating_clock";
    public static final String KEY_X = "win_x";
    public static final String KEY_Y = "win_y";
    public static final String KEY_LOCKED = "locked";
    public static final String KEY_UNIT = "unit";
    public static final String KEY_CD_TOTAL = "cd_total";
    public static final String KEY_CD_END = "cd_end";
    public static final String KEY_CD_REMAIN = "cd_remain";
    public static final String KEY_CD_RUNNING = "cd_running";

    public static final int UNIT_DAY = 0;
    public static final int UNIT_HOUR = 1;
    public static final int UNIT_SECOND = 2;

    public static SharedPreferences p(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
