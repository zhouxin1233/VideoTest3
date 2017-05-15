package com.ctrip.videotest3.video.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ctrip.videotest3.R;
import com.ctrip.videotest3.video.constant.PlayState;
import com.ctrip.videotest3.video.constant.SeekBarState;
import com.ctrip.videotest3.video.controller.IControllerImpl;
import com.ctrip.videotest3.video.util.DateUtils;
import com.ctrip.videotest3.video.util.OrientationUtil;

/**
 * 底部栏
 */
public class PlayerController extends FrameLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {
    private IControllerImpl mControllerImpl;
    private CustomSeekBar mCsb;
    private ImageView mIvPlayPause;
    private TextView mTvCurrentTime;
    private TextView mTvTotalTime;
    private ImageView mIvToggleExpandable;
    private int mDuration = 0;//视频长度(ms)
    private boolean mUserOperateSeecbar = false;//用户是否正在操作进度条
    private Drawable[] mProgressLayers = new Drawable[3];
    private LayerDrawable mProgressLayerDrawable;

    public PlayerController(Context context) {
        super(context);
        initView(context);
    }

    public PlayerController(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public PlayerController(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }
    private void initView(Context context) {
        inflate(context, R.layout.zz_video_player_controller, this);
        View rlPlayPause = findViewById(R.id.rl_play_pause);
        mIvPlayPause = (ImageView) findViewById(R.id.iv_play_pause);
        mTvCurrentTime = (TextView) findViewById(R.id.tv_current_time);
        mTvTotalTime = (TextView) findViewById(R.id.tv_total_time);

        mCsb = (CustomSeekBar) findViewById(R.id.csb);
        setProgressLayerDrawables(R.drawable.zz_player_shape_default_background,
                R.drawable.zz_player_shape_default_second_progress,
                R.drawable.zz_player_shape_default_progress);

        mProgressLayerDrawable = new LayerDrawable(mProgressLayers);
        mCsb.setProgressDrawable(mProgressLayerDrawable);

        View rlToggleExpandable = findViewById(R.id.rl_toggle_expandable);
        mIvToggleExpandable = (ImageView) findViewById(R.id.iv_toggle_expandable);

        rlPlayPause.setOnClickListener(this);
        rlToggleExpandable.setOnClickListener(this);
        mIvPlayPause.setOnClickListener(this);
        mCsb.setOnSeekBarChangeListener(this);
    }

    /**
     * 设置控制条功能回调
     */
    public void setControllerImpl(IControllerImpl controllerImpl) {
        this.mControllerImpl = controllerImpl;
    }

    @Override
    public void onClick(View v) {
        if (mControllerImpl == null) {
            return;
        }
        int id = v.getId();
        if (id == R.id.rl_play_pause || id == R.id.iv_play_pause) {
            mControllerImpl.onPlayTurn();
        } else if (id == R.id.iv_toggle_expandable || id == R.id.rl_toggle_expandable) {
            mControllerImpl.onOrientationChange();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            mTvCurrentTime.setText(DateUtils.formatPlayTime(progress));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mControllerImpl.onProgressChange(SeekBarState.START_TRACKING, 0);
        mUserOperateSeecbar = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mControllerImpl.onProgressChange(SeekBarState.STOP_TRACKING, seekBar.getProgress());
        mUserOperateSeecbar = false;

    }

    /**
     * 设置播放状态
     * @param curPlayState 参考
     */
    public void setPlayUI(int curPlayState) {
        switch (curPlayState) {
            case PlayState.PLAY://设置播放状态
                mIvPlayPause.setImageResource(R.drawable.zz_player_play);
                break;
            case PlayState.PAUSE:
            case PlayState.STOP:
            case PlayState.COMPLETE:
            case PlayState.ERROR:
                mIvPlayPause.setImageResource(R.drawable.zz_player_pause);
                break;
        }
    }

    /**
     * 屏幕方向改变时,回调该方法  更新全屏图标
     * @param orientation 新屏幕方向
     */
    public void setOrientation(int orientation) {
        if (orientation == OrientationUtil.HORIZONTAL) {
            mIvToggleExpandable.setImageResource(R.drawable.zz_player_shrink);
        } else {
            mIvToggleExpandable.setImageResource(R.drawable.zz_player_expand);
        }
    }

    /**
     * 更新播放进度
     */
    public void updateProgress(int progress, int secondProgress) {
        updateProgress(progress, secondProgress, mDuration);
    }

    /**
     * 更新播放进度
     */
    public void updateProgress(int progress, int secondProgress, int maxValue) {
        updateProgress(progress, secondProgress, maxValue, mUserOperateSeecbar);
    }

    /**
     * 更新播放进度
     * @param progress       当前进度
     * @param secondProgress 缓冲进度
     * @param maxValue       最大值
     * @param isTracking     用户是否正在操作中
     */
    public void updateProgress(int progress, int secondProgress, int maxValue, boolean isTracking) {
        // 更新播放时间信息
//        initFormatter(maxValue);

        //更新进度条
        mDuration = maxValue;
        mCsb.setMax(maxValue);
        mCsb.setSecondaryProgress(secondProgress * maxValue / 100);

        if (!isTracking) {
            mCsb.setProgress(progress);
            mTvCurrentTime.setText(DateUtils.formatPlayTime(progress));
        }
        mTvTotalTime.setText(DateUtils.formatPlayTime(maxValue));
    }
    public void updateNetworkState(boolean isAvailable) {
        mCsb.setSeekable(isAvailable);
    }

    /**
     * 设置进度条样式
     * @param resId 进度条progressDrawable分层资源
     *              数组表示的进度资源分别为 background - secondaryProgress - progress
     *              若对应的数组元素值 <=0,表示该层素材保持不变;
     *              注意:progress和secondaryProgress的shape资源需要做成clip的,否则会直接完全显示
     */
    public void setProgressLayerDrawables(@DrawableRes int... resId) {
        for (int i = 0; i < resId.length; i++) {
            if (resId[i] > 0 && i < mProgressLayers.length) {
                if (Build.VERSION.SDK_INT >= 21) {
                    mProgressLayers[i] = getResources().getDrawable(resId[i], null);
                } else {
                    mProgressLayers[i] = getResources().getDrawable(resId[i]);
                }
            }
        }
        mProgressLayerDrawable = new LayerDrawable(mProgressLayers);
        if (mCsb != null) {
            mCsb.setProgressDrawable(mProgressLayerDrawable);
        }
    }
}
