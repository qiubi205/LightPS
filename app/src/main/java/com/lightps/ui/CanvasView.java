package com.lightps.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.lightps.engine.brush.Brush;
import com.lightps.engine.layer.Layer;
import com.lightps.engine.layer.LayerManager;
import com.lightps.engine.selection.Selection;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas view that renders the layer stack and handles touch input.
 * Supports smooth curved strokes, pinch-zoom, and pan.
 */
public class CanvasView extends View {

    // ── Core data ──────────────────────────────────────
    private LayerManager layerManager;
    private Bitmap flatBitmap;
    private boolean flatDirty = true;

    // ── Drawing state ──────────────────────────────────
    private Brush currentBrush;
    private Selection currentSelection;
    private boolean drawing = false;
    private boolean eraserMode = false;

    // Smooth stroke: accumulate points for bezier interpolation
    private final List<PointF> strokePoints = new ArrayList<>();
    private Path currentPath;
    private Paint strokePaint;

    // Touch sampling for velocity-based width
    private long lastTouchTime;
    private float lastTouchX, lastTouchY;

    // ── View transform ─────────────────────────────────
    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float lastPinchDist = 0f;
    private boolean panning = false;
    private float panStartX, panStartY;
    private float panOffsetStartX, panOffsetStartY;

    // ── Undo stack (per-layer bitmap snapshots) ────────
    private final List<UndoEntry> undoStack = new ArrayList<>();
    private static final int MAX_UNDO = 20;

    // ── Paints ─────────────────────────────────────────
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

        // Stroke paint for preview
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
    }

    // ── Bind data ──────────────────────────────────────

    public void setLayerManager(LayerManager lm) {
        this.layerManager = lm;
        this.flatDirty = true;
        undoStack.clear();
        invalidate();
    }

    public void setBrush(Brush brush) {
        this.currentBrush = brush;
    }

    public void setSelection(Selection selection) {
        this.currentSelection = selection;
    }

    public void setEraserMode(boolean eraser) {
        this.eraserMode = eraser;
    }

    public boolean isEraserMode() { return eraserMode; }

    public void invalidateFlat() {
        this.flatDirty = true;
        invalidate();
    }

    // ── Undo ───────────────────────────────────────────

    /** Save a snapshot of the active layer before drawing. */
    private void snapshotForUndo() {
        if (layerManager == null) return;
        Layer active = layerManager.getActiveLayer();
        if (active == null || !active.isPixelLayer()) return;

        com.lightps.engine.layer.PixelLayer pl =
                (com.lightps.engine.layer.PixelLayer) active;
        Bitmap copy = pl.getPixels().copy(Bitmap.Config.ARGB_8888, false);
        undoStack.add(new UndoEntry(pl, copy));
        if (undoStack.size() > MAX_UNDO) {
            undoStack.remove(0).recycle();
        }
    }

    /** Undo last stroke. */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        UndoEntry entry = undoStack.remove(undoStack.size() - 1);
        if (entry.layer.getPixels() != null) {
            Bitmap current = entry.layer.getPixels();
            Bitmap saved = entry.saved;
            if (current != null && saved != null && !current.isRecycled() && !saved.isRecycled()) {
                Canvas c = new Canvas(current);
                c.drawColor(0, PorterDuff.Mode.CLEAR);
                c.drawBitmap(saved, 0, 0, null);
            }
        }
        entry.recycle();
        flatDirty = true;
        invalidate();
        return true;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    // ── View transform ─────────────────────────────────

    public void setViewTransform(float scale, float offsetX, float offsetY) {
        this.scale = Math.max(0.1f, Math.min(10f, scale));
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        invalidate();
    }

    public float getScale() { return scale; }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }

    public float screenToCanvasX(float sx) { return (sx - offsetX) / scale; }
    public float screenToCanvasY(float sy) { return (sy - offsetY) / scale; }

    // ── Drawing ────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Background
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // Checkerboard
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

        // Stroke preview while drawing
        if (drawing && currentPath != null && strokePaint != null) {
            canvas.drawPath(currentPath, strokePaint);
        }

        // Selection overlay
        if (currentSelection != null && !currentSelection.isEmpty()) {
            drawSelection(canvas);
        }

        canvas.restore();
    }

    private void drawCheckerBoard(Canvas canvas) {
        int cw = layerManager != null ? layerManager.getWidth() : 100;
        int ch = layerManager != null ? layerManager.getHeight() : 100;
        int gridSize = (int) (8 * scale);
        if (gridSize < 2) gridSize = 2;

        Paint p = new Paint();
        for (int y = 0; y < ch; y += gridSize) {
            for (int x = 0; x < cw; x += gridSize) {
                boolean white = ((x / gridSize) + (y / gridSize)) % 2 == 0;
                p.setColor(white ? 0xFFD9D9D9 : 0xFFA0A0A0);
                canvas.drawRect(x, y,
                        Math.min(x + gridSize, cw),
                        Math.min(y + gridSize, ch), p);
            }
        }
    }

    private void drawSelection(Canvas canvas) {
        Rect bounds = currentSelection.getBounds();
        if (bounds.isEmpty()) return;

        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f / scale);
        p.setColor(Color.WHITE);

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
        int action = event.getActionMasked();

        // Pinch-to-zoom: two fingers
        if (event.getPointerCount() >= 2) {
            if (action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_MOVE) {
                float dx = event.getX(0) - event.getX(1);
                float dy = event.getY(0) - event.getY(1);
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    lastPinchDist = dist;
                } else {
                    float scaleChange = dist / lastPinchDist;
                    float newScale = Math.max(0.1f, Math.min(10f, scale * scaleChange));
                    // Zoom toward center of pinch
                    float cx = (event.getX(0) + event.getX(1)) / 2f;
                    float cy = (event.getY(0) + event.getY(1)) / 2f;
                    offsetX = cx - (cx - offsetX) * (newScale / scale);
                    offsetY = cy - (cy - offsetY) * (newScale / scale);
                    scale = newScale;
                    lastPinchDist = dist;
                    invalidate();
                    drawing = false;
                }
                return true;
            }
        }

        // Single finger
        float x = screenToCanvasX(event.getX());
        float y = screenToCanvasY(event.getY());

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                drawing = true;
                strokePoints.clear();
                strokePoints.add(new PointF(x, y));
                lastX = x;
                lastY = y;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                lastTouchTime = System.currentTimeMillis();

                currentPath = new Path();
                currentPath.moveTo(x, y);

                // Snapshot for undo
                snapshotForUndo();

                // Draw the first point
                if (currentBrush != null) {
                    drawPoint(x, y, event.getPressure());
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (!drawing) break;

                float sx = event.getX();
                float sy = event.getY();

                // Compute velocity for pressure interpolation
                long now = System.currentTimeMillis();
                long dt = Math.max(1, now - lastTouchTime);
                float dScreen = (float) Math.sqrt(
                        (sx - lastTouchX) * (sx - lastTouchX)
                      + (sy - lastTouchY) * (sy - lastTouchY));
                float velocity = dScreen / dt; // px/ms

                // Interpolate between last and current for smooth strokes
                float dx = x - lastX;
                float dy = y - lastY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                // Sub-step for fast movements to avoid gaps
                int steps = Math.max(1, (int) (dist / Math.max(1, currentBrush.getSize() * 0.5f)));
                for (int i = 0; i <= steps; i++) {
                    float t = (float) i / steps;
                    float ix = lastX + dx * t;
                    float iy = lastY + dy * t;
                    float pressure = event.getPressure();

                    // Velocity-based width modulation
                    float speedFactor = Math.min(1f, velocity / 3f); // fast = thinner
                    float modulatedSize = currentBrush.getSize() * (0.5f + 0.5f * (1f - speedFactor));

                    if (currentBrush != null) {
                        drawStroke(lastX, lastY, ix, iy, pressure, modulatedSize);
                    }
                }

                // Build preview path
                strokePoints.add(new PointF(x, y));
                rebuildPreviewPath();

                lastX = x;
                lastY = y;
                lastTouchX = sx;
                lastTouchY = sy;
                lastTouchTime = System.currentTimeMillis();

                flatDirty = true;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drawing = false;
                currentPath = null;
                if (flatDirty) invalidate();
                return true;

            default:
                return super.onTouchEvent(event);
        }
        return true;
    }

    // ── Drawing helpers ────────────────────────────────

    private void rebuildPreviewPath() {
        if (strokePoints.size() < 2) return;
        currentPath = new Path();
        currentPath.moveTo(strokePoints.get(0).x, strokePoints.get(0).y);

        // Bezier curve through points
        for (int i = 1; i < strokePoints.size() - 1; i++) {
            PointF p0 = strokePoints.get(i - 1);
            PointF p1 = strokePoints.get(i);
            PointF p2 = strokePoints.get(i + 1);
            float cx = (p1.x + p2.x) / 2f;
            float cy = (p1.y + p2.y) / 2f;
            currentPath.quadTo(p1.x, p1.y, cx, cy);
        }
        // Last segment
        PointF last = strokePoints.get(strokePoints.size() - 1);
        currentPath.lineTo(last.x, last.y);

        // Setup preview paint
        strokePaint.setColor(currentBrush != null ? currentBrush.getColor() : Color.BLACK);
        strokePaint.setStrokeWidth(currentBrush != null ? currentBrush.getSize() : 10f);
        strokePaint.setXfermode(null);
    }

    private void drawPoint(float x, float y, float pressure) {
        if (layerManager == null || currentBrush == null) return;

        Layer active = layerManager.getActiveLayer();
        if (active == null || !active.isPixelLayer()) return;

        com.lightps.engine.layer.PixelLayer pl =
                (com.lightps.engine.layer.PixelLayer) active;
        Canvas pixelCanvas = new Canvas(pl.getPixels());

        currentBrush.setSize(currentBrush.getSize()); // use current size
        currentBrush.drawStroke(pixelCanvas, x, y, x, y, pressure);
    }

    private void drawStroke(float x1, float y1, float x2, float y2,
                             float pressure, float size) {
        if (layerManager == null || currentBrush == null) return;

        Layer active = layerManager.getActiveLayer();
        if (active == null || !active.isPixelLayer()) return;

        com.lightps.engine.layer.PixelLayer pl =
                (com.lightps.engine.layer.PixelLayer) active;
        Canvas pixelCanvas = new Canvas(pl.getPixels());

        // Temporarily set modulated size
        float origSize = currentBrush.getSize();
        currentBrush.setSize(size);

        if (eraserMode) {
            // Eraser: use SRC mode to clear
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.TRANSPARENT);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            pixelCanvas.drawLine(x1, y1, x2, y2, p);
        } else {
            currentBrush.drawStroke(pixelCanvas, x1, y1, x2, y2, pressure);
        }

        currentBrush.setSize(origSize);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (flatBitmap != null) {
            flatBitmap.recycle();
            flatBitmap = null;
        }
        for (UndoEntry e : undoStack) e.recycle();
        undoStack.clear();
    }

    // ── Undo entry ────────────────────────────────────

    private static class UndoEntry {
        final com.lightps.engine.layer.PixelLayer layer;
        final Bitmap saved;

        UndoEntry(com.lightps.engine.layer.PixelLayer layer, Bitmap saved) {
            this.layer = layer;
            this.saved = saved;
        }

        void recycle() {
            if (saved != null && !saved.isRecycled()) saved.recycle();
        }
    }
}
