package com.iristick.smartglass.examples.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.TouchEvent;
import com.iristick.smartglass.core.VoiceCommandDispatcher;
import com.iristick.smartglass.examples.BaseActivity;
import com.iristick.smartglass.examples.R;
import com.iristick.smartglass.examples.server.Server;
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
public class CameraActivity extends BaseActivity implements TouchEvent.Callback {

    /* Voice commands */
    private VoiceCommandDispatcher mVoiceCommandDispatcher;

    private Server server;
    private Thread serverThread;

    private boolean torchEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_activity);

        /* To open a camera, the app needs the Android CAMERA permission. */
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 0);

        /* Create the dispatcher for voice commands. */
        mVoiceCommandDispatcher = VoiceCommandDispatcher.Builder.create(this)
                .add(R.string.camera_voice_focus, this::triggerAF1)
                .add(R.string.camera_voice_reset, this::resetSettings1)
                .add(R.string.camera_voice_zoom_in, () -> zoom1(2.0f))
                .add(R.string.camera_voice_zoom_out, () -> zoom1(0.5f))
                .build();

        startServer();
    }

    private void startServer() {
        server = new Server(getCameraFragment0(), getCameraFragment1());
        serverThread = new Thread(server);
        serverThread.start();
    }

    private void stopServer() {
        if (server != null) {
            server.stopServer();

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (serverThread != null) {
                serverThread.interrupt();
                serverThread = null;
            }
        }
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

        Headset headset = IristickApp.getHeadset();

        if (headset != null) {
            headset.registerTouchEventCallback(this, null, Headset.TOUCHPAD_FLAG_OVERRIDE_ALL);
        }

        if (serverThread == null) {
            startServer();
        }
    }

    @Override
    protected void onPause() {
        /* Always stop voice commands in onPause. */
        IristickApp.stopVoice(mVoiceCommandDispatcher);
        super.onPause();

        Headset headset = IristickApp.getHeadset();

        if (headset != null) {
            headset.unregisterTouchEventCallback(this);
        }

        stopServer();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Utility methods

    private CameraFragment getCameraFragment1() {
        return (CameraFragment) getSupportFragmentManager().findFragmentById(R.id.camera1);
    }

    private CameraFragment getCameraFragment0() {
        return (CameraFragment) getSupportFragmentManager().findFragmentById(R.id.camera0);
    }

    private void triggerAF1() {
        CameraFragment fragment = getCameraFragment1();
        if (fragment == null)
            return;
        fragment.triggerAF();
    }

    private void triggerAF0() {
        CameraFragment fragment = getCameraFragment0();
        if (fragment == null)
            return;
        fragment.triggerAF();
    }

    private void zoom1(float factor) {
        CameraFragment fragment = getCameraFragment1();
        if (fragment == null)
            return;
        fragment.zoom(factor);
    }

    private void zoom0(float factor) {
        CameraFragment fragment = getCameraFragment0();
        if (fragment == null)
            return;
        fragment.zoom(factor);
    }

    private void resetSettings1() {
        CameraFragment fragment = getCameraFragment1();
        if (fragment == null)
            return;
        fragment.resetSettings();
    }

    private void resetSettings0() {
        CameraFragment fragment = getCameraFragment0();
        if (fragment == null)
            return;
        fragment.resetSettings();
    }

    @Override
    public void onTouchEvent(@NonNull TouchEvent event) {
        switch (event.getGestureCode()) {
            case TouchEvent.GESTURE_LONG_TAP:
                break;
            case TouchEvent.GESTURE_TAP:
                triggerAF1();
                triggerAF0();
                break;
            case TouchEvent.GESTURE_DOUBLE_TAP:
                Headset headset = IristickApp.getHeadset();

                if (headset != null) {
                    torchEnabled = !torchEnabled;
                    headset.setTorchMode(torchEnabled);
                }
                break;
            case TouchEvent.GESTURE_SWIPE_FORWARD:
                zoom1(2.0f);
                zoom0(2.0f);
                break;
            case TouchEvent.GESTURE_SWIPE_BACKWARD:
                zoom1(0.5f);
                zoom0(0.5f);
                break;
        }
    }
}
