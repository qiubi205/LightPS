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
import android.graphics.RectF;
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
 * Optimized: dirty-rect invalidation, no flatten during stroke.
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

    // Touch sampling
    private long lastTouchTime;
    private float lastTouchX, lastTouchY;

    // ── Dirty rect tracking (canvas coords) ────────────
    private final RectF dirtyCanvasRect = new RectF();
    private boolean dirtyRectValid = false;

    // ── View transform ─────────────────────────────────
    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float lastPinchDist = 0f;

    // ── Undo stack ─────────────────────────────────────
    private final List<UndoEntry> undoStack = new ArrayList<>();
    private static final int MAX_UNDO = 20;

    // ── Paints ─────────────────────────────────────────
    private final Paint bgPaint;
    private Bitmap checkerTile; // cached checkerboard tile

    public CanvasView(Context context) {
        this(context, null);
    }

    public CanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);

        bgPaint = new Paint();
        bgPaint.setColor(Color.DKGRAY);

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
        checkerTile = null;
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

    /** Full flat rebuild + full redraw (used after undo, layer add/delete). */
    public void invalidateFlat() {
        this.flatDirty = true;
        dirtyRectValid = false;
        invalidate();
    }

    // ── Undo ───────────────────────────────────────────

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
        invalidateFlat();
        return true;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    // ── View transform ─────────────────────────────────

    public float getScale() { return scale; }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }

    public float screenToCanvasX(float sx) { return (sx - offsetX) / scale; }
    public float screenToCanvasY(float sy) { return (sy - offsetY) / scale; }

    // ── Drawing ────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        // Background
        canvas.drawRect(0, 0, w, h, bgPaint);

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // Checkerboard — cached tile
        drawCheckerBoard(canvas);

        // Flattened layers — only rebuild when needed
        if (layerManager != null) {
            if (flatDirty || flatBitmap == null) {
                if (flatBitmap != null) flatBitmap.recycle();
                flatBitmap = layerManager.flatten();
                flatDirty = false;
            }
            canvas.drawBitmap(flatBitmap, 0, 0, null);
        }

        // Stroke preview while drawing (only in dirty region)
        if (drawing && currentPath != null && strokePaint != null) {
            canvas.drawPath(currentPath, strokePaint);
        }

        // Selection
        if (currentSelection != null && !currentSelection.isEmpty()) {
            drawSelection(canvas);
        }

        canvas.restore();
    }

    /** Build a cached 16×16 checker tile. */
    private Bitmap getCheckerTile() {
        if (checkerTile == null || checkerTile.isRecycled()) {
            checkerTile = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(checkerTile);
            Paint p = new Paint();
            for (int y = 0; y < 16; y += 8) {
                for (int x = 0; x < 16; x += 8) {
                    boolean white = ((x / 8) + (y / 8)) % 2 == 0;
                    p.setColor(white ? 0xFFD9D9D9 : 0xFFA0A0A0);
                    c.drawRect(x, y, x + 8, y + 8, p);
                }
            }
        }
        return checkerTile;
    }

    private void drawCheckerBoard(Canvas canvas) {
        Bitmap tile = getCheckerTile();
        int cw = layerManager != null ? layerManager.getWidth() : 100;
        int ch = layerManager != null ? layerManager.getHeight() : 100;

        if (scale <= 0.5f) {
            // At far zoom use small tile fill
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(tile, new Rect(0, 0, 16, 16),
                    new RectF(0, 0, cw, ch), p);
        } else {
            // Draw scaled tile repeated
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            float ts = 8 * scale / 2f; // keep grid crisp
            for (float y = 0; y < ch; y += ts * 2) {
                for (float x = 0; x < cw; x += ts * 2) {
                    Rect src = new Rect(0, 0, 16, 16);
                    RectF dst = new RectF(x, y,
                            Math.min(x + ts * 2, cw),
                            Math.min(y + ts * 2, ch));
                    canvas.drawBitmap(tile, src, dst, p);
                }
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

        // Pinch-to-zoom
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

                snapshotForUndo();

                // Draw first point
                if (currentBrush != null) {
                    drawPoint(x, y, event.getPressure());
                }

                // Init dirty rect around the point
                float r = (currentBrush != null ? currentBrush.getSize() : 10f) / 2f + 2;
                dirtyCanvasRect.set(x - r, y - r, x + r, y + r);
                dirtyRectValid = true;

                // Invalidate just that area (screen coords)
                invalidateDirtyRect();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (!drawing) break;

                float sx = event.getX();
                float sy = event.getY();

                long now = System.currentTimeMillis();
                long dt = Math.max(1, now - lastTouchTime);
                float dScreen = (float) Math.sqrt(
                        (sx - lastTouchX) * (sx - lastTouchX)
                      + (sy - lastTouchY) * (sy - lastTouchY));
                float velocity = dScreen / dt;

                float dx = x - lastX;
                float dy = y - lastY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                int steps = Math.max(1, (int) (dist / Math.max(1, currentBrush.getSize() * 0.5f)));

                for (int i = 0; i <= steps; i++) {
                    float t = (float) i / steps;
                    float ix = lastX + dx * t;
                    float iy = lastY + dy * t;
                    float pressure = event.getPressure();
                    float speedFactor = Math.min(1f, velocity / 3f);
                    float modulatedSize = currentBrush.getSize()
                            * (0.5f + 0.5f * (1f - speedFactor));

                    if (currentBrush != null) {
                        drawStroke(lastX, lastY, ix, iy, pressure, modulatedSize);
                    }

                    // Expand dirty rect to include this sub-step
                    float r = modulatedSize / 2f + 2;
                    if (dirtyRectValid) {
                        dirtyCanvasRect.top = Math.min(dirtyCanvasRect.top, iy - r);
                        dirtyCanvasRect.left = Math.min(dirtyCanvasRect.left, ix - r);
                        dirtyCanvasRect.bottom = Math.max(dirtyCanvasRect.bottom, iy + r);
                        dirtyCanvasRect.right = Math.max(dirtyCanvasRect.right, ix + r);
                    } else {
                        dirtyCanvasRect.set(ix - r, iy - r, ix + r, iy + r);
                        dirtyRectValid = true;
                    }
                }

                // Update preview path
                strokePoints.add(new PointF(x, y));
                rebuildPreviewPath();

                lastX = x;
                lastY = y;
                lastTouchX = sx;
                lastTouchY = sy;
                lastTouchTime = System.currentTimeMillis();

                // IMPORTANT: during stroke, do NOT flatten — only invalidate dirty region
                // flatDirty stays false; we draw to pixel layer + show path preview only
                invalidateDirtyRect();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                drawing = false;
                currentPath = null;
                dirtyRectValid = false;

                // Stroke finished: rebuild flat + full refresh
                if (flatBitmap != null) {
                    // Quick dirty flatten — only composite the active layer change
                    flatDirty = true;
                }
                invalidate();
                return true;
            }

            default:
                return super.onTouchEvent(event);
        }
        return true;
    }

    /** Convert dirty CanvasRect to screen-space Rect and invalidate. */
    private void invalidateDirtyRect() {
        if (!dirtyRectValid || dirtyCanvasRect.isEmpty()) return;

        // Expand a bit for anti-aliasing bleed
        float pad = (float) Math.ceil(Math.max(4, currentBrush != null
                ? currentBrush.getSize() * scale * 0.15f : 4));

        int l = (int) (dirtyCanvasRect.left * scale + offsetX - pad);
        int t = (int) (dirtyCanvasRect.top * scale + offsetY - pad);
        int r = (int) (dirtyCanvasRect.right * scale + offsetX + pad);
        int b = (int) (dirtyCanvasRect.bottom * scale + offsetY + pad);

        // Clamp to view bounds
        l = Math.max(0, l);
        t = Math.max(0, t);
        r = Math.min(getWidth(), r);
        b = Math.min(getHeight(), b);

        if (r > l && b > t) {
            invalidate(l, t, r, b);
        } else {
            invalidate();
        }
    }

    // ── Drawing helpers ────────────────────────────────

    private void rebuildPreviewPath() {
        if (strokePoints.size() < 2) return;
        currentPath = new Path();
        currentPath.moveTo(strokePoints.get(0).x, strokePoints.get(0).y);

        for (int i = 1; i < strokePoints.size() - 1; i++) {
            PointF p1 = strokePoints.get(i);
            PointF p2 = strokePoints.get(i + 1);
            float cx = (p1.x + p2.x) / 2f;
            float cy = (p1.y + p2.y) / 2f;
            currentPath.quadTo(p1.x, p1.y, cx, cy);
        }
        PointF last = strokePoints.get(strokePoints.size() - 1);
        currentPath.lineTo(last.x, last.y);

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

        float origSize = currentBrush.getSize();
        currentBrush.setSize(size);

        if (eraserMode) {
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
        if (checkerTile != null) {
            checkerTile.recycle();
            checkerTile = null;
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
