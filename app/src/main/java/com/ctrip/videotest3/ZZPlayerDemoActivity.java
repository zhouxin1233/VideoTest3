package com.ctrip.videotest3;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.ctrip.videotest3.video.controller.IPlayerImpl;
import com.ctrip.videotest3.video.util.OrientationUtil;
import com.ctrip.videotest3.video.widget.VideoPlayer;


public class ZZPlayerDemoActivity extends Activity {

    private VideoPlayer mVp;
    private String mVideoUrl;
    public static final String TAG="===DemoActivity  ";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);//设置全屏
        setContentView(R.layout.activity_custom_player_demo);

        initData();
        initView();
        initListener();
    }

    private IPlayerImpl playerImpl = new IPlayerImpl() {
        @Override
        public void onNetWorkError() {
            Toast.makeText(App.mApp, App.mApp.getString(R.string.zz_player_network_invalid), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBack() {
            // 全屏播放时,单击左上角返回箭头,先回到竖屏状态,再关闭 这里功能最好跟onBackPressed()操作一致
            int orientation = OrientationUtil.getOrientation(ZZPlayerDemoActivity.this);
            if (orientation == OrientationUtil.HORIZONTAL) {
                OrientationUtil.forceOrientation(ZZPlayerDemoActivity.this, OrientationUtil.VERTICAL);
            } else {
                finish();
            }
        }

        @Override
        public void onError() {
            Toast.makeText(App.mApp, App.mApp.getString(R.string.video_error), Toast.LENGTH_SHORT).show();
        }
    };

    private void initListener() {
        mVp.setPlayerController(playerImpl);
    }

    private void initData() {
                mVideoUrl = "android.resource://" + getPackageName() + "/" + R.raw.av;
//        mVideoUrl = "http://mvideo.spriteapp.cn/video/2017/0513/9875dcc2-37f0-11e7-960a-1866daeb0df1_wpcco.mp4";
//        mVideoUrl = "http://mvideo.spriteapp.cn/video/2017/0410/58eaedff21123_wpcco.mp4";
    }

    private void initView() {
        mVp = (VideoPlayer) findViewById(R.id.vp);
        mVp.setTitle("视频名称");
        mVp.loadAndStartVideo(this, mVideoUrl);
//        mVp.setProgressLayerDrawables(R.drawable.biz_video_progressbar);//自定义的layer-list
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged: ");
        if (mVp != null) {
            mVp.updateActivityOrientation();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume: ");
        mVp.onHostResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: ");
        mVp.onHostPause();
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed: ");

        mVp.onHostDestroy();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy: ");
        mVp.onHostDestroy();
    }
}
