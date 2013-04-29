package com.barthand.PathDrawing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Custom {@link View} which renders some custom path.
 * Paths are always drawn using the CPU, but when enclosing {@link View} is GPU accelerated then the
 * path gets drawn onto the texture first and then this texture is drawn onto the screen.
 *
 * <p>There is a problem with such approach though. If {@link Path} is large enough, it can exceed the maximum allowed
 * texture size (see {@link Canvas#getMaximumBitmapWidth()} and {@link Canvas#getMaximumBitmapHeight()}).
 * In that case the {@link Path} simply is not drawn at all -- OpenGL disallows such operation due to
 * hardware limitations.</p>
 *
 * <p>We may though overcome that by drawing such a large {@link Path} into the own managed {@link Bitmap},
 * which dimensions are the same as the ones coming from the {@link View} (here they are the same as screen dimensions).
 * By translating the Bitmap's {@link Canvas} we can control which part of the {@link Path} is drawn onto the
 * {@link Bitmap}.
 * It works well, because, as of Android 4.2, {@link Canvas} associated with the {@link Bitmap} is not GPU accelerated.
 * </p>
 *
 * <p>As soon as drawing on bitmap is done, this {@link Bitmap} may be simply drawn onto the
 * {@link View}'s canvas. Even though it is GPU accelerated, the {@link Bitmap} in its maximum is of the screen size
 * and it is guaranteed that it fits onto the maximum texture size.</p>
 *
 * @see <a href="http://developer.android.com/guide/topics/graphics/hardware-accel.html">Hardware Acceleration</a>
 */
public class PathOnBitmapRenderingView extends View {

    /** Defines the duration (in ms) for the frame to achieve 60 frames per second. */
    private static final int FRAME_DURATION_FOR_60FPS_IN_MS = 1000 / 60;

    /** Defines the width of the inner paint. Outer paint is twice of this size. */
    private static final float PAINT_INNER_WIDTH = 4f;

    /**
     * The {@link Path} that is created for this {@link View} is built on the square plan.
     * This value defines half of the both dimensions used for drawing (i.e. width and height).
     */
    private static final int HALF_PATH_DIMENSION = 3000;

    /** Cached inner {@link Paint} instance to reduce objects creation overhead. */
    private final Paint innerPaint;

    /** Cached outer {@link Paint} instance to reduce objects creation overhead. */
    private final Paint outerPaint;

    /** Cached {@link Path} instance to reduce objects creation overhead. */
    private final Path path;

    /** {@link GestureDetector} used to detect scrolling. */
    private final GestureDetector gestureDetector;

    /** {@link GestureDetector.SimpleOnGestureListener} used to adjust offsets on scroll. */
    private final GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            // This needs to return true to process further events.
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            offsetX -= distanceX;
            offsetY -= distanceY;
            return true;
        }
    };

    /** {@link Handler} associated with UI thread. Used to repeatedly invoke {@link #selfInvalidatingRunnable}. */
    private final Handler handler;

    /**
     * {@link Runnable} checking if offsets have changed since last time and invalidating the view in that case.
     * It posts itself repeatedly on the UI thread to ensure smooth screen updates.
     */
    private final Runnable selfInvalidatingRunnable = new Runnable() {
        /** Last offset in X axis. */
        private int lastOffsetX = 0;
        /** Last offset in Y axis. */
        private int lastOffsetY = 0;

        @Override
        public void run() {
            boolean change = (lastOffsetX != offsetX) || (lastOffsetY != offsetY);
            lastOffsetX = offsetX;
            lastOffsetY = offsetY;
            if (change) {
                invalidate();
            }
            handler.postDelayed(this, FRAME_DURATION_FOR_60FPS_IN_MS);
        }
    };

    /** Cached {@link Bitmap} instance used to draw {@link Path} on it using the {@link Canvas}. */
    private Bitmap bitmap;

    /** {@link Canvas} associated with the {@link #bitmap}. */
    private Canvas bitmapCanvas;

    /** Last offset in X axis. */
    private int offsetX = 0;
    /** Last offset in Y axis. */
    private int offsetY = 0;

    /**
     * Constructor allowing to inflate the view from the XML.
     */
    public PathOnBitmapRenderingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.innerPaint = createInnerPaint();
        this.outerPaint = createOuterPaint();
        this.path = createPath();
        this.gestureDetector = new GestureDetector(context, listener);
        this.handler = new Handler();
        this.handler.post(selfInvalidatingRunnable);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(this.getMeasuredWidth(), this.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            bitmapCanvas = new Canvas(bitmap);
        }

        bitmapCanvas.save();
        bitmapCanvas.translate(offsetX, offsetY);
        bitmapCanvas.drawColor(0xfff9f9f9);
        bitmapCanvas.drawPath(path, outerPaint);
        bitmapCanvas.drawPath(path, innerPaint);
        bitmapCanvas.restore();

        canvas.drawBitmap(bitmap, 0, 0, innerPaint);

        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    /**
     * Creates randomized path which looks like a steps going from top left corner into bottom right one.
     *
     * <p>It is guaranteed that the path itself is larger than the screen resolution. This is done to allow
     * scrolling through the path.</p>
     */
    private Path createPath() {
        final Path path = new Path();
        final int startX = -HALF_PATH_DIMENSION, startY = -HALF_PATH_DIMENSION;
        final int stepX = 100, stepY = 100;
        final Random random = new Random();

        path.moveTo(startX, startY);
        boolean addX = true;
        for (int x = startX, y = startY; x < -startX || y < -startY; ) {
            if (addX) {
                x += random.nextInt(stepX);
            } else {
                y += random.nextInt(stepY);
            }
            path.lineTo(x, y);
            addX = !addX;
        }
        return path;
    }

    /**
     * Simple factory-method for the inner {@link Paint} object.
     */
    private static Paint createInnerPaint() {
        final Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setStrokeWidth(PAINT_INNER_WIDTH);
        paint.setColor(0xff0099cc);
        paint.setStyle(Paint.Style.STROKE);
        return paint;
    }

    /**
     * Simple factory-method for the inner {@link Paint} object.
     */
    private static Paint createOuterPaint() {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(PAINT_INNER_WIDTH * 2);
        paint.setColor(0xff33b5e5);
        paint.setStyle(Paint.Style.STROKE);
        return paint;
    }

}
