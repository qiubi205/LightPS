package com.lightps.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.lightps.engine.brush.Brush;
import com.lightps.engine.layer.Layer;
import com.lightps.engine.layer.LayerManager;
import com.lightps.engine.selection.Selection;

/**
 * Canvas view that renders the layer stack and handles touch input.
 * This is a CPU-based implementation using Android Canvas.
 * For production, replace with GLSurfaceView for GPU rendering.
 */
public class CanvasView extends View {

    private LayerManager layerManager;
    private Bitmap flatBitmap;          // cached flatten result
    private boolean flatDirty = true;

    // Drawing state
    private Brush currentBrush;
    private Selection currentSelection;
    private boolean drawing = false;
    private float lastX, lastY;

    // View transform
    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    // Paints
    private final Paint bgPaint;
    private final Paint checkerPaint;

    public CanvasView(Context context) {
        this(context, null);
    }

    public CanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);

        bgPaint = new Paint();
        bgPaint.setColor(Color.DKGRAY);

        checkerPaint = new Paint();
    }

    // ── Bind data ──────────────────────────────────────

    public void setLayerManager(LayerManager lm) {
        this.layerManager = lm;
        this.flatDirty = true;
        invalidate();
    }

    public void setBrush(Brush brush) {
        this.currentBrush = brush;
    }

    public void setSelection(Selection selection) {
        this.currentSelection = selection;
    }

    public void invalidateFlat() {
        this.flatDirty = true;
        invalidate();
    }

    // ── View transform ─────────────────────────────────

    public void setViewTransform(float scale, float offsetX, float offsetY) {
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        invalidate();
    }

    public float getScale() { return scale; }

    /** Convert screen coordinates to canvas/image coordinates. */
    public float screenToCanvasX(float sx) {
        return (sx - offsetX) / scale;
    }

    public float screenToCanvasY(float sy) {
        return (sy - offsetY) / scale;
    }

    // ── Drawing ────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // Checkerboard for transparent areas
        drawCheckerBoard(canvas);

        // Flattened layers
        if (layerManager != null) {
            if (flatDirty || flatBitmap == null) {
                if (flatBitmap != null) flatBitmap.recycle();
                flatBitmap = layerManager.flatten();
                flatDirty = false;
            }
            canvas.drawBitmap(flatBitmap, 0, 0, null);
        }

        // Selection overlay (marching ants)
        if (currentSelection != null && !currentSelection.isEmpty()) {
            drawSelection(canvas);
        }

        canvas.restore();
    }

    private void drawCheckerBoard(Canvas canvas) {
        int canvasW = layerManager != null ? layerManager.getWidth() : 100;
        int canvasH = layerManager != null ? layerManager.getHeight() : 100;
        int gridSize = (int) (8 * scale);
        if (gridSize < 2) gridSize = 2;

        Paint p = new Paint();
        for (int y = 0; y < canvasH; y += gridSize) {
            for (int x = 0; x < canvasW; x += gridSize) {
                boolean white = ((x / gridSize) + (y / gridSize)) % 2 == 0;
                p.setColor(white ? 0xFFD9D9D9 : 0xFFA0A0A0);
                canvas.drawRect(x, y,
                        Math.min(x + gridSize, canvasW),
                        Math.min(y + gridSize, canvasH), p);
            }
        }
    }

    private void drawSelection(Canvas canvas) {
        // Marching ants effect: use the selection's bounds
        Rect bounds = currentSelection.getBounds();
        if (bounds.isEmpty()) return;

        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f / scale);
        p.setColor(Color.WHITE);

        // Animate dashes for marching ants effect
        long phase = System.currentTimeMillis() / 50;
        p.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{4f / scale, 4f / scale}, phase));

        canvas.drawRect(bounds, p);
        p.setColor(Color.BLACK);
        p.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{4f / scale, 4f / scale}, phase + 4f / scale));
        canvas.drawRect(bounds, p);
    }

    // ── Touch input ────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = screenToCanvasX(event.getX());
        float y = screenToCanvasY(event.getY());

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drawing = true;
                lastX = x;
                lastY = y;
                if (currentBrush != null) {
                    handleDrawStroke(x, y, x, y, event.getPressure());
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (drawing && currentBrush != null) {
                    handleDrawStroke(lastX, lastY, x, y, event.getPressure());
                    lastX = x;
                    lastY = y;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drawing = false;
                // Flush any pending drawing
                if (flatDirty) invalidate();
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleDrawStroke(float x1, float y1, float x2, float y2, float pressure) {
        if (layerManager == null) return;

        Layer active = layerManager.getActiveLayer();
        if (active == null || !active.isPixelLayer()) return;

        com.lightps.engine.layer.PixelLayer pl =
                (com.lightps.engine.layer.PixelLayer) active;

        android.graphics.Canvas pixelCanvas = new android.graphics.Canvas(pl.getPixels());
        currentBrush.drawStroke(pixelCanvas, x1, y1, x2, y2, pressure);

        flatDirty = true;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (flatBitmap != null) {
            flatBitmap.recycle();
            flatBitmap = null;
        }
    }
}
