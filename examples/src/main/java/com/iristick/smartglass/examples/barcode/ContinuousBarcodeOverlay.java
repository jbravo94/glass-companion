package com.iristick.smartglass.examples.barcode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

import com.iristick.smartglass.core.camera.Barcode;

/**
 * Overlay for annotating scanned barcodes over a camera preview.
 *
 * The overlay draws a colored rectangle around the bar codes and writes the barcode value therein.
 */
public final class ContinuousBarcodeOverlay extends View {

    /* General fixed drawing settings */
    private static final int BOX_STROKE_COLOR = Color.GREEN;
    private static final float BOX_STROKE_WIDTH_DIP = 2.5f;
    private static final int LABEL_TEXT_COLOR = Color.WHITE;
    private static final int LABEL_TEXT_MAX_LENGTH = 40;
    private static final int LABEL_BACKGROUND_COLOR = 0x80000000; /* black, 128 alpha */
    private static final float LABEL_SIZE_SP = 18.0f;
    private static final float LABEL_PADDING_DIP = 4.0f;

    /* Utilities for mapping points in frame coordinates to view coordinates */
    private int mFrameWidth;
    private int mFrameHeight;
    private final Matrix mTransform = new Matrix();

    /* Barcode and label data */
    private Barcode[] mBarcodes;
    private Path[] mBoxes;
    private String[] mLabels;
    private PointF[] mLabelLocations;   /* [x, y] coordinates of the center of the label */
    private float[] mLabelWidths;       /* width of the label text */

    /* Drawing helpers */
    private Paint mBoxPaint;
    private Paint mLabelTextPaint;
    private Paint mLabelBackgroundPaint;
    private float mLabelTextHeight;
    private float mLabelTextBaselineOffset;
    private float mLabelPadding;


    public ContinuousBarcodeOverlay(Context context) {
        super(context);
        init();
    }

    public ContinuousBarcodeOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ContinuousBarcodeOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /** Initialize all drawing helpers to their proper values. */
    private void init() {
        final DisplayMetrics dpMetrics = getContext().getResources().getDisplayMetrics();
        mBoxPaint = new Paint();
        mBoxPaint.setColor(BOX_STROKE_COLOR);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BOX_STROKE_WIDTH_DIP, dpMetrics));
        mBoxPaint.setAntiAlias(true);
        mLabelTextPaint = new Paint();
        mLabelBackgroundPaint = new Paint();
        mLabelBackgroundPaint.setColor(LABEL_BACKGROUND_COLOR);
        mLabelBackgroundPaint.setStyle(Paint.Style.FILL);
        mLabelTextPaint.setColor(LABEL_TEXT_COLOR);
        mLabelTextPaint.setTextAlign(Paint.Align.CENTER);
        mLabelTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SIZE_SP, dpMetrics));
        mLabelTextPaint.setTypeface(Typeface.MONOSPACE);
        Paint.FontMetrics metrics = mLabelTextPaint.getFontMetrics();
        mLabelTextHeight = metrics.bottom - metrics.top;
        mLabelTextBaselineOffset = -((metrics.descent + metrics.ascent) / 2.0f);
        mLabelPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LABEL_PADDING_DIP, dpMetrics);
    }

    /** Initialize the overlay with the given frame width and height. */
    public void setup(int frameWidth, int frameHeight) {
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;
        setupTransform();
        invalidate();
    }

    /** Set the list of barcode data for drawing annotations. */
    public void setBarcodes(Barcode[] barcodes) {
        if (barcodes == null || barcodes.length == 0) {
            mBarcodes = null;
        } else {
            mBarcodes = barcodes;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupTransform();
        invalidate();
    }

    /** Calculate the proper projection matrix for mapping frame coordinates to view coordinates.
     */
    private void setupTransform() {
        /* All barcode data set in {@link #setBarcodes} is in frame coordinates. However, drawing uses
         * view coordinates. The matrix transformation required to convert between these coordinate
         * systems is computed here.
         *
         * For simplicity, we assume that the overlay has the exact same view bounds as the camera
         * preview and that the frames are letterboxed within the preview view bounds.
         */
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0 || mFrameWidth <= 0 || mFrameHeight <= 0) return;
        mTransform.reset();
        mTransform.setRectToRect(
                new RectF(0, 0, mFrameWidth, mFrameHeight),
                new RectF(0, 0, w, h),
                Matrix.ScaleToFit.CENTER);
    }

    /** Recalculate all drawing data and invalidate the entire view.
     * This must be called from a UI thread. */
    @Override
    public void invalidate() {
        if (mBarcodes == null) {
            /* No bar codes, reset everything. */
            mBoxes = null;
            mLabels = null;
            mLabelLocations = null;
            mLabelWidths = null;
        } else {
            final int barcodeCount = mBarcodes.length;
            mBoxes = new Path[barcodeCount];
            mLabels = new String[barcodeCount];
            mLabelLocations = new PointF[barcodeCount];
            mLabelWidths = new float[barcodeCount];
            final float cyMin = mLabelTextHeight / 2.0f + mLabelPadding;
            for (int i = 0; i < barcodeCount; ++i) {
                final Barcode barcode = mBarcodes[i];
                Point[] points = barcode.getPoints();

                /* Skip barcodes without known location data. */
                final int count = points.length;
                if (count == 0)
                    continue;

                /* Map all points from frame coordinates to view coordinates. */
                float[] mappedPoints = new float[count * 2];
                for (int j = 0; j < count; ++j) {
                    final Point p = points[j];
                    mappedPoints[j * 2] = p.x;
                    mappedPoints[j * 2 + 1] = p.y;
                }
                mTransform.mapPoints(mappedPoints);

                /* Create paths to draw the colored rectangles around the barcodes. */
                if (count >= 2) {
                    Path box = new Path();
                    box.moveTo(mappedPoints[0], mappedPoints[1]);
                    for (int j = 1; j < count; ++j) {
                        box.lineTo(mappedPoints[j * 2], mappedPoints[j * 2 + 1]);
                    }
                    if (count >= 3) box.lineTo(mappedPoints[0], mappedPoints[1]);
                    mBoxes[i] = box;
                }

                /* Set the text to be drawn for each barcode. */
                String label = barcode.getValue();
                if (label.length() > LABEL_TEXT_MAX_LENGTH) {
                    label = label.substring(0, LABEL_TEXT_MAX_LENGTH - 1) + "…";
                }
                mLabels[i] = label;

                /* Calculate the size and location of the labels. */
                mLabelWidths[i] = mLabelTextPaint.measureText(label);
                float cx = 0, cy = 0;
                for (int j = 0; j < count; ++j) {
                    cx += mappedPoints[j * 2];
                    cy += mappedPoints[j * 2 + 1];
                }
                cx /= count;
                cy /= count;
                final float cxMin = mLabelWidths[i] / 2.0f + mLabelPadding;
                cx = Math.max(cxMin, Math.min(cx, getWidth() - cxMin));
                cy = Math.max(cyMin, Math.min(cy, getHeight() - cyMin));
                mLabelLocations[i] = new PointF((int) cx, (int) cy);
            }
        }

        super.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        /* Draw all the rectangles */
        if (mBoxes != null) {
            for (Path path : mBoxes) {
                if (path != null && !path.isEmpty())
                    canvas.drawPath(path, mBoxPaint);
            }
        }
        /* Draw all the labels */
        if (mLabels != null && mLabelLocations != null && mLabelWidths != null) {
            final float h = mLabelTextHeight / 2.0f + mLabelPadding;
            for (int i = 0, count = mLabels.length; i < count; ++i) {
                final String text = mLabels[i];
                final PointF loc = mLabelLocations[i];
                if (text == null || text.isEmpty() || loc == null)
                    continue;
                final float x = loc.x;
                final float y = loc.y;
                final float w = mLabelWidths[i] / 2.0f + mLabelPadding;
                canvas.drawRect(x - w, y - h, x + w, y + h, mLabelBackgroundPaint);
                canvas.drawText(text, x, y + mLabelTextBaselineOffset, mLabelTextPaint);
            }
        }
    }
}
