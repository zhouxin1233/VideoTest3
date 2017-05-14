package com.ctrip.videotest3;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.ctrip.videotest3.video.widget.VideoPlayer;

public class MainActivity extends AppCompatActivity{

    private VideoPlayer mVp;
    private String mVideoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);//设置全屏

        setContentView(R.layout.activity_main);
    }

    public void click(View v){
        Intent intent = null;
        switch (v.getId()){
            case R.id.btn_custom:
                intent = new Intent(this, ZZPlayerDemoActivity.class);
                break;
        }
        if (intent != null) {
            startActivity(intent);
        }
    }
}
