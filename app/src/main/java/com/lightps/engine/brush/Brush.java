package com.lightps.engine.brush;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Brush definition and stroke drawing.
 * Supports round, textured (image-based), and airbrush modes.
 */
public class Brush {

    public enum Shape { CIRCLE, SQUARE, TEXTURE, AIRBRUSH }

    private Shape shape;
    private int color;          // ARGB
    private float size;         // diameter in pixels
    private float hardness;     // 0.0 (soft) .. 1.0 (hard)
    private float opacity;      // 0.0 .. 1.0
    private float spacing;      // percent of diameter between dabs
    private Bitmap texture;     // null for shape-based brushes

    public Brush() {
        this.shape = Shape.CIRCLE;
        this.color = Color.BLACK;
        this.size = 20f;
        this.hardness = 0.8f;
        this.opacity = 1.0f;
        this.spacing = 0.15f;
        this.texture = null;
    }

    // ── Builder-style setters ──────────────────────────

    public Brush setShape(Shape s) { this.shape = s; return this; }
    public Brush setColor(int c) { this.color = c; return this; }
    public Brush setSize(float s) { this.size = Math.max(1, s); return this; }
    public Brush setHardness(float h) { this.hardness = Math.max(0, Math.min(1, h)); return this; }
    public Brush setOpacity(float o) { this.opacity = Math.max(0, Math.min(1, o)); return this; }
    public Brush setSpacing(float s) { this.spacing = Math.max(0.01f, s); return this; }
    public Brush setTexture(Bitmap t) { this.texture = t; return this; }

    // ── Getters ────────────────────────────────────────

    public Shape getShape() { return shape; }
    public int getColor() { return color; }
    public float getSize() { return size; }
    public float getHardness() { return hardness; }
    public float getOpacity() { return opacity; }
    public float getSpacing() { return spacing; }
    public Bitmap getTexture() { return texture; }

    // ── Drawing ────────────────────────────────────────

    /**
     * Apply a single dab at (x, y) onto canvas.
     */
    public void apply(Canvas canvas, float x, float y) {
        if (shape == Shape.TEXTURE && texture != null) {
            drawTextureDab(canvas, x, y);
        } else {
            drawShapeDab(canvas, x, y);
        }
    }

    /**
     * Draw a stroke from (x1,y1) to (x2,y2) with pressure.
     */
    public void drawStroke(Canvas canvas, float x1, float y1, float x2, float y2, float pressure) {
        float currentSize = size * pressure;
        float spacingPx = currentSize * spacing;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist == 0) {
            apply(canvas, x1, y1);
            return;
        }

        float steps = dist / spacingPx;
        float stepX = dx / steps;
        float stepY = dy / steps;

        for (int i = 0; i <= (int) steps; i++) {
            float px = x1 + stepX * i;
            float py = y1 + stepY * i;
            apply(canvas, px, py);
        }
    }

    /** Draw a single shape-based dab. */
    private void drawShapeDab(Canvas canvas, float x, float y) {
        Paint p = new Paint();
        p.setColor(color);
        p.setAlpha((int) (opacity * 255));
        p.setStyle(Paint.Style.FILL);

        float radius = size / 2f;

        if (hardness < 1.0f) {
            // Apply blur to simulate softness
            float blurRadius = radius * (1f - hardness) * 2f;
            if (blurRadius > 0) {
                p.setMaskFilter(new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL));
            }
        }

        switch (shape) {
            case CIRCLE:
                canvas.drawCircle(x, y, radius, p);
                break;
            case SQUARE:
                canvas.drawRect(x - radius, y - radius, x + radius, y + radius, p);
                break;
            case AIRBRUSH:
                // Multi-layered circles to simulate airbrush
                for (int i = 0; i < 5; i++) {
                    float r = radius * (1f - i * 0.15f);
                    int a = (int) (opacity * 255 / (i + 1));
                    p.setAlpha(a);
                    canvas.drawCircle(x, y, r, p);
                }
                break;
            default:
                break;
        }
    }

    /** Draw a dab using a texture brush. */
    private void drawTextureDab(Canvas canvas, float x, float y) {
        if (texture == null) return;
        Paint p = new Paint();
        p.setAlpha((int) (opacity * 255));

        float scale = size / Math.max(texture.getWidth(), texture.getHeight());
        canvas.save();
        canvas.translate(x - texture.getWidth() * scale / 2f,
                         y - texture.getHeight() * scale / 2f);
        canvas.scale(scale, scale);
        canvas.drawBitmap(texture, 0, 0, p);
        canvas.restore();
    }
}
