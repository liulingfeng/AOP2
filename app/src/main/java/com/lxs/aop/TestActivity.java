package com.lxs.aop;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.lxs.lib.ASMTest;

/**
 * @author liuxiaoshuai
 * @date 2019-11-18
 * @desc
 * @email liulingfeng@mistong.com
 */
public class TestActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @ASMTest
    private void testMe() {

    }
}
