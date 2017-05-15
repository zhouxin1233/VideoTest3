package com.ctrip.videotest3.video.widget;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.ctrip.videotest3.R;
import com.ctrip.videotest3.video.constant.PlayState;
import com.ctrip.videotest3.video.constant.SeekBarState;
import com.ctrip.videotest3.video.constant.VideoUriProtocol;
import com.ctrip.videotest3.video.controller.AnimationImpl;
import com.ctrip.videotest3.video.controller.IControllerImpl;
import com.ctrip.videotest3.video.controller.IPlayerImpl;
import com.ctrip.videotest3.video.controller.ITitleBarImpl;
import com.ctrip.videotest3.video.util.ClickUtils;
import com.ctrip.videotest3.video.util.DensityUtil;
import com.ctrip.videotest3.video.util.NetworkUtil;
import com.ctrip.videotest3.video.util.OrientationUtil;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by zxz on 2016/4/28.
 */
public class VideoPlayer extends RelativeLayout implements View.OnTouchListener {
    private static final String TAG = "===VideoPlayer";
    private PlayerTitleBar mTitleBar;
    private ZZVideoView mVv;
    private PlayerController mController;
    private Uri mVideoUri;
    private String mVideoProtocol;//视频地址所用协议
    private Animation mEnterFromTop,mEnterFromBottom,mExitFromTop,mExitFromBottom;

    private int mCurrentPlayState = PlayState.IDLE;
    private static final int UPDATE_TIMER_INTERVAL = 1000;
    private static final int TIME_AUTO_HIDE_BARS_DELAY = 3000;
    private static final int MSG_UPDATE_PROGRESS_TIME = 1;//更新播放进度时间

    /**隐藏标题栏和控制条*/
    private static final int MSG_AUTO_HIDE_BARS = 2;
    private double FLING_MIN_VELOCITY = 5;
    private double FLING_MIN_DISTANCE = 10;
    private double MIN_CHANGE_VOLUME_DISTANCE = FLING_MIN_DISTANCE * 10;
    private Timer mUpdateTimer = null;
    private WeakReference<Activity> mHostActivity; // 宿主绑定的activity todo:zx1 这里如果是fragment呢
    private BroadcastReceiver mNetworkReceiver;
    private boolean mOnPrepared;
    private boolean mIsOnlineSource=false;//是否是网络视频 默认为本地的 false
    private View mRlPlayerContainer;//整个播放器总布局
    private AudioManager mAudioManager;
    private FrameLayout mFlLoading;
    private float lastDownY = 0;
    private int mDuration = 0;//视频长度
    /** 断网时获取的已缓冲长度 从-1开始,用于加载前就断网,此时通过方法getBufferLength()得到的是0,不便于判断*/
    private int mLastBufferLength = -1;
    private int mLastPlayingPos = -1;//onPause时的播放位置
    public int mLastUpdateTime = 0;//上一次updateTimer更新的播放时间值
    private GestureDetector mGestureDetector;
    private Handler mHandler;
    private ITitleBarImpl mTitleBarImpl = new ITitleBarImpl() {
        @Override
        public void onBackClick() {
            if (mIPlayerImpl != null) {
                mIPlayerImpl.onBack();
            } else {
                mHostActivity.get().finish();
            }
        }
    };

    private GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            lastDownY = e.getY();
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (ClickUtils.isTouchEventValid()) {//是否为单击
                mHandler.removeMessages(MSG_AUTO_HIDE_BARS);
                if (mController.getVisibility() == VISIBLE) {
                    showOrHideBars(false, true);
                } else {
                    showOrHideBars(true, true);
                }
            }
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            int width = mRlPlayerContainer.getWidth();
            int top = mRlPlayerContainer.getTop();
            int left = mRlPlayerContainer.getLeft();
            int bottom = mRlPlayerContainer.getBottom();
            if (e2.getY() <= top || e2.getY() >= bottom) {//限制触摸区域
                return false;
            }
            float deltaY = lastDownY - e2.getY();
            // Log.i(TAG, "onScroll deltaY = " + deltaY + "  ,lastDownY= " + lastDownY + ",e2.getY() = " + e2.getY());
            if (e1.getX() < left + width/2) {//调整亮度 左边距离中间偏60
                if (deltaY > FLING_MIN_DISTANCE && Math.abs(distanceY) > FLING_MIN_VELOCITY) {
                    setScreenBrightness(20);
                } else if (deltaY < -1 * FLING_MIN_DISTANCE && Math.abs(distanceY) > FLING_MIN_VELOCITY) {
                    setScreenBrightness(-20);
                } else {
                    return false;
                }
            } else if (e1.getX()>left+width/2 ){//调整音量
                if (deltaY > MIN_CHANGE_VOLUME_DISTANCE) {
                    setVoiceVolume(true);
                } else if (deltaY < MIN_CHANGE_VOLUME_DISTANCE * -1) {
                    setVoiceVolume(false);
                } else {
                    return false;
                }
            }
            lastDownY = e2.getY();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return true;
        }
    };

    /**
     * 更新播放器状态
     * @param playState {@link PlayState}
     */
    private void updatePlayState(int playState) {
        mCurrentPlayState = playState;
        mController.setPlayUI(playState);
    }

    /**底部控制栏的相关 点击的回调*/
    private IControllerImpl mControllerImpl = new IControllerImpl() {
        @Override
        public void onPlayTurn() {
            //网络不正常时,不允许切换,本地视频则跳过这一步
            if (VideoUriProtocol.PROTOCOL_HTTP.equalsIgnoreCase(mVideoProtocol)&& !NetworkUtil.isNetworkAvailable()) {
                mIPlayerImpl.onNetWorkError();
                return;
            }
            if (mVv.isPlaying()){//播放-->暂停
                setPlayerState(PlayState.PAUSE);
            }else{  //暂停-->播放
                setPlayerState(PlayState.PLAY);
            }
            sendAutoHideBarsMsg();
        }

        @Override
        public void onProgressChange(int state, int progress) {
            switch (state) {
                case SeekBarState.START_TRACKING:
                    mHandler.removeMessages(MSG_AUTO_HIDE_BARS);
                    break;
                case SeekBarState.STOP_TRACKING:
                    if (mOnPrepared && isPlaying()) {
                        isLoading(true);
                        mVv.seekTo(progress);
                        sendAutoHideBarsMsg();
                    }
                    break;
            }
        }

        @Override
        public void onOrientationChange() {
            OrientationUtil.changeOrientation(mHostActivity.get());
        }
    };

    /**
     * 设置播放器的播放状态
     * @param currentPlayState 当前状态
     */
    private void setPlayerState(int currentPlayState){
        updatePlayState(currentPlayState);// 更新播放状态 并更改UI
        switch (currentPlayState) {
            case PlayState.PLAY: // 播放/重新播放
                startOrRestartPlay();
                break;
            case PlayState.IDLE:
            case PlayState.PREPARE:
            case PlayState.PAUSE://播放暂停
                pausePlay();
            case PlayState.COMPLETE:
            case PlayState.STOP://播放停止
                break;
            case PlayState.ERROR:
                break;
        }

    }

    class MyHandler extends Handler{
        private WeakReference<Activity> mWeakReference;

        public MyHandler(Activity activity) {
            mWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (mWeakReference.get()!=null){
                switch (msg.what){
                    case MSG_UPDATE_PROGRESS_TIME:
                        if (NetworkUtil.isNetworkAvailable()) {
                            mLastBufferLength = -1;
                        }
                        mLastPlayingPos = getCurrentTime();
                        if (mCurrentPlayState == PlayState.COMPLETE) {
                            mLastPlayingPos = 0;
                        }
                        mController.updateProgress(mLastPlayingPos, getBufferProgress());
                        mVv.setBackgroundColor(Color.TRANSPARENT);
                        Log.i(TAG, "mLastPlayingPos: "+mLastPlayingPos+" , getBufferProgress(): "+getBufferProgress());
                        break;
                    case MSG_AUTO_HIDE_BARS:
                        animateShowOrHideBars(false);
                        break;
                }
            }
        }
    }

    public boolean isPlaying() {
        try {
            return mVv.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            // 这里再设定zOrder就已经无效了
            //            mVv.setZOrderOnTop(false);

            // 在这里去掉背景的话,需要延时下,不然还是会有瞬间的透明色
            // 我放到更新进度条的时候再来去掉背景了
            //            mVv.setBackgroundColor(Color.TRANSPARENT);
            //            mVv.setBackgroundResource(0);
            mOnPrepared = true;
            updatePlayState(PlayState.PREPARE);
            mDuration = mp.getDuration();
            mController.updateProgress(mLastUpdateTime, 0, mDuration);
            sendAutoHideBarsMsg();
        }
    };

    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Log.e(TAG, "MediaPlayer.OnErrorListener what = " + what + " , extra = " + extra + " ,mNetworkAvailable:" + NetworkUtil.isNetworkAvailable() + " ,mCurrentPlayState:" + mCurrentPlayState);
            if (mCurrentPlayState != PlayState.ERROR) {
                //  判断网络状态,如果有网络,则重新加载播放,如果没有则报错
                if ((mIsOnlineSource && NetworkUtil.isNetworkAvailable()) || !mIsOnlineSource) {
                    startOrRestartPlay();
                } else {
                    if (mIPlayerImpl != null) {
                        mIPlayerImpl.onError();
                    }
                    mOnPrepared = false;
                    updatePlayState(PlayState.ERROR);
                }
            }
            return true;
        }
    };
    private MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            mLastPlayingPos = 0;
            mLastBufferLength = -1;
            mController.updateProgress(0, 100);
            stopUpdateTimer();
            updatePlayState(PlayState.COMPLETE);
            if (mIPlayerImpl != null) {
                mIPlayerImpl.onComplete();
            }
        }
    };
    /**
     * 播放器控制功能对外开放接口,包括返回按钮,播放等...
     */
    public void setPlayerController(IPlayerImpl IPlayerImpl) {
        mIPlayerImpl = IPlayerImpl;
    }

    private IPlayerImpl mIPlayerImpl = null;

    public VideoPlayer(Context context) {
        super(context);
        initView(context);
    }

    public VideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }
    private void initView(Context context) {
        mHandler=new MyHandler((Activity)context);
        inflate(context, R.layout.zz_video_player, this);
        mRlPlayerContainer = findViewById(R.id.rl_player);
        mVv = (ZZVideoView) findViewById(R.id.zzvv_main);
        mTitleBar = (PlayerTitleBar) findViewById(R.id.pt_title_bar);
        mController = (PlayerController) findViewById(R.id.pc_controller);

        mFlLoading = (FrameLayout) findViewById(R.id.fl_loading);
        initAnimation(context);

        mTitleBar.setTitleBarImpl(mTitleBarImpl);
        mController.setControllerImpl(mControllerImpl);
        mVv.setOnTouchListener(this);
        mRlPlayerContainer.setOnTouchListener(this);
        mVv.setOnPreparedListener(mPreparedListener);
        mVv.setOnCompletionListener(mCompletionListener);
        mVv.setOnErrorListener(mErrorListener);

        mGestureDetector = new GestureDetector(context, mGestureListener);
        mRlPlayerContainer.setOnTouchListener(this);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * 初始化标题栏/控制栏显隐动画效果
     */
    private void initAnimation(Context context) {
        mEnterFromTop = AnimationUtils.loadAnimation(context, R.anim.enter_from_top);
        mEnterFromBottom = AnimationUtils.loadAnimation(context, R.anim.enter_from_bottom);
        mExitFromTop = AnimationUtils.loadAnimation(context, R.anim.exit_from_top);
        mExitFromBottom = AnimationUtils.loadAnimation(context, R.anim.exit_from_bottom);
        mEnterFromTop.setAnimationListener(new AnimationImpl() {
            @Override
            public void onAnimationEnd(Animation animation) {
                super.onAnimationEnd(animation);
                mTitleBar.setVisibility(VISIBLE);
            }
        });
        mEnterFromBottom.setAnimationListener(new AnimationImpl() {
            @Override
            public void onAnimationEnd(Animation animation) {
                super.onAnimationEnd(animation);
                mController.setVisibility(VISIBLE);
            }
        });
        mExitFromTop.setAnimationListener(new AnimationImpl() {
            @Override
            public void onAnimationEnd(Animation animation) {
                super.onAnimationEnd(animation);
                mTitleBar.setVisibility(GONE);
            }
        });
        mExitFromBottom.setAnimationListener(new AnimationImpl() {
            @Override
            public void onAnimationEnd(Animation animation) {
                super.onAnimationEnd(animation);
                mController.setVisibility(GONE);
            }
        });
    }

    /**
     * 设置视频标题
     */
    public void setTitle(String title) {
        mTitleBar.setTitle(title);
    }

    /**加载视频 在这里才真正把视频路径设置到VideoView中 */
    private void loadData() {
        // 处理加载过程中,断网,再联网,如果重新设置video路径,videoView会去reset mediaPlayer,可能出错
        // TODO: 2016/7/15 郁闷了,不重新设置的话,断网播放到缓冲尽头又联网时,没法继续加载播放,矛盾啊,先备注下
        if (mIsOnlineSource) {
            if (!NetworkUtil.isNetworkAvailable()) {
                Log.i(TAG, "loadData failed because network not available");
                return;
            }
            mVv.setVideoPath(mVideoUri.toString());
        } else if (VideoUriProtocol.PROTOCOL_ANDROID_RESOURCE.equalsIgnoreCase(mVideoProtocol)) {
            mVv.setVideoURI(mVideoUri);
        }
    }

    /**开始播放或重新加载播放*/
    public void startOrRestartPlay() {
        if (mLastBufferLength >= 0 && mIsOnlineSource) {
            resumeFromError();
        } else {
            goPlay();
        }
    }

    public void resumeFromError() {
        loadData();
        mVv.start();
        mVv.seekTo(mLastPlayingPos);
        updatePlayState(PlayState.PLAY);
        resetUpdateTimer();
    }

    public void goPlay() {
        // 在线视频,网络异常时,不进行加载播放
        if (mIsOnlineSource && !NetworkUtil.isNetworkAvailable()) {
            return;
        }
        mVv.start();
        if (mCurrentPlayState == PlayState.COMPLETE) {
            mVv.seekTo(0);
        }

        resetUpdateTimer();
    }

    /**暂停播放*/
    public void pausePlay() {
        if ((mCurrentPlayState != PlayState.ERROR) && mOnPrepared && isPlaying() && mVv.canPause()) {
            mVv.pause();
        }
    }
    /**
     * 设置视频播放路径
     * 1. 设置当前项目中res/raw目录中的文件: "android.resource://" + getPackageName() + "/" + R.raw.yourName
     * 2. 设置网络视频文件: "http:\//****\/abc.mp4"
     */
    public void loadAndStartVideo(@NonNull Activity act, @NonNull String path) {
        mHostActivity = new WeakReference<>(act);
        mVideoUri = Uri.parse(path);
        mVideoProtocol = mVideoUri.getScheme();
        if (VideoUriProtocol.PROTOCOL_HTTP.equalsIgnoreCase(mVideoProtocol)) {
            mIsOnlineSource = true;
        }
        initNetworkMonitor();
        loadData();
        setPlayerState(PlayState.PLAY);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                sendAutoHideBarsMsg();
                lastDownY = 0;
                break;
        }
        //        return false;
        return mGestureDetector.onTouchEvent(event);
    }
    /**
     * 显隐标题栏和控制条
     * @param show          是否显示
     * @param animateEffect 是否需要动画效果
     */
    private void showOrHideBars(boolean show, boolean animateEffect) {
        if (animateEffect) {
            animateShowOrHideBars(show);
        } else {
            forceShowOrHideBars(show);
        }
    }
    /**直接 显/隐 标题栏和控制栏*/
    private void forceShowOrHideBars(boolean show) {
        mTitleBar.clearAnimation();
        mController.clearAnimation();
        if (show) {
            mController.setVisibility(VISIBLE);
            mTitleBar.setVisibility(VISIBLE);
        } else {
            mController.setVisibility(GONE);
            mTitleBar.setVisibility(GONE);
        }
    }

    /**带动画效果的显隐标题栏和控制栏*/
    private void animateShowOrHideBars(boolean show) {
        mTitleBar.clearAnimation();
        mController.clearAnimation();
        if (show) {
            if (mTitleBar.getVisibility() != VISIBLE) {
                mTitleBar.startAnimation(mEnterFromTop);
                mController.startAnimation(mEnterFromBottom);
            }
        } else {
            if (mTitleBar.getVisibility() != GONE) {
                mTitleBar.startAnimation(mExitFromTop);
                mController.startAnimation(mExitFromBottom);
            }
        }
    }

    private void resetUpdateTimer() {
        stopUpdateTimer();
        mUpdateTimer = new Timer();
        mUpdateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // 播放结束(onComplete)后,点击播放按钮,开始播放时初次读取到的时间值是视频结束位置
                int currentUpdateTime = getCurrentTime();
                if (currentUpdateTime >= 1000 && Math.abs(currentUpdateTime - mLastUpdateTime) >= 800) {
                    mHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS_TIME);
                    mLastUpdateTime = currentUpdateTime;
                    mLastPlayingPos = 0;
                    mCurrentPlayState = PlayState.PLAY;
                    isLoading(false);
                } else {
                    isLoading(true);
                }
            }
        }, 0, UPDATE_TIMER_INTERVAL);
    }

    /**
     * 停止更新进度时间的timer
     */
    private void stopUpdateTimer() {
        isLoading(false);
        if (mUpdateTimer != null) {
            mUpdateTimer.cancel();
            mUpdateTimer = null;
        }
    }

    private int getCurrentTime() {
        return mVv.getCurrentPosition();
    }

    /**
     * @return 缓冲百分比 0-100
     */
    private int getBufferProgress() {
        Log.i(TAG, "getBufferPercentage(): "+mVv.getBufferPercentage());
        return mIsOnlineSource ? mVv.getBufferPercentage() : 100;
    }

    /**
     * 发送message给handler,3s后自动隐藏标题栏
     */
    private void sendAutoHideBarsMsg() {
        //  初始自动隐藏标题栏和控制栏
        mHandler.removeMessages(MSG_AUTO_HIDE_BARS);
        mHandler.sendEmptyMessageDelayed(MSG_AUTO_HIDE_BARS, TIME_AUTO_HIDE_BARS_DELAY);
    }

    /**
     * 屏幕方向改变时,回调该方法
     */
    public void updateActivityOrientation() {
        int orientation = OrientationUtil.getOrientation(mHostActivity.get());
        //更新播放器宽高
        float width = DensityUtil.getWidthInPx(mHostActivity.get());
        float height = DensityUtil.getHeightInPx(mHostActivity.get());
        if (orientation == OrientationUtil.HORIZONTAL) {
            getLayoutParams().height = (int) height;
            getLayoutParams().width = (int) width;
        } else {
            width = DensityUtil.getWidthInPx(mHostActivity.get());
            height = DensityUtil.dip2px(mHostActivity.get(), 200f);//todo:zx1 这里自定义宽高的时候需要注意
        }
        getLayoutParams().height = (int) height;
        getLayoutParams().width = (int) width;

        //需要强制显示再隐藏控制条,不然若切换为横屏时控制条是隐藏的,首次触摸显示时,会显示在200dp的位置
        forceShowOrHideBars(true);
        sendAutoHideBarsMsg();
        //更新全屏图标
        mController.setOrientation(orientation);
    }

    /**
     * 宿主页面onResume的时候从上次播放位置继续播放
     */
    public void onHostResume() {
        //        Log.i(TAG, "onHostResume " + mLastPlayingPos);
        if (mLastPlayingPos >= 0) {
            // 进度条更新为上次播放时间
            startOrRestartPlay();
            setPlayerState(mCurrentPlayState);
        }
        //强制弹出标题栏和控制栏
        forceShowOrHideBars(true);
        sendAutoHideBarsMsg();
    }

    /**
     * 宿主页面onPause的时候记录播放位置，好在onResume的时候从中断点继续播放
     * 如果在宿主页面onStop的时候才来记录位置,则取到的都会是0
     */
    public void onHostPause() {
        mLastPlayingPos = getCurrentTime();
        getBufferLength();
        stopUpdateTimer();
        mHandler.removeMessages(MSG_UPDATE_PROGRESS_TIME);
        mHandler.removeMessages(MSG_AUTO_HIDE_BARS);
        // 在这里不进行stop或者pause播放的行为，因为特殊情况下会导致ANR出现
    }

    /** 宿主页面destroy的时候页面恢复成竖直状态*/
    public void onHostDestroy() {
        OrientationUtil.forceOrientation(mHostActivity.get(), OrientationUtil.VERTICAL);
        unRegisterNetworkReceiver();
    }

    /** 初始化网络变化监听器*/
    public void initNetworkMonitor() {
        unRegisterNetworkReceiver();
        // 网络变化
        mNetworkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // 网络变化
                if (intent.getAction().equalsIgnoreCase(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    mController.updateNetworkState(NetworkUtil.isNetworkAvailable() || !mIsOnlineSource);
                    if (!NetworkUtil.isNetworkAvailable()) {
                        getBufferLength();
                        mIPlayerImpl.onNetWorkError();
                    } else {
                        if (mCurrentPlayState == PlayState.ERROR) {
                            updatePlayState(PlayState.IDLE);
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mHostActivity.get().registerReceiver(mNetworkReceiver, filter);
    }

    /**
     * 获取已缓冲长度 毫秒
     */
    private int getBufferLength() {
        mLastBufferLength = getBufferProgress() * mDuration / 100;
        Log.i(TAG, "getBufferLength: "+ mLastBufferLength);
        return mLastBufferLength;
    }

    public void unRegisterNetworkReceiver() {
        if (mNetworkReceiver != null) {
            mHostActivity.get().unregisterReceiver(mNetworkReceiver);
            mNetworkReceiver = null;
        }
    }

    /**是否显示加载进度框*/
    private void isLoading(final boolean show) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mFlLoading.setVisibility((show && (mCurrentPlayState == PlayState.PLAY || mCurrentPlayState == PlayState.PREPARE))
                        ? VISIBLE : GONE);
            }
        });
    }

    /**设置当前屏幕亮度值 0--255，并使之生效*/
    private void setScreenBrightness(float value) {
        Activity activity = mHostActivity.get();
        if (activity != null) {
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.screenBrightness = lp.screenBrightness + value / 255.0f;
            Log.i("zhouxin","亮度 :　"+ lp.screenBrightness);
            if (lp.screenBrightness > 1) {
                lp.screenBrightness = 1;
            } else if (lp.screenBrightness < 0.2) {
                lp.screenBrightness = (float) 0.2;
            }
            activity.getWindow().setAttributes(lp);
        }
    }

    private void setVoiceVolume(boolean volumeUp) {
        if (volumeUp) { //降低音量，调出系统音量控制
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE,
                    AudioManager.FX_FOCUS_NAVIGATION_UP);
        } else {//增加音量，调出系统音量控制
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER,
                    AudioManager.FX_FOCUS_NAVIGATION_UP);
        }
    }
}
