package com.iristick.smartglass.examples;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.iristick.smartglass.examples.barcode.BarcodeActivity;
import com.iristick.smartglass.examples.camera.CameraActivity;
import com.iristick.smartglass.examples.displaypresentation.DisplayPresentationActivity;
import com.iristick.smartglass.examples.displaysurface.DisplaySurfaceActivity;
import com.iristick.smartglass.examples.sensorsreadout.SensorsReadoutActivity;
import com.iristick.smartglass.examples.touchpad.TouchpadActivity;
import com.iristick.smartglass.examples.voicegrammar.VoiceGrammarActivity;

public enum Example {
    CAMERA(CameraActivity.class, R.string.camera_title, R.string.camera_description),
    BARCODE(BarcodeActivity.class, R.string.barcode_title, R.string.barcode_description),
    DISPLAY_PRESENTATION(DisplayPresentationActivity.class, R.string.displaypresentation_title, R.string.displaypresentation_description),
    DISPLAY_SURFACE(DisplaySurfaceActivity.class, R.string.displaysurface_title, R.string.displaysurface_description),
    SENSORS_READOUT(SensorsReadoutActivity.class, R.string.sensorsreadout_title, R.string.sensorsreadout_description),
    TOUCHPAD(TouchpadActivity.class, R.string.touchpad_title, R.string.touchpad_description),
    VOICE_GRAMMAR(VoiceGrammarActivity.class, R.string.voicegrammar_title, R.string.voicegrammar_description),
    ;

    @NonNull public final Class<? extends BaseActivity> activity;
    @StringRes public final int title;
    @StringRes public final int description;

    Example(@NonNull Class<? extends BaseActivity> activity, int title, int description) {
        this.activity = activity;
        this.title = title;
        this.description = description;
    }

}
