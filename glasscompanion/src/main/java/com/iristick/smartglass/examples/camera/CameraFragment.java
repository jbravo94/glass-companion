package com.iristick.smartglass.examples.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.camera.CameraCharacteristics;
import com.iristick.smartglass.core.camera.CameraDevice;
import com.iristick.smartglass.core.camera.CaptureRequest;
import com.iristick.smartglass.core.camera.CaptureSession;
import com.iristick.smartglass.examples.R;
import com.iristick.smartglass.support.app.IristickApp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fragment handling one camera.
 * The camera index must be specified with the {@code auto:camera_index} XML attribute
 * in the layout.
 */
public class CameraFragment extends Fragment {

    /* We can hardcode the desired frame size here.
     * If the camera device does not support the desired frame size, it will choose another capture
     * frame size as appropriate and crop the resulting frames to match the desired aspect ratio.
     * Note that while the aspect ratio is preserved, the resulting frame size may still be smaller
     * or bigger than the desired frame size.  This is not a problem in this case, as the
     * TextureView used to show the preview performs scaling automatically.
     */
    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 480;

    private int mCameraIndex;
    private CameraPreview mPreview;
    private TextView mInfo;

    @Nullable private SurfaceTexture mSurfaceTexture;
    @Nullable private Surface mSurface;
    @Nullable private CameraDevice mCamera;
    @Nullable private CaptureSession mCaptureSession;

    private ImageReader imageReader = ImageReader.newInstance(FRAME_WIDTH, FRAME_HEIGHT, ImageFormat.JPEG, 1);
    private Surface imageReaderSurface = imageReader.getSurface();

    private byte[] lastImage;
    private ReentrantLock lock = new ReentrantLock();

    /* Camera characteristics */
    private int mAFMode;
    private float mMaxZoom;
    private Point mMaxOffset;

    /* Current settings */
    private float mZoom = 1.0f;
    @Nullable private Point mOffset = null;

    @Override
    public void onInflate(@NonNull Context context, @NonNull AttributeSet attrs, @Nullable Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);
        /* Retrieve the camera index to open from the XML attributes. */
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraFragment);
        mCameraIndex = a.getInt(R.styleable.CameraFragment_camera_index, 0);
        a.recycle();
    }

    public byte[] getLastImage() {

        byte[] image = null;

        if (lastImage != null && lock.tryLock()) {
            try {
                image = lastImage.clone();
                lastImage = null;
            } finally {
                lock.unlock();
            }
        }

        return image;
    }

    private byte[] trimTrailingNullBytes(byte[] image) {
        int lastIndex = image.length - 1;

        for (int i = image.length - 1; i >= 0; i--) {
            if (image[i] != 0) {
                lastIndex = i;
                break;
            }
        }

        int imageLength = lastIndex + 1;

        byte[] trimmedImage = new byte[imageLength];

        System.arraycopy(image, 0, trimmedImage, 0, imageLength);

        return trimmedImage;
    }

    private byte[] getImageAsByteArray(Image image) {
        Image.Plane[] planes = image.getPlanes();

        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return trimTrailingNullBytes(bytes);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.camera_fragment, container, false);

        mPreview = view.findViewById(R.id.preview);
        mPreview.setSurfaceTextureListener(mSurfaceTextureListener);
        mPreview.setOnGestureListener(new CameraPreview.OnGestureListener() {
            @Override
            public void onZoom(float factor) {
                zoom(factor);
            }

            @Override
            public void onPan(int dx, int dy) {
                move(dx, dy);
            }
        });
        mPreview.setOnClickListener(v -> triggerAF());

        mInfo = view.findViewById(R.id.info);
        mInfo.setOnClickListener(v -> resetSettings());

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();

            if (image != null) {

                if (lastImage == null && lock.tryLock()) {
                    try {
                        lastImage = getImageAsByteArray(image);
                    } finally {
                        lock.unlock();
                    }
                }
                image.close();
            }
        }, null);

        return view;
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
        String[] cameras = headset.getCameraIdList();
        if (mCameraIndex >= cameras.length ||
                requireActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mInfo.setText(R.string.camera_not_available);
            return;
        }
        String cameraId = cameras[mCameraIndex];
        CameraCharacteristics characteristics = headset.getCameraCharacteristics(cameraId);

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
                    mAFMode = afMode; // we prefer laser-assisted AF if available
                    break;
                }
            }
        }

        /* Get the maximum digital zoom level. */
        mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_MAX_ZOOM, 1.0f);

        /* Get the maximum frame offset. */
        mMaxOffset = characteristics.get(CameraCharacteristics.SCALER_MAX_OFFSET);
        if (mMaxOffset == null)
            mMaxOffset = new Point(0, 0);

        /* Open the camera. */
        headset.openCamera(cameraId, mCameraListener, null);
    }

    @Override
    public void onStop() {
        /* Close the camera as soon as possible. */
        if (mCamera != null) {
            mCamera.close();
            mCaptureSession = null;
            mCamera = null;
        }
        super.onStop();
    }

    /**
     * Set up the TextureView transform matrix to preserve the image aspect ratio.
     * Do nothing if the frame size is unknown.
     */
    private void setupTransform(@NonNull TextureView view) {
        float disp_ratio = (float) view.getWidth() / (float) view.getHeight();
        float frame_ratio = (float) FRAME_WIDTH / (float) FRAME_HEIGHT;
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
        if (mCamera == null || mSurfaceTexture == null || mSurface == null)
            return;

        /* Set the desired camera resolution. */
        mSurfaceTexture.setDefaultBufferSize(FRAME_WIDTH, FRAME_HEIGHT);
        setupTransform(mPreview);

        /* Create the capture session. */
        mCaptureSession = null;
        List<Surface> outputs = new ArrayList<>();

        outputs.add(mSurface);
        outputs.add(imageReaderSurface);

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
        CaptureRequest.Builder builder = Objects.requireNonNull(mCamera).createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        /* Add target output. */
        builder.addTarget(Objects.requireNonNull(mSurface));
        builder.addTarget(Objects.requireNonNull(imageReaderSurface));

        /* Set parameters. */
        builder.set(CaptureRequest.SCALER_ZOOM, mZoom);
        if (mOffset != null)
            builder.set(CaptureRequest.SCALER_OFFSET, mOffset);
        builder.set(CaptureRequest.CONTROL_AF_MODE, mAFMode);
        if (triggerAF && mAFMode != CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

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
        mCaptureSession.setRepeatingRequest(createCaptureRequest(false), null, null);

        /* Update info text. */
        StringBuilder str = new StringBuilder();
        if (mZoom > 1.0f) {
            str.append(getString(R.string.camera_info_zoom, (int) mZoom));
        }
        if (mOffset != null) {
            if (str.length() > 0)
                str.append("\n");
            str.append(getString(R.string.camera_info_offset, mOffset.x, mOffset.y));
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
        mCaptureSession.capture(createCaptureRequest(true), null, null);
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

    /** Move the image offset */
    public void move(int dx, int dy) {
        if (mCaptureSession == null)
            return;
        if (mOffset == null)
            mOffset = new Point(0, 0);
        mOffset.x += dx;
        mOffset.y += dy;
        mOffset.x = Math.max(-mMaxOffset.x, Math.min(mOffset.x, mMaxOffset.x));
        mOffset.y = Math.max(-mMaxOffset.y, Math.min(mOffset.y, mMaxOffset.y));
        setCapture();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Camera listeners implementations

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

}
