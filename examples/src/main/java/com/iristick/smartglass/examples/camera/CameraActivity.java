package com.iristick.smartglass.examples.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.iristick.smartglass.core.VoiceCommandDispatcher;
import com.iristick.smartglass.examples.BaseActivity;
import com.iristick.smartglass.examples.R;
import com.iristick.smartglass.support.app.IristickApp;

/**
 * This example opens both Iristick cameras and shows the captured streams.
 *
 * The stream can be zoomed in with pinch-and-zoom and moved by dragging the image.
 * Click on the info text to reset the settings.
 * For the zoom camera, a tap on the image triggers auto-focus.
 *
 * Most functionality is implemented in {@link CameraFragment}.
 */
public class CameraActivity extends BaseActivity {

    /* Voice commands */
    private VoiceCommandDispatcher mVoiceCommandDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_activity);

        /* To open a camera, the app needs the Android CAMERA permission. */
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 0);

        /* Create the dispatcher for voice commands. */
        mVoiceCommandDispatcher = VoiceCommandDispatcher.Builder.create(this)
                .add(R.string.camera_voice_focus, this::triggerAF)
                .add(R.string.camera_voice_reset, this::resetSettings)
                .add(R.string.camera_voice_zoom_in, () -> zoom(2.0f))
                .add(R.string.camera_voice_zoom_out, () -> zoom(0.5f))
                .build();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Recreate activity to try opening the camera again.
            recreate();
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
            /*
             * This activity cannot work without camera, hence we close the app if the user rejects
             * the permission. A real-world application should fall back to functioning without
             * camera if possible.
             * Check the Android documentation for best practices on permissions at
             * https://developer.android.com/training/permissions/usage-notes
             */
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* Start listening for voice commands. */
        IristickApp.startVoice(mVoiceCommandDispatcher);
    }

    @Override
    protected void onPause() {
        /* Always stop voice commands in onPause. */
        IristickApp.stopVoice(mVoiceCommandDispatcher);
        super.onPause();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Utility methods

    private CameraFragment getCameraFragment() {
        return (CameraFragment) getSupportFragmentManager().findFragmentById(R.id.camera1);
    }

    private void triggerAF() {
        CameraFragment fragment = getCameraFragment();
        if (fragment == null)
            return;
        fragment.triggerAF();
    }

    private void zoom(float factor) {
        CameraFragment fragment = getCameraFragment();
        if (fragment == null)
            return;
        fragment.zoom(factor);
    }

    private void resetSettings() {
        CameraFragment fragment = getCameraFragment();
        if (fragment == null)
            return;
        fragment.resetSettings();
    }
}
