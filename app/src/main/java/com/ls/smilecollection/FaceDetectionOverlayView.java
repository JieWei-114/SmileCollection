package com.ls.smilecollection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import org.opencv.core.Rect;

public class FaceDetectionOverlayView extends View {
    private Bitmap bitmapToDraw;
    private Rect[] faces;

    public FaceDetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void updateOverlay(Bitmap bitmap) {
        this.bitmapToDraw = bitmap;
        invalidate(); // Redraw the view
    }

    public void setFaces(Rect[] faces) {
        this.faces = faces;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw the bitmap if available
        if (bitmapToDraw != null) {
            canvas.drawBitmap(bitmapToDraw, 0, 0, null);
        }
    }
}