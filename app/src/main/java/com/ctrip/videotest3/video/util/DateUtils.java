package com.ctrip.videotest3.video.util;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by xinzhou on 2017/5/15.
 */

public class DateUtils {

    @SuppressLint("SimpleDateFormat")
    public static String formatPlayTime(long time) {
        SimpleDateFormat mFormatter=null;
        String ZERO_TIME = "00:00";

        if (time <= 0) {
            return ZERO_TIME;
        }

        if (mFormatter == null) {
            if (time >= (59 * 60 * 1000 + 59 * 1000)) {
                mFormatter = new SimpleDateFormat("HH:mm:ss");
            } else {
                mFormatter = new SimpleDateFormat("mm:ss");
            }
            mFormatter.setTimeZone(TimeZone.getTimeZone("GMT+00:00"));
        }

        String timeStr = mFormatter.format(new Date(time));
        if (TextUtils.isEmpty(timeStr)) {
            timeStr = ZERO_TIME;
        }
        return timeStr;
    }

}
