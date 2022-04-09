package com.iristick.smartglass.examples.touchpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import com.iristick.smartglass.examples.R;

import java.util.ArrayList;
import java.util.List;

public class DrawingArea extends View {

    private static final float RADIUS_DRAW = 8;
    private static final float RADIUS_MOVE = 16;

    private int mBackgroundColor;
    private Paint mLinePaint;
    private Paint mPencilPaint;
    private Paint mCursorPaint;

    private List<List<PointF>> mLines;
    private List<PointF> mCurrentLine;
    private float mPencilX, mPencilY;
    private boolean mDraw;

    public DrawingArea(Context context) {
        super(context);
        init();
    }

    public DrawingArea(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingArea(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBackgroundColor = getResources().getColor(R.color.touchpad_drawing_background, null);
        mLinePaint = new Paint();
        mLinePaint.setColor(getResources().getColor(R.color.touchpad_drawing_line, null));
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(4);
        mPencilPaint = new Paint();
        mPencilPaint.setColor(getResources().getColor(R.color.touchpad_drawing_pencil, null));
        mPencilPaint.setStyle(Paint.Style.FILL);
        mCursorPaint = new Paint(mPencilPaint);
        mCursorPaint.setStyle(Paint.Style.STROKE);
        mCursorPaint.setStrokeWidth(8);
        mLines = new ArrayList<>();
        mCurrentLine = null;
        mPencilX = 0;
        mPencilY = 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (!mDraw) {
            mPencilX = (float)w / 2;
            mPencilY = (float)h / 2;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        /* Draw the gray background. */
        canvas.drawColor(mBackgroundColor);

        /* Draw the lines. */
        for (List<PointF> line : mLines) {
            PointF last = null;
            for (PointF pt : line) {
                if (last != null)
                    canvas.drawLine(last.x, last.y, pt.x, pt.y, mLinePaint);
                last = pt;
            }
        }

        /* Draw the cursor. */
        if (mDraw)
            canvas.drawCircle(mPencilX, mPencilY, RADIUS_DRAW, mPencilPaint);
        else
            canvas.drawCircle(mPencilX, mPencilY, RADIUS_MOVE, mCursorPaint);

        super.onDraw(canvas);
    }

    public void movePencil(float dx, float dy) {
        mPencilX += dx;
        mPencilY += dy;
        /* Constrain pencil to view bounds. */
        mPencilX = Math.min(Math.max(0, mPencilX), getWidth());
        mPencilY = Math.min(Math.max(0, mPencilY), getHeight());
        if (mDraw && mCurrentLine != null) {
            mCurrentLine.add(new PointF(mPencilX, mPencilY));
        }
        invalidate();
    }

    public void startLine() {
        if (!mDraw)
            return;
        if (mCurrentLine != null && mCurrentLine.size() < 2)
            return; /* keep drawing in current line to have at least one line segment. */
        mCurrentLine = new ArrayList<>();
        mLines.add(mCurrentLine);
        mCurrentLine.add(new PointF(mPencilX, mPencilY));
    }

    public void switchMode() {
        mDraw = !mDraw;
        if (mCurrentLine != null) {
            /* Remove the last line, which was drawn due to the gesture. */
            mLines.remove(mLines.size() - 1);
            mCurrentLine = null;
        }
        invalidate();
    }

    public void clearDrawing() {
        mLines.clear();
        mCurrentLine = null;
        mDraw = false;
        invalidate();
    }

    public void removeLast() {
        /* Remove the line drawn by the gesture. */
        if (mDraw && mCurrentLine != null) {
            mLines.remove(mLines.size() - 1);
            mCurrentLine = null;
        }
        /* Remove the previously drawn line. */
        if (!mLines.isEmpty())
            mLines.remove(mLines.size() - 1);
        invalidate();
    }

}
