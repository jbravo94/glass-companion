package dev.heinzl.glasscompanion;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import dev.heinzl.glasscompanion.camera.CameraActivity;

public enum GlassCompanion {
    CAMERA(CameraActivity.class, R.string.camera_title, R.string.camera_description),
    ;

    @NonNull public final Class<? extends BaseActivity> activity;
    @StringRes public final int title;
    @StringRes public final int description;

    GlassCompanion(@NonNull Class<? extends BaseActivity> activity, int title, int description) {
        this.activity = activity;
        this.title = title;
        this.description = description;
    }

}
