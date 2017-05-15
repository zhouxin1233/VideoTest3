package com.ctrip.videotest3.video.constant;

/**
 * Created by zxz on 2016/5/3.
 * 播放状态
 */
public interface PlayState {
    int IDLE = 0x00;
    int PREPARE = 0x01;
    int PLAY = 0x02;
    int PAUSE = 0x03;
    int COMPLETE = 0x04;
    int STOP = 0x05;
    int ERROR = 0x06;

    /**横竖屏改变*/
    int ORIENTATION_CHANGE=0x07;


    int FLAG_ENABLE_VOLUME_CHANGE = 1;//允许改变音量
    int FLAG_DISABLE_VOLUME_CHANGE = 2;//禁止调节音量
    int FLAG_ENABLE_BRIGHTNESS_CHANGE = 3;//允许改变亮度
    int FLAG_DISABLE_BRIGHTNESS_CHANGE = 4;//禁止调节亮度

}
