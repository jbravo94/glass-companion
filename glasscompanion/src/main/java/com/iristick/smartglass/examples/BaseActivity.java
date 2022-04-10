package com.iristick.smartglass.examples;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.iristick.smartglass.support.app.IristickApp;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(IristickApp.wrapContext(newBase));
    }

}
