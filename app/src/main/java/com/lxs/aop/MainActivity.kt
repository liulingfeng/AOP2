package com.lxs.aop

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.lxs.lib.ASMTest

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        test()
    }

    @ASMTest
    fun test() {

    }
}
