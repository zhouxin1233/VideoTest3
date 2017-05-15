package com.ctrip.videotest3.video.util;

import android.widget.Toast;

import com.ctrip.videotest3.App;

/**
 *
 */

public class ClickUtils {
    public static long mLastDownTime;
    private static final int MIN_CLICK_INTERVAL = 400;//连续两次down事件最小时间间隔(ms)
    /**
     * 判断连续两次触摸事件间隔是否符合要求,避免快速点击等问题
     * @return true 为算单击  false为快速双击
     */
    public static boolean isTouchEventValid(){
        long time = System.currentTimeMillis();
        long value = time - mLastDownTime;
        if(0L < value && value < MIN_CLICK_INTERVAL) {//快速点击 双击
            Toast.makeText(App.mApp, "快速双击", Toast.LENGTH_SHORT).show();
            return false;
        } else {//双击 慢速 算单击
            mLastDownTime = time;
            return true;
        }
    }
}
