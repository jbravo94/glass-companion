package dev.heinzl.glasscompanion;

import android.app.Application;

import com.iristick.smartglass.support.app.IristickApp;

public class GlassCompanionApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        IristickApp.init(this);
    }

}
