package com.iristick.smartglass.examples.barcode;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.Sensor;
import com.iristick.smartglass.core.SensorEventListener;
import com.iristick.smartglass.core.VoiceCommandDispatcher;
import com.iristick.smartglass.core.camera.CameraCharacteristics;
import com.iristick.smartglass.core.camera.CameraDevice;
import com.iristick.smartglass.core.camera.CaptureListener;
import com.iristick.smartglass.core.camera.CaptureListener2;
import com.iristick.smartglass.core.camera.CaptureRequest;
import com.iristick.smartglass.core.camera.CaptureResult;
import com.iristick.smartglass.core.camera.CaptureSession;
import com.iristick.smartglass.examples.BaseActivity;
import com.iristick.smartglass.examples.R;
import com.iristick.smartglass.examples.camera.CameraPreview;
import com.iristick.smartglass.support.app.IristickApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This example opens the zoom Iristick camera and annotates recognized barcodes within the camera
 * preview.
 *
 * The stream can be zoomed in with pinch-and-zoom.
 * Click on the info text to reset the settings.
 *
 * The camera uses laser-assisted auto-focus (if available) based on the movement of the head.
 * When zoomed in, the capture frame offset is set so that the laser pointer is in the middle of the
 * frame.
 *
 * Drawing of the annotations is implemented in {@link ContinuousBarcodeOverlay}.
 */
public class ContinuousBarcodeActivity extends BaseActivity {

    public static final String EXTRA_CAMERA_TYPE = "cameraType";

    private CameraPreview mPreview;
    private ContinuousBarcodeOverlay mOverlay;
    private TextView mInfo;

    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private CameraDevice mCamera;
    private CaptureSession mCaptureSession;
    private Sensor mHeadMotionSensor;

    /* Camera characteristics */
    private int mCameraType;
    private int mAFMode;
    private float mMaxZoom;

    /* Current settings */
    @Nullable private Point mFrameSize;
    private float mZoom = 1.0f;
    @Nullable private Point mOffset;
    private boolean mHeadMovement = true;

    /* Voice commands */
    private VoiceCommandDispatcher mVoiceCommandDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.barcode_continuous_activity);

        mPreview = findViewById(R.id.preview);
        mPreview.setSurfaceTextureListener(mSurfaceTextureListener);
        mPreview.setOnGestureListener(new CameraPreview.OnGestureListener() {
            @Override
            public void onZoom(float factor) {
                zoom(factor);
            }

            @Override
            public void onPan(int dx, int dy) {
            }
        });
        mPreview.setOnClickListener(v -> triggerAF());

        mOverlay = findViewById(R.id.overlay);

        mInfo = findViewById(R.id.info);
        mInfo.setOnClickListener(v -> resetSettings());

        /* To open a camera, the app needs the Android CAMERA permission. */
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 0);

        /* Set default zoom depending on selected camera. */
        mCameraType = getIntent().getIntExtra(EXTRA_CAMERA_TYPE, CameraCharacteristics.TYPE_ZOOM);
        if (mCameraType == CameraCharacteristics.TYPE_WIDE_ANGLE)
            mZoom = 2.0f;

        /* Create the dispatcher for voice commands. */
        mVoiceCommandDispatcher = VoiceCommandDispatcher.Builder.create(this)
                .add(R.string.camera_voice_focus, this::triggerAF)
                .add(R.string.camera_voice_reset, this::resetSettings)
                .add(R.string.camera_voice_zoom_in, () -> zoom(2.0f))
                .add(R.string.camera_voice_zoom_out, () -> zoom(0.5f))
                .build();
    }

    @Override
    protected void onDestroy() {
        mVoiceCommandDispatcher.release();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Recreate activity to try opening the camera again.
            recreate();
        } else {
            Toast.makeText(this, R.string.continuousbarcode_permission_denied, Toast.LENGTH_SHORT).show();
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
    public void onStart() {
        super.onStart();

        /* Query the connected headset. */
        Headset headset = IristickApp.getHeadset();
        if (headset == null) {
            mInfo.setText(R.string.camera_waiting_for_headset);
            return;
        }

        /* Find camera. */
        String cameraId = headset.findCamera(mCameraType);
        if (cameraId == null ||
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mInfo.setText(R.string.camera_not_available);
            return;
        }
        CameraCharacteristics characteristics = headset.getCameraCharacteristics(cameraId);

        /* Find out largest frame size with 4:3 aspect ratio and capable of 30 fps.
         * In contrast with the camera example, we need to know the real frame size that will be
         * used for capturing frames to be able to map barcode locations on the preview. */
        CameraCharacteristics.StreamConfigurationMap streams = Objects.requireNonNull(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP));
        for (Point size : streams.getSizes()) {
            if (Math.abs((float)size.x / (float)size.y - 4.0f/3.0f) < 0.01 &&
                    streams.getMinFrameDuration(size) <= 1000000000L/30) {
                mFrameSize = size;
                break;
            }
        }
        if (mFrameSize == null) {
            /* Failsafe: this should not happen as all cameras should at least have one 4:3 mode
             * capable of 30 fps. */
            mInfo.setText(R.string.camera_not_available);
            return;
        }
        /* Setup the overlay with the chosen frame size */
        mOverlay.setup(mFrameSize.x, mFrameSize.y);

        /* Check whether this camera has auto focus control. */
        mAFMode = CaptureRequest.CONTROL_AF_MODE_OFF;
        int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (afModes != null) {
            for (int afMode : afModes) {
                switch (afMode) {
                case CaptureRequest.CONTROL_AF_MODE_AUTO:
                    if (mAFMode == CaptureRequest.CONTROL_AF_MODE_OFF)
                        mAFMode = afMode;
                    break;
                case CaptureRequest.CONTROL_AF_MODE_LASER_ASSISTED:
                    mAFMode = afMode;
                    break;
                }
            }
        }

        /* Get the maximum digital zoom level. */
        mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_MAX_ZOOM, 1.0f);

        /* Open the camera. */
        headset.openCamera(cameraId, mCameraListener, null);

        /* Enable the laser and start the head motion sensor */
        if (mAFMode != CaptureRequest.CONTROL_AF_MODE_OFF) {
            headset.setLaserPointer(true);
            mHeadMotionSensor = headset.getDefaultSensor(Sensor.TYPE_HEAD_MOTION_DETECT);
            if (mHeadMotionSensor != null) {
                mHeadMotionSensor.registerListener(mHeadMotionListener, 1, null);
            }
        }
    }

    @Override
    public void onStop() {
        /* Close the camera as soon as possible. */
        if (mCamera != null) {
            mCamera.close();
            mCaptureSession = null;
            mCamera = null;
        }
        if (mHeadMotionSensor != null) {
            mHeadMotionSensor.unregisterListener(mHeadMotionListener);
        }
        Headset headset = IristickApp.getHeadset();
        if (headset != null) {
            headset.setLaserPointer(false);
        }
        super.onStop();
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

    /**
     * Set up the TextureView transform matrix to preserve the image aspect ratio.
     * Do nothing if the frame size is unknown.
     */
    private void setupTransform(@NonNull TextureView view) {
        if (mFrameSize == null)
            return;

        float disp_ratio = (float) view.getWidth() / (float) view.getHeight();
        float frame_ratio = (float) mFrameSize.x / (float) mFrameSize.y;
        Matrix transform = new Matrix();
        if (disp_ratio > frame_ratio)
            transform.setScale(frame_ratio/disp_ratio, 1.0f, view.getWidth()/2.0f, view.getHeight()/2.0f);
        else
            transform.setScale(1.0f, disp_ratio/frame_ratio, view.getWidth()/2.0f, view.getHeight()/2.0f);
        view.setTransform(transform);
    }

    /**
     * Try to create a Capture Session, if both the Iristick camera and
     * the texture surface are ready.
     */
    private void createCaptureSession() {
        if (mCamera == null || mSurface == null || mFrameSize == null)
            return;

        /* Set the desired camera resolution. */
        mSurfaceTexture.setDefaultBufferSize(mFrameSize.x, mFrameSize.y);
        setupTransform(mPreview);

        /* Create the capture session. */
        mCaptureSession = null;
        List<Surface> outputs = new ArrayList<>();
        outputs.add(mSurface);
        mCamera.createCaptureSession(outputs, mCaptureSessionListener, null);
    }

    /**
     * Create a capture request with all current settings applied.
     * @param triggerAF True if the request should trigger auto focus (see {@link #triggerAF()}).
     */
    @NonNull
    private CaptureRequest createCaptureRequest(boolean triggerAF) {
        /*
         * Create a builder, specifying the intended use through the template.
         * This sets some sane defaults for our use case.
         */
        CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_BARCODE);

        /* Add target output. */
        builder.addTarget(mSurface);

        /* Set parameters. */
        builder.set(CaptureRequest.SCALER_ZOOM, mZoom);
        if (mOffset != null)
            builder.set(CaptureRequest.SCALER_OFFSET, mOffset);
        builder.set(CaptureRequest.CONTROL_AF_MODE, mAFMode);
        if (triggerAF && mAFMode != CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

        /* Enable asynchronous post processing to ensure a smooth camera preview. */
        builder.set(CaptureRequest.POSTPROCESS_MODE, CaptureRequest.POSTPROCESS_MODE_ASYNCHRONOUS);

        /* Explicitly enable less common barcode formats. */
        List<String> formats = new ArrayList<>();
        String[] defaultFormats = builder.get(CaptureRequest.POSTPROCESS_BARCODE_FORMATS);
        if (defaultFormats != null) {
            Collections.addAll(formats, defaultFormats);
        }
        formats.add(CaptureRequest.POSTPROCESS_BARCODE_FORMAT_PDF_417);
        formats.add(CaptureRequest.POSTPROCESS_BARCODE_FORMAT_AZTEC);
        builder.set(CaptureRequest.POSTPROCESS_BARCODE_FORMATS, formats.toArray(new String[0]));

        /* Build the capture request. */
        return builder.build();
    }

    /**
     * Start the stream by setting a repeating capture request.
     * Do nothing if the capture session is not configured.
     */
    private void setCapture() {
        if (mCaptureSession == null || mSurface == null)
            return;
        mCaptureSession.setRepeatingRequest(createCaptureRequest(false), mCaptureListener, null);

        /* Update info text. */
        StringBuilder str = new StringBuilder();
        if (mZoom > 1.0f) {
            str.append(getString(R.string.camera_info_zoom, (int) mZoom));
        }
        mInfo.setText(str.toString());
    }

    /**
     * Trigger auto-focus.
     * Do nothing if the capture session is not configured or the camera does not support
     * auto-focus.
     */
    public void triggerAF() {
        if (mCaptureSession == null || mSurface == null || mAFMode == CaptureRequest.CONTROL_AF_MODE_OFF)
            return;
        /*
         * Note: CONTROL_AF_TRIGGER_START should only be specified for one frame.  Hence, the use
         * of capture() here.
         */
        mCaptureSession.capture(createCaptureRequest(true), mCaptureListener, null);
    }

    /** Reset capture settings */
    public void resetSettings() {
        if (mCaptureSession == null)
            return;
        mZoom = 1.0f;
        mOffset = null;
        setCapture();
    }

    /** Adjust zoom factor */
    public void zoom(float factor) {
        if (mCaptureSession == null)
            return;
        mZoom *= factor;
        mZoom = Math.max(1.0f, Math.min(mZoom, mMaxZoom));
        setCapture();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Listener implementations

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mSurfaceTexture = surface;
            mSurface = new Surface(mSurfaceTexture);
            createCaptureSession();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            setupTransform(mPreview);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            mSurface.release();
            mSurface = null;
            mSurfaceTexture = null;
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.Listener mCameraListener = new CameraDevice.Listener() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            mCamera = device;
            createCaptureSession();
        }

        @Override
        public void onClosed(@NonNull CameraDevice device) {
            mCamera = null;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
        }

        @Override
        public void onError(@NonNull CameraDevice device, int error) {
            mInfo.setText(getString(R.string.camera_error, error));
        }
    };

    private final CaptureSession.Listener mCaptureSessionListener = new CaptureSession.Listener() {
        @Override
        public void onConfigured(@NonNull CaptureSession session) {
            mCaptureSession = session;
            setCapture();
        }

        @Override
        public void onConfigureFailed(@NonNull CaptureSession session, int error) {
            mInfo.setText(R.string.camera_error_configure);
        }

        @Override
        public void onClosed(@NonNull CaptureSession session) {
            if (mCaptureSession == session)
                mCaptureSession = null;
        }

        @Override
        public void onActive(@NonNull CaptureSession session) {
        }

        @Override
        public void onCaptureQueueEmpty(@NonNull CaptureSession session) {
        }

        @Override
        public void onReady(@NonNull CaptureSession session) {
        }
    };

    /**
     * A {@link CaptureListener2} object is required to receive notifications of post processing jobs.
     */
    private final CaptureListener mCaptureListener = new CaptureListener2() {
        @Override
        public void onCaptureCompleted(@NonNull CaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult result) {
            /*
             * After triggering laser-assisted auto-focus, the LASER_POINT_OFFSET result key
             * contains an offset that can be applied to put the laser point in the center of the
             * captured image.
             */
            Point offset = result.get(CaptureResult.LASER_POINT_OFFSET);
            if (offset != null && !offset.equals(mOffset)) {
                mOffset = offset;
                setCapture();
            }
        }

        @Override
        public void onPostProcessCompleted(@NonNull CaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult result) {
            /*
             * This method is called when all post-processing steps (e.g., barcode decoding) have
             * been performed for a capture.  This is the place to retrieve the results from the
             * CaptureResult object.
             */
            mOverlay.setBarcodes(result.get(CaptureResult.POSTPROCESS_BARCODES));
        }
    };

    private final SensorEventListener mHeadMotionListener = event -> {
        /* Only trigger auto-focus when the head has stopped moving. */
        boolean newMoving = event.values[0] == 1.0f;
        if (mHeadMovement && !newMoving) {
            triggerAF();
        }
        mHeadMovement = newMoving;
    };

}
