package com.ctrip.videotest3.video.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.ctrip.videotest3.App;

/**
 * Created by zxz on 2016/5/3.
 */
public class NetworkUtil {
    /**
     * 网络是否可用
     *
     * @return true-有网络
     */
    public static boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager) App.mApp.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        boolean connected = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            connected = true;
        }
        return connected;
    }
}
